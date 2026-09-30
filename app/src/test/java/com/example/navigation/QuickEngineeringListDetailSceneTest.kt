package com.example.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.EngineeringViewModel
import com.example.ui.MainLayout
import com.example.ui.theme.VoltAtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 3 Test Suite: Verification of QuickEngineeringListDetailScene and two-pane adaptive behavior.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickEngineeringListDetailSceneTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Application>()
        viewModel = EngineeringViewModel(context)
        viewModel.preferences.setHasCompletedFirstLaunch(true)
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
        Dispatchers.resetMain()
    }

    private fun dummyScope(): SceneStrategyScope<NavKey> {
        return object : SceneStrategyScope<NavKey>() {}
    }

    // =========================================================================
    // 1. STRATEGY UNIT TESTS
    // =========================================================================

    @Test
    fun strategy_narrowWindow_returnsNull_preservingSinglePane() {
        val strategy = QuickEngineeringListDetailSceneStrategy<NavKey>(
            windowWidthDp = 411,
            detailPlaceholder = { Text("Placeholder") }
        )
        val hubEntry: NavEntry<NavKey> = NavEntry(QuickHubKey, metadata = QuickEngineeringListDetailScene.listPane()) { Text("Hub") }
        val toolEntry: NavEntry<NavKey> = NavEntry(QuickToolKey("cable_sizing"), metadata = QuickEngineeringListDetailScene.detailPane()) { Text("Tool") }

        val scope = dummyScope()
        with(scope) {
            val scene = strategy.run { calculateScene(listOf(hubEntry, toolEntry)) }
            assertNull("Narrow windows (< 600dp) must return null to fall back to single pane", scene)
        }
    }

    @Test
    fun strategy_wideWindow_withoutHubEntry_returnsNull() {
        val strategy = QuickEngineeringListDetailSceneStrategy<NavKey>(
            windowWidthDp = 1024,
            detailPlaceholder = { Text("Placeholder") }
        )
        val otherEntry: NavEntry<NavKey> = NavEntry(QuickToolKey("cable_sizing")) { Text("No metadata") }

        val scope = dummyScope()
        with(scope) {
            val scene = strategy.run { calculateScene(listOf(otherEntry)) }
            assertNull("Without listPane metadata, calculateScene must return null", scene)
        }
    }

    @Test
    fun strategy_wideWindow_withHubOnly_returnsTwoPaneWithPlaceholder() {
        val strategy = QuickEngineeringListDetailSceneStrategy<NavKey>(
            windowWidthDp = 840,
            detailPlaceholder = { Text("Placeholder") }
        )
        val hubEntry: NavEntry<NavKey> = NavEntry(QuickHubKey, metadata = QuickEngineeringListDetailScene.listPane()) { Text("Hub") }

        val scope = dummyScope()
        with(scope) {
            val scene = strategy.run { calculateScene(listOf(hubEntry)) }
            assertNotNull("Wide window with listPane must return a QuickEngineeringListDetailScene", scene)
            assertTrue(scene is QuickEngineeringListDetailScene)
            assertEquals(1, scene!!.entries.size)
        }
    }

    @Test
    fun strategy_wideWindow_withHubAndTool_returnsTwoPaneWithBothEntries() {
        val strategy = QuickEngineeringListDetailSceneStrategy<NavKey>(
            windowWidthDp = 1024,
            detailPlaceholder = { Text("Placeholder") }
        )
        val hubEntry: NavEntry<NavKey> = NavEntry(QuickHubKey, metadata = QuickEngineeringListDetailScene.listPane()) { Text("Hub") }
        val toolEntry: NavEntry<NavKey> = NavEntry(QuickToolKey("cable_sizing"), metadata = QuickEngineeringListDetailScene.detailPane()) { Text("Tool") }

        val scope = dummyScope()
        with(scope) {
            val scene = strategy.run { calculateScene(listOf(hubEntry, toolEntry)) }
            assertNotNull(scene)
            assertEquals(2, scene!!.entries.size)
            assertEquals(hubEntry.contentKey, scene.key)
        }
    }

    // =========================================================================
    // 2. COMPOSE TWO-PANE INTEGRATION TESTS (TABLET 1024dp)
    // =========================================================================

    private fun setMainLayout() {
        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalViewModelStoreOwner provides storeOwner
            ) {
                VoltAtlasTheme {
                    MainLayout(viewModel = viewModel)
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun tablet_opensQuickEngineering_showsTwoPaneLayoutWithPlaceholder() {
        setMainLayout()

        // 1. From entry screen, tap Quick Engineering
        composeTestRule.onNodeWithTag("entry_quick_card").performClick()
        composeTestRule.waitForIdle()

        // 2. On wide 1024dp tablet screen, the two-pane row must be composed
        composeTestRule.onNodeWithTag("quick_engineering_two_pane_row").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_hub_list_pane").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_tool_detail_pane").assertExists().assertIsDisplayed()

        // 3. Left pane has calculation search, right pane has placeholder
        composeTestRule.onNodeWithTag("quick_hub_search_field").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_tool_placeholder").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Engineering Toolbox").assertExists().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-mdpi")
    fun tablet_selectsTool_displaysToolInDetailPaneWhileKeepingListPane() {
        setMainLayout()

        // 1. Open Quick Engineering
        composeTestRule.onNodeWithTag("entry_quick_card").performClick()
        composeTestRule.waitForIdle()

        // 2. Select Cable Sizing card
        composeTestRule.onNodeWithText("Cable Sizing").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify both panes remain active simultaneously:
        // Left pane retains search & categories
        composeTestRule.onNodeWithTag("quick_hub_list_pane").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_hub_search_field").assertExists().assertIsDisplayed()

        // Right pane displays the selected tool's screen
        composeTestRule.onNodeWithTag("quick_tool_detail_pane").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Cable Sizing").assertExists().assertIsDisplayed()
    }
}
