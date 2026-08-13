package com.lumocraft.app.domain.loader

/**
 * An installed loader together with its live health status, as shown in
 * the Loader Manager screen.
 */
data class LoaderInstance(
    val metadata: LoaderMetadata,
    val status: LoaderStatus
) {
    val instanceId: String get() = metadata.instanceId
}