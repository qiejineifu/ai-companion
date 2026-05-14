package com.aicompanion.data.repository

import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.StickerDao
import com.aicompanion.core.database.entity.StickerEntity
import com.aicompanion.domain.model.StickerItem
import com.aicompanion.domain.repository.StickerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StickerRepositoryImpl(private val dao: StickerDao) : StickerRepository {

    override fun getByPersona(personaId: String): Flow<List<StickerItem>> =
        dao.getByPersona(personaId).map { list -> list.map { it.toDomain() } }

    override suspend fun getRandomByEmotion(personaId: String, emotion: String): StickerItem? =
        dao.getRandomByEmotion(personaId, emotion)?.toDomain()

    override suspend fun addSticker(personaId: String, emotion: String, imagePath: String): StickerItem {
        val entity = StickerEntity(id = newId(), personaId = personaId, emotion = emotion, imagePath = imagePath, createdAt = now())
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun StickerEntity.toDomain() = StickerItem(id = id, personaId = personaId, emotion = emotion, imagePath = imagePath, createdAt = createdAt)
}
