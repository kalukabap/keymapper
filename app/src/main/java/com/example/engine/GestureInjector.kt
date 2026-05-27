package com.example.engine

import android.content.res.Resources
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.example.data.ActionSequence
import com.example.data.KeyMapping
import com.example.data.MacroAction
import com.example.service.AccessibilityTouchService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.*

/**
 * Implements ActionExecutor by bridging to AccessibilityTouchService gesture injection.
 *
 * This is the ONLY class that touches AccessibilityTouchService for injection.
 * The engine never directly calls gesture APIs.
 */
class GestureInjector(
    private val resources: Resources
) : ActionExecutor {

    companion object {
        private const val TAG = "GestureInjector"
    }

    private val screenW: Float get() = resources.displayMetrics.widthPixels.toFloat()
    private val screenH: Float get() = resources.displayMetrics.heightPixels.toFloat()

    private val moshi = Moshi.Builder().build()
    private val macroListAdapter = moshi.adapter<List<MacroAction>>(
        Types.newParameterizedType(List::class.java, MacroAction::class.java)
    )

    private val activeHolds = mutableMapOf<Int, Job>() // mappingId → hold job
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── TAP ──

    override fun onTap(mapping: KeyMapping) {
        val px = percentToPx(mapping.xPercent, screenW)
        val py = percentToPy(mapping.yPercent, screenH)
        Log.d(TAG, "TAP at ($px, $py) for key=${mapping.keyName}")
        AccessibilityTouchService.performTap(px, py)
    }

    // ── SWIPE ──

    override fun onSwipe(mapping: KeyMapping) {
        val px = percentToPx(mapping.xPercent, screenW)
        val py = percentToPy(mapping.yPercent, screenH)
        val endX = px + mapping.swipeDx
        val endY = py + mapping.swipeDy
        val duration = mapping.swipeDurationMs.coerceAtLeast(10L)
        Log.d(TAG, "SWIPE from ($px, $py) to ($endX, $endY) duration=${duration}ms")
        AccessibilityTouchService.performSwipe(px, py, endX, endY, duration)
    }

    // ── HOLD START / RELEASE ──

    override fun onHoldStart(mapping: KeyMapping) {
        val px = percentToPx(mapping.xPercent, screenW)
        val py = percentToPy(mapping.yPercent, screenH)
        Log.d(TAG, "HOLD_START at ($px, $py) for key=${mapping.keyName}")

        // Start a persistent hold gesture
        val path = Path().apply { moveTo(px, py) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
            path, 0L, Long.MAX_VALUE // indefinite hold
        )
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val service = AccessibilityTouchService.getSharedInstance()
        if (service != null) {
            service.dispatchGesture(gesture, null, null)
            // Track this hold for cleanup
            activeHolds[mapping.id] = scope.launch {
                // Keep alive — the gesture framework handles timing
            }
        }
    }

    override fun onHoldRelease(mapping: KeyMapping) {
        Log.d(TAG, "HOLD_RELEASE for key=${mapping.keyName} (mapping=${mapping.id})")
        activeHolds[mapping.id]?.cancel()
        activeHolds.remove(mapping.id)
        // The gesture will naturally end; for immediate cancellation,
        // we dispatch a zero-duration gesture at the same point
        val px = percentToPx(mapping.xPercent, screenW)
        val py = percentToPy(mapping.yPercent, screenH)
        AccessibilityTouchService.performTap(px, py) // quick release tap
    }

    // ── HOLD DRAG ──

    override fun onHoldDrag(mapping: KeyMapping) {
        val px = percentToPx(mapping.xPercent, screenW)
        val py = percentToPy(mapping.yPercent, screenH)
        val endX = px + mapping.swipeDx
        val endY = py + mapping.swipeDy
        val duration = mapping.swipeDurationMs.coerceAtLeast(50L)
        Log.d(TAG, "HOLD_DRAG from ($px, $py) to ($endX, $endY)")
        AccessibilityTouchService.performSwipe(px, py, endX, endY, duration)
    }

    // ── MOVEMENT ──

    override fun onMovementAction(mapping: KeyMapping, direction: PointF) {
        if (direction.x == 0f && direction.y == 0f) return

        val centerX = percentToPx(mapping.xPercent, screenW)
        val centerY = percentToPy(mapping.yPercent, screenH)
        val distance = 80f * mapping.sensitivity
        val targetX = centerX + direction.x * distance
        val targetY = centerY + direction.y * distance
        val duration = 40L

        Log.d(TAG, "MOVEMENT from ($centerX, $centerY) dir=(${direction.x}, ${direction.y})")
        AccessibilityTouchService.performSwipe(centerX, centerY, targetX, targetY, duration)
    }

    // ── MOUSE LOOK ──

    override fun onMouseLook(mapping: KeyMapping, deltaX: Float, deltaY: Float) {
        val centerX = percentToPx(mapping.xPercent, screenW)
        val centerY = percentToPy(mapping.yPercent, screenH)

        // Scale mouse delta to screen swipe
        val sensitivity = mapping.sensitivity
        val scaledDx = deltaX * sensitivity
        val scaledDy = deltaY * sensitivity

        // Clamp to reasonable range
        val maxSwipe = 200f
        val clampedDx = scaledDx.coerceIn(-maxSwipe, maxSwipe)
        val clampedDy = scaledDy.coerceIn(-maxSwipe, maxSwipe)

        if (Math.abs(clampedDx) < 1f && Math.abs(clampedDy) < 1f) return

        val duration = 30L // fast response
        Log.d(TAG, "MOUSE_LOOK delta=($clampedDx, $clampedDy) from ($centerX, $centerY)")
        AccessibilityTouchService.performSwipe(
            centerX, centerY,
            centerX + clampedDx, centerY + clampedDy,
            duration
        )
    }

    // ── MACRO ──

    override fun onMacro(mapping: KeyMapping, steps: List<ActionSequence>) {
        // Try legacy JSON parsing first
        if (steps.isEmpty() && mapping.macroActionsJson != "[]") {
            executeLegacyMacro(mapping)
            return
        }

        // New sequence-based macro
        scope.launch {
            for (step in steps) {
                if (step.delayMs > 0) delay(step.delayMs)
                executeMacroStep(mapping, step)
            }
        }
    }

    override fun onMacroStep(mapping: KeyMapping, step: ActionSequence) {
        executeMacroStep(mapping, step)
    }

    private fun executeMacroStep(mapping: KeyMapping, step: ActionSequence) {
        when (step.actionType) {
            ActionSequence.ACTION_TAP -> {
                val px = percentToPx(step.xPercent, screenW)
                val py = percentToPy(step.yPercent, screenH)
                AccessibilityTouchService.performTap(px, py)
            }
            ActionSequence.ACTION_SWIPE -> {
                val px1 = percentToPx(step.xPercent, screenW)
                val py1 = percentToPy(step.yPercent, screenH)
                val px2 = percentToPx(step.xPercent + step.dxPercent, screenW)
                val py2 = percentToPy(step.yPercent + step.dyPercent, screenH)
                val duration = step.durationMs.coerceAtLeast(10L)
                AccessibilityTouchService.performSwipe(px1, py1, px2, py2, duration)
            }
            ActionSequence.ACTION_HOLD -> {
                // Start hold at position
                val px = percentToPx(step.xPercent, screenW)
                val py = percentToPy(step.yPercent, screenH)
                val path = Path().apply { moveTo(px, py) }
                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0L, step.holdMs.coerceAtLeast(100L)
                )
                AccessibilityTouchService.performStrokes(listOf(stroke))
            }
            ActionSequence.ACTION_DELAY -> {
                // Handled by the caller
            }
        }
    }

    private fun executeLegacyMacro(mapping: KeyMapping) {
        scope.launch {
            try {
                val actions = withContext(Dispatchers.Default) {
                    macroListAdapter.fromJson(mapping.macroActionsJson)
                } ?: emptyList()

                for (action in actions) {
                    when (action.actionType) {
                        MacroAction.ACTION_TAP -> {
                            val px = percentToPx(action.xPercent, screenW)
                            val py = percentToPy(action.yPercent, screenH)
                            AccessibilityTouchService.performTap(px, py)
                        }
                        MacroAction.ACTION_DELAY -> delay(action.delayMs)
                        MacroAction.ACTION_SWIPE -> {
                            val px1 = percentToPx(action.xPercent, screenW)
                            val py1 = percentToPy(action.yPercent, screenH)
                            val px2 = percentToPx(action.xPercent + action.dxPercent, screenW)
                            val py2 = percentToPy(action.yPercent + action.dyPercent, screenH)
                            AccessibilityTouchService.performSwipe(px1, py1, px2, py2, 300)
                        }
                        MacroAction.ACTION_HOLD -> {
                            // Legacy hold — just a long tap
                            val px = percentToPx(action.xPercent, screenW)
                            val py = percentToPy(action.yPercent, screenH)
                            val path = Path().apply { moveTo(px, py) }
                            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                                path, 0L, action.delayMs.coerceAtLeast(100L)
                            )
                            AccessibilityTouchService.performStrokes(listOf(stroke))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run legacy macro", e)
            }
        }
    }

    // ── CANCEL ──

    override fun cancelAll() {
        activeHolds.values.forEach { it.cancel() }
        activeHolds.clear()
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "All actions cancelled")
    }

    override fun cancelForMapping(mappingId: Int) {
        activeHolds[mappingId]?.cancel()
        activeHolds.remove(mappingId)
    }

    // ── HELPERS ──

    private fun percentToPx(xPercent: Float, screenW: Float): Float {
        return (xPercent / 100f) * screenW
    }

    private fun percentToPy(yPercent: Float, screenH: Float): Float {
        return (yPercent / 100f) * screenH
    }

    fun destroy() {
        cancelAll()
        scope.cancel()
    }
}
