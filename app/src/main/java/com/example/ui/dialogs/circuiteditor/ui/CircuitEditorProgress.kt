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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
 * Implements a constraint-driven layout strategy:
 * 1. Compact widths (< 600dp): Renders numbered nodes only (or scrollable rail on narrow viewports).
 *    The single content heading in the content region communicates the active step title and helper text,
 *    eliminating duplicate active title presentations.
 * 2. Expanded widths (>= 600dp): Renders titled active pill with step number and title when ample width exists.
 * 3. Horizontal scroll rail: Automatically brings the active step fully into view upon composition or navigation.
 *
 * Guarantees:
 * - Minimum 48dp interactive touch targets on all nodes.
 * - Complete accessibility semantics (Current, Completed, Upcoming states + disabled forward state).
 * - Zero character-count heuristic formulas.
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
            val inactiveCount = (totalSteps - 1).coerceAtLeast(1)

            // Deterministic constraint calculation based on 48dp touch targets and connector spacing:
            val requiredCompactWidth = (48.dp * totalSteps) + (2.dp * inactiveCount) + 8.dp

            // Deterministic width-class strategy:
            // - On expanded widths (>= 600dp): Render titled pill.
            // - On compact widths (< 600dp):
            //     - If all numbered nodes fit: COMPACT_ACTIVE_PILL.
            //     - Else: HORIZONTAL_SCROLL_RAIL.
            val layoutMode = when {
                availableWidth >= 600.dp -> ProgressLayoutMode.TITLED_ACTIVE_PILL
                availableWidth >= requiredCompactWidth -> ProgressLayoutMode.COMPACT_ACTIVE_PILL
                else -> ProgressLayoutMode.HORIZONTAL_SCROLL_RAIL
            }

            when (layoutMode) {
                ProgressLayoutMode.TITLED_ACTIVE_PILL -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_mode_TITLED_ACTIVE_PILL")
                            .padding(horizontal = 16.dp),
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

                    // Automatically bring active item fully into view on composition or step change
                    LaunchedEffect(currentStep, currentIndex) {
                        if (currentIndex >= 0) {
                            val itemPx = with(density) { 60.dp.roundToPx() }
                            val paddingPx = with(density) { 16.dp.roundToPx() }
                            val viewportPx = with(density) { availableWidth.roundToPx() }
                            val nodeLeftPx = paddingPx + (currentIndex * itemPx)
                            val nodeRightPx = nodeLeftPx + with(density) { 48.dp.roundToPx() }

                            val currentScroll = scrollState.value
                            val targetScroll = when {
                                nodeLeftPx < currentScroll + paddingPx -> {
                                    (nodeLeftPx - paddingPx).coerceAtLeast(0)
                                }
                                nodeRightPx > currentScroll + viewportPx - paddingPx -> {
                                    (nodeRightPx - viewportPx + paddingPx).coerceAtMost(scrollState.maxValue)
                                }
                                else -> currentScroll
                            }
                            if (targetScroll != currentScroll) {
                                scrollState.scrollTo(targetScroll)
                            }
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
    val cleanTitle = step.title.replaceFirst(Regex("^\\d+\\.\\s*"), "")

    Box(
        modifier = Modifier
            .testTag("progress_step_node_$stepNum")
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = "Step $stepNum: $cleanTitle"
                stateDescription = when {
                    isActive -> "Current"
                    isVisited -> "Completed"
                    else -> "Upcoming"
                }
                if (!canClick && !isActive) {
                    disabled()
                }
            }
            .then(
                if (canClick) {
                    Modifier.clickable(
                        onClickLabel = "Navigate to Step $stepNum"
                    ) { onStepSelected(step) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            if (isTitled) {
                // Titled active pill (number + short title on expanded viewports)
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
            // Inactive node (24dp circle inside 48dp minimum touch container)
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
