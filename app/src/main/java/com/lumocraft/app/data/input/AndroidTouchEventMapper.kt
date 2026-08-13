package com.lumocraft.app.data.input

import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import com.lumocraft.app.domain.input.DragState
import com.lumocraft.app.domain.input.Gesture
import com.lumocraft.app.domain.input.RawTouch
import com.lumocraft.app.domain.input.SwipeDirection
import com.lumocraft.app.domain.input.TouchActionKind
import com.lumocraft.app.domain.input.TouchConfig
import com.lumocraft.app.domain.input.TouchEventMapper
import kotlin.math.sqrt

/**
 * Reference touch pipeline: normalizes raw pointers, tracks every
 * pointer, recognizes tap/double tap/long press/drag/swipe and the
 * two-finger variants. Long-press timing uses a handler; everything
 * else is pure math with a reused output list and no per-event maps.
 */
class AndroidTouchEventMapper : TouchEventMapper {

    private var config = TouchConfig()
    private val pointers = SparseArray<PointerState>()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressTasks = SparseArray<Runnable>()
    private val output = ArrayList<Gesture>(4)

    private var lastTap: TapCandidate? = null
    private var twoFinger: TwoFingerState? = null

    override fun feed(event: RawTouch): List<Gesture> {
        output.clear()
        when (event.action) {
            TouchActionKind.DOWN -> onDown(event)
            TouchActionKind.MOVE -> onMove(event)
            TouchActionKind.UP -> onUp(event, cancelled = false)
            TouchActionKind.CANCEL -> onCancel(event)
        }
        return output
    }

    override fun reset() {
        pointers.clear()
        twoFinger = null
        lastTap = null
        for (i in 0 until longPressTasks.size()) {
            longPressHandler.removeCallbacks(longPressTasks.valueAt(i))
        }
        longPressTasks.clear()
    }

    override fun updateConfig(config: TouchConfig) {
        this.config = config
    }

    private fun onDown(event: RawTouch) {
        val ps = PointerState(event.pointerId, event.x, event.y, event.timestampMs)
        pointers.put(event.pointerId, ps)

        if (pointers.size() == 2) {
            val first = pointers.valueAt(0)
            twoFinger = TwoFingerState(
                firstId = first.id,
                secondId = event.pointerId,
                startTime = event.timestampMs,
                firstX = first.lastX,
                firstY = first.lastY,
                secondX = event.x,
                secondY = event.y
            )
            cancelLongPress(first.id)
            cancelLongPress(event.pointerId)
            first.dragging = false
            first.tapSuppressed = true
            emitMultiDrag(MultiDragSnapshot.STARTED)
        } else {
            scheduleLongPress(event.pointerId)
        }
    }

    private fun onMove(event: RawTouch) {
        val ps = pointers.get(event.pointerId) ?: return
        val dx = event.x - ps.lastX
        val dy = event.y - ps.lastY
        ps.previousX = ps.lastX
        ps.previousY = ps.lastY
        ps.previousTime = ps.lastTime
        ps.lastX = event.x
        ps.lastY = event.y
        ps.lastTime = event.timestampMs

        if (twoFinger != null && pointers.size() >= 2) {
            if (dx * dx + dy * dy > MULTI_MOVE_EPSILON_SQ) twoFinger?.didMove = true
            emitMultiDrag(MultiDragSnapshot.MOVED)
            return
        }

        if (ps.dragging) {
            emitDrag(ps, DragState.MOVED)
        } else if (!ps.longPressFired) {
            val fromDownX = event.x - ps.downX
            val fromDownY = event.y - ps.downY
            if (fromDownX * fromDownX + fromDownY * fromDownY > config.tapSlop * config.tapSlop) {
                cancelLongPress(event.pointerId)
                ps.dragging = true
                emitDrag(ps, DragState.STARTED)
                emitDrag(ps, DragState.MOVED)
            }
        }
    }

    private fun onUp(event: RawTouch, cancelled: Boolean) {
        val ps = pointers.get(event.pointerId) ?: return
        val multi = twoFinger

        if (multi != null && pointers.size() >= 2) {
            if (pointers.size() > 2) {
                pointers.remove(event.pointerId)
                return
            }
            emitMultiDrag(MultiDragSnapshot.MOVED)
            emitMultiDrag(MultiDragSnapshot.ENDED)
            val short = event.timestampMs - multi.startTime <= config.multiTouchIntervalMs
            if (!multi.didMove && short) {
                val otherIndex = if (pointers.keyAt(0) == event.pointerId && pointers.size() > 1) 1 else 0
                val other = pointers.valueAt(otherIndex)
                output.add(
                    Gesture.MultiTap(
                        pointerId = event.pointerId,
                        x = (event.x + other.lastX) / 2f,
                        y = (event.y + other.lastY) / 2f,
                        pointerCount = 2
                    )
                )
            }
            pointers.remove(event.pointerId)
            twoFinger = null
            if (pointers.size() == 1) pointers.valueAt(0).tapSuppressed = true
            return
        }

        pointers.remove(event.pointerId)
        if (pointers.size() == 0) {
            twoFinger = null
            lastTap = null
        }

        if (cancelled) {
            cancelLongPress(event.pointerId)
            if (ps.dragging) output.add(dragFor(ps, DragState.CANCELLED))
            return
        }

        cancelLongPress(event.pointerId)

        if (ps.dragging) {
            output.add(dragFor(ps, DragState.ENDED))
            emitSwipeIfFast(ps, event)
            return
        }

        if (ps.longPressFired || ps.tapSuppressed) return

        val tap = TapCandidate(event.pointerId, event.x, event.y, event.timestampMs)
        val prev = lastTap
        if (prev != null &&
            tap.time - prev.time <= config.doubleTapWindowMs &&
            distance(tap.x, tap.y, prev.x, prev.y) <= config.tapSlop * 2f
        ) {
            output.add(Gesture.DoubleTap(event.pointerId, tap.x, tap.y))
            lastTap = null
        } else {
            output.add(Gesture.Tap(event.pointerId, tap.x, tap.y))
            lastTap = tap
        }
    }

    private fun onCancel(event: RawTouch) {
        val ps = pointers.get(event.pointerId)
        if (ps != null) {
            cancelLongPress(event.pointerId)
            if (ps.dragging) output.add(dragFor(ps, DragState.CANCELLED))
        }
        pointers.remove(event.pointerId)
        if (pointers.size() < 2) twoFinger = null
        if (pointers.size() == 0) lastTap = null
    }

    private fun scheduleLongPress(pointerId: Int) {
        cancelLongPress(pointerId)
        val task = Runnable { fireLongPress(pointerId) }
        longPressTasks.put(pointerId, task)
        longPressHandler.postDelayed(task, config.longPressMs)
    }

    private fun cancelLongPress(pointerId: Int) {
        val task = longPressTasks.get(pointerId) ?: return
        longPressHandler.removeCallbacks(task)
        longPressTasks.remove(pointerId)
    }

    private fun fireLongPress(pointerId: Int) {
        val ps = pointers.get(pointerId) ?: return
        if (ps.dragging || ps.longPressFired || twoFinger != null) return
        ps.longPressFired = true
        output.add(Gesture.LongPress(pointerId, ps.downX, ps.downY))
    }

    private fun emitDrag(ps: PointerState, state: DragState) {
        output.add(dragFor(ps, state))
    }

    private fun dragFor(ps: PointerState, state: DragState): Gesture.Drag = Gesture.Drag(
        pointerId = ps.id,
        x = ps.lastX,
        y = ps.lastY,
        startX = ps.downX,
        startY = ps.downY,
        dx = ps.lastX - ps.previousX,
        dy = ps.lastY - ps.previousY,
        state = state
    )

    private fun emitSwipeIfFast(ps: PointerState, event: RawTouch) {
        val dt = (event.timestampMs - ps.previousTime).toFloat()
        if (dt <= 0f) return
        val vx = (event.x - ps.previousX) / dt * 1000f
        val vy = (event.y - ps.previousY) / dt * 1000f
        if (sqrt(vx * vx + vy * vy) < config.swipeVelocityThreshold) return
        val movedX = event.x - ps.downX
        val movedY = event.y - ps.downY
        val direction = when {
            kotlin.math.abs(movedX) > kotlin.math.abs(movedY) ->
                if (movedX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            else -> if (movedY > 0) SwipeDirection.DOWN else SwipeDirection.UP
        }
        output.add(Gesture.Swipe(event.pointerId, event.x, event.y, vx, vy, direction))
    }

    private fun emitMultiDrag(state: MultiDragSnapshot) {
        val multi = twoFinger ?: return
        val first = pointers.get(multi.firstId) ?: return
        val second = pointers.get(multi.secondId) ?: return
        val dx = (first.lastX - multi.lastFirstX + second.lastX - multi.lastSecondX) / 2f
        val dy = (first.lastY - multi.lastFirstY + second.lastY - multi.lastSecondY) / 2f
        val dragState = when (state) {
            MultiDragSnapshot.STARTED -> DragState.STARTED
            MultiDragSnapshot.MOVED -> DragState.MOVED
            MultiDragSnapshot.ENDED -> DragState.ENDED
        }
        output.add(
            Gesture.MultiDrag(
                pointerId = multi.secondId,
                firstX = first.lastX,
                firstY = first.lastY,
                secondX = second.lastX,
                secondY = second.lastY,
                dx = dx,
                dy = dy,
                state = dragState
            )
        )
        multi.lastFirstX = first.lastX
        multi.lastFirstY = first.lastY
        multi.lastSecondX = second.lastX
        multi.lastSecondY = second.lastY
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))

    /** Per-pointer tracker; kept in a [SparseArray], reused, no boxing. */
    private class PointerState(
        val id: Int,
        val downX: Float,
        val downY: Float,
        val downTime: Long
    ) {
        var lastX: Float = downX
        var lastY: Float = downY
        var lastTime: Long = downTime
        var previousX: Float = downX
        var previousY: Float = downY
        var previousTime: Long = downTime
        var dragging: Boolean = false
        var longPressFired: Boolean = false
        var tapSuppressed: Boolean = false
    }

    private data class TapCandidate(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val time: Long
    )

    private class TwoFingerState(
        val firstId: Int,
        val secondId: Int,
        val startTime: Long,
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float
    ) {
        var lastFirstX: Float = firstX
        var lastFirstY: Float = firstY
        var lastSecondX: Float = secondX
        var lastSecondY: Float = secondY
        var didMove: Boolean = false
    }

    private enum class MultiDragSnapshot { STARTED, MOVED, ENDED }

    private companion object {
        const val MULTI_MOVE_EPSILON_SQ = 4f
    }
}