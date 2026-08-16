package com.example.ai.contracts

/**
 * Pure contract execution policy governing the executability of execution modes.
 * Prevents treating the presence of ExecutionMode.MUTATION in the enum as authorization.
 */
object ExecutionModePolicy {
    /**
     * Determines whether a given execution mode can currently be dispatched.
     * MUTATION is strictly non-executable in Phase 3 until Phase 7 establishes
     * EngineeringProposalManager, explicit approval tokens, and EngineeringRevisionGuard.
     */
    fun isCurrentlyExecutable(mode: ExecutionMode): Boolean {
        return when (mode) {
            ExecutionMode.READ_ONLY -> true
            ExecutionMode.LIVE_CALCULATION -> true
            ExecutionMode.WHAT_IF -> true
            ExecutionMode.VALIDATE_ONLY -> true
            ExecutionMode.PROPOSAL -> true // Formulating a proposal for review is executable
            ExecutionMode.MUTATION -> false // Mutation is strictly non-executable until Phase 7
        }
    }

    /**
     * Identifies whether the mode requires explicit user approval and revision locking.
     */
    fun requiresApprovalAndRevisionGuard(mode: ExecutionMode): Boolean {
        return mode == ExecutionMode.MUTATION
    }
}
