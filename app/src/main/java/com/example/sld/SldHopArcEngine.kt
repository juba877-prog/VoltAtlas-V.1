package com.example.sld

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 3 §3.2 — 2D orthogonal segment intersection & Hop Arc (bridge) engine.
 *
 * Crossing orthogonal wires are visually ambiguous (a crossing reads as an
 * electrical junction). This engine finds every place where a wire of one edge
 * crosses a wire of a DISTINCT, NON-CONNECTED edge and inserts a small
 * semicircular "hop" bridge into the interrupted (vertical) wire so the two
 * conductors are visually separated.
 *
 * Rules (matching the schematic convention):
 *  - Only strictly-INTERIOR perpendicular crossings generate a hop — a
 *    crossing that lands on a segment endpoint is a T-junction / node terminal
 *    and is never bridged.
 *  - Edges that share a node (same source or same target) are connected
 *    T-junction topology and are never bridged.
 *  - The vertical segment at a crossing is interrupted; the horizontal segment
 *    passes through straight.
 *  - Every edge remains ONE continuous path (Line → Arc → Line), so dashed
 *    emergency strokes flow across hop arcs without phase distortion.
 *
 * The engine is pure geometry (no DrawScope) so it is unit-testable and the
 * [SldCanvas] draw loop simply converts the returned commands to a memoized
 * [Path] with zero per-frame allocations.
 */
object SldHopArcEngine {

    /** Schematic bridge radius (Phase 3 §3.2). */
    const val HOP_RADIUS = 8f

    /** Interior-epsilon used to distinguish strict crossings from endpoint T-junctions. */
    const val EPSILON = 1e-3f

    /** Axis-aligned segment of an orthogonal polyline. */
    data class OrthogonalSegment(
        val edgeId: String,
        val pathIndex: Int,
        val start: Offset,
        val end: Offset
    ) {
        val isHorizontal: Boolean get() = abs(start.y - end.y) < EPSILON
        val isVertical: Boolean get() = abs(start.x - end.x) < EPSILON
        val left: Float get() = min(start.x, end.x)
        val right: Float get() = max(start.x, end.x)
        val top: Float get() = min(start.y, end.y)
        val bottom: Float get() = max(start.y, end.y)
    }

    /** A non-junction perpendicular crossing between two distinct edges. */
    data class HopCrossing(
        val point: Offset,
        /** Edge whose vertical segment is interrupted (gets the bridge arc). */
        val interruptedEdgeId: String,
        /** Edge that passes through un-interrupted. */
        val crossingEdgeId: String,
        /** Index of the interrupted segment within its polyline. */
        val segmentIndex: Int
    )

    /** Path command emitted by the builder (pure geometry, GC-light). */
    sealed interface HopPathCommand {
        val start: Offset
        val end: Offset

        /** Straight orthogonal segment. */
        data class Line(override val start: Offset, override val end: Offset) : HopPathCommand

        /** Semicircular bridge arc centered on [center] with [radius]. */
        data class Arc(
            override val start: Offset,
            override val end: Offset,
            val center: Offset,
            val radius: Float,
            val startAngleDegrees: Float,
            val sweepAngleDegrees: Float
        ) : HopPathCommand
    }

    /** Stable identity key for one routed edge. */
    fun edgeKey(edge: PositionedSldEdge): String = "${edge.fromId}->${edge.toId}"

    /** Decompose a polyline into its axis-aligned segments (degenerate segments dropped). */
    fun collectSegments(edgeId: String, points: List<Pair<Float, Float>>): List<OrthogonalSegment> {
        if (points.size < 2) return emptyList()
        val result = ArrayList<OrthogonalSegment>(points.size - 1)
        for (i in 0 until points.size - 1) {
            val a = Offset(points[i].first, points[i].second)
            val b = Offset(points[i + 1].first, points[i + 1].second)
            if (abs(a.x - b.x) < EPSILON && abs(a.y - b.y) < EPSILON) continue
            result.add(OrthogonalSegment(edgeId, i, a, b))
        }
        return result
    }

    /** True when two edges share a terminal node (connected topology). */
    fun edgesAreConnected(a: PositionedSldEdge, b: PositionedSldEdge): Boolean =
        a.fromId == b.fromId || a.toId == b.toId || a.fromId == b.toId || a.toId == b.fromId

    /**
     * Detect every hop crossing across a routed edge set. Only strictly-interior
     * perpendicular crossings between DISTINCT, NON-CONNECTED edges are reported;
     * coincident crossings are de-duplicated by coordinate.
     */
    fun detectHopCrossings(edges: List<PositionedSldEdge>): List<HopCrossing> {
        if (edges.size < 2) return emptyList()
        val segmentsByKey = HashMap<String, List<OrthogonalSegment>>(edges.size)
        edges.forEach { edge ->
            segmentsByKey[edgeKey(edge)] = collectSegments(edgeKey(edge), edge.pathPoints)
        }

        val crossings = ArrayList<HopCrossing>()
        for (i in edges.indices) {
            for (j in i + 1 until edges.size) {
                val ei = edges[i]
                val ej = edges[j]
                if (edgesAreConnected(ei, ej)) continue
                val si = segmentsByKey[edgeKey(ei)].orEmpty()
                val sj = segmentsByKey[edgeKey(ej)].orEmpty()
                if (si.isEmpty() || sj.isEmpty()) continue

                // a horizontal × b vertical
                for (h in si) {
                    if (!h.isHorizontal) continue
                    for (v in sj) {
                        if (!v.isVertical) continue
                        interiorCrossing(h, v)?.let { p ->
                            crossings.add(HopCrossing(p, edgeKey(ej), edgeKey(ei), v.pathIndex))
                        }
                    }
                }
                // a vertical × b horizontal
                for (v in si) {
                    if (!v.isVertical) continue
                    for (h in sj) {
                        if (!h.isHorizontal) continue
                        interiorCrossing(h, v)?.let { p ->
                            crossings.add(HopCrossing(p, edgeKey(ei), edgeKey(ej), v.pathIndex))
                        }
                    }
                }
            }
        }
        return crossings.distinctBy { it.point.x to it.point.y }
    }

    /** Perpendicular interior crossing point of a horizontal and a vertical segment, or null. */
    private fun interiorCrossing(h: OrthogonalSegment, v: OrthogonalSegment): Offset? {
        val x = v.start.x
        val y = h.start.y
        val strictlyInterior =
            x > h.left + EPSILON && x < h.right - EPSILON &&
                y > v.top + EPSILON && y < v.bottom - EPSILON
        return if (strictlyInterior) Offset(x, y) else null
    }

    /**
     * Build the (Line/Arc) command sequence for one edge, interrupting each of
     * its vertical segments at the crossings that target it. Continuity is
     * preserved: every command starts where the previous command ended.
     */
    fun buildPathCommands(
        edge: PositionedSldEdge,
        crossings: List<HopCrossing>
    ): List<HopPathCommand> {
        val points = edge.pathPoints
        if (points.size < 2) return emptyList()
        val key = edgeKey(edge)
        // Crossings on this edge, grouped per segment index, ordered along the segment.
        val hopsBySegment = crossings
            .filter { it.interruptedEdgeId == key }
            .groupBy { it.segmentIndex }
            .mapValues { (_, list) -> list.sortedBy { it.point.y } }

        val commands = ArrayList<HopPathCommand>(points.size + hopsBySegment.values.sumOf { it.size })
        for (i in 0 until points.size - 1) {
            val from = Offset(points[i].first, points[i].second)
            val to = Offset(points[i + 1].first, points[i + 1].second)
            val segmentHops = hopsBySegment[i].orEmpty()

            val isVertical = abs(from.x - to.x) < EPSILON && abs(from.y - to.y) >= EPSILON
            if (!isVertical || segmentHops.isEmpty()) {
                commands.add(HopPathCommand.Line(from, to))
                continue
            }

            val downward = from.y < to.y
            val sortedHops = if (downward) segmentHops.sortedBy { it.point.y } else segmentHops.sortedByDescending { it.point.y }

            val lowY = min(from.y, to.y)
            val highY = max(from.y, to.y)
            var cursor = from
            for (hop in sortedHops) {
                val cy = hop.point.y
                // Keep the arc fully inside the segment span; skip hops too close to an endpoint.
                if (cy - HOP_RADIUS < lowY || cy + HOP_RADIUS > highY) continue
                if (downward) {
                    commands.add(HopPathCommand.Line(cursor, Offset(from.x, cy - HOP_RADIUS)))
                    commands.add(
                        HopPathCommand.Arc(
                            start = Offset(from.x, cy - HOP_RADIUS),
                            end = Offset(from.x, cy + HOP_RADIUS),
                            center = Offset(from.x, cy),
                            radius = HOP_RADIUS,
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 180f
                        )
                    )
                    cursor = Offset(from.x, cy + HOP_RADIUS)
                } else {
                    commands.add(HopPathCommand.Line(cursor, Offset(from.x, cy + HOP_RADIUS)))
                    commands.add(
                        HopPathCommand.Arc(
                            start = Offset(from.x, cy + HOP_RADIUS),
                            end = Offset(from.x, cy - HOP_RADIUS),
                            center = Offset(from.x, cy),
                            radius = HOP_RADIUS,
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = -180f
                        )
                    )
                    cursor = Offset(from.x, cy - HOP_RADIUS)
                }
            }
            commands.add(HopPathCommand.Line(cursor, to))
        }
        return commands
    }

    /** Convert a command sequence into a single continuous [Path] (dash-safe). */
    fun buildPath(commands: List<HopPathCommand>): Path {
        val path = Path()
        if (commands.isEmpty()) return path
        path.moveTo(commands.first().start.x, commands.first().start.y)
        for (cmd in commands) {
            when (cmd) {
                is HopPathCommand.Line -> path.lineTo(cmd.end.x, cmd.end.y)
                is HopPathCommand.Arc -> path.arcTo(
                    rect = Rect(
                        left = cmd.center.x - cmd.radius,
                        top = cmd.center.y - cmd.radius,
                        right = cmd.center.x + cmd.radius,
                        bottom = cmd.center.y + cmd.radius
                    ),
                    startAngleDegrees = cmd.startAngleDegrees,
                    sweepAngleDegrees = cmd.sweepAngleDegrees,
                    forceMoveTo = false
                )
            }
        }
        return path
    }
}
