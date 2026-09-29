package com.example.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Navigation integration conformance for the entry flow (Phase 3.2, Navigation 3).
 *
 * The app-level destinations (Entry / Project Picker / Quick Engineering hub and tools)
 * now live on a Navigation 3 back stack rendered by NavDisplay. These checks drive the
 * real composition instead of asserting source strings, keeping the original contract:
 *  - App launch reaches the entry screen first (neither picker nor hub is composed)
 *  - The Projects destination reaches the Project Picker; Back returns to the entry root
 *  - The Quick Engineering destination reaches the existing hub
 *  - The Project Picker does not host a Quick Engineering surface
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class VoltAtlasEntryNavigationConformanceTest {

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
        AppDatabase.useTestExecutors = false
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
    fun appLaunch_reachesEntryScreenFirst() {
        setMainLayout()

        composeTestRule.onNodeWithTag("entry_projects_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("entry_quick_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("+ New Project").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Search calculations...").assertCountEquals(0)
    }

    @Test
    fun entryProjects_reachesPicker_andBackReturnsToEntryRoot() {
        setMainLayout()

        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("+ New Project").assertIsDisplayed()

        pressBack()

        composeTestRule.onNodeWithTag("entry_projects_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("+ New Project").assertCountEquals(0)
    }

    @Test
    fun entryQuickEngineering_reachesExistingHub() {
        setMainLayout()

        composeTestRule.onNodeWithTag("entry_quick_card").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Search calculations...").assertIsDisplayed()
    }

    @Test
    fun projectPicker_doesNotHostQuickEngineeringEntry() {
        setMainLayout()

        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()

        // The picker keeps its project functionality and hosts no Quick Engineering surface.
        composeTestRule.onNodeWithText("+ New Project").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Search calculations...").assertCountEquals(0)
    }
}
