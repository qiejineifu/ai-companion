package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.StickerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM stickers WHERE personaId = :personaId ORDER BY createdAt DESC")
    fun getByPersona(personaId: String): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE personaId = :personaId AND emotion = :emotion ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomByEmotion(personaId: String, emotion: String): StickerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sticker: StickerEntity)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun deleteById(id: String)
}
