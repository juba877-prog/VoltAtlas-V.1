package com.example.ai.tools.read

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.provider.CircuitContextSnapshot
import com.example.ai.context.provider.TransformerContextSnapshot
import com.squareup.moshi.JsonClass

/**
 * Common names for deterministic read tools.
 */
object ReadToolNames {
    const val GET_CURRENT_PROJECT_CONTEXT = "get_current_project_context"
    const val GET_ENTITY = "get_entity"
    const val GET_CALCULATION_STATE = "get_calculation_state"
    const val GET_DEPENDENCIES = "get_dependencies"
    const val GET_CHANGE_HISTORY = "get_change_history"
    const val GET_CATALOG_ITEM = "get_catalog_item"
}

// -----------------------------------------------------------------------------
// 1. get_current_project_context
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class GetProjectContextRequest(
    val projectId: String,
    val includeValidation: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ValidationIssueItemDto(
    val entityId: String?,
    val domain: String,
    val severity: String,
    val description: String
)

@JsonClass(generateAdapter = true)
data class ProjectContextResult(
    val projectId: String,
    val projectRevision: Long,
    val engineeringStatus: String,
    val overallStatusSummary: String,
    val totalCircuits: Int,
    val totalTransformers: Int,
    val calculatedCircuitCount: Int,
    val staleCircuitCount: Int,
    val missingCircuitCount: Int,
    val criticalFailureCount: Int,
    val warningCount: Int,
    val reviewRequiredCount: Int,
    val topIssues: List<ValidationIssueItemDto> = emptyList()
)

// -----------------------------------------------------------------------------
// 2. get_entity
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class GetEntityRequest(
    val projectId: String,
    val entityType: String,
    val entityId: String
)

@JsonClass(generateAdapter = true)
data class EntityContextResult(
    val projectId: String,
    val entityType: String,
    val entityId: String,
    val projectRevision: Long?,
    val circuitContext: CircuitContextSnapshot? = null,
    val transformerContext: TransformerContextSnapshot? = null
)

// -----------------------------------------------------------------------------
// 3. get_calculation_state
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class GetCalculationStateRequest(
    val projectId: String,
    val entityType: String,
    val entityId: String,
    val domain: CalculationDomain? = null
)

@JsonClass(generateAdapter = true)
data class CalculationStateResult(
    val projectId: String,
    val entityType: String,
    val entityId: String,
    val domain: String?,
    val state: String,
    val reasonCode: String?,
    val recordedRevision: Long?,
    val currentRevision: Long?,
    val isUsable: Boolean,
    val changedComponentCount: Int = 0
)

// -----------------------------------------------------------------------------
// 4. get_dependencies
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class GetDependenciesRequest(
    val projectId: String,
    val entityType: String,
    val entityId: String
)

@JsonClass(generateAdapter = true)
data class DependenciesResult(
    val projectId: String,
    val entityType: String,
    val entityId: String,
    val resolutionStatus: String,
    val parentId: String? = null,
    val parentName: String? = null,
    val ancestorChainIds: List<String> = emptyList(),
    val downstreamChildIds: List<String> = emptyList(),
    val assignedTransformerId: String? = null
)

// -----------------------------------------------------------------------------
// 5. get_change_history
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class GetChangeHistoryRequest(
    val projectId: String,
    val entityId: String? = null,
    val maxEntries: Int = 10
)

@JsonClass(generateAdapter = true)
data class ChangeHistoryResult(
    val projectId: String,
    val currentRevision: Long,
    val lastModifiedTimestamp: Long?,
    val isDetailedAuditLogAvailable: Boolean = false,
    val notice: String = "Granular historical audit event logging is deferred to Sub-Phase 3E. Current project revision head is authoritative."
)

// -----------------------------------------------------------------------------
// 6. get_catalog_item
// -----------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class GetCatalogItemRequest(
    val category: String,
    val query: String? = null,
    val manufacturer: String? = null,
    val sizeMm2: Double? = null,
    val ratingA: Double? = null,
    val limit: Int = 20
)

@JsonClass(generateAdapter = true)
data class CatalogItemDto(
    val category: String,
    val identifier: String,
    val manufacturer: String,
    val modelOrSeries: String,
    val primaryRating: String,
    val details: Map<String, String> = emptyMap(),
    val provenance: String
)

@JsonClass(generateAdapter = true)
data class CatalogSearchResult(
    val category: String,
    val matchCount: Int,
    val items: List<CatalogItemDto>,
    val isTruncated: Boolean = false,
    val provenance: String
)
