package com.example.sld

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*

@Composable
fun SldValidationPanel(
    issues: List<SldValidationIssue>,
    onFocusNode: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = com.example.ui.theme.LocalThemeIsDark.current
    val palette = SldTheme.palette(isDark)
    val cardBg = palette.surface
    val chipBg = if (isDark) Color(0xFF0D1117) else Color(0xFFF1F5F9)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFCBD5E1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SleekWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Network Validation Issues (${issues.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = palette.textSecondary
                    )
                }
            }

            if (issues.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓ No network topology or validation issues found.",
                        color = SleekPass,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(issues) { issue ->
                        val badgeColor = when (issue.severity) {
                            SldValidationStatus.INVALID -> SleekFail
                            SldValidationStatus.WARNING -> SleekWarning
                            SldValidationStatus.DISCONNECTED -> SlateTextSecondary
                            SldValidationStatus.DATA_REQUIRED -> SleekPrimary
                            SldValidationStatus.VALID -> SleekPass
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFocusNode(issue.affectedNodeId) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = chipBg),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = issue.componentName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = palette.textPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = issue.issueCode,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = issue.failedCondition,
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary
                                )

                                issue.recommendedCorrection?.let { rec ->
                                    Text(
                                        text = "Fix: $rec",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SleekPrimary
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
