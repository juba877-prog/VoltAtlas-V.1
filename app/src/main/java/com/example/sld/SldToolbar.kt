package com.example.sld

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.example.ui.*

@Composable
fun SldToolbar(
    displayMode: DisplayMode,
    onDisplayModeChange: (DisplayMode) -> Unit,
    issueCount: Int,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    isValidationOpen: Boolean,
    onToggleValidation: () -> Unit,
    onFitScreen: () -> Unit,
    onFitSelection: () -> Unit,
    onResetView: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onExportImage: () -> Unit,
    onExportDxf: () -> Unit = {},
    onOpenSymbolGallery: () -> Unit = {},
    layoutModeEnabled: Boolean = false,
    onToggleLayoutMode: () -> Unit = {},
    onResetLayout: () -> Unit = {},
    isDarkTheme: Boolean = com.example.ui.theme.LocalThemeIsDark.current,
    modifier: Modifier = Modifier
) {
    // State logic for the Draggable Radial FAB
    var isExpanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Dropdown states
    var showModeDropdown by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Theme & Colors
    val palette = SldTheme.palette(isDarkTheme)
    val cardBg = palette.surface
    val chipBg = if (isDarkTheme) Color(0xFF0D1117) else Color(0xFFF1F5F9)
    val borderColor = if (isDarkTheme) Color(0xFF30363D) else Color(0xFFCBD5E1)
    val textColor = palette.textPrimary

    // Animation progress for radial items popping out
    val animationProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "radial_expansion"
    )

    // Radial configuration
    // Adjacent centers are 22.5deg apart and each control is an axis-aligned 48dp
    // touch square. Non-overlap of the actual touch rectangles therefore requires
    // max(|cos(a)-cos(a+22.5deg)|, |sin(a)-sin(a+22.5deg)|) * R >= 48dp. The
    // largest projected delta is cos(292.5deg)-cos(315deg) = 0.3244 -> R >= 148dp.
    // 160dp is chosen for rounding/anti-aliasing margin; the fan still fits any
    // 360dp-wide screen because the toolbar is anchored BottomStart.
    val radiusDp = 160.dp
    val radiusPx = with(LocalDensity.current) { radiusDp.toPx() }

    // Helper function to calculate exact radial positions
    fun Modifier.radialOffset(angle: Double): Modifier {
        val offsetX = (cos(angle) * radiusPx).toFloat()
        val offsetY = (sin(angle) * radiusPx).toFloat()
        return this
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .alpha(animationProgress)
    }

    Box(
        modifier = modifier
            // Apply drag offset constraint
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) },
        contentAlignment = Alignment.Center
    ) {
        // --- RADIAL NODES (Fanning Up and Right) ---
        // Node 0: Mode Selector (Top - 270 degrees)
        Box(modifier = Modifier.radialOffset(Math.PI * 1.5)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        isExpanded = true
                        showModeDropdown = true
                    }
                    .testTag("sld_mode_dropdown_trigger"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = chipBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Modes",
                            tint = SleekPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showModeDropdown,
                onDismissRequest = { showModeDropdown = false },
                modifier = Modifier
                    .background(cardBg)
                    .testTag("sld_mode_dropdown_menu")
            ) {
                DisplayMode.values().forEach { mode ->
                    val label = when (mode) {
                        DisplayMode.OVERVIEW -> "Overview"
                        DisplayMode.CABLES -> "Cables"
                        DisplayMode.VOLTAGE_DROP -> "Voltage Drop"
                        DisplayMode.SHORT_CIRCUIT -> "Short Circuit"
                        DisplayMode.VALIDATION -> "Validation"
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (displayMode == mode) FontWeight.Bold else FontWeight.Normal,
                                color = if (displayMode == mode) SleekPrimary else textColor
                            )
                        },
                        onClick = {
                            onDisplayModeChange(mode)
                            showModeDropdown = false
                            isExpanded = false
                        }
                    )
                }
            }
        }

        // Node 1: Search Toggle (Top-Right 292.5 degrees)
        Box(modifier = Modifier.radialOffset(Math.PI * 1.625)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        onToggleSearch()
                        isExpanded = false
                    }
                    .testTag("sld_search_toggle_btn"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSearchOpen) SleekPrimary.copy(alpha = 0.2f) else chipBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchOpen) SleekPrimary else textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Node 2: Validation Issues (Up-Right 315 degrees)
        Box(modifier = Modifier.radialOffset(Math.PI * 1.75)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        onToggleValidation()
                        isExpanded = false
                    }
                    .testTag("sld_validation_toggle_btn"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isValidationOpen) SleekWarning.copy(alpha = 0.2f) else chipBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BadgedBox(
                            badge = {
                                if (issueCount > 0) {
                                    Badge(containerColor = SleekWarning) {
                                        Text("$issueCount", color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Validation",
                                tint = if (issueCount > 0) SleekWarning else SleekPass,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Node 3: Fit Project (Right-Up 337.5 degrees)
        Box(modifier = Modifier.radialOffset(Math.PI * 1.875)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        onFitScreen()
                        isExpanded = false
                    }
                    .testTag("sld_fit_btn"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = chipBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Fit Project",
                            tint = SleekPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Node 4: Overflow Actions (Right - 0 degrees)
        Box(modifier = Modifier.radialOffset(0.0)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        isExpanded = true
                        showOverflowMenu = true
                    }
                    .testTag("sld_overflow_btn"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = DarkBackground,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = ThemeTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
                modifier = Modifier.background(DarkCardBg)
            ) {
                // Group 1: Export Actions
                DropdownMenuItem(
                    text = { Text("Export DXF (AutoCAD)", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onExportDxf()
                        showOverflowMenu = false
                        isExpanded = false
                    },
                    modifier = Modifier.testTag("export_dxf_toolbar_btn")
                )
                DropdownMenuItem(
                    text = { Text("Export Image", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onExportImage()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )

                HorizontalDivider(color = borderColor)

                // Group 2: Tools & Placeholder Actions
                DropdownMenuItem(
                    text = { Text("IEC Symbol Library", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onOpenSymbolGallery()
                        showOverflowMenu = false
                        isExpanded = false
                    },
                    modifier = Modifier.testTag("open_symbol_gallery_btn")
                )
                DropdownMenuItem(
                    text = { Text("Toggle Layout Mode", fontSize = 12.sp, color = if (layoutModeEnabled) SleekPrimary else ThemeTextColor) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (layoutModeEnabled) SleekPrimary else ThemeTextColor) },
                    onClick = {
                        onToggleLayoutMode()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )

                HorizontalDivider(color = borderColor)

                // Group 3: View Controls
                DropdownMenuItem(
                    text = { Text(if (isFullscreen) "Exit Fullscreen" else "Fullscreen", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onToggleFullscreen()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Fit Selection", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onFitSelection()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reset View", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onResetView()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Expand All", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onExpandAll()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Collapse All", fontSize = 12.sp, color = ThemeTextColor) },
                    leadingIcon = { Icon(Icons.Default.UnfoldLess, contentDescription = null, tint = SleekPrimary) },
                    onClick = {
                        onCollapseAll()
                        showOverflowMenu = false
                        isExpanded = false
                    }
                )
                if (layoutModeEnabled) {
                    DropdownMenuItem(
                        text = { Text("Reset Layout to Auto", fontSize = 12.sp, color = SleekFail) },
                        leadingIcon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = SleekFail) },
                        onClick = {
                            onResetLayout()
                            showOverflowMenu = false
                            isExpanded = false
                        }
                    )
                }
            }
        }

        // --- CENTRAL DRAGGABLE FAB ---
        var isDragging by remember { mutableStateOf(false) }
        Surface(
            shape = CircleShape,
            color = SleekPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(56.dp)
                .pointerInput(isExpanded) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            if (!isExpanded) {
                                change.consume()
                                dragOffset += dragAmount
                            }
                        }
                    )
                }
                .clickable {
                    if (!isDragging) {
                        isExpanded = !isExpanded
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Rotating + icon morphing visually into an X when opened
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 45f else 0f,
                    label = "fab_rotation"
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isExpanded) "Close Toolbar" else "Open Toolbar",
                    tint = Color.White,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}
