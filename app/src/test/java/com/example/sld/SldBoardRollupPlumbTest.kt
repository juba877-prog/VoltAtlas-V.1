package com.example.sld

import com.example.data.CircuitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 2A: board-level load roll-up (TCL / TDL / DF / board current) must be
 * plumbed from the real [com.example.engine.PanelScheduleEngine.generateScheduleRecursive]
 * output into the SLD data block of switchboard nodes — and ONLY switchboard nodes
 * (leaf circuits carry no roll-up).
 */
class SldBoardRollupPlumbTest {

    @Test
    fun boardNodes_carryTclTdlDemandFactorAndCurrent() {
        val projectId = "p2a"
        val circuits = listOf(
            CircuitEntity(
                id = 1, projectId = projectId, circuitName = "MDB-1", fedFrom = "Transformer",
                panelType = "MDB", demandFactor = 0.8, loadKVA = 0.0, phases = "3P+N"
            ),
            CircuitEntity(
                id = 2, projectId = projectId, circuitName = "SMDB-1", fedFrom = "MDB-1",
                panelType = "SMDB", loadKVA = 0.0, phases = "3P+N"
            ),
            CircuitEntity(
                id = 3, projectId = projectId, circuitName = "AHU-1", fedFrom = "MDB-1",
                loadKVA = 10.0, phases = "3P+N"
            ),
            CircuitEntity(
                id = 4, projectId = projectId, circuitName = "SOCKET-1", fedFrom = "SMDB-1",
                loadKVA = 5.0, phases = "1P"
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits
        )

        val mdb = graph.nodes.first { it.id == "circ_1" }
        val smdb = graph.nodes.first { it.id == "circ_2" }
        val ahu = graph.nodes.first { it.id == "circ_3" }

        // MDB roll-up: SMDB subtree (5 kVA) + direct AHU-1 (10 kVA) = 15 kVA TCL.
        val mdbDb = mdb.dataBlock!!
        assertEquals("15.0 kVA", mdbDb.boardTotalConnectedKva)
        assertEquals("12.0 kVA", mdbDb.boardDemandKva) // 15 kVA × DF 0.8
        assertEquals("80%", mdbDb.boardDemandFactorPct)
        // 12 kVA @ 400 V 3-phase → 12000 / (√3 × 400) = 17.3 A
        assertNotNull(mdbDb.boardDesignCurrentA)
        assertEquals("17.3 A", mdbDb.boardDesignCurrentA)

        // SMDB roll-up: single 5 kVA child, default DF 1.0 → TDL == TCL, no DF label.
        val smdbDb = smdb.dataBlock!!
        assertEquals("5.0 kVA", smdbDb.boardTotalConnectedKva)
        assertEquals("5.0 kVA", smdbDb.boardDemandKva)
        assertNull(smdbDb.boardDemandFactorPct)

        // Leaf circuits must not carry a board roll-up.
        assertNull(ahu.dataBlock?.boardTotalConnectedKva)
        assertNull(ahu.dataBlock?.boardDemandKva)
        assertNull(ahu.dataBlock?.boardDesignCurrentA)
    }
}
