package com.example.engine

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.KeyEvent
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.sqrt

/**
 * THE CORE ENGINE — Panda Mouse Pro style.
 *
 * Owns: profile snapshot, key/mouse state, movement vector, state machine.
 * Does NOT own: UI, overlay, persistence, gesture injection.
 *
 * Feed it InputEvents via processKey/processMouse.
 * It decides what action to take and calls ActionExecutor to execute.
 */
class RuntimeEngine(private val context: Context) {

    companion object {
        private const val TAG = "RuntimeEngine"
        private const val DEBOUNCE_MS = 16L  // ~1 frame at 60fps
        private const val LONG_PRESS_DEFAULT_MS = 300L
        private const val REPEAT_SUPPRESS_MS = 100L // suppress auto-repeat within this window
    }

    // ── STATE MACHINE ──

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // ── PROFILE SNAPSHOT ──

    private var activeProfile: GameProfile? = null
    private var mappingsSnapshot: List<KeyMapping> = emptyList()
    private var groupsSnapshot: List<BindingGroup> = emptyList()
    private var sequencesSnapshot: Map<Int, List<ActionSequence>> = emptyMap()

    // ── PRESSED KEY TABLE ──
    // keyCode → KeyState

    private val pressedKeys = mutableMapOf<Int, KeyState>()

    // ── MOUSE BUTTON TABLE ──
    // button → isDown

    private val pressedMouseButtons = mutableMapOf<Int, Boolean>()

    // ── CHORD TABLE ──
    // set of currently pressed keycodes (for chord detection)

    private val activeChordKeys = mutableSetOf<Int>()

    // ── MOVEMENT VECTOR ──
    // Computed from WASD/DPAD state, normalized to unit circle

    private val _movementVector = MutableStateFlow(PointF(0f, 0f))
    val movementVector: StateFlow<PointF> = _movementVector.asStateFlow()

    // ── AIM STATE ──

    private val _aimDelta = MutableStateFlow(PointF(0f, 0f))
    val aimDelta: StateFlow<PointF> = _aimDelta.asStateFlow()

    private var isAimMode = false

    // ── DEBUG TOGGLE ──

    private var debugEnabled = true

    // ── REPEAT SUPPRESSION ──
    // keyCode → timestamp of last ACTION_DOWN (suppresses auto-repeat spam)

    private val lastDownTime = mutableMapOf<Int, Long>()

    // ── TOGGLE STATE ──
    // keyCode → isToggled (for HOLD_MODE_TOGGLE)

    private val toggleStates = mutableMapOf<Int, Boolean>()

    // ── MACRO STATE ──

    private var activeMacroJob: Job? = null
    private val _isMacroRunning = MutableStateFlow(false)
    val isMacroRunning: StateFlow<Boolean> = _isMacroRunning.asStateFlow()

    // ── DEBOUNCE ──

    private val lastEventTime = mutableMapOf<Int, Long>()

    // ── ACTION CALLBACK ──
    // The engine calls this to actually perform actions (tap, swipe, hold, etc.)
    // Set by the service that hosts the engine.

    var actionExecutor: ActionExecutor? = null

    // ── SHIZUKU COMPONENTS ──

    /** Sensitivity pipeline for mouse → touch translation */
    val sensitivityPipeline = SensitivityPipeline(context)

    /** Raw input manager for /dev/input/ capture */
    var rawInputManager: com.example.shizuku.RawInputManager? = null

    /** Persistent injector for touch sessions */
    var persistentInjector: PersistentInjector? = null

    /** Shizuku available flag */
    var shizukuMode = false
        private set

    // ── FRAME-BASED LOOP ──

    private var frameJob: Job? = null
    private val FRAME_INTERVAL_MS = 16L // ~60fps

    // ── AIM POINTER TRACKING ──

    private var aimPointerId: Int? = null

    // ── DEBUG ──

    private val _debugLog = MutableStateFlow("")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()

    private var eventCount = 0L

    // ═══════════════════════════════════════════
    //  PROFILE LOADING (atomic snapshot)
    // ═══════════════════════════════════════════

    /**
     * Load a profile into memory as an atomic snapshot.
     * This MUST complete before any input events are processed.
     * No DB queries happen after this — everything is in-memory.
     */

    suspend fun loadProfile(
        profile: GameProfile,
        mappings: List<KeyMapping>,
        groups: List<BindingGroup>,
        sequences: Map<Int, List<ActionSequence>>
    ) {
        transitionTo(EngineState.PROFILE_LOADING)

        activeProfile = profile
        mappingsSnapshot = mappings
        groupsSnapshot = groups
        sequencesSnapshot = sequences

        // Clear runtime state
        pressedKeys.clear()
        pressedMouseButtons.clear()
        activeChordKeys.clear()
        toggleStates.clear()
        lastEventTime.clear()
        _movementVector.value = PointF(0f, 0f)
        _aimDelta.value = PointF(0f, 0f)
        isAimMode = false
        cancelActiveMacro()

        debug("Profile loaded: ${profile.name} (${mappings.size} mappings, ${groups.size} groups)")

        transitionTo(EngineState.READY)
    }

    /**
     * Clear everything and go idle.
     */
    fun unload() {
        cancelActiveMacro()
        frameJob?.cancel()
        frameJob = null
        rawInputManager?.stopCapture()
        persistentInjector?.cancelAll()
        pressedKeys.clear()
        pressedMouseButtons.clear()
        activeChordKeys.clear()
        toggleStates.clear()
        aimPointerId = null
        shizukuMode = false
        activeProfile = null
        mappingsSnapshot = emptyList()
        groupsSnapshot = emptyList()
        sequencesSnapshot = emptyMap()
        transitionTo(EngineState.IDLE)
    }

    // ═══════════════════════════════════════════
    //  SHIZUKU MODE
    // ═══════════════════════════════════════════

    /**
     * Enable Shizuku mode for raw input capture.
     * Call after Shizuku permission is granted.
     */
    fun enableShizukuMode(rawManager: com.example.shizuku.RawInputManager, injector: PersistentInjector) {
        this.rawInputManager = rawManager
        this.persistentInjector = injector
        this.shizukuMode = true

        // Apply profile sensitivity settings
        activeProfile?.let { profile ->
            sensitivityPipeline.applyConfig(SensitivityPipeline.Config(
                sensitivity = profile.mouseSensitivity,
                smoothing = profile.mouseSmoothing,
                deadZone = profile.mouseDeadZone,
                invertY = profile.mouseInvertY
            ))
        }

        // Start frame-based loop
        startFrameLoop()
        debug("Shizuku mode enabled")
    }

    /**
     * Start the frame-based processing loop.
     */
    private fun startFrameLoop() {
        frameJob?.cancel()
        frameJob = scope.launch {
            while (isActive && shizukuMode) {
                processFrame()
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    /**
     * Process one frame: accumulate raw deltas → sensitivity pipeline → touch update.
     */
    private fun processFrame() {
        if (!shizukuMode || rawInputManager == null) return
        if (_state.value != EngineState.READY && _state.value != EngineState.AIM_MODE) return

        val (rawDx, rawDy) = rawInputManager!!.consumeAccumulatedDelta()
        if (rawDx == 0f && rawDy == 0f) return

        // Process through sensitivity pipeline
        val (newAimX, newAimY) = sensitivityPipeline.processAndUpdateAim(rawDx, rawDy)

        // Update the persistent touch pointer
        aimPointerId?.let { pid ->
            persistentInjector?.touchMove(pid, newAimX, newAimY)
        }

        _aimDelta.value = PointF(
            sensitivityPipeline.process(rawDx, rawDy).first,
            sensitivityPipeline.process(rawDx, rawDy).second
        )
    }

    // ═══════════════════════════════════════════
    //  KEYBOARD INPUT PROCESSING
    // ═══════════════════════════════════════════

    /**
     * Process a normalized keyboard InputEvent.
     * Returns true if the event was consumed (mapped), false if it should pass through.
     */
    fun processKey(event: InputEvent): Boolean {
        if (!StateTransitions.canAcceptInput(_state.value)) {
            debug("Key ignored: state=${_state.value}")
            return false
        }

        val keyCode = event.keyCode
        eventCount++

        // Debounce check
        val now = event.timestamp
        val lastTime = lastEventTime[keyCode] ?: 0L
        if (event.type == InputEvent.Type.KEY_DOWN && (now - lastTime) < DEBOUNCE_MS) {
            debug("Debounced key $keyCode (${now - lastTime}ms)")
            return false
        }
        lastEventTime[keyCode] = now

        // Update key state table
        when (event.type) {
            InputEvent.Type.KEY_DOWN -> {
                // Repeat suppression: if we just processed KEY_DOWN for this code,
                // ignore if within suppress window (handles hardware bounce)
                val lastDown = lastDownTime[keyCode] ?: 0L
                if ((now - lastDown) < REPEAT_SUPPRESS_MS && lastDown > 0L) {
                    debug("Repeat-suppressed key $keyCode (${now - lastDown}ms)")
                    return false
                }
                lastDownTime[keyCode] = now

                pressedKeys[keyCode] = KeyState(
                    keyCode = keyCode,
                    isDown = true,
                    repeatCount = 0,
                    downTimestamp = now
                )
                activeChordKeys.add(keyCode)
                debug("KEY_DOWN: ${InputNormalizer.keyName(keyCode)} (chord: $activeChordKeys)")
            }
            InputEvent.Type.KEY_UP -> {
                val wasDown = pressedKeys[keyCode]?.isDown ?: false
                pressedKeys[keyCode] = KeyState(
                    keyCode = keyCode,
                    isDown = false,
                    repeatCount = pressedKeys[keyCode]?.repeatCount ?: 0
                )
                activeChordKeys.remove(keyCode)
                debug("KEY_UP: ${InputNormalizer.keyName(keyCode)} (was down: $wasDown)")

                // Handle HOLD_MODE release
                val holdMappings = findMappingsForKey(keyCode)
                for (mapping in holdMappings) {
                    if (mapping.holdMode == KeyMapping.HOLD_MODE_HOLD) {
                        actionExecutor?.onHoldRelease(mapping)
                    }
                }

                // Handle REPEAT_ON_RELEASE
                for (mapping in holdMappings) {
                    if (mapping.repeatPolicy == KeyMapping.REPEAT_ON_RELEASE) {
                        executeMappingAction(mapping)
                    }
                }
            }
            InputEvent.Type.KEY_REPEAT -> {
                val current = pressedKeys[keyCode]
                if (current != null) {
                    pressedKeys[keyCode] = current.copy(
                        repeatCount = current.repeatCount + 1
                    )
                }

                // Handle auto-repeat
                val repeatMappings = findMappingsForKey(keyCode)
                for (mapping in repeatMappings) {
                    if (mapping.repeatPolicy == KeyMapping.REPEAT_AUTO) {
                        executeMappingAction(mapping)
                    }
                }
                return true // consume repeats
            }
            else -> return false
        }

        // Find matching mappings
        val matchingMappings = findMappingsForKey(keyCode)
        if (matchingMappings.isEmpty()) {
            debug("No mapping for key ${InputNormalizer.keyName(keyCode)}")
            return false
        }

        // Process each matching mapping
        var consumed = false
        for (mapping in matchingMappings) {
            // Check chord requirements
            if (!isChordSatisfied(mapping)) continue

            when (mapping.holdMode) {
                KeyMapping.HOLD_MODE_TAP -> {
                    if (event.type == InputEvent.Type.KEY_DOWN) {
                        executeMappingAction(mapping)
                        consumed = true
                    }
                }
                KeyMapping.HOLD_MODE_HOLD -> {
                    if (event.type == InputEvent.Type.KEY_DOWN) {
                        actionExecutor?.onHoldStart(mapping)
                        consumed = true
                    }
                    // Release handled in KEY_UP above
                }
                KeyMapping.HOLD_MODE_TOGGLE -> {
                    if (event.type == InputEvent.Type.KEY_DOWN) {
                        val currentToggle = toggleStates[keyCode] ?: false
                        toggleStates[keyCode] = !currentToggle
                        if (!currentToggle) {
                            actionExecutor?.onHoldStart(mapping)
                        } else {
                            actionExecutor?.onHoldRelease(mapping)
                        }
                        consumed = true
                    }
                }
                KeyMapping.HOLD_MODE_LONG_PRESS -> {
                    if (event.type == InputEvent.Type.KEY_DOWN) {
                        // Start a timer — if key is still held after threshold, fire
                        val holdMs = mapping.holdDurationMs.takeIf { it > 0 } ?: LONG_PRESS_DEFAULT_MS
                        // We'll detect this on KEY_UP or after timeout
                        consumed = true
                    }
                }
            }

            // Movement keys update the movement vector
            if (mapping.mappingType == KeyMapping.TYPE_DPAD) {
                updateMovementVector()
                consumed = true
            }
        }

        return consumed
    }

    // ═══════════════════════════════════════════
    //  MOUSE INPUT PROCESSING
    // ═══════════════════════════════════════════

    /**
     * Process normalized mouse movement.
     */
    fun processMouseMove(deltaX: Float, deltaY: Float) {
        if (!StateTransitions.canAcceptInput(_state.value)) return

        val profile = activeProfile ?: return
        val sensitivity = profile.mouseSensitivity
        val deadZone = profile.mouseDeadZone
        val smoothing = profile.mouseSmoothing

        // Apply dead zone
        val magnitude = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (magnitude < deadZone) return

        // Apply sensitivity
        var dx = deltaX * sensitivity
        var dy = deltaY * sensitivity

        // Apply inversion
        if (profile.mouseInvertY) dy = -dy

        // Apply acceleration curve (simple quadratic)
        if (profile.mouseAcceleration > 0f) {
            val factor = 1f + (magnitude * profile.mouseAcceleration)
            dx *= factor
            dy *= factor
        }

        // Apply smoothing (exponential moving average)
        if (smoothing > 0f) {
            val prev = _aimDelta.value
            dx = prev.x * smoothing + dx * (1f - smoothing)
            dy = prev.y * smoothing + dy * (1f - smoothing)
        }

        _aimDelta.value = PointF(dx, dy)

        // If in aim mode, translate to camera movement
        if (isAimMode) {
            // Find mouse look mappings and apply
            val mouseLookMappings = mappingsSnapshot.filter {
                it.mappingType == KeyMapping.TYPE_MOUSE_LOOK
            }
            for (mapping in mouseLookMappings) {
                actionExecutor?.onMouseLook(mapping, dx, dy)
            }
        }

        debug("Mouse move: dx=$dx dy=$dy aimMode=$isAimMode")
    }

    /**
     * Process mouse button down/up.
     */
    fun processMouseButton(button: Int, isDown: Boolean) {
        if (!StateTransitions.canAcceptInput(_state.value)) return

        pressedMouseButtons[button] = isDown
        debug("Mouse ${if (isDown) "DOWN" else "UP"}: ${InputNormalizer.buttonName(button)}")

        // Find mappings for this mouse button
        // Mouse buttons are mapped with special keycodes
        val mouseKeyCode = mouseButtonToKeyCode(button)
        val matchingMappings = findMappingsForKey(mouseKeyCode)

        for (mapping in matchingMappings) {
            if (isDown) {
                when (mapping.holdMode) {
                    KeyMapping.HOLD_MODE_TAP -> executeMappingAction(mapping)
                    KeyMapping.HOLD_MODE_HOLD -> actionExecutor?.onHoldStart(mapping)
                    KeyMapping.HOLD_MODE_TOGGLE -> {
                        val current = toggleStates[mouseKeyCode] ?: false
                        toggleStates[mouseKeyCode] = !current
                        if (!current) actionExecutor?.onHoldStart(mapping)
                        else actionExecutor?.onHoldRelease(mapping)
                    }
                    else -> executeMappingAction(mapping)
                }
            } else {
                if (mapping.holdMode == KeyMapping.HOLD_MODE_HOLD) {
                    actionExecutor?.onHoldRelease(mapping)
                }
            }
        }
    }

    /**
     * Process mouse scroll wheel.
     */
    fun processScroll(deltaX: Float, deltaY: Float) {
        if (!StateTransitions.canAcceptInput(_state.value)) return

        debug("Scroll: dx=$deltaX dy=$deltaY")

        // Find scroll mappings
        val scrollMappings = mappingsSnapshot.filter {
            it.mappingType == KeyMapping.TYPE_SWIPE && it.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            // TODO: Add proper scroll mapping type
        }

        for (mapping in scrollMappings) {
            executeMappingAction(mapping)
        }
    }

    // ═══════════════════════════════════════════
    //  AIM MODE CONTROL
    // ═══════════════════════════════════════════

    fun enterAimMode() {
        if (transitionTo(EngineState.AIM_MODE)) {
            isAimMode = true
            // In Shizuku mode, start a persistent touch pointer for aiming
            if (shizukuMode && persistentInjector != null) {
                val (x, y) = sensitivityPipeline.getAimPosition()
                aimPointerId = persistentInjector?.touchDown(x, y)
                debug("AIM mode + persistent pointer at ($x, $y)")
            } else {
                debug("Entered AIM mode (fallback)")
            }
        }
    }

    fun exitAimMode() {
        isAimMode = false
        // Release persistent touch pointer
        aimPointerId?.let { pid ->
            persistentInjector?.touchUp(pid)
        }
        aimPointerId = null
        _aimDelta.value = PointF(0f, 0f)
        transitionTo(EngineState.READY)
        debug("Exited AIM mode")
    }

    fun toggleAimMode() {
        if (isAimMode) exitAimMode() else enterAimMode()
    }

    // ═══════════════════════════════════════════
    //  INPUT LOCK
    // ═══════════════════════════════════════════

    fun lockInput() {
        transitionTo(EngineState.INPUT_LOCKED)
        debug("Input LOCKED")
    }

    fun unlockInput() {
        transitionTo(EngineState.READY)
        debug("Input UNLOCKED")
    }

    // ═══════════════════════════════════════════
    //  INTERNAL HELPERS
    // ═══════════════════════════════════════════

    /**
     * Find all mappings that match a given keyCode, considering chords.
     */
    private fun findMappingsForKey(keyCode: Int): List<KeyMapping> {
        return mappingsSnapshot.filter { mapping ->
            mapping.keyCode == keyCode
        }
    }

    /**
     * Check if a mapping's chord requirements are satisfied.
     */
    private fun isChordSatisfied(mapping: KeyMapping): Boolean {
        if (mapping.chordKeysJson == "[]" || mapping.chordKeysJson.isBlank()) return true

        // Parse chord keycodes from JSON
        return try {
            val chordKeys = parseChordKeys(mapping.chordKeysJson)
            if (chordKeys.isEmpty()) return true
            // All chord keys must be currently pressed
            chordKeys.all { chordKey ->
                pressedKeys[chordKey]?.isDown == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chord keys: ${mapping.chordKeysJson}", e)
            true // If we can't parse, assume no chord requirement
        }
    }

    /**
     * Parse chord key JSON array "[59,32]" into a set of keycodes.
     */
    private fun parseChordKeys(json: String): Set<Int> {
        if (json == "[]" || json.isBlank()) return emptySet()
        return try {
            json.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().toInt() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Execute the action for a mapping based on its type.
     */
    private fun executeMappingAction(mapping: KeyMapping) {
        val executor = actionExecutor ?: run {
            debug("No ActionExecutor set — action dropped")
            return
        }

        when (mapping.mappingType) {
            KeyMapping.TYPE_TAP -> executor.onTap(mapping)
            KeyMapping.TYPE_SWIPE -> executor.onSwipe(mapping)
            KeyMapping.TYPE_HOLD_DRAG -> executor.onHoldDrag(mapping)
            KeyMapping.TYPE_MACRO -> executeMacro(mapping)
            KeyMapping.TYPE_DPAD -> {
                // Movement is handled by updateMovementVector()
                // This fires a directional action
                executor.onMovementAction(mapping, _movementVector.value)
            }
            KeyMapping.TYPE_MOUSE_LOOK -> {
                // Mouse look is handled in processMouseMove()
                // This shouldn't be called directly
            }
        }
    }

    /**
     * Execute a macro sequence for a mapping.
     */
    private fun executeMacro(mapping: KeyMapping) {
        cancelActiveMacro()

        val steps = sequencesSnapshot[mapping.id]
        if (steps.isNullOrEmpty()) {
            // Fallback: try legacy JSON macro
            debug("No sequence steps for mapping ${mapping.id}, trying legacy JSON")
            actionExecutor?.onMacro(mapping, emptyList())
            return
        }

        _isMacroRunning.value = true
        transitionTo(EngineState.MACRO_RUNNING)

        activeMacroJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                for (step in steps) {
                    if (!isActive) break
                    if (step.delayMs > 0) delay(step.delayMs)
                    actionExecutor?.onMacroStep(mapping, step)
                }
            } finally {
                _isMacroRunning.value = false
                if (_state.value == EngineState.MACRO_RUNNING) {
                    transitionTo(EngineState.READY)
                }
            }
        }
    }

    /**
     * Cancel any running macro.
     */
    private fun cancelActiveMacro() {
        activeMacroJob?.cancel()
        activeMacroJob = null
        _isMacroRunning.value = false
    }

    /**
     * Update the movement vector using the movement BindingGroup anchor.
     * Falls back to center of screen if no movement group exists.
     * Computes a normalized direction vector.
     */
    private fun updateMovementVector() {
        var dx = 0f
        var dy = 0f

        // Check all movement keys (WASD + arrows)
        val movementKeys = setOf(
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT
        )

        for (key in movementKeys) {
            if (pressedKeys[key]?.isDown == true) {
                when (key) {
                    KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> dy -= 1f
                    KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> dy += 1f
                    KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> dx -= 1f
                    KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> dx += 1f
                }
            }
        }

        // Normalize to unit circle for diagonal movement
        if (dx != 0f && dy != 0f) {
            val length = sqrt(dx * dx + dy * dy)
            dx /= length
            dy /= length
        }

        _movementVector.value = PointF(dx, dy)
    }

    /**
     * Convert a MotionEvent button constant to a virtual keycode.
     */
    private fun mouseButtonToKeyCode(button: Int): Int {
        return when (button) {
            android.view.MotionEvent.BUTTON_PRIMARY -> 10001   // Custom code for left click
            android.view.MotionEvent.BUTTON_SECONDARY -> 10002 // Custom code for right click
            android.view.MotionEvent.BUTTON_TERTIARY -> 10003  // Custom code for middle click
            android.view.MotionEvent.BUTTON_BACK -> 10004      // Side back
            android.view.MotionEvent.BUTTON_FORWARD -> 10005   // Side forward
            else -> 10000 + button
        }
    }

    // ═══════════════════════════════════════════
    //  STATE MACHINE
    // ═══════════════════════════════════════════

    private fun transitionTo(newState: EngineState): Boolean {
        val current = _state.value
        if (current == newState) return true

        if (!StateTransitions.canTransition(current, newState)) {
            Log.e(TAG, "BLOCKED transition: $current → $newState")
            return false
        }

        _state.value = newState
        debug("State: $current → $newState")
        return true
    }

    // ═══════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════

    fun getActiveProfile(): GameProfile? = activeProfile
    fun getMappingsSnapshot(): List<KeyMapping> = mappingsSnapshot
    fun getGroupsSnapshot(): List<BindingGroup> = groupsSnapshot
    fun getPressedKeys(): Map<Int, KeyState> = pressedKeys.toMap()
    fun getPressedMouseButtons(): Map<Int, Boolean> = pressedMouseButtons.toMap()
    fun isKeyDown(keyCode: Int): Boolean = pressedKeys[keyCode]?.isDown == true
    fun isMouseButtonDown(button: Int): Boolean = pressedMouseButtons[button] == true
    fun isInAimMode(): Boolean = isAimMode
    fun getEventCount(): Long = eventCount

    // ═══════════════════════════════════════════
    //  DEBUG
    // ═══════════════════════════════════════════

    private fun debug(msg: String) {
        if (debugEnabled) {
            Log.d(TAG, msg)
            _debugLog.value = msg
        }
    }

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    fun getDebugSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== RuntimeEngine Debug ===")
        sb.appendLine("State: ${_state.value}")
        sb.appendLine("Profile: ${activeProfile?.name ?: "none"}")
        sb.appendLine("Mappings: ${mappingsSnapshot.size}")
        sb.appendLine("Groups: ${groupsSnapshot.size}")
        sb.appendLine("Pressed keys: ${pressedKeys.filter { it.value.isDown }.keys.map { InputNormalizer.keyName(it) }}")
        sb.appendLine("Mouse buttons: ${pressedMouseButtons.filter { it.value }.keys.map { InputNormalizer.buttonName(it) }}")
        sb.appendLine("Movement: ${_movementVector.value}")
        sb.appendLine("Aim delta: ${_aimDelta.value}")
        sb.appendLine("Aim mode: $isAimMode")
        sb.appendLine("Toggles: ${toggleStates.filter { it.value }.keys.map { InputNormalizer.keyName(it) }}")
        sb.appendLine("Events processed: $eventCount")
        return sb.toString()
    }
}
