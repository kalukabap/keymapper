package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyMapperDao {
    @Query("SELECT * FROM game_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<GameProfile>>

    @Query("SELECT * FROM game_profiles ORDER BY name ASC")
    suspend fun getAllProfilesList(): List<GameProfile>

    @Query("SELECT * FROM game_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: Int): GameProfile?

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId")
    fun getMappingsForProfileFlow(profileId: Int): Flow<List<KeyMapping>>

    @Query("SELECT * FROM key_mappings WHERE profileId = :profileId")
    suspend fun getMappingsForProfileList(profileId: Int): List<KeyMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GameProfile): Long

    @Update
    suspend fun updateProfile(profile: GameProfile)

    @Delete
    suspend fun deleteProfile(profile: GameProfile)

    @Query("DELETE FROM key_mappings WHERE profileId = :profileId")
    suspend fun deleteMappingsForProfile(profileId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: KeyMapping): Long

    @Delete
    suspend fun deleteMapping(mapping: KeyMapping)
}

@Database(entities = [GameProfile::class, KeyMapping::class], version = 1, exportSchema = false)
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
