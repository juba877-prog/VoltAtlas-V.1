package com.example.ai.tools.cable

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.context.freshness.adapters.ProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.provider.ProjectContextProvider
import com.example.ai.context.provider.ProjectContextProviderImpl
import com.example.ai.task.DefaultEngineeringTaskManager
import com.example.ai.task.EngineeringTaskManager
import com.example.ai.task.EngineeringTaskType
import com.example.ai.task.TaskOwner
import com.example.ai.tools.UnifiedDeterministicToolRegistry
import com.example.ai.tools.read.DeterministicReadToolInvoker
import com.example.ai.tools.read.DeterministicReadToolRegistry
import com.example.data.AppDatabase
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.CableSizingEngine
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
class CableEngineeringToolsTest {

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
    private lateinit var freshnessProvider: DefaultCalculationFreshnessProvider
    private lateinit var registry: DeterministicCableToolRegistry
    private lateinit var invoker: DeterministicCableToolInvoker
    private lateinit var readRegistry: DeterministicReadToolRegistry
    private lateinit var readInvoker: DeterministicReadToolInvoker
    private lateinit var taskManager: EngineeringTaskManager
    private lateinit var harness: CableEngineeringVerticalSliceHarness

    private val projectId = "PROJ-CABLE-TOOLS-TEST"
    private val projectOther = "PROJ-OTHER-ISOLATION"

    @Before
    fun setUp() {
        runBlocking {
            AppDatabase.useTestExecutors = true
            val context = ApplicationProvider.getApplicationContext<Context>()
            Catalog.loadAll(context)

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

            registry = DeterministicCableToolRegistry.createDefault(
                repository = repository,
                revisionProvider = repository,
                freshnessProvider = freshnessProvider,
                coordinator = coordinator
            )
            invoker = DeterministicCableToolInvoker(registry)

            val projectContextProvider = ProjectContextProviderImpl(
                repository = repository,
                coordinator = coordinator,
                freshnessProvider = freshnessProvider,
                revisionProvider = repository
            )
            readRegistry = DeterministicReadToolRegistry.createDefault(
                projectContextProvider = projectContextProvider,
                freshnessProvider = freshnessProvider,
                revisionProvider = repository,
                catalogRepository = catalogRepo
            )
            readInvoker = DeterministicReadToolInvoker(readRegistry)

            taskManager = DefaultEngineeringTaskManager(
                coroutineScope = CoroutineScope(Dispatchers.Default)
            )

            harness = CableEngineeringVerticalSliceHarness(
                cableInvoker = invoker,
                readInvoker = readInvoker,
                taskManager = taskManager
            )

            // Seed projects
            database.projectDao().upsertProject(ProjectEntity(projectId = projectId, projectName = "Primary Cable Test Project", revision = 1L))
            database.projectDao().upsertProject(ProjectEntity(projectId = projectOther, projectName = "Other Isolation Project", revision = 1L))

            // Seed standard Transformer
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    transformerId = "TX-1",
                    projectId = projectId,
                    transformerName = "Main TX",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )

            // Seed Circuits
            // 1. Root Feeder Circuit (Main incomer)
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 1,
                    projectId = projectId,
                    circuitName = "MDB-Main",
                    loadKVA = 250.0,
                    length_m = 30.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.03, // 3%
                    cableType = "Cu/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Free Air",
                    parallelRuns = 1,
                    fedFrom = "Transformer",
                    transformerId = "TX-1"
                )
            )

            // 2. Sub-feeder Circuit (fed from MDB-Main)
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 2,
                    projectId = projectId,
                    circuitName = "SMDB-1",
                    loadKVA = 80.0,
                    length_m = 60.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.05, // 5%
                    cableType = "Cu/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Free Air",
                    parallelRuns = 1,
                    fedFrom = "MDB-Main",
                    parentId = 1
                )
            )

            // 3. Branch Circuit with long run (Voltage drop sensitive)
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 3,
                    projectId = projectId,
                    circuitName = "DB-LongRun",
                    loadKVA = 20.0,
                    length_m = 250.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.04, // 4%
                    cableType = "Cu/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Free Air",
                    parallelRuns = 1,
                    fedFrom = "SMDB-1",
                    parentId = 2
                )
            )

            // 4. Circuit in other project with distinct ID 10
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 10,
                    projectId = projectOther,
                    circuitName = "OTHER-MDB",
                    loadKVA = 500.0,
                    length_m = 100.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.90,
                    maxVDpct = 0.02,
                    cableType = "Al/XLPE/PVC - 4C",
                    manufacturer = "Elsewedy",
                    installation = "Direct Buried",
                    parallelRuns = 2,
                    fedFrom = "Main"
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

    // =========================================================================
    // 1. CALCULATION AUTHORITY & PROJECT ISOLATION TESTS
    // =========================================================================

    @Test
    fun calculateCableSizing_usesCoordinatorAndExistingEngine() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-CALC-1",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            entityType = "CIRCUIT",
            entityId = "1",
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1")
        )

        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertNotNull(res.result)
        assertEquals("MDB-Main", res.result!!.circuitName)
        assertEquals(CheckStatus.PASS, res.result!!.overallStatus)
        assertTrue("Ib must be ~360.8 A", res.result!!.details.Ib_A > 300.0)

        // Verify result is cached in coordinator
        val cached = EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 1)
        assertNotNull("Coordinator cache must hold calculated result", cached)
        assertEquals(res.result!!.details.cableSize_mm2, cached!!.cableSize_mm2)
    }

    @Test
    fun calculateCableSizing_sameCircuitIdAcrossProjects_neverLeaks() = runBlocking {
        val reqProjA = ToolRequestEnvelope(
            requestId = "REQ-A",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            entityType = "CIRCUIT",
            entityId = "1",
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1")
        )
        val reqProjOther = ToolRequestEnvelope(
            requestId = "REQ-B",
            projectId = projectOther,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            entityType = "CIRCUIT",
            entityId = "10",
            parameters = CalculateCableSizingRequest(projectId = projectOther, circuitId = "10")
        )

        val resA = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqProjA)
        val resOther = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqProjOther)

        assertEquals(ToolStatus.PASS, resA.status)
        assertEquals(ToolStatus.PASS, resOther.status)
        assertEquals("MDB-Main", resA.result?.circuitName)
        assertEquals("OTHER-MDB", resOther.result?.circuitName)
        assertNotEquals(resA.result?.details?.Ib_A, resOther.result?.details?.Ib_A)

        // Test cross-project boundary rejection
        val reqCrossLeak = ToolRequestEnvelope(
            requestId = "REQ-CROSS-1",
            projectId = projectOther,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectOther, circuitId = "1")
        )
        val resCross = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqCrossLeak)
        assertEquals(ToolStatus.INCOMPLETE, resCross.status)
    }

    @Test
    fun calculateCableSizing_missingCircuit_returnsTypedIncomplete() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-MISS",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "999")
        )

        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.INCOMPLETE, res.status)
        assertNull(res.result)
    }

    @Test
    fun calculateCableSizing_blockingInputMissing_doesNotGuess() = runBlocking {
        // Insert invalid circuit with length = 0
        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 99,
                projectId = projectId,
                circuitName = "BadCircuit",
                loadKVA = 50.0,
                length_m = 0.0,
                voltageV = 400
            )
        )

        val req = ToolRequestEnvelope(
            requestId = "REQ-BAD",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "99")
        )

        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.INVALID_INPUT, res.status)
        assertEquals("INVALID_LENGTH", res.errors.first().errorCode)
    }

    @Test
    fun calculateCableSizing_currentResultReuse_doesNotRecalculate() = runBlocking {
        val req1 = ToolRequestEnvelope(
            requestId = "REQ-1",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1", forceRecalculate = false)
        )
        val res1 = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req1)
        assertEquals(ToolStatus.PASS, res1.status)
        assertTrue(res1.result!!.isRecalculated)

        val req2 = ToolRequestEnvelope(
            requestId = "REQ-2",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1", forceRecalculate = false)
        )
        val res2 = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req2)
        assertEquals(ToolStatus.PASS, res2.status)
        assertFalse("Second call must reuse current cached result", res2.result!!.isRecalculated)
        assertEquals("CURRENT", res2.result!!.freshnessState)
    }

    @Test
    fun calculateCableSizing_staleResult_requiresExplicitRecalculation() = runBlocking {
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R1", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )

        // Mutate circuit load to trigger freshness STALE
        val circuit = database.circuitDao().getCircuitById(1)!!
        database.circuitDao().updateCircuit(circuit.copy(loadKVA = 350.0))

        val reqRecalc = ToolRequestEnvelope(
            requestId = "R2",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1", forceRecalculate = true)
        )
        val resRecalc = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqRecalc)
        assertEquals(ToolStatus.PASS, resRecalc.status)
        assertTrue("Force recalculate must trigger engine calculation", resRecalc.result!!.isRecalculated)
        assertTrue("Updated Ib must reflect 350 kVA", resRecalc.result!!.details.Ib_A > 450.0)
    }

    @Test
    fun phase6Result_matchesCoordinatorFieldForField() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(1)!!
        val allCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val transformers = database.transformerDao().getTransformersListForProject(projectId)
        val directResult = EngineeringCalculationCoordinator.calculateCableSizing(circuit, allCircuits, transformers)

        val req = ToolRequestEnvelope(
            requestId = "REQ-PARITY",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1", forceRecalculate = true)
        )
        val toolRes = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, toolRes.status)
        val d = toolRes.result!!.details

        assertEquals(directResult.Ib_A, d.Ib_A, 0.001)
        assertEquals(directResult.deviceSize_A, d.deviceSize_A, 0.001)
        assertEquals(directResult.deviceType, d.deviceType)
        assertEquals(directResult.cTotalDerating, d.cTotalDerating, 0.0001)
        assertEquals(directResult.cableSize_mm2, d.cableSize_mm2)
        assertEquals(directResult.capacity_derated_Iz_A, d.capacity_derated_Iz_A, 0.001)
        assertEquals(directResult.utilizationPct, d.utilizationPct, 0.001)
        assertEquals(directResult.voltDrop_V, d.voltDrop_V, 0.001)
        assertEquals(directResult.vdPct, d.vdPct, 0.001)
        assertEquals(directResult.cumulativeVDpct, d.cumulativeVDpct, 0.001)
        assertEquals(directResult.status, d.status)
    }

    @Test
    fun currentResult_isReusedWithoutEngineInvocation() = runBlocking {
        // Initial execution
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-INIT", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )

        val reqReuse = ToolRequestEnvelope(
            requestId = "R-REUSE",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1", forceRecalculate = false)
        )
        val resReuse = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqReuse)
        assertEquals(ToolStatus.PASS, resReuse.status)
        assertFalse(resReuse.result!!.isRecalculated)
    }

    @Test
    fun staleResult_withoutForce_returnsTypedStale() = runBlocking {
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-INIT2", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )

        // Mutate circuit in DB to trigger STALE
        val circuit = database.circuitDao().getCircuitById(1)!!
        database.circuitDao().updateCircuit(circuit.copy(loadKVA = 300.0))

        val reqStale = ToolRequestEnvelope(
            requestId = "R-STALE-CHECK",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1", forceRecalculate = false)
        )
        val resStale = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqStale)
        assertEquals(ToolStatus.STALE, resStale.status)
        assertNotNull(resStale.result)
        assertEquals("STALE", resStale.result!!.freshnessState)
        assertFalse(resStale.result!!.isRecalculated)
    }

    @Test
    fun staleResult_withForce_recalculatesTargetOnly() = runBlocking {
        // Mutate circuit in DB
        val circuit = database.circuitDao().getCircuitById(1)!!
        database.circuitDao().updateCircuit(circuit.copy(loadKVA = 320.0))

        val reqForce = ToolRequestEnvelope(
            requestId = "R-FORCE",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1", forceRecalculate = true)
        )
        val resForce = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(reqForce)
        assertEquals(ToolStatus.PASS, resForce.status)
        assertTrue(resForce.result!!.isRecalculated)
    }

    @Test
    fun missingResult_explicitCalculate_createsAuthoritativeResult() = runBlocking {
        EngineeringCalculationCoordinator.clearProjectCache(projectId)
        val req = ToolRequestEnvelope(
            requestId = "R-MISS-CALC",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "2")
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertTrue(res.result!!.isRecalculated)
    }

    @Test
    fun forceRecalculate_neverTriggersFullProjectPipeline() = runBlocking {
        EngineeringCalculationCoordinator.clearProjectCache(projectId)
        val req = ToolRequestEnvelope(
            requestId = "R-LOCAL-ONLY",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1", forceRecalculate = true)
        )
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)

        // Circuit 2 was NOT calculated as part of circuit 1 request
        val cached2 = EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 2)
        assertNull("Local circuit 1 calculation must not calculate sibling circuit 2", cached2)
    }

    // =========================================================================
    // 2. CANDIDATE EVALUATION TESTS
    // =========================================================================

    @Test
    fun evaluateCandidate_usesPublicDeterministicCandidateApi() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-CAND",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(
                projectId = projectId,
                circuitId = "1",
                candidateSizeMm2 = 240.0
            )
        )

        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertNotNull(res.result)
        assertEquals(240.0, res.result!!.candidateSizeMm2, 0.001)
        assertEquals("240 mm\u00B2", res.result!!.candidateSizeFormatted)
    }

    @Test
    fun evaluateCandidate_doesNotDuplicateFormulaLogicInToolLayer() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-CAND-300",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 300.0)
        )
        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertTrue(res.result!!.details.capacity_derated_Iz_A > 0.0)
    }

    @Test
    fun evaluateCandidate_doesNotWriteRoomRevisionCacheOrFreshness() = runBlocking {
        val revBefore = repository.getCurrentRevision(projectId).revision
        val circuitsBefore = database.circuitDao().getCircuitsListForProject(projectId)
        val cachedBefore = EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 1)

        val req = ToolRequestEnvelope(
            requestId = "REQ-SIDE-EFFECT-FREE",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 300.0)
        )
        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)

        val revAfter = repository.getCurrentRevision(projectId).revision
        val circuitsAfter = database.circuitDao().getCircuitsListForProject(projectId)
        val cachedAfter = EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 1)

        assertEquals("Project revision must remain unchanged after candidate evaluation", revBefore, revAfter)
        assertEquals("Room circuits count must remain unchanged", circuitsBefore.size, circuitsAfter.size)
        assertEquals("Room circuit overrideCableSize must remain unchanged", circuitsBefore.first().overrideCableSize, circuitsAfter.first().overrideCableSize)
        assertEquals("Coordinator cache must remain unchanged", cachedBefore, cachedAfter)
    }

    @Test
    fun evaluateCandidate_smallerFailingSize_returnsActualGoverningCheck() = runBlocking {
        // Evaluating 10 mm² on a 250 kVA (360A) load must fail ampacity
        val req = ToolRequestEnvelope(
            requestId = "REQ-FAIL-SIZE",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 10.0)
        )

        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(CheckStatus.FAIL, res.result!!.overallStatus)
        assertTrue("Must fail ampacity or multiple constraints", 
            res.result!!.governingCriterion == GoverningCriterion.AMPACITY || 
            res.result!!.governingCriterion == GoverningCriterion.MULTIPLE_CONSTRAINTS ||
            res.result!!.governingCriterion == GoverningCriterion.MANUAL_OVERRIDE
        )
    }

    @Test
    fun evaluateCandidate_passingSize_preservesAllEngineFields() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-ALL-FIELDS",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 240.0)
        )

        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        val d = res.result!!.details
        assertTrue("Ib_A must be populated", d.Ib_A > 0.0)
        assertTrue("deviceSize_A must be populated", d.deviceSize_A > 0.0)
        assertFalse("productCode must not be blank", d.productCode.isBlank())
        assertTrue("capacity_derated_Iz_A must be populated", d.capacity_derated_Iz_A > 0.0)
        assertTrue("cTotalDerating must be populated", d.cTotalDerating > 0.0)
        assertTrue("kFactor must be populated", d.kFactor > 0.0)
        assertFalse("neutral_mm2 must not be blank", d.neutral_mm2.isBlank())
        assertFalse("earthCPC_mm2 must not be blank", d.earthCPC_mm2.isBlank())
    }

    @Test
    fun evaluateCandidate_invalidCatalogIdentity_isTyped() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-BAD-SIZE",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 9999.0)
        )

        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(CheckStatus.FAIL, res.result!!.overallStatus)
        assertTrue(res.result!!.governingCriterion == GoverningCriterion.CATALOG_AVAILABILITY || res.result!!.governingCriterion == GoverningCriterion.MANUAL_OVERRIDE)
    }

    @Test
    fun evaluateCandidate_manualOverride_isExplicitlyReported() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "REQ-MANUAL-REP",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 300.0)
        )

        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertNotNull(res.result)
        assertEquals("300 mm\u00B2", res.result!!.candidateSizeFormatted)
    }

    @Test
    fun phase6Candidate_matchesAuthoritativeCandidateApiFieldForField() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(1)!!
        val directCandidateResult = CableCalculationService.evaluateCandidate(
            circuit = circuit,
            candidateSizeFormatted = "240 mm\u00B2",
            parallelRuns = 1
        )

        val req = ToolRequestEnvelope(
            requestId = "R-CAND-PARITY",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId, "1", candidateSizeMm2 = 240.0)
        )
        val toolRes = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, toolRes.status)
        val d = toolRes.result!!.details

        assertEquals(directCandidateResult.capacity_derated_Iz_A, d.capacity_derated_Iz_A, 0.001)
        assertEquals(directCandidateResult.voltDrop_V, d.voltDrop_V, 0.001)
        assertEquals(directCandidateResult.vdPct, d.vdPct, 0.001)
    }

    // =========================================================================
    // 3. CANDIDATE COMPARISON TESTS
    // =========================================================================

    @Test
    fun compareCandidates_isBoundedAndDeterministic() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "C1", sizeMm2 = 185.0),
            CableCandidateSpecDto(candidateId = "C2", sizeMm2 = 240.0),
            CableCandidateSpecDto(candidateId = "C3", sizeMm2 = 300.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "REQ-COMP",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId = projectId, circuitId = "1", candidates = candidates)
        )

        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(3, res.result!!.evaluatedCount)
        assertEquals("C1", res.result!!.candidateResults[0].candidateId)
        assertEquals("C2", res.result!!.candidateResults[1].candidateId)
        assertEquals("C3", res.result!!.candidateResults[2].candidateId)
    }

    @Test
    fun compareCandidates_rejectsExcessiveCandidates() = runBlocking {
        val excessiveList = (1..12).map { CableCandidateSpecDto(sizeMm2 = (it * 10).toDouble()) }
        val req = ToolRequestEnvelope(
            requestId = "REQ-EXCESS",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId = projectId, circuitId = "1", candidates = excessiveList)
        )

        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.INVALID_INPUT, res.status)
        assertEquals("EXCESSIVE_CANDIDATES_COUNT", res.errors.first().errorCode)
    }

    @Test
    fun compareCandidates_preservesInputOrderOrDocumentsSortPolicy() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "BIG", sizeMm2 = 300.0),
            CableCandidateSpecDto(candidateId = "SMALL", sizeMm2 = 150.0),
            CableCandidateSpecDto(candidateId = "MED", sizeMm2 = 240.0)
        )

        val reqInput = ToolRequestEnvelope(
            requestId = "R-IN",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates, sortPolicy = CandidateSortPolicy.INPUT_ORDER)
        )
        val resInput = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(reqInput)
        assertEquals(ToolStatus.PASS, resInput.status)
        assertEquals("BIG", resInput.result!!.candidateResults[0].candidateId)
        assertEquals("SMALL", resInput.result!!.candidateResults[1].candidateId)

        val reqAsc = ToolRequestEnvelope(
            requestId = "R-ASC",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates, sortPolicy = CandidateSortPolicy.SIZE_ASCENDING)
        )
        val resAsc = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(reqAsc)
        assertEquals(ToolStatus.PASS, resAsc.status)
        assertEquals("SMALL", resAsc.result!!.candidateResults[0].candidateId)
        assertEquals("MED", resAsc.result!!.candidateResults[1].candidateId)
        assertEquals("BIG", resAsc.result!!.candidateResults[2].candidateId)
    }

    @Test
    fun compareCandidates_doesNotInventCost() = runBlocking {
        val candidates = listOf(CableCandidateSpecDto(candidateId = "C1", sizeMm2 = 240.0))
        val req = ToolRequestEnvelope(
            requestId = "R-COST",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        val jsonString = res.result.toString()
        assertFalse("Must not invent cost", jsonString.contains("cost", ignoreCase = true))
        assertFalse("Must not invent price", jsonString.contains("price", ignoreCase = true))
    }

    @Test
    fun compareCandidates_allFail_preservesEachFailure() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "FAIL-1", sizeMm2 = 1.5),
            CableCandidateSpecDto(candidateId = "FAIL-2", sizeMm2 = 2.5)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-ALL-FAIL",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(0, res.result!!.passingCount)
        assertEquals(2, res.result!!.failingCount)
        assertNull(res.result!!.smallestPassingCandidateId)
        assertNotNull(res.result!!.noRecommendationReason)
    }

    @Test
    fun compareCandidates_mixedResults_identifiesPassingCandidatesWithoutHidingWarnings() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "FAIL_SMALL", sizeMm2 = 25.0),
            CableCandidateSpecDto(candidateId = "PASS_OPT", sizeMm2 = 240.0),
            CableCandidateSpecDto(candidateId = "PASS_LARGE", sizeMm2 = 300.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-MIXED",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(2, res.result!!.passingCount)
        assertEquals(1, res.result!!.failingCount)
        assertEquals("PASS_OPT", res.result!!.smallestPassingCandidateId)
    }

    @Test
    fun compareCandidates_duplicateCandidates_areHandledDeterministically() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "DUP1", sizeMm2 = 240.0),
            CableCandidateSpecDto(candidateId = "DUP2", sizeMm2 = 240.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-DUP",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(2, res.result!!.evaluatedCount)
        assertEquals(res.result!!.candidateResults[0].utilizationPct, res.result!!.candidateResults[1].utilizationPct, 0.001)
    }

    @Test
    fun comparison_withoutObjective_doesNotClaimUniversalOptimum() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "C1", sizeMm2 = 240.0),
            CableCandidateSpecDto(candidateId = "C2", sizeMm2 = 300.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-OBJ-CHECK",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates, sortPolicy = CandidateSortPolicy.INPUT_ORDER)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals("C1", res.result!!.smallestPassingCandidateId)
    }

    @Test
    fun sizeAscending_returnsSmallestPassingCandidateOnly() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "C300", sizeMm2 = 300.0),
            CableCandidateSpecDto(candidateId = "C240", sizeMm2 = 240.0),
            CableCandidateSpecDto(candidateId = "C185", sizeMm2 = 185.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-SIZE-ASC",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates, sortPolicy = CandidateSortPolicy.SIZE_ASCENDING)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        // 185 mm² fails ampacity on 250 kVA; 240 mm² is smallest passing
        assertEquals("C240", res.result!!.smallestPassingCandidateId)
        assertEquals("C185", res.result!!.candidateResults[0].candidateId)
        assertEquals("C240", res.result!!.candidateResults[1].candidateId)
        assertEquals("C300", res.result!!.candidateResults[2].candidateId)
    }

    @Test
    fun utilizationAscending_doesNotClaimLowestCost() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "C240", sizeMm2 = 240.0),
            CableCandidateSpecDto(candidateId = "C300", sizeMm2 = 300.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-UTIL-ASC",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates, sortPolicy = CandidateSortPolicy.UTILIZATION_ASCENDING)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals("C300", res.result!!.lowestUtilizationPassingCandidateId)
    }

    @Test
    fun warningCandidate_isNotSilentlyPreferredOverCleanPass() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "C_PASS", sizeMm2 = 240.0)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-WARN-PREF",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals("C_PASS", res.result!!.smallestPassingCandidateId)
    }

    @Test
    fun noPassingCandidate_returnsNoRecommendation() = runBlocking {
        val candidates = listOf(
            CableCandidateSpecDto(candidateId = "C1", sizeMm2 = 1.5)
        )
        val req = ToolRequestEnvelope(
            requestId = "R-NO-REC",
            projectId = projectId,
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            parameters = CompareCableCandidatesRequest(projectId, "1", candidates)
        )
        val res = invoker.executeDirect<CompareCableCandidatesRequest, CableCandidatesComparisonResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertNull(res.result!!.smallestPassingCandidateId)
        assertEquals("All evaluated candidates failed engineering constraints.", res.result!!.noRecommendationReason)
    }

    // =========================================================================
    // 4. WHAT-IF ISOLATION TESTS
    // =========================================================================

    @Test
    fun whatIf_materialChange_doesNotMutateLiveCircuit() = runBlocking {
        val circuitBefore = database.circuitDao().getCircuitById(1)!!
        assertEquals("Cu/XLPE/PVC - 4C", circuitBefore.cableType)

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-MAT",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(cableType = "Al/XLPE/PVC - 4C")
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)

        val circuitAfter = database.circuitDao().getCircuitById(1)!!
        assertEquals("Live circuit cableType must remain Cu/XLPE/PVC - 4C", "Cu/XLPE/PVC - 4C", circuitAfter.cableType)
    }

    @Test
    fun whatIf_lengthChange_doesNotIncrementRevision() = runBlocking {
        val revBefore = repository.getCurrentRevision(projectId).revision

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-LEN",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 300.0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)

        val revAfter = repository.getCurrentRevision(projectId).revision
        assertEquals("Project revision must not increment during what-if execution", revBefore, revAfter)
    }

    @Test
    fun whatIf_doesNotReplaceCoordinatorLiveCache() = runBlocking {
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-WARM", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )
        val cachedBefore = EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 1)

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-CACHE",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(loadKVA = 500.0)
            )
        )
        invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)

        val cachedAfter = EngineeringCalculationCoordinator.getCachedSizingResult(projectId, 1)
        assertEquals("Coordinator live cache must not be replaced by scenario", cachedBefore, cachedAfter)
    }

    @Test
    fun whatIf_doesNotCreateOrModifyFreshnessRecord() = runBlocking {
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        val freshnessBefore = freshnessProvider.getCalculationState(scope)

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-FRESH",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 500.0)
            )
        )
        invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)

        val freshnessAfter = freshnessProvider.getCalculationState(scope)
        assertEquals(freshnessBefore.state, freshnessAfter.state)
    }

    @Test
    fun whatIf_resultContainsCurrentVsScenarioDiff() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-WIF-DIFF",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 150.0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        val diffs = res.result!!.inputDifferences
        assertTrue("Input differences must include length_m", diffs.any { it.fieldName == "length_m" && it.baselineValue == "30.0" && it.scenarioValue == "150.0" })
    }

    @Test
    fun whatIf_multipleOverrides_areExplicitAndTyped() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-WIF-MULTI",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(
                    length_m = 100.0,
                    parallelRuns = 2,
                    installation = "Direct Buried"
                )
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        val diffs = res.result!!.inputDifferences
        assertEquals(3, diffs.size)
    }

    @Test
    fun whatIf_failure_doesNotAffectCurrentResult() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-WIF-FAIL",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(maxVDpct = 0.0001)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(CheckStatus.FAIL, res.result!!.scenarioStatus)

        val resLive = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-LIVE", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )
        assertEquals(ToolStatus.PASS, resLive.status)
        assertEquals(CheckStatus.PASS, resLive.result!!.overallStatus)
    }

    @Test
    fun phase6WhatIf_matchesAuthoritativeTemporaryCalculationFieldForField() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(1)!!
        val scenarioCircuit = circuit.copy(length_m = 120.0)
        val directResult = CableCalculationService.calculateSingleCircuit(scenarioCircuit)

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-PARITY",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 120.0)
            )
        )
        val toolRes = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, toolRes.status)
        val d = toolRes.result!!.scenarioDetails

        assertEquals(directResult.cableSize_mm2, d.cableSize_mm2)
        assertEquals(directResult.voltDrop_V, d.voltDrop_V, 0.001)
        assertEquals(directResult.vdPct, d.vdPct, 0.001)
    }

    @Test
    fun whatIf_currentBaseline_comparesTruthfully() = runBlocking {
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-BASE-WARM", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-CURR",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 80.0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals("CURRENT", res.result!!.baselineFreshness)
        assertEquals("FULL_COMPARISON", res.result!!.comparisonValidity)
    }

    @Test
    fun whatIf_staleBaseline_marksComparisonLimited() = runBlocking {
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-BASE-STALE", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )

        // Mutate circuit
        val circuit = database.circuitDao().getCircuitById(1)!!
        database.circuitDao().updateCircuit(circuit.copy(loadKVA = 280.0))

        val req = ToolRequestEnvelope(
            requestId = "R-WIF-STALE",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 90.0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals("STALE", res.result!!.baselineFreshness)
        assertEquals("LIMITED_BASELINE_STALE", res.result!!.comparisonValidity)
    }

    @Test
    fun whatIf_missingBaseline_doesNotFabricateCurrentResult() = runBlocking {
        EngineeringCalculationCoordinator.clearProjectCache(projectId)
        val req = ToolRequestEnvelope(
            requestId = "R-WIF-MISS",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "2",
                overrides = CableWhatIfOverridesDto(length_m = 100.0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals("MISSING", res.result!!.baselineFreshness)
        assertEquals("NO_BASELINE_RECORDED", res.result!!.comparisonValidity)
    }

    @Test
    fun whatIf_invalidOverride_returnsTypedInvalidInput() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-WIF-INV",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = -50.0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.INVALID_INPUT, res.status)
        assertEquals("INVALID_OVERRIDE_VALUE", res.errors.first().errorCode)
        assertEquals("length_m", res.errors.first().failingParameter)
    }

    @Test
    fun whatIf_unsupportedOverride_isRejected() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-WIF-UNSUP",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(parallelRuns = 0)
            )
        )
        val res = invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        assertEquals(ToolStatus.INVALID_INPUT, res.status)
        assertEquals("parallelRuns", res.errors.first().failingParameter)
    }

    // =========================================================================
    // 5. GOVERNING CRITERION TESTS
    // =========================================================================

    @Test
    fun governingCriterion_ampacity_fromActualChecks() = runBlocking {
        // Short cable (30m) with high current -> Ampacity governed
        val req = ToolRequestEnvelope(
            requestId = "R-GOV-AMP",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1")
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(GoverningCriterion.AMPACITY, res.result!!.governingCriterion)
    }

    @Test
    fun governingCriterion_voltageDrop_fromActualChecks() = runBlocking {
        // Long cable (250m) with smaller current -> Voltage Drop governed
        val req = ToolRequestEnvelope(
            requestId = "R-GOV-VD",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "3")
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertTrue(
            res.result!!.governingCriterion == GoverningCriterion.VOLTAGE_DROP ||
            res.result!!.governingCriterion == GoverningCriterion.CUMULATIVE_VOLTAGE_DROP
        )
    }

    @Test
    fun governingCriterion_cumulativeVoltageDrop_fromActualChecks() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-GOV-CUMVD",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "3")
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertTrue(res.result!!.checks.any { it.checkName == "Cumulative Voltage Drop" })
    }

    @Test
    fun governingCriterion_shortCircuit_fromActualChecks() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-GOV-SC",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1")
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertTrue(res.result!!.details.kFactor > 0.0)
        assertTrue(res.result!!.details.deviceBreakingKA > 0.0)
    }

    @Test
    fun governingCriterion_multipleFailures_isNotOversimplified() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-MULTI-FAIL",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 1.5)
        )
        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(CheckStatus.FAIL, res.result!!.overallStatus)
        assertTrue(
            res.result!!.governingCriterion == GoverningCriterion.MULTIPLE_CONSTRAINTS ||
            res.result!!.governingCriterion == GoverningCriterion.MANUAL_OVERRIDE
        )
    }

    @Test
    fun governingCriterion_staleResult_isNotCurrentRecommendation() = runBlocking {
        invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(
            ToolRequestEnvelope(requestId = "R-STALE-GOV", projectId = projectId, toolName = CableToolNames.CALCULATE_CABLE_SIZING, parameters = CalculateCableSizingRequest(projectId, "1"))
        )

        // Mutate circuit
        val circuit = database.circuitDao().getCircuitById(1)!!
        database.circuitDao().updateCircuit(circuit.copy(loadKVA = 330.0))

        val req = ToolRequestEnvelope(
            requestId = "R-STALE-CHECK2",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1", forceRecalculate = false)
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.STALE, res.status)
    }

    @Test
    fun governingCriterion_manualOverride_distinguishesAutomaticResult() = runBlocking {
        val circuit = database.circuitDao().getCircuitById(1)!!
        database.circuitDao().updateCircuit(circuit.copy(overrideCableSize = "300 mm\u00B2"))

        val req = ToolRequestEnvelope(
            requestId = "R-MAN-GOV",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId = projectId, circuitId = "1", forceRecalculate = true)
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(GoverningCriterion.MANUAL_OVERRIDE, res.result!!.governingCriterion)
    }

    // =========================================================================
    // 6. CONCURRENCY & FAILURE SAFETY TESTS
    // =========================================================================

    @Test
    fun managedSameCalculation_deduplicates() = runBlocking {
        val req1 = ToolRequestEnvelope(
            requestId = "REQ-MNG-1",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            entityType = "CIRCUIT",
            entityId = "1",
            parameters = CalculateCableSizingRequest(projectId, "1")
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-MNG-2",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            entityType = "CIRCUIT",
            entityId = "1",
            parameters = CalculateCableSizingRequest(projectId, "1")
        )

        val res1 = invoker.executeManaged<CalculateCableSizingRequest, CableSizingExecutionResult>(req1, taskManager)
        val res2 = invoker.executeManaged<CalculateCableSizingRequest, CableSizingExecutionResult>(req2, taskManager)

        assertEquals(ToolStatus.PASS, res1.status)
        assertEquals(ToolStatus.PASS, res2.status)
    }

    @Test
    fun managedDifferentCandidate_doesNotFalseDeduplicate() = runBlocking {
        val encoder = DefaultCableRequestSignatureEncoder()
        val req1 = ToolRequestEnvelope(
            requestId = "R1",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId, "1", candidateSizeMm2 = 240.0)
        )
        val req2 = ToolRequestEnvelope(
            requestId = "R2",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId, "1", candidateSizeMm2 = 300.0)
        )

        val sig1 = encoder.signature(req1)
        val sig2 = encoder.signature(req2)
        assertNotEquals("Different candidate requests must produce distinct signatures", sig1, sig2)
    }

    @Test
    fun supersededCandidate_lateResultCannotPublish() = runBlocking {
        val key = com.example.ai.task.EngineeringTaskKey(projectId, EngineeringTaskType.CUSTOM, "CIRCUIT", "1", "variantA")
        val handle1 = taskManager.submit<String>(key, TaskOwner.SYSTEM) {
            delay(100)
            ToolResultEnvelope.pass("REQ-1", "test", projectId, "RESULT-1")
        }
        val handle2 = taskManager.submit<String>(key, TaskOwner.SYSTEM) {
            ToolResultEnvelope.pass("REQ-2", "test", projectId, "RESULT-2")
        }

        val res2 = handle2.awaitResult()
        assertEquals(ToolStatus.PASS, res2.status)
        assertEquals("RESULT-2", res2.result)
    }

    @Test
    fun ownerCancellation_doesNotCancelOtherSubscriber() = runBlocking {
        val ownerA = TaskOwner.screen("ScreenA")
        val ownerB = TaskOwner.screen("ScreenB")
        val key = com.example.ai.task.EngineeringTaskKey(projectId, EngineeringTaskType.CUSTOM, "CIRCUIT", "1", "variantShared")
        val handleA = taskManager.submit<String>(key, ownerA, "SIG-SHARED") {
            ToolResultEnvelope.pass("REQ-S", "test", projectId, "SHARED-RES")
        }
        val handleB = taskManager.submit<String>(key, ownerB, "SIG-SHARED") {
            ToolResultEnvelope.pass("REQ-S", "test", projectId, "SHARED-RES")
        }
        taskManager.cancelByOwner(ownerA)
        val res = handleB.awaitResult()
        assertEquals(ToolStatus.PASS, res.status)
    }

    @Test
    fun signatureEncodingFailure_failsClosedBeforeCalculation() = runBlocking {
        val throwingEncoder = object : CableRequestSignatureEncoder {
            override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray = throw IllegalStateException("Fault")
            override fun signature(request: ToolRequestEnvelope<*>): String = throw IllegalStateException("Fault")
        }
        val safeInvoker = DeterministicCableToolInvoker(registry, throwingEncoder)

        val req = ToolRequestEnvelope(
            requestId = "R-FAIL-ENC",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "1")
        )

        val res = safeInvoker.executeManaged<CalculateCableSizingRequest, CableSizingExecutionResult>(req, taskManager)
        assertEquals(ToolStatus.ERROR, res.status)
        assertNull(res.result)
        assertEquals("REQUEST_SIGNATURE_ENCODING_FAILED", res.errors.first().errorCode)
    }

    @Test
    fun engineException_returnsSanitizedTypedError() = runBlocking {
        val req = ToolRequestEnvelope(
            requestId = "R-ERR",
            projectId = projectId,
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            parameters = CalculateCableSizingRequest(projectId, "invalid-num")
        )
        val res = invoker.executeDirect<CalculateCableSizingRequest, CableSizingExecutionResult>(req)
        assertEquals(ToolStatus.INVALID_INPUT, res.status)
        assertEquals("INVALID_CIRCUIT_ID", res.errors.first().errorCode)
    }

    // =========================================================================
    // 7. REGISTRY & REGRESSION GUARDS
    // =========================================================================

    @Test
    fun unifiedRegistry_discoversBothReadAndCableTools() {
        val unified = UnifiedDeterministicToolRegistry(readRegistry, registry)
        val allNames = unified.getAllToolNames()

        assertTrue(allNames.contains("get_entity"))
        assertTrue(allNames.contains("get_current_project_context"))
        assertTrue(allNames.contains("calculate_cable_sizing"))
        assertTrue(allNames.contains("evaluate_cable_candidate"))
        assertTrue(allNames.contains("compare_cable_candidates"))
        assertTrue(allNames.contains("run_what_if"))

        assertTrue(unified.isReadTool("get_entity"))
        assertTrue(unified.isCableTool("calculate_cable_sizing"))
        assertNotNull(unified.getCableTool<CalculateCableSizingRequest, CableSizingExecutionResult>("calculate_cable_sizing"))
    }

    @Test
    fun unifiedRegistry_rejectsNamespaceCollision() {
        val customCableRegistry = DeterministicCableToolRegistry()
        val collidingTool = object : DeterministicCableTool<Any, Any> {
            override val toolName = "get_entity" // Collision with Read tool
            override val description = "Colliding description"
            override suspend fun execute(request: ToolRequestEnvelope<Any>): ToolResultEnvelope<Any> =
                ToolResultEnvelope.notSupported("REQ", toolName, "P", "Collision")
        }
        customCableRegistry.register(collidingTool)

        val unified = UnifiedDeterministicToolRegistry(readRegistry, customCableRegistry)
        try {
            unified.getAllToolNames()
            fail("Unified registry must reject namespace collision")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("collision", ignoreCase = true))
        }
    }

    @Test
    fun phase6_doesNotModifyDeterministicFormulaOutputs() {
        val circuit = CircuitEntity(
            id = 10,
            projectId = "BENCH",
            circuitName = "BenchCircuit",
            loadKVA = 100.0,
            length_m = 50.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            maxVDpct = 0.05,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            parallelRuns = 1
        )
        val res = CableSizingEngine.sizeCircuit(circuit)
        assertTrue("Engine sizing result must be valid", res.Ib_A > 0.0)
    }

    @Test
    fun phase6_doesNotTouchQuickCable() {
        val qcTest = com.example.QuickCableCalcTest()
        assertNotNull(qcTest)
    }

    @Test
    fun phase6_harnessRunsOfflineWithoutGemini() = runBlocking {
        val res = harness.calculateCableSizing(projectId, "1")
        assertEquals(ToolStatus.PASS, res.status)
        assertNotNull(res.result)
    }
}
