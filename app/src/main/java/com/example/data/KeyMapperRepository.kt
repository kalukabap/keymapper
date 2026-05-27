package com.example.data

import android.content.Context
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class KeyMapperRepository(context: Context) {
    private val dao = KeyMapperDatabase.getDatabase(context).dao()

    val allProfiles: Flow<List<GameProfile>> = dao.getAllProfiles()

    suspend fun getProfilesList(): List<GameProfile> = withContext(Dispatchers.IO) {
        dao.getAllProfilesList()
    }

    fun getMappingsFlow(profileId: Int): Flow<List<KeyMapping>> {
        return dao.getMappingsForProfileFlow(profileId)
    }

    suspend fun getMappingsList(profileId: Int): List<KeyMapping> = withContext(Dispatchers.IO) {
        dao.getMappingsForProfileList(profileId)
    }

    suspend fun getProfile(profileId: Int): GameProfile? = withContext(Dispatchers.IO) {
        dao.getProfileById(profileId)
    }

    suspend fun createProfile(name: String, packageName: String = ""): Long = withContext(Dispatchers.IO) {
        val profile = GameProfile(name = name, packageName = packageName)
        dao.insertProfile(profile)
    }

    suspend fun saveMapping(mapping: KeyMapping) = withContext(Dispatchers.IO) {
        dao.insertMapping(mapping)
    }

    suspend fun deleteMapping(mapping: KeyMapping) = withContext(Dispatchers.IO) {
        dao.deleteMapping(mapping)
    }

    suspend fun deleteProfile(profile: GameProfile) = withContext(Dispatchers.IO) {
        dao.deleteMappingsForProfile(profile.id)
        dao.deleteProfile(profile)
    }

    suspend fun seedSampleMappings(profileId: Int) = withContext(Dispatchers.IO) {
        dao.deleteMappingsForProfile(profileId)
        
        // Seed standard WASD + Space mapping
        val mappings = listOf(
            KeyMapping(profileId = profileId, keyName = "W", keyCode = KeyEvent.KEYCODE_W, xPercent = 25f, yPercent = 70f, mappingType = KeyMapping.TYPE_DPAD),
            KeyMapping(profileId = profileId, keyName = "A", keyCode = KeyEvent.KEYCODE_A, xPercent = 20f, yPercent = 75f, mappingType = KeyMapping.TYPE_DPAD),
            KeyMapping(profileId = profileId, keyName = "S", keyCode = KeyEvent.KEYCODE_S, xPercent = 25f, yPercent = 80f, mappingType = KeyMapping.TYPE_DPAD),
            KeyMapping(profileId = profileId, keyName = "D", keyCode = KeyEvent.KEYCODE_D, xPercent = 30f, yPercent = 75f, mappingType = KeyMapping.TYPE_DPAD),
            KeyMapping(profileId = profileId, keyName = "Space", keyCode = KeyEvent.KEYCODE_SPACE, xPercent = 85f, yPercent = 85f, mappingType = KeyMapping.TYPE_TAP),
            KeyMapping(profileId = profileId, keyName = "R (Reload)", keyCode = KeyEvent.KEYCODE_R, xPercent = 80f, yPercent = 65f, mappingType = KeyMapping.TYPE_TAP),
            KeyMapping(profileId = profileId, keyName = "Left Click", keyCode = KeyEvent.KEYCODE_F, xPercent = 75f, yPercent = 78f, mappingType = KeyMapping.TYPE_TAP),
            KeyMapping(profileId = profileId, keyName = "Mouse Look", keyCode = 0, xPercent = 70f, yPercent = 35f, mappingType = KeyMapping.TYPE_MOUSE_LOOK, sensitivity = 1.2f)
        )
        
        for (m in mappings) {
            dao.insertMapping(m)
        }
    }
}
