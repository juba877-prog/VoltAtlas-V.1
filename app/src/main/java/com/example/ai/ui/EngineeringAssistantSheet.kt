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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.service.AssistantMessage
import com.example.ai.service.MessageRole
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.components.*
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

    val totalItemsCount = uiState.messages.size + uiState.typedCards.size
    LaunchedEffect(totalItemsCount) {
        if (totalItemsCount > 0) {
            listState.animateScrollToItem(totalItemsCount - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.close() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                color = MaterialTheme.colorScheme.surfaceContainer,
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "VoltAtlas Assistant",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Entity Pill Badge
                                val entityName = uiState.context.activeEntityName ?: uiState.context.activeEntityId
                                if (entityName != null && uiState.context.activeEntityType != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .semantics { contentDescription = "Context entity: ${uiState.context.activeEntityType} $entityName" }
                                            .testTag("context_entity_badge")
                                    ) {
                                        Text(
                                            text = "${uiState.context.activeEntityType?.uppercase()} · $entityName",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            val subtitle = when {
                                uiState.context.projectName.isNotBlank() -> uiState.context.projectName
                                else -> "Electrical Design Assistant"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // --- CONVERSATION STREAM / MESSAGES / TYPED CARDS ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Render Typed Cards
                    items(uiState.typedCards, key = { it.cardId }) { card ->
                        when (card) {
                            is AssistantCardUiModel.ResultSummaryCard -> {
                                AssistantResultCard(
                                    card = card,
                                    onActionClick = { /* Non-executing UI action in 7F-3 */ }
                                )
                            }
                            is AssistantCardUiModel.BlockingQuestionCard -> {
                                AssistantBlockingQuestionCard(
                                    card = card,
                                    onChipClick = { chipText -> viewModel.setInputText(chipText) },
                                    onActionClick = { /* Non-executing UI action */ }
                                )
                            }
                            is AssistantCardUiModel.WhatIfResultCard -> {
                                AssistantWhatIfCard(
                                    card = card,
                                    onActionClick = { /* Non-executing UI action */ }
                                )
                            }
                            is AssistantCardUiModel.SafeErrorCard -> {
                                AssistantSafeErrorCard(
                                    card = card,
                                    onActionClick = { /* Non-executing UI action */ }
                                )
                            }
                            is AssistantCardUiModel.DirectAnswerCard -> {
                                AssistantDirectAnswerCard(
                                    card = card,
                                    onActionClick = { /* Non-executing UI action */ }
                                )
                            }
                            is AssistantCardUiModel.ComparisonCard -> {
                                AssistantComparisonCard(
                                    card = card,
                                    onActionClick = { /* Non-executing UI action */ }
                                )
                            }
                            is AssistantCardUiModel.ApprovalRequiredNonExecutableCard -> {
                                AssistantApprovalRequiredCard(
                                    card = card,
                                    onActionClick = { /* Non-executing UI action */ }
                                )
                            }
                            is AssistantCardUiModel.ProposalReviewCard -> {
                                AssistantProposalReviewCard(
                                    model = card.model,
                                    onActionClick = { /* Action handling */ }
                                )
                            }
                            is AssistantCardUiModel.ApprovalConfirmationCard -> {
                                AssistantApprovalConfirmationCard(
                                    reviewModel = card.model,
                                    isSubmitting = card.isSubmitting,
                                    onConfirmApprove = { /* Trusted confirmation */ },
                                    onCancel = { /* Cancel confirmation */ }
                                )
                            }
                            is AssistantCardUiModel.MutationReceiptCard -> {
                                AssistantMutationReceiptCard(
                                    receiptId = card.receiptId,
                                    revisionBefore = card.revisionBefore,
                                    revisionAfter = card.revisionAfter,
                                    appliedChanges = card.appliedChanges,
                                    revalidationStatus = card.revalidationStatus,
                                    isPartialOrFailed = card.isPartialOrFailed,
                                    isAlreadyApplied = card.isAlreadyApplied,
                                    onDismiss = { viewModel.close() }
                                )
                            }
                            else -> {
                                // Default fallback card
                            }
                        }
                    }

                    // Render Bubble Messages
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
