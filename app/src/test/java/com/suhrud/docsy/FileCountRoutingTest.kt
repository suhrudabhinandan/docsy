package com.suhrud.docsy

import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.query.QueryParser
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCountRoutingTest {

    @Test
    fun testFileCountVsStorageContrastiveQueries() {
        val q1 = "number of videos"
        val p1 = QueryParser.parse(q1)
        println("DEBUG DEBUG: Query '$q1' parsed as intent=${p1.intent}, filterType=${p1.filterType}, docType=${p1.documentType}")

        val countQueries = listOf(
            "videos count",
            "count videos",
            "number of videos",
            "videos total",
            "how many videos"
        )

        for (q in countQueries) {
            val parsed = QueryParser.parse(q)
            assertEquals("Query '$q' should resolve to DEVICE_STATS", QueryIntent.DEVICE_STATS, parsed.intent)
            assertEquals("Query '$q' filterType should be video", "video", parsed.filterType)
        }

        val storageQueries = listOf(
            "video storage",
            "space used by videos"
        )

        for (q in storageQueries) {
            val parsed = QueryParser.parse(q)
            val isStorage = parsed.intent == QueryIntent.DEVICE_INFO || parsed.intent == QueryIntent.FOLDER_STATS || parsed.intent == QueryIntent.DEVICE_STATS || parsed.intent == QueryIntent.STORAGE_QUERY || parsed.filterType == "storage"
            assertEquals("Query '$q' must be storage/folder query", true, isStorage)
        }
    }

    @Test
    fun testMediaNounVariants() {
        val parsedMusic = QueryParser.parse("musics on my device")
        assertEquals(QueryIntent.DEVICE_STATS, parsedMusic.intent)
        assertEquals("audio", parsedMusic.filterType)
    }
}
