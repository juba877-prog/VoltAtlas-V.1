package com.example.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Window-width policy boundary regression coverage.
 *
 * The shell's navigation mode must follow the centralized window-width
 * policy (see `com.example.ui.adaptive.VoltAtlasWindowWidth`), not device
 * identity or orientation alone:
 *
 *  - a LANDSCAPE window narrower than 600dp (e.g. split-screen landscape
 *    phone) must keep bottom navigation — orientation alone must NOT force
 *    the rail;
 *  - a PORTRAIT window of 600dp or wider (e.g. portrait tablet / unfolded
 *    foldable) must use the rail — width alone must be sufficient.
 *
 * Existing suites cover the compact phone (Pixel6) and w840dp rail cases;
 * these tests pin the boundary itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w560dp-h400dp-land-xhdpi")
class AppShellNarrowWindowBottomNavTest {

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
        // The app root is the entry / mode-selection screen; choose "Projects" so the
        // Project Picker / workspace (and with it the app shell) renders.
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        viewModel.createAndOpenProject("Narrow Window")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value != null
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun narrowLandscapeWindow_usesBottomNav_notRail() {
        setMainLayout()
        openTestProject()

        // Bottom navigation is present and destinations keep their tags.
        composeTestRule.onNodeWithTag("bottom_dest_0").assertExists()
        composeTestRule.onNodeWithTag("bottom_dest_9").assertExists()

        // The rail must NOT appear for a sub-600dp window, even in landscape.
        composeTestRule.onNodeWithTag("rail_dest_0").assertDoesNotExist()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w700dp-h800dp-xhdpi")
class AppShellMediumPortraitRailTest {

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
        // The app root is the entry / mode-selection screen; choose "Projects" so the
        // Project Picker / workspace (and with it the app shell) renders.
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        viewModel.createAndOpenProject("Medium Portrait")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value != null
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun mediumPortraitWindow_usesRail_notBottomNav() {
        setMainLayout()
        openTestProject()

        // Rail is present with its stable destination tags.
        composeTestRule.onNodeWithTag("rail_dest_0").assertExists()
        composeTestRule.onNodeWithTag("rail_dest_9").assertExists()

        // Bottom navigation must not appear for a 600dp+ window.
        composeTestRule.onNodeWithTag("bottom_dest_0").assertDoesNotExist()
    }
}
