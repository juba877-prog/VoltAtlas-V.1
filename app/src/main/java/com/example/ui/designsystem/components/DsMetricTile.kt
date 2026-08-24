package com.example.ui.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.designsystem.theme.DsShape
import com.example.ui.designsystem.theme.DsSpacing
import com.example.ui.designsystem.theme.DsType

/**
 * KPI / summary-strip tile: label + large value + optional unit + optional
 * status badge. Stateless — values are rendered exactly as provided.
 */
@Composable
fun DsMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    status: DsStatusVariant? = null
) {
    DsCard(modifier = modifier) {
        Column {
            Text(
                text = label,
                style = DsType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(DsSpacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = DsType.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(DsSpacing.xs))
                    Text(
                        text = unit,
                        style = DsType.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (status != null) {
                Spacer(modifier = Modifier.height(DsSpacing.sm))
                DsStatusBadge(text = status.label(), variant = status)
            }
        }
    }
}

private fun DsStatusVariant.label(): String = when (this) {
    DsStatusVariant.Pass -> "PASS"
    DsStatusVariant.Warning -> "WARNING"
    DsStatusVariant.Fail -> "FAIL"
    DsStatusVariant.Info -> "INFO"
    DsStatusVariant.Neutral -> "STANDBY"
}