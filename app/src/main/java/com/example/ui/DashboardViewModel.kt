package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameProfile
import com.example.data.KeyMapperRepository
import com.example.service.AccessibilityTouchService
import com.example.service.KeymappingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KeyMapperRepository(application)

    // Flow states
    val allProfiles: StateFlow<List<GameProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _isServiceActive = KeymappingService.serviceState

    val isServiceActive: StateFlow<Boolean> = _isServiceActive

    init {
        checkPermissions()
        seedDefaultProfileIfEmpty()
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        _isAccessibilityConnected.value = AccessibilityTouchService.isServiceConnected
        _isOverlayGranted.value = android.provider.Settings.canDrawOverlays(context)

        try {
            val running = rikka.shizuku.Shizuku.pingBinder()
            _isShizukuRunning.value = running
            _isShizukuGranted.value = running && (rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED)
        } catch (e: Throwable) {
            _isShizukuRunning.value = false
            _isShizukuGranted.value = false
        }
    }

    fun updateShizukuStatus() {
        checkPermissions()
    }

    fun requestShizukuPermission() {
        try {
            if (rikka.shizuku.Shizuku.pingBinder()) {
                rikka.shizuku.Shizuku.requestPermission(1001)
            }
        } catch (e: Throwable) {
            // no-op
        }
    }

    private fun seedDefaultProfileIfEmpty() {
        viewModelScope.launch {
            val list = repository.getProfilesList()
            if (list.isEmpty()) {
                val p1Id = repository.createProfile("Genshin Impact Mobile", "com.miHoYo.GenshinImpact")
                repository.seedSampleMappings(p1Id.toInt())

                val p2Id = repository.createProfile("PUBG Mobile Emulator Mode", "com.tencent.ig")
                repository.seedSampleMappings(p2Id.toInt())

                _selectedProfileId.value = p1Id.toInt()
            } else {
                if (_selectedProfileId.value == -1 && list.isNotEmpty()) {
                    _selectedProfileId.value = list.first().id
                }
            }
        }
    }

    fun selectProfile(id: Int) {
        _selectedProfileId.value = id
        // Restart or update service with active profile mapping if running
        if (isServiceActive.value) {
            startMappingService(id)
        }
    }

    fun createProfile(name: String, targetPkg: String) {
        viewModelScope.launch {
            val newId = repository.createProfile(name, targetPkg)
            repository.seedSampleMappings(newId.toInt())
            _selectedProfileId.value = newId.toInt()
        }
    }

    fun deleteProfile(profile: GameProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _selectedProfileId.value = -1
        }
    }

    fun toggleServiceActivation() {
        if (isServiceActive.value) {
            stopMappingService()
        } else {
            startMappingService(selectedProfileId.value)
        }
    }

    private fun startMappingService(profileId: Int) {
        val context = getApplication<Application>()
        try {
            val intent = Intent(context, KeymappingService::class.java).apply {
                putExtra(KeymappingService.EXTRA_PROFILE_ID, profileId)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Throwable) {
            android.util.Log.e("DashboardViewModel", "Failed to start mapping service", e)
        }
    }

    private fun stopMappingService() {
        val context = getApplication<Application>()
        try {
            context.stopService(Intent(context, KeymappingService::class.java))
        } catch (e: Throwable) {
            android.util.Log.e("DashboardViewModel", "Failed to stop mapping service", e)
        }
    }
}
