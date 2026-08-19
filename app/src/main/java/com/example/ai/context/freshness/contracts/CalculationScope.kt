package com.example.ai.context.freshness.contracts

/**
 * Strongly typed scope identifying the project, entity, and calculation domain.
 *
 * Invariants:
 * - [projectId] must not be blank.
 * - [entityId] must not be blank.
 * - [entityType] and [domain] are strongly typed enums.
 * - Pure Kotlin data class with zero Android, UI, or Room dependencies.
 */
data class CalculationScope(
    val projectId: String,
    val entityType: CalculationEntityType,
    val entityId: String,
    val domain: CalculationDomain
) {
    init {
        require(projectId.isNotBlank()) { "CalculationScope projectId must not be blank." }
        require(entityId.isNotBlank()) { "CalculationScope entityId must not be blank." }
    }
}
