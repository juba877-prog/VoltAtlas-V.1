package com.example.ai.proposal

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import com.example.engine.coordinator.GuardedCalculationResult
import com.example.engine.coordinator.ProjectCircuitKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Authoritative dependency and invalidation scope resolver for Phase 7 post-commit handoffs.
 * Single source of truth that maps mutated entity properties to affected calculation domains
 * and circuit hierarchy scopes.
 */
internal object AuthoritativeFreshnessDependencyResolver {

    val ALLOWLISTED_MUTATION_FIELDS = setOf(
        "overrideCableSize",
        "cableType",
        "manufacturer",
        "installation",
        "parallelRuns",
        "lengthM",
        "overrideDeviceSize",
        "overrideDeviceType"
    )

    /**
     * Resolves the exact downstream circuits and calculation domains requiring invalidation and revalidation.
     * Aligned with the authoritative domain freshness fingerprint manifests:
     * - All 8 allowlisted fields are direct inputs to Cable Sizing.
     * - Changes to target circuit propagate to downstream subtree circuits whose upstream
     *   dependencies (parent VD, short circuit impedance, upstream breaker rating) depend on the target.
     */
    fun resolveAffectedPlan(
        projectId: String,
        targetCircuitId: Int,
        mutatedFields: Set<String>,
        allCircuits: List<CircuitEntity>
    ): AffectedRevalidationPlan {
        val targetCircuit = allCircuits.firstOrNull { it.id == targetCircuitId }
            ?: return AffectedRevalidationPlan(
                targetCircuitId = targetCircuitId,
                affectedCircuitIds = listOf(targetCircuitId),
                affectedDomainsByCircuit = emptyMap(),
                executionOrder = emptyList(),
                cacheKeysToEvict = listOf(ProjectCircuitKey(projectId, targetCircuitId)),
                scopesToInvalidate = emptyList()
            )

        val subtree = resolveDownstreamSubtree(targetCircuit, allCircuits)
        val allAffected = listOf(targetCircuit) + subtree

        val affectedCircuitIds = mutableListOf<Int>()
        val affectedDomainsByCircuit = mutableMapOf<Int, Set<CalculationDomain>>()
        val executionOrder = mutableListOf<RevalidationStep>()
        val cacheKeysToEvict = mutableListOf<ProjectCircuitKey>()
        val scopesToInvalidate = mutableListOf<CalculationScope>()

        for (circ in allAffected) {
            affectedCircuitIds.add(circ.id)
            cacheKeysToEvict.add(ProjectCircuitKey(projectId, circ.id))

            val domains = setOf(
                CalculationDomain.CABLE_SIZING,
                CalculationDomain.SHORT_CIRCUIT,
                CalculationDomain.PROTECTION_ADS,
                CalculationDomain.COORDINATION
            )
            affectedDomainsByCircuit[circ.id] = domains

            for (domain in domains) {
                scopesToInvalidate.add(
                    CalculationScope(projectId, CalculationEntityType.CIRCUIT, circ.id.toString(), domain)
                )
            }
        }

        // Topological revalidation order (parent before child):
        // 1. All cable sizing (upstream first)
        for (circ in allAffected) {
            executionOrder.add(RevalidationStep(circ.id, CalculationDomain.CABLE_SIZING))
        }
        // 2. All short circuit
        for (circ in allAffected) {
            executionOrder.add(RevalidationStep(circ.id, CalculationDomain.SHORT_CIRCUIT))
        }
        // 3. All protection / ADS
        for (circ in allAffected) {
            executionOrder.add(RevalidationStep(circ.id, CalculationDomain.PROTECTION_ADS))
        }
        // 4. All coordination
        for (circ in allAffected) {
            executionOrder.add(RevalidationStep(circ.id, CalculationDomain.COORDINATION))
        }

        return AffectedRevalidationPlan(
            targetCircuitId = targetCircuitId,
            affectedCircuitIds = affectedCircuitIds,
            affectedDomainsByCircuit = affectedDomainsByCircuit,
            executionOrder = executionOrder,
            cacheKeysToEvict = cacheKeysToEvict,
            scopesToInvalidate = scopesToInvalidate
        )
    }

    /**
     * Resolves all downstream circuits that depend on the given circuit in topological order (parent before child).
     */
    private fun resolveDownstreamSubtree(
        root: CircuitEntity,
        allCircuits: List<CircuitEntity>
    ): List<CircuitEntity> {
        val descendants = mutableListOf<CircuitEntity>()
        val queue = mutableListOf(root)
        val visited = mutableSetOf(root.id)

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            val children = allCircuits.filter {
                !visited.contains(it.id) && (
                    (it.parentId != null && it.parentId == current.id) ||
                        it.fedFrom.trim().equals(current.circuitName.trim(), ignoreCase = true)
                    )
            }
            for (child in children) {
                visited.add(child.id)
                descendants.add(child)
                queue.add(child)
            }
        }

        return descendants
    }
}

/**
 * Result of MutationImpact pre-execution validation.
 */
internal sealed class ImpactValidationResult {
    data class Valid(val circuitId: Int, val targetCircuit: CircuitEntity) : ImpactValidationResult()
    data class Invalid(val reason: String) : ImpactValidationResult()
    data class TargetMissing(val entityId: String) : ImpactValidationResult()
    data class Superseded(val expected: Long, val actual: Long) : ImpactValidationResult()
}

/**
 * Internal Post-Commit Revalidation Orchestrator (Phase 7D).
 *
 * Responsibilities:
 * 1. Validate committed [MutationImpact] handoff data against live project state.
 * 2. Resolve authoritative invalidation scope via [AuthoritativeFreshnessDependencyResolver].
 * 3. Evict affected cache entries from [EngineeringCalculationCoordinator].
 * 4. Execute targeted deterministic calculations in dependency order with publication guards.
 * 5. Return sanitized, typed [RevalidationOutcome] preserving database commit truth.
 */
internal class PostCommitRevalidationOrchestrator(
    private val coordinator: EngineeringCalculationCoordinator = EngineeringCalculationCoordinator
) {

    /**
     * Validates that the handoff [MutationImpact] is well-formed, contains only allowlisted fields,
     * references an existing circuit in the project, and matches the live project revision.
     */
    fun validateImpact(
        impact: MutationImpact,
        allCircuits: List<CircuitEntity>,
        liveRevision: Long
    ): ImpactValidationResult {
        if (impact.projectId.isBlank()) {
            return ImpactValidationResult.Invalid("Project ID cannot be blank.")
        }
        if (impact.entityType != "CIRCUIT") {
            return ImpactValidationResult.Invalid("Entity type '${impact.entityType}' is unsupported; only 'CIRCUIT' is valid.")
        }
        val circuitId = impact.entityId.toIntOrNull()
            ?: return ImpactValidationResult.Invalid("Entity ID '${impact.entityId}' must be a valid integer.")
        if (impact.baseRevision < 1L) {
            return ImpactValidationResult.Invalid("Base revision must be >= 1.")
        }
        if (impact.resultingRevision != impact.baseRevision + 1L) {
            return ImpactValidationResult.Invalid("Resulting revision (${impact.resultingRevision}) must equal baseRevision (${impact.baseRevision}) + 1.")
        }
        if (impact.mutatedFields.isEmpty()) {
            return ImpactValidationResult.Invalid("Mutated fields set cannot be empty.")
        }
        val nonAllowlisted = impact.mutatedFields - AuthoritativeFreshnessDependencyResolver.ALLOWLISTED_MUTATION_FIELDS
        if (nonAllowlisted.isNotEmpty()) {
            return ImpactValidationResult.Invalid("Impact contains non-allowlisted mutated fields: $nonAllowlisted")
        }
        if (impact.status != MutationSuccessStatus.COMMITTED_REVALIDATION_REQUIRED) {
            return ImpactValidationResult.Invalid("Impact status must be COMMITTED_REVALIDATION_REQUIRED.")
        }
        if (!impact.requiresRevalidation) {
            return ImpactValidationResult.Invalid("Impact requiresRevalidation must be true.")
        }

        val targetCircuit = allCircuits.firstOrNull { it.id == circuitId && it.projectId == impact.projectId }
            ?: return ImpactValidationResult.TargetMissing(impact.entityId)

        if (liveRevision != impact.resultingRevision) {
            return ImpactValidationResult.Superseded(expected = impact.resultingRevision, actual = liveRevision)
        }

        return ImpactValidationResult.Valid(circuitId, targetCircuit)
    }

    /**
     * Executes post-commit cache invalidation and targeted revalidation for a committed [MutationImpact].
     */
    suspend fun revalidate(
        impact: MutationImpact,
        allCircuits: List<CircuitEntity>,
        allTransformers: List<TransformerEntity> = emptyList(),
        currentProjectRevision: Long = impact.resultingRevision
    ): RevalidationOutcome {
        // 1. Validate handoff impact
        val validation = validateImpact(impact, allCircuits, currentProjectRevision)
        val (circuitId, _) = when (validation) {
            is ImpactValidationResult.Valid -> validation.circuitId to validation.targetCircuit
            is ImpactValidationResult.Invalid -> return RevalidationOutcome.InvalidMutationImpact(validation.reason)
            is ImpactValidationResult.TargetMissing -> return RevalidationOutcome.TargetMissingAfterCommit(validation.entityId)
            is ImpactValidationResult.Superseded -> return RevalidationOutcome.RevisionAdvancedOrSuperseded(
                projectId = impact.projectId,
                expectedRevision = validation.expected,
                currentRevision = validation.actual
            )
        }

        // 2. Resolve authoritative invalidation plan
        val plan = AuthoritativeFreshnessDependencyResolver.resolveAffectedPlan(
            projectId = impact.projectId,
            targetCircuitId = circuitId,
            mutatedFields = impact.mutatedFields,
            allCircuits = allCircuits
        )

        // 3. Evict affected cache entries immediately
        for (cId in plan.affectedCircuitIds) {
            coordinator.invalidateCircuit(impact.projectId, cId)
        }

        // 4. Track domain execution outcomes
        val completedDomains = mutableSetOf<CalculationDomain>()
        val failedDomains = mutableMapOf<CalculationDomain, String>()
        val skippedDomains = mutableSetOf<CalculationDomain>()
        val circuitDomainSuccess = mutableMapOf<Pair<Int, CalculationDomain>, Boolean>()

        try {
            for (step in plan.executionOrder) {
                coroutineContext.ensureActive()

                val circuit = allCircuits.firstOrNull { it.id == step.circuitId }
                if (circuit == null) {
                    failedDomains[step.domain] = "Circuit ${step.circuitId} missing during revalidation step."
                    continue
                }

                // Check upstream domain prerequisites for this circuit
                val upstreamPrereqSatisfied = when (step.domain) {
                    CalculationDomain.CABLE_SIZING -> true
                    CalculationDomain.SHORT_CIRCUIT -> circuitDomainSuccess[step.circuitId to CalculationDomain.CABLE_SIZING] != false
                    CalculationDomain.PROTECTION_ADS -> circuitDomainSuccess[step.circuitId to CalculationDomain.CABLE_SIZING] != false
                    CalculationDomain.COORDINATION -> circuitDomainSuccess[step.circuitId to CalculationDomain.CABLE_SIZING] != false
                    else -> true
                }

                if (!upstreamPrereqSatisfied) {
                    skippedDomains.add(step.domain)
                    circuitDomainSuccess[step.circuitId to step.domain] = false
                    continue
                }

                val guardedResult = when (step.domain) {
                    CalculationDomain.CABLE_SIZING -> coordinator.calculateCableSizingGuarded(
                        circuit = circuit,
                        allCircuits = allCircuits,
                        transformers = allTransformers,
                        expectedRevision = impact.resultingRevision
                    )
                    CalculationDomain.SHORT_CIRCUIT -> coordinator.calculateShortCircuitGuarded(
                        circuitId = circuit.id,
                        allCircuits = allCircuits,
                        allTransformers = allTransformers,
                        expectedRevision = impact.resultingRevision
                    )
                    CalculationDomain.PROTECTION_ADS -> coordinator.calculateProtectionGuarded(
                        circuitId = circuit.id,
                        allCircuits = allCircuits,
                        allTransformers = allTransformers,
                        expectedRevision = impact.resultingRevision
                    )
                    CalculationDomain.COORDINATION -> coordinator.calculateCoordinationGuarded(
                        circuitId = circuit.id,
                        allCircuits = allCircuits,
                        allTransformers = allTransformers,
                        expectedRevision = impact.resultingRevision
                    )
                    else -> GuardedCalculationResult.Failed("Unsupported domain: ${step.domain}")
                }

                when (guardedResult) {
                    is GuardedCalculationResult.Published -> {
                        completedDomains.add(step.domain)
                        circuitDomainSuccess[step.circuitId to step.domain] = true
                    }
                    is GuardedCalculationResult.Superseded -> {
                        // Project revision advanced in database while revalidation was running!
                        return RevalidationOutcome.RevisionAdvancedOrSuperseded(
                            projectId = impact.projectId,
                            expectedRevision = guardedResult.expectedRevision,
                            currentRevision = guardedResult.actualRevision
                        )
                    }
                    is GuardedCalculationResult.Failed -> {
                        failedDomains[step.domain] = guardedResult.reason
                        circuitDomainSuccess[step.circuitId to step.domain] = false
                    }
                }
            }
        } catch (ex: CancellationException) {
            val allPlannedDomains = plan.executionOrder.map { it.domain }.toSet()
            val stillStale = allPlannedDomains - completedDomains
            return RevalidationOutcome.CommittedButRevalidationCancelled(
                projectId = impact.projectId,
                targetEntityId = impact.entityId,
                resultingRevision = impact.resultingRevision,
                completedDomains = completedDomains,
                stillStaleDomains = stillStale
            )
        } catch (ex: Exception) {
            return RevalidationOutcome.InternalFailure(
                sanitizedMessage = "Internal error during revalidation orchestration: ${ex.message ?: "unknown"}",
                diagnosticRef = "P7D_ORCH_ERR_${System.currentTimeMillis()}"
            )
        }

        val allPlannedDomains = plan.executionOrder.map { it.domain }.toSet()
        val stillStale = allPlannedDomains - completedDomains

        return if (failedDomains.isEmpty() && skippedDomains.isEmpty() && stillStale.isEmpty()) {
            RevalidationOutcome.RevalidatedSuccessfully(
                projectId = impact.projectId,
                targetEntityId = impact.entityId,
                resultingRevision = impact.resultingRevision,
                revalidatedDomains = completedDomains,
                affectedCircuitIds = plan.affectedCircuitIds,
                completedAt = System.currentTimeMillis()
            )
        } else if (completedDomains.isNotEmpty()) {
            RevalidationOutcome.CommittedWithPartialRevalidation(
                projectId = impact.projectId,
                targetEntityId = impact.entityId,
                resultingRevision = impact.resultingRevision,
                completedDomains = completedDomains,
                failedDomains = failedDomains,
                skippedDomains = skippedDomains,
                stillStaleDomains = stillStale,
                affectedCircuitIds = plan.affectedCircuitIds
            )
        } else {
            val firstFailedDomain = failedDomains.keys.firstOrNull() ?: allPlannedDomains.first()
            val reason = failedDomains[firstFailedDomain] ?: "Target domain revalidation failed."
            RevalidationOutcome.CommittedButRevalidationFailed(
                projectId = impact.projectId,
                targetEntityId = impact.entityId,
                resultingRevision = impact.resultingRevision,
                failedDomain = firstFailedDomain,
                reason = reason
            )
        }
    }
}
