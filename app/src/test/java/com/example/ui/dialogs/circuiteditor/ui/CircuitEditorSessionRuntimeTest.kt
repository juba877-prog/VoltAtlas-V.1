package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2 runtime tests for editor-session identity, retained-composition transitions,
 * snapshot emission retention, and bottom action-bar bounds.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class CircuitEditorSessionRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun nameInput() =
        composeTestRule.onNode(hasParent(hasTestTag("input_circuit_name")) and hasSetTextAction())

    private fun nameText(): String =
        nameInput().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    // A retained composition whose logical circuit changes from A to B must reinitialize
    // draft state so B never shows A's edits.
    @Test
    fun test01_session_editAReplacedByEditBReinitializes() {
        var circuit by mutableStateOf<CircuitEntity?>(
            CircuitEntity(id = 2, circuitName = "DB-A", panelType = "DB")
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = circuit,
                    existingCircuits = emptyList(),
                    onDismiss = {}
                )
            }
        }

        nameInput().performTextInput("A-EDITED")
        composeTestRule.waitForIdle()

        circuit = CircuitEntity(id = 3, circuitName = "DB-B", panelType = "DB")
        composeTestRule.waitForIdle()

        nameInput().assertTextContains("DB-B")
        assertFalse("A's edit must not survive the session change", nameText().contains("A-EDITED"))
    }

    // Grid Add (null) -> PanelSchedule Add-stub (id == 0) must not share state.
    @Test
    fun test02_session_addNullToAddStubDoesNotCollide() {
        var circuit by mutableStateOf<CircuitEntity?>(null)
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = circuit,
                    existingCircuits = emptyList(),
                    onDismiss = {}
                )
            }
        }
        nameInput().performTextInput("GRID-ADD")
        composeTestRule.waitForIdle()

        circuit = CircuitEntity(id = 0, circuitName = "Stub Panel", panelType = "DB")
        composeTestRule.waitForIdle()

        assertFalse("grid-add edit must not survive into the stub-add session", nameText().contains("GRID-ADD"))
        nameInput().assertTextContains("Stub Panel")
    }

    // Edit -> Add within a retained composition must not leak edit state into the add session.
    @Test
    fun test03_session_editToAddDoesNotLeak() {
        var circuit by mutableStateOf<CircuitEntity?>(
            CircuitEntity(id = 9, circuitName = "DB-Source", panelType = "DB")
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = circuit,
                    existingCircuits = emptyList(),
                    onDismiss = {}
                )
            }
        }
        nameInput().performTextInput("LEAK-EDIT")
        composeTestRule.waitForIdle()

        circuit = null
        composeTestRule.waitForIdle()

        assertFalse("edit state must not leak into the new add session", nameText().contains("LEAK-EDIT"))
    }

    // Snapshot emissions (equipment list) must NOT reset the session.
    @Test
    fun test04_session_snapshotEmissionDoesNotResetDraft() {
        var transformerList by mutableStateOf(listOf(TransformerSnapshot("TX-01")))
        var circuit by mutableStateOf<CircuitEntity?>(null)
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(
                    circuit = circuit,
                    existingCircuits = emptyList(),
                    onDismiss = {},
                    availableTransformers = transformerList
                )
            }
        }
        nameInput().performTextInput("KEEP-ME")
        composeTestRule.waitForIdle()

        // Genuine snapshot emission: a new list is emitted while the editor stays open.
        transformerList = listOf(TransformerSnapshot("TX-02"), TransformerSnapshot("TX-03"))
        composeTestRule.waitForIdle()

        nameInput().assertTextContains("KEEP-ME")
    }

    // ============================================================================
    // Bottom action-bar bounds
    // ============================================================================

    private fun rootBounds() = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()
    private fun barBounds() = composeTestRule.onNodeWithTag("circuit_editor_action_bar").getBoundsInRoot()
    private fun viewportBounds() = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()

    private fun assertBarInsideRoot() {
        val root = rootBounds()
        val bar = barBounds()
        assertTrue("bar bottom (${bar.bottom}) must be <= root bottom (${root.bottom})", bar.bottom + 0.dp <= root.bottom + 0.dp)
        assertTrue("bar top (${bar.top}) must be >= root top", bar.top >= root.top)
    }

    private fun assertPrimaryInsideRoot(tag: String, minHeightDp: Float = 48f) {
        val root = rootBounds()
        val action = composeTestRule.onNodeWithTag(tag).getBoundsInRoot()
        val actionHeight: Float = (action.bottom - action.top).value
        assertTrue("action '$tag' bottom (${action.bottom}) must be <= root bottom (${root.bottom})", action.bottom + 0.dp <= root.bottom + 0.dp)
        assertTrue("action '$tag' top (${action.top}) must be >= 0", action.top >= 0.dp)
        assertTrue("action '$tag' height (${actionHeight}) must be >= $minHeightDp dp", actionHeight >= (minHeightDp - 1f))
    }

    private fun assertViewportClearsBar() {
        val viewport = viewportBounds()
        val bar = barBounds()
        assertTrue("viewport bottom (${viewport.bottom}) must be <= bar top (${bar.top})", viewport.bottom + 0.dp <= bar.top + 0.dp)
    }

    // 5. Identity: complete Next visible inside the root; no Back; viewport clears the bar.
    @Test
    fun test05_bounds_identityNextFullyInsideRoot() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(circuit = null, existingCircuits = emptyList(), onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("circuit_editor_back_btn").assertDoesNotExist()
        assertBarInsideRoot()
        assertPrimaryInsideRoot("circuit_editor_next_btn")
        assertViewportClearsBar()
    }

    // 6. Load: Back + Next both complete and inside the root.
    @Test
    fun test06_bounds_loadBackAndNextInsideRoot() {
        var circuit by mutableStateOf<CircuitEntity?>(null)
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(circuit = circuit, existingCircuits = emptyList(), onDismiss = {})
            }
        }
        nameInput().performTextInput("Load-B")
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        composeTestRule.waitForIdle()

        assertBarInsideRoot()
        assertPrimaryInsideRoot("circuit_editor_back_btn")
        assertPrimaryInsideRoot("circuit_editor_next_btn")
        assertViewportClearsBar()
    }

    // 7. Review: Save complete and inside the root; Next absent.
    @Test
    fun test07_bounds_reviewSaveInsideRootAndNoNext() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(circuit = null, existingCircuits = emptyList(), onDismiss = {})
            }
        }
        nameInput().performTextInput("Review-B")
        repeat(5) {
            composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertDoesNotExist()
        assertBarInsideRoot()
        assertPrimaryInsideRoot("save_circuit_btn")
    }

    // 8. 320 dp at font scale 1.5: Identity Next must still be entirely inside the root.
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test08_bounds_identityNextInsideRootAt320Font15() {
        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelEntry(circuit = null, existingCircuits = emptyList(), onDismiss = {})
            }
        }
        assertBarInsideRoot()
        assertPrimaryInsideRoot("circuit_editor_next_btn")
        assertViewportClearsBar()
    }
}