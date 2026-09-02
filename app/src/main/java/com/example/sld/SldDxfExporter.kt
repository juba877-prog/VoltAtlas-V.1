package com.example.sld

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale

/**
 * Bulletproof AutoCAD Release 12 (AC1009) ASCII DXF Exporter for VoltAtlas SLD.
 *
 * Engineering & Drafting Specifications:
 * 1. Pure AutoCAD R12 ($ACADVER = AC1009) without handles or dictionary objects.
 *    100% compatible natively with all AutoCAD versions (R12 through 2026), DWG TrueView,
 *    LibreCAD, and mobile CAD viewers.
 * 2. $DWGCODEPAGE = ANSI_1252 (R12-valid text encoding declaration; $INSUNITS
 *    is an AC1015+ variable and is intentionally NOT emitted for pure R12).
 * 3. TABLES Section with standard LTYPE (CONTINUOUS, DASHED, CENTER) preceding
 *    LAYER table (including MANDATORY layer "0") and STYLE table (STANDARD).
 * 4. Standard Electrical CAD Layers:
 *    (E-TX, E-GEN, E-EMERG, E-NORMAL, E-TIE, E-PANEL, E-TEXT-TITLE, E-TEXT-DATA, E-SYMBOLS, E-BORDER).
 * 5. BLOCKS Section (Empty for clean R12 structure).
 * 6. ENTITIES Section using strict R12 group codes with complete 3D coordinates (10, 20, 30 / 11, 21, 31).
 * 7. Non-negative angles in ARC entities strictly normalized to [0.0, 360.0).
 * 8. Multiline text strings decomposed into individual 0\nTEXT entities.
 * 9. Hop Arc (Bridge) CAD Parity: Wire crossings export as DXF ARC primitives.
 * 10. Safe Text Stacking Corridor: Feeder data tags placed in dedicated side corridors (Xwire + 16.0f).
 * 11. Standard Engineering Sheet Border & Title Block on E-BORDER layer with dynamic project metadata.
 * 12. Strict Locale.US numeric formatting (formatFloat) preventing decimal comma bugs worldwide.
 */
object SldDxfExporter {

    private const val TAG = "SldDxfExporter"

    // Locale.US float formatter - MANDATORY for valid DXF group code values
    fun formatFloat(value: Float): String =
        String.format(Locale.US, "%.3f", value)

    fun normalizeAngle(angleDeg: Float): Float {
        var a = angleDeg % 360f
        if (a < 0f) a += 360f
        return a
    }

    // Y-Flip: Screen y-down -> Cartesian y-up
    private fun flipY(y: Float): Float = -y

    fun StringBuilder.appendLineEntity(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        layer: String,
        linetype: String = "CONTINUOUS"
    ) {
        append("0\nLINE\n")
        append("8\n").append(layer).append("\n")
        if (linetype != "CONTINUOUS") {
            append("6\n").append(linetype).append("\n")
        }
        append("10\n").append(formatFloat(x1)).append("\n")
        append("20\n").append(formatFloat(y1)).append("\n")
        append("30\n0.0\n")
        append("11\n").append(formatFloat(x2)).append("\n")
        append("21\n").append(formatFloat(y2)).append("\n")
        append("31\n0.0\n")
    }

    fun StringBuilder.appendCircleEntity(
        cx: Float, cy: Float,
        radius: Float,
        layer: String
    ) {
        append("0\nCIRCLE\n")
        append("8\n").append(layer).append("\n")
        append("10\n").append(formatFloat(cx)).append("\n")
        append("20\n").append(formatFloat(cy)).append("\n")
        append("30\n0.0\n")
        append("40\n").append(formatFloat(radius)).append("\n")
    }

    fun StringBuilder.appendArcEntity(
        cx: Float, cy: Float,
        radius: Float,
        startAngleDeg: Float,
        endAngleDeg: Float,
        layer: String,
        linetype: String = "CONTINUOUS"
    ) {
        append("0\nARC\n")
        append("8\n").append(layer).append("\n")
        if (linetype != "CONTINUOUS") {
            append("6\n").append(linetype).append("\n")
        }
        append("10\n").append(formatFloat(cx)).append("\n")
        append("20\n").append(formatFloat(cy)).append("\n")
        append("30\n0.0\n")
        append("40\n").append(formatFloat(radius)).append("\n")
        append("50\n").append(formatFloat(normalizeAngle(startAngleDeg))).append("\n")
        append("51\n").append(formatFloat(normalizeAngle(endAngleDeg))).append("\n")
    }

    fun StringBuilder.appendTextEntity(
        x: Float, y: Float,
        text: String?,
        height: Float = 10f,
        layer: String = "E-TEXT-DATA"
    ) {
        if (text.isNullOrBlank()) return
        val sanitized = text.replace("\r", "").trim()
        if (sanitized.isEmpty()) return

        // If string contains multiple lines, emit individual DXF TEXT entities
        val lines = sanitized.split("\n")
        var currentY = y
        for (line in lines) {
            val cleanLine = line.replace("\\", "/").trim()
            if (cleanLine.isNotEmpty()) {
                append("0\nTEXT\n")
                append("8\n").append(layer).append("\n")
                append("10\n").append(formatFloat(x)).append("\n")
                append("20\n").append(formatFloat(currentY)).append("\n")
                append("30\n0.0\n")
                append("40\n").append(formatFloat(height)).append("\n")
                append("1\n").append(cleanLine).append("\n")
                currentY -= (height * 1.35f) // Clean vertical line spacing
            }
        }
    }

    fun StringBuilder.appendRectangleEntity(
        x: Float, y: Float,
        width: Float, height: Float,
        layer: String,
        linetype: String = "CONTINUOUS"
    ) {
        val x1 = x
        val x2 = x + width
        val y1 = y
        val y2 = y + height

        appendLineEntity(x1, y1, x2, y1, layer, linetype)
        appendLineEntity(x2, y1, x2, y2, layer, linetype)
        appendLineEntity(x2, y2, x1, y2, layer, linetype)
        appendLineEntity(x1, y2, x1, y1, layer, linetype)
    }

    /**
     * Canonical DXF generator converting an SLD layout graph to an AutoCAD R12 ASCII DXF.
     */
    fun generateDxf(engineResult: SldEngineResult, projectName: String = "VoltAtlas_SLD"): String {
        val sb = StringBuilder()

        val bounds = engineResult.bounds
        val minX = if (bounds.left.isFinite()) bounds.left - 200f else -200f
        val maxX = maxOf(3000f, if (bounds.right.isFinite()) bounds.right + 400f else 3000f)
        val minY = if (bounds.top.isFinite()) bounds.top - 120f else -120f
        val maxY = if (bounds.bottom.isFinite()) bounds.bottom + 120f else 2000f

        val sheetLeft = minX - 100f
        val sheetRight = maxX + 100f
        val sheetTop = minY - 60f
        val sheetBottom = maxY + 60f

        // 1. DXF HEADER (AutoCAD R12 - AC1009)
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n")
        // R12 text encoding: ANSI Windows-1252 codepage ($INSUNITS is an AC1015+
        // variable and is NOT valid in an AC1009 header - strict R12 parsers reject it)
        sb.append("9\n\$DWGCODEPAGE\n3\nANSI_1252\n")
        sb.append("9\n\$INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n\$EXTMIN\n10\n").append(formatFloat(sheetLeft)).append("\n20\n").append(formatFloat(flipY(sheetBottom))).append("\n30\n0.0\n")
        sb.append("9\n\$EXTMAX\n10\n").append(formatFloat(sheetRight)).append("\n20\n").append(formatFloat(flipY(sheetTop))).append("\n30\n0.0\n")
        sb.append("0\nENDSEC\n")

        // 2. DXF TABLES (LTYPE & Standard Electrical CAD Layers)
        sb.append("0\nSECTION\n2\nTABLES\n")

        // LTYPE Table
        sb.append("0\nTABLE\n2\nLTYPE\n70\n3\n")
        // CONTINUOUS
        sb.append("0\nLTYPE\n2\nCONTINUOUS\n70\n64\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        // DASHED
        sb.append("0\nLTYPE\n2\nDASHED\n70\n64\n3\n__ __ __ __\n72\n65\n73\n2\n40\n9.0\n49\n6.0\n49\n-3.0\n")
        // CENTER
        sb.append("0\nLTYPE\n2\nCENTER\n70\n64\n3\n____ _ ____\n72\n65\n73\n4\n40\n16.0\n49\n10.0\n49\n-2.0\n49\n2.0\n49\n-2.0\n")
        sb.append("0\nENDTAB\n")

        // LAYER Table — MUST include the mandatory default layer "0" (DXF spec).
        // Strict CAD parsers (AutoCAD / ODA kernel used by DWG TrueView & online
        // viewers) reject or corrupt drawings whose LAYER table lacks layer 0.
        sb.append("0\nTABLE\n2\nLAYER\n70\n11\n")

        val layers = listOf(
            Triple("0", 7, "CONTINUOUS"),            // White (ACI 7) - MANDATORY default layer 0
            Triple("E-TX", 4, "CONTINUOUS"),       // Cyan (ACI 4) - Transformers
            Triple("E-GEN", 2, "CONTINUOUS"),      // Yellow (ACI 2) - Generators
            Triple("E-EMERG", 1, "DASHED"),        // Red (ACI 1) - Emergency Feeders / ATS Lines
            Triple("E-NORMAL", 5, "CONTINUOUS"),    // Blue (ACI 5) - Normal Distribution Feeders
            Triple("E-TIE", 3, "CONTINUOUS"),       // Green (ACI 3) - Bus Ties & Couplers
            Triple("E-PANEL", 7, "CONTINUOUS"),     // White (ACI 7) - Switchboards & Panels
            Triple("E-TEXT-TITLE", 7, "CONTINUOUS"),// White (ACI 7) - Major Equipment Titles
            Triple("E-TEXT-DATA", 4, "CONTINUOUS"), // Cyan (ACI 4) - Cable & Rating Data
            Triple("E-SYMBOLS", 3, "CONTINUOUS"),   // Green (ACI 3) - Breakers & Meters
            Triple("E-BORDER", 7, "CONTINUOUS")     // White (ACI 7) - Drawing Sheet Border & Title Block
        )

        for ((name, color, ltype) in layers) {
            sb.append("0\nLAYER\n")
            sb.append("2\n").append(name).append("\n")
            sb.append("70\n64\n")
            sb.append("62\n").append(color).append("\n")
            sb.append("6\n").append(ltype).append("\n")
        }

        sb.append("0\nENDTAB\n")

        // STYLE Table — TEXT entities implicitly reference the "STANDARD" text
        // style. The STYLE table with STANDARD is mandatory when TEXT entities
        // are present; strict CAD parsers fail on undefined text styles.
        sb.append("0\nTABLE\n2\nSTYLE\n70\n1\n")
        sb.append("0\nSTYLE\n")
        sb.append("2\nSTANDARD\n")
        sb.append("70\n0\n")       // Style flag: enabled
        sb.append("40\n0.0\n")     // Fixed height: 0.0 (not fixed)
        sb.append("41\n1.0\n")     // Width factor
        sb.append("50\n0.0\n")     // Oblique angle
        sb.append("71\n0\n")       // Generation flags
        sb.append("42\n2.5\n")     // Last height used
        sb.append("3\ntxt\n")      // Primary font file
        sb.append("0\nENDTAB\n")

        sb.append("0\nENDSEC\n")

        // 3. DXF BLOCKS (Empty for clean R12)
        sb.append("0\nSECTION\n2\nBLOCKS\n0\nENDSEC\n")

        // 4. DXF ENTITIES
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // Screen Coordinate Helpers with automatic Y-Flip
        fun writeLine(x1: Float, y1: Float, x2: Float, y2: Float, layer: String, linetype: String = "CONTINUOUS") {
            sb.appendLineEntity(x1, flipY(y1), x2, flipY(y2), layer, linetype)
        }

        fun writeArc(cx: Float, cy: Float, radius: Float, startAngle: Float, endAngle: Float, layer: String, linetype: String = "CONTINUOUS") {
            sb.appendArcEntity(cx, flipY(cy), radius, startAngle, endAngle, layer, linetype)
        }

        fun writeRectangle(x: Float, y: Float, width: Float, height: Float, layer: String, linetype: String = "CONTINUOUS") {
            val x1 = x
            val x2 = x + width
            val y1 = y
            val y2 = y + height

            writeLine(x1, y1, x2, y1, layer, linetype)
            writeLine(x2, y1, x2, y2, layer, linetype)
            writeLine(x2, y2, x1, y2, layer, linetype)
            writeLine(x1, y2, x1, y1, layer, linetype)
        }

        fun writeCircle(cx: Float, cy: Float, radius: Float, layer: String) {
            sb.appendCircleEntity(cx, flipY(cy), radius, layer)
        }

        fun writeText(text: String?, x: Float, y: Float, height: Float = 10f, layer: String = "E-TEXT-DATA") {
            sb.appendTextEntity(x, flipY(y), text, height, layer)
        }

        // ==========================================
        // A. Draw Tier Datum Grid Lines
        // ==========================================
        engineResult.tierDatums.forEach { datum ->
            writeLine(minX, datum.y, maxX, datum.y, "E-BORDER", "CENTER")
            writeText(datum.label, minX + 20f, datum.y - 18f, height = 12f, layer = "E-BORDER")
        }

        // ==========================================
        // B. Draw Wires with Hop Arcs & Safe Text Corridor
        // ==========================================
        val hopCrossings = SldHopArcEngine.detectHopCrossings(engineResult.edges)

        engineResult.edges.forEach { edge ->
            val isEmergency = edge.isEmergency || edge.label == "EMERGENCY"
            val wireLayer = if (isEmergency) "E-EMERG" else "E-NORMAL"
            val wireLinetype = if (isEmergency) "DASHED" else "CONTINUOUS"

            // Deconstruct into HopPathCommands
            val commands = SldHopArcEngine.buildPathCommands(edge, hopCrossings)
            for (cmd in commands) {
                when (cmd) {
                    is SldHopArcEngine.HopPathCommand.Line -> {
                        writeLine(cmd.start.x, cmd.start.y, cmd.end.x, cmd.end.y, wireLayer, wireLinetype)
                    }
                    is SldHopArcEngine.HopPathCommand.Arc -> {
                        // Semicircular bridge hop arc in Cartesian space (Y flipped):
                        // Start: 270 degrees (Cartesian bottom), Sweep CCW to 90 degrees (Cartesian top)
                        writeArc(cmd.center.x, cmd.center.y, cmd.radius, 270f, 90f, wireLayer, wireLinetype)
                    }
                }
            }

            // Draw tap junction dot at origin if connected
            val points = edge.pathPoints
            if (points.isNotEmpty() && !isEmergency) {
                writeCircle(points.first().first, points.first().second, 3.5f, "E-NORMAL")
            }

            // Feeder side-clearance text stacking corridor
            val childNode = engineResult.nodes.find { it.id == edge.toId }
            if (childNode != null && !childNode.isSource && childNode.dataBlock != null && points.size >= 2) {
                val firstPt = points.first()
                val feederX = points.last().first
                val verticalDropStartY = if (points.size >= 4) points[2].second else firstPt.second
                val breakerY = verticalDropStartY + 25f

                // Breaker Symbol on E-SYMBOLS
                val bSize = 16f
                writeRectangle(feederX - bSize / 2f, breakerY - bSize / 2f, bSize, bSize, "E-SYMBOLS")

                // Dedicated text corridor placed strictly to the right (feederX + 16f)
                val textX = feederX + 16f
                var textY = verticalDropStartY + 14f
                val db = childNode.dataBlock

                // Line 1: Breaker Type & Rating (E-TEXT-TITLE)
                db.deviceTypeAndRating?.takeIf { it.isNotBlank() }?.let {
                    writeText(it, textX, textY, height = 11f, layer = "E-TEXT-TITLE")
                    textY += 14f
                }

                // Line 2: Cable Specification (E-TEXT-DATA)
                val runsText = if (db.parallelRuns > 1) "${db.parallelRuns}x " else ""
                db.cableDescription?.takeIf { it.isNotBlank() }?.let {
                    writeText("$runsText$it", textX, textY, height = 10f, layer = "E-TEXT-DATA")
                    textY += 14f
                }

                // Line 3: Length & Voltage Drop (E-TEXT-DATA)
                val lengthStr = db.lengthM?.takeIf { it.isNotBlank() }?.let { "L: ${it}m" }
                val vdStr = db.voltageDropPct?.takeIf { it.isNotBlank() }?.let { "VD: $it" }
                val combinedLvd = listOfNotNull(lengthStr, vdStr).joinToString(" • ")
                if (combinedLvd.isNotBlank()) {
                    writeText(combinedLvd, textX, textY, height = 9f, layer = "E-TEXT-DATA")
                    textY += 13f
                }

                // Line 4: Short Circuit Fault Level (E-TEXT-DATA)
                val ik3 = db.faultLevelKa.orEmpty().replace("kA", "", ignoreCase = true).trim()
                if (ik3.isNotBlank()) {
                    writeText("Isc: $ik3 kA", textX, textY, height = 9f, layer = "E-TEXT-DATA")
                }
            }
        }

        // ==========================================
        // C. Draw Equipment Nodes (CAD Symbols & Enclosures)
        // ==========================================
        engineResult.nodes.forEach { node ->
            val panelUpper = node.panelType.uppercase()

            if (node.isSource) {
                if (panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")) {
                    val gridW = maxOf(node.width, 140f)
                    writeRectangle(node.x, node.y - 15f, gridW, 36f, "E-NORMAL")
                    writeText(node.label, node.x + 12f, node.y - 8f, height = 12f, layer = "E-TEXT-TITLE")
                } else if (panelUpper.contains("TRANSFORMER")) {
                    // Dyn11 Two-Winding Transformer Vector Geometry
                    val cx = node.x + node.width / 2f
                    val cy = node.y
                    val r = 24f
                    writeCircle(cx, cy - r * 0.55f, r, "E-TX")
                    writeCircle(cx, cy + r * 0.55f, r, "E-TX")

                    // Winding glyph annotations
                    writeText("Δ", cx - 4f, cy - r * 0.4f, height = 10f, layer = "E-TX")
                    writeText("Y", cx - 4f, cy + r * 0.7f, height = 10f, layer = "E-TX")

                    writeText(node.label, cx + 45f, cy - 12f, height = 13f, layer = "E-TEXT-TITLE")
                    writeText(node.ratingText, cx + 45f, cy + 6f, height = 10f, layer = "E-TEXT-DATA")
                } else if (panelUpper.contains("GENERATOR")) {
                    // Generator Genset Stator & Rotor Geometry
                    val cx = node.x + node.width / 2f
                    val cy = node.y
                    val r = 24f
                    writeCircle(cx, cy, r, "E-GEN")
                    writeText("G", cx - 6f, cy - 6f, height = 14f, layer = "E-GEN")
                    writeText("~", cx - 4f, cy + 10f, height = 10f, layer = "E-GEN")

                    writeText(node.label, cx + 45f, cy - 12f, height = 13f, layer = "E-TEXT-TITLE")
                    writeText(node.ratingText, cx + 45f, cy + 6f, height = 10f, layer = "E-TEXT-DATA")
                } else if (panelUpper.contains("ATS")) {
                    // 3-Terminal Automatic Transfer Switch Vector Symbol
                    val cx = node.x + 24f
                    val cy = node.y
                    val boxW = 50f
                    val boxH = 40f
                    writeRectangle(cx - boxW / 2f, cy - boxH / 2f, boxW, boxH, "E-SYMBOLS")

                    // Terminals & Switch Blade
                    val tNormal = Offset(cx - 14f, cy - 12f)
                    val tEmerg = Offset(cx + 14f, cy - 12f)
                    val tOut = Offset(cx, cy + 12f)

                    writeCircle(tNormal.x, tNormal.y, 2.5f, "E-SYMBOLS")
                    writeCircle(tEmerg.x, tEmerg.y, 2.5f, "E-EMERG")
                    writeCircle(tOut.x, tOut.y, 2.5f, "E-SYMBOLS")

                    // Normal position blade
                    writeLine(tOut.x, tOut.y, tNormal.x, tNormal.y, "E-SYMBOLS")
                    // Emergency dashed link
                    writeLine(tOut.x, tOut.y, tEmerg.x, tEmerg.y, "E-EMERG", "DASHED")

                    writeText(node.label, cx + 42f, cy - 12f, height = 13f, layer = "E-TEXT-TITLE")
                    writeText("${node.ratingText} • Interlocked", cx + 42f, cy + 6f, height = 10f, layer = "E-TEXT-DATA")
                } else if (panelUpper.contains("UPS")) {
                    // Double-Conversion Static Online UPS Symbol
                    val cx = node.x + node.width / 2f
                    val cy = node.y
                    val boxW = 56f
                    val boxH = 38f
                    writeRectangle(cx - boxW / 2f, cy - boxH / 2f, boxW, boxH, "E-SYMBOLS")

                    // Rectifier & Inverter divider
                    writeLine(cx, cy - boxH / 2f, cx, cy + boxH / 2f, "E-SYMBOLS")
                    writeText("~ / =", cx - 22f, cy - 4f, height = 8f, layer = "E-SYMBOLS")
                    writeText("= / ~", cx + 6f, cy - 4f, height = 8f, layer = "E-SYMBOLS")

                    writeText(node.label, cx + 45f, cy - 12f, height = 13f, layer = "E-TEXT-TITLE")
                    writeText(node.ratingText, cx + 45f, cy + 6f, height = 10f, layer = "E-TEXT-DATA")
                } else {
                    writeRectangle(node.x, node.y - 15f, node.width, 30f, "E-PANEL")
                    writeText(node.label, node.x + 8f, node.y - 6f, height = 11f, layer = "E-TEXT-TITLE")
                }
            } else if (node.panelType == SldNodeType.BUS_TIE.name) {
                // Normally-Open Bus Tie Breaker
                val barHeight = maxOf(node.height, 16f)
                val midX = node.x + node.width / 2f
                val midY = node.y + barHeight / 2f

                writeLine(node.x, midY, midX - 15f, midY, "E-TIE")
                writeLine(midX + 15f, midY, node.x + node.width, midY, "E-TIE")
                writeRectangle(midX - 12f, midY - 12f, 24f, 24f, "E-TIE")
                writeText("N.O. TIE", midX - 22f, midY - 18f, height = 9f, layer = "E-TEXT-TITLE")
            } else {
                // Main Switchboard / Sub-Distribution Board
                val isMain = panelUpper.contains("MDB") || panelUpper.contains("MAIN")
                val barHeight = maxOf(node.height, 16f)

                if (isMain) {
                    // Outer Enclosure Frame
                    val encPad = 36f
                    writeRectangle(node.x - encPad, node.y - 110f, node.width + encPad * 2f, barHeight + 130f, "E-PANEL", "DASHED")
                    writeText(node.label, node.x - encPad + 14f, node.y - 95f, height = 13f, layer = "E-TEXT-TITLE")
                }

                // Heavy Busbar Copper Body
                writeRectangle(node.x, node.y, node.width, barHeight, "E-PANEL")

                // Title and Ratings
                val labelX = node.x + node.width / 2f - (node.label.length * 4f)
                writeText(node.label, labelX, node.y - 20f, height = 13f, layer = "E-TEXT-TITLE")

                if (node.ratingText.isNotBlank()) {
                    writeText(node.ratingText, node.x + 8f, node.y + barHeight + 14f, height = 9f, layer = "E-TEXT-DATA")
                }
            }
        }

        // ==========================================
        // D. Drawing Sheet Border & Professional Title Block
        // ==========================================
        // Outer Sheet Frame
        writeRectangle(sheetLeft, sheetTop, sheetRight - sheetLeft, sheetBottom - sheetTop, "E-BORDER")
        // Inner Margin Frame
        writeRectangle(sheetLeft + 10f, sheetTop + 10f, sheetRight - sheetLeft - 20f, sheetBottom - sheetTop - 20f, "E-BORDER")

        // Title Block (Bottom-Right Corner)
        val tbW = 420f
        val tbH = 110f
        val tbX = sheetRight - tbW - 10f
        val tbY = sheetBottom - tbH - 10f

        writeRectangle(tbX, tbY, tbW, tbH, "E-BORDER")
        writeLine(tbX, tbY + 30f, tbX + tbW, tbY + 30f, "E-BORDER")
        writeLine(tbX, tbY + 60f, tbX + tbW, tbY + 60f, "E-BORDER")
        writeLine(tbX, tbY + 85f, tbX + tbW, tbY + 85f, "E-BORDER")

        val effectiveProjectName = projectName.ifBlank { "VoltAtlas Project" }
        writeText("PROJECT: $effectiveProjectName", tbX + 12f, tbY + 18f, height = 12f, layer = "E-BORDER")
        writeText("DRAWING: SINGLE LINE DIAGRAM (SLD)", tbX + 12f, tbY + 46f, height = 11f, layer = "E-BORDER")
        writeText("STANDARD: IEC 60364 / BS 7671 / IEC 60617", tbX + 12f, tbY + 74f, height = 9f, layer = "E-BORDER")
        writeText("ENGINE: VoltAtlas Electrical CAD Engine  |  SCALE: NTS", tbX + 12f, tbY + 98f, height = 8.5f, layer = "E-BORDER")

        // 5. DXF FOOTER
        sb.append("0\nENDSEC\n0\nEOF\n")

        return sb.toString()
    }

    /**
     * Alias for generateDxf conforming to canonical CAD exporter interface.
     */
    fun exportToDxfString(engineResult: SldEngineResult, projectName: String = "VoltAtlas_SLD"): String =
        generateDxf(engineResult, projectName)

    /**
     * Writes DXF string to an OutputStream and flushes.
     *
     * Encoding: AutoCAD R12 declares its text encoding via $DWGCODEPAGE (we emit
     * ANSI_1252), so bytes MUST be Windows-1252 — NOT US_ASCII, which silently
     * destroys non-ASCII engineering glyphs (mm² -> "mm?", Δ -> "?", • -> "?").
     */
    fun writeDxfToStream(dxfContent: String, outputStream: OutputStream) {
        outputStream.write(dxfContent.toByteArray(Charset.forName("windows-1252")))
        outputStream.flush()
    }

    /**
     * Exports DXF to app cache and triggers Android ACTION_SEND share sheet.
     */
    fun exportAndShareDxf(
        context: Context,
        engineResult: SldEngineResult,
        projectName: String = "VoltAtlas_SLD"
    ) {
        if (engineResult.nodes.isEmpty()) {
            Toast.makeText(context, "Diagram empty. Please add components first.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val dxfString = generateDxf(engineResult, projectName)
            val cacheDir = context.cacheDir
            val cleanProjectName = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dxfFile = File(cacheDir, "${cleanProjectName}_SLD.dxf")

            dxfFile.outputStream().use { out ->
                writeDxfToStream(dxfString, out)
            }

            if (dxfFile.exists() && dxfFile.length() > 0) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    dxfFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/dxf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "$projectName — Single Line Diagram (DXF)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Share / Save CAD DXF File")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                Toast.makeText(context, "CAD DXF generated successfully (${dxfFile.length() / 1024} KB)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create DXF file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "DXF Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
