package com.example

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.engine.ProtectionEngine
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ENG-30 (§12.2) — ADS loop remediation regression tests.
 *
 * (a) A missing data_sheet row is DATA MISSING with r1/r2 = null and NO loop impedance —
 *     never a 0.0 Ω loop that reads as a PASS (the former defect).
 * (b) The temperature factor is material- AND insulation-specific: 1 + α₂₀·(θ − 20),
 *     α₂₀ (IEC 60228) Cu 0.00393 /K, Al 0.00403 /K, θ = 70 °C (PVC) / 90 °C (XLPE).
 *
 * Every Zs figure asserted below is hand-calculated in the adjacent comment, not copied
 * from engine output.
 */
class ProtectionAdsCatalogMissingAndTempFactorTest {

    private var originalDataSheet: List<DataSheetEntry> = emptyList()

    /** Bundled IEC 60228 Table 2 rows for 70 mm² (phase) and 35 mm² (CPC). */
    private fun fullRows() = listOf(
        DataSheetEntry(size = 70.0, R_Cu20 = 0.268, X = 0.08, R_Al20 = 0.443, R_Cu_70_est = 0.322, R_Al_70_est = 0.532),
        DataSheetEntry(size = 35.0, R_Cu20 = 0.524, X = 0.08, R_Al20 = 0.868, R_Cu_70_est = 0.629, R_Al_70_est = 1.042)
    )

    @Before
    fun saveCatalog() {
        originalDataSheet = Catalog.dataSheet
    }

    @After
    fun restoreCatalog() {
        Catalog.dataSheet = originalDataSheet
    }

    private fun sizing(
        deviceSize: Double = 32.0,
        cableSize: String = "70 mm²",
        cpc: String = "35 mm²",
        material: String = "Cu",
        insulation: String = "XLPE"
    ) = SizingResult(
        Ib_A = 25.0,
        deviceSize_A = deviceSize,
        deviceType = "MCB",
        cTotalDerating = 1.0,
        iRequired_perCable_A = 25.0,
        productCode = "T",
        cableSize_mm2 = cableSize,
        ratedI_table_A = 80.0,
        capacity_derated_Iz_A = 80.0,
        utilizationPct = 31.0,
        neutral_mm2 = cableSize,
        earthCPC_mm2 = cpc,
        voltDrop_V = 1.0,
        vdPct = 0.2,
        cumulativeVDpct = 0.2,
        status = "✓ PASS",
        cableMaterial = material,
        insulation = insulation,
        resultStatus = SizingResultStatus.PASS,
        isAmpacityOk = true,
        isVoltageDropOk = true,
        primaryFailureCode = null
    )

    // =========================================================================
    // §12.2-a test 1 — resolved size with no catalog row -> DATA MISSING, no Zs
    // =========================================================================

    @Test
    fun entryMiss_returnsDataMissing_bothResistancesNull_andNamesTheSize() {
        // Catalog deliberately holds only 16 / 185 mm²: the resolved 70 mm² phase and
        // 35 mm² CPC rows are absent (this is the "rogue size" path, made reachable here).
        Catalog.dataSheet = listOf(
            DataSheetEntry(size = 16.0, R_Cu20 = 1.15, X = 0.082, R_Al20 = 1.91, R_Cu_70_est = 1.38, R_Al_70_est = 2.29),
            DataSheetEntry(size = 185.0, R_Cu20 = 0.0991, X = 0.074, R_Al20 = 0.164, R_Cu_70_est = 0.119, R_Al_70_est = 0.197)
        )
        val circuit = CircuitEntity(
            id = 1, circuitName = "MISSING-ROWS", loadKVA = 50.0,
            length_m = 370.0, parallelRuns = 4, overrideDeviceType = "MCB Curve C"
        )

        val ads = ProtectionEngine.calculateAds(circuit, sizing(deviceSize = 63.0), ze_ohm = 0.01, isZeResolved = true)

        assertEquals("Catalog miss must be DATA MISSING, never PASS/UNVERIFIED", "DATA MISSING", ads.status)
        assertEquals("No disconnection time may be claimed", "DATA MISSING", ads.disconnectionTime)
        assertNull("r1 must be null, not 0.0", ads.r1_ohm_km)
        assertNull("r2 must be null, not 0.0", ads.r2_ohm_km)
        assertEquals("No loop impedance is fabricated for DATA MISSING", 0.0, ads.calculatedZs, 0.0)
        // Zs_max = (0.95 · 230)/103 A = 0.35 Ω for a 63 A C-curve MCB — device data, still reported.
        assertEquals("Device limit is catalog-independent", 0.35, ads.maxZsAllowed, 0.0005)
        // The warning names the missing size explicitly, per the mandated wording.
        assertTrue(
            "Warning must name the phase size. Actual warnings: ${ads.warnings}",
            ads.warnings.any { it.contains("Conductor resistance data missing for Cu 70 mm² in data_sheet.json") }
        )
        assertTrue(
            "Warning must name the CPC size. Actual warnings: ${ads.warnings}",
            ads.warnings.any { it.contains("Conductor resistance data missing for Cu 35 mm² in data_sheet.json") }
        )
    }

    // =========================================================================
    // §12.2-a test 2 (Option C) — empty catalog refused at the door
    // =========================================================================

    @Test
    fun emptyCatalog_returnsDataMissingAtTheDoor_beforeAnySizeLookup() {
        Catalog.dataSheet = emptyList()
        val circuit = CircuitEntity(
            id = 2, circuitName = "NO-CATALOG", loadKVA = 50.0,
            length_m = 100.0, overrideDeviceType = "MCB Curve B"
        )

        val ads = ProtectionEngine.calculateAds(circuit, sizing(deviceSize = 32.0), ze_ohm = 0.01, isZeResolved = true)

        assertEquals("An unloaded catalog can never yield a numeric Zs", "DATA MISSING", ads.status)
        assertTrue(
            "Door message must say the catalog is not loaded",
            ads.warnings.any { it.contains("Cable catalog (data_sheet) not loaded") }
        )
        assertNull(ads.r1_ohm_km)
        assertNull(ads.r2_ohm_km)
        assertEquals(0.0, ads.calculatedZs, 0.0)
        assertEquals("DATA MISSING", ads.disconnectionTime)
    }

    // =========================================================================
    // §12.2-b test 3 — factor selection for all four material × insulation combos
    // =========================================================================

    @Test
    fun tempFactor_allFourMaterialInsulationCombinations_withHandCalcs() {
        Catalog.dataSheet = fullRows()

        // Zs = Ze + (r1 + r2) · L · factor / 1000, with Ze = 0.01 Ω, L = 100 m, 1 run:
        //   Cu: (0.268 + 0.524) = 0.792 Ω/km  -> 0.792 · 100 =  79.2
        //   Al: (0.443 + 0.868) = 1.311 Ω/km  -> 1.311 · 100 = 131.1
        data class Case(
            val material: String, val insulation: String,
            val factor: Double, val scaledLoop: Double, val expectedZs: Double
        )
        val cases = listOf(
            // 79.2 · 1.1965 =  94.7628 -> 0.0947628 + 0.01 = 0.1047628 -> rounds to 0.105
            Case("Cu", "PVC", 1.1965, 79.2, 0.105),
            // 79.2 · 1.2751 = 100.98792 -> 0.10098792 + 0.01 = 0.11098792 -> rounds to 0.111
            Case("Cu", "XLPE", 1.2751, 79.2, 0.111),
            // 131.1 · 1.2015 = 157.51665 -> 0.15751665 + 0.01 = 0.16751665 -> rounds to 0.168
            Case("Al", "PVC", 1.2015, 131.1, 0.168),
            // 131.1 · 1.2827 = 168.16197 -> 0.16816197 + 0.01 = 0.17816197 -> rounds to 0.178
            Case("Al", "XLPE", 1.2827, 131.1, 0.178)
        )

        cases.forEach { c ->
            val circuit = CircuitEntity(
                id = 3, circuitName = "FACTOR-${c.material}-${c.insulation}", loadKVA = 50.0,
                length_m = 100.0, overrideDeviceType = "MCB Curve B"
            )
            val ads = ProtectionEngine.calculateAds(
                circuit, sizing(deviceSize = 32.0, material = c.material, insulation = c.insulation),
                ze_ohm = 0.01, isZeResolved = true
            )

            assertEquals(
                "${c.material}/${c.insulation} temperature factor (1 + α₂₀(θ−20))",
                c.factor, ads.tempFactor, 1e-9
            )
            assertEquals(
                "${c.material}/${c.insulation} Zs hand calc: (r1+r2)·100·${c.factor}/1000 + 0.01 " +
                    "(${c.scaledLoop} · ${c.factor}) -> ${c.expectedZs}",
                c.expectedZs, ads.calculatedZs, 0.0005
            )
        }
    }

    // =========================================================================
    // §12.2-b test 4 — flip window: the factor changes a VERDICT, not just a number
    // 4 × 70 mm² Cu phase (r1/4 = 0.067), 35 mm² Cu CPC (r2 = 0.524), Ze = 0.01,
    // maxZs = 0.35 Ω (63 A MCB Curve C). Loop term = 0.591 · L · factor / 1000.
    // =========================================================================

    @Test
    fun flipWindow_465m_pvcPassesWhileXlpeFails() {
        Catalog.dataSheet = fullRows()

        fun feeder(id: Int, length: Double) = CircuitEntity(
            id = id, circuitName = "FLIP-$id", loadKVA = 50.0,
            length_m = length, parallelRuns = 4, overrideDeviceType = "MCB Curve C"
        )

        // --- L = 370 m, Cu/XLPE (factor 1.2751) --------------------------------
        // 0.591 · 370 = 218.67; 218.67 · 1.2751 = 278.826117
        // 278.826117 / 1000 = 0.278826; + 0.01 = 0.288826 -> rounds to 0.289 <= 0.35 PASS
        val at370 = ProtectionEngine.calculateAds(
            feeder(10, 370.0), sizing(deviceSize = 63.0), ze_ohm = 0.01, isZeResolved = true
        )
        assertEquals("370 m Cu/XLPE factor", 1.2751, at370.tempFactor, 1e-9)
        assertEquals(
            "370 m Cu/XLPE Zs: 0.591·370·1.2751/1000 + 0.01 = 0.288826 -> 0.289",
            0.289, at370.calculatedZs, 0.0005
        )
        assertEquals("370 m Cu/XLPE must still PASS at maxZs 0.35", "✓ PASS", at370.status)

        // --- L = 465 m (inside the reported flip window 449.5–479.4 m), Cu/XLPE ----
        // 0.591 · 465 = 274.815; 274.815 · 1.2751 = 350.416607
        // 350.416607 / 1000 = 0.350417; + 0.01 = 0.360417 -> rounds to 0.360 > 0.35 FAIL
        val at465xlpe = ProtectionEngine.calculateAds(
            feeder(11, 465.0), sizing(deviceSize = 63.0), ze_ohm = 0.01, isZeResolved = true
        )
        assertEquals("465 m Cu/XLPE Zs -> 0.360", 0.360, at465xlpe.calculatedZs, 0.0005)
        assertEquals("465 m Cu/XLPE must FAIL at maxZs 0.35", "✗ FAIL", at465xlpe.status)
        assertEquals("Failing loop must report > 0.4s", "> 0.4s", at465xlpe.disconnectionTime)

        // --- L = 465 m, Cu/PVC (factor 1.1965) — SAME circuit, opposite verdict -----
        // 0.591 · 465 = 274.815; 274.815 · 1.1965 = 328.816148
        // 328.816148 / 1000 = 0.328816; + 0.01 = 0.338816 -> rounds to 0.339 <= 0.35 PASS
        val at465pvc = ProtectionEngine.calculateAds(
            feeder(12, 465.0), sizing(deviceSize = 63.0, insulation = "PVC"),
            ze_ohm = 0.01, isZeResolved = true
        )
        assertEquals("465 m Cu/PVC factor", 1.1965, at465pvc.tempFactor, 1e-9)
        assertEquals(
            "465 m Cu/PVC Zs: 0.591·465·1.1965/1000 + 0.01 = 0.338816 -> 0.339",
            0.339, at465pvc.calculatedZs, 0.0005
        )
        assertEquals("465 m Cu/PVC must PASS at maxZs 0.35", "✓ PASS", at465pvc.status)
    }
}
