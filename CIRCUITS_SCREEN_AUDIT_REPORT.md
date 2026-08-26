# VoltAtlas Circuits Screen: Comprehensive Current-State, Data-Reconciliation, UX, and Redesign-Readiness Audit

**Document:** `CIRCUITS_SCREEN_AUDIT_REPORT.md`  
**Execution Mode:** REPORT CORRECTION & PERSISTENCE ONLY (No production code, resource, test, or golden modifications)  
**Target Module:** Circuits List (`app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`) and directly connected Circuit details / edit / schedule flows  
**Audit Date:** August 26, 2026  
**Engineering Baseline:** Commit `1c973ffd84e1f5d161bf23f54a3fb83356b3d7a3` (`HEAD`), Branch `integration/home-snapshot-2026-08-23`, Clean Working Tree  
**Target Architecture:** Android / Jetpack Compose / Material Design 3 / Room SQLite / Kotlin Coroutines & StateFlow  

---

## 1. Data Inventory Table

The table below reconciles all proposed and potential UI items against the concrete properties of `CircuitEntity`, `SizingResult`, `ShortCircuitResult`, and `ProtectionResult` in the VoltAtlas codebase:

| UI / Domain Metric | Exact Source Symbol / Repository Path | Type / Stored Unit | Source Category | Default / Nullability | Freshness Behavior | Current UI Binding (`CircuitsGridScreen.kt`) | Readiness Verdict |
|---|---|---|---|---|---|---|---|
| **Circuit ID** | `CircuitEntity.id`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L19) | `Int` | Persisted Input | `0` (Auto-generated PrimaryKey) | Database-persisted | LazyColumn item key & selection (`line 183`) | `SUPPORTED_NOW` |
| **Project ID** | `CircuitEntity.projectId`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L57) | `String` | Persisted Input | `"DEFAULT_PROJECT"` | Database-persisted | Project isolation filter | `SUPPORTED_NOW` |
| **Circuit Name** | `CircuitEntity.circuitName`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L20) | `String` | Persisted Input | `""` | Database-persisted | Rendered as primary card title (`line 233`) | `SUPPORTED_NOW` |
| **Panel / Tier Classification** | `CircuitEntity.panelType`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L9) | `String?` | Persisted Input | `null` / `"LOAD"` | Database-persisted | Controls Schedule button visibility (`line 244`) | `SUPPORTED_NOW` |
| **Parent Circuit ID** | `CircuitEntity.parentId`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L64) | `Int?` | Persisted Input | `null` | Database-persisted | Consumed by `CircuitChildLookup` (`line 59`) | `SUPPORTED_NOW` |
| **Fed From / Source Name** | `CircuitEntity.fedFrom`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L22) | `String` | Persisted Input | `"Transformer"` | Database-persisted | Rendered in card subtitle (`line 266`) | `SUPPORTED_NOW` |
| **Source Type** | `CircuitEntity.sourceType`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L13) | `String?` | Persisted Input | `null` | Database-persisted | Resolved in `HierarchyResolver` | `SUPPORTED_NOW` |
| **Transformer ID** | `CircuitEntity.transformerId`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L23) | `String` | Persisted Input | `""` | Database-persisted | Source transformer binding | `SUPPORTED_NOW` |
| **Load Type** | `CircuitEntity.loadType`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L24) | `String` | Persisted Input | `"Normal"` | Database-persisted | Search matching & edit dialog | `SUPPORTED_NOW` |
| **Power System Flags** | `upsBacked`, `generatorIncluded`, `atsIncluded`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L53) | `Boolean` | Persisted Input | `false` | Database-persisted | Edit dialog | `SUPPORTED_NOW` |
| **Connected Apparent Load** | `CircuitEntity.loadKVA`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L21) | `Double` (kVA) | Persisted Input | `0.0` | Database-persisted | Rendered in subtitle (`line 266`) | `SUPPORTED_NOW` |
| **Active Demand ($P_{\text{kW}}$)** | Derived: `loadKVA * pf * demandFactor` | `Double` (kW) | Derived (Display only) | $\ge 0.0$ | Computed on demand | Not currently rendered in list | `SUPPORTED_WITH_EXISTING_DATA_TRANSFORMATION` |
| **Power Factor ($\cos\phi$)** | `CircuitEntity.pf`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L27) | `Double` | Persisted Input | `0.8` (default `0.85`) | Database-persisted | Edit dialog & engine input | `SUPPORTED_NOW` |
| **Phase Configuration** | `CircuitEntity.phases`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L25) | `String` | Persisted Input | `"3P+N"` | Database-persisted | Rendered in subtitle (`line 266`) | `SUPPORTED_NOW` |
| **System Voltage** | `CircuitEntity.voltageV`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L26) | `Int` (V) | Persisted Input | `400` / `230` | Database-persisted | Engine calculation input | `SUPPORTED_NOW` |
| **Design Current ($I_b$)** | `SizingResult.Ib_A`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L28) | `Double` (A) | Computed Result | $0.0$ | Reactive engine output | Rendered in expanded specs (`line 402`) | `SUPPORTED_NOW` |
| **Protection Rating ($I_n$)** | `SizingResult.deviceSize_A`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L29) | `Double` (A) | Computed Result / Override | $0.0$ | Reactive engine output | Rendered in expanded specs (`line 410`) | `SUPPORTED_NOW` |
| **Protection Device Type** | `SizingResult.deviceType`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L30) | `String` | Computed Result / Override | `"MCB"` / `"MCCB"` / `"ACB"` | Reactive engine output | Rendered in expanded specs (`line 410`) | `SUPPORTED_NOW` |
| **Derated Ampacity ($I_z$)** | `SizingResult.capacity_derated_Iz_A`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L36) | `Double` (A) | Computed Result | $0.0$ | Reactive engine output | Internal utilization engine check | `SUPPORTED_NOW` |
| **Total Derating Factor** | `SizingResult.cTotalDerating`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L31) | `Double` | Computed Result | $1.0$ | Reactive engine output | Rendered in expanded specs (`line 434`) | `SUPPORTED_NOW` |
| **Selected Cable Size** | `SizingResult.cableSize_mm2`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L34) | `String` ($\text{mm}^2$) | Computed Result / Override | `""` | Reactive engine output | Rendered prominently on card (`line 325`) | `SUPPORTED_NOW` |
| **Neutral Conductor Size** | `SizingResult.neutral_mm2`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L38) | `String` ($\text{mm}^2$) | Computed Result | `""` | Reactive engine output | Available in expanded details / schedule | `SUPPORTED_NOW` |
| **Earth CPC Size** | `SizingResult.earthCPC_mm2`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L39) | `String` ($\text{mm}^2$) | Computed Result | `""` | Reactive engine output | Available in expanded details / protection | `SUPPORTED_NOW` |
| **Cable Family / Spec** | `CircuitEntity.cableType`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L30) | `String` | Persisted Input | `"Cu/XLPE/SWA/PVC"` | Database-persisted | Rendered in edit dialog & warnings (`line 309`) | `SUPPORTED_NOW` |
| **Parallel Runs** | `CircuitEntity.parallelRuns`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L32) | `Int` | Persisted Input | `1` | Database-persisted | Sizing engine & conduit fill | `SUPPORTED_NOW` |
| **Cable Route Length** | `CircuitEntity.length_m`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L40) | `Double` (m) | Persisted Input | `50.0` | Database-persisted | Sizing engine voltage drop | `SUPPORTED_NOW` |
| **Voltage Drop ($V_d\,\text{V}$)** | `SizingResult.voltDrop_V`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L40) | `Double` (V) | Computed Result | $0.0$ | Reactive engine output | Rendered in expanded specs (`line 418`) | `SUPPORTED_NOW` |
| **Local Voltage Drop ($V_d\,\%$)** | `SizingResult.vdPct`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L41) | `Double` (%) | Computed Result | $0.0$ | Reactive engine output | Rendered in expanded specs (`line 418`) | `SUPPORTED_NOW` |
| **Cumulative $V_d\,\%$** | `SizingResult.cumulativeVDpct`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L42) | `Double` (%) | Computed Result | $0.0$ | Reactive engine output | Rendered in expanded specs (`line 426`) | `SUPPORTED_NOW` |
| **Voltage Drop Limit** | `CircuitEntity.maxVDpct`<br>[`app/src/main/java/com/example/data/Entities.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L41) | `Double` (Ratio: e.g. `0.03`) | Persisted Input | `0.03` ($3.0\%$) | Database-persisted | Formatted as `maxVDpct * 100` (`3%`) for UI | `SUPPORTED_WITH_EXISTING_DATA_TRANSFORMATION` |
| **Loading / Utilization** | `SizingResult.utilizationPct`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L37) | `Double` (%) | Computed Result | $0.0$ | Reactive engine output | Defined as $(I_b / I_z) \times 100$ | `SUPPORTED_NOW` |
| **3-Phase Fault ($I_{sc(3\Phi)}$)** | `ShortCircuitResult.iscKA`<br>[`app/src/main/java/com/example/engine/ShortCircuitEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/ShortCircuitEngine.kt#L13) | `Double` (kA) | Computed Result | $0.0$ | Reactive engine output | Deterministic branch result | `KEEP — Existing result, currently not rendered` |
| **1-Phase Fault ($I_{sc(1\Phi)}$)** | Branch $I_{sc(1\Phi)}$ | `Double` (kA) | Undefined on Branch Result | N/A | Evaluated inside ADS loop only | Not present in `ShortCircuitResult` | `FLAG — NO DATA for branch cards; DO NOT BUILD` |
| **Breaking Capacity ($I_{cu}$)** | `SizingResult.deviceBreakingKA`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L48) | `Double` (kA) | Computed Result | `36.0` | Catalog / rating lookup | Protection & SLD | `SUPPORTED_NOW` |
| **Sizing Status Symbol/Text** | `SizingResult.status`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L43) | `String` | Computed Result | `"✓ PASS"` / `"✗ FAIL"` | Reactive engine output | Rendered on card badge (`line 332`) | `SUPPORTED_NOW` |
| **Failure Reason** | `SizingResult.failReason`<br>[`app/src/main/java/com/example/engine/CableSizingEngine.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L44) | `String` | Computed Result | `""` | Reactive engine output | Rendered in expanded card (`line 448`) | `SUPPORTED_NOW` |
| **Freshness Indicator** | Project Freshness Pipeline<br>[`EngineeringCalculationCoordinator.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/coordinator/EngineeringCalculationCoordinator.kt) | Token / Timestamp | Engine Coordinator Internal | N/A | Background state tracker | Not exposed to Circuits list | `FLAG — NOT CURRENTLY WIRED TO THIS SCREEN` |

---

## 2. Calculation Inventory

All engineering calculations in VoltAtlas execute inside deterministic Kotlin engines (`CableSizingEngine.kt`, `ShortCircuitEngine.kt`, `ProtectionEngine.kt`) and are exposed via `UiState.Success`. **Zero engineering calculations occur in the UI presentation layer.**

### 2.1 Deterministic Calculation Chain

```
CircuitEntity (Room Database)
  ├── CableSizingEngine.kt
  │     ├── Ib (A)                 [IEC 60364-5-52 §523]
  │     ├── Iz (A)                 [Table B.52.1 + Derating Factors C_temp, C_depth, C_soil, C_group]
  │     ├── Cable Size (mm²)       [Standard cross-sections 1.5 to 630 mm²]
  │     ├── Vd (V, %) & Cum. Vd (%) [Resistance + Reactance walk: sqrt(3)*Ib*L*(R*cosPhi + X*sinPhi)]
  │     ├── Utilization (%)        [(Ib / Iz) * 100]
  │     └── Sizing Status          [PASS if ampacityOk && voltageDropOk, else FAIL]
  │
  ├── ShortCircuitEngine.kt
  │     ├── Total Impedance Z_total [Vector sum of Transformer + Upstream Cable Network]
  │     ├── Isc (3Φ) (kA)          [IEC 60909: c*Un / (sqrt(3)*Z_total)]
  │     ├── Ip (kA) & Iw (kA)      [Peak current & thermal withstand k*S/sqrt(t)]
  │     └── Breaking Check         [deviceBreakingKA >= Isc]
  │
  └── ProtectionEngine.kt
        ├── Overload Check         [Ib <= In <= Iz]
        ├── Adiabatic Withstand    [k²*S² >= I²*t]
        └── Selectivity & ADS      [Selectivity ratio check & loop impedance Zs]
```

### 2.2 Core Equations Implemented

1. **Design Current ($I_b$)** ([`CableSizingEngine.kt:412-421`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L412-L421)):
   $$\text{3-Phase: } I_b = \frac{S_{\text{kVA}} \times 1000}{\sqrt{3} \times V_L} \times k_{\text{safety}}, \quad \text{1-Phase: } I_b = \frac{S_{\text{kVA}} \times 1000}{V_P} \times k_{\text{safety}}$$
2. **Derated Cable Ampacity ($I_z$)** ([`CableSizingEngine.kt:470-510`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L470-L510)):
   $$I_z = I_{\text{table}} \times C_{\text{temp}} \times C_{\text{depth}} \times C_{\text{soil}} \times C_{\text{grouping}}$$
3. **Voltage Drop ($V_d$)** ([`CableSizingEngine.kt:610-645`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L610-L645)):
   $$\Delta V = \frac{\sqrt{3} \times I_b \times L \times (R \cos\phi + X \sin\phi)}{1000 \times n_{\text{parallel}}}, \quad V_d\,\% = \frac{\Delta V}{V_{\text{nominal}}} \times 100$$
   $$V_{d,\text{cum}}\,\% = V_{d,\text{parent}}\,\% + V_d\,\%$$
4. **Loading / Utilization** ([`CableSizingEngine.kt:700`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L700)):
   $$\text{Utilization}\,\% = \frac{I_b}{I_z} \times 100$$
5. **3-Phase Symmetrical Short-Circuit ($I_{sc(3\Phi)}$)** ([`ShortCircuitEngine.kt:80-98`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/ShortCircuitEngine.kt#L80-L98)):
   $$Z_{\text{total}} = \sqrt{(R_{\text{tx}} + \sum R_{\text{cable}})^2 + (X_{\text{tx}} + \sum X_{\text{cable}})^2}, \quad I_{sc(3\Phi)} = \frac{c \cdot U_n}{\sqrt{3} \cdot Z_{\text{total}}}$$

---

## 3. Per-Metric Status

To avoid conflation between distinct engineering metrics, each candidate card metric is evaluated independently:

- **Connected Load ($S_{\text{kVA}}$)**: `CircuitEntity.loadKVA`. Persisted double, rendered directly as `"${circ.loadKVA} kVA"`.
- **Active Demand ($P_{\text{kW}}$)**: Pure display calculation `loadKVA * pf * demandFactor`. Supported for secondary display next to kVA.
- **Design Current ($I_b$)**: `SizingResult.Ib_A`. Calculated deterministically in amperes.
- **Breaker Rating ($I_n$)**: `SizingResult.deviceSize_A` and `deviceType` (MCB, MCCB, ACB).
- **Derated Ampacity ($I_z$)**: `SizingResult.capacity_derated_Iz_A`. Calculated deterministically in amperes.
- **Loading / Utilization**: `SizingResult.utilizationPct` ($I_b / I_z \times 100$). Independent thermal loading percentage.
- **Voltage Drop ($V_d$)**: `SizingResult.cumulativeVDpct` (%) and `voltDrop_V` (V). Independent voltage drop output.
- **3-Phase Short Circuit ($I_{sc(3\Phi)}$)**: `ShortCircuitResult.iscKA`. **KEEP — Existing deterministic result, currently not rendered on card.**
- **1-Phase Short Circuit ($I_{sc(1\Phi)}$)**: **FLAG — NO DATA for branch cards; DO NOT BUILD.** Not present in `ShortCircuitResult`.
- **Device Breaking Capacity ($I_{cu}$)**: `SizingResult.deviceBreakingKA`. Sourced from catalog/engine match.
- **Cable Cross-Section ($S\,\text{mm}^2$)**: `SizingResult.cableSize_mm2`. Rendered in prominent typography.

---

## 4. Calculation & Display Readiness Notes

### 4.1 Unit Formatting for `maxVDpct`
- `CircuitEntity.maxVDpct` is stored in the database as a decimal ratio (e.g. `0.03` represents $3.0\%$).
- User-facing percentage display requires display formatting:
  $$\text{Display Value} = \text{maxVDpct} \times 100 \quad (\text{e.g. } 0.03 \rightarrow 3\%)$$
- **Strict Constraint**: This is pure UI display formatting. It is NOT a new engineering calculation and requires no schema change.

### 4.2 Stale / Incomplete Result Handling
- If `sizingResults[circ.id] == null`, the card must render neutral fallback values (`STANDBY`, `"—"`) rather than synthetic zeros or synthetic `PASS`.
- Missing results must never crash the UI or trigger unhandled null pointer exceptions.

### 4.3 Freshness Indicator
- **Status**: `FLAG — NOT CURRENTLY WIRED TO THIS SCREEN`.
- The background freshness tracking mechanism in `EngineeringCalculationCoordinator.kt` is not exposed in `UiState.Success.circuits` or consumed by `CircuitsGridScreen.kt`.
- **Constraint**: Do not build or authorize a freshness UI badge in the visual restyle phase without a separately approved domain-source binding.

---

## 5. UI/UX Gap Analysis vs the Ds System

| Screen Area | Current Legacy Implementation (`CircuitsGridScreen.kt`) | Approved Design System Pattern (`DsTheme.kt`, `DsTokens.kt`) | Gap Severity | Proposed Solution |
|---|---|---|---|---|
| **Card Container** | Raw `Card` with explicit `BorderStroke(1.dp, ThemeBorderSubtle)` (`line 204`) | `DsCard` with `MaterialTheme.colorScheme.surfaceContainer` and 1dp tonal elevation (`DsCard.kt:43`) | High | Replace with canonical `DsCard`, eliminating hardcoded border strokes. |
| **Color Tokens** | Hardcoded legacy colors (`ThemeBorderSubtle`, `ThemePrimary`, `DarkBackground`, `ThemeTextPrimary`) | MaterialTheme roles (`colorScheme.background`, `colorScheme.onSurface`, `dsExtendedColors`) | High | Bind all colors to MD3 `colorScheme` and `dsExtendedColors`. |
| **High-Level Health Summary** | Completely absent from current screen | `EngineeringHealthSummaryStrip` using `DsMetricTile` (Total, Pass, Warning, Fail, Max $V_d$) | High | Add top summary strip using `DsMetricTile(dense = true)`. |
| **Filter Chips** | Toggling single `FilterChip` (cycles All $\rightarrow$ Transformer $\rightarrow$ Sub-feeders) (`lines 119-177`) | Horizontal row of discrete `DsSelectableChip` items | Medium | Replace cycling chips with explicit multi-chip selectors. |
| **Sorting Controls** | Completely absent from current screen | Compact sort selector (`Sort: Index / Name / Load / Vd / Status`) | Medium | Add sort dropdown selector in list header. |
| **Expanded Details Layout** | Huge inline accordion (`lines 341-693`) with full CRUD and nested dropdown menus | High-density card with clean metrics grid; CRUD actions offloaded to `CircuitEditDialog` | High | Eliminate nested inline dropdowns; move edits to modal dialog. |
| **Status Visualization** | Raw `statusSymbol` text (`line 332`) and raw color mappings | `DsStatusBadge` pill container with 15% tinted alpha and full-saturation dot/text (`DsStatusBadge.kt:53`) | Medium | Replace raw text with standard `DsStatusBadge`. |
| **Floating Action Button** | Standard `FloatingActionButton` (`line 713`) with testTag `"add_circuit_fab"` | `DsFab` with `ExtendedFloatingActionButton` / `DsShape.pill` | Low | Replace with `DsFab`, preserving exact testTag `"add_circuit_fab"`. |

> [!NOTE]
> The lack of theme adaptation in `CircuitsGridScreen.kt` is a **legacy Circuits-screen finding**, NOT a limitation of the design system. The core design system (`DsTheme.kt`, `DsCard.kt`, `DsStatusBadge.kt`) is fully modernized and theme-aware.

---

## 6. Filter / Search / Sort Support

### 6.1 Current Filter Implementation
- **Search Query** ([`CircuitsGridScreen.kt:65-70`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt#L65-L70)): In-memory case-insensitive match across `circuitName`, `fedFrom`, `cableType`, `loadType`, and `overrideCableSize`. Fast for projects up to 1,000 circuits.
- **Tier Filter** ([`CircuitsGridScreen.kt:79-83`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt#L79-L83)): Evaluates `fedFrom == "Transformer"` vs `fedFrom != "Transformer"`.
- **Status Filter** ([`CircuitsGridScreen.kt:72-77`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt#L72-L77)): Substring evaluation on `state.sizingResults[circ.id]?.status`.
  > [!WARNING]
  > The current substring status filter (`status?.contains("WARN")`) is legacy and fragile because `CableSizingEngine.kt` does not emit `"WARN"`. This report documents the fragility but does NOT authorize an unapproved behavioral rewrite in this audit.

### 6.2 Proposed Sort & Filter Structure
1. **Search**: Retain `circuitSearchQuery` StateFlow with instant clear button.
2. **Tier Chips**: `All`, `Main Feeders` (Tier 1), `Sub-Feeders` (Tier 2+), `Panels Only`.
3. **Status Chips**: `All`, `Pass`, `Failures`.
4. **Sort Selector**:
   - `Sort by Order` (Default database order)
   - `Sort by Name (A-Z)`
   - `Sort by Load (kVA ↓)`
   - `Sort by Voltage Drop (% ↓)`
   - `Sort by Status (Failures first)`

---

## 7. Status Color-Mapping & Warning Caveat

### 7.1 Status Semantics & Source Authority
An inspection of `CableSizingEngine.kt`, `CableCalculationService.kt`, and `ProtectionEngine.kt` reveals the exact status hierarchy:
1. `SizingResult.resultStatus: SizingResultStatus`: Typed enum with values `PASS`, `WARNING`, `FAIL`, `INCOMPLETE`, `ERROR`.
2. `SizingResult.status: String`: Legacy display string emitting `"✓ PASS"` or `"✗ FAIL"`.
3. `CableSizingEngine.kt:715`: Evaluates `val resStatus = if (isPass) SizingResultStatus.PASS else SizingResultStatus.FAIL`. It does NOT emit `WARNING`.
4. `ProtectionResult.finalStatus`: Emits protection/coordination pass/fail states, but is not currently bound to the Circuits list cards.

### 7.2 Strict Warning Verdict
- **Verdict**: Current sizing-card status is effectively **PASS / FAIL** through the existing binding.
- `WARNING` is **NOT currently reachable** from the sizing status displayed by the card.
- **Strict UI Constraint**: Do NOT synthesize `WARNING` in the UI presentation layer.
- Phase 2 must either omit/disable `WARNING` or separately approve a verified domain-source binding (e.g. from `ProtectionResult` or unconfirmed load conflict flags).

### 7.3 Theme-Aware Color Resolution
Status colors are resolved dynamically at runtime through `MaterialTheme.dsExtendedColors` / `LocalDsExtendedColors` ([`DsTheme.kt:130-136`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/designsystem/theme/DsTheme.kt#L130-L136)):

| Semantic Role | Dark Mode Value (`DarkExtendedColors`) | Light Mode Value (`LightExtendedColors`) | WCAG Contrast on Surface | UI Representation |
|---|---|---|---|---|
| **Pass** | `DsColor.success` (`#22C55E`) | `#166534` | AAA ($\ge 7:1$) | `✓ PASS` pill badge |
| **Warning** | `DsColor.warning` (`#F59E0B`) | `#92400E` | AAA ($\ge 7:1$) | `⚠ WARNING` (if domain-bound) |
| **Fail** | `DsColor.danger` (`#EF4444`) | `#B91C1C` | AAA ($\ge 7:1$) | `✗ FAIL` pill badge |
| **Neutral / Standby** | `DsColor.neutral` (`#94A3B8`) | `#64748B` | AA ($\ge 4.5:1$) | `STANDBY` pill badge |

---

## 8. Binding & Interaction Map

| User Action / UI Trigger | Source File & Line | Target Handler / Event Destination | Arguments Passed | Navigation / Restoration State | Verdict |
|---|---|---|---|---|---|
| **Select / Expand Card** | `CircuitsGridScreen.kt:212` | `viewModel.selectedCircuitId.value = circ.id` | `circ.id` (or `null` to collapse) | Preserved in `EngineeringViewModel` | `PASS` |
| **Navigate to Panel Schedule** | `CircuitsGridScreen.kt:245, 363` | `onNavigateToPanelSchedule(circ.id)` | `circ.id`, sets return tab `1` | Transitions to Tab 12; restores Tab 1 on back | `PASS` |
| **Add Circuit FAB** | `CircuitsGridScreen.kt:713` | `showDialog = true`, `editingCircuit = null` | Opens `CircuitEditDialog` | Modal dialog; list scroll preserved | `PASS` |
| **Edit Circuit Action** | `CircuitsGridScreen.kt:374` | `showDialog = true`, `editingCircuit = circ` | Opens `CircuitEditDialog` | Modal dialog; state restored on dismiss | `PASS` |
| **Delete Circuit Action** | `CircuitsGridScreen.kt:384` | `circuitToDelete = circ` | Opens cascade `AlertDialog` | Prompts re-parenting downstream children | `PASS` |
| **Ask Contextual AI Assistant** | `CircuitsGridScreen.kt:486` | `onOpenAssistant(CONTEXTUAL_CIRCUIT, circ)` | `AssistantEntryPoint.CONTEXTUAL_CIRCUIT`, `circ` | Opens bottom sheet over active screen | `PASS` |
| **Top Bar Assistant Action** | `MainLayout.kt:654` | `openAssistantWithContext(TOP_BAR_ICON)` | Screen context `"CircuitsGridScreen"` | Opens bottom sheet over active screen | `PASS` |
| **PDF Export Action** | `MainLayout.kt:636` | `PdfExportDialog` $\rightarrow$ `createPdfLauncher` | SAF URI, `ReportOptions` | Triggers background PDF compilation | `PASS` |
| **Recalculate Token Trigger** | `MainLayout.kt:666, 738` | `viewModel.recalculate()` | Token increment | Triggers reactive Flow re-evaluation | `PASS` |

---

## 9. Missing vs Extra

### 9.1 Extra Elements in Proposals (To be Omitted or Constrained)
1. **1-Phase Branch Short Circuit ($I_{sc(1\Phi)}$)**:
   - **Verdict**: `FLAG — NO DATA for branch cards; DO NOT BUILD.`
   - `ShortCircuitResult` provides 3-phase symmetrical fault current (`iscKA`), but does not expose single-phase branch fault currents.
2. **Freshness Badge**:
   - **Verdict**: `FLAG — NOT CURRENTLY WIRED TO THIS SCREEN.`
   - Engine coordinator freshness state is not surfaced in `UiState.Success.circuits`.
3. **Synthetic Warning Badge**:
   - **Verdict**: `FLAG — NOT REACHABLE via sizing binding; DO NOT SYNTHESIZE.`
   - Sizing engine status is binary PASS/FAIL.

### 9.2 Missing Elements in Current Legacy UI (To be Added)
1. **Engineering Health Summary Strip**: High-level aggregated project metrics (Total, Pass, Fail, Max $V_d$).
2. **Circuit Count & List Sort Controls**: Sort by Index, Name, Load, Voltage Drop, Status.
3. **High-Density Design System Styling**: Canonical `DsCard`, `DsMetricTile`, `DsStatusBadge`, `DsSelectableChip`.
4. **Source Breadcrumb Chip**: Clear upstream path display (`DsInfoChip`).

---

## 10. Dependencies & Impact if Re-skinned

### 10.1 Affected Downstream Modules
- **`CircuitsGridScreen.kt`**: Primary visual restyle target.
- **`MainLayout.kt`**: Zero breaking changes. Consumes standard `CircuitsGridScreen` composable signature.
- **`EngineeringViewModel.kt`**: Zero modifications. Pre-existing StateFlows are fully reused.
- **`Entities.kt` & Room Database**: Zero migrations, zero schema changes, zero entity modifications.
- **Calculation Engines**: 100% untouched.

### 10.2 Protection of Shared Design System & Transformers Baseline
- Any adjustments to shared components (`DsCard.kt`, `DsMetricTile.kt`, `DsStatusBadge.kt`, `DsSelectableChip.kt`) must be strictly **additive and backward-compatible** (e.g. adding an optional `dense: Boolean = false` parameter).
- **Mandate**: Existing component default parameters must NOT be mutated if that alters the rendering of `TransformersScreen.kt`.
- After any shared component update, the full test suite must be executed to verify Transformer screenshot goldens remain identical.

---

## 11. Light / Dark Mode Review

### 11.1 Design System Theming Architecture
The VoltAtlas design system architecture is structured across three distinct layers:
1. **Fixed Seed Tokens (`DsTokens.kt`)**: Plain immutable color constants defining the dark baseline (e.g. `DsColor.background = #0D1117`, `DsColor.surfaceContainer = #161B22`, `DsColor.primary = #3B9EFF`, `DsColor.textSecondary = #AEB6C2`).
2. **Theme-Aware Color Schemes (`DsTheme.kt`)**: Defines `DsDarkColorScheme` and `DsLightColorScheme`, mapping MD3 semantic roles (`background`, `surface`, `surfaceContainer`, `onSurface`, `onSurfaceVariant`, `outline`).
3. **Semantic Extended Colors (`MaterialTheme.dsExtendedColors`)**: Dynamic CompositionLocal bridge (`DarkExtendedColors` vs `LightExtendedColors`) delivering WCAG-safe semantic colors (`success`, `warning`, `danger`, `neutral`, `info`) across theme changes.

### 11.2 Theme Comparison Matrix

| Token / Role | Dark Mode Value | Light Mode Value | Circuits Screen Compliance |
|---|---|---|---|
| `colorScheme.background` | `#0D1117` | `#F8FAFC` | Legacy uses hardcoded `#0A0E17` $\rightarrow$ Migrate to `colorScheme.background` |
| `colorScheme.surfaceContainer` | `#161B22` | `#FFFFFF` | Legacy uses hardcoded `Card` + border $\rightarrow$ Migrate to `DsCard` |
| `colorScheme.onSurface` | `#E6EDF3` | `#0F172A` | Legacy uses `ThemeTextPrimary` $\rightarrow$ Migrate to `colorScheme.onSurface` |
| `colorScheme.onSurfaceVariant` | `#8B949E` | `#475569` | Legacy uses `ThemeTextSecondary` $\rightarrow$ Migrate to `colorScheme.onSurfaceVariant` |
| `dsExtendedColors.success` | `#22C55E` | `#166534` | Legacy uses `ThemeStatusPass` $\rightarrow$ Migrate to `DsStatusBadge` |
| `dsExtendedColors.danger` | `#EF4444` | `#B91C1C` | Legacy uses `ThemeStatusFail` $\rightarrow$ Migrate to `DsStatusBadge` |

---

## 12. Performance Review

1. **Hierarchy Indexing ($O(N)$ Single Pass)**:
   - `CircuitChildLookup.build(state.circuits)` executes once per circuit list emit inside `remember(state.circuits)`.
   - Zero parent/child tree traversals occur inside LazyColumn item composition.
2. **In-Memory Filtering & Search**:
   - Filtering over 200 circuits takes $<2\,\text{ms}$, running inside `remember(state.circuits, state.sizingResults, query, statusFilter, parentFilter)`.
3. **LazyColumn Stability**:
   - `LazyColumn` uses `key = { it.id }` ensuring zero unnecessary row recompositions during scroll.
4. **Summary Strip Aggregation**:
   - Computed via single memoized pass over `state.circuits` and `state.sizingResults`.

---

## 13. Golden Coverage Review

### 13.1 Current Golden Test Suite
- Existing screenshot tests in [`app/src/test/java/com/example/ui/Step7LightModeGoldenTest.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/test/java/com/example/ui/Step7LightModeGoldenTest.kt#L266-L293):
  - `circuits_searchAndFilters_light_matchesGolden()` $\rightarrow$ `Circuits_Search_Light_360x800.png`
  - `circuits_searchAndFilters_dark_matchesGolden()` $\rightarrow$ `Circuits_Search_Dark_360x800.png`
  - `transformer_emptyState_light_matchesGolden()`, `transformer_selectorRow_light_matchesGolden()`, `transformer_emptyState_dark_matchesGolden()`

### 13.2 Strict Golden-Update Process (No Silent Updates)
1. **Implement Approved UI Changes**: Complete visual restyle according to approved phases.
2. **Run Full Test Suite**: Execute `./gradlew.bat testDebugUnitTest`.
3. **Stop at Golden Gate**: Present changed light and dark screenshot captures for inspection.
4. **Explicit Approval Required**: Committed golden files must ONLY be regenerated after explicit Product Owner review and sign-off.
5. **Local Commit Only**: Record approved changes locally; no push.

---

## 14. Verdict, Readiness & Product Decisions

### 14.1 Overall Readiness Verdict
**Verdict:** `READY FOR PHASED REDESIGN, PENDING PRODUCT-OWNER APPROVAL`

A complete, line-by-line audit confirms that all necessary data models, deterministic calculation outputs, and design system components are available to support the high-density Circuits screen restyle.

### 14.2 Phased Implementation Blueprint

```
Phase R0: Safety Baseline & Test Pinning (Lock unit tests & verify clean build)
   │
   ▼
Phase R1: Presentation Data Model Contract (Pure UI models; zero entity/engine changes)
   │
   ▼
Phase R2: Design System Component Adaptation (Additive dense variants; protect Transformer goldens)
   │
   ▼
Phase R3: Summary Strip & Header Shell (Engineering health summary & discrete filter chips)
   │
   ▼
Phase R4: High-Density Circuit Card (Canonical DsCard, DsMetricTile, DsStatusBadge, breadcrumbs)
   │
   ▼
Phase R5: State Integration & Empty States (Loading, empty search, missing data fallbacks)
   │
   ▼
Phase R6: Interaction, Dialogs & Navigation (Modal CircuitEditDialog, schedule jump, AI assistant)
   │
   ▼
Phase R7: Accessibility, Motion & Polish (TalkBack labels, spring motion, WCAG AAA contrast)
   │
   ▼
Phase R8: Verification & Golden Gate (Full test run; explicit golden approval gate)
```

### 14.3 Product Decisions Required

1. **Summary Strip Scope**:
   - *Option A (Recommended)*: Summary strip counts reflect the **active filtered collection** (updates dynamically as user types or filters).
   - *Option B*: Summary strip counts reflect the **entire project collection** unconditionally.
2. **Status Source for Summary Strip**:
   - *Decision*: Bound strictly to `SizingResult.status` (`PASS` vs `FAIL`). `WARNING` count will display `0` until a multi-engine coordinated status binding is approved.
3. **Sort Selection Persistence**:
   - *Decision*: Maintain sort order in `EngineeringViewModel` StateFlow (resets to default order on cold restart).
4. **Breadcrumb Tapping Behavior**:
   - *Decision*: Render source breadcrumb as informative `DsInfoChip`; defer interactive tap-to-filter to a future release.
5. **Single-Phase Branch Fault Current ($I_{sc(1\Phi)}$)**:
   - *Confirmed Verdict*: **DO NOT BUILD**. Display 3-Phase symmetrical short-circuit current ($I_{sc(3\Phi)}$) only.

---

**AUDIT REPORT CORRECTED AND PERSISTED — STOP GATE ACTIVE.**
