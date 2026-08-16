package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.TransformerEngine
import com.example.engine.TransformerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service dedicated to orchestrating single-transformer and bulk transformer calculations.
 * Wraps [TransformerEngine] without altering any underlying electrical formulas.
 */
object TransformerCalculationService {

    /**
     * Executes calculation for a specific [TransformerEntity] and its associated project circuits.
     *
     * @param transformer The transformer entity to calculate.
     * @param circuits The project circuits (normalized or raw).
     */
    fun calculateSingleTransformer(
        transformer: TransformerEntity,
        circuits: List<CircuitEntity>
    ): TransformerResult {
        val normalizedCircuits = circuits.map { CableCalculationService.normalizeAutoOverride(it) }
        return TransformerEngine.calculate(transformer, normalizedCircuits)
    }

    /**
     * Finds and calculates a transformer by its [transformerId] from the provided lists.
     */
    fun calculateSingleTransformer(
        transformerId: String,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>
    ): TransformerResult {
        val tx = allTransformers.firstOrNull { it.transformerId.equals(transformerId.trim(), ignoreCase = true) }
            ?: TransformerEntity(
                transformerId = transformerId,
                status = "Inactive"
            )
        return calculateSingleTransformer(tx, allCircuits)
    }

    /**
     * Suspend wrapper for background calculation off the UI thread.
     */
    suspend fun calculateSingleTransformerAsync(
        transformer: TransformerEntity,
        circuits: List<CircuitEntity>
    ): TransformerResult = withContext(Dispatchers.Default) {
        calculateSingleTransformer(transformer, circuits)
    }

    /**
     * Suspend wrapper for background calculation by transformer ID.
     */
    suspend fun calculateSingleTransformerAsync(
        transformerId: String,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>
    ): TransformerResult = withContext(Dispatchers.Default) {
        calculateSingleTransformer(transformerId, allCircuits, allTransformers)
    }
}
