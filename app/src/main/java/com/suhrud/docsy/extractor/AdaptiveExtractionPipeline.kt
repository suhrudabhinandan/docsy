package com.suhrud.docsy.extractor

import com.suhrud.docsy.extractor.api.IDocumentExtractor
import com.suhrud.docsy.extractor.models.ExtractionMethod
import java.io.File

data class AdaptiveExtractionResult(
    val extractionResult: ExtractionResult,
    val primaryMethod: ExtractionMethod,
    val isOcrFallbackApplied: Boolean,
    val layoutAnalysis: List<PageLayoutAnalysis> = emptyList(),
    val warnings: List<String> = emptyList()
)

class AdaptiveExtractionPipeline(
    private val pdfExtractor: PdfExtractor = PdfExtractor(LocalOcrEngine()),
    private val ocrEngine: LocalOcrEngine = LocalOcrEngine()
) : IDocumentExtractor {

    override fun supportsExtension(extension: String): Boolean {
        return extension.lowercase() in listOf("pdf", "docx", "xlsx", "pptx", "png", "jpg", "jpeg", "txt", "csv")
    }

    override suspend fun extract(file: File): ExtractionResult {
        return processAdaptive(file).extractionResult
    }

    suspend fun processAdaptive(file: File): AdaptiveExtractionResult {
        val extension = file.extension.lowercase()
        val warnings = mutableListOf<String>()

        return when (extension) {
            "pdf" -> {
                val pdfResult = pdfExtractor.extract(file)
                val layoutList = pdfResult.layoutPages.map {
                    LayoutDetector.detectPageLayout(it, it.pageNumber)
                }
                val method = if (pdfResult.isFallbackOcrUsed) ExtractionMethod.OCR_FALLBACK else ExtractionMethod.NATIVE_PDF

                if (pdfResult.fullText.isBlank()) {
                    warnings.add("PDF extracted text was empty or unreadable")
                }

                AdaptiveExtractionResult(
                    extractionResult = pdfResult,
                    primaryMethod = method,
                    isOcrFallbackApplied = pdfResult.isFallbackOcrUsed,
                    layoutAnalysis = layoutList,
                    warnings = warnings
                )
            }
            "docx", "xlsx", "pptx" -> {
                val officeResult = OfficeXmlExtractor.extract(file)
                val method = if (extension == "docx") ExtractionMethod.DOCX_PARSER else ExtractionMethod.XLSX_PARSER

                AdaptiveExtractionResult(
                    extractionResult = officeResult,
                    primaryMethod = method,
                    isOcrFallbackApplied = false,
                    warnings = warnings
                )
            }
            "png", "jpg", "jpeg" -> {
                val imgExtractor = ImageExtractor(ocrEngine)
                val imgResult = imgExtractor.extract(file)

                AdaptiveExtractionResult(
                    extractionResult = imgResult,
                    primaryMethod = ExtractionMethod.IMAGE_OCR,
                    isOcrFallbackApplied = true,
                    warnings = warnings
                )
            }
            else -> {
                val plainResult = PlainTextExtractor.extract(file)
                AdaptiveExtractionResult(
                    extractionResult = plainResult,
                    primaryMethod = ExtractionMethod.PLAIN_TEXT,
                    isOcrFallbackApplied = false,
                    warnings = warnings
                )
            }
        }
    }
}
