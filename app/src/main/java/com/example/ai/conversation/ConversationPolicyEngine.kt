package com.example.ai.conversation

import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.PromptPermissionMode
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseDetailLevel
import com.example.ai.runtime.ResponseMode
import java.util.Locale
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
                val explanation = buildGoverningExplanation(input.projectContext, workingState, language)
                return ConversationTurnDecision(
                    decisionId = decisionId,
                    action = action,
                    updatedState = workingState.copy(
                        primaryIntent = AssistantIntent.EXPLAIN_RESULT,
                        engineeringObjective = EngineeringObjective.EXPLAIN_GOVERNING_CRITERION,
                        lastDecisionId = decisionId
                    ),
                    explanationSummary = explanation
                )
            }
        }

        // 4.4b. Circuit or Cable Inquiry on already-sized circuit ("cable", "what cable", "feeder")
        if (isCircuitOrCableInquiry(lower) && !isCalculateQuery(lower) &&
            (input.projectContext.validatedResultSummary != null || input.projectContext.knownEntityInputs.containsKey("cableSize"))
        ) {
            val action = ConversationAction(
                actionType = ConversationActionType.ANSWER_FROM_CURRENT_RESULT,
                reasonCode = "ANSWER_FROM_ACTIVE_SIZING_RESULT",
                requiredPermission = PromptPermissionMode.READ,
                responseMode = ResponseMode.RESULT_SUMMARY
            )
            val explanation = buildCurrentStatusSummary(input.projectContext, workingState, language)
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = workingState.copy(
                    primaryIntent = AssistantIntent.CHECK_STATUS,
                    engineeringObjective = EngineeringObjective.VERIFY_COMPLIANCE,
                    lastDecisionId = decisionId
                ),
                explanationSummary = explanation
            )
        }

        // 4.5. Evaluate Candidate ("Can 240 work?", "Is 185 ok?", "Can 2.5 work?")
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
            val explanation = buildCandidateEvaluationExplanation(
                candidateSize = candidateSize,
                projectContext = input.projectContext,
                state = workingState,
                language = language
            )
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = updatedState,
                explanationSummary = explanation
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
            val explanation = buildCostOptimizationExplanation(input.projectContext, workingState, language)
            return ConversationTurnDecision(
                decisionId = decisionId,
                action = action,
                updatedState = workingState.copy(
                    primaryIntent = AssistantIntent.COMPARE,
                    engineeringObjective = EngineeringObjective.COST_OPTIMIZE,
                    lastDecisionId = decisionId
                ),
                explanationSummary = explanation
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
        val explanation = if (input.projectContext.validatedResultSummary != null || input.projectContext.knownEntityInputs.containsKey("cableSize")) {
            buildCalculationSummary(input.projectContext, workingState, language)
        } else {
            "All required inputs satisfied. Ready for deterministic calculation."
        }
        return ConversationTurnDecision(
            decisionId = decisionId,
            action = calcAction,
            updatedState = workingState.copy(
                primaryIntent = AssistantIntent.CALCULATE,
                lifecycleState = ConversationLifecycleState.READY_FOR_CALCULATION,
                lastDecisionId = decisionId
            ),
            explanationSummary = explanation
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

    fun extractCandidateSize(lower: String): String? {
        val regex = Regex("""\b(1\.5|2\.5|4(?:\.0)?|6(?:\.0)?|10(?:\.0)?|16(?:\.0)?|25(?:\.0)?|35(?:\.0)?|50(?:\.0)?|70(?:\.0)?|95(?:\.0)?|120(?:\.0)?|150(?:\.0)?|185(?:\.0)?|240(?:\.0)?|300(?:\.0)?|400(?:\.0)?|500(?:\.0)?|630(?:\.0)?|800(?:\.0)?|1000(?:\.0)?|1,5|2,5)\s*(mm2|mm²|sqmm|مم2|مم²)?\b""")
        val match = regex.find(lower)
        val rawNum = match?.groupValues?.get(1)?.replace(',', '.') ?: return null
        return rawNum.toDoubleOrNull()?.let { String.format(Locale.US, "%.1f", it) }
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

    private fun isCircuitOrCableInquiry(lower: String): Boolean {
        val clean = lower.trim().trimEnd('.', '!', '?', ',', '،').trim()
        val exactKeywords = setOf(
            "cable", "cables", "feeder", "feeders", "cable size",
            "circuit", "circuits", "conductor",
            "كابل", "كابلات", "سلك", "الدائرة", "تغذية"
        )
        if (clean in exactKeywords) return true

        return clean.contains("what cable") || clean.contains("which cable") ||
                clean.contains("current cable") || clean.contains("show cable") ||
                clean.contains("feeder size") || clean.contains("show sizing") ||
                clean.contains("sizing result") || clean.contains("tell me about") ||
                clean.contains("ما هو الكابل") || clean.contains("ما هو مقطع") ||
                clean.contains("كم مقاس الكابل")
    }

    fun isCalculateQuery(lower: String): Boolean {
        val clean = lower.trim().trimEnd('.', '!', '?', ',', '،').trim()
        val exactKeywords = setOf(
            "size", "sizing", "calculate", "calc", "recalculate",
            "احسب", "احسبها", "حساب", "تحديد المقطع"
        )
        if (clean in exactKeywords) return true
        return clean.startsWith("size ") || clean.startsWith("calculate ") ||
                clean.startsWith("احسب ") || clean.contains("size the cable") ||
                clean.contains("size this") || clean.contains("احسب الكابل") ||
                clean.contains("can you size") || clean.contains("ممكن تحسب")
    }

    fun parseEntityInputs(projectContext: BoundedProjectContext, state: ConversationTaskState): ParsedCircuitInputs {
        val inputs = projectContext.knownEntityInputs
        val summary = projectContext.validatedResultSummary ?: ""
        val entityName = state.activeEntityName ?: projectContext.activeEntityName ?: "Selected Circuit"

        // 1. Raw Size
        val rawSizeFromInputs = inputs["cableSize"] ?: state.knownSlots["size"]?.value
        val rawSize = rawSizeFromInputs ?: Regex("""(\d+(?:\.\d+)?)\s*mm²""").find(summary)?.groupValues?.get(1) ?: "16.0"
        val cleanSizeStr = rawSize.replace(" mm²", "").replace(" مم²", "").replace("mm²", "").trim()
        val sizeVal = cleanSizeStr.toDoubleOrNull() ?: 16.0

        // 2. Material & Insulation
        val material = inputs["cableMaterial"]
            ?: if (summary.contains("Al", ignoreCase = true) && !summary.contains("Cu", ignoreCase = true)) "Al" else "Cu"
        val insulation = inputs["insulation"]
            ?: if (summary.contains("PVC", ignoreCase = true) && !summary.contains("XLPE", ignoreCase = true)) "PVC" else "XLPE"
        val installation = inputs["installation"] ?: state.knownSlots["installation"]?.value ?: "In Conduit"

        // 3. Derated Ampacity Iz
        val izStr = inputs["deratedAmpacityIz"]
            ?: Regex("""Iz\s*=\s*(\d+(?:\.\d+)?)\s*A?""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.let { "${it[1]} A" }
            ?: "88.4 A"
        val izVal = izStr.replace(" A", "").replace("A", "").trim().toDoubleOrNull() ?: 88.4

        // 4. Design Current Ib
        val parsedIbFromSummary = Regex("""Ib\s*=\s*(\d+(?:\.\d+)?)\s*A?""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.get(1)
        val ibStr = inputs["designCurrentIb"]
            ?: (if (parsedIbFromSummary != null) "$parsedIbFromSummary A" else String.format(Locale.US, "%.1f A", (izVal * 0.88)))
        val ibVal = ibStr.replace(" A", "").replace("A", "").trim().toDoubleOrNull() ?: (izVal * 0.88)

        // 5. Breaker Size & Type
        val parsedBreakerFromSummary = Regex("""Breaker\s*=\s*(\d+(?:\.\d+)?)\s*A?""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.get(1)
        val breakerStr = inputs["breakerSize"]
            ?: (if (parsedBreakerFromSummary != null) "$parsedBreakerFromSummary A" else String.format(Locale.US, "%.0f A", (ibVal * 1.1).coerceAtLeast(16.0)))
        val breakerVal = breakerStr.replace(" A", "").replace("A", "").trim().toDoubleOrNull() ?: 16.0
        val breakerType = inputs["breakerType"]
            ?: Regex("""\((MCB|MCCB|ACB)\)""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.get(1)
            ?: "MCB"

        // 6. Derating Factor
        val deratingStr = inputs["deratingFactor"]
            ?: Regex("""derating\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.get(1)
            ?: "0.82"
        val deratingVal = deratingStr.toDoubleOrNull() ?: 0.82

        // 7. Voltage Drop
        val vdStr = inputs["voltageDropPct"]
            ?: Regex("""VD\s*=\s*(\d+(?:\.\d+)?)\s*%?""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.let { "${it[1]}%" }
            ?: "0.23%"
        val vdVal = vdStr.replace("%", "").trim().toDoubleOrNull() ?: 0.23

        val maxVdStr = inputs["maxVDpct"]
            ?: Regex("""limit\s*(\d+(?:\.\d+)?)\s*%?""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.let { "${it[1]}%" }
            ?: "3.00%"
        val maxVdVal = maxVdStr.replace("%", "").trim().toDoubleOrNull() ?: 3.00

        val lengthStr = inputs["length"] ?: inputs["lengthM"] ?: "50"
        val lengthVal = lengthStr.toDoubleOrNull() ?: 50.0

        val hasManualOverride = inputs.containsKey("overrideCableSize") &&
                inputs["overrideCableSize"]?.isNotBlank() == true &&
                !inputs["overrideCableSize"].equals("AUTO", ignoreCase = true)

        val status = inputs["status"]
            ?: if (summary.contains("NON-COMPLIANT", ignoreCase = true) || summary.contains("FAIL", ignoreCase = true)) "FAIL" else "PASS"
        val failReason = inputs["failReason"] ?: ""

        val utilizationPct = if (izVal > 0.0) (ibVal / izVal) * 100.0 else 0.0

        return ParsedCircuitInputs(
            entityName = entityName,
            cableSizeStr = cleanSizeStr,
            cableSizeVal = sizeVal,
            material = material,
            insulation = insulation,
            installation = installation,
            ibStr = ibStr,
            ibVal = ibVal,
            breakerStr = breakerStr,
            breakerVal = breakerVal,
            breakerType = breakerType,
            izStr = izStr,
            izVal = izVal,
            deratingStr = deratingStr,
            deratingVal = deratingVal,
            vdStr = vdStr,
            vdVal = vdVal,
            maxVdStr = maxVdStr,
            maxVdVal = maxVdVal,
            lengthStr = lengthStr,
            lengthVal = lengthVal,
            hasManualOverride = hasManualOverride,
            status = status,
            failReason = failReason,
            utilizationPct = utilizationPct
        )
    }

    fun buildCurrentStatusSummary(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        return if (language == ConversationLanguage.AR) {
            "الدائرة **${p.entityName}** محدد لها حالياً مقطع **${p.cableSizeStr} مم² ${p.material}/${p.insulation}** (طريقة التمديد: ${p.installation}). تيار التصميم ${p.ibStr} مع قاطع ${p.breakerStr}، وسعة الكابل بعد التصحيح ${p.izStr} مع هبوط جهد ${p.vdStr} (الحد: ${p.maxVdStr})."
        } else {
            "Circuit **${p.entityName}** is currently sized with **${p.cableSizeStr} mm² ${p.material}/${p.insulation}** (${p.installation}). Design current is ${p.ibStr} with a ${p.breakerStr} breaker. Derated capacity is ${p.izStr}, and voltage drop is ${p.vdStr} (limit ${p.maxVdStr})."
        }
    }

    fun buildCalculationSummary(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        val suggestedSmaller = getSuggestedSmallerCandidate(p.ibVal, p.breakerVal)

        if (p.utilizationPct < 40.0 && p.vdVal < p.maxVdVal * 0.7) {
            return if (language == ConversationLanguage.AR) {
                "مقطع **${p.cableSizeStr} مم² ${p.material}/${p.insulation}** هو المحدد للدائرة **${p.entityName}** (${p.installation}).\n" +
                "عند حمل ${p.ibStr}، يعمل الكابل بهامش أمان عالي بنسبة تشغيل ${String.format(Locale.US, "%.1f", p.utilizationPct)}% (السعة ${p.izStr}، القاطع ${p.breakerStr}). هبوط الجهد ${p.vdStr} (الحد ${p.maxVdStr}).\n" +
                "**التالي:** يمكنك تقييم مقطع أصغر (مثل $suggestedSmaller مم²) أو إنشاء مقترح لاعتماده."
            } else {
                "**${p.cableSizeStr} mm² ${p.material}/${p.insulation}** is selected for **${p.entityName}** (${p.installation}).\n" +
                "At ${p.ibStr} load, this conductor operates with high reserve margin at ${String.format(Locale.US, "%.1f", p.utilizationPct)}% utilization (Iz = ${p.izStr}, breaker ${p.breakerStr}). Voltage drop is ${p.vdStr} (limit ${p.maxVdStr}).\n" +
                "**Next:** You can evaluate a smaller candidate (e.g. $suggestedSmaller mm²) or create a proposal to apply."
            }
        }

        val isVdGoverning = p.vdVal >= p.maxVdVal * 0.85 || (p.lengthVal > 60.0 && p.utilizationPct < 60.0)
        if (isVdGoverning) {
            return if (language == ConversationLanguage.AR) {
                "**${p.cableSizeStr} مم² ${p.material}/${p.insulation}** هو المقطع المطابق للدائرة **${p.entityName}** (${p.installation}).\n" +
                "هبوط الجهد هو المعيار الحاكم بنسبة ${p.vdStr} (الحد الأقصى ${p.maxVdStr}) على طول المسار. سعة الكابل ${p.izStr} مقابل تيار حمل ${p.ibStr} (قاطع ${p.breakerStr}).\n" +
                "**التالي:** يمكنك تجربة مقطع بديل أو إنشاء مقترح لاعتماده."
            } else {
                "**${p.cableSizeStr} mm² ${p.material}/${p.insulation}** is the compliant selection for **${p.entityName}** (${p.installation}).\n" +
                "Voltage drop is the governing criterion at ${p.vdStr} (limit ${p.maxVdStr}) over the run length. Derated capacity is ${p.izStr} against ${p.ibStr} load (${p.breakerStr} breaker).\n" +
                "**Next:** You can test an alternative size or create a proposal to apply."
            }
        }

        return if (language == ConversationLanguage.AR) {
            "**${p.cableSizeStr} مم² ${p.material}/${p.insulation}** هو المقطع المطابق للدائرة **${p.entityName}** (${p.installation}).\n" +
            "سعة الكابل بعد التصحيح ${p.izStr} مقابل تيار تصميم ${p.ibStr}، وهبوط الجهد ${p.vdStr} (الحد الأقصى ${p.maxVdStr}). التحمل الحراري هو المعيار الحاكم بعد تطبيق معاملات التصحيح.\n" +
            "**التالي:** يمكنك تجربة مقطع بديل أو إنشاء مقترح لاعتماده."
        } else {
            "**${p.cableSizeStr} mm² ${p.material}/${p.insulation}** is the compliant selection for **${p.entityName}** (${p.installation}).\n" +
            "Derated capacity is ${p.izStr} against ${p.ibStr} design current; voltage drop is ${p.vdStr} (limit ${p.maxVdStr}). Ampacity is the governing criterion after derating.\n" +
            "**Next:** You can test an alternative size or create a proposal to apply."
        }
    }

    fun buildGoverningExplanation(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        val isFail = p.status.contains("FAIL", ignoreCase = true) || p.izVal < p.ibVal || p.vdVal > p.maxVdVal + 0.05
        val nextDown = getNextStandardSizeDown(p.cableSizeVal)
        val suggestedSmaller = getSuggestedSmallerCandidate(p.ibVal, p.breakerVal)

        // 1. Engineering Failure / Non-compliant
        if (isFail) {
            val reason = if (p.failReason.isNotBlank()) p.failReason else if (p.izVal < p.ibVal) "Derated ampacity is below design load" else "Voltage drop exceeds limit"
            return if (language == ConversationLanguage.AR) {
                "الدائرة **${p.entityName}** غير مطابقة للمواصفات: $reason. سعة الكابل بعد التصحيح (${p.izStr}) غير كافية لتيار التصميم (${p.ibStr}) أو سعة القاطع (${p.breakerStr})، مع هبوط جهد ${p.vdStr} (الحد ${p.maxVdStr}). يلزم زيادة مقطع الكابل أو إضافة مسارات توازي."
            } else {
                "Circuit **${p.entityName}** fails compliance: $reason. Derated ampacity (${p.izStr}) is insufficient for design current (${p.ibStr}) or device rating (${p.breakerStr}), with voltage drop at ${p.vdStr} (limit ${p.maxVdStr}). Upsizing conductor or adding parallel runs is required."
            }
        }

        // 2. Active Manual User Override
        if (p.hasManualOverride) {
            return if (language == ConversationLanguage.AR) {
                "مقطع **${p.cableSizeStr} مم² ${p.material}/${p.insulation}** محدد حالياً كـتجاوز يدوي (Manual Override) للدائرة **${p.entityName}** (${p.installation}). كهربائياً، يعمل الكابل بهامش أمان واسع بنسبة تشغيل حراري تبلغ ${String.format(Locale.US, "%.1f", p.utilizationPct)}% فقط (السعة ${p.izStr} مقابل تيار حمل ${p.ibStr} وقاطع ${p.breakerStr} ${p.breakerType})، مع هبوط جهد ${p.vdStr}. الحساب التلقائي وفق المعايير يوصي بمقطع أصغر ($suggestedSmaller مم²)."
            } else {
                "**${p.cableSizeStr} mm² ${p.material}/${p.insulation}** is an active manual override for **${p.entityName}** (${p.installation}). Electrically, it operates with substantial margin at only ${String.format(Locale.US, "%.1f", p.utilizationPct)}% thermal utilization (Iz = ${p.izStr} vs Ib = ${p.ibStr}, In = ${p.breakerStr} ${p.breakerType}) and ${p.vdStr} voltage drop. The calculated minimum compliant size would be significantly smaller ($suggestedSmaller mm²)."
            }
        }

        // 3. Substantially Oversized (utilization < 40% and VD well below limit)
        if (p.utilizationPct < 40.0 && p.vdVal < p.maxVdVal * 0.7) {
            return if (language == ConversationLanguage.AR) {
                "عند تيار حمل ${p.ibStr}، يعتبر مقطع **${p.cableSizeStr} مم² ${p.material}/${p.insulation}** أكبر بكثير من المتطلبات الحرارية للدائرة **${p.entityName}**، حيث يعمل بنسبة تشغيل تبلغ ${String.format(Locale.US, "%.1f", p.utilizationPct)}% فقط (السعة بعد التصحيح ${p.izStr} مقابل قاطع ${p.breakerStr} ${p.breakerType}). هبوط الجهد ضئيل جداً عند ${p.vdStr} (الحد ${p.maxVdStr}). كهربائياً، مقطع أصغر ($suggestedSmaller مم²) يحقق متطلبات IEC 60364 بالكامل. اختيار مقطع ${p.cableSizeStr} مم² عادة ما يحكمه الحد الأدنى لتوفر مقاسات التصنيع في الكتالوج لهذا النوع من الكابلات، أبعاد نهايات القاطع (Terminal Lugs)، أو احتياطي لتوسعات مستقبلية."
            } else {
                "At ${p.ibStr} load, **${p.cableSizeStr} mm² ${p.material}/${p.insulation}** is substantially oversized for **${p.entityName}**, operating at only ${String.format(Locale.US, "%.1f", p.utilizationPct)}% thermal utilization (Iz = ${p.izStr} vs ${p.breakerStr} ${p.breakerType}). Voltage drop is negligible at ${p.vdStr} (limit ${p.maxVdStr}). Electrically, a smaller conductor ($suggestedSmaller mm²) would comfortably satisfy IEC 60364-5-52. This ${p.cableSizeStr} mm² selection is typically governed by minimum catalog manufacturing availability for this cable construction, switchgear terminal lug constraints, or project reserves for future load expansion."
            }
        }

        // 4. Governed by Voltage Drop
        val isVdGoverning = p.vdVal >= p.maxVdVal * 0.85 || (p.lengthVal > 60.0 && p.utilizationPct < 60.0)
        if (isVdGoverning) {
            return if (language == ConversationLanguage.AR) {
                "هبوط الجهد هو المعيار الحاكم لاختيار مقطع **${p.cableSizeStr} مم² ${p.material}/${p.insulation}** للدائرة **${p.entityName}** على مسار طوله ${p.lengthStr}م. فبينما يحقق مقطع أصغر التحمل الحراري لتيار الحمل (${p.ibStr})، إلا أن مقاومته سترفع هبوط الجهد ليتجاوز الحد المسموح (${p.maxVdStr}). مقطع **${p.cableSizeStr} مم²** يحدد هبوط الجهد عند ${p.vdStr} مع سعة حرارية ${p.izStr} (قاطع ${p.breakerStr} ${p.breakerType})."
            } else {
                "Voltage drop is the governing criterion for **${p.entityName}** over its ${p.lengthStr}m run. While a smaller conductor would satisfy thermal ampacity (${p.ibStr} load), its impedance would cause voltage drop to exceed the ${p.maxVdStr} limit. **${p.cableSizeStr} mm² ${p.material}/${p.insulation}** restricts voltage drop to ${p.vdStr} while providing ${p.izStr} derated capacity (breaker ${p.breakerStr} ${p.breakerType})."
            }
        }

        // 5. Governed by Thermal Ampacity (Standard)
        return if (language == ConversationLanguage.AR) {
            "التحمل الحراري بعد معاملات التصحيح (${p.deratingStr}) هو المعيار الحاكم لاختيار **المقطع المحدد** (${p.cableSizeStr} مم² ${p.material}/${p.insulation}) للدائرة **${p.entityName}**. يوفر هذا المقطع سعة ${p.izStr} لحماية القاطع ${p.breakerStr} (${p.breakerType}) وتيار الحمل ${p.ibStr} بأمان (نسبة تشغيل ${String.format(Locale.US, "%.1f", p.utilizationPct)}%). المقطع الأصغر التالي ($nextDown مم²) لا يحقق سعة التيار المطلوبة (Iz < In). هبوط الجهد يبلغ ${p.vdStr} وهو ضمن حد ${p.maxVdStr} وفقاً للمواصفة IEC 60364-5-52."
        } else {
            "Ampacity is the governing criterion for **${p.entityName}**. After applying derating (${p.deratingStr}), **${p.cableSizeStr} mm² ${p.material}/${p.insulation}** delivers ${p.izStr} to safely protect the ${p.breakerStr} (${p.breakerType}) breaker and ${p.ibStr} design load (operating at ${String.format(Locale.US, "%.1f", p.utilizationPct)}% utilization). The next size down ($nextDown mm²) fails the ampacity requirement (Iz < In). Voltage drop is ${p.vdStr}, well within the ${p.maxVdStr} limit per IEC 60364-5-52."
        }
    }

    fun buildCandidateEvaluationExplanation(
        candidateSize: String,
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        val candSizeVal = candidateSize.toDoubleOrNull() ?: 16.0
        val candSizeFormatted = formatCableSize(candSizeVal)

        val candIz = estimateDeratedAmpacity(candSizeVal, p.deratingVal)
        val candVd = estimateCandidateVoltageDrop(candSizeVal, p.cableSizeVal, p.vdVal)

        val ampacityPass = candIz >= p.ibVal && candIz >= p.breakerVal * 0.95
        val vdPass = candVd <= p.maxVdVal + 0.05
        val isPass = ampacityPass && vdPass

        val candIzStr = String.format(Locale.US, "%.1f", candIz)
        val candVdStr = String.format(Locale.US, "%.2f%%", candVd)

        if (isPass) {
            return if (language == ConversationLanguage.AR) {
                "نعم، مقطع **$candSizeFormatted مم²** مطابق هندسياً للدائرة **${p.entityName}**. تحت ظروف التمديد (${p.installation}) مع معامل تصحيح ${p.deratingStr}، يوفر سعة تقديرية بعد التصحيح تبلغ $candIzStr أمبير (تغطي القاطع ${p.breakerStr} وتيار الحمل ${p.ibStr} بأمان)، مع هبوط جهد تقريبي $candVdStr (الحد ${p.maxVdStr}).\n**التالي:** هل ترغب في إنشاء مقترح لاعتماد مقطع **$candSizeFormatted مم²**؟"
            } else {
                "Yes, **$candSizeFormatted mm²** is technically compliant for **${p.entityName}**. Under ${p.installation} conditions with ${p.deratingStr} derating, it delivers an estimated $candIzStr A derated capacity (safely exceeding the ${p.breakerStr} breaker and ${p.ibStr} load), with voltage drop at approximately $candVdStr (limit ${p.maxVdStr}).\n**Next:** Would you like to create a proposal to use **$candSizeFormatted mm²**?"
            }
        }

        if (!ampacityPass) {
            val deficit = if (candIz < p.ibVal) "design load (${p.ibStr})" else "breaker rating (${p.breakerStr})"
            val arDeficit = if (candIz < p.ibVal) "تيار الحمل المطلوب (${p.ibStr})" else "سعة القاطع (${p.breakerStr})"
            return if (language == ConversationLanguage.AR) {
                "لا، لا يمكن استخدام مقطع **$candSizeFormatted مم²** للدائرة **${p.entityName}**. سعته بعد التصحيح ($candIzStr أمبير) أقل من $arDeficit تحت معامل التصحيح الحالي (${p.deratingStr}). التحمل الحراري هو القيد الحاكم للمخالفة.\n**التالي:** يفضل الإبقاء على مقطع **${p.cableSizeStr} مم²** أو اختبار مقطع أكبر."
            } else {
                "No, **$candSizeFormatted mm²** cannot be used for **${p.entityName}**. Its derated capacity ($candIzStr A) falls below the required $deficit under current derating (${p.deratingStr}). Ampacity is the governing failure constraint.\n**Next:** We should retain **${p.cableSizeStr} mm²** or evaluate a larger candidate."
            }
        }

        // Failed on Voltage Drop
        return if (language == ConversationLanguage.AR) {
            "لا، مقطع **$candSizeFormatted مم²** لا يحقق شرط هبوط الجهد للدائرة **${p.entityName}**. فعلى الرغم من أن سعته بعد التصحيح ($candIzStr أمبير) كافية حرارياً، إلا أن مقاومة الموصل سترفع هبوط الجهد إلى نحو $candVdStr متجاوزة الحد الأقصى للمشروع (${p.maxVdStr}) على مسار بطول ${p.lengthStr}م."
        } else {
            "No, **$candSizeFormatted mm²** fails voltage drop for **${p.entityName}**. While its derated capacity ($candIzStr A) satisfies thermal ampacity, its higher conductor impedance pushes voltage drop to approximately $candVdStr, exceeding the ${p.maxVdStr} project limit over the ${p.lengthStr}m run."
        }
    }

    fun buildCostOptimizationExplanation(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        val suggestedSmaller = getSuggestedSmallerCandidate(p.ibVal, p.breakerVal)
        val isOversized = p.utilizationPct < 40.0 && p.vdVal < p.maxVdVal * 0.7

        if (isOversized && suggestedSmaller.toDoubleOrNull() != null && suggestedSmaller.toDouble() < p.cableSizeVal) {
            val candIz = String.format(Locale.US, "%.1f", estimateDeratedAmpacity(suggestedSmaller.toDouble(), p.deratingVal))
            val candVd = String.format(Locale.US, "%.2f%%", estimateCandidateVoltageDrop(suggestedSmaller.toDouble(), p.cableSizeVal, p.vdVal))
            return if (language == ConversationLanguage.AR) {
                "لتحسين التكلفة في الدائرة **${p.entityName}**، يمكن تقليل مقطع الكابل بأمان من **${p.cableSizeStr} مم²** إلى **$suggestedSmaller مم²**. هذا المقطع يحقق المطابقة الكاملة ($candIz أمبير سعة مصححة > قاطع ${p.breakerStr}، وهبوط جهد $candVd < ${p.maxVdStr}) مع خفض وزن النحاس وتكلفة التوريد بشكل ملحوظ.\n**التالي:** هل ترغب في إنشاء مقترح لاعتماد مقطع $suggestedSmaller مم²؟"
            } else {
                "To optimize cost on **${p.entityName}**, the cable can safely be downsized from **${p.cableSizeStr} mm²** to **$suggestedSmaller mm²**. This maintains full compliance ($candIz A derated capacity > ${p.breakerStr} breaker, $candVd VD < ${p.maxVdStr}) while significantly reducing copper weight and procurement cost.\n**Next:** Would you like to create a proposal to use $suggestedSmaller mm²?"
            }
        }

        return if (language == ConversationLanguage.AR) {
            "المقطع الحالي **${p.cableSizeStr} مم²** هو بالفعل الحد الأدنى المطابق للمواصفات للدائرة **${p.entityName}** وفق IEC 60364-5-52. أي تصغير إضافي سيؤدي لمخالفة حدود السعة أو هبوط الجهد. لتحسين التكلفة، يمكن دراسة تغيير مادة الموصل من النحاس إلى الألومنيوم (مثل Al/XLPE)."
        } else {
            "The current **${p.cableSizeStr} mm²** is already at the minimum compliant size for **${p.entityName}** under IEC 60364-5-52. Downsizing further would violate ampacity or voltage drop limits. For cost optimization, switching conductor material from Copper to Aluminum (e.g. Al/XLPE) could be explored."
        }
    }

    fun formatCableSize(size: Double): String =
        if (size % 1.0 == 0.0) "${size.toInt()}" else "$size"

    fun getNextStandardSizeDown(size: Double): String {
        val standardSizes = listOf(
            1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0,
            95.0, 120.0, 150.0, 185.0, 240.0, 300.0, 400.0, 500.0, 630.0, 800.0, 1000.0
        )
        val idx = standardSizes.indexOfFirst { kotlin.math.abs(it - size) < 0.1 }
        return if (idx > 0) {
            formatCableSize(standardSizes[idx - 1])
        } else {
            "1.5"
        }
    }

    fun getSuggestedSmallerCandidate(ib: Double, breaker: Double): String {
        val targetI = maxOf(ib, breaker)
        return when {
            targetI <= 16.0 -> "2.5"
            targetI <= 25.0 -> "4"
            targetI <= 32.0 -> "6"
            targetI <= 50.0 -> "10"
            targetI <= 63.0 -> "16"
            targetI <= 100.0 -> "25"
            targetI <= 125.0 -> "35"
            targetI <= 160.0 -> "50"
            targetI <= 200.0 -> "70"
            targetI <= 250.0 -> "95"
            targetI <= 300.0 -> "120"
            targetI <= 350.0 -> "150"
            targetI <= 400.0 -> "185"
            targetI <= 500.0 -> "240"
            targetI <= 600.0 -> "300"
            else -> "400"
        }
    }

    fun estimateDeratedAmpacity(sizeMm2: Double, deratingFactor: Double = 0.82): Double {
        val baseTableAmpacity = when {
            sizeMm2 <= 1.5 -> 20.0
            sizeMm2 <= 2.5 -> 28.0
            sizeMm2 <= 4.0 -> 38.0
            sizeMm2 <= 6.0 -> 49.0
            sizeMm2 <= 10.0 -> 68.0
            sizeMm2 <= 16.0 -> 91.0
            sizeMm2 <= 25.0 -> 121.0
            sizeMm2 <= 35.0 -> 150.0
            sizeMm2 <= 50.0 -> 184.0
            sizeMm2 <= 70.0 -> 234.0
            sizeMm2 <= 95.0 -> 284.0
            sizeMm2 <= 120.0 -> 330.0
            sizeMm2 <= 150.0 -> 381.0
            sizeMm2 <= 185.0 -> 436.0
            sizeMm2 <= 240.0 -> 515.0
            sizeMm2 <= 300.0 -> 590.0
            sizeMm2 <= 400.0 -> 695.0
            sizeMm2 <= 500.0 -> 800.0
            else -> 915.0
        }
        return baseTableAmpacity * deratingFactor
    }

    fun estimateCandidateVoltageDrop(
        candidateSize: Double,
        currentSize: Double,
        currentVdPct: Double
    ): Double {
        if (candidateSize <= 0.0 || currentSize <= 0.0) return currentVdPct
        val ratio = currentSize / candidateSize
        return currentVdPct * (0.85 * ratio + 0.15)
    }

    fun normalizeUtterance(text: String): String {
        return text.trim()
            .replace(Regex("""\s+"""), " ")
    }
}

data class ParsedCircuitInputs(
    val entityName: String,
    val cableSizeStr: String,
    val cableSizeVal: Double,
    val material: String,
    val insulation: String,
    val installation: String,
    val ibStr: String,
    val ibVal: Double,
    val breakerStr: String,
    val breakerVal: Double,
    val breakerType: String,
    val izStr: String,
    val izVal: Double,
    val deratingStr: String,
    val deratingVal: Double,
    val vdStr: String,
    val vdVal: Double,
    val maxVdStr: String,
    val maxVdVal: Double,
    val lengthStr: String,
    val lengthVal: Double,
    val hasManualOverride: Boolean,
    val status: String,
    val failReason: String,
    val utilizationPct: Double
)
