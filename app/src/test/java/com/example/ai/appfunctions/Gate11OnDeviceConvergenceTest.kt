package com.example.ai.appfunctions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedApprovalGateResult
import com.example.ai.approval.TrustedApprovalRequest
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.context.ContextResolver
import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.EngineeringClaim
import com.example.ai.grounding.EngineeringClaimType
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingFailureReason
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.proposal.ApprovalStatus
import com.example.ai.proposal.CableCircuitPatch
import com.example.ai.proposal.DurableMutationOutcome
import com.example.ai.proposal.DurableMutationWorkflow
import com.example.ai.proposal.EngineeringProposal
import com.example.ai.proposal.PROPOSAL_SCHEMA_VERSION_V1
import com.example.ai.proposal.PatchAction
import com.example.ai.proposal.PatchValue
import com.example.ai.proposal.ProposalOperation
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableToolNames
import com.example.ai.tools.cable.CableWhatIfResult
import com.example.ai.ui.CableProposalPreparationCoordinator
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
 * GATE 11 TEST SUITE: On-Device Convergence & AppFunctions Integration
 *
 * Verifies:
 * A. AppFunction service registration in AndroidManifest.
 * B. Invalid input rejection (blank project, blank circuit, negative/zero current).
 * C. Tool convergence (reaches AssistantToolRuntime.dispatcher and EngineeringToolRegistry).
 * D. Real deterministic calculation result returned (CableSizingOutput via CableSizingEngine).
 * E. Grounding validation (contradictory model narrative rejected by GroundedResponseValidator).
 * F. Context continuity across turns (resolved via ContextResolver).
 * G. Cross-project isolation (Circuit from Project A inaccessible from Project B).
 * H. Coroutine cancellation leaves 0 mutations in Room.
 * I. Mutation boundary (AppFunctions cannot mutate Room; direct write blocked).
 * J. Natural-language phrases ("yes", "apply", "اعتمد") produce 0 mutations.
 * K. Gate 10 concurrent duplicate approval regression check.
 * L. Gate 9 proposal preparation zero-mutation check.
 *
 * Section 18: Full End-to-End Scenario:
 * AppFunction entry -> Sizing -> Evidence -> Grounded Response -> What-If -> Comparison -> Proposal
 * (0 Room writes, revision unchanged, receipts unchanged).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Gate11OnDeviceConvergenceTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var runtime: AssistantToolRuntime
    private lateinit var appFunctions: VoltAtlasAppFunctions
    private lateinit var gateway: TrustedProposalApprovalGateway
    private lateinit var proposalCoordinator: CableProposalPreparationCoordinator

    private val projectId = "PROJ-GATE11-A"
    private val projectOther = "PROJ-GATE11-B"
    private val circuitId = 1
    private val circuitOtherId = 2
    private val baselineRevision = 1L
    private val fixedClockTime = 100_000L

    companion object {
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
    fun setUp() = runBlocking {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        repository = EngineeringRepository(database)
        catalogRepository = buildCatalogRepository()

        EngineeringCalculationCoordinator.projectRevisionProvider = repository
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

        runtime = AssistantToolRuntime.create(
            repository = repository,
            catalogRepository = catalogRepository
        )

        appFunctions = VoltAtlasAppFunctions(
            toolRuntimeProvider = { runtime }
        )

        proposalCoordinator = CableProposalPreparationCoordinator(
            projectDao = database.projectDao(),
            circuitDao = database.circuitDao()
        )

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = DurableMutationWorkflow(
                db = database,
                catalogRepository = catalogRepository,
                clock = { fixedClockTime },
                executionIdProvider = { "EXEC-GATE11" }
            ),
            clock = { fixedClockTime },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-gate11" }
        )

        seedProjectsAndCircuits()
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

    private suspend fun seedProjectsAndCircuits() {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectId, projectName = "Gate 11 Project A", revision = baselineRevision)
        )
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectOther, projectName = "Gate 11 Project B", revision = baselineRevision)
        )

        database.transformerDao().insertTransformer(
            TransformerEntity(
                transformerId = "TX-G11",
                projectId = projectId,
                transformerName = "Main TX",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0
            )
        )

        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = circuitId,
                projectId = projectId,
                circuitName = "Feeder 101",
                loadKVA = 250.0,
                length_m = 40.0,
                voltageV = 400,
                phases = "3P+N",
                pf = 0.85,
                maxVDpct = 0.04,
                cableType = "Cu/XLPE/PVC - 4C",
                manufacturer = "Elsewedy",
                installation = "Laid in Free Air",
                parallelRuns = 1,
                fedFrom = "Transformer",
                transformerId = "TX-G11",
                overrideCableSize = "70.0"
            )
        )

        database.circuitDao().insertCircuit(
            CircuitEntity(
                id = circuitOtherId,
                projectId = projectOther,
                circuitName = "Feeder 201",
                loadKVA = 100.0,
                length_m = 25.0,
                voltageV = 400,
                phases = "3P+N",
                pf = 0.90,
                maxVDpct = 0.03,
                cableType = "Cu/XLPE/PVC - 4C",
                manufacturer = "Elsewedy",
                installation = "Laid in Free Air",
                parallelRuns = 1,
                fedFrom = "Transformer",
                transformerId = "TX-G11",
                overrideCableSize = "70.0"
            )
        )
    }

    private fun boundedContext(projId: String = projectId, cId: Int = circuitId, rev: Long = baselineRevision): BoundedProjectContext =
        BoundedProjectContext(
            projectId = projId,
            projectName = "Gate 11 Context",
            liveRevision = rev,
            activeScreen = "CircuitEditor",
            activeEntityType = "CIRCUIT",
            activeEntityId = cId.toString(),
            activeEntityName = "Feeder $cId"
        )

    // =========================================================================
    // Requirement A: AppFunction Service Discovery / Registration
    // =========================================================================
    @Test
    fun testRequirementA_appFunctionServiceRegistrationInManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        val intent = Intent(VoltAtlasAppFunctionService.ACTION_APP_FUNCTION_SERVICE).apply {
            setPackage(context.packageName)
        }

        val resolveInfos = packageManager.queryIntentServices(
            intent,
            PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
        )

        assertEquals("AppFunctionService must be declared exactly once in manifest", 1, resolveInfos.size)
        val info = resolveInfos[0].serviceInfo
        assertNotNull(info)
        assertEquals(VoltAtlasAppFunctionService::class.java.name, info.name)
        assertTrue("AppFunctionService must be exported", info.exported)
        assertEquals(
            "Must be protected by BIND_APP_FUNCTION_SERVICE permission",
            "android.permission.BIND_APP_FUNCTION_SERVICE",
            info.permission
        )
    }

    // =========================================================================
    // Requirement B: Invalid Input Rejection
    // =========================================================================
    @Test
    fun testRequirementB_invalidInputRejection() = runBlocking {
        // Zero load current
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.calculateCableSize(CableSizingParams(loadCurrentAmps = 0.0))
            }
        }

        // Negative load current
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.calculateCableSize(CableSizingParams(loadCurrentAmps = -10.0))
            }
        }

        // Blank project ID
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.calculateCableSize(
                    CableSizingParams(projectId = "  ", circuitId = "1", loadCurrentAmps = 50.0)
                )
            }
        }

        // Blank circuit ID
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.calculateCableSize(
                    CableSizingParams(projectId = projectId, circuitId = "   ", loadCurrentAmps = 50.0)
                )
            }
        }

        // Missing required fields when not bound to project/circuit
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.calculateCableSize(
                    CableSizingParams(loadCurrentAmps = 50.0, cableLengthMeters = 30.0)
                )
            }
        }

        // executeCableSizing with blank arguments
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.executeCableSizing(projectId = "", circuitId = "1")
            }
        }
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.executeCableSizing(projectId = projectId, circuitId = "")
            }
        }

        // executeCandidateComparison with empty list
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking {
                appFunctions.executeCandidateComparison(projectId = projectId, circuitId = "1", candidates = emptyList())
            }
        }
        Unit
    }

    // =========================================================================
    // Requirement C & D: Tool Convergence & Real Deterministic Result
    // =========================================================================
    @Test
    fun testRequirementC_and_D_toolConvergenceAndRealDeterministicResult() = runBlocking {
        // 1. Test calculateCableSize AppFunction
        val output = appFunctions.calculateCableSize(
            CableSizingParams(
                projectId = projectId,
                circuitId = circuitId.toString(),
                loadCurrentAmps = 360.0
            )
        )

        assertNotNull("Cable sizing output must not be null", output)
        assertTrue("Recommended size must not be empty", output.recommendedSizeMm2.isNotBlank())
        assertTrue("Breaker rating must be positive", output.protectiveDeviceRatingAmps > 0.0)
        assertTrue("Iz capacity must be positive", output.izCapacityAmps > 0.0)
        assertTrue("Voltage drop pct must be non-negative", output.voltageDropPct >= 0.0)
        assertTrue("Voltage drop volts must be non-negative", output.voltageDropVolts >= 0.0)
        assertTrue("Status must be PASS or FAIL", output.status in listOf("PASS", "FAIL"))
        assertEquals("Standard reference must match IEC standard", "IEC 60364-5-52", output.standardReference)

        // 2. Test executeCableSizing producing EngineeringEvidenceRecord
        val (outcome, evidence) = appFunctions.executeCableSizing(
            projectId = projectId,
            circuitId = circuitId.toString(),
            conversationId = "CONV-G11-001"
        )

        assertEquals("Outcome must be PASS", ToolStatus.PASS, outcome.envelope.status)
        assertEquals(CableToolNames.CALCULATE_CABLE_SIZING, outcome.toolName)

        val result = outcome.envelope.result as? CableSizingExecutionResult
        assertNotNull("Payload must be CableSizingExecutionResult", result)
        assertEquals(projectId, result!!.projectId)
        assertEquals(circuitId.toString(), result.circuitId)

        // Evidence validation
        assertEquals(projectId, evidence.projectId)
        assertEquals(circuitId.toString(), evidence.entityId)
        assertTrue(
            "Evidence entityType must match CIRCUIT (ignoring case)",
            evidence.entityType.equals("CIRCUIT", ignoreCase = true)
        )
        assertEquals(CableToolNames.CALCULATE_CABLE_SIZING, evidence.toolName)
        assertEquals(ToolStatus.PASS, evidence.executionStatus)
        assertFalse("Direct sizing is not a scenario", evidence.isScenario)
    }

    // =========================================================================
    // Requirement E: Grounding Validation Rejects Contradictory Claims
    // =========================================================================
    @Test
    fun testRequirementE_groundingRejectsContradictoryClaims() = runBlocking {
        val (_, evidence) = appFunctions.executeCableSizing(
            projectId = projectId,
            circuitId = circuitId.toString()
        )
        val context = boundedContext()

        val authoritativeResult = evidence.result as CableSizingExecutionResult
        val actualSize = authoritativeResult.details.cableSize_mm2

        // 1. True claim matching authoritative evidence
        val validClaimText = "Based on calculation, we recommend to use $actualSize for this feeder."
        val validResult = GroundedResponseValidator.validateResponse(
            text = validClaimText,
            evidenceList = listOf(evidence),
            context = context
        )
        assertTrue("Valid claim must pass grounding validation", validResult.isValid)

        // 2. Contradictory structured claim asserting a completely different size (10.0 mm2 vs 70 mm2)
        val contradictoryClaim = EngineeringClaim(
            claimType = EngineeringClaimType.ENGINEERING_VALUE,
            name = "cableSize",
            typedValue = EngineeringValue.ofDecimal(10.0, EngineeringUnit.SQUARE_MILLIMETER),
            projectId = projectId,
            entityId = circuitId.toString(),
            domain = "CABLE_SIZING"
        )
        val rejectedResult = GroundedResponseValidator.validateResponse(
            text = "We recommend you size the cable to 10.0 mm2.",
            structuredClaims = listOf(contradictoryClaim),
            evidenceList = listOf(evidence),
            context = context
        )
        assertFalse("Contradictory numeric claim must be rejected by GroundedResponseValidator", rejectedResult.isValid)
        assertTrue("Rejection must be a Rejected instance", rejectedResult is GroundingValidationResult.Rejected)
        assertEquals(
            GroundingFailureReason.NUMERIC_MISMATCH,
            (rejectedResult as GroundingValidationResult.Rejected).reason
        )

        // 3. Status contradiction: asserting PASS when authoritative evidence overallStatus is FAIL
        val contradictoryStatusText = "The circuit passed the sizing check and conforms to IEC."
        val rejectedStatusResult = GroundedResponseValidator.validateResponse(
            text = contradictoryStatusText,
            evidenceList = listOf(evidence),
            context = context
        )
        assertFalse("Status contradiction must be rejected by GroundedResponseValidator", rejectedStatusResult.isValid)
        assertEquals(
            GroundingFailureReason.STATUS_MISMATCH,
            (rejectedStatusResult as GroundingValidationResult.Rejected).reason
        )
    }

    // =========================================================================
    // Requirement F: Context Continuity Across Turns
    // =========================================================================
    @Test
    fun testRequirementF_contextContinuityAcrossTurns() = runBlocking {
        val (_, evidence) = appFunctions.executeCableSizing(
            projectId = projectId,
            circuitId = circuitId.toString(),
            conversationId = "CONV-MULTI-TURN"
        )

        // Turn 1 State
        val turn1State = ConversationTaskState(
            taskId = "TASK-G11-1",
            projectId = projectId,
            baseProjectRevision = baselineRevision,
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId.toString(),
            evidenceReferences = listOf(evidence.evidenceId)
        )

        // Turn 2: User says "what if we change the length to 50m for that circuit?"
        val resolved = ContextResolver.resolve(
            userUtterance = "what if we change the length to 50m for that circuit?",
            projectContext = boundedContext(),
            priorState = turn1State,
            historicalEvidence = listOf(evidence)
        )

        assertEquals("Project continuity preserved", projectId, resolved.updatedTaskState.projectId)
        assertEquals("Entity continuity preserved", circuitId.toString(), resolved.updatedTaskState.activeEntityId)
        assertEquals("Revision continuity preserved", baselineRevision, resolved.updatedTaskState.baseProjectRevision)
        assertTrue("Evidence references preserved", resolved.updatedTaskState.evidenceReferences.isNotEmpty())
    }

    // =========================================================================
    // Requirement G: Cross-Project Isolation
    // =========================================================================
    @Test
    fun testRequirementG_crossProjectIsolation() = runBlocking {
        // Querying Circuit 1 (belongs to Project A) under Project B must fail closed
        val outcome = runtime.dispatcher.execute(
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            projectId = projectOther, // Project B
            entityType = "circuit",
            entityId = circuitId.toString(), // Circuit 1 (Project A)
            conversationId = null,
            arguments = emptyMap()
        )

        assertNotEquals("Cross-project query must not succeed", ToolStatus.PASS, outcome.envelope.status)
        assertTrue(
            "Envelope must report incomplete or not found",
            outcome.envelope.status == ToolStatus.INCOMPLETE || outcome.envelope.status == ToolStatus.ERROR
        )
    }

    // =========================================================================
    // Requirement H: Cancellation Leaves Zero Mutations
    // =========================================================================
    @Test
    fun testRequirementH_cancellationLeavesZeroMutations() = runBlocking {
        val initialCircuit = database.circuitDao().getCircuitById(circuitId)!!
        val initialRevision = database.projectDao().getRevision(projectId)!!
        val initialReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        val job = launch(Dispatchers.IO) {
            try {
                appFunctions.executeCableSizing(projectId, circuitId.toString())
            } catch (e: CancellationException) {
                // Expected on cancellation
            }
        }

        // Cancel immediately
        job.cancelAndJoin()

        // Verify zero database changes
        val afterCircuit = database.circuitDao().getCircuitById(circuitId)!!
        val afterRevision = database.projectDao().getRevision(projectId)!!
        val afterReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        assertEquals("Circuit overrideCableSize must be unchanged", initialCircuit.overrideCableSize, afterCircuit.overrideCableSize)
        assertEquals("Project revision must not advance", initialRevision, afterRevision)
        assertEquals("Receipts count must remain 0", initialReceipts.size, afterReceipts.size)
    }

    // =========================================================================
    // Requirement I: Mutation Boundary (AppFunctions Cannot Mutate Room)
    // =========================================================================
    @Test
    fun testRequirementI_mutationBoundaryEnforced() = runBlocking {
        val initialCircuit = database.circuitDao().getCircuitById(circuitId)!!
        val initialRevision = database.projectDao().getRevision(projectId)!!
        val initialReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        // Execute multiple read-only AppFunction workflows
        appFunctions.calculateCableSize(CableSizingParams(projectId = projectId, circuitId = circuitId.toString(), loadCurrentAmps = 100.0))
        appFunctions.executeCableSizing(projectId, circuitId.toString())
        appFunctions.executeCableWhatIf(projectId, circuitId.toString(), mapOf("length_m" to 60.0))
        appFunctions.executeCandidateComparison(projectId, circuitId.toString(), listOf(mapOf("sizeMm2" to 185.0), mapOf("sizeMm2" to 240.0)))

        // Verify Room is completely unmutated
        val afterCircuit = database.circuitDao().getCircuitById(circuitId)!!
        val afterRevision = database.projectDao().getRevision(projectId)!!
        val afterReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        assertEquals("Circuit overrideCableSize must remain 70.0", "70.0", afterCircuit.overrideCableSize)
        assertEquals("Project revision must remain at baseline (1L)", initialRevision, afterRevision)
        assertEquals("No mutation receipts may be written", 0, afterReceipts.size)
    }

    // =========================================================================
    // Requirement J: Natural-Language Phrases Produce Zero Mutations
    // =========================================================================
    @Test
    fun testRequirementJ_naturalLanguagePhrasesProduceZeroMutations() = runBlocking {
        val phrases = listOf("yes", "apply", "approve", "اعتمد", "نفذ", "طبق")

        for (phrase in phrases) {
            val initialRevision = database.projectDao().getRevision(projectId)!!
            val initialReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

            // Feeding natural-language phrase to ContextResolver
            val resolved = ContextResolver.resolve(
                userUtterance = phrase,
                projectContext = boundedContext(),
                priorState = null
            )
            assertNotNull(resolved)

            // Assert zero mutation
            val afterRevision = database.projectDao().getRevision(projectId)!!
            val afterReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

            assertEquals("Revision must not advance on phrase '$phrase'", initialRevision, afterRevision)
            assertEquals("Receipts must remain 0 on phrase '$phrase'", initialReceipts.size, afterReceipts.size)
        }
    }

    // =========================================================================
    // Requirement K: Gate 10 Concurrent Duplicate Approval Regression
    // =========================================================================
    @Test
    fun testRequirementK_gate10ConcurrentDuplicateApprovalRegression() = runBlocking {
        val patch = CableCircuitPatch(overrideCableSize = PatchValue.set("240.0"))
        val proposal = EngineeringProposal(
            proposalId = "PROP-G11-REGRESSION",
            schemaVersion = PROPOSAL_SCHEMA_VERSION_V1,
            projectId = projectId,
            baseProjectRevision = baselineRevision,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = patch,
            rationale = "Safe regression test proposal",
            sourceToolName = "test",
            sourceRequestId = "REQ-G11"
        )
        val expected = mapOf("overrideCableSize" to "70.0")

        val executor = Executors.newFixedThreadPool(2)
        val readyLatch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val task1 = executor.submit<TrustedApprovalGateResult> {
            readyLatch.countDown()
            startLatch.await(5, TimeUnit.SECONDS)
            runBlocking {
                gateway.processApprovalAndExecute(
                    TrustedApprovalRequest(
                        proposal = proposal,
                        projectContext = boundedContext(),
                        userActionId = "UA-G11-1",
                        approvalId = "APP-G11-1",
                        expectedCurrentValues = expected
                    )
                )
            }
        }

        val task2 = executor.submit<TrustedApprovalGateResult> {
            readyLatch.countDown()
            startLatch.await(5, TimeUnit.SECONDS)
            runBlocking {
                gateway.processApprovalAndExecute(
                    TrustedApprovalRequest(
                        proposal = proposal,
                        projectContext = boundedContext(),
                        userActionId = "UA-G11-2",
                        approvalId = "APP-G11-2",
                        expectedCurrentValues = expected
                    )
                )
            }
        }

        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()

        val res1 = task1.get(10, TimeUnit.SECONDS)
        val res2 = task2.get(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("First response must be success, got $res1", res1 is TrustedApprovalGateResult.Success)
        assertTrue("Second response must be success, got $res2", res2 is TrustedApprovalGateResult.Success)

        val outcome1 = (res1 as TrustedApprovalGateResult.Success).outcome
        val outcome2 = (res2 as TrustedApprovalGateResult.Success).outcome

        val committedCount = listOf(outcome1, outcome2).count { it is DurableMutationOutcome.Completed }
        val replayCount = listOf(outcome1, outcome2).count {
            it is DurableMutationOutcome.ExistingReceiptReturned || it is DurableMutationOutcome.ApprovalAlreadyConsumed
        }
        assertEquals("Exactly one concurrent caller must commit mutation", 1, committedCount)
        assertEquals("Second concurrent caller must return existing receipt or already consumed", 1, replayCount)

        // Invariants:
        // 1. Room persisted value updated to 240.0
        val circuitAfter = database.circuitDao().getCircuitById(circuitId)!!
        assertEquals("240.0", circuitAfter.overrideCableSize)
        // 2. Exactly one revision increment (1L -> 2L)
        val revAfter = database.projectDao().getRevision(projectId)!!
        assertEquals(2L, revAfter)
        // 3. Exactly one receipt created
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(1, receipts.size)
        // 4. Exactly one grant consumed
        val grants = database.approvalGrantDao().getGrantsForProject(projectId)
        assertEquals(1, grants.count { it.status == ApprovalStatus.CONSUMED })
    }

    // =========================================================================
    // Requirement L: Gate 9 Proposal Preparation Zero-Mutation Check
    // =========================================================================
    @Test
    fun testRequirementL_gate9ProposalPreparationZeroMutationRegression() = runBlocking {
        val initialCircuit = database.circuitDao().getCircuitById(circuitId)!!
        val initialRevision = database.projectDao().getRevision(projectId)!!
        val initialReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        val whatIfOutcome = appFunctions.executeCableWhatIf(
            projectId = projectId,
            circuitId = circuitId.toString(),
            overrides = mapOf("overrideCableSize" to "240.0")
        )
        val scenarioEvidence = EngineeringEvidenceRecord.fromOutcome(
            outcome = whatIfOutcome,
            conversationId = "CONV-G11-REGRESSION",
            entityType = "CIRCUIT",
            entityId = circuitId.toString()
        )

        val action = ConversationAction(
            actionType = ConversationActionType.CREATE_PROPOSAL,
            reasonCode = "ADOPT_SCENARIO_CANDIDATE",
            candidateParameters = mapOf("overrideCableSize" to "240.0")
        )

        val prepResult = proposalCoordinator.prepare(
            action = action,
            projectContext = boundedContext(),
            decisionId = "DEC-G11-REGRESSION",
            evidence = listOf(scenarioEvidence)
        )

        assertTrue("Proposal must be ready for review, got $prepResult", prepResult is CableProposalPreparationCoordinator.Result.Ready)

        // Strict zero mutation verification
        val afterCircuit = database.circuitDao().getCircuitById(circuitId)!!
        val afterRevision = database.projectDao().getRevision(projectId)!!
        val afterReceipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        assertEquals("Circuit overrideCableSize must remain unchanged during proposal prep", initialCircuit.overrideCableSize, afterCircuit.overrideCableSize)
        assertEquals("Revision must not advance during proposal prep", initialRevision, afterRevision)
        assertEquals("No receipts may be created during proposal prep", initialReceipts.size, afterReceipts.size)
    }

    // =========================================================================
    // Section 18: Full End-to-End Scenario
    // =========================================================================
    @Test
    fun testSection18_fullEndToEndConvergenceScenario() = runBlocking {
        // Step 1: AppFunction Entry -> Sizing
        val (outcome, evidence) = appFunctions.executeCableSizing(
            projectId = projectId,
            circuitId = circuitId.toString(),
            conversationId = "CONV-E2E-G11"
        )
        assertEquals(ToolStatus.PASS, outcome.envelope.status)
        assertNotNull(evidence)

        // Step 2: Grounding Validation
        val sizingResult = outcome.envelope.result as CableSizingExecutionResult
        val groundedValid = GroundedResponseValidator.validateResponse(
            text = "Based on calculation, we recommend to use ${sizingResult.details.cableSize_mm2} mm2 for this feeder.",
            evidenceList = listOf(evidence),
            context = boundedContext()
        )
        assertTrue("Grounded response validation must succeed", groundedValid.isValid)

        // Step 3: Follow-Up What-If
        val whatIfOutcome = appFunctions.executeCableWhatIf(
            projectId = projectId,
            circuitId = circuitId.toString(),
            overrides = mapOf("length_m" to 50.0, "overrideCableSize" to "240.0"),
            conversationId = "CONV-E2E-G11"
        )
        assertEquals(ToolStatus.PASS, whatIfOutcome.envelope.status)
        val whatIfEvidence = EngineeringEvidenceRecord.fromOutcome(
            outcome = whatIfOutcome,
            conversationId = "CONV-E2E-G11",
            entityType = "CIRCUIT",
            entityId = circuitId.toString()
        )
        assertTrue("What-if evidence must have isScenario == true", whatIfEvidence.isScenario)

        // Step 4: Candidate Comparison
        val compOutcome = appFunctions.executeCandidateComparison(
            projectId = projectId,
            circuitId = circuitId.toString(),
            candidates = listOf(
                mapOf("sizeMm2" to 185.0),
                mapOf("sizeMm2" to 240.0)
            ),
            conversationId = "CONV-E2E-G11"
        )
        assertEquals(ToolStatus.PASS, compOutcome.envelope.status)

        // Step 5: Proposal Preparation
        val action = ConversationAction(
            actionType = ConversationActionType.CREATE_PROPOSAL,
            reasonCode = "ADOPT_SCENARIO",
            candidateParameters = mapOf("overrideCableSize" to "240.0")
        )
        val prepResult = proposalCoordinator.prepare(
            action = action,
            projectContext = boundedContext(),
            decisionId = "DEC-E2E-G11",
            evidence = listOf(whatIfEvidence)
        )
        assertTrue("Proposal preparation must be Ready", prepResult is CableProposalPreparationCoordinator.Result.Ready)

        // Step 6: Verify strict zero Room mutation across the entire lifecycle up to proposal
        val circuitAfter = database.circuitDao().getCircuitById(circuitId)!!
        val revAfter = database.projectDao().getRevision(projectId)!!
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

        assertEquals("Circuit overrideCableSize must remain 70.0", "70.0", circuitAfter.overrideCableSize)
        assertEquals("Revision must remain at baseline 1L", baselineRevision, revAfter)
        assertEquals("Receipts count must remain 0", 0, receipts.size)
    }
}
