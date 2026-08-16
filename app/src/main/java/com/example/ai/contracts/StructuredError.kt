package com.example.ai.contracts

import com.squareup.moshi.JsonClass

/**
 * Machine-actionable error model separating stable error codes from human-readable remediation.
 * Never encapsulates raw Exception stack traces in client-facing payloads.
 */
@JsonClass(generateAdapter = true)
data class StructuredError(
    val errorCode: String,
    val message: String,
    val failingParameter: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val isRecoverable: Boolean = true,
    val remediationGuidance: String? = null,
    val technicalDetails: String? = null
)
