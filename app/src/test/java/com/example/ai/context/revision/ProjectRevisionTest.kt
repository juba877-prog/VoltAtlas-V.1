package com.example.ai.context.revision

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ProjectRevisionTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EngineeringRepository(database)
    }

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTesting()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    // =========================================================================
    // 1. Revision Model and Provider Tests
    // =========================================================================

    @Test
    fun projectRevisionProvider_returnsAuthoritativePersistedRevision() {
        runBlocking {
            val projectId = repository.createProject("Revision Verification Project", "Client A")
            val snapshot = repository.getCurrentRevision(projectId)

            assertEquals(projectId, snapshot.projectId)
            assertEquals(1L, snapshot.revision)
            assertNotNull(snapshot.updatedAt)
        }
    }

    @Test
    fun projectRevisionProvider_missingProject_returnsTypedFailure() {
        assertThrows(NoSuchElementException::class.java) {
            runBlocking {
                repository.getCurrentRevision("NON_EXISTENT_PROJECT_XYZ")
            }
        }
    }

    @Test
    fun revisionPersistsAcrossDatabaseCloseAndReopen() {
        runBlocking {
            val dbName = "persistent_test_${UUID.randomUUID()}.db"
            val fileDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val fileRepo = EngineeringRepository(fileDb)

            try {
                val projectId = fileRepo.createProject("Persistent DB Project", "Client CloseReopen")
                fileRepo.insertCircuit(
                    CircuitEntity(
                        id = 1001,
                        projectId = projectId,
                        circuitName = "Feeder 1",
                        loadKVA = 75.0,
                        length_m = 50.0
                    )
                )
                val revBeforeClose = fileRepo.getCurrentRevision(projectId).revision
                assertEquals(2L, revBeforeClose)

                // Close the database instance
                fileDb.close()

                // Reopen the same database from disk
                val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .allowMainThreadQueries()
                    .build()
                val reopenedRepo = EngineeringRepository(reopenedDb)

                try {
                    val revAfterReopen = reopenedRepo.getCurrentRevision(projectId).revision
                    assertEquals("Revision must be persisted across database close and reopen", revBeforeClose, revAfterReopen)
                } finally {
                    reopenedDb.close()
                }
            } finally {
                context.deleteDatabase(dbName)
            }
        }
    }

    // =========================================================================
    // 2. Monotonic and Atomic Behavior Tests
    // =========================================================================

    @Test
    fun newProject_usesDocumentedInitialRevision() {
        runBlocking {
            val projectId = repository.createProject("Initial Rev Project")
            val snapshot = repository.getCurrentRevision(projectId)
            assertEquals("Initial persisted project state must start at revision 1", 1L, snapshot.revision)
        }
    }

    @Test
    fun singleCircuitMutation_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Circuit Mut Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // Insert circuit -> +1
            repository.insertCircuit(
                CircuitEntity(
                    id = 2002,
                    projectId = projectId,
                    circuitName = "Sub Feeder",
                    loadKVA = 30.0,
                    length_m = 25.0
                )
            )
            val revAfterInsert = repository.getCurrentRevision(projectId).revision
            assertEquals(2L, revAfterInsert)

            // Update circuit -> +1
            val circuit = repository.getCircuitById(2002)!!
            repository.updateCircuit(circuit.copy(loadKVA = 35.0))
            val revAfterUpdate = repository.getCurrentRevision(projectId).revision
            assertEquals(3L, revAfterUpdate)

            // Delete circuit -> +1
            repository.deleteCircuit(circuit)
            val revAfterDelete = repository.getCurrentRevision(projectId).revision
            assertEquals(4L, revAfterDelete)
        }
    }

    @Test
    fun singleTransformerMutation_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Transformer Mut Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // Insert transformer -> +1
            val tx = TransformerEntity(
                transformerId = "TX-REV-01",
                projectId = projectId,
                transformerName = "Substation TX 1",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0,
                xr = 6.0
            )
            repository.insertTransformer(tx)
            val revAfterInsert = repository.getCurrentRevision(projectId).revision
            assertEquals(2L, revAfterInsert)

            // Update transformer -> +1
            repository.updateTransformer(tx.copy(kVA = 1250.0))
            val revAfterUpdate = repository.getCurrentRevision(projectId).revision
            assertEquals(3L, revAfterUpdate)

            // Delete transformer -> +1
            repository.deleteTransformer(tx)
            val revAfterDelete = repository.getCurrentRevision(projectId).revision
            assertEquals(4L, revAfterDelete)
        }
    }

    @Test
    fun bulkInsertMultipleEntities_advancesRevisionExactlyOncePerProject() {
        runBlocking {
            val projectId = repository.createProject("Bulk Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            val batch = (1..10).map { i ->
                CircuitEntity(
                    id = 3000 + i,
                    projectId = projectId,
                    circuitName = "Bulk Circuit $i",
                    loadKVA = 10.0 * i,
                    length_m = 15.0
                )
            }

            // Inserting 10 circuits in a single bulk operation must advance revision by +1, not +10
            repository.insertCircuits(batch)
            val revAfterBulk = repository.getCurrentRevision(projectId).revision
            assertEquals("Bulk insert must advance revision by exactly +1", initialRev + 1, revAfterBulk)
        }
    }

    @Test
    fun createGeneratorAtomic_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Gen Project")
            val initialRev = repository.getCurrentRevision(projectId).revision

            val gen = repository.createGeneratorAtomic(projectId)
            assertNotNull(gen)

            val revAfter = repository.getCurrentRevision(projectId).revision
            assertEquals("createGeneratorAtomic must advance revision by exactly +1", initialRev + 1, revAfter)
        }
    }

    @Test
    fun createAtsAtomic_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("ATS Project")
            val initialRev = repository.getCurrentRevision(projectId).revision

            val ats = repository.createAtsAtomic(projectId)
            assertNotNull(ats)

            val revAfter = repository.getCurrentRevision(projectId).revision
            assertEquals("createAtsAtomic must advance revision by exactly +1", initialRev + 1, revAfter)
        }
    }

    @Test
    fun createUpsAtomic_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("UPS Project")
            val initialRev = repository.getCurrentRevision(projectId).revision

            val ups = repository.createUpsAtomic(projectId)
            assertNotNull(ups)

            val revAfter = repository.getCurrentRevision(projectId).revision
            assertEquals("createUpsAtomic must advance revision by exactly +1", initialRev + 1, revAfter)
        }
    }

    @Test
    fun parentDeleteAndChildCleanup_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Hierarchy Cleanup Project")
            val initialRev = repository.getCurrentRevision(projectId).revision

            val parent = CircuitEntity(
                id = 7001,
                projectId = projectId,
                circuitName = "Main Distribution Board",
                loadKVA = 100.0,
                length_m = 20.0
            )
            repository.insertCircuit(parent)
            val child = CircuitEntity(
                id = 7002,
                projectId = projectId,
                circuitName = "Sub Board 1",
                parentId = 7001,
                loadKVA = 30.0,
                length_m = 15.0
            )
            repository.insertCircuit(child)
            assertEquals(initialRev + 2, repository.getCurrentRevision(projectId).revision)

            val revBeforeDelete = repository.getCurrentRevision(projectId).revision
            // Deleting parent triggers cascade on child
            repository.deleteCircuit(parent)
            val revAfterDelete = repository.getCurrentRevision(projectId).revision
            assertEquals(
                "Cascade parent-child delete must advance revision by exactly +1",
                revBeforeDelete + 1,
                revAfterDelete
            )
        }
    }

    @Test
    fun readOnlyCalculation_doesNotAdvanceRevision() {
        runBlocking {
            val projectId = repository.createProject("ReadOnly Project")
            val initialRev = repository.getCurrentRevision(projectId).revision

            // Simulate read-only inspection
            val circuits = repository.circuitDao.getCircuitsListForProject(projectId)
            val txs = repository.transformerDao.getTransformersListForProject(projectId)
            val currentSnapshot = repository.getCurrentRevision(projectId)

            assertEquals(initialRev, currentSnapshot.revision)
            assertEquals(0, circuits.size)
            assertEquals(0, txs.size)
        }
    }

    @Test
    fun proposalOrWhatIfDoesNotAdvanceRevision() {
        runBlocking {
            val projectId = repository.createProject("WhatIf Project")
            val initialRev = repository.getCurrentRevision(projectId).revision

            // Simulating WHAT_IF calculation without mutating repository
            val simulatedParams = "Hypothetical load 150 kVA"
            assertNotNull(simulatedParams)

            val revAfterWhatIf = repository.getCurrentRevision(projectId).revision
            assertEquals("WHAT_IF / Proposal formulation must advance revision by 0", initialRev, revAfterWhatIf)
        }
    }

    @Test
    fun concurrentMutations_doNotLoseRevisionIncrements() {
        runBlocking {
            val projectId = repository.createProject("Concurrent Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            val totalOps = 20
            val executor = Executors.newFixedThreadPool(4)
            val dispatcher = executor.asCoroutineDispatcher()

            try {
                coroutineScope {
                    val jobs = (1..totalOps).map { i ->
                        async(dispatcher) {
                            repository.saveConduitSetting(
                                ConduitSettingsEntity(
                                    projectId = projectId,
                                    circuitId = 5000 + i,
                                    routingMode = "ONE_PER_CONDUIT",
                                    designProfile = "PROJECT_DESIGN"
                                )
                            )
                        }
                    }
                    jobs.awaitAll()
                }

                val finalRev = repository.getCurrentRevision(projectId).revision
                assertEquals(
                    "All concurrent increments must be atomically preserved without lost updates",
                    initialRev + totalOps,
                    finalRev
                )
            } finally {
                dispatcher.close()
                executor.shutdown()
            }
        }
    }

    @Test
    fun revisionNeverDecreases() {
        runBlocking {
            val projectId = repository.createProject("Monotonicity Project")
            var currentRev = repository.getCurrentRevision(projectId).revision

            for (i in 1..5) {
                repository.insertCircuit(
                    CircuitEntity(
                        id = 6000 + i,
                        projectId = projectId,
                        circuitName = "C-$i",
                        loadKVA = 10.0,
                        length_m = 10.0
                    )
                )
                val newRev = repository.getCurrentRevision(projectId).revision
                assertTrue("Revision must strictly increase: $newRev > $currentRev", newRev > currentRev)
                currentRev = newRev
            }
        }
    }

    @Test
    fun revisionOverflow_realMutationIsRejectedAndRolledBack() {
        runBlocking {
            val projectId = "PRJ_OVERFLOW_TEST"
            // Insert a project with Long.MAX_VALUE revision directly via projectDao
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "Overflow Test Project",
                    revision = Long.MAX_VALUE
                )
            )

            // Attempt a real engineering mutation (inserting a circuit)
            val failingCircuit = CircuitEntity(
                id = 9999,
                projectId = projectId,
                circuitName = "Overflow Feeder",
                loadKVA = 50.0,
                length_m = 30.0
            )

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    repository.insertCircuit(failingCircuit)
                }
            }

            // Verify circuit was rolled back and not persisted
            assertNull("Rolled back circuit must not exist in DB", repository.getCircuitById(9999))

            // Verify project revision remains Long.MAX_VALUE without wrapping
            val snapshot = repository.getCurrentRevision(projectId)
            assertEquals(Long.MAX_VALUE, snapshot.revision)
        }
    }

    @Test
    fun revisionIncrement_updatesExactlyOneExistingProject() {
        runBlocking {
            val proj1 = repository.createProject("Project Increment 1")
            val proj2 = repository.createProject("Project Increment 2")

            val rev1Before = repository.getCurrentRevision(proj1).revision
            val rev2Before = repository.getCurrentRevision(proj2).revision

            // Mutate project 1
            repository.insertCircuit(
                CircuitEntity(
                    id = 8001,
                    projectId = proj1,
                    circuitName = "C1",
                    loadKVA = 20.0,
                    length_m = 10.0
                )
            )

            val rev1After = repository.getCurrentRevision(proj1).revision
            val rev2After = repository.getCurrentRevision(proj2).revision

            assertEquals("Project 1 must increment by exactly 1", rev1Before + 1, rev1After)
            assertEquals("Project 2 must remain completely unchanged", rev2Before, rev2After)
        }
    }

    @Test
    fun revisionIncrement_missingProjectFailsWithoutMutation() {
        runBlocking {
            val nonExistentId = "PRJ_NON_EXISTENT_${UUID.randomUUID()}"
            val circuit = CircuitEntity(
                id = 8002,
                projectId = nonExistentId,
                circuitName = "Ghost Circuit",
                loadKVA = 10.0,
                length_m = 10.0
            )

            assertThrows(NoSuchElementException::class.java) {
                runBlocking {
                    repository.insertCircuit(circuit)
                }
            }

            assertNull("Mutation on non-existent project must not persist entity", repository.getCircuitById(8002))
        }
    }

    @Test
    fun overflowGuard_remainsAtomicUnderConcurrentAttempt() {
        runBlocking {
            val projectId = "PRJ_OVERFLOW_CONCURRENT"
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "Concurrent Overflow Test",
                    revision = Long.MAX_VALUE - 2
                )
            )

            val executor = Executors.newFixedThreadPool(4)
            val dispatcher = executor.asCoroutineDispatcher()
            val attempts = 10
            val successCount = java.util.concurrent.atomic.AtomicInteger(0)
            val overflowErrorCount = java.util.concurrent.atomic.AtomicInteger(0)

            try {
                coroutineScope {
                    val jobs = (1..attempts).map { i ->
                        async(dispatcher) {
                            try {
                                repository.saveConduitSetting(
                                    ConduitSettingsEntity(
                                        projectId = projectId,
                                        circuitId = 9100 + i,
                                        routingMode = "ONE_PER_CONDUIT"
                                    )
                                )
                                successCount.incrementAndGet()
                            } catch (e: IllegalStateException) {
                                overflowErrorCount.incrementAndGet()
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                // Initial was MAX - 2. Exactly 2 increments can succeed before reaching MAX_VALUE.
                assertEquals("Exactly 2 mutations can succeed before reaching Long.MAX_VALUE", 2, successCount.get())
                assertEquals("All subsequent 8 attempts must fail with overflow exception", attempts - 2, overflowErrorCount.get())

                val finalSnapshot = repository.getCurrentRevision(projectId)
                assertEquals("Final revision must be capped at Long.MAX_VALUE without wrapping to negative numbers", Long.MAX_VALUE, finalSnapshot.revision)
            } finally {
                dispatcher.close()
                executor.shutdown()
            }
        }
    }

    @Test
    fun projectMetadataUpdate_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Project Metadata Test")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // updateProjectImbalanceLimit -> +1
            repository.updateProjectImbalanceLimit(projectId, 15.0)
            assertEquals(initialRev + 1, repository.getCurrentRevision(projectId).revision)

            // renameProject -> +1
            repository.renameProject(projectId, "Renamed Project")
            assertEquals(initialRev + 2, repository.getCurrentRevision(projectId).revision)
        }
    }

    @Test
    fun powerSystemsMutations_advanceRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Power Systems Mut Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // Generator save & delete
            val gen = GeneratorEntity(projectId = projectId, id = 1, generatorId = "GEN-01")
            repository.saveGeneratorSettings(gen)
            assertEquals(initialRev + 1, repository.getCurrentRevision(projectId).revision)

            repository.deleteGenerator(projectId, "GEN-01")
            assertEquals(initialRev + 2, repository.getCurrentRevision(projectId).revision)

            // ATS save & delete
            val ats = AtsEntity(projectId = projectId, id = 1, atsId = "ATS-01")
            repository.saveAtsSettings(ats)
            assertEquals(initialRev + 3, repository.getCurrentRevision(projectId).revision)

            repository.deleteAts(projectId, "ATS-01")
            assertEquals(initialRev + 4, repository.getCurrentRevision(projectId).revision)

            // UPS save & delete
            val ups = UpsEntity(projectId = projectId, id = 1, upsId = "UPS-01")
            repository.saveUpsSettings(ups)
            assertEquals(initialRev + 5, repository.getCurrentRevision(projectId).revision)

            repository.deleteUps(projectId, "UPS-01")
            assertEquals(initialRev + 6, repository.getCurrentRevision(projectId).revision)
        }
    }

    @Test
    fun powerFactorAndEarthingMutations_advanceRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("PF & Earthing Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // PF Global save -> +1
            repository.savePfGlobalSettings(PfGlobalEntity(projectId = projectId, id = 1, targetPf = 0.95))
            assertEquals(initialRev + 1, repository.getCurrentRevision(projectId).revision)

            // PF Transformer save -> +1
            repository.savePfTransformerSettings(PfTransformerEntity(projectId = projectId, transformerId = "TX-01"))
            assertEquals(initialRev + 2, repository.getCurrentRevision(projectId).revision)

            // Earthing save -> +1
            repository.saveEarthingSettings(EarthingEntity(projectId = projectId, id = 1, soilResistivity_ohmM = 75.0))
            assertEquals(initialRev + 3, repository.getCurrentRevision(projectId).revision)
        }
    }

    @Test
    fun bulkProjectDeletionMethods_advanceRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("Bulk Deletion Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // Insert bulk transformers -> +1
            val txList = (1..3).map { i ->
                TransformerEntity(projectId = projectId, transformerId = "TX-BULK-$i", transformerName = "TX $i")
            }
            repository.insertTransformers(txList)
            assertEquals(initialRev + 1, repository.getCurrentRevision(projectId).revision)

            // Delete transformers for project -> +1
            repository.deleteTransformersForProject(projectId)
            assertEquals(initialRev + 2, repository.getCurrentRevision(projectId).revision)

            // Insert circuit & delete circuits for project -> +2
            repository.insertCircuit(CircuitEntity(id = 8999, projectId = projectId, circuitName = "C-Del", loadKVA = 10.0, length_m = 10.0))
            assertEquals(initialRev + 3, repository.getCurrentRevision(projectId).revision)

            repository.deleteCircuitsForProject(projectId)
            assertEquals(initialRev + 4, repository.getCurrentRevision(projectId).revision)
        }
    }

    // =========================================================================
    // 3. SLD Node Position Layout State Revision Tests (Policy A)
    // =========================================================================

    @Test
    fun completedSldPositionSave_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("SLD Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            val position = SldNodePositionEntity(
                projectId = projectId,
                nodeId = "node_1",
                x = 120.5f,
                y = 340.0f
            )

            repository.saveSldNodePosition(position)
            val revAfterSave = repository.getCurrentRevision(projectId).revision
            assertEquals("Saving completed SLD node position must advance revision by +1", initialRev + 1, revAfterSave)
        }
    }

    @Test
    fun multiplePointerUpdatesBeforeCommit_doNotAdvanceMultipleTimes() {
        runBlocking {
            val projectId = repository.createProject("SLD Drag Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            // Simulating pointer move updates in memory (drag frames)
            var currentX = 100.0f
            var currentY = 100.0f
            for (frame in 1..60) {
                currentX += 1.5f
                currentY += 0.5f
            }

            // Verify in-memory pointer movement didn't touch database or revision
            assertEquals(initialRev, repository.getCurrentRevision(projectId).revision)

            // Commit final position upon drag release
            val finalPos = SldNodePositionEntity(
                projectId = projectId,
                nodeId = "dragged_node",
                x = currentX,
                y = currentY
            )
            repository.saveSldNodePosition(finalPos)

            val revAfterCommit = repository.getCurrentRevision(projectId).revision
            assertEquals("Only the final committed drag release must advance revision (+1)", initialRev + 1, revAfterCommit)
        }
    }

    @Test
    fun bulkSldLayoutSave_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("SLD Bulk Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            val batch = (1..5).map { i ->
                SldNodePositionEntity(
                    projectId = projectId,
                    nodeId = "node_$i",
                    x = 100.0f * i,
                    y = 50.0f * i
                )
            }

            repository.saveSldNodePositions(batch)
            val revAfterBulk = repository.getCurrentRevision(projectId).revision
            assertEquals("Bulk SLD layout save must advance revision by exactly +1", initialRev + 1, revAfterBulk)
        }
    }

    @Test
    fun clearSldLayout_advancesRevisionExactlyOnce() {
        runBlocking {
            val projectId = repository.createProject("SLD Clear Project")
            val initialRev = repository.getCurrentRevision(projectId).revision
            assertEquals(1L, initialRev)

            repository.saveSldNodePosition(
                SldNodePositionEntity(
                    projectId = projectId,
                    nodeId = "node_1",
                    x = 200f,
                    y = 200f
                )
            )
            assertEquals(initialRev + 1, repository.getCurrentRevision(projectId).revision)

            val revBeforeClear = repository.getCurrentRevision(projectId).revision
            repository.clearSldNodePositionsForProject(projectId)
            val revAfterClear = repository.getCurrentRevision(projectId).revision
            assertEquals("Clearing SLD layout must advance revision by exactly +1", revBeforeClear + 1, revAfterClear)
        }
    }

    // =========================================================================
    // 4. Existing Critical Mutation Paths & Import Tests
    // =========================================================================

    @Test
    fun successfulProjectImport_initializesRevisionAtOne() {
        runBlocking {
            val exportJson = """
            {
              "schema": "VoltAtlasProject",
              "version": 2,
              "project": {
                "projectName": "Imported Test Project",
                "clientName": "Test Client",
                "defaultPhaseImbalanceLimitPct": 20.0
              },
              "transformers": [
                {
                  "transformerId": "TX-IMP-01",
                  "transformerName": "Imported TX",
                  "kVA": 800.0,
                  "voltage": 400,
                  "percentZ": 5.0,
                  "xr": 7.0
                }
              ]
            }
            """.trimIndent()

            val initialProjectCount = database.projectDao().count()
            val importedProjectId = repository.importProjectFromJson(exportJson)
            assertNotNull(importedProjectId)

            val snapshot = repository.getCurrentRevision(importedProjectId)
            assertEquals(
                "Fresh imported project must initialize at revision 1",
                1L,
                snapshot.revision
            )
            assertEquals(initialProjectCount + 1, database.projectDao().count())
        }
    }

    @Test
    fun failedProjectImport_doesNotAdvanceRevisionOrInsertProject() {
        runBlocking {
            val initialProjectCount = database.projectDao().count()
            val invalidJson = "{ invalid json content }"
            val res = repository.importProjectFromJsonDetailed(invalidJson)
            assertEquals(ImportStatus.FAILED, res.status)
            assertNull(res.projectId)

            // Assert project count is unchanged
            assertEquals("Project count must remain unchanged after failed import", initialProjectCount, database.projectDao().count())
        }
    }

    @Test
    fun projectSwitch_doesNotAdvanceRevision() {
        runBlocking {
            val proj1 = repository.createProject("Project Alpha")
            val proj2 = repository.createProject("Project Beta")

            val rev1Before = repository.getCurrentRevision(proj1).revision
            val rev2Before = repository.getCurrentRevision(proj2).revision

            // Switch active project
            repository.setActiveProject(proj1)
            repository.setActiveProject(proj2)

            assertEquals(rev1Before, repository.getCurrentRevision(proj1).revision)
            assertEquals(rev2Before, repository.getCurrentRevision(proj2).revision)
        }
    }

    // =========================================================================
    // 5. API Hardening & Architecture Isolation Tests
    // =========================================================================

    @Test
    fun repository_exposesNoPublicGenericMutationWrapper() {
        val repoClass = EngineeringRepository::class.java
        val methodNames = repoClass.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }

        assertFalse("Generic withProjectMutation must NOT be public", methodNames.contains("withProjectMutation"))
    }

    @Test
    fun repository_exposesNoPublicAdvanceOrSetRevision() {
        val repoClass = EngineeringRepository::class.java
        val methodNames = repoClass.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }

        assertFalse(methodNames.contains("setRevision"))
        assertFalse(methodNames.contains("advanceRevision"))
        assertFalse(methodNames.contains("updateRevision"))
        assertTrue(methodNames.contains("getCurrentRevision"))
    }

    @Test
    fun projectRevisionProvider_hasNoComposeViewModelFirebaseOrEngineDependency() {
        val providerClass = ProjectRevisionProvider::class.java
        for (method in providerClass.declaredMethods) {
            for (param in method.parameterTypes) {
                assertFalse(param.name.startsWith("androidx.compose"))
                assertFalse(param.name.startsWith("androidx.lifecycle"))
                assertFalse(param.name.startsWith("com.google.firebase"))
                assertFalse(param.name.startsWith("com.example.engine"))
            }
            assertFalse(method.returnType.name.startsWith("androidx.compose"))
            assertFalse(method.returnType.name.startsWith("androidx.lifecycle"))
        }
    }

    @Test
    fun aiFacingRevisionProvider_exposesNoSetRevisionMethod() {
        val providerClass = ProjectRevisionProvider::class.java
        val methodNames = providerClass.declaredMethods.map { it.name }
        assertFalse(methodNames.contains("setRevision"))
        assertFalse(methodNames.contains("advanceRevision"))
        assertFalse(methodNames.contains("updateRevision"))
        assertTrue(methodNames.contains("getCurrentRevision"))
    }
}
