package ru.geelymonjaro.monjarotube

import java.net.URLEncoder

internal object SearchNavigation {
    fun url(query: String): String {
        require(query.isNotBlank())
        return "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query.trim(), "UTF-8")
    }
}
