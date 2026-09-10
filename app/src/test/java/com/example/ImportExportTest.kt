package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.EngineeringViewModel
import com.example.ui.StartupState
import com.example.ui.UiState
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ImportExportTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.example.data.AppDatabase.useTestExecutors = true
    }

    @After
    fun tearDown() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        com.example.data.AppDatabase.useTestExecutors = false
        try {
            Dispatchers.resetMain()
        } catch (_: IllegalStateException) {
            // Guard against background scheduler worker thread race during full suite runs
        }
    }

    @Test
    fun testExportImportRoundTripAndMalformedHandling() = runBlocking {
        val viewModel = EngineeringViewModel(ApplicationProvider.getApplicationContext())

        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }

        // Wait for VM initialization to complete so initializeNewInstallationIfRequired doesn't clear activeProjectId
        for (i in 1..50) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (viewModel.startupState.value is StartupState.Ready || viewModel.startupState.value is StartupState.NoProjects) break
            delay(50)
        }

        // 1. Create Project A and add circuits/transformers
        viewModel.createAndOpenProject("Project_Alpha").join()
        for (i in 1..50) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (viewModel.activeProjectId.value != null) break
            delay(50)
        }
        viewModel.addNewTransformerInline()

        val projectAId = viewModel.activeProjectId.value
        assertNotNull("Active project ID should not be null after createAndOpenProject", projectAId)
        val nonNullProjectAId = projectAId!!

        var successA: UiState.Success? = null
        for (i in 1..50) { // wait up to 2.5s for flow to emit
            val s = viewModel.uiState.value
            if (s is UiState.Success && s.transformers.isNotEmpty()) {
                successA = s
                break
            }
            delay(50)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        
        assertNotNull("Expected UiState.Success with transformers for Project A", successA)

        val origCircuitCount = successA!!.circuits.size
        val origTransformerCount = successA.transformers.size

        println("=== ORIGINAL PROJECT 'Project_Alpha' ===")
        println("ID: $projectAId")
        println("Circuit Count: $origCircuitCount")
        println("Transformer Count: $origTransformerCount")

        // 2. Export Project A to JSON
        val jsonExport = viewModel.exportProjectJson(nonNullProjectAId)
        assertNotNull("Export JSON should not be null", jsonExport)
        assertTrue("Export JSON should not be empty", jsonExport.isNotBlank())

        val exportBytes = jsonExport.toByteArray(Charsets.UTF_8).size
        println("=== EXPORTED FILE INFO ===")
        println("File size (bytes): $exportBytes")
        println("Snippet: ${jsonExport.take(200)}...")

        // 3. Import JSON as a new project
        var importSuccess = false
        var importedMsg = ""
        viewModel.importProjectJson(jsonExport) { success, msg ->
            importSuccess = success
            importedMsg = msg
        }.join()

        assertTrue("Import should succeed: $importedMsg", importSuccess)

        val projectBId = viewModel.activeProjectId.value
        assertNotNull("Imported project active ID should not be null", projectBId)
        assertNotEquals("Imported project should have a new ID", projectAId, projectBId)

        var successB: UiState.Success? = null
        for (i in 1..50) {
            val s = viewModel.uiState.value
            if (s is UiState.Success && s.transformers.isNotEmpty()) {
                successB = s
                break
            }
            delay(50)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        
        assertNotNull("Expected UiState.Success with transformers for imported project", successB)

        val importedCircuitCount = successB!!.circuits.size
        val importedTransformerCount = successB.transformers.size

        println("=== IMPORTED PROJECT ===")
        println("New ID: $projectBId")
        println("Circuit Count: $importedCircuitCount")
        println("Transformer Count: $importedTransformerCount")

        assertEquals("Circuit count should match", origCircuitCount, importedCircuitCount)
        assertEquals("Transformer count should match", origTransformerCount, importedTransformerCount)

        // 4. Malformed/Corrupted JSON handling
        val malformedJson = "{ \"project\": \"broken JSON with missing braces..."
        var malformedSuccess = true
        var malformedErrorMsg = ""

        viewModel.importProjectJson(malformedJson) { success, msg ->
            malformedSuccess = success
            malformedErrorMsg = msg
        }.join()

        assertFalse("Malformed import should return false", malformedSuccess)
        assertTrue("Error message should be provided", malformedErrorMsg.isNotBlank())
        println("=== MALFORMED IMPORT HANDLING ===")
        println("Returned success: $malformedSuccess")
        println("Error message: $malformedErrorMsg")

        collectJob.cancelAndJoin()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}

