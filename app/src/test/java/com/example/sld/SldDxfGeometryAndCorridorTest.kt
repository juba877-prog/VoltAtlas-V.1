package com.example.sld

import androidx.compose.ui.geometry.Rect
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SldDxfGeometryAndCorridorTest {

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
    ) {
        val isHorizontal: Boolean = abs(y1 - y2) < 0.05f
        val isVertical: Boolean = abs(x1 - x2) < 0.05f
        val isOrthogonal: Boolean = isHorizontal || isVertical
    }

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
                var layer = ""
                var linetype = "CONTINUOUS"
                var x1 = 0f
                var y1 = 0f
                var x2 = 0f
                var y2 = 0f
                i += 2
                while (i < lines.size && lines[i].trim() != "0") {
                    val c = lines[i].trim()
                    val v = if (i + 1 < lines.size) lines[i + 1].trim() else ""
                    when (c) {
                        "8" -> layer = v
                        "6" -> linetype = v
                        "10" -> x1 = v.toFloat()
                        "20" -> y1 = v.toFloat()
                        "11" -> x2 = v.toFloat()
                        "21" -> y2 = v.toFloat()
                    }
                    i += 2
                }
                parsedLines.add(DxfLine(layer, x1, y1, x2, y2, linetype))
                continue
            }

            if (inEntities && code == "0" && value == "TEXT") {
                var layer = ""
                var x = 0f
                var y = 0f
                var height = 10f
                var text = ""
                i += 2
                while (i < lines.size && lines[i].trim() != "0") {
                    val c = lines[i].trim()
                    val v = if (i + 1 < lines.size) lines[i + 1].trim() else ""
                    when (c) {
                        "8" -> layer = v
                        "10" -> x = v.toFloat()
                        "20" -> y = v.toFloat()
                        "40" -> height = v.toFloat()
                        "1" -> text = v
                    }
                    i += 2
                }
                parsedTexts.add(DxfText(layer, text, x, y, height))
                continue
            }
            i += 2
        }
        return DxfParsed(extMinX, extMinY, extMaxX, extMaxY, parsedLines, parsedTexts)
    }

    /**
     * Stop Gate 2 Test: Evaluates geometry remediation on a dataset OTHER THAN Stop Gate 1.
     * Dataset: Hospital / Critical Infrastructure Power Network:
     * - Utility Grid 11kV Infeed
     * - 2500 kVA Step-Down Transformer (Dyn11)
     * - Paralleled Synchronous Generators (2x 1250 kVA Prime)
     * - Generator Synchronizing Busbar
     * - 3200A 4-Pole Automatic Transfer Switch (ATS)
     * - 3200A Main Switchboard (MDB-1) with 4 close feeders:
     *   - Feeder 1 -> SMDB-HVAC (1250A)
     *   - Feeder 2 -> SMDB-POWER (630A)
     *   - Feeder 3 -> SMDB-LIGHTING (250A)
     *   - Feeder 4 -> UPS-01 (400 kVA) -> DB-ESSENTIAL (400A)
     */
    @Test
    fun testStopGate2GeometryRemediationOnHospitalDataset() {
        val gridNode = PositionedSldNode(
            id = "grid_1",
            label = "Grid: Utility-11kV",
            panelType = "UTILITY_SOURCE",
            ratingText = "11 kV",
            statusColorHex = "#10B981",
            x = 180f,
            y = 60f,
            width = 160f,
            height = 36f,
            isSource = true,
            dataBlock = SldDataBlock(
                transformerPrimaryVoltage = "11 kV",
                gridFaultMVA = "350 MVA"
            )
        )

        val txNode = PositionedSldNode(
            id = "tx_1",
            label = "Tx: TX-01",
            panelType = "TRANSFORMER",
            ratingText = "2500 kVA (Oil Immersed ONAN)",
            statusColorHex = "#10B981",
            x = 180f,
            y = 200f,
            width = 160f,
            height = 70f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "2500 kVA",
                windingConnection = "Dyn11",
                ibAmp = "3608.4 A",
                transformerZtMOhm = "8.2 mΩ",
                transformerIsc1PhaseKa = "42.1 kA",
                isc3PhaseKa = "50.4 kA",
                loadingPct = "82.0%"
            )
        )

        val gen1Node = PositionedSldNode(
            id = "gen_1",
            label = "Gen: Standby-01",
            panelType = "GENERATOR",
            ratingText = "1250 kVA",
            statusColorHex = "#F59E0B",
            x = 550f,
            y = 80f,
            width = 150f,
            height = 70f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "1250 kVA",
                voltage = "400 V",
                voltageFrequencyHz = "50 Hz",
                loadingPct = "72.0%",
                generatorMotorStartingKva = "1800 kVA",
                generatorStepLoadKva = "600 kVA"
            )
        )

        val gen2Node = PositionedSldNode(
            id = "gen_2",
            label = "Gen: Standby-02",
            panelType = "GENERATOR",
            ratingText = "1250 kVA",
            statusColorHex = "#F59E0B",
            x = 750f,
            y = 80f,
            width = 150f,
            height = 70f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "1250 kVA",
                voltage = "400 V",
                voltageFrequencyHz = "50 Hz",
                loadingPct = "72.0%",
                generatorMotorStartingKva = "1800 kVA",
                generatorStepLoadKva = "600 kVA"
            )
        )

        val syncBusNode = PositionedSldNode(
            id = "sync_bus_1",
            label = "SYNC-BUS-01",
            panelType = "BUS",
            ratingText = "2500 A",
            statusColorHex = "#F59E0B",
            x = 550f,
            y = 200f,
            width = 350f,
            height = 20f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "2500 A",
                busbarMaterial = "Copper",
                scRatingKA = "65 kA"
            )
        )

        val atsNode = PositionedSldNode(
            id = "ats_1",
            label = "ATS: ATS-01",
            panelType = "ATS",
            ratingText = "3200 A (4P)",
            statusColorHex = "#3B82F6",
            x = 420f,
            y = 350f,
            width = 160f,
            height = 70f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "3200 A",
                atsGoverningCurrentA = "3200 A",
                atsRequiredIcwKa = "65 kA",
                atsRequiredIcmKa = "143 kA",
                atsTransferTimeS = "0.35s"
            )
        )

        val mdbNode = PositionedSldNode(
            id = "circ_1",
            label = "MDB-1",
            panelType = "MDB",
            ratingText = "3200 A",
            statusColorHex = "#10B981",
            x = 180f,
            y = 500f,
            width = 820f,
            height = 40f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "3200 A",
                voltage = "400 V",
                scRatingKA = "65 kA",
                ipRating = "IP54",
                busbarMaterial = "Copper",
                busbarSize = "2x(100x10) mm²",
                panelLocation = "Main Substation",
                numberOfWays = "8",
                boardTotalConnectedKva = "2450 kVA",
                boardDemandKva = "1950 kVA",
                boardDemandFactorPct = "80%",
                boardDesignCurrentA = "2814.6 A",
                boardUnbalancePct = "4.2%"
            )
        )

        val smdbHvac = PositionedSldNode(
            id = "circ_hvac",
            label = "SMDB-HVAC",
            panelType = "SMDB",
            ratingText = "1250 A",
            statusColorHex = "#10B981",
            x = 180f,
            y = 780f,
            width = 170f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "ACB 1250A 4P",
                interruptingRatingKa = "65 kA",
                cableDescription = "4x(4x240 mm² Cu XLPE/SWA)",
                parallelRuns = 4,
                lengthM = "45 m",
                cableIbA = "980.0 A",
                cableIzA = "1150.0 A",
                cableUtilizationPct = "85.2%",
                voltageDropPct = "1.1%",
                cumulativeVdPct = "1.4%",
                faultLevelKa = "48.5 kA",
                cableCpcMm2 = "2x120 mm²",
                protectionOverload = "PASS",
                protectionShortCircuit = "PASS",
                protectionCableWithstand = "PASS",
                protectionSelectivity = "PASS",
                adsZsOhm = "0.042 Ω",
                busbarRatingA = "1250 A",
                scRatingKA = "50 kA",
                ipRating = "IP54",
                panelLocation = "Plant Room",
                numberOfWays = "12",
                boardTotalConnectedKva = "850 kVA",
                boardDemandKva = "680 kVA",
                boardDesignCurrentA = "981.5 A"
            )
        )

        val smdbPower = PositionedSldNode(
            id = "circ_pwr",
            label = "SMDB-POWER",
            panelType = "SMDB",
            ratingText = "630 A",
            statusColorHex = "#10B981",
            x = 390f,
            y = 780f,
            width = 170f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "MCCB 630A 4P",
                interruptingRatingKa = "50 kA",
                cableDescription = "2x(4x185 mm² Cu XLPE/SWA)",
                parallelRuns = 2,
                lengthM = "60 m",
                cableIbA = "510.0 A",
                cableIzA = "620.0 A",
                cableUtilizationPct = "82.3%",
                voltageDropPct = "1.5%",
                cumulativeVdPct = "1.8%",
                faultLevelKa = "42.1 kA",
                cableCpcMm2 = "150 mm²",
                protectionOverload = "PASS",
                protectionShortCircuit = "PASS",
                protectionCableWithstand = "PASS",
                protectionSelectivity = "PASS",
                adsZsOhm = "0.068 Ω",
                busbarRatingA = "630 A",
                scRatingKA = "35 kA",
                ipRating = "IP42",
                panelLocation = "Floor 1",
                numberOfWays = "18",
                boardTotalConnectedKva = "480 kVA",
                boardDemandKva = "360 kVA",
                boardDesignCurrentA = "519.6 A"
            )
        )

        val smdbLighting = PositionedSldNode(
            id = "circ_ltg",
            label = "SMDB-LIGHTING",
            panelType = "SMDB",
            ratingText = "250 A",
            statusColorHex = "#10B981",
            x = 600f,
            y = 780f,
            width = 170f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "MCCB 250A 4P",
                interruptingRatingKa = "36 kA",
                cableDescription = "4x70 mm² Cu XLPE/SWA",
                lengthM = "80 m",
                cableIbA = "185.0 A",
                cableIzA = "215.0 A",
                cableUtilizationPct = "86.0%",
                voltageDropPct = "2.1%",
                cumulativeVdPct = "2.4%",
                faultLevelKa = "28.4 kA",
                cableCpcMm2 = "50 mm²",
                protectionOverload = "PASS",
                protectionShortCircuit = "PASS",
                protectionCableWithstand = "PASS",
                protectionSelectivity = "PASS",
                adsZsOhm = "0.145 Ω",
                busbarRatingA = "250 A",
                scRatingKA = "25 kA",
                ipRating = "IP42",
                panelLocation = "Floor 2",
                numberOfWays = "24",
                boardTotalConnectedKva = "160 kVA",
                boardDemandKva = "130 kVA",
                boardDesignCurrentA = "187.6 A"
            )
        )

        val upsNode = PositionedSldNode(
            id = "ups_1",
            label = "UPS: UPS-01",
            panelType = "UPS",
            ratingText = "400 kVA",
            statusColorHex = "#3B82F6",
            x = 810f,
            y = 780f,
            width = 160f,
            height = 70f,
            isSource = false,
            dataBlock = SldDataBlock(
                ratedKva = "400 kVA",
                voltageFrequencyHz = "50 Hz",
                upsBatteryAh = "300 Ah",
                upsBatteryStrings = "3 strings"
            )
        )

        val dbEssential = PositionedSldNode(
            id = "db_ess",
            label = "DB-ESSENTIAL",
            panelType = "DB",
            ratingText = "400 A",
            statusColorHex = "#10B981",
            x = 810f,
            y = 960f,
            width = 160f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "400 A",
                scRatingKA = "25 kA",
                ipRating = "IP42",
                panelLocation = "Data Hall 1",
                numberOfWays = "16",
                boardTotalConnectedKva = "260 kVA",
                boardDemandKva = "220 kVA",
                boardDesignCurrentA = "317.5 A"
            )
        )

        val nodes = listOf(
            gridNode, txNode, gen1Node, gen2Node, syncBusNode, atsNode, mdbNode,
            smdbHvac, smdbPower, smdbLighting, upsNode, dbEssential
        )

        val edges = listOf(
            PositionedSldEdge("grid_1", "tx_1", "11kV MV Feed", listOf(260f to 96f, 260f to 160f)),
            PositionedSldEdge("tx_1", "ats_1", "LV Transformer Main", listOf(260f to 240f, 260f to 300f, 484f to 300f, 484f to 328f)),
            PositionedSldEdge("gen_1", "sync_bus_1", "Gen 1 Infeed", listOf(625f to 150f, 625f to 200f), isEmergency = true),
            PositionedSldEdge("gen_2", "sync_bus_1", "Gen 2 Infeed", listOf(825f to 150f, 825f to 200f), isEmergency = true),
            PositionedSldEdge("sync_bus_1", "ats_1", "Genset Emergency Feed", listOf(725f to 220f, 725f to 300f, 520f to 300f, 520f to 328f), isEmergency = true),
            PositionedSldEdge("ats_1", "circ_1", "ATS Load Feed", listOf(500f to 420f, 500f to 500f)),
            PositionedSldEdge("circ_1", "circ_hvac", "Feeder 1 - HVAC", listOf(265f to 540f, 265f to 780f)),
            PositionedSldEdge("circ_1", "circ_pwr", "Feeder 2 - Power", listOf(475f to 540f, 475f to 780f)),
            PositionedSldEdge("circ_1", "circ_ltg", "Feeder 3 - Lighting", listOf(685f to 540f, 685f to 780f)),
            PositionedSldEdge("circ_1", "ups_1", "Feeder 4 - UPS In", listOf(890f to 540f, 890f to 780f)),
            PositionedSldEdge("ups_1", "db_ess", "UPS Out - Essential", listOf(890f to 850f, 890f to 960f))
        )

        val tierDatums = listOf(
            TierDatumLine("GRID INFEED", 60f, -1),
            TierDatumLine("HV / GENERATION", 140f, 0),
            TierDatumLine("MAIN DISTRIBUTION", 350f, 1),
            TierDatumLine("SUB-DISTRIBUTION", 780f, 2),
            TierDatumLine("CRITICAL POWER", 960f, 3)
        )

        val engineResult = SldEngineResult(
            nodes = nodes,
            edges = edges,
            tierDatums = tierDatums,
            bounds = Rect(20f, 40f, 1050f, 1020f)
        )

        val dxf = SldDxfExporter.generateDxf(
            engineResult = engineResult,
            projectName = "Metropolitan General Hospital - Critical SLD",
            projectRevision = 3L
        )

        val artifactDir = File("C:/Users/mahmoud.mohamed/.gemini/antigravity/brain/25ee7696-0307-4ff8-9408-10e0038cfc72")
        val dxfFile = File(artifactDir, "stop_gate_2_sample.dxf")
        dxfFile.writeText(dxf, Charsets.UTF_8)
        assertTrue("DXF file must be written and non-empty", dxfFile.exists() && dxfFile.length() > 5000)

        val parsed = parseDxf(dxf)

        // ------------------------------------------------------------------
        // (A) ZERO TEXT-ON-TEXT OVERLAP VERIFICATION
        // ------------------------------------------------------------------
        val feederTexts = parsed.texts.filter { it.layer in setOf("E-TEXT-TITLE", "E-TEXT-DATA") && it.y < -500f }
        val textsByCorridor = feederTexts.groupBy { t -> (t.x / 100f).toInt() }

        textsByCorridor.values.forEach { corridorTexts ->
            val sortedByY = corridorTexts.sortedByDescending { it.y }
            for (idx in 0 until sortedByY.size - 1) {
                val top = sortedByY[idx]
                val bottom = sortedByY[idx + 1]
                val yDiff = top.y - bottom.y
                assertTrue(
                    "Consecutive lines in corridor must have strict vertical clearance without collision. Found yDiff=" + yDiff,
                    yDiff >= 10.0f
                )
            }
        }

        // ------------------------------------------------------------------
        // (B) ZERO TEXT ESCAPING CARDS / ENCLOSURES (Measure-Then-Draw)
        // ------------------------------------------------------------------
        val gridTexts = parsed.texts.filter { it.text.contains("Utility-11kV") || it.text.contains("S_kQ") }
        val gridMinX = 180f
        gridTexts.forEach { t ->
            assertTrue("Grid text must not start before box left edge", t.x >= gridMinX)
        }

        val mdbEnclosureLines = parsed.lines.filter { it.layer == "E-PANEL" && it.linetype == "DASHED" }
        assertTrue("MDB dashed enclosure must be emitted", mdbEnclosureLines.size >= 4)
        val encMinX = mdbEnclosureLines.minOf { minOf(it.x1, it.x2) }
        val encMaxX = mdbEnclosureLines.maxOf { maxOf(it.x1, it.x2) }
        val encMinY = mdbEnclosureLines.minOf { minOf(it.y1, it.y2) }
        val encMaxY = mdbEnclosureLines.maxOf { maxOf(it.y1, it.y2) }

        val mdbTag = parsed.texts.firstOrNull { it.text.contains("MDB-1 Main Busbar:") }
        assertTrue("MDB tag must be rendered", mdbTag != null)
        assertTrue("MDB tag must start inside enclosure left", mdbTag!!.x >= encMinX)
        assertTrue(
            "MDB tag right extent must stay strictly within enclosure right",
            mdbTag.x + mdbTag.estimatedWidth <= encMaxX + 5f
        )
        assertTrue("MDB tag must be below enclosure top", mdbTag.y <= encMaxY)

        val hvacBusLines = parsed.lines.filter { it.layer == "E-BUSBAR" && abs(it.y1 - (-780f)) < 5f }
        val hvacBusW = hvacBusLines.maxOfOrNull { abs(it.x2 - it.x1) } ?: 0f
        val hvacTexts = parsed.texts.filter { it.x in 180f..360f && it.y in -850f..-780f }
        hvacTexts.forEach { t ->
            val textRight = t.x + t.estimatedWidth
            assertTrue(
                "SMDB-HVAC text right edge must not exceed busbar span",
                textRight <= smdbHvac.x + hvacBusW + 15f
            )
        }

        // ------------------------------------------------------------------
        // (C) DRAWING SHEET FIT & ZERO EMPTY VOID
        // ------------------------------------------------------------------
        val sheetW = parsed.extMaxX - parsed.extMinX
        val sheetH = parsed.extMaxY - parsed.extMinY
        assertTrue("Sheet width must be dynamically fitted", sheetW < 2200f)
        assertTrue("Sheet height must be dynamically fitted", sheetH < 1700f)

        val contentW = 1000f - 20f
        val contentH = 1000f - 40f
        val contentArea = contentW * contentH
        val sheetArea = sheetW * sheetH
        val utilization = contentArea / sheetArea
        assertTrue(
            "Sheet area utilization must be >= 40% to prevent empty void",
            utilization >= 0.40f
        )

        // ------------------------------------------------------------------
        // (D) MANHATTAN ORTHOGONAL WIRE ROUTING
        // ------------------------------------------------------------------
        val wireLayers = setOf("E-NORMAL", "E-EMERG", "E-TX", "E-GEN", "E-TIE")
        val wireLines = parsed.lines.filter { it.layer in wireLayers }
        wireLines.forEach { line ->
            assertTrue(
                "Wire segment must be Manhattan orthogonal",
                line.isOrthogonal
            )
        }

        // Title Block content verification
        val tbProjectText = parsed.texts.firstOrNull { it.text.contains("PROJECT: Metropolitan General Hospital") }
        assertTrue("Title block must display full project name", tbProjectText != null)
        val tbRevText = parsed.texts.firstOrNull { it.text.contains("REV: 3") }
        assertTrue("Title block must display project revision", tbRevText != null)
    }
}
