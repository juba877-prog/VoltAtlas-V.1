# VoltAtlas — Skill Audit Verification Report (Step 1)

> **Document Status:** Complete verification findings for Hypotheses H1–H9.  
> **Repository:** `e:\voltatlas-v.1 (4)`  
> **Date:** September 19, 2026  
> **Rule:** Every finding carries exact `file:line` verification evidence. Zero production code changed.

---

## Executive Verification Summary

| Hypothesis | Topic | Status | Summary of Evidence |
|---|---|---|---|
| **H1** | Lifecycle-unaware Flow collection | **PARTIALLY CONFIRMED** | 49 active `collectAsState()` calls across 7 UI screens and dialogs. `androidx.lifecycle:lifecycle-runtime-compose:2.8.7` is already present on classpath (`app/build.gradle.kts:155`). Background collection is harmful for heavy calculation flow `uiState`. |
| **H2** | SLD pan/zoom read in composition | **CONFIRMED** | `zoomScale: Float` and `panOffset: Offset` are passed as raw scalar parameters in `com/example/ui/SldScreen.kt:325-326` to `SldCanvas.kt:45-46`. Re-evaluates `remember(zoomScale, detailLevel)` on line 80 during composition. |
| **H3** | Unstable domain models | **CONFIRMED** | `app/build/compose_reports/app-classes.txt:5454` reports `<runtime stability> = Runtime(...)` for `UiState.Success` due to `CircuitEntity`, `SizingResult`, etc. All inspected domain models (`Entities.kt`, `CableSizingEngine.kt`, `ShortCircuitEngine.kt`, `TransformerEngine.kt`) are 100% `val`-only with no `var` properties. Safe for `@Immutable` / compiler stability config. |
| **H4** | `runBlocking` on the main thread | **PARTIALLY CONFIRMED** | **Confirmed** for `EngineeringAssistantViewModelFactory.kt:54` (blocks Main thread during ViewModel creation while parsing JSON assets). **Rejected** for `EngineeringCalculationCoordinator.kt:80` as dead code (has 35 internal call sites, not dead). |
| **H5** | Exposed `MutableStateFlow` | **CONFIRMED** | Exactly 9 public `MutableStateFlow` properties in `EngineeringViewModel.kt:55-896`. Mutated directly from UI in `MainLayout.kt:1207`, `CircuitsGridScreen.kt:153,215,234,781,821,928,1032`, and `TransformersScreen.kt:168`. |
| **H6** | Manifest / FileProvider security | **CONFIRMED** | (a) `VoltAtlasAppFunctionService` is not declared in `AndroidManifest.xml`. (b) `file_paths.xml:3-6` exposes root (`path="."`) for all 4 path types. Actual export files write only to `context.cacheDir` (`VoltAtlas_SLD.png` and `*_SLD.dxf`). |
| **H7** | Adaptive layout APIs | **CONFIRMED** | Custom policy defined in `com/example/ui/adaptive/VoltAtlasWindowWidth.kt:53`. Consumed at 2 production sites (`MainLayout.kt:389`, `MoreModulesScreen.kt:120`). Does not use official `WindowSizeClass`. |
| **H8** | Focus / keyboard traversal | **CONFIRMED** | Exactly 1 `FocusRequester` app-wide (`EngineeringAssistantSheet.kt:63`). 0 usages of `focusProperties`, `onKeyEvent`, or `onPreviewKeyEvent` across all screens. |
| **H9** | Mandatory Test Baseline | **CONFIRMED** | Full suite execution: **2,241 total, 2,227 passed, 1 failed, 13 skipped**. Failing test: `CoordinatorNonBlockingExecutionTest.guardedCalculationsAwaitsMutexNativelyUnderCoroutine` caused by an uncaught coroutine leak in `NameplateAnalyzer.kt:49` (`withContext(Dispatchers.Main)`). |

---

## Detailed Findings & Evidence

### H1 — Lifecycle-unaware Flow collection
- **Status:** **PARTIALLY CONFIRMED**
- **Classpath Check:**
  - `gradle/libs.versions.toml:54`: `androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }` (version `2.8.7`)
  - `app/build.gradle.kts:155`: `implementation(libs.androidx.lifecycle.runtime.compose)`
  - *Result:* `collectAsStateWithLifecycle()` is **already on the classpath**; no dependency addition needed.
- **Occurrence Count:**
  - Total `collectAsState` occurrences in `app/src/main/java`: 64 lines (including imports and usages).
  - Active call sites in composables: 49 call sites.
- **Key Call Sites:**
  1. `com/example/ui/MainLayout.kt:73`: `val state by viewModel.uiState.collectAsState()`
  2. `com/example/ui/MainLayout.kt:75`: `val activeId by viewModel.activeProjectId.collectAsState()`
  3. `com/example/ui/MainLayout.kt:76`: `val projects by viewModel.projects.collectAsState()`
  4. `com/example/ui/MainLayout.kt:78`: `val showGlobalSettings by viewModel.showGlobalSettings.collectAsState()`
  5. `com/example/ui/MainLayout.kt:79`: `val confirmDestructiveActions by viewModel.preferences.confirmDestructiveActions.collectAsState()`
  6. `com/example/ui/MainLayout.kt:102`: `val hasCompletedFirstLaunch by viewModel.preferences.hasCompletedFirstLaunch.collectAsState()`
  7. `com/example/ui/MainLayout.kt:601`: `val currentThemeMode by viewModel.themeMode.collectAsState()`
  8. `com/example/ui/MainLayout.kt:1212-1217`: `genSet`, `atsSet`, `upsSet`, `pfGlob`, `pfTrans`, `pendingDeleteVal`
  9. `com/example/ui/MainLayout.kt:1243-1244`: `projectsVal`, `activeIdVal`
  10. `com/example/ui/ConduitFillScreen.kt:46`: `val stateVal by viewModel.uiState.collectAsState()`
  11. `com/example/ai/ui/EngineeringAssistantSheet.kt:46`: `val uiState by viewModel.uiState.collectAsState()`
  12. `com/example/ui/screens/CircuitsGridScreen.kt:69-70`: `circuitSearchQuery`, `selectedCircuitId`
  13. `com/example/ui/screens/TransformersScreen.kt:98`: `val txId by viewModel.selectedTransformerId.collectAsState()`
  14. `com/example/ui/screens/PanelScheduleScreen.kt:61, 114`: `activeProject`, `selectedCircuitId`
  15. `com/example/ui/SldScreen.kt:96, 114`: `trustedEngineerIdentity`, `dbPositionsFlow`
  16. `com/example/ui/GlobalSettingsScreen.kt:47-67, 480`: 14 preferences and project flows.
  17. `com/example/ui/dialogs/CircuitEditDialog.kt:98-102, 114-129, 187-218, 1066`: 19 inline `.collectAsState().value` calls.
- **Harm Assessment:**
  - `EngineeringViewModel.kt:781-785`:
    ```kotlin
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )
    ```
  - `uiState` runs a 9-stream combine that recalculates cable sizing, short circuit, generator, transformer, protection, ADS, coordination, earthing, conduit fill, and SLD graph generation on every emission.
  - Because `collectAsState()` remains subscribed as long as the composable is in the composition tree (even if the Activity is `STOPPED` in background), the 5,000ms `WhileSubscribed` timeout never elapses. Upstream flows keep running in background.
  - For `uiState` and major screen flows, this is **harmful to battery and CPU**. Replacing them with `collectAsStateWithLifecycle()` ensures upstream flows pause 5s after the app moves to background.
  - Long-running export operations (e.g. PDF/DXF export) run in separate coroutines launched via `viewModelScope.launch`, not inside Compose flow collections, so they are **not affected** by switching to lifecycle-aware collection.

---

### H2 — SLD pan/zoom read in composition
- **Status:** **CONFIRMED**
- **Evidence:**
  - In `com/example/ui/SldScreen.kt`:
    - Line 241: `var zoomScale by remember { mutableFloatStateOf(1.0f) }`
    - Line 242: `var panOffset by remember { mutableStateOf(Offset.Zero) }`
    - Lines 323-326:
      ```kotlin
      SldCanvas(
          engineResult = engineResult,
          zoomScale = zoomScale,
          panOffset = panOffset,
          selectedNodeId = selectedNode?.id,
      ```
    - Lines 348-357:
      ```kotlin
      onPanZoomChange = { panChange, zoomChange ->
          val newScale = (zoomScale * zoomChange).coerceIn(0.05f, 4.0f)
          zoomScale = newScale
          panOffset = com.example.sld.SldViewportHelper.clampPan(...)
      }
      ```
  - In `com/example/sld/SldCanvas.kt`:
    - Lines 43-46:
      ```kotlin
      fun SldCanvas(
          engineResult: SldEngineResult,
          zoomScale: Float,
          panOffset: Offset,
          selectedNodeId: String?,
      ```
    - Lines 80-82:
      ```kotlin
      val effectiveDetailLevel = androidx.compose.runtime.remember(zoomScale, detailLevel) {
          SldDetailLevelPolicy.resolveEffectiveDetailLevel(zoomScale, detailLevel)
      }
      ```
    - Lines 233-236:
      ```kotlin
      withTransform({
          translate(panOffset.x, panOffset.y)
          scale(zoomScale, zoomScale, pivot = Offset.Zero)
      }) { ... }
      ```
- **Finding:**
  - `zoomScale` and `panOffset` are passed as raw primitive values directly to `SldCanvas`.
  - Mutating `zoomScale` or `panOffset` on every pan/zoom gesture frame triggers recomposition of `SldCanvas` and re-evaluates `remember(zoomScale, detailLevel)` at line 80 during the composition phase.
  - Note on FPS claim: The previous claim of "120 recompositions/sec" was a theoretical maximum matching 120Hz display refresh rates; no frame-rate profiler trace was captured. The architectural finding that reads occur in composition rather than being deferred to draw is verified.

---

### H3 — Unstable domain models
- **Status:** **CONFIRMED**
- **Evidence from `app/build/compose_reports/app-classes.txt:5454`:**
  ```text
  runtime class com.example.ui.UiState.Success {
    runtime val circuits: List<CircuitEntity>
    runtime val transformers: List<TransformerEntity>
    runtime val sizingResults: Map<Int, SizingResult>
    runtime val transformerResults: Map<String, TransformerResult>
    runtime val boq: List<BoqItem>
    runtime val drums: List<DrumAllocation>
    ...
    <runtime stability> = Runtime(SldValidationIssue),Runtime(DrumAllocation),Runtime(CircuitEntity),Runtime(ConduitProcurementGroup),Runtime(ConduitFillResult),Runtime(EarthingEntity),Runtime(EarthingResult),Runtime(CoordinationResult),Runtime(ProtectionResult),Runtime(AdsResult),Runtime(ShortCircuitResult),Runtime(PfCorrectionResult),Runtime(UpsEntity),Runtime(UpsResult),Runtime(AtsEntity),Runtime(AtsEntity),Runtime(AtsResult),Runtime(GeneratorEntity),Runtime(GeneratorResult),Runtime(PanelScheduleResult),Runtime(ProcurementItem),Runtime(DrumAllocation),Runtime(BoqItem),Runtime(TransformerResult),Runtime(SizingResult),Runtime(TransformerEntity),Runtime(CircuitEntity)
  }
  ```
- **Mutability Audit of Domain Models:**
  - `com/example/data/Entities.kt:18-98` (`CircuitEntity`): 53 properties, **100% `val`**, zero `var`s. Primitive/String types only.
  - `com/example/data/Entities.kt:108-136` (`TransformerEntity`): 22 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:143-169` (`GeneratorEntity`): 16 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:194-227` (`AtsEntity`): 25 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:233-255` (`UpsEntity`): 14 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:261-272` (`PfGlobalEntity`): 8 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:278-284` (`PfTransformerEntity`): 3 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:288-299` (`ProjectEntity`): 8 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:312-328` (`ConduitSettingsEntity`): 8 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:334-354` (`EarthingEntity`): 16 properties, **100% `val`**, zero `var`s.
  - `com/example/data/Entities.kt:371-388` (`BusTieEntity`): 8 properties, **100% `val`**, zero `var`s.
  - `com/example/engine/CableSizingEngine.kt:27-68` (`SizingResult`): 36 properties, **100% `val`**, zero `var`s.
  - `com/example/engine/ShortCircuitEngine.kt:9-25` (`ShortCircuitResult`): 15 properties, **100% `val`**, zero `var`s.
  - `com/example/engine/TransformerEngine.kt:7-30` (`TransformerResult`): 22 properties, **100% `val`**, zero `var`s.
- **Contract Verification:**
  - Because all fields in these models are declared as `val` and represent deeply immutable data, annotating them as `@Immutable` (or adding their packages to `app/compose_compiler_config.conf`) is **contractually truthful and safe**. There are no `var` or hidden mutable state members.

---

### H4 — `runBlocking` on the main thread
- **Status:** **PARTIALLY CONFIRMED**
- **Site 1: `com/example/ai/ui/EngineeringAssistantViewModelFactory.kt:54`**
  - **Status: CONFIRMED on Main Thread**
  - Lines 52-62:
    ```kotlin
    val gatewayLazy = lazy {
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            val catalogRepository = productionCatalogRepository(application)
            DurableMutationWorkflow.createGateway(
                database = database,
                catalogRepository = catalogRepository,
                principalProvider = DefaultTrustedPrincipalProvider(principalProvider)
            )
        }
    }
    ```
  - `approvalGatewayProvider = { gatewayLazy.value }` is passed into `EngineeringAssistantViewModelFactory.create`.
  - When the user opens `EngineeringAssistantSheet`, Android's `ViewModelProvider` instantiates the ViewModel on the UI Main Thread.
  - `lazy.value` executes `runBlocking(Dispatchers.IO)` on the Main Thread, parsing 7 catalog asset JSON files synchronously (`cable_catalog.json`, `cable_types_data.json`, `breaker_catalog.json`, `accessory_catalog.json`, `derating_data.json`, `transformers_seed.json`, and `busway_catalog.json`) while freezing the Main Thread. (Note: `installation_methods.json` was previously listed in error; the actual code parses these 7 files).
- **Site 2: `com/example/engine/coordinator/EngineeringCalculationCoordinator.kt:80-87`**
  - **Status: REJECTED as "dead code"**
  - Lines 80-87:
    ```kotlin
    private fun getProjectRevision(projectId: String): Long {
        val provider = projectRevisionProvider ?: return 1L
        return try {
            runBlocking { provider.getCurrentRevision(projectId).revision }
        } catch (_: Exception) {
            1L
        }
    }
    ```
  - **Verification:** `getProjectRevision` has **35 active call sites** within `EngineeringCalculationCoordinator.kt` (lines 178, 186, 194, 202, 210, 218, 247, 411, 431, 455, 464, 519, 546, 645, 661, 669, 691, 819, 847, 856, 908, 956, 968, 1071, 1131, 1156, 1164, 1199, 1266, 1291, 1405, 1428, 1436, 1484, 1507).
  - It is **NOT dead code**. Deleting it would break compilation across the entire engine coordinator.
  - However, when called from `uiState`, it executes under `.flowOn(Dispatchers.Default)`, which blocks a background worker thread rather than the Android UI Main thread.

---

### H5 — Exposed `MutableStateFlow`
- **Status:** **CONFIRMED**
- **Evidence:**
  - 9 public `MutableStateFlow` fields in `com/example/ui/EngineeringViewModel.kt`:
    1. Line 55: `val selectedTransformerId = MutableStateFlow("")`
    2. Line 56: `val selectedCircuitId = MutableStateFlow<Int?>(null)`
    3. Line 58: `val circuitSearchQuery = MutableStateFlow("")`
    4. Line 59: `val circuitStatusFilter = MutableStateFlow("All")`
    5. Line 60: `val circuitParentFilter = MutableStateFlow("All")`
    6. Line 196: `val projectError = MutableStateFlow<String?>(null)`
    7. Line 197: `val startupState = MutableStateFlow<StartupState>(StartupState.Initial)`
    8. Line 200: `val catalogReady = MutableStateFlow(false)`
    9. Line 896: `val pendingDeleteConfirmation = MutableStateFlow<EquipmentDeleteConfirmation?>(null)`
  - UI Call Sites Directly Mutating State:
    - `com/example/ui/MainLayout.kt:1207`: `viewModel.selectedCircuitId.value = circId`
    - `com/example/ui/screens/CircuitsGridScreen.kt:781`: `viewModel.selectedCircuitId.value = if (isSelected) null else circ.id`
    - `com/example/ui/screens/CircuitsGridScreen.kt:821`: `viewModel.selectedCircuitId.value = if (isSelected) null else circ.id`
    - `com/example/ui/screens/CircuitsGridScreen.kt:928`: `viewModel.selectedCircuitId.value = circ.id`
    - `com/example/ui/screens/CircuitsGridScreen.kt:1032`: `viewModel.selectedCircuitId.value = circ.id`
    - `com/example/ui/screens/TransformersScreen.kt:168`: `viewModel.selectedTransformerId.value = tx.transformerId`
    - `com/example/ui/screens/CircuitsGridScreen.kt:153`: `viewModel.circuitSearchQuery.value = it`
    - `com/example/ui/screens/CircuitsGridScreen.kt:174`: `viewModel.circuitSearchQuery.value = ""`
    - `com/example/ui/screens/CircuitsGridScreen.kt:329`: `viewModel.circuitSearchQuery.value = ""`
    - `com/example/ui/screens/CircuitsGridScreen.kt:234`: `viewModel.circuitStatusFilter.value = when (status) { ... }`
    - `com/example/ui/screens/CircuitsGridScreen.kt:331`: `viewModel.circuitStatusFilter.value = "All"`
    - `com/example/ui/screens/CircuitsGridScreen.kt:215`: `viewModel.circuitParentFilter.value = when (level) { ... }`
    - `com/example/ui/screens/CircuitsGridScreen.kt:330`: `viewModel.circuitParentFilter.value = "All"`

---

### H6 — Manifest / FileProvider security
- **Status:** **CONFIRMED**
- **Evidence for (a) — `VoltAtlasAppFunctionService`:**
  - `app/src/main/AndroidManifest.xml:1-38`: Contains only `MainActivity` and `CadFileProvider`.
  - `VoltAtlasAppFunctionService` (defined in `com/example/ai/appfunctions/VoltAtlasAppFunctionService.kt`) is **completely absent** from the manifest.
  - It lacks both the `<service>` declaration and `android:permission="android.permission.BIND_APP_FUNCTION_SERVICE"`.
- **Evidence for (b) — `file_paths.xml`:**
  - Full content of `app/src/main/res/xml/file_paths.xml`:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <paths xmlns:android="http://schemas.android.com/apk/res/android">
        <cache-path name="cache" path="." />
        <files-path name="files" path="." />
        <external-files-path name="external_files" path="." />
        <external-cache-path name="external_cache" path="." />
    </paths>
    ```
  - **Export Directory Audit:**
    - `com/example/sld/SldImageExporter.kt:304`: `File(context.cacheDir, "VoltAtlas_SLD.png")`
    - `com/example/sld/SldDxfExporter.kt:2117`: `File(context.cacheDir, "${cleanProjectName}_SLD.dxf")`
    - Both active FileProvider shares write directly to `context.cacheDir`. Neither writes to `filesDir` or external storage.
    - PDF export does not use FileProvider; it uses Android Storage Access Framework (`ActivityResultContracts.CreateDocument`).

---

### H7 — Adaptive layout APIs
- **Status:** **CONFIRMED**
- **Definition:** `com/example/ui/adaptive/VoltAtlasWindowWidth.kt:53`:
  ```kotlin
  @Composable
  fun rememberVoltAtlasWindowWidth(): VoltAtlasWindowWidth {
      val configuration = LocalConfiguration.current
      return remember(configuration) {
          val widthDp = configuration.screenWidthDp
          when {
              widthDp >= 840 -> VoltAtlasWindowWidth.Expanded
              widthDp >= 600 -> VoltAtlasWindowWidth.Medium
              else -> VoltAtlasWindowWidth.Compact
          }
      }
  }
  ```
- **Call Sites:**
  - `com/example/ui/MainLayout.kt:389`: `val useNavRail = com.example.ui.adaptive.rememberVoltAtlasWindowWidth().supportsNavRail`
  - `com/example/ui/screens/MoreModulesScreen.kt:120`: `val windowWidth = rememberVoltAtlasWindowWidth()`
- **Impact Assessment:**
  - Switching to official `WindowSizeClass` (`androidx.compose.material3:material3-window-size-class`) would require adding the dependency to `build.gradle.kts`.
  - The behavior at 600dp and 840dp breakpoints is mathematically identical. Replacing it without changing layouts is a pure API modernization.

---

### H8 — Focus / keyboard traversal
- **Status:** **CONFIRMED**
- **Evidence:**
  - `FocusRequester`: Exactly 1 instance in entire `app/src/main/java`:
    - `com/example/ai/ui/EngineeringAssistantSheet.kt:63`: `val inputFocusRequester = remember { FocusRequester() }`
  - `focusProperties`: 0 occurrences app-wide.
  - `onKeyEvent`: 0 occurrences app-wide.
  - `onPreviewKeyEvent`: 0 occurrences app-wide.
  - No keyboard navigation exists on any calculation input forms.

---

### H9 — Mandatory Test Baseline
- **Execution Command:** `.\gradlew.bat :app:testDebugUnitTest -Pprofile=gate --console=plain`
- **Result:**
  - **Total Discovered & Completed:** **2,241 tests**
  - **Passed:** **2,227 tests**
  - **Failed:** **1 test**
  - **Skipped:** **13 tests**

#### Name and Details of Failing Test:
```
com.example.engine.coordinator.CoordinatorNonBlockingExecutionTest > guardedCalculationsAwaitsMutexNativelyUnderCoroutine FAILED
    kotlinx.coroutines.test.UncaughtExceptionsBeforeTest at CoordinatorNonBlockingExecutionTest.kt:159
```
- **Root Cause Analysis (from Gradle test XML report `TEST-com.example.engine.coordinator.CoordinatorNonBlockingExecutionTest.xml` lines 59-72):**
  - The failure is NOT in the coordinator logic itself (this test passes 100% when run in isolation).
  - The exception stack trace reveals an uncaught coroutine leak in:
    ```
    Suppressed: java.lang.IllegalStateException: Dispatchers.Main was accessed when the platform dispatcher was absent and the test dispatcher was unset.
    at com.example.camera.nameplate.NameplateAnalyzer$processTextFrame$1.invokeSuspend(NameplateAnalyzer.kt:49)
    ```
  - In `com/example/camera/nameplate/NameplateAnalyzer.kt:49`, `withContext(Dispatchers.Main)` is invoked asynchronously. On the JVM, `Looper.getMainLooper()` is unmocked and no test dispatcher is active, leaking an `IllegalStateException` into the shared coroutine execution context. When `CoordinatorNonBlockingExecutionTest` starts later in the JVM process, `TestScope.enter()` detects that unhandled background exception and fails with `UncaughtExceptionsBeforeTest`.

#### Exact 13 Skipped Tests (Pre-agreed Intentional Exclusions in `gate` Profile):
1. `com.example.pdf.PdfReportTest` (1 test skipped — requires on-device graphics/fonts)
2. `com.example.stress.EngineStressTest` (6 tests skipped — heavy stress suite enabled under `-Pprofile=stress`)
3. `com.example.stress.FuzzStressTest` (1 test skipped — fuzz suite enabled under `-Pprofile=stress`)
4. `com.example.stress.StructuralStressTest` (5 tests skipped — scale suite enabled under `-Pprofile=stress`)
Total intentional skips = 13.

---

## Gate A′ Audit Findings & Baseline Corrections

### 1. Partial-Cache Risk & Non-Cached Verification Requirement
- **Finding:** In incremental Gradle runs (`33 actionable tasks: 1 executed, 32 up-to-date`), tasks and test classes with unchanged inputs are treated as up-to-date and skipped. This enabled the deterministic Robolectric Native graphics failure in `GlobalSettingsResponsiveTest` to pass silently during earlier Gate A and Gate B runs where only camera and manifest files were modified.
- **Rule:** Every baseline measurement or gate clearance run MUST be executed with `--rerun-tasks` (or preceded by `cleanTestDebugUnitTest`) to guarantee 100% genuine execution of all tests across all tasks. Earlier Gate A and Gate B numbers must be annotated as having been run with Gradle incremental checking.

### 2. `92ae4cf` Feature Commit Disclosure Gap
- **Finding:** Commit `92ae4cf` (`feat(quick): add camera nameplate scanner button to quick motor starter`) landed in response to an ad-hoc user chat request during Gate A review, bypassing the STOP-GATE protocol.
- **Audit:** An exhaustive code audit confirmed that `QuickMotorStarterScreen.kt:388-398` instantiates `EquipmentNameplateScannerSheet`, which provides manual editing text fields over a decorative viewfinder box. It does **not** construct `NameplateAnalyzer`, does **not** implement `NameplateScanListener`, and does **not** open a live CameraX stream. A′-1 remains 100% sound: zero production construction sites or callers exist.

### 3. `NameplateAnalyzer` Dead-Code Status
- **Finding:** `com.example.camera.nameplate.NameplateAnalyzer` has 0 production call sites and 0 production listeners in `app/src/main/java`. It is unreachable dead code in production.

### 4. Legacy Skill Audit Distrust Rule
- **Finding:** The original skill audit claimed both `camerax` and `appfunctions` as "✅ Already Applied". Both claims were proven false: CameraX dependencies are commented out in `build.gradle.kts:142-145`, and `VoltAtlasAppFunctionService` was absent from `AndroidManifest.xml`.
- **Rule:** The legacy table of "17 skills already applied" is fundamentally untrustworthy. No skill from that list may be used to justify architectural scope without independent `file:line` verification.

### 5. `GlobalSettingsResponsiveTest` Deterministic Incompatibility
- **Finding:** `GlobalSettingsResponsiveTest.kt:77, 137, 187` failed 100% deterministically under Robolectric Native graphics due to `performScrollToNode(hasText("4.0%"))` inducing an infinite idling loop with `SubcomposeLayout` inside `BoxWithConstraints` in a `LazyColumn`.
- **Resolution:** Re-classified as a deterministic environment incompatibility (not flaky) and resolved via `performScrollToIndex(1)` in commit `14a49c8`, preserving 100% of subsequent content assertions.

### 6. P0 Engineering-Truth Defect in `92ae4cf` & Revert
- **Finding:** Commit `92ae4cf` shipped an affordance labelled 'Camera OCR' that displayed a fake decorative viewfinder and pre-filled inputs with fabricated constants ('37.0' kW, '400' V, '68.5' A). On confirm, it displayed a toast claiming a nameplate had been applied. This constituted a P0 violation of the engineering-truth invariant (never presenting synthesised or unverified values as captured engineering input).
- **Disposition:** Reverted on `main` in commit `f27be65` and preserved on branch `feat-motor-scanner-save`.
- **Verified Baseline of Record:** With `92ae4cf` reverted, the verified baseline on `main` is established at **2,244 total / 2,231 passed / 0 failed / 13 skipped (100% success rate)** via a forced non-cached `--rerun-tasks` run (`33 actionable tasks: 33 executed`).

---

## Gate B Audit Findings & Architectural Resolution — VOID (Premise Collapsed)

### 1. `VoltAtlasAppFunctionService` Premise Collapse & Revert
- **Finding:** `VoltAtlasAppFunctionService.kt:13-29` extends standard `android.app.Service` directly rather than `android.app.appfunctions.AppFunctionService` (Android 16 framework) or `androidx.appfunctions.service.AppFunctionService`. It overrides `onBind()` to return an internal `LocalBinder` and has no `onExecuteFunction()` override. Annotations `@AppFunction` and `@AppFunctionSerializable` are local mock declarations (`VoltAtlasAppFunctions.kt:18-29`).
- **Evaluation:** The service is a local in-process simulation shell. Declaring it in `AndroidManifest.xml` under the official framework `SERVICE_INTERFACE` action advertises a capability the code cannot honour, creating a reachable crash/failure at the platform IPC boundary on API 36+ devices.
- **Disposition:** Reverted commit `05bc26c` on `main` in commit `a84e471` (preserving the commit on branch `gate-b-save`). Removed `<service>` block from `AndroidManifest.xml` and deleted `VoltAtlasAppFunctionServiceTest.kt`.
- **Verdict:** **VOID — premise collapsed.**
- **Backlog Prerequisite:** *AppFunctions requires adding `androidx.appfunctions` + its KSP processor, rewriting `VoltAtlasAppFunctionService` to extend the real `AppFunctionService` and implement `onExecuteFunction`, and replacing the mock annotations. Only then does the manifest declaration become meaningful.* (Not scheduled; moved to feature backlog).
- **Verified Baseline of Record:** **2,244 total / 2,231 passed / 0 failed / 13 skipped (100% success rate)** via forced non-cached `--rerun-tasks` run (`33 actionable tasks: 33 executed`).



