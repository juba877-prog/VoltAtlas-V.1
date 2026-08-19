package com.example.ai.task

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production implementation of [EngineeringTaskManager].
 *
 * Enforces:
 * 1. Strict task identity and project isolation.
 * 2. Active task deduplication based on key + request signature.
 * 3. Latest-task-wins supersession for identical task keys.
 * 4. Late-result rejection for superseded or cancelled tasks.
 * 5. Cooperative cancellation without corrupted state.
 * 6. Structured error containment and non-blocking asynchronous execution.
 */
class DefaultEngineeringTaskManager(
    private val coroutineScope: CoroutineScope,
    private val clock: TaskClock = TaskClock.SystemClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : EngineeringTaskManager {

    private val mutex = Mutex()

    private val tasks = ConcurrentHashMap<String, TaskRecord<*>>()
    private val activeTasksByKey = ConcurrentHashMap<EngineeringTaskKey, String>()

    private val taskFlows = ConcurrentHashMap<String, MutableStateFlow<EngineeringTaskSnapshot?>>()
    private val activeTaskFlows = ConcurrentHashMap<EngineeringTaskKey, MutableStateFlow<EngineeringTaskSnapshot?>>()
    private val projectTaskFlows = ConcurrentHashMap<String, MutableStateFlow<List<EngineeringTaskSnapshot>>>()

    private class TaskRecord<T>(
        val taskId: String,
        val taskKey: EngineeringTaskKey,
        val owner: TaskOwner,
        val requestSignature: String?,
        val flow: MutableStateFlow<EngineeringTaskSnapshot>,
        val deferred: CompletableDeferred<ToolResultEnvelope<T>>,
        var job: Job? = null,
        @Volatile var isSuperseded: Boolean = false,
        @Volatile var supersededByTaskId: String? = null,
        @Volatile var explicitCancellationReason: TaskCancellationReason? = null
    ) {
        val snapshot: EngineeringTaskSnapshot
            get() = flow.value
    }

    override fun observeTask(taskId: String): StateFlow<EngineeringTaskSnapshot?> {
        return taskFlows.computeIfAbsent(taskId) {
            val record = tasks[taskId]
            MutableStateFlow(record?.snapshot)
        }.asStateFlow()
    }

    override fun observeActiveTask(key: EngineeringTaskKey): StateFlow<EngineeringTaskSnapshot?> {
        return activeTaskFlows.computeIfAbsent(key) {
            val activeId = activeTasksByKey[key]
            val activeRecord = activeId?.let { tasks[it] }
            MutableStateFlow(activeRecord?.snapshot?.takeIf { it.state.isActive })
        }.asStateFlow()
    }

    override fun observeProjectTasks(projectId: String): StateFlow<List<EngineeringTaskSnapshot>> {
        return projectTaskFlows.computeIfAbsent(projectId) {
            MutableStateFlow(getProjectSnapshots(projectId))
        }.asStateFlow()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> submit(
        key: EngineeringTaskKey,
        owner: TaskOwner,
        requestSignature: String?,
        executionBlock: suspend (progressReporter: (TaskProgress) -> Unit) -> ToolResultEnvelope<T>
    ): EngineeringTaskHandle<T> = mutex.withLock {
        val newTaskId = idGenerator()

        // 1. Check for existing active task for the same key
        val existingTaskId = activeTasksByKey[key]
        if (existingTaskId != null) {
            val existingRecord = tasks[existingTaskId] as? TaskRecord<T>
            if (existingRecord != null && existingRecord.snapshot.state.isActive) {
                // Deduplication: exact same key AND exact same non-null request signature
                if (requestSignature != null && existingRecord.requestSignature == requestSignature) {
                    return createHandle(existingRecord)
                }

                // Latest-task-wins: supersede and cancel older task
                supersedeRecordInternal(existingRecord, newTaskId)
            }
        }

        // 2. Instantiate new task record
        val now = clock.currentTimeMillis()
        val initialSnapshot = EngineeringTaskSnapshot(
            taskId = newTaskId,
            taskKey = key,
            state = EngineeringTaskState.QUEUED,
            progress = null,
            startedAt = now,
            updatedAt = now,
            owner = owner,
            requestSignature = requestSignature,
            supersededByTaskId = null,
            cancellationReason = null,
            error = null
        )

        val flow = MutableStateFlow(initialSnapshot)
        val deferred = CompletableDeferred<ToolResultEnvelope<T>>()
        val record = TaskRecord(
            taskId = newTaskId,
            taskKey = key,
            owner = owner,
            requestSignature = requestSignature,
            flow = flow,
            deferred = deferred
        )

        tasks[newTaskId] = record
        activeTasksByKey[key] = newTaskId

        publishTaskUpdate(record, initialSnapshot)

        // 3. Launch execution in coroutine scope
        val job = coroutineScope.launch(dispatcher) {
            try {
                // Transition to RUNNING
                mutex.withLock {
                    if (record.isSuperseded || record.snapshot.state.isTerminal) {
                        return@launch
                    }
                    val runningSnapshot = record.snapshot.copy(
                        state = EngineeringTaskState.RUNNING,
                        updatedAt = clock.currentTimeMillis()
                    )
                    publishTaskUpdate(record, runningSnapshot)
                }

                ensureActive()

                // Progress callback
                val progressReporter: (TaskProgress) -> Unit = { progress ->
                    if (!record.snapshot.state.isTerminal && !record.isSuperseded) {
                        val progSnapshot = record.snapshot.copy(
                            progress = progress,
                            updatedAt = clock.currentTimeMillis()
                        )
                        publishTaskUpdate(record, progSnapshot)
                    }
                }

                // Execute user block
                val resultEnvelope = executionBlock(progressReporter)

                ensureActive()

                // Complete under lock
                mutex.withLock {
                    handleExecutionSuccess(record, resultEnvelope)
                }
            } catch (ce: CancellationException) {
                mutex.withLock {
                    handleExecutionCancellation(record, ce)
                }
            } catch (t: Throwable) {
                mutex.withLock {
                    handleExecutionFailure(record, t)
                }
            }
        }

        record.job = job
        return createHandle(record)
    }

    override suspend fun cancel(taskId: String, reason: TaskCancellationReason): Unit = mutex.withLock {
        val record = tasks[taskId] ?: return
        if (record.snapshot.state.isTerminal) return

        record.explicitCancellationReason = reason
        record.job?.cancel(CancellationException("Task cancelled: $reason"))

        val now = clock.currentTimeMillis()
        val cancelledSnapshot = record.snapshot.copy(
            state = EngineeringTaskState.CANCELLED,
            cancellationReason = reason,
            updatedAt = now
        )
        if (activeTasksByKey[record.taskKey] == record.taskId) {
            activeTasksByKey.remove(record.taskKey)
        }
        publishTaskUpdate(record, cancelledSnapshot)

        @Suppress("UNCHECKED_CAST")
        val typedRecord = record as TaskRecord<Any?>
        if (!typedRecord.deferred.isCompleted) {
            typedRecord.deferred.complete(createCancelledEnvelope(record, reason))
        }
    }

    override suspend fun cancelByOwner(owner: TaskOwner, reason: TaskCancellationReason): Unit = mutex.withLock {
        tasks.values.filter { it.owner == owner && it.snapshot.state.isActive }.forEach { record ->
            cancelRecordInternal(record, reason)
        }
    }

    override suspend fun cancelByProject(projectId: String, reason: TaskCancellationReason): Unit = mutex.withLock {
        tasks.values.filter { it.taskKey.projectId == projectId && it.snapshot.state.isActive }.forEach { record ->
            cancelRecordInternal(record, reason)
        }
    }

    override suspend fun clearCompleted(projectId: String?): Unit = mutex.withLock {
        val toRemove = tasks.values.filter { record ->
            record.snapshot.state.isTerminal && (projectId == null || record.taskKey.projectId == projectId)
        }.map { it.taskId }

        toRemove.forEach { id ->
            tasks.remove(id)
            taskFlows.remove(id)
        }

        if (projectId != null) {
            notifyProjectTasksChanged(projectId)
        } else {
            projectTaskFlows.keys.forEach { notifyProjectTasksChanged(it) }
        }
    }

    // -------------------------------------------------------------------------
    // Private Internal Helpers (Must be invoked while holding [mutex] when mutating)
    // -------------------------------------------------------------------------

    private fun supersedeRecordInternal(record: TaskRecord<*>, supersedingTaskId: String) {
        record.isSuperseded = true
        record.supersededByTaskId = supersedingTaskId
        record.explicitCancellationReason = TaskCancellationReason.SUPERSEDED_BY_NEWER_TASK
        record.job?.cancel(CancellationException("Superseded by $supersedingTaskId"))

        val now = clock.currentTimeMillis()
        val supersededSnapshot = record.snapshot.copy(
            state = EngineeringTaskState.CANCELLED,
            supersededByTaskId = supersedingTaskId,
            cancellationReason = TaskCancellationReason.SUPERSEDED_BY_NEWER_TASK,
            updatedAt = now
        )
        publishTaskUpdate(record, supersededSnapshot)

        @Suppress("UNCHECKED_CAST")
        val typed = record as TaskRecord<Any?>
        if (!typed.deferred.isCompleted) {
            typed.deferred.complete(createCancelledEnvelope(record, TaskCancellationReason.SUPERSEDED_BY_NEWER_TASK))
        }
    }

    private fun cancelRecordInternal(record: TaskRecord<*>, reason: TaskCancellationReason) {
        if (record.snapshot.state.isTerminal) return

        record.explicitCancellationReason = reason
        record.job?.cancel(CancellationException("Task cancelled: $reason"))

        val now = clock.currentTimeMillis()
        val cancelledSnapshot = record.snapshot.copy(
            state = EngineeringTaskState.CANCELLED,
            cancellationReason = reason,
            updatedAt = now
        )
        if (activeTasksByKey[record.taskKey] == record.taskId) {
            activeTasksByKey.remove(record.taskKey)
        }
        publishTaskUpdate(record, cancelledSnapshot)

        @Suppress("UNCHECKED_CAST")
        val typed = record as TaskRecord<Any?>
        if (!typed.deferred.isCompleted) {
            typed.deferred.complete(createCancelledEnvelope(record, reason))
        }
    }

    private fun <T> handleExecutionSuccess(record: TaskRecord<T>, envelope: ToolResultEnvelope<T>) {
        // Late result rejection: if superseded or already cancelled, discard output
        if (record.isSuperseded || record.snapshot.state.isTerminal) {
            if (!record.deferred.isCompleted) {
                record.deferred.complete(envelope)
            }
            return
        }

        val targetState = when (envelope.status) {
            ToolStatus.PASS, ToolStatus.WARNING, ToolStatus.FAIL -> EngineeringTaskState.COMPLETED
            ToolStatus.ERROR, ToolStatus.INVALID_INPUT, ToolStatus.INCOMPLETE,
            ToolStatus.NOT_SUPPORTED -> EngineeringTaskState.FAILED
            ToolStatus.CANCELLED -> EngineeringTaskState.CANCELLED
            ToolStatus.STALE -> EngineeringTaskState.STALE
            ToolStatus.RUNNING -> EngineeringTaskState.RUNNING
            ToolStatus.REVALIDATING -> EngineeringTaskState.REVALIDATING
        }

        val taskError = if (targetState == EngineeringTaskState.FAILED) {
            val firstErr = envelope.errors.firstOrNull()
            TaskError(
                errorCode = firstErr?.errorCode ?: "TASK_FAILED",
                message = firstErr?.message ?: "Task execution finished with error status ${envelope.status}",
                details = emptyMap(),
                structuredError = firstErr
            )
        } else null

        val now = clock.currentTimeMillis()
        val finalSnapshot = record.snapshot.copy(
            state = targetState,
            updatedAt = now,
            error = taskError
        )

        if (activeTasksByKey[record.taskKey] == record.taskId) {
            activeTasksByKey.remove(record.taskKey)
        }
        publishTaskUpdate(record, finalSnapshot)

        if (!record.deferred.isCompleted) {
            record.deferred.complete(envelope)
        }
    }

    private fun <T> handleExecutionCancellation(record: TaskRecord<T>, ce: CancellationException) {
        val reason = record.explicitCancellationReason ?: TaskCancellationReason.USER_REQUESTED
        val now = clock.currentTimeMillis()
        val finalSnapshot = record.snapshot.copy(
            state = EngineeringTaskState.CANCELLED,
            cancellationReason = reason,
            updatedAt = now
        )

        if (activeTasksByKey[record.taskKey] == record.taskId) {
            activeTasksByKey.remove(record.taskKey)
        }
        publishTaskUpdate(record, finalSnapshot)

        if (!record.deferred.isCompleted) {
            record.deferred.complete(createCancelledEnvelope(record, reason))
        }
    }

    private fun <T> handleExecutionFailure(record: TaskRecord<T>, t: Throwable) {
        if (record.isSuperseded || record.snapshot.state.isTerminal) return

        val now = clock.currentTimeMillis()
        val error = TaskError(
            errorCode = "UNHANDLED_EXCEPTION",
            message = t.message ?: "Task threw unexpected exception",
            causeType = t.javaClass.name,
            structuredError = StructuredError(
                errorCode = "UNHANDLED_EXCEPTION",
                message = t.message ?: "Task threw unexpected exception",
                isRecoverable = false
            )
        )

        val finalSnapshot = record.snapshot.copy(
            state = EngineeringTaskState.FAILED,
            error = error,
            updatedAt = now
        )

        if (activeTasksByKey[record.taskKey] == record.taskId) {
            activeTasksByKey.remove(record.taskKey)
        }
        publishTaskUpdate(record, finalSnapshot)

        if (!record.deferred.isCompleted) {
            record.deferred.complete(createErrorEnvelope(record, error))
        }
    }

    private fun publishTaskUpdate(record: TaskRecord<*>, snapshot: EngineeringTaskSnapshot) {
        record.flow.value = snapshot
        taskFlows.computeIfAbsent(record.taskId) { MutableStateFlow(snapshot) }.value = snapshot

        val activeFlow = activeTaskFlows[record.taskKey]
        if (activeFlow != null) {
            if (activeTasksByKey[record.taskKey] == record.taskId && snapshot.state.isActive) {
                activeFlow.value = snapshot
            } else if (activeTasksByKey[record.taskKey] == null || activeTasksByKey[record.taskKey] != record.taskId) {
                val currentActiveId = activeTasksByKey[record.taskKey]
                val currentActiveRecord = currentActiveId?.let { tasks[it] }
                activeFlow.value = currentActiveRecord?.snapshot?.takeIf { it.state.isActive }
            }
        }

        notifyProjectTasksChanged(record.taskKey.projectId)
    }

    private fun notifyProjectTasksChanged(projectId: String) {
        val flow = projectTaskFlows[projectId]
        if (flow != null) {
            flow.value = getProjectSnapshots(projectId)
        }
    }

    private fun getProjectSnapshots(projectId: String): List<EngineeringTaskSnapshot> {
        return tasks.values.filter { it.taskKey.projectId == projectId }.map { it.snapshot }
    }

    private fun <T> createHandle(record: TaskRecord<T>): EngineeringTaskHandle<T> {
        return object : EngineeringTaskHandle<T> {
            override val taskId: String = record.taskId
            override val taskKey: EngineeringTaskKey = record.taskKey
            override val snapshot: StateFlow<EngineeringTaskSnapshot> = record.flow.asStateFlow()

            override suspend fun awaitResult(): ToolResultEnvelope<T> {
                return record.deferred.await()
            }

            override suspend fun cancel(reason: TaskCancellationReason) {
                this@DefaultEngineeringTaskManager.cancel(record.taskId, reason)
            }
        }
    }

    private fun <T> createCancelledEnvelope(
        record: TaskRecord<*>,
        reason: TaskCancellationReason
    ): ToolResultEnvelope<T> {
        val now = clock.currentTimeMillis()
        return ToolResultEnvelope(
            contractVersion = 1,
            requestId = record.taskId,
            toolName = record.taskKey.taskType.name,
            projectId = record.taskKey.projectId,
            status = ToolStatus.CANCELLED,
            result = null,
            errors = emptyList(),
            startedAt = record.snapshot.startedAt,
            completedAt = now
        )
    }

    private fun <T> createErrorEnvelope(
        record: TaskRecord<*>,
        error: TaskError
    ): ToolResultEnvelope<T> {
        val now = clock.currentTimeMillis()
        val structured = error.structuredError ?: StructuredError(
            errorCode = error.errorCode,
            message = error.message,
            isRecoverable = false
        )
        return ToolResultEnvelope(
            contractVersion = 1,
            requestId = record.taskId,
            toolName = record.taskKey.taskType.name,
            projectId = record.taskKey.projectId,
            status = ToolStatus.ERROR,
            result = null,
            errors = listOf(structured),
            startedAt = record.snapshot.startedAt,
            completedAt = now
        )
    }
}
