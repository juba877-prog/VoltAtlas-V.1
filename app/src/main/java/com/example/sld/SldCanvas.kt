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

    val edgePaths = androidx.compose.runtime.remember(engineResult.edges) {
        engineResult.edges.map { edge ->
            val points = edge.pathPoints
            val p = Path()
            if (points.size >= 2) {
                p.moveTo(points[0].first, points[0].second)
                for (i in 1 until points.size) {
                    p.lineTo(points[i].first, points[i].second)
                }
            }
            p
        }
    }

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
        withTransform({
            translate(panOffset.x, panOffset.y)
            scale(zoomScale, zoomScale, pivot = Offset.Zero)
        }) {
            // 0. Draw Tier Datum Lines
            val minCanvasX = engineResult.bounds.left - 200f
            val maxCanvasX = maxOf(3000f, engineResult.bounds.right + 400f)
            engineResult.tierDatums.forEach { datum ->
                val datumY = datum.y
                drawLine(
                    color = palette.tierDatum,
                    start = Offset(minCanvasX, datumY),
                    end = Offset(maxCanvasX, datumY),
                    strokeWidth = SldTheme.tierDatumStroke()
                )

                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = datum.label,
                    style = datumTextStyle,
                    topLeft = Offset(minCanvasX + 20f, datumY - 18f)
                )
            }

            // 1. Draw Edges and Cable Data (Mode-Specific)
            engineResult.edges.forEachIndexed { index, edge ->
                val points = edge.pathPoints
                val edgePath = edgePaths.getOrNull(index)
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

                    val baseStrokeColor = when (displayMode) {
                        DisplayMode.VOLTAGE_DROP -> if (!isAtsEdge && childNode != null && !childNode.isSource) vdStatusColor else if (edge.isEmergency) palette.feeders else palette.enclosureBusTaps
                        DisplayMode.SHORT_CIRCUIT -> if (isIscFail) palette.invalidStatus else if (edge.isEmergency) palette.feeders else palette.enclosureBusTaps
                        else -> if (isBusway) Color(0xFFE08A4B) else if (edge.isEmergency) palette.feeders else Color(0xFFE2E8F0)
                    }

                    val strokeWidth = if (isBusway) SldTheme.busbarStroke() * 0.45f else if (edge.isEmergency) SldTheme.sourceSpineStroke() else SldTheme.feederStroke()

                    // Draw the orthogonal wire path
                    drawPath(
                        path = edgePath,
                        color = baseStrokeColor,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw connection tap dot on busbar start point
                    val firstPt = points.firstOrNull() ?: Pair(0f, 0f)
                    val lastPt = points.lastOrNull() ?: Pair(0f, 0f)
                    val feederX = lastPt.first

                    if (!isAtsEdge) {
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

                    if (!isAtsEdge && verticalDropEndY - verticalDropStartY > 40f && zoomScale > 0.35f) {
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
                        if (edge.label.isNotBlank() && zoomScale > 0.35f) {
                            val channelY = if (points.size >= 4) points[1].second else (points.firstOrNull()?.second ?: 0f)
                            val channelMidX = if (points.size >= 4) (points[1].first + points[2].first) / 2f else (points.firstOrNull()?.first ?: 0f)
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = edge.label,
                                style = TextStyle(color = baseStrokeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(channelMidX - 24f, channelY - 16f)
                            )
                        }
                    } else if (childNode != null && !childNode.isSource && childNode.dataBlock != null && zoomScale > 0.35f) {
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

                        // B. Accumulate Structured 3-Line Feeder Callout Badges
                        val linesToDraw = mutableListOf<Pair<String, TextStyle>>()

                        // Line 1: Protection Device & Rating (e.g. 400A MCCB)
                        if (breakerText.isNotBlank()) {
                            linesToDraw.add(Pair(breakerText, TextStyle(color = Color(0xFFF0F6FC), fontSize = 11.sp, fontWeight = FontWeight.Bold)))
                        }

                        // Line 2: Cable or Busway specification
                        if (isBusway) {
                            val buswayLabel = if (cableText.isNotBlank()) cableText else "Busway Trunk"
                            linesToDraw.add(Pair(buswayLabel, TextStyle(color = Color(0xFFE08A4B), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)))
                        } else if (cableText.isNotBlank()) {
                            val runsText = if (childNode.dataBlock.parallelRuns > 1) "${childNode.dataBlock.parallelRuns}x" else ""
                            val fullCableStr = "$runsText$cableText".trim()
                            linesToDraw.add(Pair(fullCableStr, TextStyle(color = Color(0xFF94A3B8), fontSize = 10.5.sp)))
                        }

                        // Line 3: Mode-Specific Engineering Metric or Status (e.g. VD: 0.85% PASS)
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

                        // Render lines with dynamic measured Y advancement for zero label collision
                        var currentY = verticalDropStartY + 14f
                        linesToDraw.forEach { (text, style) ->
                            val measuredH = textMeasurer.measure(text, style).size.height.toFloat()
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = text,
                                style = style,
                                topLeft = Offset(feederX + 14f, currentY)
                            )
                            currentY += measuredH + 4f
                        }
                    }
                }
            }

            // 2. Draw Nodes (Switchboard Enclosures, Busbars, Sources, and Loads)
            engineResult.nodes.forEach { node ->
                val isSelected = node.id == selectedNodeId
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
                    } else if (panelUpper.contains("TRANSFORMER")) {
                        val cx = node.x + node.width / 2f
                        val cy = node.y
                        SldSymbols.drawTransformer(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 48f,
                            palette = palette,
                            ratingText = node.ratingText
                        )

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
                    } else if (panelUpper.contains("GENERATOR")) {
                        val cx = node.x + node.width / 2f
                        val cy = node.y
                        SldSymbols.drawGenerator(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 48f,
                            palette = palette,
                            ratingText = node.ratingText
                        )

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
                    } else if (panelUpper.contains("ATS")) {
                        val geometry = SldSymbols.atsSymbolGeometry(node.x, node.y, node.width)
                        SldSymbols.drawTransferSwitch(
                            drawScope = this,
                            geometry = geometry,
                            palette = palette
                        )

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
                    } else {
                        drawRect(
                            color = statusColor,
                            topLeft = Offset(node.x, node.y - 15f),
                            size = Size(node.width, 30f),
                            style = Stroke(width = if (displayMode == DisplayMode.VALIDATION) 3.5f else 2f)
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = node.label,
                            style = TextStyle(color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(node.x + 8f, node.y - 8f)
                        )
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
                            color = Color(0xFF334155),
                            topLeft = Offset(encLeft, encTop),
                            size = Size(encW, encH),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                            )
                        )

                        // Enclosure Header Tag (Top Left inside enclosure)
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = node.label,
                            style = TextStyle(color = Color(0xFFF1F5F9), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(encLeft + 14f, encTop + 10f)
                        )

                        // 2. Incoming Feeder with Disconnector & DMM Meter
                        val midX = node.x + node.width / 2f
                        val incomerTopY = encTop + 2f
                        val incomerBottomY = node.y

                        // Vertical incomer wire inside enclosure
                        drawLine(
                            color = Color(0xFFE2E8F0),
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
                            style = TextStyle(color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(dmmCenterX + 24f, dmmCenterY - 18f)
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = "400 V • 3Ph",
                            style = TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp),
                            topLeft = Offset(dmmCenterX + 24f, dmmCenterY - 4f)
                        )

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
                        val busTag = "MDB"
                        val tagLayout = textMeasurer.measure(
                            text = AnnotatedString(busTag),
                            style = TextStyle(color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        )
                        drawText(
                            textLayoutResult = tagLayout,
                            topLeft = Offset(midX - tagLayout.size.width / 2f, busbarY + (busbarH - tagLayout.size.height) / 2f)
                        )
                    } else if (isMotor) {
                        // Motor Load Symbol (Circle M with downward load arrow)
                        val cx = node.x + node.width / 2f
                        val cy = node.y + 16f
                        SldSymbols.drawMotorLoad(
                            drawScope = this,
                            center = Offset(cx, cy),
                            sizePx = 36f,
                            palette = palette,
                            ratingText = node.label
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = node.label,
                            style = TextStyle(color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(cx - 30f, cy + 34f)
                        )
                    } else {
                        // Sub-distribution Panel (SMDB / DB) Outlined Engineering Box
                        val outlineColor = if (isSelected) palette.enclosureBusTaps else Color(0xFF334155)
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

                        // Centered Label inside Panel Box
                        val nameLayout = textMeasurer.measure(
                            text = AnnotatedString(node.label),
                            style = TextStyle(
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val labelX = node.x + boxW / 2f - nameLayout.size.width / 2f
                        val labelY = boxTop + (boxH - nameLayout.size.height) / 2f
                        drawText(
                            textLayoutResult = nameLayout,
                            topLeft = Offset(labelX, labelY)
                        )
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
    topLeft: Offset
) {
    if (text.isBlank()) return
    val measured = textMeasurer.measure(text, style)
    drawText(
        textLayoutResult = measured,
        topLeft = topLeft
    )
}
