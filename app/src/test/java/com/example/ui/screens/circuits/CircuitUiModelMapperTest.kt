package com.example.ui.screens.circuits

import com.example.data.CircuitEntity
import com.example.engine.CableFailureCode
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircuitUiModelMapperTest {

    private fun createCircuit(
        id: Int,
        name: String = "Circuit-$id",
        loadKVA: Double = 50.0,
        fedFrom: String = "MDB-1",
        parentId: Int? = null,
        panelType: String? = null,
        cableType: String = "Cu/XLPE/PVC",
        lengthM: Double = 30.0,
        maxVDpct: Double = 0.03,
        loadType: String = "LIGHTING"
    ) = CircuitEntity(
        id = id,
        projectId = "DEFAULT_PROJECT",
        circuitName = name,
        loadKVA = loadKVA,
        fedFrom = fedFrom,
        parentId = parentId,
        panelType = panelType,
        cableType = cableType,
        length_m = lengthM,
        maxVDpct = maxVDpct,
        loadType = loadType
    )

    private fun createSizing(
        status: SizingResultStatus,
        cableSize: String = "70",
        ibA: Double = 120.0,
        deviceSizeA: Double = 160.0,
        deviceType: String = "MCCB",
        vdPct: Double = 2.45,
        utilizationPct: Double = 65.0,
        izA: Double = 185.0,
        breakingKA: Double = 36.0
    ) = SizingResult(
        Ib_A = ibA,
        deviceSize_A = deviceSizeA,
        deviceType = deviceType,
        cTotalDerating = 0.8,
        iRequired_perCable_A = 150.0,
        productCode = "PROD-1",
        cableSize_mm2 = cableSize,
        ratedI_table_A = 200.0,
        capacity_derated_Iz_A = izA,
        utilizationPct = utilizationPct,
        neutral_mm2 = "35",
        earthCPC_mm2 = "35",
        voltDrop_V = 5.0,
        vdPct = vdPct,
        cumulativeVDpct = vdPct,
        status = if (status == SizingResultStatus.PASS) "✓ PASS" else "✗ FAIL",
        deviceBreakingKA = breakingKA,
        resultStatus = status,
        isAmpacityOk = status == SizingResultStatus.PASS,
        isVoltageDropOk = status == SizingResultStatus.PASS,
        primaryFailureCode = if (status == SizingResultStatus.FAIL) CableFailureCode.AMPACITY_FAIL else null
    )

    private fun createShortCircuit(
        circuitId: Int,
        iscKA: Double
    ) = ShortCircuitResult(
        circuitId = circuitId,
        sourceName = "Source",
        cableMm2 = "70",
        iscKA = iscKA,
        iwKA = 10.0,
        deviceKA = 36.0,
        deviceCheck = "OK",
        cableCheck = "OK",
        marginPct = 20.0,
        status = "PASS",
        note = ""
    )

    // =========================================================================
    // 1. STATUS MAPPING TESTS (EXHAUSTIVE TYPED MAPPING)
    // =========================================================================

    @Test
    fun testResolveCircuitStatus_allTypedStatuses() {
        assertEquals(CircuitUiStatus.PASS, CircuitUiModelMapper.resolveCircuitStatus(createSizing(SizingResultStatus.PASS)))
        assertEquals(CircuitUiStatus.FAIL, CircuitUiModelMapper.resolveCircuitStatus(createSizing(SizingResultStatus.FAIL)))
        assertEquals(CircuitUiStatus.WARNING, CircuitUiModelMapper.resolveCircuitStatus(createSizing(SizingResultStatus.WARNING)))
        assertEquals(CircuitUiStatus.INCOMPLETE, CircuitUiModelMapper.resolveCircuitStatus(createSizing(SizingResultStatus.INCOMPLETE)))
        assertEquals(CircuitUiStatus.ERROR, CircuitUiModelMapper.resolveCircuitStatus(createSizing(SizingResultStatus.ERROR)))
        assertEquals(CircuitUiStatus.STANDBY, CircuitUiModelMapper.resolveCircuitStatus(null))
    }

    // =========================================================================
    // 2. BREADCRUMB RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testResolveBreadcrumb_parentResolved() {
        val p1 = createCircuit(id = 1, name = "MDB-01")
        val c1 = createCircuit(id = 2, name = "DB-01", parentId = 1)
        val breadcrumb = CircuitUiModelMapper.resolveBreadcrumb(c1, listOf(p1, c1))
        assertEquals("MDB-01 > DB-01", breadcrumb)
    }

    @Test
    fun testResolveBreadcrumb_fedFromFallback() {
        val c1 = createCircuit(id = 1, name = "DB-01", fedFrom = "Transformer", parentId = null)
        val breadcrumb = CircuitUiModelMapper.resolveBreadcrumb(c1, listOf(c1))
        assertEquals("Transformer > DB-01", breadcrumb)
    }

    @Test
    fun testResolveBreadcrumb_cycleProtection() {
        val c1 = createCircuit(id = 1, name = "Loop-1", parentId = 1, fedFrom = "Transformer")
        val breadcrumb = CircuitUiModelMapper.resolveBreadcrumb(c1, listOf(c1))
        assertEquals("Transformer > Loop-1", breadcrumb)
    }

    // =========================================================================
    // 3. REAL-ZERO PRESERVATION TESTS
    // =========================================================================

    @Test
    fun testMapToCardUiModel_realZeroPreserved() {
        val circ = createCircuit(id = 1, name = "C-Zero", loadKVA = 0.0, lengthM = 0.0)
        val card = CircuitUiModelMapper.mapToCardUiModel(
            circuit = circ,
            sizingResult = null,
            shortCircuitResult = null,
            allCircuits = listOf(circ),
            hasChildren = false
        )

        assertEquals("0.0 kVA", card.loadDisplay)
        assertEquals("Cu/XLPE/PVC • Length: 0.0 m", card.footerSpecDisplay)
        assertEquals(CircuitUiStatus.STANDBY, card.status)
        assertEquals("—", card.cableSizeDisplay)
        assertEquals("—", card.ibDisplay)
    }

    // =========================================================================
    // 4. MISSING AND INVALID VALUES HANDLING TESTS
    // =========================================================================

    @Test
    fun testMapToCardUiModel_missingAndInvalidData() {
        val circ = createCircuit(id = 1, name = "C-Invalid", loadKVA = Double.NaN, lengthM = -10.0, maxVDpct = Double.NaN)
        val sizing = createSizing(
            status = SizingResultStatus.ERROR,
            ibA = Double.NaN,
            deviceSizeA = 0.0,
            vdPct = Double.POSITIVE_INFINITY,
            utilizationPct = -1.0,
            izA = 0.0,
            breakingKA = 0.0
        )
        val card = CircuitUiModelMapper.mapToCardUiModel(
            circuit = circ,
            sizingResult = sizing,
            shortCircuitResult = null,
            allCircuits = listOf(circ),
            hasChildren = false
        )

        assertEquals("—", card.loadDisplay)
        assertEquals("—", card.ibDisplay)
        assertEquals("—", card.inDisplay)
        assertEquals("—", card.vdDisplay)
        assertEquals("Limit —", card.vdLimitDisplay)
        assertEquals("—", card.loadingDisplay)
        assertEquals("Iz: —", card.izDisplay)
        assertEquals("—", card.isc3phDisplay)
        assertEquals("Icu: —", card.icuDisplay)
        assertEquals("Cu/XLPE/PVC • Length: —", card.footerSpecDisplay)
    }

    // =========================================================================
    // 5. MAX VD PCT PRESENTATION (DECIMAL RATIO * 100)
    // =========================================================================

    @Test
    fun testMapToCardUiModel_maxVDpctPresentation() {
        val c1 = createCircuit(id = 1, maxVDpct = 0.03)
        val c2 = createCircuit(id = 2, maxVDpct = 0.05)
        val card1 = CircuitUiModelMapper.mapToCardUiModel(c1, null, null, listOf(c1), false)
        val card2 = CircuitUiModelMapper.mapToCardUiModel(c2, null, null, listOf(c2), false)

        assertEquals("Limit 3%", card1.vdLimitDisplay)
        assertEquals("Limit 5%", card2.vdLimitDisplay)
    }

    // =========================================================================
    // 6. SHORT CIRCUIT 3-PHASE ONLY (NO 1-PHASE)
    // =========================================================================

    @Test
    fun testMapToCardUiModel_shortCircuit3ph() {
        val circ = createCircuit(id = 1)
        val sc = createShortCircuit(circuitId = 1, iscKA = 14.85)
        val sizing = createSizing(status = SizingResultStatus.PASS, breakingKA = 36.0)
        val card = CircuitUiModelMapper.mapToCardUiModel(circ, sizing, sc, listOf(circ), false)

        assertEquals("14.85 kA", card.isc3phDisplay)
        assertEquals("Icu: 36.0 kA", card.icuDisplay)
    }

    // =========================================================================
    // 7. SUMMARY AGGREGATION TESTS (WHOLE PROJECT COLLECTION)
    // =========================================================================

    @Test
    fun testMapToSummaryUiModel_wholeProjectAggregation() {
        val c1 = createCircuit(id = 1)
        val c2 = createCircuit(id = 2)
        val c3 = createCircuit(id = 3)
        val c4 = createCircuit(id = 4)
        val c5 = createCircuit(id = 5)

        val sizingMap = mapOf(
            1 to createSizing(SizingResultStatus.PASS, vdPct = 1.8),
            2 to createSizing(SizingResultStatus.FAIL, vdPct = 4.2),
            3 to createSizing(SizingResultStatus.WARNING, vdPct = 2.9),
            4 to createSizing(SizingResultStatus.INCOMPLETE, vdPct = 0.5)
            // c5 has null sizing (standby)
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(listOf(c1, c2, c3, c4, c5), sizingMap)

        assertEquals(5, summary.totalCount)
        assertEquals(1, summary.passCount)
        assertEquals(1, summary.failCount)
        assertEquals(1, summary.warningCount)
        assertEquals(1, summary.incompleteCount)
        assertEquals(0, summary.errorCount)
        assertEquals(1, summary.standbyCount)
        assertEquals("4.20%", summary.maxVdDisplay)
        assertEquals(4.2, summary.rawMaxVdPct!!, 0.001)
    }

    @Test
    fun testMapToSummaryUiModel_emptyOrInvalidVd() {
        val c1 = createCircuit(id = 1)
        val sizingMap = mapOf(1 to createSizing(SizingResultStatus.ERROR, vdPct = Double.NaN))
        val summary = CircuitUiModelMapper.mapToSummaryUiModel(listOf(c1), sizingMap)

        assertEquals(1, summary.totalCount)
        assertEquals("—", summary.maxVdDisplay)
        assertNull(summary.rawMaxVdPct)
    }

    // =========================================================================
    // 8. FILTERING TESTS
    // =========================================================================

    @Test
    fun testFilterCircuits_searchQueryAndLevelAndStatus() {
        val c1 = createCircuit(id = 1, name = "MDB-Main", fedFrom = "Transformer", panelType = "MDB", loadType = "Normal")
        val c2 = createCircuit(id = 2, name = "DB-Floor1", fedFrom = "MDB-Main", parentId = 1, panelType = "DB", loadType = "HVAC")
        val c3 = createCircuit(id = 3, name = "Lighting-1", fedFrom = "DB-Floor1", parentId = 2, panelType = null, loadType = "Emergency")

        val card1 = CircuitUiModelMapper.mapToCardUiModel(c1, createSizing(SizingResultStatus.PASS), null, listOf(c1, c2, c3), true)
        val card2 = CircuitUiModelMapper.mapToCardUiModel(c2, createSizing(SizingResultStatus.FAIL), null, listOf(c1, c2, c3), false)
        val card3 = CircuitUiModelMapper.mapToCardUiModel(c3, null, null, listOf(c1, c2, c3), false)

        val cards = listOf(card1, card2, card3)

        // Query filter by name: "Lighting" matches only card3
        val searchLighting = CircuitUiModelMapper.filterCircuits(cards, "Lighting", CircuitLevelFilter.ALL, CircuitStatusFilter.ALL) { false }
        assertEquals(1, searchLighting.size)
        assertEquals(3, searchLighting[0].id)

        // Query filter by fedFrom / name: "Floor1" matches card2 (by name) and card3 (by fedFrom)
        val searchFloor = CircuitUiModelMapper.filterCircuits(cards, "Floor1", CircuitLevelFilter.ALL, CircuitStatusFilter.ALL) { false }
        assertEquals(2, searchFloor.size)
        assertEquals(listOf(2, 3), searchFloor.map { it.id })

        // Query filter by loadType: "HVAC" matches only card2
        val searchHvac = CircuitUiModelMapper.filterCircuits(cards, "HVAC", CircuitLevelFilter.ALL, CircuitStatusFilter.ALL) { false }
        assertEquals(1, searchHvac.size)
        assertEquals(2, searchHvac[0].id)

        // Level: Main Feeders
        val feeders = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.MAIN_FEEDERS, CircuitStatusFilter.ALL) { false }
        assertEquals(1, feeders.size)
        assertEquals(1, feeders[0].id)

        // Level: Downstream
        val downstream = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.DOWNSTREAM, CircuitStatusFilter.ALL) { false }
        assertEquals(2, downstream.size)

        // Level: Panels
        val panels = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.PANELS, CircuitStatusFilter.ALL) { false }
        assertEquals(2, panels.size) // MDB and DB

        // Status: Pass
        val passOnly = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.PASS) { false }
        assertEquals(1, passOnly.size)
        assertEquals(1, passOnly[0].id)

        // Status: Fail
        val failOnly = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.FAIL) { false }
        assertEquals(1, failOnly.size)
        assertEquals(2, failOnly[0].id)

        // Status: Standby
        val standbyOnly = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.STANDBY) { false }
        assertEquals(1, standbyOnly.size)
        assertEquals(3, standbyOnly[0].id)
    }

    // =========================================================================
    // 9. DETERMINISTIC SORTING TESTS WITH STABLE TIE-BREAKERS
    // =========================================================================

    @Test
    fun testSortCircuits_nameAscWithBlanksLastAndIdTieBreaker() {
        val c1 = createCircuit(id = 1, name = "Beta")
        val c2 = createCircuit(id = 2, name = "Alpha")
        val c3 = createCircuit(id = 3, name = "")
        val c4 = createCircuit(id = 4, name = "Alpha")

        val cards = listOf(
            CircuitUiModelMapper.mapToCardUiModel(c1, null, null, listOf(c1), false),
            CircuitUiModelMapper.mapToCardUiModel(c2, null, null, listOf(c2), false),
            CircuitUiModelMapper.mapToCardUiModel(c3, null, null, listOf(c3), false),
            CircuitUiModelMapper.mapToCardUiModel(c4, null, null, listOf(c4), false)
        )

        val sorted = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.NAME_ASC)
        assertEquals(listOf(2, 4, 1, 3), sorted.map { it.id })
    }

    @Test
    fun testSortCircuits_loadDesc() {
        val c1 = createCircuit(id = 1, loadKVA = 20.0)
        val c2 = createCircuit(id = 2, loadKVA = 100.0)
        val c3 = createCircuit(id = 3, loadKVA = 50.0)
        val c4 = createCircuit(id = 4, loadKVA = Double.NaN)

        val cards = listOf(
            CircuitUiModelMapper.mapToCardUiModel(c1, null, null, listOf(c1), false),
            CircuitUiModelMapper.mapToCardUiModel(c2, null, null, listOf(c2), false),
            CircuitUiModelMapper.mapToCardUiModel(c3, null, null, listOf(c3), false),
            CircuitUiModelMapper.mapToCardUiModel(c4, null, null, listOf(c4), false)
        )

        val sorted = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.LOAD_DESC)
        assertEquals(listOf(2, 3, 1, 4), sorted.map { it.id })
    }

    @Test
    fun testSortCircuits_vdDesc() {
        val c1 = createCircuit(id = 1)
        val c2 = createCircuit(id = 2)
        val c3 = createCircuit(id = 3)

        val s1 = createSizing(SizingResultStatus.PASS, vdPct = 1.5)
        val s2 = createSizing(SizingResultStatus.FAIL, vdPct = 4.8)

        val cards = listOf(
            CircuitUiModelMapper.mapToCardUiModel(c1, s1, null, listOf(c1), false),
            CircuitUiModelMapper.mapToCardUiModel(c2, s2, null, listOf(c2), false),
            CircuitUiModelMapper.mapToCardUiModel(c3, null, null, listOf(c3), false) // missing VD placed last
        )

        val sorted = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.VD_DESC)
        assertEquals(listOf(2, 1, 3), sorted.map { it.id })
    }

    @Test
    fun testSortCircuits_statusOrder() {
        val c1 = createCircuit(id = 1, loadKVA = 10.0) // Pass
        val c2 = createCircuit(id = 2, loadKVA = 50.0) // Fail
        val c3 = createCircuit(id = 3, loadKVA = 30.0) // Standby
        val c4 = createCircuit(id = 4, loadKVA = 80.0) // Fail

        val cards = listOf(
            CircuitUiModelMapper.mapToCardUiModel(c1, createSizing(SizingResultStatus.PASS), null, listOf(c1), false),
            CircuitUiModelMapper.mapToCardUiModel(c2, createSizing(SizingResultStatus.FAIL), null, listOf(c2), false),
            CircuitUiModelMapper.mapToCardUiModel(c3, null, null, listOf(c3), false),
            CircuitUiModelMapper.mapToCardUiModel(c4, createSizing(SizingResultStatus.FAIL), null, listOf(c4), false)
        )

        val sorted = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.STATUS)
        // Priority: FAIL (c4 load 80, c2 load 50) -> PASS (c1) -> STANDBY (c3)
        assertEquals(listOf(4, 2, 1, 3), sorted.map { it.id })
    }
}
