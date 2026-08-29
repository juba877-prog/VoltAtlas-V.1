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
        CircuitUiModelMapper.resetModelConstructionCount()
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
    // SECTION 7.C CALL-COUNT PROOFS & RECOMPUTATION ISOLATION
    // =========================================================================

    @Test
    fun testInitialPopulationProducesExactlyNConstructions() {
        val circuits = listOf(
            createCircuit(1, "MDB"),
            createCircuit(2, "SMDB-1", fedFrom = "MDB", parentId = 1),
            createCircuit(3, "DB-1", fedFrom = "SMDB-1", parentId = 2),
            createCircuit(4, "Standalone")
        )
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }
        val childLookup = CircuitChildLookup.build(circuits)
        val parentNames = circuits.associate { it.id to it.circuitName }

        assertEquals(0, CircuitUiModelMapper.modelConstructionCount)

        val models = circuits.map { circ ->
            val parentName = circ.parentId?.let { pId -> if (pId != circ.id) parentNames[pId] else null }
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = parentName,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals(4, models.size)
        assertEquals("Initial N circuits produce exactly N card-model constructions", 4, CircuitUiModelMapper.modelConstructionCount)
    }

    @Test
    fun testRecompositionWithIdenticalInputsProducesZeroNewConstructions() {
        val circuits = listOf(createCircuit(1), createCircuit(2), createCircuit(3))
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }
        val childLookup = CircuitChildLookup.build(circuits)
        val parentNames = circuits.associate { it.id to it.circuitName }

        // Initial build
        circuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(3, CircuitUiModelMapper.modelConstructionCount)

        // Second pass with identical inputs (simulates recomposition)
        val secondPassModels = circuits.map { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals("No new model constructions on identical recomposition", 3, CircuitUiModelMapper.modelConstructionCount)
        assertEquals(3, secondPassModels.size)
    }

    @Test
    fun testUpdatingSingleCircuitEntityRebuildsOnlyTargetModel() {
        val c1 = createCircuit(1, "Circuit-1", loadKVA = 50.0)
        val c2 = createCircuit(2, "Circuit-2", loadKVA = 80.0)
        val c3 = createCircuit(3, "Circuit-3", loadKVA = 100.0)
        val circuits = listOf(c1, c2, c3)
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }
        val childLookup = CircuitChildLookup.build(circuits)

        // Initial build
        circuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(3, CircuitUiModelMapper.modelConstructionCount)

        // Mutate only c2 (loadKVA changed from 80 to 95)
        val c2Updated = c2.copy(loadKVA = 95.0)
        val updatedCircuits = listOf(c1, c2Updated, c3)

        val updatedModels = updatedCircuits.map { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals("Exactly 1 additional model construction for mutated circuit", 4, CircuitUiModelMapper.modelConstructionCount)
        assertEquals("95.0 kVA", updatedModels[1].loadDisplay)
    }

    @Test
    fun testUpdatingSingleSizingResultRebuildsOnlyTargetModel() {
        val circuits = listOf(createCircuit(1), createCircuit(2), createCircuit(3))
        val sizingResults = circuits.associate { it.id to createSizing(cableSize = "70") }.toMutableMap()
        val scResults = circuits.associate { it.id to createShortCircuit(it.id) }
        val childLookup = CircuitChildLookup.build(circuits)

        // Initial build
        circuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(3, CircuitUiModelMapper.modelConstructionCount)

        // Update sizing result for circuit 2 only (cableSize 70 -> 95)
        sizingResults[2] = createSizing(cableSize = "95")

        val updatedModels = circuits.map { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals("Exactly 1 additional construction for circuit with updated sizing result", 4, CircuitUiModelMapper.modelConstructionCount)
        assertEquals("95 mm²", updatedModels[1].cableSizeDisplay)
        assertEquals("70 mm²", updatedModels[0].cableSizeDisplay)
        assertEquals("70 mm²", updatedModels[2].cableSizeDisplay)
    }

    @Test
    fun testUpdatingSingleShortCircuitResultRebuildsOnlyTargetModel() {
        val circuits = listOf(createCircuit(1), createCircuit(2), createCircuit(3))
        val sizingResults = circuits.associate { it.id to createSizing() }
        val scResults = circuits.associate { it.id to createShortCircuit(it.id, iscKA = 20.0) }.toMutableMap()
        val childLookup = CircuitChildLookup.build(circuits)

        // Initial build
        circuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(3, CircuitUiModelMapper.modelConstructionCount)

        // Update short-circuit result for circuit 3 only (20kA -> 35kA)
        scResults[3] = createShortCircuit(3, iscKA = 35.0)

        val updatedModels = circuits.map { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals("Exactly 1 additional construction for circuit with updated short-circuit result", 4, CircuitUiModelMapper.modelConstructionCount)
        assertEquals("35.0 kA", updatedModels[2].isc3phDisplay)
        assertEquals("20.0 kA", updatedModels[0].isc3phDisplay)
        assertEquals("20.0 kA", updatedModels[1].isc3phDisplay)
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

        // Initial build: 4 models
        circuits.forEach { circ ->
            val parentName = circ.parentId?.let { pId -> if (pId != circ.id) parentNames[pId] else null }
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = parentName,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(4, CircuitUiModelMapper.modelConstructionCount)

        // Rename parent: "Main-Panel" -> "Main-Distribution-Board"
        val renamedParent = parent.copy(circuitName = "Main-Distribution-Board")
        val newCircuits = listOf(renamedParent, child1, child2, unrelated)
        childLookup = CircuitChildLookup.build(newCircuits)
        parentNames = newCircuits.associate { it.id to it.circuitName }

        val newModels = newCircuits.map { circ ->
            val parentName = circ.parentId?.let { pId -> if (pId != circ.id) parentNames[pId] else null }
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizingResults[circ.id],
                shortCircuitResult = scResults[circ.id],
                parentName = parentName,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        // Expected constructions: 4 initial + 3 updated (parent + 2 children) = 7
        // Unrelated circuit #4 was NOT recomputed!
        assertEquals(7, CircuitUiModelMapper.modelConstructionCount)
        assertEquals("Main-Distribution-Board > Sub-1", newModels[1].breadcrumbDisplay)
        assertEquals("Main-Distribution-Board > Sub-2", newModels[2].breadcrumbDisplay)
        assertEquals("Transformer > Lighting-1", newModels[3].breadcrumbDisplay)
    }

    @Test
    fun testCircuitAdditionBuildsOnlyNewModel() {
        val c1 = createCircuit(1, "Circuit-1")
        val c2 = createCircuit(2, "Circuit-2")
        val circuits = listOf(c1, c2)
        val sizing = circuits.associate { it.id to createSizing() }.toMutableMap()
        val sc = circuits.associate { it.id to createShortCircuit(it.id) }.toMutableMap()
        var childLookup = CircuitChildLookup.build(circuits)

        circuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizing[circ.id],
                shortCircuitResult = sc[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(2, CircuitUiModelMapper.modelConstructionCount)

        // Add c3
        val c3 = createCircuit(3, "Circuit-3")
        val expandedCircuits = listOf(c1, c2, c3)
        sizing[3] = createSizing()
        sc[3] = createShortCircuit(3)
        childLookup = CircuitChildLookup.build(expandedCircuits)

        expandedCircuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizing[circ.id],
                shortCircuitResult = sc[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals("Adding 1 circuit produces exactly 1 additional model construction", 3, CircuitUiModelMapper.modelConstructionCount)
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

        circuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizing[circ.id],
                shortCircuitResult = sc[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }
        assertEquals(3, CircuitUiModelMapper.modelConstructionCount)
        assertEquals(3, cache.getCachedEntryCount())

        // Delete c2
        val remainingCircuits = listOf(c1, c3)
        cache.sync(remainingCircuits)
        assertEquals(2, cache.getCachedEntryCount())
        childLookup = CircuitChildLookup.build(remainingCircuits)

        remainingCircuits.forEach { circ ->
            cache.getOrCompute(
                circuit = circ,
                sizingResult = sizing[circ.id],
                shortCircuitResult = sc[circ.id],
                parentName = null,
                hasChildren = childLookup.hasChildren(circ.circuitName)
            )
        }

        assertEquals("No new model constructions after deletion for remaining unchanged circuits", 3, CircuitUiModelMapper.modelConstructionCount)
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
        assertEquals("150 mm²", modelAfter.cableSizeDisplay)
        assertEquals(2, CircuitUiModelMapper.modelConstructionCount)
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
