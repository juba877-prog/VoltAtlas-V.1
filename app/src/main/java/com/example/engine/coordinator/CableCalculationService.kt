package com.example.engine.coordinator

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.BuswayEngine
import com.example.engine.CableSizingEngine
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.engine.CableFailureCode
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CableCheckCode {
    AMPACITY,
    VOLTAGE_DROP,
    CUMULATIVE_VOLTAGE_DROP,
    SHORT_CIRCUIT_WITHSTAND,
    DEVICE_COMPATIBILITY,
    CATALOG_AVAILABILITY,
    INSTALLATION_DERATING,
    MANUAL_OVERRIDE,
    INCOMPLETE_INPUT,
    INDETERMINATE
}

enum class CableCheckStatus {
    PASS,
    WARNING,
    FAIL,
    INCOMPLETE
}

data class CableStructuredCheck(
    val checkCode: CableCheckCode,
    val checkName: String,
    val status: CableCheckStatus,
    val actualValue: EngineeringValue? = null,
    val limitValue: EngineeringValue? = null,
    val actualDisplay: String = "",
    val limitDisplay: String = "",
    val unit: String = "",
    val message: String,
    val isGoverning: Boolean = false
)

data class CableEngineeringEvaluation(
    val sizingResult: SizingResult,
    val overallStatus: CableCheckStatus,
    val checks: List<CableStructuredCheck>,
    val governingCheckCode: CableCheckCode?,
    val governingReason: String,
    val selectionSource: String = "AUTO"
)

/**
 * Service dedicated to orchestrating single-circuit and bulk cable sizing calculations.
 * Wraps [CableSizingEngine] and [BuswayEngine] without altering any underlying formulas.
 * Authoritatively adapts engine calculation outcomes into typed [CableEngineeringEvaluation] without recomputation.
 */
object CableCalculationService {

    /**
     * Normalizes circuit auto overrides before sizing execution.
     */
    fun normalizeAutoOverride(circuit: CircuitEntity): CircuitEntity {
        val normalizedCableOverride = circuit.overrideCableSize
            ?.trim()
            ?.takeUnless { it.isEmpty() || it.equals("AUTO", ignoreCase = true) }
        return circuit.copy(overrideCableSize = normalizedCableOverride)
    }

    /**
     * Executes targeted sizing for a single circuit.
     */
    fun calculateSingleCircuit(
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0,
        upstreamCircuits: List<CircuitEntity> = emptyList(),
        transformers: List<TransformerEntity> = emptyList()
    ): SizingResult {
        val normalized = normalizeAutoOverride(circuit)

        val sizingRes = if (normalized.feederType.equals("Busway", ignoreCase = true)) {
            val buswayRes = BuswayEngine.sizeBusway(
                circuit = normalized,
                parentCumulativeVD = parentCumulativeVD
            )
            BuswayEngine.toSizingResult(buswayRes)
        } else {
            CableSizingEngine.sizeCircuit(
                circuit = normalized,
                parentCumulativeVD = parentCumulativeVD,
                allCircuits = upstreamCircuits
            )
        }

        val fedLower = normalized.fedFrom.trim().lowercase()
        val txExists = transformers.any { it.transformerId.equals(normalized.transformerId, ignoreCase = true) }

        return if (fedLower == "transformer" && !txExists) {
            sizingRes.copy(
                status = "✗ FAIL",
                failReason = "Missing source transformer '${normalized.transformerId}' in this project.",
                resultStatus = SizingResultStatus.INCOMPLETE,
                isAmpacityOk = false,
                isVoltageDropOk = false,
                primaryFailureCode = CableFailureCode.INCOMPLETE_INPUT
            )
        } else {
            sizingRes
        }
    }

    /**
     * Authoritative, side-effect-free evaluation of a specific candidate conductor size or configuration.
     * Delegates directly to [CableSizingEngine] via [calculateSingleCircuit] with candidate parameters.
     */
    fun evaluateCandidate(
        circuit: CircuitEntity,
        candidateSizeFormatted: String,
        parallelRuns: Int = circuit.parallelRuns,
        cableType: String = circuit.cableType,
        deviceSizeA: Double? = circuit.overrideDeviceSize,
        deviceType: String? = circuit.overrideDeviceType,
        parentCumulativeVD: Double = 0.0,
        upstreamCircuits: List<CircuitEntity> = emptyList(),
        transformers: List<TransformerEntity> = emptyList()
    ): SizingResult {
        val candidateCircuit = circuit.copy(
            overrideCableSize = candidateSizeFormatted,
            parallelRuns = parallelRuns,
            cableType = cableType,
            overrideDeviceSize = deviceSizeA,
            overrideDeviceType = deviceType
        )
        return calculateSingleCircuit(
            circuit = candidateCircuit,
            parentCumulativeVD = parentCumulativeVD,
            upstreamCircuits = upstreamCircuits,
            transformers = transformers
        )
    }

    /**
     * Authoritative adaptation of [SizingResult] outcomes into typed [CableStructuredCheck] records.
     * Derives check outcomes directly from the engine's authoritative typed status and failure code without recomputing.
     */
    fun evaluateEngineeringChecks(
        result: SizingResult,
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0
    ): CableEngineeringEvaluation {
        val isManual = !circuit.overrideCableSize.isNullOrBlank() &&
            !circuit.overrideCableSize.equals("AUTO", ignoreCase = true)
        val selectionSource = if (isManual) "MANUAL_OVERRIDE" else "AUTO"

        val engineStatus = result.resultStatus
        val failureCode = result.primaryFailureCode

        val isEnginePass = engineStatus == SizingResultStatus.PASS && failureCode == null

        val isAmpacityFail = !isEnginePass && (
            failureCode == CableFailureCode.AMPACITY_FAIL ||
            failureCode == CableFailureCode.MULTIPLE_CONSTRAINTS ||
            !result.isAmpacityOk
        )
        val isVdFail = !isEnginePass && (
            failureCode == CableFailureCode.VOLTAGE_DROP_FAIL ||
            failureCode == CableFailureCode.CUMULATIVE_VOLTAGE_DROP_FAIL ||
            failureCode == CableFailureCode.MULTIPLE_CONSTRAINTS ||
            !result.isVoltageDropOk
        )

        val maxAllowedVdPct = circuit.maxVDpct * 100.0

        val checks = mutableListOf<CableStructuredCheck>()

        // 1. Ampacity Check
        val ampacityStatus = if (isAmpacityFail) CableCheckStatus.FAIL else CableCheckStatus.PASS
        checks.add(
            CableStructuredCheck(
                checkCode = CableCheckCode.AMPACITY,
                checkName = "Ampacity & Device Protection",
                status = ampacityStatus,
                actualValue = if (result.capacity_derated_Iz_A > 0.0) EngineeringValue.ofDecimal(result.capacity_derated_Iz_A, EngineeringUnit.AMPERE) else EngineeringValue.missing(),
                limitValue = if (result.deviceSize_A > 0.0) EngineeringValue.ofDecimal(result.deviceSize_A, EngineeringUnit.AMPERE) else EngineeringValue.missing(),
                actualDisplay = if (result.capacity_derated_Iz_A > 0.0) "${result.capacity_derated_Iz_A} A" else "N/A",
                limitDisplay = if (result.deviceSize_A > 0.0) "${result.deviceSize_A} A" else "N/A",
                unit = "A",
                message = if (ampacityStatus == CableCheckStatus.PASS) {
                    "Derated cable capacity (${result.capacity_derated_Iz_A} A) safely protects continuous design current (${result.Ib_A} A) and device rating (${result.deviceSize_A} A)."
                } else {
                    "Derated cable capacity (${result.capacity_derated_Iz_A} A) does not satisfy protection/ampacity constraint for device rating (${result.deviceSize_A} A)."
                }
            )
        )

        // 2. Local Voltage Drop Check
        val isCumulativeOnlyFailure = failureCode == CableFailureCode.CUMULATIVE_VOLTAGE_DROP_FAIL || (isVdFail && parentCumulativeVD > 0.0)
        val localVdStatus = if (isVdFail && !isCumulativeOnlyFailure) CableCheckStatus.FAIL else CableCheckStatus.PASS
        checks.add(
            CableStructuredCheck(
                checkCode = CableCheckCode.VOLTAGE_DROP,
                checkName = "Local Voltage Drop",
                status = localVdStatus,
                actualValue = EngineeringValue.ofDecimal(result.vdPct, EngineeringUnit.PERCENT),
                limitValue = EngineeringValue.ofDecimal(maxAllowedVdPct, EngineeringUnit.PERCENT),
                actualDisplay = String.format(Locale.US, "%.2f%%", result.vdPct),
                limitDisplay = String.format(Locale.US, "%.2f%%", maxAllowedVdPct),
                unit = "%",
                message = if (localVdStatus == CableCheckStatus.PASS) {
                    "Local voltage drop (${String.format(Locale.US, "%.2f", result.vdPct)}%) is within limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                } else {
                    "Local voltage drop (${String.format(Locale.US, "%.2f", result.vdPct)}%) exceeds limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
            )
        )

        // 3. Cumulative Voltage Drop Check
        val cumulativeVdStatus = if (isVdFail) CableCheckStatus.FAIL else CableCheckStatus.PASS
        checks.add(
            CableStructuredCheck(
                checkCode = CableCheckCode.CUMULATIVE_VOLTAGE_DROP,
                checkName = "Cumulative Voltage Drop",
                status = cumulativeVdStatus,
                actualValue = EngineeringValue.ofDecimal(result.cumulativeVDpct, EngineeringUnit.PERCENT),
                limitValue = EngineeringValue.ofDecimal(maxAllowedVdPct, EngineeringUnit.PERCENT),
                actualDisplay = String.format(Locale.US, "%.2f%%", result.cumulativeVDpct),
                limitDisplay = String.format(Locale.US, "%.2f%%", maxAllowedVdPct),
                unit = "%",
                message = if (cumulativeVdStatus == CableCheckStatus.PASS) {
                    "Cumulative voltage drop from supply (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}%) is within limit."
                } else {
                    "Cumulative voltage drop (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}%) exceeds limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
            )
        )

        // Map engine status to structured status
        val overallStatus = when (engineStatus) {
            SizingResultStatus.PASS -> if (isEnginePass) CableCheckStatus.PASS else CableCheckStatus.FAIL
            SizingResultStatus.WARNING -> CableCheckStatus.WARNING
            SizingResultStatus.FAIL -> CableCheckStatus.FAIL
            SizingResultStatus.INCOMPLETE -> CableCheckStatus.INCOMPLETE
            SizingResultStatus.ERROR -> CableCheckStatus.FAIL
        }

        val governingCode: CableCheckCode?
        val governingReason: String

        if (!isEnginePass) {
            when (failureCode) {
                CableFailureCode.MULTIPLE_CONSTRAINTS -> {
                    governingCode = null // Multiple constraints
                    governingReason = result.failReason.ifEmpty { "Multiple engineering constraints failed." }
                }
                CableFailureCode.AMPACITY_FAIL -> {
                    governingCode = CableCheckCode.AMPACITY
                    governingReason = result.failReason.ifEmpty { "Ampacity constraint Ib <= In <= Iz is not satisfied." }
                }
                CableFailureCode.CUMULATIVE_VOLTAGE_DROP_FAIL -> {
                    governingCode = CableCheckCode.CUMULATIVE_VOLTAGE_DROP
                    governingReason = result.failReason.ifEmpty { "Cumulative voltage drop exceeds allowed limit." }
                }
                CableFailureCode.VOLTAGE_DROP_FAIL -> {
                    governingCode = CableCheckCode.VOLTAGE_DROP
                    governingReason = result.failReason.ifEmpty { "Local voltage drop exceeds allowed limit." }
                }
                CableFailureCode.CATALOG_MISSING -> {
                    governingCode = CableCheckCode.CATALOG_AVAILABILITY
                    governingReason = result.failReason.ifEmpty { "Selected cable specification is not available in catalog." }
                }
                CableFailureCode.DERATING_INVALID -> {
                    governingCode = CableCheckCode.INSTALLATION_DERATING
                    governingReason = result.failReason.ifEmpty { "Total derating factor is zero or invalid." }
                }
                CableFailureCode.INCOMPLETE_INPUT -> {
                    governingCode = CableCheckCode.INCOMPLETE_INPUT
                    governingReason = result.failReason.ifEmpty { "Missing required engineering inputs." }
                }
                CableFailureCode.INDETERMINATE -> {
                    governingCode = CableCheckCode.INDETERMINATE
                    governingReason = result.failReason.ifEmpty { "One or more engineering checks failed." }
                }
                null -> {
                    // Fail without code maps to INDETERMINATE, never MULTIPLE_CONSTRAINTS
                    governingCode = CableCheckCode.INDETERMINATE
                    governingReason = result.failReason.ifEmpty { "Engineering failure without typed reason code." }
                }
            }
        } else {
            governingCode = null
            governingReason = if (isManual) {
                "Manual override size ${result.cableSize_mm2} satisfies all authoritative engineering constraints."
            } else {
                "All authoritative engineering checks passed (Ampacity, Voltage Drop, Cumulative Voltage Drop)."
            }
        }

        return CableEngineeringEvaluation(
            sizingResult = result,
            overallStatus = overallStatus,
            checks = checks,
            governingCheckCode = governingCode,
            governingReason = governingReason,
            selectionSource = selectionSource
        )
    }

    /**
     * Suspend wrapper for background execution off the UI thread.
     */
    suspend fun calculateSingleCircuitAsync(
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0,
        upstreamCircuits: List<CircuitEntity> = emptyList(),
        transformers: List<TransformerEntity> = emptyList()
    ): SizingResult = withContext(Dispatchers.Default) {
        calculateSingleCircuit(circuit, parentCumulativeVD, upstreamCircuits, transformers)
    }
}
