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
import androidx.compose.foundation.shape.CircleShape
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_progress")
            .semantics {
                stateDescription = "Step $activeStepNumber of $totalSteps"
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
                    // Visual step circle (28dp)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> ThemePrimary
                                    isVisited -> ThemeSurfaceSelected
                                    else -> ThemeSurface
                                }
                            )
                            .border(
                                width = if (isActive) 2.dp else 1.dp,
                                color = when {
                                    isActive -> ThemePrimary
                                    isVisited -> ThemeBorderStrong
                                    else -> ThemeBorderSubtle
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNum.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            ),
                            color = when {
                                isActive -> MaterialTheme.colorScheme.onPrimary
                                isVisited -> ThemeTextPrimary
                                else -> ThemeTextDisabled
                            }
                        )
                    }
                }

                // Connector line between nodes
                if (index < applicableSteps.size - 1) {
                    val isConnectorActive = index < currentIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (isConnectorActive) ThemePrimary else ThemeBorderSubtle)
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
                color = ThemeTextPrimary
            )
            Text(
                text = currentStep.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ThemeTextSecondary
            )
        }
    }
}
