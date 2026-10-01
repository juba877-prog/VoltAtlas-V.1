package com.example.ai.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 Batch 4 / P2-11 (findings J-09 + AU-W-03) — ExecutionModePolicy copy contract.
 *
 * The policy comment described MUTATION as non-executable "in Phase 3 until Phase 7", which
 * reads as a temporary state a later phase would lift. That is wrong and unsafe: it implies
 * direct mutation could become legal, bypassing the trusted proposal/approval/revision pipeline.
 *
 * This test pins BOTH halves:
 *  1. the documented behaviour (MUTATION is not executable via this policy), and
 *  2. the copy itself — the source must not describe the guard as temporary.
 *
 * No behaviour was changed by P2-11; this locks both the runtime truth and the wording so the
 * guard cannot be silently reframed later.
 */
class ExecutionModePolicyContractTest {

    @Test
    fun mutationIsNotExecutableThroughThisPolicy() {
        assertFalse(
            "MUTATION must remain non-executable via ExecutionModePolicy",
            ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION)
        )
        assertTrue(
            "MUTATION is precisely the mode that needs approval + revision guarding",
            ExecutionModePolicy.requiresApprovalAndRevisionGuard(ExecutionMode.MUTATION)
        )
    }

    @Test
    fun nonMutatingModesRemainExecutable() {
        assertTrue(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.READ_ONLY))
        assertTrue(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.LIVE_CALCULATION))
        assertTrue(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.WHAT_IF))
        assertTrue(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.VALIDATE_ONLY))
        assertTrue(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.PROPOSAL))
    }

    @Test
    fun onlyMutationRequiresTheApprovalGate() {
        ExecutionMode.entries.forEach { mode ->
            val expected = mode == ExecutionMode.MUTATION
            assertEquals(
                "unexpected approval requirement for $mode",
                expected,
                ExecutionModePolicy.requiresApprovalAndRevisionGuard(mode)
            )
        }
    }

    /**
     * P2-11 copy contract: the source must not describe the mutation guard as a temporary,
     * phase-dependent state that a later release would lift.
     */
    @Test
    fun policyCommentDoesNotDescribeTheGuardAsTemporary() {
        val source = ExecutionModePolicy::class.java
            .protectionDomain?.codeSource?.location
            ?.let { runCatching {
                val file = java.io.File(it.toURI())
                val src = java.io.File(file, "../../../../src/main/java/com/example/ai/contracts/ExecutionModePolicy.kt")
                if (src.exists()) src.readText() else null
            }.getOrNull() }
            ?: readSourceFromRepo()

        assertTrue("ExecutionModePolicy source must be readable for the copy contract", source != null)
        val text = source!!.lowercase()

        assertFalse(
            "P2-11 (J-09): the mutation guard must not be described as temporary " +
                "('until Phase 7'). Mutation is permanently routed through the approval gateway.",
            text.contains("until phase 7")
        )
        assertTrue(
            "P2-11 (J-09): the policy should state the guard is a PERMANENT invariant",
            text.contains("permanent")
        )
    }

    private fun readSourceFromRepo(): String? {
        val candidates = listOf(
            java.io.File("app/src/main/java/com/example/ai/contracts/ExecutionModePolicy.kt"),
            java.io.File("src/main/java/com/example/ai/contracts/ExecutionModePolicy.kt"),
            java.io.File("../app/src/main/java/com/example/ai/contracts/ExecutionModePolicy.kt")
        )
        return candidates.firstOrNull { it.exists() }?.readText()
    }
}
