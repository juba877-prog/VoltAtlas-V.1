package com.example.ai.tools.cable

import com.example.data.CircuitEntity
import com.example.engine.SizingResult
import java.util.Locale

/**
 * Deterministic interpreter over returned engine calculation results.
 *
 * Rules:
 * 1. Analyzes actual engine check results without re-calculating or guessing.
 * 2. If multiple checks fail, preserves all failures and classifies MULTIPLE_CONSTRAINTS.
 * 3. Distinguishes manual overrides from automatic engine sizing.
 * 4. Never fabricates criteria or infers constraints from cable size alone.
 */
object GoverningCriterionInterpreter {

    private const val EPSILON = 1e-6

    data class Interpretation(
        val overallStatus: CheckStatus,
        val governingCriterion: GoverningCriterion,
        val governingReason: String,
        val checks: List<EngineeringCheckItemDto>,
        val details: CableSizingDetailsDto
    )

    fun interpret(
        result: SizingResult,
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0
    ): Interpretation {
        val details = CableSizingDetailsDto(
            Ib_A = result.Ib_A,
            deviceSize_A = result.deviceSize_A,
            deviceType = result.deviceType,
            cTotalDerating = result.cTotalDerating,
            iRequired_perCable_A = result.iRequired_perCable_A,
            productCode = result.productCode,
            cableSize_mm2 = result.cableSize_mm2,
            ratedI_table_A = result.ratedI_table_A,
            capacity_derated_Iz_A = result.capacity_derated_Iz_A,
            utilizationPct = result.utilizationPct,
            neutral_mm2 = result.neutral_mm2,
            earthCPC_mm2 = result.earthCPC_mm2,
            voltDrop_V = result.voltDrop_V,
            vdPct = result.vdPct,
            cumulativeVDpct = result.cumulativeVDpct,
            status = result.status,
            failReason = result.failReason,
            cableMaterial = result.cableMaterial,
            insulation = result.insulation,
            kFactor = result.kFactor,
            deviceBreakingKA = result.deviceBreakingKA,
            cableOD_mm = result.cableOD_mm,
            earthOD_mm = result.earthOD_mm,
            rUsed = result.rUsed,
            xUsed = result.xUsed,
            clearingTime_s = result.clearingTime_s,
            odSourceMfg = result.odSourceMfg,
            isODApproximated = result.isODApproximated
        )

        val isManual = !circuit.overrideCableSize.isNullOrBlank() &&
            !circuit.overrideCableSize.equals("AUTO", ignoreCase = true)

        val ampacityOk = result.Ib_A <= result.deviceSize_A + EPSILON &&
            result.deviceSize_A <= result.capacity_derated_Iz_A + EPSILON &&
            result.capacity_derated_Iz_A > 0.0

        val maxAllowedVdPct = circuit.maxVDpct * 100.0
        val localVdOk = result.vdPct <= maxAllowedVdPct + EPSILON
        val cumulativeVdOk = result.cumulativeVDpct <= maxAllowedVdPct + EPSILON

        val checks = mutableListOf<EngineeringCheckItemDto>()

        // 1. Ampacity Check
        checks.add(
            EngineeringCheckItemDto(
                checkName = "Ampacity & Device Protection",
                status = if (ampacityOk) CheckStatus.PASS else CheckStatus.FAIL,
                actualValue = "Ib=${String.format(Locale.US, "%.1f", result.Ib_A)}A, In=${String.format(Locale.US, "%.1f", result.deviceSize_A)}A",
                limitValue = "Iz=${String.format(Locale.US, "%.1f", result.capacity_derated_Iz_A)}A",
                unit = "A",
                message = if (ampacityOk) {
                    "Derated cable capacity (${result.capacity_derated_Iz_A} A) safely exceeds protective device rating (${result.deviceSize_A} A)."
                } else {
                    "Derated cable capacity (${result.capacity_derated_Iz_A} A) is insufficient for protective device rating (${result.deviceSize_A} A)."
                }
            )
        )

        // 2. Local Voltage Drop Check
        checks.add(
            EngineeringCheckItemDto(
                checkName = "Local Voltage Drop",
                status = if (localVdOk) CheckStatus.PASS else CheckStatus.FAIL,
                actualValue = String.format(Locale.US, "%.2f", result.vdPct),
                limitValue = String.format(Locale.US, "%.2f", maxAllowedVdPct),
                unit = "%",
                message = if (localVdOk) {
                    "Local voltage drop (${String.format(Locale.US, "%.2f", result.vdPct)}%) is within limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                } else {
                    "Local voltage drop (${String.format(Locale.US, "%.2f", result.vdPct)}%) exceeds limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
            )
        )

        // 3. Cumulative Voltage Drop Check
        checks.add(
            EngineeringCheckItemDto(
                checkName = "Cumulative Voltage Drop",
                status = if (cumulativeVdOk) CheckStatus.PASS else CheckStatus.FAIL,
                actualValue = String.format(Locale.US, "%.2f", result.cumulativeVDpct),
                limitValue = String.format(Locale.US, "%.2f", maxAllowedVdPct),
                unit = "%",
                message = if (cumulativeVdOk) {
                    "Cumulative voltage drop from supply (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}%) is within limit."
                } else {
                    "Cumulative voltage drop (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}%) exceeds limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
            )
        )

        // Determine Overall Status & Governing Criterion
        val isEngineFail = result.status.contains("FAIL", ignoreCase = true) || result.failReason.isNotBlank()

        val overallStatus: CheckStatus
        val criterion: GoverningCriterion
        val reason: String

        if (isEngineFail) {
            overallStatus = CheckStatus.FAIL
            val failReason = result.failReason.trim()

            when {
                failReason.contains("Catalog data is not available", ignoreCase = true) ||
                    failReason.contains("not found in catalog", ignoreCase = true) ||
                    failReason.contains("does not exist for", ignoreCase = true) -> {
                    criterion = GoverningCriterion.CATALOG_AVAILABILITY
                    reason = failReason.ifEmpty { "Selected cable specification is not available in catalog." }
                }
                failReason.contains("Voltage must be", ignoreCase = true) ||
                    failReason.contains("Load kVA", ignoreCase = true) ||
                    failReason.contains("Parallel runs", ignoreCase = true) ||
                    failReason.contains("Power factor", ignoreCase = true) ||
                    failReason.contains("Cable length", ignoreCase = true) ||
                    failReason.contains("Missing source transformer", ignoreCase = true) -> {
                    criterion = GoverningCriterion.INCOMPLETE_INPUT
                    reason = failReason
                }
                failReason.contains("derating", ignoreCase = true) -> {
                    criterion = GoverningCriterion.INSTALLATION_DERATING
                    reason = failReason
                }
                isManual -> {
                    criterion = GoverningCriterion.MANUAL_OVERRIDE
                    reason = "Manual override size ${result.cableSize_mm2} failed engineering checks: $failReason"
                }
                !ampacityOk && (!localVdOk || !cumulativeVdOk) -> {
                    criterion = GoverningCriterion.MULTIPLE_CONSTRAINTS
                    reason = "Candidate failed both ampacity (Iz=${result.capacity_derated_Iz_A}A < In=${result.deviceSize_A}A) and voltage drop (VD=${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}% > ${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
                !ampacityOk -> {
                    criterion = GoverningCriterion.AMPACITY
                    reason = "Cable derated ampacity (${result.capacity_derated_Iz_A} A) is below protective device rating (${result.deviceSize_A} A)."
                }
                !cumulativeVdOk && localVdOk -> {
                    criterion = GoverningCriterion.CUMULATIVE_VOLTAGE_DROP
                    reason = "Cumulative voltage drop (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}%) exceeds limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%) due to upstream drop."
                }
                !localVdOk -> {
                    criterion = GoverningCriterion.VOLTAGE_DROP
                    reason = "Local voltage drop (${String.format(Locale.US, "%.2f", result.vdPct)}%) exceeds maximum limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
                else -> {
                    criterion = GoverningCriterion.MULTIPLE_CONSTRAINTS
                    reason = failReason.ifEmpty { "Engine checks did not pass." }
                }
            }
        } else {
            overallStatus = CheckStatus.PASS
            if (isManual) {
                criterion = GoverningCriterion.MANUAL_OVERRIDE
                reason = "Conductor size ${result.cableSize_mm2} was configured via manual user override and satisfies all engineering constraints."
            } else {
                val vdMargin = (maxAllowedVdPct - result.cumulativeVDpct) / maxAllowedVdPct
                val ampacityMargin = (result.capacity_derated_Iz_A - result.deviceSize_A) / result.capacity_derated_Iz_A

                if (vdMargin < ampacityMargin) {
                    if (parentCumulativeVD > 0.0 && (result.cumulativeVDpct - result.vdPct) > 0.5) {
                        criterion = GoverningCriterion.CUMULATIVE_VOLTAGE_DROP
                        reason = "Sized by cumulative voltage drop constraint (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}% / ${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                    } else {
                        criterion = GoverningCriterion.VOLTAGE_DROP
                        reason = "Sized by voltage drop constraint (${String.format(Locale.US, "%.2f", result.vdPct)}% / ${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                    }
                } else {
                    criterion = GoverningCriterion.AMPACITY
                    reason = "Sized by continuous current ampacity requirement (Iz=${result.capacity_derated_Iz_A} A >= In=${result.deviceSize_A} A, utilization ${String.format(Locale.US, "%.1f", result.utilizationPct)}%)."
                }
            }
        }

        return Interpretation(
            overallStatus = overallStatus,
            governingCriterion = criterion,
            governingReason = reason,
            checks = checks,
            details = details
        )
    }
}
