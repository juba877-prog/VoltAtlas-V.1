package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.SizingResult
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result data container for bulk project cable sizing.
 */
data class CableSizingProjectResult(
    val sizingResults: Map<Int, SizingResult>,
    val circuitParents: Map<Int, Int>,
    val resolvedCircuits: List<CircuitEntity>
)

/**
 * Pure calculation coordinator for engineering sizing calculations.
 *
 * Responsibilities:
 * - Scope resolution and dependency resolution (parent cumulative voltage drop)
 * - Result caching and freshness verification
 * - Invoking [CableCalculationService]
 * - Providing a unified calculation API for both ViewModel and future AI tool layers
 */
object EngineeringCalculationCoordinator {

    private val sizingCache = ConcurrentHashMap<Int, Pair<Int, SizingResult>>()

    /**
     * Clears all cached calculation results.
     */
    fun clearCache() {
        sizingCache.clear()
    }

    /**
     * Invalidates cache for a specific circuit ID.
     */
    fun invalidateCircuit(circuitId: Int) {
        sizingCache.remove(circuitId)
    }

    /**
     * Computes an input signature for freshness validation.
     */
    private fun computeSignature(circuit: CircuitEntity, parentCumulativeVD: Double, transformers: List<TransformerEntity>): Int {
        val txIds = transformers.map { it.transformerId }.sorted().joinToString(",")
        return Objects.hash(
            circuit.circuitName,
            circuit.loadKVA,
            circuit.fedFrom,
            circuit.transformerId,
            circuit.loadType,
            circuit.phases,
            circuit.voltageV,
            circuit.pf,
            circuit.safetyFactor,
            circuit.manufacturer,
            circuit.cableType,
            circuit.installation,
            circuit.parallelRuns,
            circuit.airTempC,
            circuit.gndTempC,
            circuit.burialDepth_m,
            circuit.soilRes,
            circuit.noCircuits,
            circuit.formation,
            circuit.spacing,
            circuit.length_m,
            circuit.maxVDpct,
            circuit.overrideDeviceType,
            circuit.overrideDeviceSize,
            circuit.overrideCableSize,
            circuit.isHarmonic,
            circuit.upsBacked,
            circuit.demandFactor,
            circuit.generatorIncluded,
            circuit.atsIncluded,
            circuit.feederType,
            parentCumulativeVD,
            txIds
        )
    }

    /**
     * Resolves the upstream parent chain for a targeted circuit.
     */
    fun resolveAncestorChain(
        circuit: CircuitEntity,
        allCircuits: List<CircuitEntity>
    ): List<CircuitEntity> {
        val ancestors = mutableListOf<CircuitEntity>()
        val visited = mutableSetOf<Int>()
        var current = circuit

        while (true) {
            visited.add(current.id)
            val fedLower = current.fedFrom.trim().lowercase()
            if (fedLower in listOf("transformer", "ats", "generator", "grid", "source", "main", "")) {
                break
            }

            val parent = (if (current.parentId != null) allCircuits.find { it.id == current.parentId } else null)
                ?: allCircuits.find { it.circuitName.equals(current.fedFrom.trim(), ignoreCase = true) }

            if (parent == null || parent.id == current.id || visited.contains(parent.id)) {
                break
            }

            ancestors.add(0, parent)
            current = parent
        }

        return ancestors
    }

    /**
     * Targeted, single-circuit cable sizing calculation.
     * Resolves parent dependency chain, checks freshness cache, and computes sizing.
     */
    fun calculateCableSizing(
        circuit: CircuitEntity,
        allCircuits: List<CircuitEntity>,
        transformers: List<TransformerEntity> = emptyList()
    ): SizingResult {
        val ancestors = resolveAncestorChain(circuit, allCircuits)
        var cumulativeVD = 0.0

        for (ancestor in ancestors) {
            val ancestorSig = computeSignature(ancestor, cumulativeVD, transformers)
            val cached = sizingCache[ancestor.id]
            val ancestorResult = if (cached != null && cached.first == ancestorSig) {
                cached.second
            } else {
                val fresh = CableCalculationService.calculateSingleCircuit(
                    circuit = ancestor,
                    parentCumulativeVD = cumulativeVD,
                    upstreamCircuits = ancestors,
                    transformers = transformers
                )
                sizingCache[ancestor.id] = ancestorSig to fresh
                fresh
            }
            cumulativeVD = ancestorResult.cumulativeVDpct
        }

        val targetSig = computeSignature(circuit, cumulativeVD, transformers)
        val cachedTarget = sizingCache[circuit.id]
        if (cachedTarget != null && cachedTarget.first == targetSig) {
            return cachedTarget.second
        }

        val freshResult = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = cumulativeVD,
            upstreamCircuits = ancestors,
            transformers = transformers
        )
        sizingCache[circuit.id] = targetSig to freshResult
        return freshResult
    }

    /**
     * Executes bulk cable sizing for an entire project with topological dependency resolution.
     * Preserves exact existing calculations for [EngineeringViewModel] and bulk operations.
     */
    fun calculateProjectCableSizing(
        circuits: List<CircuitEntity>,
        transformers: List<TransformerEntity> = emptyList()
    ): CableSizingProjectResult {
        val normalizedCircuits = circuits.map { CableCalculationService.normalizeAutoOverride(it) }

        val sizingResults = mutableMapOf<Int, SizingResult>()
        val circuitParents = mutableMapOf<Int, Int>()

        val resolvedList = mutableListOf<CircuitEntity>()
        val pending = normalizedCircuits.toMutableList()
        var iterations = 0

        while (pending.isNotEmpty() && iterations < 100) {
            val next = pending.filter { circuit ->
                val fedLower = circuit.fedFrom.trim().lowercase()
                fedLower in listOf("transformer", "ats", "generator", "grid", "source", "main", "") ||
                    resolvedList.any { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }
            }

            if (next.isEmpty()) {
                resolvedList.addAll(pending)
                pending.clear()
            } else {
                pending.removeAll(next)
                next.forEach { circuit ->
                    val fedLower = circuit.fedFrom.trim().lowercase()
                    val parentCircuit = if (fedLower in listOf("transformer", "ats", "generator", "grid", "source", "main", "")) {
                        null
                    } else {
                        resolvedList.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }
                    }

                    if (parentCircuit != null) {
                        circuitParents[circuit.id] = parentCircuit.id
                    }

                    val parentVD = parentCircuit?.id?.let { sizingResults[it]?.cumulativeVDpct } ?: 0.0

                    val sig = computeSignature(circuit, parentVD, transformers)
                    val cached = sizingCache[circuit.id]
                    val sizingRes = if (cached != null && cached.first == sig) {
                        cached.second
                    } else {
                        val fresh = CableCalculationService.calculateSingleCircuit(
                            circuit = circuit,
                            parentCumulativeVD = parentVD,
                            upstreamCircuits = resolvedList,
                            transformers = transformers
                        )
                        sizingCache[circuit.id] = sig to fresh
                        fresh
                    }

                    sizingResults[circuit.id] = sizingRes
                    resolvedList.add(circuit)
                }
            }
            iterations++
        }

        return CableSizingProjectResult(
            sizingResults = sizingResults,
            circuitParents = circuitParents,
            resolvedCircuits = resolvedList
        )
    }

    /**
     * Suspend wrapper for bulk project sizing on background dispatcher.
     */
    suspend fun calculateProjectCableSizingAsync(
        circuits: List<CircuitEntity>,
        transformers: List<TransformerEntity> = emptyList()
    ): CableSizingProjectResult = withContext(Dispatchers.Default) {
        calculateProjectCableSizing(circuits, transformers)
    }
}
