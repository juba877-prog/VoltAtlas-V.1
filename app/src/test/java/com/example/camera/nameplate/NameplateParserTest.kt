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

    @Test
    fun testNameplateAnalyzer_exceptionInAnalysisPathIsSwallowedByHandler() = kotlinx.coroutines.test.runTest {
        var callbackInvoked = false
        val analyzer = NameplateAnalyzer(
            parentScope = this,
            parser = { throw IllegalArgumentException("Corrupted OCR byte buffer") },
            listener = { callbackInvoked = true }
        )

        analyzer.processTextFrame("CORRUPTED OCR FRAME CONTENT")
        testScheduler.advanceUntilIdle()

        assertFalse("Callback should never be invoked when parser throws", callbackInvoked)
        analyzer.close()
    }

    @Test
    fun testNameplateAnalyzer_cancellationExceptionTreatedAsCancellationAndNotLoggedAsFailure() = kotlinx.coroutines.test.runTest {
        var loggedThrowable: Throwable? = null
        val analyzer = NameplateAnalyzer(
            parentScope = this,
            parser = { throw kotlinx.coroutines.CancellationException("Analysis cancelled") },
            logger = { _, tr -> loggedThrowable = tr },
            listener = {}
        )

        analyzer.processTextFrame("SOME TEXT")
        testScheduler.advanceUntilIdle()

        // Normal CancellationException is handled by coroutine machinery and must never be routed to the non-fatal error logger
        assertNull("CancellationException must not be routed to the non-fatal error logger", loggedThrowable)
        analyzer.close()
    }

    @Test
    fun testParseWegMotorNameplateAndCalculateQuickStarter() {
        val sampleWeg = """
            WEG W22 Premium Efficiency
            3~ Induction Motor
            Frame: 180M  IEC 60034-1
            Power: 22 kW  (30 HP)
            Voltage: 400 V  50 Hz
            Current: 40.5 A
            PF: 0.85  Eff: 93.0%
            1465 RPM  IP55
        """.trimIndent()

        val result = NameplateParser.parse(sampleWeg)
        assertEquals(NameplateEquipmentType.MOTOR, result.equipmentType)
        assertEquals(22.0, result.powerKw ?: 0.0, 0.01)
        assertEquals(400.0, result.voltageV ?: 0.0, 0.01)
        assertEquals(40.5, result.currentAmps ?: 0.0, 0.01)
        assertEquals(0.85, result.powerFactor ?: 0.0, 0.01)
        assertEquals(93.0, result.efficiencyPct ?: 0.0, 0.01)

        val starterInput = com.example.ui.screens.quick.MotorStarterQuickInput(
            motorPowerKW = result.powerKw ?: 15.0,
            voltageV = result.voltageV ?: 400.0,
            isThreePhase = (result.voltageV ?: 400.0) >= 380.0,
            powerFactor = result.powerFactor ?: 0.85,
            efficiencyPct = result.efficiencyPct ?: 90.0,
            startingMethod = com.example.ui.screens.quick.MotorStarterType.DOL,
            tripClass = "Class 10"
        )
        val sizingResult = com.example.ui.screens.quick.QuickMotorStarterEngine.calculate(starterInput)
        assertTrue(sizingResult.fullLoadCurrent_A > 0.0)
        assertTrue(sizingResult.mainContactorKm1_A > 0.0)
    }

    @Test
    fun testParseCrownTritonNemaMotorNameplate() {
        // Simulates OCR output from a Crown Triton 150HP 460V motor nameplate
        val crownTritonOcr = """
            Crown Triton
            Premium Efficiency AC 3 Phase Motor
            150HP 6P 460V HHI150-12-447T
            Model HLS447SR0608 Encl. TEFC Amps 169.5A
            Frame 447T Duty CONT Code G Hertz 60Hz
            Type HLS Class F HD-F1 Nom. Eff. 95.8%
            Bearing Drive NU318 S.F. 1.15 RPM 1185
            Opp. 6316C3 NEMA B Amb. 40°C
            3/4 Eff. 96.0%
            Usable at 50Hz 150HP 380V 208.5A 985rpm S.F.:1.0 Eff.:94.3% Code:F
        """.trimIndent()
    
        val result = NameplateParser.parse(crownTritonOcr)
    
        assertEquals(NameplateEquipmentType.MOTOR, result.equipmentType)
        // 150 HP * 0.7457 = 111.855 kW
        assertEquals(111.86, result.powerKw ?: 0.0, 0.1)
        assertEquals(150.0, result.powerHp ?: 0.0, 0.01)
        assertEquals(460.0, result.voltageV ?: 0.0, 0.01)
        // Must pick primary 169.5A, NOT secondary 208.5A
        assertEquals(169.5, result.currentAmps ?: 0.0, 0.01)
        // Must pick primary Nom. Eff. 95.8%, NOT secondary 94.3%
        assertEquals(95.8, result.efficiencyPct ?: 0.0, 0.1)
        assertEquals(60.0, result.frequencyHz ?: 0.0, 0.01)
        assertEquals(1185, result.rpm)
        // PF should be auto-calculated: 111.855*1000 / (sqrt(3)*460*169.5*0.958) ≈ 0.86
        assertNotNull(result.powerFactor)
        assertEquals(0.86, result.powerFactor ?: 0.0, 0.02)
        assertTrue(result.confidenceScore >= 0.8f)
    }
}
