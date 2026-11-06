package ru.geelymonjaro.monjarotube

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.geelymonjaro.monjarotube.proxy.DohResolver

@RunWith(AndroidJUnit4::class)
class DohResolverTest {
    @Test fun cnameAndTtlAreRespected() {
        assertEquals("142.250.1.1" to 25, DohResolver.parse("""{"Status":0,"Answer":[
            {"type":5,"data":"youtube-ui.l.google.com.","TTL":25},
            {"type":1,"data":"142.250.1.1","TTL":300}]}"""))
        assertEquals("1.2.3.4" to 0, DohResolver.parse("""{"Status":0,"Answer":[{"type":1,"data":"1.2.3.4","TTL":0}]}"""))
    }
    @Test fun rejectsFailedTruncatedAndNonAddressAnswers() {
        listOf("""{"Status":3}""", """{"Status":0,"TC":true}""",
            """{"Status":0,"Answer":[{"type":5,"data":"example.com","TTL":60}]}""",
            """{"Status":0,"Answer":[{"type":1,"data":"256.1.1.1","TTL":60}]}""").forEach {
            assertNull(DohResolver.parse(it))
        }
        listOf("1.2.3", "1.2.3.4.5", "01.2.3.4", "a.2.3.4", "-1.2.3.4").forEach {
            assertFalse(DohResolver.validIpv4(it))
        }
    }
}
