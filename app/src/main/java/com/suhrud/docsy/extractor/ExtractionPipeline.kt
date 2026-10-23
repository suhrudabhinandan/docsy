package com.suhrud.docsy.extractor

import android.content.Context
import com.suhrud.docsy.scanner.ContentSectionDetector
import com.suhrud.docsy.scanner.FileSection
import java.io.File

class ExtractionPipeline(
    private val context: Context? = null,
    private val ocrEngine: LocalOcrEngine = LocalOcrEngine()
) {

    private val imageExtractor = ImageExtractor(ocrEngine)
    private val pdfExtractor = PdfExtractor(ocrEngine)

    suspend fun extractFile(file: File): ExtractionResult {
        val section = ContentSectionDetector.detectSection(file)

        return when (section) {
            FileSection.PDF -> {
                pdfExtractor.extract(file)
            }
            FileSection.WORD -> {
                if (file.extension.equals("docx", true) || file.extension.equals("dotx", true)) {
                    OfficeXmlExtractor.extract(file)
                } else {
                    PlainTextExtractor.extract(file)
                }
            }
            FileSection.EXCEL -> {
                if (file.extension.equals("xlsx", true) || file.extension.equals("xlsm", true)) {
                    OfficeXmlExtractor.extract(file)
                } else {
                    PlainTextExtractor.extract(file)
                }
            }
            FileSection.IMAGES -> {
                imageExtractor.extract(file)
            }
            FileSection.VIDEOS -> {
                MediaMetadataExtractor.extract(file, isVideo = true)
            }
            FileSection.AUDIOS -> {
                MediaMetadataExtractor.extract(file, isVideo = false)
            }
            FileSection.ARCHIVES -> {
                val tempDir = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                ArchiveExtractor.extractArchiveInfoAndChildren(file, tempDir) { child ->
                    // Child cleanup handled after indexing
                    try { if (child.tempFile.exists()) child.tempFile.delete() } catch (_: Exception) {}
                }
            }
            FileSection.APPS -> {
                if (context != null) {
                    AppPackageExtractor.extract(context, file)
                } else {
                    PlainTextExtractor.extract(file)
                }
            }
            FileSection.CODE_WEB -> {
                CodeWebExtractor.extract(file)
            }
            FileSection.SMS -> {
                PlainTextExtractor.extract(file)
            }
            FileSection.OTHER -> {
                ExtractionResult(
                    contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}",
                    extension = file.extension,
                    pageCount = 0,
                    fullText = "",
                    contentHash = "",
                    durationMs = 0L,
                    failureReason = "Unrecognized or other file section"
                )
            }
        }
    }

    fun close() {
        ocrEngine.close()
    }
}
