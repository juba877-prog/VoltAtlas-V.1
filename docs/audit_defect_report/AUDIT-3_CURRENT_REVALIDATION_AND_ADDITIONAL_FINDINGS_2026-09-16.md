# VOLTATLAS V.1 — AUDIT-3: CURRENT CHECKOUT REVALIDATION & ADDITIONAL FINDINGS

> **Partially superseded.** See [AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md](AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md) §4 for corrections to this document. AUDIT-4 adjudicates C-1…C-14; AUDIT-5 is the consolidated index and the single P0 source.

| Field | Value |
|---|---|
| Artifact | `docs/audit_defect_report/AUDIT-3_CURRENT_REVALIDATION_AND_ADDITIONAL_FINDINGS_2026-09-16.md` |
| Review date | 2026-09-16 (Africa/Cairo) |
| Repository / HEAD | `E:\voltatlas-v.1 (4)` / `9929d35ef5ec85938e4dca7d4d1e28453d44ac18` |
| Review scope | Current Android/Kotlin checkout, engineering engines, input validation, catalog/matrix assets, build/release configuration, and existing audit evidence |
| Companion reports | `AUDIT-1_SECURITY_AND_DEFECT_REPORT.md`, `AUDIT-2_ELECTRICAL_EQUATION_AND_MATRIX_REVIEW.md` |
| Product source changes | None. This revalidation adds documentation only. |

## 1. Executive decision

**Do not use the current build as an engineering design sign-off tool until the High-severity calculation and data-integrity findings below are corrected and regression-tested.**

The repository has a substantial and generally coherent calculation core. Several basic equations independently reproduce correctly, the JSON assets parse, and the main safety engines contain useful assumptions and warnings. The release decision is nevertheless **NO-GO** because at least one busbar path can return `PASS` when the derated ampacity is below the required current, and other paths can select or report an electrical component using an incorrect type, fault-current basis, or display-string interpretation.

This document is a current-checkout addendum. The two companion reports remain valuable, but their source commit predates the current HEAD. Where this document conflicts with a numeric count in an older report, this document is the current evidence.

## 2. Evidence and verification status

### 2.1 Static coverage

- 306 production Kotlin files were present under `app/src/main/java`.
- 225 Kotlin test files were present under `app/src/test/java`.
- The review covered the engineering engines, quick-tool models, AI trust boundary, Room/build configuration, Android manifest/provider rules, production logging, and all 13 JSON assets under `app/src/main/assets`.
- No product source code was modified during this review.

### 2.2 Test execution limitation

The Gradle unit-test task was attempted, but this environment did not reach test execution:

1. The wrapper initially could not obtain its Gradle distribution lock because the user Gradle cache was access-restricted.
2. An escalated retry and a locally installed Gradle distribution both failed before tests with `java.net.SocketException: Invalid argument: connect` while Gradle attempted to establish its loopback connection.

Therefore, the test suite is **not certified green**. This is an environment/test-run limitation, not evidence that the source failed the tests. Static inspection and independent asset checks below were completed instead. A clean CI or developer-machine run is required before closing any finding.

### 2.3 Current asset checks

The following checks were performed independently against the current checkout:

| Matrix / asset | Current result | Interpretation |
|---|---:|---|
| JSON assets | 13/13 parsed | No syntax-level JSON failure found. |
| `cable_catalog.json` | 1,177 rows; 0 rows with `od <= 0` or `wt <= 0` | Corrects an older report sentence that described 120 incomplete rows; that count is not true for this checkout. |
| Cable manufacturers | Elsewedy 558; IEC-Generic 120; Ducab 122; Riyadh Cables 377 | Distribution is plausible but is not proof of catalogue correctness. |
| Cable `air/gnd` ratio | minimum 0.686; maximum 1.486; average 0.972 | The fields are not a single normalized reference system; source/provenance review remains required. |
| Grouping matrices | All grouping arrays have six entries | The data shape is valid, but the engine still clamps inputs above six circuits. |
| Breaker matrix | 52 rows; no basic `Icu/Ics/Icm` ordering or non-positive-value failure found | Basic matrix integrity passed; engineering certification still requires source traceability. |
| Busway matrix | 36 rows; no non-positive rating, `Icw`, `R`, or `X` failure found | Basic matrix integrity passed; peak-withstand use is incomplete in code. |
| Conduit matrix | 21 rows; geometric fill values within the checked tolerance | Formula data shape passed; type-selection and procurement code still fail. |
| Transformer seeds | 6 rows; all basic fields present, but impedance/current values are inconsistent with `%Z`/kVA arithmetic | See `E-08` and the current examples in §5. |

## 3. Current high-priority finding register

Severity meanings: **High** can change a protection, sizing, or procurement verdict in an unsafe direction; **Medium** can silently produce an incorrect design or materially weaken release assurance; **Low** is a correctness, maintainability, or transparency defect that should still be closed before a professional release.

### E-20 — Busbar derating can be bypassed by nominal rating

- **Severity:** High — engineering safety verdict
- **Evidence:** `app/src/main/java/com/example/engine/BusbarSizingEngine.kt:218-223` and `:264-266`.
- **Defect:** Profile selection accepts `prof.nominalAmpacityA >= candidateRating` even when `deratedCap < reqCurrentA`. The final `isAmpOk` repeats the same `OR candidateRating >= reqCurrentA` bypass.
- **Failure example:** A profile with 630 A nominal capacity, an IP54 factor of 0.80, and a 600 A required current has only 504 A derated capacity. The current code can still select/pass it because 630 A nominal is at least the candidate rating/current.
- **Impact:** The result can display `PASS` while the calculated continuous thermal capacity is below the design current.
- **Required correction:** Make the derated continuous ampacity the sole acceptance condition for continuous current. Treat the manufacturer/rated-current value as an input to sizing, not an alternative to the thermal check. Add boundary tests for IP54/IP65, elevated ambient, aluminium/finish factors, and manual ratings.

### E-21 — Busway short-circuit check ignores peak withstand

- **Severity:** High — short-circuit safety completeness
- **Evidence:** `app/src/main/java/com/example/engine/BuswayEngine.kt:8-24, :142-179`.
- **Defect:** The result carries `ipkKA`, but the sizing decision compares only `selected.icwKA` with `networkIscKA`. No network peak current (`κ·√2·I″k`, or an explicitly supplied peak) is calculated and compared with the selected busway `ipkKA`.
- **Impact:** A busway can pass the short-circuit check on RMS short-time withstand while its peak making/dynamic withstand is not established.
- **Required correction:** Add a named peak-current input or calculate it from the same declared short-circuit method used elsewhere; compare it with catalog `ipkKA`; emit `REVIEW REQUIRED` when the network X/R or peak factor is unknown.

### E-22 — Earthing loop impedance uses the wrong fault-current variable and is not a conductor/electrode loop model

- **Severity:** High — protection/ADS verdict
- **Evidence:** `app/src/main/java/com/example/engine/EarthingEngine.kt:163-183`.
- **Defect:** The code correctly chooses `designFaultKa` from supplied line-to-earth current when sizing the protective conductor, but computes `prospectiveFaultA` from the three-phase `faultKa` and then calculates `zs = u0 / prospectiveFaultA`. The supplied line-to-earth current is therefore ignored for the displayed loop impedance. Further, the displayed `Zs` is an inverse-fault-current estimate, not the measured/calculated line conductor + protective conductor + source + electrode loop required for an ADS verification.
- **Impact:** If `Ik1` is lower than three-phase Isc, the displayed `Zs` is understated. The result can state a loop check based on a different fault model from the one used for the CPC calculation.
- **Required correction:** Separate `Zs_source`, `Zs_conductor`, and electrode/earth-return terms, or explicitly rename the output as an inverse prospective-current estimate and prevent it from being labelled as a complete ADS loop check. Use the same fault basis throughout and require the designer to provide missing source/loop impedance data rather than silently substituting a different quantity.

### E-23 — Conduit type filtering and procurement roll-up are hardcoded incorrectly

- **Severity:** Medium — procurement/design selection
- **Evidence:** `app/src/main/java/com/example/engine/ConduitFillEngine.kt:247-251` and `:411-441`.
- **Defect:** When the requested type is `Rigid PVC`, the filter condition is true for every catalog row because of `|| input.conduitType == "Rigid PVC"`. The candidate list can therefore include EMT and GI steel. The procurement roll-up then groups every result as `Rigid PVC` and emits `PVC-...`, regardless of the selected catalog entry.
- **Impact:** A user asking for PVC can receive a steel conduit recommendation or a procurement record that misstates the material.
- **Required correction:** Filter by the requested type only; if no exact type exists, return a review/data-missing result rather than using the entire catalog. Carry `conduitType` and catalog `id` in `ConduitFillResult`, group on those fields, and generate procurement codes from the selected entry.

### E-24 — Manual conduit override silently selects a different trade size

- **Severity:** Medium — user-intent/data integrity
- **Evidence:** `app/src/main/java/com/example/engine/ConduitFillEngine.kt:320-325`.
- **Defect:** If a manual trade-size override does not match, the code chooses `candidateList.last()` instead of rejecting the override.
- **Impact:** The displayed result can look like the requested manual selection was accepted when a different size was used.
- **Required correction:** Return `INVALID_INPUT` or `REVIEW REQUIRED` with the requested and available values. Never substitute a different manual selection silently.

### E-25 — Typed numeric sizing data is converted to display strings, then reparsed

- **Severity:** High — cross-engine safety integrity
- **Evidence:** `app/src/main/java/com/example/engine/ShortCircuitEngine.kt:49-51, :174, :297-300`; `app/src/main/java/com/example/engine/BuswayEngine.kt:189`.
- **Defect:** Short-circuit thermal checking parses the leading number from `SizingResult.cableSize_mm2`. A busway is represented as text such as `800A Busway (...)`, so `800` can be interpreted as `800 mm²`. Any other display prefix, multiplier, or localized string can similarly produce an incorrect area or a null fallback.
- **Impact:** The short-circuit result may be based on an invented conductor area. This is especially unsafe when an equipment type is represented by a human-readable label rather than a cable area.
- **Required correction:** Add typed fields such as `phaseConductorAreaMm2`, `parallelRuns`, `buswayRatingA`, and `withstandIcwKA` to the domain result. Make the short-circuit engine consume typed values only. Treat missing values as `DATA MISSING`; never infer an engineering quantity from a UI label.

### E-26 — UPS battery calculation ignores end-of-discharge voltage and hardcodes 100 Ah strings

- **Severity:** Medium — battery sizing accuracy
- **Evidence:** `app/src/main/java/com/example/engine/UpsEngine.kt:83-84, :120-121`.
- **Defect:** Battery Ah is calculated using `settings.systemVoltage`, while the settings model exposes `batteryEodVoltage`. String count is `ceil(batteryAh / 100.0)` regardless of selected battery block capacity or series/parallel topology.
- **Impact:** The reported battery bank can be materially wrong for systems whose end-of-discharge voltage differs from nominal or whose battery unit is not 100 Ah. This is a purchased-quantity and runtime defect.
- **Required correction:** Model battery block voltage, end-of-discharge voltage, Ah rating, series count, parallel strings, temperature/aging/DoD factors, and manufacturer discharge curves explicitly. If the model is only a preliminary energy estimate, label it as such and do not present the hardcoded string count as a final design.

### E-27 — Cloud AI production seam is a placeholder, not a working provider integration

- **Severity:** Medium — functional/release integrity
- **Evidence:** `app/src/main/java/com/example/ai/service/FirebaseAiServiceImpl.kt:4-8, :22-33`; `app/src/main/java/com/example/ai/hybrid/CloudLlmConversationEngine.kt:37-60`.
- **Defect:** The Firebase service returns a fixed acknowledgement, and `RealGoogleGenAiClientSeam.generate` returns a fixed text after checking only that an API key exists. No provider request is made.
- **Impact:** If the feature gate is enabled, the app can appear to have processed an engineering request while it has not asked a model or produced a backed engineering result.
- **Required correction:** Keep the feature disabled until a real provider adapter is implemented, instrumented, and tested. The UI must say `NOT IMPLEMENTED`/`DEGRADED` rather than presenting a placeholder as a response. Add contract tests proving that every cloud response either has a provider result and backing tool result or is explicitly degraded.

### E-28 — Power-triangle converters silently force invalid/leading inputs into a lagging domain

- **Severity:** Low/Medium — equation-domain integrity
- **Evidence:** `app/src/main/java/com/example/engine/converters/ElectricalConverters.kt:248-266` and related converter methods at `:271-280`.
- **Defect:** `cosPhi` is coerced to `[0.1, 1.0]`, and reactive power is derived with a positive square root. Negative power factor, negative/leading kVAR, zero power factor, and invalid values are therefore converted into a plausible lagging result rather than rejected or represented with a sign.
- **Impact:** A user entering a leading or invalid power-triangle case can receive internally inconsistent P/Q/cosφ values without a review state. This is not a problem for a deliberately lagging-only tool, but the UI and model do not declare that restriction.
- **Required correction:** Either explicitly constrain the product to positive lagging loads and reject other domains, or carry signed reactive power/leading-lagging state through all converter results. Do not silently transform invalid electrical inputs into valid-looking outputs.

## 4. Existing findings revalidated in the current checkout

The following companion-report findings remain confirmed and should be treated as open unless fixed in a later change:

| ID | Finding | Current evidence / consequence |
|---|---|---|
| E-01 | Cable grouping table is capped at six circuits | `CableSizingEngine.kt:220-222, :261`; `noCircuits.coerceIn(1, 6)` silently freezes the six-circuit factor for larger groups. |
| E-02 | XLPE air derating data is non-monotone/inverted at 50–55 °C | `derating_data.json:39-44`; XLPE is 0.89 at 50 °C and 0.84 at 55 °C while PVC is 0.82 and 0.71, reversing the expected relative ordering at 50 °C. Confirm against the adopted standard table and correct the source data. |
| E-03 | Cable resistance labels/provenance do not match code semantics | `CableSizingEngine.kt:287-300, :593-613`; the code aliases labels such as `R_Cu70`/`R_Cu90` and deliberately uses `thermalFactor = 1.0`, so the displayed temperature basis is not reliably the operating-temperature basis. |
| E-04 | Earthing `Zs` is not a complete ADS loop calculation | Revalidated above as `E-22`; the code’s CPC fault basis and displayed `Zs` basis differ. |
| E-05 | Equipotential bonding conductor is set equal to the selected earth conductor | `EarthingEngine.kt:175`; this is not a complete rule-based bonding-size calculation. |
| E-06 | Duplicate motor-start, ATS, generator, and UPS constants diverge | `GeneratorEngine.kt`, `QuickNewToolsModels.kt`, `AtsEngine.kt`; quick and full tools can produce different answers for the same nominal input. |
| E-07 | Panel schedule hardcodes 230 V and 0.8 power factor | `OtherEngines.kt:485-497`; the schedule does not use each circuit’s voltage/power-factor data for those outputs. |
| E-08 | Transformer seed R/X/Isc values do not reconcile with `%Z`, kVA, and X/R | Current seed spot-checks are listed in §5. |
| E-09 | Conduit roll-up hardcodes PVC | Revalidated above as `E-23`. |
| E-10 | Short-circuit safety parses display strings | Revalidated above as `E-25`. |
| E-11 | Cable derating ranges are silently clamped | `CableSizingEngine.kt` bucket helpers use the first/last available row; out-of-range ambient, depth, and soil values need explicit review or extrapolation rules. |
| V-01 | Manifest and AI security audit disagree about Internet permission | `AndroidManifest.xml` has no `<uses-permission>` while `docs/ai_audit/11_AI_SECURITY_PRIVACY_NETWORK_AUDIT.md:5` says Internet is declared. |
| V-02 | FileProvider exposes whole cache/files roots | `res/xml/file_paths.xml:3-6` uses `path="."` for four roots. |
| V-03 | Proposal approval identity is user-editable | `GlobalSettingsScreen.kt:211-226`, `GlobalPreferences.kt:67-72`, and `EngineeringAssistantViewModelFactory.kt:43-60`; a preference is used as the trusted principal. |
| V-04 | Room schema export is disabled | `AppDatabase.kt:1064-1065`; no committed schema snapshots are available for migration review. |
| V-05 | Test executor switch can enable main-thread queries | `AppDatabase.kt:1084-1106`; public mutable `useTestExecutors` controls `allowMainThreadQueries()`. |
| V-06 | AI migration test still expects Room version 24 while production is 26 | `Phase7E1DurableSchemaTest.kt:208,1133`; AI tests are excluded from the default fast profile in `app/build.gradle.kts:218-242`. |
| V-07 | Production SLD gesture logging uses `Log.d` | `SldCanvas.kt:114,134-184`; logs may expose project/node identifiers and add release noise. |
| V-08 | Catalog parsing uses `println` and `printStackTrace` | `CatalogRepository.kt:180-181`; use structured, release-filtered logging and a surfaced data-error result. |
| V-09 | Broad R8 keep rules reduce shrinking/obfuscation | `app/proguard-rules.pro:38-41`; replace package-wide keep rules with serializer/reflective entry-point rules. |
| V-10 | Backup rules exclude only the database | `AndroidManifest.xml:5-7` and backup XML; shared preferences, project files, and external files are not clearly excluded. |
| V-14 | Logging interceptor is on the implementation classpath | `app/build.gradle.kts:183`; move it to a debug-only configuration unless it is deliberately required in release. |

## 5. Electrical equation and matrix assessment

### 5.1 Equations independently supported by the current implementation

These equation families have sound algebra/unit structure in the inspected code, subject to the input/data defects listed elsewhere:

| Family | Current assessment |
|---|---|
| Three-phase and single-phase load current | `CableSizingEngine` and converter formulas use the expected `S·1000/(√3·V)` and `S·1000/V` forms. |
| Cable derating combination | Multiplicative `Ct·Cd·Cs·Cg` structure is correct; the table coverage and bucket policy are not sufficient for all inputs. |
| Cable voltage drop | The 1P/3P factors, length conversion, and R/X structure are coherent; temperature basis and provenance need correction. |
| Short-circuit impedance/current | Series R/X accumulation and `I″k = c·U/(√3·Z)` structure are coherent; downstream cable-area parsing is not acceptable. |
| Cable adiabatic withstand | `I·√t/k` structure is correct; it must consume typed areas and a consistent fault-current basis. |
| Transformer impedance | `%Z` to mΩ conversion and X/R decomposition are coherent in `TransformerEngine`; seed values are not coherent with the same equation. |
| Earth rod resistance | The single-rod logarithmic structure is recognizable and dimensionally consistent; soil model, spacing efficiency, ring assumptions, and ADS loop model need engineering review. |
| Conduit fill | `Σ(π·OD²/4)` divided by internal area and compared with a standard fill percentage is correct; type-selection and override logic are defective. |
| Busbar thermal withstand | `I·√t/k` structure and peak-factor helper are present; continuous ampacity acceptance has the `E-20` bypass. |
| Power/conversion helpers | The inspected voltage, power, current, AWG/SWG and capacitor-bank relationships are algebraically consistent in their normal domain; input-domain checks still need strengthening. |

### 5.2 Transformer seed reconciliation

Using the current source equation `Zt = (V²/(kVA·1000))·(%Z/100)·1000` at 400 V, the seed rows produce the following independent values. These are not the values stored in the seed for R/X/Isc:

| Seed | Computed `Zt` mΩ | Computed `Isc3` kA | Stored R/X/Isc3 |
|---|---:|---:|---|
| TX-100 | 64.0 | 3.789 | 1.0 / 3.0 / 3.5 |
| TX-250 | 25.6 | 9.472 | 0.5 / 2.0 / 9.0 |
| TX-500 | 14.4 | 16.839 | 0.25 / 1.0 / 16.0 |
| TX-1000 | 8.0 | 30.311 | 0.1 / 0.5 / 28.0 |
| TX-1600 | 6.0 | 40.415 | 0.05 / 0.2 / 38.0 |
| TX-2000 | 4.8 | 50.518 | 0.04 / 0.15 / 48.0 |

The stored values may represent a different source basis, rounding convention, or hand-entered catalogue values, but no field-level provenance or reconciliation rule is present. The app must either generate these fields from one authoritative set of inputs or mark each imported value with its source and validation status.

### 5.3 Cable matrix reconciliation

The current `cable_catalog.json` has complete positive OD/weight fields after the current checkout’s normalization, but the ampacity fields are not a single normalized matrix: the measured air-to-ground ratio spans 0.686 to 1.486. That is not automatically wrong because installation methods and construction families can differ, but it prevents a reviewer from treating `air`, `gnd`, and `duct` as interchangeable columns. Each row needs an installation-method definition and source reference; otherwise a manufacturer/type selection can mix incomparable ratings.

### 5.4 Derating matrix defect

The air table is:

| Ambient | PVC | XLPE |
|---:|---:|---:|
| 45 °C | 0.95 | 0.90 |
| 50 °C | 0.82 | 0.89 |
| 55 °C | 0.71 | 0.84 |

The XLPE sequence is monotone downward, but its relative relationship with PVC reverses at 50 °C. This may be a data transcription error, an insulation/maximum-conductor-temperature mismatch, or a table-definition error. It must be resolved against the adopted reference table before the asset is used for design decisions.

## 6. Security, Kotlin/Android, and release review summary

### Confirmed strengths

- No hardcoded API credential pattern was found in tracked source; the cloud feature gate defaults closed.
- The manifest has no runtime permission declarations and only the launcher activity is externally exported; the FileProvider is `exported=false`.
- Room migrations are explicitly registered through version 26, even though schema export is disabled.
- Cloud tool names are scoped and unknown tool calls are rejected in the cloud conversation engine.

### Release blockers and hardening items

1. Require a real production upload keystore. The release signing configuration explicitly falls back to a debug keystore when the configured key is absent or invalid: `app/build.gradle.kts:39-87`.
2. Replace the editable trusted-engineer preference with an authenticated/attested identity or disable approval semantics on untrusted devices.
3. Export Room schemas and run migration tests in the default CI profile; update or remove the two stale version-24 assertions.
4. Narrow FileProvider roots to dedicated export directories.
5. Exclude project data and preferences from backup unless the product has an explicit encrypted backup design.
6. Remove package-wide R8 keep rules and debug logging/stack traces from release paths.
7. Keep the cloud AI path disabled until the provider adapter is real and its output is clearly backed by deterministic tool results.

## 7. Recommended remediation order

### P0 — before engineering release

- Fix `E-20` busbar derating acceptance.
- Fix `E-22` earthing fault basis and rename/rebuild the ADS loop model.
- Fix `E-25` typed result flow and remove string parsing from safety calculations.
- Correct and independently approve the XLPE derating table and add a complete policy for circuit groups above six (`E-01`, `E-02`).
- Add busway peak-withstand verification (`E-21`).
- Prevent release signing from falling back to a debug keystore.

### P1 — before professional beta

- Fix conduit type filtering, manual overrides, and procurement roll-up (`E-23`, `E-24`).
- Reconcile transformer seed values or attach authoritative provenance (`E-08`).
- Replace hardcoded panel voltage/power factor and consolidate duplicated quick/full-tool constants (`E-06`, `E-07`).
- Replace editable approval identity and complete backup/FileProvider/R8 hardening (`V-02`, `V-03`, `V-09`, `V-10`).
- Export Room schemas and make the complete test suite part of CI (`V-04`, `V-06`).

### P2 — quality and transparency

- Fix UPS battery topology/EOD-voltage modelling (`E-26`).
- Replace the generator’s unsupported “>15% voltage dip” warning with a calculated dip or an explicit “requires manufacturer data” review state.
- Correct quick-tool formula text (`QuickNewToolsModels.kt:400, :418-419`) so the displayed equation includes the `/1.50` step-load factor used by the code.
- Declare or implement leading/lagging power-factor handling in the converter layer (`E-28`).
- Add row-level data provenance, engineering units, adopted standard/table revision, and validation status to every catalogue/derating/transformer matrix.

## 8. Minimum regression suite required to close the report

The following tests should be added or made mandatory in CI:

1. Busbar: `required=600 A`, IP54, ambient 35 °C, copper, no fault — must fail or select a profile whose **derated** ampacity is at least 600 A.
2. Busbar: ambient at/above permissible temperature — must return an explicit invalid/review state, not a clamped 0.1 factor and a normal pass.
3. Cable: 7, 12, and 20 grouped circuits — must not reuse the six-circuit factor without a declared engineering rule.
4. Cable: XLPE/PVC at 45/50/55 °C — fixture must match the approved reference table and be monotone where the table requires it.
5. Earthing: provide different three-phase Isc and Ik1 values — `Zs`, CPC fault basis, and messages must use the declared basis consistently.
6. Short circuit: pass a typed busway result — no string-to-area parse is permitted; peak and RMS withstand must both be evaluated.
7. Conduit: request EMT and Rigid PVC separately — recommendations and procurement roll-ups must preserve the selected type and catalog ID.
8. Conduit: invalid manual trade size — must return review/invalid, never the last catalog item.
9. Transformer: change `%Z`, kVA, and X/R — generated R/X/Isc must reconcile within the stated rounding tolerance.
10. UPS: vary EOD voltage, Ah rating, series count, and DoD — battery energy and string count must respond to each input.
11. Release: fail the build if the selected signing key is the Android debug keystore.
12. CI: run the AI profile and migration tests by default, not only through an optional profile.

## 9. Final professional limitation

This is a source-code, data, and formula review. It is not a stamped electrical design, installation inspection, protection-coordination study, arc-flash study, or certification to IEC/BS/NEC. Final project values still require a qualified engineer to confirm the adopted standard edition, installation method, manufacturer data, fault study, protective-device settings, earthing measurements, and local authority requirements.

**Current disposition: NO-GO for engineering sign-off; remediation and a clean, full CI test run are required.**
