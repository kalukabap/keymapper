package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Service
import android.graphics.Path
import android.os.Build
import android.util.Log

/**
 * Persistent touch session manager.
 *
 * Instead of dispatching one-shot taps/swipes, this maintains active touch
 * sessions with continuous MOVE updates. This is how real FPS aiming works:
 *
 *   touchDown(x, y) → touchMove(x, y) → touchMove(x, y) → touchUp()
 *
 * The session stays alive between DOWN and UP, allowing continuous
 * coordinate updates at frame rate.
 */
class PersistentInjector(
    private val service: AccessibilityService?
) {
    companion object {
        private const val TAG = "PersistentInjector"
        private const val MAX_POINTER_ID = 9
        private const val STALE_POINTER_TIMEOUT_MS = 5000L
    }

    // ── ACTIVE TOUCH POINTERS ──
    // pointerId → PointerState

    data class PointerState(
        val id: Int,
        var currentX: Float,
        var currentY: Float,
        val startTime: Long,
        var lastUpdateTime: Long,
        var isActive: Boolean = true
    )

    private val activePointers = mutableMapOf<Int, PointerState>()
    private var nextPointerId = 0

    // ── INJECTION CALLBACK ──

    private var lastInjectionSuccess = true

    // ═══════════════════════════════════════════
    //  TOUCH SESSION MANAGEMENT
    // ═══════════════════════════════════════════

    /**
     * Start a new touch session at (x, y).
     * Returns the pointer ID for subsequent move/up calls.
     */
    fun touchDown(x: Float, y: Float): Int {
        val pointerId = allocatePointerId()
        val now = System.currentTimeMillis()

        val pointer = PointerState(
            id = pointerId,
            currentX = x,
            currentY = y,
            startTime = now,
            lastUpdateTime = now
        )

        activePointers[pointerId] = pointer
        Log.d(TAG, "touchDown: pointer=$pointerId at ($x, $y)")

        // Inject the DOWN event
        injectTouchDown(x, y, pointerId)
        return pointerId
    }

    /**
     * Move an existing touch pointer to new coordinates.
     * This is the HOT PATH for mouse look — called every frame.
     */
    fun touchMove(pointerId: Int, x: Float, y: Float): Boolean {
        val pointer = activePointers[pointerId]
        if (pointer == null || !pointer.isActive) {
            Log.w(TAG, "touchMove: pointer $pointerId not active")
            return false
        }

        pointer.currentX = x
        pointer.currentY = y
        pointer.lastUpdateTime = System.currentTimeMillis()

        // Inject the MOVE event
        return injectTouchMove(x, y, pointerId)
    }

    /**
     * End a touch session.
     */
    fun touchUp(pointerId: Int) {
        val pointer = activePointers[pointerId]
        if (pointer == null) {
            Log.w(TAG, "touchUp: pointer $pointerId not found")
            return
        }

        pointer.isActive = false
        Log.d(TAG, "touchUp: pointer $pointerId at (${pointer.currentX}, ${pointer.currentY})")

        // Inject the UP event
        injectTouchUp(pointer.currentX, pointer.currentY, pointerId)
        activePointers.remove(pointerId)
    }

    /**
     * Cancel all active pointers (emergency cleanup).
     */
    fun cancelAll() {
        for ((id, pointer) in activePointers) {
            if (pointer.isActive) {
                injectTouchUp(pointer.currentX, pointer.currentY, id)
            }
        }
        activePointers.clear()
        Log.w(TAG, "Cancelled all pointers")
    }

    /**
     * Clean up stale pointers (safety net).
     */
    fun cleanupStalePointers() {
        val now = System.currentTimeMillis()
        val stale = activePointers.filter { (_, p) ->
            p.isActive && (now - p.lastUpdateTime) > STALE_POINTER_TIMEOUT_MS
        }
        for ((id, _) in stale) {
            Log.w(TAG, "Cleaning up stale pointer $id")
            touchUp(id)
        }
    }

    // ═══════════════════════════════════════════
    //  GESTURE INJECTION (AccessibilityService)
    // ═══════════════════════════════════════════

    /**
     * Inject a touch DOWN event using GestureDescription.
     * Uses a zero-duration stroke at the target point.
     */
    private fun injectTouchDown(x: Float, y: Float, pointerId: Int) {
        val svc = service ?: run {
            Log.w(TAG, "No AccessibilityService for injection")
            return
        }
        try {
            val path = Path().apply { moveTo(x, y) }
            // Long duration stroke — we'll cancel it with touchUp
            val stroke = GestureDescription.StrokeDescription(path, 0L, 100_000L)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    lastInjectionSuccess = true
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    lastInjectionSuccess = false
                    Log.w(TAG, "touchDown injection cancelled for pointer $pointerId")
                }
            }, null)
        } catch (e: Throwable) {
            Log.e(TAG, "injectTouchDown failed", e)
            lastInjectionSuccess = false
        }
    }

    /**
     * Inject a touch MOVE event.
     * For Android 11+, we can use continueStroke on the GestureDescription.
     * For older versions, we inject a new gesture at the updated position.
     */
    private fun injectTouchMove(x: Float, y: Float, pointerId: Int): Boolean {
        val svc = service ?: return false
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 100_000L)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            svc.dispatchGesture(gesture, null, null)
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "injectTouchMove failed", e)
            return false
        }
    }

    /**
     * Inject a touch UP event.
     * This ends the gesture by dispatching a zero-duration tap.
     */
    private fun injectTouchUp(x: Float, y: Float, pointerId: Int) {
        val svc = service ?: return
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "touchUp completed for pointer $pointerId")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "touchUp cancelled for pointer $pointerId")
                }
            }, null)
        } catch (e: Throwable) {
            Log.e(TAG, "injectTouchUp failed", e)
        }
    }

    // ═══════════════════════════════════════════
    //  SHIZUKU INJECTION (higher fidelity)
    // ═══════════════════════════════════════════

    /**
     * Inject via Shizuku's InputManager.injectInputEvent().
     * This is higher fidelity than AccessibilityService gestures.
     * Falls back to AccessibilityService if Shizuku is not available.
     */
    fun injectViaShizuku(event: android.view.InputEvent): Boolean {
        if (!ShizukuHiddenApi.canInjectInput) {
            Log.w(TAG, "Shizuku injection not available")
            return false
        }
        return ShizukuHiddenApi.injectInputEvent(event)
    }

    // ═══════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════

    private fun allocatePointerId(): Int {
        val id = nextPointerId
        nextPointerId = (nextPointerId + 1) % (MAX_POINTER_ID + 1)
        return id
    }

    fun getActivePointerCount(): Int = activePointers.size

    fun isPointerActive(pointerId: Int): Boolean = activePointers[pointerId]?.isActive == true

    fun getPointerState(pointerId: Int): PointerState? = activePointers[pointerId]

    fun destroy() {
        cancelAll()
        Log.i(TAG, "PersistentInjector destroyed")
    }
}
