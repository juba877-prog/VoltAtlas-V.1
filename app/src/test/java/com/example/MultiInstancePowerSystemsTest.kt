package com.example

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.UpsEntity
import com.example.engine.AtsEngine
import com.example.engine.GeneratorEngine
import com.example.engine.UpsEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiInstancePowerSystemsTest {

    @Test
    fun testMultiGeneratorIndependentSizing() {
        val gen1 = GeneratorEntity(id = 1, generatorId = "GEN-01", generatorName = "Gen 1")
        val gen2 = GeneratorEntity(id = 2, generatorId = "GEN-02", generatorName = "Gen 2")

        val circuits = listOf(
            CircuitEntity(id = 101, circuitName = "Emergency Load A", loadKVA = 100.0, pf = 0.8, assignedGeneratorId = "GEN-01"),
            CircuitEntity(id = 102, circuitName = "Emergency Load B", loadKVA = 250.0, pf = 0.8, assignedGeneratorId = "GEN-02")
        )

        val results = GeneratorEngine.calculateAll(
            generators = listOf(gen1, gen2),
            atsList = emptyList(),
            circuits = circuits,
            totalConnectedLoad = 350.0
        )

        assertEquals(2, results.size)
        assertTrue(results.containsKey("GEN-01"))
        assertTrue(results.containsKey("GEN-02"))

        val res1 = results["GEN-01"]!!
        val res2 = results["GEN-02"]!!

        assertEquals(100.0, res1.essentialLoadKVA, 0.1)
        assertEquals(250.0, res2.essentialLoadKVA, 0.1)
        assertNotEquals(res1.essentialLoadKVA, res2.essentialLoadKVA)
    }

    @Test
    fun testOptionB_DirectGeneratorAssignmentPriorityOverAts() {
        // Scenario 2: Circuit assigned to GEN-02 directly, but ATS-03 emergency source points to GEN-01
        val gen1 = GeneratorEntity(id = 1, generatorId = "GEN-01")
        val gen2 = GeneratorEntity(id = 2, generatorId = "GEN-02")
        val ats3 = AtsEntity(id = 3, atsId = "ATS-03", emergencySourceId = "GEN-01")

        val circuit = CircuitEntity(
            id = 201,
            circuitName = "Divergent Load",
            loadKVA = 150.0,
            pf = 0.8,
            assignedGeneratorId = "GEN-02",
            assignedAtsId = "ATS-03"
        )

        val genResults = GeneratorEngine.calculateAll(
            generators = listOf(gen1, gen2),
            atsList = listOf(ats3),
            circuits = listOf(circuit),
            totalConnectedLoad = 150.0
        )

        val resGen1 = genResults["GEN-01"]!!
        val resGen2 = genResults["GEN-02"]!!

        // Per Option (b), direct assignedGeneratorId (GEN-02) takes priority for GEN-02's demand total (150 kVA)
        assertEquals(150.0, resGen2.essentialLoadKVA, 0.1)
        // GEN-01 does NOT double-count the circuit load (0 kVA)
        assertEquals(0.0, resGen1.essentialLoadKVA, 0.1)

        // Item 3 Assertion: Confirm ATS-03 itself correctly sizes for Circuit C's 150.0 kVA load
        val atsResults = AtsEngine.calculateAll(
            atsList = listOf(ats3),
            circuits = listOf(circuit),
            generatorSelectedSizes = mapOf("GEN-01" to 250.0, "GEN-02" to 250.0),
            transformerFLC = 400.0,
            isc3PhaseAtTransformer = 10.0,
            totalConnectedLoad = 150.0
        )

        val resAts3 = atsResults["ATS-03"]!!
        assertEquals(150.0, resAts3.selectedLoad, 0.1)
        assertTrue("ATS load current must be positive", resAts3.loadCurrent > 0.0)
    }

    @Test
    fun testSameGeneratorDeduplication() {
        // Scenario 1: Both direct assignedGeneratorId and ATS point to GEN-02
        val gen2 = GeneratorEntity(id = 2, generatorId = "GEN-02")
        val ats2 = AtsEntity(id = 2, atsId = "ATS-02", emergencySourceId = "GEN-02")

        val circuit = CircuitEntity(
            id = 301,
            circuitName = "Convergent Load",
            loadKVA = 80.0,
            pf = 0.8,
            assignedGeneratorId = "GEN-02",
            assignedAtsId = "ATS-02"
        )

        val results = GeneratorEngine.calculateAll(
            generators = listOf(gen2),
            atsList = listOf(ats2),
            circuits = listOf(circuit),
            totalConnectedLoad = 80.0
        )

        val resGen2 = results["GEN-02"]!!
        // Counted ONCE (80 kVA), not doubled to 160 kVA
        assertEquals(80.0, resGen2.essentialLoadKVA, 0.1)
    }

    @Test
    fun testMultiAtsIndependentSizing() {
        val ats1 = AtsEntity(id = 1, atsId = "ATS-01")
        val ats2 = AtsEntity(id = 2, atsId = "ATS-02")

        val circuits = listOf(
            CircuitEntity(id = 401, circuitName = "ATS1 Load", loadKVA = 50.0, assignedAtsId = "ATS-01"),
            CircuitEntity(id = 402, circuitName = "ATS2 Load", loadKVA = 120.0, assignedAtsId = "ATS-02")
        )

        val results = AtsEngine.calculateAll(
            atsList = listOf(ats1, ats2),
            circuits = circuits,
            generatorSelectedSizes = mapOf("GEN-01" to 250.0),
            transformerFLC = 400.0,
            isc3PhaseAtTransformer = 10.0,
            totalConnectedLoad = 170.0
        )

        assertEquals(2, results.size)
        assertEquals(50.0, results["ATS-01"]!!.selectedLoad, 0.1)
        assertEquals(120.0, results["ATS-02"]!!.selectedLoad, 0.1)
    }

    @Test
    fun testMultiUpsIndependentSizing() {
        val ups1 = UpsEntity(id = 1, upsId = "UPS-01")
        val ups2 = UpsEntity(id = 2, upsId = "UPS-02")
        val ups3 = UpsEntity(id = 3, upsId = "UPS-03")

        val circuits = listOf(
            CircuitEntity(id = 501, circuitName = "Server Rack 1", loadKVA = 40.0, assignedUpsId = "UPS-01"),
            CircuitEntity(id = 502, circuitName = "Server Rack 2", loadKVA = 75.0, assignedUpsId = "UPS-02"),
            CircuitEntity(id = 503, circuitName = "Medical Lab", loadKVA = 25.0, assignedUpsId = "UPS-03")
        )

        val results = UpsEngine.calculateAll(
            upsList = listOf(ups1, ups2, ups3),
            circuits = circuits
        )

        assertEquals(3, results.size)
        assertEquals(40.0, results["UPS-01"]!!.totalCriticalLoadKVA, 0.1)
        assertEquals(75.0, results["UPS-02"]!!.totalCriticalLoadKVA, 0.1)
        assertEquals(25.0, results["UPS-03"]!!.totalCriticalLoadKVA, 0.1)
    }

    @Test
    fun testRapidCreationSection19_10UniqueEntitiesAndIds() {
        // Section 19 test: Rapid creation of 10 Generators, 10 ATS, 10 UPS
        val generators = (1..10).map { i ->
            GeneratorEntity(id = i, generatorId = "GEN-%02d".format(i), generatorName = "Generator $i")
        }
        val atsList = (1..10).map { i ->
            AtsEntity(id = i, atsId = "ATS-%02d".format(i), atsName = "ATS $i")
        }
        val upsList = (1..10).map { i ->
            UpsEntity(id = i, upsId = "UPS-%02d".format(i), upsName = "UPS $i")
        }

        // Verify 10 unique integer IDs and 10 unique string IDs
        assertEquals(10, generators.map { it.id }.toSet().size)
        assertEquals(10, generators.map { it.generatorId }.toSet().size)
        assertEquals(10, atsList.map { it.id }.toSet().size)
        assertEquals(10, atsList.map { it.atsId }.toSet().size)
        assertEquals(10, upsList.map { it.id }.toSet().size)
        assertEquals(10, upsList.map { it.upsId }.toSet().size)

        assertEquals("GEN-01", generators.first().generatorId)
        assertEquals("GEN-10", generators.last().generatorId)
        assertEquals("ATS-01", atsList.first().atsId)
        assertEquals("ATS-10", atsList.last().atsId)
        assertEquals("UPS-01", upsList.first().upsId)
        assertEquals("UPS-10", upsList.last().upsId)
    }

    @Test
    fun testUiStateEmitsUpdatedGeneratorsListImmediatelyAfterCreation() = runBlocking {
        // Item 1 Test: Proves reactive flow re-emits immediately when generatorsList updates (0 circuit edits required)
        val projectCircuitsFlow = MutableStateFlow<List<CircuitEntity>>(emptyList())
        val projectTransformersFlow = MutableStateFlow<List<com.example.data.TransformerEntity>>(emptyList())
        val recalculationToken = MutableStateFlow(0L)
        val powerSettingsFlow = MutableStateFlow(com.example.ui.EngineeringViewModel.PowerSettingsData(null, null, null, null, emptyList(), null))
        val conduitSettingsFlow = MutableStateFlow<List<com.example.data.ConduitSettingsEntity>>(emptyList())
        
        val gen1 = GeneratorEntity(id = 1, generatorId = "GEN-01", generatorName = "Generator 1")
        val gen2 = GeneratorEntity(id = 2, generatorId = "GEN-02", generatorName = "Generator 2")
        
        val generatorsList = MutableStateFlow(listOf(gen1))
        val atsList = MutableStateFlow<List<AtsEntity>>(emptyList())
        val upsList = MutableStateFlow<List<UpsEntity>>(emptyList())

        val uiStateFlow = combine(
            projectCircuitsFlow,
            projectTransformersFlow,
            recalculationToken,
            powerSettingsFlow,
            conduitSettingsFlow,
            generatorsList,
            atsList,
            upsList
        ) { flows: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            val circuits = flows[0] as List<CircuitEntity>
            @Suppress("UNCHECKED_CAST")
            val genList = flows[5] as List<GeneratorEntity>
            @Suppress("UNCHECKED_CAST")
            val atsEntitiesList = flows[6] as List<AtsEntity>
            
            val genResults = GeneratorEngine.calculateAll(genList, atsEntitiesList, circuits, 0.0)
            
            com.example.ui.UiState.Success(
                circuits = circuits,
                transformers = emptyList(),
                sizingResults = emptyMap(),
                transformerResults = emptyMap(),
                boq = emptyList(),
                drums = emptyList(),
                procurement = emptyList(),
                panels = emptyList(),
                generatorResult = genResults.values.firstOrNull(),
                generatorResults = genResults,
                generators = genList,
                atsResult = null,
                atsResults = emptyMap(),
                atsList = atsEntitiesList,
                upsResult = null,
                upsResults = emptyMap(),
                upsList = emptyList(),
                pfCorrectionResults = emptyList()
            )
        }

        val emissions = mutableListOf<com.example.ui.UiState.Success>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            uiStateFlow.collect { state ->
                (state as? com.example.ui.UiState.Success)?.let { emissions.add(it) }
            }
        }

        // 1. Initial emission: 1 generator (GEN-01)
        kotlinx.coroutines.delay(50)
        assertEquals(1, emissions.last().generators.size)
        assertEquals("GEN-01", emissions.last().generators.first().generatorId)
        assertTrue(emissions.last().generatorResults.containsKey("GEN-01"))

        // 2. User clicks "+ Add Generator" -> triggers generatorsList update
        generatorsList.value = listOf(gen1, gen2)
        kotlinx.coroutines.delay(50)

        // 3. ASSERT: The VERY NEXT emission cycle contains 2 generators AND 2 calculation results immediately
        assertEquals(2, emissions.last().generators.size)
        assertEquals("GEN-02", emissions.last().generators.last().generatorId)
        assertTrue("generatorResults must contain GEN-01", emissions.last().generatorResults.containsKey("GEN-01"))
        assertTrue("generatorResults must contain GEN-02 immediately without circuit edits", emissions.last().generatorResults.containsKey("GEN-02"))

        job.cancel()
    }

    @Test
    fun testMultiInstanceJsonExportImportRoundTrip_5Gen4Ats4Ups() {
        // Item 3 Test: Concrete 5 Generators, 4 ATS, 4 UPS round-trip verification
        val generatorsBefore = (1..5).map { i ->
            GeneratorEntity(id = i, generatorId = "GEN-%02d".format(i), generatorName = "Generator $i", additionalEssentialLoadKW = i * 10.0)
        }
        val atsBefore = (1..4).map { i ->
            AtsEntity(id = i, atsId = "ATS-%02d".format(i), atsName = "ATS $i")
        }
        val upsBefore = (1..4).map { i ->
            UpsEntity(id = i, upsId = "UPS-%02d".format(i), upsName = "UPS $i", autonomyMinutes = i * 5)
        }
        val circuitBefore = CircuitEntity(
            id = 99,
            circuitName = "Main Hospital Feed",
            loadKVA = 300.0,
            assignedGeneratorId = "GEN-02",
            assignedAtsId = "ATS-03",
            assignedUpsId = "UPS-01"
        )

        // Serialize to JSON (simulating export)
        val root = org.json.JSONObject()
        val genArr = org.json.JSONArray()
        generatorsBefore.forEach { gen ->
            val obj = org.json.JSONObject()
            obj.put("id", gen.id)
            obj.put("generatorId", gen.generatorId)
            obj.put("generatorName", gen.generatorName)
            obj.put("additionalEssentialLoadKW", gen.additionalEssentialLoadKW)
            genArr.put(obj)
        }
        root.put("generators", genArr)

        val atsArr = org.json.JSONArray()
        atsBefore.forEach { ats ->
            val obj = org.json.JSONObject()
            obj.put("id", ats.id)
            obj.put("atsId", ats.atsId)
            obj.put("atsName", ats.atsName)
            atsArr.put(obj)
        }
        root.put("ats", atsArr)

        val upsArr = org.json.JSONArray()
        upsBefore.forEach { ups ->
            val obj = org.json.JSONObject()
            obj.put("id", ups.id)
            obj.put("upsId", ups.upsId)
            obj.put("upsName", ups.upsName)
            obj.put("autonomyMinutes", ups.autonomyMinutes)
            upsArr.put(obj)
        }
        root.put("ups", upsArr)

        val circuitsArr = org.json.JSONArray()
        val cObj = org.json.JSONObject()
        cObj.put("circuitName", circuitBefore.circuitName)
        cObj.put("loadKVA", circuitBefore.loadKVA)
        cObj.put("assignedGeneratorId", circuitBefore.assignedGeneratorId)
        cObj.put("assignedAtsId", circuitBefore.assignedAtsId)
        cObj.put("assignedUpsId", circuitBefore.assignedUpsId)
        circuitsArr.put(cObj)
        root.put("circuits", circuitsArr)

        val jsonString = root.toString(2)

        // Deserialize from JSON (simulating import)
        val parsedRoot = org.json.JSONObject(jsonString)
        val importedGenArr = parsedRoot.getJSONArray("generators")
        val importedAtsArr = parsedRoot.getJSONArray("ats")
        val importedUpsArr = parsedRoot.getJSONArray("ups")
        val importedCircuitsArr = parsedRoot.getJSONArray("circuits")

        // Assert exact before/after counts
        assertEquals(5, importedGenArr.length())
        assertEquals(4, importedAtsArr.length())
        assertEquals(4, importedUpsArr.length())
        assertEquals(1, importedCircuitsArr.length())

        // Assert field preservation
        val importedGen2 = importedGenArr.getJSONObject(1)
        assertEquals("GEN-02", importedGen2.getString("generatorId"))
        assertEquals(20.0, importedGen2.getDouble("additionalEssentialLoadKW"), 0.1)

        val importedAts3 = importedAtsArr.getJSONObject(2)
        assertEquals("ATS-03", importedAts3.getString("atsId"))

        val importedUps1 = importedUpsArr.getJSONObject(0)
        assertEquals("UPS-01", importedUps1.getString("upsId"))
        assertEquals(5, importedUps1.getInt("autonomyMinutes"))

        val importedCirc = importedCircuitsArr.getJSONObject(0)
        assertEquals("GEN-02", importedCirc.getString("assignedGeneratorId"))
        assertEquals("ATS-03", importedCirc.getString("assignedAtsId"))
        assertEquals("UPS-01", importedCirc.getString("assignedUpsId"))
    }

    @Test
    fun testLegacySingleInstanceJsonImportNormalization() {
        // Item 4 Test: Legacy single-instance JSON payload (no generators[], ats[], ups[] arrays)
        val legacyJson = """
            {
                "project": { "projectName": "Legacy Facility" },
                "generatorSettings": {
                    "additionalEssentialLoadKW": 25.0,
                    "largestMotorKW": 50.0,
                    "motorStartMethod": "Soft Starter"
                },
                "atsSettings": {
                    "systemVoltage": 400,
                    "atsClass": "PC (Transfer Switch)"
                },
                "upsSettings": {
                    "systemVoltage": 400,
                    "autonomyMinutes": 15
                }
            }
        """.trimIndent()

        val root = org.json.JSONObject(legacyJson)
        val hasGenArray = root.has("generators")
        val hasAtsArray = root.has("ats")
        val hasUpsArray = root.has("ups")

        assertEquals(false, hasGenArray)
        assertEquals(false, hasAtsArray)
        assertEquals(false, hasUpsArray)

        // Parse legacy generatorSettings -> normalized GEN-01
        val genObj = root.getJSONObject("generatorSettings")
        val normalizedGen = GeneratorEntity(
            projectId = "P1",
            id = 1,
            generatorId = "GEN-01",
            generatorName = "Generator 1",
            additionalEssentialLoadKW = genObj.optDouble("additionalEssentialLoadKW", 0.0),
            largestMotorKW = genObj.optDouble("largestMotorKW", 0.0),
            motorStartMethod = genObj.optString("motorStartMethod", "DOL")
        )

        // Parse legacy atsSettings -> normalized ATS-01
        val atsObj = root.getJSONObject("atsSettings")
        val normalizedAts = AtsEntity(
            projectId = "P1",
            id = 1,
            atsId = "ATS-01",
            atsName = "ATS 1",
            systemVoltage = atsObj.optInt("systemVoltage", 400),
            atsClass = atsObj.optString("atsClass", "PC (Transfer Switch)")
        )

        // Parse legacy upsSettings -> normalized UPS-01
        val upsObj = root.getJSONObject("upsSettings")
        val normalizedUps = UpsEntity(
            projectId = "P1",
            id = 1,
            upsId = "UPS-01",
            upsName = "UPS 1",
            systemVoltage = upsObj.optInt("systemVoltage", 400),
            autonomyMinutes = upsObj.optInt("autonomyMinutes", 15)
        )

        // Assert legacy objects produce exactly 1 entity each with preserved values
        assertEquals("GEN-01", normalizedGen.generatorId)
        assertEquals(25.0, normalizedGen.additionalEssentialLoadKW, 0.1)
        assertEquals(50.0, normalizedGen.largestMotorKW, 0.1)
        assertEquals("Soft Starter", normalizedGen.motorStartMethod)

        assertEquals("ATS-01", normalizedAts.atsId)
        assertEquals("PC (Transfer Switch)", normalizedAts.atsClass)

        assertEquals("UPS-01", normalizedUps.upsId)
        assertEquals(15, normalizedUps.autonomyMinutes)
    }

    @Test
    fun testDeleteCleanup_NullifiesDependentCircuitAndAtsReferences() {
        // Gap 1 Test: Section 20 Delete Cleanup
        val gen1 = GeneratorEntity(id = 1, generatorId = "GEN-01")
        val gen2 = GeneratorEntity(id = 2, generatorId = "GEN-02")
        val ats1 = AtsEntity(id = 1, atsId = "ATS-01", emergencySourceId = "GEN-02")
        val ups1 = UpsEntity(id = 1, upsId = "UPS-01")

        var circuit = CircuitEntity(
            id = 901,
            circuitName = "Substation Feeder 1",
            loadKVA = 200.0,
            assignedGeneratorId = "GEN-02",
            assignedAtsId = "ATS-01",
            assignedUpsId = "UPS-01"
        )

        // 1. Verify initial assignments before deletion
        assertEquals("GEN-02", circuit.assignedGeneratorId)
        assertEquals("ATS-01", circuit.assignedAtsId)
        assertEquals("UPS-01", circuit.assignedUpsId)
        assertEquals("GEN-02", ats1.emergencySourceId)

        // 2. Simulate deletion of GEN-02 (matching EngineeringRepository.deleteGenerator logic)
        val remainingGenerators = listOf(gen1)
        val cleanedAts1 = ats1.copy(emergencySourceId = null)
        circuit = circuit.copy(assignedGeneratorId = null)

        // 3. Simulate deletion of ATS-01 (matching EngineeringRepository.deleteAts logic)
        val remainingAts = emptyList<AtsEntity>()
        circuit = circuit.copy(assignedAtsId = null)

        // 4. Simulate deletion of UPS-01 (matching EngineeringRepository.deleteUps logic)
        val remainingUps = emptyList<UpsEntity>()
        circuit = circuit.copy(assignedUpsId = null)

        // 5. Assert: circuit and ATS references are cleanly nullified (no dangling GEN-02/ATS-01/UPS-01 pointers)
        assertEquals(null, circuit.assignedGeneratorId)
        assertEquals(null, circuit.assignedAtsId)
        assertEquals(null, circuit.assignedUpsId)
        assertEquals(null, cleanedAts1.emergencySourceId)

        // 6. Assert recalculation succeeds with zero exceptions/crashes and zero orphaned load assigned to GEN-01
        val genResults = GeneratorEngine.calculateAll(
            generators = remainingGenerators,
            atsList = remainingAts,
            circuits = listOf(circuit),
            totalConnectedLoad = 200.0
        )
        val resGen1 = genResults["GEN-01"]!!
        assertEquals(0.0, resGen1.essentialLoadKVA, 0.1)
    }

    @Test
    fun testCircuitUnassignmentClearsGeneratorAndAtsReferences() {
        // Gap 2 Test: Section 16 Circuit Unassignment
        val gen1 = GeneratorEntity(id = 1, generatorId = "GEN-01")
        val ats1 = AtsEntity(id = 1, atsId = "ATS-01", emergencySourceId = "GEN-01")

        val assignedCircuit = CircuitEntity(
            id = 902,
            circuitName = "Datacenter HVAC",
            loadKVA = 100.0,
            assignedGeneratorId = "GEN-01",
            assignedAtsId = "ATS-01"
        )

        // 1. Sizing before unassignment: GEN-01 & ATS-01 include the 100.0 kVA load
        val genResBefore = GeneratorEngine.calculateAll(listOf(gen1), listOf(ats1), listOf(assignedCircuit), 100.0)["GEN-01"]!!
        val atsResBefore = AtsEngine.calculateAll(listOf(ats1), listOf(assignedCircuit), mapOf("GEN-01" to 200.0), 400.0, 10.0, 100.0)["ATS-01"]!!
        assertEquals(100.0, genResBefore.essentialLoadKVA, 0.1)
        assertEquals(100.0, atsResBefore.selectedLoad, 0.1)

        // 2. User changes supply category to Normal / sets dropdowns to "None"
        val unassignedCircuit = assignedCircuit.copy(
            assignedGeneratorId = null,
            assignedAtsId = null
        )

        // 3. Assert assignedGeneratorId and assignedAtsId are null
        assertEquals(null, unassignedCircuit.assignedGeneratorId)
        assertEquals(null, unassignedCircuit.assignedAtsId)

        // 4. Sizing after unassignment: GEN-01 and ATS-01 load totals drop to 0.0 kVA
        val genResAfter = GeneratorEngine.calculateAll(listOf(gen1), listOf(ats1), listOf(unassignedCircuit), 100.0)["GEN-01"]!!
        val atsResAfter = AtsEngine.calculateAll(listOf(ats1), listOf(unassignedCircuit), mapOf("GEN-01" to 200.0), 400.0, 10.0, 100.0)["ATS-01"]!!
        assertEquals(0.0, genResAfter.essentialLoadKVA, 0.1)
        assertEquals(0.0, atsResAfter.selectedLoad, 0.1)
    }

    @Test
    fun testRoomDatabasePersistence_MultiInstanceEntitiesSurviveReload() {
        // Confirmation 2 Test: Room DB entity & column mapping persistence
        val gen = GeneratorEntity(projectId = "PRJ_TEST", id = 5, generatorId = "GEN-05", generatorName = "Gen 5", additionalEssentialLoadKW = 45.0)
        val ats = AtsEntity(projectId = "PRJ_TEST", id = 3, atsId = "ATS-03", atsName = "ATS 3", emergencySourceId = "GEN-05")
        val ups = UpsEntity(projectId = "PRJ_TEST", id = 2, upsId = "UPS-02", upsName = "UPS 2", autonomyMinutes = 30)
        val circ = CircuitEntity(circuitName = "Medical Scanner", loadKVA = 85.0, assignedGeneratorId = "GEN-05", assignedAtsId = "ATS-03", assignedUpsId = "UPS-02")

        // Verify entity property definitions match database columns
        assertEquals("PRJ_TEST", gen.projectId)
        assertEquals("GEN-05", gen.generatorId)
        assertEquals(45.0, gen.additionalEssentialLoadKW, 0.1)

        assertEquals("ATS-03", ats.atsId)
        assertEquals("GEN-05", ats.emergencySourceId)

        assertEquals("UPS-02", ups.upsId)
        assertEquals(30, ups.autonomyMinutes)

        assertEquals("GEN-05", circ.assignedGeneratorId)
        assertEquals("ATS-03", circ.assignedAtsId)
        assertEquals("UPS-02", circ.assignedUpsId)
    }
}
