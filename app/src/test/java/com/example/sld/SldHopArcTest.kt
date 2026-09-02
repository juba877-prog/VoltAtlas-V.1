package com.example.sld

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 §3.2 — Hop Arc (bridge) engine verification.
 *
 *  (a) Two intersecting orthogonal edges generate a hop arc centered on the
 *      intersection coordinate (the interrupted vertical wire is bridged).
 *  (b) Non-intersecting parallel edges, connected T-junctions and endpoint-touches
 *      do NOT generate hop arcs.
 *  (c) A single wire carrying multiple hop arcs preserves polyline continuity and
 *      vertex order (every command starts where the previous command ended).
 */
class SldHopArcTest {

    private fun edge(from: String, to: String, vararg pts: Pair<Float, Float>): PositionedSldEdge =
        PositionedSldEdge(
            fromId = from,
            toId = to,
            label = "",
            pathPoints = pts.toList()
        )

    private fun arcs(commands: List<SldHopArcEngine.HopPathCommand>): List<SldHopArcEngine.HopPathCommand.Arc> =
        commands.filterIsInstance<SldHopArcEngine.HopPathCommand.Arc>()

    private fun assertClose(expected: Offset, actual: Offset, tolerance: Float = 0.01f, message: String = "") {
        assertEquals("$message expected=$expected actual=$actual", expected.x, actual.x, tolerance)
        assertEquals("$message expected=$expected actual=$actual", expected.y, actual.y, tolerance)
    }

    @Test
    fun intersectingEdges_generateHopArcAtIntersection() {
        val vertical = edge("A", "B", 100f to 0f, 100f to 100f)
        val horizontal = edge("C", "D", 50f to 50f, 150f to 50f)

        val crossings = SldHopArcEngine.detectHopCrossings(listOf(vertical, horizontal))

        assertEquals("Exactly one hop crossing expected", 1, crossings.size)
        val crossing = crossings.first()
        assertClose(Offset(100f, 50f), crossing.point, message = "Crossing must be at the intersection")
        assertEquals("Vertical edge must be interrupted", "A->B", crossing.interruptedEdgeId)
        assertEquals("Horizontal edge passes through", "C->D", crossing.crossingEdgeId)

        val commands = SldHopArcEngine.buildPathCommands(vertical, crossings)
        val hopArcs = arcs(commands)
        assertEquals("Vertical wire must gain exactly one bridge arc", 1, hopArcs.size)
        assertClose(Offset(100f, 50f), hopArcs.first().center, message = "Arc center = intersection")
        assertEquals(SldHopArcEngine.HOP_RADIUS, hopArcs.first().radius, 0.001f)
        assertClose(Offset(100f, 50f - SldHopArcEngine.HOP_RADIUS), hopArcs.first().start, message = "Arc starts above crossing")
        assertClose(Offset(100f, 50f + SldHopArcEngine.HOP_RADIUS), hopArcs.first().end, message = "Arc ends below crossing")
        assertEquals(-90f, hopArcs.first().startAngleDegrees, 0.001f)
        assertEquals(180f, hopArcs.first().sweepAngleDegrees, 0.001f)
    }

    @Test
    fun parallelAndConnectedOrEndpointTouches_generateNoHopArc() {
        // (1) Non-intersecting parallel vertical edges.
        val v1 = edge("A", "B", 100f to 0f, 100f to 100f)
        val v2 = edge("C", "D", 300f to 0f, 300f to 100f)
        assertTrue(
            "Parallel edges must not cross",
            SldHopArcEngine.detectHopCrossings(listOf(v1, v2)).isEmpty()
        )

        // (2) Connected T-junction: B is shared by both edges; they meet at the terminal.
        val vertical = edge("A", "B", 100f to 0f, 100f to 100f)
        val connectedHorizontal = edge("B", "C", 100f to 100f, 200f to 100f)
        assertTrue("Edges sharing node B are connected", SldHopArcEngine.edgesAreConnected(vertical, connectedHorizontal))
        assertTrue(
            "Connected T-junction must not generate a hop",
            SldHopArcEngine.detectHopCrossings(listOf(vertical, connectedHorizontal)).isEmpty()
        )

        // (3) Unconnected edge whose vertical TERMINATES on a horizontal interior point
        //     (endpoint touch = T-junction, not a crossing).
        val verticalDrop = edge("A", "B", 100f to 0f, 100f to 50f)
        val horizontalPass = edge("C", "D", 50f to 50f, 150f to 50f)
        assertFalse(
            "Edges touching at an endpoint are not connected",
            SldHopArcEngine.edgesAreConnected(verticalDrop, horizontalPass)
        )
        assertTrue(
            "Endpoint touch must not generate a hop (strictly-interior rule)",
            SldHopArcEngine.detectHopCrossings(listOf(verticalDrop, horizontalPass)).isEmpty()
        )

        // (4) Horizontal touching a vertical at the vertical's endpoint (mirror case).
        val verticalFull = edge("A", "B", 100f to 0f, 100f to 100f)
        val horizontalTouchingBottom = edge("C", "D", 50f to 100f, 150f to 100f)
        assertTrue(
            "Endpoint touch on the other axis must also be ignored",
            SldHopArcEngine.detectHopCrossings(listOf(verticalFull, horizontalTouchingBottom)).isEmpty()
        )
    }

    @Test
    fun multipleHopsOnSingleWire_preserveContinuityAndVertexOrder() {
        val vertical = edge("A", "B", 100f to 0f, 100f to 200f)
        val h1 = edge("C", "D", 50f to 50f, 150f to 50f)
        val h2 = edge("E", "F", 50f to 120f, 150f to 120f)

        val crossings = SldHopArcEngine.detectHopCrossings(listOf(vertical, h1, h2))
        assertEquals("Two hop crossings expected on the vertical wire", 2, crossings.size)

        val commands = SldHopArcEngine.buildPathCommands(vertical, crossings)
        val hopArcs = arcs(commands)
        assertEquals("Exactly two bridge arcs on the interrupted wire", 2, hopArcs.size)
        assertTrue("Arcs must be ordered along the wire (ascending Y)", hopArcs[0].center.y < hopArcs[1].center.y)
        assertClose(Offset(100f, 50f), hopArcs[0].center, message = "First hop at first crossing")
        assertClose(Offset(100f, 120f), hopArcs[1].center, message = "Second hop at second crossing")

        // Continuity: every command starts where the previous command ended.
        assertClose(Offset(100f, 0f), commands.first().start, message = "Wire starts at polyline origin")
        assertClose(Offset(100f, 200f), commands.last().end, message = "Wire ends at polyline terminus")
        for (i in 1 until commands.size) {
            assertClose(
                commands[i - 1].end,
                commands[i].start,
                message = "Command ${i - 1}->$i must be continuous"
            )
        }

        // Vertex order: the full walk strictly progresses from start to finish.
        val allPoints = buildList {
            commands.forEach { cmd ->
                add(cmd.start)
                add(cmd.end)
            }
        }
        assertEquals(Offset(100f, 0f), allPoints.first())
        assertEquals(Offset(100f, 200f), allPoints.last())
        assertTrue(
            "Waypoints must be monotonically downward on the vertical wire",
            allPoints.zipWithNext().all { (a, b) -> a.y <= b.y + 0.01f }
        )
    }

    @Test
    fun upwardVerticalWire_preservesContinuityAndAscendingProgression() {
        val verticalUp = edge("A", "B", 100f to 200f, 100f to 0f)
        val h1 = edge("C", "D", 50f to 120f, 150f to 120f)
        val h2 = edge("E", "F", 50f to 50f, 150f to 50f)

        val crossings = SldHopArcEngine.detectHopCrossings(listOf(verticalUp, h1, h2))
        assertEquals("Two hop crossings expected on the upward vertical wire", 2, crossings.size)

        val commands = SldHopArcEngine.buildPathCommands(verticalUp, crossings)
        val hopArcs = arcs(commands)
        assertEquals("Exactly two bridge arcs on the interrupted wire", 2, hopArcs.size)
        assertTrue("Arcs must be ordered along the wire direction (descending Y)", hopArcs[0].center.y > hopArcs[1].center.y)
        assertClose(Offset(100f, 120f), hopArcs[0].center, message = "First encountered hop at y=120")
        assertClose(Offset(100f, 50f), hopArcs[1].center, message = "Second encountered hop at y=50")

        // Continuity check
        assertClose(Offset(100f, 200f), commands.first().start, message = "Wire starts at origin")
        assertClose(Offset(100f, 0f), commands.last().end, message = "Wire ends at terminus")
        for (i in 1 until commands.size) {
            assertClose(
                commands[i - 1].end,
                commands[i].start,
                message = "Command ${i - 1}->$i must be continuous"
            )
        }
    }
}