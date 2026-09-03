package com.example.sld

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Industrial-Grade AutoCAD Release 12 (AC1009) ASCII DXF Exporter for VoltAtlas SLD.
 *
 * Master CAD Overhaul Specification (IEC 60617 / IEEE 315 / IEC 62040-3):
 * 1. PART 1 — Reusable AutoCAD BLOCK library (BLK_MCCB, BLK_ACB, BLK_ATS_3P,
 *    BLK_TRANSFORMER_DYN11, BLK_GENERATOR, BLK_UPS_ONLINE, BLK_BUS_TIE) defined in
 *    the BLOCKS section with exact IEC geometry and discrete connection terminals.
 *    Every equipment symbol is instantiated in ENTITIES via 0/INSERT.
 * 2. PART 2 — Terminal-snapped wire routing: feeder wires terminate exactly on
 *    breaker terminal stubs (Ybrk +/- 14.0); transformer/generator/ATS/UPS wires
 *    terminate on discrete terminal anchors. Zero wire penetration into any
 *    equipment body (Liang-Barsky clipping against symbol exclusion boxes).
 * 3. PART 3 — Annotation corridors: feeder data stacked in a right-hand corridor
 *    (Xwire + 18.0, dY = -13.0); equipment titles sanitized of database clutter.
 * 4. PART 4 — Drafting hygiene: full-width tier guidelines replaced by compact
 *    margin datum tags (X = 20..120), strict R12-pure layer table (lineweight
 *    policy carried by standard layer names for CTB plot-style mapping — group
 *    370 is an AC1015+ code and MUST NOT appear in an AC1009 file), DASHED
 *    emergency linetype, engineering sheet border & title block.
 * 5. Pure AutoCAD R12 ($ACADVER = AC1009), $DWGCODEPAGE = ANSI_1252, strict
 *    Locale.US numeric formatting, mandatory layer "0", STANDARD text style.
 */
object SldDxfExporter {

    private const val TAG = "SldDxfExporter"

    // ------------------------------------------------------------------
    // PART 1 — IEC 60617 / IEEE 315 reusable BLOCK library names
    // ------------------------------------------------------------------
    const val BLK_MCCB = "BLK_MCCB"
    const val BLK_ACB = "BLK_ACB"
    const val BLK_ATS_3P = "BLK_ATS_3P"
    const val BLK_TRANSFORMER_DYN11 = "BLK_TRANSFORMER_DYN11"
    const val BLK_GENERATOR = "BLK_GENERATOR"
    const val BLK_UPS_ONLINE = "BLK_UPS_ONLINE"
    const val BLK_BUS_TIE = "BLK_BUS_TIE"

    /** MCCB half-height incl. terminal stubs — feeder wires terminate exactly here. */
    const val MCCB_TERMINAL_HALF = 14.0f

    /** Right-hand annotation corridor offset from the wire X (>= 16.0 clearance). */
    const val TEXT_CORRIDOR_OFFSET = 18.0f

    /** Corridor vertical line spacing (Cartesian, per master specification). */
    const val CORRIDOR_LINE_SPACING = 13.0f

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

    /**
     * R12 INSERT entity referencing a library BLOCK defined in the BLOCKS section.
     * All equipment symbols MUST be emitted through this primitive (never as
     * ad-hoc primitive geometry in ENTITIES).
     */
    fun StringBuilder.appendInsertEntity(
        blockName: String,
        x: Float, y: Float,
        layer: String
    ) {
        append("0\nINSERT\n")
        append("8\n").append(layer).append("\n")
        append("2\n").append(blockName).append("\n")
        append("10\n").append(formatFloat(x)).append("\n")
        append("20\n").append(formatFloat(y)).append("\n")
        append("30\n0.0\n")
        append("41\n1.0\n42\n1.0\n43\n1.0\n")
        append("50\n0.0\n")
    }

    /**
     * Sanitizes strings for AutoCAD ASCII DXF compatibility.
     * Converts special engineering and Unicode glyphs to standard AutoCAD escape
     * codes (%%c for diameter/phase, \U+XXXX for Unicode characters like Delta, mm2).
     * Ensures the emitted DXF is 100% clean 7-bit ASCII/UTF-8 (1 char = 1 byte in
     * windows-1252) with no decoding conflicts in desktop or mobile CAD viewers.
     */
    fun sanitizeDxfText(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when (ch) {
                'Ø', 'ø' -> sb.append("%%c")
                '°' -> sb.append("%%d")
                '±' -> sb.append("%%p")
                '•' -> sb.append(" - ")
                'Δ' -> sb.append("\\U+0394")
                'Ω' -> sb.append("\\U+03A9")
                '²' -> sb.append("\\U+00B2")
                '³' -> sb.append("\\U+00B3")
                '\r' -> {} // Strip carriage return
                in ' '..'~' -> {
                    if (ch == '\\') {
                        if (i + 2 < raw.length && raw[i + 1] == 'U' && raw[i + 2] == '+') {
                            sb.append('\\')
                        } else {
                            sb.append('/')
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                else -> {
                    if (ch.code in 0x00..0x1F || ch.code == 0x7F) {
                        // Skip unprintable control characters
                    } else {
                        sb.append(String.format(Locale.US, "\\U+%04X", ch.code))
                    }
                }
            }
            i++
        }
        return sb.toString()
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

        // Multiline strings decomposed into individual clean 0/TEXT entities
        val lines = sanitized.split("\n")
        var currentY = y
        for (line in lines) {
            val cleanLine = sanitizeDxfText(line).trim()
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

    // ==================================================================
    // PART 1 — IEC 60617 / IEEE 315 MASTER BLOCK LIBRARY
    // Block-local coordinates are Cartesian (y-up). Inserted 1:1 via INSERT.
    // ==================================================================

    private fun StringBuilder.blockDefinition(name: String, geometry: StringBuilder.() -> Unit) {
        append("0\nBLOCK\n8\n0\n")
        append("2\n").append(name).append("\n")
        append("70\n0\n")
        append("10\n0.0\n20\n0.0\n30\n0.0\n")
        append("3\n").append(name).append("\n")
        geometry()
        append("0\nENDBLK\n8\n0\n")
    }

    private fun buildIecBlockLibrary(sb: StringBuilder) {
        // ---- BLK_MCCB — Molded Case Circuit Breaker (IEC 60617-7-13) ----
        // W=24 H=28. Input (0,14), Output (0,-14). Trip cross X inside housing.
        sb.blockDefinition(BLK_MCCB) {
            appendLineEntity(0f, 14f, 0f, 8f, "E-SYMBOLS")           // top terminal stub
            appendLineEntity(-10f, -8f, 10f, -8f, "E-SYMBOLS")       // housing
            appendLineEntity(10f, -8f, 10f, 8f, "E-SYMBOLS")
            appendLineEntity(10f, 8f, -10f, 8f, "E-SYMBOLS")
            appendLineEntity(-10f, 8f, -10f, -8f, "E-SYMBOLS")
            appendLineEntity(-7f, -6f, 7f, 6f, "E-SYMBOLS")          // automatic trip cross
            appendLineEntity(-7f, 6f, 7f, -6f, "E-SYMBOLS")
            appendLineEntity(0f, -8f, 0f, -14f, "E-SYMBOLS")         // bottom terminal stub
        }

        // ---- BLK_ACB — Air / Draw-out Circuit Breaker (IEC 60617-7-14) ----
        // W=28 H=36. Input (0,18), Output (0,-18). Tulip chevrons + trip cross.
        sb.blockDefinition(BLK_ACB) {
            appendLineEntity(0f, 18f, 0f, 12f, "E-SYMBOLS")          // top terminal stub
            appendLineEntity(-6f, 15f, 0f, 11f, "E-SYMBOLS")         // upper tulip contacts
            appendLineEntity(6f, 15f, 0f, 11f, "E-SYMBOLS")
            appendLineEntity(-10f, -10f, 10f, -10f, "E-SYMBOLS")     // housing
            appendLineEntity(10f, -10f, 10f, 10f, "E-SYMBOLS")
            appendLineEntity(10f, 10f, -10f, 10f, "E-SYMBOLS")
            appendLineEntity(-10f, 10f, -10f, -10f, "E-SYMBOLS")
            appendLineEntity(-7f, -8f, 7f, 8f, "E-SYMBOLS")          // automatic trip cross
            appendLineEntity(-7f, 8f, 7f, -8f, "E-SYMBOLS")
            appendLineEntity(-6f, -15f, 0f, -11f, "E-SYMBOLS")       // lower tulip contacts
            appendLineEntity(6f, -15f, 0f, -11f, "E-SYMBOLS")
            appendLineEntity(0f, -12f, 0f, -18f, "E-SYMBOLS")        // bottom terminal stub
        }

        // ---- BLK_ATS_3P — 3-Terminal Automatic Transfer Switch (IEC 60617-7-15) ----
        // W=64 H=44. Normal (-18,+22), Emergency (+18,+22), Common Load (0,-22).
        sb.blockDefinition(BLK_ATS_3P) {
            appendLineEntity(-32f, -22f, 32f, -22f, "E-SYMBOLS")     // enclosure
            appendLineEntity(32f, -22f, 32f, 22f, "E-SYMBOLS")
            appendLineEntity(32f, 22f, -32f, 22f, "E-SYMBOLS")
            appendLineEntity(-32f, 22f, -32f, -22f, "E-SYMBOLS")
            appendCircleEntity(-18f, 16f, 3f, "E-NORMAL")            // Normal fixed contact
            appendCircleEntity(18f, 16f, 3f, "E-EMERG")              // Emergency fixed contact
            appendCircleEntity(0f, -16f, 3f, "E-SYMBOLS")            // Common load contact
            appendLineEntity(-18f, 19f, -18f, 22f, "E-NORMAL")       // Normal lead to top edge
            appendLineEntity(18f, 19f, 18f, 22f, "E-EMERG")          // Emergency lead to top edge
            appendLineEntity(0f, -19f, 0f, -22f, "E-SYMBOLS")        // Load lead to bottom edge
            appendLineEntity(0f, -16f, -18f, 16f, "E-SYMBOLS")       // solid blade -> Normal
            appendLineEntity(0f, -16f, 13f, 11f, "E-EMERG")          // dashed standby path, open gap
            appendLineEntity(-13f, 10f, 13f, 10f, "E-EMERG")         // mechanical interlock link
            appendTextEntity(-30f, -19f, "INTERLOCKED", 4f, "E-TEXT-DATA")
        }

        // ---- BLK_TRANSFORMER_DYN11 — Step-Down Transformer (IEC 60617-6-9) ----
        // R=18, total H=60. MV top (0,40), LV bottom (0,-40). Delta / Yn + earth.
        sb.blockDefinition(BLK_TRANSFORMER_DYN11) {
            appendCircleEntity(0f, 12f, 18f, "E-TX")                 // primary winding
            appendCircleEntity(0f, -12f, 18f, "E-TX")                // secondary winding
            appendTextEntity(-5f, 8f, "Δ", 8f, "E-TX")               // primary Delta
            appendTextEntity(-6f, -16f, "Yn", 8f, "E-TX")            // secondary Star with neutral
            appendLineEntity(0f, 30f, 0f, 40f, "E-TX")               // MV terminal stub
            appendLineEntity(0f, -30f, 0f, -40f, "E-TX")             // LV terminal stub
            appendLineEntity(16f, -20f, 16f, -26f, "E-TX")           // neutral earth stub
            appendLineEntity(11f, -26f, 21f, -26f, "E-TX")
            appendLineEntity(13f, -28f, 19f, -28f, "E-TX")
            appendLineEntity(15f, -30f, 17f, -30f, "E-TX")
        }

        // ---- BLK_GENERATOR — Synchronous Standby Generator (IEC 60617-6-1) ----
        // R=22. Output (0,-32). G + AC wave, frame ground at left.
        sb.blockDefinition(BLK_GENERATOR) {
            appendCircleEntity(0f, 0f, 22f, "E-GEN")                 // stator
            appendTextEntity(-8f, -8f, "G", 14f, "E-GEN")
            appendTextEntity(-4f, 10f, "~", 9f, "E-GEN")
            appendLineEntity(0f, -22f, 0f, -32f, "E-GEN")            // output terminal stub
            appendLineEntity(-22f, 0f, -28f, 0f, "E-GEN")            // frame ground lead
            appendLineEntity(-28f, 0f, -28f, -4f, "E-GEN")
            appendLineEntity(-32f, -4f, -24f, -4f, "E-GEN")
            appendLineEntity(-30f, -6f, -26f, -6f, "E-GEN")
        }

        // ---- BLK_UPS_ONLINE — Online Double-Conversion Static UPS (IEC 62040-3) ----
        // W=68 H=40. Input (-34,0), Output (34,0), DC battery bus (0,-20).
        sb.blockDefinition(BLK_UPS_ONLINE) {
            appendLineEntity(-34f, -20f, 34f, -20f, "E-SYMBOLS")     // enclosure
            appendLineEntity(34f, -20f, 34f, 20f, "E-SYMBOLS")
            appendLineEntity(34f, 20f, -34f, 20f, "E-SYMBOLS")
            appendLineEntity(-34f, 20f, -34f, -20f, "E-SYMBOLS")
            appendLineEntity(-24f, 10f, -24f, -10f, "E-SYMBOLS")     // rectifier (AC->DC)
            appendLineEntity(-24f, -10f, -8f, 0f, "E-SYMBOLS")
            appendLineEntity(-8f, 0f, -24f, 10f, "E-SYMBOLS")
            appendLineEntity(8f, 10f, 8f, -10f, "E-SYMBOLS")         // inverter (DC->AC)
            appendLineEntity(8f, -10f, 24f, 0f, "E-SYMBOLS")
            appendLineEntity(24f, 0f, 8f, 10f, "E-SYMBOLS")
            appendTextEntity(-31f, -3f, "~", 7f, "E-SYMBOLS")
            appendTextEntity(-18f, -3f, "=", 7f, "E-SYMBOLS")
            appendTextEntity(-3f, -3f, "=", 7f, "E-SYMBOLS")
            appendTextEntity(11f, -3f, "~", 7f, "E-SYMBOLS")
            appendTextEntity(27f, -3f, "~", 7f, "E-SYMBOLS")
            appendLineEntity(0f, 0f, 0f, -20f, "E-SYMBOLS")          // DC battery bus stub
            appendLineEntity(-6f, -13f, 6f, -13f, "E-SYMBOLS")       // battery plates
            appendLineEntity(-3f, -16f, 3f, -16f, "E-SYMBOLS")
            appendLineEntity(-18f, 7f, -18f, 14f, "E-SYMBOLS")       // static bypass riser
            appendLineEntity(-18f, 14f, -6f, 14f, "E-SYMBOLS")
            appendLineEntity(-6f, 14f, 5f, 19f, "E-SYMBOLS")         // open disconnect blade
            appendLineEntity(18f, 7f, 18f, 14f, "E-SYMBOLS")         // bypass riser (out)
            appendLineEntity(18f, 14f, 7f, 14f, "E-SYMBOLS")
            appendCircleEntity(6f, 14f, 1.2f, "E-SYMBOLS")           // fixed contact
            appendCircleEntity(-34f, 0f, 2f, "E-NORMAL")             // input terminal anchor
            appendCircleEntity(34f, 0f, 2f, "E-NORMAL")              // output terminal anchor
        }

        // ---- BLK_BUS_TIE — Normally-Open Bus Tie Coupler (IEC 60617) ----
        // W=40 H=24. Stubs at (-20,0) and (20,0). N.O. green badge.
        sb.blockDefinition(BLK_BUS_TIE) {
            appendLineEntity(-20f, 0f, -10f, 0f, "E-TIE")            // left busbar stub
            appendLineEntity(10f, 0f, 20f, 0f, "E-TIE")              // right busbar stub
            appendLineEntity(-10f, -8f, 10f, -8f, "E-TIE")           // coupler housing
            appendLineEntity(10f, -8f, 10f, 8f, "E-TIE")
            appendLineEntity(10f, 8f, -10f, 8f, "E-TIE")
            appendLineEntity(-10f, 8f, -10f, -8f, "E-TIE")
            appendLineEntity(-10f, 0f, -2f, 0f, "E-TIE")             // left contact
            appendLineEntity(2f, 0f, 10f, 0f, "E-TIE")               // right contact
            appendLineEntity(-2f, 0f, 2f, 7f, "E-TIE")               // open (raised) blade
            appendLineEntity(-8f, -11f, 8f, -11f, "E-TIE")           // interlock bracket
            appendTextEntity(-6f, -7f, "N.O.", 5f, "E-TIE")          // green N.O. badge
        }
    }

    // ==================================================================
    // PART 2 — TERMINAL-SNAPPED WIRE ROUTING (ZERO WIRE PENETRATION)
    // ==================================================================

    /** Screen-coordinate axis-aligned exclusion zone of one equipment symbol. */
    private data class ClipBox(
        val left: Float, val right: Float,
        val top: Float, val bottom: Float   // screen coords: top < bottom
    )

    private enum class EquipmentKind { TRANSFORMER, GENERATOR, ATS, UPS }

    private data class EquipmentSymbol(val kind: EquipmentKind, val cx: Float, val cy: Float)

    private fun equipmentSymbolOf(node: PositionedSldNode): EquipmentSymbol? {
        val p = node.panelType.uppercase()
        val cx = node.x + node.width / 2f
        val cy = node.y
        return when {
            p.contains("TRANSFORMER") -> EquipmentSymbol(EquipmentKind.TRANSFORMER, cx, cy)
            p.contains("GENERATOR") -> EquipmentSymbol(EquipmentKind.GENERATOR, cx, cy)
            p.contains("ATS") -> EquipmentSymbol(EquipmentKind.ATS, cx, cy)
            p.contains("UPS") -> EquipmentSymbol(EquipmentKind.UPS, cx, cy)
            else -> null
        }
    }

    /**
     * Symbol exclusion box in screen coordinates (y-down), spanning the full
     * terminal-anchor extents so no wire may overlap the symbol body or stubs.
     * Boxes are deflated by 1.0 unit so that wires approaching a terminal exactly
     * along its boundary are preserved (only strict interior penetration is cut).
     */
    private fun equipmentClipBox(symbol: EquipmentSymbol): ClipBox = when (symbol.kind) {
        EquipmentKind.TRANSFORMER -> ClipBox(symbol.cx - 17f, symbol.cx + 17f, symbol.cy - 39f, symbol.cy + 39f)
        EquipmentKind.GENERATOR -> ClipBox(symbol.cx - 21f, symbol.cx + 21f, symbol.cy - 21f, symbol.cy + 31f)
        EquipmentKind.ATS -> ClipBox(symbol.cx - 31f, symbol.cx + 31f, symbol.cy - 21f, symbol.cy + 21f)
        EquipmentKind.UPS -> ClipBox(symbol.cx - 33f, symbol.cx + 33f, symbol.cy - 19f, symbol.cy + 19f)
    }

    /** In-line feeder breaker center = midpoint of the final vertical drop. */
    private fun feederBreakerCenter(points: List<Pair<Float, Float>>): Pair<Float, Float>? {
        if (points.size < 2) return null
        val (x1, y1) = points[points.size - 2]
        val (x2, y2) = points[points.size - 1]
        return if (abs(x1 - x2) < 0.5f && abs(y2 - y1) >= 2f * MCCB_TERMINAL_HALF + 4f) {
            x1 to ((y1 + y2) / 2f)
        } else {
            null
        }
    }

    /**
     * Liang-Barsky segment/box intersection. Returns the (t0, t1) parameter
     * window of the segment INSIDE the box, or null when entirely outside.
     */
    private fun segmentInsideBoxT(
        x1: Float, y1: Float, x2: Float, y2: Float,
        box: ClipBox
    ): Pair<Float, Float>? {
        val dx = x2 - x1
        val dy = y2 - y1
        var t0 = 0f
        var t1 = 1f

        fun clip(p: Float, q: Float): Boolean {
            if (abs(p) < 1e-6f) return q >= 0f
            val r = q / p
            if (p < 0f) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }

        val inside = clip(-dx, x1 - box.left) &&
            clip(dx, box.right - x1) &&
            clip(-dy, y1 - box.top) &&
            clip(dy, box.bottom - y1)
        return if (inside) t0 to t1 else null
    }

    /** Splits a line into the sub-segments lying OUTSIDE the box (wire cut-out). */
    private fun clipLineOutsideBox(
        start: Offset, end: Offset, box: ClipBox
    ): List<Pair<Offset, Offset>> {
        val inside = segmentInsideBoxT(start.x, start.y, end.x, end.y, box)
            ?: return listOf(start to end)
        val out = mutableListOf<Pair<Offset, Offset>>()
        val enter = Offset(start.x + inside.first * (end.x - start.x), start.y + inside.first * (end.y - start.y))
        val exit = Offset(start.x + inside.second * (end.x - start.x), start.y + inside.second * (end.y - start.y))
        if (inside.first > 0.002f) out.add(start to enter)
        if (inside.second < 0.998f) out.add(exit to end)
        return out
    }

    /** Cuts every straight command out of all exclusion boxes; hop arcs pass through. */
    private fun clipCommandsAgainstBoxes(
        commands: List<SldHopArcEngine.HopPathCommand>,
        boxes: List<ClipBox>
    ): List<SldHopArcEngine.HopPathCommand> {
        var current = commands
        for (box in boxes) {
            val next = mutableListOf<SldHopArcEngine.HopPathCommand>()
            for (cmd in current) {
                if (cmd is SldHopArcEngine.HopPathCommand.Line) {
                    clipLineOutsideBox(cmd.start, cmd.end, box).forEach {
                        next.add(SldHopArcEngine.HopPathCommand.Line(it.first, it.second))
                    }
                } else {
                    next.add(cmd)
                }
            }
            current = next
        }
        return current
    }

    /**
     * Snaps the edge endpoint that lands on a piece of equipment to its discrete
     * terminal anchor: transformer MV/LV stubs, generator output stub, ATS
     * Normal/Emergency input ports and Load port, UPS input/output terminals.
     */
    private fun snapEdgeToTerminals(
        edge: PositionedSldEdge,
        isEmergency: Boolean,
        nodesById: Map<String, PositionedSldNode>
    ): List<Pair<Float, Float>> {
        var pts = edge.pathPoints
        if (pts.size < 2) return pts

        val fromNode = nodesById[edge.fromId]
        val toNode = nodesById[edge.toId]

        fun snapPoint(node: PositionedSldNode, isEnd: Boolean): List<Pair<Float, Float>> {
            val symbol = equipmentSymbolOf(node) ?: return pts
            val idx = if (isEnd) pts.size - 1 else 0
            val p = pts[idx]
            // Guard: only snap endpoints reasonably near the symbol
            if (abs(p.first - symbol.cx) > 260f || abs(p.second - symbol.cy) > 260f) return pts
            val out = pts.toMutableList()
            when (symbol.kind) {
                EquipmentKind.TRANSFORMER -> {
                    val targetY = if (p.second < symbol.cy) symbol.cy - 40f else symbol.cy + 40f
                    if (abs(p.first - symbol.cx) < 1f) out[idx] = symbol.cx to targetY
                }
                EquipmentKind.GENERATOR -> {
                    if (abs(p.first - symbol.cx) < 1f) out[idx] = symbol.cx to (symbol.cy + 32f)
                }
                EquipmentKind.UPS -> {
                    val targetX = if (isEnd) symbol.cx - 34f else symbol.cx + 34f
                    if (abs(p.second - symbol.cy) < 1f) out[idx] = targetX to p.second
                }
                EquipmentKind.ATS -> {
                    val target = when {
                        isEnd && isEmergency -> (symbol.cx + 18f) to (symbol.cy - 22f)
                        isEnd -> (symbol.cx - 18f) to (symbol.cy - 22f)
                        else -> symbol.cx to (symbol.cy + 22f)
                    }
                    if (abs(p.first - target.first) < 1f) {
                        out[idx] = target
                    } else if (isEnd) {
                        out[idx] = p.first to target.second
                        out.add(target)
                    } else {
                        out[idx] = target
                        out.add(1, p.first to target.second)
                    }
                }
            }
            return out
        }

        if (fromNode != null) pts = snapPoint(fromNode, isEnd = false)
        if (toNode != null) pts = snapPoint(toNode, isEnd = true)
        return pts
    }

    // ==================================================================
    // PART 3 — ANNOTATION CORRIDORS & DATA CLEANING (ZERO TEXT OVERLAP)
    // ==================================================================

    /**
     * Strips internal database clutter from equipment titles, e.g.
     * "(Dry Type (Cast Resin))" is removed entirely, leaving clean text.
     */
    private fun cleanEquipmentText(raw: String): String {
        var s = raw
        var prev: String
        do {
            prev = s
            s = s.replace(Regex("\\([^()]*\\)"), " ")
        } while (s != prev)
        return s.replace(Regex("\\s{2,}"), " ").trim(' ', '-').trim()
    }

    /**
     * Resolves the equipment designation for the annotation title: uses the first
     * label token when it reads like an engineering ID (e.g. "TX-01", "GEN-02"),
     * otherwise falls back to the canonical node id.
     */
    private fun equipmentDesignation(node: PositionedSldNode): String {
        val firstToken = node.label.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        val looksLikeId = firstToken.isNotEmpty() && firstToken.any { it.isDigit() }
        return if (looksLikeId) firstToken else node.id
    }

    /** Extracts a clean "[Rating] kVA" string for transformer/generator titles. */
    private fun extractKvaText(node: PositionedSldNode): String {
        node.dataBlock?.ratedKva?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*k\\s*VA", RegexOption.IGNORE_CASE)
        val match = regex.find(node.label) ?: regex.find(node.ratingText)
        return if (match != null) "${match.groupValues[1]} kVA" else cleanEquipmentText(node.ratingText)
    }

    /** Extracts a clean "[Rating]A (4P)" string for ATS titles. */
    private fun extractAtsRatingText(node: PositionedSldNode): String {
        val clean = cleanEquipmentText(node.ratingText)
        val amp = Regex("(\\d+(?:\\.\\d+)?)\\s*A(?![A-Za-z])").find(clean)?.groupValues?.get(1)
        val poles = when {
            clean.contains("4P") -> "(4P)"
            clean.contains("3P") -> "(3P)"
            else -> ""
        }
        return if (amp != null) "${amp}A $poles".trim() else clean
    }

    /** Engineering main busbar tag: "[BoardID] Main Busbar: [Rating]A, [Icw]kA/1s, 400V". */
    private fun buildMainBusbarTag(node: PositionedSldNode): String {
        val ratingA = Regex("(\\d+(?:\\.\\d+)?)\\s*A(?![A-Za-z])").find(node.ratingText)?.groupValues?.get(1)
        val icw = Regex("(\\d+(?:\\.\\d+)?)\\s*kA", RegexOption.IGNORE_CASE).find(node.ratingText)?.groupValues?.get(1)
        return buildString {
            append(node.id).append(" Main Busbar: ")
            if (ratingA != null) append(ratingA).append("A") else append(cleanEquipmentText(node.ratingText))
            if (icw != null) append(", ").append(icw).append("kA/1s")
            append(", 400V")
        }
    }

    /**
     * Canonical DXF generator converting an SLD layout graph to an AutoCAD R12
     * ASCII DXF with the IEC 60617 block library, terminal-snapped routing,
     * annotation corridors and full drafting hygiene.
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

        val nodesById = engineResult.nodes.associateBy { it.id }

        // Pre-compute in-line feeder breaker centers (screen coords, y-down)
        data class FeederBreaker(val edge: PositionedSldEdge, val x: Float, val y: Float)
        val feederBreakers = engineResult.edges.mapNotNull { edge ->
            val child = nodesById[edge.toId]
            if (child != null && !child.isSource && child.dataBlock != null) {
                feederBreakerCenter(edge.pathPoints)?.let { FeederBreaker(edge, it.first, it.second) }
            } else {
                null
            }
        }

        // Equipment exclusion boxes (deflated by 1.0 — see equipmentClipBox)
        val equipmentBoxes = engineResult.nodes
            .mapNotNull { equipmentSymbolOf(it) }
            .map { equipmentClipBox(it) }

        // 1. DXF HEADER (AutoCAD R12 - AC1009)
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n")
        sb.append("9\n\$DWGCODEPAGE\n3\nANSI_1252\n")
        sb.append("9\n\$INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n\$EXTMIN\n10\n").append(formatFloat(sheetLeft)).append("\n20\n").append(formatFloat(flipY(sheetBottom))).append("\n30\n0.0\n")
        sb.append("9\n\$EXTMAX\n10\n").append(formatFloat(sheetRight)).append("\n20\n").append(formatFloat(flipY(sheetTop))).append("\n30\n0.0\n")
        sb.append("0\nENDSEC\n")

        // 2. DXF TABLES (LTYPE, LAYER with standard lineweights, STYLE)
        sb.append("0\nSECTION\n2\nTABLES\n")

        // LTYPE Table
        sb.append("0\nTABLE\n2\nLTYPE\n70\n3\n")
        sb.append("0\nLTYPE\n2\nCONTINUOUS\n70\n64\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        sb.append("0\nLTYPE\n2\nDASHED\n70\n64\n3\n__ __ __ __\n72\n65\n73\n2\n40\n9.0\n49\n6.0\n49\n-3.0\n")
        sb.append("0\nLTYPE\n2\nCENTER\n70\n64\n3\n____ _ ____\n72\n65\n73\n4\n40\n16.0\n49\n10.0\n49\n-2.0\n49\n2.0\n49\n-2.0\n")
        sb.append("0\nENDTAB\n")

        // LAYER Table — includes MANDATORY layer "0" (DXF spec).
        // STRICT R12 COMPATIBILITY NOTE: the AC1009 symbol-table grammar has NO
        // group code 370 (lineweights were introduced in AutoCAD 2000/AC1015).
        // Emitting 370 in an R12 file makes strict CAD parsers (AutoCAD legacy
        // R12 import, ODA-based viewers) reject the file. Line weight policy is
        // therefore carried by the STANDARD LAYER NAMES (E-PANEL heavy copper,
        // E-NORMAL/E-EMERG medium feeders, E-SYMBOLS/E-TX/E-GEN standard,
        // E-TEXT/E-BORDER thin), which plot styles (CTB) map to weights.
        sb.append("0\nTABLE\n2\nLAYER\n70\n11\n")
        val layers = listOf(
            Triple("0", 7, "CONTINUOUS"),            // White (ACI 7) - MANDATORY default layer 0
            Triple("E-TX", 4, "CONTINUOUS"),         // Cyan (ACI 4) - Transformers
            Triple("E-GEN", 2, "CONTINUOUS"),        // Yellow (ACI 2) - Generators
            Triple("E-EMERG", 1, "DASHED"),          // Red (ACI 1) - Emergency Feeders / ATS Lines
            Triple("E-NORMAL", 5, "CONTINUOUS"),     // Blue (ACI 5) - Normal Distribution Feeders
            Triple("E-TIE", 3, "CONTINUOUS"),        // Green (ACI 3) - Bus Ties & Couplers
            Triple("E-PANEL", 7, "CONTINUOUS"),      // White (ACI 7) - Switchboards & Panels (heavy)
            Triple("E-TEXT-TITLE", 7, "CONTINUOUS"), // White (ACI 7) - Major Equipment Titles
            Triple("E-TEXT-DATA", 4, "CONTINUOUS"),  // Cyan (ACI 4) - Cable & Rating Data (thin)
            Triple("E-SYMBOLS", 3, "CONTINUOUS"),    // Green (ACI 3) - Breakers & Meters
            Triple("E-BORDER", 7, "CONTINUOUS")      // White (ACI 7) - Drawing Sheet Border (thin)
        )
        for ((name, color, ltype) in layers) {
            sb.append("0\nLAYER\n")
            sb.append("2\n").append(name).append("\n")
            sb.append("70\n64\n")
            sb.append("62\n").append(color).append("\n")
            sb.append("6\n").append(ltype).append("\n")
        }
        sb.append("0\nENDTAB\n")

        // STYLE Table — mandatory STANDARD style for TEXT entities
        sb.append("0\nTABLE\n2\nSTYLE\n70\n1\n")
        sb.append("0\nSTYLE\n")
        sb.append("2\nSTANDARD\n")
        sb.append("70\n0\n")
        sb.append("40\n0.0\n")
        sb.append("41\n1.0\n")
        sb.append("50\n0.0\n")
        sb.append("71\n0\n")
        sb.append("42\n2.5\n")
        sb.append("3\ntxt\n")
        sb.append("0\nENDTAB\n")
        sb.append("0\nENDSEC\n")

        // 3. DXF BLOCKS — Master IEC 60617 / IEEE 315 library (PART 1)
        sb.append("0\nSECTION\n2\nBLOCKS\n")
        buildIecBlockLibrary(sb)
        sb.append("0\nENDSEC\n")

        // 4. DXF ENTITIES
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // Screen Coordinate Helpers with automatic Y-Flip
        fun writeLine(x1: Float, y1: Float, x2: Float, y2: Float, layer: String, linetype: String = "CONTINUOUS") {
            if (abs(x1 - x2) < 0.01f && abs(y1 - y2) < 0.01f) return
            sb.appendLineEntity(x1, flipY(y1), x2, flipY(y2), layer, linetype)
        }

        fun writeArc(cx: Float, cy: Float, radius: Float, startAngle: Float, endAngle: Float, layer: String, linetype: String = "CONTINUOUS") {
            sb.appendArcEntity(cx, flipY(cy), radius, startAngle, endAngle, layer, linetype)
        }

        fun writeRectangle(x: Float, y: Float, width: Float, height: Float, layer: String, linetype: String = "CONTINUOUS") {
            writeLine(x, y, x + width, y, layer, linetype)
            writeLine(x + width, y, x + width, y + height, layer, linetype)
            writeLine(x + width, y + height, x, y + height, layer, linetype)
            writeLine(x, y + height, x, y, layer, linetype)
        }

        fun writeCircle(cx: Float, cy: Float, radius: Float, layer: String) {
            sb.appendCircleEntity(cx, flipY(cy), radius, layer)
        }

        fun writeText(text: String?, x: Float, y: Float, height: Float = 10f, layer: String = "E-TEXT-DATA") {
            sb.appendTextEntity(x, flipY(y), text, height, layer)
        }

        fun writeInsert(blockName: String, x: Float, y: Float, layer: String) {
            sb.appendInsertEntity(blockName, x, flipY(y), layer)
        }

        // ==========================================
        // A. Compact Margin Datum Tags (PART 4.1)
        // Full-width impaling guidelines are DELETED. Each tier datum becomes a
        // short tag on the far-left margin (X = 20..120), truncated so it can
        // never reach the nearest equipment body.
        // ==========================================
        val minNodeX = engineResult.nodes.minOfOrNull { it.x } ?: Float.MAX_VALUE
        engineResult.tierDatums.forEach { datum ->
            val blockingNodes = engineResult.nodes.filter {
                datum.y >= it.y - 20f && datum.y <= it.y + it.height + 20f
            }
            val limitX = blockingNodes.minOfOrNull { it.x } ?: minNodeX
            val tagEndX = minOf(120f, limitX - 20f)
            if (tagEndX > 40f) {
                writeLine(20f, datum.y, tagEndX, datum.y, "E-BORDER", "CENTER")
            }
            writeText(datum.label, 20f, datum.y + 8f, height = 10f, layer = "E-BORDER")
        }

        // ==========================================
        // B. Terminal-Snapped Wire Routing (PART 2) with Hop Arcs & Text Corridor
        // ==========================================
        val hopCrossings = SldHopArcEngine.detectHopCrossings(engineResult.edges)

        engineResult.edges.forEach { edge ->
            val isEmergency = edge.isEmergency || edge.label == "EMERGENCY"
            val wireLayer = if (isEmergency) "E-EMERG" else "E-NORMAL"
            val wireLinetype = if (isEmergency) "DASHED" else "CONTINUOUS"

            // 1. Snap endpoints to discrete equipment terminal anchors
            val snapped = snapEdgeToTerminals(edge, isEmergency, nodesById)

            // 2. Build hop-arc path commands (crossing bridges)
            val commands = SldHopArcEngine.buildPathCommands(edge.copy(pathPoints = snapped), hopCrossings)

            // 3. Cut every wire segment out of all equipment exclusion zones and
            //    this feeder's own breaker exclusion zone (Ybrk +/- 14.0)
            val ownBreaker = feederBreakers.firstOrNull { it.edge == edge }
            val boxes = if (ownBreaker != null) {
                equipmentBoxes + ClipBox(
                    ownBreaker.x - 10f, ownBreaker.x + 10f,
                    ownBreaker.y - MCCB_TERMINAL_HALF, ownBreaker.y + MCCB_TERMINAL_HALF
                )
            } else {
                equipmentBoxes
            }
            val routed = clipCommandsAgainstBoxes(commands, boxes)

            for (cmd in routed) {
                when (cmd) {
                    is SldHopArcEngine.HopPathCommand.Line -> {
                        writeLine(cmd.start.x, cmd.start.y, cmd.end.x, cmd.end.y, wireLayer, wireLinetype)
                    }
                    is SldHopArcEngine.HopPathCommand.Arc -> {
                        // Semicircular bridge hop arc in Cartesian space (Y flipped):
                        // start 270 degrees (Cartesian bottom), sweep CCW to 90 (Cartesian top)
                        writeArc(cmd.center.x, cmd.center.y, cmd.radius, 270f, 90f, wireLayer, wireLinetype)
                    }
                }
            }

            // Busbar tap junction dot at origin (panels only — never on equipment terminals)
            val fromNode = nodesById[edge.fromId]
            if (!isEmergency && fromNode != null && !fromNode.isSource &&
                equipmentSymbolOf(fromNode) == null && snapped.isNotEmpty()
            ) {
                writeCircle(snapped.first().first, snapped.first().second, 3.5f, "E-NORMAL")
            }
        }

        // ==========================================
        // B2. In-Line Breaker Blocks & Feeder Data Corridor (PART 2.1 + PART 3.1)
        // Incoming wire terminated at (Xbrk, Ybrk + 14); outgoing wire originates
        // at (Xbrk, Ybrk - 14) — enforced by the exclusion cut above. The breaker
        // symbol itself is a reusable BLK_MCCB INSERT.
        // ==========================================
        feederBreakers.forEach { fb ->
            val child = nodesById[fb.edge.toId] ?: return@forEach
            val db = child.dataBlock ?: return@forEach

            // Breaker block instance (terminal-snapped)
            writeInsert(BLK_MCCB, fb.x, fb.y, "E-SYMBOLS")

            // Dedicated right-hand annotation corridor (Xwire + 18.0, dY = -13.0)
            val textX = fb.x + TEXT_CORRIDOR_OFFSET
            val step = CORRIDOR_LINE_SPACING

            // Line 1: Breaker rating (E-TEXT-TITLE, height 11.0)
            db.deviceTypeAndRating?.takeIf { it.isNotBlank() }?.let {
                writeText(it, textX, fb.y - 6f, height = 11f, layer = "E-TEXT-TITLE")
            }

            // Line 2: Cable size & material (E-TEXT-DATA, height 9.5)
            val runsText = if (db.parallelRuns > 1) "${db.parallelRuns}x " else ""
            db.cableDescription?.takeIf { it.isNotBlank() }?.let {
                writeText("$runsText$it", textX, fb.y - 6f + step, height = 9.5f, layer = "E-TEXT-DATA")
            }

            // Line 3: Length & voltage drop (E-TEXT-DATA, height 9.5)
            val lengthStr = db.lengthM?.takeIf { it.isNotBlank() }?.let { "L = ${it}m" }
            val vdStr = db.voltageDropPct?.takeIf { it.isNotBlank() }?.let { "VD = $it" }
            val combinedLvd = listOfNotNull(lengthStr, vdStr).joinToString("  ")
            if (combinedLvd.isNotBlank()) {
                writeText(combinedLvd, textX, fb.y - 6f + 2 * step, height = 9.5f, layer = "E-TEXT-DATA")
            }

            // Line 4: Short-circuit fault level (E-TEXT-DATA, height 9.5)
            val ik3 = db.faultLevelKa.orEmpty().replace("kA", "", ignoreCase = true).trim()
            if (ik3.isNotBlank()) {
                writeText("Isc = $ik3 kA", textX, fb.y - 6f + 3 * step, height = 9.5f, layer = "E-TEXT-DATA")
            }
        }

        // ==========================================
        // C. Equipment Nodes — IEC 60617 BLOCK INSERTs + Sanitized Titles (PART 3.2)
        // ==========================================
        engineResult.nodes.forEach { node ->
            val panelUpper = node.panelType.uppercase()
            val symbol = equipmentSymbolOf(node)

            when {
                symbol != null && symbol.kind == EquipmentKind.TRANSFORMER -> {
                    // Dyn11 Step-Down Power Transformer block instance
                    writeInsert(BLK_TRANSFORMER_DYN11, symbol.cx, symbol.cy, "E-TX")
                    val vectorGroup = node.dataBlock?.windingConnection
                        ?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                    writeText(equipmentDesignation(node), symbol.cx + 45f, symbol.cy - 10f, height = 12f, layer = "E-TEXT-TITLE")
                    writeText(
                        "${extractKvaText(node)}\n${cleanEquipmentText(node.ratingText)}$vectorGroup",
                        symbol.cx + 45f, symbol.cy + 8f, height = 9.5f, layer = "E-TEXT-DATA"
                    )
                }

                symbol != null && symbol.kind == EquipmentKind.GENERATOR -> {
                    // Synchronous Standby Generator block instance
                    writeInsert(BLK_GENERATOR, symbol.cx, symbol.cy, "E-GEN")
                    writeText(equipmentDesignation(node), symbol.cx + 45f, symbol.cy - 10f, height = 12f, layer = "E-TEXT-TITLE")
                    writeText(
                        "${extractKvaText(node)} Prime\n400V 50Hz",
                        symbol.cx + 45f, symbol.cy + 8f, height = 9.5f, layer = "E-TEXT-DATA"
                    )
                }

                symbol != null && symbol.kind == EquipmentKind.ATS -> {
                    // 3-Terminal Automatic Transfer Switch block instance
                    writeInsert(BLK_ATS_3P, symbol.cx, symbol.cy, "E-SYMBOLS")
                    val scheme = if (
                        (node.label + node.ratingText).contains("INTERLOCK", ignoreCase = true)
                    ) {
                        "MECHANICALLY INTERLOCKED"
                    } else {
                        "AUTO TRANSFER SCHEME"
                    }
                    writeText(equipmentDesignation(node), symbol.cx + 45f, symbol.cy - 10f, height = 12f, layer = "E-TEXT-TITLE")
                    writeText(
                        "${extractAtsRatingText(node)}\n$scheme",
                        symbol.cx + 45f, symbol.cy + 8f, height = 9.5f, layer = "E-TEXT-DATA"
                    )
                }

                symbol != null && symbol.kind == EquipmentKind.UPS -> {
                    // Online Double-Conversion Static UPS block instance
                    writeInsert(BLK_UPS_ONLINE, symbol.cx, symbol.cy, "E-SYMBOLS")
                    writeText(equipmentDesignation(node), symbol.cx + 45f, symbol.cy - 10f, height = 12f, layer = "E-TEXT-TITLE")
                    writeText(
                        "${cleanEquipmentText(node.ratingText)}\nONLINE DOUBLE CONVERSION",
                        symbol.cx + 45f, symbol.cy + 8f, height = 9.5f, layer = "E-TEXT-DATA"
                    )
                }

                node.panelType == SldNodeType.BUS_TIE.name -> {
                    // Normally-Open Bus Tie Coupler block instance
                    val barHeight = maxOf(node.height, 16f)
                    val midX = node.x + node.width / 2f
                    val midY = node.y + barHeight / 2f
                    writeInsert(BLK_BUS_TIE, midX, midY, "E-TIE")
                    writeText(cleanEquipmentText(node.label), node.x, midY + 18f, height = 10f, layer = "E-TEXT-TITLE")
                }

                node.isSource && (
                    panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")
                    ) -> {
                    // Utility / grid infeed enclosure
                    val gridW = maxOf(node.width, 140f)
                    writeRectangle(node.x, node.y - 15f, gridW, 36f, "E-NORMAL")
                    writeText(node.label, node.x + 12f, node.y - 8f, height = 12f, layer = "E-TEXT-TITLE")
                }

                else -> {
                    // Main Switchboard / Sub-Distribution Board.
                    // NOTE: "SMDB" contains the substring "MDB" — classification must
                    // not treat sub-distribution boards as main boards, otherwise the
                    // dashed main enclosure would impale in-line feeder breakers.
                    val isMain = (panelUpper.startsWith("MDB") && !panelUpper.contains("SMDB")) ||
                        panelUpper.contains("MAIN")
                    val barHeight = maxOf(node.height, 16f)

                    if (isMain) {
                        // Outer enclosure frame (dashed boundary)
                        val encPad = 36f
                        writeRectangle(node.x - encPad, node.y - 110f, node.width + encPad * 2f, barHeight + 130f, "E-PANEL", "DASHED")
                        // Engineering main busbar tag parked ABOVE the enclosure,
                        // clear of all risers and feeder taps
                        writeText(buildMainBusbarTag(node), node.x - encPad + 8f, node.y - 126f, height = 9f, layer = "E-TEXT-DATA")
                    }

                    // Heavy copper busbar body (E-PANEL, 0.50mm lineweight)
                    writeRectangle(node.x, node.y, node.width, barHeight, "E-PANEL")

                    // Board title parked BELOW the bar (incoming risers terminate on
                    // the bar top edge — the zone above stays 100% clean)
                    writeText(node.label, node.x + 8f, node.y + barHeight + 12f, height = 13f, layer = "E-TEXT-TITLE")

                    if (!isMain && node.ratingText.isNotBlank()) {
                        writeText(node.ratingText, node.x + 8f, node.y + barHeight + 26f, height = 9f, layer = "E-TEXT-DATA")
                    }
                }
            }
        }

        // ==========================================
        // D. Drawing Sheet Border & Professional Title Block (PART 4.4)
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
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        writeText("PROJECT: $effectiveProjectName", tbX + 12f, tbY + 18f, height = 12f, layer = "E-BORDER")
        writeText("DRAWING: SINGLE LINE DIAGRAM (SLD)", tbX + 12f, tbY + 46f, height = 11f, layer = "E-BORDER")
        writeText("STANDARD: IEC 60364 / BS 7671 / IEC 60617", tbX + 12f, tbY + 74f, height = 9f, layer = "E-BORDER")
        writeText(
            "DATE: $dateStamp | REV: A | SCALE: NTS | VoltAtlas Electrical CAD Engine",
            tbX + 12f, tbY + 98f, height = 8f, layer = "E-BORDER"
        )

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
     * Encoded as UTF-8. Because text entities are sanitized to 7-bit ASCII
     * with standard AutoCAD escapes (\U+XXXX, %%c, etc.), the emitted bytes
     * are simultaneously 100% valid ASCII, UTF-8, and Windows-1252, completely
     * preventing charset decoding failures in both desktop and mobile CAD viewers.
     */
    fun writeDxfToStream(dxfContent: String, outputStream: OutputStream) {
        outputStream.write(dxfContent.toByteArray(Charsets.UTF_8))
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
            val cleanProjectName = projectName
                .trim()
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .takeIf { it.isNotBlank() } ?: "VoltAtlas"
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
                    putExtra(Intent.EXTRA_SUBJECT, "$cleanProjectName — Single Line Diagram (DXF)")
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "application/dxf",
                            "image/vnd.dxf",
                            "image/x-dxf",
                            "application/x-dxf",
                            "application/acad"
                        )
                    )
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