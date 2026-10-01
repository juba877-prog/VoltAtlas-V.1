# VOLATLAS_REMEDIATION_PLAN.md

Phase 5 deliverable — remediation decision gate output.
Source findings: `VOLATLAS_AUDIT_MATRIX.md`. Every item cites its finding IDs.
**Status: PLAN ONLY — implementation was halted by stop order before Phase 6.**

Priority model:
- **P0** — fabricated / wrong engineering values or cross-project data leaks reaching users.
- **P1** — reliability / runtime correctness risks (crash, blocking, dead safety paths).
- **P2** — integrity & maintainability (duplication, provenance, lint, doc-vs-code).
- **P3** — hygiene, test gaps, documentation polish.

Protected rule: any change under `com.example.engine.*`, `CircuitEditorParallelEntry.kt`,
`CircuitEditorShell.kt`, `CircuitEditorSessionRuntimeTest.kt` requires
**BLOCKED BY PROTECTED ENGINE RULE** justification in the change log. Items touching protected files are
routed to call-site (VM/UI) fixes instead.

---

## P0 — correctness & isolation (must fix first, each with focused regression test)

| ID | Finding(s) | Defect | Fix (non-protected location) | Regression test |
|---|---|---|---|---|
| P0-1 | A-01, A-02, A-09, A-05, A-08 | Quick breaker fabricates 36 kA Icu/Ics/Icm + generic name when no catalog match | `QuickNewToolsModels.kt`: on `chosen==null` return DATA MISSING state with null capacities; delete/align `FALLBACK_BREAKERS`; provenance from actual result; UI (`QuickBreakerSelectScreen`) shows DATA MISSING banner instead of kA tiles | null-path unit test (no "36.0" anywhere), fallback↔asset parity test, Compose test asserting no kA text when unselected |
| P0-2 | A-04, AB-B13 | Pole filter `\|\| b.poles==3` returns 3P breaker for 4P/1P requests | Remove clause; add `poles` to result model | poles=4 and poles=1 tests asserting no 3P row returned |
| P0-3 | D-01, D-13, T1, T7, D-10 | SLD circuit voltage label ignores `CircuitEntity.voltageV` (contradicts current calc) | `ElectricalNetworkGraphBuilder`: label from `voltageV` when >0 else DATA REQUIRED; fix `SldLabelFieldLibrary` claims alongside | 415 V / 380 V label test; 0-V test |
| P0-4 | D-02, D-03, D-04, D-05, D-06 | Generator/sync-bus/tie/UPS/transformer voltage hardcodes (400/220 V) | Builder: derive from entity or emit explicit null/DATA REQUIRED (transformer already validates range → no `else 400.0`) | zero/out-of-range voltage tests per node type |
| P0-5 | D-07, D-08 | Ib fallback `loadKVA×1.44` fabrication; unguarded `voltageV` divide (Inf/NaN → BusbarSizing) | Guard `>0` (mirror :1185); fallback → entity formula or DATA REQUIRED | 100 kVA/0.8 pf test (180.4 vs 144), 0-V no-NaN test |
| P0-6 | D-12 | SldCanvas feeder callout prints literal " PASS" regardless of limit | Compute PASS/FAIL from `vdPct` vs `maxVDpct` | `vdPct>limit` renders FAIL |
| P0-7 | E-01, E-02, E-03, E-04, E-06, E-07 | First-instance / name-contains fallbacks decide SLD topology silently | Require `size==1` (or explicit FK); else emit AMBIGUOUS / existing typed issues (`ATS_NORMAL_SOURCE_REQUIRED` etc.); delete `?: firstOrNull()` tails; drop foreign-project frequency lookup | 2-MDB ambiguity tests, duplicate-name test, blank-normalSource test |
| P0-8 | E-08 | `busTies` never passed by SldScreen → bus-tie validation dead in production | Add parameter to SldScreen's calculateLayout call; source from repository | call-path test asserting ties reach layout engine |
| P0-9 | C-02, G | U0 forced 230 V → wrong zs/zsMax/loop verdicts | VM: compute `u0_V` from project voltage (415→240 etc.) and pass into `EarthingInput` (engine unchanged) | VM-level tests at 380/415/480 V asserting Zs basis |
| P0-10 | C-03, G | mainBreaker = first-FLC ?: 630 drives shown loop verdict | Remove silent 630; explicit input or DATA MISSING | multi-tx and empty-tx tests |
| P0-11 | C-01, G | Earthing system voltage hardcoded 400 V in VM | Plumb project voltage | 415 V project test |
| P0-12 | C-04, C-06 | Import fallbacks diverge from entity defaults (kFactor 1.0 vs 159 → 159× S; pf/voltage-drop/etc.) | Align import fallbacks to `EarthingEntity`/`CircuitEntity` defaults; emit `DEFAULTED_BY_SCHEMA` notices; legacy soilResistivitySource→ASSUMED | import fallback assertion tests |
| P0-13 | F-02, O-01 | Auto-design result cache survives project switch (renders project A's electrode layout under B, into PDF) | Key cache by projectId; clear on open/create/close/delete | A/B cross-project test |
| P0-14 | O-02, S-11 | Assistant messages/typedCards/proposal state survive project switch | Clear assistant UI state when `projectId` changes in `open()`; hook automatic re-open on project change | A/B assistant isolation test |
| P0-15 | O-03, F-03 | `ProjectIsolationTest` is `assertTrue(true)` (vacuous) | Replace with Room-backed A/B switch test (template: `ImportExportTest:48-70`) | executable isolation assertions |

## P1 — reliability & blocked safety paths

| ID | Finding(s) | Defect | Fix | Regression test |
|---|---|---|---|---|
| P1-1 | K-01, V-02 | `runBlocking` on Main inside suspend chain (parks UI, immune to cancel/timeout, swallows Throwable) | Make `orchestratorProvider` suspend; remove `runBlocking`; log failures | 5 tests: no-main-block, cancellation, timeout, failure propagation, context inheritance |
| P1-2 | L-01, L-02, DC-3 | Nano route dead but presented as available; KDoc claims hardware backing | Mark experimental/unwired (or wire with provider+flag+settings+test); fix KDoc; fail-closed hardware default | production wiring test asserting expected route |
| P1-3 | M-01, M-02, M-03, DC-1, DC-2 | Unreachable unguarded echo stubs beside real pipeline | Ref-search proof → delete (both together) or `@Deprecated internal`; architecture guard test | no-reference guard test |
| P1-4 | J-01 | 15 s outer timeout makes 30 s orchestrator budget unreachable; pre-timeout evidence discarded | Derive cloud timeout from `AgenticSafetyBudget` (or lower totalTimeout) | combined interaction test |
| P1-5 | J-02 | Single-turn branch rejects BACKED text (over-rejection) | Propagate `priorEvidence` backing flag | backed-text acceptance test |
| P1-6 | R-1 | Lint status NOT_RUN, `abortOnError=false` with no baseline | First: run `:app:lintDebug` and triage report; only then decide config changes | recorded lint report artifact |
| P1-7 | I-01 | Conduits/busways tables not validated (empty → silent); conduit rows default APPROVED | Add to `Catalog.loadAll` validation; explicit provenance/status rule | empty-conduit → non-COMPLETE test |
| P1-8 | I-03, AB-B09 | `ConduitFillEngine` type filter no-op (EMT/GI admissible under "Rigid PVC") | **BLOCKED BY PROTECTED ENGINE RULE** → keep strict quick-side filter, file engine ticket; add failing-behavior characterization test | material-parity test documenting defect |
| P1-9 | O-06 | bus_ties orphaned on project delete | Delete in same transaction | bus_ties row-count test |
| P1-10 | O-10 | Delete-confirmation dialog survives project switch | Clear transient dialog state on switch | A/B test |
| P1-11 | E-09 | Blank `activeProjectId` → "DEFAULT_PROJECT" silent resolution | Resolve via app-state store; explicit error otherwise | blank-id test |
| P1-12 | J-10, S-15 | Fast profile excludes all `ai.*` tests (safety controls untested in default loop) | Ensure CI runs `-Pprofile=gate`; include `ai.ui` in fast or document requirement | CI config assertion |

## P2 — integrity & maintainability

| ID | Finding(s) | Fix | Test |
|---|---|---|---|
| P2-1 | AB-B01..B27 (quick/engine duplication) | Route quick tools through authoritative engines via adapters; sync defaults/size ladders | Full parity suite (7 tool pairs) |
| P2-2 | A-06, AB-B16, AB-B21, AB-B22 | Shared helpers: icm n-factor, η term, DF defaults, Icm factor | equivalence tests |
| P2-3 | I-04, AB-B11 | Single breaker parser; fallback↔asset parity or fallback removal | cross-parser equivalence |
| P2-4 | C-05 | Shared `TransformerDefaults` | import-vs-create parity |
| P2-5 | D-14 | Preserve builder provenance through SldScreen | sheet==builder test |
| P2-6 | T3, T4, T5 | DXF emits status verdict; unit formatting guard; PDF shares mapper rules | DXF status test, non-numeric unit test, PDF/UI parity test |
| P2-7 | T6 | Redirect remaining panel-schedule consumers (VM side) | parity test |
| P2-8 | H1, H2, H3, H6 | Surface `isODApproximated` in verdict/PDF/procurement presentation (VM/UI; engines protected) | provenance-carrying test |
| P2-9 | P-1, P-3 | Reword identity KDoc/UI ("device-local attribution", factual security wording) | copy-contract test |
| P2-10 | Q-2, Q-3 | Document backup/restore semantics (VoltAtlas_Globals transfers; DB excluded) | decision record + help-text test |
| P2-11 | J-09, AU-W-03 | Rewrite `ExecutionModePolicy` comment as permanent invariant | — |
| P2-12 | F-08, F-09 | PDF earthing basis rows (U0/In/curve/t) + messages; derive AI complianceStatus | disclosure + fidelity tests |
| P2-13 | O-04 | Gate `DEFAULT_PROJECT` row admission | membership test |
| P2-14 | O-07, O-11 | Re-verify unscoped flows with working search; decide app_state restore-vs-delete | consumer inventory |
| P2-15 | A-03 | Gate quick-tool nav on catalog readiness; surface load failure | pre-catalog nav test |
| P2-16 | R-5 | Narrow R8 keeps; minified smoke test | release smoke test |
| P2-17 | R-2 | Verify release-signing guard under configuration cache (TestKit) | guard-fires test |
| P2-18 | S-13, S-12, S-07, S-09, S-03 | Header ellipsis+weight; AiSettingsDialog widthIn/FlowRow; animate banner height; dedupe error cards; keyboardDismissPending reset | 5 behavior tests |
| P2-19 | AU-V-03, AU-W-01, AU-W-02, AU-W-04, A-08, AB-B25, D-10 | Doc-drift fixes (KDoc/label fields/hub count/Nano/bridge claims) | truth tests (registry-driven, entity-assertion) |

## P3 — hygiene & test gaps

| ID | Finding(s) | Fix |
|---|---|---|
| P3-1 | AB-B27, I-08 | Parity + catalog tests for all 7 quick tools; `Catalog.loadAll` JVM tests; empty-catalog tests (a)-(e) |
| P3-2 | H4 | CableSizing dimension-fallback assertions |
| P3-3 | J-03 | Composition tests wiring `toolRuntimeProvider` (production-only branch) |
| P3-4 | N-02..N-04 | 5-layer hardcode scanner (AI exists; add quick/SLD/adapters/report layers, shared harness) |
| P3-5 | A-07 | Quick tests load real assets instead of fallbacks |
| P3-6 | DC-5 | Fix Compose idle → re-enable quarantined suites in gate |
| P3-7 | DC-10 | Delete dead `transformerFLC` plumbing w/ characterization test |
| P3-8 | D-11 | Remove unused engineering literals from `SldSymbols` default params |
| P3-9 | E-05 | Add "inferred single generator" warning |
| P3-10 | Q-1, R-4 | Backup-rule contract test; static secret-scan test |
| P3-11 | S-14 | 48dp dialog targets; density-correct assertions |
| P3-12 | P2/3 doc drift sweep | Re-run with working search tooling |

## Explicitly NOT remediated (allow-list — do not "fix")
F-04 `flowOn(Dispatchers.Default)`; F-05 revision gating; O-05/O-08/O-09 (disproven); S-01/02/04/05/06/08/10;
Q-1 exclusions; H5 bounded estimation; I-06 standard ladders; A-10 fallback mechanism; AB-B24 standalone motor starter;
E-05 rule (warn only); E-10/E-11 visual-only; N-01 AI-scoped scanner; P-2 attribution semantics; R-3 no-key defaults.

## Gate decision
**CONDITIONAL GO for P0+P1** (all P0 items reachable, evidence-backed, non-protected locations; P1-8 engine change blocked).
**P2/P3 backlog** — no code changes without the missing search tooling (dead-code/proof-dependent items) or lint report (R-1).
Implementation status: **NOT STARTED** (stopped at end of audit per user instruction).
