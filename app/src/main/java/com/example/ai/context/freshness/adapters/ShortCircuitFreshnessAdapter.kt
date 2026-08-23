package com.example.ai.context.freshness.adapters

import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder
import com.example.data.CircuitEntity

/**
 * Freshness adapter for Short Circuit calculation domain.
 *
 * Coverage: PARTIAL unless upstream CABLE_SIZING and TRANSFORMER fingerprints are available.
 *
 * SHORT_CIRCUIT consumes intermediate SizingResult and TransformerResult outputs.
 * Rather than duplicating the full upstream calculation, this adapter uses Option A:
 * upstream fingerprints from CABLE_SIZING and TRANSFORMER are included as typed
 * dependency components. If upstream fingerprints are not yet recorded, coverage
 * is PARTIAL and CURRENT cannot be returned.
 */
class ShortCircuitFreshnessAdapter : DomainFreshnessAdapter {

    companion object {
        const val METHODOLOGY_VERSION = "VoltAtlas-ShortCircuit-M1"
        const val ENGINE_VERSION = "VoltAtlas-ShortCircuitEngine-v1.0"
    }

    override val supportedDomain: CalculationDomain = CalculationDomain.SHORT_CIRCUIT

    override fun resolveFingerprintFromEntities(
        scope: CalculationScope,
        circuits: List<com.example.data.CircuitEntity>,
        transformers: List<com.example.data.TransformerEntity>,
        upstreamFingerprintProvider: UpstreamFingerprintProvider?
    ): FingerprintResolution {
        val circuitId = scope.entityId.toIntOrNull()
            ?: return FingerprintResolution.Invalid("Invalid circuit entityId '${scope.entityId}', must be integer.")

        val circuit = circuits.firstOrNull { it.id == circuitId }
            ?: return FingerprintResolution.Invalid("Circuit $circuitId not found in project ${scope.projectId}")

        // 1. Direct Inputs (raw entity fields consumed by ShortCircuitEngine)
        val inputEncoder = CanonicalFingerprintEncoder()
            .putInt("circuitId", circuit.id)
            .putQuantity("length_m", circuit.length_m, "m")
            .putInt("parallelRuns", circuit.parallelRuns)
            .putString("fedFrom", circuit.fedFrom)
            .putString("transformerId", circuit.transformerId)
            .putString("cableType", circuit.cableType)
            .putString("manufacturer", circuit.manufacturer)
            .putString("installation", circuit.installation)

        // 2. Upstream Dependencies — includes upstream fingerprints (Option A)
        val ancestors = resolveAncestorChain(circuit, circuits)
        val ancestorSummary = ancestors.map { anc ->
            mapOf(
                "id" to anc.id,
                "length_m" to anc.length_m,
                "parallelRuns" to anc.parallelRuns,
                "cableType" to anc.cableType,
                "installation" to anc.installation
            )
        }

        val feedingTx = transformers.firstOrNull { it.transformerId.equals(circuit.transformerId.trim(), ignoreCase = true) }
        val depEncoder = CanonicalFingerprintEncoder()
            .putList("ancestorBranch", ancestorSummary)
            .putString("txId", feedingTx?.transformerId ?: "N/A")
            .putQuantity("txKVA", feedingTx?.kVA ?: 0.0, "kVA")
            .putQuantity("txVoltage", feedingTx?.voltage?.toDouble() ?: 400.0, "V")
            .putQuantity("txPercentZ", feedingTx?.percentZ ?: 5.0, "pct")
            .putQuantity("txXR", feedingTx?.xr ?: 5.0, "ratio")

        // Chain upstream CABLE_SIZING fingerprint for this circuit
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

            // Chain upstream TRANSFORMER fingerprint
            if (feedingTx != null) {
                val txScope = CalculationScope(
                    scope.projectId, CalculationEntityType.TRANSFORMER, feedingTx.transformerId, CalculationDomain.TRANSFORMER
                )
                val upstreamTxFp = upstreamFingerprintProvider.getRecordedFingerprint(txScope)
                if (upstreamTxFp != null) {
                    depEncoder.putString("upstreamTransformerInput", upstreamTxFp.inputSignature)
                    depEncoder.putString("upstreamTransformerDep", upstreamTxFp.dependencySignature)
                } else {
                    hasAllUpstreamFingerprints = false
                }
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

    private fun resolveAncestorChain(
        circuit: CircuitEntity,
        allCircuits: List<CircuitEntity>
    ): List<CircuitEntity> {
        val ancestors = mutableListOf<CircuitEntity>()
        val visited = mutableSetOf<Int>()
        var current = circuit

        while (true) {
            visited.add(current.id)
            val fedLower = current.fedFrom.trim().lowercase()
            if (fedLower in listOf("transformer", "ats", "generator", "grid", "source", "main", "")) {
                break
            }

            val parent = (if (current.parentId != null) allCircuits.find { it.id == current.parentId } else null)
                ?: allCircuits.find { it.circuitName.equals(current.fedFrom.trim(), ignoreCase = true) }

            if (parent == null || parent.id == current.id || visited.contains(parent.id)) {
                break
            }

            ancestors.add(0, parent)
            current = parent
        }

        return ancestors
    }
}
