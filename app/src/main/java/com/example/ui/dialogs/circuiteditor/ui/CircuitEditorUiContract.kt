package com.example.ui.dialogs.circuiteditor.ui

import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot

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
    val identityError: String? = null,
    val availableGenerators: List<GeneratorSnapshot> = emptyList(),
    val availableAts: List<AtsSnapshot> = emptyList(),
    val availableUps: List<UpsSnapshot> = emptyList(),
    val catalogSnapshot: CableCatalogSnapshot = CableCatalogSnapshot(),
    val scRatingText: String = "N/A (Calculated after save)",
    val reviewError: String? = null
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
    fun onSaveClick() {}
}
