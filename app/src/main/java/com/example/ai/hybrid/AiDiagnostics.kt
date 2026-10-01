package com.example.ai.hybrid

/**
 * Phase 6 Batch 4 (continuation) — structured live-test diagnostics for the Gemini agentic path.
 *
 * Purpose: when the user runs the app on a real device with their own Gemini account, a single
 * Logcat filter must be able to answer, for every assistant attempt, exactly which of these
 * stages succeeded and which failed:
 *
 *   START, CONFIGURATION, NETWORK, HTTP_STATUS, MODEL_RESPONSE, FUNCTION_CALL,
 *   TOOL_EXECUTION, FUNCTION_RESPONSE, SECOND_TURN, FINAL_RESPONSE, GROUNDING, FALLBACK
 *
 * Design constraints honoured here:
 *  - The existing user-facing UI is untouched; this is logging only.
 *  - The existing semantic vocabulary is reused — [CloudDegradationReason] and
 *    [KeyReadFailure] — rather than introducing a second competing taxonomy.
 *  - NO secrets are ever emitted. [stage] accepts only primitives that callers are expected to
 *    pass as metadata (names, counts, statuses, booleans). API keys, Authorization headers,
 *    ciphertext and conversation bodies are never passed in and are never logged.
 *
 * Filter with:  adb logcat -s VoltAtlas_AI:I
 */
object AiDiagnostics {

    /** The one stable Logcat tag for every AI diagnostic line. */
    const val TAG = "VoltAtlas_AI"

    /** Prefix that makes diagnostic lines greppable inside the shared tag. */
    const val PREFIX = "AI_DIAG"

    /**
     * The observable stages of one assistant attempt, in the order a successful agentic turn
     * passes through them. Failure at any stage stops progression, so the last emitted stage is
     * exactly where the chain broke.
     */
    enum class Stage(val token: String) {
        START("START"),
        CONFIGURATION("CONFIGURATION"),
        NETWORK("NETWORK"),
        HTTP_STATUS("HTTP_STATUS"),
        MODEL_RESPONSE("MODEL_RESPONSE"),
        FUNCTION_CALL("FUNCTION_CALL"),
        TOOL_EXECUTION("TOOL_EXECUTION"),
        FUNCTION_RESPONSE("FUNCTION_RESPONSE"),
        SECOND_TURN("SECOND_TURN"),
        FINAL_RESPONSE("FINAL_RESPONSE"),
        GROUNDING("GROUNDING"),
        FALLBACK("FALLBACK")
    }

    /**
     * Emits one structured diagnostic line.
     *
     * @param stage which stage this line describes
     * @param fields alternating key/value metadata pairs, e.g. `stage(stage, "model" to m)`.
     *               Callers MUST pass only non-sensitive metadata.
     */
    fun stage(stage: Stage, vararg fields: Pair<String, Any?>) {
        val detail = fields.joinToString(separator = " ") { (k, v) -> "$k=$v" }
        AiLogger.i(TAG, "$PREFIX stage=${stage.token}${if (detail.isEmpty()) "" else " $detail"}")
    }

    /** Convenience for a terminal failure line that names the existing degradation reason. */
    fun failure(stage: Stage, reason: CloudDegradationReason, vararg fields: Pair<String, Any?>) {
        val detail = fields.joinToString(separator = " ") { (k, v) -> "$k=$v" }
        AiLogger.e(
            TAG,
            "$PREFIX stage=${stage.token} result=FAIL degradationReason=$reason" +
                if (detail.isEmpty()) "" else " $detail"
        )
    }

    /** Convenience for a terminal failure line that names an existing keystore failure. */
    fun failure(stage: Stage, reason: KeyReadFailure, vararg fields: Pair<String, Any?>) {
        val detail = fields.joinToString(separator = " ") { (k, v) -> "$k=$v" }
        AiLogger.e(
            TAG,
            "$PREFIX stage=${stage.token} result=FAIL keyFailure=$reason" +
                if (detail.isEmpty()) "" else " $detail"
        )
    }

    /** Emits a success marker for a stage. */
    fun success(stage: Stage, vararg fields: Pair<String, Any?>) {
        val detail = fields.joinToString(separator = " ") { (k, v) -> "$k=$v" }
        AiLogger.i(TAG, "$PREFIX stage=${stage.token} result=OK${if (detail.isEmpty()) "" else " $detail"}")
    }
}
