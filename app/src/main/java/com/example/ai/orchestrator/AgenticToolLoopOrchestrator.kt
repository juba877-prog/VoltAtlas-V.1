package com.example.ai.orchestrator

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.hybrid.AiDiagnostics
import com.example.ai.hybrid.CloudDegradationReason
import com.example.ai.hybrid.CloudFunctionDeclaration
import com.example.ai.hybrid.CloudLlmClientResponse
import com.example.ai.hybrid.EngineeringClaimGuard
import com.example.ai.hybrid.ToolCall
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.EngineeringToolDispatcher
import com.example.ai.tools.EngineeringToolRegistry
import com.example.ai.tools.ToolExecutionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Provider-neutral interface for generating multi-turn responses with tool execution history.
 */
interface MultiTurnGeminiSeam {
    suspend fun generateTurn(
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<AgenticTurnItem>,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmClientResponse
}

/**
 * Orchestrator driving multi-turn agentic tool execution (Gate 5).
 *
 * Invariants:
 * - Provider Neutrality (Correction #6): Does not depend on Firebase or Gemini SDK directly.
 * - Authoritative Context (Correction #5 & #7): Overrides model arguments with authoritative projectContext.
 * - Parallel Reads vs Sequential Execution (Correction #3 & #8): Independent read calls execute concurrently
 *   capped by [AgenticSafetyBudget.maxParallelReads]. Dependent/calculation calls execute sequentially.
 * - Fail-Safe Concurrency (Correction #8): Each call preserves its callId, toolName, and individual ToolResultEnvelope.
 * - State-Aware Loop Detection (Correction #5): Detects repeated identical calls within the same revision/entity state.
 * - Structured Concurrency: Propagates [CancellationException] immediately.
 */
class AgenticToolLoopOrchestrator(
    private val clientSeam: MultiTurnGeminiSeam,
    private val toolDispatcher: EngineeringToolDispatcher,
    private val toolRegistry: EngineeringToolRegistry,
    private val safetyBudget: AgenticSafetyBudget = AgenticSafetyBudget()
) {

    suspend fun executeLoop(
        systemPrompt: String,
        userPrompt: String,
        projectContext: BoundedProjectContext,
        scopedTools: List<CloudFunctionDeclaration>,
        conversationId: String? = null
    ): AgenticExecutionResult {
        val turnHistory = mutableListOf<AgenticTurnItem>()
        val allOutcomes = mutableListOf<ToolExecutionOutcome>()
        val signatureCounts = mutableMapOf<ToolExecutionSignature, Int>()
        var totalToolCalls = 0
        val startTime = System.currentTimeMillis()

        AiDiagnostics.stage(
            AiDiagnostics.Stage.START,
            "result" to "OK",
            "toolDeclarationCount" to scopedTools.size,
            "projectScoped" to (projectContext.projectId != null),
            "iterationBudget" to safetyBudget.maxIterations
        )

        for (iteration in 0 until safetyBudget.maxIterations) {
            // Iteration 0 is the FIRST turn (no tool results yet). Any later iteration re-sends
            // the accumulated tool results, i.e. it IS the "second turn" from the provider's view.
            AiDiagnostics.stage(
                if (iteration == 0) AiDiagnostics.Stage.MODEL_RESPONSE else AiDiagnostics.Stage.SECOND_TURN,
                "iteration" to iteration,
                "historyTurns" to turnHistory.size,
                "elapsedMs" to (System.currentTimeMillis() - startTime)
            )
            // Check overall loop timeout
            if (System.currentTimeMillis() - startTime > safetyBudget.totalTimeoutMs) {
                return AgenticExecutionResult(
                    terminationReason = AgenticLoopTermination.TIMEOUT,
                    turnsExecuted = iteration,
                    toolOutcomes = allOutcomes,
                    turnHistory = turnHistory,
                    degradationReason = CloudDegradationReason.NETWORK_TIMEOUT,
                    errorMessage = "Total agentic execution exceeded ${safetyBudget.totalTimeoutMs}ms"
                )
            }

            // Call provider seam with iteration timeout
            val clientResponse = try {
                withTimeout(safetyBudget.iterationTimeoutMs) {
                    clientSeam.generateTurn(
                        systemPrompt = systemPrompt,
                        userPrompt = userPrompt,
                        conversationHistory = turnHistory,
                        tools = scopedTools
                    )
                }
            } catch (e: TimeoutCancellationException) {
                AiDiagnostics.failure(
                    AiDiagnostics.Stage.SECOND_TURN,
                    CloudDegradationReason.NETWORK_TIMEOUT,
                    "reason" to "NETWORK_TIMEOUT",
                    "iteration" to iteration,
                    "iterationTimeoutMs" to safetyBudget.iterationTimeoutMs
                )
                return AgenticExecutionResult(
                    terminationReason = AgenticLoopTermination.TIMEOUT,
                    turnsExecuted = iteration,
                    toolOutcomes = allOutcomes,
                    turnHistory = turnHistory,
                    degradationReason = CloudDegradationReason.NETWORK_TIMEOUT,
                    errorMessage = "Iteration timed out after ${safetyBudget.iterationTimeoutMs}ms"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return AgenticExecutionResult(
                    terminationReason = AgenticLoopTermination.PROVIDER_ERROR,
                    turnsExecuted = iteration,
                    toolOutcomes = allOutcomes,
                    turnHistory = turnHistory,
                    degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
                    errorMessage = e.message ?: "Provider invocation error"
                )
            }

            when (clientResponse) {
                is CloudLlmClientResponse.SafetyBlock -> {
                    return AgenticExecutionResult(
                        terminationReason = AgenticLoopTermination.SAFETY_BLOCKED,
                        turnsExecuted = iteration + 1,
                        toolOutcomes = allOutcomes,
                        turnHistory = turnHistory,
                        degradationReason = CloudDegradationReason.SAFETY_BLOCKED,
                        errorMessage = clientResponse.reason
                    )
                }

                is CloudLlmClientResponse.Timeout -> {
                    return AgenticExecutionResult(
                        terminationReason = AgenticLoopTermination.TIMEOUT,
                        turnsExecuted = iteration + 1,
                        toolOutcomes = allOutcomes,
                        turnHistory = turnHistory,
                        degradationReason = CloudDegradationReason.NETWORK_TIMEOUT,
                        errorMessage = "Provider request timed out"
                    )
                }

                is CloudLlmClientResponse.ProviderError -> {
                    val degradation = if (clientResponse.isRateLimited) {
                        CloudDegradationReason.RATE_LIMITED
                    } else {
                        CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
                    }
                    return AgenticExecutionResult(
                        terminationReason = AgenticLoopTermination.PROVIDER_ERROR,
                        turnsExecuted = iteration + 1,
                        toolOutcomes = allOutcomes,
                        turnHistory = turnHistory,
                        degradationReason = degradation,
                        errorMessage = clientResponse.message
                    )
                }

                is CloudLlmClientResponse.MalformedResponse -> {
                    return AgenticExecutionResult(
                        terminationReason = AgenticLoopTermination.PROVIDER_ERROR,
                        turnsExecuted = iteration + 1,
                        toolOutcomes = allOutcomes,
                        turnHistory = turnHistory,
                        degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
                        errorMessage = clientResponse.reason
                    )
                }

                is CloudLlmClientResponse.Cancelled -> {
                    return AgenticExecutionResult(
                        terminationReason = AgenticLoopTermination.CANCELLED,
                        turnsExecuted = iteration + 1,
                        toolOutcomes = allOutcomes,
                        turnHistory = turnHistory,
                        degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
                    )
                }

                is CloudLlmClientResponse.FinalText -> {
                    val evidenceRecords = allOutcomes.map { outcome ->
                        EngineeringEvidenceRecord.fromOutcome(
                            outcome = outcome,
                            entityType = projectContext.activeEntityType,
                            entityId = projectContext.activeEntityId
                        )
                    }

                    val validationResult = GroundedResponseValidator.validateResponse(
                        text = clientResponse.text,
                        structuredClaims = emptyList(),
                        evidenceList = evidenceRecords,
                        context = projectContext
                    )

                    if (validationResult is GroundingValidationResult.Rejected) {
                        return AgenticExecutionResult(
                            terminationReason = AgenticLoopTermination.GROUNDING_VALIDATION_FAILED,
                            finalText = clientResponse.text,
                            turnsExecuted = iteration + 1,
                            toolOutcomes = allOutcomes,
                            evidenceRecords = evidenceRecords,
                            turnHistory = turnHistory,
                            degradationReason = CloudDegradationReason.GROUNDING_VALIDATION_FAILED,
                            errorMessage = validationResult.details
                        )
                    }

                    return AgenticExecutionResult(
                        terminationReason = AgenticLoopTermination.FINAL_ANSWER_REACHED,
                        finalText = clientResponse.text,
                        turnsExecuted = iteration + 1,
                        toolOutcomes = allOutcomes,
                        evidenceRecords = evidenceRecords,
                        turnHistory = turnHistory
                    )
                }

                is CloudLlmClientResponse.ToolCallRequest -> {
                    val calls = clientResponse.calls
                    if (calls.isEmpty()) {
                        return AgenticExecutionResult(
                            terminationReason = AgenticLoopTermination.PROVIDER_ERROR,
                            turnsExecuted = iteration + 1,
                            toolOutcomes = allOutcomes,
                            turnHistory = turnHistory,
                            degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
                            errorMessage = "Empty tool calls in request"
                        )
                    }

                    // Check total tool call budget
                    if (totalToolCalls + calls.size > safetyBudget.maxTotalToolCalls) {
                        return AgenticExecutionResult(
                            terminationReason = AgenticLoopTermination.MAX_TOOL_CALLS_EXCEEDED,
                            turnsExecuted = iteration + 1,
                            toolOutcomes = allOutcomes,
                            turnHistory = turnHistory,
                            degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
                            errorMessage = "Tool call budget exceeded limit of ${safetyBudget.maxTotalToolCalls}"
                        )
                    }

                    // State-aware repeated call check
                    for (call in calls) {
                        val signature = ToolExecutionSignature(
                            toolName = call.toolName,
                            argumentsFingerprint = call.arguments.toString(),
                            projectId = projectContext.projectId,
                            entityType = projectContext.activeEntityType,
                            entityId = projectContext.activeEntityId,
                            projectRevision = projectContext.liveRevision
                        )
                        val count = signatureCounts.getOrDefault(signature, 0) + 1
                        signatureCounts[signature] = count
                        if (count > safetyBudget.maxRepeatedCalls) {
                            return AgenticExecutionResult(
                                terminationReason = AgenticLoopTermination.REPEATED_TOOL_CALL_DETECTED,
                                turnsExecuted = iteration + 1,
                                toolOutcomes = allOutcomes,
                                turnHistory = turnHistory,
                                degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
                                errorMessage = "Repeated identical tool call detected: ${call.toolName}"
                            )
                        }
                    }

                    totalToolCalls += calls.size

                    // Parallel execution for independent read tools, sequential for dependent/calculation tools
                    val allAreReadTools = calls.all { toolRegistry.isReadTool(it.toolName) }
                    val executedPairs: List<Pair<ToolCall, ToolExecutionOutcome>> = if (allAreReadTools && calls.size > 1) {
                        executeParallelReads(calls, projectContext, conversationId)
                    } else {
                        executeSequential(calls, projectContext, conversationId)
                    }

                    val turnOutcomes = executedPairs.map { it.second }
                    allOutcomes.addAll(turnOutcomes)

                    // Phase 6 Batch 4 (continuation): make the deterministic-tool execution and
                    // the functionResponse handoff observable on a real device. Only tool NAMES and
                    // outcome STATUS are logged - never arguments, payloads or engineering data.
                    AiDiagnostics.stage(
                        AiDiagnostics.Stage.TOOL_EXECUTION,
                        "result" to "OK",
                        "iteration" to iteration,
                        "toolCount" to executedPairs.size,
                        "mode" to if (allAreReadTools && calls.size > 1) "PARALLEL_READS" else "SEQUENTIAL",
                        "toolStatuses" to turnOutcomes.joinToString(",") { it.envelope.status.name }
                    )
                    AiDiagnostics.stage(
                        AiDiagnostics.Stage.FUNCTION_RESPONSE,
                        "result" to "OK",
                        "iteration" to iteration,
                        "responseCount" to executedPairs.size,
                        "names" to executedPairs.joinToString(",") { it.first.toolName },
                        "idsPresent" to executedPairs.count { it.first.callId.isNotBlank() },
                        "signaturesReplayed" to calls.count { !it.thoughtSignature.isNullOrBlank() }
                    )

                    // Add turn items to history
                    turnHistory.add(AgenticTurnItem.ModelCalls(calls))
                    turnHistory.add(AgenticTurnItem.ExecutedTools(executedPairs))
                }
            }
        }

        // Exceeded max iterations without final text
        return AgenticExecutionResult(
            terminationReason = AgenticLoopTermination.MAX_ITERATIONS_EXCEEDED,
            turnsExecuted = safetyBudget.maxIterations,
            toolOutcomes = allOutcomes,
            turnHistory = turnHistory,
            degradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
            errorMessage = "Agentic loop exceeded maximum iterations (${safetyBudget.maxIterations})"
        )
    }

    private suspend fun executeParallelReads(
        calls: List<ToolCall>,
        projectContext: BoundedProjectContext,
        conversationId: String?
    ): List<Pair<ToolCall, ToolExecutionOutcome>> = coroutineScope {
        val semaphore = Semaphore(safetyBudget.maxParallelReads)
        calls.map { call ->
            async {
                semaphore.withPermit {
                    val outcome = dispatchSingleTool(call, projectContext, conversationId)
                    call to outcome
                }
            }
        }.awaitAll()
    }

    private suspend fun executeSequential(
        calls: List<ToolCall>,
        projectContext: BoundedProjectContext,
        conversationId: String?
    ): List<Pair<ToolCall, ToolExecutionOutcome>> {
        val results = mutableListOf<Pair<ToolCall, ToolExecutionOutcome>>()
        for (call in calls) {
            val outcome = dispatchSingleTool(call, projectContext, conversationId)
            results.add(call to outcome)
        }
        return results
    }

    private suspend fun dispatchSingleTool(
        call: ToolCall,
        projectContext: BoundedProjectContext,
        conversationId: String?
    ): ToolExecutionOutcome {
        return try {
            val typedArguments = call.arguments.mapValues { it.value.toAny() }
            toolDispatcher.execute(
                toolName = call.toolName,
                projectId = projectContext.projectId,
                entityType = projectContext.activeEntityType,
                entityId = projectContext.activeEntityId,
                conversationId = conversationId,
                arguments = typedArguments
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ToolExecutionOutcome(
                toolName = call.toolName,
                requestId = call.callId,
                envelope = ToolResultEnvelope.error<Any>(
                    requestId = call.callId,
                    toolName = call.toolName,
                    projectId = projectContext.projectId,
                    errors = listOf(
                        StructuredError(
                            errorCode = "TOOL_EXECUTION_ERROR",
                            message = e.message ?: "Tool execution failed unexpectedly",
                            isRecoverable = false
                        )
                    )
                )
            )
        }
    }
}

/**
 * Converts a [JsonElement] to its corresponding native Kotlin type while preserving
 * numbers, booleans, arrays, nested maps, and nulls (Correction #1).
 */
fun JsonElement.toAny(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> {
        if (isString) {
            content
        } else {
            booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: content
        }
    }
    is JsonArray -> map { it.toAny() }
    is JsonObject -> mapValues { it.value.toAny() }
}
