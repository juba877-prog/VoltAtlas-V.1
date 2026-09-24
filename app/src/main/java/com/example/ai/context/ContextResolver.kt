package com.example.ai.context

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.ToolStatus
import com.example.ai.conversation.AssistantIntent
import com.example.ai.conversation.ContradictionRecord
import com.example.ai.conversation.ConversationLanguage
import com.example.ai.conversation.ConversationLifecycleState
import com.example.ai.conversation.ConversationTaskState
import com.example.ai.conversation.EngineeringObjective
import com.example.ai.conversation.EntityBinding
import com.example.ai.conversation.SlotState
import com.example.ai.conversation.SlotValue
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.runtime.ProvenanceLabel
import com.example.ai.runtime.ResponseDetailLevel
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableWhatIfResult
import java.util.Locale
import java.util.UUID

/**
 * Structured, bounded summary of an immutable engineering evidence record.
 * Sourced directly from [EngineeringEvidenceRecord] for model context without dumping raw payloads.
 */
data class BoundedEvidenceSummary(
    val evidenceId: String,
    val toolName: String,
    val domain: CalculationDomain,
    val projectId: String,
    val entityType: String?,
    val entityId: String?,
    val projectRevision: Long?,
    val executionStatus: ToolStatus,
    val freshnessState: String,
    val verifiedSelectedSize: String? = null,
    val verifiedCapacityIz: String? = null,
    val verifiedDesignCurrentIb: String? = null,
    val verifiedVoltageDropPct: String? = null,
    val verifiedDeviceRating: String? = null,
    val governingCriterion: String? = null,
    val overallCompliance: String? = null
) {
    fun toBoundedPromptLine(): String {
        val parts = mutableListOf<String>()
        parts.add("Ref: $evidenceId")
        parts.add("Tool: $toolName")
        parts.add("Domain: ${domain.name}")
        entityId?.let { parts.add("Entity: ${entityType ?: "CIRCUIT"}:$it") }
        projectRevision?.let { parts.add("Rev: $it") }
        parts.add("Status: ${executionStatus.name}")
        parts.add("Freshness: $freshnessState")
        verifiedSelectedSize?.let { parts.add("Conductor: $it") }
        verifiedCapacityIz?.let { parts.add("Iz: $it A") }
        verifiedDesignCurrentIb?.let { parts.add("Ib: $it A") }
        verifiedVoltageDropPct?.let { parts.add("VD: $it%") }
        verifiedDeviceRating?.let { parts.add("Device: $it A") }
        governingCriterion?.takeIf { it.isNotBlank() }?.let { parts.add("Governing: $it") }
        overallCompliance?.let { parts.add("Compliance: $it") }
        return parts.joinToString(" | ")
    }
}

/**
 * Pure, immutable result produced by [ContextResolver].
 */
data class ResolvedConversationState(
    val updatedTaskState: ConversationTaskState,
    val activeEntity: EntityBinding?,
    val activeDomain: CalculationDomain,
    val resolvedSlots: Map<String, SlotValue>,
    val missingBlockingSlots: List<String>,
    val ambiguousSlots: List<String>,
    val detectedContradictions: List<ContradictionRecord>,
    val isAmbiguousEntity: Boolean = false,
    val ambiguousCandidates: List<EntityBinding> = emptyList(),
    val evidenceSummaries: List<BoundedEvidenceSummary> = emptyList(),
    val isStaleEvidenceDetected: Boolean = false
)

/**
 * Authoritative, pure deterministic context and slot resolution engine (Gate 7).
 *
 * Invariants:
 * 1. Pure Function (Correction #4): Never mutates Room, database, cache, or incoming state.
 * 2. Project Authority & Isolation (Correction #8): Switching projects clears incompatible entities,
 *    scenarios, and evidence references. Project A context never leaks into Project B.
 * 3. Entity Scoping (Correction #9): Switching entity clears entity-specific overrides and evidence.
 * 4. Domain Scoping (Correction #3, #10): Uses typed [CalculationDomain]. Cable candidate assumptions
 *    do not contaminate transformer context.
 * 5. Typed What-If Overrides (Correction #2): Scenario values are stored as typed [EngineeringValue]s
 *    in [ConversationTaskState.typedWhatIfOverrides], never mutating project truth.
 * 6. Contradictions Tracked (Correction #11): User inputs conflicting with project truth produce
 *    [ContradictionRecord] without mutating Room.
 * 7. Tool-Specific Missing Inputs (Correction #5): Missing inputs are identified based on the active
 *    engineering operation. No defaults are fabricated (never assume 400V, 230V, 30m, 30°C).
 * 8. Historical Evidence Preserved (Correction #1, #12, #17): Never deletes old evidence on revision
 *    advance. Stale state is labelled via existing freshness logic without destroying audit trails.
 * 9. Bounded Evidence Summaries (Correction #6, #18): Produces [BoundedEvidenceSummary] so models
 *    ground explanations in verified facts rather than raw payloads or conversational guesses.
 * 10. Deterministic Reference Resolution (Correction #13, #14): Indirect references resolve to active
 *     binding; multiple candidates fail closed to AMBIGUOUS_ENTITY without random guessing.
 * 11. Zero Engineering Calculations (Correction #20): Calculates no ampacity, sizing, or voltage drop.
 */
object ContextResolver {

    private val INDIRECT_CIRCUIT_REFERENCES_AR = listOf(
        "الدائرة دي", "هذه الدائرة", "نفس الدائرة", "الدائرة الحالية", "نفس الدايرة", "الدايرة دي"
    )
    private val INDIRECT_CABLE_REFERENCES_AR = listOf(
        "الكابل ده", "هذا الكابل", "نفس الكابل", "الكابل الحالي"
    )
    private val INDIRECT_TRANSFORMER_REFERENCES_AR = listOf(
        "المحول ده", "هذا المحول", "نفس المحول", "المحول الحالي"
    )
    private val INDIRECT_REFERENCES_EN = listOf(
        "this circuit", "the circuit", "same circuit", "current circuit",
        "this cable", "the cable", "same cable", "current cable",
        "this transformer", "the transformer", "same transformer"
    )

    private val SCENARIO_VERBS_AR = listOf("جرب", "افترض", "اعتبر", "لو", "ماذا لو", "قارنها ب")
    private val SCENARIO_VERBS_EN = listOf("try", "what if", "assume", "suppose", "test", "compare with")

    fun resolve(
        userUtterance: String,
        projectContext: BoundedProjectContext,
        priorState: ConversationTaskState?,
        availableEntities: List<EntityBinding> = emptyList(),
        historicalEvidence: List<EngineeringEvidenceRecord> = emptyList()
    ): ResolvedConversationState {
        val trimmed = userUtterance.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val language = detectLanguage(trimmed)

        // 1. Project Switch Check (Correction #8)
        val isProjectSwitch = priorState != null && priorState.projectId != projectContext.projectId
        val effectivePriorState = if (isProjectSwitch) null else priorState

        // 2. Domain Resolution (Correction #3, #10)
        val activeDomain = resolveDomain(lower, effectivePriorState)
        val isDomainSwitch = effectivePriorState != null &&
                effectivePriorState.activeDomain != null &&
                effectivePriorState.activeDomain != activeDomain

        // 3. Entity Resolution & Ambiguity Detection (Correction #9, #13, #14)
        val entityResolution = resolveEntity(
            utterance = trimmed,
            lower = lower,
            projectContext = projectContext,
            priorState = effectivePriorState,
            availableEntities = availableEntities
        )

        val resolvedEntity = entityResolution.resolvedEntity
        val isAmbiguousEntity = entityResolution.isAmbiguous
        val ambiguousCandidates = entityResolution.ambiguousCandidates

        // 4. Entity Switch Check (Correction #9)
        val isEntitySwitch = effectivePriorState != null &&
                resolvedEntity != null &&
                effectivePriorState.activeEntityId != null &&
                (resolvedEntity.entityId != effectivePriorState.activeEntityId ||
                        (effectivePriorState.activeEntityType != null && resolvedEntity.entityType != effectivePriorState.activeEntityType))

        // 5. Revision Advance Check (Correction #1, #12, #17)
        val liveRevision = projectContext.liveRevision
        val priorRevision = effectivePriorState?.baseProjectRevision ?: liveRevision
        val isRevisionAdvanced = liveRevision > priorRevision

        // 6. Slots & Contradictions Resolution (Correction #2, #11)
        val slotResolution = resolveSlotsAndContradictions(
            utterance = trimmed,
            lower = lower,
            projectContext = projectContext,
            priorState = effectivePriorState,
            resolvedEntity = resolvedEntity,
            isEntitySwitch = isEntitySwitch,
            isDomainSwitch = isDomainSwitch,
            isProjectSwitch = isProjectSwitch,
            activeDomain = activeDomain
        )

        // 7. Tool/Intent Specific Missing Inputs (Correction #5)
        val missingInputs = determineMissingBlockingSlots(
            activeDomain = activeDomain,
            lower = lower,
            knownSlots = slotResolution.knownSlots,
            projectContext = projectContext,
            resolvedEntity = resolvedEntity
        )

        // 8. Evidence Continuity & Bounded Summaries (Correction #1, #6, #18)
        val evidenceResolution = resolveEvidenceContinuity(
            projectId = projectContext.projectId,
            liveRevision = liveRevision,
            activeDomain = activeDomain,
            resolvedEntity = resolvedEntity,
            isProjectSwitch = isProjectSwitch,
            isEntitySwitch = isEntitySwitch,
            historicalEvidence = historicalEvidence
        )

        // 9. Intent Classification
        val primaryIntent = resolveIntent(lower, slotResolution.isWhatIfActive, missingInputs.isNotEmpty())

        // 10. Assemble New Immutable ConversationTaskState
        val taskId = effectivePriorState?.taskId ?: ("TASK-" + UUID.randomUUID().toString().take(8))

        val updatedTaskState = ConversationTaskState(
            taskId = taskId,
            projectId = projectContext.projectId,
            baseProjectRevision = liveRevision,
            activeEntityType = resolvedEntity?.entityType ?: projectContext.activeEntityType,
            activeEntityId = resolvedEntity?.entityId ?: projectContext.activeEntityId,
            activeEntityName = resolvedEntity?.entityName ?: projectContext.activeEntityName,
            primaryIntent = primaryIntent,
            engineeringObjective = effectivePriorState?.engineeringObjective ?: EngineeringObjective.VERIFY_COMPLIANCE,
            knownSlots = slotResolution.knownSlots,
            missingBlockingSlots = missingInputs,
            ambiguousSlots = if (isAmbiguousEntity) listOf("activeEntity") else emptyList(),
            detectedContradictions = slotResolution.contradictions,
            isWhatIfBranch = slotResolution.isWhatIfActive,
            whatIfOverrides = slotResolution.typedWhatIfOverrides.mapValues {
                it.value.formattedDisplay ?: it.value.decimalValue?.toString() ?: it.value.textValue ?: ""
            },
            typedWhatIfOverrides = slotResolution.typedWhatIfOverrides,
            activeDomain = activeDomain,
            evidenceReferences = evidenceResolution.evidenceReferences,
            activeProposalId = if (isRevisionAdvanced || isEntitySwitch || isProjectSwitch) null else effectivePriorState?.activeProposalId,
            activeProposalDigest = if (isRevisionAdvanced || isEntitySwitch || isProjectSwitch) null else effectivePriorState?.activeProposalDigest,
            activeProposalSemanticKey = if (isRevisionAdvanced || isEntitySwitch || isProjectSwitch) null else effectivePriorState?.activeProposalSemanticKey,
            responseDetailLevel = effectivePriorState?.responseDetailLevel ?: ResponseDetailLevel.CONCISE,
            userLanguage = language,
            lifecycleState = if (missingInputs.isNotEmpty()) ConversationLifecycleState.WAITING_FOR_USER_INPUT else ConversationLifecycleState.ACTIVE,
            lastDecisionId = effectivePriorState?.lastDecisionId
        )

        return ResolvedConversationState(
            updatedTaskState = updatedTaskState,
            activeEntity = resolvedEntity,
            activeDomain = activeDomain,
            resolvedSlots = slotResolution.knownSlots,
            missingBlockingSlots = missingInputs,
            ambiguousSlots = if (isAmbiguousEntity) listOf("activeEntity") else emptyList(),
            detectedContradictions = slotResolution.contradictions,
            isAmbiguousEntity = isAmbiguousEntity,
            ambiguousCandidates = ambiguousCandidates,
            evidenceSummaries = evidenceResolution.summaries,
            isStaleEvidenceDetected = evidenceResolution.hasStaleEvidence
        )
    }

    private fun resolveDomain(lower: String, priorState: ConversationTaskState?): CalculationDomain {
        val hasTransformer = lower.contains("محول") || lower.contains("transformer") ||
                lower.contains("قدرة المحول") || lower.contains("kva") || lower.contains("محولات")
        val hasShortCircuit = lower.contains("قصر") || lower.contains("short circuit") ||
                lower.contains("fault") || lower.contains("شورت")
        val hasProtection = lower.contains("قاطع") || lower.contains("breaker") ||
                lower.contains("mccb") || lower.contains("mcb") || lower.contains("حماية")
        val hasCable = lower.contains("كابل") || lower.contains("cable") ||
                lower.contains("feeder") || lower.contains("دائرة") || lower.contains("دايرة") ||
                lower.contains("circuit") || lower.contains("مقاس") || lower.contains("توصيل") ||
                lower.contains("سلك") || lower.contains("أمبيرية") || lower.contains("امبيرية") ||
                lower.contains("هبوط الجهد") || lower.contains("voltage drop")

        return when {
            hasTransformer && !hasCable -> CalculationDomain.TRANSFORMER
            hasShortCircuit -> CalculationDomain.SHORT_CIRCUIT
            hasProtection -> CalculationDomain.PROTECTION_ADS
            hasCable -> CalculationDomain.CABLE_SIZING
            else -> priorState?.activeDomain ?: CalculationDomain.CABLE_SIZING
        }
    }

    private data class EntityResolutionResult(
        val resolvedEntity: EntityBinding?,
        val isAmbiguous: Boolean = false,
        val ambiguousCandidates: List<EntityBinding> = emptyList()
    )

    private fun resolveEntity(
        utterance: String,
        lower: String,
        projectContext: BoundedProjectContext,
        priorState: ConversationTaskState?,
        availableEntities: List<EntityBinding>
    ): EntityResolutionResult {
        // A. Explicit mention of entity ID or entity Name
        for (entity in availableEntities) {
            if (utterance.contains(entity.entityId, ignoreCase = true) ||
                (!entity.entityName.isNullOrBlank() && utterance.contains(entity.entityName, ignoreCase = true))
            ) {
                return EntityResolutionResult(entity)
            }
        }

        // A2. Match numeric circuit mention (e.g. "الدائرة 5", "دائرة 5", "Circuit 5", "Feeder 5")
        val numericMatch = Regex("""(?:الدائرة|دائرة|الدايرة|دايرة|circuit|feeder)\s*(?:رقم\s*)?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE).find(utterance)
        if (numericMatch != null) {
            val rawId = numericMatch.groupValues[1]
            val matched = availableEntities.filter {
                it.entityId.equals(rawId, ignoreCase = true) ||
                        (!it.entityName.isNullOrBlank() && it.entityName.endsWith(rawId, ignoreCase = true))
            }
            if (matched.size == 1) {
                return EntityResolutionResult(matched.first())
            } else if (matched.size > 1) {
                return EntityResolutionResult(null, isAmbiguous = true, ambiguousCandidates = matched)
            }
        }

        // A3. Match partial keywords (e.g. "فرعي", "فرعية", "Branch", "Substation", "Motor", "Pump")
        val keywords = listOf("فرعي", "فرعية", "branch", "substation", "motor", "pump", "lighting", "انارة", "إنارة", "مضخة")
        for (kw in keywords) {
            if (lower.contains(kw)) {
                val matches = availableEntities.filter { entity ->
                    entity.entityName?.contains(kw, ignoreCase = true) == true ||
                    entity.entityId.contains(kw, ignoreCase = true) ||
                    (kw in listOf("فرعي", "فرعية") && (entity.entityName?.contains("branch", ignoreCase = true) == true || entity.entityId.contains("branch", ignoreCase = true)))
                }
                if (matches.size > 1) {
                    return EntityResolutionResult(null, isAmbiguous = true, ambiguousCandidates = matches)
                } else if (matches.size == 1 && priorState?.activeEntityId == null && projectContext.activeEntityId == null) {
                    return EntityResolutionResult(matches.first())
                }
            }
        }

        // B. Indirect reference detection ("الدائرة دي", "الكابل ده", "this circuit", etc.)
        val mentionsCircuit = lower.contains("دائرة") || lower.contains("دايرة") ||
                lower.contains("circuit") || lower.contains("feeder")
        val mentionsTransformer = lower.contains("محول") || lower.contains("transformer")
        val isIndirect = INDIRECT_CIRCUIT_REFERENCES_AR.any { utterance.contains(it) } ||
                INDIRECT_CABLE_REFERENCES_AR.any { utterance.contains(it) } ||
                INDIRECT_TRANSFORMER_REFERENCES_AR.any { utterance.contains(it) } ||
                INDIRECT_REFERENCES_EN.any { lower.contains(it) } ||
                ((mentionsCircuit || mentionsTransformer) && priorState?.activeEntityId == null && projectContext.activeEntityId == null)

        if (isIndirect) {
            // 1. Prior bound entity (Continuity)
            if (priorState?.activeEntityId != null) {
                return EntityResolutionResult(
                    EntityBinding(
                        entityType = priorState.activeEntityType ?: "CIRCUIT",
                        entityId = priorState.activeEntityId,
                        entityName = priorState.activeEntityName
                    )
                )
            }

            // 2. Active entity in project context
            if (projectContext.activeEntityId != null) {
                return EntityResolutionResult(
                    EntityBinding(
                        entityType = projectContext.activeEntityType ?: "CIRCUIT",
                        entityId = projectContext.activeEntityId,
                        entityName = projectContext.activeEntityName
                    )
                )
            }

            // 3. No active entity binding exists: check candidates
            if (availableEntities.size == 1) {
                return EntityResolutionResult(availableEntities.first())
            } else if (availableEntities.size > 1) {
                // Ambiguous! Fail closed, do NOT guess randomly (Correction #13, #14)
                return EntityResolutionResult(
                    resolvedEntity = null,
                    isAmbiguous = true,
                    ambiguousCandidates = availableEntities
                )
            }
        }

        // C. Fall back to prior active entity or project context active entity
        if (priorState?.activeEntityId != null) {
            return EntityResolutionResult(
                EntityBinding(
                    entityType = priorState.activeEntityType ?: "CIRCUIT",
                    entityId = priorState.activeEntityId,
                    entityName = priorState.activeEntityName
                )
            )
        }

        if (projectContext.activeEntityId != null) {
            return EntityResolutionResult(
                EntityBinding(
                    entityType = projectContext.activeEntityType ?: "CIRCUIT",
                    entityId = projectContext.activeEntityId,
                    entityName = projectContext.activeEntityName
                )
            )
        }

        return EntityResolutionResult(null)
    }

    private data class SlotResolutionResult(
        val knownSlots: Map<String, SlotValue>,
        val typedWhatIfOverrides: Map<String, EngineeringValue>,
        val contradictions: List<ContradictionRecord>,
        val isWhatIfActive: Boolean
    )

    private fun resolveSlotsAndContradictions(
        utterance: String,
        lower: String,
        projectContext: BoundedProjectContext,
        priorState: ConversationTaskState?,
        resolvedEntity: EntityBinding?,
        isEntitySwitch: Boolean,
        isDomainSwitch: Boolean,
        isProjectSwitch: Boolean,
        activeDomain: CalculationDomain
    ): SlotResolutionResult {
        val workingSlots = mutableMapOf<String, SlotValue>()
        val workingWhatIfOverrides = mutableMapOf<String, EngineeringValue>()
        val detectedContradictions = mutableListOf<ContradictionRecord>()

        // 1. Inherit from prior state if not switched
        if (!isProjectSwitch && !isEntitySwitch && !isDomainSwitch && priorState != null) {
            workingSlots.putAll(priorState.knownSlots)
            workingWhatIfOverrides.putAll(priorState.typedWhatIfOverrides)
        }

        // 2. Populate authoritative baseline from project context
        val liveRevision = projectContext.liveRevision
        projectContext.knownEntityInputs.forEach { (key, strVal) ->
            val existing = workingSlots[key]
            if (existing == null || existing.provenance == ProvenanceLabel.KNOWN_FROM_PROJECT) {
                workingSlots[key] = SlotValue(
                    name = key,
                    state = SlotState.KNOWN,
                    value = strVal,
                    provenance = ProvenanceLabel.KNOWN_FROM_PROJECT,
                    isSafetyCritical = isSafetyCriticalSlot(key),
                    lastUpdatedRevision = liveRevision,
                    typedValue = parseToEngineeringValue(key, strVal)
                )
            }
        }

        // 3. Extract user-stated parameters
        val isWhatIf = SCENARIO_VERBS_AR.any { utterance.contains(it) } ||
                SCENARIO_VERBS_EN.any { lower.contains(it) } ||
                (priorState?.isWhatIfBranch == true && !isEntitySwitch && !isDomainSwitch && !isProjectSwitch)

        // 3a. Candidate size (e.g. 240 mm², 300, etc.)
        val candidateSizeMag = extractCandidateSize(lower)
        if (candidateSizeMag != null) {
            val typedSize = EngineeringValue.ofDecimal(candidateSizeMag, EngineeringUnit.SQUARE_MILLIMETER)
            if (isWhatIf) {
                workingWhatIfOverrides["targetSize"] = typedSize
                workingWhatIfOverrides["cableSize"] = typedSize
            }
            workingSlots["targetSize"] = SlotValue(
                name = "targetSize",
                state = SlotState.USER_PROVIDED,
                value = candidateSizeMag.toString(),
                unit = "mm²",
                provenance = ProvenanceLabel.USER_PROVIDED,
                lastUpdatedRevision = liveRevision,
                typedValue = typedSize
            )
        }

        // 3b. Length extraction (e.g. "80 متر", "80m", "120 m")
        val lengthMag = extractLength(utterance, lower)
        if (lengthMag != null) {
            val typedLength = EngineeringValue.ofDecimal(lengthMag, EngineeringUnit.METER)
            val projectLengthStr = projectContext.knownEntityInputs["lengthM"]
                ?: projectContext.knownEntityInputs["length_m"]
            val projectLength = projectLengthStr?.toDoubleOrNull()

            if (projectLength != null && Math.abs(projectLength - lengthMag) > 0.01) {
                // Contradiction detected! (Correction #11)
                val typedProj = EngineeringValue.ofDecimal(projectLength, EngineeringUnit.METER)
                detectedContradictions.add(
                    ContradictionRecord(
                        fieldName = "lengthM",
                        projectValue = projectLengthStr,
                        userValue = lengthMag.toString(),
                        projectSource = ProvenanceLabel.KNOWN_FROM_PROJECT,
                        userSource = ProvenanceLabel.USER_PROVIDED,
                        typedProjectValue = typedProj,
                        typedUserValue = typedLength
                    )
                )
                if (isWhatIf) {
                    workingWhatIfOverrides["lengthM"] = typedLength
                }
                workingSlots["lengthM"] = SlotValue(
                    name = "lengthM",
                    state = SlotState.CONTRADICTORY,
                    value = lengthMag.toString(),
                    unit = "m",
                    provenance = ProvenanceLabel.USER_PROVIDED,
                    lastUpdatedRevision = liveRevision,
                    typedValue = typedLength
                )
            } else {
                if (isWhatIf) {
                    workingWhatIfOverrides["lengthM"] = typedLength
                }
                workingSlots["lengthM"] = SlotValue(
                    name = "lengthM",
                    state = SlotState.USER_PROVIDED,
                    value = lengthMag.toString(),
                    unit = "m",
                    provenance = ProvenanceLabel.USER_PROVIDED,
                    lastUpdatedRevision = liveRevision,
                    typedValue = typedLength
                )
            }
        }

        // 3c. Voltage extraction (e.g. "230V", "400V", "230 فولت")
        val voltageMag = extractVoltage(utterance, lower)
        if (voltageMag != null) {
            val typedVoltage = EngineeringValue.ofDecimal(voltageMag, EngineeringUnit.VOLT)
            val projectVoltageStr = projectContext.knownEntityInputs["voltage"]
                ?: projectContext.knownEntityInputs["voltageV"]
            val projectVoltage = projectVoltageStr?.toDoubleOrNull()

            if (projectVoltage != null && Math.abs(projectVoltage - voltageMag) > 0.01) {
                // Contradiction detected!
                val typedProj = EngineeringValue.ofDecimal(projectVoltage, EngineeringUnit.VOLT)
                detectedContradictions.add(
                    ContradictionRecord(
                        fieldName = "voltage",
                        projectValue = projectVoltageStr,
                        userValue = voltageMag.toString(),
                        projectSource = ProvenanceLabel.KNOWN_FROM_PROJECT,
                        userSource = ProvenanceLabel.USER_PROVIDED,
                        typedProjectValue = typedProj,
                        typedUserValue = typedVoltage
                    )
                )
                if (isWhatIf) {
                    workingWhatIfOverrides["voltage"] = typedVoltage
                }
                workingSlots["voltage"] = SlotValue(
                    name = "voltage",
                    state = SlotState.CONTRADICTORY,
                    value = voltageMag.toString(),
                    unit = "V",
                    provenance = ProvenanceLabel.USER_PROVIDED,
                    lastUpdatedRevision = liveRevision,
                    typedValue = typedVoltage
                )
            } else {
                if (isWhatIf) {
                    workingWhatIfOverrides["voltage"] = typedVoltage
                }
                workingSlots["voltage"] = SlotValue(
                    name = "voltage",
                    state = SlotState.USER_PROVIDED,
                    value = voltageMag.toString(),
                    unit = "V",
                    provenance = ProvenanceLabel.USER_PROVIDED,
                    lastUpdatedRevision = liveRevision,
                    typedValue = typedVoltage
                )
            }
        }

        // 3d. Material extraction
        if (lower.contains("cu") || lower.contains("copper") || lower.contains("نحاس")) {
            val typedMat = EngineeringValue.ofText("Cu")
            if (isWhatIf) workingWhatIfOverrides["conductorMaterial"] = typedMat
            workingSlots["conductorMaterial"] = SlotValue("conductorMaterial", SlotState.USER_PROVIDED, "Cu", null, ProvenanceLabel.USER_PROVIDED, true, liveRevision, typedMat)
        } else if (lower.contains("al") || lower.contains("aluminum") || lower.contains("ألومنيوم")) {
            val typedMat = EngineeringValue.ofText("Al")
            if (isWhatIf) workingWhatIfOverrides["conductorMaterial"] = typedMat
            workingSlots["conductorMaterial"] = SlotValue("conductorMaterial", SlotState.USER_PROVIDED, "Al", null, ProvenanceLabel.USER_PROVIDED, true, liveRevision, typedMat)
        }

        // 3e. Installation extraction
        if (lower.contains("tray") || lower.contains("تراي") || lower.contains("حامل كابلات")) {
            val typedInst = EngineeringValue.ofText("Perforated Cable Tray")
            if (isWhatIf) workingWhatIfOverrides["installation"] = typedInst
            workingSlots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "Perforated Cable Tray", null, ProvenanceLabel.USER_PROVIDED, true, liveRevision, typedInst)
        } else if (lower.contains("conduit") || lower.contains("مواسير") || lower.contains("duct")) {
            val typedInst = EngineeringValue.ofText("In Conduit")
            if (isWhatIf) workingWhatIfOverrides["installation"] = typedInst
            workingSlots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "In Conduit", null, ProvenanceLabel.USER_PROVIDED, true, liveRevision, typedInst)
        } else if (lower.contains("direct buried") || lower.contains("مدفون")) {
            val typedInst = EngineeringValue.ofText("Direct Buried")
            if (isWhatIf) workingWhatIfOverrides["installation"] = typedInst
            workingSlots["installation"] = SlotValue("installation", SlotState.USER_PROVIDED, "Direct Buried", null, ProvenanceLabel.USER_PROVIDED, true, liveRevision, typedInst)
        }

        return SlotResolutionResult(
            knownSlots = workingSlots,
            typedWhatIfOverrides = workingWhatIfOverrides,
            contradictions = detectedContradictions,
            isWhatIfActive = isWhatIf
        )
    }

    private fun determineMissingBlockingSlots(
        activeDomain: CalculationDomain,
        lower: String,
        knownSlots: Map<String, SlotValue>,
        projectContext: BoundedProjectContext,
        resolvedEntity: EntityBinding?
    ): List<String> {
        val missing = mutableListOf<String>()

        // Intent check: is the user asking to calculate / size?
        val isCalculationIntent = lower.contains("احسب") || lower.contains("size") ||
                lower.contains("calculate") || lower.contains("راجع") || lower.contains("verify") ||
                lower.contains("اختر") || lower.contains("جرب")

        if (activeDomain == CalculationDomain.CABLE_SIZING && isCalculationIntent) {
            // Voltage check (Correction #5, #11)
            val hasVoltage = knownSlots.containsKey("voltage") ||
                    knownSlots.containsKey("voltageV") ||
                    projectContext.knownEntityInputs.containsKey("voltage") ||
                    projectContext.knownEntityInputs.containsKey("voltageV")
            if (!hasVoltage) {
                missing.add("voltage")
            }

            // Installation method check
            val hasInstallation = knownSlots.containsKey("installation") ||
                    knownSlots.containsKey("installationMethod") ||
                    projectContext.knownEntityInputs.containsKey("installation")
            if (!hasInstallation) {
                missing.add("installationMethod")
            }
        }

        return missing
    }

    private data class EvidenceResolutionResult(
        val summaries: List<BoundedEvidenceSummary>,
        val evidenceReferences: List<String>,
        val hasStaleEvidence: Boolean
    )

    private fun resolveEvidenceContinuity(
        projectId: String,
        liveRevision: Long,
        activeDomain: CalculationDomain,
        resolvedEntity: EntityBinding?,
        isProjectSwitch: Boolean,
        isEntitySwitch: Boolean,
        historicalEvidence: List<EngineeringEvidenceRecord>
    ): EvidenceResolutionResult {
        if (isProjectSwitch || historicalEvidence.isEmpty()) {
            return EvidenceResolutionResult(emptyList(), emptyList(), false)
        }

        var hasStale = false
        val domainName = activeDomain.name
        val summaries = mutableListOf<BoundedEvidenceSummary>()
        val references = mutableListOf<String>()

        for (record in historicalEvidence) {
            // Project check: Project A evidence never leaks into Project B
            if (record.projectId != projectId) continue

            // Domain check: Domain scoping prevents cable from contaminating transformer
            if (!record.domain.equals(domainName, ignoreCase = true)) continue

            // Entity check: Entity scoping prevents Circuit 1 evidence from being used for Circuit 2
            if (resolvedEntity != null && record.entityId != null && record.entityId != resolvedEntity.entityId) {
                continue
            }

            // Freshness check: Never delete old evidence, but mark stale when revision advanced
            val isRevisionStale = record.projectRevision != null && record.projectRevision < liveRevision
            val isExplicitlyStale = record.freshnessState.equals("STALE", ignoreCase = true) ||
                    record.executionStatus == ToolStatus.STALE
            val isStale = isRevisionStale || isExplicitlyStale

            if (isStale) {
                hasStale = true
            }

            val freshnessLabel = when {
                isRevisionStale -> "STALE_REVISION"
                isExplicitlyStale -> "STALE"
                else -> record.freshnessState
            }

            val summary = extractEvidenceSummary(record, isStale, freshnessLabel)
            summaries.add(summary)
            references.add(summary.toBoundedPromptLine())
        }

        return EvidenceResolutionResult(
            summaries = summaries,
            evidenceReferences = references,
            hasStaleEvidence = hasStale
        )
    }

    private fun extractEvidenceSummary(
        record: EngineeringEvidenceRecord,
        isStale: Boolean,
        freshnessLabel: String
    ): BoundedEvidenceSummary {
        val domain = CalculationDomain.values().firstOrNull {
            it.name.equals(record.domain, ignoreCase = true)
        } ?: CalculationDomain.CABLE_SIZING

        var conductor: String? = null
        var iz: String? = null
        var ib: String? = null
        var vd: String? = null
        var device: String? = null
        var governing: String? = null
        var compliance: String? = null

        when (val res = record.result) {
            is CableSizingExecutionResult -> {
                conductor = res.details.cableSize_mm2
                iz = res.details.capacity_derated_Iz_A.toString()
                ib = res.details.Ib_A.toString()
                vd = res.details.vdPct.toString()
                device = res.details.deviceSize_A.toString()
                governing = res.governingReason
                compliance = res.overallStatus.name
            }
            is CableCandidateEvaluationResult -> {
                conductor = "${res.candidateSizeMm2} mm²"
                iz = res.details.capacity_derated_Iz_A.toString()
                vd = res.details.vdPct.toString()
                compliance = res.overallStatus.name
            }
            is CableWhatIfResult -> {
                conductor = "${res.scenarioSize} mm² (Scenario)"
                iz = res.scenarioDetails.capacity_derated_Iz_A.toString()
                ib = res.scenarioDetails.Ib_A.toString()
                vd = res.scenarioDetails.vdPct.toString()
                device = res.scenarioDetails.deviceSize_A.toString()
                governing = res.scenarioGoverningCriterion.name
                compliance = res.scenarioStatus.name
            }
            is CableCandidatesComparisonResult -> {
                conductor = "Candidates: " + res.candidateResults.joinToString(", ") { it.sizeFormatted }
                governing = res.recommendedCandidateId ?: res.noRecommendationReason
                compliance = if (res.passingCount > 0) "PASS" else "FAIL"
            }
        }

        return BoundedEvidenceSummary(
            evidenceId = record.evidenceId,
            toolName = record.toolName,
            domain = domain,
            projectId = record.projectId,
            entityType = record.entityType,
            entityId = record.entityId,
            projectRevision = record.projectRevision,
            executionStatus = if (isStale) ToolStatus.STALE else record.executionStatus,
            freshnessState = freshnessLabel,
            verifiedSelectedSize = conductor,
            verifiedCapacityIz = iz,
            verifiedDesignCurrentIb = ib,
            verifiedVoltageDropPct = vd,
            verifiedDeviceRating = device,
            governingCriterion = governing,
            overallCompliance = compliance
        )
    }

    private fun resolveIntent(lower: String, isWhatIf: Boolean, hasMissingSlots: Boolean): AssistantIntent {
        val isDiagnose = lower.contains("تشخيص") || lower.contains("diagnos") ||
                lower.contains("فشل") || lower.contains("fail") || lower.contains("سقط") || lower.contains("راسب")
        val isAdoptOrPropose = lower.contains("اعتمد") || lower.contains("adopt") ||
                lower.contains("طبق") || lower.contains("propose")

        return when {
            isAdoptOrPropose -> AssistantIntent.CREATE_PROPOSAL
            isDiagnose -> AssistantIntent.DIAGNOSE
            lower.contains("قارن") || lower.contains("compare") -> AssistantIntent.COMPARE
            lower.contains("ليه") || lower.contains("why") || lower.contains("سبب") -> AssistantIntent.EXPLAIN_RESULT
            isWhatIf -> AssistantIntent.WHAT_IF
            hasMissingSlots -> AssistantIntent.CALCULATE
            lower.contains("احسب") || lower.contains("calculate") || lower.contains("size") -> AssistantIntent.CALCULATE
            lower.contains("راجع") || lower.contains("verify") || lower.contains("check") -> AssistantIntent.CHECK_STATUS
            else -> AssistantIntent.NAVIGATE
        }
    }

    fun extractCandidateSize(lower: String): Double? {
        val regex = Regex("""\b(1\.5|2\.5|4(?:\.0)?|6(?:\.0)?|10(?:\.0)?|16(?:\.0)?|25(?:\.0)?|35(?:\.0)?|50(?:\.0)?|70(?:\.0)?|95(?:\.0)?|120(?:\.0)?|150(?:\.0)?|185(?:\.0)?|240(?:\.0)?|300(?:\.0)?|400(?:\.0)?|500(?:\.0)?|630(?:\.0)?|800(?:\.0)?|1000(?:\.0)?)\s*(?:mm2|mm²|sqmm|مم2|مم²)?(?=[\s,؛!.]|$)""")
        val match = regex.find(lower) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun extractLength(utterance: String, lower: String): Double? {
        val regex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:متر|أمتار|meters|meter|m|م)(?:[\s,؛!.]|$)""", RegexOption.IGNORE_CASE)
        val match = regex.find(utterance) ?: regex.find(lower) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun extractVoltage(utterance: String, lower: String): Double? {
        val regex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:فولت|volts|volt|v)(?:[\s,؛!.]|$)""", RegexOption.IGNORE_CASE)
        val match = regex.find(utterance) ?: regex.find(lower) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    private fun parseToEngineeringValue(key: String, strVal: String): EngineeringValue {
        val num = strVal.toDoubleOrNull()
        return when (key.lowercase(Locale.ROOT)) {
            "voltage", "voltagev" -> num?.let { EngineeringValue.ofDecimal(it, EngineeringUnit.VOLT) } ?: EngineeringValue.ofText(strVal)
            "lengthm", "length_m" -> num?.let { EngineeringValue.ofDecimal(it, EngineeringUnit.METER) } ?: EngineeringValue.ofText(strVal)
            "loadkva" -> num?.let { EngineeringValue.ofDecimal(it, EngineeringUnit.KILOVOLT_AMPERE) } ?: EngineeringValue.ofText(strVal)
            "maxvdpct" -> num?.let { EngineeringValue.ofDecimal(it, EngineeringUnit.PERCENT) } ?: EngineeringValue.ofText(strVal)
            else -> num?.let { EngineeringValue.ofDecimal(it) } ?: EngineeringValue.ofText(strVal)
        }
    }

    private fun isSafetyCriticalSlot(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.contains("voltage") || lower.contains("installation") ||
                lower.contains("material") || lower.contains("conductormaterial") ||
                lower.contains("temp") || lower.contains("load")
    }

    private fun detectLanguage(text: String): ConversationLanguage {
        val arabicChars = text.count { it in '\u0600'..'\u06FF' }
        return if (arabicChars > 0 && arabicChars >= text.length / 4) {
            ConversationLanguage.AR
        } else {
            ConversationLanguage.EN
        }
    }
}
