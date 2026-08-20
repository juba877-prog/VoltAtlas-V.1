package com.example.ai.conversation

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.PromptPermissionMode
import com.example.ai.runtime.ResponseMode
import org.junit.Assert.*
import org.junit.Test

class ConversationGoldenFlowTest {

    private fun createProjectContext(
        freshness: String = "CURRENT",
        knownInputs: Map<String, String> = mapOf(
            "lengthM" to "50.0",
            "voltage" to "400",
            "installation" to "Perforated Cable Tray"
        )
    ): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = "PROJ-GOLDEN",
            projectName = "Commercial Golden Tower",
            liveRevision = 5L,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = "C-07",
            activeEntityName = "Feeder C-07",
            knownEntityInputs = knownInputs,
            freshnessSummary = mapOf(
                "CABLE_SIZING" to freshness,
                "VOLTAGE_DROP" to freshness
            ),
            validatedResultSummary = "300.0 mm² Cu, Iz=345A, VD=1.85%"
        )
    }

    /**
     * Flow A:
     * 1. "Why is C-07 using 300?" -> EXPLAIN_RESULT
     * 2. "Can 240 work?" -> EVALUATE_CANDIDATE
     * 3. "Try aluminum." -> WHAT_IF preserving other slots
     * 4. "Use 300." -> CREATE_PROPOSAL
     * 5. "Apply." -> SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE (zero approval/db writes)
     */
    @Test
    fun testGoldenFlowA_MultiTurnExplorationToProposalAndApprovalPrompt() {
        val ctx = createProjectContext()
        val c07 = EntityBinding("CIRCUIT", "C-07", "Feeder C-07")
        val available = listOf(c07)

        // Turn 1: Why is C-07 using 300?
        val t1Input = ConversationTurnInput(
            userUtterance = "Why is C-07 using 300?",
            projectContext = ctx,
            availableEntities = available
        )
        val t1Dec = ConversationPolicyEngine.evaluateTurn(t1Input)
        assertEquals(ConversationActionType.EXPLAIN_RESULT, t1Dec.action.actionType)
        assertEquals(ResponseMode.RESULT_SUMMARY, t1Dec.action.responseMode)
        assertEquals("C-07", t1Dec.updatedState.activeEntityId)

        // Turn 2: Can 240 work?
        val t2Input = ConversationTurnInput(
            userUtterance = "Can 240 work?",
            projectContext = ctx,
            priorState = t1Dec.updatedState,
            availableEntities = available
        )
        val t2Dec = ConversationPolicyEngine.evaluateTurn(t2Input)
        assertEquals(ConversationActionType.EVALUATE_CANDIDATE, t2Dec.action.actionType)
        assertEquals("240.0", t2Dec.action.candidateParameters["targetSize"])
        assertEquals(ResponseMode.COMPARISON, t2Dec.action.responseMode)

        // Turn 3: Try aluminum.
        val t3Input = ConversationTurnInput(
            userUtterance = "Try aluminum",
            projectContext = ctx,
            priorState = t2Dec.updatedState,
            availableEntities = available
        )
        val t3Dec = ConversationPolicyEngine.evaluateTurn(t3Input)
        assertEquals(ConversationActionType.RUN_WHAT_IF, t3Dec.action.actionType)
        assertTrue(t3Dec.updatedState.isWhatIfBranch)
        assertEquals("Al", t3Dec.updatedState.whatIfOverrides["conductorMaterial"])
        assertEquals(ResponseMode.WHAT_IF_RESULT, t3Dec.action.responseMode)

        // Turn 4: Use 300.
        val t4Input = ConversationTurnInput(
            userUtterance = "Use 300",
            projectContext = ctx,
            priorState = t3Dec.updatedState,
            availableEntities = available
        )
        val t4Dec = ConversationPolicyEngine.evaluateTurn(t4Input)
        assertEquals(ConversationActionType.CREATE_PROPOSAL, t4Dec.action.actionType)
        assertNotNull(t4Dec.updatedState.activeProposalId)
        assertEquals(ResponseMode.PROPOSAL_REVIEW, t4Dec.action.responseMode)

        // Turn 5: Apply.
        val t5Input = ConversationTurnInput(
            userUtterance = "Apply",
            projectContext = ctx,
            priorState = t4Dec.updatedState,
            availableEntities = available
        )
        val t5Dec = ConversationPolicyEngine.evaluateTurn(t5Input)
        assertEquals(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, t5Dec.action.actionType)
        assertEquals(ResponseMode.APPROVAL_REQUIRED, t5Dec.action.responseMode)
        assertEquals(PromptPermissionMode.PROPOSE, t5Dec.action.requiredPermission)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    /**
     * Flow B:
     * 1. "Size this." -> active circuit loaded, installation missing -> ASK_BLOCKING_QUESTION
     * 2. "Tray." -> slot updated -> RUN_CALCULATION without repeating known inputs
     */
    @Test
    fun testGoldenFlowB_MissingInputToResolvedCalculation() {
        val ctxWithoutInstall = createProjectContext(
            knownInputs = mapOf("lengthM" to "50.0", "voltage" to "400")
        )
        val c07 = EntityBinding("CIRCUIT", "C-07", "Feeder C-07")

        // Turn 1: Size this.
        val t1Input = ConversationTurnInput(
            userUtterance = "Size this",
            projectContext = ctxWithoutInstall,
            availableEntities = listOf(c07)
        )
        val t1Dec = ConversationPolicyEngine.evaluateTurn(t1Input)
        assertEquals(ConversationActionType.ASK_BLOCKING_QUESTION, t1Dec.action.actionType)
        assertEquals(listOf("installationMethod"), t1Dec.action.requiredSlots)
        assertEquals(ResponseMode.BLOCKING_QUESTION, t1Dec.action.responseMode)

        // Turn 2: Tray.
        val t2Input = ConversationTurnInput(
            userUtterance = "Tray",
            projectContext = ctxWithoutInstall,
            priorState = t1Dec.updatedState,
            availableEntities = listOf(c07)
        )
        val t2Dec = ConversationPolicyEngine.evaluateTurn(t2Input)
        assertEquals(ConversationActionType.RUN_CALCULATION, t2Dec.action.actionType)
        assertEquals("Perforated Cable Tray", t2Dec.updatedState.knownSlots["installation"]?.value)
        assertTrue(t2Dec.updatedState.missingBlockingSlots.isEmpty())
        assertEquals(ResponseMode.RESULT_SUMMARY, t2Dec.action.responseMode)
    }

    /**
     * Flow C:
     * Project is 400V.
     * User: "Calculate at 230V." -> contradiction detected -> RUN_WHAT_IF with voltage=230 -> no silent project change
     */
    @Test
    fun testGoldenFlowC_VoltageContradictionHandledSafely() {
        val ctx400V = createProjectContext(
            knownInputs = mapOf("lengthM" to "50.0", "voltage" to "400", "installation" to "Perforated Cable Tray")
        )

        val input = ConversationTurnInput(
            userUtterance = "Calculate at 230V",
            projectContext = ctx400V
        )
        val dec = ConversationPolicyEngine.evaluateTurn(input)
        assertEquals(ConversationActionType.RUN_WHAT_IF, dec.action.actionType)
        assertEquals(ResponseMode.WHAT_IF_RESULT, dec.action.responseMode)
        assertEquals(1, dec.updatedState.detectedContradictions.size)
        val record = dec.updatedState.detectedContradictions.first()
        assertEquals("voltage", record.fieldName)
        assertEquals("400", record.projectValue)
        assertEquals("230", record.userValue)
        assertEquals("230", dec.updatedState.whatIfOverrides["voltage"])
    }
}
