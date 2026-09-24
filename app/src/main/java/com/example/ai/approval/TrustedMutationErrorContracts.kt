package com.example.ai.approval

/**
 * Typed, structured rejection codes for the trusted proposal approval and durable mutation boundary.
 *
 * Purpose (Gate 10):
 * - Gives every pre-commit rejection of an approval request a stable, machine-readable identity
 *   instead of relying on free-form prose.
 * - Is produced exclusively by the trusted application layer
 *   ([TrustedProposalApprovalGateway] / [com.example.ai.proposal.DurableMutationWorkflow]).
 * - Is never produced, selected, or overridden by the conversational model, which remains outside
 *   the authorization boundary.
 *
 * Contract notes:
 * - [messageKey] is a localization key; the UI layer owns the human text.
 * - These codes describe state/contract violations only. No engineering value is computed,
 *   re-derived, or "corrected" when a code is emitted: a rejected request performs zero writes.
 */
enum class TrustedMutationRejectionCode(val messageKey: String) {

    /** Proposal base revision no longer matches the authoritative live project revision. */
    STALE_PROPOSAL("error.approval.stale_proposal"),

    /** Project revision CAS failed immediately before the durable write. */
    REVISION_CONFLICT("error.approval.revision_conflict"),

    /** Persisted value no longer matches the value the proposal was prepared against. */
    CURRENT_VALUE_CHANGED("error.approval.current_value_changed"),

    /** Approval referenced a proposal that does not exist in the trusted boundary. */
    PROPOSAL_NOT_FOUND("error.approval.proposal_not_found"),

    /** Approval was requested for a cancelled proposal. Fails closed. */
    PROPOSAL_CANCELLED("error.approval.proposal_cancelled"),

    /** Patch failed contract, bounds, or merged-catalog validation. */
    INVALID_PATCH("error.approval.invalid_patch"),

    /** Proposal targets a project other than the authoritative active project. */
    WRONG_PROJECT("error.approval.wrong_project"),

    /** Proposal targets an entity other than the authoritative approved entity. */
    WRONG_ENTITY("error.approval.wrong_entity"),

    /** Supporting evidence is missing, stale, or no longer acceptable under existing policy. */
    INVALID_EVIDENCE("error.approval.invalid_evidence"),

    /** Proposed change is already the persisted value. No mutation is created. */
    NO_OP("error.approval.no_op"),

    /** The same semantic mutation was already committed for this proposal signature. */
    DUPLICATE_EXECUTION("error.approval.duplicate_execution"),

    /** Durable mutation failed; zero partial writes were retained. */
    MUTATION_FAILED("error.approval.mutation_failed"),

    /** Mutation committed, but post-commit revalidation did not complete successfully. */
    REVALIDATION_FAILED("error.approval.revalidation_failed")
}
