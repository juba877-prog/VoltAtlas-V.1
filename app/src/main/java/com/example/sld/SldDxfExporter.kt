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
/**
 * Stamped engineering drawing metadata for AutoCAD DXF exports (Roadmap Phase 3 §3.1).
 * All fields are sourced from real project data and system parameters.
 */
data class DxfDrawingMetadata(
    val projectName: String? = null,
    val projectCode: String? = null,
    val clientName: String? = null,
    val drawingNumber: String? = null,
    val drawingNumberPattern: String? = null,
    val revision: Long? = null,
    val drawnBy: String? = null,
    val checkedBy: String? = null,
    val approvedBy: String? = null,
    val dateStamp: String? = null,
    val standard: String? = null,
    val scale: String? = null
)

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

    fun StringBuilder.appendSolidEntity(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        x4: Float, y4: Float,
        layer: String
    ) {
        append("0\nSOLID\n")
        append("8\n").append(layer).append("\n")
        append("10\n").append(formatFloat(x1)).append("\n")
        append("20\n").append(formatFloat(y1)).append("\n")
        append("30\n0.0\n")
        append("11\n").append(formatFloat(x2)).append("\n")
        append("21\n").append(formatFloat(y2)).append("\n")
        append("31\n0.0\n")
        append("12\n").append(formatFloat(x3)).append("\n")
        append("22\n").append(formatFloat(y3)).append("\n")
        append("32\n0.0\n")
        append("13\n").append(formatFloat(x4)).append("\n")
        append("23\n").append(formatFloat(y4)).append("\n")
        append("33\n0.0\n")
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
                    val targetY = symbol.cy
                    if (abs(p.first - targetX) < 1f && abs(p.second - targetY) < 1f) {
                        out[idx] = targetX to targetY
                    } else if (isEnd) {
                        out[idx] = p.first to targetY
                        out.add(targetX to targetY)
                    } else {
                        out[idx] = targetX to targetY
                        out.add(1, p.first to targetY)
                    }
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

    private val RAW_DB_KEY_REGEX = Regex(
        """\b(?:tx|gen|ats|ups|circ|tie|grid|edge|node|unresolved)[_:](?:[0-9a-zA-Z_-]+)\b""",
        RegexOption.IGNORE_CASE
    )

    private val TYPE_PREFIX_REGEX = Regex(
        """^(?:tx|gen|ats|ups|circ|tie|grid|edge|node|unresolved)[_:\s]+(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Single ownership rule for unit tokens: ensures exactly ONE instance of the unit
     * is attached to a numeric value, preventing doubled tokens (e.g. "50 mm" for 50 metres,
     * "45.2 AA", "16 mm² mm²", "0.245 ΩΩ", etc.).
     */
    fun formatUnit(
        rawValue: String?,
        unit: String,
        space: Boolean = true
    ): String? {
        if (rawValue.isNullOrBlank()) return null
        val trimmed = rawValue.trim()

        val pattern = when (unit.lowercase(Locale.US)) {
            "m" -> Regex("""^(.*?)(?:\s*(?:mm|m|metres?|meters?))+\s*$""", RegexOption.IGNORE_CASE)
            "a" -> Regex("""^(.*?)(?:\s*(?:aa|a|amps?|amperes?))+\s*$""", RegexOption.IGNORE_CASE)
            "v" -> Regex("""^(.*?)(?:\s*(?:vv|v|volts?))+\s*$""", RegexOption.IGNORE_CASE)
            "ka" -> Regex("""^(.*?)(?:\s*(?:kaka|ka))+\s*$""", RegexOption.IGNORE_CASE)
            "kva" -> Regex("""^(.*?)(?:\s*(?:kvakva|kva))+\s*$""", RegexOption.IGNORE_CASE)
            "mva" -> Regex("""^(.*?)(?:\s*(?:mvamva|mva))+\s*$""", RegexOption.IGNORE_CASE)
            "kw" -> Regex("""^(.*?)(?:\s*(?:kwkw|kw))+\s*$""", RegexOption.IGNORE_CASE)
            "hz" -> Regex("""^(.*?)(?:\s*(?:hzhz|hz))+\s*$""", RegexOption.IGNORE_CASE)
            "s" -> Regex("""^(.*?)(?:\s*(?:ss|s|sec|seconds?))+\s*$""", RegexOption.IGNORE_CASE)
            "ah" -> Regex("""^(.*?)(?:\s*(?:ahah|ah))+\s*$""", RegexOption.IGNORE_CASE)
            "%" -> Regex("""^(.*?)(?:\s*(?:%%|%|pct|percent))+\s*$""", RegexOption.IGNORE_CASE)
            "mm²", "mm\u00b2", """mm\u+00b2""" -> Regex("""^(.*?)(?:\s*(?:mm\s*[\u00B22²]|mm\\U\+00B2|sqmm))+\s*$""", RegexOption.IGNORE_CASE)
            "ω", "\u03a9", """\u+03a9""" -> Regex("""^(.*?)(?:\s*(?:[\u03A9Ω]|\\U\+03A9|ohms?))+\s*$""", RegexOption.IGNORE_CASE)
            "mω", "m\u03a9", """m\u+03a9""" -> Regex("""^(.*?)(?:\s*(?:m[\u03A9Ω]|m\\U\+03A9|mohms?))+\s*$""", RegexOption.IGNORE_CASE)
            else -> Regex("""^(.*?)(?:\s*""" + Regex.escape(unit) + """)+\s*$""", RegexOption.IGNORE_CASE)
        }

        val base = pattern.matchEntire(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
        val cleanBase = if (base.isNotBlank()) base else trimmed
        val sep = if (space) " " else ""
        return "$cleanBase$sep$unit"
    }

    /**
     * Resolves human-facing equipment display name strictly from stored data (C1 / A3).
     * Renders the stored display-name field (circuitName or label), stripping any
     * artificial system type prefix (e.g. "Tx: TX-01" -> "TX-01").
     * If empty or equal to a raw database key, falls back to the raw stored identifier as-is.
     * Never transforms, renumbers, re-prefixes, synthesizes designations, or infers a board type.
     */
    fun resolveDisplayName(node: PositionedSldNode): String {
        val circuitName = node.circuit?.circuitName?.trim().orEmpty()
        if (circuitName.isNotBlank() && !circuitName.matches(RAW_DB_KEY_REGEX)) {
            val stripped = TYPE_PREFIX_REGEX.matchEntire(circuitName)?.groupValues?.get(1)?.trim()
            return if (!stripped.isNullOrBlank() && !stripped.matches(RAW_DB_KEY_REGEX)) stripped else circuitName
        }

        val rawLabel = node.label.trim()
        if (rawLabel.isNotBlank() && !rawLabel.matches(RAW_DB_KEY_REGEX)) {
            val stripped = TYPE_PREFIX_REGEX.matchEntire(rawLabel)?.groupValues?.get(1)?.trim()
            return if (!stripped.isNullOrBlank() && !stripped.matches(RAW_DB_KEY_REGEX)) stripped else rawLabel
        }

        return node.id
    }

    /**
     * Identifies nodes lacking a user-entered display name for non-blocking export warnings (A3.5).
     */
    fun detectUnnamedNodes(engineResult: SldEngineResult): List<String> {
        return engineResult.nodes.filter { node ->
            val circuitName = node.circuit?.circuitName?.trim().orEmpty()
            val rawLabel = node.label.trim()
            val hasStoredName = (circuitName.isNotBlank() && !circuitName.matches(RAW_DB_KEY_REGEX)) ||
                (rawLabel.isNotBlank() && !rawLabel.matches(RAW_DB_KEY_REGEX))
            !hasStoredName
        }.map { it.id }
    }

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
     * Resolves the equipment designation for the annotation title: uses human-facing
     * display name and eliminates raw database keys (1.2).
     */
    private fun equipmentDesignation(node: PositionedSldNode): String =
        resolveDisplayName(node)

    private fun extractKvaOnly(node: PositionedSldNode): String {
        val candidates = listOfNotNull(node.dataBlock?.ratedKva, node.ratingText, node.label)
        val kvaRegex = Regex("""\b(\d+(?:\.\d+)?)\s*k\s*VA\b""", RegexOption.IGNORE_CASE)
        for (c in candidates) {
            val m = kvaRegex.find(c)
            if (m != null) return "${m.groupValues[1]} kVA"
        }
        return ""
    }

    private fun extractTypeOnly(node: PositionedSldNode, kvaToExclude: String): String {
        var raw = (node.dataBlock?.ratedKva ?: node.ratingText).trim()
        if (kvaToExclude.isNotBlank()) {
            val num = kvaToExclude.replace("kVA", "", ignoreCase = true).trim()
            raw = raw.replace(Regex("""\b$num\s*k\s*VA\b""", RegexOption.IGNORE_CASE), "")
        }
        var s = raw.trim()
        var prev: String
        do {
            prev = s
            s = s.replace(Regex("""\(\s*\((.*?)\)\s*\)"""), "($1)")
            s = s.replace(Regex("""\(([^()]*)\(([^()]*)\)([^()]*)\)"""), "($1 $2 $3)")
        } while (s != prev)
        return s.trim(' ', '-', '·', ',').trim()
    }

    /**
     * Generic equipment title line builder that extracts distinct attributes,
     * eliminates nested parentheses, removes duplicate attributes/tokens,
     * and joins them cleanly with a standard bullet separator (1.3).
     */
    fun buildEquipmentTitleLine(vararg rawParts: String?): String {
        val seenTokens = mutableSetOf<String>()
        val cleanParts = mutableListOf<String>()

        for (part in rawParts) {
            if (part.isNullOrBlank()) continue
            var flattened = part.trim()
            var prev: String
            do {
                prev = flattened
                flattened = flattened.replace(Regex("""\(\s*\((.*?)\)\s*\)"""), "($1)")
                flattened = flattened.replace(Regex("""\(([^()]*)\(([^()]*)\)([^()]*)\)"""), "($1 $2 $3)")
            } while (flattened != prev)
            flattened = flattened.replace(Regex("""\s{2,}"""), " ").trim()

            val normalizedKey = flattened.lowercase(Locale.US).replace(Regex("""[^a-z0-9]"""), "")
            if (normalizedKey.isEmpty()) continue

            val isDuplicate = seenTokens.any { seen ->
                seen == normalizedKey ||
                    (normalizedKey.length > 5 && seen.contains(normalizedKey)) ||
                    (seen.length > 5 && normalizedKey.contains(seen))
            }
            if (!isDuplicate) {
                seenTokens.add(normalizedKey)
                cleanParts.add(flattened)
            }
        }
        return cleanParts.joinToString("  ·  ")
    }

    /** Extracts a clean "[Rating]A (4P)" string for ATS titles. */
    private fun extractAtsRatingText(node: PositionedSldNode): String {
        val clean = cleanEquipmentText(node.ratingText)
        val ampMatch = Regex("""\b(\d+(?:\.\d+)?)\s*A\b""", RegexOption.IGNORE_CASE).find(clean)
        val amp = ampMatch?.groupValues?.get(1)
        val poles = when {
            clean.contains("4P", ignoreCase = true) -> "(4P)"
            clean.contains("3P", ignoreCase = true) -> "(3P)"
            else -> ""
        }
        return when {
            amp != null && poles.isNotBlank() -> "${amp}A $poles"
            amp != null -> "${amp}A"
            else -> clean.ifBlank { "ATS" }
        }
    }

    /**
     * Engineering main busbar tag: "[BoardLabel] Main Busbar: [Material] Bus [Rating]A, [Voltage], [Icw]/1s".
     * Strict present-or-omit-cleanly rule for all optional segments.
     * Zero fabrication: never synthesizes "400A" fallback (A2) and omits unengineered busbar cross-section (A1).
     */
    private fun buildMainBusbarTag(node: PositionedSldNode): String {
        val db = node.dataBlock
        val boardLabel = resolveDisplayName(node)
        val material = db?.busbarMaterial?.takeIf { it.isNotBlank() } ?: "Copper"
        val rawRating = db?.busbarRatingA?.takeIf { it.isNotBlank() }
            ?: node.ratingText.takeIf { it.isNotBlank() }?.let { cleanEquipmentText(it) }
        val ratingA = rawRating?.let { formatUnit(it, "A", space = false) }

        val segments = mutableListOf<String>()
        if (ratingA != null) {
            segments.add("$material Bus $ratingA")
        }

        val vStr = db?.voltage?.takeIf { it.isNotBlank() }?.let { formatUnit(it, "V") }
        if (vStr != null) segments.add(vStr)

        val icwVal = db?.scRatingKA?.takeIf { it.isNotBlank() }
        if (icwVal != null) {
            val icwClean = formatUnit(icwVal, "kA", space = false)
            if (icwClean != null) {
                segments.add("$icwClean/1s")
            }
        }

        val ip = db?.ipRating?.takeIf { it.isNotBlank() }
        if (ip != null) segments.add(ip)

        // A1: Busbar cross-section removed per no-fabrication rule (V8)

        return if (segments.isNotEmpty()) {
            "$boardLabel Main Busbar: " + segments.joinToString(", ")
        } else {
            "$boardLabel Main Busbar"
        }
    }

    /**
     * Estimates rendered text width in drawing units for AutoCAD R12 STANDARD text style
     * (proportional to txt.shx with character width ~0.65 * textHeight).
     * Properly accounts for AutoCAD escape sequences (%%c, %%d, %%p, \U+XXXX) as single glyphs.
     */
    fun estimateTextWidth(text: String?, height: Float): Float {
        if (text.isNullOrEmpty()) return 0f
        var charCount = 0
        var i = 0
        while (i < text.length) {
            if (text.startsWith("%%", i) && i + 2 < text.length) {
                charCount++
                i += 3
            } else if (text.startsWith("\\U+", i) && i + 6 < text.length) {
                charCount++
                i += 7
            } else {
                charCount++
                i++
            }
        }
        return charCount * (height * 0.65f)
    }

    /**
     * Greedily wraps tokens onto stacked lines bounded by [maxCorridorWidth].
     * If a single token exceeds [maxCorridorWidth], it breaks cleanly on space boundaries.
     */
    fun wrapTokensToCorridor(
        tokens: List<String>,
        maxCorridorWidth: Float,
        textHeight: Float,
        separator: String = "  |  "
    ): List<String> {
        if (tokens.isEmpty()) return emptyList()

        val effectiveTokens = mutableListOf<String>()
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            if (estimateTextWidth(trimmed, textHeight) > maxCorridorWidth && trimmed.contains(" ")) {
                val words = trimmed.split(" ")
                var chunk = StringBuilder()
                for (w in words) {
                    if (chunk.isEmpty()) {
                        chunk.append(w)
                    } else {
                        val cand = "$chunk $w"
                        if (estimateTextWidth(cand, textHeight) <= maxCorridorWidth) {
                            chunk.append(" ").append(w)
                        } else {
                            effectiveTokens.add(chunk.toString())
                            chunk = StringBuilder(w)
                        }
                    }
                }
                if (chunk.isNotEmpty()) effectiveTokens.add(chunk.toString())
            } else {
                effectiveTokens.add(trimmed)
            }
        }

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (token in effectiveTokens) {
            if (currentLine.isEmpty()) {
                currentLine.append(token)
            } else {
                val candidate = "$currentLine$separator$token"
                if (estimateTextWidth(candidate, textHeight) <= maxCorridorWidth) {
                    currentLine.append(separator).append(token)
                } else {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(token)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Canonical DXF generator converting an SLD layout graph to an AutoCAD R12
     * ASCII DXF with the IEC 60617 block library, terminal-snapped routing,
     * annotation corridors and full drafting hygiene.
     */
    fun generateDxf(
        engineResult: SldEngineResult,
        projectName: String = "VoltAtlas_SLD",
        projectRevision: Long? = null,
        metadata: DxfDrawingMetadata? = null
    ): String {
        val meta = metadata ?: DxfDrawingMetadata(
            projectName = projectName,
            revision = projectRevision
        )
        val sb = StringBuilder()

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

        // Determine available corridor width for each feeder breaker
        val corridorWidthByBreaker = feederBreakers.associateWith { fb ->
            val nextBreakerX = feederBreakers
                .filter { it.x > fb.x + 10f && abs(it.y - fb.y) < 300f }
                .minOfOrNull { it.x }
            val nextWireX = engineResult.edges.flatMap { edge ->
                edge.pathPoints.zipWithNext().mapNotNull { (p1, p2) ->
                    if (abs(p1.first - p2.first) < 1f && p1.first > fb.x + 10f) {
                        val minY = minOf(p1.second, p2.second)
                        val maxY = maxOf(p1.second, p2.second)
                        if (minY <= fb.y + 120f && maxY >= fb.y - 30f) p1.first else null
                    } else null
                }
            }.minOfOrNull { it }
            val nextNodeX = engineResult.nodes
                .filter { it.x > fb.x + 10f && it.y <= fb.y + 150f && it.y + it.height >= fb.y - 50f }
                .minOfOrNull { it.x }

            val nearestRightObstacle = listOfNotNull(nextBreakerX, nextWireX, nextNodeX).minOfOrNull { it }
            if (nearestRightObstacle != null) {
                val available = (nearestRightObstacle - fb.x) - (TEXT_CORRIDOR_OFFSET + 16f)
                available.coerceIn(140f, 320f)
            } else {
                240f
            }
        }

        // ------------------------------------------------------------------
        // TRUE CONTENT BOUNDING BOX (ROADMAP PHASE 2 §2.3)
        // Eliminates the hardcoded 3000x2000 empty void by measuring the true
        // geometric extents of all nodes, cards, enclosures, wires, and corridors.
        // ------------------------------------------------------------------
        var cMinX = Float.MAX_VALUE
        var cMaxX = -Float.MAX_VALUE
        var cMinY = Float.MAX_VALUE
        var cMaxY = -Float.MAX_VALUE

        fun includePoint(x: Float, y: Float) {
            if (x.isFinite()) {
                if (x < cMinX) cMinX = x
                if (x > cMaxX) cMaxX = x
            }
            if (y.isFinite()) {
                if (y < cMinY) cMinY = y
                if (y > cMaxY) cMaxY = y
            }
        }

        fun includeRect(x: Float, y: Float, w: Float, h: Float) {
            includePoint(x, y)
            includePoint(x + w, y + h)
        }

        // 1. Margin datums
        engineResult.tierDatums.forEach { datum ->
            includePoint(20f, datum.y - 15f)
            includePoint(120f, datum.y + 15f)
        }

        // 2. Wire paths
        engineResult.edges.forEach { edge ->
            edge.pathPoints.forEach { pt ->
                includePoint(pt.first, pt.second)
            }
        }

        // 3. Feeder breaker corridors
        feederBreakers.forEach { fb ->
            val corrW = corridorWidthByBreaker[fb] ?: 240f
            includePoint(fb.x - 14f, fb.y - 14f)
            includePoint(fb.x + TEXT_CORRIDOR_OFFSET + corrW, fb.y + 70f)
        }

        // 4. Nodes, cards, and enclosures
        engineResult.nodes.forEach { node ->
            val panelUpper = node.panelType.uppercase()
            val symbol = equipmentSymbolOf(node)
            when {
                symbol != null -> {
                    // Equipment symbol + right-side annotation lines
                    includeRect(symbol.cx - 35f, symbol.cy - 40f, 260f, 80f)
                }
                node.isSource && (panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")) -> {
                    val gridName = resolveDisplayName(node)
                    val gridDb = node.dataBlock
                    val gridU = gridDb?.transformerPrimaryVoltage?.takeIf { it.isNotBlank() }?.let {
                        val withUnit = formatUnit(it, "kV") ?: formatUnit(it, "V") ?: it
                        "U $withUnit"
                    }
                    val gridSk = formatUnit(gridDb?.gridFaultMVA, "MVA")?.let { "S_kQ $it" }
                    val gridInfeedLine = listOfNotNull(gridU, gridSk).joinToString("  |  ")
                    val titleW = estimateTextWidth(gridName, 12f)
                    val infeedW = estimateTextWidth(gridInfeedLine, 9f)
                    val gridW = maxOf(node.width, maxOf(titleW, infeedW) + 28f)
                    val gridH = if (gridInfeedLine.isNotBlank()) 48f else 34f
                    includeRect(node.x, node.y - 17f, gridW, gridH)
                }
                node.panelType == SldNodeType.BUS_TIE.name -> {
                    val barHeight = maxOf(node.height, 16f)
                    includeRect(node.x, node.y, node.width, barHeight + 24f)
                }
                else -> {
                    // Main switchboard or distribution board
                    val isMain = (panelUpper.startsWith("MDB") && !panelUpper.contains("SMDB")) ||
                        panelUpper.contains("MAIN")
                    val barHeight = maxOf(node.height, 16f)
                    val boardTitle = resolveDisplayName(node)
                    val boardDb = node.dataBlock
                    val busA = formatUnit(boardDb?.busbarRatingA, "A", space = false)?.let { "Bus $it" }
                    val busIcw = formatUnit(boardDb?.scRatingKA, "kA", space = false)?.let { "Icw $it" }
                    val waysStr = boardDb?.numberOfWays?.takeIf { it.isNotBlank() }?.let {
                        if (it.endsWith("ways", ignoreCase = true)) it else "$it ways"
                    }
                    val headerTokens = listOfNotNull(
                        busA, busIcw, boardDb?.ipRating?.takeIf { it.isNotBlank() },
                        boardDb?.panelLocation?.takeIf { it.isNotBlank() },
                        waysStr, boardDb?.mainIncoming?.takeIf { it.isNotBlank() }?.let { "In: $it" }
                    ).distinct()
                    val ratingClean = cleanEquipmentText(node.ratingText)
                    val shouldShowRating = !isMain && ratingClean.isNotBlank() && !headerTokens.any { it.contains(ratingClean) }
                    val tcl = formatUnit(boardDb?.boardTotalConnectedKva, "kVA")?.let { "TCL $it" }
                    val tdl = formatUnit(boardDb?.boardDemandKva, "kVA")?.let { "TDL $it" }
                    val df = formatUnit(boardDb?.boardDemandFactorPct, "%", space = false)?.let { "DF $it" }
                    val ib = formatUnit(boardDb?.boardDesignCurrentA, "A")?.let { "I $it" }
                    val unb = formatUnit(boardDb?.boardUnbalancePct, "%", space = false)?.let { "Unb $it" }
                    val loadTokens = listOfNotNull(tcl, tdl, df, ib, unb).distinct()

                    val rightNeighborX = engineResult.nodes
                        .filter { it.id != node.id && it.x > node.x + 1f && Math.abs(it.y - node.y) < 80f }
                        .minOfOrNull { it.x }
                    val maxCorridorWidth = if (rightNeighborX != null) {
                        ((rightNeighborX - node.x) - 20f).coerceAtLeast(node.width)
                    } else {
                        maxOf(node.width, 320f)
                    }

                    val textCorridor = maxCorridorWidth - 16f
                    val headerLines = wrapTokensToCorridor(headerTokens, textCorridor, 9f, separator = "  |  ")
                    val loadLines = wrapTokensToCorridor(loadTokens, textCorridor, 9f, separator = "  |  ")

                    val titleW = estimateTextWidth(boardTitle, 13f)
                    val ratingW = if (shouldShowRating) estimateTextWidth(ratingClean, 9f) else 0f
                    val allTextWidths = listOf(titleW, ratingW) +
                        headerLines.map { estimateTextWidth(it, 9f) } +
                        loadLines.map { estimateTextWidth(it, 9f) }
                    val maxTextW = allTextWidths.maxOrNull() ?: 0f
                    val effectiveW = if (isMain) {
                        maxOf(node.width, maxTextW + 24f)
                    } else {
                        maxOf(node.width, minOf(maxCorridorWidth, maxTextW + 24f))
                    }

                    var textY = node.y + barHeight + 14f
                    textY += 15f // board title
                    if (shouldShowRating) textY += 13f
                    headerLines.forEach { textY += 13f }
                    loadLines.forEach { textY += 13f }
                    val lastBottom = textY + 4f

                    if (isMain) {
                        val encPad = 36f
                        val encLeft = node.x - encPad
                        val encTop = node.y - 110f
                        val tagW = estimateTextWidth(buildMainBusbarTag(node), 10f)
                        val encW = maxOf(effectiveW + encPad * 2f, tagW + 28f)
                        val encH = (lastBottom + 16f) - encTop
                        includeRect(encLeft, encTop, encW, encH)
                    } else {
                        includeRect(node.x, node.y, effectiveW, lastBottom - node.y)
                    }
                }
            }
        }

        if (cMinX > cMaxX) {
            cMinX = 0f; cMaxX = 600f; cMinY = 0f; cMaxY = 400f
        }

        val contentW = maxOf(400f, cMaxX - cMinX)
        val contentH = maxOf(300f, cMaxY - cMinY)

        // ------------------------------------------------------------------
        // Dynamic Legend Item Discovery (Roadmap Phase 3 §3.2)
        // Scans the active diagram for real symbols, line styles, and engineering
        // abbreviations in use. Never a static literal block.
        // ------------------------------------------------------------------
        val hasTx = engineResult.nodes.any { equipmentSymbolOf(it)?.kind == EquipmentKind.TRANSFORMER }
        val hasGen = engineResult.nodes.any { equipmentSymbolOf(it)?.kind == EquipmentKind.GENERATOR }
        val hasAts = engineResult.nodes.any { equipmentSymbolOf(it)?.kind == EquipmentKind.ATS }
        val hasUps = engineResult.nodes.any { equipmentSymbolOf(it)?.kind == EquipmentKind.UPS }
        val hasBusTie = engineResult.nodes.any { it.panelType == SldNodeType.BUS_TIE.name }
        val hasMccb = feederBreakers.isNotEmpty()
        val hasBusbar = engineResult.nodes.any {
            val pt = it.panelType.uppercase()
            pt == SldNodeType.MDB.name || pt == SldNodeType.SMDB.name || pt == SldNodeType.DB.name || pt == SldNodeType.MCC.name ||
                pt.startsWith("MDB") || pt.startsWith("DB") || pt.startsWith("SMDB") || pt.contains("MAIN")
        }
        val hasEmerg = engineResult.edges.any { it.isEmergency }
        val hasNormal = engineResult.edges.any { !it.isEmergency }

        val symbolLegendItems = mutableListOf<String>()
        if (hasBusbar) symbolLegendItems.add("= CU BUSBAR (E-BUSBAR)")
        if (hasMccb) symbolLegendItems.add("[X] LV CIRCUIT BREAKER")
        if (hasTx) symbolLegendItems.add("(8) DYN11 TRANSFORMER")
        if (hasGen) symbolLegendItems.add("(G) STANDBY GENERATOR")
        if (hasAts) symbolLegendItems.add("[ATS] TRANSFER SWITCH")
        if (hasUps) symbolLegendItems.add("[UPS] STATIC UPS")
        if (hasBusTie) symbolLegendItems.add("|-| BUS TIE BREAKER")
        if (hasNormal) symbolLegendItems.add("--- NORMAL FEEDER (SOLID)")
        if (hasEmerg) symbolLegendItems.add("- - EMERG FEEDER (DASHED)")

        val allDataBlocks = engineResult.nodes.mapNotNull { it.dataBlock }
        val hasIcu = allDataBlocks.any { !it.interruptingRatingKa.isNullOrBlank() }
        val hasIb = allDataBlocks.any { it.cableIbA != null || it.boardDesignCurrentA != null }
        val hasIz = allDataBlocks.any { it.cableIzA != null }
        val hasVd = allDataBlocks.any { it.voltageDropPct != null || it.cumulativeVdPct != null }
        val hasIsc = allDataBlocks.any { it.faultLevelKa != null }
        val hasCpc = allDataBlocks.any { it.cableCpcMm2 != null }
        val hasZs = allDataBlocks.any { it.adsZsOhm != null }
        val hasProt = allDataBlocks.any {
            !it.protectionOverload.isNullOrBlank() || !it.protectionShortCircuit.isNullOrBlank() ||
                !it.protectionCableWithstand.isNullOrBlank() || !it.protectionSelectivity.isNullOrBlank()
        }
        val hasTcl = allDataBlocks.any { it.boardTotalConnectedKva != null }
        val hasTdl = allDataBlocks.any { it.boardDemandKva != null }
        val hasDf = allDataBlocks.any { it.boardDemandFactorPct != null }
        val hasIcw = allDataBlocks.any { !it.scRatingKA.isNullOrBlank() }
        val hasUnb = allDataBlocks.any { it.boardUnbalancePct != null }
        val hasFlc = hasTx && allDataBlocks.any { it.ibAmp != null }
        val hasZt = allDataBlocks.any { it.transformerZtMOhm != null }
        val hasBat = allDataBlocks.any { !it.upsBatteryAh.isNullOrBlank() }

        val abbrLegendItems = mutableListOf<String>()
        if (hasIcu) abbrLegendItems.add("Icu: Breaking Capacity (kA)")
        if (hasIb) abbrLegendItems.add("Ib: Design Current (A)")
        if (hasIz) abbrLegendItems.add("Iz: Cable Rating Derated (A)")
        if (hasVd) abbrLegendItems.add("VD / Cum: Volt Drop / Cumul (%)")
        if (hasIsc) abbrLegendItems.add("Isc: Fault Level at Board (kA)")
        if (hasCpc) abbrLegendItems.add("CPC: Circuit Protective Cond")
        if (hasZs) abbrLegendItems.add("Zs: Loop Impedance (ADS)")
        if (hasProt) abbrLegendItems.add("OL/SC/Wth: Protection Verdict")
        if (hasTcl) abbrLegendItems.add("TCL: Total Conn Load (kVA)")
        if (hasTdl) abbrLegendItems.add("TDL: Total Demand Load (kVA)")
        if (hasDf) abbrLegendItems.add("DF: Coincident Demand Factor")
        if (hasIcw) abbrLegendItems.add("Icw: Busbar Withstand (kA 1s)")
        if (hasUnb) abbrLegendItems.add("Unb: Phase Unbalance (%)")
        if (hasFlc) abbrLegendItems.add("FLC: TX Full Load Current (A)")
        if (hasZt) abbrLegendItems.add("Zt: Transformer Impedance")
        if (hasBat) abbrLegendItems.add("Bat: UPS Battery Autonomy")

        val marginX = 60f
        val marginY = 60f
        val tbW = 440f
        val tbH = 120f
        val legW = 440f
        val maxLegendRows = maxOf(symbolLegendItems.size, abbrLegendItems.size, 1)
        val legH = maxOf(90f, 32f + maxLegendRows * 10f)

        val minSheetW = legW + tbW + 40f + 2f * marginX
        val rawW = maxOf(contentW + 2f * marginX, minSheetW)
        val rawH = contentH + 2f * marginY

        val bottomBandH = maxOf(tbH, legH) + 25f
        val minRequiredBottom = cMaxY + bottomBandH
        val minRequiredTop = cMinY - marginY
        val minRequiredH = minRequiredBottom - minRequiredTop

        // Standard ISO drawing sheet aspect ratio (1.414 : 1 landscape)
        val targetAR = 1.414f
        var sheetH = maxOf(rawH, minRequiredH)
        var sheetW = maxOf(rawW, sheetH * targetAR)
        sheetH = maxOf(sheetH, sheetW / targetAR)
        sheetW = sheetH * targetAR

        var sheetLeft = cMinX - (sheetW - contentW) / 2f
        var sheetRight = sheetLeft + sheetW
        val extraH = sheetH - minRequiredH
        var sheetTop = minRequiredTop - extraH / 2f
        var sheetBottom = sheetTop + sheetH

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
        sb.append("0\nTABLE\n2\nLAYER\n70\n12\n")
        val layers = listOf(
            Triple("0", 7, "CONTINUOUS"),            // White (ACI 7) - MANDATORY default layer 0
            Triple("E-BUSBAR", 30, "CONTINUOUS"),    // Orange (ACI 30) - Heavy Solid Copper Busbars
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

        fun writeSolid(x: Float, y: Float, width: Float, height: Float, layer: String) {
            sb.appendSolidEntity(
                x1 = x, y1 = flipY(y + height),
                x2 = x + width, y2 = flipY(y + height),
                x3 = x, y3 = flipY(y),
                x4 = x + width, y4 = flipY(y),
                layer = layer
            )
        }

        fun writeCircle(cx: Float, cy: Float, radius: Float, layer: String) {
            sb.appendCircleEntity(cx, flipY(cy), radius, layer)
        }

        fun writeText(text: String?, x: Float, y: Float, height: Float = 10f, layer: String = "E-TEXT-DATA") {
            if (text.isNullOrBlank()) return
            // Guard: substitute real display name if a raw database key leaked into text (A3.4)
            val sanitized = text.replace(RAW_DB_KEY_REGEX) { match ->
                val rawKey = match.value
                val matchedNode = nodesById[rawKey]
                if (matchedNode != null) {
                    val realName = resolveDisplayName(matchedNode)
                    if (realName.isNotBlank() && !realName.matches(RAW_DB_KEY_REGEX)) {
                        realName
                    } else {
                        rawKey
                    }
                } else {
                    rawKey
                }
            }
            sb.appendTextEntity(x, flipY(y), sanitized, height, layer)
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
        // Stacked multi-line wrapped corridor prevents any text-on-text overlap (Roadmap Phase 2 §2.1).
        // ==========================================
        feederBreakers.forEach { fb ->
            val child = nodesById[fb.edge.toId] ?: return@forEach
            val db = child.dataBlock ?: return@forEach

            // Breaker block instance (terminal-snapped)
            writeInsert(BLK_MCCB, fb.x, fb.y, "E-SYMBOLS")

            // Dedicated right-hand annotation corridor (Xwire + 18.0)
            val textX = fb.x + TEXT_CORRIDOR_OFFSET
            val corridorWidth = corridorWidthByBreaker[fb] ?: 240f
            var currentY = fb.y - 14f

            // Line 1: Protection Device + Trip + Breaking Capacity Icu (E-TEXT-TITLE, height 10.0)
            val icuStr = db.interruptingRatingKa?.takeIf { it.isNotBlank() }?.let { "Icu $it" }
            val line1Parts = listOfNotNull(db.deviceTypeAndRating?.takeIf { it.isNotBlank() }, icuStr)
            val line1Lines = wrapTokensToCorridor(line1Parts, corridorWidth, 10f)
            line1Lines.forEach { line ->
                writeText(line, textX, currentY, height = 10f, layer = "E-TEXT-TITLE")
                currentY += 12.5f
            }

            // Line 2: Cable specification (E-TEXT-DATA, height 8.5)
            val runsText = if (db.parallelRuns > 1) "${db.parallelRuns}x " else ""
            val cableSpec = db.cableDescription?.takeIf { it.isNotBlank() }?.let { "$runsText$it" }
            val line2Lines = wrapTokensToCorridor(listOfNotNull(cableSpec), corridorWidth, 8.5f)
            line2Lines.forEach { line ->
                writeText(line, textX, currentY, height = 8.5f, layer = "E-TEXT-DATA")
                currentY += 11f
            }

            // Line 3: Route Length, Operating Current (Ib), Derated Capacity (Iz), and Voltage Drop (E-TEXT-DATA, height 8.5)
            val lengthStr = formatUnit(db.lengthM, "m")?.let { "L = $it" }
            val ibStr = formatUnit(db.cableIbA, "A")
            val izStr = formatUnit(db.cableIzA, "A")
            val utilStr = formatUnit(db.cableUtilizationPct, "%", space = false)?.let { " ($it)" } ?: ""
            val currentUtilStr = when {
                ibStr != null && izStr != null -> "Ib $ibStr / Iz $izStr$utilStr"
                izStr != null -> "Iz $izStr$utilStr"
                else -> null
            }
            val vdStr = formatUnit(db.voltageDropPct, "%", space = false)?.let { "VD $it" }
            val cumVdStr = formatUnit(db.cumulativeVdPct, "%", space = false)?.let { "(Cum $it)" }
            val vdFull = listOfNotNull(vdStr, cumVdStr).joinToString(" ")
            val line3Parts = listOfNotNull(lengthStr, currentUtilStr, vdFull.takeIf { it.isNotBlank() })
            val line3Lines = wrapTokensToCorridor(line3Parts, corridorWidth, 8.5f)
            line3Lines.forEach { line ->
                writeText(line, textX, currentY, height = 8.5f, layer = "E-TEXT-DATA")
                currentY += 11f
            }

            // Line 4: Fault Level (Isc), CPC/Neutral, Protection & ADS Verdicts (E-TEXT-DATA, height 8.5)
            val iscPart = formatUnit(db.faultLevelKa, "kA")?.let { "Isc = $it" }
            val cpcPart = formatUnit(db.cableCpcMm2, "mm\u00B2")?.let { "CPC $it" }
            val protParts = listOfNotNull(
                db.protectionOverload?.takeIf { it.isNotBlank() }?.let { "OL:$it" },
                db.protectionShortCircuit?.takeIf { it.isNotBlank() }?.let { "SC:$it" },
                db.protectionCableWithstand?.takeIf { it.isNotBlank() }?.let { "Wth:$it" },
                db.protectionSelectivity?.takeIf { it.isNotBlank() }?.let { "Sel:$it" }
            )
            val protSummary = if (protParts.isNotEmpty()) protParts.joinToString(" ") else null
            val zsStr = formatUnit(db.adsZsOhm, "\u03A9")?.let { "Zs $it" }
            val line4Parts = listOfNotNull(iscPart, cpcPart, protSummary, zsStr)
            val line4Lines = wrapTokensToCorridor(line4Parts, corridorWidth, 8.5f)
            line4Lines.forEach { line ->
                writeText(line, textX, currentY, height = 8.5f, layer = "E-TEXT-DATA")
                currentY += 11f
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
                    val txName = resolveDisplayName(node)
                    val txKva = extractKvaOnly(node)
                    val txType = extractTypeOnly(node, txKva)
                    val vectorGroup = node.dataBlock?.windingConnection?.takeIf { it.isNotBlank() }
                    val txFlc = formatUnit(node.dataBlock?.ibAmp, "A")?.let { "FLC $it" }
                    val txZ = formatUnit(node.dataBlock?.transformerZtMOhm, "m\u03A9")?.let { "Zt $it" }
                    val txIsc1 = formatUnit(node.dataBlock?.transformerIsc1PhaseKa, "kA")?.let { "Isc1 $it" }
                    val txIsc3 = formatUnit(node.dataBlock?.isc3PhaseKa, "kA")?.let { "Isc3 $it" }
                    val txLoading = formatUnit(node.dataBlock?.loadingPct, "%", space = false)?.let { "Load $it" }

                    writeText(txName, symbol.cx + 40f, symbol.cy - 16f, height = 11f, layer = "E-TEXT-TITLE")
                    val txRatingLine = buildEquipmentTitleLine(txKva, txType, vectorGroup)
                    if (txRatingLine.isNotBlank()) {
                        writeText(txRatingLine, symbol.cx + 40f, symbol.cy - 2f, height = 9f, layer = "E-TEXT-DATA")
                    }
                    val txMetrics = listOfNotNull(txFlc, txZ, txIsc1, txIsc3, txLoading).joinToString("  |  ")
                    if (txMetrics.isNotBlank()) {
                        writeText(txMetrics, symbol.cx + 40f, symbol.cy + 12f, height = 8f, layer = "E-TEXT-DATA")
                    }
                }

                symbol != null && symbol.kind == EquipmentKind.GENERATOR -> {
                    // Synchronous Standby Generator block instance
                    writeInsert(BLK_GENERATOR, symbol.cx, symbol.cy, "E-GEN")
                    val genName = resolveDisplayName(node)
                    val genKva = extractKvaOnly(node)
                    val genVoltage = formatUnit(node.dataBlock?.voltage, "V")
                    val genFreq = formatUnit(node.dataBlock?.voltageFrequencyHz, "Hz")
                    val genLoad = formatUnit(node.dataBlock?.loadingPct, "%", space = false)?.let { "Load $it" }
                    val genStart = formatUnit(node.dataBlock?.generatorMotorStartingKva, "kVA")?.let { "Mstart $it" }
                    val genStep = formatUnit(node.dataBlock?.generatorStepLoadKva, "kVA")?.let { "Step $it" }

                    writeText(genName, symbol.cx + 40f, symbol.cy - 18f, height = 11f, layer = "E-TEXT-TITLE")
                    val genRatingStr = if (genKva.isNotBlank()) "$genKva Prime" else "Standby Generator"
                    val genSpecLine = buildEquipmentTitleLine(genRatingStr, genVoltage, genFreq)
                    if (genSpecLine.isNotBlank()) {
                        writeText(genSpecLine, symbol.cx + 40f, symbol.cy - 4f, height = 9f, layer = "E-TEXT-DATA")
                    }
                    val genMetrics = listOfNotNull(genLoad, genStart, genStep).joinToString("  |  ")
                    if (genMetrics.isNotBlank()) {
                        writeText(genMetrics, symbol.cx + 40f, symbol.cy + 10f, height = 8f, layer = "E-TEXT-DATA")
                    }
                }

                symbol != null && symbol.kind == EquipmentKind.ATS -> {
                    // 3-Terminal Automatic Transfer Switch block instance
                    writeInsert(BLK_ATS_3P, symbol.cx, symbol.cy, "E-SYMBOLS")
                    val atsName = resolveDisplayName(node)
                    val scheme = if (
                        (node.label + node.ratingText).contains("INTERLOCK", ignoreCase = true)
                    ) {
                        "MECH INTERLOCKED"
                    } else {
                        "AUTO TRANSFER"
                    }
                    val atsRating = extractAtsRatingText(node)
                    val atsIcw = formatUnit(node.dataBlock?.atsRequiredIcwKa, "kA")?.let { "Icw $it" }
                    val atsIcm = formatUnit(node.dataBlock?.atsRequiredIcmKa, "kA")?.let { "Icm $it" }
                    val atsIg = formatUnit(node.dataBlock?.atsGoverningCurrentA, "A")?.let { "Ig $it" }
                    val atsTt = formatUnit(node.dataBlock?.atsTransferTimeS, "s", space = false)?.let { "Tt $it" }

                    writeText(atsName, symbol.cx + 40f, symbol.cy - 16f, height = 11f, layer = "E-TEXT-TITLE")
                    val atsLine2 = buildEquipmentTitleLine(atsRating, scheme)
                    if (atsLine2.isNotBlank()) {
                        writeText(atsLine2, symbol.cx + 40f, symbol.cy - 2f, height = 9f, layer = "E-TEXT-DATA")
                    }
                    val atsMetrics = listOfNotNull(atsIcw, atsIcm, atsIg, atsTt).joinToString("  |  ")
                    if (atsMetrics.isNotBlank()) {
                        writeText(atsMetrics, symbol.cx + 40f, symbol.cy + 12f, height = 8f, layer = "E-TEXT-DATA")
                    }
                }

                symbol != null && symbol.kind == EquipmentKind.UPS -> {
                    // Online Double-Conversion Static UPS block instance
                    writeInsert(BLK_UPS_ONLINE, symbol.cx, symbol.cy, "E-SYMBOLS")
                    val upsName = resolveDisplayName(node)
                    val upsKva = extractKvaOnly(node)
                    val upsBat = formatUnit(node.dataBlock?.upsBatteryAh, "Ah")?.let { "Bat $it" }
                    val upsStr = node.dataBlock?.upsBatteryStrings?.takeIf { it.isNotBlank() }
                    val upsFreq = formatUnit(node.dataBlock?.voltageFrequencyHz, "Hz")

                    writeText(upsName, symbol.cx + 40f, symbol.cy - 16f, height = 11f, layer = "E-TEXT-TITLE")
                    val upsRatingStr = if (upsKva.isNotBlank()) upsKva else cleanEquipmentText(node.ratingText)
                    val upsLine2 = buildEquipmentTitleLine(upsRatingStr, "ONLINE CONV")
                    if (upsLine2.isNotBlank()) {
                        writeText(upsLine2, symbol.cx + 40f, symbol.cy - 2f, height = 9f, layer = "E-TEXT-DATA")
                    }
                    val upsMetrics = listOfNotNull(upsBat, upsStr, upsFreq).joinToString("  |  ")
                    if (upsMetrics.isNotBlank()) {
                        writeText(upsMetrics, symbol.cx + 40f, symbol.cy + 12f, height = 8f, layer = "E-TEXT-DATA")
                    }
                }

                node.panelType == SldNodeType.BUS_TIE.name -> {
                    // Normally-Open Bus Tie Coupler block instance
                    val barHeight = maxOf(node.height, 16f)
                    val midX = node.x + node.width / 2f
                    val midY = node.y + barHeight / 2f
                    writeInsert(BLK_BUS_TIE, midX, midY, "E-TIE")
                    val tieName = resolveDisplayName(node)
                    writeText(tieName, node.x, midY + 18f, height = 10f, layer = "E-TEXT-TITLE")
                }

                node.isSource && (
                    panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")
                    ) -> {
                    // Utility / grid infeed enclosure (measure-then-draw: zero text escaping)
                    val gridName = resolveDisplayName(node)
                    val gridDb = node.dataBlock
                    val gridU = gridDb?.transformerPrimaryVoltage?.takeIf { it.isNotBlank() }?.let {
                        val withUnit = formatUnit(it, "kV") ?: formatUnit(it, "V") ?: it
                        "U $withUnit"
                    }
                    val gridSk = formatUnit(gridDb?.gridFaultMVA, "MVA")?.let { "S_kQ $it" }
                    val gridInfeedLine = listOfNotNull(gridU, gridSk).joinToString("  |  ")

                    val padH = 14f
                    val titleW = estimateTextWidth(gridName, 12f)
                    val infeedW = estimateTextWidth(gridInfeedLine, 9f)
                    val maxTextW = maxOf(titleW, infeedW)
                    val gridW = maxOf(node.width, maxTextW + padH * 2f)
                    val hasInfeed = gridInfeedLine.isNotBlank()
                    val gridH = if (hasInfeed) 48f else 34f
                    val gridTop = node.y - 17f

                    writeRectangle(node.x, gridTop, gridW, gridH, "E-NORMAL")
                    writeText(gridName, node.x + padH, gridTop + 14f, height = 12f, layer = "E-TEXT-TITLE")
                    if (hasInfeed) {
                        writeText(gridInfeedLine, node.x + padH, gridTop + 32f, height = 9f, layer = "E-TEXT-DATA")
                    }
                }

                else -> {
                    // Main Switchboard / Sub-Distribution Board.
                    // Measure-then-draw geometry ensures zero text escapes cards or enclosures (Roadmap Phase 2 §2.2).
                    val isMain = (panelUpper.startsWith("MDB") && !panelUpper.contains("SMDB")) ||
                        panelUpper.contains("MAIN")
                    val barHeight = maxOf(node.height, 16f)

                    val boardTitle = resolveDisplayName(node)
                    val boardDb = node.dataBlock
                    val busA = formatUnit(boardDb?.busbarRatingA, "A", space = false)?.let { "Bus $it" }
                    val busIcw = formatUnit(boardDb?.scRatingKA, "kA", space = false)?.let { "Icw $it" }
                    val waysStr = boardDb?.numberOfWays?.takeIf { it.isNotBlank() }?.let {
                        if (it.endsWith("ways", ignoreCase = true)) it else "$it ways"
                    }
                    val headerTokens = listOfNotNull(
                        busA,
                        busIcw,
                        boardDb?.ipRating?.takeIf { it.isNotBlank() },
                        boardDb?.panelLocation?.takeIf { it.isNotBlank() },
                        waysStr,
                        boardDb?.mainIncoming?.takeIf { it.isNotBlank() }?.let { "In: $it" }
                    ).distinct()

                    val ratingClean = cleanEquipmentText(node.ratingText)
                    val shouldShowRating = !isMain && ratingClean.isNotBlank() && !headerTokens.any { it.contains(ratingClean) }

                    val tcl = formatUnit(boardDb?.boardTotalConnectedKva, "kVA")?.let { "TCL $it" }
                    val tdl = formatUnit(boardDb?.boardDemandKva, "kVA")?.let { "TDL $it" }
                    val df = formatUnit(boardDb?.boardDemandFactorPct, "%", space = false)?.let { "DF $it" }
                    val ib = formatUnit(boardDb?.boardDesignCurrentA, "A")?.let { "I $it" }
                    val unb = formatUnit(boardDb?.boardUnbalancePct, "%", space = false)?.let { "Unb $it" }
                    val loadTokens = listOfNotNull(tcl, tdl, df, ib, unb).distinct()

                    val rightNeighborX = engineResult.nodes
                        .filter { it.id != node.id && it.x > node.x + 1f && Math.abs(it.y - node.y) < 80f }
                        .minOfOrNull { it.x }
                    val maxCorridorWidth = if (rightNeighborX != null) {
                        ((rightNeighborX - node.x) - 20f).coerceAtLeast(node.width)
                    } else {
                        maxOf(node.width, 320f)
                    }

                    val textCorridor = maxCorridorWidth - 16f
                    val headerLines = wrapTokensToCorridor(headerTokens, textCorridor, 9f, separator = "  |  ")
                    val loadLines = wrapTokensToCorridor(loadTokens, textCorridor, 9f, separator = "  |  ")

                    val titleW = estimateTextWidth(boardTitle, 13f)
                    val ratingW = if (shouldShowRating) estimateTextWidth(ratingClean, 9f) else 0f
                    val allTextWidths = listOf(titleW, ratingW) +
                        headerLines.map { estimateTextWidth(it, 9f) } +
                        loadLines.map { estimateTextWidth(it, 9f) }
                    val maxTextW = allTextWidths.maxOrNull() ?: 0f

                    val busbarTag = if (isMain) buildMainBusbarTag(node) else ""
                    val tagW = if (isMain) estimateTextWidth(busbarTag, 10f) else 0f

                    val effectiveWidth = if (isMain) {
                        maxOf(node.width, maxTextW + 24f)
                    } else {
                        maxOf(node.width, minOf(maxCorridorWidth, maxTextW + 24f))
                    }

                    var textY = node.y + barHeight + 14f
                    val linePositions = mutableListOf<Pair<String, Pair<Float, Float>>>()

                    linePositions.add(boardTitle to (textY to 13f))
                    textY += 15f

                    if (shouldShowRating) {
                        linePositions.add(ratingClean to (textY to 9f))
                        textY += 13f
                    }
                    headerLines.forEach { line ->
                        linePositions.add(line to (textY to 9f))
                        textY += 13f
                    }
                    loadLines.forEach { line ->
                        linePositions.add(line to (textY to 9f))
                        textY += 13f
                    }

                    val lastTextBottom = textY + 4f

                    if (isMain) {
                        // Outer enclosure frame (dashed boundary) sized dynamically to contain both tag and bottom lines
                        val encPad = 36f
                        val encLeft = node.x - encPad
                        val encTop = node.y - 110f
                        val encWidth = maxOf(effectiveWidth + encPad * 2f, tagW + 28f)
                        val encHeight = (lastTextBottom + 16f) - encTop
                        writeRectangle(encLeft, encTop, encWidth, encHeight, "E-PANEL", "DASHED")
                        // Switchboard designation / nameplate placed cleanly inside the cubicle top-left
                        writeText(busbarTag, encLeft + 14f, encTop + 14f, height = 10f, layer = "E-TEXT-TITLE")
                    }

                    // Heavy solid copper busbar body (E-BUSBAR, ACI 30 Vivid Copper Orange)
                    writeSolid(node.x, node.y, effectiveWidth, barHeight, "E-BUSBAR")
                    writeRectangle(node.x, node.y, effectiveWidth, barHeight, "E-BUSBAR")

                    // Board lines written below the busbar, completely contained by the busbar's span
                    linePositions.forEach { (text, yAndH) ->
                        val (yPos, h) = yAndH
                        val layer = if (h > 10f) "E-TEXT-TITLE" else "E-TEXT-DATA"
                        writeText(text, node.x + 8f, yPos, height = h, layer = layer)
                    }
                }
            }
        }

        // ==========================================
        // D. Drawing Sheet Border, Stamped Title Block & Dynamic Legend (Roadmap Phase 3)
        // ==========================================
        // Outer Sheet Frame
        writeRectangle(sheetLeft, sheetTop, sheetRight - sheetLeft, sheetBottom - sheetTop, "E-BORDER")
        // Inner Margin Frame
        writeRectangle(sheetLeft + 10f, sheetTop + 10f, sheetRight - sheetLeft - 20f, sheetBottom - sheetTop - 20f, "E-BORDER")

        // ------------------------------------------------------------------
        // Stamped Engineering Title Block (Bottom-Right Corner, Roadmap Phase 3 §3.1)
        // ------------------------------------------------------------------
        val tbX = sheetRight - tbW - 12f
        val tbY = sheetBottom - tbH - 12f

        writeRectangle(tbX, tbY, tbW, tbH, "E-BORDER")

        // Row 1: Project & Client / Project Code
        writeLine(tbX, tbY + 28f, tbX + tbW, tbY + 28f, "E-BORDER")
        writeLine(tbX + 295f, tbY, tbX + 295f, tbY + 28f, "E-BORDER")

        val effectiveProjectName = meta.projectName?.takeIf { it.isNotBlank() } ?: projectName.takeIf { it.isNotBlank() } ?: "VoltAtlas Project"
        writeText("PROJECT: $effectiveProjectName", tbX + 10f, tbY + 12f, height = 9.5f, layer = "E-BORDER")
        val clientLine = meta.clientName?.takeIf { it.isNotBlank() }?.let { "CLIENT: $it" }
        if (clientLine != null) {
            writeText(clientLine, tbX + 10f, tbY + 23f, height = 7.5f, layer = "E-BORDER")
        }
        val codeLine = meta.projectCode?.takeIf { it.isNotBlank() }?.let { "CODE: $it" }
        if (codeLine != null) {
            writeText(codeLine, tbX + 303f, tbY + 12f, height = 7.5f, layer = "E-BORDER")
        }
        writeText("STATUS: ISSUED FOR REVIEW", tbX + 303f, tbY + 23f, height = 7f, layer = "E-BORDER")

        // Row 2: Drawing Title & Drawing Number
        writeLine(tbX, tbY + 52f, tbX + tbW, tbY + 52f, "E-BORDER")
        writeLine(tbX + 295f, tbY + 28f, tbX + 295f, tbY + 52f, "E-BORDER")
        writeText("DRAWING: SINGLE LINE DIAGRAM (SLD)", tbX + 10f, tbY + 42f, height = 9.5f, layer = "E-BORDER")
        val dwgNoStr = meta.drawingNumber?.takeIf { it.isNotBlank() }
            ?: meta.drawingNumberPattern?.takeIf { it.isNotBlank() }?.let { pattern ->
                var s = pattern
                meta.projectCode?.let { s = s.replace("{projectCode}", it) }
                (meta.revision ?: projectRevision)?.let { s = s.replace("{revision}", it.toString()) }
                s
            }
            ?: "-"
        writeText("DWG NO: $dwgNoStr", tbX + 303f, tbY + 42f, height = 8f, layer = "E-BORDER")

        // Row 3: Engineering Sign-off Box (Drawn / Checked / Approved table)
        writeLine(tbX, tbY + 68f, tbX + tbW, tbY + 68f, "E-BORDER")
        writeLine(tbX, tbY + 92f, tbX + tbW, tbY + 92f, "E-BORDER")
        val col1W = 146f
        writeLine(tbX + col1W, tbY + 52f, tbX + col1W, tbY + 92f, "E-BORDER")
        writeLine(tbX + col1W * 2f, tbY + 52f, tbX + col1W * 2f, tbY + 92f, "E-BORDER")

        writeText("DESIGN / DRAWN", tbX + 10f, tbY + 63f, height = 7f, layer = "E-BORDER")
        writeText("CHECKED", tbX + col1W + 10f, tbY + 63f, height = 7f, layer = "E-BORDER")
        writeText("APPROVED", tbX + col1W * 2f + 10f, tbY + 63f, height = 7f, layer = "E-BORDER")

        writeText(meta.drawnBy?.takeIf { it.isNotBlank() } ?: "-", tbX + 10f, tbY + 83f, height = 8f, layer = "E-BORDER")
        writeText(meta.checkedBy?.takeIf { it.isNotBlank() } ?: "-", tbX + col1W + 10f, tbY + 83f, height = 8f, layer = "E-BORDER")
        writeText(meta.approvedBy?.takeIf { it.isNotBlank() } ?: "-", tbX + col1W * 2f + 10f, tbY + 83f, height = 8f, layer = "E-BORDER")

        // Row 4: Standards and Drawing Control footer
        writeLine(tbX, tbY + 106f, tbX + tbW, tbY + 106f, "E-BORDER")
        val standardStr = meta.standard?.takeIf { it.isNotBlank() }?.let { "STANDARD: $it" }
        if (standardStr != null) {
            writeText(standardStr, tbX + 10f, tbY + 102f, height = 7.5f, layer = "E-BORDER")
        }
        val dateStamp = meta.dateStamp?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val effectiveRevision = meta.revision ?: projectRevision
        val revSegment = effectiveRevision?.let { "REV: $it | " } ?: ""
        val scaleSegment = meta.scale?.takeIf { it.isNotBlank() }?.let { "SCALE: $it | " } ?: ""
        writeText(
            "DATE: $dateStamp | $revSegment${scaleSegment}VoltAtlas Electrical CAD Engine",
            tbX + 10f, tbY + 115f, height = 7f, layer = "E-BORDER"
        )

        // ------------------------------------------------------------------
        // Dynamic Drawing Legend (Bottom-Left Corner, Roadmap Phase 3 §3.2)
        // ------------------------------------------------------------------
        val legX = sheetLeft + 12f
        val legY = sheetBottom - legH - 12f

        writeRectangle(legX, legY, legW, legH, "E-BORDER")
        writeLine(legX, legY + 18f, legX + legW, legY + 18f, "E-BORDER")
        writeText("DRAWING LEGEND & DESIGNATIONS", legX + 10f, legY + 13f, height = 9f, layer = "E-BORDER")

        val legMidX = legX + 215f
        writeLine(legMidX, legY + 18f, legMidX, legY + legH, "E-BORDER")
        writeLine(legX, legY + 30f, legX + legW, legY + 30f, "E-BORDER")

        writeText("SYMBOLS & CIRCUITS", legX + 10f, legY + 26f, height = 7f, layer = "E-TEXT-TITLE")
        writeText("ENGINEERING DATA & STATUS", legMidX + 10f, legY + 26f, height = 7f, layer = "E-TEXT-TITLE")

        symbolLegendItems.forEachIndexed { idx, item ->
            writeText(item, legX + 10f, legY + 41f + idx * 10f, height = 6.5f, layer = "E-TEXT-DATA")
        }
        abbrLegendItems.forEachIndexed { idx, item ->
            writeText(item, legMidX + 10f, legY + 41f + idx * 10f, height = 6.5f, layer = "E-TEXT-DATA")
        }

        // 5. DXF FOOTER
        sb.append("0\nENDSEC\n0\nEOF\n")

        return sb.toString()
    }

    /**
     * Alias for generateDxf conforming to canonical CAD exporter interface.
     */
    fun exportToDxfString(
        engineResult: SldEngineResult,
        projectName: String = "VoltAtlas_SLD",
        projectRevision: Long? = null,
        metadata: DxfDrawingMetadata? = null
    ): String =
        generateDxf(engineResult, projectName, projectRevision, metadata)

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
        projectName: String = "VoltAtlas_SLD",
        projectRevision: Long? = null,
        metadata: DxfDrawingMetadata? = null
    ) {
        if (engineResult.nodes.isEmpty()) {
            Toast.makeText(context, "Diagram empty. Please add components first.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val unnamedNodes = detectUnnamedNodes(engineResult)
            if (unnamedNodes.isNotEmpty()) {
                android.util.Log.w(TAG, "SLD Export Warning: nodes lacking stored display names: $unnamedNodes")
            }
            val dxfString = generateDxf(engineResult, projectName, projectRevision, metadata)
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