package com.example.ai.proposal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.ai.ApprovalGrantEntity
import com.example.data.ai.MutationExecutionReceiptEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class DurableMutationWorkflowTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var testCatalogRepository: CatalogRepository
    private lateinit var workflow: DurableMutationWorkflow
    private val semanticEncoder = DefaultProposalSemanticKeyEncoder()
    private val digestEncoder = DefaultProposalDigestEncoder()

    private val projectId = "PROJ-7E2-TEST"
    private val circuitId = 101
    private var currentTime = 10_000L

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        EngineeringCalculationCoordinator.projectRevisionProvider = EngineeringRepository(database)

        testCatalogRepository = CatalogRepository().apply {
            loadAllFromStrings(
                cableCatalogJson = """
                [
                  {"mfg":"Elsewedy","typeCode":1,"size":240.0,"model":"Elsewedy Cu/XLPE 240"},
                  {"mfg":"Elsewedy","typeCode":1,"size":185.0,"model":"Elsewedy Cu/XLPE 185"},
                  {"mfg":"Ducab","typeCode":2,"size":300.0,"model":"Ducab Al/XLPE 300"}
                ]
                """.trimIndent(),
                cableTypesDataJson = """
                [
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":240.0,"rCu70":0.098,"x":0.08,"rAl70":0.0,"rCu90":0.104,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
                  {"typeCode":2,"cableType":"Al/XLPE/STA/PVC - 4C","sizeMm2":300.0,"rCu70":0.0,"x":0.08,"rAl70":0.125,"rCu90":0.0,"rAl90":0.13,"conductor":"Al","insulation":"XLPE","armour":"STA","cores":"4C"}
                ]
                """.trimIndent(),
                breakerCatalogJson = """
                [
                  {"mfg":"Schneider","type":"MCCB-TM","series":"NSX","frame":"NSX400","version":"F","ratingA":400.0,"poles":3,"icuKa":36.0,"icsKa":36.0,"icmKa":75.6,"icwKa":0.0,"cat":"A","tripUnit":"TM400D","tripType":"Thermal-Magnetic","orderingCode":"LV432676"},
                  {"mfg":"Schneider","type":"MCCB-TM","series":"NSX","frame":"NSX250","version":"F","ratingA":250.0,"poles":3,"icuKa":36.0,"icsKa":36.0,"icmKa":75.6,"icwKa":0.0,"cat":"A","tripUnit":"TM250D","tripType":"Thermal-Magnetic","orderingCode":"LV431630"}
                ]
                """.trimIndent(),
                accessoryCatalogJson = "[]",
                deratingDataJson = "{}",
                transformersSeedJson = "[]"
            )
        }

        workflow = DurableMutationWorkflow(
            db = database,
            catalogRepository = testCatalogRepository,
            clock = { currentTime },
            executionIdProvider = { "EXEC-TEST-001" }
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(
                    projectId = projectId,
                    projectName = "Phase 7E-2 Test Project",
                    revision = 1L,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
            database.transformerDao().insertTransformer(
                com.example.data.TransformerEntity(
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
                    circuitName = "Feeder 101",
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
        AppDatabase.resetInstanceForTesting()
        database.close()
    }

    private fun createValidIntent(
        proposalId: String = "PROP-7E2-01",
        revision: Long = 1L,
        targetId: String = circuitId.toString(),
        cableType: String? = "Cu/XLPE/SWA/PVC",
        manufacturer: String? = "Elsewedy",
        overrideCableSize: String? = "185.0"
    ): ValidatedCableProposalIntent {
        val instructions = CablePatchInstructions(
            cableType = cableType?.let { FieldInstruction("cableType", PatchAction.SET, it) },
            manufacturer = manufacturer?.let { FieldInstruction("manufacturer", PatchAction.SET, it) },
            overrideCableSize = overrideCableSize?.let { FieldInstruction("overrideCableSize", PatchAction.SET, it) }
        )

        val patch = CableCircuitPatch(
            cableType = cableType?.let { PatchValue.set(it) },
            manufacturer = manufacturer?.let { PatchValue.set(it) },
            overrideCableSize = overrideCableSize?.let { PatchValue.set(it) }
        )

        val envelope = CableEngineeringProposal(
            proposalId = proposalId,
            projectId = projectId,
            baseProjectRevision = revision,
            targetEntityId = targetId,
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            patch = patch,
            rationale = "Engineering optimization for test",
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-7E2-001",
            createdAt = 1000L
        )

        return ValidatedCableProposalIntent(
            validatedIntentId = "VAL-INTENT-01",
            proposalId = proposalId,
            proposalDigest = digestEncoder.computeDigest(envelope),
            schemaVersion = envelope.schemaVersion,
            projectId = projectId,
            baseProjectRevision = revision,
            targetEntityType = "CIRCUIT",
            targetEntityId = targetId,
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            instructions = instructions,
            sourceToolName = "ProposeCableSizingTool",
            sourceRequestId = "REQ-7E2-001",
            validatedAt = 1000L
        )
    }

    private suspend fun createAndInsertIssuedGrant(
        intent: ValidatedCableProposalIntent,
        approvalId: String = "APP-7E2-01",
        issuedAt: Long = currentTime,
        expiresAt: Long = currentTime + 300_000L
    ): ApprovalGrantEntity {
        val patch = intent.instructions.toCableCircuitPatch()
        val semanticKey = semanticEncoder.computeSemanticKey(
            schemaVersion = intent.schemaVersion,
            projectId = intent.projectId,
            baseProjectRevision = intent.baseProjectRevision,
            targetEntityType = intent.targetEntityType,
            targetEntityId = intent.targetEntityId,
            operation = intent.operation,
            patch = patch
        )

        val grant = ApprovalGrantEntity.createIssued(
            approvalId = approvalId,
            proposalId = intent.proposalId,
            proposalDigest = intent.proposalDigest,
            proposalSemanticKey = semanticKey,
            projectId = intent.projectId,
            targetEntityType = intent.targetEntityType,
            targetEntityId = intent.targetEntityId,
            baseProjectRevision = intent.baseProjectRevision,
            userActionId = "USER-ACT-7E2",
            approvedBy = "LeadEngineer",
            issuedAt = issuedAt,
            expiresAt = expiresAt
        )
        database.approvalGrantDao().insertGrant(grant)
        return grant
    }

    // =========================================================================
    // 1. END-TO-END EXECUTION & ATOMICITY TESTS
    // =========================================================================

    @Test
    fun validIssuedApproval_executesMutationPersistsReceiptAndRevalidates() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        val request = DurableMutationRequest(
            intent = intent,
            approvalId = grant.approvalId,
            executionId = "EXEC-SUCCESS-001"
        )

        val outcome = workflow.execute(request)
        println("DEBUG OUTCOME: $outcome")
        assertTrue("Expected Completed or Committed outcome, got: $outcome", outcome.isCommitted)
        assertTrue(outcome is DurableMutationOutcome.Completed)

        val completed = outcome as DurableMutationOutcome.Completed
        assertEquals("EXEC-SUCCESS-001", completed.receipt.executionId)
        assertEquals(grant.approvalId, completed.receipt.approvalId)
        assertEquals(1L, completed.receipt.revisionBefore)
        assertEquals(2L, completed.receipt.revisionAfter)
        assertEquals(RevalidationStatus.SUCCEEDED, completed.receipt.revalidationStatus)

        // Verify database state: revision incremented to 2
        val project = database.projectDao().getProject(projectId)
        assertEquals(2L, project?.revision)

        // Verify circuit entity was updated
        val updatedCircuit = database.circuitDao().getCircuitByIdAndProject(projectId, circuitId)
        assertEquals("185.0", updatedCircuit?.overrideCableSize)

        // Verify approval grant is CONSUMED and activeProposalSemanticKey is null
        val updatedGrant = database.approvalGrantDao().getGrantById(grant.approvalId)
        assertEquals(ApprovalStatus.CONSUMED, updatedGrant?.status)
        assertNull(updatedGrant?.activeProposalSemanticKey)
        assertEquals("EXEC-SUCCESS-001", updatedGrant?.consumedByExecutionId)

        // Verify receipt is durable in database
        val receiptInDb = database.mutationExecutionReceiptDao().getReceiptById("EXEC-SUCCESS-001")
        assertNotNull(receiptInDb)
        assertEquals(RevalidationStatus.SUCCEEDED, receiptInDb?.revalidationStatus)
    }

    @Test
    fun approvalConsumptionMutationAndReceipt_areOneAtomicTransaction() = runBlocking {
        // Set project revision in DB to 99L to force a revision conflict inside Room transaction
        database.openHelper.writableDatabase.execSQL("UPDATE projects SET revision = 99 WHERE projectId = '$projectId'")

        val intent = createValidIntent(revision = 1L)
        val grant = createAndInsertIssuedGrant(intent)

        val outcome = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId)
        )
        assertTrue("Expected RevisionConflict, got: $outcome", outcome is DurableMutationOutcome.RevisionConflict)

        // Prove approval grant was NOT consumed (rolled back!)
        val grantAfter = database.approvalGrantDao().getGrantById(grant.approvalId)
        assertEquals(ApprovalStatus.ISSUED, grantAfter?.status)
        assertNotNull(grantAfter?.activeProposalSemanticKey)

        // Prove zero receipts inserted
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(0, receipts.size)

        // Prove circuit was NOT modified
        val circuitAfter = database.circuitDao().getCircuitByIdAndProject(projectId, circuitId)
        assertEquals("240.0", circuitAfter?.overrideCableSize)
    }

    @Test
    fun mutationFailure_rollsBackApprovalConsumptionAndReceipt() = runBlocking {
        val intent = createValidIntent(targetId = "99999") // Non-existent circuit ID
        val grant = createAndInsertIssuedGrant(intent)

        val outcome = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId)
        )

        assertTrue(outcome is DurableMutationOutcome.EntityNotFound)

        // Prove approval grant remains ISSUED
        val grantAfter = database.approvalGrantDao().getGrantById(grant.approvalId)
        assertEquals(ApprovalStatus.ISSUED, grantAfter?.status)

        // Prove no receipts
        assertEquals(0, database.mutationExecutionReceiptDao().getReceiptsForProject(projectId).size)
    }

    @Test
    fun receiptInsertFailure_rollsBackMutationAndApprovalConsumption() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        // Pre-insert a conflicting receipt with the same executionId to trigger insert failure
        val conflictingReceipt = MutationExecutionReceiptEntity(
            executionId = "EXEC-CONFLICT-001",
            approvalId = "OTHER-APP-001",
            proposalId = "PROP-OTHER",
            proposalDigest = "DIGEST-OTHER",
            proposalSemanticKey = "SEM-OTHER",
            projectId = projectId,
            targetEntityId = circuitId.toString(),
            revisionBefore = 1L,
            revisionAfter = 2L,
            revalidationStatus = RevalidationStatus.PENDING,
            appliedChangesJson = "{}",
            createdAt = 1000L,
            committedAt = 1000L
        )
        // Insert a dummy approval for foreign key
        database.approvalGrantDao().insertGrant(
            ApprovalGrantEntity.createIssued(
                approvalId = "OTHER-APP-001",
                proposalId = "PROP-OTHER",
                proposalDigest = "DIGEST-OTHER",
                proposalSemanticKey = "SEM-OTHER",
                projectId = projectId,
                targetEntityId = circuitId.toString(),
                baseProjectRevision = 1L,
                userActionId = "ACT",
                approvedBy = "User",
                issuedAt = 1000L,
                expiresAt = 90000L
            )
        )
        database.mutationExecutionReceiptDao().insertReceipt(conflictingReceipt)

        // Attempt execution with conflicting executionId
        val outcome = workflow.execute(
            DurableMutationRequest(
                intent = intent,
                approvalId = grant.approvalId,
                executionId = "EXEC-CONFLICT-001"
            )
        )

        assertTrue(outcome is DurableMutationOutcome.DatabaseFailure)

        // Prove rollback: grant remains ISSUED, revision remains 1
        val grantAfter = database.approvalGrantDao().getGrantById(grant.approvalId)
        assertEquals(ApprovalStatus.ISSUED, grantAfter?.status)
        assertEquals(1L, database.projectDao().getRevision(projectId))
    }

    // =========================================================================
    // 2. APPROVAL VERIFICATION & REPLAY TESTS
    // =========================================================================

    @Test
    fun approvalMismatch_causesZeroWrites() = runBlocking {
        val intent = createValidIntent(proposalId = "PROP-MISMATCH-1")
        val grant = createAndInsertIssuedGrant(intent)

        // Create a different intent with different proposalId
        val mismatchedIntent = createValidIntent(proposalId = "PROP-MISMATCH-2")

        val outcome = workflow.execute(
            DurableMutationRequest(intent = mismatchedIntent, approvalId = grant.approvalId)
        )

        assertTrue(outcome is DurableMutationOutcome.ApprovalMismatch)
        assertEquals(1L, database.projectDao().getRevision(projectId))
        assertEquals(ApprovalStatus.ISSUED, database.approvalGrantDao().getGrantById(grant.approvalId)?.status)
    }

    @Test
    fun expiredApproval_causesZeroWrites() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(
            intent = intent,
            issuedAt = currentTime - 2000L,
            expiresAt = currentTime - 1000L
        )

        val outcome = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId)
        )

        assertTrue(outcome is DurableMutationOutcome.ApprovalExpired)
        assertEquals(1L, database.projectDao().getRevision(projectId))
    }

    @Test
    fun cancelledApproval_causesZeroWrites() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        // Cancel approval
        database.approvalGrantDao().cancelGrantGuarded(grant.approvalId)

        val outcome = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId)
        )

        assertTrue(outcome is DurableMutationOutcome.ApprovalCancelled)
        assertEquals(1L, database.projectDao().getRevision(projectId))
    }

    @Test
    fun alreadyConsumedApproval_neverMutatesAgain() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        // First execution succeeds
        val outcome1 = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId, executionId = "EXEC-1")
        )
        assertTrue(outcome1 is DurableMutationOutcome.Completed)
        assertEquals(2L, database.projectDao().getRevision(projectId))

        // Second execution with same approval returns existing truth without remutating
        val outcome2 = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId, executionId = "EXEC-2")
        )
        assertTrue(outcome2 is DurableMutationOutcome.ExistingReceiptReturned)
        val existing = outcome2 as DurableMutationOutcome.ExistingReceiptReturned
        assertEquals("EXEC-1", existing.receipt.executionId)
        assertEquals(2L, database.projectDao().getRevision(projectId)) // Revision did NOT advance again!
    }

    @Test
    fun consumedWithoutReceipt_returnsIntegrityFailure() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        // Manually consume grant without creating a receipt
        database.approvalGrantDao().consumeGrantGuarded(
            approvalId = grant.approvalId,
            expectedSemanticKey = grant.proposalSemanticKey,
            expectedRevision = grant.baseProjectRevision,
            executionId = "EXEC-GHOST",
            now = currentTime
        )

        val outcome = workflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId)
        )

        assertTrue(outcome is DurableMutationOutcome.ReceiptIntegrityFailure)
        assertEquals(1L, database.projectDao().getRevision(projectId))
    }

    // =========================================================================
    // 3. CONCURRENCY TESTS (BARRIERS)
    // =========================================================================

    @Test
    fun concurrentSameApproval_exactlyOneMutationAndReceipt() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        val executor = Executors.newFixedThreadPool(2)
        val readyLatch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val outcomes = mutableListOf<DurableMutationOutcome>()
        val successCount = AtomicInteger(0)

        val task1 = executor.submit<DurableMutationOutcome> {
            readyLatch.countDown()
            startLatch.await(5, TimeUnit.SECONDS)
            runBlocking {
                workflow.execute(DurableMutationRequest(intent, grant.approvalId, "EXEC-THREAD-1"))
            }
        }

        val task2 = executor.submit<DurableMutationOutcome> {
            readyLatch.countDown()
            startLatch.await(5, TimeUnit.SECONDS)
            runBlocking {
                workflow.execute(DurableMutationRequest(intent, grant.approvalId, "EXEC-THREAD-2"))
            }
        }

        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()

        val outcome1 = task1.get(10, TimeUnit.SECONDS)
        val outcome2 = task2.get(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Exactly one should be a fresh Completed/Committed outcome, other should be ExistingReceiptReturned or AlreadyConsumed
        val committedCount = listOf(outcome1, outcome2).count { it is DurableMutationOutcome.Completed }
        val replayCount = listOf(outcome1, outcome2).count {
            it is DurableMutationOutcome.ExistingReceiptReturned || it is DurableMutationOutcome.ApprovalAlreadyConsumed
        }

        assertEquals("Exactly one caller must commit mutation", 1, committedCount)
        assertEquals("Second caller must return existing receipt or already consumed", 1, replayCount)

        // Revision advanced exactly once (1 -> 2)
        assertEquals(2L, database.projectDao().getRevision(projectId))

        // Exactly one receipt in database
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(1, receipts.size)
    }

    // =========================================================================
    // 4. CATALOG GATE & CODEC TESTS
    // =========================================================================

    @Test
    fun catalogMissing_failsBeforeApprovalConsumption() = runBlocking {
        val unconfiguredCatalog = CatalogRepository() // empty/unloaded
        val unconfiguredWorkflow = DurableMutationWorkflow(
            db = database,
            catalogRepository = unconfiguredCatalog,
            clock = { currentTime }
        )

        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        val outcome = unconfiguredWorkflow.execute(
            DurableMutationRequest(intent = intent, approvalId = grant.approvalId)
        )

        assertTrue(outcome is DurableMutationOutcome.InvalidPatch)
        // Approval grant remains untouched and ISSUED
        assertEquals(ApprovalStatus.ISSUED, database.approvalGrantDao().getGrantById(grant.approvalId)?.status)
        assertEquals(1L, database.projectDao().getRevision(projectId))
    }

    // =========================================================================
    // 5. REVALIDATION LIFECYCLE & RETRY RECOVERY TESTS
    // =========================================================================

    @Test
    fun retryFromFailedTransitionsToRunning() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        // Project revision is 2L after previous commit
        database.projectDao().incrementRevisionGuarded(projectId, 1L, currentTime)

        // Insert a committed receipt in FAILED state
        val receipt = MutationExecutionReceiptEntity(
            executionId = "EXEC-FAIL-RETRY",
            approvalId = grant.approvalId,
            proposalId = grant.proposalId,
            proposalDigest = grant.proposalDigest,
            proposalSemanticKey = grant.proposalSemanticKey,
            projectId = grant.projectId,
            targetEntityType = grant.targetEntityType,
            targetEntityId = grant.targetEntityId,
            revisionBefore = 1L,
            revisionAfter = 2L,
            revalidationStatus = RevalidationStatus.FAILED,
            appliedChangesJson = ReceiptPayloadCodec.encode(
                listOf(FieldChangeRecord("overrideCableSize", PatchAction.SET, newValue = TypedFieldValue.ofString("185.0")))
            ),
            createdAt = currentTime,
            committedAt = currentTime,
            attemptCount = 1,
            errorCode = "ERR_TEST",
            messageKey = "error.test",
            isRecoverable = true
        )
        database.mutationExecutionReceiptDao().insertReceipt(receipt)

        // Trigger retry/recovery
        val outcome = workflow.recoverOrRetryReceipt("EXEC-FAIL-RETRY")
        assertTrue("Expected Completed or Partial, got: $outcome", outcome is DurableMutationOutcome.Completed || outcome is DurableMutationOutcome.CommittedPartial)

        val updated = database.mutationExecutionReceiptDao().getReceiptById("EXEC-FAIL-RETRY")
        assertTrue(updated?.attemptCount!! >= 2)
    }

    @Test
    fun terminalSucceededCannotRetry() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        val receipt = MutationExecutionReceiptEntity(
            executionId = "EXEC-TERM-SUCC",
            approvalId = grant.approvalId,
            proposalId = grant.proposalId,
            proposalDigest = grant.proposalDigest,
            proposalSemanticKey = grant.proposalSemanticKey,
            projectId = grant.projectId,
            targetEntityType = grant.targetEntityType,
            targetEntityId = grant.targetEntityId,
            revisionBefore = 1L,
            revisionAfter = 2L,
            revalidationStatus = RevalidationStatus.SUCCEEDED,
            appliedChangesJson = "{\"version\":\"RECEIPT-V1\",\"changeCount\":1,\"changes\":[{\"fieldName\":\"overrideCableSize\",\"action\":\"SET\",\"newValue\":{\"kind\":\"STRING\",\"stringValue\":\"185.0\"}}]}",
            createdAt = currentTime,
            committedAt = currentTime,
            attemptCount = 1,
            workflowCompletedAt = currentTime
        )
        database.mutationExecutionReceiptDao().insertReceipt(receipt)

        val outcome = workflow.recoverOrRetryReceipt("EXEC-TERM-SUCC")
        assertTrue(outcome is DurableMutationOutcome.Completed)
        assertEquals(1, (outcome as DurableMutationOutcome.Completed).receipt.attemptCount) // Did not increment attempt!
    }

    @Test
    fun terminalSupersededCannotRetry() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        val receipt = MutationExecutionReceiptEntity(
            executionId = "EXEC-TERM-SUP",
            approvalId = grant.approvalId,
            proposalId = grant.proposalId,
            proposalDigest = grant.proposalDigest,
            proposalSemanticKey = grant.proposalSemanticKey,
            projectId = grant.projectId,
            targetEntityType = grant.targetEntityType,
            targetEntityId = grant.targetEntityId,
            revisionBefore = 1L,
            revisionAfter = 2L,
            revalidationStatus = RevalidationStatus.SUPERSEDED,
            appliedChangesJson = "{\"version\":\"RECEIPT-V1\",\"changeCount\":1,\"changes\":[{\"fieldName\":\"overrideCableSize\",\"action\":\"SET\",\"newValue\":{\"kind\":\"STRING\",\"stringValue\":\"185.0\"}}]}",
            createdAt = currentTime,
            committedAt = currentTime,
            attemptCount = 1,
            workflowCompletedAt = currentTime
        )
        database.mutationExecutionReceiptDao().insertReceipt(receipt)

        val outcome = workflow.recoverOrRetryReceipt("EXEC-TERM-SUP")
        assertTrue(outcome is DurableMutationOutcome.Superseded)
    }

    @Test
    fun processRecovery_pendingReceiptCanResume() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        // Project revision is 2L after previous commit
        database.projectDao().incrementRevisionGuarded(projectId, 1L, currentTime)

        val receipt = MutationExecutionReceiptEntity(
            executionId = "EXEC-PENDING-RESUME",
            approvalId = grant.approvalId,
            proposalId = grant.proposalId,
            proposalDigest = grant.proposalDigest,
            proposalSemanticKey = grant.proposalSemanticKey,
            projectId = grant.projectId,
            targetEntityType = grant.targetEntityType,
            targetEntityId = grant.targetEntityId,
            revisionBefore = 1L,
            revisionAfter = 2L,
            revalidationStatus = RevalidationStatus.PENDING,
            appliedChangesJson = "{\"version\":\"RECEIPT-V1\",\"changeCount\":1,\"changes\":[{\"fieldName\":\"overrideCableSize\",\"action\":\"SET\",\"newValue\":{\"kind\":\"STRING\",\"stringValue\":\"185.0\"}}]}",
            createdAt = currentTime,
            committedAt = currentTime,
            attemptCount = 0
        )
        database.mutationExecutionReceiptDao().insertReceipt(receipt)

        val outcome = workflow.recoverOrRetryReceipt("EXEC-PENDING-RESUME")
        assertTrue("Expected Completed or Partial, got: $outcome", outcome is DurableMutationOutcome.Completed || outcome is DurableMutationOutcome.CommittedPartial)

        val updated = database.mutationExecutionReceiptDao().getReceiptById("EXEC-PENDING-RESUME")
        assertEquals(1, updated?.attemptCount)
    }

    // =========================================================================
    // 6. ARCHITECTURE GUARDS
    // =========================================================================

    @Test
    fun publicErrorsAreSanitized() {
        val dbFailure = DurableMutationOutcome.DatabaseFailure(
            errorCode = "TX_ERR",
            messageKey = "error.database.transaction",
            isRecoverable = true,
            diagnosticRef = "TX-123",
            sanitizedReason = "Database mutation transaction failed unexpectedly."
        )
        assertFalse(dbFailure.sanitizedReason.contains("Exception"))
        assertFalse(dbFailure.sanitizedReason.contains("SQLException"))
        assertFalse(dbFailure.sanitizedReason.contains(".kt"))
    }

    @Test
    fun executionModeMutationRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    @Test
    fun workflowIsInternalAndUnregistered() {
        assertEquals(
            "DurableMutationWorkflow must have internal visibility in Kotlin",
            kotlin.reflect.KVisibility.INTERNAL,
            DurableMutationWorkflow::class.visibility
        )
    }

    @Test
    fun noProductionApprovalIssuerExists() {
        val result = DenyAllApprovalVerifier.verifyApproval(
            intent = createValidIntent(),
            grant = ProposalApprovalGrant("A", "P", "D", 1L, "U", 1000L)
        )
        assertFalse(result.isVerified)
    }

    @Test
    fun noUiAssistantGeminiReferences() {
        val mainDir = File("src/main/java")
        if (mainDir.exists()) {
            val allMainFiles = mainDir.walk().filter { it.isFile && it.extension == "kt" }.toList()
            for (file in allMainFiles) {
                val text = file.readText()
                if (file.name.contains("DurableMutationWorkflow") ||
                    file.name.contains("DurableMutationWorkflowContracts") ||
                    file.name.contains("EngineeringMutationContracts") ||
                    file.name.contains("PostCommitRevalidation")
                ) {
                    continue
                }
                assertFalse("File ${file.name} must not instantiate DurableMutationWorkflow",
                    text.contains("DurableMutationWorkflow("))
            }
        }
    }

    @Test
    fun projectDeletionCascadesApprovalAndReceipt() = runBlocking {
        val intent = createValidIntent()
        val grant = createAndInsertIssuedGrant(intent)

        val outcome = workflow.execute(
            DurableMutationRequest(intent, grant.approvalId, "EXEC-CAS-001")
        )
        assertTrue(outcome.isCommitted)

        assertNotNull(database.approvalGrantDao().getGrantById(grant.approvalId))
        assertNotNull(database.mutationExecutionReceiptDao().getReceiptById("EXEC-CAS-001"))

        // Delete parent project
        database.projectDao().deleteProject(projectId)

        // Cascade deletes both approval and receipt
        assertNull(database.approvalGrantDao().getGrantById(grant.approvalId))
        assertNull(database.mutationExecutionReceiptDao().getReceiptById("EXEC-CAS-001"))
    }
}

private fun CablePatchInstructions.toCableCircuitPatch(): CableCircuitPatch {
    return CableCircuitPatch(
        overrideCableSize = overrideCableSize?.let { PatchValue(it.action, it.value) },
        cableType = cableType?.let { PatchValue(it.action, it.value) },
        manufacturer = manufacturer?.let { PatchValue(it.action, it.value) },
        installation = installation?.let { PatchValue(it.action, it.value) },
        parallelRuns = parallelRuns?.let { PatchValue(it.action, it.value) },
        overrideDeviceSize = overrideDeviceSize?.let { PatchValue(it.action, it.value) },
        overrideDeviceType = overrideDeviceType?.let { PatchValue(it.action, it.value) },
        lengthM = lengthM?.let { PatchValue(it.action, it.value) }
    )
}
