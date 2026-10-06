package com.suhrud.docsy.domain.ai.llm

import com.suhrud.docsy.data.model.ChatMessage

object GroundedFactValidator {

    fun isGrounded(
        generatedText: String,
        fact: ChatMessage,
        userName: String
    ): Boolean {
        val genNumbers = Regex("""\b\d+(?:\.\d+)?\b""").findAll(generatedText).map { it.value }.toSet()
        val factText = "${fact.text} ${fact.answerHighlight ?: ""} ${fact.subtext ?: ""} $userName"
        val factNumbers = Regex("""\b\d+(?:\.\d+)?\b""").findAll(factText).map { it.value }.toSet()

        for (num in genNumbers) {
            if (!factNumbers.contains(num)) {
                return false
            }
        }
        return true
    }
}
