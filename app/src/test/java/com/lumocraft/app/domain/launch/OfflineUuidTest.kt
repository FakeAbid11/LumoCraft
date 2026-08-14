package com.lumocraft.app.domain.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OfflineUuidTest {

    @Test
    fun `is deterministic for the same username`() {
        assertEquals(OfflineUuid.forUsername("Steve"), OfflineUuid.forUsername("Steve"))
        assertEquals(OfflineUuid.forUsername("alex_"), OfflineUuid.forUsername("alex_"))
    }

    @Test
    fun `different usernames yield different uuids`() {
        assertNotEquals(OfflineUuid.forUsername("Steve"), OfflineUuid.forUsername("Alex"))
    }

    @Test
    fun `is version 3 and variant rfc4122`() {
        val uuid = OfflineUuid.forUsername("Steve")
        // Version nibble (13th hex char) must be 3.
        assertEquals('3', uuid[12])
        // Variant: 4th hex char of the 3rd group must be 8/9/a/b.
        val variant = uuid[16].lowercaseChar()
        assert(variant in "89ab")
    }

    @Test
    fun `matches the reference offline uuid for Notch`() {
        // MD5("OfflinePlayer:Notch") with version-3 + RFC 4122 bits,
        // computed once and pinned to guard against regressions.
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", OfflineUuid.forUsername("Notch"))
    }

    @Test
    fun `output is 32 lowercase hex chars without dashes`() {
        val uuid = OfflineUuid.forUsername("TestUser123")
        assertEquals(32, uuid.length)
        assert(!uuid.contains('-'))
        assertEquals(uuid.lowercase(), uuid)
        assert(uuid.all { it in "0123456789abcdef" })
    }
}