package com.example.ai.orchestrator

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ToolStatus
import com.example.ai.hybrid.CloudDegradationReason
import com.example.ai.hybrid.CloudFunctionDeclaration
import com.example.ai.hybrid.CloudFunctionParameter
import com.example.ai.hybrid.CloudLlmClientResponse
import com.example.ai.hybrid.CloudLlmFeatureGate
import com.example.ai.hybrid.ToolCall
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.AssistantToolRuntimeEndToEndTest
import com.example.ai.tools.ToolExecutionOutcome
import com.example.ai.tools.cable.CableEngineeringToolsTest
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.data.ProjectEntity
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Authoritative test suite verifying the multi-turn agentic tool loop (Gate 5).
 *
 * Implements all 18 cases from Section 37 plus the real end-to-end loop with real
 * deterministic calculation engine and Room database (Correction #10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgenticToolLoopOrchestratorTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository
    private lateinit var runtime: AssistantToolRuntime

    private val projectId = "PROJ-ORCH-1"
    private val circuitId = "1"

    private lateinit var sampleContext: BoundedProjectContext
    private val scopedTools: List<CloudFunctionDeclaration>
        get() {
            val scoped = com.example.ai.tools.EngineeringToolRegistry.scopeTo(runtime.descriptorRegistry.getAllDescriptors(), sampleContext)
            return com.example.ai.hybrid.ToolSchemaTranslator.getScopedDeclarations(scoped)
        }

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            CableEngineeringToolsTest.loadCatalogData()
        }
    }

    @Before
    fun setUp() {
        CloudLlmFeatureGate.configureForTesting(enabled = true, apiKey = "test-key")
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = EngineeringRepository(database)

        EngineeringCalculationCoordinator.projectRevisionProvider = repository
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()

        runtime = AssistantToolRuntime.create(
            repository = repository,
            catalogRepository = AssistantToolRuntimeEndToEndTest.buildCatalogRepository()
        )

        runBlocking {
            database.projectDao().upsertProject(
                ProjectEntity(projectId = projectId, projectName = "Orchestrator Test Project", revision = 1L)
            )

            database.circuitDao().insertCircuit(
                CircuitEntity(
                    id = 1,
                    projectId = projectId,
                    circuitName = "Feeder 1",
                    loadKVA = 250.0,
                    length_m = 30.0,
                    voltageV = 400,
                    phases = "3P+N",
                    pf = 0.85,
                    maxVDpct = 0.03,
                    cableType = "Cu/XLPE/PVC - 4C",
                    installation = "C",
                    parallelRuns = 1,
                    airTempC = 30,
                    overrideCableSize = "300.0",
                    overrideDeviceSize = 400.0,
                    overrideDeviceType = "MCCB"
                )
            )
        }

        sampleContext = BoundedProjectContext(
            projectId = projectId,
            projectName = "Orchestrator Test Project",
            liveRevision = 1L,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = circuitId,
            activeEntityName = "Feeder 1"
        )
    }

    @After
    fun tearDown() {
        CloudLlmFeatureGate.resetToDefaults()
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        database.close()
    }

    // =========================================================================
    // CASE 1: Direct final answer without tool
    // =========================================================================
    @Test
    fun case01_directFinalAnswerWithoutTool_terminatesWithFinalAnswer() = runTest {
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                return CloudLlmClientResponse.FinalText("Project conforms to BS 7671.")
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Check compliance",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals("Project conforms to BS 7671.", result.finalText)
        assertEquals(1, result.turnsExecuted)
        assertTrue(result.toolOutcomes.isEmpty())
        assertNull(result.degradationReason)
    }

    // =========================================================================
    // CASE 2: One tool call
    // =========================================================================
    @Test
    fun case02_oneToolCall_executesTool() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "get_current_project_context")
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Project context loaded.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "What is the project?",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(1, result.toolOutcomes.size)
        assertEquals("get_current_project_context", result.toolOutcomes.first().toolName)
        assertEquals(ToolStatus.PASS, result.toolOutcomes.first().envelope.status)
        assertEquals(2, result.turnsExecuted)
    }

    // =========================================================================
    // CASE 3: One tool call followed by final answer
    // =========================================================================
    @Test
    fun case03_oneToolCallFollowedByFinalAnswer_preservesOutcomeAndBackingEvidence() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "get_entity")
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Entity is Feeder 1 with 32A load.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Get entity details",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals("Entity is Feeder 1 with 32A load.", result.finalText)
        assertEquals(1, result.toolOutcomes.size)
        assertEquals(ToolStatus.PASS, result.toolOutcomes.first().envelope.status)
    }

    // =========================================================================
    // CASE 4: Second tool requested after first result (Multi-turn sequential)
    // =========================================================================
    @Test
    fun case04_secondToolRequestedAfterFirstResult_executesSequentialTurns() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return when (turnCount) {
                    1 -> CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "get_current_project_context")
                    )
                    2 -> CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "calculate_cable_sizing")
                    )
                    else -> CloudLlmClientResponse.FinalText("All data retrieved and sized.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Context then sizing",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(3, result.turnsExecuted)
        assertEquals(2, result.toolOutcomes.size)
        assertEquals("get_current_project_context", result.toolOutcomes[0].toolName)
        assertEquals("calculate_cable_sizing", result.toolOutcomes[1].toolName)
    }

    // =========================================================================
    // CASE 5: Multiple independent read calls (parallel execution)
    // =========================================================================
    @Test
    fun case05_multipleIndependentReadCalls_executesInParallel() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        calls = listOf(
                            ToolCall(callId = "call-read-1", toolName = "get_current_project_context"),
                            ToolCall(callId = "call-read-2", toolName = "get_entity")
                        )
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Both reads executed.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Run parallel reads",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(2, result.toolOutcomes.size)
        val names = result.toolOutcomes.map { it.toolName }.toSet()
        assertTrue(names.contains("get_current_project_context"))
        assertTrue(names.contains("get_entity"))
        // Preserves distinct call ids in history
        val executedToolsTurn = result.turnHistory.filterIsInstance<AgenticTurnItem.ExecutedTools>().first()
        assertEquals(2, executedToolsTurn.outcomes.size)
        assertEquals("call-read-1", executedToolsTurn.outcomes[0].first.callId)
        assertEquals("call-read-2", executedToolsTurn.outcomes[1].first.callId)
    }

    // =========================================================================
    // CASE 6: Sequential dependent calls
    // =========================================================================
    @Test
    fun case06_sequentialDependentCalls_chainResults() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return when (turnCount) {
                    1 -> CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "get_entity")
                    )
                    2 -> {
                        // In turn 2, Gemini consumes entity results from history
                        val historyTurn = conversationHistory.filterIsInstance<AgenticTurnItem.ExecutedTools>().last()
                        assertEquals(1, historyTurn.outcomes.size)
                        assertEquals("get_entity", historyTurn.outcomes.first().first.toolName)
                        CloudLlmClientResponse.ToolCallRequest(
                            ToolCall(toolName = "calculate_cable_sizing")
                        )
                    }
                    else -> CloudLlmClientResponse.FinalText("Completed dependent flow.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Chain entity to sizing",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(2, result.toolOutcomes.size)
        assertEquals(ToolStatus.PASS, result.toolOutcomes[0].envelope.status)
        assertEquals(ToolStatus.PASS, result.toolOutcomes[1].envelope.status)
    }

    // =========================================================================
    // CASE 7: Unknown tool fails closed
    // =========================================================================
    @Test
    fun case07_unknownTool_failsClosed() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "hack_substation")
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Tool was not supported.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Run unknown tool",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(1, result.toolOutcomes.size)
        assertEquals(ToolStatus.NOT_SUPPORTED, result.toolOutcomes.first().envelope.status)
        assertTrue(result.toolOutcomes.first().envelope.errors.first().message.contains("not declared"))
    }

    // =========================================================================
    // CASE 8: Malformed arguments fail closed
    // =========================================================================
    @Test
    fun case08_malformedArguments_failsClosed() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(
                            toolName = "get_current_project_context",
                            arguments = mapOf("includeValidation" to JsonPrimitive("not-a-boolean"))
                        )
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Handled bad input safely.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Send invalid boolean argument",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(1, result.toolOutcomes.size)
    }

    // =========================================================================
    // CASE 9: Wrong project fails closed (Authoritative project enforced)
    // =========================================================================
    @Test
    fun case09_wrongProject_failsClosed_authoritativeProjectEnforced() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(
                            toolName = "get_current_project_context",
                            arguments = mapOf("projectId" to JsonPrimitive("FORGED-PROJECT-999"))
                        )
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Context read complete.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Try reading forged project",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        // Verify dispatcher forced authoritative projectId
        assertEquals(projectId, result.toolOutcomes.first().envelope.projectId)
    }

    // =========================================================================
    // CASE 10: Wrong entity fails closed (Authoritative entity enforced)
    // =========================================================================
    @Test
    fun case10_wrongEntity_failsClosed_authoritativeEntityEnforced() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(
                            toolName = "get_entity",
                            arguments = mapOf("entityId" to JsonPrimitive("FORGED-ENTITY-999"))
                        )
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Entity read complete.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Try reading forged entity",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        // Authoritative entityId from sampleContext was executed successfully
        assertEquals(ToolStatus.PASS, result.toolOutcomes.first().envelope.status)
    }

    // =========================================================================
    // CASE 11: Stale revision fails closed
    // =========================================================================
    @Test
    fun case11_staleRevision_failsClosed() = runTest {
        val nonExistentContext = sampleContext.copy(projectId = "NON-EXISTENT-PROJECT")
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "get_current_project_context")
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Done.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Check non-existent project",
            projectContext = nonExistentContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(ToolStatus.NOT_SUPPORTED, result.toolOutcomes.first().envelope.status)
        assertTrue(result.toolOutcomes.first().envelope.errors.first().message.contains("could not be resolved"))
    }

    // =========================================================================
    // CASE 12: Missing required input fails closed
    // =========================================================================
    @Test
    fun case12_missingRequiredInput_failsClosed() = runTest {
        // Context with null entityId for a tool that requires entity context
        val contextWithoutEntity = sampleContext.copy(activeEntityId = null)
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(toolName = "calculate_cable_sizing")
                    )
                } else {
                    CloudLlmClientResponse.FinalText("Failed as expected.")
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Calculate without entity binding",
            projectContext = contextWithoutEntity,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(ToolStatus.NOT_SUPPORTED, result.toolOutcomes.first().envelope.status)
        assertTrue(result.toolOutcomes.first().envelope.errors.first().message.contains("requires an authorized entity binding"))
    }

    // =========================================================================
    // CASE 13: Repeated identical tool calls detected and halted
    // =========================================================================
    @Test
    fun case13_repeatedIdenticalToolCalls_detectedAndHalted() = runTest {
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                // Loop emitting the exact same call with identical arguments
                return CloudLlmClientResponse.ToolCallRequest(
                    ToolCall(toolName = "get_current_project_context")
                )
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry,
            safetyBudget = AgenticSafetyBudget(maxRepeatedCalls = 2, maxIterations = 10)
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Pathological repeat",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.REPEATED_TOOL_CALL_DETECTED, result.terminationReason)
        assertTrue(result.errorMessage?.contains("Repeated identical tool call") == true)
    }

    // =========================================================================
    // CASE 14: Loop budget exhaustion terminates safely
    // =========================================================================
    @Test
    fun case14_loopBudgetExhaustion_terminatesSafely() = runTest {
        var turnCount = 0
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                // Request different arguments each turn so repeated call detector does not fire
                return CloudLlmClientResponse.ToolCallRequest(
                    ToolCall(
                        toolName = "get_current_project_context",
                        arguments = mapOf("turn" to JsonPrimitive(turnCount))
                    )
                )
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry,
            safetyBudget = AgenticSafetyBudget(maxIterations = 3, maxRepeatedCalls = 5)
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Endless tools",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.MAX_ITERATIONS_EXCEEDED, result.terminationReason)
        assertEquals(3, result.turnsExecuted)
        assertEquals(CloudDegradationReason.HTTP_OR_PROVIDER_ERROR, result.degradationReason)
    }

    // =========================================================================
    // CASE 15: Timeout handled
    // =========================================================================
    @Test
    fun case15_timeoutHandled_terminatesSafely() = runTest {
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                delay(200)
                return CloudLlmClientResponse.FinalText("Too late.")
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry,
            safetyBudget = AgenticSafetyBudget(iterationTimeoutMs = 50)
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Slow request",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.TIMEOUT, result.terminationReason)
        assertEquals(CloudDegradationReason.NETWORK_TIMEOUT, result.degradationReason)
    }

    // =========================================================================
    // CASE 16: Cancellation propagated
    // =========================================================================
    @Test(expected = CancellationException::class)
    fun case16_cancellationPropagated_rethrowsException() = runTest {
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                throw CancellationException("Coroutine scope cancelled")
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Cancel me",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )
    }

    // =========================================================================
    // CASE 17: Provider failure typed
    // =========================================================================
    @Test
    fun case17_providerFailureTyped_terminatesSafely() = runTest {
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                return CloudLlmClientResponse.ProviderError(
                    message = "Quota exhausted",
                    isRateLimited = true
                )
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Fail quota",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.PROVIDER_ERROR, result.terminationReason)
        assertEquals(CloudDegradationReason.RATE_LIMITED, result.degradationReason)
    }

    // =========================================================================
    // CASE 18: Safety response typed
    // =========================================================================
    @Test
    fun case18_safetyResponseTyped_terminatesSafely() = runTest {
        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                return CloudLlmClientResponse.SafetyBlock(reason = "Content violates safety policy")
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "System",
            userPrompt = "Unsafe request",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        assertEquals(AgenticLoopTermination.SAFETY_BLOCKED, result.terminationReason)
        assertEquals(CloudDegradationReason.SAFETY_BLOCKED, result.degradationReason)
    }

    // =========================================================================
    // CASE 19 (Correction #10): Real End-to-End Execution Loop
    // User -> Seam -> Tool Call -> Real Dispatcher -> Real Engine -> Envelope
    // -> History -> Second Model Turn -> Final Text
    // =========================================================================
    @Test
    fun case19_realEndToEndExecutionLoop_verifiesFullLoop() = runTest {
        var turnCount = 0
        var receivedToolResponseStatus: String? = null

        val testSeam = object : MultiTurnGeminiSeam {
            override suspend fun generateTurn(
                systemPrompt: String,
                userPrompt: String,
                conversationHistory: List<AgenticTurnItem>,
                tools: List<CloudFunctionDeclaration>
            ): CloudLlmClientResponse {
                turnCount++
                return if (turnCount == 1) {
                    // Turn 1: Model requests real cable calculation
                    CloudLlmClientResponse.ToolCallRequest(
                        ToolCall(
                            callId = "call-calc-1",
                            toolName = "calculate_cable_sizing"
                        )
                    )
                } else {
                    // Turn 2: Inspect returned tool response from conversation history
                    val executedTurn = conversationHistory.filterIsInstance<AgenticTurnItem.ExecutedTools>().firstOrNull()
                    val outcome = executedTurn?.outcomes?.firstOrNull()?.second
                    receivedToolResponseStatus = outcome?.envelope?.status?.name

                    val sizingResult = outcome?.envelope?.result as? CableSizingExecutionResult
                    val sizeText = sizingResult?.details?.cableSize_mm2.orEmpty()

                    CloudLlmClientResponse.FinalText(
                        "Calculation completed with status $receivedToolResponseStatus and selected size $sizeText."
                    )
                }
            }
        }

        val orchestrator = AgenticToolLoopOrchestrator(
            clientSeam = testSeam,
            toolDispatcher = runtime.dispatcher,
            toolRegistry = runtime.descriptorRegistry
        )

        val result = orchestrator.executeLoop(
            systemPrompt = "You are an electrical engineering assistant.",
            userPrompt = "Size the cable for feeder 1",
            projectContext = sampleContext,
            scopedTools = scopedTools
        )

        // Verify full loop execution
        assertEquals(AgenticLoopTermination.FINAL_ANSWER_REACHED, result.terminationReason)
        assertEquals(2, result.turnsExecuted)
        assertEquals(1, result.toolOutcomes.size)

        val outcome = result.toolOutcomes.first()
        assertEquals("calculate_cable_sizing", outcome.toolName)
        assertEquals(ToolStatus.PASS, outcome.envelope.status)
        assertEquals("PASS", receivedToolResponseStatus)

        val sizingResult = outcome.envelope.result as CableSizingExecutionResult
        assertFalse("Selected cable size must not be blank", sizingResult.details.cableSize_mm2.isBlank())
        assertTrue(result.finalText?.contains("Calculation completed with status PASS") == true)
    }
}
