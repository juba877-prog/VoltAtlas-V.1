package com.example

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.engine.AtsEngine
import org.junit.Assert.*
import org.junit.Test

class AtsEngineTest {

    @Test
    fun testTcAts01Emergency200kVA() {
        // TC-ATS-01: emergency=200 kVA, no generator-FLC dominance
        // loadCurrent   = 200000 / (sqrt(3)*400) = 288.7 A
        // designCurrent = 288.7 * 1.25 * 1.10 = 396.9 A ; governing = 396.9 A
        // -> finalRating = 400 A ; loading = 99.2%
        val settings = AtsEntity(
            systemVoltage = 400,
            designSafetyFactor = 1.25,
            futureGrowthFactor = 10.0,
            loadCoverage = "Emergency Only"
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "EMERGENCY-PANEL", loadKVA = 200.0, atsIncluded = true)
        )

        val result = AtsEngine.calculate(
            settings = settings,
            totalConnectedLoad = 500.0,
            essentialLoadKVA = 200.0,
            generatorSelectedSize = 0.0,
            transformerFLC = 721.7,
            isc3PhaseAtTransformer = 25.0,
            circuits = circuits
        )

        assertEquals("loadCurrent match", 288.7, result.loadCurrent, 0.5)
        assertEquals("designCurrent match", 396.9, result.designCurrent, 0.5)
        assertEquals("governingCurrent match", 396.9, result.governingCurrent, 0.5)
        assertEquals("finalRating match", 400.0, result.finalRating ?: 0.0, 0.1)
        assertEquals("finalLoadingPct match", 99.2, result.finalLoadingPct ?: 0.0, 0.5)
    }

    @Test
    fun testTcAts02Emergency350kVAWithGenFLCDominance() {
        // TC-ATS-02: emergency=350 kVA, generatorFLC=550 A
        // loadCurrent = 350000 / (sqrt(3)*400) = 505.2 A
        // designCurrent = 505.2 * 1.25 * 1.10 = 694.6 A
        // governing = max(694.6, 550) = 694.6 A -> finalRating = 800 A
        val settings = AtsEntity(
            systemVoltage = 400,
            designSafetyFactor = 1.25,
            futureGrowthFactor = 10.0,
            loadCoverage = "Emergency Only"
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "EMERGENCY-PANEL", loadKVA = 350.0, atsIncluded = true)
        )

        val result = AtsEngine.calculate(
            settings = settings,
            totalConnectedLoad = 800.0,
            essentialLoadKVA = 350.0,
            generatorSelectedSize = 381.0, // generator FLC = 381*1000 / (sqrt(3)*400) = 550 A
            transformerFLC = 1443.0,
            isc3PhaseAtTransformer = 35.0,
            circuits = circuits
        )

        assertEquals("loadCurrent match", 505.2, result.loadCurrent, 0.5)
        assertEquals("designCurrent match", 694.6, result.designCurrent, 0.5)
        assertEquals("governingCurrent match", 694.6, result.governingCurrent, 0.5)
        assertEquals("finalRating match", 800.0, result.finalRating ?: 0.0, 0.1)
    }

    @Test
    fun testTcAts03WithstandIcmPeakFactor() {
        // TC-ATS-03: given Icw = 47.36 kA -> Icm = 47.36 * 2.2 = 104.19 kA
        val settings = AtsEntity()
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-ATS", loadKVA = 100.0, atsIncluded = true))

        val result = AtsEngine.calculate(
            settings = settings,
            totalConnectedLoad = 200.0,
            essentialLoadKVA = 100.0,
            generatorSelectedSize = 150.0,
            transformerFLC = 300.0,
            isc3PhaseAtTransformer = 47.36,
            circuits = circuits
        )

        assertEquals("Icw match", 47.36, result.requiredIcw, 0.01)
        assertEquals("Icm match", 104.19, result.requiredIcm, 0.05)
    }

    @Test
    fun testAtsManualOverride_ValidSize() {
        val settings = AtsEntity(enableManualOverride = true, manualSizeAmp = 630.0)
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-PANEL", loadKVA = 200.0, atsIncluded = true))
        val result = AtsEngine.calculate(settings = settings, totalConnectedLoad = 500.0, essentialLoadKVA = 200.0, generatorSelectedSize = 0.0, transformerFLC = 721.7, isc3PhaseAtTransformer = 25.0, circuits = circuits)

        assertEquals("Manual Override Active", result.status)
        assertEquals(630.0, result.finalRating ?: 0.0, 0.1)
        assertTrue(result.messages.none { it.contains("below governing current requirement") })
    }

    @Test
    fun testAtsManualOverride_UndersizedWarning() {
        val settings = AtsEntity(enableManualOverride = true, manualSizeAmp = 100.0)
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-PANEL", loadKVA = 200.0, atsIncluded = true))
        val result = AtsEngine.calculate(settings = settings, totalConnectedLoad = 500.0, essentialLoadKVA = 200.0, generatorSelectedSize = 0.0, transformerFLC = 721.7, isc3PhaseAtTransformer = 25.0, circuits = circuits)

        assertEquals("Manual Override Active", result.status)
        assertEquals(100.0, result.finalRating ?: 0.0, 0.1)
        assertTrue(result.messages.any { it.contains("below governing current requirement") })
    }

    @Test
    fun testAtsDefaultAutoSizingRegression() {
        val settings = AtsEntity(enableManualOverride = false, manualSizeAmp = null)
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-PANEL", loadKVA = 200.0, atsIncluded = true))
        val result = AtsEngine.calculate(settings = settings, totalConnectedLoad = 500.0, essentialLoadKVA = 200.0, generatorSelectedSize = 0.0, transformerFLC = 721.7, isc3PhaseAtTransformer = 25.0, circuits = circuits)

        assertEquals(400.0, result.finalRating ?: 0.0, 0.1)
        assertEquals("Valid ATS Sized", result.status)
    }
}
