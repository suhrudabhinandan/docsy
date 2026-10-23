package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["updated_at"])
    ]
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "New chat",
    @ColumnInfo(name = "title_source")
    val titleSource: String = "DEFAULT", // "DEFAULT", "GENERATED", "USER"
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chat_id", "ts"]),
        Index(value = ["chat_id"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    val role: String, // "user" or "assistant"
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sources_json")
    val sourcesJson: String? = null,
    val state: String = "completed" // "completed", "pending", "failed"
)
