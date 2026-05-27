package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global app settings persisted via SharedPreferences.
 * Per-profile settings live in Room; these are app-wide.
 */
class AppSettings(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // ── HUD Position ──
    data class HudPosition(val x: Float = 100f, val y: Float = 200f, val edgeSnap: Boolean = true)

    private val _hudPosition = MutableStateFlow(
        HudPosition(prefs.getFloat("hud_x", 100f), prefs.getFloat("hud_y", 200f))
    )
    val hudPosition: Flow<HudPosition> = _hudPosition.asStateFlow()

    private val _showTouchPaths = MutableStateFlow(prefs.getBoolean("show_touch_paths", false))
    val showTouchPathsFlow: Flow<Boolean> = _showTouchPaths.asStateFlow()

    private val _mousePointerVisible = MutableStateFlow(prefs.getBoolean("mouse_pointer_visible", true))
    val mousePointerVisibleFlow: Flow<Boolean> = _mousePointerVisible.asStateFlow()

    private val _mousePointerSize = MutableStateFlow(prefs.getInt("mouse_pointer_size", 24))
    val mousePointerSizeFlow: Flow<Int> = _mousePointerSize.asStateFlow()

    private val _mousePollingRate = MutableStateFlow(prefs.getInt("mouse_polling_rate", 0))
    val mousePollingRateFlow: Flow<Int> = _mousePollingRate.asStateFlow()

    private val _debugLogging = MutableStateFlow(prefs.getBoolean("debug_logging", false))
    val debugLoggingFlow: Flow<Boolean> = _debugLogging.asStateFlow()

    private val _autoStart = MutableStateFlow(prefs.getBoolean("auto_start_service", false))
    val autoStartFlow: Flow<Boolean> = _autoStart.asStateFlow()

    // ── WRITERS ──

    fun saveHudPosition(x: Float, y: Float) {
        prefs.edit().putFloat("hud_x", x).putFloat("hud_y", y).apply()
        _hudPosition.value = HudPosition(x, y)
    }

    fun setEdgeSnap(enabled: Boolean) {
        prefs.edit().putBoolean("hud_edge_snap", enabled).apply()
    }

    fun setShowTouchPaths(enabled: Boolean) {
        prefs.edit().putBoolean("show_touch_paths", enabled).apply()
        _showTouchPaths.value = enabled
    }

    fun setMousePointerVisible(visible: Boolean) {
        prefs.edit().putBoolean("mouse_pointer_visible", visible).apply()
        _mousePointerVisible.value = visible
    }

    fun setMousePointerSize(sizeDp: Int) {
        val clamped = sizeDp.coerceIn(8, 64)
        prefs.edit().putInt("mouse_pointer_size", clamped).apply()
        _mousePointerSize.value = clamped
    }

    fun setMousePollingRate(hz: Int) {
        prefs.edit().putInt("mouse_polling_rate", hz).apply()
        _mousePollingRate.value = hz
    }

    fun setSensitivityHotkey(keyCode: Int, mode: String) {
        prefs.edit()
            .putInt("sensitivity_hotkey", keyCode)
            .putString("sensitivity_hotkey_mode", mode)
            .apply()
    }

    fun clearSensitivityHotkey() {
        prefs.edit()
            .remove("sensitivity_hotkey")
            .remove("sensitivity_hotkey_mode")
            .apply()
    }

    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean("auto_start_service", enabled).apply()
        _autoStart.value = enabled
    }

    fun setDebugLogging(enabled: Boolean) {
        prefs.edit().putBoolean("debug_logging", enabled).apply()
        _debugLogging.value = enabled
    }
}
