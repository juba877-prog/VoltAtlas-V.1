package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.example.ui.screens.MoreModulesGrid
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * More Modules responsive structure/semantic coverage (no screenshot
 * goldens): verifies that the compact single-column flow and the
 * medium/expanded adaptive grid preserve the eight modules, their order,
 * and their navigation targets, and survive enlarged font scale.
 */
private val moduleTitles = listOf(
    "Panel Schedule & Load Aggregation",
    "Single-Line Diagram (SLD)",
    "Protection & Coordination",
    "Conduits & Cable Fill",
    "BOQ & Procurement",
    "Electrical & Cable Conversions",
    "Engineering Methodology & Formulas",
    "Global Settings"
)

private fun centerOf(node: SemanticsNode): Pair<Float, Float> {
    val b = node.boundsInRoot
    return (b.left + b.right) / 2f to (b.top + b.bottom) / 2f
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class MoreModulesCompactFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compact_singleColumn_allModulesInOrder_navigationIntact() {
        val navigated = mutableStateListOf<Int>()
        composeTestRule.setContent {
            VoltAtlasTheme {
                MoreModulesGrid(onNavigate = { navigated.add(it) })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Secondary Engineering Modules").assertIsDisplayed()

        // All eight modules are present.
        val centers = moduleTitles.map { title ->
            val node = composeTestRule.onNodeWithText(title).fetchSemanticsNode()
            title to centerOf(node)
        }

        // Single column: the first two modules are vertically stacked —
        // same horizontal center, second below the first.
        val (firstX, firstY) = centers[0].second
        val (secondX, secondY) = centers[1].second
        assertEquals(firstX, secondX, 1f)
        assertTrue(secondY > firstY)

        // Documented order preserved (top to bottom).
        val ys = centers.map { it.second.second }
        assertEquals(ys.sorted(), ys)

        // Navigation targets remain unchanged: BOQ -> tab 7.
        composeTestRule.onNodeWithText("BOQ & Procurement").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(7), navigated.toList())
    }

    @Test
    fun compact_enlargedFontScale1_5_allModulesReachable() {
        val navigated = mutableStateListOf<Int>()
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f)
            ) {
                VoltAtlasTheme {
                    MoreModulesGrid(onNavigate = { navigated.add(it) })
                }
            }
        }
        composeTestRule.waitForIdle()

        // Every module remains in the semantics tree at fontScale 1.5
        // (nothing silently dropped or collapsed).
        moduleTitles.forEach { title ->
            composeTestRule.onNodeWithText(title).assertExists()
        }

        // The last module is reachable by scrolling (no clipped-away tail).
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Global Settings"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Global Settings").assertIsDisplayed()

        // Navigation still wired at enlarged font scale.
        composeTestRule.onNodeWithText("Global Settings").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(8), navigated.toList())
    }
}


@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class MoreModulesMediumGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mediumWindow_adaptiveGridUsesMultipleColumns() {
        composeTestRule.setContent {
            VoltAtlasTheme { MoreModulesGrid(onNavigate = {}) }
        }
        composeTestRule.waitForIdle()

        // All eight modules still present in the grid.
        moduleTitles.forEach { title ->
            composeTestRule.onNodeWithText(title).assertExists()
        }

        // At w700dp with a 320dp content minimum the adaptive grid must
        // actually use more than one column: the eight module titles must
        // occupy exactly two distinct x-positions.
        val xs = moduleTitles.map { title ->
            centerOf(composeTestRule.onNodeWithText(title).fetchSemanticsNode()).first
        }
        val distinctColumns = xs.distinctBy { (it / 10f).toInt() }
        assertEquals("expected exactly 2 grid columns at w700dp", 2, distinctColumns.size)

        // The first two modules share a grid row (same row top). Their
        // TITLE centers may differ by up to one wrapped line (~20dp) because
        // a long title wraps to two lines inside its card, so compare with a
        // generous tolerance that still excludes a stacked single-column
        // arrangement (>= ~72dp card pitch apart).
        val first = centerOf(composeTestRule.onNodeWithText(moduleTitles[0]).fetchSemanticsNode())
        val second = centerOf(composeTestRule.onNodeWithText(moduleTitles[1]).fetchSemanticsNode())
        assertEquals("first two modules must share a grid row", first.second, second.second, 40f)
        assertNotEquals(
            "first two modules must sit in different grid columns",
            first.first, second.first, 1f
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1200dp-h800dp-xhdpi")
class MoreModulesExpandedGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun expandedWindow_adaptiveGridGrowsFurtherColumns() {
        composeTestRule.setContent {
            VoltAtlasTheme { MoreModulesGrid(onNavigate = {}) }
        }
        composeTestRule.waitForIdle()

        // At w1200dp the adaptive grid must fit at least three cards per row.
        val xs = moduleTitles.take(3).map { title ->
            centerOf(composeTestRule.onNodeWithText(title).fetchSemanticsNode()).first
        }
        val distinctXs = xs.distinctBy { (it / 10f).toInt() }
        assertTrue(
            "expected >= 3 columns at w1200dp, found $distinctXs",
            distinctXs.size >= 3
        )
    }
}
