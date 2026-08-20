package com.example.ai.ui

import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationPolicyEngine
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.proposal.*
import com.example.ai.runtime.BoundedProjectContext
import org.junit.Assert.*
import org.junit.Test

class ApprovalExecutionUiStateTest {

    // 1. UI states correctly reflect committed vs non-committed status
    @Test
    fun uiStates_correctlyReflectCommittedStatus() {
        val idle = ApprovalExecutionUiState.Idle
        assertFalse(idle.isCommitted)
        assertTrue(idle.canDismiss)

        val submitting = ApprovalExecutionUiState.SubmittingApproval("PROP-1", "UA-1")
        assertFalse(submitting.isCommitted)
        assertFalse(submitting.canDismiss)

        val completed = ApprovalExecutionUiState.Completed(
            receiptId = "REC-1",
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChanges = emptyList(),
            revalidationStatus = "PASSED"
        )
        assertTrue(completed.isCommitted)
        assertTrue(completed.canDismiss)

        val partial = ApprovalExecutionUiState.CommittedPartial(
            receiptId = "REC-2",
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChanges = emptyList(),
            revalidationNote = "Voltage drop partial"
        )
        assertTrue(partial.isCommitted)

        val revalFailed = ApprovalExecutionUiState.CommittedRevalidationFailed(
            receiptId = "REC-3",
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChanges = emptyList(),
            failureReason = "Thermal overload"
        )
        assertTrue(revalFailed.isCommitted)

        val alreadyApplied = ApprovalExecutionUiState.AlreadyApplied(
            receiptId = "REC-4",
            revisionAfter = 2L,
            appliedChanges = emptyList()
        )
        assertTrue(alreadyApplied.isCommitted)
    }

    // 2. Chat text "Apply" only opens review / approval required, never calls approval gateway
    @Test
    fun chatTextApply_doesNotIssueApproval_orExecuteMutation() {
        val ctx = BoundedProjectContext("P-1", "Complex", 1L, activeEntityType = "CIRCUIT", activeEntityId = "101")
        val state = ConversationTaskState(
            taskId = "T-1",
            projectId = "P-1",
            baseProjectRevision = 1L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "101",
            activeProposalId = "PROP-1"
        )

        val input = com.example.ai.conversation.ConversationTurnInput(
            userUtterance = "Apply",
            priorState = state,
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)
        assertEquals(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, decision.action.actionType)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 3. ConversationEngine has zero references to TrustedProposalApprovalGateway
    @Test
    fun conversationEngine_cannotCallApprovalGateway() {
        val engineClass = ConversationPolicyEngine::class.java
        for (f in engineClass.declaredFields) {
            assertFalse(f.type.name.contains("TrustedProposalApprovalGateway"))
            assertFalse(f.type.name.contains("ApprovalGrantDao"))
            assertFalse(f.type.name.contains("DurableMutationWorkflow"))
        }
    }

    // 4. ExecutionMode.MUTATION remains strictly disabled
    @Test
    fun executionModeMutationRemainsFalse() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }
}
