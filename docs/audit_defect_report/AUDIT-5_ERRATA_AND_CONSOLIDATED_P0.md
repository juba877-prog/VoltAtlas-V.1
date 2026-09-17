# VOLTATLAS — AUDIT-5: ERRATA & CONSOLIDATED P0

| Field | Value |
|---|---|
| Artifact | `docs/audit_defect_report/AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md` |
| Date | 2026-09-17 |
| Repository / HEAD at execution | `e:\voltatlas-v.1 (4)` @ `97b419c` (originally cited as `d4d0fd4`; branch `main`) |
| Directive baseline | Issued against the `16cf38f` lineage (`integration/home-snapshot-2026-09-13`). The operative state is HEAD `d4d0fd4` plus the preserved working tree. |
| Working tree | Carries pre-existing, uncommitted circuit-editor UI/test changes (documented in AUDIT-4 §0). They are preserved untouched and are **excluded from the AUDIT-5 commit**. |
| Scope | Documentation only. No engine, data, build or UI file is modified by this pass. **Local commit only — no push.** |
| Precedence | **AUDIT-5 is the single consolidated index and the only P0 list in the set.** AUDIT-4 remains binding for C-1…C-14. AUDIT-1/2/3 are historical and partially superseded (one-line banner each, per directive §1.1). |
| Disposition | **NO-GO** for engineering sign-off — unchanged. E-20 strengthens it. |

## 1. Status & precedence

1. **AUDIT-4 is the binding adjudication** for C-1…C-14 and is left completely untouched by this pass.
2. **AUDIT-1, AUDIT-2 and AUDIT-3 are preserved verbatim as the historical record.** Each now carries exactly one banner line at the very top pointing to the relevant section here. **No audit body was edited by the AUDIT-5 pass.**
3. AUDIT-2's prior top banner — the one asserting a zero-defect OD/weight catalog — is **deleted, not rewritten** (directive §1.1). The corrected statement lives in §3.2 below; a banner that overturned a correct finding must not survive in any form.
4. After this pass the set contains **exactly one P0 list (§5)** and **exactly one severity vocabulary (§6)**.
5. New numbered finding introduced here: **V-16** — release signing falls back to the Android debug keystore (§2.6; P0 item 5 in §5). Evidence includes an executed diagnostic from the 16cf38f `lintRelease` run.
6. Compliance note on acceptance criterion 5 ("no document claims the cloud path is network-unreachable"): directive §1.1 forbids in-place body edits, so the withdrawn sentences remain physically present in AUDIT-1's historical body. They are **formally withdrawn by §2.1** and superseded by the banner; no document's *operative* claim set includes network-unreachability after this pass. The same mechanism covers acceptance criterion 6 for AUDIT-3 §2.3.
7. One further evidence-driven deviation from the directive is disclosed in §8 (the lint wording), in addition to the directive's own four corrections.

## 2. Errata — AUDIT-1

### 2.1 — V-01 mechanism/impact rewritten: source manifest vs merged manifest

- **Fact A (unchanged):** `app/src/main/AndroidManifest.xml` (37 lines) declares **zero** `<uses-permission>` elements.
- **Fact B (verified this pass):** the **merged release manifest** — `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml:11-13` — declares `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`, and `com.google.android.providers.gsf.permission.READ_GSERVICES`, merged in from the Firebase/GMS library manifests.
- **Withdrawn sentence 1** (AUDIT-1 V-01, Mechanism): *"The cloud path is unreachable twice over (feature gate + absent permission). Flipping `ENABLE_CLOUD_LLM=true` … would fail at the socket layer with `SecurityException`/`UnknownHostException`."* **The built APK is network-capable.** The operative gates are `ENABLE_CLOUD_LLM = false` (app/build.gradle.kts defaultConfig) and the placeholder provider seam (E-27: `FirebaseAiServiceImpl.kt:4-8, :22-33`; `CloudLlmConversationEngine.kt:37-60`). There is no transport-permission gate to fail at.
- **Withdrawn sentence 2** (AUDIT-1 V-14, Mechanism): *"The class currently has no consumer because the cloud client is unreachable (V-01)."* Corrected in §2.4.
- **Surviving V-01 substance, severity re-rated High → Medium:** (a) `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5` fails to distinguish the hand-written source manifest from the library-merged APK manifest (see D-1, §2.2); (b) seven network libraries ship on `releaseRuntimeClasspath` (AUDIT-1 §7 E-4) while the feature is gated off and its seam is a placeholder — an SBOM/surface mismatch; (c) the permission policy is undocumented. The "audit trail contradicts its own manifest" impact weakens from *false* to *imprecise* (§2.2), and the "cannot execute" component is withdrawn.

### 2.2 — D-1 re-rated: "False" → "Imprecise"

The claim *"android.permission.INTERNET is declared in AndroidManifest.xml for Firebase authentication / general connectivity"* (`docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5`) is **true of the shipped APK** (merged manifest, lines 11–13) and false only of the hand-written source file. The doc's real defect is **failing to distinguish the two** — the same error class AUDIT-1 was written to expose, present in AUDIT-1 itself until this erratum.

### 2.3 — Gate rows and §1 qualified

- Gate row *"Permission minimisation — PASS — Zero `<uses-permission>` in the manifest"* → **"PASS at source; the APK declares three normal-level permissions via library merge (`INTERNET`, `ACCESS_NETWORK_STATE`, `READ_GSERVICES`); zero dangerous/runtime-prompted permissions."**
- §1 statement *"zero declared runtime permissions were found"* → qualified identically: true (none of the three are dangerous/runtime-prompted), misleading if read as "the APK declares nothing."
- §1 point 3's clause *"an INTERNET permission that does not exist in `AndroidManifest.xml`"* → read as *"that does not exist in the source manifest"* (merged-manifest status per §2.2).

### 2.4 — V-14 premise corrected

`logging-interceptor` on `releaseRuntimeClasspath` (AUDIT-1 §7 E-4) is latent exposure gated by the feature flag and the placeholder seam (E-27), **not** by permission absence. Recommendation unchanged: move to `debugImplementation` or delete.

### 2.5 — "39 markdown audit/planning documents" (AUDIT-1 §4) scope-dated

The count describes the clean `16cf38f` tree **before** the audit documents themselves existed. The set now includes AUDIT-1…AUDIT-5.

### 2.6 — New finding V-16: release signing falls back to the Android debug keystore

- **Severity:** High (release integrity) · **Category:** build/release supply chain
- **Evidence:** `app/build.gradle.kts:39-87` — the `release` signing config resolves `keystore.properties` and, when the file or a valid password is absent, explicitly binds the debug keystore (`storePassword = "android"`, `keyAlias = "androiddebugkey"`). Executed corroboration: `build/audit/lint_release.log` from the 16cf38f `lintRelease` run printed `[SIGNING RESOLUTION] ---> WARNING: FALLING BACK TO DEBUG KEYSTORE (Reason: fileExists=false, validPassword=false)`.
- **Impact:** a release artifact built on a machine without the upload key is silently signed with the public debug key — unpublishable to Play and indistinguishable from a debug build to tooling.
- **Required correction:** fail the build when the resolved signer is the debug keystore (AUDIT-3 §8 item 11). P0 placement: §5, item 5.

## 3. Errata — AUDIT-2

### 3.1 — Banner deletion recorded

The top banner asserting a zero-defect OD/weight catalog has been **deleted** (directive §1.1). The corrected count is in §3.2. Nothing equivalent remains in the operative claim set.

### 3.2 — OD/weight count: 120 rows, all `IEC-Generic` (restores the original AUDIT-2 §3.5 count)

Per **AUDIT-4 C-13** (`Models.kt:304-313` — `od`/`wt` parsed with a zero default): an independent parse of the current `cable_catalog.json` returns 1,177 rows and **120 rows with missing/zero `od` or `wt`, all `IEC-Generic`** (first bad records: type 1, sizes 1.5/2.5/4). AUDIT-3 §2.3's zero-row statement is **not reproducible at this checkout** and is withdrawn in §4.2. The OD/weight estimation fallback (`isODApproximated`, `CableSizingEngine.kt:906-961`) is therefore load-bearing for all 120 rows — AUDIT-2's "PASS (handled and flagged)" verdict for that fallback stands, now with elevated importance.

### 3.3 — Verdicts amended by AUDIT-4 (and one by AUDIT-3), with evidence

| AUDIT-2 item | Action | Superseding ID | Corrected statement | Evidence |
|---|---|---|---|---|
| §2.7 **D-4** — demand-factor precedence, verdict "OK" | verdict withdrawn | **C-6** | `demandFactor` of **exactly 1.0** (and >1.0) is rejected and silently falls through to 0.90 (lighting) or the Table-10 RDF down to 0.60 (general). A user value of 1.0 — "no demand factor applied" — is the most common legitimate input and is not preserved. | `DiversityCalculationEngine.kt:185, :274` |
| §2.7 **D-6** — unbalance metric, verdict "OK-assumed (label it)" | amended; **the "NEMA MG-1 / IEC 61000-2-13" citation is removed outright, not relabelled** | **C-9** | The metric is (max−min)/avg spread, not NEMA max-absolute-deviation. Worked case (100/100/80): code yields **22.2 %**, max-deviation yields **11.1 %** — a 2× divergence. Present it only as a board spread metric, never as a standards-compliance value. | `OtherEngines.kt:489-503`; `SldModels.kt:277-278` |
| §2.5 **P-10/P-11** — ADS, verdict "OK (exemplary)" | amended ("exemplary" withdrawn for the fallback/model-scope claim) | **C-1, C-2** | The `deriveZeFromSource` algebra is verified, but: the **0.01 Ω fallback is real** on single-circuit paths (`ProtectionEngine.kt:153`; forwarded at `EngineeringCalculationCoordinator.kt:1071, :1731`; the freshness adapter publishes a fixed 0.01 Ω at `ProtectionAdsFreshnessAdapter.kt:44`); the model stops at the transformer-derived term; and **`parallelRuns` is never divided in the Zs loop** (`ProtectionEngine.kt:174-176`), so multi-run feeders receive overstated Zs and a false ADS FAIL. CPC parallelism is unmodelled. | `ProtectionEngine.kt:153, :174-176`; `EngineeringCalculationCoordinator.kt:1174-1178` |
| §2.8 **B-6** — busway, verdict "OK" | amended | **E-21** | `ipkKA` is carried in the result but **never compared** against a network peak; the sizing decision uses only `icwKA` vs RMS `networkIscKA`. Peak/dynamic withstand is unestablished. | `BuswayEngine.kt:8-24, :142-179` |
| §2.9 **M-5** — ATS, verdict "OK" | amended | **C-8** | `selectedLoad = if (loadCoverage == "Emergency Only") essentialLoadKVA else totalConnectedLoad` — the assigned-circuit filter is used only for a presence check. Multi-ATS projects size every instance to the whole building; single-ATS projects are numerically unchanged when assigned load equals project total. | `AtsEngine.kt:45-48, :56-58` |
| §2.10 **X-5/X-6** — conduit | extended | **E-23, E-24** | The PVC type filter is a **no-op** (`|| input.conduitType == "Rigid PVC"` admits every catalog row, so a PVC request can return steel); a manual trade-size override that does not match falls back to `candidateList.last()` instead of being rejected. (AUDIT-3 findings; confirmed by direct read this pass.) | `ConduitFillEngine.kt:247-251, :320-325, :411-441` |
| §2.11 **K-3** — power triangle, verdict "OK" | amended | **E-28** | `cosPhi` coerced to [0.1, 1.0] with a positive-root Q silently converts leading/invalid power-triangle inputs into a plausible lagging result. | `ElectricalConverters.kt:248-266, :271-280` |
| §5 **credit #1** — "fail-closed LLM gate" | qualified | **E-27** | The gate is fail-closed, but the enabled path returns a **confident fixed-text response with no provider call** — fail-closed ≠ fails-visibly. | `FirebaseAiServiceImpl.kt:4-8, :22-33`; `CloudLlmConversationEngine.kt:37-60` |

### 3.4 — Internal count fix

§5's E-06 header says "Five duplicated constant sets (a–e)"; §4 lists **six** (a–f). Read as six.

## 4. Errata — AUDIT-3

### 4.1 — Derating inversion is at 45 °C only; "reverses at 50 °C" is withdrawn

AUDIT-3 §5.4 and the §4 E-02 row state that the XLPE/PVC relationship "reverses at 50 °C." **Withdrawn.** Per **AUDIT-4 C-5**, the live table (`derating_data.json:34-44`) is:

| Ambient | PVC | XLPE | Ordering |
|---:|---:|---:|---|
| 45 °C | 0.95 | 0.90 | **inverted** (XLPE < PVC) |
| 50 °C | 0.82 | 0.89 | correct (XLPE > PVC) |
| 55 °C | 0.71 | 0.84 | correct (XLPE > PVC) |

**Mechanism of propagation:** the bucket rule `keys.firstOrNull { it >= temp } ?: keys.last()` (`CableSizingEngine.kt:170`) selects the 45 °C row for every temperature in **40 < T ≤ 45**, so the single inverted row governs that entire interval — not the 50/55 rows. The defect statement in AUDIT-2 E-02 (45 °C only) was correct as written.

### 4.2 — OD/weight: 120 rows, not zero

AUDIT-3 §2.3's statement that `cable_catalog.json` has 0 rows with non-positive OD/weight is **withdrawn** — it is not reproducible at this checkout. Per **AUDIT-4 C-13**: 1,177 rows, **120 rows** with missing/zero `od`/`wt`, all `IEC-Generic`. See §3.2.

## 5. Consolidated backlog — the single P0/P1/P2 list

This is a **remediation backlog**, not a label-inflation exercise. After this pass, exactly one such list exists in the set (this one). Definitions: **P0** blocks release; **P1** blocks a defensible engineering deliverable; **P2** quality/transparency. Every entry carries the superseding/owning ID and `file:line` evidence.

### P0 — blocks release (5)

| # | Item | IDs | Evidence | Why it gates release |
|---|---|---|---|---|
| **1** | **Test gate** — stale version assertions + one designated green CI profile | V-06, V-13 | `Phase7E1DurableSchemaTest.kt:208, :1133`; `AppDatabase.kt:1064`; test-profile block in `app/build.gradle.kts` | **Placed first deliberately.** It is the cheapest item on the list and it **gates the evidentiary value of every item below**: without a trustworthy green profile there is no mechanism to prove an E-20 or C-2 fix actually works, nor that it broke nothing else. Fixing safety defects on top of a suite where "the tests pass" is not a meaningful statement produces unverifiable remediation. |
| **2** | **Busbar derating bypass** | E-20 | `BusbarSizingEngine.kt:218-223, :264` | `isAmpOk = derated… >= req·0.99 \|\| candidateRating >= req·0.99` — a 630 A nominal profile at IP54 (0.80) has 504 A derated capacity yet can PASS against a 600 A requirement. PASS-when-unsafe on a default-adjacent input. |
| **3** | **ADS loop basis** | E-22, C-1, C-2 | `EarthingEngine.kt:163-183`; `ProtectionEngine.kt:153, :174-176`; `EngineeringCalculationCoordinator.kt:1174-1178` | Displayed Zs uses the 3-phase Isc variable rather than Ik1 and omits conductor/electrode loop terms; 0.01 Ω `Ze` fallback is real on single-circuit paths; `parallelRuns` is never divided in the Zs loop → overstated Zs / false ADS FAIL on multi-run feeders. |
| **4** | **Typed conductor data — no string parsing in safety checks** | E-25, E-10 | `ShortCircuitEngine.kt:49-51, :174, :297-300`; `BuswayEngine.kt:189`; `ProtectionEngine.kt:319-321` | Display strings reparsed as engineering areas ("800A Busway" → 800 mm²); unparseable strings silently become 1.5 mm². Invented conductor areas feed the adiabatic verdict. |
| **5** | **Debug-keystore release signing fallback** | V-16 (new) | `app/build.gradle.kts:39-87`; executed diagnostic in `build/audit/lint_release.log` | A "release" artifact is silently signed with the public debug key when the upload key is absent. |

### P1 — blocks a defensible deliverable (7)

| # | Item | IDs | Evidence |
|---|---|---|---|
| 1 | XLPE 45 °C derating row inverted (bucket-propagated) + grouping frozen above six circuits | E-02 / C-5, E-01 | `derating_data.json:34-44`; `CableSizingEngine.kt:170, :261` |
| 2 | Voltage-drop resistance temperature basis + false provenance labels | E-03 / C-3 | `CableSizingEngine.kt:611-613`; `Models.kt:29-32`; `QuickEngineeringDataBridge.kt:67-72` |
| 3 | Busway peak withstand never evaluated | E-21 | `BuswayEngine.kt:142-179` |
| 4 | Multi-ATS instances sized from project-global load | C-8 | `AtsEngine.kt:45-48, :56-58` |
| 5 | Ratio-only coordination emitted as PASS without selectivity evidence | C-11 | `CoordinationEngine.kt:36-45`; `ProtectionEngine.kt:275-276` |
| 6 | Conduit type filter no-op, silent manual-override substitution, PVC-hardcoded roll-up | E-23, E-24, E-09 | `ConduitFillEngine.kt:247-251, :320-325, :411-441` |
| 7 | UPS double margin (unit) vs DoD-omitted battery Ah on `systemVoltage` instead of EOD voltage, fixed 100 Ah strings | C-7 / E-26, E-06(d), E-17 | `UpsEngine.kt:74-84, :120-121`; `Entities.kt:247` |

### P2 — quality and transparency (all remaining open items)

V-02 (`res/xml/file_paths.xml:3-6`), V-05 (`AppDatabase.kt:1084-1106`), V-01 (doc + permission policy; `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5`), V-03 (`GlobalSettingsScreen.kt:211-226`; `GlobalPreferences.kt:67-72`; `EngineeringAssistantViewModelFactory.kt:43-60`), E-27 (placeholder cloud seam), E-28 (converter PF domain), and: V-04, V-07, V-08, V-09, V-10, V-11, V-12, V-14, V-15, E-05, E-06 (remainder), E-07, E-08, E-12, E-13, E-14, E-15, E-16, E-19, C-9, C-10, C-12.

## 6. Unified severity scale

One vocabulary applies to all five documents from this pass onward. **AUDIT-1 §2's scale is adopted, with Medium-High added formally** (V-13 and E-10 already use it).

| Level | Definition |
|---|---|
| **Critical** | Exploitable by a hostile local app/user without special conditions, or a safety verdict provably wrong in the unsafe direction on default inputs. |
| **High** | Exploitable with a plausible precondition, or a claim/verdict provably wrong in a way that could mislead a certifying engineer; in engineering terms, can change a protection, sizing or procurement verdict in an unsafe direction. |
| **Medium-High** *(added formally)* | Can silently produce an incorrect design output or materially weaken release assurance, but with a narrower trigger condition, smaller blast radius, or a recoverable detection path than High. |
| **Medium** | Defence-in-depth erosion, silent failure of a guard, or a latent defect that will bite on the next change; in engineering terms, can silently produce an incorrect design or materially weaken release assurance. |
| **Low** | Hygiene, diagnostics quality, maintainability, minor information exposure. |
| **Info** | Observation or credit; no action required. |

### Mapping of legacy labels

| Legacy label (source) | Unified |
|---|---|
| Critical (AUDIT-1 §2) | Critical |
| High (AUDIT-1 §2; AUDIT-3 §3) | High |
| Medium-High / Med-High (AUDIT-1 V-13; AUDIT-2 E-10) | Medium-High |
| Medium / Med (all reports) | Medium |
| Low-Medium (AUDIT-1 V-07) | Medium |
| Low/Medium (AUDIT-3 E-28) | Medium (conservative upper bound) |
| Low (all reports) | Low |
| Info (all reports) | Info |

**Re-maps recorded by this document:** V-07 Low-Medium → Medium; E-28 Low/Medium → Medium; V-13 and E-10 Medium-High confirmed; **V-01 High → Medium** (§2.1); **V-16 introduced at High** (§2.6).

## 7. Meta-finding — the verify-before-acting rule

Recorded because it governs how the next unverified report is handled.

- **Confirmed by line-level adjudication (5):** the busbar derating bypass (**E-20**), the missing `parallelRuns` divisor in the Zs loop (**C-2**), multi-ATS global-load assignment (**C-8**), the demand factor rejected at exactly 1.0 (**C-6**), and the debug-keystore signing fallback (**V-16**, AUDIT-3 §6 blocker 1, now numbered here).
- **Confirmed via a worked case:** the unbalance ≈2× overstatement claim — **C-9**: for phases 100/100/80 the code yields 22.2 % (spread/average) while the max-deviation definition yields 11.1 %.
- **Directionally wrong but not baseless:** the UPS claim. The net ≈25 % oversizing from margin-then-÷0.8 stands (E-06(d), AUDIT-2 §4), and **C-7/E-26 confirm the DoD omission** plus the separate `systemVoltage` vs `batteryEodVoltage` error (`UpsEngine.kt:83-84`; `Entities.kt:247`) — two different quantities pointing in different directions.
- **Rejected magnitude:** the claimed 28 % XLPE voltage-drop understatement is contradicted by the code — the XLPE path already consumes `R_Cu_70_est` (`CableSizingEngine.kt:592-598`), so the true gap versus a 90 °C basis is ≈6 % (**C-3**).
- **Conclusion:** *not* "the external audit was reliable." It carried no commit hash, no verified line quotes, and stated at least one magnitude the code contradicts. The correct conclusion is that the **verify-before-acting rule worked exactly as intended** — it converted plausible claims into adjudicated findings without ever letting an unverified claim reach the code. The rule stands unchanged: any report without a commit hash and `file:line` quotes enters as **CLAIMED** — never as a finding, and never authorising a code change.

## 8. Still not certified

The volume of documentation in this set must not be read as closure. Every limit below remains open.

1. **Nothing has been executed at the current checkout.** All executable evidence in AUDIT-1 §7 (unit-test profiles, dependency graph, lint) was produced at `16cf38f`. AUDIT-3 §2.2 and AUDIT-4 §4 record Gradle failing **before task execution** in their environments (loopback `SocketException`). Every item in §5 must be closed against a clean run at HEAD, not against 16cf38f evidence.
2. **lintRelease:** completed at `16cf38f` only (AUDIT-1 §7 E-5 — BUILD SUCCESSFUL 4 m 59 s; 251 findings, 1 error → V-15; none security-tagged; SARIF/HTML at `app/build/reports/lint-results-release.{sarif,html}`). It has **not** been executed at `9929d35`/`d4d0fd4`.
   - *Directive deviation, disclosed:* the directive's §8 template read "lintRelease never completed (AUDIT-1 E-5)." That wording would falsify the record — AUDIT-1 E-5 documents a completed, logged run. The accurate carry-forward is "**not executed at the current checkout**," which preserves the intended caution without erasing executed evidence.
3. **E-08's blast radius is still untraced** — whether `transformers_seed.json`'s inconsistent R/X/Isc columns reach any UI, PDF or AI-context rendering site.
4. **E-19's manufacturer provenance is unverified** — the Ducab/Elsewedy/Riyadh Cables table headers (reference ambient, soil resistivity, installation sub-method) have not been inspected.
5. **The `CircuitEditorSessionRuntimeTest` isolation artifact was never bisected** (AUDIT-1 §9.4). Note: the working tree carries an uncommitted, strengthened variant of this test (40 dp breathing-space assertions) plus modified editor shells; its effect on the isolation classification is unknown and unexecuted.
6. **No runtime instrumentation, no device/emulator run, no external A/B** against ETAP / DIgSILENT / a reference workbook.
7. **The Compose UI, SLD geometry and PDF renderer remain pattern-screened only**; no UI/accessibility review has been performed.
8. **Disposition unchanged: NO-GO for engineering sign-off.** No remediation work begins until the consolidated P0 (§5) is approved.

## 9. Register corrections & P0 remediation record (2026-09-17)

### 9.1 — Pending register corrections (§5 handoff batch)

1. **Test suite census (AUDIT-1 §7 E-2):** AUDIT-1 §7 E-2's cited count `1882` was stale. The true total test count across all suites at HEAD is **2260**. The designated green CI profile (`-Pprofile=gate`) runs **2209 tests** (0 failures, 13 skipped in ~4m10s).
2. **New finding V-17 (High) — `-Pprofile=full` hang:** The `-Pprofile=full` profile wedges and hangs indefinitely; it is not merely failing assertions. At HEAD it wedged following the `CircuitEditDialogDiagnosticIsolationTest` `AppNotIdleException` cases — log output froze at 13,379 bytes, with the Gradle test worker process (PID 20916, 1356 MB) consuming 0% CPU across a 60-second window, requiring termination after ~14 minutes (`build/audit/full_at_head_HUNG_1202.log`). The CI gate (`-Pprofile=gate`) excludes those three legacy diagnostic suites and completes reliably in ~4 minutes. This upgrades **V-13** from "no green profile" to "one verified green CI gate profile, one unrunnable diagnostic profile."
3. **AUDIT-5 metadata & phrasing errata:**
   - AUDIT-5 header table line 7 originally cited `d4d0fd4`; the true delivery commit of that pass was `97b419c`.
   - AUDIT-5 §1 point 2 phrasing corrected: "No audit body was edited by the AUDIT-5 pass."
4. **Release signing debug key line citation:** In earlier registers, `keyAlias = "androiddebugkey"` was cited inconsistently as `:79` or `:89`. In `app/build.gradle.kts` prior to remediation, the line was `:77` (with `debugConfig` also declaring it at `:77`).
5. **Deferred maintenance notice:** `AssistantLazyLifecycleTest.kt:152` creates a ViewModel without explicitly clearing `viewModelScope`. The pattern mirrors the Main-dispatcher leak resolved in `f4791ae`. Because 5/5 gate runs passed without flaking, this fix is deferred unless flake recurs.
6. **Isolation artifact status:** `CircuitEditorSessionRuntimeTest` isolation artifact remains open and unbisected; it manifests only under the `-Pprofile=full` profile and passes deterministically under `-Pprofile=gate`.
7. **Release lint status:** `lintRelease` was executed and completed at two commits with identical results (251 findings, 1 error = V-15 `ContextCastToActivity @ ProjectPickerScreen.kt:794`). Nothing additive.

### 9.2 — P0 Remediation record (closed items 1–5)

All five P0 items identified in AUDIT-5 §5 have been remediated, verified against the fail-first standard, and committed locally:

| Item | Finding IDs | Primary Files | Commit | Gate Status |
|---|---|---|---|---|
| **P0-1** | V-06, V-13 | `Phase7E1DurableSchemaTest.kt`, `app/build.gradle.kts` | `1f54c9f` + `f4791ae` | 2202 passed, 0 failed |
| **P0-2** | E-20 | `BusbarSizingEngine.kt`, `BusbarSizingEngineTest.kt` | `7d3d82b` | 2204 passed, 0 failed |
| **P0-3a** | E-22 | `EarthingEngine.kt`, `EarthingEngineTest.kt` | `800b004` | 2205 passed, 0 failed |
| **P0-3b** | C-1 | `ProtectionEngine.kt`, `ProtectionEngineTest.kt` | `57b5cb3` | 2206 passed, 0 failed |
| **P0-3c** | C-2 | `ProtectionEngine.kt`, `ProtectionEngineTest.kt` | `e99f4a4` | 2207 passed, 0 failed |
| **P0-4** | E-25, E-10 | `CableSizingEngine.kt`, `ProtectionEngine.kt`, `ShortCircuitEngine.kt`, `ProtectionEngineTest.kt`, `ShortCircuitEngineTest.kt` | `d4781b7` | 2209 passed, 0 failed |
| **P0-5** | V-16 | `app/build.gradle.kts` | `6f61cef` | 2209 passed, 0 failed |

### 9.3 — Product disposition

**NO-GO for engineering sign-off.**
Remediation of all 5 P0 release-blocking defects and achieving a 100% green CI gate profile (`-Pprofile=gate`, 2209 tests passed, 0 failures, 13 skipped) are mandatory prerequisites, but they **do not constitute engineering certification**.
Seven P1 defects (E-02/C-5 XLPE 45 °C derating inversion & grouping; E-03/C-3 voltage-drop temperature basis; E-21 busway peak withstand; C-8 multi-ATS global sizing; C-11 ratio-only selectivity; E-23/E-24/E-09 conduit filter and override; C-7/E-26 UPS DoD omission and battery voltage) remain open. The software must not be signed off for safety-critical deliverables until P1 remediation is complete.

---

*End of AUDIT-5. AUDIT-4 remains binding for C-1…C-14; this document is the single consolidated index and P0 source. Local commit only — no push.*