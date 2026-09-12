package com.example.ui.dialogs.circuiteditor.model

/**
 * Workflow step definitions and step progression logic for the Circuit Editor.
 */
enum class CircuitWorkflowStep(val title: String, val subtitle: String) {
    IDENTITY("1. Identity & Source", "Basic information about this circuit"),
    LOAD("2. Load Specifications", "Load details and electrical parameters"),
    SUPPLY("3. Supply & Backup", "Optional supply category and sources"),
    FEEDER("4. Cable Selection", "Select cable family and configuration"),
    INSTALLATION("5. Installation Conditions", "Environmental and installation details"),
    PANEL_HEADER("6. Panel Header Equipment", "Switchboard ratings and incoming specifications"),
    REVIEW("Review & Save", "Check all details before saving")
}

object CircuitWorkflow {

    /**
     * Source-inspected predicate from legacy CircuitEditDialog line 899:
     * `panelType != "LOAD" && panelType.isNotBlank()`
     *
     * Preserves exact case and whitespace behavior without normalization.
     */
    fun isPanelHeaderApplicable(panelType: String?): Boolean {
        return panelType != null && panelType != "LOAD" && panelType.isNotBlank()
    }

    /**
     * Returns the ordered list of applicable workflow steps for the given [panelType].
     * Exactly 6 steps for LOAD circuits; exactly 7 steps (inserting PANEL_HEADER before REVIEW)
     * for distribution boards and switchboards.
     */
    fun getApplicableSteps(panelType: String?): List<CircuitWorkflowStep> {
        return if (isPanelHeaderApplicable(panelType)) {
            listOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        } else {
            listOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        }
    }

    /**
     * Deterministic step coercion rule:
     * If currentStep == PANEL_HEADER and PANEL_HEADER becomes inapplicable, coerce to INSTALLATION.
     */
    fun coerceStepOnApplicabilityChange(
        currentStep: CircuitWorkflowStep,
        panelType: String?
    ): CircuitWorkflowStep {
        return if (currentStep == CircuitWorkflowStep.PANEL_HEADER && !isPanelHeaderApplicable(panelType)) {
            CircuitWorkflowStep.INSTALLATION
        } else {
            currentStep
        }
    }

    /**
     * Validates whether navigation from [currentStep] to [targetStep] is permissible.
     * Backward navigation to previously visited steps is always allowed.
     * Forward transition to the immediate next step is allowed if [isCurrentStepValid] is true.
     */
    fun canNavigateTo(
        targetStep: CircuitWorkflowStep,
        currentStep: CircuitWorkflowStep,
        applicableSteps: List<CircuitWorkflowStep>,
        visitedSteps: Set<CircuitWorkflowStep>,
        isCurrentStepValid: Boolean
    ): Boolean {
        val currentIndex = applicableSteps.indexOf(currentStep)
        val targetIndex = applicableSteps.indexOf(targetStep)
        if (currentIndex == -1 || targetIndex == -1) return false
        if (targetIndex < currentIndex) {
            // Backward jump to visited step
            return targetStep in visitedSteps
        }
        if (targetIndex == currentIndex + 1) {
            // Immediate next step
            return isCurrentStepValid
        }
        // Direct forward jump past unvisited steps is blocked
        return false
    }
}
