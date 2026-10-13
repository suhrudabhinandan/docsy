package com.suhrud.docsy

import com.suhrud.docsy.extractor.ExtractionBenchmark
import com.suhrud.docsy.extractor.ExtractionResult
import com.suhrud.docsy.extractor.ExtractionState
import com.suhrud.docsy.extractor.ExtractionStateMachine
import com.suhrud.docsy.scanner.ReadingStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase6ReadingProgressTest {

    @Before
    fun setUp() {
        ReadingStateRepository.reset()
        ExtractionBenchmark.reset()
    }

    @Test
    fun testReadingStateRepositoryMonotonicity() {
        // Start reading with 100 total files
        ReadingStateRepository.startReading(totalFiles = 100)
        var state = ReadingStateRepository.readingState.value
        assertTrue(state.isReading)
        assertEquals(100, state.totalFiles)
        assertEquals(0, state.currentFileIndex)
        assertEquals(0f, state.progressFraction, 0.001f)

        // Progress update to 40
        ReadingStateRepository.updateProgress(
            fileIndex = 40,
            fileName = "12th_marksheet.pdf",
            documentsIndexed = 30,
            mediaIndexed = 10,
            errorsCount = 0,
            stage = "Reading PDF documents..."
        )
        state = ReadingStateRepository.readingState.value
        assertEquals(40, state.currentFileIndex)
        assertEquals(0.40f, state.progressFraction, 0.001f)
        assertEquals("12th_marksheet.pdf", state.currentFileName)

        // Monotonic check: passing an older index (30) does NOT decrement progress
        ReadingStateRepository.updateProgress(
            fileIndex = 30,
            fileName = "older_doc.txt",
            documentsIndexed = 30,
            mediaIndexed = 10,
            errorsCount = 0,
            stage = "Reading text documents..."
        )
        state = ReadingStateRepository.readingState.value
        assertEquals(40, state.currentFileIndex)
        assertEquals(0.40f, state.progressFraction, 0.001f)

        // Finish reading
        ReadingStateRepository.finishReading(totalIndexed = 95, errors = 5)
        state = ReadingStateRepository.readingState.value
        assertFalse(state.isReading)
        assertEquals(100, state.currentFileIndex)
        assertEquals(95, state.documentsIndexed)
        assertEquals(5, state.errorsCount)
        assertEquals(1.0f, state.progressFraction, 0.001f)
    }

    @Test
    fun testExtractionStateMachineTransitions() {
        // Encrypted PDF
        val encryptedResult = ExtractionResult(
            contentKey = "key1",
            extension = "pdf",
            pageCount = 0,
            fullText = "",
            contentHash = "hash1",
            durationMs = 12L,
            isEncrypted = true,
            failureReason = "Password-protected"
        )
        assertEquals(ExtractionState.ENCRYPTED, ExtractionStateMachine.determineState(encryptedResult))

        // Unsupported format
        val unsupportedResult = ExtractionResult(
            contentKey = "key2",
            extension = "exe",
            pageCount = 0,
            fullText = "",
            contentHash = "hash2",
            durationMs = 2L,
            failureReason = "Unsupported format: exe"
        )
        assertEquals(ExtractionState.UNSUPPORTED, ExtractionStateMachine.determineState(unsupportedResult))

        // Empty file
        val emptyResult = ExtractionResult(
            contentKey = "key3",
            extension = "txt",
            pageCount = 1,
            fullText = "    \n",
            contentHash = "hash3",
            durationMs = 5L
        )
        assertEquals(ExtractionState.EMPTY, ExtractionStateMachine.determineState(emptyResult))

        // Clean extracted file
        val validResult = ExtractionResult(
            contentKey = "key4",
            extension = "pdf",
            pageCount = 2,
            fullText = "Roll No: 123456 Marks: 95%",
            contentHash = "hash4",
            durationMs = 250L
        )
        assertEquals(ExtractionState.EXTRACTED, ExtractionStateMachine.determineState(validResult))
    }

    @Test
    fun testExtractionBenchmarkThroughput() {
        ExtractionBenchmark.recordExtraction("pdf", 100L)
        ExtractionBenchmark.recordExtraction("pdf", 150L)
        ExtractionBenchmark.recordExtraction("txt", 25L)

        val pdfStats = ExtractionBenchmark.getStatsForExtension("pdf")
        assertEquals(2, pdfStats.fileCount)
        assertEquals(250L, pdfStats.totalTimeMs)
        assertEquals(125.0, pdfStats.avgTimeMs, 0.01)

        val txtStats = ExtractionBenchmark.getStatsForExtension("txt")
        assertEquals(1, txtStats.fileCount)
        assertEquals(25L, txtStats.totalTimeMs)

        assertTrue(ExtractionBenchmark.getTotalThroughputFilesPerSec() > 0.0)
    }
}
