package com.example.engine

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.*

/**
 * Mouse sensitivity pipeline.
 *
 * Raw mouse deltas go through:
 *   1. Dead zone filtering
 *   2. Smoothing (exponential moving average)
 *   3. Acceleration curve
 *   4. DPI scaling
 *   5. Sensitivity multiplier
 *   6. Velocity clamping
 *   7. Coordinate translation (relative → absolute)
 */
class SensitivityPipeline(context: Context) {

    companion object {
        private const val TAG = "SensitivityPipeline"
        private const val DEFAULT_SENSITIVITY = 1.0f
        private const val DEFAULT_ACCELERATION = 1.0f
        private const val DEFAULT_SMOOTHING = 0.3f
        private const val DEFAULT_DEAD_ZONE = 2f
        private const val DEFAULT_MAX_VELOCITY = 5000f
        private const val DEFAULT_DPI_SCALE = 1.0f
    }

    // ── SCREEN INFO ──

    private val screenW: Int
    private val screenH: Int
    private val dpiScale: Float

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        dpiScale = metrics.densityDpi / 160f // normalize to mdpi
    }

    // ── PIPELINE PARAMETERS ──

    var sensitivity = DEFAULT_SENSITIVITY
    var acceleration = DEFAULT_ACCELERATION
    var smoothing = DEFAULT_SMOOTHING       // 0 = no smoothing, 1 = max smoothing
    var deadZone = DEFAULT_DEAD_ZONE        // pixels to ignore
    var maxVelocity = DEFAULT_MAX_VELOCITY  // pixels per frame cap
    var invertY = false

    // ── INTERNAL STATE ──

    private var smoothedDx = 0f
    private var smoothedDy = 0f

    // Current aim position (absolute screen coordinates)
    private var aimX = screenW / 2f
    private var aimY = screenH / 2f

    // ═══════════════════════════════════════════
    //  PIPELINE PROCESSING
    // ═══════════════════════════════════════════

    /**
     * Process a raw mouse delta through the full pipeline.
     * Returns the processed (dx, dy) in screen pixels.
     */
    fun process(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        // 1. Dead zone
        val dzDx = applyDeadZone(rawDx)
        val dzDy = applyDeadZone(rawDy)

        // 2. Smoothing (exponential moving average)
        val smoothDx = applySmoothing(dzDx, true)
        val smoothDy = applySmoothing(dzDy, false)

        // 3. Acceleration curve
        val accelDx = applyAcceleration(smoothDx)
        val accelDy = applyAcceleration(smoothDy)

        // 4. DPI scaling
        val dpiDx = accelDx * dpiScale * DEFAULT_DPI_SCALE
        val dpiDy = accelDy * dpiScale * DEFAULT_DPI_SCALE

        // 5. Sensitivity multiplier
        val sensDx = dpiDx * sensitivity
        val sensDy = dpiDy * sensitivity * if (invertY) -1f else 1f

        // 6. Velocity clamping
        val clampedDx = clampVelocity(sensDx)
        val clampedDy = clampVelocity(sensDy)

        return clampedDx to clampedDy
    }

    /**
     * Process raw delta and update aim position.
     * Returns new absolute aim coordinates.
     */
    fun processAndUpdateAim(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val (dx, dy) = process(rawDx, rawDy)

        aimX = (aimX + dx).coerceIn(0f, screenW.toFloat())
        aimY = (aimY + dy).coerceIn(0f, screenH.toFloat())

        return aimX to aimY
    }

    /**
     * Set aim position directly (for touch-based aim).
     */
    fun setAimPosition(x: Float, y: Float) {
        aimX = x.coerceIn(0f, screenW.toFloat())
        aimY = y.coerceIn(0f, screenH.toFloat())
    }

    /**
     * Get current aim position.
     */
    fun getAimPosition(): Pair<Float, Float> = aimX to aimY

    /**
     * Reset smoothing state.
     */
    fun resetSmoothing() {
        smoothedDx = 0f
        smoothedDy = 0f
    }

    // ═══════════════════════════════════════════
    //  PIPELINE STAGES
    // ═══════════════════════════════════════════

    /**
     * Stage 1: Dead zone filtering.
     * Ignores tiny movements below the threshold.
     */
    private fun applyDeadZone(value: Float): Float {
        return if (abs(value) < deadZone) 0f else value
    }

    /**
     * Stage 2: Exponential moving average smoothing.
     * Reduces jitter while maintaining responsiveness.
     */
    private fun applySmoothing(value: Float, isX: Boolean): Float {
        val smoothed = if (isX) {
            smoothedDx = smoothedDx * (1f - smoothing) + value * smoothing
            smoothedDx
        } else {
            smoothedDy = smoothedDy * (1f - smoothing) + value * smoothing
            smoothedDy
        }
        return smoothed
    }

    /**
     * Stage 3: Acceleration curve.
     * Maps raw movement to accelerated movement.
     * acceleration=1.0 is linear, >1.0 accelerates fast movements more.
     */
    private fun applyAcceleration(value: Float): Float {
        if (acceleration == 1.0f) return value

        val sign = if (value >= 0) 1f else -1f
        val magnitude = abs(value)

        // Power curve: fast movements get more acceleration
        val accelerated = pow(magnitude, acceleration)

        return sign * accelerated
    }

    /**
     * Stage 6: Velocity clamping.
     * Prevents extreme movements from teleporting the cursor.
     */
    private fun clampVelocity(value: Float): Float {
        return value.coerceIn(-maxVelocity, maxVelocity)
    }

    // ═══════════════════════════════════════════
    //  CONFIGURATION
    // ═══════════════════════════════════════════

    data class Config(
        val sensitivity: Float = DEFAULT_SENSITIVITY,
        val acceleration: Float = DEFAULT_ACCELERATION,
        val smoothing: Float = DEFAULT_SMOOTHING,
        val deadZone: Float = DEFAULT_DEAD_ZONE,
        val maxVelocity: Float = DEFAULT_MAX_VELOCITY,
        val invertY: Boolean = false
    )

    fun applyConfig(config: Config) {
        sensitivity = config.sensitivity
        acceleration = config.acceleration
        smoothing = config.smoothing.coerceIn(0f, 0.95f)
        deadZone = config.deadZone
        maxVelocity = config.maxVelocity
        invertY = config.invertY
    }

    fun getConfig(): Config = Config(
        sensitivity = sensitivity,
        acceleration = acceleration,
        smoothing = smoothing,
        deadZone = deadZone,
        maxVelocity = maxVelocity,
        invertY = invertY
    )

    fun getScreenSize(): Pair<Int, Int> = screenW to screenH
}
