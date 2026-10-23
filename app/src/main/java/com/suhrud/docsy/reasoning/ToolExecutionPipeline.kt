package com.suhrud.docsy.reasoning

import com.suhrud.docsy.retrieval.DocsyReply
import com.suhrud.docsy.retrieval.ReplyType
import com.suhrud.docsy.tools.TopicDomain

data class PipelineStepResult(
    val stepIndex: Int,
    val toolName: String,
    val isSuccess: Boolean,
    val outputData: Any?,
    val error: String? = null
)

/**
 * Multi-step tool execution pipeline with strict step bounds (max 3 calls).
 * Satisfies Phase 10.5 in AUDIT.md.
 */
class ToolExecutionPipeline(
    private val maxSteps: Int = 3
) {

    private val stepHistory = mutableListOf<PipelineStepResult>()

    fun canExecuteNext(): Boolean = stepHistory.size < maxSteps

    suspend fun executeStep(
        toolName: String,
        action: suspend () -> Any?
    ): PipelineStepResult {
        if (!canExecuteNext()) {
            val overflow = PipelineStepResult(
                stepIndex = stepHistory.size,
                toolName = toolName,
                isSuccess = false,
                outputData = null,
                error = "Maximum step bound ($maxSteps) reached in ToolExecutionPipeline"
            )
            stepHistory.add(overflow)
            return overflow
        }

        return try {
            val result = action()
            val step = PipelineStepResult(
                stepIndex = stepHistory.size,
                toolName = toolName,
                isSuccess = true,
                outputData = result
            )
            stepHistory.add(step)
            step
        } catch (e: Exception) {
            val step = PipelineStepResult(
                stepIndex = stepHistory.size,
                toolName = toolName,
                isSuccess = false,
                outputData = null,
                error = e.message ?: "Unknown step failure"
            )
            stepHistory.add(step)
            step
        }
    }

    fun getHistory(): List<PipelineStepResult> = stepHistory.toList()

    fun clear() {
        stepHistory.clear()
    }
}
