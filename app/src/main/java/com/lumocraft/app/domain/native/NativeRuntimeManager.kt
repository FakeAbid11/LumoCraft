package com.lumocraft.app.domain.native

import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point for native runtime compatibility: locates the
 * native jars a version needs, extracts them per-architecture (cached
 * and incremental), verifies them, and exposes the JNI environment the
 * launch pipeline injects into the Java process.
 *
 * The interface is deliberately small and version-agnostic so later
 * phases (Fabric, Forge, Sodium, OptiFine, custom renderers) can extend
 * the native surface without changing this contract.
 */
interface NativeRuntimeManager {

    /** Status of the most recently prepared version. */
    val status: StateFlow<NativeStatus>

    /** The device architecture natives are prepared for. */
    fun architecture(): RuntimeArchitecture

    /** Architecture-specific native folder for a version. */
    fun nativeDirectory(versionId: String): File

    /**
     * The APK's packaged native-library directory holding the bundled
     * PojavLauncher rendering natives (libpojavexec, gl4es, GLFW stub).
     * The launch pipeline puts this on the process `LD_LIBRARY_PATH` so the
     * game JVM's LWJGL can load the rendering bridge.
     */
    fun renderingNativesDirectory(): File

    /**
     * JNI environment for the Java process: java.library.path and
     * org.lwjgl.librarypath, both pointing at [nativeDirectory]. No
     * temporary folders are hardcoded — everything lives under the
     * launcher storage layout.
     */
    fun jniEnvironment(versionId: String): Map<String, String>

    /**
     * Extracts and verifies natives for [versionId]. Reuses a previous
     * extraction when stamps are intact; re-extracts incrementally
     * otherwise. Fails with an actionable [NativeException] when the
     * architecture mismatches or the extraction is corrupted.
     */
    suspend fun prepare(versionId: String): Result<NativeVerificationReport>

    /** Deep check of the extracted natives (files + sizes + arch). */
    suspend fun verify(versionId: String): Result<NativeVerificationReport>

    /** Cheap, synchronous readiness probe (stamp presence), no hashing. */
    fun statusOf(versionId: String): NativeStatus

    /** Persisted renderer profile. */
    fun rendererProfile(): RendererProfile

    fun saveRendererProfile(profile: RendererProfile)
}