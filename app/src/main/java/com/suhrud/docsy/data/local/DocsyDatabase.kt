package com.suhrud.docsy.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suhrud.docsy.data.model.ChatMessageEntity
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.data.model.DocumentFieldEntity
import com.suhrud.docsy.data.model.DocumentFts
import com.suhrud.docsy.data.model.StepLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllChatMessagesDirect(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY modifiedDate DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY modifiedDate DESC")
    suspend fun getAllDocumentsDirect(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE pathUri = :pathUri LIMIT 1")
    suspend fun getDocumentByPath(pathUri: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE documentType = :documentType ORDER BY modifiedDate DESC")
    suspend fun getDocumentsByType(documentType: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE documentType = :documentType AND (detectedMonth = :month OR extractedText LIKE '%' || :month || '%') ORDER BY modifiedDate DESC")
    suspend fun getDocumentsByTypeAndMonth(documentType: String, month: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE documentType = :documentType AND detectedYear = :year ORDER BY modifiedDate DESC")
    suspend fun getDocumentsByTypeAndYear(documentType: String, year: Int): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE documentType = :documentType AND (detectedMonth = :month OR extractedText LIKE '%' || :month || '%') AND (detectedYear = :year OR extractedText LIKE '%' || :year || '%') ORDER BY modifiedDate DESC")
    suspend fun getDocumentsByTypeMonthYear(documentType: String, month: String, year: Int): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE detectedUtility = :utility OR documentType LIKE '%' || :utility || '%' ORDER BY modifiedDate DESC")
    suspend fun getDocumentsByUtility(utility: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE (detectedUtility = :utility OR documentType LIKE '%' || :utility || '%') AND (detectedMonth = :month OR extractedText LIKE '%' || :month || '%') ORDER BY modifiedDate DESC")
    suspend fun getDocumentsByUtilityAndMonth(utility: String, month: String): List<DocumentEntity>

    @Query("""
        SELECT documents.* FROM documents
        JOIN documents_fts ON documents.rowid = documents_fts.docid
        WHERE documents_fts MATCH :query
        ORDER BY documents.modifiedDate DESC
    """)
    suspend fun searchFts(query: String): List<DocumentEntity>

    @Query("""
        SELECT * FROM documents
        WHERE fileName LIKE '%' || :query || '%'
           OR extractedText LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
        ORDER BY modifiedDate DESC
    """)
    suspend fun searchKeyword(query: String): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<DocumentEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM documents WHERE pathUri = :pathUri")
    suspend fun deleteDocumentByPath(pathUri: String)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<DocumentFieldEntity>)

    @Query("SELECT * FROM document_fields WHERE documentId = :documentId")
    suspend fun getFieldsForDocument(documentId: String): List<DocumentFieldEntity>

    @Query("SELECT * FROM document_fields WHERE fieldKey = :fieldKey")
    suspend fun getFieldsByKey(fieldKey: String): List<DocumentFieldEntity>

    @Query("""
        SELECT document_fields.* FROM document_fields
        JOIN documents ON document_fields.documentId = documents.id
        WHERE document_fields.fieldKey = :fieldKey
          AND documents.documentType = :docType
    """)
    suspend fun getFieldsByKeyAndDocType(fieldKey: String, docType: String): List<DocumentFieldEntity>

    @Query("DELETE FROM document_fields WHERE documentId = :documentId")
    suspend fun deleteFieldsForDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND pathUri NOT LIKE '%/.%'")
    suspend fun getDocumentCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND documentType = :docType AND pathUri NOT LIKE '%/.%'")
    suspend fun getDocumentCountByType(docType: String): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (mimeType LIKE 'image/%' OR LOWER(extension) IN ('jpg','jpeg','png','webp','heic','heif','gif','bmp')) AND pathUri NOT LIKE '%/.%'")
    suspend fun getPhotoCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (mimeType LIKE 'video/%' OR LOWER(extension) IN ('mp4','mkv','mov','avi','3gp','webm','flv')) AND pathUri NOT LIKE '%/.%'")
    suspend fun getVideoCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (mimeType LIKE 'audio/%' OR LOWER(extension) IN ('mp3','m4a','wav','flac','aac','ogg','opus','wma')) AND pathUri NOT LIKE '%/.%'")
    suspend fun getAudioCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (LOWER(extension) IN ('pdf','docx','doc','txt','xlsx','csv','pptx','xls','ppt','md','json','xml') OR mimeType LIKE '%pdf%' OR mimeType LIKE '%document%' OR mimeType LIKE '%spreadsheet%' OR mimeType LIKE '%presentation%') AND pathUri NOT LIKE '%/.%'")
    suspend fun getOfficeDocCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (LOWER(extension) IN ('pdf') OR mimeType LIKE '%pdf%') AND pathUri NOT LIKE '%/.%'")
    suspend fun getPdfCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (LOWER(extension) IN ('doc','docx') OR mimeType LIKE '%word%') AND pathUri NOT LIKE '%/.%'")
    suspend fun getWordCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (LOWER(extension) IN ('xls','xlsx','csv') OR mimeType LIKE '%excel%' OR mimeType LIKE '%spreadsheet%' OR mimeType = 'text/csv') AND pathUri NOT LIKE '%/.%'")
    suspend fun getExcelCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (LOWER(extension) IN ('ppt','pptx') OR mimeType LIKE '%presentation%' OR mimeType LIKE '%powerpoint%') AND pathUri NOT LIKE '%/.%'")
    suspend fun getPowerPointCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND (LOWER(extension) IN ('txt','log','md','json','xml') OR (mimeType LIKE 'text/%' AND mimeType != 'text/csv')) AND pathUri NOT LIKE '%/.%'")
    suspend fun getTextCount(): Int

    @Query("SELECT * FROM documents WHERE fileSizeBytes > 0 ORDER BY fileSizeBytes DESC LIMIT 1")
    suspend fun getLargestFile(): DocumentEntity?

    @Query("SELECT * FROM documents WHERE fileSizeBytes > 0 ORDER BY fileSizeBytes ASC LIMIT 1")
    suspend fun getSmallestNonZeroFile(): DocumentEntity?

    @Query("SELECT * FROM documents WHERE modifiedDate > 0 ORDER BY modifiedDate ASC LIMIT 1")
    suspend fun getOldestFile(): DocumentEntity?

    @Query("SELECT * FROM documents WHERE modifiedDate > 0 ORDER BY modifiedDate DESC LIMIT 1")
    suspend fun getNewestFile(): DocumentEntity?

    @Query("""
        SELECT * FROM documents
        WHERE fileSizeBytes > 0
          AND (mimeType LIKE :mimePattern OR extension LIKE :extPattern)
        ORDER BY fileSizeBytes DESC LIMIT 1
    """)
    suspend fun getLargestFileByPattern(mimePattern: String, extPattern: String): DocumentEntity?

    @Query("""
        SELECT * FROM documents
        WHERE modifiedDate > 0
          AND (mimeType LIKE :mimePattern OR extension LIKE :extPattern)
        ORDER BY modifiedDate DESC LIMIT 1
    """)
    suspend fun getNewestFileByPattern(mimePattern: String, extPattern: String): DocumentEntity?

    @Query("""
        SELECT * FROM documents
        WHERE modifiedDate > 0
          AND (mimeType LIKE :mimePattern OR extension LIKE :extPattern)
        ORDER BY modifiedDate ASC LIMIT 1
    """)
    suspend fun getOldestFileByPattern(mimePattern: String, extPattern: String): DocumentEntity?
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step_logs WHERE dateString = :dateString LIMIT 1")
    suspend fun getStepLogByDate(dateString: String): StepLogEntity?

    @Query("SELECT * FROM step_logs ORDER BY dateString DESC LIMIT 30")
    suspend fun getRecentStepLogs(): List<StepLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStepLog(stepLog: StepLogEntity)
}

@Database(
    entities = [
        DocumentEntity::class,
        DocumentFts::class,
        DocumentFieldEntity::class,
        StepLogEntity::class,
        ChatMessageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class DocsyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun stepDao(): StepDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: DocsyDatabase? = null

        fun getInstance(context: Context): DocsyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DocsyDatabase::class.java,
                    "docsy_local_vault.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
