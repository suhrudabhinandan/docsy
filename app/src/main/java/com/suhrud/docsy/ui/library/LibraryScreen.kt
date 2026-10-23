package com.suhrud.docsy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.scanner.ContentSectionDetector
import com.suhrud.docsy.scanner.FileSection

enum class LibrarySortOption {
    NAME, SIZE, DATE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    documentDao: DocumentDao,
    onBack: () -> Unit = {}
) {
    val documents by documentDao.getAllDocuments().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSection by remember { mutableStateOf<FileSection?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }

    val sectionStats = remember(documents) {
        FileSection.ALL_SECTIONS.associateWith { sec ->
            val docs = documents.filter { doc ->
                val detected = ContentSectionDetector.detectSection(java.io.File(doc.path))
                doc.section.equals(sec.id, ignoreCase = true) || detected == sec
            }
            val count = docs.size
            val totalSize = docs.sumOf { it.size }
            val hasFailed = docs.any { it.status == DocumentStatus.FAILED }
            val hasReading = docs.any { it.status == DocumentStatus.READING || it.status == DocumentStatus.DISCOVERED }
            val chipState = when {
                count == 0 -> "done"
                hasReading -> "reading"
                hasFailed -> "failed"
                else -> "done"
            }
            Triple(count, totalSize, chipState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedSection == null) "Library" else selectedSection!!.displayName,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (selectedSection != null) {
                        IconButton(
                            onClick = { selectedSection = null },
                            modifier = Modifier.testTag("library_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sections")
                        }
                    } else if (onBack != {}) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("library_root_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (selectedSection == null) {
            // Main 9 Section Cards Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .testTag("library_section_grid"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(FileSection.ALL_SECTIONS, key = { it.id }) { section ->
                    val (count, totalSize, chipState) = sectionStats[section] ?: Triple(0, 0L, "done")
                    LibrarySectionCard(
                        section = section,
                        count = count,
                        totalSizeBytes = totalSize,
                        chipState = chipState,
                        onClick = { selectedSection = section }
                    )
                }
            }
        } else {
            // Section Document List with Sort and Search by content
            val currentSection = selectedSection!!
            val sectionDocs = remember(documents, currentSection, searchQuery, sortOption) {
                documents.filter { doc ->
                    val detected = ContentSectionDetector.detectSection(java.io.File(doc.path))
                    (doc.section.equals(currentSection.id, ignoreCase = true) || detected == currentSection) &&
                            (searchQuery.isBlank() || doc.displayName.contains(searchQuery, true) || doc.extractedText?.contains(searchQuery, true) == true)
                }.let { list ->
                    when (sortOption) {
                        LibrarySortOption.NAME -> list.sortedBy { it.displayName.lowercase() }
                        LibrarySortOption.SIZE -> list.sortedByDescending { it.size }
                        LibrarySortOption.DATE -> list.sortedByDescending { it.modified }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .testTag("library_section_detail")
            ) {
                // Search Input Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("library_search_field"),
                    placeholder = { Text("Search ${currentSection.displayName} by content...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Sort buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = {
                        sortOption = when (sortOption) {
                            LibrarySortOption.NAME -> LibrarySortOption.SIZE
                            LibrarySortOption.SIZE -> LibrarySortOption.DATE
                            LibrarySortOption.DATE -> LibrarySortOption.NAME
                        }
                    }) {
                        Text("Sort: ${sortOption.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    }
                }

                Text(
                    text = "${sectionDocs.size} files in ${currentSection.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sectionDocs, key = { it.id }) { doc ->
                        LibraryDocumentCard(doc = doc)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionCard(
    section: FileSection,
    count: Int,
    totalSizeBytes: Long,
    chipState: String,
    onClick: () -> Unit
) {
    val formattedSize = formatSize(totalSizeBytes)
    val icon = getSectionIcon(section)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("library_section_card_${section.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                StateChip(state = chipState)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = section.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$count files • $formattedSize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LibraryDocumentCard(doc: DocumentEntity) {
    val preview = doc.extractedText?.take(100)?.replace('\n', ' ') ?: "No preview available"
    val sizeStr = formatSize(doc.size)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_doc_card_${doc.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doc.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sizeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StateChip(state: String) {
    val (color, text) = when (state.lowercase()) {
        "reading" -> MaterialTheme.colorScheme.tertiary to "reading"
        "failed" -> MaterialTheme.colorScheme.error to "failed"
        else -> MaterialTheme.colorScheme.primary to "done"
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun getSectionIcon(section: FileSection): ImageVector {
    return when (section) {
        FileSection.PDF -> Icons.Default.Description
        FileSection.WORD -> Icons.Default.Description
        FileSection.EXCEL -> Icons.Default.TableChart
        FileSection.IMAGES -> Icons.Default.Image
        FileSection.VIDEOS -> Icons.Default.VideoLibrary
        FileSection.AUDIOS -> Icons.Default.AudioFile
        FileSection.ARCHIVES -> Icons.Default.FolderZip
        FileSection.APPS -> Icons.Default.Android
        FileSection.CODE_WEB -> Icons.Default.Code
        FileSection.SMS -> Icons.AutoMirrored.Filled.Message
        FileSection.OTHER -> Icons.Default.Folder
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
