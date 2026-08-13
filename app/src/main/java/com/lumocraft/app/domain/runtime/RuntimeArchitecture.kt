package com.lumocraft.app.domain.runtime

/**
 * Supported device architectures for Java runtimes.
 * Maps to Android ABI names used by the platform.
 */
enum class RuntimeArchitecture(val abi: String) {
    ARM64_V8A("arm64-v8a"),
    ARMEABI_V7A("armeabi-v7a"),
    X86_64("x86_64");

    companion object {
        fun fromAbi(abi: String): RuntimeArchitecture? =
            entries.firstOrNull { it.abi == abi }
    }
}