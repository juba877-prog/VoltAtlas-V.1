package com.example.ai.context.freshness.adapters

import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder
import com.example.data.CircuitEntity

/**
 * Freshness adapter for Cable Sizing domain.
 *
 * Extracts canonical direct sizing parameters, upstream hierarchy dependencies,
 * and methodology/catalog identity.
 * Performs zero engineering sizing calculations.
 *
 * Coverage classification:
 * - COMPLETE when methodology identity is included (engine version + methodology standard).
 * - Catalog data identity (cable catalog, derating tables, R/X data) is included
 *   via [METHODOLOGY_VERSION] which is a deterministic constant tied to the
 *   engine+catalog bundle. Any catalog data change requires a code change
 *   that bumps this version.
 */
class CableSizingFreshnessAdapter : DomainFreshnessAdapter {

    companion object {
        /**
         * Deterministic methodology version constant.
         *
         * This constant MUST be bumped whenever any of the following change:
         * - Cable catalog data (cableCatalog, cableTypes, dataSheet)
         * - Derating factor tables (temperature, depth, soil, grouping)
         * - R/X impedance data sheets
         * - Engine calculation logic or defaults
         *
         * This ensures that a catalog/methodology change invalidates all
         * prior Cable Sizing fingerprints without requiring runtime catalog hashing.
         */
        const val METHODOLOGY_VERSION = "VoltAtlas-CableSizing-M1"
        const val ENGINE_VERSION = "VoltAtlas-CableSizingEngine-v1.0"
    }

    override val supportedDomain: CalculationDomain = CalculationDomain.CABLE_SIZING

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

        // 1. Canonical Direct Inputs
        val inputEncoder = CanonicalFingerprintEncoder()
            .putString("circuitName", circuit.circuitName)
            .putQuantity("loadKVA", circuit.loadKVA, "kVA")
            .putString("loadType", circuit.loadType)
            .putString("phases", circuit.phases)
            .putQuantity("voltageV", circuit.voltageV.toDouble(), "V")
            .putQuantity("pf", circuit.pf, "ratio")
            .putQuantity("safetyFactor", circuit.safetyFactor, "ratio")
            .putString("manufacturer", circuit.manufacturer)
            .putString("cableType", circuit.cableType)
            .putString("installation", circuit.installation)
            .putInt("parallelRuns", circuit.parallelRuns)
            .putQuantity("airTempC", circuit.airTempC.toDouble(), "degC")
            .putQuantity("gndTempC", circuit.gndTempC.toDouble(), "degC")
            .putQuantity("burialDepth_m", circuit.burialDepth_m, "m")
            .putInt("soilRes", circuit.soilRes)
            .putInt("noCircuits", circuit.noCircuits)
            .putString("formation", circuit.formation)
            .putString("spacing", circuit.spacing)
            .putQuantity("length_m", circuit.length_m, "m")
            .putQuantity("maxVDpct", circuit.maxVDpct, "pct")
            .putQuantity("demandFactor", circuit.demandFactor, "ratio")
            .putString("feederType", circuit.feederType)
            .putString("buswayMfg", circuit.buswayMfg)
            .putString("buswayModel", circuit.buswayModel)
            .putString("overrideDeviceType", circuit.overrideDeviceType)
            .putQuantity("overrideDeviceSize", circuit.overrideDeviceSize, "A")
            .putString("overrideCableSize", circuit.overrideCableSize)
            .putString("overrideNeutralSize", circuit.overrideNeutralSize)
            .putString("overrideEarthSize", circuit.overrideEarthSize)
            .putBoolean("isHarmonic", circuit.isHarmonic)
            .putString("startingMethod", circuit.startingMethod)
            .putQuantity("startingCurrentMultiplier", circuit.startingCurrentMultiplier, "ratio")

        // 2. Canonical Upstream Dependencies
        val ancestors = resolveAncestorChain(circuit, circuits)
        val ancestorSummary = ancestors.map { anc ->
            mapOf(
                "id" to anc.id,
                "circuitName" to anc.circuitName,
                "length_m" to anc.length_m,
                "loadKVA" to anc.loadKVA,
                "cableType" to anc.cableType,
                "installation" to anc.installation,
                "parallelRuns" to anc.parallelRuns
            )
        }

        val txExists = transformers.any { it.transformerId.equals(circuit.transformerId, ignoreCase = true) }
        val depEncoder = CanonicalFingerprintEncoder()
            .putString("fedFrom", circuit.fedFrom)
            .putString("transformerId", circuit.transformerId)
            .putBoolean("sourceTransformerExists", txExists)
            .putList("ancestorPath", ancestorSummary)

        // 3. Methodology & Engine — includes catalog identity via version constant
        val fingerprint = CalculationFingerprint(
            schemaVersion = 1,
            inputSignature = inputEncoder.computeSha256Hex(),
            dependencySignature = depEncoder.computeSha256Hex(),
            methodologySignature = METHODOLOGY_VERSION,
            engineSignature = ENGINE_VERSION
        )

        return FingerprintResolution.Resolved(
            fingerprint = fingerprint,
            coverage = FingerprintCoverage.COMPLETE
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
