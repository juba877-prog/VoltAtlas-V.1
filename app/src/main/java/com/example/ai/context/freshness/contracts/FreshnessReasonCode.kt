package com.example.ai.context.freshness.contracts

/**
 * Machine-readable reason codes explaining calculation freshness state decisions.
 */
enum class FreshnessReasonCode {
    MATCH,
    INPUT_CHANGED,
    DEPENDENCY_CHANGED,
    METHODOLOGY_CHANGED,
    ENGINE_CHANGED,
    PROJECT_CONTEXT_CHANGED,
    PROVENANCE_INCOMPLETE,
    INVALID_INPUTS,
    NO_RECORD,
    CALCULATION_IN_PROGRESS,
    RECALCULATION_IN_PROGRESS,
    EVALUATION_ERROR
}
