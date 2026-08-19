package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.AdsResult
import com.example.engine.CoordinationResult
import com.example.engine.ProtectionResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.ai.context.freshness.adapters.InMemoryProjectFreshnessDataProvider
import com.example.ai.context.freshness.adapters.ProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.freshness.internal.FreshnessLifecycleRecorder
import com.example.ai.context.freshness.internal.InMemoryCalculationFreshnessMetadataRegistry
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import kotlinx.coroutines.runBlocking

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
 * - Scope resolution and dependency resolution (parent cumulative voltage drop, transformer loading, short-circuit impedances, protection coordination, selectivity)
 * - Result caching and freshness verification
 * - Invoking [CableCalculationService], [TransformerCalculationService], [ShortCircuitCalculationService], [ProtectionCalculationService], and [CoordinationCalculationService]
 * - Providing a unified calculation API for both ViewModel and future AI tool layers
 * - Operational freshness metadata lifecycle recording (side-effect only, non-breaking)
 */
object EngineeringCalculationCoordinator {

    val freshnessRegistry = InMemoryCalculationFreshnessMetadataRegistry()
    var projectRevisionProvider: ProjectRevisionProvider? = null

    private fun getRecorder(circuits: List<CircuitEntity>, transformers: List<TransformerEntity>): FreshnessLifecycleRecorder {
        val dataProvider = InMemoryProjectFreshnessDataProvider(circuits, transformers)
        return FreshnessLifecycleRecorder(freshnessRegistry, dataProvider)
    }

    private fun getProjectRevision(projectId: String): Long {
        return try {
            projectRevisionProvider?.let { provider ->
                runBlocking { provider.getCurrentRevision(projectId).revision }
            } ?: 1L
        } catch (_: Exception) {
            1L
        }
    }

    fun clearProjectFreshness(projectId: String) {
        freshnessRegistry.clearProject(projectId)
    }

    fun getFreshnessProvider(
        customDataProvider: ProjectFreshnessDataProvider? = null,
        customRevisionProvider: ProjectRevisionProvider? = null
    ): CalculationFreshnessProvider {
        val revProvider = customRevisionProvider ?: projectRevisionProvider ?: object : ProjectRevisionProvider {
            override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
                return ProjectRevisionSnapshot(projectId, 1L, System.currentTimeMillis())
            }
        }
        val dataProvider = customDataProvider ?: InMemoryProjectFreshnessDataProvider()
        return DefaultCalculationFreshnessProvider(
            revisionProvider = revProvider,
            metadataRegistry = freshnessRegistry,
            projectDataProvider = dataProvider
        )
    }

    private val sizingCache = ConcurrentHashMap<Int, Pair<Int, SizingResult>>()
    private val transformerCache = ConcurrentHashMap<String, Pair<Int, TransformerResult>>()
    private val shortCircuitCache = ConcurrentHashMap<Int, Pair<Int, ShortCircuitResult>>()
    private val adsCache = ConcurrentHashMap<Int, Pair<Int, AdsResult>>()
    private val protectionCache = ConcurrentHashMap<Int, Pair<Int, ProtectionResult>>()
    private val coordinationCache = ConcurrentHashMap<Int, Pair<Int, CoordinationResult>>()

    /**
     * Clears all cached calculation results.
     */
    fun clearCache() {
        sizingCache.clear()
        transformerCache.clear()
        shortCircuitCache.clear()
        adsCache.clear()
        protectionCache.clear()
        coordinationCache.clear()
    }

    // -------------------------------------------------------------------------
    // Pure Read-Only Cache Accessors for ProjectContextProvider (Sub-Phase 3F)
    // -------------------------------------------------------------------------

    fun getCachedSizingResult(circuitId: Int): SizingResult? =
        sizingCache[circuitId]?.second

    fun getCachedTransformerResult(transformerId: String): TransformerResult? =
        transformerCache[transformerId]?.second

    fun getCachedShortCircuitResult(circuitId: Int): ShortCircuitResult? =
        shortCircuitCache[circuitId]?.second

    fun getCachedAdsResult(circuitId: Int): AdsResult? =
        adsCache[circuitId]?.second

    fun getCachedProtectionResult(circuitId: Int): ProtectionResult? =
        protectionCache[circuitId]?.second

    fun getCachedCoordinationResult(circuitId: Int): CoordinationResult? =
        coordinationCache[circuitId]?.second

    /**
     * Invalidates cache for a specific circuit ID across sizing, short circuit, ads, protection, and coordination caches.
     */
    fun invalidateCircuit(circuitId: Int) {
        sizingCache.remove(circuitId)
        shortCircuitCache.remove(circuitId)
        adsCache.remove(circuitId)
        protectionCache.remove(circuitId)
        coordinationCache.remove(circuitId)
    }

    /**
     * Invalidates cache for a specific transformer ID.
     */
    fun invalidateTransformer(transformerId: String) {
        transformerCache.remove(transformerId)
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
            val projectId = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = getProjectRevision(projectId)
            val status = if (cachedTarget.second.status.contains("FAIL")) "FAIL" else if (cachedTarget.second.status.contains("WARN")) "WARNING" else "PASS"
            try {
                runBlocking {
                    getRecorder(allCircuits, transformers).recordCableSizing(projectId, circuit.id, status, rev)
                }
            } catch (_: Exception) { }
            return cachedTarget.second
        }

        val projectId = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, circuit.id.toString(), CalculationDomain.CABLE_SIZING)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val freshResult = CableCalculationService.calculateSingleCircuit(
                circuit = circuit,
                parentCumulativeVD = cumulativeVD,
                upstreamCircuits = ancestors,
                transformers = transformers
            )
            sizingCache[circuit.id] = targetSig to freshResult
            val rev = getProjectRevision(projectId)
            val status = if (freshResult.status.contains("FAIL")) "FAIL" else if (freshResult.status.contains("WARN")) "WARNING" else "PASS"
            try {
                runBlocking {
                    getRecorder(allCircuits, transformers).recordCableSizing(projectId, circuit.id, status, rev)
                }
            } catch (_: Exception) { }
            return freshResult
        } catch (ex: Exception) {
            try {
                val rev = getProjectRevision(projectId)
                getRecorder(allCircuits, transformers).recordError(scope, ExecutionErrorCode.ENGINE_RUNTIME_ERROR, ex.message, rev)
            } catch (_: Exception) { }
            throw ex
        } finally {
            freshnessRegistry.finishExecution(scope)
        }
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

        val recorder = getRecorder(resolvedList, transformers)
        resolvedList.forEach { circuit ->
            val res = sizingResults[circuit.id]
            if (res != null) {
                val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
                val rev = getProjectRevision(pid)
                val status = if (res.status.contains("FAIL")) "FAIL" else if (res.status.contains("WARN")) "WARNING" else "PASS"
                try {
                    runBlocking {
                        recorder.recordCableSizing(pid, circuit.id, status, rev)
                    }
                } catch (_: Exception) { }
            }
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

    /**
     * Computes a cache signature for a transformer and its assigned circuits.
     */
    private fun computeTransformerSignature(transformer: TransformerEntity, circuits: List<CircuitEntity>): Int {
        val txIdClean = transformer.transformerId.trim().lowercase()
        val assignedCircuits = circuits.filter { c ->
            val cTxId = c.transformerId.trim().lowercase()
            if (cTxId.isNotEmpty()) {
                cTxId == txIdClean
            } else {
                val fedClean = c.fedFrom.trim().lowercase()
                fedClean == txIdClean || fedClean in listOf("transformer", "ats", "generator", "grid")
            }
        }
        val circuitsHash = assignedCircuits.map { c ->
            Objects.hash(
                c.id,
                c.circuitName,
                c.loadKVA,
                c.fedFrom,
                c.parentId,
                c.panelType,
                c.phases,
                c.assignedPhase,
                c.directLoadConfirmed
            )
        }.sorted()

        return Objects.hash(
            transformer.transformerId,
            transformer.transformerName,
            transformer.kVA,
            transformer.voltage,
            transformer.percentZ,
            transformer.xr,
            transformer.txType,
            transformer.sourceType,
            transformer.status,
            transformer.sizeMode,
            transformer.manualKVA,
            transformer.demandFactor,
            transformer.diversity,
            transformer.growth,
            transformer.safety,
            transformer.vectorGroup,
            transformer.z0OverZ1,
            transformer.pf,
            circuitsHash
        )
    }

    /**
     * Targeted single-transformer calculation by ID.
     */
    fun calculateTransformer(
        transformerId: String,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>
    ): TransformerResult {
        val tx = allTransformers.firstOrNull { it.transformerId.equals(transformerId.trim(), ignoreCase = true) }
            ?: TransformerEntity(transformerId = transformerId, status = "Inactive")
        return calculateTransformer(tx, allCircuits)
    }

    /**
     * Targeted single-transformer calculation for a given [TransformerEntity].
     */
    fun calculateTransformer(
        transformer: TransformerEntity,
        allCircuits: List<CircuitEntity>
    ): TransformerResult {
        val sig = computeTransformerSignature(transformer, allCircuits)
        val cached = transformerCache[transformer.transformerId]
        if (cached != null && cached.first == sig) {
            val pid = transformer.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    getRecorder(allCircuits, listOf(transformer)).recordTransformer(pid, transformer.transformerId, "PASS", rev)
                }
            } catch (_: Exception) { }
            return cached.second
        }

        val pid = transformer.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, transformer.transformerId, CalculationDomain.TRANSFORMER)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val freshResult = TransformerCalculationService.calculateSingleTransformer(transformer, allCircuits)
            transformerCache[transformer.transformerId] = sig to freshResult
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    getRecorder(allCircuits, listOf(transformer)).recordTransformer(pid, transformer.transformerId, "PASS", rev)
                }
            } catch (_: Exception) { }
            return freshResult
        } catch (ex: Exception) {
            try {
                val rev = getProjectRevision(pid)
                getRecorder(allCircuits, listOf(transformer)).recordError(scope, ExecutionErrorCode.ENGINE_RUNTIME_ERROR, ex.message, rev)
            } catch (_: Exception) { }
            throw ex
        } finally {
            freshnessRegistry.finishExecution(scope)
        }
    }

    /**
     * Bulk transformer calculation for all project transformers.
     */
    fun calculateProjectTransformers(
        circuits: List<CircuitEntity>,
        transformers: List<TransformerEntity>
    ): Map<String, TransformerResult> {
        val results = transformers.associate { transformer ->
            transformer.transformerId to calculateTransformer(transformer, circuits)
        }
        val recorder = getRecorder(circuits, transformers)
        transformers.forEach { tx ->
            val pid = tx.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    recorder.recordTransformer(pid, tx.transformerId, "PASS", rev)
                }
            } catch (_: Exception) { }
        }
        return results
    }

    /**
     * Suspend wrapper for bulk transformer calculation on background dispatcher.
     */
    suspend fun calculateProjectTransformersAsync(
        circuits: List<CircuitEntity>,
        transformers: List<TransformerEntity>
    ): Map<String, TransformerResult> = withContext(Dispatchers.Default) {
        calculateProjectTransformers(circuits, transformers)
    }

    /**
     * Computes a cache signature for short circuit calculation on a circuit.
     */
    private fun computeShortCircuitSignature(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        transformerResult: TransformerResult?,
        ancestorSizings: List<Pair<CircuitEntity, SizingResult?>>
    ): Int {
        val upstreamHash = ancestorSizings.map { (c, s) ->
            Objects.hash(
                c.id,
                c.length_m,
                c.parallelRuns,
                s?.rUsed,
                s?.xUsed
            )
        }

        return Objects.hash(
            circuit.id,
            circuit.length_m,
            circuit.parallelRuns,
            circuit.fedFrom,
            circuit.transformerId,
            sizingResult?.cableSize_mm2,
            sizingResult?.cableOD_mm,
            sizingResult?.rUsed,
            sizingResult?.xUsed,
            sizingResult?.kFactor,
            sizingResult?.clearingTime_s,
            sizingResult?.deviceBreakingKA,
            sizingResult?.deviceType,
            transformerResult?.voltage,
            transformerResult?.isc3Phase,
            transformerResult?.rt,
            transformerResult?.xt,
            upstreamHash
        )
    }

    /**
     * Targeted single-circuit short circuit calculation.
     * Resolves upstream ancestor branch and source transformer, then executes calculation.
     */
    fun calculateShortCircuit(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>
    ): ShortCircuitResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return ShortCircuitResult(
                circuitId = circuitId,
                sourceName = "N/A",
                cableMm2 = "-",
                iscKA = 0.0,
                iwKA = 0.0,
                deviceKA = 0.0,
                deviceCheck = "-",
                cableCheck = "-",
                marginPct = 0.0,
                status = "DATA MISSING",
                note = "Circuit $circuitId not found in project"
            )

        val ancestors = resolveAncestorChain(circuit, allCircuits)
        val branchCircuits = ancestors + circuit

        // Sizing for branch
        val branchSizings = mutableMapOf<Int, SizingResult>()
        for (item in branchCircuits) {
            branchSizings[item.id] = calculateCableSizing(item, allCircuits, allTransformers)
        }

        // Find feeding transformer with circular reference guard
        var curr = circuit
        var txResult: TransformerResult? = null
        val visitedTxLookup = mutableSetOf<Int>()
        while (true) {
            visitedTxLookup.add(curr.id)
            val fedLower = curr.fedFrom.trim().lowercase()
            if (fedLower in listOf("transformer", "ats", "generator", "grid") || curr.transformerId.isNotBlank()) {
                val txId = curr.transformerId
                if (txId.isNotBlank()) {
                    txResult = calculateTransformer(txId, allCircuits, allTransformers)
                    break
                }
            }
            val parent = (if (curr.parentId != null) allCircuits.find { it.id == curr.parentId } else null)
                ?: allCircuits.find { it.circuitName.equals(curr.fedFrom.trim(), ignoreCase = true) }
            if (parent == null || parent.id == curr.id || visitedTxLookup.contains(parent.id)) break
            curr = parent
        }

        val txMap = if (txResult != null && curr.transformerId.isNotBlank()) {
            mapOf(curr.transformerId to txResult)
        } else {
            emptyMap()
        }

        val ancestorPairs = ancestors.map { it to branchSizings[it.id] }
        val sig = computeShortCircuitSignature(
            circuit = circuit,
            sizingResult = branchSizings[circuit.id],
            transformerResult = txResult,
            ancestorSizings = ancestorPairs
        )

        val cached = shortCircuitCache[circuit.id]
        if (cached != null && cached.first == sig) {
            val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = getProjectRevision(pid)
            val status = if (cached.second.status.contains("FAIL") || cached.second.status.contains("MISSING")) "FAIL" else "PASS"
            try {
                runBlocking {
                    getRecorder(allCircuits, allTransformers).recordShortCircuit(pid, circuitId, status, rev)
                }
            } catch (_: Exception) { }
            return cached.second
        }

        val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.SHORT_CIRCUIT)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val parentsMap = mutableMapOf<Int, Int>()
            for (i in 1 until branchCircuits.size) {
                parentsMap[branchCircuits[i].id] = branchCircuits[i - 1].id
            }

            val fresh = ShortCircuitCalculationService.calculateSingleCircuitShortCircuit(
                circuit = circuit,
                ancestors = ancestors,
                circuitParents = parentsMap,
                sizingResults = branchSizings,
                transformerResults = txMap
            )
            shortCircuitCache[circuit.id] = sig to fresh
            val rev = getProjectRevision(pid)
            val status = if (fresh.status.contains("FAIL") || fresh.status.contains("MISSING")) "FAIL" else "PASS"
            try {
                runBlocking {
                    getRecorder(allCircuits, allTransformers).recordShortCircuit(pid, circuitId, status, rev)
                }
            } catch (_: Exception) { }
            return fresh
        } catch (ex: Exception) {
            try {
                val rev = getProjectRevision(pid)
                getRecorder(allCircuits, allTransformers).recordError(scope, ExecutionErrorCode.ENGINE_RUNTIME_ERROR, ex.message, rev)
            } catch (_: Exception) { }
            throw ex
        } finally {
            freshnessRegistry.finishExecution(scope)
        }
    }

    /**
     * Bulk short circuit calculation across all resolved circuits using pre-computed sizing and transformer results.
     */
    fun calculateProjectShortCircuit(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>
    ): Map<Int, ShortCircuitResult> {
        val results = mutableMapOf<Int, ShortCircuitResult>()
        val circuitsToCompute = mutableListOf<CircuitEntity>()

        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id]
            val ancestors = mutableListOf<CircuitEntity>()
            val visitedAncestors = mutableSetOf<Int>()
            var step = circuitParents[circuit.id]?.let { pId -> resolvedCircuits.find { it.id == pId } }
            while (step != null && !visitedAncestors.contains(step.id)) {
                visitedAncestors.add(step.id)
                ancestors.add(0, step)
                step = circuitParents[step.id]?.let { pId -> resolvedCircuits.find { it.id == pId } }
            }

            var curr = circuit
            var txResult: TransformerResult? = null
            val visitedTx = mutableSetOf<Int>()
            while (true) {
                visitedTx.add(curr.id)
                val fedLower = curr.fedFrom.trim().lowercase()
                if (fedLower in listOf("transformer", "ats", "generator", "grid")) {
                    txResult = transformerResults[curr.transformerId]
                    break
                }
                val parentId = circuitParents[curr.id] ?: break
                if (visitedTx.contains(parentId)) break
                curr = resolvedCircuits.find { it.id == parentId } ?: break
            }

            val ancestorPairs = ancestors.map { it to sizingResults[it.id] }
            val sig = computeShortCircuitSignature(circuit, sizing, txResult, ancestorPairs)
            val cached = shortCircuitCache[circuit.id]

            if (cached != null && cached.first == sig) {
                results[circuit.id] = cached.second
            } else {
                circuitsToCompute.add(circuit)
            }
        }

        if (circuitsToCompute.isNotEmpty()) {
            val freshMap = ShortCircuitCalculationService.calculateProjectShortCircuit(
                resolvedCircuits = resolvedCircuits,
                circuitParents = circuitParents,
                sizingResults = sizingResults,
                transformerResults = transformerResults
            )

            for (circuit in circuitsToCompute) {
                val fresh = freshMap[circuit.id] ?: continue
                val sizing = sizingResults[circuit.id]
                val ancestors = mutableListOf<CircuitEntity>()
                val visitedAncestors = mutableSetOf<Int>()
                var step = circuitParents[circuit.id]?.let { pId -> resolvedCircuits.find { it.id == pId } }
                while (step != null && !visitedAncestors.contains(step.id)) {
                    visitedAncestors.add(step.id)
                    ancestors.add(0, step)
                    step = circuitParents[step.id]?.let { pId -> resolvedCircuits.find { it.id == pId } }
                }
                var curr = circuit
                var txResult: TransformerResult? = null
                val visitedTx = mutableSetOf<Int>()
                while (true) {
                    visitedTx.add(curr.id)
                    val fedLower = curr.fedFrom.trim().lowercase()
                    if (fedLower in listOf("transformer", "ats", "generator", "grid")) {
                        txResult = transformerResults[curr.transformerId]
                        break
                    }
                    val parentId = circuitParents[curr.id] ?: break
                    if (visitedTx.contains(parentId)) break
                    curr = resolvedCircuits.find { it.id == parentId } ?: break
                }
                val ancestorPairs = ancestors.map { it to sizingResults[it.id] }
                val sig = computeShortCircuitSignature(circuit, sizing, txResult, ancestorPairs)
                shortCircuitCache[circuit.id] = sig to fresh
                results[circuit.id] = fresh
            }
        }

        val txList = transformerResults.keys.map { id -> TransformerEntity(transformerId = id) }
        val recorder = getRecorder(resolvedCircuits, txList)
        resolvedCircuits.forEach { c ->
            val sc = results[c.id]
            if (sc != null) {
                val pid = c.projectId.ifEmpty { "DEFAULT_PROJECT" }
                val rev = getProjectRevision(pid)
                val status = if (sc.status.contains("FAIL") || sc.status.contains("MISSING")) "FAIL" else "PASS"
                try {
                    runBlocking {
                        recorder.recordShortCircuit(pid, c.id, status, rev)
                    }
                } catch (_: Exception) { }
            }
        }

        return results
    }

    /**
     * Suspend wrapper for bulk project short circuit calculation on background dispatcher.
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
     * Computes a cache signature for ADS calculation.
     */
    private fun computeAdsSignature(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        ze_ohm: Double
    ): Int {
        return Objects.hash(
            circuit.id,
            circuit.length_m,
            circuit.overrideDeviceType,
            circuit.cableType,
            sizingResult?.cableSize_mm2,
            sizingResult?.earthCPC_mm2,
            sizingResult?.cableMaterial,
            sizingResult?.deviceSize_A,
            ze_ohm
        )
    }

    /**
     * Computes a cache signature for full Protection evaluation.
     */
    private fun computeProtectionSignature(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        shortCircuitResult: ShortCircuitResult?,
        upstreamIn_A: Double,
        upstreamName: String
    ): Int {
        return Objects.hash(
            circuit.id,
            circuit.overrideDeviceType,
            circuit.loadKVA,
            sizingResult?.Ib_A,
            sizingResult?.deviceSize_A,
            sizingResult?.capacity_derated_Iz_A,
            sizingResult?.deviceBreakingKA,
            sizingResult?.cableSize_mm2,
            sizingResult?.kFactor,
            sizingResult?.clearingTime_s,
            shortCircuitResult?.iscKA,
            upstreamIn_A,
            upstreamName
        )
    }

    /**
     * Targeted single-circuit ADS calculation.
     */
    fun calculateAds(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity> = emptyList(),
        ze_ohm: Double = 0.01
    ): AdsResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return ProtectionCalculationService.buildMissingAdsResult(
                CircuitEntity(id = circuitId, circuitName = "Circuit $circuitId", projectId = "")
            )

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers)
        val sig = computeAdsSignature(circuit, sizingResult, ze_ohm)

        val cached = adsCache[circuit.id]
        if (cached != null && cached.first == sig) {
            return cached.second
        }

        val fresh = ProtectionCalculationService.calculateAds(circuit, sizingResult, ze_ohm)
        adsCache[circuit.id] = sig to fresh
        return fresh
    }

    /**
     * Targeted single-circuit Protection evaluation.
     */
    fun calculateProtection(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>
    ): ProtectionResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return ProtectionCalculationService.buildMissingProtectionResult(
                CircuitEntity(id = circuitId, circuitName = "Circuit $circuitId", projectId = "")
            )

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers)
        val scResult = calculateShortCircuit(circuitId, allCircuits, allTransformers)

        // Find parent device size or transformer FLC with cycle guard
        val parent = (if (circuit.parentId != null) allCircuits.find { it.id == circuit.parentId } else null)
            ?: allCircuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }

        val upstreamIn: Double
        val upstreamName: String
        if (parent != null && parent.id != circuit.id) {
            val parentSizing = calculateCableSizing(parent, allCircuits, allTransformers)
            upstreamIn = parentSizing.deviceSize_A
            upstreamName = parent.circuitName
        } else {
            val txResult = if (circuit.transformerId.isNotBlank()) {
                calculateTransformer(circuit.transformerId, allCircuits, allTransformers)
            } else null
            upstreamIn = txResult?.flc ?: 0.0
            upstreamName = if (circuit.transformerId.isNotBlank()) "Incomer (${circuit.transformerId})" else "Incomer"
        }

        val sig = computeProtectionSignature(circuit, sizingResult, scResult, upstreamIn, upstreamName)
        val cached = protectionCache[circuit.id]
        if (cached != null && cached.first == sig) {
            val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    getRecorder(allCircuits, allTransformers).recordProtectionAds(pid, circuitId, cached.second.finalStatus, rev)
                }
            } catch (_: Exception) { }
            return cached.second
        }

        val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.PROTECTION_ADS)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val fresh = ProtectionCalculationService.evaluateProtection(
                circuit = circuit,
                sizingResult = sizingResult,
                shortCircuitResult = scResult,
                upstreamIn_A = upstreamIn,
                upstreamName = upstreamName
            )
            protectionCache[circuit.id] = sig to fresh
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    getRecorder(allCircuits, allTransformers).recordProtectionAds(pid, circuitId, fresh.finalStatus, rev)
                }
            } catch (_: Exception) { }
            return fresh
        } catch (ex: Exception) {
            try {
                val rev = getProjectRevision(pid)
                getRecorder(allCircuits, allTransformers).recordError(scope, ExecutionErrorCode.ENGINE_RUNTIME_ERROR, ex.message, rev)
            } catch (_: Exception) { }
            throw ex
        } finally {
            freshnessRegistry.finishExecution(scope)
        }
    }

    /**
     * Bulk project ADS calculation.
     */
    fun calculateProjectAds(
        resolvedCircuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>,
        ze_ohm: Double = 0.01
    ): Map<Int, AdsResult> {
        val results = mutableMapOf<Int, AdsResult>()
        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id] ?: continue
            val sig = computeAdsSignature(circuit, sizing, ze_ohm)
            val cached = adsCache[circuit.id]
            if (cached != null && cached.first == sig) {
                results[circuit.id] = cached.second
            } else {
                val fresh = ProtectionCalculationService.calculateAds(circuit, sizing, ze_ohm)
                adsCache[circuit.id] = sig to fresh
                results[circuit.id] = fresh
            }
        }
        return results
    }

    /**
     * Bulk project Protection evaluation.
     */
    fun calculateProjectProtection(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        shortCircuitResults: Map<Int, ShortCircuitResult>,
        transformerResults: Map<String, TransformerResult>,
        allCircuits: List<CircuitEntity> = resolvedCircuits
    ): Map<Int, ProtectionResult> {
        val results = mutableMapOf<Int, ProtectionResult>()
        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id] ?: continue
            val sc = shortCircuitResults[circuit.id]
            val parentId = circuitParents[circuit.id]
            val parentSizing = if (parentId != null) sizingResults[parentId] else null
            val upstreamIn = parentSizing?.deviceSize_A ?: (transformerResults[circuit.transformerId]?.flc ?: 0.0)
            val parentName = if (parentId != null) {
                (allCircuits.find { it.id == parentId }?.circuitName ?: "Parent Board")
            } else {
                "Incomer (${circuit.transformerId})"
            }

            val sig = computeProtectionSignature(circuit, sizing, sc, upstreamIn, parentName)
            val cached = protectionCache[circuit.id]
            if (cached != null && cached.first == sig) {
                results[circuit.id] = cached.second
            } else {
                val fresh = ProtectionCalculationService.evaluateProtection(
                    circuit = circuit,
                    sizingResult = sizing,
                    shortCircuitResult = sc,
                    upstreamIn_A = upstreamIn,
                    upstreamName = parentName
                )
                protectionCache[circuit.id] = sig to fresh
                results[circuit.id] = fresh
            }
        }

        val txList = transformerResults.keys.map { id -> TransformerEntity(transformerId = id) }
        val recorder = getRecorder(resolvedCircuits, txList)
        resolvedCircuits.forEach { c ->
            val prot = results[c.id]
            if (prot != null) {
                val pid = c.projectId.ifEmpty { "DEFAULT_PROJECT" }
                val rev = getProjectRevision(pid)
                try {
                    runBlocking {
                        recorder.recordProtectionAds(pid, c.id, prot.finalStatus, rev)
                    }
                } catch (_: Exception) { }
            }
        }

        return results
    }

    /**
     * Suspend wrapper for bulk ADS calculation on background dispatcher.
     */
    suspend fun calculateProjectAdsAsync(
        resolvedCircuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>,
        ze_ohm: Double = 0.01
    ): Map<Int, AdsResult> = withContext(Dispatchers.Default) {
        calculateProjectAds(resolvedCircuits, sizingResults, ze_ohm)
    }

    /**
     * Suspend wrapper for bulk Protection calculation on background dispatcher.
     */
    suspend fun calculateProjectProtectionAsync(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        shortCircuitResults: Map<Int, ShortCircuitResult>,
        transformerResults: Map<String, TransformerResult>,
        allCircuits: List<CircuitEntity> = resolvedCircuits
    ): Map<Int, ProtectionResult> = withContext(Dispatchers.Default) {
        calculateProjectProtection(
            resolvedCircuits = resolvedCircuits,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            shortCircuitResults = shortCircuitResults,
            transformerResults = transformerResults,
            allCircuits = allCircuits
        )
    }

    /**
     * Computes a cache signature for breaker coordination evaluation.
     */
    private fun computeCoordinationSignature(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        upstreamName: String,
        upstreamRating: Double
    ): Int {
        return Objects.hash(
            circuit.id,
            circuit.circuitName,
            sizingResult?.deviceSize_A,
            upstreamName,
            upstreamRating
        )
    }

    /**
     * Targeted single-circuit Coordination evaluation.
     * Resolves immediate upstream parent breaker rating or transformer incomer FLC.
     */
    fun calculateCoordination(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity> = emptyList()
    ): CoordinationResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return CoordinationCalculationService.buildMissingCoordinationResult(
                CircuitEntity(id = circuitId, circuitName = "Circuit $circuitId", projectId = ""),
                "Circuit $circuitId not found in project"
            )

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers)

        // Resolve upstream parent device or transformer incomer with cycle protection
        val visited = mutableSetOf<Int>()
        visited.add(circuit.id)

        val parent = (if (circuit.parentId != null) allCircuits.find { it.id == circuit.parentId } else null)
            ?: allCircuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }

        val upstreamName: String
        val upstreamRating: Double

        if (parent != null && parent.id != circuit.id && !visited.contains(parent.id)) {
            val parentSizing = calculateCableSizing(parent, allCircuits, allTransformers)
            upstreamRating = parentSizing.deviceSize_A
            upstreamName = parent.circuitName
        } else if (circuit.fedFrom.equals("Transformer", ignoreCase = true) || circuit.transformerId.isNotBlank()) {
            val tx = allTransformers.firstOrNull { it.transformerId.equals(circuit.transformerId.trim(), ignoreCase = true) }
            if (tx != null) {
                val txResult = calculateTransformer(tx, allCircuits)
                upstreamRating = txResult.flc
                upstreamName = "Incomer (${tx.transformerId})"
            } else {
                return CoordinationCalculationService.buildMissingCoordinationResult(
                    circuit,
                    "Source transformer '${circuit.transformerId}' not found in project"
                )
            }
        } else {
            return CoordinationCalculationService.buildMissingCoordinationResult(
                circuit,
                "Upstream parent device or source transformer not found"
            )
        }

        val sig = computeCoordinationSignature(circuit, sizingResult, upstreamName, upstreamRating)
        val cached = coordinationCache[circuit.id]
        if (cached != null && cached.first == sig) {
            val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    getRecorder(allCircuits, allTransformers).recordCoordination(pid, circuitId, cached.second.status, rev)
                }
            } catch (_: Exception) { }
            return cached.second
        }

        val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.COORDINATION)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val fresh = CoordinationCalculationService.calculateCoordination(
                downstreamCircuit = circuit,
                downstreamResult = sizingResult,
                upstreamName = upstreamName,
                upstreamRating = upstreamRating
            )
            coordinationCache[circuit.id] = sig to fresh
            val rev = getProjectRevision(pid)
            try {
                runBlocking {
                    getRecorder(allCircuits, allTransformers).recordCoordination(pid, circuitId, fresh.status, rev)
                }
            } catch (_: Exception) { }
            return fresh
        } catch (ex: Exception) {
            try {
                val rev = getProjectRevision(pid)
                getRecorder(allCircuits, allTransformers).recordError(scope, ExecutionErrorCode.ENGINE_RUNTIME_ERROR, ex.message, rev)
            } catch (_: Exception) { }
            throw ex
        } finally {
            freshnessRegistry.finishExecution(scope)
        }
    }

    /**
     * Bulk project Coordination evaluation across all resolved circuits.
     */
    fun calculateProjectCoordination(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>,
        allCircuits: List<CircuitEntity> = resolvedCircuits
    ): List<CoordinationResult> {
        val results = resolvedCircuits.mapNotNull { circ ->
            val sizingRes = sizingResults[circ.id] ?: return@mapNotNull null
            val parentId = circuitParents[circ.id]
            val upstreamName: String
            val upstreamRating: Double
            if (parentId != null) {
                val parentCirc = allCircuits.find { it.id == parentId }
                val parentSizing = sizingResults[parentId]
                if (parentCirc != null && parentSizing != null) {
                    upstreamName = parentCirc.circuitName
                    upstreamRating = parentSizing.deviceSize_A
                } else {
                    return@mapNotNull null
                }
            } else if (circ.fedFrom.equals("Transformer", ignoreCase = true)) {
                val txRes = transformerResults[circ.transformerId]
                if (txRes != null) {
                    upstreamName = "Incomer (${circ.transformerId})"
                    upstreamRating = txRes.flc
                } else {
                    return@mapNotNull null
                }
            } else {
                return@mapNotNull null
            }

            val sig = computeCoordinationSignature(circ, sizingRes, upstreamName, upstreamRating)
            val cached = coordinationCache[circ.id]
            if (cached != null && cached.first == sig) {
                cached.second
            } else {
                val fresh = CoordinationCalculationService.calculateCoordination(
                    downstreamCircuit = circ,
                    downstreamResult = sizingRes,
                    upstreamName = upstreamName,
                    upstreamRating = upstreamRating
                )
                coordinationCache[circ.id] = sig to fresh
                fresh
            }
        }

        val txList = transformerResults.keys.map { id -> TransformerEntity(transformerId = id) }
        val recorder = getRecorder(allCircuits, txList)
        results.forEach { coord ->
            try {
                val c = allCircuits.find { it.id == coord.downstreamCircuitId }
                val pid = c?.projectId?.ifEmpty { "DEFAULT_PROJECT" } ?: "DEFAULT_PROJECT"
                val rev = getProjectRevision(pid)
                runBlocking {
                    recorder.recordCoordination(pid, coord.downstreamCircuitId, coord.status, rev)
                }
            } catch (_: Exception) { }
        }

        return results
    }

    /**
     * Suspend wrapper for targeted Coordination calculation on background dispatcher.
     */
    suspend fun calculateCoordinationAsync(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity> = emptyList()
    ): CoordinationResult = withContext(Dispatchers.Default) {
        calculateCoordination(circuitId, allCircuits, allTransformers)
    }

    /**
     * Suspend wrapper for bulk project Coordination calculation on background dispatcher.
     */
    suspend fun calculateProjectCoordinationAsync(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>,
        allCircuits: List<CircuitEntity> = resolvedCircuits
    ): List<CoordinationResult> = withContext(Dispatchers.Default) {
        calculateProjectCoordination(
            resolvedCircuits = resolvedCircuits,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults,
            allCircuits = allCircuits
        )
    }
}
