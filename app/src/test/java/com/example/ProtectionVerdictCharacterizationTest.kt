package com.example

import com.example.data.Catalog
import com.example.data.ProjectEntity
import com.example.data.TransformerEntity
import com.example.engine.SizingResultStatus
import com.example.pdf.ProjectReportSnapshot
import com.example.pdf.ReportOptions
import com.example.pdf.circuitTableStatus
import com.example.pdf.executiveSummaryStatus
import com.example.ui.screens.circuits.CircuitUiModelMapper
import com.example.ui.screens.circuits.CircuitUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File

/**
 * ECT-EE-06 §3 — CHARACTERIZATION of the safety-critical FALSE PASS,
 * plus the §4 target, the §11 matrix and the §14 edge cases.
 *
 * ============================================================================
 * WHAT WAS PROVEN (on the UNMODIFIED code)
 * ============================================================================
 *
 * A circuit may satisfy every sizing criterion
 *
 *     Ib <= In                      (candidate correctly selected)
 *     ampacity   OK
 *     voltage drop OK
 *
 * while its protective device is *physically unable* to interrupt the
 * prospective fault current available at its own installation point
 *
 *     Isc > Icu
 *
 * and the application still presents the circuit as **PASS**.
 *
 * The authoritative protection verdict already said FAIL —
 * `ProtectionEngine.evaluateProtection` returns `finalStatus == "FAIL"` — but the
 * presentation boundary never received it:
 *
 *     CableSizingEngine.kt:867   isPass = isAmpacityPass && isVdPass   (no protection)
 *     CableSizingEngine.kt:875   resStatus = PASS | FAIL               (no protection)
 *     CircuitUiModelMapper.kt:71 status = resolveCircuitStatus(sizingResult)
 *     CircuitUiModelMapper.kt:27 resolveCircuitStatus read ONLY `resultStatus`
 *                                and was never handed a ProtectionResult
 *
 * ============================================================================
 * RECORD OF THE UNMODIFIED-RUN EVIDENCE (§3 acceptance)
 * ============================================================================
 *
 * The characterization test **PASSED on the unmodified code** before any production
 * file was touched:
 *
 *     Run    : gradlew testDebugUnitTest -Pprofile=gate --rerun-tasks --no-build-cache
 *     Result : BUILD SUCCESSFUL, EXITCODE=0 — 3 tests, 0 failures
 *     XML    : 10/05/2026 15:34:39
 *     before-SHA 4da9e0341ac5b8c41530ef5ff592a80462e03197 (worktree engine diff = 0,
 *              Tier F 27/27 at the time of the run)
 *
 * Observed values captured at that run (full table in §3 of `docs/audit/EE-06.md`):
 *
 *     breaker candidate = Schneider MCB  In = 16.0 A   Icu = 10.0 kA
 *     Ib  = 2.89 A     In = 16.0 A     Iz = 47.5 A
 *     Isc = 19.10 kA   Icu = 10.0 kA   ->  Isc > Icu
 *     ProtectionResult.finalStatus        = FAIL      (already correct)
 *     circuit final status (mapper)       = PASS      (the defect)
 *     project summary                     = pass 1 / fail 0
 *     Executive Summary                   = PASS      (the defect)
 *
 * The three assertions that pinned it were all named `[THE FALSE PASS]` and all PASSed.
 * They are now inverted to the D5 contract below — re-pointed, not weakened. The
 * precondition blocks (sizing genuinely PASS, Ib <= In, authoritative verdict genuinely
 * FAIL) are unchanged, so the fixture can never pass through missing data.
 *
 * ============================================================================
 * FIXTURE
 * ============================================================================
 *
 * All real production paths and committed project data — see
 * [ProtectionVerdictFixture], which deliberately depends only on pre-existing APIs so
 * that the §4 target test also compiles against the unmodified checkout.
 *
 * TOPOLOGY NOTE (documented deviation, not a data change): the QA case carries
 * `fedFrom = "MDB-1"`. EE-06 models this way directly on the transformer LV busbar
 * (`fedFrom = "Transformer"`, `transformerId = "TX-01"`) so that `ShortCircuitEngine`
 * resolves a real source; an unresolvable `fedFrom` would yield
 * "Transformer source not found" -> DATA MISSING, which is not the state under test.
 *
 * ============================================================================
 * WHY THE NUMBERS OCCUR NATURALLY
 * ============================================================================
 *
 * All 52 MCBs in the committed breaker catalogue carry `icuKa = 10`
 * (Schneider Acti9 iC60N, 6…63 A). A 630 kVA / 5 % transformer has
 *
 *     Zt = (U² / S) · (%Z/100) = (400² / 630 000) · 0.05 = 12.70 mΩ
 *     Isc = 1.05·U / (√3·Zt)                              ≈ 19.1 kA
 *
 * so `Isc ≈ 19.1 kA > Icu = 10 kA` arises from the shipped catalogue and the shipped
 * engine, with no value chosen to force the outcome. This is the ordinary real-world
 * case of 10 kA MCBs installed on a distribution board fed by a stiff transformer.
 */
class ProtectionVerdictCharacterizationTest {

    companion object {
        @JvmField
        @ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        @BeforeClass
        @JvmStatic
        fun setUp() = ProtectionVerdictFixture.install()
    }

    private fun runPipeline(
        qaCaseId: String = "TC-CABLE-003",
        txKva: Double = 630.0
    ): ProtectionVerdictFixture.Pipeline =
        ProtectionVerdictFixture.runPipeline(qaCaseId, txKva)

    private fun runProtectionWithoutIsc() = ProtectionVerdictFixture.runProtectionWithoutIsc()

    // ==================================================================
    // §3 — CHARACTERIZATION of the false PASS.
    //
    // On the unmodified code every assertion in the "recorded values" and
    // "authoritative verdict" blocks below PASSed *together with* three
    // "[THE FALSE PASS]" assertions. Those three are now re-pointed.
    // ==================================================================
    @Test
    fun characterization_sizingPassWithIscAboveIcu_stillPresentsPass_onUnmodifiedCode() {
        val p = runPipeline()

        // ── Recorded values (§3 requires all of these be captured) ──────────
        val breakerCandidates = Catalog.breakers
            .filter { it.type.equals("MCB", ignoreCase = true) && it.rating_A >= p.sizing.deviceSize_A }
            .sortedBy { it.rating_A }
        val selected = breakerCandidates.firstOrNull()

        println("======================================================================")
        println("ECT-EE-06 CHARACTERIZATION — false PASS on unmodified code")
        println("======================================================================")
        println("  breaker candidate (catalogue order) : ${selected?.mfg} ${selected?.type} In=${selected?.rating_A}A Icu=${selected?.icu_kA}kA")
        println("  breaker candidate (engine selection): ${p.sizing.deviceType} In=${p.sizing.deviceSize_A}A")
        println("  Ib                                   : ${p.sizing.Ib_A} A")
        println("  In  (deviceSize_A)                   : ${p.sizing.deviceSize_A} A")
        println("  Iz  (capacity_derated_Iz_A)          : ${p.sizing.capacity_derated_Iz_A} A")
        println("  ampacity OK                          : ${p.sizing.isAmpacityOk}")
        println("  voltage drop OK                      : ${p.sizing.isVoltageDropOk}")
        println("  cumulative VD%                       : ${p.sizing.cumulativeVDpct} %")
        println("  SizingResult.resultStatus            : ${p.sizing.resultStatus}")
        println("  SizingResult.status (string)         : ${p.sizing.status}")
        println("  ----------------------------------------------------------------")
        println("  TX-01 rt=${p.tx.rt} mOhm xt=${p.tx.xt} mOhm")
        println("  transformer Isc3ph                   : ${p.tx.isc3Phase} kA")
        println("  Isc (near end, ShortCircuitResult)   : ${p.sc.iscKA} kA")
        println("  Icu (SizingResult.deviceBreakingKA)  : ${p.sizing.deviceBreakingKA} kA")
        println("  Isc > Icu                            : ${p.sc.iscKA > p.sizing.deviceBreakingKA}")
        println("  ----------------------------------------------------------------")
        println("  ProtectionResult.overloadStatus      : ${p.protection.overloadStatus}")
        println("  ProtectionResult.shortCircuitStatus  : ${p.protection.shortCircuitStatus}")
        println("  ProtectionResult.cableWithstandStatus: ${p.protection.cableWithstandStatus}")
        println("  ProtectionResult.finalStatus         : ${p.protection.finalStatus}")
        println("  ----------------------------------------------------------------")
        println("  circuit final status (mapper)        : ${CircuitUiModelMapper.resolveCircuitStatus(p.sizing, p.protection)}")
        println("  executive summary                    : ${executiveSummaryStatus(snapshotFor(p, mapOf(p.circuit.id to p.protection)))}")
        println("======================================================================")

        // §3 "Record: ..." — also written to a record file when the harness exports
        // EE06_RECORD, so the audit carries verbatim observed evidence.
        val recordPath = System.getenv("EE06_RECORD")
        if (!recordPath.isNullOrBlank()) {
            File(recordPath).writeText(
                buildString {
                    appendLine("case=TC-CABLE-003 (LIGHT-1), transformer=TX-01 (630 kVA, %Z=5.0, X/R=6.0)")
                    appendLine("breakerCandidate=" + selected?.mfg + " " + selected?.type + " In=" + selected?.rating_A + "A Icu=" + selected?.icu_kA + "kA")
                    appendLine("engineSelected=" + p.sizing.deviceType + " In=" + p.sizing.deviceSize_A + "A")
                    appendLine("Ib_A=" + p.sizing.Ib_A)
                    appendLine("In_A=" + p.sizing.deviceSize_A)
                    appendLine("Iz_A=" + p.sizing.capacity_derated_Iz_A)
                    appendLine("ampacityOk=" + p.sizing.isAmpacityOk)
                    appendLine("voltageDropOk=" + p.sizing.isVoltageDropOk)
                    appendLine("cumulativeVDpct=" + p.sizing.cumulativeVDpct)
                    appendLine("sizingResultStatus=" + p.sizing.resultStatus)
                    appendLine("sizingStatusString=" + p.sizing.status)
                    appendLine("tx_rt_mOhm=" + p.tx.rt)
                    appendLine("tx_xt_mOhm=" + p.tx.xt)
                    appendLine("tx_isc3Phase_kA=" + p.tx.isc3Phase)
                    appendLine("Isc_kA=" + p.sc.iscKA)
                    appendLine("Icu_kA=" + p.sizing.deviceBreakingKA)
                    appendLine("IscGtIcu=" + (p.sc.iscKA > p.sizing.deviceBreakingKA))
                    appendLine("protection_overload=" + p.protection.overloadStatus)
                    appendLine("protection_shortCircuit=" + p.protection.shortCircuitStatus)
                    appendLine("protection_cableWithstand=" + p.protection.cableWithstandStatus)
                    appendLine("protection_finalStatus=" + p.protection.finalStatus)
                    appendLine("circuitFinalStatus=" + CircuitUiModelMapper.resolveCircuitStatus(p.sizing, p.protection))
                    val s = CircuitUiModelMapper.mapToSummaryUiModel(
                        listOf(p.circuit),
                        mapOf(p.circuit.id to p.sizing),
                        mapOf(p.circuit.id to p.protection)
                    )
                    appendLine("summary_passCount=" + s.passCount)
                    appendLine("summary_failCount=" + s.failCount)
                    appendLine("executiveSummaryStatus=" + executiveSummaryStatus(snapshotFor(p, mapOf(p.circuit.id to p.protection))))
                }
            )
        }

        // ── Pre-conditions: normal sizing genuinely succeeds (D5: unchanged) ──
        assertEquals("candidate is an MCB", "MCB", p.sizing.deviceType)
        assertEquals("candidate rating In comes from the shipped catalogue", 16.0, p.sizing.deviceSize_A, 0.0)
        assertEquals("selected catalogue entry rating matches In", 16.0, selected!!.rating_A, 0.0)
        assertEquals("Icu of every 16 A MCB in the shipped catalogue", 10.0, p.sizing.deviceBreakingKA, 1e-9)
        assertTrue("D5 normal sizing: Ib <= In", p.sizing.Ib_A <= p.sizing.deviceSize_A)
        assertTrue("D5 normal sizing: ampacity satisfied", p.sizing.isAmpacityOk)
        assertTrue("D5 normal sizing: voltage drop satisfied", p.sizing.isVoltageDropOk)
        assertEquals("sizing verdict is PASS", SizingResultStatus.PASS, p.sizing.resultStatus)
        assertEquals("sizing status string is the production PASS label", "✓ PASS", p.sizing.status)

        // ── The physical facts ──
        assertTrue(
            "Isc (${p.sc.iscKA} kA) must exceed Icu (${p.sizing.deviceBreakingKA} kA) for this scenario",
            p.sc.iscKA > p.sizing.deviceBreakingKA
        )
        assertTrue("protection executed", p.protection.finalStatus.isNotBlank())

        // ── The authoritative verdict is correct and was always correct ──
        assertEquals(
            "ProtectionEngine returns FAIL for Isc > Icu",
            "FAIL", p.protection.finalStatus
        )
        assertEquals("short-circuit domain is FAIL", "FAIL", p.protection.shortCircuitStatus)

        // ════════════════════════════════════════════════════════════════════
        // RE-POINTED AFTER THE D5 FIX — documented, NOT weakened.
        // The three assertions that previously appeared here read
        //     circuit status = PASS, passCount = 1, exec summary = PASS
        // and were named "[THE FALSE PASS]". They PASSED on the unmodified code
        // at 15:34:39 on before-SHA 4da9e03 (see the header above and §3 of
        // docs/audit/EE-06.md).
        // ════════════════════════════════════════════════════════════════════

        assertEquals(
            "D5 Case B/C: Isc > Icu makes the final circuit verdict FAIL",
            CircuitUiStatus.FAIL,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, p.protection)
        )

        assertEquals(
            "D5 Case E: absent protection result resolves to INCOMPLETE, never PASS",
            CircuitUiStatus.INCOMPLETE,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing)
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(
            listOf(p.circuit),
            mapOf(p.circuit.id to p.sizing),
            mapOf(p.circuit.id to p.protection)
        )
        assertEquals("project summary must not count a protection FAIL as PASS", 0, summary.passCount)
        assertEquals("project summary must count the protection FAIL", 1, summary.failCount)

        val summaryWithoutProtection = CircuitUiModelMapper.mapToSummaryUiModel(
            listOf(p.circuit),
            mapOf(p.circuit.id to p.sizing)
        )
        assertEquals("sizing-only projection must not report PASS", 0, summaryWithoutProtection.passCount)
    }

    // ------------------------------------------------------------------
    // §12 helper — the PDF Executive Summary / circuits table surface.
    // ------------------------------------------------------------------
    private fun snapshotFor(
        p: ProtectionVerdictFixture.Pipeline,
        protectionResults: Map<Int, com.example.engine.ProtectionResult>
    ): ProjectReportSnapshot = ProjectReportSnapshot(
        project = ProjectEntity(projectId = "PRJ_EE06", projectName = "EE-06"),
        circuits = listOf(p.circuit),
        transformers = listOf(TransformerEntity(projectId = "PRJ_EE06", transformerId = "TX-01")),
        sizingResults = mapOf(p.circuit.id to p.sizing),
        shortCircuitResults = mapOf(p.circuit.id to p.sc),
        conduitResults = emptyMap(),
        conduitProcurement = emptyList(),
        protectionResults = protectionResults,
        transformerResults = mapOf("TX-01" to p.tx),
        options = ReportOptions()
    )

    // The §4 target itself lives in [ProtectionVerdictTargetTest], which uses only
    // the pre-existing one-argument `resolveCircuitStatus(sizingResult)` signature
    // so that it compiles against — and therefore FAILS on — the unmodified
    // implementation, as §4 requires.

    // ==================================================================
    // §11-B / §11-C — protection finalStatus FAIL ⇒ final circuit FAIL,
    // overriding an otherwise fully passing sizing result.
    // ==================================================================
    @Test
    fun target_protectionFinalStatusFail_isTheFinalCircuitVerdict() {
        val p = runPipeline()

        assertEquals(SizingResultStatus.PASS, p.sizing.resultStatus)
        assertEquals("✓ PASS", p.sizing.status)
        assertEquals("FAIL", p.protection.finalStatus)

        assertEquals(
            "§11-B/C: ProtectionResult.finalStatus == FAIL ⇒ final circuit FAIL",
            CircuitUiStatus.FAIL,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, p.protection)
        )

        val card = CircuitUiModelMapper.mapToCardUiModel(
            circuit = p.circuit,
            sizingResult = p.sizing,
            shortCircuitResult = p.sc,
            allCircuits = listOf(p.circuit),
            hasChildren = false,
            protectionResult = p.protection
        )
        assertEquals("§11-C: the circuit card reports FAIL, not PASS", CircuitUiStatus.FAIL, card.status)

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(
            listOf(p.circuit),
            mapOf(p.circuit.id to p.sizing),
            mapOf(p.circuit.id to p.protection)
        )
        assertEquals("§11-C: summary passCount", 0, summary.passCount)
        assertEquals("§11-C: summary failCount", 1, summary.failCount)

        assertEquals(
            "§12: the PDF circuits table row must not say PASS",
            "FAIL",
            circuitTableStatus(p.sizing, p.protection)
        )
    }

    // ==================================================================
    // §11-D — Isc required but unavailable ⇒ UNVERIFIED / INCOMPLETE.
    // Production path: `EngineeringCalculationCoordinator.calculateProjectProtection`
    // passes `shortCircuitResults[circuit.id]`, which may be null.
    // ==================================================================
    @Test
    fun missingIsc_resolvesToIncomplete_neverPass() {
        val p = runPipeline()
        val withoutIsc = runProtectionWithoutIsc()

        assertEquals(
            "§11-D: protection reports missing short-circuit data",
            "DATA MISSING",
            withoutIsc.finalStatus
        )

        assertEquals(
            "§11-D: missing Isc ⇒ INCOMPLETE, never PASS",
            CircuitUiStatus.INCOMPLETE,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, withoutIsc)
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(
            listOf(p.circuit),
            mapOf(p.circuit.id to p.sizing),
            mapOf(p.circuit.id to withoutIsc)
        )
        assertEquals("§11-D: must not be counted PASS", 0, summary.passCount)
        assertEquals("§11-D: counted as standby/INCOMPLETE", 1, summary.standbyCount)
    }

    // ==================================================================
    // §11-E — protection evaluation did not run ⇒ UNVERIFIED / INCOMPLETE.
    // Production path: `calculateProjectProtection` skips a circuit with no sizing
    // result, and a pass that has not run leaves the map empty.
    // ==================================================================
    @Test
    fun protectionNotExecuted_resolvesToIncomplete_neverPass() {
        val p = runPipeline()

        assertEquals(
            "§11-E: no ProtectionResult ⇒ INCOMPLETE, never PASS",
            CircuitUiStatus.INCOMPLETE,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, null)
        )

        val card = CircuitUiModelMapper.mapToCardUiModel(
            circuit = p.circuit,
            sizingResult = p.sizing,
            shortCircuitResult = p.sc,
            allCircuits = listOf(p.circuit),
            hasChildren = false,
            protectionResult = null
        )
        assertEquals("§11-E: card is INCOMPLETE", CircuitUiStatus.INCOMPLETE, card.status)

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(
            listOf(p.circuit),
            mapOf(p.circuit.id to p.sizing)
        )
        assertEquals("§11-E: not PASS", 0, summary.passCount)
        assertEquals("§11-E: not FAIL either — protection simply did not run", 0, summary.failCount)
        assertEquals("§11-E: standby/INCOMPLETE", 1, summary.standbyCount)
    }

    // ==================================================================
    // §12 — end-to-end `Isc > Icu ⇒ final FAIL` at the Executive Summary
    // (PDF) consumer boundary.
    // ==================================================================
    @Test
    fun executiveSummary_reportsFailWhenProtectionFails() {
        val p = runPipeline()

        assertTrue("precondition: Isc > Icu", p.sc.iscKA > p.sizing.deviceBreakingKA)
        assertEquals("precondition: protection FAIL", "FAIL", p.protection.finalStatus)

        assertEquals(
            "§12: Executive Summary must report FAIL for Isc > Icu",
            "FAIL",
            executiveSummaryStatus(snapshotFor(p, mapOf(p.circuit.id to p.protection)))
        )

        assertNotEquals(
            "§12: Executive Summary must not report PASS without a protection verdict",
            "PASS",
            executiveSummaryStatus(snapshotFor(p, emptyMap()))
        )
    }

    // ==================================================================
    // §11-F — GENUINE end-to-end PASS regression.
    //
    // A different committed QA case (TC-CABLE-006 / REMOTE-PUMP, 16 mm² cable)
    // on a 100 kVA transformer puts ≈3.0 kA at the circuit, inside both the 10 kA
    // Icu of its MCB and the cable's adiabatic withstand, so every protection
    // domain passes. This proves the D5 fix does not turn normal operation into
    // a failure.
    // ==================================================================
    @Test
    fun genuinePass_protectionPassesAndEverySurfaceShowsPass() {
        val p = runPipeline(qaCaseId = "TC-CABLE-006", txKva = 100.0)

        assertEquals("sizing verdict stays PASS", SizingResultStatus.PASS, p.sizing.resultStatus)
        assertTrue("Ib <= In", p.sizing.Ib_A <= p.sizing.deviceSize_A)
        assertTrue("Isc within Icu", p.sc.iscKA < p.sizing.deviceBreakingKA)
        assertEquals("all protection domains pass", "PASS", p.protection.finalStatus)

        assertEquals(
            "card reports PASS",
            CircuitUiStatus.PASS,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, p.protection)
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(
            listOf(p.circuit),
            mapOf(p.circuit.id to p.sizing),
            mapOf(p.circuit.id to p.protection)
        )
        assertEquals("summary passCount = 1", 1, summary.passCount)
        assertEquals("summary failCount = 0", 0, summary.failCount)

        assertEquals(
            "Executive Summary reports PASS",
            "PASS",
            executiveSummaryStatus(snapshotFor(p, mapOf(p.circuit.id to p.protection)))
        )
        assertEquals(
            "PDF circuits table row reports PASS",
            p.sizing.status,
            circuitTableStatus(p.sizing, p.protection)
        )
    }

    // ==================================================================
    // §11-F NORMAL-SIZING REGRESSION — must stay green after the fix.
    // Proves the scenario genuinely passes for the sizing domains, so the D5 fix
    // cannot be "solved" by turning everything into FAIL.
    // ==================================================================
    @Test
    fun normalSizingCase_isAPassForAmpacityAndVoltageDrop() {
        val p = runPipeline()
        assertEquals(SizingResultStatus.PASS, p.sizing.resultStatus)
        assertTrue("ampacity domain passes", p.sizing.isAmpacityOk)
        assertTrue("voltage-drop domain passes", p.sizing.isVoltageDropOk)
        assertTrue(
            "VD is within the circuit limit (maxVDpct is a fraction; limit% = maxVDpct*100)",
            p.sizing.cumulativeVDpct <= p.circuit.maxVDpct * 100.0
        )
        assertTrue("overload domain passes (Ib <= In <= Iz)", p.protection.overloadStatus == "PASS")
        assertTrue("Ib <= In", p.sizing.Ib_A <= p.sizing.deviceSize_A)
    }

    // ==================================================================
    // §11-G CANDIDATE-SELECTION NON-REGRESSION (D5) — must stay green.
    // The selected breaker is chosen by normal current sizing (In >= Ib) and
    // EE-06 must not up-size it.
    // ==================================================================
    @Test
    fun candidateSelection_isNormalCurrentSizing_andNotIcu() {
        val p = runPipeline()

        val candidates = Catalog.breakers
            .filter { it.type.equals("MCB", ignoreCase = true) && it.rating_A >= p.sizing.deviceSize_A }
            .sortedBy { it.rating_A }
        assertTrue("shipped catalogue offers a candidate", candidates.isNotEmpty())
        assertEquals(
            "engine selected the smallest In >= deviceSizeA — Icu plays no part",
            candidates.first().rating_A, p.sizing.deviceSize_A, 0.0
        )
        assertEquals("MCB", p.sizing.deviceType)
        assertEquals(16.0, p.sizing.deviceSize_A, 0.0)
        assertTrue("Ib <= In (normal sizing holds)", p.sizing.Ib_A <= p.sizing.deviceSize_A)

        // D5: no auto-upsize. The under-rated candidate must still be the one
        // reported by sizing, even though Isc exceeds its Icu.
        assertTrue("scenario really is under-rated (Isc > Icu)", p.sc.iscKA > p.sizing.deviceBreakingKA)
        assertEquals(
            "the breaker is NOT silently replaced by a larger one",
            16.0, p.sizing.deviceSize_A, 0.0
        )
        assertEquals(
            "the breaker is NOT silently replaced by a larger one",
            "MCB", p.sizing.deviceType
        )
    }

    // ==================================================================
    // §14.1 — Isc == Icu is NOT a short-circuit failure.
    // The authoritative comparison lives in ProtectionEngine (`icu >= isc - 0.001`)
    // and is untouched by EE-06; this pins that equality still yields PASS, so the
    // fix has not made the boundary stricter.
    // ==================================================================
    @Test
    fun edge_iscEqualsIcu_isNotAShortCircuitFailure() {
        val p = runPipeline()

        val scAtEquality = p.sc.copy(iscKA = p.sizing.deviceBreakingKA)
        val protectionAtEquality = com.example.engine.ProtectionEngine.evaluateProtection(
            circuit = p.circuit,
            sizingResult = p.sizing,
            shortCircuitResult = scAtEquality
        )

        assertEquals(
            "§14.1: Isc == Icu passes the short-circuit comparison",
            "PASS",
            protectionAtEquality.shortCircuitStatus
        )
        assertEquals(
            "§14.1: recorded Isc and Icu really are equal",
            p.sizing.deviceBreakingKA,
            scAtEquality.iscKA,
            0.0
        )
    }

    // ==================================================================
    // §14.4 — a zero fault level is never a PASS.
    // ==================================================================
    @Test
    fun edge_zeroIsc_neverProducesPass() {
        val p = runPipeline()
        val protection = com.example.engine.ProtectionEngine.evaluateProtection(
            circuit = p.circuit,
            sizingResult = p.sizing,
            shortCircuitResult = p.sc.copy(iscKA = 0.0)
        )

        assertNotEquals(
            "§14.4: Isc == 0 must not yield a short-circuit PASS",
            "PASS",
            protection.shortCircuitStatus
        )
        assertNotEquals(
            "§14.4: Isc == 0 must never present as a PASS circuit",
            CircuitUiStatus.PASS,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, protection)
        )
    }

    // ==================================================================
    // §14.2 / §14.3 — a missing protection or short-circuit verdict never passes.
    // ==================================================================
    @Test
    fun edge_missingProtectionOrIsc_neverProducesPass() {
        val p = runPipeline()

        assertNotEquals(
            "§14.2: no Icu (absent ProtectionResult) ⇒ not PASS",
            CircuitUiStatus.PASS,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, null)
        )
        assertNotEquals(
            "§14.3: no Isc (protection reports DATA MISSING) ⇒ not PASS",
            CircuitUiStatus.PASS,
            CircuitUiModelMapper.resolveCircuitStatus(p.sizing, runProtectionWithoutIsc())
        )
    }

    // ==================================================================
    // §14.8 — no sizing result preserves the existing STANDBY semantics.
    // ==================================================================
    @Test
    fun edge_noSizingResult_staysStandby() {
        val p = runPipeline()

        assertEquals(
            "§14.8: no sizing result ⇒ STANDBY (unchanged)",
            CircuitUiStatus.STANDBY,
            CircuitUiModelMapper.resolveCircuitStatus(null, null)
        )
        assertEquals(
            "§14.8: STANDBY even when a protection result is present",
            CircuitUiStatus.STANDBY,
            CircuitUiModelMapper.resolveCircuitStatus(null, p.protection)
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(listOf(p.circuit), emptyMap())
        assertEquals("§14.8: counted as standby", 1, summary.standbyCount)
        assertEquals("§14.8: not PASS", 0, summary.passCount)
        assertEquals("§14.8: not FAIL", 0, summary.failCount)
    }
}
