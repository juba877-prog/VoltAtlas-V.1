package com.example.ai.tools.cable

import com.example.data.CircuitEntity
import com.example.engine.SizingResult
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.CableCheckCode
import com.example.engine.coordinator.CableCheckStatus
import com.example.engine.coordinator.CableEngineeringEvaluation
import com.example.engine.coordinator.CableStructuredCheck

/**
 * Pure deterministic mapper from engine-layer [CableEngineeringEvaluation] to AI tool contracts.
 *
 * Strict Authority Boundary:
 * 1. Zero math/formulas: Does NOT calculate ampacity, voltage drop, or withstand.
 * 2. Zero thresholds: Owns zero numerical comparison limits or derating factors.
 * 3. Pure mapping: Translates authoritative engine checks and governing codes to public DTOs.
 */
object GoverningCriterionInterpreter {

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
        // Delegate authoritative checks determination to engine service layer
        val eval = CableCalculationService.evaluateEngineeringChecks(result, circuit, parentCumulativeVD)
        return fromEvaluation(eval)
    }

    fun fromEvaluation(eval: CableEngineeringEvaluation): Interpretation {
        val result = eval.sizingResult

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

        val checkDtos = eval.checks.map { mapCheck(it) }
        val overallStatus = mapStatus(eval.overallStatus)

        val hasAmpacityFail = eval.checks.any { it.checkCode == CableCheckCode.AMPACITY && it.status == CableCheckStatus.FAIL }
        val hasVdFail = eval.checks.any { (it.checkCode == CableCheckCode.VOLTAGE_DROP || it.checkCode == CableCheckCode.CUMULATIVE_VOLTAGE_DROP) && it.status == CableCheckStatus.FAIL }

        val criterion = when {
            eval.isManualOverride -> GoverningCriterion.MANUAL_OVERRIDE
            hasAmpacityFail && hasVdFail -> GoverningCriterion.MULTIPLE_CONSTRAINTS
            eval.governingCheckCode != null -> mapCode(eval.governingCheckCode)
            overallStatus == CheckStatus.PASS -> GoverningCriterion.NONE
            else -> GoverningCriterion.MULTIPLE_CONSTRAINTS
        }

        return Interpretation(
            overallStatus = overallStatus,
            governingCriterion = criterion,
            governingReason = eval.governingReason,
            checks = checkDtos,
            details = details
        )
    }

    fun mapStatus(status: CableCheckStatus): CheckStatus = when (status) {
        CableCheckStatus.PASS -> CheckStatus.PASS
        CableCheckStatus.WARNING -> CheckStatus.WARNING
        CableCheckStatus.FAIL -> CheckStatus.FAIL
        CableCheckStatus.INCOMPLETE -> CheckStatus.INCOMPLETE
    }

    fun mapCode(code: CableCheckCode): GoverningCriterion = when (code) {
        CableCheckCode.AMPACITY -> GoverningCriterion.AMPACITY
        CableCheckCode.VOLTAGE_DROP -> GoverningCriterion.VOLTAGE_DROP
        CableCheckCode.CUMULATIVE_VOLTAGE_DROP -> GoverningCriterion.CUMULATIVE_VOLTAGE_DROP
        CableCheckCode.SHORT_CIRCUIT_WITHSTAND -> GoverningCriterion.SHORT_CIRCUIT_WITHSTAND
        CableCheckCode.DEVICE_COMPATIBILITY -> GoverningCriterion.DEVICE_COMPATIBILITY
        CableCheckCode.CATALOG_AVAILABILITY -> GoverningCriterion.CATALOG_AVAILABILITY
        CableCheckCode.INSTALLATION_DERATING -> GoverningCriterion.INSTALLATION_DERATING
        CableCheckCode.MANUAL_OVERRIDE -> GoverningCriterion.MANUAL_OVERRIDE
        CableCheckCode.INCOMPLETE_INPUT -> GoverningCriterion.INCOMPLETE_INPUT
    }

    private fun mapCheck(check: CableStructuredCheck): EngineeringCheckItemDto =
        EngineeringCheckItemDto(
            checkName = check.checkName,
            status = mapStatus(check.status),
            actualValue = check.actualValue,
            limitValue = check.limitValue,
            unit = check.unit,
            message = check.message
        )
}
