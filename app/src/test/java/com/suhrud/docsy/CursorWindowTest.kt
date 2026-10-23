package com.suhrud.docsy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CursorWindowTest {

    private lateinit var db: DocsyDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DocsyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testLargeDocumentListQueryDoesNotThrowCursorWindowException() = runBlocking {
        val documentDao = db.documentDao()

        // Create 50 documents, one of which contains a massive 5 MB extractedText string
        val hugeText = "A".repeat(5 * 1024 * 1024)

        val docs = (1..50).map { i ->
            DocumentEntity(
                path = "/storage/emulated/0/Documents/file_$i.pdf",
                name = "file_$i.pdf",
                ext = "pdf",
                mime = "application/pdf",
                size = 1024L * i,
                modified = System.currentTimeMillis() - i * 1000L,
                state = DocumentStatus.INDEXED.name,
                extractedText = if (i == 50) hugeText else "Short text for file $i"
            )
        }

        documentDao.insertDocuments(docs)

        // 1. Test getAllDocumentsSnapshot()
        val snapshotList = documentDao.getAllDocumentsSnapshot()
        assertEquals(50, snapshotList.size)

        // 2. Test getAllDocuments() Flow
        val flowList = documentDao.getAllDocuments().first()
        assertEquals(50, flowList.size)

        // 3. Test getDocumentsByStatus()
        val statusList = documentDao.getDocumentsByStatus(DocumentStatus.INDEXED)
        assertEquals(50, statusList.size)

        // 4. Test min/max queries
        val largest = documentDao.getLargestDocument()
        assertNotNull(largest)
        val smallest = documentDao.getSmallestDocument()
        assertNotNull(smallest)
        val newest = documentDao.getNewestDocument()
        assertNotNull(newest)
        val oldest = documentDao.getOldestDocument()
        assertNotNull(oldest)

        // 5. Test path / key lookup
        val byPath = documentDao.getDocumentByPath("/storage/emulated/0/Documents/file_50.pdf")
        assertNotNull(byPath)
    }
}
