package com.lumocraft.app.ui.game

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.kdt.pojavlaunch.utils.JREUtils

/**
 * Full-screen host for the game's render surface.
 *
 * A single [SurfaceView] provides the `Surface` that is handed to the
 * PojavLauncher rendering bridge ([JREUtils.attachSurface]); the game's
 * patched LWJGL then draws into that surface's `ANativeWindow`. The activity
 * keeps the screen on and hides the system bars for an immersive game view.
 *
 * ORDERING (device-gated): the game's LWJGL must not create its GL context
 * before a surface exists. On a real device this activity should be started
 * so the surface is created before / as the in-process JVM reaches LWJGL
 * init. The launch pipeline sequencing that guarantees this ordering is
 * exercised on-device; here the activity + surface handshake are in place.
 */
class GameActivity : ComponentActivity() {

    private lateinit var surfaceView: SurfaceView

    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            JREUtils.attachSurface(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // The bridge reads the current window dimensions when it makes the
            // GL context current; nothing to push here.
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            JREUtils.detachSurface()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(holderCallback)
        }
        setContentView(surfaceView)
    }

    override fun onDestroy() {
        surfaceView.holder.removeCallback(holderCallback)
        JREUtils.detachSurface()
        super.onDestroy()
    }
}
