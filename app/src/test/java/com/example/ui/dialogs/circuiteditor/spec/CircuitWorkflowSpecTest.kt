package com.example.ui.dialogs.circuiteditor.spec

import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM specification tests for [CircuitWorkflow].
 *
 * Evidence Label: NEW_MAPPER_SPECIFIED
 */
class CircuitWorkflowSpecTest {

    @Test
    fun test01_panelHeaderApplicabilityPredicate_allCases() {
        assertFalse("null should not be applicable", CircuitWorkflow.isPanelHeaderApplicable(null))
        assertFalse("LOAD should not be applicable", CircuitWorkflow.isPanelHeaderApplicable("LOAD"))
        assertFalse("empty string should not be applicable", CircuitWorkflow.isPanelHeaderApplicable(""))
        assertFalse("whitespace only should not be applicable", CircuitWorkflow.isPanelHeaderApplicable("   "))

        assertTrue("MDB should be applicable", CircuitWorkflow.isPanelHeaderApplicable("MDB"))
        assertTrue("SMDB should be applicable", CircuitWorkflow.isPanelHeaderApplicable("SMDB"))
        assertTrue("DB should be applicable", CircuitWorkflow.isPanelHeaderApplicable("DB"))
        assertTrue("MCC should be applicable", CircuitWorkflow.isPanelHeaderApplicable("MCC"))
        assertTrue("PANEL should be applicable", CircuitWorkflow.isPanelHeaderApplicable("PANEL"))
        assertTrue("OTHER should be applicable", CircuitWorkflow.isPanelHeaderApplicable("OTHER"))

        // Exact legacy quirks: un-normalized strings
        assertTrue("lowercase load does not equal LOAD", CircuitWorkflow.isPanelHeaderApplicable("load"))
        assertTrue("surrounding whitespace does not equal LOAD", CircuitWorkflow.isPanelHeaderApplicable(" LOAD "))
        assertTrue("unknown non-blank type should be applicable", CircuitWorkflow.isPanelHeaderApplicable("CUSTOM_BOARD"))
    }

    @Test
    fun test02_applicableStepsCountAndSequence() {
        val loadSteps = CircuitWorkflow.getApplicableSteps("LOAD")
        assertEquals(6, loadSteps.size)
        assertFalse(loadSteps.contains(CircuitWorkflowStep.PANEL_HEADER))
        assertEquals(CircuitWorkflowStep.REVIEW, loadSteps.last())

        val panelSteps = CircuitWorkflow.getApplicableSteps("DB")
        assertEquals(7, panelSteps.size)
        assertTrue(panelSteps.contains(CircuitWorkflowStep.PANEL_HEADER))
        assertEquals(CircuitWorkflowStep.PANEL_HEADER, panelSteps[5])
        assertEquals(CircuitWorkflowStep.REVIEW, panelSteps[6])
    }

    @Test
    fun test03_defensiveCoercionOnApplicabilityRemoval() {
        // If user is on PANEL_HEADER and panelType becomes LOAD, coerce to INSTALLATION
        val coerced = CircuitWorkflow.coerceStepOnApplicabilityChange(
            currentStep = CircuitWorkflowStep.PANEL_HEADER,
            panelType = "LOAD"
        )
        assertEquals(CircuitWorkflowStep.INSTALLATION, coerced)

        // If user is on PANEL_HEADER and panelType is MDB, remain on PANEL_HEADER
        val notCoerced = CircuitWorkflow.coerceStepOnApplicabilityChange(
            currentStep = CircuitWorkflowStep.PANEL_HEADER,
            panelType = "MDB"
        )
        assertEquals(CircuitWorkflowStep.PANEL_HEADER, notCoerced)

        // If user is on IDENTITY and panelType is LOAD, remain on IDENTITY
        val identity = CircuitWorkflow.coerceStepOnApplicabilityChange(
            currentStep = CircuitWorkflowStep.IDENTITY,
            panelType = "LOAD"
        )
        assertEquals(CircuitWorkflowStep.IDENTITY, identity)
    }

    @Test
    fun test04_navigationRules() {
        val steps = CircuitWorkflow.getApplicableSteps("LOAD")
        val visited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)

        // Valid forward step from IDENTITY to LOAD
        assertTrue(
            CircuitWorkflow.canNavigateTo(
                targetStep = CircuitWorkflowStep.LOAD,
                currentStep = CircuitWorkflowStep.IDENTITY,
                applicableSteps = steps,
                visitedSteps = visited,
                isCurrentStepValid = true
            )
        )

        // Invalid forward step from IDENTITY to LOAD (blocked because current step invalid)
        assertFalse(
            CircuitWorkflow.canNavigateTo(
                targetStep = CircuitWorkflowStep.LOAD,
                currentStep = CircuitWorkflowStep.IDENTITY,
                applicableSteps = steps,
                visitedSteps = visited,
                isCurrentStepValid = false
            )
        )

        // Backward jump to visited step (LOAD -> IDENTITY)
        assertTrue(
            CircuitWorkflow.canNavigateTo(
                targetStep = CircuitWorkflowStep.IDENTITY,
                currentStep = CircuitWorkflowStep.LOAD,
                applicableSteps = steps,
                visitedSteps = visited,
                isCurrentStepValid = false // backward jump does not depend on current step validity
            )
        )

        // Direct forward jump past unvisited step (IDENTITY -> SUPPLY)
        assertFalse(
            CircuitWorkflow.canNavigateTo(
                targetStep = CircuitWorkflowStep.SUPPLY,
                currentStep = CircuitWorkflowStep.IDENTITY,
                applicableSteps = steps,
                visitedSteps = visited,
                isCurrentStepValid = true
            )
        )
    }
}
