package com.suhrud.docsy.extractor

import com.suhrud.docsy.scanner.FileFilter
import java.io.File

class ExtractionPipeline(
    private val ocrEngine: LocalOcrEngine = LocalOcrEngine()
) {

    private val imageExtractor = ImageExtractor(ocrEngine)
    private val pdfExtractor = PdfExtractor(ocrEngine)

    suspend fun extractFile(file: File): ExtractionResult {
        val extension = FileFilter.getExtension(file.name)

        return when {
            extension == "pdf" -> {
                pdfExtractor.extract(file)
            }
            extension in listOf("docx", "xlsx", "pptx") -> {
                OfficeXmlExtractor.extract(file)
            }
            FileFilter.isImage(extension) -> {
                imageExtractor.extract(file)
            }
            FileFilter.isDocument(extension) -> {
                // txt, csv, rtf, etc.
                PlainTextExtractor.extract(file)
            }
            else -> {
                ExtractionResult(
                    contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}",
                    extension = extension,
                    pageCount = 0,
                    fullText = "",
                    contentHash = "",
                    durationMs = 0L,
                    failureReason = "Unsupported format: $extension"
                )
            }
        }
    }

    fun close() {
        ocrEngine.close()
    }
}
