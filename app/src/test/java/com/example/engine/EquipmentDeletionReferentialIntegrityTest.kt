package com.example.engine

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class EquipmentDeletionReferentialIntegrityTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = AppDatabase.getDatabase(context)
        repository = EngineeringRepository(database)
        EngineeringCalculationCoordinator.clearCache()
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.clearCache()
        AppDatabase.useTestExecutors = false
    }

    @Test
    fun testDeleteAllAndReAddHasZeroInheritedState() = runBlocking {
        val projectId = repository.createProject("Integrity Test 1", "Client")
        // Create generator
        val gen1 = repository.createGeneratorAtomic(projectId)
        assertEquals("GEN-01", gen1.generatorId)

        // Create circuit assigned to GEN-01
        val circuit = CircuitEntity(
            projectId = projectId,
            circuitName = "Emergency Feeder 1",
            loadKVA = 25.0,
            generatorIncluded = true,
            assignedGeneratorId = "GEN-01"
        )
        repository.insertCircuit(circuit)

        // Verify generator engine attributes circuit to GEN-01
        val circuitsBefore = repository.circuitDao.getCircuitsListForProject(projectId)
        val resBefore = GeneratorEngine.calculate(gen1, circuitsBefore, 25.0)
        assertEquals(25.0, resBefore.essentialLoadKVA, 0.001)

        // Delete all generators
        repository.deleteGenerator(projectId, "GEN-01")

        // Assert circuits in DB have assignedGeneratorId = null and generatorIncluded = false
        val circuitsAfterDelete = repository.circuitDao.getCircuitsListForProject(projectId)
        val cAfter = circuitsAfterDelete.first { it.circuitName == "Emergency Feeder 1" }
        assertNull("assignedGeneratorId must be null after deletion", cAfter.assignedGeneratorId)
        assertFalse("generatorIncluded must be false after deletion", cAfter.generatorIncluded)

        // Re-add generator: MAX(id) was reset -> reuses "GEN-01"
        val newGen = repository.createGeneratorAtomic(projectId)
        assertEquals("GEN-01", newGen.generatorId)

        // Verify new generator attributes ZERO inherited circuits
        val circuitsAfterReAdd = repository.circuitDao.getCircuitsListForProject(projectId)
        val resAfter = GeneratorEngine.calculate(newGen, circuitsAfterReAdd, 25.0)
        assertEquals("New generator must have 0 essential load", 0.0, resAfter.essentialLoadKVA, 0.001)
        assertEquals("No Generator Required", resAfter.status)
    }

    @Test
    fun testNoCalculationPathBranchesOnLiteralIdentifier() {
        val gen1 = GeneratorEntity(projectId = "p1", generatorId = "GEN-01", generatorName = "Gen 1")
        val gen2 = GeneratorEntity(projectId = "p1", generatorId = "GEN-02", generatorName = "Gen 2")

        val unassignedCircuit = CircuitEntity(
            id = 10,
            projectId = "p1",
            circuitName = "Unassigned Emergency Load",
            loadKVA = 30.0,
            generatorIncluded = true,
            assignedGeneratorId = null,
            atsIncluded = true,
            assignedAtsId = null
        )

        // GeneratorEngine must attribute to neither generator
        val resGen1 = GeneratorEngine.calculate(gen1, listOf(unassignedCircuit), 30.0)
        assertEquals(0.0, resGen1.essentialLoadKVA, 0.001)
        val resGen2 = GeneratorEngine.calculate(gen2, listOf(unassignedCircuit), 30.0)
        assertEquals(0.0, resGen2.essentialLoadKVA, 0.001)

        // AtsEngine must attribute to neither ATS
        val ats1 = AtsEntity(projectId = "p1", atsId = "ATS-01", atsName = "ATS 1")
        val ats2 = AtsEntity(projectId = "p1", atsId = "ATS-02", atsName = "ATS 2")
        val atsResMap = AtsEngine.calculateAll(listOf(ats1, ats2), listOf(unassignedCircuit))
        assertEquals(0.0, atsResMap["ATS-01"]!!.selectedLoad, 0.001)
        assertEquals(0.0, atsResMap["ATS-02"]!!.selectedLoad, 0.001)
    }

    @Test
    fun testMultiInstanceUnassignedCircuitNotDoubleCounted() {
        val gen1 = GeneratorEntity(projectId = "p1", generatorId = "GEN-01", generatorName = "Gen 1")
        val gen2 = GeneratorEntity(projectId = "p1", generatorId = "GEN-02", generatorName = "Gen 2")

        val unassignedCircuit = CircuitEntity(
            id = 11,
            projectId = "p1",
            circuitName = "Floating Circuit",
            loadKVA = 50.0,
            generatorIncluded = true,
            assignedGeneratorId = null
        )

        val results = GeneratorEngine.calculateAll(
            generators = listOf(gen1, gen2),
            atsList = emptyList(),
            circuits = listOf(unassignedCircuit),
            totalConnectedLoad = 50.0
        )

        // Load must appear in ZERO generator results
        assertEquals(0.0, results["GEN-01"]!!.essentialLoadKVA, 0.001)
        assertEquals(0.0, results["GEN-02"]!!.essentialLoadKVA, 0.001)

        // Unassigned warning message must be emitted on each result
        assertTrue(results["GEN-01"]!!.messages.any { it.contains("Floating Circuit") && it.contains("not assigned to any generator") })
        assertTrue(results["GEN-02"]!!.messages.any { it.contains("Floating Circuit") && it.contains("not assigned to any generator") })
    }

    @Test
    fun testDeletionLeavesZeroDanglingReferences() = runBlocking {
        val projectId = repository.createProject("Dangling Ref Test", "Client")

        // 1. Two generators sharing paralleling group SYNC-A
        val gen1 = GeneratorEntity(projectId = projectId, id = 1, generatorId = "GEN-01", generatorName = "Gen 1", syncBusId = "SYNC-A")
        val gen2 = GeneratorEntity(projectId = projectId, id = 2, generatorId = "GEN-02", generatorName = "Gen 2", syncBusId = "SYNC-A")
        repository.powerSystemsDao.saveGeneratorSettings(gen1)
        repository.powerSystemsDao.saveGeneratorSettings(gen2)

        // 2. ATS referencing GEN-01 as emergency source
        val ats1 = AtsEntity(projectId = projectId, id = 1, atsId = "ATS-01", atsName = "ATS 1", emergencySourceId = "GEN-01")
        // 3. Second ATS referencing ATS-01 as normal source
        val ats2 = AtsEntity(projectId = projectId, id = 2, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "ATS-01")
        repository.powerSystemsDao.saveAtsSettings(ats1)
        repository.powerSystemsDao.saveAtsSettings(ats2)

        // 4. UPS
        val ups1 = UpsEntity(projectId = projectId, id = 1, upsId = "UPS-01", upsName = "UPS 1")
        repository.powerSystemsDao.saveUpsSettings(ups1)

        // 5. Circuits assigned to GEN-01, ATS-01, UPS-01
        repository.insertCircuit(CircuitEntity(id = 1, projectId = projectId, circuitName = "C-Gen", assignedGeneratorId = "GEN-01", generatorIncluded = true))
        repository.insertCircuit(CircuitEntity(id = 2, projectId = projectId, circuitName = "C-Ats", assignedAtsId = "ATS-01", atsIncluded = true))
        repository.insertCircuit(CircuitEntity(id = 3, projectId = projectId, circuitName = "C-Ups", assignedUpsId = "UPS-01", upsBacked = true))

        // 6. Node positions
        repository.saveSldNodePosition(SldNodePositionEntity(projectId = projectId, nodeId = "gen_GEN-01", x = 100f, y = 100f))
        repository.saveSldNodePosition(SldNodePositionEntity(projectId = projectId, nodeId = "ats_ATS-01", x = 200f, y = 200f))
        repository.saveSldNodePosition(SldNodePositionEntity(projectId = projectId, nodeId = "ups_UPS-01", x = 300f, y = 300f))

        // Delete Generator GEN-01
        repository.deleteGenerator(projectId, "GEN-01")

        // Assert:
        // a) C-Gen has assignedGeneratorId = null AND generatorIncluded = false
        val cGen = repository.circuitDao.getCircuitById(1)!!
        assertNull("assignedGeneratorId must be cleared", cGen.assignedGeneratorId)
        assertFalse("generatorIncluded must be false", cGen.generatorIncluded)

        // b) ATS-01 emergencySourceId is cleared
        val ats1After = repository.powerSystemsDao.getAllAtsList(projectId).first { it.atsId == "ATS-01" }
        assertNull("ATS emergencySourceId must be cleared", ats1After.emergencySourceId)

        // c) Paralleling group on GEN-02 dissolved because remaining membership < 2
        val gen2After = repository.powerSystemsDao.getAllGeneratorsList(projectId).first { it.generatorId == "GEN-02" }
        assertNull("paralleling group must be dissolved", gen2After.syncBusId)
        assertNull("parallelingGroupId must be null", gen2After.parallelingGroupId)

        // d) SLD node position for gen_GEN-01 is deleted
        val positionsAfterGen = repository.sldNodePositionsFlow(projectId).first()
        assertFalse("gen_GEN-01 position must be deleted", positionsAfterGen.any { it.nodeId == "gen_GEN-01" })

        // Delete ATS ATS-01
        repository.deleteAts(projectId, "ATS-01")

        // Assert:
        // a) C-Ats has assignedAtsId = null and atsIncluded = false
        val cAts = repository.circuitDao.getCircuitById(2)!!
        assertNull("assignedAtsId must be cleared", cAts.assignedAtsId)
        assertFalse("atsIncluded must be false", cAts.atsIncluded)

        // b) ATS-02 reverse-direction link normalSourceId is cleared (K2)
        val ats2After = repository.powerSystemsDao.getAllAtsList(projectId).first { it.atsId == "ATS-02" }
        assertNull("ATS-02 normalSourceId must be cleared", ats2After.normalSourceId)

        // c) SLD position for ats_ATS-01 is deleted
        val positionsAfterAts = repository.sldNodePositionsFlow(projectId).first()
        assertFalse("ats_ATS-01 position must be deleted", positionsAfterAts.any { it.nodeId == "ats_ATS-01" })

        // Delete UPS UPS-01
        repository.deleteUps(projectId, "UPS-01")

        // Assert:
        // a) C-Ups has assignedUpsId = null and upsBacked = false
        val cUps = repository.circuitDao.getCircuitById(3)!!
        assertNull("assignedUpsId must be cleared", cUps.assignedUpsId)
        assertFalse("upsBacked must be false", cUps.upsBacked)

        // b) SLD position for ups_UPS-01 is deleted
        val positionsAfterUps = repository.sldNodePositionsFlow(projectId).first()
        assertFalse("ups_UPS-01 position must be deleted", positionsAfterUps.any { it.nodeId == "ups_UPS-01" })
    }

    @Test
    fun testLegacyProjectMigrationPreservesUnambiguousAndClearsAmbiguous() = runBlocking {
        // Case A (unambiguous): 1 generator, 1 ATS, 1 UPS -> explicit assignment preserved
        val jsonUnambiguous = JSONObject().apply {
            put("version", 1)
            put("projectName", "Legacy Single Instance Project")
            put("circuits", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("circuitName", "Feeder 1")
                    put("loadKVA", 20.0)
                    put("generatorIncluded", true)
                    put("atsIncluded", true)
                    put("upsBacked", true)
                })
            })
            put("generators", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("generatorId", "GEN-01")
                    put("generatorName", "Gen 1")
                })
            })
            put("ats", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("atsId", "ATS-01")
                    put("atsName", "ATS 1")
                })
            })
            put("ups", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("upsId", "UPS-01")
                    put("upsName", "UPS 1")
                })
            })
        }

        val resultUnambiguous = repository.importProjectFromJsonDetailed(jsonUnambiguous.toString())
        val pIdUnambiguous = resultUnambiguous.projectId!!
        val circuitsUnambiguous = repository.circuitDao.getCircuitsListForProject(pIdUnambiguous)
        val c1 = circuitsUnambiguous.first { it.circuitName == "Feeder 1" }

        assertEquals("Unambiguous generator assignment must be migrated", "GEN-01", c1.assignedGeneratorId)
        assertTrue("generatorIncluded remains true", c1.generatorIncluded)
        assertEquals("Unambiguous ATS assignment must be migrated", "ATS-01", c1.assignedAtsId)
        assertTrue("atsIncluded remains true", c1.atsIncluded)
        assertEquals("Unambiguous UPS assignment must be migrated", "UPS-01", c1.assignedUpsId)
        assertTrue("upsBacked remains true", c1.upsBacked)

        // Case B (ambiguous): 2 generators, 2 ATS, 2 UPS -> flags cleared, unassigned
        val jsonAmbiguous = JSONObject().apply {
            put("version", 1)
            put("projectName", "Legacy Multi Instance Project")
            put("circuits", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("circuitName", "Feeder Multi")
                    put("loadKVA", 40.0)
                    put("generatorIncluded", true)
                    put("atsIncluded", true)
                    put("upsBacked", true)
                })
            })
            put("generators", org.json.JSONArray().apply {
                put(JSONObject().apply { put("generatorId", "GEN-01") })
                put(JSONObject().apply { put("generatorId", "GEN-02") })
            })
            put("ats", org.json.JSONArray().apply {
                put(JSONObject().apply { put("atsId", "ATS-01") })
                put(JSONObject().apply { put("atsId", "ATS-02") })
            })
            put("ups", org.json.JSONArray().apply {
                put(JSONObject().apply { put("upsId", "UPS-01") })
                put(JSONObject().apply { put("upsId", "UPS-02") })
            })
        }

        val resultAmbiguous = repository.importProjectFromJsonDetailed(jsonAmbiguous.toString())
        val pIdAmbiguous = resultAmbiguous.projectId!!
        val circuitsAmbiguous = repository.circuitDao.getCircuitsListForProject(pIdAmbiguous)
        val cMulti = circuitsAmbiguous.first { it.circuitName == "Feeder Multi" }

        assertNull("Ambiguous generator must not be assigned", cMulti.assignedGeneratorId)
        assertFalse("Ambiguous generator flag must be cleared", cMulti.generatorIncluded)
        assertNull("Ambiguous ATS must not be assigned", cMulti.assignedAtsId)
        assertFalse("Ambiguous ATS flag must be cleared", cMulti.atsIncluded)
        assertNull("Ambiguous UPS must not be assigned", cMulti.assignedUpsId)
        assertFalse("Ambiguous UPS flag must be cleared", cMulti.upsBacked)

        assertTrue("Warnings must report ambiguous circuits", resultAmbiguous.warnings.isNotEmpty())
    }
}
