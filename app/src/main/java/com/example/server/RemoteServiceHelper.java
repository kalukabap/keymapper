package com.example.server

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.IRemoteService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

/**
 * Manages the connection to the privileged server process.
 * Uses Shizuku to launch app_process, then connects via ServiceManager.
 */
class RemoteServiceHelper {

    interface ConnectCallback {
        fun onConnected(service: IRemoteService)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "RemoteServiceHelper"
        private const val SERVICE_NAME = "apexmapper"

        private var remoteService: IRemoteService? = null
        private var isConnecting = false

        fun isConnected(): Boolean = remoteService != null

        fun getService(): IRemoteService? = remoteService

        /**
         * Connect to the server. Launches via Shizuku if not already running.
         */
        fun connect(context: Context, callback: ConnectCallback) {
            if (remoteService != null) {
                callback.onConnected(remoteService!!)
                return
            }

            if (isConnecting) {
                callback.onError("Already connecting...")
                return
            }

            isConnecting = true

            // Try to connect to existing server first
            try {
                val binder = android.os.ServiceManager.getService(SERVICE_NAME)
                if (binder != null) {
                    val service = IRemoteService.Stub.asInterface(binder)
                    if (service.asBinder().isBinderAlive) {
                        remoteService = service
                        isConnecting = false
                        callback.onConnected(service)
                        return
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "No existing server found, launching new one")
            }

            // Launch via Shizuku
            launchViaShizuku(context, callback)
        }

        fun disconnect() {
            remoteService = null
            isConnecting = false
        }

        private fun launchViaShizuku(context: Context, callback: ConnectCallback) {
            try {
                if (!Shizuku.pingBinder()) {
                    isConnecting = false
                    callback.onError("Shizuku not running")
                    return
                }

                // Generate the shell script
                val appInfo = context.applicationInfo
                val nativeLibPath = appInfo.nativeLibraryDir
                val apkPath = appInfo.sourceDir
                val className = RemoteServiceShell::class.java.name

                val shellScript = """
                    |#!/system/bin/sh
                    |pkill -f "$className" 2>/dev/null
                    |exec /system/bin/app_process \
                    |    -Djava.library.path="$nativeLibPath" \
                    |    -Djava.class.path="$apkPath" \
                    |    / $className "$$@"
                """.trimMargin()

                // Write script to app-private location
                val scriptFile = File(context.cacheDir, "apexmapper_server.sh")
                FileWriter(scriptFile).use { it.write(shellScript) }
                scriptFile.setExecutable(true)

                Log.d(TAG, "Launching server: ${scriptFile.absolutePath}")
                Log.d(TAG, "APK: $apkPath")
                Log.d(TAG, "NativeLib: $nativeLibPath")

                // Launch via Shizuku.newProcess
                val process = Shizuku.newProcess(
                    arrayOf("sh", scriptFile.absolutePath),
                    null,
                    "/"
                )

                // Read stderr in background for logging
                Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(process.errorStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.e(TAG, "Server stderr: $line")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading stderr", e)
                    }
                }.start()

                // Wait a moment for server to start
                Thread {
                    try {
                        Thread.sleep(2000)

                        // Now connect via ServiceManager
                        var attempts = 0
                        while (attempts < 10) {
                            try {
                                val binder = android.os.ServiceManager.getService(SERVICE_NAME)
                                if (binder != null) {
                                    val service = IRemoteService.Stub.asInterface(binder)
                                    if (service.asBinder().isBinderAlive) {
                                        remoteService = service
                                        isConnecting = false
                                        Log.i(TAG, "Server connected after ${attempts + 1} attempts")
                                        callback.onConnected(service)
                                        return@Thread
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Connection attempt ${attempts + 1} failed: ${e.message}")
                            }
                            attempts++
                            Thread.sleep(500)
                        }

                        isConnecting = false
                        callback.onError("Server started but couldn't connect after 10 attempts")
                    } catch (e: Exception) {
                        isConnecting = false
                        callback.onError("Connection failed: ${e.message}")
                    }
                }.start()

            } catch (e: Exception) {
                isConnecting = false
                Log.e(TAG, "Failed to launch via Shizuku", e)
                callback.onError("Shizuku launch failed: ${e.message}")
            }
        }
    }
}
