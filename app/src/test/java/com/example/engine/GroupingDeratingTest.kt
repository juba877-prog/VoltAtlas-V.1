package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.CableCheckStatus
import com.example.testing.CatalogStateResetRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // ---------------------------------------------------------------
    // A1-R: exact-match installation aliases (D1 -> Duct, D2 -> Buried)
    // ---------------------------------------------------------------

    private fun circuitWithInstallation(installation: String, id: Int) = CircuitEntity(
        id = id,
        circuitName = "C-$id",
        loadKVA = 500.0,
        pf = 0.8,
        phases = "3P+N",
        voltageV = 400,
        length_m = 50.0,
        installation = installation,
        cableType = "Cu/XLPE/SWA/PVC",
        manufacturer = "Elsewedy",
        parallelRuns = 1,
        noCircuits = 4,
        formation = "Flat",
        spacing = "Touching"
    )

    @Test
    fun testAlias_d1_producesTheSameFactorsAsLaidInDucts() {
        // Compared on the derating factor itself, so the assertion isolates the alias rule from
        // unrelated catalog-resolution outcomes.
        val ducts = CableSizingEngine.getGroupingDerating("Laid in Ducts", "Flat", "Touching", 4)
        val d1 = CableSizingEngine.getGroupingDerating("D1", "Flat", "Touching", 4)
        val d1Lower = CableSizingEngine.getGroupingDerating("  d1  ", "Flat", "Touching", 4)

        assertNotNull("Duct must resolve a grouping factor", ducts)
        assertNotNull("D1 must be a Duct alias, not a failure", d1)
        assertEquals("D1 must produce the Duct grouping factor", ducts!!, d1!!, 1e-12)
        assertEquals("Alias must be case-insensitive and trimmed", ducts, d1Lower!!, 1e-12)

        // And the whole-circuit result must not be a derivation failure caused by the alias.
        val d1Circuit = CableSizingEngine.sizeCircuit(circuitWithInstallation("D1", 2), 0.0, repository)
        assertNotEquals(
            "D1 must not fail as an unsupported installation, reason='${d1Circuit.failReason}'",
            CableFailureCode.DERATING_INVALID, d1Circuit.primaryFailureCode
        )
    }

    @Test
    fun testAlias_d2_producesTheSameFactorsAsLaidDirectInGround() {
        val buried = CableSizingEngine.getGroupingDerating("Laid Direct in Ground", "Flat", "Touching", 4)
        val d2 = CableSizingEngine.getGroupingDerating("D2", "Flat", "Touching", 4)

        assertNotNull("Buried must resolve a grouping factor", buried)
        assertNotNull("D2 must be a Buried alias, not a failure", d2)
        assertEquals("D2 must produce the Buried grouping factor", buried!!, d2!!, 1e-12)

        val d2Circuit = CableSizingEngine.sizeCircuit(circuitWithInstallation("D2", 2), 0.0, repository)
        assertNotEquals(
            "D2 must not fail as an unsupported installation, reason='${d2Circuit.failReason}'",
            CableFailureCode.DERATING_INVALID, d2Circuit.primaryFailureCode
        )
    }

    // ---------------------------------------------------------------
    // A1-R: unsupported installation methods fail with a specific message
    // ---------------------------------------------------------------

    @Test
    fun testUnsupported_installationMethods_produceSpecificTypedFailureNeverANumber() {
        val unsupported = listOf(
            "In Conduit", "In Conduit / Trunking", "A1", "A2", "B1", "B2", "C", "In Ground"
        )
        var id = 500
        for (method in unsupported) {
            id++
            val result = CableSizingEngine.sizeCircuit(circuitWithInstallation(method, id), 0.0, repository)

            assertEquals(
                "'$method' must fail typed as DERATING_INVALID",
                CableFailureCode.DERATING_INVALID, result.primaryFailureCode
            )
            assertEquals(
                "'$method' must carry the specific message, got: ${result.failReason}",
                "Installation method '$method' is not supported by the current ampacity catalog " +
                    "(supported: Duct, Buried, Air/Tray).",
                result.failReason
            )
            assertEquals(
                "'$method' must never report PASS",
                com.example.engine.SizingResultStatus.FAIL, result.resultStatus
            )
        }
    }

    @Test
    fun testUnsupported_blankInstallation_isReportedNotDefaulted() {
        val result = CableSizingEngine.sizeCircuit(circuitWithInstallation("   ", 700), 0.0, repository)

        assertEquals(CableFailureCode.DERATING_INVALID, result.primaryFailureCode)
        assertEquals(
            "Installation method not specified in imported file",
            result.failReason
        )
        assertEquals(com.example.engine.SizingResultStatus.FAIL, result.resultStatus)
    }

    @Test
    fun testAlias_matchIsExactNotSubstring() {
        // A value merely containing a reference-method code must never be reinterpreted as one.
        assertNull(CableSizingEngine.unsupportedInstallationMessage("D1Laid in Ducts"))
        assertEquals(
            "Installation method 'b1' is not supported by the current ampacity catalog " +
                "(supported: Duct, Buried, Air/Tray).",
            CableSizingEngine.unsupportedInstallationMessage("  b1  ")
        )
        assertNull(CableSizingEngine.unsupportedInstallationMessage("Laid in Ducts"))
        assertNull(CableSizingEngine.unsupportedInstallationMessage("Laid Direct in Ground"))
        assertNull(CableSizingEngine.unsupportedInstallationMessage("Cable Tray"))
    }

    // ---------------------------------------------------------------------
    // N3 — the ampacity COLUMN must follow the canonical installation family
    // ---------------------------------------------------------------------

    /**
     * Mismatches collected across a whole test, so a single run reports EVERY alias instead of
     * aborting on the first one (needed to capture raw RED evidence for both D1 and D2).
     */
    private val n3Mismatches = mutableListOf<String>()

    private fun n3CheckDouble(message: String, expected: Double, actual: Double) {
        if (kotlin.math.abs(expected - actual) > 1e-9) {
            n3Mismatches += "$message — expected:<$expected> but was:<$actual>"
        }
    }

    private fun n3CheckString(message: String, expected: String, actual: String) {
        if (expected != actual) {
            n3Mismatches += "$message — expected:<$expected> but was:<$actual>"
        }
    }

    private fun n3AssertAllCollected(context: String) {
        assertTrue(
            "$context:\n" + n3Mismatches.joinToString("\n"),
            n3Mismatches.isEmpty()
        )
    }

    /**
     * Fixture that actually resolves in the loaded catalog (`Cu/XLPE/PVC - 4C`, typeCode 15,
     * 21 Elsewedy rows) so the circuit reaches candidate evaluation.
     *
     * The shared [circuitWithInstallation] fixture names a cableType that exists in neither
     * `cable_types_data.json` nor `cable_types.json`, so it stops at CATALOG_MISSING with
     * ratedI_table_A = 0.0 and never exercises the ampacity column at all.
     */
    private fun n3Circuit(installation: String, id: Int) = CircuitEntity(
        id = id,
        circuitName = "N3-$id",
        loadKVA = 500.0,
        pf = 0.8,
        phases = "3P+N",
        voltageV = 400,
        length_m = 50.0,
        installation = installation,
        cableType = "Cu/XLPE/PVC - 4C",
        manufacturer = "Elsewedy",
        parallelRuns = 1,
        noCircuits = 4,
        formation = "Flat",
        spacing = "Touching"
    )

    /**
     * Sizes the fixture circuit for [installation] and asserts the raw catalog column that was
     * actually used, so the mapping is proven through the public [CableSizingEngine.sizeCircuit]
     * path rather than by reaching into the private selection helper.
     *
     * [expectedFamily] names the family the value must collapse to after canonicalisation:
     * `Air`, `Duct` or `Buried`.
     */
    private fun assertAmpacityColumn(installation: String, expectedFamily: String, id: Int) {
        val result = CableSizingEngine.sizeCircuit(n3Circuit(installation, id), 0.0, repository)

        if (result.ratedI_table_A <= 0.0) {
            n3Mismatches += "'$installation' must be sized against a catalog column, got status=" +
                "'${result.status}', failReason='${result.failReason}'"
            return
        }

        val selectedSize = result.cableSize_mm2.substringBefore(" ").toDouble()
        val typeCode = repository.cableTypesData
            .firstOrNull { it.cableType.trim().equals("Cu/XLPE/PVC - 4C", ignoreCase = true) }
            ?.typeCode
            ?: Catalog.cableTypes.first {
                it.cableType.trim().equals("Cu/XLPE/PVC - 4C", ignoreCase = true)
            }.code
        val row = repository.cableProducts.firstOrNull {
            it.mfg.trim().equals("Elsewedy", ignoreCase = true) &&
                it.typeCode == typeCode &&
                it.size == selectedSize
        } ?: throw AssertionError(
            "No catalog row for size=${result.cableSize_mm2}, typeCode=$typeCode"
        )

        val expectedAmpacity = when (expectedFamily) {
            "Air" -> row.air
            "Duct" -> row.duct
            "Buried" -> row.gnd
            else -> throw AssertionError("Unknown expected family '$expectedFamily'")
        }

        println(
            "N3COLUMN|installation=$installation|family=$expectedFamily|size=${result.cableSize_mm2}|" +
                "ratedI_table_A=${result.ratedI_table_A}|air=${row.air}|duct=${row.duct}|gnd=${row.gnd}|" +
                "status=${result.status}"
        )

        n3CheckDouble(
            "'$installation' must take the $expectedFamily column at size=${result.cableSize_mm2} " +
                "(air=${row.air}, duct=${row.duct}, gnd=${row.gnd})",
            expectedAmpacity,
            result.ratedI_table_A
        )
    }

    /**
     * N3 RED/GREEN proof: the IEC reference-method aliases must produce the identical engineering
     * verdict as the catalog family they alias, because every derating lookup is already
     * canonicalised before use.
     */
    @Test
    fun testAmpacityColumn_d1AndD2MatchTheirCanonicalFamilyResults() {
        val ducts = CableSizingEngine.sizeCircuit(n3Circuit("Laid in Ducts", 811), 0.0, repository)
        val d1 = CableSizingEngine.sizeCircuit(n3Circuit("D1", 812), 0.0, repository)
        println(
            "N3PAIR|reference=Laid in Ducts|alias=D1|ratedI_ref=${ducts.ratedI_table_A}|" +
                "ratedI_alias=${d1.ratedI_table_A}|size_ref=${ducts.cableSize_mm2}|size_alias=${d1.cableSize_mm2}"
        )
        n3CheckDouble(
            "D1 must take the same ampacity column as 'Laid in Ducts'",
            ducts.ratedI_table_A, d1.ratedI_table_A
        )
        n3CheckString(
            "D1 must select the same cable size as 'Laid in Ducts'",
            ducts.cableSize_mm2, d1.cableSize_mm2
        )
        n3CheckDouble(
            "D1 must produce the same derated capacity as 'Laid in Ducts'",
            ducts.capacity_derated_Iz_A, d1.capacity_derated_Iz_A
        )

        val ground = CableSizingEngine.sizeCircuit(n3Circuit("Laid Direct in Ground", 813), 0.0, repository)
        val d2 = CableSizingEngine.sizeCircuit(n3Circuit("D2", 814), 0.0, repository)
        println(
            "N3PAIR|reference=Laid Direct in Ground|alias=D2|ratedI_ref=${ground.ratedI_table_A}|" +
                "ratedI_alias=${d2.ratedI_table_A}|size_ref=${ground.cableSize_mm2}|size_alias=${d2.cableSize_mm2}"
        )
        n3CheckDouble(
            "D2 must take the same ampacity column as 'Laid Direct in Ground'",
            ground.ratedI_table_A, d2.ratedI_table_A
        )
        n3CheckString(
            "D2 must select the same cable size as 'Laid Direct in Ground'",
            ground.cableSize_mm2, d2.cableSize_mm2
        )

        // Depth and soil derating now resolve through the same canonical family (N6), so the
        // derated capacity is part of the alias invariant too; it is asserted directly here and
        // factor-by-factor in testDerating_d2MatchesLaidDirectInGroundForDepthSoilAndDeratedCapacity.
        n3CheckDouble(
            "D2 must produce the same derated capacity as 'Laid Direct in Ground'",
            ground.capacity_derated_Iz_A, d2.capacity_derated_Iz_A
        )

        n3AssertAllCollected("D1/D2 must match their canonical family results")
    }

    /**
     * Explicit canonical-family coverage for all six owner-approved installation values, asserting
     * the exact catalog column used for each.
     */
    @Test
    fun testAmpacityColumn_sixInstallationValuesFollowThreeCanonicalFamilies() {
        assertAmpacityColumn("Laid in Free Air", "Air", 801)
        assertAmpacityColumn("Cable Tray", "Air", 802)
        assertAmpacityColumn("Laid in Ducts", "Duct", 803)
        assertAmpacityColumn("D1", "Duct", 804)
        assertAmpacityColumn("Laid Direct in Ground", "Buried", 805)
        assertAmpacityColumn("D2", "Buried", 806)

        n3AssertAllCollected("All six installation values must follow their canonical family")
    }

    // ---------------------------------------------------------------------
    // N6 — depth/soil derating must consume the same canonical family
    // ---------------------------------------------------------------------

    /**
     * N6 RED/GREEN proof of the alias invariant on the derating side of the SAME calculation:
     * `D2` and `Laid Direct in Ground` must be indistinguishable to every installation-dependent
     * computation the engine performs — depth factor, soil factor, total derating, rated table
     * current, derated Iz and the selected result — not only to the ampacity column.
     *
     * Both sides get identical depth/soil inputs ([CircuitEntity] defaults `burialDepth_m = 0.6 m`,
     * `soilRes = 125 ohm.m`), so the installation value itself is the only variable.
     */
    @Test
    fun testDerating_d2MatchesLaidDirectInGroundForDepthSoilAndDeratedCapacity() {
        val depth = 0.6
        val soil = 125

        val depthRef = CableSizingEngine.getDepthDerating("Laid Direct in Ground", depth)
        val depthD2 = CableSizingEngine.getDepthDerating("D2", depth)
        val depthBuried = CableSizingEngine.getDepthDerating("Buried", depth)
        val soilRef = CableSizingEngine.getSoilDerating("Laid Direct in Ground", soil)
        val soilD2 = CableSizingEngine.getSoilDerating("D2", soil)
        val soilBuried = CableSizingEngine.getSoilDerating("Buried", soil)

        println(
            "N6FACTOR|depth=ref:$depthRef|d2:$depthD2|buried:$depthBuried|" +
                "soil=ref:$soilRef|d2:$soilD2|buried:$soilBuried"
        )
        n3CheckDouble("Depth derating for 'D2' must match 'Laid Direct in Ground'", depthRef, depthD2)
        n3CheckDouble(
            "Depth derating for canonical 'Buried' must match 'Laid Direct in Ground'",
            depthRef, depthBuried
        )
        n3CheckDouble("Soil derating for 'D2' must match 'Laid Direct in Ground'", soilRef, soilD2)
        n3CheckDouble(
            "Soil derating for canonical 'Buried' must match 'Laid Direct in Ground'",
            soilRef, soilBuried
        )

        val ground = CableSizingEngine.sizeCircuit(n3Circuit("Laid Direct in Ground", 819), 0.0, repository)
        val d2 = CableSizingEngine.sizeCircuit(n3Circuit("D2", 820), 0.0, repository)

        println(
            "N6PAIR|reference=Laid Direct in Ground|alias=D2|" +
                "cTotalDerating_ref=${ground.cTotalDerating}|cTotalDerating_alias=${d2.cTotalDerating}|" +
                "ratedI_ref=${ground.ratedI_table_A}|ratedI_alias=${d2.ratedI_table_A}|" +
                "Iz_ref=${ground.capacity_derated_Iz_A}|Iz_alias=${d2.capacity_derated_Iz_A}|" +
                "size_ref=${ground.cableSize_mm2}|size_alias=${d2.cableSize_mm2}|" +
                "status_ref=${ground.status}|status_alias=${d2.status}"
        )
        n3CheckDouble(
            "Total derating for 'D2' must match 'Laid Direct in Ground'",
            ground.cTotalDerating, d2.cTotalDerating
        )
        n3CheckDouble(
            "Rated table current for 'D2' must match 'Laid Direct in Ground'",
            ground.ratedI_table_A, d2.ratedI_table_A
        )
        n3CheckDouble(
            "Derated Iz for 'D2' must match 'Laid Direct in Ground'",
            ground.capacity_derated_Iz_A, d2.capacity_derated_Iz_A
        )
        n3CheckString(
            "Selected size for 'D2' must match 'Laid Direct in Ground'",
            ground.cableSize_mm2, d2.cableSize_mm2
        )
        n3CheckString(
            "Sizing status for 'D2' must match 'Laid Direct in Ground'",
            ground.status, d2.status
        )

        n3AssertAllCollected(
            "D2 must be an exact alias of 'Laid Direct in Ground' for every installation-dependent " +
                "sizing input"
        )
    }
}
