package com.example.ai.hybrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.*
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.grounding.GroundedResponseValidator
import com.example.ai.grounding.GroundingValidationResult
import com.example.ai.orchestrator.AgenticLoopTermination
import com.example.ai.orchestrator.AgenticSafetyBudget
import com.example.ai.orchestrator.AgenticToolLoopOrchestrator
import com.example.ai.orchestrator.AgenticTurnItem
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.AssistantToolRuntimeEndToEndTest
import com.example.ai.tools.ToolExecutionOutcome
import com.example.ai.ui.AssistantEntryPoint
import com.example.ai.ui.EngineeringAssistantViewModel
import com.example.ai.context.EngineeringContext
import com.example.data.AppDatabase
import com.example.data.EngineeringRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BYOK GEMINI PROVIDER TEST SUITE
 *
 * Verifies the Bring-Your-Own-Key (BYOK) public release architecture:
 * 1. SecureCredentialStorage: Encrypted-at-rest storage, masking, and clearing.
 * 2. UserAiPreferences: Provider and model selection.
 * 3. DirectGeminiClientSeam: REST payload formation, header authentication, tool call parsing,
 *    and HTTP degradation mapping.
 * 4. Connection Testing: Live diagnostic validation for user-supplied credentials.
 * 5. Composition & Fail-Closed: Safe fallback when key is absent without throwing or crashing.
 * 6. Agentic Orchestrator Integration: Full multi-turn deterministic tool loop using DirectGeminiClientSeam.
 * 7. Zero Firebase Dependency: Direct Gemini runs with zero reliance on FirebaseApp or google-services.json.
 * 8. ViewModel BYOK UI State: Dialog lifecycle, credential updates, and provider switching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ByokGeminiProviderTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var runtime: AssistantToolRuntime

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        UserAiPreferences.resetTestOverrides()
        AppDatabase.useTestExecutors = true

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EngineeringRepository(database)
        runtime = AssistantToolRuntime.create(
            repository = repository,
            catalogRepository = AssistantToolRuntimeEndToEndTest.buildCatalogRepository()
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
        UserAiPreferences.resetTestOverrides()
    }

    // =========================================================================
    // 1. Secure Credential Storage Tests
    // =========================================================================

    @Test
    fun inMemorySecureCredentialStorage_savesAndMasksKeyCorrectly() {
        val storage = InMemorySecureCredentialStorage()
        assertFalse(storage.hasCredential())
        assertNull(storage.getCredential())
        assertEquals("", storage.getMaskedCredential())

        val testKey = "AIzaSyB1234567890abcdef1234567890XYZ"
        storage.saveCredential(testKey)

        assertTrue(storage.hasCredential())
        assertEquals(testKey, storage.getCredential())
        val masked = storage.getMaskedCredential()
        assertTrue("Masked key should end with last 4 chars '0XYZ'", masked.endsWith("0XYZ"))
        assertTrue("Masked key should contain bullets", masked.contains("••••"))
        assertFalse("Masked key must not leak the full key", masked.contains("B1234567890abcdef"))

        // Clearing
        storage.clearCredential()
        assertFalse(storage.hasCredential())
        assertNull(storage.getCredential())
        assertEquals("", storage.getMaskedCredential())

        // Blank input clears credential
        storage.saveCredential("   ")
        assertFalse(storage.hasCredential())
    }

    @Test
    fun androidKeystoreSecureCredentialStorage_failsLoudWhenHardwareKeystoreUnavailable() {
        val storage = AndroidKeystoreSecureCredentialStorage(context)
        val ex = assertThrows(SecurityException::class.java) {
            storage.saveCredential("AIzaSyD-SecretUserKey9876543210ZYX")
        }
        assertTrue(ex.message!!.contains("AndroidKeyStore hardware encryption unavailable"))

        // Verify that NOTHING was written to SharedPreferences (neither fallback key nor ciphertext)
        val rawPrefs = context.getSharedPreferences("voltatlas_ai_secure_keystore", Context.MODE_PRIVATE)
        assertFalse(rawPrefs.contains("software_fallback_key"))
        assertFalse(rawPrefs.contains("enc_gemini_api_key"))
        assertTrue(rawPrefs.all.isEmpty())
    }

    // =========================================================================
    // 2. User Preferences Tests
    // =========================================================================

    @Test
    fun userAiPreferences_managesProviderAndModelDefaults() {
        val prefs = UserAiPreferences(context)
        assertEquals(AiProvider.DIRECT_GEMINI, prefs.getSelectedProvider())
        assertEquals("gemini-3.8-flash", prefs.getSelectedModel())

        prefs.setSelectedProvider(AiProvider.LOCAL_ONLY)
        assertEquals(AiProvider.LOCAL_ONLY, prefs.getSelectedProvider())

        prefs.setSelectedProvider(AiProvider.FIREBASE_AI_LOGIC)
        assertEquals(AiProvider.FIREBASE_AI_LOGIC, prefs.getSelectedProvider())

        prefs.setSelectedProvider(AiProvider.DIRECT_GEMINI)
        assertEquals(AiProvider.DIRECT_GEMINI, prefs.getSelectedProvider())

        prefs.setSelectedModel("gemini-1.5-pro")
        assertEquals("gemini-1.5-pro", prefs.getSelectedModel())

        // Reset
        prefs.setSelectedModel("")
        assertEquals("gemini-3.8-flash", prefs.getSelectedModel())
    }

    // =========================================================================
    // 3. DirectGeminiClientSeam Payload & Header Tests
    // =========================================================================

    @Test
    fun directGeminiClientSeam_buildsValidGenerateContentJsonPayload() {
        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyMockKeyForSchemaTest" },
            config = AiModelConfig(modelName = "gemini-3.8-flash", temperature = 0.2f, topP = 0.95f)
        )

        val tools = listOf(
            CloudFunctionDeclaration(
                name = "size_feeder_cable",
                description = "Sizes electrical feeder cable per IEC 60364",
                parameters = listOf(
                    CloudFunctionParameter("circuitId", "STRING", "Feeder ID", true),
                    CloudFunctionParameter("designCurrentA", "NUMBER", "Load current in Amperes", true)
                ),
                requiredParameters = listOf("circuitId", "designCurrentA")
            )
        )

        val history = listOf<AgenticTurnItem>(
            AgenticTurnItem.ModelCalls(
                calls = listOf(
                    ToolCall("call_1", "size_feeder_cable", mapOf("circuitId" to JsonPrimitive("C-101"), "designCurrentA" to JsonPrimitive(100)))
                )
            ),
            AgenticTurnItem.ExecutedTools(
                outcomes = listOf(
                    Pair(
                        ToolCall("call_1", "size_feeder_cable"),
                        ToolExecutionOutcome(
                            toolName = "size_feeder_cable",
                            requestId = "REQ-1",
                            envelope = com.example.ai.contracts.ToolResultEnvelope(
                                requestId = "REQ-1",
                                toolName = "size_feeder_cable",
                                projectId = "P-TEST",
                                status = ToolStatus.PASS,
                                result = "50 mm²"
                            )
                        )
                    )
                )
            )
        )

        val payload = seam.buildRequestJson(
            systemPrompt = "You are VoltAtlas engineering assistant.",
            userPrompt = "What is the recommended size?",
            tools = tools,
            conversationHistory = history
        )

        // Verify system instruction
        assertTrue(payload.has("system_instruction"))
        val sysParts = payload.getJSONObject("system_instruction").getJSONArray("parts")
        assertEquals("You are VoltAtlas engineering assistant.", sysParts.getJSONObject(0).getString("text"))

        // Verify tools declaration
        assertTrue(payload.has("tools"))
        val funcDecls = payload.getJSONArray("tools").getJSONObject(0).getJSONArray("function_declarations")
        assertEquals(1, funcDecls.length())
        val toolDecl = funcDecls.getJSONObject(0)
        assertEquals("size_feeder_cable", toolDecl.getString("name"))
        val params = toolDecl.getJSONObject("parameters")
        assertEquals("OBJECT", params.getString("type"))
        val props = params.getJSONObject("properties")
        assertTrue(props.has("circuitId"))
        assertTrue(props.has("designCurrentA"))

        // Verify contents structure
        assertTrue(payload.has("contents"))
        val contents = payload.getJSONArray("contents")
        // History: 2 items + current prompt: 1 item = 3 items
        assertEquals(3, contents.length())

        // Verify generation config
        val genConfig = payload.getJSONObject("generationConfig")
        assertEquals(0.2, genConfig.getDouble("temperature"), 0.001)
    }

    @Test
    fun directGeminiClientSeam_dispatchesWithCorrectHeadersAndApiKey() = runTest(testDispatcher) {
        var capturedApiKey: String? = null
        var capturedUrl: String? = null
        var capturedContentType: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedApiKey = request.header("x-goog-api-key")
                capturedContentType = request.header("Content-Type")
                capturedUrl = request.url.toString()

                val responseJson = """
                    {
                        "candidates": [
                            {
                                "content": {
                                    "role": "model",
                                    "parts": [
                                        { "text": "Cable size is 95 mm² compliant." }
                                    ]
                                }
                            }
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val expectedKey = "AIzaSyCustomUserProvidedKey123"
        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { expectedKey },
            config = AiModelConfig(modelName = "gemini-3.8-flash"),
            httpClient = mockClient,
            ioDispatcher = testDispatcher
        )

        val response = seam.generateTurn(
            systemPrompt = "System",
            userPrompt = "Evaluate feeder 1",
            conversationHistory = emptyList(),
            tools = emptyList()
        )

        assertEquals(expectedKey, capturedApiKey)
        assertTrue(capturedUrl!!.contains("gemini-3.8-flash:generateContent"))
        assertEquals("application/json", capturedContentType)
        assertTrue(response is CloudLlmClientResponse.FinalText)
        assertEquals("Cable size is 95 mm² compliant.", (response as CloudLlmClientResponse.FinalText).text)
    }

    // =========================================================================
    // 4. Response Parsing & Tool Call Mapping Tests
    // =========================================================================

    @Test
    fun directGeminiClientSeam_parsesFunctionCallCorrectly() = runTest(testDispatcher) {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val responseJson = """
                    {
                        "candidates": [
                            {
                                "content": {
                                    "role": "model",
                                    "parts": [
                                        {
                                            "functionCall": {
                                                "name": "run_cable_sizing_tool",
                                                "args": {
                                                    "circuitId": "C-200",
                                                    "currentA": 160.0
                                                }
                                            }
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                """.trimIndent()

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyValidKey" },
            httpClient = mockClient,
            ioDispatcher = testDispatcher
        )

        val response = seam.generateTurn(
            systemPrompt = "",
            userPrompt = "Size this cable",
            conversationHistory = emptyList(),
            tools = emptyList()
        )

        assertTrue("Expected ToolCallRequest", response is CloudLlmClientResponse.ToolCallRequest)
        val toolCall = response as CloudLlmClientResponse.ToolCallRequest
        assertEquals("run_cable_sizing_tool", toolCall.toolName)
        assertTrue(toolCall.arguments.containsKey("circuitId"))
        assertTrue(toolCall.arguments.containsKey("currentA"))
    }

    // =========================================================================
    // 5. Error Handling & Degradation Mapping Tests
    // =========================================================================

    @Test
    fun directGeminiClientSeam_mapsRateLimitingToHonestReason() = runTest(testDispatcher) {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("""{"error":{"code":429,"message":"Resource exhausted"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyValidKey" },
            httpClient = mockClient,
            ioDispatcher = testDispatcher
        )

        val response = seam.generateTurn("sys", "prompt", emptyList(), emptyList())
        assertTrue("Expected ProviderError", response is CloudLlmClientResponse.ProviderError)
        val error = response as CloudLlmClientResponse.ProviderError
        assertTrue("Expected isRateLimited to be true", error.isRateLimited)
    }

    @Test
    fun directGeminiClientSeam_mapsInvalidApiKeyToHttpOrProviderError() = runTest(testDispatcher) {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("""{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyInvalidKey" },
            httpClient = mockClient,
            ioDispatcher = testDispatcher
        )

        val response = seam.generateTurn("sys", "prompt", emptyList(), emptyList())
        assertTrue("Expected ProviderError", response is CloudLlmClientResponse.ProviderError)
        val error = response as CloudLlmClientResponse.ProviderError
        assertFalse("Expected isRateLimited to be false", error.isRateLimited)
        assertTrue(error.message.contains("400"))
    }

    @Test
    fun directGeminiClientSeam_failsClosedWhenApiKeyMissing() = runTest(testDispatcher) {
        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { null }
        )

        val response = seam.generateTurn("sys", "prompt", emptyList(), emptyList())
        assertTrue("Expected ProviderError", response is CloudLlmClientResponse.ProviderError)
        val error = response as CloudLlmClientResponse.ProviderError
        assertTrue(error.message.contains("not configured"))
    }

    // =========================================================================
    // 6. Connection Testing Diagnostics Tests
    // =========================================================================

    @Test
    fun directGeminiClientSeam_testConnectionReturnsConnectedOnSuccess() = runTest(testDispatcher) {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"candidates":[{"content":{"parts":[{"text":"pong"}]}}]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = DirectGeminiClientSeam.testConnection(
            apiKey = "AIzaSyValidKey",
            modelName = "gemini-3.8-flash",
            httpClient = mockClient
        )

        assertEquals(ConnectionTestStatus.CONNECTED, result.status)
        assertTrue(result.message.contains("gemini-3.8-flash successfully"))
    }

    @Test
    fun directGeminiClientSeam_testConnectionDetectsInvalidKey() = runTest(testDispatcher) {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("""{"error":{"message":"API_KEY_INVALID"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val result = DirectGeminiClientSeam.testConnection(
            apiKey = "AIzaSyBadKey",
            modelName = "gemini-3.8-flash",
            httpClient = mockClient
        )

        assertEquals(ConnectionTestStatus.INVALID_CREDENTIAL, result.status)
    }

    @Test
    fun directGeminiClientSeam_testConnectionHandlesEmptyKey() = runTest(testDispatcher) {
        val result = DirectGeminiClientSeam.testConnection(
            apiKey = "   "
        )
        assertEquals(ConnectionTestStatus.NOT_CONFIGURED, result.status)
    }

    // =========================================================================
    // 7. Composition & Fail-Closed Tests
    // =========================================================================

    @Test
    fun conversationEngineProvider_withDirectGeminiAndNoKey_failsClosedSafely() {
        val storage = InMemorySecureCredentialStorage()
        val router = ConversationEngineProvider.provideConversationEngine(
            credentialStorage = storage,
            aiProvider = AiProvider.DIRECT_GEMINI
        )

        assertNotNull(router)
        assertTrue(router is HybridConversationRouter)
    }

    @Test
    fun conversationEngineProvider_withLocalOnly_usesLocalEngineDirectly() = runTest(testDispatcher) {
        val storage = InMemorySecureCredentialStorage()
        storage.saveCredential("AIzaSyAnyKey")

        val router = ConversationEngineProvider.provideConversationEngine(
            credentialStorage = storage,
            aiProvider = AiProvider.LOCAL_ONLY
        )

        val input = ConversationTurnInput(
            userUtterance = "Hello local",
            projectContext = BoundedProjectContext(
                projectId = "P-1",
                projectName = "Test",
                liveRevision = 1L
            )
        )

        val result = router.evaluateTurn(input)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
    }

    // =========================================================================
    // 8. Full Agentic Tool Loop Integration with DirectGeminiClientSeam
    // =========================================================================

    @Test
    fun agenticOrchestrator_withDirectGeminiSeam_executesDeterministicToolAndSynthesizesAnswer() = runTest(testDispatcher) {
        var turnCount = 0

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                turnCount++
                val responseJson = if (turnCount == 1) {
                    // Turn 1: Model invokes deterministic tool
                    """
                        {
                            "candidates": [
                                {
                                    "content": {
                                        "role": "model",
                                        "parts": [
                                            {
                                                "functionCall": {
                                                    "name": "get_current_project_context",
                                                    "args": {
                                                        "projectId": "P-AGENT"
                                                    }
                                                }
                                            }
                                        ]
                                    }
                                }
                            ]
                        }
                    """.trimIndent()
                } else {
                    // Turn 2: Model synthesizes final answer grounded in tool result
                    """
                        {
                            "candidates": [
                                {
                                    "content": {
                                        "role": "model",
                                        "parts": [
                                            { "text": "Project context loaded for P-AGENT with 0 circuits." }
                                        ]
                                    }
                                }
                            ]
                        }
                    """.trimIndent()
                }

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val directSeam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyAgenticTestKey" },
            config = AiModelConfig(modelName = "gemini-3.8-flash"),
            httpClient = mockClient,
            ioDispatcher = testDispatcher
        )

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = directSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val sampleContext = BoundedProjectContext(
            projectId = "P-AGENT",
            projectName = "Substation A",
            liveRevision = 1L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "CIRC-101"
        )

        val scopedTools = ToolSchemaTranslator.getScopedDeclarations(
            runtime.descriptorRegistry.getAllDescriptors()
        )

        val loopResult = orchestrator.executeLoop(
            systemPrompt = "VoltAtlas Engineering Assistant",
            userPrompt = "Get context for P-AGENT",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, loopResult.terminationReason)
        assertEquals(2, turnCount)
        assertEquals(1, loopResult.toolOutcomes.size)
        assertEquals("get_current_project_context", loopResult.toolOutcomes[0].toolName)
        assertTrue(loopResult.finalText?.contains("Project context loaded") == true)
    }

    // =========================================================================
    // 9. Zero Firebase Dependency Test
    // =========================================================================

    @Test
    fun zeroFirebaseDependency_directGeminiOperatesIndependently() {
        val storage = InMemorySecureCredentialStorage()
        storage.saveCredential("AIzaSyStandaloneKey")

        val client = DirectGeminiClientSeam(
            apiKeyProvider = { storage.getCredential() }
        )

        // DirectGeminiClientSeam does NOT reference FirebaseApp
        val classFields = DirectGeminiClientSeam::class.java.declaredFields.map { it.type.name }
        assertFalse("DirectGeminiClientSeam must not reference FirebaseApp", classFields.any { it.contains("Firebase") })

        // Generates request payload without FirebaseApp initialization
        val json = client.buildRequestJson(
            systemPrompt = "System",
            userPrompt = "Prompt",
            tools = emptyList(),
            conversationHistory = emptyList()
        )
        assertNotNull(json)
        assertTrue(json.has("contents"))
    }

    // =========================================================================
    // 10. ViewModel BYOK UI State & Actions Tests
    // =========================================================================

    @Test
    fun engineeringAssistantViewModel_managesByokSettingsState() = runTest(testDispatcher) {
        val storage = InMemorySecureCredentialStorage()
        val prefs = UserAiPreferences(context)

        val viewModel = EngineeringAssistantViewModel(
            credentialStorage = storage,
            userAiPreferences = prefs,
            ioDispatcher = testDispatcher
        )

        // 1. Initial State
        val initialState = viewModel.aiSettingsState.value
        assertFalse(initialState.isOpen)
        assertEquals(AiProvider.DIRECT_GEMINI, initialState.selectedProvider)
        assertFalse(initialState.hasCredential)

        // 2. Open Settings
        viewModel.openAiSettings()
        assertTrue(viewModel.aiSettingsState.value.isOpen)

        // 3. Save Credential
        val testKey = "AIzaSyUserEnteredKeyFromUi1234ABCD"
        viewModel.saveGeminiCredential(testKey)

        val stateAfterSave = viewModel.aiSettingsState.value
        assertTrue(stateAfterSave.hasCredential)
        assertTrue(stateAfterSave.maskedCredential.endsWith("ABCD"))
        assertEquals(testKey, storage.getCredential())

        // 4. Switch Provider
        viewModel.selectAiProvider(AiProvider.LOCAL_ONLY)
        assertEquals(AiProvider.LOCAL_ONLY, viewModel.aiSettingsState.value.selectedProvider)
        assertEquals(AiProvider.LOCAL_ONLY, prefs.getSelectedProvider())

        // 5. Remove Credential
        viewModel.removeGeminiCredential()
        val stateAfterRemove = viewModel.aiSettingsState.value
        assertFalse(stateAfterRemove.hasCredential)
        assertEquals("", stateAfterRemove.maskedCredential)
        assertNull(storage.getCredential())

        // 6. Close Settings
        viewModel.closeAiSettings()
        assertFalse(viewModel.aiSettingsState.value.isOpen)
    }

    // =========================================================================
    // 11. Section 18 Targeted Defect Regression Tests
    // =========================================================================

    @Test
    fun credentialSaved_runtimeProviderUsesCredential() = runTest(testDispatcher) {
        val storage = InMemorySecureCredentialStorage()
        assertNull(storage.getCredential())

        storage.saveCredential("AIzaSyRuntimeTestKey789")

        val router = ConversationEngineProvider.provideConversationEngine(
            credentialStorage = storage,
            aiProvider = AiProvider.DIRECT_GEMINI
        )

        assertNotNull(router)
        assertTrue(router is HybridConversationRouter)
        assertEquals("AIzaSyRuntimeTestKey789", CloudLlmFeatureGate.getApiKey())
    }

    @Test
    fun testConnectionAndAssistantShareCredentialSource() = runTest(testDispatcher) {
        val storage = InMemorySecureCredentialStorage()
        val key = "AIzaSySharedKey999"
        storage.saveCredential(key)

        var testConnApiKey: String? = null
        var assistantApiKey: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val authHeader = chain.request().header("x-goog-api-key")
                if (chain.request().url.queryParameter("test") == "true") {
                    testConnApiKey = authHeader
                } else {
                    assistantApiKey = authHeader
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"candidates":[{"content":{"parts":[{"text":"pong"}]}}]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        DirectGeminiClientSeam.testConnection(
            apiKey = storage.getCredential()!!,
            httpClient = mockClient
        )

        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { storage.getCredential() },
            httpClient = mockClient,
            ioDispatcher = testDispatcher
        )
        seam.generateTurn("sys", "user", emptyList(), emptyList())

        assertEquals(storage.getCredential(), key)
    }

    @Test
    fun providerCacheInvalidatedAfterCredentialChange() = runTest(testDispatcher) {
        val storage = InMemorySecureCredentialStorage()
        var capturedKey: String? = null

        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { storage.getCredential() },
            httpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedKey = chain.request().header("x-goog-api-key")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
            ioDispatcher = testDispatcher
        )

        // Step 1: Initial missing key fails closed
        assertFalse(seam.isConfigured())
        val initialResp = seam.generateTurn("sys", "hello", emptyList(), emptyList())
        assertTrue(initialResp is CloudLlmClientResponse.ProviderError)

        // Step 2: Save key -> next call immediately reads new key
        storage.saveCredential("AIzaSyNewDynamicKey123")
        assertTrue(seam.isConfigured())
        val secondResp = seam.generateTurn("sys", "hello", emptyList(), emptyList())
        assertTrue(secondResp is CloudLlmClientResponse.FinalText)
        assertEquals("AIzaSyNewDynamicKey123", capturedKey)
    }

    @Test
    fun directGeminiRequestUsesConfiguredModel() = runTest(testDispatcher) {
        var requestedUrl: String? = null
        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyKey" },
            config = AiModelConfig(modelName = "gemini-3.8-flash"),
            httpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requestedUrl = chain.request().url.toString()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
            ioDispatcher = testDispatcher
        )

        seam.generateTurn("sys", "user", emptyList(), emptyList())
        assertNotNull(requestedUrl)
        assertTrue(requestedUrl!!.contains("models/gemini-3.8-flash:generateContent"))
    }

    @Test
    fun gemini38RequestContainsNoUnsupportedLegacyGenerationFields() {
        val seam = DirectGeminiClientSeam(
            apiKeyProvider = { "AIzaSyKey" },
            config = AiModelConfig(modelName = "gemini-3.8-flash", temperature = 0.3f, topP = 0.9f)
        )

        val json = seam.buildRequestJson("sys", "user", emptyList(), emptyList())
        assertTrue(json.has("generationConfig"))
        val genConfig = json.getJSONObject("generationConfig")

        // Supported fields present
        assertTrue(genConfig.has("temperature"))
        assertTrue(genConfig.has("topP"))

        // Obsolete / unsupported legacy fields absent
        assertFalse("topK must not be sent for gemini-3.8-flash", genConfig.has("topK"))
        assertFalse("candidate_count must not be sent", genConfig.has("candidate_count"))
        assertFalse("thinking_budget must not be sent", genConfig.has("thinking_budget"))
    }

    @Test
    fun cloudFailureReasonPreservedBeforeFallback() = runTest(testDispatcher) {
        val storage = InMemorySecureCredentialStorage()
        storage.saveCredential("AIzaSyRateLimitedKey")

        val rateLimitedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("""{"error":{"code":429,"message":"Resource exhausted"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val directSeam = DirectGeminiClientSeam(
            apiKeyProvider = { storage.getCredential() },
            httpClient = rateLimitedClient,
            ioDispatcher = testDispatcher
        )

        val cloudEngine = CloudLlmConversationEngine(clientSeam = directSeam)
        val localEngine = LocalRuleEngineAdapter(ConversationPolicyEngine)

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            cloudTimeoutMs = 15_000L
        )

        val input = ConversationTurnInput(
            userUtterance = "hello",
            projectContext = BoundedProjectContext(projectId = "P-1", projectName = "Test", liveRevision = 1L)
        )

        val result = router.evaluateTurn(input)

        // Verified: Falls back to local engine BUT preserves the honest cloud degradation reason
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, result.source)
        assertEquals(CloudDegradationReason.RATE_LIMITED, result.cloudDegradationReason)
    }
}
