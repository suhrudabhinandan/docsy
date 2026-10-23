package com.suhrud.docsy

import com.suhrud.docsy.extractor.CodeWebExtractor
import com.suhrud.docsy.scanner.ContentSectionDetector
import com.suhrud.docsy.scanner.FileSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SectionClassifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // 1. Classification by magic bytes for each section
    @Test
    fun testMagicBytesClassification() {
        // PDF: %PDF-
        val pdfFile = tempFolder.newFile("test_doc.pdf")
        pdfFile.writeBytes("%PDF-1.7\nSample PDF Header".toByteArray())
        assertEquals(FileSection.PDF, ContentSectionDetector.detectSection(pdfFile))

        // PNG Image: 0x89 PNG
        val pngFile = tempFolder.newFile("sample_image.png")
        pngFile.writeBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        assertEquals(FileSection.IMAGES, ContentSectionDetector.detectSection(pngFile))

        // JPEG Image: 0xFF 0xD8 0xFF
        val jpgFile = tempFolder.newFile("sample_image.jpg")
        jpgFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        assertEquals(FileSection.IMAGES, ContentSectionDetector.detectSection(jpgFile))

        // MP3 Audio: ID3
        val mp3File = tempFolder.newFile("song.mp3")
        mp3File.writeBytes("ID3v4SampleAudioData".toByteArray())
        assertEquals(FileSection.AUDIOS, ContentSectionDetector.detectSection(mp3File))

        // FLAC Audio: fLaC
        val flacFile = tempFolder.newFile("track.flac")
        flacFile.writeBytes("fLaCSampleAudioHeader".toByteArray())
        assertEquals(FileSection.AUDIOS, ContentSectionDetector.detectSection(flacFile))

        // MP4 Video: ftyp
        val mp4File = tempFolder.newFile("clip.mp4")
        mp4File.writeBytes(byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte()))
        assertEquals(FileSection.VIDEOS, ContentSectionDetector.detectSection(mp4File))

        // 7z Archive: 7z\xBC\xAF\x27\x1C
        val szFile = tempFolder.newFile("archive.7z")
        szFile.writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))
        assertEquals(FileSection.ARCHIVES, ContentSectionDetector.detectSection(szFile))
    }

    // 2. Extension/content mismatch case (file named doc.pdf that is actually a PNG image)
    @Test
    fun testExtensionContentMismatch() {
        val mismatchedFile = tempFolder.newFile("doc.pdf") // Misleading extension
        mismatchedFile.writeBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))

        val detectedSection = ContentSectionDetector.detectSection(mismatchedFile)
        assertEquals("Mismatched file must be detected as IMAGES by magic bytes", FileSection.IMAGES, detectedSection)
    }

    // 3. ZIP containing a PDF and an image -> ARCHIVES section
    @Test
    fun testZipContainingPdfAndImage() {
        val zipFile = tempFolder.newFile("package.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("document.pdf"))
            zos.write("%PDF-1.5 Content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("photo.png"))
            zos.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
            zos.closeEntry()
        }

        val detectedSection = ContentSectionDetector.detectSection(zipFile)
        assertEquals("Generic zip containing pdf and image must route to ARCHIVES", FileSection.ARCHIVES, detectedSection)
    }

    // 4. Data .json (tabular records) vs Code .json
    @Test
    fun testDataJsonVsCodeJson() {
        val dataJsonFile = tempFolder.newFile("records.json")
        dataJsonFile.writeText("""
            [
                {"id": 1, "name": "Alpha", "amount": 100},
                {"id": 2, "name": "Beta", "amount": 250}
            ]
        """.trimIndent())
        val dataSection = ContentSectionDetector.detectSection(dataJsonFile)
        assertEquals("Tabular structured JSON must route to EXCEL", FileSection.EXCEL, dataSection)

        val codeJsonFile = tempFolder.newFile("config.json")
        codeJsonFile.writeText("""
            {
                "package": "com.example.app",
                "version": 1.0,
                "scripts": {"build": "webpack --mode production"}
            }
        """.trimIndent())
        val codeSection = ContentSectionDetector.detectSection(codeJsonFile)
        assertEquals("Configuration/Code JSON must route to CODE_WEB", FileSection.CODE_WEB, codeSection)
    }

    // 5. Minified JS skipped
    @Test
    fun testMinifiedJsSkipped() {
        val minifiedFile = tempFolder.newFile("bundle.min.js")
        assertTrue("File named .min.js must be skipped", CodeWebExtractor.isSkippedPathOrName(minifiedFile))

        val longLineText = "var a=1;" + "function foo(){return 42;}".repeat(100)
        assertTrue("Content with very long average line length must be identified as minified", CodeWebExtractor.isMinifiedContent(longLineText))
    }

    // 6. Saved web page extracting visible text
    @Test
    fun testSavedWebPageExtraction() {
        val htmlContent = """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>My Saved Invoice Web Page</title>
                    <style> body { font-family: sans-serif; } </style>
                    <script> console.log('hello'); </script>
                </head>
                <body>
                    <h1>Payment Invoice</h1>
                    <p>Total Paid: $150.00</p>
                    <div class="footer">Thank you for your business.</div>
                </body>
            </html>
        """.trimIndent()

        val extracted = CodeWebExtractor.extractHtmlText(htmlContent)

        assertTrue("Extracted text must contain Title", extracted.contains("Title: My Saved Invoice Web Page"))
        assertTrue("Extracted text must contain Heading", extracted.contains("Payment Invoice"))
        assertTrue("Extracted text must contain paragraph text", extracted.contains("Total Paid: $150.00"))
        assertFalse("Extracted text must NOT contain script blocks", extracted.contains("console.log"))
        assertFalse("Extracted text must NOT contain CSS blocks", extracted.contains("font-family"))
    }

    // 7. Counts matching between home chips and library screen
    @Test
    fun testCountsMatchingConstantSections() {
        val sections = FileSection.SECTIONS_9
        assertEquals("There must be exactly 9 primary file sections", 9, sections.size)
        assertTrue("PDF must be in sections", sections.contains(FileSection.PDF))
        assertTrue("CODE_WEB must be in sections", sections.contains(FileSection.CODE_WEB))
    }
}
