# Track A Close-out Report — Build Upgrade (Gradle / AGP / AndroidX)

Date: 2026-09-15
Branch: `integration/home-snapshot-2026-09-13`
Commits under review: `34556c0` (Stage 1 toolchain), `1567964` (Stage 2 libraries)
This report commit: Stage 1 property addendum + Stage 2 catalog neutralisation (see §1 and §3).

---

## 1. Gradle distribution provenance (close-out defect §1)

### 1.1 Correction of the earlier statement

The previous report said the SHA-256 was verified "against the official services.gradle.org
checksum" while also saying `services.gradle.org` was unreachable. That wording was **wrong**
and is corrected here:

- The `…-bin.zip.sha256` path was **never** used. Re-tested on the build machine:
  `curl -s https://services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256`
  returns **HTTP 301 Moved Permanently (cloudflare)** — the checksum path is redirected just
  like the main ZIP, so it is not directly reachable here either.
- The checksum actually used came from the **official Gradle release metadata**:
  `https://services.gradle.org/versions/current` → field `"checksum"`, retrieved over a
  network path independent of the mirror. It was fetched twice in-session with identical
  results, and independently cross-checked against
  `https://gradle.org/release-checksums/` ("9.7.1 Binary-only (-bin) ZIP Checksum").
- The **mirror was only the transport** (`mirrors.cloud.tencent.com`, the only reachable
  route, since the official URL 307-redirects to github.com which is blocked on this machine).
  It is *not* the verification source — so this is not self-attestation.

### 1.2 Full hashes, side by side

| Item | Value |
|---|---|
| Computed (mirror download, `%TEMP%\gradle-971.zip`, 151,433,392 bytes) | `ACD53F1EDAF02F1A8FF99879F8A34B302661A057D9B063AE9E35B552F804D20A` |
| Computed (installed wrapper-cache copy, same size) | `ACD53F1EDAF02F1A8FF99879F8A34B302661A057D9B063AE9E35B552F804D20A` |
| Expected — `services.gradle.org/versions/current.checksum` | `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` |
| Expected — `gradle.org/release-checksums/` (9.7.1 -bin ZIP) | `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` |

Independent corroboration: the installed distribution reports
`Revision: 92f0512e7f06d84621afba191f75e265363890cf`, which equals the `commitId` published in
`versions/current` for 9.7.1 — the mirror artifact is the genuine official build.

### 1.3 Permanent enforcement (added)

`gradle/wrapper/gradle-wrapper.properties` now contains:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
```

### 1.4 Verification that enforcement actually works (deterministic, offline)

Raw byte-scanning of a JAR is invalid (entries are deflate-compressed), so extracted classes
were scanned after `Expand-Archive`:

- **B1 (code present):** the repository's `gradle-wrapper.jar` (sha256
  `497C8C2A7E5031F6AA847F88104AA80A93532EC32EE17BDB8D1D2F67A194A9C7`, 32 classes) contains the
  string `distributionSha256Sum` in `WrapperConfiguration.class`, `WrapperExecutor.class` and
  `Install.class`, and the failure message in `Install.class`.
- **B2 (enforcement fires):** an isolated wrapper project using a `file:///…/gradle-971.zip`
  URL with a deliberately corrupted sum (last hex digit `b` instead of `a`):

  ```
  java.lang.RuntimeException: Verification of Gradle distribution failed!:
  Expected checksum: 'acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20b'
  Actual checksum:   'acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a'
  at org.gradle.wrapper.Install.verifyDownloadChecksum
  ```

  This both proves enforcement and independently re-derives the official hash from the artifact.
- **Positive path:** with the official sum and the mirror URL, the wrapper downloaded, verified
  and started: `Welcome to Gradle 9.7.1!` / `Gradle 9.7.1`.

### 1.5 Related finding (not changed here — out of scope)

The repository's `gradle-wrapper.jar` matches the **wrapper JAR checksum published for
Gradle 9.5.0 / 9.5.1 / 9.6.0 / 9.6.1** (`497C8C2A…94A9C7`), not 9.7.1's (`7a9ce74c…62c5d`).
The property is fully supported by the current JAR (proven in §1.4), so enforcement is real
today; aligning the JAR hash with the 9.7.1 distribution is recommended as a separate,
reviewable follow-up (it is a wrapper-binary change, not a dependency bump).

---

## 2. Corrected Track A completion claim (close-out defect §2)

**Corrected statement of outcome:**

> **Toolchain upgrade complete. Library scope reduced** — because a large share of 2026-era
> androidx artifacts are coupled to `compileSdk 37` and/or pull Compose off the BOM-pinned
> 1.7.x line. A pinned-back library is **not** an upgrade and is not reported as one.

### 2.1 Actually upgraded and in effect (resolved, not merely declared)

| Component | Before | Now | Resolved proof |
|---|---|---|---|
| Gradle wrapper | 9.3.1 | **9.7.1** | `gradlew --version` → `Gradle 9.7.1`, revision `92f0512e…` |
| AGP | 9.1.1 | **9.4.0** | official docs: "Android Gradle plugin 9.4.0 (September 2026)" |
| Kotlin | 2.2.10 | **2.4.20** | zero KGP/KSP version warnings |
| KSP | 2.3.5 | **2.3.12** | pairing verified clean (no fallback to Kotlin 2.3.21 needed) |
| Room (`room-runtime`/`room-ktx`/`room-compiler`) | 2.7.0 | **2.8.5** | `androidx.room:room-runtime:2.8.5`, `room-ktx:2.8.5` |
| kotlinx-coroutines (`core`/`android`/`test`) | 1.10.2 | **1.11.0** | `kotlinx-coroutines-core:1.7.3 -> 1.11.0` |
| OkHttp + logging-interceptor | 4.10.0 | **4.12.0** | `com.squareup.okhttp3:okhttp:4.12.0` |
| Retrofit + converter-moshi | 2.12.0 | **3.0.0** | `com.squareup.retrofit2:retrofit:3.0.0` |
| Firebase BOM | 34.15.0 | **34.19.0** | `com.google.firebase:firebase-bom:34.19.0` |
| core-splashscreen | 1.0.1 | **1.2.0** | `androidx.core:core-splashscreen:1.2.0` |

**Retrofit rationale (recorded — it strengthens the choice):** Retrofit 3.0.0's only change
from the 2.x line is baselining **OkHttp 4.12** (up from 3.14), and 3.x preserves forward
binary compatibility with 2.x. It therefore pairs correctly with the mandated
`okhttp 4.12.0` pin and is **not** a behavioural major bump.

### 2.2 Planned for Track A but pinned back / reverted / deferred

| Component | Track A plan | Final value | Why |
|---|---|---|---|
| `core-ktx` | 1.19.0 | **1.18.0** | 1.19.0 requires `compileSdk 37` (Gate 2 AAR-metadata failure) |
| `lifecycle-runtime-ktx` / `-viewmodel-compose` / `-runtime-compose` | 2.11.0 | **2.8.7** | 2.11.0 compose artifacts require `compileSdk 37`; 2.10+ publishes constraints aligning Compose to 1.9.0 |
| `activity-compose` | 1.13.0 | **1.10.1** | 1.12.0/1.13.0 → `navigationevent-compose:1.0.0` → `compose.runtime:1.9.2` |
| `navigation-compose` | 2.10.1 | **2.8.9** | 2.10.1 → `compose.animation:1.10.5` → Compose group escalated to 1.9.2 |
| `okhttp` / `logging-interceptor` | 5.5.0 | **4.12.0** | OkHttp 5.x Android artifact requires `compileSdk 37` |
| `roborazzi` | 1.74.0 | **1.59.0** | produced 23–67% pixel deltas on all 9 goldens with Compose held constant (see §4) |
| `datastore-preferences` | 1.2.1 | **1.1.7** | dependency is commented out in `app/build.gradle.kts`; only a transitive 1.1.7 exists (see §3) |

### 2.3 Track A regression evidence (unchanged, accepted)

| Run | Toolchain | Result |
|---|---|---|
| Full unit suite | old (9.3.1 / AGP 9.1.1 / Kotlin 2.2.10) | 1335 tests, 1 failed, 1 skipped |
| Full unit suite | new (9.7.1 / AGP 9.4.0 / Kotlin 2.4.20 / KSP 2.3.12) | 1335 tests, 1 failed, 1 skipped |
| `Step7LightModeGoldenTest` (9) | new | **0 pixel diffs** |
| Failing test in isolation | old **and** new | PASS (pre-existing isolation defect — §5) |

---

## 3. Inert catalog entries neutralised (close-out defect §3)

> **Supersedes commit 1567964**, whose commit message names CameraX 1.6.2 / credentials 1.6.0 /
> play-services-location 21.4.0 / datastore 1.2.1 — these were reverted in `42dec21` and were
> **never compiled or tested** (their dependencies are commented out in `app/build.gradle.kts`,
> so they were also never verified against `compileSdk 37`).

`app/build.gradle.kts` keeps CameraX (×4), credentials, play-services-location, Coil,
Navigation and datastore **commented out** (lines 142–145, 154, 158, 161, 175–177, 186).
Their catalog versions were raised in Stage 2 and described as "harmless". They are not
harmless by default: Gate 2 proved that 2026-era androidx artifacts (`core-ktx 1.19.0`,
`lifecycle 2.11.0`) require `compileSdk 37`, and any of these could be in the same class —
and none of them was ever compiled or tested.

**Option (a) — preferred — was applied: the values were reverted so the catalog reflects only
what was actually built and verified.**

| Catalog entry | Raised to (Stage 2) | Reverted to (now) | Status |
|---|---|---|---|
| `cameraCamera2` | 1.6.2 | **1.5.0** | NOT upgraded — dependency commented out, never compiled |
| `cameraLifecycle` | 1.6.2 | **1.5.0** | NOT upgraded — same |
| `cameraView` | 1.6.2 | **1.5.0** | NOT upgraded — same |
| `cameraCore` | 1.6.2 | **1.5.0** | NOT upgraded — same |
| `credentials` | 1.6.0 | **1.5.0** | NOT upgraded — dependency commented out |
| `playServicesLocation` | 21.4.0 | **21.3.0** | NOT upgraded — dependency commented out |
| `datastorePreferences` | 1.2.1 | **1.1.7** | NOT upgraded — declared nowhere; graph resolves 1.1.7 transitively via Room |
| `coilCompose` | (unchanged 2.7.0) | **2.7.0** | NOT upgraded — dependency commented out; already latest for `io.coil-kt` |
| `navigationCompose` | (reverted in Stage 2) | **2.8.9** | NOT upgraded — see §2.2 |

Verification that this neutralisation is inert: `:app:assembleDebug` → **BUILD SUCCESSFUL in
3s, 39 actionable tasks: 39 up-to-date**, and the resolved dependency graph is the same set as
the accepted Track A state (Room 2.8.5, core-ktx 1.18.0, compose-ui 1.7.0, lifecycle 2.8.7,
OkHttp 4.12.0, Retrofit 3.0.0, coroutines 1.11.0, Robolectric 4.16.1, Roborazzi 1.59.0,
splashscreen 1.2.0, firebase-bom 34.19.0). The golden suite was re-run on this final state:
**BUILD SUCCESSFUL, 0 diffs**.

---

## 4. Roborazzi 1.59.0 → 1.74.0 — diagnosis (close-out defect §4)

With Compose held fully constant at the BOM-pinned 1.7.x line, the Roborazzi bump alone produced
deltas of **23.6%–67.5% of pixels on all 9 goldens**. A delta of that magnitude from a screenshot
library with a constant rendering stack indicates a **changed default / changed capture path**,
not visual drift. Investigation used primary sources: the published `roborazzi-core` **sources
JARs** for 1.59.0 and 1.74.0 from Maven Central, plus Roborazzi release notes.

### 4.1 Ruled out — image comparison / resize policy is unchanged

| Item (default) | 1.59.0 | 1.74.0 |
|---|---|---|
| `CompareOptions().imageComparator` | `DefaultImageComparator = SimpleImageComparator(maxDistance = 0.007F)` | identical |
| `CompareOptions().resultValidator` | `DefaultResultValidator = ThresholdValidator(0F)` | identical |
| `CompareOptions()` default instance | `CompareOptions()` | identical |
| `resizeScale` default | `roborazziDefaultResizeScale()` → system property `roborazzi.record.resizeScale`, default `"1.0"` | identical |
| `RoborazziOptions` source diff | — | **purely additive**: `uiTreeDumpOptions` + `@Deprecated(HIDDEN)` binary-compat bridges for ≤1.68.0 |

Roborazzi's own release note for the UI-tree-dump feature states the sidecar "never participates in
image diffing and never fails verification", and the 1.69.0-era note records no behavioural change.
So the compare/resize policy is **not** the cause — and a "re-record and move on" step would be
adopting a capture-convention change silently.

### 4.2 Identified area — the capture path (capture-type resolution + window compositing)

New files present in 1.74.0 that do **not** exist in 1.59.0, in the same module:

- `CanScreenshot.kt`
- `DefaultCaptureType.kt`

The default is expressed identically in both versions as:

```kotlin
val captureType: CaptureType = if (canScreenshot()) CaptureType.Screenshot() else defaultCaptureType(),
```

Any change to the resolution of `canScreenshot()` / `defaultCaptureType()` flips capture between a
screen **Screenshot** and a tree **Dump**, which changes essentially every pixel of the output —
consistent with the observed 23–67% deltas across every screen.

Independently, the 1.74.0 release notes describe a **rendering-path change** in
`captureScreenRoboImage()`: "`captureScreenRoboImage()` composites every window onto one canvas,
and it placed all of them against the screen… Sub-windows are now placed against their parent
window… an unresolvable parent falls back to the previous screen-relative behavior" (fixes #921,
PR #922). This alters composition for captures involving multiple windows (dialogs, popups,
dropdowns) — relevant because several VoltAtlas screens render dialogs/overlays.

### 4.3 Status and required Track B procedure (do NOT re-record blindly)

Deferring the bump was correct. Track B must identify the mechanism first, because a config-induced
delta may be neutralisable by explicit configuration — preserving existing baselines — whereas a
silent re-record would permanently adopt a new rendering convention with no reviewable
justification (violating the Gate 3 rule that every visual delta needs a written explanation).

Bounded Track B procedure:

1. **Resolve the Android/JVM actuals.** The `roborazzi-core` sources JAR ships common + iOS source
   sets only (verified: `CanScreenshot.kt` / `DefaultCaptureType.kt` appear under `iosMain`; the
   1.74.0 options file is `RoborazziOptions.common.kt`). Retrieve
   `io.github.takahirom.roborazzi:roborazzi:1.74.0-sources` (and `…:1.59.0-sources`) and diff the
   **Android/JVM** `actual fun canScreenshot()` / `actual fun defaultCaptureType()` against 1.59.0.
2. **A/B capture probe on one screen.** Temporarily set only `roborazzi = "1.74.0"`, run one golden
   test (e.g. `dashboard_passState_light_matchesGolden`) with `--rerun-tasks`, and record: resolved
   `captureType`, whether output is a screen screenshot or a view/tree dump, and image dimensions
   versus the golden's `360x800`. Do not commit that state.
3. **Check the window-compositing change** on screens rendering dialogs/popups (applies even if the
   capture type is unchanged).
4. **Decide explicitly** between (a) pinning configuration (e.g. explicit `captureType`) to preserve
   the current baselines, or (b) a justified re-record committed **separately** from the dependency
   bump, with before/after pairs for all 9 cases.

---

## 5. Test isolation defect — **RESOLVED** (was close-out defect §5)

**ID:** `VOLT-TESTISO-001` — global `Catalog` state leaks across test classes sharing one JVM.

**Status: FIXED (test-only change; no production/engine code touched).**

### 5.1 Fix applied

1. **New reusable rule** — `app/src/test/java/com/example/testing/CatalogStateResetRule.kt`:
   `CatalogStateSnapshot` (immutable capture of every mutable `Catalog` field: `cableTypes`,
   `dataSheet`, `cableCatalog`, `breakers`, `accessories`, `conduits`, `busways`,
   `deratingData`, `lastError`), `captureCatalogState()` / `restoreCatalogState()` (restore also
   calls `Catalog.invalidateIndexes()`), and `CatalogStateResetRule` (JUnit4 `ExternalResource`
   usable as `@ClassRule` or `@Rule`). Restoring the entry state keeps classes that load the
   catalogue in their own setup working unchanged, while guaranteeing that a class hands back
   exactly the state it received on exit.
2. **Class-level guard (`@JvmField @ClassRule`) added to the 13 `Catalog`-mutating test classes
   that had no restoration hook** (CableSizingEngineTest, EngineFixesTest, Phase3RemediationTest,
   QaTestSuite, QuickCableCalcTest, QuickEngineeringBatch1IntegrationTest,
   QuickEngineeringDataBridgeTest, VoltageDropV2CalculationTest, VoltageDropV2ProvenanceTest,
   OperationalFreshnessIntegrationTest, EngineeringCalculationCoordinatorTest, EngineStressTest,
   FuzzStressTest — those without an existing `companion object` got one). The remaining
   Catalog-mutating classes (CatalogLoaderTest — restores in `finally`; AllEngines/Master stress
   tests, ProjectContextProviderTest, PostCommitRevalidationOrchestratorTest,
   CableEngineeringToolsTest, CoordinatorNonBlockingExecutionTest, RevisionCorrectnessTest —
   restore in `@After`) were left as-is: they already hand back their state, and widening the diff
   further was out of scope.
3. **Victim class made hermetic** — `CircuitEditorInitializerSpecTest` got the rule plus a
   `@Before` that installs its own fixture into the global `Catalog.cableTypes` (the same types it
   already passes as `catalogSnapshot`), so `resolveCableType()` sees deterministic input.
4. **Hidden pollution dependency surfaced and fixed** — the guard exposed that
   `EngineeringCalculationCoordinatorTest` had been *inheriting* the real catalogue (breakers,
   derating data, full cable set) leaked from earlier classes: its `setUp()` installed only a
   minimal fixture **conditionally** (`if (Catalog.cableTypes.isEmpty())`), and three of its tests
   (`testCoordinatorTargetedCoordinationResolvesRealUpstreamRating`,
   `testCoordinatorTargetedProtectionAssemblesDependencyBranch`,
   `testCoordinatorCoordinationCacheReusesAndInvalidatesOnUpstreamChange`) failed once that
   inheritance was cut off. Its `setUp()` now **unconditionally installs the real application
   catalogue from `src/main/assets`** (cable_types, data_sheet, cable_catalog, breaker_catalog,
   derating_data — mirroring the parsing used by `QaTestSuite` and the production loader), making
   the class deterministic.

**Not done (explicitly forbidden):** no test reordering, no re-running-until-green, no `@Ignore`,
no engine/production changes.

### 5.2 Before/after evidence

| Command | Before fix (`42dec21`) | After fix |
|---|---|---|
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks` (full suite) | **BUILD FAILED** — 1335 tests, **1 failed** (`CircuitEditorInitializerSpecTest.test01`), 1 skipped (7m12s) | **BUILD SUCCESSFUL** — 1335 tests, **0 failed**, 1 skipped (7m50s) |
| `.\gradlew.bat :app:testDebugUnitTest --tests "*CircuitEditorInitializerSpecTest" --rerun-tasks` | PASS | PASS |
| `.\gradlew.bat :app:testDebugUnitTest --tests "*EngineeringCalculationCoordinatorTest" --rerun-tasks` | PASS (via leaked state) | **PASS** (self-sufficient) |

Engine numeric outputs: unchanged — the diff touches files under `app/src/test/**` only (one new
test utility + 14 test files); no production or engine source file is modified, and every engine
suite (`*Engine*`, `*Quick*`) passes in the green full-suite run.

**Track B Stage 3 prerequisite P1 is now satisfied.**

---

## 6. Track B backlog (deferred items, prerequisites, gates)

**Hard prerequisites before Stage 3 re-record:**

| # | Prerequisite | Detail |
|---|---|---|
| P1 | ~~Fix `VOLT-TESTISO-001` and get the full suite green~~ → **RESOLVED** | Fixed by the test-isolation commit following `42dec21` (see §5): full suite green at 1335/0/1. Prevents polluted state being recorded into golden baselines. Also note: the catalog entries raised by commit 1567964 (CameraX 1.6.2 / credentials 1.6.0 / play-services-location 21.4.0 / datastore 1.2.1) were reverted in `42dec21` and were **never compiled or tested** — see §3. |
| P2 | Complete the Roborazzi mechanism diagnosis (steps 1–3 of §4.3) | prevents a config-induced delta being silently accepted as new truth |
| P3 | Owner approval for re-recording goldens | Gate 3 requires explicit acceptance |

**Deferred version bumps (all require `compileSdk = 37` / `targetSdk = 37` unless noted):**

| Component | Current | Deferred target | Coupling note |
|---|---|---|---|
| `compileSdk` / `targetSdk` | 36 / 36 | **37** | AGP 9.4.0 already supports max API 37; Gradle 9.7.1 ≥ required 9.6.0 |
| `composeBom` | 2024.09.00 | **2026.09.00** (Compose UI 1.12.0) | Compose 1.12 updated its compileSdk to API 37 → min AGP 9.2.0 (satisfied) |
| `core-ktx` | 1.18.0 | **1.19.0** | AAR metadata requires compileSdk 37 |
| `lifecycle-*` | 2.8.7 (×3) | **2.11.0** | compose artifacts require compileSdk 37; 2.10+ aligns Compose to 1.9.0 |
| `activity-compose` | 1.10.1 | **1.13.0** | → `navigationevent-compose` → Compose 1.9.x |
| `navigation-compose` | 2.8.9 | **2.10.1** | → `compose.animation:1.10.5` → Compose 1.9.2 |
| `okhttp` + `logging-interceptor` | 4.12.0 | **5.5.0** | OkHttp 5.x Android artifact requires compileSdk 37; audit for `mockwebserver3` in tests (none found in this repo) |
| `robolectric` | 4.16.1 | **4.17** | 4.16 supports API 23→36 only; 4.17 needed for API 37 + Java 21 runtime |
| `roborazzi` | 1.59.0 | **1.74.0** | gated by P2 (§4) |
| CameraX ×4 / credentials / play-services-location / datastore / Coil | see §3 | unvalidated | dependencies are commented out; only revisit when they are actually enabled — and then compile and test at `compileSdk 37` |

**Stage 3 acceptance requirements (unchanged):** before/after pairs for all 9 golden cases with a
written justification per visual delta; re-recorded PNGs committed **separately** from the
dependency bump.

---

## 7. Evidence appendix

| Check | Command | Result |
|---|---|---|
| Gradle version | `.\gradlew.bat --version` | `Gradle 9.7.1`, build time 2026-08-19, revision `92f0512e7f06d84621afba191f75e265363890cf`, Launcher JVM 21.0.11 |
| Plugin configuration | `.\gradlew.bat help --console=plain` | BUILD SUCCESSFUL (1m13s), config cache stored, 0 plugin/KGP/KSP warnings |
| Build | `.\gradlew.bat :app:assembleDebug` | BUILD SUCCESSFUL (final state: 3s, 39 up-to-date) |
| Goldens | `.\gradlew.bat :app:testDebugUnitTest --tests "*Step7LightModeGoldenTest*"` | BUILD SUCCESSFUL (3m25s) — 0 pixel diffs |
| Full suite | `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks` | 1335 tests, 1 failed (`VOLT-TESTISO-001`), 1 skipped — identical to pre-upgrade baseline |
| Resolved versions | `.\gradlew.bat :app:dependencies --configuration debugUnitTestRuntimeClasspath` | room 2.8.5 · core-ktx 1.13.0→1.18.0 · compose ui-android 1.7.0 · lifecycle-runtime-ktx 2.8.7 · okhttp 4.12.0 · retrofit 3.0.0 · coroutines-core →1.11.0 · robolectric 4.16.1 · roborazzi 1.59.0 · core-splashscreen 1.2.0 · firebase-bom 34.19.0 |
| Distribution checksum (enforcement) | class-string probe + `file://` wrong-sum test on an isolated `GRADLE_USER_HOME` | `Verification of Gradle distribution failed!` with expected/actual shown (§1.4) |
| Roborazzi defaults diff | `roborazzi-core` sources JARs 1.59.0 vs 1.74.0 (Maven Central) | additive-only diff; compare/resize defaults unchanged (§4.1) |

**Commits:** `34556c0` (Stage 1), `1567964` (Stage 2), plus this close-out commit.
**No `git push` performed** (local commits only, per standing instruction).