package com.example.engine

import android.view.KeyEvent
import android.view.MotionEvent
import com.example.data.InputEvent
import android.util.Log

/**
 * Converts raw Android KeyEvent/MotionEvent into normalized InputEvents.
 * This is the ONLY place that touches raw input — everything downstream
 * sees clean, typed InputEvent objects.
 */
object InputNormalizer {

    private const val TAG = "InputNormalizer"

    // ── KEYBOARD ──

    /**
     * Normalize a raw KeyEvent into an InputEvent.
     * Handles ACTION_DOWN, ACTION_UP, and repeat detection.
     */
    fun normalize(event: KeyEvent): InputEvent? {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val isRepeat = event.repeatCount > 0
                InputEvent(
                    type = if (isRepeat) InputEvent.Type.KEY_REPEAT else InputEvent.Type.KEY_DOWN,
                    keyCode = event.keyCode,
                    timestamp = event.eventTime
                )
            }
            KeyEvent.ACTION_UP -> {
                InputEvent(
                    type = InputEvent.Type.KEY_UP,
                    keyCode = event.keyCode,
                    timestamp = event.eventTime
                )
            }
            else -> {
                Log.w(TAG, "Unknown key action: ${event.action}")
                null
            }
        }
    }

    // ── MOUSE ──

    /**
     * Normalize a raw MotionEvent (mouse) into InputEvents.
     * A single MotionEvent can produce multiple events (move + button + scroll).
     */
    fun normalizeMouse(event: MotionEvent, previousButtonState: Int): List<InputEvent> {
        val events = mutableListOf<InputEvent>()

        // Mouse movement
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // ACTION_MOVE with historical batches — use the latest values
            val deltaX = event.getAxisValue(MotionEvent.AXIS_X)
            val deltaY = event.getAxisValue(MotionEvent.AXIS_Y)

            // Only emit move if there's actual delta
            // Note: for relative mouse, we need RELATIVE_X/Y if available
            val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
            val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)

            if (relX != 0f || relY != 0f) {
                events.add(
                    InputEvent(
                        type = InputEvent.Type.MOUSE_MOVE,
                        deltaX = relX,
                        deltaY = relY,
                        timestamp = event.eventTime
                    )
                )
            }
        }

        // Mouse buttons
        val currentButtons = event.buttonState
        val changedButtons = currentButtons xor previousButtonState

        if (changedButtons != 0) {
            // Check each button
            for (button in listOf(
                MotionEvent.BUTTON_PRIMARY,
                MotionEvent.BUTTON_SECONDARY,
                MotionEvent.BUTTON_TERTIARY,
                MotionEvent.BUTTON_BACK,
                MotionEvent.BUTTON_FORWARD
            )) {
                if (changedButtons and buttonMask(button) != 0) {
                    val isDown = currentButtons and buttonMask(button) != 0
                    events.add(
                        InputEvent(
                            type = if (isDown) InputEvent.Type.MOUSE_DOWN else InputEvent.Type.MOUSE_UP,
                            button = button,
                            timestamp = event.eventTime
                        )
                    )
                }
            }
        }

        // Scroll wheel
        val scrollY = event.getAxisValue(MotionEvent.AXIS_SCROLL)
        val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (scrollY != 0f || scrollX != 0f) {
            events.add(
                InputEvent(
                    type = InputEvent.Type.SCROLL,
                    deltaX = scrollX,
                    deltaY = scrollY,
                    timestamp = event.eventTime
                )
            )
        }

        return events
    }

    /**
     * Check if a MotionEvent is from a mouse (not a touchscreen).
     */
    fun isMouseEvent(event: MotionEvent): Boolean {
        val source = event.source
        return source and android.view.InputDevice.SOURCE_MOUSE == android.view.InputDevice.SOURCE_MOUSE ||
               source and android.view.InputDevice.SOURCE_TOUCHPAD == android.view.InputDevice.SOURCE_TOUCHPAD
    }

    /**
     * Get relative mouse deltas from a MotionEvent.
     * Falls back to historical deltas if RELATIVE_X/Y not available.
     */
    fun getMouseDelta(event: MotionEvent, lastX: Float, lastY: Float): Pair<Float, Float> {
        // Prefer RELATIVE_X/Y (Android 11+)
        val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)

        if (relX != 0f || relY != 0f) {
            return Pair(relX, relY)
        }

        // Fallback: compute delta from raw X/Y
        val dx = event.x - lastX
        val dy = event.y - lastY
        return Pair(dx, dy)
    }

    // ── HELPERS ──

    private fun buttonMask(button: Int): Int {
        return when (button) {
            MotionEvent.BUTTON_PRIMARY -> MotionEvent.BUTTON_PRIMARY
            MotionEvent.BUTTON_SECONDARY -> MotionEvent.BUTTON_SECONDARY
            MotionEvent.BUTTON_TERTIARY -> MotionEvent.BUTTON_TERTIARY
            MotionEvent.BUTTON_BACK -> 8   // MotionEvent.BUTTON_BACK
            MotionEvent.BUTTON_FORWARD -> 16 // MotionEvent.BUTTON_FORWARD
            else -> 0
        }
    }

    /**
     * Get a human-readable name for a key code.
     */
    fun keyName(keyCode: Int): String {
        return try {
            KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
        } catch (e: Exception) {
            "KEY_$keyCode"
        }
    }

    /**
     * Get a human-readable name for a mouse button.
     */
    fun buttonName(button: Int): String {
        return when (button) {
            MotionEvent.BUTTON_PRIMARY -> "Left Click"
            MotionEvent.BUTTON_SECONDARY -> "Right Click"
            MotionEvent.BUTTON_TERTIARY -> "Middle Click"
            MotionEvent.BUTTON_BACK -> "Side Back"
            MotionEvent.BUTTON_FORWARD -> "Side Forward"
            else -> "Button_$button"
        }
    }

    // ── KEY CATEGORIES ──

    /** WASD / arrow keys — commonly used for movement */
    private val MOVEMENT_KEYS = setOf(
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A,
        KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    )

    /** Modifier keys */
    private val MODIFIER_KEYS = setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT
    )

    fun isMovementKey(keyCode: Int): Boolean = keyCode in MOVEMENT_KEYS
    fun isModifierKey(keyCode: Int): Boolean = keyCode in MODIFIER_KEYS
}
