package com.example.engine

import com.example.data.Catalog
import com.example.data.ConduitCatalogEntry
import kotlin.math.PI
import kotlin.math.abs

data class ConduitFillInput(
    val circuitId: Int,
    val circuitName: String,
    val mainCableOD_mm: Double,
    val cpcSizeMm2: Double = 0.0,
    val cpcMaterial: String = "Cu",
    val parallelRuns: Int = 1,
    val routeLength_m: Double = 0.0,
    val routingMode: String = "ONE_PER_CONDUIT", // ONE_PER_CONDUIT, ALL_IN_ONE, CUSTOM
    val designProfile: String = "PROJECT_DESIGN", // PROJECT_DESIGN, NEC_CHAPTER_9, CUSTOM
    val customFillLimitPct: Double = 40.0,
    val isDirectBuried: Boolean = false,
    val cpcArrangement: String = "INTERNAL_CORE", // INTERNAL_CORE, SEPARATE_CONDUIT, EXTERNAL, ARMOURED, UNKNOWN
    val manualOverrideTradeSize: String? = null,
    val manualCpcOD_mm: Double? = null,
    val manufacturer: String = "",
    val conduitType: String = "Rigid PVC"
)

data class ConduitFillResult(
    val circuitId: Int,
    val circuitName: String,
    val recommendedConduitId: String,
    val recommendedTradeSize: String,
    val internalDiameterMm: Double,
    val conduitAreaMm2: Double,
    val totalCableAreaMm2: Double,
    val fillRatioPercent: Double,
    val allowedFillPercent: Double,
    val remainingAreaMm2: Double,
    val spareCapacityPercent: Double,
    val conduitsRequired: Int,
    val totalConduitLength_m: Double,
    val profile: String,
    val provenance: String,
    val status: String, // PASS, FAIL, DATA MISSING, REVIEW REQUIRED, NOT APPLICABLE, NO STANDARD SIZE AVAILABLE
    val validationMessages: List<String>,
    val mainCableAreaMm2: Double,
    val cpcCableAreaMm2: Double,
    val mainCableOD_mm: Double,
    val cpcOD_mm: Double = 0.0,
    val cpcSizeMm2: Double = 0.0,
    val cpcMaterial: String = "Cu",
    val cpcProductCode: String = "N/A",
    val cpcManufacturer: String = "",
    val cpcCableType: String = "N/A",
    val cpcProvenance: String = "MISSING", // CATALOG_EXACT, CATALOG_ALTERNATIVE, MANUAL_INPUT, MISSING
    val cpcMatchStatus: String = "MISSING",
    val isProvisionalWithoutCPC: Boolean = false,
    val isManualOverride: Boolean = false
)

data class ConduitProcurementGroup(
    val conduitType: String,
    val tradeSize: String,
    val catalogCode: String,
    val totalConduitsCount: Int,
    val totalLength_m: Double,
    val avgFillPercent: Double,
    val worstFillPercent: Double,
    val circuitsCount: Int,
    val passCount: Int,
    val failCount: Int,
    val reviewCount: Int,
    val missingCount: Int
)

object ConduitFillEngine {

    fun calculate(
        input: ConduitFillInput,
        catalog: List<ConduitCatalogEntry>
    ): ConduitFillResult {
        val messages = mutableListOf<String>()

        if (input.isDirectBuried) {
            return ConduitFillResult(
                circuitId = input.circuitId,
                circuitName = input.circuitName,
                recommendedConduitId = "N/A",
                recommendedTradeSize = "N/A",
                internalDiameterMm = 0.0,
                conduitAreaMm2 = 0.0,
                totalCableAreaMm2 = 0.0,
                fillRatioPercent = 0.0,
                allowedFillPercent = 0.0,
                remainingAreaMm2 = 0.0,
                spareCapacityPercent = 100.0,
                conduitsRequired = 0,
                totalConduitLength_m = 0.0,
                profile = input.designProfile,
                provenance = "DIRECT_BURIED_EXCLUSION",
                status = "NOT APPLICABLE",
                validationMessages = listOf("Circuit is marked DIRECT / NOT IN CONDUIT."),
                mainCableAreaMm2 = 0.0,
                cpcCableAreaMm2 = 0.0,
                mainCableOD_mm = input.mainCableOD_mm,
                cpcOD_mm = 0.0,
                isManualOverride = false
            )
        }

        if (input.mainCableOD_mm <= 0.0 || input.mainCableOD_mm.isNaN() || input.mainCableOD_mm.isInfinite()) {
            return ConduitFillResult(
                circuitId = input.circuitId,
                circuitName = input.circuitName,
                recommendedConduitId = "N/A",
                recommendedTradeSize = "N/A",
                internalDiameterMm = 0.0,
                conduitAreaMm2 = 0.0,
                totalCableAreaMm2 = 0.0,
                fillRatioPercent = 0.0,
                allowedFillPercent = 40.0,
                remainingAreaMm2 = 0.0,
                spareCapacityPercent = 0.0,
                conduitsRequired = input.parallelRuns,
                totalConduitLength_m = input.routeLength_m * input.parallelRuns,
                profile = input.designProfile,
                provenance = "DATA_MISSING_EXCLUSION",
                status = "DATA MISSING",
                validationMessages = listOf("Cable outside diameter is required."),
                mainCableAreaMm2 = 0.0,
                cpcCableAreaMm2 = 0.0,
                mainCableOD_mm = input.mainCableOD_mm,
                cpcOD_mm = 0.0,
                isManualOverride = false
            )
        }

        val mainCableArea = PI * input.mainCableOD_mm * input.mainCableOD_mm / 4.0

        var resolvedCpcOD = 0.0
        var resolvedCpcArea = 0.0
        var cpcProdCode = "N/A"
        var cpcMfg = ""
        var cpcType = "N/A"
        var cpcProv = "MISSING"
        var cpcMatchStat = "MISSING"
        var isProvisionalWithoutCPC = false

        if (input.cpcArrangement == "SEPARATE_CONDUIT") {
            val cpcSize = input.cpcSizeMm2
            if (input.manualCpcOD_mm != null && input.manualCpcOD_mm > 0.0) {
                resolvedCpcOD = input.manualCpcOD_mm
                resolvedCpcArea = PI * resolvedCpcOD * resolvedCpcOD / 4.0
                cpcProv = "MANUAL_INPUT"
                cpcMatchStat = "MANUAL_INPUT"
                cpcProdCode = "MANUAL-$cpcSize"
                cpcMfg = "Manual Input"
                cpcType = "Manual 1C CPC"
            } else if (cpcSize > 0.0) {
                val singleCoreTypeCodes = Catalog.cableTypes.filter {
                    it.cores.contains("1C", ignoreCase = true) || it.cableType.contains("Single Core", ignoreCase = true)
                }.map { it.code }.toSet()

                // 1. Exact catalog match
                val exactMatch = Catalog.cableCatalog.firstOrNull { entry ->
                    (entry.typeCode in singleCoreTypeCodes || entry.model.contains("1x", ignoreCase = true) || entry.model.contains("1C", ignoreCase = true)) &&
                    abs(entry.size - cpcSize) < 0.001 &&
                    entry.mfg.trim().equals(input.manufacturer.trim(), ignoreCase = true) &&
                    entry.od > 0.0
                }

                if (exactMatch != null) {
                    resolvedCpcOD = exactMatch.od
                    resolvedCpcArea = PI * resolvedCpcOD * resolvedCpcOD / 4.0
                    cpcProdCode = exactMatch.model
                    cpcMfg = exactMatch.mfg
                    cpcType = "1C $cpcSize mm²"
                    cpcProv = "CATALOG_EXACT"
                    cpcMatchStat = "CATALOG_EXACT"
                } else {
                    // 2. Alternative manufacturer match
                    val altMatch = Catalog.cableCatalog.firstOrNull { entry ->
                        (entry.typeCode in singleCoreTypeCodes || entry.model.contains("1x", ignoreCase = true) || entry.model.contains("1C", ignoreCase = true)) &&
                        abs(entry.size - cpcSize) < 0.001 &&
                        entry.od > 0.0
                    }

                    if (altMatch != null) {
                        resolvedCpcOD = altMatch.od
                        resolvedCpcArea = PI * resolvedCpcOD * resolvedCpcOD / 4.0
                        cpcProdCode = altMatch.model
                        cpcMfg = altMatch.mfg
                        cpcType = "1C $cpcSize mm²"
                        cpcProv = "CATALOG_ALTERNATIVE"
                        cpcMatchStat = "CATALOG_ALTERNATIVE"
                        messages.add("Alternative manufacturer CPC used: ${altMatch.mfg} (${altMatch.model}).")
                    } else {
                        cpcProv = "MISSING"
                        cpcMatchStat = "MISSING"
                        isProvisionalWithoutCPC = true
                        messages.add("Separate CPC in conduit requires CPC cable OD ($cpcSize mm² 1C). PROVISIONAL — CPC NOT INCLUDED.")
                    }
                }
            } else {
                cpcProv = "MISSING"
                cpcMatchStat = "MISSING"
                isProvisionalWithoutCPC = true
                messages.add("CPC size unavailable. PROVISIONAL — CPC NOT INCLUDED.")
            }
        } else if (input.cpcArrangement == "UNKNOWN") {
            messages.add("CPC arrangement is UNKNOWN — verify physical arrangement.")
        }

        val runs = if (input.parallelRuns <= 0) 1 else input.parallelRuns
        val (conduitsRequired, totalCableArea) = if (input.routingMode == "ALL_IN_ONE") {
            1 to (mainCableArea * runs + resolvedCpcArea)
        } else {
            runs to (mainCableArea + resolvedCpcArea)
        }

        val totalLength = input.routeLength_m * conduitsRequired

        // Determine allowed fill percentage and provenance
        val objectCount = if (input.routingMode == "ALL_IN_ONE") {
            runs + (if (input.cpcArrangement == "SEPARATE_CONDUIT") 1 else 0)
        } else {
            1 + (if (input.cpcArrangement == "SEPARATE_CONDUIT") 1 else 0)
        }

        val standardFillPct = when (objectCount) {
            1 -> 53.0
            2 -> 31.0
            else -> 40.0
        }

        val (allowedFill, provenance) = when (input.designProfile) {
            "CUSTOM" -> {
                input.customFillLimitPct.coerceIn(0.1, 100.0) to "CUSTOM INPUT — VERIFY PROJECT REQUIREMENT"
            }
            "NEC_CHAPTER_9" -> {
                standardFillPct to "NEC_CHAPTER_9"
            }
            else -> {
                standardFillPct to "PROJECT_DESIGN_RULE"
            }
        }

        val availableConduits = catalog.filter {
            it.conduitType.trim().equals(input.conduitType.trim(), ignoreCase = true)
        }.sortedBy { it.internalAreaMm2 }

        val candidateList = if (availableConduits.isNotEmpty()) availableConduits else catalog.sortedBy { it.internalAreaMm2 }

        if (isProvisionalWithoutCPC) {
            return ConduitFillResult(
                circuitId = input.circuitId,
                circuitName = input.circuitName,
                recommendedConduitId = "N/A",
                recommendedTradeSize = "N/A",
                internalDiameterMm = 0.0,
                conduitAreaMm2 = 0.0,
                totalCableAreaMm2 = totalCableArea,
                fillRatioPercent = 0.0,
                allowedFillPercent = allowedFill,
                remainingAreaMm2 = 0.0,
                spareCapacityPercent = 0.0,
                conduitsRequired = conduitsRequired,
                totalConduitLength_m = totalLength,
                profile = input.designProfile,
                provenance = provenance,
                status = "DATA MISSING",
                validationMessages = messages,
                mainCableAreaMm2 = mainCableArea,
                cpcCableAreaMm2 = 0.0,
                mainCableOD_mm = input.mainCableOD_mm,
                cpcOD_mm = 0.0,
                cpcSizeMm2 = input.cpcSizeMm2,
                cpcMaterial = input.cpcMaterial,
                cpcProvenance = "MISSING",
                cpcMatchStatus = "MISSING",
                isProvisionalWithoutCPC = true,
                isManualOverride = false
            )
        }

        if (candidateList.isEmpty()) {
            return ConduitFillResult(
                circuitId = input.circuitId,
                circuitName = input.circuitName,
                recommendedConduitId = "N/A",
                recommendedTradeSize = "N/A",
                internalDiameterMm = 0.0,
                conduitAreaMm2 = 0.0,
                totalCableAreaMm2 = totalCableArea,
                fillRatioPercent = 0.0,
                allowedFillPercent = allowedFill,
                remainingAreaMm2 = 0.0,
                spareCapacityPercent = 0.0,
                conduitsRequired = conduitsRequired,
                totalConduitLength_m = totalLength,
                profile = input.designProfile,
                provenance = provenance,
                status = "NO STANDARD SIZE AVAILABLE",
                validationMessages = listOf("Conduit catalog is empty."),
                mainCableAreaMm2 = mainCableArea,
                cpcCableAreaMm2 = resolvedCpcArea,
                mainCableOD_mm = input.mainCableOD_mm,
                cpcOD_mm = resolvedCpcOD,
                cpcSizeMm2 = input.cpcSizeMm2,
                cpcMaterial = input.cpcMaterial,
                cpcProductCode = cpcProdCode,
                cpcManufacturer = cpcMfg,
                cpcCableType = cpcType,
                cpcProvenance = cpcProv,
                cpcMatchStatus = cpcMatchStat,
                isManualOverride = false
            )
        }

        val selectedConduit: ConduitCatalogEntry?
        val isManual = !input.manualOverrideTradeSize.isNullOrBlank()

        if (isManual) {
            selectedConduit = candidateList.firstOrNull {
                it.tradeSize.trim().equals(input.manualOverrideTradeSize!!.trim(), ignoreCase = true)
            } ?: candidateList.last()
        } else {
            selectedConduit = candidateList.firstOrNull { entry ->
                val ratio = (totalCableArea / entry.internalAreaMm2) * 100.0
                ratio <= allowedFill
            }
        }

        if (selectedConduit == null) {
            return ConduitFillResult(
                circuitId = input.circuitId,
                circuitName = input.circuitName,
                recommendedConduitId = "N/A",
                recommendedTradeSize = "N/A",
                internalDiameterMm = 0.0,
                conduitAreaMm2 = 0.0,
                totalCableAreaMm2 = totalCableArea,
                fillRatioPercent = 0.0,
                allowedFillPercent = allowedFill,
                remainingAreaMm2 = 0.0,
                spareCapacityPercent = 0.0,
                conduitsRequired = conduitsRequired,
                totalConduitLength_m = totalLength,
                profile = input.designProfile,
                provenance = provenance,
                status = "NO STANDARD SIZE AVAILABLE",
                validationMessages = listOf("No standard conduit size satisfies fill limit of $allowedFill%."),
                mainCableAreaMm2 = mainCableArea,
                cpcCableAreaMm2 = resolvedCpcArea,
                mainCableOD_mm = input.mainCableOD_mm,
                cpcOD_mm = resolvedCpcOD,
                cpcSizeMm2 = input.cpcSizeMm2,
                cpcMaterial = input.cpcMaterial,
                cpcProductCode = cpcProdCode,
                cpcManufacturer = cpcMfg,
                cpcCableType = cpcType,
                cpcProvenance = cpcProv,
                cpcMatchStatus = cpcMatchStat,
                isManualOverride = isManual
            )
        }

        val conduitArea = selectedConduit.internalAreaMm2
        val fillRatio = (totalCableArea / conduitArea) * 100.0
        val remainingArea = conduitArea - totalCableArea
        val spareCapacity = (remainingArea / conduitArea) * 100.0

        val finalStatus = when {
            fillRatio > allowedFill -> "FAIL"
            input.cpcArrangement == "UNKNOWN" || input.designProfile == "CUSTOM" || cpcProv == "CATALOG_ALTERNATIVE" || cpcProv == "MANUAL_INPUT" -> "REVIEW REQUIRED"
            else -> "PASS"
        }

        return ConduitFillResult(
            circuitId = input.circuitId,
            circuitName = input.circuitName,
            recommendedConduitId = selectedConduit.id,
            recommendedTradeSize = selectedConduit.tradeSize,
            internalDiameterMm = selectedConduit.internalDiameterMm,
            conduitAreaMm2 = conduitArea,
            totalCableAreaMm2 = totalCableArea,
            fillRatioPercent = fillRatio,
            allowedFillPercent = allowedFill,
            remainingAreaMm2 = remainingArea,
            spareCapacityPercent = spareCapacity,
            conduitsRequired = conduitsRequired,
            totalConduitLength_m = totalLength,
            profile = input.designProfile,
            provenance = provenance,
            status = finalStatus,
            validationMessages = messages,
            mainCableAreaMm2 = mainCableArea,
            cpcCableAreaMm2 = resolvedCpcArea,
            mainCableOD_mm = input.mainCableOD_mm,
            cpcOD_mm = resolvedCpcOD,
            cpcSizeMm2 = input.cpcSizeMm2,
            cpcMaterial = input.cpcMaterial,
            cpcProductCode = cpcProdCode,
            cpcManufacturer = cpcMfg,
            cpcCableType = cpcType,
            cpcProvenance = cpcProv,
            cpcMatchStatus = cpcMatchStat,
            isManualOverride = isManual
        )
    }

    fun generateProcurementRollup(results: List<ConduitFillResult>): List<ConduitProcurementGroup> {
        val validConduitRecords = results.filter { it.status != "NOT APPLICABLE" && it.status != "DATA MISSING" && it.recommendedTradeSize != "N/A" }

        val grouped = validConduitRecords.groupBy { "${it.recommendedTradeSize}_Rigid PVC" }

        return grouped.map { (key, items) ->
            val tradeSize = items.firstOrNull()?.recommendedTradeSize ?: "N/A"
            val totalConduits = items.sumOf { it.conduitsRequired }
            val totalLength = items.sumOf { it.totalConduitLength_m }
            val avgFill = items.map { it.fillRatioPercent }.average()
            val worstFill = items.maxOfOrNull { it.fillRatioPercent } ?: 0.0

            val passCount = items.count { it.status == "PASS" }
            val failCount = items.count { it.status == "FAIL" }
            val reviewCount = items.count { it.status == "REVIEW REQUIRED" }
            val missingCount = items.count { it.status == "DATA MISSING" || it.status == "NO STANDARD SIZE AVAILABLE" }

            ConduitProcurementGroup(
                conduitType = "Rigid PVC",
                tradeSize = tradeSize,
                catalogCode = "PVC-$tradeSize",
                totalConduitsCount = totalConduits,
                totalLength_m = totalLength,
                avgFillPercent = Math.round(avgFill * 100.0) / 100.0,
                worstFillPercent = Math.round(worstFill * 100.0) / 100.0,
                circuitsCount = items.size,
                passCount = passCount,
                failCount = failCount,
                reviewCount = reviewCount,
                missingCount = missingCount
            )
        }.sortedBy { it.tradeSize }
    }
}
