package com.example.ui.screens.circuits

import com.example.data.CircuitEntity
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult

/**
 * Thread-confined / Compose-local memoization cache for [CircuitCardUiModel] instances.
 * Eliminates whole-list recomputation by preserving and reusing previously computed models
 * when a circuit's entity, sizing result, short circuit result, parent breadcrumb, and child status
 * have not changed.
 */
class CircuitCardModelCache {
    private val cache = mutableMapOf<Int, CachedCardEntry>()

    private data class CachedCardEntry(
        val circuit: CircuitEntity,
        val sizingResult: SizingResult?,
        val shortCircuitResult: ShortCircuitResult?,
        val parentName: String?,
        val hasChildren: Boolean,
        val model: CircuitCardUiModel
    )

    /**
     * Returns the cached [CircuitCardUiModel] if all inputs match, or constructs and caches a new one.
     */
    fun getOrCompute(
        circuit: CircuitEntity,
        sizingResult: SizingResult?,
        shortCircuitResult: ShortCircuitResult?,
        parentName: String?,
        hasChildren: Boolean
    ): CircuitCardUiModel {
        val existing = cache[circuit.id]
        if (existing != null &&
            existing.circuit == circuit &&
            existing.sizingResult == sizingResult &&
            existing.shortCircuitResult == shortCircuitResult &&
            existing.parentName == parentName &&
            existing.hasChildren == hasChildren
        ) {
            return existing.model
        }

        val model = CircuitUiModelMapper.mapToCardUiModel(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = shortCircuitResult,
            parentName = parentName,
            hasChildren = hasChildren
        )
        cache[circuit.id] = CachedCardEntry(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = shortCircuitResult,
            parentName = parentName,
            hasChildren = hasChildren,
            model = model
        )
        return model
    }

    /**
     * Prunes removed circuits from the cache to prevent memory leaks on deletion.
     */
    fun sync(currentCircuits: List<CircuitEntity>) {
        if (cache.size > currentCircuits.size) {
            val activeIds = HashSet<Int>(currentCircuits.size)
            for (circuit in currentCircuits) {
                activeIds.add(circuit.id)
            }
            cache.keys.retainAll(activeIds)
        }
    }

    @androidx.annotation.VisibleForTesting
    fun getCachedEntryCount(): Int = cache.size

    @androidx.annotation.VisibleForTesting
    fun clear() = cache.clear()
}
