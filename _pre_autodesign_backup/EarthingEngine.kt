package com.example.engine

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

data class EarthingInput(
    val soilResistivity_ohmM: Double = 50.0,      // ρ
    val rodLength_m: Double = 3.0,               // L
    val rodDiameter_mm: Double = 17.0,           // d
    val rodSpacing_m: Double = 5.0,              // s
    val ringConductor_mm2: Double = 25.0,         // S_ring
    val ringBurialDepth_m: Double = 0.6,         // h
    val kFactor: Double = 159.0,                 // material k factor (default 159 copper)
    val projectType: String = "Commercial",       // "Villa" | "Commercial" | ...
    val faultCurrent_kA: Double = 0.0,           // AUTO from ShortCircuit/Transformer (Isc)
    val systemVoltage_V: Double = 400.0,         // AUTO
    val mainBreakerRating_A: Double = 630.0,     // AUTO main breaker Ia
    val disconnectionTime_s: Double = 0.4,       // t
    val u0_V: Double = 230.0,                    // nominal phase-to-ground voltage
    val totalConnectedKVA: Double = 0.0          // total connected transformer load
)

data class EarthingResult(
    val soilClass: String,
    val singleRodResistance_ohm: Double,
    val requiredRods: Int,
    val earthPits: Int,
    val achievedResistance_ohm: Double,
    val targetResistance_ohm: Double,
    val fullLoadCurrent_A: Double,
    val prospectiveFault_A: Double,
    val correctedFault_A: Double,
    val earthConductorExact_mm2: Double,
    val earthConductorStandard_mm2: Double,
    val equipotentialBonding_mm2: Double,
    val zs_ohm: Double,
    val zsMax_ohm: Double,
    val loopCheck: String,
    val overallCompliant: Boolean,
    val status: String,
    val messages: List<String>
)

object EarthingEngine {

    val STANDARD_EARTH_CONDUCTORS = listOf(16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0)

    fun calculate(input: EarthingInput): EarthingResult {
        val rho = input.soilResistivity_ohmM
        val faultKa = input.faultCurrent_kA
        val L = input.rodLength_m
        val d = input.rodDiameter_mm

        // Guard against zero / negative or unconfigured inputs
        if (rho <= 0.0 || faultKa <= 0.0 || L <= 0.0 || d <= 0.0) {
            return EarthingResult(
                soilClass = if (rho > 0.0) classifySoil(rho) else "Unconfigured",
                singleRodResistance_ohm = 0.0,
                requiredRods = 0,
                earthPits = 0,
                achievedResistance_ohm = 0.0,
                targetResistance_ohm = if (input.projectType.equals("Villa", ignoreCase = true)) 5.0 else 2.0,
                fullLoadCurrent_A = 0.0,
                prospectiveFault_A = 0.0,
                correctedFault_A = 0.0,
                earthConductorExact_mm2 = 0.0,
                earthConductorStandard_mm2 = 0.0,
                equipotentialBonding_mm2 = 0.0,
                zs_ohm = 0.0,
                zsMax_ohm = 0.0,
                loopCheck = "N/A",
                overallCompliant = false,
                status = "SELECT_LOADS",
                messages = listOf("Select transformer/circuits to resolve prospective fault current and soil resistivity.")
            )
        }

        // 1. Soil Classification (R8)
        val soilClass = classifySoil(rho)

        // 2. Single Rod Resistance R1 (R32): R1 = (ρ / (2·π·L)) · (ln(8·L / (d/1000)) − 1)
        val dMeters = d / 1000.0
        val r1 = (rho / (2.0 * PI * L)) * (ln(8.0 * L / dMeters) - 1.0)
        val singleRodR = Math.round(r1 * 100.0) / 100.0

        // 3. Target Resistance R_target (R31)
        val targetR = if (input.projectType.equals("Villa", ignoreCase = true)) 5.0 else 2.0

        // 4. Ring Conductor Resistance (BS 7430 R22 model)
        // Ring perimeter ~ 20m, conductor diameter equivalent, burial depth h
        val ringConductorDiamM = sqrt(input.ringConductor_mm2 * 4.0 / PI) / 1000.0
        val ringPerimeterM = 20.0
        val ringR = if (input.ringBurialDepth_m > 0.0 && ringConductorDiamM > 0.0) {
            (rho / (2.0 * PI * PI * ringPerimeterM)) * ln(2.0 * ringPerimeterM * ringPerimeterM / (input.ringBurialDepth_m * ringConductorDiamM))
        } else 50.0

        // 5. Parallel Rod Sizing (R37 & L5 grouping efficiency)
        var nRods = 1
        var achievedR = 0.0
        while (nRods <= 50) {
            // Grouping efficiency factor η based on rod count & spacing ratio (s/L)
            val spacingRatio = input.rodSpacing_m / L
            val groupEfficiency = when {
                spacingRatio >= 2.0 -> (1.0 - 0.05 * (nRods - 1)).coerceAtLeast(0.70)
                spacingRatio >= 1.5 -> (0.95 - 0.06 * (nRods - 1)).coerceAtLeast(0.65)
                else -> (0.90 - 0.07 * (nRods - 1)).coerceAtLeast(0.60)
            }

            val rRodsCombined = r1 / (nRods * groupEfficiency)
            val totalParallelR = 1.0 / ((1.0 / rRodsCombined) + (1.0 / max(ringR, 1.0)))

            achievedR = Math.round(totalParallelR * 100.0) / 100.0
            if (achievedR <= targetR || nRods == 50) {
                break
            }
            nRods++
        }
        val earthPits = nRods

        // 6. Full Load Current (R39)
        val flc = if (input.totalConnectedKVA > 0.0 && input.systemVoltage_V > 0.0) {
            Math.round((input.totalConnectedKVA * 1000.0 / (sqrt(3.0) * input.systemVoltage_V)) * 100.0) / 100.0
        } else 0.0

        // 7. Prospective & Corrected Fault Current (R40, R41)
        val prospectiveFaultA = Math.round(faultKa * 1000.0 * 100.0) / 100.0
        val correctedFaultA = Math.round(prospectiveFaultA * 0.8 * 100.0) / 100.0

        // 8. Earth Conductor Thermal Sizing (R42, R43): S = (I_corr · √t) / k
        val k = if (input.kFactor > 0.0) input.kFactor else 159.0
        val exactConductorMm2 = (correctedFaultA * sqrt(input.disconnectionTime_s)) / k
        val exactConductorRounded = Math.round(exactConductorMm2 * 100.0) / 100.0

        val standardConductorMm2 = STANDARD_EARTH_CONDUCTORS.firstOrNull { it >= exactConductorRounded }
            ?: STANDARD_EARTH_CONDUCTORS.last()

        val equipotentialBondingMm2 = standardConductorMm2

        // 9. Earth Fault Loop Impedance Zs & Zs_max (R46, R47)
        val u0 = if (input.u0_V > 0.0) input.u0_V else 230.0
        val zs = if (prospectiveFaultA > 0.0) u0 / prospectiveFaultA else 0.0
        val zsRounded = Math.round(zs * 10000.0) / 10000.0

        val ia = if (input.mainBreakerRating_A > 0.0) input.mainBreakerRating_A else 630.0
        val zsMax = u0 / ia
        val zsMaxRounded = Math.round(zsMax * 10000.0) / 10000.0

        val loopPass = zsRounded <= zsMaxRounded
        val loopCheckStatus = if (loopPass) "PASS" else "FAIL"

        // 10. Compliance & Status
        val resistanceCompliant = achievedR <= targetR
        val overallCompliant = resistanceCompliant && loopPass

        val messages = mutableListOf<String>()
        if (!resistanceCompliant) {
            messages.add("Achieved resistance (${achievedR} Ω) exceeds target (${targetR} Ω). Increase rod count or depth.")
        }
        if (!loopPass) {
            messages.add("Loop impedance Zs (${zsRounded} Ω) exceeds Zs_max (${zsMaxRounded} Ω) for main breaker ${ia}A.")
        }
        if (overallCompliant) {
            messages.add("Earthing system fully compliant with IEC 60364-5-54 / BS 7430.")
        }

        val statusStr = if (overallCompliant) "✅ COMPLIANT" else "❌ REVIEW REQUIRED"

        return EarthingResult(
            soilClass = soilClass,
            singleRodResistance_ohm = singleRodR,
            requiredRods = nRods,
            earthPits = earthPits,
            achievedResistance_ohm = achievedR,
            targetResistance_ohm = targetR,
            fullLoadCurrent_A = flc,
            prospectiveFault_A = prospectiveFaultA,
            correctedFault_A = correctedFaultA,
            earthConductorExact_mm2 = exactConductorRounded,
            earthConductorStandard_mm2 = standardConductorMm2,
            equipotentialBonding_mm2 = equipotentialBondingMm2,
            zs_ohm = zsRounded,
            zsMax_ohm = zsMaxRounded,
            loopCheck = loopCheckStatus,
            overallCompliant = overallCompliant,
            status = statusStr,
            messages = messages
        )
    }

    private fun classifySoil(rho: Double): String {
        return when {
            rho <= 50.0 -> "Low Resistivity"
            rho <= 100.0 -> "Normal"
            rho <= 300.0 -> "Moderate"
            rho <= 1000.0 -> "High"
            else -> "Very High"
        }
    }
}
