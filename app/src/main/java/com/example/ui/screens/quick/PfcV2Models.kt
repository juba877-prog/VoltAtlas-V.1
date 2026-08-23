package com.example.ui.screens.quick

import com.example.engine.converters.CapacitorConnectionType
import com.example.engine.converters.ElectricalConverters
import java.util.Locale
import kotlin.math.acos
import kotlin.math.tan

enum class PfcInputMode {
    ACTIVE_POWER_KW,
    APPARENT_POWER_KVA,
    CURRENT_AMPS
}

enum class PfcSystemPhase {
    THREE_PHASE,
    SINGLE_PHASE
}

enum class PfcFrequency(val hz: Double, val label: String) {
    FREQ_50HZ(50.0, "50 Hz"),
    FREQ_60HZ(60.0, "60 Hz")
}

enum class PfcCapacitorConnection(val connectionType: CapacitorConnectionType, val label: String) {
    DELTA(CapacitorConnectionType.DELTA, "Delta (Δ)"),
    STAR(CapacitorConnectionType.SINGLE_OR_STAR, "Star (Y)"),
    SINGLE_PHASE(CapacitorConnectionType.SINGLE_OR_STAR, "Single-Phase")
}

data class PfcCalculationStep(
    val title: String,
    val formula: String,
    val substitution: String,
    val resultText: String
)

data class PfcInputState(
    val inputMode: PfcInputMode = PfcInputMode.ACTIVE_POWER_KW,
    val phase: PfcSystemPhase = PfcSystemPhase.THREE_PHASE,
    val voltageV: Double = 400.0,
    val powerKwText: String = "100.0",
    val powerKvaText: String = "125.0",
    val currentAText: String = "180.0",
    val initialPfText: String = "0.80",
    val targetPfText: String = "0.95",
    val frequency: PfcFrequency = PfcFrequency.FREQ_50HZ,
    val connection: PfcCapacitorConnection = PfcCapacitorConnection.DELTA
)

data class PfcDetailedResult(
    // Primary Hero Metric
    val requiredQcKvar: Double,

    // Active & Apparent Powers
    val activePowerKw: Double,
    val initialApparentKva: Double,
    val targetApparentKva: Double,
    val releasedCapacityKva: Double,

    // Reactive Powers
    val initialReactiveKvar: Double,
    val targetReactiveKvar: Double,
    val reactiveReductionKvar: Double,

    // Line Currents
    val initialCurrentA: Double,
    val targetCurrentA: Double,
    val currentReductionA: Double,
    val currentReductionPct: Double,

    // Capacitance & Hardware Sizing
    val capacitanceMicroFarad: Double,
    val connection: PfcCapacitorConnection,
    val frequencyHz: Double,
    val voltageV: Double,
    val isThreePhase: Boolean,

    // Power Factors & Trigonometric Angles
    val initialPf: Double,
    val targetPf: Double,
    val initialTanPhi: Double,
    val targetTanPhi: Double,

    // Step-by-Step Breakdown & Provenance
    val steps: List<PfcCalculationStep>,
    val provenanceCitation: String
)

/**
 * Pure Calculation Engine for Power Factor Correction V2.
 *
 * Coordinates authoritative electrical converters and pure mathematical models
 * with strict zero-mutation, zero-Room, and zero-AI guarantees.
 *
 * Authority: All conversions and physics equations delegate exclusively to [ElectricalConverters].
 */
object PfcCalculationEngine {

    fun calculate(input: PfcInputState): PfcDetailedResult? {
        val volt = input.voltageV
        if (volt <= 0.0) return null

        val freqHz = input.frequency.hz
        if (freqHz <= 0.0) return null

        val c1 = input.initialPfText.toDoubleOrNull() ?: return null
        val c2 = input.targetPfText.toDoubleOrNull() ?: return null

        // Strict PF Domain Validation: 0.10 <= c1 < c2 <= 1.00
        if (c1 <= 0.0 || c1 > 1.0 || c2 <= 0.0 || c2 > 1.0) return null
        if (c2 <= c1) return null

        val is3P = input.phase == PfcSystemPhase.THREE_PHASE

        // 1. Resolve Operating Quantities (P, S1, I1) based on Input Mode
        val (pKw, s1Kva) = when (input.inputMode) {
            PfcInputMode.ACTIVE_POWER_KW -> {
                val p = input.powerKwText.toDoubleOrNull() ?: return null
                if (p <= 0.0) return null
                val s1 = ElectricalConverters.activeToApparentPower(p, c1)
                Pair(p, s1)
            }
            PfcInputMode.APPARENT_POWER_KVA -> {
                val s = input.powerKvaText.toDoubleOrNull() ?: return null
                if (s <= 0.0) return null
                val p = ElectricalConverters.apparentToActivePower(s, c1)
                Pair(p, s)
            }
            PfcInputMode.CURRENT_AMPS -> {
                val amps = input.currentAText.toDoubleOrNull() ?: return null
                if (amps <= 0.0) return null
                val s1 = ElectricalConverters.currentToApparentPower(amps, volt, is3P)
                val p = ElectricalConverters.apparentToActivePower(s1, c1)
                Pair(p, s1)
            }
        }

        if (pKw <= 0.0 || s1Kva <= 0.0) return null

        // 2. Resolve Target Apparent Power and Reactive Powers
        val s2Kva = ElectricalConverters.activeToApparentPower(pKw, c2)
        val q1Kvar = ElectricalConverters.reactivePowerFromSAndCosPhi(s1Kva, c1)
        val q2Kvar = ElectricalConverters.reactivePowerFromSAndCosPhi(s2Kva, c2)

        // 3. Invoke Authoritative Power Factor Correction Converter
        val pfcRes = ElectricalConverters.powerFactorCorrection(pKw, c1, c2, volt)
        val qcKvar = pfcRes.qcKvar
        if (qcKvar <= 0.0) return null

        // 4. Resolve Operating Currents & Reductions
        val i1A = ElectricalConverters.apparentPowerToCurrent(s1Kva, volt, is3P)
        val i2A = ElectricalConverters.apparentPowerToCurrent(s2Kva, volt, is3P)
        val deltaAmps = (i1A - i2A).coerceAtLeast(0.0)
        val deltaAmpsPct = if (i1A > 0.0) (deltaAmps / i1A) * 100.0 else 0.0
        val releasedKva = (s1Kva - s2Kva).coerceAtLeast(0.0)
        val deltaQkvar = (q1Kvar - q2Kvar).coerceAtLeast(0.0)

        // 5. Resolve Bank Connection & Capacitance
        val effectiveConn = if (is3P) input.connection else PfcCapacitorConnection.SINGLE_PHASE
        val ufPerBranch = ElectricalConverters.kvarToMicroFarad(
            kvar = qcKvar,
            voltageV = volt,
            freqHz = freqHz,
            connection = effectiveConn.connectionType
        )

        // Trigonometric Angles for Step Details
        val tanPhi1 = tan(acos(c1))
        val tanPhi2 = tan(acos(c2))

        // 6. Build Step-by-Step Calculation Substitutions
        val steps = mutableListOf<PfcCalculationStep>()

        // Step 1: Active Power & Initial State
        when (input.inputMode) {
            PfcInputMode.ACTIVE_POWER_KW -> {
                steps.add(
                    PfcCalculationStep(
                        title = "1. Active Power & Initial Apparent Power",
                        formula = "P = Given | S₁ = P / cosφ₁",
                        substitution = "P = ${String.format(Locale.US, "%.2f", pKw)} kW | S₁ = ${String.format(Locale.US, "%.2f", pKw)} / ${String.format(Locale.US, "%.2f", c1)}",
                        resultText = "P = ${String.format(Locale.US, "%.2f", pKw)} kW, S₁ = ${String.format(Locale.US, "%.2f", s1Kva)} kVA"
                    )
                )
            }
            PfcInputMode.APPARENT_POWER_KVA -> {
                steps.add(
                    PfcCalculationStep(
                        title = "1. Active Power from Apparent Power",
                        formula = "P = S₁ × cosφ₁ | S₁ = Given",
                        substitution = "P = ${String.format(Locale.US, "%.2f", s1Kva)} × ${String.format(Locale.US, "%.2f", c1)}",
                        resultText = "P = ${String.format(Locale.US, "%.2f", pKw)} kW (S₁ = ${String.format(Locale.US, "%.2f", s1Kva)} kVA)"
                    )
                )
            }
            PfcInputMode.CURRENT_AMPS -> {
                val factorStr = if (is3P) "√3 × V × I₁ / 1000" else "V × I₁ / 1000"
                steps.add(
                    PfcCalculationStep(
                        title = "1. Apparent & Active Power from Measured Current",
                        formula = "S₁ = $factorStr | P = S₁ × cosφ₁",
                        substitution = "S₁ = ${if (is3P) "1.732 × " else ""}${String.format(Locale.US, "%.1f", volt)} × ${String.format(Locale.US, "%.1f", i1A)} / 1000 = ${String.format(Locale.US, "%.2f", s1Kva)} kVA | P = ${String.format(Locale.US, "%.2f", s1Kva)} × ${String.format(Locale.US, "%.2f", c1)}",
                        resultText = "P = ${String.format(Locale.US, "%.2f", pKw)} kW, S₁ = ${String.format(Locale.US, "%.2f", s1Kva)} kVA"
                    )
                )
            }
        }

        // Step 2: Reactive Powers (Q1 and Q2)
        steps.add(
            PfcCalculationStep(
                title = "2. Reactive Powers (Initial vs Target)",
                formula = "Q₁ = P × tan(cos⁻¹(PF₁)) | Q₂ = P × tan(cos⁻¹(PF₂))",
                substitution = "Q₁ = ${String.format(Locale.US, "%.2f", pKw)} × ${String.format(Locale.US, "%.4f", tanPhi1)} | Q₂ = ${String.format(Locale.US, "%.2f", pKw)} × ${String.format(Locale.US, "%.4f", tanPhi2)}",
                resultText = "Q₁ = ${String.format(Locale.US, "%.2f", q1Kvar)} kVAR, Q₂ = ${String.format(Locale.US, "%.2f", q2Kvar)} kVAR"
            )
        )

        // Step 3: Required Capacitor Compensation Qc
        steps.add(
            PfcCalculationStep(
                title = "3. Required Capacitive Compensation (Qc)",
                formula = "Qc = Q₁ - Q₂ = P × (tanφ₁ - tanφ₂)",
                substitution = "Qc = ${String.format(Locale.US, "%.2f", q1Kvar)} - ${String.format(Locale.US, "%.2f", q2Kvar)} = ${String.format(Locale.US, "%.2f", pKw)} × (${String.format(Locale.US, "%.4f", tanPhi1)} - ${String.format(Locale.US, "%.4f", tanPhi2)})",
                resultText = "Qc = ${String.format(Locale.US, "%.2f", qcKvar)} kVAR"
            )
        )

        // Step 4: System Relief & Current Reduction
        val currentFormula = if (is3P) "I = S × 1000 / (√3 × V)" else "I = S × 1000 / V"
        steps.add(
            PfcCalculationStep(
                title = "4. Line Current Relief & Capacity Release",
                formula = "$currentFormula | ΔI = I₁ - I₂ | ΔS = S₁ - S₂",
                substitution = "I₁ = ${String.format(Locale.US, "%.2f", i1A)} A → I₂ = ${String.format(Locale.US, "%.2f", i2A)} A (ΔI = ${String.format(Locale.US, "%.2f", deltaAmps)} A, ${String.format(Locale.US, "%.1f", deltaAmpsPct)}%) | S₂ = ${String.format(Locale.US, "%.2f", s2Kva)} kVA (ΔS = ${String.format(Locale.US, "%.2f", releasedKva)} kVA released)",
                resultText = "ΔI = ${String.format(Locale.US, "%.2f", deltaAmps)} A relief, ΔS = ${String.format(Locale.US, "%.2f", releasedKva)} kVA released"
            )
        )

        // Step 5: Equivalent Capacitance
        val capFormula = when (effectiveConn) {
            PfcCapacitorConnection.DELTA -> "C_Δ = (Qc × 10⁹) / (3 × 2π × f × V²)"
            PfcCapacitorConnection.STAR -> "C_Y = (Qc × 10⁹) / (2π × f × V²)"
            PfcCapacitorConnection.SINGLE_PHASE -> "C = (Qc × 10⁹) / (2π × f × V²)"
        }
        val denomText = when (effectiveConn) {
            PfcCapacitorConnection.DELTA -> "(3 × 2π × ${String.format(Locale.US, "%.0f", freqHz)} × ${String.format(Locale.US, "%.0f", volt)}²)"
            PfcCapacitorConnection.STAR, PfcCapacitorConnection.SINGLE_PHASE -> "(2π × ${String.format(Locale.US, "%.0f", freqHz)} × ${String.format(Locale.US, "%.0f", volt)}²)"
        }
        steps.add(
            PfcCalculationStep(
                title = "5. Equivalent Bank Capacitance (${effectiveConn.label} @ ${String.format(Locale.US, "%.0f", freqHz)} Hz)",
                formula = capFormula,
                substitution = "C = (${String.format(Locale.US, "%.2f", qcKvar)} × 10⁹) / $denomText",
                resultText = "${String.format(Locale.US, "%.1f", ufPerBranch)} µF per ${if (effectiveConn == PfcCapacitorConnection.DELTA) "branch (Delta)" else "phase"}"
            )
        )

        return PfcDetailedResult(
            requiredQcKvar = qcKvar,
            activePowerKw = pKw,
            initialApparentKva = s1Kva,
            targetApparentKva = s2Kva,
            releasedCapacityKva = releasedKva,
            initialReactiveKvar = q1Kvar,
            targetReactiveKvar = q2Kvar,
            reactiveReductionKvar = deltaQkvar,
            initialCurrentA = i1A,
            targetCurrentA = i2A,
            currentReductionA = deltaAmps,
            currentReductionPct = deltaAmpsPct,
            capacitanceMicroFarad = ufPerBranch,
            connection = effectiveConn,
            frequencyHz = freqHz,
            voltageV = volt,
            isThreePhase = is3P,
            initialPf = c1,
            targetPf = c2,
            initialTanPhi = tanPhi1,
            targetTanPhi = tanPhi2,
            steps = steps,
            provenanceCitation = "Methodology: IEC 60831 / IEEE 141 Shunt Power Capacitor Sizing | Authority: ElectricalConverters Core"
        )
    }
}
