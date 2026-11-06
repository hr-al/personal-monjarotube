package ru.geelymonjaro.monjarotube

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Run seedLegacyState on 0.2.5, install -r 1.0.0, then run verifyUpgradeState. Emulator only. */
class UpgradeStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun emulatorOnly() {
        assumeTrue(Build.MODEL.contains("Android SDK") || Build.FINGERPRINT.contains("generic"))
    }

    @Test fun seedLegacyState() {
        emulatorOnly()
        assertEquals(11L, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        context.getSharedPreferences("dpi", 0).edit()
            .putBoolean("automaticLaunch", true).putInt("profileSchema", 1)
            .putInt("mode", 0).putInt("profile", 7).putInt("accountVariant", 2)
            .putBoolean("keepScreenOn", false).putBoolean("lastPage", true)
            .putString("lastPageUrl", "https://m.youtube.com/results?search_query=upgrade-test")
            .putInt("upgradeTestUid", context.applicationInfo.uid).commit()
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie("https://example.test/",
                "monjaro_upgrade_test=kept; Max-Age=86400; Secure; Path=/") {
                assertTrue(it); CookieManager.getInstance().flush(); completed.countDown()
            }
        }
        assertTrue(completed.await(10, TimeUnit.SECONDS))
    }

    @Suppress("DEPRECATION")
    @Test fun verifyUpgradeState() {
        emulatorOnly()
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        assertEquals(12L, info.longVersionCode)
        assertEquals("1.0.0", info.versionName)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val certificate = info.signingInfo!!.apkContentsSigners.single().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }
        assertEquals("7416b51373bb20438b5da68d32ae20dec9e67a456315084f892e373c48ad8e7c", digest)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            val prefs = context.getSharedPreferences("dpi", 0)
            assertEquals(context.applicationInfo.uid, prefs.getInt("upgradeTestUid", -1))
            assertEquals(0, prefs.getInt("mode", -1))
            assertEquals(7, prefs.getInt("profile", -1))
            assertEquals(2, prefs.getInt("accountVariant", -1))
            assertFalse(prefs.getBoolean("keepScreenOn", true))
            assertTrue(prefs.getBoolean("lastPage", false))
            instrumentation.runOnMainSync {
                assertTrue(CookieManager.getInstance().getCookie("https://example.test/")
                    ?.contains("monjaro_upgrade_test=kept") == true)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
