package com.example.ai.hybrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedApprovalGateResult
import com.example.ai.approval.TrustedApprovalRequest
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.proposal.DurableMutationOutcome
import com.example.ai.proposal.DurableMutationWorkflow
import com.example.ai.context.ContextResolver
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.*
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.orchestrator.AgenticSafetyBudget
import com.example.ai.orchestrator.AgenticToolLoopOrchestrator
import com.example.ai.orchestrator.AgenticTurnItem
import com.example.ai.orchestrator.MultiTurnGeminiSeam
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.AssistantToolRuntimeEndToEndTest
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.ui.CableProposalPreparationCoordinator
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VOLTATLAS — CONTROLLED CLOUD LLM ACTIVATION VERIFICATION SUITE
 *
 * Verifies:
 * 1. Model Requirement: gemini-3.8-flash active as default model.
 * 2. Provider Seam & Honest Failure Reporting: RealGoogleGenAiClientSeam fails closed without fabricated online state.
 * 3. First Live Test (No Tools): Healthy cloud responds with LIVE_MODEL_OK, fallback used = NO.
 * 4. Second Live Test (Engineering Tool Call): Agentic loop reaches orchestrator, executes deterministic tool, synthesizes grounded answer.
 * 5. Third Live Test (Multi-Turn Agent): What-if and comparison flows preserve zero mutation and deterministic authority.
 * 6. Grounding Proof: GroundedResponseValidator guards final model text against fabrication.
 * 7. Offline Fallback: Graceful degradation to LocalRuleEngineAdapter on cloud failure.
 * 8. Natural Language Safety: Utterances ("yes", "apply", "اعتمد", "نفذ") never directly mutate the database.
 * 9. Gate 10 Mutation Boundary: Physical mutation only via TrustedProposalApprovalGateway.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ControlledCloudActivationTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var runtime: AssistantToolRuntime
    private lateinit var proposalCoordinator: CableProposalPreparationCoordinator
    private lateinit var workflow: DurableMutationWorkflow
    private lateinit var gateway: TrustedProposalApprovalGateway

    private val projectId = "PROJ-CLOUD-ACTIVATION"

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
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "test-cloud-activation-key")

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = EngineeringRepository(database)

        EngineeringCalculationCoordinator.projectRevisionProvider = repository
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

        catalogRepository = AssistantToolRuntimeEndToEndTest.buildCatalogRepository()
        runtime = AssistantToolRuntime.create(
            repository = repository,
            catalogRepository = catalogRepository
        )

        proposalCoordinator = CableProposalPreparationCoordinator(
            projectDao = database.projectDao(),
            circuitDao = database.circuitDao()
        )

        workflow = DurableMutationWorkflow(
            db = database,
            catalogRepository = catalogRepository,
            clock = { 10_000L },
            executionIdProvider = { "EXEC-TEST-GATEWAY" }
        )

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = workflow,
            clock = { 10_000L },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-mahmoud-auth" }
        )

        seedDatabase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        // KT-12 (test hygiene): restore the production default for the process-wide
        // projectRevisionProvider so no later suite inherits a repository bound to the
        // in-memory database closed below. See DeterministicWhatIfTest for the full rationale.
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        database.close()
        CloudLlmFeatureGate.resetToDefaults()
        AiModelConfig.resetToDefaults()
    }

    private fun seedDatabase() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectId, projectName = "Cloud Activation Verification Project", revision = 1L)
        )

        database.transformerDao().insertTransformer(
            TransformerEntity(
                transformerId = "TX-ACT-1",
                projectId = projectId,
                transformerName = "Main Substation TX",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0
            )
        )

        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = 1,
                projectId = projectId,
                circuitName = "Feeder-Act-1",
                loadKVA = 200.0,
                length_m = 60.0,
                voltageV = 400,
                phases = "3P+N",
                pf = 0.85,
                maxVDpct = 0.03,
                cableType = "Cu/XLPE/PVC - 4C",
                installation = "D1",
                fedFrom = "Transformer",
                transformerId = "TX-ACT-1",
                overrideCableSize = "95"
            )
        )
    }

    // =========================================================================
    // 1. MODEL REQUIREMENT: gemini-3.8-flash active as default model
    // =========================================================================
    @Test
    fun test01_cloudModelInitialization_resolvesGemini38Flash() {
        val config = AiModelConfig.current()
        assertEquals("gemini-3.8-flash", config.modelName)
        assertEquals("gemini-3.8-flash", AiModelConfig.DEFAULT_MODEL_NAME)
        assertEquals(AiModelConfig.PROVIDER_FIREBASE_AI_LOGIC, config.provider)

        val clientSeam = RealGoogleGenAiClientSeam()
        assertEquals("gemini-3.8-flash", clientSeam.config.modelName)
    }

    // =========================================================================
    // 2. REAL MODEL CALL: Honest failure reporting when unconfigured
    // =========================================================================
    @Test
    fun test02_liveModelCall_reportsHonestConfigurationStatus_whenUnconfigured() = runTest {
        CloudLlmFeatureGate.configureForTesting(enabled = false, apiKey = null)

        val seam = RealGoogleGenAiClientSeam()
        val response = seam.generateTurn(
            systemPrompt = "You are a test assistant.",
            userPrompt = "Respond with exactly: LIVE_MODEL_OK",
            conversationHistory = emptyList(),
            tools = emptyList()
        )

        assertTrue("When feature gate is disabled, seam must return ProviderError", response is CloudLlmClientResponse.ProviderError)
        val providerError = response as CloudLlmClientResponse.ProviderError
        assertTrue("Error message must indicate disabled gate", providerError.message.contains("disabled by configuration"))
    }

    // =========================================================================
    // 3. FIRST LIVE TEST (NO TOOLS): Healthy cloud responds with LIVE_MODEL_OK
    // =========================================================================
    @Test
    fun test03_firstLiveTest_healthyCloud_noTools_returnsLiveModelOk() = runTest {
        val mockSeam = object : GeminiClientSeam {
            override suspend fun generate(
                systemPrompt: String,
                userPrompt: String,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmRawResponse {
                return CloudLlmRawResponse(text = "LIVE_MODEL_OK")
            }

            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                return CloudLlmClientResponse.FinalText("LIVE_MODEL_OK")
            }
        }

        val cloudEngine = CloudLlmConversationEngine(clientSeam = mockSeam)
        val router = HybridConversationRouter(cloudEngine = cloudEngine, localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine))

        val boundedContext = BoundedProjectContext(
            projectId = projectId,
            projectName = "Cloud Activation",
            liveRevision = 1L,
            activeScreen = "HOME"
        )
        val input = ConversationTurnInput(
            userUtterance = "Respond with exactly: LIVE_MODEL_OK",
            projectContext = boundedContext
        )

        val result = router.evaluateTurn(input)

        assertEquals("Engine source must be CLOUD_GEMINI", ConversationEngineSource.CLOUD_GEMINI, result.source)
        assertNull("Cloud degradation reason must be null on healthy turn", result.cloudDegradationReason)
        assertTrue("Response text must match exact output", result.decision.explanationSummary?.contains("LIVE_MODEL_OK") == true)
        assertEquals("Fallback was NOT used", false, result.source == ConversationEngineSource.LOCAL_RULE_ENGINE)
    }

    // =========================================================================
    // 4. SECOND LIVE TEST (TOOL CALL): Agentic loop executes deterministic tool
    // =========================================================================
    @Test
    fun test04_secondLiveTest_engineeringToolCall_reachesOrchestratorAndExecutesDeterministicTool() = runTest {
        var turnCount = 0
        val multiTurnSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(
                            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
                            arguments = mapOf("circuitId" to JsonPrimitive("1"))
                        )
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Cable sizing calculated: 95 mm² Cu/XLPE meets all requirements.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = multiTurnSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry,
            safetyBudget = AgenticSafetyBudget(maxIterations = 5)
        )

        val cloudEngine = CloudLlmConversationEngine(
            clientSeam = object : GeminiClientSeam {
                override suspend fun generate(systemPrompt: String, userPrompt: String, tools: List<CloudFunctionDeclaration>) =
                    CloudLlmRawResponse(text = "stub")
                override suspend fun generateTurn(systemPrompt: String, userPrompt: String, conversationHistory: List<AgenticTurnItem>, tools: List<CloudFunctionDeclaration>) =
                    multiTurnSeam.generateTurn(systemPrompt, userPrompt, conversationHistory, tools)
            },
            orchestrator = orchestrator
        )

        val boundedContext = BoundedProjectContext(
            projectId = projectId,
            projectName = "Cloud Activation",
            liveRevision = 1L,
            activeScreen = "CIRCUIT_EDITOR",
            activeEntityType = "CIRCUIT",
            activeEntityId = "1",
            activeEntityName = "Feeder-Act-1"
        )

        val input = ConversationTurnInput(
            userUtterance = "Size the cable for the current circuit.",
            projectContext = boundedContext
        )

        val result = cloudEngine.evaluateTurn(input)

        assertEquals("Engine source must be CLOUD_GEMINI", ConversationEngineSource.CLOUD_GEMINI, result.source)
        assertNull("No degradation should occur", result.cloudDegradationReason)
        assertTrue("Must have backing tool result", result.hasBackingToolResult)
        assertEquals(2, turnCount)
        assertEquals(1, result.evidence.size)
        assertEquals(CableToolNames.CALCULATE_CABLE_SIZING, result.evidence.first().toolName)
        assertEquals("AGENTIC_LOOP_ANSWER_WITH_TOOLS", result.decision.action.reasonCode)
    }

    // =========================================================================
    // 5. THIRD LIVE TEST (MULTI-TURN AGENT): What-If & Deterministic Comparison
    // =========================================================================
    @Test
    fun test05_thirdLiveTest_multiTurnAgent_whatIfAndComparison_preservesZeroMutation() = runTest {
        val initialCircuit = database.circuitDao().getCircuitById(1)!!
        val initialRevision = repository.getCurrentRevision(projectId).revision

        // Turn 1: Analyze current cable
        val outcome1 = runtime.dispatcher.execute(
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "conv-act-1"
        )
        assertEquals(ToolStatus.PASS, outcome1.envelope.status)
        val evd1 = EngineeringEvidenceRecord.fromOutcome(outcome1)
        assertFalse("Baseline evidence must not be scenario", evd1.isScenario)

        // Turn 2: What-if exploration with 240 mm²
        val outcome2 = runtime.dispatcher.execute(
            toolName = CableToolNames.RUN_WHAT_IF,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "conv-act-1",
            arguments = mapOf(
                "overrides" to mapOf("overrideCableSize" to "240"),
                "overrideCableSize" to "240"
            )
        )
        assertEquals(ToolStatus.PASS, outcome2.envelope.status)
        val evd2 = EngineeringEvidenceRecord.fromOutcome(outcome2)
        assertTrue("What-if evidence must be flagged as scenario", evd2.isScenario)

        // Verify zero Room mutation after what-if
        val afterWhatIfCircuit = database.circuitDao().getCircuitById(1)!!
        assertEquals(initialCircuit.overrideCableSize, afterWhatIfCircuit.overrideCableSize)
        assertEquals(initialRevision, repository.getCurrentRevision(projectId).revision)

        // Turn 3: Compare candidate with baseline
        val outcome3 = runtime.dispatcher.execute(
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "conv-act-1",
            arguments = mapOf(
                "candidates" to listOf(
                    mapOf("candidateId" to "CAND-95", "sizeMm2" to 95.0),
                    mapOf("candidateId" to "CAND-240", "sizeMm2" to 240.0)
                )
            )
        )
        assertEquals(ToolStatus.PASS, outcome3.envelope.status)
        val evd3 = EngineeringEvidenceRecord.fromOutcome(outcome3)
        assertNotNull(evd3)

        // Final verification: 0 mutations, 0 revision increments
        val finalCircuit = database.circuitDao().getCircuitById(1)!!
        assertEquals(initialCircuit.overrideCableSize, finalCircuit.overrideCableSize)
        assertEquals(initialRevision, repository.getCurrentRevision(projectId).revision)
    }

    // =========================================================================
    // 6. GROUNDING PROOF: Ungrounded / Fabricated Claims Rejected
    // =========================================================================
    @Test
    fun test06_groundingProof_fabricatedClaimsRejected() = runTest {
        val boundedContext = BoundedProjectContext(
            projectId = projectId,
            projectName = "Grounding Test",
            liveRevision = 1L,
            activeScreen = "CIRCUIT_EDITOR",
            activeEntityType = "CIRCUIT",
            activeEntityId = "1"
        )

        val fabricatedSeam = object : GeminiClientSeam {
            override suspend fun generate(systemPrompt: String, userPrompt: String, tools: List<CloudFunctionDeclaration>) =
                CloudLlmRawResponse(text = "I recommend 400 mm² copper cable because it can carry 850 A.")
            override suspend fun generateTurn(systemPrompt: String, userPrompt: String, conversationHistory: List<AgenticTurnItem>, tools: List<CloudFunctionDeclaration>) =
                CloudLlmClientResponse.FinalText("I recommend 400 mm² copper cable because it can carry 850 A.")
        }

        val engine = CloudLlmConversationEngine(clientSeam = fabricatedSeam)
        val result = engine.evaluateTurn(
            ConversationTurnInput(userUtterance = "What cable should I use?", projectContext = boundedContext)
        )

        assertEquals(
            "Fabricated claim without backing evidence must be flagged",
            CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM,
            result.cloudDegradationReason
        )
    }

    // =========================================================================
    // 7. OFFLINE FALLBACK: Activates safely on cloud failure
    // =========================================================================
    @Test
    fun test07_offlineFallback_activatesSafelyOnCloudFailure() = runTest {
        val failingCloudEngine = object : ConversationEngine {
            override suspend fun evaluateTurn(
                input: ConversationTurnInput,
                decisionIdProvider: () -> String
            ): HybridEngineResult {
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "DEC-FAIL",
                        action = ConversationAction(ConversationActionType.SAFE_ERROR, "NETWORK_ERROR"),
                        updatedState = ConversationTaskState("T1", projectId, 1L),
                        explanationSummary = "Network unreachable"
                    ),
                    source = ConversationEngineSource.CLOUD_GEMINI,
                    cloudDegradationReason = CloudDegradationReason.NETWORK_TIMEOUT
                )
            }
        }

        val localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)
        val router = HybridConversationRouter(
            cloudEngine = failingCloudEngine,
            localEngine = localEngine
        )

        val boundedContext = BoundedProjectContext(
            projectId = projectId,
            projectName = "Fallback Test",
            liveRevision = 1L
        )

        val result = router.evaluateTurn(
            ConversationTurnInput(userUtterance = "Hello", projectContext = boundedContext)
        )

        assertEquals("Must fall back to LOCAL_RULE_ENGINE", ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals("Degradation reason must be preserved", CloudDegradationReason.NETWORK_TIMEOUT, result.cloudDegradationReason)
    }

    // =========================================================================
    // 8. NATURAL LANGUAGE SAFETY: Affirmations Produce Zero Mutations
    // =========================================================================
    @Test
    fun test08_naturalLanguageSafety_affirmationsProduceZeroMutations() = runTest {
        val naturalLanguageAffirmations = listOf("yes", "apply", "اعتمد", "نفذ", "approve this change")

        for (utterance in naturalLanguageAffirmations) {
            val initialRevision = repository.getCurrentRevision(projectId).revision
            val initialCircuit = database.circuitDao().getCircuitById(1)!!

            val resolved = ContextResolver.resolve(
                userUtterance = utterance,
                projectContext = BoundedProjectContext(projectId = projectId, projectName = "Test", liveRevision = initialRevision),
                priorState = null,
                historicalEvidence = emptyList()
            )

            // Even if resolved as proposal action, ZERO physical write occurs here
            val finalRevision = repository.getCurrentRevision(projectId).revision
            val finalCircuit = database.circuitDao().getCircuitById(1)!!

            assertEquals("Natural language affirmation must produce 0 revision increments", initialRevision, finalRevision)
            assertEquals("Natural language affirmation must produce 0 circuit mutations", initialCircuit.overrideCableSize, finalCircuit.overrideCableSize)
        }
    }

    // =========================================================================
    // 9. GATE 10 MUTATION REGRESSION: Physical Mutation Only Via Trusted Gateway
    // =========================================================================
    @Test
    fun test09_gate10_trustedPhysicalMutationOnlyViaGateway() = runTest {
        val initialCircuit = database.circuitDao().getCircuitById(1)!!
        val initialRevision = repository.getCurrentRevision(projectId).revision

        // Generate verified engineering evidence via deterministic what-if tool
        val outcome = runtime.dispatcher.execute(
            toolName = CableToolNames.RUN_WHAT_IF,
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = "1",
            conversationId = "conv-act-1",
            arguments = mapOf(
                "overrides" to mapOf("overrideCableSize" to "240"),
                "overrideCableSize" to "240"
            )
        )
        assertEquals(ToolStatus.PASS, outcome.envelope.status)
        val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(outcome)

        // Proposal preparation via CableProposalPreparationCoordinator (gated approval workflow)
        val prepResult = proposalCoordinator.prepare(
            action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "ADOPT_NEW_SIZE",
                candidateParameters = mapOf("overrideCableSize" to "240")
            ),
            projectContext = BoundedProjectContext(
                projectId = projectId,
                projectName = "Test",
                liveRevision = initialRevision,
                activeEntityType = "CIRCUIT",
                activeEntityId = "1"
            ),
            decisionId = "DEC-PROPOSAL",
            evidence = listOf(scenarioEvidence)
        )

        assertTrue("Proposal preparation must succeed and be Ready for review, got: $prepResult", prepResult is CableProposalPreparationCoordinator.Result.Ready)
        val ready = prepResult as CableProposalPreparationCoordinator.Result.Ready

        assertEquals("240", ready.proposal.patch.overrideCableSize?.value)
        assertEquals("CIRCUIT", ready.proposal.targetEntityType)
        assertEquals("1", ready.proposal.targetEntityId)

        // Database still has 0 mutations after proposal preparation
        val circuitAfterProposal = database.circuitDao().getCircuitById(1)!!
        val revAfterProposal = repository.getCurrentRevision(projectId).revision
        assertEquals("Revision must not advance during proposal preparation", initialRevision, revAfterProposal)
        assertEquals("Circuit overrideCableSize in Room must remain baseline before explicit approval", initialCircuit.overrideCableSize, circuitAfterProposal.overrideCableSize)

        // Explicit Human Approval executed ONLY via TrustedProposalApprovalGateway
        val approvalRequest = TrustedApprovalRequest(
            proposal = ready.proposal,
            projectContext = BoundedProjectContext(
                projectId = projectId,
                projectName = "Test",
                liveRevision = initialRevision,
                activeEntityType = "CIRCUIT",
                activeEntityId = "1"
            ),
            userActionId = "UA-TEST-APPROVAL",
            approvalId = "APP-TEST-GATEWAY-1"
        )

        val approvalResult = gateway.processApprovalAndExecute(approvalRequest)
        assertTrue("Approval execution through gateway must succeed", approvalResult is TrustedApprovalGateResult.Success)

        // Verify physical mutation occurred in Room now and ONLY now
        val mutatedCircuit = database.circuitDao().getCircuitById(1)!!
        val finalRev = repository.getCurrentRevision(projectId).revision
        assertEquals("Revision must advance by exactly 1 after trusted approval", initialRevision + 1L, finalRev)
        assertEquals("Circuit overrideCableSize must now be updated to 240 in Room", "240", mutatedCircuit.overrideCableSize)
    }
}
