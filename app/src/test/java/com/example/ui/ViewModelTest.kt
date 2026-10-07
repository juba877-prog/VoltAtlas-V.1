package com.example.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(manifest=Config.NONE)
class ViewModelTest {
    @org.junit.After
    fun tearDown() {
        // KT-12: this class reaches AppDatabase.getDatabase() (directly or via
        // EngineeringViewModel), which caches the instance process-wide together with an
        // absolute DB file path inside the current Robolectric sandbox. Robolectric rotates that
        // sandbox per @Config while Gradle reuses one JVM, so a surviving instance points at a
        // path that no longer exists and later suites fail with SQLITE_CANTOPEN (code 14).
        AppDatabase.resetInstanceForTesting()
    }

    @Test
    fun testViewModelState() = runBlocking {
        val viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())
        
        // Start collection to trigger stateIn!
        val job = launch {
            viewModel.uiState.collect {
                println("EMITTED: " + it.javaClass.simpleName)
            }
        }
        
        viewModel.createAndOpenProject("Test")
        // Bounded poll for UiState.Success (pipeline waits for catalog load)
        val readyDeadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < readyDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.uiState.value is UiState.Success) break
            kotlinx.coroutines.delay(50)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val state = viewModel.uiState.value
        println("STATE IS: " + state.javaClass.simpleName)
        job.cancel()
        assertTrue(state is UiState.Success)
    }

    /**
     * P1-10 (O-10) regression proof: a pending equipment-delete confirmation raised under
     * project A must be cleared when the active project switches to B — otherwise
     * confirming it after the switch would delete/mutate with project A's equipment id
     * against project B's state. Before the fix, switchActiveProject() dropped only the
     * active id and the auto-design cache, so the dialog state survived the transition.
     */
    @Test
    fun pendingDeleteConfirmationIsClearedOnProjectSwitch() = runBlocking {
        val viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())

        // Wait for the init { retryStartup() } pipeline to finish FIRST: its
        // initializeNewInstallationIfRequired() calls switchActiveProject(null) and can
        // otherwise race (and clobber) the activation below — that startup race is
        // pre-existing behavior, not the subject of this test. Iteration-bounded loops
        // avoid Robolectric's paused-clock deadlines.
        var attempts = 0
        while (attempts < 600 && !isTerminalStartup(viewModel.startupState.value)) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            attempts++
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Project A becomes active.
        viewModel.createAndOpenProject("Project A")
        attempts = 0
        while (attempts < 600 && viewModel.activeProjectId.value == null) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            attempts++
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val projectA = viewModel.activeProjectId.value
        assertNotNull("project A must be active", projectA)

        // Raise a delete confirmation against project A. requestDeleteGenerator always
        // builds the confirmation (unknown ids are echoed, so no seed equipment is needed).
        viewModel.requestDeleteGenerator("GEN-A")
        attempts = 0
        while (attempts < 600 && viewModel.pendingDeleteConfirmation.value == null) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            attempts++
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(
            "confirmation must be pending while project A is active",
            viewModel.pendingDeleteConfirmation.value
        )

        // Switch to project B — P1-10 (O-10): the A-scoped confirmation must not survive.
        viewModel.createAndOpenProject("Project B")
        attempts = 0
        while (attempts < 600 && viewModel.activeProjectId.value == projectA) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            attempts++
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(
            "P1-10 (O-10): switchActiveProject must clear pendingDeleteConfirmation — a " +
                "confirmation raised under project A must never confirm (and mutate) after " +
                "the active project changed.",
            viewModel.pendingDeleteConfirmation.value
        )
    }

    private fun isTerminalStartup(state: StartupState): Boolean = when (state) {
        is StartupState.Ready,
        is StartupState.NoProjects,
        is StartupState.ReadyWithWarnings -> true
        else -> false
    }
}
