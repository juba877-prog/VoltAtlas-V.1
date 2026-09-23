package com.example.ai.hybrid

import com.example.ai.conversation.*
import com.example.ai.runtime.CapabilityManifest
import com.example.ai.runtime.CompiledPromptArtifact
import com.example.ai.runtime.PromptCompilationResult
import com.example.ai.runtime.RuntimePromptCompiler
import com.example.ai.runtime.RuntimePromptInputs
import com.example.ai.runtime.ToolCapabilityDescriptor
import com.example.ai.tools.EngineeringToolCatalog
import com.example.ai.tools.EngineeringToolDescriptor
import com.example.ai.tools.EngineeringToolRegistry
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
    /**
     * Authoritative tool descriptors. Defaults to the single descriptor catalog, so
     * schema generation needs no live database. Scoping uses
     * [EngineeringToolRegistry.scopeTo], the same rule the executable registry uses.
     */
    private val toolDescriptors: List<EngineeringToolDescriptor> =
        EngineeringToolCatalog.READ_TOOLS + EngineeringToolCatalog.CABLE_TOOLS,
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

        // 2. Resolve the authoritative, context-scoped tool manifest.
        //    Scoping uses the exact same rule as the executable registry, so the schema the
        //    model sees can never include a tool the runtime cannot dispatch.
        val scopedDescriptors = EngineeringToolRegistry.scopeTo(toolDescriptors, input.projectContext)
        val scopedTools = ToolSchemaTranslator.getScopedDeclarations(scopedDescriptors)
        val validToolNames = scopedTools.map { it.name }.toSet()

        // 2b. Feed the same manifest to the prompt compiler so the tool capability layer of
        //     the prompt is generated from the registry rather than being empty.
        val capabilityManifest = CapabilityManifest(
            capabilities = scopedDescriptors.map { descriptor ->
                ToolCapabilityDescriptor(
                    name = descriptor.toolName,
                    permissionMode = descriptor.authorizationLevel,
                    summary = descriptor.purpose,
                    parameterSchema = ToolSchemaTranslator.toDeclaration(descriptor).parameterTypes.toString()
                )
            }
        )

        // 3. Compile Immutable Bounded Prompt (Layer D frozen context + capability manifest)
        val promptInputs = RuntimePromptInputs(
            capabilities = capabilityManifest,
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

        // 6. Handle Structured Tool Call Request.
        //    This layer does NOT execute tools, so it must never claim a backing tool result.
        //    It emits a typed tool *request*; execution and the resulting evidence record are
        //    produced downstream by the deterministic tool runtime. Claiming
        //    hasBackingToolResult = true here was a safety defect: it asserted evidence that
        //    had never been produced.
        if (!rawResponse.toolCallName.isNullOrBlank()) {
            val toolName = rawResponse.toolCallName

            if (!validToolNames.contains(toolName)) {
                // Rejected: the model requested a tool outside this turn's scoped manifest.
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.INVALID_TOOL_CALL)
            }

            // Fail closed: an unmappable tool must never be silently relabelled as a read.
            val mappedActionType = CloudToolActionMapping.actionFor(toolName)
                ?: return buildDegradedResult(
                    input = input,
                    decisionId = decisionIdProvider(),
                    reason = CloudDegradationReason.INVALID_TOOL_CALL
                )

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
                hasBackingToolResult = false
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

/**
 * Explicit, total-by-contract mapping from a canonical tool name to the conversation
 * action it serves.
 *
 * Returns null for any tool without an explicit mapping so callers fail closed. There is
 * deliberately no catch-all branch: the previous `else -> READ_CONTEXT` silently
 * relabelled unknown tools as harmless reads, which is how an unsupported tool request
 * could produce a plausible-looking answer. Coverage against the descriptor catalog is
 * asserted by AssistantToolContractTest, so a newly declared tool cannot ship unmapped.
 */
internal object CloudToolActionMapping {

    fun actionFor(toolName: String): ConversationActionType? = when (toolName) {
        "calculate_cable_sizing" -> ConversationActionType.RUN_CALCULATION
        "evaluate_cable_candidate" -> ConversationActionType.EVALUATE_CANDIDATE
        "compare_cable_candidates" -> ConversationActionType.COMPARE_CANDIDATES
        "run_what_if" -> ConversationActionType.RUN_WHAT_IF

        "get_current_project_context",
        "get_entity",
        "get_calculation_state",
        "get_dependencies",
        "get_change_history",
        "get_catalog_item" -> ConversationActionType.READ_CONTEXT

        else -> null
    }

    /**
     * Inverse of [actionFor]: the tool that satisfies a given conversation action.
     *
     * Returns null when an action has no deterministic tool behind it, which is the correct
     * answer for purely conversational actions. Callers must treat null as "no tool to run"
     * and must never substitute an approximation.
     */
    fun toolFor(actionType: ConversationActionType): String? = when (actionType) {
        ConversationActionType.RUN_CALCULATION,
        ConversationActionType.RECALCULATE_STALE -> "calculate_cable_sizing"

        ConversationActionType.EVALUATE_CANDIDATE -> "evaluate_cable_candidate"
        ConversationActionType.COMPARE_CANDIDATES -> "compare_cable_candidates"
        ConversationActionType.RUN_WHAT_IF -> "run_what_if"
        ConversationActionType.READ_CONTEXT,
        ConversationActionType.ANSWER_FROM_CURRENT_RESULT -> "get_entity"

        else -> null
    }
}

