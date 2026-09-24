package com.example.ai.conversation

import com.example.ai.contracts.EngineeringValue
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.runtime.PromptPermissionMode
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseDetailLevel
import com.example.ai.runtime.ResponseMode
import com.example.ai.runtime.BoundedProjectContext
import com.squareup.moshi.JsonClass

/**
 * Primary user intent classifications supported by Phase 7F-2.
 */
enum class AssistantIntent {
    NAVIGATE,
    EXPLAIN_RESULT,
    CALCULATE,
    EVALUATE_CANDIDATE,
    COMPARE,
    WHAT_IF,
    DIAGNOSE,
    RECOMMEND,
    CREATE_PROPOSAL,
    REVIEW_PROPOSAL,
    REQUEST_APPLY,
    CHECK_STATUS,
    OUT_OF_SCOPE
}

/**
 * Engineering objectives governing candidate selection and trade-offs.
 */
enum class EngineeringObjective {
    VERIFY_COMPLIANCE,
    OPTIMIZE_CABLE_SIZE,
    REDUCE_VOLTAGE_DROP,
    RESOLVE_FAULT_WITHSTAND,
    SELECT_DEVICE,
    DIAGNOSE_TRIP,
    COST_OPTIMIZE,
    EXPLAIN_GOVERNING_CRITERION
}

/**
 * Lifecycle states of an engineering conversation task.
 */
enum class ConversationLifecycleState {
    ACTIVE,
    WAITING_FOR_USER_INPUT,
    READY_FOR_CALCULATION,
    PROPOSAL_PENDING_REVIEW,
    CANCELLED,
    COMPLETED
}

/**
 * Language selection for the conversation runtime.
 */
enum class ConversationLanguage {
    EN,
    AR
}

/**
 * Typed states of an individual parameter slot.
 */
enum class SlotState {
    KNOWN,
    USER_PROVIDED,
    DERIVED,
    CALCULATED,
    MISSING,
    AMBIGUOUS,
    CONTRADICTORY,
    STALE,
    INVALID,
    UNAVAILABLE
}

/**
 * Typed container for a single parameter slot with strict provenance and safety attributes.
 */
@JsonClass(generateAdapter = true)
data class SlotValue(
    val name: String,
    val state: SlotState,
    val value: String? = null,
    val unit: String? = null,
    val provenance: ProvenanceLabel = ProvenanceLabel.USER_PROVIDED,
    val isSafetyCritical: Boolean = false,
    val lastUpdatedRevision: Long? = null,
    val typedValue: EngineeringValue? = null
)

/**
 * Structured record of detected contradiction between user statement and project truth.
 */
@JsonClass(generateAdapter = true)
data class ContradictionRecord(
    val fieldName: String,
    val projectValue: String,
    val userValue: String,
    val projectSource: ProvenanceLabel = ProvenanceLabel.KNOWN_FROM_PROJECT,
    val userSource: ProvenanceLabel = ProvenanceLabel.USER_PROVIDED,
    val safeChoices: List<String> = listOf("RUN_WHAT_IF", "PROPOSE_CHANGE"),
    val typedProjectValue: EngineeringValue? = null,
    val typedUserValue: EngineeringValue? = null
)

/**
 * Target electrical entity reference binding.
 */
@JsonClass(generateAdapter = true)
data class EntityBinding(
    val entityType: String,
    val entityId: String,
    val entityName: String? = null,
    val parentEntityId: String? = null
)

/**
 * High-level category of next action selected by the policy engine.
 */
enum class ConversationActionType {
    ANSWER_FROM_CURRENT_RESULT,
    ASK_BLOCKING_QUESTION,
    READ_CONTEXT,
    RECALCULATE_STALE,
    RUN_CALCULATION,
    EVALUATE_CANDIDATE,
    COMPARE_CANDIDATES,
    RUN_WHAT_IF,
    EXPLAIN_RESULT,
    DIAGNOSE_FAILURE,
    CREATE_PROPOSAL,
    REVIEW_PROPOSAL,
    SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE,
    OPEN_ENTITY,
    CANCEL_TASK,
    SAFE_ERROR,
    OUT_OF_SCOPE
}

/**
 * Concrete next action emitted by the deterministic policy engine.
 */
@JsonClass(generateAdapter = true)
data class ConversationAction(
    val actionType: ConversationActionType,
    val reasonCode: String,
    val requiredPermission: PromptPermissionMode = PromptPermissionMode.READ,
    val requiredSlots: List<String> = emptyList(),
    val entityBinding: EntityBinding? = null,
    val responseMode: ResponseMode = ResponseMode.DIRECT_ANSWER,
    val candidateParameters: Map<String, String> = emptyMap()
)

/**
 * Immutable conversation task state container.
 */
@JsonClass(generateAdapter = true)
data class ConversationTaskState(
    val taskId: String,
    val projectId: String,
    val baseProjectRevision: Long,
    val activeEntityType: String? = null,
    val activeEntityId: String? = null,
    val activeEntityName: String? = null,
    val primaryIntent: AssistantIntent = AssistantIntent.NAVIGATE,
    val secondaryIntents: Set<AssistantIntent> = emptySet(),
    val engineeringObjective: EngineeringObjective = EngineeringObjective.VERIFY_COMPLIANCE,
    val knownSlots: Map<String, SlotValue> = emptyMap(),
    val missingBlockingSlots: List<String> = emptyList(),
    val ambiguousSlots: List<String> = emptyList(),
    val detectedContradictions: List<ContradictionRecord> = emptyList(),
    val isWhatIfBranch: Boolean = false,
    val whatIfOverrides: Map<String, String> = emptyMap(),
    val typedWhatIfOverrides: Map<String, EngineeringValue> = emptyMap(),
    val activeDomain: CalculationDomain? = null,
    val evidenceReferences: List<String> = emptyList(),
    val activeProposalId: String? = null,
    val activeProposalDigest: String? = null,
    val activeProposalSemanticKey: String? = null,
    val responseDetailLevel: ResponseDetailLevel = ResponseDetailLevel.CONCISE,
    val userLanguage: ConversationLanguage = ConversationLanguage.EN,
    val lifecycleState: ConversationLifecycleState = ConversationLifecycleState.ACTIVE,
    val lastDecisionId: String? = null
)

/**
 * Input container for a single conversational turn.
 */
data class ConversationTurnInput(
    val userUtterance: String,
    val projectContext: BoundedProjectContext,
    val priorState: ConversationTaskState? = null,
    val availableEntities: List<EntityBinding> = emptyList(),
    val forcedLanguage: ConversationLanguage? = null,
    val priorEvidence: List<EngineeringEvidenceRecord> = emptyList()
)

/**
 * Output of a single conversational turn evaluation.
 */
data class ConversationTurnDecision(
    val decisionId: String,
    val action: ConversationAction,
    val updatedState: ConversationTaskState,
    val explanationSummary: String? = null
)
