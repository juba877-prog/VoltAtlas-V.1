package com.example.ai.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.service.AssistantMessage
import com.example.ai.service.MessageRole
import com.example.ai.ui.components.*
import com.example.ui.adaptive.rememberVoltAtlasWindowWidth
import com.example.ui.adaptive.VoltAtlasWindowWidth
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * VoltAtlas Assistant chat surface.
 *
 * Architecture (single, authoritative):
 *
 * ```
 * AssistantRoot (ModalBottomSheet)
 *     ├── Header
 *     ├── Gateway banner (engineering status, quiescent when Ready)
 *     ├── ConversationList   <- ONE LazyColumn: messages AND typed cards, chronological
 *     ├── NewMessageIndicator (floating, only while reading older content)
 *     ├── SuggestionsBar     <- OUTSIDE the conversation list, compact, max 4
 *     └── Composer           <- anchored above the IME via WindowInsets, never in the list
 * ```
 *
 * Presentation-only: conversation/agent/tool/grounding/mutation state stays in
 * [EngineeringAssistantViewModel]. Transient UI state here is limited to focus, IME
 * visibility, scroll position and the new-message indicator.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EngineeringAssistantSheet(
    viewModel: EngineeringAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiSettingsState by viewModel.aiSettingsState.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val windowWidth = rememberVoltAtlasWindowWidth()
    val isWide = windowWidth != VoltAtlasWindowWidth.Compact

    if (!uiState.isOpen) return

    // Tier-0 predictive back consumer (native sheet behavior untouched).
    PredictiveBackHandler(enabled = uiState.isOpen) { progress ->
        try {
            progress.collect { /* back progress */ }
            viewModel.onBackHandled()
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    // The ONE authoritative conversation scroll state — no competing scroll states exist.
    val listState = rememberLazyListState()
    val indicatorScope = rememberCoroutineScope()

    // --- Keyboard / focus (transient UI state only) -------------------------------
    // The Assistant NEVER grabs focus on open: the keyboard only appears after the user
    // explicitly taps the composer, and Back always dismisses the keyboard first.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    var composerFocused by remember { mutableStateOf(false) }
    // BACK POLICY (§2): the first Back after the composer/keyboard became active dismisses the
    // keyboard; only a later Back closes the Assistant. Transient UI state only.
    var keyboardDismissPending by remember { mutableStateOf(false) }
    LaunchedEffect(composerFocused) {
        if (composerFocused) keyboardDismissPending = true
    }
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) keyboardDismissPending = true
    }
    val keyboardOwnsBack = isImeVisible || keyboardDismissPending

    // --- Conversation projection (derived, read-only) -----------------------------
    // Suggestions are never part of this list: they are contextual actions rendered in a
    // dedicated bar outside the conversation.
    val conversationEntries = remember(uiState.messages, uiState.typedCards) {
        AssistantConversationOrder.build(uiState.messages, uiState.typedCards)
    }
    val conversationSignature = remember(conversationEntries) {
        if (conversationEntries.isEmpty()) {
            "empty"
        } else {
            "${conversationEntries.size}:${conversationEntries.last().key}"
        }
    }
    val suggestions = remember(uiState.messages, conversationEntries) {
        AssistantSuggestions.build(
            hasUserMessages = uiState.messages.any { it.role == MessageRole.USER },
            currentCard = AssistantConversationOrder.currentTurnCard(conversationEntries)
        )
    }

    // --- Scroll policy -------------------------------------------------------------
    val isNearLatest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            AssistantChatScrollPolicy.isNearLatest(
                lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItems = info.totalItemsCount
            )
        }
    }
    // Signature of everything the user should be scrolled to: conversation content and the
    // in-flight typing indicator. Suggestion updates are NOT part of it, so they (or any
    // other recomposition) can never move the user's reading position.
    val scrollSignature = "$conversationSignature|thinking=${uiState.isThinking}"
    var previousScrollSignature by remember { mutableStateOf<String?>(null) }
    var seenConversationSignature by remember { mutableStateOf(conversationSignature) }

    LaunchedEffect(scrollSignature) {
        val contentChanged = previousScrollSignature.let { it == null || it != scrollSignature }
        previousScrollSignature = scrollSignature
        if (!AssistantChatScrollPolicy.shouldAutoScroll(isNearLatest, contentChanged)) {
            return@LaunchedEffect
        }
        // Let the new items finish layout before resolving the target index.
        withFrameNanos { }
        val target = listState.layoutInfo.totalItemsCount - 1
        if (target >= 0) {
            listState.animateScrollToItem(target)
            seenConversationSignature = conversationSignature
        }
    }

    // Returning to the latest content (by scroll or via the indicator) clears the indicator.
    LaunchedEffect(isNearLatest) {
        if (isNearLatest) {
            seenConversationSignature = conversationSignature
        }
    }

    val showNewMessage = AssistantChatScrollPolicy.shouldShowNewMessageIndicator(
        conversationSignature = conversationSignature,
        seenConversationSignature = seenConversationSignature,
        isNearLatest = isNearLatest
    )

    // SLICE 8E: EMPHASIZED content entrance for this approved high-value
    // moment (opening the assistant). The native ModalBottomSheet slide/drag/
    // predictive-back behavior is intentionally UNTOUCHED below — only the
    // inner content performs a brief calm rise-and-fade through the
    // centralized EmphasizedSpatial token. Reduced motion publishes instantly.
    val contentEntrance = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            contentEntrance.animateTo(
                targetValue = 1f,
                animationSpec = com.example.ui.theme.VoltAtlasMotion.EmphasizedSpatial.floatSpec
            )
        } else {
            contentEntrance.snapTo(1f)
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
        // BACK BEHAVIOR (§2) — registered inside the sheet's dialog window so it takes
        // priority over the native dismiss callback:
        //   CASE A: keyboard visible -> hide the keyboard, the Assistant stays open.
        //   CASE B: keyboard hidden  -> not enabled, native dismiss closes the Assistant.
        BackHandler(enabled = keyboardOwnsBack) {
            keyboardController?.hide()
            focusManager.clearFocus()
            keyboardDismissPending = false
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isWide) Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally) else Modifier)
                .fillMaxHeight(if (uiState.isExpanded) 0.95f else 0.80f)
                // Composer bottom anchoring (§9): IME and system/navigation bars are handled
                // once, at the root, through WindowInsets — no hardcoded keyboard heights,
                // no screen-size hacks, no overlap.
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
                )
                // BACK BEHAVIOR (§2) — key-level interception as well, because the sheet lives
                // in its own dialog window whose dismiss callback is not guaranteed to consult
                // composition back handlers:
                //   CASE A: keyboard up -> Back hides the keyboard, the Assistant stays open.
                //   CASE B: keyboard hidden -> event is not consumed, native dismiss closes it.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && keyboardOwnsBack) {
                        if (event.type == KeyEventType.KeyUp) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            keyboardDismissPending = false
                        }
                        // Consume both the down and the up event so the dialog cannot dismiss.
                        true
                    } else {
                        false
                    }
                }
                .graphicsLayer {
                    alpha = contentEntrance.value
                    translationY = (1f - contentEntrance.value) * 24.dp.toPx() // subtle rise-in
                }
        ) {
            // --- TOP HEADER ROW ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

                                // Offline Indicator Badge
                                if (uiState.header.offlineBadgeVisible) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .semantics { contentDescription = "Assistant is running offline" }
                                            .testTag("offline_indicator_badge")
                                    ) {
                                        Text(
                                            text = "OFFLINE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer
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
                            onClick = { viewModel.openAiSettings() },
                            modifier = Modifier.size(48.dp).testTag("ai_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "AI Settings",
                                tint = ThemeTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleExpand() },
                            modifier = Modifier.size(48.dp)
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
                            modifier = Modifier.size(48.dp)
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

            // --- GATEWAY STATUS / CATALOG READINESS BANNER ---
            when (uiState.gatewayStatus) {
                GatewayStatus.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("gateway_loading_indicator"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Loading engineering catalog repository...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GatewayStatus.Failed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("gateway_error_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Catalog Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Catalog repository error: ${uiState.gatewayErrorMessage ?: "Failed to load"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                GatewayStatus.Unavailable -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("gateway_unavailable_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Gateway Unavailable",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI mutation gateway is unavailable. Project modifications are disabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GatewayStatus.Ready -> {
                    // Quiescent: no banner
                }
            }

            // --- CONVERSATION STREAM (ONE authoritative list, chronological) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("assistant_conversation_list")
                ) {
                    // Messages AND typed cards render interleaved in one chronological order.
                    items(
                        count = conversationEntries.size,
                        key = { index -> conversationEntries[index].key }
                    ) { index ->
                        when (val entry = conversationEntries[index]) {
                            is AssistantConversationEntry.MessageEntry -> {
                                AssistantMessageBubbleItem(message = entry.message)
                            }
                            is AssistantConversationEntry.CardEntry -> {
                                AssistantCardItem(
                                    card = entry.card,
                                    gatewayStatus = uiState.gatewayStatus,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

                    // State A: Idle Landing Surface (Zero fake metrics, contextual capabilities)
                    if (uiState.typedCards.isEmpty() && uiState.messages.none { it.role == MessageRole.USER } && !uiState.isThinking) {
                        item(key = "idle_landing") {
                            AssistantIdleLanding(
                                context = uiState.context,
                                onPromptClick = { prompt -> viewModel.sendMessage(prompt) }
                            )
                        }
                    }

                    // State C: In-Flight Tool Execution Indicator
                    if (uiState.isThinking) {
                        item(key = "thinking_indicator") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .padding(start = 8.dp, top = 4.dp)
                                    .testTag("assistant_processing_indicator")
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ThemePrimary
                                )
                                Text(
                                    text = "Running deterministic engineering calculation...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ThemeTextSecondary
                                )
                            }
                        }
                    }
                }

                // --- NEW MESSAGE INDICATOR (§8) -------------------------------------
                // Compact floating control shown only while the user is reading older
                // content and genuinely new conversation content has arrived.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showNewMessage,
                    enter = fadeIn(VoltAtlasMotion.FastEffects.floatSpec) +
                        slideInVertically(VoltAtlasMotion.StandardSpatial.offsetSpec) { it / 2 },
                    exit = fadeOut(VoltAtlasMotion.FastEffects.floatSpec) +
                        slideOutVertically(VoltAtlasMotion.StandardSpatial.offsetSpec) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Surface(
                        onClick = {
                            indicatorScope.launch {
                                val target = listState.layoutInfo.totalItemsCount - 1
                                if (target >= 0) listState.animateScrollToItem(target)
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = ThemePrimary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("assistant_new_message_indicator")
                            .semantics { contentDescription = "Scroll to latest message" }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "New message",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // --- SUGGESTIONS BAR (§4/§5) ------------------------------------------
            // Structurally separate from the conversation: contextual actions only,
            // never messages, never accumulated, always a compact single row.
            AssistantSuggestionsBar(
                suggestions = suggestions,
                onSelect = { suggestion ->
                    when (suggestion.kind) {
                        AssistantSuggestionUi.Kind.PROMPT -> {
                            viewModel.onChipSelected(suggestion.prompt ?: suggestion.label)
                        }
                        AssistantSuggestionUi.Kind.ACTION -> {
                            val cardId = suggestion.cardId
                            val action = suggestion.action
                            if (cardId != null && action != null) {
                                viewModel.onCardAction(cardId, action)
                            } else {
                                viewModel.onChipSelected(suggestion.label)
                            }
                        }
                    }
                }
            )

            // --- INPUT COMPOSER BAR (§9) ------------------------------------------
            // Anchored independently at the bottom of the sheet. The IME/system-bar insets
            // are applied once at the root column, so the composer rides above the keyboard
            // with no overlap, no fixed offsets and no jumping.
            Surface(
                color = ThemeSurface,
                border = BorderStroke(1.dp, ThemeBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_input")
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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (uiState.inputText.isNotBlank() && !uiState.isThinking) {
                                        viewModel.sendMessage(uiState.inputText)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp, max = 100.dp)
                                // Focus is granted ONLY by an explicit user tap — never by an
                                // automatic FocusRequester on composition (§1).
                                .onFocusChanged { focusState -> composerFocused = focusState.isFocused }
                                .testTag("assistant_input_field")
                        )
                    }

                    Box(
                        modifier = Modifier.testTag("assistant_send")
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(uiState.inputText)
                            },
                            enabled = uiState.inputText.isNotBlank() && !uiState.isThinking,
                            modifier = Modifier
                                .size(48.dp)
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

    if (aiSettingsState.isOpen) {
        AiSettingsDialog(
            state = aiSettingsState,
            onDismiss = { viewModel.closeAiSettings() },
            onSaveCredential = { viewModel.saveGeminiCredential(it) },
            onRemoveCredential = { viewModel.removeGeminiCredential() },
            onSelectProvider = { viewModel.selectAiProvider(it) },
            onTestConnection = { viewModel.testConnection() }
        )
    }
}

/**
 * Renders one typed Gate 12 presentation card inside the conversation list.
 * Presentation-only — every engineering meaning, state and action path is preserved.
 */
@Composable
private fun AssistantCardItem(
    card: AssistantCardUiModel,
    gatewayStatus: GatewayStatus,
    viewModel: EngineeringAssistantViewModel
) {
    when (card) {
        is AssistantCardUiModel.ResultSummaryCard -> {
            AssistantResultCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.BlockingQuestionCard -> {
            AssistantBlockingQuestionCard(
                card = card,
                onChipClick = { chipText -> viewModel.onChipSelected(chipText) },
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.WhatIfResultCard -> {
            AssistantWhatIfCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.SafeErrorCard -> {
            AssistantSafeErrorCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.DirectAnswerCard -> {
            AssistantDirectAnswerCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.ComparisonCard -> {
            AssistantComparisonCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.ApprovalRequiredNonExecutableCard -> {
            AssistantApprovalRequiredCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.ProposalReviewCard -> {
            AssistantProposalReviewCard(
                model = card.model,
                onActionClick = { action -> viewModel.onProposalReviewAction(action.actionType) }
            )
        }
        is AssistantCardUiModel.ApprovalConfirmationCard -> {
            AssistantApprovalConfirmationCard(
                reviewModel = card.model,
                isSubmitting = card.isSubmitting,
                isApplyEnabled = gatewayStatus == GatewayStatus.Ready,
                gatewayUnavailableReason = when (gatewayStatus) {
                    GatewayStatus.Unavailable ->
                        "AI mutation gateway is not configured. Modifications cannot be applied."
                    GatewayStatus.Failed ->
                        "AI mutation gateway failed to initialize. Modifications cannot be applied."
                    GatewayStatus.Loading ->
                        "AI mutation gateway is still initializing. Please wait..."
                    GatewayStatus.Ready -> null
                },
                onConfirmApprove = { viewModel.onConfirmApproveAndApply() },
                onCancel = { viewModel.onCancelApproval() }
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
        is AssistantCardUiModel.FailureDiagnosisCard -> {
            AssistantFailureDiagnosisCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.RecommendationCard -> {
            AssistantRecommendationCard(
                card = card,
                onActionClick = { action -> viewModel.onCardAction(card.cardId, action) }
            )
        }
        is AssistantCardUiModel.ToolExecutionCard -> {
            AssistantToolExecutionCard(
                toolName = card.toolName,
                methodology = card.methodology,
                checkpoints = card.checkpoints.ifEmpty {
                    listOf(
                        ToolExecutionCheckpoint("Inputs resolved from active project"),
                        ToolExecutionCheckpoint("Deterministic calculation completed"),
                        ToolExecutionCheckpoint("Engineering evidence verified")
                    )
                }
            )
        }
    }
}

/**
 * Chat-style message rendering.
 *
 * - User turn: compact right-aligned tinted bubble (low chrome, no heavy borders).
 * - Assistant turn: calm full-width text — no oversized bubble, no card wrapper, so
 *   engineering cards below remain the visual focus (hierarchy: answer -> verified data).
 */
@Composable
private fun AssistantMessageBubbleItem(message: AssistantMessage) {
    val isUser = message.role == MessageRole.USER
    val isDark = LocalThemeIsDark.current

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = ThemeTextPrimary,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ThemePrimary.copy(alpha = if (isDark) 0.16f else 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("assistant_user_message")
            )
        }
    } else {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            color = ThemeTextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("assistant_assistant_message")
        )
    }
}

