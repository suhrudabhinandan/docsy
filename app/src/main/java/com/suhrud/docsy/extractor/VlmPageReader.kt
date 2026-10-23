package com.suhrud.docsy.extractor

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Turns a page image into text through the vision model, in a form the layout engine understands.
 *
 * Gemma-class vision encoders look at a small (about 768 px) square, so a whole A4 page would be unreadable.
 * The page is therefore cut into overlapping horizontal bands, each read separately, then joined without
 * the duplicate lines that the overlap creates.
 */
object VlmPageReader {

    const val PROMPT = "Transcribe all the text in this image exactly as printed, in reading order, one line per row. " +
        "For any table, write each row on one line with the cells separated by ' | ' and keep empty cells as empty. " +
        "Copy every number, grade, roll number, SGPA, CGPA, percentage and result exactly. " +
        "Do not add comments, explanations or markdown headings."

    private const val BAND = 1024
    private const val OVERLAP = 128
    private const val MAX_BANDS = 4

    suspend fun readPage(context: android.content.Context, page: Bitmap): String? {
        val scaled = scaleToWidth(page, BAND)
        val bands = cutBands(scaled)
        try {
            val texts = ArrayList<String>()
            for (band in bands) {
                val t = VlmEngine.transcribe(context, band, PROMPT) ?: continue
                texts.add(cleanFences(t))
                if (band !== scaled) band.recycle()
            }
            if (texts.isEmpty()) return null
            return joinBands(texts)
        } finally {
            if (scaled !== page) scaled.recycle()
        }
    }

    private fun scaleToWidth(src: Bitmap, width: Int): Bitmap {
        if (src.width <= width) return src
        val h = (src.height.toFloat() * width / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, width, h, true)
    }

    private fun cutBands(src: Bitmap): List<Bitmap> {
        if (src.height <= BAND + BAND / 4) return listOf(src)
        val step = BAND - OVERLAP
        val out = ArrayList<Bitmap>()
        var top = 0
        while (top < src.height && out.size < MAX_BANDS) {
            val h = minOf(BAND, src.height - top)
            out.add(Bitmap.createBitmap(src, 0, top, src.width, h))
            if (top + h >= src.height) break
            top += step
        }
        return out
    }

    private fun cleanFences(t: String): String =
        t.lines().filterNot { it.trim().startsWith("```") }.joinToString("\n").trim()

    /** Drops lines at the top of a band that repeat the last lines of the previous band (the overlap). */
    private fun joinBands(texts: List<String>): String {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9|]"), "")
        val out = ArrayList<String>()
        for ((i, text) in texts.withIndex()) {
            var lines = text.lines().filter { it.isNotBlank() }
            if (i > 0 && out.isNotEmpty()) {
                val tail = out.takeLast(6).map { norm(it) }.toSet()
                var skip = 0
                while (skip < minOf(6, lines.size) && norm(lines[skip]) in tail && norm(lines[skip]).isNotEmpty()) skip++
                lines = lines.drop(skip)
            }
            out.addAll(lines)
        }
        return out.joinToString("\n")
    }

    /**
     * Converts the transcription into positioned units: a " | " row becomes one unit per cell, in fixed-width
     * columns, so empty cells keep their column and the layout engine rebuilds the same table.
     */
    fun toUnits(text: String): List<RawTextUnit> {
        val units = ArrayList<RawTextUnit>()
        var y = 0
        val cellW = 400
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.all { it == '|' || it == '-' || it == ':' || it == ' ' }) continue // markdown separator row
            if (line.contains('|')) {
                val cells = line.trim('|').split('|')
                for ((c, cell) in cells.withIndex()) {
                    val t = cell.trim()
                    if (t.isEmpty()) continue
                    units.add(RawTextUnit(t, Rect(c * cellW, y, c * cellW + cellW - 40, y + 30), 0.8f))
                }
            } else {
                units.add(RawTextUnit(line, Rect(0, y, 3000, y + 30), 0.8f))
            }
            y += 50
        }
        return units
    }
}
