package com.example.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.approval.TrustedApprovalGateResult
import com.example.ai.approval.TrustedApprovalRequest
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.context.EngineeringContext
import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.DurableMutationOutcome
import com.example.ai.proposal.RevalidationOutcome
import com.example.ai.proposal.RevalidationStatus
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.hybrid.ConversationEngine
import com.example.ai.hybrid.ConversationEngineProvider
import com.example.ai.hybrid.ConversationEngineSource
import com.example.ai.hybrid.HybridEngineResult
import com.example.ai.service.AssistantMessage
import com.example.ai.service.AssistantResponse
import com.example.ai.service.EngineeringAiService
import com.example.ai.service.FirebaseAiServiceImpl
import com.example.ai.service.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Entry points for invoking the VoltAtlas Engineering AI Assistant.
 */
enum class AssistantEntryPoint(val title: String) {
    DASHBOARD_ACTION("Dashboard Overview"),
    TOP_BAR_ICON("Assistant"),
    CONTEXTUAL_CIRCUIT("Circuit Analysis"),
    DIAGNOSE_FAILURE("Diagnostics"),
    OVERFLOW_MENU("Menu Action")
}

/**
 * Comprehensive lifecycle state machine for the Assistant container.
 */
enum class AssistantState {
    CLOSED,
    OPENING,
    COMPACT,
    EXPANDED,
    COLLECTING_INPUTS,
    CALCULATING,
    SHOWING_RESULT,
    SHOWING_ALTERNATIVES,
    PROPOSAL_REVIEW,
    APPLYING,
    APPLIED,
    ERROR,
    CLOSING
}

/**
 * Status of background approval gateway and catalog repository initialization.
 */
enum class GatewayStatus {
    Loading,
    Ready,
    Failed
}

/**
 * UI State exposed to the Compose container.
 */
data class AssistantUiState(
    val state: AssistantState = AssistantState.CLOSED,
    val entryPoint: AssistantEntryPoint = AssistantEntryPoint.TOP_BAR_ICON,
    val context: EngineeringContext = EngineeringContext(),
    val messages: List<AssistantMessage> = emptyList(),
    val typedCards: List<AssistantCardUiModel> = emptyList(),
    val header: ContextHeaderUiModel = ContextHeaderUiModel(),
    val approvalExecutionState: ApprovalExecutionUiState = ApprovalExecutionUiState.Idle,
    val inputText: String = "",
    val isThinking: Boolean = false,
    val errorMessage: String? = null,
    val gatewayStatus: GatewayStatus = GatewayStatus.Ready,
    val gatewayErrorMessage: String? = null
) {
    val isOpen: Boolean
        get() = state != AssistantState.CLOSED && state != AssistantState.CLOSING

    val isExpanded: Boolean
        get() = state == AssistantState.EXPANDED || 
                state == AssistantState.PROPOSAL_REVIEW || 
                state == AssistantState.SHOWING_ALTERNATIVES
}

/**
 * ViewModel managing Assistant container state, conversations, trusted approval execution, and back-gesture consumption.
 */
class EngineeringAssistantViewModel(
    private val conversationEngine: ConversationEngine = ConversationEngineProvider.provideConversationEngine(),
    /** Supplied by the production factory; nullable only to preserve isolated legacy UI tests. */
    approvalGateway: TrustedProposalApprovalGateway? = null,
    private val approvalGatewayProvider: (() -> TrustedProposalApprovalGateway?)? = null,
    private val asyncApprovalGatewayProvider: (suspend () -> TrustedProposalApprovalGateway)? = null,
    private val proposalPreparationCoordinator: CableProposalPreparationCoordinator? = null,
    private val aiService: EngineeringAiService = FirebaseAiServiceImpl(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    private var _approvalGateway: TrustedProposalApprovalGateway? = approvalGateway
    internal var approvalGateway: TrustedProposalApprovalGateway?
        get() = _approvalGateway ?: approvalGatewayProvider?.invoke()?.also { _approvalGateway = it }
        set(value) { _approvalGateway = value }

    private var gatewayJob: Job? = null

    private var currentTurnId: Long = 0L
    private var activeTurnJob: Job? = null
    private var activeConversationState: ConversationTaskState? = null
    private var activeProposalReview: Pair<CableEngineeringProposal, ProposalReviewUiModel>? = null

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        val asyncProvider = asyncApprovalGatewayProvider
        if (_approvalGateway != null) {
            _uiState.update { it.copy(gatewayStatus = GatewayStatus.Ready) }
        } else if (asyncProvider != null) {
            _uiState.update { it.copy(gatewayStatus = GatewayStatus.Loading) }
            gatewayJob = viewModelScope.launch(ioDispatcher) {
                try {
                    val gateway = asyncProvider.invoke()
                    _approvalGateway = gateway
                    _uiState.update { it.copy(gatewayStatus = GatewayStatus.Ready) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            gatewayStatus = GatewayStatus.Failed,
                            gatewayErrorMessage = e.message ?: "Failed to initialize catalog gateway"
                        )
                    }
                }
            }
        }
    }

    fun open(entryPoint: AssistantEntryPoint, context: EngineeringContext) {
        val initialGreeting = when (entryPoint) {
            AssistantEntryPoint.CONTEXTUAL_CIRCUIT -> 
                "Inspecting circuit **${context.activeEntityName ?: context.activeEntityId ?: "selected"}**. How can I help optimize or verify this feeder?"
            AssistantEntryPoint.DIAGNOSE_FAILURE ->
                "Reviewing active project compliance issues. Would you like me to analyze potential causes or recommend remediation?"
            AssistantEntryPoint.DASHBOARD_ACTION ->
                "Hello! I am your VoltAtlas Engineering Assistant. Ask me about cable sizing, transformer capacity, or voltage drop across **${context.projectName}**."
            else ->
                "Hello! How can I assist with your electrical engineering calculations today?"
        }

        _uiState.update { current ->
            current.copy(
                state = AssistantState.COMPACT,
                entryPoint = entryPoint,
                context = context,
                messages = if (current.messages.isEmpty()) {
                    listOf(AssistantMessage(role = MessageRole.ASSISTANT, text = initialGreeting))
                } else current.messages,
                errorMessage = null
            )
        }
    }

    fun close() {
        currentTurnId++
        activeTurnJob?.cancel()
        activeTurnJob = null
        _uiState.update { it.copy(state = AssistantState.CLOSED) }
    }

    fun toggleExpand() {
        _uiState.update { current ->
            if (current.isExpanded) {
                current.copy(state = AssistantState.COMPACT)
            } else {
                current.copy(state = AssistantState.EXPANDED)
            }
        }
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String) {
        onUserSubmit(text)
    }

    /**
     * Authoritative entry point evaluating a user turn through [HybridConversationRouter].
     * Protects against stale/late responses and updates UI state deterministically.
     */
    fun onUserSubmit(utterance: String) {
        val trimmed = utterance.trim()
        if (trimmed.isBlank()) return

        val turnId = ++currentTurnId
        val userMsg = AssistantMessage(role = MessageRole.USER, text = trimmed)
        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMsg,
                inputText = "",
                isThinking = true,
                errorMessage = null
            )
        }

        val currentCtx = _uiState.value.context
        val boundedContext = BoundedProjectContext(
            projectId = currentCtx.projectId ?: "DEFAULT_PROJ",
            projectName = currentCtx.projectName,
            liveRevision = activeConversationState?.baseProjectRevision ?: 1L,
            activeScreen = currentCtx.currentScreen,
            activeEntityType = currentCtx.activeEntityType,
            activeEntityId = currentCtx.activeEntityId,
            activeEntityName = currentCtx.activeEntityName
        )

        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            val input = ConversationTurnInput(
                userUtterance = trimmed,
                projectContext = boundedContext,
                priorState = activeConversationState
            )

            val result = conversationEngine.evaluateTurn(input)

            // Stale/late result protection: ensure user hasn't submitted a newer turn
            if (turnId != currentTurnId) {
                return@launch
            }

            applyHybridResult(result, boundedContext)
        }
    }

    private suspend fun applyHybridResult(
        result: HybridEngineResult,
        projectContext: BoundedProjectContext
    ) {
        val decision = result.decision
        activeConversationState = decision.updatedState

        if (decision.action.actionType == ConversationActionType.CREATE_PROPOSAL) {
            when (val preparation = proposalPreparationCoordinator?.prepare(decision.action, projectContext, decision.decisionId)) {
                is CableProposalPreparationCoordinator.Result.Ready -> {
                    activeProposalReview = preparation.proposal to preparation.review
                    _uiState.update { current ->
                        current.copy(
                            state = AssistantState.PROPOSAL_REVIEW,
                            typedCards = current.typedCards.filterNot { it is AssistantCardUiModel.ProposalReviewCard } +
                                AssistantCardUiModel.ProposalReviewCard("REVIEW-" + preparation.proposal.proposalId, preparation.review),
                            isThinking = false
                        )
                    }
                }
                is CableProposalPreparationCoordinator.Result.SafeError -> appendProposalPreparationError(preparation.message)
                null -> appendProposalPreparationError("Proposal review is unavailable in this composition.")
            }
            return
        }

        val sheetUiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = projectContext,
            existingState = AssistantSheetUiState(
                allCards = _uiState.value.typedCards
            )
        )

        val isOfflineWithDegradation = result.source == ConversationEngineSource.LOCAL_RULE_ENGINE &&
                result.cloudDegradationReason != null

        val updatedHeader = sheetUiState.header.copy(
            engineeringSource = result.source,
            offlineBadgeVisible = isOfflineWithDegradation
        )

        val replyText = decision.explanationSummary
            ?: (sheetUiState.activeCard as? AssistantCardUiModel.ResultSummaryCard)?.summaryText
            ?: (sheetUiState.activeCard as? AssistantCardUiModel.DirectAnswerCard)?.text
            ?: "Turn processed."
        val replyMsg = AssistantMessage(role = MessageRole.ASSISTANT, text = replyText)

        _uiState.update { current ->
            current.copy(
                messages = current.messages + replyMsg,
                typedCards = sheetUiState.allCards,
                header = updatedHeader,
                isThinking = false
            )
        }
    }

    private fun appendProposalPreparationError(message: String) {
        _uiState.update { current ->
            current.copy(
                typedCards = current.typedCards + AssistantCardUiModel.SafeErrorCard(
                    cardId = "PROPOSAL-PREPARATION-ERROR-" + UUID.randomUUID(),
                    userFacingMessage = message
                ),
                isThinking = false
            )
        }
    }

    fun onProposalReviewAction(action: ProposalReviewActionType) {
        val review = activeProposalReview ?: return
        when (action) {
            ProposalReviewActionType.CONTINUE_TO_APPROVAL_NON_EXECUTABLE -> onContinueToApproval(review.first, review.second)
            ProposalReviewActionType.CANCEL_PROPOSAL_REVIEW -> {
                activeProposalReview = null
                _uiState.update { current ->
                    current.copy(typedCards = current.typedCards.filterNot { it is AssistantCardUiModel.ProposalReviewCard })
                }
            }
            else -> Unit
        }
    }

    /**
     * Explicit UI action transitioning proposal review into step 2 confirmation.
     */
    fun onContinueToApproval(proposal: CableEngineeringProposal, reviewModel: ProposalReviewUiModel) {
        _uiState.update { current ->
            val confirmationCard = AssistantCardUiModel.ApprovalConfirmationCard(
                cardId = "CONFIRM-" + proposal.proposalId,
                model = reviewModel,
                isSubmitting = false
            )
            current.copy(
                approvalExecutionState = ApprovalExecutionUiState.Confirming(proposal, reviewModel),
                typedCards = current.typedCards + confirmationCard
            )
        }
    }

    /**
     * Cancels active approval confirmation with zero database/proposal writes.
     */
    fun onCancelApproval() {
        _uiState.update { current ->
            current.copy(
                approvalExecutionState = ApprovalExecutionUiState.Idle,
                typedCards = current.typedCards.filterNot { it is AssistantCardUiModel.ApprovalConfirmationCard }
            )
        }
    }

    /**
     * Sole trusted physical UI action invoking [TrustedProposalApprovalGateway].
     * Enforces single in-flight submission and double-tap prevention.
     */
    fun onConfirmApproveAndApply() {
        val current = _uiState.value
        val confirmingState = current.approvalExecutionState as? ApprovalExecutionUiState.Confirming ?: return

        val proposal = confirmingState.proposal
        val reviewModel = confirmingState.reviewModel
        val userActionId = UUID.randomUUID().toString()

        // 1. Transition to SubmittingApproval (Disables button, establishes in-flight request identity)
        _uiState.update { state ->
            val updatedCards = state.typedCards.map { card ->
                if (card is AssistantCardUiModel.ApprovalConfirmationCard) card.copy(isSubmitting = true) else card
            }
            state.copy(
                approvalExecutionState = ApprovalExecutionUiState.SubmittingApproval(proposal.proposalId, userActionId),
                typedCards = updatedCards
            )
        }

        val boundedContext = BoundedProjectContext(
            projectId = proposal.projectId,
            projectName = current.context.projectName,
            liveRevision = proposal.baseProjectRevision,
            activeScreen = "AssistantReview",
            activeEntityType = proposal.targetEntityType,
            activeEntityId = proposal.targetEntityId,
            activeEntityName = current.context.activeEntityName
        )

        viewModelScope.launch {
            gatewayJob?.join()
            val gateway = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { approvalGateway }
            if (gateway == null) {
                val failureReason = _uiState.value.gatewayErrorMessage
                    ?: "Trusted approval gateway is unavailable."
                _uiState.update { state ->
                    state.copy(
                        approvalExecutionState = ApprovalExecutionUiState.FailedPreCommit(failureReason)
                    )
                }
                return@launch
            }

            val gateResult = gateway.processApprovalAndExecute(
                TrustedApprovalRequest(
                    proposal = proposal,
                    projectContext = boundedContext,
                    userActionId = userActionId
                )
            )

            when (gateResult) {
                is TrustedApprovalGateResult.Success -> {
                    when (val outcome = gateResult.outcome) {
                        is DurableMutationOutcome.Completed -> {
                            val receiptCard = AssistantCardUiModel.MutationReceiptCard(
                                cardId = "RECEIPT-" + outcome.receipt.executionId,
                                receiptId = outcome.receipt.executionId,
                                revisionBefore = outcome.receipt.revisionBefore,
                                revisionAfter = outcome.receipt.revisionAfter,
                                appliedChanges = reviewModel.fieldChanges,
                                revalidationStatus = "SUCCEEDED"
                            )
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.Completed(
                                        receiptId = outcome.receipt.executionId,
                                        revisionBefore = outcome.receipt.revisionBefore,
                                        revisionAfter = outcome.receipt.revisionAfter,
                                        appliedChanges = reviewModel.fieldChanges,
                                        revalidationStatus = "SUCCEEDED"
                                    ),
                                    typedCards = state.typedCards.filterNot { it is AssistantCardUiModel.ApprovalConfirmationCard } + receiptCard
                                )
                            }
                        }
                        is DurableMutationOutcome.CommittedPartial -> {
                            val receiptCard = AssistantCardUiModel.MutationReceiptCard(
                                cardId = "RECEIPT-" + outcome.receipt.executionId,
                                receiptId = outcome.receipt.executionId,
                                revisionBefore = outcome.receipt.revisionBefore,
                                revisionAfter = outcome.receipt.revisionAfter,
                                appliedChanges = reviewModel.fieldChanges,
                                revalidationStatus = "PARTIAL",
                                isPartialOrFailed = true
                            )
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.CommittedPartial(
                                        receiptId = outcome.receipt.executionId,
                                        revisionBefore = outcome.receipt.revisionBefore,
                                        revisionAfter = outcome.receipt.revisionAfter,
                                        appliedChanges = reviewModel.fieldChanges,
                                        revalidationNote = "Partial revalidation"
                                    ),
                                    typedCards = state.typedCards.filterNot { it is AssistantCardUiModel.ApprovalConfirmationCard } + receiptCard
                                )
                            }
                        }
                        is DurableMutationOutcome.CommittedRevalidationFailed -> {
                            val receiptCard = AssistantCardUiModel.MutationReceiptCard(
                                cardId = "RECEIPT-" + outcome.receipt.executionId,
                                receiptId = outcome.receipt.executionId,
                                revisionBefore = outcome.receipt.revisionBefore,
                                revisionAfter = outcome.receipt.revisionAfter,
                                appliedChanges = reviewModel.fieldChanges,
                                revalidationStatus = "FAILED",
                                isPartialOrFailed = true
                            )
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.CommittedRevalidationFailed(
                                        receiptId = outcome.receipt.executionId,
                                        revisionBefore = outcome.receipt.revisionBefore,
                                        revisionAfter = outcome.receipt.revisionAfter,
                                        appliedChanges = reviewModel.fieldChanges,
                                        failureReason = "Revalidation failed"
                                    ),
                                    typedCards = state.typedCards.filterNot { it is AssistantCardUiModel.ApprovalConfirmationCard } + receiptCard
                                )
                            }
                        }
                        is DurableMutationOutcome.ExistingReceiptReturned -> {
                            val receiptCard = AssistantCardUiModel.MutationReceiptCard(
                                cardId = "RECEIPT-" + outcome.receipt.executionId,
                                receiptId = outcome.receipt.executionId,
                                revisionBefore = outcome.receipt.revisionBefore,
                                revisionAfter = outcome.receipt.revisionAfter,
                                appliedChanges = reviewModel.fieldChanges,
                                revalidationStatus = outcome.receipt.revalidationStatus.name,
                                isAlreadyApplied = true
                            )
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.AlreadyApplied(
                                        receiptId = outcome.receipt.executionId,
                                        revisionAfter = outcome.receipt.revisionAfter,
                                        appliedChanges = reviewModel.fieldChanges
                                    ),
                                    typedCards = state.typedCards.filterNot { it is AssistantCardUiModel.ApprovalConfirmationCard } + receiptCard
                                )
                            }
                        }
                        else -> {
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.FailedPreCommit("Unable to complete mutation: $outcome")
                                )
                            }
                        }
                    }
                }
                is TrustedApprovalGateResult.StaleProposal -> {
                    _uiState.update { state ->
                        state.copy(
                            approvalExecutionState = ApprovalExecutionUiState.StaleBeforeApproval(gateResult.reason)
                        )
                    }
                }
                is TrustedApprovalGateResult.PrincipalMissing -> {
                    _uiState.update { state ->
                        state.copy(
                            approvalExecutionState = ApprovalExecutionUiState.FailedPreCommit(
                                "Approval unavailable because engineer identity is not configured. Configure it in Global Settings."
                            )
                        )
                    }
                }
                is TrustedApprovalGateResult.ValidationFailed,
                is TrustedApprovalGateResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            approvalExecutionState = ApprovalExecutionUiState.FailedPreCommit("Approval validation failed.")
                        )
                    }
                }
                is TrustedApprovalGateResult.DuplicateActiveApproval -> {
                    // Ignored or existing grant used
                }
            }
        }
    }

    /**
     * Predictive Back Tier-0 consumer.
     * Returns true if back gesture was consumed by the assistant; false if assistant was closed.
     */
    fun onBackHandled(): Boolean {
        val current = _uiState.value
        if (!current.isOpen) return false

        if (current.approvalExecutionState is ApprovalExecutionUiState.Confirming) {
            // Cancel confirmation without mutating
            onCancelApproval()
            return true
        }

        if (current.approvalExecutionState is ApprovalExecutionUiState.SubmittingApproval) {
            // Submitting in progress: consume Back to prevent UI race
            return true
        }

        if (current.isExpanded) {
            // Collapse to compact first
            _uiState.update { it.copy(state = AssistantState.COMPACT) }
            return true
        } else {
            // Dismiss assistant
            close()
            return true
        }
    }
}
