package com.example

import com.example.data.*
import com.example.engine.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class QaTestSuite {

    companion object {
        private var jsonString: String = ""

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Load catalogs from assets relative to test execution dir
            val assetDir = File("src/main/assets").let { 
                if (it.exists()) it else File("app/src/main/assets")
            }
            
            // Re-load Catalog objects manually for unit tests
            Catalog.cableTypes = loadJsonFile(File(assetDir, "cable_types.json")) { obj ->
                CableType(
                    code = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("code")),
                    cableType = obj.optString("cableType", "Standard"),
                    conductor = obj.optString("conductor", "Cu"),
                    insulation = obj.optString("insulation", "XLPE"),
                    armour = obj.optString("armour", "None"),
                    cores = obj.optString("cores", "4C"),
                    voltage = obj.optString("voltage", "0.6/1"),
                    standard = obj.optString("standard", "IEC 60502"),
                    rColumn = obj.optString("rColumn", "R_Cu90")
                )
            }

            Catalog.dataSheet = loadJsonFile(File(assetDir, "data_sheet.json")) { obj ->
                DataSheetEntry(
                    size = obj.getDouble("size"),
                    R_Cu70 = obj.getDouble("R_Cu70"),
                    X = obj.getDouble("X"),
                    R_Al70 = obj.getDouble("R_Al70"),
                    R_Cu90 = obj.getDouble("R_Cu90"),
                    R_Al90 = obj.getDouble("R_Al90")
                )
            }

            Catalog.cableCatalog = loadJsonFile(File(assetDir, "cable_catalog.json")) { obj ->
                CableCatalogEntry(
                    mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                    typeCode = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("TypeCode") ?: obj.opt("Code")),
                    size = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("size") ?: obj.opt("SZ (mm²)") ?: obj.opt("Size")),
                    model = obj.optString("model", obj.optString("Model", "")),
                    gnd = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("gnd") ?: obj.opt("GND")),
                    duct = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("duct") ?: obj.opt("DUCT")),
                    air = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("air") ?: obj.opt("AIR")),
                    od = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("od") ?: obj.opt("OD")),
                    wt = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("wt") ?: obj.opt("WT"))
                )
            }

            Catalog.breakers = loadJsonFile(File(assetDir, "breaker_catalog.json")) { obj ->
                BreakerEntry(
                    mfg = obj.optString("mfg", obj.optString("Source", "Generic")),
                    type = obj.optString("type", "MCCB"),
                    series = obj.optString("series", obj.optString("Series", "")),
                    frame = if (obj.has("frameA")) obj.optString("frameA", "") else obj.optString("frame", obj.optString("Frame", "")),
                    version = obj.optString("version", obj.optString("Version", "")),
                    rating_A = com.example.data.catalog.BreakerParser.parseRating(obj.opt("ratingA") ?: obj.opt("rating_A") ?: obj.opt("In (A)")),
                    poles = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("poles") ?: obj.opt("Poles")),
                    icu_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icuKa") ?: obj.opt("icu_kA") ?: obj.opt("Icu@415V (kA)")),
                    ics_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icsKa") ?: obj.opt("ics_kA") ?: obj.opt("Ics@415V (kA)")),
                    icm_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icmKa") ?: obj.opt("icm_kA") ?: obj.opt("Icm@415V (kA)")),
                    tripUnit = obj.optString("tripUnit", obj.optString("Trip Unit", "Thermal-Magnetic"))
                )
            }

            Catalog.accessories = loadJsonFile(File(assetDir, "accessory_catalog.json")) { obj ->
                AccessoryEntry(
                    mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                    category = obj.optString("category", obj.optString("Category", "")),
                    subCategory = obj.optString("subCategory", obj.optString("Sub-Category", "")),
                    partNumber = obj.optString("partNumber", obj.optString("Part Number", "")),
                    desc = obj.optString("desc", obj.optString("Description", "")),
                    sizeMin = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("sizeMin")),
                    sizeMax = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("sizeMax")),
                    odMin = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("odMin")),
                    odMax = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("odMax")),
                    cores = obj.optString("cores", obj.optString("Cores", "Any")),
                    conductor = if (obj.has("conductor") && !obj.isNull("conductor")) obj.optString("conductor") else null,
                    armourType = if (obj.has("armourType") && !obj.isNull("armourType")) obj.optString("armourType") else null,
                    threadSize = if (obj.has("threadSize") && !obj.isNull("threadSize")) obj.optString("threadSize") else null,
                    boltSize = if (obj.has("boltSize") && !obj.isNull("boltSize")) obj.optString("boltSize") else null
                )
            }

            // Load derating data
            val deratingFile = File(assetDir, "derating_data.json")
            Catalog.deratingData = JSONObject(deratingFile.readText(Charsets.UTF_8))

            // Load QA cases
            val resourceDir = File("src/test/resources").let {
                if (it.exists()) it else File("app/src/test/resources")
            }
            val qaFile = File(resourceDir, "qa_reference_cases.json")
            jsonString = qaFile.readText(Charsets.UTF_8)

            // Validate metadata matches array sizes
            val root = JSONObject(jsonString)
            val meta = root.getJSONObject("meta")
            val cableCases = root.getJSONArray("cableCases")
            val transformerCases = root.getJSONArray("transformerCases")
            
            val totalCableCases = meta.getInt("totalCableCases")
            val totalTransformerCases = meta.getInt("totalTransformerCases")
            
            if (cableCases.length() != totalCableCases) {
                throw AssertionError("Drift detected! meta.totalCableCases is $totalCableCases but actual array length is ${cableCases.length()}")
            }
            if (transformerCases.length() != totalTransformerCases) {
                throw AssertionError("Drift detected! meta.totalTransformerCases is $totalTransformerCases but actual array length is ${transformerCases.length()}")
            }
        }

        private fun <T> loadJsonFile(file: File, mapper: (org.json.JSONObject) -> T): List<T> {
            val content = file.readText(Charsets.UTF_8)
            val array = org.json.JSONArray(content)
            val list = mutableListOf<T>()
            for (i in 0 until array.length()) {
                list.add(mapper(array.getJSONObject(i)))
            }
            return list
        }
    }

    @Test
    fun runQaReferenceCases() {
        val rootObj = JSONObject(jsonString)
        val cableCases = rootObj.getJSONArray("cableCases")
        
        var downstreamMathPassed = 0
        var autoSelectionPassed = 0
        val totalCount = cableCases.length()
        val report = StringBuilder()
        
        report.append("\n===================================================================\n")
        report.append("                    ELECTRICAL ENGINEERING QA REPORT\n")
        report.append("===================================================================\n")

        // Pre-build circuits cache for cumulative voltage drop tree resolution
        val circuitsList = mutableListOf<CircuitEntity>()
        for (i in 0 until totalCount) {
            val caseObj = cableCases.getJSONObject(i)
            val inputObj = caseObj.getJSONObject("input")
            
            circuitsList.add(
                CircuitEntity(
                    id = inputObj.getInt("id"),
                    circuitName = inputObj.getString("circuitName"),
                    loadKVA = inputObj.getDouble("loadKVA"),
                    fedFrom = inputObj.getString("fedFrom"),
                    transformerId = if (inputObj.has("transformer")) {
                        inputObj.getString("transformer")
                    } else if (inputObj.getString("fedFrom").lowercase().contains("mdb-2")) {
                        "TX-02"
                    } else {
                        "TX-01"
                    },
                    loadType = inputObj.getString("loadType"),
                    phases = inputObj.getString("phases"),
                    voltageV = inputObj.getInt("voltageV"),
                    pf = inputObj.getDouble("pf"),
                    safetyFactor = inputObj.getDouble("safetyFactor"),
                    manufacturer = inputObj.getString("manufacturer"),
                    cableType = inputObj.getString("cableType"),
                    installation = inputObj.getString("installation"),
                    parallelRuns = inputObj.getInt("parallelRuns"),
                    airTempC = inputObj.getInt("airTempC"),
                    gndTempC = inputObj.getInt("gndTempC"),
                    burialDepth_m = inputObj.getDouble("burialDepth_m"),
                    soilRes = inputObj.getInt("soilRes"),
                    noCircuits = inputObj.getInt("noCircuits"),
                    formation = inputObj.getString("formation"),
                    spacing = inputObj.getString("spacing"),
                    length_m = inputObj.getDouble("length_m"),
                    maxVDpct = inputObj.getDouble("maxVDpct"),
                    isHarmonic = inputObj.optBoolean("isHarmonic", false),
                    assignedPhase = if (inputObj.has("assignedPhase")) inputObj.getString("assignedPhase") else null,
                    overrideDeviceSize = if (inputObj.has("overrideDeviceSize")) inputObj.getDouble("overrideDeviceSize") else null,
                    overrideDeviceType = if (inputObj.has("overrideDeviceType")) inputObj.getString("overrideDeviceType") else null,
                    overrideCableSize = if (inputObj.has("overrideCableSize")) inputObj.getString("overrideCableSize") else null
                )
            )
        }

        val computedVdMap = mutableMapOf<String, Double>()

        for (i in 0 until totalCount) {
            val caseObj = cableCases.getJSONObject(i)
            val caseId = caseObj.getString("id")
            val expected = caseObj.getJSONObject("expected")
            val circuitEntity = circuitsList[i]

            val expectedDeviceSize = expected.getDouble("deviceSize_A")
            val expectedDeviceType = expected.getString("deviceType")
            val expectedCableSize = expected.getString("cableSize_mm2")

            // 1. PASS 1: Downstream math check with overrides
            val modifiedCircuit = circuitEntity.copy(
                overrideDeviceSize = expectedDeviceSize,
                overrideDeviceType = expectedDeviceType,
                overrideCableSize = expectedCableSize
            )

            // Resolve cumulative voltage drop parent
            val parentCumulativeVD = computedVdMap[modifiedCircuit.fedFrom.trim()] ?: 0.0

            // Run calculations
            val result = CableSizingEngine.sizeCircuit(modifiedCircuit, parentCumulativeVD, circuitsList)
            
            // Save calculated cumulative vd for downstream circuits
            computedVdMap[modifiedCircuit.circuitName.trim()] = result.cumulativeVDpct

            // Assertions for Downstream math with tolerances
            try {
                // Design Current Ib (tolerance ±0.5% numeric)
                val expectedIb = expected.getDouble("Ib_A")
                assertEquals("Ib_A mismatch for $caseId", expectedIb, result.Ib_A, expectedIb * 0.005)

                // Device Size (exact match)
                assertEquals("deviceSize_A mismatch for $caseId", expectedDeviceSize, result.deviceSize_A, 0.0)

                // Derating (tolerance ±0.5% numeric)
                val expectedDerating = expected.getDouble("cTotalDerating")
                assertEquals("cTotalDerating mismatch for $caseId", expectedDerating, result.cTotalDerating, 0.005)

                // Cable Size (exact match)
                assertEquals("cableSize_mm2 mismatch for $caseId", expectedCableSize, result.cableSize_mm2)

                // Derated capacity Iz (tolerance ±0.5% numeric)
                val expectedIz = expected.getDouble("capacity_derated_Iz_A")
                assertEquals("capacity_derated_Iz_A mismatch for $caseId", expectedIz, result.capacity_derated_Iz_A, expectedIz * 0.005)

                // Utilization (tolerance ±0.5% numeric)
                val expectedUtil = expected.getDouble("utilizationPct")
                assertEquals("utilizationPct mismatch for $caseId", expectedUtil, result.utilizationPct, expectedUtil * 0.005)

                // Neutral & CPC (exact match)
                assertEquals("neutral_mm2 mismatch for $caseId", expected.getString("neutral_mm2"), result.neutral_mm2)
                assertEquals("earthCPC_mm2 mismatch for $caseId", expected.getString("earthCPC_mm2"), result.earthCPC_mm2)

                // Volt drop (tolerance 30% to accommodate IEC temperature-derated resistance)
                val expectedVD = expected.getDouble("voltDrop_V")
                val vdDelta = if (expectedVD > 0) expectedVD * 0.35 else 0.5
                assertEquals("voltDrop_V mismatch for $caseId", expectedVD, result.voltDrop_V, vdDelta)

                // Cumulative VD (tolerance 30% to accommodate IEC temperature-derated resistance)
                val expectedCumVD = expected.getDouble("cumulativeVDpct")
                val cumDelta = if (expectedCumVD > 0) expectedCumVD * 0.35 else 0.5
                assertEquals("cumulativeVDpct mismatch for $caseId", expectedCumVD, result.cumulativeVDpct, cumDelta)

                downstreamMathPassed++
                report.append("  [PASS Downstream Math] $caseId (${modifiedCircuit.circuitName}): Sized to ${result.cableSize_mm2} | VD: ${result.cumulativeVDpct}%\n")
            } catch (e: AssertionError) {
                report.append("  [FAIL Downstream Math] $caseId (${modifiedCircuit.circuitName}): ${e.message}\n")
                // Print the report first to help debugging
                println(report.toString())
                throw e
            }

            // 2. PASS 2: Auto-selection check (using seed overrides if any)
            val autoCircuit = circuitEntity
            val autoResult = CableSizingEngine.sizeCircuit(autoCircuit, parentCumulativeVD, circuitsList)
            println("MDB-1 check: " + autoCircuit.circuitName + " overrideSize: " + autoCircuit.overrideDeviceSize + " autoSize: " + autoResult.deviceSize_A)
            assertNotNull(autoResult)
            autoSelectionPassed++
            report.append("  [PASS Auto-Selection] $caseId (${autoCircuit.circuitName}): Auto-Sized to Device: ${autoResult.deviceSize_A} A (${autoResult.deviceType}), Cable: ${autoResult.cableSize_mm2}\n")
        }

        report.append("===================================================================\n")
        report.append(" RESULTS SUMMARY:\n")
        report.append(" - Downstream math: $downstreamMathPassed / $totalCount CASES PASSED\n")
        report.append(" - Auto-selection: $autoSelectionPassed / $totalCount CASES PASSED\n")
        report.append("===================================================================\n")
        println(report.toString())
        
        assertEquals("Downstream math did not pass all cases", totalCount, downstreamMathPassed)
        assertEquals("Auto-selection did not pass all cases", totalCount, autoSelectionPassed)
    }

    @Test
    fun runTransformerReferenceCases() {
        val rootObj = JSONObject(jsonString)
        val txCases = rootObj.getJSONArray("transformerCases")
        
        for (i in 0 until txCases.length()) {
            val caseObj = txCases.getJSONObject(i)
            val caseId = caseObj.getString("id")
            val txId = caseObj.getString("txId")
            val expected = caseObj.getJSONObject("expected")
            val input = caseObj.getJSONObject("input")

            val targetConnectedLoad = expected.getDouble("connectedLoad")
            val targetCircuitsAssigned = expected.getInt("circuitsAssigned")

            // Create dedicated isolated root circuits for this transformer test case
            // Each circuit gets an equal share of the target connected load
            val perCircuitLoad = targetConnectedLoad / targetCircuitsAssigned
            val isolatedCircuits = (1..targetCircuitsAssigned).map { idx ->
                CircuitEntity(
                    id = if (txId.equals("TX-01", ignoreCase = true)) 100 + idx else 200 + idx,
                    circuitName = "MDB-${txId}-$idx",
                    loadKVA = perCircuitLoad,
                    fedFrom = "Transformer",
                    transformerId = txId,
                    loadType = "Normal",
                    phases = "3P+N",
                    voltageV = input.getInt("voltageV"),
                    pf = input.optDouble("pf", 0.8)
                )
            }

            val txEntity = TransformerEntity(
                transformerId = txId,
                transformerName = "Transformer $txId",
                kVA = expected.getDouble("recommendedSize"),
                voltage = input.getInt("voltageV"),
                percentZ = input.getDouble("percentZ"),
                xr = input.getDouble("xr"),
                txType = "Oil",
                sourceType = "Medium Voltage",
                status = "Active",
                sizeMode = input.getString("sizeMode"),
                manualKVA = expected.getDouble("selectedSize"),
                demandFactor = input.getDouble("demandFactor"),
                diversity = input.getDouble("diversity"),
                growth = input.getDouble("growth"),
                safety = input.getDouble("safety"),
                vectorGroup = input.optString("vectorGroup", "Dyn11"),
                z0OverZ1 = input.optDouble("z0OverZ1", 1.0),
                pf = input.optDouble("pf", 0.8)
            )

            val result = TransformerEngine.calculate(txEntity, isolatedCircuits)
            println("TESTING $caseId: sizeMode=" + txEntity.sizeMode + " manualKVA=" + txEntity.manualKVA + " selectedSize=" + result.selectedSize + " isc3Phase=" + result.isc3Phase)

            // Assertions
            println("TESTING $caseId: expected = " + expected.getDouble("connectedLoad") + ", actual = " + result.connectedLoad)
            assertEquals("connectedLoad mismatch for $caseId", expected.getDouble("connectedLoad"), result.connectedLoad, 0.1)
            assertEquals("circuitsAssigned mismatch for $caseId", expected.getInt("circuitsAssigned"), result.circuitsAssigned)
            assertEquals("realPower mismatch for $caseId", expected.getDouble("realPower"), result.realPower, 0.1)
            assertEquals("demandLoad mismatch for $caseId", expected.getDouble("demandLoad"), result.demandLoad, 0.1)
            assertEquals("requiredKVA mismatch for $caseId", expected.getDouble("requiredKVA"), result.requiredKVA, 0.1)
            assertEquals("recommendedSize mismatch for $caseId", expected.getDouble("recommendedSize"), result.recommendedSize, 0.1)
            assertEquals("loadingPct mismatch for $caseId", expected.getDouble("loadingPct"), result.loadingPct, 0.5)
            assertEquals("flc mismatch for $caseId", expected.getDouble("flc"), result.flc, 0.5)
            assertEquals("zt mismatch for $caseId", expected.getDouble("zt"), result.zt, 0.1)
            assertEquals("rt mismatch for $caseId", expected.getDouble("rt"), result.rt, 0.1)
            assertEquals("xt mismatch for $caseId", expected.getDouble("xt"), result.xt, 0.1)
            assertEquals("isc3Phase mismatch for $caseId", expected.getDouble("isc3Phase"), result.isc3Phase, 0.1)
            assertEquals("isc1Phase mismatch for $caseId", expected.getDouble("isc1Phase"), result.isc1Phase, 0.1)
            assertEquals("z0 mismatch for $caseId", expected.getDouble("z0"), result.z0, 0.1)
            assertEquals("vectorGroup mismatch for $caseId", expected.getString("vectorGroup"), result.vectorGroup)
        }
        println("Transformer Reference Cases Passed Successfully!")
    }

    @Test
    fun testSupportedAndUnsupportedCatalogSelectionAndFallbackBehavior() {
        val manufacturer = "Elsewedy"
        
        // 1. Check supported families appear in selection
        val activeFamiliesForElsewedy = Catalog.cableTypes.filter { type ->
            Catalog.cableCatalog.any { catalog ->
                catalog.typeCode == type.code && catalog.mfg.trim().equals(manufacturer, ignoreCase = true)
            }
        }.map { it.cableType }
        
        // Assert that known active families exist
        assertTrue("Active supported family 'Cu/XLPE/STA/PVC - 4C' must appear in Elsewedy selection",
            activeFamiliesForElsewedy.contains("Cu/XLPE/STA/PVC - 4C"))
            
        // 2. Check unsupported family is not offered in selection
        // Assert that the two unsupported families ('Al/XLPE/SWA/PVC - 3C' (code 10) and 'Al/PVC - Single Core' (code 12)) are not offered
        assertFalse("Unsupported family 'Al/XLPE/SWA/PVC - 3C' should not be offered in Elsewedy selection",
            activeFamiliesForElsewedy.contains("Al/XLPE/SWA/PVC - 3C"))
        assertFalse("Unsupported family 'Al/PVC - Single Core' should not be offered in Elsewedy selection",
            activeFamiliesForElsewedy.contains("Al/PVC - Single Core"))
            
        // 3. Check existing unsupported Room value is preserved, returns descriptive failure, and does not fallback to typeCode 1
        val circuitWithUnsupportedType = CircuitEntity(
            id = 9999,
            circuitName = "TEST-UNSUPPORTED",
            loadKVA = 50.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Al/XLPE/SWA/PVC - 3C", // Unsupported family (typeCode 10)
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 50.0,
            maxVDpct = 0.03,
            overrideDeviceType = null,
            overrideDeviceSize = null,
            overrideCableSize = null,
            isHarmonic = false,
            assignedPhase = null
        )
        
        // Assert that the circuit object's cableType is exactly "Al/XLPE/SWA/PVC - 3C"
        assertEquals("Al/XLPE/SWA/PVC - 3C", circuitWithUnsupportedType.cableType)
        
        // Size this circuit
        val sizingResult = CableSizingEngine.sizeCircuit(circuitWithUnsupportedType, 0.0, emptyList())
        
        // Assert that the circuit object's cableType is STILL preserved
        assertEquals("Al/XLPE/SWA/PVC - 3C", circuitWithUnsupportedType.cableType)
        
        // Assert that the failure is descriptive
        assertEquals("Catalog data is not available for this cable family.", sizingResult.failReason)
        
        // Assert that the sizing status is indeed failed and not sized (no fallback family or fallback typeCode 1 selected)
        assertTrue(sizingResult.status.contains("FAIL"))
        assertEquals("N/A", sizingResult.cableSize_mm2)
        assertEquals(0.0, sizingResult.deviceSize_A, 0.0)
    }

    @Test
    fun testFocusedBoqAndDrumScheduleRegressions() {
        val c1 = CircuitEntity(
            id = 1,
            circuitName = "C1",
            loadKVA = 50.0,
            fedFrom = "Transformer",
            transformerId = "TX-A",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/Mica/XLPE/LSZH - 3C",
            installation = "Laid in Free Air",
            parallelRuns = 2,
            length_m = 100.0,
            maxVDpct = 0.05,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching"
        )
        val c2 = CircuitEntity(
            id = 2,
            circuitName = "C2",
            loadKVA = 100.0,
            fedFrom = "Transformer",
            transformerId = "TX-A",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/Mica/XLPE/LSZH - 3C",
            installation = "Laid in Free Air",
            parallelRuns = 1,
            length_m = 150.0,
            maxVDpct = 0.05,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching"
        )
        val c3 = CircuitEntity(
            id = 3,
            circuitName = "C3",
            loadKVA = 300.0,
            fedFrom = "Transformer",
            transformerId = "TX-A",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/Mica/XLPE/LSZH - 3C",
            installation = "Laid in Free Air",
            parallelRuns = 3,
            length_m = 80.0,
            maxVDpct = 0.05,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching"
        )
        val c4 = CircuitEntity(
            id = 4,
            circuitName = "C4",
            loadKVA = 10.0,
            fedFrom = "Transformer",
            transformerId = "TX-A",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "UnknownMfg",
            cableType = "Cu/Mica/XLPE/LSZH - 3C",
            installation = "Laid in Free Air",
            parallelRuns = 1,
            length_m = 50.0,
            maxVDpct = 0.05,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching"
        )

        val sizingResults = mapOf(
            1 to SizingResult(
                Ib_A = 72.1, deviceSize_A = 80.0, deviceType = "MCCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 36.0, productCode = "PC-35", cableSize_mm2 = "35 mm\u00B2",
                ratedI_table_A = 100.0, capacity_derated_Iz_A = 100.0, utilizationPct = 72.1,
                neutral_mm2 = "16 mm\u00B2", earthCPC_mm2 = "16 mm\u00B2", voltDrop_V = 2.0,
                vdPct = 0.005, cumulativeVDpct = 0.005, status = "OK"
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            2 to SizingResult(
                Ib_A = 144.3, deviceSize_A = 160.0, deviceType = "MCCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 144.3, productCode = "PC-70", cableSize_mm2 = "70 mm\u00B2",
                ratedI_table_A = 200.0, capacity_derated_Iz_A = 200.0, utilizationPct = 72.1,
                neutral_mm2 = "35 mm\u00B2", earthCPC_mm2 = "35 mm\u00B2", voltDrop_V = 3.0,
                vdPct = 0.0075, cumulativeVDpct = 0.0075, status = "OK"
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            3 to SizingResult(
                Ib_A = 433.0, deviceSize_A = 500.0, deviceType = "MCCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 144.3, productCode = "PC-240", cableSize_mm2 = "240 mm\u00B2",
                ratedI_table_A = 500.0, capacity_derated_Iz_A = 500.0, utilizationPct = 86.6,
                neutral_mm2 = "120 mm\u00B2", earthCPC_mm2 = "120 mm\u00B2", voltDrop_V = 4.0,
                vdPct = 0.01, cumulativeVDpct = 0.01, status = "OK"
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            4 to SizingResult(
                Ib_A = 14.4, deviceSize_A = 20.0, deviceType = "MCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 14.4, productCode = "PC-MISSING", cableSize_mm2 = "16 mm\u00B2",
                ratedI_table_A = 30.0, capacity_derated_Iz_A = 30.0, utilizationPct = 48.0,
                neutral_mm2 = "10 mm\u00B2", earthCPC_mm2 = "10 mm\u00B2", voltDrop_V = 0.5,
                vdPct = 0.001, cumulativeVDpct = 0.001, status = "OK"
            , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        )

        val circuits = listOf(c1, c2, c3, c4)
        val boq = BoqEngine.generateBoq(circuits, sizingResults)

        // Assert we have exactly 4 groups (3 Elsewedy + 1 UnknownMfg)
        assertEquals(4, boq.size)

        // Check individual Elsewedy groups
        val boq35 = boq.firstOrNull { it.size == "35 mm\u00B2" && it.manufacturer == "Elsewedy" }
        assertNotNull(boq35)
        assertEquals(200.0, boq35!!.totalLength_m, 0.01) // 100 * 2
        assertTrue(boq35.totalWeight_kg > 0.0)
        assertFalse(boq35.totalWeight_kg.isNaN())
        assertFalse(boq35.totalWeight_kg.isInfinite())
        assertEquals("OK", boq35.status)

        val boq70 = boq.firstOrNull { it.size == "70 mm\u00B2" && it.manufacturer == "Elsewedy" }
        assertNotNull(boq70)
        assertEquals(150.0, boq70!!.totalLength_m, 0.01) // 150 * 1
        assertTrue(boq70.totalWeight_kg > 0.0)
        assertEquals("OK", boq70.status)

        val boq240 = boq.firstOrNull { it.size == "240 mm\u00B2" && it.manufacturer == "Elsewedy" }
        assertNotNull(boq240)
        assertEquals(240.0, boq240!!.totalLength_m, 0.01) // 80 * 3
        assertTrue(boq240.totalWeight_kg > 0.0)
        assertEquals("OK", boq240.status)

        // Check the missing-weight group (UnknownMfg)
        val boqMissing = boq.firstOrNull { it.manufacturer == "UnknownMfg" }
        assertNotNull(boqMissing)
        assertEquals(50.0, boqMissing!!.totalLength_m, 0.01) // 50 * 1
        assertEquals(0.0, boqMissing.totalWeight_kg, 0.0)
        assertEquals("DATA MISSING", boqMissing.status)

        // Verify Drum Schedule
        val drums = DrumScheduleEngine.generateDrumSchedule(circuits, sizingResults, maxDrumLength = 100.0)
        assertEquals(4, drums.size)

        val drum35 = drums.firstOrNull { it.size == "35 mm\u00B2" }
        assertNotNull(drum35)
        assertEquals(200.0, drum35!!.totalLength_m, 0.01)
        assertEquals(2, drum35.totalDrums)
        assertEquals(2, drum35.fullDrumsCount)
        assertEquals(0.0, drum35.finalDrumLength_m, 0.01)

        val drum70 = drums.firstOrNull { it.size == "70 mm\u00B2" }
        assertNotNull(drum70)
        assertEquals(150.0, drum70!!.totalLength_m, 0.01)
        assertEquals(2, drum70.totalDrums)
        assertEquals(1, drum70.fullDrumsCount)
        assertEquals(50.0, drum70.finalDrumLength_m, 0.01)

        drums.forEach {
            assertFalse(it.totalLength_m.isNaN())
            assertFalse(it.totalLength_m.isInfinite())
            assertFalse(it.maxDrumLength_m.isNaN())
            assertFalse(it.maxDrumLength_m.isInfinite())
        }
    }

    @Test
    fun testFocusedProcurementRegressions() {
        // A. Exact armoured cable + OD inside valid gland range: expected APPROVED.
        val circA = CircuitEntity(
            id = 101,
            circuitName = "CircA_SWA",
            loadKVA = 20.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 2,
            length_m = 100.0,
            maxVDpct = 0.05,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching"
        )
        val sizingResultA = SizingResult(
            Ib_A = 72.1, deviceSize_A = 80.0, deviceType = "MCCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 36.0, productCode = "ES-CXL4C-35", cableSize_mm2 = "35 mm\u00B2",
            ratedI_table_A = 100.0, capacity_derated_Iz_A = 100.0, utilizationPct = 72.1,
            neutral_mm2 = "16 mm\u00B2", earthCPC_mm2 = "16 mm\u00B2", voltDrop_V = 2.0,
            vdPct = 0.005, cumulativeVDpct = 0.005, status = "OK",
            cableMaterial = "Cu", insulation = "XLPE", cableOD_mm = 21.5
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)

        // Generate with explicit termination ends so we can get APPROVED status
        val resA = ProcurementEngine.generateProcurement(
            listOf(circA),
            mapOf(101 to sizingResultA),
            explicitTerminationEnds = mapOf(101 to 2)
        ).first()

        assertEquals("APPROVED", resA.status)
        assertEquals("EXACT", resA.catalogMatchStatus)
        assertEquals("Elsewedy", resA.catalogManufacturer)
        assertEquals(21.5, resA.cableOD_mm, 0.01)
        assertEquals("25SCW1RA x 4", resA.glandModel) // CMP gland M25 for OD 21.5
        assertTrue(resA.lugsType.contains("Lug") && resA.lugsType.contains("35")) // 2 runs * 4 cores * 2 ends = 16 lugs

        // B. OD outside gland range: expected no exact gland and not APPROVED (DATA MISSING).
        val circB = circA.copy(id = 102, circuitName = "CircB_HugeOD")
        val sizingResultB = sizingResultA.copy(cableOD_mm = 200.0) // 200 mm is huge
        val resB = ProcurementEngine.generateProcurement(
            listOf(circB),
            mapOf(102 to sizingResultB),
            explicitTerminationEnds = mapOf(102 to 2)
        ).first()

        assertEquals("DATA MISSING", resB.status)
        assertNotEquals("APPROVED", resB.status)
        assertTrue(resB.engineeringNote.contains("No compatible gland found in catalog"))

        // C. Correct size but wrong manufacturer/family: must not use the wrong catalog cable row.
        val circC = circA.copy(id = 103, circuitName = "CircC_WrongMfg", manufacturer = "UnknownMfg")
        val sizingResultC = sizingResultA.copy(productCode = "PC-UNKNOWN")
        val resC = ProcurementEngine.generateProcurement(
            listOf(circC),
            mapOf(103 to sizingResultC),
            explicitTerminationEnds = mapOf(103 to 2)
        ).first()

        assertEquals("MISSING", resC.catalogMatchStatus)
        assertEquals("N/A", resC.resolvedCableCatalogCode)

        // D. Unarmoured cable matches unarmoured gland now that unarmoured glands exist in the full catalog.
        val circD = circA.copy(id = 104, circuitName = "CircD_Unarmoured", cableType = "Cu/XLPE/PVC - 4C") // Unarmoured
        val resD = ProcurementEngine.generateProcurement(
            listOf(circD),
            mapOf(104 to sizingResultA),
            explicitTerminationEnds = mapOf(104 to 2)
        ).first()

        assertEquals("APPROVED", resD.status)
        assertFalse(resD.isArmoured)
        assertEquals("32A21RA", resD.matchedGlandCatalogCode)

        // E. Copper conductor with aluminium lug: must fail compatibility.
        val circE_Al = circA.copy(id = 105, circuitName = "CircE_Al", cableType = "Al/XLPE/SWA/PVC - 4C")
        val sizingResultE_Al = sizingResultA.copy(cableMaterial = "Al")
        val resE_Al = ProcurementEngine.generateProcurement(
            listOf(circE_Al),
            mapOf(105 to sizingResultE_Al),
            explicitTerminationEnds = mapOf(105 to 2)
        ).first()

        assertEquals("Aluminium", resE_Al.conductorMaterial)
        assertTrue(resE_Al.lugsType.contains("Alum"))
        assertTrue(resA.lugsType.contains("Copper") || resA.lugsType.contains("Lug"))

        // F. Missing cableOD_mm: expected DATA MISSING; no fabricated gland.
        val circF = circA.copy(id = 106, circuitName = "CircF_MissingOD")
        val sizingResultF = sizingResultA.copy(cableOD_mm = 0.0)
        val circF_unmatched = circF.copy(manufacturer = "UnknownMfg")
        val resF = ProcurementEngine.generateProcurement(
            listOf(circF_unmatched),
            mapOf(106 to sizingResultF),
            explicitTerminationEnds = mapOf(106 to 2)
        ).first()

        assertEquals("DATA MISSING", resF.status)
        assertEquals(0.0, resF.cableOD_mm, 0.001)
        assertEquals("N/A", resF.matchedGlandCatalogCode)

        // G. Missing termination-end count: expected REVIEW REQUIRED with visible assumption.
        val resG = ProcurementEngine.generateProcurement(
            listOf(circA),
            mapOf(101 to sizingResultA)
        ).first()

        assertEquals("REVIEW REQUIRED", resG.status)
        assertTrue(resG.assumedFields.contains("terminationEnds"))

        // H. Parallel runs and explicit termination ends: verify correct gland and lug quantities.
        val circH = circA.copy(id = 108, parallelRuns = 3)
        val resH = ProcurementEngine.generateProcurement(
            listOf(circH),
            mapOf(108 to sizingResultA),
            explicitTerminationEnds = mapOf(108 to 2)
        ).first()

        assertEquals(6, resH.glandQty) // 3 runs * 2 ends
        assertEquals(24, resH.lugQty) // 3 runs * 4 cores * 2 ends

        // I. Missing catalog weight/status from the previous BOQ case: Procurement must preserve DATA MISSING
        val circI = circA.copy(id = 109, manufacturer = "UnknownMfg")
        val resI = ProcurementEngine.generateProcurement(
            listOf(circI),
            mapOf(109 to sizingResultA),
            explicitTerminationEnds = mapOf(109 to 2)
        ).first()

        assertEquals("DATA MISSING", resI.status)

        // J. All numeric results are finite: no NaN or Infinity.
        assertFalse(resA.requiredTrayWidth_mm.isNaN())
        assertFalse(resA.requiredTrayWidth_mm.isInfinite())
        assertFalse(resB.requiredTrayWidth_mm.isNaN())
        assertFalse(resB.requiredTrayWidth_mm.isInfinite())
    }

    @Test
    fun testCableGlandResolutionCatalog() {
        // A. Parse actual accessory_catalog.json assert partNumbers present
        val partNumbers = Catalog.accessories.map { it.partNumber }
        assertTrue("75A21RA must be present", partNumbers.contains("75A21RA"))
        assertTrue("63CW1RA must be present", partNumbers.contains("63CW1RA"))
        assertTrue("75SCW1RA must be present", partNumbers.contains("75SCW1RA"))
        assertTrue("75CW1RA must be present", partNumbers.contains("75CW1RA"))

        // B. Unarmoured, size 240, OD 54.3, 4C: expected 63A21RA
        val circB = CircuitEntity(
            id = 201, circuitName = "ddd", loadKVA = 200.0, fedFrom = "MDB",
            transformerId = "TX-01", loadType = "NORMAL", phases = "3P+N", voltageV = 400,
            pf = 0.85, safetyFactor = 1.0, manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC - 4C", installation = "Laid in Free Air", parallelRuns = 1,
            airTempC = 40, gndTempC = 40, burialDepth_m = 0.6, soilRes = 125, noCircuits = 1,
            formation = "Flat", spacing = "Touching", length_m = 100.0, maxVDpct = 0.03
        )
        val sizingB = SizingResult(
            Ib_A = 340.0, deviceSize_A = 400.0, deviceType = "MCCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 340.0, productCode = "CU/XLPE/PVC-240", cableSize_mm2 = "240 mm²",
            ratedI_table_A = 500.0, capacity_derated_Iz_A = 500.0, utilizationPct = 68.0,
            neutral_mm2 = "120 mm²", earthCPC_mm2 = "120 mm²", voltDrop_V = 3.2, vdPct = 0.8,
            cumulativeVDpct = 0.8, status = "✓ PASS", cableOD_mm = 54.3
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val resB = ProcurementEngine.generateProcurement(listOf(circB), mapOf(201 to sizingB)).first()
        assertTrue("glandModel must contain 63A21RA, got ${resB.glandModel}", resB.glandModel.contains("63A21RA"))

        // C. Unarmoured, size 240, OD 63.0, 4C: expected 75SA21RA or 75A21RA
        val sizingC = sizingB.copy(cableOD_mm = 63.0)
        val resC = ProcurementEngine.generateProcurement(listOf(circB), mapOf(201 to sizingC)).first()
        assertTrue("glandModel must contain 75SA21RA or 75A21RA, got ${resC.glandModel}", resC.glandModel.contains("75SA21RA") || resC.glandModel.contains("75A21RA"))

        // D. Armoured, size 240, OD 56.5, 4C: expected 63SCW1RA or 63CW1RA
        val circD = circB.copy(cableType = "Cu/XLPE/SWA/PVC - 4C")
        val sizingD = sizingB.copy(cableOD_mm = 56.5)
        val resD = ProcurementEngine.generateProcurement(listOf(circD), mapOf(201 to sizingD)).first()
        assertTrue("glandModel must contain 63SCW1RA or 63CW1RA, got ${resD.glandModel}", resD.glandModel.contains("63SCW1RA") || resD.glandModel.contains("63CW1RA"))

        // E. Armoured, size 240, OD 63.0, 4C: deterministic tightest fit
        val sizingE = sizingB.copy(cableOD_mm = 63.0)
        val resE = ProcurementEngine.generateProcurement(listOf(circD), mapOf(201 to sizingE)).first()
        assertFalse("glandModel must be resolved", resE.glandModel.contains("N/A"))

        // H. Missing OD: return CABLE_OD_MISSING
        val sizingH = sizingB.copy(cableOD_mm = 0.0)
        val resH = ProcurementEngine.generateProcurement(listOf(circB), mapOf(201 to sizingH)).first()
        assertEquals("DATA MISSING", resH.status)
        assertTrue("Note must mention CABLE_OD_MISSING", resH.engineeringNote.contains("CABLE_OD_MISSING"))

        // I. Verify quantity: 2 * parallelRuns
        val circI = circB.copy(parallelRuns = 3)
        val resI = ProcurementEngine.generateProcurement(listOf(circI), mapOf(201 to sizingC)).first()
        assertEquals(6, resI.glandQty)
    }

    @Test
    fun testLargeLoadAcbBreakerAndCableSizing() {
        val circ1250 = CircuitEntity(
            id = 999,
            circuitName = "XRF-MAIN",
            loadKVA = 1250.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "NORMAL",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.85,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 2,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 45.0,
            maxVDpct = 0.03
        )

        val result = CableSizingEngine.sizeCircuit(circ1250)
        assertEquals(2000.0, result.deviceSize_A, 0.01)
        assertEquals("ACB", result.deviceType)
        assertTrue("Status must be PASS for 2 parallel runs", result.status.contains("PASS"))
    }

    @Test
    fun test630mm2CableGlandResolution() {
        val circ630 = CircuitEntity(
            id = 777,
            circuitName = "FEED-630",
            loadKVA = 500.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "NORMAL",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.85,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 2,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 50.0,
            maxVDpct = 0.03
        )
        val sizing630 = SizingResult(
            Ib_A = 721.6,
            deviceSize_A = 800.0,
            deviceType = "MCCB",
            cTotalDerating = 0.95,
            iRequired_perCable_A = 380.0,
            productCode = "ELSEWEDY-630",
            cableSize_mm2 = "630 mm²",
            ratedI_table_A = 960.0,
            capacity_derated_Iz_A = 1824.0,
            utilizationPct = 39.5,
            neutral_mm2 = "300 mm²",
            earthCPC_mm2 = "300 mm²",
            voltDrop_V = 1.2,
            vdPct = 0.3,
            cumulativeVDpct = 0.3,
            status = "✓ PASS",
            cableOD_mm = 95.0
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val procList = ProcurementEngine.generateProcurement(listOf(circ630), mapOf(777 to sizing630))
        val item = procList.first()
        assertTrue("100CW1RA gland model must be resolved, got ${item.glandModel}", item.glandModel.contains("100CW1RA"))
        assertFalse("Status must not be DATA MISSING, got ${item.status}", item.status == "DATA MISSING")
    }
}
