package com.example.engine.coordinator

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.data.*
import com.example.engine.SizingResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gate D' (Option A) — fail-first verification suite.
 *
 * Proves the two defects measured in Gate D are eliminated:
 *  1. Per-pass revision resolution: a 60-circuit, 6-pass uiState recalculation previously issued
 *     603 blocking revision resolutions (one per circuit per stage). After hoisting, each pass
 *     resolves the project revision ONCE and threads the value down.
 *  2. The silent `catch (_: Exception) { 1L }` fallback is removed: an indeterminate revision read
 *     (DB locked/timeout) must fail loudly instead of fabricating revision 1L, which previously let
 *     a stale cache entry validate as live and let a valid revalidation report as superseded.
 *
 * Both assertions FAIL against the pre-fix implementation and PASS after the Option A change.
 * The counting provider lives in test scope only — no production instrumentation is added.
 */
class CoordinatorPassRevisionHoistingTest {

    private class CountingRevisionProvider(
        private val revisionValue: Long = 2L,
        private val failWith: Exception? = null
    ) : ProjectRevisionProvider {
        val invocationCount = AtomicInteger(0)

        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            invocationCount.incrementAndGet()
            failWith?.let { throw it }
            return ProjectRevisionSnapshot(projectId, revisionValue, System.currentTimeMillis())
        }
    }

    @Before
    fun setUp() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = null

        if (Catalog.cableTypes.isEmpty()) {
            Catalog.cableTypes = listOf(
                CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
                CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70")
            )
        }
        if (Catalog.dataSheet.isEmpty()) {
            Catalog.dataSheet = listOf(
                DataSheetEntry(1.5, 12.1, 0.118, 19.5, 14.52, 23.4),
                DataSheetEntry(2.5, 7.41, 0.109, 12.1, 8.89, 14.52),
                DataSheetEntry(4.0, 4.61, 0.101, 7.41, 5.53, 8.89),
                DataSheetEntry(10.0, 1.83, 0.086, 3.08, 2.2, 3.7),
                DataSheetEntry(16.0, 1.15, 0.082, 1.91, 1.38, 2.29),
                DataSheetEntry(25.0, 0.727, 0.080, 1.20, 0.872, 1.44),
                DataSheetEntry(50.0, 0.387, 0.078, 0.641, 0.464, 0.77),
                DataSheetEntry(95.0, 0.193, 0.076, 0.320, 0.232, 0.384)
            )
        }
        if (Catalog.cableCatalog.isEmpty()) {
            val sizes = listOf(1.5 to 10.0, 2.5 to 11.0, 4.0 to 12.0, 10.0 to 15.0, 16.0 to 16.0, 25.0 to 19.0, 50.0 to 24.0, 95.0 to 30.0)
            val entries = mutableListOf<CableCatalogEntry>()
            for (tc in listOf(1, 2)) {
                for ((sz, od) in sizes) {
                    val current = sz * 3.0 + 10.0
                    entries.add(
                        CableCatalogEntry("Elsewedy", tc, sz, "M-$sz-T$tc", current, current, current, od, sz * 0.03 + 0.2)
                    )
                }
            }
            Catalog.cableCatalog = entries
        }
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = null
    }

    private fun referenceCircuits(pid: String, count: Int = 60): List<CircuitEntity> =
        (1..count).map { id ->
            CircuitEntity(
                id = id,
                circuitName = "C-$id",
                projectId = pid,
                loadKVA = 10.0 + (id % 10),
                voltageV = 400,
                pf = 0.85,
                length_m = 25.0,
                cableType = "Cu/XLPE",
                installation = "In air",
                fedFrom = if (id == 1) "Transformer" else "C-${id - 1}",
                parentId = if (id == 1) null else id - 1,
                transformerId = if (id == 1) "TX-01" else ""
            )
        }

    private fun referenceTransformer(pid: String) = TransformerEntity(
        projectId = pid,
        transformerId = "TX-01",
        transformerName = "Main Tx",
        kVA = 1000.0,
        voltage = 400,
        percentZ = 5.0,
        xr = 5.0
    )

    /**
     * RED before the fix: 603 resolutions (120/3/180/60/120/120).
     * GREEN after the fix: at most 1 resolution per pass stage (<= 6 total).
     */
    @Test
    fun fullUiStatePass_resolvesProjectRevisionAtMostOncePerPass() {
        val pid = "PRJ_HOIST_60"
        val provider = CountingRevisionProvider(revisionValue = 2L)
        EngineeringCalculationCoordinator.projectRevisionProvider = provider

        val circuits = referenceCircuits(pid)
        val transformers = listOf(referenceTransformer(pid))

        val perPass = linkedMapOf<String, Int>()
        fun now() = provider.invocationCount.get()
        fun mark(label: String, before: Int) {
            perPass[label] = now() - before
        }

        var before = now()
        val cableRes = EngineeringCalculationCoordinator.calculateProjectCableSizing(circuits, transformers)
        mark("Pass 1 Cable Sizing", before)

        before = now()
        val txRes = EngineeringCalculationCoordinator.calculateProjectTransformers(circuits, transformers)
        mark("Pass 2 Transformers", before)

        before = now()
        val scRes = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes
        )
        mark("Pass 3 Short Circuit", before)

        before = now()
        EngineeringCalculationCoordinator.calculateProjectAds(
            resolvedCircuits = circuits,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes
        )
        mark("Pass 4 ADS", before)

        before = now()
        EngineeringCalculationCoordinator.calculateProjectProtection(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            shortCircuitResults = scRes,
            transformerResults = txRes,
            allCircuits = circuits
        )
        mark("Pass 5 Protection", before)

        before = now()
        EngineeringCalculationCoordinator.calculateProjectCoordination(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes,
            allCircuits = circuits
        )
        mark("Pass 6 Coordination", before)

        val total = now()
        println("[Gate D' Hoist] per-pass revision resolutions: $perPass total=$total")

        perPass.forEach { (pass, count) ->
            assertTrue(
                "Gate D' Option A: $pass must resolve the project revision at most once, observed=$count",
                count <= 1
            )
        }
        assertTrue(
            "Gate D' Option A: one uiState pass must resolve the revision at most once per pass stage (<= 6), observed=$total",
            total <= 6
        )
    }
    /**
     * RED before the fix: the failing read is swallowed and revision 1L is fabricated, so the stale
     * cache entry at revision 1L is returned and the pass completes silently.
     * GREEN after the fix: [ProjectRevisionResolutionException] is raised by the guarded read, the
     * coherent snapshot, and the calculation pass — never a fabricated revision.
     */
    @Test
    fun indeterminateRevisionRead_failsLoudlyInsteadOfFabricatingRevision() {
        val pid = "PRJ_LOUD_FAIL"
        val circuit = referenceCircuits(pid, count = 1).first().copy(
            id = 501,
            circuitName = "C-501",
            fedFrom = "Transformer",
            parentId = null
        )

        // Healthy provider first: publish a cache entry stamped with revision 1L.
        EngineeringCalculationCoordinator.projectRevisionProvider = CountingRevisionProvider(revisionValue = 1L)
        EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit))

        // The revision store becomes indeterminate (SQLiteDatabaseLockedException / Room timeout).
        EngineeringCalculationCoordinator.projectRevisionProvider =
            CountingRevisionProvider(failWith = IOException("SQLiteDatabaseLockedException (simulated)"))

        var guardedFailure: Throwable? = null
        try {
            val cached = EngineeringCalculationCoordinator.getCachedSizingResultGuarded(pid, 501, expectedRevision = 1L)
            assertNull("Stale cache entry must never be returned when the live revision is unresolvable", cached)
        } catch (t: Throwable) {
            guardedFailure = t
        }
        assertTrue(
            "Guarded read must fail loudly with ProjectRevisionResolutionException, observed=${guardedFailure?.javaClass?.simpleName}",
            guardedFailure is ProjectRevisionResolutionException
        )

        var snapshotFailure: Throwable? = null
        try {
            EngineeringCalculationCoordinator.getCoherentSnapshot<SizingResult>(
                projectId = pid,
                domain = CalculationDomain.CABLE_SIZING,
                entityId = "501",
                expectedRevision = 1L
            )
        } catch (t: Throwable) {
            snapshotFailure = t
        }
        assertTrue(
            "Coherent snapshot must fail loudly with ProjectRevisionResolutionException, observed=${snapshotFailure?.javaClass?.simpleName}",
            snapshotFailure is ProjectRevisionResolutionException
        )

        var passFailure: Throwable? = null
        try {
            EngineeringCalculationCoordinator.calculateProjectCableSizing(listOf(circuit), emptyList())
        } catch (t: Throwable) {
            passFailure = t
        }
        assertTrue(
            "Calculation pass must fail loudly with ProjectRevisionResolutionException, observed=${passFailure?.javaClass?.simpleName}",
            passFailure is ProjectRevisionResolutionException
        )
    }
}

