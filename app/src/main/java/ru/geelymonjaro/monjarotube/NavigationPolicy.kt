package ru.geelymonjaro.monjarotube

import java.net.URI

object NavigationPolicy {
    const val SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"
    private fun secureUri(raw: String): URI? = try {
        val uri = URI(raw)
        uri.takeIf { it.scheme.equals("https", true) && it.userInfo == null && (it.port == -1 || it.port == 443) }
    } catch (_: Exception) { null }
    fun isYoutube(raw: String): Boolean {
        val host = secureUri(raw)?.host?.lowercase() ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }
    fun isAccount(raw: String) = secureUri(raw)?.host.equals("accounts.google.com", true)
    fun authenticationHost(raw: String): String? = secureUri(raw)?.host?.lowercase()?.takeIf {
        it == "accounts.google.com" || it == "accounts.youtube.com"
    }
    // Authentication hand-off URLs can contain one-time tokens and must never become a launch page.
    fun isContentPage(raw: String): Boolean = secureUri(raw)?.host?.lowercase() in
        setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
    fun isGoogleOAuth(raw: String): Boolean {
        val uri = secureUri(raw) ?: return false
        val path = uri.path.lowercase()
        return uri.host.equals("accounts.google.com", true) &&
            (path.startsWith("/o/oauth2") || path.startsWith("/oauth2/"))
    }
    fun allows(raw: String) = isYoutube(raw) || (isAccount(raw) && !isGoogleOAuth(raw))
}
