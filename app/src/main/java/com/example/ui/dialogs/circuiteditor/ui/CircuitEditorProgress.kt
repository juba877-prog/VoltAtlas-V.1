package com.example.ui.dialogs.circuiteditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep

/**
 * Layout modes supported by [CircuitEditorProgress].
 */
enum class ProgressLayoutMode {
    TITLED_ACTIVE_PILL,
    COMPACT_ACTIVE_PILL,
    HORIZONTAL_SCROLL_RAIL
}

/**
 * Dynamic responsive progress component for the Circuit Editor.
 *
 * Implements a constraint-driven layout with fallback priority:
 * 1. TITLED_ACTIVE_PILL: Active pill with number and short title when the full row fits.
 * 2. COMPACT_ACTIVE_PILL: Compact active numbered pill with full title in separate area when titled cannot fit.
 * 3. HORIZONTAL_SCROLL_RAIL: Scrollable rail automatically bringing active step into view when even compact cannot fit.
 *
 * Guarantees minimum 48dp interactive touch targets, unclipped nodes, and MD3 typography tokens.
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
    val shortTitle = when (currentStep) {
        CircuitWorkflowStep.IDENTITY -> "Identity"
        CircuitWorkflowStep.LOAD -> "Load"
        CircuitWorkflowStep.SUPPLY -> "Supply"
        CircuitWorkflowStep.FEEDER -> "Cable"
        CircuitWorkflowStep.INSTALLATION -> "Installation"
        CircuitWorkflowStep.PANEL_HEADER -> "Panel"
        CircuitWorkflowStep.REVIEW -> "Review"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_progress")
            .semantics {
                stateDescription = "Step $activeStepNumber of $totalSteps: $cleanTitle"
            }
            .padding(vertical = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val density = LocalDensity.current
            val fontScale = density.fontScale
            val inactiveCount = (totalSteps - 1).coerceAtLeast(1)

            // Dynamic calculation of titled pill width:
            // Base padding: 20dp (10dp on each side) + 4dp inner gap
            // Number text: ~7dp * fontScale
            // Title text: ~6.2dp per character * fontScale
            val estimatedTitleWidth = (shortTitle.length * 6.2f * fontScale).dp
            val estimatedTitledPillWidth = (24.dp + (7f * fontScale).dp + estimatedTitleWidth).coerceAtLeast(48.dp)

            // Mode 1: TITLED_ACTIVE_PILL requires:
            // - 1 active pill of estimatedTitledPillWidth
            // - inactiveCount nodes of 48dp minimum touch target
            // - inactiveCount connectors of at least 2dp each
            // - outer padding of at least 4dp each side (8dp total)
            val requiredTitledWidth = estimatedTitledPillWidth + (48.dp * inactiveCount) + (2.dp * inactiveCount) + 8.dp

            // Mode 2: COMPACT_ACTIVE_PILL requires:
            // - totalSteps nodes of 48dp minimum touch target
            // - inactiveCount connectors of at least 2dp each
            // - outer padding of at least 4dp each side (8dp total)
            val requiredCompactWidth = (48.dp * totalSteps) + (2.dp * inactiveCount) + 8.dp

            val layoutMode = when {
                availableWidth >= requiredTitledWidth -> ProgressLayoutMode.TITLED_ACTIVE_PILL
                availableWidth >= requiredCompactWidth -> ProgressLayoutMode.COMPACT_ACTIVE_PILL
                else -> ProgressLayoutMode.HORIZONTAL_SCROLL_RAIL
            }

            when (layoutMode) {
                ProgressLayoutMode.TITLED_ACTIVE_PILL -> {
                    val contentWidth = estimatedTitledPillWidth + (48.dp * inactiveCount) + (4.dp * inactiveCount)
                    val horizontalPadding = ((availableWidth - contentWidth) / 2).coerceIn(8.dp, 16.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_mode_TITLED_ACTIVE_PILL")
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RenderFixedNodes(
                            applicableSteps = applicableSteps,
                            currentStep = currentStep,
                            currentIndex = currentIndex,
                            visitedSteps = visitedSteps,
                            shortTitle = shortTitle,
                            isTitled = true,
                            onStepSelected = onStepSelected
                        )
                    }
                }

                ProgressLayoutMode.COMPACT_ACTIVE_PILL -> {
                    val contentWidth = (48.dp * totalSteps) + (2.dp * inactiveCount)
                    val horizontalPadding = ((availableWidth - contentWidth) / 2).coerceIn(4.dp, 16.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_mode_COMPACT_ACTIVE_PILL")
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RenderFixedNodes(
                            applicableSteps = applicableSteps,
                            currentStep = currentStep,
                            currentIndex = currentIndex,
                            visitedSteps = visitedSteps,
                            shortTitle = shortTitle,
                            isTitled = false,
                            onStepSelected = onStepSelected
                        )
                    }
                }

                ProgressLayoutMode.HORIZONTAL_SCROLL_RAIL -> {
                    val scrollState = rememberScrollState()

                    // Automatically bring active item fully into view
                    LaunchedEffect(currentStep, currentIndex) {
                        if (currentIndex >= 0) {
                            val itemTotalPx = with(density) { 60.dp.roundToPx() }
                            val targetScrollPx = (currentIndex * itemTotalPx) - with(density) { 16.dp.roundToPx() }
                            scrollState.animateScrollTo(targetScrollPx.coerceAtLeast(0))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_mode_HORIZONTAL_SCROLL_RAIL")
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        applicableSteps.forEachIndexed { index, step ->
                            val stepNum = index + 1
                            val isActive = step == currentStep
                            val isVisited = step in visitedSteps

                            StepNodeBox(
                                stepNum = stepNum,
                                step = step,
                                isActive = isActive,
                                isVisited = isVisited,
                                shortTitle = shortTitle,
                                isTitled = false,
                                onStepSelected = onStepSelected
                            )

                            if (index < applicableSteps.size - 1) {
                                val isConnectorActive = index < currentIndex
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(2.dp)
                                        .background(
                                            if (isConnectorActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Active step title and descriptive subtitle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
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

@Composable
private fun RowScope.RenderFixedNodes(
    applicableSteps: List<CircuitWorkflowStep>,
    currentStep: CircuitWorkflowStep,
    currentIndex: Int,
    visitedSteps: Set<CircuitWorkflowStep>,
    shortTitle: String,
    isTitled: Boolean,
    onStepSelected: (CircuitWorkflowStep) -> Unit
) {
    applicableSteps.forEachIndexed { index, step ->
        val stepNum = index + 1
        val isActive = step == currentStep
        val isVisited = step in visitedSteps

        StepNodeBox(
            stepNum = stepNum,
            step = step,
            isActive = isActive,
            isVisited = isVisited,
            shortTitle = shortTitle,
            isTitled = isTitled,
            onStepSelected = onStepSelected
        )

        if (index < applicableSteps.size - 1) {
            val isConnectorActive = index < currentIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        if (isConnectorActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

@Composable
private fun StepNodeBox(
    stepNum: Int,
    step: CircuitWorkflowStep,
    isActive: Boolean,
    isVisited: Boolean,
    shortTitle: String,
    isTitled: Boolean,
    onStepSelected: (CircuitWorkflowStep) -> Unit
) {
    val canClick = isVisited && !isActive

    Box(
        modifier = Modifier
            .testTag("progress_step_node_$stepNum")
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (canClick) Modifier.clickable { onStepSelected(step) } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            if (isTitled) {
                // Titled active pill (number + short title)
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
                // Compact active numbered pill (number only)
                Box(
                    modifier = Modifier
                        .testTag("active_progress_pill")
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNum.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            // Inactive node (24dp circle)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isVisited) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .border(
                        width = 1.dp,
                        color = if (isVisited) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNum.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
                    ),
                    color = if (isVisited) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
