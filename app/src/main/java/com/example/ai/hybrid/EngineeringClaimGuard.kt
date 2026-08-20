package com.example.ai.hybrid

import java.util.Locale

/**
 * Pure deterministic validator preventing unbacked engineering claims from reaching the user.
 *
 * Invariant:
 * - A Cloud LLM TextResponse asserting specific numeric values and engineering units
 *   (e.g., "Use 300 mm² for this feeder") MUST be backed by a deterministic tool-call result
 *   in the same turn. Otherwise, it is rejected and triggers fallback to the local rule engine.
 */
object EngineeringClaimGuard {

    private val NUMERIC_WITH_UNIT_PATTERN = Regex(
        """\d+(\.\d+)?\s*(mm²|mm2|مم²|مم2|kVA|kva|kW|kw|kA|ka|kV|kv|Hz|hz|Ω|ohm|ohms|%|فولت|أمبير|امبير)""",
        RegexOption.IGNORE_CASE
    )

    private val NUMERIC_WITH_AMPERE_OR_VOLT_PATTERN = Regex(
        """\d+(\.\d+)?\s*(A|V)\b"""
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

    /**
     * Returns true if [rawText] appears to assert a specific engineering value
     * as a recommendation or fact without being backed by an accompanying tool-call result.
     */
    fun containsUnsupportedEngineeringClaim(
        rawText: String,
        hasBackingToolResult: Boolean
    ): Boolean {
        if (hasBackingToolResult) return false
        if (rawText.isBlank()) return false

        val lower = rawText.lowercase(Locale.ROOT)
        val hasEngineeringUnit = NUMERIC_WITH_UNIT_PATTERN.containsMatchIn(rawText) ||
                NUMERIC_WITH_AMPERE_OR_VOLT_PATTERN.containsMatchIn(rawText)

        if (!hasEngineeringUnit) return false

        // Check for recommendation verbs in English or Arabic
        val hasRecommendationEn = RECOMMENDATION_VERBS_EN.any { verb ->
            Regex("""\b$verb\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        }
        val hasRecommendationAr = RECOMMENDATION_VERBS_AR.any { verb ->
            rawText.contains(verb)
        }

        return hasRecommendationEn || hasRecommendationAr
    }
}
