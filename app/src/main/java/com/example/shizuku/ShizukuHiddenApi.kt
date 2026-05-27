package com.example.shizuku

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Shizuku-backed access to Android hidden APIs.
 *
 * Uses Shizuku's privileged binder to call @hide methods on:
 * - InputManager (injectInputEvent, createInputMonitor, getInputDeviceIds)
 * - IInputManager (the AIDL service interface)
 *
 * Falls back gracefully if Shizuku is not available.
 */
object ShizukuHiddenApi {

    private const val TAG = "ShizukuHiddenApi"

    // ── CAPABILITY FLAGS ──

    var canInjectInput = false
        private set
    var canCreateMonitor = false
        private set
    var canEnumerateDevices = false
        private set
    var isShizukuAvailable = false
        private set

    // ── REFLECTED HANDLES ──

    private var inputManagerInstance: Any? = null
    private var iInputManagerBinder: IBinder? = null

    // Reflected methods
    private var injectInputEventMethod: Method? = null
    private var createInputMonitorMethod: Method? = null
    private var getInputDeviceIdsMethod: Method? = null
    private var getInputDeviceMethod: Method? = null

    // ═══════════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════════

    /**
     * Initialize Shizuku hidden API access.
     * Call this AFTER Shizuku permission is granted.
     * Returns true if at least injection is available.
     */
    fun initialize(context: Context): Boolean {
        Log.i(TAG, "Initializing Shizuku hidden API (Android ${Build.VERSION.SDK_INT})")

        try {
            // Get InputManager instance via reflection
            val imClass = InputManager::class.java
            val getInstanceMethod = imClass.getDeclaredMethod("getInstance")
            getInstanceMethod.isAccessible = true
            inputManagerInstance = getInstanceMethod.invoke(null)

            if (inputManagerInstance == null) {
                Log.e(TAG, "Failed to get InputManager instance")
                return false
            }

            // Get the IInputManager binder via Shizuku
            iInputManagerBinder = getIInputManagerBinder()

            // Discover available methods via reflection
            discoverMethods(imClass)

            isShizukuAvailable = true
            Log.i(TAG, "Shizuku initialized: inject=$canInjectInput, monitor=$canCreateMonitor, enumerate=$canEnumerateDevices")
            return canInjectInput

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Shizuku hidden API", e)
            isShizukuAvailable = false
            return false
        }
    }

    private fun discoverMethods(imClass: Class<*>) {
        // injectInputEvent(InputEvent, int mode)
        try {
            injectInputEventMethod = imClass.getDeclaredMethod(
                "injectInputEvent",
                android.view.InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            injectInputEventMethod?.isAccessible = true
            canInjectInput = true
            Log.d(TAG, "  ✓ injectInputEvent available")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "  ✗ injectInputEvent not found")
        }

        // createInputMonitor(String name, int displayId)
        try {
            createInputMonitorMethod = if (Build.VERSION.SDK_INT >= 33) {
                // Android 13+: createInputMonitor(String, int)
                imClass.getDeclaredMethod(
                    "createInputMonitor",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
            } else {
                // Android 12-: createInputMonitor(int displayId)
                imClass.getDeclaredMethod(
                    "createInputMonitor",
                    Int::class.javaPrimitiveType
                )
            }
            createInputMonitorMethod?.isAccessible = true
            canCreateMonitor = true
            Log.d(TAG, "  ✓ createInputMonitor available")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "  ✗ createInputMonitor not found")
        }

        // getInputDeviceIds()
        try {
            getInputDeviceIdsMethod = imClass.getDeclaredMethod("getInputDeviceIds")
            getInputDeviceIdsMethod?.isAccessible = true
            canEnumerateDevices = true
            Log.d(TAG, "  ✓ getInputDeviceIds available")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "  ✗ getInputDeviceIds not found")
        }

        // getInputDevice(int id)
        try {
            getInputDeviceMethod = imClass.getDeclaredMethod(
                "getInputDevice",
                Int::class.javaPrimitiveType
            )
            getInputDeviceMethod?.isAccessible = true
            Log.d(TAG, "  ✓ getInputDevice available")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "  ✗ getInputDevice not found")
        }
    }

    // ═══════════════════════════════════════════
    //  SHIZUKU BINDER ACCESS
    // ═══════════════════════════════════════════

    /**
     * Get the IInputManager binder through Shizuku's privileged context.
     * Shizuku runs as ADB user (uid 2000) which has access to system services.
     */
    private fun getIInputManagerBinder(): IBinder? {
        return try {
            // Method 1: Get via ServiceManager (hidden API, accessible through Shizuku)
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getDeclaredMethod("getService", String::class.java)
            getServiceMethod.isAccessible = true
            val binder = getServiceMethod.invoke(null, "input") as? IBinder

            if (binder != null) {
                Log.d(TAG, "Got IInputManager binder via ServiceManager")
                return binder
            }

            // Method 2: Get via InputManager's internal field
            val imClass = inputManagerInstance?.javaClass
            val mImField = imClass?.getDeclaredField("mIm")
            mImField?.isAccessible = true
            val iInputManager = mImField?.get(inputManagerInstance)

            // Get the binder from IInputManager.Stub
            val asBinderMethod = iInputManager?.javaClass?.getMethod("asBinder")
            val result = asBinderMethod?.invoke(iInputManager) as? IBinder
            Log.d(TAG, "Got IInputManager binder via reflection")
            result

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get IInputManager binder", e)
            null
        }
    }

    // ═══════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════

    /**
     * Inject an InputEvent into the system.
     * mode: 0 = async, 1 = wait for finish, 2 = wait for result
     */
    fun injectInputEvent(event: android.view.InputEvent, mode: Int = 0): Boolean {
        if (!canInjectInput || injectInputEventMethod == null) {
            Log.w(TAG, "injectInputEvent not available")
            return false
        }

        return try {
            val result = injectInputEventMethod!!.invoke(inputManagerInstance, event, mode) as Boolean
            result
        } catch (e: Throwable) {
            Log.e(TAG, "injectInputEvent failed", e)
            false
        }
    }

    /**
     * Create an InputMonitor for raw event capture.
     * Returns the InputMonitor object (use via reflection).
     */
    fun createInputMonitor(displayId: Int = 0): Any? {
        if (!canCreateMonitor || createInputMonitorMethod == null) {
            Log.w(TAG, "createInputMonitor not available")
            return null
        }

        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                createInputMonitorMethod!!.invoke(inputManagerInstance, "ApexMapper", displayId)
            } else {
                createInputMonitorMethod!!.invoke(inputManagerInstance, displayId)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "createInputMonitor failed", e)
            null
        }
    }

    /**
     * Get all input device IDs.
     */
    fun getInputDeviceIds(): IntArray {
        if (!canEnumerateDevices || getInputDeviceIdsMethod == null) {
            return IntArray(0)
        }

        return try {
            getInputDeviceIdsMethod!!.invoke(inputManagerInstance) as IntArray
        } catch (e: Throwable) {
            Log.e(TAG, "getInputDeviceIds failed", e)
            IntArray(0)
        }
    }

    /**
     * Get an InputDevice by ID.
     */
    fun getInputDevice(id: Int): android.view.InputDevice? {
        if (getInputDeviceMethod == null) return null

        return try {
            getInputDeviceMethod!!.invoke(inputManagerInstance, id) as? android.view.InputDevice
        } catch (e: Throwable) {
            Log.e(TAG, "getInputDevice($id) failed", e)
            null
        }
    }

    /**
     * Find all mouse/touchpad devices.
     */
    fun findMouseDevices(): List<android.view.InputDevice> {
        val devices = mutableListOf<android.view.InputDevice>()
        for (id in getInputDeviceIds()) {
            val device = getInputDevice(id) ?: continue
            if (!device.supportsSource(android.view.InputDevice.SOURCE_MOUSE)) continue
            if (device.isVirtual) continue
            devices.add(device)
        }
        return devices
    }

    /**
     * Find all keyboard devices.
     */
    fun findKeyboardDevices(): List<android.view.InputDevice> {
        val devices = mutableListOf<android.view.InputDevice>()
        for (id in getInputDeviceIds()) {
            val device = getInputDevice(id) ?: continue
            if (!device.supportsSource(android.view.InputDevice.SOURCE_KEYBOARD)) continue
            if (device.isVirtual) continue
            devices.add(device)
        }
        return devices
    }

    /**
     * Check if Shizuku permission is granted.
     */
    fun checkPermission(): Boolean {
        return try {
            val pmClass = Class.forName("rikka.shizuku.Shizuku")
            val checkMethod = pmClass.getMethod("checkSelfPermission")
            val result = checkMethod.invoke(null) as Int
            result == 0 // PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku permission check failed", e)
            false
        }
    }

    fun isAvailable(): Boolean = isShizukuAvailable

    fun injectScrollEvent(scrollAmount: Float): Boolean {
        if (!isShizukuAvailable) return false
        try {
            val now = android.os.SystemClock.uptimeMillis()
            val event = android.view.MotionEvent.obtain(now, now, 2 /* ACTION_SCROLL */, 0f, 0f, 0)
            // Set AXIS_VSCROLL (9) via reflection
            val method = event.javaClass.getMethod("setAxisValue", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            method.invoke(event, 9, scrollAmount)
            injectInputEvent(event)
            event.recycle()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to inject scroll event", e)
            return false
        }
    }

    /**
     * Cleanup resources.
     */
    fun destroy() {
        inputManagerInstance = null
        iInputManagerBinder = null
        injectInputEventMethod = null
        createInputMonitorMethod = null
        getInputDeviceIdsMethod = null
        getInputDeviceMethod = null
        canInjectInput = false
        canCreateMonitor = false
        canEnumerateDevices = false
        isShizukuAvailable = false
        Log.i(TAG, "ShizukuHiddenApi destroyed")
    }
}
