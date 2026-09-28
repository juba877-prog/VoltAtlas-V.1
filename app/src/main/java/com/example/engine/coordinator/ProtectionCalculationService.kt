package com.example.engine.coordinator

import com.example.data.CircuitEntity
import com.example.engine.AdsResult
import com.example.engine.ProtectionEngine
import com.example.engine.ProtectionResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result container for combined ADS and Protection bulk evaluation.
 */
data class ProjectProtectionContainer(
    val adsResults: Map<Int, AdsResult>,
    val protectionResults: Map<Int, ProtectionResult>
)

/**
 * Service dedicated to orchestrating ADS (Automatic Disconnection of Supply) and Protection calculations.
 * Wraps [ProtectionEngine] without altering any underlying electrical formulas or IEC standards.
 */
object ProtectionCalculationService {

    /**
     * Calculates ADS (Zs compliance) for a single circuit and its sizing result.
     */
    fun calculateAds(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        ze_ohm: Double = 0.01,
        isZeResolved: Boolean = false
    ): AdsResult {
        return ProtectionEngine.calculateAds(
            circuit = circuit,
            sizingResult = sizingResult,
            ze_ohm = ze_ohm,
            isZeResolved = isZeResolved
        )
    }

    /**
     * Evaluates comprehensive IEC 60364-4-43 / IEC 60947-2 protection for a circuit.
     */
    fun evaluateProtection(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        shortCircuitResult: ShortCircuitResult?,
        upstreamIn_A: Double = 0.0,
        upstreamName: String = "Upstream Board",
        hasUpstreamDevice: Boolean = (upstreamIn_A > 0.0 && !upstreamName.startsWith("Incomer", ignoreCase = true))
    ): ProtectionResult {
        return ProtectionEngine.evaluateProtection(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = shortCircuitResult,
            upstreamIn_A = upstreamIn_A,
            upstreamName = upstreamName,
            hasUpstreamDevice = hasUpstreamDevice
        )
    }

    /**
     * Fallback result when ADS calculation dependencies are missing.
     *
     * ENG-30 (§12.2-a): r1/r2 are null, not 0.0 — the sizing result is absent, so there is
     * no conductor resistance to report. A 0.0 Ω/km here is not a physically meaningful
     * zero (a real conductor never has zero resistance); it was only ever a placeholder for
     * "no data", and 0.0 is exactly the value that made a missing-data loop read as a PASS.
     */
    fun buildMissingAdsResult(
        circuit: CircuitEntity,
        status: String = "✗ FAIL",
        reason: String = "Missing sizing result"
    ): AdsResult {
        return AdsResult(
            circuitId = circuit.id,
            circuitName = circuit.circuitName,
            calculatedZs = 0.0,
            maxZsAllowed = 0.0,
            status = status,
            disconnectionTime = "> 0.4s",
            r1_ohm_km = null,
            r2_ohm_km = null,
            length_m = circuit.length_m
        )
    }

    /**
     * Fallback result when Protection calculation dependencies are missing.
     */
    fun buildMissingProtectionResult(
        circuit: CircuitEntity,
        status: String = "FAIL",
        reason: String = "Missing sizing or short-circuit result"
    ): ProtectionResult {
        return ProtectionResult(
            circuitId = circuit.id,
            circuitName = circuit.circuitName,
            Ib_A = 0.0,
            In_A = 0.0,
            Iz_A = 0.0,
            iscKA = 0.0,
            icuKA = 0.0,
            overloadStatus = "FAIL",
            shortCircuitStatus = "FAIL",
            cableWithstandStatus = "FAIL",
            selectivityStatus = "WARNING",
            finalStatus = status,
            messages = listOf(reason)
        )
    }

    /**
     * Suspend wrapper for background ADS calculation.
     */
    suspend fun calculateAdsAsync(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        ze_ohm: Double = 0.01,
        isZeResolved: Boolean = false
    ): AdsResult = withContext(Dispatchers.Default) {
        calculateAds(circuit, sizingResult, ze_ohm, isZeResolved)
    }

    /**
     * Suspend wrapper for background Protection evaluation.
     */
    suspend fun evaluateProtectionAsync(
        circuit: CircuitEntity,
        sizingResult: SizingResult,
        shortCircuitResult: ShortCircuitResult?,
        upstreamIn_A: Double = 0.0,
        upstreamName: String = "Upstream Board",
        hasUpstreamDevice: Boolean = (upstreamIn_A > 0.0 && !upstreamName.startsWith("Incomer", ignoreCase = true))
    ): ProtectionResult = withContext(Dispatchers.Default) {
        evaluateProtection(circuit, sizingResult, shortCircuitResult, upstreamIn_A, upstreamName, hasUpstreamDevice)
    }
}
