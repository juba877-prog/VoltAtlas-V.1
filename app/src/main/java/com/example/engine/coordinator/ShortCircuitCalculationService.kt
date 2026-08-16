package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitEngine
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service dedicated to orchestrating single-circuit and bulk short-circuit calculations.
 * Wraps [ShortCircuitEngine] without altering any underlying electrical formulas.
 */
object ShortCircuitCalculationService {

    /**
     * Executes bulk short circuit calculations across all resolved project circuits.
     */
    fun calculateProjectShortCircuit(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>
    ): Map<Int, ShortCircuitResult> {
        return ShortCircuitEngine.calculate(
            resolvedCircuits = resolvedCircuits,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults
        )
    }

    /**
     * Executes targeted short circuit calculation for a single circuit given its upstream dependency branch.
     *
     * @param circuit The targeted circuit entity.
     * @param ancestors The upstream ancestor chain of circuits up to the feeding source.
     * @param circuitParents The parent mapping.
     * @param sizingResults Sizing results for the target and all upstream circuits.
     * @param transformerResults Transformer results for the feeding transformer.
     */
    fun calculateSingleCircuitShortCircuit(
        circuit: CircuitEntity,
        ancestors: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>
    ): ShortCircuitResult {
        val scopedCircuits = ancestors + circuit
        val resultMap = ShortCircuitEngine.calculate(
            resolvedCircuits = scopedCircuits,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults
        )
        return resultMap[circuit.id] ?: ShortCircuitResult(
            circuitId = circuit.id,
            sourceName = "N/A",
            cableMm2 = "-",
            iscKA = 0.0,
            iwKA = 0.0,
            deviceKA = 0.0,
            deviceCheck = "-",
            cableCheck = "-",
            marginPct = 0.0,
            status = "DATA MISSING",
            note = "Short circuit calculation failed or missing dependency"
        )
    }

    /**
     * Suspend wrapper for background bulk short circuit calculation off the UI thread.
     */
    suspend fun calculateProjectShortCircuitAsync(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>
    ): Map<Int, ShortCircuitResult> = withContext(Dispatchers.Default) {
        calculateProjectShortCircuit(
            resolvedCircuits = resolvedCircuits,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults
        )
    }

    /**
     * Suspend wrapper for background targeted short circuit calculation off the UI thread.
     */
    suspend fun calculateSingleCircuitShortCircuitAsync(
        circuit: CircuitEntity,
        ancestors: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>
    ): ShortCircuitResult = withContext(Dispatchers.Default) {
        calculateSingleCircuitShortCircuit(
            circuit = circuit,
            ancestors = ancestors,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults
        )
    }
}
