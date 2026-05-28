package com.example.service

/**
 * REMOVED — Accessibility service no longer used.
 * Input injection now handled by the app_process server via AIDL.
 * Kept as stub to avoid breaking references during transition.
 */
object AccessibilityTouchService {
    @JvmStatic val isServiceConnected: Boolean = false
    @JvmStatic var keyEventOverrideListener: KeyEventOverrideListener? = null

    interface KeyEventOverrideListener {
        fun onHardwareKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean
    }

    @JvmStatic fun performTap(px: Float, py: Float) {}
    @JvmStatic fun performSwipe(px: Float, py: Float, endX: Float, endY: Float, duration: Long) {}
    @JvmStatic fun performStrokes(strokes: List<Any>) {}
    @JvmStatic fun getSharedInstance(): Any? = null
}
