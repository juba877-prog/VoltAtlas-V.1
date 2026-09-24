package com.example.ai.orchestrator

import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.hybrid.CloudDegradationReason
import com.example.ai.hybrid.ToolCall
import com.example.ai.tools.ToolExecutionOutcome

/**
 * Centralized safety budget and concurrency limits governing multi-turn agentic loops (Gate 5).
 */
data class AgenticSafetyBudget(
    val maxIterations: Int = 5,
    val maxTotalToolCalls: Int = 10,
    val maxRepeatedCalls: Int = 2,
    val maxParallelReads: Int = 4,
    val iterationTimeoutMs: Long = 10_000L,
    val totalTimeoutMs: Long = 30_000L
)

/**
 * Terminal states for the multi-turn agentic tool loop.
 */
enum class AgenticLoopTermination {
    FINAL_ANSWER_REACHED,
    MAX_ITERATIONS_EXCEEDED,
    MAX_TOOL_CALLS_EXCEEDED,
    REPEATED_TOOL_CALL_DETECTED,
    TIMEOUT,
    PROVIDER_ERROR,
    SAFETY_BLOCKED,
    TOOL_EXECUTION_FAILED,
    GROUNDING_VALIDATION_FAILED,
    CANCELLED
}

/**
 * Execution state signature used to detect pathological repeated tool calls (Correction #5).
 *
 * Includes canonical tool name, arguments fingerprint, project identity, entity identity,
 * and project revision. A recalculation with identical arguments after a revision change
 * is NOT flagged as a repeated call.
 */
data class ToolExecutionSignature(
    val toolName: String,
    val argumentsFingerprint: String,
    val projectId: String,
    val entityType: String?,
    val entityId: String?,
    val projectRevision: Long?
)

/**
 * Provider-neutral representation of turns in the multi-turn conversation (Correction #6).
 */
sealed interface AgenticTurnItem {
    /**
     * Tool calls emitted by the model in a single turn.
     */
    data class ModelCalls(
        val calls: List<ToolCall>
    ) : AgenticTurnItem

    /**
     * Preserves authoritative [ToolExecutionOutcome] paired with the triggering [ToolCall].
     */
    data class ExecutedTools(
        val outcomes: List<Pair<ToolCall, ToolExecutionOutcome>>
    ) : AgenticTurnItem
}

/**
 * Typed outcome of an entire multi-turn agentic loop execution.
 */
data class AgenticExecutionResult(
    val terminationReason: AgenticLoopTermination,
    val finalText: String? = null,
    val turnsExecuted: Int = 0,
    val toolOutcomes: List<ToolExecutionOutcome> = emptyList(),
    val evidenceRecords: List<EngineeringEvidenceRecord> = emptyList(),
    val turnHistory: List<AgenticTurnItem> = emptyList(),
    val degradationReason: CloudDegradationReason? = null,
    val errorMessage: String? = null
)
