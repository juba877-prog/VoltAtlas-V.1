package com.example.ui

import com.example.engine.ShortCircuitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ShortCircuitMarginConsistencyTest {

    @Test
    fun testLowestMarginKpi_equals_worstCardMargin() {
        val circuits = listOf(
            ShortCircuitResult(
                circuitId = 101,
                sourceName = "GRID-SUB-01",
                cableMm2 = "300 mm²",
                iscKA = 32.40,
                iwKA = 48.00,
                deviceKA = 50.0,
                deviceCheck = "PASS",
                cableCheck = "PASS",
                marginPct = 48.1,
                status = "PASS",
                note = ""
            ),
            ShortCircuitResult(
                circuitId = 102,
                sourceName = "CHILLER-TX-02",
                cableMm2 = "185 mm²",
                iscKA = 24.10,
                iwKA = 26.00,
                deviceKA = 25.0,
                deviceCheck = "WARNING",
                cableCheck = "PASS",
                marginPct = 3.7,
                status = "WARNING",
                note = "Device breaking capacity margin is below 10%."
            ),
            ShortCircuitResult(
                circuitId = 103,
                sourceName = "DIESEL-GEN-02",
                cableMm2 = "35 mm²",
                iscKA = 12.80,
                iwKA = 8.50,
                deviceKA = 10.0,
                deviceCheck = "FAIL",
                cableCheck = "FAIL",
                marginPct = -33.6,
                status = "FAIL",
                note = "Thermal withstand Iw < Isc."
            )
        )

        // Compute individual card margins via unified formula
        val cardMargins = circuits.map { calculateCircuitMarginPct(it) }
        
        // Summary KPI "Lowest Margin"
        val lowestMarginKpi = cardMargins.minOrNull() ?: 0.0

        // Emergency Core Panel (circuit 103) is the worst
        val worstCardMargin = calculateCircuitMarginPct(circuits[2])

        assertEquals(-33.59375, worstCardMargin, 0.01)
        assertEquals(worstCardMargin, lowestMarginKpi, 0.0001)

        // Formatted display values must be identical string
        val cardFormatted = String.format(java.util.Locale.US, "%.1f%%", worstCardMargin)
        val kpiFormatted = String.format(java.util.Locale.US, "%.1f%%", lowestMarginKpi)
        assertEquals("-33.6%", cardFormatted)
        assertEquals(cardFormatted, kpiFormatted)
    }

    @Test
    fun testMarginCalculation_limitingConstraint() {
        // Case 1: Device Icu is limiting (Icu 25.0 vs Iw 26.0, Isc 24.1)
        val hvac = ShortCircuitResult(
            circuitId = 102,
            sourceName = "TX",
            cableMm2 = "185 mm²",
            iscKA = 24.10,
            iwKA = 26.00,
            deviceKA = 25.0,
            deviceCheck = "WARNING",
            cableCheck = "PASS",
            marginPct = 0.0,
            status = "WARNING",
            note = ""
        )
        val hvacMargin = calculateCircuitMarginPct(hvac)
        // (25.0 - 24.1) / 24.1 = 0.9 / 24.1 = 3.7344%
        assertEquals(3.7344, hvacMargin, 0.01)

        // Case 2: Cable Iw is limiting (Iw 8.5 vs Icu 10.0, Isc 12.8)
        val emergency = ShortCircuitResult(
            circuitId = 103,
            sourceName = "GEN",
            cableMm2 = "35 mm²",
            iscKA = 12.80,
            iwKA = 8.50,
            deviceKA = 10.0,
            deviceCheck = "FAIL",
            cableCheck = "FAIL",
            marginPct = 0.0,
            status = "FAIL",
            note = ""
        )
        val emergencyMargin = calculateCircuitMarginPct(emergency)
        // (8.5 - 12.8) / 12.8 = -4.3 / 12.8 = -33.59375%
        assertEquals(-33.59375, emergencyMargin, 0.01)

        // Case 3: Cable Iw is limiting for high pass (Iw 48.0 vs Icu 50.0, Isc 32.4)
        val mainFeeder = ShortCircuitResult(
            circuitId = 101,
            sourceName = "GRID",
            cableMm2 = "300 mm²",
            iscKA = 32.40,
            iwKA = 48.00,
            deviceKA = 50.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 0.0,
            status = "PASS",
            note = ""
        )
        val mainMargin = calculateCircuitMarginPct(mainFeeder)
        // (48.0 - 32.4) / 32.4 = 15.6 / 32.4 = 48.148%
        assertEquals(48.148, mainMargin, 0.01)
    }
}
