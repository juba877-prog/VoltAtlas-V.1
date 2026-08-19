package com.example.ai.context.freshness.adapters

import com.example.ai.context.freshness.contracts.*
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder

/**
 * Freshness adapter for Transformer Sizing and Loading domain.
 *
 * Extracts canonical transformer parameters and assigned circuit loads.
 * Performs zero engineering calculations.
 *
 * Coverage: COMPLETE
 *
 * Source-backed field manifest (verified against EngineeringCalculationCoordinator.computeTransformerSignature
 * and TransformerCalculationService.calculateSingleTransformer):
 *
 * TransformerEntity direct inputs (18 fields):
 *   transformerId, transformerName, kVA, voltage, percentZ, xr, txType, sourceType,
 *   status, sizeMode, manualKVA, demandFactor, diversity, growth, safety,
 *   vectorGroup, z0OverZ1, pf
 *
 * Assigned circuit dependency fields (per circuit, 11 fields):
 *   id, circuitName, loadKVA, fedFrom, parentId, panelType, phases,
 *   assignedPhase, directLoadConfirmed, demandFactor, loadType
 *
 * Assignment filter: circuit.transformerId matches transformer OR
 *   circuit.fedFrom in {"transformer","ats","generator","grid"} (same as Coordinator)
 */
class TransformerFreshnessAdapter : DomainFreshnessAdapter {

    companion object {
        const val METHODOLOGY_VERSION = "VoltAtlas-Transformer-M1"
        const val ENGINE_VERSION = "VoltAtlas-TransformerEngine-v1.0"
    }

    override val supportedDomain: CalculationDomain = CalculationDomain.TRANSFORMER

    override suspend fun resolveFingerprint(
        scope: CalculationScope,
        projectDataProvider: ProjectFreshnessDataProvider,
        upstreamFingerprintProvider: UpstreamFingerprintProvider?
    ): FingerprintResolution {
        val txId = scope.entityId.trim()
        val transformers = projectDataProvider.getTransformers(scope.projectId)
        val transformer = transformers.firstOrNull { it.transformerId.equals(txId, ignoreCase = true) }
            ?: return FingerprintResolution.Invalid("Transformer '$txId' not found in project ${scope.projectId}")

        val circuits = projectDataProvider.getCircuits(scope.projectId)

        // 1. Direct Inputs — all 18 TransformerEntity fields consumed by engine
        val inputEncoder = CanonicalFingerprintEncoder()
            .putString("transformerId", transformer.transformerId)
            .putString("transformerName", transformer.transformerName)
            .putQuantity("kVA", transformer.kVA, "kVA")
            .putQuantity("voltage", transformer.voltage.toDouble(), "V")
            .putQuantity("percentZ", transformer.percentZ, "pct")
            .putQuantity("xr", transformer.xr, "ratio")
            .putString("txType", transformer.txType)
            .putString("sourceType", transformer.sourceType)
            .putString("status", transformer.status)
            .putString("sizeMode", transformer.sizeMode)
            .putQuantity("manualKVA", transformer.manualKVA, "kVA")
            .putQuantity("demandFactor", transformer.demandFactor, "ratio")
            .putQuantity("diversity", transformer.diversity, "ratio")
            .putQuantity("growth", transformer.growth, "ratio")
            .putQuantity("safety", transformer.safety, "ratio")
            .putString("vectorGroup", transformer.vectorGroup)
            .putQuantity("z0OverZ1", transformer.z0OverZ1, "ratio")
            .putQuantity("pf", transformer.pf, "ratio")

        // 2. Assigned Circuit Dependencies — same filter as Coordinator.computeTransformerSignature
        val txIdClean = transformer.transformerId.trim().lowercase()
        val assignedCircuits = circuits.filter { c ->
            val cTxId = c.transformerId.trim().lowercase()
            if (cTxId.isNotEmpty()) {
                cTxId == txIdClean
            } else {
                val fedClean = c.fedFrom.trim().lowercase()
                fedClean == txIdClean || fedClean in listOf("transformer", "ats", "generator", "grid")
            }
        }.sortedBy { it.id }

        val assignedCircuitSummary = assignedCircuits.map { c ->
            mapOf(
                "id" to c.id,
                "circuitName" to c.circuitName,
                "loadKVA" to c.loadKVA,
                "fedFrom" to c.fedFrom,
                "parentId" to (c.parentId ?: 0),
                "panelType" to (c.panelType ?: ""),
                "phases" to c.phases,
                "assignedPhase" to (c.assignedPhase ?: ""),
                "directLoadConfirmed" to (c.directLoadConfirmed ?: false),
                "demandFactor" to c.demandFactor,
                "loadType" to c.loadType
            )
        }

        val depEncoder = CanonicalFingerprintEncoder()
            .putList("assignedCircuits", assignedCircuitSummary)

        // 3. Methodology & Engine
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
}
