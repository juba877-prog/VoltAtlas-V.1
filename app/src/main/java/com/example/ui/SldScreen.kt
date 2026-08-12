package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.ui.theme.ElectricalIcons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    manualPositions: MutableMap<String, Offset> = remember { mutableStateMapOf() },
    onNavigateToTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.example.data.AppDatabase.getDatabase(context) }
    val effectiveProjId = activeProjectId.ifBlank { circuits.firstOrNull()?.projectId ?: transformers.firstOrNull()?.projectId ?: "DEFAULT_PROJECT" }

    val dbPositionsFlow = remember(effectiveProjId) {
        db.sldNodePositionDao().getPositionsForProject(effectiveProjId)
    }
    val dbPositions by dbPositionsFlow.collectAsState(initial = emptyList())

    LaunchedEffect(dbPositions) {
        dbPositions.forEach { pos ->
            manualPositions[pos.nodeId] = Offset(pos.x, pos.y)
        }
    }

    var layoutModeEnabled by remember { mutableStateOf(false) }

    val loadingMap = remember(sizingResults) {
        sizingResults.mapValues { (_, res) -> res.utilizationPct }
    }
    
    val engineResult = remember(circuits, effectiveProjId, transformers, sizingResults, transformerResults, generatorResult, atsResult, atsEntity, manualPositions.toMap()) {
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

    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var hasAutoFitOnLoad by remember { mutableStateOf(false) }

    var selectedNode by remember { mutableStateOf<PositionedSldNode?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showSymbolGallery by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var isValidationOpen by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(DisplayMode.OVERVIEW) }

    // Auto "Fit to Screen" calculation
    fun fitToScreen() {
        if (viewportSize.width > 0f && viewportSize.height > 0f) {
            val bounds = engineResult.bounds
            val graphW = maxOf(10f, bounds.width)
            val graphH = maxOf(10f, bounds.height)

            val availW = viewportSize.width - 60f
            val availH = viewportSize.height - 60f

            val fitScale = (minOf(availW / graphW, availH / graphH)).coerceIn(0.05f, 2.5f)
            val fitPanX = (viewportSize.width - graphW * fitScale) / 2f - bounds.left * fitScale
            val fitPanY = (viewportSize.height - graphH * fitScale) / 2f - bounds.top * fitScale

            zoomScale = fitScale
            panOffset = Offset(fitPanX, fitPanY)
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
                    zoomScale = (zoomScale * zoomChange).coerceIn(0.05f, 4.0f)
                    panOffset += panChange
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
                            db.sldNodePositionDao().insertOrUpdatePosition(
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

        // Top Overlay: SLD Toolbar with Display Modes & IEC Symbol Gallery
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
                    db.sldNodePositionDao().clearPositionsForProject(effectiveProjId)
                }
                android.widget.Toast.makeText(context, "Layout reset to auto-tree hierarchy", android.widget.Toast.LENGTH_SHORT).show()
            },
            isFullscreen = false,
            onToggleFullscreen = {},
            onExportImage = {
                com.example.sld.SldImageExporter.exportAndShareImage(
                    context = context,
                    engineResult = engineResult,
                    projectName = activeProjectId.ifBlank { "VoltAtlas_SLD" },
                    isDarkTheme = isDarkTheme
                )
            },
            onExportDxf = {
                if (engineResult.nodes.isEmpty()) {
                    android.widget.Toast.makeText(context, "Diagram not ready or empty. Please add components first.", android.widget.Toast.LENGTH_SHORT).show()
                    return@SldToolbar
                }
                try {
                    val dxfContent = com.example.sld.SldDxfExporter.generateDxf(
                        engineResult,
                        activeProjectId.ifBlank { "VoltAtlas_SLD" }
                    )
                    val cacheDir = context.cacheDir
                    val file = java.io.File(cacheDir, "VoltAtlas_SLD.dxf")
                    com.example.sld.SldDxfExporter.saveDxfToFile(dxfContent, file)

                    if (file.exists() && file.length() >= 100L) {
                        val contentUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "VoltAtlas SLD Diagram DXF")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = android.content.Intent.createChooser(intent, "Export DXF (AutoCAD)")
                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                        android.widget.Toast.makeText(context, "DXF Exported successfully (${file.length() / 1024} KB)", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "DXF Export failed: Empty output file", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "DXF Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            onOpenSymbolGallery = { showSymbolGallery = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
        )

        // Edit Mode Top Banner Indicator
        if (layoutModeEnabled) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp),
                shape = RoundedCornerShape(20.dp),
                color = SleekPrimary.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Mode", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EDIT MODE — Drag nodes to reposition", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { layoutModeEnabled = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Floating Zoom Controls Cluster (Bottom-Right within safe area, 48dp touch targets)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.enclosureBusTaps),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceIn(0.05f, 4.0f) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = palette.textPrimary)
                    }
                    IconButton(
                        onClick = { zoomScale = (zoomScale / 1.25f).coerceIn(0.05f, 4.0f) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = palette.textPrimary)
                    }
                    IconButton(
                        onClick = { fitToScreen() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Fit to Screen", tint = SleekPrimary)
                    }
                }
            }
        }

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

