package com.example.engine

import com.example.data.CircuitEntity
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.exp
import kotlin.math.round

data class ShortCircuitResult(
    val circuitId: Int,
    val sourceName: String,
    val cableMm2: String,
    val iscKA: Double,
    val iwKA: Double,
    val deviceKA: Double,
    val deviceCheck: String,
    val cableCheck: String,
    val marginPct: Double,
    val status: String,
    val note: String,
    val ipKA: Double = 0.0,
    val zTotalMOhm: Double = 0.0,
    val rMOhm: Double = 0.0,
    val xMOhm: Double = 0.0
)

object ShortCircuitEngine {

    fun calculate(
        resolvedCircuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>,
        sizingResults: Map<Int, SizingResult>,
        transformerResults: Map<String, TransformerResult>
    ): Map<Int, ShortCircuitResult> {
        val results = mutableMapOf<Int, ShortCircuitResult>()

        for (circuit in resolvedCircuits) {
            val sizing = sizingResults[circuit.id] ?: continue
            
            // Validate data integrity guards
            val rawCableStr = sizing.cableSize_mm2
            val cableMm2 = parseNumericSize(rawCableStr)
            
            if (sizing.cableOD_mm <= 0.0 || cableMm2 == null) {
                results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Missing OD or unable to parse cable size")
                continue
            }

            // Find the source transformer
            var txResult: TransformerResult? = null
            var txName: String = ""
            var curr = circuit
            while (true) {
                val fedLower = curr.fedFrom.lowercase()
                if (fedLower in listOf("transformer", "ats", "generator", "grid")) {
                    txResult = transformerResults[curr.transformerId]
                    txName = curr.transformerId
                    break
                }
                val parentId = circuitParents[curr.id]
                if (parentId == null) break
                curr = resolvedCircuits.find { it.id == parentId } ?: break
            }

            if (txResult == null) {
                results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Transformer source not found")
                continue
            }

            val c = 1.05
            val un = txResult.voltage
            val txIsc = txResult.isc3Phase
            
            if (txIsc <= 0.0) {
                results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Transformer Isc is zero or invalid")
                continue
            }

            var rTotalOhm = txResult.rt / 1000.0
            var xTotalOhm = txResult.xt / 1000.0

            // Walk the chain from this circuit up to transformer
            var step: CircuitEntity? = circuit
            while (step != null) {
                val stepSizing = sizingResults[step.id]
                if (stepSizing != null) {
                    val rSeg = stepSizing.rUsed * step.length_m / (1000.0 * step.parallelRuns)
                    val xSeg = stepSizing.xUsed * step.length_m / (1000.0 * step.parallelRuns)
                    rTotalOhm += rSeg
                    xTotalOhm += xSeg
                }
                val parentId = circuitParents[step.id]
                step = if (parentId != null) resolvedCircuits.find { it.id == parentId } else null
            }

            val zTotalOhm = sqrt(rTotalOhm.pow(2) + xTotalOhm.pow(2))
            val iscKA = if (zTotalOhm > 0.0) ((c * un) / (sqrt(3.0) * zTotalOhm)) / 1000.0 else 0.0

            if (iscKA <= 0.0 || iscKA.isNaN()) {
                results[circuit.id] = buildMissingResult(circuit, "DATA MISSING", "Isc calculated as zero or NaN")
                continue
            }

            // IEC 60909 Peak Factor kappa calculation: kappa = 1.02 + 0.98 * e^(-3 * R / X)
            val kappa = if (xTotalOhm > 0.0) 1.02 + 0.98 * exp(-3.0 * (rTotalOhm / xTotalOhm)) else 1.0
            val ipKA = sqrt(2.0) * kappa * iscKA

            // Cable thermal withstand: Iw = (kFactor * S) / sqrt(clearingTime_s) / 1000
            val iwKA = (sizing.kFactor * cableMm2) / sqrt(sizing.clearingTime_s) / 1000.0

            val deviceCheckOk = sizing.deviceBreakingKA >= iscKA
            val cableCheckOk = iwKA >= iscKA
            
            val deviceCheckStr = if (deviceCheckOk) "PASS" else "FAIL"
            val cableCheckStr = if (cableCheckOk) "PASS" else "FAIL"

            val marginPct = ((iwKA / iscKA) - 1.0) * 100.0
            
            val isSwitchingDevice = isSwitchingDevice(sizing.deviceType)
            var note = ""
            var rowStatus = if (deviceCheckOk && cableCheckOk) "PASS" else "FAIL"

            if (isSwitchingDevice) {
                note = "WARNING: switching device — verify upstream protection"
                rowStatus = if (cableCheckOk) "WARNING" else "FAIL"
            } else if (sizing.deviceBreakingKA == 10.0 || sizing.deviceBreakingKA == 36.0 || sizing.deviceBreakingKA == 65.0) {
                note = "Breaking capacity assumed (not from catalog) — verify"
            }

            results[circuit.id] = ShortCircuitResult(
                circuitId = circuit.id,
                sourceName = txName,
                cableMm2 = rawCableStr,
                iscKA = round(iscKA * 100) / 100.0,
                iwKA = round(iwKA * 100) / 100.0,
                deviceKA = sizing.deviceBreakingKA,
                deviceCheck = if (isSwitchingDevice) "N/A" else deviceCheckStr,
                cableCheck = cableCheckStr,
                marginPct = round(marginPct * 10) / 10.0,
                status = rowStatus,
                note = note,
                ipKA = round(ipKA * 100) / 100.0,
                zTotalMOhm = round(zTotalOhm * 1000.0 * 100) / 100.0,
                rMOhm = round(rTotalOhm * 1000.0 * 100) / 100.0,
                xMOhm = round(xTotalOhm * 1000.0 * 100) / 100.0
            )
        }
        return results
    }

    private fun isSwitchingDevice(type: String): Boolean {
        val upper = type.uppercase()
        return upper.contains("LBS") || 
               upper.contains("ISOLATOR") || 
               upper.contains("ATS") || 
               upper.contains("BUS COUPLER") || 
               upper.contains("CHANGE-OVER")
    }

    private fun buildMissingResult(circuit: CircuitEntity, status: String, note: String): ShortCircuitResult {
        return ShortCircuitResult(
            circuitId = circuit.id,
            sourceName = "N/A",
            cableMm2 = "-",
            iscKA = 0.0,
            iwKA = 0.0,
            deviceKA = 0.0,
            deviceCheck = "-",
            cableCheck = "-",
            marginPct = 0.0,
            status = status,
            note = note
        )
    }

    private fun parseNumericSize(value: String): Double? {
        val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""").find(value)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}
