package com.example.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "hud_theme")

/**
 * Manages HUD theme persistence via DataStore.
 */
class ThemeManager(private val context: Context) {

    private object Keys {
        val PRIMARY_COLOR = longPreferencesKey("primary_color")
        val BACKGROUND_COLOR = longPreferencesKey("background_color")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val OPACITY = floatPreferencesKey("opacity")
        val HUD_SIZE = stringPreferencesKey("hud_size")
    }

    val theme: Flow<HudTheme> = context.themeDataStore.data.map { prefs ->
        HudTheme(
            primaryColor = prefs[Keys.PRIMARY_COLOR] ?: HudTheme.COLOR_WHITE,
            backgroundColor = prefs[Keys.BACKGROUND_COLOR] ?: HudTheme.COLOR_DARK,
            accentColor = prefs[Keys.ACCENT_COLOR] ?: HudTheme.COLOR_CYAN,
            opacity = prefs[Keys.OPACITY] ?: 0.85f,
            hudSize = try {
                HudTheme.HudSize.valueOf(prefs[Keys.HUD_SIZE] ?: "MEDIUM")
            } catch (_: Exception) {
                HudTheme.HudSize.MEDIUM
            }
        )
    }

    suspend fun updateTheme(theme: HudTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.PRIMARY_COLOR] = theme.primaryColor
            prefs[Keys.BACKGROUND_COLOR] = theme.backgroundColor
            prefs[Keys.ACCENT_COLOR] = theme.accentColor
            prefs[Keys.OPACITY] = theme.opacity
            prefs[Keys.HUD_SIZE] = theme.hudSize.name
        }
    }

    suspend fun setPresetColor(colorLong: Long) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.ACCENT_COLOR] = colorLong
        }
    }

    suspend fun setOpacity(opacity: Float) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.OPACITY] = opacity.coerceIn(0.5f, 1.0f)
        }
    }

    suspend fun setHudSize(size: HudTheme.HudSize) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.HUD_SIZE] = size.name
        }
    }
}
