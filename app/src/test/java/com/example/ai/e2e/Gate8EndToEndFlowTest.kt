package com.example.ai.e2e

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.context.ContextResolver
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.hybrid.AiModelConfig
import com.example.ai.hybrid.CloudDegradationReason
import com.example.ai.hybrid.CloudFunctionDeclaration
import com.example.ai.hybrid.CloudLlmClientResponse
import com.example.ai.hybrid.CloudLlmConversationEngine
import com.example.ai.hybrid.CloudLlmFeatureGate
import com.example.ai.hybrid.CloudLlmRawResponse
import com.example.ai.hybrid.ConversationEngineSource
import com.example.ai.hybrid.GeminiClientSeam
import com.example.ai.hybrid.HybridConversationRouter
import com.example.ai.orchestrator.AgenticSafetyBudget
import com.example.ai.orchestrator.AgenticToolLoopOrchestrator
import com.example.ai.orchestrator.AgenticTurnItem
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.AssistantToolRuntimeEndToEndTest
import com.example.ai.tools.cable.CableCandidateSpecDto
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CableWhatIfOverridesDto
import com.example.ai.tools.cable.CalculateCableSizingRequest
import com.example.ai.tools.cable.CompareCableCandidatesRequest
import com.example.ai.tools.cable.RunCableWhatIfRequest
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.AssistantResponseMapper
import com.example.ai.ui.AssistantState
import com.example.ai.ui.CableProposalPreparationCoordinator
import com.example.ai.ui.EngineeringAssistantViewModel
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.engine.coordinator.EngineeringCalculationCoordinator
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
 * Gate 8: Mandatory End-to-End Multi-Turn Workflow Test.
 *
 * Sequence:
 * Turn 1: "راجع الدائرة دي وقولي ليه الكابل فشل"
 *   -> Diagnostic evidence generated -> FailureDiagnosisCard with actual vs limit numbers.
 * Turn 2: "جرب 400" (What-if scenario exploration)
 *   -> In-memory calculation -> WhatIfResultCard -> Room database remains UNCHANGED.
 * Turn 3: "قارنه بالحالي"
 *   -> Retains both baseline and scenario evidence -> Deterministic comparison -> ComparisonCard.
 * Turn 4: "اعتمد المقاس الجديد"
 *   -> Routes to proposal review workflow (CREATE_PROPOSAL) -> ProposalReviewCard -> Zero direct DB mutation!
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Gate8EndToEndFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var runtime: AssistantToolRuntime
    private lateinit var proposalCoordinator: CableProposalPreparationCoordinator

    private val projectId = "PROJ-E2E-GATE8"

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            CableEngineeringToolsTest.loadCatalogData()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppDatabase.useTestExecutors = true
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "test-key")

        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = EngineeringRepository(database)

        EngineeringCalculationCoordinator.projectRevisionProvider = repository
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

        runtime = AssistantToolRuntime.create(
            repository = repository,
            catalogRepository = AssistantToolRuntimeEndToEndTest.buildCatalogRepository()
        )

        proposalCoordinator = CableProposalPreparationCoordinator(
            projectDao = database.projectDao(),
            circuitDao = database.circuitDao()
        )

        seedDatabase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        database.close()
    }

    private fun seedDatabase() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectId, projectName = "Gate8 Project", revision = 1L)
        )

        database.transformerDao().insertTransformer(
            TransformerEntity(
                transformerId = "TX-GATE8",
                projectId = projectId,
                transformerName = "Main TX",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0
            )
        )

        // Seed circuit that intentionally fails voltage drop due to long run (75m)
        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 1,
                projectId = projectId,
                circuitName = "Feeder-1",
                loadKVA = 250.0,
                length_m = 75.0,
                voltageV = 400,
                phases = "3P+N",
                pf = 0.85,
                maxVDpct = 0.03,
                cableType = "Cu/XLPE/PVC - 4C",
                installation = "D1",
                fedFrom = "Transformer",
                transformerId = "TX-GATE8",
                overrideCableSize = "70"
            )
        )
    }

    @Test
    fun completeFourTurnEndToEndEngineeringFlow() {
        runTest {
            val evidenceHistory = mutableListOf<EngineeringEvidenceRecord>()
            var taskState: ConversationTaskState? = null

            val boundedContext = BoundedProjectContext(
                projectId = projectId,
                projectName = "Gate8 Project",
                liveRevision = 1L,
                activeScreen = "CIRCUIT_EDITOR",
                activeEntityType = "CIRCUIT",
                activeEntityId = "1",
                activeEntityName = "Feeder-1",
                knownEntityInputs = mapOf(
                    "voltage" to "400 V",
                    "installation" to "D1",
                    "cableSize" to "70 mm²",
                    "maxVDpct" to "3.0",
                    "status" to "FAIL"
                )
            )

        // =========================================================================
        // TURN 1: "راجع الدائرة دي وقولي ليه الكابل فشل"
        // =========================================================================
        val turn1Resolved = ContextResolver.resolve(
            userUtterance = "راجع الدائرة دي وقولي ليه الكابل فشل",
            projectContext = boundedContext,
            priorState = null,
            historicalEvidence = emptyList()
        )

        assertEquals(AssistantIntent.DIAGNOSE, turn1Resolved.updatedTaskState.primaryIntent)
        assertEquals("1", turn1Resolved.activeEntity?.entityId)

        // Dispatch deterministic calculation
        val outcome1 = runtime.dispatcher.execute(
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = turn1Resolved.updatedTaskState.taskId
        )

        val evd1 = EngineeringEvidenceRecord.fromOutcome(outcome1)
        evidenceHistory.add(evd1)
        assertFalse("Baseline calculation must not be marked as scenario", evd1.isScenario)

        val sizingResult = evd1.result as CableSizingExecutionResult
        assertTrue("Circuit must fail engineering constraints", sizingResult.overallStatus == com.example.ai.tools.cable.CheckStatus.FAIL)

        val turn1Decision = ConversationTurnDecision(
            decisionId = "DEC-TURN-1",
            action = ConversationAction(
                actionType = ConversationActionType.DIAGNOSE_FAILURE,
                reasonCode = "DIAGNOSE_FAILURE"
            ),
            updatedState = turn1Resolved.updatedTaskState.copy(
                evidenceReferences = listOf(evd1.evidenceId)
            )
        )
        taskState = turn1Decision.updatedState

        val turn1Ui = AssistantResponseMapper.mapDecisionToUiState(
            decision = turn1Decision,
            state = taskState,
            projectContext = boundedContext,
            evidence = evidenceHistory
        )

        assertTrue("Turn 1 active card must be FailureDiagnosisCard", turn1Ui.activeCard is AssistantCardUiModel.FailureDiagnosisCard)
        val diagCard = turn1Ui.activeCard as AssistantCardUiModel.FailureDiagnosisCard
        assertEquals(sizingResult.governingCriterion.name, diagCard.violatedCriterion)
        assertTrue("Diagnosis text must state actual failure reason", diagCard.diagnosisText.isNotBlank())
        assertTrue("Remediation options must be provided", diagCard.remediationOptions.isNotEmpty())

        // Fixture candidate correction caused by N3: 400 mm² is the smallest engine-authorized
        // candidate for this seeded circuit when D1 correctly sizes from the canonical Duct column.
        // 240 mm² was accepted only while D1 wrongly read the free-air ampacity column
        // (air 532 A -> Iz 505.4 A vs 500 A device rating; duct 445 A -> Iz 422.8 A -> ampacity FAIL).
        // Gate 8 validates the proposal WORKFLOW, not a fixed 240 mm² engineering requirement:
        // this is a test-fixture correction exposed by N3, not a new production defect.
        // =========================================================================
        // TURN 2: "جرب 400" (What-if scenario exploration)
        // =========================================================================
        val revBeforeTurn2 = repository.getCurrentRevision(projectId).revision
        val circuitBeforeTurn2 = database.circuitDao().getCircuitById(1)!!

        val turn2Resolved = ContextResolver.resolve(
            userUtterance = "جرب 400",
            projectContext = boundedContext,
            priorState = taskState,
            historicalEvidence = evidenceHistory
        )

        assertEquals(AssistantIntent.WHAT_IF, turn2Resolved.updatedTaskState.primaryIntent)
        assertTrue(turn2Resolved.updatedTaskState.isWhatIfBranch)
        assertEquals(400.0, turn2Resolved.updatedTaskState.typedWhatIfOverrides["targetSize"]?.decimalValue ?: 0.0, 0.001)

        // Execute what-if tool with typed candidate override
        val outcome2 = runtime.dispatcher.execute(
            toolName = CableToolNames.RUN_WHAT_IF,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = turn2Resolved.updatedTaskState.taskId,
            arguments = mapOf(
                "overrides" to mapOf("overrideCableSize" to "400"),
                "overrideCableSize" to "400"
            )
        )

        val evd2 = EngineeringEvidenceRecord.fromOutcome(outcome2)
        evidenceHistory.add(evd2)
        assertTrue("What-if evidence must have isScenario=true", evd2.isScenario)

        // Check Room DB immutability
        val revAfterTurn2 = repository.getCurrentRevision(projectId).revision
        val circuitAfterTurn2 = database.circuitDao().getCircuitById(1)!!
        assertEquals("Room project revision must NOT advance during what-if", revBeforeTurn2, revAfterTurn2)
        assertEquals("Room circuit override size must remain 70", "70", circuitAfterTurn2.overrideCableSize)

        val turn2Decision = ConversationTurnDecision(
            decisionId = "DEC-TURN-2",
            action = ConversationAction(
                actionType = ConversationActionType.RUN_WHAT_IF,
                reasonCode = "RUN_WHAT_IF",
                candidateParameters = mapOf("overrideCableSize" to "400")
            ),
            updatedState = turn2Resolved.updatedTaskState.copy(
                evidenceReferences = evidenceHistory.map { it.evidenceId }
            )
        )
        taskState = turn2Decision.updatedState

        val turn2Ui = AssistantResponseMapper.mapDecisionToUiState(
            decision = turn2Decision,
            state = taskState,
            projectContext = boundedContext,
            evidence = evidenceHistory
        )

        assertTrue("Turn 2 active card must be WhatIfResultCard", turn2Ui.activeCard is AssistantCardUiModel.WhatIfResultCard)
        val whatIfCard = turn2Ui.activeCard as AssistantCardUiModel.WhatIfResultCard
        assertEquals(1L, whatIfCard.baseRevision)
        assertTrue(whatIfCard.deltaSummary.contains("400 mm²"))

        // =========================================================================
        // TURN 3: "قارنه بالحالي"
        // =========================================================================
        val turn3Resolved = ContextResolver.resolve(
            userUtterance = "قارنه بالحالي",
            projectContext = boundedContext,
            priorState = taskState,
            historicalEvidence = evidenceHistory
        )

        assertEquals(AssistantIntent.COMPARE, turn3Resolved.updatedTaskState.primaryIntent)
        // Verifies multi-turn memory retains what-if branch and evidence references
        assertEquals(2, evidenceHistory.size)
        assertTrue(evidenceHistory.any { !it.isScenario })
        assertTrue(evidenceHistory.any { it.isScenario })

        // Execute deterministic candidate comparison
        val outcome3 = runtime.dispatcher.execute(
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = turn3Resolved.updatedTaskState.taskId,
            arguments = mapOf(
                "candidates" to listOf(
                    mapOf("candidateId" to "CAND-70", "sizeMm2" to 70.0),
                    mapOf("candidateId" to "CAND-400", "sizeMm2" to 400.0)
                )
            )
        )

        val evd3 = EngineeringEvidenceRecord.fromOutcome(outcome3)
        evidenceHistory.add(evd3)

        val turn3Decision = ConversationTurnDecision(
            decisionId = "DEC-TURN-3",
            action = ConversationAction(
                actionType = ConversationActionType.COMPARE_CANDIDATES,
                reasonCode = "COMPARE_CANDIDATES"
            ),
            updatedState = turn3Resolved.updatedTaskState.copy(
                evidenceReferences = evidenceHistory.map { it.evidenceId }
            )
        )
        taskState = turn3Decision.updatedState

        val turn3Ui = AssistantResponseMapper.mapDecisionToUiState(
            decision = turn3Decision,
            state = taskState,
            projectContext = boundedContext,
            evidence = evidenceHistory
        )

        assertTrue("Turn 3 active card must be ComparisonCard", turn3Ui.activeCard is AssistantCardUiModel.ComparisonCard)
        val compCard = turn3Ui.activeCard as AssistantCardUiModel.ComparisonCard
        assertTrue("Comparison card must show both candidates", compCard.candidateMetrics.size >= 2)

        // =========================================================================
        // TURN 4: "اعتمد المقاس الجديد"
        // =========================================================================
        val turn4Resolved = ContextResolver.resolve(
            userUtterance = "اعتمد المقاس الجديد",
            projectContext = boundedContext,
            priorState = taskState,
            historicalEvidence = evidenceHistory
        )

        assertEquals(AssistantIntent.CREATE_PROPOSAL, turn4Resolved.updatedTaskState.primaryIntent)

        val turn4Decision = ConversationTurnDecision(
            decisionId = "DEC-TURN-4",
            action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "ADOPT_NEW_SIZE",
                candidateParameters = mapOf("overrideCableSize" to "400")
            ),
            updatedState = turn4Resolved.updatedTaskState
        )

        // Prepare proposal via CableProposalPreparationCoordinator (gated approval workflow)
        val prepResult = proposalCoordinator.prepare(
            action = turn4Decision.action,
            projectContext = boundedContext,
            decisionId = turn4Decision.decisionId,
            evidence = evidenceHistory
        )

        assertTrue("Proposal preparation must succeed and be Ready for review", prepResult is CableProposalPreparationCoordinator.Result.Ready)
        val ready = prepResult as CableProposalPreparationCoordinator.Result.Ready

        assertEquals("400", ready.proposal.patch.overrideCableSize?.value)
        assertEquals("CIRCUIT", ready.proposal.targetEntityType)
        assertEquals("1", ready.proposal.targetEntityId)

        // CRITICAL CHECK: Room database MUST REMAIN UNTOUCHED!
        // No direct write to Room happened merely because user said "اعتمد المقاس الجديد"
        val circuitAfterTurn4 = database.circuitDao().getCircuitById(1)!!
        val revAfterTurn4 = repository.getCurrentRevision(projectId).revision
        assertEquals("Revision must not advance until explicit trusted approval execution", 1L, revAfterTurn4)
        assertEquals("Circuit overrideCableSize in Room must remain baseline before explicit approval", "70", circuitAfterTurn4.overrideCableSize)
        }
    }
}
