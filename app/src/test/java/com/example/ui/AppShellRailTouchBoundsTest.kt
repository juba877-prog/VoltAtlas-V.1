package com.example.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.theme.VoltAtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A11y Step 7: NavigationRail (tablet / wide screen, w840dp-land) touch-bound
 * and semantics regression coverage. Targets the real NavigationRailItem
 * containers via stable tags (Amendment 4) - never the Text label nodes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w840dp-h400dp-land-xhdpi")
class AppShellRailTouchBoundsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        // Drive viewModelScope deterministically (same pattern as ImportExportTest)
        // so createAndOpenProject completes without depending on looper pumping.
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

    private fun setMainLayout() {
        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                VoltAtlasTheme {
                    MainLayout(viewModel = viewModel)
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        viewModel.createAndOpenProject("A11y Rail")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value != null
        }
        composeTestRule.waitForIdle()
    }

    private fun assertTouchTargetAtLeast48dp(tag: String, label: String): Rect {
        val node = composeTestRule.onNodeWithTag(tag)
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("$label (tag=$tag) touch width ${bounds.width}px is below 48dp", bounds.width >= 48f)
        assertTrue("$label (tag=$tag) touch height ${bounds.height}px is below 48dp", bounds.height >= 48f)
        return bounds
    }

    @Test
    fun railItems_have48dpTouchBounds_clickRoleAndSelectedState() {
        setMainLayout()
        openTestProject()

        val targets = listOf(0, 1, 2, 3, 9)
        targets.forEach { target ->
            val tag = "rail_dest_$target"
            assertTouchTargetAtLeast48dp(tag, "rail item $target")
            composeTestRule.onNodeWithTag(tag).assertHasClickAction()
            composeTestRule.onNodeWithTag(tag).assertIsSelectable()
            val actualRole = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Role]
            assertEquals("rail item $target must expose Role.Tab", Role.Tab, actualRole)
        }

        // Dashboard (0) is selected initially.
        composeTestRule.onNodeWithTag("rail_dest_0").assertIsSelected()
        composeTestRule.onNodeWithTag("rail_dest_1").assertIsNotSelected()
    }

    @Test
    fun railItem_clickNavigatesAndUpdatesSelection() {
        setMainLayout()
        openTestProject()

        composeTestRule.onNodeWithTag("rail_dest_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rail_dest_2").assertIsSelected()
        composeTestRule.onNodeWithTag("rail_dest_0").assertIsNotSelected()
    }

    @Test
    fun railHeaderBackButton_is48dpTouchTarget() {
        setMainLayout()
        openTestProject()

        assertTouchTargetAtLeast48dp("rail_back_btn", "rail back")
        composeTestRule.onNodeWithContentDescription("Back to Projects").assertExists()
    }
}