package com.example.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.EngineeringStatusUiModel
import com.example.ai.ui.SuggestedActionUiModel
import com.example.ui.theme.EngineeringStatusPalette

/**
 * MD3 Result Summary Card rendering authoritative VoltAtlas sizing metrics.
 */
@Composable
fun AssistantResultCard(
    card: AssistantCardUiModel.ResultSummaryCard,
    onActionClick: (SuggestedActionUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("assistant_result_card_${card.cardId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (card.isStale) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                val badgeColor = when (card.status) {
                    EngineeringStatusUiModel.PASS -> MaterialTheme.colorScheme.primaryContainer
                    EngineeringStatusUiModel.FAIL -> MaterialTheme.colorScheme.errorContainer
                    EngineeringStatusUiModel.WARNING -> EngineeringStatusPalette.ScenarioContainer
                    EngineeringStatusUiModel.STALE -> MaterialTheme.colorScheme.surfaceVariant
                    EngineeringStatusUiModel.INFO -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val badgeTextColor = when (card.status) {
                    EngineeringStatusUiModel.PASS -> MaterialTheme.colorScheme.onPrimaryContainer
                    EngineeringStatusUiModel.FAIL -> MaterialTheme.colorScheme.onErrorContainer
                    EngineeringStatusUiModel.WARNING -> EngineeringStatusPalette.OnScenarioContainer
                    EngineeringStatusUiModel.STALE -> MaterialTheme.colorScheme.onSurfaceVariant
                    EngineeringStatusUiModel.INFO -> MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Status: ${card.status.label}" }
                        .testTag("status_badge_${card.status.name}")
                ) {
                    Text(
                        text = card.status.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeTextColor
                    )
                }
            }

            // Summary text
            Text(
                text = card.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Governing Criterion
            Text(
                text = "Governing Check: ${card.governingCriterion}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            // Metrics Table
            if (card.metrics.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    card.metrics.forEach { metric ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = metric.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val displayValue = if (metric.unit != null) "${metric.value} ${metric.unit}" else metric.value
                            val limitText = if (metric.limit != null) " (Limit: ${metric.limit})" else ""
                            Text(
                                text = "$displayValue$limitText",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Suggested actions
            SuggestedActionsRow(
                actions = card.suggestedActions,
                onActionClick = onActionClick
            )
        }
    }
}
