package com.example.ai.tools.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import java.util.concurrent.TimeUnit
import com.example.ai.context.freshness.adapters.ProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationState
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.provider.ProjectContextProvider
import com.example.ai.context.provider.ProjectContextProviderImpl
import com.example.ai.task.DefaultEngineeringTaskManager
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.TaskOwner
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeterministicReadToolsTest {

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

        private val catalogRepo = CatalogRepository()

        @BeforeClass
        @JvmStatic
        fun loadCatalogData() {
            try {
                val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
                val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
                val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
                val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
                val deratingDataJson = findAssetFile("derating_data.json").readText()
                val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

                catalogRepo.loadAllFromStrings(
                    cableCatalogJson = cableCatalogJson,
                    cableTypesDataJson = cableTypesDataJson,
                    breakerCatalogJson = breakerCatalogJson,
                    accessoryCatalogJson = accessoryCatalogJson,
                    deratingDataJson = deratingDataJson,
                    transformersSeedJson = transformersSeedJson
                )
            } catch (_: Exception) {
            }
        }
    }

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var projectContextProvider: ProjectContextProvider
    private lateinit var freshnessProvider: DefaultCalculationFreshnessProvider
    private lateinit var registry: DeterministicReadToolRegistry
    private lateinit var invoker: DeterministicReadToolInvoker
    private lateinit var taskManager: EngineeringTaskManager

    private val projectId = "PROJ-READ-TOOLS-TEST"
    private val projectOther = "PROJ-OTHER-ISOLATION"

    @Before
    fun setUp() {
        runBlocking {
            AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EngineeringRepository(database)
        val coordinator = EngineeringCalculationCoordinator
        coordinator.projectRevisionProvider = repository
        coordinator.clearCache()
        coordinator.freshnessRegistry.clearAll()

        val freshnessDataProvider = object : ProjectFreshnessDataProvider {
            override suspend fun getCircuits(projectId: String): List<CircuitEntity> =
                repository.circuitDao.getCircuitsListForProject(projectId)
            override suspend fun getTransformers(projectId: String): List<TransformerEntity> =
                repository.transformerDao.getTransformersListForProject(projectId)
        }

        freshnessProvider = DefaultCalculationFreshnessProvider(
            revisionProvider = repository,
            metadataRegistry = coordinator.freshnessRegistry,
            projectDataProvider = freshnessDataProvider
        )

        projectContextProvider = ProjectContextProviderImpl(
            repository = repository,
            coordinator = coordinator,
            freshnessProvider = freshnessProvider,
            revisionProvider = repository
        )

        registry = DeterministicReadToolRegistry.createDefault(
            projectContextProvider = projectContextProvider,
            freshnessProvider = freshnessProvider,
            revisionProvider = repository,
            catalogRepository = catalogRepo
        )

        invoker = DeterministicReadToolInvoker(registry)

        taskManager = DefaultEngineeringTaskManager(
            coroutineScope = CoroutineScope(Dispatchers.Default)
        )

        // Seed primary test project
        database.projectDao().upsertProject(
            ProjectEntity(
                projectId = projectId,
                projectName = "Phase 5 Read Tools Test Project",
                createdAt = 1000L,
                updatedAt = 1000L,
                revision = 1L
            )
        )

        // Seed primary circuit
        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 101,
                projectId = projectId,
                circuitName = "Feeder 101",
                loadKVA = 50.0,
                voltageV = 400,
                phases = "3P+N",
                length_m = 30.0,
                transformerId = "TX-01",
                fedFrom = "TX-01"
            )
        )

        // Seed secondary circuit dependent on 101
        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 102,
                projectId = projectId,
                circuitName = "Sub-Feeder 102",
                loadKVA = 20.0,
                voltageV = 400,
                phases = "3P+N",
                length_m = 15.0,
                transformerId = "TX-01",
                fedFrom = "Feeder 101"
            )
        )

        // Seed primary transformer
        database.transformerDao().insertTransformer(
            TransformerEntity(
                transformerId = "TX-01",
                projectId = projectId,
                transformerName = "Main Unit Substation TX-01",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.75,
                xr = 8.0,
                txType = "ONAN",
                sourceType = "GRID"
            )
        )

        // Seed other project for isolation tests
        database.projectDao().upsertProject(
            ProjectEntity(
                projectId = projectOther,
                projectName = "Isolated Project Other",
                createdAt = 1000L,
                updatedAt = 1000L,
                revision = 1L
            )
        )
        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 201,
                projectId = projectOther,
                circuitName = "Isolated Circuit 201",
                loadKVA = 10.0,
                voltageV = 400,
                phases = "3P+N",
                length_m = 10.0,
                transformerId = "TX-OTHER",
                fedFrom = "TX-OTHER"
            )
        )
    }
}

    @After
    fun tearDown() {
        runBlocking {
            taskManager.close()
            database.close()
        }
    }

    // -------------------------------------------------------------------------
    // 1. get_current_project_context
    // -------------------------------------------------------------------------

    @Test
    fun currentProjectContext_returnsBoundedRealSnapshot() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CTX-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val resultEnvelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        val result = resultEnvelope.result!!
        assertEquals(projectId, result.projectId)
        assertEquals(1L, result.projectRevision)
        assertEquals(2, result.totalCircuits)
        assertEquals(1, result.totalTransformers)
        assertNotNull(result.engineeringStatus)
        assertNotNull(resultEnvelope.provenance)
        assertEquals("VALIDATION_FACADE", resultEnvelope.provenance?.engineName)
    }

    @Test
    fun currentProjectContext_missingProject_returnsTypedMissing() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CTX-2",
            projectId = "NON-EXISTENT-PROJ",
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = "NON-EXISTENT-PROJ")
        )

        val resultEnvelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals(1, resultEnvelope.errors.size)
        assertEquals("INCOMPLETE_PROJECT_DATA", resultEnvelope.errors[0].errorCode)
        assertTrue(resultEnvelope.errors[0].message.contains("NON-EXISTENT-PROJ"))
    }

    // -------------------------------------------------------------------------
    // 2. get_entity
    // -------------------------------------------------------------------------

    @Test
    fun getEntity_enforcesProjectIsolation() = runBlocking {
        // Attempt to access circuit 201 (which belongs to projectOther) using projectId
        val request = ToolRequestEnvelope(
            requestId = "REQ-ENT-ISO",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "201")
        )

        val resultEnvelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals(1, resultEnvelope.errors.size)
        assertTrue(resultEnvelope.errors[0].message.contains("201"))
    }

    @Test
    fun getEntity_missingEntity_doesNotFabricate() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-ENT-MISS",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "9999")
        )

        val resultEnvelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals("INCOMPLETE_PROJECT_DATA", resultEnvelope.errors[0].errorCode)
    }

    // -------------------------------------------------------------------------
    // 3. get_calculation_state
    // -------------------------------------------------------------------------

    @Test
    fun getCalculationState_currentStaleMissingRemainDistinct() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-STATE-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CALCULATION_STATE,
            parameters = GetCalculationStateRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "101",
                domain = CalculationDomain.CABLE_SIZING
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCalculationStateRequest, CalculationStateResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        val res = resultEnvelope.result!!
        assertEquals(projectId, res.projectId)
        assertEquals("CIRCUIT", res.entityType)
        assertEquals("101", res.entityId)
        assertEquals("CABLE_SIZING", res.domain)
        // Before calculation, state is MISSING
        assertEquals(CalculationState.MISSING.name, res.state)
        assertFalse(res.isUsable)
    }

    @Test
    fun getCalculationState_engineeringFailCanStillBeCurrent() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(101)!!
        EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit))

        val request = ToolRequestEnvelope(
            requestId = "REQ-STATE-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_CALCULATION_STATE,
            parameters = GetCalculationStateRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "101",
                domain = CalculationDomain.CABLE_SIZING
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCalculationStateRequest, CalculationStateResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        assertEquals(projectId, resultEnvelope.result?.projectId)
    }

    // -------------------------------------------------------------------------
    // 4. get_dependencies
    // -------------------------------------------------------------------------

    @Test
    fun getDependencies_usesAuthoritativeResolver() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "102"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        val res = resultEnvelope.result!!
        assertEquals("RESOLVED", res.resolutionStatus)
        assertEquals("101", res.parentId)
        assertEquals("Feeder 101", res.parentName)
        assertEquals("TX-01", res.assignedTransformerId)
    }

    @Test
    fun getDependencies_cycleAmbiguityAndMissingAreTyped() = runBlocking {
        // Create 2-node cycle: Circuit 301 feeds 302, 302 feeds 301
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 301, projectId = projectId, circuitName = "C301", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "C302")
        )
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 302, projectId = projectId, circuitName = "C302", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "C301")
        )

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-CYC",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "301"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        assertEquals("CYCLE_DETECTED", resultEnvelope.result?.resolutionStatus)
    }

    @Test
    fun getDependencies_crossProjectReferenceRejected() = runBlocking {
        // Create circuit referring to a parent in another project
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 401, projectId = projectId, circuitName = "C401", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "Isolated Circuit 201")
        )

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-CROSS",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "401"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        assertEquals("MISSING_PARENT_REFERENCE", resultEnvelope.result?.resolutionStatus)
    }

    // -------------------------------------------------------------------------
    // 5. get_change_history
    // -------------------------------------------------------------------------

    @Test
    fun getChangeHistory_neverInventsUnavailableHistory() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-HIST-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CHANGE_HISTORY,
            parameters = GetChangeHistoryRequest(projectId = projectId)
        )

        val resultEnvelope = invoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        val res = resultEnvelope.result!!
        assertEquals(projectId, res.projectId)
        assertEquals(1L, res.currentRevision)
        assertFalse("Granular audit logs must not be fabricated", res.isDetailedAuditLogAvailable)
        assertTrue(res.notice.contains("Sub-Phase 3E"))
    }

    // -------------------------------------------------------------------------
    // 6. get_catalog_item
    // -------------------------------------------------------------------------

    @Test
    fun getCatalogItem_usesRealCatalogIdentity() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "CABLE",
                limit = 10
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        val res = resultEnvelope.result!!
        assertEquals("CABLE", res.category)
        assertNotNull(res.provenance)
    }

    @Test
    fun getCatalogItem_noPhantomManufacturer() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-FAKE",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "BREAKER",
                manufacturer = "NonExistentPhantomManufacturer9999"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
        assertEquals(0, resultEnvelope.result?.matchCount)
        assertEquals(0, resultEnvelope.result?.items?.size)
    }

    @Test
    fun getCatalogItem_ambiguousAndMissingAreTyped() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-BAD-CAT",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "INVALID_UNKNOWN_CATEGORY_XYZ"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.INVALID_INPUT, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals("UNSUPPORTED_CATALOG_CATEGORY", resultEnvelope.errors[0].errorCode)
    }

    // -------------------------------------------------------------------------
    // 7. General Contracts, Bounds, Error Safety & Isolation Tests
    // -------------------------------------------------------------------------

    @Test
    fun allReadResults_includeRevisionAndProvenance() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-REV-PROV",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val envelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        assertEquals(ToolStatus.PASS, envelope.status)
        assertEquals(1L, envelope.projectRevision)
        assertNotNull(envelope.provenance)
        assertFalse(envelope.provenance!!.engineName.isBlank())
        assertEquals("VALIDATION_FACADE", envelope.provenance!!.engineName)
    }

    @Test
    fun readTools_haveBoundedPayloads() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-BOUND",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "CABLE",
                limit = 5
            )
        )

        val envelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.PASS, envelope.status)
        assertTrue((envelope.result?.items?.size ?: 0) <= 5)
    }

    @Test
    fun readTools_doNotCalculate() = runBlocking {
        // Read circuit context before running any calculation
        val request = ToolRequestEnvelope(
            requestId = "REQ-NO-CALC",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "101")
        )

        val envelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.PASS, envelope.status)
        val circuitCtx = envelope.result?.circuitContext
        assertNotNull(circuitCtx)
        // Cable sizing result is null because read tool does NOT invoke calculation
        assertNull("Read tool must not invoke calculation engine", circuitCtx?.cableSizing?.result)
    }

    @Test
    fun readTools_doNotMutateRepositoryRevisionOrFreshness() = runBlocking {
        val revBefore = repository.getCurrentRevision(projectId).revision

        val request = ToolRequestEnvelope(
            requestId = "REQ-NO-MUT",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        val revAfter = repository.getCurrentRevision(projectId).revision
        assertEquals("Read tools must never mutate project revision", revBefore, revAfter)
    }

    @Test
    fun readTools_doNotWriteRoom() = runBlocking {
        val initialCircuitCount = database.circuitDao().getCircuitsListForProject(projectId).size

        val request = ToolRequestEnvelope(
            requestId = "REQ-NO-ROOM",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "101")
        )

        invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        val afterCircuitCount = database.circuitDao().getCircuitsListForProject(projectId).size
        assertEquals(initialCircuitCount, afterCircuitCount)
    }

    @Test
    fun sameEntityIdAcrossProjects_neverLeaks() = runBlocking {
        // Create same transformer ID "TX-COMMON" in projectId and in projectOther
        database.transformerDao().insertTransformer(
            TransformerEntity(transformerId = "TX-COMMON", projectId = projectId, transformerName = "Main TX-COMMON", kVA = 1000.0, voltage = 400, percentZ = 5.0, xr = 8.0, txType = "ONAN", sourceType = "GRID")
        )
        database.transformerDao().insertTransformer(
            TransformerEntity(transformerId = "TX-COMMON", projectId = projectOther, transformerName = "Other TX-COMMON", kVA = 500.0, voltage = 400, percentZ = 4.0, xr = 6.0, txType = "ONAN", sourceType = "GRID")
        )

        val reqMain = ToolRequestEnvelope(
            requestId = "REQ-ISO-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "TRANSFORMER", entityId = "TX-COMMON")
        )
        val reqOther = ToolRequestEnvelope(
            requestId = "REQ-ISO-2",
            projectId = projectOther,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectOther, entityType = "TRANSFORMER", entityId = "TX-COMMON")
        )

        val resMain = invoker.executeDirect<GetEntityRequest, EntityContextResult>(reqMain)
        val resOther = invoker.executeDirect<GetEntityRequest, EntityContextResult>(reqOther)

        assertEquals("Main TX-COMMON", resMain.result?.transformerContext?.transformerName)
        assertEquals("Other TX-COMMON", resOther.result?.transformerContext?.transformerName)
    }

    @Test
    fun structuredErrors_areSanitized() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-ERR-SAN",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "invalid-SELECT * FROM users WHERE path = C:\\secret.txt"
            )
        )

        val envelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.INVALID_INPUT, envelope.status)
        assertFalse(envelope.errors[0].message.contains("SELECT * FROM"))
    }

    @Test
    fun internalInvoker_unknownTool_returnsNotSupported() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-UNK",
            projectId = projectId,
            toolName = "UNKNOWN_NON_EXISTENT_TOOL",
            parameters = mapOf("foo" to "bar")
        )

        val envelope = invoker.executeDirect<Map<String, String>, Any>(request)

        assertEquals(ToolStatus.NOT_SUPPORTED, envelope.status)
        assertEquals("OPERATION_NOT_SUPPORTED", envelope.errors[0].errorCode)
    }

    @Test
    fun internalInvoker_executesWithoutGemini() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-NO-LLM",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        // Invoker runs purely through local in-memory Kotlin facades without Gemini
        val envelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)
        assertEquals(ToolStatus.PASS, envelope.status)
        assertNotNull(envelope.result)
    }

    @Test
    fun lateSupersededRead_cannotPublishOverNewerRead() = runBlocking {
        val startedLatch = java.util.concurrent.CountDownLatch(1)
        val pauseLatch = java.util.concurrent.CountDownLatch(1)

        val slowTool = object : DeterministicReadTool<GetProjectContextRequest, ProjectContextResult> {
            override val toolName = "slow_read_test"
            override val description = "Slow read test tool"
            override suspend fun execute(request: ToolRequestEnvelope<GetProjectContextRequest>): ToolResultEnvelope<ProjectContextResult> {
                startedLatch.countDown()
                pauseLatch.await(5, TimeUnit.SECONDS)
                return ToolResultEnvelope.pass(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = request.projectId,
                    result = ProjectContextResult(
                        projectId = request.projectId,
                        projectRevision = 1L,
                        engineeringStatus = "FULLY_COMPLIANT",
                        overallStatusSummary = "Old Read",
                        totalCircuits = 1,
                        totalTransformers = 1,
                        calculatedCircuitCount = 1,
                        staleCircuitCount = 0,
                        missingCircuitCount = 0,
                        criticalFailureCount = 0,
                        warningCount = 0,
                        reviewRequiredCount = 0
                    ),
                    provenance = com.example.ai.contracts.ProvenanceMetadata(
                        engineName = "TEST",
                        projectId = request.projectId
                    )
                )
            }
        }
        registry.register(slowTool)

        val req1 = ToolRequestEnvelope(
            requestId = "REQ-SUP-1",
            projectId = projectId,
            toolName = "slow_read_test",
            parameters = GetProjectContextRequest(projectId = projectId)
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-SUP-2",
            projectId = projectId,
            toolName = "slow_read_test",
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val handle1 = taskManager.submit<ProjectContextResult>(
            key = com.example.ai.task.EngineeringTaskKey(
                projectId = projectId,
                taskType = com.example.ai.task.EngineeringTaskType.CUSTOM,
                requestVariant = "slow_read_test"
            ),
            owner = TaskOwner.VIEW_MODEL,
            requestSignature = "SIG-1"
        ) { _ ->
            slowTool.execute(req1)
        }

        assertTrue("Task 1 must start", startedLatch.await(3, TimeUnit.SECONDS))

        val handle2 = taskManager.submit<ProjectContextResult>(
            key = com.example.ai.task.EngineeringTaskKey(
                projectId = projectId,
                taskType = com.example.ai.task.EngineeringTaskType.CUSTOM,
                requestVariant = "slow_read_test"
            ),
            owner = TaskOwner.VIEW_MODEL,
            requestSignature = "SIG-2"
        ) { _ ->
            ToolResultEnvelope.pass(
                requestId = req2.requestId,
                toolName = "slow_read_test",
                projectId = projectId,
                result = ProjectContextResult(
                    projectId = projectId,
                    projectRevision = 1L,
                    engineeringStatus = "FULLY_COMPLIANT",
                    overallStatusSummary = "Newer Read",
                    totalCircuits = 1,
                    totalTransformers = 1,
                    calculatedCircuitCount = 1,
                    staleCircuitCount = 0,
                    missingCircuitCount = 0,
                    criticalFailureCount = 0,
                    warningCount = 0,
                    reviewRequiredCount = 0
                ),
                provenance = com.example.ai.contracts.ProvenanceMetadata(
                    engineName = "TEST",
                    projectId = projectId
                )
            )
        }

        // Release slow task 1
        pauseLatch.countDown()

        val res2 = handle2.awaitResult()
        assertEquals(ToolStatus.PASS, res2.status)
        assertEquals("Newer Read", res2.result?.overallStatusSummary)

        val res1 = handle1.awaitResult()
        assertEquals(ToolStatus.CANCELLED, res1.status)
    }

    @Test
    fun ownerCancellation_doesNotCancelOtherSubscriber() = runBlocking {
        val owner1 = TaskOwner.screen("ScreenA")
        val owner2 = TaskOwner.screen("ScreenB")

        val req = ToolRequestEnvelope(
            requestId = "REQ-OWNER-TEST",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val res = invoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(
            request = req,
            taskManager = taskManager,
            owner = owner1,
            requestSignature = "SHARED-SIG"
        )

        assertEquals(ToolStatus.PASS, res.status)
    }
}
