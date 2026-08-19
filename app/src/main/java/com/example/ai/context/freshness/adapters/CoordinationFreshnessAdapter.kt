package com.example.ai.context.freshness.adapters

import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder

/**
 * Freshness adapter for Breaker Selectivity and Coordination calculation domain.
 *
 * Coverage:
 * - COMPLETE if and only if all required upstream fingerprints are present:
 *   1. Downstream circuit CABLE_SIZING (for downstream breaker size Ib/deviceSize_A)
 *   2. Upstream source identity:
 *      - Parent circuit CABLE_SIZING (if fed from parent circuit, determining upstream breaker rating)
 *      - OR Transformer TRANSFORMER (if fed from transformer incomer, determining incomer FLC)
 *   3. Downstream circuit SHORT_CIRCUIT (for selectivity prospective fault level Ik threshold)
 * - PARTIAL if ANY of these upstream fingerprints is absent or unrecorded.
 *
 * Performs zero engineering calculations; chains upstream fingerprints via Option A.
 */
class CoordinationFreshnessAdapter : DomainFreshnessAdapter {

    companion object {
        const val METHODOLOGY_VERSION = "VoltAtlas-Coordination-M1"
        const val ENGINE_VERSION = "VoltAtlas-CoordinationEngine-v1.0"
    }

    override val supportedDomain: CalculationDomain = CalculationDomain.COORDINATION

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
            .putQuantity("loadKVA", circuit.loadKVA, "kVA")

        // 2. Upstream Protective Device Dependencies
        val parent = if (circuit.parentId != null) {
            circuits.find { it.id == circuit.parentId }
        } else {
            circuits.find { it.circuitName.equals(circuit.fedFrom.trim(), ignoreCase = true) }
        }

        val tx = transformers.firstOrNull { it.transformerId.equals(circuit.transformerId.trim(), ignoreCase = true) }

        val depEncoder = CanonicalFingerprintEncoder()
            .putInt("parentId", parent?.id ?: 0)
            .putString("parentName", parent?.circuitName ?: "Incomer")
            .putString("parentDeviceType", parent?.overrideDeviceType ?: "")
            .putQuantity("parentDeviceSize", parent?.overrideDeviceSize ?: 0.0, "A")
            .putQuantity("parentLoadKVA", parent?.loadKVA ?: 0.0, "kVA")
            .putString("txId", tx?.transformerId ?: "N/A")
            .putQuantity("txKVA", tx?.kVA ?: 0.0, "kVA")
            .putQuantity("txVoltage", tx?.voltage?.toDouble() ?: 400.0, "V")

        // 3. Upstream Fingerprint Chaining (Option A)
        var hasAllUpstreamFingerprints = true
        if (upstreamFingerprintProvider != null) {
            // (a) Downstream CABLE_SIZING fingerprint
            val downstreamCableScope = CalculationScope(
                scope.projectId, CalculationEntityType.CIRCUIT, scope.entityId, CalculationDomain.CABLE_SIZING
            )
            val downstreamCableFp = upstreamFingerprintProvider.getRecordedFingerprint(downstreamCableScope)
            if (downstreamCableFp != null) {
                depEncoder.putString("downstreamCableSizingInput", downstreamCableFp.inputSignature)
                depEncoder.putString("downstreamCableSizingDep", downstreamCableFp.dependencySignature)
                depEncoder.putString("downstreamCableSizingMethodology", downstreamCableFp.methodologySignature ?: "")
            } else {
                hasAllUpstreamFingerprints = false
            }

            // (b) Upstream source identity: Parent CABLE_SIZING or Transformer TRANSFORMER
            if (parent != null) {
                val parentCableScope = CalculationScope(
                    scope.projectId, CalculationEntityType.CIRCUIT, parent.id.toString(), CalculationDomain.CABLE_SIZING
                )
                val parentCableFp = upstreamFingerprintProvider.getRecordedFingerprint(parentCableScope)
                if (parentCableFp != null) {
                    depEncoder.putString("parentCableSizingInput", parentCableFp.inputSignature)
                    depEncoder.putString("parentCableSizingDep", parentCableFp.dependencySignature)
                    depEncoder.putString("parentCableSizingMethodology", parentCableFp.methodologySignature ?: "")
                } else {
                    hasAllUpstreamFingerprints = false
                }
            } else if (tx != null || circuit.fedFrom.equals("Transformer", ignoreCase = true)) {
                val txIdToLookup = tx?.transformerId ?: circuit.transformerId.trim()
                if (txIdToLookup.isNotEmpty()) {
                    val txScope = CalculationScope(
                        scope.projectId, CalculationEntityType.TRANSFORMER, txIdToLookup, CalculationDomain.TRANSFORMER
                    )
                    val txFp = upstreamFingerprintProvider.getRecordedFingerprint(txScope)
                    if (txFp != null) {
                        depEncoder.putString("incomerTxInput", txFp.inputSignature)
                        depEncoder.putString("incomerTxDep", txFp.dependencySignature)
                    } else {
                        hasAllUpstreamFingerprints = false
                    }
                } else {
                    hasAllUpstreamFingerprints = false
                }
            } else {
                hasAllUpstreamFingerprints = false
            }

            // (c) Downstream SHORT_CIRCUIT fingerprint (for fault level threshold)
            val scScope = CalculationScope(
                scope.projectId, CalculationEntityType.CIRCUIT, scope.entityId, CalculationDomain.SHORT_CIRCUIT
            )
            val scFp = upstreamFingerprintProvider.getRecordedFingerprint(scScope)
            if (scFp != null) {
                depEncoder.putString("downstreamShortCircuitInput", scFp.inputSignature)
                depEncoder.putString("downstreamShortCircuitDep", scFp.dependencySignature)
            } else {
                hasAllUpstreamFingerprints = false
            }
        } else {
            hasAllUpstreamFingerprints = false
        }

        // 4. Methodology & Engine
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
