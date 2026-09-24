package com.example.ai.tools

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.context.EngineeringContext
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.hybrid.ConversationEngineSource
import com.example.ai.hybrid.ConversationEngineProvider
import com.example.ai.hybrid.LocalRuleEngineAdapter
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.ui.AssistantEntryPoint
import com.example.ai.ui.EngineeringAssistantViewModel
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE 4A: proves Finding A end to end - the deterministic tool layer is reachable from the
 * production assistant path.
 *
 * Chain exercised, with no mocks or shortcuts:
 * ViewModel -> AssistantToolRuntime -> EngineeringToolDispatcher -> EngineeringToolRegistry
 *   -> existing DeterministicCableToolInvoker -> CalculateCableSizingTool
 *   -> EngineeringRepository / in-memory Room -> EngineeringCalculationCoordinator
 *   -> ToolResultEnvelope -> evidence (lastToolOutcome).
 *
 * The test fails if any link is replaced by a fake: registry binding is validated at
 * construction, revisions are resolved from the repository, and the result payload can only
 * exist if Room actually returned the seeded circuit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AssistantToolRuntimeEndToEndTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var runtime: AssistantToolRuntime

    private val projectId = "PROJ-E2E-1"
    private val projectOther = "PROJ-E2E-OTHER"

    companion object {
        /** Reuses the shared shipped-catalogue fixture; never a fabricated catalogue. */
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            CableEngineeringToolsTest.loadCatalogData()
        }

        private fun findAssetFile(filename: String): File {
            val candidates = listOf(
                File("app/src/main/assets/$filename"),
                File("src/main/assets/$filename"),
                File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: throw java.io.FileNotFoundException("Asset file not found: $filename")
        }

        /** Production-equivalent catalogue construction, as used by the ViewModel factory. */
        fun buildCatalogRepository(): CatalogRepository = CatalogRepository().apply {
            loadAllFromStrings(
                cableCatalogJson = findAssetFile("cable_catalog.json").readText(),
                cableTypesDataJson = findAssetFile("cable_types_data.json").readText(),
                breakerCatalogJson = findAssetFile("breaker_catalog.json").readText(),
                accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText(),
                deratingDataJson = findAssetFile("derating_data.json").readText(),
                transformersSeedJson = findAssetFile("transformers_seed.json").readText(),
                buswayCatalogJson = findAssetFile("busway_catalog.json").readText()
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = EngineeringRepository(database)

        // Real coordinator wiring, cleared so this test never reads another test's cache.
        EngineeringCalculationCoordinator.projectRevisionProvider = repository
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

        // Real composition root - the exact production entry point.
        runtime = AssistantToolRuntime.create(
            repository = repository,
            catalogRepository = buildCatalogRepository()
        )

        seedProjects()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        database.close()
    }

    private fun currentRevision(): Long =
        runBlocking { repository.getCurrentRevision(projectId).revision }

    private fun seedProjects() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectId, projectName = "E2E Project", revision = 1L)
        )
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectOther, projectName = "Other Project", revision = 1L)
        )

        database.transformerDao().insertTransformer(
            TransformerEntity(
                transformerId = "TX-E2E",
                projectId = projectId,
                transformerName = "Main TX",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0
            )
        )

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
                maxVDpct = 0.03,
                cableType = "Cu/XLPE/PVC - 4C",
                manufacturer = "Elsewedy",
                installation = "Laid in Free Air",
                parallelRuns = 1,
                fedFrom = "Transformer",
                transformerId = "TX-E2E"
            )
        )

        // Circuit belonging to another project: used for cross-project isolation.
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

    // 1. Full production path: ViewModel -> runtime -> dispatcher -> invoker -> Room
    @Test
    fun viewModel_executesRealCableSizingTool_andRecordsEvidence() = runTest(testDispatcher) {
        val viewModel = EngineeringAssistantViewModel(
            conversationEngine = ConversationEngineProvider.provideConversationEngine(),
            projectRevisionProvider = repository,
            toolRuntimeProvider = { runtime },
            ioDispatcher = testDispatcher
        )

        viewModel.open(
            AssistantEntryPoint.CONTEXTUAL_CIRCUIT,
            EngineeringContext(
                projectId = projectId,
                projectName = "E2E Project",
                currentScreen = "CircuitsGridScreen",
                activeEntityType = "CIRCUIT",
                activeEntityId = "1",
                activeEntityName = "MDB-Main",
                knownEntityInputs = mapOf(
                    "lengthM" to "30.0",
                    "voltage" to "400",
                    "installation" to "Laid in Free Air"
                )
            )
        )

        viewModel.onUserSubmit("size this circuit")
        testScheduler.advanceUntilIdle()

        var outcome = viewModel.lastToolOutcome.value
        var attempts = 0
        while (outcome == null && attempts < 50) {
            Thread.sleep(100)
            testScheduler.advanceUntilIdle()
            outcome = viewModel.lastToolOutcome.value
            attempts++
        }

        assertNotNull("Production path must produce a real tool outcome (Finding A). uiState=${viewModel.uiState.value}", outcome)
        outcome!!

        assertEquals("calculate_cable_sizing", outcome.toolName)
        assertEquals(projectId, outcome.envelope.projectId)
        assertEquals("Envelope must carry the authoritative live revision", currentRevision(), outcome.envelope.projectRevision)

        val status = outcome.envelope.status
        assertTrue(
            "Expected a completed engineering status but was $status / ${outcome.envelope.errors}",
            status == ToolStatus.PASS || status == ToolStatus.FAIL || status == ToolStatus.WARNING
        )

        // Payload can only exist if Room returned the seeded circuit.
        val payload = outcome.envelope.result as? CableSizingExecutionResult
        assertNotNull("Typed result payload expected", payload)
        payload!!
        assertEquals(projectId, payload.projectId)
        assertEquals("1", payload.circuitId)
        assertEquals("MDB-Main", payload.circuitName)
        assertTrue("Result must carry the live revision", payload.projectRevision >= 1L)
        assertTrue("Engine must have produced governing data", payload.governingReason.isNotBlank())
        assertTrue("Engine must have produced checks", payload.checks.isNotEmpty())

        assertNotNull("Provenance metadata expected", outcome.envelope.provenance)
        assertEquals("CableSizingEngine", outcome.envelope.provenance!!.engineName)
    }

    // 2. Registry binding: descriptor/execution drift fails construction
    @Test
    fun toolRegistry_descriptorSet_exactlyMatchesExecutableTools() {
        val declared = runtime.descriptorRegistry.getAllToolNames()
        val executable = runtime.descriptorRegistry.getExecutableToolNames()

        assertTrue("Executable tools exist", executable.isNotEmpty())
        assertEquals(
            "Schema/execution drift: descriptors must equal executable tools exactly",
            executable, declared
        )

        for (name in declared) {
            assertTrue("'$name' must be canonical snake_case", name.matches(Regex("^[a-z][a-z0-9_]*$")))
            assertNotNull("Every declared tool needs a descriptor", runtime.descriptorRegistry.getDescriptor(name))
            assertTrue(
                "Every declared tool must be allowed for its own mode",
                runtime.descriptorRegistry.isAllowed(name)
            )
        }
    }

    // 3. Unknown tool fails closed
    @Test
    fun dispatcher_unknownTool_isRejected() = runBlocking {
        val outcome = runtime.dispatcher.execute(
            toolName = "does_not_exist_tool",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "CONV-E2E"
        )

        assertEquals("does_not_exist_tool", outcome.toolName)
        assertEquals(ToolStatus.NOT_SUPPORTED, outcome.envelope.status)
        assertTrue(
            "Rejection must name the cause: ${outcome.envelope.errors}",
            outcome.envelope.errors.any { it.message.contains("UNKNOWN_TOOL") }
        )
        assertNull("A rejected tool must not produce a result payload", outcome.envelope.result)
    }

    @Test
    fun dispatcher_mutationMode_isNeverAuthorized() {
        val descriptor = runtime.descriptorRegistry.getDescriptor("calculate_cable_sizing")!!
        assertFalse("Mutation must not be declarable", descriptor.isMutationAllowed)
        assertFalse(
            "Requesting MUTATION mode must be refused",
            runtime.descriptorRegistry.isAllowed("calculate_cable_sizing", ExecutionMode.MUTATION)
        )
    }

    // 4. Cross-project / unknown entity fails closed
    @Test
    fun dispatcher_crossProjectEntity_isRejected() = runBlocking {
        val outcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            // Circuit 10 belongs to projectOther: an ownership violation, not a missing row.
            entityId = "10",
            conversationId = "CONV-E2E"
        )

        assertEquals(ToolStatus.INCOMPLETE, outcome.envelope.status)
        assertTrue(
            "Must report cross-project ownership: ${outcome.envelope.errors}",
            outcome.envelope.errors.any { it.message.contains("does not belong to project") }
        )
        assertNull(outcome.envelope.result)
    }

    @Test
    fun dispatcher_unknownEntityId_reportsMissingEntity() = runBlocking {
        val outcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "999999",
            conversationId = "CONV-E2E"
        )

        assertEquals(ToolStatus.INCOMPLETE, outcome.envelope.status)
        assertTrue(
            "Must report the missing circuit: ${outcome.envelope.errors}",
            outcome.envelope.errors.any { it.message.contains("was not found") }
        )
    }

    // 5. Project identity re-resolution: model arguments cannot redirect execution
    @Test
    fun dispatcher_modelSuppliedProjectAndEntity_areOverriddenByAuthoritativeContext() = runBlocking {
        val outcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "CONV-E2E",
            arguments = mapOf(
                "projectId" to "EVIL_PROJECT",
                "entityId" to "999"
            )
        )

        assertEquals("Authoritative projectId must be retained", projectId, outcome.envelope.projectId)
        val payload = outcome.envelope.result as? CableSizingExecutionResult
        assertNotNull("Must execute on the authoritative circuit", payload)
        assertEquals(projectId, payload!!.projectId)
        assertEquals("1", payload.circuitId)
        assertEquals("MDB-Main", payload.circuitName)
    }

    @Test
    fun dispatcher_nonExistentProject_failsClosed() = runBlocking {
        val outcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = "DOES_NOT_EXIST_PROJECT",
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "CONV-E2E"
        )

        assertEquals(ToolStatus.NOT_SUPPORTED, outcome.envelope.status)
        assertTrue(
            "Must report project not found: ${outcome.envelope.errors}",
            outcome.envelope.errors.any { it.message.contains("PROJECT_NOT_FOUND") }
        )
        assertNull(outcome.envelope.result)
    }

    // 6. Evidence correctness: mere policy decision is never tool evidence
    @Test
    fun policyDecision_doesNotCreateEngineeringEvidence() = runTest(testDispatcher) {
        val adapter = LocalRuleEngineAdapter(com.example.ai.conversation.ConversationPolicyEngine)
        val input = ConversationTurnInput(
            userUtterance = "hello there",
            projectContext = BoundedProjectContext(
                projectId = projectId,
                projectName = "E2E Project",
                liveRevision = 1L
            )
        )

        val result = adapter.evaluateTurn(input)
        assertFalse("Local rule engine policy decision must never assert backing tool result", result.hasBackingToolResult)
    }

    // 7. Revision handling: execution carries authoritative live revision and detects advances
    @Test
    fun dispatcher_carriesLiveRevision_andTracksRevisionAdvance() = runBlocking {
        // Initial run at revision 1
        val initialOutcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "CONV-E2E"
        )
        assertEquals(1L, initialOutcome.envelope.projectRevision)
        val initialPayload = initialOutcome.envelope.result as CableSizingExecutionResult
        assertEquals(1L, initialPayload.projectRevision)

        // Increment project revision in the authoritative repository
        database.projectDao().incrementRevision(projectId, System.currentTimeMillis())
        assertEquals(2L, currentRevision())

        // Next run must carry the incremented revision 2
        val advancedOutcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "CONV-E2E"
        )
        assertEquals(2L, advancedOutcome.envelope.projectRevision)
        val advancedPayload = advancedOutcome.envelope.result as CableSizingExecutionResult
        assertEquals(2L, advancedPayload.projectRevision)
    }

    // 8. No fake results: missing safety-critical inputs fail closed with typed INCOMPLETE status
    @Test
    fun cableTool_missingOrInvalidCircuitInputs_failsClosedWithoutFabricatingDefaults() = runBlocking {
        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 99,
                projectId = projectId,
                circuitName = "Incomplete-Zero-Length",
                loadKVA = 100.0,
                length_m = 0.0, // Invalid/missing length
                voltageV = 400,
                phases = "3P+N",
                pf = 0.85,
                maxVDpct = 0.03,
                cableType = "Cu/XLPE/PVC - 4C",
                manufacturer = "Elsewedy",
                installation = "Laid in Free Air",
                parallelRuns = 1
            )
        )

        val outcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "99",
            conversationId = "CONV-E2E"
        )

        assertEquals("Tool must return INVALID_INPUT for invalid/missing safety input", ToolStatus.INVALID_INPUT, outcome.envelope.status)
        assertNull("A failed validation must produce zero result payload", outcome.envelope.result)
        assertTrue(
            "Must report INVALID_LENGTH error: ${outcome.envelope.errors}",
            outcome.envelope.errors.any { it.errorCode == "INVALID_LENGTH" }
        )
    }
}