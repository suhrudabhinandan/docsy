package com.suhrud.docsy

import com.suhrud.docsy.extractor.ExtractionQualityAssessment
import com.suhrud.docsy.extractor.OfficeXmlExtractor
import com.suhrud.docsy.extractor.models.ExtractionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class Phase1ExtractionTest {

    @Test
    fun testQualityAssessmentEvaluator() {
        val goodText = "Docsy offline document intelligence system processes PDF marksheets and XLSX spreadsheets on device."
        val metrics = ExtractionQualityAssessment.evaluate(
            text = goodText,
            pageCount = 1,
            extractionMethod = ExtractionMethod.NATIVE_PDF
        )

        assertTrue(metrics.isSuccess)
        assertFalse(metrics.isOcrRequired)
        assertFalse(metrics.isPartial)
        assertTrue(metrics.qualityScore > 0.5f)
        assertEquals(ExtractionMethod.NATIVE_PDF, metrics.extractionMethod)
    }

    @Test
    fun testQualityAssessmentTriggersOcrFallbackOnLowDensity() {
        val lowDensityText = "A 1" // Only 2 meaningful chars
        val needsOcr = ExtractionQualityAssessment.needsOcrFallback(lowDensityText)
        assertTrue("Page with low text density must trigger OCR fallback", needsOcr)

        val metrics = ExtractionQualityAssessment.evaluate(
            text = lowDensityText,
            pageCount = 1,
            extractionMethod = ExtractionMethod.NATIVE_PDF
        )
        assertTrue(metrics.isOcrRequired)
        assertTrue(metrics.isPartial)
    }

    @Test
    fun testDocxStructuredParsing() {
        val docxFile = createSyntheticDocxFile()
        try {
            java.util.zip.ZipFile(docxFile).use { zip ->
                val result = OfficeXmlExtractor.extractDocxDetailed(docxFile.absolutePath, zip)

                assertTrue("Docx text should contain header", result.fullText.contains("Docsy Confidential Header"))
                assertTrue("Docx text should contain footer", result.fullText.contains("Page 1 of 1"))
                assertTrue("Docx text should contain main paragraph", result.fullText.contains("Student Performance Report"))

                // Verify structured paragraph extraction
                assertTrue("Paragraphs list must not be empty", result.paragraphs.isNotEmpty())
                val headingP = result.paragraphs.firstOrNull { it.isHeading }
                assertNotNull("Heading paragraph must be detected", headingP)
                assertEquals(1, headingP?.headingLevel)

                // Verify structured table extraction
                assertTrue("Tables list must not be empty", result.tables.isNotEmpty())
                val table = result.tables.first()
                assertTrue("Table must contain rows", table.rows.isNotEmpty())
                val headerRow = table.rows.first()
                assertTrue("First row must be header", headerRow.isHeader)
                assertEquals("Subject", headerRow.cells.first().rawValue)

                // Verify embedded media discovery
                assertEquals(1, result.mediaItems.size)
                assertEquals("sample_logo.png", result.mediaItems.first().mediaName)

                // Verify provenance info
                assertNotNull(result.paragraphs.first().provenance)
                assertEquals(ExtractionMethod.DOCX_PARSER, result.paragraphs.first().provenance?.extractionMethod)
                assertEquals(docxFile.absolutePath, result.paragraphs.first().provenance?.sourcePath)
            }
        } finally {
            docxFile.delete()
        }
    }

    @Test
    fun testXlsxStructuredParsing() {
        val xlsxFile = createSyntheticXlsxFile()
        try {
            java.util.zip.ZipFile(xlsxFile).use { zip ->
                val result = OfficeXmlExtractor.extractXlsxDetailed(xlsxFile.absolutePath, zip)

                assertEquals(2, result.worksheets.size)
                assertEquals("Marksheet", result.worksheets[0].sheetName)
                assertEquals("Summary", result.worksheets[1].sheetName)

                val marksheet = result.worksheets[0]
                assertTrue("Worksheet must contain cells", marksheet.cells.isNotEmpty())

                // Verify cell coordinates and values
                val cellA1 = marksheet.cells.firstOrNull { it.coordinate == "A1" }
                assertNotNull("Cell A1 must exist", cellA1)
                assertEquals("Student Name", cellA1?.rawValue)

                // Verify formula cell parsing
                val cellB2 = marksheet.cells.firstOrNull { it.coordinate == "B2" }
                assertNotNull("Formula cell B2 must exist", cellB2)
                assertEquals("A2*10", cellB2?.formula)
                assertEquals("FORMULA", cellB2?.dataType)

                // Verify merged cell detection
                val mergedCell = marksheet.cells.firstOrNull { it.isMerged }
                assertNotNull("Merged cell must be flagged", mergedCell)

                // Verify provenance info
                assertEquals("Marksheet", cellA1?.provenance?.worksheetName)
                assertEquals("A1", cellA1?.provenance?.cellCoordinate)
                assertEquals(ExtractionMethod.XLSX_PARSER, cellA1?.provenance?.extractionMethod)
            }
        } finally {
            xlsxFile.delete()
        }
    }

    @Test
    fun testUnreadableFileHandling() {
        val nonExistentFile = File("non_existent_doc_${System.currentTimeMillis()}.pdf")
        val result = OfficeXmlExtractor.extract(nonExistentFile)

        assertFalse(result.fullText.isNotBlank())
        assertNotNull(result.failureReason)
        assertEquals(0, result.pageCount)
    }

    // Helper: Creates a synthetic DOCX file with document.xml, header, footer, and media
    private fun createSyntheticDocxFile(): File {
        val file = File.createTempFile("synthetic_test", ".docx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // Header
            zos.putNextEntry(ZipEntry("word/header1.xml"))
            zos.write("<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:r><w:t>Docsy Confidential Header</w:t></w:r></w:p></w:hdr>".toByteArray())
            zos.closeEntry()

            // Footer
            zos.putNextEntry(ZipEntry("word/footer1.xml"))
            zos.write("<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:r><w:t>Page 1 of 1</w:t></w:r></w:p></w:ftr>".toByteArray())
            zos.closeEntry()

            // Media
            zos.putNextEntry(ZipEntry("word/media/sample_logo.png"))
            zos.write(ByteArray(128))
            zos.closeEntry()

            // Main document
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val xml = """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                      <w:r><w:t>Student Performance Report</w:t></w:r>
                    </w:p>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>Subject</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>Marks</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>Mathematics</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>95</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>
            """.trimIndent()
            zos.write(xml.toByteArray())
            zos.closeEntry()
        }
        return file
    }

    // Helper: Creates a synthetic XLSX file with workbook.xml, sharedStrings.xml, and worksheets
    private fun createSyntheticXlsxFile(): File {
        val file = File.createTempFile("synthetic_test", ".xlsx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // Workbook
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            val wbXml = """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Marksheet" sheetId="1" r:id="rId1"/>
                    <sheet name="Summary" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
            """.trimIndent()
            zos.write(wbXml.toByteArray())
            zos.closeEntry()

            // Shared Strings
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val sstXml = """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Student Name</t></si>
                  <si><t>John Doe</t></si>
                </sst>
            """.trimIndent()
            zos.write(sstXml.toByteArray())
            zos.closeEntry()

            // Sheet 1
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheet1Xml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="n"><v>10</v></c>
                      <c r="B2" t="n"><f>A2*10</f><v>100</v></c>
                    </row>
                  </sheetData>
                  <mergeCells>
                    <mergeCell ref="A1:B1"/>
                  </mergeCells>
                </worksheet>
            """.trimIndent()
            zos.write(sheet1Xml.toByteArray())
            zos.closeEntry()

            // Sheet 2
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
            val sheet2Xml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>1</v></c></row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
            zos.write(sheet2Xml.toByteArray())
            zos.closeEntry()
        }
        return file
    }
}
