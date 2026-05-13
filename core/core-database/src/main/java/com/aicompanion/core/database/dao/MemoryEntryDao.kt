package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.MemoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEntryDao {
    @Query("SELECT * FROM memory_entries WHERE personaId = :personaId ORDER BY importance DESC, lastAccessedAt DESC")
    fun getByPersona(personaId: String): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE id = :id")
    suspend fun getById(id: String): MemoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MemoryEntryEntity)

    @Update
    suspend fun update(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_entries WHERE personaId = :personaId")
    suspend fun deleteByPersona(personaId: String)

    @Query("UPDATE memory_entries SET lastAccessedAt = :timestamp, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun markAccessed(id: String, timestamp: Long)
}
