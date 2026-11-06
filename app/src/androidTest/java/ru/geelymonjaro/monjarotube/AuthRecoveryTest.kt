package ru.geelymonjaro.monjarotube

import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRecoveryTest {
    @Test fun failedHandoffCanRestartWithoutReplacingWebViewOrClearingCookies() {
        // This test changes a test preference and creates a synthetic cookie: emulator only.
        assumeTrue(Build.MODEL.contains("Android SDK") || Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("dpi", 0)
        val originalMode = prefs.getInt("mode", 1)
        prefs.edit().putBoolean("automaticLaunch", true).putInt("mode", 0).commit()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val browserField = MainActivity::class.java.getDeclaredField("browser").apply { isAccessible = true }
        val routeField = MainActivity::class.java.getDeclaredField("routeReady").apply { isAccessible = true }
        val authErrorField = MainActivity::class.java.getDeclaredField("authPageFailed").apply { isAccessible = true }
        val syntheticUrl = "https://accounts.youtube.com/accounts/SetSID?test_token=not-a-real-token"
        try {
            var ready = false
            repeat(100) {
                instrumentation.runOnMainSync { ready = routeField.getBoolean(activity) }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("WebView route was not created", ready)
            instrumentation.runOnMainSync {
                val view = browserField.get(activity) as WebView
                view.stopLoading()
                view.settings.blockNetworkLoads = true
                val cookies = CookieManager.getInstance()
                cookies.setCookie("https://example.test/", "monjaro_recovery_test=kept; Secure; Path=/")
                cookies.flush()
                // Network loading is blocked before this synthetic URL is submitted.
                view.loadUrl(syntheticUrl)
            }
            var failed = false
            repeat(100) {
                instrumentation.runOnMainSync { failed = authErrorField.getBoolean(activity) }
                if (!failed) Thread.sleep(100)
            }
            assertTrue("Blocked authentication request must show the recovery UI", failed)
            instrumentation.runOnMainSync {
                val view = browserField.get(activity) as WebView
                val cookies = CookieManager.getInstance()
                view.webViewClient.onPageCommitVisible(view, syntheticUrl)
                assertEquals("Raw Chromium auth error page must stay hidden", View.INVISIBLE, view.visibility)
                fun labels(node: View): List<TextView> = when (node) {
                    is TextView -> listOf(node)
                    is ViewGroup -> (0 until node.childCount).flatMap { labels(node.getChildAt(it)) }
                    else -> emptyList()
                }
                val text = labels(activity.window.decorView)
                assertFalse(text.any { it.text.contains("test_token") })
                val retry = text.single { it.text.toString() == "Повторить вход" }
                // Keep this recovery test offline; the production callback was exercised above.
                view.webViewClient = WebViewClient()
                assertTrue(retry.performClick())
                assertSame("Retry must retain WebView", view, browserField.get(activity))
                assertTrue(routeField.getBoolean(activity))
                assertTrue(cookies.getCookie("https://example.test/")?.contains("monjaro_recovery_test=kept") == true)
                val current = MainActivity::class.java.getDeclaredField("currentUrl").apply { isAccessible = true }.get(activity)
                assertEquals("Retry starts the official flow, not the one-time URL", NavigationPolicy.SIGN_IN, current)
            }
        } finally {
            instrumentation.runOnMainSync {
                CookieManager.getInstance().setCookie("https://example.test/", "monjaro_recovery_test=; Max-Age=0; Secure; Path=/")
                activity.finish()
            }
            prefs.edit().putInt("mode", originalMode).commit()
        }
    }
}
