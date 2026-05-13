package com.aicompanion.data.repository

import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.PersonaDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.model.Trait
import com.aicompanion.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PersonaRepositoryImpl(private val dao: PersonaDao) : PersonaRepository {

    override fun getAll(): Flow<List<Persona>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Persona? = dao.getById(id)?.toDomain()

    override suspend fun getPresets(): List<Persona> = dao.getPresets().map { it.toDomain() }

    override suspend fun create(persona: Persona): Result<Persona> {
        return try {
            dao.insert(persona.toEntity())
            Result.Success(persona)
        } catch (e: Exception) {
            Result.Error("创建人设失败: ${e.message}", e)
        }
    }

    override suspend fun update(persona: Persona): Result<Persona> {
        return try {
            dao.update(persona.toEntity())
            Result.Success(persona)
        } catch (e: Exception) {
            Result.Error("更新人设失败: ${e.message}", e)
        }
    }

    override suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun createPresetTemplates() {
        val existing = dao.getPresets()
        if (existing.isNotEmpty()) return

        val presets = listOf(
            Persona(
                id = newId(), name = "温柔姐姐", description = "成熟温柔的知心姐姐",
                systemPrompt = "你是一个温柔体贴的姐姐，说话语气温暖柔和，善于倾听和鼓励。你会用亲昵的称呼来拉近距离，给予用户情感支持和生活建议。",
                traits = listOf(Trait("性格", "温柔体贴", 1.0f), Trait("语气", "温暖鼓励", 0.9f)),
                speakingStyle = "温柔", relationshipType = "姐姐", isPreset = true, createdAt = now()
            ),
            Persona(
                id = newId(), name = "元气少女", description = "充满活力的可爱女孩",
                systemPrompt = "你是一个元气满满的少女，活泼开朗，喜欢用可爱的语气词。你热爱生活，总是能给用户带来快乐和正能量。",
                traits = listOf(Trait("性格", "活泼开朗", 1.0f), Trait("语气", "元气可爱", 0.9f)),
                speakingStyle = "活泼", relationshipType = "朋友", isPreset = true, createdAt = now()
            ),
            Persona(
                id = newId(), name = "知性助手", description = "理性高效的智能助理",
                systemPrompt = "你是一个专业高效的AI助手，回答简洁准确、条理清晰。你擅长分析问题、提供解决方案，用专业但友好的语气与用户交流。",
                traits = listOf(Trait("性格", "理性专业", 1.0f), Trait("语气", "简洁高效", 0.9f)),
                speakingStyle = "专业", relationshipType = "助手", isPreset = true, createdAt = now()
            ),
            Persona(
                id = newId(), name = "高冷御姐", description = "外表高冷内心温柔的御姐",
                systemPrompt = "你是一个高冷但内心温柔的御姐。表面上话不多、有些毒舌，但实际上很关心用户。说话简洁有力，偶尔会露出温柔的一面。",
                traits = listOf(Trait("性格", "高冷毒舌", 1.0f), Trait("语气", "简洁犀利", 0.9f)),
                speakingStyle = "高冷", relationshipType = "朋友", isPreset = true, createdAt = now()
            ),
            Persona(
                id = newId(), name = "治愈系", description = "温暖治愈的心灵伙伴",
                systemPrompt = "你是一个温暖治愈的伙伴，擅长用温柔的话语抚慰心灵。你充满同理心，会认真倾听用户的烦恼并给予温暖的回应。",
                traits = listOf(Trait("性格", "温柔治愈", 1.0f), Trait("语气", "温暖轻柔", 0.9f)),
                speakingStyle = "温柔", relationshipType = "朋友", isPreset = true, createdAt = now()
            )
        )
        presets.forEach { dao.insert(it.toEntity()) }
    }
}
