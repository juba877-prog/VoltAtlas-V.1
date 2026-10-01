package com.example.ui

import com.example.data.CircuitEntity
import com.example.engine.EarthingAutoDesignResult
import com.example.engine.EarthingResult
import com.example.engine.TransformerResult

/**
 * P0-9 / P0-10 / P0-11 — the SINGLE source of truth for the engineering basis of the
 * project earthing verification (was: U0 forced 230 V, system voltage forced 400 V,
 * main breaker = map-order first transformer FLC ?: silent 630 A).
 *
 * Contract (audit C-01/C-02/C-03, remediation plan P0-9..P0-11):
 *  - **U0** derives from the project's stored voltages — never the engine's 230 V default.
 *  - **System voltage** derives from the project's transformer secondary (unique) or
 *    circuit line voltages (unique); disagreement falls back to the user's explicit
 *    default-voltage preference with a disclosed note. Never a hardcoded 400 V.
 *  - **Main breaker In** comes from a UNIQUE basis only (one distinct transformer FLC).
 *    Multiple differing transformers or no transformer ⇒ `null` = DATA MISSING —
 *    the VM passes 0.0 and the displayed Zs_max / Loop Check verdict becomes
 *    "DATA MISSING"; the engine's internal 630 A fallback never drives a verdict.
 *
 * Engine inputs stay fully derived (the engine itself is protected); this object only
 * computes the inputs the caller passes and post-processes the result for disclosure.
 */
internal object EarthingInputBasis {

    /** Explicit engineering basis of one earthing calculation pass. */
    internal data class Basis(
        /** Project LV line-to-line system voltage (V); <= 0 means genuinely unknown. */
        val systemVoltage_V: Double,
        /** Nominal phase-to-neutral voltage (V) derived from [systemVoltage_V] (P0-9). */
        val u0_V: Double,
        /** Main breaker rating In (A), or null when the project has NO defensible basis. */
        val mainBreakerRating_A: Double?,
        /** Basis disclosures appended verbatim to `EarthingResult.messages`. */
        val notes: List<String>
    ) {
        /** Zs_max / Loop Check are only verifiable when a main-breaker basis exists. */
        val loopVerdictAvailable: Boolean get() = mainBreakerRating_A != null
    }

    const val DATA_MISSING = "DATA MISSING"

    /**
     * Nominal phase-to-neutral voltage for a 3-phase line voltage (IEC 60364 conventions).
     * The explicit map covers the standard distributions; anything else uses U0 ≈ UL/√3.
     * Single source for both circuit creation and the earthing U0 basis (was inlined in
     * `EngineeringViewModel.createDefaultCircuit`).
     */
    fun phaseNeutralVoltageOf(v3ph: Int): Int = when (v3ph) {
        380 -> 220
        400 -> 230
        415 -> 240
        480 -> 277
        else -> (v3ph / 1.732).toInt()
    }

    /**
     * Computes the earthing input basis from the project's OWN stored data.
     *
     * @param transformerResults project transformer results keyed by transformerId
     * @param circuits project circuits (used for the voltage basis when transformers
     *   disagree or are absent)
     * @param defaultSystemVoltage_V the user's explicit default-voltage preference —
     *   the fallback of last resort, never a silent constant
     */
    fun compute(
        transformerResults: Map<String, TransformerResult>,
        circuits: List<CircuitEntity>,
        defaultSystemVoltage_V: Int
    ): Basis {
        val notes = mutableListOf<String>()

        // ---- P0-11: project system voltage + P0-9: U0 basis -------------------
        val txVoltages = transformerResults.values
            .mapNotNull { r -> r.voltage.takeIf { it > 0.0 } }
            .distinct()
        // Line-to-neutral circuits store the phase voltage; only 3-phase circuits carry
        // the system line voltage, so filter them first (fall back to all circuits when
        // a project has no 3-phase circuits at all).
        val circuitLineVoltages = circuits
            .filter { it.phases.startsWith("3P", ignoreCase = true) }
            .mapNotNull { c -> c.voltageV.takeIf { it > 0 } }
            .distinct()
            .map { it.toDouble() }
        val anyCircuitVoltages = circuits
            .mapNotNull { c -> c.voltageV.takeIf { it > 0 } }
            .distinct()
            .map { it.toDouble() }

        var u0 = 0.0
        val systemVoltage: Double = when {
            txVoltages.size == 1 -> txVoltages.single().also {
                u0 = phaseNeutralVoltageOf(it.toInt()).toDouble()
            }

            txVoltages.size > 1 -> {
                val fallback = circuitLineVoltages.singleOrNull() ?: defaultSystemVoltage_V.toDouble()
                val fallbackSource = if (circuitLineVoltages.size == 1) {
                    "the unique circuit-voltage basis"
                } else {
                    "the user default voltage"
                }
                notes.add(
                    "System voltage: AMBIGUOUS — project transformers disagree on secondary voltage " +
                        "(${txVoltages.joinToString { "${it.toInt()} V" }}); using $fallbackSource " +
                        "(${fallback.toInt()} V)."
                )
                u0 = phaseNeutralVoltageOf(fallback.toInt()).toDouble()
                fallback
            }

            circuitLineVoltages.size == 1 -> circuitLineVoltages.single().also {
                u0 = phaseNeutralVoltageOf(it.toInt()).toDouble()
            }

            circuitLineVoltages.size > 1 -> {
                val fallback = defaultSystemVoltage_V.toDouble()
                notes.add(
                    "System voltage: AMBIGUOUS — circuit line voltages disagree " +
                        "(${circuitLineVoltages.joinToString { "${it.toInt()} V" }}); " +
                        "using the user default voltage (${fallback.toInt()} V)."
                )
                u0 = phaseNeutralVoltageOf(fallback.toInt()).toDouble()
                fallback
            }

            anyCircuitVoltages.size == 1 ->
                // No 3-phase circuit exists: the single stored voltage IS the phase voltage
                // (1-phase project) — U0 equals it directly, never U/√3.
                anyCircuitVoltages.single().also { u0 = it }

            else -> defaultSystemVoltage_V.toDouble().also {
                u0 = phaseNeutralVoltageOf(it.toInt()).toDouble()
            }
        }

        if (systemVoltage <= 0.0) {
            notes.add(
                "System voltage: $DATA_MISSING — no transformer, circuit or default voltage is " +
                    "valid; U0 falls back to the engine default (230 V) — Zs_max basis unverified."
            )
        }

        // ---- P0-10: main breaker basis ----------------------------------------
        // Unique-distinct FLC only: one transformer ⇒ its rated current; multiple
        // transformers with DIFFERENT ratings are genuinely ambiguous (the old code took
        // the map-order first result); no transformer was the old silent 630 A.
        val distinctFlcs = transformerResults.values
            .map { it.flc }
            .filter { it > 0.0 }
            .distinct()
        val mainBreaker = distinctFlcs.singleOrNull()
        when {
            mainBreaker != null -> notes.add(
                ("Main breaker basis: transformer rated FLC %.1f A (unique project basis; " +
                    "no installed main-breaker entity exists in the project).").format(mainBreaker)
            )

            transformerResults.isEmpty() -> notes.add(
                "Main breaker: $DATA_MISSING — no transformer in project (the engine's internal " +
                    "630 A fallback does not drive any displayed verdict)."
            )

            distinctFlcs.size > 1 -> notes.add(
                "Main breaker: $DATA_MISSING — ${transformerResults.size} transformers with " +
                    "differing FLCs (${distinctFlcs.joinToString { "%.1f A".format(it) }}); " +
                    "no unique main device (was: map-order first FLC)."
            )

            else -> notes.add(
                "Main breaker: $DATA_MISSING — transformer FLC unavailable " +
                    "(was: silent 630 A default)."
            )
        }

        return Basis(
            systemVoltage_V = systemVoltage,
            u0_V = u0,
            mainBreakerRating_A = mainBreaker,
            notes = notes
        )
    }

    /**
     * Applies the basis to the engine result so ABSENCE is expressed, never hidden:
     *  - basis notes are appended to [EarthingResult.messages];
     *  - when no main-breaker basis exists the Loop Check verdict becomes
     *    [DATA MISSING] (and cannot claim overall compliance), because Zs_max was
     *    computed on the engine's assumed 630 A — an assumption, not project data.
     */
    fun applyToResult(
        base: EarthingResult,
        basis: Basis,
        autoDesign: EarthingAutoDesignResult?
    ): EarthingResult {
        var result = base.copy(messages = base.messages + basis.notes)
        if (!basis.loopVerdictAvailable) {
            result = result.copy(
                loopCheck = DATA_MISSING,
                overallCompliant = false,
                status = "⚠ REVIEW REQUIRED — DATA MISSING",
                messages = result.messages + (
                    "Zs_max / Loop Check: $DATA_MISSING — no main-breaker rating exists in this " +
                        "project; the displayed Zs_max is withheld because it would be computed " +
                        "on the engine's assumed 630 A basis."
                    )
            )
        }
        return result.copy(autoDesignResult = autoDesign)
    }
}
