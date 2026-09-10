package com.example.sld

import androidx.compose.ui.geometry.Rect
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopGate1ExportVerificationTest {

    @Test
    fun generateStopGate1Dxf() {
        val txNode = PositionedSldNode(
            id = "tx_1",
            label = "Tx: TX-01",
            panelType = "TRANSFORMER",
            ratingText = "1600 kVA (Dry Type (Cast Resin))",
            statusColorHex = "#10B981",
            x = 300f,
            y = 100f,
            width = 140f,
            height = 70f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "1600 kVA",
                windingConnection = "Dyn11",
                ibAmp = "2309.4 A",
                transformerZtMOhm = "14.5 mΩ",
                transformerIsc1PhaseKa = "28.5 kA",
                isc3PhaseKa = "35.2 kA",
                loadingPct = "78.5%"
            )
        )

        val genNode = PositionedSldNode(
            id = "gen_1",
            label = "Gen: Standby-01",
            panelType = "GENERATOR",
            ratingText = "800 kVA",
            statusColorHex = "#F59E0B",
            x = 650f,
            y = 100f,
            width = 140f,
            height = 70f,
            isSource = true,
            dataBlock = SldDataBlock(
                ratedKva = "800 kVA",
                voltage = "400 V",
                voltageFrequencyHz = "50 Hz",
                loadingPct = "65.0%",
                generatorMotorStartingKva = "1200 kVA"
            )
        )

        val atsNode = PositionedSldNode(
            id = "ats_1",
            label = "ATS: ATS-01",
            panelType = "ATS",
            ratingText = "1600 A",
            statusColorHex = "#3B82F6",
            x = 475f,
            y = 260f,
            width = 140f,
            height = 70f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "1600 A",
                atsGoverningCurrentA = "1600 A",
                atsTransferTimeS = "0.5s"
            )
        )

        val mdbNode = PositionedSldNode(
            id = "circ_100",
            label = "MDB-100",
            panelType = "MDB",
            ratingText = "1600 A",
            statusColorHex = "#10B981",
            x = 475f,
            y = 440f,
            width = 400f,
            height = 40f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "1600 A",
                voltage = "400 V",
                scRatingKA = "50 kA",
                ipRating = "IP54",
                busbarMaterial = "Copper",
                busbarSize = "2x(50x10) mm²",
                boardTotalConnectedKva = "1250 kVA",
                boardDemandKva = "980 kVA",
                boardDesignCurrentA = "1414.7 A"
            )
        )

        val incomerNode = PositionedSldNode(
            id = "circ_99",
            label = "INCOMER-1",
            panelType = "ACB",
            ratingText = "1600 A",
            statusColorHex = "#10B981",
            x = 475f,
            y = 360f,
            width = 100f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                deviceTypeAndRating = "ACB 1600A",
                interruptingRatingKa = "65 kA",
                faultLevelKa = "35.2 kA",
                protectionSelectivity = "N/A"
            )
        )

        val feeder1 = PositionedSldNode(
            id = "circ_101",
            label = "SMDB-101",
            panelType = "SMDB",
            ratingText = "400 A",
            statusColorHex = "#10B981",
            x = 350f,
            y = 650f,
            width = 180f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "400 A",
                voltage = "400 V",
                scRatingKA = "36 kA",
                ipRating = "IP54",
                lengthM = "45 m",
                cableIbA = "315.0 A",
                cableIzA = "380.0 A",
                cableUtilizationPct = "82.9%",
                voltageDropPct = "1.45%",
                cumulativeVdPct = "2.15%",
                cableCpcMm2 = "95 mm²",
                adsZsOhm = "0.082 Ω",
                faultLevelKa = "28.4 kA",
                interruptingRatingKa = "36 kA",
                deviceTypeAndRating = "MCCB 400A",
                protectionSelectivity = "PASS"
            )
        )

        val subNodeCleanDegrade = PositionedSldNode(
            id = "circ_102",
            label = "DB-LIGHTING",
            panelType = "DB",
            ratingText = "63 A",
            statusColorHex = "#10B981",
            x = 600f,
            y = 650f,
            width = 160f,
            height = 30f,
            isSource = false,
            dataBlock = SldDataBlock(
                busbarRatingA = "63 A",
                voltage = "400 V",
                scRatingKA = null, // Withstand intentionally absent -> must degrade cleanly
                lengthM = "25 m",
                cableIbA = "42.0 A",
                cableIzA = "55.0 A",
                cableUtilizationPct = "76.4%",
                voltageDropPct = "0.85%",
                cumulativeVdPct = "3.00%",
                cableCpcMm2 = "16 mm²",
                faultLevelKa = "14.2 kA",
                interruptingRatingKa = "25 kA",
                protectionSelectivity = "PASS"
            )
        )

        val edgeTxToAts = PositionedSldEdge(
            fromId = "tx_1",
            toId = "ats_1",
            label = "4x(1x240) Cu/XLPE (15m)",
            pathPoints = listOf(Pair(300f, 135f), Pair(300f, 260f), Pair(475f, 260f))
        )

        val edgeGenToAts = PositionedSldEdge(
            fromId = "gen_1",
            toId = "ats_1",
            label = "2x(1x240) Cu/XLPE (20m)",
            pathPoints = listOf(Pair(650f, 135f), Pair(650f, 260f), Pair(525f, 260f))
        )

        val edgeAtsToIncomer = PositionedSldEdge(
            fromId = "ats_1",
            toId = "circ_99",
            label = "Busduct 1600A (10m)",
            pathPoints = listOf(Pair(475f, 295f), Pair(475f, 360f))
        )

        val edgeIncomerToMdb = PositionedSldEdge(
            fromId = "circ_99",
            toId = "circ_100",
            label = "Busbar Incomer",
            pathPoints = listOf(Pair(475f, 360f), Pair(475f, 440f))
        )

        val edgeMdbToF1 = PositionedSldEdge(
            fromId = "circ_100",
            toId = "circ_101",
            label = "4x(1x185) Cu/XLPE (45m)",
            pathPoints = listOf(Pair(400f, 480f), Pair(350f, 480f), Pair(350f, 650f))
        )

        val edgeMdbToF2 = PositionedSldEdge(
            fromId = "circ_100",
            toId = "circ_102",
            label = "4x16 Cu/XLPE (25m)",
            pathPoints = listOf(Pair(550f, 480f), Pair(600f, 480f), Pair(600f, 650f))
        )

        val engineResult = SldEngineResult(
            nodes = listOf(txNode, genNode, atsNode, mdbNode, incomerNode, feeder1, subNodeCleanDegrade),
            edges = listOf(edgeTxToAts, edgeGenToAts, edgeAtsToIncomer, edgeIncomerToMdb, edgeMdbToF1, edgeMdbToF2),
            bounds = Rect(0f, 0f, 1200f, 900f),
            tierDatums = listOf(
                TierDatumLine("SOURCES", 100f, 0),
                TierDatumLine("TRANSFER", 260f, 1),
                TierDatumLine("MAIN DISTRIBUTION", 440f, 2),
                TierDatumLine("SUB-DISTRIBUTION", 650f, 3)
            )
        )

        val dxf = SldDxfExporter.generateDxf(engineResult, "PROJECT_ALPHA")

        // Save to artifacts directory
        val artifactDir = File("C:/Users/mahmoud.mohamed/.gemini/antigravity/brain/25ee7696-0307-4ff8-9408-10e0038cfc72")
        val outputFile = File(artifactDir, "stop_gate_1_sample.dxf")
        outputFile.writeText(dxf, Charsets.US_ASCII)

        // Strict Stop Gate 1 Assertions
        // 1. Zero doubled units
        assertFalse("No 'mm' for metres", dxf.contains("45 mm") || dxf.contains("25 mm") || dxf.contains("15 mm") || dxf.contains("20 mm"))
        assertFalse("No 'AA' for current", dxf.contains("AA"))
        assertFalse("No 'mm² mm²'", dxf.contains("mm² mm²") || dxf.contains("\\U+00B2 \\U+00B2"))
        assertFalse("No 'ΩΩ'", dxf.contains("ΩΩ") || dxf.contains("\\U+03A9\\U+03A9"))
        assertFalse("No 'kAkA'", dxf.contains("kAkA"))

        // 2. Zero leaked keys
        assertFalse("No circ_100 raw key", dxf.contains("circ_100"))
        assertFalse("No circ_101 raw key", dxf.contains("circ_101"))
        assertFalse("No circ_99 raw key", dxf.contains("circ_99"))
        assertFalse("No tx_1 raw key", dxf.contains("tx_1"))
        assertFalse("No gen_1 raw key", dxf.contains("gen_1"))
        assertFalse("No ats_1 raw key", dxf.contains("ats_1"))

        // 3. Proper display names
        assertTrue("Contains TX-01", dxf.contains("TX-01"))
        assertTrue("Contains Standby-01", dxf.contains("Standby-01"))
        assertTrue("Contains ATS-01", dxf.contains("ATS-01"))
        assertTrue("Contains MDB-100", dxf.contains("MDB-100"))
        assertTrue("Contains SMDB-101", dxf.contains("SMDB-101"))
        assertTrue("Contains DB-LIGHTING", dxf.contains("DB-LIGHTING"))

        // 4. Equipment title block de-duplication
        assertFalse("No nested parens", dxf.contains("(("))
        assertTrue("Has Sel:N/A for transformer incomer", dxf.contains("Sel:N/A") || dxf.contains("Sel: N/A"))

        // 5. Clean degradation for busbar without withstand
        assertFalse("No double comma or empty withstand", dxf.contains(", ,") || dxf.contains(", /1s"))
    }
}
