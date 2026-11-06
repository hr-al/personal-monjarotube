package ru.geelymonjaro.monjarotube

import java.net.URI

/** Public release metadata may only link back to this project's stable releases. */
internal object UpdatePolicy {
    const val REPOSITORY = "hr-al/personal-monjarotube"
    const val REPOSITORY_URL = "https://github.com/$REPOSITORY"
    const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"

    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
        override fun toString() = "$major.$minor.$patch"
    }

    fun version(value: String): Version? {
        val match = Regex("v?(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})").matchEntire(value) ?: return null
        return Version(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }

    fun apkName(version: Version) = "MonjaroTube-$version.apk"
    fun releaseUrl(version: Version) = "$REPOSITORY_URL/releases/tag/v$version"
    fun downloadUrl(version: Version) = "$REPOSITORY_URL/releases/download/v$version/${apkName(version)}"

    fun trustedDownload(raw: String, version: Version): Boolean = try {
        val uri = URI(raw)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && raw == downloadUrl(version)
    } catch (_: Exception) { false }
}
