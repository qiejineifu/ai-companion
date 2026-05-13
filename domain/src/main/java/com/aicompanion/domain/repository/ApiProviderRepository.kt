package com.aicompanion.domain.repository

import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.ApiProvider
import kotlinx.coroutines.flow.Flow

interface ApiProviderRepository {
    fun getAll(): Flow<List<ApiProvider>>
    suspend fun getActive(): ApiProvider?
    suspend fun create(provider: ApiProvider): Result<ApiProvider>
    suspend fun update(provider: ApiProvider): Result<ApiProvider>
    suspend fun delete(id: String)
    suspend fun setActive(id: String)
    suspend fun testConnection(provider: ApiProvider): Result<Boolean>
    suspend fun createPresetTemplates()
}
