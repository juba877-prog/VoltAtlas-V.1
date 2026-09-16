# VoltAtlas Circuits — Phase C3 Card Implementation Plan V1

> **DOCUMENT ID:** `CIRCUITS_PHASE_C3_CARD_IMPLEMENTATION_PLAN_V1`
> **TASK:** `UI-CIRCUITS-REDESIGN-V1` (Phase C3: Circuit Card Redesign to Approved Mockup)
> **MODE:** PLAN ONLY — NO PRODUCTION CODE WRITTEN.
> **GIT POLICY:** LOCAL REPOSITORY ONLY. No push. No golden regeneration. Transformers screen/goldens untouched.

---

## 0. Objective

Rebuild the Circuits list card body to reproduce the approved mockup: compact information hierarchy,
left semantic status rail, title/Schedule/cable-size/status composition, source breadcrumb,
Load/Ib/In metric row, Vd/Loading-Iz/Isc-Icu metric row, cable description/length overflow footer,
and consistent PASS/FAIL/STANDBY visual treatment — using **only verified VoltAtlas engine data**
and preserving **100% of existing actions**.

Everything ships screen-local under `com.example.ui.screens.circuits` (or as private composables in
`CircuitsGridScreen.kt`). Zero shared-component default changes. Zero ViewModel/engine/entity changes.

---

## 1. Exact Card Mapping (Plan §6.1)

Every visible card element mapped to its exact, already-verified source. Missing computed data renders
the em-dash placeholder `—`; real persisted zero remains rendered zero (e.g. `loadKVA = 0.0 → "0.0 kVA"`).

| # | Card Element | Exact Source | Formatting Rule | Missing Behavior |
|---|---|---|---|---|
| 1 | Circuit name | `CircuitEntity.circuitName` via `CircuitCardUiModel.circuitName` | Title role, bold, single line, ellipsis; no fixed width | Blank entity → `"—"` (already in mapper) |
| 2 | Schedule eligibility | `(panelType.uppercase() in PANEL_TYPES) \|\| hasChildren` → `CircuitCardUiModel.isScheduleEligible`; callback `onNavigateToPanelSchedule(circ.id)` after setting `viewModel.selectedCircuitId.value = circ.id` | Compact "Schedule →" pill, ≥44dp touch target | Hidden entirely when not eligible (never shown disabled) |
| 3 | Cable size | `SizingResult.cableSize_mm2` → `cableSizeDisplay` | `"<size> mm²"`, primary color, prominent on title row | `"—"` |
| 4 | Typed status | `CircuitUiStatus` derived exhaustively from `SizingResult.resultStatus`: PASS→PASS, FAIL→FAIL, WARNING/INCOMPLETE/ERROR/null→STANDBY (`resolveCircuitStatus`) | Text badge (`DsStatusVariant.Pass/Fail/Neutral`) + left rail color. Color never the sole signal | STANDBY treatment |
| 5 | Source breadcrumb | `CircuitUiModelMapper.resolveBreadcrumb(circuit, allCircuits)` → `breadcrumbDisplay` (`parent.name > name` or `fedFrom > name`) | Display-only caption row; NOT clickable, NOT tappable | Self/fallback → circuit name or `"—"` |
| 6 | Load kVA | `CircuitEntity.loadKVA` → `loadDisplay` | `"%.1f kVA"` (Locale.US) | NaN/Inf/negative → `"—"`; persisted `0.0` stays `"0.0 kVA"` |
| 7 | Ib | `SizingResult.Ib_A` → `ibDisplay` | `"%.1f A"` | null/NaN/≤0 → `"—"` |
| 8 | In + device type | `SizingResult.deviceSize_A` + `deviceType` → `inDisplay` | `"<int>A <TYPE>"` (blank type → `"MCCB"`) | null/NaN/≤0 → `"—"` |

| 9 | Cumulative Vd + limit | `SizingResult.cumulativeVDpct` → `vdDisplay`; limit `CircuitEntity.maxVDpct * 100` → `vdLimitDisplay` | `"x.xx%"` and `"y.y%"` shown together (`Vd x.xx% / y.y%`) | Either side independently `"—"` when invalid |
| 10 | Utilization + Iz | `SizingResult.utilizationPct` → `loadingDisplay`; `capacity_derated_Iz_A` → `izDisplay` | `"xx.x%"` and `"zzz.z A"` shown together (`Load-Iz`) | Independent `"—"` per field |
| 11 | Isc 3Φ | `ShortCircuitResult.iscKA` (from `state.shortCircuitResults[circ.id]`) → `isc3phDisplay` | `"%.1f kA"` | No SC result / ≤0 / DATA MISSING sentinel → `"—"` |
| 12 | Icu | `SizingResult.deviceBreakingKA` → `icuDisplay` | `"%.0f kA"` | null/NaN/≤0 → `"—"` |
| 13 | Cable specification | `CircuitEntity.cableType` → part of `footerSpecDisplay` | Verbatim catalog string, unmodified | Blank type omitted from footer |
| 14 | Route length | `CircuitEntity.length_m` → part of `footerSpecDisplay` | `"Length: Nm"` (rounded, Locale.US) | ≤0 → `"Length: —"` |
| 15 | Overflow actions | Existing expanded-state actions, rearranged (see §2) — no removals | Icon/text buttons ≥44dp | n/a |

**Explicitly forbidden on cards (fabrication guard):** synthetic Warning status, freshness indicators,
active-demand values, Isc 1Φ (`ipKA`), `iwKA`, `marginPct`, or any value not present verbatim in the
mapper outputs above. The existing load-conflict badge and unsupported-catalog notice are *existing*
verified signals and are preserved (relocated into expanded state, see §2), not fabricated.

---

## 2. Interaction Architecture (Plan §6.2)

The complete functional path is preserved 1:1. Signatures, callbacks, ViewModel calls, dialogs,
and test tags are unchanged; only visual arrangement changes.

### 2.1 Preserved interaction inventory (verified against current implementation)

| Interaction | Current Implementation | C3 Disposition |
|---|---|---|
| Card-body select/expand | `viewModel.selectedCircuitId.value = if (isSelected) null else circ.id`; tag `circuit_card_<name>` | Identical call & tag; whole compact card body remains the tap target |
| Schedule action (collapsed pill) | tag `open_panel_schedule_chip_<name>`; selects then `onNavigateToPanelSchedule?.invoke(circ.id)` | Kept as mockup-style "Schedule →" pill on footer row; same tag, ≥44dp target |
| Schedule action (expanded icon) | tag `open_panel_schedule_btn_<name>` | Kept inside expanded Operations row |
| Edit | `editingCircuit = circ; showDialog = true`; tag `edit_circuit_btn_<name>` | Kept in expanded Operations row; same tag + callback |
| Delete / cascade confirmation | `circuitToDelete = circ` → cascade `AlertDialog` with dependents list ("Delete Parent Board…" wording preserved); tag `delete_circuit_btn_<name>` | Same state trigger, dialog untouched; button kept in expanded Operations row |
| Contextual AI | `onOpenAssistant(AssistantEntryPoint.CONTEXTUAL_CIRCUIT, circ)` (expanded OutlinedButton) | Kept verbatim in expanded actions; same signature |
| Inline override controls | Device dropdown (`override_breaker_btn`, options `breaker_option_<N>A`), cable dropdown (`override_cable_btn`, options `cable_option_<size>`), Apply Override (`apply_override_btn_<name>` → `viewModel.recalculate()`); Auto entries reset overrides to `null` via `viewModel.updateCircuit(circ.copy(...))` | Entire cluster kept intact inside expanded details, visually grouped but functionally identical |
| Add Circuit FAB | tag `add_circuit_fab`, opens `CircuitEditDialog` with `editingCircuit = null` | Untouched |
| Load-conflict badge | tag `unconfirmed_conflict_badge_<name>`; tap selects + navigates to schedule | Preserved inside expanded state beneath metrics with original tag/callback |
| Unsupported-catalog notice | tag `unsupported_warning_<name>` | Preserved inside expanded state |
| Reset filters / empty state / >50 footer | tags `reset_filters_btn` and list tail item | Untouched |

No action is removed. Nothing moves out of reach: everything reachable today stays reachable with the
same number of taps (select → expanded state).

### 2.2 Proposed arrangement

**Collapsed card (mockup composition):**

```
+-----------------------------------------------------------------------+
|R| MDB-Main                        [70 mm2] [PASS]                     | <- title: name - cable size - status badge
|a| SMDB-1 > MDB-Main                                                   | <- source breadcrumb (display-only)
|i| Load 250.0 kVA | Ib 120.0 A | In 160A MCCB                          | <- metric row 1
|l| Vd 2.45% / 3.0% | Load-Iz 65.0% / 185.0 A                           | <- metric row 2
| | Cu/XLPE/PVC - Length: 15m                        [Schedule ->]      | <- overflow footer (+ pill if eligible)
+-----------------------------------------------------------------------+
```

- `Rail` = 4dp-wide left rail spanning full card height; success/danger/neutral semantic color keyed
  off `CircuitUiStatus`; always paired with the visible PASS/FAIL/STANDBY text badge.
- Metric rows use label+value pairs as weighted cells so narrow layouts can stack (see §3).
- Footer row carries cable spec/length left, Schedule pill right (when eligible).

**Expanded card (selected):** below the collapsed composition, in order:
1. **Operations row:** Schedule (icon, if eligible) · Edit · Delete · Ask AI — trailing-aligned,
   each ≥44dp, original tags.
2. **Extended specifications** (existing detail rows preserved verbatim): Ib, Derating Factor,
   Device & Type, Voltage Drop (V/%), Cumulative VD % / limit, failure-reason block.
3. **Notices:** load-conflict badge, unsupported-catalog warning (original tags/callbacks).
4. **Override cluster:** device dropdown → cable dropdown → Apply Override (unchanged logic/tags).
5. AnimatedVisibility expand/collapse retained (`fadeIn + expandVertically`).

### 2.3 TalkBack focus order (top-to-bottom, left-to-right)

Collapsed: (1) card container (merged semantics: "MDB-Main, PASS, 70 mm², fed from SMDB-1") →
(2) breadcrumb → (3) metric rows (merged per-row semantics) → (4) footer spec → (5) Schedule pill.
Expanded adds after footer: (6) Schedule icon-btn (7) Edit (8) Delete (9) Ask AI (10) extended spec
rows (11) notices (12) device override field (13) cable override field (14) Apply Override.
All interactive elements carry unchanged `contentDescription`s ("Edit Circuit", "Delete Circuit",
"Open Panel Schedule", "Add Circuit", "Apply Override", "Load conflict warning").

---

## 3. Card Visual Rules (Plan §6.3)

1. **One canonical style:** a single private `CircuitCard(...)` composable renders every card;
   no per-status layout forks (only rail/badge color + label differ).
2. **No fixed height:** intrinsic height from content; `Column` flow only.
3. **Minimum interactive targets 44dp:** card body, Schedule pill, all icon buttons, override fields.
4. **360dp @ fontScale 1.0 density target:** two metric rows of three pairs, ~12dp card padding,
   13–15sp title, 11–12sp metrics — matching approved mockup rhythm; the C2 top shell above the
   list remains untouched.
5. **Adaptation without shrinking text:**
   - `< 360dp (320dp)` or `fontScale ≥ 1.3`: metric pairs wrap 3-across → 2-across → stacked via a
     screen-local `BoxWithConstraints`-based helper (e.g. `MetricPairGrid`); typography never drops
     below base sp.
   - Long names/specs ellipsis on collapsed rows; full text available in expanded state.
6. **Side status rail:** PASS→success, FAIL→danger, STANDBY→neutral; always accompanied by the
   text badge (color is never the only signal).
7. **Source breadcrumb display-only:** plain `Text`, no clickable modifier, no ripple.
8. **Theme-aware tokens only:** background/surface/onSurface/onSurfaceVariant + existing semantic
   theme colors already used by the C2 summary strip; no new hardcoded hex.

---

## 4. Shared-Component Protection (Plan §6.4)

- All new composables are **screen-local**: private helpers inside `CircuitsGridScreen.kt`
  (e.g. `CircuitCard`, `CircuitStatusRail`, `MetricPairGrid`) or files under
  `app/src/main/java/com/example/ui/screens/circuits/`.
- `DsCard.kt`, `DsMetricTile.kt`, `DsStatusBadge.kt`, `DsSelectableChip.kt`, `DsSummaryStatCard.kt`
  and all other shared components: **no modifications**, defaults locked. Cards may *consume*
  `DsStatusBadge` read-only via the existing `CircuitUiStatus.toDsStatusVariant()` bridge.
- `TransformersScreen.kt` and its goldens: untouched.
- If any shared-component extension proves unavoidable during review, it will be raised as a
  separate additive mini-plan **before** implementation, never silently.

---

## 5. C3 Visual Gate Plan (Plan §6.5)

Extend the existing Robolectric capture harness pattern (`CircuitsVisualCaptureTest.kt`,
NATIVE graphics → `build/outputs/roborazzi/circuits_c3/`). Planned captures:

| Capture Matrix | Variants |
|---|---|
| Width sweep | Dark + Light @ 320dp, 360dp, 412dp (6 captures) |
| Font scale | Dark + Light @ 1.3× and 1.5× @ 360dp (4 captures) |
| Status treatments | PASS card, FAIL card, STANDBY/no-result card (dedicated fixtures) |
| Stress strings | Long realistic circuit name; long cable description (`footerSpecDisplay` overflow) |
| Expanded state | Selected card showing Operations row + extended specs + override cluster + Ask AI |
| Real host (pre-C6) | Production host with global app bar + bottom navigation: verify no duplicate heading/app bar/nav bar and correct insets (carries forward the C2-R3 harness-title note). |

Sample data uses the same verified fixture shapes as the C2 tests (real `CircuitEntity` /
`SizingResult` / `ShortCircuitResult` instances; no invented fields).

---

## 6. Forbidden Scope Acknowledged (Plan §7)

Will not be touched in C3: engines; ViewModel implementation; entities/Room/repositories;
navigation graph or `MainLayout.kt`; export/PDF/JSON/DXF/screenshot behavior;
`TransformersScreen.kt` and its goldens; global design-system defaults; committed goldens
(re-baselining deferred to explicitly approved C6b). The C2 top shell is frozen.

---

## 7. Test Strategy

- Add a screen-local `CircuitsGridScreenC3Test` following the existing C2 test patterns:
  - collapsed composition per status (PASS / FAIL / STANDBY),
  - breadcrumb presence + non-interactivity,
  - metric-row content incl. `—` fallbacks and real-zero rendering,
  - full interaction regression matrix from §2.1 (every tag resolves, every callback fires),
  - 320dp / 1.3× adaptive stacking without crash,
  - merged-semantics assertions for rail + text badge pairing (TalkBack).
- Mapper tests extended only if new presentation fields prove necessary (prefer none — the mapper
  already supplies every string in §1).
- Acceptance gate wording: *"No unexpected failures beyond the explicitly evidenced baseline and
  intended C2 golden deltas."*

## 8. STOP Confirmation

This document is the C3 plan deliverable only. **No C3 production code, tests, resources, or goldens
have been written.** Implementation begins solely after explicit approval of this plan.


