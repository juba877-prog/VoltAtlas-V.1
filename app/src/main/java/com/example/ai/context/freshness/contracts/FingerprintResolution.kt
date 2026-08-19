package com.example.ai.context.freshness.contracts

/**
 * Result of extracting and resolving calculation fingerprint from authoritative domain state.
 */
sealed interface FingerprintResolution {

    data class Resolved(
        val fingerprint: CalculationFingerprint,
        val coverage: FingerprintCoverage
    ) : FingerprintResolution

    data class Invalid(
        val reason: String,
        val invalidFieldPaths: List<String> = emptyList()
    ) : FingerprintResolution

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : FingerprintResolution
}
