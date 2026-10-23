package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_context",
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["ts"])
    ]
)
data class ChatContextEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    @ColumnInfo(name = "turn")
    val turn: Int,
    @ColumnInfo(name = "entities_json")
    val entitiesJson: String? = null,
    @ColumnInfo(name = "active_doc_ids")
    val activeDocIds: String? = null, // JSON list or comma-separated doc IDs
    @ColumnInfo(name = "active_owner")
    val activeOwner: String? = null,
    @ColumnInfo(name = "active_doc_type")
    val activeDocType: String? = null,
    @ColumnInfo(name = "active_period")
    val activePeriod: String? = null,
    @ColumnInfo(name = "last_field")
    val lastField: String? = null,
    @ColumnInfo(name = "last_intent")
    val lastIntent: String? = null,
    @ColumnInfo(name = "ts")
    val ts: Long = System.currentTimeMillis()
)
