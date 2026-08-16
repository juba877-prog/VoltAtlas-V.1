package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.engine.CoordinationEngine
import com.example.engine.CoordinationResult
import com.example.engine.SizingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service dedicated to orchestrating selectivity and breaker coordination calculations.
 * Wraps [CoordinationEngine] without altering any underlying electrical formulas or IEC 1.6:1/2:1 rules.
 */
object CoordinationCalculationService {

    /**
     * Evaluates coordination between a downstream circuit and its verified upstream protection device or transformer incomer.
     */
    fun calculateCoordination(
        downstreamCircuit: CircuitEntity,
        downstreamResult: SizingResult,
        upstreamName: String,
        upstreamRating: Double
    ): CoordinationResult {
        return CoordinationEngine.checkCoordination(
            downstreamCircuit = downstreamCircuit,
            downstreamResult = downstreamResult,
            upstreamName = upstreamName,
            upstreamRating = upstreamRating
        )
    }

    /**
     * Builds a typed, explicit missing-dependency coordination result.
     * Never converts missing upstream information into a false PASS.
     */
    fun buildMissingCoordinationResult(
        circuit: CircuitEntity,
        reason: String = "Upstream rating missing or zero"
    ): CoordinationResult {
        return CoordinationResult(
            downstreamCircuitId = circuit.id,
            downstreamName = circuit.circuitName,
            upstreamName = "N/A",
            ratio = 0.0,
            status = "WARNING",
            reason = reason
        )
    }

    /**
     * Suspend wrapper for background coordination evaluation off the UI thread.
     */
    suspend fun calculateCoordinationAsync(
        downstreamCircuit: CircuitEntity,
        downstreamResult: SizingResult,
        upstreamName: String,
        upstreamRating: Double
    ): CoordinationResult = withContext(Dispatchers.Default) {
        calculateCoordination(
            downstreamCircuit = downstreamCircuit,
            downstreamResult = downstreamResult,
            upstreamName = upstreamName,
            upstreamRating = upstreamRating
        )
    }
}
