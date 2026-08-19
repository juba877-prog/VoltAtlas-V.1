package com.example.engine.coordinator

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.BuswayEngine
import com.example.engine.CableSizingEngine
import com.example.engine.SizingResult
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
    INCOMPLETE_INPUT
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
                failReason = "Missing source transformer '${normalized.transformerId}' in this project."
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
     * Derives check outcomes directly from the engine's authoritative status and failure reasons without recomputing.
     */
    fun evaluateEngineeringChecks(
        result: SizingResult,
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0
    ): CableEngineeringEvaluation {
        val isManual = !circuit.overrideCableSize.isNullOrBlank() &&
            !circuit.overrideCableSize.equals("AUTO", ignoreCase = true)
        val selectionSource = if (isManual) "MANUAL_OVERRIDE" else "AUTO"

        val isEnginePass = result.status.contains("PASS", ignoreCase = true) && result.failReason.isBlank()
        val failReason = result.failReason.trim()

        val isAmpacityFail = !isEnginePass && failReason.contains("Ampacity", ignoreCase = true)
        val isVdFail = !isEnginePass && (
            failReason.contains("voltage drop", ignoreCase = true) ||
            failReason.contains("voltage-drop", ignoreCase = true)
        )
        val isCatalogFail = !isEnginePass && (
            failReason.contains("Catalog", ignoreCase = true) ||
            failReason.contains("not found in catalog", ignoreCase = true) ||
            failReason.contains("does not exist for", ignoreCase = true)
        )
        val isDeratingFail = !isEnginePass && failReason.contains("derating", ignoreCase = true)
        val isIncompleteFail = !isEnginePass && (
            failReason.contains("Missing source transformer", ignoreCase = true) ||
            failReason.contains("Voltage must be", ignoreCase = true) ||
            failReason.contains("Load kVA", ignoreCase = true) ||
            failReason.contains("Parallel runs", ignoreCase = true) ||
            failReason.contains("Power factor", ignoreCase = true) ||
            failReason.contains("Cable length", ignoreCase = true)
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
        val isCumulativeOnlyFailure = isVdFail && parentCumulativeVD > 0.0
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

        // Determine overall status & governing constraint code from engine outcomes
        val overallStatus = if (isEnginePass) CableCheckStatus.PASS else CableCheckStatus.FAIL
        val governingCode: CableCheckCode?
        val governingReason: String

        if (!isEnginePass) {
            when {
                isAmpacityFail && isVdFail -> {
                    governingCode = null // Multiple constraints
                    governingReason = failReason
                }
                isAmpacityFail -> {
                    governingCode = CableCheckCode.AMPACITY
                    governingReason = failReason.ifEmpty { "Ampacity constraint Ib <= In <= Iz is not satisfied." }
                }
                isVdFail -> {
                    governingCode = if (isCumulativeOnlyFailure) {
                        CableCheckCode.CUMULATIVE_VOLTAGE_DROP
                    } else {
                        CableCheckCode.VOLTAGE_DROP
                    }
                    governingReason = failReason.ifEmpty { "Voltage drop exceeds allowed limit." }
                }
                isCatalogFail -> {
                    governingCode = CableCheckCode.CATALOG_AVAILABILITY
                    governingReason = failReason.ifEmpty { "Selected cable specification is not available in catalog." }
                }
                isDeratingFail -> {
                    governingCode = CableCheckCode.INSTALLATION_DERATING
                    governingReason = failReason.ifEmpty { "Total derating factor is zero or invalid." }
                }
                isIncompleteFail -> {
                    governingCode = CableCheckCode.INCOMPLETE_INPUT
                    governingReason = failReason.ifEmpty { "Missing required engineering inputs." }
                }
                else -> {
                    governingCode = null
                    governingReason = failReason.ifEmpty { "One or more engineering checks failed." }
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
