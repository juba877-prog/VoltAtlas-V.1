package com.example

import com.example.engine.converters.CapacitorConnectionType
import com.example.engine.converters.ElectricalConverters
import org.junit.Assert.*
import org.junit.Test

class ElectricalConvertersTest {

    @Test
    fun testAwgToMm2TenGauge() {
        val res = ElectricalConverters.awgToMm2(10.0)
        assertEquals(5.261, res.trueAreaMm2, 0.05)
        assertEquals(6.0, res.nearestNominalMm2, 0.01)
        assertEquals(6.0, res.safeUpNominalMm2, 0.01)
    }

    @Test
    fun testAwgToMm2FourZero() {
        // 4/0 AWG corresponds to gauge number -3
        val res = ElectricalConverters.awgToMm2(-3.0)
        assertEquals(107.22, res.trueAreaMm2, 0.5)
        assertEquals(95.0, res.nearestNominalMm2, 0.01)
        assertEquals(120.0, res.safeUpNominalMm2, 0.01)
    }

    @Test
    fun testKcmilLargeConductorSafeUpEightHundred() {
        // 1250 kcmil = 1250 * 0.506707 = 633.38 mm²
        // Must return safeUp = 800 mm²
        val res = ElectricalConverters.kcmilToMm2(1250.0)
        assertEquals(633.38, res.trueAreaMm2, 1.0)
        assertEquals(630.0, res.nearestNominalMm2, 0.01)
        assertEquals(800.0, res.safeUpNominalMm2, 0.01)
    }

    @Test
    fun testMm2ToAwgReverseConversion() {
        assertEquals("10 AWG", ElectricalConverters.mm2ToAwg(5.261))
        assertEquals("4/0 AWG", ElectricalConverters.mm2ToAwg(107.2))
        assertEquals("1/0 AWG", ElectricalConverters.mm2ToAwg(53.5))
        assertEquals("Invalid", ElectricalConverters.mm2ToAwg(-5.0))
    }

    @Test
    fun testCapacitorUfToKvarDeltaIsThreeTimesStar() {
        val starKvar = ElectricalConverters.microFaradToKvar(
            uf = 100.0,
            voltageV = 400.0,
            freqHz = 50.0,
            connection = CapacitorConnectionType.SINGLE_OR_STAR
        )
        val deltaKvar = ElectricalConverters.microFaradToKvar(
            uf = 100.0,
            voltageV = 400.0,
            freqHz = 50.0,
            connection = CapacitorConnectionType.DELTA
        )

        assertEquals(deltaKvar, 3.0 * starKvar, 1e-4)
    }

    @Test
    fun testKvarToMicroFaradIsInverseOfUfToKvar() {
        val originalUf = 150.0
        val voltage = 400.0
        val freq = 50.0

        val kvar = ElectricalConverters.microFaradToKvar(originalUf, voltage, freq, CapacitorConnectionType.DELTA)
        val calculatedUf = ElectricalConverters.kvarToMicroFarad(kvar, voltage, freq, CapacitorConnectionType.DELTA)

        assertEquals(originalUf, calculatedUf, 1e-4)
    }

    @Test
    fun testHpToKwAndKwToHp() {
        val hpMech = 10.0
        val kwMech = ElectricalConverters.hpToKw(hpMech, isMetric = false)
        assertEquals(7.457, kwMech, 0.005)

        val backHp = ElectricalConverters.kwToHp(kwMech, isMetric = false)
        assertEquals(hpMech, backHp, 1e-4)

        val hpMetric = 10.0
        val kwMetric = ElectricalConverters.hpToKw(hpMetric, isMetric = true)
        assertEquals(7.355, kwMetric, 0.005)
    }

    @Test
    fun testFreqToRpmAndTorque() {
        val rpm = ElectricalConverters.freqToRpm(50.0, 4)
        assertEquals(1500.0, rpm, 0.01)

        val torque = ElectricalConverters.torqueFromKwRpm(15.0, 1450.0)
        assertEquals(98.785, torque, 0.05)
    }

    // =========================================================================
    // NEW 6 CONVERTER TESTS (User Reference Values)
    // =========================================================================

    @Test
    fun testPowerTriangle() {
        // S = 100 kVA, cosφ = 0.8 -> P = 80 kW, Q = 60 kVAR
        val res = ElectricalConverters.powerTriangleFromS(100.0, 0.8)
        assertEquals(80.0, res.pKw, 0.01)
        assertEquals(60.0, res.qKvar, 0.01)
        assertEquals(100.0, res.sKva, 0.01)
        assertEquals(0.8, res.cosPhi, 0.01)
        assertEquals(0.6, res.sinPhi, 0.01)

        // Inverse test from P & Q
        val invRes = ElectricalConverters.powerTriangleFromPQ(80.0, 60.0)
        assertEquals(100.0, invRes.sKva, 0.01)
        assertEquals(0.8, invRes.cosPhi, 0.01)
    }

    @Test
    fun testCurrentToPower3Phase() {
        // 100A, 400V 3P, cosφ = 0.8 -> P = 55.425 kW, S = 69.282 kVA
        val res = ElectricalConverters.currentToPower(100.0, 400.0, 0.8, isThreePhase = true)
        assertEquals(55.425, res.pKw, 0.05)
        assertEquals(69.282, res.sKva, 0.05)

        val invRes = ElectricalConverters.powerToCurrent(55.4256, 400.0, 0.8, isThreePhase = true)
        assertEquals(100.0, invRes.amps, 0.1)
    }

    @Test
    fun testPowerFactorCorrection() {
        // P = 100 kW, 0.8 -> 0.95 -> Qc ≈ 42.1 kVAR
        val res = ElectricalConverters.powerFactorCorrection(100.0, 0.8, 0.95, 400.0)
        assertEquals(42.13, res.qcKvar, 0.1)
        assertTrue(res.deltaAmps > 0)
    }

    @Test
    fun testCableVoltageDropCalculator() {
        // R = 0.727 Ω/km, X = 0.08 Ω/km, I = 100A, L = 50m, cosφ = 0.8, 400V 3P
        // mV/A/m = √3 * (0.727 * 0.8 + 0.08 * 0.6) = 1.732 * (0.5816 + 0.048) = 1.090 mV/A/m
        val res = ElectricalConverters.cableVoltageDrop(0.727, 0.08, 100.0, 50.0, 0.8, true, 400.0)
        assertTrue(res.mVperAm > 1.0)
        assertTrue(res.vDrop > 0.0)
        assertTrue(res.vDropPct > 0.0)
    }

    @Test
    fun testBatteryCapacity() {
        // 100 Ah, 48 Vdc, DOD = 0.8, η = 0.9 -> 3.456 kWh
        val res = ElectricalConverters.batteryAhToKwh(100.0, 48.0, 0.8, 0.9)
        assertEquals(3.456, res.eKwh, 0.001)

        val invRes = ElectricalConverters.batteryKwhToAh(3.456, 48.0, 0.8, 0.9)
        assertEquals(100.0, invRes.ahRequired, 0.01)
    }

    @Test
    fun testTransformerQuickShortCircuit() {
        // 1000 kVA, %Z = 5%, 400V -> IFLC = 1443.4A, Isc = 28.87 kA
        val res = ElectricalConverters.txQuickShortCircuit(1000.0, 5.0, 400.0)
        assertEquals(1443.38, res.iFlcAmps, 0.5)
        assertEquals(28.87, res.iScKa, 0.05)
    }
}
