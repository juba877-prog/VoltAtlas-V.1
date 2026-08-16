package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Provenance source category for an engineering assumption.
 */
enum class AssumptionSourceType {
    PROJECT_CONFIG,
    METHODOLOGY_DEFAULT,
    USER_PROVIDED,
    DERIVED,
    CATALOG,
    SYSTEM_FALLBACK
}

/**
 * Explicit engineering assumption or design premise used in calculations.
 * Distinguishes verified project truth from estimated or default methodology parameters.
 */
@JsonClass(generateAdapter = true)
data class EngineeringAssumption(
    val assumptionId: String,
    val parameterName: String,
    val assumedValue: EngineeringValue,
    val sourceType: AssumptionSourceType,
    val description: String,
    val isUserOverridden: Boolean = false,
    val isSafetyCritical: Boolean = false
)
