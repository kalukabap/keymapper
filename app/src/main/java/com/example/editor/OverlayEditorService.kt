package com.example.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.R

/**
 * Floating overlay editor for placing key mapping components on screen.
 * Like XtMapper's EditorUI — draggable Key, Swipe, Camera components.
 */
class OverlayEditorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var editorContainer: FrameLayout
    private lateinit var toolbar: LinearLayout
    private val components = mutableListOf<DraggableComponent>()

    companion object {
        private const val TAG = "OverlayEditor"
        private const val CHANNEL_ID = "editor_overlay"
        private const val NOTIFICATION_ID = 2001

        var isRunning = false
            private set

        // Callback to save mappings
        var onSaveCallback: ((List<ComponentData>) -> Unit)? = null
    }

    data class ComponentData(
        val type: String,
        val keyCode: Int,
        val xPercent: Float,
        val yPercent: Float,
        val extra: Map<String, Any> = emptyMap()
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE)
        isRunning = true
        createOverlay()
        createToolbar()
        startForegroundNotification()
        Log.i(TAG, "Editor overlay created")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            windowManager.removeView(editorContainer)
            windowManager.removeView(toolbar)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing views", e)
        }
    }

    // ─── OVERLAY SETUP ─────────────────────────────────

    private fun createOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        editorContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(30, 0, 0, 0))
        }

        // Tap on empty space to add a new key
        editorContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Don't add if tapping on an existing component
                true
            } else false
        }

        windowManager.addView(editorContainer, params)
    }

    private fun createToolbar() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 50

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(220, 30, 30, 50))
            setPadding(24, 16, 24, 16)
        }

        // Add Key button
        addButton("＋ Key") { addKeyComponent() }
        // Add Swipe button
        addButton("↗ Swipe") { addSwipeComponent() }
        // Save button
        addButton("💾 Save") { saveMappings() }
        // Clear All button
        addButton("🗑 Clear") { clearAllComponents() }
        // Close button
        addButton("✕ Close") { stopSelf() }

        windowManager.addView(toolbar, params)
    }

    private fun addButton(text: String, onClick: () -> Unit) {
        val btn = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 12, 24, 12)
            setOnClickListener { onClick() }
        }
        toolbar.addView(btn)
    }

    // ─── COMPONENT MANAGEMENT ──────────────────────────

    private fun addKeyComponent() {
        val component = DraggableComponent(this, windowManager, editorContainer, "KEY")
        component.setKeyCode(0) // Will be set by user
        components.add(component)
    }

    private fun addSwipeComponent() {
        val component = DraggableComponent(this, windowManager, editorContainer, "SWIPE")
        component.setKeyCode(0)
        components.add(component)
    }

    private fun clearAllComponents() {
        for (c in components) c.removeFromParent()
        components.clear()
    }

    private fun saveMappings() {
        val dataList = components.mapNotNull { it.toComponentData() }
        Log.i(TAG, "Saving ${dataList.size} components")
        onSaveCallback?.invoke(dataList)
        stopSelf()
    }

    // ─── NOTIFICATION ──────────────────────────────────

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Keymap Editor",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Keymap Editor")
            .setContentText("Tap to place keys, drag to move")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
