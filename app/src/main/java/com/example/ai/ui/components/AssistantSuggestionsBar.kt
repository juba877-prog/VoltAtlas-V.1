package com.example.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ui.AssistantSuggestionUi
import com.example.ai.ui.AssistantSuggestions
import com.example.ui.theme.ThemeBorderSubtle
import com.example.ui.theme.ThemePrimary
import com.example.ui.theme.ThemeSurface
import com.example.ui.theme.ThemeTextPrimary

/**
 * Compact, horizontally scrolling SuggestionsBar.
 *
 * Architectural rule (Chat architecture §3/§4): suggestions live in a dedicated bar that is
 * structurally OUTSIDE the conversation LazyColumn. They are never messages, never fake
 * assistant turns, and never accumulate: only the current context's suggestions
 * (max [AssistantSuggestions.MAX_VISIBLE]) are rendered in one low-footprint row.
 *
 * Test tags:
 * - `assistant_suggestions_bar` — the bar container.
 * - `assistant_suggestion_chip` — every chip (used to enforce the visible maximum).
 */
@Composable
fun AssistantSuggestionsBar(
    suggestions: List<AssistantSuggestionUi>,
    onSelect: (AssistantSuggestionUi) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stable strip: reserves the same height whether suggestions are present or not, so the
    // composer never jumps when suggestions update.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("assistant_suggestions_bar"),
        contentAlignment = Alignment.CenterStart
    ) {
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = suggestions.take(AssistantSuggestions.MAX_VISIBLE),
                    key = { "${it.kind}:${it.label}" }
                ) { suggestion ->
                    AssistantSuggestionChip(
                        suggestion = suggestion,
                        onClick = { onSelect(suggestion) }
                    )
                }
            }
        }
    }
}

/**
 * Compact suggestion chip with a >= 48dp touch target (§16 Accessibility) while keeping a
 * low vertical footprint (56dp bar incl. padding).
 */
@Composable
private fun AssistantSuggestionChip(
    suggestion: AssistantSuggestionUi,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val isPrimary = suggestion.isPrimary
    val background = when {
        isPrimary -> ThemePrimary
        suggestion.kind == AssistantSuggestionUi.Kind.ACTION -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> ThemeSurface
    }
    val textColor = when {
        isPrimary -> androidx.compose.ui.graphics.Color.White
        suggestion.kind == AssistantSuggestionUi.Kind.ACTION -> MaterialTheme.colorScheme.primary
        else -> ThemeTextPrimary
    }

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(background)
            .then(
                if (isPrimary) Modifier
                else Modifier.border(1.dp, ThemeBorderSubtle, shape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Suggested action: ${suggestion.label}"
            }
            .testTag("assistant_suggestion_chip"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = suggestion.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
