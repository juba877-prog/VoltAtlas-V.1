# VoltAtlas Circuits — Phase C3 Visual Stop Gate R1 Report

> **DOCUMENT ID:** `CIRCUITS_PHASE_C3_VISUAL_STOP_GATE_R1`
> **TASK:** `UI-CIRCUITS-REDESIGN-V1` (Phase C3: Circuit Card Redesign per approved Plan V3)
> **GATE STATUS:** **STOP — ALL C3 CHANGES UNCOMMITTED, AWAITING VISUAL/FUNCTIONAL APPROVAL**
> **GIT POLICY:** LOCAL ONLY. No push. No commit. Zero golden writes. C4 not started.
> **BASELINE:** HEAD `96b3bea` on `integration/home-snapshot-2026-08-23` (unchanged during C3).

---

## 1. Exact Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt` | Card body replaced with canonical `CircuitCardV3` + helpers (`circuitStatusAccent`, `ScheduleChipV3`, `MetricCellData`, `MetricCell`, `MetricPairGrid`, `SpecRowV3`). Old inline card (lines 319-809) removed; top shell untouched. |
| `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt` | Additive: `CircuitUiStatus` gains `WARNING`, `INCOMPLETE`, `ERROR` (exact labels + DsStatusVariant pairing). Summary model untouched. |
| `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt` | `resolveCircuitStatus` → exhaustive six-state mapping; blank `deviceType` no longer fabricates `"MCCB"`; Ib valid-zero now renders; route length precision `"%.1f m"`; STANDBY filter/sort grouping preserved for typed states. |
| `app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt` | Status test updated to non-lossy contract; +4 C3 tests (no-fabrication ×3 assertions, real zero Ib, length precision, display labels). |
| `app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC3Test.kt` | NEW — 20 tests covering gate §11 items. |
| `app/src/test/java/com/example/ui/screens/circuits/CircuitsVisualCaptureTest.kt` | +7 capture methods (§4 below). |

Forbidden layers untouched (verified via `git status`): engines, ViewModel, entities/Room/repositories,
navigation/`MainLayout.kt`, exports, `TransformersScreen.kt`, design-system defaults, committed goldens.

## 2. Override Preservation Verification

| Requirement | Evidence | Status |
|---|---|---|
| Overrides reachable without sizing result | Cluster moved out of `result != null` gate; `test10_overridesReachable_withoutSizingResult` PASS | ✅ |
| Persisted values visible from entity alone | Fields derive from `CircuitEntity`; `test11_persistedOverrideValues_visibleAfterExpand` PASS; capture `C3_MissingResultOverrides_Light_360dp.png` | ✅ |
| Truthful Auto labels (no synthetic values) | `Auto` / `Auto (no sizing run yet)` when result absent; `onAllNodesWithText("Auto (Recommended") == 0` in test10 | ✅ |
| Device type / rating / cable controls intact | Tags `override_device_type_btn`, `override_breaker_btn`, `override_cable_btn` (+ option tags) all resolve in tests 10-12 | ✅ |
| MODE indicator | `MODE: AUTOMATIC SIZING` / `MODE: OVERRIDE ACTIVE` asserted (tests 10, 11) | ✅ |
| Reset to Auto exact tag/path | `reset_to_auto_btn_<name>` present+enabled (tests 10, 11); single `updateCircuit(copy(all three = null))` unchanged line-for-line | ✅ |
| Apply Override exact recalculate path | `apply_override_btn_<name>` click → `viewModel.recalculate()`; `test12_applyOverride_invokesExistingRecalculatePath` PASS | ✅ |
| Catalog-empty truthful reason | `test13_emptyCatalog_showsTruthfulReason_noSyntheticSizes` PASS | ✅ |
| Mutation chain unchanged | `updateCircuit` → `normalizeAutoOverride` → Room `@Update` → auto-recalculate: zero ViewModel/repository edits (`git diff` scoped to allowed files) | ✅ |

## 3. Notice Severity & Visibility Verification

| Notice | Collapsed indicator | Expanded detail | Callback/tag | Test |
|---|---|---|---|---|
| Load conflict | Compact warning pill, exact tag kept | Full supersession text | Select + `onNavigateToPanelSchedule`, invoked once | test7 (presence/absence/callback), test9 (detail), capture `C3_ConflictIndicator_Dark_360dp.png` |
| Unsupported catalog | Compact warning row, never a PASS-looking card | Full explanation text | Display-only (verified current behavior), tag kept | test8 absence path; presence not constructible from shipped data (0 of 34 catalog types unsupported — verified numerically); identical code path as conflict indicator disclosed here |
| Failure reason | Not shown collapsed (FAIL badge carries the signal) | Verbatim block | none | covered by expanded renders |

## 4. Action / Test-Tag Verification (all byte-for-byte, all resolved in tests)

| Tag | Location | Proven by |
|---|---|---|
| `clear_circuit_search_btn`, `search_circuits_input`, `filter_level_selector`, `filter_status_selector`, `circuits_summary_strip`, `circuits_visible_count_header`, `circuits_sort_selector`, `reset_filters_btn` | C2 top shell (untouched) | CircuitsGridScreenC2Test (15/15) + C3 test20 |
| `circuit_card_<name>` | Card container | C3 tests 6, 7, 9-17 |
| `open_panel_schedule_chip_<name>` | Title row (footer alternative at narrow/large-font; mutually exclusive) | C3 test14 (count==1, invokes once) |
| `open_panel_schedule_btn_<name>` | Expanded Operations row | C3 test15 |
| `unconfirmed_conflict_badge_<name>` | Collapsed indicator | C3 test7 |
| `unsupported_warning_<name>` | Collapsed indicator (display-only) | C3 test8 |
| `edit_circuit_btn_` / `delete_circuit_btn_` / `ask_assistant_circuit_btn_` | Expanded Operations row | C3 tests 16, 17 |
| `reset_to_auto_btn_` / `override_device_type_btn` / `device_type_option_*` / `override_breaker_btn` / `breaker_option_*A` / `override_cable_btn` / `cable_option_*` / `apply_override_btn_` | Expanded Override cluster (every circuit) | C3 tests 10-13, captures |
| `confirm_delete_circuit_btn_<name>` | Cascade dialog (untouched) | unchanged code |
| `add_circuit_fab` | FAB (untouched) | C3 test17 |

## 5. Test Results (exact numbers)

- **Targeted Circuits suite** (`com.example.ui.screens.circuits.*`): **71 completed / 71 passed / 0 failed / 0 skipped**
  - `CircuitsGridScreenC2Test`: 15/15 — **C2 behavior fully preserved**
  - `CircuitsGridScreenC3Test`: 20/20
  - `CircuitUiModelMapperTest`: 19/19 (incl. 4 new C3 tests)
  - `CircuitsVisualCaptureTest`: 17/17 (10 pre-existing + 7 new C3 captures)
- **Golden comparison run** (`Step7LightModeGoldenTest`): 9 completed, **5 failed** — exactly the documented baseline:
  1. `circuits_searchAndFilters_dark_matchesGolden` — intended C2 delta (re-baseline at C6b)
  2. `circuits_searchAndFilters_light_matchesGolden` — intended C2 delta (re-baseline at C6b)
  3. `transformer_selectorRow_light_matchesGolden` — pre-existing stale golden
  4. `transformer_emptyState_light_matchesGolden` — pre-existing stale golden
  5. `transformer_emptyState_dark_matchesGolden` — pre-existing stale golden
  → **Zero Transformer signature drift; zero new failures.**
- **Full profile** (`testDebugUnitTest -Pprofile=full`): **1479 completed / 1471 passed / 5 failed / 13 skipped / 0 errors**.
  Acceptable wording: *"No unexpected failures beyond the explicitly evidenced baseline and intended C2 golden deltas."*

## 6. Portable Visual Captures (`app/build/outputs/roborazzi/circuits_c2/`)

Pre-existing C2 matrix: `Circuits_{Dark,Light}_{320dp_narrow,360dp,412dp_Wide}.png`,
`Circuits_{Dark,Light}_FontScale_{1_3x,1_5x}.png`.

New C3 captures:
1. `C3_StatusGallery_Dark_360dp.png` — PASS/WARNING/FAIL/INCOMPLETE/ERROR/STANDBY cards together
2. `C3_StatusGallery_Light_360dp.png`
3. `C3_LongStrings_Dark_360dp.png` — long circuit name + long cable description + decimal length
4. `C3_ExpandedOverrideCluster_Dark_360dp.png` — expanded card with active overrides (MODE: OVERRIDE ACTIVE), Operations row, extended specs, override cluster, Apply/Reset
5. `C3_MissingResultOverrides_Light_360dp.png` — no sizing result; cluster reachable, truthful Auto labels
6. `C3_ConflictIndicator_Dark_360dp.png` — collapsed load-conflict indicator
7. `C3_RealHost_Harness_Dark_360dp.png` — production-host evidence: global `DsTopAppBar` + bottom navigation wrapping the screen inside the capture harness (**no `MainLayout.kt` modification**); confirms no duplicate heading/app bar/bottom nav and correct insets

## 7. Proof the C2 Top Shell Remained Unchanged

- `git diff --stat` shows zero changes outside the six allowed files; the top-shell region of
  `CircuitsGridScreen.kt` (search field, selectors, summary strip, list header, sort) was not edited —
  only the card block (former lines 319-809) was replaced.
- `CircuitsGridScreenC2Test`: **15/15 green**, including summary invariance, content descriptions,
  selector/sort tags, 320dp narrow render, and top-shell tag assertions.
- C3 test20 re-asserts every C2 shell tag post-C3.

## 8. Git State

```
git diff --stat (working tree vs HEAD 96b3bea):
 .../screens/CircuitsGridScreen.kt            | 1304 ++++++++++++--------
 .../circuits/CircuitUiModelMapper.kt         |   44 +-
 .../circuits/CircuitUiModels.kt              |   12 +-
 .../CircuitUiModelMapperTest.kt              |   82 +-
 .../CircuitsVisualCaptureTest.kt             |  224 +++-
 5 files changed, 1155 insertions(+), 511 deletions(-)
```

`git status --porcelain`: five modified allowed files; untracked = three historical plan drafts (V1/V2),
authoritative V3 plan, this gate report, and the new `CircuitsGridScreenC3Test.kt`. Plan V1/V2 are
historical drafts excluded from any future production commit; retention of plan/gate documents is
subject to Mahmoud's explicit approval.

## 9. Confirmations

- ✅ **All C3 work remains uncommitted.** HEAD is still `96b3bea`; nothing staged; no commits made during C3.
- ✅ **No golden regeneration.** Committed PNGs untouched; comparisons executed read-only.
- ✅ **No push. No C4 started.**
- ✅ Scope limited to approved Plan V3 §7 file list; forbidden layers verified untouched via git status.


Indicators are independent of the typed status badge; no fabricated engineering status introduced.
