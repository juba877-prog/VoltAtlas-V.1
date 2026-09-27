package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.CableCheckStatus
import com.example.testing.CatalogStateResetRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class GroupingDeratingTest {

    companion object {
        @JvmField
        @ClassRule
        val catalogStateReset = CatalogStateResetRule()

        private val repository = CatalogRepository()
        private lateinit var deratingDataJson: JSONObject

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
        fun setUp() {
            val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
            val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
            val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
            val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
            val rawDeratingText = findAssetFile("derating_data.json").readText()
            val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

            deratingDataJson = JSONObject(rawDeratingText)

            val diag = repository.loadAllFromStrings(
                cableCatalogJson, cableTypesDataJson, breakerCatalogJson,
                accessoryCatalogJson, rawDeratingText, transformersSeedJson
            )
            assertEquals("COMPLETE", diag.status)

            try {
                val cableTypesJson = findAssetFile("cable_types.json").readText()
                val array = org.json.JSONArray(cableTypesJson)
                val list = mutableListOf<com.example.data.CableType>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        com.example.data.CableType(
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
            } catch (ignored: Exception) {
            }
        }
    }

    @Test
    fun testAll18GroupingKeysInDeratingData() {
        val groupingFactors = deratingDataJson
            .getJSONObject("grouping")
            .getJSONObject("factors")

        val prefixes = listOf("Buried", "Duct", "Air")
        val formations = listOf("Trefoil", "Flat")
        val spacings = listOf("Touching", "0.15m", "0.30m")

        var verifiedCombinations = 0

        for (prefix in prefixes) {
            for (formation in formations) {
                for (spacing in spacings) {
                    val key = "${prefix}_${formation}_${spacing}"
                    val expectedArray = groupingFactors.optJSONArray(key)
                    assertNotNull("JSON missing expected grouping key: $key", expectedArray)

                    for (circuits in 1..6) {
                        val factor = CableSizingEngine.getGroupingDerating(
                            installation = prefix,
                            formation = formation,
                            spacing = spacing,
                            noCircuits = circuits
                        )
                        assertNotNull(
                            "Factor must not be null for valid key: $key, circuits=$circuits",
                            factor
                        )
                        val expectedFactor = expectedArray!!.getDouble(circuits - 1)
                        assertEquals(
                            "Factor mismatch for $key with $circuits circuits",
                            expectedFactor,
                            factor!!,
                            1e-4
                        )
                    }
                    verifiedCombinations++
                }
            }
        }

        assertEquals("Must verify all 18 derating table combinations", 18, verifiedCombinations)
    }

    @Test
    fun testUiInstallationStringVariants() {
        val testCases = listOf(
            Triple("Laid Direct in Ground", "Flat", "Touching") to "Buried_Flat_Touching",
            Triple("direct_buried", "Trefoil", "0.15m") to "Buried_Trefoil_0.15m",
            Triple("Buried", "Flat", "Touching") to "Buried_Flat_Touching",
            Triple("Laid in Ducts", "Flat", "Touching") to "Duct_Flat_Touching",
            Triple("duct", "Trefoil", "0.30m") to "Duct_Trefoil_0.30m",
            Triple("Laid in Free Air", "Flat", "Touching") to "Air_Flat_Touching",
            Triple("cable tray", "Trefoil", "Touching") to "Air_Trefoil_Touching",
            Triple("air", "Flat", "0.30m") to "Air_Flat_0.30m"
        )

        val groupingFactors = deratingDataJson.getJSONObject("grouping").getJSONObject("factors")

        for ((input, expectedKey) in testCases) {
            val (installation, formation, spacing) = input
            val factor = CableSizingEngine.getGroupingDerating(
                installation = installation,
                formation = formation,
                spacing = spacing,
                noCircuits = 2
            )
            assertNotNull("Must resolve factor for UI installation: '$installation'", factor)
            val expectedFactor = groupingFactors.getJSONArray(expectedKey).getDouble(1)
            assertEquals("Factor mismatch for '$installation'", expectedFactor, factor!!, 1e-4)
        }
    }

    @Test
    fun testUnknownInstallationType_failsLoudlyWithNull() {
        val unknownInstallations = listOf("conduit", "Overhead", "Tunnel", "Underwater", "RandomMethod", "", "   ")
        for (unknown in unknownInstallations) {
            val factor = CableSizingEngine.getGroupingDerating(
                installation = unknown,
                formation = "Flat",
                spacing = "Touching",
                noCircuits = 2
            )
            assertNull("Unknown installation '$unknown' must fail loudly by returning null", factor)
        }
    }

    @Test
    fun testUnknownFormation_failsLoudlyWithNull() {
        val unknownFormations = listOf("Zigzag", "Vertical", "Triangle", "RandomFormation")
        for (unknown in unknownFormations) {
            val factor = CableSizingEngine.getGroupingDerating(
                installation = "Air",
                formation = unknown,
                spacing = "Touching",
                noCircuits = 2
            )
            assertNull("Unknown formation '$unknown' must fail loudly by returning null", factor)
        }
    }

    @Test
    fun testUnknownSpacing_failsLoudlyWithNull() {
        val unknownSpacings = listOf("0.50m", "1m", "Far", "100cm", "RandomSpacing")
        for (unknown in unknownSpacings) {
            val factor = CableSizingEngine.getGroupingDerating(
                installation = "Air",
                formation = "Flat",
                spacing = unknown,
                noCircuits = 2
            )
            assertNull("Unknown spacing '$unknown' must fail loudly by returning null", factor)
        }
    }

    @Test
    fun testInvalidCircuitCount_failsLoudlyWithNull() {
        val invalidCounts = listOf(0, -1, -5)
        for (count in invalidCounts) {
            val factor = CableSizingEngine.getGroupingDerating(
                installation = "Air",
                formation = "Flat",
                spacing = "Touching",
                noCircuits = count
            )
            assertNull("Non-positive circuit count $count must fail loudly by returning null", factor)
        }
    }

    @Test
    fun testMissingCatalogDeratingData_returnsNull() {
        val originalDerating = Catalog.deratingData
        try {
            Catalog.deratingData = null
            val factor = CableSizingEngine.getGroupingDerating(
                installation = "Air",
                formation = "Flat",
                spacing = "Touching",
                noCircuits = 2
            )
            assertNull("Missing derating data in Catalog must fail loudly by returning null", factor)
        } finally {
            Catalog.deratingData = originalDerating
        }
    }

    @Test
    fun testSizeCircuit_withUnknownInstallation_failsLoudlyWithDeratingInvalid() {
        val circuit = CircuitEntity(
            id = 1,
            circuitName = "C-TEST-DERATING",
            loadKVA = 50.0,
            pf = 0.8,
            phases = "3P+N",
            voltageV = 400,
            length_m = 50.0,
            installation = "Overhead", // Unknown installation method
            cableType = "Cu/XLPE/SWA/PVC",
            manufacturer = "Elsewedy",
            parallelRuns = 1,
            noCircuits = 2,
            formation = "Flat",
            spacing = "Touching"
        )

        val result = CableSizingEngine.sizeCircuit(circuit, 0.0, repository)
        assertEquals(CableFailureCode.DERATING_INVALID, result.primaryFailureCode)
        assertTrue(
            "Error message should mention grouping derating: ${result.failReason}",
            result.failReason.contains("Grouping derating factor not found")
        )

        val evaluation = CableCalculationService.evaluateEngineeringChecks(result, circuit)
        val ampacityCheck = evaluation.checks.first { it.checkCode == com.example.engine.coordinator.CableCheckCode.AMPACITY }
        val localVdCheck = evaluation.checks.first { it.checkCode == com.example.engine.coordinator.CableCheckCode.VOLTAGE_DROP }
        val cumulativeVdCheck = evaluation.checks.first { it.checkCode == com.example.engine.coordinator.CableCheckCode.CUMULATIVE_VOLTAGE_DROP }

        assertEquals(CableCheckStatus.INCOMPLETE, ampacityCheck.status)
        assertEquals(CableCheckStatus.INCOMPLETE, localVdCheck.status)
        assertEquals(CableCheckStatus.INCOMPLETE, cumulativeVdCheck.status)
        assertTrue(
            ampacityCheck.message.contains("Grouping derating factor not found")
        )
    }
}
