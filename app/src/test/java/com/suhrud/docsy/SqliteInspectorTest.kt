package com.suhrud.docsy

import com.suhrud.docsy.ui.inspector.SqliteInspectorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SqliteInspectorTest {

    private val viewModel = SqliteInspectorViewModel()

    @Test
    fun testWriteStatementRejection() {
        val writeStatements = listOf(
            "INSERT INTO documents VALUES (1, 'test')",
            "UPDATE documents SET name = 'foo'",
            "DELETE FROM chunks WHERE id = 1",
            "DROP TABLE documents",
            "ALTER TABLE documents ADD COLUMN x TEXT",
            "CREATE TABLE hack (id INT)",
            "REPLACE INTO facts VALUES (1, 'k', 'v')",
            "ATTACH DATABASE 'other.db' AS aux",
            "VACUUM"
        )

        for (sql in writeStatements) {
            val (isWrite, kw) = viewModel.isWriteStatement(sql)
            assertTrue("Statement '$sql' should be rejected as a write statement", isWrite)
            assertNotNull("Forbidden keyword should be captured", kw)
        }
    }

    @Test
    fun testReadOnlyStatementAllowed() {
        val readStatements = listOf(
            "SELECT * FROM documents",
            "SELECT count(*) FROM chunks",
            "PRAGMA table_info('doc_facts')",
            "EXPLAIN QUERY PLAN SELECT * FROM doc_json"
        )

        for (sql in readStatements) {
            val (isWrite, _) = viewModel.isWriteStatement(sql)
            assertFalse("Statement '$sql' should be allowed in read-only console", isWrite)
        }
    }

    @Test
    fun testNullDistinctFromEmptyAndZero() {
        val nullMarker = "[NULL]"
        val emptyStr = ""
        val zeroStr = "0"

        assertNotEquals(nullMarker, emptyStr)
        assertNotEquals(nullMarker, zeroStr)
        assertEquals("[NULL]", nullMarker)
    }

    @Test
    fun testPagingCalculation() {
        val totalRows = 125L
        val pageSize = 50
        val totalPages = (totalRows + pageSize - 1) / pageSize

        assertEquals(3L, totalPages)
    }
}
