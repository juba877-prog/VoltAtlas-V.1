# VoltAtlas Circuits Screen Redesign Implementation Plan (UI-CIRCUITS-REDESIGN-V1-R2)

**Document:** `CIRCUITS_SCREEN_IMPLEMENTATION_PLAN_V1_R2.md`  
**Execution Mode:** PLAN CORRECTION ONLY (No source, resource, test, golden, Gradle, or Git history modifications)  
**Target Module:** Circuits List (`app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`)  
**Controlled Scope:** Presentation package `app/src/main/java/com/example/ui/screens/circuits/`  
**Document Revision:** `V1-R2` (Incorporates all mandatory plan amendments before Phase C1)  
**Baseline Anchor:** Commit `1c973ffd84e1f5d161bf23f54a3fb83356b3d7a3` (`HEAD`), Branch `integration/home-snapshot-2026-08-23`  
**Git Policy:** LOCAL COMMIT ONLY. NO push, pull, fetch, merge, or rebase.  

---

## 1. Verified Baseline and Source Evidence

Every binding, calculation output, and domain relationship claimed in this plan is backed by line-exact citations in the active codebase:

| Claimed Binding / Feature | Concrete Symbol / Repository Path | Exact Line Range | Evidence Notes |
|---|---|---|---|
| **Circuits Collection** | `UiState.Success.circuits`<br>[`app/src/main/java/com/example/ui/UiState.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/UiState.kt#L36) | `lines 35-36` | `val circuits: List<CircuitEntity>` passed directly into `CircuitsGridScreen.kt:44`. |
| **Sizing Results Map** | `UiState.Success.sizingResults`<br>[`app/src/main/java/com/example/ui/UiState.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/UiState.kt#L38) | `line 38` | `val sizingResults: Map<Int, SizingResult>` keyed by `circuit.id`. |
| **Short-Circuit Results Map** | `UiState.Success.shortCircuitResults`<br>[`app/src/main/java/com/example/ui/UiState.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/UiState.kt#L57) | `line 57` | `val shortCircuitResults: Map<Int, ShortCircuitResult>` keyed by `circuit.id`. |
| **3-Phase Fault Current ($I_{sc(3\Phi)}$)** | `ShortCircuitResult.iscKA`<br>[`app/src/main/java/com/example/engine/ShortCircuitEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/ShortCircuitEngine.kt#L13) | `line 13` | Symmetrical 3-phase short-circuit current in kA calculated via IEC 60909 impedance walk. |
| **Breaking Capacity ($I_{cu}$)** | `SizingResult.deviceBreakingKA`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L48) | `line 48` | Rated breaking capacity in kA matched from device catalog / engine default ($36\,\text{kA}$). |
| **Connected Apparent Load** | `CircuitEntity.loadKVA`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L21) | `line 21` | Persisted `Double` in kVA. Real zero (`0.0`) is valid persisted input and renders as `"0.0 kVA"`. |
| **Design Current ($I_b$)** | `SizingResult.Ib_A`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L28) | `line 28` | Deterministic load current in amperes calculated at lines 412–421. |
| **Breaker Rating & Type ($I_n$)** | `SizingResult.deviceSize_A`, `deviceType`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L29-L30) | `lines 29-30` | Standard rating in amperes and frame type (`MCB`, `MCCB`, `ACB`). |
| **Derated Ampacity ($I_z$)** | `SizingResult.capacity_derated_Iz_A`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L36) | `line 36` | Thermal capacity in amperes after IEC 60364-5-52 derating factors. |
| **Loading / Utilization %** | `SizingResult.utilizationPct`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L37) | `line 37` | Deterministic engine output calculated at line 700: `(ibA / izDeratedA) * 100.0`. Consumed directly without UI re-calculation. |
| **Cumulative Voltage Drop ($V_d\,\%$)** | `SizingResult.cumulativeVDpct`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L42) | `line 42` | Percentage voltage drop from supply origin calculated at line 650. |
| **Voltage Drop Limit** | `CircuitEntity.maxVDpct`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L41) | `line 41` | Stored as decimal ratio (`0.03` represents $3.0\%$). Proven by engine check at line 652: `(cumVdPct / 100.0) <= maxVDpct`. Display formatting: `(maxVDpct * 100).toInt() + "%"`. |
| **Cable Specification** | `CircuitEntity.cableType`, `SizingResult.cableSize_mm2`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L30) | `Entities.kt:30`, `CableSizingEngine.kt:34` | Cable insulation/sheath string and selected cross-section ($\text{mm}^2$). |
| **Cable Route Length** | `CircuitEntity.length_m`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L40) | `line 40` | Physical route length in meters. |
| **Source / Hierarchy Relationships** | `fedFrom`, `parentId`, `transformerId`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L22-L23) | `lines 22-23, 64` | `fedFrom` ("Transformer", "MDB-1", etc.), `parentId: Int?`, resolved via `CircuitChildLookup.kt:14`. Non-interactive metadata display. |
| **Panel Schedule Eligibility** | `panelType`, `CircuitChildLookup.hasChildren`<br>[`app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt#L228) | `CircuitsGridScreen.kt:39, 228` | `panelType in PANEL_TYPES || childrenLookup.hasChildren(circuitName)`. |
| **Metadata Tag Chip Component** | `DsInfoChip.kt`<br>[`app/src/main/java/com/example/ui/designsystem/components/DsInfoChip.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/designsystem/components/DsInfoChip.kt) | `lines 1-59` | Verified existing canonical component used for display-only metadata chips (`surfaceContainerHigh` + `DsShape.pill`). Non-interactive. |

---

## 2. Approved Data-Binding Matrix

| Visual Zone / Element | Mockup Target | Backing Data Source | Display Transformation Rule | Null / Missing Data Representation |
|---|---|---|---|---|
| **Zone 1: Title** | Circuit Name | `CircuitEntity.circuitName` | Exact string, bold (`DsType.titleMedium`), max 2 lines | `"—"` if empty |
| **Zone 1: Schedule Action** | `[Schedule]` Pill | `panelType in PANEL_TYPES \|\| hasChildren` | Renders button if eligible; otherwise hidden | Hidden if ineligible |
| **Zone 1: Cable Size** | `70 mm²` | `SizingResult.cableSize_mm2` | Accent primary string (`DsColor.primary`) | `"—"` if sizing missing |
| **Zone 1: Status Badge** | `PASS` / `FAIL` / `STANDBY` | `SizingResult.resultStatus` (typed enum) | `DsStatusBadge` (`DsStatusVariant.Pass` / `Fail` / `Neutral`) | `STANDBY` (`DsStatusVariant.Neutral`) |
| **Zone 2: Breadcrumb** | `Transformer > DP-G3` | `fedFrom`, `parentId`, `circuitName` | Non-clickable `DsInfoChip` / display text | `"—"` if unresolved |
| **Zone 3: Load** | `87.9 kVA` | `CircuitEntity.loadKVA` | `"${String.format(Locale.US, "%.1f", loadKVA)} kVA"` | `"0.0 kVA"` (Real zero preserved) |
| **Zone 3: Ib** | `126.9 A` | `SizingResult.Ib_A` | `"${String.format(Locale.US, "%.1f", Ib_A)} A"` | `"—"` |
| **Zone 3: In** | `160 A (MCCB)` | `deviceSize_A`, `deviceType` | `"${deviceSize_A.toInt()} A (${deviceType})"` | `"—"` |
| **Zone 4: Voltage Drop** | `2.40% Limit 5.00%` | `cumulativeVDpct`, `maxVDpct` | `"${String.format(Locale.US, "%.2f", cumulativeVDpct)}%"` with `"Limit ${(maxVDpct * 100).toInt()}%"` | `"—"` |
| **Zone 4: Loading (Iz)** | `64.6% Iz: 196.6 A` | `utilizationPct`, `capacity_derated_Iz_A` | `"${String.format(Locale.US, "%.1f", utilizationPct)}%"` with `"Iz: ${String.format(Locale.US, "%.1f", Iz)} A"` | `"—"` |
| **Zone 4: Isc (3Φ)** | `2.16 kA Icu: 36.0 kA` | `ShortCircuitResult.iscKA`, `deviceBreakingKA` | `"${String.format(Locale.US, "%.2f", iscKA)} kA"` with `"Icu: ${deviceBreakingKA.toInt()} kA"` | `"—"` for any missing subvalue |
| **Zone 5: Footer Spec** | `Cu/XLPE/PVC - 4C • 95.0 m`| `cableType`, `length_m` | `"${cableType} • Length: ${length_m.toInt()} m"` | `"—"` |
| **Zone 5: Actions** | Edit, Delete, AI Assistant | Existing callbacks & handlers | Preserves exact navigation / dialog triggers | N/A |

---

## 3. Explicit Missing-Data Matrix

| Metric / Field | Condition | Display Representation | Forbidden Behavior |
|---|---|---|---|
| **Sizing Result** | `sizingResults[id] == null` | Card Status: `STANDBY`<br>Metrics: `"—"` | **FORBIDDEN:** Rendering synthetic `0.0`, synthetic `PASS`, or synthetic `WARNING`. |
| **Short-Circuit Result** | `shortCircuitResults[id] == null` | $I_{sc(3\Phi)}$ subvalue shows `"—"`; $I_{cu}$ still renders if sizing exists | **FORBIDDEN:** Inventing default fault current values or suppressing available $I_{cu}$. |
| **1-Phase Branch Fault Current ($I_{sc(1\Phi)}$)** | All branch circuits | **DO NOT BUILD / OMITTED** | **FORBIDDEN:** Building 1-phase branch fault UI without engine data. |
| **Freshness Indicator** | All circuits | **DO NOT BUILD / OMITTED** | **FORBIDDEN:** Synthesizing freshness status from background timestamps. |
| **Active Demand ($P_{\text{kW}}$)** | All circuits | **OMITTED FROM LIST CARD** | **FORBIDDEN:** Adding presentation calculations that clutter the card. |
| **Max Voltage Drop (Summary)** | No valid sizing results exist | `"—"` | **FORBIDDEN:** Rendering `0.00%` or synthesizing a project limit. |
| **Invalid Double Values** | `NaN` or `Infinite` in engine outputs | Formatted as `"—"` | **FORBIDDEN:** Crashing string formatting or rendering `"NaN%"`. |

---

## 4. Typed Status Authority & Presentation Aggregation Boundaries

### 4.1 Authoritative Status Resolution (Single Typed Pipeline)
All status mapping in `CircuitUiModelMapper.kt` prefers the typed enum `SizingResult.resultStatus: SizingResultStatus` ([`CableSizingEngine.kt:8-14, 56`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L8-L14)):

```kotlin
enum class CircuitUiStatus {
    PASS,
    FAIL,
    STANDBY;

    fun toDsStatusVariant(): DsStatusVariant = when (this) {
        PASS -> DsStatusVariant.Pass
        FAIL -> DsStatusVariant.Fail
        STANDBY -> DsStatusVariant.Neutral
    }

    fun displayLabel(): String = when (this) {
        PASS -> "PASS"
        FAIL -> "FAIL"
        STANDBY -> "STANDBY"
    }
}

fun resolveCircuitStatus(sizingResult: SizingResult?): CircuitUiStatus {
    if (sizingResult == null) return CircuitUiStatus.STANDBY
    return when (sizingResult.resultStatus) {
        SizingResultStatus.PASS -> CircuitUiStatus.PASS
        SizingResultStatus.FAIL -> CircuitUiStatus.FAIL
        SizingResultStatus.INCOMPLETE,
        SizingResultStatus.ERROR -> CircuitUiStatus.STANDBY
        SizingResultStatus.WARNING -> CircuitUiStatus.STANDBY // WARNING not reachable from current list engine binding
        null -> when { // Legacy string fallback normalized once inside mapper
            sizingResult.status.contains("PASS", ignoreCase = true) -> CircuitUiStatus.PASS
            sizingResult.status.contains("FAIL", ignoreCase = true) -> CircuitUiStatus.FAIL
            else -> CircuitUiStatus.STANDBY
        }
    }
}
```

- **Strict Warning Constraint**: `WARNING` is not reachable from `CableSizingEngine.kt:715` (emits binary PASS/FAIL). It is mapped to `STANDBY` rather than synthesized.
- **Elimination of Scattered Checks**: `CircuitUiModelMapper` is the single source of truth; no `contains("PASS")` or `contains("FAIL")` checks appear in UI composables, filters, or sorting.

### 4.2 Presentation Aggregation Boundaries
- **Project Health Summary Scope**: Evaluated over the **whole-project collection** before search/filter/sort is applied, ensuring summary KPIs remain stable while navigating.
- **Visible List Count**: Evaluated after search/filter/sort as `CIRCUITS (${filteredCircuits.size})`.
- **Presentation Aggregations**: Computing list counts and selecting the maximum valid $V_d$ are presentation aggregations, not engineering calculations.

---

## 5. Filter and Sort Semantics with Stable Ordering

### 5.1 Filter Definitions
- **Search Query**: Evaluated in-memory across `circuitName`, `fedFrom`, `cableType`, `loadType`, and `overrideCableSize` (case-insensitive).
- **Level Filter**:
  - `All`: All circuits in active project.
  - `Main Feeders`: `fedFrom.equals("Transformer", ignoreCase = true) || parentId == null`.
  - `Downstream`: `!fedFrom.equals("Transformer", ignoreCase = true) && parentId != null`.
  - `Panels`: `panelType in PANEL_TYPES || childrenLookup.hasChildren(circuitName)`.
- **Status Filter**:
  - `All`: All circuits.
  - `Pass`: `resolveCircuitStatus(sizingResult) == CircuitUiStatus.PASS`.
  - `Fail`: `resolveCircuitStatus(sizingResult) == CircuitUiStatus.FAIL`.
  - `Standby`: `resolveCircuitStatus(sizingResult) == CircuitUiStatus.STANDBY`.
- **More Control**: **Deferred and omitted**. No dead, empty, or non-functional dropdown is rendered.

### 5.2 Deterministic Sort Ordering & Tie-Breakers
Sorting evaluates **raw typed fields** (never formatted strings). Every sort order enforces `circuit.id` as the final deterministic tie-breaker:
1. `DEFAULT` (Order): Preserves original database sequence (`state.circuits`).
2. `NAME_ASC`: `compareBy<CircuitEntity> { it.circuitName.lowercase() }.thenBy { it.id }`.
3. `LOAD_DESC`: `compareByDescending<CircuitEntity> { it.loadKVA }.thenBy { it.id }`.
4. `VD_DESC`: `sizingResults[it.id]?.cumulativeVDpct ?: -1.0` descending, then `thenBy { it.id }` (missing/standby values placed deterministically last).
5. `STATUS`: Priority `FAIL` (0), `PASS` (1), `STANDBY` (2), then `thenByDescending { it.loadKVA }.thenBy { it.id }`.

### 5.3 State Ownership Strategy
- **Search & Filters**: Consumes existing ViewModel StateFlows (`circuitSearchQuery`, `circuitStatusFilter`, `circuitParentFilter`).
- **Sort Order**: Managed as screen-local presentation state using `rememberSaveable { mutableStateOf(CircuitSortOrder.DEFAULT) }`. This survives Android configuration changes and process recreation while strictly adhering to the non-modification boundary of `EngineeringViewModel.kt`.

---

## 6. Card Interaction Architecture & Operation Preservation

### 6.1 Interaction Model
- **Card Body Tap**: Toggles selection / expansion state (`viewModel.selectedCircuitId.value = if (selected) null else circ.id`).
- **Schedule Pill Tap**: Triggers `onNavigateToPanelSchedule(circ.id)` (visible only when eligible).
- **Edit Action**: Opens `CircuitEditDialog` modal form (`showDialog = true, editingCircuit = circ`).
- **Delete Action**: Triggers cascade delete `AlertDialog` (`circuitToDelete = circ`).
- **Contextual AI Action**: Invokes `onOpenAssistant(AssistantEntryPoint.CONTEXTUAL_CIRCUIT, circ)`.
- **Expanded State Details**: Displays detailed parameters (failure codes, derating factors, neutral/earth sizes, and quick override controls).

### 6.2 Strict Preservation of Contracts
- All existing test tags (including `add_circuit_fab`, `search_circuits_input`, `confirm_delete_circuit_btn_*`) are preserved byte-for-byte.
- `MainLayout.kt` is **VERIFY ONLY** and will not be edited.

---

## 7. Adaptive Layout Strategy (320dp, 360dp, 412dp & Font Scale 1.5×)

### 7.1 Summary Strip Adaptation (NO Horizontal Scrolling)
- **$320\text{dp} - 359\text{dp}$**: Wrapped $3 + 2$ grid using `DsMetricTile(dense = true)`.
- **$360\text{dp} - 411\text{dp}$**: 5-cell horizontal row **only if measured content fits at font scale 1.0**; otherwise wraps $3 + 2$.
- **$\ge 412\text{dp}$**: Balanced 5-cell row.
- **Font Scale $\ge 1.30\times$**: Wraps adaptively ($3 + 2$ or stacked); **never horizontally scrolls** and never shrinks typography below its approved type role.

### 7.2 Metric Layout & Touch Targets
- Metric rows adapt: 2-column wrapped at compact widths, 3-column at standard widths.
- All interactive controls maintain $\ge 44\text{dp} \times 44\text{dp}$ touch target bounds (targeting Material 48dp where padding allows).

---

## 8. Shared Design System Protection & Golden Reconciliation

### 8.1 Transformers Screen Baseline Protection
- `TransformersScreen.kt` is 100% locked and protected.
- Zero modifications to existing default parameters of `DsCard`, `DsMetricTile`, `DsStatusBadge`, `DsSelectableChip`, or `DsInfoChip`.
- Screenshot comparisons for `TransformersScreen` must show **0 pixel drift** across both themes. Regeneration of Transformer goldens is **strictly forbidden**.

### 8.2 Authoritative Golden Paths
- **Committed Golden Location**: `app/src/test/resources/golden/step7/`
  - `Circuits_Search_Dark_360x800.png`
  - `Circuits_Search_Light_360x800.png`
  - `Transformer_Empty_Dark_360x800.png`
  - `Transformer_Empty_Light_360x800.png`
  - `Transformer_Selector_Light_360x800.png`
- **Roborazzi Test Output Directory**: `app/build/outputs/roborazzi/step7/`

---

## 9. Verification Commands & Baseline Counts

### 9.1 Commands
- **Compilation**: `.\gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin`
- **Targeted Unit Tests**: `.\gradlew.bat testDebugUnitTest --tests "com.example.ui.screens.circuits.*" --tests "com.example.engine.*"`
- **Full Test Suite**: `.\gradlew.bat testDebugUnitTest`

### 9.2 Baseline Numeric Results
- **Discovered Tests**: 545 tests.
- **Passed**: 541 tests (100% core engine, lookup, UI theme, navigation, and motion contracts).
- **Skipped**: 1 test.
- **Known Baseline Assertion Deltas**: 3 font rendering assertion differences in `Step7LightModeGoldenTest` on Windows due to platform rasterization deltas.

---

## 10. Screen-Local Presentation Package Scope

All new presentation data models and mappers are confined to a clean screen-local package:

- **Model File**: `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt`
- **Mapper File**: `app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt`
- **Test File**: `app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt`

These are pure presentation models with zero dependency on domain calculation logic.

---

## 11. Phase-by-Phase Execution Roadmap

```
Phase C0: Evidence Baseline & Approved Plan V1-R2 (Complete)
   │
   ▼ [STOP GATE: User Approval]
Phase C1: Screen-Local Presentation Contracts & Unit Tests
   │
   ▼ [STOP GATE: Test Verification Report]
Phase C2: Search, Filters, Sort & Non-Scrolling Summary Shell
   │
   ▼ [STOP GATE: Visual Proof Review at 320/360/412dp]
Phase C3: High-Density Circuit Card Presentation Expansion
   │
   ▼ [STOP GATE: Card Layout & Action Verification]
Phase C4: Edge States & Operations Validation (MainLayout is Verify-Only)
   │
   ▼ [STOP GATE: State & Flow Verification Report]
Phase C5: Accessibility, Responsive Layouts, Theme & Motion
   │
   ▼ [STOP GATE: Contrast & Accessibility Audit Report]
Phase C6a: Full Verification & Screenshot Evidence (NO Golden Writes)
   │
   ▼ [STOP GATE: Explicit Visual Approval]
Phase C6b: Golden Re-baselining & Local Commit (Circuits Goldens Only)
```

---

## 12. Confirmation of Strict Boundaries

- **Zero modifications** to: `MainLayout.kt`, `TransformersScreen.kt`, calculation engines, Room entities, schemas, repositories, migrations, `EngineeringViewModel.kt`, export flows, or shared design-system defaults.
- **Local commit only**: No git push, pull, fetch, merge, or rebase.

---

**STOP GATE ACTIVE — UI-CIRCUITS-REDESIGN-V1-R2 AWAITING APPROVAL**
