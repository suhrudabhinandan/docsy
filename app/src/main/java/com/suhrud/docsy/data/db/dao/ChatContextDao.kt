package com.suhrud.docsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suhrud.docsy.data.db.entity.ChatContextEntity

@Dao
interface ChatContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContext(context: ChatContextEntity): Long

    @Query("SELECT * FROM chat_context WHERE chat_id = :chatId ORDER BY turn ASC")
    suspend fun getContextsForChat(chatId: Long): List<ChatContextEntity>

    @Query("SELECT * FROM chat_context WHERE chat_id = :chatId ORDER BY turn DESC LIMIT 1")
    suspend fun getLatestContextForChat(chatId: Long): ChatContextEntity?

    @Query("DELETE FROM chat_context WHERE chat_id = :chatId AND id NOT IN (SELECT id FROM chat_context WHERE chat_id = :chatId ORDER BY turn DESC LIMIT :maxTurns)")
    suspend fun trimContextHistory(chatId: Long, maxTurns: Int = 10)

    @Query("DELETE FROM chat_context WHERE chat_id = :chatId")
    suspend fun deleteContextForChat(chatId: Long)

    @Query("DELETE FROM chat_context")
    suspend fun deleteAllContexts()
}
