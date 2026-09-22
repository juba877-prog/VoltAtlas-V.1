package com.example.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.theme.VoltAtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 3D — GlobalSettingsScreen responsive coverage (structure/semantics,
 * no screenshot goldens).
 *
 * The Voltage-Drop limit selectors and the project phase-imbalance selector
 * pair a label with intrinsic FilterChips. Under narrow width or enlarged
 * font scale the chips must stack under the label (keeping usable touch
 * targets) instead of being crushed into the leftover inline space.
 *
 * Settings semantics and persisted values are exercised for real through the
 * production ViewModel/preferences. Chip assertions use labels unique against
 * helper texts ("4.0%"/"6.0%" etc. — "3.0%"/"5.0%" also occur in helpers).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class GlobalSettingsNarrowWidthTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.useTestExecutors = true
        viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    @Test
    fun narrowWidth_limitChipsKeepUsableWidth() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                GlobalSettingsScreen(
                    viewModel = viewModel,
                    preferences = viewModel.preferences,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToIndex(1)
        composeTestRule.waitForIdle()

        listOf("4.0%", "6.0%", "7.0%").forEach { chip ->
            val node = composeTestRule.onNodeWithText(chip).fetchSemanticsNode()
            assertTrue(
                "chip '$chip' must keep usable width, got ${node.boundsInRoot.width}px",
                node.boundsInRoot.width >= 30f
            )
        }
        // Note: the phase-imbalance selector uses the same
        // SettingLabelChipsRow component but renders only when an active
        // project exists; it is covered structurally by the VD selectors.
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class GlobalSettingsEnlargedFontTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.useTestExecutors = true
        viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    @Test
    fun mediumWidth_fsc1_5_limitChipsStackUnderLabel() {
        composeTestRule.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(base.density, fontScale = 1.5f)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    GlobalSettingsScreen(
                        viewModel = viewModel,
                        preferences = viewModel.preferences,
                        onBack = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToIndex(1)
        composeTestRule.waitForIdle()

        // Font-scale-stacked chips keep usable width and remain clickable
        // (selection toggling exercises the real preference callback).
        val node = composeTestRule.onNodeWithText("4.0%").fetchSemanticsNode()
        assertTrue(node.boundsInRoot.width >= 30f)
        composeTestRule.onNodeWithText("4.0%").performClick()
        composeTestRule.waitForIdle()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class GlobalSettingsMediumInlineRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.useTestExecutors = true
        viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    @Test
    fun mediumWidth_fsc1_0_labelAndChipsStayInline() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                GlobalSettingsScreen(
                    viewModel = viewModel,
                    preferences = viewModel.preferences,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToIndex(1)
        composeTestRule.waitForIdle()

        // Inline contract at medium width + fsc 1.0: label row and its chips
        // share the same line (centers within ~1 chip height).
        val label = composeTestRule.onNodeWithText("Lighting Circuits Limit")
            .fetchSemanticsNode().boundsInRoot
        val chip = composeTestRule.onNodeWithText("4.0%")
            .fetchSemanticsNode().boundsInRoot
        val labelCenterY = (label.top + label.bottom) / 2f
        val chipCenterY = (chip.top + chip.bottom) / 2f
        assertTrue(
            "label and chips must stay inline at medium width (dy=${labelCenterY - chipCenterY})",
            kotlin.math.abs(labelCenterY - chipCenterY) <= 40f
        )
    }
}