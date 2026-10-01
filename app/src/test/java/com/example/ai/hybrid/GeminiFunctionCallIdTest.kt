package com.example.ai.hybrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 4 / G-3 — function-call ID provenance.
 *
 * Google's `FunctionCall.id` is OPTIONAL:
 *   "Unique identifier of the function call. If populated, the client to execute the
 *    functionCall and return the response with the matching id."
 *
 * The previous implementation fabricated a `UUID.randomUUID()` whenever the provider omitted
 * the id, which falsely implied Google had issued that identifier. This suite locks:
 *   - provider id present  -> preserved exactly
 *   - provider id absent   -> explicit emptiness, never a manufactured UUID
 *   - correlation          -> functionResponse carries the matching id when one exists,
 *                            and omits the field entirely when it does not
 *
 * Protocol-level (parser/serializer) tests. NOT live-provider verification.
 */
class GeminiFunctionCallIdTest {

    private fun responseWith(functionCallJson: String): String = """
        {
          "candidates": [
            { "content": { "role": "model", "parts": [ { "functionCall": $functionCallJson } ] } }
          ]
        }
    """.trimIndent()

    private fun parse(json: String) =
        DirectGeminiClientSeam.parseGeminiResponse(responseWith(json)) as CloudLlmClientResponse.ToolCallRequest

    /**
     * G-3.1 — Provider id is preserved byte-for-byte.
     */
    @Test
    fun providerSuppliedIdIsPreservedExactly() {
        val req = parse("""{ "id": "fc_abc123", "name": "get_project_context", "args": {} }""")
        assertEquals("fc_abc123", req.calls.single().callId)
    }

    /**
     * G-3.2 — Provider id absent: the call must NOT gain a fabricated UUID. This is the
     * discriminating assertion for G-3; it fails on the pre-fix code.
     */
    @Test
    fun absentProviderIdIsNotReplacedWithAFabricatedUuid() {
        val req = parse("""{ "name": "get_project_context", "args": {} }""")

        val callId = req.calls.single().callId
        assertEquals(
            "a provider that returned no id must not be given a fabricated one",
            "",
            callId
        )
        assertTrue(
            "call id must not look like a generated UUID: $callId",
            !callId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        )
    }

    /**
     * G-3.3 — Blank/whitespace ids from the provider are treated as absent, not as real ids.
     */
    @Test
    fun blankProviderIdIsTreatedAsAbsent() {
        val req = parse("""{ "id": "   ", "name": "get_project_context", "args": {} }""")
        assertEquals("", req.calls.single().callId)
    }

    /**
     * G-3.4 — Single call with a provider id round-trips into the functionResponse `id`.
     */
    @Test
    fun functionResponseCarriesMatchingProviderId() {
        val call = parse("""{ "id": "fc_1", "name": "size_feeder_cable", "args": {} }""").calls.single()

        val contents = DirectGeminiClientSeam.buildGeminiContents(
            "q",
            listOf(com.example.ai.orchestrator.AgenticTurnItem.ModelCalls(calls = listOf(call)))
        )
        val funcCall = contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0)
            .getJSONObject("functionCall")
        assertEquals("fc_1", funcCall.getString("id"))
    }

    /**
     * G-3.5 — No provider id: the replayed functionCall and the functionResponse must both
     * OMIT the `id` field entirely, rather than sending a fabricated identifier.
     */
    @Test
    fun absentIdIsOmittedFromWirePayloadRatherThanInvented() {
        val call = parse("""{ "name": "size_feeder_cable", "args": {} }""").calls.single()
        assertEquals("", call.callId)

        val history = listOf(
            com.example.ai.orchestrator.AgenticTurnItem.ModelCalls(calls = listOf(call)),
            com.example.ai.orchestrator.AgenticTurnItem.ExecutedTools(
                outcomes = listOf(
                    Pair(
                        call,
                        com.example.ai.tools.ToolExecutionOutcome(
                            toolName = call.toolName,
                            requestId = "REQ-1",
                            envelope = com.example.ai.contracts.ToolResultEnvelope(
                                requestId = "REQ-1",
                                toolName = call.toolName,
                                projectId = "P-IDS",
                                status = com.example.ai.contracts.ToolStatus.PASS,
                                result = "ok"
                            )
                        )
                    )
                )
            )
        )

        val contents = DirectGeminiClientSeam.buildGeminiContents("q", history)

        val replayedFuncCall = contents.getJSONObject(1).getJSONArray("parts")
            .getJSONObject(0).getJSONObject("functionCall")
        assertTrue(
            "no provider id means no id on the wire",
            !replayedFuncCall.has("id")
        )

        val funcResponse = contents.getJSONObject(2).getJSONArray("parts")
            .getJSONObject(0).getJSONObject("functionResponse")
        assertTrue(
            "functionResponse must omit id when the provider supplied none",
            !funcResponse.has("id")
        )
    }

    /**
     * G-3.6 — Multiple calls: each id is correlated to its OWN call and never crossed.
     */
    @Test
    fun multipleCallsKeepTheirOwnIdsUncrossed() {
        val json = """
            {
              "candidates": [
                { "content": { "role": "model", "parts": [
                    { "functionCall": { "id": "id_one",   "name": "alpha", "args": {} } },
                    { "functionCall": { "id": "id_two",   "name": "beta",  "args": {} } },
                    { "functionCall": { "name": "gamma", "args": {} } }
                ] } }
              ]
            }
        """.trimIndent()

        val req = DirectGeminiClientSeam.parseGeminiResponse(json) as CloudLlmClientResponse.ToolCallRequest
        assertEquals(3, req.calls.size)
        assertEquals("id_one", req.calls[0].callId)
        assertEquals("alpha", req.calls[0].toolName)
        assertEquals("id_two", req.calls[1].callId)
        assertEquals("beta", req.calls[1].toolName)
        assertEquals("", req.calls[2].callId)
        assertEquals("gamma", req.calls[2].toolName)

        val parts = DirectGeminiClientSeam.buildGeminiContents(
            "q",
            listOf(com.example.ai.orchestrator.AgenticTurnItem.ModelCalls(calls = req.calls))
        ).getJSONObject(1).getJSONArray("parts")

        assertEquals("id_one", parts.getJSONObject(0).getJSONObject("functionCall").getString("id"))
        assertEquals("id_two", parts.getJSONObject(1).getJSONObject("functionCall").getString("id"))
        assertTrue(
            "third call had no id and must not gain one",
            !parts.getJSONObject(2).getJSONObject("functionCall").has("id")
        )
    }

    /**
     * G-3.7 — Explicit ids supplied by callers (non-provider paths, e.g. tests and the local
     * engine) must keep working unchanged — G-3 must not break id-bearing flows.
     */
    @Test
    fun explicitlySuppliedIdsStillWork() {
        val call = ToolCall(callId = "local_turn_id", toolName = "local_tool")
        val parts = DirectGeminiClientSeam.buildGeminiContents(
            "q",
            listOf(com.example.ai.orchestrator.AgenticTurnItem.ModelCalls(calls = listOf(call)))
        ).getJSONObject(1).getJSONArray("parts").getJSONObject(0).getJSONObject("functionCall")

        assertEquals("local_turn_id", parts.getString("id"))
    }
}
