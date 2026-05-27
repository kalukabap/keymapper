package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages HUD theme persistence via SharedPreferences.
 */
class ThemeManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hud_theme", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadTheme())
    val theme: Flow<HudTheme> = _theme.asStateFlow()

    private fun loadTheme(): HudTheme {
        return HudTheme(
            primaryColor = prefs.getLong("primary_color", HudTheme.COLOR_WHITE),
            backgroundColor = prefs.getLong("background_color", HudTheme.COLOR_DARK),
            accentColor = prefs.getLong("accent_color", HudTheme.COLOR_CYAN),
            opacity = prefs.getFloat("opacity", 0.85f),
            hudSize = try {
                HudTheme.HudSize.valueOf(prefs.getString("hud_size", "MEDIUM") ?: "MEDIUM")
            } catch (_: Exception) {
                HudTheme.HudSize.MEDIUM
            }
        )
    }

    fun updateTheme(theme: HudTheme) {
        prefs.edit()
            .putLong("primary_color", theme.primaryColor)
            .putLong("background_color", theme.backgroundColor)
            .putLong("accent_color", theme.accentColor)
            .putFloat("opacity", theme.opacity)
            .putString("hud_size", theme.hudSize.name)
            .apply()
        _theme.value = theme
    }

    fun setPresetColor(colorLong: Long) {
        prefs.edit().putLong("accent_color", colorLong).apply()
        _theme.value = _theme.value.copy(accentColor = colorLong)
    }

    fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.5f, 1.0f)
        prefs.edit().putFloat("opacity", clamped).apply()
        _theme.value = _theme.value.copy(opacity = clamped)
    }

    fun setHudSize(size: HudTheme.HudSize) {
        prefs.edit().putString("hud_size", size.name).apply()
        _theme.value = _theme.value.copy(hudSize = size)
    }
}
