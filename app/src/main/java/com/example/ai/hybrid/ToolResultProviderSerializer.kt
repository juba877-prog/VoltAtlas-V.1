package com.example.ai.hybrid

import com.example.ai.contracts.ToolStatus
import com.example.ai.tools.ToolExecutionOutcome
import com.squareup.moshi.Moshi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Provider-boundary serializer converting authoritative [ToolExecutionOutcome] into
 * Firebase / Gemini provider-compliant [JsonObject] (Correction #2).
 *
 * Invariants:
 * - Domain layer retains [ToolExecutionOutcome] and [com.example.ai.contracts.ToolResultEnvelope].
 * - JSON serialization only occurs at the provider boundary.
 * - Distinguishes engineering outcomes (PASS, WARNING, FAIL) from runtime execution failures (ERROR, INVALID_INPUT, etc.).
 */
object ToolResultProviderSerializer {

    private val moshi: Moshi = Moshi.Builder().build()

    fun toProviderResponse(outcome: ToolExecutionOutcome): JsonObject {
        val envelope = outcome.envelope
        val isSuccess = envelope.status == ToolStatus.PASS ||
                envelope.status == ToolStatus.WARNING ||
                envelope.status == ToolStatus.FAIL

        return buildJsonObject {
            put("toolName", outcome.toolName)
            put("requestId", outcome.requestId)
            put("status", envelope.status.name)
            put("isSuccess", isSuccess)
            put("projectId", envelope.projectId)
            envelope.projectRevision?.let { put("projectRevision", it) }

            // Result payload
            envelope.result?.let { res ->
                put("result", serializePayload(res))
            }

            // Structured checks
            if (envelope.checks.isNotEmpty()) {
                put("checks", buildJsonArray {
                    envelope.checks.forEach { check ->
                        add(buildJsonObject {
                            put("checkName", check.checkName)
                            put("status", check.status.name)
                            put("message", check.message)
                        })
                    }
                })
            }

            // Structured errors
            if (envelope.errors.isNotEmpty()) {
                put("errors", buildJsonArray {
                    envelope.errors.forEach { err ->
                        add(buildJsonObject {
                            put("errorCode", err.errorCode)
                            put("message", err.message)
                            err.failingParameter?.let { put("failingParameter", it) }
                            err.remediationGuidance?.let { put("remediationGuidance", it) }
                        })
                    }
                })
            }

            // Assumptions
            if (envelope.assumptions.isNotEmpty()) {
                put("assumptions", buildJsonArray {
                    envelope.assumptions.forEach { assumption ->
                        add(buildJsonObject {
                            put("parameterName", assumption.parameterName)
                            put("sourceType", assumption.sourceType.name)
                            put("description", assumption.description)
                        })
                    }
                })
            }

            // Provenance
            envelope.provenance?.let { prov ->
                put("provenance", buildJsonObject {
                    put("engineName", prov.engineName)
                    put("sourceKind", prov.sourceKind)
                    put("calculatedAt", prov.calculatedAt)
                })
            }
        }
    }

    private fun serializePayload(value: Any): JsonElement = when (value) {
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) ->
                if (k != null) put(k.toString(), v?.let { serializePayload(it) } ?: JsonNull)
            }
        }
        is Iterable<*> -> buildJsonArray {
            value.forEach { item ->
                add(item?.let { serializePayload(it) } ?: JsonNull)
            }
        }
        else -> {
            try {
                val adapter = moshi.adapter(value.javaClass)
                val json = adapter.toJson(value)
                Json.parseToJsonElement(json)
            } catch (e: Exception) {
                JsonPrimitive(value.toString())
            }
        }
    }
}
