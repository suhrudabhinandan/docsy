package com.suhrud.docsy.ui.inspector

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqliteInspectorScreen(
    onBack: () -> Unit,
    viewModel: SqliteInspectorViewModel = viewModel()
) {
    val context = LocalContext.current
    val health by viewModel.healthInfo.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val showShadow by viewModel.showShadowTables.collectAsState()
    val selectedTable by viewModel.selectedTable.collectAsState()
    val tableSchema by viewModel.tableSchema.collectAsState()
    val tableRows by viewModel.tableRows.collectAsState()
    val tableCols by viewModel.tableColumns.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalRows by viewModel.totalRowsInTable.collectAsState()
    val sqlResult by viewModel.sqlResult.collectAsState()
    val ftsMatch by viewModel.ftsMatchedString.collectAsState()
    val ftsResults by viewModel.ftsResults.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var sqlInputText by remember { mutableStateOf("SELECT name, type FROM sqlite_master;") }
    var ftsInputText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadHealthAndTables(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        // Top Health Header
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "SQLite Inspector (DEBUG)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SQLite: ${health.sqliteVersion}", fontSize = 12.sp)
                    Text("FTS5 Probe: ${if (health.fts5ProbePassed) "PASSED" else "FAILED"}", fontSize = 12.sp)
                    Text("Bundled Driver: ${health.isBundledDriver}", fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("DB Schema Ver: ${health.schemaVersion}", fontSize = 12.sp)
                    Text("DB Size: ${health.dbSizeBytes / 1024} KB", fontSize = 12.sp)
                }
            }
        }

        // Navigation Tabs
        val tabs = listOf("Tables", "SQL Console", "FTS5 Tester", "File Inspector")
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTabIndex) {
            0 -> {
                // Tables Tab
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showShadow,
                        onCheckedChange = { viewModel.toggleShowShadowTables(context, it) }
                    )
                    Text("Show FTS Shadow Tables", fontSize = 12.sp)
                }

                if (selectedTable == null) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(tables) { tbl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { viewModel.selectTable(context, tbl.name) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(tbl.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Type: ${tbl.type} | Virtual: ${tbl.isVirtual}", fontSize = 11.sp)
                                    }
                                    Text("${tbl.rowCount} rows", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Selected Table View
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { viewModel.selectTable(context, "") }) {
                            Text("< Back to Tables", fontSize = 11.sp)
                        }
                        Text("Table: ${selectedTable}", fontWeight = FontWeight.Bold)
                        Text("Total: $totalRows rows", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Column headers + Paged rows
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                                tableCols.forEach { col ->
                                    Text(
                                        text = col,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(6.dp).width(110.dp)
                                    )
                                }
                            }
                            LazyColumn(modifier = Modifier.height(260.dp)) {
                                items(tableRows) { row ->
                                    Row {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .width(110.dp)
                                            )
                                        }
                                    }
                                    Divider()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            if (currentPage > 0) viewModel.loadTableDataPage(context, selectedTable ?: "", currentPage - 1)
                        }, enabled = currentPage > 0) {
                            Text("Prev", fontSize = 11.sp)
                        }
                        Text("Page ${currentPage + 1}", fontSize = 12.sp)
                        Button(onClick = {
                            viewModel.loadTableDataPage(context, selectedTable ?: "", currentPage + 1)
                        }) {
                            Text("Next", fontSize = 11.sp)
                        }
                    }
                }
            }
            1 -> {
                // SQL Console
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = sqlInputText,
                        onValueChange = { sqlInputText = it },
                        label = { Text("Read-Only SQL Statement") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = { viewModel.executeReadOnlySql(context, sqlInputText) }) {
                        Text("Execute SQL", fontSize = 12.sp)
                    }

                    sqlResult.errorMessage?.let { err ->
                        Text("Error: $err", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Text("Exec Time: ${sqlResult.executionTimeMs} ms", fontSize = 11.sp)

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(sqlResult.rows) { row ->
                            Text(row.joinToString(" | "), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Divider()
                        }
                    }
                }
            }
            2 -> {
                // FTS5 Tester
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = ftsInputText,
                        onValueChange = { ftsInputText = it },
                        label = { Text("FTS Query Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = { viewModel.testFtsQuery(context, ftsInputText) }) {
                        Text("Run FTS Match", fontSize = 12.sp)
                    }

                    Text("Built MATCH String: $ftsMatch", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Hits: ${ftsResults.size}", fontSize = 12.sp)

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(ftsResults) { resRow ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(resRow.take(4).joinToString(" | "), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(resRow.lastOrNull() ?: "", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                // File Inspector
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Inspect Document by ID", fontWeight = FontWeight.Bold)
                    Button(onClick = { viewModel.inspectDocument(context, 1L) }) {
                        Text("Inspect Document #1", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
