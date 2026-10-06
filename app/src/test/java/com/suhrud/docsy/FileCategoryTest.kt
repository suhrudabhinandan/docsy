package com.suhrud.docsy

import com.suhrud.docsy.data.model.FileCategoryConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCategoryTest {

    @Test
    fun testFileCategoryBucketing() {
        assertTrue("JPEG should be photo", FileCategoryConstants.isPhoto("jpg", "image/jpeg"))
        assertTrue("PNG case insensitive should be photo", FileCategoryConstants.isPhoto("PNG", "image/png"))
        assertTrue("MP4 should be video", FileCategoryConstants.isVideo("mp4", "video/mp4"))
        assertTrue("MP3 should be audio", FileCategoryConstants.isAudio("mp3", "audio/mpeg"))
        assertTrue("PDF should be pdf and document", FileCategoryConstants.isPdf("pdf", "application/pdf"))
        assertTrue("DOCX should be word and document", FileCategoryConstants.isWord("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertTrue("XLSX should be excel and document", FileCategoryConstants.isExcel("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue("CSV should be excel and document", FileCategoryConstants.isExcel("csv", "text/csv"))
        assertTrue("PPTX should be ppt and document", FileCategoryConstants.isPowerPoint("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertTrue("TXT should be text and document", FileCategoryConstants.isText("txt", "text/plain"))
    }

    @Test
    fun testHiddenAndThumbnailFiltering() {
        assertTrue(FileCategoryConstants.isHiddenOrThumbnailPath("/storage/emulated/0/DCIM/.thumbnails/thumb_1.jpg"))
        assertTrue(FileCategoryConstants.isHiddenOrThumbnailPath("/storage/emulated/0/Android/data/com.app/.cache/tmp.dat"))
        assertTrue(FileCategoryConstants.isHiddenOrThumbnailPath("/storage/emulated/0/Download/.trashed_file"))
        assertFalse(FileCategoryConstants.isHiddenOrThumbnailPath("/storage/emulated/0/Download/Invoice.pdf"))
    }
}
