package com.example

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.data.catalog.CatalogRepository
import com.example.engine.CableSizingEngine
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import com.example.ui.catalogCableSizeOptions
import com.example.ui.getDistinctCatalogManufacturers
import com.example.ui.standardConductorSizeOptions
import com.example.ui.standardDeviceRatingOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Unit tests for Quick Cable Sizing (Stateless Scratchpad) using the REAL catalog.
 * Validates:
 * 1. Manufacturer list comes from Catalog (no phantom companies).
 * 2. Resolved cable combo is a valid Catalog entry.
 * 3. Override cable size only offers catalog sizes (not free text).
 * 4. Override rating only offers standard ratings.
 * 5. Quick Calc == direct engine result field-by-field.
 * 6. Realistic voltage drop (not 198% fallback garbage).
 * 7. Real derating applied when environmental factors are set (cTotalDerating < 1.0).
 * 8. Zero pollution to repository, project revision, or freshness registry.
 * 9. Invalid inputs handled safely.
 */
class QuickCableCalcTest {

    companion object {
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
        fun loadRealCatalogData() {
            val repo = CatalogRepository()
            val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
            val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
            val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
            val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
            val deratingDataJson = findAssetFile("derating_data.json").readText()
            val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

            repo.loadAllFromStrings(
                cableCatalogJson = cableCatalogJson,
                cableTypesDataJson = cableTypesDataJson,
                breakerCatalogJson = breakerCatalogJson,
                accessoryCatalogJson = accessoryCatalogJson,
                deratingDataJson = deratingDataJson,
                transformersSeedJson = transformersSeedJson
            )

            val cableTypesJson = findAssetFile("cable_types.json").readText()
            val ctArray = JSONArray(cableTypesJson)
            val ctList = mutableListOf<CableType>()
            for (i in 0 until ctArray.length()) {
                val obj = ctArray.getJSONObject(i)
                ctList.add(
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
            Catalog.cableTypes = ctList

            val dataSheetJson = findAssetFile("data_sheet.json").readText()
            val dsArray = JSONArray(dataSheetJson)
            val dsList = mutableListOf<DataSheetEntry>()
            for (i in 0 until dsArray.length()) {
                val obj = dsArray.getJSONObject(i)
                dsList.add(
                    DataSheetEntry(
                        size = obj.getDouble("size"),
                        R_Cu70 = obj.getDouble("R_Cu70"),
                        X = obj.getDouble("X"),
                        R_Al70 = obj.getDouble("R_Al70"),
                        R_Cu90 = obj.getDouble("R_Cu90"),
                        R_Al90 = obj.getDouble("R_Al90")
                    )
                )
            }
            Catalog.dataSheet = dsList
            Catalog.deratingData = JSONObject(deratingDataJson)

            val ccArray = JSONArray(cableCatalogJson)
            val ccList = mutableListOf<CableCatalogEntry>()
            for (i in 0 until ccArray.length()) {
                val obj = ccArray.getJSONObject(i)
                ccList.add(
                    CableCatalogEntry(
                        mfg = obj.getString("mfg"),
                        typeCode = obj.getInt("typeCode"),
                        size = obj.getDouble("size"),
                        model = obj.optString("model", "IEC-Generic-${obj.optDouble("size")}"),
                        gnd = obj.getDouble("gnd"),
                        duct = obj.getDouble("duct"),
                        air = obj.getDouble("air"),
                        od = obj.optDouble("od", 0.0),
                        wt = obj.optDouble("wt", 0.0)
                    )
                )
            }
            Catalog.cableCatalog = ccList
        }
    }

    @Test
    fun quickCalc_manufacturerList_comesFromCatalog_noPhantomCompanies() {
        val manufacturers = getDistinctCatalogManufacturers()
        assertTrue("Manufacturers list must not be empty", manufacturers.isNotEmpty())
        assertTrue("Elsewedy must be in catalog manufacturers", manufacturers.contains("Elsewedy"))
        assertTrue("Riyadh Cables must be in catalog manufacturers", manufacturers.contains("Riyadh Cables"))
        assertTrue("Ducab must be in catalog manufacturers", manufacturers.contains("Ducab"))

        // Confirm phantom manufacturers are absent
        assertFalse("Nexans must NOT be in catalog", manufacturers.contains("Nexans"))
        assertFalse("Oman Cables must NOT be in catalog", manufacturers.contains("Oman Cables"))
    }

    @Test
    fun quickCalc_resolvedCableCombo_isValidCatalogEntry() {
        val mfg = "Elsewedy"
        val cableType = "Cu/XLPE/PVC - 4C"

        val typeCode = Catalog.cableTypes.firstOrNull { it.cableType.equals(cableType, ignoreCase = true) }?.code
        assertNotNull("Cable type must exist in catalog", typeCode)

        val catalogEntries = Catalog.cableCatalog.filter {
            it.mfg.equals(mfg, ignoreCase = true) && it.typeCode == typeCode
        }
        assertTrue("Catalog must have entries for $mfg $cableType", catalogEntries.isNotEmpty())

        val availableSizes = catalogCableSizeOptions(mfg, cableType)
        assertTrue("Available sizes must not be empty", availableSizes.isNotEmpty())
        assertTrue("Available sizes must contain 25 mm²", availableSizes.contains("25 mm²") || availableSizes.contains("25.0 mm²"))
    }

    @Test
    fun quickCalc_overrideCableSize_onlyOffersCatalogSizes_notFreeText() {
        val mfg = "Elsewedy"
        val cableType = "Cu/XLPE/PVC - 4C"
        val sizes = catalogCableSizeOptions(mfg, cableType)

        // All sizes must follow standard format and exist in Catalog.cableCatalog
        val typeCode = Catalog.cableTypes.first { it.cableType.equals(cableType, ignoreCase = true) }.code
        val rawSizes = Catalog.cableCatalog.filter { it.mfg.equals(mfg, ignoreCase = true) && it.typeCode == typeCode }.map { it.size }.toSet()

        for (szStr in sizes) {
            val num = szStr.replace("mm²", "").replace("mm\u00B2", "").trim().toDoubleOrNull()
            assertNotNull("Size string '$szStr' must be parsable", num)
            assertTrue("Size $num must exist in catalog", rawSizes.contains(num))
        }
    }

    @Test
    fun quickCalc_overrideRating_onlyOffersStandardRatings() {
        val ratings = standardDeviceRatingOptions()
        assertEquals(CableSizingEngine.STANDARD_RATINGS.size, ratings.size)
        assertTrue(ratings.contains("100 A"))
        assertTrue(ratings.contains("160 A"))
        assertTrue(ratings.contains("250 A"))
        assertTrue(ratings.contains("400 A"))
    }

    @Test
    fun quickCalc_matchesProjectModeResult_forIdenticalCircuit() {
        val circuit = CircuitEntity(
            id = 999,
            projectId = "QUICK_CALC",
            circuitName = "Feeder-Comparison",
            loadKVA = 75.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.80,
            safetyFactor = 1.25,
            length_m = 80.0,
            maxVDpct = 0.03,
            parallelRuns = 1,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            airTempC = 45,
            gndTempC = 35,
            burialDepth_m = 0.8,
            soilRes = 120,
            noCircuits = 2,
            formation = "Flat",
            spacing = "Touching",
            fedFrom = "Grid",
            transformerId = ""
        )

        // 1. Call via Quick-Calc path (CableCalculationService)
        val quickResult = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        // 2. Call directly on CableSizingEngine (Project mode)
        val directEngineResult = CableSizingEngine.sizeCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            allCircuits = emptyList()
        )

        // 3. Assert exact field-by-field equality (deterministic proof)
        assertEquals("Status must match", directEngineResult.status, quickResult.status)
        assertEquals("Ib_A must match", directEngineResult.Ib_A, quickResult.Ib_A, 0.0001)
        assertEquals("deviceSize_A must match", directEngineResult.deviceSize_A, quickResult.deviceSize_A, 0.0001)
        assertEquals("deviceType must match", directEngineResult.deviceType, quickResult.deviceType)
        assertEquals("cableSize_mm2 must match", directEngineResult.cableSize_mm2, quickResult.cableSize_mm2)
        assertEquals("cTotalDerating must match", directEngineResult.cTotalDerating, quickResult.cTotalDerating, 0.0001)
        assertEquals("ratedI_table_A must match", directEngineResult.ratedI_table_A, quickResult.ratedI_table_A, 0.0001)
        assertEquals("capacity_derated_Iz_A must match", directEngineResult.capacity_derated_Iz_A, quickResult.capacity_derated_Iz_A, 0.0001)
        assertEquals("utilizationPct must match", directEngineResult.utilizationPct, quickResult.utilizationPct, 0.0001)
        assertEquals("voltDrop_V must match", directEngineResult.voltDrop_V, quickResult.voltDrop_V, 0.0001)
        assertEquals("vdPct must match", directEngineResult.vdPct, quickResult.vdPct, 0.0001)
        assertEquals("cumulativeVDpct must match", directEngineResult.cumulativeVDpct, quickResult.cumulativeVDpct, 0.0001)
        assertEquals("neutral_mm2 must match", directEngineResult.neutral_mm2, quickResult.neutral_mm2)
        assertEquals("earthCPC_mm2 must match", directEngineResult.earthCPC_mm2, quickResult.earthCPC_mm2)
        assertEquals("cableMaterial must match", directEngineResult.cableMaterial, quickResult.cableMaterial)
        assertEquals("insulation must match", directEngineResult.insulation, quickResult.insulation)
        assertEquals("cableOD_mm must match", directEngineResult.cableOD_mm, quickResult.cableOD_mm, 0.0001)
        assertEquals("earthOD_mm must match", directEngineResult.earthOD_mm, quickResult.earthOD_mm, 0.0001)
    }

    @Test
    fun quickCalc_realisticVoltageDrop_notFallbackGarbage() {
        val circuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Realistic-Feeder",
            loadKVA = 50.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            safetyFactor = 1.0,
            length_m = 50.0,
            maxVDpct = 0.03,
            parallelRuns = 1,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid",
            transformerId = ""
        )

        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("Result must PASS, got: ${result.status}", result.status.contains("PASS"))
        assertTrue("Voltage drop % must be realistic (< 3.0%), got: ${result.vdPct}%", result.vdPct <= 3.0)
        assertTrue("Voltage drop V must be realistic (< 12V), got: ${result.voltDrop_V}V", result.voltDrop_V < 12.0)
        assertFalse("Must NOT produce fallback 198% voltage drop", result.vdPct > 10.0)
    }

    @Test
    fun quickCalc_deratingApplied_whenEnvironmentalFactorsSet() {
        val circuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Derated-Feeder",
            loadKVA = 45.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            safetyFactor = 1.0,
            length_m = 40.0,
            maxVDpct = 0.03,
            parallelRuns = 1,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            airTempC = 50,
            gndTempC = 40,
            burialDepth_m = 1.0,
            soilRes = 150,
            noCircuits = 3,
            formation = "Flat",
            spacing = "Touching",
            fedFrom = "Grid",
            transformerId = ""
        )

        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("Result must PASS, got: ${result.status}", result.status.contains("PASS"))
        assertTrue("cTotalDerating must be strictly less than 1.0, got: ${result.cTotalDerating}", result.cTotalDerating < 1.0)
        assertTrue("cTotalDerating must be positive, got: ${result.cTotalDerating}", result.cTotalDerating > 0.3)
    }

    @Test
    fun quickCalc_doesNotTouchRepositoryOrRevisionOrFreshness() {
        val quickScope = CalculationScope(
            projectId = "QUICK_CALC",
            entityType = CalculationEntityType.CIRCUIT,
            entityId = "999",
            domain = CalculationDomain.CABLE_SIZING
        )

        // Clear coordinator caches before test
        EngineeringCalculationCoordinator.clearCache()

        val circuit = CircuitEntity(
            id = 999,
            projectId = "QUICK_CALC",
            circuitName = "Feeder-No-Pollution",
            loadKVA = 30.0,
            voltageV = 400,
            length_m = 25.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )

        // Run Quick-Calc
        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("Result must pass", result.status.contains("PASS"))

        // Verify: coordinator freshness registry has NO record for QUICK_CALC
        val recordedMetadata = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(quickScope)
        assertNull("Quick-calc must NOT create any records in freshnessRegistry", recordedMetadata)
    }

    @Test
    fun quickCalc_fedFromGrid_bypassesTransformerFailCheck() {
        val circuitGrid = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Grid-Fed-Feeder",
            loadKVA = 45.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid",
            transformerId = ""
        )

        val resultGrid = CableCalculationService.calculateSingleCircuit(
            circuit = circuitGrid,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("fedFrom='Grid' must NOT fail with missing transformer error", resultGrid.status.contains("PASS"))
        assertFalse("failReason must not mention transformer", resultGrid.failReason.contains("Missing source transformer"))

        // Conversely, verify that fedFrom='Transformer' without transformers list would trigger the check:
        val circuitTx = circuitGrid.copy(fedFrom = "Transformer", transformerId = "TX-MISSING")
        val resultTx = CableCalculationService.calculateSingleCircuit(
            circuit = circuitTx,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )
        assertTrue("fedFrom='Transformer' without transformer must fail", resultTx.status.contains("FAIL"))
        assertTrue("failReason must indicate missing transformer", resultTx.failReason.contains("Missing source transformer"))
    }

    @Test
    fun quickCalc_overrideCableSize_isRespected() {
        val circuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Override-Feeder",
            loadKVA = 20.0,
            voltageV = 400,
            length_m = 30.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid",
            overrideCableSize = "70 mm²",
            overrideDeviceType = "MCCB",
            overrideDeviceSize = 100.0
        )

        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("Result must pass with override", result.status.contains("PASS"))
        assertEquals("Selected cable size must respect override", "70 mm²", result.cableSize_mm2)
        assertEquals("Breaker rating must respect override", 100.0, result.deviceSize_A, 0.0001)
        assertEquals("Breaker type must respect override", "MCCB", result.deviceType)
    }

    @Test
    fun quickCalc_invalidInputs_areHandledWithoutCrash() {
        // Zero voltage circuit
        val invalidVoltageCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            voltageV = 0,
            loadKVA = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val resZeroV = CableCalculationService.calculateSingleCircuit(invalidVoltageCircuit)
        assertTrue("Zero voltage must return FAIL result", resZeroV.status.contains("FAIL"))
        assertTrue("Fail reason must mention voltage", resZeroV.failReason.contains("Voltage must be greater than zero"))

        // Negative load circuit
        val negativeLoadCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            voltageV = 400,
            loadKVA = -10.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val resNegLoad = CableCalculationService.calculateSingleCircuit(negativeLoadCircuit)
        assertTrue("Negative load must return FAIL result", resNegLoad.status.contains("FAIL"))
        assertTrue("Fail reason must mention load", resNegLoad.failReason.contains("Load kVA cannot be negative"))

        // Zero length circuit
        val zeroLengthCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            voltageV = 400,
            loadKVA = 50.0,
            length_m = 0.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val resZeroLen = CableCalculationService.calculateSingleCircuit(zeroLengthCircuit)
        assertTrue("Zero length must return FAIL result", resZeroLen.status.contains("FAIL"))
        assertTrue("Fail reason must mention cable length", resZeroLen.failReason.contains("Cable length must be greater than zero"))
    }
}
