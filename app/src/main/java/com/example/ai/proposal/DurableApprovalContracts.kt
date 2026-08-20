package com.example.ai.proposal

/**
 * Standard schema version identifier for Phase 7E durable approval receipts.
 */
const val DURABLE_RECEIPT_SCHEMA_VERSION_V1 = "RECEIPT-V1"

/**
 * Allowed approval issuer types in production.
 * Production scope strictly requires explicit physical human operator action.
 */
enum class ApprovalIssuerType {
    USER_EXPLICIT_CONFIRMATION
}

/**
 * Durable lifecycle states for [com.example.data.ai.ApprovalGrantEntity].
 */
enum class ApprovalStatus {
    ISSUED,
    CONSUMED,
    EXPIRED,
    CANCELLED
}

/**
 * Durable calculation revalidation lifecycle states for [com.example.data.ai.MutationExecutionReceiptEntity].
 */
enum class RevalidationStatus {
    NOT_REQUIRED,
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED,
    SUPERSEDED
}
