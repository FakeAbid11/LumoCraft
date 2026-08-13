package com.lumocraft.app.domain.account

/**
 * A local launcher profile (offline account).
 * Not a Microsoft/Mojang credential — simply a username saved on device.
 */
data class OfflineAccount(
    val id: String,
    val username: String,
    val createdAt: Long,
    val isSelected: Boolean
)
