package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AdsResult(
    val circuitId: Int,
    val circuitName: String,
    val calculatedZs: Double,
    val maxZsAllowed: Double,
    val status: String, // PASS / FAIL
    val disconnectionTime: String, // e.g., "< 0.4s"
    val r1_ohm_km: Double,
    val r2_ohm_km: Double,
    val length_m: Double,
    val tempFactor: Double = 1.20,
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
     * result instead of assuming a hardcoded 0.01 Ω:
     *   Ze = (cmax · Un) / (√3 · I″k,3ph)   [IEC 60909; equals the transformer
     *   positive-sequence impedance Zt upstream of the circuit cables]
     * Returns 0.0 when no valid source data exists — callers fall back to the
     * explicit, labelled default (0.01 Ω) and must emit a warning.
     */
    fun deriveZeFromSource(isc3Phase_kA: Double, un_V: Double, cmax: Double = 1.05): Double =
        if (isc3Phase_kA > 0.0 && un_V > 0.0) (cmax * un_V) / (sqrt(3.0) * isc3Phase_kA * 1000.0) else 0.0

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

    fun calculateAds(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        ze_ohm: Double = 0.01 // Assume a small source impedance if not provided
    ): AdsResult {
        val phaseSize = extractNumericSize(sizingResult.cableSize_mm2)
        val cpcSize = extractNumericSize(sizingResult.earthCPC_mm2)
        
        val phaseEntry = Catalog.dataSheet.firstOrNull { it.size == phaseSize }
        val cpcEntry = Catalog.dataSheet.firstOrNull { it.size == cpcSize }
        
        val isAl = sizingResult.cableMaterial.equals("Al", ignoreCase = true)
        val r1 = if (isAl) (phaseEntry?.R_Al70 ?: 0.0) else (phaseEntry?.R_Cu70 ?: 0.0)
        val r2 = if (isAl) (cpcEntry?.R_Al70 ?: 0.0) else (cpcEntry?.R_Cu70 ?: 0.0)
        
        val tempFactor = 1.20
        val l_m = circuit.length_m
        val calculatedZs = ze_ohm + ((r1 + r2) * l_m * tempFactor) / 1000.0
        
        // IEC 60364-4-41 §411.4.4: Zs_max = (Cmin · U0) / Ia. The trip CURVE must be parsed
        // strictly from explicit curve tokens — NEVER by substring-matching the device
        // family ("MCB", "MCCB", "ACB" all contain the letter "B").
        val deviceFamily = (circuit.overrideDeviceType ?: sizingResult.deviceType ?: "").trim()
        val u0Ads = 230.0
        val rating = sizingResult.deviceSize_A
        val warnings = mutableListOf<String>()
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

        val isPass = calculatedZs <= maxZs
        
        return AdsResult(
            circuitId = circuit.id,
            circuitName = circuit.circuitName,
            calculatedZs = (calculatedZs * 1000).roundToInt() / 1000.0,
            maxZsAllowed = (maxZs * 1000).roundToInt() / 1000.0,
            status = if (isPass) "✓ PASS" else "✗ FAIL",
            disconnectionTime = if (isPass) "< 0.4s" else "> 0.4s",
            r1_ohm_km = r1,
            r2_ohm_km = r2,
            length_m = l_m,
            warnings = warnings
        )
    }

    /**
     * FULL PROTECTION Verification Engine (IEC 60364-4-43 / IEC 60947-2).
     * Consumes resolved SizingResult, ShortCircuitResult, and CoordinationEngine status.
     */
    fun evaluateProtection(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        shortCircuitResult: ShortCircuitResult?,
        upstreamIn_A: Double = 0.0,
        upstreamName: String = "Upstream Board"
    ): ProtectionResult {
        val ib = sizingResult.Ib_A
        val in_rating = sizingResult.deviceSize_A
        val iz = sizingResult.capacity_derated_Iz_A
        val isc = shortCircuitResult?.iscKA ?: 0.0
        val icu = sizingResult.deviceBreakingKA

        // 1. Overload Check: Ib <= In <= Iz
        val overloadPass = ib <= in_rating + 0.01 && in_rating <= iz + 0.01
        val overloadStatus = if (overloadPass) "PASS" else "FAIL"

        // 2. Short-Circuit Breaking Capacity Check: Icu >= Isc
        val scPass = icu >= isc - 0.001
        val shortCircuitStatus = if (scPass) "PASS" else "FAIL"

        // 3. Cable Thermal Withstand Check (k^2 * S^2 >= Isc^2 * t)
        val s_mm2 = extractNumericSize(sizingResult.cableSize_mm2)
        val k = if (sizingResult.kFactor > 0.0) sizingResult.kFactor else 143.0
        val t = if (sizingResult.clearingTime_s > 0.0) sizingResult.clearingTime_s else 0.1
        val k2S2 = (k * s_mm2) * (k * s_mm2)
        val iscA = isc * 1000.0
        val Isc2t = iscA * iscA * t
        val cableWithstandPass = k2S2 >= Isc2t - 0.001
        val cableWithstandStatus = if (cableWithstandPass) "PASS" else "FAIL"

        // 4. Selectivity / Discrimination Check via CoordinationEngine
        val selectivityStatus: String
        val messages = mutableListOf<String>()

        if (upstreamIn_A > 0.0) {
            val coord = CoordinationEngine.checkCoordination(circuit, sizingResult, upstreamName, upstreamIn_A)
            selectivityStatus = if (coord.status.contains("OK")) "PASS" else "WARNING"
            if (!coord.status.contains("OK")) {
                messages.add("Selectivity Warning: ${coord.reason}")
            }
        } else {
            selectivityStatus = "PASS"
        }

        if (!overloadPass) {
            if (ib > in_rating) messages.add("Overload Fail: Design current Ib (%.1f A) > Device rating In (%.1f A)".format(ib, in_rating))
            if (in_rating > iz) messages.add("Overload Fail: Device rating In (%.1f A) > Cable capacity Iz (%.1f A)".format(in_rating, iz))
        }
        if (!scPass) {
            messages.add("Short-Circuit Fail: Fault current Isc (%.2f kA) > Device breaking capacity Icu (%.2f kA)".format(isc, icu))
        }
        if (!cableWithstandPass) {
            messages.add("Cable Withstand Fail: Short-circuit energy I²t exceeds cable thermal withstand k²S²")
        }

        // 5. Combined Final Status
        val finalStatus = when {
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

    private fun extractNumericSize(sizeStr: String): Double {
        return Regex("""(\d+(\.\d+)?)""").find(sizeStr)?.value?.toDoubleOrNull() ?: 1.5
    }
}
