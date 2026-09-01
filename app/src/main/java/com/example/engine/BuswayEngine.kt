package com.example.engine

import com.example.data.CircuitEntity
import com.example.data.Catalog
import com.example.data.catalog.BuswayCatalogEntry
import kotlin.math.sqrt

data class BuswaySizingResult(
    val Ib_A: Double,
    val iRequired_A: Double,
    val selectedBusway: BuswayCatalogEntry?,
    val ratingA: Double,
    val icwKA: Double,
    val ipkKA: Double,
    val voltDrop_V: Double,
    val vdPct: Double,
    val cumulativeVDpct: Double,
    val rMOhmPerM: Double,
    val xMOhmPerM: Double,
    val weightKgPerM: Double,
    val status: String,
    val failReason: String = "",
    val warningMessage: String? = null,
    val networkIscKA: Double = 0.0,
    val icwCheckStatus: String = "PASS",
    val resultStatus: SizingResultStatus,
    val primaryFailureCode: CableFailureCode?,
    val isIcwOk: Boolean,
    val isVoltageDropOk: Boolean,
    val isAmpacityOk: Boolean
)

object BuswayEngine {

    fun sizeBusway(
        circuit: CircuitEntity,
        parentCumulativeVD: Double = 0.0,
        catalog: List<BuswayCatalogEntry> = Catalog.busways,
        networkIscKA: Double = 0.0
    ): BuswaySizingResult {
        // 1. Calculate Load Current (Ib)
        val v = if (circuit.voltageV > 0) circuit.voltageV.toDouble() else 400.0
        val phases = circuit.phases.uppercase()
        val isThreePhase = phases.contains("3P")
        
        val ib = if (isThreePhase) {
            (circuit.loadKVA * 1000.0) / (sqrt(3.0) * v)
        } else {
            (circuit.loadKVA * 1000.0) / v
        }
        
        val iReq = ib * (if (circuit.safetyFactor > 0) circuit.safetyFactor else 1.0)

        if (catalog.isEmpty()) {
            return BuswaySizingResult(
                Ib_A = ib,
                iRequired_A = iReq,
                selectedBusway = null,
                ratingA = 0.0,
                icwKA = 0.0,
                ipkKA = 0.0,
                voltDrop_V = 0.0,
                vdPct = 0.0,
                cumulativeVDpct = parentCumulativeVD,
                rMOhmPerM = 0.0,
                xMOhmPerM = 0.0,
                weightKgPerM = 0.0,
                status = "✗ FAIL",
                failReason = "Busway catalog is empty or unavailable.",
                resultStatus = SizingResultStatus.FAIL,
                primaryFailureCode = CableFailureCode.CATALOG_MISSING,
                isIcwOk = false,
                isVoltageDropOk = false,
                isAmpacityOk = false
            )
        }

        // 2. Filter Candidate Entries
        val filteredByMfg = if (!circuit.buswayMfg.isNullAndBlank()) {
            catalog.filter { it.mfg.equals(circuit.buswayMfg, ignoreCase = true) }
        } else {
            catalog
        }

        val modelStr = circuit.buswayModel?.trim().orEmpty()
        val filteredByModel = if (modelStr.isNotEmpty() && 
            !modelStr.equalsIgnoreCaseAny("All", "Any", "All / Any")) {
            filteredByMfg.filter { it.model.contains(modelStr, ignoreCase = true) }
        } else {
            filteredByMfg
        }

        val candidateList = if (filteredByModel.isNotEmpty()) filteredByModel else catalog

        // Sort by ratingA ascending
        val sortedCandidates = candidateList.sortedBy { it.ratingA }
        
        // Select smallest rating where ratingA >= iReq
        val selected = sortedCandidates.firstOrNull { it.ratingA >= iReq }

        if (selected == null) {
            val maxAvailable = sortedCandidates.lastOrNull()?.ratingA ?: 0.0
            return BuswaySizingResult(
                Ib_A = ib,
                iRequired_A = iReq,
                selectedBusway = null,
                ratingA = maxAvailable,
                icwKA = 0.0,
                ipkKA = 0.0,
                voltDrop_V = 0.0,
                vdPct = 0.0,
                cumulativeVDpct = parentCumulativeVD,
                rMOhmPerM = 0.0,
                xMOhmPerM = 0.0,
                weightKgPerM = 0.0,
                status = "✗ FAIL",
                failReason = "Required current (${String.format("%.1f", iReq)} A) exceeds maximum catalog busway rating (${maxAvailable.toInt()} A).",
                resultStatus = SizingResultStatus.FAIL,
                primaryFailureCode = CableFailureCode.AMPACITY_FAIL,
                isIcwOk = false,
                isVoltageDropOk = false,
                isAmpacityOk = false
            )
        }

        // 3. Calculate Voltage Drop using exact catalog R and X (in mOhm/m)
        val pf = circuit.pf.coerceIn(0.1, 1.0)
        val sinPf = sqrt(1.0 - pf * pf)
        val lenM = circuit.length_m.coerceAtLeast(0.1)

        // R and X in mOhm/m -> vDrop = sqrt(3) * Ib * L(m) * (R*cos + X*sin) / 1000
        val factor = if (isThreePhase) sqrt(3.0) else 2.0
        val vDrop = factor * ib * (lenM / 1000.0) * (selected.rMOhmPerM * pf + selected.xMOhmPerM * sinPf)
        val vdPct = (vDrop / v) * 100.0
        val cumVDpct = parentCumulativeVD + vdPct

        // Limit Check
        val limitPct = if (circuit.maxVDpct < 1.0) circuit.maxVDpct * 100.0 else circuit.maxVDpct
        val vdOk = cumVDpct <= limitPct

        // Short Circuit Icw Check
        val icwOk = networkIscKA <= 0.0 || selected.icwKA >= networkIscKA
        val icwStatusStr = if (icwOk) "PASS" else "FAIL"

        var status = "✓ PASS"
        var failReason = ""
        var warningMsg: String? = null
        var resultStatus = SizingResultStatus.PASS
        var primaryFailureCode: CableFailureCode? = null

        if (!vdOk) {
            status = "✗ FAIL"
            failReason = "Cumulative voltage drop (${String.format("%.2f", cumVDpct)}%) exceeds maximum limit (${String.format("%.1f", limitPct)}%)."
            resultStatus = SizingResultStatus.FAIL
            primaryFailureCode = CableFailureCode.VOLTAGE_DROP_FAIL
        } else if (!icwOk) {
            status = "✗ FAIL"
            failReason = "Busway rated Icw (${selected.icwKA} kA) is lower than network short circuit level (${String.format("%.1f", networkIscKA)} kA)."
            resultStatus = SizingResultStatus.FAIL
            primaryFailureCode = CableFailureCode.INDETERMINATE
        }

        return BuswaySizingResult(
            Ib_A = ib,
            iRequired_A = iReq,
            selectedBusway = selected,
            ratingA = selected.ratingA,
            icwKA = selected.icwKA,
            ipkKA = selected.ipkKA,
            voltDrop_V = vDrop,
            vdPct = vdPct,
            cumulativeVDpct = cumVDpct,
            rMOhmPerM = selected.rMOhmPerM,
            xMOhmPerM = selected.xMOhmPerM,
            weightKgPerM = selected.weightKgPerM,
            status = status,
            failReason = failReason,
            warningMessage = warningMsg,
            networkIscKA = networkIscKA,
            icwCheckStatus = icwStatusStr,
            resultStatus = resultStatus,
            primaryFailureCode = primaryFailureCode,
            isIcwOk = icwOk,
            isVoltageDropOk = vdOk,
            isAmpacityOk = true
        )
    }

    fun toSizingResult(buswayRes: BuswaySizingResult): SizingResult {
        val selected = buswayRes.selectedBusway
        val prodCode = selected?.let { "${it.mfg} ${it.model} ${it.ratingA.toInt()}A ${it.conductor}" } ?: "Busway Trunking"
        val descSize = selected?.let { "${it.ratingA.toInt()}A Busway (${it.conductor})" } ?: "Busway Trunking"

        return SizingResult(
            Ib_A = buswayRes.Ib_A,
            deviceSize_A = buswayRes.ratingA,
            deviceType = "Busway Feeder",
            cTotalDerating = 1.0,
            iRequired_perCable_A = buswayRes.iRequired_A,
            productCode = prodCode,
            cableSize_mm2 = descSize,
            ratedI_table_A = buswayRes.ratingA,
            capacity_derated_Iz_A = buswayRes.ratingA,
            utilizationPct = if (buswayRes.ratingA > 0) (buswayRes.Ib_A / buswayRes.ratingA) * 100.0 else 0.0,
            neutral_mm2 = "Full (Integral)",
            earthCPC_mm2 = "Integral Housing",
            voltDrop_V = buswayRes.voltDrop_V,
            vdPct = buswayRes.vdPct,
            cumulativeVDpct = buswayRes.cumulativeVDpct,
            status = buswayRes.status,
            failReason = buswayRes.failReason,
            cableMaterial = selected?.conductor ?: "Cu",
            insulation = "Air/Film Insulated",
            // H4 note: k = 143 (Cu, ~90 °C-class air/film insulation) is a documented BUSWAY
            // CATALOG CONVENTION per IEC 60364-5-54 — busway thermal withstand is verified by
            // the manufacturer's listed Icw/I²t rating; this k is only a conservative proxy
            // for the adiabatic check and must not be reused for PVC-class systems.
            kFactor = 143.0,
            deviceBreakingKA = buswayRes.icwKA,
            cableOD_mm = 100.0, // Non-zero nominal housing profile height for conduit/space engines
            earthOD_mm = 0.0,
            rUsed = buswayRes.rMOhmPerM,
            xUsed = buswayRes.xMOhmPerM,
            clearingTime_s = 1.0,
            odSourceMfg = selected?.mfg ?: "Elsewedy",
            isODApproximated = false,
            resultStatus = buswayRes.resultStatus,
            isAmpacityOk = buswayRes.isAmpacityOk,
            isVoltageDropOk = buswayRes.isVoltageDropOk,
            primaryFailureCode = buswayRes.primaryFailureCode
        )
    }

    private fun String?.isNullAndBlank(): Boolean = this == null || this.trim().isEmpty()

    private fun String?.equalsIgnoreCaseAny(vararg options: String): Boolean {
        if (this == null) return false
        val trimmed = this.trim()
        return options.any { trimmed.equals(it, ignoreCase = true) }
    }
}
