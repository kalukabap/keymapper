package com.example.engine

import android.graphics.PointF
import com.example.data.ActionSequence
import com.example.data.KeyMapping

/**
 * Interface for executing mapping actions.
 * The service implements this to bridge to AccessibilityTouchService gesture injection.
 *
 * The engine decides WHAT to do. The executor decides HOW to do it.
 */
interface ActionExecutor {

    /** Simple tap at the mapping's position */
    fun onTap(mapping: KeyMapping)

    /** Swipe/drag from mapping position by delta */
    fun onSwipe(mapping: KeyMapping)

    /** Start holding at the mapping's position (key down) */
    fun onHoldStart(mapping: KeyMapping)

    /** Release hold at the mapping's position (key up) */
    fun onHoldRelease(mapping: KeyMapping)

    /** Hold-drag: start a drag gesture at mapping position */
    fun onHoldDrag(mapping: KeyMapping)

    /** Movement action with direction vector (for D-pad/DPAD) */
    fun onMovementAction(mapping: KeyMapping, direction: PointF)

    /** Mouse look: translate mouse delta to camera movement */
    fun onMouseLook(mapping: KeyMapping, deltaX: Float, deltaY: Float)

    /** Execute a macro with sequence steps */
    fun onMacro(mapping: KeyMapping, steps: List<ActionSequence>)

    /** Execute a single macro step */
    fun onMacroStep(mapping: KeyMapping, step: ActionSequence)

    /** Cancel all running actions */
    fun cancelAll()

    /** Cancel actions for a specific mapping */
    fun cancelForMapping(mappingId: Int)
}
