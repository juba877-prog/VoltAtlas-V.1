package com.example.ai.context.freshness.contracts

/**
 * Metadata recorded upon completion of an engineering calculation.
 *
 * Invariants:
 * - Does NOT store full engineering result payloads in 3D.
 * - Captures the calculation scope, project revision at computation time,
 *   deterministic fingerprint, timestamp, overall status (e.g. PASS/FAIL), and provenance.
 */
data class CalculationRecordMetadata(
    val scope: CalculationScope,
    val calculatedProjectRevision: Long,
    val fingerprint: CalculationFingerprint,
    val calculatedAt: Long,
    val overallStatus: String,
    val provenance: CalculationProvenance
)
