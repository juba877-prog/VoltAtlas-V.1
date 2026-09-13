package com.example.ui.dialogs.circuiteditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Bottom action bar for the Circuit Editor.
 *
 * Requirements:
 * - surfaceContainerHigh background with tonal elevation and no heavy border.
 * - FilledTonalButton for Back (secondary MD3 action).
 * - Button for Next (primary MD3 action).
 * - Minimum 48dp interactive height.
 * - Standard 12dp action spacing gap.
 * - Back absent on Step 1; Next end-aligned on Step 1.
 * - Back and Next share equal width on later steps.
 */
@Composable
fun CircuitEditorActionBar(
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    isLastStep: Boolean = false,
    onSaveClick: () -> Unit = onNextClick,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("circuit_editor_action_bar"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        if (canGoBack) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("circuit_editor_back_btn")
                        .semantics {
                            contentDescription = "Back to previous step"
                        },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Back")
                }

                if (isLastStep) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_circuit_btn")
                            .semantics {
                                contentDescription = "Save circuit"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Save Circuit")
                    }
                } else {
                    Button(
                        onClick = onNextClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("circuit_editor_next_btn")
                            .semantics {
                                contentDescription = "Next step"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Next")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLastStep) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_circuit_btn")
                            .semantics {
                                contentDescription = "Save circuit"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Save Circuit")
                    }
                } else {
                    Button(
                        onClick = onNextClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("circuit_editor_next_btn")
                            .semantics {
                                contentDescription = "Next step"
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}
