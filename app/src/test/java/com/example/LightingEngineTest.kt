package com.example

import com.example.engine.LightingCalculationInput
import com.example.engine.LightingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LightingEngine (Lumen Method / Room Index Lux Calculations).
 * Verifies exact formula behavior, rounding, and division-by-zero boundary protections.
 */
class LightingEngineTest {

    @Test
    fun testLightingStandardOfficeCalculation() {
        // Room: 10m x 6m (60 m²), Height: 3.0m
        // Target: 500 Lux, MF: 0.8, UF: 0.65
        // Fixture: 3200 lm, 28 W
        // Total Lumens = (500 * 60) / (0.65 * 0.8) = 30000 / 0.52 = 57692.3077 lm
        // Fixtures Count = ceil(57692.3077 / 3200) = ceil(18.0288) = 19 fixtures
        // Total Power = 19 * 28 = 532 W
        // Power Density = 532 / 60 = 8.8667 W/m² (rounded to 8.87)
        // Actual Lux = (19 * 3200 * 0.65 * 0.8) / 60 = 31616 / 60 = 526.933 Lux (rounded to 526.9)
        val input = LightingCalculationInput(
            length = 10.0,
            width = 6.0,
            height = 3.0,
            targetLux = 500.0,
            maintenanceFactor = 0.8,
            utilizationFactor = 0.65,
            lumensPerFixture = 3200.0,
            wattsPerFixture = 28.0
        )

        val result = LightingEngine.calculate(input)

        assertEquals("Area must be 60 m²", 60.0, result.area_m2, 0.01)
        assertEquals("Total lumens needed", 57692.3, result.totalLumensNeeded, 0.5)
        assertEquals("Fixtures count", 19, result.fixturesCount)
        assertEquals("Actual lux achieved", 526.9, result.actualLux, 0.2)
        assertEquals("Total power in watts", 532.0, result.totalPower_W, 0.01)
        assertEquals("Power density in W/m²", 8.87, result.powerDensity_W_m2, 0.05)
    }

    @Test
    fun testLightingSmallRoomExactCeil() {
        // Room: 4m x 3m (12 m²), Target: 300 Lux, MF: 0.8, UF: 0.6
        // Total Lumens = (300 * 12) / (0.6 * 0.8) = 3600 / 0.48 = 7500 lm
        // Fixture: 2500 lm, 20 W
        // Fixture count = ceil(7500 / 2500) = 3 fixtures
        // Actual Lux = (3 * 2500 * 0.6 * 0.8) / 12 = 3600 / 12 = 300.0 Lux
        val input = LightingCalculationInput(
            length = 4.0,
            width = 3.0,
            height = 2.8,
            targetLux = 300.0,
            maintenanceFactor = 0.8,
            utilizationFactor = 0.6,
            lumensPerFixture = 2500.0,
            wattsPerFixture = 20.0
        )

        val result = LightingEngine.calculate(input)

        assertEquals(12.0, result.area_m2, 0.01)
        assertEquals(7500.0, result.totalLumensNeeded, 0.1)
        assertEquals(3, result.fixturesCount)
        assertEquals(300.0, result.actualLux, 0.1)
        assertEquals(60.0, result.totalPower_W, 0.01)
        assertEquals(5.0, result.powerDensity_W_m2, 0.01)
    }

    @Test
    fun testLightingZeroLumensReturnsZeroResult() {
        val input = LightingCalculationInput(
            length = 10.0,
            width = 10.0,
            height = 3.0,
            targetLux = 500.0,
            maintenanceFactor = 0.8,
            utilizationFactor = 0.65,
            lumensPerFixture = 0.0,
            wattsPerFixture = 30.0
        )

        val result = LightingEngine.calculate(input)

        assertEquals(0, result.fixturesCount)
        assertEquals(0.0, result.actualLux, 0.001)
        assertEquals(0.0, result.totalPower_W, 0.001)
    }

    @Test
    fun testLightingZeroUtilizationFactorReturnsZeroResult() {
        val input = LightingCalculationInput(
            length = 10.0,
            width = 10.0,
            height = 3.0,
            targetLux = 500.0,
            maintenanceFactor = 0.8,
            utilizationFactor = 0.0,
            lumensPerFixture = 3000.0,
            wattsPerFixture = 30.0
        )

        val result = LightingEngine.calculate(input)

        assertEquals(0, result.fixturesCount)
        assertEquals(0.0, result.actualLux, 0.001)
    }

    @Test
    fun testLightingZeroMaintenanceFactorReturnsZeroResult() {
        val input = LightingCalculationInput(
            length = 10.0,
            width = 10.0,
            height = 3.0,
            targetLux = 500.0,
            maintenanceFactor = 0.0,
            utilizationFactor = 0.65,
            lumensPerFixture = 3000.0,
            wattsPerFixture = 30.0
        )

        val result = LightingEngine.calculate(input)

        assertEquals(0, result.fixturesCount)
        assertEquals(0.0, result.actualLux, 0.001)
    }
}
