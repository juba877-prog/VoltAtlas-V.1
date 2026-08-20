package com.example

import com.example.data.CircuitEntity
import com.example.engine.ProtectionEngine
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import org.junit.Assert.*
import org.junit.Test

class ProtectionEngineTest {

    private fun createDummySizing(
        ib: Double = 144.3,
        inRating: Double = 160.0,
        iz: Double = 246.0,
        icu: Double = 36.0,
        cableSize: String = "70 mm²",
        kFactor: Double = 143.0,
        clearingTime: Double = 0.1
    ): SizingResult {
        return SizingResult(
            Ib_A = ib,
            deviceSize_A = inRating,
            deviceType = "MCCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = ib,
            productCode = "CX1-T104-G12",
            cableSize_mm2 = cableSize,
            ratedI_table_A = iz,
            capacity_derated_Iz_A = iz,
            utilizationPct = ib / iz * 100.0,
            neutral_mm2 = cableSize,
            earthCPC_mm2 = "35 mm²",
            voltDrop_V = 2.0,
            vdPct = 0.5,
            cumulativeVDpct = 0.5,
            status = "✓ PASS",
            failReason = "",
            cableMaterial = "Cu",
            cableOD_mm = 24.3,
            deviceBreakingKA = icu,
            kFactor = kFactor,
            clearingTime_s = clearingTime,
            rUsed = 0.3,
            xUsed = 0.08
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)
    }

    private fun createDummyShortCircuit(
        circuitId: Int = 1,
        iscKA: Double = 7.58,
        status: String = "PASS"
    ): ShortCircuitResult {
        return ShortCircuitResult(
            circuitId = circuitId,
            sourceName = "Transformer",
            cableMm2 = "70 mm²",
            iscKA = iscKA,
            iwKA = 30.0,
            deviceKA = 36.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 20.0,
            status = status,
            note = ""
        )
    }

    @Test
    fun testTcProt01HealthyPass() {
        // TC-PROT-01: Ib=144.3, In=160, Iz=246, Isc=7.58 kA, Icu=36 kA
        // Overload: 144.3 <= 160 <= 246 -> PASS
        // SC: 36 >= 7.58 -> PASS
        // FINAL: PASS
        val circuit = CircuitEntity(id = 1, circuitName = "FEEDER-01", loadKVA = 100.0)
        val sizing = createDummySizing(ib = 144.3, inRating = 160.0, iz = 246.0, icu = 36.0)
        val sc = createDummyShortCircuit(circuitId = 1, iscKA = 7.58, status = "PASS")

        val result = ProtectionEngine.evaluateProtection(circuit, sizing, sc, upstreamIn_A = 400.0)

        assertEquals("overloadStatus match", "PASS", result.overloadStatus)
        assertEquals("shortCircuitStatus match", "PASS", result.shortCircuitStatus)
        assertEquals("cableWithstandStatus match", "PASS", result.cableWithstandStatus)
        assertEquals("selectivityStatus match", "PASS", result.selectivityStatus)
        assertEquals("finalStatus match", "PASS", result.finalStatus)
    }

    @Test
    fun testTcProt02DeviceUnderRatedFail() {
        // TC-PROT-02 (device too small): Ib=200, In=160, Iz=246 -> Overload FAIL -> FINAL: FAIL
        val circuit = CircuitEntity(id = 2, circuitName = "FEEDER-02", loadKVA = 140.0)
        val sizing = createDummySizing(ib = 200.0, inRating = 160.0, iz = 246.0, icu = 36.0)
        val sc = createDummyShortCircuit(circuitId = 2, iscKA = 7.58, status = "PASS")

        val result = ProtectionEngine.evaluateProtection(circuit, sizing, sc)

        assertEquals("FAIL", result.overloadStatus)
        assertEquals("FAIL", result.finalStatus)
    }

    @Test
    fun testTcProt03BreakingCapacityExceededFail() {
        // TC-PROT-03 (breaking capacity exceeded): Isc=50 kA, Icu=36 kA -> SC FAIL -> FINAL: FAIL
        val circuit = CircuitEntity(id = 3, circuitName = "FEEDER-03", loadKVA = 100.0)
        val sizing = createDummySizing(ib = 144.3, inRating = 160.0, iz = 246.0, icu = 36.0)
        val sc = createDummyShortCircuit(circuitId = 3, iscKA = 50.0, status = "FAIL — ISC EXCEEDS BREAKING CAPACITY")

        val result = ProtectionEngine.evaluateProtection(circuit, sizing, sc)

        assertEquals("FAIL", result.shortCircuitStatus)
        assertEquals("FAIL", result.finalStatus)
    }

    @Test
    fun testTcProt04CableNotProtectedFail() {
        // TC-PROT-04 (cable not protected): In=250, Iz=200 -> Overload FAIL -> FINAL: FAIL
        val circuit = CircuitEntity(id = 4, circuitName = "FEEDER-04", loadKVA = 100.0)
        val sizing = createDummySizing(ib = 144.3, inRating = 250.0, iz = 200.0, icu = 36.0)
        val sc = createDummyShortCircuit(circuitId = 4, iscKA = 7.58, status = "PASS")

        val result = ProtectionEngine.evaluateProtection(circuit, sizing, sc)

        assertEquals("FAIL", result.overloadStatus)
        assertEquals("FAIL", result.finalStatus)
    }

    @Test
    fun testTcProt05SelectivityWarning() {
        // TC-PROT-05 (selectivity warning): upstream In = 200, downstream In = 160 (ratio 1.25 < 1.6) -> WARNING
        val circuit = CircuitEntity(id = 5, circuitName = "SUB-FEEDER-05", loadKVA = 100.0)
        val sizing = createDummySizing(ib = 144.3, inRating = 160.0, iz = 246.0, icu = 36.0)
        val sc = createDummyShortCircuit(circuitId = 5, iscKA = 7.58, status = "PASS")

        val result = ProtectionEngine.evaluateProtection(circuit, sizing, sc, upstreamIn_A = 200.0)

        assertEquals("PASS", result.overloadStatus)
        assertEquals("PASS", result.shortCircuitStatus)
        assertEquals("WARNING", result.selectivityStatus)
        assertEquals("WARNING", result.finalStatus)
    }
}
