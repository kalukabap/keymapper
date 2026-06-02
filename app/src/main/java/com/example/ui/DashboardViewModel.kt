package com.example.ui

import android.app.Application
import android.content.Intent
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IRemoteService
import com.example.data.GameProfile
import com.example.data.KeyMapperRepository
import com.example.data.KeyMapping
import com.example.data.SandProfileTemplate
import com.example.data.SandProfileTemplates
import com.example.server.KeymapConverter
import com.example.server.RemoteServiceHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KeyMapperRepository(application)

    // Flow states
    val allProfiles: StateFlow<List<GameProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileTemplates: List<SandProfileTemplate> = SandProfileTemplates.all

    private val _selectedProfileId = MutableStateFlow(-1)
    val selectedProfileId = _selectedProfileId.asStateFlow()

    // Service States
    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected = _isAccessibilityConnected.asStateFlow()

    private val _isOverlayGranted = MutableStateFlow(false)
    val isOverlayGranted = _isOverlayGranted.asStateFlow()

    private val _isShizukuRunning = MutableStateFlow(false)
    val isShizukuRunning = _isShizukuRunning.asStateFlow()

    private val _isShizukuGranted = MutableStateFlow(false)
    val isShizukuGranted = _isShizukuGranted.asStateFlow()

    // Server state
    private val _isServerConnected = MutableStateFlow(false)
    val isServerConnected = _isServerConnected.asStateFlow()

    // Alias for UI compatibility
    val isServiceActive: StateFlow<Boolean> = _isServerConnected.asStateFlow()

    private val _serverStatus = MutableStateFlow("Disconnected")
    val serverStatus = _serverStatus.asStateFlow()

    private var remoteService: IRemoteService? = null

    init {
        checkPermissions()
        seedDefaultProfileIfEmpty()
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        _isAccessibilityConnected.value = com.example.service.AccessibilityTouchService.isServiceConnected
        _isOverlayGranted.value = android.provider.Settings.canDrawOverlays(context)

        try {
            val running = rikka.shizuku.Shizuku.pingBinder()
            _isShizukuRunning.value = running
            _isShizukuGranted.value = running &&
                (rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED)
        } catch (e: Throwable) {
            _isShizukuRunning.value = false
            _isShizukuGranted.value = false
        }

        // Check if server is already connected
        _isServerConnected.value = RemoteServiceHelper.isConnected()
    }

    fun updateShizukuStatus() {
        checkPermissions()
    }

    fun requestShizukuPermission() {
        try {
            if (rikka.shizuku.Shizuku.pingBinder()) {
                rikka.shizuku.Shizuku.requestPermission(1001)
            }
        } catch (e: Throwable) { }
    }

    // ─── SERVER CONTROL ──────────────────────────────────

    /**
     * Start the server and send the active profile's keymap.
     */
    fun startServer() {
        val context = getApplication<Application>()
        _serverStatus.value = "Connecting..."

        RemoteServiceHelper.connect(context, object : RemoteServiceHelper.ConnectCallback {
            override fun onConnected(service: IRemoteService) {
                remoteService = service
                _isServerConnected.value = true
                _serverStatus.value = "Connected"
                Log.d(TAG, "Server connected, sending keymap...")
                sendKeymapToServer()
            }

            override fun onError(message: String) {
                _isServerConnected.value = false
                _serverStatus.value = "Error: $message"
                Log.e(TAG, "Server connection failed: $message")
            }
        })
    }

    /**
     * Stop the server.
     */
    fun stopServer() {
        try {
            remoteService?.stopServer()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        remoteService = null
        _isServerConnected.value = false
        _serverStatus.value = "Stopped"
    }

    /**
     * Send the current profile's keymap to the running server.
     */
    fun sendKeymapToServer() {
        val service = remoteService ?: return
        val profileId = _selectedProfileId.value
        if (profileId < 0) return

        viewModelScope.launch {
            try {
                val profile = repository.getProfile(profileId) ?: return@launch
                val mappings = repository.getMappingsList(profileId)

                // Get screen dimensions
                val wm = getApplication<Application>()
                    .getSystemService(WindowManager::class.java)
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)

                val keymapData = KeymapConverter.convert(
                    profile, mappings, dm.widthPixels, dm.heightPixels
                )

                service.reloadKeymap(keymapData)
                _serverStatus.value = "Active — ${mappings.size} mappings"
                Log.d(TAG, "Keymap sent: ${mappings.size} mappings")
            } catch (e: Exception) {
                _serverStatus.value = "Error sending keymap: ${e.message}"
                Log.e(TAG, "Failed to send keymap", e)
            }
        }
    }

    // ─── PROFILE MANAGEMENT ──────────────────────────────

    private fun seedDefaultProfileIfEmpty() {
        viewModelScope.launch {
            val list = repository.getProfilesList()
            if (list.isEmpty()) {
                var firstProfileId = -1
                SandProfileTemplates.all.take(2).forEach { template ->
                    val newId = repository.createProfile(template.title, template.packageHint).toInt()
                    template.mappings.forEach { spec ->
                        repository.saveMapping(spec.toKeyMapping(newId))
                    }
                    if (firstProfileId == -1) {
                        firstProfileId = newId
                    }
                }
                _selectedProfileId.value = firstProfileId
            } else {
                if (_selectedProfileId.value == -1 && list.isNotEmpty()) {
                    _selectedProfileId.value = list.first().id
                }
            }
        }
    }

    fun selectProfile(id: Int) {
        _selectedProfileId.value = id
        // If server is running, reload keymap with new profile
        if (_isServerConnected.value) {
            sendKeymapToServer()
        }
    }

    fun createProfile(name: String, targetPkg: String) {
        viewModelScope.launch {
            val newId = repository.createProfile(name, targetPkg)
            repository.seedSampleMappings(newId.toInt())
            _selectedProfileId.value = newId.toInt()
        }
    }


    fun createProfileFromTemplate(template: SandProfileTemplate) {
        viewModelScope.launch {
            val newId = repository.createProfile(template.title, template.packageHint)
            val profileId = newId.toInt()
            template.mappings.forEach { spec ->
                repository.saveMapping(spec.toKeyMapping(profileId))
            }
            _selectedProfileId.value = profileId
            if (_isServerConnected.value) {
                sendKeymapToServer()
            }
        }
    }

    fun deleteProfile(profile: GameProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _selectedProfileId.value = -1
        }
    }

    /**
     * Toggle server on/off. If starting, also sends the current profile.
     */
    fun toggleServiceActivation() {
        if (_isServerConnected.value) {
            stopServer()
        } else {
            startServer()
        }
    }

    companion object {
        private const val TAG = "DashboardVM"
    }
}
