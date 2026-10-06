package com.suhrud.docsy.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.data.model.ChatMessage
import com.suhrud.docsy.ui.DocsyViewModel
import com.suhrud.docsy.ui.components.AvatarView
import com.suhrud.docsy.ui.components.DocsyArrowRightIcon
import com.suhrud.docsy.ui.components.ProfileAvatar
import com.suhrud.docsy.ui.components.DocsyLockIcon
import com.suhrud.docsy.ui.components.DocsyLockOpenIcon
import com.suhrud.docsy.ui.components.DocsyOpenIcon
import com.suhrud.docsy.ui.components.DocsySearchIcon
import com.suhrud.docsy.ui.components.ScheduleTable
import com.suhrud.docsy.ui.theme.BorderLight
import com.suhrud.docsy.ui.theme.LightSurface
import com.suhrud.docsy.ui.theme.OffWhite
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextMuted
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SimpleHomeScreen(
    viewModel: DocsyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val userName by viewModel.userName.collectAsState()
    val avatarId by viewModel.profileAvatarId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val greeting = remember { viewModel.getTimeGreeting() }
    val displayName = if (userName.isNotBlank()) userName else "there"

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size, isSearching) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    val onPerformSubmit: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        viewModel.submitQuery()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: "New chat" action if thread is active, and Profile Avatar on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chatHistory.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
                            .background(PureWhite)
                            .clickable { viewModel.startNewChat() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("btn_new_chat"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ New chat",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, BorderLight, CircleShape)
                        .background(PureWhite)
                        .clickable { viewModel.openSettings() }
                        .testTag("top_profile_icon_button"),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatar(
                        avatarId = avatarId,
                        size = 38.dp
                    )
                }
            }

            // CENTER: Minimal Home OR Conversational Chat Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (chatHistory.isEmpty() && !isSearching) {
                    // Minimal Empty Home View
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$greeting,\n$displayName.",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 40.sp,
                                letterSpacing = (-0.5).sp,
                                textAlign = TextAlign.Center
                            ),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Ask anything...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                } else {
                    // Conversational Chat Stream View
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(chatHistory, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                viewModel = viewModel,
                                context = context
                            )
                        }

                        if (isSearching) {
                            item {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    MinimalFindingIndicator()
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM AREA: Soft Rounded Search Input Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(26.dp))
                        .background(PureWhite)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocsySearchIcon(tint = TextMuted, size = 18.dp)
                        Spacer(modifier = Modifier.width(12.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (chatHistory.isNotEmpty()) "Ask a follow-up..." else "Ask anything...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextMuted
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                                cursorBrush = SolidColor(TextPrimary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onPerformSubmit() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("simple_search_input")
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(LightSurface)
                                    .clickable { onPerformSubmit() }
                                    .testTag("simple_search_arrow"),
                                contentAlignment = Alignment.Center
                            ) {
                                DocsyArrowRightIcon(tint = TextPrimary, size = 16.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "DOCSY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = TextMuted
                    )
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    viewModel: DocsyViewModel,
    context: android.content.Context
) {
    if (message.isUser) {
        // USER BUBBLE (Right-aligned, soft rounded container)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp))
                    .background(PureWhite)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary)
                )
            }
        }
    } else {
        // DOCSY BUBBLE (Left-aligned, soft rounded container with embedded answer card)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp))
                    .background(LightSurface)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Column {
                    // Docsy natural message text
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary)
                    )

                    // Hero Answer Highlight Card if present
                    if (message.answerHighlight != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
                                .background(PureWhite)
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = message.answerHighlight,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                ),
                                color = TextPrimary
                            )
                        }
                    }

                    if (message.supportingMetadata != null && (message.answerHighlight != null || message.scheduleItems != null)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message.supportingMetadata,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                    }

                    if (message.subtext != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = message.subtext,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    if (message.isSensitive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, TextPrimary, RoundedCornerShape(16.dp))
                                .background(PureWhite)
                                .clickable { viewModel.toggleRevealSensitive() }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("btn_tap_to_reveal")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (message.isRevealed) {
                                    DocsyLockOpenIcon(size = 12.dp, tint = TextPrimary)
                                } else {
                                    DocsyLockIcon(size = 12.dp, tint = TextPrimary)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (message.isRevealed) "Hide" else "Tap to reveal",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    if (message.scheduleItems != null && message.scheduleItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ScheduleTable(items = message.scheduleItems)
                    }

                    if (message.sourceDocument != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                                .background(PureWhite)
                                .clickable {
                                    val uri = message.sourceDocument.pathUri
                                    if (uri.isNotBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(uri), message.sourceDocument.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("source_doc_citation")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SOURCE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                                        color = TextMuted
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = message.sourceDocument.fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = TextPrimary
                                    )
                                }
                                DocsyOpenIcon(tint = TextSecondary, size = 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalFindingIndicator() {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(320)
            dotCount = (dotCount % 3) + 1
        }
    }

    val dots = ".".repeat(dotCount)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Thinking$dots",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        )
    }
}
