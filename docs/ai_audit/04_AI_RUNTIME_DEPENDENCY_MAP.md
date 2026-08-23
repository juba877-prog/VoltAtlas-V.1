# 04: AI Runtime Dependency Map

## 1. Subsystem Architecture Layers

```
Layer 0: Database & Storage (Room Entities, DAOs, AppDatabase)
  |
Layer 1: Deterministic Calculation Engines & Core Coordinator (EngineeringCalculationCoordinator)
  |
Layer 2: Context, Freshness & Revision Providers (ProjectContextProvider, FreshnessRegistry)
  |
Layer 3: Engineering Task Manager (DefaultEngineeringTaskManager)
  |
Layer 4: Deterministic Tools & Registries (Read Tools, Cable Tools, Unified Registry)
  |
Layer 5: Conversation Policy & Runtime Prompt Compiler (ConversationPolicyEngine, RuntimePromptCompiler)
  |
Layer 6: Hybrid Router & LLM Seam (HybridConversationRouter, CloudLlmConversationEngine, LocalRuleEngineAdapter)
  |
Layer 7: Approval Gateway & Durable Mutation Workflow (TrustedProposalApprovalGateway, DurableMutationWorkflow)
  |
Layer 8: UI & ViewModel (EngineeringAssistantViewModel, EngineeringAssistantSheet, MainLayout)
```

---

## 2. Inbound & Outbound Dependency Matrix

| Component | Inbound Callers | Outbound Dependencies | Boundary Guard |
|---|---|---|---|
| `MainLayout` | `MainActivity` | `EngineeringAssistantSheet`, `EngineeringAssistantViewModel` | UI Composable |
| `EngineeringAssistantViewModel` | `EngineeringAssistantSheet`, `MainLayout` | `ConversationEngine` (via Router), `TrustedProposalApprovalGateway`, `CableProposalPreparationCoordinator` | ViewModel boundary |
| `HybridConversationRouter` | `EngineeringAssistantViewModel` | `CloudLlmConversationEngine`, `LocalRuleEngineAdapter`, `EngineeringClaimGuard`, `NetworkConnectivityProbe` | Fail-safe local fallback |
| `CloudLlmConversationEngine` | `HybridConversationRouter` | `GeminiClientSeam`, `RuntimePromptCompiler`, `ToolSchemaTranslator`, `CloudLlmFeatureGate` | Kill-switch & Claim guard |
| `LocalRuleEngineAdapter` | `HybridConversationRouter` | `ConversationPolicyEngine` | Pure deterministic adapter |
| `ConversationPolicyEngine` | `LocalRuleEngineAdapter` | `ConversationStateManager`, `SlotExtractor`, `EntityResolver` | Pure Kotlin object |
| `TrustedProposalApprovalGateway` | `EngineeringAssistantViewModel` | `DurableMutationWorkflow`, `ApprovalGrantDao`, `ProposalPolicyValidator`, `ProposalDigestEncoder` | Authenticated principal & User action ID |
| `DurableMutationWorkflow` | `TrustedProposalApprovalGateway` | `EngineeringMutationEngine`, `PostCommitRevalidationOrchestrator`, `ApprovalGrantDao`, `MutationExecutionReceiptDao` | `internal` class visibility |
| `EngineeringMutationEngine` | `DurableMutationWorkflow` | `AppDatabase`, `CircuitDao`, `ProjectDao` | Optimistic Revision CAS |
| `PostCommitRevalidationOrchestrator` | `DurableMutationWorkflow` | `EngineeringCalculationCoordinator`, `AppDatabase` | Deterministic recalculation |
| `DeterministicCableToolInvoker` | `ConversationPolicyEngine` | `EngineeringCalculationCoordinator`, `EngineeringTaskManager` | Signature deduplication |
| `DeterministicReadToolInvoker` | `ConversationPolicyEngine` | `ProjectContextProvider`, `CalculationFreshnessProvider`, `EngineeringTaskManager` | Read-only scope |
| `EngineeringCalculationCoordinator` | Tools, ViewModel, Revalidation | Deterministic Calculation Engines, `InMemoryCalculationFreshnessMetadataRegistry` | Project-isolated cache keys |
