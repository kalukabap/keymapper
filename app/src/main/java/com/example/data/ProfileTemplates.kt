package com.example.data

import android.view.KeyEvent

/**
 * Original starter layouts for common mobile-control genres.
 * These are not copied from any commercial product; they are neutral templates
 * that users can edit in the overlay editor for their own games.
 */
data class SandProfileTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val packageHint: String,
    val mappings: List<SandMappingSpec>
)

data class SandMappingSpec(
    val keyName: String,
    val keyCode: Int,
    val xPercent: Float,
    val yPercent: Float,
    val mappingType: String = KeyMapping.TYPE_TAP,
    val holdMode: String = KeyMapping.HOLD_MODE_TAP,
    val sensitivity: Float = 1.0f,
    val swipeDx: Float = 0f,
    val swipeDy: Float = 0f,
    val swipeDurationMs: Long = 100L,
    val holdDurationMs: Long = 0L,
    val repeatPolicy: String = KeyMapping.REPEAT_NONE,
    val repeatDelayMs: Long = 0L,
    val deadZone: Float = 0f,
    val smoothing: Float = 0f
) {
    fun toKeyMapping(profileId: Int): KeyMapping = KeyMapping(
        profileId = profileId,
        keyName = keyName,
        keyCode = keyCode,
        xPercent = xPercent,
        yPercent = yPercent,
        mappingType = mappingType,
        holdMode = holdMode,
        sensitivity = sensitivity,
        swipeDx = swipeDx,
        swipeDy = swipeDy,
        swipeDurationMs = swipeDurationMs,
        holdDurationMs = holdDurationMs,
        repeatPolicy = repeatPolicy,
        repeatDelayMs = repeatDelayMs,
        deadZone = deadZone,
        smoothing = smoothing
    )
}

object SandProfileTemplates {
    val all: List<SandProfileTemplate> = listOf(
        SandProfileTemplate(
            id = "fps_mouse_keyboard",
            title = "Shooter Mouse + Keyboard",
            subtitle = "WASD movement, mouse-look, fire, ADS, jump, crouch, reload, sprint",
            packageHint = "com.example.shooter",
            mappings = listOf(
                SandMappingSpec("W", KeyEvent.KEYCODE_W, 22f, 73f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("A", KeyEvent.KEYCODE_A, 16f, 79f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("S", KeyEvent.KEYCODE_S, 22f, 85f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("D", KeyEvent.KEYCODE_D, 28f, 79f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("Mouse Look", 0, 64f, 42f, KeyMapping.TYPE_MOUSE_LOOK, KeyMapping.HOLD_MODE_HOLD, sensitivity = 1.25f, deadZone = 0.04f, smoothing = 0.22f),
                SandMappingSpec("Left Click", KeyMapping.MOUSE_LEFT, 81f, 67f, KeyMapping.TYPE_TAP, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("Right Click", KeyMapping.MOUSE_RIGHT, 89f, 47f, KeyMapping.TYPE_TAP, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("Space", KeyEvent.KEYCODE_SPACE, 79f, 86f),
                SandMappingSpec("C", KeyEvent.KEYCODE_C, 69f, 88f, KeyMapping.TYPE_TAP, KeyMapping.HOLD_MODE_TOGGLE),
                SandMappingSpec("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, 34f, 88f, KeyMapping.TYPE_TAP, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("R", KeyEvent.KEYCODE_R, 74f, 56f),
                SandMappingSpec("1", KeyEvent.KEYCODE_1, 53f, 91f),
                SandMappingSpec("2", KeyEvent.KEYCODE_2, 61f, 91f)
            )
        ),
        SandProfileTemplate(
            id = "moba_action_bar",
            title = "MOBA / Skill Buttons",
            subtitle = "Left joystick, four skills, attack, shop, recall, camera drag",
            packageHint = "com.example.moba",
            mappings = listOf(
                SandMappingSpec("W", KeyEvent.KEYCODE_W, 21f, 72f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("A", KeyEvent.KEYCODE_A, 15f, 79f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("S", KeyEvent.KEYCODE_S, 21f, 86f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("D", KeyEvent.KEYCODE_D, 28f, 79f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("Q", KeyEvent.KEYCODE_Q, 71f, 73f),
                SandMappingSpec("E", KeyEvent.KEYCODE_E, 82f, 65f),
                SandMappingSpec("R", KeyEvent.KEYCODE_R, 90f, 56f),
                SandMappingSpec("F", KeyEvent.KEYCODE_F, 88f, 79f),
                SandMappingSpec("Left Click", KeyMapping.MOUSE_LEFT, 80f, 87f),
                SandMappingSpec("B", KeyEvent.KEYCODE_B, 57f, 94f),
                SandMappingSpec("P", KeyEvent.KEYCODE_P, 91f, 9f),
                SandMappingSpec("Camera Drag", KeyMapping.MOUSE_RIGHT, 60f, 39f, KeyMapping.TYPE_HOLD_DRAG, KeyMapping.HOLD_MODE_HOLD, swipeDx = 10f, swipeDy = 0f, swipeDurationMs = 180L)
            )
        ),
        SandProfileTemplate(
            id = "action_rpg_controller",
            title = "Action RPG / Controller",
            subtitle = "Keyboard movement, dodge, interact, ability row, free-look drag",
            packageHint = "com.example.rpg",
            mappings = listOf(
                SandMappingSpec("W", KeyEvent.KEYCODE_W, 24f, 72f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("A", KeyEvent.KEYCODE_A, 18f, 79f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("S", KeyEvent.KEYCODE_S, 24f, 86f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("D", KeyEvent.KEYCODE_D, 30f, 79f, KeyMapping.TYPE_DPAD, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("Mouse Look", 0, 69f, 38f, KeyMapping.TYPE_MOUSE_LOOK, KeyMapping.HOLD_MODE_HOLD, sensitivity = 1.05f, smoothing = 0.35f),
                SandMappingSpec("Left Click", KeyMapping.MOUSE_LEFT, 82f, 78f, KeyMapping.TYPE_TAP, KeyMapping.HOLD_MODE_HOLD),
                SandMappingSpec("Space", KeyEvent.KEYCODE_SPACE, 68f, 88f),
                SandMappingSpec("E", KeyEvent.KEYCODE_E, 77f, 57f),
                SandMappingSpec("Q", KeyEvent.KEYCODE_Q, 62f, 79f),
                SandMappingSpec("1", KeyEvent.KEYCODE_1, 49f, 91f),
                SandMappingSpec("2", KeyEvent.KEYCODE_2, 56f, 91f),
                SandMappingSpec("3", KeyEvent.KEYCODE_3, 63f, 91f),
                SandMappingSpec("4", KeyEvent.KEYCODE_4, 70f, 91f),
                SandMappingSpec("Tab", KeyEvent.KEYCODE_TAB, 92f, 10f)
            )
        )
    )

    fun byId(id: String): SandProfileTemplate? = all.firstOrNull { it.id == id }
}
