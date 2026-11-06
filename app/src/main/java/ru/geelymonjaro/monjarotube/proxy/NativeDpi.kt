package ru.geelymonjaro.monjarotube.proxy

internal class NativeDpi {
    companion object { init { System.loadLibrary("monjarodpi") } }
    external fun run(arguments: Array<String>): Int
    external fun stats(): LongArray
    external fun lastError(): String
    // Called synchronously from the native worker; direct DoH avoids a recursive SOCKS request.
    fun resolveHost(host: String): String? = DohResolver.resolve(host)
}
