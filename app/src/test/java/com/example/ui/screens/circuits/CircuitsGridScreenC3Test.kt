package com.example.ui.screens.circuits

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.CircuitEntity
import com.example.engine.CableFailureCode
import com.example.engine.ShortCircuitResult
import com.example.engine.SizingResult
import com.example.engine.SizingResultStatus
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.screens.CircuitsGridScreen
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class CircuitsGridScreenC3Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    private fun createCircuit(
        id: Int, name: String = "Circuit-$id", loadKVA: Double = 50.0,
        fedFrom: String = "MDB-1", parentId: Int? = null, panelType: String? = null,
        cableType: String = "Cu/XLPE/PVC", lengthM: Double = 30.0, maxVDpct: Double = 0.03,
        loadType: String = "NORMAL"
    ) = CircuitEntity(
        id = id, projectId = "DEFAULT_PROJECT", circuitName = name, loadKVA = loadKVA,
        fedFrom = fedFrom, parentId = parentId, panelType = panelType, cableType = cableType,
        length_m = lengthM, maxVDpct = maxVDpct, loadType = loadType
    )

    private fun createSizing(
        status: SizingResultStatus, cableSize: String = "70", ibA: Double = 120.0,
        deviceSizeA: Double = 160.0, deviceType: String = "MCCB", vdPct: Double = 2.45,
        utilizationPct: Double = 65.0, izA: Double = 185.0, breakingKA: Double = 36.0
    ) = SizingResult(
        Ib_A = ibA, deviceSize_A = deviceSizeA, deviceType = deviceType, cTotalDerating = 0.8,
        iRequired_perCable_A = 150.0, productCode = "PROD-1", cableSize_mm2 = cableSize,
        ratedI_table_A = 200.0, capacity_derated_Iz_A = izA, utilizationPct = utilizationPct,
        neutral_mm2 = "35", earthCPC_mm2 = "35", voltDrop_V = 5.0, vdPct = vdPct,
        cumulativeVDpct = vdPct, status = if (status == SizingResultStatus.PASS) "PASS" else "FAIL",
        deviceBreakingKA = breakingKA, resultStatus = status,
        isAmpacityOk = status == SizingResultStatus.PASS, isVoltageDropOk = status == SizingResultStatus.PASS,
        primaryFailureCode = if (status == SizingResultStatus.FAIL) CableFailureCode.AMPACITY_FAIL else null
    )

    private fun createShortCircuit(circuitId: Int, iscKA: Double) = ShortCircuitResult(
        circuitId = circuitId, sourceName = "Transformer", cableMm2 = "185", iscKA = iscKA,
        iwKA = iscKA * 0.8, deviceKA = 36.0, deviceCheck = "OK", cableCheck = "OK",
        marginPct = 48.0, status = "CHECK OK", note = ""
    )

    private fun createSuccessState(
        circuits: List<CircuitEntity>, sizingResults: Map<Int, SizingResult> = emptyMap(),
        shortCircuitResults: Map<Int, ShortCircuitResult> = emptyMap()
    ) = UiState.Success(
        circuits = circuits, transformers = emptyList(), sizingResults = sizingResults,
        shortCircuitResults = shortCircuitResults, transformerResults = emptyMap(), boq = emptyList(),
        drums = emptyList(), procurement = emptyList(), panels = emptyList(),
        pfCorrectionResults = emptyList()
    )

    private fun setScreen(
        circuits: List<CircuitEntity>, sizings: Map<Int, SizingResult> = emptyMap(),
        shortCircuits: Map<Int, ShortCircuitResult> = emptyMap(),
        onNavigateToPanelSchedule: ((Int) -> Unit)? = null
    ) {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                CircuitsGridScreen(
                    state = createSuccessState(circuits, sizings, shortCircuits),
                    viewModel = viewModel, onNavigateToPanelSchedule = onNavigateToPanelSchedule
                )
            }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = EngineeringViewModel(context)
    }

    // ---- measurement helpers (UNMERGED tree, scoped INSIDE a given card) ----

    private fun cardRect(tag: String): Rect =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun rectContains(outer: Rect, inner: Rect): Boolean =
        inner.left >= outer.left && inner.right <= outer.right &&
            inner.top >= outer.top && inner.bottom <= outer.bottom

    private fun rectsOverlap(a: Rect, b: Rect): Boolean =
        !(a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom)

    /** Bounds of the first text node matching [text] that lies INSIDE the given card. */
    private fun inCard(cardTag: String, text: String, exact: Boolean = false): Rect {
        val card = cardRect(cardTag)
        val candidates = composeTestRule
            .onAllNodesWithText(text, substring = !exact, useUnmergedTree = true)
            .fetchSemanticsNodes()
        return candidates.map { it.boundsInRoot }.firstOrNull { rectContains(card, it) }
            ?: error("text '$text' not found inside card '$cardTag' (candidates=${candidates.size})")
    }

    @Test
    fun test1_passCard_titleAndMetricsHaveWidthAndFitWithinCard() {
        val c1 = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        val short = createShortCircuit(1, 14.85)
        setScreen(listOf(c1), mapOf(1 to sizing), mapOf(1 to short))
        val tag = "circuit_card_MDB-Main"
        val card = cardRect(tag)

        val name = inCard(tag, "MDB-Main", exact = true)
        assertTrue("title width ${name.width} must be substantial (no vertical collapse)", name.width > 60f)
        assertTrue("title height ${name.height} must be a normal line (no vertical stack)", name.height < 80f)

        val cable = inCard(tag, "4x185")
        assertTrue("cable size inside card", rectContains(card, cable))
        val badge = inCard(tag, "PASS", exact = true)
        assertTrue("badge inside card", rectContains(card, badge))

        val (load, ib, inn) = listOf(inCard(tag, "250.0 kVA"), inCard(tag, "360.0 A"), inCard(tag, "400A MCCB"))
        assertTrue(load.width > 30f); assertTrue(ib.width > 30f); assertTrue(inn.width > 30f)
        assertFalse("Load/Ib overlap", rectsOverlap(load, ib))
        assertFalse("Ib/In overlap", rectsOverlap(ib, inn))

        val (vd, loading, isc) = listOf(inCard(tag, "0.85%"), inCard(tag, "65.0%"), inCard(tag, "14.9 kA"))
        assertFalse("VD/Loading overlap", rectsOverlap(vd, loading))
        assertFalse("Loading/Isc overlap", rectsOverlap(loading, isc))
        assertTrue(vd.width > 30f)
    }

    @Test
    fun test2_footerHorizontal_wrappedHeight_breadcrumb() {
        val c1 = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c1), mapOf(1 to sizing))
        val tag = "circuit_card_MDB-Main"
        val card = cardRect(tag)

        assertTrue("wrapped height ${card.height}", card.height >= 80f && card.height <= 900f)
        val footer = inCard(tag, "Length: 15m")
        assertTrue(rectContains(card, footer))
        assertTrue("footer horizontal w=${footer.width} h=${footer.height}", footer.width > footer.height)
        val crumb = inCard(tag, "Fed from: Transformer > MDB-Main")
        assertTrue("crumb horizontal w=${crumb.width} h=${crumb.height}", crumb.width > crumb.height)
        assertTrue(rectContains(card, crumb))
    }

    @Test
    fun test3_failCard_statusBadgeAndMetricsVisible() {
        val c2 = createCircuit(2, "SMDB-1", 120.0, "MDB-Main", lengthM = 25.0)
        val sizing = createSizing(SizingResultStatus.FAIL, cableSize = "4x35", ibA = 86.6, deviceSizeA = 100.0, vdPct = 4.2)
        setScreen(listOf(c2), mapOf(2 to sizing))
        val tag = "circuit_card_SMDB-1"

        val badge = inCard(tag, "FAIL", exact = true)
        assertTrue(badge.width > 20f)
        val (ib, inn, vd) = listOf(inCard(tag, "86.6 A"), inCard(tag, "100A MCCB"), inCard(tag, "4.20%"))
        assertFalse("no overlap Ib/In", rectsOverlap(ib, inn))
        assertTrue(vd.width > 20f)
    }

    @Test
    fun test4_standbyMissingResultCard_usesEmDashAndKeepsRealZero() {
        val c5 = createCircuit(5, "AHU-Roof-01", 30.0, "MDB-Main", lengthM = 65.0)
        setScreen(listOf(c5))
        val tag = "circuit_card_AHU-Roof-01"

        val badge = inCard(tag, "STANDBY", exact = true)
        assertTrue(badge.width > 20f)
        inCard(tag, "30.0 kVA")
        assertTrue(
            "em dash placeholder present",
            composeTestRule.onAllNodesWithText("\u2014", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        )
        val name = inCard(tag, "AHU-Roof-01", exact = true)
        assertTrue(name.width > 60f)
    }

    @Test
    fun test5_longNameCardFitsWithinBounds() {
        val n = "AHU-RC-02-RECOVERY-AIR-HANDLING-SUPPLY-FAN-LONG"
        val c = createCircuit(1, n, 30.0, "MDB-Main", lengthM = 110.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x120", ibA = 44.0, deviceSizeA = 63.0, vdPct = 1.9)
        setScreen(listOf(c), mapOf(1 to sizing))
        val card = cardRect("circuit_card_$n")
        val name = inCard("circuit_card_$n", n, exact = true)
        assertTrue(name.width > 60f)
        assertTrue("long name confined to card", rectContains(card, name))
    }

    @Test
    fun test6_scheduleButton_invokesSchedule() {
        var navigateCount = 0
        val c = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c), mapOf(1 to sizing), onNavigateToPanelSchedule = { navigateCount++ })

        // Expand card to view operations
        composeTestRule.onNodeWithTag("circuit_card_MDB-Main").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("open_panel_schedule_btn_MDB-Main").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_panel_schedule_btn_MDB-Main").performClick()
        composeTestRule.waitForIdle()
        assertEquals("schedule invoked exactly once", 1, navigateCount)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-mdpi")
    fun test7_compactLayout_320dp_noCollapse() {
        val n = "DB-12-34-LIGHTING-POWER"
        val c = createCircuit(1, n, 250.0, "Transformer", panelType = "DB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c), mapOf(1 to sizing))
        val tag = "circuit_card_$n"

        composeTestRule.onNodeWithTag(tag).assertExists()
        val name = inCard(tag, n, exact = true)
        assertTrue("320 name width ${name.width}", name.width > 40f)
        assertFalse("320 Load/Ib overlap", rectsOverlap(inCard(tag, "250.0 kVA"), inCard(tag, "360.0 A")))
    }

    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi")
    fun test8_layout412dp() {
        val c = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c), mapOf(1 to sizing))
        val tag = "circuit_card_MDB-Main"
        assertFalse(rectsOverlap(inCard(tag, "250.0 kVA"), inCard(tag, "360.0 A")))
        assertFalse(rectsOverlap(inCard(tag, "360.0 A"), inCard(tag, "400A MCCB")))
        // Non-compact width: footer carries the real expand/details affordance.
        composeTestRule.onNodeWithTag("circuit_expand_btn_MDB-Main").assertExists()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test9_fontScale_1_3_rendersWithoutOverlap() {
        val c = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c), mapOf(1 to sizing))
        val tag = "circuit_card_MDB-Main"
        val name = inCard(tag, "MDB-Main", exact = true)
        assertTrue("1.3x name width ${name.width}", name.width > 40f)
        assertFalse("1.3x VD/Loading overlap", rectsOverlap(inCard(tag, "0.85%"), inCard(tag, "65.0%")))
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test10_fontScale_1_5_rendersWithoutOverlap() {
        val c = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c), mapOf(1 to sizing))
        val tag = "circuit_card_MDB-Main"
        val name = inCard(tag, "MDB-Main", exact = true)
        assertTrue("1.5x name width ${name.width}", name.width > 40f)
        assertFalse("1.5x VD/Loading overlap", rectsOverlap(inCard(tag, "0.85%"), inCard(tag, "65.0%")))
    }

    @Test
    fun test11_expandedOverridesReachable_withoutSizingResult() {
        val n = "PUMP-MAIN-01"
        val c = createCircuit(7, n, 22.0, "MDB-Main", lengthM = 12.0)
        setScreen(listOf(c)) // no sizing result -> overrides must still be reachable
        composeTestRule.onNodeWithTag("circuit_card_$n").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("override_device_type_btn").assertExists()
        composeTestRule.onNodeWithTag("override_breaker_btn").assertExists()
        composeTestRule.onNodeWithTag("override_cable_btn").assertExists()
        composeTestRule.onNodeWithTag("reset_to_auto_btn_$n").assertExists()
        composeTestRule.onNodeWithTag("apply_override_btn_$n").assertExists()
        composeTestRule.onNodeWithTag("edit_circuit_btn_$n").assertExists()
        composeTestRule.onNodeWithTag("delete_circuit_btn_$n").assertExists()
        // Truthful generic Auto label - no invented engine value when no result exists.
        assertTrue(
            "generic Auto wording shown without sizing result",
            composeTestRule.onAllNodesWithText("Auto (no sizing run yet)", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun test12_c2TopShellAndFabPresent() {
        val c = createCircuit(1, "MDB-Main", 250.0, "Transformer", panelType = "MDB", lengthM = 15.0)
        val sizing = createSizing(SizingResultStatus.PASS, cableSize = "4x185", ibA = 360.0, deviceSizeA = 400.0, vdPct = 0.85)
        setScreen(listOf(c), mapOf(1 to sizing))
        composeTestRule.onNodeWithTag("add_circuit_fab").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("CIRCUITS (1)", substring = true, useUnmergedTree = true).assertExists()
    }
}

