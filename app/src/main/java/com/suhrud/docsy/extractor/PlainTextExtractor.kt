package com.suhrud.docsy.extractor

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object PlainTextExtractor {

    private val SUPPORTED_ENCODINGS = listOf(
        StandardCharsets.UTF_8,
        StandardCharsets.UTF_16,
        StandardCharsets.ISO_8859_1,
        Charset.forName("windows-1252")
    )

    fun extract(file: File): ExtractionResult {
        val startTime = System.currentTimeMillis()
        if (!file.exists() || !file.canRead()) {
            return ExtractionResult(
                contentKey = file.absolutePath,
                extension = file.extension,
                pageCount = 0,
                fullText = "",
                contentHash = "",
                durationMs = System.currentTimeMillis() - startTime,
                failureReason = "File not accessible"
            )
        }

        val bytes = file.readBytes()
        val hash = sha256(bytes)
        val decodedText = decodeText(bytes)
        val normalized = TextNormalizer.normalize(decodedText)

        val page = ExtractedPage(
            pageNumber = 1,
            text = normalized,
            confidence = 1.0f
        )

        return ExtractionResult(
            contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}",
            extension = file.extension.lowercase(),
            pageCount = 1,
            fullText = normalized,
            pages = listOf(page),
            contentHash = hash,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    private fun decodeText(bytes: ByteArray): String {
        for (charset in SUPPORTED_ENCODINGS) {
            try {
                val decoder = charset.newDecoder()
                return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) {
                // Try next encoding
            }
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    /** Streaming hash: never loads the whole file into memory (large PDFs and videos used to be read fully). */
    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
