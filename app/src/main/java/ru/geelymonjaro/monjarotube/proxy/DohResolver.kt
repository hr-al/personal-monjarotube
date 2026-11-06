package ru.geelymonjaro.monjarotube.proxy

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Bootstrap by literal IP; never re-enter the local SOCKS event loop. IPv4 prototype. */
internal object DohResolver {
    private data class Entry(val address: String, val expires: Long)
    private val cache = mutableMapOf<String, Entry>()
    private val endpoints = listOf("1.1.1.1", "1.0.0.1")

    @Synchronized fun resolve(host: String): String? {
        if (!host.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?"))) return null
        val key = host.lowercase()
        val now = SystemClock.elapsedRealtime()
        cache[key]?.takeIf { it.expires > now }?.let { return it.address }
        cache.entries.removeAll { it.value.expires <= now }
        for (endpoint in endpoints) {
            try {
                val connection = URL("https://$endpoint/dns-query?name=$key&type=A")
                    .openConnection(Proxy.NO_PROXY) as HttpsURLConnection
                try {
                    connection.connectTimeout = 1500
                    connection.readTimeout = 1500
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/dns-json")
                    check(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 65536)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val answer = parse(String(bytes, Charsets.UTF_8)) ?: continue
                    if (cache.size >= 256) cache.clear()
                    if (answer.second > 0) cache[key] = Entry(answer.first,
                        SystemClock.elapsedRealtime() + answer.second * 1000L)
                    Log.i("MonjaroDPI", "DoH resolved $key via $endpoint")
                    return answer.first
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                Log.w("MonjaroDPI", "DoH $endpoint: ${e.javaClass.simpleName}")
            }
        }
        return null // Native system resolver remains a fallback, with its existing error report.
    }

    internal fun parse(json: String): Pair<String, Int>? {
        val root = JSONObject(json)
        if (root.optInt("Status", -1) != 0 || root.optBoolean("TC")) return null
        val answers = root.optJSONArray("Answer") ?: return null
        var ttl = 300
        var address: String? = null
        for (i in 0 until answers.length()) {
            val answer = answers.getJSONObject(i)
            ttl = minOf(ttl, answer.optInt("TTL", 0).coerceAtLeast(0))
            val data = answer.optString("data")
            if (answer.optInt("type") == 1 && validIpv4(data) && address == null) address = data
        }
        return address?.let { it to ttl }
    }

    internal fun validIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all {
            it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } &&
                (it.length == 1 || it[0] != '0') && it.toInt() in 0..255
        }
    }
}
