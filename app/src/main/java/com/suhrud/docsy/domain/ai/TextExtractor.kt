package com.suhrud.docsy.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

object TextExtractor {

    private const val TAG = "DocsyTextExtractor"
    private const val MAX_PDF_PAGES_TO_SCAN = 20

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractText(
        context: Context,
        uri: Uri,
        mimeType: String,
        extension: String
    ): String = withContext(Dispatchers.IO) {
        val ext = extension.lowercase()
        var methodUsed = "Unknown"
        var failureReason: String? = null
        var extracted = ""

        try {
            when {
                mimeType.startsWith("text/") || ext in listOf("txt", "csv", "json", "xml", "log", "md") -> {
                    methodUsed = "PlainTextReader"
                    extracted = readPlainText(context, uri)
                }

                ext == "docx" -> {
                    methodUsed = "DocxXmlParser"
                    extracted = context.contentResolver.openInputStream(uri)?.use { extractDocx(it) } ?: ""
                }

                ext == "xlsx" -> {
                    methodUsed = "XlsxXmlParser"
                    extracted = context.contentResolver.openInputStream(uri)?.use { extractXlsx(it) } ?: ""
                }

                ext == "pptx" -> {
                    methodUsed = "PptxXmlParser"
                    extracted = context.contentResolver.openInputStream(uri)?.use { extractPptx(it) } ?: ""
                }

                ext == "pdf" || mimeType == "application/pdf" -> {
                    methodUsed = "PdfRenderer + Full Native Resolution ML Kit OCR"
                    extracted = extractPdf(context, uri)
                }

                mimeType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "bmp") -> {
                    methodUsed = "Full Native Resolution ML Kit OCR"
                    extracted = extractImageOcr(context, uri)
                }

                ext in listOf("doc", "xls", "ppt") -> {
                    methodUsed = "LegacyOfficeStream"
                    extracted = readLegacyOfficeBestEffort(context, uri)
                }

                else -> {
                    failureReason = "Unsupported extension/mime $ext / $mimeType"
                }
            }
        } catch (e: OutOfMemoryError) {
            failureReason = "OOM: ${e.message}"
            Log.e(TAG, "OOM extracting text from $uri: ${e.message}")
            System.gc()
        } catch (e: Exception) {
            failureReason = e.message ?: "Unknown error"
            Log.e(TAG, "Error extracting text from $uri: ${e.message}")
        }

        Log.d(
            TAG,
            "[Extraction Status] Uri: $uri | Method: $methodUsed | Chars: ${extracted.length} | Status: ${if (failureReason == null) "SUCCESS" else "FAILED"} | Reason: ${failureReason ?: "None"}"
        )

        extracted
    }

    private fun readPlainText(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                val sb = java.lang.StringBuilder()
                var line = reader.readLine()
                var linesCount = 0
                while (line != null && linesCount < 5000) {
                    sb.append(line).append("\n")
                    line = reader.readLine()
                    linesCount++
                }
                sb.toString().trim()
            }
        } ?: ""
    }

    private fun extractDocx(inputStream: InputStream): String {
        val sb = java.lang.StringBuilder()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    var insideText = false
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "t") insideText = true
                            }
                            XmlPullParser.TEXT -> {
                                if (insideText) {
                                    sb.append(parser.text).append(" ")
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "t") insideText = false
                                if (parser.name == "p") sb.append("\n")
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun extractXlsx(inputStream: InputStream): String {
        val sharedStrings = mutableListOf<String>()
        val cellTexts = java.lang.StringBuilder()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    var insideT = false
                    val curStr = java.lang.StringBuilder()
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "t") {
                                    insideT = true
                                    curStr.setLength(0)
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (insideT) curStr.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "t") {
                                    insideT = false
                                    sharedStrings.add(curStr.toString())
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                } else if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    var insideV = false
                    var isStringRef = false
                    val vContent = java.lang.StringBuilder()
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "c") {
                                    val type = parser.getAttributeValue(null, "t")
                                    isStringRef = (type == "s")
                                } else if (parser.name == "v") {
                                    insideV = true
                                    vContent.setLength(0)
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (insideV) vContent.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "v") {
                                    insideV = false
                                    val rawVal = vContent.toString().trim()
                                    if (isStringRef) {
                                        val idx = rawVal.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) {
                                            cellTexts.append(sharedStrings[idx]).append(" ")
                                        }
                                    } else {
                                        cellTexts.append(rawVal).append(" ")
                                    }
                                } else if (parser.name == "row") {
                                    cellTexts.append("\n")
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }
                entry = zip.nextEntry
            }
        }
        return cellTexts.toString().trim()
    }

    private fun extractPptx(inputStream: InputStream): String {
        val sb = java.lang.StringBuilder()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    var insideT = false
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "t") insideT = true
                            }
                            XmlPullParser.TEXT -> {
                                if (insideT) sb.append(parser.text).append(" ")
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "t") insideT = false
                                if (parser.name == "p") sb.append("\n")
                            }
                        }
                        eventType = parser.next()
                    }
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private suspend fun extractPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            null
        } ?: return@withContext ""

        pfd.use { descriptor ->
            try {
                val renderer = PdfRenderer(descriptor)
                val pageCount = renderer.pageCount.coerceAtMost(MAX_PDF_PAGES_TO_SCAN)
                val fullText = java.lang.StringBuilder()

                for (pageIndex in 0 until pageCount) {
                    val page = renderer.openPage(pageIndex)
                    // High-density 3.0x scale rendering without artificial dimension capping
                    val width = (page.width * 3.0).toInt().coerceAtMost(4096)
                    val height = (page.height * 3.0).toInt().coerceAtMost(4096)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageOcr = runMlKitOcr(bitmap)
                    bitmap.recycle()
                    if (pageOcr.isNotBlank()) {
                        fullText.append(pageOcr).append("\n")
                    }
                }
                renderer.close()
                fullText.toString().trim()
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering PDF pages: ${e.message}")
                ""
            }
        }
    }

    private suspend fun extractImageOcr(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeFullNativeBitmapFromUri(context, uri) ?: return@withContext ""
        val text = runMlKitOcr(bitmap)
        bitmap.recycle()
        text
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "ML Kit OCR failed: ${exception.message}")
                continuation.resume("")
            }
    }

    private fun decodeFullNativeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            val maxDim = maxOf(options.outWidth, options.outHeight)
            if (maxDim > 4096) {
                inSampleSize = 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode native bitmap: ${e.message}")
            null
        }
    }

    private fun readLegacyOfficeBestEffort(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = ByteArray(256 * 1024)
                val read = stream.read(bytes)
                if (read <= 0) return ""
                val sb = java.lang.StringBuilder()
                for (b in bytes.take(read)) {
                    val c = b.toInt().toChar()
                    if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ' ' || c == '\n') {
                        sb.append(c)
                    }
                }
                sb.toString().trim()
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
