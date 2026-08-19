package com.example.ai.task

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EngineeringTaskManagerTest {

    private lateinit var testClock: FakeTaskClock

    class FakeTaskClock(var time: Long = 1000L) : TaskClock {
        override fun currentTimeMillis(): Long = time
        fun advance(ms: Long) { time += ms }
    }

    @Before
    fun setUp() {
        testClock = FakeTaskClock(1000L)
    }

    private fun createManager(scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.Default): EngineeringTaskManager {
        return DefaultEngineeringTaskManager(
            coroutineScope = scope,
            clock = testClock,
            dispatcher = dispatcher
        )
    }

    @Test
    fun submit_newTask_transitionsQueuedToRunningToCompleted() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "101")
        val owner = TaskOwner.VIEW_MODEL

        val executionStarted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()

        val handle = manager.submit<String>(key, owner) { reporter ->
            reporter(TaskProgress(0.5, "Sizing cable"))
            executionStarted.complete(Unit)
            allowCompletion.await()
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-1",
                toolName = "CALCULATE_CABLE_SIZING",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "Sizing Result 120mm2",
                completedAt = testClock.currentTimeMillis()
            )
        }

        executionStarted.await()
        val runningSnap = handle.snapshot.value
        assertEquals(EngineeringTaskState.RUNNING, runningSnap.state)
        assertEquals(0.5, runningSnap.progress?.progressFraction ?: 0.0, 0.001)

        allowCompletion.complete(Unit)
        val result = handle.awaitResult()
        assertEquals(ToolStatus.PASS, result.status)
        assertEquals("Sizing Result 120mm2", result.result)

        val completedSnap = handle.snapshot.value
        assertEquals(EngineeringTaskState.COMPLETED, completedSnap.state)
        assertNull(completedSnap.error)
    }

    @Test
    fun submit_equivalentActiveTask_isDeduplicated() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_SHORT_CIRCUIT, entityId = "101")
        val owner = TaskOwner.VIEW_MODEL
        val signature = "SIG-SHA256-ABC"

        val executionCount = AtomicInteger(0)
        val executionHold = CompletableDeferred<Unit>()

        val handle1 = manager.submit<String>(key, owner, requestSignature = signature) {
            executionCount.incrementAndGet()
            executionHold.await()
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-1",
                toolName = "CALCULATE_SHORT_CIRCUIT",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "10 kA",
                completedAt = testClock.currentTimeMillis()
            )
        }

        val handle2 = manager.submit<String>(key, owner, requestSignature = signature) {
            executionCount.incrementAndGet()
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-2",
                toolName = "CALCULATE_SHORT_CIRCUIT",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "10 kA",
                completedAt = testClock.currentTimeMillis()
            )
        }

        assertEquals("Deduplicated task handle must share identical taskId", handle1.taskId, handle2.taskId)
        assertEquals(1, executionCount.get())

        executionHold.complete(Unit)
        val res1 = handle1.awaitResult()
        val res2 = handle2.awaitResult()

        assertEquals("10 kA", res1.result)
        assertEquals("10 kA", res2.result)
        assertEquals(1, executionCount.get())
    }

    @Test
    fun submit_newerTask_sameKey_supersedesOlderTask() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_TRANSFORMER, entityId = "TX-01")
        val owner = TaskOwner.VIEW_MODEL

        val task1Started = CompletableDeferred<Unit>()
        val task1Finished = CompletableDeferred<Unit>()

        val handle1 = manager.submit<String>(key, owner, requestSignature = "SIG-V1") {
            task1Started.complete(Unit)
            task1Finished.await()
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-OLD",
                toolName = "CALCULATE_TRANSFORMER",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "OLD_TX_RESULT",
                completedAt = testClock.currentTimeMillis()
            )
        }

        task1Started.await()

        // Submit task 2 for the same key with newer signature
        val handle2 = manager.submit<String>(key, owner, requestSignature = "SIG-V2") {
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-NEW",
                toolName = "CALCULATE_TRANSFORMER",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "NEW_TX_RESULT",
                completedAt = testClock.currentTimeMillis()
            )
        }

        assertNotEquals(handle1.taskId, handle2.taskId)
        val snap1 = handle1.snapshot.value
        assertEquals(EngineeringTaskState.CANCELLED, snap1.state)
        assertEquals(handle2.taskId, snap1.supersededByTaskId)
        assertEquals(TaskCancellationReason.SUPERSEDED_BY_NEWER_TASK, snap1.cancellationReason)

        task1Finished.complete(Unit)
        val res2 = handle2.awaitResult()
        assertEquals("NEW_TX_RESULT", res2.result)
        assertEquals(EngineeringTaskState.COMPLETED, handle2.snapshot.value.state)
    }

    @Test
    fun lateOldResult_cannotReplaceNewerTaskResult() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_COORDINATION, entityId = "202")
        val owner = TaskOwner.VIEW_MODEL

        val oldTaskHold = CompletableDeferred<Unit>()

        val oldHandle = manager.submit<String>(key, owner, requestSignature = "SIG-OLD") {
            oldTaskHold.await()
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-OLD",
                toolName = "CALCULATE_COORDINATION",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "OLD_LATE_RESULT",
                completedAt = testClock.currentTimeMillis()
            )
        }

        val newHandle = manager.submit<String>(key, owner, requestSignature = "SIG-NEW") {
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-NEW",
                toolName = "CALCULATE_COORDINATION",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "NEW_FRESH_RESULT",
                completedAt = testClock.currentTimeMillis()
            )
        }

        val newResult = newHandle.awaitResult()
        assertEquals("NEW_FRESH_RESULT", newResult.result)

        // Release old task late
        oldTaskHold.complete(Unit)
        delay(50)

        // The newer task result remains COMPLETED, and old task remains CANCELLED
        assertEquals(EngineeringTaskState.COMPLETED, newHandle.snapshot.value.state)
        assertEquals(EngineeringTaskState.CANCELLED, oldHandle.snapshot.value.state)
        assertEquals("NEW_FRESH_RESULT", newHandle.awaitResult().result)
        assertEquals(newHandle.taskId, oldHandle.snapshot.value.supersededByTaskId)
    }

    @Test
    fun cancel_cooperativeTask_resultsCancelled() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)
        val owner = TaskOwner.VIEW_MODEL

        val started = CompletableDeferred<Unit>()

        val handle = manager.submit<String>(key, owner) {
            started.complete(Unit)
            delay(10000)
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-BULK",
                toolName = "CALCULATE_PROJECT_BULK",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "BULK_DONE",
                completedAt = testClock.currentTimeMillis()
            )
        }

        started.await()
        handle.cancel(TaskCancellationReason.USER_REQUESTED)

        val result = handle.awaitResult()
        assertEquals(ToolStatus.CANCELLED, result.status)
        assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
        assertEquals(TaskCancellationReason.USER_REQUESTED, handle.snapshot.value.cancellationReason)
    }

    @Test
    fun cancel_nonInterruptibleWork_discardsLateResultTruthfully() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "303")
        val owner = TaskOwner.VIEW_MODEL

        val taskRunning = CompletableDeferred<Unit>()
        val completeEngine = CompletableDeferred<Unit>()

        val handle = manager.submit<String>(key, owner) {
            taskRunning.complete(Unit)
            // Simulates non-interruptible CPU execution waiting on completion
            completeEngine.await()
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "REQ-NON-INT",
                toolName = "CALCULATE_CABLE_SIZING",
                projectId = "PROJ-01",
                status = ToolStatus.PASS,
                result = "LATE_DISCARDED_SIZING",
                completedAt = testClock.currentTimeMillis()
            )
        }

        taskRunning.await()
        manager.cancel(handle.taskId, TaskCancellationReason.USER_REQUESTED)

        assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)

        completeEngine.complete(Unit)
        delay(50)

        // Late result must not flip snapshot back to COMPLETED
        assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
        val activeSnap = manager.observeActiveTask(key).value
        assertNull("Active task flow must be null after cancellation", activeSnap)
    }

    @Test
    fun cancelByOwner_doesNotCancelOtherOwners() = runBlocking {
        val manager = createManager(this)
        val key1 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")
        val key2 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "2")

        val owner1 = TaskOwner.screen("ScreenA")
        val owner2 = TaskOwner.screen("ScreenB")

        val hold1 = CompletableDeferred<Unit>()
        val hold2 = CompletableDeferred<Unit>()

        val handle1 = manager.submit<String>(key1, owner1) {
            hold1.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
        }

        val handle2 = manager.submit<String>(key2, owner2) {
            hold2.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
        }

        delay(50)
        manager.cancelByOwner(owner1)

        assertEquals(EngineeringTaskState.CANCELLED, handle1.snapshot.value.state)
        assertEquals(EngineeringTaskState.RUNNING, handle2.snapshot.value.state)

        hold2.complete(Unit)
        val res2 = handle2.awaitResult()
        assertEquals("R2", res2.result)
        assertEquals(EngineeringTaskState.COMPLETED, handle2.snapshot.value.state)
    }

    @Test
    fun sameEntityIdAcrossProjects_tasksRemainIsolated() = runBlocking {
        val manager = createManager(this)
        val keyProjA = EngineeringTaskKey(projectId = "PROJ-A", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "100")
        val keyProjB = EngineeringTaskKey(projectId = "PROJ-B", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "100")

        val handleA = manager.submit<String>(keyProjA, TaskOwner.VIEW_MODEL) {
            ToolResultEnvelope(contractVersion = 1, requestId = "A", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-A", status = ToolStatus.PASS, result = "RESULT-A", completedAt = testClock.currentTimeMillis())
        }

        val handleB = manager.submit<String>(keyProjB, TaskOwner.VIEW_MODEL) {
            ToolResultEnvelope(contractVersion = 1, requestId = "B", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-B", status = ToolStatus.PASS, result = "RESULT-B", completedAt = testClock.currentTimeMillis())
        }

        assertNotEquals(handleA.taskId, handleB.taskId)
        assertEquals("RESULT-A", handleA.awaitResult().result)
        assertEquals("RESULT-B", handleB.awaitResult().result)
    }

    @Test
    fun sameTaskKeyDifferentRequestSignature_isNotIncorrectlyDeduplicated() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "101")

        val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "SIG-1") {
            ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
        }
        handle1.awaitResult()

        val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "SIG-2") {
            ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
        }

        assertNotEquals("Different signature must produce separate task handle", handle1.taskId, handle2.taskId)
        assertEquals("R2", handle2.awaitResult().result)
    }

    @Test
    fun projectSwitch_cancelsOrStalesOnlyOwnedTasks() = runBlocking {
        val manager = createManager(this)
        val keyA = EngineeringTaskKey(projectId = "PROJ-SWITCH-A", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)
        val keyB = EngineeringTaskKey(projectId = "PROJ-SWITCH-B", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)

        val holdA = CompletableDeferred<Unit>()
        val holdB = CompletableDeferred<Unit>()

        val handleA = manager.submit<String>(keyA, TaskOwner.VIEW_MODEL) {
            holdA.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "A", toolName = "CALCULATE_PROJECT_BULK", projectId = "PROJ-SWITCH-A", status = ToolStatus.PASS, result = "A", completedAt = testClock.currentTimeMillis())
        }

        val handleB = manager.submit<String>(keyB, TaskOwner.VIEW_MODEL) {
            holdB.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "B", toolName = "CALCULATE_PROJECT_BULK", projectId = "PROJ-SWITCH-B", status = ToolStatus.PASS, result = "B", completedAt = testClock.currentTimeMillis())
        }

        delay(50)
        manager.cancelByProject("PROJ-SWITCH-A", TaskCancellationReason.PROJECT_SWITCHED)

        assertEquals(EngineeringTaskState.CANCELLED, handleA.snapshot.value.state)
        assertEquals(TaskCancellationReason.PROJECT_SWITCHED, handleA.snapshot.value.cancellationReason)

        assertEquals(EngineeringTaskState.RUNNING, handleB.snapshot.value.state)
        holdB.complete(Unit)
        assertEquals("B", handleB.awaitResult().result)
    }

    @Test
    fun failure_isCapturedAsStructuredError() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.VALIDATE_PROJECT)

        val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
            ToolResultEnvelope(
                contractVersion = 1,
                requestId = "FAIL-REQ",
                toolName = "VALIDATE_PROJECT",
                projectId = "PROJ-01",
                status = ToolStatus.ERROR,
                errors = listOf(
                    StructuredError(
                        errorCode = "VALIDATION_ENGINE_CRASH",
                        message = "Network topology graph contained disconnected island.",
                        isRecoverable = false
                    )
                ),
                completedAt = testClock.currentTimeMillis()
            )
        }

        val result = handle.awaitResult()
        assertEquals(ToolStatus.ERROR, result.status)

        val snap = handle.snapshot.value
        assertEquals(EngineeringTaskState.FAILED, snap.state)
        assertNotNull(snap.error)
        assertEquals("VALIDATION_ENGINE_CRASH", snap.error?.errorCode)
        assertEquals("Network topology graph contained disconnected island.", snap.error?.message)
    }

    @Test
    fun failedTask_doesNotBreakManagerScope() = runBlocking {
        val manager = createManager(this)
        val keyFail = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")
        val keySuccess = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "2")

        val handleFail = manager.submit<String>(keyFail, TaskOwner.VIEW_MODEL) {
            throw IllegalStateException("Fatal simulation calculation exception")
        }

        handleFail.awaitResult()
        assertEquals(EngineeringTaskState.FAILED, handleFail.snapshot.value.state)

        // Manager must continue operating cleanly for next task
        val handleSuccess = manager.submit<String>(keySuccess, TaskOwner.VIEW_MODEL) {
            ToolResultEnvelope(contractVersion = 1, requestId = "S", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OK", completedAt = testClock.currentTimeMillis())
        }

        val res = handleSuccess.awaitResult()
        assertEquals("OK", res.result)
        assertEquals(EngineeringTaskState.COMPLETED, handleSuccess.snapshot.value.state)
    }

    @Test
    fun progress_isNullWhenNotMeasurable() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_TRANSFORMER)

        val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
            ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_TRANSFORMER", projectId = "PROJ-01", status = ToolStatus.PASS, result = "TX_OK", completedAt = testClock.currentTimeMillis())
        }

        handle.awaitResult()
        assertNull("Progress must be null when not explicitly reported", handle.snapshot.value.progress)
    }

    @Test
    fun progress_neverLeavesZeroToOneRange() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskProgress(progressFraction = -0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskProgress(progressFraction = 1.05)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskProgress(progressFraction = 0.5, currentStep = 5, totalSteps = 4)
        }

        val validProg = TaskProgress(progressFraction = 0.75, stage = "Checking ADS", currentStep = 3, totalSteps = 4)
        assertEquals(0.75, validProg.progressFraction, 0.001)
    }

    @Test
    fun repeatedCompletion_isIdempotent() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "55")

        val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
            ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "CABLE-55", completedAt = testClock.currentTimeMillis())
        }

        val res1 = handle.awaitResult()
        val res2 = handle.awaitResult()
        assertEquals(res1, res2)
        assertEquals(EngineeringTaskState.COMPLETED, handle.snapshot.value.state)
    }

    @Test
    fun observer_receivesMonotonicLegalStateTransitions() = runBlocking {
        val manager = createManager(this)
        val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)

        val observedStates = mutableListOf<EngineeringTaskState>()
        val hold = CompletableDeferred<Unit>()

        val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
            hold.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_PROJECT_BULK", projectId = "PROJ-01", status = ToolStatus.PASS, result = "DONE", completedAt = testClock.currentTimeMillis())
        }

        val job = launch {
            handle.snapshot.collect { snap ->
                synchronized(observedStates) {
                    if (observedStates.isEmpty() || observedStates.last() != snap.state) {
                        observedStates.add(snap.state)
                    }
                }
            }
        }

        delay(50)
        hold.complete(Unit)
        handle.awaitResult()
        delay(50)
        job.cancel()

        assertTrue(observedStates.contains(EngineeringTaskState.RUNNING))
        assertTrue(observedStates.contains(EngineeringTaskState.COMPLETED))
        assertEquals(EngineeringTaskState.COMPLETED, observedStates.last())
    }

    @Test
    fun viewModelOrOwnerDisposal_cleansOwnedTasks() = runBlocking {
        val manager = createManager(this)
        val key1 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "10")
        val key2 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "20")

        val vmOwner = TaskOwner("VM-MAIN", "VIEW_MODEL")

        val hold = CompletableDeferred<Unit>()

        val handle1 = manager.submit<String>(key1, vmOwner) {
            hold.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "10", completedAt = testClock.currentTimeMillis())
        }

        val handle2 = manager.submit<String>(key2, vmOwner) {
            hold.await()
            ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "20", completedAt = testClock.currentTimeMillis())
        }

        delay(50)
        // ViewModel onCleared()
        manager.cancelByOwner(vmOwner, TaskCancellationReason.OWNER_DISPOSED)

        assertEquals(EngineeringTaskState.CANCELLED, handle1.snapshot.value.state)
        assertEquals(EngineeringTaskState.CANCELLED, handle2.snapshot.value.state)
        assertEquals(TaskCancellationReason.OWNER_DISPOSED, handle1.snapshot.value.cancellationReason)
    }

    @Test
    fun manager_doesNotTriggerProjectMutation() {
        val methods = EngineeringTaskManager::class.java.declaredMethods
        val mutatingMethods = methods.filter { m ->
            m.name.startsWith("insert") || m.name.startsWith("update") || m.name.startsWith("delete") ||
                    m.name.startsWith("save") || m.name.startsWith("mutate")
        }
        assertTrue("EngineeringTaskManager must not expose mutation methods: $mutatingMethods", mutatingMethods.isEmpty())
    }

    @Test
    fun manager_hasNoRoomPersistence() {
        val taskClasses = listOf(
            EngineeringTaskKey::class.java,
            EngineeringTaskSnapshot::class.java,
            TaskOwner::class.java,
            TaskProgress::class.java,
            TaskError::class.java
        )

        for (cls in taskClasses) {
            val annotations = cls.annotations.map { it.annotationClass.java.simpleName }
            assertFalse("${cls.simpleName} must not be a Room @Entity", annotations.contains("Entity"))
            assertFalse("${cls.simpleName} must not be a Room @Dao", annotations.contains("Dao"))
            assertFalse("${cls.simpleName} must not be a Room @Database", annotations.contains("Database"))
        }
    }

    @Test
    fun manager_hasNoComposeFirebaseOrLlmDependency() {
        val taskFile = java.io.File("src/main/java/com/example/ai/task/EngineeringTaskContracts.kt")
        if (taskFile.exists()) {
            val content = taskFile.readText()
            assertFalse(content.contains("androidx.compose"))
            assertFalse(content.contains("com.google.firebase"))
            assertFalse(content.contains("com.google.ai"))
            assertFalse(content.contains("Gemini"))
        }
    }
}
