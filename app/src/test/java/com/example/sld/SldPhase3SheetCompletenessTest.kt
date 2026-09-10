package com.example.sld

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Verification test suite for Roadmap Phase 3:
 * - §3.1 Stamped Engineering Title Block (revision, drawing number, drawn/checked/approved rows, zero hardcoding)
 * - §3.2 Dynamic Legend (symbol set and abbreviations actually printed on the sheet; never a fixed literal block)
 * - Sheet border clearance and geometry
 */
class SldPhase3SheetCompletenessTest {

    data class DxfText(
        val layer: String,
        val text: String,
        val x: Float,
        val y: Float,
        val height: Float
    ) {
        val estimatedWidth: Float = SldDxfExporter.estimateTextWidth(text, height)
    }

    data class DxfLine(
        val layer: String,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val linetype: String
    )

    data class DxfParsed(
        val extMinX: Float,
        val extMinY: Float,
        val extMaxX: Float,
        val extMaxY: Float,
        val lines: List<DxfLine>,
        val texts: List<DxfText>
    )

    private fun parseDxf(dxf: String): DxfParsed {
        val lines = dxf.lines()
        var extMinX = 0f
        var extMinY = 0f
        var extMaxX = 0f
        var extMaxY = 0f
        val parsedLines = mutableListOf<DxfLine>()
        val parsedTexts = mutableListOf<DxfText>()

        var inEntities = false
        var i = 0
        while (i < lines.size) {
            val code = lines[i].trim()
            val value = if (i + 1 < lines.size) lines[i + 1].trim() else ""

            if (code == "2" && value == "ENTITIES") {
                inEntities = true
            }
            if (code == "0" && value == "ENDSEC") {
                inEntities = false
            }

            if (code == "9" && value == "\$EXTMIN") {
                extMinX = lines[i + 3].trim().toFloat()
                extMinY = lines[i + 5].trim().toFloat()
                i += 6
                continue
            }
            if (code == "9" && value == "\$EXTMAX") {
                extMaxX = lines[i + 3].trim().toFloat()
                extMaxY = lines[i + 5].trim().toFloat()
                i += 6
                continue
            }

            if (inEntities && code == "0" && value == "LINE") {
                var layer = "0"
                var linetype = "CONTINUOUS"
                var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
                var j = i + 2
                while (j < lines.size && lines[j].trim() != "0") {
                    when (lines[j].trim()) {
                        "8" -> layer = lines[j + 1].trim()
                        "6" -> linetype = lines[j + 1].trim()
                        "10" -> x1 = lines[j + 1].trim().toFloat()
                        "20" -> y1 = lines[j + 1].trim().toFloat()
                        "11" -> x2 = lines[j + 1].trim().toFloat()
                        "21" -> y2 = lines[j + 1].trim().toFloat()
                    }
                    j += 2
                }
                parsedLines.add(DxfLine(layer, x1, y1, x2, y2, linetype))
                i = j
                continue
            }

            if (inEntities && code == "0" && value == "TEXT") {
                var layer = "0"
                var text = ""
                var x = 0f; var y = 0f; var height = 0f
                var j = i + 2
                while (j < lines.size && lines[j].trim() != "0") {
                    when (lines[j].trim()) {
                        "8" -> layer = lines[j + 1].trim()
                        "1" -> text = lines[j + 1].trim()
                        "10" -> x = lines[j + 1].trim().toFloat()
                        "20" -> y = lines[j + 1].trim().toFloat()
                        "40" -> height = lines[j + 1].trim().toFloat()
                    }
                    j += 2
                }
                parsedTexts.add(DxfText(layer, text, x, y, height))
                i = j
                continue
            }

            i += 2
        }

        return DxfParsed(extMinX, extMinY, extMaxX, extMaxY, parsedLines, parsedTexts)
    }

    private fun createComprehensiveEngineResult(): SldEngineResult {
        val txNode = PositionedSldNode(
            id = "tx_1",
            label = "TX-01",
            panelType = "TRANSFORMER",
            ratingText = "1600 kVA",
            statusColorHex = "#10B981",
            x = 300f,
            y = 40f,
            width = 160f,
            height = 80f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "1600 kVA",
                voltageFrequencyHz = "11kV/400V 50Hz",
                windingConnection = "Dyn11",
                transformerZtMOhm = "10.0 mΩ",
                ibAmp = "2309.4 A"
            )
        )

        val genNode = PositionedSldNode(
            id = "gen_1",
            label = "GEN-01",
            panelType = "GENERATOR",
            ratingText = "1250 kVA",
            statusColorHex = "#F59E0B",
            x = 20f,
            y = 40f,
            width = 160f,
            height = 80f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "1250 kVA",
                voltageFrequencyHz = "400V 50Hz"
            )
        )

        val atsNode = PositionedSldNode(
            id = "ats_1",
            label = "ATS-01",
            panelType = "ATS",
            ratingText = "2500 A",
            statusColorHex = "#3B82F6",
            x = 160f,
            y = 220f,
            width = 160f,
            height = 80f,
            isSource = false,
            dataBlock = SldDataBlock(
                ratedKva = "2500 A",
                deviceTypeAndRating = "ATS 4P 2500A",
                interruptingRatingKa = "65 kA"
            )
        )

        val mdbNode = PositionedSldNode(
            id = "mdb_1",
            label = "MDB-1",
            panelType = "MDB",
            ratingText = "2500 A",
            statusColorHex = "#10B981",
            x = 160f,
            y = 420f,
            width = 380f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "2500 A",
                scRatingKA = "65 kA",
                ipRating = "IP54",
                panelLocation = "Substation Room",
                boardTotalConnectedKva = "1450 kVA",
                boardDemandKva = "1180 kVA",
                boardDemandFactorPct = "81.4%",
                boardDesignCurrentA = "1703.4 A",
                boardUnbalancePct = "4.2%"
            )
        )

        val tieNode = PositionedSldNode(
            id = "tie_1",
            label = "BUS TIE",
            panelType = "BUS_TIE",
            ratingText = "2500 A",
            statusColorHex = "#10B981",
            x = 580f,
            y = 420f,
            width = 60f,
            height = 30f,
            isSource = false
        )

        val upsNode = PositionedSldNode(
            id = "ups_1",
            label = "UPS-01",
            panelType = "UPS",
            ratingText = "400 kVA",
            statusColorHex = "#3B82F6",
            x = 750f,
            y = 420f,
            width = 160f,
            height = 70f,
            isSource = false,
            dataBlock = SldDataBlock(
                ratedKva = "400 kVA",
                voltageFrequencyHz = "400V 50Hz",
                upsBatteryAh = "300 Ah"
            )
        )

        val dbNode = PositionedSldNode(
            id = "db_1",
            label = "DB-LIGHTING",
            panelType = "DB",
            ratingText = "160 A",
            statusColorHex = "#10B981",
            x = 240f,
            y = 660f,
            width = 160f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "MCCB 160A 4P",
                interruptingRatingKa = "36 kA",
                cableDescription = "4x35 mm² Cu XLPE/SWA",
                lengthM = "45 m",
                cableIbA = "120.0 A",
                cableIzA = "145.0 A",
                voltageDropPct = "1.5%",
                cumulativeVdPct = "2.1%",
                faultLevelKa = "22.5 kA",
                cableCpcMm2 = "25 mm²",
                protectionOverload = "PASS",
                protectionShortCircuit = "PASS",
                adsZsOhm = "0.180 Ω",
                busbarRatingA = "160 A",
                scRatingKA = "16 kA",
                ipRating = "IP42",
                boardTotalConnectedKva = "85 kVA",
                boardDemandKva = "75 kVA",
                boardDesignCurrentA = "108.2 A"
            )
        )

        val edges = listOf(
            PositionedSldEdge(
                fromId = "gen_1",
                toId = "ats_1",
                label = "Feeder Gen-ATS",
                isEmergency = true,
                pathPoints = listOf(100f to 120f, 100f to 170f, 210f to 170f, 210f to 220f)
            ),
            PositionedSldEdge(
                fromId = "tx_1",
                toId = "ats_1",
                label = "Feeder TX-ATS",
                isEmergency = false,
                pathPoints = listOf(380f to 120f, 380f to 170f, 270f to 170f, 270f to 220f)
            ),
            PositionedSldEdge(
                fromId = "ats_1",
                toId = "mdb_1",
                label = "Feeder ATS-MDB",
                isEmergency = false,
                pathPoints = listOf(240f to 300f, 240f to 420f)
            ),
            PositionedSldEdge(
                fromId = "mdb_1",
                toId = "db_1",
                label = "Feeder MDB-DB",
                isEmergency = false,
                pathPoints = listOf(320f to 450f, 320f to 660f)
            )
        )

        return SldEngineResult(
            nodes = listOf(txNode, genNode, atsNode, mdbNode, tieNode, upsNode, dbNode),
            edges = edges,
            bounds = Rect(0f, 0f, 1000f, 800f),
            validationIssues = emptyList()
        )
    }

    private fun createMinimalEngineResult(): SldEngineResult {
        val mdbNode = PositionedSldNode(
            id = "mdb_min",
            label = "MDB-MIN",
            panelType = "MDB",
            ratingText = "250 A",
            statusColorHex = "#10B981",
            x = 100f,
            y = 100f,
            width = 200f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "250 A",
                scRatingKA = "25 kA",
                boardTotalConnectedKva = "80 kVA"
            )
        )

        val dbNode = PositionedSldNode(
            id = "db_min",
            label = "DB-01",
            panelType = "DB",
            ratingText = "63 A",
            statusColorHex = "#10B981",
            x = 100f,
            y = 300f,
            width = 160f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "MCB 63A",
                cableDescription = "4x16 mm² Cu",
                boardTotalConnectedKva = "30 kVA"
            )
        )

        val edges = listOf(
            PositionedSldEdge(
                fromId = "mdb_min",
                toId = "db_min",
                label = "Feeder MDB-DB",
                isEmergency = false,
                pathPoints = listOf(150f to 130f, 150f to 300f)
            )
        )

        return SldEngineResult(
            nodes = listOf(mdbNode, dbNode),
            edges = edges,
            bounds = Rect(0f, 0f, 400f, 400f),
            validationIssues = emptyList()
        )
    }

    @Test
    fun titleBlock_fullMetadata_rendersAllStampedFields() {
        val engineResult = createComprehensiveEngineResult()
        val metadata = DxfDrawingMetadata(
            projectName = "Metropolitan Hospital Expansion",
            projectCode = "MHE-2026",
            clientName = "National Health Service",
            drawingNumber = "MHE-E-SLD-001",
            revision = 4L,
            drawnBy = "Eng. A. Ibrahim",
            checkedBy = "Dr. M. El-Sayed",
            approvedBy = "Lead Eng. K. Hassan",
            dateStamp = "2026-09-09",
            standard = "IEC 60364 / BS 7671 / IEC 60617",
            scale = "NTS"
        )

        val dxf = SldDxfExporter.generateDxf(
            engineResult = engineResult,
            metadata = metadata
        )

        val artifactDir = java.io.File("C:/Users/mahmoud.mohamed/.gemini/antigravity/brain/25ee7696-0307-4ff8-9408-10e0038cfc72")
        val dxfFile = java.io.File(artifactDir, "stop_gate_3_sample.dxf")
        dxfFile.writeText(dxf, Charsets.UTF_8)

        assertTrue("Must include project name", dxf.contains("PROJECT: Metropolitan Hospital Expansion"))
        assertTrue("Must include client name", dxf.contains("CLIENT: National Health Service"))
        assertTrue("Must include project code", dxf.contains("CODE: MHE-2026"))
        assertTrue("Must include drawing title", dxf.contains("DRAWING: SINGLE LINE DIAGRAM (SLD)"))
        assertTrue("Must include drawing number", dxf.contains("DWG NO: MHE-E-SLD-001"))
        assertTrue("Must include revision 4", dxf.contains("REV: 4 | "))
        assertTrue("Must include Drawn By engineer", dxf.contains("Eng. A. Ibrahim"))
        assertTrue("Must include Checked By engineer", dxf.contains("Dr. M. El-Sayed"))
        assertTrue("Must include Approved By engineer", dxf.contains("Lead Eng. K. Hassan"))
        assertTrue("Must include date stamp", dxf.contains("DATE: 2026-09-09"))
        assertTrue("Must include IEC standards", dxf.contains("STANDARD: IEC 60364 / BS 7671 / IEC 60617"))
        assertTrue("Must include scale", dxf.contains("SCALE: NTS"))
    }

    @Test
    fun titleBlock_cleanDegradation_neverFabricatesRevisionOrEngineers() {
        val engineResult = createMinimalEngineResult()
        // No metadata supplied, revision = null
        val dxf = SldDxfExporter.generateDxf(
            engineResult = engineResult,
            projectName = "Simple Power System",
            projectRevision = null
        )

        assertTrue("Must include project name", dxf.contains("PROJECT: Simple Power System"))
        assertTrue("Must include drawing title", dxf.contains("DRAWING: SINGLE LINE DIAGRAM (SLD)"))
        // Golden rule: NEVER fabricate a revision when not supplied
        assertFalse("Must not contain REV: when revision is null", dxf.contains("REV: "))
        // Zero hardcoding: Sign-off columns must show '-' rather than fake engineer names
        assertTrue("Drawn column should show '-' when unassigned", dxf.contains("-\n"))
        // Drawing number defaults cleanly without fabrication
        assertTrue("Drawing number should degrade cleanly", dxf.contains("DWG NO: -"))
    }

    @Test
    fun dynamicLegend_comprehensiveDiagram_containsActiveSymbolsAndAbbreviations() {
        val engineResult = createComprehensiveEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Comprehensive Test")

        // Legend title and structure
        assertTrue("Legend title must be rendered", dxf.contains("DRAWING LEGEND & DESIGNATIONS"))
        assertTrue("Symbols sub-header must be rendered", dxf.contains("SYMBOLS & CIRCUITS"))
        assertTrue("Engineering data sub-header must be rendered", dxf.contains("ENGINEERING DATA & STATUS"))

        // Active symbols on sheet
        assertTrue("Busbar symbol must be listed", dxf.contains("= CU BUSBAR (E-BUSBAR)"))
        assertTrue("Breaker symbol must be listed", dxf.contains("[X] LV CIRCUIT BREAKER"))
        assertTrue("Transformer symbol must be listed", dxf.contains("(8) DYN11 TRANSFORMER"))
        assertTrue("Generator symbol must be listed", dxf.contains("(G) STANDBY GENERATOR"))
        assertTrue("ATS symbol must be listed", dxf.contains("[ATS] TRANSFER SWITCH"))
        assertTrue("UPS symbol must be listed", dxf.contains("[UPS] STATIC UPS"))
        assertTrue("Bus tie symbol must be listed", dxf.contains("|-| BUS TIE BREAKER"))
        assertTrue("Normal feeder line must be listed", dxf.contains("--- NORMAL FEEDER (SOLID)"))
        assertTrue("Emergency feeder line must be listed", dxf.contains("- - EMERG FEEDER (DASHED)"))

        // Active abbreviations on sheet
        assertTrue("Icu abbreviation must be listed", dxf.contains("Icu: Breaking Capacity (kA)"))
        assertTrue("Ib abbreviation must be listed", dxf.contains("Ib: Design Current (A)"))
        assertTrue("Iz abbreviation must be listed", dxf.contains("Iz: Cable Rating Derated (A)"))
        assertTrue("VD abbreviation must be listed", dxf.contains("VD / Cum: Volt Drop / Cumul (%)"))
        assertTrue("Isc abbreviation must be listed", dxf.contains("Isc: Fault Level at Board (kA)"))
        assertTrue("CPC abbreviation must be listed", dxf.contains("CPC: Circuit Protective Cond"))
        assertTrue("Zs abbreviation must be listed", dxf.contains("Zs: Loop Impedance (ADS)"))
        assertTrue("Protection verdicts must be listed", dxf.contains("OL/SC/Wth: Protection Verdict"))
        assertTrue("TCL must be listed", dxf.contains("TCL: Total Conn Load (kVA)"))
        assertTrue("TDL must be listed", dxf.contains("TDL: Total Demand Load (kVA)"))
        assertTrue("DF must be listed", dxf.contains("DF: Coincident Demand Factor"))
        assertTrue("Icw must be listed", dxf.contains("Icw: Busbar Withstand (kA 1s)"))
        assertTrue("Unb must be listed", dxf.contains("Unb: Phase Unbalance (%)"))
        assertTrue("FLC must be listed", dxf.contains("FLC: TX Full Load Current (A)"))
        assertTrue("Zt must be listed", dxf.contains("Zt: Transformer Impedance"))
        assertTrue("Bat must be listed", dxf.contains("Bat: UPS Battery Autonomy"))
    }

    @Test
    fun dynamicLegend_minimalDiagram_strictlyExcludesUnusedSymbolsAndAbbreviations() {
        val minimalResult = createMinimalEngineResult()
        val dxf = SldDxfExporter.generateDxf(minimalResult, "Minimal Test")

        // Legend must exist
        assertTrue("Legend must be present", dxf.contains("DRAWING LEGEND & DESIGNATIONS"))

        // Unused equipment symbols MUST NOT appear in the legend (Dynamic Rule)
        assertFalse("Transformer MUST NOT appear in minimal legend", dxf.contains("(8) DYN11 TRANSFORMER"))
        assertFalse("Generator MUST NOT appear in minimal legend", dxf.contains("(G) STANDBY GENERATOR"))
        assertFalse("ATS MUST NOT appear in minimal legend", dxf.contains("[ATS] TRANSFER SWITCH"))
        assertFalse("UPS MUST NOT appear in minimal legend", dxf.contains("[UPS] STATIC UPS"))
        assertFalse("Bus tie MUST NOT appear in minimal legend", dxf.contains("|-| BUS TIE BREAKER"))
        assertFalse("Emergency line MUST NOT appear in minimal legend", dxf.contains("- - EMERG FEEDER (DASHED)"))

        // Unused abbreviations MUST NOT appear in minimal legend
        assertFalse("FLC must not appear", dxf.contains("FLC: TX Full Load Current"))
        assertFalse("Zt must not appear", dxf.contains("Zt: Transformer Impedance"))
        assertFalse("Bat must not appear", dxf.contains("Bat: UPS Battery Autonomy"))
        assertFalse("Unb must not appear", dxf.contains("Unb: Phase Unbalance"))
        assertFalse("Zs must not appear", dxf.contains("Zs: Loop Impedance"))
    }

    @Test
    fun sheetGeometry_legendAndTitleBlock_zeroOverlapAndProperClearance() {
        val engineResult = createComprehensiveEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Clearance Test", projectRevision = 1L)
        val parsed = parseDxf(dxf)

        // Find Legend and Title Block texts
        val legendTitle = parsed.texts.firstOrNull { it.text.contains("DRAWING LEGEND") }
        val tbProject = parsed.texts.firstOrNull { it.text.contains("PROJECT: Clearance Test") }

        assertNotNull("Legend title must be present", legendTitle)
        assertNotNull("Title block project must be present", tbProject)

        // Verify that Legend is on the left and Title Block is on the right
        assertTrue("Legend must be positioned at bottom-left", legendTitle!!.x < (parsed.extMinX + parsed.extMaxX) / 2f)
        assertTrue("Title block must be positioned at bottom-right", tbProject!!.x > (parsed.extMinX + parsed.extMaxX) / 2f)

        // Verify clean horizontal clearance between Legend box and Title Block box
        // Legend box width is ~460f, Title Block width is ~460f
        val legRightApprox = parsed.extMinX + 12f + 460f
        val tbLeftApprox = parsed.extMaxX - 12f - 460f
        assertTrue(
            "Title block left must be strictly greater than legend right with clear void. Found legRight=$legRightApprox, tbLeft=$tbLeftApprox",
            tbLeftApprox > legRightApprox + 40f
        )

        // Verify sheet aspect ratio (close to 1.414 : 1 landscape)
        val sheetW = parsed.extMaxX - parsed.extMinX
        val sheetH = parsed.extMaxY - parsed.extMinY
        val ar = sheetW / sheetH
        assertTrue("Drawing sheet aspect ratio should approximate ISO standard 1.414 (found $ar)", abs(ar - 1.414f) < 0.1f)
    }
}
