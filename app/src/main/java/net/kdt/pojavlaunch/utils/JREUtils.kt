package net.kdt.pojavlaunch.utils

import android.util.Log
import android.view.Surface

/**
 * Thin bridge to PojavLauncher's rendering native (`libpojavexec.so`).
 *
 * The package, class and method names here are **load-bearing**: they must
 * match PojavLauncher's `net.kdt.pojavlaunch.utils.JREUtils` exactly, because
 * the vendored `libpojavexec.so` registers its JNI entry points by that fully
 * qualified name (`Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow`
 * / `..._releaseBridgeWindow`, see PojavLauncher's `egl_bridge.c`). Renaming
 * the package would leave those symbols unresolved at runtime.
 *
 * The bridge runs in the launcher's own process — the same process the game
 * JVM is launched into in-process — so the [android.view.Surface] handed to
 * [setupBridgeWindow] becomes the `ANativeWindow` that the game's patched
 * LWJGL draws into via `pojavMakeCurrent` / `pojavSwapBuffers`.
 *
 * DEVICE-GATED: [libraryAvailable] is false until the natives are vendored by
 * `tools/fetch-pojav-natives.sh` (they are git-ignored, not committed). On CI
 * builds without the fetch step the APK simply ships without the bridge and
 * these calls are no-ops guarded by [ensureLoaded]; on a real device with the
 * natives present the surface handshake is exercised.
 */
object JREUtils {

    private const val TAG = "JREUtils"

    /** True once [libpojavexec.so] loaded successfully. */
    @Volatile
    var libraryAvailable: Boolean = false
        private set

    /**
     * Loads the rendering bridge native. Safe to call repeatedly; the first
     * successful load flips [libraryAvailable]. Returns the load error message
     * (e.g. when the natives have not been vendored) or null on success.
     */
    @Synchronized
    fun ensureLoaded(): String? {
        if (libraryAvailable) return null
        val error = runCatching { System.loadLibrary(BRIDGE_LIBRARY) }.exceptionOrNull()
        return if (error == null) {
            libraryAvailable = true
            null
        } else {
            Log.w(TAG, "Rendering bridge '$BRIDGE_LIBRARY' unavailable: ${error.message}")
            error.message ?: "unknown load failure"
        }
    }

    /**
     * Hands the render [surface] to the native bridge. No-op (returns false)
     * if the bridge native is not present. Must be called on a live surface
     * before the game's LWJGL creates its GL context.
     */
    fun attachSurface(surface: Surface): Boolean {
        if (ensureLoaded() != null) return false
        return runCatching { setupBridgeWindow(surface) }.isSuccess
    }

    /** Releases the native window. Safe to call when nothing is attached. */
    fun detachSurface() {
        if (!libraryAvailable) return
        runCatching { releaseBridgeWindow() }
    }

    /**
     * PojavLauncher JNI entry points, implemented in the vendored
     * `libpojavexec.so`. The JNI symbol name
     * (`Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow`) is
     * identical whether the Kotlin method is static or an object-instance
     * method, and Pojav's bridge ignores the receiver arg (`ABI_COMPAT
     * jclass`), so the singleton `object` form binds correctly.
     */
    external fun setupBridgeWindow(surface: Surface)

    external fun releaseBridgeWindow()

    private const val BRIDGE_LIBRARY = "pojavexec"
}
