package com.example.ai.approval

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.proposal.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.ai.ApprovalGrantEntity
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

/**
 * GATE 10 — Trusted approval revalidation, durable mutation and post-commit revalidation.
 *
 * Runs against real Room, the real [TrustedProposalApprovalGateway] and the real
 * [DurableMutationWorkflow]; the mutation boundary is never mocked.
 *
 * Covers: successful mutation (38), stale proposal (13/40), current-value conflict (14/41),
 * no-op protection (15/45), cross-project (16/42), cross-entity (17/43), domain boundary (18),
 * cancelled approval (30/44), expired approval (31), duplicate approval exactly-once (29/46),
 * and failure atomicity (33).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Gate10TrustedMutationRevalidationTest {

    private lateinit var database: AppDatabase
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var workflow: DurableMutationWorkflow
    private lateinit var gateway: TrustedProposalApprovalGateway

    private val projectId = "PROJ-GATE10-A"
    private val otherProjectId = "PROJ-GATE10-B"
    private val circuitId = 401
    private val secondCircuitId = 402
    private val foreignCircuitId = 701
    private var currentTime = 50_000L

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = EngineeringRepository(database)

        catalogRepository = CatalogRepository().apply {
            loadAllFromStrings(
                cableCatalogJson = """
                [
                  {"mfg":"Elsewedy","typeCode":1,"size":240.0,"model":"Elsewedy Cu/XLPE 240"},
                  {"mfg":"Elsewedy","typeCode":1,"size":185.0,"model":"Elsewedy Cu/XLPE 185"}
                ]
                """.trimIndent(),
                cableTypesDataJson = """
                [
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":240.0,"rCu70":0.098,"x":0.08,"rAl70":0.0,"rCu90":0.104,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"},
                  {"typeCode":1,"cableType":"Cu/XLPE/SWA/PVC","sizeMm2":185.0,"rCu70":0.128,"x":0.08,"rAl70":0.0,"rCu90":0.134,"rAl90":0.0,"conductor":"Cu","insulation":"XLPE","armour":"SWA","cores":"4C"}
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
            executionIdProvider = { "EXEC-GATE10-DEFAULT" }
        )

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-gate10-verified" }
        )

        seed()
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        AppDatabase.resetInstanceForTesting()
        database.close()
    }

    private fun seed() = runBlocking {
        database.projectDao().upsertProject(
            ProjectEntity(projectId = projectId, projectName = "Gate 10 Project A", revision = 1L)
        )
        database.transformerDao().insertTransformer(
            TransformerEntity(
                projectId = projectId,
                transformerId = "TX-G10",
                transformerName = "Main TX",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0
            )
        )
        database.circuitDao().insertCircuit(circuit(circuitId, "Feeder 401", projectId))
        database.circuitDao().insertCircuit(circuit(secondCircuitId, "Feeder 402", projectId))

        // Foreign project + circuit used for cross-project / cross-entity coverage.
        database.projectDao().upsertProject(
            ProjectEntity(projectId = otherProjectId, projectName = "Gate 10 Project B", revision = 5L)
        )
        database.circuitDao().insertCircuit(circuit(foreignCircuitId, "Feeder 701", otherProjectId))
    }

    private fun circuit(id: Int, name: String, project: String): CircuitEntity = CircuitEntity(
        id = id,
        circuitName = name,
        projectId = project,
        transformerId = "TX-G10",
        sourceType = "TRANSFORMER",
        fedFrom = "Transformer",
        cableType = "Cu/XLPE/SWA/PVC",
        manufacturer = "Elsewedy",
        installation = "Laid in Ducts",
        parallelRuns = 1,
        length_m = 50.0,
        overrideCableSize = "240.0"
    )

    private fun proposal(
        proposalId: String,
        baseRevision: Long = 1L,
        project: String = projectId,
        entityId: Int = circuitId,
        entityType: String = "CIRCUIT",
        proposedSize: String = "185.0"
    ): CableEngineeringProposal = CableEngineeringProposal(
        proposalId = proposalId,
        projectId = project,
        baseProjectRevision = baseRevision,
        targetEntityType = entityType,
        targetEntityId = entityId.toString(),
        operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
        patch = CableCircuitPatch(overrideCableSize = PatchValue.set(proposedSize)),
        rationale = "Gate 10 verification proposal",
        sourceToolName = "ProposeCableSizingTool",
        sourceRequestId = "REQ-G10",
        createdAt = currentTime,
        expiresAt = currentTime + 600_000L
    )

    private fun context(
        project: String = projectId,
        liveRevision: Long = 1L,
        entityId: Int = circuitId
    ): BoundedProjectContext = BoundedProjectContext(
        projectId = project,
        projectName = "Gate 10 Project",
        liveRevision = liveRevision,
        activeScreen = "AssistantReview",
        activeEntityType = "CIRCUIT",
        activeEntityId = entityId.toString(),
        activeEntityName = "Feeder $entityId"
    )

    private suspend fun receiptCount(project: String = projectId): Int =
        database.mutationExecutionReceiptDao().getReceiptsForProject(project).size

    private suspend fun revision(project: String = projectId): Long? =
        database.projectDao().getProject(project)?.revision

    private suspend fun persistedSize(entityId: Int = circuitId): String? =
        database.circuitDao().getCircuitById(entityId)?.overrideCableSize

    private suspend fun intentFor(proposal: CableEngineeringProposal): ValidatedCableProposalIntent {
        val result = DefaultProposalPolicyValidator().validateCableProposal(proposal, currentTime)
        assertTrue("Fixture proposal must pass policy validation", result is ProposalPolicyResult.Success)
        return (result as ProposalPolicyResult.Success).validatedIntent
    }

    // =========================================================================
    // 38. Successful mutation: Room value, revision advance, receipt, revalidation
    // =========================================================================
    @Test
    fun successfulApproval_commitsValueReceiptAndRevisionAdvance() = runBlocking {
        val proposal = proposal("PROP-G10-SUCCESS")
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(),
            userActionId = "UA-G10-SUCCESS",
            approvalId = "APP-G10-SUCCESS",
            expectedCurrentValues = mapOf("overrideCableSize" to "240.0")
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue("Expected Success, got $result", result is TrustedApprovalGateResult.Success)
        val outcome = (result as TrustedApprovalGateResult.Success).outcome
        assertTrue("Expected a committed outcome, got $outcome", outcome is DurableMutationOutcome.Completed)
        val completed = outcome as DurableMutationOutcome.Completed

        // Room value committed
        assertEquals("185.0", persistedSize())
        // Revision advanced exactly one step
        assertEquals(2L, revision())
        assertEquals(1L, completed.receipt.revisionBefore)
        assertEquals(2L, completed.receipt.revisionAfter)
        // Real receipt created and bound to the proposal
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(1, receipts.size)
        assertEquals(proposal.proposalId, receipts.first().proposalId)
        assertEquals(circuitId.toString(), receipts.first().targetEntityId)
        assertTrue(receipts.first().appliedChangesJson.contains("overrideCableSize"))
        // Post-commit revalidation executed and recorded
        assertEquals(RevalidationStatus.SUCCEEDED, receipts.first().revalidationStatus)
        // The receipt carries the typed before/after values of the committed change
        val sizeChange = completed.appliedChanges.first { it.fieldName == "overrideCableSize" }
        assertEquals("240.0", sizeChange.previousValue?.stringValue)
        assertEquals("185.0", sizeChange.newValue?.stringValue)
        // Approval grant consumed exactly once
        val grant = database.approvalGrantDao().getGrantById("APP-G10-SUCCESS")
        assertEquals(ApprovalStatus.CONSUMED, grant?.status)
        assertEquals("eng-gate10-verified", grant?.approvedBy)
    }

    // =========================================================================
    // 13 / 40. Stale proposal: authoritative live revision advanced
    // =========================================================================
    @Test
    fun authoritativeRevisionMismatch_isStaleWithoutAnyMutation() = runBlocking {
        // A concurrent actor advanced the project revision in the durable store.
        database.projectDao().incrementRevision(projectId)

        // Caller projection still claims the old revision, so only the authoritative
        // approval-time re-resolution can detect the staleness.
        val proposal = proposal("PROP-G10-STALE")
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(liveRevision = 1L),
            userActionId = "UA-G10-STALE",
            approvalId = "APP-G10-STALE"
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue("Expected StaleProposal, got $result", result is TrustedApprovalGateResult.StaleProposal)
        val stale = result as TrustedApprovalGateResult.StaleProposal
        assertEquals(1L, stale.baseRevision)
        assertEquals(2L, stale.liveRevision)
        assertEquals(TrustedMutationRejectionCode.STALE_PROPOSAL, stale.code)

        // No mutation, no receipt, no revision increment, no grant
        assertEquals("240.0", persistedSize())
        assertEquals(2L, revision())
        assertEquals(0, receiptCount())
        assertEquals(0, database.approvalGrantDao().getGrantsForProject(projectId).size)
    }

    // =========================================================================
    // 14 / 41. Current-value conflict: persisted value changed under the proposal
    // =========================================================================
    @Test
    fun persistedValueConflict_isRejectedWithoutMutation() = runBlocking {
        // The persisted value is no longer the value the reviewed proposal was prepared against.
        val current = database.circuitDao().getCircuitById(circuitId)!!
        database.circuitDao().updateCircuit(current.copy(overrideCableSize = "250.0"))

        val proposal = proposal("PROP-G10-CONFLICT")
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(),
            userActionId = "UA-G10-CONFLICT",
            approvalId = "APP-G10-CONFLICT",
            expectedCurrentValues = mapOf("overrideCableSize" to "300.0")
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue("Expected Success wrapper, got $result", result is TrustedApprovalGateResult.Success)
        val outcome = (result as TrustedApprovalGateResult.Success).outcome
        assertTrue("Expected CurrentValueChanged, got $outcome", outcome is DurableMutationOutcome.CurrentValueChanged)
        val conflict = outcome as DurableMutationOutcome.CurrentValueChanged
        assertEquals("overrideCableSize", conflict.fieldName)
        assertEquals("300.0", conflict.expectedValue)
        assertEquals("250.0", conflict.actualValue)
        assertFalse("A precondition rejection is never a committed outcome", outcome.isCommitted)

        // No mutation, no receipt, no revision advance, and the grant was not consumed
        assertEquals("250.0", persistedSize())
        assertEquals(1L, revision())
        assertEquals(0, receiptCount())
        val grant = database.approvalGrantDao().getGrantById("APP-G10-CONFLICT")
        assertEquals(ApprovalStatus.ISSUED, grant?.status)
    }

    // =========================================================================
    // 15 / 45. No-op: proposed value is already the persisted value
    // =========================================================================
    @Test
    fun noOpPatch_producesNoMutationNoReceiptAndNoRevisionAdvance() = runBlocking {
        val proposal = proposal("PROP-G10-NOOP", proposedSize = "240.0")
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(),
            userActionId = "UA-G10-NOOP",
            approvalId = "APP-G10-NOOP",
            expectedCurrentValues = mapOf("overrideCableSize" to "240.0")
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.Success)
        val outcome = (result as TrustedApprovalGateResult.Success).outcome
        assertTrue("Expected NoOp, got $outcome", outcome is DurableMutationOutcome.NoOp)
        assertFalse("No-op must never be reported as committed", outcome.isCommitted)

        assertEquals("240.0", persistedSize())
        assertEquals(1L, revision())
        assertEquals(0, receiptCount())
    }

    // =========================================================================
    // 16 / 42. Cross-project: target entity belongs to another project
    // =========================================================================
    @Test
    fun crossProjectTarget_isRejectedWithoutAnyMutation() = runBlocking {
        val proposal = proposal(
            proposalId = "PROP-G10-CROSSPROJ",
            project = projectId,
            entityId = foreignCircuitId
        )
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(entityId = foreignCircuitId),
            userActionId = "UA-G10-CROSSPROJ",
            approvalId = "APP-G10-CROSSPROJ"
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue("Expected ValidationFailed, got $result", result is TrustedApprovalGateResult.ValidationFailed)
        assertEquals(
            TrustedMutationRejectionCode.WRONG_PROJECT,
            (result as TrustedApprovalGateResult.ValidationFailed).code
        )

        assertEquals("240.0", persistedSize(foreignCircuitId))
        assertEquals(5L, revision(otherProjectId))
        assertEquals(1L, revision(projectId))
        assertEquals(0, receiptCount(projectId))
        assertEquals(0, receiptCount(otherProjectId))
    }

    // =========================================================================
    // 17 / 43. Cross-entity: the approved entity is never a model projection
    // =========================================================================
    @Test
    fun entityBoundary_usesAuthoritativeProposalEntityOnly() = runBlocking {
        val proposal = proposal(proposalId = "PROP-G10-CROSSENTITY", entityId = secondCircuitId)
        val request = TrustedApprovalRequest(
            proposal = proposal,
            // The approval surface is scoped to circuit 401 while the proposal names 402.
            projectContext = context(entityId = circuitId),
            userActionId = "UA-G10-CROSSENTITY",
            approvalId = "APP-G10-CROSSENTITY",
            expectedCurrentValues = mapOf("overrideCableSize" to "240.0")
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue(result is TrustedApprovalGateResult.Success)

        // Only the authoritative proposal entity was mutated.
        assertEquals("185.0", persistedSize(secondCircuitId))
        assertEquals("240.0", persistedSize(circuitId))
        assertEquals(1, receiptCount())
        assertEquals(secondCircuitId.toString(), database.mutationExecutionReceiptDao()
            .getReceiptsForProject(projectId).first().targetEntityId)
    }

    // =========================================================================
    // 18. Domain boundary: a proposal cannot change its domain at approval time
    // =========================================================================
    @Test
    fun nonCircuitDomain_isRejectedFailClosed() = runBlocking {
        val proposal = proposal(proposalId = "PROP-G10-DOMAIN", entityType = "TRANSFORMER")
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(),
            userActionId = "UA-G10-DOMAIN",
            approvalId = "APP-G10-DOMAIN"
        )

        val result = gateway.processApprovalAndExecute(request)
        assertTrue("Expected ValidationFailed, got $result", result is TrustedApprovalGateResult.ValidationFailed)
        assertEquals(
            TrustedMutationRejectionCode.WRONG_ENTITY,
            (result as TrustedApprovalGateResult.ValidationFailed).code
        )
        assertEquals("240.0", persistedSize())
        assertEquals(1L, revision())
        assertEquals(0, receiptCount())
    }

    // =========================================================================
    // 30 / 44. Cancelled approval can never mutate
    // =========================================================================
    @Test
    fun cancelledApprovalGrant_cannotMutate() = runBlocking {
        val proposal = proposal("PROP-G10-CANCELLED")
        val intent = intentFor(proposal)

        val grant = ApprovalGrantEntity.createIssued(
            approvalId = "APP-G10-CANCELLED",
            proposalId = proposal.proposalId,
            proposalDigest = intent.proposalDigest,
            proposalSemanticKey = DefaultProposalSemanticKeyEncoder().computeSemanticKey(proposal),
            projectId = proposal.projectId,
            targetEntityId = proposal.targetEntityId,
            baseProjectRevision = proposal.baseProjectRevision,
            userActionId = "UA-G10-CANCELLED",
            approvedBy = "eng-gate10-verified",
            issuedAt = currentTime,
            expiresAt = currentTime + 60_000L
        )
        database.approvalGrantDao().insertGrant(grant)
        val cancelled = database.approvalGrantDao().cancelGrantGuarded("APP-G10-CANCELLED")
        assertEquals(1, cancelled)

        val outcome = workflow.execute(
            DurableMutationRequest(
                intent = intent,
                approvalId = "APP-G10-CANCELLED",
                executionId = "EXEC-G10-CANCELLED",
                expectedCurrentValues = mapOf("overrideCableSize" to "240.0")
            )
        )

        assertTrue("Expected ApprovalCancelled, got $outcome", outcome is DurableMutationOutcome.ApprovalCancelled)
        assertFalse(outcome.isCommitted)
        assertEquals("240.0", persistedSize())
        assertEquals(1L, revision())
        assertEquals(0, receiptCount())
    }

    // =========================================================================
    // 31. Expired approval can never mutate, and is never silently rebased
    // =========================================================================
    @Test
    fun expiredApprovalGrant_cannotMutate() = runBlocking {
        val proposal = proposal("PROP-G10-EXPIRED")
        val intent = intentFor(proposal)

        database.approvalGrantDao().insertGrant(
            ApprovalGrantEntity.createIssued(
                approvalId = "APP-G10-EXPIRED",
                proposalId = proposal.proposalId,
                proposalDigest = intent.proposalDigest,
                proposalSemanticKey = DefaultProposalSemanticKeyEncoder().computeSemanticKey(proposal),
                projectId = proposal.projectId,
                targetEntityId = proposal.targetEntityId,
                baseProjectRevision = proposal.baseProjectRevision,
                userActionId = "UA-G10-EXPIRED",
                approvedBy = "eng-gate10-verified",
                issuedAt = 1_000L,
                expiresAt = 2_000L
            )
        )

        val outcome = workflow.execute(
            DurableMutationRequest(
                intent = intent,
                approvalId = "APP-G10-EXPIRED",
                executionId = "EXEC-G10-EXPIRED",
                expectedCurrentValues = mapOf("overrideCableSize" to "240.0")
            )
        )

        assertTrue("Expected ApprovalExpired, got $outcome", outcome is DurableMutationOutcome.ApprovalExpired)
        assertEquals("240.0", persistedSize())
        assertEquals(1L, revision())
        assertEquals(0, receiptCount())
        assertEquals(ApprovalStatus.EXPIRED, database.approvalGrantDao().getGrantById("APP-G10-EXPIRED")?.status)
    }

    // =========================================================================
    // 29 / 46. Duplicate approval executes exactly once
    // =========================================================================
    @Test
    fun duplicateApproval_executesExactlyOneMutationAndOneReceipt() = runBlocking {
        val proposal = proposal("PROP-G10-DUPLICATE")
        val expected = mapOf("overrideCableSize" to "240.0")

        val first = gateway.processApprovalAndExecute(
            TrustedApprovalRequest(
                proposal = proposal,
                projectContext = context(),
                userActionId = "UA-G10-DUP-1",
                approvalId = "APP-G10-DUP-1",
                expectedCurrentValues = expected
            )
        )
        assertTrue(first is TrustedApprovalGateResult.Success)
        assertTrue((first as TrustedApprovalGateResult.Success).outcome is DurableMutationOutcome.Completed)

        // Second explicit approval of the same proposal signature (e.g. double tap / retry).
        val second = gateway.processApprovalAndExecute(
            TrustedApprovalRequest(
                proposal = proposal,
                projectContext = context(),
                userActionId = "UA-G10-DUP-2",
                approvalId = "APP-G10-DUP-2",
                expectedCurrentValues = expected
            )
        )
        assertTrue("Expected Success wrapper, got $second", second is TrustedApprovalGateResult.Success)
        val secondOutcome = (second as TrustedApprovalGateResult.Success).outcome
        assertTrue(
            "Duplicate approval must return the existing receipt, got $secondOutcome",
            secondOutcome is DurableMutationOutcome.ExistingReceiptReturned
        )

        // Exactly one mutation effect: one receipt, one revision advance, one consumed grant
        assertEquals("185.0", persistedSize())
        assertEquals(2L, revision())
        assertEquals(1, receiptCount())
        val grants = database.approvalGrantDao().getGrantsForProject(projectId)
        assertEquals(1, grants.size)
        assertEquals(1, grants.count { it.status == ApprovalStatus.CONSUMED })
    }

    // =========================================================================
    // 29 / 46. Concurrent duplicate approval race: exactly one mutation and one receipt
    // =========================================================================
    @Test
    fun concurrentDuplicateApproval_executesExactlyOneMutationAndOneReceipt() = runBlocking {
        val proposal = proposal("PROP-G10-CONC-DUP")
        val expected = mapOf("overrideCableSize" to "240.0")

        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val readyLatch = java.util.concurrent.CountDownLatch(2)
        val startLatch = java.util.concurrent.CountDownLatch(1)

        val task1 = executor.submit<TrustedApprovalGateResult> {
            readyLatch.countDown()
            startLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            runBlocking {
                gateway.processApprovalAndExecute(
                    TrustedApprovalRequest(
                        proposal = proposal,
                        projectContext = context(),
                        userActionId = "UA-G10-CONC-1",
                        approvalId = "APP-G10-CONC-1",
                        expectedCurrentValues = expected
                    )
                )
            }
        }

        val task2 = executor.submit<TrustedApprovalGateResult> {
            readyLatch.countDown()
            startLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            runBlocking {
                gateway.processApprovalAndExecute(
                    TrustedApprovalRequest(
                        proposal = proposal,
                        projectContext = context(),
                        userActionId = "UA-G10-CONC-2",
                        approvalId = "APP-G10-CONC-2",
                        expectedCurrentValues = expected
                    )
                )
            }
        }

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        startLatch.countDown()

        val res1 = task1.get(10, java.util.concurrent.TimeUnit.SECONDS)
        val res2 = task2.get(10, java.util.concurrent.TimeUnit.SECONDS)
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
        // 1. Room persisted value updated exactly to 185.0
        assertEquals("185.0", persistedSize())
        // 2. Exactly one revision increment (1L -> 2L)
        assertEquals(2L, revision())
        // 3. Exactly one receipt created
        assertEquals(1, receiptCount())
        // 4. Exactly one grant consumed
        val grants = database.approvalGrantDao().getGrantsForProject(projectId)
        assertEquals(1, grants.count { it.status == ApprovalStatus.CONSUMED })
    }

    // =========================================================================
    // 33. Failure atomicity: no partial write, no false receipt, retry state preserved
    // =========================================================================
    @Test
    fun rejectedApproval_retainsNoPartialState() = runBlocking {
        val current = database.circuitDao().getCircuitById(circuitId)!!
        database.circuitDao().updateCircuit(current.copy(overrideCableSize = "250.0"))

        val proposal = proposal("PROP-G10-ATOMIC")
        val request = TrustedApprovalRequest(
            proposal = proposal,
            projectContext = context(),
            userActionId = "UA-G10-ATOMIC",
            approvalId = "APP-G10-ATOMIC",
            expectedCurrentValues = mapOf("overrideCableSize" to "300.0")
        )

        val result = gateway.processApprovalAndExecute(request)
        val outcome = (result as? TrustedApprovalGateResult.Success)?.outcome
        assertTrue("Expected CurrentValueChanged, got $result", outcome is DurableMutationOutcome.CurrentValueChanged)

        // No partial value change, no false revision advance, no false receipt
        assertEquals("250.0", persistedSize())
        assertEquals(1L, revision())
        assertEquals(0, receiptCount())
        assertEquals(0, database.mutationExecutionReceiptDao().getReceiptsForProject(projectId).count {
            it.revalidationStatus == RevalidationStatus.SUCCEEDED
        })
        // The approval grant remains usable/reviewable: the request stays in a retry state
        assertEquals(ApprovalStatus.ISSUED, database.approvalGrantDao().getGrantById("APP-G10-ATOMIC")?.status)
    }
}
