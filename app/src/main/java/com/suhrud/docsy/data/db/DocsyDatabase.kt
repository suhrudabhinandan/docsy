package com.suhrud.docsy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus

class DocsyConverters {
    @TypeConverter
    fun fromStatus(status: DocumentStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DocumentStatus = try {
        DocumentStatus.valueOf(value)
    } catch (_: Exception) {
        DocumentStatus.DISCOVERED
    }
}

@Database(
    entities = [
        DocumentEntity::class,
        com.suhrud.docsy.data.db.entity.FactEntity::class,
        com.suhrud.docsy.data.db.entity.ChatSessionEntity::class,
        com.suhrud.docsy.data.db.entity.ChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DocsyConverters::class)
abstract class DocsyDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun factDao(): com.suhrud.docsy.data.db.dao.FactDao
    abstract fun chatDao(): com.suhrud.docsy.data.db.dao.ChatDao

    companion object {
        private const val DATABASE_NAME = "docsy_knowledge.db"

        @Volatile
        private var INSTANCE: DocsyDatabase? = null

        fun getInstance(context: Context): DocsyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocsyDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
