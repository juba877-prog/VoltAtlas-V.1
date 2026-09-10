package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SldPhase1VisualFoundationTest {

    private val testProjectId = "SLD_PHASE1_TEST"

    private fun createSizing(
        status: SizingResultStatus = SizingResultStatus.PASS,
        cableSize: String = "4x185 mm² Cu/XLPE",
        vdPct: Double = 0.85,
        cumVdPct: Double = 0.85,
        deviceType: String = "MCCB",
        deviceSizeA: Double = 400.0,
        ibA: Double = 340.0,
        izA: Double = 420.0
    ) = SizingResult(
        Ib_A = ibA,
        deviceSize_A = deviceSizeA,
        deviceType = deviceType,
        cTotalDerating = 0.8,
        iRequired_perCable_A = 340.0,
        productCode = "PROD-1",
        cableSize_mm2 = cableSize,
        ratedI_table_A = 450.0,
        capacity_derated_Iz_A = izA,
        utilizationPct = 81.0,
        neutral_mm2 = "95",
        earthCPC_mm2 = "95",
        voltDrop_V = 3.4,
        vdPct = vdPct,
        cumulativeVDpct = cumVdPct,
        status = if (status == SizingResultStatus.PASS) "✓ PASS" else "✗ FAIL",
        deviceBreakingKA = 36.0,
        resultStatus = status,
        isAmpacityOk = status == SizingResultStatus.PASS,
        isVoltageDropOk = status == SizingResultStatus.PASS,
        primaryFailureCode = null
    )

    // =========================================================================
    // 1. PRESENTATION MAPPING TESTS
    // =========================================================================

    @Test
    fun testUtilityAndTransformerMapping() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1500.0, voltage = 400, primaryVoltageV = 22000)
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = emptyList()
        )

        val gridNode = graph.nodes.find { it.type == SldNodeType.UTILITY_SOURCE }
        val txNode = graph.nodes.find { it.type == SldNodeType.TRANSFORMER }

        assertNotNull("Utility Grid node must be created", gridNode)
        assertNotNull("Transformer node must be created", txNode)
        assertEquals("grid_TX-01", gridNode?.id)
        assertEquals("tx_TX-01", txNode?.id)
        assertEquals("22 kV", gridNode?.voltage)

        val edge = graph.edges.find { it.fromNodeId == gridNode?.id && it.toNodeId == txNode?.id }
        assertNotNull("Source connection edge from Grid to Tx must exist", edge)
        assertEquals(SldEdgeType.SOURCE_CONNECTION, edge?.type)
    }

    @Test
    fun testMultipleGeneratorsAndAtsMapping() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-01", generatorName = "Standby Gen 1", enableManualOverride = true, manualSizeKVA = 500.0),
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-02", generatorName = "Standby Gen 2", enableManualOverride = true, manualSizeKVA = 750.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "TX-01", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = testProjectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "TX-01", emergencySourceId = "GEN-02")
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = testProjectId, circuitName = "MDB-Main", fedFrom = "ATS 1", assignedAtsId = "ATS-01"),
            CircuitEntity(id = 20, projectId = testProjectId, circuitName = "EMDB-01", fedFrom = "ATS 2", assignedAtsId = "ATS-02")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        val genNodes = graph.nodes.filter { it.type == SldNodeType.GENERATOR }
        val atsNodes = graph.nodes.filter { it.type == SldNodeType.ATS }

        assertEquals("Multiple Generators must not be collapsed into one", 2, genNodes.size)
        assertEquals("Multiple ATS units must not be collapsed into one", 2, atsNodes.size)

        // Verify ATS-01 paths
        val ats1Normal = graph.edges.find { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-01" }
        val ats1Emerg = graph.edges.find { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" }
        val ats1Output = graph.edges.find { it.fromNodeId == "ats_ATS-01" && it.toNodeId == "circ_10" }

        assertNotNull("ATS-01 normal source edge must exist", ats1Normal)
        assertNotNull("ATS-01 emergency source edge must exist", ats1Emerg)
        assertNotNull("ATS-01 output edge to MDB-Main must exist", ats1Output)

        // Verify ATS-02 paths
        val ats2Emerg = graph.edges.find { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-02" }
        val ats2Output = graph.edges.find { it.fromNodeId == "ats_ATS-02" && it.toNodeId == "circ_20" }

        assertNotNull("ATS-02 emergency source edge must exist", ats2Emerg)
        assertNotNull("ATS-02 output edge to EMDB-01 must exist", ats2Output)
    }

    @Test
    fun testPanelHierarchyAndMotorLoadMapping() {
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = testProjectId, circuitName = "MDB-Main", fedFrom = "Transformer", loadType = "Power"),
            CircuitEntity(id = 2, projectId = testProjectId, circuitName = "SMDB-01", fedFrom = "MDB-Main", loadType = "Power"),
            CircuitEntity(id = 3, projectId = testProjectId, circuitName = "DB-Power", fedFrom = "SMDB-01", loadType = "Distribution"),
            CircuitEntity(id = 4, projectId = testProjectId, circuitName = "Chiller Pump Motor", fedFrom = "DB-Power", loadType = "MOTOR")
        )
        val parents = mapOf(2 to 1, 3 to 2, 4 to 3)

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = circuits,
            circuitParents = parents
        )

        val mdbNode = graph.nodes.find { it.id == "circ_1" }
        val smdbNode = graph.nodes.find { it.id == "circ_2" }
        val dbNode = graph.nodes.find { it.id == "circ_3" }
        val motorNode = graph.nodes.find { it.id == "circ_4" }

        assertEquals(SldNodeType.MDB, mdbNode?.type)
        assertEquals(SldNodeType.SMDB, smdbNode?.type)
        assertEquals(SldNodeType.DB, dbNode?.type)
        assertEquals(SldNodeType.FINAL_CIRCUIT, motorNode?.type)
    }

    // =========================================================================
    // 2. BUSWAY DISTINCT PRESENTATION TESTS
    // =========================================================================

    @Test
    fun testBuswayFeederDistinctPresentation() {
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = testProjectId, circuitName = "MDB-Main", fedFrom = "Transformer"),
            CircuitEntity(id = 2, projectId = testProjectId, circuitName = "Riser Busway Trunk", fedFrom = "MDB-Main", cableType = "Busway 1000A Cu Trunk"),
            CircuitEntity(id = 3, projectId = testProjectId, circuitName = "Standard Cable Feeder", fedFrom = "MDB-Main", cableType = "4x185 mm² Cu/XLPE")
        )
        val parents = mapOf(2 to 1, 3 to 1)

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = circuits,
            circuitParents = parents
        )

        val buswayNode = graph.nodes.find { it.id == "circ_2" }
        val cableNode = graph.nodes.find { it.id == "circ_3" }

        assertNotNull(buswayNode)
        assertNotNull(cableNode)

        val buswayDesc = buswayNode?.dataBlock?.cableDescription.orEmpty()
        val cableDesc = cableNode?.dataBlock?.cableDescription.orEmpty()

        assertTrue("Busway description must contain Busway", buswayDesc.contains("Busway", ignoreCase = true))
        assertFalse("Busway must not have cable core formatting", buswayDesc.contains("4x1C") || buswayDesc.contains("4x185"))
        assertTrue("Standard cable feeder must contain cable specification", cableDesc.contains("185") && cableDesc.contains("XLPE"))
    }

    // =========================================================================
    // 3. ENGINEERING STATUS & DISPLAY MODES TESTS
    // =========================================================================

    @Test
    fun testNoFabricatedValuesWhenResultsMissing() {
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = testProjectId, circuitName = "Uncalculated Feeder", fedFrom = "Transformer")
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = circuits,
            sizingResults = emptyMap(),
            shortCircuitResults = emptyMap()
        )

        val node = graph.nodes.find { it.id == "circ_1" }
        assertNotNull(node)

        // Verify no fabricated numbers
        assertEquals("DATA REQUIRED", node?.dataBlock?.voltageDropPct)
        assertEquals("", node?.dataBlock?.faultLevelKa)
    }

    @Test
    fun testCalculatedValuesPopulatedCorrectly() {
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = testProjectId, circuitName = "MDB-Feeder 1", fedFrom = "Transformer", loadKVA = 250.0, length_m = 45.0)
        )
        val sizing = mapOf(1 to createSizing(status = SizingResultStatus.PASS, vdPct = 0.85, cumVdPct = 0.85))
        val sc = mapOf(1 to ShortCircuitResult(circuitId = 1, sourceName = "Tx", cableMm2 = "185", iscKA = 18.2, iwKA = 18.0, deviceKA = 25.0, deviceCheck = "PASS", cableCheck = "PASS", marginPct = 20.0, status = "PASS", note = ""))

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = circuits,
            sizingResults = sizing,
            shortCircuitResults = sc
        )

        val node = graph.nodes.find { it.id == "circ_1" }
        assertNotNull(node)
        assertEquals("0.85%", node?.dataBlock?.voltageDropPct)
        assertEquals("18.20 kA", node?.dataBlock?.faultLevelKa)
        assertEquals("MCCB 400A", node?.dataBlock?.deviceTypeAndRating)
        assertEquals("45 m", node?.dataBlock?.lengthM)
        assertEquals(SldValidationStatus.VALID, node?.status)
    }

    // =========================================================================
    // 4. LAYOUT DETERMINISM & TIER HIERARCHY TESTS
    // =========================================================================

    @Test
    fun testLayoutDeterminismAndHierarchyOrder() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = testProjectId, circuitName = "MDB-Main", fedFrom = "Transformer"),
            CircuitEntity(id = 20, projectId = testProjectId, circuitName = "SMDB-01", fedFrom = "MDB-Main"),
            CircuitEntity(id = 30, projectId = testProjectId, circuitName = "DB-01", fedFrom = "SMDB-01")
        )

        val result1 = SldLayoutEngine.calculateLayout(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits
        )

        val result2 = SldLayoutEngine.calculateLayout(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits
        )

        // Determinism: Both runs yield identical coordinates
        assertEquals(result1.nodes.size, result2.nodes.size)
        result1.nodes.forEachIndexed { i, n1 ->
            val n2 = result2.nodes[i]
            assertEquals("X coordinates must match identically", n1.x, n2.x, 0.001f)
            assertEquals("Y coordinates must match identically", n1.y, n2.y, 0.001f)
        }

        // Tier hierarchy: Grid (Tier -1) < Tx (Tier 0) < MDB (Tier 1) < SMDB (Tier 2) < DB (Tier 3)
        val grid = result1.nodes.find { it.id.startsWith("grid_") }
        val tx = result1.nodes.find { it.id.startsWith("tx_") }
        val mdb = result1.nodes.find { it.id == "circ_10" }
        val smdb = result1.nodes.find { it.id == "circ_20" }
        val db = result1.nodes.find { it.id == "circ_30" }

        assertNotNull(grid)
        assertNotNull(tx)
        assertNotNull(mdb)
        assertNotNull(smdb)
        assertNotNull(db)

        assertTrue("Grid must be above Tx", grid!!.y < tx!!.y)
        assertTrue("Tx must be above MDB", tx.y < mdb!!.y)
        assertTrue("MDB must be above SMDB", mdb.y < smdb!!.y)
        assertTrue("SMDB must be above DB", smdb.y < db!!.y)
    }

    @Test
    fun testDetailLevelPolicyFeederCalloutTiers() {
        val macro = SldDetailLevelPolicy.feederCallout(SldDetailLevel.MACRO)
        assertFalse("Macro must suppress breakerLine", macro.breakerLine)
        assertFalse("Macro must suppress cableLine", macro.cableLine)
        assertFalse("Macro must suppress modeMetricLine", macro.modeMetricLine)
        assertFalse("Macro must suppress advancedLines", macro.advancedLines)
        assertFalse("Macro must suppress inspectionLines", macro.inspectionLines)

        val compact = SldDetailLevelPolicy.feederCallout(SldDetailLevel.COMPACT)
        assertTrue("Compact must show breakerLine", compact.breakerLine)
        assertFalse("Compact must suppress cableLine", compact.cableLine)
        assertFalse("Compact must suppress modeMetricLine", compact.modeMetricLine)
        assertFalse("Compact must suppress advancedLines", compact.advancedLines)
        assertFalse("Compact must suppress inspectionLines", compact.inspectionLines)

        val standard = SldDetailLevelPolicy.feederCallout(SldDetailLevel.STANDARD)
        assertTrue("Standard must show breakerLine", standard.breakerLine)
        assertTrue("Standard must show cableLine", standard.cableLine)
        assertTrue("Standard must show modeMetricLine", standard.modeMetricLine)
        assertFalse("Standard must suppress advancedLines", standard.advancedLines)
        assertFalse("Standard must suppress inspectionLines", standard.inspectionLines)

        val detailed = SldDetailLevelPolicy.feederCallout(SldDetailLevel.DETAILED)
        assertTrue("Detailed must show breakerLine", detailed.breakerLine)
        assertTrue("Detailed must show cableLine", detailed.cableLine)
        assertTrue("Detailed must show modeMetricLine", detailed.modeMetricLine)
        assertTrue("Detailed must show advancedLines", detailed.advancedLines)
        assertFalse("Detailed must suppress inspectionLines", detailed.inspectionLines)

        val inspection = SldDetailLevelPolicy.feederCallout(SldDetailLevel.INSPECTION)
        assertTrue("Inspection must show breakerLine", inspection.breakerLine)
        assertTrue("Inspection must show cableLine", inspection.cableLine)
        assertTrue("Inspection must show modeMetricLine", inspection.modeMetricLine)
        assertTrue("Inspection must show advancedLines", inspection.advancedLines)
        assertTrue("Inspection must enable inspectionLines", inspection.inspectionLines)
    }
}
