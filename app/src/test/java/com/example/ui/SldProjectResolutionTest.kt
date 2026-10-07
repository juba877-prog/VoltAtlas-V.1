package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.EngineeringRepository
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P1-11 (E-09) regression proof for SLD project resolution.
 *
 * The screen previously resolved its project as
 * `activeProjectId.ifBlank { circuits.firstOrNull()?.projectId ?: ... ?: "DEFAULT_PROJECT" }`,
 * which silently rendered ANOTHER project's diagram when the caller id was blank (transitions)
 * and fabricated a DEFAULT_PROJECT id when no entity existed. The fixed rule resolves only
 * from the caller or the persisted active-project store and surfaces an explicit
 * `PROJECT UNRESOLVED — DATA REQUIRED` state when neither source yields an id.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class SldProjectResolutionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val circuits = listOf(
        CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-01", fedFrom = "Transformer")
    )

    private fun seedActiveProject(id: String?) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        EngineeringRepository(AppDatabase.getDatabase(context)).setActiveProject(id)
    }

    @After
    fun tearDown() {
        // Never leak persisted active-project state into other suites sharing this JVM.
        seedActiveProject(null)
        // KT-12: this class calls AppDatabase.getDatabase() directly, which pins the process-wide
        // INSTANCE (and its absolute DB file path inside the current Robolectric sandbox). Robolectric
        // rotates its temp app-data dir per @Config, so a surviving instance points at a path that no
        // longer exists and later suites fail with SQLITE_CANTOPEN (code 14). Drop it.
        AppDatabase.resetInstanceForTesting()
    }

    // ---- pure resolver rules (discriminate the old fallback inline) ----

    @Test
    fun callerProvidedIdWins() {
        assertEquals("proj_caller", resolveSldProjectId("proj_caller", "proj_store"))
    }

    @Test
    fun blankCallerFallsBackToPersistedStore() {
        assertEquals("proj_store", resolveSldProjectId("", "proj_store"))
    }

    @Test
    fun neitherSourceResolvesToExplicitNull() {
        assertNull(resolveSldProjectId("", null))
        // Blank/whitespace-only ids are NOT resolvable ids.
        assertNull(resolveSldProjectId("   ", "   "))
    }

    // ---- explicit unresolved surface ----

    @Test
    fun blankIdAndEmptyStoreSurfacesExplicitUnresolvedState() {
        seedActiveProject(null)
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(circuits = circuits, activeProjectId = "")
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sld_unresolved_state").assertExists()
        // The old first-circuit guess rendered a full diagram here — it must not.
        composeTestRule.onNodeWithTag("sld_screen_container").assertDoesNotExist()
    }

    // ---- store resolution (new capability) ----

    @Test
    fun blankIdResolvesFromPersistedStore() {
        seedActiveProject("proj_1")
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(circuits = circuits, activeProjectId = "")
            }
        }
        // First frame may surface the unresolved state until the store read lands.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("sld_unresolved_state").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("sld_unresolved_state").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sld_screen_container").assertExists()
    }
}
