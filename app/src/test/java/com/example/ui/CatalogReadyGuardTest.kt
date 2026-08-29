package com.example.ui

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Catalog
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * R2a guard verification (see performance audit, R2a / C1.4):
 *
 * 1. No UiState.Success/Error may be emitted while catalogReady == false,
 *    even though the other 8 combine source flows emit.
 * 2. The first emission must be UiState.Loading.
 * 3. After the catalog load attempt finishes (catalogReady == true), the
 *    pipeline must resolve to a non-degraded UiState.Success with the
 *    catalog actually loaded.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class CatalogReadyGuardTest {

    @Test
    fun noSuccessEmitted_whileCatalogNotReady() = runBlocking {
        val viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())

        // Snapshot (catalogReady at emission time, emitted state) per emission.
        val observations = mutableListOf<Pair<Boolean, UiState>>()
        val job = launch {
            viewModel.uiState.collect { state ->
                observations.add(viewModel.catalogReady.value to state)
            }
        }

        // Phase 1: sample emissions while the catalog load attempt is running.
        val notReadyDeadline = System.currentTimeMillis() + 10_000
        while (!viewModel.catalogReady.value && System.currentTimeMillis() < notReadyDeadline) {
            kotlinx.coroutines.delay(10)
        }
        val sawNotReadyWindow = observations.any { !it.first }

        // Phase 2: exercise the steady state (catalog attempt finished).
        viewModel.createAndOpenProject("GuardTest")
        val readyDeadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < readyDeadline) {
            // Drain the main looper so viewModelScope (Dispatchers.Main) work runs.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.uiState.value is UiState.Success) break
            kotlinx.coroutines.delay(50)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val finalState = viewModel.uiState.value
        job.cancel()

        // Invariant 1: the guard window was actually exercised.
        assertTrue(
            "Expected at least one emission while catalogReady == false, observations=" + observations.size,
            sawNotReadyWindow
        )

        // Invariant 2: no Success/Error observed while catalogReady == false.
        val violations = observations.filter { !it.first && it.second !is UiState.Loading }
        assertTrue(
            "Poisoned emissions while catalogReady == false: " +
                violations.joinToString { it.second.javaClass.simpleName },
            violations.isEmpty()
        )

        // Invariant 3: the very first emission is Loading (initialValue + guard).
        assertTrue(
            "First emission must be UiState.Loading, was: " + observations.first().second.javaClass.simpleName,
            observations.first().second is UiState.Loading
        )

        // Invariant 4: after the flip the state resolves to a non-degraded Success
        // and the catalog load attempt actually completed.
        assertTrue(
            "Final state must be UiState.Success, was: " + finalState.javaClass.simpleName,
            finalState is UiState.Success
        )
        assertTrue(
            "Catalog must be loaded after catalogReady flips true",
            Catalog.getDiagnostics().status == "COMPLETE"
        )
    }
}