package com.example.ai.proposal

import com.example.ai.contracts.EngineeringValue
import com.squareup.moshi.JsonClass

/**
 * Standard schema version identifier for Phase 7A engineering proposals.
 */
const val PROPOSAL_SCHEMA_VERSION_V1 = "PROPOSAL-V1"

/**
 * Allowed domain mutation operations for Phase 7A.
 * Initial scope strictly bounded to single-circuit cable and protective device parameter updates.
 */
enum class ProposalOperation {
    UPDATE_CABLE_ATTRIBUTES,
    UPDATE_PROTECTIVE_DEVICE
}

/**
 * Proposal lifecycle state contracts.
 * Contract definitions only; state transitions and execution engines are implemented in subsequent slices.
 */
enum class ProposalLifecycleState {
    DRAFT,
    READY_FOR_APPROVAL,
    APPROVED,
    EXPIRED,
    STALE,
    APPLIED,
    REJECTED,
    INVALID
}

/**
 * Explicit patch field action distinguishing between setting a new value and clearing/resetting an existing override.
 */
enum class PatchAction {
    SET,
    CLEAR
}

/**
 * Typed, Moshi-serializable wrapper representing a proposed field modification.
 * Distinguishes between:
 * 1. Field not proposed (enclosing property is null)
 * 2. Field proposed to a value ([action] = [PatchAction.SET], [value] = value)
 * 3. Field proposed to be cleared ([action] = [PatchAction.CLEAR], [value] = null)
 */
@JsonClass(generateAdapter = true)
data class PatchValue<T>(
    val action: PatchAction = PatchAction.SET,
    val value: T? = null
) {
    companion object {
        fun <T> set(value: T): PatchValue<T> = PatchValue(action = PatchAction.SET, value = value)
        fun <T> clear(): PatchValue<T> = PatchValue(action = PatchAction.CLEAR, value = null)
    }
}

/**
 * Allowlisted cable circuit patch contract for Phase 7A.
 *
 * Strict Invariants:
 * - Exposes ONLY allowlisted engineering fields.
 * - Identity fields (projectId, circuitId, parentId, fedFrom, transformerId) are strictly forbidden and omitted.
 * - Environmental, load, and voltage fields are deferred to Phase 7B and omitted.
 */
@JsonClass(generateAdapter = true)
data class CableCircuitPatch(
    val overrideCableSize: PatchValue<String>? = null,
    val cableType: PatchValue<String>? = null,
    val manufacturer: PatchValue<String>? = null,
    val installation: PatchValue<String>? = null,
    val parallelRuns: PatchValue<Int>? = null,
    val overrideDeviceSize: PatchValue<Double>? = null,
    val overrideDeviceType: PatchValue<String>? = null,
    val lengthM: PatchValue<Double>? = null
) {
    /**
     * Verifies whether at least one field change is proposed in this patch.
     */
    fun hasProposedChanges(): Boolean =
        overrideCableSize != null ||
        cableType != null ||
        manufacturer != null ||
        installation != null ||
        parallelRuns != null ||
        overrideDeviceSize != null ||
        overrideDeviceType != null ||
        lengthM != null

    /**
     * Counts the total number of proposed field modifications.
     */
    fun proposedFieldCount(): Int {
        var count = 0
        if (overrideCableSize != null) count++
        if (cableType != null) count++
        if (manufacturer != null) count++
        if (installation != null) count++
        if (parallelRuns != null) count++
        if (overrideDeviceSize != null) count++
        if (overrideDeviceType != null) count++
        if (lengthM != null) count++
        return count
    }
}

/**
 * Bounded, typed engineering evidence supporting a proposal.
 */
@JsonClass(generateAdapter = true)
data class ProposalEvidenceDto(
    val evidenceType: String,
    val calculationReference: String? = null,
    val governingCriterion: String? = null,
    val values: Map<String, EngineeringValue> = emptyMap()
)

/**
 * Immutable engineering change proposal envelope.
 *
 * Invariants:
 * - Pure data: Contains no executable lambdas, SQL statements, reflection payloads, or DAO references.
 * - Immutability: All properties are read-only vals.
 * - Versioned: Requires explicit [schemaVersion] (default [PROPOSAL_SCHEMA_VERSION_V1]).
 * - Bound: Captures [baseProjectRevision] at creation time for optimistic locking in Slice 7C.
 *
 * @param TPatch The typed, allowlisted patch contract (e.g. [CableCircuitPatch]).
 */
@JsonClass(generateAdapter = true)
data class EngineeringProposal<TPatch>(
    val proposalId: String,
    val schemaVersion: String = PROPOSAL_SCHEMA_VERSION_V1,
    val projectId: String,
    val baseProjectRevision: Long,
    val targetEntityType: String = "CIRCUIT",
    val targetEntityId: String,
    val operation: ProposalOperation,
    val patch: TPatch,
    val rationale: String,
    val sourceToolName: String,
    val sourceRequestId: String,
    val engineeringEvidence: ProposalEvidenceDto? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

/**
 * Standard typealias for single-circuit cable engineering proposals.
 */
typealias CableEngineeringProposal = EngineeringProposal<CableCircuitPatch>

/**
 * Immutable approval grant contract.
 *
 * Invariants:
 * - Pure evidence: Binds directly to [proposalDigest] and [baseProjectRevision].
 * - Non-stateful: Does NOT contain mutable consumption booleans (consumption registry deferred to Slice 7E).
 * - Explicit: Requires human/operator identity in [approvedBy] and timestamp in [approvedAt].
 */
@JsonClass(generateAdapter = true)
data class ProposalApprovalGrant(
    val approvalId: String,
    val proposalId: String,
    val proposalDigest: String,
    val baseProjectRevision: Long,
    val approvedBy: String,
    val approvedAt: Long,
    val expiresAt: Long? = null
)

/**
 * Standard error codes for Phase 7A contract and validation failures.
 */
enum class ProposalErrorCode {
    INVALID_PROPOSAL,
    INVALID_PATCH,
    FORBIDDEN_PATCH_FIELD,
    MISSING_REQUIRED_FIELD,
    INVALID_FIELD_VALUE,
    UNSUPPORTED_OPERATION,
    PROPOSAL_EXPIRED,
    DIGEST_GENERATION_FAILED,
    SERIALIZATION_FAILED
}

/**
 * Detailed validation error record.
 */
@JsonClass(generateAdapter = true)
data class ProposalValidationError(
    val errorCode: ProposalErrorCode,
    val field: String? = null,
    val message: String
)

/**
 * Result container for pure contract validation.
 */
@JsonClass(generateAdapter = true)
data class ProposalValidationResult(
    val isValid: Boolean,
    val errors: List<ProposalValidationError> = emptyList()
) {
    companion object {
        fun valid() = ProposalValidationResult(isValid = true)
        fun invalid(errors: List<ProposalValidationError>) = ProposalValidationResult(isValid = false, errors = errors)
        fun invalid(vararg errors: ProposalValidationError) = ProposalValidationResult(isValid = false, errors = errors.toList())
    }
}
