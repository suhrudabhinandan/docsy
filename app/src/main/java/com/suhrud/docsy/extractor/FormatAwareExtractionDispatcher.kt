package com.suhrud.docsy.extractor

import com.suhrud.docsy.extractor.api.IDocumentExtractor
import com.suhrud.docsy.extractor.models.ExtractionMethod
import com.suhrud.docsy.scanner.FileFilter
import java.io.File

data class DispatcherExtractionResult(
    val extractionResult: ExtractionResult,
    val formatCategory: String,
    val isOcrExecuted: Boolean,
    val isNativeExtractionUsed: Boolean
)

object FormatAwareExtractionDispatcher : IDocumentExtractor {

    private val pdfExtractor = PdfExtractor(LocalOcrEngine())
    private val localOcrEngine = LocalOcrEngine()
    private val imageExtractor = ImageExtractor(localOcrEngine)

    override fun supportsExtension(extension: String): Boolean {
        return FileFilter.isSupportedExtension(extension)
    }

    override suspend fun extract(file: File): ExtractionResult {
        return dispatchExtraction(file).extractionResult
    }

    suspend fun dispatchExtraction(file: File): DispatcherExtractionResult {
        val ext = FileFilter.getExtension(file.name)

        return when {
            ext == "pdf" -> {
                val pdfResult = pdfExtractor.extract(file)
                DispatcherExtractionResult(
                    extractionResult = pdfResult,
                    formatCategory = "PDF",
                    isOcrExecuted = pdfResult.isFallbackOcrUsed,
                    isNativeExtractionUsed = !pdfResult.isFallbackOcrUsed
                )
            }
            FileFilter.isOfficeDocument(ext) -> {
                val officeResult = OfficeXmlExtractor.extract(file)
                DispatcherExtractionResult(
                    extractionResult = officeResult,
                    formatCategory = "OFFICE",
                    isOcrExecuted = false,
                    isNativeExtractionUsed = true
                )
            }
            FileFilter.isTextOrStructuredData(ext) || FileFilter.isOpenDocument(ext) -> {
                val plainResult = PlainTextExtractor.extract(file)
                DispatcherExtractionResult(
                    extractionResult = plainResult,
                    formatCategory = "TEXT_STRUCTURED",
                    isOcrExecuted = false,
                    isNativeExtractionUsed = true
                )
            }
            FileFilter.isImageDocument(ext) -> {
                val imgResult = imageExtractor.extract(file)
                DispatcherExtractionResult(
                    extractionResult = imgResult,
                    formatCategory = "IMAGE",
                    isOcrExecuted = true,
                    isNativeExtractionUsed = false
                )
            }
            else -> {
                val fallbackResult = PlainTextExtractor.extract(file)
                DispatcherExtractionResult(
                    extractionResult = fallbackResult,
                    formatCategory = "UNKNOWN",
                    isOcrExecuted = false,
                    isNativeExtractionUsed = true
                )
            }
        }
    }
}
