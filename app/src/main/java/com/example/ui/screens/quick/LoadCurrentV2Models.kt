package com.example.ui.screens.quick

import com.example.engine.converters.ElectricalConverters
import java.util.Locale

enum class LoadCurrentKnownQuantity {
    KVA_APPARENT,
    KW_ACTIVE,
    AMPS_CURRENT
}

enum class LoadCurrentSourceType {
    USER_INPUT,
    DERIVED,
    NOT_APPLICABLE
}

data class LoadCurrentInputState(
    val knownQuantity: LoadCurrentKnownQuantity = LoadCurrentKnownQuantity.KVA_APPARENT,
    val phase: VoltageDropSystemPhase = VoltageDropSystemPhase.THREE_PHASE,
    val voltageV: Double = 400.0,
    val sKvaText: String = "100.0",
    val pKwText: String = "80.0",
    val ampsText: String = "100.0",
    val powerFactorText: String = "0.85",
    val calculateActivePowerWithKva: Boolean = false,
    val calculateActivePowerWithAmps: Boolean = false
)

data class LoadCurrentDetailedResult(
    // Primary Hero Metric
    val primaryLabel: String,
    val primaryValueFormatted: String,
    val primaryUnit: String,

    // Core Quantities
    val amps: Double,
    val sKva: Double,
    val pKw: Double?,
    val qKvar: Double?,
    val powerFactor: Double?,

    // Source Semantics
    val sKvaSource: LoadCurrentSourceType,
    val pKwSource: LoadCurrentSourceType,
    val ampsSource: LoadCurrentSourceType,
    val pfSource: LoadCurrentSourceType,

    // Topology
    val isThreePhase: Boolean,
    val voltageV: Double,
    val isPowerFactorRequired: Boolean,
    val isPowerFactorSupplied: Boolean,

    // Mathematical Substitution & Provenance
    val formulaString: String,
    val substitutionString: String,
    val provenanceCitation: String
)

/**
 * Pure Calculation Engine for Load Current & Power Conversion V2.
 *
 * Direct, deterministic coordinator over authoritative ElectricalConverters functions.
 * Guarantees:
 * 1. kVA is a first-class engineering quantity.
 * 2. Power Factor is NEVER requested when converting S (kVA) <-> I (Amps).
 * 3. Exact formula and step-by-step mathematical substitution for all modes.
 * 4. Pure in-memory execution with zero Room / ViewModel / AI dependencies.
 */
object LoadCurrentCalculationEngine {

    fun calculate(input: LoadCurrentInputState): LoadCurrentDetailedResult? {
        val volt = input.voltageV
        if (volt <= 0.0) return null

        val is3P = input.phase == VoltageDropSystemPhase.THREE_PHASE
        val factor = if (is3P) kotlin.math.sqrt(3.0) else 1.0
        val factorSymbol = if (is3P) "√3" else "1.0"
        val factorValStr = if (is3P) "1.732" else "1.000"

        return when (input.knownQuantity) {
            // =========================================================================
            // MODE A: KNOWN kVA (APPARENT POWER)
            // =========================================================================
            LoadCurrentKnownQuantity.KVA_APPARENT -> {
                val s = input.sKvaText.toDoubleOrNull() ?: return null
                if (s <= 0.0) return null

                // 1. Direct kVA -> Current (NO PF REQUIRED)
                val amps = ElectricalConverters.apparentPowerToCurrent(s, volt, is3P)

                if (input.calculateActivePowerWithKva) {
                    // With optional active power calculation
                    val pf = input.powerFactorText.toDoubleOrNull() ?: return null
                    if (pf <= 0.0 || pf > 1.0) return null

                    val p = ElectricalConverters.apparentToActivePower(s, pf)
                    val q = ElectricalConverters.reactivePowerFromSAndCosPhi(s, pf)

                    val formula = if (is3P) {
                        "I = (S × 1000) / (√3 × V)  |  P = S × cosφ  |  Q = S × sinφ"
                    } else {
                        "I = (S × 1000) / V  |  P = S × cosφ  |  Q = S × sinφ"
                    }

                    val substitution = if (is3P) {
                        "I = ($s × 1000) / (1.732 × $volt) = ${String.format(Locale.US, "%.2f", amps)} A\n" +
                        "P = $s × $pf = ${String.format(Locale.US, "%.2f", p)} kW\n" +
                        "Q = $s × ${String.format(Locale.US, "%.3f", kotlin.math.sqrt((1.0 - pf * pf).coerceAtLeast(0.0)))} = ${String.format(Locale.US, "%.2f", q)} kVAR"
                    } else {
                        "I = ($s × 1000) / $volt = ${String.format(Locale.US, "%.2f", amps)} A\n" +
                        "P = $s × $pf = ${String.format(Locale.US, "%.2f", p)} kW\n" +
                        "Q = $s × ${String.format(Locale.US, "%.3f", kotlin.math.sqrt((1.0 - pf * pf).coerceAtLeast(0.0)))} = ${String.format(Locale.US, "%.2f", q)} kVAR"
                    }

                    LoadCurrentDetailedResult(
                        primaryLabel = "Operating Line Current (I)",
                        primaryValueFormatted = String.format(Locale.US, "%.2f", amps),
                        primaryUnit = "A",
                        amps = amps,
                        sKva = s,
                        pKw = p,
                        qKvar = q,
                        powerFactor = pf,
                        sKvaSource = LoadCurrentSourceType.USER_INPUT,
                        pKwSource = LoadCurrentSourceType.DERIVED,
                        ampsSource = LoadCurrentSourceType.DERIVED,
                        pfSource = LoadCurrentSourceType.USER_INPUT,
                        isThreePhase = is3P,
                        voltageV = volt,
                        isPowerFactorRequired = true,
                        isPowerFactorSupplied = true,
                        formulaString = formula,
                        substitutionString = substitution,
                        provenanceCitation = "AC Power Relationship Method (Apparent Load to Line Current with Active Power Resolution)"
                    )
                } else {
                    // Direct kVA -> A (PF omitted entirely)
                    val formula = if (is3P) "I = (S × 1000) / (√3 × V)" else "I = (S × 1000) / V"
                    val substitution = if (is3P) {
                        "I = ($s × 1000) / (1.732 × $volt) = ${String.format(Locale.US, "%.2f", amps)} A"
                    } else {
                        "I = ($s × 1000) / $volt = ${String.format(Locale.US, "%.2f", amps)} A"
                    }

                    LoadCurrentDetailedResult(
                        primaryLabel = "Operating Line Current (I)",
                        primaryValueFormatted = String.format(Locale.US, "%.2f", amps),
                        primaryUnit = "A",
                        amps = amps,
                        sKva = s,
                        pKw = null,
                        qKvar = null,
                        powerFactor = null,
                        sKvaSource = LoadCurrentSourceType.USER_INPUT,
                        pKwSource = LoadCurrentSourceType.NOT_APPLICABLE,
                        ampsSource = LoadCurrentSourceType.DERIVED,
                        pfSource = LoadCurrentSourceType.NOT_APPLICABLE,
                        isThreePhase = is3P,
                        voltageV = volt,
                        isPowerFactorRequired = false,
                        isPowerFactorSupplied = false,
                        formulaString = formula,
                        substitutionString = substitution,
                        provenanceCitation = "AC Power Relationship Method (Direct Apparent Power S to Line Current I)"
                    )
                }
            }

            // =========================================================================
            // MODE B: KNOWN ACTIVE POWER (kW)
            // =========================================================================
            LoadCurrentKnownQuantity.KW_ACTIVE -> {
                val p = input.pKwText.toDoubleOrNull() ?: return null
                if (p <= 0.0) return null

                val pf = input.powerFactorText.toDoubleOrNull() ?: return null
                if (pf <= 0.0 || pf > 1.0) return null

                // Active power requires PF to resolve Amps and kVA
                val s = ElectricalConverters.activeToApparentPower(p, pf)
                val currentRes = ElectricalConverters.powerToCurrent(p, volt, pf, is3P)
                val amps = currentRes.amps
                val q = ElectricalConverters.reactivePowerFromSAndCosPhi(s, pf)

                val formula = if (is3P) {
                    "I = (P × 1000) / (√3 × V × cosφ)  |  S = P / cosφ"
                } else {
                    "I = (P × 1000) / (V × cosφ)  |  S = P / cosφ"
                }

                val substitution = if (is3P) {
                    "I = ($p × 1000) / (1.732 × $volt × $pf) = ${String.format(Locale.US, "%.2f", amps)} A\n" +
                    "S = $p / $pf = ${String.format(Locale.US, "%.2f", s)} kVA\n" +
                    "Q = ${String.format(Locale.US, "%.2f", s)} × ${String.format(Locale.US, "%.3f", kotlin.math.sqrt((1.0 - pf * pf).coerceAtLeast(0.0)))} = ${String.format(Locale.US, "%.2f", q)} kVAR"
                } else {
                    "I = ($p × 1000) / ($volt × $pf) = ${String.format(Locale.US, "%.2f", amps)} A\n" +
                    "S = $p / $pf = ${String.format(Locale.US, "%.2f", s)} kVA\n" +
                    "Q = ${String.format(Locale.US, "%.2f", s)} × ${String.format(Locale.US, "%.3f", kotlin.math.sqrt((1.0 - pf * pf).coerceAtLeast(0.0)))} = ${String.format(Locale.US, "%.2f", q)} kVAR"
                }

                LoadCurrentDetailedResult(
                    primaryLabel = "Operating Line Current (I)",
                    primaryValueFormatted = String.format(Locale.US, "%.2f", amps),
                    primaryUnit = "A",
                    amps = amps,
                    sKva = s,
                    pKw = p,
                    qKvar = q,
                    powerFactor = pf,
                    sKvaSource = LoadCurrentSourceType.DERIVED,
                    pKwSource = LoadCurrentSourceType.USER_INPUT,
                    ampsSource = LoadCurrentSourceType.DERIVED,
                    pfSource = LoadCurrentSourceType.USER_INPUT,
                    isThreePhase = is3P,
                    voltageV = volt,
                    isPowerFactorRequired = true,
                    isPowerFactorSupplied = true,
                    formulaString = formula,
                    substitutionString = substitution,
                    provenanceCitation = "AC Power Relationship Method (Active Power P and Displacement Factor to Current & Apparent Power)"
                )
            }

            // =========================================================================
            // MODE C: KNOWN LINE CURRENT (AMPS)
            // =========================================================================
            LoadCurrentKnownQuantity.AMPS_CURRENT -> {
                val amps = input.ampsText.toDoubleOrNull() ?: return null
                if (amps <= 0.0) return null

                // 1. Direct Current -> kVA (NO PF REQUIRED)
                val s = ElectricalConverters.currentToApparentPower(amps, volt, is3P)

                if (input.calculateActivePowerWithAmps) {
                    val pf = input.powerFactorText.toDoubleOrNull() ?: return null
                    if (pf <= 0.0 || pf > 1.0) return null

                    val p = ElectricalConverters.apparentToActivePower(s, pf)
                    val q = ElectricalConverters.reactivePowerFromSAndCosPhi(s, pf)

                    val formula = if (is3P) {
                        "S = (√3 × V × I) / 1000  |  P = S × cosφ  |  Q = S × sinφ"
                    } else {
                        "S = (V × I) / 1000  |  P = S × cosφ  |  Q = S × sinφ"
                    }

                    val substitution = if (is3P) {
                        "S = (1.732 × $volt × $amps) / 1000 = ${String.format(Locale.US, "%.2f", s)} kVA\n" +
                        "P = ${String.format(Locale.US, "%.2f", s)} × $pf = ${String.format(Locale.US, "%.2f", p)} kW\n" +
                        "Q = ${String.format(Locale.US, "%.2f", s)} × ${String.format(Locale.US, "%.3f", kotlin.math.sqrt((1.0 - pf * pf).coerceAtLeast(0.0)))} = ${String.format(Locale.US, "%.2f", q)} kVAR"
                    } else {
                        "S = ($volt × $amps) / 1000 = ${String.format(Locale.US, "%.2f", s)} kVA\n" +
                        "P = ${String.format(Locale.US, "%.2f", s)} × $pf = ${String.format(Locale.US, "%.2f", p)} kW\n" +
                        "Q = ${String.format(Locale.US, "%.2f", s)} × ${String.format(Locale.US, "%.3f", kotlin.math.sqrt((1.0 - pf * pf).coerceAtLeast(0.0)))} = ${String.format(Locale.US, "%.2f", q)} kVAR"
                    }

                    LoadCurrentDetailedResult(
                        primaryLabel = "Apparent Power (S)",
                        primaryValueFormatted = String.format(Locale.US, "%.2f", s),
                        primaryUnit = "kVA",
                        amps = amps,
                        sKva = s,
                        pKw = p,
                        qKvar = q,
                        powerFactor = pf,
                        sKvaSource = LoadCurrentSourceType.DERIVED,
                        pKwSource = LoadCurrentSourceType.DERIVED,
                        ampsSource = LoadCurrentSourceType.USER_INPUT,
                        pfSource = LoadCurrentSourceType.USER_INPUT,
                        isThreePhase = is3P,
                        voltageV = volt,
                        isPowerFactorRequired = true,
                        isPowerFactorSupplied = true,
                        formulaString = formula,
                        substitutionString = substitution,
                        provenanceCitation = "AC Power Relationship Method (Line Current I to Apparent & Active Power)"
                    )
                } else {
                    // Direct Amps -> kVA (PF omitted entirely)
                    val formula = if (is3P) "S = (√3 × V × I) / 1000" else "S = (V × I) / 1000"
                    val substitution = if (is3P) {
                        "S = (1.732 × $volt × $amps) / 1000 = ${String.format(Locale.US, "%.2f", s)} kVA"
                    } else {
                        "S = ($volt × $amps) / 1000 = ${String.format(Locale.US, "%.2f", s)} kVA"
                    }

                    LoadCurrentDetailedResult(
                        primaryLabel = "Apparent Power (S)",
                        primaryValueFormatted = String.format(Locale.US, "%.2f", s),
                        primaryUnit = "kVA",
                        amps = amps,
                        sKva = s,
                        pKw = null,
                        qKvar = null,
                        powerFactor = null,
                        sKvaSource = LoadCurrentSourceType.DERIVED,
                        pKwSource = LoadCurrentSourceType.NOT_APPLICABLE,
                        ampsSource = LoadCurrentSourceType.USER_INPUT,
                        pfSource = LoadCurrentSourceType.NOT_APPLICABLE,
                        isThreePhase = is3P,
                        voltageV = volt,
                        isPowerFactorRequired = false,
                        isPowerFactorSupplied = false,
                        formulaString = formula,
                        substitutionString = substitution,
                        provenanceCitation = "AC Power Relationship Method (Direct Line Current I to Apparent Power S)"
                    )
                }
            }
        }
    }
}
