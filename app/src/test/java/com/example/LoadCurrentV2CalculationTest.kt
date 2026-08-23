package com.example

import com.example.engine.converters.ElectricalConverters
import com.example.ui.screens.quick.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class LoadCurrentV2CalculationTest {

    @Test
    fun t01_threePhase_knownKva_calculatesAmpsDirectly_noPfRequired() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.KVA_APPARENT,
            phase = VoltageDropSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            sKvaText = "100.0",
            calculateActivePowerWithKva = false
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedAmps = (100.0 * 1000.0) / (sqrt(3.0) * 400.0) // 144.3375...
        assertEquals("I must be ~144.34 A", expectedAmps, result!!.amps, 0.01)
        assertEquals("S must be 100.0 kVA", 100.0, result.sKva, 0.001)
        assertNull("pKw must be null when PF is not requested", result.pKw)
        assertNull("powerFactor must be null when PF is not requested", result.powerFactor)
        assertFalse("isPowerFactorRequired must be false", result.isPowerFactorRequired)
        assertEquals(LoadCurrentSourceType.USER_INPUT, result.sKvaSource)
        assertEquals(LoadCurrentSourceType.DERIVED, result.ampsSource)
    }

    @Test
    fun t02_singlePhase_knownKva_calculatesAmpsDirectly_noPfRequired() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.KVA_APPARENT,
            phase = VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = 230.0,
            sKvaText = "10.0",
            calculateActivePowerWithKva = false
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedAmps = (10.0 * 1000.0) / 230.0 // 43.478...
        assertEquals("I must be ~43.48 A", expectedAmps, result!!.amps, 0.01)
        assertEquals("S must be 10.0 kVA", 10.0, result.sKva, 0.001)
        assertNull("pKw must be null", result.pKw)
    }

    @Test
    fun t03_threePhase_knownAmps_calculatesKvaDirectly_noPfRequired() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.AMPS_CURRENT,
            phase = VoltageDropSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            ampsText = "100.0",
            calculateActivePowerWithAmps = false
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedKva = (sqrt(3.0) * 400.0 * 100.0) / 1000.0 // 69.282...
        assertEquals("S must be ~69.28 kVA", expectedKva, result!!.sKva, 0.01)
        assertEquals("I must be 100.0 A", 100.0, result.amps, 0.001)
        assertNull("pKw must be null when PF is omitted", result.pKw)
        assertFalse("isPowerFactorRequired must be false", result.isPowerFactorRequired)
    }

    @Test
    fun t04_singlePhase_knownAmps_calculatesKvaDirectly_noPfRequired() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.AMPS_CURRENT,
            phase = VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = 230.0,
            ampsText = "50.0",
            calculateActivePowerWithAmps = false
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedKva = (230.0 * 50.0) / 1000.0 // 11.5 kVA
        assertEquals("S must be 11.50 kVA", expectedKva, result!!.sKva, 0.01)
        assertEquals("I must be 50.0 A", 50.0, result.amps, 0.001)
    }

    @Test
    fun t05_threePhase_knownKw_calculatesAmpsAndKva_withPf() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.KW_ACTIVE,
            phase = VoltageDropSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            pKwText = "55.0",
            powerFactorText = "0.85"
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedAmps = (55.0 * 1000.0) / (sqrt(3.0) * 400.0 * 0.85) // 93.41
        val expectedKva = 55.0 / 0.85 // 64.7058...
        assertEquals("I must be ~93.41 A", expectedAmps, result!!.amps, 0.01)
        assertEquals("S must be ~64.71 kVA", expectedKva, result.sKva, 0.01)
        assertEquals("P must be 55.0 kW", 55.0, result.pKw ?: 0.0, 0.001)
        assertTrue("isPowerFactorRequired must be true", result.isPowerFactorRequired)
    }

    @Test
    fun t06_singlePhase_knownKw_calculatesAmpsAndKva_withPf() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.KW_ACTIVE,
            phase = VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = 230.0,
            pKwText = "10.0",
            powerFactorText = "0.80"
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedAmps = (10.0 * 1000.0) / (230.0 * 0.80) // 54.3478...
        val expectedKva = 10.0 / 0.80 // 12.5 kVA
        assertEquals("I must be ~54.35 A", expectedAmps, result!!.amps, 0.01)
        assertEquals("S must be 12.50 kVA", expectedKva, result.sKva, 0.01)
    }

    @Test
    fun t07_threePhase_knownAmps_withPfSupplied_calculatesKvaAndKw() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.AMPS_CURRENT,
            phase = VoltageDropSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            ampsText = "100.0",
            calculateActivePowerWithAmps = true,
            powerFactorText = "0.80"
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedKva = (sqrt(3.0) * 400.0 * 100.0) / 1000.0 // 69.282 kVA
        val expectedKw = expectedKva * 0.80 // 55.4256 kW
        assertEquals("S must be ~69.28 kVA", expectedKva, result!!.sKva, 0.01)
        assertEquals("P must be ~55.43 kW", expectedKw, result.pKw ?: 0.0, 0.01)
    }

    @Test
    fun t08_singlePhase_knownAmps_withPfSupplied_calculatesKvaAndKw() {
        val input = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.AMPS_CURRENT,
            phase = VoltageDropSystemPhase.SINGLE_PHASE,
            voltageV = 230.0,
            ampsText = "40.0",
            calculateActivePowerWithAmps = true,
            powerFactorText = "0.90"
        )

        val result = LoadCurrentCalculationEngine.calculate(input)
        assertNotNull("Result must not be null", result)

        val expectedKva = (230.0 * 40.0) / 1000.0 // 9.2 kVA
        val expectedKw = expectedKva * 0.90 // 8.28 kW
        assertEquals("S must be 9.20 kVA", expectedKva, result!!.sKva, 0.01)
        assertEquals("P must be 8.28 kW", expectedKw, result.pKw ?: 0.0, 0.01)
    }

    @Test
    fun t09_activeToApparent_convertsCorrectly() {
        val s = ElectricalConverters.activeToApparentPower(100.0, 0.80)
        assertEquals("100 kW @ 0.8 PF = 125 kVA", 125.0, s, 0.001)
    }

    @Test
    fun t10_apparentToActive_convertsCorrectly() {
        val p = ElectricalConverters.apparentToActivePower(200.0, 0.85)
        assertEquals("200 kVA @ 0.85 PF = 170 kW", 170.0, p, 0.001)
    }

    @Test
    fun t11_invalidPf_rejectsCalculation() {
        val inputZeroPf = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.KW_ACTIVE,
            pKwText = "50.0",
            powerFactorText = "0.0"
        )
        assertNull("Zero PF must be rejected", LoadCurrentCalculationEngine.calculate(inputZeroPf))

        val inputHighPf = LoadCurrentInputState(
            knownQuantity = LoadCurrentKnownQuantity.KW_ACTIVE,
            pKwText = "50.0",
            powerFactorText = "1.05"
        )
        assertNull("PF > 1.0 must be rejected", LoadCurrentCalculationEngine.calculate(inputHighPf))
    }

    @Test
    fun t12_zeroAndNegativeVoltage_rejectsCalculation() {
        val inputZeroV = LoadCurrentInputState(voltageV = 0.0)
        assertNull("Zero voltage must return null", LoadCurrentCalculationEngine.calculate(inputZeroV))

        val inputNegV = LoadCurrentInputState(voltageV = -400.0)
        assertNull("Negative voltage must return null", LoadCurrentCalculationEngine.calculate(inputNegV))
    }

    @Test
    fun t13_negativeInputs_rejectsCalculation() {
        val negKva = LoadCurrentInputState(sKvaText = "-10.0")
        assertNull("Negative kVA must return null", LoadCurrentCalculationEngine.calculate(negKva))

        val negKw = LoadCurrentInputState(knownQuantity = LoadCurrentKnownQuantity.KW_ACTIVE, pKwText = "-10.0")
        assertNull("Negative kW must return null", LoadCurrentCalculationEngine.calculate(negKw))

        val negAmps = LoadCurrentInputState(knownQuantity = LoadCurrentKnownQuantity.AMPS_CURRENT, ampsText = "-10.0")
        assertNull("Negative Amps must return null", LoadCurrentCalculationEngine.calculate(negAmps))
    }

    @Test
    fun t14_customVoltageScaling_handles380_415_120V() {
        // 380V 3P 100 kVA -> I = 100000 / (sqrt(3) * 380) = 151.93 A
        val res380 = LoadCurrentCalculationEngine.calculate(
            LoadCurrentInputState(voltageV = 380.0, sKvaText = "100.0")
        )!!
        assertEquals(151.93, res380.amps, 0.01)

        // 415V 3P 100 kVA -> I = 100000 / (sqrt(3) * 415) = 139.12 A
        val res415 = LoadCurrentCalculationEngine.calculate(
            LoadCurrentInputState(voltageV = 415.0, sKvaText = "100.0")
        )!!
        assertEquals(139.12, res415.amps, 0.01)

        // 120V 1P 10 kVA -> I = 10000 / 120 = 83.33 A
        val res120 = LoadCurrentCalculationEngine.calculate(
            LoadCurrentInputState(phase = VoltageDropSystemPhase.SINGLE_PHASE, voltageV = 120.0, sKvaText = "10.0")
        )!!
        assertEquals(83.33, res120.amps, 0.01)
    }

    @Test
    fun t15_electricalConverters_directKvaApis_matchParity() {
        val i3P = ElectricalConverters.apparentPowerToCurrent(100.0, 400.0, isThreePhase = true)
        val s3P = ElectricalConverters.currentToApparentPower(i3P, 400.0, isThreePhase = true)
        assertEquals("Round-trip kVA -> A -> kVA must match exactly", 100.0, s3P, 0.0001)

        val i1P = ElectricalConverters.apparentPowerToCurrent(10.0, 230.0, isThreePhase = false)
        val s1P = ElectricalConverters.currentToApparentPower(i1P, 230.0, isThreePhase = false)
        assertEquals("1P Round-trip kVA -> A -> kVA must match exactly", 10.0, s1P, 0.0001)
    }
}
