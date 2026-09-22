package com.example.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.ProjectEntity
import com.example.ai.context.revision.ProjectRevisionProvider
import com.example.ai.context.revision.ProjectRevisionSnapshot
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Gate D' addendum (D''-1.3): application-level failure-surfacing proof.
 *
 * Proves that when the live revision read fails indeterminately (DB locked / store offline) during a
 * real EngineeringViewModel.uiState recalculation pass, the exception thrown by the coordinator is:
 *   1. CAUGHT by the pipeline's own error boundary (EngineeringViewModel.kt combine try/catch),
 *   2. SURFACED as a user-visible UiState.Error, and
 *   3. NON-FATAL: the uiState StateFlow stays alive and emits again after recalculate().
 *
 * This is the "the app does not crash" test, distinct from the coordinator-level
 * "the function throws" tests in CoordinatorPassRevisionHoistingTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class EngineeringViewModelRevisionFailureSurfacingTest {

    private val failingProvider = object : ProjectRevisionProvider {
        override suspend fun getCurrentRevision(projectId: String): ProjectRevisionSnapshot {
            throw IllegalStateException("Revision store offline (simulated SQLiteDatabaseLockedException)")
        }
    }

    @Before
    fun setUp() {
        Catalog.resetForTesting()
        AppDatabase.useTestExecutors = true
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = failingProvider
    }

    @After
    fun tearDown() {
        EngineeringCalculationCoordinator.clearCache()
        EngineeringCalculationCoordinator.freshnessRegistry.clearAll()
        EngineeringCalculationCoordinator.projectRevisionProvider = null
        AppDatabase.useTestExecutors = false
    }

    @Test
    fun revisionResolutionFailure_surfacesAsUiStateError_andPipelineStaysAlive() = runBlocking {
        val pid = "PROJ_VM_FAIL_7777"
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Robolectric native SQLite needs the databases directory to exist before Room opens the file.
        val dbFile = application.getDatabasePath("electrical_engineering_db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
        val db = AppDatabase.getDatabase(application)
        db.projectDao().upsertProject(
            ProjectEntity(projectId = pid, projectName = "VM Revision Failure", createdAt = 0, updatedAt = 0, revision = 5L)
        )
        db.circuitDao().insertCircuit(
            CircuitEntity(
                id = 7777,
                projectId = pid,
                circuitName = "C-7777",
                loadKVA = 20.0,
                voltageV = 400,
                pf = 0.85,
                length_m = 20.0,
                cableType = "Cu/XLPE",
                installation = "In air",
                fedFrom = "Transformer",
                transformerId = ""
            )
        )

        val viewModel = EngineeringViewModel(application)
        val observations = mutableListOf<UiState>()
        val collector = launch { viewModel.uiState.collect { observations.add(it) } }

        // Opening the project makes the circuit flow emit; the combine then evaluates the passes,
        // the revision resolution fails, and the error boundary must surface UiState.Error.
        viewModel.openProject(pid)

        val surfaced = withTimeoutOrNull(60_000) {
            while (observations.none { it is UiState.Error }) delay(100)
            observations.filterIsInstance<UiState.Error>().first()
        }
        assertTrue(
            "D''-1: a revision-resolution failure during a real uiState pass must surface as UiState.Error " +
                "(observed=${observations.map { it.javaClass.simpleName }})",
            surfaced != null
        )
        assertTrue(
            "D''-1: the surfaced error must carry the resolution-failure message, observed='${surfaced!!.message}'",
            surfaced.message.contains("refusing to fabricate a revision")
        )

        // Non-fatality proof: the StateFlow must still be alive and emitting after recalculate().
        val sizeBeforeRecalculate = observations.size
        viewModel.recalculate()
        val grew = withTimeoutOrNull(60_000) {
            while (observations.size <= sizeBeforeRecalculate) delay(100)
            true
        }
        assertTrue(
            "D''-1: the uiState pipeline must remain collectable after the failure (no dead StateFlow)",
            grew == true
        )

        collector.cancel()
    }
}
