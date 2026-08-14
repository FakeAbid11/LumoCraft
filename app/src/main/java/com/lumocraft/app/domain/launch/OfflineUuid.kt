package com.lumocraft.app.domain.launch

import java.security.MessageDigest
/**
 * Deterministic offline UUID generation (version-3 style, MD5).
 * Follows the classic "OfflinePlayer:<name>" convention used by Mojang's
 * own launcher, so the same username always yields the same UUID across
 * devices, servers and launchers.
 */
object OfflineUuid {

    /** 32-char lowercase hex UUID (no dashes), as expected by --uuid. */
    fun forUsername(username: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(("OfflinePlayer:$username").toByteArray(Charsets.UTF_8))
        // Version 3 (name-based, MD5) + RFC 4122 variant bits.
        digest[6] = (digest[6].toInt() and 0x0F or 0x30).toByte()
        digest[8] = (digest[8].toInt() and 0x3F or 0x80).toByte()
        return digest.joinToString("") { "%02x".format(it) }
    }
}