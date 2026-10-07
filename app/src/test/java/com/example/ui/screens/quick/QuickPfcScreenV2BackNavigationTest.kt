package com.example.ui.screens.quick

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.EngineeringViewModel
import com.example.ui.MainLayout
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
 * System-back contract for the Quick Engineering PFC tool (Phase 3.2, Navigation 3 slice 2).
 *
 * The quick tool screens no longer register their own BackHandler: Navigation 3's NavDisplay
 * owns system Back for the quick-engineering destinations so pops animate predictively.
 * This integration test drives the real MainLayout stack:
 *   Entry -> Quick hub -> Power Factor Correction tool -> system Back -> Quick hub.
 * Before Navigation 3 existed, an unhandled Back on this tool finished the Activity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class QuickPfcScreenV2BackNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
                VoltAtlasTheme { MainLayout(viewModel = viewModel) }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun systemBack_fromPfcTool_popsToQuickHub_viaNavigation3() {
        setMainLayout()

        // Entry -> Quick Engineering hub
        composeTestRule.onNodeWithTag("entry_quick_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Search calculations...").assertIsDisplayed()

        // Hub -> Power Factor Correction tool (scroll the tile into view first)
        composeTestRule.onNodeWithText("Power Factor Correction").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Target PF, Reactive Relief & Capacitor Sizing").assertIsDisplayed()

        // System Back pops the Navigation 3 stack back to the hub instead of finishing
        // the Activity (the screen itself no longer registers a legacy handler).
        pressBack()
        composeTestRule.onNodeWithText("Search calculations...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Target PF, Reactive Relief & Capacitor Sizing").assertDoesNotExist()
    }
}
