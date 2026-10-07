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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Verification test suite for 3-tier hierarchical Back Navigation in VoltAtlas:
 *  - Level 1 (App Root / ProjectPickerScreen): Back press shows "Exit VoltAtlas?" confirmation dialog.
 *  - Level 2 (Project Root / DashboardScreen): Back press closes project and returns to ProjectPickerScreen.
 *  - Level 3 (Sub-screens & Tools): Back press returns to project Dashboard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class HierarchicalBackNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EngineeringViewModel
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val context = ApplicationProvider.getApplicationContext<Application>()
        // KT-12: inject a per-test in-memory Room database. Robolectric's native SQLite shadow
        // cannot create a file-backed database inside its sandbox directory (SQLITE_CANTOPEN),
        // which aborted this test before any navigation assertion ran. Production still uses the
        // real file-backed database. See docs/audit/KT-12_DB_PATH_PROVENANCE.md (classification C).
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()

        viewModel = EngineeringViewModel(context, database)
        viewModel.preferences.setHasCompletedFirstLaunch(true)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
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

    private fun openTestProject(name: String) {
        // KT-12: await the project-open Job instead of polling a StateFlow against a 15s timeout.
        // The timeout could only ever mask the underlying SQLite failure.
        val job = viewModel.createAndOpenProject(name)
        kotlinx.coroutines.runBlocking { job.join() }
        assertTrue("project-open coroutine should have completed", job.isCompleted)
        assertFalse("project-open coroutine must not have failed", job.isCancelled)
        assertNotNull("createAndOpenProject must publish activeProjectId", viewModel.activeProjectId.value)
        composeTestRule.waitForIdle()
    }

    @Test
    fun appRoot_backPress_showsExitConfirmationDialog() {
        // Given the user is on the app root: the new entry / mode-selection screen
        viewModel.closeProject()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value == null
        }
        setMainLayout()

        // When user presses system back
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Then Exit Dialog is displayed
        composeTestRule.onNodeWithText("Exit VoltAtlas?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Are you sure you want to close the app? All local calculations and projects are securely saved.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exit").assertIsDisplayed()

        // And dismissing the dialog leaves the user on the entry screen
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Electrical Engineering Platform").assertIsDisplayed()
        composeTestRule.onNodeWithTag("entry_projects_card").assertIsDisplayed()
    }

    @Test
    fun projectPicker_backPress_returnsToEntryScreen() {
        // Given the user picked "Projects" from the entry screen
        viewModel.closeProject()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value == null
        }
        setMainLayout()
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("+ New Project").assertIsDisplayed()

        // When Back is pressed on the Project Picker
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Then the user is back on the entry root, with no exit dialog and no picker
        composeTestRule.onNodeWithTag("entry_quick_card").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Exit VoltAtlas?").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("+ New Project").assertCountEquals(0)
    }

    @Test
    fun dashboard_backPress_closesProjectAndNavigatesToProjectPicker() {
        // Given an active project is open on Dashboard
        openTestProject("Hospital Substation Alpha")
        setMainLayout()
        // Choose the Projects mode from the new entry / mode-selection screen.
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()

        // When user presses back on Dashboard (Home tab)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value == null
        }
        composeTestRule.waitForIdle()

        // Then project is closed, returning to ProjectPickerScreen without exit dialog
        assertNull("Active project must be null after exiting Dashboard", viewModel.activeProjectId.value)
        composeTestRule.onNodeWithText("Electrical Engineering Platform").assertIsDisplayed()
    }

    @Test
    fun subScreen_backPress_returnsToDashboard_thenBackPress_returnsToProjectPicker() {
        // Given an active project is open
        openTestProject("Data Center Power Feed")
        setMainLayout()
        // Choose the Projects mode from the new entry / mode-selection screen.
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()

        // When user navigates to Circuits tab
        composeTestRule.onNodeWithTag("bottom_dest_1").performClick()
        composeTestRule.waitForIdle()

        // When user presses back from Circuits tab
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Then user is returned to Dashboard (Home tab) while project remains open
        assertNotNull("Project should still be open on Dashboard", viewModel.activeProjectId.value)

        // When user presses back again from Dashboard
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.activeProjectId.value == null
        }
        composeTestRule.waitForIdle()

        // Then project is closed and ProjectPickerScreen is displayed
        assertNull("Active project must be null after exiting Dashboard", viewModel.activeProjectId.value)
        composeTestRule.onNodeWithText("Electrical Engineering Platform").assertIsDisplayed()
    }
}
