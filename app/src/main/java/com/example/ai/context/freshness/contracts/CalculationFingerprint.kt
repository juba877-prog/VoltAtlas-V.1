package com.example.ai.context.freshness.contracts

/**
 * Deterministic structured fingerprint for calculation input, dependency, methodology, and engine identity.
 *
 * Invariants:
 * - [inputSignature] is a SHA-256 hexadecimal digest of canonical direct inputs.
 * - [dependencySignature] is a SHA-256 hexadecimal digest of canonical upstream dependencies.
 * - Stable across processes, independent of map key ordering, and locale-independent.
 * - Zero JVM [hashCode] dependency for persisted/engineering comparison.
 */
data class CalculationFingerprint(
    val schemaVersion: Int = 1,
    val inputSignature: String,
    val dependencySignature: String,
    val methodologySignature: String? = null,
    val engineSignature: String? = null
) {
    init {
        require(inputSignature.isNotBlank()) { "CalculationFingerprint inputSignature must not be blank." }
        require(dependencySignature.isNotBlank()) { "CalculationFingerprint dependencySignature must not be blank." }
    }
}
