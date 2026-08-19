package com.example.ai.context.freshness.internal

import com.example.ai.context.freshness.contracts.CalculationScope

/**
 * Internal execution marker state for in-flight calculations.
 */
enum class ExecutionMarkerState {
    RUNNING,
    REVALIDATING
}

/**
 * Internal interface for tracking active calculation execution.
 *
 * State Machine Invariants:
 * - If no prior completed record exists and execution begins -> [ExecutionMarkerState.RUNNING]
 * - If prior completed record exists and execution begins -> [ExecutionMarkerState.REVALIDATING]
 * - Successful or failed completion clears the active marker.
 * - Cancellation clears the active marker and preserves prior record if one existed.
 */
interface CalculationExecutionTracker {

    /**
     * Attempts to mark execution started for a given scope.
     * Returns true if successfully started, false if already actively executing.
     */
    fun startExecution(scope: CalculationScope, hasPriorRecord: Boolean): Boolean

    /**
     * Gets the current execution marker state if execution is active, or null.
     */
    fun getExecutionState(scope: CalculationScope): ExecutionMarkerState?

    /**
     * Clears active execution marker upon completion, failure, or cancellation.
     */
    fun finishExecution(scope: CalculationScope)
}
