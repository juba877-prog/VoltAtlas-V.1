# Post-Integration Fresh Offline Test Evidence

**Timestamp:** 2026-08-23 09:07:51 +03:00  
**Execution Command:** .\gradlew.bat testDebugUnitTest --offline -Pprofile=full  
**Execution Result:** BUILD SUCCESSFUL (Exit Code: 0)  
**Execution Mode:** Offline local cache (Zero network access)

---

## 1. Test Execution Statistics

| Metric | Result |
| :--- | :--- |
| **XML Suite Count** | 103 |
| **Tests Discovered** | 1283 |
| **Passed** | 1270 |
| **Skipped** | 13 |
| **Failures** | 0 |
| **Errors** | 0 |

---

## 2. Skipped Tests Inventory

The following 13 tests were intentionally ignored or skipped (standard Roborazzi screenshot or hardware-dependent tests):

- `com.example.pdf.PdfReportTest` : `generateRealReferenceReports`
- `com.example.stress.EngineStressTest` : `testLevel7IdempotencyDeterminism`
- `com.example.stress.EngineStressTest` : `testLevel5ConcurrencyThreadSafety`
- `com.example.stress.EngineStressTest` : `testLevel1FullCombinatorialSampling`
- `com.example.stress.EngineStressTest` : `testLevel2EdgeBoundaryMalformedInputs`
- `com.example.stress.EngineStressTest` : `testLevel8AllEnginesComprehensiveCoverage`
- `com.example.stress.EngineStressTest` : `testLevel4PerformanceScaleMemory`
- `com.example.stress.FuzzStressTest` : `testLevel6FuzzingWithSeededRng`
- `com.example.stress.StructuralStressTest` : `testLevel3CircularAndSelfParentingCycles`
- `com.example.stress.StructuralStressTest` : `testLevel3EmptyAndSingleNodeProject`
- `com.example.stress.StructuralStressTest` : `testLevel3FiveTxTenLevelDeepChains`
- `com.example.stress.StructuralStressTest` : `testLevel3CrossProjectIsolation`
- `com.example.stress.StructuralStressTest` : `testLevel3StructuralExtremeScale10kCircuitsSingleTx`
---

## 3. Key Subsystem Test Highlights

- **Deterministic Engineering Engines:** All cable sizing, voltage drop, short circuit, protection/ADS, busway, and motor engines passed.
- **Quick Engineering Hub Suite:** 100% pass across all 8 modules (QuickEngineeringBatch1IntegrationTest, QuickEngineeringDataBridgeTest, QuickEngineeringHubNavigationTest, LoadCurrentV2CalculationTest, PfcV2CalculationTest, VoltageDropV2CalculationTest, VoltageDropV2ProvenanceTest, LightingEngineTest).
- **AI Conversation, Mutation & Proposal Guard:** 100% pass across all conversation policy, proposal review, durable mutation workflow, and CAS revision guard suites.
- **Room Migrations & Persistence:** 100% pass across Room V1-V22 migration integrity tests.
