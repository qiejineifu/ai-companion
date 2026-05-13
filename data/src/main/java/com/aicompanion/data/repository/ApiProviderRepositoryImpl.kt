package com.aicompanion.data.repository

import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.ApiProviderDao
import com.aicompanion.core.network.HttpClientFactory
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.ApiProvider
import com.aicompanion.domain.repository.ApiProviderRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ApiProviderRepositoryImpl(
    private val dao: ApiProviderDao
) : ApiProviderRepository {

    override fun getAll(): Flow<List<ApiProvider>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActive(): ApiProvider? = dao.getActive()?.toDomain()

    override suspend fun create(provider: ApiProvider): Result<ApiProvider> {
        return try {
            dao.insert(provider.toEntity())
            Result.Success(provider)
        } catch (e: Exception) {
            Result.Error("创建API配置失败: ${e.message}", e)
        }
    }

    override suspend fun update(provider: ApiProvider): Result<ApiProvider> {
        return try {
            dao.update(provider.toEntity())
            Result.Success(provider)
        } catch (e: Exception) {
            Result.Error("更新API配置失败: ${e.message}", e)
        }
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun setActive(id: String) {
        dao.deactivateAll()
        dao.setActive(id)
    }

    override suspend fun testConnection(provider: ApiProvider): Result<Boolean> {
        return try {
            val client = HttpClientFactory.create()
            val apiKey = try {
                CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
            } catch (_: Exception) { provider.apiKeyEncrypted }

            // Derive base URL: strip "/chat/completions" to get the API root
            val base = provider.baseUrl.trimEnd('/').removeSuffix("/chat/completions")
            val modelsUrl = "$base/models"
            val response = client.get(modelsUrl) {
                header("Authorization", "Bearer $apiKey")
            }
            if (response.status.value in 200..299) {
                Result.Success(true)
            } else {
                val body = try { response.bodyAsText() } catch (_: Exception) { "" }
                Result.Error("HTTP ${response.status.value}: ${body.take(200)}")
            }
        } catch (e: Exception) {
            Result.Error("连接失败: ${e.message}")
        }
    }

    override suspend fun createPresetTemplates() {
        val all = dao.getAll()
        // Flow, so we check via a different approach
        val templates = listOf(
            Template("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o", "openai"),
            Template("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", "deepseek"),
            Template("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", "qwen"),
            Template("Claude", "https://api.anthropic.com/v1/messages", "claude-sonnet-4-20250514", "anthropic"),
            Template("硅基流动", "https://api.siliconflow.cn/v1/chat/completions", "deepseek-ai/DeepSeek-V3", "siliconflow")
        )
        // Templates are shown in UI, not auto-saved
    }

    data class Template(val name: String, val baseUrl: String, val modelName: String, val key: String)
}
