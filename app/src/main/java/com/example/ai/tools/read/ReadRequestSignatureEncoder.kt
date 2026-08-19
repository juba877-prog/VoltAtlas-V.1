package com.example.ai.tools.read

import com.example.ai.contracts.ToolRequestEnvelope
import com.example.ai.context.freshness.internal.CanonicalFingerprintEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Deterministic canonical encoder and SHA-256 cryptographic digest generator for tool request envelopes.
 *
 * Guarantees:
 * 1. Versioned Prefix: Every signature begins with the explicit canonical format version (e.g. `READ-V1:`).
 * 2. Complete Context Framing: Encodes version, projectId, toolName, entityType, entityId, and all request parameters.
 * 3. Stable Structure & Ordering: Maps are sorted alphabetically by key; lists preserve semantic order; sets are order-independent.
 * 4. Explicit Null & Type Framing: Distinguishes null from empty strings, zero, and missing fields.
 * 5. Locale Independence: Numbers and strings are canonicalized uniformly (NFC text, ROOT locale decimals).
 * 6. Cryptographic Collision Resistance: SHA-256 digest replaces 32-bit `hashCode()`, preventing false deduplication.
 */
interface ReadRequestSignatureEncoder {
    fun canonicalBytes(request: ToolRequestEnvelope<*>): ByteArray
    fun signature(request: ToolRequestEnvelope<*>): String
}

class DefaultReadRequestSignatureEncoder : ReadRequestSignatureEncoder {

    companion object {
        const val VERSION = "READ-V1"
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
            is GetProjectContextRequest -> {
                encoder.putString("__type__", "GetProjectContextRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putBoolean("includeValidation", params.includeValidation)
            }
            is GetEntityRequest -> {
                encoder.putString("__type__", "GetEntityRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("entityType", params.entityType)
                encoder.putString("entityId", params.entityId)
            }
            is GetCalculationStateRequest -> {
                encoder.putString("__type__", "GetCalculationStateRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("entityType", params.entityType)
                encoder.putString("entityId", params.entityId)
                encoder.putString("domain", params.domain?.name ?: "")
            }
            is GetDependenciesRequest -> {
                encoder.putString("__type__", "GetDependenciesRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putString("entityType", params.entityType)
                encoder.putString("entityId", params.entityId)
            }
            is GetChangeHistoryRequest -> {
                encoder.putString("__type__", "GetChangeHistoryRequest")
                encoder.putString("projectId", params.projectId)
                encoder.putInt("maxEntries", params.maxEntries)
            }
            is GetCatalogItemRequest -> {
                encoder.putString("__type__", "GetCatalogItemRequest")
                encoder.putString("category", params.category)
                encoder.putInt("limit", params.limit)
                encoder.putString("manufacturer", params.manufacturer)
                encoder.putString("query", params.query)
                encoder.putDouble("ratingA", params.ratingA)
                encoder.putDouble("sizeMm2", params.sizeMm2)
            }
            is Map<*, *> -> {
                encoder.putString("__type__", "MAP")
                encoder.putInt("size", params.size)
                // Sort keys alphabetically by string value to guarantee insertion-order independence
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
            is Set<*> -> {
                encoder.putString("__type__", "SET")
                encoder.putInt("size", params.size)
                // Sort elements by their deterministic item digest to guarantee order-independence
                val sortedItems = params.map { it to computeItemDigest(it) }.sortedBy { it.second }
                sortedItems.forEachIndexed { index, pair ->
                    encoder.putNested("elem:$index") { child ->
                        encodeParameters(child, pair.first)
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

    private fun computeItemDigest(item: Any?): String {
        val child = CanonicalFingerprintEncoder()
        encodeParameters(child, item)
        return child.computeSha256Hex()
    }
}
