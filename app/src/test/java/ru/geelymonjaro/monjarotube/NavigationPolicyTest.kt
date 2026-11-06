package ru.geelymonjaro.monjarotube

import org.junit.Assert.*
import org.junit.Test

class NavigationPolicyTest {
    @Test fun youtubeHttpsNavigation() {
        listOf("https://m.youtube.com/", "https://www.youtube.com/watch?v=x", "https://youtu.be/x", "https://consent.youtube.com/")
            .forEach { assertTrue(it, NavigationPolicy.allows(it)) }
    }
    @Test fun rejectsExternalSchemesAndLookalikeHosts() {
        listOf("http://youtube.com", "https://youtube.com.evil.test/", "https://youtube.com@evil.test", "intent://youtube.com/", "file:///a", "https://evil.test/?youtube.com", "https://youtube.com:8080/", "javascript:alert(1)")
            .forEach { assertFalse(it, NavigationPolicy.allows(it)) }
    }
    @Test fun permitsOnlyOfficialAccountHostWithoutOpeningGeneralGoogleNavigation() {
        assertTrue(NavigationPolicy.allows(NavigationPolicy.SIGN_IN))
        assertTrue(NavigationPolicy.allows("https://accounts.google.com/v3/signin/identifier"))
        listOf("https://accounts.google.com.evil.test/", "https://accounts.google.com@evil.test/",
            "http://accounts.google.com/", "https://accounts.google.com:8080/", "https://evil.accounts.google.com/",
            "https://www.google.com/").forEach { assertFalse(it, NavigationPolicy.allows(it)) }
        assertFalse(NavigationPolicy.isYoutube(NavigationPolicy.SIGN_IN))
    }
    @Test fun googleOAuthIsHandledExternally() {
        for (url in listOf("https://accounts.google.com/o/oauth2/v2/auth?client_id=x", "https://accounts.google.com/oauth2/auth")) {
            assertTrue(NavigationPolicy.isGoogleOAuth(url)); assertFalse(NavigationPolicy.allows(url))
        }
        assertFalse(NavigationPolicy.isGoogleOAuth("https://evil.test/o/oauth2/auth"))
    }
    @Test fun authenticationHandoffsAreNeverRestoredOrProbedAsVideoPages() {
        assertTrue(NavigationPolicy.isContentPage("https://m.youtube.com/watch?v=public"))
        assertTrue(NavigationPolicy.allows("https://accounts.youtube.com/accounts/SetSID?token=sample"))
        assertFalse(NavigationPolicy.isContentPage("https://accounts.youtube.com/accounts/SetSID?token=sample"))
        assertFalse(NavigationPolicy.isContentPage(NavigationPolicy.SIGN_IN))
        assertFalse(NavigationPolicy.isContentPage("http://m.youtube.com/"))
    }
    @Test fun authenticationDiagnosticsExposeOnlyExactTrustedHost() {
        assertEquals("accounts.youtube.com", NavigationPolicy.authenticationHost("https://accounts.youtube.com/accounts/SetSID?token=sample"))
        assertEquals("accounts.google.com", NavigationPolicy.authenticationHost(NavigationPolicy.SIGN_IN))
        for (url in listOf("http://accounts.youtube.com/", "https://accounts.youtube.com.evil.test/",
            "https://accounts.youtube.com:8080/", "https://evil.accounts.google.com/", "https://m.youtube.com/"))
            assertNull(NavigationPolicy.authenticationHost(url))
    }
}
