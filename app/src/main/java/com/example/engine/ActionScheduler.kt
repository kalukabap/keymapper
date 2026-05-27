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

    sealed class Action {
        data class Tap(val x: Float, val y: Float, val holdMs: Long = 50L) : Action()
        data class Hold(val x: Float, val y: Float, val durationMs: Long = 500L) : Action()
        data class Swipe(
            val startX: Float, val startY: Float,
            val endX: Float, val endY: Float,
            val durationMs: Long = 300L, val steps: Int = 20
        ) : Action()
        data class Drag(
            val startX: Float, val startY: Float,
            val endX: Float, val endY: Float,
            val durationMs: Long = 500L
        ) : Action()
        data class Scroll(val amount: Int, val repeatCount: Int = 1, val repeatDelayMs: Long = 50L) : Action()
        data class Delay(val durationMs: Long) : Action()
        data class MultiTouch(val points: List<PointF>, val holdMs: Long = 100L) : Action()
    }

    private val actionQueue = ConcurrentLinkedQueue<Action>()
    private val isRunning = AtomicBoolean(false)
    private var currentJob: Job? = null
    private val activeHoldPointers = mutableMapOf<Int, Int>()

    fun execute(action: Action) {
        actionQueue.offer(action)
        if (!isRunning.get()) processNext()
    }

    fun executeSequence(actions: List<Action>) {
        currentJob?.cancel()
        currentJob = scope.launch {
            isRunning.set(true)
            for (action in actions) {
                ensureActive()
                executeAndWait(action)
            }
            isRunning.set(false)
        }
    }

    fun cancelAll() {
        currentJob?.cancel()
        currentJob = null
        actionQueue.clear()
        activeHoldPointers.values.forEach { pid -> injector.touchUp(pid) }
        activeHoldPointers.clear()
        isRunning.set(false)
    }

    fun startHold(x: Float, y: Float): Int {
        val pointerId = injector.touchDown(x, y)
        val id = System.identityHashCode(pointerId)
        activeHoldPointers[id] = pointerId
        return id
    }

    fun stopHold(id: Int) {
        val pointerId = activeHoldPointers.remove(id)
        pointerId?.let { injector.touchUp(it) }
    }

    fun startSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Int {
        val id = System.identityHashCode(startX.toInt() * 1000 + startY.toInt())
        scope.launch {
            val pointerId = injector.touchDown(startX, startY)
            activeHoldPointers[id] = pointerId
            val steps = (durationMs / 16).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                ensureActive()
                val t = i.toFloat() / steps
                injector.touchMove(pointerId, startX + (endX - startX) * t, startY + (endY - startY) * t)
                delay(16)
            }
        }
        return id
    }

    fun isActive(): Boolean = isRunning.get()

    private fun processNext() {
        val action = actionQueue.poll() ?: return
        currentJob = scope.launch {
            isRunning.set(true)
            executeAndWait(action)
            isRunning.set(false)
            if (actionQueue.isNotEmpty()) processNext()
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
                for (i in 1..action.steps) {
                    ensureActive()
                    val t = i.toFloat() / action.steps
                    injector.touchMove(pid, action.startX + (action.endX - action.startX) * t, action.startY + (action.endY - action.startY) * t)
                    delay(action.durationMs / action.steps)
                }
                injector.touchUp(pid)
            }
            is Action.Drag -> {
                val pid = injector.touchDown(action.startX, action.startY)
                val steps = (action.durationMs / 16).toInt().coerceAtLeast(1)
                for (i in 1..steps) {
                    ensureActive()
                    val t = i.toFloat() / steps
                    injector.touchMove(pid, action.startX + (action.endX - action.startX) * t, action.startY + (action.endY - action.startY) * t)
                    delay(16)
                }
                injector.touchUp(pid)
            }
            is Action.Scroll -> {
                for (i in 0 until action.repeatCount) {
                    ensureActive()
                    injectScroll(action.amount)
                    if (i < action.repeatCount - 1) delay(action.repeatDelayMs)
                }
            }
            is Action.Delay -> delay(action.durationMs)
            is Action.MultiTouch -> {
                val pids = action.points.map { pt -> injector.touchDown(pt.x, pt.y) }
                delay(action.holdMs)
                pids.forEach { pid -> injector.touchUp(pid) }
            }
        }
    }

    private fun injectScroll(amount: Int) {
        try {
            if (com.example.shizuku.ShizukuHiddenApi.isAvailable()) {
                com.example.shizuku.ShizukuHiddenApi.injectScrollEvent(amount.toFloat())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scroll injection failed", e)
        }
    }
}
