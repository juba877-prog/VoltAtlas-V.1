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
            val size = candidateSize ?: workingState.knownSlots["size"]?.value
            if (size.isNullOrBlank()) {
                // Never default to a cable size. A proposal without an explicit target size is
                // a blocking input, not something to fill with a plausible value.
                return ConversationTurnDecision(
                    decisionId = decisionId,
                    action = ConversationAction(
                        actionType = ConversationActionType.ASK_BLOCKING_QUESTION,
                        reasonCode = "MISSING_CANDIDATE_SIZE",
                        requiredSlots = listOf("size"),
                        responseMode = ResponseMode.BLOCKING_QUESTION
                    ),
                    updatedState = workingState.copy(primaryIntent = AssistantIntent.REQUEST_APPLY),
                    explanationSummary = if (workingState.userLanguage == ConversationLanguage.AR) {
                        "أي مقطع تريد اعتماده؟ لم يتم تحديد مقاس صريح في طلبك."
                    } else {
                        "Which conductor size would you like to propose? Your request did not specify one."
                    }
                )
            }
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
        } else if (lower.contains("duct")) {
            // Only the values the ampacity catalog can actually compute are written here.
            // Conduit wording (english "conduit" / arabic "مواسير") is deliberately NOT mapped:
            // it would have to become "In Conduit", which the catalog cannot model, so the slot is
            // left unset and the user is asked for a supported installation method instead.
            slots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "Laid in Ducts", null, ProvenanceLabel.USER_PROVIDED, true, revision)
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

    /**
     * Parses authoritative circuit facts out of project context.
     *
     * Returns null when any safety-critical value cannot be sourced from project data. There
     * are deliberately no default values here: a missing ampacity, current, voltage drop,
     * derating factor or length previously fell back to a plausible constant, which is how
     * the AI layer grew a second engineering knowledge base.
     *
     * The only inputs accepted are persisted project values, a parsed authoritative result
     * summary, or values already supplied by the user in conversation slots.
     */
    fun parseEntityInputs(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState
    ): ParsedCircuitInputs? {
        val inputs = projectContext.knownEntityInputs
        val summary = projectContext.validatedResultSummary ?: ""
        val entityName = state.activeEntityName ?: projectContext.activeEntityName ?: "Selected Circuit"

        // 1. Cable size (safety-critical)
        val rawSizeFromInputs = inputs["cableSize"] ?: state.knownSlots["size"]?.value
        val rawSize = rawSizeFromInputs
            ?: Regex("""(\d+(?:\.\d+)?)\s*mm²""").find(summary)?.groupValues?.get(1)
            ?: return null
        val cleanSizeStr = rawSize.replace(" mm²", "").replace(" مم²", "").replace("mm²", "").trim()
        val sizeVal = cleanSizeStr.toDoubleOrNull() ?: return null

        // 2. Material, insulation, installation (descriptive; may be absent)
        val material = inputs["cableMaterial"]
            ?: (if (summary.contains("Al", true) && !summary.contains("Cu", true)) "Al" else null)
        val insulation = inputs["insulation"]
            ?: (if (summary.contains("PVC", true) && !summary.contains("XLPE", true)) "PVC" else null)
        val installation = inputs["installation"] ?: state.knownSlots["installation"]?.value

        // 3. Derated ampacity Iz (safety-critical)
        val izStr = inputs["deratedAmpacityIz"]
            ?: Regex("""Iz\s*=\s*(\d+(?:\.\d+)?)\s*A?""", RegexOption.IGNORE_CASE)
                .find(summary)?.groupValues?.let { "${it[1]} A" }
            ?: return null
        val izVal = izStr.replace(" A", "").replace("A", "").trim().toDoubleOrNull() ?: return null

        // 4. Design current Ib (safety-critical). Never derived from Iz by an assumed ratio.
        val parsedIbFromSummary = Regex("""Ib\s*=\s*(\d+(?:\.\d+)?)\s*A?""", RegexOption.IGNORE_CASE)
            .find(summary)?.groupValues?.get(1)
        val ibStr = inputs["designCurrentIb"]
            ?: parsedIbFromSummary?.let { "$it A" }
            ?: return null
        val ibVal = ibStr.replace(" A", "").replace("A", "").trim().toDoubleOrNull() ?: return null

        // 5. Breaker size & type (safety-critical size; type is descriptive)
        val parsedBreakerFromSummary = Regex("""Breaker\s*=\s*(\d+(?:\.\d+)?)\s*A?""", RegexOption.IGNORE_CASE)
            .find(summary)?.groupValues?.get(1)
        val breakerStr = inputs["breakerSize"]
            ?: parsedBreakerFromSummary?.let { "$it A" }
            ?: return null
        val breakerVal = breakerStr.replace(" A", "").replace("A", "").trim().toDoubleOrNull() ?: return null
        val breakerType = inputs["breakerType"]
            ?: Regex("""\((MCB|MCCB|ACB)\)""", RegexOption.IGNORE_CASE).find(summary)?.groupValues?.get(1)

        // 6. Derating factor (safety-critical)
        val deratingStr = inputs["deratingFactor"]
            ?: Regex("""derating\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
                .find(summary)?.groupValues?.get(1)
            ?: return null
        val deratingVal = deratingStr.toDoubleOrNull() ?: return null

        // 7. Voltage drop and its limit (safety-critical)
        val vdStr = inputs["voltageDropPct"]
            ?: Regex("""VD\s*=\s*(\d+(?:\.\d+)?)\s*%?""", RegexOption.IGNORE_CASE)
                .find(summary)?.groupValues?.let { "${it[1]}%" }
            ?: return null
        val vdVal = vdStr.replace("%", "").trim().toDoubleOrNull() ?: return null

        val maxVdStr = inputs["maxVDpct"]
            ?: Regex("""limit\s*(\d+(?:\.\d+)?)\s*%?""", RegexOption.IGNORE_CASE)
                .find(summary)?.groupValues?.let { "${it[1]}%" }
            ?: return null
        val maxVdVal = maxVdStr.replace("%", "").trim().toDoubleOrNull() ?: return null

        // 8. Length (safety-critical)
        val lengthStr = inputs["length"] ?: inputs["lengthM"] ?: return null
        val lengthVal = lengthStr.toDoubleOrNull() ?: return null

        val hasManualOverride = inputs.containsKey("overrideCableSize") &&
                inputs["overrideCableSize"]?.isNotBlank() == true &&
                !inputs["overrideCableSize"].equals("AUTO", ignoreCase = true)

        val status = inputs["status"] ?: ""
        val failReason = inputs["failReason"] ?: ""
        val utilizationPct = if (ibVal == 0.0) 0.0 else (ibVal / izVal) * 100.0

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

    /**
     * The single response for any explanation that cannot be produced from authoritative
     * circuit facts. Never replaced with an estimate.
     */
    private fun unavailableExplanation(language: ConversationLanguage): String =
        if (language == ConversationLanguage.AR) {
            "النتيجة الهندسية الموثقة غير متاحة. لم يتم العثور على بيانات الدائرة المطلوبة في المشروع؛ " +
                "شغّل الحساب المطابق أولاً للحصول على قيمة موثقة."
        } else {
            "Verified engineering result unavailable. The required circuit data is not present in the " +
                "project; run the matching calculation first to obtain a certified value."
        }

    fun buildCurrentStatusSummary(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state) ?: return unavailableExplanation(language)
        return if (language == ConversationLanguage.AR) {
            "الدائرة **${p.entityName}** محدد لها حالياً مقطع **${p.cableSizeStr} مم² ${p.material ?: "—"}${p.insulation?.let { "/$it" } ?: ""}** (طريقة التمديد: ${p.installation ?: "—"}). تيار التصميم ${p.ibStr} مع قاطع ${p.breakerStr}، وسعة الكابل بعد التصحيح ${p.izStr} مع هبوط جهد ${p.vdStr} (الحد: ${p.maxVdStr})."
        } else {
            "Circuit **${p.entityName}** is currently sized with **${p.cableSizeStr} mm² ${p.material ?: "—"}${p.insulation?.let { "/$it" } ?: ""}** (${p.installation ?: "—"}). Design current is ${p.ibStr} with a ${p.breakerStr} breaker. Derated capacity is ${p.izStr}, and voltage drop is ${p.vdStr} (limit ${p.maxVdStr})."
        }
    }

    fun buildCalculationSummary(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state) ?: return unavailableExplanation(language)
        val composition = "${p.cableSizeStr} mm² ${p.material ?: "—"}${p.insulation?.let { "/$it" } ?: ""}"
        val installationSuffix = p.installation?.let { " ($it)" } ?: ""

        // Facts only. The governing criterion and the compliance verdict are produced by the
        // deterministic engine (calculate_cable_sizing); no smaller-size candidate is suggested
        // here because producing one without evaluating it would be an uncalled calculation.
        return if (language == ConversationLanguage.AR) {
            "الدائرة **${p.entityName}** محددة بمقطع **${p.cableSizeStr} مم² ${p.material ?: "—"}${p.insulation?.let { "/$it" } ?: ""}**$installationSuffix.\n" +
                "تيار التصميم ${p.ibStr}، القاطع ${p.breakerStr}، السعة بعد التصحيح ${p.izStr} (معامل ${p.deratingStr})، هبوط الجهد ${p.vdStr} (الحد ${p.maxVdStr}) على طول ${p.lengthStr} م.\n" +
                "**التالي:** شغّل حساب المقطع للحصول على المعيار الحاكم والتحقق الرسمي، أو قيّم مرشحاً بديلاً."
        } else {
            "Circuit **${p.entityName}** is sized at **$composition**$installationSuffix.\n" +
                "Design current ${p.ibStr}, breaker ${p.breakerStr}, derated capacity ${p.izStr} (factor ${p.deratingStr}), voltage drop ${p.vdStr} (limit ${p.maxVdStr}) over ${p.lengthStr} m.\n" +
                "**Next:** Run cable sizing for the authoritative governing criterion and compliance verdict, or evaluate an alternative candidate."
        }
    }

    fun buildGoverningExplanation(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state) ?: return unavailableExplanation(language)
        // Compliance verdicts, the governing criterion, and alternative candidate sizes are
        // produced by the deterministic engines, never by this policy layer. The +0.05
        // voltage-drop tolerance, the standard-size ladder and the smaller-size suggestion
        // that previously lived here were removed along with them.
        val isFail = p.status.contains("FAIL", ignoreCase = true) ||
            p.izVal < p.ibVal ||
            p.vdVal > p.maxVdVal

        // 1. Engineering Failure / Non-compliant
        if (isFail) {
            val reason = if (p.failReason.isNotBlank()) p.failReason else if (p.izVal < p.ibVal) "Derated ampacity is below design load" else "Voltage drop exceeds limit"
            return if (language == ConversationLanguage.AR) {
                "الدائرة **${p.entityName}** غير مطابقة للمواصفات: $reason. سعة الكابل بعد التصحيح (${p.izStr}) غير كافية لتيار التصميم (${p.ibStr}) أو سعة القاطع (${p.breakerStr})، مع هبوط جهد ${p.vdStr} (الحد ${p.maxVdStr}). يلزم زيادة مقطع الكابل أو إضافة مسارات توازي."
            } else {
                "Circuit **${p.entityName}** fails compliance: $reason. Derated ampacity (${p.izStr}) is insufficient for design current (${p.ibStr}) or device rating (${p.breakerStr}), with voltage drop at ${p.vdStr} (limit ${p.maxVdStr}). Upsizing conductor or adding parallel runs is required."
            }
        }

        // 2. Remaining explanation: report verified facts only. This layer never decides which
        //    criterion governs, never asserts that a smaller size would comply, and never
        //    recommends a specific alternative size. Those are calculation outcomes.
        return if (language == ConversationLanguage.AR) {
            "الدائرة **${p.entityName}** محددة بمقطع **${p.cableSizeStr} مم² ${p.material ?: "—"}${p.insulation?.let { "/$it" } ?: ""}**" +
                (p.hasManualOverride.let { if (it) " (تجاوز يدوي Manual Override)" else "" }) + ".\n" +
                "الحقائق الموثقة: تيار التصميم ${p.ibStr}، القاطع ${p.breakerStr}${p.breakerType?.let { " ($it)" } ?: ""}، " +
                "السعة بعد التصحيح ${p.izStr} (معامل ${p.deratingStr})، هبوط الجهد ${p.vdStr} (الحد ${p.maxVdStr}) " +
                "على طول ${p.lengthStr} م، نسبة التشغيل ${String.format(Locale.US, "%.1f", p.utilizationPct)}%.\n" +
                "**المعيار الحاكم:** يحدده نتيجة الحساب الرسمي.\n" +
                "**التالي:** شغّل حساب المقطع أو قيّم مرشحاً محدداً للحصول على تقييم موثق."
        } else {
            "Circuit **${p.entityName}** is selected at **${p.cableSizeStr} mm² ${p.material ?: "—"}${p.insulation?.let { "/$it" } ?: ""}**" +
                (if (p.hasManualOverride) " (active manual override)" else "") + ".\n" +
                "Verified facts: design current ${p.ibStr}, breaker ${p.breakerStr}${p.breakerType?.let { " ($it)" } ?: ""}, " +
                "derated capacity ${p.izStr} (factor ${p.deratingStr}), voltage drop ${p.vdStr} (limit ${p.maxVdStr}) " +
                "over ${p.lengthStr} m, utilization ${String.format(Locale.US, "%.1f", p.utilizationPct)}%.\n" +
                "**Governing criterion:** determined by the official calculation result, not by this layer.\n" +
                "**Next:** Run cable sizing or evaluate a specific candidate for a certified verdict."
        }
    }

    fun buildCandidateEvaluationExplanation(
        candidateSize: String,
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        val candSizeVal = candidateSize.toDoubleOrNull() ?: return (
            if (language == ConversationLanguage.AR) "مقاس المرشح غير صالح." else "The candidate size is not valid."
            )
        val candSizeFormatted = formatCableSize(candSizeVal)

        // Candidate ampacity and voltage drop are NEVER estimated in this layer. Estimating them
        // required a hardcoded ampacity table plus an invented voltage-drop formula, which is a
        // second engineering implementation. Compliance for a candidate must come from
        // evaluate_cable_candidate / compare_cable_candidates.
        val currentFacts = if (p != null) {
            if (language == ConversationLanguage.AR) {
                "المقطع الحالي ${p.cableSizeStr} مم²، السعة بعد التصحيح ${p.izStr}، تيار التصميم ${p.ibStr}، " +
                    "القاطع ${p.breakerStr}، هبوط الجهد ${p.vdStr} (الحد ${p.maxVdStr})."
            } else {
                "Current size ${p.cableSizeStr} mm², derated capacity ${p.izStr}, design current ${p.ibStr}, " +
                    "breaker ${p.breakerStr}, voltage drop ${p.vdStr} (limit ${p.maxVdStr})."
            }
        } else {
            if (language == ConversationLanguage.AR) "بيانات الدائرة الحالية غير متاحة." else "Current circuit data is unavailable."
        }

        return if (language == ConversationLanguage.AR) {
            "تم استلام المرشح **$candSizeFormatted مم²**. $currentFacts\n" +
                "المطابقة لم تُحسب بعد، لذلك لا يمكن تأكيد أو نفي المطابقة هنا.\n" +
                "**التالي:** شغّل أداة تقييم المرشح للحصول على نتيجة موثقة (السعة بعد التصحيح، هبوط الجهد، والمعيار الحاكم)."
        } else {
            "Candidate **$candSizeFormatted mm²** has been recorded. $currentFacts\n" +
                "Compliance has not been computed yet, so it can be neither confirmed nor ruled out here.\n" +
                "**Next:** Run the candidate evaluation tool for a certified result " +
                "(derated ampacity, voltage drop, and governing criterion)."
        }
    }

    fun buildCostOptimizationExplanation(
        projectContext: BoundedProjectContext,
        state: ConversationTaskState,
        language: ConversationLanguage
    ): String {
        val p = parseEntityInputs(projectContext, state)
        // Cost optimization must not be driven by a utilization heuristic in this layer.
        // Selecting a smaller size requires real catalogue candidates re-evaluated by the
        // authoritative engine against every governing criterion; this layer only routes intent.
        val facts = p?.let { q ->
            if (language == ConversationLanguage.AR) {
                "المقطع الحالي **${q.cableSizeStr} مم²** (نسبة التشغيل ${String.format(Locale.US, "%.1f", q.utilizationPct)}%)."
            } else {
                "Current size **${q.cableSizeStr} mm²** (utilization ${String.format(Locale.US, "%.1f", q.utilizationPct)}%)."
            }
        } ?: if (language == ConversationLanguage.AR) {
            "بيانات الدائرة الحالية غير متاحة."
        } else {
            "Current circuit data is unavailable."
        }

        return if (language == ConversationLanguage.AR) {
            "$facts\n" +
                "تحسين التكلفة يتطلب تقييم مرشحين حقيقيين من الكتالوج عبر محرك الحساب المطابق؛ " +
                "لا يمكن اقتراح تخفيض مقطع دون تقييم موثق لكل شرط حاكم.\n" +
                "**التالي:** شغّل مقارنة المرشحين الآن؟"
        } else {
            "$facts\n" +
                "Cost optimization requires evaluating real catalogue candidates through the authoritative engine; " +
                "a smaller size cannot be suggested without a certified evaluation of every governing condition.\n" +
                "**Next:** Run candidate comparison now?"
        }
    }

    fun formatCableSize(size: Double): String =
        if (size % 1.0 == 0.0) "${size.toInt()}" else "$size"

    // NOTE: getNextStandardSizeDown, getSuggestedSmallerCandidate, estimateDeratedAmpacity and
    // estimateCandidateVoltageDrop were deliberately removed. They were a second, competing
    // engineering knowledge base inside the AI layer: a hardcoded IEC ampacity table, a
    // hardcoded standard-size ladder and an invented voltage-drop formula. Candidate sizing,
    // ampacity and voltage drop come only from the deterministic engines via tool execution.

    fun normalizeUtterance(text: String): String {
        return text.trim()
            .replace(Regex("""\s+"""), " ")
    }
}

/**
 * Authoritative circuit facts resolved from project data.
 *
 * Only descriptive fields are nullable; every safety-critical numeric field is guaranteed
 * non-null because [ConversationPolicyEngine.parseEntityInputs] returns null rather than
 * substituting a default when a safety-critical value cannot be sourced.
 */
data class ParsedCircuitInputs(
    val entityName: String,
    val cableSizeStr: String,
    val cableSizeVal: Double,
    val material: String?,
    val insulation: String?,
    val installation: String?,
    val ibStr: String,
    val ibVal: Double,
    val breakerStr: String,
    val breakerVal: Double,
    val breakerType: String?,
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
