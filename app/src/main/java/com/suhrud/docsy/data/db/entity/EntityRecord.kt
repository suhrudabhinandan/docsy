package com.suhrud.docsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entities",
    indices = [
        Index(value = ["type"]),
        Index(value = ["canonical_name"])
    ]
)
data class EntityRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "type")
    val type: String, // person, organisation, institution, course, period, account, vehicle, place, date
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "embedding")
    val embedding: String? = null
)
