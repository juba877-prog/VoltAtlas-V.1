package com.example

import com.example.data.CircuitEntity
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.catalog.CatalogRepository
import com.example.engine.CableSizingEngine
import com.example.engine.CoordinationEngine
import com.example.engine.ProtectionEngine
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Phase 3 (P2/P3) remediation regression tests:
 *  - 3.1 H2: temperature–resistance convention documented (catalog R columns = IEC 60228 R20)
 *  - 3.2 H4: insulation-aware k fallback (Cu/PVC = 115, not 143)
 *  - 3.3: IEC 60364-5-52 Annex E harmonic phase derating (×0.86) + full-size neutral
 *  - 3.4 H5: selectivity screening wording + MAGNETIC_RATIO_MIN in status logic
 */
class Phase3RemediationTest {

    companion object {
        @JvmField
        @org.junit.ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        private val repository = CatalogRepository()

        private fun findAssetFile(filename: String): File {
            val candidates = listOf(
                File("app/src/main/assets/$filename"),
                File("src/main/assets/$filename"),
                File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: throw java.io.FileNotFoundException("Asset file not found: $filename")
        }

        @BeforeClass
        @JvmStatic
        fun setUpRepository() {
            val diag = repository.loadAllFromStrings(
                findAssetFile("cable_catalog.json").readText(),
                findAssetFile("cable_types_data.json").readText(),
                findAssetFile("breaker_catalog.json").readText(),
                findAssetFile("accessory_catalog.json").readText(),
                findAssetFile("derating_data.json").readText(),
                findAssetFile("transformers_seed.json").readText()
            )
            assertEquals("COMPLETE", diag.status)

            try {
                val array = JSONArray(findAssetFile("cable_types.json").readText())
                val list = mutableListOf<CableType>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(CableType(
                        code = obj.getInt("code"),
                        cableType = obj.getString("cableType"),
                        conductor = obj.getString("conductor"),
                        insulation = obj.getString("insulation"),
                        armour = obj.getString("armour"),
                        cores = obj.getString("cores"),
                        voltage = obj.getString("voltage"),
                        standard = obj.getString("standard"),
                        rColumn = obj.getString("rColumn")
                    ))
                }
                Catalog.cableTypes = list
            } catch (_: Exception) {
            }
        }

        private fun circuit(id: Int, harmonic: Boolean) = CircuitEntity(
            id = id, circuitName = if (harmonic) "HARM-CIRCUIT" else "BASE-CIRCUIT",
            loadKVA = 20.0, pf = 0.8, phases = "3P+N", voltageV = 400, length_m = 15.0,
            safetyFactor = 1.0, installation = "Laid in Ducts",
            cableType = "Cu/XLPE/STA/PVC - 4C", manufacturer = "Elsewedy",
            parallelRuns = 1, gndTempC = 25, isHarmonic = harmonic
        )
    }

    // ------------------------------------------------------------------
    // 3.3 — IEC 60364-5-52 Annex E harmonic phase derating + full-size neutral
    // ------------------------------------------------------------------

    @Test
    fun testHarmonicCircuit_fullNeutral_andDeratedPhaseAmpacity_andWarning() {
        val base = CableSizingEngine.sizeCircuit(circuit(301, harmonic = false), 0.0, repository)
        val harm = CableSizingEngine.sizeCircuit(circuit(302, harmonic = true), 0.0, repository)

        assertNotNull(base)
        assertNotNull(harm)

        // Full-size neutral on the harmonic circuit
        assertEquals(
            "Harmonic circuit must use a FULL-size neutral",
            harm.cableSize_mm2, harm.neutral_mm2
        )
        // Phase ampacity derated ×0.86 (Annex E, 15–33 % band) — same size => exact ratio
        if (base.cableSize_mm2 == harm.cableSize_mm2) {
            assertEquals(
                "Same-size harmonic circuit must show exactly ×0.86 phase ampacity",
                base.capacity_derated_Iz_A * 0.86, harm.capacity_derated_Iz_A, 0.6
            )
        } else {
            // Derating forced an upsize — the harmonic circuit must still pass and be larger
            assertTrue(harm.status.contains("PASS"))
            assertTrue(
                "Harmonic upsize must be >= base size",
                harm.cableSize_mm2.toDoubleOrNull() ?: 0.0 >= base.cableSize_mm2.toDoubleOrNull() ?: 0.0
            )
        }
        // Assumption must be flagged (Boolean flag => 15–33 % band assumed)
        assertTrue(
            "Harmonic derating assumption must be warned",
            harm.warnings.any { it.contains("0.86") && it.contains("60364-5-52") }
        )
        // Non-harmonic twin must be unaffected (no warnings, reduced neutral allowed)
        assertTrue(base.warnings.isEmpty())
    }

    // ------------------------------------------------------------------
    // 3.2 H4 — insulation-aware k fallback (IEC 60364-5-54)
    // ------------------------------------------------------------------

    @Test
    fun testInsulationKFactor_table() {
        assertEquals(143.0, ProtectionEngine.insulationKFactor("Cu", "XLPE"), 1e-9)
        assertEquals(115.0, ProtectionEngine.insulationKFactor("Cu", "PVC"), 1e-9)
        assertEquals(94.0, ProtectionEngine.insulationKFactor("Al", "XLPE"), 1e-9)
        assertEquals(76.0, ProtectionEngine.insulationKFactor("Al", "PVC"), 1e-9)
    }

    @Test
    fun testKFallback_insulationAware_notBlindlyCuXlpe() {
        // Cu/PVC 25 mm², kFactor NOT supplied (0.0):
        //  k=115 (correct): k²S² = 115²·25² = 8.27e6 < Isc²t = 10300²·0.08 = 8.49e6 -> FAIL
        //  k=143 (legacy fallback): 143²·25² = 12.78e6 >= 8.49e6 -> PASS (overstated!)
        fun protSizing(kFactor: Double) = SizingResult(
            Ib_A = 50.0, deviceSize_A = 63.0, deviceType = "MCCB", cTotalDerating = 1.0,
            iRequired_perCable_A = 50.0, productCode = "P", cableSize_mm2 = "25 mm²",
            ratedI_table_A = 80.0, capacity_derated_Iz_A = 80.0, utilizationPct = 62.5,
            neutral_mm2 = "16 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 1.0, vdPct = 0.25,
            cumulativeVDpct = 0.25, status = "✓ PASS", failReason = "", cableMaterial = "Cu",
            insulation = "PVC", kFactor = kFactor, deviceBreakingKA = 36.0, cableOD_mm = 20.0,
            clearingTime_s = 0.08, rUsed = 0.727, xUsed = 0.109,
            resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true,
            primaryFailureCode = null
        )
        val circuit = CircuitEntity(id = 310, circuitName = "PVC-FEEDER", loadKVA = 28.0)
        val sc = ShortCircuitResult(
            circuitId = 310, sourceName = "Transformer", cableMm2 = "25 mm²",
            iscKA = 10.3, iwKA = 30.0, deviceKA = 36.0, deviceCheck = "PASS",
            cableCheck = "PASS", marginPct = 20.0, status = "PASS", note = ""
        )

        val withLegacyK = ProtectionEngine.evaluateProtection(
            circuit, protSizing(kFactor = 143.0), sc, upstreamIn_A = 0.0
        )
        assertEquals("k=143 supplied explicitly must still be honoured", "PASS", withLegacyK.cableWithstandStatus)

        val withFallback = ProtectionEngine.evaluateProtection(
            circuit, protSizing(kFactor = 0.0), sc, upstreamIn_A = 0.0
        )
        assertEquals(
            "Missing k on a Cu/PVC cable must fall back to 115 (withstand FAILS), not 143",
            "FAIL", withFallback.cableWithstandStatus
        )
    }

    // ------------------------------------------------------------------
    // 3.4 H5 — selectivity screening wording + MAGNETIC_RATIO_MIN in status
    // ------------------------------------------------------------------

    private fun coordSizing(deviceSize_A: Double) = SizingResult(
        Ib_A = deviceSize_A * 0.8, deviceSize_A = deviceSize_A, deviceType = "MCCB",
        cTotalDerating = 1.0, iRequired_perCable_A = deviceSize_A * 0.8, productCode = "P",
        cableSize_mm2 = "70 mm²", ratedI_table_A = 300.0, capacity_derated_Iz_A = 300.0,
        utilizationPct = 75.0, neutral_mm2 = "35 mm²", earthCPC_mm2 = "35 mm²",
        voltDrop_V = 1.0, vdPct = 0.25, cumulativeVDpct = 0.25, status = "✓ PASS",
        failReason = "", cableMaterial = "Cu", cableOD_mm = 30.0, deviceBreakingKA = 36.0,
        kFactor = 143.0, clearingTime_s = 0.1, rUsed = 0.268, xUsed = 0.077,
        resultStatus = SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true,
        primaryFailureCode = null
    )

    @Test
    fun testSelectivity_highRatio_screeningWording() {
        // ratio 400/160 = 2.5 >= MAGNETIC_RATIO_MIN -> OK, but wording must NOT claim a guarantee
        val res = CoordinationEngine.checkCoordination(
            CircuitEntity(id = 320, circuitName = "DOWN", loadKVA = 80.0),
            coordSizing(160.0), "UPSTREAM-BOARD", 400.0
        )
        assertEquals("✓ OK", res.status)
        assertTrue(res.reason.contains("Likely selective"))
        assertTrue(res.reason.contains("IEC 60947-2"))
        assertFalse(res.reason.contains("guaranteed"))
    }

    @Test
    fun testSelectivity_thermalOnlyBand_nowWarning() {
        // ratio 270/160 = 1.69: passes thermal screening (1.6) but below magnetic 2.0
        // -> MAGNETIC_RATIO_MIN now drives the STATUS -> WARNING (was ✓ OK before H5 fix)
        val res = CoordinationEngine.checkCoordination(
            CircuitEntity(id = 321, circuitName = "DOWN", loadKVA = 80.0),
            coordSizing(160.0), "UPSTREAM-BOARD", 270.0
        )
        assertEquals("⚠ WARNING", res.status)
        assertTrue(res.reason.contains("magnetic (short-circuit) selectivity is unresolved"))
    }
}