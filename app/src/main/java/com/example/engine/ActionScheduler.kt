package com.example.engine

import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Timed action execution queue.
 * Handles tap, hold, swipe, scroll, and macro step execution
 * with proper timing, cancellation, and sequencing.
 */
class ActionScheduler(
    private val injector: PersistentInjector,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ActionScheduler"
    }

    // ── ACTION TYPES ──

    sealed class Action {
        data class Tap(
            val x: Float, val y: Float,
            val holdMs: Long = 50L
        ) : Action()

        data class Hold(
            val x: Float, val y: Float,
            val durationMs: Long = 500L
        ) : Action()

        data class Swipe(
            val startX: Float, val startY: Float,
            val endX: Float, val endY: Float,
            val durationMs: Long = 300L,
            val steps: Int = 20
        ) : Action()

        data class Drag(
            val startX: Float, val startY: Float,
            val endX: Float, val endY: Float,
            val durationMs: Long = 500L
        ) : Action()

        data class Scroll(
            val amount: Int, // positive = up, negative = down
            val repeatCount: Int = 1,
            val repeatDelayMs: Long = 50L
        ) : Action()

        data class Delay(
            val durationMs: Long
        ) : Action()

        data class MultiTouch(
            val points: List<PointF>,
            val holdMs: Long = 100L
        ) : Action()

        // Hold key → action starts on key down, stops on key up
        data class HoldKeyBound(
            val x: Float, val y: Float,
            val keyBinding: Int // keyCode that triggers this
        ) : Action()
    }

    // ── STATE ──

    private val actionQueue = ConcurrentLinkedQueue<Action>()
    private val isRunning = AtomicBoolean(false)
    private var currentJob: Job? = null
    private val activeHoldPointers = mutableMapOf<Int, Int>() // actionId → pointerId

    // ── EXECUTION ──

    /**
     * Queue an action for immediate execution.
     */
    fun execute(action: Action) {
        actionQueue.offer(action)
        if (!isRunning.get()) {
            processNext()
        }
    }

    /**
     * Execute a sequence of actions in order.
     */
    fun executeSequence(actions: List<Action>) {
        currentJob?.cancel()
        currentJob = scope.launch {
            isRunning.set(true)
            for (action in actions) {
                if (!isActive) break
                executeAndWait(action)
            }
            isRunning.set(false)
        }
    }

    /**
     * Cancel all pending and running actions.
     */
    fun cancelAll() {
        currentJob?.cancel()
        currentJob = null
        actionQueue.clear()
        // Release all held pointers
        activeHoldPointers.values.forEach { pid ->
            injector.touchUp(pid)
        }
        activeHoldPointers.clear()
        isRunning.set(false)
    }

    /**
     * Start a hold action that lasts until stopHold() is called.
     * Returns an ID for the hold session.
     */
    fun startHold(x: Float, y: Float): Int {
        val pointerId = injector.touchDown(x, y)
        val id = System.identityHashCode(pointerId)
        activeHoldPointers[id] = pointerId
        Log.d(TAG, "Started hold #$id at ($x, $y) pointer=$pointerId")
        return id
    }

    /**
     * Stop a hold action by ID.
     */
    fun stopHold(id: Int) {
        val pointerId = activeHoldPointers.remove(id)
        if (pointerId != null) {
            injector.touchUp(pointerId)
            Log.d(TAG, "Stopped hold #$id")
        }
    }

    /**
     * Start a swipe that can be cancelled.
     * Returns an ID for the swipe session.
     */
    fun startSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Int {
        val id = System.identityHashCode(startX.toInt() * 1000 + startY.toInt())
        scope.launch {
            val pointerId = injector.touchDown(startX, startY)
            activeHoldPointers[id] = pointerId

            val steps = (durationMs / 16).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                if (!isActive) break
                val t = i.toFloat() / steps
                val x = startX + (endX - startX) * t
                val y = startY + (endY - startY) * t
                injector.touchMove(pointerId, x, y)
                delay(16)
            }
            // Don't auto-release — caller decides
        }
        return id
    }

    /**
     * Check if an action is currently running.
     */
    fun isActive(): Boolean = isRunning.get()

    // ── INTERNAL ──

    private fun processNext() {
        val action = actionQueue.poll() ?: return
        currentJob = scope.launch {
            isRunning.set(true)
            executeAndWait(action)
            isRunning.set(false)
            if (actionQueue.isNotEmpty()) {
                processNext()
            }
        }
    }

    private suspend fun executeAndWait(action: Action) {
        when (action) {
            is Action.Tap -> {
                val pid = injector.touchDown(action.x, action.y)
                delay(action.holdMs)
                injector.touchUp(pid)
            }

            is Action.Hold -> {
                val pid = injector.touchDown(action.x, action.y)
                delay(action.durationMs)
                injector.touchUp(pid)
            }

            is Action.Swipe -> {
                val pid = injector.touchDown(action.startX, action.startY)
                val steps = action.steps
                for (i in 1..steps) {
                    if (!isActive) break
                    val t = i.toFloat() / steps
                    val x = action.startX + (action.endX - action.startX) * t
                    val y = action.startY + (action.endY - action.startY) * t
                    injector.touchMove(pid, x, y)
                    delay(action.durationMs / steps)
                }
                injector.touchUp(pid)
            }

            is Action.Drag -> {
                val pid = injector.touchDown(action.startX, action.startY)
                val steps = (action.durationMs / 16).toInt().coerceAtLeast(1)
                for (i in 1..steps) {
                    if (!isActive) break
                    val t = i.toFloat() / steps
                    val x = action.startX + (action.endX - action.startX) * t
                    val y = action.startY + (action.endY - action.startY) * t
                    injector.touchMove(pid, x, y)
                    delay(16)
                }
                injector.touchUp(pid)
            }

            is Action.Scroll -> {
                // Scroll injection via Shizuku or fallback
                for (i in 0 until action.repeatCount) {
                    if (!isActive) break
                    injectScroll(action.amount)
                    if (i < action.repeatCount - 1) {
                        delay(action.repeatDelayMs)
                    }
                }
            }

            is Action.Delay -> {
                delay(action.durationMs)
            }

            is Action.MultiTouch -> {
                val pids = action.points.map { pt -> injector.touchDown(pt.x, pt.y) }
                delay(action.holdMs)
                pids.forEach { pid -> injector.touchUp(pid) }
            }

            is Action.HoldKeyBound -> {
                // This is handled by the runtime engine, not here
                // Just a placeholder for type completeness
            }
        }
    }

    /**
     * Inject a scroll event.
     * Uses Shizuku if available, otherwise logs.
     */
    private fun injectScroll(amount: Int) {
        // TODO: Use ShizukuHiddenApi.injectInputEvent with MotionEvent for scroll
        // For now, dispatch through the engine's scroll handler
        Log.d(TAG, "Scroll: $amount")
    }
}
