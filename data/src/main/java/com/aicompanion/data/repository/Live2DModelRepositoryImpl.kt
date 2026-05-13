package com.aicompanion.data.repository

import android.content.Context
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.Live2DModelInfoDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.domain.model.Live2DModelInfo
import com.aicompanion.domain.repository.Live2DModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class Live2DModelRepositoryImpl(
    private val dao: Live2DModelInfoDao,
    private val context: Context
) : Live2DModelRepository {

    override fun getAll(): Flow<List<Live2DModelInfo>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActive(): Live2DModelInfo? = dao.getActive()?.toDomain()

    override suspend fun setActive(id: String) {
        dao.deactivateAll()
        dao.setActive(id)
    }

    override suspend fun importModel(modelJsonUri: String): Result<Live2DModelInfo> {
        return try {
            val modelDir = File(context.filesDir, "live2d_models")
            modelDir.mkdirs()

            val modelId = newId()
            val destDir = File(modelDir, modelId)
            destDir.mkdirs()

            // Copy model files from URI
            val sourceFile = File(modelJsonUri)
            val destFile = File(destDir, sourceFile.name)
            sourceFile.copyTo(destFile)

            val modelInfo = Live2DModelInfo(
                id = modelId,
                name = sourceFile.parentFile?.name ?: "导入模型",
                modelJsonPath = destFile.absolutePath,
                isBuiltIn = false,
                isActive = true,
                importedAt = now()
            )

            dao.deactivateAll()
            dao.insert(
                com.aicompanion.core.database.entity.Live2DModelInfoEntity(
                    id = modelInfo.id, name = modelInfo.name,
                    modelJsonPath = modelInfo.modelJsonPath,
                    isBuiltIn = false, isActive = true, importedAt = modelInfo.importedAt
                )
            )
            Result.Success(modelInfo)
        } catch (e: Exception) {
            Result.Error("导入模型失败: ${e.message}", e)
        }
    }

    override suspend fun delete(id: String) = dao.deleteById(id)
}
