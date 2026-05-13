package com.aicompanion.data.repository

import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.MemoryEntryDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.MemoryEntry
import com.aicompanion.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MemoryRepositoryImpl(private val dao: MemoryEntryDao) : MemoryRepository {

    override fun getByPersona(personaId: String): Flow<List<MemoryEntry>> =
        dao.getByPersona(personaId).map { list -> list.map { it.toDomain() } }

    override suspend fun searchRelevant(personaId: String, query: String, topK: Int): List<MemoryEntry> {
        val entities = dao.getByPersona(personaId).first()
        return entities.map { it.toDomain() }
            .filter { entry ->
                val queryTerms = query.split(" ").filter { it.length >= 2 }
                queryTerms.any { term -> entry.content.contains(term, ignoreCase = true) }
            }
            .sortedByDescending { it.importance * it.confidence * it.decayFactor }
            .take(topK)
    }

    override suspend fun extractAndSave(conversationId: String, personaId: String): Result<List<MemoryEntry>> {
        // Phase 2: integrate on-device embedding extraction
        return Result.Success(emptyList())
    }

    override suspend fun save(entry: MemoryEntry): Result<MemoryEntry> {
        return try {
            dao.insert(entry.toEntity())
            Result.Success(entry)
        } catch (e: Exception) {
            Result.Error("保存记忆失败: ${e.message}", e)
        }
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun markAccessed(id: String) = dao.markAccessed(id, now())
}
