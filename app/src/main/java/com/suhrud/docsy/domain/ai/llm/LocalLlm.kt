package com.suhrud.docsy.domain.ai.llm

import kotlinx.coroutines.flow.Flow

interface LocalLlm {
    fun generate(
        prompt: String,
        maxTokens: Int = 128,
        stop: String = ""
    ): Flow<String>
}

enum class LlmVoiceMode {
    BASIC,
    ENHANCED
}
