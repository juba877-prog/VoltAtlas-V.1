package com.example.ui.screens.circuits

import com.example.data.CircuitEntity
import com.example.engine.CableFailureCode
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.ui.screens.CircuitChildLookup
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CircuitCardModelCacheTest {

    private lateinit var cache: CircuitCardModelCache

    @Before
    fun setUp() {
        cache = CircuitCardModelCache()
    }

    private fun createCircuit(
        id: Int,
        name: String = "Circuit-$id",
        loadKVA: Double = 50.0,
        fedFrom: String = "MDB-1",
        parentId: Int? = null,
        panelType: String? = null,
        cableType: String = "Cu/XLPE/PVC",
        lengthM: Double = 30.0,
        maxVDpct: Double = 0.03,
        loadType: String = "LIGHTING",
        overrideCableSize: String? = null
    ) = CircuitEntity(
        id = id,
        projectId = "PROJECT_1",
        circuitName = name,
        loadKVA = loadKVA,
        fedFrom = fedFrom,
        parentId = parentId,
        panelType = panelType,
        cableType = cableType,
        length_m = lengthM,
        maxVDpct = maxVDpct,
        loadType = loadType,
        overrideCableSize = overrideCableSize
    )

    private fun createSizing(
        status: SizingResultStatus = SizingResultStatus.PASS,
        cableSize: String = "70",
        ibA: Double = 120.0,
        deviceSizeA: Double = 160.0,
        deviceType: String = "MCCB",
        vdPct: Double = 2.45,
        utilizationPct: Double = 65.0,
        izA: Double = 185.0,
        breakingKA: Double = 36.0
    ) = SizingResult(
        Ib_A = ibA,
        deviceSize_A = deviceSizeA,
        deviceType = deviceType,
        cTotalDerating = 0.8,
        iRequired_perCable_A = 150.0,
        productCode = "PROD-1",
        cableSize_mm2 = cableSize,
        ratedI_table_A = 200.0,
        capacity_derated_Iz_A = izA,
        utilizationPct = utilizationPct,
        neutral_mm2 = "35",
        earthCPC_mm2 = "35",
        voltDrop_V = 5.0,
        vdPct = vdPct,
        cumulativeVDpct = vdPct,
        status = if (status == SizingResultStatus.PASS) "✓ PASS" else "✗ FAIL",
        deviceBreakingKA = breakingKA,
        resultStatus = status,
        isAmpacityOk = status == SizingResultStatus.PASS,
        isVoltageDropOk = status == SizingResultStatus.PASS,
        primaryFailureCode = if (status == SizingResultStatus.FAIL) CableFailureCode.AMPACITY_FAIL else null
    )

    private fun createShortCircuit(
        circuitId: Int,
        iscKA: Double = 25.0
    ) = ShortCircuitResult(
        circuitId = circuitId,
        sourceName = "Source",
        cableMm2 = "70",
        iscKA = iscKA,
        iwKA = 10.0,
        deviceKA = 36.0,
        deviceCheck = "OK",
        cableCheck = "OK",
        marginPct = 20.0,
        status = "PASS",
        note = ""
    )

    // =========================================================================
    // REFERENCE IDENTITY & RECOMPUTATION ISOLATION TESTS
    // =========================================================================

    @Test
    fun testIdenticalInputsReturnSameCachedModelInstance() {
        val c1 = createCircuit(1)
        val sizing = createSizing()
        val sc = createShortCircuit(1)

        val firstModel = cache.getOrCompute(
            circuit = c1,
            sizingResult = sizing,
            shortCircuitResult = sc,
            parentName = null,
            hasChildren = false
        )

        val secondModel = cache.getOrCompute(
            circuit = c1,
            sizingResult = sizing,
            shortCircuitResult = sc,
            parentName = null,
            hasChildren = false
        )

        assertSame("Identical inputs must return the exact same cached model instance", firstModel, secondModel)
    }

    @Test
    fun testUpdatingSingleCircuitEntityReturnsNewModelAndPreservesUnrelated() {
        val c1 = createCircuit(1, "Circuit-1", loadKVA = 50.0)
        val c2 = createCircuit(2, "Circuit-2", loadKVA = 80.0)
        val c3 = createCircuit(3, "Circuit-3", loadKVA = 100.0)
        val circuits = listOf(c1, c2, c3)
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }
        val childLookup = CircuitChildLookup.build(circuits)

        // Initial build
        val initialModels = circuits.associate { circ ->
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Mutate only c2 (loadKVA changed from 80 to 95)
        val c2Updated = c2.copy(loadKVA = 95.0)
        val updatedCircuits = listOf(c1, c2Updated, c3)

        val updatedModels = updatedCircuits.associate { circ ->
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Circuit 2 received a new model instance reflecting the updated value
        assertNotSame("Mutated circuit must receive a new model instance", initialModels[2], updatedModels[2])
        assertEquals("95.0 kVA", updatedModels[2]?.loadDisplay)

        // Unrelated circuits 1 and 3 retain exact reference identity
        assertSame("Unrelated circuit 1 must retain identical instance", initialModels[1], updatedModels[1])
        assertSame("Unrelated circuit 3 must retain identical instance", initialModels[3], updatedModels[3])
    }

    @Test
    fun testUpdatingSingleSizingResultReplacesOnlyTargetModel() {
        val circuits = listOf(createCircuit(1), createCircuit(2), createCircuit(3))
        val sizingResults = circuits.associate { it.id to createSizing(cableSize = "70") }.toMutableMap()
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }
        val childLookup = CircuitChildLookup.build(circuits)

        // Initial build
        val initialModels = circuits.associate { circ ->
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Update sizing result for circuit 2 only (cableSize 70 -> 95)
        sizingResults[2] = createSizing(cableSize = "95")

        val updatedModels = circuits.associate { circ ->
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertNotSame("Circuit with updated sizing result must receive a new instance", initialModels[2], updatedModels[2])
        assertEquals("95 mm²", updatedModels[2]?.cableSizeDisplay)

        assertSame("Circuit 1 must retain identical instance", initialModels[1], updatedModels[1])
        assertSame("Circuit 3 must retain identical instance", initialModels[3], updatedModels[3])
    }

    @Test
    fun testUpdatingSingleShortCircuitResultReplacesOnlyTargetModel() {
        val circuits = listOf(createCircuit(1), createCircuit(2), createCircuit(3))
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id, iscKA = 20.0) }.toMutableMap()
        val childLookup = CircuitChildLookup.build(circuits)

        // Initial build
        val initialModels = circuits.associate { circ ->
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Update short-circuit result for circuit 3 only (20kA -> 35kA)
        scResults[3] = createShortCircuit(3, iscKA = 35.0)

        val updatedModels = circuits.associate { circ ->
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertNotSame("Circuit with updated short-circuit result must receive a new instance", initialModels[3], updatedModels[3])
        assertEquals("35.0 kA", updatedModels[3]?.isc3phDisplay)

        assertSame("Circuit 1 must retain identical instance", initialModels[1], updatedModels[1])
        assertSame("Circuit 2 must retain identical instance", initialModels[2], updatedModels[2])
    }

    @Test
    fun testParentRenameInvalidatesOnlyParentAndItsChildren() {
        val parent = createCircuit(1, "Main-Panel")
        val child1 = createCircuit(2, "Sub-1", fedFrom = "Main-Panel", parentId = 1)
        val child2 = createCircuit(3, "Sub-2", fedFrom = "Main-Panel", parentId = 1)
        val unrelated = createCircuit(4, "Lighting-1", fedFrom = "Transformer")
        val circuits = listOf(parent, child1, child2, unrelated)
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }

        var childLookup = CircuitChildLookup.build(circuits)
        var parentNames = circuits.associate { it.id to it.circuitName }

        // Initial build
        val initialModels = circuits.associate { circ ->
            val parentName = circ.parentId?.let { pId -> if (pId != circ.id) parentNames[pId] else null }
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = parentName,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Rename parent: "Main-Panel" -> "Main-Distribution-Board"
        val renamedParent = parent.copy(circuitName = "Main-Distribution-Board")
        val newCircuits = listOf(renamedParent, child1, child2, unrelated)
        childLookup = CircuitChildLookup.build(newCircuits)
        parentNames = newCircuits.associate { it.id to it.circuitName }

        val newModels = newCircuits.associate { circ ->
            val parentName = circ.parentId?.let { pId -> if (pId != circ.id) parentNames[pId] else null }
            circ.id to cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = parentName,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Parent and its children receive updated models
        assertNotSame("Renamed parent must receive new model", initialModels[1], newModels[1])
        assertNotSame("Child 1 must receive new model with updated breadcrumb", initialModels[2], newModels[2])
        assertNotSame("Child 2 must receive new model with updated breadcrumb", initialModels[3], newModels[3])
        assertEquals("Main-Distribution-Board > Sub-1", newModels[2]?.breadcrumbDisplay)
        assertEquals("Main-Distribution-Board > Sub-2", newModels[3]?.breadcrumbDisplay)

        // Unrelated circuit 4 preserves exact reference identity
        assertSame("Unrelated circuit 4 must retain identical instance", initialModels[4], newModels[4])
    }

    @Test
    fun testCircuitAdditionBuildsOnlyNewModelAndPreservesExisting() {
        val c1 = createCircuit(1, "Circuit-1")
        val c2 = createCircuit(2, "Circuit-2")
        val circuits = listOf(c1, c2)
        val sizing = circuits.associate { it.id to createSizing() }.toMutableMap()
        val sc = circuits.associate { it.id to createShortCircuit(it.id) }.toMutableMap()
        var childLookup = CircuitChildLookup.build(circuits)

        val m1 = cache.getOrCompute(c1, sizing[1], sc[1], null, childLookup.hasChildren(c1.circuitName))
        val m2 = cache.getOrCompute(c2, sizing[2], sc[2], null, childLookup.hasChildren(c2.circuitName))

        // Add c3
        val c3 = createCircuit(3, "Circuit-3")
        val expandedCircuits = listOf(c1, c2, c3)
        sizing[3] = createSizing()
        sc[3] = createShortCircuit(3)
        childLookup = CircuitChildLookup.build(expandedCircuits)

        val newM1 = cache.getOrCompute(c1, sizing[1], sc[1], null, childLookup.hasChildren(c1.circuitName))
        val newM2 = cache.getOrCompute(c2, sizing[2], sc[2], null, childLookup.hasChildren(c2.circuitName))
        val newM3 = cache.getOrCompute(c3, sizing[3], sc[3], null, childLookup.hasChildren(c3.circuitName))

        assertSame("Existing circuit 1 must retain identical instance", m1, newM1)
        assertSame("Existing circuit 2 must retain identical instance", m2, newM2)
        assertNotNull("Added circuit 3 must have a valid model", newM3)
    }

    @Test
    fun testCircuitDeletionPrunesCacheAndPreservesUnrelated() {
        val c1 = createCircuit(1, "Circuit-1")
        val c2 = createCircuit(2, "Circuit-2")
        val c3 = createCircuit(3, "Circuit-3")
        val circuits = listOf(c1, c2, c3)
        val sizing = circuits.associate { it.id to createSizing() }
        val sc = circuits.associate { it.id to createShortCircuit(it.id) }
        var childLookup = CircuitChildLookup.build(circuits)

        val m1 = cache.getOrCompute(c1, sizing[1], sc[1], null, childLookup.hasChildren(c1.circuitName))
        val m2 = cache.getOrCompute(c2, sizing[2], sc[2], null, childLookup.hasChildren(c2.circuitName))
        val m3 = cache.getOrCompute(c3, sizing[3], sc[3], null, childLookup.hasChildren(c3.circuitName))
        assertNotNull(m2)
        assertEquals(3, cache.getCachedEntryCount())

        // Delete c2
        val remainingCircuits = listOf(c1, c3)
        cache.sync(remainingCircuits)
        assertEquals(2, cache.getCachedEntryCount())
        assertFalse("Deleted circuit 2 must be pruned from cache", cache.cachedIdsForTesting().contains(2))
        childLookup = CircuitChildLookup.build(remainingCircuits)

        val newM1 = cache.getOrCompute(c1, sizing[1], sc[1], null, childLookup.hasChildren(c1.circuitName))
        val newM3 = cache.getOrCompute(c3, sizing[3], sc[3], null, childLookup.hasChildren(c3.circuitName))

        assertSame("Unrelated circuit 1 must retain identical instance", m1, newM1)
        assertSame("Unrelated circuit 3 must retain identical instance", m3, newM3)
    }

    // =========================================================================
    // CORRECTION B — EQUAL-COUNT REPLACEMENT TEST
    // =========================================================================

    @Test
    fun testEqualCountReplacementPrunesStaleIdAndReusesActive() {
        val c1 = createCircuit(1, "Circuit-1")
        val c2 = createCircuit(2, "Circuit-2")
        val c3 = createCircuit(3, "Circuit-3")
        val initialCircuits = listOf(c1, c2, c3)
        val sizing = initialCircuits.associate { it.id to createSizing() }.toMutableMap()
        val sc = initialCircuits.associate { it.id to createShortCircuit(it.id) }.toMutableMap()
        var childLookup = CircuitChildLookup.build(initialCircuits)

        val m1 = cache.getOrCompute(c1, sizing[1], sc[1], null, childLookup.hasChildren(c1.circuitName))
        val m2 = cache.getOrCompute(c2, sizing[2], sc[2], null, childLookup.hasChildren(c2.circuitName))
        val m3 = cache.getOrCompute(c3, sizing[3], sc[3], null, childLookup.hasChildren(c3.circuitName))
        assertNotNull(m3)

        assertEquals("Initial cache must have 3 entries", 3, cache.getCachedEntryCount())
        assertEquals(setOf(1, 2, 3), cache.cachedIdsForTesting())

        // Equal-count replacement: IDs 1, 2, 3 -> 1, 2, 4 (Count remains 3)
        val c4 = createCircuit(4, "Circuit-4")
        val replacementCircuits = listOf(c1, c2, c4)
        sizing[4] = createSizing()
        sc[4] = createShortCircuit(4)
        childLookup = CircuitChildLookup.build(replacementCircuits)

        // 1. Call sync with replacement circuits (equal size list)
        cache.sync(replacementCircuits)

        // 2. Assert stale ID 3 was pruned immediately
        assertFalse("Stale ID 3 must be pruned even when list size is unchanged", cache.cachedIdsForTesting().contains(3))
        assertEquals(setOf(1, 2), cache.cachedIdsForTesting())
        assertEquals(2, cache.getCachedEntryCount())

        // 3. Request models for active circuits 1, 2, 4
        val newM1 = cache.getOrCompute(c1, sizing[1], sc[1], null, childLookup.hasChildren(c1.circuitName))
        val newM2 = cache.getOrCompute(c2, sizing[2], sc[2], null, childLookup.hasChildren(c2.circuitName))
        val newM4 = cache.getOrCompute(c4, sizing[4], sc[4], null, childLookup.hasChildren(c4.circuitName))

        // 4. Assert active unchanged IDs 1 and 2 retain reference identity
        assertSame("Unchanged circuit 1 must retain identical instance", m1, newM1)
        assertSame("Unchanged circuit 2 must retain identical instance", m2, newM2)
        assertNotNull("New circuit 4 must receive a valid model", newM4)

        // 5. Assert final cache state contains exactly active IDs {1, 2, 4} with size 3
        assertEquals("Cache must contain only active IDs 1, 2, 4", setOf(1, 2, 4), cache.cachedIdsForTesting())
        assertEquals("Cache size must be exactly 3", 3, cache.getCachedEntryCount())
    }

    @Test
    fun testOverrideChangeUpdatesModelCorrectly() {
        val c1 = createCircuit(1, "Feeder-1", overrideCableSize = null)
        val sizing = createSizing(cableSize = "70")
        val sc = createShortCircuit(1)

        val modelBefore = cache.getOrCompute(
            circuit = c1,
            sizingResult = sizing,
            shortCircuitResult = sc,
            parentName = null,
            hasChildren = false
        )
        assertEquals("70 mm²", modelBefore.cableSizeDisplay)

        // Apply override
        val c1Overridden = c1.copy(overrideCableSize = "150")
        val sizingOverridden = createSizing(cableSize = "150")

        val modelAfter = cache.getOrCompute(
            circuit = c1Overridden,
            sizingResult = sizingOverridden,
            shortCircuitResult = sc,
            parentName = null,
            hasChildren = false
        )
        assertNotSame("Overridden circuit must receive a new model instance", modelBefore, modelAfter)
        assertEquals("150 mm²", modelAfter.cableSizeDisplay)
    }

    @Test
    fun testBuswayCardStaysBuswaySpecific() {
        val buswayCircuit = createCircuit(1, "Busway-Feeder", cableType = "Cu/Busway/Standard")
        val sizing = createSizing(cableSize = "800A Busbar")
        val sc = createShortCircuit(1)

        val model = cache.getOrCompute(
            circuit = buswayCircuit,
            sizingResult = sizing,
            shortCircuitResult = sc,
            parentName = null,
            hasChildren = false
        )

        assertEquals("Cu/Busway/Standard • Length: 30m", model.footerSpecDisplay)
        assertEquals("800A Busbar mm²", model.cableSizeDisplay)
    }
}
