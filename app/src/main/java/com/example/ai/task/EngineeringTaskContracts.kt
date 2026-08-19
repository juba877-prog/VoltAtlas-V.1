package com.example.ai.task

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolResultEnvelope
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.StateFlow

/**
 * Strongly-typed domain/action types for asynchronous engineering tasks.
 */
enum class EngineeringTaskType {
    CALCULATE_CABLE_SIZING,
    CALCULATE_TRANSFORMER,
    CALCULATE_SHORT_CIRCUIT,
    CALCULATE_PROTECTION_ADS,
    CALCULATE_COORDINATION,
    CALCULATE_PROJECT_BULK,
    VALIDATE_PROJECT,
    EXPORT_REPORT,
    CUSTOM
}

/**
 * Strongly-typed, project-scoped identity key for engineering tasks.
 * Prevents cross-project collisions and supports entity-scoped granularity.
 */
@JsonClass(generateAdapter = true)
data class EngineeringTaskKey(
    val projectId: String,
    val taskType: EngineeringTaskType,
    val entityType: String? = null,
    val entityId: String? = null,
    val requestVariant: String? = null
) {
    init {
        require(projectId.isNotBlank()) { "projectId cannot be blank in EngineeringTaskKey" }
    }
}

/**
 * Authoritative lifecycle states for asynchronous engineering tasks.
 */
enum class EngineeringTaskState {
    QUEUED,
    RUNNING,
    WAITING_FOR_INPUT,
    WAITING_FOR_APPROVAL,
    APPLYING,
    REVALIDATING,
    COMPLETED,
    FAILED,
    CANCELLED,
    STALE;

    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, FAILED, CANCELLED, STALE)

    val isActive: Boolean
        get() = this in setOf(QUEUED, RUNNING, REVALIDATING, WAITING_FOR_INPUT, WAITING_FOR_APPROVAL, APPLYING)
}

/**
 * Structured reason for task cancellation or supersession.
 */
enum class TaskCancellationReason {
    USER_REQUESTED,
    SUPERSEDED_BY_NEWER_TASK,
    OWNER_DISPOSED,
    PROJECT_SWITCHED,
    PROJECT_DELETED,
    TIMEOUT,
    SHUTDOWN
}

/**
 * Task owner identity, allowing scoped cancellation when a screen or ViewModel is disposed.
 */
@JsonClass(generateAdapter = true)
data class TaskOwner(
    val ownerId: String,
    val ownerType: String = "VIEW_MODEL"
) {
    companion object {
        val SYSTEM = TaskOwner("SYSTEM", "SYSTEM")
        val VIEW_MODEL = TaskOwner("VIEW_MODEL", "VIEW_MODEL")
        val ASSISTANT = TaskOwner("ASSISTANT", "ASSISTANT")
        fun screen(screenName: String) = TaskOwner(screenName, "SCREEN")
        fun entity(entityType: String, entityId: String) = TaskOwner("$entityType:$entityId", "ENTITY")
    }
}

/**
 * Measurable task progress, strictly bounded between 0.0 and 1.0.
 */
@JsonClass(generateAdapter = true)
data class TaskProgress(
    val progressFraction: Double,
    val stage: String? = null,
    val currentStep: Int? = null,
    val totalSteps: Int? = null
) {
    init {
        require(progressFraction in 0.0..1.0) {
            "progressFraction must be within 0.0..1.0, got: $progressFraction"
        }
        if (currentStep != null && totalSteps != null) {
            require(totalSteps >= 0) { "totalSteps cannot be negative" }
            require(currentStep >= 0) { "currentStep cannot be negative" }
            require(currentStep <= totalSteps) { "currentStep ($currentStep) cannot exceed totalSteps ($totalSteps)" }
        }
    }
}

/**
 * Machine-actionable task error container for failed asynchronous tasks.
 */
@JsonClass(generateAdapter = true)
data class TaskError(
    val errorCode: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val causeType: String? = null,
    val structuredError: StructuredError? = null
)

/**
 * Immutable snapshot of an engineering task's current status and metadata.
 */
@JsonClass(generateAdapter = true)
data class EngineeringTaskSnapshot(
    val taskId: String,
    val taskKey: EngineeringTaskKey,
    val state: EngineeringTaskState,
    val progress: TaskProgress? = null,
    val startedAt: Long,
    val updatedAt: Long,
    val owner: TaskOwner,
    val requestSignature: String? = null,
    val supersededByTaskId: String? = null,
    val cancellationReason: TaskCancellationReason? = null,
    val error: TaskError? = null
)

/**
 * Handle returned upon task submission to observe progress, await result, or request cancellation.
 */
interface EngineeringTaskHandle<T> {
    val taskId: String
    val taskKey: EngineeringTaskKey
    val snapshot: StateFlow<EngineeringTaskSnapshot>
    suspend fun awaitResult(): ToolResultEnvelope<T>
    suspend fun cancel(reason: TaskCancellationReason = TaskCancellationReason.USER_REQUESTED)
}

/**
 * Injectable clock abstraction for deterministic testing of timestamps.
 */
interface TaskClock {
    fun currentTimeMillis(): Long

    object SystemClock : TaskClock {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }
}
