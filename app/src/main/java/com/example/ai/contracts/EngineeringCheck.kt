package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Specific deterministic engineering check performed during engine evaluation.
 * Represents ampacity, voltage drop, short-circuit withstand, ADS/Zs,
 * breaking capacity, and breaker selectivity without computing them here.
 */
@JsonClass(generateAdapter = true)
data class EngineeringCheck(
    val checkId: String,
    val checkName: String,
    val status: ToolStatus,
    val actualValue: EngineeringValue? = null,
    val requiredLimit: EngineeringValue? = null,
    val comparisonCriterion: String? = null,
    val standardReference: String? = null,
    val message: String
)
