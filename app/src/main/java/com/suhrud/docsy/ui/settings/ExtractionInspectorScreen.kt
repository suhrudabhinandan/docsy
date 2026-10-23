package com.suhrud.docsy.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.RecordEntity
import com.suhrud.docsy.data.db.entity.SubjectEntity
import com.suhrud.docsy.scanner.FileSection
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionInspectorScreen(
    onBack: () -> Unit,
    viewModel: ExtractionInspectorViewModel = viewModel(factory = ExtractionInspectorViewModel.Factory)
) {
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
    val documentCards by viewModel.documentCards.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedDocDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    if (selectedDetail != null) {
        BackHandler { viewModel.closeDocumentDetail() }
        DocumentCardDetailScreen(
            detail = selectedDetail!!,
            onBack = { viewModel.closeDocumentDetail() },
            onUpdateField = { fieldId, newValue -> viewModel.updateFieldValue(fieldId, newValue) },
            onReclassify = { docId, newType -> viewModel.reclassifyDocument(docId, newType) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extraction Inspector", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("inspector_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAllData() },
                        modifier = Modifier.testTag("inspector_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("extraction_inspector_screen")
        ) {
            // Horizontal Scrollable Section Tabs
            ScrollableTabRow(
                selectedTabIndex = FileSection.ALL_SECTIONS.indexOf(selectedSection).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                FileSection.ALL_SECTIONS.forEach { sec ->
                    val count = categoryCounts[sec] ?: 0
                    Tab(
                        selected = selectedSection == sec,
                        onClick = { viewModel.selectSection(sec) },
                        text = {
                            Text(
                                text = "${sec.shortLabel} ($count)",
                                fontWeight = if (selectedSection == sec) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("inspector_search_input"),
                placeholder = { Text("Search by extracted content...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Status Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All", "DONE" to "Done", "NEEDS_REVIEW" to "Needs Review", "FAILED" to "Failed").forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { viewModel.setStatusFilter(key) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Document Cards List / Empty / Error State
            when {
                isLoading && documentCards.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null && documentCards.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refreshAllData() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                documentCards.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No files found in ${selectedSection.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(documentCards, key = { it.doc.id }) { item ->
                            InspectorDocumentCardRow(
                                item = item,
                                onClick = { viewModel.openDocumentDetail(item.doc.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorDocumentCardRow(
    item: DocumentCardItem,
    onClick: () -> Unit
) {
    val doc = item.doc
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Icon + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getSectionIcon(doc.fileSection),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = doc.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle Row: Method & Character Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Method: ${doc.extractionMethod ?: "Native"} • ${item.charCount} chars",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "State: ${doc.state}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (doc.state == "FAILED") Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Preview Line of 2 Key Fields
            if (item.fieldsPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val previewStr = item.fieldsPreview.joinToString(" • ") { "${it.label}: ${it.value}" }
                Text(
                    text = previewStr,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Banner if text extracted but no fields
            if (item.charCount > 0 && item.fieldsPreview.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "Text extracted, no fields found",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// PART 3: DOCUMENT CARD DETAIL SCREEN
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DocumentCardDetailScreen(
    detail: DocumentDetailState,
    onBack: () -> Unit,
    onUpdateField: (Long, String) -> Unit,
    onReclassify: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val doc = detail.doc
    var showReclassifyDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<FieldEntity?>(null) }
    var rawTextExpanded by remember { mutableStateOf(false) }
    var rawTextSearchQuery by remember { mutableStateOf("") }
    var revealedSensitiveFields = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${doc.rawFileName} (${doc.path})",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("document_card_detail_screen")
        ) {
            if (!doc.failureReason.isNullOrBlank()) {
                Text("Error: ${doc.failureReason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Extracted Information Table
            Text(
                text = "Extracted Information (Structured Table)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExtractedInfoStructuredTable(
                docType = doc.docType ?: "UNKNOWN",
                fields = detail.fields,
                revealedFields = revealedSensitiveFields,
                onToggleMask = { fieldId ->
                    val isRevealed = revealedSensitiveFields[fieldId] == true
                    revealedSensitiveFields[fieldId] = !isRevealed
                },
                onEditField = { field -> editingField = field }
            )

            // 5. Subjects Table (Marksheets)
            if (detail.subjects.isNotEmpty() || doc.docType == "MARKSHEET") {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Subjects Table & Marks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                SubjectsTableSection(subjects = detail.subjects, printedPercentage = detail.fields.find { it.key.contains("percentage") }?.value)
            }

            // 6. Records Grid (Spreadsheets)
            if (detail.records.isNotEmpty() || detail.sheetMeta.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Records Grid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                RecordsGridSection(sheetMeta = detail.sheetMeta, records = detail.records)
            }

            // 7. Raw Text Collapsed Section
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                onClick = { rawTextExpanded = !rawTextExpanded },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Text (${detail.lines.size} lines)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Icon(if (rawTextExpanded) Icons.Default.Close else Icons.Default.ChevronRight, contentDescription = null)
                    }

                    if (rawTextExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = rawTextSearchQuery,
                            onValueChange = { rawTextSearchQuery = it },
                            placeholder = { Text("Search text...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val filteredLines = if (rawTextSearchQuery.isBlank()) detail.lines
                        else detail.lines.filter { it.text.contains(rawTextSearchQuery, ignoreCase = true) }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            filteredLines.take(100).forEach { line ->
                                Text(
                                    text = "[P${line.page}:L${line.lineNo}] ${line.text}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 8. Footer Actions
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { Toast.makeText(context, "Re-reading triggered...", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-read", fontSize = 12.sp)
                }

                OutlinedButton(onClick = {
                    copyToClipboard(context, buildJsonExport(doc, detail.fields))
                    Toast.makeText(context, "Copied as JSON", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy JSON", fontSize = 12.sp)
                }

                OutlinedButton(onClick = {
                    copyToClipboard(context, buildCsvExport(detail.fields))
                    Toast.makeText(context, "Copied as CSV", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy CSV", fontSize = 12.sp)
                }

                OutlinedButton(onClick = {
                    copyToClipboard(context, "DOCSY_DEBUG_EXPORT:${doc.displayName}\n${detail.fullText}")
                    Toast.makeText(context, "Shared debug record", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }
            }
        }
    }

    // Reclassify Dialog
    if (showReclassifyDialog) {
        val categories = listOf("ELECTRICITY_BILL", "IDENTITY_CARD", "ADMIT_CARD", "MARKSHEET", "SPREADSHEET", "NOTE", "UNKNOWN")
        AlertDialog(
            onDismissRequest = { showReclassifyDialog = false },
            title = { Text("Reclassify Document") },
            text = {
                Column {
                    categories.forEach { cat ->
                        TextButton(
                            onClick = {
                                onReclassify(doc.id, cat)
                                showReclassifyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(cat)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReclassifyDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Field Dialog
    editingField?.let { field ->
        var editedVal by remember { mutableStateOf(field.value) }
        AlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text("Fix Field: ${field.label}") },
            text = {
                OutlinedTextField(
                    value = editedVal,
                    onValueChange = { editedVal = it },
                    label = { Text("Value") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateField(field.id, editedVal)
                    editingField = null
                }) {
                    Text("Save (Fix)")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtractedInfoStructuredTable(
    docType: String,
    fields: List<FieldEntity>,
    revealedFields: Map<Long, Boolean>,
    onToggleMask: (Long) -> Unit,
    onEditField: (FieldEntity) -> Unit
) {
    val context = LocalContext.current
    var activeWarningDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("Field", modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Extracted Value", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        // Dynamic Field Rows
        fields.forEachIndexed { index, field ->
            val isZebra = index % 2 == 1
            val rowBg = if (isZebra) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
            val isRevealed = revealedFields[field.id] == true

            val displayVal = if (field.sensitive && !isRevealed) {
                maskValue(field.value)
            } else {
                field.value
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onEditField(field) }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label
                Row(modifier = Modifier.weight(0.4f), verticalAlignment = Alignment.CenterVertically) {
                    Text(field.label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    if (field.userEdited) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(edited)", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Value with mask toggle / warning icon
                Row(modifier = Modifier.weight(0.6f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayVal,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )

                    if (field.sensitive) {
                        IconButton(
                            onClick = { onToggleMask(field.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Mask",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!field.validationWarning.isNullOrBlank()) {
                        IconButton(
                            onClick = { activeWarningDialog = field.validationWarning },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFF57C00),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
    }

    activeWarningDialog?.let { warnMsg ->
        AlertDialog(
            onDismissRequest = { activeWarningDialog = null },
            title = { Text("Validation Warning") },
            text = { Text(warnMsg) },
            confirmButton = { TextButton(onClick = { activeWarningDialog = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun SubjectsTableSection(subjects: List<SubjectEntity>, printedPercentage: String?) {
    val totalObtained = subjects.mapNotNull { it.marks?.toIntOrNull() }.sum()
    val totalMax = subjects.mapNotNull { it.maxMarks?.toIntOrNull() }.sum()
    val calculatedPct = if (totalMax > 0) (totalObtained.toDouble() / totalMax) * 100 else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
        ) {
            Text("Subject", modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Marks", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Max", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Grade", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        subjects.forEach { sub ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(sub.name, modifier = Modifier.weight(0.4f), fontSize = 11.sp)
                Text(sub.marks ?: "-", modifier = Modifier.weight(0.2f), fontSize = 11.sp)
                Text(sub.maxMarks ?: "-", modifier = Modifier.weight(0.2f), fontSize = 11.sp)
                Text(sub.grade ?: "-", modifier = Modifier.weight(0.2f), fontSize = 11.sp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }

        // Footer Total
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Text("Total", modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("$totalObtained", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("$totalMax", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("-", modifier = Modifier.weight(0.2f), fontSize = 11.sp)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // After Calculation Box
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("After Calculation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Percentage: ${"%.2f".format(calculatedPct)}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Working: $totalObtained out of $totalMax = ${"%.2f".format(calculatedPct)}%", fontSize = 11.sp)

            if (!printedPercentage.isNullOrBlank()) {
                val printedVal = printedPercentage.removeSuffix("%").toDoubleOrNull() ?: 0.0
                if (kotlin.math.abs(printedVal - calculatedPct) > 0.1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Warning: Printed percentage ($printedPercentage) mismatches calculated percentage (${"%.2f".format(calculatedPct)}%)",
                        fontSize = 10.sp,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsGridSection(sheetMeta: List<com.suhrud.docsy.data.db.entity.SheetMetaEntity>, records: List<RecordEntity>) {
    sheetMeta.forEach { meta ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                meta.title?.let { Text("Title: $it", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                meta.institution?.let { Text("Institution: $it", fontSize = 11.sp) }
                meta.batch?.let { Text("Batch: $it", fontSize = 11.sp) }
            }
        }
    }

    val groupedByRow = records.groupBy { it.rowNo }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        groupedByRow.entries.take(100).forEach { entry ->
            val rowNo = entry.key
            val rowCells = entry.value
            Row(modifier = Modifier.padding(6.dp)) {
                Text("Row $rowNo |", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                rowCells.forEach { cell ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${cell.colKey ?: "C${cell.colIndex}"}: ${cell.value}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
    }
}

@Composable
private fun ChipPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun maskValue(valStr: String): String {
    val clean = valStr.replace(" ", "")
    if (clean.length <= 4) return "****"
    val last4 = clean.takeLast(4)
    return "****" + last4
}

private fun getSectionIcon(section: FileSection): ImageVector {
    return when (section) {
        FileSection.PDF -> Icons.Default.Description
        FileSection.WORD -> Icons.Default.Description
        FileSection.EXCEL -> Icons.Default.TableChart
        FileSection.IMAGES -> Icons.Default.Image
        FileSection.CODE_WEB -> Icons.Default.Code
        FileSection.ARCHIVES -> Icons.Default.FolderZip
        FileSection.SMS -> Icons.AutoMirrored.Filled.Message
        else -> Icons.Default.Description
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Docsy Export", text)
    clipboard.setPrimaryClip(clip)
}

private fun buildJsonExport(doc: com.suhrud.docsy.data.db.entity.DocumentEntity, fields: List<FieldEntity>): String {
    val sb = StringBuilder()
    sb.append("{\n")
    sb.append("  \"title\": \"${doc.displayName}\",\n")
    sb.append("  \"docType\": \"${doc.docType}\",\n")
    sb.append("  \"fields\": {\n")
    fields.forEachIndexed { idx, f ->
        sb.append("    \"${f.key}\": \"${f.value}\"${if (idx < fields.size - 1) "," else ""}\n")
    }
    sb.append("  }\n")
    sb.append("}")
    return sb.toString()
}

private fun buildCsvExport(fields: List<FieldEntity>): String {
    val sb = StringBuilder()
    sb.append("Key,Label,Value,Type,Confidence\n")
    fields.forEach { f ->
        sb.append("\"${f.key}\",\"${f.label}\",\"${f.value}\",\"${f.valueType}\",${f.confidence}\n")
    }
    return sb.toString()
}
