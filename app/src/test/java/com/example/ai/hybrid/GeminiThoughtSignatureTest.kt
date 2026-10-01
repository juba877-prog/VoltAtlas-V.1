package com.example.ai.hybrid

import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.orchestrator.AgenticTurnItem
import com.example.ai.tools.ToolExecutionOutcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 4 / G-2 — Gemini 3 `thoughtSignature` preservation.
 *
 * Google's `generateContent` `Part` schema defines:
 *   `thoughtSignature` — "An opaque signature for the thought so it can be reused in
 *   subsequent requests. A base64-encoded string."
 * and Google's thinking guide states that in `generateContent` "signatures are metadata that
 * can be attached to any part, such as living inside `functionCall` parts".
 *
 * VoltAtlas drives `generateContent` over raw REST and manages conversation state itself, so
 * it is REQUIRED to capture the signature and replay it verbatim on the next request. Before
 * this fix the seam discarded it entirely, breaking Gemini 3 reasoning continuity across
 * tool-call rounds.
 *
 * Protocol-level (serialization/parser) tests. NOT live-provider verification.
 */
class GeminiThoughtSignatureTest {

    /** Opaque, deliberately non-decodable sample. Must survive byte-identical. */
    private val sigA = "Cq8CZm9kZURlczovZ28vZ2VuZXJhdGVk"
    private val sigB = "Q3JlYXRlZFNpZ25hdHVyZU9mQmxvYg=="

    private fun call(name: String, args: Map<String, JsonElement> = mapOf("projectId" to JsonPrimitive("P-1"))) =
        ToolCall(callId = "call_1", toolName = name, arguments = args)

    private fun modelResponse(partsJson: String): String = """
        {
          "candidates": [
            {
              "content": {
                "role": "model",
                "parts": [ $partsJson ]
              }
            }
          ]
        }
    """.trimIndent()

    // ------------------------------------------------------------------
    // Extraction
    // ------------------------------------------------------------------

    /**
     * G-2.1 — A signature delivered at PART level (the schema-defined location, a sibling of
     * functionCall) is captured.
     */
    @Test
    fun extractsThoughtSignatureFromPartLevel() {
        val response = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                {
                  "functionCall": { "id": "call_1", "name": "get_project_context", "args": { "projectId": "P-1" } },
                  "thoughtSignature": "$sigA"
                }
                """
            )
        )

        val req = response as CloudLlmClientResponse.ToolCallRequest
        assertEquals(1, req.calls.size)
        assertEquals(sigA, req.calls.first().thoughtSignature)
    }

    /**
     * G-2.2 — A signature delivered INSIDE the functionCall object (the location Google's prose
     * describes) is also captured, so neither placement is silently lost.
     */
    @Test
    fun extractsThoughtSignatureFromFunctionCallLevel() {
        val response = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                {
                  "functionCall": {
                    "id": "call_1",
                    "name": "get_project_context",
                    "args": { "projectId": "P-1" },
                    "thoughtSignature": "$sigB"
                  }
                }
                """
            )
        )

        val req = response as CloudLlmClientResponse.ToolCallRequest
        assertEquals(sigB, req.calls.first().thoughtSignature)
    }

    /**
     * G-2.3 — Absence is explicit: no signature => null, never a fabricated value.
     */
    @Test
    fun missingThoughtSignatureIsNullNotFabricated() {
        val response = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                { "functionCall": { "id": "call_1", "name": "get_project_context", "args": {} } }
                """
            )
        )

        val req = response as CloudLlmClientResponse.ToolCallRequest
        assertNull("absence must be represented as null, not invented", req.calls.first().thoughtSignature)
    }

    // ------------------------------------------------------------------
    // Replay
    // ------------------------------------------------------------------

    private fun replayParts(turn: AgenticTurnItem.ModelCalls): JSONArray =
        DirectGeminiClientSeam.buildGeminiContents("q", listOf(turn)).getJSONObject(1).getJSONArray("parts")

    /**
     * G-2.4 — Replay: the signature is written back to the SAME part that carries the
     * functionCall, byte-identical. This is the core round-trip requirement.
     */
    @Test
    fun replaysSignatureVerbatimOnTheFunctionCallPart() {
        val parsed = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                {
                  "functionCall": { "id": "call_1", "name": "get_project_context", "args": { "projectId": "P-1" } },
                  "thoughtSignature": "$sigA"
                }
                """
            )
        ) as CloudLlmClientResponse.ToolCallRequest

        val parts = replayParts(AgenticTurnItem.ModelCalls(calls = parsed.calls))
        val part = parts.getJSONObject(0)

        assertTrue("functionCall must be present", part.has("functionCall"))
        assertEquals("signature must replay byte-identically", sigA, part.getString("thoughtSignature"))

        // Signature must be a SIBLING of functionCall (schema location), not nested inside it.
        assertTrue(
            "signature must not be merged into the functionCall object",
            !part.getJSONObject("functionCall").has("thoughtSignature")
        )
    }

    /**
     * G-2.5 — FAILS IF THE SIGNATURE IS DROPPED: absence in the replay payload must not
     * produce an empty or placeholder signature.
     */
    @Test
    fun signatureIsNeverDroppedOrEmptiedOnReplay() {
        val parsed = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                {
                  "functionCall": { "id": "call_1", "name": "get_project_context", "args": {} },
                  "thoughtSignature": "$sigA"
                }
                """
            )
        ) as CloudLlmClientResponse.ToolCallRequest

        val part = replayParts(AgenticTurnItem.ModelCalls(calls = parsed.calls)).getJSONObject(0)
        assertTrue("signature must not be dropped", part.has("thoughtSignature"))
        assertTrue("signature must not be empty", part.getString("thoughtSignature").isNotBlank())
        assertEquals(sigA, part.getString("thoughtSignature"))
    }

    /**
     * G-2.6 — FAILS IF A SIGNATURE MOVES TO THE WRONG PART: with two sequential calls in one
     * model turn, each part must carry ITS OWN signature, in order, uncrossed.
     */
    @Test
    fun eachSequentialCallKeepsItsOwnSignatureInOrder() {
        val response = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                {
                  "functionCall": { "id": "call_1", "name": "tool_one", "args": {} },
                  "thoughtSignature": "$sigA"
                },
                {
                  "functionCall": { "id": "call_2", "name": "tool_two", "args": {} },
                  "thoughtSignature": "$sigB"
                }
                """
            )
        ) as CloudLlmClientResponse.ToolCallRequest

        assertEquals(2, response.calls.size)
        assertEquals(sigA, response.calls[0].thoughtSignature)
        assertEquals(sigB, response.calls[1].thoughtSignature)

        val parts = replayParts(AgenticTurnItem.ModelCalls(calls = response.calls))
        assertEquals(2, parts.length())
        assertEquals(sigA, parts.getJSONObject(0).getString("thoughtSignature"))
        assertEquals(sigB, parts.getJSONObject(1).getString("thoughtSignature"))

        // Guard against association swap: each part's functionCall name must line up with its signature.
        assertEquals("tool_one", parts.getJSONObject(0).getJSONObject("functionCall").getString("name"))
        assertEquals("tool_two", parts.getJSONObject(1).getJSONObject("functionCall").getString("name"))
    }

    /**
     * G-2.7 — Parallel calls: each functionCall part carries its own signature and part order is
     * preserved (no merging of multiple calls into one part).
     */
    @Test
    fun parallelFunctionCallsAreNotMergedAndKeepSignatures() {
        val response = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                { "functionCall": { "id": "c1", "name": "alpha", "args": {} }, "thoughtSignature": "$sigA" },
                { "functionCall": { "id": "c2", "name": "beta", "args": {} }, "thoughtSignature": "$sigB" },
                { "functionCall": { "id": "c3", "name": "gamma", "args": {} } }
                """
            )
        ) as CloudLlmClientResponse.ToolCallRequest

        assertEquals(3, response.calls.size)
        // Third call had no signature -> explicit null.
        assertNull(response.calls[2].thoughtSignature)

        val parts = replayParts(AgenticTurnItem.ModelCalls(calls = response.calls))
        assertEquals("parallel calls must stay separate parts", 3, parts.length())
        assertEquals(sigA, parts.getJSONObject(0).getString("thoughtSignature"))
        assertEquals(sigB, parts.getJSONObject(1).getString("thoughtSignature"))
        assertTrue(
            "a call without a provider signature must not gain one on replay",
            !parts.getJSONObject(2).has("thoughtSignature")
        )
    }

    /**
     * G-2.8 — Full two-round round trip through the real orchestrator-style history shape:
     * parse a model functionCall, execute deterministically, then rebuild the request and prove
     * the signature survives into the SECOND Gemini turn.
     */
    @Test
    fun signatureSurvivesFullToolRoundTripIntoSecondTurn() {
        val parsed = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                {
                  "functionCall": { "id": "call_1", "name": "size_feeder_cable", "args": { "circuitId": "C-9" } },
                  "thoughtSignature": "$sigA"
                }
                """
            )
        ) as CloudLlmClientResponse.ToolCallRequest

        val toolCallFromProvider = parsed.calls.single()
        val outcome = ToolExecutionOutcome(
            toolName = toolCallFromProvider.toolName,
            requestId = "REQ-9",
            envelope = ToolResultEnvelope(
                requestId = "REQ-9",
                toolName = toolCallFromProvider.toolName,
                projectId = "P-ROUNDTRIP",
                status = ToolStatus.PASS,
                result = "95 mm²"
            )
        )

        val history = listOf(
            AgenticTurnItem.ModelCalls(calls = listOf(toolCallFromProvider)),
            AgenticTurnItem.ExecutedTools(
                outcomes = listOf(Pair(toolCallFromProvider, outcome))
            )
        )

        val contents = DirectGeminiClientSeam.buildGeminiContents(
            userPrompt = "size feeder C-9",
            conversationHistory = history
        )

        assertEquals(3, contents.length())
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals(
            "signature must survive into the SECOND Gemini turn",
            sigA,
            contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("thoughtSignature")
        )
        assertEquals("user", contents.getJSONObject(2).getString("role"))
        assertNotNull(
            "second turn must carry the functionResponse",
            contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
        )
    }

    /**
     * G-2.9 — The signature is never logged/exposed as readable content: it stays opaque.
     * Guards against a future refactor decoding it into the assistant transcript.
     */
    @Test
    fun signatureIsTreatedAsOpaqueData() {
        val parsed = DirectGeminiClientSeam.parseGeminiResponse(
            modelResponse(
                """
                { "functionCall": { "id": "call_1", "name": "get_project_context", "args": {} }, "thoughtSignature": "$sigA" }
                """
            )
        ) as CloudLlmClientResponse.ToolCallRequest

        val sig = parsed.calls.first().thoughtSignature
        assertNotNull(sig)
        // Round-trips identically through the request builder -> unchanged opaque value.
        val part = replayParts(AgenticTurnItem.ModelCalls(calls = parsed.calls)).getJSONObject(0)
        assertEquals(sig, part.getString("thoughtSignature"))
    }
}
