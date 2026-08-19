package com.example.ai.context.freshness.adapters

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationFingerprint
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.ai.context.freshness.contracts.FingerprintResolution

/**
 * Common adapter interface responsible for extracting canonical direct inputs, dependencies,
 * and methodology metadata without performing new calculations.
 */
interface DomainFreshnessAdapter {
    val supportedDomain: CalculationDomain

    suspend fun resolveFingerprint(
        scope: CalculationScope,
        projectDataProvider: ProjectFreshnessDataProvider,
        upstreamFingerprintProvider: UpstreamFingerprintProvider? = null
    ): FingerprintResolution
}

/**
 * Read-only provider for looking up upstream calculation fingerprints.
 *
 * Used by downstream adapters (SHORT_CIRCUIT, PROTECTION_ADS, COORDINATION)
 * to chain upstream fingerprints as typed dependency components (Option A).
 */
interface UpstreamFingerprintProvider {
    /**
     * Returns the recorded fingerprint for an upstream scope, or null if no record exists.
     */
    fun getRecordedFingerprint(scope: CalculationScope): CalculationFingerprint?
}
