package com.example.ai.contracts

/**
 * Authoritative lifecycle and outcome status for engineering tool execution.
 */
enum class ToolStatus {
    /** Deterministic calculation executed successfully and all engineering criteria were satisfied. */
    PASS,

    /** Deterministic calculation executed successfully, but one or more engineering compliance checks failed. */
    FAIL,

    /** Calculation executed, but non-fatal engineering warnings or marginal conditions were identified. */
    WARNING,

    /** Calculation could not run because mandatory project inputs or upstream sources are missing. */
    INCOMPLETE,

    /** Provided input parameters violate physical constraints, schemas, or contain circular dependencies. */
    INVALID_INPUT,

    /** The returned result is from a prior calculation and no longer matches current project inputs or revision. */
    STALE,

    /** Asynchronous calculation or workflow is currently in progress. */
    RUNNING,

    /** Freshness evaluation or background re-validation is in progress. */
    REVALIDATING,

    /** The requested operation or calculation domain is not supported by the current engine version. */
    NOT_SUPPORTED,

    /** An unrecoverable runtime exception, database I/O error, or internal execution defect occurred. */
    ERROR,

    /** Execution was intentionally cancelled before completion. */
    CANCELLED
}
