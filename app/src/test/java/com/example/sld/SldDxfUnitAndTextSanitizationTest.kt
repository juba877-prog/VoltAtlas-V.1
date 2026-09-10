package com.example.sld

import androidx.compose.ui.geometry.Rect
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.engine.ShortCircuitResult
import com.example.engine.ProtectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SldDxfUnitAndTextSanitizationTest {

    @Test
    fun testFormatUnitPreventsDoubledTokens() {
        // 1. Length: critical test — must never turn "m" into "mm"
        assertEquals("50 m", SldDxfExporter.formatUnit("50 m", "m"))
        assertEquals("50 m", SldDxfExporter.formatUnit("50 mm", "m"))
        assertEquals("50 m", SldDxfExporter.formatUnit("50m", "m"))
        assertEquals("50 m", SldDxfExporter.formatUnit("50", "m"))

        // 2. Current: must never produce "AA"
        assertEquals("45.2 A", SldDxfExporter.formatUnit("45.2 A", "A"))
        assertEquals("45.2 A", SldDxfExporter.formatUnit("45.2 AA", "A"))
        assertEquals("45.2 A", SldDxfExporter.formatUnit("45.2A", "A"))
        assertEquals("400A", SldDxfExporter.formatUnit("400 A", "A", space = false))

        // 3. Conductor cross-section: must never duplicate mm²
        assertEquals("16 mm\u00B2", SldDxfExporter.formatUnit("16 mm\u00B2", "mm\u00B2"))
        assertEquals("16 mm\u00B2", SldDxfExporter.formatUnit("16 mm\u00B2 mm\u00B2", "mm\u00B2"))
        assertEquals("16 mm\u00B2", SldDxfExporter.formatUnit("16", "mm\u00B2"))

        // 4. Impedance: must never duplicate Ω
        assertEquals("0.245 \u03A9", SldDxfExporter.formatUnit("0.245 \u03A9", "\u03A9"))
        assertEquals("0.245 \u03A9", SldDxfExporter.formatUnit("0.245 \u03A9\u03A9", "\u03A9"))
        assertEquals("0.245 \u03A9", SldDxfExporter.formatUnit("0.245 Ω", "\u03A9"))

        // 5. Fault current: must never duplicate kA
        assertEquals("36 kA", SldDxfExporter.formatUnit("36 kA", "kA"))
        assertEquals("36 kA", SldDxfExporter.formatUnit("36 kAkA", "kA"))
        assertEquals("36 kA", SldDxfExporter.formatUnit("36", "kA"))

        // 6. Percentage: must never duplicate %
        assertEquals("2.50%", SldDxfExporter.formatUnit("2.50%", "%", space = false))
        assertEquals("2.50%", SldDxfExporter.formatUnit("2.50%%", "%", space = false))
        assertEquals("2.50%", SldDxfExporter.formatUnit("2.50", "%", space = false))

        // 7. Time: seconds
        assertEquals("10s", SldDxfExporter.formatUnit("10s", "s", space = false))
        assertEquals("10s", SldDxfExporter.formatUnit("10ss", "s", space = false))
    }

    @Test
    fun testResolveDisplayNameEliminatesInternalKeys() {
        val nodeWithPrefix = PositionedSldNode(
            id = "tx_1",
            label = "Tx: TX-01",
            panelType = "TRANSFORMER",
            ratingText = "1600 kVA",
            statusColorHex = "#FFFFFF",
            x = 0f,
            y = 0f
        )
        // Strips artificial system type prefix
        assertEquals("TX-01", SldDxfExporter.resolveDisplayName(nodeWithPrefix))

        // When stored circuitName exists, it is read directly
        val circuitEntity = com.example.data.CircuitEntity(
            id = 42,
            circuitName = "MDB-MAIN",
            loadKVA = 50.0
        )
        val nodeWithStoredName = PositionedSldNode(
            id = "circ_42",
            label = "circ_42",
            circuit = circuitEntity,
            panelType = "MDB",
            ratingText = "630A",
            statusColorHex = "#FFFFFF",
            x = 0f,
            y = 0f
        )
        assertEquals("MDB-MAIN", SldDxfExporter.resolveDisplayName(nodeWithStoredName))

        // When no human display name exists, falls back to raw stored identifier as-is (A3.2, zero fabrication)
        val nodeWithRawIdOnly = PositionedSldNode(
            id = "circ_42",
            label = "circ_42",
            panelType = "MDB",
            ratingText = "630A",
            statusColorHex = "#FFFFFF",
            x = 0f,
            y = 0f
        )
        assertEquals("circ_42", SldDxfExporter.resolveDisplayName(nodeWithRawIdOnly))

        // User-entered numeric label is preserved as-is without synthesizing "GEN-1" (A3.2)
        val nodeWithRawNumeric = PositionedSldNode(
            id = "gen_1",
            label = "1",
            panelType = "GENERATOR",
            ratingText = "500 kVA",
            statusColorHex = "#FFFFFF",
            x = 0f,
            y = 0f
        )
        assertEquals("1", SldDxfExporter.resolveDisplayName(nodeWithRawNumeric))
    }

    @Test
    fun testEquipmentTitleBlockAttributeDeDuplication() {
        // Tests that attributes are not duplicated and nested parentheses are eliminated
        val line = SldDxfExporter.buildEquipmentTitleLine(
            "1600 kVA",
            "(Dry Type (Cast Resin))",
            "1600 kVA",
            "Dyn11"
        )
        // Should contain 1600 kVA only once
        val kvaCount = Regex("1600\\s*kVA", RegexOption.IGNORE_CASE).findAll(line).count()
        assertEquals("kVA rating must appear exactly once", 1, kvaCount)

        // Nested parentheses ((...)) must be flattened
        assertFalse("Must not contain nested parentheses", line.contains("((") || line.contains("))"))
        assertTrue("Must contain flattened clean type", line.contains("Dry Type Cast Resin") || line.contains("Dry Type (Cast Resin)"))
        assertTrue("Must contain vector group", line.contains("Dyn11"))
    }

    @Test
    fun testBusbarTagPresentOrOmitCleanly() {
        // Node with short-circuit withstand populated
        val nodeWithIcw = PositionedSldNode(
            id = "circ_1",
            label = "MDB-MAIN",
            panelType = "MDB",
            ratingText = "400A",
            statusColorHex = "#FFFFFF",
            x = 0f,
            y = 0f,
            dataBlock = SldDataBlock(
                busbarRatingA = "400 A",
                voltage = "400 V",
                scRatingKA = "36 kA",
                ipRating = "IP54",
                busbarSize = "1x(40x10) mm²",
                busbarMaterial = "Copper"
            )
        )
        val tagWithIcw = SldDxfExporter.exportToDxfString(
            SldEngineResult(
                nodes = listOf(nodeWithIcw),
                edges = emptyList(),
                bounds = Rect(0f, 0f, 500f, 500f),
                tierDatums = emptyList()
            )
        )
        assertTrue("Must contain 36kA/1s", tagWithIcw.contains("36kA/1s"))
        assertFalse("Must not have double commas", tagWithIcw.contains(", ,"))
        assertFalse("Must not have empty comma before withstand", tagWithIcw.contains(", , /1s"))

        // Node without short-circuit withstand: must omit cleanly
        val nodeWithoutIcw = PositionedSldNode(
            id = "circ_2",
            label = "MDB-NO-ICW",
            panelType = "MDB",
            ratingText = "400A",
            statusColorHex = "#FFFFFF",
            x = 0f,
            y = 0f,
            dataBlock = SldDataBlock(
                busbarRatingA = "400 A",
                voltage = "400 V",
                scRatingKA = null, // Missing withstand
                ipRating = "IP54",
                busbarSize = "1x(40x10) mm²",
                busbarMaterial = "Copper"
            )
        )
        val tagWithoutIcw = SldDxfExporter.exportToDxfString(
            SldEngineResult(
                nodes = listOf(nodeWithoutIcw),
                edges = emptyList(),
                bounds = Rect(0f, 0f, 500f, 500f),
                tierDatums = emptyList()
            )
        )
        assertFalse("Must not contain /1s when Icw is absent", tagWithoutIcw.contains("/1s"))
        assertFalse("Must not have double commas", tagWithoutIcw.contains(", ,"))
    }

    @Test
    fun testSelectivityVerdictReportsNAWhenNoUpstreamProtectiveDevice() {
        val circuit = CircuitEntity(id = 1, circuitName = "MDB-INCOMING", loadKVA = 1000.0)
        val sizing = SizingResult(
            Ib_A = 1443.0,
            deviceSize_A = 1600.0,
            deviceType = "ACB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 1443.0,
            productCode = "CABLE",
            cableSize_mm2 = "240",
            ratedI_table_A = 1600.0,
            capacity_derated_Iz_A = 1800.0,
            utilizationPct = 80.0,
            neutral_mm2 = "120",
            earthCPC_mm2 = "120",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "PASS",
            resultStatus = SizingResultStatus.PASS,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null
        )
        val sc = ShortCircuitResult(
            circuitId = 1,
            sourceName = "TX-01",
            cableMm2 = "240",
            iscKA = 35.0,
            iwKA = 40.0,
            deviceKA = 50.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 20.0,
            status = "PASS",
            note = "OK"
        )

        // When fed directly from transformer with no upstream protective device
        val prot = ProtectionEngine.evaluateProtection(
            circuit = circuit,
            sizingResult = sizing,
            shortCircuitResult = sc,
            upstreamIn_A = 1443.0, // Transformer FLC
            upstreamName = "Incomer (TX-01)",
            hasUpstreamDevice = false
        )

        assertEquals("Selectivity must report N/A when no upstream protective device exists", "N/A", prot.selectivityStatus)
        assertEquals("Final status must remain PASS", "PASS", prot.finalStatus)
    }

    @Test
    fun testExportDxfHasZeroLeakedKeysAndZeroDoubledUnits() {
        val node = PositionedSldNode(
            id = "circ_10",
            label = "circ_10", // Raw DB key in label is substituted by real circuitName
            circuit = com.example.data.CircuitEntity(id = 10, circuitName = "MDB-10"),
            panelType = "MDB",
            ratingText = "400A",
            statusColorHex = "#FFFFFF",
            x = 100f,
            y = 100f,
            dataBlock = SldDataBlock(
                busbarRatingA = "400 A",
                voltage = "400 V",
                lengthM = "50 m", // Already has unit
                cableIbA = "45.2 A", // Already has unit
                cableIzA = "60.0 A", // Already has unit
                cableUtilizationPct = "75%",
                voltageDropPct = "1.25%",
                cumulativeVdPct = "2.10%",
                cableCpcMm2 = "16 mm²", // Already has unit
                adsZsOhm = "0.245 Ω", // Already has unit
                faultLevelKa = "25 kA", // Already has unit
                protectionSelectivity = "N/A"
            )
        )

        val dxf = SldDxfExporter.exportToDxfString(
            SldEngineResult(
                nodes = listOf(node),
                edges = emptyList(),
                bounds = Rect(0f, 0f, 500f, 500f),
                tierDatums = emptyList()
            )
        )

        // Verify zero leaked internal keys
        assertFalse("DXF must not contain raw circ_10 in text", dxf.contains("circ_10"))

        // Verify zero doubled units in DXF text
        assertFalse("Must not contain mm for metres", dxf.contains("50 mm") || dxf.contains("50mm"))
        assertFalse("Must not contain AA for amperes", dxf.contains("AA"))
        assertFalse("Must not contain doubled percentage", dxf.contains("%%"))
    }
}
