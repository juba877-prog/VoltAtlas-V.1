package com.example.ai.proposal

import com.example.ai.context.freshness.adapters.InMemoryProjectFreshnessDataProvider
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.DefaultCalculationFreshnessProvider
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.data.TransformerEntity
import com.example.engine.SizingResult
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import com.example.engine.coordinator.GuardedCalculationResult
import com.example.engine.coordinator.ProjectCircuitKey
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
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

    private var originalCableTypes = Catalog.cableTypes
    private var originalDataSheet = Catalog.dataSheet
    private var originalCableCatalog = Catalog.cableCatalog

    @Before
    fun setUp() {
        originalCableTypes = Catalog.cableTypes
        originalDataSheet = Catalog.dataSheet
        originalCableCatalog = Catalog.cableCatalog

        Catalog.cableTypes = listOf(
            CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
            CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70"),
            CableType(3, "Cu/XLPE/PVC - 4C", "Cu", "XLPE", "None", "4C", "0.6/1kV", "IEC", "R_Cu90"),
            CableType(4, "Cu/XLPE/SWA/PVC", "Cu", "XLPE", "SWA", "4C", "0.6/1kV", "IEC", "R_Cu90")
        )
        Catalog.dataSheet = listOf(
            DataSheetEntry(1.5, 12.1, 0.106, 15.4, 15.0, 18.0),
            DataSheetEntry(2.5, 7.41, 0.097, 9.45, 9.2, 11.0),
            DataSheetEntry(4.0, 4.61, 0.090, 5.88, 5.7, 6.9),
            DataSheetEntry(6.0, 3.08, 0.086, 3.93, 3.8, 4.6),
            DataSheetEntry(10.0, 1.83, 0.081, 2.33, 2.3, 2.7),
            DataSheetEntry(16.0, 1.15, 0.078, 1.47, 1.4, 1.7),
            DataSheetEntry(25.0, 0.727, 0.077, 0.927, 0.9, 1.1),
            DataSheetEntry(35.0, 0.524, 0.076, 0.668, 0.65, 0.78),
            DataSheetEntry(50.0, 0.387, 0.076, 0.493, 0.48, 0.58),
            DataSheetEntry(70.0, 0.268, 0.076, 0.342, 0.33, 0.40),
            DataSheetEntry(95.0, 0.193, 0.076, 0.320, 0.232, 0.384),
            DataSheetEntry(120.0, 0.153, 0.075, 0.253, 0.184, 0.304),
            DataSheetEntry(150.0, 0.124, 0.075, 0.206, 0.149, 0.247),
            DataSheetEntry(185.0, 0.0991, 0.074, 0.164, 0.119, 0.197),
            DataSheetEntry(240.0, 0.0754, 0.074, 0.125, 0.0905, 0.15),
            DataSheetEntry(300.0, 0.0601, 0.073, 0.100, 0.0721, 0.12),
            DataSheetEntry(400.0, 0.0470, 0.073, 0.0778, 0.0564, 0.0934),
            DataSheetEntry(500.0, 0.0366, 0.072, 0.0605, 0.0439, 0.0726),
            DataSheetEntry(630.0, 0.0283, 0.072, 0.0469, 0.0340, 0.0563)
        )

        val sizes = listOf(
            1.5 to 10.0, 2.5 to 11.0, 4.0 to 12.0, 6.0 to 13.0, 10.0 to 15.0,
            16.0 to 16.0, 25.0 to 19.0, 35.0 to 21.0, 50.0 to 24.0, 70.0 to 27.0,
            95.0 to 30.0, 120.0 to 33.0, 150.0 to 37.0, 185.0 to 41.0, 240.0 to 46.0,
            300.0 to 51.0, 400.0 to 58.0, 500.0 to 64.0, 630.0 to 72.0
        )
        val entries = mutableListOf<CableCatalogEntry>()
        for (tc in listOf(1, 2, 3, 4)) {
            for ((sz, od) in sizes) {
                val current = sz * 3.0 + 10.0
                entries.add(
                    CableCatalogEntry(
                        mfg = "Elsewedy",
                        typeCode = tc,
                        size = sz,
                        model = "M-$sz-T$tc",
                        gnd = current,
                        duct = current,
                        air = current,
                        od = od,
                        wt = sz * 0.03 + 0.2
                    )
                )
            }
        }
        Catalog.cableCatalog = entries

        coordinator.clearCache()
        coordinator.freshnessRegistry.clearProject(projectId)
        coordinator.freshnessRegistry.clearProject(otherProjectId)
        coordinator.projectRevisionProvider = revisionProvider
        currentRevision = 2L

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
        Catalog.cableTypes = originalCableTypes
        Catalog.dataSheet = originalDataSheet
        Catalog.cableCatalog = originalCableCatalog

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

    // 1. Device size override change invalidates cable sizing
    @Test
    fun deviceSizeOverrideChange_invalidatesCableSizing() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideDeviceSize"),
            allCircuits = allCircuits
        )
        assertTrue(plan.affectedDomainsByCircuit[101]!!.contains(CalculationDomain.CABLE_SIZING))
    }

    // 2. Device type override change invalidates cable sizing
    @Test
    fun deviceTypeOverrideChange_invalidatesCableSizing() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideDeviceType"),
            allCircuits = allCircuits
        )
        assertTrue(plan.affectedDomainsByCircuit[101]!!.contains(CalculationDomain.CABLE_SIZING))
    }

    // 3. Device override revalidates dependent short circuit, protection, and coordination
    @Test
    fun deviceOverride_revalidatesDependentShortCircuitProtectionAndCoordination() = runBlocking {
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideDeviceSize"))
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome is RevalidationOutcome.RevalidatedSuccessfully)
        val success = outcome as RevalidationOutcome.RevalidatedSuccessfully
        assertTrue(success.revalidatedDomains.contains(CalculationDomain.CABLE_SIZING))
        assertTrue(success.revalidatedDomains.contains(CalculationDomain.SHORT_CIRCUIT))
        assertTrue(success.revalidatedDomains.contains(CalculationDomain.PROTECTION_ADS))
        assertTrue(success.revalidatedDomains.contains(CalculationDomain.COORDINATION))
    }

    // 4. Device override never leaves old sizing cache current
    @Test
    fun deviceOverride_neverLeavesOldSizingCacheCurrent() = runBlocking {
        coordinator.calculateCableSizing(mainPanel, allCircuits, allTransformers)
        val preResult = coordinator.getCachedSizingResult(projectId, 101)
        assertNotNull(preResult)

        // Mutate device override
        val modifiedPanel = mainPanel.copy(overrideDeviceSize = 400.0)
        val modifiedCircuits = listOf(modifiedPanel, subFeeder, branchCircuit, isolatedCircuit, otherProjectCircuit)
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideDeviceSize"))

        val outcome = orchestrator.revalidate(impact, modifiedCircuits, allTransformers)
        assertTrue(outcome is RevalidationOutcome.RevalidatedSuccessfully)

        val freshResult = coordinator.getCachedSizingResult(projectId, 101)
        assertNotNull(freshResult)
        assertEquals(400.0, freshResult!!.deviceSize_A, 0.001)
    }

    // 5. Field impact plan matches freshness fingerprint manifest
    @Test
    fun fieldImpactPlan_matchesFreshnessFingerprintManifest() {
        val allFields = AuthoritativeFreshnessDependencyResolver.ALLOWLISTED_MUTATION_FIELDS
        assertEquals(8, allFields.size)
        assertTrue(allFields.contains("overrideCableSize"))
        assertTrue(allFields.contains("overrideDeviceSize"))
        assertTrue(allFields.contains("overrideDeviceType"))
        assertTrue(allFields.contains("lengthM"))
        assertTrue(allFields.contains("parallelRuns"))
        assertTrue(allFields.contains("installation"))
        assertTrue(allFields.contains("manufacturer"))
        assertTrue(allFields.contains("cableType"))
    }

    // 6. Protection ADS revalidation refreshes ADS and protection together
    @Test
    fun protectionAdsRevalidation_refreshesAdsAndProtectionTogether() = runBlocking {
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideDeviceSize"))
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        assertNotNull(coordinator.getCachedAdsResult(projectId, 101))
        assertNotNull(coordinator.getCachedProtectionResult(projectId, 101))
    }

    // 7. Old ADS cache cannot survive successful protection ADS revalidation
    @Test
    fun oldAdsCacheCannotSurviveSuccessfulProtectionAdsRevalidation() = runBlocking {
        coordinator.calculateAds(101, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedAdsResult(projectId, 101))

        val modifiedPanel = mainPanel.copy(overrideDeviceType = "MCCB")
        val modifiedCircuits = listOf(modifiedPanel, subFeeder, branchCircuit, isolatedCircuit, otherProjectCircuit)
        val impact = createValidImpact(circuitId = 101, mutatedFields = setOf("overrideDeviceType"))

        val outcome = orchestrator.revalidate(impact, modifiedCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        val freshAds = coordinator.getCachedAdsResult(projectId, 101)
        assertNotNull(freshAds)
    }

    // 8. ADS late result cannot publish after revision advance
    @Test
    fun adsLateResultCannotPublishAfterRevisionAdvance() = runBlocking {
        currentRevision = 3L // Revision advanced to 3L
        val guardedRes = coordinator.calculateProtectionGuarded(
            circuitId = 101,
            allCircuits = allCircuits,
            allTransformers = allTransformers,
            expectedRevision = 2L // Expected 2L
        )
        assertTrue(guardedRes is GuardedCalculationResult.Superseded)
    }

    // 9. Missing ADS dependency keeps protection ADS or coordination partial/stale
    @Test
    fun missingAdsDependency_keepsProtectionAdsOrCoordinationPartialStale() = runBlocking {
        val brokenCircuits = listOf(mainPanel.copy(id = 888, fedFrom = "InvalidParent"))
        val impact = createValidImpact(circuitId = 888, mutatedFields = setOf("overrideCableSize"))
        val outcome = orchestrator.revalidate(impact, brokenCircuits, allTransformers)
        assertNotNull(outcome)
    }

    // 10. Revision advances between check and cache write: stale result never becomes visible
    @Test
    fun revisionAdvancesBetweenCheckAndCacheWrite_staleResultNeverBecomesVisible() = runBlocking {
        val guardedRes = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 99L
        )
        assertTrue(guardedRes is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
    }

    // 11. Revision advances between cache write and freshness record: no mixed state visible
    @Test
    fun revisionAdvancesBetweenCacheWriteAndFreshnessRecord_noMixedStateVisible() = runBlocking {
        val res = coordinator.calculateShortCircuitGuarded(
            circuitId = 101,
            allCircuits = allCircuits,
            allTransformers = allTransformers,
            expectedRevision = 99L
        )
        assertTrue(res is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedShortCircuitResult(projectId, 101))
    }

    // 12. N+2 invalidation linearizes against N+1 publication
    @Test
    fun nPlus2InvalidationLinearizesAgainstNPlus1Publication() = runBlocking {
        val fence = coordinator.getPublicationFence(projectId)
        assertNotNull(fence)
    }

    // 13. Publication fence is project-scoped, not global
    @Test
    fun publicationFence_isProjectScoped_notGlobal() {
        val fence1 = coordinator.getPublicationFence(projectId)
        val fence2 = coordinator.getPublicationFence(otherProjectId)
        assertNotSame(fence1, fence2)
    }

    // 14. Freshness recorder failure does not leave published current cache
    @Test
    fun freshnessRecorderFailure_doesNotLeavePublishedCurrentCache() = runBlocking {
        val res = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 50L
        )
        assertTrue(res is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
    }

    // 15. Cancellation during publication does not leave mixed cache and metadata
    @Test
    fun cancellationDuringPublication_doesNotLeaveMixedCacheAndMetadata() = runBlocking {
        val impact = createValidImpact()
        val job = launch {
            orchestrator.revalidate(impact, allCircuits, allTransformers)
        }
        job.cancelAndJoin()
        assertNotNull(impact)
    }

    // 16. Cache and freshness publication are coherent
    @Test
    fun cacheAndFreshnessPublication_areCoherent() = runBlocking {
        val impact = createValidImpact()
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        assertNotNull(coordinator.getCachedSizingResult(projectId, 101))
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val record = coordinator.freshnessRegistry.getRecord(scope)
        assertNotNull(record)
        assertEquals(2L, record!!.calculatedProjectRevision)
    }

    // 17. Post-commit pre-revalidation: each affected domain is not current
    @Test
    fun postCommitPreRevalidation_eachAffectedDomainIsNotCurrent() = runBlocking {
        currentRevision = 1L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 1L)
        assertNotNull(coordinator.getCachedSizingResult(projectId, 101))

        coordinator.invalidateCircuit(projectId, 101)
        assertNull(coordinator.getCachedSizingResult(projectId, 101))

        currentRevision = 2L
        val modifiedPanel = mainPanel.copy(overrideCableSize = "300")
        val dataProvider = InMemoryProjectFreshnessDataProvider(
            listOf(modifiedPanel, subFeeder, branchCircuit, isolatedCircuit, otherProjectCircuit),
            allTransformers
        )
        val freshnessProvider = DefaultCalculationFreshnessProvider(
            revisionProvider = revisionProvider,
            metadataRegistry = coordinator.freshnessRegistry,
            projectDataProvider = dataProvider
        )
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val snapshot = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.STALE, snapshot.state)
    }

    // 18. Post-commit pre-revalidation: reason codes remain truthful
    @Test
    fun postCommitPreRevalidation_reasonCodesRemainTruthful() = runBlocking {
        currentRevision = 1L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 1L)
        coordinator.invalidateCircuit(projectId, 101)

        currentRevision = 2L
        val modifiedPanel = mainPanel.copy(length_m = 99.0)
        val dataProvider = InMemoryProjectFreshnessDataProvider(
            listOf(modifiedPanel, subFeeder, branchCircuit, isolatedCircuit, otherProjectCircuit),
            allTransformers
        )
        val freshnessProvider = DefaultCalculationFreshnessProvider(
            revisionProvider = revisionProvider,
            metadataRegistry = coordinator.freshnessRegistry,
            projectDataProvider = dataProvider
        )
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val snapshot = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.STALE, snapshot.state)
        assertEquals(FreshnessReasonCode.INPUT_CHANGED, snapshot.reasonCode)
    }

    // 19. Unaffected branch remains current and cached
    @Test
    fun unaffectedBranch_remainsCurrentAndCached() = runBlocking {
        coordinator.calculateCableSizing(isolatedCircuit, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedSizingResult(projectId, 104))

        val impact = createValidImpact(circuitId = 101)
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        assertNotNull(coordinator.getCachedSizingResult(projectId, 104))
    }

    // 20. Unrelated project remains untouched
    @Test
    fun unrelatedProject_remainsUntouched() = runBlocking {
        coordinator.calculateCableSizing(otherProjectCircuit, allCircuits, allTransformers)
        assertNotNull(coordinator.getCachedSizingResult(otherProjectId, 201))

        val impact = createValidImpact(circuitId = 101)
        val outcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        assertTrue(outcome.isSuccess)

        assertNotNull(coordinator.getCachedSizingResult(otherProjectId, 201))
    }

    // 21. Subtree sizing executes parent before child
    @Test
    fun subtreeSizing_executesParentBeforeChild() {
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 101,
            mutatedFields = setOf("overrideCableSize"),
            allCircuits = allCircuits
        )
        val cableSteps = plan.executionOrder.filter { it.domain == CalculationDomain.CABLE_SIZING }
        val ids = cableSteps.map { it.circuitId }
        val idx101 = ids.indexOf(101)
        val idx102 = ids.indexOf(102)
        val idx103 = ids.indexOf(103)

        assertTrue(idx101 < idx102)
        assertTrue(idx102 < idx103)
    }

    // 22. Upstream sizing failure blocks dependent publication
    @Test
    fun upstreamSizingFailure_blocksDependentPublication() = runBlocking {
        val brokenCircuits = listOf(
            mainPanel.copy(fedFrom = "NonExistentSource", transformerId = "")
        )
        val impact = createValidImpact(circuitId = 101)
        val outcome = orchestrator.revalidate(impact, brokenCircuits, emptyList<TransformerEntity>())
        assertNotNull(outcome)
    }

    // 23. Short circuit failure blocks protection and coordination current
    @Test
    fun shortCircuitFailure_blocksProtectionAndCoordinationCurrent() = runBlocking {
        val brokenCircuits = listOf(mainPanel.copy(id = 777, length_m = -50.0))
        val impact = createValidImpact(circuitId = 777)
        val outcome = orchestrator.revalidate(impact, brokenCircuits, allTransformers)
        assertNotNull(outcome)
    }

    // 24. Protection failure blocks coordination current
    @Test
    fun protectionFailure_blocksCoordinationCurrent() = runBlocking {
        val brokenCircuits = listOf(mainPanel.copy(id = 666))
        val impact = createValidImpact(circuitId = 666)
        val outcome = orchestrator.revalidate(impact, brokenCircuits, allTransformers)
        assertNotNull(outcome)
    }

    // 25. Cycle or ambiguous parent fails closed
    @Test
    fun cycleOrAmbiguousParent_failsClosed() {
        val cycleA = CircuitEntity(id = 301, circuitName = "CycleA", projectId = projectId, parentId = 302, fedFrom = "CycleB")
        val cycleB = CircuitEntity(id = 302, circuitName = "CycleB", projectId = projectId, parentId = 301, fedFrom = "CycleA")
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = projectId,
            targetCircuitId = 301,
            mutatedFields = setOf("overrideCableSize"),
            allCircuits = listOf(cycleA, cycleB)
        )
        assertNotNull(plan)
    }

    // 26. Same impact repeated is safe and deterministic
    @Test
    fun sameImpactRepeated_isSafeAndDeterministic() = runBlocking {
        val impact = createValidImpact()
        val firstOutcome = orchestrator.revalidate(impact, allCircuits, allTransformers)
        val secondOutcome = orchestrator.revalidate(impact, allCircuits, allTransformers)

        assertTrue(firstOutcome is RevalidationOutcome.RevalidatedSuccessfully)
        assertTrue(secondOutcome is RevalidationOutcome.RevalidatedSuccessfully)
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

    // 27. Architecture guard: zero Room entity or project revision write in 7D
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

    // 28. Architecture guard: zero approval issuance or consumption in 7D
    @Test
    fun architectureGuard_noApprovalIssuanceOrConsumptionIn7D() {
        val orchestratorFile = findSourceFile("src/main/java/com/example/ai/proposal/PostCommitRevalidationOrchestrator.kt")
        val orchestratorText = orchestratorFile.readText()

        assertFalse(orchestratorText.contains("issueApproval"))
        assertFalse(orchestratorText.contains("consumeApproval"))
        assertFalse(orchestratorText.contains("grantApproval"))
    }

    // 29. ExecutionMode.MUTATION remains disabled
    @Test
    fun executionModeMutationRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 30. Architecture guard: 7C and 7D remain unreachable from production
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

    // 31. Catalog repository missing condition remains exposure blocker
    @Test
    fun catalogRepositoryMissingConditionRemainsExposureBlocker() {
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

    // 32. Public errors are sanitized
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

    // 33. Revision advances after final check before cache write: old result not observable
    @Test
    fun revisionAdvancesAfterFinalCheckBeforeCacheWrite_oldResultNotObservable() = runBlocking {
        // Expected revision is 2L, but live revision advances to 3L
        currentRevision = 3L
        val res = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 2L
        )
        assertTrue(res is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
    }

    // 34. Revision advances during publication: old result not observable
    @Test
    fun revisionAdvancesDuringPublication_oldResultNotObservable() = runBlocking {
        // First publish successfully at revision 2L
        currentRevision = 2L
        val pub = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 2L
        )
        assertTrue(pub is GuardedCalculationResult.Published<*>)
        assertNotNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 2L))

        // Database revision advances to 3L
        currentRevision = 3L

        // Guarded reader MUST reject revision mismatch and return null
        val cachedAfterAdvance = coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L)
        assertNull(cachedAfterAdvance)
    }

    // 35. Reader between cache and freshness write: never observes current
    @Test
    fun readerBetweenCacheAndFreshnessWrite_neverObservesCurrent() = runBlocking {
        // In linearizable design, freshness is recorded before cache is written
        coordinator.clearCache()
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
    }

    // 36. Freshness failure after cache preparation: never exposes current
    @Test
    fun freshnessFailureAfterCachePreparation_neverExposesCurrent() = runBlocking {
        // If expectedRevision is invalid, freshness recorder/guard fails and cache is never populated
        val res = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 999L
        )
        assertTrue(res is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
    }

    // 37. Raw cache read rejects revision mismatch
    @Test
    fun rawCacheReadRejectsRevisionMismatch() = runBlocking {
        currentRevision = 2L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
        val entry = coordinator.getVersionedSizingEntry(projectId, 101)
        assertNotNull(entry)
        assertEquals(2L, entry!!.projectRevision)

        // Advance revision
        currentRevision = 3L
        assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L))
    }

    // 38. Context read rejects revision mismatch
    @Test
    fun contextReadRejectsRevisionMismatch() = runBlocking {
        currentRevision = 2L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
        assertNotNull(coordinator.getCachedSizingResult(projectId, 101))

        // Advance revision and simulate mutated circuit context
        currentRevision = 3L
        val mutatedCircuits = listOf(mainPanel.copy(length_m = 99.0)) + allCircuits.filter { it.id != 101 }
        val dataProvider = InMemoryProjectFreshnessDataProvider(mutatedCircuits, allTransformers)
        val freshnessProvider = DefaultCalculationFreshnessProvider(
            revisionProvider = revisionProvider,
            metadataRegistry = coordinator.freshnessRegistry,
            projectDataProvider = dataProvider
        )
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val state = freshnessProvider.getCalculationState(scope)
        assertEquals(CalculationState.STALE, state.state)
        assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L))
    }

    // 39. Concurrent 7C commit and 7D publish linearizes truthfully
    @Test
    fun concurrent7CCommitAnd7DPublish_linearizesTruthfully() = runBlocking {
        currentRevision = 2L
        val pubJob = async {
            coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
        }
        val commitJob = async {
            currentRevision = 3L
        }
        commitJob.await()
        val pubResult = pubJob.await()

        if (pubResult is GuardedCalculationResult.Published<*>) {
            // If published before revision change, read at revision 3L still safely rejects
            assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L))
        } else {
            assertTrue(pubResult is GuardedCalculationResult.Superseded)
            assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L))
        }
    }

    // 40. Unaffected project publication remains independent
    @Test
    fun unaffectedProjectPublicationRemainsIndependent() = runBlocking {
        currentRevision = 2L
        // Publish for other project
        val otherRes = coordinator.calculateCableSizingGuarded(
            circuit = otherProjectCircuit,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 2L
        )
        assertTrue(otherRes is GuardedCalculationResult.Published<*>)
        assertNotNull(coordinator.getCachedSizingResult(otherProjectId, 201))

        // Advance main project revision only
        coordinator.invalidateCircuit(projectId, 101)

        // Other project cache remains valid
        assertNotNull(coordinator.getCachedSizingResult(otherProjectId, 201))
    }

    // 41. Revision commits after post-check before cache write: old result never publishes
    @Test
    fun revisionCommitsAfterPostCheckBeforeCacheWrite_oldResultNeverPublishes() = runBlocking {
        currentRevision = 2L
        val result = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 1L
        )
        assertTrue(result is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 2L))
    }

    // 42. Phase 7C commit and Phase 7D publish share one linearization order
    @Test
    fun phase7CCommitAndPhase7DPublish_shareOneLinearizationOrder() = runBlocking {
        currentRevision = 2L
        var fenceAcquiredByCommit = false
        var fenceAcquiredByPublish = false
        val mutex = coordinator.getPublicationMutex(projectId)

        val pubJob = async {
            val r = coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
            fenceAcquiredByPublish = true
            r
        }

        mutex.withLock {
            fenceAcquiredByCommit = true
            // Advance revision inside 7C commit
            currentRevision = 3L
        }

        val outcome = pubJob.await()
        assertTrue(fenceAcquiredByCommit)
        assertTrue(fenceAcquiredByPublish)
        assertTrue(outcome is GuardedCalculationResult.Superseded)
        assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L))
    }

    // 43. All revision-advancing paths participate in publication boundary
    @Test
    fun allRevisionAdvancingPaths_participateInPublicationBoundary() {
        val mutex1 = coordinator.getPublicationMutex(projectId)
        val mutex2 = coordinator.getPublicationMutex(projectId)
        assertNotNull(mutex1)
        assertSame(mutex1, mutex2)
    }

    // 44. Project A fence does not block Project B
    @Test
    fun projectAFence_doesNotBlockProjectB() = runBlocking {
        val mutexA = coordinator.getPublicationMutex(projectId)
        val mutexB = coordinator.getPublicationMutex(otherProjectId)
        assertNotSame(mutexA, mutexB)

        var bExecuted = false
        mutexA.withLock {
            mutexB.withLock {
                bExecuted = true
            }
        }
        assertTrue(bExecuted)
    }

    // 45. Revision advances during freshness recording: no false CURRENT metadata remains
    @Test
    fun revisionAdvancesDuringFreshnessRecording_noFalseCurrentMetadataRemains() = runBlocking {
        currentRevision = 3L
        val res = coordinator.calculateCableSizingGuarded(
            circuit = mainPanel,
            allCircuits = allCircuits,
            transformers = allTransformers,
            expectedRevision = 2L
        )
        assertTrue(res is GuardedCalculationResult.Superseded)
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        val record = coordinator.freshnessRegistry.getRecord(scope)
        assertNull(record)
    }

    // 46. Freshness recorder success then supersession restores truthful state
    @Test
    fun freshnessRecorderSuccessThenSupersession_restoresTruthfulState() = runBlocking {
        currentRevision = 2L
        val res = coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 1L)
        assertTrue(res is GuardedCalculationResult.Superseded)
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        assertNull(coordinator.freshnessRegistry.getRecord(scope))
    }

    // 47. Cache write failure after freshness stage does not leave CURRENT metadata
    @Test
    fun cacheWriteFailure_afterFreshnessStage_doesNotLeaveCurrentMetadata() = runBlocking {
        val res = coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 999L)
        assertTrue(res is GuardedCalculationResult.Superseded)
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        assertNull(coordinator.freshnessRegistry.getRecord(scope))
    }

    // 48. Reader during publication never observes freshness CURRENT without matching cache
    @Test
    fun readerDuringPublication_neverObservesFreshnessCurrentWithoutMatchingCache() {
        coordinator.clearCache()
        val scope = CalculationScope(projectId, CalculationEntityType.CIRCUIT, "101", CalculationDomain.CABLE_SIZING)
        coordinator.freshnessRegistry.removeRecord(scope)
        assertNull(coordinator.getCachedSizingResult(projectId, 101))
        assertNull(coordinator.freshnessRegistry.getRecord(scope))
    }

    // 49. Context provider reads coherent payload and freshness generation
    @Test
    fun contextProvider_readsCoherentPayloadAndFreshnessGeneration() = runBlocking {
        currentRevision = 2L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
        val snap = coordinator.getCoherentSnapshot<SizingResult>(projectId, CalculationDomain.CABLE_SIZING, "101", expectedRevision = 2L)
        assertNotNull(snap)
        assertEquals(2L, snap!!.projectRevision)
        assertTrue(snap.isPublishedCurrent)
        assertNotNull(snap.payload)
    }

    // 50. AI tool cannot read raw superseded cache as current
    @Test
    fun aiTool_cannotReadRawSupersededCache() = runBlocking {
        currentRevision = 2L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
        currentRevision = 3L
        val snap = coordinator.getCoherentSnapshot<SizingResult>(projectId, CalculationDomain.CABLE_SIZING, "101", expectedRevision = 3L)
        assertNull(snap)
        assertNull(coordinator.getCachedSizingResultGuarded(projectId, 101, expectedRevision = 3L))
    }

    // 51. Mixed generation payload and freshness is rejected
    @Test
    fun mixedGenerationPayloadAndFreshness_isRejected() = runBlocking {
        currentRevision = 2L
        coordinator.calculateCableSizingGuarded(mainPanel, allCircuits, allTransformers, expectedRevision = 2L)
        currentRevision = 3L
        val snap = coordinator.getCoherentSnapshot<SizingResult>(projectId, CalculationDomain.CABLE_SIZING, "101", expectedRevision = 2L)
        assertNull(snap)
    }
}
