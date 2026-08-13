package com.example.engine

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import kotlin.math.sqrt

data class AtsResult(
    val selectedLoad: Double = 0.0,
    val loadCurrent: Double = 0.0,
    val designCurrent: Double = 0.0,
    val generatorFLC: Double = 0.0,
    val governingCurrent: Double = 0.0,
    val finalRating: Double? = null,
    val finalLoadingPct: Double? = null,
    val atsType: String = "PC",
    val atsClass: String = "CB",
    val construction: String = "Contactor",
    val poles: String = "4P",
    val requiredIcw: Double = 0.0,
    val requiredIcm: Double = 0.0,
    val utilizationCategory: String = "AC-33B",
    val transferStatus: String = "OK",
    val cooldownStatus: String = "OK",
    val marginAssessment: String = "OK",
    val enclosureIpRating: String = "IP54",
    val manufacturer: String = "ASCO / Schneider",
    val status: String = "",
    val totalTransferToGenerator: Int = 0,
    val totalReTransferCycle: Int = 0,
    val messages: List<String> = emptyList()
)

object AtsEngine {
    fun calculate(
        settings: AtsEntity,
        totalConnectedLoad: Double,
        essentialLoadKVA: Double,
        generatorSelectedSize: Double,
        transformerFLC: Double,
        isc3PhaseAtTransformer: Double,
        circuits: List<CircuitEntity>
    ): AtsResult {
        val atsId = settings.atsId
        val atsCircuits = circuits.filter { circ ->
            if (circ.assignedAtsId != null) {
                circ.assignedAtsId == atsId
            } else {
                circ.atsIncluded && atsId == "ATS-01"
            }
        }

        if (atsCircuits.isEmpty()) {
            return buildNullResult(settings, isc3PhaseAtTransformer, isc3PhaseAtTransformer * 2.2, "MCCB/switch-based", "4P (3P+N)", "SELECT_LOADS", emptyList())
        }

        val requiredIcw = isc3PhaseAtTransformer
        // Peak withstand factor 2.2 per sheet R43/R44
        val requiredIcm = Math.round((requiredIcw * 2.2) * 100.0) / 100.0

        val recommendedConstruction = if (requiredIcw > 65.0) {
            "ACB-based (IEC 60947-2)"
        } else {
            "MCCB/switch-based"
        }
        val numberOfPoles = "4P (3P+N)"

        val selectedLoad = if (settings.loadCoverage == "Emergency Only") essentialLoadKVA else totalConnectedLoad
        val loadCurrent = if (settings.systemVoltage > 0) {
            selectedLoad * 1000.0 / (sqrt(3.0) * settings.systemVoltage)
        } else 0.0
        val designCurrent = loadCurrent * settings.designSafetyFactor * (1.0 + settings.futureGrowthFactor / 100.0)

        val generatorFLC = if (generatorSelectedSize > 0 && settings.systemVoltage > 0) {
            generatorSelectedSize * 1000.0 / (sqrt(3.0) * settings.systemVoltage)
        } else 0.0

        val governingCurrent = maxOf(designCurrent, generatorFLC)

        val standardAtsRatings = listOf(
            100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 630.0, 800.0,
            1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3200.0, 4000.0
        )

        if (governingCurrent == 0.0) {
            return buildNullResult(settings, requiredIcw, requiredIcm, recommendedConstruction, numberOfPoles, "N/A — No Generator", emptyList())
        }

        var finalRating = standardAtsRatings.firstOrNull { it >= governingCurrent }
        var isManualOverride = false
        if (settings.enableManualOverride && settings.manualSizeAmp != null) {
            finalRating = settings.manualSizeAmp
            isManualOverride = true
        }

        val finalLoadingPct = if (finalRating != null && finalRating > 0) {
            Math.round((governingCurrent / finalRating * 100.0) * 10.0) / 10.0
        } else null

        val status = if (isManualOverride) {
            "Manual Override Active"
        } else if (finalRating == null) {
            "No ATS Required / Exceeds Standards"
        } else {
            "Valid ATS Sized"
        }

        val totalTransferToGenerator = settings.mainsFailureDetectionDelay + settings.generatorStartSignalDelay + settings.generatorWarmUp
        val transferStatus = if (totalTransferToGenerator <= 10) {
            "NFPA 110 Compliant"
        } else {
            "NFPA 110 NON-compliant — exceeds 10 sec (Type 10 requires ≤10 sec)"
        }

        val safetyMargin = 10 - totalTransferToGenerator
        val marginAssessment = if (safetyMargin <= 0) {
            "ZERO margin — reduce Mains Failure Detection to 2 sec for 1 sec safety buffer"
        } else {
            "OK — $safetyMargin sec buffer"
        }

        val totalReTransferCycle = settings.mainsReturnDetectionDelay + settings.reTransferDelay + settings.generatorCooldown
        val cooldownStatus = if (settings.generatorCooldown >= 300) {
            "ISO 8528 Compliant"
        } else {
            "NON-compliant — below 300 sec minimum"
        }

        val messages = mutableListOf<String>()
        if (isManualOverride && finalRating != null && finalRating < governingCurrent) {
            messages.add("Warning: Manual ATS rating (${finalRating} A) is below governing current requirement (${governingCurrent} A).")
        }
        if (settings.utilizationCategory.contains("AC-33B", ignoreCase = true)) {
            messages.add("Warning: For motor-heavy loads, verify motor inrush does not exceed ATS making capacity (Icm).")
        }

        return AtsResult(
            selectedLoad = selectedLoad,
            loadCurrent = loadCurrent,
            designCurrent = designCurrent,
            generatorFLC = generatorFLC,
            governingCurrent = governingCurrent,
            finalRating = finalRating,
            finalLoadingPct = finalLoadingPct,
            atsType = settings.transferType,
            atsClass = settings.atsClass,
            construction = recommendedConstruction,
            poles = numberOfPoles,
            requiredIcw = requiredIcw,
            requiredIcm = requiredIcm,
            utilizationCategory = settings.utilizationCategory,
            transferStatus = transferStatus,
            cooldownStatus = cooldownStatus,
            marginAssessment = marginAssessment,
            enclosureIpRating = settings.enclosureIpRating,
            manufacturer = settings.approvedManufacturer,
            status = status,
            totalTransferToGenerator = totalTransferToGenerator,
            totalReTransferCycle = totalReTransferCycle,
            messages = messages
        )
    }

    private fun buildNullResult(
        settings: AtsEntity,
        requiredIcw: Double,
        requiredIcm: Double,
        construction: String,
        poles: String,
        status: String,
        messages: List<String>
    ): AtsResult {
        return AtsResult(
            selectedLoad = 0.0,
            loadCurrent = 0.0,
            designCurrent = 0.0,
            generatorFLC = 0.0,
            governingCurrent = 0.0,
            finalRating = null,
            finalLoadingPct = null,
            atsType = settings.transferType,
            atsClass = settings.atsClass,
            construction = construction,
            poles = poles,
            requiredIcw = requiredIcw,
            requiredIcm = requiredIcm,
            utilizationCategory = settings.utilizationCategory,
            transferStatus = "N/A",
            cooldownStatus = "N/A",
            marginAssessment = "N/A",
            enclosureIpRating = settings.enclosureIpRating,
            manufacturer = settings.approvedManufacturer,
            status = status,
            totalTransferToGenerator = 0,
            totalReTransferCycle = 0,
            messages = messages
        )
    }

    fun calculateAll(
        atsList: List<AtsEntity>,
        circuits: List<CircuitEntity>,
        generatorSelectedSizes: Map<String, Double> = emptyMap(),
        transformerFLC: Double = 0.0,
        isc3PhaseAtTransformer: Double = 0.0,
        totalConnectedLoad: Double = 0.0
    ): Map<String, AtsResult> {
        if (atsList.isEmpty()) return emptyMap()
        return atsList.associate { ats ->
            val emergencyGenId = ats.emergencySourceId ?: "GEN-01"
            val genSize = generatorSelectedSizes[emergencyGenId] ?: 0.0
            val atsCircuits = circuits.filter { circ ->
                if (circ.assignedAtsId != null) {
                    circ.assignedAtsId == ats.atsId
                } else {
                    circ.atsIncluded && ats.atsId == "ATS-01"
                }
            }
            val essentialLoadKVA = atsCircuits.sumOf { it.loadKVA }
            ats.atsId to calculate(
                settings = ats,
                totalConnectedLoad = totalConnectedLoad,
                essentialLoadKVA = essentialLoadKVA,
                generatorSelectedSize = genSize,
                transformerFLC = transformerFLC,
                isc3PhaseAtTransformer = isc3PhaseAtTransformer,
                circuits = circuits
            )
        }
    }
}
