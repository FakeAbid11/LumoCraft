package com.lumocraft.app.domain.launch

import com.lumocraft.app.domain.account.OfflineAccount
import com.lumocraft.app.domain.runtime.JvmConfiguration
import com.lumocraft.app.domain.runtime.RuntimeInfo
import java.io.File

/**
 * Everything the launch pipeline needs to start one game session.
 * Built by the UI layer and passed to [LaunchPipeline.launch]; the
 * pipeline never touches repositories or the UI itself.
 */
data class LaunchContext(
    val account: OfflineAccount,
    val versionId: String,
    val runtime: RuntimeInfo,
    val gameDirectory: File,
    val jvmConfiguration: JvmConfiguration = JvmConfiguration()
)