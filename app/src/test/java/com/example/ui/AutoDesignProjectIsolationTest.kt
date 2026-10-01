package com.example.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.EarthingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * P0-13 (F-02, O-01) regression test: the auto-design result is project-scoped.
 *
 * Before the fix, `_autoDesignResultState` survived project switches — running auto-design
 * in project A and opening project B rendered project A's electrode layout under B (and into
 * the PDF). The cache is now keyed by projectId AND cleared on every project transition
 * ([EngineeringViewModel.switchActiveProject]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class AutoDesignProjectIsolationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppDatabase.useTestExecutors = true
    }

    @After
    fun tearDown() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        AppDatabase.useTestExecutors = false
        try {
            Dispatchers.resetMain()
        } catch (_: IllegalStateException) {
            // Guard against background scheduler worker thread race during full suite runs
        }
    }

    private fun idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.waitFor(
        condition: () -> Boolean
    ): Boolean {
        repeat(60) {
            idle()
            if (condition()) return true
            delay(50)
        }
        return condition()
    }

    @Test
    fun autoDesignResult_neverCrossesProjects() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = EngineeringViewModel(app)
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Startup
        for (i in 1..50) {
            idle()
            val st = viewModel.startupState.value
            if (st is StartupState.Ready || st is StartupState.NoProjects) break
            delay(50)
        }
        idle()
        assertTrue(
            "startup must complete",
            viewModel.startupState.value is StartupState.Ready ||
                viewModel.startupState.value is StartupState.NoProjects
        )

        // ---------------- Project A: enable + run auto-design ----------------
        viewModel.createAndOpenProject("AD_Project_A").join()
        assertTrue(
            "Project A must become active",
            waitFor { viewModel.activeProjectId.value != null }
        )
        val projectAId = viewModel.activeProjectId.value!!
        assertEquals(projectAId, viewModel.activeProjectId.value)

        viewModel.onSaveEarthing(EarthingEntity(projectId = projectAId, isAutoDesignEnabled = true))
        assertTrue(
            "A's earthing settings (auto-design enabled) must reach uiState",
            waitFor {
                (viewModel.uiState.value as? UiState.Success)?.earthingEntity
                    ?.isAutoDesignEnabled == true
            }
        )

        viewModel.onRunAutoDesign()
        assertTrue(
            "precondition: A's auto-design result must be present after onRunAutoDesign",
            waitFor { (viewModel.uiState.value as? UiState.Success)?.earthingResult?.autoDesignResult != null }
        )
        val aState = viewModel.uiState.value as? UiState.Success
        assertNotNull(aState?.earthingResult?.autoDesignResult)

        // ---------------- Project B: same feature enabled, NO run ----------------
        viewModel.createAndOpenProject("AD_Project_B").join()
        assertTrue(
            "Project B must become active",
            waitFor { viewModel.activeProjectId.value != null && viewModel.activeProjectId.value != projectAId }
        )
        val projectBId = viewModel.activeProjectId.value!!
        assertNotEquals(projectAId, projectBId)

        viewModel.onSaveEarthing(EarthingEntity(projectId = projectBId, isAutoDesignEnabled = true))
        assertTrue(
            "B's earthing settings (auto-design enabled) must reach uiState",
            waitFor {
                (viewModel.uiState.value as? UiState.Success)?.earthingEntity
                    ?.isAutoDesignEnabled == true
            }
        )
        idle()
        val bState = viewModel.uiState.value as? UiState.Success
        assertEquals(
            "B's uiState must belong to project B",
            projectBId,
            (bState?.earthingEntity?.projectId)
        )
        assertNull(
            "P0-13: project A's auto-design result must NEVER render under project B",
            bState?.earthingResult?.autoDesignResult
        )

        // ---------------- Project B: running its own design works ----------------
        viewModel.onRunAutoDesign()
        assertTrue(
            "B must be able to compute its own auto-design after the switch",
            waitFor { (viewModel.uiState.value as? UiState.Success)?.earthingResult?.autoDesignResult != null }
        )
        val bWithDesign = viewModel.uiState.value as? UiState.Success
        assertNotNull(bWithDesign?.earthingResult?.autoDesignResult)

        // ---------------- Back to A: cache was cleared on the transition ----------------
        viewModel.openProject(projectAId).join()
        assertTrue(
            "must return to project A",
            waitFor { viewModel.activeProjectId.value == projectAId }
        )
        assertTrue(
            "A's settings must be active again",
            waitFor {
                (viewModel.uiState.value as? UiState.Success)?.earthingEntity?.isAutoDesignEnabled == true
            }
        )
        idle()
        assertNull(
            "re-opening A must not resurrect a cached design — it is cleared on every transition",
            (viewModel.uiState.value as? UiState.Success)?.earthingResult?.autoDesignResult
        )

        collectJob.cancelAndJoin()
        idle()
    }
}
