package com.lumocraft.app.data.launch

import android.content.Context
import com.lumocraft.app.data.storage.StorageManager
import java.io.File

/**
 * Unpacks the patched PojavLauncher LWJGL jars bundled in the APK
 * (the `.jar` files under `assets/lwjgl/`) into
 * [StorageManager.pojavLwjglDirectory] so
 * [ClasspathBuilder] can prepend them to the game classpath.
 *
 * The jars are vendored by `tools/fetch-pojav-natives.sh` and are git-ignored,
 * so on a build without the fetch step `assets/lwjgl` is absent and this is a
 * no-op. Extraction is idempotent: a jar is rewritten only when missing or a
 * different size, so repeated launches don't re-copy.
 */
class PojavLwjglInstaller(
    private val context: Context,
    private val storage: StorageManager,
) {

    /** Copies any bundled LWJGL jars to storage. Returns the count installed. */
    fun install(): Int {
        val names = runCatching { context.assets.list(ASSET_DIR) }.getOrNull()
            ?.filter { it.endsWith(".jar") }
            ?: return 0
        if (names.isEmpty()) return 0

        val target = storage.pojavLwjglDirectory()
        target.mkdirs()
        var installed = 0
        for (name in names) {
            val out = File(target, name)
            // Jars are content-addressed by the pinned Pojav release, so an
            // existing non-empty copy is authoritative; only (re)extract when
            // absent or truncated by an interrupted earlier copy.
            if (out.isFile && out.length() > 0L) {
                installed++
                continue
            }
            runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                installed++
            }.onFailure { out.delete() }
        }
        return installed
    }

    private companion object {
        const val ASSET_DIR = "lwjgl"
    }
}
