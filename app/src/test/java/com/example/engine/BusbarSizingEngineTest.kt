package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusbarSizingEngineTest {

    @Test
    fun testStandardRatingSelection() {
        // 240A design current -> 250A standard rating
        val res250 = BusbarSizingEngine.sizeBusbar(designCurrentA = 240.0)
        assertEquals(250.0, res250.ratedCurrentA, 0.001)

        // 550A design current -> 630A standard rating
        val res630 = BusbarSizingEngine.sizeBusbar(designCurrentA = 550.0)
        assertEquals(630.0, res630.ratedCurrentA, 0.001)

        // 2200A design current -> 2500A standard rating
        val res2500 = BusbarSizingEngine.sizeBusbar(designCurrentA = 2200.0)
        assertEquals(2500.0, res2500.ratedCurrentA, 0.001)
    }

    @Test
    fun testIncomerRatingDominance() {
        // Design current 200A, but incomer is 400A MCCB -> sizes for 400A
        val res = BusbarSizingEngine.sizeBusbar(
            designCurrentA = 200.0,
            incomerRatingA = 400.0
        )
        assertEquals(400.0, res.ratedCurrentA, 0.001)
        assertEquals(400.0, res.designCurrentA, 0.001)
    }

    @Test
    fun testTemperatureDerating() {
        // Standard 35 deg C -> kTemp = 1.0
        assertEquals(1.0, BusbarSizingEngine.calculateTemperatureDerating(35.0), 0.001)

        // 25 deg C -> clamped to 1.0
        assertEquals(1.0, BusbarSizingEngine.calculateTemperatureDerating(25.0), 0.001)

        // 45 deg C -> sqrt((105 - 45) / (105 - 35)) = sqrt(60/70) = 0.9258
        val k45 = BusbarSizingEngine.calculateTemperatureDerating(45.0)
        assertEquals(0.926, k45, 0.005)

        // 50 deg C -> sqrt((105 - 50) / 70) = sqrt(55/70) = 0.8864
        val k50 = BusbarSizingEngine.calculateTemperatureDerating(50.0)
        assertEquals(0.886, k50, 0.005)
    }

    @Test
    fun testIpRatingDerating() {
        assertEquals(1.00, BusbarSizingEngine.calculateIpDerating("IP31"), 0.001)
        assertEquals(1.00, BusbarSizingEngine.calculateIpDerating("IP20"), 0.001)
        assertEquals(0.90, BusbarSizingEngine.calculateIpDerating("IP42"), 0.001)
        assertEquals(0.80, BusbarSizingEngine.calculateIpDerating("IP54"), 0.001)
        assertEquals(0.80, BusbarSizingEngine.calculateIpDerating("IP65"), 0.001)
    }

    @Test
    fun testIecTable7PeakFactorN() {
        assertEquals(1.5, BusbarSizingEngine.resolvePeakFactorN(4.0), 0.001)
        assertEquals(1.7, BusbarSizingEngine.resolvePeakFactorN(10.0), 0.001)
        assertEquals(2.0, BusbarSizingEngine.resolvePeakFactorN(18.0), 0.001)
        assertEquals(2.1, BusbarSizingEngine.resolvePeakFactorN(36.0), 0.001)
        assertEquals(2.2, BusbarSizingEngine.resolvePeakFactorN(65.0), 0.001)
    }

    @Test
    fun testShortCircuitThermalWithstand() {
        // Fault current 36 kA for 1s on Copper (k=166)
        // S_min = 36000 * 1 / 166 = 216.86 mm2
        val res = BusbarSizingEngine.sizeBusbar(
            designCurrentA = 300.0,
            faultLevelKa = 36.0,
            faultDurationS = 1.0,
            material = BusbarMaterial.COPPER
        )
        assertTrue("Min cross section must be > 216 mm2", res.minThermalCrossSectionMm2 >= 216.0)
        assertTrue("Selected profile must meet min cross section", res.profile.crossSectionPerPhaseMm2 >= res.minThermalCrossSectionMm2)
        assertEquals(36.0, res.icwKA, 0.001)
        // Peak factor for 36 kA is 2.1 -> Ipk = 36 * 2.1 = 75.6 kA
        assertEquals(75.6, res.ipkKA, 0.001)
    }

    @Test
    fun testNeutralAndPeSizing() {
        // Phase profile with 400 mm2 cross-section (e.g. 40x10)
        val resNormal = BusbarSizingEngine.sizeBusbar(
            designCurrentA = 600.0,
            hasHighHarmonics = false
        )
        // 50% neutral for large bars
        val phSec = resNormal.profile.crossSectionPerPhaseMm2
        assertTrue("Neutral must be 50% of phase cross section", resNormal.neutralProfile.startsWith("${(phSec * 0.5).toInt()} mm²"))
        assertTrue("PE must be 50% of phase cross section for large bars", resNormal.peProfile.startsWith("${(phSec * 0.5).toInt()} mm²"))

        // High harmonics (>33%) -> 200% neutral
        val resHarmonic = BusbarSizingEngine.sizeBusbar(
            designCurrentA = 600.0,
            hasHighHarmonics = true
        )
        assertTrue("Neutral must be 200% under high harmonics", resHarmonic.neutralProfile.startsWith("${(phSec * 2.0).toInt()} mm²"))
    }

    @Test
    fun testConsultantEngineeringTagFormat() {
        val res = BusbarSizingEngine.sizeBusbar(
            designCurrentA = 550.0,
            faultLevelKa = 36.0,
            voltageV = 400.0,
            ipRating = "IP54"
        )
        // Expected format: "Copper Bus 630A, 400V, 36kA/1s, IP54 [1×(40×10) mm²]"
        val tag = res.engineeringTag
        assertTrue("Tag must contain Copper Bus", tag.contains("Copper Bus"))
        assertTrue("Tag must contain 630A", tag.contains("630A"))
        assertTrue("Tag must contain 400V", tag.contains("400V"))
        assertTrue("Tag must contain 36kA/1s", tag.contains("36kA/1s"))
        assertTrue("Tag must contain IP54", tag.contains("IP54"))
        assertTrue("Tag must contain bar profile", tag.contains("mm²"))
    }

    @Test
    fun testManualOverridesRespected() {
        val res = BusbarSizingEngine.sizeBusbar(
            designCurrentA = 100.0,
            manualRatingA = 800.0,
            manualScKa = 50.0
        )
        assertEquals(800.0, res.ratedCurrentA, 0.001)
        assertEquals(50.0, res.icwKA, 0.001)
        assertEquals(2.1, res.peakFactorN, 0.001)
        assertEquals(105.0, res.ipkKA, 0.001)
    }
}
