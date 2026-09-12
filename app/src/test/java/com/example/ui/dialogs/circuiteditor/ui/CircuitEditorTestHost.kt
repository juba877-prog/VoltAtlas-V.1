package com.example.ui.dialogs.circuiteditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot
import com.example.ui.dialogs.circuiteditor.validation.CircuitEditorValidator
import com.example.ui.dialogs.circuiteditor.validation.ValidationResult
import com.example.ui.theme.VoltAtlasTheme

/**
 * Observable test state container for CircuitEditor tests.
 */
class CircuitEditorTestStateHolder(
    initialDraft: CircuitEditorDraft = CircuitEditorDraft(),
    initialStep: CircuitWorkflowStep = CircuitWorkflowStep.IDENTITY,
    initialVisited: Set<CircuitWorkflowStep> = setOf(CircuitWorkflowStep.IDENTITY),
    val existingCircuits: List<CircuitEntity> = emptyList(),
    val availableTransformers: List<TransformerSnapshot> = emptyList(),
    val availableGenerators: List<GeneratorSnapshot> = emptyList(),
    val availableAts: List<AtsSnapshot> = emptyList(),
    val availableUps: List<UpsSnapshot> = emptyList(),
    val catalogSnapshot: CableCatalogSnapshot = CableCatalogSnapshot(),
    projectSubtitle: String? = null,
    val editingCircuitId: Int = 0
) {
    var draft by mutableStateOf(initialDraft)
    var currentStep by mutableStateOf(initialStep)
    var visitedSteps by mutableStateOf(initialVisited)
    var identityError by mutableStateOf<String?>(null)
    var projectSubtitle by mutableStateOf(projectSubtitle)
    var dismissCount by mutableStateOf(0)
    var addTransformerCount by mutableStateOf(0)

    val applicableSteps: List<CircuitWorkflowStep>
        get() = CircuitWorkflow.getApplicableSteps(draft.panelType)

    val events = object : CircuitEditorUiEvents {
        override fun onDraftChange(newDraft: CircuitEditorDraft) {
            draft = newDraft
            currentStep = CircuitWorkflow.coerceStepOnApplicabilityChange(currentStep, newDraft.panelType)
        }

        override fun onStepChange(targetStep: CircuitWorkflowStep) {
            if (targetStep in visitedSteps) {
                currentStep = targetStep
            }
        }

        override fun onNextClick() {
            when (currentStep) {
                CircuitWorkflowStep.IDENTITY -> {
                    val validation = CircuitEditorValidator.validateIdentityStep(
                        draft = draft,
                        existingCircuits = existingCircuits,
                        editingCircuitId = editingCircuitId
                    )
                    when (validation) {
                        is ValidationResult.Error -> {
                            identityError = validation.message
                        }
                        is ValidationResult.Success -> {
                            identityError = null
                            currentStep = CircuitWorkflowStep.LOAD
                            visitedSteps = visitedSteps + CircuitWorkflowStep.LOAD
                        }
                    }
                }
                else -> {
                    val applicable = applicableSteps
                    val idx = applicable.indexOf(currentStep)
                    if (idx != -1 && idx < applicable.size - 1) {
                        val next = applicable[idx + 1]
                        currentStep = next
                        visitedSteps = visitedSteps + next
                    }
                }
            }
        }

        override fun onBackClick() {
            val applicable = applicableSteps
            val idx = applicable.indexOf(currentStep)
            if (idx > 0) {
                currentStep = applicable[idx - 1]
            } else {
                onDismiss()
            }
        }

        override fun onDismiss() {
            dismissCount++
        }

        override fun onAddTransformer() {
            addTransformerCount++
        }
    }
}

/**
 * Independent Compose test host for testing CircuitEditor composables.
 */
@Composable
fun CircuitEditorTestHost(
    stateHolder: CircuitEditorTestStateHolder,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val resolution = remember(
        stateHolder.draft.fedFrom,
        stateHolder.draft.transformerId,
        stateHolder.existingCircuits,
        stateHolder.availableTransformers
    ) {
        CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = stateHolder.draft.fedFrom,
            draftTransformerId = stateHolder.draft.transformerId,
            existingCircuits = stateHolder.existingCircuits,
            availableTransformers = stateHolder.availableTransformers
        )
    }

    val state = CircuitEditorUiState(
        draft = stateHolder.draft,
        currentStep = stateHolder.currentStep,
        visitedSteps = stateHolder.visitedSteps,
        applicableSteps = stateHolder.applicableSteps,
        isEditing = stateHolder.editingCircuitId != 0,
        editingCircuitId = stateHolder.editingCircuitId,
        projectSubtitle = stateHolder.projectSubtitle,
        existingCircuits = stateHolder.existingCircuits,
        availableTransformers = stateHolder.availableTransformers,
        sourceResolution = resolution,
        identityError = stateHolder.identityError,
        availableGenerators = stateHolder.availableGenerators,
        availableAts = stateHolder.availableAts,
        availableUps = stateHolder.availableUps,
        catalogSnapshot = stateHolder.catalogSnapshot
    )

    VoltAtlasTheme(darkTheme = darkTheme) {
        CircuitEditorParallelHost(
            state = state,
            events = stateHolder.events,
            modifier = modifier
        )
    }
}
