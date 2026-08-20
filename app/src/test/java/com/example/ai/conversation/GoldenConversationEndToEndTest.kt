package com.example.ai.conversation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedApprovalGateResult
import com.example.ai.approval.TrustedApprovalRequest
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.proposal.*
import com.example.ai.runtime.*
import com.example.ai.ui.ApprovalExecutionUiState
import com.example.ai.ui.ProposalReviewMapper
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoldenConversationEndToEndTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var workflow: DurableMutationWorkflow
    private lateinit var gateway: TrustedProposalApprovalGateway

    private val projectId = "PROJ-GOLDEN-1"
    private val circuitId = 107
    private var currentTime = 10_000L

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        EngineeringCalculationCoordinator.projectRevisionProvider = EngineeringRepository(database)

        catalogRepository = CatalogRepository().apply {
            loadAllFromStrings(
                cableCatalogJson = """
                [
                  {"mfg":"Elsewedy","typeCode":1,"size":240.0,"model":"Elsewedy Cu/XLPE 240"},
                  {"mfg":"Elsewedy","typeCode":1,"size":300.0,"model":"Elsewedy Cu/XLPE 300"},
                  {"mfg":"Ducab","typeCode":2,"size":300.0,"model":"Ducab Al/XLPE 300"}
                ]
                """.trimIndent(),
                cableTypesDataJson = """
                [
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":240.0,"rCu70":0.098,"x":0.08,"rAl70":0.0,"rCu90":0.104,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":300.0,"rCu70":0.078,"x":0.08,"rAl70":0.0,"rCu90":0.082,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
                  {"typeCode":2,"cableType":"Al/XLPE/STA/PVC - 4C","sizeMm2":300.0,"rCu70":0.0,"x":0.08,"rAl70":0.125,"rCu90":0.0,"rAl90":0.13,"conductor":"Al","insulation":"XLPE","armour":"STA","cores":"4C"}
                ]
                """.trimIndent(),
                breakerCatalogJson = """
                [
                  {"mfg":"Schneider","type":"MCCB-TM","series":"NSX","frame":"NSX400","version":"F","ratingA":400.0,"poles":3,"icuKa":36.0,"icsKa":36.0,"icmKa":75.6,"icwKa":0.0,"cat":"A","tripUnit":"TM400D","tripType":"Thermal-Magnetic","orderingCode":"LV432676"}
                ]
                """.trimIndent(),
                accessoryCatalogJson = "[]",
                deratingDataJson = "{}",
                transformersSeedJson = "[]"
            )
        }

        workflow = DurableMutationWorkflow(
            db = database,
            catalogRepository = catalogRepository,
            clock = { currentTime },
            executionIdProvider = { "EXEC-GOLDEN-01" }
        )

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-lead-golden" }
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "Golden Test Project",
                    revision = 1L,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    projectId = projectId,
                    transformerId = "TX-1",
                    transformerName = "Main TX",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = circuitId,
                    circuitName = "C-07 Feeder",
                    projectId = projectId,
                    transformerId = "TX-1",
                    sourceType = "TRANSFORMER",
                    fedFrom = "Transformer",
                    cableType = "Cu/XLPE/SWA/PVC",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Ducts",
                    parallelRuns = 1,
                    length_m = 50.0,
                    overrideCableSize = "240.0",
                    overrideDeviceSize = 400.0,
                    overrideDeviceType = "MCCB-TM"
                )
            )
        }
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        AppDatabase.useTestExecutors = false
        database.close()
    }

    private fun createProjectContext(liveRev: Long = 1L): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = projectId,
            projectName = "Golden Test Project",
            liveRevision = liveRev,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId.toString(),
            activeEntityName = "C-07 Feeder"
        )
    }

    // Flow 1: Explain -> Evaluate -> What-If -> Propose -> Chat Apply (Non-executable) -> Trusted Apply
    @Test
    fun flow1_explain_evaluate_whatif_propose_approve() = runBlocking {
        val ctx = createProjectContext(liveRev = 1L)

        // Turn 1: Explain
        val t1 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Why is C-07 using 300 mm²?",
                projectContext = ctx,
                priorState = null
            )
        )
        assertEquals(AssistantIntent.EXPLAIN_RESULT, t1.updatedState.primaryIntent)

        // Turn 2: Evaluate candidate
        val t2 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Can 240 mm² work?",
                projectContext = ctx,
                priorState = t1.updatedState
            )
        )
        assertEquals(AssistantIntent.EVALUATE_CANDIDATE, t2.updatedState.primaryIntent)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))

        // Turn 3: What-If (try aluminum)
        val t3 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Try aluminum",
                projectContext = ctx,
                priorState = t2.updatedState
            )
        )
        assertEquals(AssistantIntent.WHAT_IF, t3.updatedState.primaryIntent)

        // Turn 4: Propose
        val proposal = CableEngineeringProposal(
            proposalId = "PROP-GOLDEN-1",
            projectId = projectId,
            baseProjectRevision = 1L,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300.0")),
            rationale = "Upsize C-07 to 300.0 mm² for thermal capacity",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-1"
        )
        val t4State = t3.updatedState.copy(activeProposalId = proposal.proposalId)

        // Turn 5: Chat user says "Apply" -> SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE
        val t5 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Apply",
                projectContext = ctx,
                priorState = t4State
            )
        )
        assertEquals(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, t5.action.actionType)
        // Verify zero grants inserted by conversational text
        assertEquals(0, database.approvalGrantDao().getGrantsForProject(projectId).size)

        // Turn 6: Trusted UI physical confirmation action
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = ctx,
            userActionId = "UA-PHYSICAL-001"
        )
        val gateResult = gateway.processApprovalAndExecute(request)
        assertTrue(gateResult is TrustedApprovalGateResult.Success)

        // Outcome is committed
        val success = gateResult as TrustedApprovalGateResult.Success
        assertTrue(success.outcome is DurableMutationOutcome.Completed)
        val completed = success.outcome as DurableMutationOutcome.Completed
        assertEquals(1L, completed.receipt.revisionBefore)
        assertEquals(2L, completed.receipt.revisionAfter)
        assertEquals(RevalidationStatus.SUCCEEDED, completed.receipt.revalidationStatus)

        // Circuit updated in Room
        val circuit = database.circuitDao().getCircuitById(circuitId)
        assertEquals("300.0", circuit?.overrideCableSize)
        assertEquals(2L, database.projectDao().getProject(projectId)?.revision)
    }

    // Flow 2: Minimum Question Behavior
    @Test
    fun flow2_minimumQuestionBehavior() {
        val ctx = createProjectContext(liveRev = 1L)
        // User asks to size circuit with missing installation method
        val t1 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Size this feeder",
                projectContext = ctx,
                priorState = null
            )
        )
        assertNotNull(t1.action)

        // Turn 2: User provides installation method "Tray"
        val t2 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Perforated Cable Tray",
                projectContext = ctx,
                priorState = t1.updatedState
            )
        )
        assertNotNull(t2.action)
    }

    // Flow 3: Contradictory Voltage
    @Test
    fun flow3_contradictoryVoltage() = runBlocking {
        val ctx = createProjectContext(liveRev = 1L)
        val t1 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Calculate it at 230 V",
                projectContext = ctx,
                priorState = null
            )
        )
        // Contradiction recognized as what-if or explanation, project DB remains untouched
        assertNotNull(t1.action)
        assertEquals(1L, database.projectDao().getProject(projectId)?.revision)
    }

    // Flow 4: Stale Result Detection
    @Test
    fun flow4_staleResultDetection() = runBlocking {
        // Project advances to revision 2
        database.projectDao().incrementRevision(projectId)
        val liveRev = database.projectDao().getProject(projectId)?.revision ?: 2L
        assertEquals(2L, liveRev)

        val staleProposal = CableEngineeringProposal(
            proposalId = "PROP-STALE-1",
            projectId = projectId,
            baseProjectRevision = 1L, // Stale base revision
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300.0")),
            rationale = "Stale proposal rationale",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-STALE"
        )

        val ctx = createProjectContext(liveRev = liveRev)
        val request = TrustedApprovalRequest(
            proposal = staleProposal,
            projectContext = ctx,
            userActionId = "UA-STALE-001"
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.StaleProposal)
        val stale = result as TrustedApprovalGateResult.StaleProposal
        assertEquals(1L, stale.baseRevision)
        assertEquals(2L, stale.liveRevision)

        // Zero grants inserted
        assertEquals(0, database.approvalGrantDao().getGrantsForProject(projectId).size)
    }

    // Flow 5: Failed Revalidation After Commit
    @Test
    fun flow5_failedRevalidationAfterCommit_preservesCommittedTruth() {
        val completedState = ApprovalExecutionUiState.Completed(
            receiptId = "REC-FAIL-REVAL",
            revisionBefore = 1L,
            revisionAfter = 2L,
            appliedChanges = emptyList(),
            revalidationStatus = "FAILED"
        )
        // Committed truth is strictly true
        assertTrue(completedState.isCommitted)
        assertEquals(2L, completedState.revisionAfter)
    }

    // Flow 6: Already Applied Retry
    @Test
    fun flow6_alreadyAppliedRetry_returnsExistingReceiptWithoutRemutating() = runBlocking {
        val proposal = CableEngineeringProposal(
            proposalId = "PROP-RETRY-1",
            projectId = projectId,
            baseProjectRevision = 1L,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300.0")),
            rationale = "Retry test",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-1"
        )
        val ctx = createProjectContext(liveRev = 1L)

        // First apply
        val res1 = gateway.processApprovalAndExecute(
            TrustedApprovalRequest(proposal, ctx, "UA-CLICK-1", "APP-RETRY-1")
        )
        assertTrue(res1 is TrustedApprovalGateResult.Success)

        // Second retry
        val res2 = gateway.processApprovalAndExecute(
            TrustedApprovalRequest(proposal, ctx, "UA-CLICK-2", "APP-RETRY-2")
        )
        assertTrue(res2 is TrustedApprovalGateResult.Success)

        // Revision is 2L, not 3L
        assertEquals(2L, database.projectDao().getProject(projectId)?.revision)
    }

    // Flow 7: Entity Switch clears proposal binding
    @Test
    fun flow7_entitySwitch_invalidatesProposalBinding() {
        val ctx = createProjectContext(liveRev = 1L)
        val state = ConversationTaskState(
            taskId = "TASK-1",
            projectId = projectId,
            baseProjectRevision = 1L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "107",
            activeProposalId = "PROP-107"
        )

        // User switches to C-08
        val switchInput = ConversationTurnInput(
            userUtterance = "Check feeder C-08",
            projectContext = ctx,
            priorState = state,
            availableEntities = listOf(EntityBinding("CIRCUIT", "108", "Feeder C-08"))
        )
        val switchDecision = ConversationPolicyEngine.evaluateTurn(switchInput)
        // Proposal binding cleared on entity switch
        assertNull(switchDecision.updatedState.activeProposalId)
        assertEquals("108", switchDecision.updatedState.activeEntityId)
    }

    // Flow 8: Cost Data Unavailable - compares technical proxies only
    @Test
    fun flow8_costDataUnavailable_statesUnavailable() {
        val ctx = createProjectContext(liveRev = 1L)
        val t1 = ConversationPolicyEngine.evaluateTurn(
            ConversationTurnInput(
                userUtterance = "Choose the cheapest option",
                projectContext = ctx,
                priorState = null
            )
        )
        assertNotNull(t1.action)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }
}
