package com.lumocraft.app.ui.game

import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lumocraft.app.LumoCraftApplication
import com.lumocraft.app.data.launch.GameSurfaceGate
import com.lumocraft.app.domain.input.InputManager
import com.lumocraft.app.domain.input.RawTouch
import com.lumocraft.app.domain.input.TouchActionKind
import com.lumocraft.app.domain.launch.LaunchState
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.utils.JREUtils

/**
 * Full-screen host for the game's render surface.
 *
 * A single [SurfaceView] provides the `Surface` that is handed to the
 * PojavLauncher rendering bridge ([JREUtils.attachSurface]) and published to
 * the [GameSurfaceGate]; the game's patched LWJGL then draws into that
 * surface's `ANativeWindow`. The activity keeps the screen on and hides the
 * system bars for an immersive game view.
 *
 * ORDERING: the game's LWJGL must not create its GL context before a surface
 * exists. This activity is started by the launch flow *before* the in-process
 * JVM is started, and the pipeline waits on [GameSurfaceGate] until
 * [SurfaceHolder.Callback.surfaceCreated] here publishes the surface — so the
 * "surface before LWJGL init" ordering is guaranteed. The full render path is
 * still device-gated (it needs the vendored PojavLauncher natives), but the
 * sequencing is now enforced rather than deferred.
 *
 * The activity closes itself when the launch session leaves its running
 * states (finished, failed or idle), returning the user to the launcher.
 */
class GameActivity : ComponentActivity() {

    private lateinit var surfaceView: SurfaceView

    private val app: LumoCraftApplication
        get() = application as LumoCraftApplication

    private val gate: GameSurfaceGate
        get() = app.gameSurfaceGate

    private val inputManager: InputManager
        get() = app.inputManager

    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            // Hand the live surface to the native bridge and unblock the
            // pipeline, which is parked just before starting the JVM.
            JREUtils.attachSurface(holder.surface)
            gate.provideSurface(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Feed the render surface size into the input pipeline so touch
            // coordinates map correctly; the bridge reads window dimensions
            // itself when it makes the GL context current.
            inputManager.setSurfaceSize(width, height)
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

        // Close the game view once the session is no longer running, so the
        // user lands back on the launcher instead of a dead surface.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.launchPipeline.state.collect { progress ->
                    if (progress.state in TERMINAL_STATES) finish()
                }
            }
        }
    }

    /**
     * Routes raw pointer input through the existing input pipeline
     * ([InputManager.touchMapper] → recognized gestures →
     * [InputManager.handleGesture]), which drives the virtual mouse / actions
     * the game bridge consumes. Delivery of the resulting mouse/key events to
     * the game via the native GLFW callbacks remains device-gated.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val kind = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> TouchActionKind.DOWN
            MotionEvent.ACTION_MOVE -> TouchActionKind.MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> TouchActionKind.UP
            MotionEvent.ACTION_CANCEL -> TouchActionKind.CANCEL
            else -> return super.onTouchEvent(event)
        }
        val index = event.actionIndex
        val raw = RawTouch(
            x = event.getX(index),
            y = event.getY(index),
            action = kind,
            pointerId = event.getPointerId(index),
            timestampMs = event.eventTime
        )
        inputManager.touchMapper.feed(raw).forEach(inputManager::handleGesture)
        return true
    }

    override fun onDestroy() {
        surfaceView.holder.removeCallback(holderCallback)
        JREUtils.detachSurface()
        inputManager.touchMapper.reset()
        gate.clear()
        super.onDestroy()
    }

    private companion object {
        val TERMINAL_STATES = setOf(
            LaunchState.FINISHED,
            LaunchState.FAILED,
            LaunchState.IDLE
        )
    }
}
