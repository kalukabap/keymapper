package com.example.server;

import android.os.Looper;
import android.util.Log;

/**
 * Entry point for the server process launched via app_process.
 *
 * Launch command:
 *   exec /system/bin/app_process \
 *       -Djava.library.path="<nativeLibDir>" \
 *       -Djava.class.path="<apkPath>" \
 *       / com.example.server.RemoteServiceShell
 */
public class RemoteServiceShell {
    private static final String TAG = "RemoteServiceShell";

    public static void main(String[] args) {
        Log.i(TAG, "=== ApexMapper Server Starting ===");
        Log.i(TAG, "PID: " + android.os.Process.myPid());
        Log.i(TAG, "UID: " + android.os.Process.myUid());

        // Prepare main looper for the server process
        Looper.prepareMainLooper();

        try {
            // Create and start the remote service
            RemoteService service = new RemoteService();
            service.start();

            Log.i(TAG, "Server registered, entering Looper...");
            Looper.loop();
        } catch (Exception e) {
            Log.e(TAG, "Server crashed", e);
            System.exit(1);
        }
    }
}
