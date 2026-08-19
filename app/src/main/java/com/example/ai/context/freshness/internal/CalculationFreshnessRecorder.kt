package com.example.ai.context.freshness.internal

import com.example.ai.context.freshness.contracts.CalculationExecutionErrorMetadata
import com.example.ai.context.freshness.contracts.CalculationRecordMetadata
import com.example.ai.context.freshness.contracts.CalculationScope

/**
 * Internal write boundary used strictly by calculation engines / coordinator
 * to record completed calculation metadata.
 *
 * Invariants:
 * - NOT exposed to AI-facing tools or prompt context.
 * - Records calculation metadata only, never raw result payloads.
 *
 * Error lifecycle:
 * - [recordCalculationFailed] stores typed error metadata.
 * - [recordCalculationCompleted] clears any prior error for the same scope.
 * - [startExecution] (via [CalculationExecutionTracker]) clears any prior error.
 * - A prior valid record remains available alongside the error.
 */
interface CalculationFreshnessRecorder {

    /**
     * Records completed calculation metadata and clears any active execution marker and prior error.
     */
    fun recordCalculationCompleted(metadata: CalculationRecordMetadata)

    /**
     * Records typed execution error and clears any active execution marker.
     * Prior valid record is retained.
     */
    fun recordCalculationFailed(scope: CalculationScope, error: CalculationExecutionErrorMetadata)

    /**
     * Clears all calculation records, errors, and active markers for a specific project.
     */
    fun clearProject(projectId: String)

    /**
     * Clears all calculation records, errors, and active markers across all projects.
     */
    fun clearAll()
}
