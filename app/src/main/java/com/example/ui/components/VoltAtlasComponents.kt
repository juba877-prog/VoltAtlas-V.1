package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.sld.SldValidationStatus

// --- Centralized Color Getters ---
val ThemeBackground: Color
    @Composable get() = if (LocalThemeIsDark.current) Background else LightBackground

val ThemeBackgroundDeep: Color
    @Composable get() = if (LocalThemeIsDark.current) BackgroundDeep else LightBackgroundDeep

val ThemeSurface: Color
    @Composable get() = if (LocalThemeIsDark.current) Surface else LightSurface

val ThemeSurfaceHigh: Color
    @Composable get() = if (LocalThemeIsDark.current) SurfaceHigh else LightSurfaceHigh

val ThemeSurfaceSelected: Color
    @Composable get() = if (LocalThemeIsDark.current) SurfaceSelected else LightSurfaceSelected

val ThemeBorderSubtle: Color
    @Composable get() = if (LocalThemeIsDark.current) BorderSubtle else LightBorderSubtle

val ThemeBorderStrong: Color
    @Composable get() = if (LocalThemeIsDark.current) BorderStrong else LightBorderStrong

val ThemePrimary: Color
    @Composable get() = if (LocalThemeIsDark.current) PrimaryBlue else LightPrimaryBlue

val ThemeCyan: Color
    @Composable get() = if (LocalThemeIsDark.current) PrimaryCyan else LightPrimaryCyan

val ThemeTextPrimary: Color
    @Composable get() = if (LocalThemeIsDark.current) TextPrimary else LightTextPrimary

val ThemeTextSecondary: Color
    @Composable get() = if (LocalThemeIsDark.current) TextSecondary else LightTextSecondary

val ThemeTextTertiary: Color
    @Composable get() = if (LocalThemeIsDark.current) TextTertiary else LightTextTertiary

val ThemeTextDisabled: Color
    @Composable get() = if (LocalThemeIsDark.current) TextDisabled else LightTextDisabled

val ThemeDivider: Color
    @Composable get() = if (LocalThemeIsDark.current) Divider else LightDivider

// --- Standard Shared Components ---

@Composable
fun VoltAtlasScreenScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = ThemeBackgroundDeep,
        contentColor = ThemeTextPrimary,
        content = content
    )
}

@Composable
fun VoltAtlasTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ThemeSurface,
        border = BorderStroke(1.dp, ThemeBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = ScreenTitle,
                color = ThemeTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )
        }
    }
}

@Composable
fun VoltAtlasSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = SectionTitle,
            color = ThemePrimary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        HorizontalDivider(color = ThemeDivider)
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun VoltAtlasCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ThemeBorderSubtle,
    containerColor: Color = ThemeSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(VoltAtlasShapes.card),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(VoltAtlasBorders.subtle, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(VoltAtlasSpacing.lg),
            content = content
        )
    }
}

@Composable
fun VoltAtlasMetricCard(
    label: String,
    value: String,
    unit: String,
    statusText: String? = null,
    statusColor: Color? = null,
    modifier: Modifier = Modifier
) {
    VoltAtlasCard(
        modifier = modifier.defaultMinSize(minHeight = VoltAtlasResponsiveDimensions.metricCardMinHeight),
        containerColor = ThemeSurface
    ) {
        Text(
            text = label,
            style = Label,
            color = ThemeTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(VoltAtlasSpacing.xs))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(VoltAtlasSpacing.xs)
        ) {
            Text(
                text = value,
                style = EngineeringValue,
                color = ThemeTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = unit,
                style = EngineeringUnit,
                color = ThemeTextTertiary,
                modifier = Modifier.padding(bottom = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (statusText != null && statusColor != null) {
            Spacer(modifier = Modifier.height(VoltAtlasSpacing.xs))
            VoltAtlasStatusBadge(text = statusText, color = statusColor)
        }
    }
}

fun statusColor(status: String): Color {
    val upper = status.uppercase()
    return when {
        upper.contains("WARN") || upper.contains("REVIEW") -> SleekWarning
        upper.contains("FAIL") || upper.contains("INVALID") || upper.contains("ERROR") -> SleekFail
        upper.contains("PASS") || upper.contains("COMPLIANT") -> SleekPass
        upper.contains("OK") || upper.contains("NORMAL") || upper.contains("CALCULATED") -> StatusNeutral
        else -> SlateTextSecondary
    }
}

/**
 * Step 7 (Light-Mode Correction): theme-aware variant of [statusColor].
 * Dark Mode resolves to the exact legacy pixels of [statusColor]; Light Mode
 * swaps in the darker saturated PASS/WARNING/FAIL variants that meet WCAG AA
 * on light surfaces.
 */
@Composable
fun themeStatusColor(status: String): Color {
    val upper = status.uppercase()
    return when {
        upper.contains("WARN") || upper.contains("REVIEW") -> ThemeStatusWarning
        upper.contains("FAIL") || upper.contains("INVALID") || upper.contains("ERROR") -> ThemeStatusFail
        upper.contains("PASS") || upper.contains("COMPLIANT") -> ThemeStatusPass
        upper.contains("OK") || upper.contains("NORMAL") || upper.contains("CALCULATED") -> ThemeStatusNeutral
        else -> ThemeTextSecondary
    }
}

fun statusSymbol(status: String): String {
    val upper = status.uppercase()
    return when {
        upper.contains("WARN") || upper.contains("REVIEW") -> "⚠"
        upper.contains("PASS") || upper.contains("COMPLIANT") || upper.contains("OK") -> "✓"
        upper.contains("FAIL") || upper.contains("INVALID") || upper.contains("ERROR") -> "✗"
        else -> "•"
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    VoltAtlasStatusBadge(
        text = status,
        color = themeStatusColor(status),
        modifier = modifier
    )
}

@Composable
fun VoltAtlasStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    VoltAtlasStatusBadge(
        text = status,
        color = themeStatusColor(status),
        modifier = modifier
    )
}

@Composable
fun VoltAtlasStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    // SLICE 8C: engineering truth publishes IMMEDIATELY — the badge text and
    // its semantics are never delayed or interpolated. Only decorative
    // container emphasis follows the centralized StatusFeedbackPolicy
    // timeline (once per real change; never on identical recomposition;
    // empty timeline under reduced motion).
    val reduceMotion = LocalReduceMotion.current
    var previousText by remember { mutableStateOf<String?>(null) }
    val borderPulse = remember { Animatable(0f) }

    LaunchedEffect(text) {
        val pulse = StatusFeedbackPolicy.shouldPulse(previousText, text)
        previousText = text
        for (step in StatusFeedbackPolicy.emphasisTimeline(pulse, reduceMotion)) {
            when (step) {
                is StatusFeedbackPolicy.EmphasisStep.Snap -> borderPulse.snapTo(step.value)
                is StatusFeedbackPolicy.EmphasisStep.Tween -> borderPulse.animateTo(
                    targetValue = step.toValue,
                    animationSpec = tween(
                        durationMillis = step.durationMillis,
                        easing = step.easing
                    )
                )
            }
        }
    }

    // Effects-only color transition (FastEffects pacing): the animated value
    // always settles exactly on the approved EngineeringStatusPalette-derived
    // target — no intermediate engineering meaning is fabricated.
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = com.example.ui.theme.VoltAtlasMotion.FastEffects.DurationMillis),
        label = "status_badge_color"
    )

    Box(
        modifier = modifier
            .height(26.dp)
            .clip(CircleShape)
            .background(animatedColor.copy(alpha = 0.15f + 0.10f * borderPulse.value))
            .border(VoltAtlasBorders.subtle, animatedColor.copy(alpha = 0.4f + 0.35f * borderPulse.value), CircleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = StatusText,
            color = animatedColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VoltAtlasStatusStrip(
    status: SldValidationStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        SldValidationStatus.VALID -> ThemeStatusPass
        SldValidationStatus.WARNING -> ThemeStatusWarning
        SldValidationStatus.INVALID -> ThemeStatusFail
        SldValidationStatus.DISCONNECTED -> EngineeringNeutral
        // Dark keeps the legacy StatusInfo pixel; Light uses the deep brand blue.
        SldValidationStatus.DATA_REQUIRED -> if (LocalThemeIsDark.current) StatusInfo else ThemePrimary
    }
    Box(
        modifier = modifier
            .width(5.dp)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
fun VoltAtlasPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ThemePrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = ThemeBorderStrong,
            disabledContentColor = ThemeTextDisabled
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, style = Label.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary))
        }
    }
}

@Composable
fun VoltAtlasSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ThemeBorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ThemeTextPrimary,
            disabledContentColor = ThemeTextDisabled
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, style = Label.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun VoltAtlasIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = ThemePrimary,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ThemeSurface)
            .border(1.dp, ThemeBorderSubtle, RoundedCornerShape(8.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else ThemeTextDisabled,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun VoltAtlasTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = Label, color = ThemeTextSecondary) },
            isError = errorText != null,
            readOnly = readOnly,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            textStyle = BodyPrimary.copy(color = ThemeTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ThemeSurface,
                unfocusedContainerColor = ThemeSurface,
                errorContainerColor = ThemeSurface,
                focusedBorderColor = ThemePrimary,
                unfocusedBorderColor = ThemeBorderSubtle,
                errorBorderColor = StatusFail
            ),
            shape = RoundedCornerShape(8.dp)
        )
        if (errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = errorText, style = Caption, color = StatusFail)
        }
    }
}

@Composable
fun VoltAtlasNumberField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    errorText: String? = null
) {
    var rawText by remember(value) { mutableStateOf(value?.toString() ?: "") }
    
    VoltAtlasTextField(
        value = rawText,
        onValueChange = { input ->
            rawText = input
            val doubleVal = input.toDoubleOrNull()
            if (input.isEmpty()) {
                onValueChange(null)
            } else if (doubleVal != null) {
                onValueChange(doubleVal)
            }
        },
        label = label,
        modifier = modifier,
        errorText = errorText,
        trailingIcon = {
            Text(
                text = unit,
                style = EngineeringUnit,
                color = ThemeTextTertiary,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoltAtlasDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label, style = Label, color = ThemeTextSecondary) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = errorText != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                textStyle = BodyPrimary.copy(color = ThemeTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ThemeSurface,
                    unfocusedContainerColor = ThemeSurface,
                    errorContainerColor = ThemeSurface,
                    focusedBorderColor = ThemePrimary,
                    unfocusedBorderColor = ThemeBorderSubtle
                ),
                shape = RoundedCornerShape(8.dp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(ThemeSurface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = BodyPrimary, color = ThemeTextPrimary) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = errorText, style = Caption, color = StatusFail)
        }
    }
}

@Composable
fun VoltAtlasDataRow(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = BodySecondary,
            color = ThemeTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = EngineeringValue,
                color = ThemeTextPrimary
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = EngineeringUnit,
                    color = ThemeTextTertiary
                )
            }
        }
    }
}

@Composable
fun VoltAtlasEmptyState(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = ThemeTextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = BodyPrimary,
                color = ThemeTextSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                VoltAtlasPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun VoltAtlasErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = StatusFail,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = SectionTitle,
                color = ThemeTextPrimary,
                textAlign = TextAlign.Center
            )
            VoltAtlasPrimaryButton(text = "Retry", onClick = onRetry)
        }
    }
}

@Composable
fun VoltAtlasLoadingState(
    message: String = "Loading engineering data..."
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = ThemePrimary)
            Text(
                text = message,
                style = BodyPrimary,
                color = ThemeTextSecondary
            )
        }
    }
}

@Composable
fun VoltAtlasModuleCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VoltAtlasCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(ThemeSurfaceHigh, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ThemePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = CardTitle, color = ThemeTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = Caption, color = ThemeTextSecondary)
            }
            Text(text = "→", color = ThemeTextTertiary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VoltAtlasModuleCardCompact(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        shape = RoundedCornerShape(VoltAtlasShapes.card),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = ThemeSurface),
        border = androidx.compose.foundation.BorderStroke(VoltAtlasBorders.subtle, ThemeBorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(ThemeSurfaceHigh, shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = ThemePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Go to $title",
                    tint = ThemeTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = CardTitle.copy(fontSize = 14.sp),
                    color = ThemeTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = Caption.copy(fontSize = 11.sp),
                    color = ThemeTextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VoltAtlasCallout(
    title: String,
    chipText: String,
    body: String,
    modifier: Modifier = Modifier,
    statusColor: Color = SleekWarning
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(statusColor.copy(alpha = 0.08f))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = CardTitle.copy(fontSize = 14.sp), color = ThemeTextPrimary)
                VoltAtlasStatusBadge(text = chipText, color = statusColor)
            }
            Text(text = body, style = BodyPrimary.copy(fontSize = 13.sp, lineHeight = 18.sp), color = ThemeTextSecondary)
        }
    }
}

@Composable
fun VoltAtlasCountChip(
    count: Int,
    label: String,
    color: Color = ThemeTextPrimary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(ThemeSurface, shape = RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, shape = RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                style = EngineeringValue.copy(fontSize = 18.sp),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label.uppercase(),
                style = Label.copy(fontSize = 10.sp, letterSpacing = 0.04.sp),
                color = ThemeTextSecondary
            )
        }
    }
}

@Composable
fun VoltAtlasProgressBar(
    progress: Float,
    color: Color = SleekPass,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BorderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedProgress)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
fun VoltAtlasToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = CardTitle.copy(fontSize = 14.sp), color = ThemeTextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, style = Caption, color = ThemeTextSecondary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = SleekPrimary,
                uncheckedThumbColor = ThemeTextSecondary,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}

@Composable
fun VoltAtlasStatusBanner(
    title: String = "PROJECT VALIDATION STATUS",
    statusText: String = "100% Compliant",
    badgeText: String = "PASS",
    statusColor: Color = SleekPass,
    modifier: Modifier = Modifier
) {
    VoltAtlasCard(
        modifier = modifier,
        containerColor = ThemeSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title.uppercase(),
                    style = Label.copy(fontSize = 12.sp, letterSpacing = 0.06.sp),
                    color = ThemeTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = ScreenTitle.copy(fontSize = 16.sp),
                    color = statusColor
                )
            }
            VoltAtlasStatusBadge(text = badgeText, color = statusColor)
        }
    }
}

