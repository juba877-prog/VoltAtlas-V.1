package com.example.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Navigation integration conformance for the new entry flow (§1/§16/§17).
 *
 * The real routing lives in MainLayout (state-based navigation, no NavHost). These checks
 * prove the integration points without duplicating a navigation framework:
 *  - App launch reaches the entry screen first (entry gate precedes picker/quick branches)
 *  - both destinations are wired to the existing screens
 *  - the Project Picker no longer hosts a Quick Engineering entry
 *  - the picker can return to the entry root (no navigation loop, no invalid intermediate)
 */
class VoltAtlasEntryNavigationConformanceTest {

    private val moduleRoot: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from ${System.getProperty("user.dir")}")
    }

    private fun source(relative: String): String {
        val file = File(moduleRoot, relative)
        assertTrue("$relative must exist", file.exists())
        return file.readText()
    }

    @Test
    fun appLaunch_reachesEntryScreenBeforePickerOrQuickEngineering() {
        val layout = source("com/example/ui/MainLayout.kt")

        val entryIndex = layout.indexOf("VoltAtlasEntryScreen(")
        val pickerIndex = layout.indexOf("if (activeId == null)")
        val quickIndex = layout.indexOf("if (showQuickEngineering)")

        assertTrue("entry screen branch must exist", entryIndex > 0)
        assertTrue("entry screen must precede the Project Picker branch", entryIndex < pickerIndex)
        assertTrue("entry screen must precede the Quick Engineering branch", entryIndex < quickIndex)
        assertTrue("entry gate must be state-based", layout.contains("if (!entryModeChosen)"))
    }

    @Test
    fun entryScreen_isWiredToBothExistingDestinations() {
        val layout = source("com/example/ui/MainLayout.kt")

        assertTrue(layout.contains("onOpenProjects = { entryModeChosen = true }"))
        assertTrue(layout.contains("onOpenQuickEngineering = {"))
        assertTrue("quick destination must open the existing hub", layout.contains("showQuickEngineering = true"))
        assertTrue("settings must keep its existing action", layout.contains("onOpenSettings = { viewModel.openGlobalSettings() }"))
    }

    @Test
    fun projectPicker_noLongerHostsQuickEngineering() {
        val picker = source("com/example/ui/ProjectPickerScreen.kt")

        assertTrue("Quick Engineering button must be removed", !picker.contains("quick_calc_btn"))
        assertTrue("Quick Engineering parameter must be removed", !picker.contains("onOpenQuickCalc"))
        assertTrue("picker keeps its project functionality", picker.contains("onCreateProject"))
        assertTrue("picker keeps import functionality", picker.contains("onImportProjectJson"))
    }

    @Test
    fun projectPicker_canReturnToTheEntryRoot() {
        val picker = source("com/example/ui/ProjectPickerScreen.kt")

        assertTrue(picker.contains("onBackToEntry: (() -> Unit)? = null"))
        assertTrue(picker.contains("onBackToEntry != null -> onBackToEntry()"))
    }
}
