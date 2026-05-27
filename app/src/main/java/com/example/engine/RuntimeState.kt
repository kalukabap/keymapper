package com.example.engine

import android.util.Log

/**
 * Runtime state machine for the input mapping engine.
 *
 * States:
 *   IDLE → PROFILE_LOADING → READY → AIM_MODE / MACRO_RUNNING / INPUT_LOCKED / SUSPENDED
 *   Any state → PERMISSION_MISSING (if overlay/accessibility/shizuku lost)
 *   Any state → IDLE (on service stop)
 */
enum class EngineState {
    /** Service not running or just stopped */
    IDLE,
    /** Loading profile snapshot from DB */
    PROFILE_LOADING,
    /** Normal operation — accepting input, executing mappings */
    READY,
    /** Pointer locked — mouse movement translates to camera/swipe */
    AIM_MODE,
    /** A macro sequence is currently playing */
    MACRO_RUNNING,
    /** Input processing temporarily blocked (e.g. editor open, cursor lock toggle) */
    INPUT_LOCKED,
    /** Service alive but paused (e.g. game not in foreground) */
    SUSPENDED,
    /** Missing required permissions */
    PERMISSION_MISSING
}

/**
 * Valid state transitions. Returns true if the transition is allowed.
 */
object StateTransitions {
    private const val TAG = "EngineState"

    private val validTransitions = mapOf(
        EngineState.IDLE to setOf(
            EngineState.PROFILE_LOADING,
            EngineState.PERMISSION_MISSING
        ),
        EngineState.PROFILE_LOADING to setOf(
            EngineState.READY,
            EngineState.PERMISSION_MISSING,
            EngineState.IDLE
        ),
        EngineState.READY to setOf(
            EngineState.AIM_MODE,
            EngineState.MACRO_RUNNING,
            EngineState.INPUT_LOCKED,
            EngineState.SUSPENDED,
            EngineState.PERMISSION_MISSING,
            EngineState.PROFILE_LOADING, // profile switch
            EngineState.IDLE
        ),
        EngineState.AIM_MODE to setOf(
            EngineState.READY,
            EngineState.INPUT_LOCKED,
            EngineState.SUSPENDED,
            EngineState.PERMISSION_MISSING,
            EngineState.IDLE
        ),
        EngineState.MACRO_RUNNING to setOf(
            EngineState.READY,
            EngineState.INPUT_LOCKED,
            EngineState.SUSPENDED,
            EngineState.PERMISSION_MISSING,
            EngineState.IDLE
        ),
        EngineState.INPUT_LOCKED to setOf(
            EngineState.READY,
            EngineState.AIM_MODE,
            EngineState.SUSPENDED,
            EngineState.PERMISSION_MISSING,
            EngineState.IDLE
        ),
        EngineState.SUSPENDED to setOf(
            EngineState.READY,
            EngineState.PERMISSION_MISSING,
            EngineState.IDLE
        ),
        EngineState.PERMISSION_MISSING to setOf(
            EngineState.READY,
            EngineState.IDLE
        )
    )

    fun canTransition(from: EngineState, to: EngineState): Boolean {
        val allowed = validTransitions[from]?.contains(to) ?: false
        if (!allowed) {
            Log.w(TAG, "Invalid transition: $from → $to")
        }
        return allowed
    }

    fun canAcceptInput(state: EngineState): Boolean {
        return state == EngineState.READY || state == EngineState.AIM_MODE
    }
}
