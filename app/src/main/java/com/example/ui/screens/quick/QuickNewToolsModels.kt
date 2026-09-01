package com.example.ui.screens.quick

import com.example.data.BreakerEntry
import com.example.data.Catalog
import com.example.data.ConduitCatalogEntry
import kotlin.math.*

// =============================================================================
// 1. EARTHING & GROUNDING CALCULATOR (IEC 60364-5-54, BS 7430, IEC 60364-4-41)
// =============================================================================

enum class EarthSoilType(val defaultResistivity: Double, val label: String) {
    MARSHY_WET(30.0, "Marshy / Wet Ground (30 Ω·m)"),
    CLAY_LOAM(50.0, "Clay & Arable Soil (50 Ω·m)"),
    SAND_GRAVEL(250.0, "Sand / Gravel (250 Ω·m)"),
    ROCKY_GRANITE(1000.0, "Rocky / Granite Soil (1000 Ω·m)"),
    CUSTOM(100.0, "Custom Soil Resistivity")
}

enum class EarthConductorMaterial(val kFactor: Double, val label: String) {
    COPPER_BARE(159.0, "Bare Copper in Soil (k = 159)"),
    COPPER_XLPE(176.0, "Copper / XLPE (90°C→250°C, k = 176)"),
    COPPER_PVC(143.0, "Copper / PVC (70°C→160°C, k = 143)"),
    ALUMINIUM_XLPE(118.0, "Aluminium / XLPE (k = 118)"),
    ALUMINIUM_PVC(95.0, "Aluminium / PVC (k = 95)"),
    STEEL_GALVANIZED(58.0, "Galvanized Steel (k = 58)")
}

data class EarthingQuickInput(
    val soilResistivity_ohmM: Double = 50.0,
    val targetResistance_ohm: Double = 2.0,
    val faultCurrent_kA: Double = 16.0,
    val clearingTime_s: Double = 0.4,
    val kFactor: Double = 159.0,
    val rodLength_m: Double = 3.0,
    val rodDiameter_mm: Double = 17.0,
    val rodSpacing_m: Double = 5.0,
    val systemVoltage_V: Double = 400.0,
    val u0_V: Double = 230.0,
    val mainBreakerRating_A: Double = 630.0,
    val mainBreakerCurve: String = "C"
)

data class EarthingQuickResult(
    val singleRodResistance_ohm: Double,
    val requiredRods: Int,
    val groupEfficiency: Double,
    val achievedResistance_ohm: Double,
    val targetResistance_ohm: Double,
    val exactConductorMm2: Double,
    val standardConductorMm2: Double,
    val zsCalculated_ohm: Double,
    val zsMaxAllowed_ohm: Double,
    val isResistanceOk: Boolean,
    val isZsLoopOk: Boolean,
    val formulaDwight: String,
    val substitutionDwight: String,
    val formulaAdiabatic: String,
    val substitutionAdiabatic: String
)

object QuickEarthingEngine {
    val STANDARD_EARTH_SIZES = listOf(16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0)

    fun calculate(input: EarthingQuickInput): EarthingQuickResult {
        val rho = max(input.soilResistivity_ohmM, 0.1)
        val L = max(input.rodLength_m, 0.5)
        val dMm = max(input.rodDiameter_mm, 1.0)
        val dMeters = dMm / 1000.0 // CRITICAL: mm to meters conversion
        val targetR = max(input.targetResistance_ohm, 0.1)
        val faultKa = max(input.faultCurrent_kA, 0.0)
        val t = max(input.clearingTime_s, 0.01)
        val k = max(input.kFactor, 1.0)

        // 1. Dwight formula for single vertical rod
        val r1Raw = (rho / (2.0 * PI * L)) * (ln(8.0 * L / dMeters) - 1.0)
        val r1 = max(0.01, round(r1Raw * 100.0) / 100.0)

        // 2. Multiple rods with BS 7430 spacing efficiency
        val spacingRatio = input.rodSpacing_m / L
        var nRods = 1
        var achievedR = r1
        var eff = 1.0

        while (nRods < 50) {
            eff = when {
                spacingRatio >= 2.0 -> (1.0 - 0.05 * (nRods - 1)).coerceAtLeast(0.70)
                spacingRatio >= 1.5 -> (0.95 - 0.06 * (nRods - 1)).coerceAtLeast(0.65)
                else -> (0.90 - 0.07 * (nRods - 1)).coerceAtLeast(0.60)
            }
            achievedR = r1 / (nRods * eff)
            if (achievedR <= targetR) break
            nRods++
        }
        val finalAchievedR = round(achievedR * 100.0) / 100.0

        // 3. Adiabatic conductor sizing S = (Isc * sqrt(t)) / k
        val faultAmps = faultKa * 1000.0
        val exactS = (faultAmps * sqrt(t)) / k
        val standardS = STANDARD_EARTH_SIZES.firstOrNull { it >= exactS } ?: STANDARD_EARTH_SIZES.last()

        // 4. Loop impedance check Zs = U0 / Isc vs Zs_max = (0.95 * U0) / Ia
        val u0 = if (input.u0_V > 0) input.u0_V else 230.0
        val zs = if (faultAmps > 0) u0 / faultAmps else 0.0
        val curveMultiplier = when (input.mainBreakerCurve.uppercase()) {
            "B" -> 5.0
            "D" -> 20.0
            else -> 10.0 // Curve C
        }
        val ia = input.mainBreakerRating_A * curveMultiplier
        val zsMax = if (ia > 0) (0.95 * u0) / ia else 0.0

        return EarthingQuickResult(
            singleRodResistance_ohm = r1,
            requiredRods = nRods,
            groupEfficiency = round(eff * 100.0) / 100.0,
            achievedResistance_ohm = finalAchievedR,
            targetResistance_ohm = targetR,
            exactConductorMm2 = round(exactS * 100.0) / 100.0,
            standardConductorMm2 = standardS,
            zsCalculated_ohm = round(zs * 1000.0) / 1000.0,
            zsMaxAllowed_ohm = round(zsMax * 1000.0) / 1000.0,
            isResistanceOk = finalAchievedR <= targetR,
            isZsLoopOk = zs <= zsMax,
            formulaDwight = "R₁ = (ρ / (2·π·L)) · [ln(8·L / d) − 1]",
            substitutionDwight = "R₁ = (${String.format(java.util.Locale.US, "%.1f", rho)} / (2·π·${String.format(java.util.Locale.US, "%.2f", L)})) · [ln(8·${String.format(java.util.Locale.US, "%.2f", L)} / ${String.format(java.util.Locale.US, "%.4f", dMeters)}) − 1] = ${String.format(java.util.Locale.US, "%.2f", r1)} Ω",
            formulaAdiabatic = "S = (I_sc · √t) / k  [IEC 60364-5-54]",
            substitutionAdiabatic = "S = (${String.format(java.util.Locale.US, "%.0f", faultAmps)} A · √${String.format(java.util.Locale.US, "%.2f", t)} s) / ${String.format(java.util.Locale.US, "%.0f", k)} = ${String.format(java.util.Locale.US, "%.2f", exactS)} mm² → Selected Standard: ${standardS.toInt()} mm²"
        )
    }
}

// =============================================================================
// 2. CONDUIT FILL & TRADE SIZING (IEC 61386, NEC Chapter 9 Table 1)
// =============================================================================

enum class ConduitTypeOption(val catalogName: String, val label: String) {
    RIGID_PVC("Rigid PVC", "Rigid Heavy PVC (IEC 61386)"),
    EMT_STEEL("EMT Steel", "EMT Steel (ANSI C80.3)"),
    GI_STEEL("GI Steel (RMC)", "GI Steel / RMC (BS 4568 Class 4)")
}

data class ConduitFillQuickInput(
    val conduitType: ConduitTypeOption = ConduitTypeOption.RIGID_PVC,
    val mainCableCount: Int = 4,
    val mainCableOD_mm: Double = 18.5,
    val cpcCableCount: Int = 1,
    val cpcCableOD_mm: Double = 9.0,
    val customFillLimitPct: Double? = null // null = use standard NEC 1-wire: 53%, 2-wire: 31%, 3+ wire: 40%
)

data class ConduitFillQuickResult(
    val recommendedTradeSize: String,
    val internalDiameterMm: Double,
    val totalInternalAreaMm2: Double,
    val totalCableAreaMm2: Double,
    val actualFillPercent: Double,
    val allowedFillPercent: Double,
    val remainingSpareAreaMm2: Double,
    val spareCapacityPercent: Double,
    val isCompliant: Boolean,
    val formulaArea: String,
    val substitutionArea: String
)

object QuickConduitFillEngine {
    // Built-in standard metric and EMT sizes with exact internal diameters
    val FALLBACK_CONDUITS = listOf(
        ConduitCatalogEntry("PVC_20", "Rigid PVC", "20 mm", 15.8, Math.PI * 15.8 * 15.8 / 4.0),
        ConduitCatalogEntry("PVC_25", "Rigid PVC", "25 mm", 21.0, Math.PI * 21.0 * 21.0 / 4.0),
        ConduitCatalogEntry("PVC_32", "Rigid PVC", "32 mm", 27.8, Math.PI * 27.8 * 27.8 / 4.0),
        ConduitCatalogEntry("PVC_40", "Rigid PVC", "40 mm", 35.1, Math.PI * 35.1 * 35.1 / 4.0),
        ConduitCatalogEntry("PVC_50", "Rigid PVC", "50 mm", 44.3, Math.PI * 44.3 * 44.3 / 4.0),
        ConduitCatalogEntry("PVC_63", "Rigid PVC", "63 mm", 56.0, Math.PI * 56.0 * 56.0 / 4.0),
        ConduitCatalogEntry("PVC_75", "Rigid PVC", "75 mm", 67.8, Math.PI * 67.8 * 67.8 / 4.0),
        ConduitCatalogEntry("PVC_90", "Rigid PVC", "90 mm", 81.4, Math.PI * 81.4 * 81.4 / 4.0),
        ConduitCatalogEntry("PVC_110", "Rigid PVC", "110 mm", 100.0, Math.PI * 100.0 * 100.0 / 4.0),
        ConduitCatalogEntry("EMT_20", "EMT Steel", "20 mm (3/4 in)", 20.9, Math.PI * 20.9 * 20.9 / 4.0),
        ConduitCatalogEntry("EMT_25", "EMT Steel", "25 mm (1 in)", 26.6, Math.PI * 26.6 * 26.6 / 4.0),
        ConduitCatalogEntry("EMT_32", "EMT Steel", "32 mm (1-1/4 in)", 35.1, Math.PI * 35.1 * 35.1 / 4.0),
        ConduitCatalogEntry("EMT_40", "EMT Steel", "40 mm (1-1/2 in)", 40.9, Math.PI * 40.9 * 40.9 / 4.0),
        ConduitCatalogEntry("EMT_50", "EMT Steel", "50 mm (2 in)", 52.5, Math.PI * 52.5 * 52.5 / 4.0),
        ConduitCatalogEntry("GI_20", "GI Steel (RMC)", "20 mm", 17.2, Math.PI * 17.2 * 17.2 / 4.0),
        ConduitCatalogEntry("GI_25", "GI Steel (RMC)", "25 mm", 22.0, Math.PI * 22.0 * 22.0 / 4.0),
        ConduitCatalogEntry("GI_32", "GI Steel (RMC)", "32 mm", 28.8, Math.PI * 28.8 * 28.8 / 4.0),
        ConduitCatalogEntry("GI_40", "GI Steel (RMC)", "40 mm", 36.6, Math.PI * 36.6 * 36.6 / 4.0),
        ConduitCatalogEntry("GI_50", "GI Steel (RMC)", "50 mm", 48.0, Math.PI * 48.0 * 48.0 / 4.0)
    )

    fun calculate(input: ConduitFillQuickInput): ConduitFillQuickResult {
        val nMain = max(input.mainCableCount, 0)
        val odMain = max(input.mainCableOD_mm, 0.0)
        val nCpc = max(input.cpcCableCount, 0)
        val odCpc = max(input.cpcCableOD_mm, 0.0)

        val totalCables = nMain + nCpc
        val mainArea = nMain * (PI * odMain * odMain / 4.0)
        val cpcArea = nCpc * (PI * odCpc * odCpc / 4.0)
        val totalCableArea = mainArea + cpcArea

        // Allowed fill threshold per NEC Chapter 9 Table 1
        val allowedFillPct = input.customFillLimitPct ?: when (totalCables) {
            1 -> 53.0
            2 -> 31.0
            else -> 40.0
        }

        val requiredInternalArea = if (allowedFillPct > 0) (totalCableArea / (allowedFillPct / 100.0)) else totalCableArea

        // Search catalog conduits
        val activeCatalog = if (Catalog.conduits.isNotEmpty()) Catalog.conduits else FALLBACK_CONDUITS
        val matchingConduits = activeCatalog.filter {
            it.conduitType.equals(input.conduitType.catalogName, ignoreCase = true)
        }.sortedBy { it.internalDiameterMm }

        val candidate = matchingConduits.firstOrNull {
            it.areaTotalMm2 >= requiredInternalArea
        } ?: matchingConduits.lastOrNull()

        val chosenTradeSize = candidate?.tradeSize ?: "N/A (Exceeds sizes)"
        val internalDiameter = candidate?.internalDiameterMm ?: 0.0
        val totalInternalArea = candidate?.areaTotalMm2 ?: 0.0

        val actualFillPct = if (totalInternalArea > 0) (totalCableArea / totalInternalArea) * 100.0 else 0.0
        val remainingSpare = max(0.0, totalInternalArea - totalCableArea)
        val sparePct = if (totalInternalArea > 0) (remainingSpare / totalInternalArea) * 100.0 else 0.0
        val isCompliant = actualFillPct <= allowedFillPct && totalInternalArea >= requiredInternalArea

        return ConduitFillQuickResult(
            recommendedTradeSize = chosenTradeSize,
            internalDiameterMm = round(internalDiameter * 10.0) / 10.0,
            totalInternalAreaMm2 = round(totalInternalArea * 10.0) / 10.0,
            totalCableAreaMm2 = round(totalCableArea * 10.0) / 10.0,
            actualFillPercent = round(actualFillPct * 10.0) / 10.0,
            allowedFillPercent = allowedFillPct,
            remainingSpareAreaMm2 = round(remainingSpare * 10.0) / 10.0,
            spareCapacityPercent = round(sparePct * 10.0) / 10.0,
            isCompliant = isCompliant,
            formulaArea = "A_cables = Σ (π · OD_i² / 4)  |  Fill% = (A_cables / A_internal) · 100",
            substitutionArea = "A_cables = [$nMain × (π·${String.format(java.util.Locale.US, "%.1f", odMain)}²/4)] + [$nCpc × (π·${String.format(java.util.Locale.US, "%.1f", odCpc)}²/4)] = ${String.format(java.util.Locale.US, "%.1f", totalCableArea)} mm²  (Conduit ID: ${String.format(java.util.Locale.US, "%.1f", internalDiameter)} mm, Fill: ${String.format(java.util.Locale.US, "%.1f", actualFillPct)}% vs Max ${String.format(java.util.Locale.US, "%.0f", allowedFillPct)}%)"
        )
    }
}

// =============================================================================
// 3. CIRCUIT BREAKER SELECTION & SELECTIVITY (IEC 60947-2, IEC 60898-1)
// =============================================================================

data class BreakerSelectQuickInput(
    val loadCurrent_A: Double = 45.0,
    val prospectiveFault_kA: Double = 16.0,
    val poles: Int = 3,
    val deviceTypeFilter: String = "AUTO", // AUTO, MCB, MCCB, ACB
    val upstreamRating_A: Double? = 100.0
)

data class BreakerSelectQuickResult(
    val selectedBreaker: BreakerEntry?,
    val continuousRating_A: Double,
    val breakingCapacityIcu_kA: Double,
    val serviceCapacityIcs_kA: Double,
    val makingCapacityIcm_kA: Double,
    val breakingMarginPct: Double,
    val frameDescription: String,
    val tripUnit: String,
    val orderingCode: String,
    val thermalSelectivityRatio: Double?,
    val magneticSelectivityRatio: Double?,
    val isThermalSelectivityOk: Boolean,
    val isMagneticSelectivityOk: Boolean,
    val status: String
)

object QuickBreakerSelectEngine {
    val FALLBACK_BREAKERS = listOf(
        BreakerEntry(mfg = "Schneider", type = "MCB", series = "Acti9 iC60N", frame = "63A Frame", version = "Curve C", rating_A = 16.0, poles = 3, icu_kA = 10.0, ics_kA = 7.5, icm_kA = 17.0, tripUnit = "Thermal-Magnetic", orderingCode = "A9F74316"),
        BreakerEntry(mfg = "Schneider", type = "MCB", series = "Acti9 iC60N", frame = "63A Frame", version = "Curve C", rating_A = 32.0, poles = 3, icu_kA = 10.0, ics_kA = 7.5, icm_kA = 17.0, tripUnit = "Thermal-Magnetic", orderingCode = "A9F74332"),
        BreakerEntry(mfg = "Schneider", type = "MCB", series = "Acti9 iC60N", frame = "63A Frame", version = "Curve C", rating_A = 63.0, poles = 3, icu_kA = 10.0, ics_kA = 7.5, icm_kA = 17.0, tripUnit = "Thermal-Magnetic", orderingCode = "A9F74363"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX100F", frame = "NSX100", version = "TM-D", rating_A = 50.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Thermal-Magnetic", orderingCode = "LV429630"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX100F", frame = "NSX100", version = "TM-D", rating_A = 63.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Thermal-Magnetic", orderingCode = "LV429631"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX100F", frame = "NSX100", version = "TM-D", rating_A = 100.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Thermal-Magnetic", orderingCode = "LV429632"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX160F", frame = "NSX160", version = "TM-D", rating_A = 160.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Thermal-Magnetic", orderingCode = "LV430630"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX250F", frame = "NSX250", version = "TM-D", rating_A = 250.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Thermal-Magnetic", orderingCode = "LV431630"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX400F", frame = "NSX400", version = "Micrologic 2.3", rating_A = 400.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Electronic", orderingCode = "LV432676"),
        BreakerEntry(mfg = "Schneider", type = "MCCB", series = "ComPacT NSX630F", frame = "NSX630", version = "Micrologic 2.3", rating_A = 630.0, poles = 3, icu_kA = 36.0, ics_kA = 36.0, icm_kA = 75.6, tripUnit = "Electronic", orderingCode = "LV432876"),
        BreakerEntry(mfg = "Schneider", type = "ACB", series = "MasterPact MTZ1", frame = "MTZ1 08", version = "Micrologic 5.0X", rating_A = 800.0, poles = 3, icu_kA = 42.0, ics_kA = 42.0, icm_kA = 88.2, tripUnit = "MicroLogic", orderingCode = "LV847110"),
        BreakerEntry(mfg = "Schneider", type = "ACB", series = "MasterPact MTZ2", frame = "MTZ2 16", version = "Micrologic 5.0X", rating_A = 1600.0, poles = 3, icu_kA = 65.0, ics_kA = 65.0, icm_kA = 143.0, tripUnit = "MicroLogic", orderingCode = "LV847210"),
        BreakerEntry(mfg = "Schneider", type = "ACB", series = "MasterPact MTZ2", frame = "MTZ2 25", version = "Micrologic 5.0X", rating_A = 2500.0, poles = 3, icu_kA = 65.0, ics_kA = 65.0, icm_kA = 143.0, tripUnit = "MicroLogic", orderingCode = "LV847310")
    )

    fun calculate(input: BreakerSelectQuickInput): BreakerSelectQuickResult {
        val ib = max(input.loadCurrent_A, 0.1)
        val isc = max(input.prospectiveFault_kA, 0.1)
        val activeCatalog = if (Catalog.breakers.isNotEmpty()) Catalog.breakers else FALLBACK_BREAKERS

        val filtered = activeCatalog.filter { b ->
            (input.poles == 0 || b.poles == input.poles || b.poles == 3) &&
            (input.deviceTypeFilter == "AUTO" || b.type.equals(input.deviceTypeFilter, ignoreCase = true)) &&
            b.ratingA >= ib &&
            b.icuKa >= isc
        }.sortedWith(compareBy({ it.ratingA }, { it.icuKa }))

        val chosen = filtered.firstOrNull()

        val inDown = chosen?.ratingA ?: ib
        val icu = chosen?.icuKa ?: 36.0
        val ics = chosen?.ics_kA ?: icu
        val icm = chosen?.icm_kA ?: (icu * 2.1)
        val margin = if (isc > 0) ((icu - isc) / isc) * 100.0 else 0.0

        val inUp = input.upstreamRating_A
        val thermalRatio = if (inUp != null && inDown > 0) inUp / inDown else null
        val isThermalOk = thermalRatio == null || thermalRatio >= 1.6
        val isMagOk = thermalRatio == null || thermalRatio >= 2.0

        return BreakerSelectQuickResult(
            selectedBreaker = chosen,
            continuousRating_A = inDown,
            breakingCapacityIcu_kA = icu,
            serviceCapacityIcs_kA = ics,
            makingCapacityIcm_kA = icm,
            breakingMarginPct = round(margin * 10.0) / 10.0,
            frameDescription = chosen?.let { "${it.mfg} ${it.series} (${it.frame})" } ?: "Generic Breaker",
            tripUnit = chosen?.tripUnit ?: "Thermal-Magnetic",
            orderingCode = chosen?.orderingCode ?: "N/A",
            thermalSelectivityRatio = thermalRatio?.let { round(it * 100.0) / 100.0 },
            magneticSelectivityRatio = thermalRatio?.let { round(it * 100.0) / 100.0 },
            isThermalSelectivityOk = isThermalOk,
            isMagneticSelectivityOk = isMagOk,
            status = if (chosen != null) "PASS" else "NO_EXACT_CATALOG_MATCH"
        )
    }
}

// =============================================================================
// 4. GENERATOR QUICK SIZING & VOLTAGE DIP (ISO 8528, IEC 60034-1)
// =============================================================================

enum class MotorStartMethod(val multiplier: Double, val label: String) {
    DOL(6.0, "Direct-On-Line (DOL, 6.0×)"),
    STAR_DELTA(2.5, "Star-Delta (Y-Δ, 2.5×)"),
    SOFT_STARTER(1.8, "Electronic Soft Starter (1.8×)"),
    VFD(1.2, "Variable Frequency Drive (VFD, 1.2×)")
}

data class GeneratorQuickInput(
    val continuousLoadKW: Double = 120.0,
    val powerFactor: Double = 0.80,
    val demandFactor: Double = 1.0,
    val largestMotorKW: Double = 30.0,
    val motorStartMethod: MotorStartMethod = MotorStartMethod.DOL,
    val maxPermittedVoltageDipPct: Double = 15.0,
    val subtransientReactanceXdPct: Double = 15.0
)

data class GeneratorQuickResult(
    val recommendedSizeKVA: Double,
    val continuousLoadKVA: Double,
    val continuousLoadKW: Double,
    val motorStartingKVA: Double,
    val stepLoadAtStartKVA: Double,
    val continuousLoadingPercent: Double,
    val estimatedVoltageDipPercent: Double,
    val isVoltageDipOk: Boolean,
    val isPrimeLoadingOk: Boolean,
    val formulaSizing: String,
    val substitutionSizing: String
)

object QuickGeneratorEngine {
    val STANDARD_GEN_SIZES = listOf(
        20.0, 30.0, 40.0, 50.0, 60.0, 80.0, 100.0, 125.0, 150.0, 160.0, 200.0,
        250.0, 300.0, 350.0, 400.0, 500.0, 600.0, 625.0, 750.0, 800.0, 1000.0,
        1250.0, 1500.0, 2000.0, 2500.0
    )

    fun calculate(input: GeneratorQuickInput): GeneratorQuickResult {
        val pf = max(input.powerFactor, 0.1)
        val pKw = max(input.continuousLoadKW, 0.0)
        val sKva = (pKw / pf) * input.demandFactor

        // Motor starting kVA
        val motorPf = 0.85
        val motorEff = 0.90
        val motorStartingKVA = if (input.largestMotorKW > 0) {
            (input.largestMotorKW / (motorPf * motorEff)) * input.motorStartMethod.multiplier
        } else 0.0

        val stepLoad = sKva * 0.50 + motorStartingKVA
        // Prime continuous loading constraint <= 80%
        val requiredKva = max(sKva / 0.80, stepLoad / 1.50)

        val selectedKva = STANDARD_GEN_SIZES.firstOrNull { it >= requiredKva } ?: STANDARD_GEN_SIZES.last()
        val loadingPct = if (selectedKva > 0) (sKva / selectedKva) * 100.0 else 0.0

        // Transient voltage dip estimation: ΔV = (kVA_start / S_gen) * X"d(%)
        val dipPct = if (selectedKva > 0) (motorStartingKVA / selectedKva) * input.subtransientReactanceXdPct else 0.0

        return GeneratorQuickResult(
            recommendedSizeKVA = selectedKva,
            continuousLoadKVA = round(sKva * 10.0) / 10.0,
            continuousLoadKW = pKw,
            motorStartingKVA = round(motorStartingKVA * 10.0) / 10.0,
            stepLoadAtStartKVA = round(stepLoad * 10.0) / 10.0,
            continuousLoadingPercent = round(loadingPct * 10.0) / 10.0,
            estimatedVoltageDipPercent = round(dipPct * 10.0) / 10.0,
            isVoltageDipOk = dipPct <= input.maxPermittedVoltageDipPct,
            isPrimeLoadingOk = loadingPct <= 80.0,
            formulaSizing = "S_gen ≥ max(S_run / 0.80, S_step)  |  ΔV_dip% ≈ (kVA_start / S_gen) · X\"d%",
            substitutionSizing = "S_req = max(${String.format(java.util.Locale.US, "%.1f", sKva)}/0.80, ${String.format(java.util.Locale.US, "%.1f", stepLoad)}) = ${String.format(java.util.Locale.US, "%.1f", requiredKva)} kVA → Selected: ${selectedKva.toInt()} kVA (Dip: ${String.format(java.util.Locale.US, "%.1f", dipPct)}% vs Limit ${String.format(java.util.Locale.US, "%.1f", input.maxPermittedVoltageDipPct)}%)"
        )
    }
}

// =============================================================================
// 5. UPS & BATTERY SIZING (IEC 62040-3, IEEE 485)
// =============================================================================

data class UpsQuickInput(
    val criticalLoadKVA: Double = 60.0,
    val powerFactor: Double = 0.80,
    val autonomyMinutes: Double = 30.0,
    val inverterEfficiencyPct: Double = 92.0,
    val batteryVoltageDC: Double = 384.0,
    val depthOfDischarge: Double = 0.80,
    val agingFactor: Double = 1.25,
    val tempDeratingFactor: Double = 1.00
)

data class UpsQuickResult(
    val recommendedUpsSizeKVA: Double,
    val criticalLoadKVA: Double,
    val criticalLoadKW: Double,
    val inverterDcLoadKW: Double,
    val requiredEnergyKWh: Double,
    val requiredCapacityAh: Double,
    val batteryStringsEstimate100Ah: Int,
    val upsLoadingPercent: Double,
    val formulaUps: String,
    val substitutionUps: String
)

object QuickUpsEngine {
    val STANDARD_UPS_SIZES = listOf(
        1.0, 2.0, 3.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 40.0, 60.0, 80.0,
        100.0, 120.0, 160.0, 200.0, 250.0, 300.0, 400.0, 500.0, 600.0, 800.0
    )

    fun calculate(input: UpsQuickInput): UpsQuickResult {
        val sLoad = max(input.criticalLoadKVA, 0.1)
        val pf = max(input.powerFactor, 0.1)
        val pKw = sLoad * pf

        // Sizing with 20% design headroom (loading <= 80%)
        val sDesign = sLoad / 0.80
        val selectedUpsKva = STANDARD_UPS_SIZES.firstOrNull { it >= sDesign } ?: STANDARD_UPS_SIZES.last()
        val loadingPct = (sLoad / selectedUpsKva) * 100.0

        val eff = max(input.inverterEfficiencyPct, 10.0) / 100.0
        val pDc = pKw / eff

        val dod = max(input.depthOfDischarge, 0.1)
        val aging = max(input.agingFactor, 1.0)
        val temp = max(input.tempDeratingFactor, 0.5)

        // Stored energy kWh = P_dc * (t/60) * (Aging / (Temp * DoD))
        val eKwh = pDc * (input.autonomyMinutes / 60.0) * (aging / (temp * dod))
        val vDc = max(input.batteryVoltageDC, 12.0)
        val cAh = (eKwh * 1000.0) / vDc
        val strings = ceil(cAh / 100.0).toInt()

        return UpsQuickResult(
            recommendedUpsSizeKVA = selectedUpsKva,
            criticalLoadKVA = sLoad,
            criticalLoadKW = round(pKw * 10.0) / 10.0,
            inverterDcLoadKW = round(pDc * 10.0) / 10.0,
            requiredEnergyKWh = round(eKwh * 10.0) / 10.0,
            requiredCapacityAh = round(cAh * 10.0) / 10.0,
            batteryStringsEstimate100Ah = max(1, strings),
            upsLoadingPercent = round(loadingPct * 10.0) / 10.0,
            formulaUps = "S_UPS ≥ S_load / 0.80  |  C_Ah = (P_DC · t_h · Aging) / (V_DC · DoD · Temp)",
            substitutionUps = "P_DC = ${String.format(java.util.Locale.US, "%.1f", pKw)} kW / ${String.format(java.util.Locale.US, "%.2f", eff)} = ${String.format(java.util.Locale.US, "%.1f", pDc)} kW → Energy = ${String.format(java.util.Locale.US, "%.1f", eKwh)} kWh → Capacity @ ${vDc.toInt()}V = ${String.format(java.util.Locale.US, "%.1f", cAh)} Ah (${strings}x 100Ah strings)"
        )
    }
}

// =============================================================================
// 6. ATS SIZING & SHORT-TIME WITHSTAND (IEC 60947-6-1, NFPA 110 Type 10)
// =============================================================================

data class AtsQuickInput(
    val mainsCurrent_A: Double = 380.0,
    val generatorFLC_A: Double = 400.0,
    val prospectiveFault_kA: Double = 25.0,
    val utilizationCategory: String = "AC-33B",
    val mainsFailureDetectDelay_s: Double = 2.0,
    val generatorStartDelay_s: Double = 3.0,
    val generatorWarmup_s: Double = 4.0
)

data class AtsQuickResult(
    val recommendedRating_A: Double,
    val governingCurrent_A: Double,
    val shortTimeWithstandIcw_kA: Double,
    val peakMakingCapacityIcm_kA: Double,
    val recommendedConstruction: String,
    val totalTransferTimeToGen_s: Double,
    val isNfpa110Compliant: Boolean,
    val atsLoadingPercent: Double,
    val formulaAts: String,
    val substitutionAts: String
)

object QuickAtsEngine {
    val STANDARD_ATS_SIZES = listOf(
        100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 630.0, 800.0,
        1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3200.0, 4000.0
    )

    fun calculate(input: AtsQuickInput): AtsQuickResult {
        val govCurrent = max(input.mainsCurrent_A, input.generatorFLC_A)
        val selectedRating = STANDARD_ATS_SIZES.firstOrNull { it >= govCurrent } ?: STANDARD_ATS_SIZES.last()
        val loadingPct = if (selectedRating > 0) (govCurrent / selectedRating) * 100.0 else 0.0

        val icw = input.prospectiveFault_kA
        // Peak making factor n per IEC 60947-6-1 Table 7
        val n = when {
            icw <= 10.0 -> 1.5
            icw <= 20.0 -> 1.7
            icw <= 50.0 -> 2.0
            else -> 2.2
        }
        val icm = icw * n

        val construction = if (icw > 65.0) "ACB-based (IEC 60947-2)" else "MCCB / Switch-based (IEC 60947-6-1)"
        val transferTime = input.mainsFailureDetectDelay_s + input.generatorStartDelay_s + input.generatorWarmup_s
        val isNfpa110Pass = transferTime <= 10.0 // Type 10 requirement

        return AtsQuickResult(
            recommendedRating_A = selectedRating,
            governingCurrent_A = govCurrent,
            shortTimeWithstandIcw_kA = icw,
            peakMakingCapacityIcm_kA = round(icm * 10.0) / 10.0,
            recommendedConstruction = construction,
            totalTransferTimeToGen_s = round(transferTime * 10.0) / 10.0,
            isNfpa110Compliant = isNfpa110Pass,
            atsLoadingPercent = round(loadingPct * 10.0) / 10.0,
            formulaAts = "I_ATS ≥ max(I_mains, I_gen)  |  I_cm = n · I_sc [IEC 60947-6-1 Tab. 7]",
            substitutionAts = "I_gov = max(${String.format(java.util.Locale.US, "%.0f", input.mainsCurrent_A)}, ${String.format(java.util.Locale.US, "%.0f", input.generatorFLC_A)}) = ${String.format(java.util.Locale.US, "%.0f", govCurrent)} A → Selected: ${selectedRating.toInt()} A  (I_cw: ${icw} kA, I_cm: ${String.format(java.util.Locale.US, "%.1f", icm)} kA, Transfer: ${String.format(java.util.Locale.US, "%.1f", transferTime)}s vs ≤10s)"
        )
    }
}
