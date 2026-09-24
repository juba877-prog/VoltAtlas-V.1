package com.example.ai.evidence

import com.example.ai.contracts.EngineeringAssumption
import com.example.ai.contracts.EngineeringCheck
import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.ToolStatus
import com.example.ai.tools.ToolExecutionOutcome
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CableWhatIfResult
import com.squareup.moshi.JsonClass

/**
 * Immutable, strongly typed point-in-time engineering evidence container (Gate 6).
 *
 * Invariants:
 * - Built ONLY from verified, deterministic [ToolExecutionOutcome] / [com.example.ai.contracts.ToolResultEnvelope].
 *   Never instantiated from LLM predictions, intents, or unexecuted tool requests (Correction #9).
 * - Strictly immutable data class (Correction #11).
 * - Distinguishes execution status ([executionStatus]) from domain engineering criteria status (Correction #5).
 * - Zero fabricated metadata: fields unavailable from the underlying envelope/provenance remain null (Correction #10).
 */
@JsonClass(generateAdapter = true)
data class EngineeringEvidenceRecord(
    val evidenceId: String,
    val requestId: String,
    val conversationId: String? = null,
    val toolName: String,
    val projectId: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val projectRevision: Long? = null,
    val executionStatus: ToolStatus,
    val result: Any? = null,
    val checks: List<EngineeringCheck> = emptyList(),
    val assumptions: List<EngineeringAssumption> = emptyList(),
    val provenance: ProvenanceMetadata? = null,
    val freshnessState: String = "CURRENT",
    val engineVersion: String? = null,
    val isScenario: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {

    /**
     * Verifies whether this evidence record is valid, current, and relevant for the targeted scope.
     */
    fun isCurrentAndValidFor(
        targetProjectId: String,
        targetEntityId: String? = null,
        liveRevision: Long? = null,
        domain: String? = null
    ): Boolean {
        // Must belong to the same project
        if (projectId != targetProjectId) return false

        // Must match entity if both are specified
        if (targetEntityId != null && entityId != null && entityId != targetEntityId) return false

        // Must not be stale compared to live revision
        if (liveRevision != null && projectRevision != null && projectRevision != liveRevision) return false
        if (freshnessState.equals("STALE", ignoreCase = true) || executionStatus == ToolStatus.STALE) return false

        // Must represent a successful execution outcome (PASS, WARNING, FAIL are valid calculations; ERROR/INVALID_INPUT are not)
        if (executionStatus != ToolStatus.PASS &&
            executionStatus != ToolStatus.WARNING &&
            executionStatus != ToolStatus.FAIL
        ) {
            return false
        }

        // Must match targeted domain if specified
        if (domain != null && !isDomainRelevant(domain)) return false

        return true
    }

    /**
     * Checks if this evidence record corresponds to the given engineering domain.
     */
    fun isDomainRelevant(targetDomain: String): Boolean {
        val canonicalDomain = targetDomain.trim().uppercase()
        return when (canonicalDomain) {
            "CABLE_SIZING", "CABLE" -> toolName.startsWith("calculate_cable") ||
                    toolName.startsWith("evaluate_cable") ||
                    toolName.startsWith("compare_cable") ||
                    toolName == "run_what_if"
            "READ_CONTEXT", "PROJECT_CONTEXT" -> toolName == "get_current_project_context" ||
                    toolName == "get_entity" ||
                    toolName == "query_project_entities"
            else -> toolName.contains(canonicalDomain.lowercase())
        }
    }

    val domain: String
        get() = when {
            isDomainRelevant("CABLE_SIZING") -> "CABLE_SIZING"
            isDomainRelevant("TRANSFORMER") -> "TRANSFORMER"
            isDomainRelevant("SHORT_CIRCUIT") -> "SHORT_CIRCUIT"
            isDomainRelevant("PROTECTION_ADS") -> "PROTECTION_ADS"
            isDomainRelevant("COORDINATION") -> "COORDINATION"
            else -> "READ_CONTEXT"
        }

    /**
     * Convenience helper specifically checking relevance to single-circuit cable sizing.
     */
    fun isRelevantForCableSizing(targetProjectId: String, targetCircuitId: String?): Boolean {
        if (!isDomainRelevant("CABLE_SIZING")) return false
        if (projectId != targetProjectId) return false
        if (targetCircuitId != null && entityId != null && entityId != targetCircuitId) return false
        return true
    }

    companion object {
        /**
         * Authoritative factory creating an [EngineeringEvidenceRecord] from a real [ToolExecutionOutcome].
         */
        fun fromOutcome(
            outcome: ToolExecutionOutcome,
            conversationId: String? = null,
            entityType: String? = null,
            entityId: String? = null,
            freshnessState: String = "CURRENT"
        ): EngineeringEvidenceRecord {
            val env = outcome.envelope

            // Extract entity identity from result payload if available
            val derivedEntityId = entityId ?: when (val res = env.result) {
                is CableSizingExecutionResult -> res.circuitId
                is CableCandidateEvaluationResult -> res.circuitId
                is CableCandidatesComparisonResult -> res.circuitId
                is CableWhatIfResult -> res.circuitId
                else -> null
            }
            val derivedEntityType = entityType ?: if (derivedEntityId != null) "CIRCUIT" else null

            val derivedFreshness = when (val res = env.result) {
                is CableSizingExecutionResult -> res.freshnessState
                is CableWhatIfResult -> res.baselineFreshness
                else -> freshnessState
            }

            val derivedIsScenario = outcome.toolName == CableToolNames.RUN_WHAT_IF ||
                    env.result is CableWhatIfResult

            return EngineeringEvidenceRecord(
                evidenceId = "EVD-" + outcome.requestId,
                requestId = outcome.requestId,
                conversationId = conversationId,
                toolName = outcome.toolName,
                projectId = env.projectId,
                entityType = derivedEntityType,
                entityId = derivedEntityId,
                projectRevision = env.projectRevision,
                executionStatus = env.status,
                result = env.result,
                checks = env.checks,
                assumptions = env.assumptions,
                provenance = env.provenance,
                freshnessState = derivedFreshness,
                engineVersion = env.provenance?.engineVersion,
                isScenario = derivedIsScenario,
                timestamp = env.completedAt ?: System.currentTimeMillis()
            )
        }
    }
}

/**
 * Derived extension property/method checking whether valid current backing evidence exists (Correction #4).
 * Replaces indiscriminate `evidence.isNotEmpty()` checks.
 */
fun List<EngineeringEvidenceRecord>.hasCurrentBackingEvidence(
    projectId: String,
    entityId: String? = null,
    liveRevision: Long? = null,
    domain: String? = null
): Boolean = any { it.isCurrentAndValidFor(projectId, entityId, liveRevision, domain) }
