package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandMarkBlue
import com.example.ui.theme.BrandMarkGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.LightBackgroundDeep
import com.example.ui.theme.LightBorderSubtle
import com.example.ui.theme.LightDivider
import com.example.ui.theme.LightPrimaryBlue
import com.example.ui.theme.LocalThemeIsDark
import com.example.ui.theme.SlateTextSecondary
import kotlin.math.abs
import kotlin.math.min

/**
 * Geometry + timing of the entry screen's "living electrical network".
 *
 * All coordinates are normalised (0..1) fractions of the canvas, so the same network scales
 * to any phone size without hardcoded pixel dimensions. Pure data and pure functions only:
 * fully unit-testable, no UI, no engineering values.
 */
internal object EntryCircuitNetwork {

    const val CYCLE_SECONDS = 10f

    /**
     * Resting trace colour: a desaturated blue-gray taken from the existing VoltAtlas token
     * set. Circuit geometry is never neon at rest — only live energy takes on the saturated
     * brand blue / green.
     */
    internal val TRACE_BASE_COLOR: Color = SlateTextSecondary

    /** One orthogonal circuit trace. */
    internal data class Trace(
        val points: List<Offset>,
        val color: Color,
        val alpha: Float
    ) {
        val length: Float
            get() = points.zipWithNext().sumOf { (a, b) ->
                abs(b.x - a.x).toDouble() + abs(b.y - a.y).toDouble()
            }.toFloat()

        /** Point at [fraction] (0..1) along the polyline, corner by corner. */
        fun pointAt(fraction: Float): Offset {
            if (points.size < 2) return points.firstOrNull() ?: Offset.Zero
            var remaining = fraction.coerceIn(0f, 1f) * length
            points.zipWithNext().forEach { (a, b) ->
                val segment = abs(b.x - a.x) + abs(b.y - a.y)
                if (remaining <= segment) {
                    val local = if (segment == 0f) 0f else remaining / segment
                    return Offset(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local)
                }
                remaining -= segment
            }
            return points.last()
        }

        /**
         * Vertices of the sub-path between two fractions — used to let energy brighten the
         * trace itself as it passes, instead of a particle floating above a static line.
         */
        fun partialPath(fromFraction: Float, toFraction: Float): List<Offset> {
            val from = fromFraction.coerceIn(0f, 1f)
            val to = toFraction.coerceIn(from, 1f)
            val result = mutableListOf(pointAt(from))
            var travelled = 0f
            points.zipWithNext().forEach { (start, end) ->
                val segment = abs(end.x - start.x) + abs(end.y - start.y)
                val segmentEnd = (travelled + segment) / length
                if (segmentEnd > from && travelled < to) result += start
                travelled = segmentEnd
            }
            result += pointAt(to)
            return result
        }
    }

    /** A node that briefly wakes up when a pulse reaches it. */
    internal data class Node(
        val position: Offset,
        val color: Color,
        val triggerFraction: Float,
        val pulseIndex: Int
    )

    /** A staggered energy pulse travelling along one trace. */
    internal data class Pulse(
        val traceIndex: Int,
        val nodeIndex: Int,
        val color: Color,
        val startSeconds: Float,
        val travelSeconds: Float,
        val fadeInSeconds: Float = 0.45f,
        val fadeOutSeconds: Float = 0.7f
    )

    /**
     * Five short, asymmetric peripheral branches. All of them stay outside the central
     * UI safe zone (logo / title / subtitle / cards), so the foreground always stays the hero.
     * Traces are desaturated blue-gray at rest: only live energy becomes VoltAtlas blue/green.
     */
    internal val traces: List<Trace> = listOf(
        // upper-left branch (blue identity)
        Trace(
            points = listOf(
                Offset(0.035f, 0.105f), Offset(0.21f, 0.105f),
                Offset(0.21f, 0.185f), Offset(0.30f, 0.185f)
            ),
            color = TRACE_BASE_COLOR,
            alpha = 0.16f
        ),
        // upper-right branch (green identity)
        Trace(
            points = listOf(
                Offset(0.965f, 0.135f), Offset(0.85f, 0.135f),
                Offset(0.85f, 0.205f), Offset(0.93f, 0.205f)
            ),
            color = TRACE_BASE_COLOR,
            alpha = 0.15f
        ),
        // small top-centre-left stub (sparse, off-balance on purpose)
        Trace(
            points = listOf(Offset(0.285f, 0.052f), Offset(0.40f, 0.052f), Offset(0.40f, 0.105f)),
            color = TRACE_BASE_COLOR,
            alpha = 0.12f
        ),
        // lower-left branch (blue identity)
        Trace(
            points = listOf(
                Offset(0.03f, 0.825f), Offset(0.145f, 0.825f),
                Offset(0.145f, 0.915f), Offset(0.06f, 0.915f)
            ),
            color = TRACE_BASE_COLOR,
            alpha = 0.15f
        ),
        // lower-right branch (green identity)
        Trace(
            points = listOf(
                Offset(0.97f, 0.865f), Offset(0.885f, 0.865f),
                Offset(0.885f, 0.945f), Offset(0.95f, 0.945f)
            ),
            color = TRACE_BASE_COLOR,
            alpha = 0.14f
        )
    )

    /** Small connection points. They never read as glowing orbs. */
    internal val nodes: List<Node> = listOf(
        Node(Offset(0.21f, 0.185f), BrandMarkBlue, triggerFraction = 0.74f, pulseIndex = 0),
        Node(Offset(0.85f, 0.205f), BrandMarkGreen, triggerFraction = 0.72f, pulseIndex = 1),
        Node(Offset(0.40f, 0.105f), BrandMarkBlue, triggerFraction = 0.78f, pulseIndex = 2),
        Node(Offset(0.145f, 0.915f), BrandMarkBlue, triggerFraction = 0.74f, pulseIndex = 3),
        Node(Offset(0.885f, 0.865f), BrandMarkGreen, triggerFraction = 0.52f, pulseIndex = 4)
    )

    /**
     * Staggered single-pulse events. Durations differ slightly and the order alternates
     * blue/green so the loop never feels like one dot repeating a predictable path.
     * At most one pulse is ever live (§7) and the cycle is mostly idle (§10).
     */
    internal val pulses: List<Pulse> = listOf(
        Pulse(traceIndex = 0, nodeIndex = 0, color = BrandMarkBlue, startSeconds = 0.8f, travelSeconds = 1.3f),
        Pulse(traceIndex = 1, nodeIndex = 1, color = BrandMarkGreen, startSeconds = 2.6f, travelSeconds = 1.1f),
        Pulse(traceIndex = 4, nodeIndex = 4, color = BrandMarkBlue, startSeconds = 4.4f, travelSeconds = 1.2f),
        Pulse(traceIndex = 3, nodeIndex = 3, color = BrandMarkGreen, startSeconds = 6.1f, travelSeconds = 1.0f),
        Pulse(traceIndex = 2, nodeIndex = 2, color = BrandMarkBlue, startSeconds = 7.9f, travelSeconds = 0.9f)
    )

    internal data class PulseState(val fraction: Float, val alpha: Float)

    /** Pulse state at [timeSeconds], or null when that pulse is idle (keeps the screen calm). */
    internal fun pulseState(pulse: Pulse, timeSeconds: Float): PulseState? {
        val elapsed = timeSeconds - pulse.startSeconds
        if (elapsed < 0f || elapsed > pulse.travelSeconds) return null
        val fraction = (elapsed / pulse.travelSeconds).coerceIn(0f, 1f)
        val fadeIn = (elapsed / pulse.fadeInSeconds).coerceIn(0f, 1f)
        val fadeOut = ((pulse.travelSeconds - elapsed) / pulse.fadeOutSeconds).coerceIn(0f, 1f)
        return PulseState(fraction, min(fadeIn, fadeOut).coerceIn(0f, 1f))
    }

    /** Node wake-up intensity (0 = idle, 1 = peak), synchronized with the pulse arrival. */
    internal fun nodeIntensity(node: Node, timeSeconds: Float): Float {
        val pulse = pulses.getOrNull(node.pulseIndex) ?: return 0f
        val state = pulseState(pulse, timeSeconds) ?: return 0f
        // A small negative delta is float rounding at the exact arrival instant: the wake-up
        // must begin there, one frame earlier, never a frame later.
        if (state.fraction < node.triggerFraction - 0.03f) return 0f
        val sinceArrival = (state.fraction - node.triggerFraction).coerceAtLeast(0f)
        val decay = 1f - (sinceArrival / 0.22f).coerceIn(0f, 1f)
        return (decay * state.alpha).coerceIn(0f, 1f)
    }
}

/**
 * Living electrical background for the VoltAtlas entry surface.
 *
 * Layer architecture:
 *  - static VoltAtlas navy/charcoal background
 *  - static engineering grid (Compose Canvas, same language as the Project Picker)
 *  - electrical layer: static circuit traces, staggered energy pulses travelling along them
 *    with a short fading trail, and node wake-up responses
 *
 * The electrical layer is a single Compose Canvas (no AndroidView), so it can never
 * intercept touches meant for the UI and costs ~20 draw calls per frame.
 *
 * Performance / battery:
 *  - the infinite clock exists only while the screen is resumed and motion is allowed
 *  - reduced motion (or a backgrounded screen) draws one static frame, so no animation is
 *    processed at all; navigating away disposes the composition
 */
@Composable
fun EntryCircuitBackground(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    reduceMotion: Boolean = false
) {
    val animate = isPlaying && !reduceMotion
    val isDark = LocalThemeIsDark.current
    val backgroundColor = if (isDark) Color(0xFF040812) else LightBackgroundDeep
    val gridColor = if (isDark) BrandMarkBlue else LightDivider

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // --- STATIC GRID (engineering identity, never animates) -------------------
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isDark) 0.07f else 0.40f)
                .testTag("entry_static_grid")
        ) {
            val stepX = size.width / GRID_COLUMNS
            val stepY = size.height / GRID_ROWS
            val lineWidth = 1.dp.toPx()
            for (column in 1 until GRID_COLUMNS) {
                val x = column * stepX
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), lineWidth)
            }
            for (row in 1 until GRID_ROWS) {
                val y = row * stepY
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), lineWidth)
            }
        }

        // --- ELECTRICAL LAYER (traces + energy pulses + node wake-up) -------------
        if (animate) {
            val transition = rememberInfiniteTransition(label = "entryCircuitClock")
            val clockSeconds by transition.animateFloat(
                initialValue = 0f,
                targetValue = EntryCircuitNetwork.CYCLE_SECONDS,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (EntryCircuitNetwork.CYCLE_SECONDS * 1000f).toInt(),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "entryCircuitClockValue"
            )
            EntryCircuitLayer(
                timeSeconds = clockSeconds,
                isDark = isDark,
                modifier = Modifier.testTag("entry_circuit_animation")
            )
        } else {
            EntryCircuitLayer(
                timeSeconds = STATIC_FRAME_SECONDS,
                isDark = isDark,
                modifier = Modifier.testTag("entry_circuit_animation")
            )
        }
    }
}


/**
 * Draws circuit traces, energy pulses and node wake-up states for one instant.
 * One Canvas pass: no layers, no blur, no raster, no text, no controls.
 */
@Composable
private fun EntryCircuitLayer(
    timeSeconds: Float,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        fun toPixels(normalized: Offset) = Offset(normalized.x * size.width, normalized.y * size.height)

        // 1. Static traces: thin, desaturated, subordinate to the UI
        EntryCircuitNetwork.traces.forEach { trace ->
            val path = Path().apply {
                trace.points.forEachIndexed { index, point ->
                    val px = toPixels(point)
                    if (index == 0) moveTo(px.x, px.y) else lineTo(px.x, px.y)
                }
            }
            drawPath(
                path = path,
                color = if (isDark) trace.color.copy(alpha = trace.alpha) else LightBorderSubtle.copy(alpha = 0.35f),
                style = Stroke(width = TRACE_STROKE_DP.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 2. Nodes: small crisp connection points with glowing halo
        EntryCircuitNetwork.nodes.forEach { node ->
            val intensity = EntryCircuitNetwork.nodeIntensity(node, timeSeconds)
            val center = toPixels(node.position)
            val nodeColor = if (isDark) node.color else (if (node.color == BrandMarkBlue) LightPrimaryBlue else Color(0xFF059669))
            drawCircle(
                color = nodeColor.copy(alpha = if (isDark) (0.22f + 0.35f * intensity) else (0.16f + 0.20f * intensity)),
                radius = NODE_GLOW_RADIUS * size.width * (1f + 0.25f * intensity),
                center = center
            )
            drawCircle(
                color = nodeColor.copy(alpha = if (isDark) (0.90f + 0.10f * intensity) else 0.85f),
                radius = NODE_CORE_RADIUS * size.width * (1f + 0.20f * intensity),
                center = center
            )
        }

        // 3. Energy passing THROUGH the trace: the travelled section of the conductor
        //    brightens and a small bright head rides its end. No floating particles.
        EntryCircuitNetwork.pulses.forEach { pulse ->
            val state = EntryCircuitNetwork.pulseState(pulse, timeSeconds) ?: return@forEach
            val trace = EntryCircuitNetwork.traces.getOrNull(pulse.traceIndex) ?: return@forEach
            val pulseColor = if (isDark) pulse.color else (if (pulse.color == BrandMarkBlue) LightPrimaryBlue else Color(0xFF059669))

            val litSection = trace.partialPath(
                fromFraction = (state.fraction - TRACE_HIGHLIGHT_FRACTION).coerceAtLeast(0f),
                toFraction = state.fraction
            )
            if (litSection.size >= 2) {
                val litPath = Path().apply {
                    litSection.forEachIndexed { index, point ->
                        val px = toPixels(point)
                        if (index == 0) moveTo(px.x, px.y) else lineTo(px.x, px.y)
                    }
                }
                drawPath(
                    path = litPath,
                    color = pulseColor.copy(alpha = if (isDark) (0.70f * state.alpha) else (0.55f * state.alpha)),
                    style = Stroke(width = (TRACE_STROKE_DP + 1.2f).dp.toPx(), cap = StrokeCap.Round)
                )
            }

            val head = toPixels(trace.pointAt(state.fraction))
            drawCircle(
                color = pulseColor.copy(alpha = if (isDark) (0.25f * state.alpha) else (0.20f * state.alpha)),
                radius = PULSE_GLOW_RADIUS * size.width * 1.5f,
                center = head
            )
            drawCircle(
                color = pulseColor.copy(alpha = if (isDark) (0.95f * state.alpha) else (0.90f * state.alpha)),
                radius = PULSE_RADIUS * size.width * 1.5f,
                center = head
            )
        }
    }
}

private const val GRID_COLUMNS = 9
private const val GRID_ROWS = 16
private const val GRID_ALPHA = 0.04f

/** Trace geometry: hairline technical strokes, never HUD-like. */
private const val TRACE_STROKE_DP = 1.1f

/** Share of the trace that stays briefly lit behind the energy head. */
private const val TRACE_HIGHLIGHT_FRACTION = 0.22f

/** Radii are fractions of the canvas width (~360dp reference): tiny, crisp connection points. */
private const val NODE_CORE_RADIUS = 0.010f   // ~3.6dp
private const val NODE_GLOW_RADIUS = 0.026f  // ~9.4dp
private const val PULSE_RADIUS = 0.0035f     // ~1.3dp bright head
private const val PULSE_GLOW_RADIUS = 0.011f // ~4dp soft glow

/** Representative frozen frame: traces lit with one pulse mid-branch. */
private const val STATIC_FRAME_SECONDS = 1.4f

