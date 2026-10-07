package com.example.ai.proposal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.context.ContextResolver
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableCandidateSpecDto
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CableWhatIfOverridesDto
import com.example.ai.tools.cable.CompareCableCandidatesRequest
import com.example.ai.tools.cable.CompareCableCandidatesTool
import com.example.ai.tools.cable.RunCableWhatIfRequest
import com.example.ai.tools.cable.RunCableWhatIfTool
import com.example.ai.ui.CableProposalPreparationCoordinator
import com.example.ai.ui.ProposalReviewMapper
import com.example.ai.ui.ProposalReviewUiState
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
 * GATE 9: Engineering Proposals, Review & Safe Change Preparation.
 *
 * Verifies:
 * 1. Proposal Construction and Typed Contracts (Section 35)
 * 2. Proposal Digest & Semantic Key Determinism (Section 15, 35)
 * 3. What-If -> Proposal Flow with Zero Database Mutation (Section 21, 36, 44)
 * 4. Comparison -> Proposal Flow for Selected Candidate with Zero Mutation (Section 22, 37, 44)
 * 5. Invented Candidate Rejected Fail-Closed (Section 10, 38)
 * 6. No-Evidence Proposal Rejected Fail-Closed (Section 19, 39)
 * 7. Stale Proposal Detected When Revision Advances (Section 13, 14, 40)
 * 8. Cross-Project Evidence Rejected Fail-Closed (Section 26, 41)
 * 9. Wrong-Entity Evidence Rejected Fail-Closed (Section 27, 42)
 * 10. Cross-Domain Evidence Rejected Fail-Closed (Section 28)
 * 11. Current vs Proposed Values Strictly Preserved and Distinct (Section 9, 43)
 * 12. Ambiguous Candidate Selection Fails Closed (Section 12)
 * 13. Natural Language "Approval" Does Not Mutate (Section 31)
 * 14. Zero Mutation Proof Across All Operations (Section 44, 51)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Gate9ProposalPreparationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var whatIfTool: RunCableWhatIfTool
    private lateinit var compareTool: CompareCableCandidatesTool
    private lateinit var coordinator: CableProposalPreparationCoordinator

    private val projectId = "PROJ-GATE9-1"
    private val circuitId = 1
    private val baselineRevision = 3L

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
            compareTool = CompareCableCandidatesTool(
                repository = repository,
                revisionProvider = repository
            )
            coordinator = CableProposalPreparationCoordinator(
                projectDao = database.projectDao(),
                circuitDao = database.circuitDao()
            )

            // Seed project, transformer, and baseline circuit
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "Gate 9 Verification Project", revision = baselineRevision)
            )
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    transformerId = "TX-G9",
                    projectId = projectId,
                    transformerName = "Main Substation Transformer",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = circuitId,
                    projectId = projectId,
                    circuitName = "Feeder-Gate9",
                    transformerId = "TX-G9",
                    loadKVA = 200.0,
                    length_m = 45.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.03,
                    cableType = "Cu/XLPE/PVC - 4C",
                    installation = "D1",
                    overrideCableSize = "70" // Authoritative persisted baseline: 70 mm²
                )
            )
        }
    }

    @After
    fun tearDown() {
        // KT-12 (test hygiene): restore the production default for the process-wide
        // projectRevisionProvider so no later suite inherits a repository bound to the
        // in-memory database closed below. See DeterministicWhatIfTest for the full rationale.
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        database.close()
    }

    private fun sampleProjectContext(liveRev: Long = baselineRevision): BoundedProjectContext =
        BoundedProjectContext(
            projectId = projectId,
            projectName = "Gate 9 Verification Project",
            liveRevision = liveRev,
            activeScreen = "CircuitEditor",
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId.toString(),
            activeEntityName = "Feeder-Gate9"
        )

    // =========================================================================
    // 1. Proposal Construction and Typed Contracts (Section 35)
    // =========================================================================

    @Test
    fun unitTest_proposalConstruction_andTypedContracts() {
        val patch = CableCircuitPatch(overrideCableSize = PatchValue.set("240"))
        assertTrue("Patch must declare proposed changes", patch.hasProposedChanges())
        assertEquals(1, patch.proposedFieldCount())

        val proposal = EngineeringProposal(
            proposalId = "PROP-UNIT-1",
            schemaVersion = PROPOSAL_SCHEMA_VERSION_V1,
            projectId = projectId,
            baseProjectRevision = baselineRevision,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = patch,
            rationale = "Upsize cable to reduce voltage drop",
            sourceToolName = "ConversationAction.CREATE_PROPOSAL",
            sourceRequestId = "DEC-001",
            engineeringEvidence = ProposalEvidenceDto(
                evidenceType = "run_what_if",
                calculationReference = "EV-001",
                governingCriterion = "AMPACITY",
                values = mapOf(
                    "scenarioSize" to EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER),
                    "utilizationPct" to EngineeringValue.ofDecimal(68.5, EngineeringUnit.PERCENT)
                )
            )
        )

        assertEquals("PROPOSAL-V1", proposal.schemaVersion)
        assertEquals(ProposalOperation.UPDATE_CABLE_ATTRIBUTES, proposal.operation)
        assertEquals("240", proposal.patch.overrideCableSize?.value)
        assertEquals(PatchAction.SET, proposal.patch.overrideCableSize?.action)
        assertEquals(240.0, proposal.engineeringEvidence?.values?.get("scenarioSize")?.decimalValue)
    }

    // =========================================================================
    // 2. Proposal Digest & Semantic Key Determinism (Section 15, 35)
    // =========================================================================

    @Test
    fun unitTest_proposalDigestAndSemanticKey_determinism() {
        val digestEncoder = DefaultProposalDigestEncoder()
        val semanticKeyEncoder = DefaultProposalSemanticKeyEncoder()

        val patch = CableCircuitPatch(overrideCableSize = PatchValue.set("240"))
        val proposal1 = EngineeringProposal(
            proposalId = "PROP-D1",
            projectId = projectId,
            baseProjectRevision = baselineRevision,
            targetEntityType = "CIRCUIT",
            targetEntityId = "1",
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = patch,
            rationale = "Initial engineer rationale",
            sourceToolName = "CREATE_PROPOSAL",
            sourceRequestId = "REQ-1"
        )

        val proposal2Identical = proposal1.copy(proposalId = "PROP-D2")

        // 1. Deterministic hashing: same content produces same digest and key
        val digest1 = digestEncoder.computeDigest(proposal1)
        val digest2 = digestEncoder.computeDigest(proposal2Identical)
        assertEquals("Identical proposal content must produce identical digest", digest1, digest2)
        assertTrue("Digest must start with PROPOSAL-V1:", digest1.startsWith("PROPOSAL-V1:"))

        val semKey1 = semanticKeyEncoder.computeSemanticKey(proposal1)
        val semKey2 = semanticKeyEncoder.computeSemanticKey(proposal2Identical)
        assertEquals("Identical proposal content must produce identical semantic key", semKey1, semKey2)
        assertTrue("Semantic key must start with PROPOSAL-SEMANTIC-V1:", semKey1.startsWith("PROPOSAL-SEMANTIC-V1:"))

        // 2. Rationale invariance: changing rationale changes digest, but NOT semantic key
        val proposalWithDifferentRationale = proposal1.copy(rationale = "Completely different rationale text")
        val digestDiffRationale = digestEncoder.computeDigest(proposalWithDifferentRationale)
        val semKeyDiffRationale = semanticKeyEncoder.computeSemanticKey(proposalWithDifferentRationale)

        assertNotEquals("Digest must change when rationale changes", digest1, digestDiffRationale)
        assertEquals("Semantic key MUST remain identical when only rationale changes", semKey1, semKeyDiffRationale)

        // 3. Patch variance: changing proposed size changes BOTH digest and semantic key
        val proposalWithDifferentSize = proposal1.copy(
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300"))
        )
        assertNotEquals(digest1, digestEncoder.computeDigest(proposalWithDifferentSize))
        assertNotEquals(semKey1, semanticKeyEncoder.computeSemanticKey(proposalWithDifferentSize))
    }

    // =========================================================================
    // 3. What-If -> Proposal Flow with Zero Database Mutation (Section 21, 36, 44)
    // =========================================================================

    @Test
    fun whatIfToProposalFlow_createsReviewableProposal_withZeroMutation() {
        runBlocking {
            // Turn 1: Run What-If Scenario with 240 mm²
            val whatIfReq = RunCableWhatIfRequest(
                projectId = projectId,
                circuitId = circuitId.toString(),
                overrides = CableWhatIfOverridesDto(overrideCableSize = "240")
            )
            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-TURN1-WHATIF",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = whatIfReq
                )
            )
            assertEquals(com.example.ai.contracts.ToolStatus.PASS, whatIfEnvelope.status)
            val whatIfOutcome = com.example.ai.tools.ToolExecutionOutcome(
                toolName = CableToolNames.RUN_WHAT_IF,
                requestId = "REQ-TURN1-WHATIF",
                envelope = whatIfEnvelope
            )
            val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = whatIfOutcome,
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )
            assertTrue("Scenario evidence must have isScenario == true", scenarioEvidence.isScenario)

            // Turn 2: User says "اعتمد الاختيار ده" -> CREATE_PROPOSAL
            val action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "ADOPT_SCENARIO_CANDIDATE",
                candidateParameters = mapOf("overrideCableSize" to "240")
            )
            val prepResult = coordinator.prepare(
                action = action,
                projectContext = sampleProjectContext(),
                decisionId = "DEC-TURN2",
                evidence = listOf(scenarioEvidence)
            )

            assertTrue(
                "Proposal preparation must be Ready for review, but got: ${(prepResult as? CableProposalPreparationCoordinator.Result.SafeError)?.message}",
                prepResult is CableProposalPreparationCoordinator.Result.Ready
            )
            val ready = prepResult as CableProposalPreparationCoordinator.Result.Ready

            assertEquals(projectId, ready.proposal.projectId)
            assertEquals(baselineRevision, ready.proposal.baseProjectRevision)
            assertEquals("CIRCUIT", ready.proposal.targetEntityType)
            assertEquals(circuitId.toString(), ready.proposal.targetEntityId)
            assertEquals("240", ready.proposal.patch.overrideCableSize?.value)
            assertEquals(CableToolNames.RUN_WHAT_IF, ready.proposal.engineeringEvidence?.evidenceType)

            // Review UI Model checks
            assertFalse(ready.review.isStale)
            assertEquals(baselineRevision, ready.review.baseProjectRevision)
            assertEquals(baselineRevision, ready.review.currentLiveRevision)
            val fieldChange = ready.review.fieldChanges.first { it.fieldKey == "overrideCableSize" }
            assertEquals("70 mm²", fieldChange.currentValue?.formattedText)
            assertEquals("240 mm²", fieldChange.proposedValue?.formattedText)

            // CRITICAL ZERO-MUTATION VERIFICATION (Section 44)
            val circuitAfter = database.circuitDao().getCircuitById(circuitId)!!
            val revisionAfter = database.projectDao().getRevision(projectId)!!
            val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

            assertEquals("Room circuit overrideCableSize must remain 70 before explicit physical confirmation", "70", circuitAfter.overrideCableSize)
            assertEquals("Project revision must not advance in Gate 9", baselineRevision, revisionAfter)
            assertTrue("No mutation execution receipt must exist in Gate 9", receipts.isEmpty())
        }
    }

    // =========================================================================
    // 4. Comparison -> Proposal Flow for Selected Candidate with Zero Mutation (Section 22, 37, 44)
    // =========================================================================

    @Test
    fun comparisonToProposalFlow_createsReviewableProposalForSelectedCandidate_withZeroMutation() {
        runBlocking {
            // Run comparison: 185 mm² vs 240 mm²
            val compReq = CompareCableCandidatesRequest(
                projectId = projectId,
                circuitId = circuitId.toString(),
                candidates = listOf(
                    CableCandidateSpecDto(sizeMm2 = 185.0),
                    CableCandidateSpecDto(sizeMm2 = 240.0)
                )
            )
            val compEnvelope = compareTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-COMPARE-1",
                    projectId = projectId,
                    toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                    parameters = compReq
                )
            )
            val compOutcome = com.example.ai.tools.ToolExecutionOutcome(
                toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                requestId = "REQ-COMPARE-1",
                envelope = compEnvelope
            )
            val compEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = compOutcome,
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // User explicitly selects 240 mm²
            val action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "SELECT_CANDIDATE_240",
                candidateParameters = mapOf("overrideCableSize" to "240")
            )
            val prepResult = coordinator.prepare(
                action = action,
                projectContext = sampleProjectContext(),
                decisionId = "DEC-COMPARE-ADOPT",
                evidence = listOf(compEvidence)
            )

            assertTrue("Proposal preparation must succeed for evaluated candidate 240", prepResult is CableProposalPreparationCoordinator.Result.Ready)
            val ready = prepResult as CableProposalPreparationCoordinator.Result.Ready
            assertEquals("240", ready.proposal.patch.overrideCableSize?.value)
            assertEquals(CableToolNames.COMPARE_CABLE_CANDIDATES, ready.proposal.engineeringEvidence?.evidenceType)

            // ZERO-MUTATION VERIFICATION
            assertEquals("70", database.circuitDao().getCircuitById(circuitId)!!.overrideCableSize)
            assertEquals(baselineRevision, database.projectDao().getRevision(projectId)!!)
            assertTrue(database.mutationExecutionReceiptDao().getReceiptsForProject(projectId).isEmpty())
        }
    }

    // =========================================================================
    // 5. Invented Candidate Rejected Fail-Closed (Section 10, 38)
    // =========================================================================

    @Test
    fun inventedCandidate_isRejectedFailClosed() {
        runBlocking {
            // Comparison evaluated only 185 mm² and 240 mm²
            val compReq = CompareCableCandidatesRequest(
                projectId = projectId,
                circuitId = circuitId.toString(),
                candidates = listOf(
                    CableCandidateSpecDto(sizeMm2 = 185.0),
                    CableCandidateSpecDto(sizeMm2 = 240.0)
                )
            )
            val compEnvelope = compareTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-COMPARE-INVENTED",
                    projectId = projectId,
                    toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                    parameters = compReq
                )
            )
            val compEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.COMPARE_CABLE_CANDIDATES, "REQ-COMPARE-INVENTED", compEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // User/model attempts to propose 250 mm² (invented/not evaluated)
            val action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "ADOPT_INVENTED_250",
                candidateParameters = mapOf("overrideCableSize" to "250")
            )
            val prepResult = coordinator.prepare(
                action = action,
                projectContext = sampleProjectContext(),
                decisionId = "DEC-INVENTED",
                evidence = listOf(compEvidence)
            )

            assertTrue("Proposal for invented candidate must be rejected", prepResult is CableProposalPreparationCoordinator.Result.SafeError)
            val error = prepResult as CableProposalPreparationCoordinator.Result.SafeError
            assertTrue(
                "Error message must indicate candidate was not evaluated or authorized: ${error.message}",
                error.message.contains("was not evaluated or authorized")
            )

            // ZERO-MUTATION VERIFICATION
            assertEquals("70", database.circuitDao().getCircuitById(circuitId)!!.overrideCableSize)
            assertEquals(baselineRevision, database.projectDao().getRevision(projectId)!!)
        }
    }

    // =========================================================================
    // 6. No-Evidence Proposal Rejected Fail-Closed (Section 19, 39)
    // =========================================================================

    @Test
    fun noEvidence_isRejectedFailClosed() {
        runBlocking {
            val action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "ADOPT_WITHOUT_EVIDENCE",
                candidateParameters = mapOf("overrideCableSize" to "240")
            )
            val prepResult = coordinator.prepare(
                action = action,
                projectContext = sampleProjectContext(),
                decisionId = "DEC-NO-EVIDENCE",
                evidence = emptyList() // No evidence!
            )

            assertTrue("Proposal without evidence must fail closed", prepResult is CableProposalPreparationCoordinator.Result.SafeError)
            val error = prepResult as CableProposalPreparationCoordinator.Result.SafeError
            assertEquals("Proposal creation requires current verified engineering evidence.", error.message)

            // ZERO-MUTATION VERIFICATION
            assertEquals("70", database.circuitDao().getCircuitById(circuitId)!!.overrideCableSize)
            assertEquals(baselineRevision, database.projectDao().getRevision(projectId)!!)
        }
    }

    // =========================================================================
    // 7. Stale Proposal Detected When Revision Advances (Section 13, 14, 40)
    // =========================================================================

    @Test
    fun staleProposal_isDetectedWhenRevisionAdvances() {
        runBlocking {
            // Create valid scenario evidence at Rev 3
            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-STALE-1",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, circuitId.toString(), CableWhatIfOverridesDto(overrideCableSize = "240"))
                )
            )
            val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-STALE-1", whatIfEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // Proposal prepared successfully at Rev 3
            val prepResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "ADOPT_240",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext(liveRev = baselineRevision),
                decisionId = "DEC-STALE-INIT",
                evidence = listOf(scenarioEvidence)
            )
            assertTrue(prepResult is CableProposalPreparationCoordinator.Result.Ready)
            val proposal = (prepResult as CableProposalPreparationCoordinator.Result.Ready).proposal
            assertEquals(baselineRevision, proposal.baseProjectRevision)

            // Advance project revision in Room to Rev 4
            database.projectDao().incrementRevision(projectId)
            val liveRevAfterBump = database.projectDao().getRevision(projectId)!!
            assertEquals(4L, liveRevAfterBump)

            // Re-evaluate review state against live revision 4
            val remappedReview = ProposalReviewMapper.mapProposalToReviewUiState(
                proposal = proposal,
                currentValues = mapOf("overrideCableSize" to "70"),
                projectContext = sampleProjectContext(liveRev = liveRevAfterBump),
                proposalDigest = "PROPOSAL-V1:dummyDigest"
            )

            assertTrue("Review state must be Stale when revision advances", remappedReview is ProposalReviewUiState.Stale)
            val stale = remappedReview as ProposalReviewUiState.Stale
            assertTrue(stale.model.isStale)
            assertEquals(baselineRevision, stale.model.baseProjectRevision)
            assertEquals(4L, stale.model.currentLiveRevision)
            assertTrue(stale.reason.contains("advanced from Rev 3 to Rev 4"))

            // Also verify: preparing proposal against stale evidence directly fails closed
            val prepAgainstStale = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "ADOPT_240",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext(liveRev = liveRevAfterBump),
                decisionId = "DEC-STALE-PREP",
                evidence = listOf(scenarioEvidence) // Evidence is at Rev 3, live is Rev 4
            )
            assertTrue(prepAgainstStale is CableProposalPreparationCoordinator.Result.SafeError)
            assertTrue((prepAgainstStale as CableProposalPreparationCoordinator.Result.SafeError).message.contains("Evidence revision is stale"))
        }
    }

    // =========================================================================
    // 8. Cross-Project Evidence Rejected Fail-Closed (Section 26, 41)
    // =========================================================================

    @Test
    fun crossProjectEvidence_isRejectedFailClosed() {
        runBlocking {
            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-CROSS-1",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, circuitId.toString(), CableWhatIfOverridesDto(overrideCableSize = "240"))
                )
            )
            val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-CROSS-1", whatIfEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // Mutate evidence copy to represent a foreign project
            val crossProjectEvidence = scenarioEvidence.copy(
                evidenceId = "EV-CROSS-PRJ",
                projectId = "PROJECT_FOREIGN" // Foreign project!
            )

            val prepResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "ADOPT_CROSS_PRJ",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext(),
                decisionId = "DEC-CROSS-PRJ",
                evidence = listOf(crossProjectEvidence)
            )

            assertTrue(prepResult is CableProposalPreparationCoordinator.Result.SafeError)
            val error = prepResult as CableProposalPreparationCoordinator.Result.SafeError
            assertTrue(error.message.contains("Evidence project does not match active project"))

            // ZERO-MUTATION VERIFICATION
            assertEquals("70", database.circuitDao().getCircuitById(circuitId)!!.overrideCableSize)
            assertEquals(baselineRevision, database.projectDao().getRevision(projectId)!!)
        }
    }

    // =========================================================================
    // 9. Wrong-Entity Evidence Rejected Fail-Closed (Section 27, 42)
    // =========================================================================

    @Test
    fun wrongEntityEvidence_isRejectedFailClosed() {
        runBlocking {
            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-WRONG-ENTITY-1",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, circuitId.toString(), CableWhatIfOverridesDto(overrideCableSize = "240"))
                )
            )
            val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-WRONG-ENTITY-1", whatIfEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // Mutate evidence copy to target a different circuit
            val wrongEntityEvidence = scenarioEvidence.copy(
                evidenceId = "EV-WRONG-ENTITY",
                entityId = "999" // Foreign circuit!
            )

            val prepResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "ADOPT_WRONG_ENTITY",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext(), // active circuit is 1
                decisionId = "DEC-WRONG-ENTITY",
                evidence = listOf(wrongEntityEvidence)
            )

            assertTrue(prepResult is CableProposalPreparationCoordinator.Result.SafeError)
            val error = prepResult as CableProposalPreparationCoordinator.Result.SafeError
            assertTrue(error.message.contains("Evidence entity does not match active circuit"))

            // ZERO-MUTATION VERIFICATION
            assertEquals("70", database.circuitDao().getCircuitById(circuitId)!!.overrideCableSize)
        }
    }

    // =========================================================================
    // 10. Cross-Domain Evidence Rejected Fail-Closed (Section 28)
    // =========================================================================

    @Test
    fun crossDomainEvidence_isRejectedFailClosed() {
        runBlocking {
            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-DOMAIN-1",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, circuitId.toString(), CableWhatIfOverridesDto(overrideCableSize = "240"))
                )
            )
            val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-DOMAIN-1", whatIfEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // Mutate evidence copy to represent a transformer tool (domain TRANSFORMER)
            val transformerEvidence = scenarioEvidence.copy(
                evidenceId = "EV-TRANSFORMER",
                toolName = "calculate_transformer_sizing"
            )
            assertEquals("TRANSFORMER", transformerEvidence.domain)

            val prepResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "ADOPT_TRANSFORMER_EVIDENCE",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext(),
                decisionId = "DEC-TRANSFORMER-DOMAIN",
                evidence = listOf(transformerEvidence)
            )

            assertTrue(prepResult is CableProposalPreparationCoordinator.Result.SafeError)
            val error = prepResult as CableProposalPreparationCoordinator.Result.SafeError
            assertTrue(error.message.contains("Evidence domain is not compatible with cable proposal"))
        }
    }

    // =========================================================================
    // 11. Current vs Proposed Values Strictly Preserved and Distinct (Section 9, 43)
    // =========================================================================

    @Test
    fun currentVsProposedValues_areStrictlyPreservedAndDistinct() {
        runBlocking {
            // Seed Circuit 2 with current persisted override 300 mm²
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 2,
                    projectId = projectId,
                    circuitName = "Feeder-2-Current300",
                    transformerId = "TX-G9",
                    loadKVA = 200.0,
                    length_m = 50.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.03,
                    cableType = "Cu/XLPE/PVC - 4C",
                    installation = "D1",
                    overrideCableSize = "300" // Persisted: 300 mm²
                )
            )

            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-CIRCUIT2-WHATIF",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, "2", CableWhatIfOverridesDto(overrideCableSize = "240"))
                )
            )
            val evidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-CIRCUIT2-WHATIF", whatIfEnvelope),
                entityType = "CIRCUIT",
                entityId = "2"
            )

            // 1. Propose 240 mm² when current is 300 mm²: Must preserve current=300, proposed=240
            val prepResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "PROPOSE_240",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext().copy(activeEntityId = "2", activeEntityName = "Feeder-2-Current300"),
                decisionId = "DEC-DIFF-VALUES",
                evidence = listOf(evidence)
            )
            assertTrue(prepResult is CableProposalPreparationCoordinator.Result.Ready)
            val ready = prepResult as CableProposalPreparationCoordinator.Result.Ready
            val change = ready.review.fieldChanges.first { it.fieldKey == "overrideCableSize" }
            assertEquals("300 mm²", change.currentValue?.formattedText)
            assertEquals("240 mm²", change.proposedValue?.formattedText)

            // 2. Attempt to propose 300 mm² when current is ALREADY 300 mm²: Must fail closed!
            val whatIf300Envelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-CIRCUIT2-WHATIF300",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, "2", CableWhatIfOverridesDto(overrideCableSize = "300"))
                )
            )
            val evidence300 = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-CIRCUIT2-WHATIF300", whatIf300Envelope),
                entityType = "CIRCUIT",
                entityId = "2"
            )
            val prepSameResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "PROPOSE_SAME_300",
                    candidateParameters = mapOf("overrideCableSize" to "300")
                ),
                projectContext = sampleProjectContext().copy(activeEntityId = "2", activeEntityName = "Feeder-2-Current300"),
                decisionId = "DEC-SAME-VALUE",
                evidence = listOf(evidence300)
            )
            assertTrue("Proposal matching current persisted value must be rejected as no-op", prepSameResult is CableProposalPreparationCoordinator.Result.SafeError)
            val sameError = prepSameResult as CableProposalPreparationCoordinator.Result.SafeError
            assertTrue(sameError.message.contains("matches the current persisted value"))
        }
    }

    // =========================================================================
    // 12. Ambiguous Candidate Selection Fails Closed (Section 12)
    // =========================================================================

    @Test
    fun ambiguousCandidateSelection_returnsSafeErrorWithoutGuessing() {
        runBlocking {
            // Comparison evaluated both 185 mm² and 240 mm², both pass
            val compReq = CompareCableCandidatesRequest(
                projectId = projectId,
                circuitId = circuitId.toString(),
                candidates = listOf(
                    CableCandidateSpecDto(sizeMm2 = 185.0),
                    CableCandidateSpecDto(sizeMm2 = 240.0)
                )
            )
            val compEnvelope = compareTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-COMPARE-AMBIG",
                    projectId = projectId,
                    toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
                    parameters = compReq
                )
            )
            val compEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.COMPARE_CABLE_CANDIDATES, "REQ-COMPARE-AMBIG", compEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            // User says "اعتمد الاختيار ده" without specifying which candidate
            val action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "ADOPT_UNSPECIFIED",
                candidateParameters = emptyMap() // No size specified!
            )
            val prepResult = coordinator.prepare(
                action = action,
                projectContext = sampleProjectContext(),
                decisionId = "DEC-AMBIGUOUS",
                evidence = listOf(compEvidence)
            )

            assertTrue("Ambiguous selection with multiple candidates must fail closed", prepResult is CableProposalPreparationCoordinator.Result.SafeError)
            val error = prepResult as CableProposalPreparationCoordinator.Result.SafeError
            assertTrue(error.message.contains("AMBIGUOUS_SELECTION"))
        }
    }

    // =========================================================================
    // 13. Natural Language "Approval" Does Not Mutate (Section 31)
    // =========================================================================

    @Test
    fun naturalLanguageApproval_doesNotMutateDatabase() {
        runBlocking {
            val naturalPhrases = listOf("اعتمد", "yes", "apply", "نفذ", "ok")

            for (phrase in naturalPhrases) {
                val resolved = ContextResolver.resolve(
                    userUtterance = phrase,
                    projectContext = sampleProjectContext(),
                    priorState = null
                )
                // Even if intent is resolved as CREATE_PROPOSAL or other, database MUST remain unchanged
                val circuitInDb = database.circuitDao().getCircuitById(circuitId)!!
                val revInDb = database.projectDao().getRevision(projectId)!!
                val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

                assertEquals("Room circuit overrideCableSize must remain 70 after '$phrase'", "70", circuitInDb.overrideCableSize)
                assertEquals("Project revision must not advance after '$phrase'", baselineRevision, revInDb)
                assertTrue("Receipt list must remain empty after '$phrase'", receipts.isEmpty())
            }
        }
    }

    // =========================================================================
    // 14. Zero Mutation Proof Across All Operations (Section 44, 51)
    // =========================================================================

    @Test
    fun zeroMutationProof_acrossAllProposalOperations() {
        runBlocking {
            // Assert state before any check
            val circuitBefore = database.circuitDao().getCircuitById(circuitId)!!
            val revBefore = database.projectDao().getRevision(projectId)!!

            assertEquals("70", circuitBefore.overrideCableSize)
            assertEquals(baselineRevision, revBefore)
            assertTrue(database.mutationExecutionReceiptDao().getReceiptsForProject(projectId).isEmpty())

            // Run full proposal preparation
            val whatIfEnvelope = whatIfTool.execute(
                ToolRequestEnvelope(
                    requestId = "REQ-PROOF-1",
                    projectId = projectId,
                    toolName = CableToolNames.RUN_WHAT_IF,
                    parameters = RunCableWhatIfRequest(projectId, circuitId.toString(), CableWhatIfOverridesDto(overrideCableSize = "240"))
                )
            )
            val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
                outcome = com.example.ai.tools.ToolExecutionOutcome(CableToolNames.RUN_WHAT_IF, "REQ-PROOF-1", whatIfEnvelope),
                entityType = "CIRCUIT",
                entityId = circuitId.toString()
            )

            val prepResult = coordinator.prepare(
                action = ConversationAction(
                    actionType = ConversationActionType.CREATE_PROPOSAL,
                    reasonCode = "ADOPT_240",
                    candidateParameters = mapOf("overrideCableSize" to "240")
                ),
                projectContext = sampleProjectContext(),
                decisionId = "DEC-PROOF-1",
                evidence = listOf(scenarioEvidence)
            )
            assertTrue(prepResult is CableProposalPreparationCoordinator.Result.Ready)

            // Assert state after proposal preparation
            val circuitAfter = database.circuitDao().getCircuitById(circuitId)!!
            val revAfter = database.projectDao().getRevision(projectId)!!
            val receiptsAfter = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

            assertEquals("Room circuit overrideCableSize must remain unchanged (70)", "70", circuitAfter.overrideCableSize)
            assertEquals("Project revision must remain unchanged (3)", baselineRevision, revAfter)
            assertTrue("No mutation execution receipt must exist in Gate 9", receiptsAfter.isEmpty())
        }
    }
}
