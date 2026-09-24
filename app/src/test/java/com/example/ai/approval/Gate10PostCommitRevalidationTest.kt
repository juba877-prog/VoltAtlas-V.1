package com.example.ai.approval

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.ai.proposal.*
import com.example.ai.runtime.BoundedProjectContext
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import com.example.engine.coordinator.GuardedCalculationResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE 10 — Post-commit revalidation and revision advance.
 *
 * Covers Section 24 (post-commit revalidation), 25 (revision advance), 34 (post-commit failure is
 * distinguished from mutation failure), 35 (evidence after mutation) and 47 (mandatory post-commit
 * revalidation test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Gate10PostCommitRevalidationTest {

    private lateinit var database: AppDatabase
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var workflow: DurableMutationWorkflow
    private lateinit var gateway: TrustedProposalApprovalGateway

    private val projectId = "PROJ-GATE10-POST"
    private val circuitId = 505
    private var currentTime = 90_000L

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
            executionIdProvider = { "EXEC-GATE10-POST" }
        )

        gateway = TrustedProposalApprovalGateway(
            approvalGrantDao = database.approvalGrantDao(),
            durableWorkflow = workflow,
            clock = { currentTime },
            principalProvider = DefaultTrustedPrincipalProvider { "eng-gate10-post" }
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "Gate 10 Post-Commit", revision = 1L)
            )
            database.transformerDao().insertTransformer(
                TransformerEntity(
                    projectId = projectId,
                    transformerId = "TX-POST",
                    transformerName = "Main TX",
                    kVA = 1000.0,
                    voltage = 400,
                    percentZ = 5.0
                )
            )
            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = circuitId,
                    circuitName = "Feeder 505",
                    projectId = projectId,
                    transformerId = "TX-POST",
                    sourceType = "TRANSFORMER",
                    fedFrom = "Transformer",
                    cableType = "Cu/XLPE/SWA/PVC",
                    manufacturer = "Elsewedy",
                    installation = "Laid in Ducts",
                    parallelRuns = 1,
                    length_m = 50.0,
                    loadKVA = 200.0,
                    voltageV = 400,
                    pf = 0.85,
                    maxVDpct = 0.03,
                    overrideCableSize = "240.0"
                )
            )
        }
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        AppDatabase.useTestExecutors = false
        database.close()
    }

    private fun proposal(proposalId: String): CableEngineeringProposal = CableEngineeringProposal(
        proposalId = proposalId,
        projectId = projectId,
        baseProjectRevision = 1L,
        targetEntityType = "CIRCUIT",
        targetEntityId = circuitId.toString(),
        operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
        patch = CableCircuitPatch(overrideCableSize = PatchValue.set("185.0")),
        rationale = "Gate 10 post-commit verification proposal",
        sourceToolName = "ProposeCableSizingTool",
        sourceRequestId = "REQ-G10-POST",
        createdAt = currentTime,
        expiresAt = currentTime + 600_000L
    )

    private fun context(): BoundedProjectContext = BoundedProjectContext(
        projectId = projectId,
        projectName = "Gate 10 Post-Commit",
        liveRevision = 1L,
        activeScreen = "AssistantReview",
        activeEntityType = "CIRCUIT",
        activeEntityId = circuitId.toString(),
        activeEntityName = "Feeder 505"
    )

    private suspend fun approve(proposalId: String, userActionId: String): DurableMutationOutcome {
        val result = gateway.processApprovalAndExecute(
            TrustedApprovalRequest(
                proposal = proposal(proposalId),
                projectContext = context(),
                userActionId = userActionId,
                approvalId = "APP-$proposalId",
                expectedCurrentValues = mapOf("overrideCableSize" to "240.0")
            )
        )
        assertTrue("Expected Success wrapper, got $result", result is TrustedApprovalGateResult.Success)
        return (result as TrustedApprovalGateResult.Success).outcome
    }

    // =========================================================================
    // 24 / 25 / 35 / 47. Revalidation refreshes current evidence, preserves history
    // =========================================================================
    @Test
    fun postCommit_revisionAdvances_refreshesCurrentEvidence_andPreservesHistory() = runBlocking {
        val coordinator = EngineeringCalculationCoordinator
        val transformers = database.transformerDao().getTransformersListForProject(projectId)
        val preCircuits = database.circuitDao().getCircuitsListForProject(projectId)

        // Pre-commit: deterministic calculation published against revision 1 (old evidence).
        val preCommit = coordinator.calculateCableSizingGuarded(
            circuit = preCircuits.first { it.id == circuitId },
            allCircuits = preCircuits,
            transformers = transformers,
            expectedRevision = 1L
        )
        assertTrue("Pre-commit calculation must publish at revision 1, got $preCommit", preCommit is GuardedCalculationResult.Published)
        assertNotNull(coordinator.getCachedSizingResult(projectId, circuitId))

        // Explicit human approval of the verified proposal -> durable mutation + revalidation.
        val outcome = approve("PROP-POST-1", "UA-POST-1")
        assertTrue("Expected Completed, got $outcome", outcome is DurableMutationOutcome.Completed)
        val completed = outcome as DurableMutationOutcome.Completed

        // Revision advanced N -> N+1
        assertEquals(1L, completed.receipt.revisionBefore)
        assertEquals(2L, completed.receipt.revisionAfter)
        assertEquals(2L, database.projectDao().getProject(projectId)?.revision)
        // Committed project value reflects the approved change
        assertEquals("185.0", database.circuitDao().getCircuitById(circuitId)?.overrideCableSize)

        // New current evidence originates from deterministic post-commit recalculation
        assertNotNull(
            "Post-commit revalidation must publish fresh current sizing evidence",
            coordinator.getCachedSizingResult(projectId, circuitId)
        )

        // Historical evidence is preserved: the committed receipt remains the durable record
        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(1, receipts.size)
        assertEquals(1L, receipts.first().revisionBefore)
        assertEquals(2L, receipts.first().revisionAfter)
        assertEquals(RevalidationStatus.SUCCEEDED, receipts.first().revalidationStatus)

        // Pre-commit (revision 1) results can never be republished as current after the commit
        val stalePublication = coordinator.calculateCableSizingGuarded(
            circuit = database.circuitDao().getCircuitById(circuitId)!!,
            allCircuits = database.circuitDao().getCircuitsListForProject(projectId),
            transformers = transformers,
            expectedRevision = 1L
        )
        assertTrue(
            "Evidence from the superseded revision must never be published as current, got $stalePublication",
            stalePublication is GuardedCalculationResult.Superseded
        )
    }

    // =========================================================================
    // 34 / 48. Post-commit revalidation failure is never reported as mutation failure
    // =========================================================================
    @Test
    fun postCommitRevalidationFailure_isDistinguishedFromMutationFailure() = runBlocking {
        // Force the authoritative revision provider to fail during post-commit recalculation.
        EngineeringCalculationCoordinator.projectRevisionProvider = object : ProjectRevisionProvider {
            override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
                throw IllegalStateException("Simulated revision provider outage during revalidation")
            }
        }

        val outcome = approve("PROP-POST-2", "UA-POST-2")

        // The mutation itself is committed: the durable change and its receipt are authoritative.
        assertTrue("Mutation must remain committed, got $outcome", outcome.isCommitted)
        assertEquals("185.0", database.circuitDao().getCircuitById(circuitId)?.overrideCableSize)
        assertEquals(2L, database.projectDao().getProject(projectId)?.revision)

        val receipts = database.mutationExecutionReceiptDao().getReceiptsForProject(projectId)
        assertEquals(1, receipts.size)
        assertEquals(2L, receipts.first().revisionAfter)
        assertNotEquals(
            "A failed post-commit revalidation must not be recorded as SUCCEEDED",
            RevalidationStatus.SUCCEEDED,
            receipts.first().revalidationStatus
        )

        // The outcome explicitly distinguishes commit truth from revalidation failure.
        assertTrue(
            "Expected a committed-with-revalidation-failure outcome, got $outcome",
            outcome is DurableMutationOutcome.CommittedRevalidationFailed ||
                outcome is DurableMutationOutcome.CommittedPartial ||
                outcome is DurableMutationOutcome.Superseded
        )
        assertFalse(
            "A revalidation failure must never be reported as a database/mutation failure",
            outcome is DurableMutationOutcome.DatabaseFailure
        )
    }
}
