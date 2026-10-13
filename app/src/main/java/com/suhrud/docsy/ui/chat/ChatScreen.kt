package com.suhrud.docsy.ui.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.ChatMessageEntity
import com.suhrud.docsy.data.db.entity.ChatSessionEntity
import com.suhrud.docsy.planner.DeterministicReplyComposer
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.DocsyReply
import com.suhrud.docsy.retrieval.DocumentRetriever
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.NotFoundReplyGenerator
import com.suhrud.docsy.retrieval.ReplyType
import com.suhrud.docsy.retrieval.RouterOutcome
import com.suhrud.docsy.tools.BatteryTool
import com.suhrud.docsy.tools.DateTimeTool
import com.suhrud.docsy.tools.DisplayTool
import com.suhrud.docsy.tools.HardwareInfoTool
import com.suhrud.docsy.tools.MemoryTool
import com.suhrud.docsy.tools.NetworkTool
import com.suhrud.docsy.tools.StepCounterTool
import com.suhrud.docsy.tools.StorageStatsTool
import com.suhrud.docsy.tools.TopicDomain
import com.suhrud.docsy.ui.common.MaskedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(
    val id: Long = System.currentTimeMillis(),
    val isFromUser: Boolean,
    val text: String,
    val replyType: ReplyType = ReplyType.TEXT_ONLY,
    val disambiguationOwners: List<String> = emptyList(),
    val maskedIdValue: String? = null
)

/**
 * Chat interface adhering to minimal monochrome Nothing-inspired M3 layout with imePadding and autoscroll.
 * Satisfies Phase 14.1, 14.2, and 14.3 in AUDIT.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialQuery: String = "",
    userName: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DocsyDatabase.getInstance(context) }
    val retriever = remember { DocumentRetriever(db.documentDao(), db.factDao()) }

    var currentSessionId by remember { mutableStateOf<Long?>(null) }
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<UiMessage>() }
    val listState = rememberLazyListState()

    // Send query logic
    fun handleQuery(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return

        messages.add(UiMessage(isFromUser = true, text = clean))
        inputText = ""

        scope.launch {
            // Save to Room chat session
            val sessionDao = db.chatDao()
            var sessionId = currentSessionId
            if (sessionId == null) {
                val title = if (clean.length > 28) clean.take(28) + "..." else clean
                sessionId = sessionDao.insertSession(ChatSessionEntity(title = title))
                currentSessionId = sessionId
            }

            sessionDao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    isFromUser = true,
                    text = clean
                )
            )

            // Auto-scroll
            listState.animateScrollToItem(messages.size - 1)

            // Route query
            val decision = IntentRouter.route(clean)
            val reply: UiMessage = withContext(Dispatchers.IO) {
                when (decision.outcome) {
                    RouterOutcome.SMALL_TALK -> {
                        val text = DeterministicReplyComposer.composeSmallTalk(decision.intent, userName)
                        UiMessage(isFromUser = false, text = text)
                    }
                    RouterOutcome.OUT_OF_SCOPE -> {
                        val notFound = NotFoundReplyGenerator.generateOutOfScope(clean)
                        UiMessage(isFromUser = false, text = notFound.primaryText)
                    }
                    RouterOutcome.ANSWERABLE -> {
                        when (decision.intent) {
                            DocsyIntent.USER_NAME_QUERY -> {
                                val nameAnswer = if (userName.isNotBlank()) {
                                    "Your registered profile name is **$userName**."
                                } else {
                                    "You haven't set a profile name yet."
                                }
                                UiMessage(isFromUser = false, text = nameAnswer)
                            }
                            DocsyIntent.SYSTEM_TIME,
                            DocsyIntent.SYSTEM_DATE -> {
                                val snapshot = DateTimeTool.getSnapshot()
                                UiMessage(isFromUser = false, text = DateTimeTool.formatAnswer(snapshot))
                            }
                            DocsyIntent.BATTERY_STATUS -> {
                                val snapshot = BatteryTool.getSnapshot(context)
                                UiMessage(isFromUser = false, text = BatteryTool.formatAnswer(snapshot))
                            }
                            DocsyIntent.NETWORK_STATUS -> {
                                val snapshot = NetworkTool.getSnapshot(context)
                                UiMessage(isFromUser = false, text = NetworkTool.formatAnswer(snapshot))
                            }
                            DocsyIntent.BLUETOOTH_STATUS,
                            DocsyIntent.DEVICE_HARDWARE_SPECS -> {
                                val snapshot = HardwareInfoTool.getSnapshot(context)
                                UiMessage(isFromUser = false, text = HardwareInfoTool.formatAnswer(snapshot))
                            }
                            DocsyIntent.MEMORY_RAM_STATUS -> {
                                val snapshot = MemoryTool.getSnapshot(context)
                                UiMessage(isFromUser = false, text = MemoryTool.formatAnswer(snapshot))
                            }
                            DocsyIntent.STEP_COUNT_QUERY -> {
                                val snapshot = StepCounterTool.getSnapshot(context)
                                UiMessage(isFromUser = false, text = StepCounterTool.formatAnswer(snapshot))
                            }
                            DocsyIntent.MEDIA_CATEGORY_COUNT,
                            DocsyIntent.STORAGE_SUPERLATIVE_LARGEST,
                            DocsyIntent.STORAGE_SUPERLATIVE_SMALLEST,
                            DocsyIntent.STORAGE_SUPERLATIVE_NEWEST,
                            DocsyIntent.STORAGE_SUPERLATIVE_OLDEST -> {
                                val snapshot = StorageStatsTool.getSnapshot(db.documentDao())
                                val text = StorageStatsTool.formatSuperlativeAnswer(snapshot, clean)
                                UiMessage(isFromUser = false, text = text)
                            }
                            DocsyIntent.DOCUMENT_FACT_QUERY,
                            DocsyIntent.DOCUMENT_ID_LOOKUP -> {
                                val candidates = retriever.retrieveFact(
                                    query = clean,
                                    targetField = decision.targetField,
                                    targetCategory = decision.targetCategory,
                                    targetOwner = decision.targetOwner ?: userName
                                )
                                if (candidates.isNotEmpty()) {
                                    val top = candidates.first()
                                    val fact = top.fact
                                    if (fact != null) {
                                        val answer = DeterministicReplyComposer.composeFactAnswer(
                                            fact = fact,
                                            documentName = top.document.fileName,
                                            userQuery = clean
                                        )
                                        UiMessage(
                                            isFromUser = false,
                                            text = answer,
                                            maskedIdValue = if (decision.intent == DocsyIntent.DOCUMENT_ID_LOOKUP) fact.fieldValue else null
                                        )
                                    } else {
                                        UiMessage(
                                            isFromUser = false,
                                            text = "Found \"${top.document.fileName}\" matching your query."
                                        )
                                    }
                                } else {
                                    val total = db.documentDao().getAllDocumentsSnapshot().size
                                    val notFound = NotFoundReplyGenerator.generateNotFound(
                                        subject = decision.targetCategory ?: clean,
                                        targetField = decision.targetField,
                                        owner = userName,
                                        totalIndexedFiles = total
                                    )
                                    UiMessage(isFromUser = false, text = notFound.primaryText)
                                }
                            }
                            else -> {
                                val docs = retriever.searchDocumentsLexical(clean)
                                if (docs.isNotEmpty()) {
                                    val names = docs.take(3).joinToString("\n• ") { it.fileName }
                                    UiMessage(isFromUser = false, text = "I found the following matching documents:\n• $names")
                                } else {
                                    val total = db.documentDao().getAllDocumentsSnapshot().size
                                    val notFound = NotFoundReplyGenerator.generateNotFound(clean, totalIndexedFiles = total)
                                    UiMessage(isFromUser = false, text = notFound.primaryText)
                                }
                            }
                        }
                    }
                    RouterOutcome.UNRECOGNISED,
                    RouterOutcome.NEEDS_CLARIFICATION -> {
                        UiMessage(
                            isFromUser = false,
                            text = "I'm not quite sure what you're asking. Could you rephrase your query or specify a document name?"
                        )
                    }
                }
            }

            messages.add(reply)
            sessionDao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    isFromUser = false,
                    text = reply.text
                )
            )

            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Process initial query on launch
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && messages.isEmpty()) {
            handleQuery(initialQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DOCSY", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            currentSessionId = null
                            messages.clear()
                        },
                        modifier = Modifier.testTag("chat_new_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Message stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        onSelectOwner = { owner -> handleQuery("show document for $owner") }
                    )
                }
            }

            // Fixed input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_query_input"),
                    placeholder = { Text("Ask anything about your files or device...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { handleQuery(inputText) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    onSelectOwner: (String) -> Unit
) {
    val isUser = message.isFromUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (message.maskedIdValue != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MaskedText(fullText = message.maskedIdValue)
                }

                if (message.disambiguationOwners.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Whose document would you like to view?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.disambiguationOwners.forEach { owner ->
                            AssistChip(
                                onClick = { onSelectOwner(owner) },
                                label = { Text(owner) },
                                modifier = Modifier.testTag("owner_chip_$owner")
                            )
                        }
                    }
                }
            }
        }
    }
}
