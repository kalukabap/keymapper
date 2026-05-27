package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.DashboardScreen
import com.example.ui.DashboardViewModel
import com.example.ui.theme.MyApplicationTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val viewModel: DashboardViewModel by viewModels()
    private val SHIZUKU_REQUEST_CODE = 1001

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            val isGranted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d("MainActivity", "Shizuku permission dynamic result: $isGranted")
            viewModel.updateShizukuStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Shizuku connection when launched
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                checkAndRequestShizukuPermission()
            } else {
                Log.w("MainActivity", "Shizuku service is not running on launch")
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to setup Shizuku connection", e)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            // no-op
        }
    }

    private fun checkAndRequestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Log.d("MainActivity", "Shizuku perm not granted, requesting...")
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                } else {
                    Log.d("MainActivity", "Shizuku perm already granted")
                }
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to check/request Shizuku permission", e)
        }
    }

    // Intercept physical keypresses inside MainActivity window so they instantly update visual logs!
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        try {
            event?.let {
                val keyString = try {
                    KeyEvent.keyCodeToString(keyCode)
                } catch (e: Exception) {
                    null
                } ?: "KEYCODE_UNKNOWN"
                val cleanKey = keyString.replace("KEYCODE_", "")
                LogHelper.lastLoggedKey = "Interposed Key ID: $keyCode ($cleanKey)"
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error in onKeyDown intercept", e)
        }
        return super.onKeyDown(keyCode, event)
    }
}

object LogHelper {
    var lastLoggedKey: String = "No physical inputs detected yet"
}
