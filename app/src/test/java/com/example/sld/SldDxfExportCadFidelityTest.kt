package com.example.sld

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 §5.2 — CAD-Grade Multi-Layer DXF Exporter Fidelity Unit Tests.
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

        val smdbNode = PositionedSldNode(
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

        val normalEdge = PositionedSldEdge(
            fromId = "TX-01",
            toId = "MDB-1",
            label = "Incomer",
            pathPoints = listOf(270f to 170f, 270f to 400f),
            isEmergency = false
        )

        val feederEdge = PositionedSldEdge(
            fromId = "MDB-1",
            toId = "SMDB-1",
            label = "Feeder",
            pathPoints = listOf(270f to 424f, 270f to 500f, 270f to 700f),
            isEmergency = false
        )

        val emergencyEdge = PositionedSldEdge(
            fromId = "GEN-01",
            toId = "ATS-01",
            label = "EMERGENCY",
            pathPoints = listOf(800f to 170f, 800f to 400f),
            isEmergency = true
        )

        return SldEngineResult(
            nodes = listOf(txNode, mdbNode, smdbNode),
            edges = listOf(normalEdge, feederEdge, emergencyEdge),
            tierDatums = listOf(
                TierDatumLine(label = "Tier 0 (Sources)", y = 100f, tierIndex = 0),
                TierDatumLine(label = "Tier 1 (Main MV/LV)", y = 400f, tierIndex = 1),
                TierDatumLine(label = "Tier 2 (Sub-Distribution)", y = 700f, tierIndex = 2)
            ),
            bounds = Rect(0f, 0f, 1200f, 900f)
        )
    }

    @Test
    fun dxfHeader_containsValidLtypesAndLayers() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Project Alfa")

        // 1. AC1009 AutoCAD R12 Version & R12-valid codepage declaration
        assertTrue("DXF must declare AutoCAD R12 format AC1009", dxf.contains("9\n\$ACADVER\n1\nAC1009\n"))
        assertTrue(
            "DXF must declare ANSI_1252 codepage (\$INSUNITS is invalid in AC1009)",
            dxf.contains("9\n\$DWGCODEPAGE\n3\nANSI_1252\n")
        )
        assertFalse("AC1009 header must not contain AC1015+ \$INSUNITS variable", dxf.contains("\$INSUNITS"))

        // 2. LTYPE Table definitions enclosed in TABLE / ENDTAB
        assertTrue("DXF must have LTYPE table", dxf.contains("0\nTABLE\n2\nLTYPE\n70\n3\n"))
        assertTrue("DXF must define CONTINUOUS linetype", dxf.contains("2\nCONTINUOUS\n"))
        assertTrue("DXF must define DASHED linetype", dxf.contains("2\nDASHED\n"))

        // 3. LAYER Table definitions enclosed in TABLE / ENDTAB (with mandatory layer 0)
        assertTrue("DXF must have LAYER table with 11 entries incl. layer 0", dxf.contains("0\nTABLE\n2\nLAYER\n70\n11\n"))
        assertTrue("DXF must define MANDATORY layer 0", dxf.contains("2\n0\n"))
        val requiredLayers = listOf(
            "E-TX", "E-GEN", "E-EMERG", "E-NORMAL", "E-TIE",
            "E-PANEL", "E-TEXT-TITLE", "E-TEXT-DATA", "E-SYMBOLS", "E-BORDER"
        )
        requiredLayers.forEach { layer ->
            assertTrue("DXF must define layer $layer in LAYER table", dxf.contains("2\n$layer\n"))
        }

        // 3b. STYLE Table with STANDARD (mandatory for TEXT entities)
        assertTrue("DXF must have STYLE table with 1 entry", dxf.contains("0\nTABLE\n2\nSTYLE\n70\n1\n"))
        assertTrue("DXF must define STANDARD text style", dxf.contains("2\nSTANDARD\n"))

        // 4. BLOCKS Section
        assertTrue("DXF must have clean BLOCKS section", dxf.contains("0\nSECTION\n2\nBLOCKS\n0\nENDSEC\n"))

        // 5. Clean EOF Termination
        assertTrue("DXF must terminate with 0\\nENDSEC\\n0\\nEOF", dxf.trim().endsWith("0\nENDSEC\n0\nEOF"))
    }

    @Test
    fun emergencyWires_haveDashedLinetypeAndDedicatedLayer() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Project Alfa")

        // Assert emergency wire assigned to E-EMERG with DASHED linetype
        assertTrue("Emergency line must be on E-EMERG layer", dxf.contains("8\nE-EMERG\n"))
        assertTrue("Emergency line must declare DASHED linetype group code 6", dxf.contains("6\nDASHED\n"))
    }

    @Test
    fun textBlocks_doNotIntersectWireCoordinates() {
        val engineResult = createSampleEngineResult()
        val dxf = SldDxfExporter.generateDxf(engineResult, "Project Alfa")

        // The feeder wire is at X = 270.0
        // Text should be offset at X = 270.0 + 16.0 = 286.0
        assertTrue(
            "Feeder breaker tag text must be offset to X=286.000",
            dxf.contains("10\n286.000\n")
        )
        assertTrue(
            "Feeder cable spec must be placed cleanly on E-TEXT-DATA",
            dxf.contains("1\n4x(1x185 mm²) Cu/XLPE\n")
        )
    }

    @Test
    fun hopArcs_exportAsDxfArcs() {
        // Create two crossing wires: Horizontal wire (100, 300) to (500, 300), Vertical wire (300, 100) to (300, 500)
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
        val dxf = SldDxfExporter.generateDxf(engineResult, projectName)

        assertTrue("Title block must include project name", dxf.contains("PROJECT: $projectName"))
        assertTrue("Title block must include drawing subtitle", dxf.contains("DRAWING: SINGLE LINE DIAGRAM (SLD)"))
        assertTrue("Title block must include IEC standard", dxf.contains("STANDARD: IEC 60364 / BS 7671"))
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
}
