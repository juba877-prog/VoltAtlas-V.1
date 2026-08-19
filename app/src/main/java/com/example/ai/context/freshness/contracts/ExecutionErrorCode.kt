package com.example.ai.context.freshness.contracts

/**
 * Typed error codes for calculation execution failures.
 *
 * These represent deterministic, machine-readable categories of execution errors.
 * They do NOT represent engineering PASS/FAIL outcomes, which are valid calculation completions.
 */
enum class ExecutionErrorCode {
    /** Engine threw an unrecoverable runtime exception (e.g. numerical divergence). */
    ENGINE_RUNTIME_ERROR,

    /** Required entity or dependency was missing at execution time. */
    MISSING_DEPENDENCY,

    /** Input validation failed before calculation could begin. */
    INVALID_INPUT,

    /** Catalog or reference data was unavailable. */
    CATALOG_UNAVAILABLE,

    /** Generic/unknown execution failure. */
    UNKNOWN
}
