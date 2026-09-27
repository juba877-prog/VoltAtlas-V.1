package com.example.ui.screens.circuits

import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import java.util.Locale

/**
 * Standard panel types eligible for panel schedule actions and panel filters.
 */
val PANEL_TYPES: Set<String> = setOf("MDB", "SMDB", "DB", "MCC", "PANEL", "OTHER")

/**
 * Pure presentation mapper and deterministic aggregation utility for the Circuits screen.
 * This class performs NO electrical calculations and preserves raw engine outputs verbatim.
 */
object CircuitUiModelMapper {

    /**
     * Resolves the presentation status NON-LOSSILY from the typed [SizingResult].
     * Missing sizing result maps to [CircuitUiStatus.STANDBY]. Every engine
     * status keeps its exact typed label on the card: PASS, WARNING, FAIL,
     * INCOMPLETE, ERROR. No substring matching, no lossy coercion.
     * (C2 whole-project summary aggregation is intentionally unaffected.)
     */
    fun resolveCircuitStatus(sizingResult: SizingResult?): CircuitUiStatus {
        if (sizingResult == null) return CircuitUiStatus.STANDBY
        return when (sizingResult.resultStatus) {
            SizingResultStatus.PASS -> CircuitUiStatus.PASS
            SizingResultStatus.WARNING -> CircuitUiStatus.WARNING
            SizingResultStatus.FAIL -> CircuitUiStatus.FAIL
            SizingResultStatus.INCOMPLETE -> CircuitUiStatus.INCOMPLETE
            SizingResultStatus.ERROR -> CircuitUiStatus.ERROR
        }
    }

    /**
     * Resolves the non-interactive hierarchy breadcrumb safely from resolved parent name.
     */
    fun resolveBreadcrumb(circuit: CircuitEntity, parentName: String?): String {
        if (circuit.parentId != null && parentName != null) {
            return "$parentName > ${circuit.circuitName}"
        }
        if (circuit.fedFrom.isNotBlank() && !circuit.fedFrom.equals(circuit.circuitName, ignoreCase = true)) {
            return "${circuit.fedFrom} > ${circuit.circuitName}"
        }
        return circuit.circuitName.ifBlank { "—" }
    }

    /**
     * Resolves the non-interactive hierarchy breadcrumb safely from verified relationships.
     */
    fun resolveBreadcrumb(circuit: CircuitEntity, allCircuits: List<CircuitEntity>): String {
        val parentName = if (circuit.parentId != null) {
            allCircuits.firstOrNull { it.id == circuit.parentId && it.id != circuit.id }?.circuitName
        } else null
        return resolveBreadcrumb(circuit, parentName)
    }

    /**
     * Maps domain entities and engine results into an immutable [CircuitCardUiModel].
     */
    fun mapToCardUiModel(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        shortCircuitResult: ShortCircuitResult?,
        parentName: String?,
        hasChildren: Boolean
    ): CircuitCardUiModel {
        val status = resolveCircuitStatus(sizingResult)
        val isScheduleEligible = (circuit.panelType?.uppercase(Locale.ROOT) in PANEL_TYPES) || hasChildren

        // Cable Size
        val rawCableSize = sizingResult?.cableSize_mm2?.trim() ?: ""
        val cableSizeDisplay = if (rawCableSize.isNotBlank()) {
            if (rawCableSize.contains("mm²") || rawCableSize.contains("mm2")) {
                rawCableSize.replace("mm2", "mm²")
            } else {
                "$rawCableSize mm²"
            }
        } else {
            "—"
        }

        // Breadcrumb
        val breadcrumbDisplay = resolveBreadcrumb(circuit, parentName)

        // Load kVA (Real zero 0.0 is valid data and renders as "0.0 kVA")
        val loadDisplay = if (circuit.loadKVA.isNaN() || circuit.loadKVA.isInfinite() || circuit.loadKVA < 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.1f kVA", circuit.loadKVA)
        }

        // Design Current Ib
        val ibDisplay = if (sizingResult?.Ib_A != null && !sizingResult.Ib_A.isNaN() && sizingResult.Ib_A > 0.0) {
            String.format(Locale.US, "%.1f A", sizingResult.Ib_A)
        } else {
            "—"
        }

        // Breaker Rating In / Device
        val inDisplay = if (sizingResult?.deviceSize_A != null && !sizingResult.deviceSize_A.isNaN() && sizingResult.deviceSize_A > 0.0) {
            val devType = sizingResult.deviceType.ifBlank { "MCCB" }
            "${sizingResult.deviceSize_A.toInt()}A $devType"
        } else {
            "—"
        }

        // Voltage Drop
        val vdDisplay = if (sizingResult?.cumulativeVDpct != null && !sizingResult.cumulativeVDpct.isNaN() && !sizingResult.cumulativeVDpct.isInfinite() && sizingResult.cumulativeVDpct >= 0.0) {
            String.format(Locale.US, "%.2f%%", sizingResult.cumulativeVDpct)
        } else {
            "—"
        }

        // Voltage Drop Limit
        val vdLimitDisplay = if (circuit.maxVDpct.isNaN() || circuit.maxVDpct.isInfinite() || circuit.maxVDpct <= 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.1f%%", circuit.maxVDpct * 100.0)
        }

        // Thermal Loading / Utilization
        val loadingDisplay = if (sizingResult?.utilizationPct != null && !sizingResult.utilizationPct.isNaN() && !sizingResult.utilizationPct.isInfinite() && sizingResult.utilizationPct >= 0.0) {
            String.format(Locale.US, "%.1f%%", sizingResult.utilizationPct)
        } else {
            "—"
        }

        // Cable Derated Capacity Iz
        val izDisplay = if (sizingResult?.capacity_derated_Iz_A != null && !sizingResult.capacity_derated_Iz_A.isNaN() && !sizingResult.capacity_derated_Iz_A.isInfinite() && sizingResult.capacity_derated_Iz_A > 0.0) {
            String.format(Locale.US, "%.1f A", sizingResult.capacity_derated_Iz_A)
        } else {
            "—"
        }

        // Short Circuit 3-Phase
        val isc3phDisplay = if (shortCircuitResult != null && !shortCircuitResult.iscKA.isNaN() && shortCircuitResult.iscKA > 0.0) {
            String.format(Locale.US, "%.1f kA", shortCircuitResult.iscKA)
        } else {
            "—"
        }

        // Breaker Breaking Capacity Icu
        val icuDisplay = if (sizingResult?.deviceBreakingKA != null && !sizingResult.deviceBreakingKA.isNaN() && !sizingResult.deviceBreakingKA.isInfinite() && sizingResult.deviceBreakingKA > 0.0) {
            String.format(Locale.US, "%.0f kA", sizingResult.deviceBreakingKA)
        } else {
            "—"
        }

        // Footer Spec
        val validLength = circuit.length_m > 0.0
        val lengthStr = if (validLength) {
            String.format(Locale.US, "%.0fm", circuit.length_m)
        } else {
            "—"
        }

        val footerSpecDisplay = when {
            circuit.cableType.isNotBlank() && validLength -> "${circuit.cableType} • Length: $lengthStr"
            circuit.cableType.isNotBlank() && !validLength -> "${circuit.cableType} • Length: —"
            circuit.cableType.isBlank() && validLength -> "Length: $lengthStr"
            else -> "Length: —"
        }

        val safeVd: Double? = sizingResult?.cumulativeVDpct?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0.0 }
        val safeUtilization: Double? = sizingResult?.utilizationPct?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0.0 }

        val isVdOk = if (sizingResult != null) {
            sizingResult.isVoltageDropOk
        } else {
            true
        }

        val isLoadingOk = if (sizingResult != null) {
            sizingResult.isAmpacityOk
        } else {
            true
        }

        val isIscOk = if (shortCircuitResult != null &&
            shortCircuitResult.status != "DATA MISSING" &&
            shortCircuitResult.iscKA > 0.0 &&
            sizingResult?.deviceBreakingKA != null &&
            !sizingResult.deviceBreakingKA.isNaN()
        ) {
            shortCircuitResult.iscKA <= sizingResult.deviceBreakingKA
        } else {
            false
        }

        val phasesDisplay = circuit.phases.ifBlank { "3P+N" }

        return CircuitCardUiModel(
            id = circuit.id,
            circuitName = circuit.circuitName.ifBlank { "—" },
            isScheduleEligible = isScheduleEligible,
            cableSizeDisplay = cableSizeDisplay,
            status = status,
            breadcrumbDisplay = breadcrumbDisplay,
            loadDisplay = loadDisplay,
            ibDisplay = ibDisplay,
            inDisplay = inDisplay,
            vdDisplay = vdDisplay,
            vdLimitDisplay = vdLimitDisplay,
            loadingDisplay = loadingDisplay,
            izDisplay = izDisplay,
            isc3phDisplay = isc3phDisplay,
            icuDisplay = icuDisplay,
            footerSpecDisplay = footerSpecDisplay,
            rawLoadKVA = circuit.loadKVA,
            rawCumulativeVDpct = safeVd,
            rawUtilizationPct = safeUtilization,
            rawFedFrom = circuit.fedFrom,
            rawParentId = circuit.parentId,
            rawPanelType = circuit.panelType,
            rawCableType = circuit.cableType,
            rawLoadType = circuit.loadType,
            phasesDisplay = phasesDisplay,
            isVdOk = isVdOk,
            isLoadingOk = isLoadingOk,
            isIscOk = isIscOk
        )
    }

    /**
     * Compatibility overload accepting the entire [allCircuits] list.
     */
    fun mapToCardUiModel(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        shortCircuitResult: ShortCircuitResult?,
        allCircuits: List<CircuitEntity>,
        hasChildren: Boolean
    ): CircuitCardUiModel {
        val parentName = if (circuit.parentId != null) {
            allCircuits.firstOrNull { it.id == circuit.parentId && it.id != circuit.id }?.circuitName
        } else null
        return mapToCardUiModel(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = shortCircuitResult,
            parentName = parentName,
            hasChildren = hasChildren
        )
    }

    /**
     * Aggregates summary health metrics evaluated across the entire project collection.
     * Exactly 5 project health metrics: Total, Pass, Fail, Standby, Max Vd, plus Warning count.
     */
    fun mapToSummaryUiModel(
        allCircuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>
    ): CircuitsSummaryUiModel {
        var passCount = 0
        var failCount = 0
        var warningCount = 0
        var standbyCount = 0

        var maxVd: Double? = null

        for (circuit in allCircuits) {
            val sizing = sizingResults[circuit.id]
            if (sizing == null) {
                standbyCount++
            } else {
                when (sizing.resultStatus) {
                    SizingResultStatus.PASS -> passCount++
                    SizingResultStatus.FAIL -> failCount++
                    SizingResultStatus.WARNING -> {
                        warningCount++
                        standbyCount++
                    }
                    SizingResultStatus.INCOMPLETE,
                    SizingResultStatus.ERROR -> standbyCount++
                }

                val vd = sizing.cumulativeVDpct
                if (!vd.isNaN() && !vd.isInfinite() && vd >= 0.0) {
                    if (maxVd == null || vd > maxVd) {
                        maxVd = vd
                    }
                }
            }
        }

        val maxVdDisplay = if (maxVd != null) {
            String.format(Locale.US, "%.2f%%", maxVd)
        } else {
            "—"
        }

        return CircuitsSummaryUiModel(
            totalCount = allCircuits.size,
            passCount = passCount,
            failCount = failCount,
            standbyCount = standbyCount,
            maxVdDisplay = maxVdDisplay,
            rawMaxVdPct = maxVd,
            warningCount = warningCount
        )
    }

    /**
     * Filters circuit card items in memory according to search query, Level predicate, and Status predicate.
     */
    fun filterCircuits(
        cards: List<CircuitCardUiModel>,
        query: String,
        levelFilter: CircuitLevelFilter,
        statusFilter: CircuitStatusFilter,
        childrenLookup: (String) -> Boolean
    ): List<CircuitCardUiModel> {
        val trimmedQuery = query.trim().lowercase(Locale.ROOT)

        return cards.filter { card ->
            // Search query matching
            val matchesQuery = if (trimmedQuery.isEmpty()) {
                true
            } else {
                card.circuitName.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                    card.rawFedFrom.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                    card.rawCableType.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                    card.rawLoadType.lowercase(Locale.ROOT).contains(trimmedQuery)
            }

            if (!matchesQuery) return@filter false

            // Level filter matching
            val matchesLevel = when (levelFilter) {
                CircuitLevelFilter.ALL -> true
                CircuitLevelFilter.MAIN_FEEDERS -> {
                    card.rawFedFrom.equals("Transformer", ignoreCase = true) || card.rawParentId == null
                }
                CircuitLevelFilter.DOWNSTREAM -> {
                    !card.rawFedFrom.equals("Transformer", ignoreCase = true) && card.rawParentId != null
                }
                CircuitLevelFilter.PANELS -> {
                    val pType = card.rawPanelType?.uppercase(Locale.ROOT)
                    (pType != null && pType in PANEL_TYPES) || childrenLookup(card.circuitName)
                }
            }

            if (!matchesLevel) return@filter false

            // Status filter matching: Strictly ALL, PASS, FAIL, STANDBY
            val matchesStatus = when (statusFilter) {
                CircuitStatusFilter.ALL -> true
                CircuitStatusFilter.PASS -> card.status == CircuitUiStatus.PASS
                CircuitStatusFilter.FAIL -> card.status == CircuitUiStatus.FAIL
                CircuitStatusFilter.STANDBY -> card.status == CircuitUiStatus.STANDBY
            }

            matchesStatus
        }
    }

    /**
     * Deterministically sorts circuit cards using typed numeric fields and enforcing circuit.id tie-breakers.
     */
    fun sortCircuits(
        cards: List<CircuitCardUiModel>,
        sortOrder: CircuitSortOrder
    ): List<CircuitCardUiModel> {
        return when (sortOrder) {
            CircuitSortOrder.SOURCE_ORDER -> cards
            CircuitSortOrder.NAME_ASC -> {
                cards.sortedWith(
                    compareBy<CircuitCardUiModel> {
                        val name = it.circuitName.trim()
                        if (name.isEmpty() || name == "—") "\uFFFF" else name.lowercase(Locale.ROOT)
                    }.thenBy { it.id }
                )
            }
            CircuitSortOrder.LOAD_DESC -> {
                cards.sortedWith(
                    compareByDescending<CircuitCardUiModel> {
                        if (it.rawLoadKVA.isNaN() || it.rawLoadKVA.isInfinite()) -Double.MAX_VALUE else it.rawLoadKVA
                    }.thenBy { it.id }
                )
            }
            CircuitSortOrder.VD_DESC -> {
                cards.sortedWith(
                    compareByDescending<CircuitCardUiModel> {
                        val vd = it.rawCumulativeVDpct
                        if (vd == null || vd.isNaN() || vd.isInfinite()) -Double.MAX_VALUE else vd
                    }.thenBy { it.id }
                )
            }
            CircuitSortOrder.STATUS -> {
                cards.sortedWith(
                    compareBy<CircuitCardUiModel> {
                        when (it.status) {
                            // Deterministic: Fail first, Standby last, id tie-breaker below.
                            CircuitUiStatus.FAIL -> 0
                            CircuitUiStatus.WARNING -> 1
                            CircuitUiStatus.ERROR -> 2
                            CircuitUiStatus.INCOMPLETE -> 3
                            CircuitUiStatus.PASS -> 4
                            CircuitUiStatus.STANDBY -> 5
                        }
                    }.thenByDescending {
                        if (it.rawLoadKVA.isNaN() || it.rawLoadKVA.isInfinite()) -Double.MAX_VALUE else it.rawLoadKVA
                    }.thenBy { it.id }
                )
            }
        }
    }
}
