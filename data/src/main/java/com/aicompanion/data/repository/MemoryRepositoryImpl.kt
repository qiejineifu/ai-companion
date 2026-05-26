package com.aicompanion.data.repository

import android.util.Log
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.database.dao.MemoryEntryDao
import com.aicompanion.data.mapper.toDomain
import com.aicompanion.data.mapper.toEntity
import com.aicompanion.domain.model.MemoryEntry
import com.aicompanion.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.ln
import java.util.concurrent.TimeUnit

private const val TAG = "MemoryRepo"
private const val MAX_MEMORIES = 50
private const val MIN_KEEP_SCORE = 0.05f

class MemoryRepositoryImpl(private val dao: MemoryEntryDao) : MemoryRepository {

    override fun getByPersona(personaId: String): Flow<List<MemoryEntry>> =
        dao.getByPersona(personaId).map { list -> list.map { it.toDomain() } }

    override suspend fun searchRelevant(
        personaId: String, query: String, topK: Int
    ): List<MemoryEntry> {
        val entities = dao.getByPersona(personaId).first()
        val now = System.currentTimeMillis()
        val queryTerms = query.split(" ").filter { it.length >= 2 }
        if (queryTerms.isEmpty()) return emptyList()

        // Hybrid scoring: keyword * recency * importance
        data class Scored(val entry: MemoryEntry, val score: Float)

        return entities.map { it.toDomain() }
            .map { entry ->
                val keywordScore = if (queryTerms.any { term ->
                        entry.content.contains(term, ignoreCase = true)
                    }) 1.0f else 0f
                if (keywordScore == 0f) Scored(entry, 0f)
                else {
                    val daysSince = TimeUnit.MILLISECONDS.toDays(now - entry.createdAt).coerceAtLeast(0)
                    val recency = 1.0f / (1.0f + ln(1.0 + daysSince.toDouble()).toFloat())
                    val score = 0.4f * keywordScore + 0.3f * recency + 0.3f * entry.importance
                    Scored(entry, score)
                }
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(topK)
            .map { it.entry }
    }

    override suspend fun extractAndSave(conversationId: String, personaId: String): Result<List<MemoryEntry>> {
        return Result.Success(emptyList())
    }

    override suspend fun save(entry: MemoryEntry): Result<MemoryEntry> {
        return try {
            // Check for near-duplicate and consolidate
            val consolidated = consolidateWithExisting(entry)
            if (consolidated != null) {
                dao.insert(consolidated.toEntity())
                Result.Success(consolidated)
            } else {
                dao.insert(entry.toEntity())
                // Auto-cleanup if over limit
                cleanupIfNeeded(entry.personaId)
                Result.Success(entry)
            }
        } catch (e: Exception) {
            Result.Error("保存记忆失败: ${e.message}", e)
        }
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun markAccessed(id: String) = dao.markAccessed(id, now())

    // === Auto-cleanup ===

    private suspend fun cleanupIfNeeded(personaId: String) {
        val all = dao.getByPersona(personaId).first()
        if (all.size <= MAX_MEMORIES) return

        val now = System.currentTimeMillis()
        val scored = all.map { entity ->
            val daysSince = TimeUnit.MILLISECONDS.toDays(now - entity.createdAt).coerceAtLeast(0)
            val keepScore = entity.importance *
                (1.0f / (1.0f + Math.pow(daysSince.toDouble(), 0.5).toFloat())) *
                (1f + ln(1.0 + entity.accessCount.toDouble()).toFloat() * 0.1f)
            entity.id to keepScore
        }
        val toDelete = scored.sortedBy { it.second }
            .take(scored.size - MAX_MEMORIES)
            .map { it.first }
        toDelete.forEach { dao.deleteById(it) }
        if (toDelete.isNotEmpty()) Log.d(TAG, "Cleaned up ${toDelete.size} low-score memories")
    }

    // === Memory consolidation ===

    private suspend fun consolidateWithExisting(newEntry: MemoryEntry): MemoryEntry? {
        val existing = dao.getByPersona(newEntry.personaId).first().map { it.toDomain() }
        // Strip category tag for similarity comparison
        val tagPattern = "^\\[\\w+\\]\\s*".toRegex()
        val newContent = newEntry.content.replace(tagPattern, "")
        for (e in existing) {
            val existContent = e.content.replace(tagPattern, "")
            val similarity = jaccardSimilarity(newContent, existContent)
            if (similarity > 0.7f) {
                // Merge: keep highest importance, combine content if different
                val mergedContent = if (newContent != existContent) "$existContent；$newContent"
                else existContent
                val categoryTag = newEntry.content.replace(Regex("\\].*"), "]")
                val merged = MemoryEntry(
                    id = e.id,
                    personaId = e.personaId,
                    content = "$categoryTag $mergedContent",
                    importance = maxOf(e.importance, newEntry.importance),
                    confidence = maxOf(e.confidence, newEntry.confidence),
                    createdAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis(),
                    accessCount = e.accessCount + 1
                )
                Log.d(TAG, "Consolidated: $existContent + $newContent")
                return merged
            }
        }
        return null
    }

    private fun jaccardSimilarity(a: String, b: String): Float {
        val setA = a.toCharArray().toSet()
        val setB = b.toCharArray().toSet()
        val intersection = setA.intersect(setB).size
        val union = setA.union(setB).size
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}
