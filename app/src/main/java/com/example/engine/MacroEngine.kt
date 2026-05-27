package com.example.engine

import android.util.Log
import com.example.data.ActionSequence
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Macro recording, playback, and management.
 * Records key/touch sequences with timing, plays them back
 * with proper step execution, supports loops and cancellation.
 */
class MacroEngine(
    private val scheduler: ActionScheduler,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MacroEngine"
    }

    // ── RECORDING STATE ──

    data class RecordedStep(
        val action: ActionScheduler.Action,
        val timestampMs: Long, // relative to recording start
        val label: String = ""
    )

    private val recordedSteps = mutableListOf<RecordedStep>()
    private var recordingStartTime = 0L
    val isRecording = AtomicBoolean(false)

    // ── PLAYBACK STATE ──

    private var playbackJob: Job? = null
    val isPlaying = AtomicBoolean(false)
    private var currentMacroId: Int = -1

    // ── MACRO STORAGE ──

    // In-memory macro library (persists to Room via ActionSequence table)
    private val macroLibrary = mutableMapOf<Int, List<RecordedStep>>()

    // ── RECORDING ──

    /**
     * Start recording a macro.
     */
    fun startRecording() {
        recordedSteps.clear()
        recordingStartTime = System.currentTimeMillis()
        isRecording.set(true)
        Log.i(TAG, "Recording started")
    }

    /**
     * Record a step during recording.
     * Call this when a key/touch event happens during recording.
     */
    fun recordStep(action: ActionScheduler.Action, label: String = "") {
        if (!isRecording.get()) return
        val timestamp = System.currentTimeMillis() - recordingStartTime
        recordedSteps.add(RecordedStep(action, timestamp, label))
        Log.d(TAG, "Recorded step at ${timestamp}ms: $label")
    }

    /**
     * Stop recording and return the recorded steps.
     */
    fun stopRecording(): List<RecordedStep> {
        isRecording.set(false)
        Log.i(TAG, "Recording stopped: ${recordedSteps.size} steps")
        return recordedSteps.toList()
    }

    /**
     * Save a recorded macro to the library.
     */
    fun saveMacro(id: Int, steps: List<RecordedStep>) {
        macroLibrary[id] = steps
        Log.i(TAG, "Saved macro #$id with ${steps.size} steps")
    }

    /**
     * Load a macro from the library.
     */
    fun getMacro(id: Int): List<RecordedStep>? = macroLibrary[id]

    /**
     * Delete a macro from the library.
     */
    fun deleteMacro(id: Int) {
        macroLibrary.remove(id)
        Log.i(TAG, "Deleted macro #$id")
    }

    /**
     * Get all saved macro IDs.
     */
    fun getMacroIds(): List<Int> = macroLibrary.keys.toList()

    // ── PLAYBACK ──

    /**
     * Play a macro by ID.
     * @param loopCount Number of times to loop (0 = once, -1 = infinite)
     * @param stopKeyCode Key code that stops playback when pressed
     */
    fun play(id: Int, loopCount: Int = 0, stopKeyCode: Int = -1) {
        val steps = macroLibrary[id]
        if (steps.isNullOrEmpty()) {
            Log.w(TAG, "Macro #$id not found or empty")
            return
        }

        stop() // Stop any current playback
        currentMacroId = id

        playbackJob = scope.launch {
            isPlaying.set(true)
            Log.i(TAG, "Playing macro #$id (${steps.size} steps, loops=$loopCount)")

            var loopsDone = 0
            while (isActive && (loopCount == -1 || loopsDone <= loopCount)) {
                for (step in steps) {
                    if (!isActive) break
                    // Calculate delay from previous step
                    val prevTimestamp = if (steps.indexOf(step) > 0) {
                        steps[steps.indexOf(step) - 1].timestampMs
                    } else 0L
                    val delayMs = step.timestampMs - prevTimestamp
                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                    scheduler.execute(step.action)
                }
                loopsDone++
            }

            isPlaying.set(false)
            currentMacroId = -1
            Log.i(TAG, "Macro #$id playback complete")
        }
    }

    /**
     * Play a macro from ActionSequence list (from Room).
     */
    fun playFromSequences(sequences: List<ActionSequence>, loopCount: Int = 0) {
        if (sequences.isEmpty()) return

        stop()
        playbackJob = scope.launch {
            isPlaying.set(true)
            Log.i(TAG, "Playing ${sequences.size} sequence steps")

            var loopsDone = 0
            while (isActive && (loopCount == -1 || loopsDone <= loopCount)) {
                for (seq in sequences.sortedBy { it.stepIndex }) {
                    if (!isActive) break
                    if (seq.delayMs > 0) delay(seq.delayMs)

                    val action = when (seq.actionType) {
                        ActionSequence.TYPE_TAP -> ActionScheduler.Action.Tap(
                            x = seq.targetX ?: 0f,
                            y = seq.targetY ?: 0f,
                            holdMs = seq.holdMs
                        )
                        ActionSequence.TYPE_SWIPE -> {
                            val endX = seq.endX ?: (seq.targetX ?: 0f) + 100f
                            val endY = seq.endY ?: (seq.targetY ?: 0f)
                            ActionScheduler.Action.Swipe(
                                startX = seq.targetX ?: 0f,
                                startY = seq.targetY ?: 0f,
                                endX = endX, endY = endY,
                                durationMs = seq.holdMs
                            )
                        }
                        ActionSequence.TYPE_HOLD -> ActionScheduler.Action.Hold(
                            x = seq.targetX ?: 0f,
                            y = seq.targetY ?: 0f,
                            durationMs = seq.holdMs
                        )
                        ActionSequence.TYPE_RELEASE -> {
                            // Release is handled by the previous hold
                            continue
                        }
                        else -> ActionScheduler.Action.Tap(
                            x = seq.targetX ?: 0f,
                            y = seq.targetY ?: 0f,
                            holdMs = seq.holdMs
                        )
                    }
                    scheduler.execute(action)
                }
                loopsDone++
            }

            isPlaying.set(false)
            Log.i(TAG, "Sequence playback complete")
        }
    }

    /**
     * Stop current playback.
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        isPlaying.set(false)
        scheduler.cancelAll()
        currentMacroId = -1
        Log.i(TAG, "Playback stopped")
    }

    /**
     * Check if a specific macro is playing.
     */
    fun isPlayingMacro(id: Int): Boolean = isPlaying.get() && currentMacroId == id

    /**
     * Clear all recorded steps (reset).
     */
    fun clear() {
        recordedSteps.clear()
        isRecording.set(false)
    }
}
