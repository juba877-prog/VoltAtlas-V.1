package com.example.ai.hybrid

import com.example.ai.conversation.ConversationAction
import com.example.ai.conversation.ConversationActionType
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.ConversationTurnDecision
import com.example.ai.conversation.ConversationTurnInput
import com.example.ai.runtime.BoundedProjectContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OnDeviceNanoConversationEngineTest {

    private val sampleContext = BoundedProjectContext(
        projectId = "TEST-PROJ-01",
        projectName = "VoltAtlas Test Plant",
        liveRevision = 1L
    )

    @Test
    fun testMotorStarterIntent_evaluatesDeterministically() = runBlocking {
        val engine = OnDeviceNanoConversationEngine(clientSeam = DefaultNanoClientSeam(isHardwareAvailable = true))
        val input = ConversationTurnInput(
            userUtterance = "Size a motor starter for 45 kW 400V 3-phase motor",
            projectContext = sampleContext
        )

        val result = engine.evaluateTurn(input) { "DEC-TEST-001" }

        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        assertNull(result.cloudDegradationReason)
        // Intent extraction only: no tool ran, so no tool backing may be claimed.
        assertFalse(result.hasBackingToolResult)
        val text = result.decision.explanationSummary.orEmpty()
        assertTrue("Should route to the authoritative engine: $text", text.contains("authoritative VoltAtlas engine"))
        // The on-device layer must not compute starter results any more.
        assertFalse("Must not emit an FLC value: $text", text.contains("FLC"))
        assertFalse("Must not recommend a starter method: $text", text.contains("Star-Delta"))
    }

    @Test
    fun testPowerFactorCorrectionIntent_evaluatesDeterministically() = runBlocking {
        val engine = OnDeviceNanoConversationEngine(clientSeam = DefaultNanoClientSeam(isHardwareAvailable = true))
        val input = ConversationTurnInput(
            userUtterance = "Calculate PFC capacitor for 150 kW load from 0.78 to 0.96",
            projectContext = sampleContext
        )

        val result = engine.evaluateTurn(input) { "DEC-TEST-002" }

        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        assertFalse(result.hasBackingToolResult)
        val text = result.decision.explanationSummary.orEmpty()
        assertTrue("Should route to the authoritative engine: $text", text.contains("authoritative VoltAtlas engine"))
        assertFalse("Must not compute a kvar value: $text", text.contains("kvar"))
        assertFalse("Must not cite an IEC result: $text", text.contains("IEC 60831"))
    }

    @Test
    fun testTransformerFaultIntent_evaluatesDeterministically() = runBlocking {
        val engine = OnDeviceNanoConversationEngine(clientSeam = DefaultNanoClientSeam(isHardwareAvailable = true))
        val input = ConversationTurnInput(
            userUtterance = "What is the short circuit fault level for a 1600 kVA transformer with 6% Z?",
            projectContext = sampleContext
        )

        val result = engine.evaluateTurn(input) { "DEC-TEST-003" }

        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        assertFalse(result.hasBackingToolResult)
        val text = result.decision.explanationSummary.orEmpty()
        assertTrue("Should route to the authoritative engine: $text", text.contains("authoritative VoltAtlas engine"))
        assertFalse("Must not compute a fault level: $text", text.contains("kA"))
        assertFalse("Must not cite an IEC result: $text", text.contains("IEC 60909"))
    }

    @Test
    fun testCableSizingIntent_evaluatesDeterministically() = runBlocking {
        val engine = OnDeviceNanoConversationEngine(clientSeam = DefaultNanoClientSeam(isHardwareAvailable = true))
        val input = ConversationTurnInput(
            userUtterance = "Size a 3-phase cable for 30 kW over 50 meters at 400V",
            projectContext = sampleContext
        )

        val result = engine.evaluateTurn(input) { "DEC-TEST-004" }

        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        assertFalse(result.hasBackingToolResult)
        val text = result.decision.explanationSummary.orEmpty()
        assertTrue("Should route to the authoritative engine: $text", text.contains("authoritative VoltAtlas engine"))
        assertFalse("Must not emit a conductor size: $text", text.contains("mm²"))
        assertFalse("Must not cite an IEC result: $text", text.contains("IEC 60364"))
    }

    @Test
    fun testNanoHardwareUnavailable_fallsBackGracefully() = runBlocking {
        val unavailableClient = DefaultNanoClientSeam(isHardwareAvailable = false)
        val engine = OnDeviceNanoConversationEngine(clientSeam = unavailableClient)
        val input = ConversationTurnInput(
            userUtterance = "Size a cable for 30 kW",
            projectContext = sampleContext
        )

        val result = engine.evaluateTurn(input) { "DEC-TEST-005" }

        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        assertNotNull(result.cloudDegradationReason)
        assertTrue(result.decision.explanationSummary.orEmpty().contains("not available"))
    }

    @Test
    fun defaultClientSeamFailsClosed_hardwareIsNeverClaimedByDefault() = runBlocking {
        // P1-2 (L-02): DefaultNanoClientSeam.isHardwareAvailable now defaults to FALSE —
        // this build integrates no Gemini Nano / ML Kit SDK, so DEFAULT construction must
        // degrade with "not available" instead of claiming hardware that does not exist.
        // The parser tests above pass `isHardwareAvailable = true` explicitly because their
        // subject is parsing, not availability.
        val engine = OnDeviceNanoConversationEngine()
        val input = ConversationTurnInput(
            userUtterance = "Size a cable for 30 kW",
            projectContext = sampleContext
        )

        val result = engine.evaluateTurn(input) { "DEC-DEFAULT-SEAM" }

        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        assertNotNull(result.cloudDegradationReason)
        assertTrue(result.decision.explanationSummary.orEmpty().contains("not available"))
    }

    @Test
    fun testHybridRouter_offlineRoutesToNanoWhenAvailable() = runBlocking {
        val offlineProbe = object : NetworkConnectivityProbe {
            override fun isLikelyOnline(): Boolean = false
        }
        val cloudEngine = object : ConversationEngine {
            override suspend fun evaluateTurn(input: ConversationTurnInput, decisionIdProvider: () -> String): HybridEngineResult {
                throw IllegalStateException("Cloud should not be called when offline")
            }
        }
        val localEngine = object : ConversationEngine {
            override suspend fun evaluateTurn(input: ConversationTurnInput, decisionIdProvider: () -> String): HybridEngineResult {
                return HybridEngineResult(
                    decision = ConversationTurnDecision(
                        decisionId = "LOCAL-01",
                        action = ConversationAction(
                            actionType = ConversationActionType.EXPLAIN_RESULT,
                            reasonCode = "LOCAL_RULE_FALLBACK"
                        ),
                        updatedState = ConversationTaskState(
                            taskId = "TASK-01",
                            projectId = input.projectContext.projectId,
                            baseProjectRevision = input.projectContext.liveRevision
                        ),
                        explanationSummary = "Local rule engine output"
                    ),
                    source = ConversationEngineSource.LOCAL_RULE_ENGINE
                )
            }
        }
        val nanoEngine = OnDeviceNanoConversationEngine(clientSeam = DefaultNanoClientSeam(isHardwareAvailable = true))

        val router = HybridConversationRouter(
            cloudEngine = cloudEngine,
            localEngine = localEngine,
            connectivityProbe = offlineProbe,
            nanoEngine = nanoEngine
        )

        val input = ConversationTurnInput(
            userUtterance = "Size a motor starter for 22 kW motor",
            projectContext = sampleContext
        )

        val result = router.evaluateTurn(input)

        // Offline probe with functional Nano routes to Nano, which reports intent routing only
        // (it must not compute a motor result itself).
        assertEquals(ConversationEngineSource.ON_DEVICE_GEMINI_NANO, result.source)
        val summary = result.decision.explanationSummary.orEmpty()
        assertTrue("Should route to the authoritative engine: $summary", summary.contains("authoritative VoltAtlas engine"))
        assertFalse("Must not emit a motor rating result: $summary", summary.contains("kW"))
    }
}
