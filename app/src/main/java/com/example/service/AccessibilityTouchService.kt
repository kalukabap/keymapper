package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class AccessibilityTouchService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityTouchService connected")
        activeInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op for window/view changes. We only care about gesture dispatching and key events.
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityTouchService interrupted")
        activeInstance = null
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "AccessibilityTouchService unbound")
        activeInstance = null
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        try {
            Log.d(TAG, "onKeyEvent: action=${event.action}, code=${event.keyCode}")
            
            // Let the Keymapping supervisor handle or intercept this event
            val supervisor = keyEventOverrideListener
            if (supervisor != null) {
                return supervisor.onInterceptKeyEvent(event)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onKeyEvent callback", e)
        }
        return false
    }

    fun injectTap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().apply {
            addStroke(stroke)
        }.build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(10L))
        val gesture = GestureDescription.Builder().apply {
            addStroke(stroke)
        }.build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    fun injectStrokes(strokes: List<GestureDescription.StrokeDescription>, callback: ((Boolean) -> Unit)? = null) {
        if (strokes.isEmpty()) return
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke)
        }
        val gesture = builder.build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    interface KeyEventOverrideListener {
        fun onInterceptKeyEvent(event: KeyEvent): Boolean
    }

    companion object {
        private const val TAG = "KeyMapperAccess"
        
        @Volatile
        private var activeInstance: AccessibilityTouchService? = null
        
        @Volatile
        var keyEventOverrideListener: KeyEventOverrideListener? = null

        val isServiceConnected: Boolean
            get() = activeInstance != null

        fun getSharedInstance(): AccessibilityTouchService? = activeInstance

        fun performTap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
            val service = activeInstance
            return if (service != null) {
                service.injectTap(x, y, callback)
                true
            } else {
                false
            }
        }

        fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, callback: ((Boolean) -> Unit)? = null): Boolean {
            val service = activeInstance
            return if (service != null) {
                service.injectSwipe(x1, y1, x2, y2, durationMs, callback)
                true
            } else {
                false
            }
        }

        fun performStrokes(strokes: List<GestureDescription.StrokeDescription>, callback: ((Boolean) -> Unit)? = null): Boolean {
            val service = activeInstance
            return if (service != null && strokes.isNotEmpty()) {
                service.injectStrokes(strokes, callback)
                true
            } else {
                false
            }
        }
    }
}
