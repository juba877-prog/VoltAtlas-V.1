package com.example.engine.coordinator

import com.example.data.*
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CoordinatorNonBlockingExecutionTest {

    private class TestRevisionProvider : ProjectRevisionProvider {
        val revisions = ConcurrentHashMap<String, Long>()
        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            val rev = revisions[projectId] ?: 1L
            return ProjectRevisionSnapshot(projectId, rev, System.currentTimeMillis())
        }
    }

    private val revisionProvider = TestRevisionProvider()

    @Before
    fun setUp() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = revisionProvider

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
    fun bulkCalculationsExecuteWithoutThreadDeadlock() {
        val pid = "PRJ_NON_BLOCKING_BULK"
        revisionProvider.revisions[pid] = 1L

        val circuits = (1..20).map { id ->
            CircuitEntity(
                id = id,
                circuitName = "C-$id",
                projectId = pid,
                loadKVA = 10.0 + id,
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

        // 1. Bulk Cable Sizing
        val cableRes = EngineeringCalculationCoordinator.calculateProjectCableSizing(circuits, transformers)
        assertEquals(20, cableRes.sizingResults.size)

        // Verify Freshness Metadata was synchronously recorded for all circuits
        for (c in circuits) {
            val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, c.id.toString(), CalculationDomain.CABLE_SIZING)
            val record = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope)
            assertNotNull("Freshness record should exist for circuit ${c.id}", record)
            assertEquals(1L, record!!.calculatedProjectRevision)
        }

        // 2. Bulk Transformer Calculation
        val txRes = EngineeringCalculationCoordinator.calculateProjectTransformers(circuits, transformers)
        assertEquals(1, txRes.size)
        val txScope = CalculationScope(pid, CalculationEntityType.TRANSFORMER, "TX-01", CalculationDomain.TRANSFORMER)
        assertNotNull(EngineeringCalculationCoordinator.freshnessRegistry.getRecord(txScope))

        // 3. Bulk Short Circuit Calculation
        val scRes = EngineeringCalculationCoordinator.calculateProjectShortCircuit(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes
        )
        assertEquals(20, scRes.size)

        // 4. Bulk Protection Calculation
        val protRes = EngineeringCalculationCoordinator.calculateProjectProtection(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            shortCircuitResults = scRes,
            transformerResults = txRes,
            allCircuits = circuits
        )
        assertEquals(20, protRes.size)

        // 5. Bulk Coordination Calculation
        val coordRes = EngineeringCalculationCoordinator.calculateProjectCoordination(
            resolvedCircuits = circuits,
            circuitParents = cableRes.circuitParents,
            sizingResults = cableRes.sizingResults,
            transformerResults = txRes,
            allCircuits = circuits
        )
        assertEquals(20, coordRes.size)
    }

    @Test
    fun guardedCalculationsAwaitsMutexNativelyUnderCoroutine() = runTest {
        val pid = "PRJ_GUARDED_NATIVE"
        revisionProvider.revisions[pid] = 1L

        val mainCircuit = CircuitEntity(
            id = 101,
            circuitName = "Main-Feeder",
            projectId = pid,
            loadKVA = 50.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 30.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = "TX-01"
        )
        val allCircuits = listOf(mainCircuit)
        val transformers = listOf(
            TransformerEntity(projectId = pid, transformerId = "TX-01", kVA = 500.0, voltage = 400)
        )

        // 1. Guarded Cable Sizing
        val sizingResult = EngineeringCalculationCoordinator.calculateCableSizingGuarded(
            circuit = mainCircuit,
            allCircuits = allCircuits,
            transformers = transformers,
            expectedRevision = 1L
        )
        assertTrue(sizingResult is GuardedCalculationResult.Published)
        assertEquals(1L, (sizingResult as GuardedCalculationResult.Published).revision)

        // 2. Guarded Short Circuit
        val scResult = EngineeringCalculationCoordinator.calculateShortCircuitGuarded(
            circuitId = 101,
            allCircuits = allCircuits,
            allTransformers = transformers,
            expectedRevision = 1L
        )
        assertTrue(scResult is GuardedCalculationResult.Published)

        // 3. Guarded Protection
        val protResult = EngineeringCalculationCoordinator.calculateProtectionGuarded(
            circuitId = 101,
            allCircuits = allCircuits,
            allTransformers = transformers,
            expectedRevision = 1L
        )
        assertTrue(protResult is GuardedCalculationResult.Published)

        // 4. Superseded on revision conflict
        val supersededResult = EngineeringCalculationCoordinator.calculateCableSizingGuarded(
            circuit = mainCircuit,
            allCircuits = allCircuits,
            transformers = transformers,
            expectedRevision = 999L
        )
        assertTrue(supersededResult is GuardedCalculationResult.Superseded)
        val superseded = supersededResult as GuardedCalculationResult.Superseded
        assertEquals(999L, superseded.expectedRevision)
        assertEquals(1L, superseded.actualRevision)
    }

    @Test
    fun concurrentGuardedCalculationsSerializeCleanlyAcrossCoroutines() = runTest {
        val pid = "PRJ_CONCURRENT_GUARDED"
        revisionProvider.revisions[pid] = 1L

        val circuit = CircuitEntity(
            id = 501,
            circuitName = "Concurrent-Circuit",
            projectId = pid,
            loadKVA = 30.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 20.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = ""
        )
        val allCircuits = listOf(circuit)

        val completedCount = AtomicInteger(0)
        val jobs = (1..10).map {
            launch(Dispatchers.Default) {
                val res = EngineeringCalculationCoordinator.calculateCableSizingGuarded(
                    circuit = circuit,
                    allCircuits = allCircuits,
                    transformers = emptyList(),
                    expectedRevision = 1L
                )
                if (res is GuardedCalculationResult.Published) {
                    completedCount.incrementAndGet()
                }
            }
        }
        jobs.joinAll()
        assertEquals(10, completedCount.get())
    }
}
