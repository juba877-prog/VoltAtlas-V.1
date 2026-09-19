package com.example.sld

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.PathEffect

@Composable
fun SldCanvas(
    engineResult: SldEngineResult,
    zoomScale: Float,
    panOffset: Offset,
    selectedNodeId: String?,
    displayMode: DisplayMode = DisplayMode.OVERVIEW,
    detailLevel: SldDetailLevel = SldDetailLevel.STANDARD,
    onNodeClick: (PositionedSldNode) -> Unit,
    onCanvasClick: () -> Unit,
    onDoubleTap: ((Offset) -> Unit)? = null,
    onPanZoomChange: ((panChange: Offset, zoomChange: Float) -> Unit)? = null,
    isEditMode: Boolean = false,
    onNodeDrag: ((nodeId: String, dragDelta: Offset) -> Unit)? = null,
    onNodeDragEnd: ((nodeId: String) -> Unit)? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val palette = SldTheme.palette(isDarkTheme)
    // Physical legibility scaling for px-based symbol metrics (density-aware).
    val densityScale = androidx.compose.ui.platform.LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val defaultTextStyle = androidx.compose.ui.text.TextStyle(color = palette.textSecondary, fontSize = 10.sp)
    val boldTextStyle = defaultTextStyle.copy(fontWeight = FontWeight.Bold, color = palette.textPrimary)
    val datumTextStyle = androidx.compose.ui.text.TextStyle(color = palette.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)

    val currentNodesState = androidx.compose.runtime.rememberUpdatedState(engineResult.nodes)
    val currentZoomState = androidx.compose.runtime.rememberUpdatedState(zoomScale)
    val currentPanState = androidx.compose.runtime.rememberUpdatedState(panOffset)
    val currentPanZoomChangeState = androidx.compose.runtime.rememberUpdatedState(onPanZoomChange)
    val currentNodeClickState = androidx.compose.runtime.rememberUpdatedState(onNodeClick)
    val currentCanvasClickState = androidx.compose.runtime.rememberUpdatedState(onCanvasClick)
    val currentNodeDragState = androidx.compose.runtime.rememberUpdatedState(onNodeDrag)
    val currentNodeDragEndState = androidx.compose.runtime.rememberUpdatedState(onNodeDragEnd)

    var activeDraggingNodeId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Phase 4 §4.2 — Dynamically resolve the active Level of Detail band from zoom
    val effectiveDetailLevel = androidx.compose.runtime.remember(zoomScale, detailLevel) {
        SldDetailLevelPolicy.resolveEffectiveDetailLevel(zoomScale, detailLevel)
    }

    // Phase 3 §3.2 — Hop Arc-aware routed paths. Crossings between DISTINCT,
    // NON-CONNECTED wires get a semicircular bridge in the interrupted wire so
    // junctions are visually distinct from crossings. Built ONCE per edge-set
    // (memoized) so the draw loop stays allocation-free.
    val routedEdgePaths = androidx.compose.runtime.remember(engineResult.edges) {
        val crossings = SldHopArcEngine.detectHopCrossings(engineResult.edges)
        engineResult.edges.map { edge ->
            SldHopArcEngine.buildPath(SldHopArcEngine.buildPathCommands(edge, crossings))
        }
    }

    // Phase 4 §4.1 — Precompute edge bounding envelopes for zero-allocation viewport culling.
    val edgeEnvelopes = androidx.compose.runtime.remember(engineResult.edges) {
        engineResult.edges.map { SldViewportHelper.computeEdgeEnvelope(it.pathPoints) }
    }

    // Phase 3 §3.3 — every wire segment as a geometry primitive, used to keep
    // feeder data labels at a minimum clearance envelope from adjacent wires.
    val wireSegments = androidx.compose.runtime.remember(engineResult.edges) {
        engineResult.edges.flatMap { edge ->
            SldHopArcEngine.collectSegments(SldHopArcEngine.edgeKey(edge), edge.pathPoints)
        }
    }

    val textCache = androidx.compose.runtime.remember { SldTextCache(textMeasurer) }
    val cachedCallouts = textCache.getOrComputeEdgeCallouts(engineResult, displayMode, effectiveDetailLevel, palette)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    android.util.Log.d("SLD_EDIT_GESTURE", "pointerInput block started: isEditMode=true")
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val zoom = currentZoomState.value
                        val pan = currentPanState.value
                        val graphX = (down.position.x - pan.x) / zoom
                        val graphY = (down.position.y - pan.y) / zoom
                        val graphTapOffset = Offset(graphX, graphY)

                        val nodes = currentNodesState.value
                        val hitNode = nodes.firstOrNull { node ->
                            val r = Rect(
                                node.x - 20f,
                                node.y - 30f,
                                node.x + node.width + 20f,
                                node.y + node.height + 30f
                            )
                            r.contains(graphTapOffset)
                        }

                        android.util.Log.d(
                            "SLD_EDIT_GESTURE",
                            "TouchDown screen=(${down.position.x}, ${down.position.y}) pan=($pan) zoom=$zoom graph=($graphX, $graphY) hitNode=${hitNode?.id ?: "NONE"}"
                        )

                        var isDraggingNode = false
                        var targetId: String? = null

                        if (hitNode != null) {
                            isDraggingNode = true
                            targetId = hitNode.id
                            activeDraggingNodeId = targetId
                            android.util.Log.d("SLD_EDIT_GESTURE", "Matched node for drag: $targetId")
                        }

                        var previousPosition = down.position

                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.size

                            if (pointerCount > 1) {
                                activeDraggingNodeId = null
                                isDraggingNode = false
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                currentPanZoomChangeState.value?.invoke(panChange, zoomChange)
                                event.changes.forEach { it.consume() }
                            } else if (pointerCount == 1) {
                                val change = event.changes.first()
                                if (change.pressed) {
                                    val currentPosition = change.position
                                    val dragAmount = currentPosition - previousPosition
                                    previousPosition = currentPosition

                                    if (isDraggingNode && targetId != null) {
                                        change.consume()
                                        val currentZoom = currentZoomState.value
                                        val graphDelta = Offset(dragAmount.x / currentZoom, dragAmount.y / currentZoom)
                                        android.util.Log.d("SLD_EDIT_GESTURE", "onDrag node $targetId graphDelta=$graphDelta")
                                        currentNodeDragState.value?.invoke(targetId, graphDelta)
                                    } else {
                                        android.util.Log.d("SLD_EDIT_GESTURE", "onDrag empty canvas panAmount=$dragAmount")
                                        currentPanZoomChangeState.value?.invoke(dragAmount, 1.0f)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDraggingNode && targetId != null) {
                            android.util.Log.d("SLD_EDIT_GESTURE", "onDragEnd node $targetId")
                            currentNodeDragEndState.value?.invoke(targetId)
                        }
                        activeDraggingNodeId = null
                    }
                } else {
                    coroutineScope {
                        launch {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                currentPanZoomChangeState.value?.invoke(panChange, zoomChange)
                            }
                        }
                        launch {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val zoom = currentZoomState.value
                                    val pan = currentPanState.value
                                    val graphX = (tapOffset.x - pan.x) / zoom
                                    val graphY = (tapOffset.y - pan.y) / zoom
                                    val graphTapOffset = Offset(graphX, graphY)
                                    val clicked = currentNodesState.value.firstOrNull { node ->
                                        val r = Rect(
                                            node.x - 20f,
                                            node.y - 30f,
                                            node.x + node.width + 20f,
                                            node.y + node.height + 30f
                                        )
                                        r.contains(graphTapOffset)
                                    }
                                    if (clicked != null) {
                                        currentNodeClickState.value(clicked)
                                    } else {
                                        currentCanvasClickState.value()
                                    }
                                },
                                onDoubleTap = onDoubleTap
                            )
                        }
                    }
                }
            }
    ) {
        val viewportSize = size
        val visibleWorldRect = SldViewportHelper.calculateVisibleWorldRect(panOffset, zoomScale, viewportSize, safetyMargin = 64f)
        val isMacro = effectiveDetailLevel == SldDetailLevel.MACRO

        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(zoomScale, zoomScale, pivot = Offset.Zero)
        }) {
            // 0. Draw Tier Datum Lines
            val minCanvasX = engineResult.bounds.left - 200f
            val maxCanvasX = maxOf(3000f, engineResult.bounds.right + 400f)
            engineResult.tierDatums.forEach { datum ->
                val datumY = datum.y
                if (datumY < visibleWorldRect.top || datumY > visibleWorldRect.bottom) return@forEach
                drawLine(
                    color = palette.tierDatum,
                    start = Offset(minCanvasX, datumY),
                    end = Offset(maxCanvasX, datumY),
                    strokeWidth = SldTheme.tierDatumStroke()
                )

                if (!isMacro) {
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = datum.label,
                        style = datumTextStyle,
                        topLeft = Offset(minCanvasX + 20f, datumY - 18f)
                    )
                }
            }

            // 1. Draw Edges and Cable Data (Mode-Specific)
            engineResult.edges.forEachIndexed { index, edge ->
                val isConnectedToSelected = edge.fromId == selectedNodeId || edge.toId == selectedNodeId
                val envelope = edgeEnvelopes.getOrNull(index) ?: Rect.Zero
                if (!isConnectedToSelected && !SldViewportHelper.isEdgeVisible(envelope, visibleWorldRect)) {
                    return@forEachIndexed
                }

                val points = edge.pathPoints
                val edgePath = routedEdgePaths.getOrNull(index)
                if (points.size >= 2 && edgePath != null) {
                    val childNode = engineResult.nodes.find { it.id == edge.toId }
                    val isAtsChild = childNode?.panelType?.uppercase()?.contains("ATS") == true
                    val isAtsEdge = edge.isEmergency || edge.label == "NORMAL" || edge.label == "EMERGENCY"
                    val isBusway = edge.label.contains("Busway", ignoreCase = true) || childNode?.dataBlock?.cableDescription?.contains("Busway", ignoreCase = true) == true

                    // Dynamic status color for Voltage Drop mode & Short Circuit mode
                    val cumVdVal = childNode?.dataBlock?.cumulativeVdPct?.replace("%", "")?.toFloatOrNull()
                        ?: childNode?.dataBlock?.voltageDropPct?.replace("%", "")?.toFloatOrNull() ?: 0f

                    val vdStatusColor = when {
                        cumVdVal > 5.0f -> palette.invalidStatus
                        cumVdVal > 3.0f -> palette.warningStatus
                        else -> palette.validStatus
                    }

                    val rawIk3 = childNode?.dataBlock?.faultLevelKa.orEmpty()
                    val cleanedIk3 = rawIk3.replace("kA", "", ignoreCase = true).trim()
                    val ik3Val = cleanedIk3.toDoubleOrNull() ?: 0.0
                    val isIscFail = childNode?.dataBlock?.statusText?.uppercase()?.contains("FAIL") == true ||
                            childNode?.dataBlock?.statusText?.uppercase()?.contains("ICU") == true

                    val cachedCallout = cachedCallouts.getOrNull(index)
                    val baseStrokeColor = cachedCallout?.strokeColor
                        ?: if (edge.isUnresolved) Color(0xFFF59E0B) else palette.feeders

                    val strokeStyle = if (edge.isUnresolved) {
                        SldDrawPrimitives.DASHED_UNRESOLVED_STROKE
                    } else if (isBusway) {
                        SldDrawPrimitives.BUSWAY_STROKE
                    } else if (edge.isEmergency) {
                        SldDrawPrimitives.SOURCE_SPINE_STROKE
                    } else {
                        SldDrawPrimitives.FEEDER_STROKE
                    }

                    // Draw the orthogonal wire path
                    drawPath(
                        path = edgePath,
                        color = baseStrokeColor,
                        style = strokeStyle
                    )

                    // Draw connection tap dot on busbar start point
                    val firstPt = points.firstOrNull() ?: Pair(0f, 0f)
                    val lastPt = points.lastOrNull() ?: Pair(0f, 0f)
                    val feederX = lastPt.first

                    if (edge.isUnresolved) {
                        // Draw warning triangle at unresolved start
                        val warnX = firstPt.first
                        val warnY = firstPt.second
                        val warnPath = Path().apply {
                            moveTo(warnX, warnY - 8f)
                            lineTo(warnX - 7f, warnY + 6f)
                            lineTo(warnX + 7f, warnY + 6f)
                            close()
                        }
                        drawPath(path = warnPath, color = Color(0xFFF59E0B), style = androidx.compose.ui.graphics.drawscope.Fill)
                        if (edge.label.isNotBlank() && !isMacro) {
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = edge.label,
                                style = TextStyle(color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                topLeft = Offset(warnX + 10f, warnY - 8f)
                            )
                        }
                    } else if (!isAtsEdge) {
                        drawCircle(
                            color = baseStrokeColor,
                            radius = 3.5f,
                            center = Offset(firstPt.first, firstPt.second)
                        )
                    }

                    // Draw direction arrow on feeder drop wire
                    val verticalDropStartY = if (points.size >= 4) (points.getOrNull(2)?.second ?: firstPt.second) else firstPt.second
                    val verticalDropEndY = lastPt.second
                    val arrowY = verticalDropStartY + (verticalDropEndY - verticalDropStartY) * 0.85f

                    if (!isAtsEdge && verticalDropEndY - verticalDropStartY > 40f && !isMacro) {
                        val arrowSize = 6f
                        val arrowPath = Path().apply {
                            moveTo(feederX - arrowSize, arrowY - arrowSize)
                            lineTo(feederX, arrowY)
                            lineTo(feederX + arrowSize, arrowY - arrowSize)
                        }
                        drawPath(
                            path = arrowPath,
                            color = baseStrokeColor,
                            style = Stroke(width = 2.0f, cap = StrokeCap.Round)
                        )
                    }

                    if (isAtsEdge) {
                        if (edge.label.isNotBlank() && !isMacro) {
                            val channelY = if (points.size >= 4) points[1].second else (points.firstOrNull()?.second ?: 0f)
                            val channelMidX = if (points.size >= 4) (points[1].first + points[2].first) / 2f else (points.firstOrNull()?.first ?: 0f)
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = edge.label,
                                style = TextStyle(color = baseStrokeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(channelMidX - 24f, channelY - 16f)
                            )
                        }
                    } else if (childNode != null && !childNode.isSource && childNode.dataBlock != null && !isMacro) {
                        val breakerY = verticalDropStartY + 25f

                        val breakerText = childNode.dataBlock.deviceTypeAndRating.orEmpty()
                        val cableText = childNode.dataBlock.cableDescription.orEmpty()
                        val lengthText = childNode.dataBlock.lengthM?.takeIf { it.isNotBlank() }?.let { "L: ${it}m" }.orEmpty()
                        val vdText = childNode.dataBlock.voltageDropPct?.takeIf { it.isNotBlank() }?.let { "VD: $it" }.orEmpty()
                        val cumVdText = childNode.dataBlock.cumulativeVdPct?.takeIf { it.isNotBlank() }?.let { "Cum: $it" }.orEmpty()
                        val ik3Text = if (cleanedIk3.isNotBlank()) "Isc: $cleanedIk3 kA" else ""

                        // A. Breaker Symbol (Top of vertical drop wire)
                        if (breakerText.isNotBlank() || !isAtsChild) {
                            SldSymbols.drawBreaker(
                                drawScope = this,
                                center = Offset(feederX, breakerY),
                                sizePx = SldTheme.breakerBoxSize() * densityScale,
                                color = baseStrokeColor
                            )
                        }

                        // B. Accumulate Structured Feeder Callout Badges (filtered by §3.2 detail level)
                        val calloutPolicy = SldDetailLevelPolicy.feederCallout(effectiveDetailLevel)
                        val linesToDraw = mutableListOf<Pair<String, TextStyle>>()

                        // Line 1: Protection Device & Rating (e.g. 400A MCCB)
                        if (calloutPolicy.breakerLine && breakerText.isNotBlank()) {
                            linesToDraw.add(Pair(breakerText, TextStyle(color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)))
                        }

                        // Line 2: Cable or Busway specification
                        if (calloutPolicy.cableLine) {
                            if (isBusway) {
                                val buswayLabel = if (cableText.isNotBlank()) cableText else "Busway Trunk"
                                linesToDraw.add(Pair(buswayLabel, TextStyle(color = Color(0xFFE08A4B), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)))
                            } else if (cableText.isNotBlank()) {
                                val runsText = if (childNode.dataBlock.parallelRuns > 1) "${childNode.dataBlock.parallelRuns}x" else ""
                                val fullCableStr = "$runsText$cableText".trim()
                                linesToDraw.add(Pair(fullCableStr, TextStyle(color = palette.textSecondary, fontSize = 10.5.sp)))
                            }
                        }

                        // Line 3: Mode-Specific Engineering Metric or Status (e.g. VD: 0.85% PASS)
                        if (calloutPolicy.modeMetricLine) {
                            when (displayMode) {
                                DisplayMode.OVERVIEW -> {
                                    val vdStatusStr = if (vdText.isNotBlank()) "$vdText PASS" else childNode.dataBlock.statusText
                                    val statusTextColor = if (childNode.status == SldValidationStatus.INVALID) palette.invalidStatus else palette.validStatus
                                    linesToDraw.add(Pair(vdStatusStr, TextStyle(color = statusTextColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)))
                                }
                                DisplayMode.CABLES -> {
                                    if (lengthText.isNotBlank()) {
                                        linesToDraw.add(Pair(lengthText, defaultTextStyle))
                                    }
                                }
                                DisplayMode.VOLTAGE_DROP -> {
                                    val vdDisplayStr = if (cumVdText.isNotBlank()) "$vdText ($cumVdText)" else vdText
                                    linesToDraw.add(Pair(vdDisplayStr, TextStyle(color = vdStatusColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)))
                                }
                                DisplayMode.SHORT_CIRCUIT -> {
                                    val scColor = if (isIscFail) palette.invalidStatus else palette.textPrimary
                                    if (ik3Text.isNotBlank()) {
                                        linesToDraw.add(Pair(ik3Text, TextStyle(color = scColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)))
                                    }
                                }
                                DisplayMode.VALIDATION -> {
                                    val valColor = when (childNode.status) {
                                        SldValidationStatus.INVALID, SldValidationStatus.DATA_REQUIRED -> palette.invalidStatus
                                        SldValidationStatus.WARNING -> palette.warningStatus
                                        else -> palette.validStatus
                                    }
                                    linesToDraw.add(Pair(childNode.dataBlock.statusText, TextStyle(color = valColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)))
                                }
                            }
                        }

                        // Advanced lines (DETAILED+): route length, device Icu, Ib/Iz, utilization, installation (§1.2)
                        if (calloutPolicy.advancedLines) {
                            if (lengthText.isNotBlank()) {
                                linesToDraw.add(Pair(lengthText, defaultTextStyle))
                            }
                            val icuText = childNode.dataBlock.interruptingRatingKa
                                ?.takeIf { it.isNotBlank() }?.let { "Icu: $it" }.orEmpty()
                            if (icuText.isNotBlank()) {
                                linesToDraw.add(Pair(icuText, defaultTextStyle))
                            }
                            // Ib / Iz current pair
                            val ibIzParts = listOfNotNull(
                                childNode.dataBlock.ibAmp?.takeIf { it.isNotBlank() }?.let { "Ib: $it" },
                                childNode.dataBlock.izAmp?.takeIf { it.isNotBlank() }?.let { "Iz: $it" }
                            )
                            if (ibIzParts.isNotEmpty()) {
                                linesToDraw.add(Pair(ibIzParts.joinToString(" / "), defaultTextStyle))
                            }
                            // Cable thermal utilization
                            childNode.dataBlock.cableUtilizationPct?.takeIf { it.isNotBlank() }?.let {
                                linesToDraw.add(Pair("Util: $it", defaultTextStyle))
                            }
                            // Installation method
                            childNode.dataBlock.cableInstallation?.takeIf { it.isNotBlank() }?.let {
                                linesToDraw.add(Pair(it, TextStyle(color = palette.textSecondary, fontSize = 9.sp)))
                            }
                        }

                        // Inspection lines (INSPECTION only): neutral/CPC, VD limit, protection verdicts, ADS loop (§1C)
                        if (calloutPolicy.inspectionLines) {
                            // Neutral & CPC conductor sizes
                            val nCpcParts = listOfNotNull(
                                childNode.dataBlock.cableNeutralMm2?.takeIf { it.isNotBlank() }?.let { "N: ${it}mm\u00B2" },
                                childNode.dataBlock.cableCpcMm2?.takeIf { it.isNotBlank() }?.let { "CPC: ${it}mm\u00B2" }
                            )
                            if (nCpcParts.isNotEmpty()) {
                                linesToDraw.add(Pair(nCpcParts.joinToString(" | "), defaultTextStyle))
                            }
                            // VD design limit
                            childNode.dataBlock.voltageDropLimitPct?.takeIf { it.isNotBlank() }?.let {
                                linesToDraw.add(Pair("VD Limit: $it", defaultTextStyle))
                            }
                            // Protection coordination verdicts (compact badge line)
                            val protParts = listOfNotNull(
                                childNode.dataBlock.protectionOverload?.takeIf { it.isNotBlank() }?.let { "OL:$it" },
                                childNode.dataBlock.protectionShortCircuit?.takeIf { it.isNotBlank() }?.let { "SC:$it" },
                                childNode.dataBlock.protectionCableWithstand?.takeIf { it.isNotBlank() }?.let { "I\u00B2t:$it" },
                                childNode.dataBlock.protectionSelectivity?.takeIf { it.isNotBlank() }?.let { "Sel:$it" }
                            )
                            if (protParts.isNotEmpty()) {
                                val protLine = protParts.joinToString(" | ")
                                val hasFailure = protLine.uppercase().contains("FAIL")
                                val protColor = if (hasFailure) palette.invalidStatus else palette.validStatus
                                linesToDraw.add(Pair(protLine, TextStyle(color = protColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)))
                            }
                            // ADS loop impedance & disconnection time
                            val adsParts = listOfNotNull(
                                childNode.dataBlock.adsZsOhm?.takeIf { it.isNotBlank() }?.let { "Zs:$it" },
                                childNode.dataBlock.adsMaxZsOhm?.takeIf { it.isNotBlank() }?.let { "\u2264$it" },
                                childNode.dataBlock.adsDisconnectionTime?.takeIf { it.isNotBlank() }
                            )
                            if (adsParts.isNotEmpty()) {
                                val adsLine = adsParts.joinToString(" ")
                                val adsStatusStr = childNode.dataBlock.adsStatus?.takeIf { it.isNotBlank() }
                                val fullAdsLine = if (adsStatusStr != null) "$adsLine $adsStatusStr" else adsLine
                                val adsHasFail = fullAdsLine.uppercase().contains("FAIL")
                                val adsColor = if (adsHasFail) palette.invalidStatus else palette.validStatus
                                linesToDraw.add(Pair(fullAdsLine, TextStyle(color = adsColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)))
                            }
                        }

                        // Render lines with dynamic measured Y advancement for zero label collision.
                        // Pre-measured via SldTextCache for zero per-frame allocation at 120 FPS.
                        var currentY = verticalDropStartY + 14f
                        val precomputed = cachedCallout?.lines?.takeIf { it.isNotEmpty() }
                        if (precomputed != null) {
                            precomputed.forEach { cachedLine ->
                                drawSafeText(
                                    textMeasurer = textMeasurer,
                                    text = cachedLine.text,
                                    style = cachedLine.style,
                                    topLeft = Offset(feederX + 14f, currentY),
                                    clearanceSegments = wireSegments
                                )
                                currentY += cachedLine.heightPx + 4f
                            }
                        } else {
                            linesToDraw.forEach { (text, style) ->
                                val measuredH = textMeasurer.measure(text, style).size.height.toFloat()
                                drawSafeText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    style = style,
                                    topLeft = Offset(feederX + 14f, currentY),
                                    clearanceSegments = wireSegments
                                )
                                currentY += measuredH + 4f
                            }
                        }
                    }
                }
            }

            // 2. Draw Nodes (Switchboard Enclosures, Busbars, Sources, and Loads)
            engineResult.nodes.forEach { node ->
                val isSelected = node.id == selectedNodeId
                val nodeBounds = Rect(node.x - 40f, node.y - 120f, node.x + node.width + 40f, node.y + maxOf(node.height, 40f) + 40f)
                if (!isSelected && !SldViewportHelper.isNodeVisible(nodeBounds, visibleWorldRect)) {
                    return@forEach
                }

                val statusColor = when (displayMode) {
                    DisplayMode.VALIDATION -> when (node.status) {
                        SldValidationStatus.INVALID, SldValidationStatus.DATA_REQUIRED -> palette.invalidStatus
                        SldValidationStatus.WARNING -> palette.warningStatus
                        SldValidationStatus.VALID -> palette.validStatus
                        else -> palette.validStatus
                    }
                    else -> try {
                        Color(android.graphics.Color.parseColor(node.statusColorHex))
                    } catch (e: Exception) {
                        palette.validStatus
                    }
                }

                if (node.isSource) {
                    val panelUpper = node.panelType.uppercase()
                    if (panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")) {
                        val cx = node.x + node.width / 2f
                        val cy = node.y
                        SldSymbols.drawUtilitySource(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 48f,
                            palette = palette,
                            label = node.label
                        )

                        if (!isMacro) {
                            val labelX = cx - 40f
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(labelX, cy + 28f)
                            )
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.ratingText,
                                style = TextStyle(color = palette.textSecondary, fontSize = 10.sp),
                                topLeft = Offset(labelX, cy + 44f)
                            )
                            // §1C: Grid dark-data annotations (DETAILED+)
                            val isDetailed = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            if (isDetailed) {
                                val db = node.dataBlock
                                var gridAnnotY = cy + 60f
                                val annotStyle = TextStyle(color = palette.textSecondary, fontSize = 9.sp)
                                db?.transformerPrimaryVoltage?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Supply: $it", annotStyle, Offset(labelX, gridAnnotY))
                                    gridAnnotY += 14f
                                }
                                db?.gridFaultMVA?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "S_kQ: $it", annotStyle, Offset(labelX, gridAnnotY))
                                }
                            }
                        }
                    } else if (panelUpper.contains("TRANSFORMER")) {
                        val cx = node.x + node.width / 2f
                        val cy = node.y
                        SldSymbols.drawTransformer(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 48f,
                            palette = palette,
                            ratingText = if (isMacro) "" else node.ratingText
                        )

                        if (!isMacro) {
                            val labelX = cx + 45f
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(labelX, cy - 20f)
                            )
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.ratingText,
                                style = TextStyle(color = palette.textSecondary, fontSize = 11.sp),
                                topLeft = Offset(labelX, cy + 6f)
                            )
                            // §1C: Transformer dark-data annotations (DETAILED+)
                            val isDetailed = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            if (isDetailed) {
                                val db = node.dataBlock
                                var txAnnotY = cy + 22f
                                val annotStyle = TextStyle(color = palette.textSecondary, fontSize = 9.sp)
                                db?.windingConnection?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, it, annotStyle, Offset(labelX, txAnnotY))
                                    txAnnotY += 14f
                                }
                                db?.loadingPct?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Loading: $it", annotStyle, Offset(labelX, txAnnotY))
                                    txAnnotY += 14f
                                }
                                db?.transformerZtMOhm?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Zt: $it", annotStyle, Offset(labelX, txAnnotY))
                                    txAnnotY += 14f
                                }
                                // INSPECTION only: single-phase earth fault
                                if (effectiveDetailLevel == SldDetailLevel.INSPECTION) {
                                    db?.transformerIsc1PhaseKa?.takeIf { it.isNotBlank() }?.let {
                                        drawSafeText(textMeasurer, "Isc\u2081\u03C6: $it", annotStyle, Offset(labelX, txAnnotY))
                                    }
                                }
                            }
                        }
                    } else if (panelUpper.contains("GENERATOR")) {
                        val cx = node.x + node.width / 2f
                        val cy = node.y
                        SldSymbols.drawGenerator(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 48f,
                            palette = palette,
                            ratingText = if (isMacro) "" else node.ratingText
                        )

                        if (!isMacro) {
                            val labelX = cx - 50f
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(labelX, cy + 28f)
                            )
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.ratingText,
                                style = TextStyle(color = palette.textSecondary, fontSize = 10.sp),
                                topLeft = Offset(labelX, cy + 44f)
                            )
                            // §1C: Generator dark-data annotations (DETAILED+)
                            val isDetailed = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            if (isDetailed) {
                                val db = node.dataBlock
                                var genAnnotY = cy + 60f
                                val annotStyle = TextStyle(color = palette.textSecondary, fontSize = 9.sp)
                                db?.generatorMotorStartingKva?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Motor Start: $it", annotStyle, Offset(labelX, genAnnotY))
                                    genAnnotY += 14f
                                }
                                db?.generatorStepLoadKva?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Step Load: $it", annotStyle, Offset(labelX, genAnnotY))
                                }
                            }
                        }
                    } else if (panelUpper.contains("ATS")) {
                        // Phase 3 §3.1 — anchor-first geometry; falls back to the
                        // value math when the node carries no resolved ports.
                        val geometry = SldSymbols.atsSymbolGeometry(node)
                        SldSymbols.drawTransferSwitch(
                            drawScope = this,
                            geometry = geometry,
                            palette = palette
                        )

                        if (!isMacro) {
                            val labelX = node.x + node.width + 14f
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(labelX, node.y - 20f)
                            )
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.ratingText,
                                style = TextStyle(color = palette.textSecondary, fontSize = 11.sp),
                                topLeft = Offset(labelX, node.y + 6f)
                            )
                            // §1C: ATS dark-data annotations (DETAILED+)
                            val isDetailed = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            if (isDetailed) {
                                val db = node.dataBlock
                                var atsAnnotY = node.y + 22f
                                val annotStyle = TextStyle(color = palette.textSecondary, fontSize = 9.sp)
                                db?.atsGoverningCurrentA?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Ig: $it", annotStyle, Offset(labelX, atsAnnotY))
                                    atsAnnotY += 14f
                                }
                                db?.atsTransferTimeS?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Tt: $it", annotStyle, Offset(labelX, atsAnnotY))
                                    atsAnnotY += 14f
                                }
                                // INSPECTION only: Icw and Icm
                                if (effectiveDetailLevel == SldDetailLevel.INSPECTION) {
                                    db?.atsRequiredIcwKa?.takeIf { it.isNotBlank() }?.let {
                                        drawSafeText(textMeasurer, "Icw: $it", annotStyle, Offset(labelX, atsAnnotY))
                                        atsAnnotY += 14f
                                    }
                                    db?.atsRequiredIcmKa?.takeIf { it.isNotBlank() }?.let {
                                        drawSafeText(textMeasurer, "Icm: $it", annotStyle, Offset(labelX, atsAnnotY))
                                    }
                                }
                            }
                        }
                    } else if (panelUpper.contains("UPS")) {
                        val cx = node.x + node.width / 2f
                        val cy = node.y
                        SldSymbols.drawUps(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 48f,
                            palette = palette
                        )

                        if (!isMacro) {
                            val labelX = cx + 36f
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(labelX, cy - 20f)
                            )
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.ratingText,
                                style = TextStyle(color = palette.textSecondary, fontSize = 11.sp),
                                topLeft = Offset(labelX, cy + 6f)
                            )
                            // §1C: UPS dark-data annotations (DETAILED+)
                            val isDetailed = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            if (isDetailed) {
                                val db = node.dataBlock
                                var upsAnnotY = cy + 22f
                                val annotStyle = TextStyle(color = palette.textSecondary, fontSize = 9.sp)
                                db?.upsBatteryAh?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Battery: $it", annotStyle, Offset(labelX, upsAnnotY))
                                    upsAnnotY += 14f
                                }
                                db?.upsBatteryStrings?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, "Strings: $it", annotStyle, Offset(labelX, upsAnnotY))
                                    upsAnnotY += 14f
                                }
                                // INSPECTION only: frequency
                                if (effectiveDetailLevel == SldDetailLevel.INSPECTION) {
                                    db?.voltageFrequencyHz?.takeIf { it.isNotBlank() }?.let {
                                        drawSafeText(textMeasurer, it, annotStyle, Offset(labelX, upsAnnotY))
                                    }
                                }
                            }
                        }
                    } else {
                        drawRect(
                            color = statusColor,
                            topLeft = Offset(node.x, node.y - 15f),
                            size = Size(node.width, 30f),
                            style = Stroke(width = if (displayMode == DisplayMode.VALIDATION) 3.5f else 2f)
                        )
                        if (!isMacro) {
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(node.x + 8f, node.y - 8f)
                            )
                        }
                    }
                } else {
                    val panelUpper = node.panelType.uppercase()
                    val isMainPanel = panelUpper.contains("MDB") || panelUpper.contains("MAIN")
                    val isMotor = panelUpper.contains("MOTOR") || node.label.uppercase().contains("MOTOR") ||
                            node.circuit?.loadType?.uppercase() == "MOTOR" || node.circuit?.circuitName?.uppercase()?.contains("MOTOR") == true
                    val barHeight = maxOf(node.height, 16f)

                    if (isMainPanel) {
                        // 1. Draw Switchboard Cubicle Enclosure Frame
                        val encPaddingX = 36f
                        val encTop = node.y - 110f
                        val encLeft = node.x - encPaddingX
                        val encW = node.width + encPaddingX * 2f
                        val encH = barHeight + 130f

                        // Subtle transparent surface fill
                        drawRoundRect(
                            color = palette.surface.copy(alpha = 0.5f),
                            topLeft = Offset(encLeft, encTop),
                            size = Size(encW, encH),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                        // Dashed technical enclosure boundary
                        drawRoundRect(
                            color = if (palette.isDark) Color(0xFF334155) else Color(0xFF94A3B8),
                            topLeft = Offset(encLeft, encTop),
                            size = Size(encW, encH),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                            )
                        )

                        // Enclosure Header Tag (Top Left inside enclosure)
                        if (!isMacro) {
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(encLeft + 14f, encTop + 10f)
                            )
                        }

                        // 2. Incoming Feeder with Disconnector & DMM Meter
                        val midX = node.x + node.width / 2f
                        val incomerTopY = encTop + 2f
                        val incomerBottomY = node.y

                        // Vertical incomer wire inside enclosure
                        drawLine(
                            color = if (palette.isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                            start = Offset(midX, incomerTopY),
                            end = Offset(midX, incomerBottomY),
                            strokeWidth = SldTheme.feederStroke()
                        )

                        // Main Incomer Switch Disconnector / Breaker
                        SldSymbols.drawSwitchDisconnector(
                            drawScope = this,
                            center = Offset(midX, encTop + 45f),
                            sizePx = 28f,
                            palette = palette
                        )

                        // Digital Multi Meter (DMM) beside incomer
                        if (!isMacro) {
                            val dmmCenterX = midX + 65f
                            val dmmCenterY = encTop + 50f
                            SldSymbols.drawDmm(
                                drawScope = this,
                                center = Offset(dmmCenterX, dmmCenterY),
                                sizePx = 36f,
                                palette = palette
                            )
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = "DMM",
                                style = TextStyle(color = palette.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(dmmCenterX + 24f, dmmCenterY - 18f)
                            )
                            // §1C-4a: Real system voltage from dataBlock — never hardcoded "400 V • 3Ph"
                            val dmmVoltageLabel = node.dataBlock?.voltage?.takeIf { it.isNotBlank() } ?: "—"
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = dmmVoltageLabel,
                                style = TextStyle(color = if (palette.isDark) Color(0xFF64748B) else Color(0xFF475569), fontSize = 8.5.sp),
                                topLeft = Offset(dmmCenterX + 24f, dmmCenterY - 4f)
                            )
                        }

                        // 3. Heavy Glowing Copper Distribution Busbar
                        val copperColor = Color(0xFFE08A4B)
                        val busbarY = node.y
                        val busbarH = maxOf(node.height, 18f)

                        // Subtle outer copper glow
                        drawRoundRect(
                            color = copperColor.copy(alpha = 0.25f),
                            topLeft = Offset(node.x - 2f, busbarY - 2f),
                            size = Size(node.width + 4f, busbarH + 4f),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        // Solid Copper Busbar Body
                        drawRoundRect(
                            color = copperColor,
                            topLeft = Offset(node.x, busbarY),
                            size = Size(node.width, busbarH),
                            cornerRadius = CornerRadius(3f, 3f)
                        )

                        // Centered Busbar Type Tag
                        if (!isMacro) {
                            // §1C-4b: Real board label — never hardcoded "MDB"
                            val busTag = node.label
                            val tagLayout = textMeasurer.measure(
                                text = AnnotatedString(busTag),
                                style = TextStyle(color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            )
                            drawText(
                                textLayoutResult = tagLayout,
                                topLeft = Offset(midX - tagLayout.size.width / 2f, busbarY + (busbarH - tagLayout.size.height) / 2f)
                            )

                            // §1C-4c/4d: Board header info block (STANDARD+) and load roll-up badge (DETAILED+)
                            val isStandard = effectiveDetailLevel != SldDetailLevel.MACRO && effectiveDetailLevel != SldDetailLevel.COMPACT
                            val isDetailedOrInspection = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            val boardDb = node.dataBlock
                            if (isStandard && boardDb != null) {
                                var boardAnnotY = busbarY + busbarH + 14f
                                val boardAnnotStyle = TextStyle(color = palette.textSecondary, fontSize = 9.sp)

                                // Board header line 1: busbar / Icw / size / IP
                                val headerLine1 = listOfNotNull(
                                    boardDb.busbarRatingA?.takeIf { it.isNotBlank() }?.let { "Bus: $it" },
                                    boardDb.scRatingKA?.takeIf { it.isNotBlank() }?.let { "Icw: $it" },
                                    boardDb.busbarSize?.takeIf { it.isNotBlank() },
                                    boardDb.ipRating?.takeIf { it.isNotBlank() }
                                ).joinToString("  |  ")
                                if (headerLine1.isNotBlank()) {
                                    drawSafeText(textMeasurer, headerLine1, boardAnnotStyle, Offset(node.x + 8f, boardAnnotY))
                                    boardAnnotY += 14f
                                }

                                // Board header line 2: location
                                boardDb.panelLocation?.takeIf { it.isNotBlank() }?.let {
                                    drawSafeText(textMeasurer, it, boardAnnotStyle, Offset(node.x + 8f, boardAnnotY))
                                    boardAnnotY += 14f
                                }

                                // Board header line 3: main incoming / ways
                                val headerLine3 = listOfNotNull(
                                    boardDb.mainIncoming?.takeIf { it.isNotBlank() }?.let { "In: $it" },
                                    boardDb.numberOfWays?.takeIf { it.isNotBlank() }?.let { "$it ways" }
                                ).joinToString("  |  ")
                                if (headerLine3.isNotBlank()) {
                                    drawSafeText(textMeasurer, headerLine3, boardAnnotStyle, Offset(node.x + 8f, boardAnnotY))
                                    boardAnnotY += 14f
                                }

                                // Board load roll-up (DETAILED+)
                                if (isDetailedOrInspection) {
                                    val loadLine1 = listOfNotNull(
                                        boardDb.boardTotalConnectedKva?.takeIf { it.isNotBlank() }?.let { "TCL: $it" },
                                        boardDb.boardDemandKva?.takeIf { it.isNotBlank() }?.let { "TDL: $it" },
                                        boardDb.boardDemandFactorPct?.takeIf { it.isNotBlank() }?.let { "(DF $it)" }
                                    ).joinToString("  ")
                                    if (loadLine1.isNotBlank()) {
                                        drawSafeText(textMeasurer, loadLine1, TextStyle(color = palette.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold), Offset(node.x + 8f, boardAnnotY))
                                        boardAnnotY += 14f
                                    }
                                    val loadLine2 = listOfNotNull(
                                        boardDb.boardDesignCurrentA?.takeIf { it.isNotBlank() }?.let { "I: $it" },
                                        boardDb.boardUnbalancePct?.takeIf { it.isNotBlank() }?.let { "Unb: $it" }
                                    ).joinToString("  |  ")
                                    if (loadLine2.isNotBlank()) {
                                        drawSafeText(textMeasurer, loadLine2, boardAnnotStyle, Offset(node.x + 8f, boardAnnotY))
                                    }
                                }
                            }
                        }
                    } else if (isMotor) {
                        // Motor Load Symbol (Circle M with downward load arrow)
                        val cx = node.x + node.width / 2f
                        val cy = node.y + 16f
                        SldSymbols.drawMotorLoad(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 36f,
                            palette = palette,
                            ratingText = if (isMacro) "" else node.label
                        )
                        if (!isMacro) {
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = node.label,
                                style = TextStyle(color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(cx - 30f, cy + 34f)
                            )
                        }
                    } else {
                        // Sub-distribution Panel (SMDB / DB) Outlined Engineering Box
                        val outlineColor = if (isSelected) palette.enclosureBusTaps else if (palette.isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                        val boxTop = node.y
                        val boxW = node.width
                        val boxH = maxOf(node.height, 34f)

                        drawRoundRect(
                            color = palette.surface,
                            topLeft = Offset(node.x, boxTop),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = if (isSelected) palette.enclosureBusTaps else statusColor,
                            topLeft = Offset(node.x, boxTop),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = if (isSelected || displayMode == DisplayMode.VALIDATION) 2.5f else 1.5f)
                        )

                        // Centered Label inside Panel Box (Phase 3 §3.3.1 auto-fit:
                        // shrink font down to 8sp, then ellipsize, when too wide for the box).
                        if (!isMacro) {
                            val fittedLabel = textMeasurer.fitText(
                                text = node.label,
                                style = TextStyle(
                                    color = palette.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxWidth = boxW
                            )
                            val labelX = node.x + boxW / 2f - fittedLabel.size.width / 2f
                            val labelY = boxTop + (boxH - fittedLabel.size.height) / 2f
                            drawText(
                                textLayoutResult = fittedLabel,
                                topLeft = Offset(labelX, labelY)
                            )

                            // §1C-4e/4f: SMDB/DB board header (STANDARD+) and load badge (DETAILED+)
                            val isStandard = effectiveDetailLevel != SldDetailLevel.MACRO && effectiveDetailLevel != SldDetailLevel.COMPACT
                            val isDetailedOrInspection = effectiveDetailLevel == SldDetailLevel.DETAILED || effectiveDetailLevel == SldDetailLevel.INSPECTION
                            val subDb = node.dataBlock
                            if (isStandard && subDb != null) {
                                var subAnnotY = boxTop + boxH + 6f
                                val subAnnotStyle = TextStyle(color = palette.textSecondary, fontSize = 8.5.sp)

                                // Board header compact line: busbar / Icw / ways
                                val subHeaderLine = listOfNotNull(
                                    subDb.busbarRatingA?.takeIf { it.isNotBlank() }?.let { "Bus: $it" },
                                    subDb.scRatingKA?.takeIf { it.isNotBlank() }?.let { "Icw: $it" },
                                    subDb.numberOfWays?.takeIf { it.isNotBlank() }?.let { "$it ways" }
                                ).joinToString(" | ")
                                if (subHeaderLine.isNotBlank()) {
                                    drawSafeText(textMeasurer, subHeaderLine, subAnnotStyle, Offset(node.x + 4f, subAnnotY))
                                    subAnnotY += 13f
                                }

                                // Board load badge (DETAILED+)
                                if (isDetailedOrInspection) {
                                    val subLoadLine = listOfNotNull(
                                        subDb.boardTotalConnectedKva?.takeIf { it.isNotBlank() }?.let { "TCL: $it" },
                                        subDb.boardDemandKva?.takeIf { it.isNotBlank() }?.let { "TDL: $it" },
                                        subDb.boardDesignCurrentA?.takeIf { it.isNotBlank() }?.let { "I: $it" }
                                    ).joinToString(" | ")
                                    if (subLoadLine.isNotBlank()) {
                                        drawSafeText(textMeasurer, subLoadLine, TextStyle(color = palette.textPrimary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold), Offset(node.x + 4f, subAnnotY))
                                    }
                                }
                            }
                        }
                    }
                }

                if (isEditMode) {
                    val isBeingDragged = node.id == activeDraggingNodeId
                    val borderX = node.x - 8f
                    val borderY = node.y - 20f
                    val borderW = node.width + 16f
                    val borderH = node.height + 26f

                    drawRoundRect(
                        color = if (isBeingDragged) Color(0xFF38BDF8) else Color(0xFF38BDF8).copy(alpha = 0.5f),
                        topLeft = Offset(borderX, borderY),
                        size = Size(borderW, borderH),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(
                            width = if (isBeingDragged) 3.5f else 1.5f,
                            pathEffect = if (isBeingDragged) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSafeText(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    topLeft: Offset,
    maxWidth: Float? = null,
    minFontSize: TextUnit = 8.sp,
    horizontalPadding: Float = 12f,
    clearanceSegments: List<SldHopArcEngine.OrthogonalSegment>? = null,
    clearance: Float = 8f
) {
    if (text.isBlank()) return
    val measured = if (maxWidth != null) {
        textMeasurer.fitText(text, style, maxWidth, minFontSize, horizontalPadding)
    } else {
        textMeasurer.measure(text, style)
    }
    var pos = topLeft
    if (clearanceSegments != null && clearanceSegments.isNotEmpty()) {
        pos = nudgeForWireClearance(textSize = measured.size, topLeft = pos, segments = clearanceSegments, clearance = clearance)
    }
    drawText(
        textLayoutResult = measured,
        topLeft = pos
    )
}

/**
 * Phase 3 §3.3.1 — measure [text] and, when it exceeds [maxWidth] minus the
 * horizontal padding, scale the font down in 1sp steps to [minFontSize]; if it
 * still does not fit, ellipsize to a single line. Returns the fitted layout.
 */
private fun androidx.compose.ui.text.TextMeasurer.fitText(
    text: String,
    style: TextStyle,
    maxWidth: Float,
    minFontSize: TextUnit = 8.sp,
    horizontalPadding: Float = 12f
): androidx.compose.ui.text.TextLayoutResult {
    if (maxWidth <= 0f) return measure(text, style)
    val avail = (maxWidth - horizontalPadding * 2f).coerceAtLeast(4f)
    var fontSize = style.fontSize
    var result = measure(text, style)
    while (result.size.width > avail && fontSize.value > minFontSize.value) {
        fontSize = (fontSize.value - 1f).sp
        result = measure(text, style.copy(fontSize = fontSize))
    }
    if (result.size.width > avail) {
        result = measure(
            text,
            style.copy(fontSize = fontSize),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
    return result
}

/**
 * Phase 3 §3.3.2 — deterministically nudge [topLeft] away from any wire segment
 * whose clearance envelope intersects the label rect. Bounded iterations keep
 * the pass O(segments) per label with zero per-frame allocation in the hot path.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.nudgeForWireClearance(
    textSize: androidx.compose.ui.unit.IntSize,
    topLeft: Offset,
    segments: List<SldHopArcEngine.OrthogonalSegment>,
    clearance: Float
): Offset {
    var pos = topLeft
    repeat(3) {
        val rect = Rect(pos.x, pos.y, pos.x + textSize.width.toFloat(), pos.y + textSize.height.toFloat())
        val collides = segments.any { seg ->
            val segRect = Rect(
                minOf(seg.start.x, seg.end.x) - clearance,
                minOf(seg.start.y, seg.end.y) - clearance,
                maxOf(seg.start.x, seg.end.x) + clearance,
                maxOf(seg.start.y, seg.end.y) + clearance
            )
            rect.overlaps(segRect)
        }
        if (!collides) return pos
        pos = Offset(pos.x + clearance + 4f, pos.y + clearance + 2f)
    }
    return pos
}
