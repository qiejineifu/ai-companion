package com.aicompanion.domain.repository

import com.aicompanion.domain.model.WorldBookEntry
import kotlinx.coroutines.flow.Flow

interface WorldBookRepository {
    fun getByPersona(personaId: String): Flow<List<WorldBookEntry>>
    suspend fun getEnabledByPersona(personaId: String): List<WorldBookEntry>
    suspend fun create(entry: WorldBookEntry)
    suspend fun update(entry: WorldBookEntry)
    suspend fun delete(id: String)
}
