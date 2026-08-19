package com.example.ai.context.freshness.contracts

/**
 * Provenance descriptor indicating engine and methodology identity.
 */
data class CalculationProvenance(
    val engineName: String,
    val engineVersion: String? = null,
    val methodologyName: String? = null,
    val methodologyVersion: String? = null,
    val isVerified: Boolean = true
)
