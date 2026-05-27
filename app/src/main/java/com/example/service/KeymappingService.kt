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
import com.example.MainActivity
import com.example.data.KeyMapping
import com.example.data.KeyMapperRepository
import com.example.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeymappingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, AccessibilityTouchService.KeyEventOverrideListener {

    // ── LIFECYCLE BOILERPLATE ──

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // ── CORE COMPONENTS ──

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: KeyMapperRepository
    private lateinit var windowManager: WindowManager

    /** THE ENGINE — the brain of the mapper */
    private lateinit var engine: RuntimeEngine

    /** Gesture injector — bridges engine actions to accessibility service */
    private lateinit var gestureInjector: GestureInjector

    // ── OVERLAY ──

    private var floatTriggerView: View? = null
    private var editorOverlayView: View? = null

    // ── STATE ──

    private var activeProfileId = -1

    // ═══════════════════════════════════════════
    //  SERVICE LIFECYCLE
    // ═══════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeymappingService onCreate")

        try {
            savedStateRegistryController.performRestore(null)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to restore state", e)
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        repository = KeyMapperRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Initialize the engine and gesture injector
        engine = RuntimeEngine(this)
        gestureInjector = GestureInjector(resources, engine)
        engine.actionExecutor = gestureInjector

        _serviceState.value = true
        activeInstance = this

        // Register as key event interceptor
        AccessibilityTouchService.keyEventOverrideListener = this

        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID, createNotification(), fgsType)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start foreground", e)
        }

        showFloatingControl()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileId = intent?.getIntExtra(EXTRA_PROFILE_ID, -1) ?: -1
        if (profileId != -1) {
            activeProfileId = profileId
            loadProfileIntoEngine(profileId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════════
    //  PROFILE LOADING — atomic snapshot into engine
    // ═══════════════════════════════════════════

    private fun loadProfileIntoEngine(profileId: Int) {
        serviceScope.launch {
            try {
                val profile = repository.getProfile(profileId) ?: return@launch
                val mappings = repository.getMappingsList(profileId)
                val groups = repository.getGroupsList(profileId)

                // Load action sequences for all mappings
                val sequences = mutableMapOf<Int, List<com.example.data.ActionSequence>>()
                for (mapping in mappings) {
                    if (mapping.mappingType == KeyMapping.TYPE_MACRO) {
                        sequences[mapping.id] = repository.getSequenceForMapping(mapping.id)
                    }
                }

                // Atomic load into engine
                engine.loadProfile(profile, mappings, groups, sequences)
                Log.d(TAG, "Profile loaded into engine: ${profile.name} (${mappings.size} mappings)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile into engine", e)
            }
        }
    }

    // ═══════════════════════════════════════════
    //  KEY EVENT INTERCEPTION — the hot path
    // ═══════════════════════════════════════════

    /**
     * Called by AccessibilityTouchService when a hardware key is pressed.
     * This is the HOT PATH — must be fast.
     */
    override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
        try {
            // KEY BINDING MODE: forward key to editor callback instead of engine
            if (keyBindingCallback != null && event.action == KeyEvent.ACTION_DOWN) {
                val keyName = InputNormalizer.keyName(event.keyCode)
                Log.d(TAG, "Key binding mode: captured keyCode=${event.keyCode} name=$keyName")
                keyBindingCallback?.invoke(event.keyCode, keyName)
                return true
            }

            // Swallow cursor lock key (grave/ctrl) — reserved for aim toggle
            if (event.keyCode == KeyEvent.KEYCODE_GRAVE || event.keyCode == KeyEvent.KEYCODE_CTRL_LEFT) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    engine.toggleAimMode()
                }
                return true
            }

            // Normalize the raw event
            val normalized = InputNormalizer.normalize(event) ?: return false

            // Feed to engine — it decides if the event is consumed
            return engine.processKey(normalized)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onInterceptKeyEvent", e)
            return false
        }
    }

    /**
     * Called when a mouse motion event is received.
     * NOTE: AccessibilityService does NOT receive MotionEvents directly.
      * Mouse buttons come through onKeyEvent as key events.
      * Mouse movement capture requires a transparent overlay (TODO).
      * For now, this is used by the overlay-based mouse capture when in aim mode.
      */
     fun onMotionEvent(event: android.view.MotionEvent) {
         try {
             if (!InputNormalizer.isMouseEvent(event)) return

            val dx = event.getAxisValue(android.view.MotionEvent.AXIS_RELATIVE_X)
            val dy = event.getAxisValue(android.view.MotionEvent.AXIS_RELATIVE_Y)

            if (dx != 0f || dy != 0f) {
                engine.processMouseMove(dx, dy)
            }

            // Mouse buttons
            val buttonState = event.buttonState
            val changedButtons = buttonState xor lastButtonState
            if (changedButtons != 0) {
                for (button in listOf(
                    android.view.MotionEvent.BUTTON_PRIMARY,
                    android.view.MotionEvent.BUTTON_SECONDARY,
                    android.view.MotionEvent.BUTTON_TERTIARY,
                    android.view.MotionEvent.BUTTON_BACK,
                    android.view.MotionEvent.BUTTON_FORWARD
                )) {
                    val mask = buttonMask(button)
                    if (changedButtons and mask != 0) {
                        val isDown = buttonState and mask != 0
                        engine.processMouseButton(button, isDown)
                    }
                }
                lastButtonState = buttonState
            }

            // Scroll
            val scrollY = event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)
            if (scrollY != 0f) {
                engine.processScroll(0f, scrollY)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onMotionEvent", e)
        }
    }

    private var lastButtonState = 0

    private fun buttonMask(button: Int): Int {
        return when (button) {
            android.view.MotionEvent.BUTTON_PRIMARY -> android.view.MotionEvent.BUTTON_PRIMARY
            android.view.MotionEvent.BUTTON_SECONDARY -> android.view.MotionEvent.BUTTON_SECONDARY
            android.view.MotionEvent.BUTTON_TERTIARY -> android.view.MotionEvent.BUTTON_TERTIARY
            android.view.MotionEvent.BUTTON_BACK -> 8
            android.view.MotionEvent.BUTTON_FORWARD -> 16
            else -> 0
        }
    }

    // ═══════════════════════════════════════════
    //  OVERLAY MANAGEMENT
    // ═══════════════════════════════════════════

    private fun toggleOverlayEditor() {
        if (editorOverlayView != null) {
            hideOverlayEditor()
        } else {
            showOverlayEditor()
        }
    }

    private fun showFloatingControl() {
        if (floatTriggerView != null) return

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission")
            return
        }

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
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

            // Draggable
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
                                Log.e(TAG, "Error updating layout", e)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val moved = Math.abs(event.rawX - initialTouchX) > 10 || Math.abs(event.rawY - initialTouchY) > 10
                            if (!moved) toggleOverlayEditor()
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

    private fun showOverlayEditor() {
        if (editorOverlayView != null) return

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission")
            return
        }

        try {
            // Lock input while editor is open
            engine.lockInput()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
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
                        onMappingsUpdated = { loadProfileIntoEngine(activeProfileId) }
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
        keyBindingCallback = null
        editorOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove editor", e)
            }
            editorOverlayView = null
        }
        // Unlock input when editor closes
        engine.unlockInput()
    }

    // ═══════════════════════════════════════════
    //  NOTIFICATION
    // ═══════════════════════════════════════════

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

    // ═══════════════════════════════════════════
    //  DESTROY
    // ═══════════════════════════════════════════

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KeymappingService onDestroy")

        _serviceState.value = false
        activeInstance = null
        AccessibilityTouchService.keyEventOverrideListener = null

        engine.unload()
        gestureInjector.destroy()
        serviceScope.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        try {
            store.clear()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing store", e)
        }

        floatTriggerView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.e(TAG, "Error removing float", e) }
            floatTriggerView = null
        }
        editorOverlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.e(TAG, "Error removing editor", e) }
            editorOverlayView = null
        }
    }

    // ═══════════════════════════════════════════
    //  COMPANION
    // ═══════════════════════════════════════════

    companion object {
        const val TAG = "KeyMapperService"
        const val CHANNEL_ID = "KeyMapperChannel"
        const val NOTIFICATION_ID = 2026
        const val EXTRA_PROFILE_ID = "EXTRA_PROFILE_ID"

        @Volatile
        private var activeInstance: KeymappingService? = null

        private val _serviceState = kotlinx.coroutines.flow.MutableStateFlow(false)
        val serviceState = _serviceState.asStateFlow()

        val isServiceRunning: Boolean
            get() = activeInstance != null

        @Volatile
        var keyBindingCallback: ((Int, String) -> Unit)? = null

        fun getActiveInstance(): KeymappingService? = activeInstance
    }
}
