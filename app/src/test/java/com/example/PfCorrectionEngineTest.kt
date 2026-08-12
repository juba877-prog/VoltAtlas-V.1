package com.example

import com.example.data.PfGlobalEntity
import com.example.data.TransformerEntity
import com.example.engine.PfCorrectionEngine
import com.example.engine.TransformerResult
import org.junit.Assert.*
import org.junit.Test

class PfCorrectionEngineTest {

    private fun createDummyTransformerResult(demandKW: Double): TransformerResult {
        return TransformerResult(
            connectedLoad = demandKW / 0.8,
            circuitsAssigned = 1,
            realPower = demandKW,
            demandLoad = demandKW,
            diversifiedLoad = demandKW,
            futureLoad = demandKW,
            designLoad = demandKW,
            requiredKVA = demandKW / 0.8,
            recommendedSize = 1000.0,
            selectedSize = 1000.0,
            loadingPct = 50.0,
            flc = 1443.0,
            zt = 9.6,
            rt = 0.96,
            xt = 9.55,
            isc3Phase = 24.0,
            isc1Phase = 24.0,
            status = "VERIFIED",
            z0 = 8.64,
            vectorGroup = "Dyn11"
        )
    }

    @Test
    fun testTcPf01Power200kW() {
        // TC-PF-01: P=200 kW, cosφ1=0.80, target cosφ2=0.97, step=50
        // existing_kvar = 200 * tan(acos 0.80) = 150.0
        // target_kvar   = 200 * tan(acos 0.97) = 50.12
        // Qc = 150.0 - 50.12 = 99.88 ; nSteps = CEIL(99.88/50) = 2 ; bank = 100 kvar
        // capCurrent = 100000/(sqrt(3)*400) = 144.3 A
        // correctedKVA = sqrt(200^2 + (150-100)^2) = 206.16 kVA
        val globalSettings = PfGlobalEntity(targetPf = 0.97, kvarStepSize = 50.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-01", pf = 0.80, status = "Active"))
        val transformerResults = mapOf(
            "TX-01" to createDummyTransformerResult(200.0)
        )

        val results = PfCorrectionEngine.calculate(globalSettings, emptyList(), transformers, transformerResults)
        assertEquals(1, results.size)
        val res = results[0]

        assertEquals("existingKW match", 200.0, res.existingKW, 0.1)
        assertEquals("existingKvar match", 150.0, res.existingKvar, 0.5)
        assertEquals("targetKvar match", 50.12, res.targetKvar, 0.5)
        assertEquals("requiredKvar match", 99.88, res.requiredKvar, 0.5)
        assertEquals("bankSteps match", 2.0, res.bankSteps, 0.01)
        assertEquals("bankCapacity match", 100.0, res.bankCapacity, 0.1)
        assertEquals("bankCurrent match", 144.3, res.bankCurrent, 0.5)
        assertEquals("correctedKVA match", 206.16, res.correctedKVA, 0.5)
    }

    @Test
    fun testTcPf02Power500kW() {
        // TC-PF-02: P=500 kW, cosφ1=0.75, target 0.95, step=50
        // existing_kvar = 440.96 ; target_kvar = 164.34 ; Qc = 276.62
        // nSteps = CEIL(276.62/50) = 6 ; bank = 300 kvar ; correctedKVA = 519.49 kVA
        val globalSettings = PfGlobalEntity(targetPf = 0.95, kvarStepSize = 50.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-02", pf = 0.75, status = "Active"))
        val transformerResults = mapOf(
            "TX-02" to createDummyTransformerResult(500.0)
        )

        val results = PfCorrectionEngine.calculate(globalSettings, emptyList(), transformers, transformerResults)
        val res = results[0]

        assertEquals("existingKvar match", 440.96, res.existingKvar, 0.5)
        assertEquals("targetKvar match", 164.34, res.targetKvar, 0.5)
        assertEquals("requiredKvar match", 276.62, res.requiredKvar, 0.5)
        assertEquals("bankSteps match", 6.0, res.bankSteps, 0.01)
        assertEquals("bankCapacity match", 300.0, res.bankCapacity, 0.1)
        assertEquals("correctedKVA match", 519.49, res.correctedKVA, 0.5)
    }

    @Test
    fun testTcPf03AlreadyCorrectedGuard() {
        // TC-PF-03: already at/above target (P=100kW, cosφ1=0.98, target 0.97) -> Qc <= 0 -> 0 kvar guard
        val globalSettings = PfGlobalEntity(targetPf = 0.97, kvarStepSize = 50.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-03", pf = 0.98, status = "Active"))
        val transformerResults = mapOf("TX-03" to createDummyTransformerResult(100.0))

        val results = PfCorrectionEngine.calculate(globalSettings, emptyList(), transformers, transformerResults)
        val res = results[0]

        assertEquals("NO CORRECTION REQUIRED", res.status)
        assertEquals(0.0, res.requiredKvar, 0.01)
        assertEquals(0.0, res.bankCapacity, 0.01)
    }
}
