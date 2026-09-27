package com.example.ui.components

import com.example.ui.theme.BrandMarkBlue
import com.example.ui.theme.BrandMarkGreen
import com.example.ui.theme.SlateTextSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structure, restraint and timing of the entry screen's living electrical network.
 *
 * Pure, deterministic checks that enforce the background's visual contract: peripheral
 * placement, a calm central UI safe zone, desaturated traces, energy travelling through the
 * conductor, node wake-up synchronized with that energy, a mostly idle cycle, and never more
 * than one live pulse.
 */
class EntryCircuitNetworkTest {

    /** Central band occupied by logo, title, subtitle and the two mode cards. */
    private val safeZoneX = 0.36f..0.64f
    private val safeZoneY = 0.24f..0.78f

    @Test
    fun network_hasShortAsymmetricPeripheralBranches() {
        assertEquals(5, EntryCircuitNetwork.traces.size)
        assertEquals(5, EntryCircuitNetwork.nodes.size)
        assertEquals(5, EntryCircuitNetwork.pulses.size)

        EntryCircuitNetwork.traces.forEach { trace ->
            assertTrue("trace must be a short branch (length ${trace.length})", trace.length < 0.7f)
            assertTrue("every branch needs at least two corners", trace.points.size >= 3)
        }
    }

    @Test
    fun traces_neverEnterTheCentralUiSafeZone() {
        EntryCircuitNetwork.traces.forEach { trace ->
            trace.points.forEach { point ->
                val insideX = point.x in safeZoneX
                val insideY = point.y in safeZoneY
                assertTrue(
                    "trace vertex ${point.x},${point.y} crosses the UI safe zone",
                    !(insideX && insideY)
                )
            }
        }
        EntryCircuitNetwork.nodes.forEach { node ->
            val insideX = node.position.x in safeZoneX
            val insideY = node.position.y in safeZoneY
            assertTrue(
                "node ${node.position.x},${node.position.y} sits behind the UI",
                !(insideX && insideY)
            )
        }
    }

    @Test
    fun traces_restInADesaturatedBlueGray() {
        assertEquals(SlateTextSecondary, EntryCircuitNetwork.TRACE_BASE_COLOR)
        EntryCircuitNetwork.traces.forEach { trace ->
            assertEquals(EntryCircuitNetwork.TRACE_BASE_COLOR, trace.color)
            assertTrue("trace alpha must stay subtle (was ${trace.alpha})", trace.alpha <= 0.20f)
        }
    }

    @Test
    fun energy_usesBothVoltAtlasIdentities() {
        assertTrue(EntryCircuitNetwork.pulses.any { it.color == BrandMarkBlue })
        assertTrue(EntryCircuitNetwork.pulses.any { it.color == BrandMarkGreen })
    }

    @Test
    fun pulses_areStaggeredAndNeverSimultaneous() {
        val starts = EntryCircuitNetwork.pulses.map { it.startSeconds }
        assertEquals("pulses must be staggered", starts.distinct().size, starts.size)

        val step = 0.05f
        val frames = (EntryCircuitNetwork.CYCLE_SECONDS / step).toInt()
        var overlaps = 0
        (0 until frames).forEach { index ->
            val t = index * step
            if (EntryCircuitNetwork.pulses.count { EntryCircuitNetwork.pulseState(it, t) != null } > 1) overlaps++
        }
        assertEquals("at most one pulse may be live at a time", 0, overlaps)
    }

    @Test
    fun cycleIsTenSecondsAndMostlyIdle() {
        assertEquals(10f, EntryCircuitNetwork.CYCLE_SECONDS, 0.01f)

        val step = 0.05f
        val frames = (EntryCircuitNetwork.CYCLE_SECONDS / step).toInt()
        var moving = 0
        (0 until frames).forEach { index ->
            val t = index * step
            if (EntryCircuitNetwork.pulses.any { EntryCircuitNetwork.pulseState(it, t) != null }) moving++
        }
        val ratio = moving.toFloat() / frames
        assertTrue("network is too busy (live ratio $ratio)", ratio in 0.15f..0.6f)
    }

    @Test
    fun pulseIsInvisibleBeforeStartAndAfterTravel() {
        val pulse = EntryCircuitNetwork.pulses.first()
        assertEquals(null, EntryCircuitNetwork.pulseState(pulse, pulse.startSeconds - 0.1f))
        assertEquals(
            null,
            EntryCircuitNetwork.pulseState(pulse, pulse.startSeconds + pulse.travelSeconds + 0.1f)
        )
    }

    @Test
    fun energy_travelsAlongItsOwnTrace() {
        val pulse = EntryCircuitNetwork.pulses.first()
        val trace = EntryCircuitNetwork.traces[pulse.traceIndex]

        val start = requireNotNull(EntryCircuitNetwork.pulseState(pulse, pulse.startSeconds + 0.01f))
        val mid = requireNotNull(
            EntryCircuitNetwork.pulseState(pulse, pulse.startSeconds + pulse.travelSeconds / 2f)
        )
        val end = requireNotNull(
            EntryCircuitNetwork.pulseState(pulse, pulse.startSeconds + pulse.travelSeconds - 0.01f)
        )

        assertTrue("energy must advance", mid.fraction > start.fraction)
        assertTrue("energy must reach the branch end", end.fraction > 0.98f)
        assertTrue("energy must fade in", start.alpha < mid.alpha)
        assertTrue("energy must fade out", end.alpha < mid.alpha)

        val head = trace.pointAt(mid.fraction)
        assertTrue(head.x in 0f..1f && head.y in 0f..1f)
    }

    @Test
    fun litSectionOfTheTrace_followsTheEnergyHead() {
        val trace = EntryCircuitNetwork.traces.first()
        val lit = trace.partialPath(fromFraction = 0.4f, toFraction = 0.9f)

        assertTrue("lit section needs at least two vertices", lit.size >= 2)
        lit.forEach { point -> assertTrue(point.x in 0f..1f && point.y in 0f..1f) }
        assertTrue(trace.partialPath(0f, 1f).size >= trace.points.size)
    }

    @Test
    fun nodeWakesUpOnlyAfterItsEnergyArrivesAndSettlesFast() {
        EntryCircuitNetwork.nodes.forEach { node ->
            val pulse = EntryCircuitNetwork.pulses[node.pulseIndex]
            val arrival = pulse.startSeconds + pulse.travelSeconds * node.triggerFraction

            assertEquals(
                "node must stay idle before the energy arrives",
                0f,
                EntryCircuitNetwork.nodeIntensity(node, arrival - 0.2f),
                0.0001f
            )
            assertTrue(
                "node must wake when the energy reaches it",
                EntryCircuitNetwork.nodeIntensity(node, arrival) > 0.2f
            )
            val settled = EntryCircuitNetwork.nodeIntensity(
                node,
                arrival + pulse.travelSeconds * 0.22f
            )
            assertTrue("node must settle back quickly", settled < 0.35f)
        }
    }
}

