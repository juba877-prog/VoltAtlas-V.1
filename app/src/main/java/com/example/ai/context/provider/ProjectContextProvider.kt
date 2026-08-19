package com.example.ai.context.provider

import com.example.ai.context.freshness.contracts.CalculationStateSnapshot
import com.example.engine.*

/**
 * Bounded engineering inputs for a single circuit entity snapshot.
 * Contains only relevant electrical, cable, and environmental configuration parameters.
 */
data class CircuitConfiguredInputs(
    val circuitName: String,
    val loadKVA: Double,
    val voltageV: Int,
    val phases: String,
    val pf: Double,
    val safetyFactor: Double,
    val length_m: Double,
    val maxVDpct: Double,
    val parallelRuns: Int,
    val feederType: String,
    val cableType: String,
    val manufacturer: String,
    val installation: String,
    val airTempC: Int,
    val gndTempC: Int,
    val burialDepth_m: Double,
    val soilRes: Int,
    val noCircuits: Int,
    val formation: String,
    val spacing: String,
    val fedFrom: String,
    val transformerId: String,
    val overrideDeviceType: String? = null,
    val overrideDeviceSize: Double? = null,
    val overrideCableSize: String? = null,
    val overrideNeutralSize: String? = null,
    val overrideEarthSize: String? = null
)

/**
 * Transient, read-only context snapshot for a single circuit.
 * Composes configured inputs, upstream dependency references, cached domain results,
 * and authoritative freshness snapshots without triggering recomputation.
 */
data class CircuitContextSnapshot(
    val projectId: String,
    val circuitId: Int,
    val circuitName: String,
    val inputs: CircuitConfiguredInputs,
    val parentCircuitId: Int?,
    val parentCircuitName: String?,
    val sourceTransformerId: String?,
    val cableSizingResult: SizingResult?,
    val cableSizingFreshness: CalculationStateSnapshot,
    val shortCircuitResult: ShortCircuitResult?,
    val shortCircuitFreshness: CalculationStateSnapshot,
    val protectionResult: ProtectionResult?,
    val adsResult: AdsResult?,
    val protectionFreshness: CalculationStateSnapshot,
    val coordinationResult: CoordinationResult?,
    val coordinationFreshness: CalculationStateSnapshot
)

/**
 * Bounded engineering inputs for a single transformer entity snapshot.
 */
data class TransformerConfiguredInputs(
    val transformerName: String,
    val kVA: Double,
    val voltage: Int,
    val percentZ: Double,
    val xr: Double,
    val vectorGroup: String,
    val txType: String,
    val sourceType: String
)

/**
 * Transient, read-only context snapshot for a single transformer.
 */
data class TransformerContextSnapshot(
    val projectId: String,
    val transformerId: String,
    val transformerName: String,
    val inputs: TransformerConfiguredInputs,
    val transformerResult: TransformerResult?,
    val transformerFreshness: CalculationStateSnapshot,
    val assignedCircuitCount: Int
)

/**
 * Single validation or compliance finding item in the project validation snapshot.
 */
data class ValidationIssueItem(
    val entityId: String?,
    val domain: String,
    val severity: String,
    val description: String
)

/**
 * Authoritative, non-hard-coded validation and compliance summary for a project.
 */
data class ProjectValidationSnapshot(
    val projectId: String,
    val projectRevision: Long,
    val overallStatus: String,
    val criticalFailureCount: Int,
    val warningCount: Int,
    val reviewRequiredCount: Int,
    val topIssues: List<ValidationIssueItem>
)

/**
 * Pure read-only Project Context Provider interface (Sub-Phase 3F).
 *
 * Facade that composes existing repository state, Coordinator result caches,
 * 3D freshness evaluations, and network graph validations without introducing
 * new persistent stores or triggering side-effect recalculations.
 */
interface ProjectContextProvider {

    /**
     * Reads the complete context snapshot for a specific circuit.
     * Returns `null` if the circuit does not exist in the specified project.
     */
    suspend fun getCircuitContext(projectId: String, circuitId: Int): CircuitContextSnapshot?

    /**
     * Reads the complete context snapshot for a specific transformer.
     * Returns `null` if the transformer does not exist in the specified project.
     */
    suspend fun getTransformerContext(projectId: String, transformerId: String): TransformerContextSnapshot?

    /**
     * Reads the project validation and compliance summary derived from real graph and sizing validators.
     */
    suspend fun getValidationSnapshot(projectId: String): ProjectValidationSnapshot
}
