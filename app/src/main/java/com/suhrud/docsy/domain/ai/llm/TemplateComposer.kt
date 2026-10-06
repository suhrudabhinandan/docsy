package com.suhrud.docsy.domain.ai.llm

import com.suhrud.docsy.data.model.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TemplateComposer : LocalLlm {

    override fun generate(
        prompt: String,
        maxTokens: Int,
        stop: String
    ): Flow<String> = flow {
        val words = prompt.split(" ")
        val sb = java.lang.StringBuilder()
        for (w in words) {
            sb.append(w).append(" ")
            emit(sb.toString())
            delay(20)
        }
    }

    fun composeFriendlyReply(
        userName: String,
        fact: ChatMessage
    ): ChatMessage {
        val nameStr = if (userName.isNotBlank()) " $userName" else ""

        if (fact.answerHighlight != null) {
            val intro = when {
                fact.supportingMetadata?.contains("CALENDAR") == true -> "Here is the date detail you asked for$nameStr:"
                fact.supportingMetadata?.contains("STORAGE") == true -> "Here is your phone storage breakdown$nameStr:"
                fact.supportingMetadata?.contains("AADHAAR") == true -> "Found your Aadhaar number$nameStr:"
                fact.supportingMetadata?.contains("PAN") == true -> "Found your PAN card$nameStr:"
                fact.supportingMetadata?.contains("MARKSHEET") == true -> "Here is your marksheet result$nameStr:"
                fact.supportingMetadata?.contains("SENSOR") == true || fact.supportingMetadata?.contains("HEALTH") == true -> "Here is your step count$nameStr:"
                else -> "Here is what I found on your phone$nameStr:"
            }

            return fact.copy(
                text = "$intro ${fact.text}"
            )
        }

        return fact
    }
}
