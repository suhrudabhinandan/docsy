package com.suhrud.docsy

import com.suhrud.docsy.data.model.ChatMessageEntity
import com.suhrud.docsy.data.model.ChatSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistorySessionsTest {

    @Test
    fun testChatSessionCreation() {
        val session = ChatSessionEntity(
            sessionId = "test_session_101",
            title = "12th mark search"
        )

        assertEquals("test_session_101", session.sessionId)
        assertEquals("12th mark search", session.title)
    }

    @Test
    fun testChatMessageWithSessionId() {
        val message = ChatMessageEntity(
            id = "msg_001",
            sessionId = "test_session_101",
            isUser = true,
            text = "12th mark"
        )

        assertEquals("msg_001", message.id)
        assertEquals("test_session_101", message.sessionId)
        assertEquals("12th mark", message.text)
    }
}
