# VoltAtlas Circuits Screen Redesign Implementation Plan (UI-CIRCUITS-REDESIGN-V1-R3)

**Document Identifier:** `CIRCUITS_SCREEN_IMPLEMENTATION_PLAN_V1_R3.md`  
**Execution Mode:** C0 APPROVED + PHASE C1 AUTHORIZED  
**Target Module:** Circuits List (`app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`)  
**Controlled Presentation Scope:** `app/src/main/java/com/example/ui/screens/circuits/`  
**Revision:** `V1-R3` (Authoritative single source of truth)  
**Git Policy:** LOCAL COMMIT ONLY. NO push, pull, fetch, merge, or rebase.  

---

## 1. Verified Baseline and Git History Disambiguation

### 1.1 Current Repository State
- **Current HEAD at Plan Verification:** `7ddb0b7` (`docs(audit): persist Circuits audit report and redesign implementation plan V1-R2`)
- **Audited Implementation Base Commit:** `1c973ffd84e1f5d161bf23f54a3fb83356b3d7a3` (`docs(audit): add comprehensive current-state and redesign-readiness audit for Circuits screen`)
- **Documentation Commits on Top of Base:** `7ddb0b7` (Persisted audit document and previous plan revision)
- **Current Branch:** `integration/home-snapshot-2026-08-23`
- **Working Tree State:** Clean baseline.

### 1.2 Recent Git Log (Last 8 Commits)
```
7ddb0b7 docs(audit): persist Circuits audit report and redesign implementation plan V1-R2
1c973ff docs(audit): add comprehensive current-state and redesign-readiness audit for Circuits screen
f5f6b11 feat(sld): convert PNG and DXF exports to SAF with dual Save and Share options
e2b669a feat(ui): TransformersScreen V3 visual polish, compact chips, density tuning (UI-TX-V3)
e53185d feat(ui): TransformersScreen V2 corrections on theme-aware foundation (UI-TX-V2)
ad15851 fix(designsystem): make Ds components and TransformersScreen theme-aware (UI-L1)
f76955f feat(ui): TransformersScreen re-skin #01 Phase 2 - Ds design system (UI-TX-P2)
eb31ad1 feat(designsystem): Option-B multi-color treatment for DsBottomNavBar (UI-2)
```

---

## 2. Authoritative Plan Rule & Precedence

- **Canonical Specification:** `CIRCUITS_SCREEN_IMPLEMENTATION_PLAN_V1_R3.md` is the **single authoritative plan** for `UI-CIRCUITS-REDESIGN-V1`.
- **Precedence:** In the event of any contradiction with earlier audit drafts or scratch documents, `CIRCUITS_SCREEN_IMPLEMENTATION_PLAN_V1_R3.md` strictly supersedes.
- **Pointer File:** `implementation_plan.md` in the system artifact directory serves only as a lightweight pointer to this canonical file.

---

## 3. Reconciled Test Execution Baselines & Golden Diagnosis

### 3.1 Test Profile Configuration in Gradle
An inspection of [`app/build.gradle.kts:194-240`](file:///e:/voltatlas-v.1%20(4)/app/build.gradle.kts#L194-L240) defines the test execution profiles:
- **Default Fast Profile (`-Pprofile=fast` or omitting `-Pprofile`):** Filters out `com.example.ai.*`, heavy preview/screenshot suites, and stress tests to accelerate local development loops (545 tests).
- **Full Profile (`-Pprofile=full` or `-Pprofile=all`):** Executes all 1,408 unit, AI, stress, and integration tests across the repository without exclusions.

### 3.2 Full & Fast Execution Results Table

| Execution Target | Exact Command Line Executed | Discovered | Passed | Skipped | Failed | Errors | Exit Code | Status |
|---|---|---|---|---|---|---|---|---|
| **Compilation** | `.\gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin --offline` | N/A | N/A | N/A | N/A | N/A | `0` | Green |
| **Full Profile Baseline** | `.\gradlew.bat testDebugUnitTest -Pprofile=full --offline` | 1408 | 1392 | 13 | 3 | 0 | `1` | Exception (3 Transformer deltas) |
| **Fast Profile Baseline** | `.\gradlew.bat testDebugUnitTest -Pprofile=fast --offline` | 545 | 541 | 1 | 3 | 0 | `1` | Exception (3 Transformer deltas) |
| **Engine & Lookup Core** | `.\gradlew.bat testDebugUnitTest --tests "com.example.engine.*" --tests "com.example.hierarchy.*" --tests "com.example.ui.screens.CircuitChildLookupTest" --offline` | 34 | 34 | 0 | 0 | 0 | `0` | Green (100% Pass) |
| **UI Shell & Theme Core** | `.\gradlew.bat testDebugUnitTest --tests "com.example.ui.AppShellNavigationTest" --tests "com.example.ui.DashboardScreenTest" --tests "com.example.ui.theme.*" --tests "com.example.ui.components.*" --offline` | 15 | 15 | 0 | 0 | 0 | `0` | Green (100% Pass) |
| **Circuits Golden Baseline** | `.\gradlew.bat testDebugUnitTest --tests "com.example.ui.Step7LightModeGoldenTest.circuits_*" --offline` | 2 | 2 | 0 | 0 | 0 | `0` | Green (100% Pass) |
| **Transformer Golden Baseline** | `.\gradlew.bat testDebugUnitTest --tests "com.example.ui.Step7LightModeGoldenTest.transformer_*" --offline` | 3 | 0 | 0 | 3 | 0 | `1` | Locked Baseline Exception |

### 3.3 Golden Diagnosis & Authoritative Paths
- **Committed Golden Source Path:** `app/src/test/resources/golden/step7/`
- **Roborazzi Comparison Output Path:** `app/build/outputs/roborazzi/step7/`
- **Diagnosis of Failing Transformer Goldens:**

| Failing Test Name | Committed Golden File | Differing Pixels / Total | Differing % | Threshold | Visual Inspection & Root Cause |
|---|---|---|---|---|---|
| `transformer_selectorRow_light_matchesGolden` | `Transformer_Selector_Light_360x800.png` | 1,071,238 / 1,152,000 | **92.9894%** | $\le 0.1\%$ | Modernized MD3 `surfaceContainer` card layout in `f76955f..e2b669a` differs across viewport from legacy Step 7 golden. |
| `transformer_emptyState_light_matchesGolden` | `Transformer_Empty_Light_360x800.png` | 1,064,416 / 1,152,000 | **92.3972%** | $\le 0.1\%$ | Modernized MD3 empty-state card layout vs legacy Step 7 golden. |
| `transformer_emptyState_dark_matchesGolden` | `Transformer_Empty_Dark_360x800.png` | 1,065,272 / 1,152,000 | **92.4715%** | $\le 0.1\%$ | Modernized MD3 empty-state card layout vs legacy Step 7 golden. |

- **Official Classification:** **PRE-EXISTING STALE TRANSFORMER GOLDEN BASELINE — APPROVED REDESIGN NOT YET RE-BASELINED; LOCKED DURING CIRCUITS WORK.**
- **Circuits Goldens:** `Circuits_Search_Dark_360x800.png` and `Circuits_Search_Light_360x800.png` are **100% green** (0 pixel diff).
- **Strict Rule:** Transformer goldens are **locked** and will not be regenerated during Circuits work.

---

## 4. Source Evidence & Status Authority

### 4.1 Verified Current Domain Properties `[CURRENT VERIFIED]`

| Metric / Property | Current Concrete Source | Exact Line | Verified Type & Semantics |
|---|---|---|---|
| **Circuits Collection** | `UiState.Success.circuits` | [`UiState.kt:36`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/UiState.kt#L36) | `List<CircuitEntity>` |
| **Sizing Results Map** | `UiState.Success.sizingResults` | [`UiState.kt:38`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/UiState.kt#L38) | `Map<Int, SizingResult>` |
| **Short-Circuit Results Map** | `UiState.Success.shortCircuitResults` | [`UiState.kt:57`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/UiState.kt#L57) | `Map<Int, ShortCircuitResult>` |
| **Typed Sizing Status** | `SizingResult.resultStatus` | [`CableSizingEngine.kt:56`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L56) | Non-null `SizingResultStatus` enum (`PASS`, `WARNING`, `FAIL`, `INCOMPLETE`, `ERROR`). |
| **Legacy Sizing Status** | `SizingResult.status` | [`CableSizingEngine.kt:43`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L43) | `String` (`"✓ PASS"` / `"✗ FAIL"`). |
| **3-Phase Short Circuit ($I_{sc(3\Phi)}$)** | `ShortCircuitResult.iscKA` | [`ShortCircuitEngine.kt:13`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/ShortCircuitEngine.kt#L13) | `Double` (kA symmetrical fault current). |
| **Breaker Breaking Capacity ($I_{cu}$)** | `SizingResult.deviceBreakingKA` | [`CableSizingEngine.kt:48`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L48) | `Double` (kA catalog / engine breaking capacity). |
| **Load (kVA)** | `CircuitEntity.loadKVA` | [`Entities.kt:21`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L21) | `Double` (kVA). Real zero `0.0` is valid persisted input. |
| **Design Current ($I_b$)** | `SizingResult.Ib_A` | [`CableSizingEngine.kt:28`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L28) | `Double` (A). |
| **Breaker Size & Type ($I_n$)** | `deviceSize_A`, `deviceType` | [`CableSizingEngine.kt:29-30`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L29-L30) | `Double` (A), `String` (`MCB`/`MCCB`/`ACB`). |
| **Loading / Utilization %** | `SizingResult.utilizationPct` | [`CableSizingEngine.kt:37`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L37) | `Double` (%) calculated at line 700. |
| **Cumulative Voltage Drop ($V_d\,\%$)** | `SizingResult.cumulativeVDpct` | [`CableSizingEngine.kt:42`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/engine/CableSizingEngine.kt#L42) | `Double` (%). |
| **Voltage Drop Limit** | `CircuitEntity.maxVDpct` | [`Entities.kt:41`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L41) | Stored decimal ratio (`0.03` represents $3.0\%$). |
| **Cable Specification** | `CircuitEntity.cableType` | [`Entities.kt:30`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L30) | `String` (e.g. `"Cu/XLPE/SWA/PVC"`). |
| **Cable Route Length** | `CircuitEntity.length_m` | [`Entities.kt:40`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L40) | `Double` (m). |
| **Hierarchy & Sources** | `fedFrom`, `parentId`, `transformerId` | [`Entities.kt:22, 64`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/Entities.kt#L22) | Verified entity fields. |
| **Panel Schedule Eligibility** | `panelType in PANEL_TYPES \|\| hasChildren` | [`CircuitsGridScreen.kt:228`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt#L228) | Checked against `PANEL_TYPES` set (`line 39`). |

### 4.2 Non-Lossy Status Mapping `[PROPOSED FOR C1]`
All status mapping in `CircuitUiModelMapper.kt` handles the typed enum exhaustively without impossible null branches:

```kotlin
// PROPOSED FOR C1
enum class CircuitUiStatus {
    PASS,
    FAIL,
    STANDBY,
    WARNING,
    INCOMPLETE,
    ERROR;

    fun toDsStatusVariant(): DsStatusVariant = when (this) {
        PASS -> DsStatusVariant.Pass
        FAIL -> DsStatusVariant.Fail
        WARNING -> DsStatusVariant.Warning
        STANDBY,
        INCOMPLETE,
        ERROR -> DsStatusVariant.Neutral
    }

    fun displayLabel(): String = when (this) {
        PASS -> "PASS"
        FAIL -> "FAIL"
        WARNING -> "WARNING"
        STANDBY -> "STANDBY"
        INCOMPLETE -> "INCOMPLETE"
        ERROR -> "ERROR"
    }
}

// PROPOSED FOR C1: Exhaustive mapping on non-null SizingResultStatus
fun resolveCircuitStatus(sizingResult: SizingResult?): CircuitUiStatus {
    if (sizingResult == null) return CircuitUiStatus.STANDBY
    return when (sizingResult.resultStatus) {
        SizingResultStatus.PASS -> CircuitUiStatus.PASS
        SizingResultStatus.FAIL -> CircuitUiStatus.FAIL
        SizingResultStatus.WARNING -> CircuitUiStatus.WARNING
        SizingResultStatus.INCOMPLETE -> CircuitUiStatus.INCOMPLETE
        SizingResultStatus.ERROR -> CircuitUiStatus.ERROR
    }
}
```

---

## 5. Theme-Aware Runtime Styling `[PROPOSED FOR C2/C3]`

All UI components resolve colors dynamically through Material Theme roles:

| UI Area / Role | Theme-Aware Composable Expression | Sourced Palette |
|---|---|---|
| **Screen Background** | `MaterialTheme.colorScheme.background` | MD3 background role |
| **Card Container** | `MaterialTheme.colorScheme.surfaceContainer` | MD3 surface container role |
| **Primary Accent (Cable Size)** | `MaterialTheme.colorScheme.primary` | MD3 primary brand role |
| **Primary Text** | `MaterialTheme.colorScheme.onSurface` | MD3 onSurface role |
| **Secondary / Units Text** | `MaterialTheme.colorScheme.onSurfaceVariant` | MD3 onSurfaceVariant role |
| **Pass Status Pill** | `MaterialTheme.dsExtendedColors.success` | Theme-aware extended success color |
| **Warning Status Pill** | `MaterialTheme.dsExtendedColors.warning` | Theme-aware extended warning color |
| **Fail Status Pill** | `MaterialTheme.dsExtendedColors.danger` | Theme-aware extended danger color |
| **Standby / Neutral Pill** | `MaterialTheme.dsExtendedColors.neutral` | Theme-aware extended neutral color |

---

## 6. Sorting, Filtering & Breadcrumb Semantics `[PROPOSED FOR C2]`

### 6.1 Source Order & Deterministic Sorting
- **`SOURCE_ORDER`:** Follows `@Query("SELECT * FROM circuits WHERE projectId = :projectId ORDER BY id ASC")` ([`AppDatabase.kt:423`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/data/AppDatabase.kt#L423)).
- **`NAME_ASC`:** Locale-stable case-insensitive sort `circuit.circuitName.trim().lowercase(Locale.ROOT)` with blank names placed last. Tie-breaker: `thenBy { it.id }`.
- **`LOAD_DESC`:** `if (it.loadKVA.isNaN() || it.loadKVA.isInfinite()) -Double.MAX_VALUE else it.loadKVA` descending. Tie-breaker: `thenBy { it.id }`.
- **`VD_DESC`:** `sizingResults[it.id]?.cumulativeVDpct.let { if (it == null || it.isNaN() || it.isInfinite()) -Double.MAX_VALUE else it }` descending (missing/invalid values placed last). Tie-breaker: `thenBy { it.id }`.
- **`STATUS`:** Priority: `FAIL` (0), `ERROR` (1), `INCOMPLETE` (2), `WARNING` (3), `PASS` (4), `STANDBY` (5). Tie-breaker: `thenByDescending { it.loadKVA }.thenBy { it.id }`.
- **UI State Scope:** Managed via `rememberSaveable { mutableStateOf(CircuitSortOrder.SOURCE_ORDER) }` for activity recreation and process restoration.

### 6.2 Breadcrumb Resolver
Resolves verified parent and source names without concatenating unverified deep paths:
- If `parentId != null`: Finds parent in circuit list $\rightarrow$ `"${parent.circuitName} > ${circuit.circuitName}"`.
- If `parentId == null` and `fedFrom.isNotBlank()`: `"${circuit.fedFrom} > ${circuit.circuitName}"`.
- Fallback: `circuit.circuitName.ifBlank { "—" }`.

### 6.3 Overlapping Level Filter Predicates
The Level selector provides four independent predicate views:
- `All`: Matches all circuits.
- `Main Feeders`: Matches `circ.fedFrom.equals("Transformer", ignoreCase = true) || circ.parentId == null`.
- `Downstream`: Matches `!circ.fedFrom.equals("Transformer", ignoreCase = true) && circ.parentId != null`.
- `Panels`: Matches `circ.panelType in PANEL_TYPES || childrenLookup.hasChildren(circ.circuitName)`.

---

## 7. Data-Binding & Missing-Data Matrix

| UI Metric | Concrete Source | Display Rule `[PROPOSED FOR C1/C3]` | Missing / Fallback Rule |
|---|---|---|---|
| **Circuit Name** | `CircuitEntity.circuitName` | Exact string, bold (`DsType.titleMedium`), max 2 lines | `"—"` if blank |
| **Schedule Pill** | `panelType in PANEL_TYPES \|\| hasChildren` | Action chip (`DsShape.pill`) | Hidden if ineligible |
| **Cable Size** | `SizingResult.cableSize_mm2` | Accent text (`MaterialTheme.colorScheme.primary`) | `"—"` if sizing missing |
| **Status Badge** | `SizingResult.resultStatus` | `DsStatusBadge` (`Pass` / `Warning` / `Fail` / `Neutral`) | `STANDBY` if sizing missing; exact label matching status |
| **Breadcrumb** | `fedFrom`, `parentId` | `DsInfoChip` (non-interactive display) | Concise label or `"—"` |
| **Load (kVA)** | `CircuitEntity.loadKVA` | `"${String.format(Locale.US, "%.1f", loadKVA)} kVA"` | Real zero displays `"0.0 kVA"` |
| **Design Current ($I_b$)** | `SizingResult.Ib_A` | `"${String.format(Locale.US, "%.1f", Ib_A)} A"` | `"—"` if missing/invalid |
| **Breaker Rating ($I_n$)** | `deviceSize_A`, `deviceType` | `"${deviceSize_A.toInt()} A (${deviceType})"` | `"—"` if missing |
| **Cumulative $V_d$** | `cumulativeVDpct`, `maxVDpct` | `"${String.format(Locale.US, "%.2f", cumulativeVDpct)}%"` with `"Limit ${(maxVDpct * 100).toInt()}%"` | `"—"` if missing |
| **Loading ($I_z$)** | `utilizationPct`, `capacity_derated_Iz_A` | `"${String.format(Locale.US, "%.1f", utilizationPct)}%"` with `"Iz: ${String.format(Locale.US, "%.1f", Iz)} A"` | `"—"` if missing |
| **$I_{sc(3\Phi)}$ / $I_{cu}$** | `iscKA`, `deviceBreakingKA` | `"${String.format(Locale.US, "%.2f", iscKA)} kA"` with `"Icu: ${deviceBreakingKA.toInt()} kA"` | `"—"` for missing subvalue |
| **Cable Footer** | `cableType`, `length_m` | Both present: `"${cableType} • Length: ${String.format(Locale.US, "%.1f", length_m)} m"`<br>Cable blank: `"Length: ${String.format(Locale.US, "%.1f", length_m)} m"` | Length invalid: `"—"`. Real zero preserved if valid. Never fabricates `"4C"`. |

---

## 8. Approved Summary Scope & Semantics `[APPROVED]`

1. **Summary Collection Scope:**
   - Evaluated over **all project circuits** (`state.circuits`), keeping project health stable during search/filtering. Visible count is derived post-filter as `CIRCUITS (${filteredCircuits.size})`.
2. **Summary Status Counts:**
   - `Total`: `state.circuits.size` (All project circuits).
   - `Pass`: `count { it.resultStatus == SizingResultStatus.PASS }` (Typed PASS only).
   - `Warning`: `count { it.resultStatus == SizingResultStatus.WARNING }` (Typed WARNING only; 0 on current engine).
   - `Fail`: `count { it.resultStatus == SizingResultStatus.FAIL }` (Typed FAIL only).
   - `Standby`: `count { sizingResults[it.id] == null }` (Missing results).
   - `Max Vd`: `maxOfOrNull { it.cumulativeVDpct }` among valid sizing results (formatted as `"—"` if none).
   - *Note:* `INCOMPLETE` and `ERROR` circuits are included in `Total` but are not counted under Pass/Warning/Fail.
3. **More Filter Dropdown:** Deferred and omitted from primary chip row (no dead affordances).
4. **Single-Phase Branch Fault ($I_{sc(1\Phi)}$) & Freshness:** Omitted (DO NOT BUILD).

---

## 9. Adaptive Layouts & Accessibility `[PROPOSED FOR C2/C5]`

- **Summary Strip (Whole Project, No Horizontal Scrolling):**
  - $320\text{dp} - 359\text{dp}$: Wrapped $3 + 2$ grid using `DsMetricTile(dense = true)`.
  - $360\text{dp} - 411\text{dp}$: 5-cell row *only if content fits at font scale 1.0*; otherwise wraps $3 + 2$.
  - $\ge 412\text{dp}$: Balanced 5-cell row.
  - Font Scale $\ge 1.30\times$: Wraps adaptively ($3 + 2$); never horizontally scrolls.
- **Touch Targets:** Minimum $\ge 44\text{dp} \times 44\text{dp}$ touch bounds. Where Material 3 naturally provides 48dp, 48dp is retained.
- **Accessibility:** TalkBack labels for icon actions; merged semantics for metric label/value/unit; status communicated by text and dot/icon.

---

## 10. Phase-by-Phase Commit Strategy

1. **One Local Commit per Approved Phase:**
   - **Phase C1 Commit:** Pure presentation models & unit tests (`app/src/main/java/com/example/ui/screens/circuits/` & tests).
   - **Phase C2 Commit:** Search, Level/Status filters, sort, and non-scrolling summary strip in `CircuitsGridScreen.kt`.
   - **Phase C3 Commit:** High-density circuit card expansion in `CircuitsGridScreen.kt`.
   - **Phase C4 Commit:** Edge states and operation validation in `CircuitsGridScreen.kt`.
   - **Phase C5 Commit:** Accessibility, adaptive density, theme polish, and motion safety.
   - **Phase C6a:** Verification and Roborazzi diff captures (**NO commits, NO golden writes**).
   - **Phase C6b Commit:** Dedicated commit containing only approved Circuits golden updates.
2. **Git Policy:** Local commits only. No push, pull, fetch, merge, or rebase.

---

## 11. Confirmation of Protected Boundaries

I confirm that **zero modifications** will be made to:
- [`MainLayout.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/MainLayout.kt) (**VERIFY ONLY / Locked**)
- [`TransformersScreen.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/TransformersScreen.kt) (**100% Locked; 0 pixel drift**)
- Calculation Engines (`CableSizingEngine.kt`, `ShortCircuitEngine.kt`, `ProtectionEngine.kt`)
- Database Entities, Room Schemas, Repositories, Migrations (`Entities.kt`, `AppDatabase.kt`)
- State Orchestrator (`EngineeringViewModel.kt`)
- Export Pipelines (PDF, JSON, DXF, screenshot shares)
- Shared Design System Default Parameters (`DsCard`, `DsMetricTile`, `DsStatusBadge`, `DsSelectableChip`, `DsInfoChip`)

---

## 12. C0 Closeout Decision

**APPROVED WITH A DOCUMENTED BASELINE EXCEPTION FOR CIRCUITS DEVELOPMENT ONLY.**
- Baseline Exception: 3 pre-existing stale Transformer golden assertions in `Step7LightModeGoldenTest`.
- Circuits Regression Gate: Circuits goldens are 100% green and Transformer diff signatures must remain locked and unchanged.
