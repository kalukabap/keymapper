package com.example.server

import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.data.GameProfile
import com.example.data.KeyMapping
import com.example.keymap.KeymapData

/**
 * Converts app data models (GameProfile + KeyMapping list) to
 * KeymapData Parcelable for sending over AIDL to the server.
 */
object KeymapConverter {

    fun convert(
        profile: GameProfile,
        mappings: List<KeyMapping>,
        screenWidth: Int,
        screenHeight: Int
    ): KeymapData {
        val data = KeymapData()
        data.packageName = profile.packageName
        data.screenWidth = screenWidth
        data.screenHeight = screenHeight
        data.mouseSensitivity = profile.mouseSensitivity

        for (m in mappings) {
            when (m.mappingType) {
                KeyMapping.TYPE_TAP, KeyMapping.TYPE_HOLD_DRAG -> {
                    val tp = KeymapData.TouchPoint()
                    tp.keyCode = m.keyCode
                    tp.xPercent = m.xPercent / 100f   // DB stores 0-100, AIDL expects 0-1
                    tp.yPercent = m.yPercent / 100f
                    tp.tapDuration = m.swipeDurationMs.toInt()
                    tp.mode = when (m.holdMode) {
                        KeyMapping.HOLD_MODE_HOLD -> "hold"
                        KeyMapping.HOLD_MODE_LONG_PRESS -> "long_press"
                        else -> "tap"
                    }
                    data.touchPoints.add(tp)
                }
                KeyMapping.TYPE_SWIPE -> {
                    val sl = KeymapData.SwipeLine()
                    sl.keyCode = m.keyCode
                    sl.startXPercent = m.xPercent / 100f
                    sl.startYPercent = m.yPercent / 100f
                    sl.endXPercent = (m.xPercent + m.swipeDx) / 100f
                    sl.endYPercent = (m.yPercent + m.swipeDy) / 100f
                    sl.duration = m.swipeDurationMs.toInt()
                    data.swipeLines.add(sl)
                }
                KeyMapping.TYPE_MOUSE_LOOK -> {
                    // Mouse look is handled by the native mouse reader
                    // Sensitivity is set on the KeymapData itself
                    if (m.sensitivity > 0) {
                        data.mouseSensitivity = m.sensitivity
                    }
                }
                // DPAD and MACRO handled separately
            }
        }

        return data
    }
}
