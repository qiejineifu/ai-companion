package com.aicompanion.domain.repository

import com.aicompanion.core.common.Result
import com.aicompanion.domain.model.Live2DModelInfo
import kotlinx.coroutines.flow.Flow

interface Live2DModelRepository {
    fun getAll(): Flow<List<Live2DModelInfo>>
    suspend fun getActive(): Live2DModelInfo?
    suspend fun setActive(id: String)
    suspend fun importModel(modelJsonUri: String): Result<Live2DModelInfo>
    suspend fun delete(id: String)
}
