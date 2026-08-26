# VoltAtlas Circuits — Phase C3 Card Implementation Plan V3 (Authoritative)

> **DOCUMENT ID:** `CIRCUITS_PHASE_C3_CARD_IMPLEMENTATION_PLAN_V3`
> **TASK:** `UI-CIRCUITS-REDESIGN-V1` (Phase C3: Circuit Card Redesign to Approved Mockup)
> **MODE:** PLAN ONLY — NO PRODUCTION CODE WRITTEN. Supersedes V2; resolves both blocking corrections.
> **GIT POLICY:** LOCAL REPOSITORY ONLY. No push. No commit. No golden regeneration until C6b.
> **BASELINE:** HEAD = C2 local commit `96b3bea` (verified below).

---

## 1. Corrected Override Preservation Matrix — Overrides reachable for EVERY circuit

### 1.1 Evidence audit (why Option 1 works without touching forbidden layers)

The current code gates the entire expanded panel behind `result != null`
(`CircuitsGridScreen.kt:509`). Verified dependency analysis shows this gate is **only required by the
Extended Specifications block**, not by any override control:

| Dependency | Needs `SizingResult`? | Evidence |
|---|---|---|
| Persisted override fields (`overrideDeviceType/Size/CableSize`) | **No** — plain `CircuitEntity` columns | `Entities.kt:44-46` |
| Device-type option list `["MCB","MCCB","ACB","Switch-Disconnector"]` | **No** — static list | `CircuitsGridScreen.kt:664` |
| Device-rating option list `CableSizingEngine.STANDARD_RATINGS` | **No** — static list | `CircuitsGridScreen.kt:718` |
| Cable-size options `catalogCableSizeOptions(manufacturer, cableType)` | **No** — pure entity/catalog lookup | `CircuitOverrideUtils.kt:66`; call at CGS:735 |
| Mutation path `updateCircuit(...)` → Room update → auto-recalculate | **No** — operates on entity only | `EngineeringViewModel.kt:782-788`, `normalizeAutoOverride(circuit: CircuitEntity)` at `EngineeringViewModel.kt:419`, repository `EngineeringRepository.kt:200` |
| Apply Override → `recalculate()` token bump | **No** | `EngineeringViewModel.kt:778-780` |
| Only the "Auto (engine value)" display strings | **Yes** — `result.deviceType/deviceSize_A/cableSize_mm2` | CGS:627, 681, 736 |

**Resolution adopted: Preferred Option 1.** In C3 the Operations row and Override cluster render for
**every circuit**, moved out of the `result != null` gate; only Extended Specifications remains
result-gated. **Zero changes to ViewModel/repository/engine are required — no blocker, no silent
scope widening.**

### 1.2 Truthful Auto display without a sizing result (no synthetic values)

When `SizingResult` is absent, the "Auto" menu entries and field values must not invent engine
values:

| Control | With sizing result (unchanged) | Without sizing result (new, truthful) |
|---|---|---|
| Device type field | `Auto (${result.deviceType})` (CGS:627) / menu `Auto (Recommended ${result.deviceType})` (CGS:658) | Field shows persisted override or literal `Auto`; menu item labeled `Auto (no sizing run yet)` — **never a guessed device type** |
| Device rating field | `Auto (${result.deviceSize_A} A)` (CGS:681) | Persisted override or `Auto`; **no ampere value displayed** |
| Cable size field | `Auto (${result.cableSize_mm2})` (CGS:736) | Persisted override or `Auto`; options still real catalog sizes |
### 1.3 Override Preservation Matrix (full, with missing-result rows)

Evidence refs: CGS = `CircuitsGridScreen.kt` (current line numbers), EVM = `EngineeringViewModel.kt`.
Mutation chain for every row: `updateCircuit(copy(...))` → `normalizeAutoOverride(entity)` (EVM:419,
785) → `repository.updateCircuit` (`EngineeringRepository.kt:200`, Room `@Update`
`AppDatabase.kt:460`) → auto `recalculate()` (EVM:786). Apply Override additionally bumps
`recalculationToken` directly (EVM:778-780).

| # | Existing override/action | Entity/result field | Current UI test tag | Current callback/mutation | Recalc/revalidation | C3 access point (every circuit) | Preserved test |
|---|---|---|---|---|---|---|---|
| 1 | Device **type** override | `overrideDeviceType: String?` (`Entities.kt:44`) | `override_device_type_btn` (CGS:650); options `device_type_option_$typeOption` (CGS:671); Auto item (CGS:657-663) | Auto → null (CGS:660); option → value (CGS:668) | auto via updateCircuit | Expanded → Override cluster 1; rendered for ALL circuits | `C3Test.overrideDeviceType_*` |
| 2 | Device/breaker **rating** override | `overrideDeviceSize: Double?` (`Entities.kt:45`) | `override_breaker_btn` (CGS:704); options `breaker_option_${rating}A` (CGS:725); Auto item (CGS:711-717) | Auto → null (CGS:714); rating → value (CGS:722) | auto via updateCircuit | Cluster 2; ALL circuits | `C3Test.breakerOverride_*` |
| 3 | Cable-size override | `overrideCableSize: String?` (`Entities.kt:46`) | `override_cable_btn` (CGS:759); options `cable_option_$sizeStr` (CGS:780); Auto item (CGS:766-772); inert when catalog empty (CGS:740) | Auto → null (CGS:769); size → value (CGS:777) | auto via updateCircuit | Cluster 3; ALL circuits; catalog-empty reason line per §1.2 | `C3Test.cableOverride_*` |
| 4 | Mode indicator | derived from the 3 fields (CGS:572) | none today | read-only text (CGS:573-578) — works without result | n/a | Verbatim atop cluster; ALL circuits | covered by override tests |
| 5 | Reset to Auto | clears all three (CGS:582-588) | `reset_to_auto_btn_${circ.circuitName}` (CGS:592) | single `updateCircuit(copy(all three = null))` | auto via updateCircuit | Cluster header row; exact tag/behavior; ALL circuits | `C3Test.resetToAuto_*` |
| 6 | Apply Override | recompute trigger only | `apply_override_btn_${circ.circuitName}` (CGS:796) | `viewModel.recalculate()` (CGS:794) | direct token bump | Cluster end; exact path; ALL circuits | `C3Test.applyOverride_*` |
| 7 | Persisted values on reopen/expand/recompose | the 3 entity fields | field strings derive per recomposition (CGS:627/681/736 today) | read-only display + MODE indicator | Room flow re-emits | Identical derivation — **from entity only when result absent** | `C3Test.overridePersistence_*` |
| 8 | **NEW** Missing-sizing-result circuit reaches Overrides | — | same tags as rows 1-6 | identical callbacks | recalculate runs; result appears if data suffices | Overrides block no longer inside `result != null` gate | `C3Test.overridesReachableWithoutResult_*` |
| 9 | **NEW** Truthful Auto labels without result | — | unchanged tags | unchanged | unchanged | Per §1.2 table; zero synthetic device/cable values | `C3Test.noSyntheticValuesWithoutResult_*` |
| 10 | **NEW** Catalog-empty cable reason | — | dropdown inert (CGS:740) | unchanged | unchanged | Explicit reason text under selector | `C3Test.cableCatalogEmptyReason_*` |

`overrideNeutralSize` / `overrideEarthSize` (`Entities.kt:47-48`) remain out of scope (no current
card control exists; C3 adds none).


After an override is set from a missing-result state, `updateCircuit` persists it and auto-triggers
recalculation; when the coordinator produces a sizing result, the card transitions to its typed
status and the Auto labels gain real engine values via normal recomposition.

Catalog-empty cable selector (existing inert behavior preserved, CGS:740): dropdown does not open,
and an explicit user-facing reason line renders beneath it: *"No catalog sizes available for this
cable family."* (consistent with the existing unsupported-catalog wording family, CGS:425). Never a
synthetic size list.


---

## 2. Notice Severity & Visibility Matrix

Notices are **separate from the typed sizing-status badge** — no fabricated engineering status is
introduced. Any notice meaning "shown data may be unreliable / superseded / unsupported / requires
user action" keeps a compact collapsed indicator; full explanatory text lives in Expanded.

| Notice | Verified condition (evidence CGS) | Severity / user impact | Current collapsed visibility | V3 collapsed representation | Expanded detail | Callback/tag |
|---|---|---|---|---|---|---|
| Load-conflict badge | `hasChildren && circ.directLoadConfirmed == null && abs(loadKVA - childrenTotalLoad) > 0.001` (CGS:382-384) | **High — action required:** panel load may be superseded by children; shown values unreviewed | Full badge visible on card (CGS:386-419) | **Compact warning indicator retained** (small warning glyph + short label, ≥44dp target), exact tag kept | Full supersession text + review guidance in Notices block | Tap = select + `onNavigateToPanelSchedule(circ.id)`; tag `unconfirmed_conflict_badge_${circ.circuitName}` (CGS:397) — unchanged |
| Unsupported-catalog warning | `Catalog.isCableTypeUnsupported(circ.cableType)` (CGS:422) | **High — reliability:** sizing cannot run from catalog data; a bare PASS-looking card would mislead | Warning text visible on card (CGS:423-433) | **Compact warning indicator retained** (never a normal PASS-looking card); display-only, exact tag kept | Full explanation text in Notices block | None today (display-only, CGS:423-433 verified) — stays display-only; tag `unsupported_warning_${circ.circuitName}` (CGS:431) |
| Failure reason | `result.failReason.isNotBlank()` (CGS:541) | Medium — informational for FAIL cards | Not shown collapsed (FAIL badge already signals) | No new collapsed indicator (typed FAIL badge covers signal; nothing fabricated) | Verbatim failure-reason block in specs | none |

Collapsed indicators use the same theme-aware warning color as the existing badges
(`dsExtendedColors.warning`) and always pair icon with short text (color never sole signal).

### Added tests (notices)
- Collapsed indicator present under each verified condition (conflict fixture; unsupported fixture).
- No indicator when each condition is false.
- Expanded Notices block still carries the full text for both.
- Conflict indicator tap → selection set + `onNavigateToPanelSchedule` invoked exactly once
  (navigation preserved).
- Unsupported notice asserts zero click action (display-only) and its exact tag.

---

## 3. Status Scope Clarification (C2 summary frozen)

The six-state typed card status matrix accepted in V2 stands: missing result → `STANDBY`;
`PASS`/`WARNING`/`FAIL`/`INCOMPLETE`/`ERROR` preserve exact labels. This expansion is
**screen-local to the Circuits card scope** (`com.example.ui.screens.circuits` models only). The
**already-approved C2 Project Health Summary remains frozen during C3** — Total/Pass/Fail/Standby
whole-project counts and their semantics are untouched; summary aggregation is not modified by C3.

---

## 4. Schedule Duplication Rule

- Title-row Schedule pill = primary position; at <360dp or fontScale ≥1.3 it relocates to the footer
  row as an **alternative position** (never a duplicate).
  - Title/footer responsive action tag: `open_panel_schedule_chip_${circ.circuitName}` (CGS:366)
  - Expanded action tag: `open_panel_schedule_btn_${circ.circuitName}` (CGS:481) — retained solely to
    preserve its exact existing tag/contract.
- **Invariant: title pill and footer fallback are mutually exclusive** — one composable slot whose
  placement switches on available width/fontScale; they can never render simultaneously.
- Tests: title path fires navigation exactly once per tap at 360dp+; fallback path fires exactly once
  at 320dp / 1.3×; expanded button fires independently; assert-at-most-one-of
  (chip XOR fallback) via uniqueness assertion on merged semantics.


---

## 5. Final Collapsed / Expanded Interaction Model (unchanged from V2 except §1-§4 corrections)

**Collapsed:** left 4dp status rail (six-state color map) + title row (name · Schedule pill/fallback ·
cable size · typed badge) + display-only breadcrumb + metric rows (Load/Ib/In; Vd+limit /
Load-Iz / Isc-Icu pair-grid with adaptive wrap) + overflow footer (cable spec • length) + compact
notice indicators per §2. No fixed height; ≥44dp targets; `DsType` roles only;
`colorScheme.*`/`dsExtendedColors.*` only.

**Expanded (selected), top-to-bottom:** (1) Operations row: Schedule icon-btn (if eligible) · Edit ·
Delete · Ask Assistant — original tags; (2) Extended Specifications verbatim — **this block alone
remains gated on an existing sizing result**; (3) Notices block with full text; (4) Override cluster
for EVERY circuit: MODE indicator + Reset to Auto → device type → rating (+ truthful Auto labels) →
cable size (+ catalog-empty reason) → Apply Override; (5) AnimatedVisibility retained.
Accessibility model (no whole-card merge, independent action focus, decorative rail, state
announcement) as specified in V2 §7, unchanged.

## 6. Added Tests for the Two Corrected Areas

Override access without sizing result:
- `overridesReachableWithoutResult`: circuit fixture with no `SizingResult` → all six override tags
  resolve and are enabled where operable; cable selector still lists real catalog sizes.
- `overridePersistence_withoutResult`: persisted override values render from entity fields alone;
  survive expand/collapse/recomposition.
- `resetToAuto_clearsAllThreeFields`: from a missing-result state, one tap clears all three fields
  via a single update path.
- `applyOverride_afterMissingResultChange`: setting an override then Apply triggers recalculation;
  when data suffices the card gains its typed status on recomposition.
- `cableCatalogEmptyReason`: empty catalog → dropdown inert + explicit reason line shown; no
  synthetic sizes anywhere (`noSyntheticValuesWithoutResult`).

Notice severity/visibility:
- collapsed indicator present for conflict condition and absent otherwise;
- collapsed indicator present for unsupported condition and absent otherwise;
- expanded full texts available in both blocks;
- conflict tap navigates exactly once with exact tag preserved;
- unsupported notice asserts display-only behavior and exact tag preserved.

## 7. Exact Allowed File Scope & Stop Gates

- `app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`
- `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt` (additive status entries)
- `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt` (status mapping,
  blank-device-type fix, length precision)
- `app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt`
- new `app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC3Test.kt`
- capture test extension/new class

Forbidden: engines, ViewModel implementation, entities/Room/repositories, navigation/MainLayout,
exports, Transformers screen/goldens, design-system defaults, committed goldens. The restructure of
the card's gating lives entirely inside `CircuitsGridScreen.kt`.

Sequence: implement approved C3 → compile + targeted/full tests → captures (incl. missing-result
override state, notice indicators, six statuses, expanded override cluster, real host) → **STOP
uncommitted for approval** → after approval ONE local commit → NO PUSH → goldens only at C6b.

## 8. Plan Hygiene & Confirmations

- **V3 is the only authoritative C3 plan.** V1/V2 remain historical untracked documents and will be
  excluded from any future C3 production commit unless Mahmoud explicitly approves retention.
- Baseline verified: HEAD = `96b3bea` (C2 commit), branch `integration/home-snapshot-2026-08-23`.
- Working tree: only the three untracked plan documents (V1, V2, V3). No Kotlin/test/resource/
  golden/Gradle changes.
- ✅ **No C3 code, tests, resources, or goldens have been written.**

