package com.example.ai.conversation

import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.PromptPermissionMode
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseDetailLevel
import com.example.ai.runtime.ResponseMode
import java.util.UUID

/**
 * Pure deterministic policy engine evaluating user turns into typed decisions and updated state.
 *
 * Invariants:
 * - Deterministic: No network, no Room DB, no UI, no mutable global state, no LLM inference.
 * - Single-Use / Safety: Conversational "apply/ok/yes" never marks a proposal APPLIED and never mutates.
 * - Minimum-Question: Uses existing context before asking; asks only blocking safety-critical inputs.
 * - Contradiction & Stale: Detects conflicts and stale revisions; fails closed or offers what-if.
 * - Entity Boundary: Switching entities clears incompatible proposal and what-if bindings.
 */
object ConversationPolicyEngine {

    fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String = { "DEC-" + UUID.randomUUID().toString().take(8) }
    ): ConversationTurnDecision {
        val userUtterance = input.userUtterance.trim()
        val language = input.forcedLanguage ?: detectLanguage(userUtterance)

        // 1. Language Normalization & Slot Extraction
        val normalizedUtterance = normalizeUtterance(userUtterance)
        val extractedSlots = extractSlotValues(userUtterance, input.projectContext.liveRevision)

        // 2. Entity Resolution
        val resolvedEntity = resolveTargetEntity(
            utterance = userUtterance,
            projectContext = input.projectContext,
            priorState = input.priorState,
            availableEntities = input.availableEntities
        )

        // 3. Handle Entity Switch
        var workingState = if (input.priorState == null) {
            ConversationStateManager.startTaskForEntity(
                priorState = null,
                projectContext = input.projectContext,
                entity = resolvedEntity,
                intent = AssistantIntent.NAVIGATE,
                objective = EngineeringObjective.VERIFY_COMPLIANCE,
                language = language
            )
        } else if (resolvedEntity != null &&
            (resolvedEntity.entityType != input.priorState.activeEntityType ||
                    resolvedEntity.entityId != input.priorState.activeEntityId)
        ) {
            ConversationStateManager.switchEntity(
                state = input.priorState,
                newEntity = resolvedEntity,
                projectContext = input.projectContext
            )
        } else {
            ConversationStateManager.handleRevisionAdvance(
                state = input.priorState,
                newRevision = input.projectContext.liveRevision
            )
        }

        // Apply newly extracted slots to working state
        extractedSlots.forEach { (slotName, slotValue) ->
            workingState = ConversationStateManager.updateSlot(
                state = workingState,
                slotName = slotName,
                value = slotValue.value ?: "",
                provenance = slotValue.provenance
            )
        }

        // 4. Intent & Action Classification
        val decisionId = decisionIdProvider()
        val lower = normalizedUtterance.lowercase()

        // 4.0. Greeting Check ("hi", "hello", "مرحبا", "سلام")
        if (isGreeting(lower)) {
            val isArabic = detectLanguage(normalizedUtterance) == ConversationLanguage.AR
            val greetingMsg = if (isArabic) {
                "مرحباً بك! أنا مساعد VoltAtlas الهندسي. كيف يمكنني مساعدتك في الحسابات الكهربائية أو اختيار الكابلات أو مراجعة المشروع؟"
            } else {
                "Hello! I am your VoltAtlas Engineering Assistant. How can I assist you with your electrical calculations, cable sizing, or circuit analysis today?"
            }
            val action = ConversationAction(
                actionType = ConversationActionType.READ_CONTEXT,
                reasonCode = "GREETING_ACKNOWLEDGED",
                requiredPermission = PromptPermissionMode.READ,
                responseMode = ResponseMode.DIRECT_ANSWER
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = workingState.copy(
                    primaryIntent = AssistantIntent.CHECK_STATUS,
                    lastDecisionId = decisionId
                ),
                explanationSummary = greetingMsg
            )
        }

        // 4.1. Out of Scope Check
        if (isOutOfScope(lower)) {
            val action = ConversationAction(
                actionType = ConversationActionType.OUT_OF_SCOPE,
                reasonCode = "OUT_OF_SCOPE_REQUEST",
                requiredPermission = PromptPermissionMode.READ,
                responseMode = ResponseMode.DIRECT_ANSWER
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = workingState.copy(
                    primaryIntent = AssistantIntent.OUT_OF_SCOPE,
                    lastDecisionId = decisionId
                ),
                explanationSummary = "Query is outside electrical engineering calculation and navigation scope."
            )
        }

        // 4.2. Request Apply / Conversational Affirmation
        if (isApplyOrAffirmation(lower)) {
            return handleApplyOrAffirmation(workingState, decisionId)
        }

        // 4.3. Contradiction Detection (e.g. 230V requested on 400V circuit)
        val voltageContradiction = checkVoltageContradiction(lower, input.projectContext)
        if (voltageContradiction != null) {
            val updatedContradictions = workingState.detectedContradictions + voltageContradiction
            val updatedState = workingState.copy(
                primaryIntent = AssistantIntent.WHAT_IF,
                detectedContradictions = updatedContradictions,
                isWhatIfBranch = true,
                whatIfOverrides = workingState.whatIfOverrides + mapOf("voltage" to voltageContradiction.userValue),
                lastDecisionId = decisionId
            )
            val action = ConversationAction(
                actionType = ConversationActionType.RUN_WHAT_IF,
                reasonCode = "CONTRADICTORY_VOLTAGE_OFFER_WHAT_IF",
                requiredPermission = PromptPermissionMode.CALCULATE,
                candidateParameters = mapOf("voltage" to voltageContradiction.userValue),
                responseMode = ResponseMode.WHAT_IF_RESULT
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = "Voltage contradiction detected between project (${voltageContradiction.projectValue}V) and request (${voltageContradiction.userValue}V)."
            )
        }

        // 4.4. Explain Result ("Why did it jump to 300?", "Why 300?")
        if (isWhyOrExplainQuery(lower)) {
            val isStale = input.projectContext.freshnessSummary["CABLE_SIZING"] == "STALE"
            if (isStale) {
                val action = ConversationAction(
                    actionType = ConversationActionType.RECALCULATE_STALE,
                    reasonCode = "CABLE_RESULT_STALE",
                    requiredPermission = PromptPermissionMode.CALCULATE,
                    responseMode = ResponseMode.RESULT_SUMMARY
                )
                return ConversationTurnDecision(
                    decisionId = decisionId,
                    action = action,
                    updatedState = workingState.copy(
                        primaryIntent = AssistantIntent.CHECK_STATUS,
                        lastDecisionId = decisionId
                    ),
                    explanationSummary = "Calculation result is STALE due to project revision change. Recalculation required."
                )
            } else {
                val action = ConversationAction(
                    actionType = ConversationActionType.EXPLAIN_RESULT,
                    reasonCode = "EXPLAIN_GOVERNING_CRITERION",
                    requiredPermission = PromptPermissionMode.READ,
                    responseMode = ResponseMode.RESULT_SUMMARY
                )
                return ConversationTurnDecision(
                    decisionId = decisionId,
                    action = action,
                    updatedState = workingState.copy(
                        primaryIntent = AssistantIntent.EXPLAIN_RESULT,
                        engineeringObjective = EngineeringObjective.EXPLAIN_GOVERNING_CRITERION,
                        lastDecisionId = decisionId
                    ),
                    explanationSummary = "Explaining governing criteria for active sizing result."
                )
            }
        }

        // 4.5. Evaluate Candidate ("Can 240 work?", "Is 185 ok?")
        val candidateSize = extractCandidateSize(lower)
        if (candidateSize != null && isCandidateInquiry(lower)) {
            val updatedState = workingState.copy(
                primaryIntent = AssistantIntent.EVALUATE_CANDIDATE,
                engineeringObjective = EngineeringObjective.VERIFY_COMPLIANCE,
                lastDecisionId = decisionId
            )
            val action = ConversationAction(
                actionType = ConversationActionType.EVALUATE_CANDIDATE,
                reasonCode = "EVALUATE_CANDIDATE_SIZE",
                requiredPermission = PromptPermissionMode.CALCULATE,
                candidateParameters = mapOf("targetSize" to candidateSize),
                responseMode = ResponseMode.COMPARISON
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = "Evaluating requested cable candidate size $candidateSize mm²."
            )
        }

        // 4.6. What-If Scenario ("Try aluminum", "What if ambient is 50C")
        if (isWhatIfQuery(lower)) {
            val whatIfParam = extractWhatIfParameters(lower)
            val whatIfState = workingState.copy(
                primaryIntent = AssistantIntent.WHAT_IF,
                isWhatIfBranch = true,
                whatIfOverrides = workingState.whatIfOverrides + whatIfParam,
                lastDecisionId = decisionId
            )
            val action = ConversationAction(
                actionType = ConversationActionType.RUN_WHAT_IF,
                reasonCode = "RUN_WHAT_IF_SCENARIO",
                requiredPermission = PromptPermissionMode.CALCULATE,
                candidateParameters = whatIfState.whatIfOverrides,
                responseMode = ResponseMode.WHAT_IF_RESULT
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = whatIfState,
                explanationSummary = "Running in-memory what-if scenario with parameters ${whatIfState.whatIfOverrides}."
            )
        }

        // 4.7. Create Proposal ("Use 300", "Select 300", "Set cable to 300")
        if (isCreateProposalQuery(lower)) {
            val size = candidateSize ?: workingState.knownSlots["size"]?.value ?: "300.0"
            val updatedState = workingState.copy(
                primaryIntent = AssistantIntent.CREATE_PROPOSAL,
                activeProposalId = "PROP-" + UUID.randomUUID().toString().take(8),
                lifecycleState = ConversationLifecycleState.PROPOSAL_PENDING_REVIEW,
                lastDecisionId = decisionId
            )
            val action = ConversationAction(
                actionType = ConversationActionType.CREATE_PROPOSAL,
                reasonCode = "CREATE_CABLE_PROPOSAL",
                requiredPermission = PromptPermissionMode.PROPOSE,
                candidateParameters = mapOf("overrideCableSize" to size),
                responseMode = ResponseMode.PROPOSAL_REVIEW
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = "Creating cable modification proposal for size $size mm²."
            )
        }

        // 4.8. Review Proposal
        if (isReviewProposalQuery(lower)) {
            val action = ConversationAction(
                actionType = ConversationActionType.REVIEW_PROPOSAL,
                reasonCode = "REVIEW_ACTIVE_PROPOSAL",
                requiredPermission = PromptPermissionMode.PROPOSE,
                responseMode = ResponseMode.PROPOSAL_REVIEW
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = workingState.copy(
                    primaryIntent = AssistantIntent.REVIEW_PROPOSAL,
                    lastDecisionId = decisionId
                ),
                explanationSummary = "Reviewing active proposal diff."
            )
        }

        // 4.9. Cost Optimization / Compare Alternatives ("Make it cheaper", "Cheapest")
        if (isCostOptimizationQuery(lower)) {
            val action = ConversationAction(
                actionType = ConversationActionType.COMPARE_CANDIDATES,
                reasonCode = "COST_OPTIMIZE_WITHOUT_PRICING",
                requiredPermission = PromptPermissionMode.CALCULATE,
                candidateParameters = mapOf("costDataAvailable" to "false"),
                responseMode = ResponseMode.COMPARISON
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = workingState.copy(
                    primaryIntent = AssistantIntent.COMPARE,
                    engineeringObjective = EngineeringObjective.COST_OPTIMIZE,
                    lastDecisionId = decisionId
                ),
                explanationSummary = "Comparing valid cable cross-sections; cost data unavailable, comparing physical volume."
            )
        }

        // 4.10. Calculate / Size ("Size this", "Calculate")
        val missingSafetySlots = checkMissingSafetySlots(workingState, input.projectContext)
        if (missingSafetySlots.isNotEmpty()) {
            val updatedState = workingState.copy(
                primaryIntent = AssistantIntent.CALCULATE,
                missingBlockingSlots = missingSafetySlots,
                lifecycleState = ConversationLifecycleState.WAITING_FOR_USER_INPUT,
                lastDecisionId = decisionId
            )
            val action = ConversationAction(
                actionType = ConversationActionType.ASK_BLOCKING_QUESTION,
                reasonCode = "MISSING_BLOCKING_INPUTS",
                requiredPermission = PromptPermissionMode.READ,
                requiredSlots = missingSafetySlots,
                responseMode = ResponseMode.BLOCKING_QUESTION
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = "Missing safety-critical inputs: ${missingSafetySlots.joinToString()}."
            )
        }

        // If all blocking slots are known, proceed to run calculation
        val calcAction = ConversationAction(
            actionType = ConversationActionType.RUN_CALCULATION,
            reasonCode = "ALL_BLOCKING_SLOTS_SATISFIED",
            requiredPermission = PromptPermissionMode.CALCULATE,
            responseMode = ResponseMode.RESULT_SUMMARY
        )
        return ConversationTurnDecision(
            decisionId = decisionId,
            action = calcAction,
            updatedState = workingState.copy(
                primaryIntent = AssistantIntent.CALCULATE,
                lifecycleState = ConversationLifecycleState.READY_FOR_CALCULATION,
                lastDecisionId = decisionId
            ),
            explanationSummary = "All required inputs satisfied. Ready for deterministic calculation."
        )
    }

    private fun handleApplyOrAffirmation(
        state: ConversationTaskState,
        decisionId: String
    ): ConversationTurnDecision {
        if (state.activeProposalId != null) {
            // Conversational apply or affirmation leads strictly to explicit confirmation prompt
            val action = ConversationAction(
                actionType = ConversationActionType.SHOW_APPROVAL_REQUIRED_NON_EXECUTABLE,
                reasonCode = "EXPLICIT_USER_CONFIRMATION_REQUIRED",
                requiredPermission = PromptPermissionMode.PROPOSE,
                responseMode = ResponseMode.APPROVAL_REQUIRED
            )
            val updatedState = state.copy(
                primaryIntent = AssistantIntent.REQUEST_APPLY,
                lifecycleState = ConversationLifecycleState.PROPOSAL_PENDING_REVIEW,
                lastDecisionId = decisionId
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = "Proposal ${state.activeProposalId} requires explicit confirmation via dedicated UI action. Conversational text alone cannot apply changes."
            )
        } else {
            val action = ConversationAction(
                actionType = ConversationActionType.SAFE_ERROR,
                reasonCode = "NO_ACTIVE_PROPOSAL_TO_APPLY",
                requiredPermission = PromptPermissionMode.READ,
                responseMode = ResponseMode.SAFE_ERROR
            )
            val updatedState = state.copy(
                primaryIntent = AssistantIntent.REQUEST_APPLY,
                lastDecisionId = decisionId
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = "No active proposal exists to apply."
            )
        }
    }

    private fun checkMissingSafetySlots(
        state: ConversationTaskState,
        ctx: BoundedProjectContext
    ): List<String> {
        val missing = mutableListOf<String>()
        val hasInstallation = state.knownSlots.containsKey("installation") ||
                ctx.knownEntityInputs.containsKey("installation")
        if (!hasInstallation) {
            missing.add("installationMethod")
        }
        return missing
    }

    private fun checkVoltageContradiction(
        lower: String,
        ctx: BoundedProjectContext
    ): ContradictionRecord? {
        val projectVoltage = ctx.knownEntityInputs["voltage"] ?: "400"
        if (lower.contains("230v") || lower.contains("230 v") || lower.contains("230 فولت")) {
            if (projectVoltage == "400" || projectVoltage.contains("400")) {
                return ContradictionRecord(
                    fieldName = "voltage",
                    projectValue = projectVoltage,
                    userValue = "230",
                    projectSource = ProvenanceLabel.KNOWN_FROM_PROJECT,
                    userSource = ProvenanceLabel.USER_PROVIDED
                )
            }
        }
        return null
    }

    private fun resolveTargetEntity(
        utterance: String,
        projectContext: BoundedProjectContext,
        priorState: ConversationTaskState?,
        availableEntities: List<EntityBinding>
    ): EntityBinding? {
        // 1. Explicit mention in utterance (e.g. "C-06", "C-07", "Feeder 101", "Feeder 201")
        for (entity in availableEntities) {
            if (utterance.contains(entity.entityId, ignoreCase = true) ||
                (!entity.entityName.isNullOrBlank() && utterance.contains(entity.entityName, ignoreCase = true))
            ) {
                return entity
            }
        }

        // 2. Active entity in project context
        if (projectContext.activeEntityType != null && projectContext.activeEntityId != null) {
            return EntityBinding(
                entityType = projectContext.activeEntityType,
                entityId = projectContext.activeEntityId,
                entityName = projectContext.activeEntityName
            )
        }

        // 3. Prior bound entity
        if (priorState?.activeEntityType != null && priorState.activeEntityId != null) {
            return EntityBinding(
                entityType = priorState.activeEntityType,
                entityId = priorState.activeEntityId,
                entityName = priorState.activeEntityName
            )
        }

        return null
    }

    private fun extractSlotValues(utterance: String, revision: Long): Map<String, SlotValue> {
        val slots = mutableMapOf<String, SlotValue>()
        val lower = utterance.lowercase()

        // Size extraction
        val size = extractCandidateSize(lower)
        if (size != null) {
            slots["size"] = SlotValue("size", SlotState.USER_PROVIDED, size, "mm²", ProvenanceLabel.USER_PROVIDED, false, revision)
        }

        // Material extraction
        if (lower.contains("cu") || lower.contains("copper") || lower.contains("نحاس")) {
            slots["conductorMaterial"] = SlotValue("conductorMaterial", SlotState.USER_PROVIDED, "Cu", null, ProvenanceLabel.USER_PROVIDED, true, revision)
        } else if (lower.contains("al") || lower.contains("aluminum") || lower.contains("aluminium") || lower.contains("ألومنيوم")) {
            slots["conductorMaterial"] = SlotValue("conductorMaterial", SlotState.USER_PROVIDED, "Al", null, ProvenanceLabel.USER_PROVIDED, true, revision)
        }

        // Installation extraction
        if (lower.contains("tray") || lower.contains("تراي") || lower.contains("حامل كابلات")) {
            slots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "Perforated Cable Tray", null, ProvenanceLabel.USER_PROVIDED, true, revision)
        } else if (lower.contains("conduit") || lower.contains("مواسير") || lower.contains("duct")) {
            slots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "In Conduit", null, ProvenanceLabel.USER_PROVIDED, true, revision)
        } else if (lower.contains("direct buried") || lower.contains("مدفون")) {
            slots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "Direct Buried", null, ProvenanceLabel.USER_PROVIDED, true, revision)
        }

        return slots
    }

    private fun extractCandidateSize(lower: String): String? {
        val regex = Regex("""\b(10|16|25|35|50|70|95|120|150|185|240|300|400|500|630)\s*(mm2|mm²|sqmm|مم2|مم²)?\b""")
        val match = regex.find(lower)
        return match?.groupValues?.get(1)?.toDoubleOrNull()?.let { String.format("%.1f", it) }
    }

    private fun extractWhatIfParameters(lower: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (lower.contains("aluminum") || lower.contains("aluminium") || lower.contains("ألومنيوم")) {
            params["conductorMaterial"] = "Al"
        }
        if (lower.contains("copper") || lower.contains("نحاس")) {
            params["conductorMaterial"] = "Cu"
        }
        val size = extractCandidateSize(lower)
        if (size != null) {
            params["overrideCableSize"] = size
        }
        return params
    }

    private fun isApplyOrAffirmation(lower: String): Boolean {
        return lower == "apply" || lower == "طبق" || lower == "نفذ" || lower == "موافق" ||
                lower == "ok" || lower == "yes" || lower == "sure" ||
                lower == "ok apply" || lower == "yes apply" || lower == "do it" ||
                lower == "تمام" || lower == "نعم" || lower.contains("موافق") || lower.contains("اعتمد") ||
                lower.startsWith("apply") || lower.startsWith("طبق") || lower.startsWith("نفذ") ||
                lower.startsWith("اعتمد") || lower.startsWith("تمام") ||
                lower.startsWith("commit") || lower.startsWith("save changes")
    }

    private fun isCandidateInquiry(lower: String): Boolean {
        return lower.contains("can") || lower.contains("is") || lower.contains("work") ||
                lower.contains("ok") || lower.contains("suitable") || lower.contains("ينفع") ||
                lower.contains("مناسب") || lower.contains("يصلح") || lower.contains("ممكن")
    }

    private fun isWhatIfQuery(lower: String): Boolean {
        return lower.contains("what if") || lower.contains("ماذا لو") || lower.contains("try") ||
                lower.contains("جرب") || lower.contains("assume") || lower.contains("افترض") ||
                lower.contains("لو")
    }

    private fun isWhyOrExplainQuery(lower: String): Boolean {
        return lower.contains("why") || lower.contains("ليه") || lower.contains("لماذا") ||
                lower.contains("explain") || lower.contains("reason") || lower.contains("اشرح") ||
                lower.contains("فسر") || lower.contains("سبب")
    }

    private fun isCreateProposalQuery(lower: String): Boolean {
        val pattern = Regex("""\b(use|select|set)\s+\d+""")
        return pattern.containsMatchIn(lower) ||
                lower.startsWith("use ") || lower.startsWith("set size") || lower.startsWith("create proposal") ||
                lower.startsWith("اختر ") || lower.startsWith("اعتمد ") || lower.startsWith("ضع ")
    }

    private fun isReviewProposalQuery(lower: String): Boolean {
        return lower.contains("review proposal") || lower.contains("show proposal") ||
                lower.contains("راجع المقترح") || lower.contains("عرض المقترح")
    }

    private fun isCostOptimizationQuery(lower: String): Boolean {
        return lower.contains("cheaper") || lower.contains("cheapest") || lower.contains("ارخص") ||
                lower.contains("توفير") || lower.contains("cost") || lower.contains("سعر")
    }

    private fun isGreeting(lower: String): Boolean {
        val clean = lower.trim().trimEnd('.', '!', '?', ',', '،').trim()
        val exactGreetings = setOf(
            "hi", "hello", "hey", "greetings", "good morning", "good evening", "good afternoon",
            "howdy", "hello there", "hi there",
            "مرحبا", "مرحباً", "اهلا", "اهلاً", "أهلا", "أهلاً", "سلام", "السلام عليكم",
            "صباح الخير", "مساء الخير", "هلا"
        )
        if (clean in exactGreetings) return true

        val isGreetingPrefix = clean.startsWith("hi ") || clean.startsWith("hello ") ||
                clean.startsWith("hey ") || clean.startsWith("مرحبا ") ||
                clean.startsWith("أهلا ") || clean.startsWith("اهلا ") || clean.startsWith("سلام ")
        if (isGreetingPrefix) {
            val hasEngineeringIntent = clean.contains("size") || clean.contains("cable") ||
                    clean.contains("calculate") || clean.contains("voltage") || clean.contains("feeder") ||
                    clean.contains("كابل") || clean.contains("احسب") || clean.contains("مقطع") ||
                    clean.contains("هبوط") || clean.contains("فولت")
            return !hasEngineeringIntent
        }
        return false
    }

    private fun isOutOfScope(lower: String): Boolean {
        return lower.contains("weather") || lower.contains("joke") || lower.contains("poem") ||
                lower.contains("طقس") || lower.contains("نكتة") || lower.contains("شعر") ||
                lower.contains("football") || lower.contains("recipe")
    }

    fun detectLanguage(text: String): ConversationLanguage {
        val arabicRegex = Regex("""[\u0600-\u06FF]""")
        return if (arabicRegex.containsMatchIn(text)) ConversationLanguage.AR else ConversationLanguage.EN
    }

    fun normalizeUtterance(text: String): String {
        return text.trim()
            .replace(Regex("""\s+"""), " ")
    }
}
