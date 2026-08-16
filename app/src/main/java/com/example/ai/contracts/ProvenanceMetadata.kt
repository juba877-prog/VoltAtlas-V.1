package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Origin and calculation lineage metadata ensuring complete traceability.
 */
@JsonClass(generateAdapter = true)
data class ProvenanceMetadata(
    val contractVersion: Int = 1,
    val engineName: String,
    val engineVersion: String? = null,
    val methodologyReference: String? = null,
    val calculatedAt: Long = System.currentTimeMillis(),
    val projectId: String,
    val projectRevision: Long? = null,
    val inputSignature: Int? = null,
    val dependencySignature: Int? = null,
    val sourceKind: String = "DETERMINISTIC_ENGINE"
)
