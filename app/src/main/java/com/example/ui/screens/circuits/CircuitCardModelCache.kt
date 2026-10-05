package com.example.ui.screens.circuits

import com.example.data.CircuitEntity
import com.example.engine.ProtectionResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult

/**
 * Thread-confined / Compose-local memoization cache for [CircuitCardUiModel] instances.
 * Eliminates whole-list recomputation by preserving and reusing previously computed models
 * when a circuit's entity, sizing result, short circuit result, parent breadcrumb, and child status
 * have not changed.
 *
 * ECT-EE-06: the [ProtectionResult] is part of the memo key. Without it, a circuit whose
 * protection verdict changes (e.g. after an Isc or device re-run) would keep serving the
 * previously computed card, because the sizing result alone is unchanged when only the fault
 * level moves.
 */
class CircuitCardModelCache {
    private val cache = mutableMapOf<Int, CachedCardEntry>()

    private data class CachedCardEntry(
        val circuit: CircuitEntity,
        val sizingResult: SizingResult?,
        val shortCircuitResult: ShortCircuitResult?,
        val parentName: String?,
        val hasChildren: Boolean,
        val protectionResult: ProtectionResult?,
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
        hasChildren: Boolean,
        protectionResult: ProtectionResult? = null
    ): CircuitCardUiModel {
        val existing = cache[circuit.id]
        if (existing != null &&
            existing.circuit == circuit &&
            existing.sizingResult == sizingResult &&
            existing.shortCircuitResult == shortCircuitResult &&
            existing.parentName == parentName &&
            existing.hasChildren == hasChildren &&
            existing.protectionResult == protectionResult
        ) {
            return existing.model
        }

        val model = CircuitUiModelMapper.mapToCardUiModel(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = shortCircuitResult,
            parentName = parentName,
            hasChildren = hasChildren,
            protectionResult = protectionResult
        )
        cache[circuit.id] = CachedCardEntry(
            circuit = circuit,
            sizingResult = sizingResult,
            shortCircuitResult = shortCircuitResult,
            parentName = parentName,
            hasChildren = hasChildren,
            protectionResult = protectionResult,
            model = model
        )
        return model
    }

    /**
     * Prunes removed circuits from the cache to prevent memory leaks on deletion
     * or stale model retention on equal-count replacement.
     */
    fun sync(currentCircuits: List<CircuitEntity>) {
        val activeIds = HashSet<Int>(currentCircuits.size)
        for (circuit in currentCircuits) {
            activeIds.add(circuit.id)
        }
        cache.keys.retainAll(activeIds)
    }

    @androidx.annotation.VisibleForTesting
    fun getCachedEntryCount(): Int = cache.size

    @androidx.annotation.VisibleForTesting
    internal fun cachedIdsForTesting(): Set<Int> = cache.keys.toSet()

    @androidx.annotation.VisibleForTesting
    fun clear() = cache.clear()
}
