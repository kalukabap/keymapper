package com.example.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * A draggable key/swipe component on the overlay editor.
 * User drags to position, taps to configure key binding.
 */
class DraggableComponent(
    private val context: Context,
    private val windowManager: WindowManager,
    private val parent: FrameLayout,
    private val type: String // "KEY" or "SWIPE"
) {
    private val view: TextView
    private var keyCode: Int = 0
    private var keyName: String = "Tap to set"
    private var lastX = 0
    private var lastY = 0
    private var isDragging = false
    private var scaleFactor = 1.0f

    // Screen dimensions
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val dm = context.resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels

        view = TextView(context).apply {
            text = if (type == "SWIPE") "↗ Swipe" else "⬆ Key"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(if (type == "SWIPE") Color.argb(180, 0, 120, 200)
                              else Color.argb(180, 200, 60, 60))
            setPadding(32, 24, 32, 24)

            // Long press to configure
            setOnLongClickListener {
                showKeyPicker()
                true
            }
        }

        // Default position: center of screen
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = screenWidth / 2 - 60
            topMargin = screenHeight / 2 - 30
        }

        // Drag handling
        view.setOnTouchListener(DragTouchListener())

        // Delete on double tap
        var lastTapTime = 0L
        view.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 300) {
                removeFromParent()
            }
            lastTapTime = now
        }

        parent.addView(view, params)
    }

    fun setKeyCode(code: Int) {
        keyCode = code
        keyName = if (code > 0) android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_") else "Tap to set"
        view.text = if (type == "SWIPE") "↗ $keyName" else "⬆ $keyName"
    }

    fun removeFromParent() {
        parent.removeView(view)
    }

    fun toComponentData(): OverlayEditorService.ComponentData? {
        if (keyCode == 0) return null

        val params = view.layoutParams as FrameLayout.LayoutParams
        val xPercent = (params.leftMargin.toFloat() / screenWidth) * 100f
        val yPercent = (params.topMargin.toFloat() / screenHeight) * 100f

        return OverlayEditorService.ComponentData(
            type = type,
            keyCode = keyCode,
            xPercent = xPercent,
            yPercent = yPercent
        )
    }

    private fun showKeyPicker() {
        // Simple key picker — for now, cycle through common keys
        // In production, show a proper key capture dialog
        val commonKeys = listOf(
            android.view.KeyEvent.KEYCODE_W to "W",
            android.view.KeyEvent.KEYCODE_A to "A",
            android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D",
            android.view.KeyEvent.KEYCODE_SPACE to "SPACE",
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT to "SHIFT",
            android.view.KeyEvent.KEYCODE_Q to "Q",
            android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R",
            android.view.KeyEvent.KEYCODE_F to "F",
            android.view.KeyEvent.KEYCODE_Z to "Z",
            android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C",
            android.view.KeyEvent.KEYCODE_V to "V",
        )

        // Cycle to next key
        val currentIndex = commonKeys.indexOfFirst { it.first == keyCode }
        val nextIndex = (currentIndex + 1) % commonKeys.size
        setKeyCode(commonKeys[nextIndex].first)
    }

    // ─── DRAG HANDLER ──────────────────────────────────

    private inner class DragTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = v.layoutParams as FrameLayout.LayoutParams
                    initialX = params.leftMargin
                    initialY = params.topMargin
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return false // Let click/longpress handle it
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging && (dx * dx + dy * dy) > 100) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val params = v.layoutParams as FrameLayout.LayoutParams
                        params.leftMargin = (initialX + dx).toInt().coerceIn(0, screenWidth - v.width)
                        params.topMargin = (initialY + dy).toInt().coerceIn(0, screenHeight - v.height)
                        v.layoutParams = params
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP -> {
                    return isDragging
                }
            }
            return false
        }
    }
}
