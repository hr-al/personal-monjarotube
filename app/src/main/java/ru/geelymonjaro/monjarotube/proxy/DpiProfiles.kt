package ru.geelymonjaro.monjarotube.proxy

/** Arguments go directly to the bundled C program, without a shell. Listener settings stay fixed. */
object DpiProfiles {
    const val YOUTUBE_PROFILE = 9
    val accountNames = listOf("Стандартный", "Расширенное разбиение", "Разбиение + TLS record")
    private val accountStrategies = listOf(
        "-d 1 -s 1+s -s 3+s -s 6+s -s 9+s -s 12+s -s 15+s -s 20+s -s 30+s",
        "-d 1 -s 1+s -d 3+s -s 6+s -d 9+s -s 12+s -d 15+s -s 20+s -d 25+s -s 30+s -d 35+s",
        "-s 1 -d 1 -s 5+s -s 10+s -s 15+s -s 20+s -r 1+s -S"
    )
    private fun youtubeArguments(accountVariant: Int) =
        "-H :accounts.youtube.com ${accountStrategies[accountVariant]} -A none -H :googlevideo.com -q 1 -r 25+s -A none -o 3+s"
    val names = listOf("SOCKS без обхода", "Split 1", "Disorder 1 + TLS 1+s",
        "Split внутри SNI", "Disorder внутри SNI", "TLS record внутри SNI",
        "Fake TTL 8", "Fake TCP MD5", "OOB внутри SNI", "YouTube, видео и вход", "Свои параметры")
    private val presets = listOf("", "-s 1", "-d 1 -r 1+s", "-s 1+s", "-d 1+s",
        "-r 3+s", "-f -1 -t 8", "-f -1 -S", "-o 3+s",
        youtubeArguments(0))
    fun arguments(profile: Int, custom: String = "", accountVariant: Int = 0): Array<String> {
        require(profile in names.indices) { "Неизвестный профиль" }
        require(accountVariant in accountNames.indices) { "Неизвестный вариант входа" }
        return parse(when {
            profile == YOUTUBE_PROFILE -> youtubeArguments(accountVariant)
            profile < presets.size -> presets[profile]
            else -> custom
        })
    }
    fun parse(text: String): Array<String> {
        require(text.length <= 512) { "Не более 512 символов" }
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(tokens.size <= 64) { "Слишком много параметров" }
        var i = 0
        while (i < tokens.size) {
            val option = tokens[i++]
            if (option in setOf("-S", "-Y")) continue
            require(option in setOf("-s", "-d", "-o", "-q", "-f", "-r", "-t", "-T", "-n", "-A", "-H")) {
                "Неподдерживаемый параметр: $option. Доступны -s -d -o -q -f -r -t -T -n -A -H -S -Y"
            }
            require(i < tokens.size) { "После $option нужно значение" }
            val value = tokens[i++]
            val valid = when (option) {
                "-t" -> value.toIntOrNull()?.let { it in 1..64 } == true
                "-T" -> value.toDoubleOrNull()?.let { it in 0.1..30.0 } == true
                "-n" -> value.length <= 253 && Regex("[A-Za-z0-9.-]+").matches(value)
                "-A" -> value.split(',').all { it in setOf("torst", "redirect", "ssl_err", "none") }
                "-H" -> value.startsWith(':') && value.length in 2..254 &&
                    value.drop(1).split('.').all { Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?").matches(it) }
                else -> Regex("-?[0-9]{1,5}(\\+[sh])?").matches(value) &&
                    value.substringBefore('+').toInt().let { it != 0 && it in -16384..16384 }
            }
            require(valid) { "Некорректное значение $option: $value" }
        }
        return tokens.toTypedArray()
    }
}
