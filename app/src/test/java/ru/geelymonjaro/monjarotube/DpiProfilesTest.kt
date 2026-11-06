package ru.geelymonjaro.monjarotube

import org.junit.Assert.*
import org.junit.Test
import ru.geelymonjaro.monjarotube.proxy.DpiProfiles

class DpiProfilesTest {
    @Test fun presetsAreValid() {
        DpiProfiles.names.indices.forEach { DpiProfiles.arguments(it, "-s 1+s") }
    }
    @Test fun preservesOffsetsAndAutoGroups() {
        assertArrayEquals(arrayOf("-f", "-1", "-S", "-A", "torst", "-s", "1+s"),
            DpiProfiles.parse(" -f -1\n-S -A torst -s 1+s "))
    }
    @Test fun rejectsListenerAndFileOptions() {
        listOf("-i 0.0.0.0", "-p 8888", "-H hosts.txt", "-l secret", "-s 1;anything").forEach {
            assertThrows(IllegalArgumentException::class.java) { DpiProfiles.parse(it) }
        }
    }
    @Test fun rejectsMissingOrInvalidValues() {
        listOf("-s", "-s 0", "-s 99999", "-t 100", "-T NaN", "-A wrong").forEach {
            assertThrows(IllegalArgumentException::class.java) { DpiProfiles.parse(it) }
        }
    }
    @Test fun permitsLiteralHostScopeButNeverHostFiles() {
        val groups = DpiProfiles.arguments(9).toList().joinToString(" ").split(" -A none ")
        assertEquals(3, groups.size)
        assertEquals("-H :accounts.youtube.com -d 1 -s 1+s -s 3+s -s 6+s -s 9+s -s 12+s -s 15+s -s 20+s -s 30+s", groups[0])
        assertEquals("-H :googlevideo.com -q 1 -r 25+s", groups[1])
        assertEquals("-o 3+s", groups[2])
        listOf("-H hosts.txt", "-H :../hosts", "-H :foo..com", "-H :bad/name", "-H :-bad.com").forEach {
            assertThrows(IllegalArgumentException::class.java) { DpiProfiles.parse(it) }
        }
    }
    @Test fun accountVariantsDoNotChangeMediaOrGeneralTraffic() {
        val defaultGroups = DpiProfiles.arguments(9).joinToString(" ").split(" -A none ")
        val firstGroups = mutableSetOf<String>()
        DpiProfiles.accountNames.indices.forEach { variant ->
            val groups = DpiProfiles.arguments(9, accountVariant = variant).joinToString(" ").split(" -A none ")
            assertEquals(defaultGroups.drop(1), groups.drop(1))
            assertTrue(groups[0].startsWith("-H :accounts.youtube.com "))
            firstGroups += groups[0]
        }
        assertEquals(DpiProfiles.accountNames.size, firstGroups.size)
    }
    @Test fun accountVariantDoesNotReplaceCustomOrOtherProfiles() {
        for (profile in DpiProfiles.names.indices.filter { it != DpiProfiles.YOUTUBE_PROFILE })
            assertArrayEquals(DpiProfiles.arguments(profile, "-s 1+s"), DpiProfiles.arguments(profile, "-s 1+s", 2))
        assertThrows(IllegalArgumentException::class.java) { DpiProfiles.arguments(9, accountVariant = 3) }
    }
}
