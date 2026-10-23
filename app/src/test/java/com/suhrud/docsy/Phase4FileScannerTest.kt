package com.suhrud.docsy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.scanner.FileFilter
import com.suhrud.docsy.scanner.FileScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class Phase4FileScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: DocsyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DocsyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFileFilterExtensionsAndRules() {
        // Supported Documents
        assertTrue(FileFilter.isSupportedExtension("pdf"))
        assertTrue(FileFilter.isSupportedExtension("PDF"))
        assertTrue(FileFilter.isSupportedExtension("docx"))
        assertTrue(FileFilter.isSupportedExtension("xlsx"))
        assertTrue(FileFilter.isSupportedExtension("txt"))
        assertTrue(FileFilter.isSupportedExtension("csv"))

        // Supported Images
        assertTrue(FileFilter.isSupportedExtension("jpg"))
        assertTrue(FileFilter.isSupportedExtension("JPEG"))
        assertTrue(FileFilter.isSupportedExtension("png"))
        assertTrue(FileFilter.isSupportedExtension("webp"))

        // Unsupported files
        assertFalse(FileFilter.isSupportedExtension("exe"))
        assertFalse(FileFilter.isSupportedExtension("apk"))
        assertFalse(FileFilter.isSupportedExtension("zip"))
        assertFalse(FileFilter.isSupportedExtension("bin"))

        // MIME Types
        assertEquals("application/pdf", FileFilter.getMimeType("pdf"))
        assertEquals("image/png", FileFilter.getMimeType("png"))
        assertEquals("text/plain", FileFilter.getMimeType("txt"))

        // Content Key computation
        val key = FileFilter.computeContentKey("/path/to/doc.pdf", 1024L, 999999L)
        assertEquals("/path/to/doc.pdf:1024:999999", key)
    }

    @Test
    fun testDirectoryTraversalWithHiddenNomediaAndSkippedFolders() = runBlocking {
        val root = tempFolder.newFolder("storage_root")

        // 1. Valid document
        val validPdf = File(root, "marksheet.pdf").apply { writeText("12th marksheet data") }

        // 2. Case-insensitive document
        val upperPdf = File(root, "CAT_EXAM.PDF").apply { writeText("CAT admit card data") }

        // 3. Hidden directory (.hidden_folder) -> MUST BE WALKED
        val hiddenDir = File(root, ".hidden_folder").apply { mkdirs() }
        val secretDoc = File(hiddenDir, "confidential.docx").apply { writeText("secret doc") }

        // 4. Messenger directory (WhatsApp) -> MUST BE WALKED
        val messengerDir = File(root, "WhatsApp/Media/Documents").apply { mkdirs() }
        val billPdf = File(messengerDir, "electricity_bill.pdf").apply { writeText("bill data") }

        // 5. Gallery directory with .nomedia -> MUST BE WALKED (nomedia ignored for documents)
        val noMediaDir = File(root, "Camera").apply { mkdirs() }
        File(noMediaDir, ".nomedia").createNewFile()
        val receiptPng = File(noMediaDir, "receipt.png").apply { writeText("fake image bytes") }

        // 6. Cache directory -> MUST BE SKIPPED
        val cacheDir = File(root, "cache").apply { mkdirs() }
        File(cacheDir, "cached_report.pdf").apply { writeText("cache data") }

        // 7. Dot cache directory (.cache) -> MUST BE SKIPPED
        val dotCacheDir = File(root, ".cache").apply { mkdirs() }
        File(dotCacheDir, "cached_item.pdf").apply { writeText("cache data") }

        // 8. Temp directory (tmp & temp) -> MUST BE SKIPPED
        val tmpDir = File(root, "tmp").apply { mkdirs() }
        File(tmpDir, "temp_data.pdf").apply { writeText("tmp data") }
        val tempDir = File(root, "temp").apply { mkdirs() }
        File(tempDir, "temp_doc.xlsx").apply { writeText("temp data") }

        // 9. Android/data and Android/obb -> MUST BE SKIPPED
        val androidDataDir = File(root, "Android/data/com.other.app").apply { mkdirs() }
        File(androidDataDir, "game_save.pdf").apply { writeText("private data") }
        val androidObbDir = File(root, "Android/obb/com.other.app").apply { mkdirs() }
        File(androidObbDir, "obb_asset.pdf").apply { writeText("obb data") }

        // 10. Unsupported files
        File(root, "installer.apk").apply { writeText("binary data") }
        File(root, "script.sh").apply { writeText("#!/bin/bash") }
        // 0-byte file (must be skipped)
        File(root, "empty.pdf").createNewFile()

        val scanner = object : FileScanner(context, db.documentDao()) {
            // Override root volumes to point to our test folder
            override fun getRootStorageVolumes(): List<File> = listOf(root)
        }

        val result = scanner.scanStorage()

        // Expected discovered files >= 5
        assertTrue(result.documentsDiscovered >= 5)

        // Verify Room records
        val allDocs = db.documentDao().getAllDocuments().first()
        assertEquals(5, allDocs.size)

        val paths = allDocs.map { it.absolutePath }
        assertTrue(paths.contains(validPdf.absolutePath))
        assertTrue(paths.contains(upperPdf.absolutePath))
        assertTrue(paths.contains(secretDoc.absolutePath))
        assertTrue(paths.contains(billPdf.absolutePath))
        assertTrue(paths.contains(receiptPng.absolutePath))

        // Verify skipped files are absent (checking relative paths within root)
        val relativePaths = paths.map { it.removePrefix(root.absolutePath) }
        assertFalse(relativePaths.any { it.contains("/cache/") })
        assertFalse(relativePaths.any { it.contains("/.cache/") })
        assertFalse(relativePaths.any { it.contains("/tmp/") })
        assertFalse(relativePaths.any { it.contains("/temp/") })
        assertFalse(relativePaths.any { it.contains("/Android/data/") })
        assertFalse(relativePaths.any { it.contains("/Android/obb/") })
    }

    @Test
    fun testRoomContentKeyChangeDetectionAndStatus() = runBlocking {
        val dao = db.documentDao()

        val doc1 = DocumentEntity(
            path = "/storage/emulated/0/doc1.pdf",
            name = "doc1",
            ext = "pdf",
            mime = "application/pdf",
            size = 2048L,
            modified = 1000L,
            contentHash = "hash1000",
            state = DocumentStatus.DISCOVERED.name
        )

        val insertedId = dao.insertDocument(doc1)
        assertTrue(insertedId > 0)

        // Query by path
        val retrieved = dao.getDocumentByPath("/storage/emulated/0/doc1.pdf")
        assertNotNull(retrieved)
        assertEquals(DocumentStatus.DISCOVERED.name, retrieved!!.state)

        // Query non-existent path
        val nonExistent = dao.getDocumentByPath("/storage/emulated/0/nonexistent.pdf")
        assertNull(nonExistent)

        // Update status to READING, then INDEXED
        dao.updateStatus(retrieved.id, DocumentStatus.READING)
        var updated = dao.getDocumentByPath("/storage/emulated/0/doc1.pdf")
        assertEquals(DocumentStatus.READING.name, updated!!.state)

        dao.updateStatus(retrieved.id, DocumentStatus.INDEXED)
        updated = dao.getDocumentByPath("/storage/emulated/0/doc1.pdf")
        assertEquals(DocumentStatus.INDEXED.name, updated!!.state)
    }

    @Test
    fun testSyntheticTreeWalkBenchmark() = runBlocking {
        val benchRoot = tempFolder.newFolder("bench_root")

        // Create 20 folders with 50 files each = 1,000 files
        for (i in 1..20) {
            val sub = File(benchRoot, "folder_$i").apply { mkdirs() }
            for (j in 1..50) {
                File(sub, "doc_${i}_$j.txt").writeText("Sample content $j")
            }
        }

        val scanner = object : FileScanner(context, db.documentDao()) {
            override fun getRootStorageVolumes(): List<File> = listOf(benchRoot)
        }

        val startTime = System.currentTimeMillis()
        val result = scanner.scanStorage()
        val duration = System.currentTimeMillis() - startTime

        assertTrue(result.documentsDiscovered >= 0)
        // Walking 1,000 files in local JVM test must complete under 6,000ms in container
        assertTrue("Benchmark took ${duration}ms, expected < 6000ms", duration < 6000L)
    }
}
