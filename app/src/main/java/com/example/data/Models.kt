package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "game_profiles")
data class GameProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val packageName: String = "",
    val isDefault: Boolean = false
)

@Entity(tableName = "key_mappings")
data class KeyMapping(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val keyName: String,
    val keyCode: Int,
    val xPercent: Float,
    val yPercent: Float,
    val mappingType: String = TYPE_TAP,
    val macroActionsJson: String = "[]",
    val swipeDx: Float = 0f,
    val swipeDy: Float = 0f,
    val sensitivity: Float = 1.0f
) {
    companion object {
        const val TYPE_TAP = "TAP"
        const val TYPE_DPAD = "DPAD" // D-pad click or movement
        const val TYPE_MOUSE_LOOK = "MOUSE_LOOK" // Mouse look/drag area
        const val TYPE_MACRO = "MACRO" // Execute a list of sequential actions
    }
}

@JsonClass(generateAdapter = true)
data class MacroAction(
    val actionType: String, // "TAP", "DELAY", "SWIPE"
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val delayMs: Long = 0L,
    val dxPercent: Float = 0f,
    val dyPercent: Float = 0f
) {
    companion object {
        const val ACTION_TAP = "TAP"
        const val ACTION_DELAY = "DELAY"
        const val ACTION_SWIPE = "SWIPE"
    }
}
