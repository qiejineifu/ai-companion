package com.aicompanion.feature.memory

import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.domain.model.MemoryEntry
import com.aicompanion.domain.model.Message

class MemoryExtractor {

    data class ExtractedFact(
        val content: String,
        val confidence: Float,
        val importance: Float,
        val category: String
    )

    private val extractionPatterns = listOf(
        // Personal info
        ExtractionRule(
            category = "personal",
            patterns = listOf(
                Regex("(?:我(?:叫|是|的名字是))(.+?)(?:[，。！,.!]|$)"),
                Regex("(?:我(?:今年)?)(\\d+)(?:岁)"),
                Regex("(?:我(?:在|住在|位于))(.+?)(?:[，。！,.!]|$)"),
                Regex("(?:我(?:的)?)(?:电话|手机|联系方式)(?:是|：|:)?(.+?)(?:[，。！,.!]|$)"),
            ),
            importance = 0.9f
        ),
        // Preferences
        ExtractionRule(
            category = "preference",
            patterns = listOf(
                Regex("(?:我(?:喜欢|爱好|爱吃|爱看|爱听|爱玩))(.+?)(?:[，。！,.!]|$)"),
                Regex("(?:我(?:不喜欢|讨厌|不爱))(.+?)(?:[，。！,.!]|$)"),
                Regex("(?:我(?:最(?:喜欢|爱)))(.+?)(?:[，。！,.!]|$)"),
            ),
            importance = 0.7f
        ),
        // Life events
        ExtractionRule(
            category = "life_event",
            patterns = listOf(
                Regex("(?:我(?:刚|最近|今天|昨天|上周))(.+?)(?:[，。！,.!]|$)"),
                Regex("(?:我(?:要|准备|打算|计划))(.+?)(?:[，。！,.!]|$)"),
            ),
            importance = 0.6f
        ),
        // Emotional state
        ExtractionRule(
            category = "emotional",
            patterns = listOf(
                Regex("(?:我(?:觉得|感觉|感到|好|很))(.+?)(?:[，。！,.!]|$)"),
                Regex("(?:我(?:心情|情绪))(.+?)(?:[，。！,.!]|$)"),
            ),
            importance = 0.5f
        )
    )

    fun extractFromMessages(messages: List<Message>, personaId: String): List<MemoryEntry> {
        val userMessages = messages.filter { it.role == "user" }
        if (userMessages.isEmpty()) return emptyList()

        val extractedFacts = mutableListOf<ExtractedFact>()

        for (message in userMessages.takeLast(10)) {
            for (rule in extractionPatterns) {
                for (pattern in rule.patterns) {
                    pattern.findAll(message.content).forEach { match ->
                        val fact = match.groupValues.getOrNull(1)?.trim() ?: return@forEach
                        if (fact.length in 2..50) {
                            extractedFacts.add(
                                ExtractedFact(
                                    content = fact,
                                    confidence = 0.7f,
                                    importance = rule.importance,
                                    category = rule.category
                                )
                            )
                        }
                    }
                }
            }
        }

        return extractedFacts
            .distinctBy { it.content.lowercase() }
            .map { fact ->
                MemoryEntry(
                    id = newId(),
                    personaId = personaId,
                    content = "[${fact.category}] ${fact.content}",
                    sourceMessageIds = listOf(userMessages.last().id),
                    confidence = fact.confidence,
                    importance = fact.importance,
                    createdAt = now(),
                    lastAccessedAt = now()
                )
            }
    }

    fun extractFromText(text: String, personaId: String): List<MemoryEntry> {
        val facts = mutableListOf<ExtractedFact>()
        for (rule in extractionPatterns) {
            for (pattern in rule.patterns) {
                pattern.findAll(text).forEach { match ->
                    val fact = match.groupValues.getOrNull(1)?.trim() ?: return@forEach
                    if (fact.length in 2..50) {
                        facts.add(ExtractedFact(fact, 0.7f, rule.importance, rule.category))
                    }
                }
            }
        }

        return facts
            .distinctBy { it.content.lowercase() }
            .map { fact ->
                MemoryEntry(
                    id = newId(),
                    personaId = personaId,
                    content = "[${fact.category}] ${fact.content}",
                    sourceMessageIds = emptyList(),
                    confidence = fact.confidence,
                    importance = fact.importance,
                    createdAt = now(),
                    lastAccessedAt = now()
                )
            }
    }

    private data class ExtractionRule(
        val category: String,
        val patterns: List<Regex>,
        val importance: Float
    )
}
