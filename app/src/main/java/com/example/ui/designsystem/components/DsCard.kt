package com.example.ui.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.DsColor
import com.example.ui.designsystem.theme.DsShape
import com.example.ui.designsystem.theme.DsSpacing

/**
 * CANONICAL VoltAtlas container (MD3): `surfaceContainer` + subtle tonal
 * elevation. NO border-as-definition, no shadow — separation comes from the
 * surface-container level per the MD3 mandate. Single card style app-wide.
 */
@Composable
fun DsCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = DsColor.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = DsShape.card,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(DsSpacing.lg),
            content = content
        )
    }
}