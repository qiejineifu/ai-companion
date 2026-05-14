package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.WorldBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {
    @Query("SELECT * FROM world_book_entries WHERE personaId = :personaId ORDER BY priority DESC")
    fun getByPersona(personaId: String): Flow<List<WorldBookEntity>>

    @Query("SELECT * FROM world_book_entries WHERE personaId = :personaId AND enabled = 1 ORDER BY priority DESC")
    suspend fun getEnabledByPersona(personaId: String): List<WorldBookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WorldBookEntity)

    @Update
    suspend fun update(entry: WorldBookEntity)

    @Query("DELETE FROM world_book_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
