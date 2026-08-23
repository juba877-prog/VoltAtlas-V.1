# VoltAtlas Performance Audit: Startup, UI Lag & Navigation

**Executive Verdict: MULTIPLE HIGH-CONFIDENCE CONTRIBUTORS**

---

## 1. Executive Verdict

```
MULTIPLE HIGH-CONFIDENCE CONTRIBUTORS
```

1. **Eager `EngineeringAssistantViewModel` instantiation** at app startup forces a synchronous `GlobalPreferences` disk-read and `AppDatabase` builder execution on the **main thread** during `setContent`, even though the AI assistant is not visible. This was **added in commit `2445b66`**.
2. **Boot animation waits for `Catalog.loadAll()`** — 7 sequential JSON file reads (~6 catalog files + derating) must complete before `StartupState.Ready` is emitted. The parsing runs on `Dispatchers.IO` but `catalogJob.join()` blocks the ready state.
3. **O(N²) per-item filtering** in `CircuitsGridScreen` — each rendered circuit item scans the entire circuit list to find children and calls `Catalog.isCableTypeUnsupported()`, causing visible scroll jank on populated projects.
4. **21 `runBlocking` calls** in `EngineeringCalculationCoordinator` — these block whichever thread invokes the coordinator (typically `Dispatchers.Default` via `calculateProjectCableSizingAsync`), but risk blocking the main thread if invoked synchronously during navigation or recalculation.
5. **Broad root-level state collection** in `MainLayout` — 6+ `collectAsState` calls at the root Scaffold level force full recomposition of the 1,319-line composable tree on any minor state change.

---

## 2. Reproduction Environment

| Field | Value |
| :--- | :--- |
| Branch | `integration/home-snapshot-2026-08-23` |
| HEAD | `2445b66` |
| Comparison Baseline | `ffe89e9d282f74448e5f358188861de205ac1d14` |
| Build Type | `debug` (no proguard, no R8, full compose tooling) |
| Device/Emulator | **UNAVAILABLE** (adb not found in PATH) |
| Android Version | N/A |
| Project State | Unknown (varies by user) |
| Cloud LLM Gate | `ENABLE_CLOUD_LLM = false` (hardcoded in `build.gradle.kts` L28) |
| minSdk / targetSdk | 24 / 36 |
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |
| Compose BOM | 2024.09.00 |
| Gradle | 9.3.1 |

> **IMPORTANT:** This is a **debug build** analysis. Debug builds include Compose tooling, no R8 shrinking, no proguard optimization, and full reflection. Some observed lag may not manifest in release/profile builds. However, the architectural issues identified here will affect all build types, only varying in magnitude.

---

## 3. Measured Symptoms

```
RUNTIME PROFILING NOT AVAILABLE IN THIS ENVIRONMENT
```

ADB is not available on the development machine. No `am start -W` timings, no Perfetto traces, no frame timing data, no Logcat evidence, and no memory allocation snapshots were captured.

All findings below are based on **static code analysis**, **diff regression analysis**, and **structural call-path tracing**. Runtime-dependent findings are clearly marked.

---

## 4. Startup Critical Path

The following is the ordered call chain from process creation to first interactive screen, with thread and eager/lazy classification.

### Step 1: `MainActivity.onCreate` — `installSplashScreen()`, `enableEdgeToEdge()`, `requestHighRefreshRate()`
- **File:** `MainActivity.kt:31-36`
- **Thread:** `MAIN_THREAD_CONFIRMED`
- **Cost:** LOW — Standard Android lifecycle setup. `requestHighRefreshRate()` is a try-catch around `windowManager.defaultDisplay.supportedModes` (trivial).
- **Changed in `2445b66`:** NO

### Step 2: `setContent {}` — `viewModel()` for `EngineeringViewModel`
- **File:** `MainActivity.kt:39`
- **Thread:** `MAIN_THREAD_CONFIRMED`
- **Cost:** MEDIUM — Creates `EngineeringViewModel(application)`. The `init` block launches `retryStartup()` on `viewModelScope`, which dispatches to `Dispatchers.IO`. The ViewModel constructor itself is lightweight.
- **Changed in `2445b66`:** NO

### Step 3: `setContent {}` — `viewModel(factory = ...)` for `EngineeringAssistantViewModel` — REGRESSION
- **File:** `MainActivity.kt:40-42`
- **Thread:** `MAIN_THREAD_CONFIRMED` / `EAGER`
- **Cost:** **HIGH** — This was **added in `2445b66`**. It executes the following chain **synchronously on the main thread** during first composition:
  1. `EngineeringAssistantViewModelFactory.production(application)` (Factory:36-59)
  2. `AppDatabase.getDatabase(application)` — Room singleton builder with 22 migrations registered (AppDatabase.kt:1007-1033). On first call, this builds the database instance synchronously. On subsequent calls, it returns the cached singleton (fast).
  3. `GlobalPreferences(application)` — Reads 18 SharedPreferences keys from disk (GlobalPreferences.kt:8-204). `getSharedPreferences()` loads the entire XML file into memory **synchronously**.
  4. `DefaultTrustedPrincipalProvider { ... }` — Lightweight lambda capture (trivial).
  5. `CableProposalPreparationCoordinator(projectDao, circuitDao)` — Instantiates DAO handles (lightweight).
  6. `lazy { ... }` for the approval gateway — **DEFERRED** (good). The `CatalogRepository` JSON parsing is not triggered at startup.
  7. The factory's `create()` then instantiates `EngineeringAssistantViewModel(conversationEngine, ...)`:
     - `ConversationEngineProvider.provideConversationEngine()` — Creates `LocalRuleEngineAdapter`, checks `CloudLlmFeatureGate` (cloud disabled), returns `HybridConversationRouter`. All lightweight.
     - `FirebaseAiServiceImpl()` — Empty Phase 1 stub (trivial).
  8. The ViewModel has **no `init` block** and launches no background work.

**Impact:** The `GlobalPreferences` synchronous SharedPreferences load is the main-thread-blocking operation. `AppDatabase.getDatabase()` is also synchronous but typically fast if already initialized by `EngineeringViewModel` (the two ViewModels are created sequentially in the same `setContent` block, so `EngineeringViewModel` likely calls `getDatabase()` first via its `repository`).

### Step 4: `EngineeringViewModel.init` — `retryStartup()`
- **File:** `EngineeringViewModel.kt:200-246`
- **Thread:** `BACKGROUND_CONFIRMED` (via `viewModelScope.launch` then `Dispatchers.IO` for heavy work)
- **Cost:** HIGH latency but NOT main-thread-blocking. The chain:
  1. `launch(Dispatchers.IO) { Catalog.loadAll(context) }` — Reads and parses 7 JSON asset files sequentially.
  2. `withContext(Dispatchers.IO) { repository.getAllProjectsList() }` — First Room query, triggers SQLite open.
  3. `catalogJob.join()` — **Blocks `StartupState.Ready` emission until all catalogs are parsed**. The boot animation screen is shown until this completes.
- **Changed in `2445b66`:** NO (pre-existing)

### Step 5: Boot Animation — `MainLayout` transition
- **File:** `MainActivity.kt:79-98`
- **Thread:** `MAIN_THREAD_CONFIRMED`
- **Cost:** MEDIUM — `AnimatedContent` with fade transitions. When `isBootFinished` becomes true, `MainLayout` is composed for the first time.
- **Changed in `2445b66`:** Only the `MainLayout` call signature changed (added `assistantViewModel` parameter).

### Step 6: `MainLayout` initial composition — `ProjectPickerScreen`
- **File:** `MainLayout.kt:67-176`
- **Thread:** `MAIN_THREAD_CONFIRMED`
- **Cost:** MEDIUM — Collects 6+ StateFlows, evaluates conditions, renders `ProjectPickerScreen` when `activeId == null`.
- **Changed in `2445b66`:** YES — Added `assistantViewModel` parameter (L70) and `assistantUiState` collection (L80). Added Quick Engineering navigation state (L112-113).

---

## 5. Navigation and Recomposition Findings

### Finding 5.1: O(N squared) filtering inside `CircuitsGridScreen` lazy list items — HIGH
- **File:** `CircuitsGridScreen.kt:222-265`
- **Code:**
  ```kotlin
  items(filteredCircuits, key = { it.id }) { circ ->
      val childCircuits = state.circuits.filter { 
          it.fedFrom.trim().equals(circ.circuitName.trim(), ignoreCase = true) 
      }
      val childrenTotalLoad = childCircuits.sumOf { it.loadKVA }
      ...
      val isUnsupported = Catalog.isCableTypeUnsupported(circ.cableType)
  ```
- **Problem:** For each visible item during scrolling, the entire `state.circuits` list is scanned (O(N) per item = O(N squared) total). `Catalog.isCableTypeUnsupported()` triggers a lazy `cableTypesByName` map build and `supportedTypeCodes` set build on first call.
- **Affects:** Scroll performance on projects with >20 circuits. User-visible jank during navigation to and scrolling within the Circuits tab.
- **Changed in `2445b66`:** The `Catalog.isCableTypeUnsupported()` call was added in this commit. The `childCircuits` filter pre-existed.

### Finding 5.2: Broad root-level state collection in `MainLayout` — MEDIUM
- **File:** `MainLayout.kt:72-84`
- **Code:** 6 `collectAsState` calls at root level including `assistantUiState` at L80 (new in `2445b66`).
- **Problem:** Any emission to any of these flows recomposes the entire 1,319-line `MainLayout` composable, including the top bar, bottom navigation, and all child screens.
- **Affects:** Every state change causes a full recomposition sweep from the root.

### Finding 5.3: `GlobalSettingsScreen` collects 14+ preference flows individually — LOW
- **File:** `GlobalSettingsScreen.kt`
- **Problem:** Each preference toggle causes the entire settings screen tree to recompose.
- **Affects:** Settings screen only. Not on startup or navigation critical path.

### Finding 5.4: Quick Engineering V2 screens run calculations synchronously in `remember` — LOW
- **Files:** `QuickVoltageDropScreenV2.kt`, `QuickPfcScreenV2.kt`, `QuickLoadCurrentScreenV2.kt`
- **Problem:** Engineering calculations inside `remember(inputState) { ... }` execute on the main thread during composition. However, these are memoized and only re-execute when inputs change.
- **Affects:** Text input responsiveness in Quick Engineering screens only. Not on startup path.

---

## 6. Regression Diff Findings

### Changes between `ffe89e9` and `2445b66` that plausibly caused or worsened lag:

| File | Change | Startup Impact | Navigation Impact |
| :--- | :--- | :--- | :--- |
| `MainActivity.kt:40-42` | Added eager `EngineeringAssistantViewModel` creation with `EngineeringAssistantViewModelFactory.production(application)` | **YES — main thread SharedPreferences I/O** | No |
| `EngineeringAssistantViewModelFactory.kt` | **NEW FILE** — Synchronously creates `AppDatabase`, `GlobalPreferences`, `CableProposalPreparationCoordinator` on main thread | **YES** | No |
| `MainLayout.kt:70,80` | Added `assistantViewModel` param and `assistantUiState` collection at root | Minimal | **YES — additional root recomposition trigger** |
| `CircuitsGridScreen.kt:304` | Added `Catalog.isCableTypeUnsupported(circ.cableType)` inside lazy item | No | **YES — per-item catalog lookup during scroll** |
| `GlobalPreferences.kt:15-17` | Added `trustedEngineerIdentity` field (one more SharedPreferences read at init) | **YES — marginal** | No |
| `Models.kt:117-144` | Added `cachedDiagnostics`, `supportedTypeCodes`, `cableTypesByName` volatile caches | No | **Positive — reduces repeat computation** |

### Changes that do NOT impact performance:
- `EngineeringAssistantSheet.kt` — One lambda reference change (trivial).
- `TrustedProposalApprovalGateway.kt` — Visibility modifier change only.
- `DurableMutationWorkflow.kt` — Added companion helper (not called at startup).
- `ElectricalConverters.kt` — Pure formula corrections (not on startup path).
- All Quick Engineering screen/model files — Only loaded on demand, not at startup.
- All test files and documentation — No runtime impact.
- `gradlew`, `gradlew.bat`, `gradle-wrapper.properties` — Build-time only.
- `gradle/libs.versions.toml` — Removed unused dependency (positive if anything).

---

## 7. Ranked Root Causes

### P1: HIGH-CONFIDENCE CONTRIBUTOR — Eager `EngineeringAssistantViewModel` Factory on Main Thread

- **File:** `MainActivity.kt:40-42` then `EngineeringAssistantViewModelFactory.kt:36-59` then `GlobalPreferences.kt:8-204`
- **Call path:** `MainActivity.setContent` then `remember { Factory.production(app) }` then `GlobalPreferences(app)` then `getSharedPreferences("VoltAtlas_Globals", MODE_PRIVATE)` then 18x `getString/getBoolean/getInt/getFloat`
- **Evidence:** Static call-path analysis. `remember {}` executes its lambda synchronously during first composition, which runs on the main thread. `getSharedPreferences()` is documented by Android to perform synchronous disk I/O on first access.
- **Affected user action:** App startup / first frame
- **Why it appeared after `2445b66`:** Lines 40-42 in `MainActivity.kt` are entirely new. Before `ffe89e9`, no `assistantViewModel` was created at startup.
- **Minimum correction concept:** Defer `EngineeringAssistantViewModel` creation until the user actually opens the assistant sheet. Use `lazy` delegate or create it only when `assistantUiState.isOpen` is needed.
- **Regression risk:** None if the ViewModel is still scoped to the Activity lifecycle and created before first use.
- **Verification:** Measure `am start -W` cold start time before and after deferral.

### P1: HIGH-CONFIDENCE CONTRIBUTOR — Boot Screen Blocked by Sequential Catalog JSON Parsing

- **File:** `EngineeringViewModel.kt:210-233` then `Models.kt:238-360`
- **Call path:** `EngineeringViewModel.init` then `retryStartup()` then `launch(Dispatchers.IO) { Catalog.loadAll(context) }` then reads 7 JSON asset files **sequentially** then `catalogJob.join()` blocks `StartupState.Ready`
- **Evidence:** Static. `loadAll` reads: `derating_data.json`, `cable_types.json`, `data_sheet.json`, `cable_catalog.json`, `breaker_catalog.json`, `accessory_catalog.json`, `conduit_catalog.json` — each via `readAsset()` which reads the entire file into a `ByteArray` then parses it with `JSONArray`.
- **Affected user action:** Time from app open to first interactive screen (ProjectPickerScreen). The boot animation plays until catalogs finish loading.
- **Why it appeared after `2445b66`:** This is **pre-existing** (not a regression). However, the new eager `GlobalPreferences` I/O on the main thread delays the start of `retryStartup()`, compounding the total time-to-interactive.
- **Minimum correction concept:** (1) Load catalog files concurrently (7 parallel `async` calls instead of sequential). (2) Allow `StartupState.Ready` to emit before catalog loading completes, loading catalogs lazily or showing a partial-ready state.
- **Regression risk:** Must ensure `Catalog.isLoaded` is checked before any engineering calculation. Currently gated by `if (isLoaded) return`.
- **Verification:** Measure boot animation duration before and after concurrent loading.

### P2: PLAUSIBLE, NEEDS RUNTIME CONFIRMATION — O(N squared) Filtering in CircuitsGridScreen

- **File:** `CircuitsGridScreen.kt:222-265`
- **Call path:** `MainLayout` then tab 1 then `CircuitsGridScreen` then `LazyColumn` then `items(filteredCircuits)` then per-item `state.circuits.filter { ... }` + `Catalog.isCableTypeUnsupported()`
- **Evidence:** Static O(N squared) complexity analysis. For N=100 circuits, this is 10,000 string comparisons per scroll frame. The `isCableTypeUnsupported` call added in `2445b66` adds map lookups per item.
- **Affected user action:** Scrolling in the Circuits tab, especially on projects with many circuits.
- **Why it worsened after `2445b66`:** `Catalog.isCableTypeUnsupported()` was added per item at line 304.
- **Minimum correction concept:** Pre-compute a `Map<String, List<CircuitEntity>>` keyed by `fedFrom` in the ViewModel. Pre-compute `isUnsupported` per circuit. Pass flat UI models to the composable.
- **Regression risk:** None if the map is rebuilt when circuits change.
- **Verification:** Profile frame timing while scrolling 100+ circuit project before and after.

### P2: PLAUSIBLE, NEEDS RUNTIME CONFIRMATION — `runBlocking` in EngineeringCalculationCoordinator

- **File:** `EngineeringCalculationCoordinator.kt` — 21 occurrences
- **Call path:** Various calculation methods then `getProjectRevision()` (L71-79) then `runBlocking { provider.getCurrentRevision(projectId).revision }`. Also used in freshness recording after each circuit calculation.
- **Evidence:** Static. `runBlocking` blocks the calling thread. The bulk calculation methods (`calculateProjectCableSizingAsync`) dispatch to `Dispatchers.Default`, so the `runBlocking` calls execute on Default threads, not Main. However, single-circuit calculations (`calculateCableSizing`) do **not** specify a dispatcher, and callers could invoke them from Main.
- **Affected user action:** Recalculation, project opening with auto-recalc, navigation to screens that trigger calculation.
- **Why it appeared after `2445b66`:** **Pre-existing** — not a regression.
- **Minimum correction concept:** Replace `runBlocking` with `suspend fun` signatures. Make `getProjectRevision` suspend. Callers already dispatch to background.
- **Regression risk:** Requires converting coordinator API to suspend functions, which changes call sites.
- **Verification:** Profile thread blocking during project open and recalculation.

### P2: PLAUSIBLE, NEEDS RUNTIME CONFIRMATION — Root-Level State Collection in MainLayout

- **File:** `MainLayout.kt:72-84`
- **Evidence:** Static. Adding `assistantUiState` collection at L80 means any assistant state change (including internal `isThinking` toggles) triggers recomposition of the entire `MainLayout` tree.
- **Affected user action:** Any assistant interaction while the main layout is visible.
- **Why it worsened after `2445b66`:** The `assistantUiState` collection at L80 is new.
- **Minimum correction concept:** Move `assistantUiState` collection to a derived composable that only wraps the assistant sheet overlay, not the entire `MainLayout`. Use `derivedStateOf` for boolean checks like `assistantUiState.isOpen`.
- **Regression risk:** Minimal — structural refactor of state scoping.
- **Verification:** Enable Compose recomposition counter in Layout Inspector; count recompositions before/after.

---

## 8. Non-Causes Cleared

| System | Evidence | Verdict |
| :--- | :--- | :--- |
| **AI Conversation Engine** | `ConversationEngineProvider.provideConversationEngine()` creates lightweight stubs. `CloudLlmFeatureGate.isCloudLlmEnabled()` returns `false` (hardcoded `ENABLE_CLOUD_LLM = false`). `FirebaseAiServiceImpl` is an empty stub. | **NOT ON STARTUP PATH** |
| **Approval Gateway / Mutation Workflow** | Wrapped in `lazy { }` inside the factory. `CatalogRepository` JSON parsing only triggers on actual approval execution. | **DEFERRED — NOT ON STARTUP PATH** |
| **Room Database Migrations** | 22 migrations are *registered* but only *executed* if the DB schema version is outdated. On a normal launch, this is a no-op. | **NOT PERFORMANCE RELEVANT** |
| **Quick Engineering Hub** | All 8 calculator screens are behind navigation conditionals (`if (showQuickEngineering)`). None are composed at startup. | **NOT ON STARTUP PATH** |
| **`ElectricalConverters.kt` changes** | Pure formula corrections. Not called at startup or during navigation. | **NOT PERFORMANCE RELEVANT** |
| **`ConversionPreferences.kt` synchronization** | Added `synchronized(lock)` — only affects conversion history persistence, not startup. | **NOT ON STARTUP PATH** |
| **`DurableMutationWorkflow.kt` companion helper** | Added `createGateway()` — only called inside `lazy {}` block. | **NOT ON STARTUP PATH** |
| **Test execution profiles** (`build.gradle.kts`) | Build-time only configuration. No runtime impact. | **NOT PERFORMANCE RELEVANT** |
| **Firebase App Check / BOM** | `firebase-appcheck-recaptcha` is declared but Firebase initialization is implicit via ContentProvider. This is standard and pre-existing. | **PRE-EXISTING, NOT A REGRESSION** |

---

## 9. Minimum Correction Plan

### MUST FIX

1. **Defer `EngineeringAssistantViewModel` creation** — Do not create it eagerly in `setContent`. Either use `lazy` initialization in `MainActivity` or create the ViewModel only when the assistant sheet is first opened. This eliminates the synchronous `GlobalPreferences` disk I/O and redundant `AppDatabase.getDatabase()` call from the main-thread startup path.

2. **Pre-compute circuit children map in ViewModel** — Replace the per-item `state.circuits.filter { ... }` in `CircuitsGridScreen` with a pre-computed `Map<String, List<CircuitEntity>>` keyed by normalized `fedFrom`. Also pre-compute `isUnsupported` per circuit. This converts O(N squared) to O(N).

### MEASURE FIRST

3. **Parallelize `Catalog.loadAll()` JSON parsing** — Replace sequential `readAsset` + `loadJson` calls with concurrent `async` blocks inside a `coroutineScope`. Measure actual parsing time before and after to confirm the improvement justifies the change.

4. **Scope `assistantUiState` collection** — Move `assistantUiState` collection from `MainLayout` root to a narrower scope (e.g., a wrapper composable around the assistant overlay). Measure recomposition counts before and after.

### OPTIONAL LATER

5. **Replace `runBlocking` in `EngineeringCalculationCoordinator`** — Convert `getProjectRevision()` and freshness recording to suspend functions. This requires API changes across the coordinator's callers.

6. **Consolidate `GlobalPreferences` into a single state class** — Replace 14+ individual `MutableStateFlow` fields with a single `data class GlobalPreferencesState` to reduce collector count and recomposition triggers in `GlobalSettingsScreen`.

---

## 10. Verification Plan

### Before/After Measurements Required

| Metric | Tool | Target |
| :--- | :--- | :--- |
| Cold start time | `adb shell am start -W` (3 runs) | Measure before fix 1, after fix 1 |
| Warm start time | `adb shell am start -W` (3 runs) | Same |
| Circuits tab scroll frame time | Systrace / Perfetto | Measure before fix 2, after fix 2 |
| Boot animation duration | Logcat timestamps or Perfetto | Measure before fix 3, after fix 3 |
| MainLayout recomposition count | Compose Layout Inspector | Measure before fix 4, after fix 4 |

### Functional Regression Tests

- Full offline test suite: `.\gradlew.bat testDebugUnitTest --offline -Pprofile=full` — must pass 1,270/1,270.
- Manual: Open assistant sheet after fix 1 — verify it still functions correctly.
- Manual: Navigate to Circuits tab with >20 circuits after fix 2 — verify children indicators still display.

### Protected Area Checks

- Engineering calculation results must be identical before and after all fixes.
- Demand factors unchanged.
- Room schema/migrations unmodified.
- Proposal then Approval then Revision Guard then Mutation then Revalidation pipeline unaffected.

---

## 11. Audit Limitations

1. **No runtime profiling available.** ADB is not in PATH. No device or emulator was connected. All findings are from static analysis. Runtime confirmation is required for P2 findings.
2. **Debug build only.** The observed lag may be partially attributable to debug build overhead (Compose tooling, no R8, full reflection). A profile or release build comparison was not possible.
3. **No frame timing data.** Cannot quantify exact millisecond impact of each finding.
4. **No Compose recomposition counts.** Layout Inspector was not available.
5. **No memory/GC data.** Cannot confirm allocation pressure from catalog parsing or list filtering.
6. **Project data state unknown.** The impact of O(N squared) filtering depends on circuit count, which varies by user project.

---

## 12. Safety Confirmation

```
Production files modified:  NO
Test files modified:        NO
Database/schema modified:   NO
Engineering formulas modified: NO
Commit created:             NO
Push/pull/fetch/remote operation: NO
New phase started:          NO
```
