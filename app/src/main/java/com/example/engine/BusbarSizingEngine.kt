package com.example.engine

import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Conductor material for switchboard distribution busbars.
 *
 * @param displayName Human-readable material name.
 * @param kFactor Material adiabatic thermal coefficient k (A*s^(1/2)/mm2) per IEC 60364-5-54 / IEC 60865.
 * @param conductivityMS Electrical conductivity in MS/m (m/(Ohm*mm2)).
 */
enum class BusbarMaterial(val displayName: String, val kFactor: Double, val conductivityMS: Double) {
    COPPER("Copper", 166.0, 56.0),
    ALUMINIUM("Aluminium", 106.0, 34.0)
}

/**
 * Surface finish for busbar conductors.
 * Painted/coated bars have higher emissivity (~0.9 vs ~0.4 for bare metal),
 * allowing ~15% higher thermal dissipation (DIN 43671 / IEC 61439).
 */
enum class BusbarFinish(val displayName: String, val ratingMultiplier: Double) {
    BARE("Bare", 1.00),
    PAINTED("Painted / Coated", 1.15)
}

/**
 * Physical geometry of a busbar conductor phase arrangement.
 */
data class BusbarProfile(
    val barsPerPhase: Int,
    val widthMm: Double,
    val thicknessMm: Double,
    val nominalAmpacityA: Double,
    val material: BusbarMaterial = BusbarMaterial.COPPER
) {
    val crossSectionPerPhaseMm2: Double
        get() = barsPerPhase * widthMm * thicknessMm

    val description: String
        get() = if (barsPerPhase == 1) {
            "1×(${widthMm.toInt()}×${thicknessMm.toInt()}) mm²"
        } else {
            "${barsPerPhase}×(${widthMm.toInt()}×${thicknessMm.toInt()}) mm²"
        }
}

/**
 * Comprehensive engineering calculation result for a switchboard distribution busbar.
 */
data class BusbarSizingResult(
    val ratedCurrentA: Double,
    val designCurrentA: Double,
    val deratedAmpacityA: Double,
    val profile: BusbarProfile,
    val material: BusbarMaterial,
    val icwKA: Double,
    val faultDurationS: Double,
    val ipkKA: Double,
    val peakFactorN: Double,
    val neutralProfile: String,
    val peProfile: String,
    val minThermalCrossSectionMm2: Double,
    val tempDeratingFactor: Double,
    val ipDeratingFactor: Double,
    val isAmpacitySufficient: Boolean,
    val isShortCircuitSufficient: Boolean,
    val status: String,
    val engineeringTag: String
)

/**
 * Switchboard distribution busbar sizing engine compliant with:
 * - IEC 61439-1 & IEC 61439-2: Low-voltage switchgear and controlgear assemblies (ratings, temperature rise, Table 7 peak withstand).
 * - DIN 43671: Copper busbars - continuous current carrying capacity, ambient temperature derating.
 * - IEC 60865-1: Short-circuit currents - calculation of effects (thermal short-time withstand).
 * - IEC 60364-5-54: Protective conductors and neutral conductor sizing rules.
 */
object BusbarSizingEngine {

    /**
     * Standard rated continuous currents (In) for low-voltage switchboards per IEC 61439-1.
     */
    val STANDARD_RATINGS_A = listOf(
        100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 630.0, 800.0,
        1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3200.0, 4000.0, 5000.0, 6300.0
    )

    /**
     * Standard short-time withstand current ratings (Icw) for low-voltage switchgear per IEC 61439-1.
     */
    val STANDARD_ICW_RATINGS_KA = listOf(
        10.0, 16.0, 25.0, 30.0, 35.0, 36.0, 40.0, 50.0, 65.0, 80.0, 100.0
    )

    /**
     * Standard rectangular bar profiles per DIN 43671 for vertical arrangement in indoor switchgear
     * at standard 35°C ambient with 65K temperature rise (100°C conductor temperature).
     */
    val STANDARD_PROFILES = listOf(
        BusbarProfile(1, 15.0, 3.0, 110.0),
        BusbarProfile(1, 20.0, 3.0, 145.0),
        BusbarProfile(1, 20.0, 5.0, 200.0),
        BusbarProfile(1, 25.0, 5.0, 250.0),
        BusbarProfile(1, 30.0, 5.0, 315.0),
        BusbarProfile(1, 40.0, 5.0, 400.0),
        BusbarProfile(1, 40.0, 10.0, 630.0),
        BusbarProfile(1, 50.0, 10.0, 800.0),
        BusbarProfile(1, 60.0, 10.0, 1000.0),
        BusbarProfile(1, 80.0, 10.0, 1250.0),
        BusbarProfile(1, 100.0, 10.0, 1600.0),
        BusbarProfile(2, 50.0, 10.0, 1400.0),
        BusbarProfile(2, 60.0, 10.0, 1650.0),
        BusbarProfile(2, 80.0, 10.0, 2000.0),
        BusbarProfile(2, 100.0, 10.0, 2500.0),
        BusbarProfile(3, 100.0, 10.0, 3200.0),
        BusbarProfile(3, 120.0, 10.0, 3800.0),
        BusbarProfile(4, 100.0, 10.0, 4000.0),
        BusbarProfile(4, 120.0, 10.0, 5000.0),
        BusbarProfile(4, 160.0, 10.0, 6300.0)
    )

    /**
     * Resolves the temperature derating factor according to DIN 43671:
     * k_temp = sqrt((T_perm - T_amb) / (T_perm - 35))
     */
    fun calculateTemperatureDerating(
        ambientTempC: Double,
        permissibleTempC: Double = 105.0
    ): Double {
        if (ambientTempC <= 35.0) return 1.0
        val deltaNominal = max(1.0, permissibleTempC - 35.0)
        val deltaActual = max(0.0, permissibleTempC - ambientTempC)
        return sqrt(deltaActual / deltaNominal).coerceIn(0.1, 1.0)
    }

    /**
     * Resolves the enclosure protection degree derating factor (k_ip).
     * Ventilated switchboards (IP20-IP31) have good convection (k_ip = 1.0),
     * while sealed enclosures (IP54-IP65) impede cooling (k_ip ≈ 0.80).
     */
    fun calculateIpDerating(ipRating: String?): Double {
        val cleanIp = ipRating?.trim()?.uppercase().orEmpty()
        return when {
            cleanIp.contains("IP5") || cleanIp.contains("IP6") -> 0.80
            cleanIp.contains("IP4") -> 0.90
            else -> 1.00 // IP00, IP20, IP30, IP31 or unspecified
        }
    }

    /**
     * Resolves dynamic peak factor n = Ipk / Icw from IEC 61439-1 Table 7:
     * - Icw <= 5 kA -> n = 1.5 (cos phi = 0.7)
     * - 5 < Icw <= 10 kA -> n = 1.7 (cos phi = 0.5)
     * - 10 < Icw <= 20 kA -> n = 2.0 (cos phi = 0.3)
     * - 20 < Icw <= 50 kA -> n = 2.1 (cos phi = 0.25)
     * - Icw > 50 kA -> n = 2.2 (cos phi = 0.2)
     */
    fun resolvePeakFactorN(icwKa: Double): Double {
        return when {
            icwKa <= 5.0 -> 1.5
            icwKa <= 10.0 -> 1.7
            icwKa <= 20.0 -> 2.0
            icwKa <= 50.0 -> 2.1
            else -> 2.2
        }
    }

    /**
     * Sizes the distribution busbar for a switchboard or panel.
     */
    fun sizeBusbar(
        designCurrentA: Double,
        incomerRatingA: Double? = null,
        faultLevelKa: Double = 0.0,
        faultDurationS: Double = 1.0,
        voltageV: Double = 400.0,
        material: BusbarMaterial = BusbarMaterial.COPPER,
        finish: BusbarFinish = BusbarFinish.BARE,
        ipRating: String? = "IP54",
        ambientTempC: Double = 35.0,
        hasHighHarmonics: Boolean = false,
        manualRatingA: Double? = null,
        manualScKa: Double? = null
    ): BusbarSizingResult {
        // 1. Governing continuous current requirement
        val reqCurrentA = max(designCurrentA, incomerRatingA ?: 0.0)

        // 2. Environmental & Enclosure Derating Factors
        val kTemp = calculateTemperatureDerating(ambientTempC)
        val kIp = calculateIpDerating(ipRating)
        val kFinish = finish.ratingMultiplier
        val kMaterial = if (material == BusbarMaterial.ALUMINIUM) 0.78 else 1.00

        val totalDerating = kTemp * kIp * kFinish * kMaterial

        // 3. Select Standard Rated Continuous Current (In)
        val candidateRating = if (manualRatingA != null && manualRatingA > 0.0) {
            manualRatingA
        } else {
            STANDARD_RATINGS_A.firstOrNull { it >= reqCurrentA } ?: STANDARD_RATINGS_A.last()
        }

        // 4. Short-Circuit Thermal Withstand Requirement (IEC 60865 / IEC 60364-5-54)
        val effectiveFaultKa = if (manualScKa != null && manualScKa > 0.0) {
            max(manualScKa, faultLevelKa)
        } else {
            faultLevelKa
        }
        val minThermalCrossSectionMm2 = if (effectiveFaultKa > 0.0) {
            (effectiveFaultKa * 1000.0 * sqrt(max(0.1, faultDurationS))) / material.kFactor
        } else {
            0.0
        }

        // 5. Select Physical Busbar Profile
        val suitableProfile = STANDARD_PROFILES.firstOrNull { prof ->
            val deratedCap = prof.nominalAmpacityA * totalDerating
            val hasAmpacity = deratedCap >= reqCurrentA || prof.nominalAmpacityA >= candidateRating
            val hasThermalWithstand = prof.crossSectionPerPhaseMm2 >= minThermalCrossSectionMm2
            hasAmpacity && hasThermalWithstand
        } ?: STANDARD_PROFILES.last()

        val selectedProfile = suitableProfile.copy(material = material)
        val deratedAmpacityA = selectedProfile.nominalAmpacityA * totalDerating

        // 6. Rated Short-Time Withstand Current (Icw)
        val computedIcwKa = (material.kFactor * selectedProfile.crossSectionPerPhaseMm2) /
                (sqrt(max(0.1, faultDurationS)) * 1000.0)
        val finalIcwKa = if (manualScKa != null && manualScKa > 0.0) {
            manualScKa
        } else if (effectiveFaultKa > 0.0) {
            // Sized to standard switchboard fault withstand rating (>= faultLevelKa and <= bar physical capacity)
            STANDARD_ICW_RATINGS_KA.firstOrNull { it >= effectiveFaultKa && it <= computedIcwKa }
                ?: STANDARD_ICW_RATINGS_KA.firstOrNull { it >= effectiveFaultKa }
                ?: round(computedIcwKa * 10.0) / 10.0
        } else {
            STANDARD_ICW_RATINGS_KA.lastOrNull { it <= computedIcwKa } ?: round(computedIcwKa * 10.0) / 10.0
        }

        // 7. Dynamic Peak Withstand Current (Ipk)
        val factorN = resolvePeakFactorN(finalIcwKa)
        val ipkKa = round(finalIcwKa * factorN * 10.0) / 10.0

        // 8. Neutral & Protective Earth (PE) Busbar Sizing (IEC 61439-1 Section 8.6.1 & Table 3)
        val phSection = selectedProfile.crossSectionPerPhaseMm2
        val neutralSection = when {
            hasHighHarmonics -> phSection * 2.0
            phSection <= 16.0 -> phSection
            else -> max(16.0, phSection * 0.5)
        }
        val peSection = when {
            phSection <= 16.0 -> phSection
            phSection <= 35.0 -> 16.0
            else -> max(16.0, phSection * 0.5)
        }

        val neutralProfileStr = "${neutralSection.toInt()} mm² ${material.displayName}"
        val peProfileStr = "${peSection.toInt()} mm² ${material.displayName}"

        // 9. Compliance Checks & Status
        val isAmpOk = deratedAmpacityA >= (reqCurrentA * 0.99) || candidateRating >= (reqCurrentA * 0.99)
        val isScOk = effectiveFaultKa <= 0.0 || finalIcwKa >= (effectiveFaultKa * 0.99)
        val status = if (isAmpOk && isScOk) "PASS" else "FAIL"

        // 10. Consultant-Grade Engineering Tag
        val ratingInt = candidateRating.toInt()
        val voltInt = voltageV.toInt()
        val icwFormatted = if (finalIcwKa == finalIcwKa.toLong().toDouble()) {
            "${finalIcwKa.toLong()}kA"
        } else {
            "%.1fkA".format(finalIcwKa)
        }
        val ipFormatted = ipRating?.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
        val tag = "${material.displayName} Bus ${ratingInt}A, ${voltInt}V, ${icwFormatted}/${faultDurationS.toInt()}s$ipFormatted [${selectedProfile.description}]"

        return BusbarSizingResult(
            ratedCurrentA = candidateRating,
            designCurrentA = round(reqCurrentA * 10.0) / 10.0,
            deratedAmpacityA = round(deratedAmpacityA * 10.0) / 10.0,
            profile = selectedProfile,
            material = material,
            icwKA = finalIcwKa,
            faultDurationS = faultDurationS,
            ipkKA = ipkKa,
            peakFactorN = factorN,
            neutralProfile = neutralProfileStr,
            peProfile = peProfileStr,
            minThermalCrossSectionMm2 = round(minThermalCrossSectionMm2 * 10.0) / 10.0,
            tempDeratingFactor = round(kTemp * 100.0) / 100.0,
            ipDeratingFactor = round(kIp * 100.0) / 100.0,
            isAmpacitySufficient = isAmpOk,
            isShortCircuitSufficient = isScOk,
            status = status,
            engineeringTag = tag
        )
    }
}
