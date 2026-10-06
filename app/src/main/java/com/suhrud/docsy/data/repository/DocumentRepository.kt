package com.suhrud.docsy.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.suhrud.docsy.data.local.DocumentDao
import com.suhrud.docsy.data.model.DocumentCategories
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.data.model.DocumentFieldEntity
import com.suhrud.docsy.domain.ai.DocumentClassifier
import com.suhrud.docsy.domain.ai.TextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

class DocumentRepository(
    private val documentDao: DocumentDao
) {
    private val TAG = "DocsyRepository"

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _indexingProgress = MutableStateFlow("")
    val indexingProgress: StateFlow<String> = _indexingProgress.asStateFlow()

    private var contentObserverRegistered = false

    suspend fun syncDeviceFiles(context: Context) = withContext(Dispatchers.IO) {
        if (_isIndexing.value) return@withContext
        _isIndexing.value = true
        _indexingProgress.value = "Scanning phone storage..."

        try {
            scanMediaStoreFiles(context)
            scanMediaStoreImages(context)
            scanMediaStoreAudio(context)
            scanMediaStoreVideo(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                scanDirectDirectories(context)
            }

            // Re-extraction pass for documents with empty extractedText
            runReextractionPass(context)

            // Reconciliation check: log Room counts vs MediaStore counts
            runReconciliationCheck(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error during storage scan: ${e.message}")
        } finally {
            _isIndexing.value = false
            _indexingProgress.value = ""
        }
    }

    private suspend fun runReextractionPass(context: Context) = withContext(Dispatchers.IO) {
        val unextracted = documentDao.getAllDocumentsDirect().filter { !it.isDirectory && it.extractedText.isBlank() }
        for (doc in unextracted) {
            val uri = Uri.parse(doc.pathUri)
            val text = TextExtractor.extractText(context, uri, doc.mimeType, doc.extension)
            if (text.isNotBlank()) {
                val fields = DocumentClassifier.classifyAndExtract(doc.fileName, text)
                val updated = doc.copy(
                    extractedText = text,
                    documentType = fields.category,
                    lastIndexedTime = System.currentTimeMillis()
                )
                documentDao.insertDocument(updated)
            }
        }
    }

    private suspend fun runReconciliationCheck(context: Context) = withContext(Dispatchers.IO) {
        val roomPhotos = documentDao.getPhotoCount()
        val roomVideos = documentDao.getVideoCount()
        val roomPdfs = documentDao.getPdfCount()
        val roomDocs = documentDao.getOfficeDocCount()

        Log.d(TAG, "[Reconciliation Check] Room Photos: $roomPhotos | Room Videos: $roomVideos | Room PDFs: $roomPdfs | Room Total Office Docs: $roomDocs")
    }

    private suspend fun scanMediaStoreFiles(context: Context) = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword"
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "file_$id"
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    processAndStoreFile(context, contentUri, name, mime, size, dateModified)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore files query error: ${e.message}")
        }
    }

    private suspend fun scanMediaStoreImages(context: Context) = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "image_$id"
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    processAndStoreFile(context, contentUri, name, mime, size, dateModified)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore images query error: ${e.message}")
        }
    }

    private suspend fun scanMediaStoreAudio(context: Context) = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "audio_$id"
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    processAndStoreFile(context, contentUri, name, mime, size, dateModified)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore audio query error: ${e.message}")
        }
    }

    private suspend fun scanMediaStoreVideo(context: Context) = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "video_$id"
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    processAndStoreFile(context, contentUri, name, mime, size, dateModified)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore video query error: ${e.message}")
        }
    }

    private suspend fun scanDirectDirectories(context: Context) = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        if (root != null && root.exists() && root.isDirectory) {
            root.walkTopDown().maxDepth(6).filter { !it.name.startsWith(".") }.forEach { file ->
                if (file.isDirectory) {
                    documentDao.insertDocument(
                        DocumentEntity(
                            id = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString(),
                            pathUri = file.absolutePath,
                            fileName = file.name,
                            isDirectory = true,
                            parentFolder = file.parent ?: "",
                            indexStatus = "INDEXED"
                        )
                    )
                } else if (file.isFile && file.length() > 0 && file.length() < 150 * 1024 * 1024) {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    val mime = when (ext) {
                        "pdf" -> "application/pdf"
                        "txt", "csv", "json", "xml", "log", "md" -> "text/plain"
                        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "mp3", "m4a", "wav", "flac", "aac", "ogg", "opus" -> "audio/$ext"
                        "mp4", "mkv", "mov", "avi", "3gp", "webm" -> "video/$ext"
                        else -> null
                    }
                    if (mime != null) {
                        val uri = Uri.fromFile(file)
                        processAndStoreFile(context, uri, file.name, mime, file.length(), file.lastModified())
                    }
                }
            }
        }
    }

    private suspend fun processAndStoreFile(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        modifiedDate: Long
    ) = withContext(Dispatchers.IO) {
        val existing = documentDao.getDocumentByPath(uri.toString())
        if (existing != null && existing.modifiedDate == modifiedDate && existing.fileSizeBytes == fileSizeBytes) {
            return@withContext
        }

        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        var text = ""
        var docCategory = DocumentCategories.GENERAL
        var durationMs: Long? = null
        var resolution: String? = null
        var artistName: String? = null
        var albumName: String? = null
        var mediaTitle: String? = null
        var status = "INDEXED"

        val folder = uri.path?.substringBeforeLast('/', "") ?: ""

        if (mimeType.startsWith("audio/") || ext in listOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus", "wma")) {
            docCategory = "Audio"
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.scheme == "content") {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(uri.path)
                }
                mediaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName.substringBeforeLast('.')
                artistName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                albumName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

                val durSec = (durationMs ?: 0L) / 1000
                val durFormatted = if (durSec >= 3600) {
                    String.format(Locale.ROOT, "%d:%02d:%02d", durSec / 3600, (durSec % 3600) / 60, durSec % 60)
                } else {
                    String.format(Locale.ROOT, "%d:%02d", durSec / 60, durSec % 60)
                }

                text = "Audio: $mediaTitle. Artist: $artistName. Album: $albumName. Duration: $durFormatted. Bitrate: ${bitrate ?: "standard"} bps. File: $fileName."
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt or unreadable audio file $fileName: ${e.message}")
                status = "CORRUPT"
                text = "Audio: $fileName (unreadable metadata)"
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } else if (mimeType.startsWith("video/") || ext in listOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "flv")) {
            docCategory = "Video"
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.scheme == "content") {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(uri.path)
                }
                mediaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName.substringBeforeLast('.')
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (width != null && height != null) {
                    resolution = "${width}x${height}"
                }
                val durSec = (durationMs ?: 0L) / 1000
                val durFormatted = if (durSec >= 3600) {
                    String.format(Locale.ROOT, "%d:%02d:%02d", durSec / 3600, (durSec % 3600) / 60, durSec % 60)
                } else {
                    String.format(Locale.ROOT, "%d:%02d", durSec / 60, durSec % 60)
                }

                text = "Video: $mediaTitle. Resolution: ${resolution ?: "Standard"}. Duration: $durFormatted. File: $fileName."
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt or unreadable video file $fileName: ${e.message}")
                status = "CORRUPT"
                text = "Video: $fileName (unreadable metadata)"
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } else {
            text = TextExtractor.extractText(context, uri, mimeType, ext)
        }

        val fields = DocumentClassifier.classifyAndExtract(fileName, text)
        if (docCategory == DocumentCategories.GENERAL) {
            docCategory = fields.category
        }

        val docId = existing?.id ?: UUID.randomUUID().toString()

        val structuredObj = JSONObject()
        structuredObj.put("category", docCategory)
        durationMs?.let { structuredObj.put("durationMs", it) }
        resolution?.let { structuredObj.put("resolution", it) }
        artistName?.let { structuredObj.put("artist", it) }
        albumName?.let { structuredObj.put("album", it) }
        mediaTitle?.let { structuredObj.put("title", it) }
        fields.billingMonth?.let { structuredObj.put("billingMonth", it) }
        fields.amountPayable?.let { structuredObj.put("amountPayable", it) }
        fields.dueDate?.let { structuredObj.put("dueDate", it) }

        val docEntity = DocumentEntity(
            id = docId,
            pathUri = uri.toString(),
            fileName = fileName,
            extension = ext,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            createdDate = modifiedDate,
            modifiedDate = modifiedDate,
            isDirectory = false,
            parentFolder = folder,
            extractedText = text,
            documentType = docCategory,
            detectedMonth = fields.billingMonth,
            detectedYear = fields.billingYear,
            detectedUtility = fields.utilityType,
            detectedIssuer = fields.issuerOrProvider,
            detectedIdType = if (fields.aadhaarNumberMasked != null) "Aadhaar" else if (fields.panNumber != null) "PAN" else null,
            indexStatus = status,
            lastIndexedTime = System.currentTimeMillis(),
            summary = text.take(120),
            structuredJson = structuredObj.toString()
        )

        documentDao.insertDocument(docEntity)

        documentDao.deleteFieldsForDocument(docId)
        val fieldEntities = DocumentClassifier.toFieldEntities(docId, fields).toMutableList()
        artistName?.let {
            fieldEntities.add(DocumentFieldEntity(documentId = docId, fieldKey = "artist", fieldValue = it))
        }
        durationMs?.let {
            fieldEntities.add(DocumentFieldEntity(documentId = docId, fieldKey = "duration_ms", fieldValue = it.toString()))
        }
        resolution?.let {
            fieldEntities.add(DocumentFieldEntity(documentId = docId, fieldKey = "resolution", fieldValue = it))
        }
        if (fieldEntities.isNotEmpty()) {
            documentDao.insertFields(fieldEntities)
        }
    }

    suspend fun importUserDocument(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val text = TextExtractor.extractText(context, uri, mimeType, ext)
        val fields = DocumentClassifier.classifyAndExtract(fileName, text)

        val docId = UUID.randomUUID().toString()
        val docEntity = DocumentEntity(
            id = docId,
            pathUri = uri.toString(),
            fileName = fileName,
            extension = ext,
            mimeType = mimeType,
            fileSizeBytes = 0L,
            createdDate = System.currentTimeMillis(),
            modifiedDate = System.currentTimeMillis(),
            isDirectory = false,
            parentFolder = "Imported",
            extractedText = text,
            documentType = fields.category,
            detectedMonth = fields.billingMonth,
            detectedYear = fields.billingYear,
            detectedUtility = fields.utilityType,
            detectedIssuer = fields.issuerOrProvider,
            detectedIdType = if (fields.aadhaarNumberMasked != null) "Aadhaar" else if (fields.panNumber != null) "PAN" else null,
            indexStatus = "INDEXED",
            lastIndexedTime = System.currentTimeMillis(),
            summary = fields.category,
            structuredJson = DocumentClassifier.toJson(fields)
        )

        documentDao.insertDocument(docEntity)
        val fieldEntities = DocumentClassifier.toFieldEntities(docId, fields)
        if (fieldEntities.isNotEmpty()) {
            documentDao.insertFields(fieldEntities)
        }

        docEntity
    }

    fun registerMediaStoreObserver(context: Context) {
        if (contentObserverRegistered) return
        contentObserverRegistered = true

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    syncDeviceFiles(context)
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register MediaStore content observer: ${e.message}")
        }
    }

    suspend fun clearAllDocuments() = withContext(Dispatchers.IO) {
        documentDao.deleteAllDocuments()
    }
}
