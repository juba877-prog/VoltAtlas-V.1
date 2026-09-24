package com.example.ai.grounding

import com.example.ai.contracts.EngineeringUnit
import com.example.ai.contracts.EngineeringValue
import com.example.ai.contracts.EngineeringValueKind
import com.example.ai.contracts.ToolStatus
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.runtime.BoundedProjectContext
import com.example.ai.tools.cable.CableCandidateEvaluationResult
import com.example.ai.tools.cable.CableCandidatesComparisonResult
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableWhatIfResult
import com.example.ai.tools.cable.CheckStatus
import java.util.Locale
import kotlin.math.abs

/**
 * Root cause classification for grounding validation rejections (Gate 6).
 *
 * Invariant:
 * - Grounding failures are strictly segregated from deterministic tool execution failures (Correction #2).
 */
enum class GroundingFailureReason {
    NO_EVIDENCE,
    WRONG_PROJECT,
    WRONG_ENTITY,
    STALE_REVISION,
    WRONG_DOMAIN,
    NUMERIC_MISMATCH,
    STATUS_MISMATCH
}

/**
 * Closed outcome of a grounding validation check.
 */
sealed interface GroundingValidationResult {
    data object Valid : GroundingValidationResult

    data class Rejected(
        val reason: GroundingFailureReason,
        val violatingClaim: EngineeringClaim? = null,
        val details: String
    ) : GroundingValidationResult

    val isValid: Boolean get() = this is Valid
}

/**
 * Pure, deterministic validator verifying that engineering claims made in assistant responses
 * are strictly grounded in authoritative [EngineeringEvidenceRecord]s (Gate 6).
 *
 * Invariants:
 * 1. NO EVIDENCE = NO CLAIM: Any engineering assertion without backing evidence is rejected.
 * 2. DETERMINISTIC TRUTH OVER NARRATIVE: Model explanation cannot alter, redefine, or override
 *    authoritative calculations.
 * 3. TYPED COMPARISON: Authoritative quantities are compared as typed values, not parsed strings (Correction #1).
 * 4. SEPARATE STATUS: Tool execution status is distinct from engineering compliance status (Correction #5).
 * 5. REGEX IS NOT FINAL AUTHORITY: Text pre-filtering detects suspicious narrative claims, while
 *    validation evaluates against typed evidence (Correction #3).
 * 6. ZERO FORMULAS: No engineering formulas or default values exist in this layer (Correction #14).
 */
object GroundedResponseValidator {

    private const val NUMERIC_EPSILON = 0.05

    // Patterns identifying non-engineering numbers that should never be flagged as engineering claims
    private val DATE_PATTERN = Regex("""\b\d{4}-\d{2}-\d{2}\b""")
    private val TURN_PATTERN = Regex("""\b(turn|iteration|step|card|attempt)\s*#?\d+\b""", RegexOption.IGNORE_CASE)
    private val IDENTIFIER_PATTERN = Regex("""\b(PROJ|CIRCUIT|TASK|DEC|CARD|EVD|REQ)-[A-Za-z0-9_-]+\b""")
    private val STANDARD_SPEC_PATTERN = Regex("""\b(BS|IEC|NEC|IEEE|ISO|EN)\s*\d+\b""", RegexOption.IGNORE_CASE)

    // Patterns detecting engineering claims with physical units
    private val CABLE_SIZE_PATTERN = Regex(
        """\b(\d+(\.\d+)?)\s*(mm²|mm2|مم²|مم2)""",
        RegexOption.IGNORE_CASE
    )
    private val CURRENT_PATTERN = Regex(
        """\b(\d+(\.\d+)?)\s*(A|أمبير|امبير)\b"""
    )
    private val VOLTAGE_PATTERN = Regex(
        """\b(\d+(\.\d+)?)\s*(V|فولت)\b"""
    )
    private val PERCENT_PATTERN = Regex(
        """\b(\d+(\.\d+)?)\s*%"""
    )

    private val RECOMMENDATION_VERBS_EN = listOf(
        "use", "choose", "select", "recommend", "recommended",
        "size", "sized", "install", "installed", "upgrade", "downgrade",
        "require", "requires", "rated", "set to"
    )
    private val RECOMMENDATION_VERBS_AR = listOf(
        "يستخدم", "استخدم", "اختار", "اختر", "حجم",
        "ركب", "تركيب", "نوصي", "يوصى", "يحتاج", "يتطلب", "سعة", "ضع"
    )

    private val PASS_STATUS_WORDS = listOf(
        "pass", "passed", "passes", "compliant", "conforms", "acceptable", "satisfied", "ناجح", "مطابق"
    )
    private val FAIL_STATUS_WORDS = listOf(
        "fail", "failed", "fails", "violated", "non-compliant", "exceeded", "راسب", "غير مطابق"
    )

    /**
     * Complete response validation verifying both structured claims and free-text narrative against evidence.
     */
    fun validateResponse(
        text: String,
        structuredClaims: List<EngineeringClaim> = emptyList(),
        evidenceList: List<EngineeringEvidenceRecord>,
        context: BoundedProjectContext
    ): GroundingValidationResult {
        // 1. Validate all explicit structured claims
        for (claim in structuredClaims) {
            val claimResult = validateStructuredClaim(claim, evidenceList, context)
            if (claimResult is GroundingValidationResult.Rejected) {
                return claimResult
            }
        }

        // 2. Validate narrative text
        return validateNarrative(text, evidenceList, context)
    }

    /**
     * Validates a single strongly typed [EngineeringClaim] against the evidence record ledger.
     */
    fun validateStructuredClaim(
        claim: EngineeringClaim,
        evidenceList: List<EngineeringEvidenceRecord>,
        context: BoundedProjectContext
    ): GroundingValidationResult {
        if (evidenceList.isEmpty()) {
            return GroundingValidationResult.Rejected(
                reason = GroundingFailureReason.NO_EVIDENCE,
                violatingClaim = claim,
                details = "No engineering evidence exists to support claim '${claim.name}'"
            )
        }

        // 1. Domain Relevance Check
        val domainMatches = evidenceList.filter { it.isDomainRelevant(claim.domain) }
        if (domainMatches.isEmpty()) {
            return GroundingValidationResult.Rejected(
                reason = GroundingFailureReason.WRONG_DOMAIN,
                violatingClaim = claim,
                details = "Evidence domain does not match claim domain '${claim.domain}'"
            )
        }

        // 2. Project Boundary Check
        val targetProject = claim.projectId ?: context.projectId
        val projectMatches = domainMatches.filter { it.projectId == targetProject }
        if (projectMatches.isEmpty()) {
            return GroundingValidationResult.Rejected(
                reason = GroundingFailureReason.WRONG_PROJECT,
                violatingClaim = claim,
                details = "Evidence project does not match target project '$targetProject'"
            )
        }

        // 3. Entity Boundary Check
        val targetEntity = claim.entityId ?: context.activeEntityId
        val entityMatches = if (targetEntity != null) {
            projectMatches.filter { it.entityId == null || it.entityId == targetEntity }
        } else {
            projectMatches
        }
        if (entityMatches.isEmpty()) {
            return GroundingValidationResult.Rejected(
                reason = GroundingFailureReason.WRONG_ENTITY,
                violatingClaim = claim,
                details = "Evidence entity does not match target entity '$targetEntity'"
            )
        }

        // 4. Freshness and Revision Check
        val targetRevision = context.liveRevision
        val freshMatches = entityMatches.filter { ev ->
            val revMatch = (ev.projectRevision == null || ev.projectRevision == targetRevision)
            val notStale = !ev.freshnessState.equals("STALE", ignoreCase = true) && ev.executionStatus != ToolStatus.STALE
            revMatch && notStale
        }
        if (freshMatches.isEmpty()) {
            return GroundingValidationResult.Rejected(
                reason = GroundingFailureReason.STALE_REVISION,
                violatingClaim = claim,
                details = "Evidence revision is stale compared to live revision '$targetRevision'"
            )
        }

        // 5. Numeric Value Consistency Check (Typed comparison, Correction #1)
        if (claim.claimType == EngineeringClaimType.ENGINEERING_VALUE && claim.typedValue != null) {
            val matchingValue = findEvidenceValue(freshMatches, claim.name)
            if (matchingValue != null) {
                val mismatch = checkNumericMismatch(claim.typedValue, matchingValue)
                if (mismatch != null) {
                    return GroundingValidationResult.Rejected(
                        reason = GroundingFailureReason.NUMERIC_MISMATCH,
                        violatingClaim = claim,
                        details = mismatch
                    )
                }
            } else {
                // If claim specifies a standard cable size or parameter that contradicts all candidate values
                val anyCandidateMismatch = checkAgainstCandidateDetails(freshMatches, claim)
                if (anyCandidateMismatch != null) {
                    return GroundingValidationResult.Rejected(
                        reason = GroundingFailureReason.NUMERIC_MISMATCH,
                        violatingClaim = claim,
                        details = anyCandidateMismatch
                    )
                }
            }
        }

        // 6. Status Consistency Check (PASS vs FAIL, Correction #5)
        if (claim.claimType == EngineeringClaimType.COMPLIANCE_STATUS && claim.statusValue != null) {
            val statusMismatch = checkStatusMismatch(claim.statusValue, freshMatches)
            if (statusMismatch != null) {
                return GroundingValidationResult.Rejected(
                    reason = GroundingFailureReason.STATUS_MISMATCH,
                    violatingClaim = claim,
                    details = statusMismatch
                )
            }
        }

        return GroundingValidationResult.Valid
    }

    /**
     * Validates narrative text to ensure any asserted engineering quantities or verdicts
     * match verified evidence without hallucination or numerical drift.
     */
    fun validateNarrative(
        rawText: String,
        evidenceList: List<EngineeringEvidenceRecord>,
        context: BoundedProjectContext
    ): GroundingValidationResult {
        if (rawText.isBlank()) return GroundingValidationResult.Valid

        // Sanitize out non-engineering numbers before inspecting
        var sanitized = rawText
        sanitized = DATE_PATTERN.replace(sanitized, " ")
        sanitized = TURN_PATTERN.replace(sanitized, " ")
        sanitized = IDENTIFIER_PATTERN.replace(sanitized, " ")
        sanitized = STANDARD_SPEC_PATTERN.replace(sanitized, " ")

        val lower = sanitized.lowercase(Locale.ROOT)

        // Check for cable size claims
        val cableSizeMatch = CABLE_SIZE_PATTERN.find(sanitized)
        val hasRecommendationEn = RECOMMENDATION_VERBS_EN.any { verb ->
            Regex("""\b$verb\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        }
        val hasRecommendationAr = RECOMMENDATION_VERBS_AR.any { verb ->
            rawText.contains(verb)
        }
        val isRecommendation = hasRecommendationEn || hasRecommendationAr

        // If narrative asserts a specific cable size recommendation
        if (cableSizeMatch != null && isRecommendation) {
            val claimedMagnitude = cableSizeMatch.groupValues[1].toDoubleOrNull()
            if (claimedMagnitude != null) {
                val claim = EngineeringClaim(
                    claimType = EngineeringClaimType.ENGINEERING_VALUE,
                    name = "cableSize",
                    typedValue = EngineeringValue.ofDecimal(claimedMagnitude, EngineeringUnit.SQUARE_MILLIMETER),
                    projectId = context.projectId,
                    entityId = context.activeEntityId,
                    domain = "CABLE_SIZING",
                    rawTextSnippet = cableSizeMatch.value
                )

                // Validate this claim against evidence
                val claimResult = validateStructuredClaim(claim, evidenceList, context)
                if (claimResult is GroundingValidationResult.Rejected) {
                    return claimResult
                }
            }
        }

        // If narrative asserts other numeric engineering claims without any backing evidence
        val hasAnyUnit = cableSizeMatch != null ||
                CURRENT_PATTERN.containsMatchIn(sanitized) ||
                VOLTAGE_PATTERN.containsMatchIn(sanitized) ||
                PERCENT_PATTERN.containsMatchIn(sanitized)

        if (hasAnyUnit && isRecommendation && evidenceList.isEmpty()) {
            return GroundingValidationResult.Rejected(
                reason = GroundingFailureReason.NO_EVIDENCE,
                violatingClaim = EngineeringClaim(
                    claimType = EngineeringClaimType.ENGINEERING_VALUE,
                    name = "unbackedNarrativeClaim",
                    stringValue = rawText.take(100),
                    domain = "CABLE_SIZING",
                    rawTextSnippet = rawText.take(100)
                ),
                details = "Narrative asserts engineering recommendation without backing evidence"
            )
        }

        // Check for compliance verdict assertions (PASS / FAIL in narrative)
        val assertsFail = listOf("fail", "failed", "fails", "violated", "non-compliant", "non compliant", "exceeded", "راسب", "غير مطابق").any {
            lower.contains(it)
        }
        val assertsPass = listOf("pass", "passed", "passes", "conforms", "acceptable", "satisfied", "ناجح", "مطابق").any {
            lower.contains(it)
        } || (lower.contains("compliant") && !lower.contains("non-compliant") && !lower.contains("non compliant") && !lower.contains("not compliant"))

        if (evidenceList.isNotEmpty()) {
            val targetEvidence = evidenceList.filter {
                it.projectId == context.projectId &&
                        (it.entityId == null || it.entityId == context.activeEntityId)
            }

            if (targetEvidence.isNotEmpty()) {
                val overallStatus = getOverallEngineeringStatus(targetEvidence)

                if (overallStatus != null) {
                    if (assertsPass && !assertsFail && overallStatus == CheckStatus.FAIL) {
                        return GroundingValidationResult.Rejected(
                            reason = GroundingFailureReason.STATUS_MISMATCH,
                            details = "Narrative asserts compliance/pass but verified evidence status is FAIL"
                        )
                    }

                    if (assertsFail && !assertsPass && overallStatus == CheckStatus.PASS) {
                        return GroundingValidationResult.Rejected(
                            reason = GroundingFailureReason.STATUS_MISMATCH,
                            details = "Narrative asserts failure/non-compliance but verified evidence status is PASS"
                        )
                    }
                }
            }
        }

        return GroundingValidationResult.Valid
    }

    private fun findEvidenceValue(
        evidenceList: List<EngineeringEvidenceRecord>,
        claimName: String
    ): EngineeringValue? {
        val canonicalName = claimName.trim().lowercase(Locale.ROOT)

        for (record in evidenceList) {
            when (val res = record.result) {
                is CableSizingExecutionResult -> {
                    when (canonicalName) {
                        "cablesize", "conductor", "cablesize_mm2" -> {
                            val magnitude = res.details.cableSize_mm2.toDoubleOrNull()
                            if (magnitude != null) {
                                return EngineeringValue.ofDecimal(magnitude, EngineeringUnit.SQUARE_MILLIMETER)
                            }
                        }
                        "deratedampacityiz", "iz", "capacity_derated_iz_a" -> {
                            return EngineeringValue.ofDecimal(res.details.capacity_derated_Iz_A, EngineeringUnit.AMPERE)
                        }
                        "designcurrentib", "ib", "ib_a" -> {
                            return EngineeringValue.ofDecimal(res.details.Ib_A, EngineeringUnit.AMPERE)
                        }
                        "voltagedroppct", "vdpct", "voltagedrop" -> {
                            return EngineeringValue.ofDecimal(res.details.vdPct, EngineeringUnit.PERCENT)
                        }
                        "voltdrop_v" -> {
                            return EngineeringValue.ofDecimal(res.details.voltDrop_V, EngineeringUnit.VOLT)
                        }
                        "devicesize_a" -> {
                            return EngineeringValue.ofDecimal(res.details.deviceSize_A, EngineeringUnit.AMPERE)
                        }
                    }
                }
                is CableCandidateEvaluationResult -> {
                    when (canonicalName) {
                        "cablesize", "conductor", "candidatesizemm2" -> {
                            return EngineeringValue.ofDecimal(res.candidateSizeMm2, EngineeringUnit.SQUARE_MILLIMETER)
                        }
                        "deratedampacityiz", "iz", "capacity_derated_iz_a" -> {
                            return EngineeringValue.ofDecimal(res.details.capacity_derated_Iz_A, EngineeringUnit.AMPERE)
                        }
                        "voltagedroppct", "vdpct" -> {
                            return EngineeringValue.ofDecimal(res.details.vdPct, EngineeringUnit.PERCENT)
                        }
                    }
                }
                is CableWhatIfResult -> {
                    when (canonicalName) {
                        "cablesize", "conductor", "scenariosize" -> {
                            val magnitude = res.scenarioSize.toDoubleOrNull()
                            if (magnitude != null) {
                                return EngineeringValue.ofDecimal(magnitude, EngineeringUnit.SQUARE_MILLIMETER)
                            }
                        }
                        "baselinesize" -> {
                            val magnitude = res.baselineSize?.toDoubleOrNull()
                            if (magnitude != null) {
                                return EngineeringValue.ofDecimal(magnitude, EngineeringUnit.SQUARE_MILLIMETER)
                            }
                        }
                        "deratedampacityiz", "iz", "capacity_derated_iz_a" -> {
                            return EngineeringValue.ofDecimal(res.scenarioDetails.capacity_derated_Iz_A, EngineeringUnit.AMPERE)
                        }
                        "designcurrentib", "ib", "ib_a" -> {
                            return EngineeringValue.ofDecimal(res.scenarioDetails.Ib_A, EngineeringUnit.AMPERE)
                        }
                        "voltagedroppct", "vdpct" -> {
                            return EngineeringValue.ofDecimal(res.scenarioDetails.vdPct, EngineeringUnit.PERCENT)
                        }
                    }
                }
                is CableCandidatesComparisonResult -> {
                    when (canonicalName) {
                        "smallestpassing", "smallestpassingsize" -> {
                            val cand = res.candidateResults.firstOrNull { it.candidateId == res.smallestPassingCandidateId }
                            if (cand != null) {
                                return EngineeringValue.ofDecimal(cand.sizeMm2, EngineeringUnit.SQUARE_MILLIMETER)
                            }
                        }
                        "recommended", "recommendedsize" -> {
                            val cand = res.candidateResults.firstOrNull { it.candidateId == res.recommendedCandidateId }
                            if (cand != null) {
                                return EngineeringValue.ofDecimal(cand.sizeMm2, EngineeringUnit.SQUARE_MILLIMETER)
                            }
                        }
                    }
                }
            }

            // Also search in structured checks
            for (check in record.checks) {
                if (check.checkName.equals(claimName, ignoreCase = true)) {
                    return check.actualValue ?: check.requiredLimit
                }
            }
        }
        return null
    }

    private fun checkAgainstCandidateDetails(
        evidenceList: List<EngineeringEvidenceRecord>,
        claim: EngineeringClaim
    ): String? {
        val claimMag = claim.typedValue?.decimalValue ?: claim.typedValue?.intValue?.toDouble() ?: return null

        for (record in evidenceList) {
            when (val res = record.result) {
                is CableSizingExecutionResult -> {
                    val sizeMag = res.details.cableSize_mm2.toDoubleOrNull()
                    if (sizeMag != null && abs(claimMag - sizeMag) > NUMERIC_EPSILON) {
                        return "Claimed size $claimMag ${claim.typedValue?.unit?.symbol ?: "mm²"} conflicts with verified selected size $sizeMag mm²"
                    }
                }
                is CableCandidateEvaluationResult -> {
                    if (abs(claimMag - res.candidateSizeMm2) > NUMERIC_EPSILON) {
                        return "Claimed candidate size $claimMag ${claim.typedValue?.unit?.symbol ?: "mm²"} conflicts with evaluated candidate size ${res.candidateSizeMm2} mm²"
                    }
                }
                is CableWhatIfResult -> {
                    val scenarioMag = res.scenarioSize.toDoubleOrNull()
                    val baselineMag = res.baselineSize?.toDoubleOrNull()
                    val matchesScenario = scenarioMag != null && abs(claimMag - scenarioMag) <= NUMERIC_EPSILON
                    val matchesBaseline = baselineMag != null && abs(claimMag - baselineMag) <= NUMERIC_EPSILON
                    if (!matchesScenario && !matchesBaseline) {
                        return "Claimed size $claimMag ${claim.typedValue?.unit?.symbol ?: "mm²"} conflicts with what-if scenario size ${res.scenarioSize} mm² and baseline ${res.baselineSize ?: "N/A"}"
                    }
                }
                is CableCandidatesComparisonResult -> {
                    val matchingCandidate = res.candidateResults.any { abs(claimMag - it.sizeMm2) <= NUMERIC_EPSILON }
                    if (!matchingCandidate) {
                        return "Claimed candidate size $claimMag ${claim.typedValue?.unit?.symbol ?: "mm²"} conflicts with candidate comparison set"
                    }
                }
            }
        }
        return null
    }

    private fun checkNumericMismatch(
        claimed: EngineeringValue,
        verified: EngineeringValue
    ): String? {
        val v1 = claimed.decimalValue ?: claimed.intValue?.toDouble()
        val v2 = verified.decimalValue ?: verified.intValue?.toDouble()

        if (v1 != null && v2 != null) {
            if (abs(v1 - v2) > NUMERIC_EPSILON) {
                return "Claimed numeric value $v1 conflicts with verified evidence value $v2"
            }
        } else if (claimed.kind == EngineeringValueKind.BOOLEAN && verified.kind == EngineeringValueKind.BOOLEAN) {
            if (claimed.booleanValue != verified.booleanValue) {
                return "Claimed boolean ${claimed.booleanValue} conflicts with verified evidence ${verified.booleanValue}"
            }
        }
        return null
    }

    private fun checkStatusMismatch(
        claimedStatus: ToolStatus,
        evidenceList: List<EngineeringEvidenceRecord>
    ): String? {
        val overallStatus = getOverallEngineeringStatus(evidenceList) ?: return null

        if (claimedStatus == ToolStatus.PASS && overallStatus == CheckStatus.FAIL) {
            return "Claim asserts PASS but verified evidence overall status is FAIL"
        }
        if (claimedStatus == ToolStatus.FAIL && overallStatus == CheckStatus.PASS) {
            return "Claim asserts FAIL but verified evidence overall status is PASS"
        }
        return null
    }

    private fun getOverallEngineeringStatus(evidenceList: List<EngineeringEvidenceRecord>): CheckStatus? {
        var hasEngineeringResult = false
        for (record in evidenceList) {
            when (val res = record.result) {
                is CableSizingExecutionResult -> {
                    hasEngineeringResult = true
                    if (res.overallStatus == CheckStatus.FAIL) return CheckStatus.FAIL
                }
                is CableCandidateEvaluationResult -> {
                    hasEngineeringResult = true
                    if (res.overallStatus == CheckStatus.FAIL) return CheckStatus.FAIL
                }
                is CableWhatIfResult -> {
                    hasEngineeringResult = true
                    if (res.scenarioStatus == CheckStatus.FAIL) return CheckStatus.FAIL
                }
                is CableCandidatesComparisonResult -> {
                    hasEngineeringResult = true
                    if (res.passingCount == 0 && res.evaluatedCount > 0) return CheckStatus.FAIL
                }
            }
            if (record.checks.isNotEmpty()) {
                hasEngineeringResult = true
                if (record.checks.any { it.status == ToolStatus.FAIL }) {
                    return CheckStatus.FAIL
                }
            }
        }
        return if (hasEngineeringResult) CheckStatus.PASS else null
    }
}
