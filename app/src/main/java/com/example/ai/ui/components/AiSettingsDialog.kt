package com.example.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ai.hybrid.AiProvider
import com.example.ai.hybrid.AiSettingsState
import com.example.ai.hybrid.ConnectionTestStatus

/**
 * Production UI dialog for user Bring-Your-Own-Key (BYOK) Gemini API configuration.
 *
 * Invariants:
 * - Credentials are masked and never shown in plaintext once saved.
 * - Test Connection validates the personal key against gemini-3.8-flash without exposing the secret.
 * - Supports switching between Direct Gemini, Firebase AI Logic, and Local Only.
 */
@Composable
fun AiSettingsDialog(
    state: AiSettingsState,
    onDismiss: () -> Unit,
    onSaveCredential: (String) -> Unit,
    onRemoveCredential: () -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onTestConnection: () -> Unit
) {
    if (!state.isOpen) return

    var keyInputText by remember { mutableStateOf("") }
    var isEditingCredential by remember { mutableStateOf(!state.hasCredential) }

    LaunchedEffect(state.hasCredential) {
        if (!state.hasCredential) {
            isEditingCredential = true
            keyInputText = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("ai_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Settings Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "AI Provider & Key",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_ai_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Provider Mode Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AI Provider",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedProvider == AiProvider.DIRECT_GEMINI,
                            onClick = { onSelectProvider(AiProvider.DIRECT_GEMINI) },
                            label = { Text("Direct Gemini (BYOK)", fontSize = 12.sp) },
                            leadingIcon = if (state.selectedProvider == AiProvider.DIRECT_GEMINI) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("provider_direct_gemini_chip")
                        )
                        FilterChip(
                            selected = state.selectedProvider == AiProvider.LOCAL_ONLY,
                            onClick = { onSelectProvider(AiProvider.LOCAL_ONLY) },
                            label = { Text("Local (Offline)", fontSize = 12.sp) },
                            leadingIcon = if (state.selectedProvider == AiProvider.LOCAL_ONLY) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("provider_local_chip")
                        )
                    }
                }

                // Direct Gemini Configuration Section
                if (state.selectedProvider == AiProvider.DIRECT_GEMINI) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Status Banner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gemini API",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                ConnectionStatusBadge(state.connectionStatus, state.hasCredential)
                            }

                            // Model Info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Model: ${state.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Credential Display or Input
                            if (state.hasCredential && !isEditingCredential) {
                                // Masked credential display
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Credential",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = state.maskedCredential,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .testTag("masked_credential_text")
                                                        .semantics { contentDescription = "Configured Gemini Credential" }
                                                )
                                            }
                                            Text(
                                                text = "Encrypted",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Status message if present
                                state.statusMessage?.let { msg ->
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = when (state.connectionStatus) {
                                            ConnectionTestStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                            ConnectionTestStatus.INVALID_CREDENTIAL,
                                            ConnectionTestStatus.NETWORK_ERROR -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.testTag("connection_test_message")
                                    )
                                }

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = onTestConnection,
                                        enabled = !state.isTestingConnection,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("test_connection_button")
                                    ) {
                                        if (state.isTestingConnection) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Testing...", fontSize = 11.sp, maxLines = 1)
                                        } else {
                                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Test Connection", fontSize = 11.sp, maxLines = 1)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { isEditingCredential = true },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("update_credential_button")
                                    ) {
                                        Text("Update", fontSize = 12.sp)
                                    }

                                    IconButton(
                                        onClick = onRemoveCredential,
                                        modifier = Modifier.size(36.dp).testTag("remove_credential_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Credential",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                // Credential input form
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (state.hasCredential) "Update Gemini API Key" else "Add your Gemini API Key to enable AI assistance. Your key is stored encrypted on this device and transmitted directly to Google.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = keyInputText,
                                        onValueChange = { keyInputText = it },
                                        label = { Text("Gemini API Key") },
                                        placeholder = { Text("AIzaSy...") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (keyInputText.isNotBlank()) {
                                                onSaveCredential(keyInputText)
                                                isEditingCredential = false
                                            }
                                        }),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("gemini_key_input_field")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (state.hasCredential) {
                                            TextButton(onClick = { isEditingCredential = false }) {
                                                Text("Cancel")
                                            }
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Button(
                                            onClick = {
                                                if (keyInputText.isNotBlank()) {
                                                    onSaveCredential(keyInputText)
                                                    isEditingCredential = false
                                                }
                                            },
                                            enabled = keyInputText.isNotBlank(),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("save_credential_button")
                                        ) {
                                            Text("Save & Connect")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Local Only Info
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Offline Engineering Mode", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Text(
                                text = "All calculations and diagnostic evaluations run locally on-device. Zero network requests are made.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Privacy Note
                Text(
                    text = "Privacy: Your API key is encrypted using Android Keystore and never sent to VoltAtlas servers.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusBadge(status: ConnectionTestStatus, hasCredential: Boolean) {
    val (text, bgColor, textColor) = when {
        !hasCredential -> Triple("Not Configured", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        status == ConnectionTestStatus.CONNECTED -> Triple("● Connected", Color(0xFF1B5E20), Color(0xFFC8E6C9))
        status == ConnectionTestStatus.INVALID_CREDENTIAL -> Triple("● Credential rejected", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        status == ConnectionTestStatus.NETWORK_ERROR -> Triple("● Network unavailable", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        status == ConnectionTestStatus.RATE_LIMITED -> Triple("● Quota reached", Color(0xFFE65100), Color(0xFFFFE0B2))
        status == ConnectionTestStatus.TESTING -> Triple("● Testing...", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Triple("Configured", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("connection_status_badge")
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = textColor
        )
    }
}
