package com.example

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.engine.ProtectionEngine
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProtectionEngineTest {

    private var originalDataSheet: List<DataSheetEntry> = emptyList()

    /**
     * ENG-30 (§12.2-a): the ADS tests below assert verdicts and warnings that only hold when
     * the sizes they resolve (70 / 35 mm²) have data_sheet rows. Installing the fixture
     * unconditionally removes a pre-existing dependence on whichever catalog state the
     * previous test class happened to leave behind — a resolved size with no row is now
     * DATA MISSING rather than a silent 0.0 Ω loop.
     */
    @Before
    fun installDataSheetFixture() {
        originalDataSheet = Catalog.dataSheet
        Catalog.dataSheet = listOf(
            DataSheetEntry(size = 70.0, R_Cu20 = 0.268, X = 0.08, R_Al20 = 0.443, R_Cu_70_est = 0.322, R_Al_70_est = 0.532),
            DataSheetEntry(size = 35.0, R_Cu20 = 0.524, X = 0.08, R_Al20 = 0.868, R_Cu_70_est = 0.629, R_Al_70_est = 1.042)
        )
    }

    @After
    fun restoreDataSheetFixture() {
        Catalog.dataSheet = originalDataSheet
    }

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

        val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01, isZeResolved = true)

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

        val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01, isZeResolved = true)

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

        val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01, isZeResolved = true)

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

    // =========================================================================
    // AUDIT-5 P0 item 3b (C-1): Ze fallback. Where no source impedance resolves,
    // the verdict must be UNVERIFIED — never PASS.
    // =========================================================================

    @Test
    fun testAds_unresolvedZe_verdictMustBeUnverifiedNeverPass() {
        val circuit = CircuitEntity(id = 10, circuitName = "UNRESOLVED-ZE", loadKVA = 10.0, length_m = 10.0, overrideDeviceType = "MCB Curve B")
        val sizing = createDummySizing(ib = 10.0, inRating = 16.0, iz = 25.0, icu = 10.0)

        // Pre-fix: calculateAds default parameter was ze_ohm = 0.01, giving "✓ PASS" optimistically.
        // Post-fix: without resolved source impedance, the verdict MUST be UNVERIFIED, never PASS.
        val ads = ProtectionEngine.calculateAds(circuit, sizing)

        assertEquals("ADS verdict must be UNVERIFIED when source impedance is unresolved", "UNVERIFIED", ads.status)
        assertEquals("Disconnection time must be UNVERIFIED when Ze is unresolved", "UNVERIFIED", ads.disconnectionTime)
        assertTrue(
            "Warning must flag unverified Ze",
            ads.warnings.any { it.contains("unverified", ignoreCase = true) || it.contains("Ze", ignoreCase = true) }
        )
    }

    // =========================================================================
    // AUDIT-5 P0 item 3c (C-2): ADS loop with parallel runs.
    // Phase resistance r1 MUST be divided by circuit.parallelRuns.
    // RED LINE: r2 MUST NOT be divided by parallelRuns (unmodelled CPC topology).
    // =========================================================================

    @Test
    fun testParallelRuns_r1DividedByParallelRuns_r2NotDivided() {
        // 4 parallel runs of 70 mm² Cu cable (r1 = 0.268 ohm/km per run -> r1_eff = 0.067 ohm/km).
        // earthCPC = 35 mm² Cu (r2 = 0.524 ohm/km, NOT divided by parallelRuns).
        // length = 370 m, ze = 0.01 ohm.
        // ENG-30 (§12.2-b): tempFactor is no longer a flat 1.20 — this fixture's SizingResult
        // defaults to Cu/XLPE, so the factor is 1.2751 (1 + 0.00393 × (90 − 20)).
        // Pre-factor-fix (old assertion): (0.067 + 0.524) * 370 * 1.20 / 1000 + 0.01
        //     = 0.272402 -> rounds to 0.272 ohm.
        // Post-factor-fix:  (0.067 + 0.524) * 370 * 1.2751 / 1000 + 0.01
        //     = 0.278826 + 0.01 = 0.288826 -> rounds to 0.289 ohm <= maxZs (0.35) -> PASS.
        // Still catches the r1-division bug: without the /4 the same calc gives
        //     0.373657 + 0.01 = 0.383657 -> 0.384 ohm > 0.35 -> "✗ FAIL".
        val originalDataSheet = com.example.data.Catalog.dataSheet
        try {
            com.example.data.Catalog.dataSheet = listOf(
                com.example.data.DataSheetEntry(size = 70.0, R_Cu20 = 0.268, X = 0.08, R_Al20 = 0.443, R_Cu_70_est = 0.322, R_Al_70_est = 0.532),
                com.example.data.DataSheetEntry(size = 35.0, R_Cu20 = 0.524, X = 0.08, R_Al20 = 0.868, R_Cu_70_est = 0.629, R_Al_70_est = 1.042)
            )
            val circuit = CircuitEntity(
                id = 20,
                circuitName = "PARALLEL-FEEDER",
                loadKVA = 50.0,
                length_m = 370.0,
                parallelRuns = 4,
                overrideDeviceType = "MCB Curve C"
            )
            val sizing = createDummySizing(ib = 50.0, inRating = 63.0, iz = 246.0, icu = 10.0, cableSize = "70 mm²")

            val ads = ProtectionEngine.calculateAds(circuit, sizing, ze_ohm = 0.01, isZeResolved = true)

            assertEquals("Max Zs for 63 A Curve C from table is 0.35 ohm", 0.35, ads.maxZsAllowed, 0.001)
            assertEquals(
                "Calculated Zs must divide r1 by 4 but NOT r2, at the Cu/XLPE factor 1.2751: 0.289 ohm",
                0.289, ads.calculatedZs, 0.0005
            )
            assertEquals("Cu/XLPE fixture must use the 1.2751 temperature factor", 1.2751, ads.tempFactor, 1e-9)
            assertEquals("Verdict must PASS with parallel r1 reduction", "✓ PASS", ads.status)
            assertEquals("Disconnection time must be < 0.4s", "< 0.4s", ads.disconnectionTime)
        } finally {
            com.example.data.Catalog.dataSheet = originalDataSheet
        }
    }

    // =========================================================================
    // AUDIT-5 P0 item 4 (E-25, E-10): Typed conductor data.
    // Busway or unparseable text must NOT become 800 mm² or silently fallback to 1.5 mm².
    // Fallback in ProtectionEngine must fail loudly with DATA MISSING.
    // =========================================================================

    @Test
    fun testEvaluateProtection_buswayOrUnparseableText_failsLoudlyWithDataMissing() {
        val circuit = CircuitEntity(id = 30, circuitName = "BUSWAY-FEEDER", loadKVA = 500.0)
        // 800A Busway (Cu): pre-fix extractNumericSize extracts 800.0 -> k2S2 computed with 800 mm² -> PASS
        val sizingBusway = createDummySizing(
            ib = 721.7,
            inRating = 800.0,
            iz = 800.0,
            icu = 50.0,
            cableSize = "800A Busway (Cu)"
        )
        val sc = createDummyShortCircuit(circuitId = 30, iscKA = 25.0, status = "PASS")

        val resultBusway = ProtectionEngine.evaluateProtection(circuit, sizingBusway, sc)

        assertEquals("DATA MISSING", resultBusway.cableWithstandStatus)
        assertEquals("DATA MISSING", resultBusway.finalStatus)
        assertTrue(resultBusway.messages.any { it.contains("DATA MISSING", ignoreCase = true) })

        // Unparseable text: pre-fix extractNumericSize fell back to ?: 1.5 mm² -> (143*1.5)^2 >= (500)^2*0.1 -> PASS
        val sizingUnparseable = createDummySizing(
            ib = 10.0,
            inRating = 16.0,
            iz = 25.0,
            icu = 10.0,
            cableSize = "UnparseableConductor"
        )
        val scLow = createDummyShortCircuit(circuitId = 30, iscKA = 0.5, status = "PASS")
        val resultUnparseable = ProtectionEngine.evaluateProtection(circuit, sizingUnparseable, scLow)

        assertEquals("DATA MISSING", resultUnparseable.cableWithstandStatus)
        assertEquals("DATA MISSING", resultUnparseable.finalStatus)
        assertTrue(resultUnparseable.messages.any { it.contains("DATA MISSING", ignoreCase = true) })
    }
}

