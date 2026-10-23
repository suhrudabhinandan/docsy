package com.suhrud.docsy.retrieval

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.understanding.DocumentSynonymDictionary
import java.text.Normalizer
import java.util.Locale

data class SearchResultItem(
    val chunkId: Long,
    val documentId: Long,
    val page: Int,
    val position: Int,
    val chunkType: String,
    val text: String,
    val score: Float,
    val ownerName: String? = null,
    val isRelaxedResult: Boolean = false
)

class Fts5SearchEngine(
    private val database: DocsyDatabase
) {

    companion object {
        private const val TAG = "Fts5SearchEngine"

        @Volatile
        var isFts5Supported: Boolean = true
            private set

        @Volatile
        var lastProbeError: String? = null
            private set

        private val STOP_WORDS = setOf(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "when",
            "at", "by", "for", "with", "about", "against", "between", "into",
            "through", "during", "before", "after", "above", "below", "to", "from",
            "up", "down", "in", "out", "on", "off", "over", "under", "again", "further",
            "is", "am", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "my", "what", "tell", "me", "show", "get", "find", "kya", "hai", "mera"
        )

        fun probeFts5(db: SupportSQLiteDatabase): Boolean {
            return try {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _fts5_probe USING fts5(dummy, tokenize=\"unicode61 remove_diacritics 2\");")
                db.execSQL("DROP TABLE IF EXISTS _fts5_probe;")
                isFts5Supported = true
                lastProbeError = null
                Log.d(TAG, "FTS5 startup probe passed successfully.")
                true
            } catch (e: Throwable) {
                isFts5Supported = false
                lastProbeError = e.message ?: e.toString()
                Log.e(TAG, "FTS5 startup probe failed!", e)
                false
            }
        }

        fun normalizeToken(token: String): String {
            val decomposed = Normalizer.normalize(token, Normalizer.Form.NFD)
            val withoutDiacritics = decomposed.replace(Regex("\\p{M}"), "")
            return Normalizer.normalize(withoutDiacritics, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        }

        fun escapeFtsToken(token: String): String {
            val clean = token.replace("\"", "\"\"").replace("'", "''")
            return "\"$clean\""
        }

        fun quoteFtsToken(token: String): String {
            return escapeFtsToken(token)
        }
    }

    suspend fun searchFreeText(query: String, limit: Int = 20): List<SearchResultItem> {
        if (!isFts5Supported) return emptyList()

        val normalizedQuery = QueryNormalizer.normalize(query)
        val rawTokens = normalizedQuery.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { normalizeToken(it) }
            .filter { it.isNotBlank() && it !in STOP_WORDS }

        if (rawTokens.isEmpty()) return emptyList()

        // 1. Strict Query (AND of required content terms)
        val strictExpression = buildStrictFtsExpression(rawTokens)
        var results = executeFtsQuery(strictExpression, isRelaxed = false, limit = limit)

        // 2. Relaxed Query (OR with minimum coverage) if strict returns empty
        if (results.isEmpty() && rawTokens.size > 1) {
            val relaxedExpression = buildRelaxedFtsExpression(rawTokens)
            results = executeFtsQuery(relaxedExpression, isRelaxed = true, limit = limit)
        }

        return results
    }

    private fun buildStrictFtsExpression(tokens: List<String>): String {
        return tokens.joinToString(" AND ") { token ->
            val synonyms = getSynonymsForToken(token)
            val allForms = (listOf(token) + synonyms).distinct()
            if (allForms.size == 1) {
                val term = if (token.length >= 3 && !token.any { it.isDigit() }) "${escapeFtsToken(token)}*" else escapeFtsToken(token)
                term
            } else {
                "(" + allForms.joinToString(" OR ") { syn ->
                    if (syn.length >= 3 && !syn.any { it.isDigit() }) "${escapeFtsToken(syn)}*" else escapeFtsToken(syn)
                } + ")"
            }
        }
    }

    private fun buildRelaxedFtsExpression(tokens: List<String>): String {
        return tokens.joinToString(" OR ") { escapeFtsToken(it) }
    }

    private fun getSynonymsForToken(token: String): List<String> {
        val cat = DocumentSynonymDictionary.resolveCanonicalCategory(token)
        return if (cat != null) {
            DocumentSynonymDictionary.getSynonyms(cat)
        } else {
            when (token) {
                "12th" -> listOf("xii", "twelfth", "senior secondary")
                "10th" -> listOf("x", "tenth", "secondary")
                "marksheet" -> listOf("result", "transcript", "scorecard", "report card")
                "cv" -> listOf("resume", "biodata", "curriculum vitae")
                else -> emptyList()
            }
        }
    }

    private fun executeFtsQuery(ftsExpression: String, isRelaxed: Boolean, limit: Int): List<SearchResultItem> {
        val sql = """
            SELECT c.id, c.documentId, c.page, c.position, c.kind, c.text, bm25(chunks_fts, 3.0, 2.0, 1.0) AS rankScore
            FROM chunks c
            JOIN chunks_fts f ON c.id = f.rowid
            WHERE chunks_fts MATCH ?
            ORDER BY rankScore ASC
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<SearchResultItem>()
        try {
            val cursor = database.openHelper.readableDatabase.query(sql, arrayOf(ftsExpression, limit))
            cursor.use {
                val idIdx = cursor.getColumnIndex("id")
                val docIdIdx = cursor.getColumnIndex("documentId")
                val pageIdx = cursor.getColumnIndex("page")
                val posIdx = cursor.getColumnIndex("position")
                val kindIdx = cursor.getColumnIndex("kind")
                val textIdx = cursor.getColumnIndex("text")
                val scoreIdx = cursor.getColumnIndex("rankScore")

                while (cursor.moveToNext()) {
                    results.add(
                        SearchResultItem(
                            chunkId = cursor.getLong(idIdx),
                            documentId = cursor.getLong(docIdIdx),
                            page = cursor.getInt(pageIdx),
                            position = cursor.getInt(posIdx),
                            chunkType = if (kindIdx >= 0) cursor.getString(kindIdx) else "PARAGRAPH",
                            text = cursor.getString(textIdx),
                            score = cursor.getFloat(scoreIdx),
                            isRelaxedResult = isRelaxed
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing FTS query: $ftsExpression", e)
        }
        return results
    }

    fun optimizeFts() {
        if (!isFts5Supported) return
        try {
            database.openHelper.writableDatabase.execSQL("INSERT INTO chunks_fts(chunks_fts) VALUES('optimize');")
        } catch (e: Exception) {
            Log.w(TAG, "FTS optimize failed", e)
        }
    }
}
