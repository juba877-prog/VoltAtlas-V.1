package com.example.ai.hybrid

import com.example.ai.contracts.ToolResultEnvelope
import com.example.ai.contracts.ToolStatus
import com.example.ai.orchestrator.AgenticTurnItem
import com.example.ai.tools.ToolExecutionOutcome
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 4 / G-1 — Gemini `generateContent` wire-contract regression tests.
 *
 * Root cause these tests lock: the seam emitted the tool-result turn with
 * `role = "function"`. Google's `Content.role` schema states verbatim:
 *
 *   "The producer of the content. Must be either 'user' or 'model'."
 *
 * `generateContent` therefore has no `function` role; tool results must be returned as a
 * `role = "user"` content whose parts carry `functionResponse`. The invalid role produced
 * HTTP 400 INVALID_ARGUMENT on the second turn, which the router degraded to the local
 * engine and the UI rendered as "OFFLINE" — while Settings (a tools-free ping) had already
 * reported "Connected".
 *
 * These tests are protocol-level (serialization) tests. They are NOT live-provider
 * verification and are deliberately never labelled as such.
 *
 * Deliberately NOT asserted here (per the audit):
 *  - `system_instruction` and `function_declarations` snake_case keys are ACCEPTED by the
 *    API and are left untouched.
 *  - `functionCall` uses `args` (NOT `arguments`) — that is correct for generateContent.
 */
class GeminiWireContractTest {

    private val seam = DirectGeminiClientSeam(
        apiKeyProvider = { "AIzaWireContractTestKeyNotARealCredential" }
    )

    private fun toolCall(callId: String, name: String = "size_feeder_cable") = ToolCall(
        callId = callId,
        toolName = name,
        arguments = mapOf("circuitId" to JsonPrimitive("C-101"))
    )

    private fun executed(call: ToolCall) = AgenticTurnItem.ExecutedTools(
        outcomes = listOf(
            Pair(
                call,
                ToolExecutionOutcome(
                    toolName = call.toolName,
                    requestId = "REQ-1",
                    envelope = ToolResultEnvelope(
                        requestId = "REQ-1",
                        toolName = call.toolName,
                        projectId = "P-WIRE",
                        status = ToolStatus.PASS,
                        result = "50 mm²"
                    )
                )
            )
        )
    )

    private fun contentsOf(history: List<AgenticTurnItem>): JSONArray =
        DirectGeminiClientSeam.buildGeminiContents("user question", history)

    /**
     * G-1.1 — Every emitted Content.role is valid per the official schema.
     * This is the discriminating assertion: it FAILS on the pre-fix code because the
     * tool-result turn emitted role="function".
     */
    @Test
    fun everyEmittedContentRoleIsValid_userOrModel() {
        val contents = contentsOf(
            listOf(
                AgenticTurnItem.ModelCalls(calls = listOf(toolCall("call_1"))),
                executed(toolCall("call_1"))
            )
        )

        assertTrue("contents must not be empty", contents.length() > 0)

        var sawFunctionResponseTurn = false
        for (i in 0 until contents.length()) {
            val content = contents.getJSONObject(i)
            val role = content.optString("role")
            assertTrue(
                "Content[$i] has INVALID role '$role'. generateContent Content.role must be " +
                    "either 'user' or 'model' (offical schema: Content.role).",
                role == "user" || role == "model"
            )
            val parts = content.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                if (parts.getJSONObject(j).has("functionResponse")) {
                    sawFunctionResponseTurn = true
                }
            }
        }
        assertTrue("expected at least one functionResponse turn in the history", sawFunctionResponseTurn)
    }

    /**
     * G-1.2 — The tool-result turn uses role="user" and carries functionResponse parts.
     */
    @Test
    fun toolResultTurnUsesRoleUserAndCarriesFunctionResponse() {
        val contents = contentsOf(
            listOf(
                AgenticTurnItem.ModelCalls(calls = listOf(toolCall("call_1"))),
                executed(toolCall("call_1"))
            )
        )

        // contents = [ userPrompt, modelTurn(functionCall), toolTurn(functionResponse) ]
        assertEquals(3, contents.length())

        val toolTurn = contents.getJSONObject(2)
        assertEquals("user", toolTurn.getString("role"))

        val parts = toolTurn.getJSONArray("parts")
        assertEquals(1, parts.length())
        val part = parts.getJSONObject(0)
        assertTrue("tool-result part must contain functionResponse", part.has("functionResponse"))
    }

    /**
     * G-1.3 — functionResponse carries the tool name, the structured response, and the
     * provider function-call id when one was supplied.
     */
    @Test
    fun functionResponseCarriesNameResponseAndProviderId() {
        val contents = contentsOf(
            listOf(
                AgenticTurnItem.ModelCalls(calls = listOf(toolCall("provider_call_id_42"))),
                executed(toolCall("provider_call_id_42"))
            )
        )

        val funcResponse = contents.getJSONObject(2)
            .getJSONArray("parts").getJSONObject(0)
            .getJSONObject("functionResponse")

        assertEquals("size_feeder_cable", funcResponse.getString("name"))
        assertTrue("functionResponse must carry a 'response' object", funcResponse.has("response"))
        assertNotNull(funcResponse.getJSONObject("response"))
        assertEquals("provider_call_id_42", funcResponse.getString("id"))
    }

    /**
     * G-1.4 — No `role: "function"` survives anywhere in the generated request body.
     * This scans the whole serialized request, not just the history helper, so a regression
     * that reintroduces the role elsewhere (e.g. inside buildRequestJson) is also caught.
     */
    @Test
    fun noRoleFunctionRemainsAnywhereInGeneratedRequest() {
        val declaration = CloudFunctionDeclaration(
            name = "size_feeder_cable",
            description = "Sizes electrical feeder cable per IEC 60364",
            parameters = listOf(
                CloudFunctionParameter("circuitId", "STRING", "Feeder ID", true)
            ),
            requiredParameters = listOf("circuitId")
        )

        val payload = seam.buildRequestJson(
            systemPrompt = "You are VoltAtlas engineering assistant.",
            userPrompt = "What is the recommended size?",
            conversationHistory = listOf(
                AgenticTurnItem.ModelCalls(calls = listOf(toolCall("call_1"))),
                executed(toolCall("call_1"))
            ),
            tools = listOf(declaration)
        )

        assertTrue(
            "generated request must never contain role=function",
            !payload.toString().contains("\"role\":\"function\"") &&
                !payload.toString().contains("\"role\": \"function\"")
        )

        // And positively: every role in the payload is user|model.
        val contents = payload.getJSONArray("contents")
        for (i in 0 until contents.length()) {
            val role = contents.getJSONObject(i).optString("role")
            assertTrue("Content[$i] role '$role' invalid", role == "user" || role == "model")
        }
    }

    /**
     * G-1.5 — Ordinary user/model history semantics are preserved by the fix.
     * The initial prompt stays role=user, the model's functionCall turn stays role=model,
     * and the functionCall arguments remain under `args` (the correct generateContent field).
     */
    @Test
    fun ordinaryHistorySemanticsAndArgsFieldArePreserved() {
        val contents = contentsOf(
            listOf(AgenticTurnItem.ModelCalls(calls = listOf(toolCall("call_1"))))
        )

        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))

        val funcCall = contents.getJSONObject(1)
            .getJSONArray("parts").getJSONObject(0)
            .getJSONObject("functionCall")

        assertEquals("size_feeder_cable", funcCall.getString("name"))
        // generateContent uses `args`, NOT `arguments`.
        assertTrue("functionCall must use 'args'", funcCall.has("args"))
        assertTrue("functionCall must NOT use 'arguments'", !funcCall.has("arguments"))
    }

    /**
     * G-1.6 — Ordering is preserved: prompt -> model(functionCall) -> user(functionResponse).
     * A fix that reordered or merged turns would silently change tool-call semantics.
     */
    @Test
    fun turnOrderingIsPreserved() {
        val contents = contentsOf(
            listOf(
                AgenticTurnItem.ModelCalls(calls = listOf(toolCall("call_1"))),
                executed(toolCall("call_1"))
            )
        )

        val kinds = (0 until contents.length()).map { i ->
            val parts = contents.getJSONObject(i).optJSONArray("parts") ?: JSONArray()
            when {
                parts.length() > 0 && parts.getJSONObject(0).has("text") -> "text"
                parts.length() > 0 && parts.getJSONObject(0).has("functionCall") -> "functionCall"
                parts.length() > 0 && parts.getJSONObject(0).has("functionResponse") -> "functionResponse"
                else -> "other"
            }
        }
        assertEquals(listOf("text", "functionCall", "functionResponse"), kinds)
    }
}
