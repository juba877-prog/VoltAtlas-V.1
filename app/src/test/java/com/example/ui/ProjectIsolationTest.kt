package com.example.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
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
 * P0-15 (O-03, F-03): executable Room-backed A/B project-switch isolation test
 * (harness template: [ImportExportTest]).
 *
 * The former implementation was `assertTrue(true)` — a vacuous stub whose comment merely
 * *claimed* that flatMapLatest made cross-project leakage impossible. This version proves it:
 * circuits are written through the real DAO into two different projects, and across every
 * switch the ViewModel's uiState must expose only the ACTIVE project's rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ProjectIsolationTest {

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

    private fun isTerminalStartup(state: StartupState): Boolean = when (state) {
        is StartupState.Ready,
        is StartupState.NoProjects,
        is StartupState.ReadyWithWarnings -> true
        else -> false
    }

    @Test
    fun testProjectIsolation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = AppDatabase.getDatabase(app)
        val viewModel = EngineeringViewModel(app)

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Wait for VM startup to reach a TERMINAL state before activating any project.
        // retryStartup()'s initializeNewInstallationIfRequired() executes
        // switchActiveProject(null) BEFORE the terminal emission; if this test proceeds
        // while startup is still in flight (full-profile JVMs are slower than isolated
        // runs — reproduced order-dependent failure at :106), that late switch(null)
        // clobbers the activation below and the circuits flow — keyed by activeProjectId —
        // never surfaces the inserted rows. ReadyWithWarnings is terminal as well; the
        // former loop only accepted Ready/NoProjects and silently fell through otherwise.
        var startupAttempts = 0
        while (startupAttempts < 600 && !isTerminalStartup(viewModel.startupState.value)) {
            idle()
            delay(50)
            startupAttempts++
        }
        idle()
        assertTrue(
            "startup must reach a terminal state before project activation " +
                "(was ${viewModel.startupState.value})",
            isTerminalStartup(viewModel.startupState.value)
        )

        // ---------------- Project A ----------------
        viewModel.createAndOpenProject("ISO_Project_A").join()
        for (i in 1..50) {
            idle()
            if (viewModel.activeProjectId.value != null) break
            delay(50)
        }
        val projectAId = viewModel.activeProjectId.value
        assertNotNull("Project A must be active after createAndOpenProject", projectAId)

        database.circuitDao().insertCircuit(
            CircuitEntity(
                projectId = projectAId!!,
                circuitName = "CKT_IN_A",
                loadKVA = 10.0,
                voltageV = 400,
                fedFrom = "Transformer"
            )
        )
        var successA: UiState.Success? = null
        for (i in 1..200) {
            idle()
            val s = viewModel.uiState.value
            if (s is UiState.Success && s.circuits.any { it.circuitName == "CKT_IN_A" }) {
                successA = s
                break
            }
            delay(50)
        }
        if (successA == null) {
            // Diagnostic probe (P0-15 harness, added during Phase 6): full-profile JVMs
            // reproduced an emission failure that isolated runs do not — distinguish
            // stuck-Loading vs empty-flow vs dead-collector vs missing Room row.
            val ui = viewModel.uiState.value
            val roomRows = database.circuitDao().getCircuitsListForProject(projectAId)
            fail(
                "uiState must surface Project A's circuit | " +
                    "activeProjectId=${viewModel.activeProjectId.value} | " +
                    "startup=${viewModel.startupState.value.javaClass.simpleName} | " +
                    "uiState=${ui?.javaClass?.simpleName}" +
                    (ui?.let { if (it is UiState.Success) "(circuits=${it.circuits.map { c -> c.circuitName }})" else "" } ?: "") +
                    " | roomRows=${roomRows.map { it.circuitName }} | " +
                    "collectorAlive=${collectJob.isActive}"
            )
        }
        assertTrue(
            "every circuit shown while A is active must belong to A",
            successA!!.circuits.all { it.projectId == projectAId }
        )

        // ---------------- Project B (new) ----------------
        viewModel.createAndOpenProject("ISO_Project_B").join()
        for (i in 1..50) {
            idle()
            if (viewModel.activeProjectId.value != null &&
                viewModel.activeProjectId.value != projectAId
            ) break
            delay(50)
        }
        val projectBId = viewModel.activeProjectId.value
        assertNotNull("Project B must be active", projectBId)
        assertNotEquals("Switch must land on a new project id", projectAId, projectBId)

        database.circuitDao().insertCircuit(
            CircuitEntity(
                projectId = projectBId!!,
                circuitName = "CKT_IN_B",
                loadKVA = 25.0,
                voltageV = 400,
                fedFrom = "Transformer"
            )
        )
        var successB: UiState.Success? = null
        for (i in 1..200) {
            idle()
            val s = viewModel.uiState.value
            if (s is UiState.Success && s.circuits.any { it.circuitName == "CKT_IN_B" }) {
                successB = s
                break
            }
            delay(50)
        }
        assertNotNull("uiState must surface Project B's circuit", successB)
        assertTrue(successB!!.circuits.any { it.circuitName == "CKT_IN_B" })
        assertFalse(
            "Project A's circuit must NOT leak into Project B's uiState",
            successB.circuits.any { it.circuitName == "CKT_IN_A" }
        )
        assertTrue(
            "every circuit shown while B is active must belong to B",
            successB.circuits.all { it.projectId == projectBId }
        )

        // ---------------- Back to Project A ----------------
        viewModel.openProject(projectAId).join()
        var backOnA: UiState.Success? = null
        for (i in 1..200) {
            idle()
            val s = viewModel.uiState.value
            if (s is UiState.Success && s.circuits.any { it.circuitName == "CKT_IN_A" }) {
                backOnA = s
                break
            }
            delay(50)
        }
        assertNotNull("switching back must re-surface Project A's circuit", backOnA)
        assertFalse(
            "Project B's circuit must NOT leak into Project A's uiState",
            backOnA!!.circuits.any { it.circuitName == "CKT_IN_B" }
        )
        assertTrue(backOnA.circuits.all { it.projectId == projectAId })

        // ---------------- Room: rows are project-keyed ----------------
        val rowsA = database.circuitDao().getCircuitsListForProject(projectAId)
        val rowsB = database.circuitDao().getCircuitsListForProject(projectBId)
        assertEquals(listOf("CKT_IN_A"), rowsA.map { it.circuitName })
        assertEquals(listOf("CKT_IN_B"), rowsB.map { it.circuitName })

        collectJob.cancelAndJoin()
        idle()
    }
}
