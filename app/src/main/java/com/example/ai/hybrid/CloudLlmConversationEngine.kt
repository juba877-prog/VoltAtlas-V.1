package com.example.ai.hybrid

import com.example.ai.context.ContextResolver
import com.example.ai.conversation.*
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.orchestrator.AgenticTurnItem
import com.example.ai.orchestrator.AgenticToolLoopOrchestrator
import com.example.ai.orchestrator.MultiTurnGeminiSeam
import com.example.ai.runtime.ActiveConversationStateSummary
import com.example.ai.runtime.CapabilityManifest
import com.example.ai.runtime.CompiledPromptArtifact
import com.example.ai.runtime.PromptCompilationResult
import com.example.ai.runtime.ResponseMode
import com.example.ai.runtime.RuntimePromptCompiler
import com.example.ai.runtime.RuntimePromptInputs
import com.example.ai.runtime.ToolCapabilityDescriptor
import com.example.ai.tools.EngineeringToolCatalog
import com.example.ai.tools.EngineeringToolDescriptor
import com.example.ai.tools.EngineeringToolRegistry
import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Raw response outcome from the underlying Gemini client seam.
 *
 * Backed by strongly-typed [CloudLlmResponseType] and [CloudLlmClientResponse] (Gate 4B & 5).
 */
data class CloudLlmRawResponse(
    val text: String? = null,
    val toolCallName: String? = null,
    val toolCallArguments: Map<String, JsonElement> = emptyMap(),
    val isSafetyBlocked: Boolean = false,
    val isRateLimited: Boolean = false,
    val responseType: CloudLlmResponseType = when {
        isSafetyBlocked -> CloudLlmResponseType.SAFETY_BLOCK
        isRateLimited -> CloudLlmResponseType.PROVIDER_ERROR
        !toolCallName.isNullOrBlank() -> CloudLlmResponseType.TOOL_CALL_REQUEST
        else -> CloudLlmResponseType.FINAL_TEXT
    },
    val errorMessage: String? = null
) {
    companion object {
        fun fromClientResponse(response: CloudLlmClientResponse): CloudLlmRawResponse = when (response) {
            is CloudLlmClientResponse.FinalText -> CloudLlmRawResponse(
                text = response.text,
                responseType = CloudLlmResponseType.FINAL_TEXT
            )
            is CloudLlmClientResponse.ToolCallRequest -> CloudLlmRawResponse(
                text = null,
                toolCallName = response.toolName,
                toolCallArguments = response.arguments,
                responseType = CloudLlmResponseType.TOOL_CALL_REQUEST
            )
            is CloudLlmClientResponse.ProviderError -> CloudLlmRawResponse(
                isRateLimited = response.isRateLimited,
                errorMessage = response.message,
                responseType = CloudLlmResponseType.PROVIDER_ERROR
            )
            is CloudLlmClientResponse.MalformedResponse -> CloudLlmRawResponse(
                errorMessage = response.reason,
                responseType = CloudLlmResponseType.MALFORMED_RESPONSE
            )
            is CloudLlmClientResponse.SafetyBlock -> CloudLlmRawResponse(
                isSafetyBlocked = true,
                errorMessage = response.reason,
                responseType = CloudLlmResponseType.SAFETY_BLOCK
            )
            is CloudLlmClientResponse.Cancelled -> CloudLlmRawResponse(
                responseType = CloudLlmResponseType.CANCELLED
            )
            is CloudLlmClientResponse.Timeout -> CloudLlmRawResponse(
                errorMessage = "Request timed out after ${response.timeoutMs}ms",
                responseType = CloudLlmResponseType.TIMEOUT
            )
        }
    }
}

/**
 * Testable abstraction seam over the Google GenAI / Firebase AI Logic Kotlin SDK client.
 * Supports both single-turn generation and provider-neutral multi-turn agentic loops.
 */
interface GeminiClientSeam : MultiTurnGeminiSeam {
    fun isConfigured(): Boolean = !CloudLlmFeatureGate.getApiKey().isNullOrBlank()

    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmRawResponse

    override suspend fun generateTurn(
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<AgenticTurnItem>,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmClientResponse = CloudLlmClientResponse.FinalText("")
}

/**
 * Real implementation of [GeminiClientSeam] wrapping Firebase AI Logic's [GenerativeModel].
 *
 * Invariants:
 * - Feature-gate protected: verifies [CloudLlmFeatureGate] before initiating SDK calls.
 * - Centralized Configuration: reads model identifier and generation hyperparameters from [AiModelConfig].
 * - Security: No API keys or secrets in source code, logs, or error strings; relies on Firebase / App Check.
 * - Schema-driven: Function declarations derive from the authoritative [EngineeringToolRegistry].
 * - Canonical names: Preserves canonical snake_case tool names across all layers.
 * - Typed contract: Distinguishes FINAL_TEXT, TOOL_CALL_REQUEST, PROVIDER_ERROR, MALFORMED_RESPONSE,
 *   SAFETY_BLOCK, CANCELLED, and TIMEOUT.
 * - Multi-turn Firebase semantics (Correction #6): maps [AgenticTurnItem] to Firebase [Content] parts.
 */
class RealGoogleGenAiClientSeam(
    val config: AiModelConfig = AiModelConfig.current(),
    private val modelFactory: ((config: AiModelConfig, systemPrompt: String, tools: List<CloudFunctionDeclaration>) -> GenerativeModel)? = null,
    private val contentGenerator: (suspend (systemPrompt: String, userPrompt: String, tools: List<CloudFunctionDeclaration>) -> GenerateContentResponse)? = null,
    private val turnGenerator: (suspend (systemPrompt: String, userPrompt: String, history: List<AgenticTurnItem>, tools: List<CloudFunctionDeclaration>) -> GenerateContentResponse)? = null
) : GeminiClientSeam {

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmRawResponse {
        val clientResponse = generateTurn(systemPrompt, userPrompt, emptyList(), tools)
        return CloudLlmRawResponse.fromClientResponse(clientResponse)
    }

    suspend fun generateTyped(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmClientResponse {
        return generateTurn(systemPrompt, userPrompt, emptyList(), tools)
    }

    override suspend fun generateTurn(
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<AgenticTurnItem>,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmClientResponse {
        // 1. Kill-Switch: CloudLlmFeatureGate must be explicitly enabled
        if (!CloudLlmFeatureGate.isCloudLlmEnabled()) {
            return CloudLlmClientResponse.ProviderError(
                message = "Cloud LLM is disabled by configuration feature gate."
            )
        }

        return try {
            val response: GenerateContentResponse = withTimeout(config.timeoutMs) {
                if (turnGenerator != null) {
                    turnGenerator.invoke(systemPrompt, userPrompt, conversationHistory, tools)
                } else if (contentGenerator != null && conversationHistory.isEmpty()) {
                    contentGenerator.invoke(systemPrompt, userPrompt, tools)
                } else {
                    val generativeModel = modelFactory?.invoke(config, systemPrompt, tools)
                        ?: buildDefaultGenerativeModel(config, systemPrompt, tools)

                    val contents = buildConversationContents(userPrompt, conversationHistory)
                    generativeModel.generateContent(contents)
                }
            }

            parseResponse(response)
        } catch (e: TimeoutCancellationException) {
            CloudLlmClientResponse.Timeout(config.timeoutMs)
        } catch (e: CancellationException) {
            // Must propagate genuine coroutine cancellation
            throw e
        } catch (e: SocketTimeoutException) {
            CloudLlmClientResponse.Timeout(config.timeoutMs)
        } catch (e: RequestTimeoutException) {
            CloudLlmClientResponse.Timeout(config.timeoutMs)
        } catch (e: PromptBlockedException) {
            CloudLlmClientResponse.SafetyBlock(e.message ?: "Prompt blocked by safety filters.")
        } catch (e: ContentBlockedException) {
            CloudLlmClientResponse.SafetyBlock(e.message ?: "Content blocked by safety filters.")
        } catch (e: QuotaExceededException) {
            CloudLlmClientResponse.ProviderError(
                message = e.message ?: "Quota exceeded.",
                cause = e,
                isRateLimited = true
            )
        } catch (e: FirebaseAIException) {
            CloudLlmClientResponse.ProviderError(
                message = e.message ?: "Firebase AI provider error.",
                cause = e
            )
        } catch (e: Throwable) {
            CloudLlmClientResponse.ProviderError(
                message = e.message ?: "Unexpected cloud generation error.",
                cause = e
            )
        }
    }

    internal fun parseResponse(response: GenerateContentResponse): CloudLlmClientResponse {
        val candidate = response.candidates.firstOrNull()

        if (candidate != null) {
            val finishReason = candidate.finishReason
            if (finishReason != null) {
                when (finishReason) {
                    FinishReason.SAFETY,
                    FinishReason.BLOCKLIST,
                    FinishReason.PROHIBITED_CONTENT,
                    FinishReason.SPII -> {
                        return CloudLlmClientResponse.SafetyBlock(
                            reason = candidate.finishMessage ?: "Blocked by safety filter (${finishReason.name})"
                        )
                    }
                    FinishReason.MALFORMED_FUNCTION_CALL -> {
                        return CloudLlmClientResponse.MalformedResponse(
                            reason = "Provider returned malformed function call: ${candidate.finishMessage.orEmpty()}"
                        )
                    }
                    FinishReason.MALFORMED_RESPONSE -> {
                        return CloudLlmClientResponse.MalformedResponse(
                            reason = "Provider returned malformed response: ${candidate.finishMessage.orEmpty()}"
                        )
                    }
                    else -> { /* Proceed */ }
                }
            }
        }

        // 1. Structured function / tool calls (preserves typed arguments and multiple calls)
        val functionCalls = response.functionCalls
        if (functionCalls.isNotEmpty()) {
            val calls = functionCalls.map { call ->
                ToolCall(
                    toolName = call.name,
                    arguments = call.args
                )
            }
            return CloudLlmClientResponse.ToolCallRequest(calls = calls)
        }

        // 2. Plain text response
        val text = response.text
        if (!text.isNullOrBlank()) {
            return CloudLlmClientResponse.FinalText(text = text)
        }

        // 3. Neither valid text nor function call
        return CloudLlmClientResponse.MalformedResponse(
            reason = "Response contained neither valid text nor function calls."
        )
    }

    private fun buildDefaultGenerativeModel(
        config: AiModelConfig,
        systemPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): GenerativeModel {
        val firebaseAi = Firebase.ai

        val generationConfig = generationConfig {
            temperature = config.temperature
            topP = config.topP
            topK = config.topK
            config.maxOutputTokens?.let { maxOutputTokens = it }
        }

        val firebaseTools = if (tools.isNotEmpty()) {
            val declarations = tools.map { toFirebaseFunctionDeclaration(it) }
            listOf(Tool.functionDeclarations(declarations))
        } else {
            emptyList()
        }

        val systemInstruction = if (systemPrompt.isNotBlank()) {
            Content(parts = listOf(TextPart(systemPrompt)))
        } else null

        return firebaseAi.generativeModel(
            modelName = config.modelName,
            generationConfig = generationConfig,
            safetySettings = emptyList(),
            tools = firebaseTools,
            toolConfig = null,
            systemInstruction = systemInstruction,
            requestOptions = RequestOptions(config.timeoutMs)
        )
    }

    companion object {
        fun buildConversationContents(
            userPrompt: String,
            conversationHistory: List<AgenticTurnItem>
        ): List<Content> {
            val contents = mutableListOf<Content>()
            contents.add(Content(role = "user", parts = listOf(TextPart(userPrompt))))

            for (turn in conversationHistory) {
                when (turn) {
                    is AgenticTurnItem.ModelCalls -> {
                        val parts = turn.calls.map { call ->
                            FunctionCallPart(
                                name = call.toolName,
                                args = call.arguments
                            )
                        }
                        contents.add(Content(role = "model", parts = parts))
                    }
                    is AgenticTurnItem.ExecutedTools -> {
                        val parts = turn.outcomes.map { (call, outcome) ->
                            FunctionResponsePart(
                                name = call.toolName,
                                response = ToolResultProviderSerializer.toProviderResponse(outcome)
                            )
                        }
                        contents.add(Content(role = "user", parts = parts))
                    }
                }
            }
            return contents
        }

        fun toFirebaseFunctionDeclaration(declaration: CloudFunctionDeclaration): FunctionDeclaration {
            val parameters = declaration.parameters.associate { param ->
                param.name to toFirebaseSchema(param)
            }
            val optionalParameters = declaration.parameters
                .filterNot { it.required }
                .map { it.name }

            return FunctionDeclaration(
                name = declaration.name,
                description = declaration.description,
                parameters = parameters,
                optionalParameters = optionalParameters
            )
        }

        fun toFirebaseSchema(parameter: CloudFunctionParameter): Schema = when (parameter.type) {
            "STRING" -> Schema.string(
                description = parameter.description,
                nullable = !parameter.required
            )
            "INTEGER" -> Schema.integer(
                description = parameter.description,
                nullable = !parameter.required
            )
            "NUMBER" -> Schema.double(
                description = parameter.description,
                nullable = !parameter.required
            )
            "BOOLEAN" -> Schema.boolean(
                description = parameter.description,
                nullable = !parameter.required
            )
            "ARRAY<STRING>" -> Schema.array(
                items = Schema.string(),
                description = parameter.description,
                nullable = !parameter.required
            )
            "ARRAY<OBJECT>" -> {
                val itemSchema = if (parameter.fields.isNotEmpty()) {
                    val props = parameter.fields.associate { it.name to toFirebaseSchema(it) }
                    val optionalProps = parameter.fields.filterNot { it.required }.map { it.name }
                    Schema.obj(
                        properties = props,
                        optionalProperties = optionalProps
                    )
                } else {
                    Schema.obj(properties = emptyMap())
                }
                Schema.array(
                    items = itemSchema,
                    description = parameter.description,
                    nullable = !parameter.required
                )
            }
            "OBJECT" -> {
                val props = parameter.fields.associate { it.name to toFirebaseSchema(it) }
                val optionalProps = parameter.fields.filterNot { it.required }.map { it.name }
                Schema.obj(
                    properties = props,
                    optionalProperties = optionalProps,
                    description = parameter.description,
                    nullable = !parameter.required
                )
            }
            else -> Schema.string(
                description = parameter.description,
                nullable = true
            )
        }
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
 * - Multi-turn Loop: If an [orchestrator] is supplied, delegates multi-turn tool execution.
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
    private val promptCompiler: (RuntimePromptInputs) -> PromptCompilationResult = { RuntimePromptCompiler.compile(it, maxBudgetCharacters = 24_000) },
    private val orchestrator: AgenticToolLoopOrchestrator? = null,
    private val orchestratorProvider: (() -> AgenticToolLoopOrchestrator?)? = null
) : ConversationEngine {

    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult {
        // 1. Kill-Switch and Credential Guard
        val isConfigured = clientSeam.isConfigured() || !CloudLlmFeatureGate.getApiKey().isNullOrBlank()
        val isEnabled = CloudLlmFeatureGate.isCloudLlmEnabled() || clientSeam.isConfigured()
        if (!isEnabled || !isConfigured) {
            val reason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
            AiLogger.w("VoltAtlas_AI", "AI_RUNTIME: cloud degraded before dispatch. isEnabled=$isEnabled, isConfigured=$isConfigured, fallbackReason=$reason")
            return buildDegradedResult(
                input = input,
                decisionId = decisionIdProvider(),
                reason = reason
            )
        }

        // 1b. Gate 7 Context & Slot Resolution (Pure, deterministic, no Room mutation)
        val resolved = ContextResolver.resolve(
            userUtterance = input.userUtterance,
            projectContext = input.projectContext,
            priorState = input.priorState,
            availableEntities = input.availableEntities,
            historicalEvidence = input.priorEvidence
        )

        // 1c. Ambiguity Check: fail closed if multiple entities match an indirect reference
        if (resolved.isAmbiguousEntity && resolved.ambiguousCandidates.size > 1) {
            val candidateNames = resolved.ambiguousCandidates.joinToString(", ") {
                it.entityName?.takeIf { name -> name.isNotBlank() } ?: "${it.entityType}:${it.entityId}"
            }
            val question = if (resolved.updatedTaskState.userLanguage == ConversationLanguage.AR) {
                "يوجد أكثر من عنصر مطابق ($candidateNames). يرجى تحديد العنصر المطلوب بدقة للمتابعة."
            } else {
                "Multiple matching entities found ($candidateNames). Please specify which entity you mean."
            }
            val decision = ConversationTurnDecision(
                decisionId = decisionIdProvider(),
                action = ConversationAction(
                    actionType = ConversationActionType.ASK_BLOCKING_QUESTION,
                    reasonCode = "AMBIGUOUS_ENTITY",
                    candidateParameters = mapOf("candidates" to candidateNames)
                ),
                updatedState = resolved.updatedTaskState,
                explanationSummary = question
            )
            return HybridEngineResult(
                decision = decision,
                source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                cloudDegradationReason = null,
                hasBackingToolResult = false,
                evidence = input.priorEvidence
            )
        }

        // 2. Resolve the authoritative, context-scoped tool manifest.
        val effectiveProjectContext = input.projectContext.copy(
            activeEntityType = resolved.activeEntity?.entityType ?: input.projectContext.activeEntityType,
            activeEntityId = resolved.activeEntity?.entityId ?: input.projectContext.activeEntityId,
            activeEntityName = resolved.activeEntity?.entityName ?: input.projectContext.activeEntityName
        )
        val scopedDescriptors = EngineeringToolRegistry.scopeTo(toolDescriptors, effectiveProjectContext)
        val scopedTools = ToolSchemaTranslator.getScopedDeclarations(scopedDescriptors)
        val validToolNames = scopedTools.map { it.name }.toSet()

        // 2b. Capability manifest for prompt compiler
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

        // 3. Compile Immutable Bounded Prompt with Layer E active conversation state summary
        val conversationSummary = ActiveConversationStateSummary(
            taskId = resolved.updatedTaskState.taskId,
            intentName = resolved.updatedTaskState.primaryIntent.name,
            engineeringObjective = resolved.updatedTaskState.engineeringObjective.name,
            boundEntityType = resolved.activeEntity?.entityType ?: resolved.updatedTaskState.activeEntityType,
            boundEntityId = resolved.activeEntity?.entityId ?: resolved.updatedTaskState.activeEntityId,
            knownSlots = resolved.resolvedSlots.mapValues { it.value.value ?: "" },
            missingBlockingSlots = resolved.missingBlockingSlots,
            detectedContradictions = resolved.detectedContradictions.map { "${it.fieldName}: project=${it.projectValue} vs user=${it.userValue}" },
            isWhatIfActive = resolved.updatedTaskState.isWhatIfBranch,
            whatIfParameters = resolved.updatedTaskState.typedWhatIfOverrides.mapValues { 
                it.value.formattedDisplay ?: it.value.textValue ?: it.value.decimalValue?.toString() ?: "" 
            },
            activeProposalId = resolved.updatedTaskState.activeProposalId,
            activeProposalDigest = resolved.updatedTaskState.activeProposalDigest,
            activeProposalSemanticKey = resolved.updatedTaskState.activeProposalSemanticKey,
            recentEvidenceReferences = resolved.evidenceSummaries.map { it.toBoundedPromptLine() }
        )

        val promptInputs = RuntimePromptInputs(
            capabilities = capabilityManifest,
            projectContext = effectiveProjectContext,
            conversationSummary = conversationSummary,
            latestUserUtterance = input.userUtterance
        )

        val compileResult = promptCompiler(promptInputs)
        if (compileResult !is PromptCompilationResult.Success) {
            val diag = (compileResult as? PromptCompilationResult.PromptBudgetExceeded)?.diagnostic ?: "Unknown prompt failure"
            AiLogger.w("VoltAtlas_AI", "AI_RUNTIME: prompt compilation failed: $diag. Falling back to local engine.")
            return buildDegradedResult(
                input = input,
                decisionId = decisionIdProvider(),
                reason = CloudDegradationReason.INVALID_TOOL_CALL,
                fallbackState = resolved.updatedTaskState
            )
        }
        val artifact = compileResult.artifact

        // 4. Multi-turn Agentic Loop branch (if orchestrator is present)
        val activeOrchestrator = orchestrator ?: orchestratorProvider?.invoke()
        if (activeOrchestrator != null) {
            val agenticResult = activeOrchestrator.executeLoop(
                systemPrompt = artifact.systemPrompt,
                userPrompt = artifact.userPrompt ?: input.userUtterance,
                projectContext = effectiveProjectContext,
                scopedTools = scopedTools,
                conversationId = resolved.updatedTaskState.taskId
            )

            if (agenticResult.degradationReason != null) {
                return buildDegradedResult(input, decisionIdProvider(), agenticResult.degradationReason, resolved.updatedTaskState)
            }

            val combinedEvidence = (input.priorEvidence + agenticResult.evidenceRecords).distinctBy { it.evidenceId }
            val finalTaskState = resolved.updatedTaskState.copy(
                evidenceReferences = combinedEvidence.map { it.evidenceId }
            )

            val decision = ConversationTurnDecision(
                decisionId = decisionIdProvider(),
                action = ConversationAction(
                    actionType = ConversationActionType.EXPLAIN_RESULT,
                    reasonCode = if (agenticResult.toolOutcomes.isNotEmpty()) "AGENTIC_LOOP_ANSWER_WITH_TOOLS" else "CLOUD_TEXT_EXPLANATION"
                ),
                updatedState = finalTaskState,
                explanationSummary = agenticResult.finalText
            )

            return HybridEngineResult(
                decision = decision,
                source = ConversationEngineSource.CLOUD_GEMINI,
                cloudDegradationReason = null,
                hasBackingToolResult = agenticResult.toolOutcomes.isNotEmpty(),
                evidence = combinedEvidence,
                groundingResult = GroundingValidationResult.Valid
            )
        }

        // 5. Fallback: Single-Turn Client Seam Execution
        val rawResponse = try {
            clientSeam.generate(
                systemPrompt = artifact.systemPrompt,
                userPrompt = artifact.userPrompt ?: input.userUtterance,
                tools = scopedTools
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.NETWORK_TIMEOUT, resolved.updatedTaskState)
        } catch (e: IOException) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.HTTP_OR_PROVIDER_ERROR, resolved.updatedTaskState)
        } catch (e: Exception) {
            return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.HTTP_OR_PROVIDER_ERROR, resolved.updatedTaskState)
        }

        // 6. Handle Provider-Level Safety / Rate Limits / Typed Errors
        when (rawResponse.responseType) {
            CloudLlmResponseType.SAFETY_BLOCK -> {
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.SAFETY_BLOCKED, resolved.updatedTaskState)
            }
            CloudLlmResponseType.TIMEOUT -> {
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.NETWORK_TIMEOUT, resolved.updatedTaskState)
            }
            CloudLlmResponseType.PROVIDER_ERROR -> {
                val reason = if (rawResponse.isRateLimited) CloudDegradationReason.RATE_LIMITED else CloudDegradationReason.HTTP_OR_PROVIDER_ERROR
                return buildDegradedResult(input, decisionIdProvider(), reason, resolved.updatedTaskState)
            }
            CloudLlmResponseType.MALFORMED_RESPONSE -> {
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.HTTP_OR_PROVIDER_ERROR, resolved.updatedTaskState)
            }
            CloudLlmResponseType.CANCELLED -> {
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.HTTP_OR_PROVIDER_ERROR, resolved.updatedTaskState)
            }
            CloudLlmResponseType.TOOL_CALL_REQUEST,
            CloudLlmResponseType.FINAL_TEXT -> {
                if (rawResponse.isSafetyBlocked) {
                    return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.SAFETY_BLOCKED, resolved.updatedTaskState)
                }
                if (rawResponse.isRateLimited) {
                    return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.RATE_LIMITED, resolved.updatedTaskState)
                }
            }
        }

        // 7. Handle Structured Tool Call Request
        if (!rawResponse.toolCallName.isNullOrBlank()) {
            val toolName = rawResponse.toolCallName

            if (!validToolNames.contains(toolName)) {
                return buildDegradedResult(input, decisionIdProvider(), CloudDegradationReason.INVALID_TOOL_CALL, resolved.updatedTaskState)
            }

            val mappedActionType = CloudToolActionMapping.actionFor(toolName)
                ?: return buildDegradedResult(
                    input = input,
                    decisionId = decisionIdProvider(),
                    reason = CloudDegradationReason.INVALID_TOOL_CALL,
                    fallbackState = resolved.updatedTaskState
                )

            val parsedParams = rawResponse.toolCallArguments.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.content ?: value.toString()
            }

            val decision = ConversationTurnDecision(
                decisionId = decisionIdProvider(),
                action = ConversationAction(
                    actionType = mappedActionType,
                    reasonCode = "CLOUD_TOOL_REQUEST_$toolName",
                    candidateParameters = parsedParams
                ),
                updatedState = resolved.updatedTaskState,
                explanationSummary = rawResponse.text
            )

            return HybridEngineResult(
                decision = decision,
                source = ConversationEngineSource.CLOUD_GEMINI,
                cloudDegradationReason = null,
                hasBackingToolResult = false,
                evidence = input.priorEvidence
            )
        }

        // 8. Handle Plain Text Response with Engineering Claim Guard & GroundedResponseValidator
        val text = rawResponse.text ?: ""
        val hasBackingEvidence = input.priorEvidence.isNotEmpty()
        val hasFabrication = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = text,
            hasBackingToolResult = hasBackingEvidence
        )
        val groundingValidation = GroundedResponseValidator.validateResponse(
            text = text,
            evidenceList = input.priorEvidence,
            context = effectiveProjectContext
        )

        if (hasFabrication || groundingValidation is GroundingValidationResult.Rejected) {
            return buildDegradedResult(
                input = input,
                decisionId = decisionIdProvider(),
                reason = CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM,
                fallbackState = resolved.updatedTaskState
            )
        }

        val decision = ConversationTurnDecision(
            decisionId = decisionIdProvider(),
            action = ConversationAction(
                actionType = ConversationActionType.EXPLAIN_RESULT,
                reasonCode = "CLOUD_TEXT_EXPLANATION"
            ),
            updatedState = resolved.updatedTaskState,
            explanationSummary = text
        )

        return HybridEngineResult(
            decision = decision,
            source = ConversationEngineSource.CLOUD_GEMINI,
            cloudDegradationReason = null,
            hasBackingToolResult = false,
            evidence = input.priorEvidence,
            groundingResult = GroundingValidationResult.Valid
        )
    }

    private fun buildDegradedResult(
        input: ConversationTurnInput,
        decisionId: String,
        reason: CloudDegradationReason,
        fallbackState: ConversationTaskState? = null
    ): HybridEngineResult {
        return HybridEngineResult(
            decision = ConversationTurnDecision(
                decisionId = decisionId,
                action = ConversationAction(
                    actionType = ConversationActionType.SAFE_ERROR,
                    reasonCode = "CLOUD_DEGRADED_$reason"
                ),
                updatedState = fallbackState ?: input.priorState ?: ConversationTaskState(
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

    fun toolFor(actionType: ConversationActionType): String? = when (actionType) {
        ConversationActionType.RUN_CALCULATION,
        ConversationActionType.RECALCULATE_STALE,
        ConversationActionType.DIAGNOSE_FAILURE -> "calculate_cable_sizing"

        ConversationActionType.EVALUATE_CANDIDATE -> "evaluate_cable_candidate"
        ConversationActionType.COMPARE_CANDIDATES -> "compare_cable_candidates"
        ConversationActionType.RUN_WHAT_IF -> "run_what_if"
        ConversationActionType.READ_CONTEXT,
        ConversationActionType.ANSWER_FROM_CURRENT_RESULT -> "get_entity"

        else -> null
    }
}
