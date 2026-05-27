package com.example.server;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Entry point for the server process launched via app_process.
 * Shizuku or root runs: app_process / com.example.server.RemoteServiceShell
 * 
 * This runs in a SEPARATE process with elevated privileges.
 * It creates a RemoteService and registers it with ServiceManager.
 */
public class RemoteServiceShell {

    public static void main(String[] args) {
        try {
            System.out.println("ApexMapper server starting...");
            Looper.prepareMainLooper();

            Context context = getContext();
            RemoteService service = new RemoteService(context);

            // Register as a system service so the app can find us
            ServiceManager.addService("apexmapper", service);
            Log.i(RemoteService.TAG, "Service registered with ServiceManager");

            // Grant overlay permission
            new ProcessBuilder("pm", "grant",
                    context.getPackageName(),
                    "android.permission.SYSTEM_ALERT_WINDOW")
                    .inheritIO().start();

            System.out.println("ApexMapper server ready!");
            Looper.loop();
        } catch (Exception e) {
            Log.e(RemoteService.TAG, "Server failed to start", e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static Context getContext() {
        Context systemContext = getSystemContext();
        Context context = null;
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        try {
            // Get the app's package context so we can load its resources
            context = systemContext.createPackageContext(
                    "com.aistudio.keymapper.vqkym", flags);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(RemoteService.TAG, "Package not found", e);
        }
        return unwrap(context);
    }

    private static Context unwrap(Context context) {
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context;
    }

    @SuppressLint("PrivateApi")
    private static Context getSystemContext() {
        try {
            Class<?> atClazz = Class.forName("android.app.ActivityThread");
            Method systemMain = atClazz.getMethod("systemMain");
            Object activityThread = systemMain.invoke(null);
            Method getSystemContext = atClazz.getMethod("getSystemContext");
            return (Context) getSystemContext.invoke(activityThread);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get system context", e);
        }
    }
}
