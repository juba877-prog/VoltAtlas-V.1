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
                    val strokeColor = if (edge.isEmergency) palette.feeders else palette.enclosureBusTaps
                    val strokeWidth = if (edge.isEmergency) SldTheme.sourceSpineStroke() else SldTheme.feederStroke()

                    drawPath(
                        path = edgePath,
                        color = strokeColor,
                        style = Stroke(width = strokeWidth)
                    )

                    val childNode = engineResult.nodes.find { it.id == edge.toId }
                    val isAtsChild = childNode?.panelType?.uppercase()?.contains("ATS") == true
                    val isAtsEdge = edge.isEmergency || edge.label == "NORMAL" || edge.label == "EMERGENCY"

                    if (isAtsEdge) {
                        if (edge.label.isNotBlank() && zoomScale > 0.35f) {
                            val channelY = if (points.size >= 4) points[1].second else (points.firstOrNull()?.second ?: 0f)
                            val channelMidX = if (points.size >= 4) (points[1].first + points[2].first) / 2f else (points.firstOrNull()?.first ?: 0f)
                            drawSafeText(
                                textMeasurer = textMeasurer,
                                text = edge.label,
                                style = TextStyle(color = strokeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(channelMidX - 24f, channelY - 16f)
                            )
                        }
                    } else if (childNode != null && !childNode.isSource && childNode.dataBlock != null && zoomScale > 0.4f) {
                        val firstPt = points.firstOrNull() ?: Pair(0f, 0f)
                        val lastPt = points.lastOrNull() ?: Pair(0f, 0f)
                        val feederX = lastPt.first
                        val verticalDropStartY = if (points.size >= 4) (points.getOrNull(2)?.second ?: firstPt.second) else firstPt.second

                        val breakerY = verticalDropStartY + 25f

                        val breakerText = childNode.dataBlock.deviceTypeAndRating.orEmpty()
                        val cableText = childNode.dataBlock.cableDescription.orEmpty()
                        val lengthText = childNode.dataBlock.lengthM?.takeIf { it.isNotBlank() }?.let { "L: ${it}m" }.orEmpty()
                        val vdText = childNode.dataBlock.voltageDropPct?.takeIf { it.isNotBlank() }?.let { "VD: $it" }.orEmpty()
                        val cumVdText = childNode.dataBlock.cumulativeVdPct?.takeIf { it.isNotBlank() }?.let { "Cum: $it" }.orEmpty()

                        val rawIk3 = childNode.dataBlock.faultLevelKa.orEmpty()
                        val cleanedIk3 = rawIk3.replace("kA", "", ignoreCase = true).trim()
                        val ik3Text = if (cleanedIk3.isNotBlank()) "Isc: $cleanedIk3 kA" else ""

                        // A. Breaker Symbol (Top of vertical drop wire)
                        if (breakerText.isNotBlank() || !isAtsChild) {
                            SldSymbols.drawBreaker(
                                drawScope = this,
                                center = Offset(feederX, breakerY),
                                sizePx = SldTheme.breakerBoxSize() * densityScale,
                                color = strokeColor
                            )
                        }

                        // B. Accumulate Mode-Specific Data Lines for Dynamic Measured Y Stacking
                        val linesToDraw = mutableListOf<Pair<String, TextStyle>>()

                        if (breakerText.isNotBlank()) {
                            linesToDraw.add(Pair(breakerText, TextStyle(color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)))
                        }

                        when (displayMode) {
                            DisplayMode.OVERVIEW -> {
                                // OVERVIEW: Minimal view — Breaker rating only, hide detailed cable specs
                            }
                            DisplayMode.CABLES -> {
                                // CABLES: Emphasize cable type, size, parallel runs, and length
                                val runsText = if (childNode.dataBlock.parallelRuns > 1) "${childNode.dataBlock.parallelRuns}x " else ""
                                val fullCableStr = "$runsText$cableText".trim()
                                if (fullCableStr.isNotBlank()) {
                                    linesToDraw.add(Pair(fullCableStr, TextStyle(color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)))
                                }
                                if (lengthText.isNotBlank()) {
                                    linesToDraw.add(Pair(lengthText, defaultTextStyle))
                                }
                            }
                            DisplayMode.VOLTAGE_DROP -> {
                                // VOLTAGE DROP: Emphasize cumulative VD% with amber/red status thresholding
                                val cumVdVal = childNode.dataBlock.cumulativeVdPct?.replace("%", "")?.toFloatOrNull()
                                    ?: childNode.dataBlock.voltageDropPct?.replace("%", "")?.toFloatOrNull() ?: 0f

                                val vdColor = when {
                                    cumVdVal > 5.0f -> Color(0xFFEF4444) // Red (>5%)
                                    cumVdVal > 3.0f -> Color(0xFFF59E0B) // Amber (>3%)
                                    else -> Color(0xFF10B981) // Green (Normal)
                                }

                                if (vdText.isNotBlank()) {
                                    linesToDraw.add(Pair(vdText, TextStyle(color = vdColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)))
                                }
                                if (cumVdText.isNotBlank()) {
                                    linesToDraw.add(Pair(cumVdText, TextStyle(color = vdColor, fontSize = 10.sp)))
                                }
                            }
                            DisplayMode.SHORT_CIRCUIT -> {
                                // SHORT CIRCUIT: Emphasize fault level Isc (kA) and device Icu rating
                                val statusTxt = childNode.dataBlock.statusText
                                val isIscFail = statusTxt.uppercase().contains("FAIL") || statusTxt.uppercase().contains("ICU")
                                val scColor = if (isIscFail) Color(0xFFEF4444) else palette.textPrimary

                                if (ik3Text.isNotBlank()) {
                                    linesToDraw.add(Pair(ik3Text, TextStyle(color = scColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)))
                                }
                                if (statusTxt.isNotBlank()) {
                                    linesToDraw.add(Pair(statusTxt, TextStyle(color = scColor, fontSize = 10.sp)))
                                }
                            }
                            DisplayMode.VALIDATION -> {
                                // VALIDATION: Show status summary on feeder run
                                val statusTxt = childNode.dataBlock.statusText
                                val valColor = when (childNode.status) {
                                    SldValidationStatus.INVALID, SldValidationStatus.DATA_REQUIRED -> Color(0xFFEF4444)
                                    SldValidationStatus.WARNING -> Color(0xFFF59E0B)
                                    else -> Color(0xFF10B981)
                                }

                                if (cableText.isNotBlank()) {
                                    linesToDraw.add(Pair(cableText, defaultTextStyle))
                                }
                                linesToDraw.add(Pair(statusTxt, TextStyle(color = valColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)))
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
                            currentY += measuredH + 5f
                        }
                    }
                }
            }

            // 2. Draw Nodes (Mode-Specific Validation Badging & Outlines)
            engineResult.nodes.forEach { node ->
                val isSelected = node.id == selectedNodeId
                val statusColor = when (displayMode) {
                    DisplayMode.VALIDATION -> when (node.status) {
                        SldValidationStatus.INVALID, SldValidationStatus.DATA_REQUIRED -> Color(0xFFEF4444)
                        SldValidationStatus.WARNING -> Color(0xFFF59E0B)
                        SldValidationStatus.VALID -> Color(0xFF10B981)
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
                        val gridX = node.x
                        val gridY = node.y
                        val gridW = maxOf(node.width, 140f)

                        drawRoundRect(
                            color = if (isSelected) palette.enclosureBusTaps else palette.surface,
                            topLeft = Offset(gridX, gridY - 15f),
                            size = Size(gridW, 36f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = statusColor,
                            topLeft = Offset(gridX, gridY - 15f),
                            size = Size(gridW, 36f),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = if (isSelected || displayMode == DisplayMode.VALIDATION) 3.5f else 2f)
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = node.label,
                            style = TextStyle(color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(gridX + 12f, gridY - 10f)
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
                    } else if (panelUpper.contains("ATS")) {
                        // Authoritative geometry: the two input terminals land EXACTLY on
                        // the layout-provided NORMAL/EMERGENCY ports, and the output terminal
                        // aligns with the downstream feeder origin. No off-center box.
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
                    // Panel / Busbar
                    val outlineColor = if (isSelected) palette.enclosureBusTaps else statusColor
                    val barHeight = maxOf(node.height, 16f)
                    val strokeW = if (isSelected || displayMode == DisplayMode.VALIDATION) 4f else 2f

                    // Solid Busbar / Panel line
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(node.x, node.y),
                        size = Size(node.width, barHeight),
                        cornerRadius = CornerRadius(2f, 2f)
                    )

                    // Title
                    val nameLayout = textMeasurer.measure(
                        text = AnnotatedString(node.label),
                        style = TextStyle(
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val labelX = node.x + node.width / 2f - nameLayout.size.width / 2f
                    drawText(
                        textLayoutResult = nameLayout,
                        topLeft = Offset(labelX, node.y - 22f)
                    )

                    // Subtitle / Rating / Validation text below busbar
                    if (displayMode == DisplayMode.VALIDATION) {
                        val statusLabel = when (node.status) {
                            SldValidationStatus.VALID -> "[VALID]"
                            SldValidationStatus.WARNING -> "[WARNING]"
                            SldValidationStatus.INVALID -> "[INVALID]"
                            SldValidationStatus.DATA_REQUIRED -> "[DATA REQUIRED]"
                            SldValidationStatus.DISCONNECTED -> "[DISCONNECTED]"
                        }
                        val statusLayout = textMeasurer.measure(
                            text = AnnotatedString(statusLabel),
                            style = TextStyle(
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val statusX = node.x + node.width / 2f - statusLayout.size.width / 2f
                        drawText(
                            textLayoutResult = statusLayout,
                            topLeft = Offset(statusX, node.y + barHeight + 6f)
                        )
                    } else if (node.ratingText.isNotBlank() && !node.ratingText.uppercase().contains("MCCB") && !node.ratingText.uppercase().contains("MCB") && !node.ratingText.uppercase().contains("ACB")) {
                        val ratingLayout = textMeasurer.measure(
                            text = AnnotatedString(node.ratingText),
                            style = TextStyle(
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            )
                        )
                        val ratingX = node.x + node.width / 2f - ratingLayout.size.width / 2f
                        drawText(
                            textLayoutResult = ratingLayout,
                            topLeft = Offset(ratingX, node.y + barHeight + 6f)
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
