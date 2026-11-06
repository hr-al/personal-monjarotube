package ru.geelymonjaro.monjarotube

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

/** Separate public HTTPS request: no WebView cookies, account data or GitHub credentials. */
internal class GitHubUpdates {
    data class Release(val version: UpdatePolicy.Version, val notes: String, val downloadUrl: String)
    class NoRelease : Exception()
    class RateLimited : Exception()
    private val active = AtomicReference<HttpsURLConnection?>()
    @Volatile private var cancelled = false

    fun cancel() { cancelled = true; active.getAndSet(null)?.disconnect() }

    fun latest(): Release {
        val connection = URL(UpdatePolicy.API_URL).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        check(active.compareAndSet(null, connection))
        try {
            check(!cancelled)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "MonjaroTube/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept-Encoding", "identity")
            when (connection.responseCode) {
                404 -> throw NoRelease()
                403, 429 -> throw RateLimited()
                200 -> Unit
                else -> error("GitHub HTTP response was not successful")
            }
            val data = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    check(!cancelled)
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 262144) { "Release response is too large" }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            return parse(data)
        } finally { active.compareAndSet(connection, null); connection.disconnect() }
    }

    companion object {
        internal fun parse(json: String): Release {
            val root = JSONObject(json)
            require(!root.getBoolean("draft") && !root.getBoolean("prerelease"))
            val version = requireNotNull(UpdatePolicy.version(root.getString("tag_name")))
            require(root.getString("tag_name") == "v$version")
            require(root.getString("html_url") == UpdatePolicy.releaseUrl(version))
            val assets = root.getJSONArray("assets")
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name") == UpdatePolicy.apkName(version) }
            val asset = matches.single()
            require(asset.getString("state") == "uploaded")
            require(asset.getLong("size") in 1..104857600L)
            val download = asset.getString("browser_download_url")
            require(UpdatePolicy.trustedDownload(download, version))
            return Release(version, root.optString("body").take(4000), download)
        }
    }
}
