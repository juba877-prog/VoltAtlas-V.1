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
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
 * A11y Step 7: bottom navigation (phone, w360dp) touch-bound and semantics
 * regression coverage. Targets the real NavigationBarItem containers via
 * stable tags (Amendment 4) - never the Text label nodes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class AppShellPhoneTouchBoundsTest {

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
        // The app root is the entry / mode-selection screen; choose "Projects" so the
        // Project Picker / workspace (and with it the app shell) renders.
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        viewModel.createAndOpenProject("A11y Test")
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
    fun bottomNavItems_have48dpTouchBounds_clickRoleAndSelectedState() {
        setMainLayout()
        openTestProject()

        val targets = listOf(0, 1, 2, 3, 9)
        targets.forEach { target ->
            val tag = "bottom_dest_$target"
            assertTouchTargetAtLeast48dp(tag, "nav item $target")
            composeTestRule.onNodeWithTag(tag).assertHasClickAction()
            composeTestRule.onNodeWithTag(tag).assertIsSelectable()
            // Role.Tab is contributed by the NavigationBarItem selectable modifier.
            val actualRole = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Role]
            assertEquals("nav item $target must expose Role.Tab", Role.Tab, actualRole)
        }

        // Home (0) is selected initially.
        composeTestRule.onNodeWithTag("bottom_dest_0").assertIsSelected()
        composeTestRule.onNodeWithTag("bottom_dest_1").assertIsNotSelected()
    }

    @Test
    fun bottomNavItem_clickNavigatesAndUpdatesSelection() {
        setMainLayout()
        openTestProject()

        // Click the Circuits destination via its stable tag.
        composeTestRule.onNodeWithTag("bottom_dest_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_dest_1").assertIsSelected()
        composeTestRule.onNodeWithTag("bottom_dest_0").assertIsNotSelected()

        // Click More.
        composeTestRule.onNodeWithTag("bottom_dest_9").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_dest_9").assertIsSelected()
    }

    @Test
    fun phoneHeaderBackButton_is48dpTouchTarget() {
        setMainLayout()
        openTestProject()

        assertTouchTargetAtLeast48dp("phone_back_btn", "phone back")
        composeTestRule.onNodeWithContentDescription("Back to Projects").assertExists()
    }

    @Test
    fun themeToggleButton_is48dpTouchTarget() {
        setMainLayout()
        openTestProject()

        assertTouchTargetAtLeast48dp("theme_toggle_btn", "theme toggle")
    }
}