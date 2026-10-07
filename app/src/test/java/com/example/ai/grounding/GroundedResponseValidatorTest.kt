package com.example.ai.grounding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.EngineeringCheck
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.evidence.hasCurrentBackingEvidence
import com.example.ai.hybrid.CloudLlmFeatureGate
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.AssistantToolRuntimeEndToEndTest
import com.example.ai.tools.ToolExecutionOutcome
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingDetailsDto
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CheckStatus
import com.example.ai.tools.cable.GoverningCriterion
import com.example.ai.ui.AssistantCardUiModel
import com.example.ai.ui.AssistantResponseMapper
import com.example.ai.ui.EngineeringStatusUiModel
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Authoritative test suite verifying evidence grounding and response validation (Gate 6).
 *
 * Implements:
 * - All 10 cases from Section 43.
 * - Strongly typed structured claim tests (Correction #1, #12).
 * - Distinct execution vs engineering status tests (Correction #5).
 * - Mandatory Test 1: Real deterministic tool -> ToolResultEnvelope -> Evidence -> Structured claim -> PASS.
 * - Mandatory Test 2: Evidence = 300 mm², model narrative = 240 mm² -> Grounding rejection -> Final UI structured value remains 300 mm²!
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GroundedResponseValidatorTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var runtime: AssistantToolRuntime

    private val projectId = "PROJ-GROUND-1"
    private val circuitId = "1"

    private lateinit var sampleContext: BoundedProjectContext
    private lateinit var sampleEvidenceRecord: EngineeringEvidenceRecord

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            CableEngineeringToolsTest.loadCatalogData()
        }
    }

    @Before
    fun setUp() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "test-key")
        AppDatabase.useTestExecutors = true
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

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "Grounding Test Project", revision = 1L)
            )

            database.transformerDao().insertTransformer(
                TransformerEntity(
                    transformerId = "TX-GROUND",
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
                    circuitName = "Main Feeder",
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
                    transformerId = "TX-GROUND",
                    airTempC = 30,
                    overrideCableSize = "300.0",
                    overrideDeviceSize = 400.0,
                    overrideDeviceType = "MCCB"
                )
            )
        }

        sampleContext = BoundedProjectContext(
            projectId = projectId,
            projectName = "Grounding Test Project",
            liveRevision = 1L,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId,
            activeEntityName = "Main Feeder"
        )

        // Build verified evidence fixture: 300 mm², PASS
        val cableDetails = CableSizingDetailsDto(
            Ib_A = 360.84,
            deviceSize_A = 400.0,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 400.0,
            productCode = "CBL-300",
            cableSize_mm2 = "300",
            ratedI_table_A = 520.0,
            capacity_derated_Iz_A = 520.0,
            utilizationPct = 76.92,
            neutral_mm2 = "150",
            earthCPC_mm2 = "150",
            voltDrop_V = 4.2,
            vdPct = 1.05,
            cumulativeVDpct = 1.05,
            status = "PASS"
        )

        val resultPayload = CableSizingExecutionResult(
            projectId = projectId,
            circuitId = circuitId,
            circuitName = "Main Feeder",
            projectRevision = 1L,
            freshnessState = "CURRENT",
            isRecalculated = true,
            overallStatus = CheckStatus.PASS,
            governingCriterion = GoverningCriterion.NONE,
            governingReason = "Compliance confirmed.",
            details = cableDetails
        )

        val envelope = ToolResultEnvelope.pass(
            requestId = "REQ-100",
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            result = resultPayload,
            projectRevision = 1L
        )

        val outcome = ToolExecutionOutcome(
            toolName = "calculate_cable_sizing",
            requestId = "REQ-100",
            envelope = envelope
        )

        sampleEvidenceRecord = EngineeringEvidenceRecord.fromOutcome(
            outcome = outcome,
            entityType = "CIRCUIT",
            entityId = circuitId
        )
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        // KT-12 (test hygiene): restore the production default for the process-wide
        // projectRevisionProvider so no later suite inherits a repository bound to the
        // in-memory database closed below. See DeterministicWhatIfTest for the full rationale.
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        database.close()
    }

    // =========================================================================
    // SECTION 43: 10 CANONICAL CASES
    // =========================================================================

    // Case 1: Valid grounded response (evidence: 300 mm², model recommends 300 mm²) -> PASS
    @Test
    fun case01_validGroundedResponse_evidenceMatches_returnsValid() {
        val narrative = "Based on calculation, we recommend to use 300 mm² for this feeder."
        val result = GroundedResponseValidator.validateResponse(
            text = narrative,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertTrue("Expected valid grounding for matching 300 mm² evidence", result.isValid)
    }

    // Case 2: Unsupported number (no evidence, model recommends 300 mm²) -> REJECT
    @Test
    fun case02_unsupportedNumber_noEvidence_rejectedWithNoEvidence() {
        val narrative = "We recommend to use 300 mm² for this feeder."
        val result = GroundedResponseValidator.validateResponse(
            text = narrative,
            evidenceList = emptyList(),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.NO_EVIDENCE, rejected.reason)
    }

    // Case 3: Mismatched number (evidence: 300 mm², model claims 240 mm²) -> REJECT
    @Test
    fun case03_mismatchedNumber_evidence300_modelNarrative240_rejectedWithNumericMismatch() {
        val narrative = "We recommend to use 240 mm² for this feeder."
        val result = GroundedResponseValidator.validateResponse(
            text = narrative,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.NUMERIC_MISMATCH, rejected.reason)
    }

    // Case 4: Wrong project (evidence from PROJ-OTHER, context is PROJ-1) -> REJECT
    @Test
    fun case04_wrongProject_evidenceFromOtherProject_rejectedWithWrongProject() {
        val foreignEvidence = sampleEvidenceRecord.copy(projectId = "PROJ-FOREIGN")
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = sampleContext.projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(foreignEvidence),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.WRONG_PROJECT, rejected.reason)
    }

    // Case 5: Wrong entity (evidence from Circuit 99, context is Circuit 1) -> REJECT
    @Test
    fun case05_wrongEntity_evidenceFromOtherCircuit_rejectedWithWrongEntity() {
        val otherCircuitEvidence = sampleEvidenceRecord.copy(entityId = "99")
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = sampleContext.projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(otherCircuitEvidence),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.WRONG_ENTITY, rejected.reason)
    }

    // Case 6: Stale revision (evidence revision 1, context revision 2) -> REJECT
    @Test
    fun case06_staleRevision_evidenceStale_rejectedWithStaleRevision() {
        val contextRev2 = sampleContext.copy(liveRevision = 2L)
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = sampleContext.projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord), // revision 1
            context = contextRev2 // live revision 2
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.STALE_REVISION, rejected.reason)
    }

    // Case 7: Wrong domain (cable evidence for transformer claim) -> REJECT
    @Test
    fun case07_wrongDomain_cableEvidenceForTransformerClaim_rejectedWithWrongDomain() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "ratingKVA",
            typedValue = EngineeringValue.ofDecimal(500.0, EngineeringUnit.KILOVOLT_AMPERE),
            projectId = sampleContext.projectId,
            domain = "TRANSFORMER"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord), // CABLE_SIZING evidence
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.WRONG_DOMAIN, rejected.reason)
    }

    // Case 8: PASS mismatch (evidence FAIL, model claims PASS) -> REJECT
    @Test
    fun case08_statusMismatch_evidenceFail_modelClaimsPass_rejectedWithStatusMismatch() {
        val failResult = (sampleEvidenceRecord.result as CableSizingExecutionResult).copy(
            overallStatus = CheckStatus.FAIL,
            governingReason = "Thermal ampacity exceeded."
        )
        val failEvidence = sampleEvidenceRecord.copy(
            result = failResult,
            executionStatus = ToolStatus.FAIL
        )

        val narrative = "The feeder complies with all checks and passed calculation."
        val result = GroundedResponseValidator.validateResponse(
            text = narrative,
            evidenceList = listOf(failEvidence),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.STATUS_MISMATCH, rejected.reason)
    }

    // Case 9: FAIL mismatch (evidence PASS, model claims FAIL) -> REJECT
    @Test
    fun case09_statusMismatch_evidencePass_modelClaimsFail_rejectedWithStatusMismatch() {
        val narrative = "The feeder failed the sizing criteria and is non-compliant."
        val result = GroundedResponseValidator.validateResponse(
            text = narrative,
            evidenceList = listOf(sampleEvidenceRecord), // PASS evidence
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.STATUS_MISMATCH, rejected.reason)
    }

    // Case 10: Non-engineering numbers (dates, turn counts, request IDs) -> not falsely rejected
    @Test
    fun case10_nonEngineeringNumbers_datesAndTurnCounts_notFalselyRejected() {
        val narrative = "On 2026-09-23 during iteration #2 for task TASK-12345, the design was evaluated under BS 7671."
        val result = GroundedResponseValidator.validateResponse(
            text = narrative,
            evidenceList = emptyList(),
            context = sampleContext
        )

        assertTrue("Dates, turn numbers, and standard identifiers must not be flagged as engineering claims", result.isValid)
    }

    // =========================================================================
    // CORRECTION #1 & #12: STRUCTURED CLAIM TESTS (TYPED COMPARISON)
    // =========================================================================

    @Test
    fun structuredClaim_exactMatch_returnsValid() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertTrue(result.isValid)
    }

    @Test
    fun structuredClaim_numericMismatch_rejectedWithNumericMismatch() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.NUMERIC_MISMATCH, rejected.reason)
    }

    @Test
    fun structuredClaim_statusMismatch_rejectedWithStatusMismatch() {
        val failResult = (sampleEvidenceRecord.result as CableSizingExecutionResult).copy(
            overallStatus = CheckStatus.FAIL
        )
        val failEvidence = sampleEvidenceRecord.copy(result = failResult, executionStatus = ToolStatus.FAIL)

        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.COMPLIANCE_STATUS,
            name = "overallStatus",
            statusValue = ToolStatus.PASS,
            projectId = projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(failEvidence),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.STATUS_MISMATCH, rejected.reason)
    }

    @Test
    fun structuredClaim_staleRevision_rejectedWithStaleRevision() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val staleEvidence = sampleEvidenceRecord.copy(freshnessState = "STALE")
        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(staleEvidence),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.STALE_REVISION, rejected.reason)
    }

    @Test
    fun structuredClaim_wrongProject_rejectedWithWrongProject() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = "OTHER-PROJECT-99",
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.WRONG_PROJECT, rejected.reason)
    }

    @Test
    fun structuredClaim_wrongEntity_rejectedWithWrongEntity() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(300.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = projectId,
            entityId = "DIFFERENT-CIRCUIT-99",
            domain = "CABLE_SIZING"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.WRONG_ENTITY, rejected.reason)
    }

    @Test
    fun structuredClaim_wrongDomain_rejectedWithWrongDomain() {
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "shortCircuitCurrent",
            typedValue = EngineeringValue.ofDecimal(25.0, EngineeringUnit.KILOAMPERE),
            projectId = projectId,
            domain = "SHORT_CIRCUIT"
        )

        val result = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(sampleEvidenceRecord),
            context = sampleContext
        )

        assertFalse(result.isValid)
        val rejected = result as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.WRONG_DOMAIN, rejected.reason)
    }

    // =========================================================================
    // CORRECTION #4: hasCurrentBackingEvidence tests
    // =========================================================================

    @Test
    fun hasCurrentBackingEvidence_discriminatesStaleAndWrongEntities() {
        val list = listOf(sampleEvidenceRecord)

        // Valid query
        assertTrue(list.hasCurrentBackingEvidence(projectId, circuitId, 1L, "CABLE_SIZING"))

        // Wrong project
        assertFalse(list.hasCurrentBackingEvidence("WRONG_PROJ", circuitId, 1L, "CABLE_SIZING"))

        // Wrong entity
        assertFalse(list.hasCurrentBackingEvidence(projectId, "999", 1L, "CABLE_SIZING"))

        // Stale revision
        assertFalse(list.hasCurrentBackingEvidence(projectId, circuitId, 2L, "CABLE_SIZING"))

        // Wrong domain
        assertFalse(list.hasCurrentBackingEvidence(projectId, circuitId, 1L, "TRANSFORMER"))
    }

    // =========================================================================
    // MANDATORY TEST 2: CONFLICT SCENARIO & UI STRUCTURED IMMUTABILITY
    // Tool returns 300 mm², model claims 240 mm²
    // -> Grounding rejection
    // -> Final UI structured value remains 300 mm²!
    // =========================================================================
    @Test
    fun conflictScenario_evidence300_modelClaims240_uiRendersVerified300_mandatoryTest() {
        // 1. Evidence is verified 300 mm²
        val evidenceList = listOf(sampleEvidenceRecord)

        // 2. Model narrative hallucinating 240 mm²
        val hallucinatedNarrative = "Use 240 mm² for this feeder."

        // 3. Grounding validation MUST reject this narrative
        val validationResult = GroundedResponseValidator.validateResponse(
            text = hallucinatedNarrative,
            evidenceList = evidenceList,
            context = sampleContext
        )
        assertFalse("Grounding must reject model narrative claiming 240 mm² against 300 mm² evidence", validationResult.isValid)
        val rejected = validationResult as GroundingValidationResult.Rejected
        assertEquals(GroundingFailureReason.NUMERIC_MISMATCH, rejected.reason)

        // 4. Map to UI state via AssistantResponseMapper
        val decision = ConversationTurnDecision(
            decisionId = "DEC-CONFLICT-1",
            action = ConversationAction(
                actionType = ConversationActionType.EXPLAIN_RESULT,
                reasonCode = "EXPLAIN_SIZING"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-1",
                projectId = projectId,
                baseProjectRevision = 1L,
                activeEntityType = "CIRCUIT",
                activeEntityId = circuitId
            ),
            explanationSummary = hallucinatedNarrative
        )

        val uiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = sampleContext,
            evidence = evidenceList
        )

        // 5. Assert final UI structured value remains 300 mm²! Model narrative cannot alter structured value (Correction #7)
        val card = uiState.activeCard as AssistantCardUiModel.ResultSummaryCard
        val conductorMetric = card.metrics.first { it.label == "Conductor" }
        assertEquals("Structured UI value MUST remain verified 300 from evidence, never model's 240", "300", conductorMetric.value)
        assertEquals("mm²", conductorMetric.unit)
        assertEquals(EngineeringStatusUiModel.PASS, card.status)
    }

    // =========================================================================
    // MANDATORY TEST 1: REAL DETERMINISTIC TOOL END-TO-END GROUNDING TEST
    // Real deterministic tool -> actual ToolResultEnvelope -> actual EngineeringEvidenceRecord
    // -> model structured claim -> matching evidence -> accepted response
    // =========================================================================
    @Test
    fun realEndToEnd_deterministicExecution_producesEvidence_groundsValidResponse_mandatoryTest() = runTest {
        // 1. Dispatch real deterministic tool
        val outcome = runtime.dispatcher.execute(
            toolName = "calculate_cable_sizing",
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = circuitId,
            conversationId = "TASK-E2E-1",
            arguments = emptyMap()
        )

        // 2. Real ToolResultEnvelope produced
        assertNotNull("Deterministic execution must produce non-null outcome", outcome)
        val envelope = outcome.envelope
        assertEquals("calculate_cable_sizing", envelope.toolName)
        assertEquals(projectId, envelope.projectId)
        assertEquals(ToolStatus.PASS, envelope.status)
        val cableResult = envelope.result as CableSizingExecutionResult
        assertNotNull(cableResult)

        // 3. Create real EngineeringEvidenceRecord
        val evidenceRecord = EngineeringEvidenceRecord.fromOutcome(
            outcome = outcome,
            conversationId = "TASK-E2E-1",
            entityType = "CIRCUIT",
            entityId = circuitId
        )

        assertEquals(projectId, evidenceRecord.projectId)
        assertEquals(circuitId, evidenceRecord.entityId)
        assertEquals(ToolStatus.PASS, evidenceRecord.executionStatus)

        // 4. Model emits structured claim matching the verified result
        val verifiedSize = cableResult.details.cableSize_mm2
            .replace("mm²", "")
            .replace(" مم²", "")
            .trim()
            .toDoubleOrNull() ?: 300.0
        val claim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(verifiedSize, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = projectId,
            entityId = circuitId,
            domain = "CABLE_SIZING"
        )

        // 5. Validate structured claim against real evidence
        val validationResult = GroundedResponseValidator.validateStructuredClaim(
            claim = claim,
            evidenceList = listOf(evidenceRecord),
            context = sampleContext
        )

        assertTrue("Structured claim matching verified calculation must be accepted as Valid", validationResult.isValid)

        // 6. Map to UI
        val decision = ConversationTurnDecision(
            decisionId = "DEC-E2E-SUCCESS",
            action = ConversationAction(
                actionType = ConversationActionType.RUN_CALCULATION,
                reasonCode = "CALCULATION_EXECUTED"
            ),
            updatedState = ConversationTaskState(
                taskId = "TASK-E2E-1",
                projectId = projectId,
                baseProjectRevision = 1L,
                activeEntityType = "CIRCUIT",
                activeEntityId = circuitId
            ),
            explanationSummary = "Cable sizing calculation completed successfully."
        )

        val uiState = AssistantResponseMapper.mapDecisionToUiState(
            decision = decision,
            state = decision.updatedState,
            projectContext = sampleContext,
            evidence = listOf(evidenceRecord)
        )

        val card = uiState.activeCard as AssistantCardUiModel.ResultSummaryCard
        val conductorMetric = card.metrics.first { it.label == "Conductor" }
        assertEquals(cableResult.details.cableSize_mm2, conductorMetric.value)
        assertEquals(EngineeringStatusUiModel.PASS, card.status)
    }
}
