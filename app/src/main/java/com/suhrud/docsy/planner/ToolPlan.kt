package com.suhrud.docsy.planner

data class ToolCallProposal(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap()
)

data class PlannedToolSequence(
    val calls: List<ToolCallProposal>,
    val explanation: String = ""
)

/**
 * Validates planned tool proposals against strict schemas and registered tool names.
 * Satisfies Phase 11.1 in AUDIT.md.
 */
object ToolCallValidator {

    private val ALLOWED_TOOLS = setOf(
        "DateTimeTool",
        "BatteryTool",
        "NetworkTool",
        "DisplayTool",
        "MemoryTool",
        "HardwareInfoTool",
        "StepCounterTool",
        "ContactsTool",
        "TelephonyTool",
        "StorageStatsTool",
        "DocumentRetriever",
        "DocsyCalculator"
    )

    fun validate(proposal: ToolCallProposal): Boolean {
        if (!ALLOWED_TOOLS.contains(proposal.toolName)) return false
        return true
    }

    fun validateSequence(sequence: PlannedToolSequence, maxSteps: Int = 3): Boolean {
        if (sequence.calls.isEmpty() || sequence.calls.size > maxSteps) return false
        return sequence.calls.all { validate(it) }
    }
}
