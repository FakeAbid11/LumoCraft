package com.lumocraft.app.core.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionManagerTest {

    @Test
    fun `parse tolerates leading v and prerelease suffixes`() {
        assertEquals(Version(0, 1, 0), VersionManager.parse("v0.1.0"))
        assertEquals(Version(0, 1, 0, listOf("rc1")), VersionManager.parse("0.1.0-rc1"))
        assertEquals(Version(0, 1, 0, listOf("rc", "1")), VersionManager.parse("v0.1.0-rc.1"))
        assertEquals(Version(1, 2, 3), VersionManager.parse("1.2.3  ").also {
            assertTrue(it != null)
        })
    }

    @Test
    fun `parse rejects invalid input`() {
        assertNull(VersionManager.parse(null))
        assertNull(VersionManager.parse(""))
        assertNull(VersionManager.parse("v"))
        assertNull(VersionManager.parse("1.2.x"))
        assertNull(VersionManager.parse("1.2.3.4"))
    }

    @Test
    fun `compare follows semver precedence`() {
        assertTrue(VersionManager.compare(Version(0, 1, 0), Version(0, 2, 0)) < 0)
        assertTrue(VersionManager.compare(Version(1, 0, 0), Version(0, 9, 9)) > 0)
        assertTrue(VersionManager.compare(Version(0, 1, 0), Version(0, 1, 0)) == 0)
        // A release is newer than any prerelease of the same core.
        assertTrue(VersionManager.compare(Version(0, 1, 0, listOf("rc1")), Version(0, 1, 0)) < 0)
        // Numeric prerelease identifiers compare numerically.
        assertTrue(
            VersionManager.compare(
                Version(0, 1, 0, listOf("rc", "2")),
                Version(0, 1, 0, listOf("rc", "10"))
            ) < 0
        )
        // Precedence is determined by the first differing identifier.
        assertTrue(VersionManager.compare(Version(0, 1, 0, listOf("alpha")), Version(0, 1, 0, listOf("beta"))) < 0)
    }

    @Test
    fun `isNewer only accepts strictly newer versions`() {
        assertTrue(VersionManager.isNewer(Version(0, 1, 0), Version(0, 1, 1)))
        assertFalse(VersionManager.isNewer(Version(0, 2, 0), Version(0, 1, 1)))
        assertFalse(VersionManager.isNewer(Version(0, 1, 0), Version(0, 1, 0)))
    }

    @Test
    fun `versionCodeFromTags grows monotonically with releases`() {
        val tags = listOf("v0.1.0", "v0.1.1", "v0.2.0")
        assertEquals(1, VersionManager.versionCodeFromTags(Version(0, 1, 0), tags))
        assertEquals(2, VersionManager.versionCodeFromTags(Version(0, 1, 1), tags))
        assertEquals(3, VersionManager.versionCodeFromTags(Version(0, 2, 0), tags))
    }

    @Test
    fun `versionCodeFromTags counts prereleases and falls back to one`() {
        val tags = listOf("v0.1.0-rc1", "v0.1.0")
        assertEquals(1, VersionManager.versionCodeFromTags(Version(0, 1, 0, listOf("rc1")), tags))
        assertEquals(2, VersionManager.versionCodeFromTags(Version(0, 1, 0), tags))
        // No comparable tags: never below 1.
        assertEquals(1, VersionManager.versionCodeFromTags(Version(0, 5, 0), emptyList()))
        assertEquals(1, VersionManager.versionCodeFromTags(Version(0, 5, 0), listOf("not-a-version")))
    }

    @Test
    fun `isValid accepts only parseable semver`() {
        assertTrue(VersionManager.isValid("v0.1.0"))
        assertTrue(VersionManager.isValid("1.2.3-beta.1"))
        assertFalse(VersionManager.isValid("banana"))
        assertFalse(VersionManager.isValid(""))
        assertFalse(VersionManager.isValid("1.2.x"))
    }

    @Test
    fun `display and parse round trip`() {
        val version = Version(1, 2, 3, listOf("rc", "2"))
        assertEquals(version, VersionManager.parse(version.display))
        assertEquals("1.2.3-rc.2 (Build 42)", VersionManager.displayName("1.2.3-rc.2", 42))
    }
}