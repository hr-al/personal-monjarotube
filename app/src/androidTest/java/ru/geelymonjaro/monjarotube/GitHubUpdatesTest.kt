package ru.geelymonjaro.monjarotube

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdatesTest {
    private fun fixture(): JSONObject = JSONObject().put("draft", false).put("prerelease", false)
        .put("tag_name", "v1.0.1").put("html_url", UpdatePolicy.releaseUrl(UpdatePolicy.version("1.0.1")!!))
        .put("body", "Release notes").put("assets", JSONArray().put(JSONObject()
            .put("name", "MonjaroTube-1.0.1.apk").put("state", "uploaded").put("size", 12345)
            .put("browser_download_url", UpdatePolicy.downloadUrl(UpdatePolicy.version("1.0.1")!!))))

    private fun rejected(root: JSONObject) {
        assertTrue(runCatching { GitHubUpdates.parse(root.toString()) }.isFailure)
    }

    @Test fun acceptsPublishedStableReleaseWithExpectedApk() {
        val release = GitHubUpdates.parse(fixture().toString())
        assertEquals("1.0.1", release.version.toString())
        assertEquals("Release notes", release.notes)
    }

    @Test fun rejectsDraftPreviewMissingApkOrUntrustedLinks() {
        rejected(fixture().put("draft", true))
        rejected(fixture().put("prerelease", true))
        rejected(fixture().put("assets", JSONArray()))
        rejected(fixture().put("html_url", "https://evil.test/release"))
        val root = fixture()
        root.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.test/update.apk")
        rejected(root)
        val oversized = fixture()
        oversized.getJSONArray("assets").getJSONObject(0).put("size", 104857601)
        rejected(oversized)
    }
}
