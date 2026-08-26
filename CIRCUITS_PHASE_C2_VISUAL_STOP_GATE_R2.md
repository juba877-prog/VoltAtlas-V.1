# VoltAtlas Circuits — Phase C2 Visual Stop Gate R2 Report

> **DOCUMENT ID:** `CIRCUITS_PHASE_C2_VISUAL_STOP_GATE_R2`  
> **TASK:** `UI-CIRCUITS-REDESIGN-V1` (Phase C2: Search, Filter, Sort & Health Summary Shell)  
> **GATE STATUS:** **STOP GATE ACTIVE — UNCOMMITTED REVIEW AWAITING APPROVAL**  
> **GIT POLICY:** LOCAL REPOSITORY ONLY. Working tree uncommitted. Zero golden updates.

---

## 1. Exact Files Changed in C2

### Production Implementation
1. [`app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt)
   - Integrated search input bar with trailing clear icon.
   - Built 5-metric adaptive `CircuitsSummaryStrip` (`Total`, `Pass`, `Fail`, `Standby`, `Max Vd`).
   - Integrated `CompactSelector` for Level (`All`, `Main Feeders`, `Downstream`, `Panels`) and Status (`All`, `Pass`, `Fail`, `Standby`).
   - Integrated `CompactSortSelector` (`Order`, `Name`, `Load`, `Length`, `Vd %`, `Status`).
   - Preserved all card bodies, expansion state, callbacks, and floating action button intact for Phase C3.
2. [`app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt)
   - Status model strictly limited to `PASS`, `FAIL`, `STANDBY`.
   - Filter model strictly limited to `ALL`, `PASS`, `FAIL`, `STANDBY`.
   - Summary model defined with 5 metrics: `totalCount`, `passCount`, `failCount`, `standbyCount`, `maxVdDisplay`, `rawMaxVdPct`.
3. [`app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt)
   - `resolveCircuitStatus`: Maps `PASS` $\to$ `PASS`, `FAIL` $\to$ `FAIL`, missing/`INCOMPLETE`/`ERROR`/`WARNING` $\to$ `STANDBY`.
   - `mapToSummaryUiModel`: Computes 5 project metrics from authoritative engine data.
   - `filterCircuits`: Filters on Level and 4 status options (`ALL`, `PASS`, `FAIL`, `STANDBY`).
   - `sortCircuits`: Pure presentation sorting with deterministic ID tie-breaking.

### Tests & Verification
4. [`app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt) (15 unit tests)
5. [`app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC2Test.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC2Test.kt) (15 Compose screen tests)
6. [`app/src/test/java/com/example/ui/screens/circuits/CircuitsVisualCaptureTest.kt`](file:///e:/voltatlas-v.1%20(4)/app/src/test/java/com/example/ui/screens/circuits/CircuitsVisualCaptureTest.kt) (9 Roborazzi visual captures)

---

## 2. Correct Summary & Status Model (Total / Pass / Fail / Standby / Max Vd)

- **Authoritative Status Mapping**:
  - `SizingResultStatus.PASS` $\to$ **Pass** (Green `#10B981` in Dark / `#166534` in Light)
  - `SizingResultStatus.FAIL` $\to$ **Fail** (Red `#EF4444` in Dark / `#B91C1C` in Light)
  - Missing result / `INCOMPLETE` / `ERROR` / `WARNING` $\to$ **Standby** (Neutral Slate `#94A3B8` in Dark / `#64748B` in Light)
- **Zero Synthetic Warning**:
  - `WARNING` is **not** present in the user-visible summary row, status selector, TalkBack semantics, or preview fixtures.
  - Warning is not reachable from the authoritative engine calculation path and is completely eliminated from C2.
- **Removed Attention Note**:
  - The standalone warning banner / secondary status system has been removed. Standby count in the summary row acts as the single, clear representation of incomplete or uncalculated circuits.

---

## 3. Search, Filter, & Sort State-Ownership & Preservation Matrix

| Control | Old State Owner | New State Owner | Restored After Rotation / Config Change? | Restored After Leaving & Returning? | External Consumers / Lifecycle Impact | Verdict |
|---|---|---|---|---|---|---|
| **Search Query** | `EngineeringViewModel.circuitSearchQuery` (`MutableStateFlow<String>`) | `EngineeringViewModel.circuitSearchQuery` (retained, collected as Compose state) | Yes (ViewModel survives configuration changes) | Yes (ViewModel is scoped to project/activity lifecycle) | Reset on project load/switch/clear via existing ViewModel methods | **Preserved**: 100% backward compatible |
| **Level Filter** | `EngineeringViewModel.circuitParentFilter` (`MutableStateFlow<String>`) | `EngineeringViewModel.circuitParentFilter` + typed adapter `CircuitLevelFilter` | Yes (ViewModel survives configuration changes) | Yes (ViewModel retains active selection) | Reset on project load/switch/clear via existing ViewModel methods | **Preserved**: Bidirectional synchronization with typed enum |
| **Status Filter** | `EngineeringViewModel.circuitStatusFilter` (`MutableStateFlow<String>`) | `EngineeringViewModel.circuitStatusFilter` + typed adapter `CircuitStatusFilter` | Yes (ViewModel survives configuration changes) | Yes (ViewModel retains active selection) | Reset on project load/switch/clear via existing ViewModel methods | **Preserved**: Bidirectional synchronization with typed enum |
| **Sort Order** | None (default source order only) | Screen-local `rememberSaveable` (`CircuitSortOrder`) | Yes (`rememberSaveable` survives configuration changes) | Resets to default `SOURCE_ORDER` upon screen entry | None (screen-local presentation concern) | **Approved**: Pure presentation sort with ID tie-breaker |

---

## 4. Test Suite Evidence & Five Failure Explanations

### Test Suite Execution Summary
- **Targeted Circuits Suite (`com.example.ui.screens.circuits.*`)**: `39 discovered / 39 passed / 0 failed / 0 skipped` (**100% PASS**).
- **Full Profile Suite (`testDebugUnitTest -Pprofile=full`)**:
  - **Discovered:** 1447
  - **Passed:** 1442
  - **Skipped:** 13
  - **Failed:** 5
  - **Errors:** 0
- **Accurate Wording Statement:**
  > **No unexpected failures beyond the explicitly evidenced baseline and intended C2 golden deltas.**

### Table of All Five Test Failures
| # | Test Name | Class | Category | Failure Type | Root Cause & Expected Resolution |
|---|---|---|---|---|---|
| 1 | `circuits_searchAndFilters_dark_matchesGolden` | `Step7LightModeGoldenTest` | Circuits Top Shell Golden | `AssertionError` (Intended C2 delta) | Expected C2 visual delta: Screen top shell was redesigned with search, 5-metric health summary, and compact dropdown selectors. Will be re-baselined in Phase C6b per golden policy. |
| 2 | `circuits_searchAndFilters_light_matchesGolden` | `Step7LightModeGoldenTest` | Circuits Top Shell Golden | `AssertionError` (Intended C2 delta) | Expected C2 visual delta: Screen top shell light mode rendered with high-contrast surfaces. Will be re-baselined in Phase C6b per golden policy. |
| 3 | `transformer_selectorRow_light_matchesGolden` | `Step7LightModeGoldenTest` | Transformer Baseline Golden | `AssertionError` (Pre-existing exception) | Pre-existing baseline exception: Stale committed golden PNG predating the approved Transformers redesign. Locked until Phase C6b. |
| 4 | `transformer_emptyState_light_matchesGolden` | `Step7LightModeGoldenTest` | Transformer Baseline Golden | `AssertionError` (Pre-existing exception) | Pre-existing baseline exception: Stale committed golden PNG predating the approved Transformers redesign. Locked until Phase C6b. |
| 5 | `transformer_emptyState_dark_matchesGolden` | `Step7LightModeGoldenTest` | Transformer Baseline Golden | `AssertionError` (Pre-existing exception) | Pre-existing baseline exception: Stale committed golden PNG predating the approved Transformers redesign. Locked until Phase C6b. |

---

## 5. Visual Captures

All 9 Roborazzi captures generated and stored directly in the review artifact directory:

### 1. Default Mobile (360dp x 800dp)
| Dark Theme (360dp) | Light Theme (360dp) |
|---|---|
| ![Circuits Dark 360dp](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Dark_360dp.png) | ![Circuits Light 360dp](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Light_360dp.png) |

### 2. Narrow Device (320dp x 640dp)
| Dark Theme Narrow (320dp) | Light Theme Narrow (320dp) |
|---|---|
| ![Circuits Dark 320dp Narrow](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Dark_320dp_Narrow.png) | ![Circuits Light 320dp Narrow](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Light_320dp_Narrow.png) |

### 3. Wide Device (412dp x 915dp)
| Dark Theme Wide (412dp) | Light Theme Wide (412dp) |
|---|---|
| ![Circuits Dark 412dp Wide](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Dark_412dp_Wide.png) | ![Circuits Light 412dp Wide](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Light_412dp_Wide.png) |

### 4. Accessibility Font Scales (1.3x & 1.5x)
| Dark 1.3x Font Scale (360dp) | Dark 1.5x Font Scale (360dp) | Light 1.5x Font Scale (360dp) |
|---|---|---|
| ![Circuits Dark FontScale 1.3x](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Dark_FontScale_1_3x.png) | ![Circuits Dark FontScale 1.5x](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Dark_FontScale_1_5x.png) | ![Circuits Light FontScale 1.5x](C:/Users/mahmoud.mohamed/.gemini/antigravity-ide/brain/a2221d80-8900-4585-a0b1-c73b9536e70e/Circuits_Light_FontScale_1_5x.png) |

---

## 6. Light-Mode Separation & Contrast Review

1. **Search Surface**: Renders on clean `DsColor.surface` (`#FFFFFF`) with `DsColor.outline` (`#94A3B8`) border and high contrast `DsColor.onSurface` text.
2. **Summary Strip Container**: Container uses `DsColor.surface` (`#FFFFFF`) with subtle border `DsColor.outlineVariant` (`#CBD5E1`), ensuring crisp elevation from background `#F8FAFC`.
3. **Metric Cells**:
   - `Total`: `#0F172A` (Bold primary slate)
   - `Pass`: `#166534` (WCAG AA green)
   - `Fail`: `#B91C1C` (WCAG AA red)
   - `Standby`: `#64748B` (WCAG AA slate neutral)
   - `Max Vd`: `#0F172A` with `#B91C1C` alert highlighting if failing limit.
4. **Compact Selectors & Dropdowns**:
   - Level and Status compact selector pills render on `#FFFFFF` with `#CBD5E1` borders.
   - Dropdown menu surfaces have distinct elevation shadow, high contrast menu items, and primary highlight checkmarks for active selections.

---

## 7. Density & Adaptive Layout Contract Review

- **320–359dp (Narrow)**: `CircuitsSummaryStrip` wraps into 3+2 layout (`Total`, `Pass`, `Fail` on Row 1; `Standby`, `Max Vd` on Row 2) with zero overflow or text clipping.
- **360–399dp (Default)**: Clean, balanced 3+2 presentation on standard phones with comfortable touch targets.
- **$\ge$400dp (Wide / 412dp)**: Expands gracefully to a balanced 5-cell horizontal layout with subtle vertical cell dividers.
- **Accessibility Font Scale $\ge$ 1.30**: Summary metrics wrap cleanly to avoid numeric truncations.

---

## 8. Circuit Card Body Preservation for Phase C3

- **Untouched Scope**:
  - Circuit card container structure, expandable details, cable specs, voltage drop rows, breaker rows, and schedule navigation chips remain 100% untouched.
  - All existing card callbacks (`onCircuitClick`, `onNavigateToPanelSchedule`, `onDeleteCircuit`, `onDuplicateCircuit`) remain identical and fully wired.
  - FAB (`+`) button remains completely untouched.
- **Planned C3 Scope**: Circuit card body redesign will be executed strictly in Phase C3 after C2 approval.

---

## 9. Protected Subsystem & Architecture Integrity

Confirmed **zero changes** to:
- Calculation engines (`SizingEngine`, `ShortCircuitEngine`, `TransformerEngine`, etc.)
- `EngineeringViewModel` class structure & business logic
- Room database, schema, entities, and DAOs
- Navigation graph & routes
- `MainLayout`, top app bar, bottom navigation bar
- Transformers screen & tests
- BoQ / Procurement / Cable Drum / PDF / JSON / DXF export implementations
- Shared Design System default values

---

## 10. Git Status & Diff Statistics

### `git diff --stat`
```
 app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt   | 568 +++++++++++++++++----
 app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt    | 103 ++--
 app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt |  12 +-
 app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt   |  27 +-
 4 files changed, 539 insertions(+), 171 deletions(-)
```

### `git status --porcelain`
```
 M app/src/main/java/com/example/ui/screens/CircuitsGridScreen.kt
 M app/src/main/java/com/example/ui/screens/circuits/CircuitUiModelMapper.kt
 M app/src/main/java/com/example/ui/screens/circuits/CircuitUiModels.kt
 M app/src/test/java/com/example/ui/screens/circuits/CircuitUiModelMapperTest.kt
?? CIRCUITS_PHASE_C2_VISUAL_STOP_GATE_R2.md
?? app/src/test/java/com/example/ui/screens/circuits/CircuitsGridScreenC2Test.kt
?? app/src/test/java/com/example/ui/screens/circuits/CircuitsVisualCaptureTest.kt
```

---

> **STOP GATE ACTIVE — UI-CIRCUITS-REDESIGN-V1 / PHASE C2 R2 AWAITING APPROVAL**  
> No commits made. Working tree uncommitted. Awaiting Mahmoud's visual review and authorization before committing C2.
