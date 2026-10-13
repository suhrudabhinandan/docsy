package com.suhrud.docsy.scanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

data class ReadingProgress(
    val isReading: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val documentsIndexed: Int = 0,
    val mediaIndexed: Int = 0,
    val errorsCount: Int = 0,
    val stageDescription: String = "",
    val estimatedSecondsRemaining: Int = 0
) {
    val progressFraction: Float
        get() = if (totalFiles > 0) (currentFileIndex.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Singleton repository holding real-time reading progress and status across the application.
 * Satisfies Item 6.2 and 6.1 in AUDIT.md.
 */
object ReadingStateRepository {

    private val _readingState = MutableStateFlow(ReadingProgress())
    val readingState: StateFlow<ReadingProgress> = _readingState.asStateFlow()

    fun startReading(totalFiles: Int) {
        _readingState.update {
            ReadingProgress(
                isReading = true,
                currentFileIndex = 0,
                totalFiles = totalFiles,
                stageDescription = "Discovering and reading documents...",
                estimatedSecondsRemaining = max(1, totalFiles / 5)
            )
        }
    }

    fun updateProgress(
        fileIndex: Int,
        fileName: String,
        documentsIndexed: Int,
        mediaIndexed: Int,
        errorsCount: Int,
        stage: String
    ) {
        _readingState.update { current ->
            // Monotonic progress guarantee (item 6.1)
            val updatedIndex = max(current.currentFileIndex, fileIndex)
            val remainingFiles = max(0, current.totalFiles - updatedIndex)
            val estSeconds = if (remainingFiles > 0) max(1, remainingFiles / 4) else 0

            current.copy(
                isReading = true,
                currentFileIndex = updatedIndex,
                currentFileName = fileName,
                documentsIndexed = documentsIndexed,
                mediaIndexed = mediaIndexed,
                errorsCount = errorsCount,
                stageDescription = stage,
                estimatedSecondsRemaining = estSeconds
            )
        }
    }

    fun finishReading(totalIndexed: Int, errors: Int) {
        _readingState.update { current ->
            current.copy(
                isReading = false,
                currentFileIndex = current.totalFiles,
                documentsIndexed = totalIndexed,
                errorsCount = errors,
                stageDescription = "Reading complete",
                estimatedSecondsRemaining = 0
            )
        }
    }

    fun reset() {
        _readingState.value = ReadingProgress()
    }
}
