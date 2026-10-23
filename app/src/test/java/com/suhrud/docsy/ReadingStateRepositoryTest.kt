package com.suhrud.docsy

import com.suhrud.docsy.scanner.FileCategory
import com.suhrud.docsy.scanner.ReadingProgress
import com.suhrud.docsy.scanner.ReadingStateRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStateRepositoryTest {

    @Test
    fun testProcessedCountsSumEqualsN() {
        ReadingStateRepository.reset()
        val totalFiles = 100
        ReadingStateRepository.startReading(totalFiles)

        val sampleFiles = listOf(
            "doc.pdf" to "application/pdf",
            "sheet.xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image.png" to "image/png",
            "clip.mp4" to "video/mp4",
            "song.mp3" to "audio/mpeg",
            "contact.vcf" to "text/vcard",
            "unknown.bin" to "application/octet-stream"
        )

        for (i in 1..totalFiles) {
            val (file, mime) = sampleFiles[(i - 1) % sampleFiles.size]
            val ext = file.substringAfterLast('.')
            val category = FileCategory.fromExtension(ext)

            ReadingStateRepository.recordProcessedFile(
                category = category,
                fileName = file,
                fileIndex = i,
                totalFilesSoFar = totalFiles
            )

            val state: ReadingProgress = ReadingStateRepository.readingState.value
            val chipSum = state.documentsCount + state.photosCount + state.videosCount +
                    state.musicCount + state.messagesCount + state.otherCount

            assertEquals("Sum of chips must equal processed file count N at step $i", i, chipSum)
            assertEquals("Processed files count must equal N", i, state.processedFiles)
        }
    }
}
