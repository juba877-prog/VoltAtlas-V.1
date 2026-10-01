package com.example.ai.ui

import com.example.ai.context.EngineeringContext
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.hybrid.CloudLlmFeatureGate
import com.example.ai.hybrid.ConversationEngine
import com.example.ai.hybrid.ConversationEngineSource
import com.example.ai.hybrid.HybridEngineResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P0-14 (O-02, S-11) regression tests: assistant conversation surface is project-scoped.
 *
 * Project A's messages, typed cards, drafts and in-flight turns must never survive a switch
 * to project B — neither through [EngineeringAssistantViewModel.open] (user re-open) nor
 * through [EngineeringAssistantViewModel.onProjectChanged] (MainLayout's automatic hook on
 * activeProjectId change). A same-project reopen must preserve the conversation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantProjectIsolationTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Deterministic revision source so a turn is allowed to proceed past the fail-closed gate. */
    private val revisionProvider = object : ProjectRevisionProvider {
        override suspend fun getCurrentRevision(projectId: String) =
            ProjectRevisionSnapshot(projectId, 7L, System.currentTimeMillis())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        CloudLlmFeatureGate.resetToDefaults()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        CloudLlmFeatureGate.resetToDefaults()
    }

    private fun ctx(projectId: String, projectName: String) =
        EngineeringContext(projectId = projectId, projectName = projectName)

    /** Engine that never answers until [release] completes — used to park an in-flight turn. */
    private class BlockingEngine : ConversationEngine {
        val release = CompletableDeferred<Unit>()
        override suspend fun evaluateTurn(
            input: ConversationTurnInput,
            decisionIdProvider: () -> String
        ): HybridEngineResult {
            release.await()
            return HybridEngineResult(
                decision = com.example.ai.conversation.ConversationTurnDecision(
                    decisionId = "DEC-LATE",
                    action = ConversationAction(ConversationActionType.EXPLAIN_RESULT, "TEST"),
                    updatedState = ConversationTaskState("T-LATE", input.projectContext.projectId, 1L),
                    explanationSummary = "Late answer that must never surface after a switch."
                ),
                source = ConversationEngineSource.LOCAL_RULE_ENGINE,
                cloudDegradationReason = null
            )
        }
    }

    // ------------------------------------------------------------------
    // 1. open() with a different projectId resets the whole surface
    // ------------------------------------------------------------------
    @Test
    fun openWithDifferentProject_resetsMessagesCardsAndDraft() = runTest(testDispatcher) {
        val vm = EngineeringAssistantViewModel()

        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_A", "Project Alpha"))
        assertEquals("greeting seeded on first open", 1, vm.uiState.value.messages.size)

        // Fail-closed turn: no tool/engine is reached; a ctx-blocked card is raised instead.
        vm.onUserSubmit("Size the main feeder")
        advanceUntilIdle()
        assertEquals("greeting + user message", 2, vm.uiState.value.messages.size)
        assertEquals("ctx-blocked card raised", 1, vm.uiState.value.typedCards.size)
        vm.setInputText("draft for A")

        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_B", "Project Beta"))
        val after = vm.uiState.value
        assertEquals("PROJ_B", after.context.projectId)
        assertEquals(
            "conversation must restart with exactly the fresh greeting — A's messages are gone",
            1, after.messages.size
        )
        assertTrue(
            "greeting must belong to project B",
            after.messages.first().text.contains("Project Beta")
        )
        assertEquals("A's typed cards must not survive the switch", 0, after.typedCards.size)
        assertEquals("A's draft must not survive the switch", "", after.inputText)
        assertFalse("isThinking must be reset", after.isThinking)
        assertNull("errorMessage must be reset", after.errorMessage)
        assertEquals(ApprovalExecutionUiState.Idle, after.approvalExecutionState)
        assertTrue("sheet stays open after the automatic switch", after.isOpen)
    }

    // ------------------------------------------------------------------
    // 2. Same-project reopen preserves the conversation
    // ------------------------------------------------------------------
    @Test
    fun openWithSameProject_preservesConversation() = runTest(testDispatcher) {
        val vm = EngineeringAssistantViewModel()

        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_B", "Project Beta"))
        vm.onUserSubmit("Explain the earthing result")
        advanceUntilIdle()
        val before = vm.uiState.value
        assertEquals(2, before.messages.size)
        assertEquals(1, before.typedCards.size)

        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_B", "Project Beta"))
        val after = vm.uiState.value
        assertEquals(
            "same-project reopen must keep the conversation",
            before.messages, after.messages
        )
        assertEquals(
            "same-project reopen must keep the typed cards",
            before.typedCards, after.typedCards
        )
    }

    // ------------------------------------------------------------------
    // 3. Automatic hook path (MainLayout observes activeProjectId)
    // ------------------------------------------------------------------
    @Test
    fun onProjectChanged_withoutOpen_resetsTheSurface() = runTest(testDispatcher) {
        val vm = EngineeringAssistantViewModel()

        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_A", "Project Alpha"))
        vm.onUserSubmit("What is the tie status?")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.messages.size)
        assertEquals(1, vm.uiState.value.typedCards.size)
        vm.setInputText("half-typed question")

        // MainLayout calls this automatically on every activeProjectId change — no open() involved.
        vm.onProjectChanged("PROJ_B")

        val after = vm.uiState.value
        assertEquals("conversation dropped without an open() call", 0, after.messages.size)
        assertEquals("cards dropped without an open() call", 0, after.typedCards.size)
        assertEquals("", after.inputText)
        assertFalse(after.isThinking)
        assertEquals(ApprovalExecutionUiState.Idle, after.approvalExecutionState)

        // No-op for the same project afterwards.
        vm.onProjectChanged("PROJ_B")
        assertEquals(0, vm.uiState.value.messages.size)
    }

    // ------------------------------------------------------------------
    // 4. In-flight turn from project A never lands under project B
    // ------------------------------------------------------------------
    @Test
    fun inFlightTurnFromOldProject_isCancelledAndNeverSurfaces() = runTest(testDispatcher) {
        val engine = BlockingEngine()
        val vm = EngineeringAssistantViewModel(
            projectRevisionProvider = revisionProvider,
            conversationEngine = engine
        )

        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_A", "Project Alpha"))
        vm.onUserSubmit("Run the cable analysis")
        advanceUntilIdle()
        // Turn is parked inside evaluateTurn; the user message is shown, thinking is on.
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue("turn must be in flight", vm.uiState.value.isThinking)

        // Switch projects while the old turn is still running.
        vm.open(AssistantEntryPoint.DASHBOARD_ACTION, ctx("PROJ_B", "Project Beta"))
        engine.release.complete(Unit)
        advanceUntilIdle()

        val after = vm.uiState.value
        assertEquals("B greeting only — the late A answer must never surface", 1, after.messages.size)
        assertTrue("greeting belongs to B", after.messages.first().text.contains("Project Beta"))
        assertEquals("no card may arrive from the cancelled turn", 0, after.typedCards.size)
        assertFalse("thinking cleared by the switch", after.isThinking)
    }
}
