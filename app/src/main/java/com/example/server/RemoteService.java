package com.example.server;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.example.IRemoteService;
import com.example.IRemoteServiceCallback;
import com.example.OnKeyEventListener;
import com.example.keymap.KeymapData;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote service running in a privileged process (app_process via Shizuku or root).
 * Handles input injection, mouse event processing, and key event dispatch.
 * Communicates with the app process via AIDL.
 */
public class RemoteService extends IRemoteService.Stub {

    public static final String TAG = "ApexMapper-Server";
    private final Context context;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Input input;
    private InputService inputService;
    private IRemoteServiceCallback callback;
    private OnKeyEventListener keyEventListener;
    private KeymapData currentKeymap;
    private boolean active = false;
    private int screenWidth, screenHeight;

    // Pointer IDs — reserved ranges like XtMapper
    public static final int POINTER_ID_MOUSE = 36;
    public static final int POINTER_ID_AIM = 37;
    public static final int POINTER_ID_RIGHTCLICK = 38;

    public RemoteService(Context context) {
        this.context = context;
        Log.i(TAG, "RemoteService created");
    }

    @Override
    public void startServer(KeymapData keymap, IRemoteServiceCallback cb,
                            int width, int height) throws RemoteException {
        Log.i(TAG, "startServer: " + width + "x" + height);
        this.callback = cb;
        this.currentKeymap = keymap;
        this.screenWidth = width;
        this.screenHeight = height;

        // Scale keymap to current screen size
        keymap.scale(width, height);

        // Create input injector
        input = new Input(0); // displayId 0 = default display

        // Create input service (mouse + key handler)
        inputService = new InputService(input, keymap, callback, width, height);

        active = true;
        if (callback != null) callback.onServiceConnected();

        // Start native mouse reader if available
        startMouseReader();
    }

    @Override
    public void stopServer() throws RemoteException {
        Log.i(TAG, "stopServer");
        active = false;
        if (inputService != null) inputService.stop();
        if (input != null) input.releaseAll();
        if (callback != null) callback.onServiceDisconnected();
    }

    @Override
    public void reloadKeymap(KeymapData keymap) throws RemoteException {
        Log.i(TAG, "reloadKeymap");
        this.currentKeymap = keymap;
        if (inputService != null) {
            inputService.reload(keymap, screenWidth, screenHeight);
        }
    }

    @Override
    public void registerOnKeyEventListener(OnKeyEventListener l) throws RemoteException {
        this.keyEventListener = l;
    }

    @Override
    public void unregisterOnKeyEventListener(OnKeyEventListener l) throws RemoteException {
        this.keyEventListener = null;
    }

    @Override
    public boolean isActive() throws RemoteException {
        return active;
    }

    @Override
    public void pauseMouse() throws RemoteException {
        if (inputService != null) inputService.setPaused(true);
    }

    @Override
    public void resumeMouse() throws RemoteException {
        if (inputService != null) inputService.setPaused(false);
    }

    @Override
    public void destroy() throws RemoteException {
        stopServer();
        Log.i(TAG, "destroy");
        System.exit(0);
    }

    private void startMouseReader() {
        try {
            System.loadLibrary("mouse_read");
            nativeStartMouseReader();
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native mouse reader not available: " + e.getMessage());
        }
    }

    // Called from native code (JNI)
    @SuppressWarnings("unused")
    private void onNewMouseRelEvent(int relX, int relY, int buttons, int wheel) {
        if (!active || inputService == null) return;
        mHandler.post(() -> inputService.onMouseEvent(relX, relY, buttons, wheel));
    }

    // Called from native code (JNI)
    @SuppressWarnings("unused")
    private void onKeyEvent(int keyCode, boolean pressed) {
        if (!active || inputService == null) return;
        mHandler.post(() -> inputService.onKeyEvent(keyCode, pressed));
        if (keyEventListener != null) {
            try {
                keyEventListener.onKeyEvent(String.valueOf(keyCode), pressed);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to notify key listener", e);
            }
        }
    }

    private native void nativeStartMouseReader();
}
