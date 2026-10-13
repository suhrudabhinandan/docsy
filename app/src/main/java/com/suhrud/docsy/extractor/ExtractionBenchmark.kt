package com.suhrud.docsy.extractor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class TypeBenchmarkStats(
    val fileCount: Int,
    val totalTimeMs: Long,
    val avgTimeMs: Double,
    val throughputFilesPerSec: Double
)

object ExtractionBenchmark {

    private val countMap = ConcurrentHashMap<String, AtomicInteger>()
    private val durationMap = ConcurrentHashMap<String, AtomicLong>()
    private val totalProcessedCount = AtomicInteger(0)
    private val totalProcessingTimeMs = AtomicLong(0)

    fun recordExtraction(extension: String, durationMs: Long) {
        val ext = extension.lowercase()
        countMap.computeIfAbsent(ext) { AtomicInteger(0) }.incrementAndGet()
        durationMap.computeIfAbsent(ext) { AtomicLong(0) }.addAndGet(durationMs)
        totalProcessedCount.incrementAndGet()
        totalProcessingTimeMs.addAndGet(durationMs)
    }

    fun getStatsForExtension(extension: String): TypeBenchmarkStats {
        val ext = extension.lowercase()
        val count = countMap[ext]?.get() ?: 0
        val duration = durationMap[ext]?.get() ?: 0L
        val avg = if (count > 0) duration.toDouble() / count else 0.0
        val throughput = if (duration > 0) (count * 1000.0) / duration else 0.0
        return TypeBenchmarkStats(
            fileCount = count,
            totalTimeMs = duration,
            avgTimeMs = avg,
            throughputFilesPerSec = throughput
        )
    }

    fun getTotalThroughputFilesPerSec(): Double {
        val totalCount = totalProcessedCount.get()
        val totalTime = totalProcessingTimeMs.get()
        return if (totalTime > 0) (totalCount * 1000.0) / totalTime else 0.0
    }

    fun reset() {
        countMap.clear()
        durationMap.clear()
        totalProcessedCount.set(0)
        totalProcessingTimeMs.set(0)
    }
}
