package com.example.ui.components.quick

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.quick.VoltageDropComplianceStatus
import com.example.ui.theme.*

@Composable
fun QuickCard(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderColor,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun QuickSectionTitle(
    title: String,
    accentColor: Color = SleekPrimary,
    badgeText: String? = null,
    badgeColor: Color = SleekPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ThemeTextColor
            )
        }

        if (badgeText != null) {
            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, badgeColor)
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun QuickSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SleekPrimary
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) activeColor else Color.Transparent)
                        .clickable { onSelectIndex(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) ThemeTextColor else SlateTextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuickStatusBanner(
    status: VoltageDropComplianceStatus,
    headline: String,
    subText: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, icon, statusText) = when (status) {
        VoltageDropComplianceStatus.PASS -> Tuple4(
            SleekPass.copy(alpha = 0.12f),
            SleekPass,
            Icons.Default.CheckCircle,
            "PASS"
        )
        VoltageDropComplianceStatus.WARNING -> Tuple4(
            SleekWarning.copy(alpha = 0.12f),
            SleekWarning,
            Icons.Default.Warning,
            "WARNING"
        )
        VoltageDropComplianceStatus.FAIL -> Tuple4(
            SleekFail.copy(alpha = 0.12f),
            SleekFail,
            Icons.Default.Cancel,
            "FAIL"
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("status_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(borderColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = statusText, tint = borderColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextColor,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subText,
                    color = SlateTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun QuickMetricHero(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = SleekPrimary
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, color = SlateTextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = SlateTextSecondary.copy(alpha = 0.8f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun QuickCheckRow(
    title: String,
    status: VoltageDropComplianceStatus,
    summary: String,
    detail: String? = null
) {
    val iconColor = when (status) {
        VoltageDropComplianceStatus.PASS -> SleekPass
        VoltageDropComplianceStatus.WARNING -> SleekWarning
        VoltageDropComplianceStatus.FAIL -> SleekFail
    }
    val icon = when (status) {
        VoltageDropComplianceStatus.PASS -> Icons.Default.CheckCircle
        VoltageDropComplianceStatus.WARNING -> Icons.Default.Warning
        VoltageDropComplianceStatus.FAIL -> Icons.Default.Cancel
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = ThemeTextColor, fontSize = 13.sp)
            Text(text = summary, color = SlateTextSecondary, fontSize = 12.sp)
            if (detail != null) {
                Text(text = detail, color = SlateTextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun QuickCalculationDetailsAccordion(
    formula: String,
    substitution: String
) {
    var expanded by remember { mutableStateOf(false) }
    val reduceMotion = com.example.ui.theme.LocalReduceMotion.current

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier
            .fillMaxWidth()
            // SLICE 8D: central motion tokens — deterministic tween, no spring
            // bounce on dense engineering content; instant under reduced motion.
            .animateContentSize(
                animationSpec = if (reduceMotion) {
                    androidx.compose.animation.core.tween(com.example.ui.theme.VoltAtlasMotion.ReducedMotion.DurationMillis)
                } else {
                    androidx.compose.animation.core.tween(
                        durationMillis = com.example.ui.theme.VoltAtlasMotion.StandardSpatial.DurationMillis,
                        easing = com.example.ui.theme.VoltAtlasMotion.StandardSpatial.easing
                    )
                }
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Functions, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calculation Details & Substitution",
                        fontWeight = FontWeight.SemiBold,
                        color = ThemeTextColor,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = SlateTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(com.example.ui.theme.VoltAtlasMotion.ReducedMotion.DurationMillis))
                } else {
                    fadeIn(animationSpec = com.example.ui.theme.VoltAtlasMotion.StandardSpatial.floatSpec) +
                        expandVertically(animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = com.example.ui.theme.VoltAtlasMotion.StandardSpatial.DurationMillis,
                            easing = com.example.ui.theme.VoltAtlasMotion.StandardSpatial.easing
                        ))
                },
                exit = if (reduceMotion) {
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(com.example.ui.theme.VoltAtlasMotion.ReducedMotion.DurationMillis))
                } else {
                    fadeOut(animationSpec = com.example.ui.theme.VoltAtlasMotion.FastEffects.floatSpec) +
                        shrinkVertically(animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = com.example.ui.theme.VoltAtlasMotion.FastEffects.DurationMillis,
                            easing = androidx.compose.animation.core.FastOutLinearInEasing
                        ))
                }
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = BorderColor)
                    Text("Governing Formula:", color = SlateTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formula,
                            color = SleekPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Text("Numerical Substitution:", color = SlateTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = substitution,
                            color = ThemeTextColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickProvenanceFooter(
    rColumn: String,
    resolvedR: Double,
    resolvedX: Double,
    baseAmpacityA: Double?,
    dataSource: String,
    citation: String
) {
    Surface(
        color = DarkBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Data Provenance & Engineering Standards",
                    fontWeight = FontWeight.Bold,
                    color = ThemeTextColor,
                    fontSize = 12.sp
                )
            }
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AC Resistance (R):", color = SlateTextSecondary, fontSize = 11.sp)
                Text("${String.format(java.util.Locale.US, "%.4f", resolvedR)} Ω/km ($rColumn)", color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reactance (X):", color = SlateTextSecondary, fontSize = 11.sp)
                Text("${String.format(java.util.Locale.US, "%.4f", resolvedX)} Ω/km (50 Hz)", color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (baseAmpacityA != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Base Rating (Iz):", color = SlateTextSecondary, fontSize = 11.sp)
                    Text("${String.format(java.util.Locale.US, "%.1f", baseAmpacityA)} A", color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Data Source:", color = SlateTextSecondary, fontSize = 11.sp)
                Text(dataSource, color = SleekPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = citation,
                color = SlateTextSecondary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
