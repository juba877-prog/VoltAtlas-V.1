package com.example.engine

import com.example.data.AccessoryEntry
import com.example.data.Catalog
import com.example.data.CircuitEntity
import kotlin.math.ceil
import kotlin.math.sqrt

// 1. BOQ Engine Model
data class BoqItem(
    val cableType: String,
    val size: String,
    val totalLength_m: Double,
    val unitWeight_g_m: Double,
    val totalWeight_kg: Double,
    val runsCount: Int,
    val manufacturer: String = "",
    val productCode: String = "",
    val status: String = "OK",
    val messages: List<String> = emptyList(),
    val circuitsCount: Int = 1,
    val routeLength_m: Double = 0.0,
    val installedLength_m: Double = 0.0,
    val orderedLength_m: Double = 0.0,
    val waste_m: Double = 0.0
)

data class BoqKpi(
    val totalCircuits: Int = 0,
    val totalRoute_m: Double = 0.0,
    val totalInstalled_m: Double = 0.0,
    val totalOrdered_m: Double = 0.0,
    val totalDrums: Int = 0,
    val totalDevices: Int = 0,
    val cableFamilies: Int = 0,
    val wastePct: Double = 0.0,
    val totalGlands: Int = 0
)

data class BoqResult(
    val items: List<BoqItem>,
    val kpi: BoqKpi
)

data class BoqGroupKey(
    val manufacturer: String,
    val cableType: String,
    val size: String,
    val productCode: String
)

object BoqEngine {
    fun generateBoq(circuits: List<CircuitEntity>): List<BoqItem> {
        return generateBoq(circuits, emptyMap())
    }

    fun generateBoq(
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>
    ): List<BoqItem> {
        val grouped = circuits.groupBy { circuit ->
            val sizingRes = sizingResults[circuit.id]
            val size = sizingRes?.cableSize_mm2 ?: (circuit.overrideCableSize ?: "Auto")
            val productCode = sizingRes?.productCode ?: "N/A"
            BoqGroupKey(
                manufacturer = circuit.manufacturer.trim(),
                cableType = circuit.cableType.trim(),
                size = size,
                productCode = productCode
            )
        }
        
        return grouped.map { (key, list) ->
            val routeLength = list.sumOf { it.length_m }
            val installedLength = list.sumOf { it.length_m * (if (it.parallelRuns <= 0) 1 else it.parallelRuns) }
            val sizeVal = parseCableSize(key.size) ?: 0.0
            
            // Find weight per km from catalog
            val typeCode = Catalog.cableTypes.firstOrNull { it.cableType.lowercase() == key.cableType.lowercase() }?.code ?: 1
            
            val catalogEntry = if (key.productCode.isNotEmpty() && key.productCode != "N/A") {
                Catalog.cableCatalog.firstOrNull { 
                    it.model.trim().equals(key.productCode.trim(), ignoreCase = true) &&
                    it.mfg.trim().equals(key.manufacturer.trim(), ignoreCase = true) &&
                    Math.abs(it.size - sizeVal) < 0.001
                } ?: Catalog.cableCatalog.firstOrNull { 
                    it.typeCode == typeCode &&
                    it.mfg.trim().equals(key.manufacturer.trim(), ignoreCase = true) &&
                    Math.abs(it.size - sizeVal) < 0.001
                }
            } else {
                Catalog.cableCatalog.firstOrNull { 
                    it.typeCode == typeCode &&
                    it.mfg.trim().equals(key.manufacturer.trim(), ignoreCase = true) &&
                    Math.abs(it.size - sizeVal) < 0.001
                }
            }

            val wt = if (catalogEntry != null) {
                if (catalogEntry.wt > 0.0) {
                    catalogEntry.wt
                } else {
                    val fallback = Catalog.cableCatalog.firstOrNull {
                        it.typeCode == typeCode &&
                        it.wt > 0.0 &&
                        Math.abs(it.size - sizeVal) < 0.001 &&
                        it.mfg.trim().equals("Elsewedy", ignoreCase = true)
                    } ?: Catalog.cableCatalog.firstOrNull {
                        it.typeCode == typeCode &&
                        it.wt > 0.0 &&
                        Math.abs(it.size - sizeVal) < 0.001
                    }
                    fallback?.wt ?: CableSizingEngine.estimateSensibleWT(sizeVal, typeCode)
                }
            } else {
                0.0
            }
            val isMissing = wt <= 0.0
            val totalWeight = if (isMissing) 0.0 else (installedLength * wt) / 1000.0
            
            val displayCableType = if (key.manufacturer.isNotEmpty()) {
                if (key.productCode.isNotEmpty() && key.productCode != "N/A") {
                    "${key.manufacturer} - ${key.cableType} (${key.productCode})"
                } else {
                    "${key.manufacturer} - ${key.cableType}"
                }
            } else {
                key.cableType
            }

            BoqItem(
                cableType = displayCableType,
                size = key.size,
                totalLength_m = installedLength,
                unitWeight_g_m = wt,
                totalWeight_kg = Math.round(totalWeight * 100.0) / 100.0,
                runsCount = list.sumOf { if (it.parallelRuns <= 0) 1 else it.parallelRuns },
                manufacturer = key.manufacturer,
                productCode = key.productCode,
                status = if (isMissing) "DATA MISSING" else "OK",
                messages = if (isMissing) listOf("Unit weight missing for ${key.manufacturer} ${key.cableType} ${key.size}") else emptyList(),
                circuitsCount = list.size,
                routeLength_m = routeLength,
                installedLength_m = installedLength,
                orderedLength_m = installedLength,
                waste_m = 0.0
            )
        }.sortedWith(compareByDescending<BoqItem> { it.totalLength_m }.thenBy { it.size })
    }

    fun generateBoqResult(
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        drumAllocations: List<DrumAllocation> = emptyList()
    ): BoqResult {
        val items = generateBoq(circuits, sizingResults)
        val totalRoute = circuits.sumOf { it.length_m }
        val totalInstalled = items.sumOf { it.installedLength_m }
        val totalOrderedFromDrums = drumAllocations.sumOf { it.drumCapacity_m }
        val totalOrdered = if (totalOrderedFromDrums > 0.0) totalOrderedFromDrums else totalInstalled
        val totalDrums = drumAllocations.size
        val totalDevices = circuits.size
        val cableFamilies = items.map { it.cableType }.distinct().size
        val wasteLen = maxOf(0.0, totalOrdered - totalInstalled)
        val wastePct = if (totalOrdered > 0.0) (wasteLen / totalOrdered) * 100.0 else 0.0
        val totalGlands = circuits.sumOf { 2 * (if (it.parallelRuns <= 0) 1 else it.parallelRuns) }

        val updatedItems = items.map { item ->
            val familyAlloc = drumAllocations.filter { it.size == item.size }
            val ord = familyAlloc.sumOf { it.drumCapacity_m }
            val ordLen = if (ord > 0.0) ord else item.installedLength_m
            val wst = maxOf(0.0, ordLen - item.installedLength_m)
            item.copy(orderedLength_m = ordLen, waste_m = wst)
        }

        val kpi = BoqKpi(
            totalCircuits = circuits.size,
            totalRoute_m = totalRoute,
            totalInstalled_m = totalInstalled,
            totalOrdered_m = totalOrdered,
            totalDrums = totalDrums,
            totalDevices = totalDevices,
            cableFamilies = cableFamilies,
            wastePct = wastePct,
            totalGlands = totalGlands
        )
        return BoqResult(updatedItems, kpi)
    }

    private fun parseCableSize(value: String?): Double? {
        if (value == null) return null
        val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""").find(value)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }
}

// 2. Drum Schedule Engine Model
data class DrumItem(
    val cableType: String,
    val size: String,
    val totalLength_m: Double,
    val maxDrumLength_m: Double,
    val fullDrumsCount: Int,
    val finalDrumLength_m: Double,
    val totalDrums: Int
)

data class CableCut(
    val circuitId: Int,
    val circuitName: String,
    val runIndex: Int,
    val length_m: Double
)

data class DrumAllocation(
    val drumIndex: Int,
    val cableType: String,
    val size: String,
    val drumCapacity_m: Double,
    val allocatedCuts: List<CableCut>,
    val totalUsedLength_m: Double,
    val wasteLength_m: Double
)

object DrumScheduleEngine {
    fun generateDrumSchedule(circuits: List<CircuitEntity>, maxDrumLength: Double = 500.0): List<DrumItem> {
        return generateDrumSchedule(circuits, emptyMap(), maxDrumLength)
    }

    fun generateDetailedDrumSchedule(
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        maxDrumLength: Double = 500.0,
        safetyMargin: Double = 0.0,
        cutOverhead: Double = 0.0
    ): List<DrumAllocation> {
        val standardDrumSizes = listOf(100.0, 250.0, 500.0, 1000.0)

        val grouped = circuits.groupBy { circuit ->
            val sizingRes = sizingResults[circuit.id]
            val size = sizingRes?.cableSize_mm2 ?: (circuit.overrideCableSize ?: "Auto")
            val productCode = sizingRes?.productCode ?: "N/A"
            BoqGroupKey(
                manufacturer = circuit.manufacturer.trim(),
                cableType = circuit.cableType.trim(),
                size = size,
                productCode = productCode
            )
        }

        val allAllocations = mutableListOf<DrumAllocation>()
        var globalDrumIndex = 1

        grouped.forEach { (key, circuitList) ->
            val cuts = mutableListOf<CableCut>()
            circuitList.forEach { circ ->
                val runs = if (circ.parallelRuns <= 0) 1 else circ.parallelRuns
                for (run in 1..runs) {
                    val rawLen = circ.length_m * (1.0 + safetyMargin) + cutOverhead
                    val roundedLen = Math.round(rawLen * 100.0) / 100.0
                    cuts.add(
                        CableCut(
                            circuitId = circ.id,
                            circuitName = circ.circuitName,
                            runIndex = run,
                            length_m = roundedLen
                        )
                    )
                }
            }

            val unallocatedCuts = cuts.sortedByDescending { it.length_m }.toMutableList()

            class TempDrum(val index: Int, var capacity: Double) {
                val allocated = mutableListOf<CableCut>()
                val used: Double get() = allocated.sumOf { it.length_m }
                val remaining: Double get() = capacity - used
            }

            val drums = mutableListOf<TempDrum>()

            while (unallocatedCuts.isNotEmpty()) {
                var placedInExisting = false
                for (drum in drums) {
                    val fitCut = unallocatedCuts.firstOrNull { it.length_m <= drum.remaining }
                    if (fitCut != null) {
                        drum.allocated.add(fitCut)
                        unallocatedCuts.remove(fitCut)
                        placedInExisting = true
                        break
                    }
                }

                if (!placedInExisting) {
                    val largestCutLen = unallocatedCuts.firstOrNull()?.length_m ?: 0.0
                    val remainingSum = unallocatedCuts.sumOf { it.length_m }

                    val chosenCapacity = when {
                        largestCutLen > 1000.0 -> Math.ceil(largestCutLen / 100.0) * 100.0
                        else -> {
                            val singleFit = standardDrumSizes.firstOrNull { it >= largestCutLen } ?: 1000.0
                            if (remainingSum <= singleFit) {
                                singleFit
                            } else {
                                standardDrumSizes.firstOrNull { it >= remainingSum }
                                    ?: standardDrumSizes.filter { it >= largestCutLen }.maxOrNull()
                                    ?: 500.0
                            }
                        }
                    }

                    val newDrum = TempDrum(globalDrumIndex++, chosenCapacity)

                    val iterator = unallocatedCuts.iterator()
                    while (iterator.hasNext()) {
                        val cut = iterator.next()
                        if (cut.length_m <= newDrum.remaining) {
                            newDrum.allocated.add(cut)
                            iterator.remove()
                        }
                    }
                    drums.add(newDrum)
                }
            }

            val displayCableType = if (key.manufacturer.isNotEmpty()) {
                if (key.productCode.isNotEmpty() && key.productCode != "N/A") {
                    "${key.manufacturer} - ${key.cableType} (${key.productCode})"
                } else {
                    "${key.manufacturer} - ${key.cableType}"
                }
            } else {
                key.cableType
            }

            drums.forEach { tempDrum ->
                val waste = Math.max(0.0, Math.round(tempDrum.remaining * 100.0) / 100.0)
                allAllocations.add(
                    DrumAllocation(
                        drumIndex = tempDrum.index,
                        cableType = displayCableType,
                        size = key.size,
                        drumCapacity_m = tempDrum.capacity,
                        allocatedCuts = tempDrum.allocated,
                        totalUsedLength_m = Math.round(tempDrum.used * 100.0) / 100.0,
                        wasteLength_m = waste
                    )
                )
            }
        }

        return allAllocations
    }

    fun generateDrumSchedule(
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>,
        maxDrumLength: Double = 500.0
    ): List<DrumItem> {
        val boq = BoqEngine.generateBoq(circuits, sizingResults)
        return boq.map { item ->
            val total = item.totalLength_m
            val maxDrum = if (maxDrumLength <= 0.0 || maxDrumLength.isNaN() || maxDrumLength.isInfinite()) 500.0 else maxDrumLength
            
            val fullDrums = if (total <= 0.0 || total.isNaN() || total.isInfinite()) {
                0
            } else {
                (total / maxDrum).toInt()
            }
            
            val remainder = if (total <= 0.0 || total.isNaN() || total.isInfinite()) {
                0.0
            } else {
                total % maxDrum
            }
            
            val totalDrums = fullDrums + if (remainder > 0.0) 1 else 0
            
            DrumItem(
                cableType = item.cableType,
                size = item.size,
                totalLength_m = if (total.isNaN() || total.isInfinite() || total < 0.0) 0.0 else total,
                maxDrumLength_m = maxDrum,
                fullDrumsCount = fullDrums,
                finalDrumLength_m = Math.round(remainder * 100.0) / 100.0,
                totalDrums = totalDrums
            )
        }
    }
}

// 3. Panel Schedule Engine Model
data class PanelPhaseLoad(
    val phase: String,       // "L1", "L2", "L3"
    val loadKVA: Double,
    val current_A: Double
)

data class PanelScheduleResult(
    val panelName: String,
    val totalConnected_kVA: Double,
    val activeDemand_kW: Double,
    val phaseLoads: List<PanelPhaseLoad>,
    val unbalancePct: Double
)

data class PanelScheduleRow(
    val itemNo: Int,
    val description: String,
    val circuitId: Int?,
    val isSubPanel: Boolean = false,
    val subPanelName: String? = null,
    val loadKVA: Double,
    val phases: String,
    val assignedPhase: String,
    val breakerRating: String = "-",
    val cableSize: String = "-",
    val isDirectLoadItem: Boolean = false
)

data class PanelScheduleRecursiveResult(
    val panelName: String,
    val panelCircuit: CircuitEntity?,
    val childrenCircuits: List<CircuitEntity> = emptyList(),
    val rows: List<PanelScheduleRow> = emptyList(),
    val aggregatedChildrenLoadKVA: Double = 0.0,
    val panelOwnLoadKVA: Double = 0.0,
    val directLoadConfirmed: Boolean? = null,
    val totalPanelLoadKVA: Double = 0.0,
    val hasLoadMismatch: Boolean = false,
    val hasUnconfirmedDirectLoadDialog: Boolean = false,
    val phaseL1KVA: Double = 0.0,
    val phaseL2KVA: Double = 0.0,
    val phaseL3KVA: Double = 0.0,
    val unbalancePct: Double = 0.0,
    val hasCircularReference: Boolean = false,
    val errorMessage: String? = null,
    val loadTypeBreakdown: com.example.data.LoadTypeBreakdown = com.example.data.calculateLoadTypeBreakdown(childrenCircuits)
) {
    fun effectiveImbalanceLimit(project: com.example.data.ProjectEntity?): Double {
        return panelCircuit?.phaseImbalanceLimitOverridePct
            ?: project?.defaultPhaseImbalanceLimitPct
            ?: 20.0
    }

    fun isBalanced(project: com.example.data.ProjectEntity?): Boolean {
        return unbalancePct <= effectiveImbalanceLimit(project)
    }
}

object PanelScheduleEngine {
    fun generateSchedule(panelName: String, circuits: List<CircuitEntity>, demandFactor: Double = 0.8): PanelScheduleResult {
        val panelCircuits = circuits.filter { it.fedFrom == panelName }
        val totalKva = panelCircuits.sumOf { it.loadKVA }
        
        // Distribute load across 3 phases (L1, L2, L3)
        var l1 = 0.0
        var l2 = 0.0
        var l3 = 0.0
        
        panelCircuits.forEachIndexed { index, circ ->
            if (circ.phases.contains("3P")) {
                val perPhase = circ.loadKVA / 3.0
                l1 += perPhase
                l2 += perPhase
                l3 += perPhase
            } else {
                val phase = circ.assignedPhase?.trim()?.uppercase()
                when (phase) {
                    "L1" -> l1 += circ.loadKVA
                    "L2" -> l2 += circ.loadKVA
                    "L3" -> l3 += circ.loadKVA
                    else -> {
                        // Distribute single phase loads round-robin to balance
                        when (index % 3) {
                            0 -> l1 += circ.loadKVA
                            1 -> l2 += circ.loadKVA
                            2 -> l3 += circ.loadKVA
                        }
                    }
                }
            }
        }

        val l1Cur = (l1 * 1000.0) / 230.0
        val l2Cur = (l2 * 1000.0) / 230.0
        val l3Cur = (l3 * 1000.0) / 230.0
        
        val maxPhase = maxOf(l1, l2, l3)
        val minPhase = minOf(l1, l2, l3)
        val avgPhase = (l1 + l2 + l3) / 3.0
        val unbalance = if (avgPhase > 0.0) ((maxPhase - minPhase) / avgPhase) * 100.0 else 0.0

        return PanelScheduleResult(
            panelName = panelName,
            totalConnected_kVA = Math.round(totalKva * 100.0) / 100.0,
            activeDemand_kW = Math.round(totalKva * demandFactor * 0.8 * 100.0) / 100.0, // default 0.8 pf
            phaseLoads = listOf(
                PanelPhaseLoad("L1", Math.round(l1 * 100.0) / 100.0, Math.round(l1Cur * 10.0) / 10.0),
                PanelPhaseLoad("L2", Math.round(l2 * 100.0) / 100.0, Math.round(l2Cur * 10.0) / 10.0),
                PanelPhaseLoad("L3", Math.round(l3 * 100.0) / 100.0, Math.round(l3Cur * 10.0) / 10.0)
            ),
            unbalancePct = Math.round(unbalance * 10.0) / 10.0
        )
    }

    /**
     * Bottom-up recursive schedule generation.
     * Evaluates child loads fed from panelName (direct circuits + sub-panels),
     * recursively aggregating sub-panel totals.
     */
    fun generateScheduleRecursive(
        panelName: String,
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        visitedChain: List<String> = emptyList(),
        targetCircuitId: Int? = null
    ): PanelScheduleRecursiveResult {
        val targetName = panelName.trim()
        val normalizedTarget = targetName.lowercase()
        val panelKey = if (targetCircuitId != null) "id_$targetCircuitId" else targetName

        // 1. Circular reference protection
        if (visitedChain.any { it.trim().equals(targetName, ignoreCase = true) || it == panelKey }) {
            val chainStr = (visitedChain + targetName).joinToString(" → ")
            return PanelScheduleRecursiveResult(
                panelName = targetName,
                panelCircuit = null,
                childrenCircuits = emptyList(),
                rows = emptyList(),
                aggregatedChildrenLoadKVA = 0.0,
                panelOwnLoadKVA = 0.0,
                directLoadConfirmed = null,
                totalPanelLoadKVA = 0.0,
                hasLoadMismatch = false,
                hasUnconfirmedDirectLoadDialog = false,
                phaseL1KVA = 0.0,
                phaseL2KVA = 0.0,
                phaseL3KVA = 0.0,
                unbalancePct = 0.0,
                hasCircularReference = true,
                errorMessage = "Circular reference detected in panel hierarchy: $chainStr"
            )
        }

        val currentVisited = visitedChain + panelKey

        // 2. Find panel's own CircuitEntity (if exists)
        val panelCircuit = if (targetCircuitId != null) {
            circuits.find { it.id == targetCircuitId } ?: circuits.find { it.circuitName.trim().equals(targetName, ignoreCase = true) }
        } else {
            circuits.find { it.circuitName.trim().equals(targetName, ignoreCase = true) }
        }
        val resolvedPanelName = panelCircuit?.circuitName?.trim() ?: targetName
        val panelOwnLoadKVA = panelCircuit?.loadKVA ?: 0.0
        val directLoadConfirmed = panelCircuit?.directLoadConfirmed

        // 3. Find children fed from this panel
        val directChildren = circuits.filter { child ->
            (panelCircuit != null && child.parentId == panelCircuit.id) ||
                    child.fedFrom.trim().equals(resolvedPanelName, ignoreCase = true)
        }

        val scheduleRows = mutableListOf<PanelScheduleRow>()
        var aggregatedChildrenLoadKVA = 0.0
        var itemCounter = 1

        var l1Sum = 0.0
        var l2Sum = 0.0
        var l3Sum = 0.0

        for (child in directChildren) {
            val pType = child.panelType?.trim()?.uppercase() ?: ""
            val isExplicitPanel = pType in setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")
            val isSubPanel = isExplicitPanel ||
                    circuits.any { it.parentId == child.id || it.fedFrom.trim().equals(child.circuitName.trim(), ignoreCase = true) }

            val rowLoadKVA: Double
            val breakerStr: String
            val cableStr: String

            val sizingRes = sizingResults[child.id]
            breakerStr = sizingRes?.let { "${it.deviceSize_A.toInt()}A ${it.deviceType}" } ?: "-"
            cableStr = sizingRes?.let { it.cableSize_mm2 } ?: (child.overrideCableSize ?: "-")

            if (isSubPanel) {
                // Recursive calculation for sub-panel
                val subResult = generateScheduleRecursive(
                    panelName = child.circuitName,
                    circuits = circuits,
                    sizingResults = sizingResults,
                    visitedChain = currentVisited,
                    targetCircuitId = child.id
                )

                if (subResult.hasCircularReference) {
                    return PanelScheduleRecursiveResult(
                        panelName = targetName,
                        panelCircuit = panelCircuit,
                        childrenCircuits = directChildren,
                        rows = emptyList(),
                        aggregatedChildrenLoadKVA = 0.0,
                        panelOwnLoadKVA = panelOwnLoadKVA,
                        directLoadConfirmed = directLoadConfirmed,
                        totalPanelLoadKVA = 0.0,
                        hasLoadMismatch = false,
                        hasUnconfirmedDirectLoadDialog = false,
                        phaseL1KVA = 0.0,
                        phaseL2KVA = 0.0,
                        phaseL3KVA = 0.0,
                        unbalancePct = 0.0,
                        hasCircularReference = true,
                        errorMessage = subResult.errorMessage
                    )
                }

                rowLoadKVA = subResult.totalPanelLoadKVA
                scheduleRows.add(
                    PanelScheduleRow(
                        itemNo = itemCounter++,
                        description = "Sub-Panel: ${child.circuitName}",
                        circuitId = child.id,
                        isSubPanel = true,
                        subPanelName = child.circuitName,
                        loadKVA = Math.round(rowLoadKVA * 100.0) / 100.0,
                        phases = child.phases,
                        assignedPhase = child.assignedPhase ?: if (child.phases.contains("3P")) "3P" else "L1",
                        breakerRating = breakerStr,
                        cableSize = cableStr
                    )
                )
            } else {
                rowLoadKVA = child.loadKVA
                scheduleRows.add(
                    PanelScheduleRow(
                        itemNo = itemCounter++,
                        description = child.circuitName,
                        circuitId = child.id,
                        isSubPanel = false,
                        subPanelName = null,
                        loadKVA = Math.round(rowLoadKVA * 100.0) / 100.0,
                        phases = child.phases,
                        assignedPhase = child.assignedPhase ?: if (child.phases.contains("3P")) "3P" else "L1",
                        breakerRating = breakerStr,
                        cableSize = cableStr
                    )
                )
            }

            aggregatedChildrenLoadKVA += rowLoadKVA

            // Phase accumulation
            if (child.phases.contains("3P")) {
                val perPhase = rowLoadKVA / 3.0
                l1Sum += perPhase
                l2Sum += perPhase
                l3Sum += perPhase
            } else {
                val p = child.assignedPhase?.trim()?.uppercase()
                when (p) {
                    "L1" -> l1Sum += rowLoadKVA
                    "L2" -> l2Sum += rowLoadKVA
                    "L3" -> l3Sum += rowLoadKVA
                    else -> {
                        when ((itemCounter - 1) % 3) {
                            1 -> l1Sum += rowLoadKVA
                            2 -> l2Sum += rowLoadKVA
                            0 -> l3Sum += rowLoadKVA
                        }
                    }
                }
            }
        }

        // 4. Combine panel's own load based on directLoadConfirmed
        val finalPanelTotalKVA: Double
        val hasLoadMismatch: Boolean
        val hasUnconfirmedDirectLoadDialog: Boolean

        if (directChildren.isEmpty()) {
            // Leaf panel with no sub-children
            finalPanelTotalKVA = panelOwnLoadKVA
            hasLoadMismatch = false
            hasUnconfirmedDirectLoadDialog = false
        } else {
            // Panel has children
            val childrenDiffers = Math.abs(panelOwnLoadKVA - aggregatedChildrenLoadKVA) > 0.001
            when (directLoadConfirmed) {
                true -> {
                    // Confirmed direct load: Add direct load to aggregated total
                    finalPanelTotalKVA = aggregatedChildrenLoadKVA + panelOwnLoadKVA
                    hasLoadMismatch = false
                    hasUnconfirmedDirectLoadDialog = false

                    if (panelOwnLoadKVA > 0.0) {
                        scheduleRows.add(
                            PanelScheduleRow(
                                itemNo = itemCounter++,
                                description = "Direct Load (Other Equipment)",
                                circuitId = panelCircuit?.id,
                                isSubPanel = false,
                                subPanelName = null,
                                loadKVA = Math.round(panelOwnLoadKVA * 100.0) / 100.0,
                                phases = panelCircuit?.phases ?: "3P+N",
                                assignedPhase = "3P",
                                breakerRating = "-",
                                cableSize = "-",
                                isDirectLoadItem = true
                            )
                        )
                        val pPhase = panelOwnLoadKVA / 3.0
                        l1Sum += pPhase
                        l2Sum += pPhase
                        l3Sum += pPhase
                    }
                }
                false -> {
                    // Confirmed estimate: Total is purely aggregated children
                    finalPanelTotalKVA = aggregatedChildrenLoadKVA
                    hasLoadMismatch = childrenDiffers
                    hasUnconfirmedDirectLoadDialog = false
                }
                null -> {
                    // Unconfirmed: If children load differs from recorded load (including 0.0), flag mismatch
                    if (childrenDiffers) {
                        finalPanelTotalKVA = aggregatedChildrenLoadKVA
                        hasLoadMismatch = true
                        hasUnconfirmedDirectLoadDialog = panelOwnLoadKVA > 0.0
                    } else {
                        finalPanelTotalKVA = aggregatedChildrenLoadKVA
                        hasLoadMismatch = false
                        hasUnconfirmedDirectLoadDialog = false
                    }
                }
            }
        }

        val maxP = maxOf(l1Sum, l2Sum, l3Sum)
        val minP = minOf(l1Sum, l2Sum, l3Sum)
        val avgP = (l1Sum + l2Sum + l3Sum) / 3.0
        val unbal = if (avgP > 0.0) ((maxP - minP) / avgP) * 100.0 else 0.0

        return PanelScheduleRecursiveResult(
            panelName = targetName,
            panelCircuit = panelCircuit,
            childrenCircuits = directChildren,
            rows = scheduleRows,
            aggregatedChildrenLoadKVA = Math.round(aggregatedChildrenLoadKVA * 100.0) / 100.0,
            panelOwnLoadKVA = Math.round(panelOwnLoadKVA * 100.0) / 100.0,
            directLoadConfirmed = directLoadConfirmed,
            totalPanelLoadKVA = Math.round(finalPanelTotalKVA * 100.0) / 100.0,
            hasLoadMismatch = hasLoadMismatch,
            hasUnconfirmedDirectLoadDialog = hasUnconfirmedDirectLoadDialog,
            phaseL1KVA = Math.round(l1Sum * 100.0) / 100.0,
            phaseL2KVA = Math.round(l2Sum * 100.0) / 100.0,
            phaseL3KVA = Math.round(l3Sum * 100.0) / 100.0,
            unbalancePct = Math.round(unbal * 10.0) / 10.0,
            hasCircularReference = false,
            errorMessage = null
        )
    }
}

// 4. Procurement Engine Model
data class ProcurementItem(
    val circuitName: String,
    val cableType: String,
    val cableSize: String,
    val glandModel: String,
    val glandDesc: String,
    val lugsType: String,
    val requiredTrayWidth_mm: Double, // Standard commercial tray width (100, 150, 200, 300, 450, 600, etc.)
    val rawCalculatedTrayWidth_mm: Double = 0.0, // Physical calculated arrangement width
    val status: String,
    val projectId: String = "DEFAULT_PROJECT",
    val manufacturer: String = "",
    val productCode: String = "",
    val cableOD_mm: Double = 0.0,
    val coresCount: Int = 3,
    val conductorMaterial: String = "Copper",
    val insulationConstruction: String = "XLPE",
    val isArmoured: Boolean = false,
    val parallelRuns: Int = 1,
    val installedLength_m: Double = 0.0,
    val resolvedCableCatalogCode: String = "N/A",
    val catalogManufacturer: String = "",
    val catalogMatchStatus: String = "MISSING", // EXACT, GENERIC_COMPATIBLE, MISSING
    val matchedGlandCatalogCode: String = "N/A",
    val matchedLugCatalogCode: String = "N/A",
    val matchingBasis: String = "",
    val assumedFields: List<String> = emptyList(),
    val missingFields: List<String> = emptyList(),
    val engineeringNote: String = "",
    val glandQty: Int = 0,
    val lugQty: Int = 0,
    val terminationEnds: Int = 2
)

object ProcurementEngine {
    private fun resolveStandardTrayWidth(calculatedWidth_mm: Double): Double {
        val standardSizes = listOf(100.0, 150.0, 200.0, 300.0, 450.0, 600.0, 750.0, 900.0)
        for (size in standardSizes) {
            if (calculatedWidth_mm <= size) {
                return size
            }
        }
        val overflowMultiplier = kotlin.math.ceil(calculatedWidth_mm / 150.0)
        return overflowMultiplier * 150.0
    }

    fun generateProcurement(circuits: List<CircuitEntity>): List<ProcurementItem> {
        return generateProcurement(circuits, emptyMap())
    }

    fun generateProcurement(
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>,
        explicitTerminationEnds: Map<Int, Int> = emptyMap()
    ): List<ProcurementItem> {
        return circuits.map { circ ->
            val sizing = sizingResults[circ.id]
            val cableSize = sizing?.cableSize_mm2 ?: (circ.overrideCableSize ?: "Auto")
            val sizeVal = parseCableSize(cableSize) ?: 1.5
            val productCode = sizing?.productCode ?: "N/A"
            val cableMaterial = sizing?.cableMaterial ?: (if (circ.cableType.lowercase().contains("al")) "Al" else "Cu")
            val insulation = sizing?.insulation ?: "XLPE"

            val coresCount = getCoresCount(circ.cableType, circ.phases)
            val isArmoured = circ.cableType.contains("SWA", ignoreCase = true) || 
                             circ.cableType.contains("STA", ignoreCase = true) || 
                             circ.cableType.contains("Armoured", ignoreCase = true)
            val conductorMaterial = if (cableMaterial.equals("Al", ignoreCase = true) || circ.cableType.contains("Al", ignoreCase = true)) "Aluminium" else "Copper"

            // 1. Resolve exact Cable Catalog Entry
            val typeCode = Catalog.cableTypes.firstOrNull { it.cableType.lowercase() == circ.cableType.lowercase() }?.code ?: 1
            val exactCatalogEntry = Catalog.cableCatalog.firstOrNull {
                it.mfg.trim().equals(circ.manufacturer.trim(), ignoreCase = true) &&
                it.typeCode == typeCode &&
                Math.abs(it.size - sizeVal) < 0.001
            }

            val catalogMatchStatus = if (exactCatalogEntry != null) "EXACT" else "MISSING"
            val resolvedCableCatalogCode = exactCatalogEntry?.model ?: "N/A"
            val catalogManufacturer = exactCatalogEntry?.mfg ?: ""
            val cableOD = if (sizing != null) sizing.cableOD_mm else (exactCatalogEntry?.od ?: 0.0)

            // Determine explicit or assumed termination ends
            val explicitEnds = explicitTerminationEnds[circ.id]
            val terminationEnds = explicitEnds ?: 2
            val isAssumedEnds = explicitEnds == null

            val assumedFieldsList = mutableListOf<String>()
            val missingFieldsList = mutableListOf<String>()
            val notesList = mutableListOf<String>()

            if (isAssumedEnds) {
                assumedFieldsList.add("terminationEnds")
                notesList.add("Assumed 2 termination ends (Source and Load).")
            }

            val reqArmourGroup = if (isArmoured) "ARMOURED" else "UNARMOURED"
            val normalizedCores = when {
                circ.cableType.contains("1C", ignoreCase = true) -> "1C"
                circ.cableType.contains("3C", ignoreCase = true) && !circ.cableType.contains("3.5C", ignoreCase = true) && !circ.cableType.contains("4C", ignoreCase = true) -> "3C"
                else -> "4C"
            }



            // Gland resolution
            var glandModel = "N/A"
            var glandDesc = "No gland resolved"
            var matchedGlandCatalogCode = "N/A"
            var glandStatus = "APPROVED"
            var glandMatchingBasis = ""

            if (cableOD <= 0.0) {
                glandStatus = "DATA MISSING"
                missingFieldsList.add("cableOD_mm")
                glandDesc = "No gland resolved (missing actual cable OD)"
                notesList.add("Failure Reason: CABLE_OD_MISSING. Actual cable outer diameter is missing or zero.")
            } else {
                val candidateGlands = Catalog.accessories.filter { acc ->
                    acc.category.equals("Cable Gland", ignoreCase = true) &&
                    (if (acc.armourType != null) {
                        if (reqArmourGroup == "ARMOURED") acc.armourType.equals("SWA", ignoreCase = true)
                        else acc.armourType.equals("None", ignoreCase = true)
                    } else {
                        if (reqArmourGroup == "ARMOURED") acc.subCategory.contains("CW", ignoreCase = true)
                        else (acc.subCategory.contains("A2", ignoreCase = true) || !acc.subCategory.contains("CW", ignoreCase = true))
                    })
                }

                if (candidateGlands.isEmpty()) {
                    glandStatus = "DATA MISSING"
                    glandDesc = "No gland resolved"
                    notesList.add("Failure Reason: NO_REQUIRED_ARMOUR_FAMILY. No gland records exist for $reqArmourGroup family.")
                } else {
                    // Match by physical OD first: odMin <= cableOD && cableOD <= odMax
                    val odMatches = candidateGlands.filter { acc ->
                        cableOD >= (acc.odMin - 1e-6) && cableOD <= (acc.odMax + 1e-6)
                    }

                    if (odMatches.isEmpty()) {
                        glandStatus = "DATA MISSING"
                        glandDesc = "No compatible gland found in catalog"
                        notesList.add("No compatible gland found in catalog for OD $cableOD mm and size $sizeVal mm².")
                        notesList.add("Failure Reason: OD_OUTSIDE_AVAILABLE_RANGES. Cable OD $cableOD mm is outside all available $reqArmourGroup gland ranges.")
                        candidateGlands.forEach { cand ->
                            val odPass = cableOD >= (cand.odMin - 1e-6) && cableOD <= (cand.odMax + 1e-6)
                            val sizePass = sizeVal >= cand.sizeMin && sizeVal <= cand.sizeMax
                            val corePass = cand.cores.equals("Any", ignoreCase = true) || cand.cores.contains("3C/4C") || cand.cores.contains(normalizedCores)
                            notesList.add("Rejection Trace '${cand.partNumber}': OD range [${cand.odMin}-${cand.odMax}] (OD Pass: $odPass), Size range [${cand.sizeMin}-${cand.sizeMax}] (Size Pass: $sizePass), Cores '${cand.cores}' (Core Pass: $corePass)")
                        }
                    } else {
                        // Rank OD-compatible candidates:
                        // 1. Nominal size range match preference
                        // 2. Core match preference
                        // 3. Smallest odMax that safely contains cableOD
                        // 4. Smallest OD range width (odMax - odMin)
                        val sorted = odMatches.sortedWith(
                            compareByDescending<AccessoryEntry> { sizeVal >= it.sizeMin && sizeVal <= it.sizeMax }
                                .thenByDescending { it.cores.equals("Any", ignoreCase = true) || it.cores.contains("3C/4C") || it.cores.contains(normalizedCores) }
                                .thenBy { it.odMax }
                                .thenBy { it.odMax - it.odMin }
                                .thenBy { it.partNumber }
                        )

                        val chosenGland = sorted.firstOrNull()
                        if (chosenGland != null) {
                            val sizeMatches = sizeVal >= chosenGland.sizeMin && sizeVal <= chosenGland.sizeMax
                            glandModel = chosenGland.partNumber
                            glandDesc = chosenGland.desc
                            matchedGlandCatalogCode = chosenGland.partNumber

                            if (sizeMatches) {
                                glandStatus = "APPROVED"
                                glandMatchingBasis = "Gland '${chosenGland.partNumber}' matched on: $reqArmourGroup construction, cable OD $cableOD mm, conductor size $sizeVal mm²."
                            } else {
                                glandStatus = "MATCH_FOUND_WITH_SIZE_RANGE_WARNING"
                                glandMatchingBasis = "Matched by actual cable OD ($cableOD mm) within range [${chosenGland.odMin}–${chosenGland.odMax}] mm ($reqArmourGroup)."
                                notesList.add("Matched by actual cable OD. Nominal catalog size range differs (${chosenGland.sizeMin}–${chosenGland.sizeMax} mm² vs cable $sizeVal mm²); verify against selected cable datasheet.")
                                assumedFieldsList.add("sizeRangeMismatchWarning")
                            }
                        }
                    }
                }
            }

            // Lug resolution
            val sizeStr = if (sizeVal % 1.0 == 0.0) sizeVal.toInt().toString() else sizeVal.toString()
            val matchedLugEntry = Catalog.accessories.firstOrNull { acc ->
                acc.category.equals("Cable Lug", ignoreCase = true) &&
                sizeVal >= acc.sizeMin && sizeVal <= acc.sizeMax &&
                (acc.conductor == null || 
                 (conductorMaterial.startsWith("Cu", ignoreCase = true) && acc.conductor.contains("Copper", ignoreCase = true)) ||
                 (conductorMaterial.startsWith("Al", ignoreCase = true) && (acc.conductor.contains("Alum", ignoreCase = true) || acc.conductor.contains("Bi-Metallic", ignoreCase = true))))
            }

            val matchedLugCatalogCode = matchedLugEntry?.partNumber ?: "LUG-${conductorMaterial.take(2).uppercase()}-${sizeVal.toInt()}"
            val lugsType = matchedLugEntry?.desc ?: "$conductorMaterial Compression Lug ($sizeStr mm²)"
            val lugStatus = "APPROVED"
            val lugMatchingBasis = "Matched $conductorMaterial conductor of size $sizeStr mm² to $conductorMaterial Compression Lug."

            // Calculate quantities
            val glandQty = circ.parallelRuns * terminationEnds
            val lugQty = circ.parallelRuns * coresCount * terminationEnds

            val displayGlandModel = if (glandModel != "N/A") "$glandModel x $glandQty" else "N/A"
            val displayGlandDesc = if (glandModel != "N/A") "$glandDesc (Qty: $glandQty)" else glandDesc
            val displayLugsType = "$lugsType x $lugQty"

            val finalOD = if (cableOD > 0.0) cableOD else 10.0
            val rawTrayWidth = circ.parallelRuns * finalOD * 1.4
            val standardTrayWidth = resolveStandardTrayWidth(rawTrayWidth)

            val finalStatus = when {
                cableOD <= 0.0 || circ.manufacturer.isBlank() || circ.cableType.isBlank() || coresCount <= 0 || glandStatus == "DATA MISSING" || catalogMatchStatus == "MISSING" -> "DATA MISSING"
                isAssumedEnds || catalogMatchStatus == "GENERIC_COMPATIBLE" -> "REVIEW REQUIRED"
                else -> "APPROVED"
            }

            if (circ.manufacturer.isBlank()) missingFieldsList.add("manufacturer")
            if (circ.cableType.isBlank()) missingFieldsList.add("cableType")
            if (coresCount <= 0) missingFieldsList.add("coresCount")

            ProcurementItem(
                circuitName = circ.circuitName,
                cableType = circ.cableType,
                cableSize = cableSize,
                glandModel = displayGlandModel,
                glandDesc = displayGlandDesc,
                lugsType = displayLugsType,
                requiredTrayWidth_mm = standardTrayWidth,
                rawCalculatedTrayWidth_mm = Math.round(rawTrayWidth * 10.0) / 10.0,
                status = finalStatus,
                projectId = circ.projectId,
                manufacturer = circ.manufacturer,
                productCode = productCode,
                cableOD_mm = cableOD,
                coresCount = coresCount,
                conductorMaterial = conductorMaterial,
                insulationConstruction = insulation,
                isArmoured = isArmoured,
                parallelRuns = circ.parallelRuns,
                installedLength_m = circ.length_m * circ.parallelRuns,
                resolvedCableCatalogCode = resolvedCableCatalogCode,
                catalogManufacturer = catalogManufacturer,
                catalogMatchStatus = catalogMatchStatus,
                matchedGlandCatalogCode = matchedGlandCatalogCode,
                matchedLugCatalogCode = matchedLugCatalogCode,
                matchingBasis = if (glandMatchingBasis.isNotEmpty()) "$glandMatchingBasis; $lugMatchingBasis" else lugMatchingBasis,
                assumedFields = assumedFieldsList,
                missingFields = missingFieldsList,
                engineeringNote = notesList.joinToString(" "),
                glandQty = glandQty,
                lugQty = lugQty,
                terminationEnds = terminationEnds
            )
        }
    }


    private fun parseCableSize(value: String?): Double? {
        if (value == null) return null
        val match = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""").find(value)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun getCoresCount(cableType: String, phases: String): Int {
        val match = Regex("""([0-9]+)\s*[cC]""").find(cableType)
        if (match != null) {
            return match.groupValues[1].toInt()
        }
        return when {
            phases.contains("3P+N", ignoreCase = true) -> 4
            phases.contains("3P", ignoreCase = true) -> 3
            phases.contains("1P+N", ignoreCase = true) -> 2
            else -> 3
        }
    }
}

// 5. Lighting Engine (Lux Calculation)
data class LightingCalculationInput(
    val length: Double,
    val width: Double,
    val height: Double,
    val targetLux: Double,
    val maintenanceFactor: Double = 0.8, // LLF
    val utilizationFactor: Double = 0.65, // UF
    val lumensPerFixture: Double = 3200.0,
    val wattsPerFixture: Double = 28.0
)

data class LightingResult(
    val area_m2: Double,
    val totalLumensNeeded: Double,
    val fixturesCount: Int,
    val actualLux: Double,
    val totalPower_W: Double,
    val powerDensity_W_m2: Double
)

object LightingEngine {
    fun calculate(input: LightingCalculationInput): LightingResult {
        if (input.lumensPerFixture <= 0.0 || input.utilizationFactor <= 0.0 || input.maintenanceFactor <= 0.0) {
            return LightingResult(0.0, 0.0, 0, 0.0, 0.0, 0.0)
        }
        val area = input.length * input.width
        // Lumens = Lux * Area / (UF * MF)
        val totalLumens = (input.targetLux * area) / (input.utilizationFactor * input.maintenanceFactor)
        
        val count = ceil(totalLumens / input.lumensPerFixture).toInt()
        val totalPower = count * input.wattsPerFixture
        val powerDensity = if (area > 0.0) totalPower / area else 0.0
        val actualLux = if (area > 0.0) (count * input.lumensPerFixture * input.utilizationFactor * input.maintenanceFactor) / area else 0.0

        return LightingResult(
            area_m2 = Math.round(area * 100.0) / 100.0,
            totalLumensNeeded = Math.round(totalLumens * 10.0) / 10.0,
            fixturesCount = count,
            actualLux = Math.round(actualLux * 10.0) / 10.0,
            totalPower_W = totalPower,
            powerDensity_W_m2 = Math.round(powerDensity * 100.0) / 100.0
        )
    }
}
