package com.example.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
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
 * Uses Shizuku to launch app_process, then connects via ServiceManager.
 */
public class RemoteServiceHelper {

    private static final String TAG = "ApexMapper-Helper";
    private static IRemoteService service = null;

    public interface ConnectCallback {
        void onConnected(IRemoteService service);
        void onError(String message);
    }

    /**
     * Connect to the remote server.
     * First tries existing server via ServiceManager.
     * If not found, launches via Shizuku.
     */
    public static void connect(Context context, ConnectCallback callback) {
        // Try existing service first
        IBinder binder = ServiceManager.getService("apexmapper");
        if (binder != null && binder.pingBinder()) {
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
            File script = generateLaunchScript(context);
            String[] cmd = {"sh", script.getAbsolutePath()};

            // Use ShizukuRemoteProcess to launch the server
            // Shizuku provides privileged process execution
            Process process = new ShizukuRemoteProcess(cmd);
            
            // Wait for server to register
            waitForServer(callback, 10);

        } catch (Exception e) {
            Log.e(TAG, "Failed to launch via Shizuku", e);
            callback.onError("Shizuku launch failed: " + e.getMessage());
        }
    }

    /**
     * Wait for the server to register with ServiceManager.
     */
    private static void waitForServer(ConnectCallback callback, int maxRetries) {
        new Thread(() -> {
            for (int i = 0; i < maxRetries; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }

                IBinder binder = ServiceManager.getService("apexmapper");
                if (binder != null && binder.pingBinder()) {
                    service = IRemoteService.Stub.asInterface(binder);
                    Log.i(TAG, "Server connected after " + (i + 1) + " retries");
                    callback.onConnected(service);
                    return;
                }
            }
            callback.onError("Server failed to start after " + maxRetries + "s");
        }).start();
    }

    private static File generateLaunchScript(Context context) throws IOException {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Package not found", e);
        }

        String className = RemoteServiceShell.class.getName();
        File script = new File(context.getExternalFilesDir(null), "apexmapper_server.sh");

        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh\n");
        sb.append("pkill -f ").append(className).append(" 2>/dev/null\n");
        sb.append("exec /system/bin/app_process");
        sb.append(" -Djava.library.path=\"").append(ai.nativeLibraryDir).append("\"");
        sb.append(" -Djava.class.path=\"").append(ai.publicSourceDir).append("\"");
        sb.append(" / ").append(className);
        sb.append(" \"$@\"\n");

        try (FileWriter fw = new FileWriter(script)) {
            fw.write(sb.toString());
        }
        script.setExecutable(true);
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

    /**
     * Wrapper for Shizuku remote process execution.
     * Uses reflection since Shizuku.newProcess may have access restrictions.
     */
    private static class ShizukuRemoteProcess extends Process {
        private final Process delegate;

        ShizukuRemoteProcess(String[] cmd) throws Exception {
            // Try Shizuku's newProcess via reflection
            try {
                java.lang.reflect.Method method = Shizuku.class.getDeclaredMethod(
                        "newProcess", String[].class, String[].class, String.class);
                method.setAccessible(true);
                delegate = (Process) method.invoke(null, (Object) cmd, null, null);
            } catch (Exception e) {
                // Fallback: use regular Runtime.exec (won't have elevated privileges)
                Log.w(TAG, "Shizuku.newProcess failed, falling back to Runtime.exec");
                delegate = Runtime.getRuntime().exec(cmd);
            }
        }

        @Override public OutputStream getOutputStream() { return delegate.getOutputStream(); }
        @Override public InputStream getInputStream() { return delegate.getInputStream(); }
        @Override public InputStream getErrorStream() { return delegate.getErrorStream(); }
        @Override public int waitFor() throws InterruptedException { return delegate.waitFor(); }
        @Override public int exitValue() { return delegate.exitValue(); }
        @Override public void destroy() { delegate.destroy(); }
    }
}
