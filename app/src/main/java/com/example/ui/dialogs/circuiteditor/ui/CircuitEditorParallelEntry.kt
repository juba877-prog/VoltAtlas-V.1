package com.example.ui.dialogs.circuiteditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorPreferencesSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.state.CircuitEditorInitializer
import com.example.ui.dialogs.circuiteditor.validation.CircuitEditorValidator
import com.example.ui.dialogs.circuiteditor.validation.ValidationResult

/**
 * Parallel UI entry point for the Circuit Editor.
 *
 * Exists independently of legacy production call sites to enable isolated testing,
 * characterization, and visual verification before Stage C3 integration.
 */
@Composable
fun CircuitEditorParallelEntry(
    circuit: CircuitEntity?,
    existingCircuits: List<CircuitEntity>,
    preferences: CircuitEditorPreferencesSnapshot = CircuitEditorPreferencesSnapshot(),
    availableTransformers: List<TransformerSnapshot> = emptyList(),
    catalogSnapshot: CableCatalogSnapshot = CableCatalogSnapshot(),
    onDismiss: () -> Unit,
    onAddTransformer: () -> Unit = {},
    projectSubtitle: String? = null,
    modifier: Modifier = Modifier
) {
    var draft by remember {
        mutableStateOf(
            CircuitEditorInitializer.initialize(
                original = circuit,
                preferences = preferences,
                availableTransformers = availableTransformers,
                catalogSnapshot = catalogSnapshot
            )
        )
    }

    var currentStep by remember { mutableStateOf(CircuitWorkflowStep.IDENTITY) }
    var visitedSteps by remember { mutableStateOf(setOf(CircuitWorkflowStep.IDENTITY)) }
    var identityError by remember { mutableStateOf<String?>(null) }

    val applicableSteps = remember(draft.panelType) {
        CircuitWorkflow.getApplicableSteps(draft.panelType)
    }

    val sourceResolution = remember(draft.fedFrom, draft.transformerId, existingCircuits, availableTransformers) {
        CircuitSourceResolver.resolveSourceHierarchy(
            fedFrom = draft.fedFrom,
            draftTransformerId = draft.transformerId,
            existingCircuits = existingCircuits,
            availableTransformers = availableTransformers
        )
    }

    val events = remember(draft, currentStep, visitedSteps, applicableSteps, circuit, existingCircuits) {
        object : CircuitEditorUiEvents {
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
                            editingCircuitId = circuit?.id ?: 0
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
                    CircuitWorkflowStep.LOAD -> {
                        val idx = applicableSteps.indexOf(currentStep)
                        if (idx != -1 && idx < applicableSteps.size - 1) {
                            val next = applicableSteps[idx + 1]
                            currentStep = next
                            visitedSteps = visitedSteps + next
                        }
                    }
                    else -> {
                        val idx = applicableSteps.indexOf(currentStep)
                        if (idx != -1 && idx < applicableSteps.size - 1) {
                            val next = applicableSteps[idx + 1]
                            currentStep = next
                            visitedSteps = visitedSteps + next
                        }
                    }
                }
            }

            override fun onBackClick() {
                val idx = applicableSteps.indexOf(currentStep)
                if (idx > 0) {
                    currentStep = applicableSteps[idx - 1]
                } else {
                    onDismiss()
                }
            }

            override fun onDismiss() {
                onDismiss()
            }

            override fun onAddTransformer() {
                onAddTransformer()
            }
        }
    }

    val state = CircuitEditorUiState(
        draft = draft,
        currentStep = currentStep,
        visitedSteps = visitedSteps,
        applicableSteps = applicableSteps,
        isEditing = circuit != null,
        editingCircuitId = circuit?.id ?: 0,
        projectSubtitle = projectSubtitle,
        existingCircuits = existingCircuits,
        availableTransformers = availableTransformers,
        sourceResolution = sourceResolution,
        identityError = identityError
    )

    CircuitEditorShell(
        state = state,
        events = events,
        modifier = modifier
    )
}
