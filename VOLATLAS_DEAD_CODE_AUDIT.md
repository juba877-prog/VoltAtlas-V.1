# VOLATLAS_DEAD_CODE_AUDIT.md

Companion to `VOLATLAS_AUDIT_MATRIX.md` (Audit U/V/W). **Rule: no deletion in this audit.**
Every candidate needs (1) exhaustive reachability proof and (2) an explicit owner decision.
Repository-wide search tooling (ripgrep) was non-functional this session; reachability evidence below is
from Python scans + targeted reads and is marked PARTIALLY_VERIFIED where exhaustive proof is impossible.

| # | Candidate | Location | Reachability evidence | Class | Safe to delete? | Required proof / precondition |
|---|---|---|---|---|---|---|
| DC-1 | `FirebaseAiServiceImpl` | `app/src/main/java/com/example/ai/service/FirebaseAiServiceImpl.kt` (35 lines) | Not constructed by `ConversationEngineProvider:51-127`; KDoc self-describes Phase-1 stub awaiting "Phase 2"; returns unconditional echo text | PARTIALLY_VERIFIED | **NO** (this session) | Repo-wide reference search (grep) + confirm no reflection/DI; then delete or `@Deprecated internal` |
| DC-2 | `EngineeringAiService` | `ai/service/EngineeringAiService.kt` (44 lines) | Only implementor = DC-1; no composition-root wiring found; `EngineeringViewModel` line 32 import unused | PARTIALLY_VERIFIED | **NO** | Same ref search as DC-1; delete together |
| DC-3 | Nano route (`OnDeviceNanoConversationEngine`, `NanoClientSeam`) | `ai/hybrid/…`, `ai/ondevice/…` | Production router constructed **without** `nanoEngine` (ConversationEngineProvider:121-126); `AiProvider` enum has no Nano; only test constructs it | VERIFIED (unwired) | **NO** (architectural artifact) | Product decision: wire (provider + flag + settings + test) or mark experimental; test suite must move with it |
| DC-4 | Legacy circuit-editor branch | `ui/dialogs/CircuitEditDialog.kt:75-83` (`LegacyCircuitEditDialogInternal` caller) | Compile-dead while `USE_MOBILE_CIRCUIT_EDITOR = true` (:57) | VERIFIED (compile-dead) | **NO** | Un-quarantine the two characterization suites (`ProductionCutover`, `Legacy*` — currently diagnostic-only) before any removal; reconcile with contradictory KDoc (AU-V-03) |
| DC-5 | Quarantined test suites (3) | `test/…` (629 + 629 + 232 lines) | Excluded from fast **and** gate profiles; run only under `diagnostic` — documented, not silent | VERIFIED (quarantined) | **NO** | Fix Compose idle harness → re-enable in gate; never delete (would be test weakening) |
| DC-6 | `DataSheetEntry` legacy ctor + aliases | `data/Models.kt:28-51` (`R_Cu70→R_Cu20` etc.) | Cross-mapping aliases suggest legacy column support; consumption depends on `data_sheet.json` column set | PARTIALLY_VERIFIED | **NO** | Verify asset columns are all modern; assert no reader uses legacy names |
| DC-7 | Duplicate panel-schedule path | `engine/OtherEngines.kt:485-497` (`generateSchedule`) vs `PanelScheduleEngine` | VM:627-629 wires the engine path to `UiState.panels`; flat path still referenced — consumer inventory incomplete (no grep) | PARTIALLY_VERIFIED | **NO** | Prove `UiState.panels` dead or redirect all consumers; method is in PROTECTED engine package |
| DC-8 | Dormant `app_state` persistence | `AppDatabase/AppStateDao:409-419`; `EngineeringRepository:358`; VM:148-151 | Written but never restored at startup (VM forces `activeProjectId = null`) | VERIFIED (dormant) | **NO** (decision needed) | Choose: restore on boot (and test) or remove write path |
| DC-9 | `@Deprecated`/`@Ignore` sweep | repo-wide | Tooling-limited this session (ripgrep broken) | NOT_CHECKABLE | N/A | Re-run sweep with working search tooling |
| DC-10 | Dead plumbing: `transformerFLC` → `AtsEngine` param | `EngineeringViewModel:664,670` → `AtsEngine.transformerFLC` (:39, unused) | Parameter accepted, never read by engine | VERIFIED (dead coupling) | YES (low risk) — with characterization test | Add test asserting ATS results unchanged, then delete plumbing |

## Classification summary
- **Compile-dead:** DC-4 (behind flag), DC-10 (unused param).
- **Unwired but intentional architecture:** DC-3 (Nano), DC-1/DC-2 (Phase-1 legacy awaiting decision).
- **Quarantined, documented:** DC-5.
- **Dormant/incomplete:** DC-6, DC-7, DC-8.
- **Not verifiable this session:** DC-9 (tooling).

## Policy
1. No dead-code deletion performed in this audit run (stop order + missing exhaustive search tooling).
2. Recommended first deletion batch (after ref-search proof): DC-1 + DC-2 together, guarded by an architecture test (`AssistantArchitectureGuardTest`-style) asserting no `com.example.ai.service` references.
3. DC-7 cannot be touched: `OtherEngines.kt` is protected `com.example.engine.*` → **BLOCKED BY PROTECTED ENGINE RULE** unless justified.
