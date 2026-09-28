package com.example.ui.screens.quick

import androidx.compose.runtime.Immutable
import com.example.engine.converters.ElectricalConverters
import com.example.engine.quick.BaseAmpacityResolution
import com.example.engine.quick.QuickEngineeringDataBridge
import com.example.engine.quick.ResolvedCableImpedance
import java.util.Locale
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VoltageDropInputMode {
    CURRENT,
    POWER
}

enum class VoltageDropSystemPhase {
    THREE_PHASE,
    SINGLE_PHASE
}

enum class VoltageDropComplianceStatus {
    PASS,
    FAIL,
    WARNING,

    /** The value this check needs could not be resolved, so no PASS/FAIL/WARNING can be asserted. */
    DATA_MISSING
}

@Immutable
data class VoltageDropCheckResult(
    val title: String,
    val status: VoltageDropComplianceStatus,
    val summaryText: String,
    val detailText: String
)

@Immutable
data class VoltageDropInputState(
    val inputMode: VoltageDropInputMode = VoltageDropInputMode.CURRENT,
    val phase: VoltageDropSystemPhase = VoltageDropSystemPhase.THREE_PHASE,
    val voltageV: Double = 400.0,
    val currentA: Double = 100.0,
    val powerKw: Double = 55.0,
    val powerFactor: Double = 0.85,
    val lengthM: Double = 50.0,
    val parallelRuns: Int = 1,
    val maxVdLimitPct: Double = 3.0,
    val manufacturer: String = "Elsewedy",
    val cableType: String = "Cu/XLPE/PVC - 4C",
    val cableSizeMm2: Double = 35.0,
    val installationMethod: String = "Laid in Free Air",
    val isManualOverride: Boolean = false,
    val manualROhmPerKm: Double = 0.727,
    val manualXOhmPerKm: Double = 0.080
)

@Immutable
data class VoltageDropDetailedResult(
    // Primary Metrics
    val voltageDropPct: Double,
    val voltageDropV: Double,
    val mVperAm: Double,
    val receivingVoltageV: Double,

    // Operating Flow
    val effectiveOperatingCurrentA: Double,
    val isCurrentDerived: Boolean,
    val currentPerRunA: Double,
    val isThreePhase: Boolean,
    val parallelRuns: Int,
    val nominalVoltageV: Double,

    // Engineering Checks
    val vdCheck: VoltageDropCheckResult,
    val ampacityCheck: VoltageDropCheckResult?,

    // Resolved Cable & Impedance Data
    val resolvedROhmPerKm: Double,
    val resolvedXOhmPerKm: Double,
    val rColumnUsed: String,
    val baseAmpacityA: Double?,
    val isManualOverride: Boolean,
    val dataSource: String,

    // Calculation Breakdown & Provenance
    val formulaString: String,
    val substitutionString: String,
    val provenanceCitation: String
)

/**
 * Pure Calculation Engine for Voltage Drop V2.
 *
 * Coordinates authoritative electrical converters and catalog resolution
 * with strict zero-mutation guarantees.
 */
object VoltageDropCalculationEngine {

    fun calculate(
        input: VoltageDropInputState,
        bridge: QuickEngineeringDataBridge = QuickEngineeringDataBridge
    ): VoltageDropDetailedResult? {
        // 1. Validate fundamental numeric boundaries
        if (input.voltageV <= 0.0 || input.lengthM <= 0.0 || input.parallelRuns <= 0) return null
        if (input.maxVdLimitPct <= 0.0) return null

        val cosPhi = input.powerFactor.coerceIn(0.01, 1.0)
        val is3P = input.phase == VoltageDropSystemPhase.THREE_PHASE

        // 2. Resolve operating current based on input mode (User supplied vs Derived from Power)
        val (operatingCurrentA, isDerived) = when (input.inputMode) {
            VoltageDropInputMode.CURRENT -> {
                if (input.currentA <= 0.0) return null
                Pair(input.currentA, false)
            }
            VoltageDropInputMode.POWER -> {
                if (input.powerKw <= 0.0) return null
                val currentRes = ElectricalConverters.powerToCurrent(
                    pKw = input.powerKw,
                    voltageV = input.voltageV,
                    cosPhi = cosPhi,
                    isThreePhase = is3P
                )
                Pair(currentRes.amps, true)
            }
        }

        // 3. Resolve Resistance (R) & Reactance (X) from Catalog or Manual Override
        val (r, x, rColumn, dataSource) = if (input.isManualOverride) {
            if (input.manualROhmPerKm < 0.0 || input.manualXOhmPerKm < 0.0) return null
            Tuple4(
                input.manualROhmPerKm,
                input.manualXOhmPerKm,
                "Manual User Override",
                "MANUAL OVERRIDE"
            )
        } else {
            val resolvedImp = bridge.resolveCableImpedance(input.cableSizeMm2, input.cableType) ?: return null
            Tuple4(
                resolvedImp.rOhmPerKm,
                resolvedImp.xOhmPerKm,
                resolvedImp.rColumnUsed,
                "Catalog (data_sheet.json)"
            )
        }

        // 4. Resolve Base Ampacity (Iz) if in catalog mode.
        // Manual override deliberately supplies R/X instead, so no catalog Iz is looked up: a null
        // resolution here means "not from the catalog" and is already labelled MANUAL OVERRIDE in
        // dataSource, not that a lookup failed.
        val ampacityResolution: BaseAmpacityResolution? = if (!input.isManualOverride) {
            bridge.resolveBaseAmpacityDetailed(
                sizeMm2 = input.cableSizeMm2,
                cableType = input.cableType,
                manufacturer = input.manufacturer,
                installation = input.installationMethod
            )
        } else null

        val baseAmpacity = (ampacityResolution as? BaseAmpacityResolution.Resolved)?.ampacityA

        // 5. Invoke Authoritative Voltage Drop Converter
        // Parallel runs scaling: effective current per run = I / N (or effective R/N, X/N)
        val currentPerRun = operatingCurrentA / input.parallelRuns.toDouble()
        val rawVdResult = ElectricalConverters.cableVoltageDrop(
            rOhmPerKm = r,
            xOhmPerKm = x,
            amps = currentPerRun,
            lengthM = input.lengthM,
            cosPhi = cosPhi,
            isThreePhase = is3P,
            voltageV = input.voltageV
        )

        val vDropV = rawVdResult.vDrop
        val vDropPct = rawVdResult.vDropPct
        val mVperAm = rawVdResult.mVperAm
        val receivingVoltage = input.voltageV - vDropV

        // 6. Build Engineering Checks
        // A. Voltage Drop Compliance Check
        val isVdPass = vDropPct <= input.maxVdLimitPct + 0.0001
        val marginPct = input.maxVdLimitPct - vDropPct
        val marginV = (marginPct / 100.0) * input.voltageV
        val vdStatus = if (isVdPass) VoltageDropComplianceStatus.PASS else VoltageDropComplianceStatus.FAIL

        val vdSummary = if (isVdPass) {
            "Compliant: ${String.format(Locale.US, "%.2f", vDropPct)}% ≤ ${String.format(Locale.US, "%.2f", input.maxVdLimitPct)}% limit"
        } else {
            "Non-Compliant: ${String.format(Locale.US, "%.2f", vDropPct)}% exceeds ${String.format(Locale.US, "%.2f", input.maxVdLimitPct)}% limit"
        }

        val vdDetail = "Margin: ${if (marginPct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", marginPct)}% (${String.format(Locale.US, "%.2f", marginV)} V)"

        val vdCheck = VoltageDropCheckResult(
            title = "Voltage Drop Compliance",
            status = vdStatus,
            summaryText = vdSummary,
            detailText = vdDetail
        )

        // B. Conductor Capacity Check
        //
        // N1: an unresolved base ampacity is surfaced as an explicit non-PASS check instead of
        // being dropped, so a missing or unsupported installation method can never read as an
        // implied PASS and never silently removes this check from the result.
        val ampacityCheck: VoltageDropCheckResult? = when {
            ampacityResolution == null -> null

            ampacityResolution is BaseAmpacityResolution.Resolved -> {
                val iz = ampacityResolution.ampacityA
                val isAmpacityPass = currentPerRun <= iz + 0.0001
                val loadingPct = (currentPerRun / iz) * 100.0
                val ampStatus = if (isAmpacityPass) VoltageDropComplianceStatus.PASS else VoltageDropComplianceStatus.WARNING
                val ampSummary = if (isAmpacityPass) {
                    "Conductor Loading: ${String.format(Locale.US, "%.1f", currentPerRun)} A ≤ ${String.format(Locale.US, "%.1f", iz)} A rating (${String.format(Locale.US, "%.1f", loadingPct)}% loaded)"
                } else {
                    "Thermal Overload Warning: ${String.format(Locale.US, "%.1f", currentPerRun)} A exceeds ${String.format(Locale.US, "%.1f", iz)} A rating (${String.format(Locale.US, "%.1f", loadingPct)}% loaded)"
                }
                VoltageDropCheckResult(
                    title = "Conductor Thermal Rating (Iz)",
                    status = ampStatus,
                    summaryText = ampSummary,
                    detailText = "Source: cable_catalog.json (${input.manufacturer}, ${input.installationMethod})"
                )
            }

            else -> {
                val unresolved = ampacityResolution as BaseAmpacityResolution.Unresolved
                VoltageDropCheckResult(
                    title = "Conductor Thermal Rating (Iz)",
                    status = VoltageDropComplianceStatus.DATA_MISSING,
                    summaryText = when (unresolved.reason) {
                        BaseAmpacityResolution.Reason.UNSUPPORTED_INSTALLATION ->
                            "DATA MISSING — base cable ampacity cannot be resolved for installation " +
                                "method '${input.installationMethod}'."
                        BaseAmpacityResolution.Reason.CATALOG_MISS ->
                            "DATA MISSING — base cable ampacity cannot be resolved for " +
                                "${input.cableSizeMm2} mm2 ${input.cableType} from ${input.manufacturer}."
                    },
                    detailText = unresolved.detail
                )
            }
        }

        // 7. Format Calculation Details & Step-by-Step Mathematical Substitution
        val factorSymbol = if (is3P) "√3" else "2"
        val sinPhi = sin(acos(cosPhi))
        val formulaStr = if (input.parallelRuns > 1) {
            "ΔV = [$factorSymbol × (R·cosφ + X·sinφ) × I × L] / (1000 × N_parallel)"
        } else {
            "ΔV = [$factorSymbol × (R·cosφ + X·sinφ) × I × L] / 1000"
        }

        val substitutionStr = if (input.parallelRuns > 1) {
            "ΔV = [${if (is3P) "1.732" else "2.000"} × (${String.format(Locale.US, "%.4f", r)}×${String.format(Locale.US, "%.2f", cosPhi)} + ${String.format(Locale.US, "%.4f", x)}×${String.format(Locale.US, "%.3f", sinPhi)}) × ${String.format(Locale.US, "%.1f", operatingCurrentA)} × ${String.format(Locale.US, "%.1f", input.lengthM)}] / (1000 × ${input.parallelRuns}) = ${String.format(Locale.US, "%.2f", vDropV)} V (${String.format(Locale.US, "%.2f", vDropPct)}%)"
        } else {
            "ΔV = [${if (is3P) "1.732" else "2.000"} × (${String.format(Locale.US, "%.4f", r)}×${String.format(Locale.US, "%.2f", cosPhi)} + ${String.format(Locale.US, "%.4f", x)}×${String.format(Locale.US, "%.3f", sinPhi)}) × ${String.format(Locale.US, "%.1f", operatingCurrentA)} × ${String.format(Locale.US, "%.1f", input.lengthM)}] / 1000 = ${String.format(Locale.US, "%.2f", vDropV)} V (${String.format(Locale.US, "%.2f", vDropPct)}%)"
        }

        val citation = "IEC 60364-5-52 / BS 7671 Voltage Drop Methodology (Balanced AC Network)"

        return VoltageDropDetailedResult(
            voltageDropPct = vDropPct,
            voltageDropV = vDropV,
            mVperAm = mVperAm,
            receivingVoltageV = receivingVoltage,
            effectiveOperatingCurrentA = operatingCurrentA,
            isCurrentDerived = isDerived,
            currentPerRunA = currentPerRun,
            isThreePhase = is3P,
            parallelRuns = input.parallelRuns,
            nominalVoltageV = input.voltageV,
            vdCheck = vdCheck,
            ampacityCheck = ampacityCheck,
            resolvedROhmPerKm = r,
            resolvedXOhmPerKm = x,
            rColumnUsed = rColumn,
            baseAmpacityA = baseAmpacity,
            isManualOverride = input.isManualOverride,
            dataSource = dataSource,
            formulaString = formulaStr,
            substitutionString = substitutionStr,
            provenanceCitation = citation
        )
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
