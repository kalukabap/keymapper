package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Global app settings persisted via DataStore.
 * Per-profile settings live in Room; these are app-wide.
 */
class AppSettings(private val context: Context) {

    // ── HUD Position ──
    private val hudX = floatPreferencesKey("hud_x")
    private val hudY = floatPreferencesKey("hud_y")
    private val hudEdgeSnap = booleanPreferencesKey("hud_edge_snap")

    // ── Feature Toggles ──
    private val showTouchPaths = booleanPreferencesKey("show_touch_paths")
    private val touchPathColor = longPreferencesKey("touch_path_color")
    private val mousePointerVisible = booleanPreferencesKey("mouse_pointer_visible")
    private val mousePointerSize = intPreferencesKey("mouse_pointer_size")

    // ── Mouse ──
    private val mousePollingRate = intPreferencesKey("mouse_polling_rate") // Hz: 125, 250, 500, 0=uncapped
    private val sensitivityHotkey = intPreferencesKey("sensitivity_hotkey") // keyCode
    private val sensitivityHotkeyMode = stringPreferencesKey("sensitivity_hotkey_mode") // CLICK, LONG_PRESS

    // ── Runtime ──
    private val autoStartService = booleanPreferencesKey("auto_start_service")
    private val debugLogging = booleanPreferencesKey("debug_logging")

    // ── READERS ──

    data class HudPosition(val x: Float = 100f, val y: Float = 200f, val edgeSnap: Boolean = true)

    val hudPosition: Flow<HudPosition> = context.appDataStore.data.map { prefs ->
        HudPosition(
            x = prefs[hudX] ?: 100f,
            y = prefs[hudY] ?: 200f,
            edgeSnap = prefs[hudEdgeSnap] ?: true
        )
    }

    val showTouchPathsFlow: Flow<Boolean> = context.appDataStore.data.map { it[showTouchPaths] ?: false }
    val mousePointerVisibleFlow: Flow<Boolean> = context.appDataStore.data.map { it[mousePointerVisible] ?: true }
    val mousePointerSizeFlow: Flow<Int> = context.appDataStore.data.map { it[mousePointerSize] ?: 24 }
    val mousePollingRateFlow: Flow<Int> = context.appDataStore.data.map { it[mousePollingRate] ?: 0 }
    val debugLoggingFlow: Flow<Boolean> = context.appDataStore.data.map { it[debugLogging] ?: false }
    val autoStartFlow: Flow<Boolean> = context.appDataStore.data.map { it[autoStartService] ?: false }

    // ── WRITERS ──

    suspend fun saveHudPosition(x: Float, y: Float) {
        context.appDataStore.edit { prefs ->
            prefs[hudX] = x
            prefs[hudY] = y
        }
    }

    suspend fun setEdgeSnap(enabled: Boolean) {
        context.appDataStore.edit { it[hudEdgeSnap] = enabled }
    }

    suspend fun setShowTouchPaths(enabled: Boolean) {
        context.appDataStore.edit { it[showTouchPaths] = enabled }
    }

    suspend fun setMousePointerVisible(visible: Boolean) {
        context.appDataStore.edit { it[mousePointerVisible] = visible }
    }

    suspend fun setMousePointerSize(sizeDp: Int) {
        context.appDataStore.edit { it[mousePointerSize] = sizeDp.coerceIn(8, 64) }
    }

    suspend fun setMousePollingRate(hz: Int) {
        // 125, 250, 500, 0=uncapped
        context.appDataStore.edit { it[mousePollingRate] = hz }
    }

    suspend fun setSensitivityHotkey(keyCode: Int, mode: String) {
        context.appDataStore.edit { prefs ->
            prefs[sensitivityHotkey] = keyCode
            prefs[sensitivityHotkeyMode] = mode
        }
    }

    suspend fun clearSensitivityHotkey() {
        context.appDataStore.edit { prefs ->
            prefs.remove(sensitivityHotkey)
            prefs.remove(sensitivityHotkeyMode)
        }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.appDataStore.edit { it[autoStartService] = enabled }
    }

    suspend fun setDebugLogging(enabled: Boolean) {
        context.appDataStore.edit { it[debugLogging] = enabled }
    }
}
