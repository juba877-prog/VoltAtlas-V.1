package com.example.ui.dialogs.circuiteditor.ui

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot

/**
 * Immutable state consumed by the Circuit Editor UI layer.
 */
data class CircuitEditorUiState(
    val draft: CircuitEditorDraft,
    val currentStep: CircuitWorkflowStep = CircuitWorkflowStep.IDENTITY,
    val visitedSteps: Set<CircuitWorkflowStep> = setOf(CircuitWorkflowStep.IDENTITY),
    val applicableSteps: List<CircuitWorkflowStep> = CircuitWorkflow.getApplicableSteps(draft.panelType),
    val isEditing: Boolean = false,
    val editingCircuitId: Int = 0,
    val projectSubtitle: String? = null,
    val existingCircuits: List<CircuitEntity> = emptyList(),
    val availableTransformers: List<TransformerSnapshot> = emptyList(),
    val sourceResolution: CircuitSourceResolution,
    val identityError: String? = null
)

/**
 * Callbacks emitted by the Circuit Editor UI layer.
 */
interface CircuitEditorUiEvents {
    fun onDraftChange(newDraft: CircuitEditorDraft)
    fun onStepChange(targetStep: CircuitWorkflowStep)
    fun onNextClick()
    fun onBackClick()
    fun onDismiss()
    fun onAddTransformer()
}
