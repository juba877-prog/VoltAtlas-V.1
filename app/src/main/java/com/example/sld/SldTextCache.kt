package com.example.sld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pre-allocated static draw primitives for zero-allocation rendering at 120 FPS.
 * Eliminates heap churn from allocating Strokes and PathEffects inside onDraw.
 */
object SldDrawPrimitives {
    val DASHED_PATH_EFFECT = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    val DASHED_UNRESOLVED_STROKE = Stroke(width = 2.0f, pathEffect = DASHED_PATH_EFFECT)

    val FEEDER_STROKE = Stroke(width = 2.0f)
    val BUSWAY_STROKE = Stroke(width = 4.0f)
    val SOURCE_SPINE_STROKE = Stroke(width = 3.5f)
    val TIER_DATUM_STROKE = Stroke(width = 1.0f)
}

/**
 * Pre-measured text line ready for zero-allocation drawing on Canvas.
 */
data class SldCachedTextLine(
    val text: String,
    val style: TextStyle,
    val heightPx: Float
)

/**
 * Pre-computed callout block for an edge feeder.
 */
data class SldCachedEdgeCallout(
    val edgeIndex: Int,
    val lines: List<SldCachedTextLine>,
    val totalHeightPx: Float,
    val strokeColor: Color
)

/**
 * High-performance layout and measurement cache for SLD Canvas.
 * Computes text formatting and geometry once per (engineResult, displayMode, detailLevel)
 * and reuses the result across all pan and zoom frames without re-measuring text.
 */
class SldTextCache(
    private val textMeasurer: TextMeasurer
) {
    private var cachedEdgeCallouts: List<SldCachedEdgeCallout> = emptyList()
    private var lastEdgesSignature: Int = 0
    private var lastDisplayMode: DisplayMode? = null
    private var lastDetailLevel: SldDetailLevel? = null

    fun getOrComputeEdgeCallouts(
        engineResult: SldEngineResult,
        displayMode: DisplayMode,
        detailLevel: SldDetailLevel,
        palette: SldPalette
    ): List<SldCachedEdgeCallout> {
        val signature = engineResult.edges.hashCode() xor palette.isDark.hashCode()
        if (signature == lastEdgesSignature && displayMode == lastDisplayMode && detailLevel == lastDetailLevel) {
            return cachedEdgeCallouts
        }

        val calloutPolicy = SldDetailLevelPolicy.feederCallout(detailLevel)
        val defaultTextStyle = TextStyle(color = palette.textSecondary, fontSize = 10.sp)

        val computed = engineResult.edges.mapIndexed { index, edge ->
            val childNode = engineResult.nodes.find { it.id == edge.toId }
            val isAtsEdge = edge.isEmergency || edge.label == "NORMAL" || edge.label == "EMERGENCY"
            val isBusway = edge.label.contains("Busway", ignoreCase = true) ||
                    childNode?.dataBlock?.cableDescription?.contains("Busway", ignoreCase = true) == true

            // Resolve Stroke Color
            val cumVdVal = childNode?.dataBlock?.cumulativeVdPct?.replace("%", "")?.toFloatOrNull()
                ?: childNode?.dataBlock?.voltageDropPct?.replace("%", "")?.toFloatOrNull() ?: 0f

            val vdStatusColor = when {
                cumVdVal > 5.0f -> palette.invalidStatus
                cumVdVal > 3.0f -> palette.warningStatus
                else -> palette.validStatus
            }

            val isIscFail = childNode?.dataBlock?.statusText?.uppercase()?.contains("FAIL") == true ||
                    childNode?.dataBlock?.statusText?.uppercase()?.contains("ICU") == true

            val strokeColor = if (edge.isUnresolved) {
                Color(0xFFF59E0B)
            } else when (displayMode) {
                DisplayMode.VOLTAGE_DROP -> if (!isAtsEdge && childNode != null && !childNode.isSource) vdStatusColor else if (edge.isEmergency) palette.feeders else palette.enclosureBusTaps
                DisplayMode.SHORT_CIRCUIT -> if (isIscFail) palette.invalidStatus else if (edge.isEmergency) palette.feeders else palette.enclosureBusTaps
                else -> if (isBusway) Color(0xFFE08A4B) else if (edge.isEmergency) palette.feeders else if (palette.isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
            }

            // Build Lines if callout is active
            val lines = mutableListOf<SldCachedTextLine>()
            if (childNode != null && !childNode.isSource) {
                val db = childNode.dataBlock
                val cableText = db?.cableDescription.orEmpty()
                val runs = db?.parallelRuns ?: 1

                // Line 1: Cable designation
                if (calloutPolicy.cableLine && cableText.isNotBlank()) {
                    val label = if (isBusway) {
                        cableText
                    } else {
                        val runsStr = if (runs > 1) "${runs}x" else ""
                        "$runsStr$cableText".trim()
                    }
                    val style = if (isBusway) TextStyle(color = Color(0xFFE08A4B), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    else TextStyle(color = palette.textSecondary, fontSize = 10.5.sp)
                    val h = textMeasurer.measure(label, style).size.height.toFloat()
                    lines.add(SldCachedTextLine(label, style, h))
                }

                // Line 2: Engineering Metric
                if (calloutPolicy.modeMetricLine) {
                    val (metricText, metricStyle) = when (displayMode) {
                        DisplayMode.VOLTAGE_DROP -> {
                            val vdStr = "VD: ${String.format("%.2f", cumVdVal)}%"
                            Pair(vdStr, TextStyle(color = vdStatusColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
                        }
                        DisplayMode.SHORT_CIRCUIT -> {
                            val ik3 = db?.faultLevelKa.orEmpty()
                            val scColor = if (isIscFail) palette.invalidStatus else palette.textPrimary
                            Pair(if (ik3.isNotBlank()) "Isc: $ik3" else "", TextStyle(color = scColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
                        }
                        else -> {
                            val statusStr = db?.statusText.orEmpty()
                            Pair(statusStr, defaultTextStyle)
                        }
                    }
                    if (metricText.isNotBlank()) {
                        val h = textMeasurer.measure(metricText, metricStyle).size.height.toFloat()
                        lines.add(SldCachedTextLine(metricText, metricStyle, h))
                    }
                }
            }

            val totalH = lines.sumOf { (it.heightPx + 4.0f).toDouble() }.toFloat()
            SldCachedEdgeCallout(index, lines, totalH, strokeColor)
        }

        cachedEdgeCallouts = computed
        lastEdgesSignature = signature
        lastDisplayMode = displayMode
        lastDetailLevel = detailLevel
        return computed
    }
}
