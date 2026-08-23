package com.example.engine.converters

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class CapacitorConnectionType {
    DELTA,
    STAR,
    SINGLE_OR_STAR
}

data class CableConversionResult(
    val trueAreaMm2: Double,
    val nearestNominalMm2: Double,
    val safeUpNominalMm2: Double
)

data class PowerTriangleResult(
    val pKw: Double,
    val qKvar: Double,
    val sKva: Double,
    val cosPhi: Double,
    val sinPhi: Double
)

data class CurrentPowerResult(
    val pKw: Double,
    val sKva: Double,
    val amps: Double
)

data class PfcResult(
    val qcKvar: Double,
    val deltaAmps: Double
)

data class VdropResult(
    val mVperAm: Double,
    val vDrop: Double,
    val vDropPct: Double
)

data class BatteryResult(
    val eKwh: Double,
    val ahRequired: Double
)

data class TxShortCircuitResult(
    val iFlcAmps: Double,
    val iScKa: Double
)

/**
 * Pure Kotlin Calculation Engine for Electrical & Cable Conversions.
 * Implements exact formulas according to IEC 60364, IEC 60076, and BS 7671 standards.
 */
object ElectricalConverters {

    // Complete IEC 60228 nominal sizes list up to 1000 mm²
    val IEC_NOMINAL_SIZES = listOf(
        0.5, 0.75, 1.0, 1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0,
        120.0, 150.0, 185.0, 240.0, 300.0, 400.0, 500.0, 630.0, 800.0, 1000.0
    )

    fun getNearestNominal(area: Double): Double {
        if (area <= 0) return IEC_NOMINAL_SIZES.first()
        return IEC_NOMINAL_SIZES.minByOrNull { abs(it - area) } ?: IEC_NOMINAL_SIZES.last()
    }

    fun getSafeUpNominal(area: Double): Double {
        if (area <= 0) return IEC_NOMINAL_SIZES.first()
        return IEC_NOMINAL_SIZES.firstOrNull { it >= area } ?: IEC_NOMINAL_SIZES.last()
    }

    // AWG to Gauge Number helper: "4/0" -> -3, "3/0" -> -2, "2/0" -> -1, "1/0" -> 0, "1" -> 1, ...
    fun parseAwgGauge(awgStr: String): Double? {
        val s = awgStr.trim().uppercase()
        return when (s) {
            "4/0" -> -3.0
            "3/0" -> -2.0
            "2/0" -> -1.0
            "1/0" -> 0.0
            else -> s.replace("AWG", "").trim().toDoubleOrNull()
        }
    }

    // AWG Diameter (mm): d_n = 0.127 * 92^((36 - n)/39)
    fun awgToMm2(gauge: Double): CableConversionResult {
        val diameter = 0.127 * 92.0.pow((36.0 - gauge) / 39.0)
        val trueArea = Math.PI / 4.0 * diameter * diameter
        return CableConversionResult(
            trueAreaMm2 = trueArea,
            nearestNominalMm2 = getNearestNominal(trueArea),
            safeUpNominalMm2 = getSafeUpNominal(trueArea)
        )
    }

    // mm2ToAwg(areaMm2: Double): String
    // Formula: n = 36 - 39 * (ln(diameter / 0.127) / ln(92))
    fun mm2ToAwg(areaMm2: Double): String {
        if (areaMm2 <= 0) return "Invalid"
        val diameter = sqrt(4.0 * areaMm2 / Math.PI)
        val n = 36.0 - 39.0 * (ln(diameter / 0.127) / ln(92.0))
        val roundedGauge = Math.round(n).toInt()

        return when {
            roundedGauge <= -4 -> "> 4/0 AWG (${String.format(java.util.Locale.US, "%.1f", areaMm2)} mm²)"
            roundedGauge == -3 -> "4/0 AWG"
            roundedGauge == -2 -> "3/0 AWG"
            roundedGauge == -1 -> "2/0 AWG"
            roundedGauge == 0 -> "1/0 AWG"
            else -> "$roundedGauge AWG"
        }
    }

    // SWG from diameter (mm)
    fun swgToMm2(diameterMm: Double): CableConversionResult {
        if (diameterMm <= 0) return CableConversionResult(0.0, 0.5, 0.5)
        val trueArea = Math.PI / 4.0 * diameterMm * diameterMm
        return CableConversionResult(
            trueAreaMm2 = trueArea,
            nearestNominalMm2 = getNearestNominal(trueArea),
            safeUpNominalMm2 = getSafeUpNominal(trueArea)
        )
    }

    // kcmil to mm2: 1 kcmil = 0.506707479 mm2
    fun kcmilToMm2(kcmil: Double): CableConversionResult {
        if (kcmil <= 0) return CableConversionResult(0.0, 0.5, 0.5)
        val trueArea = kcmil * 0.506707479
        return CableConversionResult(
            trueAreaMm2 = trueArea,
            nearestNominalMm2 = getNearestNominal(trueArea),
            safeUpNominalMm2 = getSafeUpNominal(trueArea)
        )
    }

    // HP <-> kW
    fun hpToKw(hp: Double, isMetric: Boolean = false): Double {
        val factor = if (isMetric) 0.73549875 else 0.745699872
        return hp * factor
    }

    fun kwToHp(kw: Double, isMetric: Boolean = false): Double {
        val factor = if (isMetric) 0.73549875 else 0.745699872
        return kw / factor
    }

    // Frequency <-> RPM: N = 120 * f / poles
    fun freqToRpm(freqHz: Double, poles: Int): Double {
        if (poles <= 0) return 0.0
        return (120.0 * freqHz) / poles
    }

    fun rpmToFreq(rpm: Double, poles: Int): Double {
        return (rpm * poles) / 120.0
    }

    // Torque T = 9549.29658 * P(kW) / N(RPM)
    fun torqueFromKwRpm(kw: Double, rpm: Double): Double {
        if (rpm <= 0) return 0.0
        return (9549.29658 * kw) / rpm
    }

    fun kwFromTorqueRpm(torqueNm: Double, rpm: Double): Double {
        return (torqueNm * rpm) / 9549.29658
    }

    // =========================================================================
    // CAPACITOR BANK CONVERSIONS (Total Bank kVAR <-> Per-Branch/Phase µF)
    // =========================================================================

    /**
     * Converts capacitance (µF per branch or per phase) to total capacitor bank reactive power (kVAR).
     *
     * Physical Derivation:
     * - Single branch reactive power: Q_branch = 2π * f * C * V_branch² * 1e-9 (kVAR)
     * - 3-Phase Star (Y) or 1-Phase: V_branch = V_LL / √3 -> Q_total = 3 * Q_branch = 2π * f * C_uF * V_LL² * 1e-9 (kVAR)
     * - 3-Phase Delta (Δ):          V_branch = V_LL      -> Q_total = 3 * Q_branch = 3 * 2π * f * C_uF * V_LL² * 1e-9 (kVAR)
     *
     * @param uf Capacitance in µF per branch (Delta) or per phase (Star/1-Phase)
     * @param voltageV Line-to-line voltage for 3-Phase, or system voltage for 1-Phase
     * @param freqHz Power frequency in Hz (default: 50.0)
     * @param connection Capacitor bank connection topology (DELTA or STAR/SINGLE_OR_STAR)
     * @return Total capacitor bank reactive power in kVAR
     */
    fun microFaradToKvar(
        uf: Double,
        voltageV: Double,
        freqHz: Double = 50.0,
        connection: CapacitorConnectionType = CapacitorConnectionType.DELTA
    ): Double {
        if (voltageV <= 0.0 || freqHz <= 0.0 || uf <= 0.0) return 0.0
        val baseStarKvar = 2.0 * Math.PI * freqHz * uf * voltageV * voltageV * 1e-9
        return if (connection == CapacitorConnectionType.DELTA) baseStarKvar * 3.0 else baseStarKvar
    }

    /**
     * Converts total capacitor bank reactive power (kVAR) to capacitance (µF per branch or per phase).
     *
     * Physical Derivation:
     * - 3-Phase Star (Y) or 1-Phase: C_uF (per phase)  = (Q_total * 1e9) / (2π * f * V_LL²)
     * - 3-Phase Delta (Δ):          C_uF (per branch) = (Q_total * 1e9) / (3 * 2π * f * V_LL²) = C_Star / 3
     *
     * @param kvar Total reactive power of the capacitor bank in kVAR
     * @param voltageV Line-to-line voltage for 3-Phase, or system voltage for 1-Phase
     * @param freqHz Power frequency in Hz (default: 50.0)
     * @param connection Capacitor bank connection topology (DELTA or STAR/SINGLE_OR_STAR)
     * @return Capacitance in µF per branch (Delta) or per phase (Star/1-Phase)
     */
    fun kvarToMicroFarad(
        kvar: Double,
        voltageV: Double,
        freqHz: Double = 50.0,
        connection: CapacitorConnectionType = CapacitorConnectionType.DELTA
    ): Double {
        if (voltageV <= 0.0 || freqHz <= 0.0 || kvar <= 0.0) return 0.0
        val starUf = (kvar * 1e9) / (2.0 * Math.PI * freqHz * voltageV * voltageV)
        return if (connection == CapacitorConnectionType.DELTA) starUf / 3.0 else starUf
    }

    /**
     * Explicit semantic API: Converts total capacitor bank reactive power (kVAR) to per-branch/phase capacitance (µF).
     */
    fun totalBankKvarToPerBranchMicroFarad(
        totalKvar: Double,
        voltageV: Double,
        freqHz: Double = 50.0,
        connection: CapacitorConnectionType = CapacitorConnectionType.DELTA
    ): Double = kvarToMicroFarad(totalKvar, voltageV, freqHz, connection)

    /**
     * Explicit semantic API: Converts per-branch/phase capacitance (µF) to total capacitor bank reactive power (kVAR).
     */
    fun perBranchMicroFaradToTotalBankKvar(
        ufPerBranch: Double,
        voltageV: Double,
        freqHz: Double = 50.0,
        connection: CapacitorConnectionType = CapacitorConnectionType.DELTA
    ): Double = microFaradToKvar(ufPerBranch, voltageV, freqHz, connection)

    // =========================================================================
    // NEW CONVERTERS (1 to 6)
    // =========================================================================

    // 1. Power Triangle (kVA <-> kW <-> kVAR)
    fun powerTriangleFromS(sKva: Double, cosPhi: Double): PowerTriangleResult {
        if (sKva <= 0) return PowerTriangleResult(0.0, 0.0, 0.0, 0.8, 0.6)
        val clampedCos = cosPhi.coerceIn(0.1, 1.0)
        val sinPhi = sqrt(1.0 - clampedCos * clampedCos)
        val pKw = sKva * clampedCos
        val qKvar = sKva * sinPhi
        return PowerTriangleResult(pKw, qKvar, sKva, clampedCos, sinPhi)
    }

    fun powerTriangleFromPQ(pKw: Double, qKvar: Double): PowerTriangleResult {
        if (pKw <= 0 && qKvar <= 0) return PowerTriangleResult(0.0, 0.0, 0.0, 1.0, 0.0)
        val sKva = sqrt(pKw * pKw + qKvar * qKvar)
        val cosPhi = if (sKva > 0) (pKw / sKva).coerceIn(0.1, 1.0) else 1.0
        val sinPhi = sqrt(1.0 - cosPhi * cosPhi)
        return PowerTriangleResult(pKw, qKvar, sKva, cosPhi, sinPhi)
    }

    // 2. Current to Power (Amps <-> kW / kVA)
    fun currentToPower(amps: Double, voltageV: Double = 400.0, cosPhi: Double = 0.8, isThreePhase: Boolean = true): CurrentPowerResult {
        if (amps <= 0 || voltageV <= 0) return CurrentPowerResult(0.0, 0.0, 0.0)
        val clampedCos = cosPhi.coerceIn(0.1, 1.0)
        val factor = if (isThreePhase) sqrt(3.0) else 1.0
        val pKw = (factor * voltageV * amps * clampedCos) / 1000.0
        val sKva = (factor * voltageV * amps) / 1000.0
        return CurrentPowerResult(pKw, sKva, amps)
    }

    fun powerToCurrent(pKw: Double, voltageV: Double = 400.0, cosPhi: Double = 0.8, isThreePhase: Boolean = true): CurrentPowerResult {
        if (pKw <= 0 || voltageV <= 0) return CurrentPowerResult(0.0, 0.0, 0.0)
        val clampedCos = cosPhi.coerceIn(0.1, 1.0)
        val factor = if (isThreePhase) sqrt(3.0) else 1.0
        val amps = (pKw * 1000.0) / (factor * voltageV * clampedCos)
        val sKva = (factor * voltageV * amps) / 1000.0
        return CurrentPowerResult(pKw, sKva, amps)
    }

    // Direct Apparent Power (kVA) <-> Current (Amps) Conversion (Independent of PF)
    fun apparentPowerToCurrent(sKva: Double, voltageV: Double = 400.0, isThreePhase: Boolean = true): Double {
        if (sKva <= 0.0 || voltageV <= 0.0) return 0.0
        val factor = if (isThreePhase) sqrt(3.0) else 1.0
        return (sKva * 1000.0) / (factor * voltageV)
    }

    fun currentToApparentPower(amps: Double, voltageV: Double = 400.0, isThreePhase: Boolean = true): Double {
        if (amps <= 0.0 || voltageV <= 0.0) return 0.0
        val factor = if (isThreePhase) sqrt(3.0) else 1.0
        return (factor * voltageV * amps) / 1000.0
    }

    fun apparentToActivePower(sKva: Double, cosPhi: Double): Double {
        if (sKva <= 0.0) return 0.0
        val clampedCos = cosPhi.coerceIn(0.0, 1.0)
        return sKva * clampedCos
    }

    fun activeToApparentPower(pKw: Double, cosPhi: Double): Double {
        if (pKw <= 0.0) return 0.0
        val clampedCos = cosPhi.coerceIn(0.01, 1.0)
        return pKw / clampedCos
    }

    fun reactivePowerFromSAndCosPhi(sKva: Double, cosPhi: Double): Double {
        if (sKva <= 0.0) return 0.0
        val clampedCos = cosPhi.coerceIn(0.0, 1.0)
        val sinPhi = sqrt((1.0 - clampedCos * clampedCos).coerceAtLeast(0.0))
        return sKva * sinPhi
    }

    // 3. Power Factor Correction (kVAR Needed)
    fun powerFactorCorrection(pKw: Double, cosPhi1: Double, cosPhi2: Double, voltageV: Double = 400.0): PfcResult {
        if (pKw <= 0) return PfcResult(0.0, 0.0)
        val c1 = cosPhi1.coerceIn(0.1, 0.999)
        val c2 = cosPhi2.coerceIn(c1, 1.0)
        val tanPhi1 = kotlin.math.tan(acos(c1))
        val tanPhi2 = kotlin.math.tan(acos(c2))
        val qcKvar = pKw * (tanPhi1 - tanPhi2)
        val deltaAmps = if (voltageV > 0) ((pKw * 1000.0) / (sqrt(3.0) * voltageV)) * ((1.0 / c1) - (1.0 / c2)) else 0.0
        return PfcResult(qcKvar, deltaAmps)
    }

    // 4. Cable Voltage Drop Calculator (mV/A/m)
    fun cableVoltageDrop(
        rOhmPerKm: Double,
        xOhmPerKm: Double,
        amps: Double,
        lengthM: Double,
        cosPhi: Double = 0.8,
        isThreePhase: Boolean = true,
        voltageV: Double = 400.0
    ): VdropResult {
        if (amps <= 0 || lengthM <= 0) return VdropResult(0.0, 0.0, 0.0)
        val clampedCos = cosPhi.coerceIn(0.1, 1.0)
        val sinPhi = sin(acos(clampedCos))
        val factor = if (isThreePhase) sqrt(3.0) else 2.0
        val mVperAm = factor * (rOhmPerKm * clampedCos + xOhmPerKm * sinPhi)
        val vDrop = (mVperAm * amps * lengthM) / 1000.0
        val vDropPct = if (voltageV > 0) (vDrop / voltageV) * 100.0 else 0.0
        return VdropResult(mVperAm, vDrop, vDropPct)
    }

    // 5. Battery Capacity (Ah <-> kWh)
    fun batteryAhToKwh(ah: Double, vDc: Double, dod: Double = 0.8, efficiency: Double = 0.9): BatteryResult {
        if (ah <= 0 || vDc <= 0) return BatteryResult(0.0, 0.0)
        val clampedDod = dod.coerceIn(0.1, 1.0)
        val clampedEff = efficiency.coerceIn(0.1, 1.0)
        val eKwh = (ah * vDc * clampedDod * clampedEff) / 1000.0
        return BatteryResult(eKwh, ah)
    }

    fun batteryKwhToAh(kwh: Double, vDc: Double, dod: Double = 0.8, efficiency: Double = 0.9): BatteryResult {
        if (kwh <= 0 || vDc <= 0) return BatteryResult(0.0, 0.0)
        val clampedDod = dod.coerceIn(0.1, 1.0)
        val clampedEff = efficiency.coerceIn(0.1, 1.0)
        val ahReq = (kwh * 1000.0) / (vDc * clampedDod * clampedEff)
        return BatteryResult(kwh, ahReq)
    }

    // 6. Transformer Quick Short Circuit Current (kA)
    fun txQuickShortCircuit(kva: Double, zPct: Double, secondaryVoltV: Double = 400.0): TxShortCircuitResult {
        if (kva <= 0 || zPct <= 0 || secondaryVoltV <= 0) return TxShortCircuitResult(0.0, 0.0)
        val iFlcAmps = (kva * 1000.0) / (sqrt(3.0) * secondaryVoltV)
        val iScKa = iFlcAmps / (1000.0 * (zPct / 100.0))
        return TxShortCircuitResult(iFlcAmps, iScKa)
    }
}
