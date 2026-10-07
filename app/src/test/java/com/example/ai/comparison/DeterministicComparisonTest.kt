package com.example.ai.comparison

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
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingFailureReason
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableCandidateSpecDto
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CandidateComparisonItemDto
import com.example.ai.tools.cable.CheckStatus
import com.example.ai.tools.cable.CompareCableCandidatesRequest
import com.example.ai.tools.cable.CompareCableCandidatesTool
import com.example.ai.tools.cable.GoverningCriterion
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
 * Gate 8: Deterministic comparison tests.
 *
 * Verifies:
 * 1. Compare real candidate results via CompareCableCandidatesTool.
 * 2. Model cannot alter numeric differences or invent a winner.
 * 3. Cross-project / stale evidence is rejected.
 * 4. ComparisonCard displays verified candidate metrics from authoritative result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeterministicComparisonTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var compareTool: CompareCableCandidatesTool

    private val projectId = "PROJ-COMP-1"
    private val otherProjectId = "PROJ-COMP-OTHER"

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

            compareTool = CompareCableCandidatesTool(
                repository = repository,
                revisionProvider = repository
            )

            // Seed project and baseline circuit
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "Comparison Project", revision = 2L)
            )
            database.projectDao().upsertProject(
                ProjectEntity(projectId = otherProjectId, projectName = "Other Project", revision = 1L)
            )

            database.transformerDao().insertTransformer(
                TransformerEntity(
                    transformerId = "TX-C1",
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
                    loadKVA = 100.0,
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
                    transformerId = "TX-C1"
                )
            )
        }
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        // KT-12 (test hygiene): restore the production default for the process-wide
        // projectRevisionProvider so no later suite inherits a repository bound to the
        // in-memory database closed below. See DeterministicWhatIfTest for the full rationale.
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        database.close()
    }

    @Test
    fun compareCableCandidatesTool_executesDeterministicallyWithObjectiveRecommendation() {
        runBlocking {
            val request = ToolRequestEnvelope(
                requestId = "REQ-COMP-1",
                toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                projectId = projectId,
                parameters = CompareCableCandidatesRequest(
                    projectId = projectId,
                    circuitId = "1",
                    candidates = listOf(
                        CableCandidateSpecDto(candidateId = "CAND-185", sizeMm2 = 185.0),
                        CableCandidateSpecDto(candidateId = "CAND-240", sizeMm2 = 240.0)
                    )
                )
            )

            val outcome = compareTool.execute(request)
            assertTrue(outcome.status == ToolStatus.PASS)
            val result = outcome.result!!

            assertEquals(2, result.evaluatedCount)
            assertEquals(2, result.candidateResults.size)

            val c185 = result.candidateResults.first { it.candidateId == "CAND-185" }
            val c240 = result.candidateResults.first { it.candidateId == "CAND-240" }

            assertEquals(185.0, c185.sizeMm2, 0.001)
            assertEquals(240.0, c240.sizeMm2, 0.001)

            // Verifies utilization differences exist deterministically
            assertTrue(c185.utilizationPct > c240.utilizationPct)
            // Verifies voltage drop differences exist deterministically
            assertTrue(c185.voltDropPct > c240.voltDropPct)

            // Recommendation is backed by deterministic objective
            assertNotNull(result.smallestPassingCandidateId)
        }
    }

    @Test
    fun modelCannotAlterNumericDifferencesOrInventWinner() {
        val comparisonResult = CableCandidatesComparisonResult(
            projectId = projectId,
            circuitId = "1",
            circuitName = "Feeder-1",
            currentAuthoritativeSize = "70",
            evaluatedCount = 2,
            passingCount = 2,
            failingCount = 0,
            smallestPassingCandidateId = "CAND-185",
            lowestUtilizationPassingCandidateId = "CAND-240",
            noRecommendationReason = null,
            recommendedCandidateId = "CAND-185",
            sortPolicy = com.example.ai.tools.cable.CandidateSortPolicy.INPUT_ORDER,
            candidateResults = listOf(
                CandidateComparisonItemDto(
                    candidateId = "CAND-185",
                    sizeMm2 = 185.0,
                    sizeFormatted = "185 mm²",
                    material = "Cu",
                    parallelRuns = 1,
                    overallStatus = CheckStatus.PASS,
                    utilizationPct = 78.5,
                    voltDropPct = 2.1,
                    cumulativeVdPct = 2.1,
                    governingCriterion = GoverningCriterion.NONE,
                    governingReason = "Passes all constraints"
                ),
                CandidateComparisonItemDto(
                    candidateId = "CAND-240",
                    sizeMm2 = 240.0,
                    sizeFormatted = "240 mm²",
                    material = "Cu",
                    parallelRuns = 1,
                    overallStatus = CheckStatus.PASS,
                    utilizationPct = 62.0,
                    voltDropPct = 1.6,
                    cumulativeVdPct = 1.6,
                    governingCriterion = GoverningCriterion.NONE,
                    governingReason = "Passes all constraints"
                )
            )
        )

        val evidence = listOf(
            EngineeringEvidenceRecord(
                evidenceId = "EVD-COMP-RES",
                requestId = "REQ-COMP-RES",
                toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "1",
                projectRevision = 2L,
                executionStatus = ToolStatus.PASS,
                result = comparisonResult
            )
        )

        val projectCtx = BoundedProjectContext(
            projectId = projectId,
            projectName = "Comparison Project",
            liveRevision = 2L,
            activeScreen = "CIRCUIT_EDITOR",
            activeEntityType = "CIRCUIT",
            activeEntityId = "1"
        )

        // Narrative asserts fabricated size 300 mm² not in candidate set
        val fabricatedNarrative = "I compared the candidates and recommend 300 mm² as the winning size."
        val validation = GroundedResponseValidator.validateResponse(
            text = fabricatedNarrative,
            evidenceList = evidence,
            context = projectCtx
        )

        assertTrue(validation is GroundingValidationResult.Rejected)
        assertEquals(GroundingFailureReason.NUMERIC_MISMATCH, (validation as GroundingValidationResult.Rejected).reason)
    }

    @Test
    fun crossProjectOrStaleEvidence_isRejected() {
        val staleEvidence = EngineeringEvidenceRecord(
            evidenceId = "EVD-STALE-COMP",
            requestId = "REQ-STALE-COMP",
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            projectRevision = 1L, // live is 2L
            executionStatus = ToolStatus.PASS,
            freshnessState = "STALE"
        )

        val crossProjectEvidence = EngineeringEvidenceRecord(
            evidenceId = "EVD-CROSS-COMP",
            requestId = "REQ-CROSS-COMP",
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            projectId = otherProjectId,
            entityType = "CIRCUIT",
            entityId = "1",
            projectRevision = 2L,
            executionStatus = ToolStatus.PASS
        )

        val projectCtx = BoundedProjectContext(
            projectId = projectId,
            projectName = "Comparison Project",
            liveRevision = 2L,
            activeScreen = "CIRCUIT_EDITOR",
            activeEntityType = "CIRCUIT",
            activeEntityId = "1"
        )

        // Stale evidence must be flagged as stale
        assertFalse(staleEvidence.isCurrentAndValidFor(projectId, "1", liveRevision = 2L))

        // Cross-project evidence must be rejected
        assertFalse(crossProjectEvidence.isCurrentAndValidFor(projectId, "1", liveRevision = 2L))
    }

    @Test
    fun assistantResponseMapper_buildsComparisonCardWithCandidateMetrics() {
        val comparisonResult = CableCandidatesComparisonResult(
            projectId = projectId,
            circuitId = "1",
            circuitName = "Feeder-1",
            currentAuthoritativeSize = "70",
            evaluatedCount = 2,
            passingCount = 2,
            failingCount = 0,
            smallestPassingCandidateId = "CAND-185",
            lowestUtilizationPassingCandidateId = "CAND-240",
            noRecommendationReason = null,
            recommendedCandidateId = "CAND-185",
            sortPolicy = com.example.ai.tools.cable.CandidateSortPolicy.INPUT_ORDER,
            candidateResults = listOf(
                CandidateComparisonItemDto(
                    candidateId = "CAND-185",
                    sizeMm2 = 185.0,
                    sizeFormatted = "185 mm²",
                    material = "Cu",
                    parallelRuns = 1,
                    overallStatus = CheckStatus.PASS,
                    utilizationPct = 78.5,
                    voltDropPct = 2.1,
                    cumulativeVdPct = 2.1,
                    governingCriterion = GoverningCriterion.NONE,
                    governingReason = "Passes all constraints"
                ),
                CandidateComparisonItemDto(
                    candidateId = "CAND-240",
                    sizeMm2 = 240.0,
                    sizeFormatted = "240 mm²",
                    material = "Cu",
                    parallelRuns = 1,
                    overallStatus = CheckStatus.PASS,
                    utilizationPct = 62.0,
                    voltDropPct = 1.6,
                    cumulativeVdPct = 1.6,
                    governingCriterion = GoverningCriterion.NONE,
                    governingReason = "Passes all constraints"
                )
            )
        )

        val evidence = listOf(
            EngineeringEvidenceRecord(
                evidenceId = "EVD-COMP-1",
                requestId = "REQ-COMP-1",
                toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                projectId = projectId,
                entityType = "CIRCUIT",
                entityId = "1",
                projectRevision = 2L,
                executionStatus = ToolStatus.PASS,
                result = comparisonResult
            )
        )

        val decision = ConversationTurnDecision(
            decisionId = "DEC-COMP-1",
            action = ConversationAction(
                actionType = ConversationActionType.COMPARE_CANDIDATES,
                reasonCode = "COMPARE_ALTERNATIVES"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-COMP-1",
                projectId = projectId,
                baseProjectRevision = 2L,
                activeEntityId = "1"
            )
        )

        val projectCtx = BoundedProjectContext(
            projectId = projectId,
            projectName = "Comparison Project",
            liveRevision = 2L,
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

        assertTrue(uiState.activeCard is AssistantCardUiModel.ComparisonCard)
        val card = uiState.activeCard as AssistantCardUiModel.ComparisonCard

        // Verifies both candidates are represented in candidateMetrics
        assertTrue(card.candidateMetrics.containsKey("185 mm²"))
        assertTrue(card.candidateMetrics.containsKey("240 mm²"))

        // Verifies suggested actions include proposal for recommended candidate
        assertTrue(card.suggestedActions.any { it.actionType == ConversationActionType.CREATE_PROPOSAL })
    }
}
