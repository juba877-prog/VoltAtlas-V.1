# VOLATLAS_PHASE6_IMPLEMENTATION_LOG.md

Phase 6 working log — controlled implementation. Audit documents are evidence records; **not overwritten**.

## Batch 0 — Baseline Safety (COMPLETE)

| Check | Result |
|---|---|
| Starting commit | `abd3110` (feat(ui): adaptive multi-device layouts) — matches audit baseline HEAD |
| Tracked diff vs HEAD | **EMPTY** — zero production/test files modified |
| Working tree | Untracked only: 7 VOLATLAS_*.md audit artifacts + pre-existing scratch `*.txt` (appsrc, g1, g2, kt2, kt_all, kt_only, kt_tmp, str_test, tmp_test — left untouched, not created in Phase 6, not deleted) |
| Audit artifacts correspond to tree | YES (written against `abd3110` this session) |
| Protected files unchanged | CONFIRMED — `git diff HEAD` empty ⇒ `com.example.engine.*`, `CircuitEditorParallelEntry.kt`, `CircuitEditorShell.kt`, `CircuitEditorSessionRuntimeTest.kt` untouched |
| Baseline tests (pre-Phase-6) | fast EXECUTED 1477/0F/2skip; gate FROM-CACHE 2641/0F/14skip (cached ⇒ not current evidence; re-run fresh per §12) |

## Batch 1 — P0 Engineering integrity (COMPLETE)
Scope: P0-1..P0-12 (breaker fabrication, pole matching, SLD voltages/Ib, Vd verdict, topology resolution, bus ties, earthing u0/breaker/voltage, import schema-default parity).
Checkpoint commit: `fix(ph6): Phase 6 Batch 1 - P0 Engineering Integrity (P0-1..P0-12)` (see `git log`; exact hash not embedded here to keep it stable across amend).
Final consolidated fresh evidence: **63 suites / 377 tests / 0 failures / 1 skip** (see Tests run §5).

## Planned batches
- **B1 P0 Engineering integrity:** P0-1..P0-12 (breaker fabrication, poles, SLD voltages, Ib/NaN, PASS/FAIL, topology, bus ties, earthing U0/breaker/voltage, import defaults)
- **B2 P0 Isolation:** P0-13..P0-15 (auto-design cache, assistant context, real isolation test)
- **B3 P1 Runtime/AI/Catalog:** P1-1..P1-12
- **B4 P2 Source of truth:** P2-1..P2-17 (parity consolidation) — only after B1–B3 green
- **B5 P2 Outputs/docs:** exports, provenance, identity wording, backups, PDF disclosure
- **B6 P3/Cleanup:** only after core green
- **B7 Final verification + real post-audit**

## Item status table
| ID | Status | Files | Tests | Result | Notes |
|---|---|---|---|---|---|
| P0-1 | VERIFIED | QuickNewToolsModels.kt, QuickBreakerSelectScreen.kt, QuickEngineeringToolsTest.kt | QuickEngineeringToolsTest (focused) | 13/13 PASS | FALLBACK_BREAKERS deleted; model now expresses absence (all fields null); statuses CATALOG_MISSING / NO_EXACT_CATALOG_MATCH; UI renders DATA_MISSING banner + no kA tiles |
| P0-2 | VERIFIED | QuickNewToolsModels.kt, QuickEngineeringToolsTest.kt | testP0_2_exactPoleMatching | PASS | pole filter now exact-match only (`poles<=0` = unspecified ⇒ any); 1P/3P/4P covered |
| P0-3 | VERIFIED | ElectricalNetworkGraphBuilder.kt, SldScreen.kt, SldVoltageIntegrityTest.kt | SldVoltageIntegrityTest (380/400/415/230/0V) | 6/6 PASS | circuit voltage label = CircuitEntity.voltageV; 0V ⇒ DATA REQUIRED; SldScreen.toSldNode fixed (√3 only for 3ph, no literal 400 V) |
| P0-4 | VERIFIED | ElectricalNetworkGraphBuilder.kt, SldTopologyAndEquipmentIntegrityTest.kt | SldTopologyAndEquipmentIntegrityTest | PASS | gen voltage ← linked ATS systemVoltage (NO new DB column — deviation recorded below); sync bus ← member agreement; UPS ← systemVoltage; tie ← endpoints (+mismatch surfaced); tx invalid voltage ⇒ DATA REQUIRED (no 400 V FLC) |
| P0-5 | VERIFIED | ElectricalNetworkGraphBuilder.kt, SldVoltageIntegrityTest.kt | SldVoltageIntegrityTest (0V board, Ib) | PASS | 1.44 fallback removed; all voltage divisions guarded (finite check); 0V board ⇒ no Infinity/NaN, busbar/diversity skipped |
| P0-6 | VERIFIED | SldCanvas.kt, SldVdVerdictTest.kt | SldVdVerdictTest | 4/4 PASS | single `computeVdVerdict` source: ≤limit PASS, >limit FAIL, missing basis ⇒ DATA REQUIRED; text + color derive from same verdict (hardcoded 5%/3% color thresholds removed) |
| P0-7 | VERIFIED | ElectricalNetworkGraphBuilder.kt, SldTopologyAndEquipmentIntegrityTest.kt | SldTopologyAndEquipmentIntegrityTest (4 topology tests) | PASS | unique-resolution only: 2-MDB ⇒ AMBIGUOUS_SOURCE_INSTANCE; dup names ⇒ AMBIGUOUS; generic keyword w/ multiple ⇒ AMBIGUOUS; `?: firstOrNull()` tails deleted; foreign-project frequency tail deleted; ATS downstream board excluded from its own normal source (loop prevention) |
| P0-8 | VERIFIED | SldScreen.kt, SldScreenBusTieCallPathTest.kt | SldScreenBusTieCallPathTest + builder tie synthesis test | 3/3 + builder PASS | Room flow (getBusTiesForProject) → remember key → `busTies = busTies` in calculateLayout call path |
| P0-9 | VERIFIED | `ui/EarthingInputBasis.kt` (new), `ui/EngineeringViewModel.kt` | EarthingInputBasisTest | 11/11 PASS | u0 basis: unique tx secondary (ambiguous tx ⇒ unique circuit line-V or prefs default + AMBIGUOUS note) → unique 3φ circuit V → 1φ-only project (stored V IS phase V, u0=V) → prefs default; `phaseNeutralVoltageOf` (380→220/400→230/415→240/480→277/else ÷1.732) is single-source, also called by `createDefaultCircuit` |
| P0-10 | VERIFIED | `ui/EarthingInputBasis.kt`, `ui/EngineeringViewModel.kt` | EarthingInputBasisTest (FLC tests) | PASS | main breaker = `distinctFlcs.singleOrNull()` (single tx, or identical parallel FLCs); none/differing multi-tx ⇒ null ⇒ DATA MISSING + message disclosing engine's protected 630 A assumption; verdict never driven by fabricated 630 |
| P0-11 | VERIFIED | `ui/EarthingInputBasis.kt`, `ui/EngineeringViewModel.kt`, `ui/ProtectionScreen.kt`, `pdf/Sections.kt` | EarthingInputBasisTest + pdf.* + ProtectionResponsiveTest | PASS | `applyToResult`: loopCheck="DATA MISSING", overallCompliant=false, status="⚠ REVIEW REQUIRED — DATA MISSING"; Zs_max row + PDF render "DATA MISSING" when basis missing (zs_ohm kept — breaker-independent); engine untouched (protected) |
| P0-12 | VERIFIED | `data/EngineeringRepository.kt`, `ImportDefaultParityTest.kt` (new) | ImportDefaultParityTest 4/4 + ProjectPersistenceIntegrityTest 13/13 + ImportExportTest 1 + DatabaseMigrationTest 19 + data.MigrationTest 3 + engine.GroupingDeratingTest 16 | PASS | circuit/pfGlobal/earthing import fallbacks == entity schema defaults (pf 0.8, safety 1.25, Elsewedy, Cu/XLPE/SWA/PVC, maxVD 0.03 / 0.97·50·6000·1.5·0.03·0.5 / 50·17·5·25·0.6·**159**·10, ASSUMED); DEFAULTED_BY_SCHEMA notice per absent cited field; legacy `soilResistivitySource=DEFAULT_TABLE` ⇒ ASSUMED + MIGRATED; explicit values never rewritten (test #4) |
| P0-13..P0-15 | NOT_STARTED (Batch 2) | — | — | — | — |
| P1-1..P1-12 | NOT_STARTED | — | — | — | — |
| P2-* | NOT_STARTED | — | — | — | — |

## Tests run (Phase 6 executions only)
1. `gradlew testDebugUnitTest -Pprofile=fast --tests com.example.QuickEngineeringToolsTest` → BUILD SUCCESSFUL, **13 tests / 0 failures** (P0-1 ×2, P0-2 ×1 regression; test now loads canonical `breaker_catalog.json`)
2. `gradlew testDebugUnitTest -Pprofile=fast --tests <4 SLD suites>` → **58 tests / 0 failures** (P0-3/P0-5 ×6 + existing SLD builder suites)
3. `gradlew testDebugUnitTest -Pprofile=fast --tests <44 SLD suites + SldScreenBusTieCallPathTest>` → BUILD SUCCESSFUL, **43 result suites / 229 tests / 0 failures / 0 errors / 0 skipped** (includes new P0-4/P0-7 ×14, P0-6 ×4, P0-8 ×3)
   - NOTE: one intermediate run had 1 failure — `SldGraphAndLayoutTest.testAtsTopologyAndValidation` (expected circ_10, was tx_TX-01). Classified **legitimate changed behavior**: old expectation encoded list-order first-or-null selection between 2 MDB candidates (the audited defect). Fixed production (downstream-board exclusion + explicit AMBIGUOUS for >1 candidates), test expectation left untouched.
   - Two of 44 filtered suites (DebugLayoutTest, SldScreenScreenshotTest — Roborazzi-gated) produce no result files without `-Proborazzi.test.record`; they are skipped, not passed.
4. P0-9..P0-12 focused runs (Phase 6):
   - Affected suites after earthing edits (pdf.*, ProtectionResponsiveTest, DsSegmentedPillTabRowTest, PowerAndShortCircuitScreenshotTest, ImportExportTest, ViewModelTest, CatalogReadyGuardTest, EngineeringViewModelRevisionFailureSurfacingTest, EarthingEngineTest, PowerSystemsDownstreamZeroHonestyTest, MultiInstancePowerSystemsTest, EarthingInputBasisTest) → **81 tests / 0 failures / 2 skips** — both skips pre-existing `@Ignore` (EngineeringViewModelRevisionFailureSurfacingTest:37; PdfReportTest:19 "Requires real Android environment").
   - Import-path run (ImportDefaultParityTest, ProjectPersistenceIntegrityTest, ImportExportTest, DatabaseMigrationTest) → **37 tests / 0 failures**; then engine.GroupingDeratingTest + data.MigrationTest (first attempt used wrong FQCNs `com.example.GroupingDeratingTest` / `com.example.MigrationTest` — matched nothing, re-ran with correct `com.example.engine.` / `com.example.data.`) → **19 tests / 0 failures**.
   - Intermediate failure (before fix): `ProjectPersistenceIntegrityTest.testV2RoundTripFieldByFieldIntegrityAndRemapping` — my initial soil-source whitelist rewrote the explicitly carried value `"Soil Analysis"` → ASSUMED on round-trip. Classified **wrong expectation (my change)**: violates round-trip integrity (locked test) and the "explicit values never rewritten" rule; audit C-04 authorizes only the documented legacy token. Narrowed mapping to `DEFAULT_TABLE` only; added `carriedNonLegacySoilSource_isPreservedVerbatimWithoutNotice` to lock it. After fix: 13/13 + 4/4.
5. **B1 FINAL consolidated fresh run** (all B1-relevant suites, single execution, XML timestamps 2026-10-01T05:30:36Z→05:31:20Z): **63 suites / 377 tests / 0 failures / 0 errors / 1 skip** — 42 `com.example.sld.*` (226) + SldScreenBusTieCallPathTest (3) + QuickEngineeringToolsTest (13) + EarthingInputBasisTest (11) + EarthingEngineTest (14) + ImportDefaultParityTest (4) + ProjectPersistenceIntegrityTest (13) + ImportExportTest (1) + DatabaseMigrationTest (19) + data.MigrationTest (3) + engine.GroupingDeratingTest (16) + ViewModelTest (1) + CatalogReadyGuardTest (1) + ProtectionResponsiveTest (2) + DsSegmentedPillTabRowTest (3) + PowerAndShortCircuitScreenshotTest (17) + PowerSystemsDownstreamZeroHonestyTest (7) + MultiInstancePowerSystemsTest (12) + pdf.* (4 suites, 11). Sole skip = `PdfReportTest.generateRealReferenceReports` (pre-existing `@Ignore`). Coverage verified: zero missing / zero extra vs intended filter set.

## Blockers
- ripgrep glob/grep broken (env) → repo searches via Python scripts / `git grep` / `read`.
- PowerShell non-functional; shell quoting mangled → Python scripts in temp dir.

## Deviations from plan
- P0-1: fallback breaker table DELETED (plan allowed "delete/align" — proved not alignable: phantom ordering codes + wrong capacities per audit A-05). CATALOG_MISSING now distinct explicit state (feeds P2-15).
- P0-4 generator column migration AVOIDED — generator voltage derived from linked ATS (`AtsEntity.systemVoltage` via the same `emergencySourceId` relationship used for frequency); no independent voltage property exists on GeneratorEntity. Sync bus derives from member agreement (unknown/disagreeing ⇒ DATA REQUIRED).
- P0-5: `SizingResult.Ib_A` is non-null ⇒ the `loadKVA×1.44` branch was **unreachable dead code** (latent fabrication, audit D-07 "conditional" overstated) — removed anyway; `ibAmp` now always DATA REQUIRED when sizing absent.
- P0-7: added downstream-board exclusion to ATS blank-normal-source candidates (an ATS cannot be normal-fed from the board it supplies — completes the existing assignedAtsId exclusion); this resolves the previously order-dependent fixture deterministically without weakening the size==1 rule.
- New issue code introduced: `AMBIGUOUS_SOURCE_INSTANCE` (INVALID/DISCONNECTED) — plan explicitly required an AMBIGUOUS class; existing codes kept for all other conditions.
- Compile fixes during P0-1: `ImmutableList +` returns `List` → rebuilt provenance list with `PersistentList.add` chains (no behavior change).
- P0-9/10/11 (engine PROTECTED → caller-side only): `EarthingEngine.kt:184/:188` (u0 fallback 230, inMain fallback 630) untouched; `EarthingInputBasis` always passes explicit u0/systemVoltage (230 V fallback never fires) and converts a missing breaker basis into `loopCheck="DATA MISSING"` + `overallCompliant=false` + message disclosing the 630 A engine assumption — the engine's internal default can never drive a verdict.
- P0-10: single-transformer FLC retained as the unique defensible breaker basis (disclosed via note); empty or differing multi-transformer FLCs ⇒ DATA MISSING (audit finding: no installed-breaker entity exists — do not invent one).
- P0-11: `zs_ohm` stays numeric when basis missing (it depends only on u0/ik1, breaker-independent); only Zs_max (breaker-dependent) renders DATA MISSING.
- P0-12 **REJECTED for `installation`**: C-06's generic "fallback==schema default" conflicts with prior authorized fix **A1-R** (`ProjectPersistenceIntegrityTest.testImport_missingInstallationKey_isNotDefaultedAndIsReported`, `EngineeringRepository.kt:837` warning, `CableSizingEngine.kt:364` loud failure) — "an imported circuit with no installation key must NOT be given a fabricated default". `installation` stays blank + warned; no notice (no default applied). A1-R test retained as authority.
- P0-12 soil-source mapping NARROWED after test failure: initial whitelist (anything outside MEASURED/SOIL_REPORT/ASSUMED ⇒ ASSUMED) rewrote explicit carried values → round-trip failure. Final rule: only the documented legacy token `DEFAULT_TABLE` migrates (⇒ ASSUMED + MIGRATED notice); all other carried values preserved verbatim.
- P0-12 OBSERVATION (not changed): `createDefaultCircuit` (EngineeringViewModel:354-396) uses pf 0.85 / safetyFactor 1.0 / preference `maxVDpct` — diverges from entity schema defaults (0.8 / 1.25 / 0.03). In-app creation semantics are out of P0-12 scope; only import fallbacks were aligned to entity constructors. C-05 transformer %Z parity is P2-4.
- P0-12 cableType note: neither the old fallback `CU/XLPE/PVC` nor the new `Cu/XLPE/SWA/PVC` is an exact `Catalog.cableTypes` entry (catalog values carry ` - 3C`/` - 4C` suffixes) → sizing fails `CATALOG_MISSING` loudly either way; the change only aligns the disclosed value with the schema default (behavior class unchanged). Round-trip exports write resolved catalog values, so normal flows never hit the fallback.
