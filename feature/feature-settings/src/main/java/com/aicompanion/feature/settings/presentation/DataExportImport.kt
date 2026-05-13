package com.aicompanion.feature.settings.presentation

import android.content.Context
import android.net.Uri
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.onError
import com.aicompanion.core.common.onSuccess
import com.aicompanion.domain.model.*
import com.aicompanion.domain.repository.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExportData(
    val version: String = "1.0",
    val exportDate: Long = System.currentTimeMillis(),
    val personas: List<Persona> = emptyList(),
    val apiProviders: List<ApiProvider> = emptyList(),
    val conversations: List<ConversationExport> = emptyList(),
    val memories: List<MemoryEntry> = emptyList(),
    val voiceProfiles: List<VoiceProfile> = emptyList()
)

data class ConversationExport(
    val conversation: Conversation,
    val messages: List<Message>
)

class DataExportImport(
    private val context: Context,
    private val personaRepository: PersonaRepository,
    private val apiProviderRepository: ApiProviderRepository,
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val voiceRepository: VoiceRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToJson(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val personas = mutableListOf<Persona>()
            personaRepository.getAll().first().let { personas.addAll(it) }

            val providers = mutableListOf<ApiProvider>()
            apiProviderRepository.getAll().first().let { providers.addAll(it) }

            val memories = mutableListOf<MemoryEntry>()
            val persona = personas.firstOrNull()
            if (persona != null) {
                memoryRepository.getByPersona(persona.id).first().let { memories.addAll(it) }
            }

            val voiceProfiles = mutableListOf<VoiceProfile>()
            voiceRepository.getProfiles().first().let { voiceProfiles.addAll(it) }

            // Export conversations with messages
            val conversationExports = mutableListOf<ConversationExport>()
            chatRepository.getConversations().first().forEach { conv ->
                val messages = mutableListOf<Message>()
                chatRepository.getMessages(conv.id).first().let { messages.addAll(it) }
                // Sanitize API keys before export
                val sanitizedConv = conv
                conversationExports.add(ConversationExport(sanitizedConv, messages))
            }

            val exportData = ExportData(
                personas = personas.map { it.copy(isPreset = false) },
                apiProviders = providers.map { it.copy(apiKeyEncrypted = "**REDACTED**") },
                conversations = conversationExports,
                memories = memories,
                voiceProfiles = voiceProfiles
            )

            val json = gson.toJson(exportData)
            Result.Success(json)
        } catch (e: Exception) {
            Result.Error("导出失败: ${e.message}", e)
        }
    }

    suspend fun exportToZip(outputUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val json = when (val r = exportToJson()) {
                is Result.Success -> r.data
                is Result.Error -> return@withContext Result.Error(r.message)
            }

            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                    // Add JSON data
                    zip.putNextEntry(ZipEntry("ai_companion_data.json"))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    // Add export metadata
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val metadata = """
                        导出时间: ${sdf.format(Date())}
                        应用版本: 0.2.0
                        数据格式: JSON
                    """.trimIndent()
                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write(metadata.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            Result.Success(json.toByteArray().size.toLong())
        } catch (e: Exception) {
            Result.Error("导出 ZIP 失败: ${e.message}", e)
        }
    }

    suspend fun importFromJson(json: String): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            val exportData = gson.fromJson(json, ExportData::class.java)
            var personaCount = 0
            var convCount = 0
            var memoryCount = 0
            val errors = mutableListOf<String>()

            // Import personas
            for (persona in exportData.personas) {
                val result = personaRepository.create(persona)
                result.onSuccess { personaCount++ }
                result.onError { errors.add("人设导入失败: $it") }
            }

            // Import memories
            for (memory in exportData.memories) {
                val result = memoryRepository.save(memory)
                result.onSuccess { memoryCount++ }
                result.onError { errors.add("记忆导入失败: $it") }
            }

            // Import conversations
            for (convExport in exportData.conversations) {
                val result = chatRepository.createConversation(
                    convExport.conversation.personaId,
                    convExport.conversation.apiProviderId,
                    convExport.conversation.title
                )
                result.onSuccess { conv ->
                    convCount++
                }
                result.onError { errors.add("对话导入失败: $it") }
            }

            Result.Success(
                ImportSummary(
                    personaCount = personaCount,
                    conversationCount = convCount,
                    memoryCount = memoryCount,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.Error("导入失败: JSON 格式不正确 (${e.message})", e)
        }
    }

    suspend fun importFromZip(inputUri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "ai_companion_data.json") {
                            val json = zip.readBytes().toString(Charsets.UTF_8)
                            return@withContext importFromJson(json)
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            Result.Error("ZIP 文件中未找到数据文件")
        } catch (e: Exception) {
            Result.Error("导入 ZIP 失败: ${e.message}", e)
        }
    }

    suspend fun validateExportJson(json: String): Result<ExportData> {
        return try {
            val data = gson.fromJson(json, ExportData::class.java)
            if (data.version.isEmpty()) {
                Result.Error("无效的导出文件格式")
            } else {
                Result.Success(data)
            }
        } catch (e: Exception) {
            Result.Error("JSON 解析失败: ${e.message}")
        }
    }
}

data class ImportSummary(
    val personaCount: Int = 0,
    val conversationCount: Int = 0,
    val memoryCount: Int = 0,
    val errors: List<String> = emptyList()
)
