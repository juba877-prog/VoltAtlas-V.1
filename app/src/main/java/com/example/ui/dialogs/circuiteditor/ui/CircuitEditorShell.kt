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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.ThemeBackgroundDeep
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.components.ThemeTextPrimary
import com.example.ui.components.ThemeTextSecondary
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.ui.steps.IdentityStepContent
import com.example.ui.dialogs.circuiteditor.ui.steps.LoadStepContent

/**
 * Responsive Material 3 shell for the VoltAtlas Circuit Editor.
 *
 * Implements sticky header/progress, scrollable content with IME padding,
 * sticky bottom action bar, accessible BackHandler, and responsive width constraints.
 */
@Composable
fun CircuitEditorShell(
    state: CircuitEditorUiState,
    events: CircuitEditorUiEvents,
    modifier: Modifier = Modifier
) {
    // Intercept hardware / system Back gesture
    BackHandler(enabled = true) {
        if (state.currentStep == state.applicableSteps.first()) {
            events.onDismiss()
        } else {
            events.onBackClick()
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier
            .testTag("circuit_editor_root")
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("circuit_editor_header"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isEditing) "Edit Circuit" else "New Circuit",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!state.projectSubtitle.isNullOrBlank()) {
                                Text(
                                    text = state.projectSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        },
        bottomBar = {
            // Sticky Bottom Action Bar (Back / Next) with layout-owned measured region
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
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

                    else -> {
                        // Non-implemented workflow steps in Stage C2 render clean placeholder for navigation tests
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .testTag("circuit_editor_step_placeholder"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Step '${state.currentStep.title}' is scheduled for subsequent stage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal dialog wrapper for [CircuitEditorShell].
 */
@Composable
fun CircuitEditorModal(
    state: CircuitEditorUiState,
    events: CircuitEditorUiEvents,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = events::onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CircuitEditorShell(
            state = state,
            events = events,
            modifier = modifier
        )
    }
}
