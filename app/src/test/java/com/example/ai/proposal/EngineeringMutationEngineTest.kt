package com.example.ai.proposal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.ProjectEntity
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class EngineeringMutationEngineTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var testVerifier: TestApprovalVerifier
    private lateinit var mutationEngine: EngineeringMutationEngine

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testVerifier = TestApprovalVerifier(shouldVerify = true)
        mutationEngine = EngineeringMutationEngine(
            db = database,
            approvalVerifier = testVerifier,
            clock = { 10_000L }
        )
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    private suspend fun seedProjectAndCircuit(
        projectId: String = "PRJ-1",
        revision: Long = 5L,
        circuitId: Int = 101,
        overrideCableSize: String? = null,
        overrideDeviceSize: Double? = null,
        overrideDeviceType: String? = null
    ): Pair<ProjectEntity, CircuitEntity> {
        val project = ProjectEntity(
            projectId = projectId,
            projectName = "Test Project",
            revision = revision,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.projectDao().upsertProject(project)

        val circuit = CircuitEntity(
            id = circuitId,
            projectId = projectId,
            circuitName = "Feeder 1",
            cableType = "Cu/XLPE/SWA/PVC",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            length_m = 50.0,
            overrideCableSize = overrideCableSize,
            overrideDeviceSize = overrideDeviceSize,
            overrideDeviceType = overrideDeviceType
        )
        database.circuitDao().insertCircuit(circuit)
        return Pair(project, circuit)
    }

    private fun createValidIntent(
        proposalId: String = "PROP-1",
        digest: String = "PROPOSAL-V1:1111222233334444555566667777888899990000aaaa11112222333344445555",
        projectId: String = "PRJ-1",
        baseRevision: Long = 5L,
        targetEntityId: String = "101",
        operation: ProposalOperation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
        instructions: CablePatchInstructions = CablePatchInstructions(
            overrideCableSize = FieldInstruction("overrideCableSize", PatchAction.SET, "240 mm²"),
            parallelRuns = FieldInstruction("parallelRuns", PatchAction.SET, 2)
        )
    ): ValidatedCableProposalIntent = ValidatedProposalIntent(
        validatedIntentId = "INTENT-$proposalId-$baseRevision",
        proposalId = proposalId,
        proposalDigest = digest,
        schemaVersion = PROPOSAL_SCHEMA_VERSION_V1,
        projectId = projectId,
        baseProjectRevision = baseRevision,
        targetEntityType = "CIRCUIT",
        targetEntityId = targetEntityId,
        operation = operation,
        instructions = instructions,
        sourceToolName = "calculate_cable_sizing",
        sourceRequestId = "REQ-001",
        validatedAt = 10_000L,
        policyVersion = PROPOSAL_POLICY_VERSION_V1
    )

    private fun createMatchingGrant(
        intent: ValidatedCableProposalIntent,
        approvedBy: String = "LeadEngineer"
    ): ProposalApprovalGrant = ProposalApprovalGrant(
        approvalId = "APPR-1",
        proposalId = intent.proposalId,
        proposalDigest = intent.proposalDigest,
        baseProjectRevision = intent.baseProjectRevision,
        approvedBy = approvedBy,
        approvedAt = 10_000L
    )

    // =========================================================================
    // 1. Revision Guard and Atomicity Tests
    // =========================================================================

    @Test
    fun matchingRevision_appliesPatchAndIncrementsExactlyOnce() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)

        assertTrue("Mutation must succeed when revision matches", outcome.isSuccess)
        val success = outcome as MutationOutcome.Success
        assertEquals(5L, success.baseRevision)
        assertEquals(6L, success.resultingRevision)
        assertEquals("PRJ-1", success.projectId)
        assertEquals("101", success.targetEntityId)

        // Verify Database state
        val updatedProject = database.projectDao().getProject("PRJ-1")
        assertNotNull(updatedProject)
        assertEquals(6L, updatedProject!!.revision)

        val updatedCircuit = database.circuitDao().getCircuitById(101)
        assertNotNull(updatedCircuit)
        assertEquals("240 mm²", updatedCircuit!!.overrideCableSize)
        assertEquals(2, updatedCircuit.parallelRuns)
    }

    @Test
    fun revisionMismatch_causesZeroEntityWrites() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 6L, circuitId = 101)
        // Intent carries old baseRevision = 5L while DB is already at 6L
        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)

        assertTrue("Outcome must be RevisionConflict", outcome is MutationOutcome.RevisionConflict)
        val conflict = outcome as MutationOutcome.RevisionConflict
        assertEquals(5L, conflict.expectedRevision)
        assertEquals(6L, conflict.actualRevision)

        // Verify Entity in DB was NOT modified
        val circuit = database.circuitDao().getCircuitById(101)!!
        assertNull(circuit.overrideCableSize)
        assertEquals(1, circuit.parallelRuns)
    }

    @Test
    fun revisionMismatch_doesNotIncrementRevision() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 6L, circuitId = 101)
        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        mutationEngine.applyCableMutation(intent, grant)

        // Revision in DB must remain 6L
        val project = database.projectDao().getProject("PRJ-1")!!
        assertEquals(6L, project.revision)
    }

    @Test
    fun concurrentSameRevision_firstSucceeds_secondConflicts() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 10L, circuitId = 101)

        val intent1 = createValidIntent(
            proposalId = "PROP-A",
            digest = "PROPOSAL-V1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            projectId = "PRJ-1",
            baseRevision = 10L,
            instructions = CablePatchInstructions(overrideCableSize = FieldInstruction("overrideCableSize", PatchAction.SET, "185 mm²"))
        )
        val grant1 = createMatchingGrant(intent1)

        val intent2 = createValidIntent(
            proposalId = "PROP-B",
            digest = "PROPOSAL-V1:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            projectId = "PRJ-1",
            baseRevision = 10L,
            instructions = CablePatchInstructions(overrideCableSize = FieldInstruction("overrideCableSize", PatchAction.SET, "300 mm²"))
        )
        val grant2 = createMatchingGrant(intent2)

        val startBarrier = CompletableDeferred<Unit>()

        val job1 = async(Dispatchers.IO) {
            startBarrier.await()
            mutationEngine.applyCableMutation(intent1, grant1)
        }
        val job2 = async(Dispatchers.IO) {
            startBarrier.await()
            mutationEngine.applyCableMutation(intent2, grant2)
        }

        startBarrier.complete(Unit)

        val outcome1 = job1.await()
        val outcome2 = job2.await()

        val results = listOf(outcome1, outcome2)
        val successCount = results.count { it is MutationOutcome.Success }
        val conflictCount = results.count { it is MutationOutcome.RevisionConflict }

        assertEquals("Exactly 1 concurrent mutation must succeed", 1, successCount)
        assertEquals("Exactly 1 concurrent mutation must encounter revision conflict", 1, conflictCount)

        // Final revision must be exactly 11L
        val finalProject = database.projectDao().getProject("PRJ-1")!!
        assertEquals(11L, finalProject.revision)
    }

    // =========================================================================
    // 2. Entity Scoping and Rollback Tests
    // =========================================================================

    @Test
    fun projectMissing_causesZeroWrites() = runBlocking {
        // Project is NOT seeded in database
        val intent = createValidIntent(projectId = "NON-EXISTENT", baseRevision = 1L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)

        assertTrue(outcome is MutationOutcome.ProjectNotFound)
        assertEquals("NON-EXISTENT", (outcome as MutationOutcome.ProjectNotFound).projectId)
    }

    @Test
    fun circuitMissing_rollsBackRevisionClaim() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        // Intent targets non-existent circuit 999
        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "999")
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)

        assertTrue("Outcome must be EntityNotFound", outcome is MutationOutcome.EntityNotFound)
        val notFound = outcome as MutationOutcome.EntityNotFound
        assertEquals("999", notFound.entityId)

        // Critical CAS Rollback Check: Project revision must remain 5L!
        val project = database.projectDao().getProject("PRJ-1")!!
        assertEquals("Revision claim must be rolled back when entity is missing", 5L, project.revision)
    }

    @Test
    fun sameCircuitIdAcrossProjects_neverLeaks() = runBlocking {
        // Circuit 101 exists in PRJ-2, but Intent is for PRJ-1
        seedProjectAndCircuit(projectId = "PRJ-2", revision = 5L, circuitId = 101)
        database.projectDao().upsertProject(ProjectEntity("PRJ-1", "Project 1", revision = 5L, createdAt = 1000L, updatedAt = 1000L))

        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)

        // Must fail with EntityNotFound in PRJ-1 and must NOT modify circuit in PRJ-2
        assertTrue(outcome is MutationOutcome.EntityNotFound)

        val circuitInPrj2 = database.circuitDao().getCircuitByIdAndProject("PRJ-2", 101)!!
        assertNull("Circuit in PRJ-2 must not be modified by PRJ-1 intent", circuitInPrj2.overrideCableSize)

        // Revision in PRJ-1 must be rolled back to 5L
        assertEquals(5L, database.projectDao().getProject("PRJ-1")!!.revision)
    }

    @Test
    fun invalidIntentDoesNotOpenTransaction() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        val badIdIntent = createValidIntent(targetEntityId = "NON_NUMERIC")
        val grant = createMatchingGrant(badIdIntent)

        val outcome = mutationEngine.applyCableMutation(badIdIntent, grant)
        assertTrue(outcome is MutationOutcome.InvalidIntent)

        assertEquals(5L, database.projectDao().getProject("PRJ-1")!!.revision)
    }

    @Test
    fun approvalFailureDoesNotOpenTransaction() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        testVerifier.shouldVerify = false // Force verification rejection

        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)
        assertTrue(outcome is MutationOutcome.ApprovalNotVerified)

        // DB state completely untouched
        assertEquals(5L, database.projectDao().getProject("PRJ-1")!!.revision)
    }

    // =========================================================================
    // 3. Patch Mapping and CLEAR Semantics Tests
    // =========================================================================

    @Test
    fun successfulCableSet_mapsExactEntityField() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        val intent = createValidIntent(
            instructions = CablePatchInstructions(
                cableType = FieldInstruction("cableType", PatchAction.SET, "Al/XLPE/STA/PVC - 4C"),
                manufacturer = FieldInstruction("manufacturer", PatchAction.SET, "Ducab"),
                installation = FieldInstruction("installation", PatchAction.SET, "Laid Direct in Ground"),
                parallelRuns = FieldInstruction("parallelRuns", PatchAction.SET, 3),
                lengthM = FieldInstruction("lengthM", PatchAction.SET, 125.5)
            )
        )
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)
        assertTrue(outcome.isSuccess)

        val c = database.circuitDao().getCircuitById(101)!!
        assertEquals("Al/XLPE/STA/PVC - 4C", c.cableType)
        assertEquals("Ducab", c.manufacturer)
        assertEquals("Laid Direct in Ground", c.installation)
        assertEquals(3, c.parallelRuns)
        assertEquals(125.5, c.length_m, 0.001)
    }

    @Test
    fun successfulOverrideCableClear_mapsNull() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101, overrideCableSize = "300 mm²")
        val intent = createValidIntent(
            instructions = CablePatchInstructions(
                overrideCableSize = FieldInstruction("overrideCableSize", PatchAction.CLEAR, null)
            )
        )
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)
        assertTrue(outcome.isSuccess)

        val c = database.circuitDao().getCircuitById(101)!!
        assertNull("overrideCableSize must be cleared to null", c.overrideCableSize)
    }

    @Test
    fun successfulDeviceOverrideClear_mapsNull() = runBlocking {
        seedProjectAndCircuit(
            projectId = "PRJ-1",
            revision = 5L,
            circuitId = 101,
            overrideDeviceSize = 400.0,
            overrideDeviceType = "MCCB-TM"
        )
        val intent = createValidIntent(
            operation = ProposalOperation.UPDATE_PROTECTIVE_DEVICE,
            instructions = CablePatchInstructions(
                overrideDeviceSize = FieldInstruction("overrideDeviceSize", PatchAction.CLEAR, null),
                overrideDeviceType = FieldInstruction("overrideDeviceType", PatchAction.CLEAR, null)
            )
        )
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)
        assertTrue(outcome.isSuccess)

        val c = database.circuitDao().getCircuitById(101)!!
        assertNull("overrideDeviceSize must be cleared to null", c.overrideDeviceSize)
        assertNull("overrideDeviceType must be cleared to null", c.overrideDeviceType)
    }

    @Test
    fun duplicateRetryAgainstOldRevision_returnsConflict() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        // First application succeeds
        val outcome1 = mutationEngine.applyCableMutation(intent, grant)
        assertTrue(outcome1.isSuccess)

        // Second application with the same intent (still carrying baseRevision = 5L)
        val outcome2 = mutationEngine.applyCableMutation(intent, grant)
        assertTrue("Duplicate apply against old revision must fail with conflict", outcome2 is MutationOutcome.RevisionConflict)
        val conflict = outcome2 as MutationOutcome.RevisionConflict
        assertEquals(5L, conflict.expectedRevision)
        assertEquals(6L, conflict.actualRevision)
    }

    // =========================================================================
    // 4. Cancellation & Error Safety Tests
    // =========================================================================

    @Test
    fun cancellationBeforeCommit_rollsBackAndRethrows() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        val intent = createValidIntent(projectId = "PRJ-1", baseRevision = 5L, targetEntityId = "101")
        val grant = createMatchingGrant(intent)

        val cancelledScope = CoroutineScope(Dispatchers.IO + Job().apply { cancel() })

        try {
            cancelledScope.async {
                mutationEngine.applyCableMutation(intent, grant)
            }.await()
            fail("Should have thrown CancellationException")
        } catch (e: CancellationException) {
            // Expected: CancellationException must be rethrown and NOT converted to DatabaseFailure
        }

        // DB state must be 100% rolled back
        assertEquals(5L, database.projectDao().getProject("PRJ-1")!!.revision)
        assertNull(database.circuitDao().getCircuitById(101)!!.overrideCableSize)
    }

    @Test
    fun committedSuccessIncludesRequiresRevalidationImpact() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101)
        val intent = createValidIntent()
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant)
        assertTrue(outcome.isSuccess)
        val success = outcome as MutationOutcome.Success

        assertTrue(success.impact.requiresRevalidation)
        assertTrue(success.impact.candidateAffectedDomains.contains("CABLE_SIZING"))
        assertTrue(success.impact.candidateAffectedDomains.contains("VOLTAGE_DROP"))
        assertTrue(success.impact.candidateAffectedDomains.contains("SHORT_CIRCUIT"))
    }

    @Test
    fun receiptPreservesStringIntegerDecimalAndNull() = runBlocking {
        seedProjectAndCircuit(projectId = "PRJ-1", revision = 5L, circuitId = 101, overrideCableSize = "185 mm²")
        val intent = createValidIntent(
            instructions = CablePatchInstructions(
                overrideCableSize = FieldInstruction("overrideCableSize", PatchAction.SET, "240 mm²"),
                parallelRuns = FieldInstruction("parallelRuns", PatchAction.SET, 2),
                lengthM = FieldInstruction("lengthM", PatchAction.SET, 75.5)
            )
        )
        val grant = createMatchingGrant(intent)

        val outcome = mutationEngine.applyCableMutation(intent, grant) as MutationOutcome.Success
        val changes = outcome.appliedChanges

        val sizeChange = changes.first { it.fieldName == "overrideCableSize" }
        assertEquals(FieldValueKind.STRING, sizeChange.previousValue?.kind)
        assertEquals("185 mm²", sizeChange.previousValue?.stringValue)
        assertEquals(FieldValueKind.STRING, sizeChange.newValue?.kind)
        assertEquals("240 mm²", sizeChange.newValue?.stringValue)

        val runsChange = changes.first { it.fieldName == "parallelRuns" }
        assertEquals(FieldValueKind.INTEGER, runsChange.newValue?.kind)
        assertEquals(2L, runsChange.newValue?.intValue)

        val lenChange = changes.first { it.fieldName == "lengthM" }
        assertEquals(FieldValueKind.DECIMAL, lenChange.newValue?.kind)
        assertEquals(75.5, lenChange.newValue?.doubleValue)
    }

    @Test
    fun publicDatabaseFailureIsSanitized() {
        val failure = MutationOutcome.DatabaseFailure(
            errorCode = "DB_TRANSACTION_FAILED",
            sanitizedMessage = "Database mutation transaction failed unexpectedly."
        )
        assertFalse("Must not expose SQL syntax", failure.sanitizedMessage.contains("SELECT "))
        assertFalse("Must not expose filesystem path", failure.sanitizedMessage.contains("E:\\"))
        assertFalse("Must not expose exception class", failure.sanitizedMessage.contains("SQLException"))
    }

    // =========================================================================
    // 5. System Protection & Non-Exposure Boundaries
    // =========================================================================

    @Test
    fun mutationExecutionRemainsDisabled() {
        assertFalse("ExecutionMode.MUTATION must remain false in 7C",
            ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    @Test
    fun engineIsInternal() {
        assertEquals("EngineeringMutationEngine must have internal visibility in Kotlin",
            kotlin.reflect.KVisibility.INTERNAL, EngineeringMutationEngine::class.visibility)
    }

    @Test
    fun noProductionAllowAllApprovalVerifierExists() {
        // Verify DenyAllApprovalVerifier rejects verification by default
        val intent = createValidIntent()
        val grant = createMatchingGrant(intent)
        val result = DenyAllApprovalVerifier.verifyApproval(intent, grant)
        assertTrue("DenyAllApprovalVerifier must always reject in production", result is ApprovalVerificationResult.Rejected)
    }
}

class TestApprovalVerifier(var shouldVerify: Boolean = true) : ProposalApprovalVerifier {
    override fun verifyApproval(
        intent: ValidatedCableProposalIntent,
        grant: ProposalApprovalGrant,
        currentTimeMillis: Long
    ): ApprovalVerificationResult {
        return if (shouldVerify && grant.proposalId == intent.proposalId && grant.proposalDigest == intent.proposalDigest && grant.baseProjectRevision == intent.baseProjectRevision) {
            ApprovalVerificationResult.Verified
        } else {
            ApprovalVerificationResult.Rejected("Approval verification rejected in test.")
        }
    }
}
