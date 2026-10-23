package com.suhrud.docsy.reconstruction

import com.suhrud.docsy.model.CommonDocumentModel.Block
import com.suhrud.docsy.model.CommonDocumentModel.BoundingBox
import com.suhrud.docsy.model.CommonDocumentModel.Line
import com.suhrud.docsy.model.CommonDocumentModel.PageDoc
import com.suhrud.docsy.model.CommonDocumentModel.Word
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object TextReconstructionEngine {

    fun reconstructPage(pageNumber: Int, words: List<Word>, width: Float = 1000f, height: Float = 1000f): PageDoc {
        if (words.isEmpty()) return PageDoc(pageNumber, emptyList(), width, height)

        // 1. Group words by vertical overlap and sort horizontally
        val sortedWords = words.sortedWith(compareBy({ it.box?.top ?: 0f }, { it.box?.left ?: 0f }))
        val lines = mutableListOf<Line>()
        var currentLineWords = mutableListOf<Word>()
        var currentLineTop: Float? = null
        var currentLineBottom: Float? = null

        for (word in sortedWords) {
            val box = word.box
            if (box == null) {
                currentLineWords.add(word)
                continue
            }

            if (currentLineTop == null || currentLineBottom == null) {
                currentLineWords.add(word)
                currentLineTop = box.top
                currentLineBottom = box.bottom
            } else {
                val lineH = currentLineBottom - currentLineTop
                val overlapTop = max(currentLineTop, box.top)
                val overlapBottom = min(currentLineBottom, box.bottom)
                val overlapH = (overlapBottom - overlapTop).coerceAtLeast(0f)

                if (lineH > 0 && overlapH / min(lineH, box.height) >= 0.4f) {
                    currentLineWords.add(word)
                    currentLineTop = min(currentLineTop, box.top)
                    currentLineBottom = max(currentLineBottom, box.bottom)
                } else {
                    lines.add(buildLineFromWords(currentLineWords))
                    currentLineWords = mutableListOf(word)
                    currentLineTop = box.top
                    currentLineBottom = box.bottom
                }
            }
        }
        if (currentLineWords.isNotEmpty()) {
            lines.add(buildLineFromWords(currentLineWords))
        }

        // 2. Group lines into blocks
        val blocks = groupLinesIntoBlocks(lines)
        return PageDoc(pageNumber = pageNumber, blocks = blocks, width = width, height = height)
    }

    fun cleanAndDehyphenateText(text: String): String {
        if (text.isBlank()) return ""
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val dehyphenated = normalized.replace(Regex("""(\b[a-zA-Z]{2,})-\s*\n\s*([a-zA-Z]{2,}\b)"""), "$1$2")
        val repairedSlashes = repairFragmentedSlashes(dehyphenated)
        return repairedSlashes.replace(Regex("""[ \t]+"""), " ").trim()
    }

    fun repairFragmentedSlashes(raw: String): String {
        // Repair fragmented words with slashes or spaces like "na / me" or "ab / hinandan" without altering numbers/IDs
        return raw.replace(Regex("""\b([a-zA-Z]{1,4})\s*/\s*([a-zA-Z]{1,8})\b""")) { match ->
            val p1 = match.groupValues[1]
            val p2 = match.groupValues[2]
            p1 + p2
        }.replace(Regex("""\b([a-zA-Z]{1,3})\s+([a-zA-Z]{2,4})\s+([a-zA-Z]{2,8})\b""")) { match ->
            val w1 = match.groupValues[1]
            val w2 = match.groupValues[2]
            val w3 = match.groupValues[3]
            if (w1.lowercase() == "na" && w2.lowercase() == "me") "name $w3"
            else match.value
        }
    }

    private fun buildLineFromWords(words: List<Word>): Line {
        val sorted = words.sortedBy { it.box?.left ?: 0f }
        val mergedTextBuilder = StringBuilder()
        var prevBox: BoundingBox? = null

        for (w in sorted) {
            val box = w.box
            if (prevBox != null && box != null) {
                val gap = box.left - prevBox.right
                val avgCharWidth = if (w.text.isNotEmpty()) box.width / w.text.length else 10f
                if (gap > avgCharWidth * 0.4f) {
                    mergedTextBuilder.append(" ")
                }
            } else if (prevBox != null && mergedTextBuilder.isNotEmpty()) {
                mergedTextBuilder.append(" ")
            }
            mergedTextBuilder.append(w.text)
            prevBox = box
        }

        val rawText = mergedTextBuilder.toString()
        val cleanedText = cleanAndDehyphenateText(rawText)
        val boundingBox = computeBoundingBox(sorted.mapNotNull { it.box })
        return Line(words = sorted, text = cleanedText, box = boundingBox)
    }

    private fun groupLinesIntoBlocks(lines: List<Line>): List<Block> {
        if (lines.isEmpty()) return emptyList()
        val blocks = mutableListOf<Block>()
        var currentBlockLines = mutableListOf<Line>()

        for (line in lines) {
            if (currentBlockLines.isEmpty()) {
                currentBlockLines.add(line)
            } else {
                val lastLine = currentBlockLines.last()
                val gapY = if (line.box != null && lastLine.box != null) {
                    line.box.top - lastLine.box.bottom
                } else 0f

                val avgHeight = lastLine.box?.height ?: 15f
                if (gapY > avgHeight * 1.8f) {
                    blocks.add(Block(lines = currentBlockLines, box = computeBoundingBox(currentBlockLines.mapNotNull { it.box })))
                    currentBlockLines = mutableListOf(line)
                } else {
                    currentBlockLines.add(line)
                }
            }
        }
        if (currentBlockLines.isNotEmpty()) {
            blocks.add(Block(lines = currentBlockLines, box = computeBoundingBox(currentBlockLines.mapNotNull { it.box })))
        }
        return blocks
    }

    fun computeBoundingBox(boxes: List<BoundingBox>): BoundingBox? {
        if (boxes.isEmpty()) return null
        var minL = Float.MAX_VALUE
        var minT = Float.MAX_VALUE
        var maxR = Float.MIN_VALUE
        var maxB = Float.MIN_VALUE

        for (b in boxes) {
            if (b.left < minL) minL = b.left
            if (b.top < minT) minT = b.top
            if (b.right > maxR) maxR = b.right
            if (b.bottom > maxB) maxB = b.bottom
        }
        return BoundingBox(minL, minT, maxR, maxB)
    }

    fun createCompactLayoutBlob(pageDoc: PageDoc): String {
        return buildString {
            append("P${pageDoc.pageNumber}:")
            for (block in pageDoc.blocks) {
                append("[B:")
                for (line in block.lines) {
                    append(line.text.replace("\n", " ")).append("|")
                }
                append("]")
            }
        }
    }
}
