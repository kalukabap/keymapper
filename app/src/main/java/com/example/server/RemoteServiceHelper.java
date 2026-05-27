package com.example.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.example.IRemoteService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import rikka.shizuku.Shizuku;

/**
 * Manages connection to the remote server process.
 * Supports Shizuku (app_process) and root (libsu) launch methods.
 */
public class RemoteServiceHelper {

    private static final String TAG = "ApexMapper-Helper";
    private static IRemoteService service = null;
    private static ServiceConnection serviceConnection;

    public interface ConnectCallback {
        void onConnected(IRemoteService service);
        void onError(String message);
    }

    /**
     * Connect to the remote server.
     * First tries to find an already-running server via ServiceManager.
     * If not found, launches one via Shizuku.
     */
    public static void connect(Context context, ConnectCallback callback) {
        // Try existing service first
        IBinder binder = ServiceManager.getService("apexmapper");
        if (binder != null) {
            service = IRemoteService.Stub.asInterface(binder);
            Log.i(TAG, "Connected to existing server");
            callback.onConnected(service);
            return;
        }

        // Launch via Shizuku
        launchViaShizuku(context, callback);
    }

    private static void launchViaShizuku(Context context, ConnectCallback callback) {
        try {
            // Generate the launch script
            File script = generateLaunchScript(context);

            // Execute via Shizuku
            // Shizuku runs: sh /path/to/script.sh
            // This launches app_process which starts RemoteServiceShell
            String cmd = "sh " + script.getAbsolutePath();

            // Use Shizuku's newProcess to run the script
            // This starts the server in a separate process
            Process process = Shizuku.newProcess(
                    new String[]{"sh", script.getAbsolutePath()}, null, null);

            // Wait a moment for the server to start
            new Thread(() -> {
                try {
                    Thread.sleep(2000);

                    // Try to connect now
                    IBinder binder = ServiceManager.getService("apexmapper");
                    if (binder != null) {
                        service = IRemoteService.Stub.asInterface(binder);
                        Log.i(TAG, "Connected to Shizuku-launched server");
                        callback.onConnected(service);
                    } else {
                        // Try a few more times
                        for (int i = 0; i < 5; i++) {
                            Thread.sleep(1000);
                            binder = ServiceManager.getService("apexmapper");
                            if (binder != null) {
                                service = IRemoteService.Stub.asInterface(binder);
                                Log.i(TAG, "Connected after retry " + (i + 1));
                                callback.onConnected(service);
                                return;
                            }
                        }
                        callback.onError("Server failed to register with ServiceManager");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect after launch", e);
                    callback.onError("Connection failed: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to launch via Shizuku", e);
            callback.onError("Shizuku launch failed: " + e.getMessage());
        }
    }

    private static File generateLaunchScript(Context context) throws IOException {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Package not found: " + packageName, e);
        }

        String className = RemoteServiceShell.class.getName();
        File script = new File(context.getExternalFilesDir(null), "apexmapper_server.sh");

        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh\n");
        sb.append("pkill -f ").append(className).append("\n");
        sb.append("exec /system/bin/app_process");
        sb.append(" -Djava.library.path=\"").append(ai.nativeLibraryDir).append("\"");
        sb.append(" -Djava.class.path=\"").append(ai.publicSourceDir).append("\"");
        sb.append(" / ").append(className);
        sb.append(" \"$@\"\n");

        try (FileWriter fw = new FileWriter(script)) {
            fw.write(sb.toString());
        }

        script.setExecutable(true);
        Log.i(TAG, "Launch script: " + script.getAbsolutePath());
        return script;
    }

    public static IRemoteService getService() {
        return service;
    }

    public static void disconnect() {
        if (service != null) {
            try {
                service.destroy();
            } catch (RemoteException e) {
                Log.e(TAG, "Error destroying service", e);
            }
            service = null;
        }
    }

    public static boolean isConnected() {
        if (service == null) return false;
        try {
            return service.isActive();
        } catch (RemoteException e) {
            service = null;
            return false;
        }
    }
}
