package com.example.ai.service

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * P1-3 (M-01..03 / DC-1..3) architecture guard.
 *
 * The unreachable echo surfaces were deleted in Phase 6 Batch 3:
 *  - `FirebaseAiServiceImpl` — a 35-line stub answering with canned "Phase 1 ready, sir!"
 *    text, zero references anywhere in main/test/androidTest/build files;
 *  - the `EngineeringAiService` interface it implemented — only ever referenced by that stub.
 *
 * The file [EngineeringAiService.kt] remains because it hosts the LIVE DTOs
 * (`MessageRole`, `AssistantMessage`, `AssistantResponse`) imported by the assistant UI and
 * its tests. These guards fail if either echo surface is reintroduced beside the real
 * grounded pipeline.
 */
class EchoStubArchitectureGuardTest {

    private fun classPresent(name: String): Boolean = try {
        Class.forName(name)
        true
    } catch (e: ClassNotFoundException) {
        false
    }

    @Test
    fun firebaseAiServiceImplMustNotReturn() {
        assertFalse(
            "P1-3: FirebaseAiServiceImpl (unreachable echo stub with fabricated 'Phase 1 " +
                "ready' responses) was deleted — do not reintroduce it beside the real AI " +
                "pipeline.",
            classPresent("com.example.ai.service.FirebaseAiServiceImpl")
        )
    }

    @Test
    fun engineeringAiServiceInterfaceMustNotReturn() {
        assertFalse(
            "P1-3: the EngineeringAiService interface was only ever referenced by the echo " +
                "stub and was deleted with it — do not reintroduce it. The live contract is " +
                "AssistantResponse/MessageRole/AssistantMessage.",
            classPresent("com.example.ai.service.EngineeringAiService")
        )
    }

    @Test
    fun liveAssistantContractsRemain() {
        // Deletion scope stopped at the dead interface + echo impl on purpose: these DTOs
        // are imported by AssistantConversationOrder, EngineeringAssistantSheet,
        // EngineeringAssistantViewModel and their tests. A ClassNotFoundException here
        // fails the build with the class name in the message.
        Class.forName("com.example.ai.service.MessageRole")
        Class.forName("com.example.ai.service.AssistantMessage")
        Class.forName("com.example.ai.service.AssistantResponse")
    }
}
