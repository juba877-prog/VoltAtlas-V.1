package com.example.ai.appfunctions

import com.example.ai.contracts.ToolStatus
import com.example.ai.evidence.EngineeringEvidenceRecord
import com.example.ai.tools.AssistantToolRuntime
import com.example.ai.tools.ToolExecutionOutcome
import com.example.ai.tools.cable.CableSizingExecutionResult
import com.example.ai.tools.cable.CableToolNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.tan

// =============================================================================
// AppFunctions Core Annotations & Contracts (Android 16 / SDK 36 Compatibility)
// =============================================================================

/**
 * Declares an AppFunction exposed to system AI agents and voice shortcuts.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppFunction(
    val isDescribedByKDoc: Boolean = true
)

/**
 * Marks a schema data class as serializable across the AppFunction IPC boundary.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppFunctionSerializable(
    val isDescribedByKDoc: Boolean = true
)

open class AppFunctionException(message: String) : Exception(message)
class AppFunctionInvalidArgumentException(message: String) : AppFunctionException(message)

// =============================================================================
// 1. Cable Sizing AppFunction Schema & Models
// =============================================================================

/** Parameters for sizing an electrical low-voltage power cable. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CableSizingParams(
    /** Authoritative project identity. */
    val projectId: String? = null,
    /** Authoritative circuit identity. */
    val circuitId: String? = null,
    /** Design load operating current in Amperes. */
    val loadCurrentAmps: Double = 0.0,
    /** Rated phase-to-phase system voltage in Volts. Not defaulted: a missing system voltage is a blocking input. */
    val voltageV: Double? = null,
    /** One-way physical route length of the cable in meters. Not defaulted. */
    val cableLengthMeters: Double? = null,
    /** Installation reference method per IEC 60364-5-52 (e.g. 'C' for perforated tray). Not defaulted. */
    val installationMethod: String? = null,
    /** Design ambient temperature in degrees Celsius. Not defaulted. */
    val ambientTempC: Int? = null,
    /** Operating load power factor cos φ (between 0.1 and 1.0). Not defaulted. */
    val powerFactor: Double? = null,
    /** True if 3-phase 4-wire system. Not defaulted. */
    val isThreePhase: Boolean? = null,
    /** Maximum acceptable voltage drop percentage. Not defaulted: a project or user value is required. */
    val maxVdPct: Double? = null
)

/** Sizing result for an electrical low-voltage power cable. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CableSizingOutput(
    /** Recommended phase conductor cross-sectional area in mm² per IEC 60228. */
    val recommendedSizeMm2: String,
    /** Recommended protective breaker / fuse rating In in Amperes. */
    val protectiveDeviceRatingAmps: Double,
    /** Continuous current carrying capacity Iz after all deratings in Amperes. */
    val izCapacityAmps: Double,
    /** Calculated end-of-line voltage drop percentage. */
    val voltageDropPct: Double,
    /** Calculated end-of-line voltage drop in Volts. */
    val voltageDropVolts: Double,
    /** Overall compliance verdict ('PASS' or 'FAIL'). */
    val status: String,
    /** Standard engineering reference code. */
    val standardReference: String
)

// =============================================================================
// 2. Motor Starter Sizing AppFunction Schema & Models
// =============================================================================

/** Parameters for sizing an induction motor starter and circuit protection. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MotorStarterParams(
    /** Rated motor mechanical shaft output power in kW. */
    val motorPowerKw: Double = 0.0,
    /** Rated operational voltage in Volts. Not defaulted. */
    val lineVoltageV: Double? = null,
    /** Rated full load power factor cos φ. Not defaulted. */
    val powerFactor: Double? = null,
    /** Motor efficiency percentage at full load (e.g. 91.5 for IE3). Not defaulted. */
    val efficiencyPct: Double? = null,
    /** True for 3-phase motor, false for single-phase. Not defaulted. */
    val isThreePhase: Boolean? = null,
    /** Preferred starting topology ('DOL', 'STAR_DELTA', 'SOFT_STARTER', 'VFD'). Not defaulted. */
    val preferredTopology: String? = null
)

/** Recommended starter hardware ratings and starting parameters. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MotorStarterOutput(
    /** Rated continuous full-load operating current in Amperes. */
    val flcAmps: Double,
    /** Peak initial inrush starting current in Amperes. */
    val startingCurrentAmps: Double,
    /** Recommended starting method based on IEC 60034 / utility grid constraints. */
    val recommendedMethod: String,
    /** Required main contactor AC-3 thermal rating in Amperes. */
    val contactorRatingAmps: Double,
    /** Recommended motor branch circuit breaker rating (Type D or Motor Circuit Protector) in Amperes. */
    val breakerRatingAmps: Double,
    /** Thermal overload relay setting current in Amperes. */
    val thermalOverloadSettingAmps: Double,
    /** Applicable international standard citation. */
    val standardReference: String
)

// =============================================================================
// 3. Power Factor Correction (PFC) AppFunction Schema & Models
// =============================================================================

/** Parameters for sizing power factor correction capacitor banks. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PfcParams(
    /** Total active load in kilowatts (kW). */
    val activePowerKw: Double = 0.0,
    /** Existing uncompensated power factor cos φ1. Not defaulted. */
    val initialPowerFactor: Double? = null,
    /** Desired target power factor cos φ2. Not defaulted: a target is a user/project decision. */
    val targetPowerFactor: Double? = null,
    /** Line voltage in Volts. Not defaulted. */
    val lineVoltageV: Double? = null
)

/** Sized capacitor bank output and power savings metrics. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PfcOutput(
    /** Net reactive compensation required in kilovolt-amperes reactive (kvar). */
    val requiredKvar: Double,
    /** Recommended commercial standard capacitor bank rating in kvar. */
    val recommendedBankKvar: Double,
    /** Line current drawn before compensation in Amperes. */
    val currentBeforeAmps: Double,
    /** Line current drawn after compensation in Amperes. */
    val currentAfterAmps: Double,
    /** Reduction in apparent power demand (kVA saved). */
    val kvaDemandSaved: Double,
    /** Percentage reduction in distribution line current. */
    val currentReductionPct: Double,
    /** Engineering standard reference. */
    val standardReference: String
)

// =============================================================================
// 4. Transformer Fault Level AppFunction Schema & Models
// =============================================================================

/** Parameters for calculating transformer short-circuit current per IEC 60909. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TransformerFaultParams(
    /** Transformer rated apparent power in kVA. */
    val transformerRatingKva: Double = 0.0,
    /** Primary MV voltage in Volts. Not defaulted. */
    val primaryVoltageV: Double? = null,
    /** Secondary LV voltage in Volts. Not defaulted. */
    val secondaryVoltageV: Double? = null,
    /** Percentage short-circuit impedance %Z (or Uk%). Not defaulted: never assumed. */
    val impedancePercentZ: Double? = null
)

/** Transformer full load currents and fault level outputs. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TransformerFaultOutput(
    /** Secondary rated continuous full-load current in Amperes. */
    val secondaryFlcAmps: Double,
    /** Secondary 3-phase symmetrical bolted short-circuit fault current in kA. */
    val secondaryFaultKa: Double,
    /** Primary rated continuous full-load current in Amperes. */
    val primaryFlcAmps: Double,
    /** Primary reflected short-circuit fault current in kA. */
    val primaryFaultKa: Double,
    /** Standard reference citation. */
    val standardReference: String
)

// =============================================================================
// AppFunctions Service Implementations (System AI & Assistant Shortcuts)
// =============================================================================

/**
 * System-level AppFunctions provider exposing VoltAtlas engineering calculations.
 *
 * Safety & Architecture Rules:
 * - Rule 3 Compliance: NEVER guesses or hallucinates engineering values.
 * - Concurrency: All routines execute on [Dispatchers.IO].
 * - Precision: Inputs validated against engineering domain boundaries.
 */
class VoltAtlasAppFunctions(
    var toolRuntimeProvider: (suspend () -> AssistantToolRuntime?)? = null
) {

    companion object {
        // The hardcoded STANDARD_COPPER_SIZES and STANDARD_BREAKER_RATINGS arrays were
        // deliberately removed: they were a second engineering knowledge base inside the AI
        // layer. Standard sizes and ratings come from the shipped equipment catalogue and the
        // authoritative engines only.
    }

    /**
     * Sizing of electrical low-voltage power cables.
     *
     * Invariant: Never calculates engineering values internally.
     * Delegates to the authoritative deterministic tool dispatcher when a project and circuit
     * are provided.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculateCableSize(params: CableSizingParams): CableSizingOutput = withContext(Dispatchers.IO) {
        if (params.loadCurrentAmps <= 0.0) {
            throw AppFunctionInvalidArgumentException("Load current must be greater than 0 A.")
        }
        if (params.projectId != null && params.projectId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Project ID cannot be blank.")
        }
        if (params.circuitId != null && params.circuitId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Circuit ID cannot be blank.")
        }

        if (params.projectId != null && params.circuitId != null) {
            val runtime = toolRuntimeProvider?.invoke()
                ?: throw AppFunctionException("ENGINE_UNAVAILABLE: AssistantToolRuntime is not configured.")

            val outcome = runtime.dispatcher.execute(
                toolName = CableToolNames.CALCULATE_CABLE_SIZING,
                projectId = params.projectId,
                entityType = "circuit",
                entityId = params.circuitId,
                conversationId = null,
                arguments = emptyMap()
            )

            val result = outcome.envelope.result as? CableSizingExecutionResult
                ?: run {
                    val err = outcome.envelope.errors.firstOrNull()?.message
                        ?: "Calculation failed with status ${outcome.envelope.status}"
                    throw AppFunctionException("CALCULATION_FAILED: $err")
                }

            return@withContext CableSizingOutput(
                recommendedSizeMm2 = result.details.cableSize_mm2,
                protectiveDeviceRatingAmps = result.details.deviceSize_A,
                izCapacityAmps = result.details.capacity_derated_Iz_A,
                voltageDropPct = result.details.vdPct,
                voltageDropVolts = result.details.voltDrop_V,
                status = result.overallStatus.name,
                standardReference = "IEC 60364-5-52"
            )
        }

        if (params.voltageV == null || params.cableLengthMeters == null || params.maxVdPct == null) {
            throw AppFunctionInvalidArgumentException(
                "System voltage, cable length and voltage-drop limit are required; " +
                    "they are never assumed."
            )
        }
        throw AppFunctionException(
            "ENGINE_UNAVAILABLE: cable sizing must be executed by the authoritative " +
                "VoltAtlas CableSizingEngine through the engineering tool registry. " +
                "This wrapper never computes a conductor size itself."
        )
    }

    /**
     * Executes authoritative cable sizing via the registered deterministic tool runtime
     * and produces an [EngineeringEvidenceRecord].
     *
     * Invariant: Read-only operation. Zero Room mutations, zero revision changes.
     */
    suspend fun executeCableSizing(
        projectId: String,
        circuitId: String,
        conversationId: String? = null
    ): Pair<ToolExecutionOutcome, EngineeringEvidenceRecord> = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Project ID cannot be blank.")
        }
        if (circuitId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Circuit ID cannot be blank.")
        }
        val runtime = toolRuntimeProvider?.invoke()
            ?: throw AppFunctionException("ENGINE_UNAVAILABLE: AssistantToolRuntime is not configured.")

        val outcome = runtime.dispatcher.execute(
            toolName = CableToolNames.CALCULATE_CABLE_SIZING,
            projectId = projectId,
            entityType = "circuit",
            entityId = circuitId,
            conversationId = conversationId,
            arguments = emptyMap()
        )
        val evidence = EngineeringEvidenceRecord.fromOutcome(
            outcome = outcome,
            conversationId = conversationId,
            entityType = "CIRCUIT",
            entityId = circuitId
        )
        Pair(outcome, evidence)
    }

    /**
     * Executes an in-memory cable what-if calculation via the registered deterministic tool runtime.
     *
     * Invariant: In-memory only. Zero Room mutations, zero revision changes.
     */
    suspend fun executeCableWhatIf(
        projectId: String,
        circuitId: String,
        overrides: Map<String, Any?>,
        conversationId: String? = null
    ): ToolExecutionOutcome = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Project ID cannot be blank.")
        }
        if (circuitId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Circuit ID cannot be blank.")
        }
        val runtime = toolRuntimeProvider?.invoke()
            ?: throw AppFunctionException("ENGINE_UNAVAILABLE: AssistantToolRuntime is not configured.")

        runtime.dispatcher.execute(
            toolName = CableToolNames.RUN_WHAT_IF,
            projectId = projectId,
            entityType = "circuit",
            entityId = circuitId,
            conversationId = conversationId,
            arguments = mapOf("overrides" to overrides)
        )
    }

    /**
     * Executes deterministic candidate comparison via the registered deterministic tool runtime.
     *
     * Invariant: In-memory only. Zero Room mutations, zero revision changes.
     */
    suspend fun executeCandidateComparison(
        projectId: String,
        circuitId: String,
        candidates: List<Map<String, Any?>>,
        sortPolicy: String? = null,
        conversationId: String? = null
    ): ToolExecutionOutcome = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Project ID cannot be blank.")
        }
        if (circuitId.isBlank()) {
            throw AppFunctionInvalidArgumentException("Circuit ID cannot be blank.")
        }
        if (candidates.isEmpty()) {
            throw AppFunctionInvalidArgumentException("Candidates list cannot be empty.")
        }
        val runtime = toolRuntimeProvider?.invoke()
            ?: throw AppFunctionException("ENGINE_UNAVAILABLE: AssistantToolRuntime is not configured.")

        val args = mutableMapOf<String, Any?>("candidates" to candidates)
        if (sortPolicy != null) {
            args["sortPolicy"] = sortPolicy
        }
        runtime.dispatcher.execute(
            toolName = CableToolNames.COMPARE_CABLE_CANDIDATES,
            projectId = projectId,
            entityType = "circuit",
            entityId = circuitId,
            conversationId = conversationId,
            arguments = args
        )
    }

    /**
     * Sizing of induction motor starters and breaker protection.
     *
     * The duplicated starter formulas (FLC derivation, starter-method thresholds and inrush
     * multipliers) were removed: they are a second engineering implementation. Results must
     * come from the authoritative motor/protection engine.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculateMotorStarter(params: MotorStarterParams): MotorStarterOutput = withContext(Dispatchers.IO) {
        if (params.motorPowerKw <= 0.0) {
            throw AppFunctionInvalidArgumentException("Motor power must be greater than 0 kW.")
        }
        if (params.lineVoltageV == null || params.powerFactor == null ||
            params.efficiencyPct == null || params.isThreePhase == null
        ) {
            throw AppFunctionInvalidArgumentException(
                "Voltage, power factor, efficiency and phase configuration are required; " +
                    "they are never assumed."
            )
        }
        throw AppFunctionException(
            "ENGINE_UNAVAILABLE: motor starter sizing must be executed by the authoritative " +
                "VoltAtlas engine through the engineering tool registry. This wrapper never " +
                "computes full-load current, inrush or starter method itself."
        )
    }

    /**
     * Sizing of Power Factor Correction (PFC) capacitor banks.
     *
     * The duplicated PFC formula (tan(phi1)-tan(phi2) plus commercial step rounding) was
     * removed: results must come from the authoritative PFC engine.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculatePowerFactorCorrection(params: PfcParams): PfcOutput = withContext(Dispatchers.IO) {
        if (params.activePowerKw <= 0.0) {
            throw AppFunctionInvalidArgumentException("Active power must be greater than 0 kW.")
        }
        if (params.initialPowerFactor == null || params.targetPowerFactor == null || params.lineVoltageV == null) {
            throw AppFunctionInvalidArgumentException(
                "Initial power factor, target power factor and line voltage are required; " +
                    "they are never assumed."
            )
        }
        if (params.targetPowerFactor <= params.initialPowerFactor) {
            throw AppFunctionInvalidArgumentException("Target power factor must exceed the initial power factor.")
        }
        throw AppFunctionException(
            "ENGINE_UNAVAILABLE: power factor correction must be executed by the authoritative " +
                "VoltAtlas engine through the engineering tool registry. This wrapper never " +
                "computes required kvar itself."
        )
    }

    /**
     * Sizing of Transformer short-circuit fault levels and rated currents.
     *
     * The duplicated IEC 60909 formulas were removed: results must come from the
     * authoritative short-circuit engine.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculateTransformerShortCircuit(params: TransformerFaultParams): TransformerFaultOutput = withContext(Dispatchers.IO) {
        if (params.transformerRatingKva <= 0.0) {
            throw AppFunctionInvalidArgumentException("Transformer rating must be greater than 0 kVA.")
        }
        if (params.primaryVoltageV == null || params.secondaryVoltageV == null ||
            params.impedancePercentZ == null
        ) {
            throw AppFunctionInvalidArgumentException(
                "Primary voltage, secondary voltage and impedance are required; they are never assumed."
            )
        }
        throw AppFunctionException(
            "ENGINE_UNAVAILABLE: transformer fault levels must be executed by the authoritative " +
                "VoltAtlas short-circuit engine through the engineering tool registry. This " +
                "wrapper never computes fault current itself."
        )
    }
}
