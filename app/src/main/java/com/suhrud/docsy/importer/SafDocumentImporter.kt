package com.suhrud.docsy.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.extractor.ExtractionPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Storage Access Framework (SAF) Document Importer.
 * Allows users to import specific documents using the native system document picker
 * without requiring broad storage access permissions (MANAGE_EXTERNAL_STORAGE).
 */
object SafDocumentImporter {

    suspend fun importDocument(
        context: Context,
        uri: Uri,
        documentDao: DocumentDao,
        extractionPipeline: ExtractionPipeline? = null
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = "imported_doc_${System.currentTimeMillis()}"
            var size = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }

            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val ext = displayName.substringAfterLast('.', "").lowercase()

            val docsDir = File(context.filesDir, "imported_docs").apply { mkdirs() }
            val destFile = File(docsDir, "${System.currentTimeMillis()}_$displayName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (size == 0L) {
                size = destFile.length()
            }

            val docEntity = DocumentEntity(
                path = destFile.absolutePath,
                name = displayName,
                ext = ext,
                mime = mimeType,
                size = size,
                modified = System.currentTimeMillis(),
                state = DocumentStatus.DISCOVERED.name,
                section = "OTHER"
            )

            val id = documentDao.insertDocument(docEntity)
            val savedEntity = docEntity.copy(id = id)

            if (extractionPipeline != null) {
                try {
                    extractionPipeline.extractFile(destFile)
                } catch (_: Exception) {}
            }

            savedEntity
        } catch (_: Exception) {
            null
        }
    }
}
