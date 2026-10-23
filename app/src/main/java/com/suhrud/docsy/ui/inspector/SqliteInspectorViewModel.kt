package com.suhrud.docsy.ui.inspector

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.retrieval.Fts5SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class DbHealthInfo(
    val sqliteVersion: String = "Unknown",
    val fts5ProbePassed: Boolean = false,
    val isBundledDriver: Boolean = true,
    val schemaVersion: Int = 5,
    val dbSizeBytes: Long = 0L,
    val lastError: String? = null
)

data class TableMeta(
    val name: String,
    val type: String,
    val isVirtual: Boolean,
    val isShadow: Boolean,
    val rowCount: Long
)

data class TableSchemaCol(
    val cid: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val isPk: Boolean
)

data class QueryResult(
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val executionTimeMs: Long = 0L,
    val errorMessage: String? = null
)

class SqliteInspectorViewModel : ViewModel() {

    private val _healthInfo = MutableStateFlow(DbHealthInfo())
    val healthInfo: StateFlow<DbHealthInfo> = _healthInfo.asStateFlow()

    private val _tables = MutableStateFlow<List<TableMeta>>(emptyList())
    val tables: StateFlow<List<TableMeta>> = _tables.asStateFlow()

    private val _showShadowTables = MutableStateFlow(false)
    val showShadowTables: StateFlow<Boolean> = _showShadowTables.asStateFlow()

    private val _selectedTable = MutableStateFlow<String?>(null)
    val selectedTable: StateFlow<String?> = _selectedTable.asStateFlow()

    private val _tableCreateSql = MutableStateFlow("")
    val tableCreateSql: StateFlow<String> = _tableCreateSql.asStateFlow()

    private val _tableSchema = MutableStateFlow<List<TableSchemaCol>>(emptyList())
    val tableSchema: StateFlow<List<TableSchemaCol>> = _tableSchema.asStateFlow()

    private val _tableRows = MutableStateFlow<List<List<String>>>(emptyList())
    val tableRows: StateFlow<List<List<String>>> = _tableRows.asStateFlow()

    private val _tableColumns = MutableStateFlow<List<String>>(emptyList())
    val tableColumns: StateFlow<List<String>> = _tableColumns.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalRowsInTable = MutableStateFlow(0L)
    val totalRowsInTable: StateFlow<Long> = _totalRowsInTable.asStateFlow()

    private val _sortColumn = MutableStateFlow<String?>(null)
    val sortColumn: StateFlow<String?> = _sortColumn.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _filterQuery = MutableStateFlow("")
    val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

    private val _sqlResult = MutableStateFlow(QueryResult())
    val sqlResult: StateFlow<QueryResult> = _sqlResult.asStateFlow()

    private val _sqlHistory = MutableStateFlow<List<String>>(emptyList())
    val sqlHistory: StateFlow<List<String>> = _sqlHistory.asStateFlow()

    private val _ftsQueryInput = MutableStateFlow("")
    val ftsQueryInput: StateFlow<String> = _ftsQueryInput.asStateFlow()

    private val _ftsMatchedString = MutableStateFlow("")
    val ftsMatchedString: StateFlow<String> = _ftsMatchedString.asStateFlow()

    private val _ftsResults = MutableStateFlow<List<List<String>>>(emptyList())
    val ftsResults: StateFlow<List<List<String>>> = _ftsResults.asStateFlow()

    private val _selectedDocId = MutableStateFlow<Long?>(null)
    val selectedDocId: StateFlow<Long?> = _selectedDocId.asStateFlow()

    private val _docDetailRows = MutableStateFlow<Map<String, List<List<String>>>>(emptyMap())
    val docDetailRows: StateFlow<Map<String, List<List<String>>>> = _docDetailRows.asStateFlow()

    fun loadHealthAndTables(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DocsyDatabase.getInstance(context)
            val sqliteDb = db.openHelper.readableDatabase

            var sqliteVer = "Unknown"
            try {
                val cursor = sqliteDb.query("SELECT sqlite_version()")
                if (cursor.moveToFirst()) sqliteVer = cursor.getString(0)
                cursor.close()
            } catch (_: Exception) {}

            val dbFile = context.getDatabasePath("docsy_knowledge.db")
            val dbSize = if (dbFile.exists()) dbFile.length() else 0L

            _healthInfo.value = DbHealthInfo(
                sqliteVersion = sqliteVer,
                fts5ProbePassed = Fts5SearchEngine.isFts5Supported,
                isBundledDriver = true,
                schemaVersion = 5,
                dbSizeBytes = dbSize,
                lastError = Fts5SearchEngine.lastProbeError
            )

            refreshTableList(context)
        }
    }

    fun toggleShowShadowTables(context: Context, show: Boolean) {
        _showShadowTables.value = show
        refreshTableList(context)
    }

    fun refreshTableList(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DocsyDatabase.getInstance(context).openHelper.readableDatabase
            val tableList = mutableListOf<TableMeta>()

            val sql = "SELECT name, type, sql FROM sqlite_master WHERE type IN ('table', 'view', 'index') ORDER BY name ASC"
            try {
                val cursor = db.query(sql)
                cursor.use {
                    while (it.moveToNext()) {
                        val name = it.getString(0)
                        val type = it.getString(1)
                        val createSql = it.getString(2) ?: ""

                        val isVirtual = createSql.lowercase(Locale.ROOT).contains("using fts")
                        val isShadow = name.endsWith("_content") || name.endsWith("_segments") ||
                                name.endsWith("_stat") || name.endsWith("_docsize") || name.endsWith("_idx") || name.startsWith("sqlite_")

                        var count = 0L
                        if (!isShadow || _showShadowTables.value) {
                            if (type == "table" || type == "view") {
                                try {
                                    val cCursor = db.query("SELECT COUNT(*) FROM \"$name\"")
                                    if (cCursor.moveToFirst()) count = cCursor.getLong(0)
                                    cCursor.close()
                                } catch (_: Exception) {}
                            }

                            tableList.add(
                                TableMeta(
                                    name = name,
                                    type = type,
                                    isVirtual = isVirtual,
                                    isShadow = isShadow,
                                    rowCount = count
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            _tables.value = tableList
        }
    }

    fun selectTable(context: Context, tableName: String) {
        if (tableName.isBlank()) {
            _selectedTable.value = null
            return
        }
        _selectedTable.value = tableName
        _currentPage.value = 0
        _sortColumn.value = null
        _sortAscending.value = true
        _filterQuery.value = ""
        loadTableSchema(context, tableName)
        loadTableDataPage(context, tableName, 0)
    }

    private fun loadTableSchema(context: Context, tableName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DocsyDatabase.getInstance(context).openHelper.readableDatabase
            val cols = mutableListOf<TableSchemaCol>()
            var createSql = ""

            try {
                val sqlCursor = db.query("SELECT sql FROM sqlite_master WHERE name = ?", arrayOf(tableName))
                if (sqlCursor.moveToFirst()) {
                    createSql = sqlCursor.getString(0) ?: ""
                }
                sqlCursor.close()

                val cursor = db.query("PRAGMA table_info(\"$tableName\")")
                cursor.use {
                    while (it.moveToNext()) {
                        cols.add(
                            TableSchemaCol(
                                cid = it.getInt(0),
                                name = it.getString(1),
                                type = it.getString(2),
                                notNull = it.getInt(3) == 1,
                                defaultValue = it.getString(4),
                                isPk = it.getInt(5) == 1
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
            _tableCreateSql.value = createSql
            _tableSchema.value = cols
        }
    }

    fun setSort(context: Context, column: String) {
        if (_sortColumn.value == column) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortColumn.value = column
            _sortAscending.value = true
        }
        _selectedTable.value?.let { loadTableDataPage(context, it, _currentPage.value) }
    }

    fun setFilter(context: Context, filter: String) {
        _filterQuery.value = filter
        _currentPage.value = 0
        _selectedTable.value?.let { loadTableDataPage(context, it, 0) }
    }

    fun loadTableDataPage(context: Context, tableName: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DocsyDatabase.getInstance(context).openHelper.readableDatabase
            val pageSize = 50
            val offset = page * pageSize

            val filter = _filterQuery.value.trim()
            val sortCol = _sortColumn.value
            val asc = _sortAscending.value

            val rowsList = mutableListOf<List<String>>()
            val colNames = mutableListOf<String>()

            // Get columns
            try {
                val pragmaCursor = db.query("PRAGMA table_info(\"$tableName\")")
                pragmaCursor.use {
                    while (it.moveToNext()) {
                        colNames.add(it.getString(1))
                    }
                }
            } catch (_: Exception) {}

            var whereClause = ""
            val bindArgs = mutableListOf<String>()
            if (filter.isNotBlank() && colNames.isNotEmpty()) {
                val conditions = colNames.joinToString(" OR ") { "\"$it\" LIKE ?" }
                whereClause = " WHERE $conditions"
                colNames.forEach { _ -> bindArgs.add("%$filter%") }
            }

            var totalRows = 0L
            try {
                val countSql = "SELECT COUNT(*) FROM \"$tableName\"$whereClause"
                val countCursor = db.query(countSql, bindArgs.toTypedArray())
                if (countCursor.moveToFirst()) totalRows = countCursor.getLong(0)
                countCursor.close()
            } catch (_: Exception) {}
            _totalRowsInTable.value = totalRows

            var orderClause = ""
            if (!sortCol.isNullOrBlank()) {
                orderClause = " ORDER BY \"$sortCol\" ${if (asc) "ASC" else "DESC"}"
            }

            val querySql = "SELECT * FROM \"$tableName\"$whereClause$orderClause LIMIT ? OFFSET ?"
            val finalArgs = (bindArgs + listOf(pageSize.toString(), offset.toString())).toTypedArray()

            try {
                val cursor = db.query(querySql, finalArgs)
                cursor.use {
                    while (it.moveToNext()) {
                        val row = mutableListOf<String>()
                        for (i in 0 until it.columnCount) {
                            if (it.isNull(i)) {
                                row.add("[NULL]")
                            } else {
                                val type = it.getType(i)
                                if (type == android.database.Cursor.FIELD_TYPE_BLOB) {
                                    val blob = it.getBlob(i)
                                    val hex = blob.take(8).joinToString("") { b -> "%02X".format(b) }
                                    row.add("[BLOB ${blob.size}B: 0x$hex...]")
                                } else {
                                    row.add(it.getString(i))
                                }
                            }
                        }
                        rowsList.add(row)
                    }
                }
            } catch (_: Exception) {}

            _tableColumns.value = colNames
            _tableRows.value = rowsList
            _currentPage.value = page
        }
    }

    fun isWriteStatement(sql: String): Pair<Boolean, String?> {
        val trimmed = sql.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val forbidden = listOf("insert", "update", "delete", "drop", "alter", "create", "replace", "attach", "vacuum")
        for (kw in forbidden) {
            if (lower.startsWith(kw) || lower.contains(" $kw ")) {
                return Pair(true, kw)
            }
        }
        return Pair(false, null)
    }

    fun executeReadOnlySql(context: Context, userSql: String) {
        val (isWrite, forbiddenKw) = isWriteStatement(userSql)
        if (isWrite) {
            _sqlResult.value = QueryResult(errorMessage = "Write statement rejected: '$forbiddenKw' is not permitted in read-only SQL console.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val db = DocsyDatabase.getInstance(context).openHelper.readableDatabase

            val colNames = mutableListOf<String>()
            val rows = mutableListOf<List<String>>()

            try {
                val cursor = db.query(userSql.trim(), emptyArray())
                cursor.use {
                    for (i in 0 until it.columnCount) {
                        colNames.add(it.getColumnName(i))
                    }
                    while (it.moveToNext()) {
                        val row = mutableListOf<String>()
                        for (i in 0 until it.columnCount) {
                            row.add(if (it.isNull(i)) "[NULL]" else it.getString(i))
                        }
                        rows.add(row)
                    }
                }
                val duration = System.currentTimeMillis() - startTime
                _sqlResult.value = QueryResult(columns = colNames, rows = rows, executionTimeMs = duration)
                _sqlHistory.value = (_sqlHistory.value + userSql.trim()).takeLast(10)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                _sqlResult.value = QueryResult(executionTimeMs = duration, errorMessage = e.message ?: "SQL execution failed")
            }
        }
    }

    fun testFtsQuery(context: Context, queryText: String) {
        _ftsQueryInput.value = queryText
        viewModelScope.launch(Dispatchers.IO) {
            val db = DocsyDatabase.getInstance(context)
            val engine = Fts5SearchEngine(db)

            val tokens = queryText.split(Regex("[^\\p{L}\\p{N}]+"))
                .map { Fts5SearchEngine.normalizeToken(it) }
                .filter { it.isNotBlank() }

            val ftsMatch = tokens.joinToString(" AND ") { Fts5SearchEngine.quoteFtsToken(it) }
            _ftsMatchedString.value = ftsMatch

            val hits = engine.searchFreeText(queryText)
            val rows = hits.map {
                listOf(
                    "Doc #${it.documentId}",
                    "Chunk #${it.chunkId}",
                    "Page ${it.page}",
                    "Score: %.3f".format(it.score),
                    it.text
                )
            }
            _ftsResults.value = rows
        }
    }

    fun inspectDocument(context: Context, docId: Long) {
        _selectedDocId.value = docId
        viewModelScope.launch(Dispatchers.IO) {
            val db = DocsyDatabase.getInstance(context).openHelper.readableDatabase
            val detailsMap = mutableMapOf<String, List<List<String>>>()

            // 1. Documents row
            val docCursor = db.query("SELECT * FROM documents WHERE id = ?", arrayOf(docId.toString()))
            docCursor.use {
                val row = mutableListOf<String>()
                if (it.moveToFirst()) {
                    for (i in 0 until it.columnCount) {
                        row.add("${it.getColumnName(i)}: ${if (it.isNull(i)) "[NULL]" else it.getString(i)}")
                    }
                }
                detailsMap["documents"] = if (row.isNotEmpty()) listOf(row) else emptyList()
            }

            // 2. Chunks
            val chunkCursor = db.query("SELECT id, page, kind, text FROM chunks WHERE documentId = ?", arrayOf(docId.toString()))
            chunkCursor.use {
                val chunkRows = mutableListOf<List<String>>()
                while (it.moveToNext()) {
                    chunkRows.add(listOf("Chunk #${it.getLong(0)}", "P.${it.getInt(1)}", it.getString(2), it.getString(3)))
                }
                detailsMap["chunks"] = chunkRows
            }

            // 3. Facts
            val factCursor = db.query("SELECT key, value, confidence FROM doc_facts WHERE documentId = ?", arrayOf(docId.toString()))
            factCursor.use {
                val factRows = mutableListOf<List<String>>()
                while (it.moveToNext()) {
                    factRows.add(listOf(it.getString(0), it.getString(1), "${(it.getFloat(2)*100).toInt()}%"))
                }
                detailsMap["doc_facts"] = factRows
            }

            // 4. JSON
            val jsonCursor = db.query("SELECT json FROM doc_json WHERE documentId = ?", arrayOf(docId.toString()))
            jsonCursor.use {
                if (it.moveToFirst()) {
                    detailsMap["doc_json"] = listOf(listOf(it.getString(0)))
                }
            }

            // 5. Layout Size
            val layoutCursor = db.query("SELECT blob FROM doc_layout WHERE documentId = ?", arrayOf(docId.toString()))
            layoutCursor.use {
                if (it.moveToFirst()) {
                    val blob = it.getBlob(0)
                    detailsMap["doc_layout"] = listOf(listOf("Blob size: ${blob.size} bytes"))
                }
            }

            _docDetailRows.value = detailsMap
        }
    }
}
