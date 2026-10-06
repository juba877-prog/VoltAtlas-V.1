package com.example.engine

import com.example.data.Catalog
import com.example.data.CableType
import com.example.data.CircuitEntity
import com.example.data.catalog.CatalogRepository
import com.example.testing.CatalogStateResetRule
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File

/**
 * ECT-EE-01 Target Regression Test.
 *
 * Verifies the approved D1 contract for voltage-drop resistance temperature basis:
 *  - PVC evaluates at 70 °C maximum operating conductor temperature:
 *      R_op = R20 * 1.1965 (Cu) or R20 * 1.2015 (Al) per IEC 60364-5-52 Annex G / IEC 60228.
 *  - XLPE evaluates at 90 °C maximum operating conductor temperature:
 *      R_op = R20 * 1.2751 (Cu) or R20 * 1.2821 (Al) per IEC 60364-5-52 Annex G / IEC 60228.
 *  - r20 remains purely 20 °C conductor resistance for short-circuit consumption.
 *  - Proves the expected PASS -> FAIL verdict flips on marginal circuits.
 *
 * THIS TEST MUST FAIL ON UNMODIFIED CODE.
 */
class VoltageDropTemperatureBasisTargetTest {

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
    fun target_pvcEvaluatesWithR70_andFlipsPassToFailOnMarginalCircuit() {
        // 35 mm² Cu/PVC feeder (Elsewedy Cu/PVC/PVC - 4C), 400 V, 3P+N, Ib = 95 A, L = 135 m, pf = 0.85, maxVD = 3.0%
        // Catalog data for 35 mm² Cu: R20 = 0.524 Ω/km, X = 0.078 Ω/km
        // Under D1 (70 °C PVC):
        // R_70 = 0.524 * 1.1965 = 0.626966 Ω/km
        // Z = 0.626966 * 0.85 + 0.078 * sin(acos(0.85)) = 0.532921 + 0.041089 = 0.574010 Ω/km
        // ΔV = sqrt(3) * 95 * 0.574010 * 0.135 = 12.752 V => ΔV% = 3.19% > 3.00%
        // Must flip from PASS to FAIL!
        val circuit = CircuitEntity(
            id = 201,
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
        // Under D1: rUsed is R20 * 1.1965 = 0.626966 Ω/km
        val expectedR70 = 0.524 * 1.1965
        assertEquals(expectedR70, result.rUsed, 1e-4)
        assertEquals(12.752, result.voltDrop_V, 0.05)
        assertEquals(3.19, result.vdPct, 0.05)
        assertFalse("35 mm² PVC must FAIL voltage drop at 70 °C operating temperature", result.isVoltageDropOk)
        assertEquals("✗ FAIL", result.status)
        assertEquals(CableFailureCode.VOLTAGE_DROP_FAIL, result.primaryFailureCode)
    }

    @Test
    fun target_xlpeEvaluatesWithR90_andFlipsPassToFailOnMarginalCircuit() {
        // 50 mm² Cu/XLPE feeder (Elsewedy Cu/XLPE/STA/PVC - 4C), 400 V, 3P+N, Ib = 135 A, L = 155 m, pf = 0.85, maxVD = 4.0%
        // Catalog data for 50 mm² Cu: R20 = 0.387 Ω/km, X = 0.076 Ω/km
        // Under D1 (90 °C XLPE):
        // R_90 = 0.387 * 1.2751 = 0.493464 Ω/km
        // Z = 0.493464 * 0.85 + 0.076 * sin(acos(0.85)) = 0.419444 + 0.040035 = 0.459480 Ω/km
        // ΔV = sqrt(3) * 135 * 0.459480 * 0.155 = 16.637 V => ΔV% = 4.16% > 4.00%
        // Must flip from PASS to FAIL!
        val circuit = CircuitEntity(
            id = 202,
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
        // Under D1: rUsed is R20 * 1.2751 = 0.493464 Ω/km (NOT the catalog estimated 0.464)
        val expectedR90 = 0.387 * 1.2751
        assertEquals(expectedR90, result.rUsed, 1e-4)
        assertEquals(16.691, result.voltDrop_V, 0.05)
        assertEquals(4.17, result.vdPct, 0.05)
        assertFalse("50 mm² XLPE must FAIL voltage drop at 90 °C operating temperature", result.isVoltageDropOk)
        assertEquals("✗ FAIL", result.status)
        assertEquals(CableFailureCode.VOLTAGE_DROP_FAIL, result.primaryFailureCode)
    }

    @Test
    fun target_150mm2_pvcVsXlpe_bothAboveR20_withExactAnnexGFactors() {
        // 150 mm² Cu: R20 = 0.124 Ω/km
        // D1: PVC uses R20 * 1.1965 = 0.148366 Ω/km (70 °C)
        // D1: XLPE uses R20 * 1.2751 = 0.158112 Ω/km (90 °C)
        val cXlpe = CircuitEntity(
            id = 203, circuitName = "XLPE-150", loadKVA = 100.0, pf = 0.85,
            phases = "3P+N", voltageV = 400, length_m = 50.0, installation = "Laid in Ducts",
            cableType = "Cu/XLPE/STA/PVC - 4C", manufacturer = "Elsewedy",
            overrideCableSize = "150 mm²"
        )
        val cPvc = CircuitEntity(
            id = 204, circuitName = "PVC-150", loadKVA = 100.0, pf = 0.85,
            phases = "3P+N", voltageV = 400, length_m = 50.0, installation = "Laid in Ducts",
            cableType = "Cu/PVC/PVC - 4C", manufacturer = "Elsewedy",
            overrideCableSize = "150 mm²"
        )

        val resXlpe = CableSizingEngine.sizeCircuit(cXlpe, 0.0, repository)
        val resPvc = CableSizingEngine.sizeCircuit(cPvc, 0.0, repository)

        // r20 is 20 °C in both
        assertEquals(0.124, resPvc.r20!!, 1e-6)
        assertEquals(0.124, resXlpe.r20!!, 1e-6)

        // Under D1:
        val expectedPvcR70 = 0.124 * 1.1965
        val expectedXlpeR90 = 0.124 * 1.2751
        assertEquals("PVC rUsed must be R20 * 1.1965 (70 °C basis)", expectedPvcR70, resPvc.rUsed, 1e-6)
        assertEquals("XLPE rUsed must be R20 * 1.2751 (90 °C basis)", expectedXlpeR90, resXlpe.rUsed, 1e-6)

        assertTrue("PVC r20 must be strictly below its 70 °C rUsed basis", resPvc.r20 < resPvc.rUsed)
        assertTrue("XLPE r20 must be strictly below its 90 °C rUsed basis", resXlpe.r20 < resXlpe.rUsed)
        assertTrue("PVC 70 °C rUsed must be strictly below XLPE 90 °C rUsed", resPvc.rUsed < resXlpe.rUsed)
    }
}
