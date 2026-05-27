package com.example.engine

import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * FPS View / Free Look engine.
 * Translates mouse deltas into persistent touch drag for camera control.
 * Supports hold-to-look and toggle-to-look modes.
 */
class FpsViewEngine(
    private val injector: PersistentInjector,
    private val sensitivityPipeline: SensitivityPipeline,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FpsViewEngine"
        private const val FRAME_INTERVAL_MS = 16L // ~60fps
        private const val EDGE_MARGIN_PX = 50f
    }

    // ── CONFIG ──

    data class Config(
        val sensitivityX: Float = 1.0f,
        val sensitivityY: Float = 1.0f,
        val resetOnEdge: Boolean = true,
        val edgeMarginPx: Float = EDGE_MARGIN_PX,
        val mode: LookMode = LookMode.HOLD,
        val viewCenterX: Float = 540f, // center of screen
        val viewCenterY: Float = 1200f,
        val viewRadius: Float = 300f, // radius of the look area
        val invertY: Boolean = false
    )

    enum class LookMode {
        HOLD,   // hold key to look, release to stop
        TOGGLE  // press to start looking, press again to stop
    }

    var config = Config()
        private set

    // ── STATE ──

    private var isActive = false
    private var currentPointerId: Int? = null
    private var aimX = 0f
    private var aimY = 0f
    private var frameJob: Job? = null

    // Accumulated raw deltas (fed from mouse input)
    private var accumulatedDx = 0f
    private var accumulatedDy = 0f

    // ── CONFIGURATION ──

    fun updateConfig(newConfig: Config) {
        config = newConfig
        Log.d(TAG, "Config updated: mode=${config.mode}, sens=${config.sensitivityX}/${config.sensitivityY}")
    }

    // ── CONTROL ──

    /**
     * Start looking (hold mode or toggle mode).
     */
    fun startLooking() {
        if (isActive) return

        aimX = config.viewCenterX
        aimY = config.viewCenterY

        // Create persistent touch pointer at center of view area
        currentPointerId = injector.touchDown(aimX, aimY)
        isActive = true

        // Start frame loop for continuous updates
        frameJob = scope.launch {
            while (isActive) {
                processFrame()
                delay(FRAME_INTERVAL_MS)
            }
        }

        Log.i(TAG, "Started looking at ($aimX, $aimY)")
    }

    /**
     * Stop looking.
     */
    fun stopLooking() {
        if (!isActive) return

        frameJob?.cancel()
        frameJob = null

        currentPointerId?.let { pid ->
            injector.touchUp(pid)
        }
        currentPointerId = null
        isActive = false

        Log.i(TAG, "Stopped looking")
    }

    /**
     * Toggle looking (for toggle mode).
     */
    fun toggleLooking() {
        if (isActive) stopLooking() else startLooking()
    }

    /**
     * Feed raw mouse delta into the engine.
     * Called from RuntimeEngine when mouse movement is detected.
     */
    fun feedMouseDelta(dx: Float, dy: Float) {
        accumulatedDx += dx
        accumulatedDy += dy
    }

    /**
     * Check if currently in look mode.
     */
    fun isLooking(): Boolean = isActive

    // ── FRAME PROCESSING ──

    private fun processFrame() {
        if (!isActive) return

        // Consume accumulated deltas
        val dx = accumulatedDx
        val dy = accumulatedDy
        accumulatedDx = 0f
        accumulatedDy = 0f

        if (dx == 0f && dy == 0f) return

        // Apply sensitivity
        val processedDx = dx * config.sensitivityX
        val processedDy = dy * config.sensitivityY * if (config.invertY) -1f else 1f

        // Update aim position
        aimX += processedDx
        aimY += processedDy

        // Edge detection
        if (config.resetOnEdge) {
            val edgeX = config.viewCenterX - config.viewRadius
            val edgeY = config.viewCenterY - config.viewRadius
            val maxX = config.viewCenterX + config.viewRadius
            val maxY = config.viewCenterY + config.viewRadius

            if (aimX < edgeX + config.edgeMarginPx || aimX > maxX - config.edgeMarginPx ||
                aimY < edgeY + config.edgeMarginPx || aimY > maxY - config.edgeMarginPx
            ) {
                // Reset to center
                aimX = config.viewCenterX
                aimY = config.viewCenterY
                Log.d(TAG, "Edge hit — reset to center")
            }
        }

        // Clamp to view area
        aimX = aimX.coerceIn(
            config.viewCenterX - config.viewRadius,
            config.viewCenterX + config.viewRadius
        )
        aimY = aimY.coerceIn(
            config.viewCenterY - config.viewRadius,
            config.viewCenterY + config.viewRadius
        )

        // Update persistent touch pointer
        currentPointerId?.let { pid ->
            injector.touchMove(pid, aimX, aimY)
        }
    }

    // ── STATE ──

    data class ViewState(
        val isActive: Boolean,
        val aimX: Float,
        val aimY: Float,
        val centerX: Float,
        val centerY: Float,
        val radius: Float
    )

    fun getViewState(): ViewState = ViewState(
        isActive = isActive,
        aimX = aimX,
        aimY = aimY,
        centerX = config.viewCenterX,
        centerY = config.viewCenterY,
        radius = config.viewRadius
    )
}
