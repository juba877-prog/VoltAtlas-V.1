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
            return CloudLlmClientResponse.ProviderError(
                message = "Gemini API personal credential not configured. Please add your API key in AI Settings."
            )
        }

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

                    AiLogger.i("VoltAtlas_AI", "DIRECT_GEMINI_REQUEST model=${config.modelName} endpoint=https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:generateContent credentialConfigured=${!apiKey.isNullOrBlank()}")
                    val startTime = System.currentTimeMillis()

                    val response = httpClient.newCall(request).execute()
                    val latencyMs = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string().orEmpty()
                    val responseCode = response.code

                    AiLogger.i("VoltAtlas_AI", "DIRECT_GEMINI_RESPONSE httpStatus=$responseCode latencyMs=$latencyMs")

                    if (response.isSuccessful) {
                        parseGeminiResponse(responseBody)
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
            CloudLlmClientResponse.ProviderError("Network connection unavailable. Please check your internet connection.", cause = e)
        } catch (e: IOException) {
            CloudLlmClientResponse.ProviderError("Network error connecting to Gemini API: ${e.message}", cause = e)
        } catch (e: Throwable) {
            CloudLlmClientResponse.ProviderError(e.message ?: "Unexpected error during Gemini API call.", cause = e)
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
                        ConnectionTestResult(
                            status = ConnectionTestStatus.CONNECTED,
                            message = "Connected to $modelName successfully"
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
                                }
                                parts.put(JSONObject().put("functionCall", funcCall))
                            }
                            put("parts", parts)
                        }
                        contents.put(modelTurn)
                    }
                    is AgenticTurnItem.ExecutedTools -> {
                        val functionTurn = JSONObject().apply {
                            put("role", "function")
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
                        val callId = fc.optString("id").takeIf { it.isNotBlank() }
                            ?: fc.optString("call_id").takeIf { it.isNotBlank() }
                            ?: java.util.UUID.randomUUID().toString()
                        val argsObj = fc.optJSONObject("args")
                        val argsMap: Map<String, JsonElement> = if (argsObj != null) {
                            runCatching {
                                Json.parseToJsonElement(argsObj.toString()).jsonObject
                            }.getOrDefault(emptyMap())
                        } else {
                            emptyMap()
                        }
                        functionCalls.add(ToolCall(callId = callId, toolName = name, arguments = argsMap))
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
            val message = try {
                val json = JSONObject(body)
                json.optJSONObject("error")?.optString("message") ?: body
            } catch (_: Throwable) {
                body
            }

            val isRateLimited = code == 429 || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
            val category = when {
                code == 429 || isRateLimited -> "RATE_LIMITED"
                code in 401..403 -> "AUTHENTICATION_FAILURE"
                code == 400 -> "INVALID_REQUEST"
                code in 500..599 -> "SERVER_ERROR"
                else -> "PROVIDER_ERROR"
            }
            AiLogger.e("VoltAtlas_AI", "DIRECT_GEMINI_ERROR: Cloud failure reason=$category, HTTP status=$code, provider error category=$category, sanitized provider message=$message")
            return CloudLlmClientResponse.ProviderError(
                message = "Gemini API error ($code): $message",
                isRateLimited = isRateLimited
            )
        }
    }
}
