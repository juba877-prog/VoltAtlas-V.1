package com.example.ai.context.freshness.contracts

/**
 * Typed metadata recorded when a calculation execution fails.
 *
 * Lifecycle invariants:
 * - Created when execution throws an exception or encounters an unrecoverable error.
 * - A new successful execution for the same scope clears any prior error.
 * - A new [startExecution] for the same scope clears any prior error.
 * - [clearProject] removes all errors for the project.
 * - A prior valid [CalculationRecordMetadata] remains available alongside the error.
 * - The evaluator checks errors after adapter validation but documents the precedence.
 */
data class CalculationExecutionErrorMetadata(
    val scope: CalculationScope,
    val errorCode: ExecutionErrorCode,
    val occurredAt: Long,
    val recoverability: ErrorRecoverability,
    val safeMessage: String? = null,
    val attemptedProjectRevision: Long? = null
)

/**
 * Whether the error condition can be resolved by retrying or requires user intervention.
 */
enum class ErrorRecoverability {
    /** Retrying the calculation may succeed (e.g. transient catalog load failure). */
    RECOVERABLE,

    /** Requires user action to fix (e.g. missing transformer, invalid input). */
    NON_RECOVERABLE
}
