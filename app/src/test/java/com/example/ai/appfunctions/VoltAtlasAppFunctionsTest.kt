package com.example.ai.appfunctions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * AppFunctions are transport wrappers only.
 *
 * These tests previously asserted values produced by duplicated local formulas (an ampacity
 * table, an mV/A/m table, PFC kvar math and IEC 60909 fault math). That was a second
 * engineering implementation inside the AI layer. The wrappers now refuse to compute and must
 * report that the authoritative engine is required, while still rejecting malformed inputs.
 */
class VoltAtlasAppFunctionsTest {

    private val appFunctions = VoltAtlasAppFunctions()

    private fun assertRequiresAuthoritativeEngine(block: suspend () -> Any?) = runBlocking {
        val error = assertThrows(AppFunctionException::class.java) {
            runBlocking { block() }
        }
        assertTrue(
            "Error must identify ENGINE_UNAVAILABLE, was: ${error.message}",
            error.message?.contains("ENGINE_UNAVAILABLE") == true
        )
    }

    @Test
    fun calculateCableSize_validParameters_requiresAuthoritativeEngine() = runBlocking {
        assertRequiresAuthoritativeEngine {
            appFunctions.calculateCableSize(
                CableSizingParams(
                    loadCurrentAmps = 52.0,
                    voltageV = 400.0,
                    cableLengthMeters = 45.0,
                    installationMethod = "C",
                    ambientTempC = 35,
                    powerFactor = 0.85,
                    isThreePhase = true,
                    maxVdPct = 4.0
                )
            )
        }
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun testCalculateCableSize_zeroLoadCurrent_throwsException() = runBlocking {
        appFunctions.calculateCableSize(CableSizingParams(loadCurrentAmps = 0.0))
        Unit
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun testCalculateCableSize_missingSystemVoltage_isRejected() = runBlocking {
        appFunctions.calculateCableSize(
            CableSizingParams(loadCurrentAmps = 52.0, cableLengthMeters = 45.0, maxVdPct = 4.0)
        )
        Unit
    }

    @Test
    fun calculateMotorStarter_requiresAuthoritativeEngine() = runBlocking {
        assertRequiresAuthoritativeEngine {
            appFunctions.calculateMotorStarter(
                MotorStarterParams(
                    motorPowerKw = 4.0,
                    lineVoltageV = 400.0,
                    powerFactor = 0.84,
                    efficiencyPct = 88.0,
                    isThreePhase = true
                )
            )
        }
    }

    @Test
    fun calculatePowerFactorCorrection_requiresAuthoritativeEngine() = runBlocking {
        assertRequiresAuthoritativeEngine {
            appFunctions.calculatePowerFactorCorrection(
                PfcParams(
                    activePowerKw = 200.0,
                    initialPowerFactor = 0.75,
                    targetPowerFactor = 0.96,
                    lineVoltageV = 400.0
                )
            )
        }
    }

    @Test
    fun calculateTransformerShortCircuit_requiresAuthoritativeEngine() = runBlocking {
        assertRequiresAuthoritativeEngine {
            appFunctions.calculateTransformerShortCircuit(
                TransformerFaultParams(
                    transformerRatingKva = 1000.0,
                    primaryVoltageV = 11000.0,
                    secondaryVoltageV = 400.0,
                    impedancePercentZ = 5.0
                )
            )
        }
    }
}
