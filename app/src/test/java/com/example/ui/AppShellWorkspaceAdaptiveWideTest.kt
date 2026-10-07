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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.ui.theme.VoltAtlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
 *
 * KT-12: this test injects an IN-MEMORY Room database instead of using
 * `AppDatabase.getDatabase(context)`. Robolectric's native SQLite shadow cannot create a
 * file-backed database inside its per-test sandbox directory — the open fails with
 * `SQLITE_CANTOPEN` even though the path and its parent directory are both correct. That
 * failure is environmental, not a product defect, and it aborted the test before any
 * navigation assertion ran. See docs/audit/KT-12_DB_PATH_PROVENANCE.md (classification C).
 *
 * Production is unaffected: `EngineeringViewModel(application)` still resolves the real
 * file-backed database. This test validates UI/navigation behaviour, not filesystem
 * persistence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w840dp-h1200dp-xhdpi")
class AppShellWorkspaceAdaptiveWideTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: EngineeringViewModel
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val context = ApplicationProvider.getApplicationContext<Application>()
        // KT-12: per-test in-memory Room. Executors run inline so DAO work is deterministic and
        // allowMainThreadQueries permits the test thread. Mirrors the pattern already used by
        // EquipmentDeletionReferentialIntegrityTest, which is green with it.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()

        viewModel = EngineeringViewModel(context, database)
        viewModel.preferences.setHasCompletedFirstLaunch(true)
    }

    @After
    fun tearDown() {
        // Close only the injected in-memory database. The process-wide AppDatabase singleton is
        // never populated by this test, so there is nothing to reset.
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
        composeTestRule.onNodeWithTag("entry_projects_card").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openTestProject() {
        // Assert the real contract: the project-open coroutine completed successfully and
        // published the active project id. `createAndOpenProject` is async by design, so its Job
        // is awaited rather than polling a StateFlow against a timeout — a longer timeout or a
        // retry loop would only hide the underlying failure.
        val job = viewModel.createAndOpenProject("Adaptive Wide")
        kotlinx.coroutines.runBlocking { job.join() }

        assertTrue("project-open coroutine should have completed", job.isCompleted)
        assertFalse("project-open coroutine must not have failed", job.isCancelled)

        val projectId = viewModel.activeProjectId.value
        assertNotNull("createAndOpenProject must publish activeProjectId", projectId)

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
