package com.example.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 4 — adaptive Navigation 3 scene coverage for phone-width windows.
 *
 * Below the 600dp breakpoint there is no list-detail scene: the More hub and its
 * children stay single-pane and the detail placeholder never appears.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class AppShellWorkspaceListDetailNarrowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Application>()
        viewModel = EngineeringViewModel(context)
        viewModel.preferences.setHasCompletedFirstLaunch(true)
    }

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTesting()
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
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        viewModel.createAndOpenProject("Adaptive Phone")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value != null
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun phoneWidth_moreHub_staysSinglePane_withoutPlaceholder() {
        setMainLayout()
        openTestProject()

        composeTestRule.onNodeWithTag("bottom_dest_9").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Secondary Engineering Modules").assertIsDisplayed()
        // No list-detail scene on a phone: the detail placeholder must not exist.
        composeTestRule.onNodeWithTag("workspace_list_detail_placeholder").assertDoesNotExist()

        composeTestRule.onNodeWithText("Protection & Coordination").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Protection").onFirst().assertIsDisplayed()
        // Single pane: the hub is not composed any more.
        composeTestRule.onAllNodesWithText("Secondary Engineering Modules").assertCountEquals(0)
        composeTestRule.onNodeWithTag("bottom_dest_9").assertIsSelected()
    }
}
