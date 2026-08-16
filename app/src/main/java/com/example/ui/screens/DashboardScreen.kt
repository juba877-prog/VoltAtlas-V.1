package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ui.AssistantEntryPoint
import com.example.sld.SldValidationStatus
import com.example.ui.UiState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.theme.ThemeTextSecondary
import com.example.ui.theme.ThemeSurface
import java.util.Locale

internal data class MetricDisplayItem(
    val label: String,
    val value: String,
    val unit: String,
    val statusText: String? = null,
    val statusColor: Color? = null
)

@Composable
fun DashboardScreen(
    state: UiState.Success, 
    onNavigate: (Int) -> Unit,
    onOpenAssistant: ((AssistantEntryPoint) -> Unit)? = null
) {
    val summary = state.validationSummary
    val totalCircuits = state.circuits.size

    val maxVdPct = remember(state.sizingResults) { state.sizingResults.values.maxOfOrNull { it.cumulativeVDpct } ?: 0.0 }
    val maxTxLoadPct = remember(state.transformerResults) { state.transformerResults.values.maxOfOrNull { it.loadingPct } ?: 0.0 }
    val maxIscKa = remember(state.shortCircuitResults) { state.shortCircuitResults.values.maxOfOrNull { it.iscKA } ?: 0.0 }

    val overallColor = when {
        summary.totalFailures > 0 -> StatusFail
        summary.totalReviewsRequired > 0 -> StatusWarning
        summary.totalWarnings > 0 -> SleekPrimary
        else -> StatusPass
    }

    val overallBadgeText = when {
        summary.totalFailures > 0 -> "FAIL"
        summary.totalReviewsRequired > 0 -> "REVIEW REQUIRED"
        summary.totalWarnings > 0 -> "WARNINGS"
        else -> "PASS"
    }

    val statusIcon = when {
        summary.totalFailures > 0 -> Icons.Default.Error
        summary.totalReviewsRequired > 0 -> Icons.Default.Warning
        summary.totalWarnings > 0 -> Icons.Default.Info
        else -> Icons.Default.CheckCircle
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Project Health Card
        item {
            VoltAtlasCard(
                onClick = {
                    if (summary.openIssues.isNotEmpty()) {
                        onNavigate(5)
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(overallColor.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, overallColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "Status",
                                tint = overallColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PROJECT HEALTH",
                                style = Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = ThemeTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = summary.overallStatus,
                                style = CardTitle.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                                color = overallColor,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (summary.openIssues.isEmpty()) "No open issues" else if (summary.openIssues.size == 1) "1 item needs attention" else "${summary.openIssues.size} items need attention",
                                style = Caption.copy(fontSize = 12.sp),
                                color = SlateTextSecondary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(overallColor.copy(alpha = 0.12f), shape = CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (summary.openIssues.isEmpty()) "0 items" else if (summary.openIssues.size == 1) "1 item" else "${summary.openIssues.size} items",
                            style = Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = overallColor
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Key Engineering Metrics Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "KEY METRICS",
                    style = Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = SleekPrimary
                )
                
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val width = maxWidth
                    val isTabletRow: Boolean = width >= 600.dp
                    val useTwoColumns = width >= 260.dp
                    val isSingleColumn = !useTwoColumns && !isTabletRow

                    val metricsList = listOf(
                        MetricDisplayItem(
                            label = "Active Circuits",
                            value = if (totalCircuits > 0) totalCircuits.toString() else "0",
                            unit = "",
                            statusText = if (totalCircuits > 0) "OK" else null,
                            statusColor = if (totalCircuits > 0) StatusNeutral else null
                        ),
                        MetricDisplayItem(
                            label = "Max Tx Load",
                            value = if (state.transformers.isNotEmpty()) String.format(Locale.US, "%.1f", maxTxLoadPct) else "N/A",
                            unit = if (state.transformers.isNotEmpty()) "%" else "",
                            statusText = if (state.transformers.isEmpty()) null else if (maxTxLoadPct > 100.0) "OVERLOAD" else if (maxTxLoadPct > 0.0) "OK" else null,
                            statusColor = if (state.transformers.isEmpty()) null else if (maxTxLoadPct > 100.0) SleekFail else if (maxTxLoadPct > 0.0) StatusNeutral else null
                        ),
                        MetricDisplayItem(
                            label = "Max Cum. VD",
                            value = if (state.sizingResults.isNotEmpty()) String.format(Locale.US, "%.2f", maxVdPct) else "N/A",
                            unit = if (state.sizingResults.isNotEmpty()) "%" else "",
                            statusText = if (state.sizingResults.isEmpty()) null else if (maxVdPct > 5.0) "EXCEEDED" else if (maxVdPct > 0.0) "OK" else null,
                            statusColor = if (state.sizingResults.isEmpty()) null else if (maxVdPct > 5.0) SleekFail else if (maxVdPct > 0.0) StatusNeutral else null
                        ),
                        MetricDisplayItem(
                            label = "Max Isc (3φ)",
                            value = if (state.shortCircuitResults.isNotEmpty()) String.format(Locale.US, "%.2f", maxIscKa) else "N/A",
                            unit = if (state.shortCircuitResults.isNotEmpty()) "kA" else "",
                            statusText = if (state.shortCircuitResults.isNotEmpty() && maxIscKa > 0.0) "CALCULATED" else null,
                            statusColor = if (maxIscKa > 0.0) SleekPrimary else null
                        )
                    )

                    if (isTabletRow) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            metricsList.forEach { metric ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    VoltAtlasMetricCard(
                                        label = metric.label,
                                        value = metric.value,
                                        unit = metric.unit,
                                        statusText = metric.statusText,
                                        statusColor = metric.statusColor,
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                        }
                    } else if (isSingleColumn) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            metricsList.forEach { metric ->
                                VoltAtlasMetricCard(
                                    label = metric.label,
                                    value = metric.value,
                                    unit = metric.unit,
                                    statusText = metric.statusText,
                                    statusColor = metric.statusColor
                                )
                            }
                        }
                    } else {
                        // Standard phone: 2x2 grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (i in 0..1) {
                                    val metric = metricsList[i]
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        VoltAtlasMetricCard(
                                            label = metric.label,
                                            value = metric.value,
                                            unit = metric.unit,
                                            statusText = metric.statusText,
                                            statusColor = metric.statusColor,
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (i in 2..3) {
                                    val metric = metricsList[i]
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        VoltAtlasMetricCard(
                                            label = metric.label,
                                            value = metric.value,
                                            unit = metric.unit,
                                            statusText = metric.statusText,
                                            statusColor = metric.statusColor,
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Needs Attention Section (ONLY rendered if openIssues is NOT empty)
        if (summary.openIssues.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NEEDS ATTENTION",
                            style = Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = SleekWarning
                        )
                        Box(
                            modifier = Modifier
                                .background(SleekWarning.copy(alpha = 0.15f), shape = CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (summary.openIssues.size == 1) "1 item" else "${summary.openIssues.size} items",
                                style = Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = SleekWarning
                            )
                        }
                    }

                    summary.openIssues.take(6).forEach { issue ->
                        val itemColor = when (issue.severity) {
                            SldValidationStatus.INVALID -> StatusFail
                            SldValidationStatus.DATA_REQUIRED -> StatusWarning
                            else -> SleekPrimary
                        }
                        val issueIcon = when (issue.severity) {
                            SldValidationStatus.INVALID -> Icons.Default.Error
                            else -> Icons.Default.Warning
                        }

                        VoltAtlasCard(
                            onClick = {
                                val targetTab = when {
                                    issue.componentName.contains("Transformer", ignoreCase = true) -> 2
                                    issue.componentName.contains("ATS", ignoreCase = true) || issue.componentName.contains("Generator", ignoreCase = true) -> 3
                                    issue.componentName.contains("Protection", ignoreCase = true) -> 4
                                    else -> 1
                                }
                                onNavigate(targetTab)
                            }
                        ) {
                            // Row 1: icon + component name + chevron
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = issueIcon,
                                    contentDescription = "Issue status",
                                    tint = itemColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = issue.componentName,
                                    style = CardTitle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = ThemeTextColor,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Row 2: full-width description
                            Text(
                                text = issue.failedCondition,
                                style = Caption.copy(fontSize = 12.sp),
                                color = SlateTextSecondary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Row 3: severity badge and diagnose button
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VoltAtlasStatusBadge(
                                    text = issue.severity.name.replace("_", " "),
                                    color = itemColor
                                )
                                if (onOpenAssistant != null) {
                                    OutlinedButton(
                                        onClick = { onOpenAssistant(AssistantEntryPoint.DIAGNOSE_FAILURE) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(VoltAtlasShapes.chip),
                                        border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.5f)),
                                        modifier = Modifier.testTag("diagnose_issue_btn_${issue.componentName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Diagnose",
                                            tint = SleekPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Diagnose", fontSize = 11.sp, color = SleekPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3.5 AI Assistant Quick Action Card
        if (onOpenAssistant != null) {
            item {
                Card(
                    onClick = { onOpenAssistant(AssistantEntryPoint.DASHBOARD_ACTION) },
                    colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                    border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(VoltAtlasShapes.card),
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_ask_assistant_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(SleekPrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Ask VoltAtlas",
                                tint = SleekPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ask VoltAtlas Assistant",
                                style = CardTitle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = ThemeTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Analyze sizing, voltage drop, and transformer load",
                                style = Caption.copy(fontSize = 12.sp),
                                color = SlateTextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Assistant",
                            tint = SleekPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 4. Primary Engineering Modules Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PRIMARY MODULES",
                    style = Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = SleekPrimary
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val width = maxWidth
                    val isTabletRow: Boolean = width >= 600.dp
                    val useTwoColumns = width >= 260.dp
                    val isSingleColumn = !useTwoColumns && !isTabletRow

                    val modules = if (!isSingleColumn) {
                        listOf(
                            Triple("Circuits", "$totalCircuits Configured", ElectricalIcons.Circuits to 1),
                            Triple("Transformers", "${state.transformers.size} Configured", ElectricalIcons.Transformer to 2),
                            Triple("Power Systems", "Gen · ATS · UPS · PF", ElectricalIcons.Power to 3),
                            Triple("SLD View", "Single-Line Diagram", ElectricalIcons.Sld to 5)
                        )
                    } else {
                        listOf(
                            Triple("Circuits", "$totalCircuits Configured", ElectricalIcons.Circuits to 1),
                            Triple("Transformers", "${state.transformers.size} Configured", ElectricalIcons.Transformer to 2),
                            Triple("Power Systems", "Generator, ATS, UPS, PF Correction", ElectricalIcons.Power to 3),
                            Triple("SLD View", "Interactive Diagram", ElectricalIcons.Sld to 5)
                        )
                    }

                    if (isTabletRow) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            modules.forEach { (title, subtitle, iconAndTab) ->
                                val (icon, targetTab) = iconAndTab
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    VoltAtlasModuleCardCompact(
                                        title = title,
                                        description = subtitle,
                                        icon = icon,
                                        onClick = { onNavigate(targetTab) },
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                        }
                    } else if (isSingleColumn) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            modules.forEach { (title, subtitle, iconAndTab) ->
                                val (icon, targetTab) = iconAndTab
                                VoltAtlasModuleCard(
                                    title = title,
                                    description = subtitle,
                                    icon = icon,
                                    onClick = { onNavigate(targetTab) }
                                )
                            }
                        }
                    } else {
                        // Standard phone: 2x2 grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (i in 0..1) {
                                    val (title, subtitle, iconAndTab) = modules[i]
                                    val (icon, targetTab) = iconAndTab
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        VoltAtlasModuleCardCompact(
                                            title = title,
                                            description = subtitle,
                                            icon = icon,
                                            onClick = { onNavigate(targetTab) },
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (i in 2..3) {
                                    val (title, subtitle, iconAndTab) = modules[i]
                                    val (icon, targetTab) = iconAndTab
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        VoltAtlasModuleCardCompact(
                                            title = title,
                                            description = subtitle,
                                            icon = icon,
                                            onClick = { onNavigate(targetTab) },
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
