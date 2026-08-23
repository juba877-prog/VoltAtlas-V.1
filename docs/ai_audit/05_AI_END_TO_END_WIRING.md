# 05: AI End-to-End Wiring Trace & Flow Diagrams

This document illustrates the exact runtime call graph traces for all four primary flows.

---

## 1. Diagram 1: Current Read-Only Assistant Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Sheet as EngineeringAssistantSheet
    participant VM as EngineeringAssistantViewModel
    participant Router as HybridConversationRouter
    participant Local as LocalRuleEngineAdapter
    participant Policy as ConversationPolicyEngine
    participant ReadInvoker as DeterministicReadToolInvoker
    participant TaskMgr as DefaultEngineeringTaskManager
    participant ReadTool as GetCurrentProjectContextTool
    participant ContextProvider as ProjectContextProviderImpl
    participant DB as AppDatabase / DAOs

    User->>Sheet: Types utterance ("Show project status")
    Sheet->>VM: sendUtterance(text, context)
    VM->>Router: evaluateTurn(input)
    Router->>Local: evaluateTurn(input) [Cloud Gate OFF / Disabled]
    Local->>Policy: evaluateTurn(input)
    Policy->>ReadInvoker: invoke(GetCurrentProjectContextTool)
    ReadInvoker->>TaskMgr: submit(TaskKey, RequestSignature)
    TaskMgr->>ReadTool: execute(ToolRequest)
    ReadTool->>ContextProvider: getCurrentContext(projectId)
    ContextProvider->>DB: Query circuits, transformers, revision
    DB-->>ContextProvider: Live project entities
    ContextProvider-->>ReadTool: BoundedProjectContext
    ReadTool-->>TaskMgr: ToolResultEnvelope(Success)
    TaskMgr-->>ReadInvoker: ToolResultEnvelope
    ReadInvoker-->>Policy: Read Result
    Policy-->>Local: ConversationTurnDecision(DirectAnswer)
    Local-->>Router: HybridEngineResult(LOCAL_RULE_ENGINE)
    Router-->>VM: HybridEngineResult
    VM->>Sheet: Update uiState (AssistantCardUiModel.ResultCard)
    Sheet-->>User: Renders contextual status card
```

---

## 2. Diagram 2: Current Deterministic Calculation / What-If Tool Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Sheet as EngineeringAssistantSheet
    participant VM as EngineeringAssistantViewModel
    participant Router as HybridConversationRouter
    participant Local as LocalRuleEngineAdapter
    participant Policy as ConversationPolicyEngine
    participant CableInvoker as DeterministicCableToolInvoker
    participant TaskMgr as DefaultEngineeringTaskManager
    participant WhatIfTool as RunCableWhatIfTool
    participant Coordinator as EngineeringCalculationCoordinator
    participant Engine as CableCalculationService

    User->>Sheet: Types query ("What if length is 85m for C-01?")
    Sheet->>VM: sendUtterance(text, context)
    VM->>Router: evaluateTurn(input)
    Router->>Local: evaluateTurn(input)
    Local->>Policy: evaluateTurn(input)
    Policy->>Policy: Extract slots (length=85.0, entity=C-01)
    Policy->>CableInvoker: invoke(RunCableWhatIfTool, request)
    CableInvoker->>TaskMgr: submit(CableTaskKey, signature)
    TaskMgr->>WhatIfTool: execute(WhatIfRequest)
    WhatIfTool->>Coordinator: calculateCableSizing(modifiedCircuit, liveCircuits)
    Coordinator->>Engine: calculateSizing(...)
    Engine-->>Coordinator: SizingResult(compliance=PASS, voltageDrop=2.4%)
    Coordinator-->>WhatIfTool: SizingResult
    WhatIfTool-->>TaskMgr: ToolResultEnvelope(WhatIfOutcome)
    TaskMgr-->>CableInvoker: ToolResultEnvelope
    CableInvoker-->>Policy: What-If Result
    Policy-->>Local: ConversationTurnDecision(WHAT_IF_ANALYSIS)
    Local-->>Router: HybridEngineResult(LOCAL_RULE_ENGINE)
    Router-->>VM: HybridEngineResult
    VM->>Sheet: Update uiState (AssistantCardUiModel.WhatIfCard)
    Sheet-->>User: Renders What-If comparison card with slider
```

---

## 3. Diagram 3: Current Proposal / Approval / Mutation / Revalidation Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Sheet as EngineeringAssistantSheet
    participant VM as EngineeringAssistantViewModel
    participant Coord as CableProposalPreparationCoordinator
    participant Gateway as TrustedProposalApprovalGateway
    participant Workflow as DurableMutationWorkflow
    participant MutationEngine as EngineeringMutationEngine
    participant RevalOrch as PostCommitRevalidationOrchestrator
    participant Coordinator as EngineeringCalculationCoordinator
    participant DB as AppDatabase (Room)

    User->>Sheet: Clicks "Create Proposal" for recommended cable
    Sheet->>VM: onProposalReviewAction(CREATE_PROPOSAL)
    VM->>Coord: prepare(action, projectContext)
    Coord->>Coord: Generate CableEngineeringProposal & ProposalReviewUiModel
    Coord-->>VM: Ready(proposal, reviewModel)
    VM->>Sheet: Display AssistantProposalReviewCard (Non-executable review)
    
    User->>Sheet: Clicks "Continue to Approval"
    Sheet->>VM: onContinueToApproval(proposal)
    VM->>Sheet: Display AssistantApprovalConfirmationCard
    
    User->>Sheet: Clicks "Confirm and Apply Mutation"
    Sheet->>VM: onConfirmApproval(userActionId, approvalId, validityMs)
    VM->>Gateway: processApprovalAndExecute(TrustedApprovalRequest)
    
    Gateway->>Gateway: 1. Validate UserActionId is non-blank
    Gateway->>Gateway: 2. Resolve TrustedPrincipalProvider (fail-closed)
    Gateway->>Gateway: 3. Verify Revision CAS (baseRevision == liveRevision)
    Gateway->>Gateway: 4. Compute SHA-256 Digest & Semantic Key
    Gateway->>DB: 5. Insert ApprovalGrantEntity (status=ISSUED)
    
    Gateway->>Workflow: execute(DurableMutationRequest)
    Workflow->>DB: 6. Insert MutationExecutionReceiptEntity (status=IN_PROGRESS)
    Workflow->>MutationEngine: 7. applyMutation(intent, expectedRevision)
    MutationEngine->>DB: 8. Execute SQL CAS (UPDATE circuits SET ... WHERE revision == baseRevision)
    MutationEngine->>DB: 9. Increment Project Revision (revision = baseRevision + 1)
    
    Workflow->>RevalOrch: 10. revalidate(projectId, circuitId, newRevision)
    RevalOrch->>Coordinator: 11. calculateCableSizing(postCommitCircuit)
    Coordinator-->>RevalOrch: PostCommitSizingResult
    RevalOrch-->>Workflow: RevalidationOutcome.Success
    
    Workflow->>DB: 12. Update Receipt (status=COMMITTED_REVALIDATED) & Grant (status=CONSUMED)
    Workflow-->>Gateway: DurableMutationOutcome.CommittedRevalidated
    Gateway-->>VM: TrustedApprovalGateResult.Success
    VM->>Sheet: Display AssistantMutationReceiptCard (Receipt ID, New Revision)
    Sheet-->>User: Visual confirmation of verified database mutation
```

---

## 4. Diagram 4: Intended Target Flow from Official Plan (Cloud Function Proxy)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Sheet as EngineeringAssistantSheet
    participant VM as EngineeringAssistantViewModel
    participant Router as HybridConversationRouter
    participant CloudEngine as CloudLlmConversationEngine
    participant FirebaseProxy as Backend Firebase AI Logic Proxy
    participant GeminiModel as Gemini 2.5 Flash (Google Cloud)
    participant ToolBridge as Tool Execution Bridge
    participant Coordinator as EngineeringCalculationCoordinator
    participant Gateway as TrustedProposalApprovalGateway

    User->>Sheet: Freeform engineering query
    Sheet->>VM: sendUtterance(text, context)
    VM->>Router: evaluateTurn(input)
    Router->>CloudEngine: evaluateTurn(input) [Cloud Gate ON]
    CloudEngine->>FirebaseProxy: HTTPS POST (Encrypted Layer D Context + Utterance + Tool Declarations)
    Note over CloudEngine,FirebaseProxy: Zero client API key in APK; authenticated via Firebase App Check
    FirebaseProxy->>GeminiModel: generateContent with Function Calling
    GeminiModel-->>FirebaseProxy: Tool Call: calculate_cable_sizing(circuitId=101, length=120)
    FirebaseProxy-->>CloudEngine: Tool Call Request
    CloudEngine->>ToolBridge: Execute Deterministic Tool
    ToolBridge->>Coordinator: calculateCableSizing(...)
    Coordinator-->>ToolBridge: Authoritative Sizing Result
    ToolBridge-->>CloudEngine: Deterministic Tool Output
    CloudEngine->>FirebaseProxy: HTTPS POST (Tool Output Result)
    FirebaseProxy->>GeminiModel: generateContent with Tool Output
    GeminiModel-->>FirebaseProxy: Conversational Synthesis + Proposal Intent
    FirebaseProxy-->>CloudEngine: Final Synthesized Response + Proposal Spec
    CloudEngine->>CloudEngine: EngineeringClaimGuard (Verify synthesis matches tool output)
    CloudEngine-->>Router: HybridEngineResult(CLOUD_GEMINI)
    Router-->>VM: HybridEngineResult
    VM->>Sheet: Render Synthesized Explanation + Proposal Review Card
    Note over Sheet,Gateway: Proposal Mutation continues exclusively via Trusted Approval Gateway
```
