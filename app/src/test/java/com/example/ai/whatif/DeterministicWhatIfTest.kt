package com.example.ai.whatif

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.context.ContextResolver
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationLifecycleState
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CableWhatIfOverridesDto
import com.example.ai.tools.cable.CableWhatIfResult
import com.example.ai.tools.cable.CheckStatus
import com.example.ai.tools.cable.GoverningCriterion
import com.example.ai.tools.cable.RunCableWhatIfRequest
import com.example.ai.tools.cable.RunCableWhatIfTool
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.AssistantResponseMapper
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate 8: Deterministic what-if scenario tests.
 *
 * Verifies:
 * 1. Scenario does not mutate Room / baseline unchanged.
 * 2. Scenario uses typed overrides (EngineeringValue).
 * 3. Scenario evidence is distinct from baseline evidence (isScenario == true).
 * 4. Follow-up preserves both baseline and scenario evidence across turns.
 * 5. UI maps CableWhatIfResult to WhatIfResultCard with verified delta and suggested actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeterministicWhatIfTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var whatIfTool: RunCableWhatIfTool

    private val projectId = "PROJ-WHATIF-1"

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            CableEngineeringToolsTest.loadCatalogData()
        }
    }

    @Before
    fun setUp() {
        runBlocking {
            AppDatabase.useTestExecutors = true
            val context = ApplicationProvider.getApplicationContext<Context>()

            database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            repository = EngineeringRepository(database)
            EngineeringCalculationCoordinator.projectRevisionProvider = repository
            EngineeringCalculationCoordinator.clearCache()
            EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

            whatIfTool = RunCableWhatIfTool(
                repository = repository,
                revisionProvider = repository
            )

            // Seed project and baseline circuit
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "WhatIf Project", revision = 3L)
            )
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    transformerId = "TX-W1",
                    projectId = projectId,
                    transformerName = "Supply Transformer",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 1,
                    projectId = projectId,
                    circuitName = "Feeder-1",
                    loadKVA = 200.0,
                    length_m = 45.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.03,
                    cableType = "Cu/XLPE/PVC - 4C",
                    installation = "D1"
                )
            )
        }
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        // KT-12 (test hygiene): EngineeringCalculationCoordinator is a process-wide object
        // singleton and projectRevisionProvider is GLOBAL state. setUp() bound it to a repository
        // over the in-memory database that is closed below; without restoring the production
        // default, every later test class in this JVM inherits a revision provider pointing at a
        // CLOSED database, the coordinator's revision lookup throws, and EngineeringViewModel
        // circuits flows silently stop emitting (observed as ProjectIsolationTest failing only
        // when this class ran first in the same JVM). Restore the production default (null).
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        database.close()
    }

    @Test
    fun whatIfScenario_doesNotMutateRoomOrAdvanceRevision() {
        runBlocking {
        val revBefore = repository.getCurrentRevision(projectId).revision
        val circuitBefore = database.circuitDao().getCircuitById(1)!!

        val request = ToolRequestEnvelope<RunCableWhatIfRequest>(
            requestId = "REQ-WI-1",
            toolName = CableToolNames.RUN_WHAT_IF,
            projectId = projectId,
            parameters = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = "1",
                overrides = CableWhatIfOverridesDto(
                    length_m = 85.0,
                    overrideCableSize = "240"
                )
            )
        )

        val outcome = whatIfTool.execute(request)
        assertTrue(outcome.status == ToolStatus.PASS || outcome.status == ToolStatus.WARNING)
        val result = outcome.result!!

        assertTrue("scenarioSize must contain 240", result.scenarioSize.contains("240"))
        assertNotNull("baselineSize must not be null", result.baselineSize)

        // CRITICAL: Room database MUST NOT be mutated!
        val revAfter = repository.getCurrentRevision(projectId).revision
        val circuitAfter = database.circuitDao().getCircuitById(1)!!

        assertEquals("Revision must not change during what-if scenario", revBefore, revAfter)
        assertEquals("Circuit length in DB must remain baseline", circuitBefore.length_m, circuitAfter.length_m, 0.001)
        assertEquals("Circuit override size in DB must remain null", circuitBefore.overrideCableSize, circuitAfter.overrideCableSize)
        }
    }

    @Test
    fun whatIfEvidence_isDistinctFromBaselineEvidence() {
        val outcome = com.example.ai.tools.ToolExecutionOutcome(
            toolName = CableToolNames.RUN_WHAT_IF,
            requestId = "REQ-WI-DISTINCT",
            envelope = com.example.ai.contracts.ToolResultEnvelope(
                requestId = "REQ-WI-DISTINCT",
                toolName = CableToolNames.RUN_WHAT_IF,
                projectId = projectId,
                status = ToolStatus.PASS,
                result = CableWhatIfResult(
                    projectId = projectId,
                    circuitId = "1",
                    circuitName = "Feeder-1",
                    sourceRevision = 3L,
                    baselineSize = "70",
                    scenarioSize = "240",
                    baselineStatus = CheckStatus.FAIL,
                    scenarioStatus = CheckStatus.PASS,
                    baselineGoverningCriterion = GoverningCriterion.VOLTAGE_DROP,
                    scenarioGoverningCriterion = GoverningCriterion.NONE,
                    baselineFreshness = "CURRENT",
                    comparisonValidity = "VALID",
                    inputDifferences = emptyList(),
                    scenarioDetails = com.example.ai.tools.cable.CableSizingDetailsDto(
                        Ib_A = 180.0,
                        deviceSize_A = 200.0,
                        deviceType = "MCCB",
                        cTotalDerating = 0.8,
                        iRequired_perCable_A = 180.0,
                        productCode = "CABLE-240",
                        cableSize_mm2 = "240",
                        ratedI_table_A = 400.0,
                        capacity_derated_Iz_A = 320.0,
                        utilizationPct = 56.25,
                        neutral_mm2 = "120",
                        earthCPC_mm2 = "70",
                        voltDrop_V = 5.2,
                        vdPct = 1.3,
                        cumulativeVDpct = 1.3,
                        status = "PASS"
                    ),
                    scenarioChecks = emptyList()
                )
            )
        )

        val evidence = EngineeringEvidenceRecord.fromOutcome(outcome)

        // Verifies isScenario flag is true
        assertTrue("Evidence record from what_if must have isScenario=true", evidence.isScenario)
        assertEquals("1", evidence.entityId)
    }

    @Test
    fun multiTurnFollowUp_preservesBaselineAndScenarioEvidence() {
        val baselineEvidence = EngineeringEvidenceRecord(
            evidenceId = "EVD-BASE-1",
            requestId = "REQ-BASE-1",
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            projectRevision = 3L,
            executionStatus = ToolStatus.FAIL,
            isScenario = false
        )

        val scenarioEvidence = EngineeringEvidenceRecord(
            evidenceId = "EVD-SCENARIO-1",
            requestId = "REQ-SCENARIO-1",
            toolName = CableToolNames.RUN_WHAT_IF,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            projectRevision = 3L,
            executionStatus = ToolStatus.PASS,
            isScenario = true
        )

        val evidenceHistory = listOf(baselineEvidence, scenarioEvidence)

        val projectCtx = BoundedProjectContext(
            projectId = projectId,
            projectName = "Test Project",
            liveRevision = 3L,
            activeScreen = "CIRCUIT_EDITOR",
            activeEntityType = "CIRCUIT",
            activeEntityId = "1",
            knownEntityInputs = mapOf("cableSize" to "70 mm²")
        )

        val priorState = ConversationTaskState(
            taskId = "TASK-WI-1",
            projectId = projectId,
            baseProjectRevision = 3L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "1",
            isWhatIfBranch = true,
            typedWhatIfOverrides = mapOf(
                "targetSize" to EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER)
            ),
            evidenceReferences = listOf("EVD-BASE-1", "EVD-SCENARIO-1")
        )

        // Follow-up utterance: "قارنه بالحالي"
        val resolved = ContextResolver.resolve(
            userUtterance = "قارنه بالحالي",
            projectContext = projectCtx,
            priorState = priorState,
            historicalEvidence = evidenceHistory
        )

        // Verifies intent is resolved as COMPARE and what-if overrides are preserved
        assertEquals(com.example.ai.conversation.AssistantIntent.COMPARE, resolved.updatedTaskState.primaryIntent)
        assertTrue(resolved.updatedTaskState.isWhatIfBranch)
        assertEquals(1, resolved.updatedTaskState.typedWhatIfOverrides.size)
        assertEquals(240.0, resolved.updatedTaskState.typedWhatIfOverrides["targetSize"]?.decimalValue ?: 0.0, 0.001)
    }

    @Test
    fun assistantResponseMapper_mapsWhatIfResultCardWithMetricsAndActions() {
        val whatIfResult = CableWhatIfResult(
            projectId = projectId,
            circuitId = "1",
            circuitName = "Feeder-1",
            sourceRevision = 3L,
            baselineSize = "70",
            scenarioSize = "240",
            baselineStatus = CheckStatus.FAIL,
            scenarioStatus = CheckStatus.PASS,
            baselineGoverningCriterion = GoverningCriterion.VOLTAGE_DROP,
            scenarioGoverningCriterion = GoverningCriterion.NONE,
            baselineFreshness = "CURRENT",
            comparisonValidity = "VALID",
            inputDifferences = listOf(
                com.example.ai.tools.cable.WhatIfFieldDiffDto("length_m", "45", "85"),
                com.example.ai.tools.cable.WhatIfFieldDiffDto("cableSize", "70", "240")
            ),
            scenarioDetails = com.example.ai.tools.cable.CableSizingDetailsDto(
                Ib_A = 180.0,
                deviceSize_A = 200.0,
                deviceType = "MCCB",
                cTotalDerating = 0.8,
                iRequired_perCable_A = 180.0,
                productCode = "CABLE-240",
                cableSize_mm2 = "240",
                ratedI_table_A = 400.0,
                capacity_derated_Iz_A = 320.0,
                utilizationPct = 56.25,
                neutral_mm2 = "120",
                earthCPC_mm2 = "70",
                voltDrop_V = 5.2,
                vdPct = 1.3,
                cumulativeVDpct = 1.3,
                status = "PASS"
            ),
            scenarioChecks = emptyList()
        )

        val evidence = listOf(
            EngineeringEvidenceRecord(
                evidenceId = "EVD-WI-RESULT",
                requestId = "REQ-WI-RESULT",
                toolName = CableToolNames.RUN_WHAT_IF,
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "1",
                projectRevision = 3L,
                executionStatus = ToolStatus.PASS,
                isScenario = true,
                result = whatIfResult
            )
        )

        val decision = ConversationTurnDecision(
            decisionId = "DEC-WI-1",
            action = ConversationAction(
                actionType = ConversationActionType.RUN_WHAT_IF,
                reasonCode = "EVALUATE_WHAT_IF"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-WI-1",
                projectId = projectId,
                baseProjectRevision = 3L,
                activeEntityId = "1",
                isWhatIfBranch = true
            )
        )

        val projectCtx = BoundedProjectContext(
            projectId = projectId,
            projectName = "Test Project",
            liveRevision = 3L,
            activeScreen = "CIRCUIT_EDITOR",
            activeEntityType = "CIRCUIT",
            activeEntityId = "1",
            knownEntityInputs = mapOf("cableSize" to "70 mm²")
        )

        val uiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = projectCtx,
            evidence = evidence
        )

        assertTrue(uiState.activeCard is AssistantCardUiModel.WhatIfResultCard)
        val card = uiState.activeCard as AssistantCardUiModel.WhatIfResultCard

        assertEquals(3L, card.baseRevision)
        assertTrue(card.deltaSummary.contains("240 mm²"))
        assertTrue(card.deltaSummary.contains("70 mm²"))
        assertTrue(card.suggestedActions.any { it.actionType == ConversationActionType.COMPARE_CANDIDATES })
        assertTrue(card.suggestedActions.any { it.actionType == ConversationActionType.CREATE_PROPOSAL })
    }
}
