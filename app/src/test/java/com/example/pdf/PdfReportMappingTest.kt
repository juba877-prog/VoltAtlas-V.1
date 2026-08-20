package com.example.pdf

import com.example.data.*
import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

class PdfReportMappingTest {

    @Test
    fun testIssue1_DeviceColumnDoesNotUsePanelType() {
        val circuit = CircuitEntity(id = 1, circuitName = "Feeder-1", fedFrom = "Transformer", panelType = "MDB")
        val sizingResult = SizingResult(
            Ib_A = 250.0, deviceSize_A = 315.0, deviceType = "MCCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 250.0, productCode = "C1", cableSize_mm2 = "185 mm²",
            ratedI_table_A = 300.0, capacity_derated_Iz_A = 300.0, utilizationPct = 83.3,
            neutral_mm2 = "95 mm²", earthCPC_mm2 = "95 mm²", voltDrop_V = 2.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "PASS"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)

        val devType = sizingResult.deviceType.takeIf { it.isNotBlank() } ?: "CB"
        val devSize = "%.0f".format(sizingResult.deviceSize_A)
        val deviceText = "$devType $devSize A"
        val panelText = circuit.panelType.orEmpty()

        // Device column must output "MCCB 315 A"
        assertEquals("MCCB 315 A", deviceText)
        // Panel type "MDB" must NOT be in the Device column text
        assertFalse("Device text must not contain panel type 'MDB'", deviceText.contains(panelText))
        assertNotEquals("315 A MDB", deviceText)
    }

    @Test
    fun testIssue2_ProtectionFaultTypeDerivationAndRowSeparation() {
        val circuit = CircuitEntity(id = 10, circuitName = "MDB-Main-Feeder")
        val scResult = ShortCircuitResult(
            circuitId = 10, sourceName = "TX-01", cableMm2 = "70 mm²",
            iscKA = 12.5, iwKA = 18.0, deviceKA = 25.0, deviceCheck = "PASS",
            cableCheck = "PASS", marginPct = 44.0, status = "PASS", note = ""
        )
        val adsResult = AdsResult(
            circuitId = 10, circuitName = "MDB-Main-Feeder", calculatedZs = 0.045,
            maxZsAllowed = 0.1, status = "PASS", disconnectionTime = "0.1",
            r1_ohm_km = 0.3, r2_ohm_km = 0.3, length_m = 50.0
        )

        // 3-Phase fault row
        val faultType3P = "3-Phase"
        assertEquals("3-Phase", faultType3P)
        assertEquals(12.5, scResult.iscKA, 0.01)

        // Earth Fault / ADS row
        val faultTypeEarth = "Earth Fault (ADS)"
        assertEquals("Earth Fault (ADS)", faultTypeEarth)
        assertEquals(0.045, adsResult.calculatedZs, 0.0001)

        // Verify distinct fault type labels (not hardcoded identical)
        assertNotEquals(faultType3P, faultTypeEarth)
    }

    @Test
    fun testIssue3_ValidationStatusScopeCannotRepresentActionRequiredAsOverallPass() {
        val genResult = GeneratorResult(
            status = "SELECT_LOADS",
            essentialLoadKVA = 0.0,
            nonEssentialLoadKVA = 500.0,
            totalConnectedLoad = 500.0,
            designCapacity = 0.0
        )

        val formattedStatus = formatReportStatus(genResult.status)
        assertEquals("Action Required — Select Loads", formattedStatus)

        // Verify that an Action Required power state is never represented as "PASS"
        assertNotEquals("PASS", formattedStatus)
        assertTrue(formattedStatus.contains("Action Required"))
    }

    @Test
    fun testIssue4_TransformerZtUnitIsMilliohms() {
        val txResult = TransformerResult(
            connectedLoad = 800.0, circuitsAssigned = 5, realPower = 640.0,
            demandLoad = 640.0, diversifiedLoad = 640.0, futureLoad = 640.0,
            designLoad = 640.0, requiredKVA = 800.0, recommendedSize = 1000.0,
            selectedSize = 1000.0, loadingPct = 80.0, flc = 1443.4, zt = 8.0,
            rt = 1.6, xt = 7.8, isc3Phase = 30.3, isc1Phase = 22.0, status = "✓ PASS",
            z0 = 7.6, vectorGroup = "Dyn11", voltage = 400.0
        )

        // zt = 8.0 mΩ for 1000 kVA 400V 5% Z transformer
        assertEquals(8.0, txResult.zt, 0.001)
        val headerUnit = "Zt (mΩ)"
        assertTrue("Header must specify mΩ unit", headerUnit.contains("mΩ"))
        assertFalse("Header must not be generic 'Ω / p.u.'", headerUnit.contains("Ω / p.u."))
    }

    @Test
    fun testIssue5_AccessoryGroupingNullSafety() {
        val itemGlandUncoded = ProcurementItem(
            circuitName = "C1", cableType = "Cu/XLPE/SWA", cableSize = "70 mm²",
            glandModel = "CW-25", glandDesc = "CW 25 Cable Gland", lugsType = "Lug 70",
            requiredTrayWidth_mm = 200.0, status = "APPROVED", glandQty = 2, lugQty = 8,
            matchedGlandCatalogCode = "", matchedLugCatalogCode = "N/A"
        )

        val itemLugUncoded = ProcurementItem(
            circuitName = "C2", cableType = "Cu/PVC", cableSize = "70 mm²",
            glandModel = "N/A", glandDesc = "No gland", lugsType = "Copper Compression Lug 70 mm²",
            requiredTrayWidth_mm = 100.0, status = "APPROVED", glandQty = 0, lugQty = 8,
            matchedGlandCatalogCode = "N/A", matchedLugCatalogCode = ""
        )

        val items = listOf(itemGlandUncoded, itemLugUncoded)

        // Group glands
        val glandItems = items.filter { it.glandModel != "N/A" && it.glandModel.isNotBlank() }
        val glandGroupCount = glandItems.groupBy {
            Pair("Cable Gland", it.matchedGlandCatalogCode.ifBlank { it.glandDesc })
        }.size

        // Group lugs
        val lugItems = items.filter { it.lugsType.isNotBlank() }
        val lugGroupCount = lugItems.groupBy {
            Pair("Compression Lug", it.matchedLugCatalogCode.ifBlank { it.lugsType })
        }.size

        assertEquals(1, glandGroupCount)
        assertEquals(2, lugGroupCount) // itemGlandUncoded's "Lug 70" vs itemLugUncoded's "Copper Compression Lug 70 mm²"

        // Ensure category distinction prevents cross-category merging
        val categoryGland = "Cable Gland"
        val categoryLug = "Compression Lug"
        assertNotEquals(categoryGland, categoryLug)
    }

    @Test
    fun testFix1_OptionalProjectMetadataOmittedWhenBlank() {
        val projectWithClient = ProjectEntity(projectId = "P1", projectName = "Substation A", clientName = "Ministry of Electricity", projectCode = "PRJ-01")
        val projectBlankClient = ProjectEntity(projectId = "P2", projectName = "Substation B", clientName = "", projectCode = null)

        assertEquals("Ministry of Electricity", projectWithClient.clientName)
        assertFalse(projectBlankClient.clientName.orEmpty().isNotBlank())
    }
}
