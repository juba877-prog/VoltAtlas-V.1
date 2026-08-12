package com.example.engine

import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import kotlin.math.abs

data class GeneratorResult(
    val status: String = "",
    val essentialLoadKVA: Double = 0.0,
    val nonEssentialLoadKVA: Double = 0.0,
    val totalConnectedLoad: Double = 0.0,
    val essentialLoadKVA_calc: Double = 0.0,
    val essentialLoadKW: Double = 0.0,
    val motorStartingKVA: Double = 0.0,
    val stepLoadAtStart: Double = 0.0,
    val requiredGenerator: Double = 0.0,
    val designCapacity: Double = 0.0,
    val finalSize: Double? = null,
    val loadingPct: Double? = null,
    val messages: List<String> = emptyList()
)

object GeneratorEngine {
    fun calculate(
        settings: GeneratorEntity,
        circuits: List<CircuitEntity>,
        totalConnectedLoad: Double
    ): GeneratorResult {
        val generatorCircuits = circuits.filter { it.generatorIncluded }
        
        if (generatorCircuits.isEmpty() && settings.additionalEssentialLoadKW == 0.0) {
            val statusStr = if (circuits.isEmpty()) "SELECT_LOADS" else "No Generator Required"
            return GeneratorResult(
                status = statusStr,
                essentialLoadKVA = 0.0,
                nonEssentialLoadKVA = totalConnectedLoad,
                totalConnectedLoad = totalConnectedLoad,
                essentialLoadKVA_calc = 0.0,
                essentialLoadKW = 0.0,
                motorStartingKVA = 0.0,
                stepLoadAtStart = 0.0,
                requiredGenerator = 0.0,
                designCapacity = 0.0,
                finalSize = null,
                loadingPct = null,
                messages = if (circuits.isEmpty()) listOf("No loads selected. Choose loads from the engine tab to size it.") else emptyList()
            )
        }

        val essentialLoadKVA = generatorCircuits.sumOf { it.loadKVA }
        val nonEssentialLoadKVA = maxOf(0.0, totalConnectedLoad - essentialLoadKVA)

        if (essentialLoadKVA == 0.0 && settings.additionalEssentialLoadKW == 0.0) {
            return GeneratorResult(
                status = "No Generator Required",
                essentialLoadKVA = essentialLoadKVA,
                nonEssentialLoadKVA = nonEssentialLoadKVA,
                totalConnectedLoad = totalConnectedLoad,
                essentialLoadKVA_calc = 0.0,
                essentialLoadKW = 0.0,
                motorStartingKVA = 0.0,
                stepLoadAtStart = 0.0,
                requiredGenerator = 0.0,
                designCapacity = 0.0,
                finalSize = null,
                loadingPct = null,
                messages = emptyList()
            )
        }

        val genPf = if (settings.generatorPf > 0.0) settings.generatorPf else 0.8

        // B22
        val additionalKVA = settings.additionalEssentialLoadKW / genPf
        val totalEssentialKVA = essentialLoadKVA + additionalKVA
        val essentialLoadKVA_calc = totalEssentialKVA * settings.demandFactor / settings.diversityFactor
        
        // B23
        val essentialLoadKW = Math.round(essentialLoadKVA_calc * genPf * 100.0) / 100.0
        
        val motorStartMultiplier = when (settings.motorStartMethod) {
            "DOL" -> 6.0
            "SD" -> 2.5
            "Soft-Start" -> 1.8
            "VFD" -> 1.2
            else -> 6.0
        }
        
        // B24
        val motorPf = if (settings.motorPf > 0.0) settings.motorPf else 0.85
        val motorStartingKVA = if (settings.largestMotorKW > 0.0) {
            Math.round((settings.largestMotorKW / motorPf * motorStartMultiplier) * 100.0) / 100.0
        } else 0.0

        // B25
        val stepLoadAtStart = Math.round((essentialLoadKVA_calc * settings.stepLoadPct / 100.0 + motorStartingKVA) * 100.0) / 100.0
        
        // B26: required_kVA = MAX(essential_kVA, stepLoad_kVA) / GenPF
        val rawMaxKVA = maxOf(essentialLoadKVA_calc, stepLoadAtStart)
        val requiredGenerator = Math.round((rawMaxKVA / genPf) * 100.0) / 100.0
        
        // B27: design_kVA = required_kVA * OversizingFactor
        val designCapacity = Math.round((requiredGenerator * settings.oversizingFactor) * 100.0) / 100.0

        val standardSizes = listOf(
            10.0, 15.0, 20.0, 30.0, 40.0, 50.0, 60.0, 75.0, 100.0, 125.0, 150.0,
            200.0, 250.0, 300.0, 350.0, 400.0, 500.0, 600.0, 750.0, 1000.0, 1250.0,
            1500.0, 2000.0, 2500.0
        )
        
        var recommended = standardSizes.firstOrNull { it >= designCapacity }
        
        var isManualOverride = false
        if (settings.enableManualOverride && settings.manualSizeKVA != null) {
            recommended = settings.manualSizeKVA
            isManualOverride = true
        }

        val messages = mutableListOf<String>()
        val loadingPct = if (recommended != null && recommended > 0.0) {
            Math.round((designCapacity / recommended * 100.0) * 10.0) / 10.0
        } else null

        val status = if (isManualOverride) {
            "Manual Override Active"
        } else if (loadingPct != null) {
            when {
                loadingPct >= 80.0 -> "High load"
                loadingPct >= 60.0 -> "Optimal load"
                else -> "Under-loaded — consider a smaller unit"
            }
        } else {
            "Error: Could not determine size"
        }

        if (isManualOverride && recommended != null && recommended < designCapacity) {
            messages.add("Warning: Manual Generator size (${recommended} kVA) is below design requirement (${designCapacity} kVA).")
        }

        if (recommended != null && recommended > 0.0 && motorStartingKVA / recommended > 0.3) {
            messages.add("Warning: Motor starting may cause >15% voltage dip.")
        }

        if (recommended != null && recommended > 0.0 && settings.largestMotorKW > 0.0) {
            if ((settings.largestMotorKW / motorPf) / recommended > 0.25) {
                messages.add("Warning: Verify step-load capability for large motor.")
            }
        }

        return GeneratorResult(
            status = status,
            essentialLoadKVA = essentialLoadKVA,
            nonEssentialLoadKVA = nonEssentialLoadKVA,
            totalConnectedLoad = totalConnectedLoad,
            essentialLoadKVA_calc = essentialLoadKVA_calc,
            essentialLoadKW = essentialLoadKW,
            motorStartingKVA = motorStartingKVA,
            stepLoadAtStart = stepLoadAtStart,
            requiredGenerator = requiredGenerator,
            designCapacity = designCapacity,
            finalSize = recommended,
            loadingPct = loadingPct,
            messages = messages
        )
    }
}
