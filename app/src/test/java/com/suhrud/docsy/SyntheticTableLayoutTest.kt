package com.suhrud.docsy

import android.graphics.Rect
import com.suhrud.docsy.extractor.ImagePreprocessor
import com.suhrud.docsy.extractor.LayoutModelAnalyzer
import com.suhrud.docsy.extractor.RawTextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyntheticTableLayoutTest {

    @Test
    fun testSyntheticTableExtractionAndClustering() {
        // Create synthetic text units representing a marksheet/report card table with key-value header
        val units = listOf(
            RawTextUnit(text = "Name: Suhrud Abhinandan", rect = Rect(50, 40, 400, 70)),
            RawTextUnit(text = "Roll No: 12646545", rect = Rect(50, 80, 300, 110)),
            
            // Table Header (Row 0)
            RawTextUnit(text = "Subject", rect = Rect(50, 160, 200, 190)),
            RawTextUnit(text = "Marks", rect = Rect(220, 160, 320, 190)),
            RawTextUnit(text = "Max", rect = Rect(340, 160, 420, 190)),

            // Table Row 1
            RawTextUnit(text = "Mathematics", rect = Rect(50, 210, 200, 240)),
            RawTextUnit(text = "95", rect = Rect(220, 210, 320, 240)),
            RawTextUnit(text = "100", rect = Rect(340, 210, 420, 240)),

            // Table Row 2
            RawTextUnit(text = "Physics", rect = Rect(50, 260, 200, 290)),
            RawTextUnit(text = "88", rect = Rect(220, 260, 320, 290)),
            RawTextUnit(text = "100", rect = Rect(340, 260, 420, 290))
        )

        val analysisResult = LayoutModelAnalyzer.analyzeUnits(units, pageNumber = 1)

        assertNotNull(analysisResult)
        assertTrue("Lines should not be empty", analysisResult.lines.isNotEmpty())
        assertTrue("Blocks should be extracted", analysisResult.blocks.isNotEmpty())

        // Verify Key-Values detected
        val kvPairs = analysisResult.keyValues
        assertTrue("Should detect key-value pairs", kvPairs.isNotEmpty())
        assertEquals("Suhrud Abhinandan", kvPairs.find { it.key == "Name" }?.value)
        assertEquals("12646545", kvPairs.find { it.key == "Roll No" }?.value)

        // Verify Table Cells detected
        val tableCells = analysisResult.tables
        assertTrue("Should detect table cells", tableCells.isNotEmpty())

        // Check header row 0
        val mathCell = tableCells.find { it.text == "Mathematics" }
        assertNotNull("Mathematics cell should exist in table", mathCell)
        val mathMarksCell = tableCells.find { it.row == mathCell?.row && it.col == 1 }
        assertEquals("95", mathMarksCell?.text)

        // Verify Reading Order
        val firstBlock = analysisResult.blocks.first()
        assertEquals(1, firstBlock.order)
        assertTrue("First block should contain Name", firstBlock.text.contains("Name"))
    }

    @Test
    fun testImagePreprocessorDeskewAndEstimate() {
        val bitmap = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
        }
        canvas.drawText("Test Synthetic Document Preprocessing", 100f, 200f, paint)

        val preprocessed = ImagePreprocessor.preprocess(bitmap)
        assertNotNull(preprocessed)
        assertTrue("Preprocessed image should have valid width", preprocessed.width > 0)
    }
}
