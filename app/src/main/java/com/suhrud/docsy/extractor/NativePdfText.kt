package com.suhrud.docsy.extractor

import android.graphics.Rect
import android.util.Log
import com.suhrud.docsy.DocsyApplication
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * Reads the text that is already inside a digital PDF, with the position of every word.
 * No rendering and no OCR, so it is exact (no misread digits) and many times faster.
 * Pages without a usable text layer (scans, photos, broken fonts) are left out and read by OCR instead.
 */
object NativePdfText {
    private const val TAG = "NativePdfText"
    private const val SCALE = 2f // PDF points -> pixel-like units, so geometry matches what OCR produces
    private const val MAX_FILE_BYTES = 25L * 1024 * 1024

    @Volatile private var initialized = false

    class Result(val pages: Map<Int, LayoutPageAnalysisResult>, val pageCount: Int)

    private class Word(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

    private class WordStripper : PDFTextStripper() {
        val words = ArrayList<Word>()

        // PDFBox calls this once per word, with the glyph positions of that word.
        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (tp in textPositions) {
                val x = tp.xDirAdj
                val y = tp.yDirAdj
                val w = tp.widthDirAdj
                val h = tp.heightDir
                if (x < l) l = x
                if (x + w > r) r = x + w
                if (y - h < t) t = y - h
                if (y > b) b = y
            }
            if (r > l && b > t) words.add(Word(text.trim(), l, t, r, b))
        }
    }

    /** Returns null when native reading is not possible at all (encrypted, huge, library problem). */
    fun extract(file: File, maxPages: Int): Result? {
        if (file.length() > MAX_FILE_BYTES) return null
        val context = try { DocsyApplication.instance.applicationContext } catch (_: Throwable) { return null }

        var doc: PDDocument? = null
        try {
            if (!initialized) {
                PDFBoxResourceLoader.init(context)
                initialized = true
            }
            doc = PDDocument.load(file)
            if (doc.isEncrypted) return null

            val total = doc.numberOfPages
            val out = HashMap<Int, LayoutPageAnalysisResult>()
            for (pageNo in 1..minOf(total, maxPages)) {
                val stripper = WordStripper()
                stripper.setSortByPosition(true)
                stripper.setStartPage(pageNo)
                stripper.setEndPage(pageNo)
                stripper.getText(doc)

                val units = mergeWords(stripper.words)
                if (!isUsable(units)) continue
                out[pageNo] = LayoutModelAnalyzer.analyzeUnits(units, pageNo)
            }
            return Result(out, total)
        } catch (t: Throwable) {
            Log.w(TAG, "Native PDF text unavailable, falling back to OCR: ${t.message}")
            return null
        } finally {
            try { doc?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Words on the same line that sit close together become one piece of text ("Mathematics Paper I").
     * A wide gap keeps pieces apart, which is what lets the layout engine see table columns.
     */
    private fun mergeWords(words: List<Word>): List<RawTextUnit> {
        if (words.isEmpty()) return emptyList()

        val wordUnits = words.map {
            RawTextUnit(
                text = it.text,
                rect = Rect(
                    (it.left * SCALE).toInt(), (it.top * SCALE).toInt(),
                    (it.right * SCALE).toInt().coerceAtLeast((it.left * SCALE).toInt() + 1),
                    (it.bottom * SCALE).toInt().coerceAtLeast((it.top * SCALE).toInt() + 1)
                ),
                confidence = 1.0f
            )
        }

        val merged = ArrayList<RawTextUnit>()
        for (row in LayoutModelAnalyzer.clusterRows(wordUnits)) {
            var current: RawTextUnit? = null
            for (w in row) {
                val c = current
                if (c == null) { current = w; continue }
                val height = maxOf(c.rect.height(), w.rect.height()).coerceAtLeast(1)
                val gap = w.rect.left - c.rect.right
                if (gap <= height * 0.7) {
                    current = RawTextUnit(
                        text = c.text + " " + w.text,
                        rect = Rect(
                            minOf(c.rect.left, w.rect.left), minOf(c.rect.top, w.rect.top),
                            maxOf(c.rect.right, w.rect.right), maxOf(c.rect.bottom, w.rect.bottom)
                        ),
                        confidence = 1.0f
                    )
                } else {
                    merged.add(c)
                    current = w
                }
            }
            current?.let { merged.add(it) }
        }
        return merged
    }

    /** A page counts as digital text only if it has real letters/digits and no sign of broken font encoding. */
    private fun isUsable(units: List<RawTextUnit>): Boolean {
        if (units.isEmpty()) return false
        val text = units.joinToString(" ") { it.text }
        val meaningful = text.count { it.isLetterOrDigit() }
        if (meaningful < 25) return false
        val broken = text.count { it == '\uFFFD' || it in '\uE000'..'\uF8FF' || (it.isISOControl() && !it.isWhitespace()) }
        return broken <= text.length * 0.05
    }
}
