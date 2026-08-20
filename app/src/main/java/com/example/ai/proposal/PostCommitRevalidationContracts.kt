package com.example.ai.proposal

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.engine.coordinator.ProjectCircuitKey
import com.squareup.moshi.JsonClass

/**
 * Single calculation domain revalidation step for a specific circuit.
 */
data class RevalidationStep(
    val circuitId: Int,
    val domain: CalculationDomain
)

/**
 * Resolved, authoritative post-commit invalidation and revalidation execution plan.
 */
data class AffectedRevalidationPlan(
    val targetCircuitId: Int,
    val affectedCircuitIds: List<Int>,
    val affectedDomainsByCircuit: Map<Int, Set<CalculationDomain>>,
    val executionOrder: List<RevalidationStep>,
    val cacheKeysToEvict: List<ProjectCircuitKey>,
    val scopesToInvalidate: List<CalculationScope>
)

/**
 * Result of single domain revalidation during orchestrator execution.
 */
sealed class DomainRevalidationResult {
    data class Success(val circuitId: Int, val domain: CalculationDomain, val revision: Long) : DomainRevalidationResult()
    data class Superseded(val circuitId: Int, val domain: CalculationDomain, val expectedRevision: Long, val actualRevision: Long) : DomainRevalidationResult()
    data class Failed(val circuitId: Int, val domain: CalculationDomain, val reason: String) : DomainRevalidationResult()
    data class Skipped(val circuitId: Int, val domain: CalculationDomain, val reason: String) : DomainRevalidationResult()
}

/**
 * Public, sanitized outcome container for post-commit targeted revalidation.
 */
sealed class RevalidationOutcome {
    /**
     * All affected calculation domains were successfully recalculated,
     * publication guards verified matching resultingRevision, and CURRENT freshness metadata recorded.
     */
    data class RevalidatedSuccessfully(
        val projectId: String,
        val targetEntityId: String,
        val resultingRevision: Long,
        val revalidatedDomains: Set<CalculationDomain>,
        val affectedCircuitIds: List<Int>,
        val completedAt: Long
    ) : RevalidationOutcome()

    /**
     * Database mutation remains committed, but some downstream domains failed or were skipped.
     * Successfully recalculated domains are marked CURRENT; failed/unexecuted domains remain STALE.
     */
    data class CommittedWithPartialRevalidation(
        val projectId: String,
        val targetEntityId: String,
        val resultingRevision: Long,
        val completedDomains: Set<CalculationDomain>,
        val failedDomains: Map<CalculationDomain, String>,
        val skippedDomains: Set<CalculationDomain>,
        val stillStaleDomains: Set<CalculationDomain>,
        val affectedCircuitIds: List<Int>
    ) : RevalidationOutcome()

    /**
     * Database mutation remains committed, but the primary target domain calculation failed.
     * All affected domains remain STALE.
     */
    data class CommittedButRevalidationFailed(
        val projectId: String,
        val targetEntityId: String,
        val resultingRevision: Long,
        val failedDomain: CalculationDomain,
        val reason: String
    ) : RevalidationOutcome()

    /**
     * Database mutation remains committed, but revalidation was cancelled before completion.
     * Any unverified domains remain STALE.
     */
    data class CommittedButRevalidationCancelled(
        val projectId: String,
        val targetEntityId: String,
        val resultingRevision: Long,
        val completedDomains: Set<CalculationDomain>,
        val stillStaleDomains: Set<CalculationDomain>
    ) : RevalidationOutcome()

    /**
     * Project revision in database advanced beyond resultingRevision (N+1 -> N+2) before publication.
     * Calculation results for older revision N+1 were safely discarded and not published.
     */
    data class RevisionAdvancedOrSuperseded(
        val projectId: String,
        val expectedRevision: Long,
        val currentRevision: Long
    ) : RevalidationOutcome()

    /**
     * MutationImpact was malformed, forged, or contained unsupported fields.
     * Zero cache eviction and zero revalidation occurred.
     */
    data class InvalidMutationImpact(
        val reason: String
    ) : RevalidationOutcome()

    /**
     * Target circuit was not found in the project post-commit.
     */
    data class TargetMissingAfterCommit(
        val entityId: String
    ) : RevalidationOutcome()

    /**
     * Internal runtime or unexpected error occurred during revalidation orchestration.
     */
    data class InternalFailure(
        val sanitizedMessage: String,
        val diagnosticRef: String
    ) : RevalidationOutcome()

    val isSuccess: Boolean
        get() = this is RevalidatedSuccessfully
}
