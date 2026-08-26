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
     * Resolves the presentation status exhaustively from the non-null typed [SizingResultStatus].
     * Returns [CircuitUiStatus.STANDBY] strictly when no sizing result exists.
     */
    fun resolveCircuitStatus(sizingResult: SizingResult?): CircuitUiStatus {
        if (sizingResult == null) return CircuitUiStatus.STANDBY
        return when (sizingResult.resultStatus) {
            SizingResultStatus.PASS -> CircuitUiStatus.PASS
            SizingResultStatus.FAIL -> CircuitUiStatus.FAIL
            SizingResultStatus.WARNING -> CircuitUiStatus.WARNING
            SizingResultStatus.INCOMPLETE -> CircuitUiStatus.INCOMPLETE
            SizingResultStatus.ERROR -> CircuitUiStatus.ERROR
        }
    }

    /**
     * Resolves the non-interactive hierarchy breadcrumb safely from verified relationships.
     */
    fun resolveBreadcrumb(circuit: CircuitEntity, allCircuits: List<CircuitEntity>): String {
        if (circuit.parentId != null) {
            val parent = allCircuits.firstOrNull { it.id == circuit.parentId }
            if (parent != null && parent.id != circuit.id) {
                return "${parent.circuitName} > ${circuit.circuitName}"
            }
        }
        if (circuit.fedFrom.isNotBlank() && !circuit.fedFrom.equals(circuit.circuitName, ignoreCase = true)) {
            return "${circuit.fedFrom} > ${circuit.circuitName}"
        }
        return circuit.circuitName.ifBlank { "—" }
    }

    /**
     * Maps domain entities and engine results into an immutable [CircuitCardUiModel].
     */
    fun mapToCardUiModel(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        shortCircuitResult: ShortCircuitResult?,
        allCircuits: List<CircuitEntity>,
        hasChildren: Boolean
    ): CircuitCardUiModel {
        val status = resolveCircuitStatus(sizingResult)
        val isScheduleEligible = (circuit.panelType?.uppercase(Locale.ROOT) in PANEL_TYPES) || hasChildren

        // Cable Size
        val cableSizeDisplay = if (!sizingResult?.cableSize_mm2.isNullOrBlank()) {
            "${sizingResult?.cableSize_mm2} mm²"
        } else {
            "—"
        }

        // Breadcrumb
        val breadcrumbDisplay = resolveBreadcrumb(circuit, allCircuits)

        // Load kVA (Real zero 0.0 is valid data and renders as "0.0 kVA")
        val loadDisplay = if (circuit.loadKVA.isNaN() || circuit.loadKVA.isInfinite() || circuit.loadKVA < 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.1f kVA", circuit.loadKVA)
        }

        // Ib
        val ibDisplay = if (sizingResult == null || sizingResult.Ib_A.isNaN() || sizingResult.Ib_A.isInfinite() || sizingResult.Ib_A < 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.1f A", sizingResult.Ib_A)
        }

        // In (Breaker rating & type)
        val inDisplay = if (sizingResult == null || sizingResult.deviceSize_A.isNaN() || sizingResult.deviceSize_A <= 0.0) {
            "—"
        } else {
            val devType = sizingResult.deviceType.ifBlank { "MCB" }
            "${sizingResult.deviceSize_A.toInt()} A ($devType)"
        }

        // Voltage Drop (cumulative % and limit %)
        val vdDisplay = if (sizingResult == null || sizingResult.cumulativeVDpct.isNaN() || sizingResult.cumulativeVDpct.isInfinite() || sizingResult.cumulativeVDpct < 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.2f%%", sizingResult.cumulativeVDpct)
        }

        val vdLimitDisplay = if (circuit.maxVDpct.isNaN() || circuit.maxVDpct.isInfinite() || circuit.maxVDpct <= 0.0) {
            "Limit —"
        } else {
            "Limit ${(circuit.maxVDpct * 100).toInt()}%"
        }

        // Loading (utilization %) and Iz
        val loadingDisplay = if (sizingResult == null || sizingResult.utilizationPct.isNaN() || sizingResult.utilizationPct.isInfinite() || sizingResult.utilizationPct < 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.1f%%", sizingResult.utilizationPct)
        }

        val izDisplay = if (sizingResult == null || sizingResult.capacity_derated_Iz_A.isNaN() || sizingResult.capacity_derated_Iz_A.isInfinite() || sizingResult.capacity_derated_Iz_A <= 0.0) {
            "Iz: —"
        } else {
            String.format(Locale.US, "Iz: %.1f A", sizingResult.capacity_derated_Iz_A)
        }

        // Short-Circuit Current Isc(3ph) and Breaker Breaking Capacity Icu
        val isc3phDisplay = if (shortCircuitResult == null || shortCircuitResult.iscKA.isNaN() || shortCircuitResult.iscKA.isInfinite() || shortCircuitResult.iscKA < 0.0) {
            "—"
        } else {
            String.format(Locale.US, "%.2f kA", shortCircuitResult.iscKA)
        }

        val icuDisplay = if (sizingResult == null || sizingResult.deviceBreakingKA.isNaN() || sizingResult.deviceBreakingKA.isInfinite() || sizingResult.deviceBreakingKA <= 0.0) {
            "Icu: —"
        } else {
            String.format(Locale.US, "Icu: %.1f kA", sizingResult.deviceBreakingKA)
        }

        // Footer Spec
        val validLength = !circuit.length_m.isNaN() && !circuit.length_m.isInfinite() && circuit.length_m >= 0.0
        val lengthStr = if (validLength) {
            String.format(Locale.US, "%.1f m", circuit.length_m)
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
            rawLoadType = circuit.loadType
        )
    }

    /**
     * Aggregates summary health metrics evaluated across the entire project collection.
     */
    fun mapToSummaryUiModel(
        allCircuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult>
    ): CircuitsSummaryUiModel {
        var passCount = 0
        var warningCount = 0
        var failCount = 0
        var standbyCount = 0
        var incompleteCount = 0
        var errorCount = 0

        var maxVd: Double? = null

        for (circuit in allCircuits) {
            val sizing = sizingResults[circuit.id]
            if (sizing == null) {
                standbyCount++
            } else {
                when (sizing.resultStatus) {
                    SizingResultStatus.PASS -> passCount++
                    SizingResultStatus.WARNING -> warningCount++
                    SizingResultStatus.FAIL -> failCount++
                    SizingResultStatus.INCOMPLETE -> incompleteCount++
                    SizingResultStatus.ERROR -> errorCount++
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
            warningCount = warningCount,
            failCount = failCount,
            standbyCount = standbyCount,
            incompleteCount = incompleteCount,
            errorCount = errorCount,
            maxVdDisplay = maxVdDisplay,
            rawMaxVdPct = maxVd
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

            // Status filter matching
            val matchesStatus = when (statusFilter) {
                CircuitStatusFilter.ALL -> true
                CircuitStatusFilter.PASS -> card.status == CircuitUiStatus.PASS
                CircuitStatusFilter.WARNING -> card.status == CircuitUiStatus.WARNING
                CircuitStatusFilter.FAIL -> card.status == CircuitUiStatus.FAIL
                CircuitStatusFilter.STANDBY -> {
                    card.status == CircuitUiStatus.STANDBY ||
                        card.status == CircuitUiStatus.INCOMPLETE ||
                        card.status == CircuitUiStatus.ERROR
                }
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
                            CircuitUiStatus.FAIL -> 0
                            CircuitUiStatus.ERROR -> 1
                            CircuitUiStatus.INCOMPLETE -> 2
                            CircuitUiStatus.WARNING -> 3
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
