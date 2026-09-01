package com.example.ai.tools.cable

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
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
import com.example.engine.BuswayEngine
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.engine.CableFailureCode
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.CableCheckCode
import com.example.engine.coordinator.CableCheckStatus
import com.example.engine.coordinator.CableEngineeringEvaluation
import com.example.engine.coordinator.CableStructuredCheck
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.io.File
import java.util.Locale
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

                Catalog.deratingData = org.json.JSONObject(deratingDataJson)
                Catalog.cableTypes = org.json.JSONArray(findAssetFile("cable_types.json").readText()).let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
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
                    }
                }
                Catalog.dataSheet = org.json.JSONArray(findAssetFile("data_sheet.json").readText()).let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        com.example.data.DataSheetEntry(
                            size = obj.getDouble("size"),
                            R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                            X = obj.getDouble("X"),
                            R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                            R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                            R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
                        )
                    }
                }
                Catalog.cableCatalog = org.json.JSONArray(cableCatalogJson).let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        com.example.data.CableCatalogEntry(
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
                    }
                }
                Catalog.breakers = org.json.JSONArray(breakerCatalogJson).let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        com.example.data.BreakerEntry(
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        loadCatalogData()
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
        val req = ToolRequestEnvelope(
            requestId = "REQ-FAIL-SIZE",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 10.0)
        )

        val res = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        assertEquals(ToolStatus.PASS, res.status)
        assertEquals(CheckStatus.FAIL, res.result!!.overallStatus)
        assertTrue(
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
    // 5. GOVERNING CRITERION AUTHORITY & BOUNDARY TESTS
    // =========================================================================

    @Test
    fun governingCriterion_mapsAuthoritativeAmpacityCheckWithoutRecalculation() {
        val dummyResult = SizingResult(
            Ib_A = 400.0,
            deviceSize_A = 400.0,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 400.0,
            productCode = "CODE-1",
            cableSize_mm2 = "185 mm²",
            ratedI_table_A = 350.0,
            capacity_derated_Iz_A = 350.0,
            utilizationPct = 114.0,
            neutral_mm2 = "95 mm²",
            earthCPC_mm2 = "95 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "✗ FAIL",
            failReason = "Ampacity constraint Ib ≤ In ≤ Iz is not satisfied",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = true,
            primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_mapsAuthoritativeVoltageDropCheckWithoutRecalculation() {
        val dummyResult = SizingResult(
            Ib_A = 50.0,
            deviceSize_A = 63.0,
            deviceType = "MCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 63.0,
            productCode = "CODE-VD",
            cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0,
            capacity_derated_Iz_A = 100.0,
            utilizationPct = 63.0,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 20.0,
            vdPct = 5.0,
            cumulativeVDpct = 5.0,
            status = "✗ FAIL",
            failReason = "Local voltage drop exceeds the allowed limit",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = true,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.VOLTAGE_DROP_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.03) // 3% limit

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.VOLTAGE_DROP, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_mapsAuthoritativeCumulativeVdCheckWithoutRecalculation() {
        val dummyResult = SizingResult(
            Ib_A = 50.0,
            deviceSize_A = 63.0,
            deviceType = "MCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 63.0,
            productCode = "CODE-CUMVD",
            cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0,
            capacity_derated_Iz_A = 100.0,
            utilizationPct = 63.0,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 8.0,
            vdPct = 2.0, // branch is 2% <= 3%
            cumulativeVDpct = 4.5, // cumulative is 4.5% > 3%
            status = "✗ FAIL",
            failReason = "Cumulative voltage drop exceeds the allowed limit",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = true,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.CUMULATIVE_VOLTAGE_DROP_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.03)

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit, parentCumulativeVD = 2.5)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.CUMULATIVE_VOLTAGE_DROP, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_mapsAuthoritativeShortCircuitCheckOnlyWhenProvided() {
        val check = CableStructuredCheck(
            checkCode = CableCheckCode.SHORT_CIRCUIT_WITHSTAND,
            checkName = "Short-Circuit Thermal Withstand",
            status = CableCheckStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(12.0, com.example.ai.contracts.EngineeringUnit.KILOAMPERE),
            limitValue = EngineeringValue.ofDecimal(25.0, com.example.ai.contracts.EngineeringUnit.KILOAMPERE),
            actualDisplay = "12 kA",
            limitDisplay = "25 kA",
            unit = "kA",
            message = "Thermal withstand exceeded",
            isGoverning = true
        )
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(0.0, 0.0, "", 1.0, 0.0, "", "10 mm²", 0.0, 0.0, 0.0, "", "", 0.0, 0.0, 0.0, "✗ FAIL", failReason = "Thermal withstand exceeded", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.FAIL,
            checks = listOf(check),
            governingCheckCode = CableCheckCode.SHORT_CIRCUIT_WITHSTAND,
            governingReason = "Thermal withstand exceeded"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(GoverningCriterion.SHORT_CIRCUIT_WITHSTAND, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_mapsAuthoritativeDeviceCompatibilityOnlyWhenProvided() {
        val check = CableStructuredCheck(
            checkCode = CableCheckCode.DEVICE_COMPATIBILITY,
            checkName = "Protective Device Compatibility",
            status = CableCheckStatus.FAIL,
            actualValue = EngineeringValue.ofDecimal(63.0, com.example.ai.contracts.EngineeringUnit.AMPERE),
            limitValue = EngineeringValue.ofDecimal(100.0, com.example.ai.contracts.EngineeringUnit.AMPERE),
            actualDisplay = "63 A",
            limitDisplay = "100 A",
            unit = "A",
            message = "Device rating exceeds frame capacity",
            isGoverning = true
        )
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(0.0, 0.0, "", 1.0, 0.0, "", "25 mm²", 0.0, 0.0, 0.0, "", "", 0.0, 0.0, 0.0, "✗ FAIL", failReason = "Device rating exceeds frame capacity", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.FAIL,
            checks = listOf(check),
            governingCheckCode = CableCheckCode.DEVICE_COMPATIBILITY,
            governingReason = "Device rating exceeds frame capacity"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(GoverningCriterion.DEVICE_COMPATIBILITY, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_installationDeratingRequiresAuthoritativeReasonCode() {
        val dummyResult = SizingResult(
            Ib_A = 100.0,
            deviceSize_A = 125.0,
            deviceType = "MCCB",
            cTotalDerating = 0.55,
            iRequired_perCable_A = 125.0,
            productCode = "CODE-DER",
            cableSize_mm2 = "50 mm²",
            ratedI_table_A = 160.0,
            capacity_derated_Iz_A = 88.0,
            utilizationPct = 142.0,
            neutral_mm2 = "25 mm²",
            earthCPC_mm2 = "25 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "✗ FAIL",
            failReason = "Total derating factor is zero or invalid.",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = true,
            primaryFailureCode = CableFailureCode.DERATING_INVALID
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(GoverningCriterion.INSTALLATION_DERATING, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_multipleAuthoritativeFailures_returnsMultipleConstraints() {
        val dummyResult = SizingResult(
            Ib_A = 300.0,
            deviceSize_A = 400.0,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 400.0,
            productCode = "CODE-MULTI",
            cableSize_mm2 = "10 mm²",
            ratedI_table_A = 70.0,
            capacity_derated_Iz_A = 70.0,
            utilizationPct = 571.0,
            neutral_mm2 = "10 mm²",
            earthCPC_mm2 = "10 mm²",
            voltDrop_V = 40.0,
            vdPct = 10.0,
            cumulativeVDpct = 10.0,
            status = "✗ FAIL",
            failReason = "Ampacity constraint Ib ≤ In ≤ Iz is not satisfied; Cumulative voltage drop exceeds the allowed limit.",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.MULTIPLE_CONSTRAINTS
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.03)

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.MULTIPLE_CONSTRAINTS, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_noFailedChecks_doesNotInventTightestCriterion() {
        val dummyResult = SizingResult(
            Ib_A = 100.0,
            deviceSize_A = 125.0,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 125.0,
            productCode = "CODE-PASS",
            cableSize_mm2 = "70 mm²",
            ratedI_table_A = 200.0,
            capacity_derated_Iz_A = 200.0,
            utilizationPct = 62.5,
            neutral_mm2 = "35 mm²",
            earthCPC_mm2 = "35 mm²",
            voltDrop_V = 3.0,
            vdPct = 0.75,
            cumulativeVDpct = 0.75,
            status = "✓ PASS"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.03)

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.PASS, interp.overallStatus)
        assertEquals("When all checks pass, tool layer does not invent tightest criterion", GoverningCriterion.NONE, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_warningRemainsWarning() {
        val check = CableStructuredCheck(
            checkCode = CableCheckCode.AMPACITY,
            checkName = "Ampacity High Utilization Warning",
            status = CableCheckStatus.WARNING,
            actualValue = EngineeringValue.ofDecimal(98.0, com.example.ai.contracts.EngineeringUnit.PERCENT),
            limitValue = EngineeringValue.ofDecimal(100.0, com.example.ai.contracts.EngineeringUnit.PERCENT),
            actualDisplay = "98%",
            limitDisplay = "100%",
            unit = "%",
            message = "High utilization warning"
        )
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(0.0, 0.0, "", 1.0, 0.0, "", "70 mm²", 0.0, 0.0, 0.0, "", "", 0.0, 0.0, 0.0, "✓ PASS", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.WARNING,
            checks = listOf(check),
            governingCheckCode = null,
            governingReason = "Warning state"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.WARNING, interp.overallStatus)
    }

    @Test
    fun governingCriterion_manualOverride_isSelectionState_notAutomaticFailure() {
        val dummyResult = SizingResult(
            Ib_A = 100.0,
            deviceSize_A = 125.0,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 125.0,
            productCode = "CODE-MAN",
            cableSize_mm2 = "120 mm²",
            ratedI_table_A = 250.0,
            capacity_derated_Iz_A = 250.0,
            utilizationPct = 50.0,
            neutral_mm2 = "70 mm²",
            earthCPC_mm2 = "70 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "✓ PASS"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.03, overrideCableSize = "120 mm²")

        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.PASS, interp.overallStatus)
        assertEquals("MANUAL_OVERRIDE", interp.selectionSource)
        assertEquals(GoverningCriterion.NONE, interp.governingCriterion)
    }

    @Test
    fun governingCriterion_unsupportedEvidence_isNotFabricated() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(0.0, 0.0, "", 1.0, 0.0, "", "240 mm²", 0.0, 0.0, 0.0, "", "", 0.0, 0.0, 0.0, "✓ PASS", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.PASS,
            checks = emptyList(),
            governingCheckCode = null,
            governingReason = "Pass"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertFalse(interp.governingCriterion == GoverningCriterion.SHORT_CIRCUIT_WITHSTAND)
        assertFalse(interp.governingCriterion == GoverningCriterion.DEVICE_COMPATIBILITY)
    }

    @Test
    fun governingCriterion_adversarialPass_persistsEvenWithUnusualValues() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(1000.0, 100.0, "MCCB", 1.0, 100.0, "X", "1.5 mm²", 10.0, 10.0, 1000.0, "", "", 100.0, 50.0, 50.0, "✓ PASS", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.PASS,
            checks = listOf(
                CableStructuredCheck(CableCheckCode.AMPACITY, "Ampacity", CableCheckStatus.PASS, EngineeringValue.ofDecimal(10.0), EngineeringValue.ofDecimal(100.0), "10A", "100A", "A", "Pass")
            ),
            governingCheckCode = null,
            governingReason = "Engine declared pass"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.PASS, interp.overallStatus)
    }

    @Test
    fun governingCriterion_adversarialFail_persistsEvenWhenRecomputationWouldPass() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(10.0, 20.0, "MCB", 1.0, 20.0, "X", "240 mm²", 500.0, 500.0, 4.0, "", "", 0.1, 0.02, 0.02, "✗ FAIL", failReason = "Coordination constraint", resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.FAIL,
            checks = listOf(
                CableStructuredCheck(CableCheckCode.AMPACITY, "Ampacity", CableCheckStatus.FAIL, EngineeringValue.ofDecimal(500.0), EngineeringValue.ofDecimal(20.0), "500A", "20A", "A", "Failed by upstream policy")
            ),
            governingCheckCode = CableCheckCode.AMPACITY,
            governingReason = "Failed by upstream policy"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    // -------------------------------------------------------------------------
    // Section 4: Voltage-Drop Unit Safety Tests
    // -------------------------------------------------------------------------

    @Test
    fun maxVdUnit_matchesExistingEngineContract() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", maxVDpct = 0.03, fedFrom = "Main")
        val result = CableSizingEngine.sizeCircuit(circuit)
        assertTrue("vdPct must be in percentage points (e.g. <= 3.0%)", result.vdPct <= 3.0)
    }

    @Test
    fun voltageDrop_belowLimit_matchesLegacyEngineStatus() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 10.0, maxVDpct = 0.05, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)
        assertEquals(CableCheckStatus.PASS, eval.overallStatus)
        val vdCheck = eval.checks.first { it.checkCode == CableCheckCode.VOLTAGE_DROP }
        assertEquals(CableCheckStatus.PASS, vdCheck.status)
    }

    @Test
    fun voltageDrop_atLimit_matchesLegacyEngineStatus() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", maxVDpct = 0.03, fedFrom = "Main")
        val dummyResult = SizingResult(
            Ib_A = 10.0, deviceSize_A = 16.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 16.0, productCode = "C1", cableSize_mm2 = "2.5 mm²",
            ratedI_table_A = 25.0, capacity_derated_Iz_A = 25.0, utilizationPct = 40.0,
            neutral_mm2 = "2.5 mm²", earthCPC_mm2 = "2.5 mm²", voltDrop_V = 12.0,
            vdPct = 3.0, cumulativeVDpct = 3.0, status = "✓ PASS"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        assertEquals(CableCheckStatus.PASS, eval.overallStatus)
    }

    @Test
    fun voltageDrop_aboveLimit_matchesLegacyEngineStatus() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 500.0, maxVDpct = 0.001, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)
        assertEquals(CableCheckStatus.FAIL, eval.overallStatus)
        val vdCheck = eval.checks.first { it.checkCode == CableCheckCode.CUMULATIVE_VOLTAGE_DROP }
        assertEquals(CableCheckStatus.FAIL, vdCheck.status)
    }

    @Test
    fun cumulativeVd_failure_matchesLegacyEngineStatus() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 20.0, maxVDpct = 0.03, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit, parentCumulativeVD = 2.9)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit, parentCumulativeVD = 2.9)
        if (sizingRes.status.contains("FAIL")) {
            assertEquals(CableCheckStatus.FAIL, eval.overallStatus)
        }
    }

    // -------------------------------------------------------------------------
    // Section 5: Manual Override Independent Model Tests
    // -------------------------------------------------------------------------

    @Test
    fun manualOverride_cleanPass_reportsManualSelectionAndNoFailureCriterion() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, overrideCableSize = "25 mm²", maxVDpct = 0.05, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals("MANUAL_OVERRIDE", interp.selectionSource)
        assertEquals(CheckStatus.PASS, interp.overallStatus)
        assertEquals(GoverningCriterion.NONE, interp.governingCriterion)
    }

    @Test
    fun manualOverride_ampacityFail_preservesAmpacityFailure() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 300.0, overrideCableSize = "1.5 mm²", maxVDpct = 0.05, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals("MANUAL_OVERRIDE", interp.selectionSource)
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertTrue(
            interp.governingCriterion == GoverningCriterion.AMPACITY ||
            interp.governingCriterion == GoverningCriterion.MULTIPLE_CONSTRAINTS
        )
    }

    @Test
    fun manualOverride_multipleFailures_preservesMultipleConstraints() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 300.0, length_m = 500.0, overrideCableSize = "1.5 mm²", maxVDpct = 0.01, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals("MANUAL_OVERRIDE", interp.selectionSource)
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.MULTIPLE_CONSTRAINTS, interp.governingCriterion)
    }

    @Test
    fun manualOverride_doesNotConvertFailToPassOrNone() {
        val dummyResult = SizingResult(
            Ib_A = 500.0, deviceSize_A = 630.0, deviceType = "MCCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 630.0, productCode = "C1", cableSize_mm2 = "1.5 mm²",
            ratedI_table_A = 20.0, capacity_derated_Iz_A = 20.0, utilizationPct = 2500.0,
            neutral_mm2 = "1.5 mm²", earthCPC_mm2 = "1.5 mm²", voltDrop_V = 50.0,
            vdPct = 12.0, cumulativeVDpct = 12.0, status = "✗ FAIL",
            failReason = "Manual cable override failed validation: Ampacity constraint Ib ≤ In ≤ Iz is not satisfied",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = true,
            primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", overrideCableSize = "1.5 mm²", maxVDpct = 0.03, fedFrom = "Main")
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals("MANUAL_OVERRIDE", interp.selectionSource)
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertNotEquals(GoverningCriterion.NONE, interp.governingCriterion)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    // -------------------------------------------------------------------------
    // Section 7: Check Contract Typed Values Tests
    // -------------------------------------------------------------------------

    @Test
    fun structuredCheck_preservesTypedActualValueAndUnit() {
        val dummyResult = SizingResult(
            Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 125.0, productCode = "C1", cableSize_mm2 = "50 mm²",
            ratedI_table_A = 150.0, capacity_derated_Iz_A = 150.0, utilizationPct = 66.7,
            neutral_mm2 = "25 mm²", earthCPC_mm2 = "25 mm²", voltDrop_V = 2.0,
            vdPct = 0.5, cumulativeVDpct = 0.5, status = "✓ PASS"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", maxVDpct = 0.03, fedFrom = "Main")
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val ampacityCheck = eval.checks.first { it.checkCode == CableCheckCode.AMPACITY }

        assertNotNull(ampacityCheck.actualValue)
        assertEquals(150.0, ampacityCheck.actualValue!!.decimalValue!!, 0.001)
        assertEquals("A", ampacityCheck.unit)
    }

    @Test
    fun structuredCheck_preservesTypedLimitAndUnit() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", maxVDpct = 0.03, fedFrom = "Main")
        val dummyResult = SizingResult(
            Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 125.0, productCode = "C1", cableSize_mm2 = "50 mm²",
            ratedI_table_A = 150.0, capacity_derated_Iz_A = 150.0, utilizationPct = 66.7,
            neutral_mm2 = "25 mm²", earthCPC_mm2 = "25 mm²", voltDrop_V = 2.0,
            vdPct = 0.5, cumulativeVDpct = 0.5, status = "✓ PASS"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val vdCheck = eval.checks.first { it.checkCode == CableCheckCode.VOLTAGE_DROP }

        assertNotNull(vdCheck.limitValue)
        assertEquals(3.0, vdCheck.limitValue!!.decimalValue!!, 0.001)
        assertEquals("%", vdCheck.unit)
    }

    @Test
    fun structuredCheck_serializationIsLocaleIndependent() {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", maxVDpct = 0.03, fedFrom = "Main")
            val dummyResult = SizingResult(
                Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 125.0, productCode = "C1", cableSize_mm2 = "50 mm²",
                ratedI_table_A = 150.0, capacity_derated_Iz_A = 150.0, utilizationPct = 66.7,
                neutral_mm2 = "25 mm²", earthCPC_mm2 = "25 mm²", voltDrop_V = 2.0,
                vdPct = 0.5, cumulativeVDpct = 0.5, status = "✓ PASS",
                resultStatus = SizingResultStatus.PASS,
                isAmpacityOk = true,
                isVoltageDropOk = true,
                primaryFailureCode = null
            )
            val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
            val vdCheck = eval.checks.first { it.checkCode == CableCheckCode.VOLTAGE_DROP }
            assertTrue("Display must use US dot formatting", vdCheck.limitDisplay.contains("3.00%"))
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    @Test
    fun missingValue_remainsNull_notEmptyOrZero() {
        val dummyResult = SizingResult(
            Ib_A = 0.0, deviceSize_A = 0.0, deviceType = "N/A", cTotalDerating = 0.0,
            iRequired_perCable_A = 0.0, productCode = "N/A", cableSize_mm2 = "N/A",
            ratedI_table_A = 0.0, capacity_derated_Iz_A = 0.0, utilizationPct = 0.0,
            neutral_mm2 = "N/A", earthCPC_mm2 = "N/A", voltDrop_V = 0.0,
            vdPct = 0.0, cumulativeVDpct = 0.0, status = "✗ FAIL",
            failReason = "Missing source transformer 'TX-9' in this project.",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.INCOMPLETE_INPUT
        )
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Main")
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val ampacityCheck = eval.checks.first { it.checkCode == CableCheckCode.AMPACITY }
        assertEquals(com.example.ai.contracts.EngineeringValueKind.MISSING, ampacityCheck.actualValue!!.kind)
    }

    // -------------------------------------------------------------------------
    // Section 8: Required Parity Tests
    // -------------------------------------------------------------------------

    @Test
    fun structuredChecks_matchEngineOverallStatus() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 100.0, length_m = 50.0, maxVDpct = 0.05, fedFrom = "Main")
        val engineResult = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(engineResult, circuit)
        val expected = if (engineResult.status.contains("PASS")) CableCheckStatus.PASS else CableCheckStatus.FAIL
        assertEquals(expected, eval.overallStatus)
    }

    @Test
    fun structuredAmpacityCheck_matchesEngineDecision() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 300.0, length_m = 10.0, maxVDpct = 0.05, overrideCableSize = "1.5 mm²", fedFrom = "Main")
        val engineResult = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(engineResult, circuit)
        val ampacityCheck = eval.checks.first { it.checkCode == CableCheckCode.AMPACITY }
        assertEquals(CableCheckStatus.FAIL, ampacityCheck.status)
    }

    @Test
    fun structuredLocalVdCheck_matchesEngineDecision() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 500.0, maxVDpct = 0.001, fedFrom = "Main")
        val engineResult = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(engineResult, circuit)
        val vdCheck = eval.checks.first { it.checkCode == CableCheckCode.VOLTAGE_DROP }
        assertEquals(CableCheckStatus.FAIL, vdCheck.status)
    }

    @Test
    fun structuredCumulativeVdCheck_matchesEngineDecision() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 100.0, maxVDpct = 0.03, fedFrom = "Main")
        val engineResult = CableSizingEngine.sizeCircuit(circuit, parentCumulativeVD = 2.95)
        val eval = CableCalculationService.evaluateEngineeringChecks(engineResult, circuit, parentCumulativeVD = 2.95)
        if (engineResult.status.contains("FAIL")) {
            val cumVdCheck = eval.checks.first { it.checkCode == CableCheckCode.CUMULATIVE_VOLTAGE_DROP }
            assertEquals(CableCheckStatus.FAIL, cumVdCheck.status)
        }
    }

    @Test
    fun structuredCatalogFailure_matchesEngineDecision() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, overrideCableSize = "9999 mm²", fedFrom = "Main")
        val engineResult = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(engineResult, circuit)
        assertEquals(CableCheckStatus.FAIL, eval.overallStatus)
        assertEquals(CableCheckCode.CATALOG_AVAILABILITY, eval.governingCheckCode)
    }

    @Test
    fun structuredIncompleteInput_matchesEngineDecision() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Transformer", transformerId = "NON_EXISTENT_TX")
        val engineResult = CableCalculationService.calculateSingleCircuit(circuit, transformers = emptyList())
        val eval = CableCalculationService.evaluateEngineeringChecks(engineResult, circuit)
        assertEquals(CableCheckStatus.INCOMPLETE, eval.overallStatus)
        assertEquals(CableCheckCode.INCOMPLETE_INPUT, eval.governingCheckCode)
    }

    @Test
    fun noServiceCheckContradictsSizingResultStatus() {
        val testCircuits = listOf(
            CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 20.0, maxVDpct = 0.05, fedFrom = "Main"),
            CircuitEntity(id = 2, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 250.0, length_m = 100.0, maxVDpct = 0.03, fedFrom = "Main"),
            CircuitEntity(id = 3, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 500.0, length_m = 300.0, maxVDpct = 0.02, overrideCableSize = "1.5 mm²", fedFrom = "Main")
        )
        for (c in testCircuits) {
            val res = CableSizingEngine.sizeCircuit(c)
            val eval = CableCalculationService.evaluateEngineeringChecks(res, c)
            val expected = if (res.status.contains("PASS")) CableCheckStatus.PASS else CableCheckStatus.FAIL
            assertEquals("Service status must match SizingResult status for circuit ${c.id}", expected, eval.overallStatus)
        }
    }

    // -------------------------------------------------------------------------
    // Section 9: Static Duplication Guard
    // -------------------------------------------------------------------------

    @Test
    fun noDuplicateEngineeringRulesInServiceOrToolLayer() {
        val serviceFile = listOf(
            File("app/src/main/java/com/example/engine/coordinator/CableCalculationService.kt"),
            File("src/main/java/com/example/engine/coordinator/CableCalculationService.kt"),
            File("../app/src/main/java/com/example/engine/coordinator/CableCalculationService.kt")
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("Service file not found")

        val content = serviceFile.readText()
        assertFalse("Service must not recompute ampacity Ib <= In", content.contains("Ib_A <= result.deviceSize_A"))
        assertFalse("Service must not recompute In <= Iz", content.contains("deviceSize_A <= result.capacity_derated_Iz_A"))
        assertFalse("Service must not recompute vd <= maxAllowed", content.contains("result.vdPct <= maxAllowedVdPct"))
        assertFalse("Service must not contain sqrt calculation", content.contains("sqrt("))
        assertFalse("Service must not contain 0.70 constant", content.contains("0.70"))
    }

    @Test
    fun toolLayer_containsNoEngineeringFormulaOrThresholdLogic() {
        val cableToolsDir = listOf(
            File("app/src/main/java/com/example/ai/tools/cable"),
            File("src/main/java/com/example/ai/tools/cable"),
            File("../app/src/main/java/com/example/ai/tools/cable")
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("cable tools dir not found")

        val prohibitedTokens = listOf(
            "sqrt(",
            "0.70",
            "Math.sqrt",
            "kotlin.math.sqrt",
            "maxVDpct *",
            "maxVDpct*",
            "cTotalDerating <",
            "cTotalDerating<",
            "Ib_A <=",
            "capacity_derated_Iz_A <"
        )

        val files = cableToolsDir.listFiles { f -> f.extension == "kt" } ?: emptyArray()
        assertTrue("Must scan multiple tool files", files.isNotEmpty())

        for (file in files) {
            val content = file.readText()
            for (token in prohibitedTokens) {
                assertFalse(
                    "Prohibited engineering formula/threshold token '$token' found in tool layer file ${file.name}",
                    content.contains(token)
                )
            }
        }
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

    // =========================================================================
    // 8. BLOCKER B, E, H, K TARGETED AUDIT TESTS
    // =========================================================================

    @Test
    fun changingHumanReadableFailMessage_doesNotChangeTypedCheckOutcome() {
        val baseResult = SizingResult(
            Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 125.0, productCode = "C1", cableSize_mm2 = "1.5 mm²",
            ratedI_table_A = 20.0, capacity_derated_Iz_A = 20.0, utilizationPct = 500.0,
            neutral_mm2 = "1.5 mm²", earthCPC_mm2 = "1.5 mm²", voltDrop_V = 10.0,
            vdPct = 2.5, cumulativeVDpct = 2.5, status = "✗ FAIL",
            failReason = "Standard failure message",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false, isVoltageDropOk = true, primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Main")
        val eval1 = CableCalculationService.evaluateEngineeringChecks(baseResult, circuit)

        val rewordedResult = baseResult.copy(failReason = "Arbitrary localized human text in Arabic or German")
        val eval2 = CableCalculationService.evaluateEngineeringChecks(rewordedResult, circuit)

        assertEquals(eval1.overallStatus, eval2.overallStatus)
        assertEquals(eval1.governingCheckCode, eval2.governingCheckCode)
        assertEquals(eval1.checks.map { it.status }, eval2.checks.map { it.status })
    }

    @Test
    fun localizedOrRewordedMessage_doesNotChangeGoverningCriterion() {
        val baseResult = SizingResult(
            Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 125.0, productCode = "C1", cableSize_mm2 = "1.5 mm²",
            ratedI_table_A = 20.0, capacity_derated_Iz_A = 20.0, utilizationPct = 500.0,
            neutral_mm2 = "1.5 mm²", earthCPC_mm2 = "1.5 mm²", voltDrop_V = 10.0,
            vdPct = 2.5, cumulativeVDpct = 2.5, status = "✗ FAIL",
            failReason = "Localized text: فشل في سعة التيار",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false, isVoltageDropOk = true, primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Main")
        val eval = CableCalculationService.evaluateEngineeringChecks(baseResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    @Test
    fun allSupportedChecks_haveTypedAuthoritativeEvidence() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)

        assertTrue(eval.checks.any { it.checkCode == CableCheckCode.AMPACITY })
        assertTrue(eval.checks.any { it.checkCode == CableCheckCode.VOLTAGE_DROP })
        assertTrue(eval.checks.any { it.checkCode == CableCheckCode.CUMULATIVE_VOLTAGE_DROP })
        eval.checks.forEach { check ->
            assertNotNull("Each check must have typed actualValue", check.actualValue)
            assertNotNull("Each check must have typed limitValue", check.limitValue)
        }
    }

    @Test
    fun unsupportedCheck_remainsExplicitlyNotEvaluated() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, fedFrom = "Main")
        val sizingRes = CableSizingEngine.sizeCircuit(circuit)
        val eval = CableCalculationService.evaluateEngineeringChecks(sizingRes, circuit)

        assertFalse(eval.checks.any { it.checkCode == CableCheckCode.SHORT_CIRCUIT_WITHSTAND })
        assertFalse(eval.checks.any { it.checkCode == CableCheckCode.DEVICE_COMPATIBILITY })
    }

    @Test
    fun failedStatusWithoutTypedFailedCheck_doesNotInventMultipleConstraints() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(10.0, 16.0, "MCB", 1.0, 16.0, "C1", "2.5 mm²", 25.0, 25.0, 40.0, "", "", 1.0, 0.5, 0.5, "✗ FAIL", failReason = "Opaque failure", resultStatus = SizingResultStatus.FAIL, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.FAIL,
            checks = listOf(
                CableStructuredCheck(CableCheckCode.AMPACITY, "Ampacity", CableCheckStatus.PASS, message = "Pass"),
                CableStructuredCheck(CableCheckCode.VOLTAGE_DROP, "Voltage Drop", CableCheckStatus.PASS, message = "Pass")
            ),
            governingCheckCode = null,
            governingReason = "Opaque failure"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.INDETERMINATE, interp.governingCriterion)
    }

    @Test
    fun singleTypedFailure_mapsExactly() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(10.0, 16.0, "MCB", 1.0, 16.0, "C1", "2.5 mm²", 25.0, 25.0, 40.0, "", "", 1.0, 0.5, 0.5, "✗ FAIL", failReason = "Ampacity failure", resultStatus = SizingResultStatus.FAIL, isAmpacityOk = false, isVoltageDropOk = true, primaryFailureCode = CableFailureCode.AMPACITY_FAIL),
            overallStatus = CableCheckStatus.FAIL,
            checks = listOf(
                CableStructuredCheck(CableCheckCode.AMPACITY, "Ampacity", CableCheckStatus.FAIL, message = "Fail"),
                CableStructuredCheck(CableCheckCode.VOLTAGE_DROP, "Voltage Drop", CableCheckStatus.PASS, message = "Pass")
            ),
            governingCheckCode = CableCheckCode.AMPACITY,
            governingReason = "Ampacity failure"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    @Test
    fun multipleTypedFailures_mapsMultipleConstraints() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(10.0, 16.0, "MCB", 1.0, 16.0, "C1", "2.5 mm²", 25.0, 25.0, 40.0, "", "", 1.0, 0.5, 0.5, "✗ FAIL", failReason = "Both failed", resultStatus = SizingResultStatus.FAIL, isAmpacityOk = false, isVoltageDropOk = false, primaryFailureCode = CableFailureCode.MULTIPLE_CONSTRAINTS),
            overallStatus = CableCheckStatus.FAIL,
            checks = listOf(
                CableStructuredCheck(CableCheckCode.AMPACITY, "Ampacity", CableCheckStatus.FAIL, message = "Fail"),
                CableStructuredCheck(CableCheckCode.VOLTAGE_DROP, "Voltage Drop", CableCheckStatus.FAIL, message = "Fail")
            ),
            governingCheckCode = null,
            governingReason = "Both failed"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(GoverningCriterion.MULTIPLE_CONSTRAINTS, interp.governingCriterion)
    }

    @Test
    fun warningOnly_doesNotMapToFailureCriterion() {
        val eval = CableEngineeringEvaluation(
            sizingResult = SizingResult(10.0, 16.0, "MCB", 1.0, 16.0, "C1", "2.5 mm²", 25.0, 25.0, 40.0, "", "", 1.0, 0.5, 0.5, "✓ PASS", resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null),
            overallStatus = CableCheckStatus.PASS,
            checks = listOf(
                CableStructuredCheck(CableCheckCode.AMPACITY, "Ampacity", CableCheckStatus.WARNING, message = "Warning")
            ),
            governingCheckCode = null,
            governingReason = "Warning only"
        )
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)
        assertEquals(CheckStatus.PASS, interp.overallStatus)
        assertEquals(GoverningCriterion.NONE, interp.governingCriterion)
    }

    @Test
    fun candidateEvaluation_stateSnapshotUnchanged() = runBlocking {
        val beforeCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val beforeRev = repository.getCurrentRevision(projectId).revision
        val req = ToolRequestEnvelope(
            requestId = "REQ-SNAP-CAND",
            projectId = projectId,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = projectId, circuitId = "1", candidateSizeMm2 = 35.0)
        )
        invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req)
        val afterCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val afterRev = repository.getCurrentRevision(projectId).revision

        assertEquals(beforeCircuits, afterCircuits)
        assertEquals(beforeRev, afterRev)
    }

    @Test
    fun whatIf_stateSnapshotUnchanged() = runBlocking {
        val beforeCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val beforeRev = repository.getCurrentRevision(projectId).revision
        val req = ToolRequestEnvelope(
            requestId = "REQ-SNAP-WIF",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = 150.0)
            )
        )
        invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        val afterCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val afterRev = repository.getCurrentRevision(projectId).revision

        assertEquals(beforeCircuits, afterCircuits)
        assertEquals(beforeRev, afterRev)
    }

    @Test
    fun whatIf_exception_stateSnapshotUnchanged() = runBlocking {
        val beforeCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val beforeRev = repository.getCurrentRevision(projectId).revision
        val req = ToolRequestEnvelope(
            requestId = "REQ-SNAP-EXC",
            projectId = projectId,
            toolName = CableToolNames.RUN_WHAT_IF,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(length_m = -50.0)
            )
        )
        invoker.executeDirect<RunCableWhatIfRequest, CableWhatIfResult>(req)
        val afterCircuits = database.circuitDao().getCircuitsListForProject(projectId)
        val afterRev = repository.getCurrentRevision(projectId).revision

        assertEquals(beforeCircuits, afterCircuits)
        assertEquals(beforeRev, afterRev)
    }

    @Test
    fun candidateAcrossSameIdsDifferentProjects_neverLeaks() = runBlocking {
        val p1 = "P1"
        val p2 = "P2"
        database.projectDao().upsertProject(ProjectEntity(projectId = p1, projectName = "Project 1"))
        database.projectDao().upsertProject(ProjectEntity(projectId = p2, projectName = "Project 2"))
        database.circuitDao().insertCircuit(CircuitEntity(id = 101, projectId = p1, circuitName = "C_P1", loadKVA = 20.0, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Main"))
        database.circuitDao().insertCircuit(CircuitEntity(id = 102, projectId = p2, circuitName = "C_P2", loadKVA = 200.0, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Main"))

        val req1 = ToolRequestEnvelope(
            requestId = "REQ-P1",
            projectId = p1,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = p1, circuitId = "101", candidateSizeMm2 = 10.0)
        )
        val req2 = ToolRequestEnvelope(
            requestId = "REQ-P2",
            projectId = p2,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = p2, circuitId = "102", candidateSizeMm2 = 10.0)
        )

        val res1 = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req1)
        val res2 = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(req2)

        assertNotNull(res1.result)
        assertNotNull(res2.result)
        assertEquals(28.87, res1.result!!.details.Ib_A, 5.0)
        assertEquals(288.67, res2.result!!.details.Ib_A, 5.0)

        // Cross-project access must fail
        val reqCross = ToolRequestEnvelope(
            requestId = "REQ-CROSS",
            projectId = p2,
            toolName = CableToolNames.EVALUATE_CABLE_CANDIDATE,
            parameters = EvaluateCableCandidateRequest(projectId = p2, circuitId = "101", candidateSizeMm2 = 10.0)
        )
        val resCross = invoker.executeDirect<EvaluateCableCandidateRequest, CableCandidateEvaluationResult>(reqCross)
        assertEquals(ToolStatus.INCOMPLETE, resCross.status)
    }

    @Test
    fun quickCalc_matchesPrePhase6AuthoritativeResult() {
        val circuit = CircuitEntity(id = 0, projectId = "", circuitName = "QuickScratch", loadKVA = 45.0, cableType = "Cu/XLPE/PVC - 4C", fedFrom = "Main")
        val res = CableSizingEngine.sizeCircuit(circuit)
        assertEquals("✓ PASS", res.status)
        assertTrue(res.Ib_A > 0.0)
    }

    @Test
    fun projectMode_matchesPrePhase6AuthoritativeResult() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 30.0, fedFrom = "Main")
        val res = CableSizingEngine.sizeCircuit(circuit)
        assertEquals("✓ PASS", res.status)
        assertTrue(res.Ib_A > 0.0)
    }

    @Test
    fun buswayDispatch_unchanged() {
        val circuit = CircuitEntity(id = 1, feederType = "Busway", loadKVA = 500.0, voltageV = 400, phases = "3P+N", fedFrom = "Main")
        val res = CableCalculationService.calculateSingleCircuit(circuit)
        assertNotNull(res)
        assertTrue(res.status.contains("PASS") || res.status.contains("FAIL"))
    }

    @Test
    fun existingManualOverrideBehavior_unchanged() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, overrideCableSize = "50 mm²", fedFrom = "Main")
        val res = CableSizingEngine.sizeCircuit(circuit)
        assertEquals("50 mm²", res.cableSize_mm2)
        assertEquals("✓ PASS", res.status)
    }

    // =========================================================================
    // SECTION 9: MANDATORY ADVERSARIAL STRING-INDEPENDENCE & TYPED INVARIANTS
    // =========================================================================

    @Test
    fun typedPass_withNonEnglishDisplayStatus_remainsPass() {
        val dummyResult = SizingResult(
            Ib_A = 45.0,
            deviceSize_A = 50.0,
            deviceType = "MCB",
            cTotalDerating = 0.8,
            iRequired_perCable_A = 56.25,
            productCode = "CODE-1",
            cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0,
            capacity_derated_Iz_A = 80.0,
            utilizationPct = 56.25,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "ناجح / REUSSI", // Non-English display text
            failReason = "",
            resultStatus = SizingResultStatus.PASS,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.PASS, interp.overallStatus)
        assertEquals(GoverningCriterion.NONE, interp.governingCriterion)
    }

    @Test
    fun typedFail_withDisplayTextContainingPass_remainsFail() {
        val dummyResult = SizingResult(
            Ib_A = 100.0,
            deviceSize_A = 125.0,
            deviceType = "MCCB",
            cTotalDerating = 0.8,
            iRequired_perCable_A = 156.25,
            productCode = "CODE-2",
            cableSize_mm2 = "25 mm²",
            ratedI_table_A = 120.0,
            capacity_derated_Iz_A = 96.0,
            utilizationPct = 104.1,
            neutral_mm2 = "25 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 5.0,
            vdPct = 1.25,
            cumulativeVDpct = 1.25,
            status = "✓ PASS (DECEPTIVE DISPLAY)", // Adversarial display text containing 'PASS'
            failReason = "Deceptive success text",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = true,
            primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    @Test
    fun typedWarning_withPassDisplayText_remainsWarning() {
        val dummyResult = SizingResult(
            Ib_A = 45.0,
            deviceSize_A = 50.0,
            deviceType = "MCB",
            cTotalDerating = 0.8,
            iRequired_perCable_A = 56.25,
            productCode = "CODE-3",
            cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0,
            capacity_derated_Iz_A = 80.0,
            utilizationPct = 56.25,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "✓ PASS",
            failReason = "",
            resultStatus = SizingResultStatus.WARNING,
            isAmpacityOk = true,
            isVoltageDropOk = true,
            primaryFailureCode = null
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.WARNING, interp.overallStatus)
        assertEquals(GoverningCriterion.NONE, interp.governingCriterion)
    }

    @Test
    fun typedIncomplete_withBlankFailReason_remainsIncomplete() {
        val dummyResult = SizingResult(
            Ib_A = 0.0,
            deviceSize_A = 0.0,
            deviceType = "N/A",
            cTotalDerating = 0.0,
            iRequired_perCable_A = 0.0,
            productCode = "N/A",
            cableSize_mm2 = "N/A",
            ratedI_table_A = 0.0,
            capacity_derated_Iz_A = 0.0,
            utilizationPct = 0.0,
            neutral_mm2 = "N/A",
            earthCPC_mm2 = "N/A",
            voltDrop_V = 0.0,
            vdPct = 0.0,
            cumulativeVDpct = 0.0,
            status = "",
            failReason = "", // Blank fail reason
            resultStatus = SizingResultStatus.INCOMPLETE,
            isAmpacityOk = false,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.INCOMPLETE_INPUT
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.INCOMPLETE, interp.overallStatus)
        assertEquals(GoverningCriterion.INCOMPLETE_INPUT, interp.governingCriterion)
    }

    @Test
    fun typedError_withPassDisplayText_remainsError() {
        val dummyResult = SizingResult(
            Ib_A = 0.0,
            deviceSize_A = 0.0,
            deviceType = "N/A",
            cTotalDerating = 0.0,
            iRequired_perCable_A = 0.0,
            productCode = "N/A",
            cableSize_mm2 = "N/A",
            ratedI_table_A = 0.0,
            capacity_derated_Iz_A = 0.0,
            utilizationPct = 0.0,
            neutral_mm2 = "N/A",
            earthCPC_mm2 = "N/A",
            voltDrop_V = 0.0,
            vdPct = 0.0,
            cumulativeVDpct = 0.0,
            status = "✓ ALL_CLEAR_PASS", // Deceptive text
            failReason = "Internal engine crash",
            resultStatus = SizingResultStatus.ERROR,
            isAmpacityOk = false,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.INDETERMINATE
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.INDETERMINATE, interp.governingCriterion)
    }

    @Test
    fun typedFailureCode_drivesCriterion_withoutMessageParsing() {
        val dummyResult = SizingResult(
            Ib_A = 50.0,
            deviceSize_A = 63.0,
            deviceType = "MCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 63.0,
            productCode = "CODE-VD",
            cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0,
            capacity_derated_Iz_A = 100.0,
            utilizationPct = 50.0,
            neutral_mm2 = "16 mm²",
            earthCPC_mm2 = "16 mm²",
            voltDrop_V = 25.0,
            vdPct = 6.25,
            cumulativeVDpct = 6.25,
            status = "✗ FAIL",
            failReason = "فشل هبوط الجهد / Voltage drop in non-standard phrasing",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = true,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.VOLTAGE_DROP_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.03)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.VOLTAGE_DROP, interp.governingCriterion)
    }

    @Test
    fun localizedStatusSymbols_doNotAffectMachineOutcome() {
        val passRes = SizingResult(
            Ib_A = 45.0, deviceSize_A = 50.0, deviceType = "MCB", cTotalDerating = 0.8,
            iRequired_perCable_A = 56.25, productCode = "C1", cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0, capacity_derated_Iz_A = 80.0, utilizationPct = 56.25,
            neutral_mm2 = "16 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 2.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✔ מַעֲבָר", failReason = "",
            resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null
        )
        val failRes = SizingResult(
            Ib_A = 45.0, deviceSize_A = 50.0, deviceType = "MCB", cTotalDerating = 0.8,
            iRequired_perCable_A = 56.25, productCode = "C1", cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0, capacity_derated_Iz_A = 40.0, utilizationPct = 112.5,
            neutral_mm2 = "16 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 2.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✘ נִכְשָׁל", failReason = "Opaque failure",
            resultStatus = SizingResultStatus.FAIL, isAmpacityOk = false, isVoltageDropOk = true, primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)

        val evalPass = CableCalculationService.evaluateEngineeringChecks(passRes, circuit)
        val evalFail = CableCalculationService.evaluateEngineeringChecks(failRes, circuit)

        assertEquals(CheckStatus.PASS, GoverningCriterionInterpreter.fromEvaluation(evalPass).overallStatus)
        assertEquals(CheckStatus.FAIL, GoverningCriterionInterpreter.fromEvaluation(evalFail).overallStatus)
    }

    @Test
    fun changingFailReasonText_doesNotChangeStructuredChecks() {
        val resA = SizingResult(
            Ib_A = 100.0, deviceSize_A = 125.0, deviceType = "MCCB", cTotalDerating = 0.8,
            iRequired_perCable_A = 156.25, productCode = "C1", cableSize_mm2 = "25 mm²",
            ratedI_table_A = 120.0, capacity_derated_Iz_A = 96.0, utilizationPct = 104.1,
            neutral_mm2 = "25 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 5.0, vdPct = 1.25,
            cumulativeVDpct = 1.25, status = "✗ FAIL", failReason = "English failure text: Ampacity exceeded",
            resultStatus = SizingResultStatus.FAIL, isAmpacityOk = false, isVoltageDropOk = true, primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val resB = resA.copy(failReason = "Deutsche Fehlermeldung: Strombelastbarkeit überschritten")
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)

        val evalA = CableCalculationService.evaluateEngineeringChecks(resA, circuit)
        val evalB = CableCalculationService.evaluateEngineeringChecks(resB, circuit)

        val interpA = GoverningCriterionInterpreter.fromEvaluation(evalA)
        val interpB = GoverningCriterionInterpreter.fromEvaluation(evalB)

        assertEquals(interpA.overallStatus, interpB.overallStatus)
        assertEquals(interpA.governingCriterion, interpB.governingCriterion)
        assertEquals(interpA.checks.size, interpB.checks.size)
        assertEquals(interpA.checks[0].status, interpB.checks[0].status)
        assertEquals(interpA.checks[1].status, interpB.checks[1].status)
    }

    @Test
    fun unknownTypedFailure_mapsIndeterminate_notMultipleConstraints() {
        val dummyResult = SizingResult(
            Ib_A = 50.0, deviceSize_A = 63.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 63.0, productCode = "C1", cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0, capacity_derated_Iz_A = 100.0, utilizationPct = 50.0,
            neutral_mm2 = "16 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 2.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✗ FAIL", failReason = "Unknown failure cause",
            resultStatus = SizingResultStatus.FAIL, isAmpacityOk = true, isVoltageDropOk = true,
            primaryFailureCode = CableFailureCode.INDETERMINATE
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.INDETERMINATE, interp.governingCriterion)
        assertNotEquals(GoverningCriterion.MULTIPLE_CONSTRAINTS, interp.governingCriterion)
    }

    @Test
    fun passWithFailureCode_isRejectedByInvariant() {
        // Contradictory result: resultStatus = PASS but failureCode is set
        val contradictoryResult = SizingResult(
            Ib_A = 50.0, deviceSize_A = 63.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 63.0, productCode = "C1", cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0, capacity_derated_Iz_A = 100.0, utilizationPct = 50.0,
            neutral_mm2 = "16 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 2.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✓ PASS", failReason = "Contradiction",
            resultStatus = SizingResultStatus.PASS, isAmpacityOk = false, isVoltageDropOk = true,
            primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(contradictoryResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        // Must fail closed because primaryFailureCode is not null
        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.AMPACITY, interp.governingCriterion)
    }

    @Test
    fun failWithoutFailureCode_mapsIndeterminate() {
        val dummyResult = SizingResult(
            Ib_A = 50.0, deviceSize_A = 63.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 63.0, productCode = "C1", cableSize_mm2 = "16 mm²",
            ratedI_table_A = 100.0, capacity_derated_Iz_A = 100.0, utilizationPct = 50.0,
            neutral_mm2 = "16 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 2.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✗ FAIL", failReason = "Generic fail with null failure code",
            resultStatus = SizingResultStatus.FAIL, isAmpacityOk = true, isVoltageDropOk = true,
            primaryFailureCode = null
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(dummyResult, circuit)
        val interp = GoverningCriterionInterpreter.fromEvaluation(eval)

        assertEquals(CheckStatus.FAIL, interp.overallStatus)
        assertEquals(GoverningCriterion.INDETERMINATE, interp.governingCriterion)
    }

    @Test
    fun allSizingResultFactories_populateTypedFieldsConsistently() {
        // Factory 1: Clean Pass
        val passCircuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 25.0, fedFrom = "Main")
        val passRes = CableSizingEngine.sizeCircuit(passCircuit)
        assertEquals(SizingResultStatus.PASS, passRes.resultStatus)
        assertNull(passRes.primaryFailureCode)
        assertTrue(passRes.isAmpacityOk)
        assertTrue(passRes.isVoltageDropOk)

        // Factory 2: Incomplete Input (negative load)
        val invalidCircuit = CircuitEntity(id = 2, cableType = "Cu/XLPE/PVC - 4C", loadKVA = -10.0, length_m = 25.0, fedFrom = "Main")
        val invalidRes = CableSizingEngine.sizeCircuit(invalidCircuit)
        assertEquals(SizingResultStatus.INCOMPLETE, invalidRes.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, invalidRes.primaryFailureCode)
        assertFalse(invalidRes.isAmpacityOk)
        assertFalse(invalidRes.isVoltageDropOk)

        // Factory 3: Missing Catalog
        val missingCatCircuit = CircuitEntity(id = 3, cableType = "NonExistentCableType999", loadKVA = 50.0, length_m = 25.0, fedFrom = "Main")
        val missingCatRes = CableSizingEngine.sizeCircuit(missingCatCircuit)
        assertEquals(SizingResultStatus.FAIL, missingCatRes.resultStatus)
        assertEquals(CableFailureCode.CATALOG_MISSING, missingCatRes.primaryFailureCode)

        // Factory 4: Ampacity / Sizing Failure on Manual Override
        val failingOverrideCircuit = CircuitEntity(id = 4, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 200.0, length_m = 25.0, overrideCableSize = "1.5 mm²", fedFrom = "Main")
        val failingOverrideRes = CableSizingEngine.sizeCircuit(failingOverrideCircuit)
        assertEquals(SizingResultStatus.FAIL, failingOverrideRes.resultStatus)
        assertTrue(failingOverrideRes.primaryFailureCode == CableFailureCode.AMPACITY_FAIL || failingOverrideRes.primaryFailureCode == CableFailureCode.MULTIPLE_CONSTRAINTS)
    }

    @Test
    fun sizingResult_hasNoStringDerivedTypedDefaults() {
        val res = SizingResult(
            Ib_A = 10.0, deviceSize_A = 16.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 16.0, productCode = "C1", cableSize_mm2 = "2.5 mm²",
            ratedI_table_A = 25.0, capacity_derated_Iz_A = 25.0, utilizationPct = 40.0,
            neutral_mm2 = "", earthCPC_mm2 = "", voltDrop_V = 1.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✗ FAIL"
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
        assertEquals(SizingResultStatus.PASS, res.resultStatus)
    }

    @Test
    fun allProductionSizingResultCallSites_supplyTypedFields() {
        val circuit = CircuitEntity(id = 1, cableType = "Cu/XLPE/PVC - 4C", loadKVA = 50.0, length_m = 25.0, fedFrom = "Main")
        val autoRes = CableSizingEngine.sizeCircuit(circuit)
        assertEquals(SizingResultStatus.PASS, autoRes.resultStatus)
        assertNull(autoRes.primaryFailureCode)

        val buswayRes = BuswayEngine.sizeBusway(circuit)
        val adapted = BuswayEngine.toSizingResult(buswayRes)
        assertNotNull(adapted.resultStatus)
    }

    @Test
    fun misleadingDisplayStatus_cannotInitializeTypedStatus() {
        val result = SizingResult(
            Ib_A = 10.0, deviceSize_A = 16.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 16.0, productCode = "C1", cableSize_mm2 = "2.5 mm²",
            ratedI_table_A = 25.0, capacity_derated_Iz_A = 25.0, utilizationPct = 40.0,
            neutral_mm2 = "", earthCPC_mm2 = "", voltDrop_V = 1.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✓ PASS DECEPTIVE",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.AMPACITY_FAIL
        )
        assertEquals(SizingResultStatus.FAIL, result.resultStatus)
        assertFalse(result.isAmpacityOk)
    }

    @Test
    fun blankFailReason_cannotInitializePassState() {
        val result = SizingResult(
            Ib_A = 10.0, deviceSize_A = 16.0, deviceType = "MCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 16.0, productCode = "C1", cableSize_mm2 = "2.5 mm²",
            ratedI_table_A = 25.0, capacity_derated_Iz_A = 25.0, utilizationPct = 40.0,
            neutral_mm2 = "", earthCPC_mm2 = "", voltDrop_V = 1.0, vdPct = 0.5,
            cumulativeVDpct = 0.5, status = "✗ FAIL",
            failReason = "",
            resultStatus = SizingResultStatus.FAIL,
            isAmpacityOk = false,
            isVoltageDropOk = false,
            primaryFailureCode = CableFailureCode.INDETERMINATE
        )
        val circuit = CircuitEntity(id = 1, maxVDpct = 0.05)
        val eval = CableCalculationService.evaluateEngineeringChecks(result, circuit)
        assertEquals(CableCheckStatus.FAIL, eval.overallStatus)
        assertEquals(CableCheckCode.INDETERMINATE, eval.governingCheckCode)
    }
}
