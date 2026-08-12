package com.example.sld

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

    // 4. ATS (Automatic Transfer Switch)
    fun drawAts(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        palette: SldPalette,
        ratingText: String = "800 A"
    ) {
        val boxW = sizePx * 0.9f
        val boxH = sizePx * 0.7f
        val strokeWidth = 2.0f
        val color = palette.textPrimary

        drawScope.drawRoundRect(
            color = color,
            topLeft = Offset(center.x - boxW / 2f, center.y - boxH / 2f),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = strokeWidth)
        )

        val leftX = center.x - boxW * 0.25f
        val rightX = center.x + boxW * 0.25f
        val topY = center.y - boxH * 0.3f
        val bottomY = center.y + boxH * 0.3f

        drawScope.drawCircle(color = color, radius = 3f, center = Offset(leftX, topY))
        drawScope.drawCircle(color = color, radius = 3f, center = Offset(rightX, topY))
        drawScope.drawCircle(color = color, radius = 3f, center = Offset(center.x, bottomY))

        drawScope.drawLine(
            color = color,
            start = Offset(center.x, bottomY),
            end = Offset(leftX + 2f, topY + 4f),
            strokeWidth = strokeWidth * 1.2f
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
        val strokeWidth = 2.5f

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
        drawScope.drawCircle(color = color, radius = 3f, center = Offset(center.x, center.y - h / 2f))
        drawScope.drawCircle(color = color, radius = 3f, center = Offset(center.x, center.y + h / 2f))
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
        val strokeWidth = 2.5f

        // Terminal dots
        drawScope.drawCircle(color = color, radius = 3f, center = Offset(center.x, center.y - h / 2f))
        drawScope.drawCircle(color = color, radius = 3f, center = Offset(center.x, center.y + h / 2f))

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

    /**
     * Draw breaker symbol on feeder paths.
     */
    fun drawBreaker(
        drawScope: DrawScope,
        center: Offset,
        sizePx: Float,
        color: Color,
        isEmergency: Boolean = false
    ) {
        val strokeWidth = 2.5f
        val radius = 3f

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
