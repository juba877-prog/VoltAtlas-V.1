package com.example.camera.nameplate

import org.junit.Assert.*
import org.junit.Test

class NameplateParserTest {

    @Test
    fun testParseSiemensMotorNameplate() {
        val sampleOcr = """
            SIEMENS SIMOTICS GP
            3~ Mot. 1LE1001-1DB43-4AA4
            IEC/EN 60034-1
            Rating: 45 kW
            V: 400 V  Hz: 50 Hz
            I: 82.0 A
            cos φ: 0.86  η: 94.2 %
            1475 RPM
            IP55  S1  IM B3
        """.trimIndent()

        val result = NameplateParser.parse(sampleOcr)

        assertEquals(NameplateEquipmentType.MOTOR, result.equipmentType)
        assertEquals(45.0, result.powerKw ?: 0.0, 0.01)
        assertEquals(400.0, result.voltageV ?: 0.0, 0.01)
        assertEquals(82.0, result.currentAmps ?: 0.0, 0.01)
        assertEquals(0.86, result.powerFactor ?: 0.0, 0.01)
        assertEquals(94.2, result.efficiencyPct ?: 0.0, 0.01)
        assertEquals(1475, result.rpm)
        assertEquals(50.0, result.frequencyHz ?: 0.0, 0.01)
        assertTrue(result.confidenceScore >= 0.8f)
    }

    @Test
    fun testParseNemaHpMotorNameplate() {
        val sampleOcr = """
            BALDOR RELIANCE
            INDUSTRIAL MOTOR
            CAT NO. EM3710T
            SPEC: 07H057X206G1
            HP: 10.0
            VOLTS: 460
            FLA: 13.4 A
            RPM: 1765  HZ: 60
            PF: 0.82  EFF: 91.7%
        """.trimIndent()

        val result = NameplateParser.parse(sampleOcr)

        assertEquals(NameplateEquipmentType.MOTOR, result.equipmentType)
        assertEquals(10.0, result.powerHp ?: 0.0, 0.01)
        // 10 HP * 0.746 = 7.46 kW
        assertEquals(7.46, result.powerKw ?: 0.0, 0.01)
        assertEquals(460.0, result.voltageV ?: 0.0, 0.01)
        assertEquals(13.4, result.currentAmps ?: 0.0, 0.01)
        assertEquals(1765, result.rpm)
        assertEquals(60.0, result.frequencyHz ?: 0.0, 0.01)
        assertEquals(0.82, result.powerFactor ?: 0.0, 0.01)
        assertEquals(91.7, result.efficiencyPct ?: 0.0, 0.01)
    }

    @Test
    fun testParseTransformerNameplate() {
        val sampleOcr = """
            SCHNEIDER ELECTRIC
            TRIHAL CAST RESIN TRANSFORMER
            IEC 60076-11
            RATED POWER: 1600 kVA
            VOLTAGE: 11000 / 400 V
            FREQUENCY: 50 Hz
            IMPEDANCE Uk: 6.0 %
            VECTOR GROUP: Dyn11
            WEIGHT: 4200 kg
        """.trimIndent()

        val result = NameplateParser.parse(sampleOcr)

        assertEquals(NameplateEquipmentType.TRANSFORMER, result.equipmentType)
        assertEquals(1600.0, result.kvaRating ?: 0.0, 0.01)
        assertEquals(6.0, result.impedancePercentZ ?: 0.0, 0.01)
        assertEquals(50.0, result.frequencyHz ?: 0.0, 0.01)
        assertTrue(result.confidenceScore >= 0.4f)
    }

    @Test
    fun testNameplateAnalyzer_frameDebouncing() = kotlinx.coroutines.test.runTest {
        var callbackResult: NameplateScanResult? = null
        val analyzer = NameplateAnalyzer(parentScope = this) {
            callbackResult = it
        }

        val frame1 = "SIEMENS MOTOR 30 kW 400V FLA: 55A"
        analyzer.processTextFrame(frame1)
        testScheduler.advanceUntilIdle()

        assertNotNull(callbackResult)
        assertEquals(NameplateEquipmentType.MOTOR, callbackResult?.equipmentType)
        assertEquals(30.0, callbackResult?.powerKw ?: 0.0, 0.01)
        analyzer.close()
    }

    @Test
    fun testNameplateAnalyzer_cancellationStopsBackgroundProcessing() = kotlinx.coroutines.test.runTest {
        var callCount = 0
        val analyzer = NameplateAnalyzer(parentScope = this) {
            callCount++
        }
        analyzer.close()
        analyzer.processTextFrame("SIEMENS MOTOR 30 kW 400V FLA: 55A")
        testScheduler.advanceUntilIdle()
        assertEquals(0, callCount)
    }
}
