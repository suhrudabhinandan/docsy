package com.suhrud.docsy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.suhrud.docsy.extractor.ImagePreprocessor
import com.suhrud.docsy.extractor.OfficeXmlExtractor
import com.suhrud.docsy.extractor.PlainTextExtractor
import com.suhrud.docsy.extractor.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class Phase5FormatConversionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTextNormalizerAndNfcCleaning() {
        // Hyphenated line wrap removal
        val inputHyphen = "Please submit the docu-\nment and certifi-\ncate."
        val normalizedHyphen = TextNormalizer.normalize(inputHyphen)
        assertEquals("Please submit the document and certificate.", normalizedHyphen)

        // Multiple spaces and excess line breaks
        val inputMessy = "Name:   Rahul   Kumar  \n\n\n\nMarks:  95.5%"
        val normalizedMessy = TextNormalizer.normalize(inputMessy)
        assertEquals("Name: Rahul Kumar\n\nMarks: 95.5%", normalizedMessy)

        // OCR numeric repair
        val repairedOcr = TextNormalizer.repairNumericCandidate("1234O678")
        assertEquals("12340678", repairedOcr)

        val repairedAdmitCard = TextNormalizer.repairNumericCandidate("98765432I")
        assertEquals("987654321", repairedAdmitCard)
    }

    @Test
    fun testPlainTextExtractorWithEncodings() {
        val testFileUtf8 = tempFolder.newFile("sample_notes.txt").apply {
            writeText("Docsy notes: offline personal assistant for your files.")
        }

        val result = PlainTextExtractor.extract(testFileUtf8)
        assertEquals("txt", result.extension)
        assertEquals(1, result.pageCount)
        assertTrue(result.fullText.contains("Docsy notes: offline personal assistant"))
        assertNotNull(result.contentHash)
        assertTrue(result.contentHash.length == 64) // SHA-256 length
    }

    @Test
    fun testOfficeDocxExtractorFromSyntheticZip() {
        val docxFile = tempFolder.newFile("marksheet.docx")
        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val xmlContent = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        <w:p><w:r><w:t>Central Board of Secondary Education</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Roll No: 12654321</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Result: PASS with 92%</w:t></w:r></w:p>
                    </w:body>
                </w:document>
            """.trimIndent()
            zos.write(xmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = OfficeXmlExtractor.extract(docxFile)
        assertEquals("docx", result.extension)
        assertTrue(result.fullText.contains("Central Board of Secondary Education"))
        assertTrue(result.fullText.contains("Roll No: 12654321"))
        assertTrue(result.fullText.contains("Result: PASS with 92%"))
    }

    @Test
    fun testOfficeXlsxExtractorWithSharedStrings() {
        val xlsxFile = tempFolder.newFile("expenses.xlsx")
        ZipOutputStream(FileOutputStream(xlsxFile)).use { zos ->
            // Shared strings table
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val sst = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <si><t>Electricity Bill</t></si>
                    <si><t>Internet Bill</t></si>
                </sst>
            """.trimIndent()
            zos.write(sst.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Sheet 1
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheet = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>0</v></c>
                            <c r="B1"><v>1450</v></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="s"><v>1</v></c>
                            <c r="B2"><v>999</v></c>
                        </row>
                    </sheetData>
                </worksheet>
            """.trimIndent()
            zos.write(sheet.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = OfficeXmlExtractor.extract(xlsxFile)
        assertEquals("xlsx", result.extension)
        assertTrue(result.fullText.contains("Electricity Bill"))
        assertTrue(result.fullText.contains("1450"))
        assertTrue(result.fullText.contains("Internet Bill"))
        assertTrue(result.fullText.contains("999"))
    }

    @Test
    fun testImagePreprocessorTextCandidateHeuristic() {
        // Blank bitmap (zero contrast, no text)
        val blankBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        assertFalse(ImagePreprocessor.hasTextCandidates(blankBitmap))

        // High contrast bitmap with text-like strokes
        val textBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        for (i in 20..180 step 15) {
            for (x in 10..190) {
                textBitmap.setPixel(x, i, Color.BLACK)
                textBitmap.setPixel(x, i + 1, Color.BLACK)
            }
        }
        assertTrue(ImagePreprocessor.hasTextCandidates(textBitmap))

        // Enhanced contrast bitmap
        val enhanced = ImagePreprocessor.enhanceForOcr(textBitmap)
        assertNotNull(enhanced)
        assertEquals(200, enhanced.width)
        assertEquals(200, enhanced.height)
    }
}
