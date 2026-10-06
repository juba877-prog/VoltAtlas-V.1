package com.example.engine

import com.example.data.Catalog
import com.example.data.CableType
import com.example.data.CircuitEntity
import com.example.data.catalog.CatalogRepository
import com.example.testing.CatalogStateResetRule
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * ECT-EE-01 Characterization Test.
 *
 * Verifies the UNMODIFIED behavior of CableSizingEngine prior to ECT-EE-01:
 *  - PVC cables evaluate voltage drop using R20 (R_Cu20 / R_Al20) with thermalFactor = 1.0 (20 °C basis).
 *  - XLPE cables evaluate voltage drop using the 70 °C estimated catalog column (R_Cu_70_est / R_Al_70_est)
 *    with thermalFactor = 1.0 (70 °C estimated basis, rather than 90 °C).
 *  - Near-marginal circuits (35 mm² Cu/PVC at 135m, 50 mm² Cu/XLPE at 155m) PASS on unmodified code
 *    because the resistance basis is lower than the true operating temperature under IEC 60364-5-52.
 *
 * Characterization verified on unmodified baseline (3/3 passed). Ignored post-fix to preserve provenance.
 */
@Ignore("Pre-fix characterization baseline verified on unmodified code prior to ECT-EE-01 fix; preserved for provenance")
class VoltageDropTemperatureBasisCharacterizationTest {

    companion object {
        @JvmField
        @ClassRule
        val catalogStateReset = CatalogStateResetRule()

        private val repository = CatalogRepository()

        private fun findAssetFile(filename: String): File {
            val candidates = listOf(
                File("app/src/main/assets/$filename"),
                File("src/main/assets/$filename"),
                File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: throw java.io.FileNotFoundException("Asset file not found: $filename")
        }

        @BeforeClass
        @JvmStatic
        fun setUpRepository() {
            val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
            val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
            val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
            val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
            val deratingDataJson = findAssetFile("derating_data.json").readText()
            val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

            val diag = repository.loadAllFromStrings(
                cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
                accessoryCatalogJson, deratingDataJson, transformersSeedJson
            )
            assertEquals("COMPLETE", diag.status)

            try {
                val cableTypesJson = findAssetFile("cable_types.json").readText()
                val array = JSONArray(cableTypesJson)
                val list = mutableListOf<CableType>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CableType(
                            code = obj.getInt("code"),
                            cableType = obj.getString("cableType"),
                            conductor = obj.getString("conductor"),
                            insulation = obj.getString("insulation"),
                            armour = obj.getString("armour"),
                            cores = obj.getString("cores"),
                            voltage = obj.getString("voltage"),
                            standard = obj.getString("standard"),
                            rColumn = obj.getString("rColumn")
                        )
                    )
                }
                Catalog.cableTypes = list
            } catch (e: Exception) {
                // Ignore if not present
            }
        }
    }

    @Test
    fun characterization_pvcEvaluatesWithR20_thermalFactorIsOne() {
        // 35 mm² Cu/PVC feeder (Elsewedy Cu/PVC/PVC - 4C), 400 V, 3P+N, Ib = 95 A, L = 135 m, pf = 0.85, maxVD = 3.0%
        // Catalog data for 35 mm² Cu: R20 = 0.524 Ω/km, X = 0.078 Ω/km
        // Unmodified code evaluates R = R20 = 0.524 Ω/km (thermalFactor = 1.0)
        // Z = 0.524 * 0.85 + 0.078 * sin(acos(0.85)) = 0.44540 + 0.041089 = 0.486489 Ω/km
        // ΔV = sqrt(3) * 95 * 0.486489 * 0.135 = 10.807 V => 2.702% <= 3.0% => PASS
        val circuit = CircuitEntity(
            id = 101,
            circuitName = "PVC-35-Feeder",
            loadKVA = 65.818, // sqrt(3) * 400 * 95 / 1000 = 65.818 kVA -> Ib ≈ 95 A
            pf = 0.85,
            phases = "3P+N",
            voltageV = 400,
            safetyFactor = 1.0,
            overrideDeviceSize = 100.0,
            length_m = 135.0,
            installation = "Laid in Ducts",
            cableType = "Cu/PVC/PVC - 4C",
            manufacturer = "Elsewedy",
            parallelRuns = 1,
            maxVDpct = 0.03,
            overrideCableSize = "35 mm²"
        )

        val result = CableSizingEngine.sizeCircuit(circuit, 0.0, repository)

        assertEquals("35 mm²", result.cableSize_mm2)
        assertEquals(0.524, result.r20!!, 1e-6)
        // Unmodified code: rUsed is exactly R20
        assertEquals(0.524, result.rUsed, 1e-6)
        assertEquals(result.r20, result.rUsed, 1e-6)
        assertEquals(10.807, result.voltDrop_V, 0.05)
        assertEquals(2.70, result.vdPct, 0.05)
        assertTrue("Unmodified code must PASS 35 mm² PVC at 20 °C resistance basis", result.isVoltageDropOk)
        assertEquals("✓ PASS", result.status)
    }

    @Test
    fun characterization_xlpeEvaluatesWithR70est_notR90_thermalFactorIsOne() {
        // 50 mm² Cu/XLPE feeder (Elsewedy Cu/XLPE/STA/PVC - 4C), 400 V, 3P+N, Ib = 135 A, L = 155 m, pf = 0.85, maxVD = 4.0%
        // Catalog data for 50 mm² Cu: R20 = 0.387 Ω/km, X = 0.076 Ω/km, R_Cu_70_est = 0.464 Ω/km
        // Unmodified code evaluates R = R_Cu_70_est = 0.464 Ω/km (thermalFactor = 1.0)
        // Z = 0.464 * 0.85 + 0.076 * sin(acos(0.85)) = 0.39440 + 0.040035 = 0.434435 Ω/km
        // ΔV = sqrt(3) * 135 * 0.434435 * 0.155 = 15.728 V => 3.932% <= 4.0% => PASS
        val circuit = CircuitEntity(
            id = 102,
            circuitName = "XLPE-50-Feeder",
            loadKVA = 93.53, // sqrt(3) * 400 * 135 / 1000 = 93.53 kVA -> Ib ≈ 135 A
            pf = 0.85,
            phases = "3P+N",
            voltageV = 400,
            safetyFactor = 1.0,
            overrideDeviceSize = 135.0,
            length_m = 155.0,
            installation = "Laid in Ducts",
            cableType = "Cu/XLPE/STA/PVC - 4C",
            manufacturer = "Elsewedy",
            parallelRuns = 1,
            maxVDpct = 0.04,
            overrideCableSize = "50 mm²"
        )

        val result = CableSizingEngine.sizeCircuit(circuit, 0.0, repository)

        assertEquals("50 mm²", result.cableSize_mm2)
        assertEquals(0.387, result.r20!!, 1e-6)
        // Unmodified code: rUsed is R_Cu_70_est (0.464), not R90 (0.387 * 1.2751 = 0.4935)
        assertEquals(0.464, result.rUsed, 1e-6)
        assertEquals(15.783, result.voltDrop_V, 0.05)
        assertEquals(3.95, result.vdPct, 0.05)
        assertTrue("Unmodified code must PASS 50 mm² XLPE at 70 °C est resistance basis", result.isVoltageDropOk)
        assertEquals("✓ PASS", result.status)

    }

    @Test
    fun characterization_150mm2_pvcVsXlpe_unmodifiedRUsedDifference() {
        // 150 mm² Cu: R20 = 0.124 Ω/km, R_Cu_70_est = 0.149 Ω/km
        val cXlpe = CircuitEntity(
            id = 103, circuitName = "XLPE-150", loadKVA = 100.0, pf = 0.85,
            phases = "3P+N", voltageV = 400, length_m = 50.0, installation = "Laid in Ducts",
            cableType = "Cu/XLPE/STA/PVC - 4C", manufacturer = "Elsewedy",
            overrideCableSize = "150 mm²"
        )
        val cPvc = CircuitEntity(
            id = 104, circuitName = "PVC-150", loadKVA = 100.0, pf = 0.85,
            phases = "3P+N", voltageV = 400, length_m = 50.0, installation = "Laid in Ducts",
            cableType = "Cu/PVC/PVC - 4C", manufacturer = "Elsewedy",
            overrideCableSize = "150 mm²"
        )

        val resXlpe = CableSizingEngine.sizeCircuit(cXlpe, 0.0, repository)
        val resPvc = CableSizingEngine.sizeCircuit(cPvc, 0.0, repository)

        assertEquals(0.124, resPvc.r20!!, 1e-6)
        assertEquals(0.124, resXlpe.r20!!, 1e-6)

        // Unmodified code: PVC rUsed is 0.124 (R20), XLPE rUsed is 0.149 (R_Cu_70_est)
        assertEquals(0.124, resPvc.rUsed, 1e-6)
        assertEquals(0.149, resXlpe.rUsed, 1e-6)
    }
}
