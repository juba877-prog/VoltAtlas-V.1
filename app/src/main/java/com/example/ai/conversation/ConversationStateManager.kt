package com.example.ai.conversation

import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseDetailLevel
import java.util.UUID

/**
 * Pure, deterministic manager for immutable [ConversationTaskState] transitions.
 */
object ConversationStateManager {

    fun createInitialState(
        projectId: String,
        baseRevision: Long,
        taskId: String = "TASK-" + UUID.randomUUID().toString().take(8),
        language: ConversationLanguage = ConversationLanguage.EN
    ): ConversationTaskState {
        return ConversationTaskState(
            taskId = taskId,
            projectId = projectId,
            baseProjectRevision = baseRevision,
            userLanguage = language,
            lifecycleState = ConversationLifecycleState.ACTIVE
        )
    }

    fun startTaskForEntity(
        priorState: ConversationTaskState?,
        projectContext: BoundedProjectContext,
        entity: EntityBinding?,
        intent: AssistantIntent,
        objective: EngineeringObjective,
        language: ConversationLanguage = ConversationLanguage.EN,
        taskIdProvider: () -> String = { "TASK-" + UUID.randomUUID().toString().take(8) }
    ): ConversationTaskState {
        val taskId = priorState?.taskId ?: taskIdProvider()
        val isEntitySame = priorState != null &&
                priorState.activeEntityType == entity?.entityType &&
                priorState.activeEntityId == entity?.entityId

        val knownSlots = if (isEntitySame) {
            priorState.knownSlots.toMutableMap()
        } else {
            mutableMapOf()
        }

        // Populate known inputs from project context
        projectContext.knownEntityInputs.forEach { (k, v) ->
            if (!knownSlots.containsKey(k)) {
                knownSlots[k] = SlotValue(
                    name = k,
                    state = SlotState.KNOWN,
                    value = v,
                    provenance = ProvenanceLabel.KNOWN_FROM_PROJECT,
                    isSafetyCritical = isSafetyCriticalSlot(k),
                    lastUpdatedRevision = projectContext.liveRevision
                )
            }
        }

        return ConversationTaskState(
            taskId = taskId,
            projectId = projectContext.projectId,
            baseProjectRevision = projectContext.liveRevision,
            activeEntityType = entity?.entityType ?: projectContext.activeEntityType,
            activeEntityId = entity?.entityId ?: projectContext.activeEntityId,
            activeEntityName = entity?.entityName ?: projectContext.activeEntityName,
            primaryIntent = intent,
            engineeringObjective = objective,
            knownSlots = knownSlots,
            missingBlockingSlots = emptyList(),
            ambiguousSlots = emptyList(),
            detectedContradictions = emptyList(),
            isWhatIfBranch = if (isEntitySame) priorState.isWhatIfBranch else false,
            whatIfOverrides = if (isEntitySame) priorState.whatIfOverrides else emptyMap(),
            activeProposalId = if (isEntitySame) priorState.activeProposalId else null,
            activeProposalDigest = if (isEntitySame) priorState.activeProposalDigest else null,
            activeProposalSemanticKey = if (isEntitySame) priorState.activeProposalSemanticKey else null,
            responseDetailLevel = priorState?.responseDetailLevel ?: ResponseDetailLevel.CONCISE,
            userLanguage = language,
            lifecycleState = ConversationLifecycleState.ACTIVE
        )
    }

    fun updateSlot(
        state: ConversationTaskState,
        slotName: String,
        value: String,
        provenance: ProvenanceLabel = ProvenanceLabel.USER_PROVIDED
    ): ConversationTaskState {
        val updatedSlots = state.knownSlots.toMutableMap()
        updatedSlots[slotName] = SlotValue(
            name = slotName,
            state = SlotState.USER_PROVIDED,
            value = value,
            provenance = provenance,
            isSafetyCritical = isSafetyCriticalSlot(slotName),
            lastUpdatedRevision = state.baseProjectRevision
        )
        val remainingMissing = state.missingBlockingSlots.filterNot {
            it.equals(slotName, ignoreCase = true) ||
                    (slotName.equals("installation", ignoreCase = true) && it.equals("installationMethod", ignoreCase = true)) ||
                    (slotName.equals("installationMethod", ignoreCase = true) && it.equals("installation", ignoreCase = true))
        }
        return state.copy(
            knownSlots = updatedSlots,
            missingBlockingSlots = remainingMissing
        )
    }

    fun setWhatIfOverride(
        state: ConversationTaskState,
        slotName: String,
        value: String
    ): ConversationTaskState {
        val updatedOverrides = state.whatIfOverrides.toMutableMap()
        updatedOverrides[slotName] = value
        return state.copy(
            isWhatIfBranch = true,
            whatIfOverrides = updatedOverrides
        )
    }

    fun bindProposal(
        state: ConversationTaskState,
        proposalId: String,
        digest: String,
        semanticKey: String
    ): ConversationTaskState {
        return state.copy(
            activeProposalId = proposalId,
            activeProposalDigest = digest,
            activeProposalSemanticKey = semanticKey,
            lifecycleState = ConversationLifecycleState.PROPOSAL_PENDING_REVIEW
        )
    }

    fun invalidateProposal(state: ConversationTaskState): ConversationTaskState {
        return state.copy(
            activeProposalId = null,
            activeProposalDigest = null,
            activeProposalSemanticKey = null,
            lifecycleState = if (state.lifecycleState == ConversationLifecycleState.PROPOSAL_PENDING_REVIEW) {
                ConversationLifecycleState.ACTIVE
            } else {
                state.lifecycleState
            }
        )
    }

    fun switchEntity(
        state: ConversationTaskState,
        newEntity: EntityBinding,
        projectContext: BoundedProjectContext
    ): ConversationTaskState {
        return startTaskForEntity(
            priorState = null,
            projectContext = projectContext,
            entity = newEntity,
            intent = state.primaryIntent,
            objective = state.engineeringObjective,
            language = state.userLanguage,
            taskIdProvider = { state.taskId }
        )
    }

    fun handleRevisionAdvance(
        state: ConversationTaskState,
        newRevision: Long
    ): ConversationTaskState {
        if (newRevision == state.baseProjectRevision) return state
        // When revision advances, proposals based on the older revision are invalidated
        return state.copy(
            baseProjectRevision = newRevision,
            activeProposalId = null,
            activeProposalDigest = null,
            activeProposalSemanticKey = null
        )
    }

    fun isSafetyCriticalSlot(slotName: String): Boolean {
        val normalized = slotName.lowercase()
        return normalized.contains("voltage") ||
                normalized.contains("phase") ||
                normalized.contains("installation") ||
                normalized.contains("conductor") ||
                normalized.contains("material") ||
                normalized.contains("fault") ||
                normalized.contains("length")
    }
}
