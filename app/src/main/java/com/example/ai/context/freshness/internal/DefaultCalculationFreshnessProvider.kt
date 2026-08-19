package com.example.ai.context.freshness.internal

import com.example.ai.context.freshness.adapters.*
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.revision.ProjectRevisionProvider

/**
 * Authoritative deterministic calculation freshness provider.
 *
 * Corrected Evaluation Decision Order:
 * 1. Resolve authoritative current project revision.
 * 2. Resolve current entity/dependencies via domain adapter.
 *    - Invalid → INVALID (takes precedence over execution markers)
 *    - Error → ERROR
 *    - Partial → cannot return CURRENT
 * 3. Inspect active execution markers.
 *    - RUNNING (no prior record)
 *    - REVALIDATING (prior record exists)
 * 4. Inspect recorded execution error.
 * 5. Locate recorded metadata.
 * 6. Compare deterministic fingerprints.
 * 7. Return CURRENT / STALE / MISSING.
 *
 * Key design note on RUNNING/REVALIDATING and INVALID:
 * If an entity's current inputs are invalid (entity deleted, fields invalid),
 * the evaluator reports INVALID regardless of whether an execution marker is active.
 * This is because the evaluator describes the current project state,
 * not the state of a captured in-flight request.
 */
class DefaultCalculationFreshnessProvider(
    private val revisionProvider: ProjectRevisionProvider,
    private val metadataRegistry: InMemoryCalculationFreshnessMetadataRegistry,
    private val projectDataProvider: ProjectFreshnessDataProvider,
    private val adapters: Map<CalculationDomain, DomainFreshnessAdapter> = mapOf(
        CalculationDomain.CABLE_SIZING to CableSizingFreshnessAdapter(),
        CalculationDomain.TRANSFORMER to TransformerFreshnessAdapter(),
        CalculationDomain.SHORT_CIRCUIT to ShortCircuitFreshnessAdapter(),
        CalculationDomain.PROTECTION_ADS to ProtectionAdsFreshnessAdapter(),
        CalculationDomain.COORDINATION to CoordinationFreshnessAdapter()
    )
) : CalculationFreshnessProvider {

    /**
     * Provides upstream fingerprint lookup from the registry for downstream adapter chaining.
     */
    private val upstreamFingerprintProvider = object : UpstreamFingerprintProvider {
        override fun getRecordedFingerprint(scope: CalculationScope): CalculationFingerprint? {
            return metadataRegistry.getRecord(scope)?.fingerprint
        }
    }

    override suspend fun getCalculationState(scope: CalculationScope): CalculationStateSnapshot {
        // 1. Resolve current authoritative project revision
        val currentRevision: Long
        try {
            val snapshot = revisionProvider.getCurrentRevision(scope.projectId)
            currentRevision = snapshot.revision
        } catch (e: NoSuchElementException) {
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.ERROR,
                currentProjectRevision = -1L,
                reasonCode = FreshnessReasonCode.PROJECT_CONTEXT_CHANGED,
                reason = "Project '${scope.projectId}' could not be resolved: ${e.message}"
            )
        } catch (e: Exception) {
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.ERROR,
                currentProjectRevision = -1L,
                reasonCode = FreshnessReasonCode.EVALUATION_ERROR,
                reason = "Failed to resolve project revision: ${e.message}"
            )
        }

        // 2. Resolve current entity/dependencies via adapter BEFORE checking execution markers
        val adapter = adapters[scope.domain]
            ?: return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.ERROR,
                currentProjectRevision = currentRevision,
                reasonCode = FreshnessReasonCode.EVALUATION_ERROR,
                reason = "No adapter registered for domain ${scope.domain}"
            )

        val resolution = adapter.resolveFingerprint(scope, projectDataProvider, upstreamFingerprintProvider)
        val currentFingerprint: CalculationFingerprint
        val coverage: FingerprintCoverage

        when (resolution) {
            is FingerprintResolution.Invalid -> {
                // INVALID takes precedence over RUNNING/REVALIDATING
                return CalculationStateSnapshot(
                    scope = scope,
                    state = CalculationState.INVALID,
                    currentProjectRevision = currentRevision,
                    changedFieldPaths = resolution.invalidFieldPaths,
                    reasonCode = FreshnessReasonCode.INVALID_INPUTS,
                    reason = resolution.reason
                )
            }
            is FingerprintResolution.Error -> {
                return CalculationStateSnapshot(
                    scope = scope,
                    state = CalculationState.ERROR,
                    currentProjectRevision = currentRevision,
                    reasonCode = FreshnessReasonCode.EVALUATION_ERROR,
                    reason = resolution.message
                )
            }
            is FingerprintResolution.Resolved -> {
                currentFingerprint = resolution.fingerprint
                coverage = resolution.coverage
            }
        }

        // Partial coverage cannot claim formal CURRENT
        if (coverage == FingerprintCoverage.PARTIAL) {
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.STALE,
                currentProjectRevision = currentRevision,
                currentFingerprint = currentFingerprint,
                changedComponents = setOf(ChangedComponent.PROVENANCE),
                reasonCode = FreshnessReasonCode.PROVENANCE_INCOMPLETE,
                reason = "Domain ${scope.domain} has partial fingerprint coverage and cannot guarantee CURRENT state."
            )
        }

        // 3. Inspect active execution / revalidation markers
        val executionState = metadataRegistry.getExecutionState(scope)
        if (executionState == ExecutionMarkerState.RUNNING) {
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.RUNNING,
                currentProjectRevision = currentRevision,
                currentFingerprint = currentFingerprint,
                reasonCode = FreshnessReasonCode.CALCULATION_IN_PROGRESS,
                reason = "Initial calculation for scope $scope is currently executing."
            )
        } else if (executionState == ExecutionMarkerState.REVALIDATING) {
            val existing = metadataRegistry.getRecord(scope)
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.REVALIDATING,
                currentProjectRevision = currentRevision,
                currentFingerprint = currentFingerprint,
                recordedProjectRevision = existing?.calculatedProjectRevision,
                recordedFingerprint = existing?.fingerprint,
                lastCalculatedAt = existing?.calculatedAt,
                recordedStatus = existing?.overallStatus,
                reasonCode = FreshnessReasonCode.RECALCULATION_IN_PROGRESS,
                reason = "Recalculation for scope $scope is currently executing; prior result exists."
            )
        }

        // 4. Inspect recorded execution errors
        val recordedError = metadataRegistry.getError(scope)
        if (recordedError != null) {
            val existing = metadataRegistry.getRecord(scope)
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.ERROR,
                currentProjectRevision = currentRevision,
                currentFingerprint = currentFingerprint,
                recordedProjectRevision = existing?.calculatedProjectRevision,
                recordedFingerprint = existing?.fingerprint,
                lastCalculatedAt = existing?.calculatedAt,
                recordedStatus = existing?.overallStatus,
                reasonCode = FreshnessReasonCode.EVALUATION_ERROR,
                reason = "Calculation execution failed: [${recordedError.errorCode}] ${recordedError.safeMessage ?: "No details"}"
            )
        }

        // 5. Locate recorded metadata for the requested scope
        val record = metadataRegistry.getRecord(scope)
            ?: return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.MISSING,
                currentProjectRevision = currentRevision,
                currentFingerprint = currentFingerprint,
                reasonCode = FreshnessReasonCode.NO_RECORD,
                reason = "No calculation record exists for scope $scope."
            )

        // 6. Compare deterministic fingerprints
        val changedComponents = mutableSetOf<ChangedComponent>()
        val changedFieldPaths = mutableListOf<String>()

        if (record.fingerprint.inputSignature != currentFingerprint.inputSignature) {
            changedComponents.add(ChangedComponent.DIRECT_INPUT)
            changedFieldPaths.add("directInputs")
        }
        if (record.fingerprint.dependencySignature != currentFingerprint.dependencySignature) {
            changedComponents.add(ChangedComponent.UPSTREAM_DEPENDENCY)
            changedFieldPaths.add("upstreamDependencies")
        }
        if (record.fingerprint.methodologySignature != currentFingerprint.methodologySignature) {
            changedComponents.add(ChangedComponent.METHODOLOGY)
            changedFieldPaths.add("methodology")
        }
        if (record.fingerprint.engineSignature != currentFingerprint.engineSignature) {
            changedComponents.add(ChangedComponent.ENGINE_VERSION)
            changedFieldPaths.add("engineVersion")
        }
        if (!record.provenance.isVerified) {
            changedComponents.add(ChangedComponent.PROVENANCE)
            changedFieldPaths.add("provenance")
        }

        // 7. Decide state: CURRENT vs STALE
        if (changedComponents.isEmpty()) {
            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.CURRENT,
                currentProjectRevision = currentRevision,
                recordedProjectRevision = record.calculatedProjectRevision,
                currentFingerprint = currentFingerprint,
                recordedFingerprint = record.fingerprint,
                changedComponents = emptySet(),
                changedFieldPaths = emptyList(),
                reasonCode = FreshnessReasonCode.MATCH,
                reason = "Calculation result matches current authoritative inputs, dependencies, and methodology.",
                lastCalculatedAt = record.calculatedAt,
                recordedStatus = record.overallStatus
            )
        } else {
            val primaryReasonCode = when {
                ChangedComponent.DIRECT_INPUT in changedComponents -> FreshnessReasonCode.INPUT_CHANGED
                ChangedComponent.UPSTREAM_DEPENDENCY in changedComponents -> FreshnessReasonCode.DEPENDENCY_CHANGED
                ChangedComponent.METHODOLOGY in changedComponents -> FreshnessReasonCode.METHODOLOGY_CHANGED
                ChangedComponent.ENGINE_VERSION in changedComponents -> FreshnessReasonCode.ENGINE_CHANGED
                else -> FreshnessReasonCode.PROJECT_CONTEXT_CHANGED
            }

            return CalculationStateSnapshot(
                scope = scope,
                state = CalculationState.STALE,
                currentProjectRevision = currentRevision,
                recordedProjectRevision = record.calculatedProjectRevision,
                currentFingerprint = currentFingerprint,
                recordedFingerprint = record.fingerprint,
                changedComponents = changedComponents,
                changedFieldPaths = changedFieldPaths,
                reasonCode = primaryReasonCode,
                reason = "Calculation state is STALE due to changes in: ${changedComponents.joinToString(", ")}.",
                lastCalculatedAt = record.calculatedAt,
                recordedStatus = record.overallStatus
            )
        }
    }
}
