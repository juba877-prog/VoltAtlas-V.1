package com.example.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.ui.SuggestedActionUiModel

/**
 * MD3 suggested actions row displaying at most 3 non-executing UI action chips/buttons.
 */
@Composable
fun SuggestedActionsRow(
    actions: List<SuggestedActionUiModel>,
    onActionClick: (SuggestedActionUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return

    val displayActions = actions.take(3)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("suggested_actions_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        displayActions.forEach { action ->
            if (action.isPrimary) {
                Button(
                    onClick = { onActionClick(action) },
                    modifier = Modifier.testTag("action_primary_${action.actionType.name}")
                ) {
                    Text(action.label)
                }
            } else if (action == displayActions.last() && displayActions.size > 1) {
                OutlinedButton(
                    onClick = { onActionClick(action) },
                    modifier = Modifier.testTag("action_tertiary_${action.actionType.name}")
                ) {
                    Text(action.label)
                }
            } else {
                FilledTonalButton(
                    onClick = { onActionClick(action) },
                    modifier = Modifier.testTag("action_secondary_${action.actionType.name}")
                ) {
                    Text(action.label)
                }
            }
        }
    }
}
