package com.example.ai.hybrid

import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1-1 (K-01, V-02) regression suite for the production orchestrator resolution path
 * ([ConversationEngineProvider.provideConversationEngine] -> [CloudLlmConversationEngine]).
 *
 * The defect: the provider's `orchestratorProvider` lambda ran `runBlocking { toolRuntimeProvider() }`
 * on the calling thread. That parked the Main dispatcher for the duration of the runtime resolution,
 * created a fresh coroutine scope that discarded the caller's context, was immune to job
 * cancellation / `withTimeout`, and swallowed every `Throwable` through `runCatching`.
 *
 * These tests exercise the REAL production composition (real provider graph, stub cloud seam),
 * not a mock of it: the provider lambda only runs when the cloud path is configured and the turn
 * reaches the orchestrator resolution step, which is exactly what the gate + seam stub arrange.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationEngineProviderSuspensionTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "mock-valid-api-key")
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
        Dispatchers.resetMain()
    }

    /** Stub seam: configured via the gate, answers benign text so the turn completes on the cloud path. */
    private class StubSeam : GeminiClientSeam {
        override suspend fun generate(
            systemPrompt: String,
            userPrompt: String,
            tools: List<CloudFunctionDeclaration>
        ): CloudLlmRawResponse = CloudLlmRawResponse(text = "Hello! How can I help you today?")
    }

    private fun sampleContext() = BoundedProjectContext(
        projectId = "PROJ-P1-1",
        projectName = "P1-1 Suspension Project",
        liveRevision = 7L
    )

    private fun sampleInput() = ConversationTurnInput(
        userUtterance = "Hello, can you help me?",
        projectContext = sampleContext(),
        priorState = ConversationTaskState(
            taskId = "TASK-P1-1",
            projectId = "PROJ-P1-1",
            baseProjectRevision = 7L
        )
    )

    private fun buildEngine(
        toolRuntimeProvider: (suspend () -> AssistantToolRuntime?)?
    ): ConversationEngine = ConversationEngineProvider.provideConversationEngine(
        clientSeam = StubSeam(),
        toolRuntimeProvider = toolRuntimeProvider
    )

    @Test
    fun orchestratorProviderSuspendsInsteadOfParkingTheCallingDispatcher() = runTest(testDispatcher) {
        // Peer coroutine scheduled on the SAME single-threaded dispatcher as the turn.
        var markerRan = false
        val marker = launch {
            delay(50)
            markerRan = true
        }

        // Provider resolution takes 150ms of (virtual) time — long enough for the marker to interleave.
        val engine = buildEngine(toolRuntimeProvider = { delay(150); null })
        val result = engine.evaluateTurn(sampleInput())

        assertTrue(
            "The orchestrator provider must suspend so peer coroutines on the same dispatcher keep " +
                "running; runBlocking parks the thread and starves them.",
            markerRan
        )
        assertNotNull("turn must complete on the cloud path", result)
        marker.cancel()
    }

    @Test
    fun orchestratorProviderIsCancellableByTheCallingScope() = runTest(testDispatcher) {
        val neverCompleted = CompletableDeferred<Unit>() // deliberately never completed
        val engine = buildEngine(toolRuntimeProvider = { neverCompleted.await(); null })

        val turn = launch { engine.evaluateTurn(sampleInput()) }
        runCurrent() // advance until the turn parks inside the provider resolution
        assertTrue("turn must be in flight before cancellation", turn.isActive)

        turn.cancelAndJoin()
        assertTrue(
            "Turn cancellation must reach the orchestrator provider; runBlocking would swallow it " +
                "and the turn could only end by completing the blocked scope.",
            turn.isCancelled
        )
    }

    @Test
    fun orchestratorProviderIsBoundedByTheCallerTimeoutBudget() = runTest(testDispatcher) {
        val engine = buildEngine(toolRuntimeProvider = { awaitCancellation() })

        val outcome = runCatching {
            withTimeout(CALLER_TIMEOUT_MS) { engine.evaluateTurn(sampleInput()) }
        }

        // Either the caller's TimeoutCancellationException propagates, or the router converted it
        // into its honest NETWORK_TIMEOUT local fallback. Both outcomes prove the turn ENDED at the
        // bound and the provider did not outlive it — the runBlocking defect made the provider
        // unreachable by any timeout at all.
        val bounded = outcome.exceptionOrNull() is TimeoutCancellationException ||
            outcome.getOrNull()?.cloudDegradationReason == CloudDegradationReason.NETWORK_TIMEOUT
        assertTrue(
            "The turn must be bounded by the caller's timeout; outcome=$outcome",
            bounded
        )
    }

    @Test
    fun orchestratorProviderFailureIsContainedLoggedAndTheTurnContinues() = runTest(testDispatcher) {
        val engine = buildEngine(
            toolRuntimeProvider = { throw IllegalStateException("tool runtime exploded") }
        )

        val result = engine.evaluateTurn(sampleInput())

        assertNotNull("A provider failure must not crash the turn", result)
        assertNull(
            "A provider failure must degrade to the single-turn path without a degradation flag " +
                "(and be logged), never surface a phantom orchestrator.",
            result.cloudDegradationReason
        )
        assertEquals(ConversationEngineSource.CLOUD_GEMINI, result.source)
    }

    @Test
    fun orchestratorProviderInheritsTheCallerCoroutineContext() = runTest(testDispatcher) {
        var coroutineNameInside: String? = null
        val engine = buildEngine(
            toolRuntimeProvider = {
                coroutineNameInside = currentCoroutineContext()[CoroutineName]?.name
                null
            }
        )

        withContext(CoroutineName(CONTEXT_PROBE)) {
            engine.evaluateTurn(sampleInput())
        }

        assertEquals(
            "The provider must execute inside the caller's coroutine; runBlocking starts a fresh " +
                "scope and silently drops caller context elements.",
            CONTEXT_PROBE,
            coroutineNameInside
        )
    }

    companion object {
        private const val CONTEXT_PROBE = "p1-1-context-probe"
        private const val CALLER_TIMEOUT_MS = 500L
    }
}
