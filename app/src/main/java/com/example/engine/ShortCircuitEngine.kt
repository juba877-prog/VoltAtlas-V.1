package com.example.engine

import com.example.data.CircuitEntity
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.exp
import kotlin.math.round

data class ShortCircuitResult(
    val circuitId: Int,
    val sourceName: String,
    val cableMm2: String,
    val iscKA: Double,
    val iwKA: Double,
    val deviceKA: Double,
    val deviceCheck: String,
    val cableCheck: String,
    val marginPct: Double,
    val status: String,
    val note: String,
    val ipKA: Double = 0.0,
    val zTotalMOhm: Double = 0.0,
    val rMOhm: Double = 0.0,
    val xMOhm: Double = 0.0
)

object ShortCircuitEngine {

    fun calculate(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>,
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        generatorXdPercent: Double = 12.0,           // ASSUMED subtransient reactance — verify nameplate
        atsSelectedSource: Map<String, String> = emptyMap(), // atsId -> "NORMAL" | "EMERGENCY"
        motorContributionEnabled: Boolean = false,   // IEC 60909 §6.10 motor group — default OFF
        motorGroupKVA: Double = 0.0,
        motorLoadRatioILR_IR: Double = 5.5,
        makingCheckFails: Boolean = false            // false = WARNING on Icm < ip; true = FAIL
    ): Map<Int, ShortCircuitResult> {
        val results = mutableMapOf<Int, ShortCircuitResult>()

        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id] ?: continue
            
            // Validate data integrity guards
            val rawCableStr = sizing.cableSize_mm2
            val cableMm2 = parseNumericSize(rawCableStr)
            
            if (sizing.cableOD_mm <= 0.0 || cableMm2 == null) {
                results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Missing OD or unable to parse cable size")
                continue
            }

            // --- Source resolution (IEC 60909-0:2016) — polymorphic by source type (CRITICAL-4) ---
            //  transformer | grid | ats(NORMAL)  -> transformer positive-sequence impedance
            //  generator   | ats(EMERGENCY)      -> generator subtransient impedance; the transformer
            //                                       is NOT in the impedance path unless a real
            //                                       interposing transformer exists between the
            //                                       generator and the fault point.
            var sourceKind = "transformer"   // "transformer" | "generator"
            var sourceId = ""
            var curr = circuit
            while (true) {
                val fedLower = curr.fedFrom.trim().lowercase()
                if (fedLower in listOf("transformer", "ats", "generator", "grid")) {
                    val atsSource = if (fedLower == "ats")
                        atsSelectedSource[curr.assignedAtsId?.uppercase() ?: "ATS-01"]?.uppercase()
                    else null
                    sourceKind = if (fedLower == "generator" || atsSource == "EMERGENCY") "generator" else "transformer"
                    sourceId = if (sourceKind == "generator") (curr.assignedGeneratorId ?: "GEN-01") else curr.transformerId
                    break
                }
                val parentId = circuitParents[curr.id]
                if (parentId == null) break
                curr = resolvedCircuits.find { it.id == parentId } ?: break
            }

            val c = 1.05
            val sourceWarnings = mutableListOf<String>()
            var un = GENERATOR_TERMINAL_V
            var rTotalOhm = 0.0
            var xTotalOhm = 0.0

            when (sourceKind) {
                "generator" -> {
                    // IEC 60909-0:2016 §6.6/6.7 — generator subtransient impedance from rated kVA:
                    // Z_G = (x″d/100)·(U_rG²/S_rG); R_G ≈ 0.15·X″d for LV machines
                    // (X″d = Z_G/√(1 + 0.15²)). x″d is ASSUMED and flagged; K_G assumed 1.0 (flagged).
                    val genRes = generatorResults[sourceId]
                        ?: if (generatorResults.size == 1) generatorResults.values.first() else null
                    val genKVA = genRes?.finalSize ?: genRes?.designCapacity ?: 0.0
                    if (genRes == null || genKVA <= 0.0) {
                        results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Generator source not found or capacity data unavailable")
                        continue
                    }
                    val zgOhm = (generatorXdPercent / 100.0) * (un * un) / (genKVA * 1000.0)
                    val xgOhm = zgOhm / sqrt(1.0 + 0.15 * 0.15)
                    rTotalOhm = 0.15 * xgOhm
                    xTotalOhm = xgOhm
                    sourceWarnings.add("Generator subtransient reactance ASSUMED x″d = $generatorXdPercent% — verify nameplate (IEC 60909 §3.6)")
                    sourceWarnings.add("Generator correction factor K_G assumed 1.0 — verify terminal voltage correction (IEC 60909 §6.6)")
                }
                else -> {
                    val txResult = transformerResults[sourceId]
                    if (txResult == null) {
                        results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Transformer source not found")
                        continue
                    }
                    if (txResult.isc3Phase <= 0.0) {
                        results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Transformer Isc is zero or invalid")
                        continue
                    }
                    un = txResult.voltage
                    rTotalOhm = txResult.rt / 1000.0
                    xTotalOhm = txResult.xt / 1000.0
                }
            }

            // Walk the chain from this circuit up to transformer
            var step: CircuitEntity? = circuit
            while (step != null) {
                val stepSizing = sizingResults[step.id]
                if (stepSizing != null) {
                    val rSeg = stepSizing.rUsed * step.length_m / (1000.0 * step.parallelRuns)
                    val xSeg = stepSizing.xUsed * step.length_m / (1000.0 * step.parallelRuns)
                    rTotalOhm += rSeg
                    xTotalOhm += xSeg
                }
                val parentId = circuitParents[step.id]
                step = if (parentId != null) resolvedCircuits.find { it.id == parentId } else null
            }

            val zTotalOhm = sqrt(rTotalOhm.pow(2) + xTotalOhm.pow(2))
            val iscKA = if (zTotalOhm > 0.0) ((c * un) / (sqrt(3.0) * zTotalOhm)) / 1000.0 else 0.0

            if (iscKA <= 0.0 || iscKA.isNaN()) {
                results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Isc calculated as zero or NaN")
                continue
            }

            // IEC 60909 Peak Factor kappa calculation: kappa = 1.02 + 0.98 * e^(-3 * R / X)
            val kappa = if (xTotalOhm > 0.0) 1.02 + 0.98 * exp(-3.0 * (rTotalOhm / xTotalOhm)) else 1.0
            // IEC 60909-0:2016 Clause 6.10 — LV motor-group contribution (OPTIONAL, default OFF).
            // Z_M = (1/(I_LR/I_rM))·(U_rM/(√3·I_rM)); contribution I″kM = c·Un/(√3·Z_M)
            // superimposed on the network I″k. Gated behind a toggle because motor data is
            // usually assumed; active use is flagged in the result note.
            var iscTotalKA = iscKA
            if (motorContributionEnabled && motorGroupKVA > 0.0) {
                val iRM_A = motorGroupKVA * 1000.0 / (sqrt(3.0) * un)
                val zMOhm = (1.0 / motorLoadRatioILR_IR) * (un / (sqrt(3.0) * iRM_A))
                if (zMOhm > 0.0) {
                    val motorIscKA = (c * un / (sqrt(3.0) * zMOhm)) / 1000.0
                    iscTotalKA = iscKA + motorIscKA
                    sourceWarnings.add(
                        "Motor-group contribution ENABLED: +%.2f kA (motor group %.0f kVA, I_LR/I_rM = %.1f) — verify motor data (IEC 60909 §6.10)"
                            .format(motorIscKA, motorGroupKVA, motorLoadRatioILR_IR)
                    )
                }
            }

            val ipKA = sqrt(2.0) * kappa * iscTotalKA

            // Cable thermal withstand: Iw = (kFactor * S) / sqrt(clearingTime_s) / 1000
            val iwKA = (sizing.kFactor * cableMm2) / sqrt(sizing.clearingTime_s) / 1000.0

            val deviceCheckOk = sizing.deviceBreakingKA >= iscTotalKA
            val cableCheckOk = iwKA >= iscTotalKA
            
            val deviceCheckStr = if (deviceCheckOk) "PASS" else "FAIL"
            val cableCheckStr = if (cableCheckOk) "PASS" else "FAIL"

            val marginPct = ((iwKA / iscTotalKA) - 1.0) * 100.0
            
            val isSwitchingDevice = isSwitchingDevice(sizing.deviceType)
            // IEC 60947-2 peak making capacity check (CRITICAL-5): Icm >= ip.
            // Icu (RMS breaking) alone is insufficient — closing onto a fault stresses the device
            // with the peak (fully-asymmetric) current. Icm is taken from the CATALOG when
            // available; when absent it is ESTIMATED via the IEC 60947-2 §4.3.6.1 Table 2
            // tiered n-factor (Icm = n·Icu) and flagged. The ATS-engine convention
            // (required Icm = 2.2×Icw for design) is a separate design-target rule.
            val icmCatalog = sizing.deviceMakingKA
            val icmEstimated = icmCatalog <= 0.0
            val icmNFactor = if (icmEstimated) iec60947_2_icmNFactor(sizing.deviceBreakingKA) else 1.0
            val icmEffectiveKA = if (icmEstimated) sizing.deviceBreakingKA * icmNFactor else icmCatalog
            val makingPass = icmEffectiveKA >= ipKA

            var note = ""
            var rowStatus = if (deviceCheckOk && cableCheckOk) "PASS" else "FAIL"

            if (isSwitchingDevice) {
                note = "WARNING: switching device — verify upstream protection"
                rowStatus = if (cableCheckOk) "WARNING" else "FAIL"
            } else if (!sizing.isDeviceFromCatalog) {
                note = "Breaking capacity assumed (not from catalog) — verify"
            }

            // Source-type warnings (assumed x″d, K_G, motor contribution) appended first
            if (sourceWarnings.isNotEmpty()) {
                note = if (note.isBlank()) sourceWarnings.joinToString(" | ")
                       else sourceWarnings.joinToString(" | ") + " | " + note
            }
            if (!isSwitchingDevice && icmEstimated) {
                val estNote = ("Icm estimated per IEC 60947-2 Table 2 " +
                    "(n=%.2f, Icu %.1f kA -> %.1f kA) — verify nameplate")
                    .format(icmNFactor, sizing.deviceBreakingKA, icmEffectiveKA)
                note = if (note.isBlank()) estNote else note + " | " + estNote
            }
            if (!isSwitchingDevice && !makingPass) {
                val failNote = if (icmEstimated) {
                    "Making capacity FAIL: Icm estimated per IEC 60947-2 Table 2 (%.1f kA) < peak make current ip (%.1f kA) — verify device rating (IEC 60947-2)".format(icmEffectiveKA, ipKA)
                } else {
                    "Making capacity FAIL: Icm from catalog (%.1f kA) < peak make current ip (%.1f kA) — verify device rating (IEC 60947-2)".format(icmEffectiveKA, ipKA)
                }
                note = if (note.isBlank()) failNote else note + " | " + failNote
                rowStatus = if (makingCheckFails) "FAIL" else if (rowStatus == "PASS") "WARNING" else rowStatus
            }

            results[circuit.id] = ShortCircuitResult(
                circuitId = circuit.id,
                sourceName = sourceId,
                cableMm2 = rawCableStr,
                iscKA = round(iscTotalKA * 100) / 100.0,
                iwKA = round(iwKA * 100) / 100.0,
                deviceKA = sizing.deviceBreakingKA,
                deviceCheck = if (isSwitchingDevice) "N/A" else deviceCheckStr,
                cableCheck = cableCheckStr,
                marginPct = round(marginPct * 10) / 10.0,
                status = rowStatus,
                note = note,
                ipKA = round(ipKA * 100) / 100.0,
                zTotalMOhm = round(zTotalOhm * 1000.0 * 100) / 100.0,
                rMOhm = round(rTotalOhm * 1000.0 * 100) / 100.0,
                xMOhm = round(xTotalOhm * 1000.0 * 100) / 100.0
            )
        }
        return results
    }

    private fun isSwitchingDevice(type: String): Boolean {
        val upper = type.uppercase()
        return upper.contains("LBS") || 
               upper.contains("ISOLATOR") || 
               upper.contains("ATS") || 
               upper.contains("BUS COUPLER") || 
               upper.contains("CHANGE-OVER")
    }

    /**
     * IEC 60947-2 §4.3.6.1 Table 2 — ratio n of the rated making capacity Icm to the
     * ultimate short-circuit breaking capacity Icu, tiered by Icu:
     *   Icu ≤ 1.5 kA -> 1.41; ≤3 -> 1.42; ≤4.5 -> 1.47; ≤6 -> 1.53; ≤10 -> 1.70;
     *   ≤20 -> 2.00; ≤50 -> 2.10; >50 -> 2.20.
     * Used ONLY as the FALLBACK estimate when the device catalog omits Icm (catalog Icm
     * takes priority). A blanket 2.2 would over-estimate making capacity in the common
     * 20–50 kA band (should be 2.1), making the safety check optimistic.
     */
    private fun iec60947_2_icmNFactor(icuKA: Double): Double = when {
        icuKA <= 1.5 -> 1.41
        icuKA <= 3.0 -> 1.42
        icuKA <= 4.5 -> 1.47
        icuKA <= 6.0 -> 1.53
        icuKA <= 10.0 -> 1.70
        icuKA <= 20.0 -> 2.00
        icuKA <= 50.0 -> 2.10
        else -> 2.20
    }

    /** Generator LV terminal voltage used for islanded-source fault calculations (IEC 60909 §6.7). */
    private const val GENERATOR_TERMINAL_V = 400.0

    private fun buildMissingResult(circuit: CircuitEntity, status: String, note: String): ShortCircuitResult {
        return ShortCircuitResult(
            circuitId = circuit.id,
            sourceName = "N/A",
            cableMm2 = "-",
            iscKA = 0.0,
            iwKA = 0.0,
            deviceKA = 0.0,
            deviceCheck = "-",
            cableCheck = "-",
            marginPct = 0.0,
            status = status,
            note = note
        )
    }

    private fun parseNumericSize(value: String): Double? {
        val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""").find(value)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}
