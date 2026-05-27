package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────
// GAME PROFILE
// ─────────────────────────────────────────────────────────

@Entity(tableName = "game_profiles")
data class GameProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val packageName: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Mouse / pointer settings
    val mouseSensitivity: Float = 1.0f,
    val mouseAcceleration: Float = 0f,
    val mouseSmoothing: Float = 0f,
    val mouseDeadZone: Float = 0f,
    val mouseInvertY: Boolean = false,
    // Movement settings
    val movementMode: String = MOVEMENT_MODE_DPAD,
    val movementSensitivity: Float = 1.0f
) {
    companion object {
        const val MOVEMENT_MODE_DPAD = "DPAD"
        const val MOVEMENT_MODE_ANALOG = "ANALOG"
    }
}

// ─────────────────────────────────────────────────────────
// BINDING GROUP — logical clusters (movement, aim, fire, etc.)
// ─────────────────────────────────────────────────────────

@Entity(
    tableName = "binding_groups",
    foreignKeys = [
        ForeignKey(
            entity = GameProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class BindingGroup(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val name: String,
    val groupType: String = GROUP_TYPE_CUSTOM,
    // Anchor position for this group (used by movement, aim, etc.)
    val anchorXPercent: Float = 50f,
    val anchorYPercent: Float = 50f,
    val radiusPercent: Float = 10f,
    val isEnabled: Boolean = true
) {
    companion object {
        const val GROUP_TYPE_MOVEMENT = "MOVEMENT"
        const val GROUP_TYPE_AIM = "AIM"
        const val GROUP_TYPE_FIRE = "FIRE"
        const val GROUP_TYPE_UTILITY = "UTILITY"
        const val GROUP_TYPE_MACRO = "MACRO"
        const val GROUP_TYPE_CUSTOM = "CUSTOM"
    }
}

// ─────────────────────────────────────────────────────────
// KEY MAPPING — expanded with hold, chord, repeat, group
// ─────────────────────────────────────────────────────────

@Entity(
    tableName = "key_mappings",
    foreignKeys = [
        ForeignKey(
            entity = GameProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("groupId")]
)
data class KeyMapping(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: Int,
    val keyName: String,
    val keyCode: Int,
    val xPercent: Float,
    val yPercent: Float,
    val mappingType: String = TYPE_TAP,
    // Action config
    val macroActionsJson: String = "[]",
    val swipeDx: Float = 0f,
    val swipeDy: Float = 0f,
    val sensitivity: Float = 1.0f,
    // NEW: Hold mode
    val holdMode: String = HOLD_MODE_TAP,
    // NEW: Repeat policy
    val repeatPolicy: String = REPEAT_NONE,
    val repeatDelayMs: Long = 0L,
    // NEW: Chord support (JSON array of keycodes, e.g. "[59,32]" for Shift+W)
    val chordKeysJson: String = "[]",
    // NEW: Group membership
    val groupId: Int = -1,
    // NEW: Mouse/look parameters
    val deadZone: Float = 0f,
    val smoothing: Float = 0f,
    val accelerationCurve: Float = 0f,
    // NEW: Swipe duration for drag actions
    val swipeDurationMs: Long = 100L,
    // NEW: Hold duration for long-press actions
    val holdDurationMs: Long = 0L
) {
    companion object {
        // Mapping types
        const val TYPE_TAP = "TAP"
        const val TYPE_DPAD = "DPAD"
        const val TYPE_MOUSE_LOOK = "MOUSE_LOOK"
        const val TYPE_MACRO = "MACRO"
        const val TYPE_SWIPE = "SWIPE"
        const val TYPE_HOLD_DRAG = "HOLD_DRAG"

        // Hold modes
        const val HOLD_MODE_TAP = "TAP"           // Fire on key down
        const val HOLD_MODE_HOLD = "HOLD"         // Active while key held, release on up
        const val HOLD_MODE_TOGGLE = "TOGGLE"     // Toggle on/off with each press
        const val HOLD_MODE_LONG_PRESS = "LONG_PRESS" // Fire after held for N ms

        // Repeat policies
        const val REPEAT_NONE = "NONE"
        const val REPEAT_AUTO = "AUTO"            // Auto-repeat while held
        const val REPEAT_ON_RELEASE = "ON_RELEASE" // Fire again when released
    }
}

// ─────────────────────────────────────────────────────────
// ACTION SEQUENCE — for complex macros
// ─────────────────────────────────────────────────────────

@Entity(
    tableName = "action_sequences",
    foreignKeys = [
        ForeignKey(
            entity = KeyMapping::class,
            parentColumns = ["id"],
            childColumns = ["mappingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mappingId")]
)
data class ActionSequence(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mappingId: Int,
    val stepIndex: Int,  // Order within the sequence
    val actionType: String,
    // Position params
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    // Swipe/drag params
    val dxPercent: Float = 0f,
    val dyPercent: Float = 0f,
    // Timing
    val delayMs: Long = 0L,
    val durationMs: Long = 50L,
    // Hold specific
    val holdMs: Long = 0L,
    // Repeat
    val repeatCount: Int = 1,
    val repeatDelayMs: Long = 0L
) {
    companion object {
        const val ACTION_TAP = "TAP"
        const val ACTION_DELAY = "DELAY"
        const val ACTION_SWIPE = "SWIPE"
        const val ACTION_HOLD = "HOLD"
        const val ACTION_RELEASE = "RELEASE"
        const val ACTION_REPEAT_START = "REPEAT_START"  // Mark start of repeat block
        const val ACTION_REPEAT_END = "REPEAT_END"      // Mark end of repeat block
    }
}

// ─────────────────────────────────────────────────────────
// MACRO ACTION (legacy — still used for JSON compat)
// ─────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class MacroAction(
    val actionType: String,
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
        const val ACTION_HOLD = "HOLD"
        const val ACTION_RELEASE = "RELEASE"
    }
}

// ─────────────────────────────────────────────────────────
// RUNTIME STATE ENUM
// ─────────────────────────────────────────────────────────

enum class RuntimeState {
    IDLE,
    PROFILE_LOADING,
    READY,
    INPUT_LOCKED,
    AIM_MODE,
    MACRO_RUNNING,
    SUSPENDED,
    PERMISSION_MISSING
}

// ─────────────────────────────────────────────────────────
// KEY STATE — tracks physical key lifecycle
// ─────────────────────────────────────────────────────────

data class KeyState(
    val keyCode: Int,
    val isDown: Boolean,
    val repeatCount: Int = 0,
    val consumed: Boolean = false,
    val downTimestamp: Long = 0L,
    val isToggled: Boolean = false,
    val isChordMember: Boolean = false
)

// ─────────────────────────────────────────────────────────
// INPUT EVENT — normalized internal event
// ─────────────────────────────────────────────────────────

data class InputEvent(
    val type: Type,
    val keyCode: Int = 0,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val button: Int = 0,
    val wheelDelta: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Type {
        KEY_DOWN,
        KEY_UP,
        KEY_REPEAT,
        MOUSE_MOVE,
        MOUSE_DOWN,
        MOUSE_UP,
        SCROLL
    }
}
