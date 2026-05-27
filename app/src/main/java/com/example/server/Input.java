package com.example.server;

import android.hardware.input.InputManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.genymobile.scrcpy.Point;
import com.genymobile.scrcpy.Pointer;
import com.genymobile.scrcpy.PointersState;

import java.lang.reflect.Method;

/**
 * Touch event injector using InputManager.injectInputEvent().
 * Based on scrcpy's touch injection approach.
 * Must run in a privileged process (shell/root via app_process).
 */
public class Input {

    private static final String TAG = "ApexMapper-Input";
    private static Method injectInputEventMethod;
    private static Object inputManager;
    private static Method setDisplayIdMethod;

    private final PointersState pointersState = new PointersState();
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[PointersState.MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[PointersState.MAX_POINTERS];
    private final int displayId;
    private long lastTouchDown;
    private int pointerCount = 0;

    static {
        try {
            Class<?> imClass = Class.forName("android.hardware.input.InputManager");
            Class<?> inputEventClass = Class.forName("android.view.InputEvent");
            Method getInstance = imClass.getMethod("getInstance");
            inputManager = getInstance.invoke(null);
            injectInputEventMethod = imClass.getMethod("injectInputEvent",
                    inputEventClass, int.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to init InputManager reflection", e);
        }
    }

    public Input(int displayId) {
        this.displayId = displayId;
        initPointers();
    }

    private void initPointers() {
        for (int i = 0; i < PointersState.MAX_POINTERS; ++i) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;

            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.orientation = 0;
            coords.size = 0;

            pointerProperties[i] = props;
            pointerCoords[i] = coords;
        }
    }

    public boolean noPointersDown() {
        if (pointerCount > 0) {
            pointerCount = pointersState.update(pointerProperties, pointerCoords);
            for (int i = 0; i < pointerCount; i++) {
                if (!pointersState.get(i).isUp()) return false;
            }
        }
        return true;
    }

    public void injectTouch(int action, int pointerId, float pressure, float x, float y) {
        long now = SystemClock.uptimeMillis();
        Point point = new Point(x, y);

        int pointerIndex = pointersState.getPointerIndex(pointerId);
        if (pointerIndex == -1) {
            Log.e(TAG, "Too many pointers for touch event");
            return;
        }
        Pointer pointer = pointersState.get(pointerIndex);
        pointer.setPoint(point);
        pointer.setPressure(pressure);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_HOVER_MOVE)
            pointer.setUp(true);
        else if (action == MotionEvent.ACTION_DOWN) pointer.setUp(false);

        int source = InputDevice.SOURCE_TOUCHSCREEN;

        pointerCount = pointersState.update(pointerProperties, pointerCoords);

        if (pointerCount == 1) {
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchDown = now;
            }
        } else {
            // secondary pointers must use ACTION_POINTER_* ORed with the pointerIndex
            if (action == MotionEvent.ACTION_UP) {
                action = MotionEvent.ACTION_POINTER_UP |
                        (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (action == MotionEvent.ACTION_DOWN) {
                action = MotionEvent.ACTION_POINTER_DOWN |
                        (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
        }

        MotionEvent event = MotionEvent.obtain(
                lastTouchDown, now, action, pointerCount,
                pointerProperties, pointerCoords, 0, 0, 1.0f, 1.0f,
                0, 0, source, 0);

        if (displayId != 0) {
            try {
                if (setDisplayIdMethod == null) {
                    setDisplayIdMethod = MotionEvent.class.getMethod("setDisplayId", int.class);
                }
                setDisplayIdMethod.invoke(event, displayId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set displayId", e);
            }
        }

        injectEvent(event);
        event.recycle();
    }

    public void injectScroll(float x, float y, float value) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] scrollProps = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerCoords[] scrollCoords = new MotionEvent.PointerCoords[1];

        scrollProps[0] = new MotionEvent.PointerProperties();
        scrollProps[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

        scrollCoords[0] = new MotionEvent.PointerCoords();
        scrollCoords[0].x = x;
        scrollCoords[0].y = y;
        scrollCoords[0].pressure = 1.0f;

        MotionEvent event = MotionEvent.obtain(
                lastTouchDown, now, MotionEvent.ACTION_SCROLL, 1,
                scrollProps, scrollCoords, 0, 0, 1.0f, 1.0f,
                0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);

        if (displayId != 0) {
            try {
                if (setDisplayIdMethod == null) {
                    setDisplayIdMethod = MotionEvent.class.getMethod("setDisplayId", int.class);
                }
                setDisplayIdMethod.invoke(event, displayId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set displayId", e);
            }
        }

        injectEvent(event);
        event.recycle();
    }

    private void injectEvent(MotionEvent event) {
        try {
            if (injectInputEventMethod != null) {
                injectInputEventMethod.invoke(inputManager, event, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject touch event", e);
        }
    }

    public void releaseAll() {
        pointersState.release();
        pointerCount = 0;
    }
}
