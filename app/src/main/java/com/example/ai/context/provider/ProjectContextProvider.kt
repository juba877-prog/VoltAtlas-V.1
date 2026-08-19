package com.example.ai.context.provider

import com.example.ai.context.freshness.contracts.CalculationStateSnapshot
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity

/**
 * Result usability status qualifying how an AI tool or consumer may consume a calculation result.
 */
enum class ResultUsability {
    CURRENT,
    STALE,
    PARTIAL,
    MISSING,
    INVALID,
    RUNNING,
    REVALIDATING,
    ERROR
}

/**
 * Strongly typed wrapper uniting an engineering result with its point-in-time freshness and usability.
 */
data class ContextResult<T>(
    val result: T?,
    val freshness: CalculationStateSnapshot,
    val usability: ResultUsability
)

/**
 * Bounded, pure engineering inputs for a single circuit entity snapshot.
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
 * Bounded Cable Sizing calculation result contract owned by the AI Context layer (zero engine imports).
 */
data class CableSizingContextResult(
    val status: String,
    val failReason: String,
    val Ib_A: Double,
    val deviceSize_A: Double,
    val deviceType: String,
    val cableSize_mm2: String,
    val cTotalDerating: Double,
    val ratedI_table_A: Double,
    val capacity_derated_Iz_A: Double,
    val utilizationPct: Double,
    val voltDrop_V: Double,
    val vdPct: Double,
    val cumulativeVDpct: Double,
    val neutral_mm2: String,
    val earthCPC_mm2: String,
    val cableMaterial: String,
    val insulation: String,
    val cableOD_mm: Double,
    val earthOD_mm: Double
)

/**
 * Bounded Transformer calculation result contract owned by the AI Context layer.
 */
data class TransformerContextResult(
    val status: String,
    val connectedLoadKVA: Double,
    val circuitsAssigned: Int,
    val demandLoadKVA: Double,
    val diversifiedLoadKVA: Double,
    val futureLoadKVA: Double,
    val designLoadKVA: Double,
    val recommendedSizeKVA: Double,
    val selectedSizeKVA: Double,
    val loadingPct: Double,
    val flcA: Double,
    val ztOhms: Double,
    val rtOhms: Double,
    val xtOhms: Double,
    val isc3PhaseKA: Double,
    val isc1PhaseKA: Double
)

/**
 * Bounded Short Circuit calculation result contract owned by the AI Context layer.
 */
data class ShortCircuitContextResult(
    val status: String,
    val iscKA: Double,
    val iwKA: Double,
    val deviceKA: Double,
    val deviceCheck: String,
    val cableCheck: String,
    val marginPct: Double,
    val note: String,
    val ipKA: Double,
    val zTotalMOhm: Double,
    val rMOhm: Double,
    val xMOhm: Double
)

/**
 * Bounded Protection and ADS calculation result contract owned by the AI Context layer.
 */
data class ProtectionAdsContextResult(
    val finalStatus: String,
    val overloadStatus: String,
    val shortCircuitStatus: String,
    val cableWithstandStatus: String,
    val selectivityStatus: String,
    val adsStatus: String,
    val calculatedZs: Double,
    val maxZsAllowed: Double,
    val disconnectionTime: String,
    val messages: List<String>
)

/**
 * Bounded Protection Coordination calculation result contract owned by the AI Context layer.
 */
data class CoordinationContextResult(
    val downstreamCircuitId: Int,
    val downstreamName: String,
    val upstreamName: String,
    val ratio: Double,
    val status: String,
    val reason: String
)

/**
 * Typed resolution outcome for parent circuit lookup.
 */
sealed interface ParentResolution {
    data class Resolved(val parent: CircuitEntity) : ParentResolution
    data object Root : ParentResolution
    data class Ambiguous(val candidateIds: List<Int>) : ParentResolution
    data class InvalidCycle(val visitedIds: List<Int>) : ParentResolution
    data class MissingReference(val reference: String) : ParentResolution
}

/**
 * Authoritative parent resolution helper shared between Coordinator and Context Provider.
 */
object ParentResolutionResolver {
    fun resolveParent(
        circuit: CircuitEntity,
        allCircuits: List<CircuitEntity>
    ): ParentResolution {
        val projectCircuits = allCircuits.filter { it.projectId == circuit.projectId }

        // 1. Explicit parentId precedence (including self-parent cycle check)
        if (circuit.parentId != null) {
            if (circuit.parentId == circuit.id) {
                return ParentResolution.InvalidCycle(listOf(circuit.id))
            }
            val parent = projectCircuits.find { it.id == circuit.parentId }
            if (parent == null) {
                val otherProjectParent = allCircuits.find { it.id == circuit.parentId }
                return if (otherProjectParent != null && otherProjectParent.projectId != circuit.projectId) {
                    ParentResolution.MissingReference("Parent ID ${circuit.parentId} belongs to different project (${otherProjectParent.projectId})")
                } else {
                    ParentResolution.MissingReference("Parent ID ${circuit.parentId} not found in project ${circuit.projectId}")
                }
            }
            return ParentResolution.Resolved(parent)
        }

        // 2. Root source keywords
        val fedLower = circuit.fedFrom.trim().lowercase()
        if (fedLower in listOf("transformer", "ats", "generator", "grid", "source", "main", "")) {
            return ParentResolution.Root
        }

        // 3. Name-based match within project
        val candidates = projectCircuits.filter {
            it.id != circuit.id && it.circuitName.trim().equals(circuit.fedFrom.trim(), ignoreCase = true)
        }

        return when {
            candidates.isEmpty() -> ParentResolution.MissingReference(circuit.fedFrom)
            candidates.size == 1 -> ParentResolution.Resolved(candidates.first())
            else -> ParentResolution.Ambiguous(candidates.map { it.id })
        }
    }
}

/**
 * Authoritative transformer circuit assignment resolver shared across Coordinator, Freshness, and Context Provider.
 */
object TransformerAssignmentResolver {
    fun resolveAssignedCircuits(
        transformer: TransformerEntity,
        circuits: List<CircuitEntity>
    ): List<CircuitEntity> {
        val txIdClean = transformer.transformerId.trim().lowercase()
        return circuits.filter { c ->
            if (c.projectId != transformer.projectId) return@filter false
            val cTxId = c.transformerId.trim().lowercase()
            if (cTxId.isNotEmpty()) {
                cTxId == txIdClean
            } else {
                val fedClean = c.fedFrom.trim().lowercase()
                fedClean == txIdClean || fedClean in listOf("transformer", "ats", "generator", "grid")
            }
        }.sortedBy { it.id }
    }
}

/**
 * Transient, read-only context snapshot for a single circuit.
 * Composes configured inputs, upstream dependency references, cached domain results with usability,
 * and authoritative freshness snapshots without triggering recomputation.
 */
data class CircuitContextSnapshot(
    val projectId: String,
    val circuitId: Int,
    val circuitName: String,
    val inputs: CircuitConfiguredInputs,
    val parentCircuitId: Int?,
    val parentCircuitName: String?,
    val parentResolution: ParentResolution,
    val sourceTransformerId: String?,
    val cableSizing: ContextResult<CableSizingContextResult>,
    val shortCircuit: ContextResult<ShortCircuitContextResult>,
    val protectionAds: ContextResult<ProtectionAdsContextResult>,
    val coordination: ContextResult<CoordinationContextResult>
)

/**
 * Bounded engineering inputs for a single transformer entity snapshot.
 * Includes all 18 calculation determinants consumed by the deterministic transformer engine.
 */
data class TransformerConfiguredInputs(
    val transformerId: String,
    val transformerName: String,
    val kVA: Double,
    val voltage: Int,
    val percentZ: Double,
    val xr: Double,
    val txType: String,
    val sourceType: String,
    val status: String,
    val sizeMode: String,
    val manualKVA: Double,
    val demandFactor: Double,
    val diversity: Double,
    val growth: Double,
    val safety: Double,
    val vectorGroup: String,
    val z0OverZ1: Double,
    val pf: Double
)

/**
 * Transient, read-only context snapshot for a single transformer.
 */
data class TransformerContextSnapshot(
    val projectId: String,
    val transformerId: String,
    val transformerName: String,
    val inputs: TransformerConfiguredInputs,
    val transformerResult: ContextResult<TransformerContextResult>,
    val assignedCircuitCount: Int
)

/**
 * Explicit project engineering compliance status.
 */
enum class ProjectEngineeringStatus {
    FULLY_COMPLIANT,
    CALCULATIONS_MISSING,
    CALCULATIONS_STALE,
    CALCULATIONS_PARTIAL,
    ENGINEERING_FAILURE,
    ENGINEERING_WARNING,
    TOPOLOGY_FAILURE,
    TOPOLOGY_WARNING,
    REVIEW_REQUIRED,
    UNCALCULATED,
    ERROR
}

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
    val engineeringStatus: ProjectEngineeringStatus,
    val overallStatusSummary: String,
    val totalCircuits: Int,
    val totalTransformers: Int,
    val calculatedCircuitCount: Int,
    val staleCircuitCount: Int,
    val missingCircuitCount: Int,
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
     * Returns `null` if the project does not exist or its authoritative revision is unavailable.
     */
    suspend fun getValidationSnapshot(projectId: String): ProjectValidationSnapshot?
}
