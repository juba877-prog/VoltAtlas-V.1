package com.example.ai.appfunctions

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
    /** Design load operating current in Amperes. */
    val loadCurrentAmps: Double = 0.0,
    /** Rated phase-to-phase system voltage in Volts (e.g. 400.0 V or 230.0 V). */
    val voltageV: Double = 400.0,
    /** One-way physical route length of the cable in meters. */
    val cableLengthMeters: Double = 30.0,
    /** Installation reference method per IEC 60364-5-52 (e.g. 'C' for perforated tray, 'D' for underground conduit). */
    val installationMethod: String = "C",
    /** Design ambient temperature in degrees Celsius (default 30°C for air). */
    val ambientTempC: Int = 30,
    /** Operating load power factor cos φ (between 0.1 and 1.0). */
    val powerFactor: Double = 0.85,
    /** True if 3-phase 4-wire system; false for single phase 230V. */
    val isThreePhase: Boolean = true,
    /** Maximum acceptable voltage drop percentage (default 3.0% for lighting/normal, 5.0% for power). */
    val maxVdPct: Double = 4.0
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
    /** Rated operational voltage in Volts. */
    val lineVoltageV: Double = 400.0,
    /** Rated full load power factor cos φ. */
    val powerFactor: Double = 0.85,
    /** Motor efficiency percentage at full load (e.g. 91.5 for IE3). */
    val efficiencyPct: Double = 91.0,
    /** True for 3-phase motor, false for single-phase. */
    val isThreePhase: Boolean = true,
    /** Preferred starting topology (optional: 'AUTO', 'DOL', 'STAR_DELTA', 'SOFT_STARTER', 'VFD'). */
    val preferredTopology: String = "AUTO"
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
    /** Existing uncompensated power factor cos φ1. */
    val initialPowerFactor: Double = 0.78,
    /** Desired target power factor cos φ2 (typical 0.95 or 0.98). */
    val targetPowerFactor: Double = 0.95,
    /** Line voltage in Volts (e.g. 400.0 V). */
    val lineVoltageV: Double = 400.0
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
    /** Primary MV voltage in Volts (e.g. 11000.0 V or 22000.0 V). */
    val primaryVoltageV: Double = 11000.0,
    /** Secondary LV voltage in Volts (e.g. 400.0 V). */
    val secondaryVoltageV: Double = 400.0,
    /** Percentage short-circuit impedance %Z (or Uk%). */
    val impedancePercentZ: Double = 5.0
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
class VoltAtlasAppFunctions {

    companion object {
        val STANDARD_COPPER_SIZES = listOf(
            1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0,
            95.0, 120.0, 150.0, 185.0, 240.0, 300.0, 400.0, 500.0
        )
        val STANDARD_BREAKER_RATINGS = listOf(
            10.0, 16.0, 20.0, 25.0, 32.0, 40.0, 50.0, 63.0, 80.0, 100.0,
            125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0
        )
    }

    /**
     * Sizing of electrical low-voltage power cables per IEC 60364-5-52.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculateCableSize(params: CableSizingParams): CableSizingOutput = withContext(Dispatchers.IO) {
        if (params.loadCurrentAmps <= 0.0) {
            throw AppFunctionInvalidArgumentException("Load current must be greater than 0 A.")
        }
        if (params.cableLengthMeters <= 0.0) {
            throw AppFunctionInvalidArgumentException("Cable length must be greater than 0 meters.")
        }

        // 1. Minimum protective device rating In >= Ib
        val breakerIn = STANDARD_BREAKER_RATINGS.firstOrNull { it >= params.loadCurrentAmps } ?: 800.0

        // 2. Temperature and grouping derating
        val tempDerating = when {
            params.ambientTempC <= 30 -> 1.0
            params.ambientTempC <= 35 -> 0.96
            params.ambientTempC <= 40 -> 0.91
            params.ambientTempC <= 45 -> 0.87
            params.ambientTempC <= 50 -> 0.82
            else -> 0.76
        }
        val totalDerating = tempDerating * 0.90 // Include multi-circuit grouping factor
        val requiredIz = breakerIn / totalDerating

        // 3. Selection based on ampacity (IEC 60364-5-52 Table 4E4A Cu XLPE in air)
        val (sizeMm2, nominalIz) = when {
            requiredIz <= 24.0 -> Pair(2.5, 31.0)
            requiredIz <= 32.0 -> Pair(4.0, 42.0)
            requiredIz <= 42.0 -> Pair(6.0, 54.0)
            requiredIz <= 60.0 -> Pair(10.0, 75.0)
            requiredIz <= 80.0 -> Pair(16.0, 100.0)
            requiredIz <= 105.0 -> Pair(25.0, 133.0)
            requiredIz <= 130.0 -> Pair(35.0, 164.0)
            requiredIz <= 160.0 -> Pair(50.0, 198.0)
            requiredIz <= 200.0 -> Pair(70.0, 253.0)
            requiredIz <= 245.0 -> Pair(95.0, 306.0)
            requiredIz <= 285.0 -> Pair(120.0, 354.0)
            requiredIz <= 330.0 -> Pair(150.0, 407.0)
            requiredIz <= 380.0 -> Pair(185.0, 464.0)
            requiredIz <= 445.0 -> Pair(240.0, 546.0)
            else -> Pair(300.0, 628.0)
        }

        val actualIz = nominalIz * totalDerating

        // 4. Voltage drop check
        // mV/A/m approximation for Cu XLPE
        val mvPerAmpMeter = when (sizeMm2) {
            2.5 -> 18.0
            4.0 -> 11.0
            6.0 -> 7.3
            10.0 -> 4.4
            16.0 -> 2.8
            25.0 -> 1.75
            35.0 -> 1.25
            50.0 -> 0.93
            70.0 -> 0.63
            95.0 -> 0.46
            120.0 -> 0.38
            150.0 -> 0.31
            185.0 -> 0.26
            240.0 -> 0.21
            else -> 0.18
        }

        val vDropV = (mvPerAmpMeter * params.loadCurrentAmps * params.cableLengthMeters) / 1000.0
        val vDropPct = (vDropV / params.voltageV) * 100.0
        val isCompliant = vDropPct <= params.maxVdPct && actualIz >= breakerIn

        return@withContext CableSizingOutput(
            recommendedSizeMm2 = "${sizeMm2.toInt()} mm²",
            protectiveDeviceRatingAmps = breakerIn,
            izCapacityAmps = String.format("%.1f", actualIz).toDouble(),
            voltageDropPct = String.format("%.2f", vDropPct).toDouble(),
            voltageDropVolts = String.format("%.2f", vDropV).toDouble(),
            status = if (isCompliant) "PASS" else "FAIL",
            standardReference = "IEC 60364-5-52 / BS 7671 Table 4E4A"
        )
    }

    /**
     * Sizing of induction motor starters and breaker protection per IEC 60034 / IEC 60947.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculateMotorStarter(params: MotorStarterParams): MotorStarterOutput = withContext(Dispatchers.IO) {
        if (params.motorPowerKw <= 0.0) {
            throw AppFunctionInvalidArgumentException("Motor power must be greater than 0 kW.")
        }
        val eff = (params.efficiencyPct / 100.0).coerceIn(0.5, 0.99)
        val pf = params.powerFactor.coerceIn(0.5, 1.0)

        val flc = if (params.isThreePhase) {
            (params.motorPowerKw * 1000.0) / (sqrt(3.0) * params.lineVoltageV * pf * eff)
        } else {
            (params.motorPowerKw * 1000.0) / (params.lineVoltageV * pf * eff)
        }

        val method = if (params.preferredTopology != "AUTO") {
            params.preferredTopology
        } else {
            when {
                params.motorPowerKw <= 5.5 -> "Direct-On-Line (DOL)"
                params.motorPowerKw <= 45.0 -> "Star-Delta (Y-Δ)"
                params.motorPowerKw <= 110.0 -> "Soft Starter"
                else -> "Variable Frequency Drive (VFD)"
            }
        }

        val inrushMultiplier = when {
            method.contains("DOL") || method.contains("Direct") -> 6.5
            method.contains("Star") -> 2.3
            method.contains("Soft") -> 1.8
            else -> 1.2
        }

        val startingCurrent = flc * inrushMultiplier
        val contactorRating = flc * 1.15
        val breakerRating = STANDARD_BREAKER_RATINGS.firstOrNull { it >= flc * 1.25 } ?: (flc * 1.25)
        val overloadSetting = flc

        return@withContext MotorStarterOutput(
            flcAmps = String.format("%.1f", flc).toDouble(),
            startingCurrentAmps = String.format("%.1f", startingCurrent).toDouble(),
            recommendedMethod = method,
            contactorRatingAmps = String.format("%.1f", contactorRating).toDouble(),
            breakerRatingAmps = breakerRating,
            thermalOverloadSettingAmps = String.format("%.1f", overloadSetting).toDouble(),
            standardReference = "IEC 60034-1 / IEC 60947-4-1"
        )
    }

    /**
     * Sizing of Power Factor Correction (PFC) capacitor banks per IEC 60831.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculatePowerFactorCorrection(params: PfcParams): PfcOutput = withContext(Dispatchers.IO) {
        if (params.activePowerKw <= 0.0) {
            throw AppFunctionInvalidArgumentException("Active power must be greater than 0 kW.")
        }
        if (params.initialPowerFactor <= 0.0 || params.initialPowerFactor >= 1.0) {
            throw AppFunctionInvalidArgumentException("Initial power factor must be between 0.1 and 0.99.")
        }
        if (params.targetPowerFactor <= params.initialPowerFactor || params.targetPowerFactor > 1.0) {
            throw AppFunctionInvalidArgumentException("Target power factor must be greater than initial power factor and <= 1.0.")
        }

        val phi1 = acos(params.initialPowerFactor)
        val phi2 = acos(params.targetPowerFactor)
        val requiredKvar = params.activePowerKw * (tan(phi1) - tan(phi2))

        val currentBefore = (params.activePowerKw * 1000.0) / (sqrt(3.0) * params.lineVoltageV * params.initialPowerFactor)
        val currentAfter = (params.activePowerKw * 1000.0) / (sqrt(3.0) * params.lineVoltageV * params.targetPowerFactor)
        val currentReduction = ((currentBefore - currentAfter) / currentBefore) * 100.0

        val kvaBefore = params.activePowerKw / params.initialPowerFactor
        val kvaAfter = params.activePowerKw / params.targetPowerFactor
        val kvaSaved = kvaBefore - kvaAfter

        // Step size rounded to nearest 5 kvar commercial rating
        val commercialKvar = kotlin.math.ceil(requiredKvar / 5.0) * 5.0

        return@withContext PfcOutput(
            requiredKvar = String.format("%.1f", requiredKvar).toDouble(),
            recommendedBankKvar = commercialKvar,
            currentBeforeAmps = String.format("%.1f", currentBefore).toDouble(),
            currentAfterAmps = String.format("%.1f", currentAfter).toDouble(),
            kvaDemandSaved = String.format("%.1f", kvaSaved).toDouble(),
            currentReductionPct = String.format("%.1f", currentReduction).toDouble(),
            standardReference = "IEC 60831-1 / IEEE 18"
        )
    }

    /**
     * Sizing of Transformer short-circuit fault levels and rated currents per IEC 60909.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculateTransformerShortCircuit(params: TransformerFaultParams): TransformerFaultOutput = withContext(Dispatchers.IO) {
        if (params.transformerRatingKva <= 0.0) {
            throw AppFunctionInvalidArgumentException("Transformer rating must be greater than 0 kVA.")
        }
        if (params.impedancePercentZ <= 0.0) {
            throw AppFunctionInvalidArgumentException("Transformer impedance %Z must be greater than 0%.")
        }

        val secFlc = (params.transformerRatingKva * 1000.0) / (sqrt(3.0) * params.secondaryVoltageV)
        val secFaultCurrentA = secFlc / (params.impedancePercentZ / 100.0)
        val secFaultKa = secFaultCurrentA / 1000.0

        val priFlc = (params.transformerRatingKva * 1000.0) / (sqrt(3.0) * params.primaryVoltageV)
        val priFaultCurrentA = priFlc / (params.impedancePercentZ / 100.0)
        val priFaultKa = priFaultCurrentA / 1000.0

        return@withContext TransformerFaultOutput(
            secondaryFlcAmps = String.format("%.1f", secFlc).toDouble(),
            secondaryFaultKa = String.format("%.2f", secFaultKa).toDouble(),
            primaryFlcAmps = String.format("%.1f", priFlc).toDouble(),
            primaryFaultKa = String.format("%.2f", priFaultKa).toDouble(),
            standardReference = "IEC 60909-0 / IEC 60076-5"
        )
    }
}
