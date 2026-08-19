package com.example.ai.context.freshness.contracts

/**
 * Formal calculation freshness state vocabulary.
 *
 * Deterministic lifecycle states:
 * - [CURRENT]: A valid recorded result matches the current relevant inputs, dependencies, and methodology.
 * - [STALE]: A recorded result exists, but relevant project/entity/dependency state has changed.
 * - [MISSING]: No result record exists for the requested scope and no calculation is executing.
 * - [INVALID]: Current inputs or dependencies cannot form a valid calculation request.
 * - [RUNNING]: First calculation for the current scope is actively executing; no prior result exists.
 * - [REVALIDATING]: A prior result exists and recalculation for current relevant state is actively executing.
 * - [ERROR]: Calculation execution or freshness evaluation failed deterministically.
 */
enum class CalculationState {
    CURRENT,
    STALE,
    MISSING,
    INVALID,
    RUNNING,
    REVALIDATING,
    ERROR
}
