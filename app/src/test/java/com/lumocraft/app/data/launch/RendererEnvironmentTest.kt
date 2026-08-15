package com.lumocraft.app.data.launch

import com.lumocraft.app.domain.native.RendererProfile
import com.lumocraft.app.domain.native.RendererType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM tests for the RendererProfile → gl4es/pojav env mapping. */
class RendererEnvironmentTest {

    @Test
    fun `compatibility preset maps to opengles2`() {
        val env = RendererEnvironment.of(RendererProfile.preset(RendererType.COMPATIBILITY))
        assertEquals("opengles2", env["POJAV_RENDERER"])
        assertEquals("2", env["LIBGL_ES"])
        assertEquals("21", env["LIBGL_GL"])
    }

    @Test
    fun `experimental preset raises the gles level`() {
        val env = RendererEnvironment.of(RendererProfile.preset(RendererType.EXPERIMENTAL))
        assertEquals("opengles3", env["POJAV_RENDERER"])
        assertEquals("3", env["LIBGL_ES"])
    }

    @Test
    fun `vsync and mipmaps follow the profile`() {
        val on = RendererEnvironment.of(RendererProfile(vsync = true, mipmaps = 4))
        assertEquals("1", on["LIBGL_VSYNC"])
        assertEquals("3", on["LIBGL_MIPMAP"])

        val off = RendererEnvironment.of(RendererProfile(vsync = false, mipmaps = 0))
        assertEquals("0", off["LIBGL_VSYNC"])
        assertEquals("0", off["LIBGL_MIPMAP"])
    }
}
