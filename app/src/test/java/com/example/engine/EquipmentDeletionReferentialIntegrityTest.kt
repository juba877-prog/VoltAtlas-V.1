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
            assignedAtsId = null,
            upsBacked = true,
            assignedUpsId = null
        )

        // GeneratorEngine must attribute to neither generator
        val resGen1 = GeneratorEngine.calculate(gen1, listOf(unassignedCircuit), 30.0)
        assertEquals(0.0, resGen1.essentialLoadKVA, 0.001)
        val resGen2 = GeneratorEngine.calculate(gen2, listOf(unassignedCircuit), 30.0)
        assertEquals(0.0, resGen2.essentialLoadKVA, 0.001)

        // AtsEngine must attribute to neither ATS (aggregate entry point)
        val ats1 = AtsEntity(projectId = "p1", atsId = "ATS-01", atsName = "ATS 1")
        val ats2 = AtsEntity(projectId = "p1", atsId = "ATS-02", atsName = "ATS 2")
        val atsResMap = AtsEngine.calculateAll(listOf(ats1, ats2), listOf(unassignedCircuit))
        assertEquals(0.0, atsResMap["ATS-01"]!!.selectedLoad, 0.001)
        assertEquals(0.0, atsResMap["ATS-02"]!!.selectedLoad, 0.001)

        // AtsEngine PER-INSTANCE entry point: the literal "ATS-01" fallback lived on this
        // path only, so it must be exercised directly. A flagged-but-unassigned circuit
        // must be attributed to no instance at all.
        val atsRes1 = AtsEngine.calculate(
            settings = ats1, totalConnectedLoad = 0.0, essentialLoadKVA = 30.0,
            generatorSelectedSize = 0.0, transformerFLC = 0.0,
            isc3PhaseAtTransformer = 10.0, circuits = listOf(unassignedCircuit)
        )
        assertEquals(
            "Per-instance ATS-01 must not adopt a flagged-but-unassigned circuit",
            0.0, atsRes1.selectedLoad, 0.001
        )
        assertEquals(
            "Per-instance ATS-01 must report SELECT_LOADS with no assigned circuits",
            "SELECT_LOADS", atsRes1.status
        )
        val atsRes2 = AtsEngine.calculate(
            settings = ats2, totalConnectedLoad = 0.0, essentialLoadKVA = 30.0,
            generatorSelectedSize = 0.0, transformerFLC = 0.0,
            isc3PhaseAtTransformer = 10.0, circuits = listOf(unassignedCircuit)
        )
        assertEquals(0.0, atsRes2.selectedLoad, 0.001)

        // UpsEngine must attribute to neither UPS (aggregate entry point)
        val ups1 = UpsEntity(projectId = "p1", upsId = "UPS-01", upsName = "UPS 1")
        val ups2 = UpsEntity(projectId = "p1", upsId = "UPS-02", upsName = "UPS 2")
        val upsResMap = UpsEngine.calculateAll(listOf(ups1, ups2), listOf(unassignedCircuit))
        assertEquals(0.0, upsResMap["UPS-01"]!!.totalCriticalLoadKVA, 0.001)
        assertEquals(0.0, upsResMap["UPS-02"]!!.totalCriticalLoadKVA, 0.001)

        // UpsEngine PER-INSTANCE entry point: the literal "UPS-01" fallback lived here and
        // was reachable in production via calculateAll -> calculate. It must adopt nothing.
        val upsRes1 = UpsEngine.calculate(ups1, listOf(unassignedCircuit))
        assertEquals(
            "Per-instance UPS-01 must not adopt a flagged-but-unassigned circuit",
            0.0, upsRes1.totalCriticalLoadKVA, 0.001
        )
        assertEquals(
            "Per-instance UPS-01 must report SELECT_LOADS with no assigned circuits",
            "SELECT_LOADS", upsRes1.status
        )
        val upsRes2 = UpsEngine.calculate(ups2, listOf(unassignedCircuit))
        assertEquals(0.0, upsRes2.totalCriticalLoadKVA, 0.001)
    }

    @Test
    fun testMigratedSingleInstanceProjectRetainsAttribution() {
        // Post-migration state produced by MIGRATION_25_26 for an unambiguous legacy
        // project: legacy flags remain set AND explicit assignments are populated.
        // Attribution must be preserved by the explicit assignment alone — no
        // literal-identifier fallback may be required to keep the original result.
        val circuit = CircuitEntity(
            id = 20,
            projectId = "p1",
            circuitName = "Legacy Emergency Feeder",
            loadKVA = 40.0,
            generatorIncluded = true,
            atsIncluded = true,
            upsBacked = true,
            assignedGeneratorId = "GEN-01",
            assignedAtsId = "ATS-01",
            assignedUpsId = "UPS-01"
        )
        val soleGen = GeneratorEntity(projectId = "p1", generatorId = "GEN-01", generatorName = "Gen 1")
        val soleAts = AtsEntity(projectId = "p1", atsId = "ATS-01", atsName = "ATS 1")
        val soleUps = UpsEntity(projectId = "p1", upsId = "UPS-01", upsName = "UPS 1")

        val genRes = GeneratorEngine.calculate(soleGen, listOf(circuit), 40.0)
        assertEquals(
            "Migrated generator attribution must be preserved via explicit assignment",
            40.0, genRes.essentialLoadKVA, 0.001
        )

        val atsRes = AtsEngine.calculate(
            settings = soleAts, totalConnectedLoad = 40.0, essentialLoadKVA = 40.0,
            generatorSelectedSize = 0.0, transformerFLC = 0.0,
            isc3PhaseAtTransformer = 10.0, circuits = listOf(circuit)
        )
        assertEquals(
            "Migrated ATS attribution must be preserved via explicit assignment",
            40.0, atsRes.selectedLoad, 0.001
        )

        val upsRes = UpsEngine.calculate(soleUps, listOf(circuit))
        assertEquals(
            "Migrated UPS attribution must be preserved via explicit assignment",
            40.0, upsRes.totalCriticalLoadKVA, 0.001
        )
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

        // Unassigned circuits are surfaced ONCE at project level via the graph builder's
        // UNASSIGNED_GENERATOR_CIRCUIT validation issue — not duplicated on every instance.
        assertTrue(results["GEN-01"]!!.messages.none { it.contains("not assigned to any generator") })
        assertTrue(results["GEN-02"]!!.messages.none { it.contains("not assigned to any generator") })
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

    @Test
    fun testImportedAtsToUpsSourceLinkClearedOnUpsDeletion() = runBlocking {
        // The JSON import persists ATS source strings without validation, so an ATS->UPS
        // source link can exist even though the power-systems UI cannot create one.
        // Deleting the UPS must clear the imported link (M4: real case, not defensive).
        val json = JSONObject().apply {
            put("version", 1)
            put("projectName", "Imported Ats Ups Link")
            put("circuits", org.json.JSONArray())
            put("ats", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("atsId", "ATS-01")
                    put("emergencySourceId", "UPS-01")
                })
            })
            put("ups", org.json.JSONArray().apply {
                put(JSONObject().apply { put("upsId", "UPS-01") })
            })
        }
        val result = repository.importProjectFromJsonDetailed(json.toString())
        val pid = result.projectId!!
        val atsBefore = repository.powerSystemsDao.getAllAtsList(pid).first()
        assertEquals("Import must persist the ATS->UPS link", "UPS-01", atsBefore.emergencySourceId)

        repository.deleteUps(pid, "UPS-01")

        val atsAfter = repository.powerSystemsDao.getAllAtsList(pid).first()
        assertNull("Imported ATS->UPS link must be cleared when the UPS is deleted", atsAfter.emergencySourceId)
    }

    @Test
    fun testDeletionPreventsNodePositionAndOverrideInheritanceOnIdentifierReuse() = runBlocking {
        val projectId = repository.createProject("Position Inheritance Test", "Client")

        // Configured instances with a saved SLD node position (including manual port
        // overrides) and a manual sizing override.
        val gen = repository.createGeneratorAtomic(projectId)
        repository.powerSystemsDao.saveGeneratorSettings(gen.copy(enableManualOverride = true, manualSizeKVA = 250.0))
        repository.saveSldNodePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "gen_${gen.generatorId}", x = 140f, y = 90f, portOverrides = """{"EMERGENCY":"TOP"}""")
        )

        val ats = repository.createAtsAtomic(projectId)
        repository.powerSystemsDao.saveAtsSettings(ats.copy(enableManualOverride = true, manualSizeAmp = 800.0))
        repository.saveSldNodePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "ats_${ats.atsId}", x = 240f, y = 190f, portOverrides = """{"NORMAL":"LEFT"}""")
        )

        val ups = repository.createUpsAtomic(projectId)
        repository.powerSystemsDao.saveUpsSettings(ups.copy(enableManualOverride = true, manualSizeKVA = 60.0))
        repository.saveSldNodePosition(
            SldNodePositionEntity(projectId = projectId, nodeId = "ups_${ups.upsId}", x = 340f, y = 290f, portOverrides = """{"OUTPUT":"RIGHT"}""")
        )

        // Delete all three instances.
        repository.deleteGenerator(projectId, gen.generatorId)
        repository.deleteAts(projectId, ats.atsId)
        repository.deleteUps(projectId, ups.upsId)

        // Deterministic identifier reuse: an emptied collection starts at MAX(id)=0,
        // so a newly created instance receives the exact identifier the deleted one had.
        val newGen = repository.createGeneratorAtomic(projectId)
        val newAts = repository.createAtsAtomic(projectId)
        val newUps = repository.createUpsAtomic(projectId)
        assertEquals("Identifier reuse after deletion must be deterministic", gen.generatorId, newGen.generatorId)
        assertEquals(ats.atsId, newAts.atsId)
        assertEquals(ups.upsId, newUps.upsId)

        // No inherited saved position and no inherited manual port overrides for the
        // re-created identifiers.
        val positions = repository.sldNodePositionsFlow(projectId).first()
        assertTrue(
            "Re-created ${newGen.generatorId} must not inherit the deleted instance's saved position",
            positions.none { it.nodeId == "gen_${newGen.generatorId}" }
        )
        assertTrue(
            "Re-created ${newAts.atsId} must not inherit the deleted instance's saved position",
            positions.none { it.nodeId == "ats_${newAts.atsId}" }
        )
        assertTrue(
            "Re-created ${newUps.upsId} must not inherit the deleted instance's saved position",
            positions.none { it.nodeId == "ups_${newUps.upsId}" }
        )

        // No inherited manual sizing override either.
        assertFalse("Re-created generator must not inherit manual override", newGen.enableManualOverride)
        assertNull("Re-created generator must not inherit manual size", newGen.manualSizeKVA)
        assertFalse("Re-created ATS must not inherit manual override", newAts.enableManualOverride)
        assertNull("Re-created ATS must not inherit manual size", newAts.manualSizeAmp)
        assertFalse("Re-created UPS must not inherit manual override", newUps.enableManualOverride)
        assertNull("Re-created UPS must not inherit manual size", newUps.manualSizeKVA)
    }

    @Test
    fun testDeleteToZeroCleansCircuitAssignmentsAndPreservesSourceIntegrity() = runBlocking {
        val projectId = repository.createProject("Delete To Zero Circuit Cleanliness", "Client")

        // 1. Create one of each equipment type (generic IDs derived from entities)
        val gen = repository.createGeneratorAtomic(projectId)
        val ats = repository.createAtsAtomic(projectId)
        val ups = repository.createUpsAtomic(projectId)

        // 2. Insert circuits referencing them, with real panel source designations and unassigned sources
        val circGen = CircuitEntity(
            id = 101,
            projectId = projectId,
            circuitName = "Gen Circuit",
            loadKVA = 50.0,
            assignedGeneratorId = gen.generatorId,
            generatorIncluded = true,
            fedFrom = "MDB-1",
            sourceType = "PANEL"
        )
        val circAts = CircuitEntity(
            id = 102,
            projectId = projectId,
            circuitName = "Ats Circuit",
            loadKVA = 30.0,
            assignedAtsId = ats.atsId,
            atsIncluded = true,
            fedFrom = "MDB-1",
            sourceType = "PANEL"
        )
        val circUps = CircuitEntity(
            id = 103,
            projectId = projectId,
            circuitName = "Ups Circuit",
            loadKVA = 20.0,
            assignedUpsId = ups.upsId,
            upsBacked = true,
            fedFrom = "MDB-1",
            sourceType = "PANEL"
        )
        val circUnassigned = CircuitEntity(
            id = 104,
            projectId = projectId,
            circuitName = "Unassigned Source Circuit",
            loadKVA = 15.0,
            assignedGeneratorId = gen.generatorId,
            generatorIncluded = true,
            fedFrom = "Unassigned",
            sourceType = "UNASSIGNED"
        )
        repository.insertCircuit(circGen)
        repository.insertCircuit(circAts)
        repository.insertCircuit(circUps)
        repository.insertCircuit(circUnassigned)

        // 3. Delete all equipment down to zero
        repository.deleteGenerator(projectId, gen.generatorId)
        repository.deleteAts(projectId, ats.atsId)
        repository.deleteUps(projectId, ups.upsId)

        // 4. Assert collections in repository are empty
        assertTrue("Generators list must be empty", repository.powerSystemsDao.getAllGeneratorsList(projectId).isEmpty())
        assertTrue("ATS list must be empty", repository.powerSystemsDao.getAllAtsList(projectId).isEmpty())
        assertTrue("UPS list must be empty", repository.powerSystemsDao.getAllUpsList(projectId).isEmpty())

        // 5. Assert all circuits have cleared assignments and inclusion flags, without fabricating 'Transformer'
        val c1After = repository.circuitDao.getCircuitById(101)!!
        assertNull("assignedGeneratorId must be null", c1After.assignedGeneratorId)
        assertFalse("generatorIncluded must be false", c1After.generatorIncluded)
        assertEquals("fedFrom must remain MDB-1 (not fabricated to Transformer)", "MDB-1", c1After.fedFrom)
        assertEquals("sourceType must remain PANEL", "PANEL", c1After.sourceType)

        val c2After = repository.circuitDao.getCircuitById(102)!!
        assertNull("assignedAtsId must be null", c2After.assignedAtsId)
        assertFalse("atsIncluded must be false", c2After.atsIncluded)
        assertEquals("fedFrom must remain MDB-1", "MDB-1", c2After.fedFrom)
        assertEquals("sourceType must remain PANEL", "PANEL", c2After.sourceType)

        val c3After = repository.circuitDao.getCircuitById(103)!!
        assertNull("assignedUpsId must be null", c3After.assignedUpsId)
        assertFalse("upsBacked must be false", c3After.upsBacked)
        assertEquals("fedFrom must remain MDB-1", "MDB-1", c3After.fedFrom)
        assertEquals("sourceType must remain PANEL", "PANEL", c3After.sourceType)

        val c4After = repository.circuitDao.getCircuitById(104)!!
        assertNull("assignedGeneratorId must be null", c4After.assignedGeneratorId)
        assertFalse("generatorIncluded must be false", c4After.generatorIncluded)
        assertEquals("fedFrom must remain Unassigned (not substituted with Transformer)", "Unassigned", c4After.fedFrom)
        assertEquals("sourceType must remain UNASSIGNED", "UNASSIGNED", c4After.sourceType)

        // 6. Re-create instances
        val newGen = repository.createGeneratorAtomic(projectId)
        val newAts = repository.createAtsAtomic(projectId)
        val newUps = repository.createUpsAtomic(projectId)
        assertNotNull("Re-created generator must have valid ID", newGen.generatorId)
        assertNotNull("Re-created ATS must have valid ID", newAts.atsId)
        assertNotNull("Re-created UPS must have valid ID", newUps.upsId)

        // 7. Verify newly created instances calculate with zero inherited loads
        val circuitsAfterReAdd = repository.circuitDao.getCircuitsListForProject(projectId)
        val genResult = GeneratorEngine.calculate(newGen, circuitsAfterReAdd, 100.0)
        assertEquals("Re-created generator must have 0 essential load", 0.0, genResult.essentialLoadKVA, 0.001)

        val atsResult = AtsEngine.calculate(newAts, 100.0, 0.0, 0.0, 0.0, 10.0, circuitsAfterReAdd)
        assertEquals("Re-created ATS must have 0 selected load", 0.0, atsResult.selectedLoad, 0.001)

        val upsResult = UpsEngine.calculate(newUps, circuitsAfterReAdd)
        assertEquals("Re-created UPS must have 0 critical load", 0.0, upsResult.totalCriticalLoadKVA, 0.001)
    }
}
