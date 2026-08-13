package com.example.engine

import com.example.data.CircuitEntity
import com.example.data.UpsEntity
import kotlin.math.ceil

data class UpsResult(
    val status: String,
    val totalCriticalLoadKVA: Double,
    val totalCriticalLoadKW: Double,
    val upsApparentPowerReq: Double,
    val designUpsKVA: Double,
    val batteryLoadKW: Double?,
    val batteryEnergyReq: Double?,
    val correctedBatteryCapacity: Double?,
    val batteryAh: Double?,
    val recommendedSize: Double?,
    val loadingPct: Double?,
    val batteryBankRequired: String,
    val estimatedBatteryStrings: Int?,
    val autonomyAtFullLoad: String,
    val messages: List<String>
)

object UpsEngine {
    fun calculate(
        settings: UpsEntity,
        circuits: List<CircuitEntity>
    ): UpsResult {
        val upsId = settings.upsId
        val criticalCircuits = circuits.filter { circ ->
            if (circ.assignedUpsId != null) {
                circ.assignedUpsId == upsId
            } else {
                circ.upsBacked && upsId == "UPS-01"
            }
        }
        val totalCriticalLoadKVA = criticalCircuits.sumOf { it.loadKVA }

        if (totalCriticalLoadKVA == 0.0) {
            return UpsResult(
                status = "SELECT_LOADS",
                totalCriticalLoadKVA = 0.0,
                totalCriticalLoadKW = 0.0,
                upsApparentPowerReq = 0.0,
                designUpsKVA = 0.0,
                batteryLoadKW = null,
                batteryEnergyReq = null,
                correctedBatteryCapacity = null,
                batteryAh = null,
                recommendedSize = null,
                loadingPct = null,
                batteryBankRequired = "N/A",
                estimatedBatteryStrings = null,
                autonomyAtFullLoad = "N/A",
                messages = emptyList()
            )
        }

        // Project PF is roughly 0.8, but ideally we'd use a weighted average of circuits.
        // Assuming 0.8 to match transformer default, or derive it.
        val weightedPf = if (totalCriticalLoadKVA > 0) {
            criticalCircuits.sumOf { it.loadKVA * it.pf } / totalCriticalLoadKVA
        } else 0.8

        val totalCriticalLoadKW = totalCriticalLoadKVA * weightedPf
        val upsApparentPowerReq = if (settings.upsPf > 0) {
            maxOf(totalCriticalLoadKVA, totalCriticalLoadKW / settings.upsPf)
        } else {
            totalCriticalLoadKVA
        }
        val designUpsKVA = upsApparentPowerReq * (1 + settings.designMarginPct / 100.0)

        val batteryLoadKW = if (settings.upsEfficiency > 0) {
            (designUpsKVA * settings.upsPf) / (settings.upsEfficiency / 100.0)
        } else 0.0
        
        val batteryEnergyReq = batteryLoadKW * (settings.autonomyMinutes / 60.0)
        
        val correctedBatteryCapacity = if (settings.tempDeratingFactor > 0) {
            batteryEnergyReq * settings.agingFactor / settings.tempDeratingFactor
        } else 0.0
        
        val batteryAh = if (settings.systemVoltage > 0) {
            correctedBatteryCapacity * 1000.0 / settings.systemVoltage
        } else 0.0

        val standardUpsSizes = listOf(1.0, 2.0, 3.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 40.0, 60.0, 80.0, 100.0, 120.0, 160.0, 200.0, 250.0, 300.0, 400.0, 500.0, 600.0, 800.0)
        
        val d = designUpsKVA / 0.8
        var recommendedSize = standardUpsSizes.firstOrNull { it >= d }
        var isManualOverride = false
        if (settings.enableManualOverride && settings.manualSizeKVA != null) {
            recommendedSize = settings.manualSizeKVA
            isManualOverride = true
        }

        val messages = mutableListOf<String>()
        if (isManualOverride && recommendedSize != null && recommendedSize < designUpsKVA) {
            messages.add("Warning: Manual UPS size (${recommendedSize} kVA) is below design requirement (${designUpsKVA} kVA).")
        }

        val status: String
        val loadingPct: Double?
        if (isManualOverride) {
            loadingPct = if (recommendedSize != null && recommendedSize > 0) designUpsKVA / recommendedSize * 100.0 else null
            status = "Manual Override Active"
        } else if (recommendedSize != null) {
            loadingPct = designUpsKVA / recommendedSize * 100.0
            status = when {
                loadingPct <= 60.0 -> "Under-loaded (<60%)"
                loadingPct <= 80.0 -> "Optimal (60-80%)"
                loadingPct <= 90.0 -> "High loading (80-90%)"
                else -> "Overloaded (>90%)"
            }
        } else {
            status = "EXCEEDS 800 kVA (Recommend Parallel Modules)"
            loadingPct = null
        }

        val batteryBankRequired = "${String.format("%.2f", batteryAh)} Ah @ ${settings.systemVoltage}V"
        val estimatedBatteryStrings = ceil(batteryAh / 100.0).toInt()
        val autonomyAtFullLoad = "${settings.autonomyMinutes} min"

        return UpsResult(
            status = status,
            totalCriticalLoadKVA = totalCriticalLoadKVA,
            totalCriticalLoadKW = totalCriticalLoadKW,
            upsApparentPowerReq = upsApparentPowerReq,
            designUpsKVA = designUpsKVA,
            batteryLoadKW = batteryLoadKW,
            batteryEnergyReq = batteryEnergyReq,
            correctedBatteryCapacity = correctedBatteryCapacity,
            batteryAh = batteryAh,
            recommendedSize = recommendedSize,
            loadingPct = loadingPct,
            batteryBankRequired = batteryBankRequired,
            estimatedBatteryStrings = estimatedBatteryStrings,
            autonomyAtFullLoad = autonomyAtFullLoad,
            messages = messages
        )
    }

    fun calculateAll(
        upsList: List<UpsEntity>,
        circuits: List<CircuitEntity>
    ): Map<String, UpsResult> {
        if (upsList.isEmpty()) return emptyMap()
        return upsList.associate { ups ->
            ups.upsId to calculate(ups, circuits)
        }
    }
}
