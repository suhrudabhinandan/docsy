package com.suhrud.docsy.domain.ai.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class RuntimeLlmEngine(private val context: Context) : LocalLlm {

    private val TAG = "DocsyLlmEngine"

    fun isModelImported(): Boolean {
        val f = File(context.filesDir, "imported_llm_model.bin")
        return f.exists() && f.length() > 0
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        stop: String
    ): Flow<String> = flow {
        if (!isModelImported()) {
            val template = TemplateComposer()
            template.generate(prompt, maxTokens, stop).collect { emit(it) }
            return@flow
        }

        try {
            val template = TemplateComposer()
            template.generate(prompt, maxTokens, stop).collect { emit(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Runtime LLM error: ${e.message}")
            val template = TemplateComposer()
            template.generate(prompt, maxTokens, stop).collect { emit(it) }
        }
    }
}
