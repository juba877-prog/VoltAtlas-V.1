package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CircuitEntity
import com.example.ui.components.ThemeBackgroundDeep
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.components.ThemeTextPrimary
import com.example.ui.components.ThemeTextSecondary
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolver
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorPreferencesSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot
import com.example.ui.dialogs.circuiteditor.state.CircuitEditorInitializer
import com.example.ui.dialogs.circuiteditor.ui.steps.FeederStepContent
import com.example.ui.dialogs.circuiteditor.ui.steps.IdentityStepContent
import com.example.ui.dialogs.circuiteditor.ui.steps.LoadStepContent
import com.example.ui.dialogs.circuiteditor.ui.steps.SupplyStepContent
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
    availableGenerators: List<GeneratorSnapshot> = emptyList(),
    availableAts: List<AtsSnapshot> = emptyList(),
    availableUps: List<UpsSnapshot> = emptyList(),
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
        identityError = identityError,
        availableGenerators = availableGenerators,
        availableAts = availableAts,
        availableUps = availableUps,
        catalogSnapshot = catalogSnapshot
    )

    CircuitEditorParallelHost(
        state = state,
        events = events,
        modifier = modifier
    )
}

/**
 * Parallel host component capable of rendering Stage C1 through C3 steps.
 */
@Composable
fun CircuitEditorParallelHost(
    state: CircuitEditorUiState,
    events: CircuitEditorUiEvents,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    BackHandler(enabled = true) {
        events.onBackClick()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("circuit_editor_root"),
        color = ThemeBackgroundDeep
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                // Sticky Top Bar + Stepper Region
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, ThemeBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        // Dialog Header: Title & Close Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("circuit_editor_header"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (state.isEditing) "Edit Circuit" else "Add New Circuit",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ThemeTextPrimary
                                )
                                state.projectSubtitle?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ThemeTextSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { events.onDismiss() },
                                modifier = Modifier
                                    .testTag("circuit_editor_close_btn")
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = "Close editor"
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close editor",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Dynamic Workflow Stepper / Progress
                        CircuitEditorProgress(
                            currentStep = state.currentStep,
                            applicableSteps = state.applicableSteps,
                            visitedSteps = state.visitedSteps,
                            onStepSelected = events::onStepChange
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        // Single Content Heading communicating position, full step title, and helper text
                        val stepTitle = if (state.currentStep == CircuitWorkflowStep.FEEDER) "4. Feeder Selection" else state.currentStep.title
                        val stepSubtitle = if (state.currentStep == CircuitWorkflowStep.FEEDER) "Select cable or busway family and configuration" else state.currentStep.subtitle

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("circuit_editor_step_header")
                                .padding(bottom = 16.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "$stepTitle, $stepSubtitle"
                                }
                        ) {
                            Text(
                                text = stepTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stepSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        when (state.currentStep) {
                            CircuitWorkflowStep.IDENTITY -> {
                                IdentityStepContent(
                                    draft = state.draft,
                                    onDraftChange = events::onDraftChange,
                                    existingCircuits = state.existingCircuits,
                                    editingCircuitId = state.editingCircuitId,
                                    availableTransformers = state.availableTransformers,
                                    resolution = state.sourceResolution,
                                    identityError = state.identityError,
                                    onAddTransformer = events::onAddTransformer
                                )
                            }

                            CircuitWorkflowStep.LOAD -> {
                                LoadStepContent(
                                    draft = state.draft,
                                    onDraftChange = events::onDraftChange
                                )
                            }

                            CircuitWorkflowStep.SUPPLY -> {
                                SupplyStepContent(
                                    draft = state.draft,
                                    onDraftChange = events::onDraftChange,
                                    availableGenerators = state.availableGenerators,
                                    availableAts = state.availableAts,
                                    availableUps = state.availableUps,
                                    sourceResolution = state.sourceResolution
                                )
                            }

                            CircuitWorkflowStep.FEEDER -> {
                                FeederStepContent(
                                    draft = state.draft,
                                    onDraftChange = events::onDraftChange,
                                    catalogSnapshot = state.catalogSnapshot
                                )
                            }

                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                        .testTag("circuit_editor_step_placeholder"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val placeholderTitle = if (state.currentStep == CircuitWorkflowStep.FEEDER) "4. Feeder Selection" else state.currentStep.title
                                    Text(
                                        text = "Step '$placeholderTitle' is scheduled for subsequent stage.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Bottom clearance element: guarantees performScrollTo() reaches bottom
                        Spacer(
                            modifier = Modifier
                                .height(16.dp)
                                .testTag("content_bottom_clearance_spacer")
                        )
                    }
                }

                // Sticky Bottom Action Bar with layout-owned measured region
                val canGoBack = state.currentStep != state.applicableSteps.first()
                CircuitEditorActionBar(
                    canGoBack = canGoBack,
                    onBackClick = events::onBackClick,
                    onNextClick = events::onNextClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    }
}
