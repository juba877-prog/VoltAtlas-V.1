package com.example.ai.hybrid

import com.example.ai.conversation.*
import com.example.ai.runtime.RuntimePromptCompiler
import com.example.ai.runtime.RuntimePromptInputs
import com.example.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Structured electrical engineering intent produced by the on-device seam.
 *
 * P1-2 (L-02): this type is populated by [DefaultNanoClientSeam] — a deterministic
 * keyword/regex parser. There is no Gemini Nano or ML Kit GenAI dependency in this build,
 * so "on-device inference" claims are deliberately not made here. Field shape follows the
 * structured-output conventions the future hardware integration is expected to target.
 *
 * Every field is nullable and has no default: the on-device layer only reports what the user
 * actually said. Missing engineering inputs must be requested as blocking questions rather
 * than filled with assumed voltages, lengths, power factors or impedances.
 */
sealed class NanoStructuredIntent {
    data class CableSizing(
        val powerKw: Double? = null,
        val currentAmps: Double? = null,
        val voltageV: Double? = null,
        val lengthM: Double? = null,
        val isThreePhase: Boolean? = null,
        val targetVdPct: Double? = null
    ) : NanoStructuredIntent()

    data class MotorStarter(
        val motorKw: Double,
        val voltageV: Double? = null,
        val isThreePhase: Boolean? = null,
        val preferredMethod: String? = null
    ) : NanoStructuredIntent()

    data class PowerFactorCorrection(
        val activePowerKw: Double,
        val currentPf: Double,
        val targetPf: Double? = null,
        val voltageV: Double? = null
    ) : NanoStructuredIntent()

    data class TransformerFault(
        val ratingKva: Double,
        val secondaryVoltageV: Double? = null,
        val impedancePercentZ: Double? = null
    ) : NanoStructuredIntent()

    data class GeneralAdvisory(
        val explanation: String
    ) : NanoStructuredIntent()
}

/**
 * Testable abstraction seam over on-device ML Kit GenAI Prompt API / Gemini Nano client.
 */
interface NanoClientSeam {
    suspend fun isAvailable(): Boolean
    suspend fun parseIntent(prompt: String, systemContext: String): NanoStructuredIntent?
}

/**
 * Deterministic keyword/regex client seam — the ONLY [NanoClientSeam] implementation in the
 * build. It performs no hardware inference.
 *
 * P1-2 (L-02): [isHardwareAvailable] defaults to FALSE (fail-closed). This build integrates
 * no Gemini Nano / ML Kit GenAI SDK, so availability must never be claimed by default; a
 * wiring that gains real probing must pass explicit evidence instead of relying on a baked-in
 * `true`.
 */
class DefaultNanoClientSeam(
    private val isHardwareAvailable: Boolean = false
) : NanoClientSeam {

    override suspend fun isAvailable(): Boolean = isHardwareAvailable

    override suspend fun parseIntent(prompt: String, systemContext: String): NanoStructuredIntent? {
        val lower = prompt.lowercase()

        // 1. Motor Starter Intent. The rating must actually be spoken; no default rating,
        //    system voltage or efficiency is assumed.
        if (lower.contains("motor") && (lower.contains("starter") || lower.contains("starting") || lower.contains("kw") || lower.contains("flc"))) {
            val kwMatch = Regex("""(\d+(\.\d+)?)\s*(kw|hp)""").find(lower)
            val kw = kwMatch?.let {
                val value = it.groupValues[1].toDoubleOrNull() ?: return@let null
                if (it.groupValues[3] == "hp") value * 0.746 else value
            }
            val is3p = !lower.contains("single phase") && !lower.contains("1-phase")
            if (kw != null) {
                return NanoStructuredIntent.MotorStarter(motorKw = kw, voltageV = null, isThreePhase = is3p)
            }
        }

        // 2. Power Factor Correction Intent. Power factor values are extracted only when stated.
        if (lower.contains("pfc") || lower.contains("power factor") || lower.contains("capacitor") || lower.contains("kvar")) {
            val kwMatch = Regex("""(\d+(\.\d+)?)\s*kw""").find(lower)
            val kw = kwMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val pfMatch = Regex("""0\.\d{1,2}""").findAll(lower).mapNotNull { it.value.toDoubleOrNull() }.toList()
            val initialPf = pfMatch.firstOrNull()
            val targetPf = pfMatch.getOrNull(1)
            if (kw != null && initialPf != null) {
                return NanoStructuredIntent.PowerFactorCorrection(
                    activePowerKw = kw,
                    currentPf = initialPf,
                    targetPf = targetPf
                )
            }
        }

        // 3. Transformer Fault Intent. Rating and impedance are extracted only when stated;
        //    impedance is never assumed to be a "typical" percentage.
        if (lower.contains("transformer") || lower.contains("trafo") || lower.contains("fault level") || lower.contains("short circuit")) {
            val kvaMatch = Regex("""(\d+(\.\d+)?)\s*(kva|mva)""").find(lower)
            val kva = kvaMatch?.let {
                val value = it.groupValues[1].toDoubleOrNull() ?: return@let null
                if (it.groupValues[3] == "mva") value * 1000.0 else value
            }
            val zMatch = Regex("""(\d+(\.\d+)?)\s*%""").find(lower)
            val zPct = zMatch?.groupValues?.get(1)?.toDoubleOrNull()
            if (kva != null && zPct != null) {
                return NanoStructuredIntent.TransformerFault(ratingKva = kva, impedancePercentZ = zPct)
            }
        }

        // 4. Cable Sizing Intent. Voltage, length and load are extracted only when stated —
        //    no system voltage, route length or derived load is assumed.
        if (lower.contains("cable") || lower.contains("ampacity") || lower.contains("voltage drop") || lower.contains("run")) {
            val kwMatch = Regex("""(\d+(\.\d+)?)\s*kw""").find(lower)
            val aMatch = Regex("""(\d+(\.\d+)?)\s*a""").find(lower)
            val lengthMatch = Regex("""(\d+(\.\d+)?)\s*(m|meter)""").find(lower)

            val kw = kwMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val amps = aMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val length = lengthMatch?.groupValues?.get(1)?.toDoubleOrNull()
            val is3p = !lower.contains("single phase") && !lower.contains("1-phase")

            if (kw != null || amps != null) {
                return NanoStructuredIntent.CableSizing(
                    powerKw = kw,
                    currentAmps = amps,
                    voltageV = null,
                    lengthM = length,
                    isThreePhase = is3p
                )
            }
        }

        return NanoStructuredIntent.GeneralAdvisory(
            explanation = "VoltAtlas Offline Nano Assistant: Ready to size cables, evaluate motor starters, calculate PFC capacitor banks, or check transformer fault levels per IEC 60364 & IEC 60909."
        )
    }
}

/**
 * EXPERIMENTAL on-device conversation engine — keyword-parser-backed, NOT hardware Gemini Nano.
 *
 * P1-2 (L-01): this engine is NOT WIRED into production routing.
 * [com.example.ai.hybrid.ConversationEngineProvider] never supplies it to
 * [HybridConversationRouter] (the router's `nanoEngine` parameter stays null), so the offline
 * nano branch is dormant. Any future wiring must land with a provider, a feature flag, a
 * settings surface and tests — a wiring plan, not an implicit default.
 *
 * Safety Invariants:
 * - Rule 3 Compliance: NEVER computes electrical math directly in the model.
 *   Extracts structured intent parameters and calls deterministic [com.example.engine] models.
 * - Fabrication Guard: All responses pass through [EngineeringClaimGuard].
 * - Graceful Fallback: Emits [CloudDegradationReason.HTTP_OR_PROVIDER_ERROR] when hardware or model is absent.
 */
class OnDeviceNanoConversationEngine(
    private val clientSeam: NanoClientSeam = DefaultNanoClientSeam()
) : ConversationEngine {

    companion object {
        private val SYSTEM_ELECTRICAL_CONSTRAINTS = """
            You are the VoltAtlas on-device electrical engineering assistant operating strictly under IEC 60364, BS 7671, and IEC 60909 standards.
            All electrical calculations must be executed by deterministic computational engines.
            Never invent or guess conductor cross-sections, current capacities, or breaker ratings.
        """.trimIndent()
    }

    override suspend fun evaluateTurn(
        input: ConversationTurnInput,
        decisionIdProvider: () -> String
    ): HybridEngineResult = withContext(Dispatchers.Default) {
        val decisionId = decisionIdProvider()

        // 1. Check On-Device Availability
        if (!clientSeam.isAvailable()) {
            return@withContext HybridEngineResult(
                decision = ConversationTurnDecision(
                    decisionId = decisionId,
                    action = ConversationAction(
                        actionType = ConversationActionType.SAFE_ERROR,
                        reasonCode = "NANO_UNAVAILABLE"
                    ),
                    updatedState = input.priorState ?: ConversationTaskState(
                        taskId = "TASK-DEGRADED-" + UUID.randomUUID().toString().take(6),
                        projectId = input.projectContext.projectId,
                        baseProjectRevision = input.projectContext.liveRevision
                    ),
                    explanationSummary = "On-Device Gemini Nano is not available on this device."
                ),
                source = ConversationEngineSource.ON_DEVICE_GEMINI_NANO,
                cloudDegradationReason = CloudDegradationReason.HTTP_OR_PROVIDER_ERROR,
                hasBackingToolResult = false
            )
        }

        // 2. Parse Structured Intent from User Prompt
        val intent = clientSeam.parseIntent(input.userUtterance, SYSTEM_ELECTRICAL_CONSTRAINTS)
            ?: return@withContext HybridEngineResult(
                decision = ConversationTurnDecision(
                    decisionId = decisionId,
                    action = ConversationAction(
                        actionType = ConversationActionType.EXPLAIN_RESULT,
                        reasonCode = "NANO_NO_INTENT"
                    ),
                    updatedState = input.priorState ?: ConversationTaskState(
                        taskId = "TASK-NANO-" + UUID.randomUUID().toString().take(6),
                        projectId = input.projectContext.projectId,
                        baseProjectRevision = input.projectContext.liveRevision
                    ),
                    explanationSummary = "Could not interpret electrical intent. Please specify motor rating, cable length, or transformer kVA."
                ),
                source = ConversationEngineSource.ON_DEVICE_GEMINI_NANO,
                cloudDegradationReason = null,
                hasBackingToolResult = false
            )

        // 3. Structured intent extraction ONLY (Rule 3). The on-device layer never computes
        //    electrical results. Previously it ran motor-current, PFC kvar, transformer fault
        //    and cable-sizing formulas here, with a fabricated `vdPct = 1.85` / `63 A` / `16 mm²`
        //    cable result baked in. Any engineering answer must come from the deterministic
        //    engines through the shared tool registry, so this layer only reports what is
        //    needed and routes the request onward.
        val explanationText: String
        when (intent) {
            is NanoStructuredIntent.MotorStarter ->
                explanationText = routingMessage(language = input.priorState?.userLanguage)

            is NanoStructuredIntent.PowerFactorCorrection ->
                explanationText = routingMessage(language = input.priorState?.userLanguage)

            is NanoStructuredIntent.TransformerFault ->
                explanationText = routingMessage(language = input.priorState?.userLanguage)

            is NanoStructuredIntent.CableSizing ->
                explanationText = routingMessage(language = input.priorState?.userLanguage)

            is NanoStructuredIntent.GeneralAdvisory ->
                explanationText = intent.explanation
        }

        // 4. Evidence guard. No deterministic tool was executed in this path, so this result
        //    must never be reported as tool-backed.
        val hasFabrication = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = explanationText,
            hasBackingToolResult = false
        )
        if (hasFabrication) {
            return@withContext HybridEngineResult(
                decision = ConversationTurnDecision(
                    decisionId = decisionId,
                    action = ConversationAction(
                        actionType = ConversationActionType.SAFE_ERROR,
                        reasonCode = "UNSUPPORTED_ENGINEERING_CLAIM"
                    ),
                    updatedState = input.priorState ?: ConversationTaskState(
                        taskId = "TASK-NANO-" + UUID.randomUUID().toString().take(6),
                        projectId = input.projectContext.projectId,
                        baseProjectRevision = input.projectContext.liveRevision,
                        activeEntityType = input.projectContext.activeEntityType,
                        activeEntityId = input.projectContext.activeEntityId
                    ),
                    explanationSummary = if (input.priorState?.userLanguage == ConversationLanguage.AR) {
                        "تم رفض الإجابة لأنها ادعت قيمة هندسية دون نتيجة حساب موثقة."
                    } else {
                        "The response was rejected because it asserted an engineering value " +
                            "without a certified calculation result."
                    }
                ),
                source = ConversationEngineSource.ON_DEVICE_GEMINI_NANO,
                cloudDegradationReason = CloudDegradationReason.UNSUPPORTED_ENGINEERING_CLAIM,
                hasBackingToolResult = false
            )
        }

        return@withContext HybridEngineResult(
            decision = ConversationTurnDecision(
                decisionId = decisionId,
                action = ConversationAction(
                    actionType = ConversationActionType.EXPLAIN_RESULT,
                    reasonCode = "ON_DEVICE_NANO_INTENT_ONLY"
                ),
                updatedState = input.priorState ?: ConversationTaskState(
                    taskId = "TASK-NANO-" + UUID.randomUUID().toString().take(6),
                    projectId = input.projectContext.projectId,
                    baseProjectRevision = input.projectContext.liveRevision,
                    activeEntityType = input.projectContext.activeEntityType,
                    activeEntityId = input.projectContext.activeEntityId
                ),
                explanationSummary = explanationText
            ),
            source = ConversationEngineSource.ON_DEVICE_GEMINI_NANO,
            cloudDegradationReason = null,
            hasBackingToolResult = false
        )
    }

    /**
     * The on-device layer reports that a certified result must come from the deterministic
     * engine. It never substitutes an estimate.
     */
    private fun routingMessage(language: ConversationLanguage?): String =
        if (language == ConversationLanguage.AR) {
            "تم التقاط نيتك على الجهاز. يجب الحصول على النتيجة الهندسية من محرك VoltAtlas المطابق " +
                "عبر سجل الأدوات المشترك؛ لن يتم حساب أي قيمة هنا."
        } else {
            "Your intent was captured on-device. The engineering result must come from the " +
                "authoritative VoltAtlas engine through the shared tool registry; nothing is " +
                "calculated here."
        }
}
