package com.example.engine

import com.example.data.CircuitEntity

data class CoordinationResult(
    val downstreamCircuitId: Int,
    val downstreamName: String,
    val upstreamName: String,
    val ratio: Double,
    val status: String, // OK / WARNING
    val reason: String
)

object CoordinationEngine {

    /**
     * Standard IEC 1.6:1 ratio for thermal (overload) coordination.
     * Standard 2:1 ratio for magnetic (short-circuit) coordination.
     */
    private const val THERMAL_RATIO_MIN = 1.6
    private const val MAGNETIC_RATIO_MIN = 2.0

    fun checkCoordination(
        downstreamCircuit: CircuitEntity,
        downstreamResult: SizingResult,
        upstreamName: String,
        upstreamRating: Double
    ): CoordinationResult {
        val downstreamRating = downstreamResult.deviceSize_A
        
        if (upstreamRating <= 0.0) {
            return CoordinationResult(
                downstreamCircuitId = downstreamCircuit.id,
                downstreamName = downstreamCircuit.circuitName,
                upstreamName = upstreamName,
                ratio = 0.0,
                status = "WARNING",
                reason = "Upstream rating missing or zero"
            )
        }

        val ratio = upstreamRating / downstreamRating
        
        // H5 — MAGNETIC_RATIO_MIN is now used in the STATUS logic (was message-text only):
        // 1.6 ≤ ratio < 2.0 passes the THERMAL screening only — magnetic (short-circuit)
        // selectivity is unresolved at current-ratio level and must be surfaced as WARNING.
        val status = when {
            ratio >= MAGNETIC_RATIO_MIN -> "✓ OK"
            else -> "⚠ WARNING"
        }
        val reason = when {
            ratio < 1.0 -> "Downstream breaker (${"%.2f".format(downstreamRating)} A) is larger than Upstream (${"%.2f".format(upstreamRating)} A)!"
            ratio < THERMAL_RATIO_MIN -> "Ratio is only ${"%.2f".format(ratio)}. Upstream (${"%.2f".format(upstreamRating)} A) to Downstream (${"%.2f".format(downstreamRating)} A) requires > 1.6 for selectivity."
            ratio < MAGNETIC_RATIO_MIN -> "Thermal coordination OK, but magnetic (short-circuit) selectivity is unresolved at this current ratio — verify with manufacturer tables."
            else -> "Likely selective (current-ratio screening) — verify with manufacturer cascade/selectivity tables per IEC 60947-2."
        }

        return CoordinationResult(
            downstreamCircuitId = downstreamCircuit.id,
            downstreamName = downstreamCircuit.circuitName,
            upstreamName = upstreamName,
            ratio = (ratio * 100).toInt() / 100.0,
            status = status,
            reason = reason
        )
    }
}
