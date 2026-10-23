package com.suhrud.docsy.scanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

data class ReadingProgress(
    val isReading: Boolean = false,
    val isDiscoveryFinished: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val documentsIndexed: Int = 0,
    val photosIndexed: Int = 0,
    val videosIndexed: Int = 0,
    val musicIndexed: Int = 0,
    val messagesIndexed: Int = 0,
    val otherIndexed: Int = 0,
    val mediaIndexed: Int = 0,
    val errorsCount: Int = 0,
    val stageDescription: String = "",
    val estimatedSecondsRemaining: Int = 0
) {
    val isFinished: Boolean
        get() = !isReading && (isDiscoveryFinished || currentFileIndex >= totalFiles)
    val processedFiles: Int
        get() = currentFileIndex
    val documentsCount: Int
        get() = documentsIndexed
    val photosCount: Int
        get() = photosIndexed
    val videosCount: Int
        get() = videosIndexed
    val musicCount: Int
        get() = musicIndexed
    val messagesCount: Int
        get() = messagesIndexed
    val otherCount: Int
        get() = otherIndexed

    val progressFraction: Float
        get() = if (totalFiles > 0) (currentFileIndex.coerceAtMost(totalFiles).toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Singleton repository holding real-time reading progress and status across the application.
 */
object ReadingStateRepository {

    private val _readingState = MutableStateFlow(ReadingProgress())
    val readingState: StateFlow<ReadingProgress> = _readingState.asStateFlow()

    fun startReading(totalFiles: Int = 0) {
        _readingState.update {
            ReadingProgress(
                isReading = true,
                isDiscoveryFinished = false,
                currentFileIndex = 0,
                totalFiles = max(1, totalFiles),
                stageDescription = "Still finding files...",
                estimatedSecondsRemaining = max(1, totalFiles / 5)
            )
        }
    }

    fun setDiscoveryFinished(finalTotal: Int) {
        _readingState.update { current ->
            current.copy(
                isDiscoveryFinished = true,
                totalFiles = max(current.totalFiles, finalTotal)
            )
        }
    }

    fun recordProcessedFile(
        category: FileCategory,
        fileName: String,
        fileIndex: Int,
        totalFilesSoFar: Int
    ) {
        _readingState.update { current ->
            val updatedTotal = max(current.totalFiles, totalFilesSoFar)
            val updatedIndex = max(current.currentFileIndex, fileIndex).coerceAtMost(updatedTotal)
            val remainingFiles = max(0, updatedTotal - updatedIndex)
            val estSeconds = if (remainingFiles > 0) max(1, remainingFiles / 4) else 0

            var docs = current.documentsIndexed
            var photos = current.photosIndexed
            var videos = current.videosIndexed
            var music = current.musicIndexed
            var messages = current.messagesIndexed
            var other = current.otherIndexed

            when (category) {
                FileSection.PDF, FileSection.WORD, FileSection.EXCEL -> docs++
                FileSection.IMAGES -> photos++
                FileSection.VIDEOS -> videos++
                FileSection.AUDIOS -> music++
                FileSection.SMS -> messages++
                FileSection.APPS, FileSection.ARCHIVES, FileSection.CODE_WEB, FileSection.OTHER -> other++
            }

            current.copy(
                isReading = true,
                totalFiles = updatedTotal,
                currentFileIndex = updatedIndex,
                currentFileName = fileName,
                documentsIndexed = docs,
                photosIndexed = photos,
                videosIndexed = videos,
                musicIndexed = music,
                messagesIndexed = messages,
                otherIndexed = other,
                stageDescription = "Reading files...",
                estimatedSecondsRemaining = estSeconds
            )
        }
    }

    fun updateProgress(
        fileIndex: Int,
        totalFilesSoFar: Int = fileIndex,
        fileName: String,
        documentsIndexed: Int,
        photosIndexed: Int = 0,
        videosIndexed: Int = 0,
        musicIndexed: Int = 0,
        messagesIndexed: Int = 0,
        otherIndexed: Int = 0,
        mediaIndexed: Int = 0,
        errorsCount: Int = 0,
        stage: String = "Reading files..."
    ) {
        _readingState.update { current ->
            val calculatedIndex = max(fileIndex, documentsIndexed + mediaIndexed)
            val updatedTotal = max(current.totalFiles, max(totalFilesSoFar, calculatedIndex))
            val updatedIndex = max(current.currentFileIndex, calculatedIndex).coerceAtMost(updatedTotal)
            val remainingFiles = max(0, updatedTotal - updatedIndex)
            val estSeconds = if (remainingFiles > 0) max(1, remainingFiles / 4) else 0

            current.copy(
                isReading = true,
                totalFiles = updatedTotal,
                currentFileIndex = updatedIndex,
                currentFileName = fileName,
                documentsIndexed = documentsIndexed,
                photosIndexed = photosIndexed,
                videosIndexed = videosIndexed,
                musicIndexed = musicIndexed,
                messagesIndexed = messagesIndexed,
                otherIndexed = otherIndexed,
                mediaIndexed = mediaIndexed,
                errorsCount = errorsCount,
                stageDescription = stage,
                estimatedSecondsRemaining = estSeconds
            )
        }
    }

    fun finishReading(totalIndexed: Int, errors: Int) {
        _readingState.update { current ->
            val finalTotal = max(max(current.totalFiles, current.currentFileIndex), totalIndexed + errors)
            current.copy(
                isReading = false,
                isDiscoveryFinished = true,
                currentFileIndex = finalTotal, // Strictly 100% complete
                totalFiles = finalTotal,
                documentsIndexed = if (totalIndexed > 0) totalIndexed else current.documentsIndexed,
                errorsCount = errors,
                stageDescription = "Reading complete (100%)",
                estimatedSecondsRemaining = 0
            )
        }
    }

    fun reset() {
        _readingState.value = ReadingProgress()
    }
}
