package com.example.ai.context.freshness.contracts

/**
 * Pure, read-only calculation freshness provider.
 *
 * Invariants:
 * - Exposes zero mutation methods (no setCurrent, forceCurrent, setRecordedRevision).
 * - Implemented by deterministic evaluation layers only.
 */
interface CalculationFreshnessProvider {

    /**
     * Obtains the authoritative calculation state snapshot for a targeted calculation scope.
     */
    suspend fun getCalculationState(scope: CalculationScope): CalculationStateSnapshot
}
