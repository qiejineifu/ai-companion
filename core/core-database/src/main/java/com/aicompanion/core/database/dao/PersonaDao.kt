package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY isPreset DESC, createdAt DESC")
    fun getAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE isPreset = 1")
    suspend fun getPresets(): List<PersonaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaEntity)

    @Update
    suspend fun update(persona: PersonaEntity)

    @Delete
    suspend fun delete(persona: PersonaEntity)
}
