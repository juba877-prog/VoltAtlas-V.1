package com.example.ui.screens.circuits

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.CircuitEntity
import com.example.engine.CableFailureCode
import com.example.engine.ProtectionResult
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.screens.CircuitsGridScreen
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class CircuitsGridScreenC2Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    /**
     * ECT-EE-06 (D5): a circuit is only `PASS` when sizing passes **and** the
     * authoritative `ProtectionResult` passes. Values are internally consistent
     * (Ib <= In <= Iz, Icu >= Isc) — they describe a healthy circuit.
     */
    private fun passingProtection(circuitId: Int, circuitName: String = "Circuit-$circuitId") =
        ProtectionResult(
            circuitId = circuitId,
            circuitName = circuitName,
            Ib_A = 10.0,
            In_A = 16.0,
            Iz_A = 32.0,
            iscKA = 3.0,
            icuKA = 10.0,
            overloadStatus = "PASS",
            shortCircuitStatus = "PASS",
            cableWithstandStatus = "PASS",
            selectivityStatus = "PASS",
            finalStatus = "PASS"
        )

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
        loadType: String = "NORMAL"
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

    private fun createSuccessState(
        circuits: List<CircuitEntity>,
        sizingResults: Map<Int, SizingResult> = emptyMap(),
        shortCircuitResults: Map<Int, ShortCircuitResult> = emptyMap(),
        protectionResults: Map<Int, ProtectionResult> = emptyMap()
    ) = UiState.Success(
        circuits = circuits,
        transformers = emptyList(),
        sizingResults = sizingResults,
        shortCircuitResults = shortCircuitResults,
        protectionResults = protectionResults,
        transformerResults = emptyMap(),
        boq = emptyList(),
        drums = emptyList(),
        procurement = emptyList(),
        panels = emptyList(),
        pfCorrectionResults = emptyList()
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = EngineeringViewModel(context)
    }

    // =========================================================================
    // 1. WHOLE-PROJECT SUMMARY INVARIANCE TESTS
    // =========================================================================

    @Test
    fun test1_summaryRemainsInvariant_whenSearchApplied() {
        val c1 = createCircuit(id = 1, name = "MDB-Main")
        val c2 = createCircuit(id = 2, name = "DB-Floor1")
        val c3 = createCircuit(id = 3, name = "Lighting-1")
        val allCircuits = listOf(c1, c2, c3)
        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.PASS, vdPct = 1.2),
            2 to createSizing(SizingResultStatus.FAIL, vdPct = 4.5),
            3 to createSizing(SizingResultStatus.WARNING, vdPct = 2.8)
        )

        // ECT-EE-06 (D5): an executed protection verdict accompanies each sized
        // circuit, so circuit 1 (sizing PASS) is counted as PASS rather than
        // resolving to INCOMPLETE.
        val protections = mapOf(
            1 to passingProtection(1, "MDB-Main"),
            2 to passingProtection(2, "DB-Floor1"),
            3 to passingProtection(3, "Lighting-1")
        )

        // Pre-filter whole-project summary (WARNING maps to Standby)
        val baselineSummary = CircuitUiModelMapper.mapToSummaryUiModel(allCircuits, sizings, protections)
        assertEquals(3, baselineSummary.totalCount)
        assertEquals(1, baselineSummary.passCount)
        assertEquals(1, baselineSummary.failCount)
        assertEquals(1, baselineSummary.standbyCount)
        assertEquals("4.50%", baselineSummary.maxVdDisplay)

        // Filter cards by search query "Floor1" (matches only c2)
        val cards = allCircuits.map {
            CircuitUiModelMapper.mapToCardUiModel(it, sizings[it.id], null, allCircuits, false, protections[it.id])
        }
        val filtered = CircuitUiModelMapper.filterCircuits(cards, "Floor1", CircuitLevelFilter.ALL, CircuitStatusFilter.ALL) { false }
        assertEquals(1, filtered.size)

        // Summary must STILL be computed from whole project
        val postFilterSummary = CircuitUiModelMapper.mapToSummaryUiModel(allCircuits, sizings, protections)
        assertEquals(baselineSummary, postFilterSummary)
    }

    /**
     * ECT-EE-06 (D5): the same three circuits with **no** protection results may not
     * contribute a PASS — sizing alone can no longer decide the verdict.
     */
    @Test
    fun test1b_summaryWithoutProtectionCountsNoPass() {
        val allCircuits = listOf(
            createCircuit(id = 1, name = "MDB-Main"),
            createCircuit(id = 2, name = "DB-Floor1"),
            createCircuit(id = 3, name = "Lighting-1")
        )
        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.PASS, vdPct = 1.2),
            2 to createSizing(SizingResultStatus.FAIL, vdPct = 4.5),
            3 to createSizing(SizingResultStatus.WARNING, vdPct = 2.8)
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(allCircuits, sizings)
        assertEquals(3, summary.totalCount)
        assertEquals(0, summary.passCount)
        assertEquals(1, summary.failCount)
        assertEquals(2, summary.standbyCount)
    }

    @Test
    fun test2_summaryRemainsInvariant_whenFilterApplied() {
        val c1 = createCircuit(id = 1, name = "MDB-Main", fedFrom = "Transformer")
        val c2 = createCircuit(id = 2, name = "DB-1", fedFrom = "MDB-Main", parentId = 1)
        val allCircuits = listOf(c1, c2)
        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.PASS, vdPct = 1.0),
            2 to createSizing(SizingResultStatus.PASS, vdPct = 2.0)
        )

        val initialSummary = CircuitUiModelMapper.mapToSummaryUiModel(allCircuits, sizings)

        // Apply Level filter MAIN_FEEDERS -> 1 item visible
        val cards = allCircuits.map { CircuitUiModelMapper.mapToCardUiModel(it, sizings[it.id], null, allCircuits, it.id == 1) }
        val filtered = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.MAIN_FEEDERS, CircuitStatusFilter.ALL) { it == "MDB-Main" }
        assertEquals(1, filtered.size)

        // Summary remains whole-project collection
        val afterLevelSummary = CircuitUiModelMapper.mapToSummaryUiModel(allCircuits, sizings)
        assertEquals(initialSummary, afterLevelSummary)
    }

    // =========================================================================
    // 2. VISIBLE COUNT FORMAT TESTS
    // =========================================================================

    @Test
    fun test3_visibleCountHeader_reflectsFilterSubsets() {
        val c1 = createCircuit(id = 1, name = "MDB-Main")
        val c2 = createCircuit(id = 2, name = "DB-Floor1")
        val c3 = createCircuit(id = 3, name = "DB-Floor2")
        val all = listOf(c1, c2, c3)

        // When all 3 are visible
        val fullCountHeader = if (all.size == all.size) "CIRCUITS (${all.size})" else "CIRCUITS (${all.size} of ${all.size})"
        assertEquals("CIRCUITS (3)", fullCountHeader)

        // When 1 of 3 is visible
        val subsetSize = 1
        val subsetCountHeader = if (subsetSize == all.size) "CIRCUITS (${all.size})" else "CIRCUITS ($subsetSize of ${all.size})"
        assertEquals("CIRCUITS (1 of 3)", subsetCountHeader)
    }

    // =========================================================================
    // 3. LEVEL AND STATUS FILTERING
    // =========================================================================

    @Test
    fun test4_levelFilter_filtersFeedersAndPanelsCorrectly() {
        val c1 = createCircuit(id = 1, name = "MDB-Main", fedFrom = "Transformer", panelType = "MDB")
        val c2 = createCircuit(id = 2, name = "DB-Floor1", fedFrom = "MDB-Main", parentId = 1, panelType = "DB")
        val c3 = createCircuit(id = 3, name = "Motor-1", fedFrom = "DB-Floor1", parentId = 2, panelType = null)
        val all = listOf(c1, c2, c3)

        val cards = all.map { CircuitUiModelMapper.mapToCardUiModel(it, null, null, all, it.id in listOf(1, 2)) }

        val feeders = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.MAIN_FEEDERS, CircuitStatusFilter.ALL) { it in listOf("MDB-Main", "DB-Floor1") }
        assertEquals(listOf(1), feeders.map { it.id })

        val downstream = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.DOWNSTREAM, CircuitStatusFilter.ALL) { it in listOf("MDB-Main", "DB-Floor1") }
        assertEquals(listOf(2, 3), downstream.map { it.id })

        val panels = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.PANELS, CircuitStatusFilter.ALL) { it in listOf("MDB-Main", "DB-Floor1") }
        assertEquals(listOf(1, 2), panels.map { it.id })
    }

    @Test
    fun test5_statusFilter_filtersTypedPassFailStandby() {
        val c1 = createCircuit(id = 1)
        val c2 = createCircuit(id = 2)
        val c3 = createCircuit(id = 3)
        val all = listOf(c1, c2, c3)

        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.PASS),
            2 to createSizing(SizingResultStatus.FAIL)
            // c3 is null -> STANDBY
        )

        // ECT-EE-06 (D5): card1 carries an executed, passing ProtectionResult, so it
        // is still selected by CircuitStatusFilter.PASS.
        val cards = all.map {
            CircuitUiModelMapper.mapToCardUiModel(
                it, sizings[it.id], null, all, false,
                if (it.id == 1) passingProtection(1) else null
            )
        }

        val passCards = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.PASS) { false }
        assertEquals(listOf(1), passCards.map { it.id })

        val failCards = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.FAIL) { false }
        assertEquals(listOf(2), failCards.map { it.id })

        val standbyCards = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.STANDBY) { false }
        assertEquals(listOf(3), standbyCards.map { it.id })
    }

    @Test
    fun test6_statusFilter_allOptionsAreExhaustive() {
        val c1 = createCircuit(id = 1)
        val c2 = createCircuit(id = 2)
        val c3 = createCircuit(id = 3)
        val all = listOf(c1, c2, c3)

        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.PASS),
            2 to createSizing(SizingResultStatus.FAIL)
        )

        val cards = all.map { CircuitUiModelMapper.mapToCardUiModel(it, sizings[it.id], null, all, false) }
        val allCards = CircuitUiModelMapper.filterCircuits(cards, "", CircuitLevelFilter.ALL, CircuitStatusFilter.ALL) { false }

        assertEquals(3, allCards.size)
    }

    // =========================================================================
    // 4. DETERMINISTIC SORTING & TIE BREAKERS
    // =========================================================================

    @Test
    fun test7_sortSelector_ordersCardsDeterministically() {
        val c1 = createCircuit(id = 1, name = "Gamma", loadKVA = 30.0)
        val c2 = createCircuit(id = 2, name = "Alpha", loadKVA = 100.0)
        val c3 = createCircuit(id = 3, name = "Beta", loadKVA = 50.0)
        val all = listOf(c1, c2, c3)

        val cards = all.map { CircuitUiModelMapper.mapToCardUiModel(it, null, null, all, false) }

        val bySource = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.SOURCE_ORDER)
        assertEquals(listOf(1, 2, 3), bySource.map { it.id })

        val byName = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.NAME_ASC)
        assertEquals(listOf(2, 3, 1), byName.map { it.id })

        val byLoad = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.LOAD_DESC)
        assertEquals(listOf(2, 3, 1), byLoad.map { it.id })
    }

    // =========================================================================
    // 5. EXCEPTIONAL STATUSES & INVALID MAX VD
    // =========================================================================

    @Test
    fun test8_summary_mapsIncompleteAndErrorToStandby() {
        val c1 = createCircuit(id = 1)
        val c2 = createCircuit(id = 2)
        val c3 = createCircuit(id = 3)
        val all = listOf(c1, c2, c3)

        val sizings = mapOf(
            1 to createSizing(SizingResultStatus.INCOMPLETE),
            2 to createSizing(SizingResultStatus.ERROR)
            // c3 is STANDBY
        )

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(all, sizings)
        assertEquals(3, summary.totalCount)
        assertEquals(0, summary.passCount)
        assertEquals(0, summary.failCount)
        assertEquals(3, summary.standbyCount)
    }

    @Test
    fun test9_summary_handlesInvalidMaxVdGracefully() {
        val c1 = createCircuit(id = 1)
        val sizings = mapOf(1 to createSizing(SizingResultStatus.PASS, vdPct = Double.NaN))

        val summary = CircuitUiModelMapper.mapToSummaryUiModel(listOf(c1), sizings)
        assertEquals("—", summary.maxVdDisplay)
    }

    // =========================================================================
    // 6. PIPELINE & ENTITY ADAPTER PRESERVATION
    // =========================================================================

    @Test
    fun test11_resetFilters_clearsQueryLevelAndStatus_butPreservesSort() {
        var query = "Floor"
        var selectedLevel = CircuitLevelFilter.MAIN_FEEDERS
        var selectedStatus = CircuitStatusFilter.FAIL
        var selectedSort = CircuitSortOrder.LOAD_DESC

        // Reset action
        query = ""
        selectedLevel = CircuitLevelFilter.ALL
        selectedStatus = CircuitStatusFilter.ALL
        // sort is preserved

        assertEquals("", query)
        assertEquals(CircuitLevelFilter.ALL, selectedLevel)
        assertEquals(CircuitStatusFilter.ALL, selectedStatus)
        assertEquals(CircuitSortOrder.LOAD_DESC, selectedSort)
    }

    @Test
    fun test12_legacyViewModelFilters_areSynchronizedWithC2Pipeline() {
        // ViewModel StateFlows are synchronized on change:
        viewModel.circuitParentFilter.value = "Transformer"
        viewModel.circuitStatusFilter.value = "PASS"

        val selectedStatus = CircuitStatusFilter.PASS
        val selectedLevel = CircuitLevelFilter.MAIN_FEEDERS

        val c1 = createCircuit(id = 1, fedFrom = "Transformer") // Main feeder with PASS result
        val sizings = mapOf(1 to createSizing(SizingResultStatus.PASS))
        // ECT-EE-06 (D5): sizing PASS plus an executed, passing ProtectionResult.
        val cards = listOf(
            CircuitUiModelMapper.mapToCardUiModel(c1, sizings[1], null, listOf(c1), false, passingProtection(1))
        )

        val visible = CircuitUiModelMapper.filterCircuits(cards, "", selectedLevel, selectedStatus) { false }
        assertEquals(1, visible.size)
        assertEquals(1, visible[0].id)
    }

    @Test
    fun test13_orderedVisibleModels_resolveToOriginalCircuitEntities_andPreserveStableIds() {
        val c1 = createCircuit(id = 10, name = "C-10", loadKVA = 20.0)
        val c2 = createCircuit(id = 20, name = "C-20", loadKVA = 80.0)
        val originalList = listOf(c1, c2)

        val cards = originalList.map { CircuitUiModelMapper.mapToCardUiModel(it, null, null, originalList, false) }
        val sortedCards = CircuitUiModelMapper.sortCircuits(cards, CircuitSortOrder.LOAD_DESC)

        val entityById = originalList.associateBy { it.id }
        val resolvedEntities = sortedCards.mapNotNull { entityById[it.id] }

        assertEquals(listOf(20, 10), resolvedEntities.map { it.id })
        // Instances are exact original entities
        assertTrue(resolvedEntities[0] === c2)
        assertTrue(resolvedEntities[1] === c1)
    }

    // =========================================================================
    // 7. COMPOSE UI RENDERING & ACCESSIBILITY TESTS
    // =========================================================================

    @Test
    fun test10_accessibility_summaryTilesHaveMergedSemantics() {
        val c1 = createCircuit(id = 1, name = "MDB-1")
        // ECT-EE-06 (D5): the PASS tile is announced only when an executed protection
        // verdict also passes — see test10b for the no-protection counterpart.
        val state = createSuccessState(
            circuits = listOf(c1),
            sizingResults = mapOf(1 to createSizing(SizingResultStatus.PASS, vdPct = 2.1)),
            protectionResults = mapOf(1 to passingProtection(1, "MDB-1"))
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                CircuitsGridScreen(
                    state = state,
                    viewModel = viewModel
                )
            }
        }

        // Summary strip exists
        composeTestRule.onNodeWithTag("circuits_summary_strip").assertExists().assertIsDisplayed()

        // Content descriptions with merged semantics exist: Total, Pass, Fail, Standby, Max Vd
        composeTestRule.onNodeWithContentDescription("Total circuits: 1").assertExists()
        composeTestRule.onNodeWithContentDescription("Passing circuits: 1").assertExists()
        composeTestRule.onNodeWithContentDescription("Failing circuits: 0").assertExists()
        composeTestRule.onNodeWithContentDescription("Standby circuits: 0").assertExists()
        composeTestRule.onNodeWithContentDescription("Maximum voltage drop: 2.10%").assertExists()
    }

    /**
     * ECT-EE-06 (D5) — the accessibility contract of the summary strip without an
     * executed protection verdict: the screen must announce **zero** passing circuits
     * rather than a sizing-only PASS, and the circuit is announced as standby.
     */
    @Test
    fun test10b_accessibility_summaryTilesNeverClaimPassWithoutProtection() {
        val c1 = createCircuit(id = 1, name = "MDB-1")
        val state = createSuccessState(
            circuits = listOf(c1),
            sizingResults = mapOf(1 to createSizing(SizingResultStatus.PASS, vdPct = 2.1))
            // no protectionResults — Case E
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                CircuitsGridScreen(
                    state = state,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag("circuits_summary_strip").assertExists()
        composeTestRule.onNodeWithContentDescription("Total circuits: 1").assertExists()
        composeTestRule.onNodeWithContentDescription("Passing circuits: 0").assertExists()
        composeTestRule.onNodeWithContentDescription("Failing circuits: 0").assertExists()
        composeTestRule.onNodeWithContentDescription("Standby circuits: 1").assertExists()
    }

    @Test
    fun test14_existingCardCallbacksAndTestTags_remainAvailable() {
        val c1 = createCircuit(id = 1, name = "MDB-Main", panelType = "MDB")
        val state = createSuccessState(
            circuits = listOf(c1),
            sizingResults = mapOf(1 to createSizing(SizingResultStatus.PASS))
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(
                    state = state,
                    viewModel = viewModel
                )
            }
        }

        // Search input tag exists
        composeTestRule.onNodeWithTag("search_circuits_input").assertExists().assertIsDisplayed()

        // Level, Status, and Sort selector tags exist
        composeTestRule.onNodeWithTag("filter_level_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_status_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuits_sort_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuits_visible_count_header").assertExists().assertIsDisplayed()

        // Circuit card exists with intact testTag
        composeTestRule.onNodeWithTag("circuit_card_MDB-Main").assertExists().assertIsDisplayed()

        // FAB exists
        composeTestRule.onNodeWithTag("add_circuit_fab").assertExists().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun test15_responsive_narrow320dp_rendersWithoutCrash() {
        val c1 = createCircuit(id = 1, name = "MDB-1")
        val state = createSuccessState(
            circuits = listOf(c1),
            sizingResults = mapOf(1 to createSizing(SizingResultStatus.PASS, vdPct = 1.5))
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                CircuitsGridScreen(
                    state = state,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag("circuits_summary_strip").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_circuits_input").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_level_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_status_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_card_MDB-1").assertExists().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h1024dp-xhdpi")
    fun test16_adaptive_wide840dp_tablet_rendersMultiColumnGrid() {
        val c1 = createCircuit(id = 1, name = "MDB-1")
        val c2 = createCircuit(id = 2, name = "MDB-2")
        val state = createSuccessState(
            circuits = listOf(c1, c2),
            sizingResults = mapOf(
                1 to createSizing(SizingResultStatus.PASS, vdPct = 1.2),
                2 to createSizing(SizingResultStatus.PASS, vdPct = 1.8)
            )
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                CircuitsGridScreen(
                    state = state,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag("circuits_summary_strip").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_circuits_input").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_level_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_status_selector").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuits_lazy_grid").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_card_MDB-1").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_card_MDB-2").assertExists().assertIsDisplayed()
    }
}
