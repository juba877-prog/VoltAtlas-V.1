package com.example.engine

import com.example.data.CircuitEntity
import com.example.hierarchy.HierarchyResolver

/**
 * Result of aggregating the short-circuit Isc over the circuits that feed one ATS.
 *
 * @param maxResolvedIsc highest usable Isc among circuits with a real computed value.
 * @param incomplete     true when at least one assigned circuit could NOT be resolved (missing
 *                       result, DATA MISSING, or non-positive/NaN Isc). When true,
 *                       [maxResolvedIsc] is a PARTIAL maximum and must not be treated as the
 *                       governing fault level.
 * @param missingCircuitIds ids of the unresolved circuits, for diagnostics.
 */
data class AtsIscAggregation(
    val maxResolvedIsc: Double,
    val incomplete: Boolean,
    val missingCircuitIds: List<Int>
)

/**
 * Outcome of deciding the Isc an ATS must be sized against.
 *
 * @param iscKA       usable fault level, or 0.0 when [dataMissing] is true.
 * @param dataMissing true when no defensible basis exists. There is deliberately NO silent
 *                    fallback to a partial maximum: an understated Icw is a safety defect, so it
 *                    must surface as DATA MISSING rather than a plausible number.
 * @param basis       human-readable provenance, surfaced in the ATS result.
 */
data class AtsIscDecision(
    val iscKA: Double,
    val dataMissing: Boolean,
    val basis: String
)

/**
 * Pure resolution of the fault level an ATS must be sized against.
 *
 * Kept in the engine layer (not the ViewModel) so the calculation boundary owns all engineering
 * decisions and so the rules are unit-testable without Android or a database.
 */
object AtsIscResolver {

    /**
     * Aggregates ONLY the circuits explicitly assigned to [atsId].
     *
     * A project may hold several ATS instances; aggregating across all of them would mix unrelated
     * sources into one number. Attribution is explicit assignment only, matching
     * [AtsEngine.calculateAll].
     */
    fun aggregateFor(
        atsId: String,
        circuits: List<CircuitEntity>,
        shortCircuitResults: Map<Int, ShortCircuitResult>
    ): AtsIscAggregation {
        var maxResolved = 0.0
        val missing = mutableListOf<Int>()
        for (c in circuits) {
            if (c.assignedAtsId != atsId) continue
            val res = shortCircuitResults[c.id]
            if (res == null) { missing.add(c.id); continue }
            if (res.status == "DATA MISSING") { missing.add(c.id); continue }
            val ka = res.iscKA
            if (ka <= 0.0 || ka.isNaN()) { missing.add(c.id); continue }
            if (ka > maxResolved) maxResolved = ka
        }
        return AtsIscAggregation(maxResolved, missing.isNotEmpty(), missing)
    }

    /**
     * Resolves the transformer that feeds [atsId] by walking each assigned feeder's ancestor chain.
     *
     * Traversal goes through [HierarchyResolver.resolveAncestors] so the shared cycle/self-parent
     * guard is used rather than a private loop: a cyclic hierarchy is UNSOLVABLE, not something to
     * spin on or silently truncate.
     *
     * Returns null when it cannot be resolved unambiguously: no feeder, a cyclic chain, or feeders
     * that reach more than one distinct transformer. It never falls back to "first transformer in
     * the map", which in a multi-instance power system would size the ATS against the wrong unit.
     */
    fun resolveFeedTransformerId(
        atsId: String,
        circuits: List<CircuitEntity>,
        circuitParents: Map<Int, Int>
    ): String? {
        val feeders = circuits.filter { it.assignedAtsId == atsId }
        if (feeders.isEmpty()) return null
        val byId = circuits.associateBy { it.id }
        val found = linkedSetOf<String>()

        for (feeder in feeders) {
            when (val resolution = HierarchyResolver.resolveAncestors(feeder.id) { circuitParents[it] }) {
                is HierarchyResolver.AncestorResolution.CycleDetected -> return null
                is HierarchyResolver.AncestorResolution.Linear -> {
                    // The feeder itself is the first node; the nearest non-blank transformer wins.
                    val transformer = resolution.path
                        .mapNotNull { byId[it] }
                        .firstOrNull { it.transformerId.isNotBlank() }
                    if (transformer != null) found.add(transformer.transformerId)
                }
            }
        }
        // Exactly one distinct transformer across ALL feeders, else the source is ambiguous.
        return if (found.size == 1) found.first() else null
    }

    /**
     * Decides the Isc the ATS must be sized against. Never silently falls back to a partial max.
     *
     * @param basisNoteSuffix appended to the [AtsIscDecision.basis] of the upper-bound branch, used
     *                        to state honestly which sources that bound does and does not include.
     */
    fun decide(
        aggregation: AtsIscAggregation,
        transformerIscKA: Double,
        basisNoteSuffix: String = ""
    ): AtsIscDecision {
        if (!aggregation.incomplete) {
            return AtsIscDecision(
                iscKA = aggregation.maxResolvedIsc,
                dataMissing = aggregation.maxResolvedIsc <= 0.0,
                basis = "max of resolved ATS circuits"
            )
        }
        if (transformerIscKA > 0.0 && !transformerIscKA.isNaN()) {
            return AtsIscDecision(
                iscKA = transformerIscKA,
                dataMissing = false,
                basis = "requiredIcw based on transformer-terminal Isc" +
                    (if (basisNoteSuffix.isNotBlank()) " ($basisNoteSuffix)" else "") +
                    "; ${aggregation.missingCircuitIds.size} ATS circuit(s) unresolved"
            )
        }
        return AtsIscDecision(
            iscKA = 0.0,
            dataMissing = true,
            basis = "ATS fault level unresolved: ${aggregation.missingCircuitIds.size} " +
                "circuit(s) DATA MISSING and no feeding-transformer Isc available"
        )
    }
}
