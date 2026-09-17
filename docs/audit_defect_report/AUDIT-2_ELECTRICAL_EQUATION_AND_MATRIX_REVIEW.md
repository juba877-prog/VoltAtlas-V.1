# VOLTATLAS V.1 — AUDIT-2: ELECTRICAL EQUATION & MATRIX REVIEW

> **Partially superseded.** See [AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md](AUDIT-5_ERRATA_AND_CONSOLIDATED_P0.md) §3 for corrections to this document. AUDIT-4 adjudicates C-1…C-14; AUDIT-5 is the consolidated index and the single P0 source.

| Field | Value |
|---|---|
| Artifact | `docs/audit_defect_report/AUDIT-2_ELECTRICAL_EQUATION_AND_MATRIX_REVIEW.md` |
| Companion | `AUDIT-1_SECURITY_AND_DEFECT_REPORT.md` (security, privacy, build, release gates) |
| Repository / HEAD | `e:\voltatlas-v.1 (4)` @ `16cf38f` (branch `integration/home-snapshot-2026-09-13`) |
| Scope | Every electrical equation, constraint rule, lookup matrix and standards constant implemented in `com.example.engine.**`, `com.example.ui.screens.quick.**`, `com.example.engine.converters.**`, plus every JSON data matrix in `app/src/main/assets/**` |
| Verification method | (1) code read against the cited clause; (2) **independent re-computation** of the arithmetic and of the published reference cases; (3) cross-check of the JSON matrices element-by-element against the source table; (4) search for divergent duplicate implementations of the same quantity |
| Standards in scope | IEC 60909-0, IEC 60364-4-41/-4-43/-5-52/-5-54, IEC 60228, IEC 60287, IEC 60364 Annex G, IEC 61439-1/-2, IEC 60865-1, IEC 60947-2/-4-1/-6-1, IEC 62040-3, IEC 60076, BS 7430, BS 7671 (App. A/G, Table 41.3), DIN 43671, NEC Chapter 9, ISO 8528, IEEE 485, EN 50588-1 |
| Source modifications | **None.** Findings only. |

---

## 1. How to read the verdicts

| Verdict | Meaning |
|---|---|
| **OK** | Equation implemented correctly, units consistent, and (where a reference existed) independently reproduced. |
| **OK-assumed** | Correct arithmetic, but rests on an input the tool cannot know; the code flags it to the user (acceptable if flagged). |
| **DEFECT** | Wrong in a way that changes a verdict or a purchased quantity. Carries an `E-nn` id from §2/§3 below. |
| **DIVERGENT** | Two or more implementations of the same physical quantity coexist with different answers (`E-06`). |

**Unit convention used throughout the code (verified consistent):** lengths m, conductor cross-sections mm², R/X in Ω/km for cables and mΩ/m for busway, transformer Z in mΩ, fault currents in kA in the domain `*Result` types, voltages in V. The mΩ↔Ω and Ω/km↔Ω/m conversions were individually checked (see §2.11) because they are the usual home of 1000× errors.

## 2. Equation register

Format: **equation as implemented → code location → cited clause → independent check → verdict.**
`OK` = reproduced independently and correct. `OK-assumed` = correct arithmetic on an unknowable input, flagged to the user. `DEFECT`/`DIVERGENT` = see the referenced finding.

### 2.1 Reference-case reproduction (the single most valuable sanity test in the repo)

`app/src/main/assets/qa_reference_cases.json` publishes expected engine outputs. I recomputed them by hand from the formulas to confirm the file is a genuine regression oracle and not self-fulfilling:

| Reference | Inputs | Expected (file) | Hand re-derivation | Verdict |
|---|---|---|---|---|
| `TC-TX-01` | 200 kVA manual, %Z 4, X/R 10, Dyn11, Z0/Z1 0.9, 400 V, pf 0.8 | zt 32 mΩ, rt 3.18, xt 31.84, Isc3 7.58 kA, Isc1 7.84 kA, z0 28.8 mΩ, FLC 288.68 A | zt = (400²/200 000)·0.04·1000 = **32** ✓; rt = 32/√101 = **3.184** ✓; xt = 3.184·10 = **31.84** ✓; Isc3 = 1.05·400/(√3·32) = **7.578** ✓; Z1 = 0.032 Ω, Z0 = 0.0288 → isc1 = √3·1.05·400/(2·0.032+0.0288)/1000 = **7.839** ✓; FLC = 200 000/(√3·400) = **288.68** ✓ | **OK — exact parity** |
| `TC-TX-02` | 400 kVA, %Z 6, X/R 8, Z0/Z1 0.95 | zt 30.5, rt 3.78, xt 30.24, Isc3 7.96, z0 29.0 | zt = (160 000/400 000)·0.06·1000 = 24.0 mΩ **≠ 30.5** ⚠ — see note below | **Flagged** |
| `TC-CABLE-008` | 3P+N, 80 m, ducts, 40 °C air, Cu/XLPE/STA/PVC 4C | Ib 144.34 A, cTotalDerating 0.95, Iz 192.8 A, In 160 A, 70 mm², VD 6.076 V, 2.8278 %, CPC 35 mm² | Ib = kVA·1000/(√3·400) consistent; CPC 35 mm² for a 70 mm² phase matches Table 54.7 (S>35 → S/2 = 35) ✓ | **OK for the rules I can re-derive from the file alone** |

> **Note on `TC-TX-02`:** the published `zt` of 30.5 mΩ corresponds to a *selected size* of 315 kVA, not the 400 kVA manual input — i.e. the case is genuinely exercising the auto-size path (`selectedSize = 315`, `requiredKVA = 240`, `recommendedSize = 315`). The engine's `zt` is computed from `selectedSize` (`TransformerEngine.kt:180`), so this is **consistent, not an error** — but it is a trap for a reader: the reference file mixes "input kVA" and "selected kVA" in the same block. Recorded as a documentation-clarity defect, not a calculation defect.

**Verdict: the QA oracle is real.** Transformer and cable expectations reproduce from first principles, which materially raises confidence in the rest of the engine family.

### 2.2 Cable sizing — `engine/CableSizingEngine.kt`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| C-1 | `Ib = kVA·1000/(√3·V)` (3P) / `kVA·1000/V` (1P) | `:413-418` | IEC 60364-5-52 §523 | dimensionally exact for a balanced load | **OK** |
| C-2 | Mandatory-input guards (V>0, kVA≥0, runs≥1, 0<pf≤1, maxVD>0, L>0) with typed `INCOMPLETE_INPUT` | `:393-411` | — | rejects rather than defaults; the desirable failure direction | **OK** |
| C-3 | `cTotal = Ct·Cd·Cs·Cg`; `I_required = Ib/(runs·cTotal)` | `:453-488` | IEC 60364-5-52 Annex B / IEC 60287 | multiplication order irrelevant; the divisor form is correct | **OK** |
| C-4 | Temperature factor bucket = *first key ≥ T* | `:170` | conservative rounding | e.g. T = 42 °C → 45 °C row | **OK** |
| C-5 | **Temperature factors, XLPE @45 °C air** | `derating_data.json` | IEC 60364-5-52 Table B.52.14 | XLPE 0.90 < PVC 0.95 — physically inverted and out of family with this file's own ground table | **DEFECT E-02** |
| C-6 | Depth factor bucket = *first key ≥ depth* | `:194` | conservative | depth 0.7 m → 0.8 m row (0.97) | **OK** |
| C-7 | Depth factor for depth > 2.0 m → `keys.last()` = 0.91 | `:194` | **non-conservative** | deeper burial cools worse; the table simply ends at 2 m | **DEFECT E-11(b)** |
| C-8 | Soil factor bucket = *first key ≥ ρ*, with `ρ ≤ 10 → ρ·100` legacy re-scaling | `:207` | conservative | ρ = 130 → 150 → 0.91; ρ = 3 → 300 → 0.67 | **OK (with a legacy quirk)** |
| C-9 | **Grouping index** `noCircuits.coerceIn(1,6) - 1` into a 6-element array | `:261` | IEC 60364-5-52 Table B.52.17 | For 9–20 circuits the factor is *optimistically* frozen at the 6-circuit value → **Iz overstated up to ~18 %**, silently | **DEFECT E-01** |
| C-10 | Third-harmonic phase factor 0.86 for 15–33 % content | `:485` | IEC 60364-5-52 Annex E | 0.86 is the correct value for the 15–33 % band | **OK-assumed** (the entity is a Boolean, so the band cannot be refined; the code says so) |
| C-11 | Ampacity acceptance `Ib ≤ In ≤ Iz_derated` | `:631-633` | IEC 60364-4-43 §433 | correct two-sided constraint | **OK** |
| C-12 | `ΔU₃φ = √3·Ib·(R·cosφ + X·sinφ)·L/(1000·N)`; `ΔU₁φ = 2·Ib·(…)/1000·N` | `:615-624` | IEC 60364-5-52 Annex G | algebra and the /1000 (m→km) exact; 3P/1P factors correct | **OK** |
| C-13 | **Resistance selected at 20 °C (PVC) / 70 °C (XLPE)** with `thermalFactor = 1.0` | `:585-613` | Annex G requires the max operating temperature (70/90 °C) | ΔU **understated ≈20 % for PVC, ≈6 % for XLPE**; deliberate ("workbook/QA parity") but on the unsafe side | **DEFECT E-03** |
| C-14 | `cumulativeVD = parentCumulativeVD + vdPct`; limit test `≤ maxVDpct·100` | `:628-635` | Annex G / BS 7671 App. 4 | cumulative chain correct | **OK** |
| C-15 | Candidate selection = smallest size satisfying BOTH constraints, else last (largest) checked with a reason | `:685-700` | — | correct, and it does not silently pass a failed candidate | **OK** |
| C-16 | Neutral sizing table (`getHalfSize`) | `:84-102` | reduced-neutral practice | mapping is monotone and ≤ phase size in every branch | **OK** |
| C-17 | CPC by Table 54.7 (S≤16→S; 25/35→16; >35→S/2 up, min 16) | `:113-131` | IEC 60364-5-54 Table 54.7 | correct, and it correctly refuses to confuse this with the neutral | **OK** |
| C-18 | OD/weight fallback chain (catalog → Elsewedy → `3.0·√S + 9` estimate) with `isODApproximated` + source manufacturer | `:906-961` | — | estimation is flagged in the result; never silent | **OK-assumed** |

### 2.3 Short-circuit — `engine/ShortCircuitEngine.kt` (IEC 60909-0)

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| S-1 | Impedance accumulation `R = Σ(R_seg·L/(1000·N))`, `X = Σ(…)`, `Z = √(R²+X²)` | `:129-143` | §5 series combination | per-segment Ω/km → Ω conversion correct; parallel-run divisor correct | **OK** |
| S-2 | `I″k = c·Un/(√3·Z)` with `c = 1.05` | `:80,143-144` | Table 1 (LV, c_max = 1.05) | reproduced against `TC-TX-01` (7.58 kA) | **OK** |
| S-3 | `κ = 1.02 + 0.98·e^(−3R/X)`; `ip = κ·√2·I″k` | `:151-152,171` | eq. (54)/(55) | both expressions are the standard LV forms; κ→1.0 guard at X=0 is sensible | **OK** |
| S-4 | Cable thermal withstand check `Iw = k·S/√t` vs `I″k` | `:173-180` | IEC 60364-4-43 §434 / IEC 60364-5-54 | correct **structure**; `S` is regex-parsed from the display string `cableSize_mm2`, and a busway row yields `"800A Busway (Cu)"` → `S = 800 mm²` | **DEFECT E-10** |
| S-5 | `marginPct = (Iw/I″k − 1)·100` | `:182` | — | correct definition of withstand margin | **OK** |
| S-6 | Device breaking test `Icu ≥ I″k` | `:176` | IEC 60947-2 | correct | **OK** |
| S-7 | Peak making test `Icm ≥ ip`, catalog Icm first, else `n·Icu` with IEC 60947-2 Table 2 tiers (1.41→2.2) | `:191-226`, `:267-275` | §4.3.6.1 Tab. 2 | tiers are correct and the code explains that a blanket 2.2 would be optimistic in the 20–50 kA band | **OK (exemplary)** |
| S-8 | Grid-source impedance `Z_Q = c·Un²/S″kQ` reflected to LV; `X/R = 10` → `R = Z/√101`, `X = 10R` | `TransformerEngine.kt:163-176` | §6.2 | the primary voltage cancels in the reflection — the comment is right and the arithmetic with `c = 1.1` on the grid side is correct; gated on `S_kQ > 0` so the infinite-bus default is preserved and flagged | **OK** |
| S-9 | Generator source `Z_G = (x″d/100)·Un²/S_rG`, `X″d = Z_G/√(1+0.15²)`, `R_G = 0.15·X″d` | `:97-100` | §6.6/6.7 | standard LV machine approximation; `x″d` assumed and **flagged**, `K_G = 1.0` assumed and **flagged** | **OK-assumed** |
| S-10 | Generator terminal voltage hardcoded `GENERATOR_TERMINAL_V = 400.0` | `:279` | §6.7 | For a 380 V or 480 V islanded system the computed fault current is wrong by ∓5 %/∓17 %; unlike `x″d`/`K_G`, **this assumption is not surfaced** in the note | **DEFECT E-13 (minor)** |
| S-11 | LV motor-group contribution `Z_M = (1/(I_LR/I_rM))·(U_rM/(√3·I_rM))`, `I″kM = c·Un/(√3·Z_M)`, summed arithmetically | `:158-168` | §6.10 | formula and units check out (Ω from V/A); arithmetic addition is the §6.10 LV allowance; default **OFF** and the enablement is flagged with the assumed `I_LR/I_rM = 5.5` | **OK-assumed** |
| S-12 | Source-type polymorphism (transformer / grid / ATS-NORMAL vs generator / ATS-EMERGENCY) with an explicit statement that the transformer is *not* in the generator's impedance path | `:56-127` | §6.7 | correct and a real improvement over naive tools that add both | **OK (exemplary)** |
| S-13 | Missing-data handling: `DATA MISSING` + a specific reason for absent transformer, zero Isc, unparseable size, NaN result | `:93-95,106-113,146-149,281-295` | — | refuses to invent a value | **OK** |
| S-14 | Switching-device suppression (`LBS`, `ISOLATOR`, `ATS`, `BUS COUPLER`, `CHANGE-OVER` → `deviceCheck = "N/A"`, cable check still enforced) | `:249-256,200-202` | — | physically sensible (these devices are not rated to break fault current) | **OK** (string-match fragility noted as low risk) |
| S-15 | No IEC 60909 transformer impedance correction `K_T = 0.95·c_max/(1+0.6·x_T)` | `TransformerEngine.kt:186-188` | §6.3.3 | the simplification is common in consulting practice and slightly non-conservative for a single-transformer LV network; must be declared as a method assumption | **OK-assumed (declare it)** |

### 2.4 Transformer — `engine/TransformerEngine.kt`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| T-1 | `Z_t(mΩ) = (Un²/S_r)·(%Z/100)·1000` | `:180` | IEC 60076-5 | 200 kVA/4 % → 32 mΩ ✓; 315 kVA/6 % → 30.5 mΩ ✓ (both reproduce the QA file) | **OK** |
| T-2 | `R_t = Z_t/√(1+(X/R)²)`, `X_t = R_t·(X/R)` | `:183-184` | — | exact inverse of the X/R definition | **OK** |
| T-3 | `I″k,3ph = c·Un/(√3·Z_t)` with c = 1.05 | `:186-188` | Table 1 | 7.58 kA verified | **OK** |
| T-4 | `I″k,1ph = √3·c·Un/(2·Z1+Z0)`, `Z0 = (Z0/Z1)·Z1`, Dyn11 ⇒ 0.9 when the stored value is exactly 1.0 | `:190-197` | §11 (Clause 11) | 7.84 kA / 28.8 mΩ verified; the "only override when the user left 1.0" guard is a thoughtful touch | **OK-assumed** (Z0/Z1 for Dyn11 LV is strongly network-dependent — keep it user-overridable and visible) |
| T-5 | `FLC = S_r·1000/(√3·Un)` | `:161` | — | 288.68 A verified | **OK** |
| T-6 | Load aggregation chain connected → realPower(pf) → demand → diversified → future(growth) → design(safety) → `requiredKVA` → recommended standard size → `loadingPct` | `:98-160` | IEC 60364 / practice | the chain is applied in the right order and each stage is exposed in the result | **OK** |
| T-7 | `STANDARD_TX_SIZES` 50…4000 kVA ladder | `:43-46` | IEC 60076-1 preferred ratings | monotone, standard R10/R20 values | **OK** |
| T-8 | `getDefaultPercentZ` (oil ≤630 → 4 %, ≤1250 → 5 %, else 6 %; dry ≤2500 → 6 %, else 8 %) | `:48-63` | EN 50588-1 / practice | plausible defaults, and it is only a *default* the user can override | **OK-assumed** |
| T-9 | Status verdict: `>100 % → OVERLOADED-FAIL`, `connected > rating → REVIEW`, else `VERIFIED` | `:200-204` | — | distinguishes overload (thermal) from connected-load-exceeds-rating | **OK** |
| T-10 | `transformers_seed.json` R/X/isc columns | matrix asset | — | internally inconsistent by >10× | **DEFECT E-08** |

### 2.5 Protection & automatic disconnection (ADS) — `engine/ProtectionEngine.kt`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| P-1 | Overload: `Ib ≤ In + ε` **and** `In ≤ Iz + ε` | `:252` | IEC 60364-4-43 §433.1 | the two-sided form is the standard requirement; ε guards float noise | **OK** |
| P-2 | Breaking: `Icu ≥ I″k − ε` | `:256` | IEC 60947-2 | correct | **OK** |
| P-3 | Cable thermal: `k²S² ≥ I″k²·t` (computed on both sides in A²s) | `:259-268` | IEC 60364-4-43 §434.5.2 | algebraically equivalent to `S ≥ I·√t/k`; `t` falls back to 0.1 s when unset | **OK structurally**; the `S` source is the string parse of E-10 |
| P-4 | Insulation-aware `k`: Cu/XLPE 143, Cu/PVC 115, Al/XLPE 94, Al/PVC 76 | `:120-125` | IEC 60364-5-54 Table 54.2/54.3 | all four values match the standard | **OK** |
| P-5 | Strict trip-curve parser: exact `B`/`C`/`D`, or explicit `curve/type/char` token; device *families* return `null` | `:52-70` | — | fixes the historic `contains("B")` defect that forced every device onto B-curve limits; the note "MCCB does not contain MCB: M-C-C-B" is correct | **OK (exemplary)** |
| P-6 | `Ia = In × curve multiple` (B 5×, C 10×, D 20×) as the single source of truth | `:46-50,88` | IEC 60364-4-41 §411.4.4 | correct; consulted by both this engine and the earthing engine | **OK** |
| P-7 | `Zs_max = (Cmin·U0)/Ia`, `Cmin = 0.95` | `:93-94` | §411.4.4 / Table 41.3 | 0.95·230/(5·16) = 2.73 Ω ✓ for B16; matches Table 41.3 exactly | **OK** |
| P-8 | `MAX_ZS_TABLE` B/C/D × 6–125 A | `:132-148` | Table 41.3 (TN, 230 V) | spot-checked B16 2.73, C16 1.37, D16 0.68 — all exact | **OK** |
| P-9 | Non-MCB devices are **not** evaluated against the MCB table; an explicit `Ii = 10·In` assumption is applied **and warned** | `:204-214` | — | correct handling; avoids the classic "ACB judged by MCB curve" error | **OK (exemplary)** |
| P-10 | `Ze = Z1·(2+Z0/Z1)/3` from `Z1 = (cmax·Un)/(√3·I″k)` | `:109-113` | §6 / loop-equivalent | 500 kVA/4 % → Z1 = 0.0128 Ω → Ze = 0.0124 Ω; algebra verified; returns 0.0 (forcing a labelled fallback) rather than guessing | **OK (exemplary)** |
| P-11 | ADS loop `Zs = Ze + (R1+R2)·L·1.20/1000` | `:174-176` | Annex G / Table 41.3 | structure correct; the `1.20` factor is the Cu 70 °C multiplier (1 + 0.00393·50 = 1.1965) — correct for PVC, **≈4 % optimistic for XLPE (should be 1.275)** | **DEFECT E-14 (minor)** |
| P-12 | `extractNumericSize` regex with a silent `?: 1.5` fallback | `:319-321` | — | On any unparseable conductor description the engine silently checks the adiabatic condition of a **1.5 mm²** conductor | **DEFECT E-10(b)** |

### 2.6 Earthing & bonding — `engine/EarthingEngine.kt`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| G-1 | Dwight single rod `R₁ = (ρ/2πL)·(ln(8L/d) − 1)`, with mm→m conversion | `:116-118` | BS 7430 §R32 | ρ = 50, L = 3, d = 0.017 → (50/18.85)·(ln(1411.8)−1) = 2.653·(7.2525−1) = **16.60 Ω**; the quick engine's published substitution string shows the same arithmetic | **OK** |
| G-2 | Group efficiency by spacing ratio: `s/L ≥ 2 → 1 − 0.05(n−1) ≥ 0.70`; `≥1.5 → 0.95 − 0.06(n−1) ≥ 0.65`; else `0.90 − 0.07(n−1) ≥ 0.60` | `:136-140` | BS 7430 practice | monotone decreasing in n, floors enforced, spacing-ratio dependent — physically sensible | **OK-assumed** (empirical; document the source of the coefficients) |
| G-3 | Rods combined with the ring: `1/(1/(R₁/(n·η)) + 1/max(R_ring, 1))` | `:142-143` | BS 7430 §R22 | parallel-combination form is correct; `R_ring` from the buried-ring formula at `:126-128` | **OK-assumed** (ring perimeter is hardcoded to 20 m — see E-15) |
| G-4 | `R_ring = (ρ/(2π²·l))·ln(2l²/(h·d_c))`, `l = 20 m` fixed | `:124-128` | BS 7430 | formula shape is the standard buried-ring expression; **the 20 m perimeter is a hardcoded assumption, not derived from the site**, and is not surfaced as an assumption | **DEFECT E-15 (minor)** |
| G-5 | Earth conductor `S = I_corrected·√t / k` with `I_corrected = Ik1` when supplied, else 3-phase Isc | `:163-170` | IEC 60364-5-54 §54.2.2 / §543.1.1 | arithmetic exact; preferring Ik1 is the correct standard basis and the fallback is explicitly warned | **OK (exemplary)** |
| G-6 | Rounding up to the next `STANDARD_EARTH_CONDUCTORS` size | `:172-173` | — | correct direction | **OK** |
| G-7 | `equipotentialBonding = standardConductorMm2` | `:175` | §544.2 caps main bonding at 25 mm² Cu / 50 mm² Al (≥ S/2, min 6/16) | e.g. a 185 mm² electrode conductor yields a 185 mm² bonding conductor → over-specified and not the standard rule | **DEFECT E-05** |
| G-8 | `Zs = U0/I_prospective`, compared with the main device `Zs_max` | `:183,194-197` | §411.4.4 | **omits the PE/CPC R1+R2 and the electrode resistance, and uses the 3-phase Isc rather than Ik1**; the main-breaker `In` is taken from the transformer FLC (`EngineeringViewModel.kt:675`). The number is a source-strength screen, not the installation Zs | **DEFECT E-04 (high)** |
| G-9 | Soil classification bands (≤50 Low, ≤100 Normal, ≤300 Moderate, ≤1000 High, else Very High) | `:250-258` | practice | reasonable engineering bands | **OK** |
| G-10 | Auto-design: iterate rod count/length/diameter/spacing, require `R_achieved ≤ target·(1−margin)`, exclude configurations exceeding the site footprint, rank by total rod length then count then resistance | `:260-409` | practice | the margin direction is correct (`designLimit` = target − margin, i.e. a *stricter* target); site-footprint exclusion is a genuinely useful constraint; "no feasible configuration" is reported rather than silently returning the best compromise | **OK (exemplary)** |
| G-11 | Assumed-soil warning (`Wenner 4-pin test`) and margin clamping to [0,50] % | `:280-289` | — | correct engineering caution | **OK** |

### 2.7 Diversity & load aggregation — `engine/DiversityCalculationEngine.kt`, `engine/OtherEngines.kt`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| D-1 | IEC 61439-1 Table 10 RDF by circuit count: 1→1.0, 2–3→0.9, 4–5→0.8, 6–9→0.7, ≥10→0.6 | `Diversity…:74-82` | Table 10 | matches the published RDF table | **OK** |
| D-2 | Category demand rules: lighting 0.90; sockets first-10 kVA@100 % then 50 %; HVAC cascading 100/80/60 %; lifts 100/80/75/70 %; pumps duty/standby exclusion; fire 0 %; IT/UPS 100 %; spare 0 %; sub-panel 0.90 | `Diversity…:180-277` | IEC 60364-5-52 / BS 7671 App. A / practice | each rule is monotone and documented in the KDoc; the fire-0 %/spare-0 % choices are explicitly stated as *normal-mode* assumptions | **OK-assumed** |
| D-3 | Overall diversity factor `min(1.0, TDL/TCL)`; `I_board = TDL·1000/(√3·V)` | `Diversity…:297-307` | — | correct; the `min(1.0, …)` cap prevents a diversity factor > 1 from mixed rules | **OK** |
| D-4 | `GENERAL` category uses `circuit.demandFactor` when in (0,1), else the Table-10 RDF | `Diversity…:271-277` | — | sensible precedence, no silent overwrite of a user value | **OK** |
| D-5 | `PanelScheduleEngine`: single-phase loads distributed round-robin by index; 3-phase split ÷3 | `OtherEngines:461-483` | — | stable and deterministic | **OK** |
| D-6 | Unbalance `% = (max−min)/avg·100`, default limit 20 % | `OtherEngines:492`, `:440-448` | NEMA MG-1 / IEC 61000-2-13 | the formula matches the IEC-style "max deviation" family; the 20 % *default* is a board-load-balance rule, not a motor voltage-unbalance limit — keep it clearly labelled as such | **OK-assumed (label it)** |
| D-7 | Phase current `I = kVA_phase·1000/230` with **230 V hardcoded**; `activeDemand_kW = totalKVA·demandFactor·0.8` with **0.8 pf hardcoded** | `OtherEngines:485-497` | — | numerically equivalent to √3·400 only on a 400 V system; ignores each circuit's voltage and pf | **DEFECT E-07** |

### 2.8 Busbars & busway — `engine/BusbarSizingEngine.kt`, `engine/BuswayEngine.kt`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| B-1 | Adiabatic short-time withstand `I_cw = k·S/(√t·1000)` (kA) | `Busbar:230-231` | IEC 60865-1 / IEC 61439-1 | algebraically the inverse of `S = I√t/k`; Cu k = 166 A·√s/mm² and Al k = 106 are the correct DIN 43671/IEC values | **OK** |
| B-2 | `I_cw` selection: prefer a standard rating ≥ fault level that is also ≤ the bar's physical capacity, else fall back, else computed | `Busbar:232-241` | IEC 61439-1 | three-tier logic is sound and never exceeds the physical limit silently | **OK** |
| B-3 | Peak `I_pk = I_cw·n` with n from IEC 61439 Table 7 | `:244-245` | Table 7 | correct family of factors (≈1.5–2.2 by Icw) | **OK** |
| B-4 | Ambient + paint/emissivity derating applied multiplicatively to the nominal ampacity | `:227` | DIN 43671 | plausible, conservative direction for hot ambient | **OK-assumed** |
| B-5 | Neutral: harmonics → 2× phase; else ≥16 mm² and ≥ S/2. PE: ≤16→S, ≤35→16, else ≥16 and ≥S/2 | `:249-258` | IEC 61439-1 §8.6.1 / IEC 60364-5-54 | both rules match the standards | **OK** |
| B-6 | Busway: `Ib` from load/voltage, `iReq = Ib·safetyFactor`, smallest catalog rating ≥ iReq, Icw vs network Isc, VD from catalog mΩ/m | `Busway:41-163` | IEC 61439-6 | the VD term uses the catalog R/X per metre with the same ΔU formula — consistent | **OK** |
| B-7 | Busway → `SizingResult` bridge sets `cableSize_mm2 = "800A Busway (Cu)"`, `kFactor = 143`, `cableOD_mm = 100`, `clearingTime_s = 1.0` | `Busway:194-231` | — | the documented convention is explicit and the busway's own Icw check is authoritative — **but** these placeholder values then flow into the string-parsing checks of E-10 | **DEFECT E-10 (source)** |

### 2.9 Rotating machines, transfer & compensation — Generator / ATS / UPS / Motor-starter / PFC

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| M-1 | Generator essential load = Σ assigned kVA; `requiredGen = max(essential, stepLoad)/0.80`; × oversizing; next standard size | `GeneratorEngine:65-124` | ISO 8528-1 | the 0.80 (=80 % continuous loading) and the size ladder are consistent with practice; the ladder includes real genset ratings 625/750/1500/2500 | **OK** |
| M-2 | `motorStartingKVA = (kW/(pf·η))·startMultiplier`; `stepLoad = 0.50·S_run + S_start` | `GeneratorEngine:103-113`; `QuickNewToolsModels:392-398` | ISO 8528-5 | arithmetic correct; the 0.5 pre-load fraction is a fixed assumption that should be an input | **OK-assumed** |
| M-3 | Dip warning at `start/S_gen > 0.30`; step-load warning at `(kW/pf)/S_gen > 0.25` | `GeneratorEngine:154-162` | ISO 8528-5 | the message states ">15 % voltage dip" as if computed, but no dip is computed on this path — wording over-claims (the quick tool *does* compute a dip, M-4) | **Minor E-16 (wording)** |
| M-4 | Quick dip `ΔV% ≈ (S_start/S_gen)·X″d%` | `QuickNewToolsModels:405-406` | ISO 8528-5 | dimensional check passes; standard subtransient approximation; `X″d` is an explicit user input here | **OK-assumed** |
| M-5 | ATS: `I_icw = I″k at the ATS location`; `I_icm = 2.2·I_icw`; `I_gov = max(I_design, I_genFLC)`; next standard rating | `AtsEngine:55-90` | IEC 60947-6-1 | `I_icw` is taken from the circuit-level Isc of ATS-fed circuits (`EngineeringViewModel.kt:631-634`) — the correct location-sensitive choice; the 2.2 convention is documented (see S-7) | **OK** |
| M-6 | UPS `S_app = max(S_load, P_load/pf_UPS)`; `S_design = S_app·(1+margin)`; **then `/0.80` again**; `P_batt = S_design·pf/η`; `E = P·t/60`; `C = E·aging/tempDerating`; `Ah = C·1000/V_dc` | `UpsEngine:66-90` | IEC 62040-3 / IEEE 485 | the battery energy chain is correct; the **double margin** inflates the unit ~25 % and disagrees with the quick UPS tool for identical input | **DEFECT E-06(d)** |
| M-7 | UPS `loadingPct` computed from the **raw** load while sizing used the **design** load | `UpsEngine:105-108` | — | 60 kVA on a 100 kVA unit reports "60 % loaded" although the unit was chosen for ~90 kVA — misleading headroom | **DEFECT E-06(d) part 2** |
| M-8 | `estimatedBatteryStrings = ceil(Ah/100)` | `UpsEngine:121` | IEEE 485 | assumes exactly 100 Ah blocks; should be a parameter | **Minor E-17** |
| M-9 | PFC: `Q_c = P·(tanφ₁ − tanφ₂)`; `steps = ceil(Q_c/step)`; detune uplift ×1.075 on bank **current**; leading-PF / undersized / target-not-met verdicts | `PfCorrectionEngine:86-119` | IEC 60831 | algebra exact; applying the detuning uplift to current (not kVAr) is correct; leading-PF over-compensation detection is a genuinely useful inverter-compat check | **OK (exemplary)** |
| M-10 | Starter `FLC = kW·1000/(√3·V·pf·η)`, `I_start = FLC·multiplier`, contactor/TOR/MPCB/fuse from standard ladders | `QuickNewToolsModels:616-630` | IEC 60947-4-1 | correct structure | **OK** |
| M-11 | **Starting multipliers duplicated and contradictory, in one file**: `MotorStartMethod` 6.0/2.5/1.8/1.2 vs `MotorStarterType` 6.5/2.17/2.5/1.15 | `QuickNewToolsModels:346-351` vs `:571-576` | IEC 60947-4-1 | same quantity, two different number sets in the same source file → different starter/contactor/fuse selections for identical input | **DEFECT E-06(a)** |

### 2.10 Panels, BOQ, drums, procurement, conduit, lighting

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| X-1 | BOQ group by (mfr, type, size, product code); `installed = Σ L·runs`; `weight = installed·wt/1000`; drum packing | `OtherEngines:53-148,225-389` | — | unit handling correct (`wt` g/km → kg via /1000) | **OK** |
| X-2 | `totalOrdered = Σ drum capacity` when drums exist, else `installed`; `waste% = (ordered−installed)/ordered` | `OtherEngines:157-165` | — | correct, and refuses to invent a waste figure without a drum schedule | **OK** |
| X-3 | Gland/lug matching on armour group + OD range + conductor size/material + core count, with a per-row `glandMatchingBasis` | `OtherEngines:900-1009` | IEC 62444 | auditable procurement; `DATA MISSING`/`REVIEW REQUIRED` instead of a guess | **OK (exemplary)** |
| X-4 | Tray width `runs·OD·1.4` → next standard width; `OD = 0 → 10 mm` placeholder | `OtherEngines:990-992` | practice | 1.4 is a stated convention; the placeholder is silent but paired with `DATA MISSING` | **OK-assumed** |
| X-5 | Conduit fill `fill% = Σ(π·d²/4)/A_conduit·100` vs PROJECT_DESIGN 40 % / NEC Ch. 9 53 %-31 %-40 % | `ConduitFillEngine`; `QuickNewToolsModels:171+` | IEC 61386 / NEC Ch. 9 Tab. 1 | area arithmetic and NEC limits correct; CPC arrangements modelled, not assumed | **OK (exemplary)** |
| X-6 | **Procurement rollup hardcodes `"Rigid PVC"`** and groups only by trade size | `ConduitFillEngine:414,429` | — | EMT/GI steel export as PVC; same-size different types merge into one BOQ line | **DEFECT E-09** |
| X-7 | Lighting lumen method `N = ceil(E·A/(Φ·UF·MF))`, `E_actual = N·Φ·UF·MF/A`, power density W/m² | `OtherEngines:1062-1090` | IES/CIBSE | lumen-method form correct | **OK** |

### 2.11 Converters & unit machinery — `engine/converters/ElectricalConverters.kt`, `ui/conversions/**`

| # | Quantity / equation as coded | Location | Clause | Independent check | Verdict |
|---|---|---|---|---|---|
| K-1 | AWG diameter `d = 0.127·92^((36−n)/39)`; inverse `n = 36 − 39·ln(d/0.127)/ln(92)`; `4/0→−3 … 1/0→0` | `:80-114` | ASTM B258 | AWG 10 → d = 2.5880 mm → **5.2613 mm²** ✓ (test asserts 5.261); AWG 4/0 → d = 11.684 mm → **107.22 mm²** ✓ | **OK — verified against the standard** |
| K-2 | **SWG kept separate from AWG**: SWG diameters from the asset table (keyed `swg_table`; SWG 10 = 3.251 mm → **8.301 mm²**, SWG 4/0 = 10.160 mm → 81.07 mm²); AWG from the formula | `assets/awg_swg_kcmil_table.json`; `ConversionDetailScreen:874-897`; `ElectricalConverters:120-127` | BS 3737 / ASTM B258 | The two gauges differ by up to ≈58 % in area at the same gauge number (SWG 10 = 8.30 vs AWG 10 = 5.26 mm²). The asset is correctly keyed `swg_table`; the SWG path uses the table diameter and the AWG path the formula — **no conflation**, both numerically right | **OK (a classic wire-gauge defect avoided; add a regression test)** |
| K-3 | Power triangle `P = S·cosφ`, `Q = S·sinφ`, `S = P/cosφ`, `cosφ` clamped to (0,1] | `:300-317` | — | exact; clamping removes `NaN`/division-by-zero | **OK** |
| K-4 | `mV/A/m = factor·(R·cosφ + X·sinφ)`; `ΔV = mVperAm·I·L/1000` | `:344-347` | BS 7671 App. 4 | dimension check: Ω/km ≡ mV/A/m; identical algebra to the cable engine and the VD-V2 engine | **OK — but see §4 DIVERGENT** |
| K-5 | Battery `E = Ah·V·DoD·η/1000`; `Ah = kWh·1000/(V·DoD·η)` | `:352-366` | IEEE 485 | exact inverses, both clamped | **OK** |
| K-6 | `I_sc = I_FLC/(1000·z_pu) = I_FLC/(10·%Z)` — **c = 1.0 implied** | `:369-373` | IEC 60909 Table 1 (LV c_max = 1.05) | 200 kVA/4 % → **7.217 kA**, whereas the transformer engine returns **7.58 kA** for the same machine — a 5 % divergence published under the same physical label | **DIVERGENT E-06(c)** |
| K-7 | µF ↔ kVAR, HP ↔ kW, frequency ↔ rpm, kcmil | `:128-300` | — | unit-explicit, clamp-guarded, internally consistent | **OK** |

---

## 3. Matrix integrity (element-by-element audit of the data assets)

Each JSON matrix was parsed programmatically and checked for internal consistency, completeness and conformance to its cited source.

### 3.1 `data_sheet.json` — conductor R/X matrix (21 sizes) — **PASS, with one caveat**

Checked every row against **IEC 60228 Table 2** (max DC resistance at 20 °C):

| Size (mm²) | 1.5 | 2.5 | 4 | 6 | 10 | 16 | 25 | 35 | 50 | 70 | 95 | 120 | 150 | 185 | 240 | 300 | 400 | 500 | 630 | 800 | 1000 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Cu table | 12.1 | 7.41 | 4.61 | 3.08 | 1.83 | 1.15 | 0.727 | 0.524 | 0.387 | 0.268 | 0.193 | 0.153 | 0.124 | 0.0991 | 0.0754 | 0.0601 | 0.047 | 0.0366 | 0.0283 | 0.0221 | 0.0176 |
| IEC 60228 | 12.1 | 7.41 | 4.61 | 3.08 | 1.83 | 1.15 | 0.727 | 0.524 | 0.387 | 0.268 | 0.193 | 0.153 | 0.124 | 0.0991 | 0.0754 | 0.0601 | 0.047 | 0.0366 | 0.0283 | 0.0221 | 0.0176 |
| Match | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Aluminium likewise matches IEC 60228 exactly at every size (2.5 → 12.1, …, 1000 → 0.0291), and the extra 1.5 mm² Al row (19.5) is a legitimate extrapolation. `R_Cu_70_est`/`R_Al_70_est` are internally consistent at exactly ×1.2 of the 20 °C value (1 + 0.00393·50 = 1.1965 for Cu; the file rounds to 1.200), which is the 70 °C operating-temperature resistance for **both** materials.

**Caveat (see E-03):** because the file labels the 70 °C column as an "est", the model aliases `R_Cu90 → R_Cu_70_est` and `R_Cu70 → R_Cu20`, the *consumer* labels diverge from the *content*. The matrix is correct; its naming and its consumers are not.

### 3.2 `derating_data.json` — temperature/depth/soil/grouping matrix — **one defective row, one truncated dimension**

| Sub-matrix | Structure | Verdict |
|---|---|---|
| `air_temperature` (20–55 °C × PVC/XLPE) | 8 rows | **DEFECT E-02**: XLPE 0.90 at 45 °C < PVC 0.95 — inverted; should be ≈0.95–0.96 |
| `ground_temperature` (15–50 °C × PVC/XLPE) | 8 rows | **PASS** — below the 35 °C reference PVC > XLPE and above it XLPE > PVC, i.e. internally consistent with the physics |
| `soil_resistivity` (80–300, ρ in °C·cm/W) | 8 keys | **PASS** for the tabulated range; ρ > 300 clamps to 0.67 with the file's own note "use engineer judgement" — **but the code emits no warning** (E-11(c)) |
| `burial_depth` (0.5–2.0 m) | 8 keys | Value ladder is sane and conservative up to 2 m; beyond 2 m the clamp to 0.91 is **non-conservative** (E-11(b)) |
| `grouping` | **6 columns only** (`Nr1…Nr6`) × 18 installation/formation/spacing combinations | **DEFECT E-01**: the underlying standard tabulates to 20 circuits; the engine silently freezes at 6 |
| Reference conditions | declared: 40 °C air, 35 °C ground, soil ρ 120 | consistent with IEC 60287; and the code's base factors are exactly 1.0 at those conditions (verified in the table) ✓ |

**Grouping sanity check (buried, trefoil, touching):** 1→1.00, 2→0.77, 3→0.66, 4→0.60, 5→0.56, 6→0.53. Monotone, decreasing, and matching the standard's shape for the first six circuits — so the *existing* entries are right; the defect is the missing 7th…20th columns and the silent clamp.

### 3.3 `transformers_seed.json` — **FAIL (internal inconsistency, >10×)**

6 pre-sets (TX-100 … TX-2000). Each states `kVA`, `percentZ`, `xrRatio` **and** explicit `rtMOhm`, `xtMOhm`, `iscKA`. These must satisfy the engine's own relations `Z_t = (Un²/S_r)·(%Z/100)·1000`, `R_t = Z_t/√(1+(X/R)²)`, `X_t = R_t·(X/R)`, `I″k = 1.05·Un/(√3·Z_t)`:

| Preset | `%Z` / kVA | Z_t from %Z | R_t / X_t from %Z | I″k from %Z | File `rtMOhm` / `xtMOhm` | File `iscKA` | Verdict |
|---|---|---|---|---|---|---|---|
| TX-100 | 4 % / 100 kVA | 64 mΩ | 17.58 / 61.54 mΩ | 3.79 kA | **1 / 3** | 3.5 | R/X wrong ≈17× |
| TX-250 | 4 % / 250 kVA | 25.6 mΩ | 6.21 / 24.83 mΩ | 9.47 kA | **0.5 / 2** | 9 | R/X wrong ≈12× |
| TX-500 | 4.5 % / 500 kVA | 14.4 mΩ | 3.12 / 14.05 mΩ | 16.84 kA | **0.25 / 1** | 16 | R/X wrong ≈12× |
| TX-1000 | 5 % / 1000 kVA | 8.0 mΩ | 1.57 / 7.85 mΩ | 30.3 kA | **0.1 / 0.5** | 28 | R/X wrong ≈16× |
| TX-1600 | 6 % / 1600 kVA | 6.0 mΩ | 1.08 / 5.9 mΩ | 40.4 kA | **0.05 / 0.2** | 38 | R/X wrong ≈21× |
| TX-2000 | 6 % / 2000 kVA | 4.8 mΩ | 0.79 / 4.73 mΩ | 50.5 kA | **0.04 / 0.15** | 48 | R/X wrong ≈20× |

The `iscKA` column agrees with the engine (within c-factor rounding) but the **`rtMOhm`/`xtMOhm` columns do not** — e.g. TX-500's implied 1.03 mΩ total would give ≈225 kA, not the 16 kA stated in the same record. (A primary-side interpretation does not rescue them either: the reflection factor would be (11 000/400)² = 756, far larger than the observed discrepancy.) They are simply inconsistent, and inconsistent *within the same object*.
**Action:** if these fields are rendered anywhere (catalog "typical transformer" reference, AI context, PDF appendix) they are misinformation and must be regenerated from the engine relations; if they are dead data, delete them. Confirming the display path is required — see §6 (limits).

### 3.4 `qa_reference_cases.json` — **PASS (a genuine verification oracle)**

Reproduced from first principles in §2.1: `TC-TX-01` matches to the last digit (zt 32, rt 3.18, xt 31.84, Isc3 7.58, Isc1 7.84, z0 28.8, FLC 288.68) and `TC-CABLE-008` matches the IEC 60364-5-54 Table 54.7 CPC rule. One clarity defect: `TC-TX-02` publishes `zt`/`Isc` for the *auto-selected* 315 kVA rather than the 400 kVA in its own input block — reads as an inconsistency to a naive reader (documentation defect, not a calculation defect). Recommend adding a `selectedSizeRef` field or a comment.

### 3.5 Equipment catalog matrices — **arithmetically PASS, one semantic finding**

| Asset | Rows | Checks performed | Result |
|---|---|---|---|
| `cable_catalog.json` | **1177** | 4 manufacturers (Ducab, Elsewedy, IEC-Generic, Riyadh Cables), 43 typeCodes; zero non-positive sizes/ampacities; **zero duplicate (mfg, typeCode, size) keys** | **PASS** |
| `cable_catalog.json` — completeness | 1177 | `od ≤ 0` and `wt ≤ 0` in **120 rows, all of them `IEC-Generic`** (the three real manufacturers are complete). Covered by the documented OD/weight estimation + `isODApproximated` flag | **PASS (handled and flagged)** |
| `cable_catalog.json` — **base-ampacity normalization** | 1177 | air/ground ampacity ratio: **min 0.69, max 1.49, average 0.97**; the same manufacturer and family shows both directions (Elsewedy 70 mm²: gnd 255 / air 235 / duct 195; Elsewedy type-90 70 mm²: gnd 314 / duct 230 / air 218; Ducab 240 mm²: gnd 570 / air 606) | **FINDING E-19 (medium)** — below |
| `breaker_catalog.json` | 52 | `Icu > 0` for all; `Ics ≤ Icu` for all, and **`Ics = 0.75·Icu` exactly** (the IEC 60898-1 requirement for MCBs); `Icm ≥ Icu` for all; no missing `orderingCode` | **PASS — standards-correct** |
| `breaker_catalog.json` — repeated keys | 52 | 3 groups repeat (mfg, series, rating, poles) but differ in `version`/`tripUnit` — curve/config variants (B/C/D), not duplicates | **PASS (ensure `version` participates in selection)** |
| `busway_catalog.json` | 36 | `ratingA > 0`, `icwKA > 0`, `rMOhmPerM > 0`, `xMOhmPerM > 0` in every row; `I_pk/I_cw` ∈ {1.47, 1.62, 1.76, 1.79, 1.83, 2.06, 2.1, 2.2} — exactly the IEC 61439-1 Table 7 band | **PASS** |
| `conduit_catalog.json` | 21 | `areaTotalMm2` equals `π·d²/4` within 0.12 mm² **for every row**; `maxFill40PctMm2` equals 40 % of the area exactly; 3 conduit types present (Rigid PVC, EMT Steel, GI Steel RMC); no non-positive diameters | **PASS (arithmetically exact)** — which isolates E-09 as the only defect here |
| `awg_swg_kcmil_table.json` | 34 SWG gauges | SWG diameters match BS 3737 (SWG 10 = 3.251 mm; 4/0 = 10.160 mm) and `areaMm2 = π·d²/4` for each | **PASS** |

**E-19 — Cable base ampacities are not demonstrably normalized to a common reference (Medium).**
`derating_data.json` declares its reference conditions explicitly (40 °C air, 35 °C ground, ρ = 120 °C·cm/W) and its factors are exactly 1.0 at those conditions, so the *derating* side is properly normalized. The *base* ampacity in `cable_catalog.json`, however, is whatever each manufacturer's table says — and the observed air/ground relationship (0.69 … 1.49, mean 0.97) **cannot arise from one set of reference conditions**: with air at 40 °C, ground at 35 °C and identical soil resistivity, the IEC 60364-5-52 free-air (method E) rating should exceed the direct-buried (method D) rating essentially always. The spread implies rows carry different underlying bases (different ambient, soil resistivity, sub-method, or a transposed column in one manufacturer's import).
**Impact:** the same multiplicative derating chain is applied to non-comparable base numbers. Changing manufacturer — or comparing two manufacturers' products — silently changes the cable size, and a buried installation can appear *cooler* than a free-air one. For a consultant's deliverable this is a defensibility problem, not merely a data-quality one.
**Action:** (1) confirm each manufacturer's table header (ambient / soil resistivity / method) and normalize, or extend the existing per-row `provenance` field into explicit `referenceConditions`; (2) add a validation test asserting `air ≥ gnd` under the app's declared reference conditions, failing loudly when a row violates it; (3) surface ampacity-base provenance in the sizing result exactly as `odSourceMfg` already does for OD.

### 3.6 Constants matrices (in code, not JSON)

| Constant set | Values | Clause | Verdict |
|---|---|---|---|
| Adiabatic `k` (conductors) | Cu PVC 115, Cu XLPE 143, Al PVC 76, Al XLPE 94; bare Cu in soil 159; galvanised steel 58 | IEC 60364-5-54 Tab. 54.2/54.3 | **PASS** |
| Busbar `k` / conductivity | Cu 166 A·√s/mm², Al 106; 56 / 34 MS/m | DIN 43671 / IEC 60865-1 | **PASS** (56 MS/m ⇔ 0.01786 μΩ·m for Cu) |
| IEC 61439-1 Table 10 RDF | 1→1.0, 2–3→0.9, 4–5→0.8, 6–9→0.7, ≥10→0.6 | Table 10 | **PASS** |
| IEC 60947-2 `Icm/Icu` tiers | 1.41 / 1.42 / 1.47 / 1.53 / 1.70 / 2.00 / 2.10 / 2.20 | Tab. 2 | **PASS** |
| Busbar peak factor `n` | resolved from `I_cw` (1.47…2.2 observed in the catalog) | IEC 61439-1 Tab. 7 | **PASS** |
| B/C/D trip multiples | 5 / 10 / 20 × In | IEC 60364-4-41 §411.4.4 | **PASS** |
| Max-Zs table | B/C/D × 6–125 A | Tab. 41.3 (230 V TN) | **PASS** (spot-checks exact) |
| Standard-size ladders (cable, device, transformer, generator, UPS, ATS, contactor, fuse, conduit) | as tabulated in §2 | IEC/BS preferred numbers | **PASS** (monotone, no gap that would mis-size) |

---

## 4. Divergence matrix — the same physical quantity computed more than once

The single most actionable pattern in this codebase: **most of the engineering findings share one root cause — duplicated physics.**

| Quantity | Implementation A | Implementation B | Divergence | Finding |
|---|---|---|---|---|
| Motor starting multiplier | `MotorStartMethod`: DOL **6.0**, Δ 2.5, soft 1.8, VFD 1.2 (`QuickNewToolsModels:346-351`) | `MotorStarterType`: DOL **6.5**, Δ **2.17**, soft **2.5**, VFD **1.15** (`:571-576`, same file) | up to **39 %** (soft 1.8 vs 2.5) | **E-06(a)** |
| B/C/D trip multipliers | `ProtectionEngine.TripCurve` 5/10/20 — declared "single source of truth … do not duplicate multipliers elsewhere" (`ProtectionEngine.kt:82-88`) | `QuickEarthingEngine` hardcodes 5/10/20 (`QuickNewToolsModels:108-114`) | 0 % today; the declared invariant is already violated and **will** drift | **E-06(b)** |
| Transformer I″k | `TransformerEngine` c = **1.05** → 7.58 kA (200 kVA / 4 %) | `ElectricalConverters.txQuickShortCircuit` c = **1.0** → 7.22 kA | **5 %** on the same machine under the same label | **E-06(c)** |
| UPS sizing margin | `UpsEngine` applies the design margin **then** ÷0.8 → 100 kVA for a 60 kVA load | `QuickUpsEngine` ÷0.8 only → 80 kVA | **25 %** capacity difference | **E-06(d)** |
| Earth-conductor basis | `EarthingEngine` prefers **Ik1** (correct per §54.2.2) | `QuickEarthingEngine` always uses 3-phase Isc | over/under-size depending on Z0/Z1 | **E-06(e)** |
| Voltage-drop equation | `CableSizingEngine:615-624` | `ElectricalConverters:344-347`; `VoltageDropCalculationEngine` (`VoltageDropV2Models:209-227`) | **0 % algebraically** — three copies to maintain | **E-06(f)** |
| Cable resistance base | `CableSizingEngine`: PVC→`R_Cu20`, XLPE→`R_Cu_70_est`, `thermalFactor = 1.0` | `QuickEngineeringDataBridge` labels those same numbers `"R_Cu70 (70 °C)"` / `"R_Cu90 (90 °C)"` | label ≠ content | **E-03 / D-3** |
| `Z0/Z1` for the earth-fault current | `TransformerEngine` (0.9 only when the user left 1.0) | `ProtectionEngine.deriveZeFromSource` assumes 0.9 unconditionally | consistent today; two sites to keep in step | **E-12 (watch)** |

**Root-cause statement for remediation:** there is no single authoritative module for (i) *physical constants* (k values, trip/start multipliers, c-factors), (ii) *derating/table data*, or (iii) *display-label provenance*. Every divergence above is a satellite drifting from a value that should be derived. The project's stated philosophy — deterministic, single-source — is right in intent and only partly realised in structure.

---

## 5. Consolidated engineering findings & remediation

| ID | Sev | Finding | Fix (concrete) | Acceptance criterion |
|---|---|---|---|---|
| **E-01** | **High** | Grouping factor frozen at the 6-circuit value for 7–20 circuits (silent, optimistic up to ≈18 %) | Extend `derating_data.json` grouping to ≥20 circuits (IEC 60364-5-52 Tab. B.52.17); remove `coerceIn(1,6)`; warn where data is genuinely absent | 12 buried touching circuits yield the 12-circuit factor; a beyond-table input raises a visible warning, never a silent clamp |
| **E-02** | **High** | XLPE air factor at 45 °C (0.90) inverted vs PVC and vs the same file's ground table | Correct to ≈0.95–0.96; re-derive the air row set from Tab. B.52.14 | Test asserts `XLPE >= PVC` for every ambient above the reference, in every table |
| **E-03** | **High** | Voltage drop uses 20 °C (PVC) / 70 °C (XLPE) resistance while the UI advertises "R_Cu70 (70 °C)" / "R_Cu90 (90 °C)"; Annex G requires the maximum operating temperature | Choose one convention, document it in one module, make the on-screen label equal the number's real basis | Exactly one R-column-to-label mapping exists; displayed provenance matches the arithmetic |
| **E-04** | **High** | Earthing `Zs` omits R1+R2 and the electrode, uses Isc3 not Ik1, and is judged against a Zs_max derived from the transformer FLC — a source screen presented as the ADS verdict | Rename to a source-loop screen, or include R1+R2 + the achieved electrode resistance and state the `In` used; `ProtectionEngine.calculateAds` remains the ADS authority | The panel names its impedance basis and the device `In`; a 2 Ω electrode cannot silently PASS |
| **E-05** | Med | Main equipotential bonding conductor = full electrode conductor (e.g. 185 mm²) instead of §544.2's capped rule | Cu `max(6, min(25, S/2))`; Al 16/50 | Bonding ≤ 25 mm² Cu in every project; boundary tests at S/2 |
| **E-06** | Med | Five duplicated constant sets (a–e) that already disagree | Single `EngineeringConstants` for multipliers/c-factors/k-values; one `UpsSizingPolicy` with one margin concept | A test asserts every consumer reads the constant; no such numeric literal outside the constants module |
| **E-07** | Med | Panel schedule hardcodes 230 V and 0.8 pf | Use `circuit.voltageV` and the real pf mix, with documented defaults | A 415 V board scales correctly; test at pf 0.9 |
| **E-08** | Med | `transformers_seed.json` R/X inconsistent with its own %Z/kVA/`iscKA` (by >10×) | Regenerate from the engine relations or delete; confirm the display path | Every row satisfies `Z = sqrt(R²+X²)` and `I″k = c·Un/(√3·Z)` within 2 %; CI check added |
| **E-09** | Med | Conduit procurement rollup hardcodes "Rigid PVC"; groups by trade size only | Group by `(conduitType, tradeSize, catalogCode)` and carry the real type | EMT and GI RMC produce distinct, correctly-typed BOQ lines |
| **E-10** | **Med-High** | Safety checks re-parse a *display string* for conductor area (busway → 800 "mm²"); an unparseable string silently becomes 1.5 mm² | Add numeric `conductorAreaMm2` / `cpcAreaMm2` to `SizingResult`; a missing area is a hard `DATA MISSING` | No regex parsing in the short-circuit or protection engines; a null area fails loudly |
| **E-11** | Med | Table-range clamps (grouping > 6, depth > 2 m, ρ > 300, T > 55 °C) silently extrapolate; the depth case is non-conservative | Warn with the substituted bucket; set REVIEW REQUIRED | Every out-of-range input appears in `warnings` and in the UI/PDF note |
| **E-12** | Low | No IEC 60909 `K_T` transformer correction; Dyn11 `Z0/Z1 = 0.9` applied conditionally | Declare both as named method assumptions in the result and report appendix | Assumptions appear on the short-circuit worksheet |
| **E-13** | Low | Generator LV terminal voltage hardcoded 400 V and unflagged | Derive from the generator/circuit voltage, or add to the flagged assumptions | A 480 V islanded system yields the correct I″k |
| **E-14** | Low | ADS loop temperature factor fixed at 1.20 for every insulation | 1.275 for XLPE, 1.20 for PVC | Factor selected from `SizingResult` insulation |
| **E-15** | Low | Earthing ring perimeter hardcoded to 20 m, unflagged | Make it an input with a flagged default | The 20 m assumption appears in `messages` |
| **E-16** | Low | Generator ">15 % voltage dip" message with no dip computed on that path | Compute the dip (as the quick tool does) or reword qualitatively | Message text matches computed data or carries no number |
| **E-17** | Low | UPS battery strings assume 100 Ah blocks | Parameterise the block size | Input-driven string count |
| **E-19** | Med | Cable base ampacities not normalized to the declared reference conditions (air/gnd 0.69–1.49, mean 0.97) | Verify/record per-row reference conditions; validate `air >= gnd` at the declared reference | Validation passes or explicitly whitelists documented exceptions |

**Sequencing (consulting-risk order).** `P0`: **E-01, E-02, E-03, E-04, E-10** — any of these can change a deliverable's verdict or a purchased quantity. `P1`: **E-19, E-05, E-06, E-07, E-08, E-09, E-11** — consistency and defensibility of the output set. `P2`: **E-12 … E-17** — assumption documentation and message precision.

> **ID note:** `E-18` is unused — it was reserved during drafting and retired before publication, so the sequence jumps from E-17 to E-19. AUDIT-3/AUDIT-4 continue the numbering at E-20 and above for findings discovered at the current checkout; cross-report IDs are therefore unique.

---

## 6. Limits of this audit (what is NOT certified)

Stated explicitly so that nothing above is over-read:

1. **Not a UI/UX, accessibility or visual review.** The Compose layer was pattern-screened, not read (see AUDIT-1 §6, tier T3).
2. **No runtime execution of the engines and no external A/B.** All verification is static reading plus independent arithmetic re-derivation. No ETAP / DIgSILENT / Excel cross-check and no instrumented device run was performed. The QA reference file reproduces exactly, which is strong evidence — but it remains a *self-consistent* oracle.
3. **E-08's impact is not quantified.** Whether `transformers_seed.json`'s R/X columns reach the UI, the PDF or the AI context was not traced to a rendering site in this pass. The data defect itself is proven; the blast radius is not.
4. **Manufacturer-catalogue verification is out of scope.** E-19's root cause requires the actual Ducab / Elsewedy / Riyadh Cables table headers (reference ambient, soil resistivity, installation sub-method). I verified the *internal spread*, not the *provenance*.
5. **Not a fitness-for-use certification.** This is a defect-and-equation review, not a design sign-off. No deliverable should be issued on the strength of this document alone; a licensed engineer must still review project outputs.
6. **Unread engine-adjacent code** (AUDIT-1 §6, tiers T3/T4): SLD layout geometry, PDF renderer internals and most Compose screens — unit-conversion defects there are not covered by this review.

### What can be said with confidence

- The **core LV design chain** (load → ampacity/derating → voltage drop → short-circuit → device selection → ADS → CPC) is implemented with the correct standard equations, correct arithmetic and correct units, and three of its published reference expectations reproduce exactly from first principles.
- The engine layer's **assumption discipline** (assumed x″d, K_G, X/R = 10, Icm estimation, assumed soil, missing catalogue entries) is better than typical commercial practice: unknowns are surfaced rather than silently invented, and several historic defects are documented and fixed in-line.
- The defects that matter most are **not exotic physics** — they are a truncated grouping table (E-01), one inverted derating row (E-02), a resistance-basis/provenance mismatch (E-03), a mislabelled loop-impedance screen (E-04), and numeric data being recovered from display strings (E-10). All five are mechanical to fix and are exactly the class of finding a reviewing authority raises against a third-party calculation package.

---

*End of AUDIT-2. Security, privacy, build and release-gate findings are in `AUDIT-1_SECURITY_AND_DEFECT_REPORT.md`.*


