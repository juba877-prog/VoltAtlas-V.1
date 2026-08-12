package com.example

import com.example.data.ConduitCatalogEntry
import com.example.engine.ConduitFillEngine
import com.example.engine.ConduitFillInput
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ConduitFillEngineTest {

    private val referenceCatalog = listOf(
        ConduitCatalogEntry("PVC_20", "Rigid PVC", "20 mm", 15.8),
        ConduitCatalogEntry("PVC_25", "Rigid PVC", "25 mm", 21.0),
        ConduitCatalogEntry("PVC_32", "Rigid PVC", "32 mm", 27.8),
        ConduitCatalogEntry("PVC_40", "Rigid PVC", "40 mm", 35.1),
        ConduitCatalogEntry("PVC_50", "Rigid PVC", "50 mm", 44.3),
        ConduitCatalogEntry("PVC_63", "Rigid PVC", "63 mm", 56.0),
        ConduitCatalogEntry("PVC_75", "Rigid PVC", "75 mm", 67.8),
        ConduitCatalogEntry("PVC_90", "Rigid PVC", "90 mm", 81.4),
        ConduitCatalogEntry("PVC_110", "Rigid PVC", "110 mm", 100.0),
        ConduitCatalogEntry("PVC_160", "Rigid PVC", "160 mm", 147.6),
        ConduitCatalogEntry("PVC_200", "Rigid PVC", "200 mm", 184.6)
    )

    // EXCEL REFERENCE CASE A
    @Test
    fun testExcelReferenceCaseA() {
        val input = ConduitFillInput(
            circuitId = 1,
            circuitName = "EXCEL_CASE_A",
            mainCableOD_mm = 20.8,
            manualCpcOD_mm = 9.2,
            cpcArrangement = "SEPARATE_CONDUIT",
            designProfile = "PROJECT_DESIGN"
        )
        val res = ConduitFillEngine.calculate(input, referenceCatalog)

        val expectedTotalOccupied = 406.27076196223203
        val expectedConduitArea = 1541.3360416858582
        val expectedFillRatio = 26.358350870577684

        assertTrue("Occupied area match", abs(res.totalCableAreaMm2 - expectedTotalOccupied) < 1.0)
        assertEquals("50 mm", res.recommendedTradeSize)
        assertTrue("Conduit area match", abs(res.conduitAreaMm2 - expectedConduitArea) < 1.0)
        assertTrue("Fill ratio match", abs(res.fillRatioPercent - expectedFillRatio) < 0.5)
        assertEquals("REVIEW REQUIRED", res.status) // Manual CPC input gives REVIEW REQUIRED
    }

    // EXCEL REFERENCE CASE B (2 cables in conduit -> 31% fill limit per NEC/IEC)
    @Test
    fun testExcelReferenceCaseB() {
        val input = ConduitFillInput(
            circuitId = 2,
            circuitName = "EXCEL_CASE_B",
            mainCableOD_mm = 45.7,
            manualCpcOD_mm = 14.8,
            cpcArrangement = "SEPARATE_CONDUIT",
            designProfile = "PROJECT_DESIGN"
        )
        val res = ConduitFillEngine.calculate(input, referenceCatalog)

        val expectedTotalOccupied = 1812.3298239845142
        val expectedConduitArea = 7853.981633974483
        val expectedFillRatio = 23.0753

        assertTrue("Occupied area match", abs(res.totalCableAreaMm2 - expectedTotalOccupied) < 1.0)
        assertEquals("110 mm", res.recommendedTradeSize)
        assertEquals(31.0, res.allowedFillPercent, 0.01)
        assertTrue("Conduit area match", abs(res.conduitAreaMm2 - expectedConduitArea) < 1.0)
        assertTrue("Fill ratio match", abs(res.fillRatioPercent - expectedFillRatio) < 0.5)
        assertEquals("REVIEW REQUIRED", res.status)
    }

    // EXCEL REFERENCE CASE C (2 cables in conduit -> 31% fill limit per NEC/IEC)
    @Test
    fun testExcelReferenceCaseC() {
        val input = ConduitFillInput(
            circuitId = 3,
            circuitName = "EXCEL_CASE_C",
            mainCableOD_mm = 56.5,
            manualCpcOD_mm = 17.8,
            cpcArrangement = "SEPARATE_CONDUIT",
            designProfile = "PROJECT_DESIGN"
        )

        val res = ConduitFillEngine.calculate(input, referenceCatalog)

        val expectedTotalOccupied = 2756.0328411963519
        val expectedConduitArea = 17110.3
        val expectedFillRatio = 16.107

        assertTrue("Occupied area match", abs(res.totalCableAreaMm2 - expectedTotalOccupied) < 1.0)
        assertEquals("160 mm", res.recommendedTradeSize)
        assertEquals(31.0, res.allowedFillPercent, 0.01)
        assertTrue("Conduit area match", abs(res.conduitAreaMm2 - expectedConduitArea) < 10.0)
        assertTrue("Fill ratio match", abs(res.fillRatioPercent - expectedFillRatio) < 0.5)
        assertEquals("REVIEW REQUIRED", res.status)
    }

    // SECTION 9 TEST A: Separate CPC with exact manual 1C input
    @Test
    fun testSeparateCPCExactManualInput() {
        val input = ConduitFillInput(1, "TEST_SEC9_A", mainCableOD_mm = 20.0, cpcSizeMm2 = 16.0, manualCpcOD_mm = 10.2, cpcArrangement = "SEPARATE_CONDUIT", designProfile = "PROJECT_DESIGN")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals(10.2, res.cpcOD_mm, 0.01)
        assertEquals("MANUAL_INPUT", res.cpcProvenance)
        assertEquals("REVIEW REQUIRED", res.status)
        assertTrue(res.totalCableAreaMm2 > res.mainCableAreaMm2)
    }

    // SECTION 9 TEST B: Separate CPC with no catalog match and no manual OD -> DATA MISSING
    @Test
    fun testSeparateCPCNoCatalogMatch() {
        val input = ConduitFillInput(1, "TEST_SEC9_B", mainCableOD_mm = 20.0, cpcSizeMm2 = 999.0, cpcArrangement = "SEPARATE_CONDUIT", designProfile = "PROJECT_DESIGN")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals("DATA MISSING", res.status)
        assertTrue(res.isProvisionalWithoutCPC)
        assertEquals("N/A", res.recommendedTradeSize)
    }

    // SECTION 9 TEST C: Manual CPC OD -> REVIEW REQUIRED
    @Test
    fun testManualCPCOD() {
        val input = ConduitFillInput(1, "TEST_SEC9_C", mainCableOD_mm = 25.0, cpcSizeMm2 = 16.0, manualCpcOD_mm = 10.5, cpcArrangement = "SEPARATE_CONDUIT")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals(10.5, res.cpcOD_mm, 0.01)
        assertEquals("MANUAL_INPUT", res.cpcProvenance)
        assertEquals("REVIEW REQUIRED", res.status)
    }

    // SECTION 9 TEST D: Multicore internal CPC -> NEC limit = 53%
    @Test
    fun testMulticoreInternalCPC_NEC_53() {
        val input = ConduitFillInput(1, "TEST_SEC9_D", mainCableOD_mm = 18.0, cpcArrangement = "INTERNAL_CORE", designProfile = "NEC_CHAPTER_9")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals(53.0, res.allowedFillPercent, 0.01)
        assertEquals(0.0, res.cpcCableAreaMm2, 0.01)
    }

    // SECTION 9 TEST E: Separate CPC -> NEC limit = 31%
    @Test
    fun testSeparateCPC_NEC_31() {
        val input = ConduitFillInput(1, "TEST_SEC9_E", mainCableOD_mm = 18.0, cpcSizeMm2 = 16.0, manualCpcOD_mm = 10.0, cpcArrangement = "SEPARATE_CONDUIT", designProfile = "NEC_CHAPTER_9")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals(31.0, res.allowedFillPercent, 0.01)
    }

    // SECTION 9 TEST F: Outside-conduit CPC -> NEC limit = 53%
    @Test
    fun testOutsideConduitCPC_NEC_53() {
        val input = ConduitFillInput(1, "TEST_SEC9_F", mainCableOD_mm = 18.0, cpcArrangement = "EXTERNAL", designProfile = "NEC_CHAPTER_9")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals(53.0, res.allowedFillPercent, 0.01)
        assertEquals(0.0, res.cpcCableAreaMm2, 0.01)
    }

    // SECTION 9 TEST G: Label OD in mm and Area in mm2
    @Test
    fun testLabelODInMmAndAreaInMm2() {
        val input = ConduitFillInput(1, "TEST_SEC9_G", mainCableOD_mm = 20.0, manualCpcOD_mm = 10.0, cpcArrangement = "SEPARATE_CONDUIT")
        val res = ConduitFillEngine.calculate(input, referenceCatalog)
        assertEquals(20.0, res.mainCableOD_mm, 0.01)
        assertEquals(Math.PI * 100.0, res.mainCableAreaMm2, 0.1)
        assertEquals(10.0, res.cpcOD_mm, 0.01)
        assertEquals(Math.PI * 25.0, res.cpcCableAreaMm2, 0.1)
        assertEquals(res.mainCableAreaMm2 + res.cpcCableAreaMm2, res.totalCableAreaMm2, 0.1)
    }

    @Test
    fun testDefaultProfileCableCountFillLimits() {
        // 1 cable (Multicore internal CPC) -> 53% limit
        val input1 = ConduitFillInput(1, "TEST_1C", mainCableOD_mm = 20.0, cpcArrangement = "INTERNAL_CORE", designProfile = "PROJECT_DESIGN")
        val res1 = ConduitFillEngine.calculate(input1, referenceCatalog)
        assertEquals(53.0, res1.allowedFillPercent, 0.01)

        // 2 cables (Separate CPC in conduit) -> 31% limit
        val input2 = ConduitFillInput(2, "TEST_2C", mainCableOD_mm = 20.0, manualCpcOD_mm = 10.0, cpcArrangement = "SEPARATE_CONDUIT", designProfile = "PROJECT_DESIGN")
        val res2 = ConduitFillEngine.calculate(input2, referenceCatalog)
        assertEquals(31.0, res2.allowedFillPercent, 0.01)

        // 3+ cables (Parallel runs ALL_IN_ONE = 3 runs) -> 40% limit
        val input3 = ConduitFillInput(3, "TEST_3C", mainCableOD_mm = 20.0, parallelRuns = 3, routingMode = "ALL_IN_ONE", cpcArrangement = "INTERNAL_CORE", designProfile = "PROJECT_DESIGN")
        val res3 = ConduitFillEngine.calculate(input3, referenceCatalog)
        assertEquals(40.0, res3.allowedFillPercent, 0.01)
    }

    @Test
    fun testEmtAndGiSteelConduitSelection() {
        val expandedCatalog = listOf(
            ConduitCatalogEntry("PVC_20", "Rigid PVC", "20 mm", 15.8, 196.1),
            ConduitCatalogEntry("EMT_20", "EMT Steel", "20 mm (3/4 in)", 20.9, 343.1),
            ConduitCatalogEntry("GI_20", "GI Steel (RMC)", "20 mm", 17.2, 232.4)
        )

        // EMT Steel selection
        val inputEmt = ConduitFillInput(1, "TEST_EMT", mainCableOD_mm = 12.0, conduitType = "EMT Steel")
        val resEmt = ConduitFillEngine.calculate(inputEmt, expandedCatalog)
        assertEquals("EMT_20", resEmt.recommendedConduitId)

        // GI Steel selection
        val inputGi = ConduitFillInput(2, "TEST_GI", mainCableOD_mm = 12.0, conduitType = "GI Steel (RMC)")
        val resGi = ConduitFillEngine.calculate(inputGi, expandedCatalog)
        assertEquals("GI_20", resGi.recommendedConduitId)
    }
}
