package com.example.ai.conversation

import com.example.ai.contracts.ExecutionMode
import com.example.ai.contracts.ExecutionModePolicy
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.PromptPermissionMode
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ConversationPolicyEngineTest {

    private fun createSampleProjectContext(
        revision: Long = 3L,
        freshness: String = "CURRENT",
        knownInputs: Map<String, String> = mapOf(
            "lengthM" to "50.0",
            "voltage" to "400",
            "installation" to "Perforated Cable Tray"
        )
    ): BoundedProjectContext {
        return BoundedProjectContext(
            projectId = "PROJ-101",
            projectName = "Commercial Center",
            liveRevision = revision,
            activeScreen = "CircuitsGridScreen",
            activeEntityType = "CIRCUIT",
            activeEntityId = "201",
            activeEntityName = "Feeder C-07",
            knownEntityInputs = knownInputs,
            freshnessSummary = mapOf(
                "CABLE_SIZING" to freshness,
                "VOLTAGE_DROP" to freshness
            ),
            validatedResultSummary = "300.0 mm² Cu, Iz=345A, VD=1.85%"
        )
    }

    // 1. Available context does not ask again
    @Test
    fun availableContext_doesNotAskAgain() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "size this",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.RUN_CALCULATION, decision.action.actionType)
        assertTrue(decision.updatedState.missingBlockingSlots.isEmpty())
    }

    // 2. Missing blocking input asks one narrow question
    @Test
    fun missingBlockingInput_asksOneNarrowQuestion() {
        val ctx = createSampleProjectContext(knownInputs = mapOf("lengthM" to "50.0"))
        val input = ConversationTurnInput(
            userUtterance = "size this",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.ASK_BLOCKING_QUESTION, decision.action.actionType)
        assertEquals(listOf("installationMethod"), decision.action.requiredSlots)
        assertEquals(ResponseMode.BLOCKING_QUESTION, decision.action.responseMode)
    }

    // 3. Optional preference does not block calculation
    @Test
    fun optionalPreference_doesNotBlockCalculation() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "calculate feeder",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.RUN_CALCULATION, decision.action.actionType)
        assertFalse(decision.action.requiredSlots.contains("manufacturerPreference"))
    }

    // 4. Safety-critical input is never silently assumed
    @Test
    fun safetyCriticalInput_isNeverSilentlyAssumed() {
        val ctx = createSampleProjectContext(knownInputs = emptyMap())
        val input = ConversationTurnInput(
            userUtterance = "size circuit",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.ASK_BLOCKING_QUESTION, decision.action.actionType)
        assertTrue(decision.action.requiredSlots.contains("installationMethod"))
    }

    // 5. Explicit circuit ID wins over recent context
    @Test
    fun explicitCircuitIdWinsOverRecentContext() {
        val ctx = createSampleProjectContext()
        val c06 = EntityBinding("CIRCUIT", "C-06", "Feeder C-06")
        val c07 = EntityBinding("CIRCUIT", "C-07", "Feeder C-07")
        val input = ConversationTurnInput(
            userUtterance = "Why is C-06 using 240?",
            projectContext = ctx,
            availableEntities = listOf(c06, c07)
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals("C-06", decision.updatedState.activeEntityId)
    }

    // 6. Entity switch clears incompatible proposal
    @Test
    fun entitySwitch_clearsIncompatibleProposal() {
        val ctx = createSampleProjectContext()
        val c07 = EntityBinding("CIRCUIT", "C-07", "Feeder C-07")
        val c08 = EntityBinding("CIRCUIT", "C-08", "Feeder C-08")

        val stateWithProposal = ConversationStateManager.startTaskForEntity(
            priorState = null,
            projectContext = ctx,
            entity = c07,
            intent = AssistantIntent.CREATE_PROPOSAL,
            objective = EngineeringObjective.OPTIMIZE_CABLE_SIZE
        ).let {
            ConversationStateManager.bindProposal(it, "PROP-77", "digest-77", "key-77")
        }

        assertNotNull(stateWithProposal.activeProposalId)

        val input = ConversationTurnInput(
            userUtterance = "Check C-08",
            projectContext = ctx,
            priorState = stateWithProposal,
            availableEntities = listOf(c07, c08)
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals("C-08", decision.updatedState.activeEntityId)
        assertNull("Switching entity must clear incompatible proposal", decision.updatedState.activeProposalId)
    }

    // 7. Entity switch clears incompatible what-if
    @Test
    fun entitySwitch_clearsIncompatibleWhatIf() {
        val ctx = createSampleProjectContext()
        val c07 = EntityBinding("CIRCUIT", "C-07", "Feeder C-07")
        val c08 = EntityBinding("CIRCUIT", "C-08", "Feeder C-08")

        val stateWithWhatIf = ConversationStateManager.startTaskForEntity(
            priorState = null,
            projectContext = ctx,
            entity = c07,
            intent = AssistantIntent.WHAT_IF,
            objective = EngineeringObjective.OPTIMIZE_CABLE_SIZE
        ).let {
            ConversationStateManager.setWhatIfOverride(it, "conductorMaterial", "Al")
        }

        assertTrue(stateWithWhatIf.isWhatIfBranch)

        val input = ConversationTurnInput(
            userUtterance = "Inspect C-08",
            projectContext = ctx,
            priorState = stateWithWhatIf,
            availableEntities = listOf(c07, c08)
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals("C-08", decision.updatedState.activeEntityId)
        assertFalse(decision.updatedState.isWhatIfBranch)
        assertTrue(decision.updatedState.whatIfOverrides.isEmpty())
    }

    // 8. Contradictory voltage offers what-if
    @Test
    fun contradictoryVoltage_offersWhatIfOrChange() {
        val ctx = createSampleProjectContext(knownInputs = mapOf("voltage" to "400"))
        val input = ConversationTurnInput(
            userUtterance = "Calculate feeder at 230V",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.RUN_WHAT_IF, decision.action.actionType)
        assertEquals("CONTRADICTORY_VOLTAGE_OFFER_WHAT_IF", decision.action.reasonCode)
        assertEquals(1, decision.updatedState.detectedContradictions.size)
        val contradiction = decision.updatedState.detectedContradictions.first()
        assertEquals("voltage", contradiction.fieldName)
        assertEquals("400", contradiction.projectValue)
        assertEquals("230", contradiction.userValue)
    }

    // 9. Stale result routes to recalculate not explain
    @Test
    fun staleResult_routesToRecalculateNotExplain() {
        val ctx = createSampleProjectContext(freshness = "STALE")
        val input = ConversationTurnInput(
            userUtterance = "Why did the cable jump to 300?",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.RECALCULATE_STALE, decision.action.actionType)
        assertEquals(ResponseMode.RESULT_SUMMARY, decision.action.responseMode)
    }

    // 10. Revision advance invalidates active proposal
    @Test
    fun revisionAdvance_invalidatesActiveProposal() {
        val ctxRev3 = createSampleProjectContext(revision = 3L)
        val state = ConversationStateManager.startTaskForEntity(
            priorState = null,
            projectContext = ctxRev3,
            entity = EntityBinding("CIRCUIT", "201"),
            intent = AssistantIntent.REVIEW_PROPOSAL,
            objective = EngineeringObjective.OPTIMIZE_CABLE_SIZE
        ).let {
            ConversationStateManager.bindProposal(it, "PROP-99", "digest-99", "key-99")
        }

        assertEquals("PROP-99", state.activeProposalId)

        val ctxRev4 = createSampleProjectContext(revision = 4L)
        val input = ConversationTurnInput(
            userUtterance = "show proposal",
            projectContext = ctxRev4,
            priorState = state
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(4L, decision.updatedState.baseProjectRevision)
        assertNull("Revision advance from 3 to 4 must invalidate proposal", decision.updatedState.activeProposalId)
    }

    // 11. What-if changes only requested parameter and never produces applied state
    @Test
    fun whatIf_changesOnlyRequestedParameter_andNeverProducesAppliedState() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "Try aluminum",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.RUN_WHAT_IF, decision.action.actionType)
        assertTrue(decision.updatedState.isWhatIfBranch)
        assertEquals("Al", decision.updatedState.whatIfOverrides["conductorMaterial"])
        assertNotEquals(ProvenanceLabel.APPLIED, decision.updatedState.knownSlots["conductorMaterial"]?.provenance)
    }

    // 12. User says apply routes to proposal review / approval prompt not execution
    @Test
    fun userSaysApply_routesToProposalReviewNotExecution() {
        val ctx = createSampleProjectContext()
        val state = ConversationStateManager.startTaskForEntity(
            priorState = null,
            projectContext = ctx,
            entity = EntityBinding("CIRCUIT", "201"),
            intent = AssistantIntent.CREATE_PROPOSAL,
            objective = EngineeringObjective.OPTIMIZE_CABLE_SIZE
        ).let {
            ConversationStateManager.bindProposal(it, "PROP-101", "digest-101", "key-101")
        }

        val input = ConversationTurnInput(
            userUtterance = "apply",
            projectContext = ctx,
            priorState = state
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, decision.action.actionType)
        assertEquals(ResponseMode.APPROVAL_REQUIRED, decision.action.responseMode)
        assertEquals(PromptPermissionMode.PROPOSE, decision.action.requiredPermission)
    }

    // 13. User says ok / yes does not issue approval
    @Test
    fun userSaysOk_doesNotIssueApproval() {
        val ctx = createSampleProjectContext()
        val state = ConversationStateManager.startTaskForEntity(
            priorState = null,
            projectContext = ctx,
            entity = EntityBinding("CIRCUIT", "201"),
            intent = AssistantIntent.CREATE_PROPOSAL,
            objective = EngineeringObjective.OPTIMIZE_CABLE_SIZE
        ).let {
            ConversationStateManager.bindProposal(it, "PROP-102", "digest-102", "key-102")
        }

        val input = ConversationTurnInput(
            userUtterance = "ok",
            projectContext = ctx,
            priorState = state
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, decision.action.actionType)
        assertFalse("Conversational OK must not execute mutation", ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 14. Request apply without proposal routes to error
    @Test
    fun requestApply_withoutProposal_routesToReviewOrError() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "apply changes",
            projectContext = ctx,
            priorState = null
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.SAFE_ERROR, decision.action.actionType)
        assertEquals("NO_ACTIVE_PROPOSAL_TO_APPLY", decision.action.reasonCode)
    }

    // 15. Known slots survive compatible follow up
    @Test
    fun knownSlotsSurviveCompatibleFollowUp() {
        val ctx = createSampleProjectContext(knownInputs = mapOf("lengthM" to "50.0"))
        // Turn 1: user provides installation
        val input1 = ConversationTurnInput(
            userUtterance = "Tray",
            projectContext = ctx
        )
        val dec1 = ConversationPolicyEngine.evaluateTurn(input1)
        assertEquals("Perforated Cable Tray", dec1.updatedState.knownSlots["installation"]?.value)

        // Turn 2: follow-up on same entity
        val input2 = ConversationTurnInput(
            userUtterance = "Size this now",
            projectContext = ctx,
            priorState = dec1.updatedState
        )
        val dec2 = ConversationPolicyEngine.evaluateTurn(input2)
        assertEquals(ConversationActionType.RUN_CALCULATION, dec2.action.actionType)
        assertEquals("Perforated Cable Tray", dec2.updatedState.knownSlots["installation"]?.value)
    }

    // 16. Cost optimize without pricing marks cost unavailable
    @Test
    fun costOptimize_withoutCostData_marksCostUnavailable() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "Make it cheaper",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.COMPARE_CANDIDATES, decision.action.actionType)
        assertEquals("COST_OPTIMIZE_WITHOUT_PRICING", decision.action.reasonCode)
        assertEquals("false", decision.action.candidateParameters["costDataAvailable"])
    }

    // 17. Can I 240 routes to candidate evaluation
    @Test
    fun canI240_routesToCandidateEvaluation() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "Can 240 work?",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.EVALUATE_CANDIDATE, decision.action.actionType)
        assertEquals("240.0", decision.action.candidateParameters["targetSize"])
    }

    // 18. Why 300 routes to explain current result when fresh
    @Test
    fun why300_routesToExplainCurrentResultWhenFresh() {
        val ctx = createSampleProjectContext(freshness = "CURRENT")
        val input = ConversationTurnInput(
            userUtterance = "Why did you choose 300 mm²?",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.EXPLAIN_RESULT, decision.action.actionType)
        assertEquals(EngineeringObjective.EXPLAIN_GOVERNING_CRITERION, decision.updatedState.engineeringObjective)
    }

    // 19. Out of scope returns brief typed redirect
    @Test
    fun outOfScope_returnsBriefTypedRedirect() {
        val ctx = createSampleProjectContext()
        val input = ConversationTurnInput(
            userUtterance = "What is the weather in Cairo?",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.OUT_OF_SCOPE, decision.action.actionType)
        assertEquals(ResponseMode.DIRECT_ANSWER, decision.action.responseMode)
    }

    // 20. Arabic why is normalized without changing machine logic
    @Test
    fun arabicWhy_isNormalizedWithoutChangingEngineeringLogic() {
        val ctx = createSampleProjectContext(freshness = "CURRENT")
        val input = ConversationTurnInput(
            userUtterance = "ليه اخترت 300 مم²؟",
            projectContext = ctx
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.EXPLAIN_RESULT, decision.action.actionType)
        assertEquals(ConversationLanguage.AR, decision.updatedState.userLanguage)
    }

    // 21. Arabic apply remains non-executable
    @Test
    fun arabicApply_remainsNonExecutable() {
        val ctx = createSampleProjectContext()
        val state = ConversationStateManager.startTaskForEntity(
            priorState = null,
            projectContext = ctx,
            entity = EntityBinding("CIRCUIT", "201"),
            intent = AssistantIntent.CREATE_PROPOSAL,
            objective = EngineeringObjective.OPTIMIZE_CABLE_SIZE,
            language = ConversationLanguage.AR
        ).let {
            ConversationStateManager.bindProposal(it, "PROP-AR-1", "digest-ar", "key-ar")
        }

        val input = ConversationTurnInput(
            userUtterance = "طبق المقترح",
            projectContext = ctx,
            priorState = state
        )
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE, decision.action.actionType)
        assertEquals(ConversationLanguage.AR, decision.updatedState.userLanguage)
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 22. Conversation state round trips with Moshi
    @Test
    fun conversationState_roundTripsWithMoshi() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(ConversationTaskState::class.java)

        val state = ConversationTaskState(
            taskId = "TASK-505",
            projectId = "PROJ-101",
            baseProjectRevision = 2L,
            activeEntityType = "CIRCUIT",
            activeEntityId = "201",
            activeEntityName = "Feeder 201",
            primaryIntent = AssistantIntent.CALCULATE,
            engineeringObjective = EngineeringObjective.VERIFY_COMPLIANCE,
            knownSlots = mapOf("size" to SlotValue("size", SlotState.KNOWN, "300.0", "mm²", ProvenanceLabel.KNOWN_FROM_PROJECT, false, 2L)),
            missingBlockingSlots = listOf("installationMethod"),
            detectedContradictions = listOf(ContradictionRecord("voltage", "400", "230")),
            isWhatIfBranch = true,
            whatIfOverrides = mapOf("voltage" to "230"),
            activeProposalId = "PROP-505",
            activeProposalDigest = "sha256-505",
            activeProposalSemanticKey = "key-505"
        )

        val json = adapter.toJson(state)
        assertNotNull(json)
        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals(state.taskId, restored?.taskId)
        assertEquals(state.projectId, restored?.projectId)
        assertEquals(state.primaryIntent, restored?.primaryIntent)
        assertEquals(state.knownSlots.size, restored?.knownSlots?.size)
        assertEquals(state.detectedContradictions.size, restored?.detectedContradictions?.size)
    }

    // 23. Determinism across calls
    @Test
    fun sameInputs_produceSameDecision() {
        val ctx = createSampleProjectContext()
        val input1 = ConversationTurnInput("Why 300?", ctx)
        val input2 = ConversationTurnInput("Why 300?", ctx)

        val dec1 = ConversationPolicyEngine.evaluateTurn(input1, decisionIdProvider = { "FIXED-DEC" })
        val dec2 = ConversationPolicyEngine.evaluateTurn(input2, decisionIdProvider = { "FIXED-DEC" })

        assertEquals(dec1.action.actionType, dec2.action.actionType)
        assertEquals(dec1.action.reasonCode, dec2.action.reasonCode)
        assertEquals(dec1.updatedState.primaryIntent, dec2.updatedState.primaryIntent)
    }

    // 24. Locale does not change machine decision
    @Test
    fun localeDoesNotChangeMachineDecision() {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            val ctx = createSampleProjectContext()
            val inputDe = ConversationTurnInput("size this", ctx)
            val decDe = ConversationPolicyEngine.evaluateTurn(inputDe, decisionIdProvider = { "DEC-1" })

            Locale.setDefault(Locale.US)
            val inputUs = ConversationTurnInput("size this", ctx)
            val decUs = ConversationPolicyEngine.evaluateTurn(inputUs, decisionIdProvider = { "DEC-1" })

            assertEquals(decDe.action.actionType, decUs.action.actionType)
            assertEquals(decDe.action.reasonCode, decUs.action.reasonCode)
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    // 25. No database, UI, network, or mutation dependency
    @Test
    fun noDatabaseUiNetworkMutationDependency() {
        val engineClass = ConversationPolicyEngine::class.java
        for (field in engineClass.declaredFields) {
            assertFalse(field.type.name.contains("Room"))
            assertFalse(field.type.name.contains("AppDatabase"))
            assertFalse(field.type.name.contains("ViewModel"))
            assertFalse(field.type.name.contains("Firebase"))
        }
    }

    // 26. ExecutionMode.MUTATION remains disabled
    @Test
    fun executionModeMutationRemainsDisabled() {
        assertFalse(ExecutionModePolicy.isCurrentlyExecutable(ExecutionMode.MUTATION))
    }

    // 27. Greetings return direct welcoming answer without triggering blocking question
    @Test
    fun greetings_returnDirectAnswerWithoutTriggeringBlockingQuestion() {
        // Missing installationMethod in context: a calculation query would trigger ASK_BLOCKING_QUESTION
        val ctx = createSampleProjectContext(knownInputs = emptyMap())

        val greetings = listOf("hi", "hello", "hey", "good morning", "مرحبا", "سلام", "اهلا", "السلام عليكم")
        for (greeting in greetings) {
            val input = ConversationTurnInput(userUtterance = greeting, projectContext = ctx)
            val decision = ConversationPolicyEngine.evaluateTurn(input)

            assertEquals("Greeting '$greeting' must return READ_CONTEXT action",
                ConversationActionType.READ_CONTEXT, decision.action.actionType)
            assertEquals("Greeting '$greeting' must have GREETING_ACKNOWLEDGED reasonCode",
                "GREETING_ACKNOWLEDGED", decision.action.reasonCode)
            assertEquals("Greeting '$greeting' must return DIRECT_ANSWER mode",
                ResponseMode.DIRECT_ANSWER, decision.action.responseMode)
            assertTrue("Greeting '$greeting' must not have missing blocking slots",
                decision.updatedState.missingBlockingSlots.isEmpty())
            assertTrue("Greeting '$greeting' must have a non-blank welcoming explanation",
                decision.explanationSummary?.isNotBlank() == true)
        }
    }

    // 28. Greeting combined with explicit engineering intent routes to engineering rule
    @Test
    fun greetingWithEngineeringIntent_routesToEngineeringRule() {
        val ctx = createSampleProjectContext(knownInputs = emptyMap())
        val input = ConversationTurnInput(userUtterance = "hi, size this feeder", projectContext = ctx)
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        // Since installationMethod is missing and user explicitly asked to "size this feeder", it should trigger calculation rule
        assertEquals(ConversationActionType.ASK_BLOCKING_QUESTION, decision.action.actionType)
        assertTrue(decision.action.requiredSlots.contains("installationMethod"))
    }

    // 29. Why on sized circuit returns detailed governing explanation, not placeholder
    @Test
    fun why_onSizedCircuit_returnsDetailedGoverningExplanation() {
        val ctx = createSampleProjectContext(
            freshness = "CURRENT",
            knownInputs = mapOf(
                "installation" to "In Conduit",
                "cableSize" to "16.0",
                "cableMaterial" to "Cu",
                "insulation" to "XLPE",
                "designCurrentIb" to "45.0 A",
                "breakerSize" to "50 A",
                "breakerType" to "MCB",
                "deratedAmpacityIz" to "65.0 A",
                "deratingFactor" to "0.82",
                "voltageDropPct" to "1.20%",
                "voltageDropV" to "2.40 V",
                "maxVDpct" to "3.00"
            )
        )
        val input = ConversationTurnInput(userUtterance = "why?", projectContext = ctx)
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.EXPLAIN_RESULT, decision.action.actionType)
        val explanation = decision.explanationSummary ?: ""
        assertFalse("Must not return raw placeholder string", explanation == "Explaining governing criteria for active sizing result.")
        assertTrue("Explanation must mention conductor size", explanation.contains("16.0 mm²"))
        assertTrue("Explanation must mention ampacity Iz", explanation.contains("65.0 A"))
        assertTrue("Explanation must mention breaker", explanation.contains("50 A"))
        assertTrue("Explanation must mention voltage drop", explanation.contains("1.20%"))
        assertTrue("Explanation must mention IEC standard", explanation.contains("IEC 60364-5-52"))
    }

    // 30. Cable inquiry on already-sized circuit answers from current result
    @Test
    fun cableInquiry_onSizedCircuit_returnsAnswerFromCurrentResult() {
        val ctx = createSampleProjectContext(
            freshness = "CURRENT",
            knownInputs = mapOf(
                "installation" to "In Conduit",
                "cableSize" to "16.0",
                "cableMaterial" to "Cu",
                "insulation" to "XLPE",
                "designCurrentIb" to "45.0 A",
                "breakerSize" to "50 A",
                "deratedAmpacityIz" to "65.0 A"
            )
        )
        val inquiries = listOf("cable", "what cable", "size", "sizing", "feeder")
        for (query in inquiries) {
            val input = ConversationTurnInput(userUtterance = query, projectContext = ctx)
            val decision = ConversationPolicyEngine.evaluateTurn(input)

            assertEquals("Query '$query' must return ANSWER_FROM_CURRENT_RESULT",
                ConversationActionType.ANSWER_FROM_CURRENT_RESULT, decision.action.actionType)
            val explanation = decision.explanationSummary ?: ""
            assertFalse("Query '$query' must not return 'Ready for deterministic calculation.'",
                explanation == "All required inputs satisfied. Ready for deterministic calculation.")
            assertTrue("Query '$query' must explain cable size", explanation.contains("16.0 mm²"))
        }
    }

    // 31. Known installation does not trigger missing blocking question
    @Test
    fun knownInstallation_doesNotTriggerMissingBlockingQuestion() {
        val ctx = createSampleProjectContext(
            knownInputs = mapOf(
                "installation" to "Laid in Ducts",
                "cableSize" to "25.0"
            )
        )
        val input = ConversationTurnInput(userUtterance = "cable", projectContext = ctx)
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertNotEquals("Must not ask blocking question when installation is known",
            ConversationActionType.ASK_BLOCKING_QUESTION, decision.action.actionType)
    }

    // 32. Arabic why on sized circuit returns Arabic governing explanation
    @Test
    fun arabicWhy_onSizedCircuit_returnsArabicGoverningExplanation() {
        val ctx = createSampleProjectContext(
            freshness = "CURRENT",
            knownInputs = mapOf(
                "installation" to "داخل مواسير",
                "cableSize" to "16.0",
                "cableMaterial" to "Cu",
                "insulation" to "XLPE",
                "designCurrentIb" to "45.0 A",
                "breakerSize" to "50 A",
                "deratedAmpacityIz" to "65.0 A",
                "voltageDropPct" to "1.20%"
            )
        )
        val input = ConversationTurnInput(userUtterance = "ليه؟", projectContext = ctx)
        val decision = ConversationPolicyEngine.evaluateTurn(input)

        assertEquals(ConversationActionType.EXPLAIN_RESULT, decision.action.actionType)
        assertEquals(ConversationLanguage.AR, decision.updatedState.userLanguage)
        val explanation = decision.explanationSummary ?: ""
        assertTrue("Must contain Arabic conductor header", explanation.contains("المقطع المحدد"))
        assertTrue("Must contain cable size", explanation.contains("16.0 مم²"))
        assertTrue("Must contain governing criterion in Arabic", explanation.contains("المعيار الحاكم"))
    }
}
