package com.example.sld

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showModeDropdown by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val palette = SldTheme.palette(isDarkTheme)
    val cardBg = palette.surface
    val chipBg = if (isDarkTheme) Color(0xFF0D1117) else Color(0xFFF1F5F9)
    val borderColor = if (isDarkTheme) Color(0xFF30363D) else Color(0xFFCBD5E1)
    val textColor = palette.textPrimary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Display Mode Selector Dropdown
            Box {
                Surface(
                    onClick = { showModeDropdown = true },
                    shape = RoundedCornerShape(8.dp),
                    color = chipBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.height(36.dp).testTag("sld_mode_dropdown_trigger")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val currentModeLabel = when (displayMode) {
                            DisplayMode.OVERVIEW -> "Overview"
                            DisplayMode.CABLES -> "Cables"
                            DisplayMode.VOLTAGE_DROP -> "Voltage Drop"
                            DisplayMode.SHORT_CIRCUIT -> "Short Circuit"
                            DisplayMode.VALIDATION -> "Validation"
                        }
                        Text(
                            text = currentModeLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Mode",
                            tint = SleekPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showModeDropdown,
                    onDismissRequest = { showModeDropdown = false },
                    modifier = Modifier.background(cardBg).testTag("sld_mode_dropdown_menu")
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
                            }
                        )
                    }
                }
            }

            // Quick Actions: Search, Issues, Fit, Fullscreen, Layout Mode, Overflow
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Toggle Button
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isSearchOpen) SleekPrimary.copy(alpha = 0.2f) else chipBg,
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("sld_search_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Network",
                        tint = if (isSearchOpen) SleekPrimary else textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Validation Issues Badge Toggle
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isValidationOpen) SleekWarning.copy(alpha = 0.2f) else chipBg)
                        .clickable { onToggleValidation() }
                        .padding(horizontal = 8.dp)
                        .testTag("sld_validation_toggle_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Validation Issues",
                            tint = if (issueCount > 0) SleekWarning else SleekPass,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (issueCount > 0) "Req. ($issueCount)" else "OK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                // IEC Symbols Library Button (Prominent)
                IconButton(
                    onClick = onOpenSymbolGallery,
                    modifier = Modifier
                        .size(36.dp)
                        .background(SleekPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .testTag("open_symbol_gallery_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "IEC Symbol Library",
                        tint = SleekPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Fit Project Button
                IconButton(
                    onClick = onFitScreen,
                    modifier = Modifier
                        .size(36.dp)
                        .background(chipBg, RoundedCornerShape(8.dp))
                        .testTag("sld_fit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Fit Project",
                        tint = SleekPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Layout Mode Toggle Button
                IconButton(
                    onClick = onToggleLayoutMode,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (layoutModeEnabled) SleekPrimary.copy(alpha = 0.25f) else chipBg,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Toggle Layout Mode",
                        tint = if (layoutModeEnabled) SleekPrimary else textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Fullscreen Toggle Button
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isFullscreen) SleekPrimary.copy(alpha = 0.2f) else chipBg,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                        tint = SleekPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Export Image Button
                IconButton(
                    onClick = onExportImage,
                    modifier = Modifier
                        .size(36.dp)
                        .background(chipBg, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Export Image",
                        tint = SleekPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Export DXF (AutoCAD) Button - Prominent & Directly Visible
                Surface(
                    onClick = onExportDxf,
                    shape = RoundedCornerShape(8.dp),
                    color = SleekPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("export_dxf_toolbar_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export DXF",
                            tint = SleekPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "DXF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekPrimary
                        )
                    }
                }

                // Overflow Menu Button
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkBackground, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = ThemeTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(DarkCardBg)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export DXF (AutoCAD)", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = SleekPrimary) },
                            onClick = {
                                onExportDxf()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("IEC Symbol Library", fontSize = 12.sp, color = ThemeTextColor) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = SleekPrimary) },
                            onClick = {
                                onOpenSymbolGallery()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fit Selection", fontSize = 12.sp, color = ThemeTextColor) },
                            leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = SleekPrimary) },
                            onClick = {
                                onFitSelection()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset View", fontSize = 12.sp, color = ThemeTextColor) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = SleekPrimary) },
                            onClick = {
                                onResetView()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Expand All", fontSize = 12.sp, color = ThemeTextColor) },
                            leadingIcon = { Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = SleekPrimary) },
                            onClick = {
                                onExpandAll()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Collapse All", fontSize = 12.sp, color = ThemeTextColor) },
                            leadingIcon = { Icon(Icons.Default.UnfoldLess, contentDescription = null, tint = SleekPrimary) },
                            onClick = {
                                onCollapseAll()
                                showOverflowMenu = false
                            }
                        )
                        if (layoutModeEnabled) {
                            DropdownMenuItem(
                                text = { Text("Reset Layout to Auto", fontSize = 12.sp, color = ThemeTextColor) },
                                leadingIcon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = SleekFail) },
                                onClick = {
                                    onResetLayout()
                                    showOverflowMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
