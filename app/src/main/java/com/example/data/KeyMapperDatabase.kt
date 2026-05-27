package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyMapperDao {

    // ── PROFILES ──

    @Query("SELECT * FROM game_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<GameProfile>>

    @Query("SELECT * FROM game_profiles ORDER BY name ASC")
    suspend fun getAllProfilesList(): List<GameProfile>

    @Query("SELECT * FROM game_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: Int): GameProfile?

    @Query("SELECT * FROM game_profiles WHERE packageName = :packageName LIMIT 1")
    suspend fun getProfileByPackageName(packageName: String): GameProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GameProfile): Long

    @Update
    suspend fun updateProfile(profile: GameProfile)

    @Delete
    suspend fun deleteProfile(profile: GameProfile)

    // ── BINDING GROUPS ──

    @Query("SELECT * FROM binding_groups WHERE profileId = :profileId ORDER BY name ASC")
    fun getGroupsForProfile(profileId: Int): Flow<List<BindingGroup>>

    @Query("SELECT * FROM binding_groups WHERE profileId = :profileId ORDER BY name ASC")
    suspend fun getGroupsForProfileList(profileId: Int): List<BindingGroup>

    @Query("SELECT * FROM binding_groups WHERE profileId = :profileId AND groupType = :type LIMIT 1")
    suspend fun getGroupByType(profileId: Int, type: String): BindingGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: BindingGroup): Long

    @Update
    suspend fun updateGroup(group: BindingGroup)

    @Delete
    suspend fun deleteGroup(group: BindingGroup)

    @Query("DELETE FROM binding_groups WHERE profileId = :profileId")
    suspend fun deleteGroupsForProfile(profileId: Int)

    // ── KEY MAPPINGS ──

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId")
    fun getMappingsForProfileFlow(profileId: Int): Flow<List<KeyMapping>>

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId")
    suspend fun getMappingsForProfileList(profileId: Int): List<KeyMapping>

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId AND keyCode = :keyCode")
    suspend fun getMappingsForKey(profileId: Int, keyCode: Int): List<KeyMapping>

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId AND groupId = :groupId")
    suspend fun getMappingsForGroup(profileId: Int, groupId: Int): List<KeyMapping>

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId AND mappingType = :type")
    suspend fun getMappingsByType(profileId: Int, type: String): List<KeyMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: KeyMapping): Long

    @Update
    suspend fun updateMapping(mapping: KeyMapping)

    @Delete
    suspend fun deleteMapping(mapping: KeyMapping)

    @Query("DELETE FROM key_mappings WHERE profileId = :profileId")
    suspend fun deleteMappingsForProfile(profileId: Int)

    // ── ACTION SEQUENCES ──

    @Query("SELECT * FROM action_sequences WHERE mappingId = :mappingId ORDER BY stepIndex ASC")
    suspend fun getSequenceForMapping(mappingId: Int): List<ActionSequence>

    @Query("SELECT * FROM action_sequences WHERE mappingId = :mappingId ORDER BY stepIndex ASC")
    fun getSequenceForMappingFlow(mappingId: Int): Flow<List<ActionSequence>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSequenceStep(step: ActionSequence): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSequenceSteps(steps: List<ActionSequence>)

    @Query("DELETE FROM action_sequences WHERE mappingId = :mappingId")
    suspend fun deleteSequenceForMapping(mappingId: Int)

    @Update
    suspend fun updateSequenceStep(step: ActionSequence)

    @Delete
    suspend fun deleteSequenceStep(step: ActionSequence)
}

@Database(
    entities = [GameProfile::class, KeyMapping::class, BindingGroup::class, ActionSequence::class],
    version = 2,
    exportSchema = false
)
abstract class KeyMapperDatabase : RoomDatabase() {
    abstract fun dao(): KeyMapperDao

    companion object {
        @Volatile
        private var INSTANCE: KeyMapperDatabase? = null

        fun getDatabase(context: Context): KeyMapperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KeyMapperDatabase::class.java,
                    "key_mapper_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
