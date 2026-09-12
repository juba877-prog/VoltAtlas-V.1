package com.example.ui.dialogs.circuiteditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ThemeBorderStrong
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.components.ThemePrimary
import com.example.ui.components.ThemeSurface
import com.example.ui.components.ThemeSurfaceSelected
import com.example.ui.components.ThemeTextDisabled
import com.example.ui.components.ThemeTextPrimary
import com.example.ui.components.ThemeTextSecondary
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep

/**
 * Dynamic responsive progress component for the Circuit Editor.
 *
 * Implements accessible 48dp touch targets, semantic step descriptions,
 * compact node layout on narrow viewports, and visited-step jump policy.
 */
@Composable
fun CircuitEditorProgress(
    currentStep: CircuitWorkflowStep,
    applicableSteps: List<CircuitWorkflowStep>,
    visitedSteps: Set<CircuitWorkflowStep>,
    onStepSelected: (CircuitWorkflowStep) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentIndex = applicableSteps.indexOf(currentStep)
    val activeStepNumber = if (currentIndex != -1) currentIndex + 1 else 1
    val totalSteps = applicableSteps.size

    val cleanTitle = currentStep.title.replaceFirst(Regex("^\\d+\\.\\s*"), "")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_progress")
            .semantics {
                stateDescription = "Step $activeStepNumber of $totalSteps: $cleanTitle"
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Step nodes row with connectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            applicableSteps.forEachIndexed { index, step ->
                val stepNum = index + 1
                val isActive = step == currentStep
                val isVisited = step in visitedSteps
                val canClick = isVisited && !isActive

                // 48dp minimum accessible interactive container
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .then(
                            if (canClick) {
                                Modifier.clickable { onStepSelected(step) }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive) {
                        val shortTitle = when (step) {
                            CircuitWorkflowStep.IDENTITY -> "Identity"
                            CircuitWorkflowStep.LOAD -> "Load"
                            CircuitWorkflowStep.SUPPLY -> "Supply"
                            CircuitWorkflowStep.FEEDER -> "Cable"
                            CircuitWorkflowStep.INSTALLATION -> "Installation"
                            CircuitWorkflowStep.PANEL_HEADER -> "Panel"
                            CircuitWorkflowStep.REVIEW -> "Review"
                        }

                        // DEFECT C2-V05 / C2-FV04: MD3 active-pill container with visible short title
                        Row(
                            modifier = Modifier
                                .testTag("active_progress_pill")
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stepNum.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = shortTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("active_progress_pill_title")
                            )
                        }
                    } else {
                        // Compact inactive circle (24dp)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isVisited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isVisited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stepNum.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = if (isVisited) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Connector line between nodes
                if (index < applicableSteps.size - 1) {
                    val isConnectorActive = index < currentIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (isConnectorActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Active step title and descriptive subtitle
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = currentStep.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = currentStep.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
