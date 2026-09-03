package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.ui.designsystem.icons.ElectricalIcons
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.sld.PositionedSldNode
import com.example.sld.SldCanvas
import com.example.sld.SldLayoutEngine
import com.example.sld.SldSymbolGallery
import com.example.sld.SldToolbar
import com.example.sld.DisplayMode
import com.example.sld.SldDetailLevel
import com.example.sld.SldDetailLevelPolicy
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SldScreen(
    circuits: List<CircuitEntity>,
    activeProjectId: String = "",
    sizingResults: Map<Int, SizingResult> = emptyMap(),
    shortCircuitResults: Map<Int, ShortCircuitResult> = emptyMap(),
    atsEntity: com.example.data.AtsEntity? = null,
    atsResult: com.example.engine.AtsResult? = null,
    transformers: List<com.example.data.TransformerEntity> = emptyList(),
    transformerResults: Map<String, com.example.engine.TransformerResult> = emptyMap(),
    generatorResult: com.example.engine.GeneratorResult? = null,
    generators: List<com.example.data.GeneratorEntity> = emptyList(),
    generatorResults: Map<String, com.example.engine.GeneratorResult> = emptyMap(),
    atsList: List<com.example.data.AtsEntity> = emptyList(),
    atsResults: Map<String, com.example.engine.AtsResult> = emptyMap(),
    upsEntity: com.example.data.UpsEntity? = null,
    upsResult: com.example.engine.UpsResult? = null,
    upsList: List<com.example.data.UpsEntity> = emptyList(),
    upsResults: Map<String, com.example.engine.UpsResult> = emptyMap(),
    manualPositions: MutableMap<String, Offset> = remember { mutableStateMapOf() },
    onNavigateToTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.example.data.AppDatabase.getDatabase(context) }
    val repository = remember { com.example.data.EngineeringRepository(db) }
    val effectiveProjId = activeProjectId.ifBlank { circuits.firstOrNull()?.projectId ?: transformers.firstOrNull()?.projectId ?: "DEFAULT_PROJECT" }

    val dbPositionsFlow = remember(effectiveProjId) {
        repository.sldNodePositionsFlow(effectiveProjId)
    }
    val dbPositions by dbPositionsFlow.collectAsState(initial = emptyList())

    LaunchedEffect(dbPositions) {
        dbPositions.forEach { pos ->
            manualPositions[pos.nodeId] = Offset(pos.x, pos.y)
        }
    }

    var layoutModeEnabled by remember { mutableStateOf(false) }
    var isToolbarExpanded by remember { mutableStateOf(false) }

    val loadingMap = remember(sizingResults) {
        sizingResults.mapValues { (_, res) -> res.utilizationPct }
    }
    
    val engineResult = remember(circuits, effectiveProjId, transformers, sizingResults, transformerResults, generatorResult, generators, generatorResults, atsResult, atsEntity, atsList, atsResults, upsEntity, upsResult, upsList, upsResults, manualPositions.toMap()) {
        try {
            SldLayoutEngine.calculateLayout(
                activeProjectId = effectiveProjId,
                transformers = transformers,
                circuits = circuits,
                sizingResults = sizingResults,
                transformerResults = transformerResults,
                shortCircuitResults = shortCircuitResults,
                generatorResult = generatorResult,
                atsResult = atsResult,
                atsEntity = atsEntity,
                upsEntity = upsEntity,
                upsResult = upsResult,
                generators = generators,
                generatorResults = generatorResults,
                atsList = atsList,
                atsResults = atsResults,
                upsList = upsList,
                upsResults = upsResults,
                manualPositions = manualPositions
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            com.example.sld.SldEngineResult(
                nodes = emptyList(),
                edges = emptyList(),
                bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 1000f, 800f),
                validationIssues = listOf(
                    com.example.sld.SldValidationIssue(
                        affectedNodeId = "error_layout",
                        componentName = "SLD Layout Engine",
                        failedCondition = "Layout calculation exception: ${e.localizedMessage}",
                        issueCode = "ENGINE_ERROR",
                        severity = com.example.sld.SldValidationStatus.INVALID,
                        recommendedCorrection = "Check transformer & circuit configuration"
                    )
                )
            )
        }
    }

    val isDarkTheme = com.example.ui.theme.LocalThemeIsDark.current
    val palette = com.example.sld.SldTheme.palette(isDarkTheme)

    // SAF Launcher for SLD PNG Image Export
    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bitmap = com.example.sld.SldImageExporter.renderSldBitmap(
                        engineResult = engineResult,
                        projectName = activeProjectId.ifBlank { "VoltAtlas_SLD" },
                        isDarkTheme = isDarkTheme
                    )
                    if (bitmap != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            com.example.sld.SldImageExporter.writeBitmapToStream(bitmap, out)
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "SLD Image saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Diagram empty. Please add components first.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to save image: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // SAF Launcher for SLD DXF Export
    val saveDxfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/dxf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    if (engineResult.nodes.isEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Diagram not ready or empty. Please add components first.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val dxfContent = com.example.sld.SldDxfExporter.generateDxf(
                        engineResult,
                        activeProjectId.ifBlank { "VoltAtlas_SLD" }
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        com.example.sld.SldDxfExporter.writeDxfToStream(dxfContent, out)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "SLD DXF saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to save DXF: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var hasAutoFitOnLoad by remember { mutableStateOf(false) }

    var selectedNode by remember { mutableStateOf<PositionedSldNode?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showSymbolGallery by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isMinimapVisible by remember { mutableStateOf(true) }
    var isValidationOpen by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(DisplayMode.OVERVIEW) }
    var detailLevel by remember { mutableStateOf(SldDetailLevel.STANDARD) }

    // Auto "Fit to Screen" calculation
    fun fitToScreen() {
        if (viewportSize.width > 0f && viewportSize.height > 0f) {
            val (fitScale, fitPan) = com.example.sld.SldViewportHelper.calculateFitViewport(
                contentBounds = engineResult.bounds,
                viewportSize = viewportSize
            )
            zoomScale = fitScale
            panOffset = fitPan
        }
    }

    LaunchedEffect(viewportSize, engineResult) {
        if (!hasAutoFitOnLoad && viewportSize.width > 0f && viewportSize.height > 0f) {
            fitToScreen()
            hasAutoFitOnLoad = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .onSizeChanged { intSize ->
                viewportSize = Size(intSize.width.toFloat(), intSize.height.toFloat())
            }
            .testTag("sld_screen_container")
    ) {
        // 2. Interactive SLD Canvas or Empty State
        if (circuits.isEmpty() && transformers.isEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(0.88f),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = ElectricalIcons.Sld,
                        contentDescription = "Empty SLD",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Diagram Available",
                        color = ThemeTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add circuits and panels to generate the single-line diagram.",
                        color = SlateTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            SldCanvas(
                engineResult = engineResult,
                zoomScale = zoomScale,
                panOffset = panOffset,
                selectedNodeId = selectedNode?.id,
                displayMode = displayMode,
                detailLevel = detailLevel,
                onNodeClick = { pNode ->
                    selectedNode = pNode
                    showBottomSheet = true
                },
                onCanvasClick = {
                    selectedNode = null
                    showBottomSheet = false
                },
                onDoubleTap = { tapPos ->
                    val newScale = (zoomScale * 1.8f).coerceIn(0.05f, 4.0f)
                    val focusX = (tapPos.x - panOffset.x) / zoomScale
                    val focusY = (tapPos.y - panOffset.y) / zoomScale
                    zoomScale = newScale
                    panOffset = Offset(
                        tapPos.x - focusX * newScale,
                        tapPos.y - focusY * newScale
                    )
                },
                onPanZoomChange = { panChange, zoomChange ->
                    val newScale = (zoomScale * zoomChange).coerceIn(0.05f, 4.0f)
                    zoomScale = newScale
                    panOffset = com.example.sld.SldViewportHelper.clampPan(
                        zoomScale = newScale,
                        panOffset = panOffset + panChange,
                        contentBounds = engineResult.bounds,
                        viewportSize = viewportSize
                    )
                },
                isEditMode = layoutModeEnabled,
                onNodeDrag = { nodeId, dragDelta ->
                    val posNode = engineResult.nodes.find { it.id == nodeId }
                    val currentPos = manualPositions[nodeId] ?: posNode?.let { Offset(it.x, it.y) } ?: Offset.Zero
                    manualPositions[nodeId] = currentPos + dragDelta
                },
                onNodeDragEnd = { nodeId ->
                    val finalPos = manualPositions[nodeId]
                    if (finalPos != null) {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            repository.saveSldNodePosition(
                                com.example.data.SldNodePositionEntity(
                                    projectId = effectiveProjId,
                                    nodeId = nodeId,
                                    x = finalPos.x,
                                    y = finalPos.y
                                )
                            )
                        }
                    }
                },
                isDarkTheme = isDarkTheme,
                modifier = Modifier.fillMaxSize().testTag("sld_canvas")
            )
        }

        // 1. Slim Translucent Top Bar (Glassmorphism)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(20f),
            color = if (isDarkTheme) Color(0xE6131B2E) else Color(0xF2F8FAFC),
            border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = activeProjectId.ifBlank { "VoltAtlas SLD" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "SLD • ${displayMode.name} MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SleekPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isSearchOpen = !isSearchOpen },
                        modifier = Modifier.size(36.dp).testTag("sld_search_toggle_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search Diagram", tint = SleekPrimary, modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = { isMinimapVisible = !isMinimapVisible },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Toggle Radar Minimap", tint = if (isMinimapVisible) SleekPrimary else Color(0xFF94A3B8), modifier = Modifier.size(19.dp))
                    }
                }
            }
        }

        // 2. Animated Search Bar Overlay
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(30f)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SleekPrimary)
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search boards, breakers, ratings...", fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isSearchOpen = false; searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color(0xFF94A3B8))
                    }
                }
            }
        }

        // 3. Top-Right Collapsible Radar Minimap (110x75dp)
        AnimatedVisibility(
            visible = isMinimapVisible && engineResult.nodes.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 58.dp, end = 14.dp)
                .zIndex(15f)
        ) {
            Box(modifier = Modifier.size(110.dp, 75.dp)) {
                com.example.sld.SldMinimap(
                    engineResult = engineResult,
                    viewportSize = viewportSize,
                    panOffset = panOffset,
                    zoomScale = zoomScale,
                    onPanChanged = { newPan -> panOffset = newPan },
                    onClose = { isMinimapVisible = false },
                    manualPositions = manualPositions
                )
            }
        }

        // 4. Edit Mode Top Banner Indicator
        if (layoutModeEnabled) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .zIndex(18f),
                shape = RoundedCornerShape(20.dp),
                color = SleekPrimary.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Mode", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EDIT MODE — Drag nodes to reposition", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { layoutModeEnabled = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Unified Bottom CAD Action Dock
        SldToolbar(
            displayMode = displayMode,
            onDisplayModeChange = { displayMode = it },
            issueCount = engineResult.validationIssues.size,
            isSearchOpen = isSearchOpen,
            onToggleSearch = { isSearchOpen = !isSearchOpen },
            isValidationOpen = isValidationOpen,
            onToggleValidation = { isValidationOpen = !isValidationOpen },
            onFitScreen = { fitToScreen() },
            onFitSelection = { fitToScreen() },
            onResetView = { fitToScreen() },
            onExpandAll = {},
            onCollapseAll = {},
            layoutModeEnabled = layoutModeEnabled,
            onToggleLayoutMode = { layoutModeEnabled = !layoutModeEnabled },
            onResetLayout = {
                manualPositions.clear()
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    repository.clearSldNodePositionsForProject(effectiveProjId)
                }
                android.widget.Toast.makeText(context, "Layout reset to auto-tree hierarchy", android.widget.Toast.LENGTH_SHORT).show()
            },
            detailLevel = detailLevel,
            onCycleDetailLevel = { detailLevel = SldDetailLevelPolicy.next(detailLevel) },
            isMinimapVisible = isMinimapVisible,
            onToggleMinimap = { isMinimapVisible = !isMinimapVisible },
            isFullscreen = false,
            onToggleFullscreen = {},
            onExportImage = {
                if (engineResult.nodes.isEmpty()) {
                    android.widget.Toast.makeText(context, "Diagram empty. Please add components first.", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val safeName = activeProjectId.ifBlank { "VoltAtlas" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    savePngLauncher.launch("${safeName}_SLD.png")
                }
            },
            onShareImage = {
                com.example.sld.SldImageExporter.exportAndShareImage(
                    context = context,
                    engineResult = engineResult,
                    projectName = activeProjectId.ifBlank { "VoltAtlas_SLD" },
                    isDarkTheme = isDarkTheme
                )
            },
            onExportDxf = {
                val safeProjectName = activeProjectId
                    .trim()
                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    .takeIf { it.isNotBlank() } ?: "VoltAtlas"
                val suggestedFileName = "${safeProjectName}_SLD.dxf"

                if (engineResult.nodes.isNotEmpty()) {
                    saveDxfLauncher.launch(suggestedFileName)
                } else {
                    android.widget.Toast.makeText(context, "Diagram not ready or empty. Please add components first.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onShareDxf = {
                val safeProjectName = activeProjectId
                    .trim()
                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    .takeIf { it.isNotBlank() } ?: "VoltAtlas"

                if (engineResult.nodes.isNotEmpty()) {
                    com.example.sld.SldDxfExporter.exportAndShareDxf(
                        context = context,
                        engineResult = engineResult,
                        projectName = safeProjectName
                    )
                } else {
                    android.widget.Toast.makeText(context, "Diagram not ready or empty. Please add components first.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onOpenSymbolGallery = { showSymbolGallery = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .zIndex(25f)
        )

        // 4. Node Detail Bottom Sheet (Thumb-Reachable)
        if (showBottomSheet && selectedNode != null) {
            val currentNode = selectedNode ?: return
            com.example.sld.SldDetailsSheet(
                node = currentNode.toSldNode(activeProjectId),
                issues = engineResult.validationIssues,
                onClose = { showBottomSheet = false },
                onFocusUpstreamPath = { nodeId ->
                    val incomingEdge = engineResult.edges.find { it.toId == currentNode.id }
                    val parentId = incomingEdge?.fromId ?: currentNode.circuit?.fedFrom
                    val parentNode = engineResult.nodes.find { it.id == parentId || it.label == parentId }
                    if (parentNode != null) {
                        selectedNode = parentNode
                        panOffset = Offset(
                            -parentNode.x * zoomScale + viewportSize.width / 2f,
                            -parentNode.y * zoomScale + viewportSize.height / 2f
                        )
                    }
                },
                onFocusDownstreamBranch = { nodeId ->
                    val outgoingEdge = engineResult.edges.find { it.fromId == currentNode.id }
                    val childId = outgoingEdge?.toId
                    val childNode = engineResult.nodes.find { it.id == childId }
                    if (childNode != null) {
                        selectedNode = childNode
                        panOffset = Offset(
                            -childNode.x * zoomScale + viewportSize.width / 2f,
                            -childNode.y * zoomScale + viewportSize.height / 2f
                        )
                    }
                },
                onNavigateToTab = { targetTab ->
                    showBottomSheet = false
                    onNavigateToTab(targetTab)
                }
            )
        }

        // 5. Symbol Library Bottom Sheet
        if (showSymbolGallery) {
            ModalBottomSheet(
                onDismissRequest = { showSymbolGallery = false },
                containerColor = palette.surface,
                scrimColor = Color.Black.copy(alpha = 0.5f)
            ) {
                SldSymbolGallery(
                    onDismiss = { showSymbolGallery = false },
                    modifier = Modifier.fillMaxHeight(0.85f)
                )
            }
        }

        // 6. Search Overlay Panel
        if (isSearchOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                com.example.sld.SldSearchPanel(
                    allNodes = engineResult.nodes,
                    onSelectNode = { posNode ->
                        selectedNode = posNode
                        showBottomSheet = true
                        isSearchOpen = false
                        panOffset = Offset(-posNode.x + viewportSize.width / 2f, -posNode.y + viewportSize.height / 2f)
                    },
                    onClose = { isSearchOpen = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }

        // 7. Validation Issues Overlay Panel
        if (isValidationOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                com.example.sld.SldValidationPanel(
                    issues = engineResult.validationIssues,
                    onFocusNode = { nodeId ->
                        val posNode = engineResult.nodes.find { it.id == nodeId }
                        if (posNode != null) {
                            selectedNode = posNode
                            showBottomSheet = true
                            isValidationOpen = false
                        }
                    },
                    onClose = { isValidationOpen = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
    }
}

private fun PositionedSldNode.toSldNode(projectId: String): com.example.sld.SldNode {
    val nodeType = when (panelType.uppercase()) {
        "TRANSFORMER" -> com.example.sld.SldNodeType.TRANSFORMER
        "GENERATOR" -> com.example.sld.SldNodeType.GENERATOR
        "ATS" -> com.example.sld.SldNodeType.ATS
        "MDB" -> com.example.sld.SldNodeType.MDB
        "SMDB" -> com.example.sld.SldNodeType.SMDB
        "DB" -> com.example.sld.SldNodeType.DB
        else -> if (isSource) com.example.sld.SldNodeType.UTILITY_SOURCE else com.example.sld.SldNodeType.FINAL_CIRCUIT
    }
    val calculatedIb = if (circuit != null && circuit.voltageV > 0) {
        String.format(java.util.Locale.US, "%.1f A", (circuit.loadKVA * 1000.0) / (kotlin.math.sqrt(3.0) * circuit.voltageV))
    } else null

    val nodeVoltage = dataBlock?.voltage?.takeIf { it.isNotBlank() }
        ?: circuit?.let { "${it.voltageV} V (${if (it.phases.contains("3")) "3P+N" else "1P+N"})" }
        ?: "400 V (3P+N)"

    return com.example.sld.SldNode(
        id = id,
        projectId = projectId,
        type = nodeType,
        displayName = label,
        voltage = nodeVoltage,
        dataBlock = dataBlock?.copy(voltage = nodeVoltage) ?: com.example.sld.SldDataBlock(
            ratedKva = circuit?.loadKVA?.let { "$it kVA" },
            voltage = nodeVoltage,
            ibAmp = calculatedIb,
            statusText = status.name
        ),
        status = status,
        provenance = com.example.sld.SldProvenance.VERIFIED_FORMULA,
        rawCircuitId = circuit?.id
    )
}

