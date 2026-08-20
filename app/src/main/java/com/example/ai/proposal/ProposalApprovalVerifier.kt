package com.example.ai.proposal

/**
 * Result of proposal approval grant verification.
 */
sealed class ApprovalVerificationResult {
    /** Approval grant successfully verified and matched to intent. */
    object Verified : ApprovalVerificationResult()

    /** Approval grant verification failed with explicit reason. */
    data class Rejected(val reason: String) : ApprovalVerificationResult()

    val isVerified: Boolean
        get() = this is Verified
}

/**
 * Interface responsible for verifying that an incoming [ProposalApprovalGrant] binds
 * accurately to a [ValidatedCableProposalIntent] before any database mutation is opened.
 *
 * Invariants:
 * - Pure logic: Does not perform database operations in 7C.
 * - Single-use consumption and replay registry are deferred to Phase 7E.
 */
interface ProposalApprovalVerifier {
    /**
     * Verifies that the given [grant] matches the proposal ID, canonical digest,
     * base project revision, and expiration constraints of the [intent].
     */
    fun verifyApproval(
        intent: ValidatedCableProposalIntent,
        grant: ProposalApprovalGrant,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ApprovalVerificationResult
}

/**
 * Default fail-closed verifier for production runtime.
 * Guarantees mutation execution remains unreachable from public runtime until Phase 7E.
 */
object DenyAllApprovalVerifier : ProposalApprovalVerifier {
    override fun verifyApproval(
        intent: ValidatedCableProposalIntent,
        grant: ProposalApprovalGrant,
        currentTimeMillis: Long
    ): ApprovalVerificationResult {
        return ApprovalVerificationResult.Rejected(
            "Proposal mutation execution is disabled in runtime until Phase 7E approval issuance and authorization."
        )
    }
}
