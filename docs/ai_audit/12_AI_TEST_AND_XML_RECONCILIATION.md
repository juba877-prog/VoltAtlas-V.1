# 12: AI Test & XML Reconciliation

## 1. JUnit XML Programmatic Reconciliation

Programmatic parse across all 94 XML report files in `app/build/test-results/testDebugUnitTest/`:

```text
Suite Count:      94
Tests Discovered: 1,206
Passed:           1,193
Skipped:          13
Failures:         0
Errors:           0
Execution Rate:   100% of executed tests passed (1,193 / 1,193)
```

---

## 2. Inventory of 13 Skipped Tests

1. `com.example.pdf.PdfReportTest > generateRealReferenceReports` (`@Ignore` — skipped during standard local unit test runs)
2. `com.example.stress.EngineStressTest > testLevel1FullCombinatorialSampling` (`@Ignore` — intensive stress test)
3. `com.example.stress.EngineStressTest > testLevel2EdgeBoundaryMalformedInputs` (`@Ignore` — intensive stress test)
4. `com.example.stress.EngineStressTest > testLevel4PerformanceScaleMemory` (`@Ignore` — intensive stress test)
5. `com.example.stress.EngineStressTest > testLevel5ConcurrencyThreadSafety` (`@Ignore` — intensive stress test)
6. `com.example.stress.EngineStressTest > testLevel7IdempotencyDeterminism` (`@Ignore` — intensive stress test)
7. `com.example.stress.EngineStressTest > testLevel8AllEnginesComprehensiveCoverage` (`@Ignore` — intensive stress test)
8. `com.example.stress.FuzzStressTest > testLevel6FuzzingWithSeededRng` (`@Ignore` — intensive stress test)
9. `com.example.stress.StructuralStressTest > testLevel3CircularAndSelfParentingCycles` (`@Ignore` — intensive stress test)
10. `com.example.stress.StructuralStressTest > testLevel3EmptyAndSingleNodeProject` (`@Ignore` — intensive stress test)
11. `com.example.stress.StructuralStressTest > testLevel3FiveTxTenLevelDeepChains` (`@Ignore` — intensive stress test)
12. `com.example.stress.StructuralStressTest > testLevel3CrossProjectIsolation` (`@Ignore` — intensive stress test)
13. `com.example.stress.StructuralStressTest > testLevel3StructuralExtremeScale10kCircuitsSingleTx` (`@Ignore` — intensive stress test)

---

## 3. AI Test Coverage by Subsystem (36 Test Files)

| Subsystem | Test Files Count | Key Test Suites | Status |
|---|---|---|---|
| Revision, Freshness & Context | 4 | `ProjectRevisionTest`, `CalculationFreshnessTest`, `OperationalFreshnessIntegrationTest`, `CalculationFreshnessCorrectionTest` | **PASS (100%)** |
| Task Manager | 1 | `EngineeringTaskManagerTest` | **PASS (100%)** |
| Deterministic Tools | 2 | `DeterministicReadToolsTest`, `CableEngineeringToolsTest` | **PASS (100%)** |
| Proposals & Mutations | 6 | `EngineeringProposalContractsTest`, `ProposalPolicyAndDigestTest`, `Phase7E1DurableSchemaTest`, `EngineeringMutationEngineTest`, `PostCommitRevalidationOrchestratorTest`, `DurableMutationWorkflowTest` | **PASS (100%)** |
| Approval Gateway | 2 | `TrustedProposalApprovalGatewayTest`, `TrustedApprovalEndToEndTest` | **PASS (100%)** |
| Conversation & Policy | 5 | `ConversationPolicyEngineTest`, `ConversationGoldenFlowTest`, `GoldenConversationEndToEndTest`, `ArabicEnglishBehaviorParityTest`, `AdversarialAssistantSafetyTest` | **PASS (100%)** |
| Runtime & Hybrid Router | 7 | `RuntimePromptCompilerTest`, `ToolSchemaTranslatorTest`, `EngineeringClaimGuardTest`, `CloudLlmFeatureGateTest`, `CloudLlmConversationEngineTest`, `HybridConversationRouterTest`, `HybridPipelineRegressionTest` | **PASS (100%)** |
| UI & ViewModels | 8 | `AssistantUiSemanticsTest`, `AssistantResponseMapperTest`, `ProposalReviewMapperTest`, `ProposalReviewUiSemanticsTest`, `ApprovalExecutionUiStateTest`, `AssistantOfflineBadgeTest`, `EngineeringAssistantViewModelHybridTest`, `AssistantArchitectureGuardTest` | **PASS (100%)** |
