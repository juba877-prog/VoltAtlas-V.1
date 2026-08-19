package com.example.ai.context.freshness.contracts

/**
 * Coverage classification indicating whether all required direct inputs and dependencies
 * for a domain are fully captured in the fingerprint.
 */
enum class FingerprintCoverage {
    COMPLETE,
    PARTIAL
}
