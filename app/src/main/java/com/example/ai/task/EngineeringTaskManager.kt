package com.example.ai.task

import com.example.ai.contracts.ToolResultEnvelope
import kotlinx.coroutines.flow.StateFlow

/**
 * Controlled asynchronous task manager responsible for task identity, deduplication,
 * supersession (latest-task-wins), cooperative cancellation, progress observation,
 * owner-based scoping, and structured error containment.
 */
interface EngineeringTaskManager {

    /**
     * Observes the snapshot of a specific task by its unique [taskId].
     * Returns null if the task ID is unknown.
     */
    fun observeTask(taskId: String): StateFlow<EngineeringTaskSnapshot?>

    /**
     * Observes the active snapshot for a specific typed [EngineeringTaskKey].
     * Emits null when no task is currently active for this key.
     */
    fun observeActiveTask(key: EngineeringTaskKey): StateFlow<EngineeringTaskSnapshot?>

    /**
     * Observes all task snapshots belonging to a specific [projectId].
     */
    fun observeProjectTasks(projectId: String): StateFlow<List<EngineeringTaskSnapshot>>

    /**
     * Submits an asynchronous engineering task with deduplication and latest-task-wins semantics.
     *
     * Semantics:
     * 1. Deduplication: If an active task with identical [key] AND identical non-blank [requestSignature]
     *    is already running, adds [owner] to subscribers and returns the shared active handle without launching new work.
     * 2. Supersession: If an active task with identical [key] but different (or null/blank) [requestSignature]
     *    exists, supersedes and cancels the older task, and starts the new task.
     * 3. Late Result Rejection: If an older superseded or cancelled task finishes execution,
     *    its result is discarded and will never overwrite active or newer task state.
     *
     * @param key Typed, project-scoped task key.
     * @param owner Lifecycle owner token (e.g. Screen, ViewModel).
     * @param requestSignature Authoritative hash/fingerprint of inputs for deduplication.
     * @param executionBlock The suspendable execution block receiving a progress reporter callback.
     */
    suspend fun <T> submit(
        key: EngineeringTaskKey,
        owner: TaskOwner,
        requestSignature: String? = null,
        executionBlock: suspend (progressReporter: (TaskProgress) -> Unit) -> ToolResultEnvelope<T>
    ): EngineeringTaskHandle<T>

    /**
     * Administratively cancels a specific task by [taskId] for all subscribers.
     */
    suspend fun cancel(
        taskId: String,
        reason: TaskCancellationReason = TaskCancellationReason.USER_REQUESTED
    )

    /**
     * Cancels / detaches a specific [owner]'s subscription from a specific task by [taskId].
     * If the specified owner is the last remaining subscriber, cancels the underlying task.
     */
    suspend fun cancelForOwner(
        taskId: String,
        owner: TaskOwner,
        reason: TaskCancellationReason = TaskCancellationReason.USER_REQUESTED
    )

    /**
     * Cancels all active tasks belonging to a specific [owner].
     * If other owners share subscription to any of the tasks, only detaches this owner.
     */
    suspend fun cancelByOwner(
        owner: TaskOwner,
        reason: TaskCancellationReason = TaskCancellationReason.OWNER_DISPOSED
    )

    /**
     * Cancels all active tasks belonging to a specific [projectId].
     */
    suspend fun cancelByProject(
        projectId: String,
        reason: TaskCancellationReason = TaskCancellationReason.PROJECT_SWITCHED
    )

    /**
     * Clears terminal task history. If [projectId] is provided, clears only for that project.
     */
    suspend fun clearCompleted(projectId: String? = null)

    /**
     * Clears all task history and active state for a specific [projectId].
     */
    suspend fun clearProjectTasks(projectId: String)

    /**
     * Closes the task manager, cancelling all active tasks and clearing observers.
     */
    suspend fun close()
}
