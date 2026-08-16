package com.example.ai.contracts

/**
 * Execution authorization mode governing tool invocation boundaries.
 */
enum class ExecutionMode {
    /** Read-only inspection of project context, catalogs, or cached results without triggering calculations. */
    READ_ONLY,

    /** Pure deterministic calculation using active project entities; produces results without saving to DB. */
    LIVE_CALCULATION,

    /** Hypothetical simulation evaluating alternative parameters without affecting project state (alias: DRY_RUN). */
    WHAT_IF,

    /** Formulates a structured change proposal for user review and confirmation (alias: PROPOSE_MUTATION). */
    PROPOSAL,

    /** Runs design validation and compliance rule checks without modifying any values. */
    VALIDATE_ONLY,

    /**
     * Direct authorized mutation of project entities in the persistent repository.
     * MUTATION is declarative-only in Sub-Phase 3B and must be rejected by all executors until
     * Proposal, Explicit Approval, Revision Guard, Mutation, and Revalidation contracts are implemented.
     */
    MUTATION
}
