package com.example.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.DsShape
import com.example.ui.designsystem.theme.DsType
import com.example.ui.designsystem.theme.dsExtendedColors

enum class DsStatusVariant {
    Pass,
    Warning,
    Fail,
    Info,
    Neutral;

    @Composable
    fun color(): Color {
        val ext = MaterialTheme.dsExtendedColors
        return when (this) {
            Pass -> ext.success
            Warning -> ext.warning
            Fail -> ext.danger
            Info -> ext.info
            Neutral -> ext.neutral
        }
    }
}

/**
 * VoltAtlas pill status badge — `surfaceContainer` + tinted 15% status
 * background, full-saturation status foreground text and a leading dot / icon.
 * The ONLY status visualization. Colors come from the theme-aware extended palette.
 */
@Composable
fun DsStatusBadge(
    text: String,
    variant: DsStatusVariant,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val statusColor = variant.color()
    Box(
        modifier = modifier
            .clip(DsShape.pill)
            .background(statusColor.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = statusColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
            }
            Text(
                text = text,
                style = DsType.labelMedium,
                color = statusColor
            )
        }
    }
}