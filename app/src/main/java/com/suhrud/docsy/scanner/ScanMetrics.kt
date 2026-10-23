package com.suhrud.docsy.scanner

data class ScanMetrics(
    val totalFilesWalked: Int = 0,
    val supportedDiscovered: Int = 0,
    val successfullyProcessed: Int = 0,
    val officeDocsCount: Int = 0,
    val textDocsCount: Int = 0,
    val imageDocsCount: Int = 0,
    val ocrRequiredCount: Int = 0,
    val unsupportedCount: Int = 0,
    val failedCount: Int = 0,
    val inaccessibleCount: Int = 0,
    val duplicatesSkippedCount: Int = 0,
    val scanDurationMs: Long = 0L
)
