package ru.geelymonjaro.monjarotube

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class Socks5ProbeTest {
    private fun withServer(serve: (Socket) -> Unit, test: (Int) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 4000
            val failure = AtomicReference<Throwable>()
            val thread = Thread {
                try { server.accept().use { it.soTimeout = 3000; serve(it) } }
                catch (e: Throwable) { failure.set(e) }
            }.apply { isDaemon = true; start() }
            test(server.localPort)
            thread.join(4500)
            assertFalse("Test SOCKS server leaked", thread.isAlive)
            failure.get()?.let { throw AssertionError("Test server failed", it) }
        }
    }
    private fun greeting(socket: Socket) {
        val request = ByteArray(3)
        DataInputStream(socket.getInputStream()).readFully(request)
        assertArrayEquals(byteArrayOf(5, 1, 0), request)
    }
    @Test fun validatesNoAuthHandshake() = withServer({ socket ->
        greeting(socket); socket.getOutputStream().write(byteArrayOf(5, 0))
    }) { port -> assertTrue(Socks5Probe.check(port).ok) }

    @Test fun rejectsPasswordOnlyProxy() = withServer({ socket ->
        greeting(socket); socket.getOutputStream().write(byteArrayOf(5, 2))
    }) { port ->
        val result = Socks5Probe.check(port)
        assertFalse(result.ok); assertTrue(result.message.contains("method=2"))
    }
    @Test fun reportsTruncatedReplyWithoutCrashing() = withServer({ socket ->
        greeting(socket); socket.getOutputStream().write(byteArrayOf(5))
    }) { port -> assertFalse(Socks5Probe.check(port).ok) }

    @Test fun encodesDomainAndExplainsConnectResult() {
        for (reply in listOf(0, 4)) withServer({ socket ->
            greeting(socket)
            val output = socket.getOutputStream()
            output.write(byteArrayOf(5, 0))
            val input = DataInputStream(socket.getInputStream())
            val header = ByteArray(4); input.readFully(header)
            assertArrayEquals(byteArrayOf(5, 1, 0, 3), header)
            val domain = ByteArray(input.readUnsignedByte()); input.readFully(domain)
            assertEquals("m.youtube.com", domain.toString(Charsets.US_ASCII))
            assertEquals(443, input.readUnsignedShort())
            output.write(byteArrayOf(5, reply.toByte(), 0, 1, 127, 0, 0, 1, 0, 0))
        }) { port ->
            val result = Socks5Probe.check(port, "m.youtube.com")
            assertEquals(reply == 0, result.ok)
            assertTrue(result.message.contains("REP=$reply"))
        }
    }
}
