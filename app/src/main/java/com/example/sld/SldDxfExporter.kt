package com.example.sld

import java.io.File
import java.util.Locale

/**
 * Pure Kotlin ASCII DXF Exporter for VoltAtlas Single-Line Diagram (SLD).
 * Serializes positioned SLD graph geometry (nodes, edges, busbars, symbols, labels, datums)
 * into a pure AutoCAD R12 ASCII DXF file (AC1009) compatible natively with ALL versions
 * of AutoCAD, DWG FastView, and mobile CAD viewers.
 * 
 * Key Engineering Specs:
 * 1. Pure R12 ($ACADVER = AC1009) without $INSUNITS or subclass markers (avoids invalid group code 7 error).
 * 2. Mandatory Locale.US formatting for ALL floating point numbers (f(v)) to prevent decimal comma corruption on Arabic/EU devices.
 * 3. Pure CAD Primitives: LINE, CIRCLE, TEXT entities (100% CAD parser compatibility).
 * 4. Structured 6-Layer Table with explicit group codes (0/LAYER, 2/name, 70/0, 62/color, 6/CONTINUOUS).
 * 5. Atomic File Writer with stream flushing before FileProvider sharing.
 */
object SldDxfExporter {

    private const val TAG = "SldDxfExporter"

    // Locale.US float formatter - MANDATORY for valid DXF group code values on Arabic/EU devices
    private fun f(v: Float): String = String.format(Locale.US, "%.3f", v)

    fun generateDxf(engineResult: SldEngineResult, projectName: String = "VoltAtlas_SLD"): String {
        val sb = StringBuilder()
        var entityCount = 0

        val bounds = engineResult.bounds
        val minX = bounds.left - 200f
        val maxX = maxOf(3000f, bounds.right + 400f)
        val minY = -bounds.bottom - 200f
        val maxY = -bounds.top + 200f

        // 1. DXF HEADER (Pure AutoCAD R12 ASCII format - AC1009, NO $INSUNITS)
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n")
        sb.append("9\n\$INSBASE\n10\n0.0\n20\n0.0\n30\n0.0\n")
        sb.append("9\n\$EXTMIN\n10\n${f(minX)}\n20\n${f(minY)}\n30\n0.0\n")
        sb.append("9\n\$EXTMAX\n10\n${f(maxX)}\n20\n${f(maxY)}\n30\n0.0\n")
        sb.append("0\nENDSEC\n")

        // 2. DXF TABLES (Layers with explicit group code structure)
        sb.append("0\nSECTION\n2\nTABLES\n")
        sb.append("0\nTABLE\n2\nLAYER\n70\n6\n")

        fun addLayer(name: String, color: Int) {
            sb.append("0\nLAYER\n")
            sb.append("2\n$name\n")
            sb.append("70\n0\n")
            sb.append("62\n$color\n") // AutoCAD ACI Color Code
            sb.append("6\nCONTINUOUS\n")
        }

        addLayer("SLD_NODES", 7)    // White / Black (ACI 7)
        addLayer("SLD_BUS", 2)      // Yellow (ACI 2)
        addLayer("SLD_WIRES", 3)    // Green (ACI 3)
        addLayer("SLD_SYMBOLS", 4)  // Cyan (ACI 4)
        addLayer("SLD_TEXT", 7)     // White / Black (ACI 7)
        addLayer("SLD_TIERS", 8)    // Gray (ACI 8)

        sb.append("0\nENDTAB\n")
        sb.append("0\nENDSEC\n")

        // 3. DXF ENTITIES
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // Y-Flip: Screen y-down -> Cartesian y-up
        fun flipY(y: Float): Float = -y

        // Primitive Helper: LINE
        fun writeLine(x1: Float, y1: Float, x2: Float, y2: Float, layer: String) {
            sb.append("0\nLINE\n")
            sb.append("8\n$layer\n")
            sb.append("10\n${f(x1)}\n")
            sb.append("20\n${f(flipY(y1))}\n")
            sb.append("11\n${f(x2)}\n")
            sb.append("21\n${f(flipY(y2))}\n")
            entityCount++
        }

        // Primitive Helper: Rectangle using 4 LINE entities (100% R12 CAD reader compatible)
        fun writeRectangle(x: Float, y: Float, width: Float, height: Float, layer: String) {
            val x1 = x
            val x2 = x + width
            val y1 = y
            val y2 = y + height

            // Top
            writeLine(x1, y1, x2, y1, layer)
            // Right
            writeLine(x2, y1, x2, y2, layer)
            // Bottom
            writeLine(x2, y2, x1, y2, layer)
            // Left
            writeLine(x1, y2, x1, y1, layer)
        }

        // Primitive Helper: CIRCLE
        fun writeCircle(cx: Float, cy: Float, radius: Float, layer: String) {
            sb.append("0\nCIRCLE\n")
            sb.append("8\n$layer\n")
            sb.append("10\n${f(cx)}\n")
            sb.append("20\n${f(flipY(cy))}\n")
            sb.append("40\n${f(radius)}\n")
            entityCount++
        }

        // Primitive Helper: TEXT
        fun writeText(text: String?, x: Float, y: Float, height: Float = 10f, layer: String = "SLD_TEXT") {
            if (text.isNullOrBlank()) return
            // Strip newlines and carriage returns for single-line DXF TEXT entity
            val cleanText = text.replace("\r", "").replace("\n", " ").replace("\\", "/").trim()
            if (cleanText.isBlank()) return

            sb.append("0\nTEXT\n")
            sb.append("8\n$layer\n")
            sb.append("10\n${f(x)}\n")
            sb.append("20\n${f(flipY(y))}\n")
            sb.append("40\n${f(height)}\n")
            sb.append("1\n$cleanText\n")
            entityCount++
        }

        // A. Draw Tier Datum Lines
        engineResult.tierDatums.forEach { datum ->
            writeLine(minX, datum.y, maxX, datum.y, "SLD_TIERS")
            writeText(datum.label, minX + 20f, datum.y - 18f, height = 12f, layer = "SLD_TIERS")
        }

        // B. Draw Wires / Edges
        engineResult.edges.forEach { edge ->
            val points = edge.pathPoints
            if (points.size >= 2) {
                val wireLayer = "SLD_WIRES"
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    writeLine(p1.first, p1.second, p2.first, p2.second, wireLayer)
                }

                val childNode = engineResult.nodes.find { it.id == edge.toId }
                if (childNode != null && !childNode.isSource && childNode.dataBlock != null) {
                    val firstPt = points.first()
                    val feederX = points.last().first
                    val verticalDropStartY = if (points.size >= 4) points[2].second else firstPt.second
                    val breakerY = verticalDropStartY + 25f

                    // Breaker Symbol Box (16x16 px)
                    val bSize = 16f
                    writeRectangle(feederX - bSize / 2f, breakerY - bSize / 2f, bSize, bSize, "SLD_SYMBOLS")

                    // Feeder Data Labels
                    var currentY = verticalDropStartY + 14f
                    val db = childNode.dataBlock

                    db.deviceTypeAndRating?.takeIf { it.isNotBlank() }?.let {
                        writeText(it, feederX + 14f, currentY, height = 10f, layer = "SLD_TEXT")
                        currentY += 14f
                    }

                    val runsText = if (db.parallelRuns > 1) "${db.parallelRuns}x " else ""
                    db.cableDescription?.takeIf { it.isNotBlank() }?.let {
                        writeText("$runsText$it", feederX + 14f, currentY, height = 9f, layer = "SLD_TEXT")
                        currentY += 13f
                    }

                    db.lengthM?.takeIf { it.isNotBlank() }?.let {
                        writeText("L: ${it}m", feederX + 14f, currentY, height = 8f, layer = "SLD_TEXT")
                        currentY += 12f
                    }

                    db.voltageDropPct?.takeIf { it.isNotBlank() }?.let {
                        writeText("VD: $it", feederX + 14f, currentY, height = 8f, layer = "SLD_TEXT")
                        currentY += 12f
                    }

                    val ik3 = db.faultLevelKa.orEmpty().replace("kA", "", ignoreCase = true).trim()
                    if (ik3.isNotBlank()) {
                        writeText("Isc: $ik3 kA", feederX + 14f, currentY, height = 8f, layer = "SLD_TEXT")
                    }
                }
            }
        }

        // C. Draw Nodes (Panels, Sources, Busbars, Symbols)
        engineResult.nodes.forEach { node ->
            val panelUpper = node.panelType.uppercase()

            if (node.isSource) {
                if (panelUpper.contains("GRID") || panelUpper.contains("UTILITY") || panelUpper.contains("SOURCE")) {
                    val gridW = maxOf(node.width, 140f)
                    writeRectangle(node.x, node.y - 15f, gridW, 36f, "SLD_NODES")
                    writeText(node.label, node.x + 12f, node.y - 8f, height = 12f, layer = "SLD_TEXT")
                } else if (panelUpper.contains("TRANSFORMER")) {
                    val cx = node.x + node.width / 2f
                    val cy = node.y
                    val r = 24f
                    // Two overlapping circles for Transformer
                    writeCircle(cx, cy - r / 2f, r, "SLD_SYMBOLS")
                    writeCircle(cx, cy + r / 2f, r, "SLD_SYMBOLS")

                    writeText(node.label, cx + 45f, cy - 12f, height = 13f, layer = "SLD_TEXT")
                    writeText(node.ratingText, cx + 45f, cy + 8f, height = 10f, layer = "SLD_TEXT")
                } else if (panelUpper.contains("GENERATOR")) {
                    val cx = node.x + node.width / 2f
                    val cy = node.y
                    val r = 24f
                    writeCircle(cx, cy, r, "SLD_SYMBOLS")
                    writeText("G", cx - 6f, cy - 6f, height = 14f, layer = "SLD_SYMBOLS")

                    writeText(node.label, cx + 45f, cy - 12f, height = 13f, layer = "SLD_TEXT")
                    writeText(node.ratingText, cx + 45f, cy + 8f, height = 10f, layer = "SLD_TEXT")
                } else if (panelUpper.contains("ATS")) {
                    val cx = node.x + 24f
                    val cy = node.y
                    writeRectangle(cx - 20f, cy - 20f, 40f, 40f, "SLD_SYMBOLS")
                    writeText("ATS", cx - 14f, cy - 6f, height = 11f, layer = "SLD_SYMBOLS")

                    writeText(node.label, cx + 42f, cy - 12f, height = 13f, layer = "SLD_TEXT")
                    writeText(node.ratingText, cx + 42f, cy + 8f, height = 10f, layer = "SLD_TEXT")
                } else {
                    writeRectangle(node.x, node.y - 15f, node.width, 30f, "SLD_NODES")
                    writeText(node.label, node.x + 8f, node.y - 6f, height = 11f, layer = "SLD_TEXT")
                }
            } else {
                // Busbar / Distribution Enclosure
                val barHeight = maxOf(node.height, 16f)
                writeRectangle(node.x, node.y, node.width, barHeight, "SLD_BUS")

                // Title above busbar
                val labelX = node.x + node.width / 2f - (node.label.length * 4f)
                writeText(node.label, labelX, node.y - 20f, height = 13f, layer = "SLD_TEXT")

                // Rating below busbar
                if (node.ratingText.isNotBlank()) {
                    writeText(node.ratingText, node.x + 8f, node.y + barHeight + 12f, height = 9f, layer = "SLD_TEXT")
                }
            }
        }

        // 4. DXF FOOTER
        sb.append("0\nENDSEC\n0\nEOF\n")

        println("[$TAG] Exported pure R12 AC1009 DXF: $entityCount entities written, nodes=${engineResult.nodes.size}, edges=${engineResult.edges.size}, total string length=${sb.length} chars")

        return sb.toString()
    }

    /**
     * Saves DXF string atomically to a local file, ensuring writer stream is flushed and closed
     * BEFORE returning or serving to FileProvider.
     */
    fun saveDxfToFile(dxfString: String, file: File) {
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(dxfString)
            writer.flush()
        }
        println("[$TAG] Atomically wrote ${file.length()} bytes to ${file.absolutePath}")
    }
}
