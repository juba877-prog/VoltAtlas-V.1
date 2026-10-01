package com.example.sld

import com.example.data.AtsEntity
import com.example.data.BusTieEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import com.example.data.UpsEntity
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Phase 6 Batch 1 regression tests — P0-7 (first-instance SLD topology) and
 * P0-4 (equipment voltage derivation).
 *
 * P0-7 contract: engineering parentage resolves only via
 *   1. explicit FK, or
 *   2. a deterministic UNIQUE exact match, else
 *   3. an explicit AMBIGUOUS / typed issue, or
 *   4. a DISCONNECTED/unresolved state.
 * Never silently "object #1".
 *
 * P0-4 contract: equipment voltage derives from its authoritative stored/related value
 * (CircuitEntity.voltageV, AtsEntity.systemVoltage, UpsEntity.systemVoltage, endpoint
 * boards for ties). Missing/invalid ⇒ DATA REQUIRED — never a fabricated 400 V.
 */
class SldTopologyAndEquipmentIntegrityTest {

    private val projectId = "proj_topology_integrity"

    private fun transformer(
        id: String = "TX-01",
        voltage: Int = 400,
        kVA: Double = 1000.0
    ) = TransformerEntity(projectId = projectId, transformerId = id, kVA = kVA, voltage = voltage)

    private fun circuit(
        id: Int,
        name: String,
        fedFrom: String,
        transformerId: String = "",
        panelType: String? = null,
        voltageV: Int = 400,
        loadKVA: Double = 100.0
    ) = CircuitEntity(
        id = id,
        projectId = projectId,
        circuitName = name,
        fedFrom = fedFrom,
        transformerId = transformerId,
        panelType = panelType,
        voltageV = voltageV,
        loadKVA = loadKVA
    )

    private fun build(
        circuits: List<CircuitEntity>,
        transformers: List<TransformerEntity> = listOf(transformer()),
        generators: List<GeneratorEntity>? = null,
        atsList: List<AtsEntity>? = null,
        upsList: List<UpsEntity>? = null,
        busTies: List<BusTieEntity>? = null
    ) = ElectricalNetworkGraphBuilder.buildGraph(
        activeProjectId = projectId,
        transformers = transformers,
        circuits = circuits,
        generators = generators,
        atsList = atsList,
        upsList = upsList,
        busTies = busTies
    )

    // =========================================================================
    // P0-7 — first-instance / name-contains topology fallbacks
    // =========================================================================

    @Test
    fun p0_7_twoUpstreamMdbsMakeAtsNormalSourceAmbiguous() {
        val graph = build(
            circuits = listOf(
                circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB"),
                circuit(20, "MDB-02", "Transformer", "TX-01", panelType = "MDB"),
                circuit(30, "DB-L", "ATS-01", panelType = "DB")
            ),
            atsList = listOf(AtsEntity(projectId = projectId, atsId = "1", atsName = "ATS-01"))
        )

        assertTrue(
            "two upstream MDB candidates must surface an explicit ambiguity issue",
            graph.validationIssues.any {
                it.issueCode == "AMBIGUOUS_SOURCE_INSTANCE" && it.affectedNodeId == "ats_1"
            }
        )
        assertEquals(
            "no normal-source edge may assert either candidate by list order",
            0,
            graph.edges.count { it.type == SldEdgeType.ATS_NORMAL_PATH }
        )
        assertTrue(
            "unresolved normal path must be rendered explicitly",
            graph.edges.any {
                it.type == SldEdgeType.UNRESOLVED_PATH && it.fromNodeId == "unresolved_normal_ats_1"
            }
        )
    }

    @Test
    fun p0_7_blankNormalSourceResolvesUniqueUpstreamMdb_notDownstreamBoard() {
        val graph = build(
            circuits = listOf(
                circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB"),
                circuit(20, "MDB-Emergency", "ATS-01", panelType = "MDB")
            ),
            atsList = listOf(AtsEntity(projectId = projectId, atsId = "1", atsName = "ATS-01"))
        )

        val normalEdge = graph.edges.firstOrNull { it.type == SldEdgeType.ATS_NORMAL_PATH }
        assertNotNull("a unique upstream candidate must resolve", normalEdge)
        assertEquals("circ_10", normalEdge!!.fromNodeId)
        assertEquals("ats_1", normalEdge.toNodeId)
        assertTrue(
            "the ATS's own downstream board must never be its normal source (circular path)",
            graph.edges.none { it.type == SldEdgeType.ATS_NORMAL_PATH && it.fromNodeId == "circ_20" }
        )
    }

    @Test
    fun p0_7_duplicateBoardNamesAreAmbiguous_neverInstanceOne() {
        val graph = build(
            circuits = listOf(
                circuit(10, "DB-X", "Transformer"),
                circuit(20, "DB-X", "Transformer"),
                circuit(30, "LOAD-1", "DB-X")
            ),
            transformers = emptyList()
        )

        assertTrue(
            "child of duplicate-named boards must be disconnected, not wired to #1",
            graph.unconnectedNodes.any { it.id == "circ_30" }
        )
        assertTrue(
            "explicit ambiguity issue required for duplicate parent names",
            graph.validationIssues.any {
                it.issueCode == "AMBIGUOUS_SOURCE_INSTANCE" && it.affectedNodeId == "circ_30"
            }
        )
        assertTrue("no edge may assert a parent for the ambiguous child", graph.edges.none { it.toNodeId == "circ_30" })
    }

    @Test
    fun p0_7_genericAtsKeywordWithMultipleInstancesIsAmbiguous() {
        val graph = build(
            circuits = listOf(circuit(10, "DB-1", "ATS")),
            transformers = emptyList(),
            atsList = listOf(
                AtsEntity(projectId = projectId, atsId = "1", atsName = "ATS-A"),
                AtsEntity(projectId = projectId, atsId = "2", atsName = "ATS-B")
            )
        )

        assertTrue(
            "generic 'ATS' keyword with 2 instances must be ambiguous (was: instance #1)",
            graph.validationIssues.any {
                it.issueCode == "AMBIGUOUS_SOURCE_INSTANCE" && it.affectedNodeId == "circ_10"
            }
        )
        assertTrue(graph.unconnectedNodes.any { it.id == "circ_10" })
        assertTrue(
            "no edge may connect the circuit to an arbitrary ATS instance",
            graph.edges.none { it.toNodeId == "circ_10" && it.fromNodeId.startsWith("ats_") }
        )
    }

    @Test
    fun p0_7_foreignProjectAtsNeverStampsFrequencyOrVoltage() {
        val graph = build(
            circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
            generators = listOf(
                GeneratorEntity(
                    projectId = projectId,
                    generatorId = "GEN-1",
                    generatorName = "Genset",
                    manualSizeKVA = 500.0
                )
            ),
            atsList = listOf(
                // Foreign project FIRST in list order and linked — the old `?: atsList.firstOrNull()`
                // tail stamped its 60 Hz onto this project's generator.
                AtsEntity(
                    projectId = "OTHER_PROJECT",
                    atsId = "X1",
                    atsName = "Foreign ATS",
                    systemVoltage = 400,
                    systemFrequency = 60,
                    emergencySourceId = "GEN-1"
                ),
                AtsEntity(
                    projectId = projectId,
                    atsId = "A1",
                    atsName = "Local ATS",
                    systemVoltage = 415,
                    systemFrequency = 49
                    // deliberately NOT linked to GEN-1
                )
            )
        )

        val genNode = graph.nodes.first { it.id == "gen_GEN-1" }
        assertNull(
            "foreign-project frequency must not stamp this project's generator",
            genNode.dataBlock.voltageFrequencyHz
        )
        assertEquals(
            "no linked ATS ⇒ no voltage basis, never 400 V",
            "DATA REQUIRED",
            genNode.voltage
        )
    }

    @Test
    fun p0_7_linkedProjectAtsSuppliesFrequencyAndVoltage() {
        val graph = build(
            circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
            generators = listOf(
                GeneratorEntity(
                    projectId = projectId,
                    generatorId = "GEN-1",
                    generatorName = "Genset",
                    manualSizeKVA = 500.0
                )
            ),
            atsList = listOf(
                AtsEntity(
                    projectId = "OTHER_PROJECT",
                    atsId = "X1",
                    atsName = "Foreign ATS",
                    systemVoltage = 400,
                    systemFrequency = 60,
                    emergencySourceId = "GEN-1"
                ),
                AtsEntity(
                    projectId = projectId,
                    atsId = "A1",
                    atsName = "Local ATS",
                    systemVoltage = 415,
                    systemFrequency = 49,
                    emergencySourceId = "GEN-1"
                )
            )
        )

        val genNode = graph.nodes.first { it.id == "gen_GEN-1" }
        assertEquals("project-scoped linked ATS supplies frequency", "49 Hz", genNode.dataBlock.voltageFrequencyHz)
        assertEquals("415 V", genNode.voltage)
        // Ib = 500 kVA / (√3 · 415 V) ≈ 695.6 A — derived from the ATS voltage, not 400 V.
        val ib = genNode.dataBlock.ibAmp?.removeSuffix(" A")?.toDoubleOrNull()
        assertNotNull("derived current expected once voltage is known", ib)
        assertTrue("expected ≈695.6 A, was $ib", ib!! in 690.0..701.0)
    }

    // =========================================================================
    // P0-4 — equipment voltages
    // =========================================================================

    @Test
    fun p0_4_transformerInvalidVoltageIsDataRequired_never400() {
        val graph = build(
            circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
            transformers = listOf(transformer(id = "TX-01", voltage = 0))
        )
        val txNode = graph.nodes.first { it.type == SldNodeType.TRANSFORMER }
        assertEquals("DATA REQUIRED", txNode.voltage)
        assertFalse("no fabricated secondary voltage", txNode.voltage?.contains("400") == true)
        assertEquals(
            "no fabricated FLC basis when the secondary voltage is invalid",
            "DATA REQUIRED",
            txNode.dataBlock.ibAmp
        )
    }

    @Test
    fun p0_4_transformerValidVoltageStillRenders() {
        val graph = build(
            circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
            transformers = listOf(transformer(id = "TX-01", voltage = 415))
        )
        val txNode = graph.nodes.first { it.type == SldNodeType.TRANSFORMER }
        assertEquals("415 V", txNode.voltage)
        // Ib = 1000 kVA / (√3 · 415) ≈ 1394.4 A
        val ib = txNode.dataBlock.ibAmp?.removeSuffix(" A")?.toDoubleOrNull()
        assertNotNull(ib)
        assertTrue("expected ≈1394 A, was $ib", ib!! in 1380.0..1410.0)
    }

    @Test
    fun p0_4_generatorWithoutLinkedAtsIsDataRequired() {
        val graph = build(
            circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
            generators = listOf(
                GeneratorEntity(
                    projectId = projectId,
                    generatorId = "GEN-1",
                    generatorName = "Genset",
                    manualSizeKVA = 500.0
                )
            )
        )
        val genNode = graph.nodes.first { it.id == "gen_GEN-1" }
        assertEquals("DATA REQUIRED", genNode.voltage)
        assertEquals("DATA REQUIRED", genNode.dataBlock.voltage)
        assertNull(
            "current must not be fabricated on a missing voltage basis",
            genNode.dataBlock.ibAmp
        )
        assertEquals("500 kVA", genNode.dataBlock.ratedKva)
    }

    @Test
    fun p0_4_syncBusFollowsMemberVoltages() {
        fun syncVoltage(atsList: List<AtsEntity>?): String? {
            val graph = build(
                circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
                generators = listOf(
                    GeneratorEntity(
                        projectId = projectId,
                        generatorId = "GEN-1",
                        generatorName = "Genset 1",
                        manualSizeKVA = 500.0,
                        syncBusId = "SG1"
                    ),
                    GeneratorEntity(
                        projectId = projectId,
                        generatorId = "GEN-2",
                        generatorName = "Genset 2",
                        manualSizeKVA = 500.0,
                        syncBusId = "SG1"
                    )
                ),
                atsList = atsList
            )
            return graph.nodes.first { it.id == "sync_SG1" }.voltage
        }

        val withAts = syncVoltage(
            listOf(
                AtsEntity(projectId = projectId, atsId = "A1", atsName = "ATS A", systemVoltage = 415, emergencySourceId = "GEN-1"),
                AtsEntity(projectId = projectId, atsId = "A2", atsName = "ATS B", systemVoltage = 415, emergencySourceId = "GEN-2")
            )
        )
        assertEquals("sync bus follows agreed member (ATS) voltages", "415 V", withAts)

        val withoutAts = syncVoltage(null)
        assertEquals("no voltage basis ⇒ DATA REQUIRED, never 400 V", "DATA REQUIRED", withoutAts)
    }

    @Test
    fun p0_4_upsVoltageFromStoredSystemVoltage() {
        fun upsNode(ups: UpsEntity) =
            build(
                circuits = listOf(circuit(10, "MDB-01", "Transformer", "TX-01", panelType = "MDB")),
                upsList = listOf(ups)
            ).nodes.first { it.type == SldNodeType.UPS }

        val valid = upsNode(
            UpsEntity(projectId = projectId, upsId = "U1", upsName = "UPS A", systemVoltage = 415, manualSizeKVA = 100.0)
        )
        assertEquals("415 V", valid.voltage)
        // Ib = 100 kVA / (√3 · 415) ≈ 139.9 A
        val ib = valid.dataBlock.ibAmp?.removeSuffix(" A")?.toDoubleOrNull()
        assertNotNull(ib)
        assertTrue("expected ≈140 A, was $ib", ib!! in 135.0..145.0)

        val invalid = upsNode(
            UpsEntity(projectId = projectId, upsId = "U2", upsName = "UPS B", systemVoltage = 0, manualSizeKVA = 100.0)
        )
        assertEquals("DATA REQUIRED", invalid.voltage)
        assertEquals("DATA REQUIRED", invalid.dataBlock.ibAmp)
    }

    @Test
    fun p0_4_busTieVoltageFromEndpointBoards() {
        val graph = build(
            circuits = listOf(
                circuit(10, "MDB-A", "Transformer", "TX-01", panelType = "MDB", voltageV = 415),
                circuit(20, "MDB-B", "Transformer", "TX-01", panelType = "MDB", voltageV = 415),
                circuit(30, "MDB-C", "Transformer", "TX-01", panelType = "MDB", voltageV = 400),
                circuit(40, "MDB-D", "Transformer", "TX-01", panelType = "MDB", voltageV = 0),
                circuit(50, "MDB-E", "Transformer", "TX-01", panelType = "MDB", voltageV = 0)
            ),
            busTies = listOf(
                BusTieEntity(projectId = projectId, busTieId = "T-A", name = "Tie A", sourceBoardId = 10L, targetBoardId = 20L),
                BusTieEntity(projectId = projectId, busTieId = "T-B", name = "Tie B", sourceBoardId = 10L, targetBoardId = 30L),
                BusTieEntity(projectId = projectId, busTieId = "T-C", name = "Tie C", sourceBoardId = 40L, targetBoardId = 50L)
            )
        )

        val tieA = graph.nodes.first { it.id == "tie_T-A" }
        val tieB = graph.nodes.first { it.id == "tie_T-B" }
        val tieC = graph.nodes.first { it.id == "tie_T-C" }

        assertEquals("415 V", tieA.voltage)
        assertEquals("endpoint disagreement must be surfaced, not papered over", "415 V / 400 V", tieB.voltage)
        assertEquals("no valid endpoint voltage ⇒ DATA REQUIRED, never 400 V", "DATA REQUIRED", tieC.voltage)
    }

    // =========================================================================
    // P0-8 — bus ties reach the graph (layout engine facade feeds this builder)
    // =========================================================================

    @Test
    fun p0_8_busTieSynthesisUsesProvidedProjectTies() {
        val graph = build(
            circuits = listOf(
                circuit(10, "MDB-A", "Transformer", "TX-01", panelType = "MDB"),
                circuit(20, "MDB-B", "Transformer", "TX-01", panelType = "MDB")
            ),
            busTies = listOf(
                BusTieEntity(projectId = projectId, busTieId = "T-1", name = "Tie 1", sourceBoardId = 10L, targetBoardId = 20L)
            )
        )
        val tieNode = graph.nodes.firstOrNull { it.type == SldNodeType.BUS_TIE }
        assertNotNull("bus ties passed to the engine must synthesize a tie node", tieNode)
        assertEquals("tie_T-1", tieNode!!.id)
        assertTrue(
            "tie must be wired to both endpoint boards",
            graph.edges.any { it.toNodeId == "tie_T-1" } || graph.edges.any { it.fromNodeId == "tie_T-1" }
        )
    }

    @Test
    fun p0_8_noTiesProvidedYieldsNoTieNodes() {
        val graph = build(
            circuits = listOf(
                circuit(10, "MDB-A", "Transformer", "TX-01", panelType = "MDB"),
                circuit(20, "MDB-B", "Transformer", "TX-01", panelType = "MDB")
            )
        )
        assertTrue(
            "absence of ties must be exact (no fabricated tie)",
            graph.nodes.none { it.type == SldNodeType.BUS_TIE }
        )
    }

    private fun assertNoNonFinite(value: Double, label: String) {
        assertTrue("$label must be finite, was $value", value.isFinite())
        assertFalse("$label must not be NaN", value.isNaN())
    }

    private fun assertApprox(expected: Double, actual: Double, tol: Double, label: String) {
        assertTrue("$label expected ≈$expected, was $actual", abs(expected - actual) <= tol)
    }
}
