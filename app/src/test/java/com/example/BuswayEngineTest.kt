package com.example

import com.example.data.CircuitEntity
import com.example.data.catalog.BuswayCatalogEntry
import com.example.data.catalog.CatalogRepository
import com.example.engine.BuswayEngine
import com.example.engine.CableSizingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuswayEngineTest {

    private fun findAssetFile(filename: String): File {
        val candidates = listOf(
            File("app/src/main/assets/$filename"),
            File("src/main/assets/$filename"),
            File("../app/src/main/assets/$filename")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Asset file not found in candidates: $filename")
    }

    private fun loadCatalog(): List<BuswayCatalogEntry> {
        val jsonText = findAssetFile("busway_catalog.json").readText()
        val repo = CatalogRepository()
        return repo.parseBusways(jsonText, mutableListOf())
    }

    @Test
    fun testBuswayCatalogLoading() {
        val catalog = loadCatalog()
        assertEquals("busway_catalog.json must contain exactly 36 rows", 36, catalog.size)
        
        val elsewedyCount = catalog.count { it.mfg == "Elsewedy" }
        val schneiderCount = catalog.count { it.mfg == "Schneider" }
        val abbCount = catalog.count { it.mfg == "ABB" }
        assertEquals("Must have 20 Elsewedy rows", 20, elsewedyCount)
        assertEquals("Must have 8 Schneider rows", 8, schneiderCount)
        assertEquals("Must have 8 ABB rows", 8, abbCount)

        // Verify first Cu row (Power Link 800A)
        val cu800 = catalog.first { it.mfg == "Elsewedy" && it.model == "Power Link" && it.ratingA == 800.0 }
        assertEquals(36.0, cu800.icwKA, 0.001)
        assertEquals(75.6, cu800.ipkKA, 0.001)
        assertEquals(0.01669, cu800.rMOhmPerM, 0.000001)
        assertEquals(0.01069, cu800.xMOhmPerM, 0.000001)

        // Verify first Al row (Spine 800A)
        val al800 = catalog.first { it.mfg == "Elsewedy" && it.model == "Spine" && it.ratingA == 800.0 }
        assertEquals(36.0, al800.icwKA, 0.001)
        assertEquals(75.6, al800.ipkKA, 0.001)
        assertEquals(0.02103, al800.rMOhmPerM, 0.000001)
        assertEquals(0.01340, al800.xMOhmPerM, 0.000001)

        // Verify first Schneider row (Canalis KTA 800A)
        val kta800 = catalog.first { it.mfg == "Schneider" && it.model == "Canalis KTA" && it.ratingA == 800.0 }
        assertEquals(31.0, kta800.icwKA, 0.001)
        assertEquals(64.0, kta800.ipkKA, 0.001)
        assertEquals(0.079, kta800.rMOhmPerM, 0.000001)
        assertEquals(0.018, kta800.xMOhmPerM, 0.000001)
        assertEquals(0.0, kta800.weightKgPerM, 0.001)

        // Verify first ABB row (WavePro-A 800A)
        val abb800 = catalog.first { it.mfg == "ABB" && it.model == "WavePro-A" && it.ratingA == 800.0 }
        assertEquals(30.0, abb800.icwKA, 0.001)
        assertEquals(63.0, abb800.ipkKA, 0.001)
        assertEquals(0.071, abb800.rMOhmPerM, 0.000001)
        assertEquals(0.043, abb800.xMOhmPerM, 0.000001)
        assertEquals(0.0, abb800.weightKgPerM, 0.001)
    }

    @Test
    fun testSchneiderCanalisKtaSizing() {
        val catalog = loadCatalog()
        // 1000 kVA at 400V 3P+N -> Ib = 1443.38 A
        // Candidates in Canalis KTA: 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000
        // Smallest rating >= 1443.38 A is 1600 A
        val circuit = CircuitEntity(
            circuitName = "TR-MDB Feeder Schneider",
            loadKVA = 1000.0,
            fedFrom = "Transformer",
            voltageV = 400,
            phases = "3P+N",
            safetyFactor = 1.0,
            feederType = "Busway",
            buswayMfg = "Schneider",
            buswayModel = "Canalis KTA",
            length_m = 30.0,
            maxVDpct = 0.03
        )

        val result = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog)

        assertEquals("✓ PASS", result.status)
        assertNotNull(result.selectedBusway)
        assertEquals("Schneider", result.selectedBusway?.mfg)
        assertEquals("Canalis KTA", result.selectedBusway?.model)
        assertEquals(1600.0, result.ratingA, 0.001)
        assertEquals(65.0, result.icwKA, 0.001)
        assertEquals(143.0, result.ipkKA, 0.001)
        assertEquals(0.035, result.rMOhmPerM, 0.00001)
        assertEquals(0.013, result.xMOhmPerM, 0.00001)
    }

    @Test
    fun testAbbWaveProASizing() {
        val catalog = loadCatalog()
        // 1000 kVA at 400V 3P+N -> Ib = 1443.38 A
        // Candidates in WavePro-A: 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000
        // Smallest rating >= 1443.38 A is 1600 A
        val circuit = CircuitEntity(
            circuitName = "TR-MDB Feeder ABB",
            loadKVA = 1000.0,
            fedFrom = "Transformer",
            voltageV = 400,
            phases = "3P+N",
            safetyFactor = 1.0,
            feederType = "Busway",
            buswayMfg = "ABB",
            buswayModel = "WavePro-A",
            length_m = 30.0,
            maxVDpct = 0.03
        )

        val result = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog)

        assertEquals("✓ PASS", result.status)
        assertNotNull(result.selectedBusway)
        assertEquals("ABB", result.selectedBusway?.mfg)
        assertEquals("WavePro-A", result.selectedBusway?.model)
        assertEquals(1600.0, result.ratingA, 0.001)
        assertEquals(65.0, result.icwKA, 0.001)
        assertEquals(143.0, result.ipkKA, 0.001)
        assertEquals(0.032, result.rMOhmPerM, 0.00001)
        assertEquals(0.025, result.xMOhmPerM, 0.00001)
    }

    @Test
    fun testPowerLinkCuSizing_NoInterpolation() {
        val catalog = loadCatalog()
        // 1250 kVA at 400V 3P+N -> Ib = 1250 * 1000 / (sqrt(3) * 400) = 1804.22 A
        // Safety factor 1.0 -> req = 1804.22 A
        // Candidates in Power Link: 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6300
        // Smallest rating >= 1804.22 A is 2000 A!
        val circuit = CircuitEntity(
            circuitName = "TR-MDB Feeder",
            loadKVA = 1250.0,
            fedFrom = "Transformer",
            voltageV = 400,
            phases = "3P+N",
            safetyFactor = 1.0,
            feederType = "Busway",
            buswayMfg = "Elsewedy",
            buswayModel = "Power Link",
            length_m = 40.0,
            maxVDpct = 0.03
        )

        val result = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog)

        assertEquals("✓ PASS", result.status)
        assertNotNull(result.selectedBusway)
        assertEquals(2000.0, result.ratingA, 0.001)
        assertEquals(80.0, result.icwKA, 0.001)
        assertEquals(143.0, result.ipkKA, 0.001)
        assertEquals(0.00874, result.rMOhmPerM, 0.00001)
        assertEquals(0.00736, result.xMOhmPerM, 0.00001)
        
        // Verify SizingResult conversion
        val adapted = BuswayEngine.toSizingResult(result)
        assertEquals("✓ PASS", adapted.status)
        assertEquals(0.00874, adapted.rUsed, 0.00001)
        assertEquals(0.00736, adapted.xUsed, 0.00001)
        assertEquals(80.0, adapted.deviceBreakingKA, 0.001)
    }

    @Test
    fun testSpineAlSizing_NoInterpolation() {
        val catalog = loadCatalog()
        // 1000 kVA at 400V 3P+N -> Ib = 1443.38 A
        // Smallest rating >= 1443.38 A in Spine (Al) is 1600 A
        val circuit = CircuitEntity(
            circuitName = "TR-MDB Feeder Al",
            loadKVA = 1000.0,
            fedFrom = "Transformer",
            voltageV = 400,
            phases = "3P+N",
            safetyFactor = 1.0,
            feederType = "Busway",
            buswayMfg = "Elsewedy",
            buswayModel = "Spine",
            length_m = 30.0,
            maxVDpct = 0.03
        )

        val result = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog)

        assertEquals("✓ PASS", result.status)
        assertNotNull(result.selectedBusway)
        assertEquals(1600.0, result.ratingA, 0.001)
        assertEquals(65.0, result.icwKA, 0.001)
        assertEquals(105.0, result.ipkKA, 0.001)
        assertEquals(0.01577, result.rMOhmPerM, 0.00001)
        assertEquals(0.01199, result.xMOhmPerM, 0.00001)
    }

    @Test
    fun testOverloadExceedsMaxCatalogRating() {
        val catalog = loadCatalog()
        // 5000 kVA at 400V -> Ib = 7216.88 A > 6300 A (max rating)
        val circuit = CircuitEntity(
            circuitName = "Gigantic Trunk",
            loadKVA = 5000.0,
            fedFrom = "Transformer",
            voltageV = 400,
            phases = "3P+N",
            safetyFactor = 1.0,
            feederType = "Busway",
            buswayMfg = "Elsewedy",
            buswayModel = "Power Link"
        )

        val result = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog)

        assertEquals("✗ FAIL", result.status)
        assertTrue(result.failReason.contains("exceeds maximum catalog busway rating"))
    }

    @Test
    fun testShortCircuitIcwCheck() {
        val catalog = loadCatalog()
        // 500 kVA load -> Ib = 721.68 A -> selects 800A Power Link (Icw = 36 kA)
        val circuit = CircuitEntity(
            circuitName = "TR-Panel High Short Circuit",
            loadKVA = 500.0,
            fedFrom = "Transformer",
            voltageV = 400,
            phases = "3P+N",
            safetyFactor = 1.0,
            feederType = "Busway",
            buswayMfg = "Elsewedy",
            buswayModel = "Power Link"
        )

        // If network short circuit is 50 kA, 800A busway has Icw = 36 kA -> Fail!
        val resultFail = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog, networkIscKA = 50.0)
        assertEquals("✗ FAIL", resultFail.status)
        assertTrue(resultFail.failReason.contains("Busway rated Icw (36.0 kA) is lower than network short circuit level"))

        // If network short circuit is 30 kA, 800A busway has Icw = 36 kA -> Pass!
        val resultPass = BuswayEngine.sizeBusway(circuit, parentCumulativeVD = 0.0, catalog = catalog, networkIscKA = 30.0)
        assertEquals("✓ PASS", resultPass.status)
    }

    @Test
    fun testCableSizingRegression() {
        // Confirm CableSizingEngine behavior remains unchanged for standard Cable circuits
        val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
        val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
        val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
        val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
        val deratingDataJson = findAssetFile("derating_data.json").readText()
        val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

        val repository = CatalogRepository()
        repository.loadAllFromStrings(
            cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
            accessoryCatalogJson, deratingDataJson, transformersSeedJson
        )

        val circuit = CircuitEntity(
            circuitName = "Motor Feed Cable",
            loadKVA = 30.0,
            pf = 0.85,
            phases = "3P+N",
            voltageV = 400,
            safetyFactor = 1.0,
            length_m = 50.0,
            installation = "Laid in Ducts",
            cableType = "Cu/XLPE/STA/PVC - 3C",
            manufacturer = "Elsewedy",
            feederType = "Cable"
        )

        val result = CableSizingEngine.sizeCircuit(circuit, 0.0, repository)
        assertNotNull(result)
        assertEquals("Fail reason: ${result.failReason}", "✓ PASS", result.status)
    }
}
