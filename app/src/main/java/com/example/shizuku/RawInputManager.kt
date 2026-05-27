package com.example.shizuku

import android.os.Build
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw input capture via Shizuku.
 *
 * Captures raw mouse deltas, keyboard state, and button events
 * directly from /dev/input/ devices using Shizuku's elevated permissions.
 *
 * This is what Panda Mouse Pro does — bypasses Android's input framework
 * and reads raw kernel input events.
 */
class RawInputManager {

    companion object {
        private const val TAG = "RawInputManager"

        // Linux input event struct: timeval(16 bytes on 64-bit) + type(2) + code(2) + value(4)
        private const val EVENT_STRUCT_SIZE = 24

        // Linux input event types
        private const val EV_SYN = 0x00
        private const val EV_KEY = 0x01
        private const val EV_REL = 0x02
        private const val EV_ABS = 0x03

        // Relative axes
        private const val REL_X = 0x00
        private const val REL_Y = 0x01
        private const val REL_WHEEL = 0x08
        private const val REL_HWHEEL = 0x06

        // Key codes (Linux)
        private const val BTN_MOUSE = 0x110
        private const val BTN_RIGHT = 0x111
        private const val BTN_MIDDLE = 0x112
        private const val BTN_SIDE = 0x113
        private const val BTN_EXTRA = 0x114
    }

    // ── RAW EVENT DATA ──

    data class RawMouseDelta(
        val dx: Float,
        val dy: Float,
        val timestamp: Long
    )

    data class RawKeyEvent(
        val keyCode: Int,
        val isDown: Boolean,
        val timestamp: Long
    )

    data class RawMouseButton(
        val button: Int, // Linux button code
        val isDown: Boolean,
        val timestamp: Long
    )

    data class RawScroll(
        val delta: Float,
        val timestamp: Long
    )

    // ── EVENT FLOWS ──

    private val _mouseDeltas = MutableSharedFlow<RawMouseDelta>(extraBufferCapacity = 64)
    val mouseDeltas: SharedFlow<RawMouseDelta> = _mouseDeltas.asSharedFlow()

    private val _keyEvents = MutableSharedFlow<RawKeyEvent>(extraBufferCapacity = 64)
    val keyEvents: SharedFlow<RawKeyEvent> = _keyEvents.asSharedFlow()

    private val _mouseButtons = MutableSharedFlow<RawMouseButton>(extraBufferCapacity = 16)
    val mouseButtons: SharedFlow<RawMouseButton> = _mouseButtons.asSharedFlow()

    private val _scrollEvents = MutableSharedFlow<RawScroll>(extraBufferCapacity = 16)
    val scrollEvents: SharedFlow<RawScroll> = _scrollEvents.asSharedFlow()

    // ── DEVICE TRACKING ──

    private val mouseDeviceIds = mutableSetOf<Int>()
    private val keyboardDeviceIds = mutableSetOf<Int>()

    // ── STATE ──

    private var isCapturing = false
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Accumulated mouse delta (reset each frame)
    private var accumulatedDx = 0f
    private var accumulatedDy = 0f

    // ═══════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════

    /**
     * Enumerate and register input devices.
     * Call after ShizukuHiddenApi is initialized.
     */
    fun enumerateDevices() {
        mouseDeviceIds.clear()
        keyboardDeviceIds.clear()

        val mice = ShizukuHiddenApi.findMouseDevices()
        for (device in mice) {
            mouseDeviceIds.add(device.id)
            Log.i(TAG, "Mouse device: ${device.name} (id=${device.id}, sources=${device.sources})")
        }

        val keyboards = ShizukuHiddenApi.findKeyboardDevices()
        for (device in keyboards) {
            keyboardDeviceIds.add(device.id)
            Log.i(TAG, "Keyboard device: ${device.name} (id=${device.id})")
        }

        Log.i(TAG, "Found ${mouseDeviceIds.size} mice, ${keyboardDeviceIds.size} keyboards")
    }

    // ═══════════════════════════════════════════
    //  RAW CAPTURE — /dev/input/ reading
    // ═══════════════════════════════════════════

    /**
     * Start capturing raw events from /dev/input/ devices.
     * Requires Shizuku (ADB-level permissions to read /dev/input/).
     */
    fun startCapture() {
        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return
        }

        if (!ShizukuHiddenApi.isShizukuAvailable) {
            Log.w(TAG, "Shizuku not available, cannot capture raw input")
            return
        }

        isCapturing = true

        // Try to read from mouse devices
        captureJob = scope.launch {
            for (deviceId in mouseDeviceIds) {
                launch { captureDeviceLoop(deviceId) }
            }
            // Also capture from event0, event1, etc. as fallback
            for (i in 0..15) {
                if (i !in mouseDeviceIds) {
                    launch { captureDeviceLoop(i) }
                }
            }
        }

        Log.i(TAG, "Started raw input capture")
    }

    /**
     * Capture events from a single /dev/input/eventN device.
     */
    private suspend fun captureDeviceLoop(deviceId: Int) {
        val path = "/dev/input/event$deviceId"
        Log.d(TAG, "Attempting to capture from $path")

        try {
            val fd = openInputDevice(path) ?: return
            val inputStream = FileInputStream(fd)
            val buffer = ByteBuffer.allocate(EVENT_STRUCT_SIZE)
            buffer.order(ByteOrder.nativeOrder())

            var lastRelX = 0f
            var lastRelY = 0f

            while (isCapturing && isActive) {
                buffer.clear()
                val bytesRead = inputStream.read(buffer.array())

                if (bytesRead < EVENT_STRUCT_SIZE) {
                    if (bytesRead < 0) break
                    continue
                }

                buffer.position(0)
                // Skip timeval (16 bytes)
                buffer.position(16)
                val type = buffer.getShort().toInt() and 0xFFFF
                val code = buffer.getShort().toInt() and 0xFFFF
                val value = buffer.getInt()

                val timestamp = System.nanoTime()

                when (type) {
                    EV_REL -> {
                        when (code) {
                            REL_X -> {
                                accumulatedDx += value.toFloat()
                                _mouseDeltas.tryEmit(RawMouseDelta(value.toFloat(), 0f, timestamp))
                            }
                            REL_Y -> {
                                accumulatedDy += value.toFloat()
                                _mouseDeltas.tryEmit(RawMouseDelta(0f, value.toFloat(), timestamp))
                            }
                            REL_WHEEL -> {
                                _scrollEvents.tryEmit(RawScroll(value.toFloat(), timestamp))
                            }
                            REL_HWHEEL -> {
                                _scrollEvents.tryEmit(RawScroll(value.toFloat() * 0.5f, timestamp))
                            }
                        }
                    }
                    EV_KEY -> {
                        when {
                            code >= BTN_MOUSE && code <= BTN_EXTRA -> {
                                _mouseButtons.tryEmit(RawMouseButton(code, value == 1, timestamp))
                            }
                            else -> {
                                _keyEvents.tryEmit(RawKeyEvent(code, value == 1, timestamp))
                            }
                        }
                    }
                }
            }

            inputStream.close()
            Log.d(TAG, "Stopped capturing from $path")

        } catch (e: Exception) {
            Log.d(TAG, "Device $path not accessible: ${e.message}")
        }
    }

    /**
     * Open /dev/input/eventN via Shizuku.
     * Uses Shizuku's elevated permissions to read raw input devices.
     */
    private fun openInputDevice(path: String): java.io.FileDescriptor? {
        return try {
            // Use Shizuku's privileged file access
            // The ParcelFileDescriptor approach
            val pfdClass = Class.forName("android.os.ParcelFileDescriptor")
            val openMethod = pfdClass.getDeclaredMethod("open", java.io.File::class.java, Int::class.javaPrimitiveType)

            val file = java.io.File(path)
            val pfd = openMethod.invoke(null, file, 0 /* MODE_READ */) as? android.os.ParcelFileDescriptor
            pfd?.fileDescriptor
        } catch (e: Throwable) {
            Log.d(TAG, "Cannot open $path via ParcelFileDescriptor: ${e.message}")

            // Fallback: try direct file open (works if running with ADB permissions)
            try {
                val fis = FileInputStream(path)
                fis.fd
            } catch (e2: Throwable) {
                Log.d(TAG, "Cannot open $path directly either: ${e2.message}")
                null
            }
        }
    }

    // ═══════════════════════════════════════════
    //  DELTA ACCUMULATION (frame-based)
    // ═══════════════════════════════════════════

    /**
     * Consume accumulated mouse delta and reset.
     * Call this once per frame from the runtime loop.
     */
    fun consumeAccumulatedDelta(): Pair<Float, Float> {
        val dx = accumulatedDx
        val dy = accumulatedDy
        accumulatedDx = 0f
        accumulatedDy = 0f
        return dx to dy
    }

    // ═══════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null
        Log.i(TAG, "Stopped raw input capture")
    }

    fun destroy() {
        stopCapture()
        scope.cancel()
        mouseDeviceIds.clear()
        keyboardDeviceIds.clear()
        Log.i(TAG, "RawInputManager destroyed")
    }
}
