package com.example.ui.dialogs.circuiteditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.components.ThemeBorderSubtle
import com.example.ui.components.ThemePrimary
import com.example.ui.components.ThemeSurface
import com.example.ui.components.ThemeTextDisabled
import com.example.ui.components.ThemeTextPrimary

/**
 * Bottom action bar for the Circuit Editor.
 *
 * Provides accessible Back and Next controls. No Save action is present in Stage C2.
 */
@Composable
fun CircuitEditorActionBar(
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = if (canGoBack) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .testTag("circuit_editor_back_btn")
                        .sizeIn(minWidth = 100.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Back to previous step"
                        },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text("Back")
                }
            }

            Button(
                onClick = onNextClick,
                modifier = Modifier
                    .testTag("circuit_editor_next_btn")
                    .sizeIn(minWidth = 120.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Next step"
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemePrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Next")
            }
        }
    }
}
