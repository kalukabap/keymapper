package com.example.engine

import android.graphics.PointF
import android.util.Log
import kotlin.math.*

/**
 * Virtual joystick engine.
 * Converts WASD/DPAD key presses into a unit-circle direction vector,
 * then maps that to a touch point within a configurable radius.
 */
class JoystickEngine {
    companion object {
        private const val TAG = "JoystickEngine"
    }

    // ── CONFIG ──

    data class Config(
        val centerX: Float = 200f,
        val centerY: Float = 600f,
        val radius: Float = 120f,
        val deadZone: Float = 0.15f,
        val sensitivity: Float = 1.0f,
        val invertX: Boolean = false,
        val invertY: Boolean = false,
        val diagonalCorrection: Boolean = true // normalize diagonal to unit length
    )

    var config = Config()
        private set

    // ── STATE ──

    // Currently pressed movement keys (normalized to -1/0/1)
    private var axisX = 0f // -1 = left, 0 = center, 1 = right
    private var axisY = 0f // -1 = up, 0 = center, 1 = down

    private var isActive = false
    private var currentPointerId: Int? = null

    // ── CONFIGURATION ──

    fun updateConfig(newConfig: Config) {
        config = newConfig
        Log.d(TAG, "Config updated: center=(${config.centerX}, ${config.centerY}), radius=${config.radius}")
    }

    // ── KEY INPUT ──

    /**
     * Update movement from key state.
     * Call this when WASD/DPAD keys change.
     * @param left right up down: true if key is pressed
     */
    fun updateKeys(left: Boolean, right: Boolean, up: Boolean, down: Boolean) {
        axisX = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        axisY = (if (down) 1f else 0f) - (if (up) 1f else 0f)

        // Apply inversion
        if (config.invertX) axisX = -axisX
        if (config.invertY) axisY = -axisY

        // Diagonal correction: normalize to unit circle
        if (config.diagonalCorrection) {
            val magnitude = sqrt(axisX * axisX + axisY * axisY)
            if (magnitude > 1f) {
                axisX /= magnitude
                axisY /= magnitude
            }
        }

        // Apply dead zone
        val magnitude = sqrt(axisX * axisX + axisY * axisY)
        if (magnitude < config.deadZone) {
            axisX = 0f
            axisY = 0f
        }

        // Apply sensitivity
        axisX *= config.sensitivity
        axisY *= config.sensitivity
        axisX = axisX.coerceIn(-1f, 1f)
        axisY = axisY.coerceIn(-1f, 1f)

        isActive = axisX != 0f || axisY != 0f
    }

    // ── TOUCH OUTPUT ──

    /**
     * Get the touch point for the current joystick state.
     * Returns null if joystick is centered (no touch needed).
     */
    fun getTouchPoint(): PointF? {
        if (!isActive) return null

        val touchX = config.centerX + axisX * config.radius
        val touchY = config.centerY + axisY * config.radius
        return PointF(touchX, touchY)
    }

    /**
     * Get the direction vector (unit circle).
     * Useful for other systems that need the raw direction.
     */
    fun getDirection(): PointF = PointF(axisX, axisY)

    /**
     * Get the magnitude (0.0 to 1.0).
     */
    fun getMagnitude(): Float = sqrt(axisX * axisX + axisY * axisY)

    /**
     * Check if the joystick is currently active (not centered).
     */
    fun isJoystickActive(): Boolean = isActive

    /**
     * Reset the joystick to center.
     */
    fun reset() {
        axisX = 0f
        axisY = 0f
        isActive = false
    }

    /**
     * Set axis values directly (for analog input).
     */
    fun setAxis(x: Float, y: Float) {
        axisX = x.coerceIn(-1f, 1f)
        axisY = y.coerceIn(-1f, 1f)

        val magnitude = sqrt(axisX * axisX + axisY * axisY)
        if (magnitude < config.deadZone) {
            axisX = 0f
            axisY = 0f
        }

        isActive = axisX != 0f || axisY != 0f
    }

    // ── RENDERING ──

    /**
     * Get data for visual overlay rendering.
     */
    data class OverlayState(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val currentX: Float,
        val currentY: Float,
        val isActive: Boolean
    )

    fun getOverlayState(): OverlayState {
        val touchX = config.centerX + axisX * config.radius
        val touchY = config.centerY + axisY * config.radius
        return OverlayState(
            centerX = config.centerX,
            centerY = config.centerY,
            radius = config.radius,
            currentX = touchX,
            currentY = touchY,
            isActive = isActive
        )
    }
}
