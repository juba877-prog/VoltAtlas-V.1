package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.AdsResult
import com.example.engine.CoordinationResult
import com.example.engine.GeneratorResult
import com.example.engine.ProtectionEngine
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Strongly typed project-scoped cache keys ensuring complete calculation cache isolation across projects.
 */
data class ProjectCircuitKey(
    val projectId: String,
    val circuitId: Int
)

data class ProjectTransformerKey(
    val projectId: String,
    val transformerId: String
)

/**
 * Result data container for bulk project cable sizing.
 */
data class CableSizingProjectResult(
    val sizingResults: Map<Int, SizingResult>,
    val circuitParents: Map<Int, Int>,
    val resolvedCircuits: List<CircuitEntity>
)

/**
 * Raised when the live project revision cannot be resolved by a configured [ProjectRevisionProvider]
 * because of an indeterminate read failure (database locked, query timeout, IO failure).
 *
 * Gate D' rationale: the previous `catch (_: Exception) { 1L }` fallback let an indeterminate read
 * masquerade as an authentic initial revision, so a stale cache entry could validate as live and a
 * valid post-commit revalidation could report as superseded. An indeterminate read must fail loudly.
 */
class ProjectRevisionResolutionException(
    val projectId: String,
    cause: Throwable?
) : IllegalStateException(
    "Failed to resolve live project revision for project '$projectId'; refusing to fabricate a revision.",
    cause
)

/**
 * Pure calculation coordinator for engineering sizing calculations.
 *
 * Responsibilities:
 * - Scope resolution and dependency resolution (parent cumulative voltage drop, transformer loading, short-circuit impedances, protection coordination, selectivity)
 * - Result caching and freshness verification (strictly project-scoped)
 * - Invoking [CableCalculationService], [TransformerCalculationService], [ShortCircuitCalculationService], [ProtectionCalculationService], and [CoordinationCalculationService]
 * - Providing a unified calculation API for both ViewModel and future AI tool layers
 * - Operational freshness metadata lifecycle recording (side-effect only, non-breaking)
 */
object EngineeringCalculationCoordinator {

    val freshnessRegistry = InMemoryCalculationFreshnessMetadataRegistry()
    var projectRevisionProvider: ProjectRevisionProvider? = null

    private fun getRecorder(circuits: List<CircuitEntity>, transformers: List<TransformerEntity>): FreshnessLifecycleRecorder {
        return FreshnessLifecycleRecorder(freshnessRegistry, circuits, transformers)
    }

    /**
     * Gate D' Option A: the single revision-resolution point for a calculation pass.
     *
     * - No provider wired -> [untrackedProjectRevision] (revision tracking is not configured).
     * - Project row absent -> [untrackedProjectRevision] (definitive answer: no revision exists yet).
     * - Indeterminate read failure (DB locked / timeout / IO) -> fails loudly with
     *   [ProjectRevisionResolutionException] instead of the previous silent `1L` fallback, which could
     *   validate a stale revision-1L cache entry as live or misreport a valid revalidation as superseded.
     */
    private val untrackedProjectRevision = 1L

    private suspend fun getProjectRevisionSuspend(projectId: String): Long {
        val provider = projectRevisionProvider ?: return untrackedProjectRevision
        return try {
            provider.getCurrentRevision(projectId).revision
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: NoSuchElementException) {
            untrackedProjectRevision
        } catch (ex: Exception) {
            throw ProjectRevisionResolutionException(projectId, ex)
        }
    }

    private fun getProjectRevision(projectId: String): Long {
        val provider = projectRevisionProvider ?: return untrackedProjectRevision
        return try {
            runBlocking { provider.getCurrentRevision(projectId).revision }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: NoSuchElementException) {
            untrackedProjectRevision
        } catch (ex: Exception) {
            throw ProjectRevisionResolutionException(projectId, ex)
        }
    }

    /**
     * Resolves the one revision shared by an entire calculation pass, reusing an already-resolved
     * value handed down by an outer pass when supplied.
     */
    private fun resolvePassRevision(projectId: String, preResolvedRevision: Long?): Long =
        preResolvedRevision ?: getProjectRevision(projectId)

    /** Project id a bulk pass belongs to — used for the pass-level revision hoist. */
    private fun passProjectId(circuits: List<CircuitEntity>): String =
        circuits.firstOrNull()?.projectId?.ifEmpty { "DEFAULT_PROJECT" } ?: "DEFAULT_PROJECT"

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

    private val sizingCache = ConcurrentHashMap<ProjectCircuitKey, VersionedCacheEntry<SizingResult>>()
    private val transformerCache = ConcurrentHashMap<ProjectTransformerKey, VersionedCacheEntry<TransformerResult>>()
    private val shortCircuitCache = ConcurrentHashMap<ProjectCircuitKey, VersionedCacheEntry<ShortCircuitResult>>()
    private val adsCache = ConcurrentHashMap<ProjectCircuitKey, VersionedCacheEntry<AdsResult>>()
    private val protectionCache = ConcurrentHashMap<ProjectCircuitKey, VersionedCacheEntry<ProtectionResult>>()
    private val coordinationCache = ConcurrentHashMap<ProjectCircuitKey, VersionedCacheEntry<CoordinationResult>>()

    /**
     * Clears all cached calculation results across all projects.
     */
    fun clearCache() {
        sizingCache.clear()
        transformerCache.clear()
        shortCircuitCache.clear()
        adsCache.clear()
        protectionCache.clear()
        coordinationCache.clear()
    }

    /**
     * Clears cached calculation results and freshness metadata for a specific project only.
     */
    fun clearProjectCache(projectId: String) {
        sizingCache.keys.filter { it.projectId == projectId }.forEach { sizingCache.remove(it) }
        transformerCache.keys.filter { it.projectId == projectId }.forEach { transformerCache.remove(it) }
        shortCircuitCache.keys.filter { it.projectId == projectId }.forEach { shortCircuitCache.remove(it) }
        adsCache.keys.filter { it.projectId == projectId }.forEach { adsCache.remove(it) }
        protectionCache.keys.filter { it.projectId == projectId }.forEach { protectionCache.remove(it) }
        coordinationCache.keys.filter { it.projectId == projectId }.forEach { coordinationCache.remove(it) }
        freshnessRegistry.clearProject(projectId)
    }

    private val publicationMutexes = ConcurrentHashMap<String, Mutex>()

    /**
     * Authoritative project-scoped publication mutex shared across 7C Room CAS mutations,
     * repository mutations, and 7D guarded calculation publications.
     */
    fun getPublicationMutex(projectId: String): Mutex =
        publicationMutexes.computeIfAbsent(projectId) { Mutex() }

    fun getPublicationFence(projectId: String): Mutex = getPublicationMutex(projectId)

    // -------------------------------------------------------------------------
    // Pure Read-Only Project-Scoped Cache Accessors (Sub-Phase 3F & 7D)
    // -------------------------------------------------------------------------

    fun getCachedSizingResult(projectId: String, circuitId: Int): SizingResult? =
        sizingCache[ProjectCircuitKey(projectId, circuitId)]?.result

    fun getCachedTransformerResult(projectId: String, transformerId: String): TransformerResult? =
        transformerCache[ProjectTransformerKey(projectId, transformerId)]?.result

    fun getCachedShortCircuitResult(projectId: String, circuitId: Int): ShortCircuitResult? =
        shortCircuitCache[ProjectCircuitKey(projectId, circuitId)]?.result

    fun getCachedAdsResult(projectId: String, circuitId: Int): AdsResult? =
        adsCache[ProjectCircuitKey(projectId, circuitId)]?.result

    fun getCachedProtectionResult(projectId: String, circuitId: Int): ProtectionResult? =
        protectionCache[ProjectCircuitKey(projectId, circuitId)]?.result

    fun getCachedCoordinationResult(projectId: String, circuitId: Int): CoordinationResult? =
        coordinationCache[ProjectCircuitKey(projectId, circuitId)]?.result

    fun getCachedSizingResultGuarded(projectId: String, circuitId: Int, expectedRevision: Long): SizingResult? {
        val entry = sizingCache[ProjectCircuitKey(projectId, circuitId)] ?: return null
        if (entry.projectRevision != expectedRevision) return null
        val liveRev = getProjectRevision(projectId)
        if (liveRev != expectedRevision) return null
        return entry.result
    }

    fun getCachedTransformerResultGuarded(projectId: String, transformerId: String, expectedRevision: Long): TransformerResult? {
        val entry = transformerCache[ProjectTransformerKey(projectId, transformerId)] ?: return null
        if (entry.projectRevision != expectedRevision) return null
        val liveRev = getProjectRevision(projectId)
        if (liveRev != expectedRevision) return null
        return entry.result
    }

    fun getCachedShortCircuitResultGuarded(projectId: String, circuitId: Int, expectedRevision: Long): ShortCircuitResult? {
        val entry = shortCircuitCache[ProjectCircuitKey(projectId, circuitId)] ?: return null
        if (entry.projectRevision != expectedRevision) return null
        val liveRev = getProjectRevision(projectId)
        if (liveRev != expectedRevision) return null
        return entry.result
    }

    fun getCachedAdsResultGuarded(projectId: String, circuitId: Int, expectedRevision: Long): AdsResult? {
        val entry = adsCache[ProjectCircuitKey(projectId, circuitId)] ?: return null
        if (entry.projectRevision != expectedRevision) return null
        val liveRev = getProjectRevision(projectId)
        if (liveRev != expectedRevision) return null
        return entry.result
    }

    fun getCachedProtectionResultGuarded(projectId: String, circuitId: Int, expectedRevision: Long): ProtectionResult? {
        val entry = protectionCache[ProjectCircuitKey(projectId, circuitId)] ?: return null
        if (entry.projectRevision != expectedRevision) return null
        val liveRev = getProjectRevision(projectId)
        if (liveRev != expectedRevision) return null
        return entry.result
    }

    fun getCachedCoordinationResultGuarded(projectId: String, circuitId: Int, expectedRevision: Long): CoordinationResult? {
        val entry = coordinationCache[ProjectCircuitKey(projectId, circuitId)] ?: return null
        if (entry.projectRevision != expectedRevision) return null
        val liveRev = getProjectRevision(projectId)
        if (liveRev != expectedRevision) return null
        return entry.result
    }

    fun getVersionedSizingEntry(projectId: String, circuitId: Int): VersionedCacheEntry<SizingResult>? =
        sizingCache[ProjectCircuitKey(projectId, circuitId)]

    fun getVersionedTransformerEntry(projectId: String, transformerId: String): VersionedCacheEntry<TransformerResult>? =
        transformerCache[ProjectTransformerKey(projectId, transformerId)]

    fun getVersionedShortCircuitEntry(projectId: String, circuitId: Int): VersionedCacheEntry<ShortCircuitResult>? =
        shortCircuitCache[ProjectCircuitKey(projectId, circuitId)]

    fun getVersionedAdsEntry(projectId: String, circuitId: Int): VersionedCacheEntry<AdsResult>? =
        adsCache[ProjectCircuitKey(projectId, circuitId)]

    fun getVersionedProtectionEntry(projectId: String, circuitId: Int): VersionedCacheEntry<ProtectionResult>? =
        protectionCache[ProjectCircuitKey(projectId, circuitId)]

    fun getVersionedCoordinationEntry(projectId: String, circuitId: Int): VersionedCacheEntry<CoordinationResult>? =
        coordinationCache[ProjectCircuitKey(projectId, circuitId)]

    fun <T> getCoherentSnapshot(
        projectId: String,
        domain: CalculationDomain,
        entityId: String,
        expectedRevision: Long? = null
    ): CoherentCalculationSnapshot<T>? {
        val liveRev = getProjectRevision(projectId)
        if (expectedRevision != null && liveRev != expectedRevision) return null

        val entry = when (domain) {
            CalculationDomain.CABLE_SIZING -> {
                val id = entityId.toIntOrNull() ?: return null
                sizingCache[ProjectCircuitKey(projectId, id)]
            }
            CalculationDomain.TRANSFORMER -> {
                transformerCache[ProjectTransformerKey(projectId, entityId)]
            }
            CalculationDomain.SHORT_CIRCUIT -> {
                val id = entityId.toIntOrNull() ?: return null
                shortCircuitCache[ProjectCircuitKey(projectId, id)]
            }
            CalculationDomain.PROTECTION_ADS -> {
                val id = entityId.toIntOrNull() ?: return null
                protectionCache[ProjectCircuitKey(projectId, id)]
            }
            CalculationDomain.COORDINATION -> {
                val id = entityId.toIntOrNull() ?: return null
                coordinationCache[ProjectCircuitKey(projectId, id)]
            }
        } ?: return null

        if (entry.projectRevision != liveRev) return null
        if (expectedRevision != null && entry.projectRevision != expectedRevision) return null

        @Suppress("UNCHECKED_CAST")
        return CoherentCalculationSnapshot(
            payload = entry.result as T,
            projectRevision = entry.projectRevision,
            isPublishedCurrent = true,
            publishedAt = entry.publishedAt
        )
    }

    /**
     * Invalidates cache for a specific project and circuit ID across all circuit domain caches.
     */
    fun invalidateCircuit(projectId: String, circuitId: Int) {
        val key = ProjectCircuitKey(projectId, circuitId)
        sizingCache.remove(key)
        shortCircuitCache.remove(key)
        adsCache.remove(key)
        protectionCache.remove(key)
        coordinationCache.remove(key)
    }

    /**
     * Convenience overload invalidating a circuit ID across all projects containing that ID.
     */
    fun invalidateCircuit(circuitId: Int) {
        sizingCache.keys.filter { it.circuitId == circuitId }.forEach { sizingCache.remove(it) }
        shortCircuitCache.keys.filter { it.circuitId == circuitId }.forEach { shortCircuitCache.remove(it) }
        adsCache.keys.filter { it.circuitId == circuitId }.forEach { adsCache.remove(it) }
        protectionCache.keys.filter { it.circuitId == circuitId }.forEach { protectionCache.remove(it) }
        coordinationCache.keys.filter { it.circuitId == circuitId }.forEach { coordinationCache.remove(it) }
    }

    /**
     * Invalidates cache for a specific transformer in a project.
     */
    fun invalidateTransformer(projectId: String, transformerId: String) {
        transformerCache.remove(ProjectTransformerKey(projectId, transformerId))
    }

    /**
     * Convenience overload invalidating a transformer ID across all projects.
     */
    fun invalidateTransformer(transformerId: String) {
        transformerCache.keys.filter { it.transformerId == transformerId }.forEach { transformerCache.remove(it) }
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
        transformers: List<TransformerEntity> = emptyList(),
        projectRevision: Long? = null
    ): SizingResult {
        val ancestors = resolveAncestorChain(circuit, allCircuits)
        var cumulativeVD = 0.0
        // Gate D' Option A: resolve the revision once per call and reuse it for the ancestor loop,
        // the target cache check, and the fresh-publication/error paths (was 2–3 resolutions per call).
        val passRevision = resolvePassRevision(passProjectId(listOf(circuit)), projectRevision)

        for (ancestor in ancestors) {
            val ancestorPid = ancestor.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val ancestorKey = ProjectCircuitKey(ancestorPid, ancestor.id)
            val ancestorSig = computeSignature(ancestor, cumulativeVD, transformers)
            val liveRev = passRevision
            val cached = sizingCache[ancestorKey]
            val ancestorResult = if (cached != null && cached.signature == ancestorSig && cached.projectRevision == liveRev) {
                cached.result
            } else {
                val fresh = CableCalculationService.calculateSingleCircuit(
                    circuit = ancestor,
                    parentCumulativeVD = cumulativeVD,
                    upstreamCircuits = ancestors,
                    transformers = transformers
                )
                sizingCache[ancestorKey] = VersionedCacheEntry(ancestorSig, fresh, liveRev)
                fresh
            }
            cumulativeVD = ancestorResult.cumulativeVDpct
        }

        val targetPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val targetKey = ProjectCircuitKey(targetPid, circuit.id)
        val targetSig = computeSignature(circuit, cumulativeVD, transformers)
        val targetLiveRev = passRevision
        val cachedTarget = sizingCache[targetKey]
        if (cachedTarget != null && cachedTarget.signature == targetSig && cachedTarget.projectRevision == targetLiveRev) {
            val projectId = targetPid
            val rev = targetLiveRev
            val status = if (cachedTarget.result.status.contains("FAIL")) "FAIL" else if (cachedTarget.result.status.contains("WARN")) "WARNING" else "PASS"
            try {
                getRecorder(allCircuits, transformers).recordCableSizing(projectId, circuit.id, status, rev)
            } catch (_: Exception) { }
            return cachedTarget.result
        }

        val projectId = targetPid
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
            val rev = passRevision
            sizingCache[targetKey] = VersionedCacheEntry(targetSig, freshResult, rev)
            val status = if (freshResult.status.contains("FAIL")) "FAIL" else if (freshResult.status.contains("WARN")) "WARNING" else "PASS"
            try {
                getRecorder(allCircuits, transformers).recordCableSizing(projectId, circuit.id, status, rev)
            } catch (_: Exception) { }
            return freshResult
        } catch (ex: Exception) {
            try {
                val rev = passRevision
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
        transformers: List<TransformerEntity> = emptyList(),
        projectRevision: Long? = null
    ): CableSizingProjectResult {
        val normalizedCircuits = circuits.map { CableCalculationService.normalizeAutoOverride(it) }
        if (normalizedCircuits.isEmpty()) {
            return CableSizingProjectResult(emptyMap(), emptyMap(), emptyList())
        }
        // Gate D' Option A: resolve the pass revision ONCE (was once per circuit — 120 per 60-circuit pass).
        val passRevision = resolvePassRevision(passProjectId(normalizedCircuits), projectRevision)

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

                    val cPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
                    val cKey = ProjectCircuitKey(cPid, circuit.id)
                    val sig = computeSignature(circuit, parentVD, transformers)
                    val liveRev = passRevision
                    val cached = sizingCache[cKey]
                    val sizingRes = if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
                        cached.result
                    } else {
                        val fresh = CableCalculationService.calculateSingleCircuit(
                            circuit = circuit,
                            parentCumulativeVD = parentVD,
                            upstreamCircuits = resolvedList,
                            transformers = transformers
                        )
                        sizingCache[cKey] = VersionedCacheEntry(sig, fresh, liveRev)
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
                val rev = passRevision
                val status = if (res.status.contains("FAIL")) "FAIL" else if (res.status.contains("WARN")) "WARNING" else "PASS"
                try {
                    recorder.recordCableSizing(pid, circuit.id, status, rev)
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
        allTransformers: List<TransformerEntity>,
        projectRevision: Long? = null
    ): TransformerResult {
        val tx = allTransformers.firstOrNull { it.transformerId.equals(transformerId.trim(), ignoreCase = true) }
            ?: TransformerEntity(transformerId = transformerId, status = "Inactive")
        return calculateTransformer(tx, allCircuits, projectRevision)
    }

    /**
     * Targeted single-transformer calculation for a given [TransformerEntity].
     *
     * @param projectRevision Gate D' Option A: pass-level hoisted revision; when supplied, no revision
     *   resolution is performed (the whole pass shares one resolved value).
     */
    fun calculateTransformer(
        transformer: TransformerEntity,
        allCircuits: List<CircuitEntity>,
        projectRevision: Long? = null
    ): TransformerResult {
        val pid = transformer.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val txKey = ProjectTransformerKey(pid, transformer.transformerId)
        val sig = computeTransformerSignature(transformer, allCircuits)
        // Gate D' Option A: resolve once per call and reuse for cache check, publication and recording.
        val liveRev = resolvePassRevision(pid, projectRevision)
        val cached = transformerCache[txKey]
        if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
            val rev = liveRev
            try {
                getRecorder(allCircuits, listOf(transformer)).recordTransformer(pid, transformer.transformerId, "PASS", rev)
            } catch (_: Exception) { }
            return cached.result
        }

        val scope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, transformer.transformerId, CalculationDomain.TRANSFORMER)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val freshResult = TransformerCalculationService.calculateSingleTransformer(transformer, allCircuits)
            val rev = liveRev
            transformerCache[txKey] = VersionedCacheEntry(sig, freshResult, rev)
            try {
                getRecorder(allCircuits, listOf(transformer)).recordTransformer(pid, transformer.transformerId, "PASS", rev)
            } catch (_: Exception) { }
            return freshResult
        } catch (ex: Exception) {
            try {
                val rev = liveRev
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
        transformers: List<TransformerEntity>,
        projectRevision: Long? = null
    ): Map<String, TransformerResult> {
        if (transformers.isEmpty()) return emptyMap()
        // Gate D' Option A: resolve the pass revision ONCE and thread it into every transformer stage.
        val passPid = transformers.firstOrNull()?.projectId?.ifEmpty { "DEFAULT_PROJECT" } ?: "DEFAULT_PROJECT"
        val passRevision = resolvePassRevision(passPid, projectRevision)
        val results = transformers.associate { transformer ->
            transformer.transformerId to calculateTransformer(transformer, circuits, passRevision)
        }
        val recorder = getRecorder(circuits, transformers)
        transformers.forEach { tx ->
            val pid = tx.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val rev = passRevision
            try {
                recorder.recordTransformer(pid, tx.transformerId, "PASS", rev)
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
        allTransformers: List<TransformerEntity>,
        projectRevision: Long? = null
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

        val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scKey = ProjectCircuitKey(pid, circuit.id)
        // Gate D' Option A: resolve once per call and reuse for cache check, publication and recording.
        val liveRev = resolvePassRevision(pid, projectRevision)
        val cached = shortCircuitCache[scKey]
        if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
            val rev = liveRev
            val status = if (cached.result.status.contains("FAIL") || cached.result.status.contains("MISSING")) "FAIL" else "PASS"
            try {
                getRecorder(allCircuits, allTransformers).recordShortCircuit(pid, circuitId, status, rev)
            } catch (_: Exception) { }
            return cached.result
        }

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
            val rev = liveRev
            shortCircuitCache[scKey] = VersionedCacheEntry(sig, fresh, rev)
            val status = if (fresh.status.contains("FAIL") || fresh.status.contains("MISSING")) "FAIL" else "PASS"
            try {
                getRecorder(allCircuits, allTransformers).recordShortCircuit(pid, circuitId, status, rev)
            } catch (_: Exception) { }
            return fresh
        } catch (ex: Exception) {
            try {
                val rev = liveRev
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
        transformerResults: Map<String, TransformerResult>,
        generatorResults: Map<String, GeneratorResult> = emptyMap(),
        projectRevision: Long? = null
    ): Map<Int, ShortCircuitResult> {
        if (resolvedCircuits.isEmpty()) return emptyMap()
        // Gate D' Option A: resolve the pass revision ONCE (was once per circuit — 180 per 60-circuit pass).
        val passRevision = resolvePassRevision(passProjectId(resolvedCircuits), projectRevision)
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
            val scPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val scKey = ProjectCircuitKey(scPid, circuit.id)
            val liveRev = passRevision
            val cached = shortCircuitCache[scKey]

            if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
                results[circuit.id] = cached.result
            } else {
                circuitsToCompute.add(circuit)
            }
        }

        if (circuitsToCompute.isNotEmpty()) {
            val freshMap = ShortCircuitCalculationService.calculateProjectShortCircuit(
                resolvedCircuits = resolvedCircuits,
                circuitParents = circuitParents,
                sizingResults = sizingResults,
                transformerResults = transformerResults,
                generatorResults = generatorResults
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
                val scPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
                val scKey = ProjectCircuitKey(scPid, circuit.id)
                val rev = passRevision
                shortCircuitCache[scKey] = VersionedCacheEntry(sig, fresh, rev)
                results[circuit.id] = fresh
            }
        }

        val txList = transformerResults.keys.map { id -> TransformerEntity(transformerId = id) }
        val recorder = getRecorder(resolvedCircuits, txList)
        resolvedCircuits.forEach { c ->
            val sc = results[c.id]
            if (sc != null) {
                val pid = c.projectId.ifEmpty { "DEFAULT_PROJECT" }
                val rev = passRevision
                val status = if (sc.status.contains("FAIL") || sc.status.contains("MISSING")) "FAIL" else "PASS"
                try {
                    recorder.recordShortCircuit(pid, c.id, status, rev)
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
        transformerResults: Map<String, TransformerResult>,
        generatorResults: Map<String, GeneratorResult> = emptyMap()
    ): Map<Int, ShortCircuitResult> = withContext(Dispatchers.Default) {
        calculateProjectShortCircuit(
            resolvedCircuits = resolvedCircuits,
            circuitParents = circuitParents,
            sizingResults = sizingResults,
            transformerResults = transformerResults,
            generatorResults = generatorResults
        )
    }

    /**
     * Computes a cache signature for ADS calculation.
     */
    private fun computeAdsSignature(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        ze_ohm: Double?
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
        ze_ohm: Double = 0.01,
        projectRevision: Long? = null
    ): AdsResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return ProtectionCalculationService.buildMissingAdsResult(
                CircuitEntity(id = circuitId, circuitName = "Circuit $circuitId", projectId = "")
            )

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers, projectRevision)
        val txMap = calculateProjectTransformers(allCircuits, allTransformers, projectRevision)
        val sourceTx = resolveSourceTransformer(circuit, allCircuits, txMap)
        val derivedZe = if (sourceTx != null)
            ProtectionEngine.deriveZeFromSource(sourceTx.isc3Phase, sourceTx.voltage)
        else 0.0
        val isResolved = derivedZe > 0.0
        val effZe = if (isResolved) derivedZe else ze_ohm
        val sig = computeAdsSignature(circuit, sizingResult, effZe)
        val adsPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val adsKey = ProjectCircuitKey(adsPid, circuit.id)
        // Gate D' Option A: resolve once per call and reuse for cache check and publication.
        val liveRev = resolvePassRevision(adsPid, projectRevision)

        val cached = adsCache[adsKey]
        if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
            return cached.result
        }

        var fresh = ProtectionCalculationService.calculateAds(circuit, sizingResult, effZe)
        if (!isResolved) {
            fresh = fresh.copy(
                status = if (fresh.calculatedZs > fresh.maxZsAllowed) "✗ FAIL" else "UNVERIFIED",
                disconnectionTime = if (fresh.calculatedZs > fresh.maxZsAllowed) "> 0.4s" else "UNVERIFIED",
                warnings = fresh.warnings +
                    "Ze assumed $ze_ohm Ω (no transformer source data for this circuit) — verify"
            )
        }
        adsCache[adsKey] = VersionedCacheEntry(sig, fresh, liveRev)
        return fresh
    }

    /**
     * Targeted single-circuit Protection evaluation.
     */
    fun calculateProtection(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>,
        projectRevision: Long? = null
    ): ProtectionResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return ProtectionCalculationService.buildMissingProtectionResult(
                CircuitEntity(id = circuitId, circuitName = "Circuit $circuitId", projectId = "")
            )

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers, projectRevision)
        val scResult = calculateShortCircuit(circuitId, allCircuits, allTransformers, projectRevision)

        // Find parent device size or transformer FLC with cycle guard
        val parent = (if (circuit.parentId != null) allCircuits.find { it.id == circuit.parentId } else null)
            ?: allCircuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }

        val upstreamIn: Double
        val upstreamName: String
        val hasUpstreamDevice: Boolean
        if (parent != null && parent.id != circuit.id) {
            val parentSizing = calculateCableSizing(parent, allCircuits, allTransformers, projectRevision)
            upstreamIn = parentSizing.deviceSize_A
            upstreamName = parent.circuitName
            hasUpstreamDevice = upstreamIn > 0.0
        } else {
            val txResult = if (circuit.transformerId.isNotBlank()) {
                calculateTransformer(circuit.transformerId, allCircuits, allTransformers, projectRevision)
            } else null
            upstreamIn = txResult?.flc ?: 0.0
            upstreamName = if (circuit.transformerId.isNotBlank()) "Incomer (${circuit.transformerId})" else "Incomer"
            hasUpstreamDevice = false
        }

        val sig = computeProtectionSignature(circuit, sizingResult, scResult, upstreamIn, upstreamName)
        val protPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val protKey = ProjectCircuitKey(protPid, circuit.id)
        // Gate D' Option A: resolve once per call and reuse for cache check, publication and recording.
        val liveRev = resolvePassRevision(protPid, projectRevision)
        val cached = protectionCache[protKey]
        if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
            val pid = protPid
            val rev = liveRev
            try {
                getRecorder(allCircuits, allTransformers).recordProtectionAds(pid, circuitId, cached.result.finalStatus, rev)
            } catch (_: Exception) { }
            return cached.result
        }

        val pid = protPid
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.PROTECTION_ADS)
        val hasPrior = freshnessRegistry.getRecord(scope) != null
        freshnessRegistry.startExecution(scope, hasPrior)

        try {
            val fresh = ProtectionCalculationService.evaluateProtection(
                circuit = circuit,
                sizingResult = sizingResult,
                shortCircuitResult = scResult,
                upstreamIn_A = upstreamIn,
                upstreamName = upstreamName,
                hasUpstreamDevice = hasUpstreamDevice
            )
            val rev = liveRev
            protectionCache[protKey] = VersionedCacheEntry(sig, fresh, rev)
            try {
                getRecorder(allCircuits, allTransformers).recordProtectionAds(pid, circuitId, fresh.finalStatus, rev)
            } catch (_: Exception) { }
            return fresh
        } catch (ex: Exception) {
            try {
                val rev = liveRev
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
        ze_ohm: Double = 0.01,
        transformerResults: Map<String, TransformerResult> = emptyMap(),
        projectRevision: Long? = null
    ): Map<Int, AdsResult> {
        if (resolvedCircuits.isEmpty()) return emptyMap()
        // Gate D' Option A: resolve the pass revision ONCE (was once per circuit — 60 per 60-circuit pass).
        val passRevision = resolvePassRevision(passProjectId(resolvedCircuits), projectRevision)
        val results = mutableMapOf<Int, AdsResult>()
        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id] ?: continue

            // AUDIT-5 P0 item 3b (C-1): derive Ze from the feeding transformer when available.
            // When no source transformer resolves, effZe falls back to ze_ohm (0.01 ohm), but the
            // verdict is UNVERIFIED — never an optimistic PASS.
            val sourceTx = resolveSourceTransformer(circuit, resolvedCircuits, transformerResults)
            val derivedZe = if (sourceTx != null)
                ProtectionEngine.deriveZeFromSource(sourceTx.isc3Phase, sourceTx.voltage)
            else 0.0
            val isResolved = derivedZe > 0.0
            val effZe = if (isResolved) derivedZe else ze_ohm

            val sig = computeAdsSignature(circuit, sizing, effZe)
            val adsPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val adsKey = ProjectCircuitKey(adsPid, circuit.id)
            val liveRev = passRevision
            val cached = adsCache[adsKey]
            if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
                results[circuit.id] = cached.result
            } else {
                var fresh = ProtectionCalculationService.calculateAds(circuit, sizing, effZe)
                if (!isResolved) {
                    fresh = fresh.copy(
                        status = if (fresh.calculatedZs > fresh.maxZsAllowed) "✗ FAIL" else "UNVERIFIED",
                        disconnectionTime = if (fresh.calculatedZs > fresh.maxZsAllowed) "> 0.4s" else "UNVERIFIED",
                        warnings = fresh.warnings +
                            "Ze assumed $ze_ohm Ω (no transformer source data for this circuit) — verify"
                    )
                }
                val rev = liveRev
                adsCache[adsKey] = VersionedCacheEntry(sig, fresh, rev)
                results[circuit.id] = fresh
            }
        }
        return results
    }

    /**
     * Walks up the parent chain to find the feeding transformer result for ADS Ze derivation.
     */
    private fun resolveSourceTransformer(
        circuit: CircuitEntity,
        resolvedCircuits: List<CircuitEntity>,
        transformerResults: Map<String, TransformerResult>
    ): TransformerResult? {
        var curr: CircuitEntity? = circuit
        var guard = 0
        while (curr != null && guard++ < 32) {
            transformerResults[curr.transformerId]?.let { return it }
            curr = curr.parentId?.let { pid -> resolvedCircuits.find { it.id == pid } }
        }
        return null
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
        allCircuits: List<CircuitEntity> = resolvedCircuits,
        projectRevision: Long? = null
    ): Map<Int, ProtectionResult> {
        if (resolvedCircuits.isEmpty()) return emptyMap()
        // Gate D' Option A: resolve the pass revision ONCE (was once per circuit — 120 per 60-circuit pass).
        val passRevision = resolvePassRevision(passProjectId(resolvedCircuits), projectRevision)
        val results = mutableMapOf<Int, ProtectionResult>()
        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id] ?: continue
            val sc = shortCircuitResults[circuit.id]
            val parentId = circuitParents[circuit.id]
            val parentSizing = if (parentId != null) sizingResults[parentId] else null
            val hasUpstreamDevice = parentSizing != null && parentSizing.deviceSize_A > 0.0
            val upstreamIn = if (hasUpstreamDevice) parentSizing!!.deviceSize_A else (transformerResults[circuit.transformerId]?.flc ?: 0.0)
            val parentName = if (hasUpstreamDevice) {
                (allCircuits.find { it.id == parentId }?.circuitName ?: "Parent Board")
            } else {
                "Incomer (${circuit.transformerId})"
            }

            val sig = computeProtectionSignature(circuit, sizing, sc, upstreamIn, parentName)
            val protPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val protKey = ProjectCircuitKey(protPid, circuit.id)
            val liveRev = passRevision
            val cached = protectionCache[protKey]
            if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
                results[circuit.id] = cached.result
            } else {
                val fresh = ProtectionCalculationService.evaluateProtection(
                    circuit = circuit,
                    sizingResult = sizing,
                    shortCircuitResult = sc,
                    upstreamIn_A = upstreamIn,
                    upstreamName = parentName,
                    hasUpstreamDevice = hasUpstreamDevice
                )
                val rev = liveRev
                protectionCache[protKey] = VersionedCacheEntry(sig, fresh, rev)
                results[circuit.id] = fresh
            }
        }

        val txList = transformerResults.keys.map { id -> TransformerEntity(transformerId = id) }
        val recorder = getRecorder(resolvedCircuits, txList)
        resolvedCircuits.forEach { c ->
            val prot = results[c.id]
            if (prot != null) {
                val pid = c.projectId.ifEmpty { "DEFAULT_PROJECT" }
                val rev = passRevision
                try {
                    recorder.recordProtectionAds(pid, c.id, prot.finalStatus, rev)
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
        ze_ohm: Double = 0.01,
        transformerResults: Map<String, TransformerResult> = emptyMap()
    ): Map<Int, AdsResult> = withContext(Dispatchers.Default) {
        calculateProjectAds(resolvedCircuits, sizingResults, ze_ohm, transformerResults)
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
        allTransformers: List<TransformerEntity> = emptyList(),
        projectRevision: Long? = null
    ): CoordinationResult {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return CoordinationCalculationService.buildMissingCoordinationResult(
                CircuitEntity(id = circuitId, circuitName = "Circuit $circuitId", projectId = ""),
                "Circuit $circuitId not found in project"
            )

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers, projectRevision)

        // Resolve upstream parent device or transformer incomer with cycle protection
        val visited = mutableSetOf<Int>()
        visited.add(circuit.id)

        val parent = (if (circuit.parentId != null) allCircuits.find { it.id == circuit.parentId } else null)
            ?: allCircuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }

        val upstreamName: String
        val upstreamRating: Double

        if (parent != null && parent.id != circuit.id && !visited.contains(parent.id)) {
            val parentSizing = calculateCableSizing(parent, allCircuits, allTransformers, projectRevision)
            upstreamRating = parentSizing.deviceSize_A
            upstreamName = parent.circuitName
        } else if (circuit.fedFrom.equals("Transformer", ignoreCase = true) || circuit.transformerId.isNotBlank()) {
            val tx = allTransformers.firstOrNull { it.transformerId.equals(circuit.transformerId.trim(), ignoreCase = true) }
            if (tx != null) {
                val txResult = calculateTransformer(tx, allCircuits, projectRevision)
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
        val coordPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val coordKey = ProjectCircuitKey(coordPid, circuit.id)
        // Gate D' Option A: resolve once per call and reuse for cache check, publication and recording.
        val liveRev = resolvePassRevision(coordPid, projectRevision)
        val cached = coordinationCache[coordKey]
        if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
            val pid = coordPid
            val rev = liveRev
            try {
                getRecorder(allCircuits, allTransformers).recordCoordination(pid, circuitId, cached.result.status, rev)
            } catch (_: Exception) { }
            return cached.result
        }

        val pid = coordPid
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
            val rev = liveRev
            coordinationCache[coordKey] = VersionedCacheEntry(sig, fresh, rev)
            try {
                getRecorder(allCircuits, allTransformers).recordCoordination(pid, circuitId, fresh.status, rev)
            } catch (_: Exception) { }
            return fresh
        } catch (ex: Exception) {
            try {
                val rev = liveRev
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
        allCircuits: List<CircuitEntity> = resolvedCircuits,
        projectRevision: Long? = null
    ): List<CoordinationResult> {
        if (resolvedCircuits.isEmpty()) return emptyList()
        // Gate D' Option A: resolve the pass revision ONCE (was once per circuit — 120 per 60-circuit pass).
        val passRevision = resolvePassRevision(passProjectId(resolvedCircuits), projectRevision)
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
            val circPid = circ.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val circKey = ProjectCircuitKey(circPid, circ.id)
            val liveRev = passRevision
            val cached = coordinationCache[circKey]
            if (cached != null && cached.signature == sig && cached.projectRevision == liveRev) {
                cached.result
            } else {
                val fresh = CoordinationCalculationService.calculateCoordination(
                    downstreamCircuit = circ,
                    downstreamResult = sizingRes,
                    upstreamName = upstreamName,
                    upstreamRating = upstreamRating
                )
                val rev = liveRev
                coordinationCache[circKey] = VersionedCacheEntry(sig, fresh, rev)
                fresh
            }
        }

        val txList = transformerResults.keys.map { id -> TransformerEntity(transformerId = id) }
        val recorder = getRecorder(allCircuits, txList)
        results.forEach { coord ->
            try {
                val c = allCircuits.find { it.id == coord.downstreamCircuitId }
                val pid = c?.projectId?.ifEmpty { "DEFAULT_PROJECT" } ?: "DEFAULT_PROJECT"
                val rev = passRevision
                recorder.recordCoordination(pid, coord.downstreamCircuitId, coord.status, rev)
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

    // -------------------------------------------------------------------------
    // Revision-Guarded Targeted Calculation Entrypoints (Phase 7D)
    // -------------------------------------------------------------------------

    suspend fun calculateCableSizingGuarded(
        circuit: CircuitEntity,
        allCircuits: List<CircuitEntity>,
        transformers: List<TransformerEntity> = emptyList(),
        expectedRevision: Long
    ): GuardedCalculationResult<SizingResult> {
        val ancestors = resolveAncestorChain(circuit, allCircuits)
        var cumulativeVD = 0.0
        for (ancestor in ancestors) {
            val ancestorPid = ancestor.projectId.ifEmpty { "DEFAULT_PROJECT" }
            val ancestorKey = ProjectCircuitKey(ancestorPid, ancestor.id)
            val ancestorSig = computeSignature(ancestor, cumulativeVD, transformers)
            val liveRev = getProjectRevisionSuspend(ancestorPid)
            val cached = sizingCache[ancestorKey]
            val ancestorResult = if (cached != null && cached.signature == ancestorSig && cached.projectRevision == liveRev) {
                cached.result
            } else {
                val fresh = CableCalculationService.calculateSingleCircuit(
                    circuit = ancestor,
                    parentCumulativeVD = cumulativeVD,
                    upstreamCircuits = ancestors,
                    transformers = transformers
                )
                sizingCache[ancestorKey] = VersionedCacheEntry(ancestorSig, fresh, liveRev)
                fresh
            }
            cumulativeVD = ancestorResult.cumulativeVDpct
        }

        val targetPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val targetKey = ProjectCircuitKey(targetPid, circuit.id)
        val targetSig = computeSignature(circuit, cumulativeVD, transformers)

        val freshResult = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = cumulativeVD,
            upstreamCircuits = ancestors,
            transformers = transformers
        )

        val status = if (freshResult.status.contains("FAIL")) "FAIL" else if (freshResult.status.contains("WARN")) "WARNING" else "PASS"
        val scope = CalculationScope(targetPid, CalculationEntityType.CIRCUIT, circuit.id.toString(), CalculationDomain.CABLE_SIZING)

        return getPublicationMutex(targetPid).withLock {
            val liveRev = getProjectRevisionSuspend(targetPid)
            if (liveRev != expectedRevision) {
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, liveRev)
            }

            // Step 1: Record freshness metadata FIRST (failure aborts before cache write)
            try {
                getRecorder(allCircuits, transformers).recordCableSizing(targetPid, circuit.id, status, liveRev)
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Failed("Freshness recorder failed: ${ex.message}")
            }

            // Step 2: Verify revision post-recording
            val postRev = getProjectRevisionSuspend(targetPid)
            if (postRev != expectedRevision) {
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, postRev)
            }

            // Step 3: Atomically write versioned cache entry
            sizingCache[targetKey] = VersionedCacheEntry(targetSig, freshResult, liveRev)

            GuardedCalculationResult.Published(freshResult, liveRev)
        }
    }

    suspend fun calculateShortCircuitGuarded(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>,
        expectedRevision: Long
    ): GuardedCalculationResult<ShortCircuitResult> {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return GuardedCalculationResult.Failed("Circuit $circuitId not found in project")

        val ancestors = resolveAncestorChain(circuit, allCircuits)
        val branchCircuits = ancestors + circuit

        val branchSizings = mutableMapOf<Int, SizingResult>()
        for (item in branchCircuits) {
            branchSizings[item.id] = calculateCableSizing(item, allCircuits, allTransformers)
        }

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

        val ancestorPairs = ancestors.map { it to branchSizings[it.id] }
        val sig = computeShortCircuitSignature(
            circuit = circuit,
            sizingResult = branchSizings[circuit.id],
            transformerResult = txResult,
            ancestorSizings = ancestorPairs
        )

        val pid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val scKey = ProjectCircuitKey(pid, circuit.id)
        val status = if (fresh.status.contains("FAIL") || fresh.status.contains("MISSING")) "FAIL" else "PASS"
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.SHORT_CIRCUIT)

        return getPublicationMutex(pid).withLock {
            val liveRev = getProjectRevisionSuspend(pid)
            if (liveRev != expectedRevision) {
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, liveRev)
            }

            try {
                getRecorder(allCircuits, allTransformers).recordShortCircuit(pid, circuitId, status, liveRev)
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Failed("Freshness recorder failed: ${ex.message}")
            }

            val postRev = getProjectRevisionSuspend(pid)
            if (postRev != expectedRevision) {
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, postRev)
            }

            shortCircuitCache[scKey] = VersionedCacheEntry(sig, fresh, liveRev)

            GuardedCalculationResult.Published(fresh, liveRev)
        }
    }

    suspend fun calculateProtectionGuarded(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity>,
        expectedRevision: Long,
        ze_ohm: Double = 0.01
    ): GuardedCalculationResult<ProtectionResult> {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return GuardedCalculationResult.Failed("Circuit $circuitId not found in project")

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers)
        val scResult = calculateShortCircuit(circuitId, allCircuits, allTransformers)

        val parent = (if (circuit.parentId != null) allCircuits.find { it.id == circuit.parentId } else null)
            ?: allCircuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }

        val upstreamIn: Double
        val upstreamName: String
        val hasUpstreamDevice: Boolean
        if (parent != null && parent.id != circuit.id) {
            val parentSizing = calculateCableSizing(parent, allCircuits, allTransformers)
            upstreamIn = parentSizing.deviceSize_A
            upstreamName = parent.circuitName
            hasUpstreamDevice = upstreamIn > 0.0
        } else {
            val txResult = if (circuit.transformerId.isNotBlank()) {
                calculateTransformer(circuit.transformerId, allCircuits, allTransformers)
            } else null
            upstreamIn = txResult?.flc ?: 0.0
            upstreamName = if (circuit.transformerId.isNotBlank()) "Incomer (${circuit.transformerId})" else "Incomer"
            hasUpstreamDevice = false
        }

        val txMap = calculateProjectTransformers(allCircuits, allTransformers)
        val sourceTx = resolveSourceTransformer(circuit, allCircuits, txMap)
        val derivedZe = if (sourceTx != null)
            ProtectionEngine.deriveZeFromSource(sourceTx.isc3Phase, sourceTx.voltage)
        else 0.0
        val isResolved = derivedZe > 0.0
        val effZe = if (isResolved) derivedZe else ze_ohm

        // Atomically compute both ADS and Protection outputs
        var freshAds = ProtectionCalculationService.calculateAds(circuit, sizingResult, effZe)
        if (!isResolved) {
            freshAds = freshAds.copy(
                status = if (freshAds.calculatedZs > freshAds.maxZsAllowed) "✗ FAIL" else "UNVERIFIED",
                disconnectionTime = if (freshAds.calculatedZs > freshAds.maxZsAllowed) "> 0.4s" else "UNVERIFIED",
                warnings = freshAds.warnings +
                    "Ze assumed $ze_ohm Ω (no transformer source data for this circuit) — verify"
            )
        }
        val freshProt = ProtectionCalculationService.evaluateProtection(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = scResult,
            upstreamIn_A = upstreamIn,
            upstreamName = upstreamName,
            hasUpstreamDevice = hasUpstreamDevice
        )

        val sigAds = computeAdsSignature(circuit, sizingResult, effZe)
        val sigProt = computeProtectionSignature(circuit, sizingResult, scResult, upstreamIn, upstreamName)
        val protPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val protKey = ProjectCircuitKey(protPid, circuit.id)
        val adsKey = ProjectCircuitKey(protPid, circuit.id)
        val scope = CalculationScope(protPid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.PROTECTION_ADS)

        return getPublicationMutex(protPid).withLock {
            val liveRev = getProjectRevisionSuspend(protPid)
            if (liveRev != expectedRevision) {
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, liveRev)
            }

            try {
                getRecorder(allCircuits, allTransformers).recordProtectionAds(protPid, circuitId, freshProt.finalStatus, liveRev)
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Failed("Freshness recorder failed: ${ex.message}")
            }

            val postRev = getProjectRevisionSuspend(protPid)
            if (postRev != expectedRevision) {
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, postRev)
            }

            adsCache[adsKey] = VersionedCacheEntry(sigAds, freshAds, liveRev)
            protectionCache[protKey] = VersionedCacheEntry(sigProt, freshProt, liveRev)

            GuardedCalculationResult.Published(freshProt, liveRev)
        }
    }

    suspend fun calculateCoordinationGuarded(
        circuitId: Int,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity> = emptyList(),
        expectedRevision: Long
    ): GuardedCalculationResult<CoordinationResult> {
        val circuit = allCircuits.firstOrNull { it.id == circuitId }
            ?: return GuardedCalculationResult.Failed("Circuit $circuitId not found in project")

        val sizingResult = calculateCableSizing(circuit, allCircuits, allTransformers)

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
                return GuardedCalculationResult.Failed("Source transformer '${circuit.transformerId}' not found in project")
            }
        } else {
            return GuardedCalculationResult.Failed("Upstream parent device or source transformer not found")
        }

        val fresh = CoordinationCalculationService.calculateCoordination(
            downstreamCircuit = circuit,
            downstreamResult = sizingResult,
            upstreamName = upstreamName,
            upstreamRating = upstreamRating
        )

        val sig = computeCoordinationSignature(circuit, sizingResult, upstreamName, upstreamRating)
        val coordPid = circuit.projectId.ifEmpty { "DEFAULT_PROJECT" }
        val coordKey = ProjectCircuitKey(coordPid, circuit.id)
        val scope = CalculationScope(coordPid, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.COORDINATION)

        return getPublicationMutex(coordPid).withLock {
            val liveRev = getProjectRevisionSuspend(coordPid)
            if (liveRev != expectedRevision) {
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, liveRev)
            }

            try {
                getRecorder(allCircuits, allTransformers).recordCoordination(coordPid, circuitId, fresh.status, liveRev)
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Failed("Freshness recorder failed: ${ex.message}")
            }

            val postRev = getProjectRevisionSuspend(coordPid)
            if (postRev != expectedRevision) {
                freshnessRegistry.removeRecord(scope)
                return@withLock GuardedCalculationResult.Superseded(expectedRevision, postRev)
            }

            coordinationCache[coordKey] = VersionedCacheEntry(sig, fresh, liveRev)

            GuardedCalculationResult.Published(fresh, liveRev)
        }
    }
}

/**
 * Immutable versioned cache container ensuring atomic cache/revision coherence.
 */
data class VersionedCacheEntry<T>(
    val signature: Int,
    val result: T,
    val projectRevision: Long,
    val publishedAt: Long = System.currentTimeMillis()
)

/**
 * Immutable snapshot describing a coherent calculation payload and its publication generation.
 */
data class CoherentCalculationSnapshot<T>(
    val payload: T?,
    val projectRevision: Long,
    val isPublishedCurrent: Boolean,
    val publishedAt: Long
)

sealed class GuardedCalculationResult<out T> {
    data class Published<T>(val result: T, val revision: Long) : GuardedCalculationResult<T>()
    data class Superseded(val expectedRevision: Long, val actualRevision: Long) : GuardedCalculationResult<Nothing>()
    data class Failed(val reason: String) : GuardedCalculationResult<Nothing>()
}


