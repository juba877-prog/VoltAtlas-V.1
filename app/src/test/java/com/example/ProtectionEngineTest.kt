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

    // ------------------------------------------------------------------
    // P0 CRITICAL-1 regression tests — strict trip-curve parsing (IEC 60364-4-41 §411.4.4)
    // Legacy bug: `overrideType.contains("B")` matched "MCB"/"MCCB"/"ACB" and forced
    // every overridden device onto the B-curve MAX_ZS_TABLE limits.
    // ------------------------------------------------------------------

    @Test
    fun testMccbIsNeverEvaluatedAsCurveB() {
        // "MCCB" must resolve to NULL curve (family string, not a curve) and must NOT
        // use the MCB table. Assumed 10×In pick-up → Zs_max = 0.95·230/(160×10) = 0.137 Ω
        // (legacy bug would have produced the B-curve value 0.95·230/(160×5) = 0.273 Ω)
        val circuit = CircuitEntity(id = 6, circuitName = "MCCB-FEEDER", loadKVA = 100.0, overrideDeviceType = "MCCB")
        val sizing = createDummySizing(ib = 144.3, inRating = 160.0, iz = 246.0, icu = 36.0)

        val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01)

        assertEquals(0.137, ads.maxZsAllowed, 0.0005)
        assertTrue(
            "Non-MCB device must produce an assumption warning",
            ads.warnings.any { it.contains("not an MCB") }
        )
        // Legacy bug assertion: 0.273 Ω (B-curve for 160 A) must NOT appear
        assertTrue(ads.maxZsAllowed < 0.2)
    }

    @Test
    fun testMCBCurveD_usesD_CurveMultipliers() {
        // Explicit curve token "MCB Curve D" → D-curve table → Zs_max for 32 A = 0.34 Ω
        val circuit = CircuitEntity(id = 7, circuitName = "D-CURVE-MCB", loadKVA = 100.0, overrideDeviceType = "MCB Curve D")
        val sizing = createDummySizing(ib = 25.0, inRating = 32.0, iz = 40.0, icu = 10.0)

        val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01)

        assertEquals(0.34, ads.maxZsAllowed, 0.0005)
        assertEquals(ProtectionEngine.TripCurve.D, ProtectionEngine.parseTripCurve("MCB Curve D"))
    }

    @Test
    fun testMCBCurveB_exactToken_usesB_CurveTable() {
        // MCB with explicit curve token "MCB Curve B" → B-curve table → Zs_max for 32 A = 1.37 Ω
        // No assumption warning expected (curve was explicitly designated)
        // NOTE: a bare "B" is NOT an MCB family string and correctly routes to the
        // non-MCB assumption path — family and curve are strictly separated.
        val circuit = CircuitEntity(id = 8, circuitName = "B-CURVE-MCB", loadKVA = 100.0, overrideDeviceType = "MCB Curve B")
        val sizing = createDummySizing(ib = 25.0, inRating = 32.0, iz = 40.0, icu = 10.0)

        val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01)

        assertEquals(1.37, ads.maxZsAllowed, 0.0005)
        assertTrue("Explicit curve should not trigger assumption warning", ads.warnings.isEmpty())
    }

    @Test
    fun testParseTripCurve_strictFamilyVsCurveSeparation() {
        // Device FAMILIES are never curves; only explicit curve designations resolve
        assertNull(ProtectionEngine.parseTripCurve("MCB"))
        assertNull(ProtectionEngine.parseTripCurve("MCCB"))
        assertNull(ProtectionEngine.parseTripCurve("ACB"))
        assertNull(ProtectionEngine.parseTripCurve("Load Break Switch (LBS)"))
        assertEquals(ProtectionEngine.TripCurve.B, ProtectionEngine.parseTripCurve("B"))
        assertEquals(ProtectionEngine.TripCurve.C, ProtectionEngine.parseTripCurve("c"))
        assertEquals(ProtectionEngine.TripCurve.D, ProtectionEngine.parseTripCurve("Type D"))
        assertNull(ProtectionEngine.parseTripCurve(null))
        assertNull(ProtectionEngine.parseTripCurve(""))
    }
}
