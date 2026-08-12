package com.example.engine

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import kotlin.math.sqrt

data class TransformerResult(
    val connectedLoad: Double,
    val circuitsAssigned: Int,
    val realPower: Double,
    val demandLoad: Double,
    val diversifiedLoad: Double,
    val futureLoad: Double,
    val designLoad: Double,
    val requiredKVA: Double,
    val recommendedSize: Double,
    val selectedSize: Double,
    val loadingPct: Double,
    val flc: Double,
    val zt: Double,
    val rt: Double,
    val xt: Double,
    val isc3Phase: Double,
    val isc1Phase: Double,
    val status: String,
    val z0: Double,
    val vectorGroup: String,
    val voltage: Double = 400.0
)

object TransformerEngine {

    val STANDARD_TX_SIZES = listOf(
        50.0, 100.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0,
        1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0
    )

    fun getDefaultPercentZ(txType: String, kVA: Double): Double {
        val isOil = txType.contains("Oil", ignoreCase = true)
        return if (isOil) {
            when {
                kVA <= 630.0 -> 4.0
                kVA <= 1250.0 -> 5.0
                else -> 6.0
            }
        } else {
            // Dry Type / Cast Resin (EN 50588-1)
            when {
                kVA <= 2500.0 -> 6.0
                else -> 8.0
            }
        }
    }

    fun calculate(
        tx: TransformerEntity,
        assignedCircuits: List<CircuitEntity>
    ): TransformerResult {
        // Handle inactive transformer
        if (tx.status.equals("Inactive", ignoreCase = true)) {
            return TransformerResult(
                connectedLoad = 0.0,
                circuitsAssigned = 0,
                realPower = 0.0,
                demandLoad = 0.0,
                diversifiedLoad = 0.0,
                futureLoad = 0.0,
                designLoad = 0.0,
                requiredKVA = 0.0,
                recommendedSize = 0.0,
                selectedSize = 0.0,
                loadingPct = 0.0,
                flc = 0.0,
                zt = 0.0,
                rt = 0.0,
                xt = 0.0,
                isc3Phase = 0.0,
                isc1Phase = 0.0,
                status = "INACTIVE",
                z0 = 0.0,
                vectorGroup = tx.vectorGroup,
                voltage = 400.0
            )
        }

        // A. Connected load aggregation for circuits assigned to this transformer
        val txIdClean = tx.transformerId.trim().lowercase()

        fun belongsToTx(c: CircuitEntity): Boolean {
            val cTxId = c.transformerId.trim().lowercase()
            if (cTxId.isNotEmpty()) {
                return cTxId == txIdClean
            }
            val fedFromClean = c.fedFrom.trim().lowercase()
            return fedFromClean == txIdClean || fedFromClean in listOf("transformer", "ats", "generator", "grid")
        }

        val txCircuits = assignedCircuits.filter { belongsToTx(it) }

        val rootCircuits = txCircuits.filter { c ->
            val fedLower = c.fedFrom.trim().lowercase()
            val isPrimarySource = fedLower in listOf("transformer", "ats", "generator", "grid", txIdClean)
            val parentExists = c.parentId?.let { pId -> assignedCircuits.any { it.id == pId } } ?: false
            val fedFromOtherCircuit = assignedCircuits.any { it.circuitName.trim().equals(c.fedFrom.trim(), ignoreCase = true) && it.id != c.id }

            isPrimarySource || (!parentExists && !fedFromOtherCircuit)
        }

        var connectedLoad = 0.0
        for (root in rootCircuits) {
            val pType = root.panelType?.trim()?.uppercase() ?: ""
            val isExplicitPanel = pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
            val isSubPanel = isExplicitPanel || assignedCircuits.any { it.parentId == root.id || it.fedFrom.trim().equals(root.circuitName.trim(), ignoreCase = true) }

            if (isSubPanel) {
                val subRes = PanelScheduleEngine.generateScheduleRecursive(
                    panelName = root.circuitName,
                    circuits = assignedCircuits,
                    targetCircuitId = root.id
                )
                connectedLoad += subRes.totalPanelLoadKVA
            } else {
                connectedLoad += root.loadKVA
            }
        }

        val circuitsCount = txCircuits.size

        // B. Demand chain: designLoad = connectedLoad * demandFactor / diversity * growth * safety
        val pf = if (tx.pf > 0.0) tx.pf else 0.8
        val realPower = connectedLoad * pf
        val demandLoad = realPower * tx.demandFactor
        val diversifiedLoad = if (tx.diversity > 0.0) demandLoad / tx.diversity else demandLoad
        val futureLoad = diversifiedLoad * tx.growth
        val designLoad = futureLoad * tx.safety
        
        val requiredKVA = designLoad / pf

        // C. Standard size selection (minSize = designLoad / 0.85)
        val minRequiredSize = requiredKVA / 0.85
        val recommendedSize = STANDARD_TX_SIZES.firstOrNull { it >= minRequiredSize } ?: STANDARD_TX_SIZES.last()
        val selectedSize = if (tx.sizeMode.equals("Manual", ignoreCase = true) && tx.manualKVA > 0.0) tx.manualKVA else recommendedSize

        // D. Loading %
        val loadingPct = if (selectedSize > 0.0) (requiredKVA / selectedSize) * 100.0 else 0.0

        // E. Full-load current FLC & Fault impedance basis (Secondary LV System)
        val secondaryVoltage = if (tx.voltage in 100..1000) tx.voltage.toDouble() else 400.0
        val flc = if (selectedSize > 0.0) (selectedSize * 1000.0) / (sqrt(3.0) * secondaryVoltage) else 0.0

        // F. Short-circuit basis (per transformer) in milliohms
        // Zt (mΩ) = (V² / (selectedSize * 1000)) * (%Z / 100) * 1000
        val zt = if (selectedSize > 0.0) (secondaryVoltage * secondaryVoltage / (selectedSize * 1000.0)) * (tx.percentZ / 100.0) * 1000.0 else 0.0

        // Rt and Xt in mΩ
        val rt = if (zt > 0.0) zt / sqrt(1.0 + tx.xr * tx.xr) else 0.0
        val xt = rt * tx.xr

        // Isc3ph (kA) = 1.05 * V / (sqrt(3) * Zt_mOhm)
        val c = 1.05
        val isc3Phase = if (zt > 0.0) (c * secondaryVoltage) / (sqrt(3.0) * zt) else 0.0

        // IEC 60909 Clause 11: Single-phase to earth short-circuit current Ik1" = (sqrt(3) * c * Un) / (2 * Z1 + Z0)
        // For Dyn11 vector group, standard Z0/Z1 ratio is 0.9 (yielding Z0 = 0.9 * Z1)
        val Z1 = zt / 1000.0
        val effectiveZ0OverZ1 = if (tx.z0OverZ1 == 1.0 && tx.vectorGroup.contains("Dyn11", ignoreCase = true)) 0.9 else tx.z0OverZ1
        val Z0 = effectiveZ0OverZ1 * Z1
        val denominator = 2.0 * Z1 + Z0
        val isc1Phase = if (denominator > 0.0) (sqrt(3.0) * c * secondaryVoltage) / denominator / 1000.0 else 0.0
        val z0_mOhm = Z0 * 1000.0

        // G. Status verdict
        val status = when {
            loadingPct > 100.0 -> "OVERLOADED — FAIL"
            connectedLoad > selectedSize -> "REVIEW — CONNECTED LOAD EXCEEDS RATING"
            else -> "VERIFIED"
        }

        return TransformerResult(
            connectedLoad = connectedLoad,
            circuitsAssigned = circuitsCount,
            realPower = realPower,
            demandLoad = demandLoad,
            diversifiedLoad = diversifiedLoad,
            futureLoad = futureLoad,
            designLoad = designLoad,
            requiredKVA = requiredKVA,
            recommendedSize = recommendedSize,
            selectedSize = selectedSize,
            loadingPct = loadingPct,
            flc = flc,
            zt = zt,
            rt = rt,
            xt = xt,
            isc3Phase = isc3Phase,
            isc1Phase = isc1Phase,
            status = status,
            z0 = z0_mOhm,
            vectorGroup = tx.vectorGroup,
            voltage = secondaryVoltage
        )
    }
}
