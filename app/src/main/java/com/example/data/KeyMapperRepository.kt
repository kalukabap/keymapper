package com.example.data

import android.content.Context
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class KeyMapperRepository(context: Context) {
    private val dao = KeyMapperDatabase.getDatabase(context).dao()

    // ── PROFILES ──

    val allProfiles: Flow<List<GameProfile>> = dao.getAllProfiles()

    suspend fun getProfilesList(): List<GameProfile> = withContext(Dispatchers.IO) {
        dao.getAllProfilesList()
    }

    suspend fun getProfile(profileId: Int): GameProfile? = withContext(Dispatchers.IO) {
        dao.getProfileById(profileId)
    }

    suspend fun getProfileByPackageName(packageName: String): GameProfile? = withContext(Dispatchers.IO) {
        dao.getProfileByPackageName(packageName)
    }

    suspend fun createProfile(name: String, packageName: String = ""): Long = withContext(Dispatchers.IO) {
        val profile = GameProfile(name = name, packageName = packageName)
        dao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: GameProfile) = withContext(Dispatchers.IO) {
        dao.updateProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProfile(profile: GameProfile) = withContext(Dispatchers.IO) {
        dao.deleteMappingsForProfile(profile.id)
        dao.deleteGroupsForProfile(profile.id)
        dao.deleteProfile(profile)
    }

    // ── BINDING GROUPS ──

    fun getGroupsFlow(profileId: Int): Flow<List<BindingGroup>> {
        return dao.getGroupsForProfile(profileId)
    }

    suspend fun getGroupsList(profileId: Int): List<BindingGroup> = withContext(Dispatchers.IO) {
        dao.getGroupsForProfileList(profileId)
    }

    suspend fun getMovementGroup(profileId: Int): BindingGroup? = withContext(Dispatchers.IO) {
        dao.getGroupByType(profileId, BindingGroup.GROUP_TYPE_MOVEMENT)
    }

    suspend fun saveGroup(group: BindingGroup): Long = withContext(Dispatchers.IO) {
        dao.insertGroup(group)
    }

    suspend fun deleteGroup(group: BindingGroup) = withContext(Dispatchers.IO) {
        dao.deleteGroup(group)
    }

    // ── KEY MAPPINGS ──

    fun getMappingsFlow(profileId: Int): Flow<List<KeyMapping>> {
        return dao.getMappingsForProfileFlow(profileId)
    }

    suspend fun getMappingsList(profileId: Int): List<KeyMapping> = withContext(Dispatchers.IO) {
        dao.getMappingsForProfileList(profileId)
    }

    suspend fun getMappingsForKey(profileId: Int, keyCode: Int): List<KeyMapping> = withContext(Dispatchers.IO) {
        dao.getMappingsForKey(profileId, keyCode)
    }

    suspend fun getMappingsForGroup(profileId: Int, groupId: Int): List<KeyMapping> = withContext(Dispatchers.IO) {
        dao.getMappingsForGroup(profileId, groupId)
    }

    suspend fun saveMapping(mapping: KeyMapping) = withContext(Dispatchers.IO) {
        dao.insertMapping(mapping)
    }

    suspend fun deleteMapping(mapping: KeyMapping) = withContext(Dispatchers.IO) {
        dao.deleteMapping(mapping)
    }

    // ── ACTION SEQUENCES ──

    suspend fun getSequenceForMapping(mappingId: Int): List<ActionSequence> = withContext(Dispatchers.IO) {
        dao.getSequenceForMapping(mappingId)
    }

    fun getSequenceFlow(mappingId: Int): Flow<List<ActionSequence>> {
        return dao.getSequenceForMappingFlow(mappingId)
    }

    suspend fun saveSequenceSteps(steps: List<ActionSequence>) = withContext(Dispatchers.IO) {
        if (steps.isNotEmpty()) {
            dao.deleteSequenceForMapping(steps.first().mappingId)
            dao.insertSequenceSteps(steps)
        }
    }

    suspend fun deleteSequenceForMapping(mappingId: Int) = withContext(Dispatchers.IO) {
        dao.deleteSequenceForMapping(mappingId)
    }

    // ── SEED DATA ──

    suspend fun seedSampleMappings(profileId: Int) = withContext(Dispatchers.IO) {
        dao.deleteMappingsForProfile(profileId)
        dao.deleteGroupsForProfile(profileId)

        // Create binding groups
        val movementGroupId = dao.insertGroup(
            BindingGroup(
                profileId = profileId,
                name = "Movement",
                groupType = BindingGroup.GROUP_TYPE_MOVEMENT,
                anchorXPercent = 25f,
                anchorYPercent = 75f,
                radiusPercent = 10f
            )
        ).toInt()

        val aimGroupId = dao.insertGroup(
            BindingGroup(
                profileId = profileId,
                name = "Camera / Aim",
                groupType = BindingGroup.GROUP_TYPE_AIM,
                anchorXPercent = 70f,
                anchorYPercent = 35f,
                radiusPercent = 15f
            )
        ).toInt()

        val fireGroupId = dao.insertGroup(
            BindingGroup(
                profileId = profileId,
                name = "Actions",
                groupType = BindingGroup.GROUP_TYPE_FIRE,
                anchorXPercent = 80f,
                anchorYPercent = 75f,
                radiusPercent = 10f
            )
        ).toInt()

        // Movement keys — grouped together
        val mappings = listOf(
            KeyMapping(
                profileId = profileId, keyName = "W", keyCode = KeyEvent.KEYCODE_W,
                xPercent = 25f, yPercent = 70f, mappingType = KeyMapping.TYPE_DPAD,
                holdMode = KeyMapping.HOLD_MODE_HOLD, groupId = movementGroupId
            ),
            KeyMapping(
                profileId = profileId, keyName = "A", keyCode = KeyEvent.KEYCODE_A,
                xPercent = 20f, yPercent = 75f, mappingType = KeyMapping.TYPE_DPAD,
                holdMode = KeyMapping.HOLD_MODE_HOLD, groupId = movementGroupId
            ),
            KeyMapping(
                profileId = profileId, keyName = "S", keyCode = KeyEvent.KEYCODE_S,
                xPercent = 25f, yPercent = 80f, mappingType = KeyMapping.TYPE_DPAD,
                holdMode = KeyMapping.HOLD_MODE_HOLD, groupId = movementGroupId
            ),
            KeyMapping(
                profileId = profileId, keyName = "D", keyCode = KeyEvent.KEYCODE_D,
                xPercent = 30f, yPercent = 75f, mappingType = KeyMapping.TYPE_DPAD,
                holdMode = KeyMapping.HOLD_MODE_HOLD, groupId = movementGroupId
            ),
            // Action keys
            KeyMapping(
                profileId = profileId, keyName = "Space", keyCode = KeyEvent.KEYCODE_SPACE,
                xPercent = 85f, yPercent = 85f, mappingType = KeyMapping.TYPE_TAP,
                holdMode = KeyMapping.HOLD_MODE_TAP, groupId = fireGroupId
            ),
            KeyMapping(
                profileId = profileId, keyName = "R (Reload)", keyCode = KeyEvent.KEYCODE_R,
                xPercent = 80f, yPercent = 65f, mappingType = KeyMapping.TYPE_TAP,
                holdMode = KeyMapping.HOLD_MODE_TAP, groupId = fireGroupId
            ),
            KeyMapping(
                profileId = profileId, keyName = "Left Click", keyCode = KeyEvent.KEYCODE_F,
                xPercent = 75f, yPercent = 78f, mappingType = KeyMapping.TYPE_TAP,
                holdMode = KeyMapping.HOLD_MODE_HOLD, groupId = fireGroupId
            ),
            // Mouse look — aim group
            KeyMapping(
                profileId = profileId, keyName = "Mouse Look", keyCode = 0,
                xPercent = 70f, yPercent = 35f, mappingType = KeyMapping.TYPE_MOUSE_LOOK,
                sensitivity = 1.2f, holdMode = KeyMapping.HOLD_MODE_HOLD,
                groupId = aimGroupId, deadZone = 0.05f, smoothing = 0.3f
            )
        )

        for (m in mappings) {
            dao.insertMapping(m)
        }
    }
}
