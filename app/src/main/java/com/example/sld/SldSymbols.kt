package com.example.sld

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Extended IEC 60617 Symbol Library for VoltAtlas SLD (Gate C.1).
 * All symbols are fully parameterized and theme-aware via [SldPalette].
 */
object SldSymbols {

    enum class IecSymbolType {
        TRANSFORMER,
        GENERATOR,
        ATS,
        CAPACITOR_BANK,
        BUSWAY,
        AIR_CIRCUIT_BREAKER_ACB,
        MOLDED_CASE_BREAKER_MCCB,
        DIGITAL_MULTI_METER_DMM,
        INDICATION_LAMP,
        DIGITAL_KWH_METER,
        SWITCH_DISCONNECTOR,
        UTILITY_SOURCE,
        UPS,
        LOAD_ARROW
    }

    /** Which fixed input terminal of an ATS the movable blade rests on. */
    enum class AtsConnectedInput { NORMAL, EMERGENCY }

    /**
     * Exact ATS symbol anchor points derived from the positioned layout node.
     *
     * The two input terminals land precisely on the layout-provided source ports
     * (RiserLayoutEngine routes NORMAL to x+width*0.25 and EMERGENCY to x+width*0.75,
     * both terminating at y-24f; the downstream feeder departs from x+width*0.5 at
     * y+24f because the ATS is a source-classified node). Every internal drawing
     * ratio derives from these anchors, so the symbol scales with node dimensions
     * and its terminals can never drift from the routed wires.
     */
    data class AtsSymbolGeometry(
        val normalTerminal: Offset,
        val emergencyTerminal: Offset,
        val outputTerminal: Offset
    )

    fun atsSymbolGeometry(nodeX: Float, nodeY: Float, nodeWidth: Float): AtsSymbolGeometry =
        AtsSymbolGeometry(
            normalTerminal = Offset(nodeX + nodeWidth * 0.25f, nodeY - 24f),
            emergencyTerminal = Offset(nodeX + nodeWidth * 0.75f, nodeY - 24f),
            outputTerminal = Offset(nodeX + nodeWidth * 0.5f, nodeY + 24f)
        )
    /**
     * Draw standard node symbol given SldNodeType.
     */
    fun drawSymbol(
        drawScope: DrawScope,
        type: SldNodeType,
        status: SldValidationStatus,
        center: Offset,
        sizePx: Float,
        palette: SldPalette
    ) {
        val strokeWidth = 2.5f
        val iconColor = palette.textPrimary
        val statusColor = when (status) {
            SldValidationStatus.VALID -> palette.validStatus
            SldValidationStatus.WARNING -> palette.warningStatus
            SldValidationStatus.INVALID -> palette.invalidStatus
            SldValidationStatus.DISCONNECTED -> palette.textSecondary
            SldValidationStatus.DATA_REQUIRED -> palette.enclosureBusTaps
        }

        when (type) {
            SldNodeType.UTILITY_SOURCE -> drawUtilitySource(drawScope, center, sizePx, palette)
            SldNodeType.TRANSFORMER -> drawTransformer(drawScope, center, sizePx, palette, ratingText = "1000 kVA")
            SldNodeType.GENERATOR -> drawGenerator(drawScope, center, sizePx, palette, ratingText = "500 kVA")
            SldNodeType.ATS -> drawAts(drawScope, center, sizePx, palette, ratingText = "800 A")
            SldNodeType.UPS -> drawUps(drawScope, center, sizePx, palette)
            SldNodeType.CAPACITOR_BANK -> drawCapacitorBank(drawScope, center, sizePx, palette)
            SldNodeType.BUS, SldNodeType.MDB, SldNodeType.SMDB, SldNodeType.DB, SldNodeType.MCC -> {
                val busW = sizePx * 1.5f
                drawScope.drawLine(
                    color = palette.enclosureBusTaps,
                    start = Offset(center.x - busW / 2f, center.y),
                    end = Offset(center.x + busW / 2f, center.y),
                    strokeWidth = strokeWidth * 3.5f,
                    cap = StrokeCap.Round
                )
            }
            SldNodeType.FINAL_CIRCUIT, SldNodeType.LOAD -> drawLoadArrow(drawScope, center, sizePx, palette)
            SldNodeType.UNKNOWN -> {
                drawScope.drawRect(
                    color = iconColor,
                    topLeft = Offset(center.x - sizePx / 3f, center.y - sizePx / 3f),
                    size = Size(sizePx * 0.66f, sizePx * 0.66f),
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // Status badge dot
        drawScope.drawCircle(
            color = statusColor,
            radius = 5.5f,
            center = Offset(center.x + sizePx * 0.45f, center.y - sizePx * 0.45f)
        )
    }

    // 1. Utility Source
    fun drawUtilitySource(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        label: String = "22 kV Grid"
    ) {
        val r = sizePx * 0.45f
        val strokeWidth = 2.5f
        val color = palette.gridSource

        drawScope.drawCircle(
            color = color,
            radius = r,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        // AC Sine wave inside
        val path = Path().apply {
            val w = r * 0.7f
            val h = r * 0.35f
            moveTo(center.x - w, center.y)
            cubicTo(center.x - w / 2f, center.y - h, center.x, center.y - h, center.x, center.y)
            cubicTo(center.x, center.y + h, center.x + w / 2f, center.y + h, center.x + w, center.y)
        }
        drawScope.drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
    }

    // 2. Transformer (two overlapping circles)
    fun drawTransformer(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "1000 kVA"
    ) {
        val r = sizePx * 0.3f
        val offset = r * 0.65f
        val strokeWidth = 2.5f
        val color = palette.textPrimary

        // Primary winding circle (top)
        drawScope.drawCircle(
            color = color,
            radius = r,
            center = Offset(center.x, center.y - offset),
            style = Stroke(width = strokeWidth)
        )
        // Secondary winding circle (bottom)
        drawScope.drawCircle(
            color = color,
            radius = r,
            center = Offset(center.x, center.y + offset),
            style = Stroke(width = strokeWidth)
        )
    }

    // 3. Generator
    fun drawGenerator(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "500 kVA"
    ) {
        val r = sizePx * 0.45f
        val strokeWidth = 2.5f
        val color = palette.textPrimary

        drawScope.drawCircle(
            color = color,
            radius = r,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        // Letter "G"
        val w = r * 0.5f
        val h = r * 0.6f
        drawScope.drawLine(color, Offset(center.x + w * 0.4f, center.y - h * 0.3f), Offset(center.x + w * 0.4f, center.y - h * 0.5f), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x + w * 0.4f, center.y - h * 0.5f), Offset(center.x - w * 0.4f, center.y - h * 0.5f), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x - w * 0.4f, center.y - h * 0.5f), Offset(center.x - w * 0.4f, center.y + h * 0.5f), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x - w * 0.4f, center.y + h * 0.5f), Offset(center.x + w * 0.4f, center.y + h * 0.5f), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x + w * 0.4f, center.y + h * 0.5f), Offset(center.x + w * 0.4f, center.y), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x + w * 0.4f, center.y), Offset(center.x, center.y), strokeWidth = strokeWidth)
    }

    // 4. ATS (Automatic Transfer Switch) — IEC-style two-input transfer contact
    fun drawTransferSwitch(
        drawScope: DrawScope,
        geometry: AtsSymbolGeometry,
        palette: SldPalette,
        connectedTo: AtsConnectedInput = AtsConnectedInput.NORMAL
    ) {
        val n = geometry.normalTerminal
        val e = geometry.emergencyTerminal
        val out = geometry.outputTerminal
        // Effective span between the two authoritative input ports drives every ratio.
        val span = (e.x - n.x).coerceAtLeast(1f)
        val strokeWidth = (span * 0.055f).coerceIn(2f, 6f)
        val dotRadius = (span * 0.075f).coerceIn(2.5f, 8f)
        val color = palette.textPrimary

        // Enclosure spanning between (and slightly beyond) the two input ports,
        // top edge exactly on the wire termini, bottom edge on the output terminal.
        val pad = span * 0.08f
        val boxLeft = n.x - pad
        val boxRight = e.x + pad
        drawScope.drawRoundRect(
            color = color,
            topLeft = Offset(boxLeft, n.y),
            size = Size(boxRight - boxLeft, out.y - n.y),
            cornerRadius = CornerRadius(span * 0.06f, span * 0.06f),
            style = Stroke(width = strokeWidth)
        )

        // Input terminals land EXACTLY on the routed wire termini (box top edge).
        drawScope.drawCircle(color = color, radius = dotRadius, center = n)
        drawScope.drawCircle(color = color, radius = dotRadius, center = e)

        // Common output terminal on the box bottom edge, aligned with the downstream feeder.
        drawScope.drawCircle(color = color, radius = dotRadius, center = out)

        // Movable blade: solid toward the connected input, dashed toward the alternate.
        val bladeGap = span * 0.10f
        val solidTarget = if (connectedTo == AtsConnectedInput.NORMAL) n else e
        val dashedTarget = if (connectedTo == AtsConnectedInput.NORMAL) e else n
        drawScope.drawLine(
            color = color,
            start = out,
            end = Offset(solidTarget.x, solidTarget.y + bladeGap),
            strokeWidth = strokeWidth * 1.2f
        )
        drawScope.drawLine(
            color = color,
            start = out,
            end = Offset(dashedTarget.x, dashedTarget.y + bladeGap),
            strokeWidth = strokeWidth * 0.9f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(span * 0.08f, span * 0.06f), 0f)
        )
    }

    /**
     * Legacy center-based entry point retained for SldSymbolGallery previews.
     * Synthesizes a symmetric transfer-switch geometry around [center].
     */
    fun drawAts(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "800 A"
    ) {
        val span = sizePx * 1.8f
        val inputY = center.y - sizePx * 0.35f
        drawTransferSwitch(
            drawScope = drawScope,
            geometry = AtsSymbolGeometry(
                normalTerminal = Offset(center.x - span / 2f, inputY),
                emergencyTerminal = Offset(center.x + span / 2f, inputY),
                outputTerminal = Offset(center.x, center.y + sizePx * 0.45f)
            ),
            palette = palette
        )
    }
    // 5. Capacitor Bank
    fun drawCapacitorBank(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette
    ) {
        val plateW = sizePx * 0.6f
        val gap = sizePx * 0.15f
        val strokeWidth = 2.5f
        val color = palette.textPrimary

        drawScope.drawLine(
            color = color,
            start = Offset(center.x - plateW / 2f, center.y - gap / 2f),
            end = Offset(center.x + plateW / 2f, center.y - gap / 2f),
            strokeWidth = strokeWidth * 1.8f
        )
        drawScope.drawLine(
            color = color,
            start = Offset(center.x - plateW / 2f, center.y + gap / 2f),
            end = Offset(center.x + plateW / 2f, center.y + gap / 2f),
            strokeWidth = strokeWidth * 1.8f
        )
    }

    // 6. Busway (Cu, 3L+N trunking)
    fun drawBusway(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        specText: String = "Cu 2500A 3L+N"
    ) {
        val w = sizePx * 1.2f
        val h = sizePx * 0.4f
        val color = palette.enclosureBusTaps
        val strokeWidth = 2.0f

        // Outer trunking box
        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
            size = Size(w, h),
            style = Stroke(width = strokeWidth)
        )
        // Triple parallel bus lines inside
        val lineSpacing = h / 4f
        for (i in 1..3) {
            val y = center.y - h / 2f + i * lineSpacing
            drawScope.drawLine(
                color = color,
                start = Offset(center.x - w / 2f + 4f, y),
                end = Offset(center.x + w / 2f - 4f, y),
                strokeWidth = 1.5f
            )
        }
    }

    // 7. ACB (Air Circuit Breaker)
    fun drawAcb(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "2500A 3P"
    ) {
        val w = sizePx * 0.8f
        val h = sizePx * 0.8f
        val color = palette.protection
        val strokeWidth = (h * 0.16f).coerceIn(2f, 6f)

        // ACB box with drawout arrows
        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
            size = Size(w, h),
            style = Stroke(width = strokeWidth)
        )
        // Diagonal cross line indicating automatic trip mechanism
        drawScope.drawLine(
            color = color,
            start = Offset(center.x - w / 2f, center.y + h / 2f),
            end = Offset(center.x + w / 2f, center.y - h / 2f),
            strokeWidth = strokeWidth * 0.8f
        )
        // Upper and lower terminal contact arcs
        drawScope.drawCircle(color = color, radius = h * 0.19f, center = Offset(center.x, center.y - h / 2f))
        drawScope.drawCircle(color = color, radius = h * 0.19f, center = Offset(center.x, center.y + h / 2f))
    }

    // 8. Fixed 3-Pole MCCB
    fun drawMccb(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "250A 3P"
    ) {
        val w = sizePx * 0.7f
        val h = sizePx * 0.7f
        val color = palette.protection
        val strokeWidth = 2.0f

        // Rectangle with thermal-magnetic symbol inside
        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
            size = Size(w, h),
            style = Stroke(width = strokeWidth)
        )
        // Thermal-magnetic semi-circle / cross
        drawScope.drawLine(color, Offset(center.x - w * 0.3f, center.y), Offset(center.x + w * 0.3f, center.y), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x, center.y - h * 0.3f), Offset(center.x, center.y + h * 0.3f), strokeWidth = strokeWidth)
    }

    // 9. Digital Multi Meter (DMM)
    fun drawDmm(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette
    ) {
        val size = sizePx * 0.7f
        val color = palette.enclosureBusTaps
        val strokeWidth = 2.0f

        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - size / 2f, center.y - size / 2f),
            size = Size(size, size),
            style = Stroke(width = strokeWidth)
        )
        // Screen bar
        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - size * 0.35f, center.y - size * 0.35f),
            size = Size(size * 0.7f, size * 0.3f)
        )
    }

    // 10. Indication Lamp
    fun drawIndicationLamp(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        lampColor: Color = Color(0xFFFFB703)
    ) {
        val r = sizePx * 0.35f
        val strokeWidth = 2.0f

        drawScope.drawCircle(
            color = palette.textPrimary,
            radius = r,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        // 'X' inside circle
        val offset = r * 0.707f
        drawScope.drawLine(
            color = lampColor,
            start = Offset(center.x - offset, center.y - offset),
            end = Offset(center.x + offset, center.y + offset),
            strokeWidth = strokeWidth
        )
        drawScope.drawLine(
            color = lampColor,
            start = Offset(center.x - offset, center.y + offset),
            end = Offset(center.x + offset, center.y - offset),
            strokeWidth = strokeWidth
        )
    }

    // 11. Digital kWh Meter
    fun drawKwhMeter(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette
    ) {
        val w = sizePx * 0.8f
        val h = sizePx * 0.6f
        val color = palette.textPrimary
        val strokeWidth = 2.0f

        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
            size = Size(w, h),
            style = Stroke(width = strokeWidth)
        )
        // Counter line
        drawScope.drawLine(
            color = color,
            start = Offset(center.x - w * 0.35f, center.y),
            end = Offset(center.x + w * 0.35f, center.y),
            strokeWidth = strokeWidth
        )
    }

    // 12. Switch Disconnector (Isolator)
    fun drawSwitchDisconnector(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "800A 3P"
    ) {
        val h = sizePx * 0.8f
        val color = palette.textPrimary
        val strokeWidth = (h * 0.16f).coerceIn(2f, 6f)

        // Terminal dots
        drawScope.drawCircle(color = color, radius = h * 0.19f, center = Offset(center.x, center.y - h / 2f))
        drawScope.drawCircle(color = color, radius = h * 0.19f, center = Offset(center.x, center.y + h / 2f))

        // Lever line with cross bar disconnector mark
        drawScope.drawLine(
            color = color,
            start = Offset(center.x, center.y + h / 2f),
            end = Offset(center.x + sizePx * 0.35f, center.y - h * 0.25f),
            strokeWidth = strokeWidth
        )
        // Horizontal bar on contact head indicating disconnector function
        drawScope.drawLine(
            color = color,
            start = Offset(center.x + sizePx * 0.2f, center.y - h * 0.35f),
            end = Offset(center.x + sizePx * 0.5f, center.y - h * 0.15f),
            strokeWidth = strokeWidth
        )
    }

    // 13. UPS
    fun drawUps(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette
    ) {
        val boxSize = sizePx * 0.75f
        val strokeWidth = 2.0f
        val color = palette.textPrimary

        drawScope.drawRect(
            color = color,
            topLeft = Offset(center.x - boxSize / 2f, center.y - boxSize / 2f),
            size = Size(boxSize, boxSize),
            style = Stroke(width = strokeWidth)
        )
        // Diagonal
        drawScope.drawLine(
            color = color,
            start = Offset(center.x - boxSize / 2f, center.y + boxSize / 2f),
            end = Offset(center.x + boxSize / 2f, center.y - boxSize / 2f),
            strokeWidth = strokeWidth * 0.8f
        )
        // AC Sine wave bottom right
        val sinePath = Path().apply {
            val w = boxSize * 0.2f
            val h = boxSize * 0.1f
            val cx = center.x + boxSize * 0.22f
            val cy = center.y + boxSize * 0.22f
            moveTo(cx - w, cy)
            cubicTo(cx - w / 2f, cy - h, cx, cy - h, cx, cy)
            cubicTo(cx, cy + h, cx + w / 2f, cy + h, cx + w, cy)
        }
        drawScope.drawPath(path = sinePath, color = color, style = Stroke(width = strokeWidth * 0.8f))
        
        // DC straight bar top left
        drawScope.drawLine(
            color = color,
            start = Offset(center.x - boxSize * 0.35f, center.y - boxSize * 0.22f),
            end = Offset(center.x - boxSize * 0.1f, center.y - boxSize * 0.22f),
            strokeWidth = strokeWidth * 0.8f
        )
    }

    // 14. Load Arrow
    fun drawLoadArrow(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette
    ) {
        val arrowH = sizePx * 0.5f
        val strokeWidth = 2.5f
        val color = palette.textPrimary

        drawScope.drawLine(
            color = color,
            start = Offset(center.x, center.y - arrowH / 2f),
            end = Offset(center.x, center.y + arrowH / 2f),
            strokeWidth = strokeWidth
        )
        drawScope.drawLine(color, Offset(center.x - sizePx * 0.18f, center.y + arrowH * 0.15f), Offset(center.x, center.y + arrowH / 2f), strokeWidth = strokeWidth)
        drawScope.drawLine(color, Offset(center.x, center.y + arrowH / 2f), Offset(center.x + sizePx * 0.18f, center.y + arrowH * 0.15f), strokeWidth = strokeWidth)
    }

    /** Terminal dot radius as a ratio of the breaker's sizePx (never a hard-coded px value). */
    fun breakerDotRadius(sizePx: Float): Float = sizePx * 0.19f

    /** Contact stroke width as a ratio of the breaker's sizePx. */
    fun breakerStrokeWidth(sizePx: Float): Float = sizePx * 0.17f

    /**
     * Draw breaker symbol on feeder paths.
     *
     * Emergency-fed breakers are already visually distinguished at the call site via
     * [color] (palette.feeders vs enclosureBusTaps on emergency edges), so no separate
     * flag is required; the former dead isEmergency parameter was removed.
     */
    fun drawBreaker(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        color: Color
    ) {
        val strokeWidth = breakerStrokeWidth(sizePx)
        val radius = breakerDotRadius(sizePx)
        drawScope.drawCircle(color = color, radius = radius, center = Offset(center.x, center.y - sizePx / 2f))
        drawScope.drawCircle(color = color, radius = radius, center = Offset(center.x, center.y + sizePx / 2f))

        drawScope.drawLine(
            color = color,
            start = Offset(center.x, center.y + sizePx / 2f),
            end = Offset(center.x + sizePx * 0.4f, center.y - sizePx * 0.3f),
            strokeWidth = strokeWidth
        )
    }
}
