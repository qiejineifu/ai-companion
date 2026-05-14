package com.aicompanion.domain.repository

import com.aicompanion.domain.model.StickerItem
import kotlinx.coroutines.flow.Flow

interface StickerRepository {
    fun getByPersona(personaId: String): Flow<List<StickerItem>>
    suspend fun getRandomByEmotion(personaId: String, emotion: String): StickerItem?
    suspend fun addSticker(personaId: String, emotion: String, imagePath: String): StickerItem
    suspend fun delete(id: String)
}
