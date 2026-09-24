package com.example.ai.grounding

import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.squareup.moshi.JsonClass

/**
 * Functional taxonomy of engineering assertions made within an assistant response.
 */
enum class EngineeringClaimType {
    ENGINEERING_VALUE,
    COMPLIANCE_STATUS,
    CANDIDATE_SELECTION,
    ENGINEERING_CHECK,
    ENGINEERING_ASSUMPTION,
    FRESHNESS_STATUS
}

/**
 * Strongly typed representation of an engineering assertion made by the model or structured response (Gate 6).
 *
 * Invariants:
 * - Authoritative engineering quantities are typed via [typedValue] ([EngineeringValue]), preserving
 *   canonical magnitude and unit (Correction #1).
 * - Compliance and lifecycle statuses are typed via [statusValue] ([ToolStatus]), distinct from
 *   execution status (Correction #5).
 * - Free-text snippets remain in [rawTextSnippet] for auditing, never as the authoritative numeric source.
 */
@JsonClass(generateAdapter = true)
data class EngineeringClaim(
    val claimType: EngineeringClaimType,
    val name: String,
    val typedValue: EngineeringValue? = null,
    val statusValue: ToolStatus? = null,
    val stringValue: String? = null,
    val projectId: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val domain: String = "CABLE_SIZING",
    val rawTextSnippet: String? = null
)
