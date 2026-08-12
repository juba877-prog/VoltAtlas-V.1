package com.example.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
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
        // Wait a bit
        kotlinx.coroutines.delay(1000)
        val state = viewModel.uiState.value
        println("STATE IS: " + state.javaClass.simpleName)
        job.cancel()
        assertTrue(state is UiState.Success)
    }
}
