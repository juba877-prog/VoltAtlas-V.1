package com.example

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.engine.GeneratorEngine
import org.junit.Assert.*
import org.junit.Test

class GeneratorEngineTest {

    @Test
    fun testTcGen01Emergency250kVA() {
        // TC-GEN-01: emergency=250 kVA, no motor
        // essential = 250 * 0.8 / 1.0 = 200.0
        // stepLoad  = 200 * 0.5 + 0 = 100.0
        // required  = max(200, 100) / 0.8 = 250.0
        // design    = 250.0 -> selectedSize = 250 kVA ; loading = 100.0%
        val settings = GeneratorEntity(
            generatorPf = 0.8,
            demandFactor = 0.8,
            diversityFactor = 1.0,
            stepLoadPct = 50.0,
            oversizingFactor = 1.0,
            largestMotorKW = 0.0
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "EMERGENCY-MDB", loadKVA = 250.0, generatorIncluded = true)
        )

        val result = GeneratorEngine.calculate(settings, circuits, 250.0)

        assertEquals("essentialLoadKVA_calc match", 200.0, result.essentialLoadKVA_calc, 0.1)
        assertEquals("stepLoadAtStart match", 100.0, result.stepLoadAtStart, 0.1)
        assertEquals("requiredGenerator match", 250.0, result.requiredGenerator, 0.1)
        assertEquals("designCapacity match", 250.0, result.designCapacity, 0.1)
        assertEquals("finalSize match", 250.0, result.finalSize ?: 0.0, 0.1)
        assertEquals("loadingPct match", 100.0, result.loadingPct ?: 0.0, 0.1)
    }

    @Test
    fun testTcGen02Emergency300kVAWithDOLMotor() {
        // TC-GEN-02: emergency=300 kVA + 75 kW DOL motor
        // essential = 300 * 0.8 / 1.0 = 240.0
        // motorStart = 75 / 0.85 * 6 = 529.41
        // stepLoad  = 240 * 0.5 + 529.41 = 649.41
        // required  = max(240, 649.41) / 0.8 = 811.76
        // design    = 811.76 -> selectedSize = 1000 kVA ; loading = 81.2%
        val settings = GeneratorEntity(
            generatorPf = 0.8,
            demandFactor = 0.8,
            diversityFactor = 1.0,
            stepLoadPct = 50.0,
            oversizingFactor = 1.0,
            largestMotorKW = 75.0,
            motorPf = 0.85,
            motorStartMethod = "DOL"
        )

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "EMERGENCY-LOAD", loadKVA = 300.0, generatorIncluded = true)
        )

        val result = GeneratorEngine.calculate(settings, circuits, 300.0)

        assertEquals("essentialLoadKVA_calc match", 240.0, result.essentialLoadKVA_calc, 0.1)
        assertEquals("motorStartingKVA match", 529.41, result.motorStartingKVA, 0.5)
        assertEquals("stepLoadAtStart match", 649.41, result.stepLoadAtStart, 0.5)
        assertEquals("requiredGenerator match", 811.76, result.requiredGenerator, 0.5)
        assertEquals("designCapacity match", 811.76, result.designCapacity, 0.5)
        assertEquals("finalSize match", 1000.0, result.finalSize ?: 0.0, 0.1)
        assertEquals("loadingPct match", 81.2, result.loadingPct ?: 0.0, 0.5)

        // Dip warning assertion: motorStart (529.41) / selected (1000) > 0.3
        assertTrue("Motor dip warning must fire", result.messages.any { it.contains("voltage dip") })
    }

    @Test
    fun testTcGen03EmergencyZeroGuard() {
        // TC-GEN-03: emergency = 0 -> "No Generator Required"
        val settings = GeneratorEntity(additionalEssentialLoadKW = 0.0)
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "NORMAL-LOAD", loadKVA = 100.0, generatorIncluded = false))
        val result = GeneratorEngine.calculate(settings, circuits, 100.0)

        assertEquals("No Generator Required", result.status)
        assertNull(result.finalSize)
    }

    @Test
    fun testGeneratorManualOverride_ValidSize() {
        val settings = GeneratorEntity(
            enableManualOverride = true,
            manualSizeKVA = 500.0
        )
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-MDB", loadKVA = 250.0, generatorIncluded = true))
        val result = GeneratorEngine.calculate(settings, circuits, 250.0)

        assertEquals("Manual Override Active", result.status)
        assertEquals(500.0, result.finalSize ?: 0.0, 0.1)
        assertTrue(result.messages.none { it.contains("below design requirement") })
    }

    @Test
    fun testGeneratorManualOverride_UndersizedWarning() {
        val settings = GeneratorEntity(
            enableManualOverride = true,
            manualSizeKVA = 100.0
        )
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-MDB", loadKVA = 250.0, generatorIncluded = true))
        val result = GeneratorEngine.calculate(settings, circuits, 250.0)

        assertEquals("Manual Override Active", result.status)
        assertEquals(100.0, result.finalSize ?: 0.0, 0.1)
        assertTrue(result.messages.any { it.contains("below design requirement") })
    }

    @Test
    fun testGeneratorDefaultAutoSizingRegression() {
        val settings = GeneratorEntity(enableManualOverride = false, manualSizeKVA = null)
        val circuits = listOf(CircuitEntity(id = 1, circuitName = "EMERGENCY-MDB", loadKVA = 250.0, generatorIncluded = true))
        val result = GeneratorEngine.calculate(settings, circuits, 250.0)

        assertEquals(250.0, result.finalSize ?: 0.0, 0.1)
        assertNotEquals("Manual Override Active", result.status)
    }

    @Test
    fun testMultiInstanceGeneratorAggregation() {
        val gen1Settings = GeneratorEntity(generatorId = "GEN-01")
        val gen2Settings = GeneratorEntity(generatorId = "GEN-02")

        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "GEN1-LOAD", loadKVA = 100.0, assignedGeneratorId = "GEN-01", pf = 1.0),
            CircuitEntity(id = 2, circuitName = "GEN2-LOAD", loadKVA = 200.0, assignedGeneratorId = "GEN-02", pf = 1.0)
        )

        val res1 = GeneratorEngine.calculate(gen1Settings, circuits, 300.0)
        val res2 = GeneratorEngine.calculate(gen2Settings, circuits, 300.0)

        assertEquals(100.0, res1.essentialLoadKVA, 0.1)
        assertEquals(200.0, res2.essentialLoadKVA, 0.1)
    }

    @Test
    fun testAtsIndirectGeneratorAggregation() {
        val gen2Settings = GeneratorEntity(generatorId = "GEN-02")
        val ats1Settings = AtsEntity(atsId = "ATS-01", emergencySourceId = "GEN-02")

        val circuits = listOf(
            CircuitEntity(id = 3, circuitName = "ATS-LOAD", loadKVA = 150.0, assignedAtsId = "ATS-01", pf = 1.0)
        )

        val resGen2 = GeneratorEngine.calculate(gen2Settings, circuits, 150.0, listOf(ats1Settings))

        assertEquals(150.0, resGen2.essentialLoadKVA, 0.1)
    }
}
