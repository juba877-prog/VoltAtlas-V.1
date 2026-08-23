package com.example.ai.context.freshness.internal

import com.example.ai.context.freshness.adapters.*
import com.example.ai.context.freshness.contracts.*
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import kotlinx.coroutines.runBlocking

/**
 * Lifecycle integration hook that records freshness metadata after Coordinator calculations.
 *
 * Design:
 * - Stateless adapter: receives calculation inputs and results status, computes fingerprints,
 *   and records metadata synchronously into [InMemoryCalculationFreshnessMetadataRegistry].
 * - In production: uses pre-resolved [circuits] and [transformers] entities for 100% synchronous,
 *   zero-coroutine-overhead in-memory recording.
 * - In testing / legacy contexts: supports [ProjectFreshnessDataProvider] fallback.
 * - Does not alter any engineering inputs, outputs, or PASS/FAIL/WARNING decisions.
 * - Does not store full result payloads.
 */
class FreshnessLifecycleRecorder(
    private val registry: InMemoryCalculationFreshnessMetadataRegistry,
    private val circuits: List<CircuitEntity> = emptyList(),
    private val transformers: List<TransformerEntity> = emptyList(),
    private val projectDataProvider: ProjectFreshnessDataProvider? = null
) {

    constructor(
        registry: InMemoryCalculationFreshnessMetadataRegistry,
        projectDataProvider: ProjectFreshnessDataProvider
    ) : this(
        registry = registry,
        circuits = emptyList(),
        transformers = emptyList(),
        projectDataProvider = projectDataProvider
    )

    private val cableAdapter = CableSizingFreshnessAdapter()
    private val transformerAdapter = TransformerFreshnessAdapter()
    private val scAdapter = ShortCircuitFreshnessAdapter()
    private val protectionAdapter = ProtectionAdsFreshnessAdapter()
    private val coordinationAdapter = CoordinationFreshnessAdapter()

    private val upstreamProvider = object : UpstreamFingerprintProvider {
        override fun getRecordedFingerprint(scope: CalculationScope): CalculationFingerprint? {
            return registry.getRecord(scope)?.fingerprint
        }
    }

    /**
     * Records cable sizing freshness metadata after a successful calculation.
     * [overallStatus] is the engineering status (e.g. "PASS", "FAIL", "WARNING").
     */
    fun recordCableSizing(
        projectId: String,
        circuitId: Int,
        overallStatus: String,
        projectRevision: Long
    ) {
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.CABLE_SIZING)
        recordDomain(scope, cableAdapter, overallStatus, projectRevision)
    }

    /**
     * Records transformer freshness metadata after a successful calculation.
     */
    fun recordTransformer(
        projectId: String,
        transformerId: String,
        overallStatus: String,
        projectRevision: Long
    ) {
        val scope = CalculationScope(projectId, CalculationEntityType.TRANSFORMER, transformerId, CalculationDomain.TRANSFORMER)
        recordDomain(scope, transformerAdapter, overallStatus, projectRevision)
    }

    /**
     * Records short circuit freshness metadata after a successful calculation.
     */
    fun recordShortCircuit(
        projectId: String,
        circuitId: Int,
        overallStatus: String,
        projectRevision: Long
    ) {
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.SHORT_CIRCUIT)
        recordDomain(scope, scAdapter, overallStatus, projectRevision)
    }

    /**
     * Records protection/ADS freshness metadata after a successful calculation.
     */
    fun recordProtectionAds(
        projectId: String,
        circuitId: Int,
        overallStatus: String,
        projectRevision: Long
    ) {
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.PROTECTION_ADS)
        recordDomain(scope, protectionAdapter, overallStatus, projectRevision)
    }

    /**
     * Records coordination freshness metadata after a successful calculation.
     */
    fun recordCoordination(
        projectId: String,
        circuitId: Int,
        overallStatus: String,
        projectRevision: Long
    ) {
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, circuitId.toString(), CalculationDomain.COORDINATION)
        recordDomain(scope, coordinationAdapter, overallStatus, projectRevision)
    }

    /**
     * Records execution error for a given scope.
     */
    fun recordError(
        scope: CalculationScope,
        errorCode: ExecutionErrorCode,
        safeMessage: String?,
        projectRevision: Long?
    ) {
        registry.recordCalculationFailed(
            scope,
            CalculationExecutionErrorMetadata(
                scope = scope,
                errorCode = errorCode,
                occurredAt = System.currentTimeMillis(),
                recoverability = when (errorCode) {
                    ExecutionErrorCode.CATALOG_UNAVAILABLE -> ErrorRecoverability.RECOVERABLE
                    else -> ErrorRecoverability.NON_RECOVERABLE
                },
                safeMessage = safeMessage,
                attemptedProjectRevision = projectRevision
            )
        )
    }

    private fun recordDomain(
        scope: CalculationScope,
        adapter: DomainFreshnessAdapter,
        overallStatus: String,
        projectRevision: Long
    ) {
        try {
            val resolution = if (projectDataProvider != null) {
                runBlocking {
                    adapter.resolveFingerprint(scope, projectDataProvider, upstreamProvider)
                }
            } else {
                adapter.resolveFingerprintFromEntities(scope, circuits, transformers, upstreamProvider)
            }
            if (resolution is FingerprintResolution.Resolved) {
                registry.recordCalculationCompleted(
                    CalculationRecordMetadata(
                        scope = scope,
                        calculatedProjectRevision = projectRevision,
                        fingerprint = resolution.fingerprint,
                        calculatedAt = System.currentTimeMillis(),
                        overallStatus = overallStatus,
                        provenance = CalculationProvenance(
                            engineName = adapter.supportedDomain.name,
                            isVerified = true
                        )
                    )
                )
            }
        } catch (_: Exception) {
            // Fingerprint resolution failure during recording is non-fatal
            // The calculation result is already returned to the caller
        }
    }
}
