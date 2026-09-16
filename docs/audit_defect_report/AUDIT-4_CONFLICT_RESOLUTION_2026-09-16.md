# VOLTATLAS — AUDIT-4: CONFLICT RESOLUTION

## 0. Baseline

- HEAD: 9929d35ef5ec85938e4dca7d4d1e28453d44ac18
- Branch: main
- Audit date: 2026-09-16
- Environment: Windows PowerShell, E:\voltatlas-v.1 (4)
- Scope: current checkout only. No source remediation was performed as part of AUDIT-4.
- The working tree already contained circuit-editor UI/test changes and prior audit-document changes. They were preserved and are not included in the AUDIT-4 commit.
- Gradle status: all required invocations were attempted, but each failed before task execution on Gradle's loopback connection. Tests, lint, and dependency resolution are NOT CERTIFIED.

## 1. Adjudication table

Each row contains a binding verdict, current file/line evidence, a literal source line, and the corrected engineering statement. The verdict label identifies the controlling account where a row contains multiple independent effects.

| ID | Subject | Verdict | file:line | Literal code line | Corrected engineering statement |
|---|---|---|---|---|---|
| C-1 | ADS earth-source impedance Ze | BOTH-WRONG | ProtectionEngine.kt:153; EngineeringCalculationCoordinator.kt:1176-1178 | ze_ohm: Double = 0.01 // Assume a small source impedance if not provided | 0.01 ohm is a real public fallback, while the bulk project path derives Ze from a source transformer when available. The displayed Zs still omits separately modelled Tx-to-MDB-to-DB distribution impedance. |
| C-2 | ADS Zs and parallel runs | CONFIRMED-A | ProtectionEngine.kt:176 | val calculatedZs = ze_ohm + ((r1 + r2) * l_m * tempFactor) / 1000.0 | Phase resistance is not divided by circuit.parallelRuns. Multi-run feeders can therefore receive overstated Zs and a false ADS failure. CPC parallelism is not separately modelled. |
| C-3 | Voltage-drop temperature magnitude | CONFIRMED-B | CableSizingEngine.kt:611-613 | val thermalFactor = 1.0 | The main path uses R20 for PVC and the estimated 70-degree column for XLPE. PVC is about 20% low versus 70 degrees; XLPE is about 6-7% low versus true 90-degree resistance. The displayed R70/R90 provenance aliases are wrong. |
| C-4 | Tabulated mV/A/m path | NOT-FOUND | ElectricalConverters.kt:344-347 | val mVperAm = factor * (rOhmPerKm * clampedCos + xOhmPerKm * sinPhi) | No current numeric tabulated mV/A/m consumer was found. The current converter derives mV/A/m from R/X; a future genuine table path must not receive a second thermal correction. |
| C-5 | Defective XLPE air-derating row | CONFIRMED-A | derating_data.json:34-44; CableSizingEngine.kt:170 | "45": { "PVC": 0.95, "XLPE": 0.9 } | The 45-degree row is inverted. The live 50-degree and 55-degree rows have XLPE above PVC and are correctly ordered. The first-key-at-or-above-temperature bucket broadens the 45-degree defect through the 40-45-degree interval. |
| C-6 | Demand-factor condition | CONFIRMED-A | DiversityCalculationEngine.kt:185; :274 | val df = if (c.demandFactor > 0.0 && c.demandFactor < 1.0) c.demandFactor else 0.90 | A user value of exactly 1.0 is rejected. Lighting falls to 0.90; general loads fall to Table 10 RDF, including 0.60 at 10 or more circuits. Values above 1.0 also fall through. |
| C-7 | UPS unit versus battery sizing | CONFIRMED-A, battery-scoped | UpsEngine.kt:74-84; :120-121 | val batteryAh = if (settings.systemVoltage > 0) { correctedBatteryCapacity * 1000.0 / settings.systemVoltage } else 0.0 | The full UPS engine omits DoD from battery Ah, while the UPS unit separately applies design margin and then an 80%-loading divisor. EOD voltage is unused and strings are based on fixed 100 Ah blocks. These are different quantities, so both directions can be true. |
| C-8 | ATS load assignment | CONFIRMED-A | AtsEngine.kt:56-58 | val selectedLoad = if (settings.loadCoverage == "Emergency Only") essentialLoadKVA else totalConnectedLoad | Assigned circuits are filtered only for configuration presence; sizing then uses project-global load. Multiple ATS instances can all be sized to the whole building. A single-ATS project is numerically unchanged when its assigned load equals the project total. |
| C-9 | Phase-unbalance definition | CONFIRMED-A | OtherEngines.kt:489-503 | val unbalance = if (avgPhase > 0.0) ((maxPhase - minPhase) / avgPhase) * 100.0 else 0.0 | This is max-minus-min spread divided by average, not NEMA maximum absolute deviation divided by average. The current UI/SLD wording is generic; no NEMA claim was found. The result must not be presented as a NEMA compliance value. |
| C-10 | Neutral current I_N | CONFIRMED-A | OtherEngines.kt:399-405; DiversityCalculationEngine.kt:39-49 | val phaseLoads: List<PanelPhaseLoad>, | Neither engine computes or surfaces neutral current. The fundamental-frequency vector expression would still exclude triplen-harmonic neutral loading. |
| C-11 | Coordination selectivity claim | CONFIRMED-A | CoordinationEngine.kt:36-45; ProtectionEngine.kt:275-276 | ratio >= MAGNETIC_RATIO_MIN -> "OK" | Current-ratio screening is not proof of IEC selectivity. The current OK result is converted to protection PASS without selectivity-limit-current or I-squared-t evidence. This is a label/evidence defect, not an arithmetic error in the preliminary ratio screen. |
| C-12 | Synchronous calculation cascade | CONFIRMED-A, core-cascade scoped | EngineeringViewModel.kt:501-510 | val uiState: StateFlow<UiState> = combine( | Any emission from the nine combine inputs or the explicit recalculation token reruns the complete calculation block. Search and filter flows exist but are not inputs, so search keystrokes alone do not rerun engineering calculations. |
| C-13 | Cable-catalog completeness | CONFIRMED-A | Models.kt:311-313; cable_catalog.json:1 | od = obj.optDouble("od", 0.0), | A fresh parse of the current asset gives 1,177 rows and 120 rows with missing/zero od or wt, all IEC-Generic. AUDIT-3's zero-row result is not reproducible at this checkout. |
| C-14 | Kotlin file count | CONFIRMED-A, app/src denominator | app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt:1 | package com.example | The broad count is 533 .kt files under app/src: 306 main, 225 test, and 2 androidTest. The 531 figure is correct only for main plus test. |

## 2. Detail per contested row

### C-1 — Ze fallback and transformer-derived source term

The derivation is present:

~~~kotlin
// app/src/main/java/com/example/engine/ProtectionEngine.kt:109-112
fun deriveZeFromSource(isc3Phase_kA: Double, un_V: Double, cmax: Double = 1.05, z0z1Ratio: Double = 0.9): Double {
    if (isc3Phase_kA <= 0.0 || un_V <= 0.0) return 0.0
    val z1 = (cmax * un_V) / (sqrt(3.0) * isc3Phase_kA * 1000.0)
    return z1 * (2.0 + z0z1Ratio) / 3.0
}
~~~

The project path uses the zero result as a signal, then selects the fallback:

~~~kotlin
// app/src/main/java/com/example/engine/coordinator/EngineeringCalculationCoordinator.kt:1174-1178
val sourceTx = resolveSourceTransformer(circuit, resolvedCircuits, transformerResults)
val derivedZe = if (sourceTx != null)
    ProtectionEngine.deriveZeFromSource(sourceTx.isc3Phase, sourceTx.voltage)
else 0.0
val effZe = if (derivedZe > 0.0) derivedZe else ze_ohm
~~~

Relevant production defaults and pass-through sites are ProtectionEngine.kt:153; ProtectionCalculationService.kt:32, 34-37; EngineeringCalculationCoordinator.kt:1053, 1071; :1164, 1176-1188; :1289, 1292; and :1702, 1731. The AI freshness adapter also publishes a fixed 0.01-ohm quantity at ProtectionAdsFreshnessAdapter.kt:44. The literal forwarding lines are:

~~~kotlin
// ProtectionCalculationService.kt:37
ze_ohm = ze_ohm
// EngineeringCalculationCoordinator.kt:1071
val fresh = ProtectionCalculationService.calculateAds(circuit, sizingResult, ze_ohm)
// EngineeringCalculationCoordinator.kt:1188
var fresh = ProtectionCalculationService.calculateAds(circuit, sizing, effZe)
// EngineeringCalculationCoordinator.kt:1292
calculateProjectAds(resolvedCircuits, sizingResults, ze_ohm, transformerResults)
// EngineeringCalculationCoordinator.kt:1731
val freshAds = ProtectionCalculationService.calculateAds(circuit, sizingResult, ze_ohm)
// ProtectionAdsFreshnessAdapter.kt:44
.putQuantity("ze_ohm", 0.01, "ohm")
~~~

Verdict: the formula is not the defect. The external account is correct that a hardcoded fallback exists and that the bulk source model stops at the transformer-derived term. The prior “exemplary” account is wrong because it treats the fallback as absent. This is a wrong fallback/model-scope statement, not wrong sequence-network arithmetic.

### C-2 — parallel runs

The exact ADS expression has no parallel-run divisor:

~~~kotlin
// app/src/main/java/com/example/engine/ProtectionEngine.kt:174-176
val tempFactor = 1.20
val l_m = circuit.length_m
val calculatedZs = ze_ohm + ((r1 + r2) * l_m * tempFactor) / 1000.0
~~~

CircuitEntity exposes parallelRuns at Entities.kt:28, but ProtectionEngine does not use it. The sizing result exposes earthCPC_mm2 as a display string at CableSizingEngine.kt:39; no CPC parallel-run or arrangement field was found. Confirm the phase-run arithmetic defect, but do not divide r2 without a defined CPC topology.

### C-3 and C-4 — resistance temperature and mV/A/m provenance

The current cable path chooses:

~~~kotlin
// app/src/main/java/com/example/engine/CableSizingEngine.kt:592-598
val resistance = when (rColumn) {
    "R_Cu20", "R_Cu70" -> sheet.rCu20
    "R_Al20", "R_Al70" -> sheet.rAl20
    "R_Cu_70_est", "R_Cu90" -> sheet.rCu_70_est
    "R_Al_70_est", "R_Al90" -> sheet.rAl_70_est
    else -> return null
}
~~~

It then sets thermalFactor to 1.0 at CableSizingEngine.kt:611-613. Models.kt:29-32 aliases R_Cu70 to R_Cu20 and R_Cu90 to R_Cu_70_est, while QuickEngineeringDataBridge.kt:67-72 displays those aliases as 70 and 90 degrees. Therefore Account A's “all R20” statement is wrong, but the provenance label remains wrong.

The converter is derived:

~~~kotlin
// app/src/main/java/com/example/engine/converters/ElectricalConverters.kt:344-347
val mVperAm = factor * (rOhmPerKm * clampedCos + xOhmPerKm * sinPhi)
val vDrop = (mVperAm * amps * lengthM) / 1000.0
val vDropPct = if (voltageV > 0) (vDrop / voltageV) * 100.0 else 0.0
return VdropResult(mVperAm, vDrop, vDropPct)
~~~

No numeric tabulated mV/A/m asset consumer was found. PVC needs a 70-degree correction relative to R20. XLPE currently uses a 1.20-times R20 estimated column, so it is approximately 6-7% below a 90-degree value, not 28% below R20.

### C-5 — derating row

The live values are:

~~~json
// app/src/main/assets/derating_data.json:34-44
"45": { "PVC": 0.95, "XLPE": 0.9 },
"50": { "PVC": 0.82, "XLPE": 0.89 },
"55": { "PVC": 0.71, "XLPE": 0.84 }
~~~

CableSizingEngine.kt:170 uses:

~~~kotlin
val bucket = keys.firstOrNull { it >= temp } ?: keys.last()
~~~

Only 45 degrees is inverted. The selection rule applies it to temperatures greater than 40 and up to 45 degrees; it does not invert the 50 or 55 rows.

### C-6 — demand factor

Lighting uses the strict condition at DiversityCalculationEngine.kt:185:

~~~kotlin
val df = if (c.demandFactor > 0.0 && c.demandFactor < 1.0) c.demandFactor else 0.90
~~~

General loads repeat it at line 274 but use table10Rdf as fallback. The table resolver returns 1.0 for one circuit, 0.9 for two to three, 0.8 for four to five, 0.7 for six to nine, and 0.6 for ten or more at lines 65-81. A supplied 1.0 or greater than 1.0 is therefore not preserved. Approved category constants are excluded from this conflict.

### C-7 — UPS quantities point in different directions

UPS unit selection applies both margin and loading headroom:

~~~kotlin
// app/src/main/java/com/example/engine/UpsEngine.kt:74, 88-90
val designUpsKVA = upsApparentPowerReq * (1 + settings.designMarginPct / 100.0)
val d = designUpsKVA / 0.8
var recommendedSize = standardUpsSizes.firstOrNull { it >= d }
~~~

The display loading is based on raw critical load at lines 104-110. The battery calculation applies efficiency, aging, and temperature but no DoD:

~~~kotlin
// app/src/main/java/com/example/engine/UpsEngine.kt:76-84
val batteryLoadKW = if (settings.upsEfficiency > 0) {
    (designUpsKVA * settings.upsPf) / (settings.upsEfficiency / 100.0)
} else 0.0
val batteryEnergyReq = batteryLoadKW * (settings.autonomyMinutes / 60.0)
val correctedBatteryCapacity = if (settings.tempDeratingFactor > 0) {
    batteryEnergyReq * settings.agingFactor / settings.tempDeratingFactor
} else 0.0
val batteryAh = if (settings.systemVoltage > 0) {
    correctedBatteryCapacity * 1000.0 / settings.systemVoltage
} else 0.0
~~~

Entities.kt:247 exposes batteryEodVoltage, but UpsEngine uses systemVoltage. UpsEngine.kt:120-121 divides by fixed 100 Ah. The quick tool includes DoD and applies inverter efficiency once at QuickNewToolsModels.kt:470-480; adding another efficiency factor would double-count. The binding resolution is: full-engine UPS unit sizing can be high while full-engine battery Ah is low.

### C-8 — ATS global-load assignment

The filter exists:

~~~kotlin
// app/src/main/java/com/example/engine/AtsEngine.kt:45-48
val atsCircuits = circuits.filter { circ ->
    circ.assignedAtsId != null && circ.assignedAtsId == atsId
}
~~~

After only checking whether that list is empty, the engine uses:

~~~kotlin
// app/src/main/java/com/example/engine/AtsEngine.kt:56-58
val selectedLoad = if (settings.loadCoverage == "Emergency Only") essentialLoadKVA else totalConnectedLoad
~~~

The ViewModel separately computes the maximum short-circuit result among ATS-assigned circuits at EngineeringViewModel.kt:630-644. That supports the limited current-level observation in the older report but does not fix load assignment. Multi-ATS sizing is confirmed wrong; single-ATS numerical impact is normally zero.

### C-9 — phase-unbalance metric

SldModels.kt:277-278 calls it phase unbalance without a NEMA citation. The engine uses max-minus-min spread. For phase values 100, 100, and 80, the current result is 22.22%, while max absolute deviation divided by average is 11.11%. The arithmetic is therefore not the NEMA max-deviation definition. This is a wrong metric if max-deviation was intended, and a provenance/label defect if the result is intended only as a board spread metric.

### C-10 — neutral current absence

PanelScheduleResult contains only panel name, connected kVA, active demand, phase loads, and unbalance at OtherEngines.kt:399-405. DiversityCalculationResult has boardDesignCurrentA but no neutral-current field at DiversityCalculationEngine.kt:39-49. The requested fundamental-frequency equation is not present. Even if added, triplen harmonics would require an independent harmonic neutral model.

### C-11 — ratio screen emitted as PASS

CoordinationEngine.kt:16-20 declares 1.6 thermal and 2.0 magnetic screening constants. Lines 36-45 select status from upstreamRating/downstreamRating only. ProtectionEngine.kt:275-276 maps any status containing OK to PASS. Manufacturer selectivity-limit current, let-through energy, and I-squared-t evidence are absent. The ratio screen is not itself invalid arithmetic; the compliance-looking status is unsupported.

### C-12 — invalidation granularity

The nine combine inputs at EngineeringViewModel.kt:501-510 are projectCircuitsFlow, projectTransformersFlow, recalculationToken, powerSettingsFlow, conduitSettingsFlow, generatorsList, atsList, upsList, and catalogReady. The lambda invokes the calculation pipeline from lines 537-702, and flowOn(Dispatchers.Default) at lines 781-785 only changes execution context.

Any emission from those nine inputs reruns the complete block. Search, status, and parent filters are declared at EngineeringViewModel.kt:58-60 but are not combine inputs; the external account is correct about the core cascade and incorrect about a search keystroke triggering it.

### C-13 — raw asset is not zero-defect at this checkout

Models.kt:304-313 parses optional od and wt fields with a zero default:

~~~kotlin
od = obj.optDouble("od", 0.0),
wt = obj.optDouble("wt", 0.0),
~~~

Independent current-asset parsing returned rows=1177, bad=120, bad_mfg=IEC-Generic. The first bad records are IEC-Generic type 1 sizes 1.5, 2.5, and 4 with blank od and wt. Therefore the AUDIT-3 statement of zero such rows is not reproducible at current HEAD. E-19 is referenced by ID only and not re-derived here.

### C-14 — precise Kotlin denominator

The current checked-in .kt count under app/src is:

| Root | Count |
|---|---:|
| app/src/main | 306 |
| app/src/test | 225 |
| app/src/androidTest | 2 |
| All app/src Kotlin files | 533 |

The 533 count includes the two instrumented-test files; 531 is main plus test only. No generated/build source root was included.

## 3. Revised P0 list

Safety-first sequencing, referencing existing findings by ID without restating their registers:

1. E-20 — busbar continuous derating can be bypassed by nominal rating.
2. E-22 — earthing loop/fault-current basis is incomplete.
3. E-25 — typed electrical data is converted to display strings and reparsed.
4. C-2 — ADS Zs omits phase parallel-run division.
5. C-1 — ADS source impedance can fall back to 0.01 ohm and omits intermediate distribution terms.
6. E-02 / C-5 — 45-degree XLPE air derating row is inverted and bucket-selected above 40 degrees.
7. E-01 — grouping factors freeze above six circuits.
8. E-03 / C-3 — voltage-drop temperature and resistance provenance are inconsistent.
9. C-8 — multi-ATS instances use project-global load.
10. C-11 — ratio-only coordination is emitted as PASS without selectivity evidence.
11. E-21 — busway peak withstand is not established.
12. E-09 / E-23 — conduit type/procurement assumptions remain unresolved.

This ordering does not close findings. The release disposition remains NO-GO until engineering evidence and executable verification are available.

## 4. Test execution status

Required commands attempted from the repository root:

| Command | Result |
|---|---|
| ./gradlew.bat :app:testDebugUnitTest -Pprofile=fast --no-daemon --stacktrace | NOT CERTIFIED; failed before task execution |
| ./gradlew.bat :app:testDebugUnitTest -Pprofile=ai --no-daemon --stacktrace | NOT CERTIFIED; failed before task execution |
| ./gradlew.bat :app:testDebugUnitTest -Pprofile=full --no-daemon --stacktrace | NOT CERTIFIED; failed before task execution |
| ./gradlew.bat :app:lintRelease --no-daemon --stacktrace | NOT CERTIFIED; failed before task execution |
| ./gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --no-daemon | NOT CERTIFIED; failed before task execution |

Verbatim representative failure:

~~~text
FAILURE: Build failed with an exception.

* What went wrong:
java.io.IOException: Unable to establish loopback connection

Caused by: java.net.SocketException: Invalid argument: connect
~~~

No test, lint, or dependency task reached execution. Static inspection is not an executed green build.

## 5. What this document does NOT settle

- It does not certify any calculation, catalog, protection result, or construction deliverable for fitness for use.
- It does not adjudicate findings outside C-1 through C-14.
- It does not validate manufacturer selectivity tables, cable reference conditions, battery discharge curves, harmonic neutral loading, or measured earth-loop data.
- It does not replace a clean CI or developer-machine run of all required Gradle profiles, lint, dependency resolution, and release assembly.
- It does not remediate source findings, alter the UI, or change electrical equations.

## 6. Local commit hash (NO PUSH)

This document is for one local documentation-only commit. No push is authorized or performed.
