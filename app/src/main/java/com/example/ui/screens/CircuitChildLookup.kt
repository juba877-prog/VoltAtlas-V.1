package com.example.ui.screens

import com.example.data.CircuitEntity

/**
 * Precomputed summary of child circuits fed from a given parent circuit or distribution source.
 */
data class CircuitChildSummary(
    val children: List<CircuitEntity>,
    val totalLoadKVA: Double
)

/**
 * High-performance, immutable lookup for parent-child circuit hierarchies.
 * Precomputes normalized grouping and total child load in a single O(N) pass,
 * replacing repeated O(N^2) list scans during lazy list rendering.
 */
class CircuitChildLookup private constructor(
    private val summaryByParent: Map<String, CircuitChildSummary>
) {
    /**
     * Returns all child circuits fed from [parentName], matching case-insensitively and ignoring whitespace.
     */
    fun getChildren(parentName: String?): List<CircuitEntity> {
        if (parentName.isNullOrBlank()) return emptyList()
        val key = parentName.trim().lowercase()
        return summaryByParent[key]?.children ?: emptyList()
    }

    /**
     * Returns true if there is at least one child circuit fed from [parentName].
     */
    fun hasChildren(parentName: String?): Boolean {
        if (parentName.isNullOrBlank()) return false
        val key = parentName.trim().lowercase()
        return summaryByParent[key]?.children?.isNotEmpty() == true
    }

    /**
     * Returns the precomputed sum of connected loads (kVA) for all children of [parentName].
     */
    fun getChildrenTotalLoad(parentName: String?): Double {
        if (parentName.isNullOrBlank()) return 0.0
        val key = parentName.trim().lowercase()
        return summaryByParent[key]?.totalLoadKVA ?: 0.0
    }

    companion object {
        /**
         * Builds an immutable [CircuitChildLookup] in a single linear pass (O(N)) over [circuits].
         */
        fun build(circuits: List<CircuitEntity>): CircuitChildLookup {
            if (circuits.isEmpty()) {
                return CircuitChildLookup(emptyMap())
            }
            val grouped = HashMap<String, MutableList<CircuitEntity>>()
            for (circuit in circuits) {
                val fedFromKey = circuit.fedFrom.trim().lowercase()
                if (fedFromKey.isNotEmpty()) {
                    grouped.getOrPut(fedFromKey) { ArrayList() }.add(circuit)
                }
            }
            val summaries = HashMap<String, CircuitChildSummary>(grouped.size)
            for ((key, list) in grouped) {
                summaries[key] = CircuitChildSummary(
                    children = list,
                    totalLoadKVA = list.sumOf { it.loadKVA }
                )
            }
            return CircuitChildLookup(summaries)
        }
    }
}
