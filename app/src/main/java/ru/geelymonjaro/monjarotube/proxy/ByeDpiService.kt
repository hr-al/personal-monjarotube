package ru.geelymonjaro.monjarotube.proxy

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log

/** Bound service, isolated from WebView. Ending the binding ends all native sockets. */
class ByeDpiService : Service() {
    companion object { const val START = 1; const val STATS = 2; const val READY = 3; const val FAILED = 4 }
    private val handler = Handler(Looper.getMainLooper())
    private var native: NativeDpi? = null
    private var started = false
    private var attempts = 0
    private var client: Messenger? = null
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            START -> {
                client = message.replyTo
                if (!started) {
                    started = true
                    try {
                        val engine = NativeDpi().also { native = it }
                        val arguments = DpiProfiles.arguments(message.arg1, message.data.getString("custom").orEmpty(), message.data.getInt("accountVariant", 0))
                        Log.i("MonjaroDPI", "Arguments: ${arguments.joinToString(" ")}")
                        Thread({
                            try { Log.i("MonjaroDPI", "Native exit: ${engine.run(arguments)}") }
                            catch (e: Throwable) { Log.e("MonjaroDPI", "Native failure", e); handler.post { reply(FAILED) } }
                        }, "ByeDPI").start()
                        handler.post(::checkReady)
                    } catch (e: Throwable) { Log.e("MonjaroDPI", "Load failure", e); reply(FAILED) }
                }
            }
            STATS -> try {
                val values = native?.stats() ?: longArrayOf(-1, 0, 0, 0)
                val response = Message.obtain(null, STATS).apply {
                    data.putLongArray("stats", values)
                    data.putString("lastError", native?.lastError().orEmpty())
                }
                message.replyTo?.send(response)
            } catch (e: Exception) { Log.w("MonjaroDPI", "Stats unavailable", e) }
        }
        true
    })
    private fun reply(what: Int) { try { client?.send(Message.obtain(null, what)) } catch (_: RemoteException) {} }
    private fun checkReady() {
        when (native?.stats()?.firstOrNull()) {
            1L -> reply(READY)
            -1L -> reply(FAILED)
            else -> if (++attempts < 100) handler.postDelayed(::checkReady, 100) else reply(FAILED)
        }
    }
    override fun onBind(intent: Intent): IBinder = inbox.binder
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        // Native globals and all sockets belong to this private process. No unsafe reset on a live worker.
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
