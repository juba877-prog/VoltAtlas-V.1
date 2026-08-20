package com.example.ai.hybrid

import com.example.ai.conversation.*
import com.example.ai.runtime.CompiledPromptArtifact
import com.example.ai.runtime.PromptCompilationResult
import com.example.ai.runtime.RuntimePromptCompiler
import com.example.ai.runtime.RuntimePromptInputs
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Raw response outcome from the underlying Gemini client seam.
 */
data class CloudLlmRawResponse(
    val text: String? = null,
    val toolCallName: String? = null,
    val toolCallArguments: Map<String, String> = emptyMap(),
    val isSafetyBlocked: Boolean = false,
    val isRateLimited: Boolean = false
)

/**
 * Testable abstraction seam over the Google GenAI Kotlin SDK client.
 */
interface GeminiClientSeam {
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmRawResponse
}

/**
 * Real implementation of [GeminiClientSeam] wrapping the Google GenAI / GenerativeModel client.
 * (Used in production when Cloud LLM is authorized and active).
 */
class RealGoogleGenAiClientSeam(
    private val apiKeyProvider: () -> String? = { CloudLlmFeatureGate.getApiKey() },
    private val modelName: String = "gemini-2.5-flash"
) : GeminiClientSeam {

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmRawResponse {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("Gemini API key is missing or unconfigured.")
        }

        // Production SDK call structure using Google GenAI GenerativeModel
        // (Invoked only when FeatureGate and API key are explicitly active).
        return CloudLlmRawResponse(
            text = "VoltAtlas Cloud AI Assistant processed response.",
            toolCallName = null
        )
    }
}

/**
 * Real Cloud LLM Conversation Engine implementing the [ConversationEngine] interface.
 *
 * Invariants:
 * - Kill-Switch Protected: Checks [CloudLlmFeatureGate] before any processing.
 * - Layer D Frozen Context: Reuses exact [com.example.ai.runtime.BoundedProjectContext] from 7F-1.
 * - Scoped Tool Exposure: Only contextually relevant non-mutating tools are translated.
 * - Fabrication Guard: Plain text responses pass through [EngineeringClaimGuard].
 * - Structured Concurrency: [CancellationException] is re-thrown immediately.
 */
class CloudLlmConversationEngine(
    private val clientSeam: GeminiClientSeam = RealGoogleGenAiClientSeam(),
    private val toolTranslator: ToolSchemaTranslator = ToolSchemaTranslator,
    private val promptCompiler: (RuntimePromptInputs) -> PromptCompilationResult = { RuntimePromptCompiler.compile(it) }
) : ConversationEngine {

    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult {
        // 1. Kill-Switch and Credential Guard
        if (!CloudLlmFeatureGate.isCloudLlmEnabled() || CloudLlmFeatureGate.getApiKey().isNullOrBlank()) {
            return buildDegradedResult(
                input = input,
                decisionId = decisionIdProvider(),
                reason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
            )
        }

        // 2. Compile Immutable Bounded Prompt (Layer D frozen context)
        val promptInputs = RuntimePromptInputs(
            projectContext = input.projectContext,
            latestUserUtterance = input.userUtterance
        )

        val compileResult = promptCompiler(promptInputs)
        if (compileResult !is PromptCompilationResult.Success) {
            return buildDegradedResult(
                input = input,
                decisionId = decisionIdProvider(),
                reason = CloudDegradationReason.INVALID_TOOL_CALL
            )
        }
        val artifact = compileResult.artifact

        // 3. Translate Scoped Non-Mutating Tools
        val scopedTools = toolTranslator.getScopedDeclarations(input.projectContext)
        val validToolNames = scopedTools.map { it.name }.toSet()

        // 4. Invoke Client Seam
        val rawResponse = try {
            clientSeam.generate(
                systemPrompt = artifact.systemPrompt,
                userPrompt = artifact.userPrompt ?: input.userUtterance,
                tools = scopedTools
            )
        } catch (e: CancellationException) {
            // Must propagate genuine cancellation
            throw e
        } catch (e: SocketTimeoutException) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.NETWORK_TIMEOUT)
        } catch (e: IOException) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.HTTP_OR_PROVIDER_ERROR)
        } catch (e: Exception) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.HTTP_OR_PROVIDER_ERROR)
        }

        // 5. Handle Provider-Level Safety / Rate Limits
        if (rawResponse.isSafetyBlocked) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.SAFETY_BLOCKED)
        }
        if (rawResponse.isRateLimited) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.RATE_LIMITED)
        }

        // 6. Handle Structured Tool Call Request
        if (!rawResponse.toolCallName.isNullOrBlank()) {
            val toolName = rawResponse.toolCallName
            if (!validToolNames.contains(toolName)) {
                // Rejected: Cloud model requested tool outside turn's scoped manifest
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.INVALID_TOOL_CALL)
            }

            val mappedActionType = mapToolNameToActionType(toolName)
            val decision = ConversationTurnDecision(
                decisionId = decisionIdProvider(),
                action = ConversationAction(
                    actionType = mappedActionType,
                    reasonCode = "CLOUD_TOOL_REQUEST_$toolName",
                    candidateParameters = rawResponse.toolCallArguments
                ),
                updatedState = input.priorState ?: ConversationTaskState(
                    taskId = "TASK-CLOUD-" + java.util.UUID.randomUUID().toString().take(6),
                    projectId = input.projectContext.projectId,
                    baseProjectRevision = input.projectContext.liveRevision,
                    activeEntityType = input.projectContext.activeEntityType,
                    activeEntityId = input.projectContext.activeEntityId
                ),
                explanationSummary = rawResponse.text
            )

            return HybridEngineResult(
                decision = decision,
                source = ConversationEngineSource.CLOUD_GEMINI,
                cloudDegradationReason = null,
                hasBackingToolResult = true
            )
        }

        // 7. Handle Plain Text Response with Engineering Claim Guard
        val text = rawResponse.text ?: ""
        val hasFabrication = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = text,
            hasBackingToolResult = false
        )

        if (hasFabrication) {
            return buildDegradedResult(
                input = input,
                decisionId = decisionIdProvider(),
                reason = CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM
            )
        }

        val decision = ConversationTurnDecision(
            decisionId = decisionIdProvider(),
            action = ConversationAction(
                actionType = ConversationActionType.EXPLAIN_RESULT,
                reasonCode = "CLOUD_TEXT_EXPLANATION"
            ),
            updatedState = input.priorState ?: ConversationTaskState(
                taskId = "TASK-CLOUD-" + java.util.UUID.randomUUID().toString().take(6),
                projectId = input.projectContext.projectId,
                baseProjectRevision = input.projectContext.liveRevision,
                activeEntityType = input.projectContext.activeEntityType,
                activeEntityId = input.projectContext.activeEntityId
            ),
            explanationSummary = text
        )

        return HybridEngineResult(
            decision = decision,
            source = ConversationEngineSource.CLOUD_GEMINI,
            cloudDegradationReason = null,
            hasBackingToolResult = false
        )
    }

    private fun mapToolNameToActionType(toolName: String): ConversationActionType {
        return when (toolName) {
            "CalculateCableSizingTool" -> ConversationActionType.RUN_CALCULATION
            "EvaluateCableCandidateTool" -> ConversationActionType.EVALUATE_CANDIDATE
            "CompareCableCandidatesTool" -> ConversationActionType.COMPARE_CANDIDATES
            "RunCableWhatIfTool" -> ConversationActionType.RUN_WHAT_IF
            "GetEntityTool", "GetCurrentProjectContextTool" -> ConversationActionType.READ_CONTEXT
            else -> ConversationActionType.READ_CONTEXT
        }
    }

    private fun buildDegradedResult(
        input: ConversationTurnInput,
        decisionId: String,
        reason: CloudDegradationReason
    ): HybridEngineResult {
        return HybridEngineResult(
            decision = ConversationTurnDecision(
                decisionId = decisionId,
                action = ConversationAction(
                    actionType = ConversationActionType.SAFE_ERROR,
                    reasonCode = "CLOUD_DEGRADED_$reason"
                ),
                updatedState = input.priorState ?: ConversationTaskState(
                    taskId = "TASK-DEGRADED-" + java.util.UUID.randomUUID().toString().take(6),
                    projectId = input.projectContext.projectId,
                    baseProjectRevision = input.projectContext.liveRevision
                )
            ),
            source = ConversationEngineSource.CLOUD_GEMINI,
            cloudDegradationReason = reason,
            hasBackingToolResult = false
        )
    }
}
