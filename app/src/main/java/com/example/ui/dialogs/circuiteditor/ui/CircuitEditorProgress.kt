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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import kotlin.math.roundToInt

/**
 * Design tokens governing progress rail sizing and accessibility touch targets.
 */
object CircuitEditorProgressTokens {
    val VisibleCircleDiameter = 24.dp
    val ActivePillWidth = 48.dp
    val ActivePillHeight = 28.dp
    val MinimumInteractiveSize = 48.dp
    val AllocatedItemWidth = 48.dp
    val ConnectorWidth = 12.dp
    val InterItemSpacing = 4.dp
    val RailDefaultHorizontalPadding = 16.dp
}

/**
 * Layout modes supported by [CircuitEditorProgress].
 */
enum class ProgressLayoutMode {
    COMPACT_ACTIVE_PILL,
    HORIZONTAL_SCROLL_RAIL
}

/**
 * Dynamic responsive progress component for the Circuit Editor.
 *
 * Requirements:
 * 1. Communicates workflow position with numbered nodes only across all compact phone layouts.
 * 2. Active node renders a clear stadium/pill visual shape (48x28 dp) distinct from inactive 24dp circles.
 * 3. Inactive steps render 24dp compact circles inside minimum 48dp interactive bounds.
 * 4. Content area displays the full step title and helper text exactly once.
 * 5. Horizontal scroll rail uses symmetric content padding to mathematically center the active node.
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
            .padding(vertical = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val density = LocalDensity.current

            val inactiveCount = (totalSteps - 1).coerceAtLeast(1)
            // Minimum width required to display all steps as fixed without scrolling:
            // totalSteps items (48dp) + minimum connectors (2dp each) + 8dp edge margin
            val requiredCompactWidth = (CircuitEditorProgressTokens.AllocatedItemWidth * totalSteps) +
                    (2.dp * inactiveCount) + 8.dp

            val layoutMode = if (availableWidth >= requiredCompactWidth) {
                ProgressLayoutMode.COMPACT_ACTIVE_PILL
            } else {
                ProgressLayoutMode.HORIZONTAL_SCROLL_RAIL
            }

            when (layoutMode) {
                ProgressLayoutMode.COMPACT_ACTIVE_PILL -> {
                    val contentWidth = (CircuitEditorProgressTokens.AllocatedItemWidth * totalSteps) +
                            (2.dp * inactiveCount)
                    val horizontalPadding = ((availableWidth - contentWidth) / 2)
                        .coerceIn(4.dp, CircuitEditorProgressTokens.RailDefaultHorizontalPadding)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_mode_COMPACT_ACTIVE_PILL")
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                }

                ProgressLayoutMode.HORIZONTAL_SCROLL_RAIL -> {
                    val scrollState = rememberScrollState()
                    val layoutDirection = LocalLayoutDirection.current

                    // Pitch per step: 48dp item container + 12dp connector = 60dp
                    val itemPitchDp = CircuitEditorProgressTokens.AllocatedItemWidth + CircuitEditorProgressTokens.ConnectorWidth
                    val itemPitchPx = with(density) { itemPitchDp.roundToPx() }

                    // Symmetric leading and trailing padding to center any active node in the viewport
                    val symmetricPaddingDp = (availableWidth - CircuitEditorProgressTokens.AllocatedItemWidth) / 2

                    // Automatically center active node on composition and step change
                    LaunchedEffect(currentStep, currentIndex, availableWidth, layoutDirection) {
                        if (currentIndex >= 0) {
                            val targetScroll = (currentIndex * itemPitchPx).coerceIn(0, scrollState.maxValue)
                            scrollState.scrollTo(targetScroll)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_mode_HORIZONTAL_SCROLL_RAIL")
                            .horizontalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading symmetric padding spacer
                        Spacer(modifier = Modifier.width(symmetricPaddingDp))

                        applicableSteps.forEachIndexed { index, step ->
                            val stepNum = index + 1
                            val isActive = step == currentStep
                            val isVisited = step in visitedSteps

                            StepNodeBox(
                                stepNum = stepNum,
                                step = step,
                                isActive = isActive,
                                isVisited = isVisited,
                                onStepSelected = onStepSelected
                            )

                            if (index < applicableSteps.size - 1) {
                                val isConnectorActive = index < currentIndex
                                Box(
                                    modifier = Modifier
                                        .width(CircuitEditorProgressTokens.ConnectorWidth)
                                        .height(2.dp)
                                        .background(
                                            if (isConnectorActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                )
                            }
                        }

                        // Trailing symmetric padding spacer
                        Spacer(modifier = Modifier.width(symmetricPaddingDp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNodeBox(
    stepNum: Int,
    step: CircuitWorkflowStep,
    isActive: Boolean,
    isVisited: Boolean,
    onStepSelected: (CircuitWorkflowStep) -> Unit
) {
    val canClick = isVisited && !isActive
    val cleanTitle = step.title.replaceFirst(Regex("^\\d+\\.\\s*"), "")

    Box(
        modifier = Modifier
            .testTag("progress_step_node_$stepNum")
            .sizeIn(
                minWidth = CircuitEditorProgressTokens.MinimumInteractiveSize,
                minHeight = CircuitEditorProgressTokens.MinimumInteractiveSize
            )
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
            // Stadium pill active indicator: 48dp width x 28dp height
            Box(
                modifier = Modifier
                    .testTag("active_progress_pill")
                    .size(
                        width = CircuitEditorProgressTokens.ActivePillWidth,
                        height = CircuitEditorProgressTokens.ActivePillHeight
                    )
                    .clip(RoundedCornerShape(CircuitEditorProgressTokens.ActivePillHeight / 2))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNum.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        } else {
            // Inactive node: 24dp diameter circle
            Box(
                modifier = Modifier
                    .size(CircuitEditorProgressTokens.VisibleCircleDiameter)
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
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isVisited) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
