package ru.geelymonjaro.monjarotube

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Checks the SOCKS protocol itself; a listening TCP port alone is insufficient. */
object Socks5Probe {
    data class Result(val ok: Boolean, val message: String)
    fun check(port: Int, destination: String? = null, https: Boolean = false, connectAddress: String? = null): Result {
        var stage = "local SOCKS"
        return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2500)
            socket.soTimeout = 3500
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            output.write(byteArrayOf(5, 1, 0))
            val version = input.readUnsignedByte()
            val method = input.readUnsignedByte()
            kotlin.check(version == 5) { "На порту $port не SOCKS5-сервер (version=$version)" }
            kotlin.check(method == 0) { "SOCKS5 на порту $port не разрешил подключение без пароля (method=$method)" }
            if (destination == null) {
                Result(true, "SOCKS5 127.0.0.1:$port: подключение без пароля работает")
            } else {
                stage = "SOCKS CONNECT"
                val domain = (connectAddress ?: destination).toByteArray(Charsets.US_ASCII)
                require(domain.size in 1..255)
                output.write(byteArrayOf(5, 1, 0, 3, domain.size.toByte()) + domain + byteArrayOf(1, -69)) // TCP 443
                val replyVersion = input.readUnsignedByte()
                val reply = input.readUnsignedByte()
                input.readUnsignedByte()
                val addressType = input.readUnsignedByte()
                kotlin.check(replyVersion == 5) { "Некорректный ответ SOCKS5 CONNECT" }
                val addressSize = when (addressType) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> error("Неизвестный адрес SOCKS5: $addressType") }
                input.readFully(ByteArray(addressSize + 2))
                val reason = when (reply) {
                    0 -> "TCP-соединение установлено"
                    1 -> "общая ошибка SOCKS-сервера"
                    2 -> "соединение запрещено сервером"
                    3 -> "сеть недоступна"
                    4 -> "целевой узел не найден или недоступен; проверьте DNS и сеть"
                    5 -> "целевой узел отказал в подключении"
                    6 -> "истекло время ожидания"
                    7 -> "сервер не поддерживает CONNECT"
                    8 -> "сервер не поддерживает тип адреса"
                    else -> "неизвестный ответ сервера"
                }
                val tcp = "SOCKS5 → $destination:443: $reason (REP=$reply)."
                if (reply == 0 && https) {
                    stage = "TLS handshake"
                    // Use system trust and hostname verification; CONNECT success alone is insufficient.
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    (factory.createSocket(socket, destination, 443, true) as SSLSocket).use { tls ->
                        tls.soTimeout = 8000
                        tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                        tls.startHandshake()
                        stage = "HTTPS response"
                        tls.getOutputStream().write(("GET / HTTP/1.1\r\nHost: $destination\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                        val status = StringBuilder()
                        val stream = tls.getInputStream()
                        while (status.length < 256) {
                            val b = stream.read()
                            if (b < 0 || b == 10) break
                            if (b != 13) status.append(b.toChar())
                        }
                        kotlin.check(status.startsWith("HTTP/1.")) { "Нет корректного ответа HTTP: $status" }
                        Result(true, "$tcp\nHTTPS: ${tls.session.protocol}; $status. Воспроизведение видео ещё не проверено.")
                    }
                } else Result(reply == 0, "$tcp TCP-проверка не подтверждает TLS или воспроизведение видео.")
            }
        }
    } catch (e: Exception) {
        Result(false, "${if (https) "SOCKS/HTTPS" else "SOCKS5"} 127.0.0.1:$port ($stage): ${e.javaClass.simpleName}: ${e.message.orEmpty().take(220)}")
    }
    }
}
