package com.example

import com.example.data.CircuitEntity
import com.example.data.UpsEntity
import com.example.engine.UpsEngine
import org.junit.Assert.*
import org.junit.Test

class UpsEngineTest {

    @Test
    fun testTcUps01Critical50kW() {
        // TC-UPS-01: criticalLoad = 50 kW
        // apparent = 50 / 0.9 = 55.56 kVA
        // design   = 55.56 * 1.20 = 66.67 kVA
        // battLoad = 66.67 * 0.9 / 0.94 = 63.83 kW
        // energy   = 63.83 * 0.25 = 15.96 kWh
        // corrected= 15.96 * 1.25 / 1.0 = 19.95 kWh
        // battery_Ah = 19.95 * 1000 / 400 = 49.9 Ah
        // selectedUPS = 100 kVA (via design/0.8 = 83.34 -> 100 kVA)
        // strings  = CEIL(49.9 / 100) = 1
        val settings = UpsEntity(
            systemVoltage = 400,
            upsPf = 0.9,
            upsEfficiency = 94.0,
            autonomyMinutes = 15,
            agingFactor = 1.25,
            tempDeratingFactor = 1.0,
            designMarginPct = 20.0
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "CRITICAL-SERVER", loadKVA = 55.55555555555556, pf = 0.9, upsBacked = true)
        )

        val result = UpsEngine.calculate(settings, circuits)

        assertEquals("totalCriticalLoadKW match", 50.0, result.totalCriticalLoadKW, 0.1)
        assertEquals("upsApparentPowerReq match", 55.56, result.upsApparentPowerReq, 0.1)
        assertEquals("designUpsKVA match", 66.67, result.designUpsKVA, 0.1)
        assertEquals("batteryLoadKW match", 63.83, result.batteryLoadKW ?: 0.0, 0.1)
        assertEquals("batteryEnergyReq match", 15.96, result.batteryEnergyReq ?: 0.0, 0.1)
        assertEquals("correctedBatteryCapacity match", 19.95, result.correctedBatteryCapacity ?: 0.0, 0.1)
        assertEquals("batteryAh match", 49.9, result.batteryAh ?: 0.0, 0.5)
        assertEquals("recommendedSize match", 100.0, result.recommendedSize ?: 0.0, 0.1)
        assertEquals("estimatedBatteryStrings match", 1, result.estimatedBatteryStrings ?: 0)
    }

    @Test
    fun testTcUps02Critical100kW() {
        // TC-UPS-02: criticalLoad = 100 kW
        // apparent = 100 / 0.9 = 111.11 kVA
        // design   = 111.11 * 1.20 = 133.33 kVA
        // battery_Ah = 99.7 Ah
        // selectedUPS = 200 kVA (via design/0.8 = 166.66 -> 200 kVA)
        // strings  = 1
        val settings = UpsEntity(
            systemVoltage = 400,
            upsPf = 0.9,
            upsEfficiency = 94.0,
            autonomyMinutes = 15,
            agingFactor = 1.25,
            tempDeratingFactor = 1.0,
            designMarginPct = 20.0
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "CRITICAL-DATA-CENTER", loadKVA = 111.11111111111111, pf = 0.9, upsBacked = true)
        )

        val result = UpsEngine.calculate(settings, circuits)

        assertEquals("totalCriticalLoadKW match", 100.0, result.totalCriticalLoadKW, 0.1)
        assertEquals("upsApparentPowerReq match", 111.11, result.upsApparentPowerReq, 0.1)
        assertEquals("designUpsKVA match", 133.33, result.designUpsKVA, 0.1)
        assertEquals("batteryAh match", 99.7, result.batteryAh ?: 0.0, 0.5)
        assertEquals("recommendedSize match", 200.0, result.recommendedSize ?: 0.0, 0.1)
        assertEquals("estimatedBatteryStrings match", 1, result.estimatedBatteryStrings ?: 0)
    }
}
