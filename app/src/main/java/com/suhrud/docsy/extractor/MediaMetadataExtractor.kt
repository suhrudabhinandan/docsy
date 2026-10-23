package com.suhrud.docsy.extractor

import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaMetadataExtractor {

    fun extract(file: File, isVideo: Boolean): ExtractionResult {
        val contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = file.extension,
                pageCount = 0,
                fullText = "",
                contentHash = contentKey,
                durationMs = 0L,
                failureReason = "Invalid media file"
            )
        }

        val sb = StringBuilder()
        val typeLabel = if (isVideo) "Video File" else "Audio File"
        sb.append(typeLabel).append(": ").append(file.name).append("\n")

        val retriever = MediaMetadataRetriever()
        var extractedDurationMs = 0L
        try {
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)

            if (!title.isNullOrBlank()) sb.append("Title: ").append(title).append("\n")
            if (!artist.isNullOrBlank()) sb.append("Artist: ").append(artist).append("\n")
            if (!album.isNullOrBlank()) sb.append("Album: ").append(album).append("\n")

            if (!durationMsStr.isNullOrBlank()) {
                val durationMs = durationMsStr.toLongOrNull() ?: 0L
                extractedDurationMs = durationMs
                val seconds = (durationMs / 1000) % 60
                val minutes = (durationMs / (1000 * 60)) % 60
                val hours = durationMs / (1000 * 60 * 60)
                val formattedDuration = if (hours > 0) {
                    String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
                }
                sb.append("Duration: ").append(formattedDuration).append("\n")
            }

            if (isVideo && !width.isNullOrBlank() && !height.isNullOrBlank()) {
                sb.append("Resolution: ").append(width).append("x").append(height).append("\n")
            }

            if (!dateStr.isNullOrBlank()) {
                sb.append("Date: ").append(dateStr).append("\n")
            }
        } catch (_: Exception) {
            sb.append("Metadata extraction fallback\n")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        sb.append("Size: ").append(file.length() / 1024).append(" KB\n")
        sb.append("Modified: ").append(sdf.format(Date(file.lastModified()))).append("\n")

        return ExtractionResult(
            contentKey = contentKey,
            extension = file.extension,
            pageCount = 1,
            fullText = sb.toString().trim(),
            contentHash = contentKey,
            durationMs = extractedDurationMs,
            isFallbackOcrUsed = false,
            failureReason = null
        )
    }
}
