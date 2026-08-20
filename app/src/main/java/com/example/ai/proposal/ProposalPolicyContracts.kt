package com.example.ai.proposal

import com.squareup.moshi.JsonClass

/**
 * Policy defining whether a patch field supports clearing back to automatic sizing / default,
 * or requires an explicit concrete value when proposed.
 */
enum class FieldClearPolicy {
    /** Field must be supplied with an explicit, valid non-null value (e.g. cable material, parallel runs). */
    SET_ONLY,

    /** Field can be either overridden with a new value or explicitly cleared to reset to automatic sizing. */
    SET_OR_CLEAR
}

/**
 * Comprehensive, fail-closed error codes for Phase 7B policy and executable-intent validation.
 */
enum class ProposalPolicyErrorCode {
    INVALID_PROPOSAL,
    INVALID_PATCH,
    EMPTY_PATCH,
    SET_REQUIRES_VALUE,
    CLEAR_REQUIRES_NULL,
    CLEAR_NOT_ALLOWED,
    FIELD_NOT_ALLOWED_FOR_OPERATION,
    MIXED_OPERATION_FIELDS,
    UNSUPPORTED_OPERATION,
    INVALID_FIELD_VALUE,
    INVALID_SCHEMA_VERSION,
    INVALID_BASE_REVISION,
    PROPOSAL_EXPIRED,
    DIGEST_GENERATION_FAILED
}

/**
 * Detailed structured error record for proposal policy validation failures.
 */
@JsonClass(generateAdapter = true)
data class ProposalPolicyError(
    val errorCode: ProposalPolicyErrorCode,
    val field: String? = null,
    val message: String
)

/**
 * Normalized, validated instruction for a single patch field.
 */
@JsonClass(generateAdapter = true)
data class FieldInstruction<T>(
    val fieldName: String,
    val action: PatchAction,
    val value: T? = null
)

/**
 * Normalized collection of validated field instructions for a single-circuit cable patch.
 */
@JsonClass(generateAdapter = true)
data class CablePatchInstructions(
    val overrideCableSize: FieldInstruction<String>? = null,
    val cableType: FieldInstruction<String>? = null,
    val manufacturer: FieldInstruction<String>? = null,
    val installation: FieldInstruction<String>? = null,
    val parallelRuns: FieldInstruction<Int>? = null,
    val overrideDeviceSize: FieldInstruction<Double>? = null,
    val overrideDeviceType: FieldInstruction<String>? = null,
    val lengthM: FieldInstruction<Double>? = null
) {
    /**
     * Returns the list of all active (non-null) field instructions.
     */
    fun activeInstructions(): List<FieldInstruction<*>> {
        val list = mutableListOf<FieldInstruction<*>>()
        overrideCableSize?.let { list.add(it) }
        cableType?.let { list.add(it) }
        manufacturer?.let { list.add(it) }
        installation?.let { list.add(it) }
        parallelRuns?.let { list.add(it) }
        overrideDeviceSize?.let { list.add(it) }
        overrideDeviceType?.let { list.add(it) }
        lengthM?.let { list.add(it) }
        return list
    }
}

/**
 * Standard policy version identifier for Phase 7B intent validation.
 */
const val PROPOSAL_POLICY_VERSION_V1 = "POLICY-V1"

/**
 * Pure, immutable, non-executable data contract representing an engineering proposal
 * that has passed all structural, compatibility, bounds, action, and digest checks.
 *
 * Truthfulness Invariants:
 * - Does NOT mean approved (approval grant is a separate future workflow).
 * - Does NOT mean current revision (revision check is executed inside Room transactions in Slice 7C).
 * - Does NOT mean target entity exists in persistent database.
 * - Does NOT enable [ExecutionMode.MUTATION].
 * - Contains NO executable lambdas, SQL, repository/DAO references, or reflection payloads.
 */
@JsonClass(generateAdapter = true)
data class ValidatedProposalIntent<out TInstruction>(
    val validatedIntentId: String,
    val proposalId: String,
    val proposalDigest: String,
    val schemaVersion: String,
    val projectId: String,
    val baseProjectRevision: Long,
    val targetEntityType: String,
    val targetEntityId: String,
    val operation: ProposalOperation,
    val instructions: TInstruction,
    val sourceToolName: String,
    val sourceRequestId: String,
    val validatedAt: Long,
    val policyVersion: String = PROPOSAL_POLICY_VERSION_V1
)

/**
 * Standard typealias for validated cable circuit proposal intents.
 */
typealias ValidatedCableProposalIntent = ValidatedProposalIntent<CablePatchInstructions>

/**
 * Sealed result type representing the outcome of proposal policy and intent validation.
 */
sealed class ProposalPolicyResult<out T> {
    data class Success<T>(val validatedIntent: ValidatedProposalIntent<T>) : ProposalPolicyResult<T>()
    data class Failure(val errors: List<ProposalPolicyError>) : ProposalPolicyResult<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): ValidatedProposalIntent<T>? = (this as? Success<T>)?.validatedIntent

    fun errorsOrEmpty(): List<ProposalPolicyError> = (this as? Failure)?.errors ?: emptyList()
}
