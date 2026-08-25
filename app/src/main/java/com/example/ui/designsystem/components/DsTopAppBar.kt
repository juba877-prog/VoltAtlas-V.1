package com.example.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsType

/**
 * Visual shell only — project name (eyebrow) + screen title, then
 * PDF / assistant (AI) / overflow actions. No data wiring.
 */
@Composable
fun DsTopAppBar(
    projectName: String,
    screenTitle: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onPdfClick: (() -> Unit)? = null,
    onAssistantClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    text = projectName,
                    style = DsType.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = screenTitle,
                    style = DsType.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onPdfClick != null) {
                IconButton(onClick = onPdfClick) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = "PDF")
                }
            }
            if (onAssistantClick != null) {
                IconButton(onClick = onAssistantClick) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = "Assistant")
                }
            }
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Overflow menu")
                }
            }
        }
    }
}