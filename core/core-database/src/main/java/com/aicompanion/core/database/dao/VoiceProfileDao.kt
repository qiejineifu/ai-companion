package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.VoiceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): VoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VoiceProfileEntity)

    @Update
    suspend fun update(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE voice_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE voice_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)
}
