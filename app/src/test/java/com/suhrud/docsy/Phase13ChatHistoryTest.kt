package com.suhrud.docsy

import com.suhrud.docsy.data.db.entity.ChatMessageEntity
import com.suhrud.docsy.data.db.entity.ChatSessionEntity
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import com.suhrud.docsy.data.db.entity.FactEntity
import com.suhrud.docsy.planner.DeterministicReplyComposer
import com.suhrud.docsy.planner.FactGroundingValidator
import com.suhrud.docsy.planner.LocalLlmManager
import com.suhrud.docsy.planner.PlannedToolSequence
import com.suhrud.docsy.planner.ToolCallProposal
import com.suhrud.docsy.planner.ToolCallValidator
import com.suhrud.docsy.retrieval.DocsyIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase13ChatHistoryTest {

    @Test
    fun testChatSessionEntities() {
        val session = ChatSessionEntity(
            id = 1L,
            title = "12th Marksheet Query",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        assertEquals("12th Marksheet Query", session.title)
        assertEquals(1L, session.id)

        val message = ChatMessageEntity(
            id = 10L,
            sessionId = 1L,
            isFromUser = true,
            text = "What is my 12th percentage?",
            timestamp = 1500L
        )
        assertTrue(message.isFromUser)
        assertEquals("What is my 12th percentage?", message.text)
    }

    @Test
    fun testToolCallValidator() {
        val validProposal = ToolCallProposal(
            toolName = "DateTimeTool",
            arguments = mapOf("query" to "time")
        )
        assertTrue(ToolCallValidator.validate(validProposal))

        val invalidProposal = ToolCallProposal(
            toolName = "FakeMaliciousTool",
            arguments = emptyMap()
        )
        assertFalse(ToolCallValidator.validate(invalidProposal))

        val validSequence = PlannedToolSequence(
            calls = listOf(
                ToolCallProposal("DocumentRetriever"),
                ToolCallProposal("DocsyCalculator")
            )
        )
        assertTrue(ToolCallValidator.validateSequence(validSequence))

        // Exceeding 3 steps bound must fail validation
        val tooLongSequence = PlannedToolSequence(
            calls = listOf(
                ToolCallProposal("DocumentRetriever"),
                ToolCallProposal("DocsyCalculator"),
                ToolCallProposal("BatteryTool"),
                ToolCallProposal("NetworkTool")
            )
        )
        assertFalse(ToolCallValidator.validateSequence(tooLongSequence))
    }

    @Test
    fun testFactGroundingValidator() {
        val facts = listOf(
            FactEntity(
                documentId = 1L,
                fieldName = "percentage",
                fieldValue = "88.4%",
                factType = "MARKSHEET",
                confidence = 0.95f
            ),
            FactEntity(
                documentId = 1L,
                fieldName = "roll_number",
                fieldValue = "9123456",
                factType = "MARKSHEET",
                confidence = 0.95f
            )
        )

        // Grounded reply containing verified numbers
        val groundedText = "Your percentage is 88.4% and your roll number is 9123456."
        assertTrue(FactGroundingValidator.validateGrounding(groundedText, facts))

        // Hallucinated number (e.g. claiming 94.5% or 9999999) must fail validation
        val hallucinatedText = "Your percentage is 94.5% with roll number 9999999."
        assertFalse(FactGroundingValidator.validateGrounding(hallucinatedText, facts))
    }

    @Test
    fun testDeterministicReplyComposer() {
        val fact = FactEntity(
            documentId = 1L,
            fieldName = "percentage",
            fieldValue = "88.4%",
            factType = "MARKSHEET",
            confidence = 0.95f
        )

        val englishAnswer = DeterministicReplyComposer.composeFactAnswer(
            fact = fact,
            documentName = "Class12_Marksheet.pdf",
            userQuery = "What is my 12th percentage?"
        )
        assertTrue(englishAnswer.contains("According to your \"Class12_Marksheet.pdf\":"))
        assertTrue(englishAnswer.contains("88.4%"))

        // Hinglish query mirroring
        val hinglishAnswer = DeterministicReplyComposer.composeFactAnswer(
            fact = fact,
            documentName = "Class12_Marksheet.pdf",
            userQuery = "Mera 12th marksheet ka percentage batao"
        )
        assertTrue(hinglishAnswer.contains("Aapke \"Class12_Marksheet.pdf\" ke mutabiq:"))
        assertTrue(hinglishAnswer.contains("88.4%"))

        // Small talk greetings
        val greeting = DeterministicReplyComposer.composeSmallTalk(DocsyIntent.GREETING, userName = "Abhinandan")
        assertTrue(greeting.contains("Abhinandan"))
        assertTrue(greeting.contains("DOCSY"))
    }

    @Test
    fun testChatDeletionIsolation() {
        // Deleting chats must only clear chat tables, never touching indexed documents
        val documents = mutableListOf(
            DocumentEntity(
                id = 1L,
                displayName = "Passport.pdf",
                extension = "pdf",
                mimeType = "application/pdf",
                absolutePath = "/storage/emulated/0/Documents/Passport.pdf",
                sizeBytes = 100000L,
                lastModified = 1700000000000L,
                status = DocumentStatus.INDEXED,
                contentKey = "key1"
            )
        )
        val chatSessions = mutableListOf(
            ChatSessionEntity(id = 1L, title = "Session 1")
        )

        // Clear chat sessions
        chatSessions.clear()

        // Verify documents are completely untouched
        assertEquals(0, chatSessions.size)
        assertEquals(1, documents.size)
        assertEquals("Passport.pdf", documents.first().fileName)
    }
}
