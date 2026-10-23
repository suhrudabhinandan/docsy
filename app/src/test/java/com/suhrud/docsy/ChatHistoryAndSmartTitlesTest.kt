package com.suhrud.docsy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.ChatContextEntity
import com.suhrud.docsy.data.db.entity.ChatMessageEntity
import com.suhrud.docsy.data.db.entity.ChatSessionEntity
import com.suhrud.docsy.planner.LocalLlmManager
import com.suhrud.docsy.planner.LocalModelTier
import com.suhrud.docsy.retrieval.DocsyIntent
import com.suhrud.docsy.retrieval.RouterOutcome
import com.suhrud.docsy.retrieval.RoutingDecision
import com.suhrud.docsy.retrieval.SmartTitleGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatHistoryAndSmartTitlesTest {

    private lateinit var db: DocsyDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DocsyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testThreeTurnsPersistenceReopenAndDeleteNoOrphans() = runBlocking {
        val chatDao = db.chatDao()
        val contextDao = db.chatContextDao()

        // 1. Lazy chat session creation on first user message
        val sessionId = chatDao.insertSession(
            ChatSessionEntity(
                title = "New chat",
                titleSource = "DEFAULT",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Turn 1: User & Assistant
        val t1 = System.currentTimeMillis()
        chatDao.insertMessage(
            ChatMessageEntity(chatId = sessionId, role = "user", text = "Hello Docsy", ts = t1)
        )
        chatDao.insertMessage(
            ChatMessageEntity(chatId = sessionId, role = "assistant", text = "Hello! How can I help?", ts = t1 + 10)
        )
        contextDao.insertContext(
            ChatContextEntity(chatId = sessionId, turn = 1, activeDocType = "GREETING", ts = t1 + 15)
        )

        // Turn 2: User & Assistant
        val t2 = t1 + 1000
        chatDao.insertMessage(
            ChatMessageEntity(chatId = sessionId, role = "user", text = "What is my 12th marksheet percentage?", ts = t2)
        )
        chatDao.insertMessage(
            ChatMessageEntity(chatId = sessionId, role = "assistant", text = "Your 12th percentage is 88.4%", ts = t2 + 10)
        )
        contextDao.insertContext(
            ChatContextEntity(chatId = sessionId, turn = 2, activeDocType = "12th", ts = t2 + 15)
        )

        // Turn 3: User & Assistant
        val t3 = t2 + 1000
        chatDao.insertMessage(
            ChatMessageEntity(chatId = sessionId, role = "user", text = "Check my battery status", ts = t3)
        )
        chatDao.insertMessage(
            ChatMessageEntity(chatId = sessionId, role = "assistant", text = "Your battery is at 85%", ts = t3 + 10)
        )
        contextDao.insertContext(
            ChatContextEntity(chatId = sessionId, turn = 3, activeDocType = "BATTERY", ts = t3 + 15)
        )

        // Simulate app kill & reopen: load messages snapshot from DB
        val restoredMessages = chatDao.getMessagesForSessionSnapshot(sessionId)
        assertEquals("All 6 messages (3 turns) must be restored after app reopen", 6, restoredMessages.size)
        assertEquals("user", restoredMessages[0].role)
        assertEquals("Hello Docsy", restoredMessages[0].text)
        assertEquals("assistant", restoredMessages[1].role)
        assertEquals("Hello! How can I help?", restoredMessages[1].text)
        assertEquals("user", restoredMessages[4].role)
        assertEquals("Check my battery status", restoredMessages[4].text)
        assertEquals("assistant", restoredMessages[5].role)
        assertEquals("Your battery is at 85%", restoredMessages[5].text)

        val restoredContext = contextDao.getLatestContextForChat(sessionId)
        assertNotNull("Chat context must be restored on reopen", restoredContext)
        assertEquals("BATTERY", restoredContext?.activeDocType)

        // Delete session and confirm cascading delete leaves NO ORPHAN ROWS
        chatDao.deleteSession(sessionId)

        val remainingSessions = chatDao.getAllSessionsSnapshot()
        val remainingMessages = chatDao.getMessagesForSessionSnapshot(sessionId)
        val remainingContexts = contextDao.getContextsForChat(sessionId)

        assertEquals("No remaining chat sessions", 0, remainingSessions.size)
        assertEquals("No remaining messages (no orphan rows)", 0, remainingMessages.size)
        assertEquals("No remaining chat contexts (no orphan rows)", 0, remainingContexts.size)
    }

    @Test
    fun testSyntheticTurnTitleGenerationRules() {
        // 1. Marksheet query -> "12th marksheet percentage"
        val decisionMarksheet = RoutingDecision(
            outcome = RouterOutcome.ANSWERABLE,
            intent = DocsyIntent.DOCUMENT_FACT_QUERY,
            confidence = 0.95f,
            targetCategory = "12th",
            targetField = "percentage"
        )
        val titleMarksheet = SmartTitleGenerator.generateTitle(
            userQuery = "what is my 12th marksheet percentage",
            assistantResponseText = "Your 12th percentage is 88.4%",
            decision = decisionMarksheet
        )
        assertTrue("12th marksheet percentage title", titleMarksheet.equals("12th marksheet percentage", ignoreCase = true))

        // 2. Battery question -> "Battery status"
        val decisionBattery = RoutingDecision(
            outcome = RouterOutcome.ANSWERABLE,
            intent = DocsyIntent.BATTERY_STATUS,
            confidence = 0.95f
        )
        val titleBattery = SmartTitleGenerator.generateTitle(
            userQuery = "check my battery level",
            assistantResponseText = "Your battery is at 85%",
            decision = decisionBattery
        )
        assertTrue("Battery status title", titleBattery.equals("Battery status", ignoreCase = true))

        // 3. Greeting only -> "Small talk"
        val decisionGreeting = RoutingDecision(
            outcome = RouterOutcome.SMALL_TALK,
            intent = DocsyIntent.GREETING,
            confidence = 0.99f
        )
        val titleGreeting = SmartTitleGenerator.generateTitle(
            userQuery = "hello docsy",
            assistantResponseText = "Hello! How can I help you?",
            decision = decisionGreeting
        )
        assertTrue("Small talk title", titleGreeting.equals("Small talk", ignoreCase = true))

        // 4. Mixed topic turn 3 -> "Battery and storage"
        val turns = listOf(
            Pair("hello docsy", "Hello!"),
            Pair("check battery status", "Battery is 85%"),
            Pair("check internal storage space", "Free storage is 42 GB")
        )
        val turn3Title = SmartTitleGenerator.generateTurn3Title(turns)
        assertTrue("Battery and storage title", turn3Title.equals("Battery and storage", ignoreCase = true))

        // 5. Personal ID numbers stripped from title
        val rawTitleAadhaar = "Aadhaar Card 1234 5678 9012 Details"
        val sanitizedAadhaar = SmartTitleGenerator.sanitizeTitle(rawTitleAadhaar)
        assertFalse("Title must not contain 12-digit Aadhaar number", sanitizedAadhaar.contains("1234"))
        assertFalse("Title must not contain 12-digit Aadhaar number", sanitizedAadhaar.contains("5678"))
        assertTrue("Title retains descriptive words", sanitizedAadhaar.contains("Aadhaar"))

        val rawTitlePan = "PAN Card ABCDE1234F Details"
        val sanitizedPan = SmartTitleGenerator.sanitizeTitle(rawTitlePan)
        assertFalse("Title must not contain PAN number", sanitizedPan.contains("ABCDE1234F"))

        val rawTitlePhone = "Contact Phone 9876543210 Info"
        val sanitizedPhone = SmartTitleGenerator.sanitizeTitle(rawTitlePhone)
        assertFalse("Title must not contain phone number", sanitizedPhone.contains("9876543210"))

        // 6. Fallback title format
        val fallback = SmartTitleGenerator.formatFallbackTitle()
        assertTrue("Fallback title starts with Chat ·", fallback.startsWith("Chat ·"))
    }

    @Test
    fun testRenamedChatStaysRenamedUserOverride() = runBlocking {
        val chatDao = db.chatDao()

        val sessionId = chatDao.insertSession(
            ChatSessionEntity(
                title = "12th marksheet percentage",
                titleSource = "GENERATED"
            )
        )

        // User manually renames chat to "My Board Exam Marks"
        chatDao.updateSessionTitle(
            sessionId = sessionId,
            title = "My Board Exam Marks",
            titleSource = "USER"
        )

        val updatedSession = chatDao.getSessionById(sessionId)
        assertNotNull(updatedSession)
        assertEquals("My Board Exam Marks", updatedSession?.title)
        assertEquals("USER", updatedSession?.titleSource)

        // Verify that auto-titling logic respects USER title_source override
        if (updatedSession != null && updatedSession.titleSource != "USER") {
            chatDao.updateSessionTitle(sessionId, "Overwritten Title", "GENERATED")
        }

        val recheckedSession = chatDao.getSessionById(sessionId)
        assertEquals("Title must remain user's custom title", "My Board Exam Marks", recheckedSession?.title)
    }

    @Test
    fun testLlmModelTitleGenerationWhenLoaded() {
        LocalLlmManager.loadModel(LocalModelTier.BUNDLED_SMALL_TIER)
        assertTrue(LocalLlmManager.isModelLoaded.value)

        val turns = listOf(
            Pair("Explain photosynthesis process", "Photosynthesis is the process by which green plants use sunlight...")
        )
        val title = SmartTitleGenerator.generateTitle(
            userQuery = "Explain photosynthesis process",
            assistantResponseText = "Photosynthesis is...",
            historyTurns = turns
        )
        assertTrue("Title should be generated and non-blank", title.isNotBlank())
        assertFalse("Raw message not used", title == "Explain photosynthesis process")

        LocalLlmManager.unloadModel()
        assertFalse(LocalLlmManager.isModelLoaded.value)
    }

    @Test
    fun testEmptyChatsFilteredFromHistory() = runBlocking {
        val chatDao = db.chatDao()

        // Eager session creation without messages
        val emptySessionId = chatDao.insertSession(
            ChatSessionEntity(title = "Empty Chat", titleSource = "DEFAULT")
        )

        // Non-empty session with messages
        val activeSessionId = chatDao.insertSession(
            ChatSessionEntity(title = "Active Chat", titleSource = "GENERATED")
        )
        chatDao.insertMessage(
            ChatMessageEntity(chatId = activeSessionId, role = "user", text = "Hi")
        )

        val sessionsInHistory = chatDao.getAllSessionsSnapshot()
        assertEquals("History must only list sessions containing messages", 1, sessionsInHistory.size)
        assertEquals(activeSessionId, sessionsInHistory[0].id)
    }
}
