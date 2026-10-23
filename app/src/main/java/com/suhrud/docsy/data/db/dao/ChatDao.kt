package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.suhrud.docsy.data.db.entity.ChatMessageEntity
import com.suhrud.docsy.data.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("UPDATE chats SET title = :title, title_source = :titleSource, updated_at = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(
        sessionId: Long,
        title: String,
        titleSource: String = "GENERATED",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM chats WHERE id IN (SELECT DISTINCT chat_id FROM messages) ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chats WHERE id IN (SELECT DISTINCT chat_id FROM messages) ORDER BY updated_at DESC")
    suspend fun getAllSessionsSnapshot(): List<ChatSessionEntity>

    @Query("SELECT * FROM chats WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChatSessionEntity?

    @Query("DELETE FROM chats WHERE id = :sessionId")
    suspend fun deleteSessionOnly(sessionId: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAllSessionsOnly()

    // Message Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM messages WHERE chat_id = :sessionId ORDER BY ts ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM messages WHERE chat_id = :sessionId ORDER BY ts ASC")
    suspend fun getMessagesForSessionSnapshot(sessionId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM messages WHERE chat_id = :sessionId")
    suspend fun deleteMessagesForSessionOnly(sessionId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessagesOnly()

    @Query("DELETE FROM chat_context WHERE chat_id = :sessionId")
    suspend fun deleteContextForSessionOnly(sessionId: Long)

    @Query("DELETE FROM chat_context")
    suspend fun deleteAllContextsOnly()

    // Combined Cascading Deletes to avoid orphan rows across chats, messages, and chat_context
    @Transaction
    suspend fun deleteSession(sessionId: Long) {
        deleteMessagesForSessionOnly(sessionId)
        deleteContextForSessionOnly(sessionId)
        deleteSessionOnly(sessionId)
    }

    @Transaction
    suspend fun deleteAllSessions() {
        deleteAllMessagesOnly()
        deleteAllContextsOnly()
        deleteAllSessionsOnly()
    }
}
