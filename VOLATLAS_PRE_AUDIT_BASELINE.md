# VOLATLAS_PRE_AUDIT_BASELINE.md

Phase 0 — Baseline Preservation. Captured BEFORE any production code modification.
Repository is the authority. All values below were read directly from the repository on 2026-09-30.

## 1. Repository status

| Item | Value |
|---|---|
| Branch | `main` |
| HEAD commit | `abd3110c8db625750b4088ef438a679a89b9f900` (`feat(ui): implement adaptive multi-device layouts and collapsible app bars`) |
| `git status --porcelain` | EMPTY (clean working tree; ignored artifacts exist, see §8) |
| `git diff` | EMPTY (no uncommitted changes) |
| Recent history | `3076bc1` fix(nameplate), `7d3799d` build(release): refuse debug keystore, `9907047` fix(ai) context-blocked card, `3c6b6a9` docs remediation status |

## 2. Repository structure

- Single Gradle module: `:app` (`settings.gradle.kts`, rootProject `VoltAtlas`).
- Kotlin source roots: `app/src/main` (production), `app/src/test` (unit tests), `app/src/androidTest` (instrumentation).
- Top-level docs: `README.md`, `SKILL_AUDIT_VERIFICATION_REPORT.md`, `docs/` (prior audit archives: `audit_defect_report/` AUDIT-1..5, `ai_audit/` 00-15, `local_integration/` 00-08, `performance_audit/`, `build_upgrade/`, `archive/`).
- No `VOLATLAS_*` deliverable documents existed prior to this audit.

## 3. Production file inventory (tracked source)

| Area | Kotlin files |
|---|---|
| `app/src/main` | 350 |
| `app/src/test` | 281 |
| `app/src/androidTest` | 2 |
| Total tracked `.kt` lines (main+test) | ~189,313 |

Main packages (`com/example/*`): `ai` 155, `ui` 109, `sld` 26, `engine` 24, `data` 16, `pdf` 7, `camera` 4, `navigation` 4, `boot` 3, `hierarchy` 1.

Test packages: `ui` 85, `ai` 76, root-level 52, `sld` 41, `engine` 10, `data` 5, `pdf` 4, `stress` 3, `camera` 3, `navigation` 1, `testing` 1.

Largest production files (lines): `SldDxfExporter.kt` 2157, `PowerSystemsScreen.kt` 2116, `EngineeringCalculationCoordinator.kt` 2002, `RiserLayoutEngine.kt` 1854, `PanelScheduleScreen.kt` 1642, `CircuitsGridScreen.kt` 1479, `EngineeringRepository.kt` 1376, `ElectricalNetworkGraphBuilder.kt` 1373, `CircuitEditDialog.kt` 1343, `QuickCableCalcScreen.kt` 1324, `QuickNewToolsModels.kt` (quick tools engine file), `EngineeringViewModel.kt` 1192.
Thresholds (all src): >500 lines: 102 files; >800: 52; >1000: 37; >1500: 9; >2000: 4.

## 4. Gradle / build configuration

- Gradle wrapper: 9.7.1; Launcher JVM 21.0.11; `gradlew.bat --version` EXECUTED OK.
- AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.06.01, KSP plugin 2.3.12, Room 2.8.5, Coroutines 1.11.0, Robolectric 4.16.1, Roborazzi 1.59.0 (from `gradle/libs.versions.toml`).
- `compileSdk 36 (minorApiLevel 1)`, `minSdk 24`, `targetSdk 36`, `applicationId com.voltatlas.app.v1`, versionCode 2, versionName 1.1.
- Java compatibility: 11.
- `settings.gradle.kts`: FAIL_ON_PROJECT_REPOS; repos google() + mavenCentral() + gradlePluginPortal(); foojay resolver 1.0.0.
- BuildConfig fields: `GEMINI_API_KEY` (empty), `ENABLE_CLOUD_LLM` (false).
- Secrets plugin reads `.env` (gitignored) with `.env.example` default; `GEMINI_API_KEY` on ignoreList.
- `google-services` plugin is commented out (not applied).
- `org.gradle.configuration-cache=true`, caching=true, parallel=true, workers.max=4, Kotlin in-process compiler.

## 5. Release / signing / lint

- Release: `isMinifyEnabled=true`, `isShrinkResources=true`, `isCrunchPngs=false`, proguard-android-optimize + `proguard-rules.pro`.
- Signing: `keystore.properties` (gitignored) or env vars; explicit DEBUG-KEYSTORE REFUSAL (name `debug.keystore` / alias `androiddebugkey` rejected) plus a `gradle.taskGraph.whenReady` hard-fail that throws `GradleException` if release packaging is attempted without a valid non-debug keystore.
- Lint: `checkReleaseBuilds = false`, `abortOnError = false`, `checkDependencies = false` (AUDIT-R candidate; NOT changed in Phase 0).

## 6. Test profiles (verbatim from `app/build.gradle.kts`)

Selected via `-Pprofile=` (or `-PtestProfile=`), default `fast`:
- `fast` / `dev` — excludes `com.example.ai.*`, screenshot tests, stress suites, and 3 STAGE-B0 diagnostic suites.
- `ai` — includes only `com.example.ai.*`.
- `stress` — includes named stress/scale/fuzz suites only.
- `gate` — ALL tests except the 3 STAGE-B0 diagnostic suites (includes AI).
- `diagnostic` — ONLY the 3 quarantined STAGE-B0 suites.
- `full` / `all` — everything, no exclusions.
- Unknown profile → `GradleException`.
- Quarantined (documented, not silent): `LegacyCircuitEditDialogSaveMappingCharacterizationTest`, `LegacyCircuitEditDialogVisibilityCharacterizationTest`, `CircuitEditDialogDiagnosticIsolationTest` (Robolectric Compose-idle issue; owner documented in build file comments).
- `testDebugUnitTest` dependsOn `generateReleaseBuildConfig`.

## 7. Room / schema

- `com.example.data.AppDatabase`: `version = 26`, `exportSchema = false`, 25 inline migrations (`MIGRATION_*`) in `AppDatabase.kt` (1116 lines).
- No exported schema JSON directory in-repo (exportSchema=false) → Room migration tests must use in-repo migration objects.

## 8. Generated / build artifacts (present, git-ignored)

`build/`, `app/build/`, `.gradle/`, `.kotlin/`, `build_logs/`, `a1r.log`, `probe.log`, `app/test_output*.txt`, `local.properties` (sdk.dir → `C:\Users\mahmoud.mohamed\android-sdk`). Keystore material git-ignored (`*.jks`, `keystore.properties`).

## 9. Protected files (confirmed present, untouched)

- `app/src/main/java/com/example/ui/dialogs/circuiteditor/ui/CircuitEditorParallelEntry.kt`
- `app/src/main/java/com/example/ui/dialogs/circuiteditor/ui/CircuitEditorShell.kt`
- `app/src/test/java/com/example/ui/dialogs/circuiteditor/ui/CircuitEditorSessionRuntimeTest.kt`
- Protected engine package `com.example.engine.*`: 24 production files (incl. `coordinator/*` services and `quick/QuickEngineeringDataBridge.kt`).

## 10. Current AI architecture (as wired in source)

- Canonical composition root: `com.example.ai.hybrid.ConversationEngineProvider.provideConversationEngine(...)` (object, `ConversationEngineProvider.kt`).
  - Builds `LocalRuleEngineAdapter(ConversationPolicyEngine)` + conditional `CloudLlmConversationEngine` (or `DisabledCloudLlmEngine`) → returns `HybridConversationRouter(cloudEngine, localEngine, connectivityProbe, cloudTimeoutMs)`.
  - **Observed at baseline:** the `HybridConversationRouter` construction at line 121 does NOT pass `nanoEngine`; `OnDeviceNanoConversationEngine` has no production construction site (Audit L hypothesis).
  - **Observed at baseline:** `kotlinx.coroutines.runBlocking { provider() }` at line 106 inside the `orchestratorProvider` lambda (Audit K hypothesis).
- Consumers: `EngineeringAssistantViewModel` (line 147) and `EngineeringAssistantViewModelFactory` (line 97).
- Agentic loop: `com.example.ai.orchestrator.AgenticToolLoopOrchestrator` wired with `AssistantToolRuntime` dispatcher + descriptor registry.
- `FirebaseAiServiceImpl` (`ai/service/`) has no production reference beyond its own declaration (Audit M hypothesis).
- Build flags: `ENABLE_CLOUD_LLM=false`, empty `GEMINI_API_KEY` at baseline.

## 11. Current engineering architecture (as wired in source)

- Authoritative engines under protected `com.example.engine.*`: `EarthingEngine`, `ConduitFillEngine`, `GeneratorEngine`, `UpsEngine`, `AtsEngine`, `AtsIscResolver`, `CableSizingEngine`, `ShortCircuitEngine`, `TransformerEngine`, `ProtectionEngine`, `CoordinationEngine`, `BusbarSizingEngine`, `BuswayEngine`, `DiversityCalculationEngine`, `PfCorrectionEngine`, `OtherEngines`, `converters/ElectricalConverters`.
- Aggregate orchestration: `engine/coordinator/EngineeringCalculationCoordinator` (2002 lines) + services (Cable, Coordination, Protection, ShortCircuit, Transformer).
- Data layer: `data/EngineeringRepository` (1376), `data/AppDatabase` (Room v26), `engine/quick/QuickEngineeringDataBridge`.
- Quick calculators live in UI layer: `ui/screens/quick/QuickNewToolsModels.kt` (contains `QuickEarthingEngine`, `QuickConduitFillEngine`, `QuickBreakerSelectEngine` + `FALLBACK_BREAKERS`, `QuickGeneratorEngine`, `QuickUpsEngine`, `QuickAtsEngine`, `QuickMotorStarterEngine`, `FALLBACK_CONDUITS`).
- SLD: `sld/ElectricalNetworkGraphBuilder` (1373), `sld/SldLabelFieldLibrary`, `sld/SldDxfExporter` (2157), `sld/RiserLayoutEngine` (1854).
- UI state: `ui/EngineeringViewModel` (1192).

## 12. Baseline test execution status

- Command initiated at baseline (Phase 0): `gradlew.bat testDebugUnitTest -Pprofile=fast --console=plain`.
- Result recorded in §19 of the final report and in `VOLATLAS_POST_AUDIT.md` (PRE-run). **No test result is claimed until the run output is read.**
- Environment supports builds: Gradle 9.7.1 + JDK 21 + Android SDK path present. Build infra available → builds/tests SHOULD be executed (not `BUILD_NOT_EXECUTED`) unless a run fails for environmental reasons.

## 13. Environment constraints affecting this audit

- `glob`/`grep` (ripgrep) tools fail in this workspace ("ripgrep execution failed") → file discovery done via Python scripts + `read` tool.
- Shell wrapper mangles inline quotes → scripts written to `C:\Users\mahmoud.mohamed\AppData\Local\Temp\opencode\` and executed as `python <script>`.
- PowerShell is non-functional (echoes input); `find`/`findstr` unreliable on some patterns.
- `git grep`, `git ls-files`, `dir`, `type`, `read` tool are reliable.

## 14. Phase 0 compliance

- No production code modified in Phase 0. Working tree remains clean (`git status` empty after Phase 0 except ignored artifacts and audit markdown deliverables created by this task).
