package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AdsResult(
    val circuitId: Int,
    val circuitName: String,
    val calculatedZs: Double, // 0.0 when status == "DATA MISSING" (no loop impedance computed)
    val maxZsAllowed: Double, // device limit — catalog-independent, still reported when DATA MISSING
    val status: String, // PASS / FAIL / UNVERIFIED / DATA MISSING
    val disconnectionTime: String, // e.g., "< 0.4s" / "UNVERIFIED" / "DATA MISSING"
    val r1_ohm_km: Double?, // null = no data_sheet row for the resolved size (NEVER 0.0-as-"missing")
    val r2_ohm_km: Double?,
    val length_m: Double,
    val tempFactor: Double = ProtectionEngine.TEMP_FACTOR_CU_PVC,
    val warnings: List<String> = emptyList()
)

data class ProtectionResult(
    val circuitId: Int,
    val circuitName: String,
    val Ib_A: Double,
    val In_A: Double,
    val Iz_A: Double,
    val iscKA: Double,
    val icuKA: Double,
    val overloadStatus: String,      // PASS / FAIL (Ib <= In <= Iz)
    val shortCircuitStatus: String,   // PASS / FAIL (Icu >= Isc)
    val cableWithstandStatus: String, // PASS / FAIL (k^2 * S^2 >= Isc^2 * t)
    val selectivityStatus: String,    // PASS / WARNING (IEC 60947 1.6:1 ratio check)
    val finalStatus: String,          // PASS / FAIL / WARNING
    val messages: List<String> = emptyList()
)

object ProtectionEngine {

    /**
     * MCB trip curve characteristics per IEC 60364-4-41 Table 41.1:
     * the instantaneous (magnetic) trip current that causes automatic disconnection
     * within the stipulated time, expressed as a multiple of the rated current In.
     * B = 5xIn, C = 10xIn, D = 20xIn.
     */
    enum class TripCurve(val timesIn: Double) {
        B(5.0),
        C(10.0),
        D(20.0)
    }

    /**
     * STRICT trip-curve parser. Only explicit curve designations resolve to a curve:
     *  - exact single-letter strings: "B", "C", "D"
     *  - explicit tokens inside the string: "MCB Curve C", "Type D", "C-curve"
     *
     * Device-family strings such as "MCB", "MCCB", "ACB" resolve to NULL — they are
     * FAMILIES, not curves. (Legacy bug: `contains("B")` matched "MCB"/"MCCB"/"ACB"
     * and forced every overridden device onto the B-curve Zs limits.)
     */
    fun parseTripCurve(deviceType: String?): TripCurve? {
        if (deviceType.isNullOrBlank()) return null
        val s = deviceType.trim()
        TripCurve.values().firstOrNull { it.name.equals(s, ignoreCase = true) }?.let { return it }
        val match = Regex(
            """(?:curve|type|char(?:acteristic)?)\s*[:-]?\s*([BCD])\b""",
            RegexOption.IGNORE_CASE
        ).find(s) ?: return null
        return TripCurve.values().firstOrNull { it.name.equals(match.groupValues[1], ignoreCase = true) }
    }

    /**
     * True only for the MCB family (or unclassified/blank, which is conservatively
     * handled by the MCB path with an explicit assumption warning).
     * NOTE: "MCCB" does NOT contain the substring "MCB" — verified: M-C-C-B.
     */
    fun isMcbFamily(deviceType: String?): Boolean {
        if (deviceType.isNullOrBlank()) return true
        return deviceType.uppercase().contains("MCB")
    }

    /**
     * IEC 60364-4-41 §411.4.4 — the disconnection current Ia: the current causing
     * automatic disconnection of the circuit-breaking device within the stipulated time.
     * Single source of truth for the B/C/D multiplier convention (also used by
     * EarthingEngine — do not duplicate multipliers elsewhere).
     */
    fun disconnectionCurrent(in_A: Double, curve: TripCurve): Double = in_A * curve.timesIn

    /**
     * IEC 60364-4-41 §411.4.4 — Zs_max = (Cmin * U0) / Ia, with Cmin = 0.95.
     */
    fun maxZsAllowed(u0_V: Double, ia_A: Double, cmin: Double = 0.95): Double =
        if (ia_A > 0.0) (cmin * u0_V) / ia_A else Double.POSITIVE_INFINITY

    /**
     * H6 — Derive the upstream earth-source impedance Ze from the feeding transformer
     * result instead of assuming a hardcoded 0.01 Ω. Two layers:
     *   1. Z1 = (cmax · Un)/(√3 · I″k,3ph)        [IEC 60909 — transformer positive-sequence]
     *   2. Earth-loop-equivalent Ze = (2·Z1 + Z0)/3  [single line-to-earth fault loop:
     *      Z(1)+Z(2)+Z(0) with Z(2)=Z(1); per unit U0 → /3]
     * Z0 is taken as z0z1Ratio·Z1 (default 0.9 = the engine's Dyn11 Z0/Z1 convention), so
     * Ze = Z1·(2 + z0z1Ratio)/3 ≈ 0.967·Z1. For a 500 kVA / 4 % transformer:
     * Z1 ≈ 0.0128 Ω → Ze ≈ 0.0124 Ω (≈3 % less than Z1). This matches the Zs loop a
     * loop-tester would measure on the L-E fault path.
     * Returns 0.0 when no valid source data exists — callers fall back to the
     * explicit, labelled default (0.01 Ω) and must emit a warning.
     */
    fun deriveZeFromSource(isc3Phase_kA: Double, un_V: Double, cmax: Double = 1.05, z0z1Ratio: Double = 0.9): Double {
        if (isc3Phase_kA <= 0.0 || un_V <= 0.0) return 0.0
        val z1 = (cmax * un_V) / (sqrt(3.0) * isc3Phase_kA * 1000.0)
        return z1 * (2.0 + z0z1Ratio) / 3.0
    }

    /**
     * H4 — insulation-aware adiabatic k factor (IEC 60364-5-54 / IEC 60364-4-43):
     * Cu/XLPE = 143, Cu/PVC = 115, Al/XLPE = 94, Al/PVC = 76.
     * Used as the FALLBACK when no k factor was supplied — never assume Cu/XLPE blindly.
     */
    fun insulationKFactor(material: String, insulation: String): Double = when {
        material.equals("Al", ignoreCase = true) && insulation.equals("XLPE", ignoreCase = true) -> 94.0
        material.equals("Al", ignoreCase = true) -> 76.0
        insulation.equals("PVC", ignoreCase = true) -> 115.0
        else -> 143.0
    }

    /**
     * Max Zs values from IEC 60364-4-41 Table 41.3 for 230V systems.
     * Based on Zs = (U0 * Cmin) / Ia
     * U0 = 230V, Cmin = 0.95, Ia = 5*In (B), 10*In (C), 20*In (D)
     */
    private val MAX_ZS_TABLE = mapOf(
        "B" to mapOf(
            6.0 to 7.28, 10.0 to 4.37, 16.0 to 2.73, 20.0 to 2.19, 25.0 to 1.75,
            32.0 to 1.37, 40.0 to 1.09, 50.0 to 0.87, 63.0 to 0.69, 80.0 to 0.55,
            100.0 to 0.44, 125.0 to 0.35
        ),
        "C" to mapOf(
            6.0 to 3.64, 10.0 to 2.19, 16.0 to 1.37, 20.0 to 1.09, 25.0 to 0.87,
            32.0 to 0.68, 40.0 to 0.55, 50.0 to 0.44, 63.0 to 0.35, 80.0 to 0.27,
            100.0 to 0.22, 125.0 to 0.17
        ),
        "D" to mapOf(
            6.0 to 1.82, 10.0 to 1.09, 16.0 to 0.68, 20.0 to 0.55, 25.0 to 0.44,
            32.0 to 0.34, 40.0 to 0.27, 50.0 to 0.22, 63.0 to 0.17, 80.0 to 0.14,
            100.0 to 0.11, 125.0 to 0.09
        )
    )

    /**
     * ADS min-fault temperature-correction factor, 1 + α₂₀·(θ − 20), rounded to 4 decimals.
     *
     * α₂₀ per IEC 60228: Cu 0.00393 /K, Al 0.00403 /K. θ = maximum conductor operating
     * temperature: 70 °C for PVC insulation, 90 °C for XLPE.
     *  - Cu/PVC  1 + 0.00393 × 50 = 1.1965
     *  - Cu/XLPE 1 + 0.00393 × 70 = 1.2751
     *  - Al/PVC  1 + 0.00403 × 50 = 1.2015
     *  - Al/XLPE 1 + 0.00403 × 70 = 1.2821 by formula; 1.2827 as owner-specified (ENG-30 §12.2-b,
     *    see closeout report — the two differ by 0.0006, 0.05 %, verdict-identical on every
     *    bundled fixture).
     *
     * Selected per circuit by [adsTempFactor] from `SizingResult.cableMaterial` + `insulation`.
     * Replaces the former flat `1.20` constant that ignored material and insulation.
     */
    const val TEMP_FACTOR_CU_PVC = 1.1965
    const val TEMP_FACTOR_CU_XLPE = 1.2751
    const val TEMP_FACTOR_AL_PVC = 1.2015
    const val TEMP_FACTOR_AL_XLPE = 1.2827

    /**
     * ADS temperature-correction factor for a circuit's conductor material and insulation.
     *
     * Unknown/blank insulation resolves to the XLPE (90 °C) factor — the conservative
     * direction for a min-fault loop: a higher factor raises Zs, so an unknown insulation
     * class can never turn a failing loop into a passing one. CableSizingEngine only ever
     * emits "PVC"/"XLPE", so the fallback is belt-and-braces for busway/"Air" insulation
     * strings, which never reach the loop anyway (no size parses to a catalog row).
     */
    fun adsTempFactor(material: String?, insulation: String?): Double {
        val isAl = material.equals("Al", ignoreCase = true)
        val isXlpe = !insulation.equals("PVC", ignoreCase = true)
        return when {
            isAl && isXlpe -> TEMP_FACTOR_AL_XLPE
            isAl -> TEMP_FACTOR_AL_PVC
            isXlpe -> TEMP_FACTOR_CU_XLPE
            else -> TEMP_FACTOR_CU_PVC
        }
    }

    /**
     * Renders a resolved catalog size as a user-facing size label: 70.0 -> "70", 2.5 -> "2.5".
     * Without this the Double's default toString() turns "Cu 70 mm²" into "Cu 70.0 mm²".
     */
    private fun sizeLabel(size: Double): String =
        if (size == size.toLong().toDouble()) size.toLong().toString() else size.toString()

    fun calculateAds(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        ze_ohm: Double = 0.01,
        isZeResolved: Boolean = false
    ): AdsResult {
        val phaseSize = resolveConductorArea(sizingResult)
        val cpcSize = resolveCpcArea(sizingResult)
        
        // TEMPERATURE CONVENTION (single source of truth):
        // The catalog "R_Cu20/R_Al20" columns are VERIFIED to contain IEC 60228 20 °C DC
        // resistance values (e.g. 2.5 mm² Cu = 7.41 Ω/km = R20 exactly; 70 mm² = 0.268 Ω/km
        // = R20; 240 mm² = 0.0754 Ω/km = R20). For the ADS min-fault loop (cmin) the
        // resistance is corrected HERE to the conductor's maximum operating temperature θ,
        // 1 + α₂₀·(θ − 20), per IEC 60364-4-41 §411.4.4 — this is NOT double-counting.
        // α₂₀ (IEC 60228): Cu 0.00393 /K, Al 0.00403 /K; θ = 70 °C (PVC) / 90 °C (XLPE),
        // so the factor is material- AND insulation-specific (see [adsTempFactor]) rather
        // than the former flat 1.20. The max-fault path (ShortCircuitEngine, cmax = 1.05)
        // correctly uses the 20 °C values per IEC 60909-0 §7.1.2. Voltage drop uses the
        // 20 °C values by workbook/QA-parity convention (see CableSizingEngine thermalFactor).
        val tempFactor = adsTempFactor(sizingResult.cableMaterial, sizingResult.insulation)
        val l_m = circuit.length_m
        val effectiveZe = if (ze_ohm > 0.0) ze_ohm else 0.0
        val runs = kotlin.math.max(1, circuit.parallelRuns)
        
        // IEC 60364-4-41 §411.4.4: Zs_max = (Cmin · U0) / Ia. The trip CURVE must be parsed
        // strictly from explicit curve tokens — NEVER by substring-matching the device
        // family ("MCB", "MCCB", "ACB" all contain the letter "B").
        // Evaluated BEFORE the catalog lookups: the device limit is catalog-independent, so a
        // DATA MISSING result still reports the Zs_max that would apply to this circuit.
        val deviceFamily = (circuit.overrideDeviceType ?: sizingResult.deviceType ?: "").trim()
        val u0Ads = 230.0
        val rating = sizingResult.deviceSize_A
        val warnings = mutableListOf<String>()
        if (phaseSize == null || cpcSize == null) {
            warnings.add(
                "Conductor or CPC cross-section area is unverified or missing (phase: $phaseSize, cpc: $cpcSize) — " +
                    "ADS loop verdict is UNVERIFIED (never assumed PASS without conductor data)."
            )
        }
        if (!isZeResolved) {
            warnings.add(
                "Source impedance Ze is unverified (no source transformer resolved) — " +
                    "ADS loop verdict is UNVERIFIED (never assumed PASS without source impedance)."
            )
        }
        val parsedCurve = parseTripCurve(deviceFamily)
        val isMcb = isMcbFamily(deviceFamily)

        val maxZs: Double
        when {
            parsedCurve != null && isMcb -> {
                // MCB with an explicit curve designation (e.g. "MCB Curve D") -> table/fallback by curve
                maxZs = MAX_ZS_TABLE[parsedCurve.name]?.get(rating)
                    ?: maxZsAllowed(u0Ads, disconnectionCurrent(rating, parsedCurve))
            }
            isMcb -> {
                // MCB without an explicit curve token -> assume C (10×In), flagged as assumption
                warnings.add(
                    "Trip curve not specified for MCB '${deviceFamily.ifBlank { "MCB" }}' — " +
                        "assumed C-curve (10×In). Verify the device characteristic (IEC 60898)."
                )
                maxZs = MAX_ZS_TABLE["C"]?.get(rating)
                    ?: maxZsAllowed(u0Ads, disconnectionCurrent(rating, TripCurve.C))
            }
            else -> {
                // MCCB / ACB / LBS and other families must NOT be evaluated against the
                // MCB MAX_ZS_TABLE multipliers. Without a configured magnetic setting,
                // assume an instantaneous/short-time pick-up of 10×In and flag it.
                warnings.add(
                    "Device '$deviceFamily' is not an MCB — MCB Zs table not applied. " +
                        "Assuming instantaneous pick-up Ii = 10×In (${rating * 10.0} A) for Zs_max. " +
                        "Configure the actual Ii/Isd setting."
                )
                maxZs = maxZsAllowed(u0Ads, disconnectionCurrent(rating, TripCurve.C))
            }
        }

        // ── ENG-30 (§12.2-a): a missing resistance is DATA MISSING, never 0.0 ──────────
        // (1) Option C — refuse at the door when the resistance catalog never loaded, so a
        //     load failure can never silently produce a numeric Zs from missing rows.
        if (Catalog.dataSheet.isEmpty()) {
            warnings.add("Cable catalog (data_sheet) not loaded — ADS loop is DATA MISSING (no Zs computed).")
            return adsResultDataMissing(circuit, maxZs, tempFactor, l_m, warnings)
        }

        val phaseEntry = phaseSize?.let { sz -> Catalog.dataSheet.firstOrNull { it.size == sz } }
        val cpcEntry = cpcSize?.let { sz -> Catalog.dataSheet.firstOrNull { it.size == sz } }
        val isAl = sizingResult.cableMaterial.equals("Al", ignoreCase = true)
        val materialName = if (isAl) "Al" else "Cu"
        val r1: Double? = if (isAl) phaseEntry?.R_Al20 else phaseEntry?.R_Cu20
        val r2: Double? = if (isAl) cpcEntry?.R_Al20 else cpcEntry?.R_Cu20

        // (2) A size we DID resolve but the catalog has no row for (rogue size, or a
        //     materially different bundle) — same DATA MISSING class, message names the size.
        val catalogMisses = linkedSetOf<String>()
        if (phaseSize != null && phaseEntry == null) {
            catalogMisses += "Conductor resistance data missing for $materialName ${sizeLabel(phaseSize)} mm² in data_sheet.json"
        }
        if (cpcSize != null && cpcEntry == null) {
            catalogMisses += "Conductor resistance data missing for $materialName ${sizeLabel(cpcSize)} mm² in data_sheet.json"
        }
        if (catalogMisses.isNotEmpty()) {
            warnings.addAll(catalogMisses)
            return adsResultDataMissing(circuit, maxZs, tempFactor, l_m, warnings)
        }

        // (3) The Zs loop runs ONLY with both resistances resolved. An UNPARSED size
        //     (phaseSize/cpcSize null — busway, unparseable text) has no catalog row to
        //     miss: it keeps the legacy numeric shape Zs = Ze (no conductor term) and is
        //     reported UNVERIFIED below — a different failure class from a catalog miss.
        val calculatedZs = if (r1 != null && r2 != null) {
            effectiveZe + ((r1 / runs + r2) * l_m * tempFactor) / 1000.0
        } else {
            effectiveZe
        }

        val hasConductorData = phaseSize != null && cpcSize != null
        val isPass = calculatedZs <= maxZs && hasConductorData && isZeResolved
        val status = when {
            calculatedZs > maxZs -> "✗ FAIL"
            !isZeResolved || !hasConductorData -> "UNVERIFIED"
            else -> "✓ PASS"
        }
        val disconnectionTime = when {
            calculatedZs > maxZs -> "> 0.4s"
            !isZeResolved || !hasConductorData -> "UNVERIFIED"
            else -> "< 0.4s"
        }
        
        return AdsResult(
            circuitId = circuit.id,
            circuitName = circuit.circuitName,
            calculatedZs = (calculatedZs * 1000).roundToInt() / 1000.0,
            maxZsAllowed = (maxZs * 1000).roundToInt() / 1000.0,
            status = status,
            disconnectionTime = disconnectionTime,
            r1_ohm_km = r1,
            r2_ohm_km = r2,
            length_m = l_m,
            tempFactor = tempFactor,
            warnings = warnings
        )
    }

    /**
     * ADS result for a missing resistance basis (ENG-30 / §12.2-a), same shape and spirit as
     * ProtectionCalculationService.buildMissingAdsResult: no loop impedance is computed
     * (calculatedZs = 0.0), r1/r2 are null rather than 0.0, and status/disconnectionTime are
     * DATA MISSING — the caller is told the numbers are absent instead of being handed a
     * plausible 0.0 Ω loop that reads as a PASS. Zs_max is device data, not catalog data,
     * so the applicable limit is still reported.
     */
    private fun adsResultDataMissing(
        circuit: CircuitEntity,
        maxZs: Double,
        tempFactor: Double,
        lengthM: Double,
        warnings: List<String>
    ): AdsResult = AdsResult(
        circuitId = circuit.id,
        circuitName = circuit.circuitName,
        calculatedZs = 0.0,
        maxZsAllowed = (maxZs * 1000).roundToInt() / 1000.0,
        status = "DATA MISSING",
        disconnectionTime = "DATA MISSING",
        r1_ohm_km = null,
        r2_ohm_km = null,
        length_m = lengthM,
        tempFactor = tempFactor,
        warnings = warnings
    )

    /**
     * FULL PROTECTION Verification Engine (IEC 60364-4-43 / IEC 60947-2).
     * Consumes resolved SizingResult, ShortCircuitResult, and CoordinationEngine status.
     */
    fun evaluateProtection(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        shortCircuitResult: ShortCircuitResult?,
        upstreamIn_A: Double = 0.0,
        upstreamName: String = "Upstream Board",
        hasUpstreamDevice: Boolean = (upstreamIn_A > 0.0 && !upstreamName.startsWith("Incomer", ignoreCase = true))
    ): ProtectionResult {
        val ib = sizingResult.Ib_A
        val in_rating = sizingResult.deviceSize_A
        val iz = sizingResult.capacity_derated_Iz_A
        val isc = shortCircuitResult?.iscKA ?: 0.0
        val icu = sizingResult.deviceBreakingKA

        // 1. Overload Check: Ib <= In <= Iz
        val overloadPass = ib <= in_rating + 0.01 && in_rating <= iz + 0.01
        val overloadStatus = if (overloadPass) "PASS" else "FAIL"

        val isIscMissing = shortCircuitResult == null ||
            shortCircuitResult.status == "DATA MISSING" ||
            shortCircuitResult.iscKA <= 0.0

        // 2. Short-Circuit Breaking Capacity Check: Icu >= Isc
        val (scPass, shortCircuitStatus) = if (isIscMissing) {
            false to "DATA MISSING"
        } else {
            val pass = icu >= isc - 0.001
            pass to (if (pass) "PASS" else "FAIL")
        }

        // 3. Cable Thermal Withstand Check (k^2 * S^2 >= Isc^2 * t)
        val s_mm2 = resolveConductorArea(sizingResult)
        val (cableWithstandPass, cableWithstandStatus) = when {
            s_mm2 == null || s_mm2 <= 0.0 -> false to "DATA MISSING"
            isIscMissing -> false to "DATA MISSING"
            else -> {
                val k = if (sizingResult.kFactor > 0.0) sizingResult.kFactor
                else insulationKFactor(sizingResult.cableMaterial, sizingResult.insulation)
                val t = if (sizingResult.clearingTime_s > 0.0) sizingResult.clearingTime_s else 0.1
                val k2S2 = (k * s_mm2) * (k * s_mm2)
                val iscA = isc * 1000.0
                val Isc2t = iscA * iscA * t
                val pass = k2S2 >= Isc2t - 0.001
                pass to (if (pass) "PASS" else "FAIL")
            }
        }

        // 4. Selectivity / Discrimination Check via CoordinationEngine
        val selectivityStatus: String
        val messages = mutableListOf<String>()

        if (hasUpstreamDevice && upstreamIn_A > 0.0) {
            val coord = CoordinationEngine.checkCoordination(circuit, sizingResult, upstreamName, upstreamIn_A)
            selectivityStatus = if (coord.status.contains("OK")) "PASS" else "WARNING"
            if (!coord.status.contains("OK")) {
                messages.add("Selectivity Warning: ${coord.reason}")
            }
        } else {
            selectivityStatus = "N/A"
        }

        if (!overloadPass) {
            if (ib > in_rating) messages.add("Overload Fail: Design current Ib (%.1f A) > Device rating In (%.1f A)".format(ib, in_rating))
            if (in_rating > iz) messages.add("Overload Fail: Device rating In (%.1f A) > Cable capacity Iz (%.1f A)".format(in_rating, iz))
        }
        if (shortCircuitStatus == "DATA MISSING") {
            messages.add("Short-Circuit: Fault current Isc data missing or uncalculated (DATA MISSING)")
        } else if (!scPass) {
            messages.add("Short-Circuit Fail: Fault current Isc (%.2f kA) > Device breaking capacity Icu (%.2f kA)".format(isc, icu))
        }
        if (cableWithstandStatus == "DATA MISSING") {
            if (s_mm2 == null || s_mm2 <= 0.0) {
                messages.add("Cable Withstand: Conductor size missing, unparseable, or non-cable equipment (DATA MISSING)")
            } else {
                messages.add("Cable Withstand: Fault current Isc data missing or uncalculated (DATA MISSING)")
            }
        } else if (!cableWithstandPass) {
            messages.add("Cable Withstand Fail: Short-circuit energy I²t exceeds cable thermal withstand k²S²")
        }

        // 5. Combined Final Status
        val finalStatus = when {
            cableWithstandStatus == "DATA MISSING" || shortCircuitStatus == "DATA MISSING" -> "DATA MISSING"
            !overloadPass || !scPass || !cableWithstandPass -> "FAIL"
            selectivityStatus == "WARNING" -> "WARNING"
            else -> "PASS"
        }

        return ProtectionResult(
            circuitId = circuit.id,
            circuitName = circuit.circuitName,
            Ib_A = ib,
            In_A = in_rating,
            Iz_A = iz,
            iscKA = isc,
            icuKA = icu,
            overloadStatus = overloadStatus,
            shortCircuitStatus = shortCircuitStatus,
            cableWithstandStatus = cableWithstandStatus,
            selectivityStatus = selectivityStatus,
            finalStatus = finalStatus,
            messages = messages
        )
    }

    fun parseCableConductorArea(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (s.contains("busway", ignoreCase = true) ||
            s.contains("housing", ignoreCase = true) ||
            s.contains("trunking", ignoreCase = true) ||
            s.equals("N/A", ignoreCase = true) ||
            s.equals("-")
        ) {
            return null
        }
        if (Regex("""\b\d+\s*A\b""").containsMatchIn(s)) {
            return null
        }
        val match = Regex("""^(?:(?:\d+)\s*[xX*]\s*)?(\d+(?:\.\d+)?)\s*(?:mm[²2])?(?:\s*\(?[A-Za-z/]+\)?)?$""", RegexOption.IGNORE_CASE).find(s)
            ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun resolveConductorArea(sizingResult: SizingResult): Double? =
        sizingResult.conductorAreaMm2 ?: parseCableConductorArea(sizingResult.cableSize_mm2)

    fun resolveCpcArea(sizingResult: SizingResult): Double? =
        sizingResult.cpcAreaMm2 ?: parseCableConductorArea(sizingResult.earthCPC_mm2)

    private fun extractNumericSize(sizeStr: String): Double? = parseCableConductorArea(sizeStr)
}
