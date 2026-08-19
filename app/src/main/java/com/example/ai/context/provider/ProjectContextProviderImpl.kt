package com.example.ai.context.provider

import com.example.ai.context.freshness.adapters.InMemoryProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.TransformerEntity
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

        // Resolve immediate parent and upstream source safely
        val parentCircuit = if (circuit.parentId != null) {
            allCircuits.find { it.id == circuit.parentId }
        } else {
            allCircuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }
        }

        val parentCircuitId = parentCircuit?.id
        val parentCircuitName = parentCircuit?.circuitName
        val sourceTransformerId = circuit.transformerId.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }

        // Read cached calculation results (pure lookups, zero side-effects)
        val sizingResult = coordinator.getCachedSizingResult(circuitId)
        val shortCircuitResult = coordinator.getCachedShortCircuitResult(circuitId)
        val protectionResult = coordinator.getCachedProtectionResult(circuitId)
        val adsResult = coordinator.getCachedAdsResult(circuitId)
        val coordinationResult = coordinator.getCachedCoordinationResult(circuitId)

        // Obtain freshness evaluator with project data context
        val effectiveFreshness = freshnessProvider ?: coordinator.getFreshnessProvider(
            customDataProvider = InMemoryProjectFreshnessDataProvider(allCircuits, allTransformers),
            customRevisionProvider = revisionProvider ?: repository
        )

        val entityIdStr = circuitId.toString()
        val cableSizingFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.CABLE_SIZING)
        )
        val shortCircuitFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.SHORT_CIRCUIT)
        )
        val protectionFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.PROTECTION_ADS)
        )
        val coordinationFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.CIRCUIT, entityIdStr, CalculationDomain.COORDINATION)
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
            sourceTransformerId = sourceTransformerId,
            cableSizingResult = sizingResult,
            cableSizingFreshness = cableSizingFreshness,
            shortCircuitResult = shortCircuitResult,
            shortCircuitFreshness = shortCircuitFreshness,
            protectionResult = protectionResult,
            adsResult = adsResult,
            protectionFreshness = protectionFreshness,
            coordinationResult = coordinationResult,
            coordinationFreshness = coordinationFreshness
        )
    }

    override suspend fun getTransformerContext(projectId: String, transformerId: String): TransformerContextSnapshot? = withContext(ioDispatcher) {
        val tx = repository.getTransformerById(projectId, transformerId) ?: return@withContext null

        val allCircuits = repository.circuitDao.getCircuitsListForProject(projectId)
        val assignedCount = allCircuits.count {
            it.transformerId == transformerId || it.fedFrom.equals(tx.transformerName, ignoreCase = true)
        }

        val txResult = coordinator.getCachedTransformerResult(transformerId)

        val effectiveFreshness = freshnessProvider ?: coordinator.getFreshnessProvider(
            customDataProvider = InMemoryProjectFreshnessDataProvider(allCircuits, listOf(tx)),
            customRevisionProvider = revisionProvider ?: repository
        )

        val txFreshness = effectiveFreshness.getCalculationState(
            CalculationScope(projectId, CalculationEntityType.TRANSFORMER, transformerId, CalculationDomain.TRANSFORMER)
        )

        val inputs = TransformerConfiguredInputs(
            transformerName = tx.transformerName,
            kVA = tx.kVA,
            voltage = tx.voltage,
            percentZ = tx.percentZ,
            xr = tx.xr,
            vectorGroup = tx.vectorGroup,
            txType = tx.txType,
            sourceType = tx.sourceType
        )

        TransformerContextSnapshot(
            projectId = projectId,
            transformerId = transformerId,
            transformerName = tx.transformerName,
            inputs = inputs,
            transformerResult = txResult,
            transformerFreshness = txFreshness,
            assignedCircuitCount = assignedCount
        )
    }

    override suspend fun getValidationSnapshot(projectId: String): ProjectValidationSnapshot = withContext(ioDispatcher) {
        val circuits = repository.circuitDao.getCircuitsListForProject(projectId)
        val transformers = repository.transformerDao.getTransformersListForProject(projectId)

        val revision = try {
            (revisionProvider ?: repository).getCurrentRevision(projectId).revision
        } catch (_: Exception) {
            1L
        }

        val sizingResults = circuits.mapNotNull { c ->
            coordinator.getCachedSizingResult(c.id)?.let { c.id to it }
        }.toMap()

        val transformerResults = transformers.mapNotNull { t ->
            coordinator.getCachedTransformerResult(t.transformerId)?.let { t.transformerId to it }
        }.toMap()

        // Run real graph topology and network validator
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
        val criticalFailureCount = graphFailures + sizingFailures

        val reviewRequiredCount = graph.validationIssues.count { it.severity == SldValidationStatus.DATA_REQUIRED }

        val graphWarnings = graph.validationIssues.count { it.severity == SldValidationStatus.WARNING }
        val sizingWarnings = sizingResults.values.count { it.status.contains("WARNING") || it.status.contains("⚠") }
        val warningCount = graphWarnings + sizingWarnings

        val overallStatus = when {
            criticalFailureCount > 0 -> "$criticalFailureCount ISSUES REQUIRE ATTENTION"
            reviewRequiredCount > 0 -> "$reviewRequiredCount ITEMS NEED ENGINEERING REVIEW"
            warningCount > 0 -> "$warningCount WARNINGS DETECTED"
            else -> "100% COMPLIANT / PASS"
        }

        val issueItems = mutableListOf<ValidationIssueItem>()

        // 1. Graph validation issues
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

        // 2. Sizing issues
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

        // Cap top issues at 20 items
        val cappedTopIssues = issueItems.take(20)

        ProjectValidationSnapshot(
            projectId = projectId,
            projectRevision = revision,
            overallStatus = overallStatus,
            criticalFailureCount = criticalFailureCount,
            warningCount = warningCount,
            reviewRequiredCount = reviewRequiredCount,
            topIssues = cappedTopIssues
        )
    }
}
