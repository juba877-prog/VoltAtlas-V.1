package com.example.ai.context.provider

import com.example.ai.context.freshness.adapters.InMemoryProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.TransformerEntity
import com.example.engine.*
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import com.example.sld.ElectricalNetworkGraphBuilder
import com.example.sld.SldValidationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure read-only implementation of [ProjectContextProvider] (Sub-Phase 3F).
 *
 * Composes data from:
 * - [EngineeringRepository] (read-only entity lookups and revision provider)
 * - [EngineeringCalculationCoordinator] (cached calculation results and cycle-guarded ancestor resolution)
 * - [CalculationFreshnessProvider] (authoritative 3D freshness evaluations)
 * - [ElectricalNetworkGraphBuilder] (real network graph and compliance validation findings)
 *
 * Invariants:
 * - Performs zero mutations on repository, Room database, or Coordinator caches.
 * - Does not trigger lazy recomputation; cached results are read as-is (null if uncalculated).
 * - Caches are strictly project-scoped (no cross-project result contamination).
 * - Revision failures return null without fabricating a placeholder revision.
 * - Uncalculated projects report UNCALCULATED / CALCULATIONS_MISSING (never FULLY_COMPLIANT).
 * - Exposes zero mutation methods.
 */
class ProjectContextProviderImpl(
    private val repository: EngineeringRepository,
    private val coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator,
    private val freshnessProvider: CalculationFreshnessProvider? = null,
    private val revisionProvider: ProjectRevisionProvider? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProjectContextProvider {

    override suspend fun getCircuitContext(projectId: String, circuitId: Int): CircuitContextSnapshot? = withContext(ioDispatcher) {
        val circuit = repository.circuitDao.getCircuitById(circuitId) ?: return@withContext null
        if (circuit.projectId != projectId) return@withContext null

        val allCircuits = repository.circuitDao.getCircuitsListForProject(projectId)
        val allTransformers = repository.transformerDao.getTransformersListForProject(projectId)

        // 1. Authoritative parent resolution using shared cycle-guarded resolver
        val parentResolution = ParentResolutionResolver.resolveParent(circuit, allCircuits)
        val parentCircuit = when (parentResolution) {
            is ParentResolution.Resolved -> parentResolution.parent
            else -> null
        }
        val parentCircuitId = parentCircuit?.id
        val parentCircuitName = parentCircuit?.circuitName
        val sourceTransformerId = circuit.transformerId.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }

        // 2. Read cached calculation results (strictly project-scoped, zero side-effects)
        val sizingResult = coordinator.getCachedSizingResult(projectId, circuitId)
        val shortCircuitResult = coordinator.getCachedShortCircuitResult(projectId, circuitId)
        val protectionResult = coordinator.getCachedProtectionResult(projectId, circuitId)
        val adsResult = coordinator.getCachedAdsResult(projectId, circuitId)
        val coordinationResult = coordinator.getCachedCoordinationResult(projectId, circuitId)

        // 3. Obtain freshness evaluator with project data context
        val effectiveFreshness = freshnessProvider ?: coordinator.getFreshnessProvider(
            customDataProvider = InMemoryProjectFreshnessDataProvider(allCircuits, allTransformers),
            customRevisionProvider = revisionProvider ?: repository
        )

        val entityIdStr = circuitId.toString()
        val sizingFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.CABLE_SIZING)
        )
        val scFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.SHORT_CIRCUIT)
        )
        val protFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.PROTECTION_ADS)
        )
        val coordFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.COORDINATION)
        )

        // 4. Map engine results to pure context DTOs and attach typed usability
        val cableSizingContextResult = mapCableSizing(sizingResult)
        val shortCircuitContextResult = mapShortCircuit(shortCircuitResult)
        val protectionAdsContextResult = mapProtectionAds(protectionResult, adsResult)
        val coordinationContextResult = mapCoordination(coordinationResult)

        val cableSizingWrapper = ContextResult(
            result = cableSizingContextResult,
            freshness = sizingFreshness,
            usability = resolveUsability(cableSizingContextResult, sizingFreshness)
        )
        val shortCircuitWrapper = ContextResult(
            result = shortCircuitContextResult,
            freshness = scFreshness,
            usability = resolveUsability(shortCircuitContextResult, scFreshness)
        )
        val protectionAdsWrapper = ContextResult(
            result = protectionAdsContextResult,
            freshness = protFreshness,
            usability = resolveUsability(protectionAdsContextResult, protFreshness)
        )
        val coordinationWrapper = ContextResult(
            result = coordinationContextResult,
            freshness = coordFreshness,
            usability = resolveUsability(coordinationContextResult, coordFreshness)
        )

        val inputs = CircuitConfiguredInputs(
            circuitName = circuit.circuitName,
            loadKVA = circuit.loadKVA,
            voltageV = circuit.voltageV,
            phases = circuit.phases,
            pf = circuit.pf,
            safetyFactor = circuit.safetyFactor,
            length_m = circuit.length_m,
            maxVDpct = circuit.maxVDpct,
            parallelRuns = circuit.parallelRuns,
            feederType = circuit.feederType,
            cableType = circuit.cableType,
            manufacturer = circuit.manufacturer,
            installation = circuit.installation,
            airTempC = circuit.airTempC,
            gndTempC = circuit.gndTempC,
            burialDepth_m = circuit.burialDepth_m,
            soilRes = circuit.soilRes,
            noCircuits = circuit.noCircuits,
            formation = circuit.formation,
            spacing = circuit.spacing,
            fedFrom = circuit.fedFrom,
            transformerId = circuit.transformerId,
            overrideDeviceType = circuit.overrideDeviceType,
            overrideDeviceSize = circuit.overrideDeviceSize,
            overrideCableSize = circuit.overrideCableSize,
            overrideNeutralSize = circuit.overrideNeutralSize,
            overrideEarthSize = circuit.overrideEarthSize
        )

        CircuitContextSnapshot(
            projectId = projectId,
            circuitId = circuitId,
            circuitName = circuit.circuitName,
            inputs = inputs,
            parentCircuitId = parentCircuitId,
            parentCircuitName = parentCircuitName,
            parentResolution = parentResolution,
            sourceTransformerId = sourceTransformerId,
            cableSizing = cableSizingWrapper,
            shortCircuit = shortCircuitWrapper,
            protectionAds = protectionAdsWrapper,
            coordination = coordinationWrapper
        )
    }

    override suspend fun getTransformerContext(projectId: String, transformerId: String): TransformerContextSnapshot? = withContext(ioDispatcher) {
        val tx = repository.getTransformerById(projectId, transformerId) ?: return@withContext null

        val allCircuits = repository.circuitDao.getCircuitsListForProject(projectId)
        val assignedCircuits = TransformerAssignmentResolver.resolveAssignedCircuits(tx, allCircuits)
        val assignedCount = assignedCircuits.size

        val txResult = coordinator.getCachedTransformerResult(projectId, transformerId)

        val effectiveFreshness = freshnessProvider ?: coordinator.getFreshnessProvider(
            customDataProvider = InMemoryProjectFreshnessDataProvider(allCircuits, listOf(tx)),
            customRevisionProvider = revisionProvider ?: repository
        )

        val txFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.TRANSFORMER, transformerId, CalculationDomain.TRANSFORMER)
        )

        val txContextResult = mapTransformer(txResult)
        val txWrapper = ContextResult(
            result = txContextResult,
            freshness = txFreshness,
            usability = resolveUsability(txContextResult, txFreshness)
        )

        val inputs = TransformerConfiguredInputs(
            transformerId = tx.transformerId,
            transformerName = tx.transformerName,
            kVA = tx.kVA,
            voltage = tx.voltage,
            percentZ = tx.percentZ,
            xr = tx.xr,
            txType = tx.txType,
            sourceType = tx.sourceType,
            status = tx.status,
            sizeMode = tx.sizeMode,
            manualKVA = tx.manualKVA,
            demandFactor = tx.demandFactor,
            diversity = tx.diversity,
            growth = tx.growth,
            safety = tx.safety,
            vectorGroup = tx.vectorGroup,
            z0OverZ1 = tx.z0OverZ1,
            pf = tx.pf
        )

        TransformerContextSnapshot(
            projectId = projectId,
            transformerId = transformerId,
            transformerName = tx.transformerName,
            inputs = inputs,
            transformerResult = txWrapper,
            assignedCircuitCount = assignedCount
        )
    }

    override suspend fun getValidationSnapshot(projectId: String): ProjectValidationSnapshot? = withContext(ioDispatcher) {
        // 1. Authoritative project revision resolution — fails fast if project does not exist
        val project = repository.projectDao.getProject(projectId) ?: return@withContext null
        val revision = try {
            (revisionProvider ?: repository).getCurrentRevision(projectId).revision
        } catch (_: Exception) {
            project.revision
        }

        val circuits = repository.circuitDao.getCircuitsListForProject(projectId)
        val transformers = repository.transformerDao.getTransformersListForProject(projectId)

        val sizingResults = circuits.mapNotNull { c ->
            coordinator.getCachedSizingResult(projectId, c.id)?.let { c.id to it }
        }.toMap()

        val transformerResults = transformers.mapNotNull { t ->
            coordinator.getCachedTransformerResult(projectId, t.transformerId)?.let { t.transformerId to it }
        }.toMap()

        val effectiveFreshness = freshnessProvider ?: coordinator.getFreshnessProvider(
            customDataProvider = InMemoryProjectFreshnessDataProvider(circuits, transformers),
            customRevisionProvider = revisionProvider ?: repository
        )

        var calculatedCircuitCount = 0
        var staleCircuitCount = 0
        var missingCircuitCount = 0
        var anyDomainCalculatedCount = 0

        for (circuit in circuits) {
            var hasMissing = false
            var hasStale = false
            var allCurrent = true

            // Applicable domains: root circuits without upstream protection do not require COORDINATION
            val applicableDomains = mutableListOf(
                CalculationDomain.CABLE_SIZING,
                CalculationDomain.SHORT_CIRCUIT,
                CalculationDomain.PROTECTION_ADS
            )
            val hasUpstream = circuit.parentId != null ||
                    (circuit.fedFrom.isNotBlank() && circuit.fedFrom.trim().lowercase() !in listOf("grid", "source", "main", "generator")) ||
                    circuit.transformerId.isNotBlank()
            if (hasUpstream) {
                applicableDomains.add(CalculationDomain.COORDINATION)
            }

            for (domain in applicableDomains) {
                val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, circuit.id.toString(), domain)
                val st = effectiveFreshness.getCalculationState(scope)
                when (st.state) {
                    CalculationState.CURRENT -> {
                        anyDomainCalculatedCount++
                    }
                    CalculationState.STALE -> {
                        hasStale = true
                        allCurrent = false
                    }
                    CalculationState.MISSING -> {
                        hasMissing = true
                        allCurrent = false
                    }
                    else -> {
                        allCurrent = false
                    }
                }
            }

            when {
                hasMissing -> missingCircuitCount++
                hasStale -> staleCircuitCount++
                allCurrent -> calculatedCircuitCount++
            }
        }

        var calculatedTxCount = 0
        var staleTxCount = 0
        var missingTxCount = 0

        for (tx in transformers) {
            val scope = CalculationScope(projectId, CalculationEntityType.TRANSFORMER, tx.transformerId, CalculationDomain.TRANSFORMER)
            val st = effectiveFreshness.getCalculationState(scope)
            when (st.state) {
                CalculationState.CURRENT -> {
                    calculatedTxCount++
                    anyDomainCalculatedCount++
                }
                CalculationState.STALE -> staleTxCount++
                CalculationState.MISSING -> missingTxCount++
                else -> {}
            }
        }

        // 2. Run real network topology and graph validator
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            sizingResults = sizingResults,
            transformerResults = transformerResults
        )

        val graphFailures = graph.validationIssues.count {
            it.severity == SldValidationStatus.INVALID || it.severity == SldValidationStatus.DISCONNECTED
        }
        val sizingFailures = sizingResults.values.count { it.status.contains("FAIL") || it.status.contains("✗") }

        // Also check cached short circuit and protection results for failures
        val scFailures = circuits.count { c ->
            coordinator.getCachedShortCircuitResult(projectId, c.id)?.let { it.status.contains("FAIL") || it.status.contains("MISSING") } == true
        }
        val protFailures = circuits.count { c ->
            coordinator.getCachedProtectionResult(projectId, c.id)?.let { it.finalStatus.contains("FAIL") } == true
        }

        val criticalFailureCount = graphFailures + sizingFailures + scFailures + protFailures

        val reviewRequiredCount = graph.validationIssues.count { it.severity == SldValidationStatus.DATA_REQUIRED }

        val graphWarnings = graph.validationIssues.count { it.severity == SldValidationStatus.WARNING }
        val sizingWarnings = sizingResults.values.count { it.status.contains("WARNING") || it.status.contains("⚠") }
        val coordWarnings = circuits.count { c ->
            coordinator.getCachedCoordinationResult(projectId, c.id)?.let { it.status.contains("WARN") } == true
        }
        val warningCount = graphWarnings + sizingWarnings + coordWarnings

        // 3. Determine truthful engineering status
        val engineeringStatus = when {
            criticalFailureCount > 0 -> {
                if (graphFailures > 0 && sizingFailures == 0 && scFailures == 0 && protFailures == 0) {
                    ProjectEngineeringStatus.TOPOLOGY_FAILURE
                } else {
                    ProjectEngineeringStatus.ENGINEERING_FAILURE
                }
            }
            circuits.isEmpty() && transformers.isEmpty() -> ProjectEngineeringStatus.UNCALCULATED
            missingCircuitCount > 0 || missingTxCount > 0 -> {
                if (anyDomainCalculatedCount == 0) ProjectEngineeringStatus.UNCALCULATED
                else ProjectEngineeringStatus.CALCULATIONS_MISSING
            }
            staleCircuitCount > 0 || staleTxCount > 0 -> ProjectEngineeringStatus.CALCULATIONS_STALE
            reviewRequiredCount > 0 -> ProjectEngineeringStatus.REVIEW_REQUIRED
            warningCount > 0 -> {
                if (graphWarnings > 0 && sizingWarnings == 0 && coordWarnings == 0) {
                    ProjectEngineeringStatus.TOPOLOGY_WARNING
                } else {
                    ProjectEngineeringStatus.ENGINEERING_WARNING
                }
            }
            else -> ProjectEngineeringStatus.FULLY_COMPLIANT
        }

        val overallStatusSummary = when (engineeringStatus) {
            ProjectEngineeringStatus.FULLY_COMPLIANT -> "100% COMPLIANT / PASS"
            ProjectEngineeringStatus.UNCALCULATED -> "Project contains uncalculated circuits and transformers"
            ProjectEngineeringStatus.CALCULATIONS_MISSING -> "$missingCircuitCount circuits / $missingTxCount transformers have missing calculation results across engineering domains"
            ProjectEngineeringStatus.CALCULATIONS_STALE -> "$staleCircuitCount circuits / $staleTxCount transformers have stale calculations requiring re-run"
            ProjectEngineeringStatus.ENGINEERING_FAILURE -> "$criticalFailureCount engineering issues require attention"
            ProjectEngineeringStatus.TOPOLOGY_FAILURE -> "$graphFailures topology issues require attention"
            ProjectEngineeringStatus.REVIEW_REQUIRED -> "$reviewRequiredCount items need engineering review"
            ProjectEngineeringStatus.ENGINEERING_WARNING, ProjectEngineeringStatus.TOPOLOGY_WARNING -> "$warningCount warnings detected"
            else -> "Engineering validation review required"
        }

        val issueItems = mutableListOf<ValidationIssueItem>()

        // Graph validation issues
        for (issue in graph.validationIssues) {
            issueItems.add(
                ValidationIssueItem(
                    entityId = issue.affectedNodeId,
                    domain = "TOPOLOGY",
                    severity = issue.severity.name,
                    description = "${issue.componentName}: ${issue.failedCondition}"
                )
            )
        }

        // Sizing issues
        for ((circuitId, res) in sizingResults) {
            if (res.status.contains("FAIL") || res.status.contains("✗") || res.failReason.isNotBlank()) {
                issueItems.add(
                    ValidationIssueItem(
                        entityId = circuitId.toString(),
                        domain = "CABLE_SIZING",
                        severity = if (res.status.contains("FAIL") || res.status.contains("✗")) "INVALID" else "WARNING",
                        description = res.failReason.ifBlank { "Cable sizing check failed" }
                    )
                )
            }
        }

        // Missing / stale calculation findings
        if (missingCircuitCount > 0) {
            issueItems.add(
                ValidationIssueItem(
                    entityId = null,
                    domain = "CALCULATION_STATUS",
                    severity = "WARNING",
                    description = "$missingCircuitCount circuits have incomplete/missing domain calculations"
                )
            )
        }
        if (staleCircuitCount > 0) {
            issueItems.add(
                ValidationIssueItem(
                    entityId = null,
                    domain = "CALCULATION_STATUS",
                    severity = "WARNING",
                    description = "$staleCircuitCount circuits have stale calculations"
                )
            )
        }

        val cappedTopIssues = issueItems.take(20)

        ProjectValidationSnapshot(
            projectId = projectId,
            projectRevision = revision,
            engineeringStatus = engineeringStatus,
            overallStatusSummary = overallStatusSummary,
            totalCircuits = circuits.size,
            totalTransformers = transformers.size,
            calculatedCircuitCount = calculatedCircuitCount,
            staleCircuitCount = staleCircuitCount,
            missingCircuitCount = missingCircuitCount,
            criticalFailureCount = criticalFailureCount,
            warningCount = warningCount,
            reviewRequiredCount = reviewRequiredCount,
            topIssues = cappedTopIssues
        )
    }

    // =========================================================================
    // Pure Result Mapping Helpers (Zero Engine Recomputation)
    // =========================================================================

    private fun mapCableSizing(res: SizingResult?): CableSizingContextResult? {
        if (res == null) return null
        return CableSizingContextResult(
            status = res.status,
            failReason = res.failReason,
            Ib_A = res.Ib_A,
            deviceSize_A = res.deviceSize_A,
            deviceType = res.deviceType,
            cableSize_mm2 = res.cableSize_mm2,
            cTotalDerating = res.cTotalDerating,
            ratedI_table_A = res.ratedI_table_A,
            capacity_derated_Iz_A = res.capacity_derated_Iz_A,
            utilizationPct = res.utilizationPct,
            voltDrop_V = res.voltDrop_V,
            vdPct = res.vdPct,
            cumulativeVDpct = res.cumulativeVDpct,
            neutral_mm2 = res.neutral_mm2,
            earthCPC_mm2 = res.earthCPC_mm2,
            cableMaterial = res.cableMaterial,
            insulation = res.insulation,
            cableOD_mm = res.cableOD_mm,
            earthOD_mm = res.earthOD_mm
        )
    }

    private fun mapTransformer(res: TransformerResult?): TransformerContextResult? {
        if (res == null) return null
        return TransformerContextResult(
            status = res.status,
            connectedLoadKVA = res.connectedLoad,
            circuitsAssigned = res.circuitsAssigned,
            demandLoadKVA = res.demandLoad,
            diversifiedLoadKVA = res.diversifiedLoad,
            futureLoadKVA = res.futureLoad,
            designLoadKVA = res.designLoad,
            recommendedSizeKVA = res.recommendedSize,
            selectedSizeKVA = res.selectedSize,
            loadingPct = res.loadingPct,
            flcA = res.flc,
            ztOhms = res.zt,
            rtOhms = res.rt,
            xtOhms = res.xt,
            isc3PhaseKA = res.isc3Phase,
            isc1PhaseKA = res.isc1Phase
        )
    }

    private fun mapShortCircuit(res: ShortCircuitResult?): ShortCircuitContextResult? {
        if (res == null) return null
        return ShortCircuitContextResult(
            status = res.status,
            iscKA = res.iscKA,
            iwKA = res.iwKA,
            deviceKA = res.deviceKA,
            deviceCheck = res.deviceCheck,
            cableCheck = res.cableCheck,
            marginPct = res.marginPct,
            note = res.note,
            ipKA = res.ipKA,
            zTotalMOhm = res.zTotalMOhm,
            rMOhm = res.rMOhm,
            xMOhm = res.xMOhm
        )
    }

    private fun mapProtectionAds(prot: ProtectionResult?, ads: AdsResult?): ProtectionAdsContextResult? {
        if (prot == null && ads == null) return null
        return ProtectionAdsContextResult(
            finalStatus = prot?.finalStatus ?: ads?.status ?: "MISSING",
            overloadStatus = prot?.overloadStatus ?: "MISSING",
            shortCircuitStatus = prot?.shortCircuitStatus ?: "MISSING",
            cableWithstandStatus = prot?.cableWithstandStatus ?: "MISSING",
            selectivityStatus = prot?.selectivityStatus ?: "MISSING",
            adsStatus = ads?.status ?: "MISSING",
            calculatedZs = ads?.calculatedZs ?: 0.0,
            maxZsAllowed = ads?.maxZsAllowed ?: 0.0,
            disconnectionTime = ads?.disconnectionTime ?: "",
            messages = prot?.messages ?: emptyList()
        )
    }

    private fun mapCoordination(coord: CoordinationResult?): CoordinationContextResult? {
        if (coord == null) return null
        return CoordinationContextResult(
            downstreamCircuitId = coord.downstreamCircuitId,
            downstreamName = coord.downstreamName,
            upstreamName = coord.upstreamName,
            ratio = coord.ratio,
            status = coord.status,
            reason = coord.reason
        )
    }

    private fun resolveUsability(res: Any?, freshness: CalculationStateSnapshot): ResultUsability {
        return when (freshness.state) {
            CalculationState.CURRENT -> {
                if (res == null) ResultUsability.MISSING else ResultUsability.CURRENT
            }
            CalculationState.STALE -> {
                if (res == null) {
                    ResultUsability.MISSING
                } else if (freshness.reasonCode == FreshnessReasonCode.PROVENANCE_INCOMPLETE) {
                    ResultUsability.PARTIAL
                } else {
                    ResultUsability.STALE
                }
            }
            CalculationState.MISSING -> ResultUsability.MISSING
            CalculationState.INVALID -> ResultUsability.INVALID
            CalculationState.RUNNING -> ResultUsability.RUNNING
            CalculationState.REVALIDATING -> ResultUsability.REVALIDATING
            CalculationState.ERROR -> ResultUsability.ERROR
        }
    }
}
