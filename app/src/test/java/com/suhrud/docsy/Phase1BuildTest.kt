package com.suhrud.docsy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase1BuildTest {

    @Test
    fun testVersionAndPackageConstants() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue(BuildConfig.VERSION_CODE >= 2)
        assertEquals("com.suhrud.docsy", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun testManifestHasNoInternetPermission() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            if (content.contains("android.permission.INTERNET")) {
                assertTrue(
                    "Any occurrence of INTERNET in manifest must be tagged with tools:node=\"remove\"",
                    content.contains("tools:node=\"remove\"")
                )
            }
            assertTrue(
                "Manifest must declare allowBackup=false",
                content.contains("android:allowBackup=\"false\"")
            )
        }
    }

    @Test
    fun testFlavorsAndRulesConfigured() {
        val buildGradle = File("build.gradle.kts")
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            assertTrue(content.contains("full"))
            assertTrue(content.contains("noSmsCalls"))
            assertTrue(content.contains("filesOnly"))
            assertFalse(content.contains("debugConfig"))
        }
    }
}
