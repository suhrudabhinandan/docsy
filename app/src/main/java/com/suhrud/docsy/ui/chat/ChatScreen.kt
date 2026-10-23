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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.ChatMessageEntity
import com.suhrud.docsy.data.db.entity.ChatSessionEntity
import com.suhrud.docsy.planner.DeterministicReplyComposer
import com.suhrud.docsy.retrieval.ChatMemoryManager
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.DocumentRetriever
import com.suhrud.docsy.retrieval.IntentRouter
import com.suhrud.docsy.retrieval.MarksheetLookup
import com.suhrud.docsy.retrieval.NotFoundReplyGenerator
import com.suhrud.docsy.retrieval.ReplyType
import com.suhrud.docsy.retrieval.RouterOutcome
import com.suhrud.docsy.retrieval.SmartTitleGenerator
import com.suhrud.docsy.tools.DateTimeTool
import com.suhrud.docsy.tools.PhoneSpecCardData
import com.suhrud.docsy.tools.PhoneSpecsReader
import com.suhrud.docsy.tools.StepCounterTool
import com.suhrud.docsy.tools.StorageStatsTool
import com.suhrud.docsy.ui.common.MaskedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UiMessage(
    val id: Long = System.currentTimeMillis(),
    val isFromUser: Boolean,
    val text: String,
    val replyType: ReplyType = ReplyType.TEXT_ONLY,
    val disambiguationOwners: List<String> = emptyList(),
    val maskedIdValue: String? = null,
    val specCard: PhoneSpecCardData? = null
)

/**
 * Chat interface supporting persistent Room history restoration, smart auto-titling, and context restoration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialSessionId: Long? = null,
    initialQuery: String = "",
    userName: String = "",
    onSessionCreated: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DocsyDatabase.getInstance(context) }
    val retriever = remember { DocumentRetriever(db.documentDao(), db.factDao()) }
    val chatMemoryManager = remember { ChatMemoryManager(db.chatContextDao()) }

    var currentSessionId by remember { mutableStateOf(initialSessionId) }
    var chatTitle by remember { mutableStateOf("New chat") }
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<UiMessage>() }
    val listState = rememberLazyListState()

    val turnsList = remember { mutableStateListOf<Pair<String, String>>() }

    // Load existing chat session messages and restore context when opening an existing card
    LaunchedEffect(initialSessionId) {
        if (initialSessionId != null && initialSessionId > 0) {
            currentSessionId = initialSessionId
            withContext(Dispatchers.IO) {
                val sessionDao = db.chatDao()
                val session = sessionDao.getSessionById(initialSessionId)
                val storedMessages = sessionDao.getMessagesForSessionSnapshot(initialSessionId)

                withContext(Dispatchers.Main) {
                    if (session != null) {
                        chatTitle = session.title
                    }
                    messages.clear()
                    turnsList.clear()

                    var lastUserText = ""
                    storedMessages.forEach { msg ->
                        val (maskedId, specCard, owners) = deserializeSourcesJson(msg.sourcesJson)
                        val isUser = msg.role.equals("user", ignoreCase = true)
                        val uiMsg = UiMessage(
                            id = msg.id,
                            isFromUser = isUser,
                            text = msg.text,
                            maskedIdValue = maskedId,
                            specCard = specCard,
                            disambiguationOwners = owners
                        )
                        messages.add(uiMsg)

                        if (isUser) {
                            lastUserText = msg.text
                        } else if (lastUserText.isNotBlank()) {
                            turnsList.add(Pair(lastUserText, msg.text))
                            lastUserText = ""
                        }
                    }

                    if (messages.isNotEmpty()) {
                        try {
                            listState.scrollToItem(messages.size - 1)
                        } catch (_: Throwable) {}
                    }
                }

                // Restore conversational session context
                ChatMemoryManager.restoreContextForChat(initialSessionId)
            }
        }
    }

    // Send query logic
    fun handleQuery(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return

        // Cap long message text to 512 KB max per row
        val safeUserText = capTextSize(clean)

        val userMsgId = System.currentTimeMillis()
        messages.add(UiMessage(id = userMsgId, isFromUser = true, text = safeUserText))
        inputText = ""

        // Use Application IO scope for non-cancellable DB writes
        val appScope = com.suhrud.docsy.DocsyApplication.instance.applicationScope
        appScope.launch {
            val sessionDao = db.chatDao()
            var sessionId = currentSessionId

            // Lazy session creation: create chat row on first message
            if (sessionId == null || sessionId <= 0) {
                sessionId = sessionDao.insertSession(
                    ChatSessionEntity(
                        title = "New chat",
                        titleSource = "DEFAULT",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                withContext(Dispatchers.Main) {
                    currentSessionId = sessionId
                    onSessionCreated(sessionId)
                }
            } else {
                sessionDao.updateSession(
                    ChatSessionEntity(
                        id = sessionId,
                        title = chatTitle,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            // Load conversation memory for this chat session
            val chatContextDao = db.chatContextDao()
            try {
                val latestCtx = chatContextDao.getLatestContextForChat(sessionId)
                if (latestCtx != null) {
                    val lastDocId = latestCtx.activeDocIds?.toLongOrNull()
                    MarksheetLookup.setActiveDocId(lastDocId)
                }
            } catch (_: Exception) {}

            // Save user message immediately
            sessionDao.insertMessage(
                ChatMessageEntity(
                    chatId = sessionId,
                    role = "user",
                    text = safeUserText,
                    ts = userMsgId,
                    state = "completed"
                )
            )

            // Auto-scroll
            scope.launch {
                if (messages.isNotEmpty()) {
                    try {
                        listState.animateScrollToItem(messages.size - 1)
                    } catch (_: Throwable) {
                        try {
                            listState.scrollToItem(messages.size - 1)
                        } catch (_: Throwable) {}
                    }
                }
            }

            // Route query and process assistant response
            val decision = IntentRouter.route(clean)
            val reply: UiMessage = when (decision.outcome) {
                RouterOutcome.SMALL_TALK -> {
                    val text = DeterministicReplyComposer.composeSmallTalk(
                        intent = decision.intent,
                        userName = userName,
                        targetCategory = decision.targetCategory,
                        sessionId = sessionId.toString()
                    )
                    UiMessage(isFromUser = false, text = text)
                }
                RouterOutcome.GENERAL_KNOWLEDGE -> {
                    val text = if (com.suhrud.docsy.planner.LocalLlmManager.isModelLoaded.value) {
                        "[General Knowledge] Here is general information regarding \"$clean\": Docsy operates offline. For general knowledge queries, LLM response mode provides general facts, not from your phone's personal data."
                    } else {
                        "Docsy works offline on your phone and can't answer general knowledge questions without a chat model. You can add one in Settings."
                    }
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
                        DocsyIntent.PHONE_OVERVIEW_SUMMARY -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerOverview(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_FULL_SPECS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerFullSpecs(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_KERNEL -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerKernel(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_BUILD_NUMBER -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerBuildNumber(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_ANDROID_VERSION -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerAndroidVersion(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_SECURITY_PATCH -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerSecurityPatch(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_CHIPSET_CPU -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerChipsetAndCpu(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_REFRESH_RATE -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerRefreshRate(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_CAMERAS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerCameras(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_SENSORS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerSensors(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_CONNECTIVITY_INFO,
                        DocsyIntent.BLUETOOTH_STATUS,
                        DocsyIntent.NETWORK_STATUS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerConnectivity(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_AUDIO_THERMAL -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerAudioAndThermal(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_SECURITY_STATUS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerSecurityStatus(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.PHONE_SOFTWARE_SPECS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerSoftwareOverview(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.BATTERY_STATUS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerBattery(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.MEMORY_RAM_STATUS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerRam(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.INTERNAL_STORAGE_SPACE -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerStorage(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
                        }
                        DocsyIntent.DEVICE_HARDWARE_SPECS -> {
                            val snapshot = PhoneSpecsReader.getLiveSnapshot(context)
                            val (text, card) = PhoneSpecsReader.answerDisplay(snapshot)
                            UiMessage(isFromUser = false, text = text, specCard = card)
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
                            val marksheetReply = com.suhrud.docsy.retrieval.MarksheetLookup.answer(db, clean)
                            if (marksheetReply != null) {
                                UiMessage(isFromUser = false, text = marksheetReply)
                            } else {
                            val isMarksheetQuery = decision.targetCategory == "MARKSHEET" || clean.contains("12th") || clean.contains("marksheet")
                            val candidates = retriever.retrieveFact(
                                query = clean,
                                targetField = decision.targetField,
                                targetCategory = decision.targetCategory,
                                targetOwner = decision.targetOwner
                            )
                            if (candidates.isNotEmpty()) {
                                val top = candidates.first()
                                val fact = top.fact
                                if (fact != null) {
                                    val answer = if (isMarksheetQuery && (decision.targetField == "PERCENTAGE" || clean.contains("percentage") || clean.contains("percent") || clean.contains("12th"))) {
                                        val parsed = com.suhrud.docsy.understanding.MarksheetParser.parse(db.lineDao().getLinesForDocument(top.document.id).joinToString("\n") { it.text })
                                        com.suhrud.docsy.understanding.MarksheetParser.formatAnswer(parsed)
                                    } else {
                                        DeterministicReplyComposer.composeFactAnswer(
                                            fact = fact,
                                            documentName = top.document.displayTitle ?: top.document.docType ?: "document",
                                            userQuery = clean
                                        )
                                    }
                                    UiMessage(
                                        isFromUser = false,
                                        text = answer,
                                        maskedIdValue = if (decision.intent == DocsyIntent.DOCUMENT_ID_LOOKUP) fact.fieldValue else null
                                    )
                                } else {
                                    UiMessage(
                                        isFromUser = false,
                                        text = "I found a document that matches your query."
                                    )
                                }
                            } else {
                                val total = try { db.documentDao().getAllDocumentsSnapshot().size } catch (_: Exception) { 0 }
                                val notFound = NotFoundReplyGenerator.generateNotFound(
                                    subject = decision.targetCategory ?: clean,
                                    targetField = decision.targetField,
                                    owner = null,
                                    totalIndexedFiles = total
                                )
                                UiMessage(isFromUser = false, text = notFound.primaryText)
                            }
                            }
                        }
                        else -> {
                            val marksheetReplyOther = com.suhrud.docsy.retrieval.MarksheetLookup.answer(db, clean)
                            val docs = if (marksheetReplyOther == null) retriever.searchDocumentsLexical(clean) else emptyList()
                            if (marksheetReplyOther != null) {
                                UiMessage(isFromUser = false, text = marksheetReplyOther)
                            } else if (docs.isNotEmpty()) {
                                val names = docs.take(3).joinToString("\n• ") { it.displayTitle ?: it.docType ?: "Document" }
                                UiMessage(isFromUser = false, text = "I found the following matching documents:\n• $names")
                            } else {
                                val total = try { db.documentDao().getAllDocumentsSnapshot().size } catch (_: Exception) { 0 }
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

            val assistantMsgTime = System.currentTimeMillis()
            val safeReplyText = capTextSize(reply.text)
            val serializedSources = serializeSourcesJson(reply.maskedIdValue, reply.specCard, reply.disambiguationOwners)

            // Save assistant message immediately
            sessionDao.insertMessage(
                ChatMessageEntity(
                    chatId = sessionId,
                    role = "assistant",
                    text = safeReplyText,
                    ts = assistantMsgTime,
                    sourcesJson = serializedSources,
                    state = "completed"
                )
            )

            // Save chat context memory for multi-turn conversation tracking
            try {
                val activeDocId = MarksheetLookup.getActiveDocId()
                chatContextDao.insertContext(
                    com.suhrud.docsy.data.db.entity.ChatContextEntity(
                        chatId = sessionId,
                        turn = (messages.size / 2) + 1,
                        activeDocIds = activeDocId?.toString(),
                        activeOwner = decision.targetOwner,
                        activeDocType = decision.targetCategory,
                        lastField = decision.targetField,
                        lastIntent = decision.intent.name,
                        ts = assistantMsgTime
                    )
                )
                chatContextDao.trimContextHistory(sessionId, maxTurns = 10)
            } catch (_: Exception) {}

            // Save conversation turn to ChatMemoryManager
            chatMemoryManager.addTurnToHistory(
                chatId = sessionId,
                userQuery = clean,
                decision = decision,
                replyText = reply.text
            )

            withContext(Dispatchers.Main) {
                messages.add(reply)
                turnsList.add(Pair(clean, reply.text))
            }
            scope.launch {
                if (messages.isNotEmpty()) {
                    try {
                        listState.animateScrollToItem(messages.size - 1)
                    } catch (_: Throwable) {
                        try {
                            listState.scrollToItem(messages.size - 1)
                        } catch (_: Throwable) {}
                    }
                }
            }

            // SMART CHAT TITLES: Auto-titling rules
            val currentSession = sessionDao.getSessionById(sessionId)
            if (currentSession != null && currentSession.titleSource != "USER") {
                val newTitle = if (turnsList.size == 1) {
                    SmartTitleGenerator.generateTitle(
                        userQuery = clean,
                        assistantResponseText = reply.text,
                        decision = decision,
                        historyTurns = turnsList
                    )
                } else if (turnsList.size == 3) {
                    SmartTitleGenerator.generateTurn3Title(turnsList)
                } else {
                    null
                }

                if (!newTitle.isNullOrBlank() && newTitle != currentSession.title) {
                    sessionDao.updateSessionTitle(
                        sessionId = sessionId,
                        title = newTitle,
                        titleSource = "GENERATED",
                        updatedAt = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.Main) {
                        chatTitle = newTitle
                    }
                }
            }
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
                title = { Text(text = if (chatTitle.isNotBlank()) chatTitle else "DOCSY", fontWeight = FontWeight.SemiBold) },
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
                            chatTitle = "New chat"
                            messages.clear()
                            turnsList.clear()
                            com.suhrud.docsy.retrieval.MarksheetLookup.resetContext()
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

// 512 KB per row limit helper
private fun capTextSize(text: String): String {
    val maxBytes = 512 * 1024
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return text
    return text.take(250000) + "\n...[truncated long text]"
}

// Helper methods for sources_json serialization/deserialization
private fun serializeSourcesJson(
    maskedIdValue: String?,
    specCard: PhoneSpecCardData?,
    owners: List<String>
): String? {
    if (maskedIdValue == null && specCard == null && owners.isEmpty()) return null
    return try {
        val json = JSONObject()
        maskedIdValue?.let { json.put("maskedIdValue", it) }
        if (owners.isNotEmpty()) {
            val arr = JSONArray()
            owners.forEach { arr.put(it) }
            json.put("owners", arr)
        }
        specCard?.let { card ->
            val cardObj = JSONObject()
            cardObj.put("title", card.title)
            cardObj.put("timestampText", card.timestampText)
            val itemsArr = JSONArray()
            card.items.forEach { (k, v) ->
                val pair = JSONObject()
                pair.put("k", k)
                pair.put("v", v)
                itemsArr.put(pair)
            }
            cardObj.put("items", itemsArr)

            val expArr = JSONArray()
            card.expandableItems.forEach { (k, v) ->
                val pair = JSONObject()
                pair.put("k", k)
                pair.put("v", v)
                expArr.put(pair)
            }
            cardObj.put("expandableItems", expArr)
            json.put("specCard", cardObj)
        }
        json.toString()
    } catch (_: Exception) {
        null
    }
}

private fun deserializeSourcesJson(jsonStr: String?): Triple<String?, PhoneSpecCardData?, List<String>> {
    if (jsonStr.isNullOrBlank()) return Triple(null, null, emptyList())
    return try {
        val json = JSONObject(jsonStr)
        val maskedId = if (json.has("maskedIdValue")) json.getString("maskedIdValue") else null

        val owners = mutableListOf<String>()
        if (json.has("owners")) {
            val arr = json.getJSONArray("owners")
            for (i in 0 until arr.length()) {
                owners.add(arr.getString(i))
            }
        }

        var specCard: PhoneSpecCardData? = null
        if (json.has("specCard")) {
            val cardObj = json.getJSONObject("specCard")
            val title = cardObj.getString("title")
            val ts = cardObj.getString("timestampText")

            val items = mutableListOf<Pair<String, String>>()
            if (cardObj.has("items")) {
                val itemsArr = cardObj.getJSONArray("items")
                for (i in 0 until itemsArr.length()) {
                    val pair = itemsArr.getJSONObject(i)
                    items.add(Pair(pair.getString("k"), pair.getString("v")))
                }
            }

            val expandableItems = mutableListOf<Pair<String, String>>()
            if (cardObj.has("expandableItems")) {
                val expArr = cardObj.getJSONArray("expandableItems")
                for (i in 0 until expArr.length()) {
                    val pair = expArr.getJSONObject(i)
                    expandableItems.add(Pair(pair.getString("k"), pair.getString("v")))
                }
            }

            specCard = PhoneSpecCardData(
                title = title,
                timestampText = ts,
                items = items,
                expandableItems = expandableItems
            )
        }

        Triple(maskedId, specCard, owners)
    } catch (_: Exception) {
        Triple(null, null, emptyList())
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

                if (message.specCard != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val card = message.specCard
                    var isExpanded by remember { mutableStateOf(false) }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_specs_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = card.timestampText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            card.items.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                }
                            }
                            if (card.expandableItems.isNotEmpty()) {
                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    card.expandableItems.forEach { (label, value) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1.3f)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isExpanded) "Show Less ▲" else "Show More (${card.expandableItems.size} items) ▼",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(vertical = 4.dp)
                                        .testTag("specs_card_expand_button")
                                )
                            }
                        }
                    }
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
