package com.example.ai.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.service.AssistantMessage
import com.example.ai.service.MessageRole
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EngineeringAssistantSheet(
    viewModel: EngineeringAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val reduceMotion = LocalReduceMotion.current

    if (!uiState.isOpen) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.close() },
        sheetState = sheetState,
        containerColor = ThemeSurface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier.testTag("engineering_assistant_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (uiState.isExpanded) 0.95f else 0.80f)
        ) {
            // --- TOP HEADER ROW ---
            Surface(
                color = ThemeSurface,
                border = BorderStroke(1.dp, ThemeBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(SleekPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Assistant Icon",
                                tint = SleekPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VoltAtlas Assistant",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                color = ThemeTextPrimary
                            )
                            val subtitle = when {
                                uiState.context.activeEntityName != null -> "Context: ${uiState.context.activeEntityName}"
                                uiState.context.activeEntityType != null -> "Context: ${uiState.context.activeEntityType}"
                                else -> uiState.context.projectName
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = ThemeTextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleExpand() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (uiState.isExpanded) "Collapse" else "Expand",
                                tint = ThemeTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.close() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Assistant",
                                tint = ThemeTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // --- CONVERSATION STREAM / MESSAGES ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ThemeBackground)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        AssistantMessageBubbleItem(message = message)
                    }

                    if (uiState.isThinking) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ThemePrimary
                                )
                                Text(
                                    text = "Analyzing engineering inputs...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ThemeTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // --- INPUT COMPOSER BAR ---
            Surface(
                color = ThemeSurface,
                border = BorderStroke(1.dp, ThemeBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.setInputText(it) },
                        placeholder = {
                            Text(
                                text = "Ask about sizing, load, or voltage drop...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = ThemeTextSecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemePrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedTextColor = ThemeTextPrimary,
                            unfocusedTextColor = ThemeTextPrimary,
                            cursorColor = ThemePrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 100.dp)
                            .testTag("assistant_input_field")
                    )

                    IconButton(
                        onClick = {
                            viewModel.sendMessage(uiState.inputText)
                        },
                        enabled = uiState.inputText.isNotBlank() && !uiState.isThinking,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (uiState.inputText.isNotBlank()) ThemePrimary else ThemeBorderSubtle,
                                shape = CircleShape
                            )
                            .testTag("assistant_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (uiState.inputText.isNotBlank()) Color.White else ThemeTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBubbleItem(message: AssistantMessage) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val isDark = LocalThemeIsDark.current
    val bubbleColor = if (isUser) {
        ThemePrimary.copy(alpha = if (isDark) 0.15f else 0.10f)
    } else {
        ThemeSurface
    }
    val borderColor = if (isUser) {
        ThemePrimary.copy(alpha = if (isDark) 0.35f else 0.25f)
    } else {
        ThemeBorderSubtle
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleColor)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = ThemeTextPrimary
            )
        }
    }
}
