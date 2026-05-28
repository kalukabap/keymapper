package com.example.ui

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import com.example.data.AppSettings
import com.example.ui.theme.HudTheme
import com.example.ui.theme.ThemeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Floating HUD service.
 * Manages the draggable mini bar and expanded tool palette overlays.
 * Runs as a foreground service to stay alive during gameplay.
 */
class FloatingHudService : Service() {
    companion object {
        private const val TAG = "FloatingHud"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "floating_hud"

        var instance: FloatingHudService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingHudService::class.java))
        }
    }

    // ── OVERLAY VIEWS ──

    private var windowManager: WindowManager? = null
    private var miniHudView: View? = null
    private var paletteView: View? = null

    // ── SERVICES ──

    private lateinit var appSettings: AppSettings
    private lateinit var themeManager: ThemeManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── STATE ──

    private var currentTheme = HudTheme()
    private var isPaletteOpen = false
    private var currentProfileName = "No Profile"
    private var runtimeStatus = RuntimeStatus.IDLE

    enum class RuntimeStatus {
        IDLE, LOADING, READY, RUNNING, ERROR, PERMISSION_MISSING
    }

    // ── LIFECYCLE ──

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        appSettings = AppSettings(this)
        themeManager = ThemeManager(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Observe theme changes
        scope.launch {
            themeManager.theme.collect { theme ->
                currentTheme = theme
                updateTheme()
            }
        }

        // Create overlays
        createMiniHud()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlays()
        scope.cancel()
        instance = null
    }

    // ── MINI HUD ──

    private fun createMiniHud() {
        val inflater = LayoutInflater.from(this)
        miniHudView = inflater.inflate(com.example.R.layout.floating_hud_mini, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager?.addView(miniHudView, params)

        // Setup drag
        setupDrag(miniHudView!!, params)

        // Setup click handlers
        miniHudView?.setOnClickListener {
            togglePalette()
        }

        // Long press for quick actions
        miniHudView?.setOnLongClickListener {
            // Quick toggle service
            true
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 25) { // 5px threshold
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    } else {
                        // Save position
                        scope.launch {
                            appSettings.saveHudPosition(params.x.toFloat(), params.y.toFloat())
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ── PALETTE ──

    private fun togglePalette() {
        if (isPaletteOpen) {
            closePalette()
        } else {
            openPalette()
        }
    }

    private fun openPalette() {
        if (paletteView != null) return

        val inflater = LayoutInflater.from(this)
        paletteView = inflater.inflate(com.example.R.layout.tool_palette, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager?.addView(paletteView, params)
        isPaletteOpen = true

        // Setup palette buttons
        setupPaletteButtons()
    }

    private fun closePalette() {
        paletteView?.let { windowManager?.removeView(it) }
        paletteView = null
        isPaletteOpen = false
    }

    private fun setupPaletteButtons() {
        // Each button opens its feature panel
        val buttons = mapOf(
            com.example.R.id.btn_touch to ::openTouchPanel,
            com.example.R.id.btn_swipe to ::openSwipePanel,
            com.example.R.id.btn_scroll to ::openScrollPanel,
            com.example.R.id.btn_macro to ::openMacroPanel,
            com.example.R.id.btn_joystick to ::openJoystickPanel,
            com.example.R.id.btn_fps_view to ::openFpsViewPanel,
            com.example.R.id.btn_free_look to ::openFreeLookPanel,
            com.example.R.id.btn_keyboard to ::openKeyboardPanel,
            com.example.R.id.btn_keymap to ::openKeymapPanel,
            com.example.R.id.btn_settings to ::openSettingsPanel,
            com.example.R.id.btn_diagnostics to ::openDiagnosticsPanel,
            com.example.R.id.btn_help to ::openHelpPanel
        )

        buttons.forEach { (id, action) ->
            paletteView?.findViewById<View>(id)?.setOnClickListener {
                action()
            }
        }
    }

    // ── FEATURE PANEL LAUNCHERS ──

    private fun openTouchPanel() {
        closePalette()
        // Launch touch config activity/fragment
        val intent = Intent(this, com.example.ui.panels.TouchConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openSwipePanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.SwipeConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openScrollPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.ScrollConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openMacroPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.MacroConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openJoystickPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.JoystickConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openFpsViewPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.FpsViewConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openFreeLookPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.FreeLookConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openKeyboardPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.KeyboardConfigActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openKeymapPanel() {
        closePalette()
        // Launch the overlay editor instead of a config panel
        val intent = Intent(this, com.example.editor.OverlayEditorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openSettingsPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openDiagnosticsPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.DiagnosticsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openHelpPanel() {
        closePalette()
        val intent = Intent(this, com.example.ui.panels.HelpActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ── THEME ──

    private fun updateTheme() {
        // Update mini HUD background
        val bgColor = currentTheme.backgroundColor.toInt()
        val alpha = ((currentTheme.opacity * 255).toInt() shl 24)
        miniHudView?.setBackgroundColor((bgColor and 0x00FFFFFF) or alpha)
    }

    // ── STATUS UPDATES ──

    fun updateStatus(status: RuntimeStatus, profileName: String? = null) {
        runtimeStatus = status
        profileName?.let { currentProfileName = it }

        // Update mini HUD indicators
        val statusDot = miniHudView?.findViewById<View>(com.example.R.id.status_dot)
        val profileLabel = miniHudView?.findViewById<TextView>(com.example.R.id.profile_name)

        statusDot?.setBackgroundColor(when (status) {
            RuntimeStatus.READY -> 0xFF43A047.toInt() // green
            RuntimeStatus.RUNNING -> 0xFF00BCD4.toInt() // cyan
            RuntimeStatus.LOADING -> 0xFFFDD835.toInt() // yellow
            RuntimeStatus.ERROR -> 0xFFE53935.toInt() // red
            RuntimeStatus.PERMISSION_MISSING -> 0xFFFB8C00.toInt() // orange
            RuntimeStatus.IDLE -> 0xFF757575.toInt() // gray
        })

        profileLabel?.text = currentProfileName
    }

    // ── NOTIFICATION ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ApexMapper floating control HUD"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("ApexMapper")
            .setContentText("Floating HUD active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    // ── CLEANUP ──

    private fun removeOverlays() {
        try {
            miniHudView?.let { windowManager?.removeView(it) }
            paletteView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        miniHudView = null
        paletteView = null
    }
}
