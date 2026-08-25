package com.example.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ui.designsystem.icons.ElectricalIcons
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
import com.example.ui.screens.*
import com.example.ui.dialogs.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

fun isMoreTabSelected(currentTab: Int): Boolean {
    return currentTab in listOf(4, 5, 6, 7, 8, 9, 10, 11, 12)
}

@Composable
fun MainLayout(
    viewModel: EngineeringViewModel,
    assistantViewModel: com.example.ai.ui.EngineeringAssistantViewModel? = null,
    assistantViewModelFactory: androidx.lifecycle.ViewModelProvider.Factory? = null
) {
    val state by viewModel.uiState.collectAsState()

    val activeId by viewModel.activeProjectId.collectAsState()
    val projects by viewModel.projects.collectAsState()

    val showGlobalSettings by viewModel.showGlobalSettings.collectAsState()
    val confirmDestructiveActions by viewModel.preferences.confirmDestructiveActions.collectAsState()

    val layoutContext = LocalContext.current
    val viewModelStoreOwner = checkNotNull(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner available"
    }

    var isAssistantInitialized by remember { mutableStateOf(assistantViewModel != null) }

    val resolveAssistantViewModel: () -> com.example.ai.ui.EngineeringAssistantViewModel = remember(
        assistantViewModel, assistantViewModelFactory, viewModelStoreOwner, layoutContext
    ) {
        {
            assistantViewModel ?: run {
                val app = layoutContext.applicationContext as android.app.Application
                val factory = assistantViewModelFactory ?: com.example.ai.ui.EngineeringAssistantViewModelFactory.production(app)
                androidx.lifecycle.ViewModelProvider(viewModelStoreOwner, factory)[com.example.ai.ui.EngineeringAssistantViewModel::class.java]
            }
        }
    }

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

    var showQuickEngineering by rememberSaveable { mutableStateOf(false) }
    var selectedQuickToolId by rememberSaveable { mutableStateOf<String?>(null) }

    // SLICE 8B: direction memory for the Project Picker <-> Workspace entry.
    var previousHadActiveProject by rememberSaveable { mutableStateOf(false) }

    val reduceMotion = com.example.ui.theme.LocalReduceMotion.current

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

    if (showQuickEngineering) {
        val activeToolId = selectedQuickToolId
        // SLICE 8B: directional shared-axis transition Hub <-> individual tool.
        // AnimatedContent keeps per-target saved state (scroll position,
        // entered inputs) so switching does not recreate screen state.
        androidx.compose.animation.AnimatedContent(
            targetState = activeToolId,
            transitionSpec = {
                val direction = if (targetState == null) {
                    VoltAtlasMotion.NavigationDirection.Backward
                } else {
                    VoltAtlasMotion.NavigationDirection.Forward
                }
                VoltAtlasMotion.tabContentTransform(
                    direction = direction,
                    reduceMotion = reduceMotion
                )
            },
            label = "QuickToolSwitchAnimation"
        ) { toolId ->
            if (toolId == null) {
                QuickEngineeringHubScreen(
                    onSelectTool = { selectedQuickToolId = it },
                    onBack = {
                        showQuickEngineering = false
                        selectedQuickToolId = null
                    }
                )
            } else {
                when (toolId) {
                    "cable_sizing" -> QuickCableCalcScreen(onBack = { selectedQuickToolId = null })
                    "voltage_drop" -> com.example.ui.screens.quick.QuickVoltageDropScreenV2(onBack = { selectedQuickToolId = null })
                    "load_current" -> com.example.ui.screens.quick.QuickLoadCurrentScreenV2(onBack = { selectedQuickToolId = null })
                    "power_triangle" -> QuickPowerTriangleScreen(onBack = { selectedQuickToolId = null })
                    "pfc" -> com.example.ui.screens.quick.QuickPfcScreenV2(onBack = { selectedQuickToolId = null })
                    "transformer_quick" -> QuickTransformerScreen(onBack = { selectedQuickToolId = null })
                    "transformer_sc" -> QuickTransformerScScreen(onBack = { selectedQuickToolId = null })
                    "lighting" -> QuickLightingScreen(onBack = { selectedQuickToolId = null })
                    else -> QuickCableCalcScreen(onBack = { selectedQuickToolId = null })
                }
            }
        }
        return
    }

    if (activeId == null) {
        // SLICE 8B: Project Picker re-entry after closing a workspace reads as
        // Backward (mirrors Forward workspace entry). Cold launch plays NO
        // entrance animation (performance guardrail: no motion on cold start).
        val pickerContent: @Composable () -> Unit = {
            ProjectPickerScreen(
                projects = projects,
                onCreateProject = { name -> viewModel.createAndOpenProject(name) },
                onOpenProject = { id -> viewModel.openProject(id) },
                onRenameProject = { id, newName -> viewModel.renameProject(id, newName) },
                onDeleteProject = { id -> viewModel.deleteProject(id) },
                onExportProjectJson = { id -> viewModel.exportProjectJson(id) },
                onImportProjectJson = { jsonStr, callback -> viewModel.importProjectJson(jsonStr, callback) },
                onOpenSettings = { viewModel.openGlobalSettings() },
                onOpenQuickCalc = {
                    showQuickEngineering = true
                    selectedQuickToolId = null
                },
                confirmDestructiveActions = confirmDestructiveActions
            )
        }
        if (previousHadActiveProject) {
            com.example.ui.theme.SharedAxisEnter(
                direction = com.example.ui.theme.VoltAtlasMotion.NavigationDirection.Backward
            ) { pickerContent() }
        } else {
            pickerContent()
        }

        LaunchedEffect(Unit) { previousHadActiveProject = false }
        return
    }

    // SLICE 8B: remember that a workspace was open so the Picker's next entry
    // can be direction-aware (Backward) without replaying during recomposition.
    LaunchedEffect(activeId) { previousHadActiveProject = true }

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

    // ── Hierarchical Predictive Back-Button Handler ──
    // Priority: (0) Assistant sheet → (1) close overlay → (2) Panel Schedule return / More child→More grid → (3) any non-Home→Home → (4) Home→exit dialog
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackNav by remember { mutableStateOf(false) }

    fun openAssistantWithContext(
        entryPoint: com.example.ai.ui.AssistantEntryPoint,
        entityType: String? = null,
        entityId: String? = null,
        entityName: String? = null
    ) {
        val summary = when (val s = state) {
            is UiState.Success -> com.example.ai.context.ProjectSummarySnapshot(
                totalCircuits = s.circuits.size,
                totalTransformers = s.transformers.size,
                totalConnectedKVA = s.transformers.sumOf { it.kVA },
                maxTxLoadingPct = s.transformerResults.values.maxOfOrNull { it.loadingPct } ?: 0.0,
                maxCumulativeVdPct = s.sizingResults.values.maxOfOrNull { it.cumulativeVDpct } ?: 0.0,
                complianceStatus = "NORMAL"
            )
            else -> com.example.ai.context.ProjectSummarySnapshot()
        }
        val currentScreenName = when (currentTab) {
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
            11 -> "Formulas & Methodology"
            12 -> "Panel Schedule"
            else -> "Dashboard"
        }
        val ctx = com.example.ai.context.EngineeringContext(
            projectId = activeId,
            projectName = projectName,
            currentScreen = currentScreenName,
            activeEntityType = entityType,
            activeEntityId = entityId,
            activeEntityName = entityName,
            projectSummary = summary
        )
        isAssistantInitialized = true
        val assistantVm = resolveAssistantViewModel()
        assistantVm.open(entryPoint, ctx)
    }

    PredictiveBackHandler(enabled = true) { progress: kotlinx.coroutines.flow.Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
            }
            // Gesture Completed -> Execute exact 6-tier navigation logic
            when {
                // 1. Close any open overlay / dialog / fullscreen first
                showPdfExportDialog -> showPdfExportDialog = false
                showDiagnosticsDialog -> showDiagnosticsDialog = false
                showNewProjectConfirmation -> showNewProjectConfirmation = false
                showExitDialog -> showExitDialog = false
                isSldFullscreen -> isSldFullscreen = false
                // 2. Panel Schedule (12) returns to entry point (Circuits or More grid)
                currentTab == 12 -> {
                    isPredictiveBackNav = true
                    currentTab = panelScheduleReturnTab
                }
                // 3. More-children (4,5,6,7,8,10,11) → More grid (9)
                currentTab in listOf(4, 5, 6, 7, 8, 10, 11) -> {
                    isPredictiveBackNav = true
                    currentTab = 9
                }
                // 4. More grid (9) or direct nav items (1,2,3) → Home (0)
                currentTab != 0 -> {
                    isPredictiveBackNav = true
                    currentTab = 0
                }
                // 5. Already on Home → show exit confirmation
                else -> showExitDialog = true
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            predictiveBackProgress = 0f
        } finally {
            predictiveBackProgress = 0f
        }
    }

    // Deterministically reset isPredictiveBackNav after AnimatedContent evaluates the gesture transition
    LaunchedEffect(currentTab) {
        if (isPredictiveBackNav) {
            isPredictiveBackNav = false
        }
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

    // SLICE 8B: workspace entry is an approved EMPHASIZED moment — restrained
    // shared-axis enter (short travel + subtle scale + alpha), played once per
    // project open. Draw-phase only: input is never blocked.
    com.example.ui.theme.SharedAxisEnter(
        direction = com.example.ui.theme.VoltAtlasMotion.NavigationDirection.Forward,
        modifier = Modifier.fillMaxSize()
    ) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (useNavRail) {
            NavigationRail(
                containerColor = ThemeSurface,
                header = {
                    IconButton(
                        onClick = { viewModel.closeProject() },
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(48.dp)
                            .testTag("rail_back_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(ThemeBackground, shape = RoundedCornerShape(VoltAtlasShapes.chip))
                                .border(1.dp, ThemeBorderSubtle, RoundedCornerShape(VoltAtlasShapes.chip)),
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
                            modifier = Modifier.testTag("rail_dest_$targetTab"),
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
                        .background(ThemeSurface)
                        .border(BorderStroke(1.dp, ThemeBorderSubtle))
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
                                modifier = Modifier.size(48.dp).testTag("phone_back_btn")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(ThemeBackground, shape = RoundedCornerShape(VoltAtlasShapes.tag))
                                        .border(1.dp, ThemeBorderSubtle, RoundedCornerShape(VoltAtlasShapes.tag)),
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

                            IconButton(
                                onClick = { openAssistantWithContext(com.example.ai.ui.AssistantEntryPoint.TOP_BAR_ICON) },
                                modifier = Modifier.size(48.dp).testTag("top_bar_assistant_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Open AI Assistant",
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
                                        text = { Text("VoltAtlas Assistant", color = ThemeTextColor) },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant", tint = SleekPrimary) },
                                        onClick = {
                                            showOverflowMenu = false
                                            openAssistantWithContext(com.example.ai.ui.AssistantEntryPoint.OVERFLOW_MENU)
                                        },
                                        modifier = Modifier.testTag("menu_assistant_btn")
                                    )
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
                        containerColor = ThemeSurface,
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
                                modifier = Modifier.testTag("bottom_dest_$targetTab"),
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
                        val peekBackTarget = remember(currentTab, showPdfExportDialog, showDiagnosticsDialog, showNewProjectConfirmation, showExitDialog, isSldFullscreen, panelScheduleReturnTab) {
                            when {
                                showPdfExportDialog || showDiagnosticsDialog || showNewProjectConfirmation || showExitDialog || isSldFullscreen -> null
                                currentTab == 12 -> panelScheduleReturnTab
                                currentTab in listOf(4, 5, 6, 7, 8, 10, 11) -> 9
                                currentTab != 0 -> 0
                                else -> null
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // LAYER 1: Destination Screen Preview (Rendered UNDERNEATH during edge swipe)
                            if (predictiveBackProgress > 0f && peekBackTarget != null && !reduceMotion) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val bgScale = 0.95f + (0.05f * predictiveBackProgress)
                                            scaleX = bgScale
                                            scaleY = bgScale
                                            alpha = 0.5f + (0.5f * predictiveBackProgress)
                                        }
                                ) {
                                    RenderTabContent(
                                        targetTab = peekBackTarget,
                                        uiStateVal = uiStateVal,
                                        viewModel = viewModel,
                                        panelScheduleReturnTab = panelScheduleReturnTab,
                                        showNewProjectConfirmation = { showNewProjectConfirmation = it },
                                        showDiagnosticsDialog = { showDiagnosticsDialog = it },
                                        onSetPanelScheduleReturnTab = { panelScheduleReturnTab = it },
                                        onNavigateTab = { currentTab = it },
                                        onOpenAssistant = { entryPoint, eType, eId, eName ->
                                            openAssistantWithContext(entryPoint, eType, eId, eName)
                                        }
                                    )
                                }
                            }

                            // LAYER 2: Active Screen Content (Scales down on TOP with draw-phase deferred transforms)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val progress = predictiveBackProgress
                                        val isReduced = reduceMotion

                                        val scaleFactor = if (isReduced) 1f else (1f - (progress * 0.08f))
                                        val alphaFactor = if (isReduced) 1f else (1f - (progress * 0.15f))
                                        val cornerRadiusPx = if (isReduced) 0f else (progress * 16.dp.toPx())

                                        scaleX = scaleFactor
                                        scaleY = scaleFactor
                                        alpha = alphaFactor

                                        if (cornerRadiusPx > 0f) {
                                            shape = RoundedCornerShape(cornerRadiusPx)
                                            clip = true
                                        } else {
                                            clip = false
                                        }
                                    }
                            ) {
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        if (isPredictiveBackNav) {
                                            EnterTransition.None togetherWith ExitTransition.None
                                        } else {
                                            // SLICE 8B: restrained shared-axis composition —
                                            // directional short slide + subtle scale + alpha.
                                            VoltAtlasMotion.tabContentTransform(
                                                direction = VoltAtlasMotion.navigationDirection(initialState, targetState),
                                                reduceMotion = reduceMotion
                                            )
                                        }
                                    },
                                    label = "MainTabSwitchAnimation"
                                ) { targetTab ->
                                    RenderTabContent(
                                        targetTab = targetTab,
                                        uiStateVal = uiStateVal,
                                        viewModel = viewModel,
                                        panelScheduleReturnTab = panelScheduleReturnTab,
                                        showNewProjectConfirmation = { showNewProjectConfirmation = it },
                                        showDiagnosticsDialog = { showDiagnosticsDialog = it },
                                        onSetPanelScheduleReturnTab = { panelScheduleReturnTab = it },
                                        onNavigateTab = { currentTab = it },
                                        onOpenAssistant = { entryPoint, eType, eId, eName ->
                                            openAssistantWithContext(entryPoint, eType, eId, eName)
                                        }
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
    } // SLICE 8B: close SharedAxisEnter workspace wrapper

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
                shape = RoundedCornerShape(VoltAtlasShapes.card),
                colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                border = BorderStroke(1.dp, ThemeBorderSubtle)
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

    if (isAssistantInitialized) {
        val assistantVm = resolveAssistantViewModel()
        com.example.ai.ui.EngineeringAssistantSheet(viewModel = assistantVm)
    }
}

@Composable
private fun RenderTabContent(
    targetTab: Int,
    uiStateVal: UiState.Success,
    viewModel: EngineeringViewModel,
    panelScheduleReturnTab: Int,
    showNewProjectConfirmation: (Boolean) -> Unit,
    showDiagnosticsDialog: (Boolean) -> Unit,
    onSetPanelScheduleReturnTab: (Int) -> Unit,
    onNavigateTab: (Int) -> Unit,
    onOpenAssistant: ((com.example.ai.ui.AssistantEntryPoint, String?, String?, String?) -> Unit)? = null
) {
    when (targetTab) {
        0 -> DashboardScreen(
            uiStateVal, 
            onNavigate = onNavigateTab,
            onOpenAssistant = { ep -> onOpenAssistant?.invoke(ep, null, null, null) }
        )
        1 -> CircuitsGridScreen(
            uiStateVal, 
            viewModel, 
            onNavigateToPanelSchedule = { circId -> viewModel.selectedCircuitId.value = circId; onSetPanelScheduleReturnTab(1); onNavigateTab(12) },
            onOpenAssistant = { ep, circ -> onOpenAssistant?.invoke(ep, "CIRCUIT", circ.id.toString(), circ.circuitName) }
        )
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
                onSavePfTransformer = { viewModel.savePfTransformerSettings(it) },
                onAddGenerator = { viewModel.addGenerator() },
                onDeleteGenerator = { viewModel.deleteGenerator(it) },
                onAddAts = { viewModel.addAts() },
                onDeleteAts = { viewModel.deleteAts(it) },
                onAddUps = { viewModel.addUps() },
                onDeleteUps = { viewModel.deleteUps(it) }
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
            generators = uiStateVal.generators,
            generatorResults = uiStateVal.generatorResults,
            atsResult = uiStateVal.atsResult,
            atsList = uiStateVal.atsList,
            atsResults = uiStateVal.atsResults
        )
        6 -> ConduitFillScreen(viewModel)
        7 -> BoqProcurementScreen(uiStateVal)
        8 -> {
            GlobalSettingsScreen(
                viewModel = viewModel,
                preferences = viewModel.preferences,
                onBack = { onNavigateTab(0) }
            )
        }
        9 -> MoreModulesGrid(onNavigate = { targetTab -> if (targetTab == 12) onSetPanelScheduleReturnTab(9); onNavigateTab(targetTab) })
        10 -> ConversionsContainerScreen(onBack = { onNavigateTab(9) })
        11 -> FormulasMethodologyScreen()
        12 -> com.example.ui.screens.PanelScheduleScreen(
            viewModel = viewModel,
            uiState = uiStateVal,
            onBack = { onNavigateTab(panelScheduleReturnTab) }
        )
        else -> DashboardScreen(uiStateVal, onNavigate = onNavigateTab)
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

