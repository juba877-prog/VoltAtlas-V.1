package com.example.engine.coordinator

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
    val actualValue: String,
    val limitValue: String,
    val unit: String,
    val message: String,
    val isGoverning: Boolean = false
)

data class CableEngineeringEvaluation(
    val sizingResult: SizingResult,
    val overallStatus: CableCheckStatus,
    val checks: List<CableStructuredCheck>,
    val governingCheckCode: CableCheckCode?,
    val governingReason: String,
    val isManualOverride: Boolean = false
)

/**
 * Service dedicated to orchestrating single-circuit and bulk cable sizing calculations.
 * Wraps [CableSizingEngine] and [BuswayEngine] without altering any underlying formulas.
 * Authoritatively determines engineering checks and governing criteria in the engine/service layer.
 */
object CableCalculationService {

    private const val EPSILON = 1e-6

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
     *
     * @param circuit The circuit entity to size.
     * @param parentCumulativeVD Percentage cumulative voltage drop from upstream parent boards.
     * @param upstreamCircuits Resolved upstream circuit entities in the hierarchy path.
     * @param transformers List of available project transformers for validation.
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
     * Authoritative engine-layer evaluation of engineering checks.
     * Computes pass/fail status and governing criterion inside the deterministic service layer.
     */
    fun evaluateEngineeringChecks(
        result: SizingResult,
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0
    ): CableEngineeringEvaluation {
        val isManual = !circuit.overrideCableSize.isNullOrBlank() &&
            !circuit.overrideCableSize.equals("AUTO", ignoreCase = true)

        val ampacityOk = result.Ib_A <= result.deviceSize_A + EPSILON &&
            result.deviceSize_A <= result.capacity_derated_Iz_A + EPSILON &&
            result.capacity_derated_Iz_A > 0.0

        val maxAllowedVdPct = circuit.maxVDpct * 100.0
        val localVdOk = result.vdPct <= maxAllowedVdPct + EPSILON
        val cumulativeVdOk = result.cumulativeVDpct <= maxAllowedVdPct + EPSILON

        val checks = mutableListOf<CableStructuredCheck>()

        // 1. Ampacity Check
        checks.add(
            CableStructuredCheck(
                checkCode = CableCheckCode.AMPACITY,
                checkName = "Ampacity & Device Protection",
                status = if (ampacityOk) CableCheckStatus.PASS else CableCheckStatus.FAIL,
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
            CableStructuredCheck(
                checkCode = CableCheckCode.VOLTAGE_DROP,
                checkName = "Local Voltage Drop",
                status = if (localVdOk) CableCheckStatus.PASS else CableCheckStatus.FAIL,
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
            CableStructuredCheck(
                checkCode = CableCheckCode.CUMULATIVE_VOLTAGE_DROP,
                checkName = "Cumulative Voltage Drop",
                status = if (cumulativeVdOk) CableCheckStatus.PASS else CableCheckStatus.FAIL,
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

        // Determine Overall Status & Governing Criterion in Engine Layer
        val isEngineFail = result.status.contains("FAIL", ignoreCase = true) ||
            result.failReason.isNotBlank() ||
            !ampacityOk ||
            !localVdOk ||
            !cumulativeVdOk

        val overallStatus: CableCheckStatus
        val governingCode: CableCheckCode?
        val reason: String

        if (isEngineFail) {
            overallStatus = CableCheckStatus.FAIL
            val failReason = result.failReason.trim()

            when {
                failReason.contains("Catalog data is not available", ignoreCase = true) ||
                    failReason.contains("not found in catalog", ignoreCase = true) ||
                    failReason.contains("does not exist for", ignoreCase = true) -> {
                    governingCode = CableCheckCode.CATALOG_AVAILABILITY
                    reason = failReason.ifEmpty { "Selected cable specification is not available in catalog." }
                }
                failReason.contains("Voltage must be", ignoreCase = true) ||
                    failReason.contains("Load kVA", ignoreCase = true) ||
                    failReason.contains("Parallel runs", ignoreCase = true) ||
                    failReason.contains("Power factor", ignoreCase = true) ||
                    failReason.contains("Cable length", ignoreCase = true) ||
                    failReason.contains("Missing source transformer", ignoreCase = true) -> {
                    governingCode = CableCheckCode.INCOMPLETE_INPUT
                    reason = failReason
                }
                failReason.contains("derating", ignoreCase = true) -> {
                    governingCode = CableCheckCode.INSTALLATION_DERATING
                    reason = failReason
                }
                isManual -> {
                    governingCode = CableCheckCode.MANUAL_OVERRIDE
                    reason = "Manual override size ${result.cableSize_mm2} failed engineering checks: ${failReason.ifEmpty { "One or more constraints not met." }}"
                }
                !ampacityOk && (!localVdOk || !cumulativeVdOk) -> {
                    governingCode = null // Multiple failures -> maps to MULTIPLE_CONSTRAINTS
                    reason = "Candidate failed both ampacity (Iz=${result.capacity_derated_Iz_A}A < In=${result.deviceSize_A}A) and voltage drop (VD=${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}% > ${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
                !ampacityOk -> {
                    governingCode = CableCheckCode.AMPACITY
                    reason = "Cable derated ampacity (${result.capacity_derated_Iz_A} A) is below protective device rating (${result.deviceSize_A} A)."
                }
                !cumulativeVdOk && localVdOk -> {
                    governingCode = CableCheckCode.CUMULATIVE_VOLTAGE_DROP
                    reason = "Cumulative voltage drop (${String.format(Locale.US, "%.2f", result.cumulativeVDpct)}%) exceeds limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%) due to upstream drop."
                }
                !localVdOk -> {
                    governingCode = CableCheckCode.VOLTAGE_DROP
                    reason = "Local voltage drop (${String.format(Locale.US, "%.2f", result.vdPct)}%) exceeds maximum limit (${String.format(Locale.US, "%.2f", maxAllowedVdPct)}%)."
                }
                else -> {
                    governingCode = null
                    reason = failReason.ifEmpty { "Engine checks did not pass." }
                }
            }
        } else {
            overallStatus = CableCheckStatus.PASS
            if (isManual) {
                governingCode = CableCheckCode.MANUAL_OVERRIDE
                reason = "Conductor size ${result.cableSize_mm2} was configured via manual user override and satisfies all engineering constraints."
            } else {
                governingCode = null // All passed -> Option B truthful result
                reason = "All authoritative engineering checks passed (Ampacity, Voltage Drop, Cumulative Voltage Drop)."
            }
        }

        return CableEngineeringEvaluation(
            sizingResult = result,
            overallStatus = overallStatus,
            checks = checks,
            governingCheckCode = governingCode,
            governingReason = reason,
            isManualOverride = isManual
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
