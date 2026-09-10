package com.example.sld

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Phase 5 §5.2 — CAD-Grade Multi-Layer DXF Exporter Fidelity Unit Tests.
 *
 * Master Architectural Overhaul verification:
 * 1. PART 1 — Standard IEC 60617 / IEEE 315 BLOCK library definitions.
 * 2. PART 2 — Equipment instantiated via 0/INSERT; zero wire penetration into breakers.
 * 3. PART 3 — Feeder annotation corridor with >= 16.0 wire clearance.
 * 4. PART 4 — No guideline segment intersects any equipment visual bounds.
 */
class SldDxfExportCadFidelityTest {

    private fun createSampleEngineResult(): SldEngineResult {
        val txNode = PositionedSldNode(
            id = "TX-01",
            label = "TX-01 1000kVA",
            panelType = "TRANSFORMER",
            ratingText = "22kV / 400V",
            statusColorHex = "#38BDF8",
            x = 200f,
            y = 100f,
            width = 140f,
            height = 70f,
            isSource = true
        )

        val genNode = PositionedSldNode(
            id = "GEN-01",
            label = "GEN-01 500kVA",
            panelType = "GENERATOR",
            ratingText = "500 kVA 400V",
            statusColorHex = "#38BDF8",
            x = 700f,
            y = 100f,
            width = 140f,
            height = 70f,
            isSource = true
        )

        val atsNode = PositionedSldNode(
            id = "ATS-01",
            label = "ATS-01",
            panelType = "ATS",
            ratingText = "630A 4P - Interlocked",
            statusColorHex = "#F59E0B",
            x = 700f,
            y = 400f,
            width = 120f,
            height = 60f,
            isSource = true
        )

        val upsNode = PositionedSldNode(
            id = "UPS-01",
            label = "UPS-01",
            panelType = "UPS",
            ratingText = "60 kVA",
            statusColorHex = "#F59E0B",
            x = 950f,
            y = 400f,
            width = 120f,
            height = 60f,
            isSource = true
        )

        val mdbNode = PositionedSldNode(
            id = "MDB-1",
            label = "MDB-1 Main Switchboard",
            panelType = "MDB",
            ratingText = "2500A 3P 50kA",
            statusColorHex = "#10B981",
            x = 100f,
            y = 400f,
            width = 600f,
            height = 24f,
            isSource = false
        )

        val smdb1Node = PositionedSldNode(
            id = "SMDB-1",
            label = "SMDB-1 Essential",
            panelType = "SMDB",
            ratingText = "400A",
            statusColorHex = "#10B981",
            x = 200f,
            y = 700f,
            width = 140f,
            height = 36f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "MCCB 400A 3P",
                cableDescription = "4x(1x185 mm²) Cu/XLPE",
                parallelRuns = 1,
                lengthM = "35",
                voltageDropPct = "0.85%",
                faultLevelKa = "25.0 kA"
            )
        )

        val smdb2Node = PositionedSldNode(
            id = "SMDB-2",
            label = "SMDB-2 Lighting",
            panelType = "SMDB",
            ratingText = "250A",
            statusColorHex = "#10B981",
            x = 500f,
            y = 700f,
            width = 140f,
            height = 36f,
            isSource = false
        )

        val tieNode = PositionedSldNode(
            id = "TIE-01",
            label = "BUS TIE",
            panelType = "BUS_TIE",
            ratingText = "1250A",
            statusColorHex = "#10B981",
            x = 400f,
            y = 850f,
            width = 100f,
            height = 20f,
            isSource = false
        )

        val normalEdge = PositionedSldEdge(
            fromId = "TX-01",
            toId = "MDB-1",
            label = "Incomer",
            pathPoints = listOf(270f to 170f, 270f to 400f),
            isEmergency = false
        )

        val emergencyEdge = PositionedSldEdge(
            fromId = "GEN-01",
            toId = "ATS-01",
            label = "EMERGENCY",
            pathPoints = listOf(770f to 170f, 770f to 380f),
            isEmergency = true
        )

        val feederEdge = PositionedSldEdge(
            fromId = "MDB-1",
            toId = "SMDB-1",
            label = "Feeder",
            pathPoints = listOf(270f to 424f, 270f to 500f, 270f to 700f),
            isEmergency = false
        )

        // Crossing edge -> forces a DXF ARC hop bridge at (270, 560), clear of
        // the feeder breaker exclusion zone [586, 614].
        val crossingEdge = PositionedSldEdge(
            fromId = "MDB-1",
            toId = "SMDB-2",
            label = "Feeder B",
            pathPoints = listOf(100f to 560f, 570f to 560f),
            isEmergency = false
        )

        return SldEngineResult(
            nodes = listOf(txNode, genNode, atsNode, upsNode, mdbNode, smdb1Node, smdb2Node, tieNode),
            edges = listOf(normalEdge, emergencyEdge, feederEdge, crossingEdge),
            tierDatums = listOf(
                TierDatumLine(label = "Tier 0 (Sources)", y = 100f, tierIndex = 0),
                TierDatumLine(label = "Tier 1 (Main MV/LV)", y = 400f, tierIndex = 1),
                TierDatumLine(label = "Tier 2 (Sub-Distribution)", y = 700f, tierIndex = 2)
            ),
            bounds = Rect(0f, 0f, 1200f, 900f)
        )
    }

    // ------------------------------------------------------------------
    // Minimal DXF group-code parser for CAD fidelity assertions
    // ------------------------------------------------------------------
    private data class DxfPair(val code: Int, val value: String)

    private fun tokenize(dxf: String): List<DxfPair> {
        val lines = dxf.lines()
        val out = mutableListOf<DxfPair>()
        var i = 0
        while (i + 1 < lines.size) {
            out.add(DxfPair(lines[i].trim().toIntOrNull() ?: -1, lines[i + 1]))
            i += 2
        }
        return out
    }

    private fun section(dxf: String, name: String): List<DxfPair> {
        val pairs = tokenize(dxf)
        val out = mutableListOf<DxfPair>()
        var inside = false
        var i = 0
        while (i < pairs.size) {
            val p = pairs[i]
            when {
                p.code == 0 && p.value == "SECTION" ->
                    inside = pairs.getOrNull(i + 1)?.let { it.code == 2 && it.value == name } == true
                p.code == 0 && p.value == "ENDSEC" -> if (inside) break
                inside -> out.add(p)
            }
            i++
        }
        return out
    }

    private fun bodyValue(body: List<DxfPair>, code: Int): String? =
        body.firstOrNull { it.code == code }?.value

    private fun bodyFloat(body: List<DxfPair>, code: Int): Float? =
        bodyValue(body, code)?.toFloatOrNull()

    /** Iterates entities; [onEntity] receives the entity tag and its group pairs. Returning false stops iteration. */
    private fun forEachEntity(pairs: List<DxfPair>, onEntity: (String, List<DxfPair>) -> Boolean) {
        var i = 0
        while (i < pairs.size) {
            if (pairs[i].code == 0) {
                val tag = pairs[i].value
                var j = i + 1
                val body = mutableListOf<DxfPair>()
                while (j < pairs.size && pairs[j].code != 0) {
                    body.add(pairs[j])
                    j++
                }
                if (!onEntity(tag, body)) return
                i = j
            } else {
                i++
            }
        }
    }

    // =====================================================================
    // PART 5.1 — Standard IEC BLOCK library must be defined in BLOCKS
    // =====================================================================
    @Test
    fun assertDxfDefinesStandardIecBlocks() {
        val dxf = SldDxfExporter.generateDxf(createSampleEngineResult(), "Block Library Audit")
        val blocks = section(dxf, "BLOCKS")

        val required = listOf(
            SldDxfExporter.BLK_MCCB,
            SldDxfExporter.BLK_ACB,
            SldDxfExporter.BLK_ATS_3P,
            SldDxfExporter.BLK_TRANSFORMER_DYN11,
            SldDxfExporter.BLK_GENERATOR,
            SldDxfExporter.BLK_UPS_ONLINE,
            SldDxfExporter.BLK_BUS_TIE
        )

        // Parse BLOCK records: name (group 2 after BLOCK tag) + geometry entity count
        val geometryCount = mutableMapOf<String, Int>()
        var currentName: String? = null
        var count = 0
        for (p in blocks) {
            when {
                p.code == 0 && p.value == "BLOCK" -> { currentName = null; count = 0 }
                p.code == 0 && p.value == "ENDBLK" -> {
                    currentName?.let { geometryCount[it] = count }
                    currentName = null
                }
                p.code == 2 && currentName == null -> currentName = p.value
                p.code == 0 && currentName != null -> count++
            }
        }

        required.forEach { name ->
            assertTrue(
                "BLOCKS section must define a valid $name block with geometry",
                (geometryCount[name] ?: 0) > 0
            )
        }

        // Every block record must be properly terminated with ENDBLK
        assertTrue(
            "BLOCKS section must contain ENDBLK terminators",
            blocks.count { it.code == 0 && it.value == "ENDBLK" } >= required.size
        )
    }

    // =====================================================================
    // PART 5.2 — Equipment symbols must be emitted as 0/INSERT entities
    // =====================================================================
    @Test
    fun assertEntitiesUseInsertForEquipment() {
        val dxf = SldDxfExporter.generateDxf(createSampleEngineResult(), "Insert Audit")
        val entities = section(dxf, "ENTITIES")

        val insertTargets = mutableSetOf<String>()
        forEachEntity(entities) { tag, body ->
            if (tag == "INSERT") {
                bodyValue(body, 2)?.let { insertTargets.add(it) }
            }
            true
        }

        listOf(
            SldDxfExporter.BLK_MCCB,
            SldDxfExporter.BLK_TRANSFORMER_DYN11,
            SldDxfExporter.BLK_GENERATOR,
            SldDxfExporter.BLK_ATS_3P,
            SldDxfExporter.BLK_UPS_ONLINE,
            SldDxfExporter.BLK_BUS_TIE
        ).forEach { name ->
            assertTrue(
                "Equipment symbol must be instantiated in ENTITIES as INSERT of $name",
                insertTargets.contains(name)
            )
        }
    }

    // =====================================================================
    // PART 5.3 — Zero wire penetration into in-line breakers
    // =====================================================================
    @Test
    fun assertZeroWirePenetrationIntoBreakers() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Penetration Audit")
        val entities = section(dxf, "ENTITIES")

        // In-line breaker center = midpoint of the feeder's final vertical drop
        val feeder = engineResult.edges.first { it.toId == "SMDB-1" }
        val pts = feeder.pathPoints
        val brkX = pts[pts.size - 2].first
        val brkY = (pts[pts.size - 2].second + pts[pts.size - 1].second) / 2f // 600.0 screen

        // DXF (Cartesian, y-flipped) exclusion interval [Ybrk - 14, Ybrk + 14]
        val dxfIntervalMin = -(brkY + SldDxfExporter.MCCB_TERMINAL_HALF) // -614
        val dxfIntervalMax = -(brkY - SldDxfExporter.MCCB_TERMINAL_HALF) // -586
        val bodyHalf = 10f

        val violators = mutableListOf<String>()
        forEachEntity(entities) { tag, body ->
            if (tag == "LINE") {
                val x1 = bodyFloat(body, 10)
                val y1 = bodyFloat(body, 20)
                val x2 = bodyFloat(body, 11)
                val y2 = bodyFloat(body, 21)
                if (x1 != null && y1 != null && x2 != null && y2 != null) {
                    val segMinX = minOf(x1, x2)
                    val segMaxX = maxOf(x1, x2)
                    val segMinY = minOf(y1, y2)
                    val segMaxY = maxOf(y1, y2)
                    val crossesX = segMinX < brkX + bodyHalf - 1f && segMaxX > brkX - bodyHalf + 1f
                    val crossesY = segMaxY > dxfIntervalMin + 1f && segMinY < dxfIntervalMax - 1f
                    if (crossesX && crossesY) {
                        violators.add("LINE($x1,$y1)-($x2,$y2)")
                    }
                }
            }
            true
        }

        assertTrue(
            "No wire segment may overlap the breaker exclusion zone " +
                "[$dxfIntervalMin, $dxfIntervalMax] on X=$brkX. Violators: $violators",
            violators.isEmpty()
        )
    }

    // =====================================================================
    // PART 5.4 — Feeder text corridor: minimum 16.0 units wire clearance
    // =====================================================================
    @Test
    fun assertTextCorridorHasZeroWireCollision() {
        val dxf = SldDxfExporter.generateDxf(createSampleEngineResult(), "Corridor Audit")
        val entities = section(dxf, "ENTITIES")

        // Feeder wire X (sample feeder drop)
        val wireX = 270f

        var tagCount = 0
        val violations = mutableListOf<String>()
        forEachEntity(entities) { tag, body ->
            if (tag == "TEXT") {
                val x = bodyFloat(body, 10)
                val text = bodyValue(body, 1).orEmpty()
                val isFeederTag = text.contains("MCCB") ||
                    text.contains("Cu/XLPE") ||
                    text.startsWith("L =") ||
                    text.startsWith("Isc =")
                if (isFeederTag && x != null) {
                    tagCount++
                    if (x - wireX < 16.0f) {
                        violations.add("TEXT '$text' at X=$x (clearance ${x - wireX})")
                    }
                }
            }
            true
        }

        assertTrue(
            "All feeder data tags must sit in the right-hand corridor with " +
                "Xtext - Xwire >= 16.0. Violations: $violations",
            violations.isEmpty()
        )
        assertTrue(
            "Expected the 4-line feeder data stack (breaker, cable, L/VD, Isc), found $tagCount",
            tagCount >= 4
        )
    }

    // =====================================================================
    // PART 5.5 — No horizontal guideline intersects equipment visual bounds
    // =====================================================================
    @Test
    fun assertNoGuidelinesIntersectEquipment() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Guideline Audit")
        val entities = section(dxf, "ENTITIES")

        // Equipment visual bounds in DXF (Cartesian, y-flipped) coordinates
        data class Box(val left: Float, val right: Float, val top: Float, val bottom: Float)
        val boxes = engineResult.nodes.map {
            Box(it.x, it.x + it.width, -it.y, -it.y - it.height)
        }

        val violations = mutableListOf<String>()
        forEachEntity(entities) { tag, body ->
            if (tag == "LINE" && bodyValue(body, 8) == "E-BORDER") {
                val x1 = bodyFloat(body, 10)
                val y1 = bodyFloat(body, 20)
                val x2 = bodyFloat(body, 11)
                val y2 = bodyFloat(body, 21)
                if (x1 != null && y1 != null && x2 != null && y2 != null && abs(y1 - y2) < 0.001f) {
                    val segMinX = minOf(x1, x2)
                    val segMaxX = maxOf(x1, x2)
                    boxes.forEach { b ->
                        val overlapX = segMaxX > b.left + 1f && segMinX < b.right - 1f
                        val overlapY = y1 < b.top - 1f && y1 > b.bottom + 1f
                        if (overlapX && overlapY) {
                            violations.add("GUIDELINE($x1,$y1)-($x2,$y2) x node box [${b.left},${b.right}]")
                        }
                    }
                }
            }
            true
        }

        assertTrue(
            "No horizontal E-BORDER guideline may slice through any equipment body. " +
                "Violations: $violations",
            violations.isEmpty()
        )
    }

    // =====================================================================
    // Retained fidelity: header, linetypes, layers & standard lineweights
    // =====================================================================
    @Test
    fun dxfHeader_containsValidLtypesAndLayers() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Project Alfa")

        // 1. AC1009 AutoCAD R12 Version & R12-valid codepage declaration
        assertTrue("DXF must declare AutoCAD R12 format AC1009", dxf.contains("9\n\$ACADVER\n1\nAC1009\n"))
        assertTrue("DXF must declare ANSI_1252 codepage", dxf.contains("\$DWGCODEPAGE\n3\nANSI_1252"))
        assertFalse("AC1009 header must not contain AC1015+ \$INSUNITS", dxf.contains("\$INSUNITS"))

        // 2. Standard electrical CAD layers
        listOf(
            "0", "E-TX", "E-GEN", "E-EMERG", "E-NORMAL", "E-TIE",
            "E-PANEL", "E-TEXT-TITLE", "E-TEXT-DATA", "E-SYMBOLS", "E-BORDER"
        ).forEach { layer ->
            assertTrue("LAYER table must define mandatory layer $layer", dxf.contains("2\n$layer\n"))
        }

        // 3. STRICT R12 purity — group code 370 (lineweight) is an AutoCAD 2000 /
        //    AC1015 code; emitting it in an AC1009 file makes strict CAD parsers
        //    (AutoCAD legacy R12 import, ODA-based viewers) reject the file.
        //    Line weights are mapped from the standard layer names via plot styles.
        assertFalse("AC1009 file must not contain AC1015+ group code 370", dxf.contains("\n370\n"))
        assertFalse("AC1009 file must not contain AC1015+ group code 1001 (XDATA)", dxf.contains("\n1001\n"))

        // 4. STYLE table with STANDARD text style
        assertTrue("STYLE table with STANDARD must exist", dxf.contains("TABLE\n2\nSTYLE") && dxf.contains("2\nSTANDARD\n"))

        // 5. BLOCKS section must be populated (not empty)
        assertTrue("BLOCKS section must exist", dxf.contains("SECTION\n2\nBLOCKS"))
    }

    @Test
    fun emergencyWires_haveDashedLinetypeAndDedicatedLayer() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Project Alfa")
        val entities = section(dxf, "ENTITIES")

        var foundDashedEmergencyWire = false
        forEachEntity(entities) { tag, body ->
            if (tag == "LINE" && bodyValue(body, 8) == "E-EMERG" && bodyValue(body, 6) == "DASHED") {
                foundDashedEmergencyWire = true
                return@forEachEntity false
            }
            true
        }

        assertTrue("Emergency wires must reside on E-EMERG with DASHED linetype", foundDashedEmergencyWire)
    }

    @Test
    fun textBlocks_doNotIntersectWireCoordinates() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Project Alfa")

        // The feeder wire is at X = 270.0; the corridor anchor is Xwire + 18.0 = 288.0
        assertTrue(
            "Feeder breaker tag text must be offset to X=288.000 (right-hand corridor)",
            dxf.contains("10\n288.000\n")
        )
        assertTrue(
            "Feeder cable spec must be placed cleanly on E-TEXT-DATA with sanitized Unicode escape for mm²",
            dxf.contains("1\n4x(1x185 mm\\U+00B2) Cu/XLPE\n") || dxf.contains("1\n4x(1x185 mm²) Cu/XLPE\n")
        )
    }

    @Test
    fun hopArcs_exportAsDxfArcs() {
        // Two crossing wires: horizontal (100,300)-(500,300), vertical (300,100)-(300,500)
        val hEdge = PositionedSldEdge(
            fromId = "A",
            toId = "B",
            label = "H-Wire",
            pathPoints = listOf(100f to 300f, 500f to 300f)
        )
        val vEdge = PositionedSldEdge(
            fromId = "C",
            toId = "D",
            label = "V-Wire",
            pathPoints = listOf(300f to 100f, 300f to 500f)
        )

        val crossingResult = SldEngineResult(
            nodes = emptyList(),
            edges = listOf(hEdge, vEdge),
            tierDatums = emptyList(),
            bounds = Rect(0f, 0f, 600f, 600f)
        )

        val dxf = SldDxfExporter.generateDxf(crossingResult, "Hop Test")

        // Assert that a true CAD ARC primitive was generated
        assertTrue("Crossing vertical wire must generate a DXF ARC bridge", dxf.contains("0\nARC\n"))
        assertTrue("ARC must have radius 8.000", dxf.contains("40\n8.000\n"))
    }

    @Test
    fun titleBlock_isGeneratedWithProjectMetadata() {
        val engineResult = createSampleEngineResult()
        val projectName = "Hospital Central Substation"
        val dxf = SldDxfExporter.generateDxf(
            engineResult = engineResult,
            projectName = projectName,
            metadata = DxfDrawingMetadata(standard = "IEC 60364 / BS 7671")
        )
        val dxfWithRevision = SldDxfExporter.generateDxf(
            engineResult = engineResult,
            projectName = projectName,
            projectRevision = 3L,
            metadata = DxfDrawingMetadata(standard = "IEC 60364 / BS 7671")
        )

        assertTrue("Title block must include project name", dxf.contains("PROJECT: $projectName"))
        assertTrue("Title block must include drawing subtitle", dxf.contains("DRAWING: SINGLE LINE DIAGRAM (SLD)"))
        assertTrue("Title block must include IEC standard", dxf.contains("STANDARD: IEC 60364 / BS 7671"))
        // Golden rule: the revision must come from ProjectEntity.revision — the real
        // value is rendered when supplied, and NO revision is ever fabricated when absent.
        assertTrue("Title block must render the real project revision", dxfWithRevision.contains("REV: 3"))
        assertTrue("Title block must not fabricate a revision when none is supplied", !dxf.contains("REV: "))
        assertTrue("Title block must include date", dxf.contains("DATE: "))
        assertTrue("Sheet frame must be on E-BORDER layer", dxf.contains("8\nE-BORDER\n"))
    }

    @Test
    fun dxfOutput_strictlyConformsToDxfGrammar() {
        val sampleEngineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.exportToDxfString(sampleEngineResult, "Gate 3 Test")
        val lines = dxf.lines().filter { it.isNotEmpty() }

        // 1. Every even line index must be a numeric DXF Group Code integer
        for (i in 0 until lines.size step 2) {
            val groupCode = lines[i].toIntOrNull()
            org.junit.Assert.assertNotNull("Line $i must be a valid integer group code, found: '${lines[i]}'", groupCode)
        }

        // 2. Assert no negative angles in group codes 50 and 51
        for (i in 0 until lines.size - 1) {
            if (lines[i] == "50" || lines[i] == "51") {
                val angle = lines[i + 1].toFloatOrNull() ?: 0f
                assertTrue("DXF angle in group ${lines[i]} must be non-negative: $angle", angle >= 0f && angle < 360f)
            }
        }

        // 3. Assert file ends strictly with 0 followed by EOF
        org.junit.Assert.assertEquals("0", lines[lines.size - 2])
        org.junit.Assert.assertEquals("EOF", lines[lines.size - 1])
    }

    @Test
    fun busbarLayerAndSolidColor_areExportedCorrectly() {
        val sampleEngineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(sampleEngineResult, "Gate 3")

        // 1. Assert E-BUSBAR layer is declared in TABLES with color 30 (Vivid Orange)
        assertTrue("DXF must define layer E-BUSBAR", dxf.contains("2\nE-BUSBAR\n"))
        assertTrue("Layer table must include color 30 for E-BUSBAR", dxf.contains("2\nE-BUSBAR\n70\n64\n62\n30\n"))

        // 2. Assert busbar is rendered with a SOLID entity on E-BUSBAR
        assertTrue("DXF must emit a filled SOLID entity for the copper busbar", dxf.contains("0\nSOLID\n8\nE-BUSBAR\n"))
    }
}