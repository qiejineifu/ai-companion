package com.aicompanion.core.database.dao

import androidx.room.*
import com.aicompanion.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY lastMessageAt DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE conversations SET lastMessageAt = :timestamp, lastMessagePreview = :preview WHERE id = :id")
    suspend fun updateLastMessage(id: String, timestamp: Long, preview: String)

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteMessagesForConversation(id: String)

    @androidx.room.Transaction
    suspend fun hardDelete(id: String) {
        deleteMessagesForConversation(id)
        deleteById(id)
    }
}
