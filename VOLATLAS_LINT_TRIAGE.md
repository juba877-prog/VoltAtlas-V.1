# VOLATLAS_LINT_TRIAGE.md

Phase 6 **P1-6 / R-1** deliverable — recorded, triaged lint run. Config decisions below are made
**after** this triage, not blind (R-1 defect: `abortOnError=false` was set with no report, no
baseline, status NOT_RUN).

## 1. Run record

| Field | Value |
|---|---|
| Command | `gradlew.bat :app:lintDebug --console=plain` |
| Executed | 2026-10-01 (Phase 6 Batch 3, against tree at Batch-2 commit `8cb9ab4`) |
| Result | **BUILD SUCCESSFUL** — lint completed, reports written |
| Config at run time | `checkReleaseBuilds=false`, `abortOnError=false`, `checkDependencies=false` (as found) |
| Artifacts | `app/build/reports/lint-results-debug.html`, `app/build/reports/lint-results-debug.sarif` (build dir is not committed → full digest reproduced in §2/§3 of this document) |
| Issue totals | **290 issues — 0 errors, 0 fatal, 290 warnings** |

## 2. Triage — all 290 warnings by rule

| Count | Rule | Nature | Triage decision |
|---|---|---|---|
| 121 | `UseKtx` | style — `SharedPreferences.edit` KTX extensions | Accepted debt. No correctness impact. P3 candidate batch (mechanical, needs behavior-preserving care around `commit`/`apply` semantics). |
| 76 | `DefaultLocale` | correctness-adjacent — `String.format`/`toUpperCase` without `Locale` | Accepted debt **with ordering**: export/print paths (BOQ, PDF text) are the risk surface (locale-dependent digits/case in generated documents). P3 candidate: fix export paths first, cosmetic UI strings after. |
| 26 | `ModifierParameter` | Compose API style (Modifier not first optional param) | Accepted debt. No runtime defect. |
| 21 | `GradleDependency` | version-bump suggestions (compileSdk 37, libs) | **Not applied.** Dependency/toolchain upgrades are out of Phase 6 scope (frozen during remediation; upgrade = its own verified change with fresh full-profile runs). |
| 16 | `UnusedResources` | possibly-dead resources (`R.color.purple_200`, …) | P3 candidate — requires ref-search proof before deletion (same discipline as dead-code items; grep tooling limitation applies). |
| 11 | `NewerVersionAvailable` | library updates available | Not applied (same freeze rationale). |
| 6 | `UseTomlInstead` | build-script structure | Not applied (mechanical build refactor, no defect). |
| 3 | `ConfigurationScreenWidthHeight` | uses `Configuration.screenWidthDp` instead of `LocalWindowInfo.containerSize` | Observation — window-size edge cases on foldables/resize. P3 candidate (adaptive-layout audits covered behavior; this is a redundancy nit unless a divergence is demonstrated). |
| 2 | `AutoboxingStateCreation` | perf nit (`mutableStateOf(Int)` vs `mutableIntStateOf`) | Accepted debt, P3 candidate. |
| 2 | `IconDipSize` | launcher icon density variance | Cosmetic. Accepted. |
| 1 | `NewApi` | **see §3 — NEW-1** | Recorded new finding (§3). |
| 1 | `OldTargetApi` | targetSdk not latest | Policy decision — not changed here (targeting change affects behavior/Play; separate decision). |
| 1 | `UnusedAttribute` | `enableOnBackInvokedCallback` is API 33+ | Informational; predictive back remains correct on 33+, no-op below. |
| 1 | `AndroidGradlePluginVersion` | AGP 9.4.1 available | Not applied (toolchain freeze). |
| 1 | `ContextCastToActivity` | `LocalContext as? Activity` in ProjectPickerScreen | P3 candidate (use `LocalActivity`; behavior-preserving refactor). |
| 1 | `ObsoleteSdkInt` | redundant `SDK_INT >= 24` check (min is 24) | P3 dead-branch candidate (needs ref-search proof, then delete with test). |

## 3. NEW-1 — finding produced by this triage (not in the audit matrix)

| Field | Value |
|---|---|
| Rule / location | `NewApi` — `app/src/main/java/com/example/camera/nameplate/NameplateOcrProcessor.kt:26` |
| Code | `if (bitmap.config == Bitmap.Config.HARDWARE)` — `Bitmap.Config.HARDWARE` requires API 26, **minSdk is 24** |
| Defect | On API 24/25 devices the `getstatic Bitmap$Config.HARDWARE` resolves against a runtime enum without that constant → `NoSuchFieldError` (an `Error`, **not** caught by the surrounding `catch (e: Exception)`) → synchronous throw out of `processBitmap` → nameplate OCR crashes on Android 7.x |
| Guarded? | NO — lint NewApi fires only for unguarded access; the SDK `Build` import in the file is used elsewhere, not around line 26 |
| Classification | **NEW-1** — produced by the P1-6 triage itself; no row exists in the audit matrix or remediation plan |
| Disposition | **Recorded, not fixed in Batch 3** — Batch 3 scope is the planned P1-1..P1-12 only; unplanned fixes are not slipped into batches. Proposed fix (for a future batch / post-audit backlog): `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE` (short-circuit keeps the field access off API < 26) + Robolectric `@Config(sdk=[24])`/`[26]` regression tests proving no `NoSuchFieldError` on 24 and correct `HARDWARE` detection on 26. Surfaced here so the post-audit report can carry it forward honestly. |

## 4. Config decision (post-triage — R-1 closure)

| Setting | Decision | Rationale |
|---|---|---|
| `abortOnError` | `false` → **`true`** | Evidence: current tree = 0 errors / 0 fatal. Enabling costs nothing today and turns lint into a real gate for future **errors** (severity regressions fail the build). This is the ratchet the disabled flag removed. |
| `checkReleaseBuilds` | **kept `false`** | Release is intentionally blocked by the debug-keystore signing guard; running lint on a variant that must never build adds no signal and would surface lintVital noise. Documented rather than silently kept. |
| `checkDependencies` | **kept `false`** | Library-internal findings would multiply triage scope far beyond this audit's app-source charter. |
| `lint-baseline.xml` | **NOT created** | A baseline would silently reclassify all 290 warnings as "accepted" inside build config. The explicit record is this document; warnings stay visible in every run. |

Enforcement: **Batch 7** re-runs `:app:lintDebug` fresh with `abortOnError=true` — any lint error
introduced by Phase 6 changes fails that gate.

## 5. What this triage does NOT claim

- Warnings in §2 are triaged, **not fixed** (except where a plan item covers them elsewhere).
- `checkReleaseBuilds=false` means release-variant lint (lintVital) has still **not** been run;
  release builds remain blocked by design (debug-keystore guard) — never reported as "release verified".
- One run (2026-10-01) is recorded; freshness for the final gate comes from the Batch 7 re-run.
