package ru.geelymonjaro.monjarotube

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class SearchNavigationTest {
    @Test fun userQueryCannotChangeDestinationOrAddParameters() {
        val query = "музыка & next=https://example.test/#фрагмент + 100%"
        val uri = URI(SearchNavigation.url(" $query "))
        assertEquals("m.youtube.com", uri.host)
        assertEquals("/results", uri.path)
        assertFalse(uri.rawQuery.contains('&'))
        assertNull(uri.fragment)
        assertEquals(query, URLDecoder.decode(uri.rawQuery.substringAfter('='), "UTF-8"))
    }
}
