# 03: File-by-File Audit

This document provides detailed audit blocks for every primary production and test file in the AI subsystem.

---

## 1. Production UI & ViewModel Subsystem

### File: `app/src/main/java/com/example/ai/ui/EngineeringAssistantViewModel.kt`
- **Purpose**: Authoritative ViewModel coordinating user utterances, conversation state, proposal preparation, review cards, and explicit approval execution.
- **Current Implementation**: Production implementation holding `StateFlow<AssistantUiState>`. Manages active turn job cancellation, routes utterances through `ConversationEngine`, receives `ConversationTurnDecision`, delegates proposal preparation to `CableProposalPreparationCoordinator`, and handles proposal approval via `TrustedProposalApprovalGateway`.
- **Public API/Contracts**: `sendUtterance(String, Context)`, `onConfirmApproval(String, String, Long)`, `onCancelApproval()`, `onProposalReviewAction(ProposalReviewActionType)`, `close()`, `uiState: StateFlow<AssistantUiState>`.
- **Callers**: `EngineeringAssistantSheet.kt`, `MainLayout.kt`.
- **Dependencies**: `ConversationEngine`, `TrustedProposalApprovalGateway`, `CableProposalPreparationCoordinator`, `AssistantResponseMapper`.
- **Data Read**: Reads active project context (`projectId`, `liveRevision`, `activeEntityType`, `activeEntityId`).
- **Data Written**: Updates UI state (`typedCards`, `approvalExecutionState`, `isThinking`).
- **Threading/Coroutine Behavior**: Uses `viewModelScope` with `Dispatchers.Main`; cancels prior turn job (`activeTurnJob?.cancel()`) when a new utterance arrives or on close.
- **Error Handling**: Converts pipeline errors to `AssistantCardUiModel.SafeErrorCard` or `ApprovalExecutionUiState.FailedPreCommit`.
- **Security/Privacy Behavior**: Zero direct network calls; enforces non-blank `userActionId` before dispatching approval.
- **Feature-Gate Behavior**: N/A (UI layer).
- **Real vs Fake/Stub Status**: **REAL**.
- **Tests**: `EngineeringAssistantViewModelHybridTest.kt`, `AssistantUiSemanticsTest.kt`.
- **Official Phase Mapping**: Phase 7F-5 / Phase 8C.
- **Completion Verdict**: `VERIFIED`.
- **Problems or Contradictions**: None.

### File: `app/src/main/java/com/example/ai/ui/EngineeringAssistantSheet.kt`
- **Purpose**: Material Design 3 Bottom Sheet UI rendering assistant message history, typed cards, blocking questions, what-if sliders, proposal review, and approval confirmation.
- **Current Implementation**: Compose `@Composable` using `ModalBottomSheetLayout` or MD3 standard sheet. Renders `LazyColumn` of `AssistantCardUiModel` with typed card renderers.
- **Callers**: `MainLayout.kt`.
- **Dependencies**: `EngineeringAssistantViewModel`, Material3 components.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7F-3.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/ui/EngineeringAssistantViewModelFactory.kt`
- **Purpose**: Production composition factory constructing `EngineeringAssistantViewModel` with Room DAOs and `ConversationEngineProvider`.
- **Current Implementation**: Provides `production(Application)` companion method wiring `AppDatabase`, `CatalogRepository`, `GlobalPreferences`, `DurableMutationWorkflow.createGateway(...)`, and `ConversationEngineProvider.provideConversationEngine()`.
- **Callers**: `MainActivity.kt`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7F-5.
- **Completion Verdict**: `VERIFIED`.

---

## 2. Hybrid Engine & Router Subsystem

### File: `app/src/main/java/com/example/ai/hybrid/HybridConversationRouter.kt`
- **Purpose**: Authoritative hybrid conversation router directing conversation turns to Cloud LLM or falling back to local rule engine.
- **Current Implementation**: Probes connectivity via `NetworkConnectivityProbe`, applies `cloudTimeoutMs` (4,000ms), evaluates cloud response with `EngineeringClaimGuard`, and falls back to `LocalRuleEngineAdapter` on failure, timeout, or disabled gate.
- **Callers**: `ConversationEngineProvider.kt`, `EngineeringAssistantViewModel.kt`.
- **Dependencies**: `CloudLlmConversationEngine`, `LocalRuleEngineAdapter`, `EngineeringClaimGuard`, `NetworkConnectivityProbe`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 8A.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/hybrid/CloudLlmConversationEngine.kt`
- **Purpose**: Cloud conversation engine implementing `ConversationEngine` via `GeminiClientSeam`.
- **Current Implementation**: Gated by `CloudLlmFeatureGate`. Compiles Layer D prompt via `RuntimePromptCompiler`, translates tools via `ToolSchemaTranslator`, and calls `GeminiClientSeam.generate(...)`.
- **Real vs Fake/Stub Status**: **SEAM / STUB** (`RealGoogleGenAiClientSeam` returns static text stub without real cloud API).
- **Official Phase Mapping**: Phase 8B.
- **Completion Verdict**: `VERIFIED (SEAM)` — Ready for Phase E Firebase proxy.

### File: `app/src/main/java/com/example/ai/hybrid/CloudLlmFeatureGate.kt`
- **Purpose**: Master kill switch and API key check for Cloud LLM invocation.
- **Current Implementation**: Evaluates `BuildConfig.ENABLE_CLOUD_LLM` (defaults to `false`) and `BuildConfig.GEMINI_API_KEY`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 8B.
- **Completion Verdict**: `VERIFIED`.

---

## 3. Conversation & Policy Subsystem

### File: `app/src/main/java/com/example/ai/conversation/ConversationPolicyEngine.kt`
- **Purpose**: Pure deterministic conversation policy engine evaluating user utterances, extracting slots, resolving entities, handling revisions, and generating typed decisions.
- **Current Implementation**: Pure Kotlin object with 0 external dependencies. Supports Arabic and English utterances, resolves cable what-if, navigates entity context, creates proposal intents, and enforces safety disclaimers.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7F-2.
- **Completion Verdict**: `VERIFIED`.

---

## 4. Approval & Durable Mutation Subsystem

### File: `app/src/main/java/com/example/ai/approval/TrustedProposalApprovalGateway.kt`
- **Purpose**: Gateway connecting explicit UI approvals to internal `DurableMutationWorkflow`.
- **Current Implementation**: Requires non-blank `userActionId`, verifies authenticated engineer principal, checks project revision CAS (`baseProjectRevision == liveRevision`), revalidates proposal policy, computes SHA-256 digest, inserts single-use `ApprovalGrantEntity`, and invokes `DurableMutationWorkflow`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7F-5.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/proposal/DurableMutationWorkflow.kt`
- **Purpose**: Internal 5-stage mutation workflow executing database CAS, durable receipt tracking, and post-commit revalidation.
- **Current Implementation**: Internal class unreachable from UI. Runs inside Room transaction, verifies catalog gate, checks active approval grant, updates project revision (`revision + 1`), applies patch via `EngineeringMutationEngine`, triggers `PostCommitRevalidationOrchestrator`, and records `MutationExecutionReceiptEntity`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7E-2.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/proposal/EngineeringMutationEngine.kt`
- **Purpose**: Executes atomic Room database writes for cable circuit patches with optimistic revision CAS.
- **Current Implementation**: Transactional SQL update ensuring `revision == expectedBaseRevision`. Increments project revision and updates cable entity fields.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7C.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/proposal/PostCommitRevalidationOrchestrator.kt`
- **Purpose**: Orchestrates deterministic recalculation post-commit to verify electrical compliance.
- **Current Implementation**: Calls `EngineeringCalculationCoordinator.calculateCableSizing(...)` for modified circuits post-commit. If calculation fails or violations occur, records `COMMITTED_REVALIDATION_FAILED` status on the durable receipt.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 7D.
- **Completion Verdict**: `VERIFIED`.

---

## 5. Tools & Invoker Subsystem

### File: `app/src/main/java/com/example/ai/tools/read/DeterministicReadToolRegistry.kt`
- **Purpose**: Dispatches 6 deterministic read tools (`get_current_project_context`, `get_entity`, `get_calculation_state`, `get_dependencies`, `get_catalog_item`, `get_change_history`).
- **Current Implementation**: Immutable map of typed read tools routed through `DeterministicReadToolInvoker`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 5.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/tools/cable/DeterministicCableToolRegistry.kt`
- **Purpose**: Dispatches 4 deterministic cable tools (`calculate_cable_sizing`, `evaluate_cable_candidate`, `compare_cable_candidates`, `run_cable_what_if`).
- **Current Implementation**: Immutable map of typed cable tools invoking `EngineeringCalculationCoordinator`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 6.
- **Completion Verdict**: `VERIFIED`.

---

## 6. Core Coordinator & Task Manager Subsystem

### File: `app/src/main/java/com/example/engine/coordinator/EngineeringCalculationCoordinator.kt`
- **Purpose**: Unified calculation coordinator for all 5 power domains (Cable, Transformer, Short Circuit, Protection/ADS, Coordination).
- **Current Implementation**: Thread-safe calculation coordinator with project-scoped result caching, dependency resolution, and operational freshness tracking via `InMemoryCalculationFreshnessMetadataRegistry`.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 3 / Core.
- **Completion Verdict**: `VERIFIED`.

### File: `app/src/main/java/com/example/ai/task/DefaultEngineeringTaskManager.kt`
- **Purpose**: In-memory task manager providing concurrency control, task deduplication, latest-wins supersession, multi-owner subscriptions, and progress emissions.
- **Current Implementation**: Production implementation using Kotlin Coroutines, Mutex, and ConcurrentHashMap.
- **Real vs Fake/Stub Status**: **REAL**.
- **Official Phase Mapping**: Phase 4.
- **Completion Verdict**: `VERIFIED`.
