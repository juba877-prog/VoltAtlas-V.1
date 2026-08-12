package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sld.SldValidationStatus
import com.example.ui.theme.ElectricalIcons
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.engine.*
import com.example.ui.components.statusColor
import com.example.ui.components.statusSymbol
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.sld.SldScreen
import com.example.ui.screens.FormulasMethodologyScreen
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch



private data class CableFamilyInfo(
    val originalType: String,
    val material: String,
    val armourGroup: String,
    val exactArmour: String,
    val coresConfig: String,
    val insulation: String,
    val displayLabel: String
)

private data class MetricDisplayItem(
    val label: String,
    val value: String,
    val unit: String,
    val statusText: String? = null,
    val statusColor: Color? = null
)



private fun formatCatalogCableSizeSafe(size: Double): String {
    val value = if (size % 1.0 == 0.0) size.toInt().toString() else size.toString()
    return "$value mm\u00B2"
}

private fun parseCableSizeSafe(value: String?): Double? {
    val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""")
        .find(value.orEmpty())

    return match
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
}

private fun catalogCableSizeOptions(manufacturer: String, cableType: String): List<String> {
    val typeCode = Catalog.cableTypes
        .firstOrNull { it.cableType.equals(cableType, ignoreCase = true) }
        ?.code ?: return emptyList()

    return Catalog.cableCatalog
        .asSequence()
        .filter { it.mfg.equals(manufacturer.trim(), ignoreCase = true) }
        .filter { it.typeCode == typeCode }
        .map { it.size }
        .distinct()
        .sorted()
        .map(::formatCatalogCableSizeSafe)
        .toList()
}



fun isMoreTabSelected(currentTab: Int): Boolean {
    return currentTab in listOf(4, 5, 6, 7, 8, 9)
}

@Composable
fun MainLayout(viewModel: EngineeringViewModel) {
    val state by viewModel.uiState.collectAsState()

    val activeId by viewModel.activeProjectId.collectAsState()
    val projects by viewModel.projects.collectAsState()

    val showGlobalSettings by viewModel.showGlobalSettings.collectAsState()
    val confirmDestructiveActions by viewModel.preferences.confirmDestructiveActions.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(0) }

    val hasCompletedFirstLaunch by viewModel.preferences.hasCompletedFirstLaunch.collectAsState()
    var showWelcomeSheet by remember { mutableStateOf(!hasCompletedFirstLaunch) }

    LaunchedEffect(hasCompletedFirstLaunch) {
        if (!hasCompletedFirstLaunch) {
            showWelcomeSheet = true
        }
    }

    if (showWelcomeSheet) {
        com.example.ui.components.WelcomeGuideSheet(
            onDismiss = {
                viewModel.preferences.setHasCompletedFirstLaunch(true)
                showWelcomeSheet = false
            },
            onNavigateToMethodology = {
                viewModel.preferences.setHasCompletedFirstLaunch(true)
                showWelcomeSheet = false
                viewModel.closeGlobalSettings()
                currentTab = 11
            }
        )
    }

    var showPdfExportDialog by rememberSaveable { mutableStateOf(false) }
    val pdfScope = rememberCoroutineScope()
    var currentReportOptions by remember { mutableStateOf(com.example.pdf.ReportOptions()) }

    val createPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            pdfScope.launch {
                viewModel.exportPdf(uri, currentReportOptions)
            }
        }
    }

    if (showGlobalSettings) {
        GlobalSettingsScreen(
            viewModel = viewModel,
            preferences = viewModel.preferences,
            onBack = { viewModel.closeGlobalSettings() }
        )
        return
    }

    if (activeId == null) {
        ProjectPickerScreen(
            projects = projects,
            onCreateProject = { name -> viewModel.createAndOpenProject(name) },
            onOpenProject = { id -> viewModel.openProject(id) },
            onRenameProject = { id, newName -> viewModel.renameProject(id, newName) },
            onDeleteProject = { id -> viewModel.deleteProject(id) },
            onExportProjectJson = { id -> viewModel.exportProjectJson(id) },
            onImportProjectJson = { jsonStr, callback -> viewModel.importProjectJson(jsonStr, callback) },
            onOpenSettings = { viewModel.openGlobalSettings() },
            confirmDestructiveActions = confirmDestructiveActions
        )

        return
    }

    val activeProject = projects.find { it.projectId == activeId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportActiveProject by remember { mutableStateOf<ProjectEntity?>(null) }

    val exportActiveProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val proj = pendingExportActiveProject
        if (uri != null && proj != null) {
            scope.launch {
                try {
                    val jsonStr = viewModel.exportProjectJson(proj.projectId)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Exported '${proj.projectName}' successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    pendingExportActiveProject = null
                }
            }
        } else {
            pendingExportActiveProject = null
        }
    }

    fun launchExportActive(project: ProjectEntity) {
        pendingExportActiveProject = project
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val safeName = project.projectName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val defaultFileName = "${safeName}_${dateStr}.json"
        exportActiveProjectLauncher.launch(defaultFileName)
    }
    val projectName = activeProject?.projectName ?: "VoltAtlas"

    
    var panelScheduleReturnTab by rememberSaveable { mutableStateOf(9) }
    var isSldFullscreen by rememberSaveable { mutableStateOf(false) }
    val sldManualPositions = remember(activeProject?.projectId) { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
    var showNewProjectConfirmation by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticsDialog by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf("Dashboard", "Circuits", "Transformers", "Power Systems", "Protection", "SLD", "Conduits", "BOQ / Procurement", "Settings")

    // ── Hierarchical Back-Button Handler ──
    // Priority: (1) close overlay → (2) Panel Schedule return / More child→More grid → (3) any non-Home→Home → (4) Home→exit dialog
    BackHandler(enabled = true) {
        when {
            // 1. Close any open overlay / dialog / fullscreen first
            showPdfExportDialog -> showPdfExportDialog = false
            showDiagnosticsDialog -> showDiagnosticsDialog = false
            showNewProjectConfirmation -> showNewProjectConfirmation = false
            showExitDialog -> showExitDialog = false
            isSldFullscreen -> isSldFullscreen = false
            // 2. Panel Schedule (12) returns to entry point (Circuits or More grid)
            currentTab == 12 -> currentTab = panelScheduleReturnTab
            // 3. More-children (4,5,6,7,8,10,11) → More grid (9)
            currentTab in listOf(4, 5, 6, 7, 8, 10, 11) -> currentTab = 9
            // 4. More grid (9) or direct nav items (1,2,3) → Home (0)
            currentTab != 0 -> currentTab = 0
            // 5. Already on Home → show exit confirmation
            else -> showExitDialog = true
        }
    }

    val reduceMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) { false }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val useNavRail = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp >= 600

    // Short labels for bottom nav (phone); NavigationRail uses full labels
    val navItems = listOf(
        Triple("Home", ElectricalIcons.Home, 0),
        Triple("Circuits", ElectricalIcons.Circuits, 1),
        Triple("Tx", ElectricalIcons.Transformer, 2),
        Triple("Power", ElectricalIcons.Power, 3),
        Triple("More", Icons.Default.MoreHoriz, 9)
    )
    // Full labels for NavigationRail (tablet)
    val navItemsFull = listOf(
        Triple("Dashboard", ElectricalIcons.Home, 0),
        Triple("Circuits", ElectricalIcons.Circuits, 1),
        Triple("Transformers", ElectricalIcons.Transformer, 2),
        Triple("Power", ElectricalIcons.Power, 3),
        Triple("More", Icons.Default.MoreHoriz, 9)
    )

    val moduleName = when (currentTab) {
        0 -> "Dashboard"
        1 -> "Circuits"
        2 -> "Transformers"
        3 -> "Power Systems"
        4 -> "Protection"
        5 -> "SLD"
        6 -> "Conduits"
        7 -> "BOQ / Procurement"
        8 -> "Settings"
        9 -> "More Modules"
        10 -> "Conversions"
        11 -> "Methodology & Formulas"
        12 -> "Panel Schedule"
        else -> "Module"
    }

    // Safety redirection if old saved tab index points to obsolete Panels or Lighting
    val safeTab = if (currentTab in tabs.indices || currentTab in 9..12) currentTab else 0
    if (currentTab != safeTab) {
        currentTab = safeTab
    }

    if (currentTab == 5 && isSldFullscreen) {
        val uiStateVal = state
        if (uiStateVal is UiState.Success) {
            SldScreen(
                state = uiStateVal,
                activeProjectId = activeProject?.projectId ?: "",
                projectName = projectName,
                isFullscreen = true,
                onToggleFullscreen = { isSldFullscreen = it },
                onBackToProjects = { viewModel.closeProject() },
                onNavigateToTab = { currentTab = it },
                manualPositions = sldManualPositions,
                modifier = Modifier.fillMaxSize().statusBarsPadding()
            )
            return
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useNavRail) {
            NavigationRail(
                containerColor = DarkCardBg,
                header = {
                    IconButton(
                        onClick = { viewModel.closeProject() },
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(DarkBackground, shape = RoundedCornerShape(10.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Projects",
                                tint = SleekPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    navItemsFull.forEach { (title, icon, targetTab) ->
                        val isSelected = if (targetTab == 9) {
                            isMoreTabSelected(currentTab)
                        } else {
                            currentTab == targetTab
                        }
                        val isCustomVector = icon == ElectricalIcons.Home ||
                                icon == ElectricalIcons.Circuits ||
                                icon == ElectricalIcons.Transformer ||
                                icon == ElectricalIcons.Power
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = {
                                currentTab = targetTab
                            },
                            icon = {
                                Icon(
                                    icon,
                                    contentDescription = title,
                                    tint = if (isCustomVector) Color.Unspecified else (if (isSelected) SleekPrimary else SlateTextSecondary)
                                )
                            },
                            label = { Text(title, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = SleekPrimary,
                                selectedTextColor = SleekPrimary,
                                unselectedIconColor = SlateTextSecondary,
                                indicatorColor = SleekPrimary.copy(alpha = 0.12f),
                                unselectedTextColor = SlateTextSecondary
                            )
                        )
                    }
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                BoxWithConstraints(
                    modifier = Modifier
                        .statusBarsPadding()
                        .background(DarkCardBg)
                        .border(BorderStroke(1.dp, BorderColor))
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val width = maxWidth
                    val showSingleDirectAction = width < 360.dp
                    val showTwoDirectActions = width >= 360.dp && width < 600.dp
                    val showAllDirectActions = width >= 600.dp

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!useNavRail) {
                            IconButton(
                                onClick = { viewModel.closeProject() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(DarkBackground, shape = RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Projects",
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = projectName,
                                color = ThemeTextColor,
                                style = ScreenTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = moduleName,
                                color = SlateTextSecondary,
                                style = Caption.copy(fontSize = 11.sp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val diagnostics = Catalog.getDiagnostics()
                        val isAllOk = diagnostics.isCableCatalogLoaded && 
                                     diagnostics.isBreakerCatalogLoaded && 
                                     diagnostics.isAccessoryCatalogLoaded && 
                                     diagnostics.isDataSheetLoaded && 
                                     diagnostics.isDeratingDataLoaded
                        
                        val currentThemeMode by viewModel.themeMode.collectAsState()
                        val isDarkThemeActive = when (currentThemeMode) {
                            "LIGHT" -> false
                            "DARK" -> true
                            else -> isSystemInDarkTheme()
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (showAllDirectActions) {
                                IconButton(
                                    onClick = { showDiagnosticsDialog = true },
                                    modifier = Modifier.size(48.dp).testTag("diagnostics_indicator_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isAllOk) Icons.Default.CloudQueue else Icons.Default.Storage,
                                        contentDescription = "Catalog Status",
                                        tint = if (isAllOk) SleekPass else SleekFail,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (showTwoDirectActions || showAllDirectActions) {
                                IconButton(
                                    onClick = {
                                        if (isDarkThemeActive) {
                                            viewModel.preferences.setThemeMode("LIGHT")
                                        } else {
                                            viewModel.preferences.setThemeMode("DARK")
                                        }
                                    },
                                    modifier = Modifier.size(48.dp).testTag("theme_toggle_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkThemeActive) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = if (isDarkThemeActive) "Switch to Light Mode" else "Switch to Dark Mode",
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    currentReportOptions = com.example.pdf.ReportOptions(isCompleteProjectReport = true, selectedSections = listOf("Executive Summary", "Circuits & Cable Sizing", "Transformers", "Power Systems", "Protection & Short Circuit", "Conduits", "BOQ & Procurement"))
                                    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                    val fileName = "VoltAtlas_${projectName.replace(" ", "_")}_Complete_Project_Report_${dateStr}.pdf"
                                    showPdfExportDialog = true
                                },
                                modifier = Modifier.size(48.dp).testTag("pdf_export_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Export PDF",
                                    tint = SleekPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (showAllDirectActions) {
                                IconButton(
                                    onClick = { viewModel.recalculate() },
                                    modifier = Modifier.size(48.dp).testTag("recalculate_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = "Recalculate without resetting data",
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            var showOverflowMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.size(48.dp).testTag("top_bar_overflow_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor = DarkCardBg
                                ) {
                                    if (!showAllDirectActions) {
                                        DropdownMenuItem(
                                            text = { Text("Catalog Diagnostics", color = ThemeTextColor) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isAllOk) Icons.Default.CloudQueue else Icons.Default.Storage,
                                                    contentDescription = "Connection status",
                                                    tint = if (isAllOk) SleekPass else SleekFail
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                showDiagnosticsDialog = true
                                            },
                                            modifier = Modifier.testTag("menu_diagnostics_btn")
                                        )
                                    }

                                    if (showSingleDirectAction) {
                                        DropdownMenuItem(
                                            text = { Text(if (isDarkThemeActive) "Light Mode" else "Dark Mode", color = ThemeTextColor) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isDarkThemeActive) Icons.Default.LightMode else Icons.Default.DarkMode,
                                                    contentDescription = "Toggle theme",
                                                    tint = SleekPrimary
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                if (isDarkThemeActive) {
                                                    viewModel.preferences.setThemeMode("LIGHT")
                                                } else {
                                                    viewModel.preferences.setThemeMode("DARK")
                                                }
                                            },
                                            modifier = Modifier.testTag("menu_theme_toggle_btn")
                                        )
                                    }

                                    if (!showAllDirectActions) {
                                        DropdownMenuItem(
                                            text = { Text("Recalculate", color = ThemeTextColor) },
                                            leadingIcon = { Icon(Icons.Default.Calculate, contentDescription = "Recalculate", tint = SleekPrimary) },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.recalculate()
                                            },
                                            modifier = Modifier.testTag("menu_recalculate_btn")
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text("Export Project (JSON)", color = ThemeTextColor) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = "Export Project", tint = SleekPrimary) },
                                        onClick = {
                                            showOverflowMenu = false
                                            val proj = activeProject
                                            if (proj != null) {
                                                launchExportActive(proj)
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_export_project_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = ThemeTextColor) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SleekPrimary) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.openGlobalSettings()
                                        },
                                        modifier = Modifier.testTag("menu_settings_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Active Project", color = ThemeTextColor) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Clear Active Project", tint = SleekFail) },
                                        onClick = {
                                            showOverflowMenu = false
                                            if (confirmDestructiveActions) {
                                                showNewProjectConfirmation = true
                                            } else {
                                                viewModel.startNewEmptyProject()
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_clear_project_btn")
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = DarkCardBg,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { (title, icon, targetTab) ->
                            val isSelected = if (targetTab == 9) {
                                isMoreTabSelected(currentTab)
                            } else {
                                currentTab == targetTab
                            }
                            val fullLabel = navItemsFull.find { it.third == targetTab }?.first ?: title
                            val isCustomVector = icon == ElectricalIcons.Home ||
                                    icon == ElectricalIcons.Circuits ||
                                    icon == ElectricalIcons.Transformer ||
                                    icon == ElectricalIcons.Power
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    currentTab = targetTab
                                },
                                icon = {
                                    Icon(
                                        icon,
                                        contentDescription = fullLabel,
                                        tint = if (isCustomVector) Color.Unspecified else (if (isSelected) SleekPrimary else SlateTextSecondary)
                                    )
                                },
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SleekPrimary,
                                    selectedTextColor = SleekPrimary,
                                    unselectedIconColor = SlateTextSecondary,
                                    unselectedTextColor = SlateTextSecondary,
                                    indicatorColor = SleekPrimary.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            },
            containerColor = ThemeBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Central State Container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                when (val uiStateVal = state) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = SleekPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading engineering catalogs...", color = ThemeTextColor)
                            }
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Error: ${uiStateVal.message}", color = SleekFail, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UiState.Success -> {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                if (reduceMotion) {
                                    fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
                                } else {
                                    val isForward = targetState > initialState
                                    if (isForward) {
                                        (fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                                            initialOffsetX = { fullWidth -> fullWidth / 12 }
                                        )) togetherWith (
                                            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                targetOffsetX = { fullWidth -> -fullWidth / 12 }
                                            )
                                        )
                                    } else {
                                        (fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                                            initialOffsetX = { fullWidth -> -fullWidth / 12 }
                                        )) togetherWith (
                                            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                targetOffsetX = { fullWidth -> fullWidth / 12 }
                                            )
                                        )
                                    }
                                }
                            },
                            label = "MainTabSwitchAnimation"
                        ) { targetTab ->
                            when (targetTab) {
                                0 -> DashboardScreen(uiStateVal, onNavigate = { currentTab = it })
                                1 -> CircuitsGridScreen(uiStateVal, viewModel, onNavigateToPanelSchedule = { circId -> viewModel.selectedCircuitId.value = circId; panelScheduleReturnTab = 1; currentTab = 12 })
                                2 -> TransformersScreen(uiStateVal, viewModel)
                                3 -> {
                                    val genSet = viewModel.generatorSettings.collectAsState().value ?: com.example.data.GeneratorEntity()
                                    val atsSet = viewModel.atsSettings.collectAsState().value ?: com.example.data.AtsEntity()
                                    val upsSet = viewModel.upsSettings.collectAsState().value ?: com.example.data.UpsEntity()
                                    val pfGlob = viewModel.pfGlobalSettings.collectAsState().value ?: com.example.data.PfGlobalEntity()
                                    val pfTrans = viewModel.pfTransformerSettings.collectAsState().value
                                    PowerSystemsScreen(
                                        state = uiStateVal,
                                        generatorSettings = genSet,
                                        atsSettings = atsSet,
                                        upsSettings = upsSet,
                                        pfGlobalSettings = pfGlob,
                                        pfTransformerSettings = pfTrans,
                                        onSaveGenerator = { viewModel.saveGeneratorSettings(it) },
                                        onSaveAts = { viewModel.saveAtsSettings(it) },
                                        onSaveUps = { viewModel.saveUpsSettings(it) },
                                        onSavePfGlobal = { viewModel.savePfGlobalSettings(it) },
                                        onSavePfTransformer = { viewModel.savePfTransformerSettings(it) }
                                    )
                                }
                                4 -> ProtectionScreen(uiStateVal, onSaveEarthing = { viewModel.onSaveEarthing(it) })
                                5 -> SldScreen(
                                    circuits = uiStateVal.circuits,
                                    activeProjectId = viewModel.activeProjectId.value ?: "",
                                    sizingResults = uiStateVal.sizingResults,
                                    shortCircuitResults = uiStateVal.shortCircuitResults,
                                    atsEntity = uiStateVal.atsEntity,
                                    transformers = uiStateVal.transformers,
                                    transformerResults = uiStateVal.transformerResults,
                                    generatorResult = uiStateVal.generatorResult,
                                    atsResult = uiStateVal.atsResult
                                )
                                6 -> ConduitFillScreen(viewModel)
                                7 -> BoqProcurementScreen(uiStateVal)
                                8 -> {
                                    GlobalSettingsScreen(
                                        viewModel = viewModel,
                                        preferences = viewModel.preferences,
                                        onBack = { currentTab = 0 }
                                    )
                                }
                                9 -> MoreModulesGrid(onNavigate = { targetTab -> if (targetTab == 12) panelScheduleReturnTab = 9; currentTab = targetTab })
                                10 -> ConversionsContainerScreen(onBack = { currentTab = 9 })
                                11 -> FormulasMethodologyScreen()
                                12 -> com.example.ui.screens.PanelScheduleScreen(
                                    viewModel = viewModel,
                                    uiState = uiStateVal,
                                    onBack = { currentTab = panelScheduleReturnTab }
                                )
                                else -> DashboardScreen(uiStateVal, onNavigate = { currentTab = it })
                            }
                        }
                    }

                }
            }
        }
    }
    }

    if (showNewProjectConfirmation) {
        AlertDialog(
            onDismissRequest = { showNewProjectConfirmation = false },
            title = { Text("Start New Empty Project?", color = ThemeTextColor) },
            text = {
                Text(
                    "This will delete the current circuits and transformers from Room. This action cannot be undone.",
                    color = SlateTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewProjectConfirmation = false
                        viewModel.startNewEmptyProject()
                    }
                ) {
                    Text("Delete and Start", color = SleekWarning)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectConfirmation = false }) {
                    Text("Cancel", color = ThemeTextColor)
                }
            },
            containerColor = DarkCardBg
        )
    }

    // ── Exit Confirmation Dialog (dark-themed, matches app identity) ──
    if (showExitDialog) {
        val activity = LocalContext.current as? android.app.Activity
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showExitDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                SleekPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Exit",
                            tint = SleekPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Exit VoltAtlas?",
                        color = SleekPrimary,
                        style = SectionTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Message
                    Text(
                        text = "Are you sure you want to exit the app?",
                        color = SlateTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel — subtle outline button
                        OutlinedButton(
                            onClick = { showExitDialog = false },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("Cancel", color = ThemeTextColor, fontSize = 14.sp)
                        }

                        // Exit — accent/danger button
                        Button(
                            onClick = {
                                showExitDialog = false
                                activity?.finishAffinity()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SleekFail
                            )
                        ) {
                            Text("Exit", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        val diagnostics = Catalog.getDiagnostics()
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Database Icon",
                        tint = SleekPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Catalog Diagnostics", color = ThemeTextColor)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Verifying JSON assets and specifications parsed in memory:",
                        color = SlateTextSecondary,
                        fontSize = 13.sp
                    )
                    
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    DiagnosticRow(label = "cable_types.json", loaded = Catalog.cableTypes.isNotEmpty(), count = Catalog.cableTypes.size)
                    DiagnosticRow(label = "data_sheet.json", loaded = Catalog.dataSheet.isNotEmpty(), count = Catalog.dataSheet.size)
                    DiagnosticRow(label = "cable_catalog.json", loaded = Catalog.cableCatalog.isNotEmpty(), count = Catalog.cableCatalog.size)
                    DiagnosticRow(label = "breaker_catalog.json", loaded = Catalog.breakers.isNotEmpty(), count = Catalog.breakers.size)
                    DiagnosticRow(label = "accessory_catalog.json", loaded = Catalog.accessories.isNotEmpty(), count = Catalog.accessories.size)
                    DiagnosticRow(label = "derating_data.json", loaded = Catalog.deratingData != null)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Catalog Integrity Metrics (Status: ${diagnostics.status})", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Defined Families: ${diagnostics.definedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Supported Families: ${diagnostics.supportedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Unsupported Families: ${diagnostics.unsupportedFamilies}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Cable Records: ${diagnostics.cableRecords}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Missing R/X: ${diagnostics.missingRx}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Duplicate Keys: ${diagnostics.duplicateKeys}", color = ThemeTextColor, fontSize = 12.sp)
                    Text("Orphan Type Codes: ${diagnostics.orphanTypeCodes}", color = ThemeTextColor, fontSize = 12.sp)
                    
                    val successState = state as? UiState.Success
                    val shop1Circuit = successState?.circuits?.firstOrNull { it.circuitName.trim().equals("SHOP-1", ignoreCase = true) }
                    val shop1Result = successState?.sizingResults?.get(shop1Circuit?.id)
                    
                    if (successState != null && shop1Circuit != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                        Text("SHOP-1 Diagnostic Trace:", color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        val selectedType = Catalog.cableTypes.firstOrNull {
                            it.cableType.trim().equals(shop1Circuit.cableType.trim(), ignoreCase = true)
                        }
                        val typeCode = selectedType?.code ?: -1
                        val candidates = Catalog.cableCatalog.filter {
                            it.mfg.trim().equals(shop1Circuit.manufacturer.trim(), ignoreCase = true) &&
                            it.typeCode == typeCode
                        }
                        val matchingRxCount = Catalog.dataSheet.count { sheet ->
                            candidates.any { c -> kotlin.math.abs(sheet.size - c.size) <= 1e-9 }
                        }
                        
                        Text("• circuit.id: ${shop1Circuit.id}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• circuitName: ${shop1Circuit.circuitName}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• loadKVA: ${shop1Circuit.loadKVA}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• voltageV: ${shop1Circuit.voltageV}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• pf: ${shop1Circuit.pf}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• safetyFactor: ${shop1Circuit.safetyFactor}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• manufacturer: '[${shop1Circuit.manufacturer}]'", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• cableType: '[${shop1Circuit.cableType}]'", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• installation: ${shop1Circuit.installation}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• parallelRuns: ${shop1Circuit.parallelRuns}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• overrideCableSize: ${shop1Circuit.overrideCableSize}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• overrideDeviceSize: ${shop1Circuit.overrideDeviceSize}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• resolved CableTypeEntry code: $typeCode", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• number of manufacturer/type candidates: ${candidates.size}", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• matching R/X row count: $matchingRxCount", color = ThemeTextColor, fontSize = 11.sp)
                        Text("• final failReason: '${shop1Result?.failReason ?: ""}'", color = if (shop1Result?.status?.contains("FAIL") == true) SleekFail else SleekPass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (diagnostics.lastErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Last Error Message:", color = SleekWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SleekFail.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = diagnostics.lastErrorMessage ?: "",
                                color = SleekFail,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SleekPass.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓ ALL ASSETS LOADED SUCCESSFULLY",
                                color = SleekPass,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text("Dismiss", color = SleekPrimary)
                }
            },
            containerColor = DarkCardBg
        )
    }
    if (showPdfExportDialog) {
        val currentProjectId = activeId ?: "DEFAULT_PROJECT"
        val currentProjName = activeProject?.projectName ?: "Project"
        PdfExportDialog(
            projectId = currentProjectId,
            currentProjectName = currentProjName,
            currentClientName = activeProject?.clientName ?: "",
            preferences = viewModel.preferences,
            onDismiss = { showPdfExportDialog = false },
            onExport = { options ->
                showPdfExportDialog = false
                currentReportOptions = options
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                val safeProj = (options.projectName.ifBlank { currentProjName }).replace(" ", "_")
                val fileName = "VoltAtlas_${safeProj}_Complete_Project_Report_${dateStr}.pdf"
                createPdfLauncher.launch(fileName)
            }
        )
    }
}

// 1. DASHBOARD SCREEN
@Composable
fun DashboardScreen(state: UiState.Success, onNavigate: (Int) -> Unit) {
    val summary = state.validationSummary
    val totalCircuits = state.circuits.size

    val maxVdPct = state.sizingResults.values.maxOfOrNull { it.cumulativeVDpct } ?: 0.0
    val maxTxLoadPct = state.transformerResults.values.maxOfOrNull { it.loadingPct } ?: 0.0
    val maxIscKa = state.shortCircuitResults.values.maxOfOrNull { it.iscKA } ?: 0.0

    val overallColor = when {
        summary.totalFailures > 0 -> com.example.ui.theme.StatusFail
        summary.totalReviewsRequired > 0 -> com.example.ui.theme.StatusWarning
        summary.totalWarnings > 0 -> SleekPrimary
        else -> com.example.ui.theme.StatusPass
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
            com.example.ui.components.VoltAtlasCard(
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
                                color = com.example.ui.components.ThemeTextSecondary
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
                            statusColor = if (totalCircuits > 0) SleekPass else null
                        ),
                        MetricDisplayItem(
                            label = "Max Tx Load",
                            value = if (state.transformers.isNotEmpty()) String.format(Locale.US, "%.1f", maxTxLoadPct) else "N/A",
                            unit = if (state.transformers.isNotEmpty()) "%" else "",
                            statusText = if (state.transformers.isEmpty()) null else if (maxTxLoadPct > 100.0) "OVERLOAD" else if (maxTxLoadPct > 0.0) "OK" else null,
                            statusColor = if (state.transformers.isEmpty()) null else if (maxTxLoadPct > 100.0) SleekFail else if (maxTxLoadPct > 0.0) SleekPass else null
                        ),
                        MetricDisplayItem(
                            label = "Max Cum. VD",
                            value = if (state.sizingResults.isNotEmpty()) String.format(Locale.US, "%.2f", maxVdPct) else "N/A",
                            unit = if (state.sizingResults.isNotEmpty()) "%" else "",
                            statusText = if (state.sizingResults.isEmpty()) null else if (maxVdPct > 5.0) "EXCEEDED" else if (maxVdPct > 0.0) "OK" else null,
                            statusColor = if (state.sizingResults.isEmpty()) null else if (maxVdPct > 5.0) SleekFail else if (maxVdPct > 0.0) SleekPass else null
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
                                    com.example.ui.components.VoltAtlasMetricCard(
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
                                com.example.ui.components.VoltAtlasMetricCard(
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
                                        com.example.ui.components.VoltAtlasMetricCard(
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
                                        com.example.ui.components.VoltAtlasMetricCard(
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
                            SldValidationStatus.INVALID -> com.example.ui.theme.StatusFail
                            SldValidationStatus.DATA_REQUIRED -> com.example.ui.theme.StatusWarning
                            else -> SleekPrimary
                        }
                        val issueIcon = when (issue.severity) {
                            SldValidationStatus.INVALID -> Icons.Default.Error
                            else -> Icons.Default.Warning
                        }

                        com.example.ui.components.VoltAtlasCard(
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
                            // Row 3: severity badge on its own line
                            Spacer(modifier = Modifier.height(4.dp))
                            com.example.ui.components.VoltAtlasStatusBadge(
                                text = issue.severity.name.replace("_", " "),
                                color = itemColor
                            )
                        }
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
                            modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            modules.forEach { (title, subtitle, iconAndTab) ->
                                val (icon, targetTab) = iconAndTab
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    com.example.ui.components.VoltAtlasModuleCardCompact(
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
                                com.example.ui.components.VoltAtlasModuleCard(
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
                                modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (i in 0..1) {
                                    val (title, subtitle, iconAndTab) = modules[i]
                                    val (icon, targetTab) = iconAndTab
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        com.example.ui.components.VoltAtlasModuleCardCompact(
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
                                modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (i in 2..3) {
                                    val (title, subtitle, iconAndTab) = modules[i]
                                    val (icon, targetTab) = iconAndTab
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        com.example.ui.components.VoltAtlasModuleCardCompact(
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



// 2. TRANSFORMERS SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformersScreen(state: UiState.Success, viewModel: EngineeringViewModel) {
    val txId by viewModel.selectedTransformerId.collectAsState()
    val activeTx = state.transformers.firstOrNull { it.transformerId == txId } ?: state.transformers.firstOrNull()
    val result = activeTx?.let { state.transformerResults[it.transformerId] }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeTx == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = ElectricalIcons.Transformer,
                            contentDescription = "No Transformers",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Transformer Yet",
                            color = ThemeTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No transformer yet. Add one from a circuit's Fed From, or here. Its size is calculated from the loads you assign.",
                            color = SlateTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.addNewTransformerInline()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add Transformer", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            // Dropdown selection row with Add (+) support
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Transformer:", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.transformers.forEach { tx ->
                            val isSelected = tx.transformerId == txId
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectedTransformerId.value = tx.transformerId },
                                label = { Text(tx.transformerId, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SleekPrimary,
                                    containerColor = DarkCardBg
                                ),
                                modifier = Modifier.testTag("tx_tab_${tx.transformerId}")
                            )
                        }
                    }
                    
                    // Button to add an additional transformer
                    IconButton(
                        onClick = {
                            viewModel.addNewTransformerInline()
                        },
                        modifier = Modifier.size(32.dp).testTag("add_additional_tx_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Transformer",
                            tint = SleekPrimary
                        )
                    }
                }
            }
        }

        if (result != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeTx.transformerName, color = SleekPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("Assigned circuits: ${result.circuitsAssigned}", color = SlateTextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (state.transformers.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.deleteTransformer(activeTx) },
                                    modifier = Modifier.testTag("delete_tx_btn_${activeTx.transformerId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Transformer",
                                        tint = SleekFail
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "${String.format("%.1f", result.loadingPct)}%",
                                color = if (result.loadingPct <= 100) SleekPass else SleekFail,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Insulation Type", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(activeTx.txType, color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Connected Total Load", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.connectedLoad)} kVA", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Max Active Demand", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.demandLoad)} kW", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("FLC (Incomer Rating)", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.flc)} A", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Recommended Size", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.0f", result.recommendedSize)} kVA", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Operating Size", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.0f", result.selectedSize)} kVA", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Impedance Zt (%Z / mΩ)", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", activeTx.percentZ)}% (${String.format("%.2f", result.zt)} mΩ)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Isc 3-Phase", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.isc3Phase)} kA", color = SleekWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Isc 1-Phase", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${String.format("%.2f", result.isc1Phase)} kA", color = SleekWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Interactive Override Settings Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TRANSFORMER SPECIFICATIONS & OVERRIDES", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. Transformer Type Selection (Oil-Immersed vs Dry Type Cast Resin)
                        Text("Transformer Insulation Type:", color = SlateTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val types = listOf("Dry Type (Cast Resin)", "Oil-Immersed")
                            types.forEach { type ->
                                val isSelected = activeTx.txType.contains(if (type.contains("Oil")) "Oil" else "Dry", ignoreCase = true) ||
                                                 activeTx.txType.trim().equals(type.trim(), ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val defaultZ = com.example.engine.TransformerEngine.getDefaultPercentZ(type, activeTx.kVA)
                                        viewModel.updateTransformer(
                                            activeTx.copy(txType = type, percentZ = defaultZ)
                                        )
                                    },
                                    label = { Text(type, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SleekPrimary,
                                        containerColor = DarkCardBg
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Capacity Sizing Mode (Auto vs Manual)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-recommend capacity", color = ThemeTextColor, fontSize = 13.sp)
                            Switch(
                                checked = activeTx.sizeMode == "Auto",
                                onCheckedChange = { isChecked ->
                                    viewModel.updateTransformer(
                                        activeTx.copy(sizeMode = if (isChecked) "Auto" else "Manual")
                                    )
                                },
                                modifier = Modifier.testTag("tx_mode_switch")
                            )
                        }

                        if (activeTx.sizeMode == "Manual") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Manual Capacity Rating (kVA):", color = SlateTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            var capacityExpanded by remember { mutableStateOf(false) }
                            val iecRatings = listOf(50.0, 100.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0)

                            ExposedDropdownMenuBox(
                                expanded = capacityExpanded,
                                onExpandedChange = { capacityExpanded = !capacityExpanded }
                            ) {
                                OutlinedTextField(
                                    value = "${activeTx.manualKVA.toInt()} kVA",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = capacityExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SleekPrimary,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = ThemeTextColor,
                                        unfocusedTextColor = ThemeTextColor
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = capacityExpanded,
                                    onDismissRequest = { capacityExpanded = false }
                                ) {
                                    iecRatings.forEach { rating ->
                                        DropdownMenuItem(
                                            text = { Text("${rating.toInt()} kVA") },
                                            onClick = {
                                                capacityExpanded = false
                                                val defaultZ = com.example.engine.TransformerEngine.getDefaultPercentZ(activeTx.txType, rating)
                                                viewModel.updateTransformer(
                                                    activeTx.copy(manualKVA = rating, kVA = rating, percentZ = defaultZ)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Editable %Z Impedance Field
                        Text("Impedance %Z (Standard or Nameplate):", color = SlateTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        var zText by remember(activeTx.percentZ) { mutableStateOf(activeTx.percentZ.toString()) }
                        OutlinedTextField(
                            value = zText,
                            onValueChange = { input ->
                                zText = input
                                val parsed = input.toDoubleOrNull()
                                if (parsed != null && parsed > 0.0) {
                                    viewModel.updateTransformer(activeTx.copy(percentZ = parsed))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = { Text("%", color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Auto-set per IEC 60076 / EN 50588-1 tables. Editable for real nameplate %Z.",
                            color = SlateTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// 3. CIRCUITS MASTER GRID SCREEN
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CircuitsGridScreen(
    state: UiState.Success,
    viewModel: EngineeringViewModel,
    onNavigateToPanelSchedule: ((Int) -> Unit)? = null
) {
    val query by viewModel.circuitSearchQuery.collectAsState()
    val statusFilter by viewModel.circuitStatusFilter.collectAsState()
    val parentFilter by viewModel.circuitParentFilter.collectAsState()
    val selectedId by viewModel.selectedCircuitId.collectAsState()

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var editingCircuit by rememberSaveable { mutableStateOf<CircuitEntity?>(null) }

    // Perform queries and filters
    val filteredCircuits = state.circuits.filter { circ ->
        val matchesQuery = query.isBlank() ||
            circ.circuitName.contains(query, ignoreCase = true) ||
            circ.fedFrom.contains(query, ignoreCase = true) ||
            circ.cableType.contains(query, ignoreCase = true) ||
            circ.loadType.contains(query, ignoreCase = true) ||
            (circ.overrideCableSize ?: "").contains(query, ignoreCase = true)

        val matchesStatus = when (statusFilter) {
            "PASS" -> state.sizingResults[circ.id]?.status?.contains("PASS") == true
            "FAIL" -> state.sizingResults[circ.id]?.status?.contains("FAIL") == true
            "WARNING" -> state.sizingResults[circ.id]?.status?.contains("WARN") == true
            else -> true
        }

        val matchesParent = when (parentFilter) {
            "Transformer" -> circ.fedFrom.equals("Transformer", ignoreCase = true)
            "Sub-feeders" -> !circ.fedFrom.equals("Transformer", ignoreCase = true)
            else -> true
        }

        matchesQuery && matchesStatus && matchesParent
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            TextField(
                value = query,
                onValueChange = { viewModel.circuitSearchQuery.value = it },
                placeholder = { Text("Search circuits by name, parent, cable, or load...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_circuits_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkCardBg,
                    unfocusedContainerColor = DarkCardBg,
                    focusedTextColor = ThemeTextColor,
                    unfocusedTextColor = ThemeTextColor
                )
            )

            // Dynamic Filter Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tier filter chip (All -> Transformer -> Sub-feeders)
                val isParentFiltered = parentFilter != "All"
                FilterChip(
                    selected = isParentFiltered,
                    onClick = {
                        viewModel.circuitParentFilter.value = when (parentFilter) {
                            "All" -> "Transformer"
                            "Transformer" -> "Sub-feeders"
                            else -> "All"
                        }
                    },
                    label = {
                        Text(
                            text = "Tier: $parentFilter",
                            color = ThemeTextColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SleekPrimary,
                        containerColor = DarkCardBg
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Status filter chip (All -> PASS -> WARNING -> FAIL)
                val isStatusFiltered = statusFilter != "All"
                val statusChipBg = when (statusFilter) {
                    "PASS" -> SleekPass
                    "FAIL" -> SleekFail
                    "WARNING" -> SleekWarning
                    else -> DarkCardBg
                }
                FilterChip(
                    selected = isStatusFiltered,
                    onClick = {
                        viewModel.circuitStatusFilter.value = when (statusFilter) {
                            "All" -> "PASS"
                            "PASS" -> "WARNING"
                            "WARNING" -> "FAIL"
                            else -> "All"
                        }
                    },
                    label = {
                        Text(
                            text = "Status: $statusFilter",
                            color = if (isStatusFiltered) MaterialTheme.colorScheme.onPrimary else ThemeTextColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = statusChipBg,
                        containerColor = DarkCardBg
                    ),
                    modifier = Modifier.weight(1f)
                )
            }


            // Horizontal Grid/List of circuits
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (filteredCircuits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No circuits found. Click '+' to add your first circuit.",
                                color = SlateTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(filteredCircuits, key = { it.id }) { circ ->
                    val result = state.sizingResults[circ.id]
                    val isSelected = selectedId == circ.id
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectedCircuitId.value = if (isSelected) null else circ.id }
                            .testTag("circuit_card_${circ.circuitName}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) if (LocalThemeIsDark.current) SelectedTabContainerDark else SelectedTabContainerLight else DarkCardBg
                        ),
                        border = BorderStroke(1.dp, if (isSelected) SleekPrimary else BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(circ.circuitName, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fed from: ${circ.fedFrom}", color = SlateTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    Text("Load: ${circ.loadKVA} kVA | ${circ.phases}", color = SlateTextSecondary, fontSize = 12.sp, maxLines = 1)
                                    
                                    val childCircuits = state.circuits.filter { it.fedFrom.trim().equals(circ.circuitName.trim(), ignoreCase = true) }
                                    val hasChildren = childCircuits.isNotEmpty()
                                    val childrenTotalLoad = childCircuits.sumOf { it.loadKVA }
                                    val childrenDiffers = Math.abs(circ.loadKVA - childrenTotalLoad) > 0.001
                                    val isUnconfirmedConflict = hasChildren && circ.directLoadConfirmed == null && (circ.loadKVA > 0.0 || childrenDiffers)
                                    
                                    if (isUnconfirmedConflict) {
                                        Surface(
                                            onClick = {
                                                viewModel.selectedCircuitId.value = circ.id
                                                onNavigateToPanelSchedule?.invoke(circ.id)
                                            },
                                            color = StatusWarning.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .testTag("unconfirmed_conflict_badge_${circ.circuitName}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Load conflict warning",
                                                    tint = StatusWarning,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Superseded by ${Math.round(childrenTotalLoad * 10.0) / 10.0} kVA from children — tap to review in Panel Schedule",
                                                    color = StatusWarning,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    
                                    val selectedType = Catalog.cableTypes.firstOrNull {
                                        it.cableType.trim().equals(circ.cableType.trim(), ignoreCase = true)
                                    }
                                    val isUnsupported = selectedType != null && Catalog.cableCatalog.none { it.typeCode == selectedType.code }
                                    if (isUnsupported) {
                                        Text(
                                            text = "Catalog data is not available for this cable family.",
                                            color = SleekWarning,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp).testTag("unsupported_warning_${circ.circuitName}")
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (result != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = result.cableSize_mm2,
                                            color = SleekPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = statusSymbol(result.status),
                                            color = statusColor(result.status),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                            // Expanded detailing with interactive overrides and CRUD edit/delete buttons
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(200)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
                            ) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    HorizontalDivider(color = BorderColor)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // CRUD Quick Control Panel
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Circuit Operations:", color = SlateTextSecondary, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(
                                            onClick = {
                                                editingCircuit = circ
                                                showDialog = true
                                            },
                                            modifier = Modifier.testTag("edit_circuit_btn_${circ.circuitName}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Circuit", tint = SleekPrimary)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteCircuit(circ)
                                            },
                                            modifier = Modifier.testTag("delete_circuit_btn_${circ.circuitName}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Circuit", tint = SleekFail)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (result != null) {
                                        // Sizing Details
                                        Text("Sizing Specifications", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Design Current (Ib)", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${String.format("%.2f", result.Ib_A)} A", color = ThemeTextColor, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Derating Factor", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${String.format("%.3f", result.cTotalDerating)}", color = ThemeTextColor, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Device & Type", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${result.deviceSize_A.toInt()} A (${result.deviceType})", color = ThemeTextColor, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Voltage Drop (V / %)", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text("${String.format("%.2f", result.voltDrop_V)} V (${String.format("%.2f", result.vdPct)}%)", color = ThemeTextColor, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cumulative VD %", color = SlateTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text(
                                                text = "${String.format("%.2f", result.cumulativeVDpct)}% / ${circ.maxVDpct * 100}%",
                                                color = statusColor(result.status),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }

                                        if (result.failReason.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = "Failure reason:",
                                                color = SleekFail,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                text = result.failReason,
                                                color = ThemeTextColor,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = BorderColor)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Interactive Manual Overrides Panel
                                        Text("MANUAL OVERRIDES PANEL", color = SleekWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Mode Indicator & Reset to Auto Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isOverrideActive = circ.overrideDeviceSize != null || circ.overrideCableSize != null || circ.overrideDeviceType != null
                                            Text(
                                                text = if (isOverrideActive) "MODE: OVERRIDE ACTIVE" else "MODE: AUTOMATIC SIZING",
                                                color = if (isOverrideActive) SleekWarning else SleekPass,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.updateCircuit(
                                                        circ.copy(
                                                            overrideDeviceSize = null,
                                                            overrideCableSize = null,
                                                            overrideDeviceType = null
                                                        )
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.testTag("reset_to_auto_btn_${circ.circuitName}")
                                            ) {
                                                Text("Reset to Auto", color = ThemeTextColor, fontSize = 11.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 1. Device Type Dropdown via ExposedDropdownMenuBox
                                        var deviceTypeDropdownExpanded by remember { mutableStateOf(false) }
                                        val selectedDeviceTypeStr = circ.overrideDeviceType ?: "Auto (${result.deviceType})"

                                        ExposedDropdownMenuBox(
                                            expanded = deviceTypeDropdownExpanded,
                                            onExpandedChange = { deviceTypeDropdownExpanded = !deviceTypeDropdownExpanded },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedDeviceTypeStr,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Protection Device Type Override", color = SlateTextSecondary, fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceTypeDropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                    focusedContainerColor = DarkBackground,
                                                    unfocusedContainerColor = DarkBackground,
                                                    focusedBorderColor = SleekPrimary,
                                                    unfocusedBorderColor = BorderColor,
                                                    focusedLabelColor = SleekPrimary,
                                                    unfocusedLabelColor = SlateTextSecondary,
                                                    focusedTextColor = ThemeTextColor,
                                                    unfocusedTextColor = ThemeTextColor
                                                ),
                                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_device_type_btn")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = deviceTypeDropdownExpanded,
                                                onDismissRequest = { deviceTypeDropdownExpanded = false },
                                                modifier = Modifier.background(DarkCardBg).heightIn(max = 280.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto (Recommended ${result.deviceType})", color = SleekPass, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    onClick = {
                                                        viewModel.updateCircuit(circ.copy(overrideDeviceType = null))
                                                        deviceTypeDropdownExpanded = false
                                                    }
                                                )
                                                listOf("MCB", "MCCB", "ACB", "Switch-Disconnector").forEach { typeOption ->
                                                    DropdownMenuItem(
                                                        text = { Text(typeOption, color = ThemeTextColor, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateCircuit(circ.copy(overrideDeviceType = typeOption))
                                                            deviceTypeDropdownExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("device_type_option_$typeOption")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // 2. Device Rating Dropdown via ExposedDropdownMenuBox
                                        var deviceDropdownExpanded by remember { mutableStateOf(false) }
                                        val selectedDeviceRatingStr = if (circ.overrideDeviceSize != null) "${circ.overrideDeviceSize.toInt()} A" else "Auto (${result.deviceSize_A.toInt()} A)"

                                        ExposedDropdownMenuBox(
                                            expanded = deviceDropdownExpanded,
                                            onExpandedChange = { deviceDropdownExpanded = !deviceDropdownExpanded },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedDeviceRatingStr,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Protection Device Rating Override", color = SlateTextSecondary, fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                    focusedContainerColor = DarkBackground,
                                                    unfocusedContainerColor = DarkBackground,
                                                    focusedBorderColor = SleekPrimary,
                                                    unfocusedBorderColor = BorderColor,
                                                    focusedLabelColor = SleekPrimary,
                                                    unfocusedLabelColor = SlateTextSecondary,
                                                    focusedTextColor = ThemeTextColor,
                                                    unfocusedTextColor = ThemeTextColor
                                                ),
                                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_breaker_btn")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = deviceDropdownExpanded,
                                                onDismissRequest = { deviceDropdownExpanded = false },
                                                modifier = Modifier.background(DarkCardBg).heightIn(max = 280.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto (Recommended ${result.deviceSize_A.toInt()} A)", color = SleekPass, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    onClick = {
                                                        viewModel.updateCircuit(circ.copy(overrideDeviceSize = null))
                                                        deviceDropdownExpanded = false
                                                    }
                                                )
                                                CableSizingEngine.STANDARD_RATINGS.forEach { rating ->
                                                    DropdownMenuItem(
                                                        text = { Text("${rating.toInt()} A", color = ThemeTextColor, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateCircuit(circ.copy(overrideDeviceSize = rating))
                                                            deviceDropdownExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("breaker_option_${rating.toInt()}A")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // 2. Cable Size Dropdown via ExposedDropdownMenuBox
                                        var cableDropdownExpanded by remember { mutableStateOf(false) }
                                        val catalogSizes = catalogCableSizeOptions(circ.manufacturer, circ.cableType)
                                        val selectedCableSizeStr = circ.overrideCableSize ?: "Auto (${result.cableSize_mm2})"

                                        ExposedDropdownMenuBox(
                                            expanded = cableDropdownExpanded,
                                            onExpandedChange = { if (catalogSizes.isNotEmpty()) cableDropdownExpanded = !cableDropdownExpanded },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = selectedCableSizeStr,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Cable Cross-Section Override", color = SlateTextSecondary, fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cableDropdownExpanded) },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                                    focusedContainerColor = DarkBackground,
                                                    unfocusedContainerColor = DarkBackground,
                                                    focusedBorderColor = SleekPrimary,
                                                    unfocusedBorderColor = BorderColor,
                                                    focusedLabelColor = SleekPrimary,
                                                    unfocusedLabelColor = SlateTextSecondary,
                                                    focusedTextColor = ThemeTextColor,
                                                    unfocusedTextColor = ThemeTextColor
                                                ),
                                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("override_cable_btn")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = cableDropdownExpanded,
                                                onDismissRequest = { cableDropdownExpanded = false },
                                                modifier = Modifier.background(DarkCardBg).heightIn(max = 280.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Auto (Recommended ${result.cableSize_mm2})", color = SleekPass, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    onClick = {
                                                        viewModel.updateCircuit(circ.copy(overrideCableSize = null))
                                                        cableDropdownExpanded = false
                                                    }
                                                )
                                                catalogSizes.forEach { sizeStr ->
                                                    DropdownMenuItem(
                                                        text = { Text(sizeStr, color = ThemeTextColor, fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.updateCircuit(circ.copy(overrideCableSize = sizeStr))
                                                            cableDropdownExpanded = false
                                                        },
                                                        modifier = Modifier.testTag("cable_option_$sizeStr")
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Apply Override Button Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            FilledTonalButton(
                                                onClick = { viewModel.recalculate() },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("apply_override_btn_${circ.circuitName}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Apply Override", modifier = Modifier.size(14.dp), tint = ThemeTextColor)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Apply Override", color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (filteredCircuits.size > 50) {
                    item {
                        Text(
                            "Showing all ${filteredCircuits.size} matching circuits.",
                            color = SlateTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Add Floating Action Button for adding new circuits
        FloatingActionButton(
            onClick = {
                editingCircuit = null
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_circuit_fab"),
            containerColor = SleekPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Circuit", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    if (showDialog) {
        CircuitEditDialog(
            circuit = editingCircuit,
            existingCircuits = state.circuits,
            onDismiss = { showDialog = false },
            onSave = { saved ->
                val original = editingCircuit
                val normalized = if (
                    original != null &&
                    (!original.manufacturer.equals(saved.manufacturer, ignoreCase = true) ||
                        !original.cableType.equals(saved.cableType, ignoreCase = true))
                ) {
                    saved.copy(overrideCableSize = null)
                } else {
                    saved
                }
                if (normalized.id == 0) {
                    viewModel.addCircuit(normalized)
                } else {
                    viewModel.updateCircuit(normalized)
                }
                showDialog = false
            },
            viewModel = viewModel
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitEditDialog(
    circuit: CircuitEntity?,
    existingCircuits: List<CircuitEntity>,
    onDismiss: () -> Unit,
    onSave: (CircuitEntity) -> Unit,
    viewModel: EngineeringViewModel? = null
) {
    val defaultV3P = viewModel?.preferences?.defaultVoltage3Phase?.collectAsState()?.value ?: 400
    val defaultMaxVdPower = viewModel?.preferences?.maxVdLimitPower?.collectAsState()?.value ?: 5.0
    val defaultConductor = viewModel?.preferences?.defaultConductorMaterial?.collectAsState()?.value ?: "Cu"
    val defaultInsulation = viewModel?.preferences?.defaultInsulation?.collectAsState()?.value ?: "XLPE"
    val defaultCoreConfig = viewModel?.preferences?.defaultCoreConfig?.collectAsState()?.value ?: "4C"

    val transformers = viewModel?.projectTransformersFlow?.collectAsState()?.value ?: emptyList()
    val txOptions = remember(transformers) {
        if (transformers.isEmpty()) listOf("➕ Add Transformer") else transformers.map { it.transformerId } + listOf("➕ Add Another Transformer")
    }
    var expandedTxDropdown by remember { mutableStateOf(false) }

    var name by rememberSaveable { mutableStateOf(circuit?.circuitName ?: "") }
    var loadKVA by rememberSaveable { mutableStateOf(circuit?.loadKVA?.toString() ?: "10.0") }
    var fedFrom by rememberSaveable { mutableStateOf(circuit?.fedFrom ?: "Transformer") }
    var transformerId by rememberSaveable { mutableStateOf(circuit?.transformerId ?: (transformers.firstOrNull()?.transformerId ?: "")) }
    var loadType by rememberSaveable { mutableStateOf(
        when {
            circuit?.generatorIncluded == true && circuit?.upsBacked == true -> "EMERGENCY + UPS"
            circuit?.upsBacked == true -> "UPS"
            circuit?.generatorIncluded == true -> "EMERGENCY"
            else -> "NORMAL"
        }
    ) }
    var phases by rememberSaveable { mutableStateOf(circuit?.phases ?: "3P+N") }
    var voltageV by rememberSaveable { mutableStateOf(circuit?.voltageV?.toString() ?: defaultV3P.toString()) }
    var pf by rememberSaveable { mutableStateOf(circuit?.pf?.toString() ?: "0.85") }
    var safetyFactor by rememberSaveable { mutableStateOf(circuit?.safetyFactor?.toString() ?: "1.0") }
    var manufacturer by rememberSaveable { mutableStateOf(circuit?.manufacturer ?: "Elsewedy") }
    val initialCableType = remember(circuit, defaultConductor, defaultInsulation, defaultCoreConfig) {
        if (circuit != null && circuit.cableType.isNotBlank()) {
            com.example.data.resolveCableType(circuit.cableType, defaultCoreConfig, defaultConductor, defaultInsulation)
        } else {
            com.example.data.resolveCableType("$defaultConductor/$defaultInsulation/PVC", defaultCoreConfig, defaultConductor, defaultInsulation)
        }
    }
    var cableType by rememberSaveable { mutableStateOf(initialCableType) }
    var installation by rememberSaveable { mutableStateOf(circuit?.installation ?: "Laid in Ducts") }
    var parallelRuns by rememberSaveable { mutableStateOf(circuit?.parallelRuns?.toString() ?: "1") }
    var airTempC by rememberSaveable { mutableStateOf(circuit?.airTempC?.toString() ?: "40") }
    var gndTempC by rememberSaveable { mutableStateOf(circuit?.gndTempC?.toString() ?: "40") }
    var burialDepth_m by rememberSaveable { mutableStateOf(circuit?.burialDepth_m?.toString() ?: "0.6") }
    var soilRes by rememberSaveable { mutableStateOf(circuit?.soilRes?.toString() ?: "125") }
    var noCircuits by rememberSaveable { mutableStateOf(circuit?.noCircuits?.toString() ?: "1") }
    var formation by rememberSaveable { mutableStateOf(circuit?.formation ?: "Flat") }
    var spacing by rememberSaveable { mutableStateOf(circuit?.spacing ?: "Touching") }
    var length_m by rememberSaveable { mutableStateOf(circuit?.length_m?.toString() ?: "30") }
    var maxVDpct by rememberSaveable { mutableStateOf(if (circuit != null) (circuit.maxVDpct * 100.0).toString() else defaultMaxVdPower.toString()) }
    var loadNature by rememberSaveable { mutableStateOf(circuit?.loadNature ?: "Static") }
    var startingMethod by rememberSaveable { mutableStateOf(circuit?.startingMethod ?: "DOL") }
    var startingCurrentMultiplier by rememberSaveable { mutableStateOf(circuit?.startingCurrentMultiplier?.toString() ?: "") }
    var isHarmonic by rememberSaveable { mutableStateOf(circuit?.isHarmonic ?: false) }
    var panelType by rememberSaveable { mutableStateOf(circuit?.panelType?.ifEmpty { "LOAD" } ?: "LOAD") }
    var loadCategory by rememberSaveable { mutableStateOf(circuit?.loadCategory) }
    var demandFactor by rememberSaveable { mutableStateOf(circuit?.demandFactor?.toString() ?: "1.0") }

    var busbarRatingA by rememberSaveable { mutableStateOf(circuit?.busbarRatingA?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var mainIncomingType by rememberSaveable { mutableStateOf(circuit?.mainIncomingType ?: "") }
    var mainIncomingCount by rememberSaveable { mutableStateOf(circuit?.mainIncomingCount?.toString() ?: "") }
    var ipRating by rememberSaveable { mutableStateOf(circuit?.ipRating ?: "") }
    var panelLocation by rememberSaveable { mutableStateOf(circuit?.panelLocation ?: "") }
    var cableEntry by rememberSaveable { mutableStateOf(circuit?.cableEntry ?: "") }
    var feederDescription by rememberSaveable { mutableStateOf(circuit?.feederDescription ?: "") }
    var numberOfWays by rememberSaveable { mutableStateOf(circuit?.numberOfWays?.toString() ?: "") }
    var feederType by rememberSaveable { mutableStateOf(circuit?.feederType ?: "Cable") }
    var buswayMfg by rememberSaveable { mutableStateOf(circuit?.buswayMfg ?: "Elsewedy") }
    var buswayModel by rememberSaveable { mutableStateOf(circuit?.buswayModel ?: "Power Link") }
    var isPanelHeaderExpanded by rememberSaveable { mutableStateOf(circuit?.panelType != null && circuit.panelType != "LOAD") }

    val parentOptions = remember(existingCircuits, circuit) {
        val validBoards = if (circuit != null) {
            val boardMap = existingCircuits.associateBy { it.id }
            existingCircuits.filter { candidate ->
                if (candidate.id == circuit.id) return@filter false
                var pId = candidate.parentId
                while (pId != null) {
                    if (pId == circuit.id) return@filter false
                    pId = boardMap[pId]?.parentId
                }
                true
            }
        } else existingCircuits
        val candidatePanels = validBoards.filter { c ->
            val pType = c.panelType?.trim()?.uppercase() ?: ""
            pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER") ||
                    existingCircuits.any { child -> child.parentId == c.id || child.fedFrom.trim().equals(c.circuitName.trim(), ignoreCase = true) }
        }
        listOf("Transformer", "Generator", "Grid", "ATS") + candidatePanels.map { it.circuitName }.distinct()
    }
    val catalogTypeOptions = remember(manufacturer) {
        Catalog.cableTypes.filter { type ->
            Catalog.cableCatalog.any { catalog ->
                catalog.typeCode == type.code && catalog.mfg.trim().equals(manufacturer.trim(), ignoreCase = true)
            }
        }.map { it.cableType }.distinct().sorted()
    }
    val cableTypeOptions = if (catalogTypeOptions.isNotEmpty()) {
        catalogTypeOptions
    } else {
        listOf(cableType)
    }

    // Dropdowns open/close state
    var expandedLoadType by rememberSaveable { mutableStateOf(false) }
    var expandedPhases by rememberSaveable { mutableStateOf(false) }
    var expandedInstallation by rememberSaveable { mutableStateOf(false) }
    var expandedFormation by rememberSaveable { mutableStateOf(false) }
    var expandedSpacing by rememberSaveable { mutableStateOf(false) }
    var expandedFedFrom by rememberSaveable { mutableStateOf(false) }
    var expandedCableType by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (circuit == null) "Add New Circuit" else "Edit Circuit ${circuit.circuitName}",
                color = ThemeTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        containerColor = DarkCardBg,
        text = {
            var isIdentityExpanded by rememberSaveable { mutableStateOf(true) }
            var isLoadExpanded by rememberSaveable { mutableStateOf(true) }
            var isCableExpanded by rememberSaveable { mutableStateOf(false) }
            var isInstallationExpanded by rememberSaveable { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = com.example.ui.theme.StatusFail,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // --- GROUP 1: IDENTITY & SOURCE ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isIdentityExpanded = !isIdentityExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Identity & Source", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isIdentityExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand identity",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isIdentityExpanded) {
                    com.example.ui.components.VoltAtlasTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = "Circuit / Board Name",
                        modifier = Modifier.testTag("input_circuit_name")
                    )

                    com.example.ui.components.VoltAtlasDropdown(
                        value = if (panelType.isEmpty()) "LOAD" else panelType,
                        options = listOf("LOAD", "DB", "SMDB", "MDB", "MCC", "PANEL", "OTHER"),
                        onSelect = { panelType = it },
                        label = "Board Type (Classification)"
                    )

                    com.example.ui.components.VoltAtlasDropdown(
                        value = fedFrom,
                        options = parentOptions,
                        onSelect = { item ->
                            fedFrom = item
                            if (item == "Transformer") {
                                if (transformers.isEmpty()) {
                                    val newTxId = viewModel?.addNewTransformerInline() ?: "TX-01"
                                    transformerId = newTxId
                                } else if (transformerId.isEmpty() || !transformers.any { it.transformerId == transformerId }) {
                                    transformerId = transformers.firstOrNull()?.transformerId ?: ""
                                }
                            } else {
                                val parentCirc = existingCircuits.firstOrNull { it.circuitName == item }
                                if (parentCirc != null) {
                                    transformerId = parentCirc.transformerId
                                }
                            }
                        },
                        label = "Fed From (Parent Source / Board)",
                        modifier = Modifier.testTag("input_fed_from")
                    )

                    val parentBoard = remember(fedFrom, existingCircuits) {
                        existingCircuits.firstOrNull { it.circuitName == fedFrom }
                    }
                    val computedLevel = remember(parentBoard, fedFrom) {
                        if (parentBoard == null) 0 else (parentBoard.level ?: 0) + 1
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Computed Hierarchy Depth:", style = BodySecondary, color = SlateTextSecondary)
                        com.example.ui.components.VoltAtlasStatusBadge(
                            text = "Level $computedLevel ${if (computedLevel == 0) "(Root)" else ""}",
                            color = SleekPrimary
                        )
                    }

                    if (fedFrom == "Transformer") {
                        val selectedTxDisplay = remember(transformers, transformerId) {
                            if (transformers.isEmpty()) {
                                "➕ Add Transformer"
                            } else if (transformerId.isEmpty()) {
                                transformers.firstOrNull()?.transformerId ?: "➕ Add Transformer"
                            } else {
                                transformerId
                            }
                        }

                        com.example.ui.components.VoltAtlasDropdown(
                            value = selectedTxDisplay,
                            options = txOptions,
                            onSelect = { selected ->
                                if (selected == "➕ Add Transformer" || selected == "➕ Add Another Transformer") {
                                    val newTxId = viewModel?.addNewTransformerInline() ?: "TX-01"
                                    transformerId = newTxId
                                } else {
                                    transformerId = selected
                                }
                            },
                            label = "Transformer ID",
                            modifier = Modifier.testTag("input_transformer_id")
                        )
                    }
                }

                // --- GROUP 2: LOAD ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLoadExpanded = !isLoadExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. Load Specifications", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isLoadExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand load",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isLoadExpanded) {
                    val categoryOptions = listOf("Unspecified (None)") + com.example.data.LoadCategories.OPTIONS.map { it.label }
                    val currentCategoryLabel = com.example.data.LoadCategories.findByCode(loadCategory)?.label ?: "Unspecified (None)"

                    com.example.ui.components.VoltAtlasDropdown(
                        value = currentCategoryLabel,
                        options = categoryOptions,
                        onSelect = { selectedLabel ->
                            if (selectedLabel.startsWith("Unspecified")) {
                                loadCategory = null
                            } else {
                                val opt = com.example.data.LoadCategories.OPTIONS.firstOrNull { it.label == selectedLabel }
                                if (opt != null) {
                                    loadCategory = opt.code
                                    pf = opt.defaultPf.toString()
                                    demandFactor = opt.defaultDf.toString()
                                }
                            }
                        },
                        label = "Load Category (Classification & Default Suggestion)"
                    )

                    if (loadCategory == "MTR") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = StatusWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Note: Motor category affects load reporting only. Manually verify breaker curve (C/D) and starting-current cable sizing per IEC 60947-4-1 — this is not yet automated.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    com.example.ui.components.VoltAtlasDropdown(
                        value = loadNature,
                        options = listOf("Static", "Motor"),
                        onSelect = { loadNature = it },
                        label = "Load Nature"
                    )

                    if (loadNature == "Motor") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasDropdown(
                                    value = startingMethod,
                                    options = listOf("DOL", "Star-Delta", "Soft-Starter", "VFD"),
                                    onSelect = { startingMethod = it },
                                    label = "Starting Method"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasTextField(
                                    value = startingCurrentMultiplier,
                                    onValueChange = { startingCurrentMultiplier = it },
                                    label = "Start Current Multiplier",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = loadKVA,
                                onValueChange = { loadKVA = it },
                                label = "Load",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("kVA", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_load_kva")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = pf,
                                onValueChange = { pf = it },
                                label = "Power Factor",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_pf")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = demandFactor,
                                onValueChange = { demandFactor = it },
                                label = "Demand Factor (DF)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_demand_factor")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = voltageV,
                                onValueChange = { voltageV = it },
                                label = "Voltage",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("V", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_voltage")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = safetyFactor,
                                onValueChange = { safetyFactor = it },
                                label = "Safety Factor",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_safety_factor")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasDropdown(
                                value = phases,
                                options = listOf("3P+N", "1P+N", "3P"),
                                onSelect = { phases = it },
                                label = "Phases"
                            )
                        }
                    }

                    com.example.ui.components.VoltAtlasDropdown(
                        value = loadType,
                        options = listOf("NORMAL", "EMERGENCY", "UPS", "EMERGENCY + UPS"),
                        onSelect = { loadType = it },
                        label = "Supply Category (NORMAL / EMERGENCY / UPS)"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = isHarmonic,
                            onCheckedChange = { isHarmonic = it },
                            modifier = Modifier.testTag("checkbox_is_harmonic")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Non-linear / harmonic-heavy load (derates neutral)",
                            style = BodySecondary,
                            color = ThemeTextColor
                        )
                    }
                }

                // --- GROUP 3: CABLE PREFERENCES ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCableExpanded = !isCableExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3. Cable Preferences", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isCableExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand cable",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isCableExpanded) {
                    com.example.ui.components.VoltAtlasDropdown(
                        value = feederType,
                        options = listOf("Cable", "Busway"),
                        onSelect = { feederType = it },
                        label = "Feeder Type",
                        modifier = Modifier.testTag("input_feeder_type")
                    )

                    if (feederType == "Busway") {
                        val buswayMfgOptions = remember {
                            val dynamicList = Catalog.busways.map { it.mfg.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                            if (dynamicList.isEmpty()) listOf("Elsewedy") else dynamicList
                        }
                        com.example.ui.components.VoltAtlasDropdown(
                            value = buswayMfg,
                            options = buswayMfgOptions,
                            onSelect = { buswayMfg = it },
                            label = "Busway Manufacturer",
                            modifier = Modifier.testTag("input_busway_mfg")
                        )

                        val buswayModelOptions = remember(buswayMfg) {
                            val dynamicModels = Catalog.busways
                                .filter { buswayMfg.isBlank() || buswayMfg == "All / Any" || it.mfg.equals(buswayMfg, ignoreCase = true) }
                                .map { it.model.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .sorted()
                            if (dynamicModels.isEmpty()) listOf("All / Any") else (dynamicModels + "All / Any").distinct()
                        }
                        com.example.ui.components.VoltAtlasDropdown(
                            value = buswayModel,
                            options = buswayModelOptions,
                            onSelect = { buswayModel = it },
                            label = "Busway Model (Conductor / Family)",
                            modifier = Modifier.testTag("input_busway_model")
                        )
                    }

                    val manufacturerOptions = remember {
                        val dynamicList = Catalog.cableCatalog
                            .map { it.mfg.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                        if (dynamicList.isEmpty()) listOf("Elsewedy", "IEC-Generic") else dynamicList
                    }

                    com.example.ui.components.VoltAtlasDropdown(
                        value = manufacturer,
                        options = manufacturerOptions,
                        onSelect = { selected ->
                            manufacturer = selected
                            val validTypes = Catalog.cableTypes.filter { type ->
                                Catalog.cableCatalog.any { cat ->
                                    cat.typeCode == type.code && cat.mfg.trim().equals(selected.trim(), ignoreCase = true)
                                }
                            }.map { it.cableType }.distinct().sorted()
                            if (validTypes.isNotEmpty() && !validTypes.contains(cableType)) {
                                cableType = validTypes.firstOrNull() ?: ""
                            }
                        },
                        label = "Manufacturer / Cable Source",
                        modifier = Modifier.testTag("input_manufacturer")
                    )

                    // Progressive Cascading Filter Cable Selector
                    val parseFamilyInfo = remember {
                        { rawType: String ->
                            val resolved = com.example.data.resolveCableType(rawType, defaultCoreConfig, defaultConductor, defaultInsulation)
                            val ct = Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(resolved.trim(), ignoreCase = true) }
                                ?: Catalog.cableTypes.firstOrNull { it.cableType.trim().equals(rawType.trim(), ignoreCase = true) }
                            val cond = ct?.conductor ?: if (rawType.contains("Al", ignoreCase = true)) "Al" else "Cu"
                            val mat = if (cond.contains("Al", ignoreCase = true)) "Aluminium" else "Copper"
                            val arm = ct?.armour ?: if (rawType.contains("SWA", ignoreCase = true)) "SWA" else "None"
                            val armGroup = if (arm.equals("None", ignoreCase = true) || arm.isBlank()) "Unarmoured" else "Armoured"
                            val rawCores = ct?.cores ?: rawType
                            val coresConfig = when {
                                rawCores.contains("1C") || rawCores.contains("Single", ignoreCase = true) -> "1C"
                                rawCores.contains("2C") -> "2C"
                                rawCores.contains("3.5C") -> "3.5C"
                                rawCores.contains("3C") -> "3C"
                                rawCores.contains("4C") -> "4C"
                                else -> defaultCoreConfig.ifBlank { "4C" }
                            }
                            val ins = ct?.insulation ?: if (rawType.contains("XLPE", ignoreCase = true)) "XLPE" else "PVC"
                            CableFamilyInfo(
                                originalType = ct?.cableType ?: rawType,
                                material = mat,
                                armourGroup = armGroup,
                                exactArmour = arm,
                                coresConfig = coresConfig,
                                insulation = ins,
                                displayLabel = "$mat • $armGroup (${if (arm != "None") arm else "Plain"}) • $coresConfig • $ins"
                            )
                        }
                    }

                    val initialFamilyInfo = remember(cableType) { parseFamilyInfo(cableType) }
                    var filterMat by remember { mutableStateOf(initialFamilyInfo.material) }
                    var filterArmour by remember { mutableStateOf(initialFamilyInfo.armourGroup) }
                    var filterCores by remember { mutableStateOf(initialFamilyInfo.coresConfig) }
                    var filterQuery by remember { mutableStateOf("") }

                    val allCatalogFamilies = remember(manufacturer) {
                        val matching = Catalog.cableTypes.filter { type ->
                            Catalog.cableCatalog.any { cat -> cat.typeCode == type.code && cat.mfg.trim().equals(manufacturer.trim(), ignoreCase = true) }
                        }.map { it.cableType }.distinct()
                        if (matching.isNotEmpty()) matching else listOf(cableType)
                    }

                    val allParsedFamilies = remember(allCatalogFamilies) {
                        allCatalogFamilies.map { parseFamilyInfo(it) }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Conductor Material Filter", color = SlateTextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Copper", "Aluminium").forEach { mat ->
                                val isAvail = allParsedFamilies.any { it.material == mat }
                                FilterChip(
                                    selected = filterMat == mat,
                                    onClick = {
                                        if (isAvail) {
                                            filterMat = mat
                                            val candidate = allParsedFamilies.firstOrNull { it.material == mat && it.armourGroup == filterArmour && it.coresConfig == filterCores }
                                            if (candidate != null) {
                                                cableType = candidate.originalType
                                            } else {
                                                val alt = allParsedFamilies.firstOrNull { it.material == mat }
                                                if (alt != null) {
                                                    filterArmour = alt.armourGroup
                                                    filterCores = alt.coresConfig
                                                    cableType = alt.originalType
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(if (isAvail) mat else "$mat (N/A)", fontSize = 11.sp) },
                                    enabled = isAvail,
                                    modifier = Modifier.weight(1f).testTag("filter_mat_$mat")
                                )
                            }
                        }

                        Text("Armour Construction Filter", color = SlateTextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Unarmoured", "Armoured").forEach { arm ->
                                val isAvail = allParsedFamilies.any { it.material == filterMat && it.armourGroup == arm }
                                FilterChip(
                                    selected = filterArmour == arm,
                                    onClick = {
                                        if (isAvail) {
                                            filterArmour = arm
                                            val candidate = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == arm && it.coresConfig == filterCores }
                                            if (candidate != null) {
                                                cableType = candidate.originalType
                                            } else {
                                                val alt = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == arm }
                                                if (alt != null) {
                                                    filterCores = alt.coresConfig
                                                    cableType = alt.originalType
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(if (isAvail) arm else "$arm (N/A)", fontSize = 11.sp) },
                                    enabled = isAvail,
                                    modifier = Modifier.weight(1f).testTag("filter_armour_$arm")
                                )
                            }
                        }

                        val availableCores = remember(filterMat, filterArmour, allParsedFamilies) {
                            allParsedFamilies.filter { it.material == filterMat && it.armourGroup == filterArmour }
                                .map { it.coresConfig }.distinct()
                        }

                        com.example.ui.components.VoltAtlasDropdown(
                            value = when (filterCores) {
                                "1C" -> "1C — Single Core"
                                "2C" -> "2C — Single Phase"
                                "3C" -> "3C — Three Phase, No Neutral"
                                "3.5C" -> "3.5C — Three Phase + Reduced Neutral"
                                "4C" -> "4C — Three Phase + Full Neutral"
                                else -> filterCores
                            },
                            options = availableCores,
                            onSelect = { core ->
                                filterCores = core
                                val cand = allParsedFamilies.firstOrNull { it.material == filterMat && it.armourGroup == filterArmour && it.coresConfig == core }
                                if (cand != null) {
                                    cableType = cand.originalType
                                }
                            },
                            label = "Core Configuration",
                            modifier = Modifier.testTag("input_core_config")
                        )

                        val matchingFamilies = remember(filterMat, filterArmour, filterCores, filterQuery, allParsedFamilies) {
                            allParsedFamilies.filter {
                                it.material == filterMat &&
                                it.armourGroup == filterArmour &&
                                it.coresConfig == filterCores &&
                                (filterQuery.isBlank() || it.originalType.contains(filterQuery, ignoreCase = true) || it.displayLabel.contains(filterQuery, ignoreCase = true))
                            }
                        }

                        if (matchingFamilies.size > 3) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                label = "Search compatible families..."
                            )
                        }

                        val currentParsed = remember(cableType) { parseFamilyInfo(cableType) }
                        com.example.ui.components.VoltAtlasDropdown(
                            value = currentParsed.displayLabel,
                            options = matchingFamilies.map { it.originalType },
                            onSelect = { selectedType ->
                                cableType = selectedType
                            },
                            label = "Selected Cable Family",
                            modifier = Modifier.testTag("input_cable_type")
                        )
                    }

                    com.example.ui.components.VoltAtlasDropdown(
                        value = installation,
                        options = listOf("Laid in Ducts", "Laid Direct in Ground", "Laid in Free Air"),
                        onSelect = { installation = it },
                        label = "Installation Method"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = parallelRuns,
                                onValueChange = { parallelRuns = it },
                                label = "Parallel Runs",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.testTag("input_parallel_runs")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = length_m,
                                onValueChange = { length_m = it },
                                label = "Length",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("m", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_length")
                            )
                        }
                    }

                    com.example.ui.components.VoltAtlasTextField(
                        value = maxVDpct,
                        onValueChange = { maxVDpct = it },
                        label = "Max Voltage Drop (%)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("input_max_vd")
                    )
                }

                // --- GROUP 4: INSTALLATION CONDITIONS ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isInstallationExpanded = !isInstallationExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("4. Installation Conditions", style = CardTitle, color = SleekPrimary)
                    Icon(
                        imageVector = if (isInstallationExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Expand installation",
                        tint = SleekPrimary
                    )
                }
                HorizontalDivider(color = BorderColor)

                if (isInstallationExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = airTempC,
                                onValueChange = { airTempC = it },
                                label = "Air Temp",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("°C", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_air_temp")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = gndTempC,
                                onValueChange = { gndTempC = it },
                                label = "Gnd Temp",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("°C", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_gnd_temp")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = burialDepth_m,
                                onValueChange = { burialDepth_m = it },
                                label = "Burial Depth",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("m", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_burial_depth")
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            com.example.ui.components.VoltAtlasTextField(
                                value = soilRes,
                                onValueChange = { soilRes = it },
                                label = "Soil Res",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("Ωm", style = Caption, color = SlateTextSecondary, modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.testTag("input_soil_res")
                            )
                        }
                    }

                    com.example.ui.components.VoltAtlasTextField(
                        value = noCircuits,
                        onValueChange = { noCircuits = it },
                        label = "Grouping (No. of Circuits)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("input_no_circuits")
                    )

                    com.example.ui.components.VoltAtlasDropdown(
                        value = formation,
                        options = listOf("Flat", "Trefoil"),
                        onSelect = { formation = it },
                        label = "Formation"
                    )

                    com.example.ui.components.VoltAtlasDropdown(
                        value = spacing,
                        options = listOf("Touching", "0.15m", "0.30m"),
                        onSelect = { spacing = it },
                        label = "Spacing"
                    )
                }

                if (panelType != "LOAD" && panelType.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPanelHeaderExpanded = !isPanelHeaderExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "5. Panel Header Equipment Data",
                            style = CardTitle,
                            color = SleekPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isPanelHeaderExpanded = !isPanelHeaderExpanded }) {
                            Icon(
                                imageVector = if (isPanelHeaderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Panel Header Section",
                                tint = SleekPrimary
                            )
                        }
                    }
                    HorizontalDivider(color = BorderColor)

                    if (isPanelHeaderExpanded) {
                        val uiStateVal = viewModel?.uiState?.collectAsState()?.value as? UiState.Success
                        val calculatedIscKA = if (circuit != null) uiStateVal?.shortCircuitResults?.get(circuit.id)?.iscKA else null
                        val scText = calculatedIscKA?.let { String.format(java.util.Locale.US, "%.2f kA", it) } ?: "N/A (Calculated after save)"

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasTextField(
                                    value = busbarRatingA,
                                    onValueChange = { busbarRatingA = it },
                                    label = "Busbar Rating (A)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.testTag("input_edit_dialog_busbar_rating")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasTextField(
                                    value = scText,
                                    onValueChange = {},
                                    label = "Short Circuit (kA) [Calculated]",
                                    readOnly = true,
                                    modifier = Modifier.testTag("display_edit_dialog_sc_rating")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasDropdown(
                                    value = mainIncomingType,
                                    options = listOf("", "MCCB", "ACB", "LBS", "Switch-Disconnector", "Fuse-Switch"),
                                    onSelect = { mainIncomingType = it },
                                    label = "Main Incoming Type",
                                    modifier = Modifier.testTag("input_edit_dialog_main_incoming_type")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasTextField(
                                    value = mainIncomingCount,
                                    onValueChange = { mainIncomingCount = it },
                                    label = "Incoming Count",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.testTag("input_edit_dialog_main_incoming_count")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasDropdown(
                                    value = ipRating,
                                    options = listOf("", "IP20", "IP41", "IP54", "IP55", "IP65"),
                                    onSelect = { ipRating = it },
                                    label = "IP Rating",
                                    modifier = Modifier.testTag("input_edit_dialog_ip_rating")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasDropdown(
                                    value = cableEntry,
                                    options = listOf("", "Top", "Bottom", "Top/Bottom"),
                                    onSelect = { cableEntry = it },
                                    label = "Cable Entry",
                                    modifier = Modifier.testTag("input_edit_dialog_cable_entry")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasTextField(
                                    value = panelLocation,
                                    onValueChange = { panelLocation = it },
                                    label = "Location",
                                    modifier = Modifier.testTag("input_edit_dialog_location")
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                com.example.ui.components.VoltAtlasTextField(
                                    value = numberOfWays,
                                    onValueChange = { numberOfWays = it },
                                    label = "Number of Ways",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.testTag("input_edit_dialog_number_of_ways")
                                )
                            }
                        }

                        com.example.ui.components.VoltAtlasTextField(
                            value = feederDescription,
                            onValueChange = { feederDescription = it },
                            label = "Feeder / Supply Cable Description",
                            modifier = Modifier.testTag("input_edit_dialog_feeder_description")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Circuit/Panel name cannot be empty."
                        return@Button
                    }
                    val isDuplicateName = existingCircuits.any { c ->
                        (circuit == null || circuit.id == 0 || c.id != circuit.id) && c.circuitName.trim().equals(trimmedName, ignoreCase = true)
                    }
                    if (isDuplicateName) {
                        errorMessage = "A circuit/panel named '$trimmedName' already exists — choose a different name."
                        return@Button
                    }

                    val doubleLengthRaw = length_m.toDoubleOrNull() ?: 30.0
                    if (doubleLengthRaw <= 0) {
                        errorMessage = "Cable length must be greater than zero."
                        return@Button
                    }
                    val doubleLength = doubleLengthRaw
                    val doubleKva = kotlin.math.max(0.0, loadKVA.toDoubleOrNull() ?: 10.0)
                    val doublePf = (pf.toDoubleOrNull() ?: 0.8).coerceIn(0.1, 1.0)
                    val doubleDemandFactor = (demandFactor.toDoubleOrNull() ?: 1.0).coerceIn(0.0, 1.0)
                    val doubleSafety = kotlin.math.max(1.0, safetyFactor.toDoubleOrNull() ?: 1.0)
                    val intVoltage = voltageV.toIntOrNull() ?: 400
                    val intParallel = parallelRuns.toIntOrNull() ?: 1
                                        val doubleMaxVD = (maxVDpct.toDoubleOrNull() ?: 3.0) / 100.0
                    val intAirTemp = airTempC.toIntOrNull() ?: 40
                    val intGndTemp = gndTempC.toIntOrNull() ?: 40
                    val doubleDepth = burialDepth_m.toDoubleOrNull() ?: 0.6
                    val intSoilRes = soilRes.toIntOrNull() ?: 125
                    val intNoCircuits = noCircuits.toIntOrNull() ?: 1

                    val isGen = loadType == "EMERGENCY" || loadType == "EMERGENCY + UPS"
                    val isUps = loadType == "UPS" || loadType == "EMERGENCY + UPS"

                    val finalTxId = if (fedFrom == "Transformer" && (transformerId.isEmpty() || transformerId.contains("Add Transformer"))) {
                        if (transformers.isNotEmpty()) {
                            transformers.firstOrNull()?.transformerId ?: ""
                        } else {
                            viewModel?.addNewTransformerInline() ?: "TX-01"
                        }
                    } else {
                        transformerId
                    }

                    val parentBoardObj = existingCircuits.firstOrNull { it.circuitName == fedFrom }
                    val calculatedParentId = parentBoardObj?.id
                    val calculatedLevel = if (parentBoardObj == null) 0 else (parentBoardObj.level ?: 0) + 1
                    val calculatedSourceType = when (fedFrom.uppercase()) {
                        "GENERATOR" -> com.example.data.SourceType.GENERATOR.name
                        "GRID", "UTILITY GRID" -> com.example.data.SourceType.GRID.name
                        "ATS" -> "ATS"
                        "TRANSFORMER" -> com.example.data.SourceType.TRANSFORMER.name
                        else -> if (parentBoardObj != null) com.example.data.SourceType.PANEL.name else com.example.data.SourceType.TRANSFORMER.name
                    }

                    val savedCircuit = circuit?.copy(
                        circuitName = name.trim().takeIf { it.isNotEmpty() } ?: "Unnamed Circuit",
                        loadKVA = doubleKva,
                        fedFrom = fedFrom,
                        transformerId = finalTxId,
                        loadType = loadType,
                        phases = phases,
                        voltageV = intVoltage,
                        pf = doublePf,
                        safetyFactor = doubleSafety,
                        manufacturer = manufacturer,
                        cableType = cableType,
                        installation = installation,
                        parallelRuns = intParallel,
                        airTempC = intAirTemp,
                        gndTempC = intGndTemp,
                        burialDepth_m = doubleDepth,
                        soilRes = intSoilRes,
                        noCircuits = intNoCircuits,
                        formation = formation,
                        spacing = spacing,
                        length_m = doubleLength,
                        maxVDpct = doubleMaxVD,
                        generatorIncluded = isGen,
                        atsIncluded = isGen,
                        upsBacked = isUps,
                        isHarmonic = isHarmonic,
                        loadNature = loadNature,
                        startingMethod = if (loadNature == "Motor") startingMethod else null,
                        startingCurrentMultiplier = if (loadNature == "Motor") startingCurrentMultiplier.toDoubleOrNull() else null,
                        panelType = panelType,
                        parentId = calculatedParentId,
                        level = calculatedLevel,
                        sourceType = calculatedSourceType,
                        demandFactor = doubleDemandFactor,
                        loadCategory = loadCategory,
                        busbarRatingA = busbarRatingA.toDoubleOrNull(),
                        scRatingKA = circuit?.scRatingKA,
                        mainIncomingType = mainIncomingType.ifBlank { null },
                        mainIncomingCount = mainIncomingCount.toIntOrNull(),
                        ipRating = ipRating.ifBlank { null },
                        panelLocation = panelLocation.ifBlank { null },
                        cableEntry = cableEntry.ifBlank { null },
                        feederDescription = feederDescription.ifBlank { null },
                        numberOfWays = numberOfWays.toIntOrNull(),
                        feederType = feederType,
                        buswayMfg = if (feederType == "Busway") buswayMfg else null,
                        buswayModel = if (feederType == "Busway") buswayModel else null
                    ) ?: CircuitEntity(
                        circuitName = name.trim().takeIf { it.isNotEmpty() } ?: "Unnamed Circuit",
                        loadKVA = doubleKva,
                        fedFrom = fedFrom,
                        transformerId = finalTxId,
                        loadType = loadType,
                        phases = phases,
                        voltageV = intVoltage,
                        pf = doublePf,
                        safetyFactor = doubleSafety,
                        manufacturer = manufacturer,
                        cableType = cableType,
                        installation = installation,
                        parallelRuns = intParallel,
                        airTempC = intAirTemp,
                        gndTempC = intGndTemp,
                        burialDepth_m = doubleDepth,
                        soilRes = intSoilRes,
                        noCircuits = intNoCircuits,
                        formation = formation,
                        spacing = spacing,
                        length_m = doubleLength,
                        maxVDpct = doubleMaxVD,
                        generatorIncluded = isGen,
                        atsIncluded = isGen,
                        upsBacked = isUps,
                        isHarmonic = isHarmonic,
                        loadNature = loadNature,
                        startingMethod = if (loadNature == "Motor") startingMethod else null,
                        startingCurrentMultiplier = if (loadNature == "Motor") startingCurrentMultiplier.toDoubleOrNull() else null,
                        panelType = panelType,
                        parentId = calculatedParentId,
                        level = calculatedLevel,
                        sourceType = calculatedSourceType,
                        demandFactor = doubleDemandFactor,
                        loadCategory = loadCategory,
                        busbarRatingA = busbarRatingA.toDoubleOrNull(),
                        scRatingKA = circuit?.scRatingKA,
                        mainIncomingType = mainIncomingType.ifBlank { null },
                        mainIncomingCount = mainIncomingCount.toIntOrNull(),
                        ipRating = ipRating.ifBlank { null },
                        panelLocation = panelLocation.ifBlank { null },
                        cableEntry = cableEntry.ifBlank { null },
                        feederDescription = feederDescription.ifBlank { null },
                        numberOfWays = numberOfWays.toIntOrNull(),
                        feederType = feederType,
                        buswayMfg = if (feederType == "Busway") buswayMfg else null,
                        buswayModel = if (feederType == "Busway") buswayModel else null
                    )

                    onSave(savedCircuit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                modifier = Modifier.testTag("save_circuit_btn")
            ) {
                Text("Save", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Text("Cancel", color = ThemeTextColor)
            }
        }
    )
}

// 4. BOQ CABLES TAB
@Composable
fun BoqCablesScreen(state: UiState.Success) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("BILL OF QUANTITIES — CABLES", color = ThemeTextColor, style = SectionTitle)
        }

        val kpiResult = com.example.engine.BoqEngine.generateBoqResult(state.circuits, state.sizingResults, state.drumAllocations)
        val kpi = kpiResult.kpi

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PROJECT PROCUREMENT KPI DASHBOARD", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Circuits", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalCircuits}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Total Route", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.1f", kpi.totalRoute_m)} m", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Total Ordered", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.1f", kpi.totalOrdered_m)} m", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Drums Used", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalDrums}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Cable Families", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.cableFamilies}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Scrap / Waste", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${String.format("%.1f", kpi.wastePct)}%", color = if (kpi.wastePct > 15.0) SleekWarning else SleekPass, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Total Glands", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalGlands}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Devices Count", color = SlateTextSecondary, fontSize = 10.sp)
                            Text("${kpi.totalDevices}", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (state.boq.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("No cable BOQ items calculated.", modifier = Modifier.padding(16.dp), color = SlateTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            items(state.boq, key = { "${it.cableType}_${it.size}_${it.productCode}_${it.manufacturer}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.cableType, color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Size", color = SlateTextSecondary, fontSize = 10.sp)
                                Text(item.size, color = ThemeTextColor, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Total Length", color = SlateTextSecondary, fontSize = 10.sp)
                                Text("${item.totalLength_m} m", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Total Weight", color = SlateTextSecondary, fontSize = 10.sp)
                                if (item.status != "OK") {
                                    Text(item.status, color = SleekFail, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("${item.totalWeight_kg} kg", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column {
                                Text("Total Runs", color = SlateTextSecondary, fontSize = 10.sp)
                                Text("${item.runsCount}", color = ThemeTextColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. PROCUREMENT & ACCESSORIES SCREEN
@Composable
fun ProcurementScreen(state: UiState.Success) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("RESOLVED ACCESSORIES & PATHWAYS", color = ThemeTextColor, style = SectionTitle)
        }

        if (state.procurement.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("No accessory procurement items calculated.", modifier = Modifier.padding(16.dp), color = SlateTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            items(state.procurement, key = { it.circuitName }) { item ->
                val glandText = if (item.glandModel.isNotBlank() && item.glandModel != "N/A") "${item.glandModel} (${item.glandDesc})" else "Not specified"
                val lugsText = if (item.lugsType.isNotBlank()) item.lugsType else "Not specified"
                val heatShrinkText = if (item.cableOD_mm > 0.0) "Heat-Shrink Sleeve (${(item.cableOD_mm + 4).toInt()}/${maxOf(1, (item.cableOD_mm / 2.5).toInt())} mm)" else "Not specified"
                val trayText = if (item.requiredTrayWidth_mm > 0.0) "${item.requiredTrayWidth_mm.toInt()} mm (${item.requiredTrayWidth_mm.toInt() / 10} cm)" else "Not specified"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.circuitName, color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(item.cableSize, color = SleekPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cable Glands (CMP)", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(glandText, color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Compression Lugs (El-Massalla)", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(lugsText, color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Heat-Shrink Sleeve", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(heatShrinkText, color = ThemeTextColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Standard Cable Tray Width", color = SlateTextSecondary, fontSize = 11.sp)
                            Text(trayText, color = SleekWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 6. DRUMS SCREEN
@Composable
fun BoqDrumsView(state: UiState.Success) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text("OPTIMIZED DRUM PACKING & CUTTING PLANS", color = ThemeTextColor, style = SectionTitle)
        }

        if (state.drumAllocations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("No drum schedule allocations computed.", modifier = Modifier.padding(16.dp), color = SlateTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            val grouped = state.drumAllocations.groupBy { it.cableType to it.size }

            grouped.forEach { (key, allocations) ->
                item {
                    Text(
                        text = "${key.second} - ${key.first}",
                        color = SleekSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(allocations, key = { "${it.cableType}_${it.size}_drum_${it.drumIndex}" }) { drum ->
                    var isExpanded by rememberSaveable { mutableStateOf(false) }
                    val efficiency = if (drum.drumCapacity_m > 0.0) (drum.totalUsedLength_m / drum.drumCapacity_m) else 0.0
                    val wastePct = if (drum.drumCapacity_m > 0.0) (drum.wasteLength_m / drum.drumCapacity_m) * 100.0 else 0.0
                    val progressColor = when {
                        efficiency >= 0.85 -> SleekPass
                        efficiency >= 0.50 -> SleekWarning
                        else -> SleekFail
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("DRUM #${drum.drumIndex}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Capacity: ${drum.drumCapacity_m.toInt()}m", color = SlateTextSecondary, fontSize = 10.sp)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Used: ${drum.totalUsedLength_m.toInt()}m (${String.format("%.1f", efficiency * 100.0)}%)", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Scrap: ${String.format("%.1f", drum.wasteLength_m)}m (${String.format("%.1f", wastePct)}%)", color = if (wastePct > 15.0) SleekWarning else SlateTextSecondary, fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { efficiency.toFloat() },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = BorderColor
                                )
                                Text(
                                    text = "${(efficiency * 100).toInt()}% Utilized",
                                    color = progressColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = { isExpanded = !isExpanded },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand details",
                                        tint = SleekPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExpanded) "Hide Cutting Sheet" else "View Cutting Sheet (${drum.allocatedCuts.size} cuts)",
                                        color = SleekPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(tween(200)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBackground, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("REQUIRED CONTIGUOUS CUTS:", color = SlateTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    drum.allocatedCuts.forEach { cut ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "• ${cut.circuitName} (Run #${cut.runIndex})",
                                                color = ThemeTextColor,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "${cut.length_m} m",
                                                color = SleekSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
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
}

// Combined BOQ / Procurement Screen
@Composable
fun BoqProcurementScreen(state: UiState.Success) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Cables", "Accessories", "Drums", "Conduit Roll-Up")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = subTab,
            containerColor = DarkCardBg,
            contentColor = SleekPrimary,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = subTab == index,
                    onClick = { subTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (subTab) {
            0 -> BoqCablesScreen(state)
            1 -> ProcurementScreen(state)
            2 -> BoqDrumsView(state)
            3 -> ConduitProcurementRollupScreen(state)
        }
    }
}

@Composable
fun ConduitProcurementRollupScreen(state: UiState.Success) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("CONDUIT PROCUREMENT ROLL-UP", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (state.conduitProcurement.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No valid conduit records in project.", color = SlateTextSecondary, fontSize = 13.sp)
                }
            }
        }
        items(state.conduitProcurement, key = { "${it.conduitType}_${it.tradeSize}" }) { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${group.tradeSize} ${group.conduitType}", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${group.totalConduitsCount} pcs | ${group.totalLength_m.toInt()} m", color = ThemeTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Circuits Count: ${group.circuitsCount}", color = SlateTextSecondary, fontSize = 11.sp)
                        Text("Avg Fill: ${String.format("%.1f", group.avgFillPercent)}% | Worst: ${String.format("%.1f", group.worstFillPercent)}%", color = ThemeTextColor, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PASS: ${group.passCount} | FAIL: ${group.failCount}", color = SleekPass, fontSize = 11.sp)
                        Text("REVIEW: ${group.reviewCount} | MISSING: ${group.missingCount}", color = SleekWarning, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}




@Composable
private fun DiagnosticRow(label: String, loaded: Boolean, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (loaded) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = if (loaded) "Loaded" else "Failed",
                tint = if (loaded) SleekPass else SleekFail,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = ThemeTextColor, fontSize = 13.sp)
        }
        if (loaded && count != null) {
            Text("$count records", color = SlateTextSecondary, fontSize = 12.sp)
        } else {
            Text(
                text = if (loaded) "Loaded" else "Failed",
                color = if (loaded) SleekPass else SleekFail,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PdfExportDialog(
    projectId: String,
    currentProjectName: String,
    currentClientName: String,
    preferences: com.example.data.GlobalPreferences,
    onDismiss: () -> Unit,
    onExport: (com.example.pdf.ReportOptions) -> Unit
) {
    var projectName by rememberSaveable { mutableStateOf(currentProjectName) }
    var engineer by rememberSaveable { mutableStateOf(preferences.getReportEngineer(projectId)) }
    var contractorCompany by rememberSaveable { mutableStateOf(preferences.getReportContractor(projectId)) }
    var consultant by rememberSaveable { mutableStateOf(preferences.getReportConsultant(projectId)) }
    var owner by rememberSaveable {
        mutableStateOf(preferences.getReportOwner(projectId).ifBlank { currentClientName })
    }
    var date by rememberSaveable { mutableStateOf(preferences.getReportDate(projectId)) }
    var revision by rememberSaveable { mutableStateOf(preferences.getReportRevision(projectId)) }

    var isPortrait by rememberSaveable { mutableStateOf(true) }
    var includeCalculationDetails by rememberSaveable { mutableStateOf(true) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            border = BorderStroke(1.dp, ThemeBorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                SleekPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF",
                            tint = SleekPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Export PDF Report",
                            color = ThemeTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Report metadata & title block details",
                            color = SlateTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = ThemeBorderSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_project_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = engineer,
                        onValueChange = { engineer = it },
                        label = { Text("Engineer") },
                        placeholder = { Text("e.g. Eng. John Doe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_engineer_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = contractorCompany,
                        onValueChange = { contractorCompany = it },
                        label = { Text("Contractor Company") },
                        placeholder = { Text("e.g. Apex Electrical Contracting") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_contractor_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = consultant,
                        onValueChange = { consultant = it },
                        label = { Text("Consultant") },
                        placeholder = { Text("e.g. Global Engineering Consultancy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_consultant_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text("Owner / Client") },
                        placeholder = { Text("e.g. City Development Authority") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_owner_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = ThemeBorderSubtle,
                            focusedLabelColor = SleekPrimary,
                            unfocusedLabelColor = SlateTextSecondary,
                            focusedTextColor = ThemeTextColor,
                            unfocusedTextColor = ThemeTextColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Date") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f).testTag("pdf_date_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = ThemeBorderSubtle,
                                focusedLabelColor = SleekPrimary,
                                unfocusedLabelColor = SlateTextSecondary,
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor
                            )
                        )

                        OutlinedTextField(
                            value = revision,
                            onValueChange = { revision = it },
                            label = { Text("Revision") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f).testTag("pdf_revision_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = ThemeBorderSubtle,
                                focusedLabelColor = SleekPrimary,
                                unfocusedLabelColor = SlateTextSecondary,
                                focusedTextColor = ThemeTextColor,
                                unfocusedTextColor = ThemeTextColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(color = ThemeBorderSubtle)
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isPortrait,
                            onCheckedChange = { isPortrait = it },
                            colors = CheckboxDefaults.colors(checkedColor = SleekPrimary)
                        )
                        Text("Portrait Layout", color = ThemeTextColor, fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeCalculationDetails,
                            onCheckedChange = { includeCalculationDetails = it },
                            colors = CheckboxDefaults.colors(checkedColor = SleekPrimary)
                        )
                        Text("Include Calculation Details", color = ThemeTextColor, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeBorderSubtle)
                    ) {
                        Text("Cancel", color = ThemeTextColor, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            preferences.setReportEngineer(projectId, engineer)
                            preferences.setReportContractor(projectId, contractorCompany)
                            preferences.setReportConsultant(projectId, consultant)
                            preferences.setReportOwner(projectId, owner)
                            preferences.setReportDate(projectId, date)
                            preferences.setReportRevision(projectId, revision)

                            onExport(
                                com.example.pdf.ReportOptions(
                                    isCompleteProjectReport = true,
                                    selectedSections = listOf(
                                        "Executive Summary",
                                        "Circuits & Cable Sizing",
                                        "Transformers",
                                        "Power Systems",
                                        "Protection & Short Circuit",
                                        "Conduits",
                                        "BOQ & Procurement"
                                    ),
                                    isPortrait = isPortrait,
                                    includeCalculationDetails = includeCalculationDetails,
                                    projectName = projectName,
                                    engineer = engineer,
                                    contractorCompany = contractorCompany,
                                    consultant = consultant,
                                    owner = owner,
                                    date = date,
                                    revision = revision
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).height(42.dp).testTag("pdf_export_confirm_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Text("Generate PDF", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MoreModulesGrid(onNavigate: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        com.example.ui.components.VoltAtlasSectionHeader(title = "Secondary Engineering Modules")

        VoltAtlasFullWidthModuleRow(
            title = "Panel Schedule & Load Aggregation",
            description = "Bottom-up recursive feeder load aggregation, phase balancing & feeder schedules",
            icon = Icons.Default.TableChart,
            iconTint = Color(0xFF10B981),
            onClick = { onNavigate(12) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Single-Line Diagram (SLD)",
            description = "Interactive single-line network diagram & validation view",
            icon = ElectricalIcons.Sld,
            onClick = { onNavigate(5) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Protection & Coordination",
            description = "Breaker coordination, tripping curves & fault currents",
            icon = ElectricalIcons.Protection,
            onClick = { onNavigate(4) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Conduits & Cable Fill",
            description = "Conduit sizing, NEC fill ratios & cable bundling",
            icon = ElectricalIcons.Conduit,
            onClick = { onNavigate(6) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "BOQ & Procurement",
            description = "Bill of quantities, cable drum allocation & procurement schedules",
            icon = ElectricalIcons.Boq,
            onClick = { onNavigate(7) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Electrical & Cable Conversions",
            description = "AWG/SWG/kcmil to mm² (True, Nearest, Safe Up-Size), µF ↔ kVAR, HP ↔ kW, Freq ↔ RPM",
            icon = Icons.Default.SwapHoriz,
            iconTint = Color(0xFF38BDF8),
            onClick = { onNavigate(10) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Engineering Methodology & Formulas",
            description = "Complete reference guide for IEC & BS formulas, variables, and worked examples",
            icon = Icons.Default.Functions,
            iconTint = Color(0xFFA855F7),
            onClick = { onNavigate(11) }
        )

        VoltAtlasFullWidthModuleRow(
            title = "Global Settings",
            description = "Design preferences, system frequency, nominal voltage & calculation defaults",
            icon = ElectricalIcons.Settings,
            onClick = { onNavigate(8) }
        )
    }
}

@Composable
fun ConversionsContainerScreen(onBack: () -> Unit = {}) {
    var selectedTool by remember { mutableStateOf<com.example.ui.conversions.ConversionTool?>(null) }

    val activeTool = selectedTool
    if (activeTool != null) {
        com.example.ui.conversions.ConversionDetailScreen(
            tool = activeTool,
            onBack = { selectedTool = null }
        )
    } else {
        com.example.ui.conversions.ConversionsScreen(
            onSelectTool = { selectedTool = it },
            onBack = onBack
        )
    }
}

@Composable
fun VoltAtlasFullWidthModuleRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    com.example.ui.components.VoltAtlasCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (iconTint != Color.Unspecified) iconTint.copy(alpha = 0.12f)
                        else SleekPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Menu icon",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CardTitle.copy(fontSize = 15.sp),
                    color = ThemeTextColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = Caption.copy(fontSize = 12.sp),
                    color = SlateTextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = SlateTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
