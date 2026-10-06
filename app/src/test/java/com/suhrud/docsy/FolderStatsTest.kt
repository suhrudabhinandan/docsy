package com.suhrud.docsy

import com.suhrud.docsy.data.local.DocumentDao
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import com.suhrud.docsy.domain.stats.FolderStatsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderStatsTest {

    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var folderStatsEngine: FolderStatsEngine

    class FakeDocumentDao(private val docs: MutableList<DocumentEntity> = mutableListOf()) : DocumentDao {
        fun setDocs(newDocs: List<DocumentEntity>) {
            docs.clear()
            docs.addAll(newDocs)
        }
        override fun getAllDocuments(): Flow<List<DocumentEntity>> = flowOf(docs)
        override suspend fun getAllDocumentsDirect(): List<DocumentEntity> = docs
        override suspend fun getDocumentById(id: String): DocumentEntity? = docs.find { it.id == id }
        override suspend fun getDocumentByPath(pathUri: String): DocumentEntity? = docs.find { it.pathUri == pathUri }
        override suspend fun getDocumentsByType(documentType: String): List<DocumentEntity> = docs.filter { it.documentType == documentType }
        override suspend fun getDocumentsByTypeAndMonth(documentType: String, month: String): List<DocumentEntity> = emptyList()
        override suspend fun getDocumentsByTypeAndYear(documentType: String, year: Int): List<DocumentEntity> = emptyList()
        override suspend fun getDocumentsByTypeMonthYear(documentType: String, month: String, year: Int): List<DocumentEntity> = emptyList()
        override suspend fun getDocumentsByUtility(utility: String): List<DocumentEntity> = emptyList()
        override suspend fun getDocumentsByUtilityAndMonth(utility: String, month: String): List<DocumentEntity> = emptyList()
        override suspend fun searchFts(query: String): List<DocumentEntity> = emptyList()
        override suspend fun searchKeyword(query: String): List<DocumentEntity> = emptyList()
        override suspend fun insertDocument(document: DocumentEntity) { docs.add(document) }
        override suspend fun insertDocuments(documents: List<DocumentEntity>) { docs.addAll(documents) }
        override suspend fun deleteDocument(id: String) { docs.removeIf { it.id == id } }
        override suspend fun deleteDocumentByPath(pathUri: String) { docs.removeIf { it.pathUri == pathUri } }
        override suspend fun deleteAllDocuments() { docs.clear() }
        override suspend fun insertFields(fields: List<com.suhrud.docsy.data.model.DocumentFieldEntity>) {}
        override suspend fun getFieldsForDocument(documentId: String): List<com.suhrud.docsy.data.model.DocumentFieldEntity> = emptyList()
        override suspend fun getFieldsByKey(fieldKey: String): List<com.suhrud.docsy.data.model.DocumentFieldEntity> = emptyList()
        override suspend fun getFieldsByKeyAndDocType(fieldKey: String, docType: String): List<com.suhrud.docsy.data.model.DocumentFieldEntity> = emptyList()
        override suspend fun deleteFieldsForDocument(documentId: String) {}
        override suspend fun getDocumentCount(): Int = docs.size
        override suspend fun getDocumentCountByType(docType: String): Int = docs.count { it.documentType == docType }
        override suspend fun getPhotoCount(): Int = docs.count { it.mimeType.startsWith("image/") }
        override suspend fun getVideoCount(): Int = docs.count { it.mimeType.startsWith("video/") }
        override suspend fun getAudioCount(): Int = docs.count { it.mimeType.startsWith("audio/") }
        override suspend fun getOfficeDocCount(): Int = docs.count { it.extension in listOf("pdf", "docx") }
        override suspend fun getPdfCount(): Int = docs.count { it.extension == "pdf" }
        override suspend fun getWordCount(): Int = docs.count { it.extension in listOf("doc", "docx") }
        override suspend fun getExcelCount(): Int = docs.count { it.extension in listOf("xls", "xlsx", "csv") }
        override suspend fun getPowerPointCount(): Int = docs.count { it.extension in listOf("ppt", "pptx") }
        override suspend fun getTextCount(): Int = docs.count { it.extension in listOf("txt", "log", "md") }
        override suspend fun getLargestFile(): DocumentEntity? = docs.maxByOrNull { it.fileSizeBytes }
        override suspend fun getSmallestNonZeroFile(): DocumentEntity? = docs.filter { it.fileSizeBytes > 0 }.minByOrNull { it.fileSizeBytes }
        override suspend fun getOldestFile(): DocumentEntity? = docs.minByOrNull { it.modifiedDate }
        override suspend fun getNewestFile(): DocumentEntity? = docs.maxByOrNull { it.modifiedDate }
        override suspend fun getLargestFileByPattern(mimePattern: String, extPattern: String): DocumentEntity? = null
        override suspend fun getNewestFileByPattern(mimePattern: String, extPattern: String): DocumentEntity? = null
        override suspend fun getOldestFileByPattern(mimePattern: String, extPattern: String): DocumentEntity? = null
    }

    @Before
    fun setup() {
        fakeDao = FakeDocumentDao()
        folderStatsEngine = FolderStatsEngine(fakeDao)
    }

    @Test
    fun testFolderQueryParser() {
        val q1 = QueryParser.parse("how many photos are in the Telegram folder")
        assertEquals(QueryIntent.FOLDER_STATS, q1.intent)
        assertEquals("telegram", q1.targetFolder?.lowercase())
        assertEquals("photo", q1.filterType)

        val q2 = QueryParser.parse("files in Downloads")
        assertEquals(QueryIntent.FOLDER_STATS, q2.intent)
        assertEquals("downloads", q2.targetFolder?.lowercase())
        assertEquals("all", q2.filterType)

        val q3 = QueryParser.parse("size of WhatsApp folder")
        assertEquals(QueryIntent.FOLDER_STATS, q3.intent)
        assertEquals("whatsapp", q3.targetFolder?.lowercase())
        assertEquals("size", q3.folderQueryType)

        val q4 = QueryParser.parse("largest file in Camera")
        assertEquals(QueryIntent.FOLDER_STATS, q4.intent)
        assertEquals("camera", q4.targetFolder?.lowercase())
        assertEquals("superlative", q4.folderQueryType)
        assertEquals("largest", q4.filterType?.lowercase())
    }

    @Test
    fun testFolderCandidateResolverAndCountAggregation() = runBlocking {
        val testFiles = listOf(
            DocumentEntity(
                id = "dir_tg",
                pathUri = "/storage/emulated/0/Telegram",
                fileName = "Telegram",
                isDirectory = true,
                parentFolder = "/storage/emulated/0"
            ),
            DocumentEntity(
                id = "f1",
                pathUri = "/storage/emulated/0/Telegram/photo1.jpg",
                fileName = "photo1.jpg",
                extension = "jpg",
                mimeType = "image/jpeg",
                fileSizeBytes = 2_000_000L,
                modifiedDate = 1000L,
                parentFolder = "/storage/emulated/0/Telegram"
            ),
            DocumentEntity(
                id = "dir_tg_img",
                pathUri = "/storage/emulated/0/Telegram/Telegram Images",
                fileName = "Telegram Images",
                isDirectory = true,
                parentFolder = "/storage/emulated/0/Telegram"
            ),
            DocumentEntity(
                id = "f2",
                pathUri = "/storage/emulated/0/Telegram/Telegram Images/photo2.jpg",
                fileName = "photo2.jpg",
                extension = "jpg",
                mimeType = "image/jpeg",
                fileSizeBytes = 3_000_000L,
                modifiedDate = 2000L,
                parentFolder = "/storage/emulated/0/Telegram/Telegram Images"
            ),
            DocumentEntity(
                id = "f3",
                pathUri = "/storage/emulated/0/Telegram/video1.mp4",
                fileName = "video1.mp4",
                extension = "mp4",
                mimeType = "video/mp4",
                fileSizeBytes = 15_000_000L,
                modifiedDate = 3000L,
                parentFolder = "/storage/emulated/0/Telegram"
            )
        )

        fakeDao.setDocs(testFiles)

        val candidates = folderStatsEngine.resolveFolderCandidates(testFiles, "Telegram")
        assertTrue("Should resolve Telegram folder", candidates.isNotEmpty())
        assertEquals("/storage/emulated/0/Telegram", candidates.first())

        val queryPhotos = QueryParser.parse("how many photos are in the Telegram folder")
        val ansPhotos = folderStatsEngine.answerFolderQuery(queryPhotos)
        assertEquals("2 Photos", ansPhotos.answerHighlight)
        assertTrue(ansPhotos.text.contains("1 directly in folder"))

        val querySize = QueryParser.parse("size of Telegram folder")
        val ansSize = folderStatsEngine.answerFolderQuery(querySize)
        assertTrue(ansSize.answerHighlight?.contains("MB") == true)

        val queryLargest = QueryParser.parse("largest file in Telegram")
        val ansLargest = folderStatsEngine.answerFolderQuery(queryLargest)
        assertEquals("video1.mp4", ansLargest.answerHighlight)
    }

    @Test
    fun testRestrictedAndroidDataFolderResponse() = runBlocking {
        val query = QueryParser.parse("files in Android/data")
        val ans = folderStatsEngine.answerFolderQuery(query)
        assertEquals("Access Restricted by Android", ans.answerHighlight)
        assertTrue(ans.text.contains("Android blocks"))
    }
}
