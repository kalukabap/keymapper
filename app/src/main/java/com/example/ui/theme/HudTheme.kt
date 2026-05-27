package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HUD theme configuration.
 * Persisted via DataStore.
 */
data class HudTheme(
    val primaryColor: Long = COLOR_WHITE,
    val backgroundColor: Long = COLOR_DARK,
    val accentColor: Long = COLOR_CYAN,
    val opacity: Float = 0.85f,
    val hudSize: HudSize = HudSize.MEDIUM
) {
    enum class HudSize(val scale: Float) {
        SMALL(0.8f),
        MEDIUM(1.0f),
        LARGE(1.2f)
    }

    fun primaryColor() = Color(primaryColor)
    fun backgroundColor() = Color(backgroundColor)
    fun accentColor() = Color(accentColor)

    companion object {
        const val COLOR_WHITE = 0xFFFFFFFF
        const val COLOR_BLACK = 0xFF000000
        const val COLOR_RED = 0xFFE53935
        const val COLOR_GREEN = 0xFF43A047
        const val COLOR_PURPLE = 0xFF8E24AA
        const val COLOR_YELLOW = 0xFFFDD835
        const val COLOR_ORANGE = 0xFFFB8C00
        const val COLOR_CYAN = 0xFF00BCD4
        const val COLOR_DARK = 0xE61A1A2E
        const val COLOR_DARK_SURFACE = 0xCC16162A

        val PRESETS = mapOf(
            "White" to COLOR_WHITE,
            "Black" to COLOR_BLACK,
            "Red" to COLOR_RED,
            "Green" to COLOR_GREEN,
            "Purple" to COLOR_PURPLE,
            "Yellow" to COLOR_YELLOW,
            "Orange" to COLOR_ORANGE,
            "Cyan" to COLOR_CYAN
        )
    }
}
