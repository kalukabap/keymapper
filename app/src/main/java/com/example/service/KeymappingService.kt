package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.KeyMapping
import com.example.data.KeyMapperRepository
import com.example.data.MacroAction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class KeymappingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, AccessibilityTouchService.KeyEventOverrideListener {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: KeyMapperRepository
    private lateinit var windowManager: WindowManager

    // Overlay components
    private var floatTriggerView: View? = null
    private var editorOverlayView: View? = null

    // State
    private var activeProfileId = -1
    private val mappingsList = mutableListOf<KeyMapping>()
    private val keyStateMap = mutableMapOf<Int, Boolean>()

    // Joystick keys state for WASD bundling
    private var isWPressed = false
    private var isAPressed = false
    private var isSPressed = false
    private var isDPressed = false

    private val moshi = Moshi.Builder().build()
    private val macroListAdapter = moshi.adapter<List<MacroAction>>(
        Types.newParameterizedType(List::class.java, MacroAction::class.java)
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeymappingService onCreate")
        
        try {
            savedStateRegistryController.performRestore(null)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to performSavedStateRegistry restore", e)
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        repository = KeyMapperRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        _serviceState.value = true
        activeInstance = this

        // Register keys interceptor
        AccessibilityTouchService.keyEventOverrideListener = this

        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                val fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID, createNotification(), fgsType)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start service in foreground", e)
        }

        // Show floating menu controller
        showFloatingControl()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileId = intent?.getIntExtra(EXTRA_PROFILE_ID, -1) ?: -1
        if (profileId != -1) {
            activeProfileId = profileId
            loadProfileMappings(profileId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadProfileMappings(profileId: Int) {
        serviceScope.launch {
            val list = repository.getMappingsList(profileId)
            synchronized(mappingsList) {
                mappingsList.clear()
                mappingsList.addAll(list)
            }
            Log.d(TAG, "Loaded ${list.size} mappings for profile $profileId")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keymapping Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that the KeyMapper inputs are capturing inputs."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KeyMapper Service Running")
            .setContentText("Mapper handles background hardware configuration.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showFloatingControl() {
        if (floatTriggerView != null) return

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating control: draw overlays permission not granted")
            return
        }

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 200
            }

            val frame = FrameLayout(this)
            frame.setViewTreeLifecycleOwner(this)
            frame.setViewTreeViewModelStoreOwner(this)
            frame.setViewTreeSavedStateRegistryOwner(this)

            val composeView = ComposeView(this).apply {
                setContent {
                    IconButton(
                        onClick = { toggleOverlayEditor() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Edit Keymaps",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            frame.addView(composeView)

            // Make floating trigger draggable
            frame.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            try {
                                windowManager.updateViewLayout(frame, layoutParams)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating floating view layout", e)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val moved = Math.abs(event.rawX - initialTouchX) > 10 || Math.abs(event.rawY - initialTouchY) > 10
                            if (!moved) {
                                toggleOverlayEditor()
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            floatTriggerView = frame
            windowManager.addView(frame, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating control", e)
        }
    }

    private fun toggleOverlayEditor() {
        if (editorOverlayView != null) {
            hideOverlayEditor()
        } else {
            showOverlayEditor()
        }
    }

    private fun showOverlayEditor() {
        if (editorOverlayView != null) return

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay editor: draw overlays permission not granted")
            return
        }

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            val frame = FrameLayout(this)
            frame.setViewTreeLifecycleOwner(this)
            frame.setViewTreeViewModelStoreOwner(this)
            frame.setViewTreeSavedStateRegistryOwner(this)

            val composeView = ComposeView(this).apply {
                setContent {
                    com.example.ui.OverlayEditorView(
                        profileId = activeProfileId,
                        onDismiss = { hideOverlayEditor() },
                        onMappingsUpdated = { loadProfileMappings(activeProfileId) }
                    )
                }
            }
            frame.addView(composeView)

            editorOverlayView = frame
            windowManager.addView(frame, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay editor", e)
        }
    }

    private fun hideOverlayEditor() {
        // Clear any active key binding callback
        keyBindingCallback = null
        editorOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay editor view", e)
            }
            editorOverlayView = null
        }
    }

    override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
        try {
            val keyCode = event.keyCode
            val action = event.action

            // KEY BINDING MODE: forward key to callback instead of mapping
            if (keyBindingCallback != null && action == KeyEvent.ACTION_DOWN) {
                val keyName = try {
                    KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
                } catch (e: Exception) {
                    "KEY_$keyCode"
                }
                Log.d(TAG, "Key binding mode: captured keyCode=$keyCode name=$keyName")
                keyBindingCallback?.invoke(keyCode, keyName)
                return true // consume the event
            }

            // Check if cursor lock gesture was pressed (using Grave accent / Backtick / Ctrl)
            if (keyCode == KeyEvent.KEYCODE_GRAVE || keyCode == KeyEvent.KEYCODE_CTRL_LEFT) {
                return true
            }

            val matchingMappings = synchronized(mappingsList) {
                mappingsList.filter { it.keyCode == keyCode }
            }
            if (matchingMappings.isEmpty()) {
                return false
            }

            val isDown = action == KeyEvent.ACTION_DOWN
            keyStateMap[keyCode] = isDown

            val metrics = resources.displayMetrics
            val screenW = metrics.widthPixels.toFloat()
            val screenH = metrics.heightPixels.toFloat()

            for (mapping in matchingMappings) {
                val px = (mapping.xPercent / 100f) * screenW
                val py = (mapping.yPercent / 100f) * screenH

                when (mapping.mappingType) {
                    KeyMapping.TYPE_TAP -> {
                        if (isDown) {
                            AccessibilityTouchService.performTap(px, py)
                        }
                    }
                    KeyMapping.TYPE_DPAD -> {
                        when (keyCode) {
                            KeyEvent.KEYCODE_W -> isWPressed = isDown
                            KeyEvent.KEYCODE_A -> isAPressed = isDown
                            KeyEvent.KEYCODE_S -> isSPressed = isDown
                            KeyEvent.KEYCODE_D -> isDPressed = isDown
                        }
                        handleDpadBundling(screenW, screenH)
                    }
                    KeyMapping.TYPE_MOUSE_LOOK -> {
                        // Mouse look: drag from center based on key held state
                        // Sensitivity scales the drag distance
                        val centerX = (mapping.xPercent / 100f) * screenW
                        val centerY = (mapping.yPercent / 100f) * screenH
                        val lookDistance = 50f * mapping.sensitivity
                        if (isDown) {
                            // Perform a short swipe from center to simulate look drag
                            AccessibilityTouchService.performSwipe(
                                centerX, centerY,
                                centerX + lookDistance, centerY,
                                50
                            )
                        }
                    }
                    KeyMapping.TYPE_MACRO -> {
                        if (isDown) {
                            executeMacro(mapping.macroActionsJson, screenW, screenH)
                        }
                    }
                }
            }

            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onInterceptKeyEvent callback", e)
            return false
        }
    }

    private fun handleDpadBundling(screenW: Float, screenH: Float) {
        val dpadMapping = mappingsList.firstOrNull { it.mappingType == KeyMapping.TYPE_DPAD } ?: return
        val centerX = (dpadMapping.xPercent / 100f) * screenW
        val centerY = (dpadMapping.yPercent / 100f) * screenH

        val distance = 80f
        var dx = 0f
        var dy = 0f

        if (isWPressed) dy -= 1f
        if (isSPressed) dy += 1f
        if (isAPressed) dx -= 1f
        if (isDPressed) dx += 1f

        if (dx == 0f && dy == 0f) {
            return
        }

        val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val targetX = centerX + (dx / length) * distance
        val targetY = centerY + (dy / length) * distance

        AccessibilityTouchService.performSwipe(centerX, centerY, targetX, targetY, 40)
    }

    private fun executeMacro(macroJson: String, screenW: Float, screenH: Float) {
        serviceScope.launch {
            try {
                val actions = withContext(Dispatchers.Default) {
                    macroListAdapter.fromJson(macroJson)
                } ?: emptyList()

                for (action in actions) {
                    when (action.actionType) {
                        MacroAction.ACTION_TAP -> {
                            val px = (action.xPercent / 100f) * screenW
                            val py = (action.yPercent / 100f) * screenH
                            AccessibilityTouchService.performTap(px, py)
                        }
                        MacroAction.ACTION_DELAY -> {
                            delay(action.delayMs)
                        }
                        MacroAction.ACTION_SWIPE -> {
                            val px1 = (action.xPercent / 100f) * screenW
                            val py1 = (action.yPercent / 100f) * screenH
                            val px2 = ((action.xPercent + action.dxPercent) / 100f) * screenW
                            val py2 = ((action.yPercent + action.dyPercent) / 100f) * screenH
                            AccessibilityTouchService.performSwipe(px1, py1, px2, py2, 300)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run macro", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KeymappingService onDestroy")
        _serviceState.value = false
        activeInstance = null
        AccessibilityTouchService.keyEventOverrideListener = null
        serviceScope.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        try {
            store.clear()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing store on destroy", e)
        }

        floatTriggerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove float trigger view on destroy", e)
            }
            floatTriggerView = null
        }
        editorOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove editor overlay view on destroy", e)
            }
            editorOverlayView = null
        }
    }

    companion object {
        const val TAG = "KeyMapperService"
        const val CHANNEL_ID = "KeyMapperChannel"
        const val NOTIFICATION_ID = 2026
        const val EXTRA_PROFILE_ID = "EXTRA_PROFILE_ID"

        @Volatile
        private var activeInstance: KeymappingService? = null

        private val _serviceState = MutableStateFlow(false)
        val serviceState = _serviceState.asStateFlow()

        val isServiceRunning: Boolean
            get() = activeInstance != null

        // Key binding mode - when set, intercepted keys are forwarded here instead of mapped
        @Volatile
        var keyBindingCallback: ((Int, String) -> Unit)? = null

        fun getActiveInstance(): KeymappingService? = activeInstance
    }
}
