package com.suhrud.docsy

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class StartupSourceHygieneTest {

    @Test
    fun testNoFileUnderUiContainsBothCircleAndDocsyLogo() {
        val uiDir = File("src/main/java/com/suhrud/docsy/ui")
        if (!uiDir.exists()) {
            val fallbackDir = File("app/src/main/java/com/suhrud/docsy/ui")
            if (fallbackDir.exists()) {
                checkDirectory(fallbackDir)
                return
            }
        } else {
            checkDirectory(uiDir)
        }
    }

    private fun checkDirectory(dir: File) {
        val violations = mutableListOf<String>()
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            val hasLogo = content.contains("docsy_logo")
            val hasCircle = content.contains("CircleShape") || content.contains("drawCircle")
            if (hasLogo && hasCircle) {
                violations.add("${file.relativeTo(dir).path}: references docsy_logo and uses CircleShape/drawCircle")
            }
        }
        if (violations.isNotEmpty()) {
            fail("Build hygiene violation: files under ui/ must not combine docsy_logo with CircleShape or drawCircle:\n" +
                    violations.joinToString("\n"))
        }
    }
}
