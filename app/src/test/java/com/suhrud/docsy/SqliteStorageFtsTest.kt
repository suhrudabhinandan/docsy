package com.suhrud.docsy

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.ChunkEntity
import com.suhrud.docsy.data.db.entity.DocJsonEntity
import com.suhrud.docsy.data.db.entity.DocLayoutEntity
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.retrieval.Fts5SearchEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SqliteStorageFtsTest {

    private lateinit var db: DocsyDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DocsyDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(sqliteDb: SupportSQLiteDatabase) {
                    super.onOpen(sqliteDb)
                    DocsyDatabase.initFts5Tables(sqliteDb)
                }
            })
            .build()
        // Ensure FTS5 tables initialized
        db.openHelper.writableDatabase.apply {
            DocsyDatabase.initFts5Tables(this)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFts5ProbeResult() {
        val sqliteDb = db.openHelper.writableDatabase
        val probeResult = Fts5SearchEngine.probeFts5(sqliteDb)
        if (probeResult) {
            assertTrue("FTS5 probe passed", probeResult)
            assertNull("lastProbeError should be null when probe passes", Fts5SearchEngine.lastProbeError)
        } else {
            assertNotNull("lastProbeError should be populated when FTS5 is unsupported", Fts5SearchEngine.lastProbeError)
        }
    }

    @Test
    fun testFtsRowCountEqualsChunkRowCountOnInsertsUpdatesDeletes() = runBlocking {
        org.junit.Assume.assumeTrue(Fts5SearchEngine.isFts5Supported)
        val docDao = db.documentDao()
        val chunkDao = db.chunkDao()
        val docId = docDao.insertDocument(
            DocumentEntity(
                path = "/sdcard/test.pdf",
                name = "test.pdf",
                ext = "pdf",
                mime = "application/pdf",
                size = 1024L,
                modified = System.currentTimeMillis()
            )
        )

        // Insert chunks
        chunkDao.insertChunks(
            listOf(
                ChunkEntity(documentId = docId, page = 1, position = 0, kind = "HEADER", text = "Tax Invoice 2026"),
                ChunkEntity(documentId = docId, page = 1, position = 1, kind = "PARAGRAPH", text = "Total Amount Due: $500"),
                ChunkEntity(documentId = docId, page = 2, position = 0, kind = "LINE", text = "Thank you for your business")
            )
        )

        val chunkCountAfterInsert = chunkDao.getChunkCount()
        val ftsCountAfterInsert = getFtsRowCount()
        assertEquals(3, chunkCountAfterInsert)
        assertEquals(chunkCountAfterInsert.toLong(), ftsCountAfterInsert)

        // Update chunk
        val chunks = chunkDao.getChunksByDocumentId(docId)
        val targetChunk = chunks.first()
        db.openHelper.writableDatabase.execSQL(
            "UPDATE chunks SET text = ? WHERE id = ?",
            arrayOf("Updated Tax Invoice Header", targetChunk.id)
        )

        val chunkCountAfterUpdate = chunkDao.getChunkCount()
        val ftsCountAfterUpdate = getFtsRowCount()
        assertEquals(3, chunkCountAfterUpdate)
        assertEquals(chunkCountAfterUpdate.toLong(), ftsCountAfterUpdate)

        // Delete chunk
        db.openHelper.writableDatabase.execSQL("DELETE FROM chunks WHERE id = ?", arrayOf(targetChunk.id))
        val chunkCountAfterDelete = chunkDao.getChunkCount()
        val ftsCountAfterDelete = getFtsRowCount()
        assertEquals(2, chunkCountAfterDelete)
        assertEquals(chunkCountAfterDelete.toLong(), ftsCountAfterDelete)
    }

    @Test
    fun testAtomicReExtraction() = runBlocking {
        org.junit.Assume.assumeTrue(Fts5SearchEngine.isFts5Supported)
        val docDao = db.documentDao()
        val chunkDao = db.chunkDao()
        val factDao = db.factDao()
        val jsonDao = db.docJsonDao()

        val docId = docDao.insertDocument(
            DocumentEntity(
                path = "/sdcard/invoice.pdf",
                name = "invoice.pdf",
                ext = "pdf",
                mime = "application/pdf",
                size = 2048L,
                modified = System.currentTimeMillis()
            )
        )

        // Initial extraction write
        db.runInTransaction {
            runBlocking {
                chunkDao.insertChunks(
                    listOf(
                        ChunkEntity(documentId = docId, page = 1, position = 0, kind = "PARAGRAPH", text = "Old Version Content")
                    )
                )
                factDao.insertFact(
                    FactEntity(documentId = docId, key = "total", value = "100")
                )
                jsonDao.insertDocJson(DocJsonEntity(docId, "{\"version\": 1}"))
            }
        }

        assertEquals(1, chunkDao.getChunkCount())
        assertEquals(1L, getFtsRowCount())

        // Re-extraction replaces all rows of that document atomically
        db.runInTransaction {
            runBlocking {
                chunkDao.deleteChunksByDocumentId(docId)
                factDao.deleteFactsForDocument(docId)
                jsonDao.deleteDocJson(docId)

                chunkDao.insertChunks(
                    listOf(
                        ChunkEntity(documentId = docId, page = 1, position = 0, kind = "HEADER", text = "New Version Header"),
                        ChunkEntity(documentId = docId, page = 1, position = 1, kind = "PARAGRAPH", text = "New Version Details")
                    )
                )
                factDao.insertFact(FactEntity(documentId = docId, key = "total", value = "250"))
                jsonDao.insertDocJson(DocJsonEntity(docId, "{\"version\": 2}"))
            }
        }

        val chunks = chunkDao.getChunksByDocumentId(docId)
        assertEquals(2, chunks.size)
        assertEquals(2L, getFtsRowCount())

        val facts = factDao.getFactsForDocument(docId)
        assertEquals(1, facts.size)
        assertEquals("250", facts[0].value)

        val docJson = jsonDao.getDocJson(docId)
        assertNotNull(docJson)
        assertEquals("{\"version\": 2}", docJson!!.json)
    }

    @Test
    fun testDeletionCleanup() = runBlocking {
        org.junit.Assume.assumeTrue(Fts5SearchEngine.isFts5Supported)
        val docDao = db.documentDao()
        val chunkDao = db.chunkDao()
        val factDao = db.factDao()
        val jsonDao = db.docJsonDao()
        val layoutDao = db.docLayoutDao()

        val docId = docDao.insertDocument(
            DocumentEntity(
                path = "/sdcard/delete_me.pdf",
                name = "delete_me.pdf",
                ext = "pdf",
                mime = "application/pdf",
                size = 512L,
                modified = System.currentTimeMillis()
            )
        )

        chunkDao.insertChunks(listOf(ChunkEntity(documentId = docId, page = 1, position = 0, kind = "LINE", text = "Delete me text")))
        factDao.insertFact(FactEntity(documentId = docId, key = "status", value = "temporary"))
        jsonDao.insertDocJson(DocJsonEntity(docId, "{\"temp\": true}"))
        layoutDao.insertDocLayout(DocLayoutEntity(docId, 1, byteArrayOf(1, 2, 3)))

        assertEquals(1, chunkDao.getChunkCount())
        assertEquals(1L, getFtsRowCount())

        // Delete document and all associated entries
        db.runInTransaction {
            runBlocking {
                docDao.deleteDocumentById(docId)
                chunkDao.deleteChunksByDocumentId(docId)
                factDao.deleteFactsForDocument(docId)
                jsonDao.deleteDocJson(docId)
                layoutDao.deleteDocLayout(docId)
            }
        }

        assertNull(docDao.getDocumentById(docId))
        assertEquals(0, chunkDao.getChunkCount())
        assertEquals(0L, getFtsRowCount())
        assertTrue(factDao.getFactsForDocument(docId).isEmpty())
        assertNull(jsonDao.getDocJson(docId))
        assertNull(layoutDao.getDocLayout(docId, 1))
    }

    private fun getFtsRowCount(): Long {
        return try {
            val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM chunks_fts")
            cursor.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
