# VOLTATLAS V.1 — AUDIT-1: SECURITY, PRIVACY & DEFECT REPORT

> **Partially superseded.** See [AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md](AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md) §2 for corrections to this document. AUDIT-4 adjudicates C-1…C-14; AUDIT-5 is the consolidated index and the single P0 source.

| Field | Value |
|---|---|
| Artifact | `docs/audit_defect_report/AUDIT-1_SECURITY_AND_DEFECT_REPORT.md` |
| Repository | `e:\voltatlas-v.1 (4)` |
| Branch / HEAD at audit start | `integration/home-snapshot-2026-09-13` / `16cf38f` |
| App identity | `com.voltatlas.app.v1`, versionCode 1, versionName "1.0" |
| Platform | Android, minSdk 24, targetSdk 36, AGP 9.4.0 / Gradle 9.7.1 / Kotlin 2.4.20 / KSP 2.3.12 |
| Persistence | Room `electrical_engineering_db`, `@Database(version = 26)`, 25-step migration chain |
| Reviewer role | Android/Kotlin security reviewer + chief consulting electrical engineer |
| Method | Line-level static review, evidence-anchored (every claim carries `file:line`); prior `docs/**` audit claims treated as **untrusted input** and re-verified against source |
| Companion artifact | `AUDIT-2_ELECTRICAL_EQUATION_AND_MATRIX_REVIEW.md` (engineering equations & matrices) |
| Source modifications | **None.** This audit changes no engine, data, UI or build file. Only audit documentation and git-ignored evidence logs are created. |

---

## 1. Executive verdict

**Overall: the engineering core is unusually well-disciplined; the release/security hygiene is not.**

The calculation layer (see AUDIT-2) is defensible, assumption-aware and in several places IEC-exact. The defect concentration is **not** in the physics — it is in:

1. **Release hardening** — R8 is neutralised by blanket `-keep` rules; the FileProvider exposes whole storage roots; `allowMainThreadQueries()` can be switched on in production by a public mutable static.
2. **Verification integrity** — the default test profile *excludes* the entire AI subsystem, and one of those excluded tests contains an assertion that cannot pass (`version == 24` against `@Database(version = 26)`). A red test is therefore invisible to the default dev/CI loop.
3. **Audit-trail truthfulness** — `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md` asserts an `INTERNET` permission that does not exist in `AndroidManifest.xml`. A safety/engineering app whose internal audit trail contradicts its own manifest cannot be trusted for compliance sign-off until the trail is corrected. (The same pattern appears in the engineering layer — AUDIT-2 E-03: the UI reports `"R_Cu90 (90°C)"` while the arithmetic uses a 70 °C value.)
4. **Trust model** — the AI mutation-approval gate is architecturally fail-closed (a genuine strength), yet the "trusted engineer" identity it binds to is a **user-editable preference**, not an authenticated principal.

**No hardcoded credentials, no cleartext networking, no WebView, no exported components beyond the single launcher activity, and zero declared runtime permissions were found.** The privacy posture is, if anything, more closed than the documentation claims.

### Release-readiness gates

| Gate | Status | Basis |
|---|---|---|
| No secrets in VCS | **PASS** | `.gitignore` covers `.env*`, `*.jks`, `*.keystore`, `keystore.properties`; `BuildConfig.GEMINI_API_KEY` is `""`; no `AIza…` pattern in tracked source |
| Cloud/LLM fails closed | **PASS** | `CloudLlmFeatureGate` defaults closed; `ENABLE_CLOUD_LLM = false`; production verifier is deny-all |
| Component exposure minimal | **PASS** | Only `.MainActivity` (`exported=true`, launcher) + `CadFileProvider` (`exported=false`) |
| Permission minimisation | **PASS** | Zero `<uses-permission>` in the manifest |
| Network transport safety | **PASS** | No cleartext opt-in; no `networkSecurityConfig` needed at targetSdk 36 |
| FileProvider scoping | **FAIL** | Whole `cache`/`files`/`external-*` roots shared (§V-02) |
| Code-shrinking/obfuscation | **FAIL** | Entire `data`/`engine`/`pdf`/`sld`/`hierarchy` packages kept (§V-09) |
| Main-thread DB protection cannot be bypassed | **FAIL** | `useTestExecutors` is a public mutable static (§V-05) |
| Migration chain machine-validated | **FAIL** | `exportSchema = false`; 63 raw `execSQL` migrations (§V-04) |
| Default test profile is a trustworthy gate | **FAIL** | Red assertion hidden in an excluded suite (§V-06) |
| Documentation matches code | **FAIL** | §V-01, and AUDIT-2 §E-03 |

## 2. Severity model

| Severity | Meaning |
|---|---|
| **Critical** | Exploitable by a hostile local app/user without special conditions, or a safety verdict provably wrong in the unsafe direction on default inputs. |
| **High** | Exploitable with a plausible precondition (user shares an export, test-state leak), or documentation/audit claims that are provably false in a way that could mislead a certifying engineer. |
| **Medium** | Defence-in-depth erosion, silent failure of a guard, or a latent defect that will bite on the next change. |
| **Low** | Hygiene, diagnostics quality, maintainability, minor information exposure. |
| **Info** | Observation or credit; no action required. |

Exploitability note: VoltAtlas is an offline, single-user, local-data engineering tool with **no exported attack surface beyond one launcher activity**. "Exploitable" in this report therefore means *local hostile app / shared device / lost device / supply chain / audit integrity*, not remote network attack. Nothing in this audit is rated Critical.

---

## 3. Register — Part A: security, privacy, integrity, defects

### V-01 — Manifest declares no permissions, while the audit trail claims it does
- **Severity:** High (integrity) · **Category:** doc/code contradiction, dead feature surface
- **Evidence:**
  - `app/src/main/AndroidManifest.xml` — contains **no `<uses-permission>` element at all** (whole file, 37 lines); only `<application>`, one `<activity>`, one `<provider>`.
  - `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5` — *"**Internet Permission**: `android.permission.INTERNET` is declared in `AndroidManifest.xml` for Firebase authentication / general connectivity."* — **false**.
  - `app/build.gradle.kts` ships `firebase-ai`, `okhttp`, `logging-interceptor`, `retrofit`, `converter-moshi`, `firebase-appcheck-recaptcha`, while setting `ENABLE_CLOUD_LLM = false` and `GEMINI_API_KEY = ""`.
- **Mechanism:** The cloud path is unreachable twice over (feature gate + absent permission). Flipping `ENABLE_CLOUD_LLM=true` and injecting a key would fail at the socket layer with `SecurityException`/`UnknownHostException`, not at the gate — a confusing failure mode for a maintainer who trusts the audit doc.
- **Impact:** (a) The internal audit trail is untrustworthy and this doc set is the project's compliance evidence. (b) Seven network libraries ship for a feature that cannot execute.
- **Recommendation:** Correct `11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md` to state that **no permissions are declared and the cloud feature is intentionally non-executable**; then either declare `INTERNET` behind the same gate or exclude the network dependency block from the release variant so the APK matches the shipped capability. Add a CI assertion that the manifest permission set equals a documented expected list.

### V-02 — FileProvider roots expose entire storage subtrees
- **Severity:** High · **Category:** over-permissive IPC surface
- **Evidence:**
  - `app/src/main/res/xml/file_paths.xml` — four roots with `path="."`: `cache-path`, `files-path`, `external-files-path`, `external-cache-path`.
  - `app/src/main/AndroidManifest.xml:26-34` — `CadFileProvider`, `authorities="${applicationId}.fileprovider"`, `exported="false"`, `grantUriPermissions="true"`.
  - Sharing sites: `sld/SldDxfExporter.kt:2117-2147` (`File(cacheDir, "${cleanProjectName}_SLD.dxf")` → `FileProvider.getUriForFile` → `Intent.ACTION_SEND`); `sld/SldImageExporter.kt:304-311` (`File(cacheDir, "VoltAtlas_SLD.png")`).
  - `sld/CadFileProvider.kt` overrides only `getType()`; all path enforcement lives in `file_paths.xml`.
- **Mechanism:** `exported="false"` blocks unsolicited access and grants are per-URI, so this is not exploitable today. But the roots mean **any future artifact written under `cache/`, `files/`, or external `files`/`cache` becomes shareable by a one-line change** — a DB copy, exported project JSON, crash log, or cached catalog included.
- **Impact:** Latent data-exfiltration primitive: one future `ACTION_SEND`/`getUriForFile` call with an unintended `File` hands a third-party app read access to internal storage.
- **Recommendation:** Scope every root to a dedicated export directory (`<cache-path name="exports" path="exports/"/>`) and write shared artifacts into `cacheDir/exports/`. Add a unit test asserting `file_paths.xml` contains no `path="."`.
### V-03 — The "trusted principal" behind the AI mutation gate is a user-editable preference
- **Severity:** Medium (High in a regulated/multi-tenant deployment) · **Category:** trust boundary / attribution
- **Evidence:**
  - `ai/ui/EngineeringAssistantViewModelFactory.kt:45-47` — `val principalProvider: () -> String = { preferences.trustedEngineerIdentity.value }`, injected at `:59` into `DefaultTrustedPrincipalProvider(principalProvider)`.
  - `ai/approval/TrustedPrincipalProvider.kt:20-24` — returns the supplier value, blanks trimmed to `null`.
  - `ai/approval/TrustedProposalApprovalGateway.kt:40-44` — `PrincipalMissing` fails closed on `null`/blank; `:89` binds the resolved string to `ApprovedBy` on the durable grant.
  - Backing store: `data/GlobalPreferences.kt`.
- **Mechanism:** The gate's architecture is correct and genuinely fail-closed (§5 credit). Its **identity source** is application preference state that the user (or any code path able to write preferences) controls. The durable `ApprovalGrantEntity.approvedBy` audit field therefore records a self-asserted string.
- **Impact:** The approval record is not attributable to a verified person. Where "who approved this cable change" is part of QA evidence, the record is not evidentially sound.
- **Recommendation:** (1) Rename the field/labels to `operatorName`/`declaredIdentity` and never present it as authenticated; (2) if real attribution is needed, bind approvals to a Keystore-backed, biometric-gated device identity or an external IdP; (3) at minimum record `identitySource = "PREFERENCE_UNVERIFIED"` on the grant row and show it in the audit UI.

### V-04 — 25-step migration chain with no machine-validated schema
- **Severity:** Medium · **Category:** data integrity / verification gap
- **Evidence:** `data/AppDatabase.kt:1046-1066` — `@Database(entities = [… 15 entities …], version = 26, exportSchema = false)`; **63 `execSQL` call sites** in the same file implementing `MIGRATION_1_2` … `MIGRATION_25_26` (declared at lines 87, 95, 290, 343, 626, 647, 656, 663, 669, 675, 696, 712, 718, 725, 731, 750, 763, 780, 800, 812, 818, 824, 901, 943, 961); `.addMigrations(...)` at `:1092-1100`.
- **Mechanism:** With `exportSchema = false`, Room emits no `schemas/26.json`, so the compiler/Room cannot cross-check migration DDL against the generated entity schema, and no `MigrationTestHelper` differential test is possible. Correctness rests entirely on hand-written `DatabaseMigrationTest` assertions.
- **Impact:** A future entity edit that is not mirrored in a migration will not be detected until a device holds old data; the failure surfaces as a runtime `IllegalStateException` on user devices, i.e. **possible loss of a user's project data**.
- **Recommendation:** Set `exportSchema = true` + `room.schemaLocation` in KSP args; commit `app/schemas/`; replace the hand-rolled verification with `MigrationTestHelper` opening every prior schema JSON. The existing `DatabaseMigrationTest` (which already re-paths a hand-built v-old DB through the real migration chain, `DatabaseMigrationTest.kt:490-520`) is a good foundation but cannot substitute for schema-diff validation.

### V-05 — `allowMainThreadQueries()` reachable in production via a public mutable static
- **Severity:** Medium · **Category:** guard bypass / latent ANR + correctness
- **Evidence:** `data/AppDatabase.kt:1080-1112`:
  ```kotlin
  companion object {
      @Volatile private var INSTANCE: AppDatabase? = null
      var useTestExecutors = false                 // :1084  public mutable, no @VisibleForTesting
      fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
          val builder = Room.databaseBuilder(...).addMigrations(...)
          if (useTestExecutors) {                  // :1103
              builder.setQueryExecutor { it.run() }
              builder.setTransactionExecutor { it.run() }
              builder.allowMainThreadQueries()     // :1106
          }
          ...
      }
  }
  ```
- **Mechanism:** `useTestExecutors` is a writable global on the production singleton. Any code (including a test that leaks state across the Robolectric class loader, or a future diagnostic toggle) that sets it `true` before the first `getDatabase()` call permanently disables main-thread protection for that process.
- **Impact:** Strict-mode-violating synchronous DB I/O on the UI thread → jank and ANR risk; on a weakly-tested codepath it can also mask a lost-write/ordering defect. Compounded by the singleton caching the decision for the process lifetime.
- **Recommendation:** Annotate `@VisibleForTesting`, make it `private set` with an explicit `internal fun enableTestExecutors()`, and assert in `onCreate` of the `Application` that it is `false` in release (`check(!BuildConfig.DEBUG)`). Better: never touch the singleton from tests — build an in-memory DB per test (as most suites already do).
### V-06 — A guaranteed-failing assertion is hidden inside the excluded AI test profile
- **Severity:** Medium · **Category:** verification integrity / red suite invisible
- **Evidence:**
  - `ai/proposal/Phase7E1DurableSchemaTest.kt:205-209`:
    ```kotlin
    @Test
    fun noDestructiveMigrationFallback_isConfigured() {
        // AppDatabase specifies explicit migration chain without fallbackToDestructiveMigration
        assertEquals(24, AppDatabase.getDatabase(context).openHelper.readableDatabase.version)
    }
    ```
  - `data/AppDatabase.kt:1064` — `version = 26`. Room stamps `user_version = 26` on first open, so this assertion **cannot pass**.
  - `app/build.gradle.kts`, `tasks.withType<Test>` fast/dev profile: `excludeTestsMatching("com.example.ai.*")` (plus stress and screenshot suites). `-Pprofile=ai` includes it; the default is `fast`.
- **Mechanism:** The assertion was left behind when the schema advanced 24 → 26. It lives in the one package family the default profile excludes, so the default dev/CI loop is green while the AI profile is red. (Confirmation run: §7 evidence, `-Pprofile=ai`.)
- **Impact:** Two-fold. (a) A permanently red suite trains the team to ignore AI-profile results, which is exactly where the mutation-approval security tests live. (b) The test's *intent* — "no destructive migration fallback is configured" — is now unverified, even though the artefact it guards (`.addMigrations(...)` with no `fallbackToDestructiveMigration()`, `AppDatabase.kt:1092-1100`) is present and correct.
- **Recommendation:** Rewrite the assertion against a stable contract rather than a magic number:
  ```kotlin
  assertFalse(AppDatabase.isDestructiveFallbackConfigured)   // or introspect the builder in a factory seam
  assertTrue(AppDatabase.getDatabase(context).openHelper.readableDatabase.version >= 1)
  ```
  and add `excludeTestsMatching("com.example.ai.*")` review criteria so profile exclusions cannot hide security-relevant suites.

### V-07 — Production interaction-state debug logging in the SLD canvas
- **Severity:** Low-Medium · **Category:** information exposure / log hygiene
- **Evidence:** `sld/SldCanvas.kt:114` (`pointerInput block started: isEditMode=true`), `:134-136` (`TouchDown screen=(x, y) pan=(…) zoom=… graph=… hitNode=…`), `:146`, `:173`, `:176`, `:184` — all `android.util.Log.d("SLD_EDIT_GESTURE", …)`, compiled into release (`isMinifyEnabled = true` but `-keep` rules and `Log` calls are not stripped).
- **Mechanism:** Per-gesture `Log.d` with coordinates and node identifiers.
- **Impact:** Logcat/bugreport noise on user devices; SLD node identifiers and layout coordinates of a client's electrical design are written to the system log buffer, which `adb bugreport` and some OEM telemetry paths collect.
- **Recommendation:** Remove or gate behind `if (BuildConfig.DEBUG)`; prefer a single structured, redacted diagnostics channel. Add a CI grep rejecting `android.util.Log` in `main/` outside an approved logging facade.

### V-08 — `println`/`printStackTrace` on production parse failure
- **Severity:** Low · **Category:** diagnostics quality / error-text exposure
- **Evidence:** `data/catalog/CatalogRepository.kt:180-181` — `println("ERROR PARSING BREAKERS: ${e.message}")`, `e.printStackTrace()`, alongside a proper `warnings.add(...)`.
- **Impact:** Unstructured stdout in a shipped app; raw exception text (which can embed file paths) bypasses the app's own error-sanitisation layer (`ai/task/DefaultEngineeringTaskManager.kt:520-530` shows the project already has a redaction routine — this site does not use it).
- **Recommendation:** Route through the existing sanitising logger; drop `printStackTrace`.

### V-09 — R8/minification effectively disabled for the whole domain layer
- **Severity:** Medium · **Category:** release hardening
- **Evidence:** `app/proguard-rules.pro:29-41` —
  ```
  -keep class com.example.data.** { *; }
  -keep class com.example.sld.** { *; }
  -keep class com.example.engine.** { *; }
  -keep class com.example.pdf.** { *; }
  -keep class com.example.hierarchy.** { *; }
  ```
  combined with `release { isMinifyEnabled = true; isShrinkResources = true }` in `app/build.gradle.kts`.
- **Mechanism:** The blanket keeps preserve *all* classes and members in those packages. Room needs `@Entity`/`@Dao` and Moshi needs `@JsonClass`/`@Json` members — those narrower rules already exist at `:20-33`. The package-wide keeps are redundant for serialization and defeat shrinking.
- **Impact:** Larger APK; no identifier renaming on business logic (modest reverse-engineering cost barrier removed); **dead code and orphaned engines cannot be detected** — directly relevant to this project's own "gaps/orphans" audit goal.
- **Recommendation:** Delete the five package-wide keeps; keep only `@Entity`/`@Dao`/`RoomDatabase` subclasses/`@JsonClass`/`@Json` members (already present), plus the `ru.noties.jlatexmath.**` rule. Then verify with `-printusage`/`-printseeds` and the release smoke test. Expect a measurable size drop and a real orphan report.
### V-10 — Backup rules protect only the Room database
- **Severity:** Low · **Category:** privacy / data-at-rest egress
- **Evidence:**
  - `app/src/main/AndroidManifest.xml:5-7` — `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:fullBackupContent="@xml/backup_rules"`.
  - `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` — both exclude exactly three paths, all `domain="database"`: `electrical_engineering_db`, `-wal`, `-shm`.
- **Mechanism:** Everything outside `databases/` remains eligible: `shared_pref/` (incl. `GlobalPreferences` — theme, settings, and the `trustedEngineerIdentity` from V-03) and `file/` (DataStore under `files/datastore/`, plus any future `files/` artifact — recall `file_paths.xml` declares `files-path path="."`). The exclusions are correct as far as they go, and deliberately excluding the engineering DB is the right instinct.
- **Impact:** On a device with cloud backup enabled, a client's settings and any future file-domain artefact (AI transcripts, project exports) leave the device to Google's backup service. The engineering database itself is (correctly) withheld, so project data is safe **today**.
- **Recommendation:** Make the exclusion explicit and complete: add `<exclude domain="sharedpref" path="."/>`, `<exclude domain="file" path="."/>`, `<exclude domain="external" path="."/>` (with narrowly re-allowed non-sensitive paths if settings restore is desired), or set `android:allowBackup="false"` outright — an engineering design tool has little to gain from cloud restore relative to the compliance cost. Document the decision either way.

### V-11 — Silent failures around audit/freshness recording
- **Severity:** Low · **Category:** audit integrity / observability
- **Evidence:** `engine/coordinator/EngineeringCalculationCoordinator.kt` — `try { … recordCableSizing(…) } catch (_: Exception) { }` at `:437-440`, `:458-460`, `:463-466`, `:825-827`, `:850-852`, `:855-858`, `:1274-1276` (and equivalent sites in the bulk loops).
- **Mechanism:** Freshness/execution metadata is recorded best-effort and any failure is discarded. Results still publish, so the calculation is correct but the *provenance record* is not.
- **Impact:** The AI context layer (`ai/context/freshness/**`) decides whether a calculation is STALE/CURRENT from this registry. Silent loss of the record degrades the assistant's answers (stale results presented as current) with **no signal** anywhere. For an app whose selling point is "calculations with provenance", a silent audit-channel failure deserves at least a counter/log.
- **Recommendation:** Replace the empty catches with a failure counter surfaced in diagnostics (and, for the `recordError` path, ensure one failed `recordError` cannot mask the original exception). Keep the non-throwing behaviour — that design choice is right — but make it observable.

### V-12 — Blocking calls on potentially UI-adjacent paths
- **Severity:** Low · **Category:** responsiveness / ANR risk
- **Evidence:**
  - `engine/coordinator/EngineeringCalculationCoordinator.kt:80-87` — `private fun getProjectRevision(projectId)` wraps a suspend provider in `runBlocking { … }`; called from `calculateCableSizing` (`:411,431,455,464`), `calculateProjectShortCircuit` (`:908,956`), `calculateProjectProtection` (`:1248,1261,1273`) etc.
  - `ai/ui/EngineeringAssistantViewModelFactory.kt:52-62` — `val gatewayLazy = lazy { kotlinx.coroutines.runBlocking(Dispatchers.IO) { … DurableMutationWorkflow.createGateway(…) } }`; `gatewayLazy.value` is dereferenced by `approvalGatewayProvider` on first assistant use.
- **Mechanism:** Room's `getCurrentRevision` is a suspend DAO call; `runBlocking` on it inside a computation that can be invoked from a UI-triggered coroutine serialises on the current dispatcher. The `lazy` block performs asset JSON parsing under `runBlocking`, and `lazy` is evaluated wherever the provider is first touched.
- **Impact:** If any caller runs on `Dispatchers.Main`, this blocks the main thread (ANR at scale). Most bulk paths are already wrapped in `withContext(Dispatchers.Default)` (`:1286-1306`), so the exposure is narrow — but the guard is incidental, not enforced.
- **Recommendation:** Convert the revision lookup to a suspend function (`getProjectRevisionSuspend` already exists and is unused on these paths) or cache the revision per calculation pass; pre-initialise the gateway off the main thread at app start rather than inside a `lazy` that the UI can trigger.
### V-13 — No green test profile exists (found during verification)
- **Severity:** Medium-High · **Category:** verification integrity / CI signal
- **Evidence:** §7 E-1/E-2/E-3. `-Pprofile=full` → **1882 tests, 51 failed**; `-Pprofile=ai` → **819 tests, 2 failed**; the default `fast` profile excludes `com.example.ai.*` and the three legacy dialog suites (`app/build.gradle.kts` `tasks.withType<Test>`), i.e. 48 of the 51 failures.
- **Mechanism:** The build script documents its exclusions as "diagnostic evidence only; must not participate in default runs". That is reasonable for the Robolectric-idle suites, but the effect is that **`full` is a known-red profile** (it re-includes them) while the **default profile hides the two stale AI assertions and an order-dependent isolation failure**. There is therefore no configuration in which "the tests pass" can be relied upon.
- **Impact:** This is precisely the mechanism that let V-06 survive: a real regression in an AI or dialog suite would be invisible, and the AI suite is where the mutation-approval security tests live.
- **Recommendation:** Add `-Pprofile=diagnostic` to own the quarantined suites, remove them from `full`, and designate a single green profile as the CI gate (including `com.example.ai.*`). Run it with `--rerun` so a Gradle up-to-date cache can never report a false SUCCESSFUL.

### V-14 — `logging-interceptor` ships on the release classpath
- **Severity:** Low (latent) · **Category:** release hardening / information exposure
- **Evidence:** §7 E-4 — `releaseRuntimeClasspath` contains `com.squareup.okhttp3:logging-interceptor:4.12.0`, declared as `implementation(libs.logging.interceptor)` in `app/build.gradle.kts` (not `debugImplementation`).
- **Mechanism:** The class currently has no consumer because the cloud client is unreachable (V-01). If the cloud path is ever enabled and an interceptor is installed at `Level.BODY`, full request/response bodies — the prompt and any project context attached to it — would be written to logcat, which `adb bugreport` and some OEM telemetry paths collect. This compounds V-07 (gesture tracing) and V-08 (`printStackTrace`).
- **Impact:** Latent, not active. Recorded so that enabling the cloud feature does not silently re-open a data-exposure channel.
- **Recommendation:** Move the dependency to `debugImplementation`, and enforce interceptor level in code (never `Level.BODY` in a release build) if the cloud path is ever activated.

### V-15 — Error-severity lint finding: `ContextCastToActivity` at `ProjectPickerScreen.kt:794`
- **Severity:** Low · **Category:** Compose correctness / latent silent behaviour divergence
- **Evidence:** `app/build/reports/lint-results-release.sarif` — rule `ContextCastToActivity`, **error** severity, at `app/src/main/java/com/example/ui/ProjectPickerScreen.kt:794`:
  ```kotlin
  LocalContext.current as? android.app.Activity
  ```
  Lint message: *"LocalContext should not be cast to Activity, use LocalActivity instead."* Found by the completed `lintRelease` run (§7 E-5).
- **Mechanism:** Inside Compose, `LocalContext.current` is not guaranteed to be an `Activity` — in dialog/popup/local-activity-absent hosts it can be a `ContextThemeWrapper`. The `as?` form degrades **silently**: whatever the code does with the `Activity` reference (window flags, insets, finish()) is skipped on some hosts and executed on others, with no error surface.
- **Impact:** Host-dependent behaviour divergence; no security impact.
- **Recommendation:** Use `androidx.activity.compose.LocalActivity`, or unwrap the context with an explicit failure path so a missing `Activity` is logged/asserted rather than silently ignored.

---

## 4. Documentation-vs-code contradictions (audit-trail integrity)

The repository ships 39 markdown audit/planning documents. In a compliance context those documents *are* evidence, so a false statement is a defect in its own right. Every claim below was checked against source and failed.

| # | Claim | Claimed in | Actual code | Verdict |
|---|---|---|---|---|
| D-1 | "`android.permission.INTERNET` is declared in `AndroidManifest.xml` for Firebase authentication / general connectivity." | `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5` | `AndroidManifest.xml` declares **no permissions at all** | **False** |
| D-2 | "AppDatabase specifies explicit migration chain without `fallbackToDestructiveMigration`" (asserted as DB version 24) | `ai/proposal/Phase7E1DurableSchemaTest.kt:206-208`, `:1131-1134` | No destructive fallback is indeed configured, but `@Database(version = 26)`; both assertions are stale magic numbers and **fail** | **Half-true + red test** |
| D-3 | Radiation/material provenance shown to the user as `R_Cu70 (70°C)` / `R_Cu90 (90°C)` | `engine/quick/QuickEngineeringDataBridge.kt:67-72` (`rColumnUsed`) | The underlying numbers are the 20 °C value and a 70 °C estimate respectively (`data/Models.kt:29-32` aliases) — see AUDIT-2 E-03 | **False provenance** |
| D-4 | "`kT`/ambient-derating tables are IEC-consistent" (implied by `standards: ["IEC 60364-5-52"]` in `derating_data.json`) | `assets/derating_data.json` header | The XLPE 45 °C air row is out of family with the same file's ground table and with Table B.52.14 — see AUDIT-2 E-02 | **Non-conforming row** |
| D-5 | "Zero API keys / secrets copied — Secret scanner returned 0 matches" | `docs/local_integration/01_PRE_INTEGRATION_VERDICT.md:32` | Independently re-verified: **true** (no `AIza…`, no keystore, no `.env` in tree) | **Confirmed** |

**Root cause of D-1/D-2/D-3 in one sentence:** numeric constants and provenance strings are duplicated across engines, tests and documents instead of deriving from one typed source — so when a value advances (DB version 24→26, R-column semantics), the satellites drift silently. The fix pattern for all three is the same: *derive, don't restate* (planned remediation P1-3).

---

## 5. Credited controls (verified strengths)

These were actively probed and held. They matter because they are the parts a future refactor is most likely to break.

1. **Fail-closed LLM gate, doubly.** `ai/hybrid/CloudLlmFeatureGate.kt:16-30` defaults `enabledSupplier`/`apiKeySupplier` to `BuildConfig` values wrapped in `try/catch(Throwable) → false/null`; `ENABLE_CLOUD_LLM=false` and `GEMINI_API_KEY=""` in `app/build.gradle.kts`. A missing key throws inside the client seam *before* any network call (`CloudLlmConversationEngine.kt:48-51`), and `ConversationEngineProvider` resolves to `DisabledCloudLlmEngine`.
2. **Deny-all production approval verifier.** `DenyAllApprovalVerifier` is the production default (asserted by `ai/proposal/EngineeringMutationEngineTest.kt:628-632`, `Phase7E1DurableSchemaTest.kt:1089-1091`), so mutation is impossible without an explicitly injected authority.
3. **The mutation pipeline is genuinely transactional and revision-guarded.** `TrustedProposalApprovalGateway.processApprovalAndExecute` (`:28-120`) performs, in order: blank-`userActionId` rejection → principal resolution (fail-closed) → **project-ID equality** → **optimistic revision CAS** (`baseProjectRevision != liveRevision` ⇒ `StaleProposal`) → pure policy revalidation → digest + semantic-key recomputation → single-use durable grant issuance → workflow execution. `DurableMutationWorkflow` wraps the write in a Room transaction with a checked row-count and records `MutationExecutionReceiptEntity`.
4. **Cross-project entity access is explicitly blocked**, returning `InvalidIntent("Entity belongs to project X, expected Y")` (`DurableMutationWorkflow.kt:373`, catch of `InternalDurableRollbackException.ProjectMismatch`).
5. **Architecture guard tests exist** that assert the conversation engine holds no DB/network/mutation references (`ai/AssistantArchitectureGuardTest.kt:16`, `ai/conversation/ConversationPolicyEngineTest.kt:520`, `ai/ui/ApprovalExecutionUiStateTest.kt:205`) — i.e. "the LLM cannot mutate" is enforced by test, not by convention.
6. **Error sanitation exists and is tested**: `DefaultEngineeringTaskManager.kt:520-530` redacts SQL, stack frames, credentials and file paths; `ai/task/EngineeringTaskManagerTest.kt:963-969` proves `SELECT secret_token …` and `C:\\private\\passwords.txt` do not reach the UI snapshot.
7. **Truthful missing-data behaviour throughout the engines**: rather than defaulting, the engines return `DATA MISSING` / `SELECT_LOADS` / `CATALOG_MISSING` with explicit notes (e.g. `ShortCircuitEngine.buildMissingResult`, `AtsEngine.buildNullResult`, `UpsEngine` `SELECT_LOADS`, `CableSizingEngine.failedResult` with `CableFailureCode`). Assumptions that cannot be removed (x″d, K_G, X/R = 10, Icm estimation, assumed soil) are surfaced in the result `note`/`warnings` for the engineer to verify.
8. **No silent engineering fallbacks on the happy path**: the legacy `typeCode = 1` fallback was deliberately removed with a comment (`CableSizingEngine.kt:490`), and the `contains("B")` trip-curve bug (which matched every MCB/MCCB/ACB) is documented and replaced by a strict parser (`ProtectionEngine.kt:52-70`).
9. **Secure defaults verified absent-where-good**: no `WebView`, no `usesCleartextTraffic`, no `networkSecurityConfig` needed, exactly one exported component (the launcher activity), `CadFileProvider` is `exported="false"`.
10. **Secrets hygiene confirmed**: `.gitignore` blocks `.env*`, `*.jks`, `*.keystore`, `*.pepk`, `keystore.properties`; `keystore.properties.example` carries obvious placeholders; no credential material is tracked.
---

## 6. Coverage matrix (risk-ranked, honest)

533 Kotlin files exist under `app/src`. A literal line-by-line read of every UI composable would not change the security verdict, so coverage was allocated by risk. This table is what the audit **actually** did — treat any file not listed in column 1 as un-reviewed.

| Tier | Scope | Files | Coverage |
|---|---|---|---|
| **T1 — deep line-level** | All 16 engines + coordinator; Room schema/migrations/builder; DAO query surfaces; manifest/res/XML; ProGuard; build scripts; version catalog; secret-bearing files; AI approval + mutation + gate chain; properties/preferences; data matrices (JSON) | ~55 | **100 %** |
| **T2 — targeted read** | ViewModel calculation orchestration (`EngineeringViewModel` around the engine wiring), `ProjectContextProviderImpl`, freshness adapters, `GlobalPreferences`, `CatalogRepository`, DXF/PNG/PDF export writers, `DurableMutationWorkflow`, `ConversationPolicyEngine` | ~25 | **~90 %** (specific methods) |
| **T3 — structural/grep-based** | Compose UI layer (screens/components/dialogs/design system), SLD layout & geometry engines, PDF renderer internals, test sources | ~420 | **Pattern-searched only** — screening for `Log`, `!!`, `runBlocking`, `GlobalScope`, `Thread.sleep`, raw SQL, `Intent`, `File(`, `TODO/FIXME`, `WebView`, secrets, `fallbackToDestructiveMigration`, `allowMainThreadQueries` |
| **T4 — unread** | Fonts/themes/branding assets, generated resources, `app/catalog_migration/backup/**` (legacy), `.qodo`/`.vscode` configs | — | None (declared, not silently omitted) |

**Consequence for the reader:** Part A is exhaustive for the security/integrity/build surface and for the calculation core. It is **not** a UI/accessibility review, and AUDIT-2 §6 discloses the specific equation sites that remain unverified. If a UI-layer or accessibility audit is also required, it should be commissioned as a separate pass (the repo already contains roborazzi visual-capture suites such a pass can build on).

---

## 7. Verification evidence (commands actually run)

Executed against HEAD `16cf38f` on Windows/PowerShell with the project's own wrapper. Raw logs: `build/audit/*.log` (git-ignored).

| # | Command | Result | Interpretation |
|---|---|---|---|
| E-1 | `.\gradlew.bat :app:testDebugUnitTest -Pprofile=ai --console=plain` | **`819 tests completed, 2 failed`** — both in `Phase7E1DurableSchemaTest`: `noDestructiveMigrationFallback_isConfigured` (`:208`) and `no7DOrFormulaBehaviorChanged` (`:1133`), both `java.lang.AssertionError` | Confirms **V-06**. Both are the same stale magic-number assertion (`assertEquals(24, …version)` vs `@Database(version = 26)`). **Important negative result: no engineering/formula test in the AI profile failed** — the behaviour those tests guard is intact; only the version literal is stale. |
| E-2 | `.\gradlew.bat :app:testDebugUnitTest -Pprofile=full --console=plain` | **`1882 tests completed, 51 failed, 14 skipped`**, BUILD FAILED in 11 m 7 s | Confirms **V-06** and yields new finding **V-13**. Failure distribution extracted from the log: <br>• `LegacyCircuitEditDialogSaveMappingCharacterizationTest` — **27** <br>• `LegacyCircuitEditDialogVisibilityCharacterizationTest` — **16** <br>• `CircuitEditDialogDiagnosticIsolationTest` — **5** <br>• `Phase7E1DurableSchemaTest` — **2** <br>• `CircuitEditorSessionRuntimeTest` — **1** <br>Total = 51 ✓ (all five classes accounted for) |
| E-3 | `.\gradlew.bat :app:testDebugUnitTest --tests "*CircuitEditorSessionRuntimeTest*" --console=plain` | **BUILD SUCCESSFUL** (passes in isolation under the default profile) | Proves the E-2 failure of `CircuitEditorSessionRuntimeTest.test07_bounds_reviewSaveInsideRootAndNoNext` is **order/isolation-dependent, not a product regression** — the same family as the already-fixed `VOLT-TESTISO-001` (`648a628`). |
| E-4 | `.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --console=plain` | 83 KB dependency graph captured | Confirms the shipped network surface that V-01 says is unreachable: `firebase-bom 34.19.0`, `firebase-ai → 17.17.0`, `firebase-appcheck-recaptcha → 19.2.1`, `okhttp 4.12.0` **and `logging-interceptor 4.12.0` on the release classpath** (declared as `implementation`, not `debugImplementation`), `retrofit converter-moshi 3.0.0`, `moshi-kotlin 1.15.2`; persistence `room-ktx`/`room-runtime 2.8.5`. |
| E-5 | `.\gradlew.bat :app:lintRelease --console=plain` | **COMPLETED — BUILD SUCCESSFUL in 4 m 59 s.** SARIF + HTML reports written to `app/build/reports/lint-results-release.{sarif,html}`. **251 findings: 250 warnings, 1 error.** Distribution: `UseKtx` 117, `DefaultLocale` 78, `GradleDependency` 19, `UnusedResources` 16, `NewerVersionAvailable` 10, `UseTomlInstead` 5, `IconDipSize` 2, `OldTargetApi` 1, **`ContextCastToActivity` 1 (error → filed as V-15)**, `UnusedAttribute` 1, `ObsoleteSdkInt` 1. No rule tagged `security` fired. | Closes the evidence gap AUDIT-3/AUDIT-4 could not close (their Gradle invocations failed before task execution). The single error-severity finding is filed as **V-15**; the rest are hygiene/maintenance (KTX adoption, locale handling, API updates, unused resources) and do not alter any Part A verdict. |
| E-6 | Programmatic JSON audits of `cable_catalog`, `breaker_catalog`, `busway_catalog`, `conduit_catalog`, `data_sheet`, `derating_data`, `transformers_seed`, `awg_swg_kcmil_table` | see AUDIT-2 §3 | Produced the element-level matrix verdicts, including **E-19** (air/gnd ratio 0.69–1.49) and **E-08** (seed R/X inconsistent by >10×). |
| E-7 | Hand re-derivation of `qa_reference_cases.json` (TC-TX-01/02, TC-CABLE-008) | exact match on TC-TX-01 | Establishes engine↔oracle parity (AUDIT-2 §2.1). |
| E-8 | Pattern sweeps (`Log`, `println`, `runBlocking`, `GlobalScope`, `allowMainThreadQueries`, `fallbackToDestructiveMigration`, `rawQuery`, `Intent`, `File(`, `WebView`, `INTERNET`/`uses-permission`, secret patterns, `AIza…`) | no cleartext/WebView/hardcoded-secret hits | Supports V-07/V-08/V-11/V-12 and the "no leaks" credit in §5. |
| E-9 | `git log --oneline -15`, `.gitignore` and tracked-file inspection | clean tree; no `*.jks`, `.env`, `keystore.properties` tracked | Supports the secrets-hygiene credit in §5. |

**New finding from E-2 — V-13: the repository has no green test profile.**
`fast` (the default) excludes 48 of the 51 failures, hiding both stale AI assertions and the isolation failure; `full` fails by design because it re-includes suites the build script itself declares diagnostic-only; `ai` fails on the stale assertion. Consequence: **"the tests pass" is not currently a meaningful statement in this repo**, and a genuinely new failure in an AI or dialog suite would go unnoticed. Fix priority is high because this is the mechanism that allowed V-06 to survive.

**Secondary finding from E-4 — V-14:** `okhttp3:logging-interceptor` is on the **release** runtime classpath. Combined with V-07, review whether any interceptor with `Level.BODY` could be installed (it would log full request/response bodies, including the prompt and any project context, to logcat if the cloud path were ever enabled). Currently the client is unreachable, so this is a latent, not an active, exposure — but the dependency belongs in `debugImplementation`.

---

## 8. Remediation roadmap

### P0 — release blockers (≈1–2 days)

| ID | Action | Acceptance criterion |
|---|---|---|
| **V-06 / V-13** | Replace both `assertEquals(24, …version)` assertions with a version-agnostic contract check; make **one** profile green and designate it the CI gate; move the diagnostic-only dialog suites behind an explicit `-Pprofile=diagnostic` so `full` can pass | `gradlew :app:testDebugUnitTest -Pprofile=<gate>` exits 0; the gate includes `com.example.ai.*`; a deliberately broken test fails the gate |
| **V-02** | Scope `file_paths.xml` to a dedicated `exports/` directory; route all DXF/PNG/PDF sharing through it | No `path="."` in any provider path definition; sharing verified from the new location |
| **V-01** | Correct `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5`; decide and document the network-permission policy; align manifest, docs and SBOM | Doc statement == manifest reality; a CI assertion pins the expected permission set |
| **V-05** | Make `useTestExecutors` `@VisibleForTesting` + `private set`; assert `false` in release | A release-build unit test proves the flag cannot be enabled |
| **V-14** | Move `okhttp3:logging-interceptor` (and the unreachable cloud stack) to `debugImplementation`, or delete it | Release classpath contains no logging interceptor |
| **V-15** | Replace the `LocalContext.current as? Activity` cast at `ProjectPickerScreen.kt:794` with `LocalActivity` (or an unwrapping helper with an explicit failure path) | No `ContextCastToActivity` lint error remains; picker behaviour verified in a dialog host |

### P1 — hardening & integrity (≈3–5 days)

| ID | Action | Acceptance criterion |
|---|---|---|
| **V-04** | `exportSchema = true` + committed `app/schemas/`; adopt `MigrationTestHelper` differential tests | Every prior schema version opens and migrates in CI; a missing migration fails the build |
| **V-09** | Remove the five package-wide `-keep` rules; verify with `-printusage`/`-printseeds` | Release smoke test passes; APK size decreases; orphan/dead-code detection becomes meaningful |
| **V-03** | Rename the principal field to `declaredIdentity`/`operatorName`; record `identitySource` on the grant; surface it in the audit UI | No UI string implies authentication; grant rows carry provenance |
| **V-10** | Complete the backup exclusions (`sharedpref`, `file`, `external`) or set `allowBackup="false"` | Documented policy matches actual data egress; verified with `adb shell bmgr` or an equivalent check |
| **V-11** | Replace the empty catches around freshness recording with a counter surfaced in diagnostics | A forced failure increments a visible counter; the pipeline still does not throw |
| **E-01, E-02, E-03, E-04, E-10** | The five engineering P0 items (AUDIT-2 §5) | Per-item acceptance criteria listed there |

### P2 — hygiene (ongoing)

| ID | Action |
|---|---|
| **V-07, V-08** | Gate/remove the `Log.d` gesture tracing; route catalogue parse failures through the existing redacting logger |
| **V-12** | Convert the coordinator's `runBlocking` revision lookups to the existing suspend path; pre-initialise the assistant gateway off the main thread |
| **V-13 (follow-on)** | Add a test-isolation guard (snapshot/restore shared `Catalog` state, as done for `VOLT-TESTISO-001`) and run the gate with `--rerun` so an up-to-date cache can never produce a false green |
| **E-05 … E-17** | Per AUDIT-2 §5 |

---

## 9. Statement of limits

1. This audit read **all** calculation engines, the persistence/migration layer, the security-relevant configuration and the AI approval/mutation chain at line level, and pattern-screened the remaining UI/SLD/PDF code. Files outside tiers T1–T2 (§6) were **not** line-reviewed — treat their security properties as unassessed.
2. **No runtime instrumentation, no penetration test, no fuzzing, no device/emulator deployment and no external benchmark comparison** were performed. Findings derive from source, programmatic analysis of the data assets, and the project's own test runs executed here.
3. **Lint completed** within this pass (E-5): 251 findings, one error-severity (`ContextCastToActivity`, filed as **V-15**), none security-tagged. Lint output is additional to, and does not invalidate, the findings above.
4. `CircuitEditorSessionRuntimeTest` failed only in the `full` run and passed in isolation; I classified it as an isolation artifact but did **not** bisect the offending precedent suite. If that classification matters for a release decision, bisect first.
5. Severity ratings assume the threat model in §2 (local, single-user, offline engineering tool). Under a multi-user or regulated-QA threat model, **V-03 escalates to High**.
6. This document is a **defect review, not an engineering sign-off**. No project deliverable should be issued on the strength of it alone.

---
*End of AUDIT-1. Engineering equation and data-matrix findings are in `AUDIT-2_ELECTRICAL_EQUATION_AND_MATRIX_REVIEW.md`.*
