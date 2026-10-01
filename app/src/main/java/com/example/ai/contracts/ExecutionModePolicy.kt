package com.example.ai.contracts

/**
 * Pure contract execution policy governing the executability of execution modes.
 * Prevents treating the presence of ExecutionMode.MUTATION in the enum as authorization.
 */
object ExecutionModePolicy {
    /**
     * Determines whether a given execution mode can currently be dispatched.
     *
     * PERMANENT INVARIANT (Phase 6 Batch 4 / P2-11, finding J-09 + AU-W-03):
     * [ExecutionMode.MUTATION] is PERMANENTLY non-executable through this policy.
     * The previous wording scoped this guard to an early delivery phase and implied a later
     * phase would silently lift it. That reading was wrong and dangerous: direct mutation would
     * bypass the trusted proposal/approval/revision pipeline
     * (AssistantToolRuntime -> EngineeringToolDispatcher -> EngineeringToolRegistry ->
     * deterministic invoker -> authoritative VoltAtlas tools/repositories/Room).
     *
     * MUTATION requests are not "blocked pending a phase". They must be expressed as
     * [ExecutionMode.PROPOSAL] and executed only through
     * `TrustedProposalApprovalGateway`, which requires explicit user approval and revision
     * guarding. Flipping this to `true` would be a safety regression, not a feature.
     */
    fun isCurrentlyExecutable(mode: ExecutionMode): Boolean {
        return when (mode) {
            ExecutionMode.READ_ONLY -> true
            ExecutionMode.LIVE_CALCULATION -> true
            ExecutionMode.WHAT_IF -> true
            ExecutionMode.VALIDATE_ONLY -> true
            ExecutionMode.PROPOSAL -> true // Formulating a proposal for review is executable
            // Permanently non-executable via this policy - see the KDoc above. MUTATION must be
            // expressed as PROPOSAL and pass the trusted approval gateway instead.
            ExecutionMode.MUTATION -> false
        }
    }

    /**
     * Identifies whether the mode requires explicit user approval and revision locking.
     */
    fun requiresApprovalAndRevisionGuard(mode: ExecutionMode): Boolean {
        return mode == ExecutionMode.MUTATION
    }
}
