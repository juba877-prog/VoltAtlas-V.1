package com.example.ai.appfunctions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoltAtlasAppFunctionsTest {

    private val appFunctions = VoltAtlasAppFunctions()

    @Test
    fun testCalculateCableSize_validParameters_producesCompliantResult() = runBlocking {
        val params = CableSizingParams(
            loadCurrentAmps = 52.0,
            voltageV = 400.0,
            cableLengthMeters = 45.0,
            installationMethod = "C",
            ambientTempC = 35,
            powerFactor = 0.85,
            isThreePhase = true,
            maxVdPct = 4.0
        )

        val output = appFunctions.calculateCableSize(params)

        assertEquals("PASS", output.status)
        assertTrue(output.protectiveDeviceRatingAmps >= 52.0)
        assertTrue(output.izCapacityAmps >= output.protectiveDeviceRatingAmps)
        assertTrue(output.voltageDropPct <= 4.0)
        assertTrue(output.recommendedSizeMm2.contains("mm²"))
        assertTrue(output.standardReference.contains("IEC 60364-5-52"))
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun testCalculateCableSize_zeroLoadCurrent_throwsException() = runBlocking {
        val invalidParams = CableSizingParams(loadCurrentAmps = 0.0)
        appFunctions.calculateCableSize(invalidParams)
        Unit
    }

    @Test
    fun testCalculateMotorStarter_dolSmallMotor_recommendsDol() = runBlocking {
        val params = MotorStarterParams(
            motorPowerKw = 4.0,
            lineVoltageV = 400.0,
            powerFactor = 0.84,
            efficiencyPct = 88.0,
            isThreePhase = true
        )

        val output = appFunctions.calculateMotorStarter(params)

        assertTrue("Small motor should recommend DOL", output.recommendedMethod.contains("DOL"))
        assertTrue(output.flcAmps in 7.0..10.0)
        assertTrue(output.startingCurrentAmps > output.flcAmps * 5.0)
        assertTrue(output.breakerRatingAmps >= output.flcAmps)
        assertTrue(output.standardReference.contains("IEC 60034-1"))
    }

    @Test
    fun testCalculateMotorStarter_mediumMotor_recommendsStarDelta() = runBlocking {
        val params = MotorStarterParams(
            motorPowerKw = 30.0,
            lineVoltageV = 400.0,
            powerFactor = 0.86,
            efficiencyPct = 92.0,
            isThreePhase = true
        )

        val output = appFunctions.calculateMotorStarter(params)

        assertTrue("30 kW motor should recommend Star-Delta", output.recommendedMethod.contains("Star-Delta"))
        assertTrue(output.flcAmps in 50.0..65.0)
        assertTrue(output.contactorRatingAmps > output.flcAmps)
    }

    @Test
    fun testCalculatePowerFactorCorrection_validParams_calculatesCompensation() = runBlocking {
        val params = PfcParams(
            activePowerKw = 200.0,
            initialPowerFactor = 0.75,
            targetPowerFactor = 0.96,
            lineVoltageV = 400.0
        )

        val output = appFunctions.calculatePowerFactorCorrection(params)

        assertTrue(output.requiredKvar in 110.0..130.0)
        assertTrue(output.recommendedBankKvar >= output.requiredKvar)
        assertTrue(output.currentAfterAmps < output.currentBeforeAmps)
        assertTrue(output.currentReductionPct > 15.0)
        assertTrue(output.standardReference.contains("IEC 60831-1"))
    }

    @Test
    fun testCalculateTransformerShortCircuit_1000Kva_calculatesFaultKa() = runBlocking {
        val params = TransformerFaultParams(
            transformerRatingKva = 1000.0,
            primaryVoltageV = 11000.0,
            secondaryVoltageV = 400.0,
            impedancePercentZ = 5.0
        )

        val output = appFunctions.calculateTransformerShortCircuit(params)

        // 1000 kVA @ 400V FLC is ~1443.4 A, at 5% Z, fault is 1443.4 / 0.05 = ~28.87 kA
        assertTrue("Secondary FLC should be ~1443 A", output.secondaryFlcAmps in 1440.0..1450.0)
        assertTrue("Secondary fault should be ~28.8 kA", output.secondaryFaultKa in 28.0..29.5)
        assertTrue(output.primaryFlcAmps in 50.0..55.0)
        assertTrue(output.standardReference.contains("IEC 60909"))
    }
}
