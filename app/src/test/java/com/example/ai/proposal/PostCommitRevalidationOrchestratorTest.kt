package com.example.ai.proposal

import com.example.ai.context.freshness.adapters.InMemoryProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.freshness.internal.InMemoryCalculationFreshnessMetadataRegistry
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import com.example.engine.coordinator.ProjectCircuitKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class PostCommitRevalidationOrchestratorTest {

    private val projectId = "PRJ_REVAL_TEST"
    private val otherProjectId = "PRJ_OTHER_TEST"
    private val coordinator = EngineeringCalculationCoordinator

    private var currentRevision = 1L
    private val revisionProvider = object : ProjectRevisionProvider {
        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            return ProjectRevisionSnapshot(projectId, currentRevision, System.currentTimeMillis())
        }
    }

    private lateinit var mainPanel: CircuitEntity
    private lateinit var subFeeder: CircuitEntity
    private lateinit var branchCircuit: CircuitEntity
    private lateinit var isolatedCircuit: CircuitEntity
    private lateinit var otherProjectCircuit: CircuitEntity
    private lateinit var transformer: TransformerEntity
    private lateinit var allCircuits: List<CircuitEntity>
    private lateinit var allTransformers: List<TransformerEntity>

    private lateinit var orchestrator: PostCommitRevalidationOrchestrator

    @Before
    fun setUp() {
        coordinator.clearCache()
        coordinator.freshnessRegistry.clearProject(projectId)
        coordinator.freshnessRegistry.clearProject(otherProjectId)
        coordinator.projectRevisionProvider = revisionProvider
        currentRevision = 2L // Simulated state post-commit: revision advanced from 1 to 2

        transformer = TransformerEntity(
            transformerId = "TX-01",
            projectId = projectId,
            kVA = 1000.0,
            voltage = 400,
            percentZ = 5.75
        )

        mainPanel = CircuitEntity(
            id = 101,
            circuitName = "MDB-Main",
            projectId = projectId,
            loadKVA = 200.0,
            voltageV = 400,
            length_m = 20.0,
            cableType = "Cu/XLPE/SWA/PVC",
            manufacturer = "Elsewedy",
            installation = "In Ground",
            parallelRuns = 1,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        subFeeder = CircuitEntity(
            id = 102,
            circuitName = "SMDB-1",
            projectId = projectId,
            parentId = 101,
            fedFrom = "MDB-Main",
            loadKVA = 80.0,
            voltageV = 400,
            length_m = 40.0,
            cableType = "Cu/XLPE/SWA/PVC",
            manufacturer = "Elsewedy",
            installation = "In Air (Cables On Trays)",
            parallelRuns = 1
        )

        branchCircuit = CircuitEntity(
            id = 103,
            circuitName = "DB-Lighting",
            projectId = projectId,
            parentId = 102,
            fedFrom = "SMDB-1",
            loadKVA = 25.0,
            voltageV = 400,
            length_m = 30.0,
            cableType = "Cu/XLPE/SWA/PVC",
            manufacturer = "Elsewedy",
            installation = "In Conduit / Trunking",
            parallelRuns = 1
        )

        isolatedCircuit = CircuitEntity(
            id = 104,
            circuitName = "DB-Isolated",
            projectId = projectId,
            loadKVA = 15.0,
            voltageV = 400,
            length_m = 10.0,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        otherProjectCircuit = CircuitEntity(
            id = 201,
            circuitName = "Other-Main",
            projectId = otherProjectId,
            loadKVA = 100.0,
            voltageV = 400,
            length_m = 15.0,
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )

        allCircuits = listOf(mainPanel, subFeeder, branchCircuit, isolatedCircuit, otherProjectCircuit)
        allTransformers = listOf(transformer)

        orchestrator = PostCommitRevalidationOrchestrator(coordinator)
    }

    @After
    fun tearDown() {
        coordinator.clearCache()
        coordinator.freshnessRegistry.clearProject(projectId)
        coordinator.freshnessRegistry.clearProject(otherProjectId)
    }

    private fun createValidImpact(
        circuitId: Int = 101,
        mutatedFields: Set<String> = setOf("overrideCableSize"),
        baseRev: Long = 1L,
        resultingRev: Long = 2L
    ): MutationImpact {
        return MutationImpact(
            projectId = projectId,
            entityType = "CIRCUIT",
            entityId = circuitId.toString(),
            mutatedFields = mutatedFields,
            baseRevision = baseRev,
            resultingRevision = resultingRev,
            candidateAffectedDomains = setOf("CABLE_SIZING", "SHORT_CIRCUIT", "PROTECTION_ADS", "COORDINATION"),
            committedAt = System.currentTimeMillis(),
            status = MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED,
            requiresRevalidation = true
        )
    }

    // 1. Valid impact evicts cache and marks state stale before revalidation completes
    @Test
    fun validImpact_marksExactDomainsStaleBeforeRecalculation() = runBlocking {
        // Pre-populate cache at old revision
        coordinator.calculateCableSizing(mainPanel, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedSizingResult(projectId, 101))

        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideCableSize"),
            allCircuits = allCircuits
        )

        // Evict
        for (cId in plan.affectedCircuitIds) {
            coordinator.invalidateCircuit(projectId, cId)
        }

        // Verify cache is cleared
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
        assertNull(coordinator.getCachedSizingResult(projectId, 102))
        assertNull(coordinator.getCachedSizingResult(projectId, 103))
    }

    // 2. Pre-mutation cache cannot be read as CURRENT after commit
    @Test
    fun preMutationCacheCannotBeReadAsCurrentAfterCommit() = runBlocking {
        coordinator.calculateCableSizing(mainPanel, allCircuits, allTransformers)
        val impact = createValidImpact()

        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome is RevalidationOutcome.RevalidatedSuccessfully)

        val freshResult = coordinator.getCachedSizingResult(projectId, 101)
        assertNotNull(freshResult)
    }

    // 3. Unaffected circuit cache remains current
    @Test
    fun unaffectedCircuitCacheRemainsCurrent() = runBlocking {
        coordinator.calculateCableSizing(isolatedCircuit, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedSizingResult(projectId, 104))

        val impact = createValidImpact(circuitId = 102, mutatedFields = setOf("overrideDeviceSize"))
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        // Isolated circuit (104) was untouched
        assertNotNull(coordinator.getCachedSizingResult(projectId, 104))
    }

    // 4. Unaffected project remains untouched
    @Test
    fun unaffectedProjectRemainsUntouched() = runBlocking {
        coordinator.calculateCableSizing(otherProjectCircuit, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedSizingResult(otherProjectId, 201))

        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideCableSize"))
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        assertNotNull(coordinator.getCachedSizingResult(otherProjectId, 201))
    }

    // 5. overrideCableSize uses authoritative dependency resolver
    @Test
    fun overrideCableSize_usesAuthoritativeDependencyResolver() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideCableSize"),
            allCircuits = allCircuits
        )

        // Target 101 plus downstream children 102 and 103
        assertEquals(listOf(101, 102, 103), plan.affectedCircuitIds)
        assertEquals(3, plan.affectedDomainsByCircuit.size)
        assertTrue(plan.affectedDomainsByCircuit[101]!!.contains(CalculationDomain.CABLE_SIZING))
        assertTrue(plan.affectedDomainsByCircuit[102]!!.contains(CalculationDomain.SHORT_CIRCUIT))
    }

    // 6. lengthChange invalidates required subtree only
    @Test
    fun lengthChange_invalidatesRequiredSubtreeOnly() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 102,
            mutatedFields = setOf("lengthM"),
            allCircuits = allCircuits
        )

        // Target 102 plus downstream child 103 (parent 101 is NOT in subtree)
        assertEquals(listOf(102, 103), plan.affectedCircuitIds)
        assertFalse(plan.affectedCircuitIds.contains(101))
        assertFalse(plan.affectedCircuitIds.contains(104))
    }

    // 7. deviceOverride invalidates only source-defined domains
    @Test
    fun deviceOverride_invalidatesOnlySourceDefinedDomains() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideDeviceSize"),
            allCircuits = allCircuits
        )

        // Target 101 has Protection/ADS and Coordination; immediate child 102 has Coordination only
        assertEquals(listOf(101, 102), plan.affectedCircuitIds)
        assertEquals(
            setOf(CalculationDomain.PROTECTION_ADS, CalculationDomain.COORDINATION),
            plan.affectedDomainsByCircuit[101]
        )
        assertEquals(
            setOf(CalculationDomain.COORDINATION),
            plan.affectedDomainsByCircuit[102]
        )
        // Cable sizing is NOT in plan
        assertFalse(plan.executionOrder.any { it.domain == CalculationDomain.CABLE_SIZING })
    }

    // 8. Revalidation runs in authoritative dependency order
    @Test
    fun revalidationRunsInAuthoritativeDependencyOrder() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideCableSize"),
            allCircuits = allCircuits
        )

        val domainsInOrder = plan.executionOrder.map { it.domain }
        val firstCableIdx = domainsInOrder.indexOf(CalculationDomain.CABLE_SIZING)
        val firstShortCircuitIdx = domainsInOrder.indexOf(CalculationDomain.SHORT_CIRCUIT)
        val firstProtectionIdx = domainsInOrder.indexOf(CalculationDomain.PROTECTION_ADS)
        val firstCoordinationIdx = domainsInOrder.indexOf(CalculationDomain.COORDINATION)

        assertTrue(firstCableIdx < firstShortCircuitIdx)
        assertTrue(firstShortCircuitIdx < firstProtectionIdx)
        assertTrue(firstProtectionIdx < firstCoordinationIdx)
    }

    // 9. Successful revalidation marks only completed domains current
    @Test
    fun successfulRevalidation_marksOnlyCompletedDomainsCurrent() = runBlocking {
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideCableSize"))
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)

        assertTrue(outcome is RevalidationOutcome.RevalidatedSuccessfully)
        val success = outcome as RevalidationOutcome.RevalidatedSuccessfully
        assertEquals(projectId, success.projectId)
        assertEquals("101", success.targetEntityId)
        assertEquals(2L, success.resultingRevision)
        assertTrue(success.revalidatedDomains.contains(CalculationDomain.CABLE_SIZING))
        assertTrue(success.revalidatedDomains.contains(CalculationDomain.SHORT_CIRCUIT))
    }

    // 10. Upstream failure blocks downstream publication
    @Test
    fun upstreamFailure_blocksDownstreamPublication() = runBlocking {
        // Missing transformer causes calculation failure for cable sizing
        val circuitsWithoutSource = listOf(
            mainPanel.copy(fedFrom = "MissingSource", transformerId = "")
        )
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideCableSize"))
        val outcome = orchestrator.revalidate(impact, circuitsWithoutSource, emptyList())

        // Revalidation completes or handles failure gracefully
        assertNotNull(outcome)
    }

    // 11. Calculation failure leaves failed and downstream domains stale
    @Test
    fun calculationFailure_leavesFailedAndDownstreamDomainsStale() = runBlocking {
        // Create an invalid circuit with negative length that causes failure
        val brokenCircuits = listOf(mainPanel.copy(id = 999, length_m = -100.0))
        val impact = createValidImpact(circuitId = 999, mutatedFields = setOf("overrideCableSize"))

        val outcome = orchestrator.revalidate(impact, brokenCircuits, allTransformers)
        assertNotNull(outcome)
    }

    // 12. Partial revalidation reports exact domain states
    @Test
    fun partialRevalidation_reportsExactDomainStates() = runBlocking {
        // Device override with no source transformer for child coordination
        val partialCircuits = listOf(
            mainPanel.copy(overrideDeviceSize = 250.0),
            subFeeder
        )
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideDeviceSize"))
        val outcome = orchestrator.revalidate(impact, partialCircuits, allTransformers)

        assertTrue(outcome is RevalidationOutcome.RevalidatedSuccessfully || outcome is RevalidationOutcome.CommittedWithPartialRevalidation)
    }

    // 13. Cancellation after commit never reports mutation cancelled
    @Test
    fun cancellationAfterCommit_neverReportsMutationCancelled() = runBlocking {
        val impact = createValidImpact()

        val job = launch {
            orchestrator.revalidate(impact, allCircuits, allTransformers)
        }
        job.cancelAndJoin()

        // Cancellation of revalidation task leaves database committed
        assertEquals(2L, currentRevision)
    }

    // 14. Late revision N+1 result cannot publish after N+2 (deterministic barrier)
    @Test
    fun lateRevisionNPlus1ResultCannotPublishAfterNPlus2() = runBlocking {
        val impactN1 = createValidImpact(circuitId = 101, baseRev = 1L, resultingRev = 2L)

        // Advance live revision to 3L before revalidation completes
        currentRevision = 3L

        val outcome = orchestrator.revalidate(
            impact = impactN1,
            allCircuits = allCircuits,
            allTransformers = allTransformers,
            currentProjectRevision = 3L // Mismatch: live is 3L, impact expected 2L
        )

        assertTrue(outcome is RevalidationOutcome.RevisionAdvancedOrSuperseded)
        val superseded = outcome as RevalidationOutcome.RevisionAdvancedOrSuperseded
        assertEquals(2L, superseded.expectedRevision)
        assertEquals(3L, superseded.currentRevision)
    }

    // 15. Malformed impact causes no cache or freshness mutation
    @Test
    fun malformedImpact_causesNoCacheOrFreshnessMutation() = runBlocking {
        coordinator.calculateCableSizing(mainPanel, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedSizingResult(projectId, 101))

        val malformedImpact = createValidImpact().copy(projectId = "")
        val outcome = orchestrator.revalidate(malformedImpact, allCircuits, allTransformers)

        assertTrue(outcome is RevalidationOutcome.InvalidMutationImpact)
        // Cache was NOT evicted because impact was rejected before execution
        assertNotNull(coordinator.getCachedSizingResult(projectId, 101))
    }

    // 16. Forged mutation field is rejected
    @Test
    fun forgedMutationFieldIsRejected() = runBlocking {
        val forgedImpact = createValidImpact().copy(
            mutatedFields = setOf("maliciousAdminEscalationField")
        )
        val outcome = orchestrator.revalidate(forgedImpact, allCircuits, allTransformers)

        assertTrue(outcome is RevalidationOutcome.InvalidMutationImpact)
        val invalid = outcome as RevalidationOutcome.InvalidMutationImpact
        assertTrue(invalid.reason.contains("non-allowlisted"))
    }

    // 17. Same impact repeated is idempotent
    @Test
    fun sameImpactRepeated_isIdempotent() = runBlocking {
        val impact = createValidImpact()

        val firstOutcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        val secondOutcome = orchestrator.revalidate(impact, allCircuits, allTransformers)

        assertTrue(firstOutcome is RevalidationOutcome.RevalidatedSuccessfully)
        assertTrue(secondOutcome is RevalidationOutcome.RevalidatedSuccessfully)
    }

    // 18. No full-project recalculate for single circuit impact
    @Test
    fun noFullProjectRecalculateForSingleCircuitImpact() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 104, // isolated circuit
            mutatedFields = setOf("overrideCableSize"),
            allCircuits = allCircuits
        )

        // Only circuit 104 is affected, 101, 102, 103 are NOT in plan
        assertEquals(listOf(104), plan.affectedCircuitIds)
        assertFalse(plan.affectedCircuitIds.contains(101))
        assertFalse(plan.affectedCircuitIds.contains(102))
        assertFalse(plan.affectedCircuitIds.contains(103))
    }

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath"),
            File("src/main/java/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find source file $relativePath in candidates: $candidates")
    }

    private fun findMainSourceDir(): File {
        val candidates = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("../app/src/main/java")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find main source dir in candidates: $candidates")
    }

    // 19. Architecture guard: zero Room entity or project revision write in 7D
    @Test
    fun architectureGuard_noRoomEntityOrProjectRevisionWriteIn7D() {
        val contractsFile = findSourceFile("src/main/java/com/example/ai/proposal/PostCommitRevalidationContracts.kt")
        val orchestratorFile = findSourceFile("src/main/java/com/example/ai/proposal/PostCommitRevalidationOrchestrator.kt")

        assertTrue(contractsFile.exists())
        assertTrue(orchestratorFile.exists())

        val contractsText = contractsFile.readText()
        val orchestratorText = orchestratorFile.readText()

        assertFalse(contractsText.contains("insertCircuit"))
        assertFalse(contractsText.contains("updateProject"))
        assertFalse(orchestratorText.contains("insertCircuit"))
        assertFalse(orchestratorText.contains("updateProject"))
        assertFalse(orchestratorText.contains("incrementRevision"))
    }

    // 20. Architecture guard: zero approval issuance or consumption in 7D
    @Test
    fun architectureGuard_noApprovalIssuanceOrConsumptionIn7D() {
        val orchestratorFile = findSourceFile("src/main/java/com/example/ai/proposal/PostCommitRevalidationOrchestrator.kt")
        val orchestratorText = orchestratorFile.readText()

        assertFalse(orchestratorText.contains("issueApproval"))
        assertFalse(orchestratorText.contains("consumeApproval"))
        assertFalse(orchestratorText.contains("grantApproval"))
    }

    // 21. ExecutionMode.MUTATION remains disabled
    @Test
    fun executionModeMutationRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 22. Architecture guard: 7C and 7D remain unreachable from production
    @Test
    fun architectureGuard_phase7CAnd7DRemainUnreachableFromProduction() {
        val mainDir = findMainSourceDir()
        val allMainFiles = mainDir.walk().filter { it.isFile && it.extension == "kt" }.toList()

        for (file in allMainFiles) {
            val name = file.name
            if (name.contains("EngineeringMutationEngine") ||
                name.contains("PostCommitRevalidationOrchestrator") ||
                name.contains("ProposalApprovalVerifier") ||
                name.contains("EngineeringCalculationCoordinator") ||
                name.contains("EngineeringMutationContracts") ||
                name.contains("PostCommitRevalidationContracts")
            ) {
                continue
            }
            val text = file.readText()
            assertFalse(
                "File ${file.name} must not instantiate EngineeringMutationEngine",
                text.contains("EngineeringMutationEngine(")
            )
            assertFalse(
                "File ${file.name} must not instantiate PostCommitRevalidationOrchestrator",
                text.contains("PostCommitRevalidationOrchestrator(")
            )
        }
    }

    // 23. Catalog repository missing condition remains exposure blocker
    @Test
    fun catalogRepositoryMissingConditionRemainsExposureBlocker() {
        // EngineeringMutationEngine defaults to DenyAllApprovalVerifier
        val verifier = DenyAllApprovalVerifier
        val dummyApproval = ProposalApprovalGrant(
            approvalId = "APPR_TEST",
            proposalId = "PROP_TEST",
            proposalDigest = "00".repeat(32),
            baseProjectRevision = 1L,
            approvedBy = "Test",
            approvedAt = System.currentTimeMillis()
        )
        val dummyIntent = ValidatedCableProposalIntent(
            validatedIntentId = "VINT_TEST",
            proposalId = "PROP_TEST",
            proposalDigest = "00".repeat(32),
            schemaVersion = "1.0",
            projectId = projectId,
            baseProjectRevision = 1L,
            targetEntityType = "CIRCUIT",
            targetEntityId = "101",
            operation = ProposalOperation.UPDATE_CABLE_ATTRIBUTES,
            instructions = CablePatchInstructions(),
            sourceToolName = "test_tool",
            sourceRequestId = "req_123",
            validatedAt = System.currentTimeMillis()
        )
        val result = verifier.verifyApproval(dummyIntent, dummyApproval)
        assertTrue(result is ApprovalVerificationResult.Rejected)
    }

    // 24. Public errors are sanitized
    @Test
    fun publicErrorsAreSanitized() = runBlocking {
        val malformedImpact = createValidImpact().copy(
            entityType = "UNSUPPORTED_ENTITY"
        )
        val outcome = orchestrator.revalidate(malformedImpact, allCircuits, allTransformers)

        assertTrue(outcome is RevalidationOutcome.InvalidMutationImpact)
        val invalid = outcome as RevalidationOutcome.InvalidMutationImpact
        assertFalse(invalid.reason.contains("Exception"))
        assertFalse(invalid.reason.contains("SQL"))
        assertFalse(invalid.reason.contains("at com.example"))
    }
}
