package com.example.ai.context.freshness.adapters

import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder

/**
 * Freshness adapter for Protection and ADS (Automatic Disconnection of Supply) domain.
 *
 * Coverage: PARTIAL unless upstream CABLE_SIZING and SHORT_CIRCUIT fingerprints are available.
 *
 * PROTECTION_ADS consumes intermediate SizingResult and ShortCircuitResult outputs.
 * Uses Option A: upstream fingerprints are chained as typed dependency components.
 */
class ProtectionAdsFreshnessAdapter : DomainFreshnessAdapter {

    companion object {
        const val METHODOLOGY_VERSION = "VoltAtlas-Protection-M1"
        const val ENGINE_VERSION = "VoltAtlas-ProtectionEngine-v1.0"
    }

    override val supportedDomain: CalculationDomain = CalculationDomain.PROTECTION_ADS

    override suspend fun resolveFingerprint(
        scope: CalculationScope,
        projectDataProvider: ProjectFreshnessDataProvider,
        upstreamFingerprintProvider: UpstreamFingerprintProvider?
    ): FingerprintResolution {
        val circuitId = scope.entityId.toIntOrNull()
            ?: return FingerprintResolution.Invalid("Invalid circuit entityId '${scope.entityId}', must be integer.")

        val circuits = projectDataProvider.getCircuits(scope.projectId)
        val circuit = circuits.firstOrNull { it.id == circuitId }
            ?: return FingerprintResolution.Invalid("Circuit $circuitId not found in project ${scope.projectId}")

        val transformers = projectDataProvider.getTransformers(scope.projectId)

        // 1. Direct Inputs
        val inputEncoder = CanonicalFingerprintEncoder()
            .putInt("circuitId", circuit.id)
            .putString("circuitName", circuit.circuitName)
            .putString("overrideDeviceType", circuit.overrideDeviceType)
            .putQuantity("overrideDeviceSize", circuit.overrideDeviceSize, "A")
            .putString("cableType", circuit.cableType)
            .putQuantity("length_m", circuit.length_m, "m")
            .putQuantity("loadKVA", circuit.loadKVA, "kVA")
            .putQuantity("ze_ohm", 0.01, "ohm")

        // 2. Upstream Protective Device Dependencies
        val parent = if (circuit.parentId != null) {
            circuits.find { it.id == circuit.parentId }
        } else {
            circuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }
        }

        val feedingTx = transformers.firstOrNull { it.transformerId.equals(circuit.transformerId.trim(), ignoreCase = true) }

        val depEncoder = CanonicalFingerprintEncoder()
            .putInt("parentId", parent?.id ?: 0)
            .putString("parentName", parent?.circuitName ?: "Incomer")
            .putQuantity("parentLoadKVA", parent?.loadKVA ?: 0.0, "kVA")
            .putString("parentDeviceType", parent?.overrideDeviceType ?: "")
            .putQuantity("parentDeviceSize", parent?.overrideDeviceSize ?: 0.0, "A")
            .putString("txId", feedingTx?.transformerId ?: "N/A")
            .putQuantity("txKVA", feedingTx?.kVA ?: 0.0, "kVA")
            .putQuantity("txVoltage", feedingTx?.voltage?.toDouble() ?: 400.0, "V")

        // Chain upstream CABLE_SIZING and SHORT_CIRCUIT fingerprints (Option A)
        var hasAllUpstreamFingerprints = true
        if (upstreamFingerprintProvider != null) {
            val cableSizingScope = CalculationScope(
                scope.projectId, CalculationEntityType.CIRCUIT, scope.entityId, CalculationDomain.CABLE_SIZING
            )
            val upstreamCableFp = upstreamFingerprintProvider.getRecordedFingerprint(cableSizingScope)
            if (upstreamCableFp != null) {
                depEncoder.putString("upstreamCableSizingInput", upstreamCableFp.inputSignature)
                depEncoder.putString("upstreamCableSizingDep", upstreamCableFp.dependencySignature)
                depEncoder.putString("upstreamCableSizingMethodology", upstreamCableFp.methodologySignature ?: "")
            } else {
                hasAllUpstreamFingerprints = false
            }

            val scScope = CalculationScope(
                scope.projectId, CalculationEntityType.CIRCUIT, scope.entityId, CalculationDomain.SHORT_CIRCUIT
            )
            val upstreamScFp = upstreamFingerprintProvider.getRecordedFingerprint(scScope)
            if (upstreamScFp != null) {
                depEncoder.putString("upstreamShortCircuitInput", upstreamScFp.inputSignature)
                depEncoder.putString("upstreamShortCircuitDep", upstreamScFp.dependencySignature)
            } else {
                hasAllUpstreamFingerprints = false
            }
        } else {
            hasAllUpstreamFingerprints = false
        }

        // 3. Methodology & Engine
        val fingerprint = CalculationFingerprint(
            schemaVersion = 1,
            inputSignature = inputEncoder.computeSha256Hex(),
            dependencySignature = depEncoder.computeSha256Hex(),
            methodologySignature = METHODOLOGY_VERSION,
            engineSignature = ENGINE_VERSION
        )

        val coverage = if (hasAllUpstreamFingerprints) {
            FingerprintCoverage.COMPLETE
        } else {
            FingerprintCoverage.PARTIAL
        }

        return FingerprintResolution.Resolved(
            fingerprint = fingerprint,
            coverage = coverage
        )
    }
}
