package com.example.bridge;

/**
 * Small JNI facade used by the dashboard to show which native input helpers are
 * packaged with the APK. The runtime service still owns native event reading.
 */
public final class NativeInputBridge {
    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("mouse_read");
            loaded = true;
        } catch (UnsatisfiedLinkError ignored) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private NativeInputBridge() {
    }

    public static boolean isAvailable() {
        return LIBRARY_LOADED;
    }

    public static String capabilitiesSummary() {
        if (!LIBRARY_LOADED) {
            return "Native helper unavailable in this runtime";
        }
        return nativeCapabilitiesSummary();
    }

    public static int nativeInputSlotLimit() {
        if (!LIBRARY_LOADED) {
            return 0;
        }
        return nativeMaxInputDevices();
    }

    private static native String nativeCapabilitiesSummary();

    private static native int nativeMaxInputDevices();
}
