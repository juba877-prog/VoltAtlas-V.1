package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.scene.DialogSceneStrategy
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
import com.example.navigation.BoqProcurementKey
import com.example.navigation.CircuitsKey
import com.example.navigation.ConduitsKey
import com.example.navigation.ConversionsKey
import com.example.navigation.DiagnosticsDialogKey
import com.example.navigation.EntryKey
import com.example.navigation.MethodologyKey
import com.example.navigation.MoreModulesKey
import com.example.navigation.NewProjectConfirmDialogKey
import com.example.navigation.PanelScheduleKey
import com.example.navigation.PowerSystemsKey
import com.example.navigation.ProjectPickerKey
import com.example.navigation.ProtectionKey
import com.example.navigation.QuickHubKey
import com.example.navigation.QuickToolKey
import com.example.navigation.SldKey
import com.example.navigation.TransformersKey
import com.example.navigation.WorkspaceHomeKey
import com.example.navigation.WorkspaceNavigator
import com.example.navigation.WorkspaceListDetailScene
import com.example.navigation.rememberWorkspaceListDetailSceneStrategy
import com.example.navigation.WorkspaceSettingsKey
import com.example.navigation.rememberWorkspaceNavigationState
import com.example.navigation.toDecoratedEntries
import com.example.navigation.workspaceTabIndex
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

    // Navigation 3 back stack for the app-level flow (entry / picker / quick engineering).
    // Hoisted above the workspace branch so the stack survives while a project is open and
    // the workspace temporarily replaces the NavDisplay.
    val appBackStack = rememberNavBackStack(EntryKey)

    // Navigation 3 workspace state (multiple back stacks). Hoisted above every branch so the
    // per-tab stacks survive while the app-level flow (or the welcome sheet) is on screen.
    val workspaceState = rememberWorkspaceNavigationState()
    val workspaceNavigator = remember(workspaceState) {
        WorkspaceNavigator(workspaceState) { viewModel.closeProject() }
    }
    // Legacy Int tab index of the visible workspace destination: the shell chrome
    // (bottom bar / rail selection, module title, assistant context) keeps speaking
    // the pre-Navigation 3 `currentTab` contract.
    val currentTabIndex = workspaceState.currentKey.workspaceTabIndex()

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
                workspaceNavigator.navigateToTabIndex(11)
            }
        )
    }

    var showPdfExportDialog by rememberSaveable { mutableStateOf(false) }
    val pdfScope = rememberCoroutineScope()
    var currentReportOptions by remember { mutableStateOf(com.example.pdf.ReportOptions()) }

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

    // ===== APP-LEVEL FLOW (Navigation 3) =====
    // Entry -> { Project Picker | Quick Engineering hub -> Quick tool } now lives on a
    // Navigation 3 back stack. The workspace (project open) keeps the existing state-based
    // router and temporarily replaces the NavDisplay; the stack is hoisted above so it
    // survives that period.
    //
    // Entry-gate recovery: while the stack root (mode selection) is current, the NavDisplay
    // owns the frame even if a project is already active (e.g. restored from storage), so a
    // restored workspace still passes through the entry screen first.
    val isOnAppRoot = appBackStack.lastOrNull() == EntryKey
    if (isOnAppRoot || activeId == null) {
        NavDisplay(
            backStack = appBackStack,
            onBack = {
                // Root destination: the Entry screen owns Back (exit confirmation).
                if (appBackStack.size > 1) appBackStack.removeLastOrNull()
            },
            entryProvider = { key ->
                when (key) {
                    is EntryKey -> NavEntry(key) {
                        VoltAtlasEntryScreen(
                            onOpenProjects = { appBackStack.add(ProjectPickerKey) },
                            onOpenQuickEngineering = { appBackStack.add(QuickHubKey) },
                            onOpenSettings = { viewModel.openGlobalSettings() }
                        )
                    }
                    is ProjectPickerKey -> NavEntry(key) {
                        ProjectPickerScreen(
                            projects = projects,
                            onCreateProject = { name -> viewModel.createAndOpenProject(name) },
                            onOpenProject = { id -> viewModel.openProject(id) },
                            onRenameProject = { id, newName -> viewModel.renameProject(id, newName) },
                            onDeleteProject = { id -> viewModel.deleteProject(id) },
                            onExportProjectJson = { id -> viewModel.exportProjectJson(id) },
                            onImportProjectJson = { jsonStr, callback -> viewModel.importProjectJson(jsonStr, callback) },
                            onOpenSettings = { viewModel.openGlobalSettings() },
                            onBackToEntry = { appBackStack.removeLastOrNull() },
                            confirmDestructiveActions = confirmDestructiveActions
                        )
                    }
                    is QuickHubKey -> NavEntry(key) {
                        QuickEngineeringHubScreen(
                            onSelectTool = { appBackStack.add(QuickToolKey(it)) },
                            onBack = { appBackStack.removeLastOrNull() }
                        )
                    }
                    is QuickToolKey -> NavEntry(key) {
                        QuickToolDestination(
                            toolId = key.toolId,
                            onBack = { appBackStack.removeLastOrNull() }
                        )
                    }
                    else -> error("Unknown VoltAtlas destination: $key")
                }
            }
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
    var isSldFullscreen by rememberSaveable { mutableStateOf(false) }
    val sldManualPositions = remember(activeProject?.projectId) { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }

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
        val projectName = activeProject?.projectName ?: "VoltAtlas Project"
        val currentScreenName = when (currentTabIndex) {
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

        val successState = state as? UiState.Success
        // Auto-detect circuit context when opened from top bar without explicit entity
        val resolvedEntityType: String?
        val resolvedEntityId: String?
        val resolvedEntityName: String?
        val circuit: com.example.data.CircuitEntity?
        if (entityType == "CIRCUIT" && entityId != null) {
            circuit = successState?.circuits?.find { it.id.toString() == entityId || it.circuitName == entityName }
            resolvedEntityType = entityType
            resolvedEntityId = entityId
            resolvedEntityName = entityName
        } else {
            // Auto-detect: prefer the only circuit, or the selected circuit, or the first circuit on Circuits tab
            val autoCircuit = when {
                successState?.circuits?.size == 1 -> successState.circuits.first()
                workspaceState.currentKey is CircuitsKey && successState != null -> {
                    val selId = viewModel.selectedCircuitId.value
                    if (selId != null) successState.circuits.find { it.id == selId }
                    else successState.circuits.firstOrNull()
                }
                else -> null
            }
            circuit = autoCircuit
            resolvedEntityType = if (autoCircuit != null) "CIRCUIT" else entityType
            resolvedEntityId = if (autoCircuit != null) autoCircuit.id.toString() else entityId
            resolvedEntityName = if (autoCircuit != null) autoCircuit.circuitName else entityName
        }
        val sizing = if (circuit != null) successState?.sizingResults?.get(circuit.id) else null

        val knownInputs = mutableMapOf<String, String>()
        var validatedSummary: String? = null

        if (circuit != null) {
            knownInputs["installation"] = circuit.installation
            knownInputs["voltage"] = circuit.voltageV.toString()
            knownInputs["loadKVA"] = circuit.loadKVA.toString()
            knownInputs["length"] = circuit.length_m.toString()
            knownInputs["phases"] = circuit.phases
            knownInputs["powerFactor"] = circuit.pf.toString()
            knownInputs["cableType"] = circuit.cableType
            knownInputs["ambientTemp"] = circuit.airTempC.toString()
            knownInputs["parallelRuns"] = circuit.parallelRuns.toString()
            knownInputs["maxVDpct"] = String.format(Locale.US, "%.2f", circuit.maxVDpct * 100.0)
            circuit.overrideCableSize?.let { knownInputs["overrideCableSize"] = it }
            circuit.overrideDeviceSize?.let { knownInputs["overrideDeviceSize"] = String.format(Locale.US, "%.0f", it) }
            circuit.overrideDeviceType?.let { knownInputs["overrideDeviceType"] = it }

            if (sizing != null) {
                knownInputs["cableSize"] = sizing.cableSize_mm2
                knownInputs["cableMaterial"] = sizing.cableMaterial
                knownInputs["insulation"] = sizing.insulation
                knownInputs["designCurrentIb"] = String.format(Locale.US, "%.1f A", sizing.Ib_A)
                knownInputs["breakerSize"] = String.format(Locale.US, "%.0f A", sizing.deviceSize_A)
                knownInputs["breakerType"] = sizing.deviceType
                knownInputs["deratedAmpacityIz"] = String.format(Locale.US, "%.1f A", sizing.capacity_derated_Iz_A)
                knownInputs["deratingFactor"] = String.format(Locale.US, "%.2f", sizing.cTotalDerating)
                knownInputs["voltageDropV"] = String.format(Locale.US, "%.2f V", sizing.voltDrop_V)
                knownInputs["voltageDropPct"] = String.format(Locale.US, "%.2f%%", sizing.vdPct)
                knownInputs["status"] = sizing.status
                knownInputs["utilizationPct"] = String.format(Locale.US, "%.1f%%", sizing.utilizationPct)
                knownInputs["isAmpacityOk"] = sizing.isAmpacityOk.toString()
                knownInputs["isVoltageDropOk"] = sizing.isVoltageDropOk.toString()
                knownInputs["failReason"] = sizing.failReason

                validatedSummary = if (sizing.status == "PASS") {
                    "Circuit ${circuit.circuitName} is sized with ${sizing.cableSize_mm2} mm² ${sizing.cableMaterial}/${sizing.insulation} (${circuit.installation}). Design load Ib = ${String.format(Locale.US, "%.1f", sizing.Ib_A)} A, Breaker = ${String.format(Locale.US, "%.0f", sizing.deviceSize_A)} A (${sizing.deviceType}), Derated Iz = ${String.format(Locale.US, "%.1f", sizing.capacity_derated_Iz_A)} A (derating ${String.format(Locale.US, "%.2f", sizing.cTotalDerating)}), Voltage Drop = ${String.format(Locale.US, "%.2f", sizing.vdPct)}% (${String.format(Locale.US, "%.2f", sizing.voltDrop_V)} V, limit ${String.format(Locale.US, "%.2f", circuit.maxVDpct * 100.0)}%)."
                } else {
                    "Circuit ${circuit.circuitName} sizing is NON-COMPLIANT: ${sizing.failReason.ifBlank { "Constraints violated" }}. Cable ${sizing.cableSize_mm2} mm², Ib = ${String.format(Locale.US, "%.1f", sizing.Ib_A)} A, Iz = ${String.format(Locale.US, "%.1f", sizing.capacity_derated_Iz_A)} A, VD = ${String.format(Locale.US, "%.2f", sizing.vdPct)}%."
                }
            }
        }

        val ctx = com.example.ai.context.EngineeringContext(
            projectId = activeId,
            projectName = projectName,
            currentScreen = currentScreenName,
            activeEntityType = resolvedEntityType,
            activeEntityId = resolvedEntityId,
            activeEntityName = resolvedEntityName,
            projectSummary = summary,
            knownEntityInputs = knownInputs,
            validatedResultSummary = validatedSummary
        )
        isAssistantInitialized = true
        val assistantVm = resolveAssistantViewModel()
        assistantVm.open(entryPoint, ctx)
    }

    // Window-level navigation decision: centralized width policy, NOT
    // orientation. A landscape phone window is still too narrow for a
    // rail-plus-content composition, while a portrait tablet window is wide
    // enough. Destination ordering, selection state, predictive-back
    // behavior, and test tags are unchanged.
    val useNavRail = com.example.ui.adaptive.rememberVoltAtlasWindowWidth().supportsNavRail

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

    val moduleName = when (currentTabIndex) {
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

    if (workspaceState.currentKey is SldKey && isSldFullscreen) {
        val uiStateVal = state
        if (uiStateVal is UiState.Success) {
            SldScreen(
                state = uiStateVal,
                activeProjectId = activeProject?.projectId ?: "",
                projectName = activeProject?.projectName ?: "VoltAtlas",
                isFullscreen = true,
                onToggleFullscreen = { isSldFullscreen = it },
                onBackToProjects = { viewModel.closeProject() },
                onNavigateToTab = { workspaceNavigator.navigateToTabIndex(it) },
                manualPositions = sldManualPositions,
                projectRevision = activeProject?.revision,
                modifier = Modifier.fillMaxSize().statusBarsPadding()
            )
            return
        }
    }

    // System Back closes an open SLD fullscreen session before the stack pops.
    BackHandler(enabled = isSldFullscreen) { isSldFullscreen = false }

    // System Back at a top-level base has nothing for NavDisplay to pop, so the explicit
    // workspace hierarchy runs: non-Home destination -> Home, Home -> close project
    // (returns to the Project Picker). Deeper pops stay with NavDisplay (animated).
    BackHandler(enabled = !isSldFullscreen && workspaceState.isAtTopLevelBase) {
        workspaceNavigator.goBack()
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
                            isMoreTabSelected(currentTabIndex)
                        } else {
                            currentTabIndex == targetTab
                        }
                        val isCustomVector = icon == ElectricalIcons.Home ||
                                icon == ElectricalIcons.Circuits ||
                                icon == ElectricalIcons.Transformer ||
                                icon == ElectricalIcons.Power
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = {
                                workspaceNavigator.navigateToTabIndex(targetTab)
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
                                    onClick = { workspaceNavigator.navigate(DiagnosticsDialogKey) },
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
                                                workspaceNavigator.navigate(DiagnosticsDialogKey)
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
                                                workspaceNavigator.navigate(NewProjectConfirmDialogKey)
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
                                isMoreTabSelected(currentTabIndex)
                            } else {
                                currentTabIndex == targetTab
                            }
                            val fullLabel = navItemsFull.find { it.third == targetTab }?.first ?: title
                            val isCustomVector = icon == ElectricalIcons.Home ||
                                    icon == ElectricalIcons.Circuits ||
                                    icon == ElectricalIcons.Transformer ||
                                    icon == ElectricalIcons.Power
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    workspaceNavigator.navigateToTabIndex(targetTab)
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
                        val tabEntry: @Composable (Int) -> Unit = { index ->
                            RenderTabContent(
                                targetTab = index,
                                uiStateVal = uiStateVal,
                                viewModel = viewModel,
                                onNavigateTab = { tabIndex -> workspaceNavigator.navigateToTabIndex(tabIndex) },
                                onGoBack = { workspaceNavigator.goBack() },
                                onOpenNewProjectConfirm = { workspaceNavigator.navigate(NewProjectConfirmDialogKey) },
                                onOpenDiagnostics = { workspaceNavigator.navigate(DiagnosticsDialogKey) },
                                onOpenAssistant = { entryPoint, eType, eId, eName ->
                                    openAssistantWithContext(entryPoint, eType, eId, eName)
                                }
                            )
                        }
                        val workspaceDialogStrategy = remember { DialogSceneStrategy<NavKey>() }
                        // Phase 4 adaptive: on windows of at least 600dp the More-modules hub
                        // becomes the list pane and the selected module the detail pane; phones
                        // and narrow split-screen windows keep the single-pane layout. The
                        // breakpoint matches the shell's rail-vs-bottom-bar policy.
                        val listDetailStrategy = rememberWorkspaceListDetailSceneStrategy<NavKey> {
                            WorkspaceModulePlaceholder()
                        }
                        NavDisplay(
                            entries = workspaceState.toDecoratedEntries(
                                entryProvider = entryProvider {
                                    entry<WorkspaceHomeKey> { tabEntry(0) }
                                    entry<CircuitsKey> { tabEntry(1) }
                                    entry<TransformersKey> { tabEntry(2) }
                                    entry<PowerSystemsKey> { tabEntry(3) }
                                    entry<MoreModulesKey>(
                                        metadata = WorkspaceListDetailScene.listPane()
                                    ) { tabEntry(9) }
                                    entry<ProtectionKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(4) }
                                    entry<SldKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(5) }
                                    entry<ConduitsKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(6) }
                                    entry<BoqProcurementKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(7) }
                                    entry<WorkspaceSettingsKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(8) }
                                    entry<ConversionsKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(10) }
                                    entry<MethodologyKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(11) }
                                    entry<PanelScheduleKey>(metadata = WorkspaceListDetailScene.detailPane()) { tabEntry(12) }
                                    entry<NewProjectConfirmDialogKey>(
                                        metadata = DialogSceneStrategy.dialog()
                                    ) {
                                        WorkspaceNewProjectDialogContent(
                                            onConfirm = {
                                                workspaceNavigator.goBack()
                                                viewModel.startNewEmptyProject()
                                            },
                                            onDismiss = { workspaceNavigator.goBack() }
                                        )
                                    }
                                    entry<DiagnosticsDialogKey>(
                                        metadata = DialogSceneStrategy.dialog()
                                    ) {
                                        WorkspaceDiagnosticsDialogContent(
                                            state = uiStateVal,
                                            onDismiss = { workspaceNavigator.goBack() }
                                        )
                                    }
                                }
                            ),
                            onBack = { workspaceNavigator.goBack() },
                            sceneStrategies = listOf(workspaceDialogStrategy, listDetailStrategy),
                            transitionSpec = {
                                VoltAtlasMotion.tabContentTransform(
                                    direction = VoltAtlasMotion.NavigationDirection.Forward,
                                    reduceMotion = reduceMotion
                                )
                            },
                            popTransitionSpec = {
                                VoltAtlasMotion.tabContentTransform(
                                    direction = VoltAtlasMotion.NavigationDirection.Backward,
                                    reduceMotion = reduceMotion
                                )
                            }
                        )
                    }

                }
            }
        }
    }
    }
    } // SLICE 8B: close SharedAxisEnter workspace wrapper

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
    onNavigateTab: (Int) -> Unit,
    onGoBack: () -> Unit,
    onOpenNewProjectConfirm: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
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
            onNavigateToPanelSchedule = { circId -> viewModel.selectedCircuitId.value = circId; onNavigateTab(12) },
            onOpenAssistant = { ep, circ -> onOpenAssistant?.invoke(ep, "CIRCUIT", circ.id.toString(), circ.circuitName) }
        )
        2 -> TransformersScreen(uiStateVal, viewModel)
        3 -> {
            val genSet = viewModel.generatorSettings.collectAsState().value ?: com.example.data.GeneratorEntity()
            val atsSet = viewModel.atsSettings.collectAsState().value ?: com.example.data.AtsEntity()
            val upsSet = viewModel.upsSettings.collectAsState().value ?: com.example.data.UpsEntity()
            val pfGlob = viewModel.pfGlobalSettings.collectAsState().value ?: com.example.data.PfGlobalEntity()
            val pfTrans = viewModel.pfTransformerSettings.collectAsState().value
            val pendingDeleteVal by viewModel.pendingDeleteConfirmation.collectAsState()
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
                onDeleteGenerator = { viewModel.requestDeleteGenerator(it) },
                onAddAts = { viewModel.addAts() },
                onDeleteAts = { viewModel.requestDeleteAts(it) },
                onAddUps = { viewModel.addUps() },
                onDeleteUps = { viewModel.requestDeleteUps(it) },
                pendingDeleteConfirmation = pendingDeleteVal,
                onConfirmDelete = { viewModel.confirmPendingDelete() },
                onCancelDelete = { viewModel.cancelPendingDelete() }
            )
        }
        4 -> ProtectionScreen(uiStateVal, onSaveEarthing = { viewModel.onSaveEarthing(it) })
        5 -> {
            val projectsVal by viewModel.projects.collectAsState()
            val activeIdVal by viewModel.activeProjectId.collectAsState()
            val activeProjVal = projectsVal.find { it.projectId == activeIdVal }
            SldScreen(
                circuits = uiStateVal.circuits,
                activeProjectId = activeIdVal ?: "",
                projectName = activeProjVal?.projectName ?: "VoltAtlas",
                sizingResults = uiStateVal.sizingResults,
                shortCircuitResults = uiStateVal.shortCircuitResults,
                transformers = uiStateVal.transformers,
                transformerResults = uiStateVal.transformerResults,
                generators = uiStateVal.generators,
                generatorResults = uiStateVal.generatorResults,
                atsList = uiStateVal.atsList,
                atsResults = uiStateVal.atsResults,
                upsList = uiStateVal.upsList,
                upsResults = uiStateVal.upsResults,
                protectionResults = uiStateVal.protectionResults,
                adsResults = uiStateVal.adsResults,
                projectRevision = activeProjVal?.revision,
                onNavigateToTab = onNavigateTab
            )
        }
        6 -> ConduitFillScreen(viewModel)
        7 -> BoqProcurementScreen(uiStateVal)
        8 -> {
            GlobalSettingsScreen(
                viewModel = viewModel,
                preferences = viewModel.preferences,
                onBack = { onNavigateTab(0) }
            )
        }
        9 -> MoreModulesGrid(onNavigate = { targetTab -> onNavigateTab(targetTab) })
        10 -> ConversionsContainerScreen(onBack = { onGoBack() })
        11 -> FormulasMethodologyScreen()
        12 -> com.example.ui.screens.PanelScheduleScreen(
            viewModel = viewModel,
            uiState = uiStateVal,
            onBack = { onGoBack() }
        )
        else -> DashboardScreen(uiStateVal, onNavigate = onNavigateTab)
    }
}
/**
 * Hosts a single Quick Engineering tool destination on the Navigation 3 back stack.
 * Tool screens keep their existing top-bar Back wiring; system Back pops the stack.
 */
@Composable
private fun QuickToolDestination(
    toolId: String,
    onBack: () -> Unit
) {
    when (toolId) {
        "cable_sizing" -> QuickCableCalcScreen(onBack = onBack)
        "voltage_drop" -> com.example.ui.screens.quick.QuickVoltageDropScreenV2(onBack = onBack)
        "conduit_fill" -> com.example.ui.screens.quick.QuickConduitFillScreen(onBack = onBack)
        "load_current" -> com.example.ui.screens.quick.QuickLoadCurrentScreenV2(onBack = onBack)
        "power_triangle" -> QuickPowerTriangleScreen(onBack = onBack)
        "pfc" -> com.example.ui.screens.quick.QuickPfcScreenV2(onBack = onBack)
        "motor_starter" -> com.example.ui.screens.quick.QuickMotorStarterScreen(onBack = onBack)
        "breaker_select" -> com.example.ui.screens.quick.QuickBreakerSelectScreen(onBack = onBack)
        "earthing" -> com.example.ui.screens.quick.QuickEarthingScreen(onBack = onBack)
        "generator_quick" -> com.example.ui.screens.quick.QuickGeneratorScreen(onBack = onBack)
        "ups_battery" -> com.example.ui.screens.quick.QuickUpsScreen(onBack = onBack)
        "ats_sizing" -> com.example.ui.screens.quick.QuickAtsScreen(onBack = onBack)
        "transformer_quick" -> QuickTransformerScreen(onBack = onBack)
        "transformer_sc" -> QuickTransformerScScreen(onBack = onBack)
        "lighting" -> QuickLightingScreen(onBack = onBack)
        else -> QuickCableCalcScreen(onBack = onBack)
    }
}

/**
 * Detail-pane placeholder for the adaptive More-modules list-detail scene: on wide
 * windows the hub occupies the list pane and this guidance fills the detail pane
 * until a module is selected.
 */
@Composable
private fun WorkspaceModulePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Select a module to open it here",
            color = SlateTextSecondary,
            modifier = Modifier.testTag("workspace_list_detail_placeholder")
        )
    }
}

