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
    val mainBreakerRating_A: Double = 630.0,     // AUTO main breaker In
    val mainBreakerCurve: String = "C",          // main breaker trip curve B/C/D (IEC 60364-4-41 §411.4.4)
    val mainBreakerInstantMultiplier: Double = 0.0, // explicit Ii/Isd pick-up multiplier (×In); 0 = use curve default
    val earthFaultCurrent_kA: Double = 0.0,      // AUTO max prospective L-E earth-fault current Ik1 (0 = fall back to 3-phase Isc)
    val disconnectionTime_s: Double = 0.4,       // t
    val u0_V: Double = 230.0,                    // nominal phase-to-ground voltage
    val totalConnectedKVA: Double = 0.0          // total connected transformer load
)

data class ElectrodeCandidate(
    val id: String,
    val title: String,
    val rodLength_m: Double,
    val rodDiameter_mm: Double,
    val rodSpacing_m: Double,
    val rodCount: Int,
    val calculatedResistance_ohm: Double,
    val singleRodResistance_ohm: Double,
    val groupEfficiency: Double,
    val meetsDesignLimit: Boolean,
    val estimatedTotalRodLength_m: Double
)

data class EarthingAutoDesignResult(
    val effectiveTargetResistance_ohm: Double,
    val designLimitResistance_ohm: Double,
    val soilResistivitySource: String,
    val recommended: ElectrodeCandidate?,
    val alternatives: List<ElectrodeCandidate>,
    val feasible: Boolean,
    val warnings: List<String>
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
    val messages: List<String>,
    val autoDesignResult: EarthingAutoDesignResult? = null
)

object EarthingEngine {

    val STANDARD_EARTH_CONDUCTORS = listOf(16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0)

    fun calculate(input: EarthingInput, targetROverride: Double? = null): EarthingResult {
        val rho = input.soilResistivity_ohmM
        val faultKa = input.faultCurrent_kA
        val L = input.rodLength_m
        val d = input.rodDiameter_mm

        // Target Resistance R_target
        val targetR = targetROverride?.takeIf { it > 0.0 }
            ?: (if (input.projectType.equals("Villa", ignoreCase = true)) 5.0 else 2.0)

        // Guard against zero / negative or unconfigured inputs
        if (rho <= 0.0 || faultKa <= 0.0 || L <= 0.0 || d <= 0.0) {
            return EarthingResult(
                soilClass = if (rho > 0.0) classifySoil(rho) else "Unconfigured",
                singleRodResistance_ohm = 0.0,
                requiredRods = 0,
                earthPits = 0,
                achievedResistance_ohm = 0.0,
                targetResistance_ohm = targetR,
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

        // 3. Target Resistance R_target (R31) - determined at start of function

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

        // 7. Prospective Earth-Fault Current (R40, R41)
        // IEC 60364-5-54 §54.2.2: the protective conductor is sized on the MAXIMUM
        // prospective earth-fault current (the line-to-earth fault). Use Ik1 (2·Z1 + Z0)
        // when provided; otherwise fall back to the supplied 3-phase Isc (conservative).
        // No arbitrary reduction factors are applied.
        val designFaultKa = if (input.earthFaultCurrent_kA > 0.0) input.earthFaultCurrent_kA else faultKa
        val prospectiveFaultA = Math.round(faultKa * 1000.0 * 100.0) / 100.0
        val correctedFaultA = Math.round(designFaultKa * 1000.0 * 100.0) / 100.0

        // 8. Earth Conductor Thermal Sizing (R42, R43): S = (I_corr · √t) / k
        val k = if (input.kFactor > 0.0) input.kFactor else 159.0
        val exactConductorMm2 = (correctedFaultA * sqrt(input.disconnectionTime_s)) / k
        val exactConductorRounded = Math.round(exactConductorMm2 * 100.0) / 100.0

        val standardConductorMm2 = STANDARD_EARTH_CONDUCTORS.firstOrNull { it >= exactConductorRounded }
            ?: STANDARD_EARTH_CONDUCTORS.last()

        val equipotentialBondingMm2 = standardConductorMm2

        // 9. Earth Fault Loop Impedance Zs & Zs_max (R46, R47)
        // IEC 60364-4-41 §411.4.4: Zs_max = (Cmin · U0) / Ia, Cmin = 0.95, where Ia is the
        // current causing automatic disconnection within the stipulated time — the trip
        // MULTIPLIER (5×In B / 10×In C / 20×In D), NOT the breaker continuous rating.
        // Multiplier convention is the single source of truth in ProtectionEngine.
        val u0 = if (input.u0_V > 0.0) input.u0_V else 230.0
        val zs = if (prospectiveFaultA > 0.0) u0 / prospectiveFaultA else 0.0
        val zsRounded = Math.round(zs * 10000.0) / 10000.0

        val inMain = if (input.mainBreakerRating_A > 0.0) input.mainBreakerRating_A else 630.0
        val mainCurve = ProtectionEngine.parseTripCurve(input.mainBreakerCurve)
            ?: ProtectionEngine.TripCurve.C
        val ia = if (input.mainBreakerInstantMultiplier > 0.0) {
            inMain * input.mainBreakerInstantMultiplier
        } else {
            ProtectionEngine.disconnectionCurrent(inMain, mainCurve)
        }
        val zsMax = ProtectionEngine.maxZsAllowed(u0, ia)
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
            messages.add("Loop impedance Zs (${zsRounded} Ω) exceeds Zs_max (${zsMaxRounded} Ω) for main breaker Ia = ${ia}A (In = ${inMain}A, trip multiplier applied).")
        }
        if (input.mainBreakerInstantMultiplier <= 0.0) {
            messages.add(
                "Zs_max uses an assumed trip multiplier (${mainCurve.name}-curve, ${mainCurve.timesIn}×In) for the main breaker " +
                    "— verify the device instantaneous/short-time setting (IEC 60364-4-41 §411.4.4)."
            )
        }
        if (input.earthFaultCurrent_kA <= 0.0 && faultKa > 0.0) {
            messages.add(
                "Earth conductor sized on the 3-phase Isc (no line-to-earth Ik1 provided) — conservative per IEC 60364-5-54 §54.2.2."
            )
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

    fun recommendConfiguration(
        soilResistivity_ohmM: Double,
        soilResistivitySource: String = "ASSUMED",
        userTargetResistance_ohm: Double? = null,
        projectTypeDefaultTarget_ohm: Double = 2.0,
        designMarginPct: Double = 10.0,
        siteLength_m: Double? = null,
        siteWidth_m: Double? = null,
        ringConductor_mm2: Double = 25.0,
        ringBurialDepth_m: Double = 0.6,
        allowedRodLengths_m: List<Double> = listOf(1.2, 1.5, 2.0, 2.4, 3.0),
        allowedRodDiameters_mm: List<Double> = listOf(14.2, 16.0, 17.0, 20.0),
        maxRods: Int = 50
    ): EarthingAutoDesignResult {
        val warnings = mutableListOf<String>()

        // 1. Effective target resistance
        val effectiveTarget = userTargetResistance_ohm?.takeIf { it > 0.0 } ?: projectTypeDefaultTarget_ohm

        // 2. Design limit with margin guard [0, 50]
        val clampedMargin = designMarginPct.coerceIn(0.0, 50.0)
        if (designMarginPct != clampedMargin) {
            warnings.add("Design safety margin clamped to valid range [0%, 50%].")
        }
        val designLimit = Math.round(effectiveTarget * (1.0 - clampedMargin / 100.0) * 100.0) / 100.0

        // 3. Soil source disclaimer check
        if (soilResistivitySource.equals("ASSUMED", ignoreCase = true)) {
            warnings.add("Assumed soil data — verify with a Wenner 4-pin test before final construction.")
        }

        if (soilResistivity_ohmM <= 0.0) {
            warnings.add("Soil resistivity must be greater than zero.")
            return EarthingAutoDesignResult(
                effectiveTargetResistance_ohm = effectiveTarget,
                designLimitResistance_ohm = designLimit,
                soilResistivitySource = soilResistivitySource,
                recommended = null,
                alternatives = emptyList(),
                feasible = false,
                warnings = warnings
            )
        }

        // Ring conductor parallel resistance (using standard model)
        val ringConductorDiamM = sqrt(ringConductor_mm2 * 4.0 / PI) / 1000.0
        val ringPerimeterM = 20.0
        val ringR = if (ringBurialDepth_m > 0.0 && ringConductorDiamM > 0.0) {
            (soilResistivity_ohmM / (2.0 * PI * PI * ringPerimeterM)) * ln(2.0 * ringPerimeterM * ringPerimeterM / (ringBurialDepth_m * ringConductorDiamM))
        } else 50.0

        val candidates = mutableListOf<ElectrodeCandidate>()
        val maxSiteFootprint_m = if (siteLength_m != null && siteWidth_m != null && siteLength_m > 0.0 && siteWidth_m > 0.0) {
            max(siteLength_m, siteWidth_m) * 2.0
        } else null

        var siteExceededCount = 0

        for (L in allowedRodLengths_m) {
            if (L <= 0.0) continue
            for (d in allowedRodDiameters_mm) {
                if (d <= 0.0) continue
                val dMeters = d / 1000.0
                val r1 = (soilResistivity_ohmM / (2.0 * PI * L)) * (ln(8.0 * L / dMeters) - 1.0)
                if (r1 <= 0.0 || r1.isNaN() || r1.isInfinite()) continue

                val spacing = max(2.0 * L, 3.0)
                var nRods = 1
                while (nRods <= maxRods) {
                    val spacingRatio = spacing / L
                    val groupEfficiency = when {
                        spacingRatio >= 2.0 -> (1.0 - 0.05 * (nRods - 1)).coerceAtLeast(0.70)
                        spacingRatio >= 1.5 -> (0.95 - 0.06 * (nRods - 1)).coerceAtLeast(0.65)
                        else -> (0.90 - 0.07 * (nRods - 1)).coerceAtLeast(0.60)
                    }

                    val rRodsCombined = r1 / (nRods * groupEfficiency)
                    val totalParallelR = 1.0 / ((1.0 / rRodsCombined) + (1.0 / max(ringR, 1.0)))
                    val achievedR = Math.round(totalParallelR * 100.0) / 100.0

                    if (achievedR <= designLimit) {
                        val requiredArrayLength_m = (nRods - 1) * spacing
                        if (maxSiteFootprint_m != null && requiredArrayLength_m > maxSiteFootprint_m) {
                            siteExceededCount++
                        } else {
                            val candidateId = "opt_L${L}_d${d}_s${spacing}_n${nRods}"
                            candidates.add(
                                ElectrodeCandidate(
                                    id = candidateId,
                                    title = "${nRods}x ${L}m Rods (${d}mm, ${spacing}m spacing)",
                                    rodLength_m = L,
                                    rodDiameter_mm = d,
                                    rodSpacing_m = spacing,
                                    rodCount = nRods,
                                    calculatedResistance_ohm = achievedR,
                                    singleRodResistance_ohm = Math.round(r1 * 100.0) / 100.0,
                                    groupEfficiency = Math.round(groupEfficiency * 100.0) / 100.0,
                                    meetsDesignLimit = true,
                                    estimatedTotalRodLength_m = nRods * L
                                )
                            )
                        }
                        break
                    }
                    nRods++
                }
            }
        }

        if (siteExceededCount > 0) {
            warnings.add(
                if (candidates.isEmpty())
                    "Site dimensions (${siteLength_m}m x ${siteWidth_m}m) restrict required electrode array footprint — no feasible configuration fits."
                else
                    "Note: ${siteExceededCount} otherwise-viable configuration(s) were excluded because they exceed the available site dimensions (${siteLength_m}m x ${siteWidth_m}m)."
            )
        }

        val ranked = candidates.distinctBy { Pair(it.rodLength_m, Pair(it.rodDiameter_mm, it.rodCount)) }
            .sortedWith(
                compareBy<ElectrodeCandidate> { it.estimatedTotalRodLength_m }
                    .thenBy { it.rodCount }
                    .thenBy { it.calculatedResistance_ohm }
            )

        return if (ranked.isNotEmpty()) {
            val recommended = ranked.first()
            val alternatives = ranked.drop(1).take(2)
            EarthingAutoDesignResult(
                effectiveTargetResistance_ohm = effectiveTarget,
                designLimitResistance_ohm = designLimit,
                soilResistivitySource = soilResistivitySource,
                recommended = recommended,
                alternatives = alternatives,
                feasible = true,
                warnings = warnings
            )
        } else {
            warnings.add("No feasible configuration achieved target resistance of ${designLimit} Ω within ${maxRods} rods.")
            EarthingAutoDesignResult(
                effectiveTargetResistance_ohm = effectiveTarget,
                designLimitResistance_ohm = designLimit,
                soilResistivitySource = soilResistivitySource,
                recommended = null,
                alternatives = emptyList(),
                feasible = false,
                warnings = warnings
            )
        }
    }
}
