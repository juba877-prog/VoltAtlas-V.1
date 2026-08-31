package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the Generator-to-ATS Emergency Routing vs MDB Busbar Collision Prevention invariants:
 *
 * 1. Dedicated Emergency Lane: Generator emergency line drops straight vertically to ATS emergency terminal.
 * 2. MDB Busbar Bounds: MDB busbar rail stops before the active emergency source lane.
 * 3. Zero Intersection: No segment of any Generator -> ATS edge intersects the MDB busbar rectangle.
 * 4. ATS Normal Feed: Routes cleanly from MDB busbar takeoff port to ATS normal terminal without crossing emergency feeders.
 * 5. Strict Orthogonality: All polyline segments are strictly horizontal or vertical.
 */
class SldGeneratorEmergencyCollisionTest {

    private val projectId = "PRJ-EMERG-TEST"
    private val eps = 0.01f

    private fun doesSegmentIntersectBox(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float
    ): Boolean {
        val isVertical = Math.abs(p1.first - p2.first) < 1e-3f
        val isHorizontal = Math.abs(p1.second - p2.second) < 1e-3f

        if (isVertical) {
            val segX = p1.first
            val minY = minOf(p1.second, p2.second)
            val maxY = maxOf(p1.second, p2.second)
            return (segX in boxLeft..boxRight) && (minY < boxBottom && maxY > boxTop)
        } else if (isHorizontal) {
            val segY = p1.second
            val minX = minOf(p1.first, p2.first)
            val maxX = maxOf(p1.first, p2.first)
            return (segY in boxTop..boxBottom) && (minX < boxRight && maxX > boxLeft)
        }
        return false
    }

    @Test
    fun singleAtsSingleGenerator_emergencyFeederDoesNotIntersectMdbBusbar() {
        val transformers = listOf(TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0))
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-MAIN", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "FEEDER-01", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "FEEDER-02", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "PANEL-ATS-1", fedFrom = "MDB-MAIN", assignedAtsId = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "MDB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10)
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            generators = generators,
            atsList = atsList,
            circuits = circuits
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val ats1Node = result.nodes.first { it.id == "ats_ATS-01" }
        val gen1Node = result.nodes.first { it.id == "gen_GEN-01" }

        // 1. Generator vertical alignment above ATS emergency terminal
        val ats1EmergX = ats1Node.x + ats1Node.width * 0.75f
        val gen1CenterX = gen1Node.x + gen1Node.width / 2f
        assertEquals("Generator 1 must align vertically with ATS 1 emergency terminal", ats1EmergX, gen1CenterX, 5.0f)

        // 2. MDB busbar bounds check
        val mdbBusbarLeft = mdbNode.x
        val mdbBusbarRight = mdbNode.x + mdbNode.width
        val mdbBusbarTop = mdbNode.y
        val mdbBusbarBottom = mdbNode.y + maxOf(mdbNode.height, 18f)

        assertTrue(
            "MDB busbar right ($mdbBusbarRight) must end before Generator 1 emergency lane ($ats1EmergX)",
            mdbBusbarRight < ats1EmergX
        )

        // 3. Emergency edge collision check against MDB busbar
        val emergEdge = result.edges.first { it.toId == "ats_ATS-01" && it.isEmergency }
        for (i in 0 until emergEdge.pathPoints.size - 1) {
            val p1 = emergEdge.pathPoints[i]
            val p2 = emergEdge.pathPoints[i + 1]
            val intersects = doesSegmentIntersectBox(p1, p2, mdbBusbarLeft, mdbBusbarTop, mdbBusbarRight, mdbBusbarBottom)
            assertFalse("Emergency segment $p1 -> $p2 must not intersect MDB busbar [$mdbBusbarLeft..$mdbBusbarRight, $mdbBusbarTop..$mdbBusbarBottom]", intersects)
        }

        // 4. ATS Normal feed connects to normal terminal from MDB busbar
        val normalEdge = result.edges.first { it.toId == "ats_ATS-01" && !it.isEmergency && it.label == "NORMAL" }
        val normalOriginX = normalEdge.pathPoints.first().first
        val normalTerminusX = normalEdge.pathPoints.last().first
        val ats1NormalX = ats1Node.x + ats1Node.width * 0.25f

        assertTrue("Normal wire origin ($normalOriginX) must be on MDB busbar", normalOriginX in mdbBusbarLeft..mdbBusbarRight)
        assertEquals("Normal wire terminus must be ATS 1 normal terminal", ats1NormalX, normalTerminusX, 5.0f)
    }

    @Test
    fun multiAtsMultiGenerator_zeroCollisionsAcrossAllEmergencyFeeders() {
        val transformers = listOf(TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0))
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-MAIN", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "FEEDER-01", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "FEEDER-02", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 4, projectId = projectId, circuitName = "FEEDER-03", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "PANEL-ATS-1", fedFrom = "MDB-MAIN", assignedAtsId = "ATS-01", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "PANEL-ATS-2", fedFrom = "MDB-MAIN", assignedAtsId = "ATS-02", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "MDB-MAIN", emergencySourceId = "GEN-01", outputBoardId = 10),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "MDB-MAIN", emergencySourceId = "GEN-02", outputBoardId = 11)
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            generators = generators,
            atsList = atsList,
            circuits = circuits
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val ats1Node = result.nodes.first { it.id == "ats_ATS-01" }
        val ats2Node = result.nodes.first { it.id == "ats_ATS-02" }
        val gen1Node = result.nodes.first { it.id == "gen_GEN-01" }
        val gen2Node = result.nodes.first { it.id == "gen_GEN-02" }

        val mdbBusbarLeft = mdbNode.x
        val mdbBusbarRight = mdbNode.x + mdbNode.width
        val mdbBusbarTop = mdbNode.y
        val mdbBusbarBottom = mdbNode.y + maxOf(mdbNode.height, 18f)

        // 1. Both Generators aligned with ATS emergency terminals
        val ats1EmergX = ats1Node.x + ats1Node.width * 0.75f
        val gen1CenterX = gen1Node.x + gen1Node.width / 2f
        assertEquals("Gen 1 must align with ATS 1 emerg terminal", ats1EmergX, gen1CenterX, 5.0f)

        val ats2EmergX = ats2Node.x + ats2Node.width * 0.75f
        val gen2CenterX = gen2Node.x + gen2Node.width / 2f
        assertEquals("Gen 2 must align with ATS 2 emerg terminal", ats2EmergX, gen2CenterX, 5.0f)

        // 2. MDB busbar ends before the FIRST emergency lane
        assertTrue(
            "MDB busbar right ($mdbBusbarRight) must end before Gen 1 emergency lane ($ats1EmergX)",
            mdbBusbarRight < ats1EmergX
        )
        assertTrue(
            "MDB busbar right ($mdbBusbarRight) must end before Gen 2 emergency lane ($ats2EmergX)",
            mdbBusbarRight < ats2EmergX
        )

        // 3. No emergency edge intersects MDB busbar
        val emergencyEdges = result.edges.filter { it.isEmergency }
        assertEquals("Must have 2 emergency edges", 2, emergencyEdges.size)
        for (edge in emergencyEdges) {
            for (i in 0 until edge.pathPoints.size - 1) {
                val p1 = edge.pathPoints[i]
                val p2 = edge.pathPoints[i + 1]
                val intersects = doesSegmentIntersectBox(p1, p2, mdbBusbarLeft, mdbBusbarTop, mdbBusbarRight, mdbBusbarBottom)
                assertFalse("Emergency segment ${edge.fromId}->${edge.toId} $p1 -> $p2 must not intersect MDB busbar", intersects)
            }
        }

        // 4. Strict Orthogonality check on all edges
        for (edge in result.edges) {
            for (i in 0 until edge.pathPoints.size - 1) {
                val p1 = edge.pathPoints[i]
                val p2 = edge.pathPoints[i + 1]
                val isStrictlyOrthogonal = (Math.abs(p1.first - p2.first) < 1e-3f) || (Math.abs(p1.second - p2.second) < 1e-3f)
                assertTrue("Edge ${edge.fromId}->${edge.toId} segment $p1 -> $p2 must be strictly horizontal or vertical", isStrictlyOrthogonal)
            }
        }
    }
}
