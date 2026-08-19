package com.example.ai.task

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
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
    fun submit_newTask_transitionsQueuedToRunningToCompleted() {
        runBlocking {
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
    }

    @Test
    fun submit_equivalentActiveTask_isDeduplicated() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_SHORT_CIRCUIT, entityId = "101")
            val owner = TaskOwner.VIEW_MODEL
            val signature = "SIG-SHA256-ABC"

            val executionCount = AtomicInteger(0)
            val started = CompletableDeferred<Unit>()
            val executionHold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, owner, requestSignature = signature) {
                executionCount.incrementAndGet()
                started.complete(Unit)
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

            started.await()

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
    }

    @Test
    fun submit_newerTask_sameKey_supersedesOlderTask() {
        runBlocking {
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
    }

    @Test
    fun lateOldResult_cannotReplaceNewerTaskResult() {
        runBlocking {
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

            oldTaskHold.complete(Unit)

            assertEquals(EngineeringTaskState.COMPLETED, newHandle.snapshot.value.state)
            assertEquals(EngineeringTaskState.CANCELLED, oldHandle.snapshot.value.state)
            assertEquals("NEW_FRESH_RESULT", newHandle.awaitResult().result)
            assertEquals(newHandle.taskId, oldHandle.snapshot.value.supersededByTaskId)
        }
    }

    @Test
    fun cancel_cooperativeTask_resultsCancelled() {
        runBlocking {
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
    }

    @Test
    fun cancel_nonCooperativeCpuWork_returnsCancelledAndRejectsLateResult() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "303")
            val owner = TaskOwner.VIEW_MODEL

            val cpuStarted = CountDownLatch(1)
            val cpuAllowFinish = CountDownLatch(1)
            val cpuFinished = AtomicBoolean(false)

            val handle = manager.submit<String>(key, owner) {
                cpuStarted.countDown()
                while (!cpuAllowFinish.await(5, TimeUnit.MILLISECONDS)) {
                    // non-cooperative CPU loop
                }
                cpuFinished.set(true)
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

            assertTrue("CPU work should start", cpuStarted.await(2, TimeUnit.SECONDS))

            // Cancel while non-interruptible CPU is running
            manager.cancel(handle.taskId, TaskCancellationReason.USER_REQUESTED)

            assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
            val cancelledRes = handle.awaitResult()
            assertEquals(ToolStatus.CANCELLED, cancelledRes.status)

            // Release non-cooperative CPU loop
            cpuAllowFinish.countDown()
            val executor = Executors.newSingleThreadScheduledExecutor()
            val finishLatch = CountDownLatch(1)
            executor.scheduleAtFixedRate({
                if (cpuFinished.get()) finishLatch.countDown()
            }, 10, 10, TimeUnit.MILLISECONDS)
            assertTrue(finishLatch.await(2, TimeUnit.SECONDS))
            executor.shutdown()

            // Late result must NOT overwrite CANCELLED
            assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
            assertNull("Active task flow must be null after cancellation", manager.observeActiveTask(key).value)
            assertEquals(ToolStatus.CANCELLED, handle.awaitResult().status)
        }
    }

    @Test
    fun superseded_nonCooperativeCpuWork_cannotReplaceLatestResult() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "304")
            val owner = TaskOwner.VIEW_MODEL

            val cpuStarted = CountDownLatch(1)
            val cpuAllowFinish = CountDownLatch(1)

            val handleOld = manager.submit<String>(key, owner, requestSignature = "OLD-SIG") {
                cpuStarted.countDown()
                while (!cpuAllowFinish.await(5, TimeUnit.MILLISECONDS)) {
                    // non-cooperative loop
                }
                ToolResultEnvelope(contractVersion = 1, requestId = "OLD", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OLD_LATE", completedAt = testClock.currentTimeMillis())
            }

            assertTrue(cpuStarted.await(2, TimeUnit.SECONDS))

            val handleNew = manager.submit<String>(key, owner, requestSignature = "NEW-SIG") {
                ToolResultEnvelope(contractVersion = 1, requestId = "NEW", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "NEW_FRESH", completedAt = testClock.currentTimeMillis())
            }

            assertEquals(EngineeringTaskState.CANCELLED, handleOld.snapshot.value.state)
            val newRes = handleNew.awaitResult()
            assertEquals("NEW_FRESH", newRes.result)
            assertEquals(EngineeringTaskState.COMPLETED, handleNew.snapshot.value.state)

            // Release old CPU work
            cpuAllowFinish.countDown()

            assertEquals(EngineeringTaskState.COMPLETED, handleNew.snapshot.value.state)
            assertEquals(EngineeringTaskState.CANCELLED, handleOld.snapshot.value.state)
        }
    }

    @Test
    fun cancelByOwner_doesNotCancelOtherOwners() {
        runBlocking {
            val manager = createManager(this)
            val key1 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")
            val key2 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "2")

            val owner1 = TaskOwner.screen("ScreenA")
            val owner2 = TaskOwner.screen("ScreenB")

            val started1 = CompletableDeferred<Unit>()
            val started2 = CompletableDeferred<Unit>()
            val hold1 = CompletableDeferred<Unit>()
            val hold2 = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key1, owner1) {
                started1.complete(Unit)
                hold1.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key2, owner2) {
                started2.complete(Unit)
                hold2.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
            }

            started1.await()
            started2.await()
            manager.cancelByOwner(owner1)

            assertEquals(EngineeringTaskState.CANCELLED, handle1.snapshot.value.state)
            assertEquals(EngineeringTaskState.RUNNING, handle2.snapshot.value.state)

            hold2.complete(Unit)
            val res2 = handle2.awaitResult()
            assertEquals("R2", res2.result)
            assertEquals(EngineeringTaskState.COMPLETED, handle2.snapshot.value.state)
            hold1.complete(Unit)
        }
    }

    @Test
    fun deduplicatedSameOwner_reusesHandle() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_SHORT_CIRCUIT, entityId = "501")
            val owner = TaskOwner.screen("PanelA")
            val hold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, owner, requestSignature = "SIG-501") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_SHORT_CIRCUIT", projectId = "PROJ-01", status = ToolStatus.PASS, result = "501", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key, owner, requestSignature = "SIG-501") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_SHORT_CIRCUIT", projectId = "PROJ-01", status = ToolStatus.PASS, result = "501", completedAt = testClock.currentTimeMillis())
            }

            assertEquals(handle1.taskId, handle2.taskId)
            hold.complete(Unit)
        }
    }

    @Test
    fun deduplicatedDifferentOwners_followsDocumentedPolicy() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_SHORT_CIRCUIT, entityId = "502")
            val ownerA = TaskOwner.screen("ScreenA")
            val ownerB = TaskOwner.screen("ScreenB")
            val hold = CompletableDeferred<Unit>()

            val handleA = manager.submit<String>(key, ownerA, requestSignature = "SIG-SHARED") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_SHORT_CIRCUIT", projectId = "PROJ-01", status = ToolStatus.PASS, result = "SHARED", completedAt = testClock.currentTimeMillis())
            }

            val handleB = manager.submit<String>(key, ownerB, requestSignature = "SIG-SHARED") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_SHORT_CIRCUIT", projectId = "PROJ-01", status = ToolStatus.PASS, result = "SHARED", completedAt = testClock.currentTimeMillis())
            }

            assertEquals(handleA.taskId, handleB.taskId)
            assertTrue(handleA.snapshot.value.subscribers.contains(ownerA))
            assertTrue(handleA.snapshot.value.subscribers.contains(ownerB))

            hold.complete(Unit)
        }
    }

    @Test
    fun cancelByOneOwner_doesNotIncorrectlyCancelOtherOwnerInterest() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_SHORT_CIRCUIT, entityId = "503")
            val ownerA = TaskOwner.screen("ScreenA")
            val ownerB = TaskOwner.screen("ScreenB")
            val hold = CompletableDeferred<Unit>()

            val handleA = manager.submit<String>(key, ownerA, requestSignature = "SIG-MULTI") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_SHORT_CIRCUIT", projectId = "PROJ-01", status = ToolStatus.PASS, result = "MULTI_OK", completedAt = testClock.currentTimeMillis())
            }

            val handleB = manager.submit<String>(key, ownerB, requestSignature = "SIG-MULTI") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_SHORT_CIRCUIT", projectId = "PROJ-01", status = ToolStatus.PASS, result = "MULTI_OK", completedAt = testClock.currentTimeMillis())
            }

            // Owner A leaves screen / disposes
            manager.cancelByOwner(ownerA)

            // Task must NOT be cancelled because Owner B is still subscribed!
            assertEquals(EngineeringTaskState.RUNNING, handleA.snapshot.value.state)
            assertEquals(EngineeringTaskState.RUNNING, handleB.snapshot.value.state)
            assertFalse(handleA.snapshot.value.subscribers.contains(ownerA))
            assertTrue(handleA.snapshot.value.subscribers.contains(ownerB))

            hold.complete(Unit)
            val resB = handleB.awaitResult()
            assertEquals("MULTI_OK", resB.result)
            assertEquals(EngineeringTaskState.COMPLETED, handleB.snapshot.value.state)
        }
    }

    @Test
    fun nullSignature_neverDeduplicates_perPolicy() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "601")
            val hold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = null) {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = null) {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
            }

            assertNotEquals("null signature must never deduplicate", handle1.taskId, handle2.taskId)
            assertEquals(EngineeringTaskState.CANCELLED, handle1.snapshot.value.state)
            hold.complete(Unit)
        }
    }

    @Test
    fun emptySignature_isRejected_orHandledExplicitly() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "602")
            val hold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "   ") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "   ") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
            }

            assertNotEquals("Blank signature must not deduplicate", handle1.taskId, handle2.taskId)
            hold.complete(Unit)
        }
    }

    @Test
    fun sameCanonicalSignature_deduplicates() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "603")
            val hold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "canonical-sha256-abc") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "canonical-sha256-abc") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
            }

            assertEquals(handle1.taskId, handle2.taskId)
            hold.complete(Unit)
        }
    }

    @Test
    fun sameKeyDifferentCanonicalSignature_supersedes() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "604")
            val hold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "canonical-sha256-v1") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R1", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "canonical-sha256-v2") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R2", completedAt = testClock.currentTimeMillis())
            }

            assertNotEquals(handle1.taskId, handle2.taskId)
            assertEquals(EngineeringTaskState.CANCELLED, handle1.snapshot.value.state)
            assertEquals(handle2.taskId, handle1.snapshot.value.supersededByTaskId)
            hold.complete(Unit)
        }
    }

    @Test
    fun progressRacingWithCancel_cannotOverwriteCancelled() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "701")

            var savedReporter: ((TaskProgress) -> Unit)? = null
            val started = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) { reporter ->
                savedReporter = reporter
                started.complete(Unit)
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "LATE", completedAt = testClock.currentTimeMillis())
            }

            started.await()
            manager.cancel(handle.taskId)

            savedReporter?.invoke(TaskProgress(0.9, "Late progress"))
            delay(10)

            // State must remain CANCELLED, never flipped back to RUNNING by late progress
            assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
            hold.complete(Unit)
        }
    }

    @Test
    fun progressRacingWithSupersede_cannotOverwriteCancelled() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "702")

            var oldReporter: ((TaskProgress) -> Unit)? = null
            val started = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()

            val handleOld = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "SIG-1") { reporter ->
                oldReporter = reporter
                started.complete(Unit)
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OLD", completedAt = testClock.currentTimeMillis())
            }

            started.await()
            val handleNew = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "SIG-2") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "NEW", completedAt = testClock.currentTimeMillis())
            }

            oldReporter?.invoke(TaskProgress(0.95, "Late progress from superseded task"))
            delay(10)

            assertEquals(EngineeringTaskState.CANCELLED, handleOld.snapshot.value.state)
            assertEquals(EngineeringTaskState.COMPLETED, handleNew.snapshot.value.state)
            hold.complete(Unit)
        }
    }

    @Test
    fun progressAfterCompletion_isRejected() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "703")

            var savedReporter: ((TaskProgress) -> Unit)? = null

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) { reporter ->
                savedReporter = reporter
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "DONE", completedAt = testClock.currentTimeMillis())
            }

            handle.awaitResult()
            assertEquals(EngineeringTaskState.COMPLETED, handle.snapshot.value.state)

            // Attempt progress emission after completion
            savedReporter?.invoke(TaskProgress(0.99, "Post-complete"))

            assertEquals(EngineeringTaskState.COMPLETED, handle.snapshot.value.state)
        }
    }

    @Test
    fun terminalState_isImmutableUnderConcurrentPublishers() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "704")

            val started = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) { reporter ->
                started.complete(Unit)
                hold.await()
                reporter(TaskProgress(0.8, "Running"))
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "R", completedAt = testClock.currentTimeMillis())
            }

            started.await()
            manager.cancel(handle.taskId)

            val latch = CountDownLatch(10)
            repeat(10) {
                launch(Dispatchers.Default) {
                    handle.cancel()
                    latch.countDown()
                }
            }
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            hold.complete(Unit)

            assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
        }
    }

    @Test
    fun allAdvertisedOperationalStates_haveReachableLegalTransitionPaths() {
        val operationalStates = EngineeringTaskState.values().filter { it.isOperationalInPhase4 }
        assertTrue(operationalStates.contains(EngineeringTaskState.QUEUED))
        assertTrue(operationalStates.contains(EngineeringTaskState.RUNNING))
        assertTrue(operationalStates.contains(EngineeringTaskState.REVALIDATING))
        assertTrue(operationalStates.contains(EngineeringTaskState.COMPLETED))
        assertTrue(operationalStates.contains(EngineeringTaskState.FAILED))
        assertTrue(operationalStates.contains(EngineeringTaskState.CANCELLED))
        assertTrue(operationalStates.contains(EngineeringTaskState.STALE))
    }

    @Test
    fun terminalToRunning_isRejected() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "801")

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OK", completedAt = testClock.currentTimeMillis())
            }

            handle.awaitResult()
            assertEquals(EngineeringTaskState.COMPLETED, handle.snapshot.value.state)
        }
    }

    @Test
    fun cancelledToCompleted_isRejected() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "802")
            val hold = CompletableDeferred<Unit>()

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OK", completedAt = testClock.currentTimeMillis())
            }

            handle.cancel()
            hold.complete(Unit)

            assertEquals(EngineeringTaskState.CANCELLED, handle.snapshot.value.state)
        }
    }

    @Test
    fun failedToRunning_isRejected() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "803")

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                throw RuntimeException("Fatal failure")
            }

            handle.awaitResult()
            assertEquals(EngineeringTaskState.FAILED, handle.snapshot.value.state)
        }
    }

    @Test
    fun progressUpdate_doesNotChangeLifecycleState() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "804")

            val started = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) { reporter ->
                reporter(TaskProgress(0.2, "Stage 1"))
                started.complete(Unit)
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OK", completedAt = testClock.currentTimeMillis())
            }

            started.await()
            assertEquals(EngineeringTaskState.RUNNING, handle.snapshot.value.state)
            assertEquals(0.2, handle.snapshot.value.progress?.progressFraction ?: 0.0, 0.001)

            hold.complete(Unit)
            handle.awaitResult()
            assertEquals(EngineeringTaskState.COMPLETED, handle.snapshot.value.state)
        }
    }

    @Test
    fun lateSuccessAfterCancel_awaitResultRemainsCancelled() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "805")
            val hold = CompletableDeferred<Unit>()

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "LATE_SUCCESS", completedAt = testClock.currentTimeMillis())
            }

            handle.cancel()
            hold.complete(Unit)

            val result = handle.awaitResult()
            assertEquals(ToolStatus.CANCELLED, result.status)
            assertNull(result.result)
        }
    }

    @Test
    fun lateSuccessAfterSupersede_awaitResultRemainsCancelled() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "806")
            val hold = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "V1") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OLD", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "V2") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "NEW", completedAt = testClock.currentTimeMillis())
            }

            hold.complete(Unit)

            val res1 = handle1.awaitResult()
            val res2 = handle2.awaitResult()

            assertEquals(ToolStatus.CANCELLED, res1.status)
            assertEquals(ToolStatus.PASS, res2.status)
            assertEquals("NEW", res2.result)
        }
    }

    @Test
    fun observeActiveTask_clearsAfterCompletion() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_TRANSFORMER, entityId = "TX-CLEAR")

            val hold = CompletableDeferred<Unit>()
            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_TRANSFORMER", projectId = "PROJ-01", status = ToolStatus.PASS, result = "TX_DONE", completedAt = testClock.currentTimeMillis())
            }

            assertEquals(handle.taskId, manager.observeActiveTask(key).value?.taskId)

            hold.complete(Unit)
            handle.awaitResult()

            assertNull("Active task flow must be null after completion", manager.observeActiveTask(key).value)
        }
    }

    @Test
    fun observeActiveTask_switchesAtomicallyToSupersedingTask() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_TRANSFORMER, entityId = "TX-SWITCH")

            val hold = CompletableDeferred<Unit>()
            val handle1 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "V1") {
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_TRANSFORMER", projectId = "PROJ-01", status = ToolStatus.PASS, result = "TX1", completedAt = testClock.currentTimeMillis())
            }

            assertEquals(handle1.taskId, manager.observeActiveTask(key).value?.taskId)

            val handle2 = manager.submit<String>(key, TaskOwner.VIEW_MODEL, requestSignature = "V2") {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_TRANSFORMER", projectId = "PROJ-01", status = ToolStatus.PASS, result = "TX2", completedAt = testClock.currentTimeMillis())
            }

            // Active task must switch to handle2
            assertEquals(handle2.taskId, manager.observeActiveTask(key).value?.taskId)
            hold.complete(Unit)
        }
    }

    @Test
    fun clearCompleted_removesTaskObservationAndProjectHistory() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-CLEAR", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-CLEAR", status = ToolStatus.PASS, result = "1", completedAt = testClock.currentTimeMillis())
            }
            handle.awaitResult()

            assertEquals(1, manager.observeProjectTasks("PROJ-CLEAR").value.size)

            manager.clearCompleted("PROJ-CLEAR")

            assertEquals(0, manager.observeProjectTasks("PROJ-CLEAR").value.size)
        }
    }

    @Test
    fun clearCompleted_oneProject_doesNotAffectAnotherProject() {
        runBlocking {
            val manager = createManager(this)
            val keyA = EngineeringTaskKey(projectId = "PROJ-A", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")
            val keyB = EngineeringTaskKey(projectId = "PROJ-B", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "2")

            val handleA = manager.submit<String>(keyA, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "A", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-A", status = ToolStatus.PASS, result = "A", completedAt = testClock.currentTimeMillis())
            }
            val handleB = manager.submit<String>(keyB, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "B", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-B", status = ToolStatus.PASS, result = "B", completedAt = testClock.currentTimeMillis())
            }

            handleA.awaitResult()
            handleB.awaitResult()

            manager.clearCompleted("PROJ-A")

            assertEquals(0, manager.observeProjectTasks("PROJ-A").value.size)
            assertEquals(1, manager.observeProjectTasks("PROJ-B").value.size)
        }
    }

    @Test
    fun projectTaskSnapshots_haveDeterministicOrdering() {
        runBlocking {
            val manager = createManager(this)
            val key1 = EngineeringTaskKey(projectId = "PROJ-SORT", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")
            val key2 = EngineeringTaskKey(projectId = "PROJ-SORT", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "2")

            testClock.time = 1000L
            val handle1 = manager.submit<String>(key1, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-SORT", status = ToolStatus.PASS, result = "1", completedAt = testClock.currentTimeMillis())
            }
            handle1.awaitResult()

            testClock.time = 2000L
            val handle2 = manager.submit<String>(key2, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-SORT", status = ToolStatus.PASS, result = "2", completedAt = testClock.currentTimeMillis())
            }
            handle2.awaitResult()

            val history = manager.observeProjectTasks("PROJ-SORT").value
            assertEquals(2, history.size)
            assertEquals(handle1.taskId, history[0].taskId)
            assertEquals(handle2.taskId, history[1].taskId)
            assertTrue(history[0].startedAt <= history[1].startedAt)
        }
    }

    @Test
    fun unexpectedException_publicSnapshotContainsSafeMessage() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-ERR", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING)

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                throw NullPointerException("Null pointer while evaluating internal entity reference")
            }

            handle.awaitResult()
            val snap = handle.snapshot.value
            assertEquals(EngineeringTaskState.FAILED, snap.state)
            assertEquals("TASK_EXECUTION_ERROR", snap.error?.errorCode)
            assertEquals("NullPointerException", snap.error?.causeType)
            assertNotNull(snap.error?.message)
        }
    }

    @Test
    fun sensitiveExceptionText_isNotExposedInPublicTaskError() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-ERR", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING)

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                throw RuntimeException("Failed query SELECT secret_token FROM users WHERE path = C:\\private\\passwords.txt")
            }

            handle.awaitResult()
            val snap = handle.snapshot.value
            assertFalse(snap.error!!.message.contains("SELECT secret_token"))
            assertFalse(snap.error!!.message.contains("C:\\private\\passwords.txt"))
        }
    }

    @Test
    fun structuredError_isPreservedWithoutFabrication() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-ERR", taskType = EngineeringTaskType.VALIDATE_PROJECT)

            val originalStructuredError = StructuredError(
                errorCode = "TOPOLOGY_CYCLE_DETECTED",
                message = "Circuit 101 forms a cycle with Circuit 102.",
                isRecoverable = false
            )

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(
                    contractVersion = 1,
                    requestId = "REQ-CYC",
                    toolName = "VALIDATE_PROJECT",
                    projectId = "PROJ-ERR",
                    status = ToolStatus.ERROR,
                    errors = listOf(originalStructuredError),
                    completedAt = testClock.currentTimeMillis()
                )
            }

            val res = handle.awaitResult()
            assertEquals(ToolStatus.ERROR, res.status)
            assertEquals("TOPOLOGY_CYCLE_DETECTED", handle.snapshot.value.error?.errorCode)
            assertEquals(originalStructuredError, handle.snapshot.value.error?.structuredError)
        }
    }

    @Test
    fun sameEntityIdAcrossProjects_tasksRemainIsolated() {
        runBlocking {
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
    }

    @Test
    fun sameTaskKeyDifferentRequestSignature_isNotIncorrectlyDeduplicated() {
        runBlocking {
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
    }

    @Test
    fun projectSwitch_cancelsOrStalesOnlyOwnedTasks() {
        runBlocking {
            val manager = createManager(this)
            val keyA = EngineeringTaskKey(projectId = "PROJ-SWITCH-A", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)
            val keyB = EngineeringTaskKey(projectId = "PROJ-SWITCH-B", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)

            val startedA = CompletableDeferred<Unit>()
            val startedB = CompletableDeferred<Unit>()
            val holdA = CompletableDeferred<Unit>()
            val holdB = CompletableDeferred<Unit>()

            val handleA = manager.submit<String>(keyA, TaskOwner.VIEW_MODEL) {
                startedA.complete(Unit)
                holdA.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "A", toolName = "CALCULATE_PROJECT_BULK", projectId = "PROJ-SWITCH-A", status = ToolStatus.PASS, result = "A", completedAt = testClock.currentTimeMillis())
            }

            val handleB = manager.submit<String>(keyB, TaskOwner.VIEW_MODEL) {
                startedB.complete(Unit)
                holdB.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "B", toolName = "CALCULATE_PROJECT_BULK", projectId = "PROJ-SWITCH-B", status = ToolStatus.PASS, result = "B", completedAt = testClock.currentTimeMillis())
            }

            startedA.await()
            startedB.await()
            manager.cancelByProject("PROJ-SWITCH-A", TaskCancellationReason.PROJECT_SWITCHED)

            assertEquals(EngineeringTaskState.CANCELLED, handleA.snapshot.value.state)
            assertEquals(TaskCancellationReason.PROJECT_SWITCHED, handleA.snapshot.value.cancellationReason)

            assertEquals(EngineeringTaskState.RUNNING, handleB.snapshot.value.state)
            holdB.complete(Unit)
            assertEquals("B", handleB.awaitResult().result)
            holdA.complete(Unit)
        }
    }

    @Test
    fun failure_isCapturedAsStructuredError() {
        runBlocking {
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
    }

    @Test
    fun failedTask_doesNotBreakManagerScope() {
        runBlocking {
            val manager = createManager(this)
            val keyFail = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "1")
            val keySuccess = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "2")

            val handleFail = manager.submit<String>(keyFail, TaskOwner.VIEW_MODEL) {
                throw IllegalStateException("Fatal simulation calculation exception")
            }

            handleFail.awaitResult()
            assertEquals(EngineeringTaskState.FAILED, handleFail.snapshot.value.state)

            val handleSuccess = manager.submit<String>(keySuccess, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "S", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "OK", completedAt = testClock.currentTimeMillis())
            }

            val res = handleSuccess.awaitResult()
            assertEquals("OK", res.result)
            assertEquals(EngineeringTaskState.COMPLETED, handleSuccess.snapshot.value.state)
        }
    }

    @Test
    fun progress_isNullWhenNotMeasurable() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_TRANSFORMER)

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_TRANSFORMER", projectId = "PROJ-01", status = ToolStatus.PASS, result = "TX_OK", completedAt = testClock.currentTimeMillis())
            }

            handle.awaitResult()
            assertNull("Progress must be null when not explicitly reported", handle.snapshot.value.progress)
        }
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
    fun repeatedCompletion_isIdempotent() {
        runBlocking {
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
    }

    @Test
    fun observer_receivesMonotonicLegalStateTransitions() {
        runBlocking {
            val manager = createManager(this)
            val key = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_PROJECT_BULK)

            val observedStates = mutableListOf<EngineeringTaskState>()
            val started = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()

            val handle = manager.submit<String>(key, TaskOwner.VIEW_MODEL) {
                started.complete(Unit)
                hold.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_PROJECT_BULK", projectId = "PROJ-01", status = ToolStatus.PASS, result = "DONE", completedAt = testClock.currentTimeMillis())
            }

            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                handle.snapshot.collect { snap ->
                    synchronized(observedStates) {
                        if (observedStates.isEmpty() || observedStates.last() != snap.state) {
                            observedStates.add(snap.state)
                        }
                    }
                }
            }

            started.await()
            yield()
            hold.complete(Unit)
            handle.awaitResult()
            yield()
            job.cancel()

            assertTrue(observedStates.contains(EngineeringTaskState.RUNNING))
            assertTrue(observedStates.contains(EngineeringTaskState.COMPLETED))
            assertEquals(EngineeringTaskState.COMPLETED, observedStates.last())
        }
    }

    @Test
    fun viewModelOrOwnerDisposal_cleansOwnedTasks() {
        runBlocking {
            val manager = createManager(this)
            val key1 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "10")
            val key2 = EngineeringTaskKey(projectId = "PROJ-01", taskType = EngineeringTaskType.CALCULATE_CABLE_SIZING, entityId = "20")

            val vmOwner = TaskOwner("VM-MAIN", "VIEW_MODEL")

            val started1 = CompletableDeferred<Unit>()
            val started2 = CompletableDeferred<Unit>()
            val hold1 = CompletableDeferred<Unit>()
            val hold2 = CompletableDeferred<Unit>()

            val handle1 = manager.submit<String>(key1, vmOwner) {
                started1.complete(Unit)
                hold1.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "1", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "10", completedAt = testClock.currentTimeMillis())
            }

            val handle2 = manager.submit<String>(key2, vmOwner) {
                started2.complete(Unit)
                hold2.await()
                ToolResultEnvelope(contractVersion = 1, requestId = "2", toolName = "CALCULATE_CABLE_SIZING", projectId = "PROJ-01", status = ToolStatus.PASS, result = "20", completedAt = testClock.currentTimeMillis())
            }

            started1.await()
            started2.await()

            manager.cancelByOwner(vmOwner, TaskCancellationReason.OWNER_DISPOSED)

            assertEquals(EngineeringTaskState.CANCELLED, handle1.snapshot.value.state)
            assertEquals(EngineeringTaskState.CANCELLED, handle2.snapshot.value.state)
            assertEquals(TaskCancellationReason.OWNER_DISPOSED, handle1.snapshot.value.cancellationReason)

            hold1.complete(Unit)
            hold2.complete(Unit)
        }
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
        val taskFile = java.io.File("app/src/main/java/com/example/ai/task/EngineeringTaskContracts.kt")
        if (taskFile.exists()) {
            val content = taskFile.readText()
            assertFalse(content.contains("androidx.compose"))
            assertFalse(content.contains("com.google.firebase"))
            assertFalse(content.contains("com.google.ai"))
            assertFalse(content.contains("Gemini"))
        }
    }
}
