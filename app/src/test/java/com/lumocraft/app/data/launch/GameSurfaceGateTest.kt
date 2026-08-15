package com.lumocraft.app.data.launch

import android.graphics.SurfaceTexture
import android.view.Surface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the surface-before-LWJGL gate: the pipeline only waits when a
 * surface is expected, times out non-fatally when none arrives, and returns
 * the surface once provided.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameSurfaceGateTest {

    @Test
    fun `no surface is expected until announced`() {
        val gate = GameSurfaceGate()
        assertFalse(gate.isSurfaceExpected)
        gate.expectSurface()
        assertTrue(gate.isSurfaceExpected)
        gate.clear()
        assertFalse(gate.isSurfaceExpected)
    }

    @Test
    fun `awaitSurface times out to null when none is provided`() = runBlocking {
        val gate = GameSurfaceGate()
        assertNull(gate.awaitSurface(timeoutMs = 50))
    }

    @Test
    fun `awaitSurface returns the provided surface`() = runBlocking {
        val gate = GameSurfaceGate()
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        try {
            gate.provideSurface(surface)
            assertEquals(surface, gate.awaitSurface(timeoutMs = 1_000))
        } finally {
            surface.release()
            texture.release()
        }
    }

    @Test
    fun `clear drops a provided surface`() = runBlocking {
        val gate = GameSurfaceGate()
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        try {
            gate.provideSurface(surface)
            gate.clear()
            assertNull(gate.awaitSurface(timeoutMs = 50))
        } finally {
            surface.release()
            texture.release()
        }
    }
}
