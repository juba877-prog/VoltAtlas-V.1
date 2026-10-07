package com.example.ai.e2e

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.approval.TrustedProposalApprovalGateway
import com.example.ai.context.EngineeringContext
import com.example.ai.proposal.*
import com.example.ai.ui.ApprovalExecutionUiState
import com.example.ai.ui.AssistantEntryPoint
import com.example.ai.ui.EngineeringAssistantViewModel
import com.example.ai.ui.EngineeringDisplayValueUi
import com.example.ai.ui.ProposalChangeActionUi
import com.example.ai.ui.ProposalFieldChangeUiModel
import com.example.ai.ui.ProposalReviewUiModel
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE 10 — End-to-end trusted mutation lifecycle at the UI boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Gate10TrustedMutationLifecycleTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: AppDatabase
    private lateinit var gateway: TrustedProposalApprovalGateway

    private val projectId = "PROJ-GATE10-UI"
    private val circuitId = 601
    private var currentTime = 70_000L

    private val createdViewModels = mutableListOf<EngineeringAssistantViewModel>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = EngineeringRepository(database)

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = DurableMutationWorkflow(
                db = database,
                catalogRepository = filledCatalog(),
                clock = { currentTime },
                executionIdProvider = { "EXEC-GATE10-UI" }
            ),
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-gate10-ui" }
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "Gate 10 UI Project", revision = 1L)
            )
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    projectId = projectId,
                    transformerId = "TX-UI",
                    transformerName = "Main TX",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = circuitId,
                    circuitName = "Feeder 601",
                    projectId = projectId,
                    transformerId = "TX-UI",
                    sourceType = "TRANSFORMER",
                    fedFrom = "Transformer",
                    cableType = "Cu/XLPE/SWA/PVC",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Ducts",
                    parallelRuns = 1,
                    length_m = 50.0,
                    overrideCableSize = "300.0"
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        AppDatabase.resetInstanceForTesting()
        database.close()
        Dispatchers.resetMain()
    }

    private fun filledCatalog(): CatalogRepository = CatalogRepository().apply {
        loadAllFromStrings(
            cableCatalogJson = """
            [
              {"mfg":"Elsewedy","typeCode":1,"size":300.0,"model":"Elsewedy Cu/XLPE 300"},
              {"mfg":"Elsewedy","typeCode":1,"size":240.0,"model":"Elsewedy Cu/XLPE 240"}
            ]
            """.trimIndent(),
            cableTypesDataJson = """
            [
              {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":300.0,"rCu70":0.078,"x":0.08,"rAl70":0.0,"rCu90":0.082,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
              {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":240.0,"rCu70":0.098,"x":0.08,"rAl70":0.0,"rCu90":0.104,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"}
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

    private fun uiViewModel(): EngineeringAssistantViewModel =
        EngineeringAssistantViewModel(
            approvalGateway = gateway,
            projectRevisionProvider = EngineeringRepository(database)
        ).also { createdViewModels += it }

    private fun uiContext(): EngineeringContext = EngineeringContext(
        projectId = projectId,
        projectName = "Gate 10 UI Project",
        currentScreen = "CircuitsGridScreen",
        activeEntityType = "CIRCUIT",
        activeEntityId = circuitId.toString(),
        activeEntityName = "Feeder 601"
    )

    private suspend fun roomSize(): String? = database.circuitDao().getCircuitById(circuitId)?.overrideCableSize

    private suspend fun roomRevision(): Long? = database.projectDao().getProject(projectId)?.revision

    private suspend fun roomReceipts() = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)

    private suspend fun roomGrants() = database.approvalGrantDao().getGrantsForProject(projectId)

    private fun openedViewModel(): EngineeringAssistantViewModel {
        val viewModel = uiViewModel()
        viewModel.open(AssistantEntryPoint.DASHBOARD_ACTION, uiContext())
        return viewModel
    }

    /**
     * Drives the assistant's dispatcher queue to quiescence for an approval submission.
     *
     * The mutation boundary performs real SQLite work; the virtual test scheduler can go idle
     * while that work is still finishing on a real thread, so the queue is re-driven until the
     * submission leaves [ApprovalExecutionUiState.SubmittingApproval] (bounded, no fabricated wait).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun awaitSubmissionSettled(
        viewModel: EngineeringAssistantViewModel
    ): ApprovalExecutionUiState {
        repeat(200) {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value.approvalExecutionState
            if (state !is ApprovalExecutionUiState.SubmittingApproval) return state
            Thread.sleep(10)
        }
        return viewModel.uiState.value.approvalExecutionState
    }

    // Section 8 / 39. Natural language is never authorization: zero mutation.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun naturalLanguagePhrases_doNotMutate() = runTest(testDispatcher) {
        val viewModel = openedViewModel()
        val phrases = listOf(
            "yes",
            "ok",
            "apply",
            "اعتمد",
            "نفذ",
            "طبّق",
            "اعملها",
            "اعتمد الاختيار ده",
            "اعتمد القياس ده",
            "نفذ التعديل"
        )
        phrases.forEach { phrase -> viewModel.onUserSubmit(phrase) }
        advanceUntilIdle()
        assertEquals("300.0", roomSize())
        assertEquals(1L, roomRevision())
        assertTrue(roomReceipts().isEmpty())
        assertTrue(roomGrants().isEmpty())
        assertFalse(viewModel.uiState.value.approvalExecutionState.isCommitted)
    }

    // Section 49. Full lifecycle: Review -> Approval -> Mutation -> Receipt -> UI.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fullTrustedApprovalFlow_mutatesRoomAndUpdatesUiState() = runTest(testDispatcher) {
        val viewModel = openedViewModel()
        val (proposal, review) = reviewedProposal()

        viewModel.onContinueToApproval(proposal, review)
        advanceUntilIdle()

        viewModel.onConfirmApproveAndApply()
        advanceUntilIdle()
        val execState = awaitSubmissionSettled(viewModel)
        val diagnostics = "state=$execState size=${roomSize()} rev=${roomRevision()} " +
            "receipts=${roomReceipts().map { "${it.executionId}:${it.revisionBefore}->${it.revisionAfter}:${it.revalidationStatus}:${it.targetEntityId}" }} " +
            "grants=${roomGrants().map { "${it.approvalId}:${it.status}" }} " +
            "circuits=${database.circuitDao().getCircuitsListForProject(projectId).map { "${it.id}:${it.overrideCableSize}" }}"
        assertTrue("UI must reflect a committed state. $diagnostics", execState.isCommitted)
        assertEquals("Committed value. $diagnostics", "240.0", roomSize())
        assertEquals(2L, roomRevision())
        val receipts = roomReceipts()
        assertEquals(1, receipts.size)
        assertEquals(1L, receipts.first().revisionBefore)
        assertEquals(2L, receipts.first().revisionAfter)
        assertEquals(1, roomGrants().count { it.status == ApprovalStatus.CONSUMED })

        // Lineage: reviewed proposal -> approval grant -> durable receipt (no fabricated step).
        val receipt = receipts.first()
        assertEquals(proposal.proposalId, receipt.proposalId)
        assertEquals(DefaultProposalDigestEncoder().computeDigest(proposal), receipt.proposalDigest)
        assertEquals(DefaultProposalSemanticKeyEncoder().computeSemanticKey(proposal), receipt.proposalSemanticKey)
        assertEquals(projectId, receipt.projectId)
        assertEquals(circuitId.toString(), receipt.targetEntityId)
        val consumedGrant = roomGrants().first { it.status == ApprovalStatus.CONSUMED }
        assertEquals(receipt.approvalId, consumedGrant.approvalId)
        assertEquals("eng-gate10-ui", consumedGrant.approvedBy)
        // The committed before/after values are exactly the values shown in the review model.
        assertTrue(receipt.appliedChangesJson.contains("300.0"))
        assertTrue(receipt.appliedChangesJson.contains("240.0"))

        assertTrue(execState is ApprovalExecutionUiState.Completed)
    }

    // Section 29 / 46. Duplicate UI submission still executes exactly once.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun duplicateUiApproval_executesExactlyOnce() = runTest(testDispatcher) {
        val viewModel = openedViewModel()
        val (proposal, review) = reviewedProposal()

        viewModel.onContinueToApproval(proposal, review)
        advanceUntilIdle()
        viewModel.onConfirmApproveAndApply()
        advanceUntilIdle()
        awaitSubmissionSettled(viewModel)

        // Second explicit approval of the same reviewed proposal.
        viewModel.onContinueToApproval(proposal, review)
        advanceUntilIdle()
        viewModel.onConfirmApproveAndApply()
        advanceUntilIdle()
        val execState = awaitSubmissionSettled(viewModel)

        assertEquals("240.0", roomSize())
        assertEquals(2L, roomRevision())
        assertEquals(1, roomReceipts().size)
        assertEquals(1, roomGrants().count { it.status == ApprovalStatus.CONSUMED })
        // Section 29: a repeated approval is either resolved idempotently against the existing
        // receipt, or fails closed as stale. It is never a second mutation.
        assertTrue(
            "Duplicate approval must be idempotent (AlreadyApplied) or fail closed (StaleBeforeApproval), got $execState",
            execState is ApprovalExecutionUiState.AlreadyApplied ||
                execState is ApprovalExecutionUiState.StaleBeforeApproval
        )
    }

    // Section 30. A cancelled review can never approve or mutate.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledReview_cannotApproveOrMutate() = runTest(testDispatcher) {
        val viewModel = openedViewModel()
        val (proposal, review) = reviewedProposal()

        viewModel.onContinueToApproval(proposal, review)
        advanceUntilIdle()
        viewModel.onCancelApproval()
        advanceUntilIdle()

        // The confirmation state is gone, so the trusted action has nothing to approve.
        viewModel.onConfirmApproveAndApply()
        advanceUntilIdle()

        assertEquals("300.0", roomSize())
        assertEquals(1L, roomRevision())
        assertTrue(roomReceipts().isEmpty())
        assertTrue(roomGrants().isEmpty())
        assertEquals(ApprovalExecutionUiState.Idle, viewModel.uiState.value.approvalExecutionState)
    }

    // Section 15 / 45. A no-op approval produces a receipt-free UI state.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun noOpApprovalAtUiBoundary_reportsNoChangeWithoutReceipt() = runTest(testDispatcher) {
        val viewModel = openedViewModel()
        val (_, review) = reviewedProposal()

        val noOpProposal = CableEngineeringProposal(
            proposalId = "PROP-GATE10-UI-NOOP",
            projectId = projectId,
            baseProjectRevision = 1L,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("300.0")),
            rationale = "Proposed value already matches the persisted value.",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-GATE10-UI-NOOP",
            createdAt = currentTime,
            expiresAt = currentTime + 600_000L
        )
        val noOpReview = review.copy(
            proposalId = noOpProposal.proposalId,
            proposalArtifactDigest = DefaultProposalDigestEncoder().computeDigest(noOpProposal),
            fieldChanges = review.fieldChanges.map {
                it.copy(proposedValue = EngineeringDisplayValueUi("300.0", "300.0 mm²", "mm²"))
            }
        )

        viewModel.onContinueToApproval(noOpProposal, noOpReview)
        advanceUntilIdle()
        viewModel.onConfirmApproveAndApply()
        advanceUntilIdle()
        val execState = awaitSubmissionSettled(viewModel)

        assertEquals("300.0", roomSize())
        assertEquals(1L, roomRevision())
        assertTrue(roomReceipts().isEmpty())
        assertTrue(
            "No-op must surface an explicit no-change state, got $execState",
            execState is ApprovalExecutionUiState.NoChangeRequired
        )
        assertFalse(execState.isCommitted)
    }

    /**
     * Verified proposal mirroring a Gate 9 coordinator artifact: patch 300 -> 240 mm² with typed
     * before/after lineage preserved from preparation (scenario evidence) through review to
     * approval.
     */
    private fun reviewedProposal(): Pair<CableEngineeringProposal, ProposalReviewUiModel> {
        val proposal = CableEngineeringProposal(
            proposalId = "PROP-GATE10-UI-E2E",
            projectId = projectId,
            baseProjectRevision = 1L,
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = CableCircuitPatch(overrideCableSize = PatchValue.set("240.0")),
            rationale = "Scenario evidence: 240 mm² verified against ampacity and voltage drop.",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-GATE10-UI-E2E",
            createdAt = currentTime,
            expiresAt = currentTime + 600_000L
        )
        val review = ProposalReviewUiModel(
            proposalId = proposal.proposalId,
            proposalArtifactDigest = DefaultProposalDigestEncoder().computeDigest(proposal),
            proposalSemanticKey = DefaultProposalSemanticKeyEncoder().computeSemanticKey(proposal),
            projectId = projectId,
            projectLabel = "Gate 10 UI Project",
            targetEntityType = "CIRCUIT",
            targetEntityId = circuitId.toString(),
            targetEntityLabel = "Feeder 601",
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES.name,
            baseProjectRevision = 1L,
            currentLiveRevision = 1L,
            isStale = false,
            fieldChanges = listOf(
                ProposalFieldChangeUiModel(
                    fieldKey = "overrideCableSize",
                    label = "Cable Size",
                    action = ProposalChangeActionUi.SET,
                    currentValue = EngineeringDisplayValueUi("300.0", "300.0 mm²", "mm²"),
                    proposedValue = EngineeringDisplayValueUi("240.0", "240.0 mm²", "mm²"),
                    unit = "mm²"
                )
            ),
            governingCriterion = "Ampacity Iz",
            evidenceSummary = "Verified against ampacity and voltage drop evidence.",
            expectedPostApplyBehavior = "Revision will advance 1 -> 2 with post-commit revalidation."
        )
        return proposal to review
    }
}

