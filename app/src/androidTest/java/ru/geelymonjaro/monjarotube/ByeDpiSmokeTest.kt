package ru.geelymonjaro.monjarotube

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.geelymonjaro.monjarotube.proxy.ByeDpiService
import ru.geelymonjaro.monjarotube.proxy.DpiProfiles
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ByeDpiSmokeTest {
    private fun exerciseProfile(profile: Int, network: Boolean = false, custom: String = "", host: String = "m.youtube.com", connectAddress: String? = null, requireNetwork: Boolean = false, accountVariant: Int = 0) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = CountDownLatch(1)
        val response = AtomicReference<Int>()
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == ByeDpiService.READY || message.what == ByeDpiService.FAILED) {
                response.set(message.what); ready.countDown()
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Messenger(binder).send(Message.obtain(null, ByeDpiService.START, profile, 0).apply {
                    replyTo = receiver
                    data.putString("custom", custom)
                    data.putInt("accountVariant", accountVariant)
                })
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        assertTrue(context.bindService(Intent(context, ByeDpiService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Native startup timed out for profile $profile", ready.await(15, TimeUnit.SECONDS))
            assertEquals("Native profile failed", ByeDpiService.READY, response.get())
            if (network) {
                val result = Socks5Probe.check(1088, host, https = true, connectAddress = connectAddress)
                android.util.Log.i("MonjaroNetworkScan", "profile=$profile args=$custom host=$host ok=${result.ok} ${result.message}")
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("stream", "\nprofile=$profile args=$custom host=$host address=$connectAddress ok=${result.ok} ${result.message}\n")
                })
                if (requireNetwork) assertTrue("$host: ${result.message}", result.ok)
            } else Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 1088), 2000)
                socket.soTimeout = 3000
                socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertEquals(5, socket.getInputStream().read())
                assertEquals("SOCKS no-auth negotiation", 0, socket.getInputStream().read())
                // Real loopback traffic through the proxy, without depending on YouTube or DNS.
                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { echo ->
                    echo.soTimeout = 3000
                    val echoed = CountDownLatch(1)
                    val error = AtomicReference<Throwable>()
                    Thread {
                        try {
                            echo.accept().use { peer ->
                                peer.soTimeout = 3000
                                val payload = ByteArray(4)
                                java.io.DataInputStream(peer.getInputStream()).readFully(payload)
                                assertArrayEquals("ping".toByteArray(), payload)
                                peer.getOutputStream().write("pong".toByteArray())
                            }
                        } catch (e: Throwable) { error.set(e) }
                        finally { echoed.countDown() }
                    }.start()
                    val port = echo.localPort
                    socket.getOutputStream().write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, (port shr 8).toByte(), port.toByte()))
                    val reply = ByteArray(10)
                    java.io.DataInputStream(socket.getInputStream()).readFully(reply)
                    assertEquals("SOCKS CONNECT response", 0, reply[1].toInt())
                    socket.getOutputStream().write("ping".toByteArray())
                    val payload = ByteArray(4)
                    java.io.DataInputStream(socket.getInputStream()).readFully(payload)
                    assertArrayEquals("pong".toByteArray(), payload)
                    assertTrue(echoed.await(5, TimeUnit.SECONDS))
                    assertNull(error.get())
                }
            }
        } finally { context.unbindService(connection) }
        val deadline = SystemClock.elapsedRealtime() + 5000
        var closed = false
        while (SystemClock.elapsedRealtime() < deadline) {
            try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 1088), 150) } }
            catch (_: Exception) { closed = true; break }
            SystemClock.sleep(100)
        }
        assertTrue("Proxy listener leaked after unbind", closed)
    }
    @Test fun plainSocksStartupAndTeardown() = exerciseProfile(0)
    @Test fun splitStartupAndTeardown() = exerciseProfile(1)
    @Test fun disorderTlsStartupAndTeardown() = exerciseProfile(2)
    @Test fun networkProfileScanWhenRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("networkProfiles") == "true")
        for (profile in 0..8) exerciseProfile(profile, network = true)
    }
    @Test fun networkCustomScanWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkCustom") == "true")
        val host = requireNotNull(args.getString("networkHost"))
        for (custom in listOf("-o 1+s", "-o 2+s", "-o 7+s", "-q 3+s", "-o 3+s -r 1+s",
                "-s 1 -o 3+s", "-q 1", "-q 1+s", "-s 1 -q 1+s")) {
            exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = custom, host = host)
        }
    }
    @Test fun networkReferenceScanWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkReference") == "true")
        val host = requireNotNull(args.getString("networkHost"))
        // Compatible TCP variants observed in the user's reference APK. UDP stays disabled.
        for (custom in listOf("",
                "-d 1 -s 1+s -s 3+s -s 6+s -s 9+s -s 12+s -s 15+s -s 20+s -s 30+s",
                "-d 1 -s 1+s -d 3+s -s 6+s -d 9+s -s 12+s -d 15+s -s 20+s -d 25+s -s 30+s -d 35+s",
                "-s 1+s -d 3+s", "-o 1+s -d 3+s", "-d 1 -s 3+s", "-q 1 -r 25+s",
                "-s 1 -q 1 -A redirect -s 5 -o 1+s -A torst -f -1 -r 1+s -A ssl_err -s 1 -o 1+s -s -1")) {
            exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = custom, host = host)
        }
    }
    @Test fun networkReferenceConfirmWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkConfirm") == "true")
        exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = "-q 1 -r 25+s")
        val combined = "-o 3+s -A torst,ssl_err -T 3 -q 1 -r 25+s"
        exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = combined)
        exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = combined, host = requireNotNull(args.getString("networkHost")))
    }
    @Test fun networkHostScopeWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkHostScope") == "true")
        exerciseProfile(9, network = true)
        exerciseProfile(9, network = true, host = requireNotNull(args.getString("networkHost")))
    }
    @Test fun networkAccountsWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkAccounts") == "true")
        val host = "accounts.youtube.com"
        exerciseProfile(9, network = true, host = host)
        for (custom in listOf("-q 1 -r 25+s", "-o 1+s", "-o 2+s", "-s 1+s -d 3+s", "-d 1 -s 3+s", "-s 1 -o 3+s", "")) {
            exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = custom, host = host)
        }
    }
    @Test fun networkAccountSpecialWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkAccountSpecial") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (endpoint in listOf("https://1.1.1.1/dns-query?name=accounts.youtube.com&type=A", "https://dns.google/resolve?name=accounts.youtube.com&type=A")) {
            val result = try {
                val connection = java.net.URL(endpoint).openConnection(java.net.Proxy.NO_PROXY) as javax.net.ssl.HttpsURLConnection
                try {
                    connection.connectTimeout = 2000; connection.readTimeout = 2000
                    connection.setRequestProperty("Accept", "application/dns-json")
                    connection.inputStream.bufferedReader().use { it.readText().take(4096) }
                } finally { connection.disconnect() }
            } catch (e: Exception) { e.javaClass.simpleName + ": " + e.message }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nDNS $endpoint $result\n") })
        }
        for (profile in listOf(4, 5, 6, 7)) exerciseProfile(profile, network = true, host = "accounts.youtube.com")
        for (custom in listOf("-f -1 -t 3 -r 1+s", "-s 1 -s 3+s"))
            exerciseProfile(DpiProfiles.names.lastIndex, network = true, host = "accounts.youtube.com", custom = custom)
    }
    @Test fun networkAccountAddressWhenRequested() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("networkAccountAddress") == "true")
        for (address in listOf("64.233.164.101", "64.233.164.100", "209.85.233.113")) {
            exerciseProfile(9, network = true, host = "accounts.youtube.com", connectAddress = address)
        }
        exerciseProfile(DpiProfiles.names.lastIndex, network = true, host = "accounts.youtube.com",
            custom = "-q 1 -r 25+s", connectAddress = "64.233.164.101")
    }
    @Test fun networkAccountIsolationWhenRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("networkAccountIsolation") == "true")
        val addresses = mutableMapOf<String, String>()
        for (host in listOf("accounts.youtube.com", "accounts.google.com", "m.youtube.com")) {
            val address = ru.geelymonjaro.monjarotube.proxy.DohResolver.resolve(host)
            if (address != null) addresses[host] = address
            exerciseProfile(9, network = true, host = host)
        }
        // Isolate endpoint IP reachability from the TLS hostname. Certificate checks remain enabled.
        addresses["accounts.youtube.com"]?.let {
            exerciseProfile(9, network = true, host = "accounts.google.com", connectAddress = it)
        }
        addresses["m.youtube.com"]?.let {
            exerciseProfile(9, network = true, host = "accounts.youtube.com", connectAddress = it)
        }
        for (suffix in listOf("", "&edns_client_subnet=0.0.0.0/0")) {
            val connection = java.net.URL("https://dns.google/resolve?name=accounts.youtube.com&type=A$suffix")
                .openConnection(java.net.Proxy.NO_PROXY) as javax.net.ssl.HttpsURLConnection
            val answer = try {
                connection.connectTimeout = 2500; connection.readTimeout = 2500
                connection.inputStream.bufferedReader().use { ru.geelymonjaro.monjarotube.proxy.DohResolver.parse(it.readText()) }
            } finally { connection.disconnect() }
            answer?.first?.let { exerciseProfile(9, network = true, host = "accounts.youtube.com", connectAddress = it) }
        }
    }
    @Test fun networkAccountKvnWhenRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("networkAccountKvn") == "true")
        // TCP-only strategies observed in the reference, compatible with the pinned MIT core.
        for (custom in listOf(
            "-d 1 -s 1+s -d 3+s -s 6+s -d 9+s -s 12+s -d 15+s -s 20+s -d 25+s -s 30+s -d 35+s",
            "-d 1 -s 1+s -s 3+s -s 6+s -s 9+s -s 12+s -s 15+s -s 20+s -s 30+s",
            "-s 1 -d 1 -s 5+s -s 10+s -s 15+s -s 20+s -r 1+s -S",
            "-f -1 -t 8 -s 1+s -d 3+s", "-s 1 -f -1 -S",
            "-o 1 -d 1 -r 1+s -S -s 1+s -d 3+s",
            "-d 1 -r 1+s -f -1 -S -t 8 -o 3+s", "-d 1+s -o 2 -s 5 -r 5"
        )) exerciseProfile(DpiProfiles.names.lastIndex, network = true, custom = custom, host = "accounts.youtube.com")
    }
    @Test fun networkAuthRouteWhenRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("networkAuthRoute") == "true")
        repeat(2) {
            for (host in listOf("accounts.youtube.com", "accounts.google.com"))
                exerciseProfile(9, network = true, host = host, requireNetwork = true)
        }
        exerciseProfile(9) // Same combined profile must still relay ordinary loopback TCP and close cleanly.
    }
    @Test fun networkAuthVariantsWhenRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("networkAuthVariants") == "true")
        for (variant in DpiProfiles.accountNames.indices) {
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("stream", "\nAccount variant: ${DpiProfiles.accountNames[variant]}\n")
            })
            exerciseProfile(9, accountVariant = variant)
            exerciseProfile(9, network = true, host = "accounts.youtube.com", accountVariant = variant)
        }
    }
}
