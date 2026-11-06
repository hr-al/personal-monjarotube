package ru.geelymonjaro.monjarotube

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun versionsCompareNumericallyAndOnlyStableTagsAreAccepted() {
        assertTrue(UpdatePolicy.version("v1.10.0")!! > UpdatePolicy.version("1.9.9")!!)
        assertEquals(UpdatePolicy.version("1.0.0"), UpdatePolicy.version("v1.0.0"))
        listOf("1.0", "1.0.0-beta", "1.0.0+local", "01.0.0", "1.0.0/other", "999999999.0.0", " 1.0.0").forEach {
            assertNull(it, UpdatePolicy.version(it))
        }
    }

    @Test fun downloadCannotEscapeTheRepositoryReleaseOrUseAnUnexpectedScheme() {
        val version = UpdatePolicy.version("1.0.1")!!
        val valid = UpdatePolicy.downloadUrl(version)
        assertTrue(UpdatePolicy.trustedDownload(valid, version))
        listOf(valid.replace("https:", "http:"), valid.replace("github.com/", "github.com.evil.test/"),
            valid.replace("github.com/", "github.com@evil.test/"), valid.replace("personal-monjarotube", "other-project"),
            valid.replace("v1.0.1/", "v1.0.0/"), "$valid?redirect=elsewhere", "$valid#fragment",
            valid.replace("github.com/", "github.com:443/"), "file:///tmp/update.apk").forEach {
            assertFalse(it, UpdatePolicy.trustedDownload(it, version))
        }
    }
}
