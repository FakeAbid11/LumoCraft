package com.lumocraft.app.core.version

import com.lumocraft.app.BuildConfig

/**
 * Semantic version handling for the launcher and its update checks.
 *
 * LumoCraft versions follow SemVer with an optional prerelease suffix
 * (e.g. `0.1.0-rc1`). Git release tags are the same value with an
 * optional leading `v` (`v0.1.0-rc1`). Build numbers come from the
 * Android [BuildConfig.VERSION_CODE].
 *
 * Comparison follows the SemVer 2.0.0 precedence rules: core numbers
 * compare numerically, a release (no prerelease) is newer than any
 * prerelease of the same core, and prerelease identifiers compare
 * numerically then lexically.
 */
object VersionManager {

    /** Parses a version string, tolerating a leading `v` and trailing junk. */
    fun parse(raw: String?): Version? {
        val input = raw?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: return null
        val core = input.substringBefore('-')
        val prerelease = input.substringAfter('-', missingDelimiterValue = "")
        val parts = core.split('.')
        if (parts.size !in 1..3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        val prereleaseIds = prerelease
            .takeIf { it.isNotEmpty() }
            ?.split('.')
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return Version(
            major = numbers.getOrElse(0) { 0 },
            minor = numbers.getOrElse(1) { 0 },
            patch = numbers.getOrElse(2) { 0 },
            prerelease = prereleaseIds
        )
    }

    /** The installed app version. */
    fun current(): Version =
        parse(BuildConfig.VERSION_NAME) ?: Version(major = 0, minor = 0, patch = 0)

    /** SemVer precedence: negative = [a] older, zero = equal, positive = newer. */
    fun compare(a: Version, b: Version): Int {
        val core = compareNumbers(a.major, b.major)
            .takeIf { it != 0 } ?: compareNumbers(a.minor, b.minor)
            .takeIf { it != 0 } ?: compareNumbers(a.patch, b.patch)
        if (core != 0) return core
        return when {
            a.prerelease.isEmpty() && b.prerelease.isEmpty() -> 0
            a.prerelease.isEmpty() -> 1
            b.prerelease.isEmpty() -> -1
            else -> comparePrerelease(a.prerelease, b.prerelease)
        }
    }

    /** True when [candidate] is strictly newer than [current]. */
    fun isNewer(current: Version, candidate: Version): Boolean =
        compare(current, candidate) < 0

    /** e.g. `0.1.0-rc1 (Build 42)`. */
    fun displayName(versionName: String, buildCode: Int): String =
        "$versionName (Build $buildCode)"

    /** The installed app version as a display string. */
    fun currentDisplayName(): String =
        displayName(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

    private fun compareNumbers(a: Int, b: Int): Int = a.compareTo(b)

    private fun comparePrerelease(a: List<String>, b: List<String>): Int {
        val shared = minOf(a.size, b.size)
        for (i in 0 until shared) {
            val x = a[i]
            val y = b[i]
            val xNumeric = x.toIntOrNull()
            val yNumeric = y.toIntOrNull()
            val result = when {
                xNumeric != null && yNumeric != null -> xNumeric.compareTo(yNumeric)
                xNumeric != null -> -1
                yNumeric != null -> 1
                else -> x.compareTo(y)
            }
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }
}

/** Parsed semantic version. */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) {
    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    /** Canonical string form, e.g. `0.1.0-rc1`. */
    val display: String get() =
        "$major.$minor.$patch" + if (prerelease.isEmpty()) "" else "-${prerelease.joinToString(".")}"
}
