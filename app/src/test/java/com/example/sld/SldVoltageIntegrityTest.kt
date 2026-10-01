package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6 Batch 1 regression tests — P0-3 (SLD circuit voltage label) and
 * P0-5 (design current / non-finite input guards).
 *
 * The defect under test: node voltage labels were derived from PHASE COUNT
 * ("400 V (3Ph)" / "230 V (1Ph)") while the current calculation used the stored
 * CircuitEntity.voltageV, and zero/invalid voltages could feed Infinity into
 * board/busbar calculations. Expected behavior: label == stored voltage, and
 * explicit DATA REQUIRED when the stored voltage is missing/invalid.
 */
class SldVoltageIntegrityTest {

    private val projectId = "proj_voltage_integrity"

    private fun circuit(
        id: Int,
        name: String,
        fedFrom: String,
        voltageV: Int,
        phases: String,
        loadKVA: Double = 100.0
    ) = CircuitEntity(
        id = id,
        projectId = projectId,
        circuitName = name,
        fedFrom = fedFrom,
        transformerId = "TX-01",
        loadType = "Power",
        voltageV = voltageV,
        phases = phases,
        loadKVA = loadKVA
    )

    private fun build(circuits: List<CircuitEntity>) =
        ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = listOf(TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)),
            circuits = circuits
        )

    // ---- P0-3 -------------------------------------------------------------------
    @Test
    fun p0_3_voltageLabelFollowsStoredVoltage_notPhaseCount() {
        val cases = listOf(
            415 to "415 V (3Ph)",
            380 to "380 V (3Ph)",
            400 to "400 V (3Ph)"
        )
        for ((voltageV, expected) in cases) {
            val graph = build(listOf(circuit(10, "MDB-01", "Transformer", voltageV, "3P+N")))
            val node = graph.nodes.first { it.id == "circ_10" }
            assertEquals("node voltage for voltageV=$voltageV", expected, node.voltage)
            assertEquals("dataBlock voltage for voltageV=$voltageV", expected, node.dataBlock?.voltage)
        }
    }

    @Test
    fun p0_3_singlePhaseLabelFollowsStoredVoltage() {
        val graph = build(listOf(circuit(10, "DB-01", "Transformer", 230, "1P+N")))
        val node = graph.nodes.first { it.id == "circ_10" }
        assertEquals("230 V (1Ph)", node.voltage)
        assertEquals("230 V (1Ph)", node.dataBlock?.voltage)
    }

    @Test
    fun p0_3_zeroVoltageIsDataRequired_notDefault400() {
        val graph = build(listOf(circuit(10, "MDB-01", "Transformer", 0, "3P+N")))
        val node = graph.nodes.first { it.id == "circ_10" }
        assertEquals("DATA REQUIRED", node.voltage)
        assertEquals("DATA REQUIRED", node.dataBlock?.voltage)
    }

    // ---- P0-5 -------------------------------------------------------------------
    @Test
    fun p0_5_missingSizingReportsDataRequiredIb_notFabricated() {
        // No sizing results are supplied to buildGraph ⇒ authoritative Ib is absent.
        val graph = build(listOf(circuit(10, "MDB-01", "Transformer", 400, "3P+N", loadKVA = 50.0)))
        val node = graph.nodes.first { it.id == "circ_10" }
        assertEquals("DATA REQUIRED", node.dataBlock?.ibAmp)
        assertFalse(
            "no 1.44-assumption estimate may be rendered as Ib",
            node.dataBlock?.ibAmp?.contains("1.44") == true || node.dataBlock?.ibAmp == "72 A"
        )
    }

    @Test
    fun p0_5_zeroVoltageBoardProducesNoNonFiniteValues() {
        // MDB with a child panel load but voltageV = 0: previously the board current
        // divided by zero (Infinity) while busbar sizing ran on a fake 400 V basis.
        val graph = build(
            listOf(
                circuit(10, "MDB-01", "Transformer", 0, "3P+N", loadKVA = 100.0),
                circuit(20, "SMDB-01", "MDB-01", 0, "3P+N", loadKVA = 50.0)
            )
        )
        val mdb = graph.nodes.first { it.id == "circ_10" }
        val dataBlock = requireNotNull(mdb.dataBlock)

        assertNull(
            "board design current must be absent (not Infinity/NaN) when voltage is 0",
            dataBlock.boardDesignCurrentA
        )
        val rendered = dataBlock.toString()
        assertFalse("no Infinity may reach the data block: $rendered", rendered.contains("Infinity"))
        assertFalse("no NaN may reach the data block: $rendered", rendered.contains("NaN"))
        assertFalse("no fabricated 400 V basis may be presented: $rendered", rendered.contains("400 V"))
    }

    @Test
    fun p0_5_validVoltageBoardCurrentUsesStoredVoltage() {
        val graph = build(
            listOf(
                circuit(10, "MDB-01", "Transformer", 400, "3P+N", loadKVA = 100.0),
                circuit(20, "SMDB-01", "MDB-01", 400, "3P+N", loadKVA = 50.0)
            )
        )
        val mdb = graph.nodes.first { it.id == "circ_10" }
        val boardCurrent = requireNotNull(mdb.dataBlock?.boardDesignCurrentA)
        // 100 kVA demand @ 400 V 3ph → 100000 / (√3·400) ≈ 144.3 A; finite and voltage-derived.
        val value = boardCurrent.removeSuffix(" A").toDouble()
        assertTrue("board current should be finite, was $value", value.isFinite())
        assertTrue("board current should be voltage-derived and plausible, was $value", value in 50.0..300.0)
    }
}
