package com.example.ai.runtime

import com.squareup.moshi.JsonClass

/**
 * Permission modes supported by the assistant tool contract in Phase 7F-1.
 * MUTATE_DISABLED is strictly enforced.
 */
enum class PromptPermissionMode {
    READ,
    CALCULATE,
    PROPOSE,
    MUTATE_DISABLED
}

/**
 * Authoritative, typed data provenance labels distinguishing source of truth.
 * These labels are strictly non-interchangeable. APPLIED cannot be established by conversational text alone.
 */
enum class ProvenanceLabel {
    KNOWN_FROM_PROJECT,
    CALCULATED_BY_VOLTATLAS,
    DERIVED,
    USER_PROVIDED,
    ASSUMED,
    UNAVAILABLE,
    RECOMMENDED,
    PROPOSED,
    APPLIED
}

/**
 * Bounded response modes dictating presentation structure and allowed claims.
 */
enum class ResponseMode {
    DIRECT_ANSWER,
    BLOCKING_QUESTION,
    RESULT_SUMMARY,
    FAILURE_DIAGNOSIS,
    COMPARISON,
    WHAT_IF_RESULT,
    RECOMMENDATION,
    PROPOSAL_REVIEW,
    APPROVAL_REQUIRED,
    SAFE_ERROR
}

/**
 * Granular response detail levels. Default is CONCISE.
 */
enum class ResponseDetailLevel {
    CONCISE,
    STANDARD,
    DETAILED,
    AUDIT
}

/**
 * Typed descriptor for an individual tool capability provided to the compiler.
 */
@JsonClass(generateAdapter = true)
data class ToolCapabilityDescriptor(
    val name: String,
    val permissionMode: PromptPermissionMode,
    val summary: String,
    val parameterSchema: String? = null
)

/**
 * Manifest of caller-supplied tools and authorized permission modes.
 * Tools not explicitly registered here are never advertised in the prompt.
 */
@JsonClass(generateAdapter = true)
data class CapabilityManifest(
    val capabilities: List<ToolCapabilityDescriptor> = emptyList(),
    val permittedModes: Set<PromptPermissionMode> = setOf(
        PromptPermissionMode.READ,
        PromptPermissionMode.CALCULATE,
        PromptPermissionMode.PROPOSE,
        PromptPermissionMode.MUTATE_DISABLED
    )
)

/**
 * Bounded snapshot of active project state.
 * Contains only relevant structured fields—no raw database dumps or unrestricted notes.
 */
@JsonClass(generateAdapter = true)
data class BoundedProjectContext(
    val projectId: String,
    val projectName: String,
    val liveRevision: Long,
    val activeScreen: String? = null,
    val activeEntityType: String? = null,
    val activeEntityId: String? = null,
    val activeEntityName: String? = null,
    val knownEntityInputs: Map<String, String> = emptyMap(),
    val freshnessSummary: Map<String, String> = emptyMap(),
    val validatedResultSummary: String? = null,
    val optionalNotes: String? = null
)

/**
 * Typed summary of active conversation task state.
 * Contains structured slot bindings—no raw unlimited message histories.
 */
@JsonClass(generateAdapter = true)
data class ActiveConversationStateSummary(
    val taskId: String,
    val intentName: String? = null,
    val engineeringObjective: String? = null,
    val boundEntityType: String? = null,
    val boundEntityId: String? = null,
    val knownSlots: Map<String, String> = emptyMap(),
    val missingBlockingSlots: List<String> = emptyList(),
    val detectedContradictions: List<String> = emptyList(),
    val isWhatIfActive: Boolean = false,
    val whatIfParameters: Map<String, String> = emptyMap(),
    val activeProposalId: String? = null,
    val activeProposalDigest: String? = null,
    val activeProposalSemanticKey: String? = null
)

/**
 * Configuration for rendering style, response mode, and language.
 */
@JsonClass(generateAdapter = true)
data class ResponseRenderingPolicy(
    val targetMode: ResponseMode = ResponseMode.DIRECT_ANSWER,
    val detailLevel: ResponseDetailLevel = ResponseDetailLevel.CONCISE,
    val language: String = "en"
)

/**
 * Complete input container for [RuntimePromptCompiler].
 */
data class RuntimePromptInputs(
    val capabilities: CapabilityManifest = CapabilityManifest(),
    val projectContext: BoundedProjectContext? = null,
    val conversationSummary: ActiveConversationStateSummary? = null,
    val renderingPolicy: ResponseRenderingPolicy = ResponseRenderingPolicy(),
    val latestUserUtterance: String? = null
)

/**
 * Immutable compiled prompt artifact with deterministic canonical hashes.
 */
data class CompiledPromptArtifact(
    val systemPrompt: String,
    val userPrompt: String?,
    val assistantBehaviorVersion: String,
    val promptSchemaVersion: String,
    val staticContractHash: String,
    val conversationPolicyHash: String,
    val compiledPromptHash: String,
    val estimatedCharacterCount: Int
)

/**
 * Result sealed class for prompt compilation.
 */
sealed class PromptCompilationResult {
    data class Success(val artifact: CompiledPromptArtifact) : PromptCompilationResult()
    data class PromptBudgetExceeded(
        val requestedCharacters: Int,
        val maxBudgetCharacters: Int,
        val diagnostic: String
    ) : PromptCompilationResult()
    data class InvalidInput(val reason: String) : PromptCompilationResult()
}
