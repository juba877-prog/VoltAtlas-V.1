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
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(manifest=Config.NONE)
class ViewModelTest {
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
}
