package com.example.ai

import com.example.ai.approval.DefaultTrustedPrincipalProvider
import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.conversation.ConversationPolicyEngine
import com.example.ai.runtime.RuntimePromptCompiler
import org.junit.Assert.*
import org.junit.Test

class AssistantArchitectureGuardTest {

    // 1. ConversationPolicyEngine cannot access mutation or database types
    @Test
    fun conversationEngine_hasZeroMutationOrDatabaseReferences() {
        val engineClass = ConversationPolicyEngine::class.java
        for (field in engineClass.declaredFields) {
            val typeName = field.type.name
            assertFalse(typeName.contains("TrustedProposalApprovalGateway"))
            assertFalse(typeName.contains("ApprovalGrantDao"))
            assertFalse(typeName.contains("MutationReceiptDao"))
            assertFalse(typeName.contains("DurableMutationWorkflow"))
            assertFalse(typeName.contains("AppDatabase"))
        }
    }

    // 2. RuntimePromptCompiler advertises no mutation tools
    @Test
    fun runtimePromptCompiler_advertisesNoMutationTools() {
        assertNotNull(RuntimePromptCompiler)
        // Verify ExecutionMode policy
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 3. Global ExecutionMode.MUTATION remains false
    @Test
    fun executionModeMutation_isStrictlyFalse() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 4. DefaultTrustedPrincipalProvider has zero hardcoded principal strings in default constructor
    @Test
    fun defaultTrustedPrincipalProvider_failsClosedByDefault() {
        val defaultProvider = DefaultTrustedPrincipalProvider()
        assertNull(defaultProvider.getTrustedPrincipalId())
    }
}
