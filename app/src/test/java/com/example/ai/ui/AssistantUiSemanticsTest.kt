package com.example.ai.ui

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.context.EngineeringContext
import org.junit.Assert.*
import org.junit.Test

class AssistantUiSemanticsTest {

    // 1. Predictive Back collapses expanded sheet before closing
    @Test
    fun predictiveBack_expandedCollapsesBeforeDismiss() {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectName = "Test Mall"))
        viewModel.toggleExpand()

        assertTrue(viewModel.uiState.value.isExpanded)
        assertTrue(viewModel.uiState.value.isOpen)

        // First Back: Collapses to compact
        val consumed1 = viewModel.onBackHandled()
        assertTrue(consumed1)
        assertFalse(viewModel.uiState.value.isExpanded)
        assertTrue(viewModel.uiState.value.isOpen)

        // Second Back: Closes assistant
        val consumed2 = viewModel.onBackHandled()
        assertTrue(consumed2)
        assertFalse(viewModel.uiState.value.isOpen)

        // Third Back: Not consumed (passes to underlying navigation)
        val consumed3 = viewModel.onBackHandled()
        assertFalse(consumed3)
    }

    // 2. Predictive Back from compact dismisses without underlying pop
    @Test
    fun predictiveBack_compactDismissesWithoutUnderlyingPop() {
        val viewModel = EngineeringAssistantViewModel()
        viewModel.open(AssistantEntryPoint.TOP_BAR_ICON, EngineeringContext(projectName = "Test Mall"))

        assertFalse(viewModel.uiState.value.isExpanded)
        assertTrue(viewModel.uiState.value.isOpen)

        val consumed = viewModel.onBackHandled()
        assertTrue(consumed)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    // 3. Accessibility labels for PASS, FAIL, WARNING, STALE
    @Test
    fun passFailWarningStale_haveAccessibleText() {
        val pass = EngineeringStatusUiModel.PASS
        val fail = EngineeringStatusUiModel.FAIL
        val warning = EngineeringStatusUiModel.WARNING
        val stale = EngineeringStatusUiModel.STALE

        assertEquals("PASS", pass.label)
        assertEquals("FAIL", fail.label)
        assertEquals("WARNING", warning.label)
        assertEquals("STALE", stale.label)
    }

    // 4. No tool execution or DB mutation from UI
    @Test
    fun noToolExecutionFromUiCallbacks() {
        val uiClass = AssistantResponseMapper::class.java
        for (field in uiClass.declaredFields) {
            assertFalse(field.type.name.contains("Room"))
            assertFalse(field.type.name.contains("DurableMutationWorkflow"))
            assertFalse(field.type.name.contains("ApprovalGrantDao"))
        }
    }

    // 5. ExecutionMode.MUTATION remains strictly disabled
    @Test
    fun executionModeMutationRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }
}
