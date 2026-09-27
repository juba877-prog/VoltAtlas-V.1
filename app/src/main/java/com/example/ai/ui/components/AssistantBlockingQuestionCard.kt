package com.example.ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.SuggestedActionUiModel

/**
 * MD3 Blocking Question Card presenting narrow, required safety-critical input queries.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantBlockingQuestionCard(
    card: AssistantCardUiModel.BlockingQuestionCard,
    onChipClick: (String) -> Unit,
    onActionClick: (SuggestedActionUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("assistant_blocking_question_card_${card.cardId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Information Required",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = card.questionText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Why this is required: ${card.requiredReason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (card.suggestedChips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    card.suggestedChips.forEach { chipText ->
                        SuggestionChip(
                            onClick = { onChipClick(chipText) },
                            label = { Text(chipText) },
                            modifier = Modifier.testTag("suggestion_chip_$chipText")
                        )
                    }
                }
            }
        }
    }
}
