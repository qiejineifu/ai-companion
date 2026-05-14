package com.aicompanion.data.repository

import com.aicompanion.core.database.dao.WorldBookDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.WorldBookEntry
import com.aicompanion.domain.repository.WorldBookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorldBookRepositoryImpl(
    private val dao: WorldBookDao
) : WorldBookRepository {

    override fun getByPersona(personaId: String): Flow<List<WorldBookEntry>> =
        dao.getByPersona(personaId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEnabledByPersona(personaId: String): List<WorldBookEntry> =
        dao.getEnabledByPersona(personaId).map { it.toDomain() }

    override suspend fun create(entry: WorldBookEntry) = dao.insert(entry.toEntity())

    override suspend fun update(entry: WorldBookEntry) = dao.insert(entry.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)
}
