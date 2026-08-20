package com.example.ai.context.freshness.internal

import com.example.ai.context.freshness.contracts.CalculationExecutionErrorMetadata
import com.example.ai.context.freshness.contracts.CalculationRecordMetadata
import com.example.ai.context.freshness.contracts.CalculationScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Temporary in-memory registry for calculation metadata and active execution markers.
 *
 * Scope and Lifecycle Invariants:
 * - Stores [CalculationRecordMetadata] and [ExecutionMarkerState] keyed by [CalculationScope].
 * - Stores typed [CalculationExecutionErrorMetadata] keyed by [CalculationScope].
 * - Thread-safe across concurrent readers and writers.
 * - Does NOT persist results in Room or across process recreation (process-local only).
 * - Does NOT store full engineering result payloads (metadata only).
 * - Cleans up reliably via [clearProject] or [clearAll].
 *
 * Error lifecycle:
 * - [startExecution] clears any prior error for the scope.
 * - [recordCalculationCompleted] clears any prior error for the scope.
 * - [recordCalculationFailed] stores typed error metadata; prior valid record is retained.
 * - A new execution supersedes the error.
 * - [clearProject] removes errors, metadata, and markers.
 */
class InMemoryCalculationFreshnessMetadataRegistry : CalculationFreshnessRecorder, CalculationExecutionTracker {

    private val metadataStore = ConcurrentHashMap<CalculationScope, CalculationRecordMetadata>()
    private val activeExecutionStore = ConcurrentHashMap<CalculationScope, ExecutionMarkerState>()
    private val errorStore = ConcurrentHashMap<CalculationScope, CalculationExecutionErrorMetadata>()

    fun getRecord(scope: CalculationScope): CalculationRecordMetadata? {
        return metadataStore[scope]
    }

    fun removeRecord(scope: CalculationScope) {
        metadataStore.remove(scope)
        activeExecutionStore.remove(scope)
        errorStore.remove(scope)
    }

    fun getError(scope: CalculationScope): CalculationExecutionErrorMetadata? {
        return errorStore[scope]
    }

    override fun recordCalculationCompleted(metadata: CalculationRecordMetadata) {
        activeExecutionStore.remove(metadata.scope)
        errorStore.remove(metadata.scope)
        metadataStore[metadata.scope] = metadata
    }

    override fun recordCalculationFailed(scope: CalculationScope, error: CalculationExecutionErrorMetadata) {
        activeExecutionStore.remove(scope)
        errorStore[scope] = error
        // Prior valid record is intentionally retained
    }

    override fun startExecution(scope: CalculationScope, hasPriorRecord: Boolean): Boolean {
        // Starting a new execution clears any prior error
        errorStore.remove(scope)
        val targetState = if (hasPriorRecord || metadataStore.containsKey(scope)) {
            ExecutionMarkerState.REVALIDATING
        } else {
            ExecutionMarkerState.RUNNING
        }
        val previous = activeExecutionStore.putIfAbsent(scope, targetState)
        return previous == null
    }

    override fun getExecutionState(scope: CalculationScope): ExecutionMarkerState? {
        return activeExecutionStore[scope]
    }

    override fun finishExecution(scope: CalculationScope) {
        activeExecutionStore.remove(scope)
    }

    override fun clearProject(projectId: String) {
        metadataStore.keys.filter { it.projectId == projectId }.forEach { metadataStore.remove(it) }
        activeExecutionStore.keys.filter { it.projectId == projectId }.forEach { activeExecutionStore.remove(it) }
        errorStore.keys.filter { it.projectId == projectId }.forEach { errorStore.remove(it) }
    }

    override fun clearAll() {
        metadataStore.clear()
        activeExecutionStore.clear()
        errorStore.clear()
    }
}
