package com.aicompanion.feature.memory

import com.aicompanion.domain.model.MemoryEntry
import kotlin.math.ln
import kotlin.math.sqrt

class MemorySearchEngine {

    data class ScoredMemory(
        val memory: MemoryEntry,
        val score: Float,
        val matchReasons: List<String> = emptyList()
    )

    fun search(
        query: String,
        memories: List<MemoryEntry>,
        topK: Int = 5,
        minConfidence: Float = 0.5f
    ): List<ScoredMemory> {
        if (memories.isEmpty()) return emptyList()

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        return memories
            .map { memory -> scoreMemory(memory, queryTerms) }
            .filter { it.score > 0f && it.memory.confidence >= minConfidence }
            .sortedWith(compareByDescending<ScoredMemory> { it.score }
                .thenByDescending { it.memory.importance }
                .thenByDescending { it.memory.lastAccessedAt })
            .take(topK)
    }

    private fun scoreMemory(memory: MemoryEntry, queryTerms: List<String>): ScoredMemory {
        val memoryTerms = tokenize(memory.content)
        val reasons = mutableListOf<String>()

        // TF-IDF-like scoring
        var score = 0f

        for (qTerm in queryTerms) {
            // Exact match
            if (memory.content.contains(qTerm, ignoreCase = true)) {
                score += 0.4f
                reasons.add("exact:$qTerm")
            }
            // Substring match in memory terms
            for (mTerm in memoryTerms) {
                if (mTerm.contains(qTerm, ignoreCase = true) || qTerm.contains(mTerm, ignoreCase = true)) {
                    score += 0.2f
                    reasons.add("partial:$qTerm>$mTerm")
                }
            }
            // Character overlap (Jaccard-like)
            val overlap = qTerm.toSet().intersect(memory.content.toSet()).size.toFloat()
            val union = qTerm.toSet().union(memory.content.toSet()).size.toFloat()
            if (union > 0) {
                score += 0.1f * (overlap / union)
            }
        }

        // Importance boost
        score *= (0.5f + 0.5f * memory.importance)

        // Recency boost
        val daysSinceAccess = (System.currentTimeMillis() - memory.lastAccessedAt) / (24 * 3600 * 1000f)
        val recencyFactor = 1.0f / (1.0f + ln(1.0f + daysSinceAccess).toFloat())
        score *= recencyFactor

        // Decay factor
        score *= memory.decayFactor

        return ScoredMemory(memory, score.coerceIn(0f, 1f), reasons.distinct())
    }

    private fun tokenize(text: String): List<String> {
        // Chinese + English tokenizer
        val tokens = mutableListOf<String>()

        // Extract Chinese character bigrams
        val chineseChars = text.filter { it in '一'..'鿿' }
        if (chineseChars.length >= 2) {
            chineseChars.windowed(2).forEach { tokens.add(it) }
        }
        if (chineseChars.length == 1) {
            tokens.add(chineseChars)
        }

        // Extract English/Number words
        val wordPattern = Regex("[a-zA-Z0-9]+")
        wordPattern.findAll(text).forEach { tokens.add(it.value.lowercase()) }

        // Add single Chinese chars for partial matching
        chineseChars.forEach { tokens.add(it.toString()) }

        return tokens.distinct().filter { it.length >= 1 }
    }
}
