package com.suhrud.docsy

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class NoSampleContentTest {

    @Test
    fun testNoSamplePhrasesInUiCodebase() {
        val uiDir = File("src/main/java/com/suhrud/docsy/ui")
        if (!uiDir.exists()) return

        val samplePatterns = listOf(
            "₹1,842",
            "1,842",
            "What is my electricity bill",
            "APRIL · ELECTRICITY BILL",
            "Electricity_Bill_April",
            "Water_Bill_May"
        )

        uiDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            for (pattern in samplePatterns) {
                assertFalse(
                    "UI file ${file.name} contains forbidden sample phrase: '$pattern'",
                    text.contains(pattern)
                )
            }
        }
    }
}
