# VoltAtlas Circuits — Phase C3 Card Implementation Plan V2

> **DOCUMENT ID:** `CIRCUITS_PHASE_C3_CARD_IMPLEMENTATION_PLAN_V2`
> **TASK:** `UI-CIRCUITS-REDESIGN-V1` (Phase C3: Circuit Card Redesign to Approved Mockup)
> **MODE:** PLAN ONLY — NO PRODUCTION CODE WRITTEN. Supersedes V1 per mandatory corrections.
> **GIT POLICY:** LOCAL REPOSITORY ONLY. No push. No golden regeneration until C6b. Transformers untouched.
> **BASELINE:** C2 local commit `96b3bea`.

---

## 1. Corrected Exact Data Map (with zero/precision rules)

Every value renders from verified engine/entity data. Placeholder `—` only where the source contract
proves absence; valid persisted zeros remain zeros.

| Element | Source | Format | Missing / Zero Rule (evidence) |
|---|---|---|---|
| Circuit name | `CircuitEntity.circuitName` → `circuitName` | Title role, bold, ellipsis | Blank → `"—"` |
| Schedule eligibility | `(panelType.uppercase() in PANEL_TYPES) \|\| hasChildren` (`CircuitUiModelMapper.kt:63`) | Title-row pill (see §4) | Hidden when ineligible |
| Cable size | `SizingResult.cableSize_mm2` | `"<size> mm²"` | Blank/null → `"—"` |
| Typed status | See §2 non-lossy matrix | Badge + rail | Missing `SizingResult` → STANDBY |
| Breadcrumb | `resolveBreadcrumb` (`CircuitUiModelMapper.kt:39-50`) | Caption, display-only | Fallback name / `"—"` |
| Load kVA | `CircuitEntity.loadKVA` | `"%.1f kVA"` | NaN/Inf/neg → `"—"`; **persisted `0.0` → `"0.0 kVA"`** |
| Ib | `SizingResult.Ib_A` | `"%.1f A"` | No result / NaN / Inf → `"—"`; **`Ib = 0.0` with an existing result is a genuine zero (zero load) → `"0.0 A"`**. Current `> 0.0` guard (`CircuitUiModelMapper.kt:83`) is corrected in C3; unit test added |
| In + device type | `deviceSize_A` + `deviceType` | `"<int>A <TYPE>"` | Size null/NaN/**≤0** → `"—"` (ratings are positive by contract: catalog ratings; AI mutation rejects ≤0, `EngineeringMutationEngine.kt:199-202`). Blank `deviceType` → **`—` sublabel only, never fabricated** (see §1.1) |
| Cumulative Vd + limit | `cumulativeVDpct`; limit `maxVDpct * 100` | `"x.xx%"`, `"y.y%"`, paired | Independent `"—"` per invalid side; `0.00%` is a valid value and renders |
| Loading + Iz | `utilizationPct`; `capacity_derated_Iz_A` | `"xx.x%"`, `"zzz.z A"` paired | Utilization `0.0%` valid → renders. Iz null/NaN/**≤0** → `"—"` (table-derived capacities positive by construction) |
| Isc 3Φ | `ShortCircuitResult.iscKA` | `"%.1f kA"` | Engine itself marks `iscKA ≤ 0` as DATA MISSING (`ShortCircuitEngine.kt:74-77, 99-102`) → zero proven unavailable → `"—"` |
| Icu | `SizingResult.deviceBreakingKA` | `"%.0f kA"` | null/NaN/≤0 → `"—"` (verified sources: catalog values and assumed 10/36/65 kA defaults, all > 0, `ShortCircuitEngine.kt:126`) |
| Cable spec | `CircuitEntity.cableType` | Verbatim catalog string | Blank → omitted from footer only |
| Route length | `CircuitEntity.length_m` | **`"%.1f m"` preserving decimal precision, e.g. `95.5 m`** (corrected from V1 rounding) | Edit dialog enforces `length_m > 0` (`CircuitEditDialog.kt:1038-1042`), so ≤ 0 / NaN / Inf are proven-invalid → `"—"`; zero cannot persist through validated entry |

### 1.1 No fabricated `MCCB`
Current mapper line `CircuitUiModelMapper.kt:91` substitutes `ifBlank { "MCCB" }`. This is fabrication.
C3 corrects it: blank/null `deviceType` → the type sublabel renders `"—"` or is omitted while the
ampere value still shows (`"160A"` + `"—"` type). **Deliberate behavior change, covered by a new unit
test** (`blank deviceType does not produce MCCB/MCB/ACB anywhere on the card`).

---

## 2. Non-Lossy Status Matrix (typed, exhaustive)

The approved typed contract is restored. `SizingResult.resultStatus` is a non-null typed enum, so
there is **no null branch**. No substring matching anywhere.

| `resultStatus` | Card badge label (exact) | Rail color | Badge variant (`DsStatusVariant`) |
|---|---|---|---|
| *(no `SizingResult`)* | `STANDBY` | `dsExtendedColors.neutral` | `Neutral` |
| `PASS` | `PASS` | `dsExtendedColors.success` | `Pass` |
| `WARNING` | `WARNING` | `dsExtendedColors.warning` | `Warning` |
| `FAIL` | `FAIL` | `dsExtendedColors.danger` | `Fail` |
| `INCOMPLETE` | `INCOMPLETE` | `dsExtendedColors.neutral` (or `info`) | `Neutral` |
| `ERROR` | `ERROR` | `dsExtendedColors.danger` | `Fail` |

- Labels are never renamed; `INCOMPLETE`/`ERROR` are **not** displayed as `STANDBY`.
- All six states have defined rail + badge colors (`DsTheme.kt:19-28`, `DsStatusBadge.kt:27-45`);
  badge text ensures color is never the sole signal.
- **Additive model change (screen-local):** `CircuitUiStatus` gains `WARNING`, `INCOMPLETE`, `ERROR`;
  `resolveCircuitStatus` becomes the exhaustive mapping above. The C2-approved **summary strip is
  frozen** (Total/Pass/Fail/Standby whole-project counts unchanged) — this correction applies to the
  card scope only, exactly as mandated.


---

## 3. Override Preservation Matrix (verified, file:line evidence)

All evidence from `app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt` (CGS) unless noted.
Mutation path for every control: `viewModel.updateCircuit(circ.copy(...))` →
`EngineeringViewModel.kt:782-788` → `repository.updateCircuit(normalizeAutoOverride(...))`
(`EngineeringRepository.kt:200`, Room `@Update` `AppDatabase.kt:460`) → automatic `recalculate()`
(`EngineeringViewModel.kt:786`). `Apply Override` additionally calls `recalculate()` directly
(`EngineeringViewModel.kt:778-780`: bumps `recalculationToken`, re-running the coordinator without
touching stored overrides).

| Existing override/action | Entity/result field | Current UI test tag | Current callback/mutation | Recalc/revalidation | Proposed C3 access point | Preserved test |
|---|---|---|---|---|---|---|
| Device **type** override (distinct control) | `CircuitEntity.overrideDeviceType: String?` (`Entities.kt:44`) | `override_device_type_btn` (CGS:650); options `device_type_option_$typeOption` (CGS:671); options = `["MCB","MCCB","ACB","Switch-Disconnector"]` (CGS:664) | Auto item → `updateCircuit(copy(overrideDeviceType = null))` (CGS:660); option → `copy(overrideDeviceType = typeOption)` (CGS:668) | Auto via `updateCircuit` | Expanded → Override cluster, position 1 (unchanged logic/tags) | `C3Test.overrideDeviceType_*` |
| Device/breaker **rating** override | `overrideDeviceSize: Double?` (`Entities.kt:45`) | `override_breaker_btn` (CGS:704); options `breaker_option_${rating}A` (CGS:725); options = `CableSizingEngine.STANDARD_RATINGS` (CGS:718) | Auto → `overrideDeviceSize = null` (CGS:714); rating → `overrideDeviceSize = rating` (CGS:722) | Auto via `updateCircuit` | Expanded → Override cluster, position 2 | `C3Test.breakerOverride_*` |
| Cable-size override | `overrideCableSize: String?` (`Entities.kt:46`) | `override_cable_btn` (CGS:759); options `cable_option_$sizeStr` (CGS:780); options = `catalogCableSizeOptions(manufacturer, cableType)` (CGS:735) | Auto → null (CGS:769); size → value (CGS:777); dropdown inert when catalog empty (CGS:740) | Auto via `updateCircuit` | Expanded → Override cluster, position 3 | `C3Test.cableOverride_*` |
| Mode indicator | derived: any of the 3 override fields non-null (CGS:572) | none today (text only) | read-only text `MODE: OVERRIDE ACTIVE / AUTOMATIC SIZING` (CGS:573-578) | n/a | Retained verbatim atop Override cluster; no new behavior | covered by override tests |
| Reset to Auto (distinct action — exists today) | clears all three fields: CGS:582-588 | `reset_to_auto_btn_${circ.circuitName}` (CGS:592) | single `updateCircuit(copy(all three = null))` | auto via `updateCircuit` | Retained in Override cluster header row; exact tag + behavior | `C3Test.resetToAuto_*` |
| Apply Override | none (recompute trigger) | `apply_override_btn_${circ.circuitName}` (CGS:796) | `viewModel.recalculate()` (CGS:794) | direct token bump (`EngineeringViewModel.kt:778-780`) | Retained at Override cluster end; exact path | `C3Test.applyOverride_*` |
| Persisted values on reopen/expand/recompose | the 3 override fields | selected strings derive per recomposition: CGS:627 type, CGS:681 rating, CGS:736 cable | read-only display + MODE indicator | Room flow re-emits after update | Identical derivation from `state.circuits` items | `C3Test.overridePersistence_*` |

**Not card-exposed today (honest scope note):** `overrideNeutralSize` / `overrideEarthSize`
(`Entities.kt:47-48`) have no card control anywhere in the current UI; C3 adds no new controls, so
they remain out of scope. No existing control is dropped, made display-only, buried in a dead
overflow item, or deferred. Overrides stay reachable for every circuit with an existing sizing
result (expanded panel sits inside the same `result != null` gating as today, CGS:509 — unchanged).

---

## 4. Exact Collapsed / Expanded Layout (Schedule placement reconciled)

V1 self-contradicted on Schedule placement. Corrected: the approved mockup composition is
**title / Schedule / cable-size / status**, so the Schedule pill's primary home is the **title row**.

**Collapsed card:**

```
+--------------------------------------------------------------------------
|R| MDB-Main [Schedule ->]              [70 mm2] [PASS]      <- title row
|R| SMDB-1 > MDB-Main                                        <- breadcrumb (display-only)
|R| Load 250.0 kVA | Ib 120.0 A | In 160A MCCB               <- metric row 1
|R| Vd 2.45% / 3.0% | Load-Iz 65.0% / 185.0 A | Isc-Icu ...  <- metric row 2
|R| Cu/XLPE/PVC - Length: 15.0 m                             <- overflow footer
+--------------------------------------------------------------------------
```

- Metric row 2 carries `Vd x.xx% / y.y%`, `Load-Iz xx.x% / zzz.z A`, and `Isc-Icu a.kA / b kA` as a
  wrapping pair-grid; at 412dp all three fit, at 360dp they wrap 2+1, at 320dp or fontScale ≥ 1.3
  they stack vertically.
- **Schedule pill fallback:** when the title row cannot hold the pill without shrinking text
  (`< 360dp` or font scale ≥ 1.3), the pill relocates to the footer row trailing edge. Same tag,
  same callback, same ≥44dp target in both positions — placement adapts, action never disappears.

**Expanded card (selected), top-to-bottom:** (1) Operations row: Schedule icon-btn (if eligible) ·
Edit · Delete · Ask Assistant — each ≥44dp, original tags; (2) Extended specifications verbatim
(Ib, Derating Factor, Device & Type, Voltage Drop V/%, Cumulative VD %/limit, failure-reason block);
(3) Notices block (§5); (4) Override cluster in §3 order: MODE indicator + Reset to Auto → device
type → device rating → cable size → Apply Override; (5) AnimatedVisibility retained.

---

## 5. Notice Visibility Matrix

| Notice | Exact condition (evidence CGS) | C3 location | Tag / callback |
|---|---|---|---|
| Load-conflict badge | `hasChildren && circ.directLoadConfirmed == null && abs(loadKVA - childrenTotalLoad) > 0.001` (CGS:382-384) | Expanded → Notices block | `unconfirmed_conflict_badge_${name}` (CGS:397); tap selects + `onNavigateToPanelSchedule` |
| Unsupported-catalog warning | `Catalog.isCableTypeUnsupported(circ.cableType)` (CGS:422) | Expanded → Notices block | `unsupported_warning_${name}` (CGS:431); display-only |
| Failure reason | `result.failReason.isNotBlank()` (CGS:541) | Expanded → specs end | none today; unchanged |

No synthetic Warning/freshness/active-demand/Isc-1Φ anywhere; collapsed rows show only §1 fields.

---

## 6. Action / Test-Tag Map (byte-for-byte)

Every existing tag with its final C3 location (line refs = current CGS unless noted):

| Test tag (exact) | Line | C3 location |
|---|---|---|
| `clear_circuit_search_btn` | 152 | Top shell — unchanged |
| `search_circuits_input` | 165 | Top shell — unchanged |
| `filter_level_selector` (+ `_option_*` via :1148) | 198 | Top shell — unchanged |
| `filter_status_selector` (+ options) | 217 | Top shell — unchanged |
| `circuits_summary_strip` | 225 | Top shell — unchanged |
| `circuits_visible_count_header` | 251 | Top shell — unchanged |
| `circuits_sort_selector` (+ `sort_option_${name}` :1212) | 260 | Top shell — unchanged |
| `reset_filters_btn` | 312 | Empty state — unchanged |
| `circuit_card_${circ.circuitName}` | 327 | Card container |
| `open_panel_schedule_chip_${circ.circuitName}` | 366 | Title-row pill (footer fallback at narrow/large-font) |
| `unconfirmed_conflict_badge_${circ.circuitName}` | 397 | Expanded → Notices |
| `unsupported_warning_${circ.circuitName}` | 431 | Expanded → Notices |
| `open_panel_schedule_btn_${circ.circuitName}` | 481 | Expanded → Operations row |
| `edit_circuit_btn_${circ.circuitName}` | 492 | Expanded → Operations row |
| `delete_circuit_btn_${circ.circuitName}` | 501 | Expanded → Operations row |
| `reset_to_auto_btn_${circ.circuitName}` | 592 | Expanded → Override cluster header |
| `ask_assistant_circuit_btn_${circ.circuitName}` | 605 | Expanded → Operations row (rendered only when `onOpenAssistant != null`, CGS:598) |
| `override_device_type_btn` | 650 | Expanded → Override cluster 1 |
| `device_type_option_$typeOption` | 671 | Same menu |
| `override_breaker_btn` | 704 | Expanded → Override cluster 2 |
| `breaker_option_${rating}A` | 725 | Same menu |
| `override_cable_btn` | 759 | Expanded → Override cluster 3 |
| `cable_option_$sizeStr` | 780 | Same menu |
| `apply_override_btn_${circ.circuitName}` | 796 | Expanded → Override cluster end |
| `add_circuit_fab` | 835 | Screen FAB — unchanged |
| `confirm_delete_circuit_btn_${deletingCircuit.circuitName}` | 909 | Cascade dialog — unchanged |

There is **no three-dot overflow icon** in C3 — every action keeps its explicit, always-visible
control in the locations above. Nothing is deferred to an unimplemented menu.


---

## 7. Accessibility Focus Model (no swallowed nested actions)

- **The whole card is NOT one merged semantic node.** Only the non-interactive summary texts merge:
  - title row: name + cable size + badge text merged ("MDB-Main, 70 mm², PASS");
  - breadcrumb row merges into itself;
  - each metric pair merges label+value+unit within its own cell only.
- Schedule pill, Operations buttons, override fields, Apply/Reset remain **independently focusable**
  with unchanged `contentDescription`s and tags.
- Card container announces selection state via `stateDescription` = "Collapsed" / "Expanded"
  (driven by `viewModel.selectedCircuitId`), never `mergeDescendants` over action children.
- Rail is decorative: `clearAndSetSemantics {}` (not focusable, not read); state is conveyed by the
  badge text.
- Focus order collapsed: title-row merged summary → breadcrumb → metric cells → footer → Schedule
  pill. Expanded appends: Schedule icon-btn → Edit → Delete → Ask Assistant → spec rows → notices →
  device type field → rating field → cable field → Reset to Auto → Apply Override.
- C3 tests assert both focus models and that no clickable descendant is unreachable.

---

## 8. Typography & Color Rules (design-system roles, no drift)

- **No raw sp targets.** Collapsed card uses verified roles from `DsType`
  (`DsType.titleMedium` for the name; `DsType.bodySmall` / `DsType.labelSmall` for metrics, footer,
  breadcrumb — exact role assignment fixed at implementation review). Expanded panel keeps existing
  roles. Density comes from spacing/wrapping (`DsSpacing`), never font-size drift.
- No fixed card height; ~12dp-class internal rhythm measured against the mockup; ≥44dp targets;
  adaptive 3-column / 2+1 / stacked metric grid; full text in expanded state.
- **Runtime theme colors only:** surfaces/text/primary from `MaterialTheme.colorScheme.*`;
  rail/badge semantics from `MaterialTheme.dsExtendedColors.*` (`DsTheme.kt:130`). **No direct
  `DsColor.*` usage** in `CircuitsGridScreen.kt` or any new C3 composable. Light mode relies on tonal
  surface separation, not heavy borders. Shared tokens/defaults locked.

---

## 9. Visual Capture Matrix

Robolectric NATIVE harness → `build/outputs/roborazzi/circuits_c3/`, fixtures using real
`CircuitEntity`/`SizingResult`/`ShortCircuitResult` instances:

| Group | Captures |
|---|---|
| Width sweep | Dark + Light @ 320dp, 360dp, 412dp (6) |
| Font scale | Dark + Light @ 1.3×, 1.5× @ 360dp (4) |
| Status treatments | PASS, FAIL, STANDBY (no result), plus WARNING / INCOMPLETE / ERROR fixtures where constructible via real `SizingResult.resultStatus` values (6) |
| Stress strings | Long realistic circuit name; long cable description overflow (2) |
| Expanded Override state | Selected card showing MODE indicator, device-type/rating/cable dropdowns, Apply Override, Reset to Auto, Ask Assistant (1+) |
| Real host | Production host with global top/bottom bars: no duplicate heading/app-bar/nav-bar, correct insets |

---

## 10. Targeted / Full Test Plan (maps to all 15 mandated items)

New `CircuitsGridScreenC3Test` (+ mapper additions in `CircuitUiModelMapperTest`):

1. Six typed status labels with rail/badge pairing assertions (badge text per §2).
2. Missing result → STANDBY only; WARNING/INCOMPLETE/ERROR never renamed.
3. Blank `deviceType` renders no MCCB/MCB/ACB (mapper unit test + UI assertion).
4. Isc3Φ missing while Icu present (and inverse) render independently.
5. Length precision `95.5 m`; validated-zero/negative → `—` (mapper unit test).
6. Conflict badge + unsupported notice visibility conditions and callbacks (§5).
7. Override values survive expand/collapse/recomposition (all three fields + MODE indicator).
8. Apply Override invokes `recalculate()` path exactly once per tap.
9. Reset to Auto clears all three fields via single `updateCircuit`.
10. Title-row schedule pill (and narrow-layout fallback) + expanded icon-btn both fire navigation.
11. Edit dialog open, Delete cascade dialog wording + confirm tag, Ask Assistant signature intact.
12. Semantics: no lost nested actions; rail not readable; badge conveys state; focus order at
    collapsed + expanded states.
13. 320/360/412dp and 1.3×/1.5× layouts render without crash/clipping.
14. Card internals outside approved scope unchanged (expanded spec rows byte-identical strings).
15. C2 top shell pixel-stable: existing `CircuitsGridScreenC2Test` suite must stay fully green.

Gate wording after full run: *"No unexpected failures beyond the explicitly evidenced baseline and
intended C2 golden deltas."*

---

## 11. File Scope & Stop Gates

**Allowed files (implementation phase):**
- `app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`
- `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt` (additive status entries)
- `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt` (status mapping, blank-device-type fix, length precision)
- `app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt`
- new `app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC3Test.kt`
- visual capture test extension/new class

**Sequence:** implement approved C3 → compile + targeted/full tests → captures (§9 incl. six
statuses + expanded Override capture + real host) → **STOP uncommitted for visual/functional
approval** → only after approval one local commit → NO PUSH → no golden regeneration until C6b.

Forbidden: engines, ViewModel implementation, entities/Room/repositories, navigation/MainLayout,
exports, Transformers screen/goldens, design-system defaults, committed goldens.

---

## 12. Confirmation

This V2 document is plan-only. **No C3 source, test, resource, Gradle, golden, or Git-history changes
have been made.** Current baseline: C2 local commit `96b3bea`; working tree contains only the two
untracked plan documents (`...V1.md`, `...V2.md`).

