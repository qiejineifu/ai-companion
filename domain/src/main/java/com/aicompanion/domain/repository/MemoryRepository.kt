package com.aicompanion.domain.repository

import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getByPersona(personaId: String): Flow<List<MemoryEntry>>
    suspend fun searchRelevant(personaId: String, query: String, topK: Int): List<MemoryEntry>
    suspend fun extractAndSave(conversationId: String, personaId: String): Result<List<MemoryEntry>>
    suspend fun save(entry: MemoryEntry): Result<MemoryEntry>
    suspend fun delete(id: String)
    suspend fun markAccessed(id: String)
}
