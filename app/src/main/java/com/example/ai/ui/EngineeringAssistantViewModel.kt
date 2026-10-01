package com.example.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.approval.TrustedApprovalGateResult
import com.example.ai.approval.TrustedApprovalRequest
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.context.EngineeringContext
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.proposal.CableEngineeringProposal
import com.example.ai.proposal.DurableMutationOutcome
import com.example.ai.proposal.RevalidationOutcome
import com.example.ai.proposal.RevalidationStatus
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationLifecycleState
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.hybrid.CloudToolActionMapping
import com.example.ai.hybrid.AiModelConfig
import com.example.ai.hybrid.AiProvider
import com.example.ai.hybrid.AiSettingsState
import com.example.ai.hybrid.ConnectionTestStatus
import com.example.ai.hybrid.ConversationEngine
import com.example.ai.hybrid.ConversationEngineProvider
import com.example.ai.hybrid.ConversationEngineSource
import com.example.ai.hybrid.DirectGeminiClientSeam
import com.example.ai.hybrid.HybridEngineResult
import com.example.ai.hybrid.SecureCredentialStorage
import com.example.ai.hybrid.UserAiPreferences
import com.example.ai.service.AssistantMessage
import com.example.ai.service.AssistantResponse
import com.example.ai.service.MessageRole
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.ToolExecutionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.ai.hybrid.CloudLlmFeatureGate
import com.example.ai.hybrid.AiLogger
import com.example.ai.hybrid.DefaultSecureCredentialStorage

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
    Failed,
    Unavailable
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
    val gatewayStatus: GatewayStatus = GatewayStatus.Unavailable,
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
    private val conversationEngine: ConversationEngine? = null,
    private val credentialStorage: SecureCredentialStorage? = null,
    private val userAiPreferences: UserAiPreferences? = null,
    private val conversationEngineProvider: (() -> ConversationEngine)? = null,
    /** Supplied by the production factory; nullable only to preserve isolated legacy UI tests. */
    approvalGateway: TrustedProposalApprovalGateway? = null,
    private val approvalGatewayProvider: (() -> TrustedProposalApprovalGateway?)? = null,
    private val asyncApprovalGatewayProvider: (suspend () -> TrustedProposalApprovalGateway)? = null,
    private val proposalPreparationCoordinator: CableProposalPreparationCoordinator? = null,
    /**
     * Authoritative revision source. When absent, a turn can only proceed if a prior
     * conversation state already carries a resolved revision; otherwise the turn is
     * blocked rather than being evaluated against a fabricated revision.
     */
    private val projectRevisionProvider: ProjectRevisionProvider? = null,
    /**
     * Deterministic tool runtime supplier. When present, tool-backed turns become reachable
     * from the production assistant path. Resolved lazily on an IO dispatcher because catalog
     * JSON parsing is not safe on the main thread. When absent (legacy isolated UI tests) the
     * assistant still degrades safely because no tool result is ever fabricated.
     */
    private val toolRuntimeProvider: (suspend () -> AssistantToolRuntime?)? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    private fun getActiveConversationEngine(): ConversationEngine {
        return conversationEngine
            ?: conversationEngineProvider?.invoke()
            ?: ConversationEngineProvider.provideConversationEngine(
                credentialStorage = credentialStorage,
                aiProvider = userAiPreferences?.getSelectedProvider(),
                toolRuntimeProvider = toolRuntimeProvider
            )
    }

    private val _aiSettingsState = MutableStateFlow(
        AiSettingsState(
            selectedProvider = userAiPreferences?.getSelectedProvider() ?: AiProvider.DIRECT_GEMINI,
            modelName = userAiPreferences?.getSelectedModel() ?: AiModelConfig.DEFAULT_MODEL_NAME,
            hasCredential = credentialStorage?.hasCredential() ?: false,
            maskedCredential = credentialStorage?.getMaskedCredential() ?: "",
            connectionStatus = ConnectionTestStatus.NOT_CONFIGURED
        )
    )
    val aiSettingsState: StateFlow<AiSettingsState> = _aiSettingsState.asStateFlow()

    fun openAiSettings() {
        val provider = userAiPreferences?.getSelectedProvider() ?: _aiSettingsState.value.selectedProvider
        val model = userAiPreferences?.getSelectedModel() ?: _aiSettingsState.value.modelName
        val hasCred = credentialStorage?.hasCredential() ?: false
        val masked = credentialStorage?.getMaskedCredential() ?: ""
        _aiSettingsState.update {
            it.copy(
                isOpen = true,
                selectedProvider = provider,
                modelName = model,
                hasCredential = hasCred,
                maskedCredential = masked,
                statusMessage = null
            )
        }
    }

    fun closeAiSettings() {
        _aiSettingsState.update { it.copy(isOpen = false) }
    }

    fun selectAiProvider(provider: AiProvider) {
        userAiPreferences?.setSelectedProvider(provider)
        _aiSettingsState.update { it.copy(selectedProvider = provider) }
    }

    fun selectAiModel(model: String) {
        val trimmed = model.trim().ifEmpty { AiModelConfig.DEFAULT_MODEL_NAME }
        userAiPreferences?.setSelectedModel(trimmed)
        _aiSettingsState.update { it.copy(modelName = trimmed) }
    }

    fun saveGeminiCredential(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isNotBlank()) {
            credentialStorage?.saveCredential(trimmed)
            CloudLlmFeatureGate.setCredentialSupplier { credentialStorage?.getCredential() }
        }
        val hasCred = credentialStorage?.hasCredential() ?: false
        val masked = credentialStorage?.getMaskedCredential() ?: ""
        _aiSettingsState.update {
            it.copy(
                hasCredential = hasCred,
                maskedCredential = masked,
                connectionStatus = ConnectionTestStatus.NOT_CONFIGURED,
                statusMessage = if (hasCred) "API key saved securely." else null
            )
        }
    }

    fun removeGeminiCredential() {
        credentialStorage?.clearCredential()
        CloudLlmFeatureGate.setCredentialSupplier { null }
        _aiSettingsState.update {
            it.copy(
                hasCredential = false,
                maskedCredential = "",
                connectionStatus = ConnectionTestStatus.NOT_CONFIGURED,
                statusMessage = "API key removed."
            )
        }
    }

    fun testConnection(apiKey: String? = null) {
        val keyToTest = apiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: credentialStorage?.getCredential()
            ?: CloudLlmFeatureGate.getApiKey().orEmpty()

        if (keyToTest.isEmpty()) {
            _aiSettingsState.update {
                it.copy(
                    connectionStatus = ConnectionTestStatus.NOT_CONFIGURED,
                    statusMessage = "No API key configured to test."
                )
            }
            return
        }

        _aiSettingsState.update {
            it.copy(
                isTestingConnection = true,
                connectionStatus = ConnectionTestStatus.TESTING,
                statusMessage = "Testing connection to Gemini..."
            )
        }

        viewModelScope.launch(ioDispatcher) {
            val result = DirectGeminiClientSeam.testConnection(
                apiKey = keyToTest,
                modelName = _aiSettingsState.value.modelName
            )
            _aiSettingsState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionStatus = result.status,
                    statusMessage = result.message
                )
            }
        }
    }

    private var _approvalGateway: TrustedProposalApprovalGateway? = approvalGateway
    internal var approvalGateway: TrustedProposalApprovalGateway?
        get() = _approvalGateway ?: approvalGatewayProvider?.invoke()?.also { _approvalGateway = it }
        set(value) { _approvalGateway = value }

    private var gatewayJob: Job? = null

    private var currentTurnId: Long = 0L
    private var activeTurnJob: Job? = null
    private var activeConversationState: ConversationTaskState? = null
    private var activeProposalReview: Pair<CableEngineeringProposal, ProposalReviewUiModel>? = null
    private val _conversationEvidence = mutableListOf<com.example.ai.evidence.EngineeringEvidenceRecord>()

    /**
     * Most recent deterministic tool outcome for this assistant session. Non-null only when a
     * real tool executed; this is the evidence handle used to distinguish certified results
     * from unbacked conversation.
     */
    private val _lastToolOutcome = MutableStateFlow<ToolExecutionOutcome?>(null)
    val lastToolOutcome: StateFlow<ToolExecutionOutcome?> = _lastToolOutcome.asStateFlow()

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
        } else {
            _uiState.update { it.copy(gatewayStatus = GatewayStatus.Unavailable) }
        }
    }

    /** P0-14 (O-02, S-11): project the assistant surface currently belongs to (null = none). */
    private var lastOpenedProjectId: String? = null

    /**
     * P0-14 (O-02, S-11): drops ALL project-scoped assistant state when [projectId] differs
     * from the one the surface was last opened for. Called from [open] on every open, and
     * automatically by MainLayout whenever the active project changes (LaunchedEffect on
     * activeProjectId).
     *
     * Conversation messages, typed cards, header, approval/proposal state, the in-flight
     * turn, tool outcome and evidence never survive a project switch — project A's
     * assistant surface can never render under project B. Same project ⇒ no-op (the
     * conversation is preserved across close/reopen).
     */
    fun onProjectChanged(projectId: String?) {
        if (projectId == lastOpenedProjectId) return
        lastOpenedProjectId = projectId
        // Invalidate any in-flight turn so a late response from the old project is dropped.
        currentTurnId++
        activeTurnJob?.cancel()
        activeTurnJob = null
        activeConversationState = null
        activeProposalReview = null
        _lastToolOutcome.value = null
        _conversationEvidence.clear()
        _uiState.update { current ->
            current.copy(
                messages = emptyList(),
                typedCards = emptyList(),
                header = ContextHeaderUiModel(),
                approvalExecutionState = ApprovalExecutionUiState.Idle,
                inputText = "",
                isThinking = false,
                errorMessage = null
            )
        }
    }

    fun open(entryPoint: AssistantEntryPoint, context: EngineeringContext) {
        // P0-14 (O-02, S-11): project-scoped reset on switch (same-project reopen keeps the
        // conversation); MainLayout additionally calls onProjectChanged automatically when
        // the active project changes while the sheet is in use.
        onProjectChanged(context.projectId)

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

        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            val currentCtx = _uiState.value.context

            // Fail closed: never evaluate a turn against a fabricated project identity.
            val projectId = currentCtx.projectId?.takeIf { it.isNotBlank() }
            if (projectId == null) {
                appendContextBlocked(
                    "No active project. Open a project before asking the assistant for engineering results."
                )
                return@launch
            }

            // Fail closed: never evaluate a turn against a fabricated revision.
            val liveRevision = resolveLiveRevision(projectId)
                ?: activeConversationState?.baseProjectRevision
            if (liveRevision == null) {
                appendContextBlocked(
                    "The project revision could not be resolved, so no result can be certified as current."
                )
                return@launch
            }

            val boundedContext = BoundedProjectContext(
                projectId = projectId,
                projectName = currentCtx.projectName,
                liveRevision = liveRevision,
                activeScreen = currentCtx.currentScreen,
                activeEntityType = currentCtx.activeEntityType,
                activeEntityId = currentCtx.activeEntityId,
                activeEntityName = currentCtx.activeEntityName,
                knownEntityInputs = currentCtx.knownEntityInputs,
                validatedResultSummary = currentCtx.validatedResultSummary
            )

            val availableEntities = if (currentCtx.activeEntityType != null && currentCtx.activeEntityId != null) {
                listOf(
                    com.example.ai.conversation.EntityBinding(
                        entityType = currentCtx.activeEntityType,
                        entityId = currentCtx.activeEntityId,
                        entityName = currentCtx.activeEntityName
                    )
                )
            } else {
                emptyList()
            }

            val input = ConversationTurnInput(
                userUtterance = trimmed,
                projectContext = boundedContext,
                priorState = activeConversationState,
                availableEntities = availableEntities,
                priorEvidence = _conversationEvidence.toList()
            )

            val activeEngine = getActiveConversationEngine()
            val provider = userAiPreferences?.getSelectedProvider() ?: _aiSettingsState.value.selectedProvider
            val credConfigured = credentialStorage?.hasCredential() == true
            val cloudEnabled = CloudLlmFeatureGate.isCloudLlmEnabled()

            AiLogger.i("VoltAtlas_AI", "AI_RUNTIME: provider=$provider credentialConfigured=$credConfigured engine=${activeEngine.javaClass.simpleName} cloudEnabled=$cloudEnabled fallbackReason=none")

            val result = activeEngine.evaluateTurn(input)

            if (result.source == ConversationEngineSource.LOCAL_RULE_ENGINE && result.cloudDegradationReason != null) {
                AiLogger.w("VoltAtlas_AI", "AI_RUNTIME: fallback to LOCAL_RULE_ENGINE reason=${result.cloudDegradationReason}")
            } else if (result.source == ConversationEngineSource.CLOUD_GEMINI) {
                AiLogger.i("VoltAtlas_AI", "AI_RUNTIME: CLOUD_GEMINI response received hasBackingToolResult=${result.hasBackingToolResult}")
            }

            // Stale/late result protection: ensure user hasn't submitted a newer turn
            if (turnId != currentTurnId) {
                return@launch
            }

            executeToolForDecision(result, boundedContext)

            if (turnId != currentTurnId) {
                return@launch
            }

            applyHybridResult(result, boundedContext)
        }
    }

    /**
     * Executes the deterministic tool that the resolved decision requires, and records the
     * typed outcome as evidence. This is the production path that makes the previously
     * orphaned read and cable tool families reachable.
     *
     * The project and entity identities passed to the executor come from the authoritative
     * bounded context, never from the conversation model.
     */
    private suspend fun executeToolForDecision(
        result: HybridEngineResult,
        projectContext: BoundedProjectContext
    ) {
        val toolName = CloudToolActionMapping.toolFor(result.decision.action.actionType) ?: return

        // Resolved in the current coroutine context: building the runtime loads and parses
        // catalog JSON internally on IO. Wrapping this in withContext(ioDispatcher) here would
        // move execution onto a non-test dispatcher and break deterministic turn ordering.
        val runtime = toolRuntimeProvider?.invoke() ?: return

        if (!runtime.descriptorRegistry.isRegistered(toolName)) return

        val outcome = runtime.dispatcher.execute(
            toolName = toolName,
            projectId = projectContext.projectId,
            entityType = projectContext.activeEntityType,
            entityId = projectContext.activeEntityId,
            conversationId = activeConversationState?.taskId,
            arguments = result.decision.action.candidateParameters
        )
        _lastToolOutcome.value = outcome
        val rec = com.example.ai.evidence.EngineeringEvidenceRecord.fromOutcome(
            outcome = outcome,
            entityType = projectContext.activeEntityType,
            entityId = projectContext.activeEntityId
        )
        if (!_conversationEvidence.any { it.evidenceId == rec.evidenceId }) {
            _conversationEvidence.add(rec)
        }
    }

    /** Resolves the live project revision from authoritative state; null when unavailable. */
    private suspend fun resolveLiveRevision(projectId: String): Long? {
        val provider = projectRevisionProvider ?: return null
        return try {
            provider.getCurrentRevision(projectId).revision
        } catch (e: NoSuchElementException) {
            null
        }
    }

    /**
     * Surfaces a fail-closed context block. No engineering value is produced, and no
     * fabricated project or revision is substituted.
     */
    private fun appendContextBlocked(message: String) {
        _uiState.update { current ->
            current.copy(
                isThinking = false,
                typedCards = current.typedCards + AssistantCardUiModel.SafeErrorCard(
                    // cardId becomes the conversation LazyColumn key (AssistantConversationOrder:
                    // "card:$cardId"). A millisecond timestamp collides when two context blocks
                    // are raised inside the same millisecond, which Compose reports as a duplicate
                    // key and throws. Match the UUID pattern already used for
                    // PROPOSAL-PREPARATION-ERROR below so this id is unique per emitted card.
                    cardId = "CTX-BLOCKED-" + UUID.randomUUID(),
                    userFacingMessage = message,
                    isRecoverable = true,
                    dataUnchangedConfirmed = true,
                    suggestedActions = emptyList()
                )
            )
        }
    }

    private suspend fun applyHybridResult(
        result: HybridEngineResult,
        projectContext: BoundedProjectContext
    ) {
        val decision = result.decision
        activeConversationState = decision.updatedState
        if (result.evidence.isNotEmpty()) {
            for (rec in result.evidence) {
                if (!_conversationEvidence.any { it.evidenceId == rec.evidenceId }) {
                    _conversationEvidence.add(rec)
                }
            }
        }

        if (decision.action.actionType == ConversationActionType.CREATE_PROPOSAL) {
            when (val preparation = proposalPreparationCoordinator?.prepare(
                action = decision.action,
                projectContext = projectContext,
                decisionId = decision.decisionId,
                evidence = result.evidence.ifEmpty { _conversationEvidence },
                taskState = activeConversationState
            )) {
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
            ),
            evidence = result.evidence.ifEmpty {
                _lastToolOutcome.value?.let {
                    listOf(
                        com.example.ai.evidence.EngineeringEvidenceRecord.fromOutcome(
                            outcome = it,
                            entityType = projectContext.activeEntityType,
                            entityId = projectContext.activeEntityId
                        )
                    )
                } ?: emptyList()
            }
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
        // Phase 6 R-1: normalize ONCE, here, at the single point where an assistant turn
        // becomes the displayed message. The renderer is plain Compose Text (no Markdown or
        // LaTeX library in the module), so raw math delimiters and `**bold**` used to reach
        // the screen literally. Normalizing before the message enters UI state means the
        // normalized text is exactly what is displayed and exactly what conversation history
        // would replay - display and storage can never diverge.
        val replyMsg = AssistantMessage(
            role = MessageRole.ASSISTANT,
            text = EngineeringDisplayFormatter.normalizeDisplayText(replyText)
        )

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

    /**
     * Submits a suggested chip choice as an explicit user turn.
     */
    fun onChipSelected(chipText: String) {
        sendMessage(chipText)
    }

    /**
     * Dismisses a typed card from the active container without side-effects.
     */
    fun onDismissCard(cardId: String) {
        _uiState.update { current ->
            current.copy(typedCards = current.typedCards.filterNot { it.cardId == cardId })
        }
    }

    /**
     * Handles card-level suggested actions (Cancel, Recalculate, Review & Apply, etc.).
     */
    fun onCardAction(cardId: String, action: SuggestedActionUiModel) {
        when (action.actionType) {
            ConversationActionType.CANCEL_TASK -> {
                onDismissCard(cardId)
                if (activeConversationState?.lifecycleState == ConversationLifecycleState.WAITING_FOR_USER_INPUT) {
                    activeConversationState = activeConversationState?.copy(
                        lifecycleState = ConversationLifecycleState.CANCELLED,
                        missingBlockingSlots = emptyList()
                    )
                }
            }
            ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE -> {
                val proposal = activeProposalReview?.first
                val reviewModel = activeProposalReview?.second
                if (proposal != null && reviewModel != null) {
                    onContinueToApproval(proposal, reviewModel)
                } else {
                    sendMessage(action.label)
                }
            }
            else -> {
                sendMessage(action.label)
            }
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

        // Structured approval input. Preconditions are derived from the reviewed proposal model,
        // which was produced by the preparation coordinator from authoritative persisted state.
        // The conversational model contributes nothing to these values.
        val expectedCurrentValues = reviewModel.fieldChanges.mapNotNull { change ->
            change.currentValue?.rawValue?.let { raw -> change.fieldKey to raw }
        }.toMap()

        viewModelScope.launch {
            gatewayJob?.join()
            val gateway = approvalGateway
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

            // Authoritative live revision where available. The trusted approval gateway re-resolves
            // project identity, entity identity, and revision from the durable store regardless, so
            // this projection can never be the authority for a write.
            val authoritativeLiveRevision = resolveLiveRevision(proposal.projectId)
                ?: proposal.baseProjectRevision

            val boundedContext = BoundedProjectContext(
                projectId = proposal.projectId,
                projectName = current.context.projectName,
                liveRevision = authoritativeLiveRevision,
                activeScreen = "AssistantReview",
                activeEntityType = proposal.targetEntityType,
                activeEntityId = proposal.targetEntityId,
                activeEntityName = current.context.activeEntityName
            )

            val gateResult = gateway.processApprovalAndExecute(
                TrustedApprovalRequest(
                    proposal = proposal,
                    projectContext = boundedContext,
                    userActionId = userActionId,
                    expectedCurrentValues = expectedCurrentValues
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
                            onMutationCommitted(outcome.receipt.revisionAfter)
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
                            onMutationCommitted(outcome.receipt.revisionAfter)
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
                            // The mutation remains COMMITTED and its receipt stays authoritative.
                            // A post-commit revalidation failure is deliberately never reclassified
                            // as a mutation failure.
                            onMutationCommitted(outcome.receipt.revisionAfter)
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
                            onMutationCommitted(outcome.receipt.revisionAfter)
                        }
                        is DurableMutationOutcome.NoOp -> {
                            // The approved patch is already the persisted state. Zero mutation,
                            // zero receipt, zero revision advance. Never reported as applied.
                            activeProposalReview = null
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.NoChangeRequired(outcome.sanitizedReason),
                                    typedCards = state.typedCards.filterNot { it is AssistantCardUiModel.ApprovalConfirmationCard },
                                    isThinking = false
                                )
                            }
                        }
                        is DurableMutationOutcome.CurrentValueChanged -> {
                            // The persisted value changed after review. Fail closed and require a new
                            // proposal; the approved patch is never rebased or reinterpreted.
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.StaleBeforeApproval(outcome.sanitizedReason)
                                )
                            }
                        }
                        is DurableMutationOutcome.RevisionConflict -> {
                            _uiState.update { state ->
                                state.copy(
                                    approvalExecutionState = ApprovalExecutionUiState.StaleBeforeApproval(
                                        "The project changed before the mutation could be committed. Review the proposal again."
                                    )
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
     * Post-commit context update (Gate 10 §26).
     *
     * After a durable commit the conversation/task context must reflect the new project state:
     * - the committed proposal is no longer pending,
     * - the task base revision advances to the committed revision so no later turn can be
     *   evaluated against a superseded revision,
     * - stale in-memory proposal bindings are cleared instead of being reused.
     *
     * Historical evidence and receipts are never deleted or overwritten here.
     */
    private fun onMutationCommitted(committedRevision: Long) {
        activeProposalReview = null
        val state = activeConversationState ?: return
        activeConversationState = state.copy(
            baseProjectRevision = committedRevision,
            activeProposalId = null,
            activeProposalDigest = null,
            activeProposalSemanticKey = null
        )
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
