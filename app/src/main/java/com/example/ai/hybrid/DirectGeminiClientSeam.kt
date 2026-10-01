package com.example.ai.hybrid

import com.example.ai.orchestrator.AgenticTurnItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Direct Gemini API client seam implementing Bring-Your-Own-Key (BYOK) architecture.
 *
 * Transmits user queries, agentic conversation history, and function calls directly
 * to Google's Generative Language API endpoint (gemini-3.8-flash).
 *
 * Invariants:
 * - Operates independently of Firebase (requires zero FirebaseApp or App Check).
 * - Receives credential at runtime from user-configured [SecureCredentialStorage].
 * - Supports the full Agentic Tool Loop: function declarations, function call requests,
 *   function responses, and final model synthesis.
 * - Never logs or leaks raw credentials.
 */
class DirectGeminiClientSeam(
    private val apiKeyProvider: () -> String?,
    val config: AiModelConfig = AiModelConfig.current(),
    private val httpClient: OkHttpClient = defaultHttpClient(config.timeoutMs),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val customCallHandler: (suspend (systemPrompt: String, userPrompt: String, history: List<AgenticTurnItem>, tools: List<CloudFunctionDeclaration>) -> CloudLlmClientResponse)? = null
) : GeminiClientSeam {

    override fun isConfigured(): Boolean {
        val key = apiKeyProvider()?.trim()
        return !key.isNullOrEmpty() && key != "placeholder_for_secrets"
    }

    fun getApiKey(): String? = apiKeyProvider()?.trim()

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmRawResponse {
        val clientResponse = generateTurn(systemPrompt, userPrompt, emptyList(), tools)
        return CloudLlmRawResponse.fromClientResponse(clientResponse)
    }

    override suspend fun generateTurn(
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<AgenticTurnItem>,
        tools: List<CloudFunctionDeclaration>
    ): CloudLlmClientResponse {
        val apiKey = apiKeyProvider()?.trim()
        if (apiKey.isNullOrEmpty() || apiKey == "placeholder_for_secrets") {
            AiDiagnostics.stage(
                AiDiagnostics.Stage.CONFIGURATION,
                "result" to "FAIL", "reason" to "KEY_NOT_CONFIGURED", "model" to config.modelName
            )
            return CloudLlmClientResponse.ProviderError(
                message = "Gemini API personal credential not configured. Please add your API key in AI Settings."
            )
        }
        // A stored credential proves CONFIGURED only. It does NOT yet prove authentication.
        AiDiagnostics.stage(
            AiDiagnostics.Stage.CONFIGURATION,
            "result" to "OK", "model" to config.modelName, "credentialPresent" to true
        )

        if (customCallHandler != null) {
            return customCallHandler.invoke(systemPrompt, userPrompt, conversationHistory, tools)
        }

        return try {
            withContext(ioDispatcher) {
                withTimeout(config.timeoutMs) {
                    val requestPayload = buildRequestJson(systemPrompt, userPrompt, conversationHistory, tools)
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:generateContent"

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = requestPayload.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-goog-api-key", apiKey)
                        .post(requestBody)
                        .build()

                    // Phase 6 Batch 4 / G-5-observability: enough metadata to identify exactly which
                    // protocol stage failed. Deliberately records NO credential, NO Authorization
                    // header, and NO conversation content — only counts, names and timings.
                    AiDiagnostics.stage(
                        AiDiagnostics.Stage.NETWORK,
                        "result" to "OK",
                        "model" to config.modelName,
                        "toolCount" to tools.size,
                        "isSecondTurn" to conversationHistory.any { it is AgenticTurnItem.ExecutedTools },
                        "turnIndex" to (conversationHistory.count { it is AgenticTurnItem.ExecutedTools } + 1)
                    )
                    AiLogger.i(
                        "VoltAtlas_AI",
                        "DIRECT_GEMINI_REQUEST model=${config.modelName} " +
                            "endpointFamily=generativelanguage/v1beta:generateContent " +
                            "toolCount=${tools.size} " +
                            "historyTurns=${conversationHistory.size} " +
                            "turnIndex=${conversationHistory.count { it is AgenticTurnItem.ExecutedTools } + 1} " +
                            "isSecondTurn=${conversationHistory.any { it is AgenticTurnItem.ExecutedTools }} " +
                            "credentialConfigured=${!apiKey.isNullOrBlank()}"
                    )
                    val startTime = System.currentTimeMillis()

                    val response = httpClient.newCall(request).execute()
                    val latencyMs = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string().orEmpty()
                    val responseCode = response.code

                    AiLogger.i("VoltAtlas_AI", "DIRECT_GEMINI_RESPONSE httpStatus=$responseCode latencyMs=$latencyMs")
                    AiDiagnostics.stage(
                        AiDiagnostics.Stage.HTTP_STATUS,
                        "result" to if (response.isSuccessful) "OK" else "FAIL",
                        "httpStatus" to responseCode,
                        "latencyMs" to latencyMs
                    )

                    if (response.isSuccessful) {
                        val parsed = parseGeminiResponse(responseBody)
                        AiDiagnostics.stage(
                            AiDiagnostics.Stage.MODEL_RESPONSE,
                            "result" to "OK",
                            "outcome" to parsed::class.simpleName,
                            "httpStatus" to responseCode
                        )
                        val isToolCall = parsed is CloudLlmClientResponse.ToolCallRequest
                        AiDiagnostics.stage(
                            AiDiagnostics.Stage.FUNCTION_CALL,
                            "result" to if (isToolCall) "RECEIVED" else "NONE",
                            "toolCount" to ((parsed as? CloudLlmClientResponse.ToolCallRequest)?.calls?.size ?: 0),
                            "toolNames" to ((parsed as? CloudLlmClientResponse.ToolCallRequest)
                                ?.calls?.joinToString(",") { it.toolName }.orEmpty()),
                            "signatureCount" to ((parsed as? CloudLlmClientResponse.ToolCallRequest)
                                ?.calls?.count { !it.thoughtSignature.isNullOrBlank() } ?: 0)
                        )
                        // Record the parser outcome so a silent protocol break is visible in logs.
                        AiLogger.i(
                            "VoltAtlas_AI",
                            "DIRECT_GEMINI_PARSED outcome=${parsed::class.simpleName} " +
                                "functionCallPresent=${parsed is CloudLlmClientResponse.ToolCallRequest} " +
                                "toolNames=${(parsed as? CloudLlmClientResponse.ToolCallRequest)
                                    ?.calls?.joinToString(",") { it.toolName }.orEmpty()} " +
                                "signaturesPresent=${(parsed as? CloudLlmClientResponse.ToolCallRequest)
                                    ?.calls?.count { !it.thoughtSignature.isNullOrBlank() } ?: 0}"
                        )
                        parsed
                    } else {
                        parseErrorResponse(responseCode, responseBody)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            CloudLlmClientResponse.Timeout(config.timeoutMs)
        } catch (e: SocketTimeoutException) {
            CloudLlmClientResponse.Timeout(config.timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            // Phase 6 Batch 4 / G-4: a DNS/connectivity failure is NOT a timeout and NOT a
            // provider fault — it is classified so it can be reported honestly.
            CloudLlmClientResponse.ProviderError(
                message = "Network connection unavailable. Please check your internet connection.",
                cause = e,
                category = CloudProviderFailureCategory.NETWORK_ERROR
            )
        } catch (e: IOException) {
            CloudLlmClientResponse.ProviderError(
                message = "Network error connecting to Gemini API: ${e.message}",
                cause = e,
                category = CloudProviderFailureCategory.NETWORK_ERROR
            )
        } catch (e: Throwable) {
            CloudLlmClientResponse.ProviderError(
                message = e.message ?: "Unexpected error during Gemini API call.",
                cause = e,
                category = CloudProviderFailureCategory.UNKNOWN
            )
        }
    }

    internal fun buildRequestJson(
        systemPrompt: String,
        userPrompt: String,
        conversationHistory: List<AgenticTurnItem>,
        tools: List<CloudFunctionDeclaration>
    ): JSONObject {
        val root = JSONObject()

        if (systemPrompt.isNotBlank()) {
            val sysPart = JSONObject().put("text", systemPrompt)
            val sysParts = JSONArray().put(sysPart)
            root.put("system_instruction", JSONObject().put("parts", sysParts))
        }

        val contents = buildGeminiContents(userPrompt, conversationHistory)
        root.put("contents", contents)

        if (tools.isNotEmpty()) {
            val declarationsArray = JSONArray()
            for (tool in tools) {
                declarationsArray.put(toGeminiFunctionDeclaration(tool))
            }
            val toolsArray = JSONArray().put(JSONObject().put("function_declarations", declarationsArray))
            root.put("tools", toolsArray)
        }

        val genConfig = JSONObject()
        genConfig.put("temperature", config.temperature)
        genConfig.put("topP", config.topP)
        config.maxOutputTokens?.let { genConfig.put("maxOutputTokens", it) }
        root.put("generationConfig", genConfig)

        return root
    }

    companion object {
        fun defaultHttpClient(timeoutMs: Long): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(minOf(timeoutMs, 10_000L), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        }

        suspend fun testConnection(
            apiKey: String,
            modelName: String = AiModelConfig.DEFAULT_MODEL_NAME,
            httpClient: OkHttpClient = defaultHttpClient(8_000L)
        ): ConnectionTestResult = withContext(Dispatchers.IO) {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isEmpty() || trimmedKey == "placeholder_for_secrets") {
                return@withContext ConnectionTestResult(
                    status = ConnectionTestStatus.NOT_CONFIGURED,
                    message = "Gemini API key is not configured"
                )
            }

            try {
                val pingPayload = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val part = JSONObject().put("text", "ping")
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(part))
                        })
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().put("maxOutputTokens", 5))
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = pingPayload.toString().toRequestBody(mediaType)
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", trimmedKey)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()
                val code = response.code

                when {
                    response.isSuccessful -> {
                        // Phase 6 Batch 4 / G-6: this test performed an authenticated
                        // generateContent call to the CONFIGURED model using the same endpoint
                        // family and HTTP stack as production, so it proves connectivity and
                        // model access — and nothing more. It does NOT run the agentic tool loop,
                        // so it must not imply the assistant runtime works.
                        ConnectionTestResult(
                            status = ConnectionTestStatus.CONNECTED,
                            message = "Connected to $modelName successfully " +
                                "(connectivity verified; assistant tool loop not exercised by this test)",
                            verifiedScope = GeminiVerificationScope.CONNECTIVITY_OK
                        )
                    }
                    code == 400 && responseBody.contains("API_KEY_INVALID", ignoreCase = true) -> {
                        ConnectionTestResult(
                            status = ConnectionTestStatus.INVALID_CREDENTIAL,
                            message = "Invalid API key (API_KEY_INVALID)"
                        )
                    }
                    code == 401 || code == 403 -> {
                        ConnectionTestResult(
                            status = ConnectionTestStatus.INVALID_CREDENTIAL,
                            message = "Unauthorized: check Gemini API key permissions"
                        )
                    }
                    code == 404 -> {
                        ConnectionTestResult(
                            status = ConnectionTestStatus.MODEL_UNAVAILABLE,
                            message = "Model '$modelName' not found or unsupported"
                        )
                    }
                    code == 429 || responseBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) -> {
                        ConnectionTestResult(
                            status = ConnectionTestStatus.RATE_LIMITED,
                            message = "Quota limit reached (RESOURCE_EXHAUSTED)"
                        )
                    }
                    else -> {
                        ConnectionTestResult(
                            status = ConnectionTestStatus.PROVIDER_ERROR,
                            message = "Provider error (HTTP $code)"
                        )
                    }
                }
            } catch (e: SocketTimeoutException) {
                ConnectionTestResult(ConnectionTestStatus.TIMEOUT, "Connection timed out")
            } catch (e: UnknownHostException) {
                ConnectionTestResult(ConnectionTestStatus.NETWORK_ERROR, "Network unavailable: cannot resolve host")
            } catch (e: IOException) {
                ConnectionTestResult(ConnectionTestStatus.NETWORK_ERROR, "Network error: ${e.message}")
            } catch (e: Throwable) {
                ConnectionTestResult(ConnectionTestStatus.PROVIDER_ERROR, e.message ?: "Connection test error")
            }
        }

        fun buildGeminiContents(
            userPrompt: String,
            conversationHistory: List<AgenticTurnItem>
        ): JSONArray {
            val contents = JSONArray()

            // 1. Initial user message
            val initialUser = JSONObject().apply {
                put("role", "user")
                val parts = JSONArray().put(JSONObject().put("text", userPrompt))
                put("parts", parts)
            }
            contents.put(initialUser)

            // 2. Multi-turn history (model calls and function results)
            for (turn in conversationHistory) {
                when (turn) {
                    is AgenticTurnItem.ModelCalls -> {
                        val modelTurn = JSONObject().apply {
                            put("role", "model")
                            val parts = JSONArray()
                            for (call in turn.calls) {
                                val funcCall = JSONObject().apply {
                                    put("name", call.toolName)
                                    put("args", JSONObject(call.arguments))
                                    if (!call.callId.isNullOrBlank()) {
                                        put("id", call.callId)
                                    }
                                }
                                val part = JSONObject().put("functionCall", funcCall)
                                // Phase 6 Batch 4 / G-2: replay Gemini 3's thought signature on the
                                // SAME part that carried the functionCall, byte-identical. Required
                                // because VoltAtlas manages conversation state manually; omitting
                                // it breaks Gemini 3 reasoning continuity across tool rounds.
                                // When the provider sent no signature, nothing is emitted (absence
                                // is explicit, never fabricated).
                                call.thoughtSignature?.takeIf { it.isNotBlank() }?.let { sig ->
                                    part.put("thoughtSignature", sig)
                                }
                                parts.put(part)
                            }
                            put("parts", parts)
                        }
                        contents.put(modelTurn)
                    }
                    is AgenticTurnItem.ExecutedTools -> {
                        val functionTurn = JSONObject().apply {
                            // Phase 6 Batch 4 / G-1: generateContent's Content.role accepts ONLY
                            // "user" or "model" (verified against the official schema at
                            // https://ai.google.dev/api/generate-content -> Content.role:
                            // "The producer of the content. Must be either 'user' or 'model'.").
                            // Tool results are therefore returned as a role="user" content whose
                            // parts carry `functionResponse` — there is no "function" role.
                            // The previous role="function" made every tool-result turn a
                            // structurally invalid request (HTTP 400 INVALID_ARGUMENT), which the
                            // router surfaced to the user as the misleading "OFFLINE" badge even
                            // though Settings had already reported "Connected".
                            put("role", "user")
                            val parts = JSONArray()
                            for ((call, outcome) in turn.outcomes) {
                                val responseObj = JSONObject(ToolResultProviderSerializer.toProviderResponse(outcome).toString())
                                val funcResponse = JSONObject().apply {
                                    put("name", call.toolName)
                                    put("response", responseObj)
                                    if (call.callId.isNotBlank()) {
                                        put("id", call.callId)
                                    }
                                }
                                parts.put(JSONObject().put("functionResponse", funcResponse))
                            }
                            put("parts", parts)
                        }
                        contents.put(functionTurn)
                    }
                }
            }

            return contents
        }

        fun toGeminiFunctionDeclaration(declaration: CloudFunctionDeclaration): JSONObject {
            val decl = JSONObject()
            decl.put("name", declaration.name)
            decl.put("description", declaration.description)

            val parameters = JSONObject()
            parameters.put("type", "OBJECT")
            val properties = JSONObject()
            val required = JSONArray()

            for (param in declaration.parameters) {
                properties.put(param.name, toGeminiSchema(param))
                if (param.required) {
                    required.put(param.name)
                }
            }

            parameters.put("properties", properties)
            if (required.length() > 0) {
                parameters.put("required", required)
            }
            decl.put("parameters", parameters)

            return decl
        }

        fun toGeminiSchema(param: CloudFunctionParameter): JSONObject {
            val schema = JSONObject()
            when (param.type) {
                "STRING" -> schema.put("type", "STRING")
                "INTEGER" -> schema.put("type", "INTEGER")
                "NUMBER" -> schema.put("type", "NUMBER")
                "BOOLEAN" -> schema.put("type", "BOOLEAN")
                "ARRAY<STRING>" -> {
                    schema.put("type", "ARRAY")
                    schema.put("items", JSONObject().put("type", "STRING"))
                }
                "ARRAY<OBJECT>" -> {
                    schema.put("type", "ARRAY")
                    val items = JSONObject().put("type", "OBJECT")
                    val props = JSONObject()
                    val req = JSONArray()
                    for (field in param.fields) {
                        props.put(field.name, toGeminiSchema(field))
                        if (field.required) req.put(field.name)
                    }
                    items.put("properties", props)
                    if (req.length() > 0) items.put("required", req)
                    schema.put("items", items)
                }
                "OBJECT" -> {
                    schema.put("type", "OBJECT")
                    val props = JSONObject()
                    val req = JSONArray()
                    for (field in param.fields) {
                        props.put(field.name, toGeminiSchema(field))
                        if (field.required) req.put(field.name)
                    }
                    schema.put("properties", props)
                    if (req.length() > 0) schema.put("required", req)
                }
                else -> schema.put("type", "STRING")
            }

            if (param.description.isNotBlank()) {
                schema.put("description", param.description)
            }

            return schema
        }

        fun parseGeminiResponse(responseJsonStr: String): CloudLlmClientResponse {
            val root = JSONObject(responseJsonStr)

            if (root.has("error")) {
                val error = root.getJSONObject("error")
                val code = error.optInt("code")
                val message = error.optString("message", "Gemini API error")
                val status = error.optString("status")
                val isRateLimited = code == 429 || status == "RESOURCE_EXHAUSTED"
                return CloudLlmClientResponse.ProviderError(message = message, isRateLimited = isRateLimited)
            }

            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val promptFeedback = root.optJSONObject("promptFeedback")
                val blockReason = promptFeedback?.optString("blockReason")
                return if (!blockReason.isNullOrBlank()) {
                    CloudLlmClientResponse.SafetyBlock("Prompt blocked by safety filters: $blockReason")
                } else {
                    CloudLlmClientResponse.MalformedResponse("Empty candidates returned by Gemini API")
                }
            }

            val firstCandidate = candidates.getJSONObject(0)
            val finishReason = firstCandidate.optString("finishReason")
            if (finishReason in listOf("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII")) {
                return CloudLlmClientResponse.SafetyBlock(
                    firstCandidate.optString("finishMessage").ifBlank { "Blocked by safety filter ($finishReason)" }
                )
            }

            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val functionCalls = mutableListOf<ToolCall>()
                val textBuilder = StringBuilder()

                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        val name = fc.getString("name")
                        // Phase 6 Batch 4 / G-3: the provider's function-call id is preserved
                        // EXACTLY when supplied. When the provider omits it, the absence is
                        // represented explicitly as an empty string rather than fabricated with a
                        // locally generated UUID — a random UUID would falsely imply Google issued
                        // that identifier. Google's FunctionCall.id is documented as optional:
                        // "If populated, the client to execute the functionCall and return the
                        // response with the matching id." An empty id is therefore valid and the
                        // functionResponse simply omits the `id` field (see buildGeminiContents).
                        val callId = fc.optString("id").takeIf { it.isNotBlank() }
                            ?: fc.optString("call_id").takeIf { it.isNotBlank() }
                            ?: ""
                        // Phase 6 Batch 4 / G-2: capture Gemini 3's opaque thought signature
                        // EXACTLY as received. It is documented on the `Part` (a sibling of
                        // `functionCall`) and Google's prose also describes signatures "living
                        // inside functionCall parts", so both locations are read and the
                        // Part-level value wins. The value is stored verbatim as opaque data:
                        // never decoded, re-encoded, or regenerated. Absence stays null.
                        val thoughtSignature = part.optString("thoughtSignature").takeIf { it.isNotBlank() }
                            ?: fc.optString("thoughtSignature").takeIf { it.isNotBlank() }
                        val argsObj = fc.optJSONObject("args")
                        val argsMap: Map<String, JsonElement> = if (argsObj != null) {
                            runCatching {
                                Json.parseToJsonElement(argsObj.toString()).jsonObject
                            }.getOrDefault(emptyMap())
                        } else {
                            emptyMap()
                        }
                        functionCalls.add(
                            ToolCall(
                                callId = callId,
                                toolName = name,
                                arguments = argsMap,
                                thoughtSignature = thoughtSignature
                            )
                        )
                    } else if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                }

                if (functionCalls.isNotEmpty()) {
                    val first = functionCalls.first()
                    AiLogger.i("VoltAtlas_AI", "toolCallReceived=true toolName=${first.toolName} argumentKeys=${first.arguments.keys}")
                    return CloudLlmClientResponse.ToolCallRequest(calls = functionCalls)
                } else {
                    AiLogger.i("VoltAtlas_AI", "toolCallReceived=false toolName=none argumentKeys=[]")
                }

                val text = textBuilder.toString()
                if (text.isNotBlank()) {
                    return CloudLlmClientResponse.FinalText(text = text)
                }
            }

            return CloudLlmClientResponse.MalformedResponse("Response contained neither valid text nor function calls")
        }

        private fun parseErrorResponse(code: Int, body: String): CloudLlmClientResponse {
            // Provider error body is read for its `error.status`/`error.message` only. It never
            // contains the credential, and the credential is never logged.
            val providerStatus = try {
                JSONObject(body).optJSONObject("error")?.optString("status").orEmpty()
            } catch (_: Throwable) {
                ""
            }
            val message = try {
                val json = JSONObject(body)
                json.optJSONObject("error")?.optString("message") ?: body
            } catch (_: Throwable) {
                body
            }

            val isRateLimited = code == 429 ||
                providerStatus.equals("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                message.contains("RESOURCE_EXHAUSTED", ignoreCase = true)

            // Phase 6 Batch 4 / G-4: classify from the real evidence (HTTP status + the
            // provider's own status token) instead of collapsing everything into one
            // provider error. Order matters: the most specific signal wins.
            val category = when {
                isRateLimited -> CloudProviderFailureCategory.RATE_LIMITED
                code == 401 || code == 403 -> CloudProviderFailureCategory.AUTHENTICATION_FAILURE
                providerStatus.equals("PERMISSION_DENIED", ignoreCase = true) ||
                    providerStatus.equals("UNAUTHENTICATED", ignoreCase = true) ->
                    CloudProviderFailureCategory.AUTHENTICATION_FAILURE
                code == 404 || providerStatus.equals("NOT_FOUND", ignoreCase = true) ->
                    CloudProviderFailureCategory.MODEL_UNAVAILABLE
                code == 400 -> CloudProviderFailureCategory.REQUEST_INVALID
                code in 500..599 -> CloudProviderFailureCategory.SERVER_ERROR
                else -> CloudProviderFailureCategory.UNKNOWN
            }

            // Sanitized diagnostics: status, category and the provider's message. No credential,
            // no Authorization header, no conversation content.
            AiLogger.e(
                "VoltAtlas_AI",
                "DIRECT_GEMINI_ERROR category=$category httpStatus=$code " +
                    "providerStatus=${providerStatus.ifBlank { "n/a" }} message=$message"
            )

            return CloudLlmClientResponse.ProviderError(
                message = "Gemini API error ($code): $message",
                isRateLimited = isRateLimited,
                category = category,
                httpStatus = code
            )
        }
    }
}
