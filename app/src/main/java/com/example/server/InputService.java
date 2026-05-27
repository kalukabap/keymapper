package com.example.server;

import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.example.IRemoteServiceCallback;
import com.example.keymap.KeymapData;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes mouse and key events, dispatches touch injections.
 * Runs in the server process.
 */
public class InputService {

    private static final String TAG = "ApexMapper-InputService";
    public static final int UP = 0, DOWN = 1, MOVE = 2;

    private final Input input;
    private final IRemoteServiceCallback callback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private KeymapData keymap;
    private int screenWidth, screenHeight;
    private boolean paused = false;

    // Key → touch point mapping
    private final Map<Integer, KeymapData.TouchPoint> keyMap = new HashMap<>();
    // Key → swipe line mapping
    private final Map<Integer, KeymapData.SwipeLine> swipeMap = new HashMap<>();
    // Active pointer IDs per key
    private final Map<Integer, Integer> activePointers = new HashMap<>();
    private int nextPointerId = 0;

    // Mouse aim state
    private boolean mouseAimActive = false;
    private float aimX, aimY;
    private KeymapData.MouseAimConfig aimConfig;
    private final RectF aimArea = new RectF();

    // Pointer ID allocation (like XtMapper)
    private static final int POINTER_ID_BASE = 100; // Avoid conflicts with reserved IDs

    public InputService(Input input, KeymapData keymap,
                        IRemoteServiceCallback callback,
                        int width, int height) {
        this.input = input;
        this.keymap = keymap;
        this.callback = callback;
        this.screenWidth = width;
        this.screenHeight = height;
        buildMappings();
        initMouseAim();
    }

    private void buildMappings() {
        keyMap.clear();
        swipeMap.clear();
        for (KeymapData.TouchPoint tp : keymap.touchPoints) {
            keyMap.put(tp.keyCode, tp);
        }
        for (KeymapData.SwipeLine sl : keymap.swipeLines) {
            swipeMap.put(sl.keyCode, sl);
        }
    }

    private void initMouseAim() {
        this.aimConfig = keymap.mouseAimConfig;
        if (aimConfig != null) {
            aimX = aimConfig.xCenter;
            aimY = aimConfig.yCenter;
            if (aimConfig.areaWidth > 0 && aimConfig.areaHeight > 0) {
                aimArea.left = aimX - aimConfig.areaWidth;
                aimArea.right = aimX + aimConfig.areaWidth;
                aimArea.top = aimY - aimConfig.areaHeight;
                aimArea.bottom = aimY + aimConfig.areaHeight;
            } else {
                aimArea.set(0, 0, screenWidth, screenHeight);
            }
        }
    }

    public void reload(KeymapData newKeymap, int width, int height) {
        this.keymap = newKeymap;
        this.screenWidth = width;
        this.screenHeight = height;
        buildMappings();
        initMouseAim();
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            input.releaseAll();
            activePointers.clear();
        }
    }

    public void stop() {
        paused = true;
        input.releaseAll();
        activePointers.clear();
    }

    // ─── KEY EVENTS ─────────────────────────────────────

    public void onKeyEvent(int keyCode, boolean pressed) {
        if (paused) return;

        // Check mouse aim trigger
        if (aimConfig != null && keyCode == aimConfig.triggerKeyCode) {
            if (aimConfig.toggle) {
                if (pressed) toggleMouseAim();
            } else {
                if (mouseAimActive != pressed) toggleMouseAim();
            }
            return;
        }

        // Handle touch points
        KeymapData.TouchPoint tp = keyMap.get(keyCode);
        if (tp != null) {
            handleTouchPoint(tp, pressed);
            return;
        }

        // Handle swipe lines
        KeymapData.SwipeLine sl = swipeMap.get(keyCode);
        if (sl != null) {
            handleSwipeLine(sl, pressed);
        }
    }

    private void handleTouchPoint(KeymapData.TouchPoint tp, boolean pressed) {
        float x = tp.xPercent;
        float y = tp.yPercent;

        if (pressed) {
            int pid = allocatePointerId(tp.keyCode);
            input.injectTouch(MotionEvent.ACTION_DOWN, pid, 1.0f, x, y);

            if ("tap".equals(tp.mode)) {
                // Auto-release after duration
                final int fPid = pid;
                mHandler.postDelayed(() -> {
                    input.injectTouch(MotionEvent.ACTION_UP, fPid, 0, x, y);
                    releasePointerId(tp.keyCode);
                }, tp.tapDuration > 0 ? tp.tapDuration : 50);
            } else if ("hold".equals(tp.mode)) {
                // Keep down until release
            } else if ("long_press".equals(tp.mode)) {
                // Keep down, user releases manually
            }
        } else {
            Integer pid = activePointers.get(tp.keyCode);
            if (pid != null) {
                input.injectTouch(MotionEvent.ACTION_UP, pid, 0, x, y);
                releasePointerId(tp.keyCode);
            }
        }
    }

    private void handleSwipeLine(KeymapData.SwipeLine sl, boolean pressed) {
        if (pressed) {
            int pid = allocatePointerId(sl.keyCode);
            float startX = sl.startXPercent;
            float startY = sl.startYPercent;
            float endX = sl.endXPercent;
            float endY = sl.endYPercent;
            int steps = Math.max(1, sl.duration / 16); // ~60fps

            input.injectTouch(MotionEvent.ACTION_DOWN, pid, 1.0f, startX, startY);

            // Animate swipe
            final int fPid = pid;
            for (int i = 1; i <= steps; i++) {
                final float t = (float) i / steps;
                mHandler.postDelayed(() -> {
                    float cx = startX + (endX - startX) * t;
                    float cy = startY + (endY - startY) * t;
                    input.injectTouch(MotionEvent.ACTION_MOVE, fPid, 1.0f, cx, cy);
                }, i * 16L);
            }

            // Release at end
            mHandler.postDelayed(() -> {
                input.injectTouch(MotionEvent.ACTION_UP, fPid, 0, endX, endY);
                releasePointerId(sl.keyCode);
            }, sl.duration + 50);
        }
    }

    // ─── MOUSE EVENTS ───────────────────────────────────

    public void onMouseEvent(int relX, int relY, int buttons, int wheel) {
        if (paused) return;

        // Mouse aim mode
        if (mouseAimActive && aimConfig != null) {
            handleMouseAim(relX, relY);
            return;
        }

        // Scroll wheel
        if (wheel != 0) {
            handleScroll(wheel);
        }

        // Button events
        if (buttons != 0) {
            handleMouseButtons(buttons);
        }
    }

    private void handleMouseAim(int relX, int relY) {
        float dx = relX * aimConfig.xSensitivity * keymap.mouseSensitivity;
        float dy = relY * aimConfig.ySensitivity * keymap.mouseSensitivity;

        aimX += dx;
        aimY += dy;

        // Clamp to area
        boolean reset = false;
        if (aimX < aimArea.left) { aimX = aimArea.left; reset = true; }
        if (aimX > aimArea.right) { aimX = aimArea.right; reset = true; }
        if (aimY < aimArea.top) { aimY = aimArea.top; reset = true; }
        if (aimY > aimArea.bottom) { aimY = aimArea.bottom; reset = true; }

        if (reset) {
            // Edge reset: UP → recenter → DOWN
            input.injectTouch(MotionEvent.ACTION_UP, RemoteService.POINTER_ID_AIM, 0, aimX, aimY);
            mHandler.postDelayed(() -> {
                aimX = aimConfig.xCenter;
                aimY = aimConfig.yCenter;
                input.injectTouch(MotionEvent.ACTION_DOWN, RemoteService.POINTER_ID_AIM, 1.0f, aimX, aimY);
            }, 16);
        } else {
            input.injectTouch(MotionEvent.ACTION_MOVE, RemoteService.POINTER_ID_AIM, 1.0f, aimX, aimY);
        }

        // Update cursor position callback
        if (callback != null) {
            try {
                callback.setCursorX((int) aimX);
                callback.setCursorY((int) aimY);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to update cursor", e);
            }
        }
    }

    private void handleScroll(int wheel) {
        // Find a scroll target position (use screen center or last mouse pos)
        float scrollX = screenWidth / 2f;
        float scrollY = screenHeight / 2f;
        input.injectScroll(scrollX, scrollY, wheel * keymap.scrollSpeed);
    }

    private void handleMouseButtons(int buttons) {
        // Bit 0 = left click, bit 1 = right click, bit 2 = middle
        boolean leftClick = (buttons & 1) != 0;
        boolean rightClick = (buttons & 2) != 0;

        if (leftClick) {
            // Inject touch at current aim position or screen center
            float x = mouseAimActive ? aimX : screenWidth / 2f;
            float y = mouseAimActive ? aimY : screenHeight / 2f;
            input.injectTouch(MotionEvent.ACTION_DOWN, RemoteService.POINTER_ID_MOUSE, 1.0f, x, y);
            mHandler.postDelayed(() -> {
                input.injectTouch(MotionEvent.ACTION_UP, RemoteService.POINTER_ID_MOUSE, 0, x, y);
            }, 50);
        }
    }

    private void toggleMouseAim() {
        mouseAimActive = !mouseAimActive;
        if (mouseAimActive) {
            // Start aim mode: touch down at center
            aimX = aimConfig.xCenter;
            aimY = aimConfig.yCenter;
            input.injectTouch(MotionEvent.ACTION_DOWN, RemoteService.POINTER_ID_AIM, 1.0f, aimX, aimY);
            if (callback != null) {
                try { callback.alertMouseAimActivated(); } catch (RemoteException ignored) {}
            }
        } else {
            // Stop aim mode: touch up
            input.injectTouch(MotionEvent.ACTION_UP, RemoteService.POINTER_ID_AIM, 0, aimX, aimY);
        }
    }

    // ─── POINTER ID MANAGEMENT ──────────────────────────

    private int allocatePointerId(int keyCode) {
        int pid = POINTER_ID_BASE + (nextPointerId++ % 10);
        activePointers.put(keyCode, pid);
        return pid;
    }

    private void releasePointerId(int keyCode) {
        activePointers.remove(keyCode);
    }
}
