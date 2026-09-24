package com.example.ai.context

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.*
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.hybrid.CloudDegradationReason
import com.example.ai.hybrid.CloudFunctionDeclaration
import com.example.ai.hybrid.CloudLlmConversationEngine
import com.example.ai.hybrid.CloudLlmFeatureGate
import com.example.ai.hybrid.CloudLlmRawResponse
import com.example.ai.hybrid.GeminiClientSeam
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContextMemoryIntegrationTest {

    private val projectAContext = BoundedProjectContext(
        projectId = "PROJ-A",
        projectName = "Industrial Complex Alpha",
        liveRevision = 10L,
        activeScreen = "CircuitView",
        activeEntityType = "CIRCUIT",
        activeEntityId = "5",
        activeEntityName = "Motor Feeder 5",
        knownEntityInputs = mapOf(
            "voltage" to "400.0",
            "lengthM" to "120.0",
            "installation" to "Perforated Cable Tray"
        )
    )

    @Before
    fun setUp() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "test-api-key")
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
    }

    // Mandatory Scenario 1: Multi-Turn Continuity & What-If Isolation
    @Test
    fun scenario1_multiTurnContinuity_preservesCircuit_appliesWhatIfWithoutMutation() = runTest {
        var seamCalledWithPrompt = ""
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                seamCalledWithPrompt = systemPrompt
                return CloudLlmRawResponse(
                    text = "Checking 240 mm² candidate...",
                    toolCallName = "evaluate_cable_candidate",
                    toolCallArguments = mapOf("candidateSize" to JsonPrimitive("240.0"))
                )
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        // Turn 1 initial state representing circuit 5
        val turn1State = ConversationTaskState(
            taskId = "TASK-001",
            projectId = "PROJ-A",
            baseProjectRevision = 10L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "5",
            activeEntityName = "Motor Feeder 5",
            activeDomain = CalculationDomain.CABLE_SIZING
        )

        // Turn 2 follow-up
        val turn2Input = ConversationTurnInput(
            userUtterance = "طيب جرب 240",
            projectContext = projectAContext,
            priorState = turn1State,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Motor Feeder 5")),
            priorEvidence = emptyList()
        )

        val result = engine.evaluateTurn(turn2Input)

        assertNull(result.cloudDegradationReason)
        assertEquals(ConversationActionType.EVALUATE_CANDIDATE, result.decision.action.actionType)
        // Entity remains Circuit 5
        assertEquals("5", result.decision.updatedState.activeEntityId)
        // Scenario is what-if, not persisted baseline mutation
        assertTrue("What-if branch must be true", result.decision.updatedState.isWhatIfBranch)
        val typedOverride = result.decision.updatedState.typedWhatIfOverrides["targetSize"]
        assertNotNull(typedOverride)
        assertEquals(240.0, typedOverride?.decimalValue ?: 0.0, 0.001)

        // System prompt contains Layer E with what-if summary
        assertTrue(seamCalledWithPrompt.contains("isWhatIfActive: true"))
    }

    // Mandatory Scenario 2: Revision Advance (History Preserved, Marked Stale)
    @Test
    fun scenario2_revisionAdvance_preservesEvidence_marksStaleInPrompt() = runTest {
        var observedSystemPrompt = ""
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                observedSystemPrompt = systemPrompt
                return CloudLlmRawResponse(
                    text = "Project revision changed from 10 to 11. Stale evidence detected."
                )
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        val details = com.example.ai.tools.cable.CableSizingDetailsDto(
            Ib_A = 410.0,
            deviceSize_A = 500.0,
            deviceType = "MCCB",
            cTotalDerating = 0.85,
            iRequired_perCable_A = 482.0,
            productCode = "XLPE-CU-300",
            cableSize_mm2 = "300",
            ratedI_table_A = 610.0,
            capacity_derated_Iz_A = 520.0,
            utilizationPct = 78.8,
            neutral_mm2 = "150",
            earthCPC_mm2 = "150",
            voltDrop_V = 8.4,
            vdPct = 2.1,
            cumulativeVDpct = 2.1,
            status = "PASS"
        )
        val sizingResult = com.example.ai.tools.cable.CableSizingExecutionResult(
            projectId = "PROJ-A",
            circuitId = "5",
            circuitName = "Motor Feeder 5",
            projectRevision = 10L,
            freshnessState = "CURRENT",
            isRecalculated = false,
            overallStatus = com.example.ai.tools.cable.CheckStatus.PASS,
            governingCriterion = com.example.ai.tools.cable.GoverningCriterion.AMPACITY,
            governingReason = "Iz >= In",
            details = details
        )
        val rev10Evidence = EngineeringEvidenceRecord(
            evidenceId = "EVID-REV10-001",
            requestId = "REQ-001",
            toolName = "calculate_cable_sizing",
            projectId = "PROJ-A",
            entityType = "CIRCUIT",
            entityId = "5",
            projectRevision = 10L,
            executionStatus = ToolStatus.PASS,
            result = sizingResult,
            freshnessState = "CURRENT"
        )

        // Revision advances to 11
        val rev11Context = projectAContext.copy(liveRevision = 11L)

        val input = ConversationTurnInput(
            userUtterance = "هل النتيجة ما زالت صالحة؟",
            projectContext = rev11Context,
            priorState = null,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Motor Feeder 5")),
            priorEvidence = listOf(rev10Evidence)
        )

        val result = engine.evaluateTurn(input)

        // Verify evidence is preserved
        assertEquals(1, result.evidence.size)
        assertEquals("EVID-REV10-001", result.evidence[0].evidenceId)
        // Verify prompt Layer E marks it STALE_REVISION
        assertTrue(observedSystemPrompt.contains("Freshness: STALE_REVISION"))
    }

    // Mandatory Scenario 3: Project Isolation
    @Test
    fun scenario3_projectIsolation_wipesPriorProjectContext() = runTest {
        var observedSystemPrompt = ""
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                observedSystemPrompt = systemPrompt
                return CloudLlmRawResponse(text = "Welcome to Project B.")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        val priorStateProjectA = ConversationTaskState(
            taskId = "TASK-PROJ-A",
            projectId = "PROJ-A",
            baseProjectRevision = 10L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "5",
            activeEntityName = "Project A Feeder",
            isWhatIfBranch = true,
            typedWhatIfOverrides = mapOf("targetSize" to EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER)),
            evidenceReferences = listOf("EVID-A-1")
        )

        val projectBContext = BoundedProjectContext(
            projectId = "PROJ-B",
            projectName = "Project B Factory",
            liveRevision = 1L,
            activeScreen = "Overview"
        )

        val input = ConversationTurnInput(
            userUtterance = "ما هي حالة المشروع؟",
            projectContext = projectBContext,
            priorState = priorStateProjectA,
            availableEntities = emptyList(),
            priorEvidence = emptyList()
        )

        val result = engine.evaluateTurn(input)

        assertEquals("PROJ-B", result.decision.updatedState.projectId)
        assertNull("Project A entity must be cleared", result.decision.updatedState.activeEntityId)
        assertTrue("Project A overrides must be cleared", result.decision.updatedState.typedWhatIfOverrides.isEmpty())
        assertFalse(observedSystemPrompt.contains("Project A Feeder"))
    }

    // Mandatory Scenario 4: Ambiguous Entity Detection
    @Test
    fun scenario4_ambiguousEntity_failsClosedWithoutGuessing() = runTest {
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                fail("Provider must not be invoked when entity reference is ambiguous")
                throw IllegalStateException("Unreachable")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        val availableEntities = listOf(
            EntityBinding("CIRCUIT", "C-01", "Feeder Branch 1"),
            EntityBinding("CIRCUIT", "C-02", "Feeder Branch 2")
        )

        val input = ConversationTurnInput(
            userUtterance = "راجع الدائرة الفرعية",
            projectContext = projectAContext.copy(activeEntityId = null),
            priorState = null,
            availableEntities = availableEntities,
            priorEvidence = emptyList()
        )

        val result = engine.evaluateTurn(input)

        assertEquals(ConversationActionType.ASK_BLOCKING_QUESTION, result.decision.action.actionType)
        assertEquals("AMBIGUOUS_ENTITY", result.decision.action.reasonCode)
        assertNotNull(result.decision.explanationSummary)
        assertTrue(result.decision.explanationSummary!!.contains("C-01") || result.decision.explanationSummary!!.contains("Feeder Branch 1"))
    }

    // Mandatory Scenario 5: Contradiction Detection Without Mutation
    @Test
    fun scenario5_contradictionDetection_identifiesConflictWithoutMutatingState() = runTest {
        var observedSystemPrompt = ""
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                observedSystemPrompt = systemPrompt
                return CloudLlmRawResponse(text = "Noted difference: project length is 120m, user specified 80m.")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        val input = ConversationTurnInput(
            userUtterance = "الكابل طوله 80 متر احسب عليه",
            projectContext = projectAContext, // base projectContext has lengthM = 120.0
            priorState = null,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Motor Feeder 5")),
            priorEvidence = emptyList()
        )

        val result = engine.evaluateTurn(input)

        val contradictions = result.decision.updatedState.detectedContradictions
        assertEquals(1, contradictions.size)
        assertEquals("lengthM", contradictions[0].fieldName)
        assertEquals("120.0", contradictions[0].projectValue)
        assertEquals("80.0", contradictions[0].userValue)

        assertTrue(observedSystemPrompt.contains("detectedContradictions:"))
        assertTrue(observedSystemPrompt.contains("lengthM: project=120.0 vs user=80.0"))
    }

    // Mandatory Scenario 6: Missing Inputs - No Defaults Fabricated
    @Test
    fun scenario6_missingInputs_neverFabricatesDefaults() = runTest {
        val blankContext = BoundedProjectContext(
            projectId = "PROJ-NEW",
            projectName = "New Project",
            liveRevision = 1L,
            activeScreen = "CircuitList",
            knownEntityInputs = emptyMap()
        )

        val resolved = ContextResolver.resolve(
            userUtterance = "احسب كابل",
            projectContext = blankContext,
            priorState = null,
            availableEntities = emptyList(),
            historicalEvidence = emptyList()
        )

        assertTrue("Voltage must be missing", resolved.missingBlockingSlots.contains("voltage"))
        assertNull("Must never assume 400V", resolved.resolvedSlots["voltage"])
        assertNull("Must never assume 230V", resolved.resolvedSlots["voltage"])
        assertNull("Must never assume 30m length", resolved.resolvedSlots["lengthM"])
    }

    // Mandatory Scenario 7: Evidence Follow-up
    @Test
    fun scenario7_evidenceFollowUp_groundedInHistoricalEvidence() = runTest {
        var observedSystemPrompt = ""
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                observedSystemPrompt = systemPrompt
                return CloudLlmRawResponse(
                    text = "Selected size 300 mm² was governed by voltage drop criterion (2.1%)."
                )
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        val details = com.example.ai.tools.cable.CableSizingDetailsDto(
            Ib_A = 410.0,
            deviceSize_A = 500.0,
            deviceType = "MCCB",
            cTotalDerating = 0.85,
            iRequired_perCable_A = 482.0,
            productCode = "XLPE-CU-300",
            cableSize_mm2 = "300",
            ratedI_table_A = 610.0,
            capacity_derated_Iz_A = 520.0,
            utilizationPct = 78.8,
            neutral_mm2 = "150",
            earthCPC_mm2 = "150",
            voltDrop_V = 8.4,
            vdPct = 2.1,
            cumulativeVDpct = 2.1,
            status = "PASS"
        )
        val sizingResult = com.example.ai.tools.cable.CableSizingExecutionResult(
            projectId = "PROJ-A",
            circuitId = "5",
            circuitName = "Motor Feeder 5",
            projectRevision = 10L,
            freshnessState = "CURRENT",
            isRecalculated = false,
            overallStatus = com.example.ai.tools.cable.CheckStatus.PASS,
            governingCriterion = com.example.ai.tools.cable.GoverningCriterion.VOLTAGE_DROP,
            governingReason = "Voltage Drop <= 2.5%",
            details = details
        )
        val priorEvidence = EngineeringEvidenceRecord(
            evidenceId = "EVID-001",
            requestId = "REQ-001",
            toolName = "calculate_cable_sizing",
            projectId = "PROJ-A",
            entityType = "CIRCUIT",
            entityId = "5",
            projectRevision = 10L,
            executionStatus = ToolStatus.PASS,
            result = sizingResult,
            freshnessState = "CURRENT"
        )

        val input = ConversationTurnInput(
            userUtterance = "ليه اخترته؟",
            projectContext = projectAContext,
            priorState = null,
            availableEntities = listOf(EntityBinding("CIRCUIT", "5", "Motor Feeder 5")),
            priorEvidence = listOf(priorEvidence)
        )

        val result = engine.evaluateTurn(input)

        assertNull(result.cloudDegradationReason)
        assertEquals(GroundingValidationResult.Valid, result.groundingResult)
        assertTrue("Prompt Layer E must contain evidence reference", observedSystemPrompt.contains("Ref: EVID-001"))
        assertTrue("Prompt Layer E must contain conductor info", observedSystemPrompt.contains("Conductor: 300"))
        assertTrue("Prompt Layer E must contain voltage drop info", observedSystemPrompt.contains("VD: 2.1%"))
    }

    // Mandatory Scenario 8: Domain Switch Isolation
    @Test
    fun scenario8_domainSwitchIsolation_cableParametersDoNotLeakToTransformer() = runTest {
        var observedSystemPrompt = ""
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                observedSystemPrompt = systemPrompt
                return CloudLlmRawResponse(text = "Calculating transformer capacity...")
            }
        }

        val engine = CloudLlmConversationEngine(clientSeam = mockSeam)

        val priorCableState = ConversationTaskState(
            taskId = "TASK-001",
            projectId = "PROJ-A",
            baseProjectRevision = 10L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "5",
            activeDomain = CalculationDomain.CABLE_SIZING,
            isWhatIfBranch = true,
            typedWhatIfOverrides = mapOf("targetSize" to EngineeringValue.ofDecimal(240.0, EngineeringUnit.SQUARE_MILLIMETER))
        )

        val input = ConversationTurnInput(
            userUtterance = "احسب قدرة المحول الرئيسي",
            projectContext = projectAContext.copy(
                activeEntityType = "TRANSFORMER",
                activeEntityId = "TX-01",
                activeEntityName = "Main Transformer 1"
            ),
            priorState = priorCableState,
            availableEntities = listOf(EntityBinding("TRANSFORMER", "TX-01", "Main Transformer 1")),
            priorEvidence = emptyList()
        )

        val result = engine.evaluateTurn(input)

        assertEquals(CalculationDomain.TRANSFORMER, result.decision.updatedState.activeDomain)
        assertFalse(
            "Cable targetSize must not leak into transformer domain overrides",
            result.decision.updatedState.typedWhatIfOverrides.containsKey("targetSize")
        )
    }
}
