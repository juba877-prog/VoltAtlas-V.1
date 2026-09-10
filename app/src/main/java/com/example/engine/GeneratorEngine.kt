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
        totalConnectedLoad: Double,
        atsList: List<com.example.data.AtsEntity> = emptyList()
    ): GeneratorResult {
        val genId = settings.generatorId
        val atsFeedingThisGen = atsList.filter { it.emergencySourceId == genId }.map { it.atsId }.toSet()

        val generatorCircuits = circuits.filter { circ ->
            if (circ.assignedGeneratorId != null) {
                // Option (b) Direct Assignment Priority: direct generator assignment takes precedence for generator demand sizing
                circ.assignedGeneratorId == genId
            } else if (circ.assignedAtsId != null) {
                // If no direct generator assigned, route via ATS emergency source
                atsFeedingThisGen.contains(circ.assignedAtsId)
            } else {
                // Unassigned circuits with legacy/stale flags are attributed to NO instance (B1)
                false
            }
        }
        
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

        // Additional essential load
        val additionalKVA = settings.additionalEssentialLoadKW / genPf
        val totalEssentialKVA = essentialLoadKVA + additionalKVA
        val essentialLoadKVA_calc = totalEssentialKVA * settings.demandFactor / settings.diversityFactor
        
        val essentialLoadKW = Math.round(essentialLoadKVA_calc * genPf * 100.0) / 100.0
        
        val motorStartMultiplier = when (settings.motorStartMethod) {
            "DOL" -> 6.0
            "SD" -> 2.5
            "Soft-Start" -> 1.8
            "VFD" -> 1.2
            else -> 6.0
        }
        
        val motorPf = if (settings.motorPf > 0.0) settings.motorPf else 0.85
        val motorStartingKVA = if (settings.largestMotorKW > 0.0) {
            Math.round((settings.largestMotorKW / motorPf * motorStartMultiplier) * 100.0) / 100.0
        } else 0.0

        val stepLoadAtStart = Math.round((essentialLoadKVA_calc * settings.stepLoadPct / 100.0 + motorStartingKVA) * 100.0) / 100.0
        
        // ISO 8528 / IEC 60034 continuous safe loading rule:
        // Continuous running load must not exceed 80% of rated capacity (Safety Margin >= 1.25x).
        // Target Minimum Capacity: S_min = S_essential / 0.80 = S_essential * 1.25
        val rawMaxKVA = maxOf(essentialLoadKVA_calc, stepLoadAtStart)
        val requiredGenerator = Math.round((rawMaxKVA / 0.80) * 100.0) / 100.0
        
        val designCapacity = Math.round((requiredGenerator * settings.oversizingFactor) * 100.0) / 100.0

        val standardSizes = listOf(
            10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 75.0, 80.0, 100.0, 125.0, 150.0,
            160.0, 200.0, 250.0, 300.0, 350.0, 400.0, 500.0, 600.0, 625.0, 750.0, 800.0, 1000.0,
            1250.0, 1500.0, 2000.0, 2500.0
        )
        
        var recommended = standardSizes.firstOrNull { it >= designCapacity }
        
        var isManualOverride = false
        if (settings.enableManualOverride && settings.manualSizeKVA != null) {
            recommended = settings.manualSizeKVA
            isManualOverride = true
        }

        val messages = mutableListOf<String>()
        val loadingPct = if (recommended != null && recommended > 0.0) {
            Math.round((essentialLoadKVA_calc / recommended * 100.0) * 10.0) / 10.0
        } else null

        val status = if (isManualOverride) {
            "Manual Override Active"
        } else if (loadingPct != null) {
            when {
                loadingPct > 100.0 -> "Overloaded"
                loadingPct > 80.0 -> "High load"
                loadingPct >= 50.0 -> "Optimal load"
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

    fun calculateAll(
        generators: List<GeneratorEntity>,
        atsList: List<com.example.data.AtsEntity>,
        circuits: List<CircuitEntity>,
        totalConnectedLoad: Double
    ): Map<String, GeneratorResult> {
        if (generators.isEmpty()) return emptyMap()

        // Unassigned flagged circuits are surfaced ONCE at project level via the graph
        // builder's UNASSIGNED_GENERATOR_CIRCUIT validation issue — never per instance.
        return generators.associate { gen ->
            val res = calculate(gen, circuits, totalConnectedLoad, atsList)
            gen.generatorId to res
        }
    }
}
