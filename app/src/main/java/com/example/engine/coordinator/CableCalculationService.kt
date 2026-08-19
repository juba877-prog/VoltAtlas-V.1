package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.BuswayEngine
import com.example.engine.CableSizingEngine
import com.example.engine.SizingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service dedicated to orchestrating single-circuit and bulk cable sizing calculations.
 * Wraps [CableSizingEngine] and [BuswayEngine] without altering any underlying formulas.
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
