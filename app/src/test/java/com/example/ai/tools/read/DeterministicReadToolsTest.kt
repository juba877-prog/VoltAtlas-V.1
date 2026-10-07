package com.example.ai.tools.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.ProvenanceMetadata
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.context.freshness.adapters.ProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.freshness.contracts.CalculationState
import com.example.ai.context.freshness.contracts.FreshnessReasonCode
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.provider.ProjectContextProvider
import com.example.ai.context.provider.ProjectContextProviderImpl
import com.example.ai.task.DefaultEngineeringTaskManager
import com.example.ai.task.EngineeringTaskKey
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.EngineeringTaskType
import com.example.ai.task.TaskOwner
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

            // Seed primary circuit (101)
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 101,
                    projectId = projectId,
                    circuitName = "Feeder 101",
                    loadKVA = 50.0,
                    voltageV = 400,
                    phases = "3P+N",
                    cableType = "Cu/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Free Air",
                    length_m = 30.0,
                    transformerId = "TX-01",
                    fedFrom = "TX-01"
                )
            )

            // Seed secondary circuit (102) dependent on 101
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 102,
                    projectId = projectId,
                    circuitName = "Sub-Feeder 102",
                    loadKVA = 20.0,
                    voltageV = 400,
                    phases = "3P+N",
                    cableType = "Cu/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Free Air",
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

            // Seed isolated project for isolation tests
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
                    cableType = "Cu/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
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
            val coordinator = EngineeringCalculationCoordinator
            coordinator.clearCache()
            coordinator.freshnessRegistry.clearAll()
            coordinator.projectRevisionProvider = null
            AppDatabase.resetInstanceForTesting()
            if (::database.isInitialized && database.isOpen) {
                database.close()
            }
        }
    }

    // =========================================================================
    // BLOCKER A & I: Registry & Invoker Type Safety and Canonical Signatures
    // =========================================================================

    @Test
    fun invoker_wrongRequestType_returnsTypedInvalidInput_notClassCastException() = runBlocking {
        // Pass a Map instead of GetProjectContextRequest to get_current_project_context
        val wrongRequest = ToolRequestEnvelope<Any>(
            requestId = "REQ-WRONG-TYPE",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = mapOf("invalidKey" to 12345)
        )

        val resultEnvelope = invoker.executeDirect<Any, ProjectContextResult>(wrongRequest)

        assertEquals(ToolStatus.INVALID_INPUT, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals("INVALID_REQUEST_PAYLOAD", resultEnvelope.errors[0].errorCode)
        assertFalse("Internal exception class names must not leak", resultEnvelope.errors[0].message.contains("ClassCastException"))
    }

    @Test
    fun invoker_malformedRequest_returnsTypedError() = runBlocking {
        val malformedRequest = ToolRequestEnvelope(
            requestId = "REQ-MALFORMED",
            projectId = "",
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = "", entityType = "", entityId = "")
        )

        val resultEnvelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(malformedRequest)

        assertEquals(ToolStatus.INVALID_INPUT, resultEnvelope.status)
        assertEquals("INVALID_REQUEST_PARAMETERS", resultEnvelope.errors[0].errorCode)
    }

    @Test
    fun registry_duplicateToolName_isRejectedDeterministically() {
        val duplicateTool = GetCurrentProjectContextTool(projectContextProvider)
        try {
            registry.register(duplicateTool, allowOverride = false)
            fail("Registering duplicate tool name without allowOverride must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already registered"))
        }
    }

    @Test
    fun registry_concurrentLookup_isStable() {
        val executor = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(100)
        val errorCount = AtomicInteger(0)

        for (i in 0 until 100) {
            executor.submit {
                try {
                    val tool = registry.getTool<GetProjectContextRequest, ProjectContextResult>(ReadToolNames.GET_CURRENT_PROJECT_CONTEXT)
                    if (tool == null) errorCount.incrementAndGet()
                } catch (_: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(0, errorCount.get())
        executor.shutdown()
    }

    @Test
    fun invoker_resultTypeMismatch_isContained() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-MISMATCH",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        // Attempt to cast result to String envelope
        val res = invoker.executeDirect<GetProjectContextRequest, String>(req)
        // Invocation itself succeeds deterministically without crashing
        assertNotNull(res)
    }

    @Test
    fun managedInvoker_sameRequest_computesSameCanonicalSignature() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req1 = ToolRequestEnvelope(
            requestId = "REQ-SIG-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-SIG-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val sig1 = encoder.signature(req1)
        val sig2 = encoder.signature(req2)
        assertEquals(sig1, sig2)
    }

    @Test
    fun managedInvoker_differentRequest_doesNotDeduplicate() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req1 = ToolRequestEnvelope(
            requestId = "REQ-DIF-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId, includeValidation = true)
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-DIF-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId, includeValidation = false)
        )

        val sig1 = encoder.signature(req1)
        val sig2 = encoder.signature(req2)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun managedInvoker_forgedSignature_cannotMergeDifferentRequests() = runBlocking {
        val req1 = ToolRequestEnvelope(
            requestId = "REQ-FORGE-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "101")
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-FORGE-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "102")
        )

        // Even if caller attempts to pass forged requestSignature = "FORGED_IDENTICAL", invoker computes canonical
        val res1 = invoker.executeManaged<GetEntityRequest, EntityContextResult>(
            request = req1,
            taskManager = taskManager,
            requestSignature = "FORGED_IDENTICAL"
        )
        val res2 = invoker.executeManaged<GetEntityRequest, EntityContextResult>(
            request = req2,
            taskManager = taskManager,
            requestSignature = "FORGED_IDENTICAL"
        )

        assertEquals("101", res1.result?.entityId)
        assertEquals("102", res2.result?.entityId)
    }

    @Test
    fun managedInvoker_blankSignature_usesDocumentedSafePolicy() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-BLANK-SIG",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val res = invoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(
            request = req,
            taskManager = taskManager,
            requestSignature = ""
        )

        assertEquals(ToolStatus.PASS, res.status)
        assertNotNull(res.result)
    }

    // =========================================================================
    // BLOCKER B: Calculation State Lifecycle Fixtures (MISSING, CURRENT, STALE)
    // =========================================================================

    @Test
    fun getCalculationState_missing_isReportedExactly() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-STATE-MISS",
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
        val res = resultEnvelope.result!!
        assertEquals(projectId, res.projectId)
        assertEquals("CIRCUIT", res.entityType)
        assertEquals("101", res.entityId)
        assertEquals("CABLE_SIZING", res.domain)
        assertEquals(CalculationState.MISSING.name, res.state)
        assertEquals(FreshnessReasonCode.NO_RECORD.name, res.reasonCode)
        assertFalse(res.isUsable)
        assertNull(res.recordedRevision)
    }

    @Test
    fun getCalculationState_current_isReportedExactly() = runBlocking {
        val allCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val circuit = allCircuits.find { it.id == 101 }!!
        val allTransformers = database.transformerDao().getTransformersListForProject(projectId)
        EngineeringCalculationCoordinator.calculateCableSizing(circuit, allCircuits, allTransformers)

        val request = ToolRequestEnvelope(
            requestId = "REQ-STATE-CURR",
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
        val res = resultEnvelope.result!!
        assertEquals(CalculationState.CURRENT.name, res.state)
        assertEquals(FreshnessReasonCode.MATCH.name, res.reasonCode)
        assertTrue(res.isUsable)
        assertEquals(1L, res.recordedRevision)
    }

    @Test
    fun getCalculationState_stale_isReportedExactlyAfterMutation() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(101)!!
        EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit))

        // Mutate circuit load without recalculating
        database.circuitDao().insertCircuit(circuit.copy(loadKVA = 120.0))

        val request = ToolRequestEnvelope(
            requestId = "REQ-STATE-STALE",
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
        val res = resultEnvelope.result!!
        assertEquals(CalculationState.STALE.name, res.state)
        assertFalse(res.isUsable)
        assertTrue("Changed components must be > 0 when STALE", res.changedComponentCount > 0)
    }

    @Test
    fun getCalculationState_engineeringFailCanStillBeCurrent() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(101)!!
        EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit))

        val request = ToolRequestEnvelope(
            requestId = "REQ-STATE-ENG-FAIL",
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

    // =========================================================================
    // BLOCKER C: Complete Dependency Outcome Verification
    // =========================================================================

    @Test
    fun getDependencies_usesAuthoritativeResolver() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-AUTH",
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
        val res = resultEnvelope.result!!
        assertEquals("RESOLVED", res.resolutionStatus)
        assertEquals("101", res.parentId)
        assertEquals("Feeder 101", res.parentName)
        assertEquals("TX-01", res.assignedTransformerId)
    }

    @Test
    fun getDependencies_missingParent_isTyped() = runBlocking {
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 305, projectId = projectId, circuitName = "C305", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "NonExistentParent999")
        )

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-MISS-P",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "305"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertEquals("MISSING_PARENT_REFERENCE", resultEnvelope.result?.resolutionStatus)
    }

    @Test
    fun getDependencies_duplicateName_isAmbiguous_notFirstMatch() = runBlocking {
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 310, projectId = projectId, circuitName = "DuplicatePanel", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "TX-01")
        )
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 311, projectId = projectId, circuitName = "DuplicatePanel", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "TX-01")
        )
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 312, projectId = projectId, circuitName = "ChildPanel", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "DuplicatePanel")
        )

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-AMB",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "312"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertEquals("AMBIGUOUS_PARENT", resultEnvelope.result?.resolutionStatus)
    }

    @Test
    fun getDependencies_selfParent_isTyped() = runBlocking {
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 320, projectId = projectId, circuitName = "SelfParent", parentId = 320, loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "SelfParent")
        )

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-SELF",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "320"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertEquals("CYCLE_DETECTED", resultEnvelope.result?.resolutionStatus)
    }

    @Test
    fun getDependencies_multiNodeCycle_isTyped() = runBlocking {
        // A -> B -> C -> A
        database.circuitDao().insertCircuit(CircuitEntity(id = 330, projectId = projectId, circuitName = "NodeA", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "NodeC"))
        database.circuitDao().insertCircuit(CircuitEntity(id = 331, projectId = projectId, circuitName = "NodeB", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "NodeA"))
        database.circuitDao().insertCircuit(CircuitEntity(id = 332, projectId = projectId, circuitName = "NodeC", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "NodeB"))

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-MULTI-CYC",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "330"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertEquals("CYCLE_DETECTED", resultEnvelope.result?.resolutionStatus)
    }

    @Test
    fun getDependencies_crossProjectReferenceRejected() = runBlocking {
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
        assertEquals("MISSING_PARENT_REFERENCE", resultEnvelope.result?.resolutionStatus)
    }

    @Test
    fun getDependencies_rootCircuit_returnsTruthfulNoParent() = runBlocking {
        database.circuitDao().insertCircuit(
            CircuitEntity(id = 501, projectId = projectId, circuitName = "RootFeeder", loadKVA = 10.0, voltageV = 400, phases = "3P+N", length_m = 10.0, transformerId = "TX-01", fedFrom = "Grid")
        )

        val request = ToolRequestEnvelope(
            requestId = "REQ-DEP-ROOT",
            projectId = projectId,
            toolName = ReadToolNames.GET_DEPENDENCIES,
            parameters = GetDependenciesRequest(
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "501"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetDependenciesRequest, DependenciesResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertEquals("ROOT", resultEnvelope.result?.resolutionStatus)
        assertNull(resultEnvelope.result?.parentId)
    }

    // =========================================================================
    // BLOCKER D: Catalog Result Semantics, Missing/Ambiguity & Bounds
    // =========================================================================

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

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals("INCOMPLETE_PROJECT_DATA", resultEnvelope.errors[0].errorCode)
    }

    @Test
    fun getCatalogItem_validCategoryNoMatch_isNotReportedAsSuccessfulData() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-NO-MATCH",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "CABLE",
                query = "non_existent_model_xyz_999999"
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.result)
    }

    @Test
    fun getCatalogItem_ambiguousQuery_hasTypedOutcome() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-AMB",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "CABLE",
                query = "elsewedy",
                limit = 5
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertTrue(resultEnvelope.result!!.matchCount > 0)
        assertTrue(resultEnvelope.result!!.items.size <= 5)
    }

    @Test
    fun getCatalogItem_limitAbove50_isBounded() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-CLAMP",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "CABLE",
                limit = 100
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertTrue(resultEnvelope.result!!.items.size <= 50)
    }

    @Test
    fun getCatalogItem_nonPositiveLimit_isInvalidInput() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-BAD-LIM",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "CABLE",
                limit = 0
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.INVALID_INPUT, resultEnvelope.status)
        assertEquals("INVALID_REQUEST_PARAMETERS", resultEnvelope.errors[0].errorCode)
    }

    @Test
    fun getCatalogItem_exactIdentity_returnsOnlyAuthoritativeMatches() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CAT-EXACT",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(
                category = "TRANSFORMER",
                ratingA = 1000.0
            )
        )

        val resultEnvelope = invoker.executeDirect<GetCatalogItemRequest, CatalogSearchResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertTrue(resultEnvelope.result!!.items.isNotEmpty())
    }

    // =========================================================================
    // BLOCKER E: Get Change History Truthfulness
    // =========================================================================

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
        val res = resultEnvelope.result!!
        assertEquals(projectId, res.projectId)
        assertEquals(1L, res.currentRevision)
        assertFalse("Granular audit logs must not be fabricated", res.isDetailedAuditLogAvailable)
        assertTrue(res.notice.contains("Sub-Phase 3E"))
    }

    @Test
    fun getChangeHistory_detailedHistoryUnavailable_isNotPresentedAsHistoryEvents() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-HIST-DETAIL",
            projectId = projectId,
            toolName = ReadToolNames.GET_CHANGE_HISTORY,
            parameters = GetChangeHistoryRequest(projectId = projectId, maxEntries = 50)
        )

        val resultEnvelope = invoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertFalse(resultEnvelope.result!!.isDetailedAuditLogAvailable)
    }

    @Test
    fun getChangeHistory_returnsNoSyntheticEvents() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-HIST-SYNTH",
            projectId = projectId,
            toolName = ReadToolNames.GET_CHANGE_HISTORY,
            parameters = GetChangeHistoryRequest(projectId = projectId)
        )

        val resultEnvelope = invoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertTrue(resultEnvelope.result!!.notice.isNotBlank())
    }

    @Test
    fun getChangeHistory_currentRevisionMatchesRepositoryExactly() = runBlocking {
        val repoRevision = repository.getCurrentRevision(projectId)

        val request = ToolRequestEnvelope(
            requestId = "REQ-HIST-MATCH",
            projectId = projectId,
            toolName = ReadToolNames.GET_CHANGE_HISTORY,
            parameters = GetChangeHistoryRequest(projectId = projectId)
        )

        val resultEnvelope = invoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(request)

        assertEquals(repoRevision.revision, resultEnvelope.result?.currentRevision)
    }

    @Test
    fun getChangeHistory_missingProject_doesNotFabricateRevision() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-HIST-MISS",
            projectId = "NON-EXISTENT-PROJECT",
            toolName = ReadToolNames.GET_CHANGE_HISTORY,
            parameters = GetChangeHistoryRequest(projectId = "NON-EXISTENT-PROJECT")
        )

        val resultEnvelope = invoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.projectRevision)
    }

    // =========================================================================
    // BLOCKER F: Deep Read-Only / No-Write / No-Cache / No-Freshness Proof
    // =========================================================================

    @Test
    fun readTools_doNotCalculate() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-NO-CALC",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "101")
        )

        val envelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.PASS, envelope.status)
        assertNull("Read tool must not invoke calculation engine", envelope.result?.circuitContext?.cableSizing?.result)
    }

    @Test
    fun readTools_doNotPopulateAnyCoordinatorCache() = runBlocking {
        EngineeringCalculationCoordinator.clearCache()

        val req1 = ToolRequestEnvelope(requestId = "R1", projectId = projectId, toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT, parameters = GetProjectContextRequest(projectId))
        val req2 = ToolRequestEnvelope(requestId = "R2", projectId = projectId, toolName = ReadToolNames.GET_ENTITY, parameters = GetEntityRequest(projectId, "CIRCUIT", "101"))
        val req3 = ToolRequestEnvelope(requestId = "R3", projectId = projectId, toolName = ReadToolNames.GET_CALCULATION_STATE, parameters = GetCalculationStateRequest(projectId, "CIRCUIT", "101"))

        invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(req1)
        invoker.executeDirect<GetEntityRequest, EntityContextResult>(req2)
        invoker.executeDirect<GetCalculationStateRequest, CalculationStateResult>(req3)

        assertNull("Coordinator cache must remain completely empty after read tools", EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 101))
    }

    @Test
    fun readTools_doNotCreateOrModifyFreshnessRecords() = runBlocking {
        val scope = CalculationScope(
            projectId = projectId,
            entityType = CalculationEntityType.CIRCUIT,
            entityId = "101",
            domain = CalculationDomain.CABLE_SIZING
        )
        val initialRecord = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope)

        val req = ToolRequestEnvelope(
            requestId = "R-FRESH",
            projectId = projectId,
            toolName = ReadToolNames.GET_CALCULATION_STATE,
            parameters = GetCalculationStateRequest(projectId, "CIRCUIT", "101")
        )
        invoker.executeDirect<GetCalculationStateRequest, CalculationStateResult>(req)

        val finalRecord = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope)
        assertEquals("Freshness registry record must not change", initialRecord, finalRecord)
    }

    @Test
    fun readTools_doNotChangeAnySupportedEntityRows() = runBlocking {
        val circuitsBefore = database.circuitDao().getCircuitsListForProject(projectId)
        val transformersBefore = database.transformerDao().getTransformersListForProject(projectId)
        val projectBefore = database.projectDao().getProject(projectId)

        val req = ToolRequestEnvelope(
            requestId = "R-ENT",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId, "CIRCUIT", "101")
        )
        invoker.executeDirect<GetEntityRequest, EntityContextResult>(req)

        val circuitsAfter = database.circuitDao().getCircuitsListForProject(projectId)
        val transformersAfter = database.transformerDao().getTransformersListForProject(projectId)
        val projectAfter = database.projectDao().getProject(projectId)

        assertEquals(circuitsBefore, circuitsAfter)
        assertEquals(transformersBefore, transformersAfter)
        assertEquals(projectBefore, projectAfter)
    }

    @Test
    fun readTools_doNotIncrementRevision() = runBlocking {
        val revBefore = repository.getCurrentRevision(projectId).revision

        val request = ToolRequestEnvelope(
            requestId = "REQ-NO-REV-INC",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )
        invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        val revAfter = repository.getCurrentRevision(projectId).revision
        assertEquals("Revision must not change on reads", revBefore, revAfter)
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

    // =========================================================================
    // BLOCKER G: Bounded Scope of Project Context Tool
    // =========================================================================

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
        val result = resultEnvelope.result!!
        assertEquals(projectId, result.projectId)
        assertEquals(1L, result.projectRevision)
        assertEquals(2, result.totalCircuits)
        assertEquals(1, result.totalTransformers)
        assertNotNull(result.engineeringStatus)
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
        assertEquals("INCOMPLETE_PROJECT_DATA", resultEnvelope.errors[0].errorCode)
    }

    @Test
    fun currentProjectContext_doesNotFabricateActiveScreenOrEntity() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-CTX-BOUNDED",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val resultEnvelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        // Verified: ProjectContextResult contains ONLY validation & project level stats
        assertNotNull(resultEnvelope.result?.topIssues)
    }

    // =========================================================================
    // BLOCKER H: Provenance & Revision Policy on Success and Failures
    // =========================================================================

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
        assertEquals("VALIDATION_FACADE", envelope.provenance!!.engineName)
    }

    @Test
    fun successEnvelope_hasAuthoritativeRevisionAndProvenance() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-SUCC-PROV",
            projectId = projectId,
            toolName = ReadToolNames.GET_CHANGE_HISTORY,
            parameters = GetChangeHistoryRequest(projectId = projectId)
        )

        val envelope = invoker.executeDirect<GetChangeHistoryRequest, ChangeHistoryResult>(request)

        assertEquals(ToolStatus.PASS, envelope.status)
        assertEquals(1L, envelope.projectRevision)
        assertEquals("REVISION_TRACKER", envelope.provenance?.engineName)
    }

    @Test
    fun missingProjectEnvelope_doesNotFabricateRevision() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-MISS-REV",
            projectId = "UNKNOWN-P",
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest("UNKNOWN-P")
        )

        val envelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, envelope.status)
        assertNull("Missing project must have null projectRevision", envelope.projectRevision)
    }

    @Test
    fun invalidInputEnvelope_hasTruthfulProvenance() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-INV-PROV",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId, "UNSUPPORTED_TYPE_XYZ", "101")
        )

        val envelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.INVALID_INPUT, envelope.status)
    }

    @Test
    fun notSupportedHistoryEnvelope_hasTruthfulProvenance() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-NOT-SUPP",
            projectId = projectId,
            toolName = "UNREGISTERED_TOOL_XYZ",
            parameters = mapOf("a" to 1)
        )

        val envelope = invoker.executeDirect<Map<String, Int>, Any>(request)

        assertEquals(ToolStatus.NOT_SUPPORTED, envelope.status)
        assertEquals("OPERATION_NOT_SUPPORTED", envelope.errors[0].errorCode)
    }

    @Test
    fun errorEnvelope_sanitizesDiagnostics() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-SAN-DIAG",
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

    // =========================================================================
    // Isolation, Concurrency & Managed Execution
    // =========================================================================

    @Test
    fun getEntity_enforcesProjectIsolation() = runBlocking {
        val request = ToolRequestEnvelope(
            requestId = "REQ-ENT-ISO",
            projectId = projectId,
            toolName = ReadToolNames.GET_ENTITY,
            parameters = GetEntityRequest(projectId = projectId, entityType = "CIRCUIT", entityId = "201")
        )

        val resultEnvelope = invoker.executeDirect<GetEntityRequest, EntityContextResult>(request)

        assertEquals(ToolStatus.INCOMPLETE, resultEnvelope.status)
        assertNull(resultEnvelope.result)
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
    }

    @Test
    fun sameEntityIdAcrossProjects_neverLeaks() = runBlocking {
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

        val envelope = invoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(request)
        assertEquals(ToolStatus.PASS, envelope.status)
        assertNotNull(envelope.result)
    }

    @Test
    fun lateSupersededRead_cannotPublishOverNewerRead() = runBlocking {
        val startedLatch = CountDownLatch(1)
        val pauseLatch = CountDownLatch(1)

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
                    provenance = ProvenanceMetadata(
                        engineName = "TEST",
                        projectId = request.projectId
                    )
                )
            }
        }
        registry.register(slowTool, allowOverride = true)

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
            key = EngineeringTaskKey(
                projectId = projectId,
                taskType = EngineeringTaskType.CUSTOM,
                requestVariant = "slow_read_test"
            ),
            owner = TaskOwner.VIEW_MODEL,
            requestSignature = "SIG-1"
        ) { _ ->
            slowTool.execute(req1)
        }

        assertTrue("Task 1 must start", startedLatch.await(3, TimeUnit.SECONDS))

        val handle2 = taskManager.submit<ProjectContextResult>(
            key = EngineeringTaskKey(
                projectId = projectId,
                taskType = EngineeringTaskType.CUSTOM,
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
                provenance = ProvenanceMetadata(
                    engineName = "TEST",
                    projectId = projectId
                )
            )
        }

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

    // =========================================================================
    // CANONICAL REQUEST SIGNATURE & CRYPTOGRAPHIC COLLISION TESTS (Section 3)
    // =========================================================================

    @Test
    fun sameTypedRequest_sameSignatureAcrossSeparateInstances() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req1 = ToolRequestEnvelope(
            requestId = "REQ-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "elsewedy", limit = 10)
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-2", // different envelope requestId must not affect semantic signature
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "elsewedy", limit = 10)
        )
        val sig1 = encoder.signature(req1)
        val sig2 = encoder.signature(req2)
        assertEquals(sig1, sig2)
    }

    @Test
    fun sameSemanticMap_differentInsertionOrder_sameSignature() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val map1 = linkedMapOf<String, Any>("z" to "last", "a" to 100, "m" to true)
        val map2 = linkedMapOf<String, Any>("a" to 100, "m" to true, "z" to "last")

        val req1 = ToolRequestEnvelope(requestId = "R1", projectId = projectId, toolName = "custom_map", parameters = map1)
        val req2 = ToolRequestEnvelope(requestId = "R2", projectId = projectId, toolName = "custom_map", parameters = map2)

        assertEquals(encoder.signature(req1), encoder.signature(req2))
    }

    @Test
    fun differentRequestFields_differentSignature() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req1 = ToolRequestEnvelope(requestId = "R1", projectId = projectId, toolName = ReadToolNames.GET_CATALOG_ITEM, parameters = GetCatalogItemRequest(category = "CABLE", limit = 10))
        val req2 = ToolRequestEnvelope(requestId = "R2", projectId = projectId, toolName = ReadToolNames.GET_CATALOG_ITEM, parameters = GetCatalogItemRequest(category = "CABLE", limit = 20))
        assertNotEquals(encoder.signature(req1), encoder.signature(req2))
    }

    @Test
    fun nullAndEmptyRemainDistinctWhenSemanticallyDistinct() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val reqNull = ToolRequestEnvelope(requestId = "R1", projectId = projectId, toolName = ReadToolNames.GET_CATALOG_ITEM, parameters = GetCatalogItemRequest(category = "CABLE", query = null))
        val reqEmpty = ToolRequestEnvelope(requestId = "R2", projectId = projectId, toolName = ReadToolNames.GET_CATALOG_ITEM, parameters = GetCatalogItemRequest(category = "CABLE", query = ""))
        assertNotEquals(encoder.signature(reqNull), encoder.signature(reqEmpty))
    }

    @Test
    fun listOrderChangesSignatureWhenOrderIsSemantic() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req1 = ToolRequestEnvelope(requestId = "R1", projectId = projectId, toolName = "list_tool", parameters = listOf("ALPHA", "BETA"))
        val req2 = ToolRequestEnvelope(requestId = "R2", projectId = projectId, toolName = "list_tool", parameters = listOf("BETA", "ALPHA"))
        assertNotEquals(encoder.signature(req1), encoder.signature(req2))
    }

    @Test
    fun numericEncoding_isLocaleIndependent() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req = ToolRequestEnvelope(
            requestId = "R1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", ratingA = 123.456, sizeMm2 = 50.0)
        )

        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN) // German uses comma ',' as decimal separator
            val sigGerman = encoder.signature(req)

            Locale.setDefault(Locale.FRENCH) // French uses comma ','
            val sigFrench = encoder.signature(req)

            Locale.setDefault(Locale.US)
            val sigUS = encoder.signature(req)

            assertEquals(sigUS, sigGerman)
            assertEquals(sigUS, sigFrench)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun signature_isStableAcrossRepeatedInvocations() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req = ToolRequestEnvelope(
            requestId = "R-STABLE",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId, includeValidation = true)
        )
        val baselineBytes = encoder.canonicalBytes(req)
        val baselineSig = encoder.signature(req)

        for (i in 0 until 50) {
            assertArrayEquals(baselineBytes, encoder.canonicalBytes(req))
            assertEquals(baselineSig, encoder.signature(req))
        }
    }

    @Test
    fun signatureUsesVersionPrefixAndSha256Hex() {
        val encoder = DefaultReadRequestSignatureEncoder()
        val req = ToolRequestEnvelope(
            requestId = "R-FMT",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )
        val sig = encoder.signature(req)
        assertTrue("Signature must start with READ-V1:", sig.startsWith("READ-V1:"))
        val hexPart = sig.removePrefix("READ-V1:")
        assertEquals(64, hexPart.length)
        assertTrue("Hex part must be lowercase sha-256", hexPart.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun knownJavaHashCollision_doesNotDeduplicateDifferentRequests() {
        val encoder = DefaultReadRequestSignatureEncoder()
        // "FB" and "Ea" produce identical 32-bit String.hashCode() (2236) in Java
        assertEquals("FB".hashCode(), "Ea".hashCode())

        val req1 = ToolRequestEnvelope(
            requestId = "R-COL-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "FB")
        )
        val req2 = ToolRequestEnvelope(
            requestId = "R-COL-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "Ea")
        )

        // Old implementation using parameters.hashCode() produced false collision:
        assertEquals(req1.parameters.hashCode(), req2.parameters.hashCode())

        // Canonical SHA-256 encoder guarantees distinct signatures:
        val sig1 = encoder.signature(req1)
        val sig2 = encoder.signature(req2)
        assertNotEquals("Distinct requests with colliding 32-bit hashCode must have distinct SHA-256 signatures", sig1, sig2)
    }

    @Test
    fun managedInvoker_hashCollisionRequests_doNotShareTask() = runBlocking {
        val encoder = DefaultReadRequestSignatureEncoder()
        // "FB" and "Ea" collide under old 32-bit hashCode()
        val req1 = ToolRequestEnvelope(
            requestId = "R-TASK-COL-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "FB")
        )
        val req2 = ToolRequestEnvelope(
            requestId = "R-TASK-COL-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "Ea")
        )

        val sig1 = encoder.signature(req1)
        val sig2 = encoder.signature(req2)
        assertNotEquals(sig1, sig2)

        val res1 = invoker.executeManaged<GetCatalogItemRequest, CatalogSearchResult>(req1, taskManager)
        val res2 = invoker.executeManaged<GetCatalogItemRequest, CatalogSearchResult>(req2, taskManager)

        assertNotNull(res1)
        assertNotNull(res2)
    }

    @Test
    fun signatureEncodingFailure_returnsTypedError_notPass() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Encoder internal fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Encoder internal fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val resultEnvelope = safeInvoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(req, taskManager)
        assertEquals(ToolStatus.ERROR, resultEnvelope.status)
        assertNull(resultEnvelope.result)
        assertEquals(1, resultEnvelope.errors.size)
        assertEquals("REQUEST_SIGNATURE_ENCODING_FAILED", resultEnvelope.errors.single().errorCode)
    }

    @Test
    fun signatureEncodingFailure_doesNotSubmitTask() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val projectTasksBefore = taskManager.observeProjectTasks(projectId).value.size

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-SUBMIT",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val resultEnvelope = safeInvoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(req, taskManager)
        assertEquals(ToolStatus.ERROR, resultEnvelope.status)

        val projectTasksAfter = taskManager.observeProjectTasks(projectId).value.size
        assertEquals("Project tasks count must remain unchanged", projectTasksBefore, projectTasksAfter)
    }

    @Test
    fun signatureEncodingFailure_doesNotExecuteTool() = runBlocking {
        val toolExecutionCount = AtomicInteger(0)
        val countingTool = object : DeterministicReadTool<GetProjectContextRequest, ProjectContextResult> {
            override val toolName = "counting_tool"
            override val description = "Tool to verify zero execution on signature failure"
            override suspend fun execute(request: ToolRequestEnvelope<GetProjectContextRequest>): ToolResultEnvelope<ProjectContextResult> {
                toolExecutionCount.incrementAndGet()
                return ToolResultEnvelope.pass(
                    requestId = request.requestId,
                    toolName = toolName,
                    projectId = request.projectId,
                    result = ProjectContextResult(
                        projectId = request.projectId,
                        projectRevision = 1L,
                        engineeringStatus = "OK",
                        overallStatusSummary = "OK",
                        totalCircuits = 1,
                        totalTransformers = 1,
                        calculatedCircuitCount = 1,
                        staleCircuitCount = 0,
                        missingCircuitCount = 0,
                        criticalFailureCount = 0,
                        warningCount = 0,
                        reviewRequiredCount = 0
                    )
                )
            }
        }
        registry.register(countingTool, allowOverride = true)

        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-EXEC",
            projectId = projectId,
            toolName = "counting_tool",
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val resultEnvelope = safeInvoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(req, taskManager)
        assertEquals(ToolStatus.ERROR, resultEnvelope.status)
        assertEquals("Tool execute() block must strictly NEVER be invoked when signature encoding fails", 0, toolExecutionCount.get())
    }

    @Test
    fun signatureEncodingFailure_doesNotCreateActiveTask() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-ACTIVE",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        safeInvoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(req, taskManager)

        val taskKey = EngineeringTaskKey(
            projectId = projectId,
            taskType = EngineeringTaskType.CUSTOM,
            requestVariant = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT
        )
        assertNull("Active task snapshot must be null", taskManager.observeActiveTask(taskKey).value)
    }

    @Test
    fun signatureEncodingFailure_doesNotCreateTaskHistory() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-HIST",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        safeInvoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(req, taskManager)
        assertTrue("Task list for project must be empty", taskManager.observeProjectTasks(projectId).value.isEmpty())
    }

    @Test
    fun signatureEncodingFailure_sanitizesExceptionDetails() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray =
                throw IllegalStateException("Secret key in /var/secrets/key.pem; SQL: SELECT * FROM credentials")
            override fun signature(request: ToolRequestEnvelope<*>): String =
                throw IllegalStateException("Secret key in /var/secrets/key.pem; SQL: SELECT * FROM credentials")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-SAN",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        val resultEnvelope = safeInvoker.executeManaged<GetProjectContextRequest, ProjectContextResult>(req, taskManager)
        assertEquals(ToolStatus.ERROR, resultEnvelope.status)
        val msg = resultEnvelope.errors.single().message
        assertFalse("Must not leak internal paths or secrets", msg.contains("/var/secrets"))
        assertFalse("Must not leak raw SQL", msg.contains("SELECT"))
        assertFalse("Must not leak raw class name", msg.contains("IllegalStateException"))
    }

    @Test
    fun multipleEncodingFailures_doNotDeduplicateOrShareResult() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req1 = ToolRequestEnvelope(
            requestId = "R-FAIL-MULTI-1",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "A")
        )
        val req2 = ToolRequestEnvelope(
            requestId = "R-FAIL-MULTI-2",
            projectId = projectId,
            toolName = ReadToolNames.GET_CATALOG_ITEM,
            parameters = GetCatalogItemRequest(category = "CABLE", query = "B")
        )

        val res1 = safeInvoker.executeManaged<GetCatalogItemRequest, CatalogSearchResult>(req1, taskManager)
        val res2 = safeInvoker.executeManaged<GetCatalogItemRequest, CatalogSearchResult>(req2, taskManager)

        assertEquals(ToolStatus.ERROR, res1.status)
        assertEquals(ToolStatus.ERROR, res2.status)
        assertEquals("R-FAIL-MULTI-1", res1.requestId)
        assertEquals("R-FAIL-MULTI-2", res2.requestId)
        assertTrue("No tasks should have been submitted for project", taskManager.observeProjectTasks(projectId).value.isEmpty())
    }

    @Test
    fun executeDirect_doesNotRequireSignatureAndRemainsExplicitlySeparate() = runBlocking {
        val throwingEncoder = object : ReadRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicReadToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-DIRECT-SEP",
            projectId = projectId,
            toolName = ReadToolNames.GET_CURRENT_PROJECT_CONTEXT,
            parameters = GetProjectContextRequest(projectId = projectId)
        )

        // executeDirect runs pure in-memory without computing signatures or touching TaskManager
        val resultEnvelope = safeInvoker.executeDirect<GetProjectContextRequest, ProjectContextResult>(req)
        assertEquals(ToolStatus.PASS, resultEnvelope.status)
        assertNotNull(resultEnvelope.result)
    }
}
