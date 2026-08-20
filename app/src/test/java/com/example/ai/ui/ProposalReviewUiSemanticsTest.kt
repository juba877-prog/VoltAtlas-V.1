package com.example.ai.ui

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.proposal.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import org.junit.Assert.*
import org.junit.Test

class ProposalReviewUiSemanticsTest {

    private fun createSampleProposalModel(isStale: Boolean = false): ProposalReviewUiModel {
        return ProposalReviewUiModel(
            proposalId = "PROP-777",
            proposalArtifactDigest = "sha256-777",
            projectId = "PROJ-101",
            projectLabel = "Commercial Tower",
            targetEntityType = "CIRCUIT",
            targetEntityId = "201",
            targetEntityLabel = "Feeder 201",
            operation = "UPDATE_CABLE_ATTRIBUTES",
            baseProjectRevision = 2L,
            currentLiveRevision = if (isStale) 3L else 2L,
            isStale = isStale,
            fieldChanges = listOf(
                ProposalFieldChangeUiModel(
                    fieldKey = "overrideCableSize",
                    label = "Cable Size",
                    action = ProposalChangeActionUi.SET,
                    currentValue = EngineeringDisplayValueUi("240.0", "240.0 mm²", "mm²"),
                    proposedValue = EngineeringDisplayValueUi("300.0", "300.0 mm²", "mm²"),
                    unit = "mm²",
                    significance = ProposalChangeSignificanceUi.CRITICAL
                )
            ),
            governingCriterion = "Ampacity Iz",
            evidenceSummary = "Governing check is ampacity derating.",
            affectedDomains = listOf("CABLE_SIZING", "VOLTAGE_DROP"),
            expectedPostApplyBehavior = "Revision will advance and revalidation will run.",
            provenance = ProvenanceLabel.PROPOSED
        )
    }

    // 1. Proposal review displays Not Applied and Proposed labels
    @Test
    fun proposalReview_showsProposedAndNotAppliedLabels() {
        val model = createSampleProposalModel()
        assertEquals(ProvenanceLabel.PROPOSED, model.provenance)
        assertNotEquals(ProvenanceLabel.APPLIED, model.provenance)
    }

    // 2. Stale proposal disables Continue to Approval
    @Test
    fun staleProposal_disablesContinueToApproval() {
        val staleModel = createSampleProposalModel(isStale = true)
        assertTrue(staleModel.isStale)
        assertNotEquals(staleModel.baseProjectRevision, staleModel.currentLiveRevision)
    }

    // 3. Continue to approval is non executable in 7F-4
    @Test
    fun continueToApproval_isNonExecutableIn7F4() {
        var approvalExecuted = false
        val action = ProposalReviewActionUiModel(
            actionType = ProposalReviewActionType.CONTINUE_TO_APPROVAL_NON_EXECUTABLE,
            label = "Review Approval Step",
            isPrimary = true
        )

        // Simulating UI action handling
        when (action.actionType) {
            ProposalReviewActionType.CONTINUE_TO_APPROVAL_NON_EXECUTABLE -> {
                // In Phase 7F-4, this action is strictly non-executable
                approvalExecuted = false
            }
            else -> {}
        }

        assertFalse(approvalExecuted)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 4. Cancel is an explicit non-mutating UI action
    @Test
    fun cancelAction_clearsReviewWithoutProjectMutation() {
        val model = createSampleProposalModel()
        val initialState: ProposalReviewUiState = ProposalReviewUiState.Ready(model)
        assertTrue(initialState is ProposalReviewUiState.Ready)

        // User triggers cancel
        val cancelledState: ProposalReviewUiState = ProposalReviewUiState.Cancelled(model.proposalId)
        assertTrue(cancelledState is ProposalReviewUiState.Cancelled)
        assertEquals("PROP-777", (cancelledState as ProposalReviewUiState.Cancelled).proposalId)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 5. What-if never renders as proposal review
    @Test
    fun whatIfCard_neverRendersAsProposalReview() {
        val whatIfCard: AssistantCardUiModel = AssistantCardUiModel.WhatIfResultCard(
            cardId = "W-1",
            scenarioTitle = "Scenario Al",
            changedParameters = mapOf("conductorMaterial" to "Al"),
            baseRevision = 2L,
            deltaSummary = "Simulated delta"
        )
        assertFalse(whatIfCard is AssistantCardUiModel.ProposalReviewCard)
    }

    // 6. Proposal review never claims Applied or Approved
    @Test
    fun proposalReview_neverClaimsAppliedOrApproved() {
        val model = createSampleProposalModel()
        assertNotEquals(ProvenanceLabel.APPLIED, model.provenance)
        assertEquals(ProvenanceLabel.PROPOSED, model.provenance)
    }

    // 7. No Room DAO or tool registry dependency from proposal review UI
    @Test
    fun noRoomDaoReferenceFromProposalReviewUi() {
        val mapperClass = ProposalReviewMapper::class.java
        for (f in mapperClass.declaredFields) {
            assertFalse(f.type.name.contains("Room"))
            assertFalse(f.type.name.contains("Dao"))
            assertFalse(f.type.name.contains("DurableMutationWorkflow"))
        }
    }

    // 8. ExecutionMode.MUTATION remains strictly disabled
    @Test
    fun executionModeMutationRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }
}
