package com.example.ai.context.freshness.contracts

/**
 * Authoritative point-in-time calculation state snapshot.
 *
 * Invariants:
 * - [scope] identifies the project, entity, and domain.
 * - [state] is the formal calculation freshness state.
 * - [changedComponents] contains strongly typed component classifications when [state] is STALE.
 * - [changedFieldPaths] contains canonical, machine-readable field paths indicating which exact inputs/dependencies differed.
 * - [reasonCode] and [reason] provide structured and human-readable explanation of the state decision.
 */
data class CalculationStateSnapshot(
    val scope: CalculationScope,
    val state: CalculationState,
    val currentProjectRevision: Long,
    val recordedProjectRevision: Long? = null,
    val currentFingerprint: CalculationFingerprint? = null,
    val recordedFingerprint: CalculationFingerprint? = null,
    val changedComponents: Set<ChangedComponent> = emptySet(),
    val changedFieldPaths: List<String> = emptyList(),
    val reasonCode: FreshnessReasonCode,
    val reason: String,
    val lastCalculatedAt: Long? = null,
    val recordedStatus: String? = null
)
