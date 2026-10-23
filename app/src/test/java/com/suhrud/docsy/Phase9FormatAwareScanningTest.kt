package com.suhrud.docsy

import com.suhrud.docsy.extractor.FormatAwareExtractionDispatcher
import com.suhrud.docsy.scanner.FileFilter
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
class Phase9FormatAwareScanningTest {

    @Test
    fun testFileFilterFormatCategories() {
        assertTrue("PDF must be office document", FileFilter.isOfficeDocument("pdf"))
        assertTrue("DOCX must be office document", FileFilter.isOfficeDocument("docx"))
        assertTrue("XLSX must be office document", FileFilter.isOfficeDocument("xlsx"))

        assertTrue("TXT must be text/structured data", FileFilter.isTextOrStructuredData("txt"))
        assertTrue("CSV must be text/structured data", FileFilter.isTextOrStructuredData("csv"))
        assertTrue("JSON must be text/structured data", FileFilter.isTextOrStructuredData("json"))
        assertTrue("XML must be text/structured data", FileFilter.isTextOrStructuredData("xml"))

        assertTrue("ODT must be OpenDocument format", FileFilter.isOpenDocument("odt"))
        assertTrue("ODS must be OpenDocument format", FileFilter.isOpenDocument("ods"))

        assertTrue("JPG must be image document", FileFilter.isImageDocument("jpg"))
        assertTrue("PNG must be image document", FileFilter.isImageDocument("png"))
    }

    @Test
    fun testTextAndStructuredDataFormatsDoNotTriggerOcr() {
        val txtFile = createTempTextFile("sample.txt", "Student Name: Rahul Sharma\nRoll No: 241023")
        val csvFile = createTempTextFile("sample.csv", "Subject,Marks\nMathematics,95\nPhysics,92")
        val jsonFile = createTempTextFile("sample.json", "{\"student\":\"Rahul\",\"marks\":95}")

        try {
            val txtResult = kotlinx.coroutines.runBlocking {
                FormatAwareExtractionDispatcher.dispatchExtraction(txtFile)
            }
            assertFalse("TXT files must NOT trigger OCR", txtResult.isOcrExecuted)
            assertTrue("TXT text must be extracted", txtResult.extractionResult.fullText.contains("Rahul Sharma"))

            val csvResult = kotlinx.coroutines.runBlocking {
                FormatAwareExtractionDispatcher.dispatchExtraction(csvFile)
            }
            assertFalse("CSV files must NOT trigger OCR", csvResult.isOcrExecuted)
            assertTrue("CSV text must be extracted", csvResult.extractionResult.fullText.contains("Mathematics"))

            val jsonResult = kotlinx.coroutines.runBlocking {
                FormatAwareExtractionDispatcher.dispatchExtraction(jsonFile)
            }
            assertFalse("JSON files must NOT trigger OCR", jsonResult.isOcrExecuted)
            assertTrue("JSON text must be extracted", jsonResult.extractionResult.fullText.contains("Rahul"))
        } finally {
            txtFile.delete()
            csvFile.delete()
            jsonFile.delete()
        }
    }

    @Test
    fun testDocxAndXlsxDoNotTriggerOcr() {
        val docxFile = createSyntheticDocxFile()
        try {
            val docxResult = kotlinx.coroutines.runBlocking {
                FormatAwareExtractionDispatcher.dispatchExtraction(docxFile)
            }
            assertFalse("DOCX files must NOT trigger OCR", docxResult.isOcrExecuted)
            assertTrue("DOCX text must be extracted natively", docxResult.extractionResult.fullText.contains("Student Performance Report"))
        } finally {
            docxFile.delete()
        }
    }

    @Test
    fun testUnsupportedFilesAreSkippedSafely() {
        val unsupportedFile = createTempTextFile("unknown_bin.xyz", "Binary data stream")
        try {
            assertFalse("XYZ extension should not be supported file", FileFilter.isSupportedFile(unsupportedFile))
            val result = kotlinx.coroutines.runBlocking {
                FormatAwareExtractionDispatcher.dispatchExtraction(unsupportedFile)
            }
            assertEquals("UNKNOWN", result.formatCategory)
            assertFalse("Unsupported files must NOT trigger OCR", result.isOcrExecuted)
        } finally {
            unsupportedFile.delete()
        }
    }

    private fun createTempTextFile(name: String, content: String): File {
        val file = File.createTempFile("test_", name)
        file.writeText(content)
        return file
    }

    private fun createSyntheticDocxFile(): File {
        val file = File.createTempFile("test_", ".docx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val xml = """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>Student Performance Report</w:t></w:r></w:p>
                  </w:body>
                </w:document>
            """.trimIndent()
            zos.write(xml.toByteArray())
            zos.closeEntry()
        }
        return file
    }
}
