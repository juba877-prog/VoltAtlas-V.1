package com.example.engine.coordinator

import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.data.*
import com.example.engine.SizingResult
import com.example.ai.context.freshness.contracts.CalculationDomain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gate D Measurement & CAS Write-Hazard Audit Suite.
 *
 * Requirements:
 * 1. Non-shipping instrumentation: Atomic invocation counter is test-scope only.
 * 2. Measure before redesigning: Measure exact invocation count across all 6 calculation passes
 *    and determine the calling thread / dispatcher behavior.
 * 3. CAS write-hazard audit: Prove that catch (_: Exception) { 1L } silently swallows query failures
 *    and returns 1L, which can cause stale cache acceptance and invalid Superseded reports.
 */
class CoordinatorInvocationMeasurementTest {

    private class InstrumentedRevisionProvider(
        private val delegateRevision: Long = 1L,
        private val shouldThrow: Boolean = false
    ) : ProjectRevisionProvider {
        val invocationCount = AtomicInteger(0)
        val invokingThreads = ConcurrentHashMap.newKeySet<String>()

        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            invocationCount.incrementAndGet()
            invokingThreads.add(Thread.currentThread().name)
            if (shouldThrow) {
                throw IOException("Simulated SQLiteDatabaseLockedException or Room query timeout")
            }
            return ProjectRevisionSnapshot(projectId, delegateRevision, System.currentTimeMillis())
        }
    }

    @Before
    fun setUp() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

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

    @Test
    fun measurePerPassInvocationCount_andDispatcherBehavior() {
        val pid = "PRJ_MEASURE_60"
        val instrumentedProvider = InstrumentedRevisionProvider(delegateRevision = 2L)
        EngineeringCalculationCoordinator.projectRevisionProvider = instrumentedProvider

        val circuitCount = 60
        val circuits = (1..circuitCount).map { id ->
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
        val transformers = listOf(
            TransformerEntity(
                projectId = pid,
                transformerId = "TX-01",
                transformerName = "Main Tx",
                kVA = 1000.0,
                voltage = 400,
                percentZ = 5.0,
                xr = 5.0
            )
        )

        // Pass 1: Cable Sizing
        val countBeforePass1 = instrumentedProvider.invocationCount.get()
        val cableRes = EngineeringCalculationCoordinator.calculateProjectCableSizing(circuits, transformers)
        val pass1Invocations = instrumentedProvider.invocationCount.get() - countBeforePass1

        // Pass 2: Transformers
        val countBeforePass2 = instrumentedProvider.invocationCount.get()
        val txRes = EngineeringCalculationCoordinator.calculateProjectTransformers(circuits, transformers)
        val pass2Invocations = instrumentedProvider.invocationCount.get() - countBeforePass2

        // Pass 3: Short Circuit
        val countBeforePass3 = instrumentedProvider.invocationCount.get()
        val scRes = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes
        )
        val pass3Invocations = instrumentedProvider.invocationCount.get() - countBeforePass3

        // Pass 4: ADS
        val countBeforePass4 = instrumentedProvider.invocationCount.get()
        val adsRes = EngineeringCalculationCoordinator.calculateProjectAds(
            resolvedCircuits = circuits,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes
        )
        val pass4Invocations = instrumentedProvider.invocationCount.get() - countBeforePass4

        // Pass 5: Protection
        val countBeforePass5 = instrumentedProvider.invocationCount.get()
        val protRes = EngineeringCalculationCoordinator.calculateProjectProtection(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            shortCircuitResults = scRes,
            transformerResults = txRes,
            allCircuits = circuits
        )
        val pass5Invocations = instrumentedProvider.invocationCount.get() - countBeforePass5

        // Pass 6: Coordination
        val countBeforePass6 = instrumentedProvider.invocationCount.get()
        val coordRes = EngineeringCalculationCoordinator.calculateProjectCoordination(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes,
            allCircuits = circuits
        )
        val pass6Invocations = instrumentedProvider.invocationCount.get() - countBeforePass6

        val totalInvocations = instrumentedProvider.invocationCount.get()

        println("[Gate D Measurement] 60 Circuits Reference Run:")
        println("  Pass 1 (Cable Sizing):    $pass1Invocations invocations")
        println("  Pass 2 (Transformers):    $pass2Invocations invocations")
        println("  Pass 3 (Short Circuit):   $pass3Invocations invocations")
        println("  Pass 4 (ADS):             $pass4Invocations invocations")
        println("  Pass 5 (Protection):      $pass5Invocations invocations")
        println("  Pass 6 (Coordination):    $pass6Invocations invocations")
        println("  Total per uiState pass:   $totalInvocations invocations")
        println("  Invoking threads:         ${instrumentedProvider.invokingThreads}")

        // Invariants
        val baseThreadName = Thread.currentThread().name.substringBefore(" @coroutine")
        assertTrue("Invoking threads must all be on the caller thread due to runBlocking: ${instrumentedProvider.invokingThreads}", 
            instrumentedProvider.invokingThreads.all { it.startsWith(baseThreadName) })
    }

    @Test
    fun testSilentExceptionSwallowing_swallowsFailureAndReturnsDefault1L() {
        val pid = "PRJ_FAILING_PROVIDER"
        val initialProvider = InstrumentedRevisionProvider(delegateRevision = 1L, shouldThrow = false)
        EngineeringCalculationCoordinator.projectRevisionProvider = initialProvider

        // Populate a cache entry with revision 1L (e.g. from initial project creation)
        val dummyCircuit = CircuitEntity(
            id = 999,
            projectId = pid,
            circuitName = "Feeder-999",
            loadKVA = 50.0,
            cableType = "Cu/XLPE",
            voltageV = 400
        )
        EngineeringCalculationCoordinator.calculateCableSizing(dummyCircuit, listOf(dummyCircuit))

        // Now switch to failing provider simulating SQLiteDatabaseLockedException or Room query timeout
        val failingProvider = InstrumentedRevisionProvider(delegateRevision = 5L, shouldThrow = true)
        EngineeringCalculationCoordinator.projectRevisionProvider = failingProvider

        // 1. Guarded cache check: caller asks for expectedRevision = 1L
        // The live database is actually failing, but getProjectRevision catches the exception and returns 1L.
        // Therefore, entry.projectRevision (1L) == liveRev (1L) matches!
        val cached = EngineeringCalculationCoordinator.getCachedSizingResultGuarded(pid, 999, expectedRevision = 1L)
        assertNotNull(
            "VULNERABILITY PROOF: Stale cache entry at revision 1L was accepted because exception was swallowed and defaulted to 1L",
            cached
        )

        // 2. Coherent snapshot check: liveRev falls back to 1L
        val snapshot = EngineeringCalculationCoordinator.getCoherentSnapshot<SizingResult>(
            projectId = pid,
            domain = com.example.ai.context.freshness.contracts.CalculationDomain.CABLE_SIZING,
            entityId = "999",
            expectedRevision = 1L
        )
        assertNotNull(
            "VULNERABILITY PROOF: Coherent snapshot returned valid at revision 1L despite database failure",
            snapshot
        )
    }
}
