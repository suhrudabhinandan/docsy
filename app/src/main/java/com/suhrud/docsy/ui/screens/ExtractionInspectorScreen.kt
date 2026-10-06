package com.suhrud.docsy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.BuildConfig
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.ui.DocsyViewModel
import com.suhrud.docsy.ui.components.DocsyCloseIcon
import com.suhrud.docsy.ui.components.DocsySearchIcon
import com.suhrud.docsy.ui.theme.BorderLight
import com.suhrud.docsy.ui.theme.LightSurface
import com.suhrud.docsy.ui.theme.OffWhite
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextMuted
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ExtractionInspectorScreen(
    viewModel: DocsyViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    val allDocs by viewModel.allDocuments.collectAsState(initial = emptyList())
    var filterQuery by remember { mutableStateOf("") }

    val filteredDocs = remember(allDocs, filterQuery) {
        val q = filterQuery.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) allDocs else {
            allDocs.filter { doc ->
                doc.fileName.lowercase(Locale.ROOT).contains(q) ||
                doc.pathUri.lowercase(Locale.ROOT).contains(q) ||
                doc.extractedText.lowercase(Locale.ROOT).contains(q) ||
                doc.documentType.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Extraction Inspector (DEBUG)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${filteredDocs.size} / ${allDocs.size} documents indexed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                IconButton(onClick = onClose) {
                    DocsyCloseIcon(tint = TextPrimary, size = 18.dp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .background(PureWhite)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DocsySearchIcon(tint = TextMuted, size = 16.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (filterQuery.isEmpty()) {
                            Text(text = "Search by content or file name...", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                        BasicTextField(
                            value = filterQuery,
                            onValueChange = { filterQuery = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            cursorBrush = SolidColor(TextPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDocs, key = { it.id }) { doc ->
                    InspectorDocCard(doc = doc)
                }
            }
        }
    }
}

@Composable
fun InspectorDocCard(doc: DocumentEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
            .background(PureWhite)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doc.fileName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            when (doc.indexStatus) {
                                "EXTRACTED", "INDEXED" -> LightSurface
                                "EMPTY" -> BorderLight
                                else -> OffWhite
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = doc.indexStatus,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Type: ${doc.documentType} · Chars: ${doc.extractedText.length} · Version: v${doc.extractorVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (!doc.ownerName.isNullOrBlank()) {
                Text(
                    text = "Owner Found: ${doc.ownerName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }

            if (!doc.failureReason.isNullOrBlank()) {
                Text(
                    text = "Failure Reason: ${doc.failureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            if (doc.extractedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                val preview = doc.extractedText.take(500)
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = TextMuted
                )
            }
        }
    }
}
