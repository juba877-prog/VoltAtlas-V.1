package com.example.engine.coordinator

import com.example.data.*
import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class RevisionCorrectnessTest {

    private class MockRevisionProvider : ProjectRevisionProvider {
        val revisions = ConcurrentHashMap<String, Long>()
        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            val rev = revisions[projectId] ?: throw NoSuchElementException("Project $projectId not found")
            return ProjectRevisionSnapshot(projectId, rev, System.currentTimeMillis())
        }
    }

    private val revisionProvider = MockRevisionProvider()

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

    @Test
    fun testProviderNull_usesExplicitContractRevision1L() {
        val pid = "PRJ_NULL_PROVIDER"
        val circuit = CircuitEntity(
            id = 1,
            circuitName = "C-1",
            projectId = pid,
            loadKVA = 20.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 20.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = ""
        )

        // When projectRevisionProvider is null, calculations publish cache entry with revision 1L
        val res = EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit))
        assertNotNull(res)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        val rec = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope)
        assertNotNull(rec)
        assertEquals(1L, rec!!.calculatedProjectRevision)
    }

    @Test
    fun testProviderPresent_usesAuthoritativeRevisionGreaterThanOne() = runTest {
        val pid = "PRJ_REV_5"
        revisionProvider.revisions[pid] = 5L
        EngineeringCalculationCoordinator.projectRevisionProvider = revisionProvider

        val circuit = CircuitEntity(
            id = 1,
            circuitName = "C-1",
            projectId = pid,
            loadKVA = 20.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 20.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = ""
        )

        // Synchronous calculation records authoritative revision 5L
        val res = EngineeringCalculationCoordinator.calculateCableSizing(circuit, listOf(circuit))
        assertNotNull(res)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        val rec = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope)
        assertNotNull(rec)
        assertEquals(5L, rec!!.calculatedProjectRevision)
    }

    @Test
    fun testGuardedCalculation_usesExactProviderRevision() = runTest {
        val pid = "PRJ_GUARDED_REV"
        revisionProvider.revisions[pid] = 42L
        EngineeringCalculationCoordinator.projectRevisionProvider = revisionProvider

        val circuit = CircuitEntity(
            id = 10,
            circuitName = "C-10",
            projectId = pid,
            loadKVA = 35.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 15.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = ""
        )

        val guardedRes = EngineeringCalculationCoordinator.calculateCableSizingGuarded(
            circuit = circuit,
            allCircuits = listOf(circuit),
            expectedRevision = 42L
        )

        assertTrue(guardedRes is GuardedCalculationResult.Published)
        val published = guardedRes as GuardedCalculationResult.Published
        assertEquals(42L, published.revision)

        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "10", CalculationDomain.CABLE_SIZING)
        val rec = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope)
        assertNotNull(rec)
        assertEquals(42L, rec!!.calculatedProjectRevision)
    }

    @Test
    fun testGuardedCalculation_rejectsSupersededRevision() = runTest {
        val pid = "PRJ_SUPERSEDED"
        revisionProvider.revisions[pid] = 7L
        EngineeringCalculationCoordinator.projectRevisionProvider = revisionProvider

        val circuit = CircuitEntity(
            id = 20,
            circuitName = "C-20",
            projectId = pid,
            loadKVA = 15.0,
            voltageV = 400,
            pf = 0.85,
            length_m = 10.0,
            cableType = "Cu/XLPE",
            installation = "In air",
            fedFrom = "Transformer",
            transformerId = ""
        )

        // Attempting calculation with expectedRevision = 6L when live revision is 7L
        val guardedRes = EngineeringCalculationCoordinator.calculateCableSizingGuarded(
            circuit = circuit,
            allCircuits = listOf(circuit),
            expectedRevision = 6L
        )

        assertTrue(guardedRes is GuardedCalculationResult.Superseded)
        val superseded = guardedRes as GuardedCalculationResult.Superseded
        assertEquals(6L, superseded.expectedRevision)
        assertEquals(7L, superseded.actualRevision)

        // Metadata registry must NOT contain published record for superseded execution
        val scope = CalculationScope(pid, CalculationEntityType.CIRCUIT, "20", CalculationDomain.CABLE_SIZING)
        assertNull(EngineeringCalculationCoordinator.freshnessRegistry.getRecord(scope))
    }
}
