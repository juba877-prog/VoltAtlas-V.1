package com.example.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
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
 * Phase 4 — adaptive Navigation 3 scene coverage for wide windows.
 *
 * The More-modules hub is a list pane and every More child a detail pane, so a wide
 * window shows both side by side while the rail (not the bottom bar) drives the shell.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w840dp-h1200dp-xhdpi")
class AppShellWorkspaceAdaptiveWideTest {

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
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        viewModel.createAndOpenProject("Adaptive Wide")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value != null
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun wideWindow_moreHub_showsDetailPlaceholder_thenChild_sharesListAndDetail() {
        setMainLayout()
        openTestProject()

        // Wide window: the shell uses the navigation rail.
        composeTestRule.onNodeWithTag("rail_dest_9").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Secondary Engineering Modules").assertIsDisplayed()
        // List pane alone: the detail pane shows the placeholder.
        composeTestRule.onNodeWithTag("workspace_list_detail_placeholder").assertIsDisplayed()

        // Selecting a module keeps the hub visible in the list pane (list-detail scene).
        composeTestRule.onNodeWithText("Protection & Coordination").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Secondary Engineering Modules").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Protection").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspace_list_detail_placeholder").assertDoesNotExist()
        // The selected module still belongs to the More destination in the chrome.
        composeTestRule.onNodeWithTag("rail_dest_9").assertIsSelected()
    }
}
