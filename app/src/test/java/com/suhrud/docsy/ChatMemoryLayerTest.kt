package com.suhrud.docsy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.retrieval.ChatMemoryManager
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatMemoryLayerTest {

    private lateinit var db: DocsyDatabase
    private lateinit var memoryManager: ChatMemoryManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DocsyDatabase::class.java
        ).allowMainThreadQueries().build()

        memoryManager = ChatMemoryManager(db.chatContextDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Follow-up inherits slots (e.g. "my 3rd sem score" -> "what were the subjects")
    @Test
    fun testFollowUpInheritsSlots() = runBlocking {
        val chatId = 101L

        // Turn 1: "my 3rd sem score"
        memoryManager.recordTurnContext(
            chatId = chatId,
            docIds = listOf(15L),
            owner = "Suhrud Abhinandan",
            docType = "marksheet 10/12/semester",
            period = "Semester 3",
            fieldsReturned = listOf("percentage", "total_marks"),
            intent = "FIELD_QUERY"
        )

        // Turn 2: "what were the subjects"
        val resolved = memoryManager.resolveQueryContext(
            chatId = chatId,
            query = "what were the subjects"
        )

        assertTrue("Turn 2 must be identified as follow-up", resolved.isFollowUp)
        assertEquals("Inherited doc IDs must contain doc 15", listOf(15L), resolved.inheritedDocIds)
        assertEquals("Inherited period must be Semester 3", "Semester 3", resolved.inheritedPeriod)
        assertEquals("Inherited doc type must match", "marksheet 10/12/semester", resolved.inheritedDocType)
        assertNotNull("Source card must specify inherited context", resolved.sourceCardInheritedContext)
        assertTrue(
            "Source card context text must describe 3rd semester",
            resolved.sourceCardInheritedContext!!.contains("3rd semester", ignoreCase = true) ||
                    resolved.sourceCardInheritedContext!!.contains("Semester 3", ignoreCase = true)
        )
    }

    // 2. Topic switch resets context completely
    @Test
    fun testTopicSwitchResetsContext() = runBlocking {
        val chatId = 102L

        // Turn 1: "my 3rd sem score"
        memoryManager.recordTurnContext(
            chatId = chatId,
            docIds = listOf(15L),
            owner = "Suhrud Abhinandan",
            docType = "marksheet 10/12/semester",
            period = "Semester 3",
            fieldsReturned = listOf("percentage"),
            intent = "FIELD_QUERY"
        )

        // Turn 2: Query for a different document ("10th marksheet")
        val resolved = memoryManager.resolveQueryContext(
            chatId = chatId,
            query = "show my 10th marksheet",
            parsedDocType = "10th marksheet",
            parsedPeriod = "Class 10"
        )

        assertFalse("Topic switch must NOT inherit follow-up state", resolved.isFollowUp)
        assertTrue("Inherited doc IDs must be empty", resolved.inheritedDocIds.isEmpty())
        assertNull("Inherited period must be null", resolved.inheritedPeriod)
    }

    // 3. Ambiguous follow-up asks clarifying question
    @Test
    fun testAmbiguousFollowUpAsksClarification() = runBlocking {
        val chatId = 103L

        memoryManager.recordTurnContext(
            chatId = chatId,
            docIds = listOf(20L),
            owner = "Suhrud Abhinandan",
            docType = "Aadhaar",
            period = null,
            fieldsReturned = listOf("aadhaar_no"),
            intent = "FIELD_QUERY"
        )

        // Turn 2: Short ambiguous query "or?"
        val resolved = memoryManager.resolveQueryContext(
            chatId = chatId,
            query = "or?"
        )

        assertTrue("Ambiguous query must flag follow-up", resolved.isFollowUp)
        assertTrue("Ambiguous query must require clarification", resolved.needsClarification)
        assertNotNull("Clarification question must be provided", resolved.clarificationQuestion)
    }

    // 4. New chat starts empty
    @Test
    fun testNewChatStartsEmpty() = runBlocking {
        val newChatId = 999L

        val resolved = memoryManager.resolveQueryContext(
            chatId = newChatId,
            query = "what were the subjects"
        )

        assertFalse("New chat must not have follow-up context", resolved.isFollowUp)
        assertTrue("New chat doc IDs must be empty", resolved.inheritedDocIds.isEmpty())
    }

    // 5. Deleting a chat deletes its context
    @Test
    fun testDeletingChatDeletesContext() = runBlocking {
        val chatId = 202L

        memoryManager.recordTurnContext(
            chatId = chatId,
            docIds = listOf(30L),
            owner = "Rahul Sharma",
            docType = "PAN Card",
            period = null,
            fieldsReturned = listOf("pan_no"),
            intent = "FIELD_QUERY"
        )

        // Confirm context exists
        val beforeDelete = db.chatContextDao().getLatestContextForChat(chatId)
        assertNotNull("Chat context must exist before deletion", beforeDelete)

        // Delete chat context
        memoryManager.clearChatMemory(chatId)

        val afterDelete = db.chatContextDao().getLatestContextForChat(chatId)
        assertNull("Chat context must be null after deletion", afterDelete)
    }
}
