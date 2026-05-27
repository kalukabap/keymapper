package com.example.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Deep runtime diagnostics.
 *
 * Logs every stage of the input pipeline:
 *   raw device event → normalized event → runtime state → translation → injection
 *
 * Each log entry is tagged with a sequence number and timestamp for correlation.
 */
object DeepDiagnostics {

    private const val TAG = "ApexMapper.Diag"
    private const val MAX_LOG_ENTRIES = 1000

    // ── LOG ENTRY ──

    enum class Stage {
        RAW_INPUT,          // Raw device event from /dev/input/
        NORMALIZED,         // After InputNormalizer
        ENGINE_STATE,       // Runtime state update
        TRANSLATION,        // Sensitivity pipeline output
        INJECTION_START,    // Touch DOWN / MOVE dispatched
        INJECTION_RESULT,   // Injection success/failure
        POINTER_LIFECYCLE,  // Pointer created/moved/released
        CANCELLATION,       // Why something was cancelled
        FRAME_TICK,         // Frame-based loop timing
        DEVICE_EVENT        // Device connect/disconnect
    }

    data class LogEntry(
        val sequence: Long,
        val timestamp: Long,
        val stage: Stage,
        val tag: String,
        val message: String,
        val data: Map<String, Any> = emptyMap()
    )

    // ── LOG FLOW ──

    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 256)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    // ── CIRCULAR BUFFER ──

    private val logBuffer = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)
    private var sequenceCounter = 0L

    // ── ENABLED FLAGS ──

    var enabled = true
    var logRawInput = true
    var logNormalized = true
    var logEngineState = true
    var logTranslation = true
    var logInjection = true
    var logPointerLifecycle = true
    var logCancellations = true
    var logFrameTicks = false  // noisy, off by default
    var logDeviceEvents = true

    // ═══════════════════════════════════════════
    //  LOGGING METHODS
    // ═══════════════════════════════════════════

    fun logRawInput(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logRawInput) return
        emit(Stage.RAW_INPUT, tag, message, data)
    }

    fun logNormalized(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logNormalized) return
        emit(Stage.NORMALIZED, tag, message, data)
    }

    fun logEngineState(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logEngineState) return
        emit(Stage.ENGINE_STATE, tag, message, data)
    }

    fun logTranslation(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logTranslation) return
        emit(Stage.TRANSLATION, tag, message, data)
    }

    fun logInjectionStart(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logInjection) return
        emit(Stage.INJECTION_START, tag, message, data)
    }

    fun logInjectionResult(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logInjection) return
        emit(Stage.INJECTION_RESULT, tag, message, data)
    }

    fun logPointerLifecycle(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logPointerLifecycle) return
        emit(Stage.POINTER_LIFECYCLE, tag, message, data)
    }

    fun logCancellation(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logCancellations) return
        emit(Stage.CANCELLATION, tag, message, data)
    }

    fun logFrameTick(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logFrameTicks) return
        emit(Stage.FRAME_TICK, tag, message, data)
    }

    fun logDeviceEvent(tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        if (!enabled || !logDeviceEvents) return
        emit(Stage.DEVICE_EVENT, tag, message, data)
    }

    // ═══════════════════════════════════════════
    //  INTERNAL
    // ═══════════════════════════════════════════

    private fun emit(stage: Stage, tag: String, message: String, data: Map<String, Any>) {
        val entry = LogEntry(
            sequence = sequenceCounter++,
            timestamp = System.nanoTime(),
            stage = stage,
            tag = tag,
            message = message,
            data = data
        )

        // Add to circular buffer
        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOG_ENTRIES) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(entry)
        }

        // Emit to flow
        _logFlow.tryEmit(entry)

        // Log to Android logcat
        val dataStr = if (data.isNotEmpty()) data.entries.joinToString(", ") { "${it.key}=${it.value}" } else ""
        val fullMsg = if (dataStr.isNotEmpty()) "$message [$dataStr]" else message
        Log.d("${TAG}.${stage.name}", "[$tag] $fullMsg")
    }

    // ═══════════════════════════════════════════
    //  QUERY
    // ═══════════════════════════════════════════

    fun getRecentEntries(count: Int = 50): List<LogEntry> {
        synchronized(logBuffer) {
            return logBuffer.takeLast(count)
        }
    }

    fun getEntriesByStage(stage: Stage, count: Int = 50): List<LogEntry> {
        synchronized(logBuffer) {
            return logBuffer.filter { it.stage == stage }.takeLast(count)
        }
    }

    fun clear() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        sequenceCounter = 0
    }

    fun getStats(): String {
        synchronized(logBuffer) {
            val byStage = logBuffer.groupBy { it.stage }
            return buildString {
                appendLine("=== Diagnostics Stats ===")
                appendLine("Total entries: ${logBuffer.size}")
                for (stage in Stage.entries) {
                    val count = byStage[stage]?.size ?: 0
                    appendLine("  ${stage.name}: $count")
                }
            }
        }
    }
}
