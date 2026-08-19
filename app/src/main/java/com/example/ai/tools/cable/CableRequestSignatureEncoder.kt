package com.example.ai.tools.cable

import com.example.ai.contracts.StructuredError
import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder
import com.example.ai.tools.read.RequestSignatureResult
import java.security.MessageDigest
import java.util.Locale

/**
 * Deterministic canonical encoder and SHA-256 cryptographic digest generator for cable tool requests.
 *
 * Guarantees:
 * 1. Version Prefix: `CABLE-V1:`
 * 2. Strict canonical serialization of all 4 cable request DTOs.
 * 3. Fail-Closed Semantics: Returns [RequestSignatureResult.Failure] on encoding failure.
 */
interface CableRequestSignatureEncoder {
    fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray
    fun signature(request: ToolRequestEnvelope<*>): String

    fun computeSignature(request: ToolRequestEnvelope<*>): RequestSignatureResult {
        return try {
            RequestSignatureResult.Success(signature(request))
        } catch (t: Throwable) {
            RequestSignatureResult.Failure(
                StructuredError(
                    errorCode = "REQUEST_SIGNATURE_ENCODING_FAILED",
                    message = "Failed to compute canonical request signature for '${request.toolName}'.",
                    isRecoverable = false
                )
            )
        }
    }
}

class DefaultCableRequestSignatureEncoder : CableRequestSignatureEncoder {

    companion object {
        const val VERSION = "CABLE-V1"
    }

    override fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray {
        val encoder = CanonicalFingerprintEncoder()
        encoder.putString("version", VERSION)
        encoder.putString("projectId", request.projectId)
        encoder.putString("toolName", request.toolName)
        encoder.putString("entityType", request.entityType ?: "")
        encoder.putString("entityId", request.entityId ?: "")

        encoder.putNested("params") { p ->
            encodeParameters(p, request.parameters)
        }

        return encoder.encodeCanonicalBytes()
    }

    override fun signature(request: ToolRequestEnvelope<*>): String {
        val bytes = canonicalBytes(request)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
        return "$VERSION:$hex"
    }

    private fun encodeParameters(encoder: CanonicalFingerprintEncoder, params: Any?) {
        when (params) {
            null -> {
                encoder.putString("__type__", "NULL")
            }
            is CalculateCableSizingRequest -> {
                encoder.putString("__type__", "CalculateCableSizingRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("circuitId", params.circuitId)
                encoder.putBoolean("forceRecalculate", params.forceRecalculate)
            }
            is EvaluateCableCandidateRequest -> {
                encoder.putString("__type__", "EvaluateCableCandidateRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("circuitId", params.circuitId)
                encoder.putDouble("candidateSizeMm2", params.candidateSizeMm2)
                encoder.putString("candidateSizeText", params.candidateSizeText)
                encoder.putString("candidateMaterial", params.candidateMaterial)
                encoder.putInt("candidateParallelRuns", params.candidateParallelRuns)
                encoder.putString("candidateProductCode", params.candidateProductCode)
                encoder.putDouble("candidateDeviceSizeA", params.candidateDeviceSizeA)
                encoder.putString("candidateDeviceType", params.candidateDeviceType)
            }
            is CompareCableCandidatesRequest -> {
                encoder.putString("__type__", "CompareCableCandidatesRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("circuitId", params.circuitId)
                encoder.putString("sortPolicy", params.sortPolicy.name)
                encoder.putInt("candidateCount", params.candidates.size)
                params.candidates.forEachIndexed { index, c ->
                    encoder.putNested("cand:$index") { child ->
                        child.putString("candidateId", c.candidateId)
                        child.putDouble("sizeMm2", c.sizeMm2)
                        child.putString("sizeText", c.sizeText)
                        child.putString("material", c.material)
                        child.putInt("parallelRuns", c.parallelRuns)
                        child.putDouble("deviceSizeA", c.deviceSizeA)
                        child.putString("deviceType", c.deviceType)
                    }
                }
            }
            is RunCableWhatIfRequest -> {
                encoder.putString("__type__", "RunCableWhatIfRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("circuitId", params.circuitId)
                encoder.putNested("overrides") { o ->
                    o.putDouble("length_m", params.overrides.length_m)
                    o.putDouble("loadKVA", params.overrides.loadKVA)
                    o.putDouble("pf", params.overrides.pf)
                    o.putInt("voltageV", params.overrides.voltageV)
                    o.putDouble("maxVDpct", params.overrides.maxVDpct)
                    o.putString("cableType", params.overrides.cableType)
                    o.putString("manufacturer", params.overrides.manufacturer)
                    o.putString("installation", params.overrides.installation)
                    o.putInt("parallelRuns", params.overrides.parallelRuns)
                    o.putInt("airTempC", params.overrides.airTempC)
                    o.putInt("gndTempC", params.overrides.gndTempC)
                    o.putDouble("burialDepth_m", params.overrides.burialDepth_m)
                    o.putInt("soilRes", params.overrides.soilRes)
                    o.putString("formation", params.overrides.formation)
                    o.putString("spacing", params.overrides.spacing)
                    o.putInt("noCircuits", params.overrides.noCircuits)
                    o.putString("overrideCableSize", params.overrides.overrideCableSize)
                    o.putDouble("overrideDeviceSize", params.overrides.overrideDeviceSize)
                    o.putString("overrideDeviceType", params.overrides.overrideDeviceType)
                }
            }
            is Map<*, *> -> {
                encoder.putString("__type__", "MAP")
                encoder.putInt("size", params.size)
                val sortedKeys = params.keys.map { it.toString() }.sorted()
                for (k in sortedKeys) {
                    val entry = params.entries.firstOrNull { it.key.toString() == k }
                    encoder.putNested("k:$k") { child ->
                        encodeParameters(child, entry?.value)
                    }
                }
            }
            is List<*> -> {
                encoder.putString("__type__", "LIST")
                encoder.putInt("size", params.size)
                params.forEachIndexed { index, item ->
                    encoder.putNested("idx:$index") { child ->
                        encodeParameters(child, item)
                    }
                }
            }
            is String -> {
                encoder.putString("__type__", "STRING")
                encoder.putString("value", params)
            }
            is Number -> {
                encoder.putString("__type__", "NUMBER")
                encoder.putDouble("value", params.toDouble())
            }
            is Boolean -> {
                encoder.putString("__type__", "BOOLEAN")
                encoder.putBoolean("value", params)
            }
            else -> {
                encoder.putString("__type__", params.javaClass.name)
                encoder.putString("value", params.toString())
            }
        }
    }
}
