package com.example.verdict

import com.example.engine.ProtectionResult
import com.example.engine.SizingResult

/**
 * Canonical final circuit verdict (ECT-EE-06, owner decision **D5 = PLAN DEFAULT (A)**).
 *
 * ============================================================================
 * WHY THIS EXISTS (§13 of the ECT)
 * ============================================================================
 *
 * Neither existing model can express the combination on its own:
 *
 *  * `SizingResult.resultStatus` is produced by `CableSizingEngine` as
 *    `isPass = isAmpacityPass && isVdPass` (`CableSizingEngine.kt:867/875`) —
 *    the **sizing** verdict. It never sees a `ProtectionResult`, and correctly so.
 *  * `ProtectionResult.finalStatus` is produced by `ProtectionEngine`
 *    (`ProtectionEngine.kt:453-458`) and is the **authoritative protection**
 *    verdict. It never sees the ampacity / voltage-drop verdicts.
 *
 * The final circuit verdict is genuinely a function of *both*, so §13's own
 * escape clause applies: "Only introduce a dedicated helper/sealed status model
 * if an existing canonical model genuinely cannot express the required states."
 *
 * ============================================================================
 * WHAT THIS IS *NOT*
 * ============================================================================
 *
 *  * **Not an engineering engine.** It performs no IEC calculation, evaluates no
 *    formula and introduces no engineering constant. It only combines two
 *    status strings that already exist.
 *  * **Not a second protection engine.** It never evaluates protection. It *reads*
 *    `ProtectionResult.finalStatus`, which is and remains produced exclusively by
 *    the existing `ProtectionEngine`.
 *  * **Not a parallel status architecture.** This is a single pure function, and
 *    every consumer below calls *this* one rather than re-deriving
 *    `if (Isc > Icu)` locally — which is the opposite of duplication.
 *  * **Not in the Tier F directory.** The owner directed this file into a neutral
 *    package so that nothing new is added under `app/src/main/java/com/example/engine/`.
 *
 * ============================================================================
 * STATUS CONTRACT (§6 of the ECT) — exact semantics
 * ============================================================================
 *
 * | Case | Condition | Result |
 * |---|---|---|
 * | **A** | protection executed, `Isc <= Icu`, `finalStatus != FAIL`, other domains valid | `PASS` |
 * | **B** | protection executed, `Isc > Icu` (`finalStatus == FAIL`) | `FAIL` — overrides ampacity / voltage-drop PASS |
 * | **C** | `ProtectionResult.finalStatus == FAIL` for any other reason | `FAIL` |
 * | **D** | Isc required but unavailable (`finalStatus` reports missing data) | `INCOMPLETE` |
 * | **E** | protection evaluation did not run / is unavailable (`null`) | `INCOMPLETE` |
 * | §14.6 | sizing `FAIL` / `ERROR` | `FAIL` regardless of protection |
 * | §14.8 | no sizing result at all | `STANDBY` (existing semantics preserved) |
 *
 * **PASS is never reachable from a missing or failed protection verdict.** That is
 * the entire point of this type: every path that previously fell through to
 * `PASS` now lands on `FAIL` or `INCOMPLETE`.
 *
 * `Isc == Icu` is *not* a failure: `ProtectionEngine` uses `icu >= isc - 0.001`
 * (`ProtectionEngine.kt:395-400`), so equality yields `finalStatus == "PASS"`
 * and this resolver returns `PASS` (§14.1).
 *
 * A missing `Icu` (`ProtectionResult` absent, or its short-circuit domain
 * reported as missing data) resolves to `INCOMPLETE`, never `PASS` (§14.3).
 *
 * ============================================================================
 * SOURCE-OF-TRUTH RULE (§8 of the ECT)
 * ============================================================================
 *
 * This type never computes `Isc` and never computes `Icu`. It consumes the
 * authoritative `SizingResult.resultStatus` and the authoritative
 * `ProtectionResult.finalStatus` exactly as produced by their engines.
 */
enum class CircuitFinalVerdict {

    /** All required engineering domains valid and protection passed (Case A). */
    PASS,

    /** Nothing failed, but a non-fatal condition is reported (e.g. selectivity WARNING). */
    WARNING,

    /** A required domain failed — includes `Isc > Icu` (Cases B, C, §14.6). */
    FAIL,

    /** Required data or evaluation unavailable — never PASS (Cases D, E). */
    INCOMPLETE,

    /** The sizing engine itself errored. Distinct from FAIL and never PASS. */
    ERROR,

    /** No sizing result exists for this circuit (§14.8 — existing semantics). */
    STANDBY;

    companion object {

        /**
         * Canonical combination of the sizing verdict and the protection verdict.
         *
         * @param sizingStatus `SizingResult.resultStatus`, or `null` when the circuit
         *   has not been sized.
         * @param protectionFinalStatus `ProtectionResult.finalStatus`, or `null` when
         *   protection evaluation did not run / is unavailable (**Case E**).
         */
        fun resolve(
            sizingStatus: com.example.engine.SizingResultStatus?,
            protectionFinalStatus: String?
        ): CircuitFinalVerdict {
            // §14.8 — no sizing result: existing STANDBY semantics are preserved and
            // this is emphatically not a PASS.
            if (sizingStatus == null) return STANDBY

            // §14.6 — a sizing-domain failure cannot be erased by any protection result.
            return when (sizingStatus) {
                com.example.engine.SizingResultStatus.FAIL -> FAIL
                com.example.engine.SizingResultStatus.ERROR -> ERROR
                com.example.engine.SizingResultStatus.INCOMPLETE -> INCOMPLETE
                com.example.engine.SizingResultStatus.PASS,
                com.example.engine.SizingResultStatus.WARNING -> {
                    val verdict = fromProtection(protectionFinalStatus)
                    // A sizing WARNING is never downgraded to a plain PASS.
                    if (verdict == PASS && sizingStatus == com.example.engine.SizingResultStatus.WARNING) {
                        WARNING
                    } else {
                        verdict
                    }
                }
            }
        }

        /** Convenience overload taking the domain results directly. */
        fun resolve(
            sizingResult: SizingResult?,
            protectionResult: ProtectionResult?
        ): CircuitFinalVerdict = resolve(sizingResult?.resultStatus, protectionResult?.finalStatus)

        /**
         * Maps the authoritative `ProtectionResult.finalStatus` onto a verdict.
         *
         * Matching is substring-based and ordered fail-first because the protection
         * engine has historically emitted several spellings (`"FAIL"`, `"DATA MISSING"`,
         * `"MISSING"`); anything unrecognised resolves to [INCOMPLETE] so that an
         * unknown protection state can never surface as PASS.
         */
        private fun fromProtection(protectionFinalStatus: String?): CircuitFinalVerdict {
            // Case E — protection evaluation did not run or is unavailable.
            val raw = protectionFinalStatus?.trim().orEmpty()
            if (raw.isEmpty()) return INCOMPLETE

            val status = raw.uppercase()
            return when {
                // Cases B / C — `Isc > Icu`, or any other protection failure.
                status.contains("FAIL") -> FAIL
                // Case D — required Isc / Icu data missing or the check could not run.
                status.contains("MISSING") || status.contains("INVALID") ||
                    status.contains("UNAVAILABLE") || status.contains("INCOMPLETE") -> INCOMPLETE
                status.contains("WARNING") || status.contains("REVIEW") -> WARNING
                // Case A — protection executed and passed.
                status.contains("PASS") -> PASS
                // Unrecognised protection state: never PASS.
                else -> INCOMPLETE
            }
        }
    }
}
