package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.native.RendererProfile
import com.lumocraft.app.domain.native.RendererType

/**
 * Translates a launcher [RendererProfile] into the process-environment
 * variables that the PojavLauncher rendering stack (`libpojavexec.so` +
 * gl4es) reads at runtime.
 *
 * gl4es and pojavexec are configured through environment variables — not
 * JVM `-D` system properties — because they are plain C libraries loaded
 * by the game JVM's LWJGL, outside the JVM's property space. These are
 * merged into the process environment applied via `setenv` before
 * `JLI_Launch` (see [LaunchEnvironment.buildProcessEnvironment]).
 *
 * NOTE (device-gated): the exact values below (renderer id, GLES level,
 * mipmap mode) mirror PojavLauncher's `gladiolus` defaults but are the
 * primary knobs to iterate on from logcat on a real device. The *mapping*
 * is deterministic and unit-tested; the ideal values per device are not.
 */
object RendererEnvironment {

    /** Environment variables for the selected [profile]. */
    fun of(profile: RendererProfile): Map<String, String> {
        val renderer = when (profile.renderer) {
            RendererType.COMPATIBILITY -> "opengles2"
            RendererType.PERFORMANCE -> "opengles2"
            // gl4es' GLES3 path — heavier, only for the experimental preset.
            RendererType.EXPERIMENTAL -> "opengles3"
        }
        val glesLevel = if (profile.renderer == RendererType.EXPERIMENTAL) "3" else "2"
        return buildMap {
            put("POJAV_RENDERER", renderer)
            put("LIBGL_ES", glesLevel)
            // Emulate desktop OpenGL 2.1 for the game.
            put("LIBGL_GL", "21")
            put("LIBGL_NORMALIZE", "1")
            // gl4es mipmap mode: 3 = generate + hardware mipmaps; 0 = off.
            put("LIBGL_MIPMAP", if (profile.mipmaps > 0) "3" else "0")
            put("LIBGL_VSYNC", if (profile.vsync) "1" else "0")
            // Silence gl4es' own logging on release; raise on-device to debug.
            put("LIBGL_NOERROR", "1")
        }
    }
}
