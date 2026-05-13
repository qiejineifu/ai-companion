package com.aicompanion.domain.repository

import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.Persona
import kotlinx.coroutines.flow.Flow

interface PersonaRepository {
    fun getAll(): Flow<List<Persona>>
    suspend fun getById(id: String): Persona?
    suspend fun getPresets(): List<Persona>
    suspend fun create(persona: Persona): Result<Persona>
    suspend fun update(persona: Persona): Result<Persona>
    suspend fun delete(id: String)
    suspend fun createPresetTemplates()
}
